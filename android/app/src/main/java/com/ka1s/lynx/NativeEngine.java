package com.ka1s.lynx;

import java.nio.ByteBuffer;

/**
 * The engine's native calls (android/native/Jni.cpp). A handle comes from create() and goes to destroy() once the
 * threads that use it have stopped. load*() run on one thread (the app's loader); render() on the audio thread; the
 * rest on any thread.
 */
final class NativeEngine
{
    static
    {
        System.loadLibrary ("lynx_android");
    }

    private NativeEngine() {}

    static native long create (int sampleRate);
    static native void destroy (long handle);

    /** Ticks in a quarter note (core/Song.h). */
    static native int ppq();

    /** null when it worked, else what went wrong. */
    static native String loadTemplate (long handle, int index);
    static native String loadFlp (long handle, byte[] project);

    static native void setPlaying (long handle, boolean on);
    static native void rewind (long handle);
    static native double positionTicks (long handle);

    /** Fills a direct buffer with `frames` interleaved stereo float frames. */
    static native void render (long handle, ByteBuffer buffer, int frames);

    static native String patternName (long handle);
    static native double bpm (long handle);
    static native long lengthTicks (long handle);

    /** start, length, key and channel (all ticks or numbers) of every note of the playing pattern, one after the other. */
    static native int[] notes (long handle);
}
