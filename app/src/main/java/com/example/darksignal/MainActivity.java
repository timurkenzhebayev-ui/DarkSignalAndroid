package com.example.darksignal;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

public class MainActivity extends Activity implements HorrorRenderer.GameEvents {
    private HorrorGLView glView;
    private HudView hudView;
    private MediaPlayer ambient;
    private SoundPool soundPool;
    private int pickupSound;
    private int scareSound;
    private int winSound;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        setupAudio();

        HorrorRenderer renderer = new HorrorRenderer(this);
        glView = new HorrorGLView(this, renderer);
        hudView = new HudView(this, renderer);

        FrameLayout root = new FrameLayout(this);
        root.addView(glView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(hudView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Important on Android 16 / newer Samsung firmware:
        // apply immersive mode only after the DecorView has been created.
        hideSystemUi();
    }

    private void setupAudio() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build();
        pickupSound = soundPool.load(this, R.raw.pickup, 1);
        scareSound = soundPool.load(this, R.raw.scare, 1);
        winSound = soundPool.load(this, R.raw.win, 1);

        ambient = MediaPlayer.create(this, R.raw.ambient);
        if (ambient != null) {
            ambient.setLooping(true);
            ambient.setVolume(0.45f, 0.45f);
        }
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            View decor = getWindow().getDecorView();
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void vibrate(long ms, int amplitude) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, amplitude));
        } else {
            vibrator.vibrate(ms);
        }
    }

    @Override
    public void onPickup() {
        runOnUiThread(() -> {
            soundPool.play(pickupSound, 0.8f, 0.8f, 1, 0, 1f);
            vibrate(45, 90);
            hudView.invalidate();
        });
    }

    @Override
    public void onMonsterAwake() {
        runOnUiThread(() -> {
            soundPool.play(scareSound, 0.45f, 0.45f, 1, 0, 0.65f);
            vibrate(110, 120);
            hudView.invalidate();
        });
    }

    @Override
    public void onDeath() {
        runOnUiThread(() -> {
            soundPool.play(scareSound, 1f, 1f, 1, 0, 0.8f);
            vibrate(260, 220);
            if (ambient != null) ambient.setVolume(0.12f, 0.12f);
            hudView.invalidate();
        });
    }

    @Override
    public void onWin() {
        runOnUiThread(() -> {
            soundPool.play(winSound, 1f, 1f, 1, 0, 1f);
            vibrate(120, 130);
            if (ambient != null) ambient.setVolume(0.22f, 0.22f);
            hudView.invalidate();
        });
    }

    @Override
    public void onGameStart() {
        runOnUiThread(() -> {
            if (ambient != null) {
                ambient.setVolume(0.45f, 0.45f);
                if (!ambient.isPlaying()) ambient.start();
            }
            hudView.invalidate();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (glView != null) glView.onResume();
        if (ambient != null && !ambient.isPlaying()) ambient.start();
    }

    @Override
    protected void onPause() {
        if (glView != null) glView.onPause();
        if (ambient != null && ambient.isPlaying()) ambient.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (ambient != null) {
            ambient.stop();
            ambient.release();
        }
        if (soundPool != null) soundPool.release();
        super.onDestroy();
    }
}
