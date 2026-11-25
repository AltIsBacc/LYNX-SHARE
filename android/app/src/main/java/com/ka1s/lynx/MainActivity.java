package com.ka1s.lynx;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * LYNX SHARE's one screen on Android: play the current pattern of a template or of an .flp project, with the pattern
 * drawn into a SurfaceView (PatternView) and its audio coming from the engine (android/native) through AudioOutput.
 */
public final class MainActivity extends Activity
{
    private static final int openProjectRequest = 1;
    private static final int basicBeatTemplate = 1;

    private long engine;
    private AudioOutput audio;
    private PatternView patternView;
    private TextView info;
    private Button playButton;
    private boolean playing;

    // every load runs here, one after the other: a project is handed to the audio thread from one thread only
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;

    @Override
    protected void onCreate (Bundle savedInstanceState)
    {
        super.onCreate (savedInstanceState);

        final int sampleRate = AudioOutput.deviceSampleRate (this);
        engine = NativeEngine.create (sampleRate);
        audio = new AudioOutput (engine, sampleRate);

        playButton = new Button (this);
        playButton.setOnClickListener (v -> setPlaying (! playing));

        final Button open = new Button (this);
        open.setText (R.string.open_flp);
        open.setOnClickListener (v -> {
            final Intent intent = new Intent (Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory (Intent.CATEGORY_OPENABLE);
            intent.setType ("*/*");
            startActivityForResult (intent, openProjectRequest);
        });

        final Button template = new Button (this);
        template.setText (R.string.new_from_template);
        template.setOnClickListener (v -> new AlertDialog.Builder (this)
                .setTitle (R.string.new_from_template)
                .setItems (R.array.templates, (dialog, which) -> loadTemplate (which))
                .show());

        final LinearLayout buttons = new LinearLayout (this);
        buttons.setOrientation (LinearLayout.HORIZONTAL);
        buttons.addView (playButton);
        buttons.addView (open);
        buttons.addView (template);

        info = new TextView (this);
        info.setGravity (Gravity.CENTER_VERTICAL);
        info.setPadding (24, 8, 24, 8);

        patternView = new PatternView (this);
        patternView.attach (engine);

        final LinearLayout root = new LinearLayout (this);
        root.setOrientation (LinearLayout.VERTICAL);
        root.setFitsSystemWindows (true);
        root.addView (buttons);
        root.addView (info);
        root.addView (patternView, new LinearLayout.LayoutParams (LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        setContentView (root);

        showPlaying (false);
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
        patternView.attach (0);
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

        // (a load that has not ended still uses the engine: leave it to the process rather than crash it)
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
        load (() -> {
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

    private void loadTemplate (int index)
    {
        load (() -> NativeEngine.loadTemplate (engine, index));
    }

    /** Runs `work` (a NativeEngine.load call: null when it worked, else the error) off the UI thread, then shows the pattern. */
    private void load (Supplier<String> work)
    {
        loader.execute (() -> {
            final String error = work.get();
            final int[] notes = NativeEngine.notes (engine);
            final long length = NativeEngine.lengthTicks (engine);
            final String name = NativeEngine.patternName (engine);
            final double bpm = NativeEngine.bpm (engine);

            runOnUiThread (() -> {
                if (destroyed)
                    return;
                if (error != null)
                {
                    Toast.makeText (this, getString (R.string.load_failed, error), Toast.LENGTH_LONG).show();
                    return;
                }
                patternView.show (notes, length);
                info.setText (getString (R.string.pattern_info, name, bpm));
            });
        });
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
        showPlaying (on);
    }

    private void showPlaying (boolean on)
    {
        playButton.setText (on ? R.string.stop : R.string.play);
    }
}
