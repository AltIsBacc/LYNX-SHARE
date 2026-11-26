package com.ka1s.lynx;

import android.content.Context;
import android.graphics.Canvas;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

abstract class RenderSurface extends SurfaceView implements SurfaceHolder.Callback, Runnable
{
    private static final int frameMilliseconds = 16;

    private volatile boolean drawing;
    private Thread thread;

    RenderSurface (Context context)
    {
        super (context);
        getHolder().addCallback (this);
    }

    abstract void paintFrame (Canvas canvas, int width, int height);

    @Override
    public void surfaceCreated (SurfaceHolder holder)
    {
        drawing = true;
        thread = new Thread (this, "LYNX " + getClass().getSimpleName());
        thread.start();
    }

    @Override
    public void surfaceChanged (SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed (SurfaceHolder holder)
    {
        drawing = false;
        while (thread != null)
        {
            try
            {
                thread.join();
                thread = null;
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
                    paintFrame (canvas, canvas.getWidth(), canvas.getHeight());
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
}
