package com.example.worlddomination.game.rendering;

import android.os.Build;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import com.example.worlddomination.game.ui.SafeAreaInsets;
import com.example.worlddomination.testing.GameActivityScenario;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Physical surface exclusions must survive viewport scaling, extension and letterboxing. */
@RunWith(AndroidJUnit4.class)
public final class SafeAreaViewportInstrumentedTest {
    private static final float PIXEL_TOLERANCE = .35f;

    @Test public void surfacePixelInsetsTrackResizeAndLetterboxWithoutDensityConversion()
            throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            Object originalGame = field("game").get(app);
            SafeAreaInsets originalInsets = (SafeAreaInsets) field("pendingSafeInsets").get(app);
            GameSetup setup = new GameSetup(0L);
            assertTrue(setup.toggleOpponent(0));
            GameState game = new GameState(setup);
            try {
                // Exercise the render-time pending-snapshot path, with no resize call.
                int actualWidth=Gdx.graphics.getWidth(),actualHeight=Gdx.graphics.getHeight();
                FrameBuffer actual=new FrameBuffer(Pixmap.Format.RGBA8888,
                        actualWidth,actualHeight,false);
                try {
                    field("game").set(app,null);
                    app.setSafeAreaInsetsPixels(37,19,29,17);
                    paint(app,actual);
                    assertPhysicalEdges(app,actualWidth,actualHeight,37,19,29,17);
                    assertControlsInside(app,safeRectangle(app,actualWidth,actualHeight,37,19,29,17));
                    app.setSafeAreaInsetsPixels(0,0,0,0);
                    paint(app,actual);
                    assertPhysicalEdges(app,actualWidth,actualHeight,0,0,0,0);
                    assertControlsInside(app,safeRectangle(app,actualWidth,actualHeight,0,0,0,0));
                } finally { actual.dispose(); }

                // A fixed physical exclusion must be reprojected when only size changes.
                app.setSafeAreaInsetsPixels(96, 32, 24, 20);
                for (int[] size : new int[][]{{800,600}, {1194,552}, {2424,1080}}) {
                    verifyWorld(app, game, size[0], size[1], 96,32,24,20, false);
                }
                // A different exclusion at unchanged dimensions must replace, not accumulate.
                app.setSafeAreaInsetsPixels(142, 18, 88, 24);
                verifyWorld(app, game, 2424,1080,142,18,88,24, true);

                // A cutout beyond the old 13%-wide setup margin must protect all controls.
                app.setSafeAreaInsetsPixels(220,18,32,24);
                verifyWorld(app, game,1194,552,220,18,32,24,false);

                // Wide/tall cases exercise both an exclusion wholly within the black bars
                // and one extending into the actual viewport, on all four edge directions.
                app.setSafeAreaInsetsPixels(200,16,120,24);
                verifyWorld(app, game,2400,500,200,16,120,24,false);
                assertEquals(0f, field("safeLeft").getFloat(app), .001f);
                assertEquals(0f, field("safeRight").getFloat(app), .001f);
                app.setSafeAreaInsetsPixels(400,16,390,24);
                verifyWorld(app, game,2400,500,400,16,390,24,false);
                assertTrue(field("safeLeft").getFloat(app) > 0);
                assertTrue(field("safeRight").getFloat(app) > 0);
                app.setSafeAreaInsetsPixels(24,100,32,120);
                verifyWorld(app, game,800,1200,24,100,32,120,false);
                assertEquals(0f, field("safeTop").getFloat(app), .001f);
                assertEquals(0f, field("safeBottom").getFloat(app), .001f);
                app.setSafeAreaInsetsPixels(24,280,32,270);
                verifyWorld(app, game,800,1200,24,280,32,270,false);
                assertTrue(field("safeTop").getFloat(app) > 0);
                assertTrue(field("safeBottom").getFloat(app) > 0);

                app.setSafeAreaInsetsPixels(0,0,0,0);
                verifyWorld(app, game,800,600,0,0,0,0,false);
                for (String name : new String[]{"safeLeft","safeTop","safeRight","safeBottom"})
                    assertEquals("Return to zero: " + name,0f,field(name).getFloat(app),.001f);

                // Independent negative control for the former px/density-as-world contract.
                // This synthetic density is deliberately unrelated to the viewport scale.
                Viewport viewport = (Viewport) field("viewport").get(app);
                float wrongPixelEdge = viewport.project(new Vector2(96f / 2.625f, 0)).x;
                assertTrue("The former dp interpretation must fail the physical boundary check",
                        Math.abs(wrongPixelEdge - 96f) > 10f);
                app.setSafeAreaInsetsPixels(96,32,24,20);
                verifyWorld(app, game,800,600,96,32,24,20,true);
            } finally {
                field("game").set(app, originalGame);
                app.setSafeAreaInsetsPixels(originalInsets.left,originalInsets.top,
                        originalInsets.right,originalInsets.bottom);
                app.resize(Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
            }
        });
    }

    @Test public void actualWindowCutoutIsTranslatedIntoTheGlSurfaceBeforeProjection()
            throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            try {
                scenario.onActivity(activity -> activity.postRunnable(new Runnable() {
                    boolean initialized;
                    final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
                    @Override public void run() {
                        if(cancelled.get()) return;
                        try {
                            assertTrue("Scene readiness timed out",System.nanoTime()<deadline);
                            WorldDominationGame app=(WorldDominationGame)activity.getApplicationListener();
                            if(!initialized) {
                                Method load=WorldDominationGame.class.getDeclaredMethod("initializeGameplayAssets");
                                load.setAccessible(true);load.invoke(app);
                                // Keep the production splash visible while the separate
                                // setup/early atlas uploads. Geometry checks need no dismissal.
                                initialized=true;
                            }
                            OriginalFlashAnimationAssets assets=(OriginalFlashAnimationAssets)
                                    field("flashAnimationAssets").get(app);
                            if(!assets.update() || !assets.areSetupPortraitsLoaded()
                                    || !assets.areCenterUiAssetsLoaded()) {
                                activity.postRunnable(this);
                                return;
                            }
                            // Never wait for the UI from inside a locked GL callback.
                            activity.runOnUiThread(() -> {
                                if(cancelled.get()) return;
                                try {
                                    float[] physical=windowSurfaceInsets(activity);
                                    activity.postRunnable(() -> {
                                        if(cancelled.get()) return;
                                        try {
                                            int width=Gdx.graphics.getWidth(),height=Gdx.graphics.getHeight();
                                            assertEquals("FillResolutionStrategy uses surface pixel width",
                                                    (int)physical[4],width);
                                            assertEquals("FillResolutionStrategy uses surface pixel height",
                                                    (int)physical[5],height);
                                            app.resize(width,height);
                                            assertPhysicalEdges(app,width,height,physical[0],physical[1],
                                                    physical[2],physical[3]);
                                            assertControlsInside(app,safeRectangle(app,width,height,
                                                    physical[0],physical[1],physical[2],physical[3]));
                                        } catch(Throwable error) { failure.compareAndSet(null,error); }
                                        finally { done.countDown(); }
                                    });
                                } catch(Throwable error) {
                                    failure.compareAndSet(null,error);done.countDown();
                                }
                            });
                        } catch(Throwable error) {
                            failure.compareAndSet(null,error);done.countDown();
                        }
                    }
                }));
                assertTrue("Actual window probe timed out",done.await(75,TimeUnit.SECONDS));
                if(failure.get()!=null) throw new AssertionError("Actual cutout listener",failure.get());
            } finally { cancelled.set(true); }
        }
    }

    /** Runs only on Android's UI thread; returns pixels and dispatches the actual listener. */
    private static float[] windowSurfaceInsets(MainActivity activity) {
        View decor=activity.getWindow().getDecorView();
        View surface=((com.badlogic.gdx.backends.android.AndroidGraphics)
                activity.getGraphics()).getView();
        WindowInsets insets=decor.getRootWindowInsets();
        assertNotNull("Attached window must expose insets",insets);
        DisplayCutout cutout=Build.VERSION.SDK_INT>=Build.VERSION_CODES.P
                ? insets.getDisplayCutout():null;
        int[] origin=new int[2],surfaceOrigin=new int[2];
        decor.getLocationOnScreen(origin);surface.getLocationOnScreen(surfaceOrigin);
        int left=cutout==null?0:cutout.getSafeInsetLeft();
        int top=cutout==null?0:cutout.getSafeInsetTop();
        int right=cutout==null?0:cutout.getSafeInsetRight();
        int bottom=cutout==null?0:cutout.getSafeInsetBottom();
        float x=surfaceOrigin[0]-origin[0],y=surfaceOrigin[1]-origin[1];
        assertTrue(surface.getWidth()>0 && surface.getHeight()>0);
        float[] physical={Math.max(0,left-x),Math.max(0,top-y),
                Math.max(0,x+surface.getWidth()-(decor.getWidth()-right)),
                Math.max(0,y+surface.getHeight()-(decor.getHeight()-bottom)),
                surface.getWidth(),surface.getHeight()};
        decor.dispatchApplyWindowInsets(insets);
        return physical;
    }

    private static void verifyWorld(WorldDominationGame app, GameState game, int width,
            int height,float left,float top,float right,float bottom,boolean capture) throws Exception {
        app.resize(width,height);
        assertPhysicalEdges(app,width,height,left,top,right,bottom);
        Rectangle safe=safeRectangle(app,width,height,left,top,right,bottom);
        FrameBuffer target=new FrameBuffer(Pixmap.Format.RGBA8888,width,height,false);
        try {
            field("game").set(app,null);
            paint(app,target);
            assertControlsInside(app,safe);
            if(capture && width==800) save(target,"safe-area-setup-800x600.png",width,height);
            field("game").set(app,game);
            ((BattlefieldCityRenderer)field("cityRenderer").get(app)).reset(game);
            paint(app,target);
            assertControlsInside(app,safe);
            if(capture && width==2424) save(target,"safe-area-game-2424x1080.png",width,height);
        } finally { target.dispose(); }
    }

    private static Rectangle safeRectangle(WorldDominationGame app,int width,int height,
            float left,float top,float right,float bottom) throws Exception {
        Viewport v=(Viewport)field("viewport").get(app);
        float l=Math.max(v.getScreenX(),left),b=Math.max(v.getScreenY(),bottom);
        float r=Math.min(v.getScreenX()+v.getScreenWidth(),width-right);
        float t=Math.min(v.getScreenY()+v.getScreenHeight(),height-top);
        assertTrue("Nonempty independent physical safe rectangle",r>l && t>b);
        return new Rectangle(l,b,r-l,t-b);
    }

    private static void assertPhysicalEdges(WorldDominationGame app,int width,int height,
            float left,float top,float right,float bottom) throws Exception {
        Viewport v=(Viewport)field("viewport").get(app);
        Rectangle safe=safeRectangle(app,width,height,left,top,right,bottom);
        Vector2 low=v.project(new Vector2(field("safeLeft").getFloat(app),
                field("safeBottom").getFloat(app)));
        Vector2 high=v.project(new Vector2(v.getWorldWidth()-field("safeRight").getFloat(app),
                v.getWorldHeight()-field("safeTop").getFloat(app)));
        assertEquals("Projected safe left",safe.x,low.x,PIXEL_TOLERANCE);
        assertEquals("Projected safe bottom",safe.y,low.y,PIXEL_TOLERANCE);
        assertEquals("Projected safe right",safe.x+safe.width,high.x,PIXEL_TOLERANCE);
        assertEquals("Projected safe top",safe.y+safe.height,high.y,PIXEL_TOLERANCE);
    }

    private static void assertControlsInside(WorldDominationGame app,Rectangle safe) throws Exception {
        Viewport v=(Viewport)field("viewport").get(app);
        if(field("game").get(app)==null) {
            SetupScreenLayout s=(SetupScreenLayout)field("setupLayout").get(app);
            for(Rectangle r:new Rectangle[]{s.startButton,s.leaderPrevious,s.leaderNext}) inside(v,safe,r);
            for(Rectangle[] rows:new Rectangle[][]{s.difficultyButtons,s.colorSwatches,s.buildingButtons,
                    s.leaderCards,s.opponentSlots}) for(Rectangle r:rows) inside(v,safe,r);
        } else {
            for(String name:new String[]{"helpButton","muteButton","settingsButton","confirmButton",
                    "navViewport","equipmentViewport"}) inside(v,safe,(Rectangle)field(name).get(app));
            for(Rectangle r:(Rectangle[])field("actionSlots").get(app)) inside(v,safe,r);
        }
    }

    private static void inside(Viewport v,Rectangle safe,Rectangle r) {
        assertTrue("Control must retain positive dimensions",r.width>0 && r.height>0);
        Vector2 low=v.project(new Vector2(r.x,r.y));
        Vector2 high=v.project(new Vector2(r.x+r.width,r.y+r.height));
        assertTrue("Projected control inside safe pixels: "+r+" -> "+low+","+high+" safe="+safe,
                low.x>=safe.x-PIXEL_TOLERANCE && low.y>=safe.y-PIXEL_TOLERANCE
                && high.x<=safe.x+safe.width+PIXEL_TOLERANCE
                && high.y<=safe.y+safe.height+PIXEL_TOLERANCE);
    }

    private static void paint(WorldDominationGame app,FrameBuffer target) throws Exception {
        field("skipNextFrameDelta").setBoolean(app,true);
        target.begin();
        try { app.render();assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError()); }
        finally { target.end(); }
    }

    private static void save(FrameBuffer target,String name,int width,int height) {
        target.begin();
        Pixmap pixels;
        try { pixels=ScreenUtils.getFrameBufferPixmap(0,0,width,height); }
        finally { target.end(); }
        try {
            File dir=InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getExternalFilesDir("beta-ui-evidence");
            assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
            PixmapIO.writePNG(Gdx.files.absolute(new File(dir,name).getAbsolutePath()),pixels,-1,true);
        } finally { pixels.dispose(); }
    }

    private static Field field(String name) throws Exception {
        Field field=WorldDominationGame.class.getDeclaredField(name);
        field.setAccessible(true);return field;
    }
}
