#pragma once

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
struct PatternView
{
    std::string name;
    double bpm = 0.0;
    std::int64_t lengthTicks = 0;
    std::vector<std::int32_t> notes;
};

struct ChannelInfo
{
    std::string name;
    std::uint32_t colour = 0;
    bool muted = false;
};

class Engine
{
public:
    explicit Engine (double sampleRate);
    ~Engine();

    Engine (const Engine&) = delete;
    Engine& operator= (const Engine&) = delete;

    std::string loadTemplate (int index);
    std::string loadFlp (const std::uint8_t* data, std::size_t size);

    bool toggleStep (int channel, int step);
    bool toggleNote (int channel, int key, int step);
    void setTempo (double bpm);
    void setChannelMuted (int channel, bool muted);

    void setPlaying (bool on) noexcept { playing.store (on, std::memory_order_relaxed); }
    bool isPlaying() const noexcept { return playing.load (std::memory_order_relaxed); }
    void rewind() noexcept { rewindRequested.store (true, std::memory_order_relaxed); }
    double positionTicks() const noexcept { return position.load (std::memory_order_relaxed); }

    PatternView view() const;
    std::vector<ChannelInfo> channels() const;

    void render (float* out, int frames) noexcept;

private:
    struct Playback
    {
        std::unique_ptr<const RackState> state;
        double bpm = 120.0;
        std::int64_t lengthTicks = 0;
        std::uint64_t project = 0;
    };

    std::string commit (Song next, bool fresh);

    static constexpr int maxBlock = 1024;

    const double sampleRate;
    RackRenderer renderer;
    SnapshotExchange<Playback> exchange;
    std::vector<float> left, right;

    std::uint64_t playingProject = 0;
    double tick = 0.0;

    std::atomic<bool> playing { false };
    std::atomic<bool> rewindRequested { false };
    std::atomic<double> position { 0.0 };

    mutable std::mutex editLock;
    Song song;
    const RackState* lastState = nullptr;
    std::uint64_t projectId = 0;
};
}
