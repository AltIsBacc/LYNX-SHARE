package com.ka1s.lynx;

import android.content.Context;
import android.graphics.Color;

final class Theme
{
    static final int background = Color.rgb (21, 23, 27);
    static final int panel = Color.rgb (31, 34, 40);
    static final int panelRaised = Color.rgb (42, 46, 54);
    static final int panelBeat = Color.rgb (50, 55, 64);
    static final int line = Color.rgb (58, 63, 73);
    static final int text = Color.rgb (233, 235, 240);
    static final int textDim = Color.rgb (139, 146, 160);
    static final int textOnColour = Color.rgb (24, 20, 16);
    static final int accent = Color.rgb (255, 143, 46);
    static final int accentDim = Color.rgb (112, 66, 28);
    static final int record = Color.rgb (232, 64, 64);
    static final int whiteKey = Color.rgb (226, 229, 235);
    static final int blackKey = Color.rgb (36, 39, 46);

    private static final int[] palette =
    {
        Color.rgb (255, 143, 46),
        Color.rgb (255, 90, 95),
        Color.rgb (255, 200, 61),
        Color.rgb (122, 214, 108),
        Color.rgb (64, 201, 198),
        Color.rgb (92, 160, 255),
        Color.rgb (170, 128, 255),
        Color.rgb (255, 120, 200)
    };

    private Theme() {}

    static int channelColour (int index, int stored)
    {
        if (stored != 0)
            return stored | 0xFF000000;
        return palette[Math.floorMod (index, palette.length)];
    }

    static int withAlpha (int colour, int alpha)
    {
        return Color.argb (alpha, Color.red (colour), Color.green (colour), Color.blue (colour));
    }

    static float dp (Context context, float value)
    {
        return value * context.getResources().getDisplayMetrics().density;
    }

    static float sp (Context context, float value)
    {
        return value * context.getResources().getDisplayMetrics().scaledDensity;
    }
}
