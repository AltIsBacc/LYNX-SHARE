# Android

A first, minimal Android app (`android/`): it plays the current pattern of a built-in template or of an `.flp` project with
LYNX SHARE's own engine, and edits it on a touch screen in the style of a phone DAW: a transport bar on top, a channel rack
(step sequencer) and a piano roll as tabs below, both drawn into `SurfaceView`s. It is a starting point, not the desktop
app on a phone: the JUCE app (`src/ui`, `src/engine`) is not part of it.

**Status: written, built by CI, not run on a device yet.** `.github/workflows/android.yml` builds the debug APK for
`arm64-v8a` on every push that touches `android/`, the core or `CMakeLists.txt`, and uploads it as the
`LYNX-android-debug` artifact. Nobody has started it on a phone or an emulator, so expect fixes in the touch handling and
the audio path first.

## What it is

| Part | Where | What it does |
|---|---|---|
| Engine | `android/native/AndroidEngine.*` | The JUCE-free core (`lynx_core`): a `Song`, a `RackRenderer` and the lock-free hand-over of a project to the audio thread (`core/SnapshotExchange.h`, as the desktop engine, [ARCHITECTURE.md](ARCHITECTURE.md)). Loops the project's current pattern; `render()` fills a block of interleaved stereo floats and never allocates or locks. |
| Editing | same | Toggling a step or a note, the tempo and a channel's mute edit a copy of the `Song` and publish a new snapshot built from the previous rack, so instruments keep their voices and the loop keeps its position. A load starts the loop over. |
| JNI bridge | `android/native/Jni.cpp` | `com.ka1s.lynx.NativeEngine`'s calls; a handle is the address of an `Engine`. |
| Audio | `AudioOutput.java` | A thread asks the engine for 512 frames and writes them to a low-latency float `AudioTrack` at the device's own sample rate. |
| Channel rack | `StepSequencerView.java` | A pad column (mute LED, coloured pad with the channel's name, tap to select) and a grid of 16th-note steps that scrolls in both directions, with the playhead over it. |
| Piano roll | `PianoRollView.java` | A keyboard column and a note grid for the selected channel, the other channels' notes ghosted; a tap adds or removes a one-step note. |
| Chrome | `TransportBar.java`, `TabBar.java`, `Theme.java` | Menu, play / stop, tempo with a step of one BPM and the pattern's name on top; the two tabs below; the colours and the channel palette. |
| Screen | `MainActivity.java` | Puts them together. *Open...* (an `.flp` through the system file picker) and *New from template* are in the menu. Every load and edit runs on one background thread and hands the views a `ProjectModel` snapshot. |

Both grids are drawn from a thread of their own (`RenderSurface.java`: `lockCanvas` / `unlockCanvasAndPost`), so drawing
never waits for the UI thread.

The native library is the repository's own CMake project built for Android: `CMakeLists.txt` adds `android/native` when
`ANDROID` is set, and the JUCE app is then off by default (`LYNX_BUILD_APP`). Gradle builds only the `lynx_android` target
(`android/app/build.gradle`), so none of the desktop tools and tests are compiled.

## Building

Needs Android Studio (or the command-line SDK) with the NDK and CMake 3.22.1 from the SDK Manager, and Gradle 8.9 or newer.

```
cd android
gradle assembleDebug          # or open android/ in Android Studio
```

CI does the same with NDK 27.2.12479018 and CMake 3.22.1 on an Ubuntu runner, then checks that `liblynx_android.so` is in the APK.

ABI: `arm64-v8a` only (phones, tablets, and the emulator on an arm64 Mac). There is no `x86_64` build: several effects run
FL's x87 arithmetic in `long double` to match it bit for bit (`src/core/Granulizer.cpp`, `DistructorFx.cpp`...), and Android's
x86_64 ABI has no 80-bit `long double`, so the inline assembly does not compile there. Minimum Android 8.0 (API 26). Release builds should use the
NDK's clang; the core is compiled with `-ffp-contract=off` and, on arm64, a signed `char` as everywhere else
([PLATFORMS.md](PLATFORMS.md)), and the arm64 build takes the same sse2neon path as Linux arm64.

## What is missing

- **Samples.** The engine is given no sample reader, so a channel that plays a sample (the templates' drums are built-in
  synths; most `.flp` projects use samples) stays silent, and an `.flp`'s sample paths are not looked for.
- **The Playlist.** The current pattern plays in a loop; the song mode is not played. Tempo automation is not followed.
- **Editing.** Steps, one-step notes, tempo and mute only: no note length, velocity, drag or select, no new channels, no undo,
  and nothing is saved. `.lynx` projects (`engine/ProjectFile`, JUCE) are not opened.
- **Plugins**, the plugin sandbox, MIDI input and the effects' hosted formats (no VST3 / CLAP / LV2 on Android).
- **Threads.** The block is rendered on the audio thread alone: no `WorkerPool`, so a heavy project drops out sooner than on
  a desktop. Audio-thread priority is the `AudioTrack` thread's.
- **Tests.** No test covers the Android part yet beyond CI compiling and linking it; the core's own suite
  (`scripts/build-local.sh`) is unchanged.

## Next steps

Sample loading (the core's WAV / Ogg / FLAC readers behind `RackState::Factories::samples`), a mixer tab, a Playlist view and
song mode, note length and velocity in the piano roll, saving, and the Oboe library for lower audio latency than
`AudioTrack` gives.
