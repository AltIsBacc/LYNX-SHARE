package com.ka1s.lynx;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class MainActivity extends Activity
{
    private static final int openProjectRequest = 1;
    private static final int basicBeatTemplate = 1;
    private static final int rackTab = 0;
    private static final int rollTab = 1;

    private long engine;
    private AudioOutput audio;
    private TransportBar transport;
    private StepSequencerView rack;
    private PianoRollView roll;
    private TabBar tabs;
    private boolean playing;
    private int selectedChannel;

    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private volatile ProjectModel current;
    private volatile boolean destroyed;

    @Override
    protected void onCreate (Bundle savedInstanceState)
    {
        super.onCreate (savedInstanceState);

        final int sampleRate = AudioOutput.deviceSampleRate (this);
        engine = NativeEngine.create (sampleRate);
        audio = new AudioOutput (engine, sampleRate);

        transport = new TransportBar (this);
        transport.setListener (new TransportBar.Listener()
        {
            @Override public void onMenu() { showMenu(); }
            @Override public void onPlay() { setPlaying (! playing); }
            @Override public void onTempo (double bpm) { edit (() -> NativeEngine.setTempo (engine, bpm)); }
        });

        rack = new StepSequencerView (this);
        rack.attach (engine);
        rack.setListener (new StepSequencerView.Listener()
        {
            @Override public void onStep (int channel, int step)
            {
                edit (() -> NativeEngine.toggleStep (engine, channel, step));
            }

            @Override public void onSelect (int channel)
            {
                selectChannel (channel);
            }

            @Override public void onMute (int channel)
            {
                final ProjectModel model = current;
                if (model != null && channel < model.channelCount())
                    edit (() -> NativeEngine.setChannelMuted (engine, channel, ! model.channelMuted[channel]));
            }
        });

        roll = new PianoRollView (this);
        roll.attach (engine);
        roll.setListener ((key, step) ->
        {
            final int channel = selectedChannel;
            edit (() -> NativeEngine.toggleNote (engine, channel, key, step));
        });
        roll.setVisibility (FrameLayout.GONE);

        final FrameLayout content = new FrameLayout (this);
        content.addView (rack, new FrameLayout.LayoutParams (FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        content.addView (roll, new FrameLayout.LayoutParams (FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        tabs = new TabBar (this, new String[] { getString (R.string.tab_rack), getString (R.string.tab_roll) });
        tabs.setListener (this::showTab);

        final LinearLayout root = new LinearLayout (this);
        root.setOrientation (LinearLayout.VERTICAL);
        root.setBackgroundColor (Theme.background);
        root.setFitsSystemWindows (true);
        root.addView (transport);
        root.addView (content, new LinearLayout.LayoutParams (LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        root.addView (tabs);
        setContentView (root);

        transport.setPlaying (false);
        loadTemplate (basicBeatTemplate);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        audio.start();
    }

    @Override
    protected void onPause()
    {
        setPlaying (false);
        audio.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy()
    {
        destroyed = true;
        rack.attach (0);
        roll.attach (0);
        loader.shutdown();
        boolean idle = false;
        try
        {
            idle = loader.awaitTermination (5, TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

        if (idle)
            NativeEngine.destroy (engine);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult (requestCode, resultCode, data);
        if (requestCode != openProjectRequest || resultCode != RESULT_OK || data == null || data.getData() == null)
            return;

        final Uri uri = data.getData();
        load (() ->
        {
            try
            {
                return NativeEngine.loadFlp (engine, readAll (uri));
            }
            catch (IOException e)
            {
                return e.getMessage() != null ? e.getMessage() : e.toString();
            }
        });
    }

    private void showMenu()
    {
        new AlertDialog.Builder (this)
                .setItems (new String[] { getString (R.string.open_flp), getString (R.string.new_from_template) }, (dialog, which) ->
                {
                    if (which == 0)
                        openProject();
                    else
                        showTemplates();
                })
                .show();
    }

    private void openProject()
    {
        final Intent intent = new Intent (Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory (Intent.CATEGORY_OPENABLE);
        intent.setType ("*/*");
        startActivityForResult (intent, openProjectRequest);
    }

    private void showTemplates()
    {
        new AlertDialog.Builder (this)
                .setTitle (R.string.new_from_template)
                .setItems (R.array.templates, (dialog, which) -> loadTemplate (which))
                .show();
    }

    private void showTab (int index)
    {
        rack.setVisibility (index == rackTab ? FrameLayout.VISIBLE : FrameLayout.GONE);
        roll.setVisibility (index == rollTab ? FrameLayout.VISIBLE : FrameLayout.GONE);
    }

    private void selectChannel (int channel)
    {
        selectedChannel = channel;
        rack.select (channel);
        roll.showChannel (channel);
    }

    private void loadTemplate (int index)
    {
        load (() -> NativeEngine.loadTemplate (engine, index));
    }

    private void load (Supplier<String> work)
    {
        loader.execute (() ->
        {
            final String error = work.get();
            final ProjectModel model = ProjectModel.read (engine);
            runOnUiThread (() ->
            {
                if (destroyed)
                    return;
                if (error != null)
                {
                    Toast.makeText (this, getString (R.string.load_failed, error), Toast.LENGTH_LONG).show();
                    return;
                }
                selectChannel (0);
                apply (model);
            });
        });
    }

    private void edit (Runnable work)
    {
        loader.execute (() ->
        {
            work.run();
            final ProjectModel model = ProjectModel.read (engine);
            runOnUiThread (() ->
            {
                if (! destroyed)
                    apply (model);
            });
        });
    }

    private void apply (ProjectModel model)
    {
        current = model;
        rack.show (model);
        roll.show (model);
        transport.setTempo (model.bpm);
        transport.setTitle (model.patternName);
    }

    private byte[] readAll (Uri uri) throws IOException
    {
        try (InputStream in = getContentResolver().openInputStream (uri))
        {
            if (in == null)
                throw new IOException (uri.toString());

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] chunk = new byte[64 * 1024];
            for (int n; (n = in.read (chunk)) > 0;)
                out.write (chunk, 0, n);
            return out.toByteArray();
        }
    }

    private void setPlaying (boolean on)
    {
        playing = on;
        NativeEngine.setPlaying (engine, on);
        if (! on)
            NativeEngine.rewind (engine);
        transport.setPlaying (on);
    }
}
