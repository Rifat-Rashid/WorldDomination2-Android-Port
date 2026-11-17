package com.example.worlddomination.game.rendering;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLSurfaceView;
import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.backends.android.AndroidGraphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.testing.GameActivityScenario;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class FlashBattleContextRecoveryInstrumentedTest {
    @Test
    public void liveRocketAndBattleArtworkSurviveEglLoss() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            boolean[] preserve = new boolean[1];
            State[] registered = new State[1];
            try {
                scenario.onActivity(activity -> {
                    GLSurfaceView view = (GLSurfaceView) ((AndroidGraphics) activity.getGraphics()).getView();
                    preserve[0] = view.getPreserveEGLContextOnPause();
                    view.setPreserveEGLContextOnPause(false);
                });
                State before = onGlThread(scenario, () -> {
                    WorldDominationGame app = (WorldDominationGame) Gdx.app.getApplicationListener();
                    call(app, "initializeGameplayAssets");
                    OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) field(WorldDominationGame.class, "flashAnimationAssets").get(app);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                    while (!assets.isLoaded() && System.nanoTime() < deadline) {
                        assets.update();
                        Thread.sleep(10);
                    }
                    assertTrue("Full battle atlas must load", assets.isLoaded());
                    GameSetup setup = new GameSetup(0L);
                    setup.setDifficulty(1);
                    assertTrue(setup.toggleOpponent(0));
                    assertTrue(setup.toggleOpponent(1));
                    GameState game = new GameState(setup);
                    field(WorldDominationGame.class, "game").set(app, game);
                    BattlefieldCityRenderer cities = (BattlefieldCityRenderer) field(WorldDominationGame.class, "cityRenderer").get(app);
                    cities.reset(game);
                    ((SourceBattleRecordClock) field(WorldDominationGame.class, "battleRecordClock").get(app)).reset();
                    Method fixed = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
                    fixed.setAccessible(true);
                    game.selectAction(GameState.Action.PREPARED_ATTACK);
                    assertTrue(game.configurePreparedCarrier("rocket1"));
                    assertTrue(game.acceptSelectedAction());
                    game.selectAction(GameState.Action.RESOURCES);
                    assertTrue(game.acceptSelectedAction());
                    call(app, "confirmTurnAndPreparePlayback");
                    for (int i = 0; i < 1200 && cities.isBattleActive(); i++) {
                        fixed.invoke(app, 1f / 24f);
                    }
                    assertFalse(cities.isBattleActive());
                    game.selectAction(GameState.Action.PREPARED_ATTACK);
                    assertTrue(game.configurePreparedLaunch(1, 2, "bioload1"));
                    assertTrue(game.acceptSelectedAction());
                    game.selectAction(GameState.Action.RESOURCES);
                    assertTrue(game.acceptSelectedAction());
                    call(app, "confirmTurnAndPreparePlayback");
                    @SuppressWarnings("unchecked") List<FlashRocketAnimation> rockets = (List<FlashRocketAnimation>) field(WorldDominationGame.class, "rocketBattleAnimations").get(app);
                    FlashRocketAnimation rocket = null;
                    for (FlashRocketAnimation candidate : rockets) {
                        if (!candidate.getRoute().submarineLaunch && candidate.targetPlayerId() == 1) {
                            rocket = candidate;
                        }
                    }
                    assertNotNull(rocket);
                    SourceRocketPlayback core = rocket.getSourcePlayback();
                    assertNotNull(core);
                    for (int i = 0; i < 190; i++) {
                        fixed.invoke(app, 1f / 24f);
                    }
                    assertTrue("Battle still active", cities.isBattleActive());
                    assertTrue("rocket has left launch position", core.getY() != 356.25d);
                    TextureRegion trail = assets.getShape(OriginalFlashAnimationAssets.ROCKET_TRAIL_SHAPE_ID);
                    assertNotNull("Full atlas rocket trail", trail);
                    assertTrue(trail.getTexture().isManaged());
                    State result = new State(app, game, assets, rocket, core, fixed, trail,
                            EGL14.eglGetCurrentDisplay(), EGL14.eglGetCurrentContext());
                    result.pixels = render(new TextureRegion[]{trail}, "source-battle-context-before.png");
                    result.initialPose = result.pose();
                    Gdx.app.addLifecycleListener(result.listener);
                    registered[0] = result;
                    return result;
                });
                boolean progressed = false;
                long progressDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (!progressed && System.nanoTime() < progressDeadline) {
                    progressed = onGlThread(scenario, () -> !before.initialPose.equals(before.pose()));
                    if (!progressed) {
                        Thread.sleep(20);
                    }
                }
                assertTrue("Normal render loop advances the live rocket", progressed);
                assertFalse(EGL14.EGL_NO_CONTEXT.equals(before.context));
                scenario.moveToState(Lifecycle.State.CREATED);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (EGL14.eglQueryContext(before.display, before.context,
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, new int[1], 0)
                        && System.nanoTime() < deadline) {
                    Thread.sleep(20);
                }
                assertFalse("Old EGL context destroyed", EGL14.eglQueryContext(before.display, before.context, EGL14.EGL_CONTEXT_CLIENT_VERSION, new int[1], 0));
                assertNotNull("Pause callback records live state", before.paused.get());
                Thread.sleep(2000);
                scenario.moveToState(Lifecycle.State.RESUMED);
                assertTrue("First resumed render observation timed out",
                        before.firstRenderObserved.await(10, TimeUnit.SECONDS));
                if (before.observerFailure.get() != null) {
                    throw new AssertionError("First resumed render observer", before.observerFailure.get());
                }
                onGlThread(scenario, () -> {
                    assertSame(before.app, Gdx.app.getApplicationListener());
                    assertSame(before.game, field(WorldDominationGame.class, "game").get(before.app));
                    assertTrue("Live rocket owner retained", ((List<?>) field(WorldDominationGame.class, "rocketBattleAnimations").get(before.app)).contains(before.rocket));
                    assertSame(before.assets, field(WorldDominationGame.class, "flashAnimationAssets").get(before.app));
                    assertNotNull("Resume callback records state before drawing", before.resumed.get());
                    assertEquals("No paused-time movement", before.paused.get(), before.resumed.get());
                    assertEquals("Canonical RNG unchanged", before.canonical, before.game.createPresentationRandomCopy().nextDouble(), 0d);
                    assertSame(before.trail, before.assets.getShape(OriginalFlashAnimationAssets.ROCKET_TRAIL_SHAPE_ID));
                    assertArrayEquals("Battle atlas pixels restored", before.pixels, render(new TextureRegion[]{before.trail}, "source-battle-context-after.png"));
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    double x = before.core.getX(), y = before.core.getY();
                    before.fixed.invoke(before.app, 1f / 24f);
                    assertTrue("Next source tick moves rocket", x != before.core.getX() || y != before.core.getY());
                    JSONObject evidence = new JSONObject();
                    evidence.put("scope", "EGL loss and exactly the first resumed render; not later-frame timing");
                    evidence.put("pausePose", before.paused.get());
                    evidence.put("resumePose", before.resumed.get());
                    evidence.put("beforeFirstRenderPose", before.beforeFirstRenderPose);
                    evidence.put("afterFirstRenderPose", before.afterFirstRenderPose);
                    evidence.put("resumeDeltaSeconds", before.resumeDelta);
                    evidence.put("resumeAccumulatorSeconds", before.resumeAccumulator);
                    evidence.put("beforeFirstRenderCanonicalRngNext", before.beforeFirstRenderRng);
                    evidence.put("afterFirstRenderCanonicalRngNext", before.afterFirstRenderRng);
                    evidence.put("postTickPose", before.pose());
                    evidence.put("canonicalRngNext", before.canonical);
                    Gdx.files.absolute(new File(evidenceDirectory(), "source-battle-context.json").getAbsolutePath())
                            .writeString(evidence.toString(2), false, "UTF-8");
                    return null;
                });
            } finally {
                if (registered[0] != null && Gdx.app != null) {
                    Gdx.app.removeLifecycleListener(registered[0].listener);
                }
                scenario.onActivity(activity -> ((GLSurfaceView) ((AndroidGraphics) activity.getGraphics()).getView())
                        .setPreserveEGLContextOnPause(preserve[0]));
            }
        }
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void call(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static final class State {
        final WorldDominationGame app;
        final GameState game;
        final OriginalFlashAnimationAssets assets;
        final FlashRocketAnimation rocket;
        final SourceRocketPlayback core;
        final Method fixed;
        final TextureRegion trail;
        final EGLDisplay display;
        final EGLContext context;
        final double canonical;
        final AtomicReference<String> paused = new AtomicReference<>(), resumed = new AtomicReference<>();
        final CountDownLatch firstRenderObserved = new CountDownLatch(1);
        final AtomicReference<Throwable> observerFailure = new AtomicReference<>();
        byte[] pixels;
        String initialPose;
        String beforeFirstRenderPose;
        String afterFirstRenderPose;
        float resumeDelta;
        float resumeAccumulator;
        double beforeFirstRenderRng;
        double afterFirstRenderRng;
        final LifecycleListener listener = new LifecycleListener() {
            @Override public void pause() { paused.set(pose()); }
            @Override public void resume() {
                resumed.set(pose());
                // AndroidGraphics snapshots the runnable queue before rendering.
                // The nested post runs on the following frame, after exactly one render.
                Gdx.app.postRunnable(() -> {
                    try {
                        resumeDelta = Gdx.graphics.getDeltaTime();
                        resumeAccumulator = field(WorldDominationGame.class, "accumulator").getFloat(app);
                        beforeFirstRenderPose = pose();
                        beforeFirstRenderRng = game.createPresentationRandomCopy().nextDouble();
                        assertEquals("Resumed frame delta", 0f, resumeDelta, 0f);
                        assertEquals("Accumulator reset by application resume", 0f, resumeAccumulator, 0f);
                        assertEquals("Application resume preserves rocket pose", resumed.get(), beforeFirstRenderPose);
                        assertEquals("Application resume preserves canonical RNG", canonical, beforeFirstRenderRng, 0d);
                        Gdx.app.postRunnable(() -> {
                            try {
                                afterFirstRenderPose = pose();
                                afterFirstRenderRng = game.createPresentationRandomCopy().nextDouble();
                                assertEquals("First resumed render preserves rocket pose",
                                        beforeFirstRenderPose, afterFirstRenderPose);
                                assertEquals("First resumed render preserves canonical RNG",
                                        beforeFirstRenderRng, afterFirstRenderRng, 0d);
                            } catch (Throwable error) {
                                observerFailure.set(error);
                            } finally {
                                firstRenderObserved.countDown();
                            }
                        });
                    } catch (Throwable error) {
                        observerFailure.set(error);
                        firstRenderObserved.countDown();
                    }
                });
            }
            @Override public void dispose() { }
        };
        State(WorldDominationGame app, GameState game, OriginalFlashAnimationAssets assets, FlashRocketAnimation rocket,
              SourceRocketPlayback core, Method fixed, TextureRegion trail, EGLDisplay display, EGLContext context) {
            this.app = app;
            this.game = game;
            this.assets = assets;
            this.rocket = rocket;
            this.core = core;
            this.fixed = fixed;
            this.trail = trail;
            this.display = display;
            this.context = context;
            this.canonical = game.createPresentationRandomCopy().nextDouble();
        }
        String pose() {
            return core.getAction() + ":" + core.getSourceFrame() + ":"
                    + core.getX() + ":" + core.getY();
        }
    }
    private static <T> T onGlThread(GameActivityScenario scenario,
                                    Callable<T> work) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
            try {
                result.set(work.call());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                done.countDown();
            }
        }));
        assertTrue("Flash atlas GL task timed out", done.await(180, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError("Flash atlas GL task", failure.get());
        return result.get();
    }

    private static File evidenceDirectory() {
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull("Evidence directory", directory);
        assertTrue("Evidence directory exists", directory.isDirectory() || directory.mkdirs());
        return directory;
    }

    private static byte[] render(TextureRegion[] regions, String evidenceName) {
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, 352, 128, false);
        SpriteBatch batch = new SpriteBatch();
        Pixmap pixels = null;
        try {
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 352, 128));
            buffer.begin();
            Gdx.gl.glClearColor(0, 0, 0, 0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.begin();
            for (int index = 0; index < regions.length; index++) {
                batch.draw(regions[index], 12 + index * 40, 40, 32, 32);
            }
            batch.end();
            pixels = ScreenUtils.getFrameBufferPixmap(0, 0, 352, 128);
            buffer.end();
            PixmapIO.writePNG(Gdx.files.absolute(
                    new File(evidenceDirectory(), evidenceName).getAbsolutePath()), pixels);
            byte[] rgba = new byte[pixels.getWidth() * pixels.getHeight() * 4];
            int offset = 0;
            int[] paintedByRegion = new int[regions.length];
            for (int y = 0; y < pixels.getHeight(); y++) {
                for (int x = 0; x < pixels.getWidth(); x++) {
                    int color = pixels.getPixel(x, y);
                    if ((color & 0xff) != 0) {
                        for (int index = 0; index < regions.length; index++) {
                            if (x >= 12 + index * 40 && x < 44 + index * 40)
                                paintedByRegion[index]++;
                        }
                    }
                    rgba[offset++] = (byte) (color >>> 24);
                    rgba[offset++] = (byte) (color >>> 16);
                    rgba[offset++] = (byte) (color >>> 8);
                    rgba[offset++] = (byte) color;
                }
            }
            for (int index = 0; index < regions.length; index++) {
                assertTrue("Battle atlas region " + index + " rendered no visible pixels",
                        paintedByRegion[index] > 0);
            }
            return rgba;
        } finally {
            if (pixels != null) pixels.dispose();
            batch.dispose();
            buffer.dispose();
        }
    }

}
