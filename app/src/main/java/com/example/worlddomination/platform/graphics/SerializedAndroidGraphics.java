package com.example.worlddomination.platform.graphics;

import com.badlogic.gdx.backends.android.AndroidApplicationBase;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.AndroidGraphics;
import com.badlogic.gdx.backends.android.surfaceview.ResolutionStrategy;
import javax.microedition.khronos.opengles.GL10;

/** Keeps Android's finishing cache clear behind the complete GL lifecycle callback. */
public final class SerializedAndroidGraphics extends AndroidGraphics {
    private final Object frameLifecycleLock = new Object();

    public SerializedAndroidGraphics(AndroidApplicationBase application,
            AndroidApplicationConfiguration configuration, ResolutionStrategy resolutionStrategy) {
        super(application, configuration, resolutionStrategy);
    }

    @Override public void onDrawFrame(GL10 gl) {
        synchronized (frameLifecycleLock) {
            super.onDrawFrame(gl);
        }
    }

    @Override public void clearManagedCaches() {
        // libGDX 1.14.2 wakes Android's onPause thread before listener.pause returns.
        // Do not let that thread remove FBO registrations during GL-thread disposal.
        // This block never waits for a GL callback while holding the monitor.
        synchronized (frameLifecycleLock) {
            super.clearManagedCaches();
        }
    }
}
