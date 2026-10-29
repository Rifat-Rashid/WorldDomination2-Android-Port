package com.example.worlddomination;

import android.os.Build;
import android.os.Bundle;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.example.worlddomination.game.WorldDominationGame;

/** Android-only landscape launcher for the libGDX game. */
public final class MainActivity extends AndroidApplication {
    private WorldDominationGame game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        game = new WorldDominationGame();

        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true;
        configuration.useGL30 = false;
        configuration.numSamples = 0;
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
            DisplayCutout cutout = insets.getDisplayCutout();
            if (cutout != null) {
                float density = getResources().getDisplayMetrics().density;
                game.setSafeAreaInsets(cutout.getSafeInsetLeft() / density,
                        cutout.getSafeInsetTop() / density,
                        cutout.getSafeInsetRight() / density,
                        cutout.getSafeInsetBottom() / density);
            } else {
                game.setSafeAreaInsets(0f, 0f, 0f, 0f);
            }
            return view.onApplyWindowInsets(insets);
        });
        decor.requestApplyInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveMode();
    }
}
