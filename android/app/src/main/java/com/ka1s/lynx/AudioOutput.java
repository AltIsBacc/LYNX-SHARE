package com.ka1s.lynx;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Plays the engine's audio: a thread that asks it for a block and writes the block to an AudioTrack. */
final class AudioOutput implements Runnable
{
    private static final int framesPerBlock = 512;
    private static final int bytesPerFrame = 2 * 4; // stereo, 32-bit float

    private final long engine;
    private final int sampleRate;
    private volatile boolean running;
    private Thread thread;
    private AudioTrack track;

    AudioOutput (long engine, int sampleRate)
    {
        this.engine = engine;
        this.sampleRate = sampleRate;
    }

    /** The device's own rate, which the engine is made for (no resampling in the way). */
    static int deviceSampleRate (Context context)
    {
        final AudioManager manager = (AudioManager) context.getSystemService (Context.AUDIO_SERVICE);
        final String rate = manager.getProperty (AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        try
        {
            return rate != null ? Integer.parseInt (rate) : 48000;
        }
        catch (NumberFormatException e)
        {
            return 48000;
        }
    }

    synchronized void start()
    {
        if (running)
            return;

        final AudioFormat format = new AudioFormat.Builder()
                .setSampleRate (sampleRate)
                .setEncoding (AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask (AudioFormat.CHANNEL_OUT_STEREO)
                .build();
        final int minimum = AudioTrack.getMinBufferSize (sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);

        track = new AudioTrack.Builder()
                .setAudioAttributes (new AudioAttributes.Builder()
                        .setUsage (AudioAttributes.USAGE_MEDIA)
                        .setContentType (AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat (format)
                .setBufferSizeInBytes (Math.max (minimum, 2 * framesPerBlock * bytesPerFrame))
                .setTransferMode (AudioTrack.MODE_STREAM)
                .setPerformanceMode (AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build();

        running = true;
        thread = new Thread (this, "LYNX audio");
        thread.setPriority (Thread.MAX_PRIORITY);
        thread.start();
    }

    /** Returns once the audio thread has ended: the engine is not in use after it. */
    synchronized void stop()
    {
        if (! running)
            return;

        running = false;
        boolean joined = false;
        while (! joined)
        {
            try
            {
                thread.join();
                joined = true;
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
        thread = null;
    }

    @Override
    public void run()
    {
        final ByteBuffer block = ByteBuffer.allocateDirect (framesPerBlock * bytesPerFrame).order (ByteOrder.nativeOrder());
        track.play();

        while (running)
        {
            block.clear();
            NativeEngine.render (engine, block, framesPerBlock);
            block.clear();
            if (track.write (block, block.capacity(), AudioTrack.WRITE_BLOCKING) < 0)
                break; // the track is gone
        }

        track.stop();
        track.release();
        track = null;
    }
}
