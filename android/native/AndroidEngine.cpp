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
namespace
{
constexpr double minTempo = 40.0;
constexpr double maxTempo = 300.0;

std::int64_t patternLength (const Song& song)
{
    const auto& pattern = song.patterns[(std::size_t) song.currentPattern];
    return std::max<std::int64_t> (pattern.effectiveLength (song.meter()), song.beatTicks());
}

std::string messageOf (const std::exception& e, const char* fallback)
{
    return e.what()[0] != 0 ? e.what() : fallback;
}
}

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
        std::lock_guard<std::mutex> lock (editLock);
        return commit (makeTemplate (std::clamp (index, 0, count - 1)), true);
    }
    catch (const std::exception& e)
    {
        return messageOf (e, "The template could not be built");
    }
}

std::string Engine::loadFlp (const std::uint8_t* data, std::size_t size)
{
    try
    {
        const auto parsed = flp::parseProject (std::span<const std::uint8_t> (data, size));
        auto imported = importFlp (parsed);
        std::lock_guard<std::mutex> lock (editLock);
        return commit (std::move (imported), true);
    }
    catch (const std::exception& e)
    {
        return messageOf (e, "This is not an FL Studio project");
    }
}

std::string Engine::commit (Song next, bool fresh)
{
    next.keepOnePattern();
    next.mode = PlayMode::pattern;

    auto playback = std::make_unique<Playback>();
    playback->bpm = next.bpm;
    playback->lengthTicks = patternLength (next);
    playback->project = fresh ? projectId + 1 : projectId;

    try
    {
        playback->state = RackState::build (next, fresh ? nullptr : lastState, sampleRate);
        renderer.reserveFor (*playback->state);
    }
    catch (const std::exception& e)
    {
        return messageOf (e, "The project could not be built");
    }

    projectId = playback->project;
    lastState = playback->state.get();
    song = std::move (next);
    exchange.publish (std::move (playback));
    return {};
}

bool Engine::toggleStep (int channel, int step)
{
    std::lock_guard<std::mutex> lock (editLock);
    if (channel < 0 || channel >= (int) song.channels.size())
        return false;

    Song next = song;
    auto& pattern = next.patterns[(std::size_t) next.currentPattern];
    const std::int64_t start = (std::int64_t) step * ticksPerStep;
    if (step < 0 || start >= patternLength (next))
        return false;

    const auto before = pattern.notes.size();
    std::erase_if (pattern.notes, [&] (const Note& n) { return n.channel == channel && n.start == start; });
    const bool added = pattern.notes.size() == before;
    if (added)
    {
        Note note;
        note.start = start;
        note.length = ticksPerStep;
        note.channel = channel;
        pattern.notes.push_back (note);
        pattern.sortNotes();
    }
    commit (std::move (next), false);
    return added;
}

bool Engine::toggleNote (int channel, int key, int step)
{
    std::lock_guard<std::mutex> lock (editLock);
    if (channel < 0 || channel >= (int) song.channels.size())
        return false;

    Song next = song;
    auto& pattern = next.patterns[(std::size_t) next.currentPattern];
    const std::int64_t start = (std::int64_t) step * ticksPerStep;
    if (step < 0 || start >= patternLength (next))
        return false;

    const auto before = pattern.notes.size();
    std::erase_if (pattern.notes, [&] (const Note& n)
    {
        return n.channel == channel && n.key == key && n.start <= start && start < n.start + n.length;
    });
    const bool added = pattern.notes.size() == before;
    if (added)
    {
        Note note;
        note.start = start;
        note.length = ticksPerStep;
        note.channel = channel;
        note.key = key;
        pattern.notes.push_back (note);
        pattern.sortNotes();
    }
    commit (std::move (next), false);
    return added;
}

void Engine::setTempo (double bpm)
{
    std::lock_guard<std::mutex> lock (editLock);
    if (song.patterns.empty())
        return;

    Song next = song;
    next.bpm = std::clamp (bpm, minTempo, maxTempo);
    commit (std::move (next), false);
}

void Engine::setChannelMuted (int channel, bool muted)
{
    std::lock_guard<std::mutex> lock (editLock);
    if (channel < 0 || channel >= (int) song.channels.size())
        return;

    Song next = song;
    next.channels[(std::size_t) channel].muted = muted;
    commit (std::move (next), false);
}

PatternView Engine::view() const
{
    std::lock_guard<std::mutex> lock (editLock);
    PatternView result;
    if (song.patterns.empty())
        return result;

    const auto& pattern = song.patterns[(std::size_t) song.currentPattern];
    result.name = pattern.name;
    result.bpm = song.bpm;
    result.lengthTicks = patternLength (song);
    result.notes.reserve (pattern.notes.size() * 4);
    for (const auto& note : pattern.notes)
    {
        if (note.muted)
            continue;
        result.notes.push_back ((std::int32_t) note.start);
        result.notes.push_back ((std::int32_t) note.length);
        result.notes.push_back ((std::int32_t) note.key);
        result.notes.push_back ((std::int32_t) note.channel);
    }
    return result;
}

std::vector<ChannelInfo> Engine::channels() const
{
    std::lock_guard<std::mutex> lock (editLock);
    std::vector<ChannelInfo> result;
    result.reserve (song.channels.size());
    for (const auto& channel : song.channels)
        result.push_back ({ channel.name, channel.colour, channel.muted });
    return result;
}

void Engine::render (float* out, int frames) noexcept
{
    const ScopedFlushDenormals noDenormals;
    const ScopedExtendedPrecision extended;

    const auto* current = exchange.acquire();
    if (rewindRequested.exchange (false, std::memory_order_relaxed))
        tick = 0.0;

    if (current == nullptr)
    {
        std::memset (out, 0, sizeof (float) * 2 * (std::size_t) std::max (frames, 0));
        return;
    }

    if (current->project != playingProject)
    {
        playingProject = current->project;
        tick = 0.0;
    }

    const bool on = playing.load (std::memory_order_relaxed);
    const double ticksPerFrame = ticksPerSample (current->bpm, sampleRate);
    const double length = (double) current->lengthTicks;
    if (tick >= length)
        tick = 0.0;

    for (int done = 0; done < frames;)
    {
        int n = std::min (frames - done, maxBlock);
        if (on)
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
}
