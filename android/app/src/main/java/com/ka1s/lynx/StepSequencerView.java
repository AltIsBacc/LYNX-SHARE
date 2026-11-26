package com.ka1s.lynx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

final class StepSequencerView extends RenderSurface
{
    interface Listener
    {
        void onStep (int channel, int step);
        void onSelect (int channel);
        void onMute (int channel);
    }

    private final Paint paint = new Paint (Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float rowHeight, padWidth, cellWidth, headerHeight, gap, radius, ledZone, touchSlop;
    private final int ticksPerStep = NativeEngine.ticksPerStep();

    private volatile ProjectModel model;
    private volatile long engine;
    private volatile int selected;
    private volatile float scrollX, scrollY;
    private volatile Listener listener;

    private float downX, downY, lastX, lastY;
    private boolean scrolling;

    StepSequencerView (Context context)
    {
        super (context);
        rowHeight = Theme.dp (context, 56);
        padWidth = Theme.dp (context, 112);
        cellWidth = Theme.dp (context, 36);
        headerHeight = Theme.dp (context, 26);
        gap = Theme.dp (context, 3);
        radius = Theme.dp (context, 6);
        ledZone = Theme.dp (context, 30);
        touchSlop = ViewConfiguration.get (context).getScaledTouchSlop();
        paint.setTextAlign (Paint.Align.LEFT);
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
        if (value != null && selected >= value.channelCount())
            selected = 0;
    }

    void select (int channel)
    {
        selected = channel;
    }

    private float maxScrollX (ProjectModel m, int width)
    {
        return Math.max (0.0f, m.steps * cellWidth - (width - padWidth));
    }

    private float maxScrollY (ProjectModel m, int height)
    {
        return Math.max (0.0f, m.channelCount() * rowHeight - (height - headerHeight));
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
                    scrollY = Math.max (0.0f, Math.min (maxScrollY (m, getHeight()), scrollY - (event.getY() - lastY)));
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
        if (target == null || y < headerHeight)
            return;

        final int channel = (int) ((y - headerHeight + scrollY) / rowHeight);
        if (channel < 0 || channel >= m.channelCount())
            return;

        if (x < padWidth)
        {
            if (x < ledZone)
                target.onMute (channel);
            else
                target.onSelect (channel);
            return;
        }

        final int step = (int) ((x - padWidth + scrollX) / cellWidth);
        if (step >= 0 && step < m.steps)
            target.onStep (channel, step);
    }

    private String fit (String name, float maxWidth)
    {
        final int count = paint.breakText (name, true, maxWidth, null);
        return count >= name.length() ? name : name.substring (0, Math.max (0, count - 1)) + "…";
    }

    @Override
    void paintFrame (Canvas canvas, int width, int height)
    {
        final ProjectModel m = model;
        canvas.drawColor (Theme.background);
        if (m == null)
            return;

        final float sx = Math.min (scrollX, maxScrollX (m, width));
        final float sy = Math.min (scrollY, maxScrollY (m, height));
        final int firstRow = (int) (sy / rowHeight);
        final int lastRow = Math.min (m.channelCount() - 1, (int) ((sy + height - headerHeight) / rowHeight));
        final int firstStep = (int) (sx / cellWidth);
        final int lastStep = Math.min (m.steps - 1, (int) ((sx + width - padWidth) / cellWidth));

        canvas.save();
        canvas.clipRect (padWidth, headerHeight, width, height);
        paint.setStyle (Paint.Style.FILL);
        for (int row = firstRow; row <= lastRow; ++row)
        {
            final float top = headerHeight + row * rowHeight - sy;
            final int colour = m.colourOf (row);
            final boolean muted = m.channelMuted[row];
            for (int step = firstStep; step <= lastStep; ++step)
            {
                final float left = padWidth + step * cellWidth - sx;
                rect.set (left + gap / 2, top + gap / 2, left + cellWidth - gap / 2, top + rowHeight - gap / 2);
                if (m.stepOn (row, step))
                    paint.setColor (muted ? Theme.withAlpha (colour, 90) : colour);
                else
                    paint.setColor ((step / 4) % 2 == 0 ? Theme.panelBeat : Theme.panelRaised);
                canvas.drawRoundRect (rect, radius, radius, paint);
            }
        }

        final long handle = engine;
        if (handle != 0 && m.steps > 0)
        {
            final float x = padWidth + (float) (NativeEngine.positionTicks (handle) / ticksPerStep) * cellWidth - sx;
            paint.setColor (Theme.withAlpha (Theme.text, 40));
            canvas.drawRect (x, headerHeight, x + cellWidth, height, paint);
            paint.setColor (Theme.text);
            canvas.drawRect (x - 1.5f, headerHeight, x + 1.5f, height, paint);
        }
        canvas.restore();

        canvas.save();
        canvas.clipRect (padWidth, 0, width, headerHeight);
        paint.setTextSize (Theme.sp (getContext(), 11));
        for (int step = firstStep; step <= lastStep; ++step)
        {
            paint.setColor (step % 4 == 0 ? Theme.text : Theme.textDim);
            canvas.drawText (Integer.toString (step + 1), padWidth + step * cellWidth - sx + gap * 2, headerHeight * 0.68f, paint);
        }
        canvas.restore();

        canvas.save();
        canvas.clipRect (0, headerHeight, padWidth, height);
        paint.setColor (Theme.background);
        canvas.drawRect (0, headerHeight, padWidth, height, paint);
        paint.setTextSize (Theme.sp (getContext(), 13));
        for (int row = firstRow; row <= lastRow; ++row)
        {
            final float top = headerHeight + row * rowHeight - sy;
            final int colour = m.colourOf (row);
            final boolean muted = m.channelMuted[row];

            paint.setColor (muted ? Theme.line : Theme.accent);
            canvas.drawCircle (ledZone / 2, top + rowHeight / 2, Theme.dp (getContext(), 5), paint);

            rect.set (ledZone, top + gap / 2, padWidth - gap, top + rowHeight - gap / 2);
            paint.setStyle (Paint.Style.FILL);
            paint.setColor (muted ? Theme.withAlpha (colour, 90) : colour);
            canvas.drawRoundRect (rect, radius, radius, paint);

            if (row == selected)
            {
                paint.setStyle (Paint.Style.STROKE);
                paint.setStrokeWidth (Theme.dp (getContext(), 2));
                paint.setColor (Theme.text);
                canvas.drawRoundRect (rect, radius, radius, paint);
                paint.setStyle (Paint.Style.FILL);
            }

            paint.setColor (Theme.textOnColour);
            final float textX = rect.left + Theme.dp (getContext(), 8);
            canvas.drawText (fit (m.channelNames[row], rect.width() - Theme.dp (getContext(), 14)), textX,
                             rect.centerY() + paint.getTextSize() * 0.35f, paint);
        }
        canvas.restore();
    }
}
