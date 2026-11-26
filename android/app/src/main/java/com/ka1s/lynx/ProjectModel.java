package com.ka1s.lynx;

final class ProjectModel
{
    final String patternName;
    final double bpm;
    final long lengthTicks;
    final int[] notes;
    final String[] channelNames;
    final int[] channelColours;
    final boolean[] channelMuted;
    final int steps;
    private final boolean[][] stepGrid;

    private ProjectModel (String patternName, double bpm, long lengthTicks, int[] notes,
                          String[] channelNames, int[] channelColours, boolean[] channelMuted)
    {
        this.patternName = patternName;
        this.bpm = bpm;
        this.lengthTicks = lengthTicks;
        this.notes = notes;
        this.channelNames = channelNames;
        this.channelColours = channelColours;
        this.channelMuted = channelMuted;

        final int ticksPerStep = NativeEngine.ticksPerStep();
        steps = (int) (lengthTicks / ticksPerStep);
        stepGrid = new boolean[channelNames.length][steps];
        for (int i = 0; i + 3 < notes.length; i += 4)
        {
            final int channel = notes[i + 3];
            final int step = notes[i] / ticksPerStep;
            if (notes[i] % ticksPerStep == 0 && channel >= 0 && channel < channelNames.length && step < steps)
                stepGrid[channel][step] = true;
        }
    }

    static ProjectModel read (long engine)
    {
        return new ProjectModel (NativeEngine.patternName (engine),
                                 NativeEngine.bpm (engine),
                                 NativeEngine.lengthTicks (engine),
                                 NativeEngine.notes (engine),
                                 NativeEngine.channelNames (engine),
                                 NativeEngine.channelColours (engine),
                                 NativeEngine.channelMuted (engine));
    }

    int channelCount()
    {
        return channelNames.length;
    }

    boolean stepOn (int channel, int step)
    {
        return channel >= 0 && channel < stepGrid.length && step >= 0 && step < steps && stepGrid[channel][step];
    }

    int colourOf (int channel)
    {
        return Theme.channelColour (channel, channelColours[channel]);
    }

    int averageKey (int channel)
    {
        long sum = 0;
        int count = 0;
        for (int i = 0; i + 3 < notes.length; i += 4)
        {
            if (notes[i + 3] == channel)
            {
                sum += notes[i + 2];
                ++count;
            }
        }
        return count == 0 ? 60 : (int) (sum / count);
    }
}
