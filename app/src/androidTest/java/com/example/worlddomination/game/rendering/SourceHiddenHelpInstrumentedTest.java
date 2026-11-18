package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.SourceHiddenCommands;
import com.example.worlddomination.game.ui.SourceHelpState;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises authored hidden controls through Android input routing and the real GL renderer. */
@RunWith(AndroidJUnit4.class)
public final class SourceHiddenHelpInstrumentedTest {
    @Test public void hiddenInputMessagesAndMatchResetPreserveGameplay() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = setup(app);
            double random = game.createPresentationRandomCopy().nextDouble();
            int money = game.getHuman().moneyMillions;
            open(app); SourceHelpState help = help(app);
            for (int i = 0; i < 9; i++) pressIndex(app);
            assertFalse(help.hasInputField());
            pressIndex(app); assertTrue(help.hasInputField());
            assertEquals(11, help.getInputFrame());
            tap(app, SourceHiddenHelpRenderer.passwordBounds(map(app), new Rectangle()));
            type(app, "Youda"); pressIndex(app);
            assertFalse(help.isUnlocked());
            tap(app, SourceHiddenHelpRenderer.passwordBounds(map(app), new Rectangle()));
            for (int i = 0; i < 5; i++) app.keyTyped('\b');
            type(app, "youda"); pressIndex(app);
            assertTrue(help.isCommandVisible()); assertEquals("", help.getInputText());
            capture(app, "hidden-help-unlocked.png");
            inputCommand(app, "trump");
            assertEquals("trump", help.getInputText());
            capture(app, "hidden-help-command.png");
            pressIndex(app);
            assertFalse(help.isOpen());
            assertTrue(game.getHiddenCommands().isActive(SourceHiddenCommands.Flag.CASH));
            assertTrue(game.getHiddenCommands().isScoreCorrupt());
            open(app); pressIndex(app); assertTrue(help.isCommandVisible());
            inputCommand(app, "#2dawgz"); pressIndex(app);
            assertEquals("## 2 dawgz / 2 pirates. No ninja's. ##", help.getCommandMessage());
            assertFalse(help.hasInputField());
            capture(app, "hidden-help-warning.png");
            tap(app, SourceHiddenHelpRenderer.warningOkBounds(map(app), new Rectangle()));
            render(app); assertNull(help.getCommandMessage());
            assertEquals("", help.getInputText()); assertTrue(help.isCommandVisible());
            inputCommand(app, "#fc1"); pressIndex(app);
            assertTrue(help.getCommandMessage().endsWith("Chapter 8 "));
            capture(app, "hidden-help-long-warning.png");
            tap(app, SourceHiddenHelpRenderer.warningOkBounds(map(app), new Rectangle()));
            inputCommand(app, "Trump"); pressIndex(app);
            assertFalse(help.isOpen());
            assertTrue("Unknown input must not toggle the lowercase command", game.getHiddenCommands().isActive(SourceHiddenCommands.Flag.CASH));
            assertEquals(money, game.getHuman().moneyMillions);
            assertEquals(0, game.getTurn()); assertEquals(0, game.getQueuedActionCount());
            assertEquals(random, game.createPresentationRandomCopy().nextDouble(), 0d);
            open(app); pressIndex(app); inputCommand(app, "abc");
            app.keyDown(Input.Keys.BACK); assertFalse(help.isInputFocused());
            invoke(app, "beginNewGameSetup"); assertFalse(help.isUnlocked());
        });
    }

    @Test public void privateCreaturesAndExplosionTimersLeaveCanonicalTurnIntact() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = setup(app);
            unlock(app);
            for (String command : new String[]{"free willy", "nessy", "psycho killer", "saturdaynight"}) {
                if (!help(app).isOpen()) { open(app); pressIndex(app); }
                inputCommand(app, command); pressIndex(app);
            }
            // Queue both actions through the actual navigation/modal controls.
            tap(app, ((Rectangle[]) field("navRows").get(app))[0]);
            tap(app, (Rectangle) field("modalAccept").get(app));
            tap(app, ((Rectangle[]) field("navRows").get(app))[3]);
            tap(app, (Rectangle) field("modalAccept").get(app));
            assertEquals(2, game.getQueuedActionCount());
            for (int i=0; i<11; i++) tick(app);
            tap(app, (Rectangle) field("confirmButton").get(app));
            for (int i=0; i<100 && game.getTurn()==0; i++) tick(app);
            assertEquals(1, game.getTurn());
            SourceHiddenBattleEffects effects = (SourceHiddenBattleEffects) field("hiddenBattleEffects").get(app);
            assertTrue(effects.isActive()); assertEquals(0, effects.getElapsedTicks());
            double canonicalRandom = game.createPresentationRandomCopy().nextDouble();
            int[] population = new int[25];
            for(int p=0;p<5;p++) for(int c=0;c<5;c++) population[p*5+c]=game.getPlayerState(p).getCity(c).populationMillions;
            int funds = game.getHuman().moneyMillions;
            int cities = game.getBattlePresentationPopulatedCities(false).length;
            int finishTick = 36*(cities+1)+61;
            for(int i=1;i<=finishTick;i++) {
                tick(app);
                if(i==1) { assertEquals(2,effects.getOrcaFrame());assertEquals(2,effects.getNessyFrame()); }
                if(i==20) capture(app,"hidden-battle-creatures.png");
                if(i==100) capture(app,"hidden-battle-explosions.png");
                if(i==finishTick-1) assertTrue(effects.isActive());
            }
            assertFalse(effects.isActive());
            assertFalse(((TurnPlaybackSequence)field("turnPlaybackSequence").get(app)).isBlockingInput());
            assertNull(field("battlePresentationRandom").get(app));
            for(int p=0;p<5;p++) for(int c=0;c<5;c++) assertEquals(population[p*5+c],game.getPlayerState(p).getCity(c).populationMillions);
            assertEquals(funds,game.getHuman().moneyMillions);
            assertEquals(canonicalRandom,game.createPresentationRandomCopy().nextDouble(),0d);
            capture(app,"hidden-battle-canonical-handoff.png");
        });
    }

    @Test public void combinedExplosionCommandsKeepSourceTimerOverwriteAndChildDepths() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game=setup(app);unlock(app);
            inputCommand(app,"psycho killer");pressIndex(app);
            open(app);pressIndex(app);inputCommand(app,"I am Bush");pressIndex(app);
            // Clicking an already queued navigation action reselects that tube.
            // Use two distinct source actions to fill both slots through the UI.
            for(int row:new int[]{0,3}) {
                tap(app,((Rectangle[])field("navRows").get(app))[row]);
                tap(app,(Rectangle)field("modalAccept").get(app));
            }
            assertEquals(2,game.getQueuedActionCount());
            for(int i=0;i<11;i++)tick(app);
            tap(app,(Rectangle)field("confirmButton").get(app));
            for(int i=0;i<100&&game.getTurn()==0;i++)tick(app);
            assertEquals(1,game.getTurn());
            double random=game.createPresentationRandomCopy().nextDouble();
            int population=game.getHuman().populationMillions;
            SourceHiddenBattleEffects effects=(SourceHiddenBattleEffects)field("hiddenBattleEffects").get(app);
            BattlefieldCityRenderer cities=(BattlefieldCityRenderer)field("cityRenderer").get(app);
            for(int i=1;i<=205;i++) {
                tick(app);
                if(i==36) {
                    assertEquals("source city4 boom at first timer",0,cities.getHiddenExplosionFrame(4));
                    assertEquals("same-deadline second callback reaches city3",0,cities.getHiddenExplosionFrame(3));
                    assertEquals("authored city children occupy through depth28",29,cities.getHiddenExplosionDepth(4));
                    capture(app,"hidden-battle-combined-first.png");
                }
                if(i==37) assertEquals(30,cities.getCityDustDepth(4));
                if(i==204) {assertTrue(effects.isActive());capture(app,"hidden-battle-combined-cleanup.png");}
            }
            assertFalse(effects.isActive());assertFalse(((TurnPlaybackSequence)field("turnPlaybackSequence").get(app)).isBlockingInput());
            assertEquals(population,game.getHuman().populationMillions);
            assertEquals(random,game.createPresentationRandomCopy().nextDouble(),0d);
        });
    }

    private static GameState setup(WorldDominationGame app) throws Exception {
        GameSetup setup = new GameSetup(0L);setup.setDifficulty(1);
        for(int id:new int[]{0,1,2,4}) assertTrue(setup.toggleOpponent(id));
        GameState game = new GameState(setup);field("game").set(app,game);
        ((BattlefieldCityRenderer)field("cityRenderer").get(app)).reset(game);
        help(app).resetMatch();render(app);return game;
    }
    private static void unlock(WorldDominationGame app) throws Exception {
        open(app);for(int i=0;i<10;i++)pressIndex(app);
        tap(app,SourceHiddenHelpRenderer.passwordBounds(map(app),new Rectangle()));
        type(app,"youda");pressIndex(app);assertTrue(help(app).isCommandVisible());
    }
    private static void open(WorldDominationGame app) throws Exception {tap(app,(Rectangle)field("helpButton").get(app));render(app);}
    private static void pressIndex(WorldDominationGame app) throws Exception {tap(app,SourceHiddenHelpRenderer.buttonBounds(map(app),new Rectangle()));render(app);}
    private static void inputCommand(WorldDominationGame app,String text) throws Exception {
        tap(app,SourceHiddenHelpRenderer.commandBounds(map(app),new Rectangle()));type(app,text);render(app);
    }
    private static void type(WorldDominationGame app,String text) {for(int i=0;i<text.length();i++) assertTrue(app.keyTyped(text.charAt(i)));}
    private static SourceHelpState help(WorldDominationGame app) throws Exception {return (SourceHelpState)field("helpState").get(app);}
    private static Rectangle map(WorldDominationGame app) throws Exception {return (Rectangle)field("mapBounds").get(app);}
    private static Field field(String name) throws Exception {Field f=WorldDominationGame.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void invoke(WorldDominationGame app,String name) throws Exception {Method m=WorldDominationGame.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(app);}
    private static void tick(WorldDominationGame app) throws Exception {Method m=WorldDominationGame.class.getDeclaredMethod("fixedUpdate",float.class);m.setAccessible(true);m.invoke(app,1f/24f);}
    private static void tap(WorldDominationGame app,Rectangle b) throws Exception {
        Viewport vp=(Viewport)field("viewport").get(app);
        Vector2 p=vp.project(new Vector2(b.x+b.width/2,b.y+b.height/2));
        int x=Math.round(p.x),y=Gdx.graphics.getHeight()-Math.round(p.y);
        app.touchDown(x,y,0,0);app.touchUp(x,y,0,0);
    }
    private static void render(WorldDominationGame app) throws Exception {field("accumulator").setFloat(app,0);field("skipNextFrameDelta").setBoolean(app,true);app.render();}
    private static void capture(WorldDominationGame app,String name) throws Exception {
        render(app);int w=Gdx.graphics.getWidth(),h=Gdx.graphics.getHeight();
        Pixmap image=new Pixmap(w,h,Pixmap.Format.RGBA8888);
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
        try{image.getPixels().put(ScreenUtils.getFrameBufferPixels(0,0,w,h,true));PixmapIO.writePNG(Gdx.files.absolute(new File(dir,name).getAbsolutePath()),image);}finally{image.dispose();}
    }
}
