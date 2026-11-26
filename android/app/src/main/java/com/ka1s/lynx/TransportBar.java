package com.ka1s.lynx;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

final class TransportBar extends LinearLayout
{
    interface Listener
    {
        void onMenu();
        void onPlay();
        void onTempo (double bpm);
    }

    private static final double tempoStep = 1.0;

    private final TextView play;
    private final TextView tempo;
    private final TextView title;
    private double bpm = 120.0;
    private Listener listener;

    TransportBar (Context context)
    {
        super (context);
        setOrientation (HORIZONTAL);
        setGravity (Gravity.CENTER_VERTICAL);
        setBackgroundColor (Theme.panel);
        final int pad = (int) Theme.dp (context, 8);
        setPadding (pad, pad, pad, pad);

        final TextView menu = button (context, "☰", Theme.panelRaised, Theme.text);
        menu.setOnClickListener (v -> { if (listener != null) listener.onMenu(); });

        play = button (context, "▶︎", Theme.panelRaised, Theme.accent);
        play.setOnClickListener (v -> { if (listener != null) listener.onPlay(); });

        final TextView down = button (context, "−", Theme.panelRaised, Theme.text);
        down.setOnClickListener (v -> changeTempo (-tempoStep));

        final TextView up = button (context, "+", Theme.panelRaised, Theme.text);
        up.setOnClickListener (v -> changeTempo (tempoStep));

        tempo = new TextView (context);
        tempo.setTextColor (Theme.accent);
        tempo.setTextSize (22);
        tempo.setTypeface (Typeface.MONOSPACE, Typeface.BOLD);
        tempo.setGravity (Gravity.CENTER);
        tempo.setMinWidth ((int) Theme.dp (context, 76));
        tempo.setBackground (rounded (context, Theme.background));

        title = new TextView (context);
        title.setTextColor (Theme.textDim);
        title.setTextSize (14);
        title.setSingleLine();
        title.setPadding (pad * 2, 0, 0, 0);

        final int gap = (int) Theme.dp (context, 6);
        addView (menu, params (context, gap));
        addView (play, params (context, gap * 2));
        addView (down, params (context, 0));
        addView (tempo, new LayoutParams ((int) Theme.dp (context, 84), (int) Theme.dp (context, 44)));
        addView (up, params (context, gap));
        addView (title, new LayoutParams (0, LayoutParams.WRAP_CONTENT, 1.0f));
        refreshTempo();
    }

    void setListener (Listener value)
    {
        listener = value;
    }

    void setPlaying (boolean on)
    {
        play.setText (on ? "■" : "▶︎");
        play.setTextColor (on ? Theme.textOnColour : Theme.accent);
        play.setBackground (rounded (getContext(), on ? Theme.accent : Theme.panelRaised));
    }

    void setTempo (double value)
    {
        bpm = value;
        refreshTempo();
    }

    void setTitle (String value)
    {
        title.setText (value);
    }

    private void changeTempo (double delta)
    {
        bpm = Math.max (40.0, Math.min (300.0, Math.round (bpm) + delta));
        refreshTempo();
        if (listener != null)
            listener.onTempo (bpm);
    }

    private void refreshTempo()
    {
        tempo.setText (String.format (Locale.US, "%.1f", bpm));
    }

    private static LayoutParams params (Context context, int rightMargin)
    {
        final LayoutParams result = new LayoutParams ((int) Theme.dp (context, 44), (int) Theme.dp (context, 44));
        result.rightMargin = rightMargin;
        return result;
    }

    private static GradientDrawable rounded (Context context, int colour)
    {
        final GradientDrawable shape = new GradientDrawable();
        shape.setColor (colour);
        shape.setCornerRadius (Theme.dp (context, 10));
        return shape;
    }

    private static TextView button (Context context, String label, int background, int colour)
    {
        final TextView view = new TextView (context);
        view.setText (label);
        view.setTextColor (colour);
        view.setTextSize (20);
        view.setGravity (Gravity.CENTER);
        view.setBackground (rounded (context, background));
        return view;
    }
}
