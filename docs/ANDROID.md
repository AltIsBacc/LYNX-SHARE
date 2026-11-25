# Android

A first, minimal Android app (`android/`): it plays the current pattern of a built-in template or of an `.flp` project with
LYNX SHARE's own engine and draws the pattern, with its playhead, into a `SurfaceView`. It is a starting point, not the
desktop app on a phone: the JUCE app (`src/ui`, `src/engine`) is not part of it.

**Status: written, not built or run yet.** The first build on a machine with the Android SDK and NDK will most likely
need a fix or two; this page is what to expect from it and where to look.

## What it is

| Part | Where | What it does |
|---|---|---|
| Engine | `android/native/AndroidEngine.*` | The JUCE-free core (`lynx_core`): a `Song`, a `RackRenderer` and the lock-free hand-over of a loaded project to the audio thread (`core/SnapshotExchange.h`, as the desktop engine, [ARCHITECTURE.md](ARCHITECTURE.md)). Loops the project's current pattern; `render()` fills a block of interleaved stereo floats and never allocates or locks. |
| JNI bridge | `android/native/Jni.cpp` | `com.ka1s.lynx.NativeEngine`'s calls; a handle is the address of an `Engine`. |
| Audio | `AudioOutput.java` | A thread asks the engine for 512 frames and writes them to a low-latency float `AudioTrack` at the device's own sample rate. |
| Drawing | `PatternView.java` | A `SurfaceView` drawn from a thread of its own (`lockCanvas` / `unlockCanvasAndPost`): a bar per note (a hue per channel), a line per beat, the playhead. |
| Screen | `MainActivity.java` | Play / Stop, *Open...* (an `.flp` through the system file picker) and *New from template*. Loading runs on one background thread. |

The native library is the repository's own CMake project built for Android: `CMakeLists.txt` adds `android/native` when
`ANDROID` is set, and the JUCE app is then off by default (`LYNX_BUILD_APP`). Gradle builds only the `lynx_android` target
(`android/app/build.gradle`), so none of the desktop tools and tests are compiled.

## Building

Needs Android Studio (or the command-line SDK) with the NDK and CMake 3.22.1 from the SDK Manager, and Gradle 8.9 or newer.

```
cd android
gradle assembleDebug          # or open android/ in Android Studio
```

ABIs: `arm64-v8a` (phones, tablets) and `x86_64` (the emulator). Minimum Android 8.0 (API 26). Release builds should use the
NDK's clang; the core is compiled with `-ffp-contract=off` and, on arm64, a signed `char` as everywhere else
([PLATFORMS.md](PLATFORMS.md)), and the arm64 build takes the same sse2neon path as Linux arm64.

## What is missing

- **Samples.** The engine is given no sample reader, so a channel that plays a sample (the templates' drums are built-in
  synths; most `.flp` projects use samples) stays silent, and an `.flp`'s sample paths are not looked for.
- **The Playlist.** The current pattern plays in a loop; the song mode is not played. Tempo automation is not followed.
- **Editing and saving.** Nothing is edited. `.lynx` projects (`engine/ProjectFile`, JUCE) are not opened.
- **Plugins**, the plugin sandbox, MIDI input and the effects' hosted formats (no VST3 / CLAP / LV2 on Android).
- **Threads.** The block is rendered on the audio thread alone: no `WorkerPool`, so a heavy project drops out sooner than on
  a desktop. Audio-thread priority is the `AudioTrack` thread's.
- **Tests.** No test covers the Android part yet; the core's own suite (`scripts/build-local.sh`) is unchanged.

## Next steps

Sample loading (the core's WAV / Ogg / FLAC readers behind `RackState::Factories::samples`), a Playlist view and song mode,
touch editing in the piano roll, and the Oboe library for lower audio latency than `AudioTrack` gives.
