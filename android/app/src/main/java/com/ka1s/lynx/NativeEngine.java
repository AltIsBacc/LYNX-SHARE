package com.ka1s.lynx;

import java.nio.ByteBuffer;

final class NativeEngine
{
    static
    {
        System.loadLibrary ("lynx_android");
    }

    private NativeEngine() {}

    static native long create (int sampleRate);
    static native void destroy (long handle);

    static native int ppq();
    static native int ticksPerStep();

    static native String loadTemplate (long handle, int index);
    static native String loadFlp (long handle, byte[] project);

    static native boolean toggleStep (long handle, int channel, int step);
    static native boolean toggleNote (long handle, int channel, int key, int step);
    static native void setTempo (long handle, double bpm);
    static native void setChannelMuted (long handle, int channel, boolean muted);

    static native void setPlaying (long handle, boolean on);
    static native void rewind (long handle);
    static native double positionTicks (long handle);

    static native void render (long handle, ByteBuffer buffer, int frames);

    static native String patternName (long handle);
    static native double bpm (long handle);
    static native long lengthTicks (long handle);
    static native int[] notes (long handle);

    static native String[] channelNames (long handle);
    static native int[] channelColours (long handle);
    static native boolean[] channelMuted (long handle);
}
