package com.example.worlddomination;

import android.os.Build;
import android.os.Bundle;
import android.content.Context;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.AndroidAudio;
import com.badlogic.gdx.backends.android.AndroidGraphics;
import com.badlogic.gdx.backends.android.surfaceview.FillResolutionStrategy;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.ui.SafeAreaInsets;
import com.example.worlddomination.platform.audio.SourceAndroidAudio;
import com.example.worlddomination.platform.persistence.AndroidScorePreferenceBackend;
import com.example.worlddomination.platform.persistence.AndroidGameSaveStore;
import com.example.worlddomination.platform.graphics.SerializedAndroidGraphics;

/** Android-only landscape launcher for the libGDX game. */
public final class MainActivity extends AndroidApplication {
    private WorldDominationGame game;

    @Override
    protected AndroidGraphics createGraphics(AndroidApplicationConfiguration configuration) {
        return new SerializedAndroidGraphics(this, configuration,
                configuration.resolutionStrategy == null
                        ? new FillResolutionStrategy() : configuration.resolutionStrategy);
    }

    @Override
    public AndroidAudio createAudio(Context context, AndroidApplicationConfiguration config) {
        if (config.disableAudio) return super.createAudio(context, config);
        return new SourceAndroidAudio(context, config);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        game = new WorldDominationGame(new AndroidScorePreferenceBackend(
                getSharedPreferences("world_domination_2_scores", Context.MODE_PRIVATE)));
        game.setGameSaveStore(AndroidGameSaveStore.create(getFilesDir()));

        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true;
        configuration.useGL30 = false;
        configuration.numSamples = 0;
        configuration.stencil = 8;
        configuration.useWakelock = false;
        initialize(game, configuration);
        applyImmersiveMode();
        installCutoutInsetsListener();
    }

    private void applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void installCutoutInsetsListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        View decor = getWindow().getDecorView();
        decor.setOnApplyWindowInsetsListener((view, insets) -> {
            applyCutoutInsets(view, insets);
            return view.onApplyWindowInsets(insets);
        });
        // Insets can arrive before the GL view is laid out, or before it moves
        // into an already-inset window. Recompute after either layout changes.
        graphics.getView().addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    WindowInsets insets = decor.getRootWindowInsets();
                    if (insets != null) applyCutoutInsets(decor, insets);
                });
        decor.requestApplyInsets();
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.P)
    private void applyCutoutInsets(View decor, WindowInsets insets) {
        View surface = graphics.getView();
        if (decor.getWidth() <= 0 || decor.getHeight() <= 0
                || surface.getWidth() <= 0 || surface.getHeight() <= 0) return;
        DisplayCutout cutout = insets.getDisplayCutout();
        SafeAreaInsets windowInsets = cutout == null ? SafeAreaInsets.NONE
                : new SafeAreaInsets(cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                        cutout.getSafeInsetRight(), cutout.getSafeInsetBottom());
        int[] decorPosition = new int[2];
        int[] surfacePosition = new int[2];
        decor.getLocationInWindow(decorPosition);
        surface.getLocationInWindow(surfacePosition);
        SafeAreaInsets pixels = windowInsets.relativeTo(decor.getWidth(), decor.getHeight(),
                surfacePosition[0] - decorPosition[0], surfacePosition[1] - decorPosition[1],
                surface.getWidth(), surface.getHeight());
        game.setSafeAreaInsetsPixels(pixels.left, pixels.top, pixels.right, pixels.bottom);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveMode();
    }
}
