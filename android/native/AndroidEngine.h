#pragma once

// The engine behind the Android app (docs/ANDROID.md): the JUCE-free core (a Song, a RackRenderer) behind a small
// interface, so the Java side only starts and stops it, hands it a project and asks for audio.
//
// Threads, as in the desktop engine (docs/ARCHITECTURE.md): the loading calls (loadTemplate, loadFlp) all run on one
// thread, the UI's; render() runs on the audio thread and never allocates or locks. A project goes from the one to the
// other as an immutable snapshot (core/SnapshotExchange.h).

#include <atomic>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "core/RackRenderer.h"
#include "core/SnapshotExchange.h"

namespace lynx::android
{
// What the pattern view draws: the playing pattern's notes, flat (start, length, key and channel per note, in ticks).
struct PatternView
{
    std::string name;
    double bpm = 0.0;
    std::int64_t lengthTicks = 0;
    std::vector<std::int32_t> notes;
};

class Engine
{
public:
    // `sampleRate` is the audio device's.
    explicit Engine (double sampleRate);
    ~Engine();

    Engine (const Engine&) = delete;
    Engine& operator= (const Engine&) = delete;

    // The UI's thread. A built-in template (core/Templates.h: 0 Empty, 1 Basic beat, 2 Trap with 808, 3 House) or an
    // .flp project's bytes; an empty string when it worked, else what went wrong. The pattern that plays in a loop is the
    // project's current one. Samples are not read yet: a channel that plays one stays silent.
    std::string loadTemplate (int index);
    std::string loadFlp (const std::uint8_t* data, std::size_t size);

    // Any thread.
    void setPlaying (bool on) noexcept { playing.store (on, std::memory_order_relaxed); }
    bool isPlaying() const noexcept { return playing.load (std::memory_order_relaxed); }
    void rewind() noexcept { rewindRequested.store (true, std::memory_order_relaxed); }
    // Where the pattern plays now, in ticks (core/Song.h ppq a quarter note); as of the last block rendered.
    double positionTicks() const noexcept { return position.load (std::memory_order_relaxed); }
    PatternView view() const;

    // The audio thread: `frames` stereo frames, interleaved, into `out`. Silence before a project is loaded.
    void render (float* out, int frames) noexcept;

private:
    // What the audio thread plays: the project's rack and where its loop ends.
    struct Playback
    {
        std::unique_ptr<const RackState> state;
        double bpm = 120.0;
        std::int64_t lengthTicks = 0;
    };

    std::string publish (Song song);

    static constexpr int maxBlock = 1024; // frames per RackRenderer::process call

    const double sampleRate;
    RackRenderer renderer;
    SnapshotExchange<Playback> exchange;
    std::vector<float> left, right; // one block, before it is interleaved

    // the audio thread's
    const Playback* playingNow = nullptr;
    double tick = 0.0;

    std::atomic<bool> playing { false };
    std::atomic<bool> rewindRequested { false };
    std::atomic<double> position { 0.0 };

    mutable std::mutex viewLock; // the UI's: view() and publish()
    PatternView shown;
};
} // namespace lynx::android
