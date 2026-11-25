#include "AndroidEngine.h"

#include <algorithm>
#include <cmath>
#include <cstring>
#include <exception>
#include <span>

#include "core/Denormals.h"
#include "core/FlpImport.h"
#include "core/Scheduler.h"
#include "core/Templates.h"
#include "flp/Flp.h"

namespace lynx::android
{
Engine::Engine (double rate)
    : sampleRate (rate), left ((std::size_t) maxBlock), right ((std::size_t) maxBlock)
{
    renderer.prepare (sampleRate, maxBlock);
}

Engine::~Engine() = default;

std::string Engine::loadTemplate (int index)
{
    try
    {
        const auto count = (int) templateNames().size();
        return publish (makeTemplate (std::clamp (index, 0, count - 1)));
    }
    catch (const std::exception& e)
    {
        return e.what()[0] != 0 ? e.what() : "The template could not be built";
    }
}

std::string Engine::loadFlp (const std::uint8_t* data, std::size_t size)
{
    try
    {
        const auto parsed = flp::parseProject (std::span<const std::uint8_t> (data, size));
        return publish (importFlp (parsed));
    }
    catch (const std::exception& e)
    {
        return e.what()[0] != 0 ? e.what() : "This is not an FL Studio project";
    }
}

std::string Engine::publish (Song song)
{
    song.keepOnePattern();
    song.mode = PlayMode::pattern; // the current pattern in a loop (the Playlist is not played here yet)

    const auto& pattern = song.patterns[(std::size_t) song.currentPattern];
    auto playback = std::make_unique<Playback>();
    playback->bpm = song.bpm;
    playback->lengthTicks = std::max<std::int64_t> (pattern.effectiveLength (song.meter()), song.beatTicks());

    PatternView next;
    next.name = pattern.name;
    next.bpm = song.bpm;
    next.lengthTicks = playback->lengthTicks;
    next.notes.reserve (pattern.notes.size() * 4);
    for (const auto& note : pattern.notes)
    {
        if (note.muted)
            continue;
        next.notes.push_back ((std::int32_t) note.start);
        next.notes.push_back ((std::int32_t) note.length);
        next.notes.push_back ((std::int32_t) note.key);
        next.notes.push_back ((std::int32_t) note.channel);
    }

    try
    {
        playback->state = RackState::build (std::move (song), nullptr, sampleRate); // (`pattern` is not used past here)
        renderer.reserveFor (*playback->state); // (off the audio thread, before the state reaches it)
    }
    catch (const std::exception& e)
    {
        return e.what()[0] != 0 ? e.what() : "The project could not be built";
    }

    std::lock_guard<std::mutex> lock (viewLock);
    exchange.publish (std::move (playback));
    shown = std::move (next);
    return {};
}

PatternView Engine::view() const
{
    std::lock_guard<std::mutex> lock (viewLock);
    return shown;
}

void Engine::render (float* out, int frames) noexcept
{
    const ScopedFlushDenormals noDenormals;
    const ScopedExtendedPrecision extended;

    const auto* current = exchange.acquire();
    if (current != playingNow) // a new project starts from its beginning
    {
        playingNow = current;
        tick = 0.0;
    }
    if (rewindRequested.exchange (false, std::memory_order_relaxed))
        tick = 0.0;

    if (current == nullptr)
    {
        std::memset (out, 0, sizeof (float) * 2 * (std::size_t) std::max (frames, 0));
        return;
    }

    const bool on = playing.load (std::memory_order_relaxed);
    const double ticksPerFrame = ticksPerSample (current->bpm, sampleRate);
    const double length = (double) current->lengthTicks;

    for (int done = 0; done < frames;)
    {
        int n = std::min (frames - done, maxBlock);
        if (on) // a block ends where the loop does, so the pattern starts again on its first sample
            n = std::min (n, std::max (1, (int) std::ceil ((length - tick) / ticksPerFrame)));

        renderer.process (current->state.get(), on, tick, n, left.data(), right.data());

        float* dest = out + 2 * (std::size_t) done;
        for (int i = 0; i < n; ++i)
        {
            dest[2 * i] = left[(std::size_t) i];
            dest[2 * i + 1] = right[(std::size_t) i];
        }

        if (on)
        {
            tick += n * ticksPerFrame;
            if (tick >= length)
                tick = 0.0;
        }
        done += n;
    }
    position.store (tick, std::memory_order_relaxed);
}
} // namespace lynx::android
