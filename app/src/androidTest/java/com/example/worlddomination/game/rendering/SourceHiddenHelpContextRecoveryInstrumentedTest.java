package com.example.worlddomination.game.rendering;

import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.GLSurfaceView;
import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidGraphics;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
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
import com.example.worlddomination.game.ui.TitleMenuLayout;
import com.example.worlddomination.game.ui.SourceHelpState;
import com.example.worlddomination.testing.GameActivityScenario;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Observes the first real resumed app render, not a later warmed screenshot. */
@RunWith(AndroidJUnit4.class)
public final class SourceHiddenHelpContextRecoveryInstrumentedTest {
    @Test public void authoredCommandGlyphsRestoreOnFirstFrameAfterForcedEglLoss() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            boolean[] preserve = new boolean[1];
            Observer[] installed = new Observer[1];
            try {
                scenario.onActivity(activity -> {
                    GLSurfaceView surface = surface(activity);
                    preserve[0] = surface.getPreserveEGLContextOnPause();
                    surface.setPreserveEGLContextOnPause(false);
                });
                Observer observer = onGl(scenario, activity -> {
                    WorldDominationGame app = (WorldDominationGame) activity.getApplicationListener();
                    openCommandByTouch(app);
                    assertTrue(help(app).isInputFocused());
                    app.keyDown(Input.Keys.BACK);
                    assertFalse("Baseline excludes blinking caret", help(app).isInputFocused());
                    app.render();
                    Observer result = new Observer(app);
                    result.before = capture(app, "hidden-help-context-before.png");
                    result.before.assertCommandInk();
                    assertTrue(contextValid(result.display, result.context));
                    // Focus again without drawing: pause must clear real edit focus and keyboard state.
                    tap(app, SourceHiddenHelpRenderer.commandBounds(map(app), new Rectangle()));
                    assertTrue("Pause begins with focused command", help(app).isInputFocused());
                    assertTrue("Native keyboard requested", field("hiddenHelpKeyboardVisible").getBoolean(app));
                    installed[0] = result;
                    listenerField().set(activity, result);
                    return result;
                });
                scenario.moveToState(Lifecycle.State.CREATED);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (contextValid(observer.display, observer.context) && System.nanoTime() < deadline)
                    Thread.sleep(20);
                assertFalse("Old EGL context actually destroyed", contextValid(observer.display, observer.context));
                assertTrue("Pause callback reached", observer.paused);
                scenario.moveToState(Lifecycle.State.RESUMED);
                assertTrue("First resumed render captured", observer.first.await(20, TimeUnit.SECONDS));
                if (observer.failure.get() != null)
                    throw new AssertionError("Hidden Help first resumed render", observer.failure.get());
                assertEquals(1, observer.resumes);
                assertNotNull(observer.after);
                observer.before.assertSame(observer.after);
                onGl(scenario, activity -> {
                    observer.assertState();
                    // Restored field still accepts real key input, without submitting a command.
                    tap(observer.app, SourceHiddenHelpRenderer.commandBounds(map(observer.app), new Rectangle()));
                    assertTrue(observer.app.keyTyped('!'));
                    assertEquals("I am Bush!", help(observer.app).getInputText());
                    observer.app.keyDown(Input.Keys.BACK);
                    assertFalse(help(observer.app).isInputFocused());
                    assertEquals(observer.random, observer.game.createPresentationRandomCopy().nextDouble(), 0d);
                    return null;
                });
            } finally {
                try {
                    if (installed[0] != null && scenario.getState() != Lifecycle.State.DESTROYED) {
                        scenario.moveToState(Lifecycle.State.RESUMED);
                        onGl(scenario, activity -> {
                            if (activity.getApplicationListener() == installed[0])
                                listenerField().set(activity, installed[0].app);
                            return null;
                        });
                    }
                } finally {
                    scenario.onActivity(activity -> surface(activity).setPreserveEGLContextOnPause(preserve[0]));
                }
            }
        }
    }

    private static void openCommandByTouch(WorldDominationGame app) throws Exception {
        GameSetup setup = new GameSetup(0L); setup.setDifficulty(1);
        field("setup").set(app, setup);
        app.render();
        tap(app, ((TitleMenuLayout) field("titleMenu").get(app)).newGame);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        OriginalFlashAnimationAssets assets;
        do {
            app.render();
            assets = (OriginalFlashAnimationAssets) field("flashAnimationAssets").get(app);
            if (assets != null && assets.areSetupPortraitsLoaded()) break;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        assertNotNull(assets); assertTrue(assets.areSetupPortraitsLoaded());
        app.render();
        SetupScreenLayout layout = (SetupScreenLayout) field("setupLayout").get(app);
        tap(app, layout.leaderCards[0]); assertTrue(setup.canStart());
        tap(app, layout.startButton);
        assertNotNull(field("game").get(app));
        while (!assets.isLoaded() && System.nanoTime() < deadline) { assets.update(); Thread.sleep(10); }
        assertTrue(assets.isLoaded()); app.render();
        tap(app, (Rectangle) field("helpButton").get(app)); app.render();
        for (int index = 0; index < 10; index++) {
            tap(app, SourceHiddenHelpRenderer.buttonBounds(map(app), new Rectangle())); app.render();
        }
        assertEquals(11, help(app).getInputFrame());
        tap(app, SourceHiddenHelpRenderer.passwordBounds(map(app), new Rectangle()));
        type(app, "youda");
        tap(app, SourceHiddenHelpRenderer.buttonBounds(map(app), new Rectangle())); app.render();
        assertTrue(help(app).isCommandVisible()); assertTrue(help(app).isUnlocked());
        tap(app, SourceHiddenHelpRenderer.commandBounds(map(app), new Rectangle()));
        type(app, "I am Bush"); app.render();
        assertEquals("I am Bush", help(app).getInputText());
        assertFalse("Typing must not submit the score-corrupting command",
                ((GameState)field("game").get(app)).getHiddenCommands().isScoreCorrupt());
    }

    private static final class Observer implements ApplicationListener {
        final WorldDominationGame app;
        final GameState game;
        final int money, budget, population, turn;
        final long score;
        final double random;
        final BitmapFont command, warning;
        final Texture commandTexture, warningTexture;
        final EGLDisplay display = EGL14.eglGetCurrentDisplay();
        final EGLContext context = EGL14.eglGetCurrentContext();
        final CountDownLatch first = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Snapshot before, after;
        volatile boolean paused;
        boolean next;
        int resumes;
        Observer(WorldDominationGame app) throws Exception {
            this.app=app; game=(GameState)field("game").get(app);
            money=game.getHuman().moneyMillions; budget=game.getHuman().turnMoneyMillions;
            population=game.getHuman().populationMillions; score=game.getHuman().score; turn=game.getTurn();
            random=game.createPresentationRandomCopy().nextDouble();
            command=(BitmapFont)field("hiddenHelpCommandFont").get(app);
            warning=(BitmapFont)field("hiddenHelpWarningFont").get(app);
            assertNotNull(command); assertNotNull(warning);
            commandTexture=command.getRegion().getTexture(); warningTexture=warning.getRegion().getTexture();
            assertTrue(commandTexture.isManaged()); assertTrue(warningTexture.isManaged());
        }
        void assertState() throws Exception {
            assertSame(game,field("game").get(app));
            assertEquals(money,game.getHuman().moneyMillions); assertEquals(budget,game.getHuman().turnMoneyMillions);
            assertEquals(population,game.getHuman().populationMillions); assertEquals(score,game.getHuman().score);
            assertEquals(turn,game.getTurn()); assertEquals(0,game.getQueuedActionCount());
            assertEquals(random,game.createPresentationRandomCopy().nextDouble(),0d);
            assertNull(game.getSelectedAction()); assertFalse(game.getHiddenCommands().isScoreCorrupt());
            assertTrue(help(app).isCommandVisible()); assertTrue(help(app).isUnlocked());
            assertEquals("I am Bush",help(app).getInputText());
            assertFalse("Pause blurs editable field",help(app).isInputFocused());
            assertFalse("Pause clears keyboard request",field("hiddenHelpKeyboardVisible").getBoolean(app));
            assertSame(command,field("hiddenHelpCommandFont").get(app));
            assertSame(warning,field("hiddenHelpWarningFont").get(app));
            assertSame(commandTexture,command.getRegion().getTexture());
            assertSame(warningTexture,warning.getRegion().getTexture());
            assertTrue("Command loader texture restored",Gdx.gl.glIsTexture(commandTexture.getTextureObjectHandle()));
            assertTrue("Warning loader texture restored",Gdx.gl.glIsTexture(warningTexture.getTextureObjectHandle()));
        }
        @Override public void create(){app.create();}
        @Override public void resize(int w,int h){app.resize(w,h);}
        @Override public void dispose(){app.dispose();}
        @Override public void pause(){
            app.pause();
            try {
                assertFalse(help(app).isInputFocused());
                assertFalse(field("hiddenHelpKeyboardVisible").getBoolean(app));
            } catch(Throwable error){failure.compareAndSet(null,error);}
            paused=true;
        }
        @Override public void resume(){app.resume();resumes++;next=true;}
        @Override public void render(){
            boolean capture=next;next=false;
            try {
                app.render();
                if(capture){
                    assertState();
                    assertTrue(contextValid(EGL14.eglGetCurrentDisplay(),EGL14.eglGetCurrentContext()));
                    after=capture(app,"hidden-help-context-first-resumed.png");
                    after.assertCommandInk();before.assertSame(after);
                }
            } catch(Throwable error){
                if(!capture)throw new AssertionError("Delegated hidden Help render",error);
                failure.compareAndSet(null,error);
            } finally {if(capture)first.countDown();}
        }
    }

    private static final class Snapshot {
        final int width,height;
        final byte[][] crops;
        Snapshot(int width,int height,byte[][] crops){this.width=width;this.height=height;this.crops=crops;}
        void assertCommandInk(){
            int orange=orange(crops[0]);
            assertTrue("Command contains nonblack orange glyph strokes: "+orange,orange>12);
            assertEquals("Missing font negative control must fail",0,orange(new byte[crops[0].length]));
        }
        void assertSame(Snapshot other){
            assertEquals(width,other.width);assertEquals(height,other.height);
            for(int i=0;i<crops.length;i++)assertArrayEquals("First resumed crop "+i,crops[i],other.crops[i]);
        }
        private static int orange(byte[] rgba){
            int count=0;
            for(int p=0;p<rgba.length;p+=4){int r=rgba[p]&255,g=rgba[p+1]&255,b=rgba[p+2]&255;
                if(r>120&&g>35&&r>g+40&&g>b+20)count++;}
            return count;
        }
    }
    private static Snapshot capture(WorldDominationGame app,String name) throws Exception {
        int w=Gdx.graphics.getBackBufferWidth(),h=Gdx.graphics.getBackBufferHeight();
        byte[] pixels=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
        Pixmap image=new Pixmap(w,h,Pixmap.Format.RGBA8888);PixmapIO.PNG writer=new PixmapIO.PNG();
        try{image.getPixels().put(pixels).position(0);writer.setFlipY(false);
            writer.write(Gdx.files.absolute(new File(dir,name).getAbsolutePath()),image);
        }finally{writer.dispose();image.dispose();}
        Rectangle fitted=SourceHiddenHelpRenderer.fit(map(app),new Rectangle());
        Rectangle[] bounds={SourceHiddenHelpRenderer.commandTextClipBounds(map(app),new Rectangle()),
                new Rectangle(fitted.x,fitted.y+fitted.height-25*fitted.height/275f,fitted.width,25*fitted.height/275f),
                SourceHiddenHelpRenderer.buttonBounds(map(app),new Rectangle())};
        byte[][] crops=new byte[bounds.length][];
        Viewport viewport=(Viewport)field("viewport").get(app);
        for(int i=0;i<bounds.length;i++){
            Rectangle b=bounds[i];Vector2 lo=viewport.project(new Vector2(b.x,b.y));
            Vector2 hi=viewport.project(new Vector2(b.x+b.width,b.y+b.height));
            int left=Math.max(0,(int)Math.floor(lo.x)),right=Math.min(w,(int)Math.ceil(hi.x));
            int top=Math.max(0,h-(int)Math.ceil(hi.y)),bottom=Math.min(h,h-(int)Math.floor(lo.y));
            assertTrue("Visible crop "+i,right>left&&bottom>top);
            crops[i]=new byte[(right-left)*(bottom-top)*4];
            for(int row=top;row<bottom;row++)System.arraycopy(pixels,(row*w+left)*4,
                    crops[i],(row-top)*(right-left)*4,(right-left)*4);
        }
        return new Snapshot(w,h,crops);
    }
    private static void type(WorldDominationGame app,String text){for(int i=0;i<text.length();i++)assertTrue(app.keyTyped(text.charAt(i)));}
    private static Rectangle map(WorldDominationGame app)throws Exception{return (Rectangle)field("mapBounds").get(app);}
    private static SourceHelpState help(WorldDominationGame app)throws Exception{return (SourceHelpState)field("helpState").get(app);}
    private static void tap(WorldDominationGame app,Rectangle b)throws Exception{
        Vector2 p=((Viewport)field("viewport").get(app)).project(new Vector2(b.x+b.width/2,b.y+b.height/2));
        int x=Math.round(p.x),y=Gdx.graphics.getHeight()-Math.round(p.y);app.touchDown(x,y,0,0);app.touchUp(x,y,0,0);
    }
    private static boolean contextValid(EGLDisplay d,EGLContext c){return !EGL14.EGL_NO_CONTEXT.equals(c)&&EGL14.eglQueryContext(d,c,EGL14.EGL_CONTEXT_CLIENT_VERSION,new int[1],0);}
    private static GLSurfaceView surface(MainActivity a){return (GLSurfaceView)((AndroidGraphics)a.getGraphics()).getView();}
    private static Field field(String name)throws Exception{Field f=WorldDominationGame.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Field listenerField()throws Exception{Field f=AndroidApplication.class.getDeclaredField("listener");f.setAccessible(true);return f;}
    private interface Work<T>{T call(MainActivity activity)throws Exception;}
    private static <T>T onGl(GameActivityScenario scenario,Work<T> work)throws Exception{
        CountDownLatch done=new CountDownLatch(1);AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();
        scenario.onActivity(activity->Gdx.app.postRunnable(()->{try{result.set(work.call(activity));}catch(Throwable e){error.set(e);}finally{done.countDown();}}));
        assertTrue("Hidden Help GL timeout",done.await(90,TimeUnit.SECONDS));
        if(error.get()!=null)throw new AssertionError("Hidden Help GL task",error.get());return result.get();
    }
}
