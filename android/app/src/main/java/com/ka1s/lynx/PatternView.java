package com.ka1s.lynx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Draws the playing pattern, as a piano roll (a bar for each note) with the playhead moving over it, straight into a
 * SurfaceView's surface from a thread of its own, so drawing never waits for the UI thread.
 */
final class PatternView extends SurfaceView implements SurfaceHolder.Callback, Runnable
{
    private static final int frameMilliseconds = 16;
    private static final int minimumKeys = 12; // an empty or one-note pattern still shows an octave

    private final Paint paint = new Paint();
    private final float[] hsv = new float[] { 0.0f, 0.55f, 0.95f };
    private final int ppq = NativeEngine.ppq();

    private volatile long engine; // 0 = none yet
    private volatile int[] notes = new int[0];
    private volatile long lengthTicks = 1;

    private volatile boolean drawing;
    private Thread thread;

    PatternView (Context context)
    {
        super (context);
        getHolder().addCallback (this);
    }

    /** The engine whose playhead is drawn (set before the surface exists; 0 when it is about to go). */
    void attach (long handle)
    {
        engine = handle;
    }

    /** What to draw: NativeEngine.notes() and lengthTicks(). */
    void show (int[] noteList, long length)
    {
        lengthTicks = Math.max (1, length);
        notes = noteList;
    }

    @Override
    public void surfaceCreated (SurfaceHolder holder)
    {
        drawing = true;
        thread = new Thread (this, "LYNX pattern view");
        thread.start();
    }

    @Override
    public void surfaceChanged (SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed (SurfaceHolder holder)
    {
        drawing = false;
        boolean joined = false;
        while (! joined && thread != null)
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
        final SurfaceHolder holder = getHolder();

        while (drawing)
        {
            final Canvas canvas = holder.lockCanvas();
            if (canvas != null)
            {
                try
                {
                    drawPattern (canvas, engine);
                }
                finally
                {
                    holder.unlockCanvasAndPost (canvas);
                }
            }

            try
            {
                Thread.sleep (frameMilliseconds);
            }
            catch (InterruptedException e)
            {
                break;
            }
        }
    }

    private void drawPattern (Canvas canvas, long handle)
    {
        final int width = canvas.getWidth(), height = canvas.getHeight();
        final int[] list = notes;
        final double length = lengthTicks;
        canvas.drawColor (Color.rgb (24, 26, 31));

        int lowest = Integer.MAX_VALUE, highest = Integer.MIN_VALUE;
        for (int i = 0; i + 3 < list.length; i += 4)
        {
            lowest = Math.min (lowest, list[i + 2]);
            highest = Math.max (highest, list[i + 2]);
        }
        if (lowest > highest)
            lowest = highest = 60;
        if (highest - lowest + 1 < minimumKeys)
        {
            final int middle = (lowest + highest) / 2;
            lowest = middle - minimumKeys / 2;
            highest = lowest + minimumKeys - 1;
        }
        final float keyHeight = (float) height / (highest - lowest + 1);

        // a line at every beat
        paint.setColor (Color.rgb (44, 47, 55));
        paint.setStrokeWidth (1.0f);
        for (long tick = 0; tick < length; tick += ppq)
        {
            final float x = (float) (tick / length * width);
            canvas.drawLine (x, 0.0f, x, height, paint);
        }

        paint.setStyle (Paint.Style.FILL);
        for (int i = 0; i + 3 < list.length; i += 4)
        {
            hsv[0] = (list[i + 3] * 47) % 360; // a hue for each channel
            paint.setColor (Color.HSVToColor (hsv));
            final float left = (float) (list[i] / length * width);
            final float right = Math.max (left + 3.0f, (float) ((list[i] + list[i + 1]) / length * width));
            final float top = (highest - list[i + 2]) * keyHeight;
            canvas.drawRect (left, top + 1.0f, right, top + keyHeight - 1.0f, paint);
        }

        if (handle != 0)
        {
            final float x = (float) (NativeEngine.positionTicks (handle) / length * width);
            paint.setColor (Color.WHITE);
            paint.setStrokeWidth (3.0f);
            canvas.drawLine (x, 0.0f, x, height, paint);
        }
    }
}
