package com.ka1s.lynx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

final class PianoRollView extends RenderSurface
{
    interface Listener
    {
        void onNote (int key, int step);
    }

    private static final int keyCount = 128;
    private static final String[] noteNames = { "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B" };

    private final Paint paint = new Paint (Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float keyHeight, keyboardWidth, cellWidth, headerHeight, radius, touchSlop;
    private final int ticksPerStep = NativeEngine.ticksPerStep();

    private volatile ProjectModel model;
    private volatile long engine;
    private volatile int channel;
    private volatile float scrollX, scrollY;
    private volatile boolean needsCentering = true;
    private volatile Listener listener;

    private float downX, downY, lastX, lastY;
    private boolean scrolling;

    PianoRollView (Context context)
    {
        super (context);
        keyHeight = Theme.dp (context, 24);
        keyboardWidth = Theme.dp (context, 64);
        cellWidth = Theme.dp (context, 36);
        headerHeight = Theme.dp (context, 26);
        radius = Theme.dp (context, 4);
        touchSlop = ViewConfiguration.get (context).getScaledTouchSlop();
    }

    void attach (long handle)
    {
        engine = handle;
    }

    void setListener (Listener value)
    {
        listener = value;
    }

    void show (ProjectModel value)
    {
        model = value;
    }

    void showChannel (int value)
    {
        if (value != channel)
            needsCentering = true;
        channel = value;
    }

    private static boolean isBlack (int key)
    {
        switch (key % 12)
        {
            case 1: case 3: case 6: case 8: case 10:
                return true;
            default:
                return false;
        }
    }

    private float maxScrollX (ProjectModel m, int width)
    {
        return Math.max (0.0f, m.steps * cellWidth - (width - keyboardWidth));
    }

    private float maxScrollY (int height)
    {
        return Math.max (0.0f, keyCount * keyHeight - (height - headerHeight));
    }

    private int keyAt (float y, float sy)
    {
        return keyCount - 1 - (int) ((y - headerHeight + sy) / keyHeight);
    }

    @Override
    public boolean onTouchEvent (MotionEvent event)
    {
        final ProjectModel m = model;
        if (m == null)
            return false;

        switch (event.getActionMasked())
        {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                scrolling = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                if (! scrolling && Math.hypot (event.getX() - downX, event.getY() - downY) > touchSlop)
                    scrolling = true;
                if (scrolling)
                {
                    scrollX = Math.max (0.0f, Math.min (maxScrollX (m, getWidth()), scrollX - (event.getX() - lastX)));
                    scrollY = Math.max (0.0f, Math.min (maxScrollY (getHeight()), scrollY - (event.getY() - lastY)));
                }
                lastX = event.getX();
                lastY = event.getY();
                return true;

            case MotionEvent.ACTION_UP:
                if (! scrolling)
                    tap (m, event.getX(), event.getY());
                return true;

            default:
                return super.onTouchEvent (event);
        }
    }

    private void tap (ProjectModel m, float x, float y)
    {
        final Listener target = listener;
        if (target == null || x < keyboardWidth || y < headerHeight)
            return;

        final int key = keyAt (y, scrollY);
        final int step = (int) ((x - keyboardWidth + scrollX) / cellWidth);
        if (key >= 0 && key < keyCount && step >= 0 && step < m.steps)
            target.onNote (key, step);
    }

    @Override
    void paintFrame (Canvas canvas, int width, int height)
    {
        final ProjectModel m = model;
        canvas.drawColor (Theme.background);
        if (m == null || m.channelCount() == 0)
            return;

        if (needsCentering)
        {
            final int centre = m.averageKey (channel);
            scrollY = Math.max (0.0f, Math.min (maxScrollY (height), (keyCount - 1 - centre) * keyHeight - height / 2.0f));
            needsCentering = false;
        }

        final float sx = Math.min (scrollX, maxScrollX (m, width));
        final float sy = Math.min (scrollY, maxScrollY (height));
        final int topKey = Math.min (keyCount - 1, keyCount - 1 - (int) (sy / keyHeight));
        final int bottomKey = Math.max (0, keyCount - 1 - (int) ((sy + height - headerHeight) / keyHeight));
        final int firstStep = (int) (sx / cellWidth);
        final int lastStep = Math.min (m.steps - 1, (int) ((sx + width - keyboardWidth) / cellWidth));
        final int selected = Math.min (channel, m.channelCount() - 1);

        canvas.save();
        canvas.clipRect (keyboardWidth, headerHeight, width, height);
        paint.setStyle (Paint.Style.FILL);
        for (int key = bottomKey; key <= topKey; ++key)
        {
            final float top = headerHeight + (keyCount - 1 - key) * keyHeight - sy;
            paint.setColor (isBlack (key) ? Theme.panel : Theme.panelRaised);
            canvas.drawRect (keyboardWidth, top, width, top + keyHeight, paint);
            paint.setColor (Theme.line);
            canvas.drawRect (keyboardWidth, top + keyHeight - 1, width, top + keyHeight, paint);
        }
        for (int step = firstStep; step <= lastStep + 1; ++step)
        {
            final float x = keyboardWidth + step * cellWidth - sx;
            paint.setColor (step % 16 == 0 ? Theme.textDim : step % 4 == 0 ? Theme.line : Theme.panelRaised);
            canvas.drawRect (x, headerHeight, x + 1, height, paint);
        }

        for (int i = 0; i + 3 < m.notes.length; i += 4)
        {
            final int owner = m.notes[i + 3];
            final int key = m.notes[i + 2];
            if (owner == selected || key < bottomKey || key > topKey)
                continue;
            drawNote (canvas, m, i, sx, sy, Theme.withAlpha (m.colourOf (owner), 55), key);
        }
        for (int i = 0; i + 3 < m.notes.length; i += 4)
        {
            final int key = m.notes[i + 2];
            if (m.notes[i + 3] == selected && key >= bottomKey && key <= topKey)
                drawNote (canvas, m, i, sx, sy, m.colourOf (selected), key);
        }

        final long handle = engine;
        if (handle != 0)
        {
            final float x = keyboardWidth + (float) (NativeEngine.positionTicks (handle) / ticksPerStep) * cellWidth - sx;
            paint.setColor (Theme.text);
            canvas.drawRect (x - 1.5f, headerHeight, x + 1.5f, height, paint);
        }
        canvas.restore();

        canvas.save();
        canvas.clipRect (keyboardWidth, 0, width, headerHeight);
        paint.setTextSize (Theme.sp (getContext(), 11));
        for (int step = firstStep; step <= lastStep; ++step)
        {
            if (step % 4 != 0)
                continue;
            paint.setColor (step % 16 == 0 ? Theme.text : Theme.textDim);
            canvas.drawText (Integer.toString (step / 4 + 1), keyboardWidth + step * cellWidth - sx + 6, headerHeight * 0.68f, paint);
        }
        canvas.restore();

        canvas.save();
        canvas.clipRect (0, headerHeight, keyboardWidth, height);
        paint.setTextSize (Theme.sp (getContext(), 10));
        for (int key = bottomKey; key <= topKey; ++key)
        {
            final float top = headerHeight + (keyCount - 1 - key) * keyHeight - sy;
            paint.setStyle (Paint.Style.FILL);
            paint.setColor (isBlack (key) ? Theme.blackKey : Theme.whiteKey);
            rect.set (0, top, isBlack (key) ? keyboardWidth * 0.62f : keyboardWidth, top + keyHeight);
            if (isBlack (key))
            {
                paint.setColor (Theme.whiteKey);
                canvas.drawRect (0, top, keyboardWidth, top + keyHeight, paint);
                paint.setColor (Theme.blackKey);
            }
            canvas.drawRect (rect, paint);
            paint.setColor (Theme.line);
            canvas.drawRect (0, top + keyHeight - 1, keyboardWidth, top + keyHeight, paint);
            if (key % 12 == 0)
            {
                paint.setColor (Theme.textOnColour);
                canvas.drawText (noteNames[0] + (key / 12 - 1), keyboardWidth - Theme.dp (getContext(), 24),
                                 top + keyHeight * 0.68f, paint);
            }
        }
        canvas.restore();
    }

    private void drawNote (Canvas canvas, ProjectModel m, int index, float sx, float sy, int colour, int key)
    {
        final float top = headerHeight + (keyCount - 1 - key) * keyHeight - sy;
        final float left = keyboardWidth + (float) m.notes[index] / ticksPerStep * cellWidth - sx;
        final float right = left + Math.max (6.0f, (float) m.notes[index + 1] / ticksPerStep * cellWidth);
        rect.set (left + 1, top + 2, right - 1, top + keyHeight - 2);
        paint.setStyle (Paint.Style.FILL);
        paint.setColor (colour);
        canvas.drawRoundRect (rect, radius, radius, paint);
    }
}
