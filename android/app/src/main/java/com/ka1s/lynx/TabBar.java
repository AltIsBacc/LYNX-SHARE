package com.ka1s.lynx;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

final class TabBar extends LinearLayout
{
    interface Listener
    {
        void onTab (int index);
    }

    private final TextView[] tabs;
    private Listener listener;

    TabBar (Context context, String[] labels)
    {
        super (context);
        setOrientation (HORIZONTAL);
        setBackgroundColor (Theme.panel);
        tabs = new TextView[labels.length];

        for (int i = 0; i < labels.length; ++i)
        {
            final int index = i;
            final TextView tab = new TextView (context);
            tab.setText (labels[i]);
            tab.setTextSize (14);
            tab.setGravity (Gravity.CENTER);
            tab.setAllCaps (true);
            tab.setLetterSpacing (0.06f);
            tab.setOnClickListener (v ->
            {
                select (index);
                if (listener != null)
                    listener.onTab (index);
            });
            tabs[i] = tab;
            addView (tab, new LayoutParams (0, (int) Theme.dp (context, 52), 1.0f));
        }
        select (0);
    }

    void setListener (Listener value)
    {
        listener = value;
    }

    void select (int index)
    {
        for (int i = 0; i < tabs.length; ++i)
        {
            final boolean on = i == index;
            tabs[i].setTextColor (on ? Theme.accent : Theme.textDim);
            tabs[i].setBackgroundColor (on ? Theme.panelRaised : Theme.panel);
        }
    }
}
