package com.example.worlddomination.testing;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class GameActivityScenarioInstrumentedTest {
    @Test public void repeatedFullAtlasLaunchesReleaseManagedResources() throws Exception {
        String texturesBefore = Texture.getManagedStatus();
        String meshesBefore = Mesh.getManagedStatus();
        String shadersBefore = ShaderProgram.getManagedStatus();
        String buffersBefore = FrameBuffer.getManagedStatus();

        for (int launch = 1; launch <= 3; launch++) {
            final int launchNumber = launch;
            try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
                CountDownLatch done = new CountDownLatch(1);
                AtomicReference<Throwable> failure = new AtomicReference<>();
                scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                    try {
                        WorldDominationGame game =
                                (WorldDominationGame) Gdx.app.getApplicationListener();
                        Method initialize = WorldDominationGame.class
                                .getDeclaredMethod("initializeGameplayAssets");
                        initialize.setAccessible(true);
                        initialize.invoke(game);
                        Field assetField = WorldDominationGame.class
                                .getDeclaredField("flashAnimationAssets");
                        assetField.setAccessible(true);
                        OriginalFlashAnimationAssets assets =
                                (OriginalFlashAnimationAssets) assetField.get(game);
                        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                        while (!assets.update() && System.nanoTime() < deadline) {
                            Thread.sleep(5);
                        }
                        assertTrue("Full Flash atlas loaded for launch " + launchNumber,
                                assets.isLoaded());
                    } catch (Throwable error) {
                        failure.set(error);
                    } finally {
                        done.countDown();
                    }
                }));
                assertTrue("GL initialization finished for launch " + launch,
                        done.await(50, TimeUnit.SECONDS));
                if (failure.get() != null) throw new AssertionError(failure.get());
            }

            assertEquals("Texture registry after launch " + launch,
                    texturesBefore, Texture.getManagedStatus());
            assertEquals("Mesh registry after launch " + launch,
                    meshesBefore, Mesh.getManagedStatus());
            assertEquals("Shader registry after launch " + launch,
                    shadersBefore, ShaderProgram.getManagedStatus());
            assertEquals("FrameBuffer registry after launch " + launch,
                    buffersBefore, FrameBuffer.getManagedStatus());
            awaitAssetLoaderExit();
        }
    }

    private static void awaitAssetLoaderExit() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (assetLoaderRunning() && System.nanoTime() < deadline) Thread.sleep(20);
        assertTrue("Flash atlas loader thread terminated", !assetLoaderRunning());
    }

    private static boolean assetLoaderRunning() {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && thread.getName().startsWith("wd2-flash-assets")) {
                return true;
            }
        }
        return false;
    }
}
