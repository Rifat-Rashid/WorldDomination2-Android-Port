package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.ProductionScreenLayout;
import com.example.worlddomination.game.ui.ScrollPaneState;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** Actual phone production controls against the seed-zero 65M source UI capture. */
@RunWith(AndroidJUnit4.class)
public final class SourceProductionSliderBudgetInstrumentedTest {
    @Test public void sliderTouchPreservesFractionThroughReservationAndTurn() throws Exception {
        verify(false);
    }
    @Test public void splitSlidersPreserveFractionsThroughReservationAndTurn() throws Exception {
        verify(true);
    }
    @Test public void defenseSliderUsesOriginalCandidatesAndRoundedUnusedAmounts() throws Exception {
        for (int percent : new int[]{20, 30}) {
            SourceBattleCleanupInstrumentedTest.runInGame(app -> {
                GameSetup setup=new GameSetup(0L);setup.setDifficulty(1);
                assertTrue(setup.toggleOpponent(0));assertTrue(setup.toggleOpponent(1));
                GameState game=new GameState(setup);field(WorldDominationGame.class,"game").set(app,game);
                field(WorldDominationGame.class,"modalOpen").setBoolean(app,false);
                BattlefieldCityRenderer cities=(BattlefieldCityRenderer)field(WorldDominationGame.class,"cityRenderer").get(app);
                cities.reset(game);navigation(app,GameState.Action.PRODUCTION);
                Rectangle modal=(Rectangle)field(WorldDominationGame.class,"modal").get(app);
                tap(app,ProductionScreenLayout.setHandleBounds(7,percent+1,modal,new Rectangle()));
                assertEquals(percent,game.getProductionPercent("defense"));
                assertEquals(percent==20?13d:19.5d,game.getProductionSliderBudgetMillions("defense"),0d);
                assertEquals(percent==20?13:20,game.getSelectedProductionTotalBudgetMillions());
                evidence(app,game,"defense"+percent,"selection");
                tap(app,(Rectangle)field(WorldDominationGame.class,"modalAccept").get(app));
                assertEquals(GameState.Action.PRODUCTION,game.getQueuedAction(0));
                assertEquals(percent==20?52:45,game.getTurnMoneyMillions());
                assertEquals(65,game.getHuman().moneyMillions);
                evidence(app,game,"defense"+percent,"queued");
                navigation(app,GameState.Action.RESOURCES);
                tap(app,(Rectangle)field(WorldDominationGame.class,"modalAccept").get(app));
                call(app,"synchronizeTurnControls");advance(app,11);
                tap(app,(Rectangle)field(WorldDominationGame.class,"confirmButton").get(app));
                for(int i=0;i<40&&game.getTurn()==0;i++)advance(app,1);
                assertEquals(1,game.getTurn());
                assertEquals(percent==20?106:91,game.getHuman().moneyMillions);
                assertEquals(percent==20?106:91,game.getTurnMoneyMillions());
                assertEquals(2,game.getHuman().getWeapons().get("antiAir").inStock);
                assertEquals(percent==20?2:3,game.getHuman().getWeapons().get("antiRocket").inStock);
                assertEquals(percent==20?9102:11122,game.getHuman().score);
                double canonical=percent==20?.18031790152968785:.5036365052098872;
                assertEquals(canonical,game.createPresentationRandomCopy().nextDouble(),0d);
                assertTrue(game.getMessages().contains("Money returned from production: "
                        +(percent==20?13:5)+".000.000"));
                assertTrue(cities.isBattleActive());
                int ticks=0;while(cities.isBattleActive()&&ticks++<1500){
                    advance(app,1);assertEquals(canonical,game.createPresentationRandomCopy().nextDouble(),0d);
                }
                assertFalse("source defense production handoff",cities.isBattleActive());
                evidence(app,game,"defense"+percent,"resolved");
            });
        }
    }
    private static void verify(boolean split) throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameSetup setup=new GameSetup(0L);setup.setDifficulty(1);
            assertTrue(setup.toggleOpponent(0));assertTrue(setup.toggleOpponent(1));
            GameState game=new GameState(setup);field(WorldDominationGame.class,"game").set(app,game);
            field(WorldDominationGame.class,"modalOpen").setBoolean(app,false);
            ((BattlefieldCityRenderer)field(WorldDominationGame.class,"cityRenderer").get(app)).reset(game);
            assertEquals(65,game.getTurnMoneyMillions());assertEquals(65,game.getHuman().moneyMillions);
            assertEquals(.8050278270130223,game.createPresentationRandomCopy().nextDouble(),0d);
            navigation(app,GameState.Action.PRODUCTION);
            assertEquals(GameState.Action.PRODUCTION,game.getSelectedAction());
            Rectangle modal=(Rectangle)field(WorldDominationGame.class,"modal").get(app);
            Rectangle position=ProductionScreenLayout.setHandleBounds(0,51,modal,new Rectangle());
            tap(app,position.x+position.width/2f,position.y+position.height/2f);
            assertEquals("physical slider stepped to 50%",50,game.getProductionPercent("rockets"));
            assertEquals(32.5d,game.getProductionSliderBudgetMillions("rockets"),0d);
            if(split) {
                Rectangle soldier=ProductionScreenLayout.setHandleBounds(5,51,modal,new Rectangle());
                tap(app,soldier.x+soldier.width/2f,soldier.y+soldier.height/2f);
                assertEquals("soldier slider stepped to 50%",50,game.getProductionPercent("soldier"));
                assertEquals(32.5d,game.getProductionSliderBudgetMillions("soldier"),0d);
            }
            assertEquals(split?65:33,game.getSelectedProductionTotalBudgetMillions());
            String[] summaries=(String[])field(WorldDominationGame.class,"productionSummaryAmounts").get(app);
            assertEquals("50 % ( $ 32.500.000 )",summaries[0]);
            if(split)assertEquals("50 % ( $ 32.500.000 )",summaries[1]);
            evidence(app,game,split,"selection");
            assertEquals(65,game.getTurnMoneyMillions());assertEquals(65,game.getHuman().moneyMillions);
            tap(app,(Rectangle)field(WorldDominationGame.class,"modalAccept").get(app));
            assertEquals(GameState.Action.PRODUCTION,game.getQueuedAction(0));
            assertEquals(split?0:32,game.getTurnMoneyMillions());assertEquals(65,game.getHuman().moneyMillions);
            @SuppressWarnings("unchecked") List<Object> queued=(List<Object>)field(GameState.class,"queuedActions").get(game);
            Object order=queued.get(0);Field slider=order.getClass().getDeclaredField("sliderProductionBudgets");slider.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String,Double> amounts=(Map<String,Double>)slider.get(order);
            assertEquals(32.5d,amounts.get("rockets"),0d);
            if(split)assertEquals(32.5d,amounts.get("soldier"),0d);
            evidence(app,game,split,"queued");
            navigation(app,GameState.Action.RESOURCES);
            tap(app,(Rectangle)field(WorldDominationGame.class,"modalAccept").get(app));
            assertEquals(GameState.Action.RESOURCES,game.getQueuedAction(1));
            call(app,"synchronizeTurnControls");advance(app,11);
            tap(app,(Rectangle)field(WorldDominationGame.class,"confirmButton").get(app));
            for(int i=0;i<40&&game.getTurn()==0;i++)advance(app,1);
            assertEquals("physical confirm completed",1,game.getTurn());
            assertEquals(split?66:86,game.getHuman().moneyMillions);assertEquals(split?66:86,game.getTurnMoneyMillions());
            assertEquals(5,game.getHuman().getWeapons().get("rocket1").inStock);
            if(split)assertEquals(5,game.getHuman().getWeapons().get("soldier1").inStock);
            assertEquals(split?25254:17181,game.getHuman().score);
            double canonical=split?.46689223535252355:.24013040782635398;
            assertEquals(canonical,game.createPresentationRandomCopy().nextDouble(),0d);
            String returned=split?"26.000.000":"13.000.000";
            assertTrue("source rounded unused production message",game.getMessages().stream()
                    .anyMatch(message->message.contains(returned) && message.toLowerCase().contains("production")));
            BattlefieldCityRenderer cities=(BattlefieldCityRenderer)field(WorldDominationGame.class,"cityRenderer").get(app);
            assertTrue("private battle presentation started",cities.isBattleActive());
            int ticks=0;while(cities.isBattleActive() && ticks++<1500) {
                advance(app,1);assertEquals("canonical random frozen during private playback",canonical,
                        game.createPresentationRandomCopy().nextDouble(),0d);
            }
            assertFalse("source battle handed off",cities.isBattleActive());
            evidence(app,game,split,"resolved");
        });
    }
    private static void evidence(WorldDominationGame app,GameState game,boolean split,String stage)throws Exception {
        evidence(app,game,split?"split":"half",stage);
    }
    private static void evidence(WorldDominationGame app,GameState game,String scenario,String stage)throws Exception {
        double before=game.createPresentationRandomCopy().nextDouble();
        render(app);
        assertEquals("screenshot does not consume canonical RNG",before,game.createPresentationRandomCopy().nextDouble(),0d);
        int w=Gdx.graphics.getBackBufferWidth(),h=Gdx.graphics.getBackBufferHeight();
        Pixmap image=new Pixmap(w,h,Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(ScreenUtils.getFrameBufferPixels(0,0,w,h,true)).position(0);
            File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
            assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
            PixmapIO.writePNG(Gdx.files.absolute(new File(dir,"source-production-"+scenario+"-"+stage+".png").getAbsolutePath()),image);
        } finally {image.dispose();}
    }
    private static void navigation(WorldDominationGame app,GameState.Action action)throws Exception {
        ScrollPaneState scroll=(ScrollPaneState)field(WorldDominationGame.class,"navigationScroll").get(app);
        scroll.moveBy(-scroll.getOffset());render(app);
        Rectangle viewport=(Rectangle)field(WorldDominationGame.class,"navViewport").get(app);
        Rectangle row=((Rectangle[])field(WorldDominationGame.class,"navRows").get(app))[action.ordinal()];
        scroll.moveBy(viewport.y+viewport.height/2f-(row.y+row.height/2f));render(app);
        tap(app,((Rectangle[])field(WorldDominationGame.class,"navRows").get(app))[action.ordinal()]);
    }
    private static void tap(WorldDominationGame app,Rectangle r)throws Exception{tap(app,r.x+r.width/2f,r.y+r.height/2f);}
    private static void tap(WorldDominationGame app,float x,float y)throws Exception{
        Vector2 p=((Viewport)field(WorldDominationGame.class,"viewport").get(app)).project(new Vector2(x,y));
        int sx=Math.round(p.x),sy=Gdx.graphics.getHeight()-Math.round(p.y);
        app.touchDown(sx,sy,0,0);app.touchUp(sx,sy,0,0);
    }
    private static void render(WorldDominationGame app)throws Exception{
        field(WorldDominationGame.class,"skipNextFrameDelta").setBoolean(app,true);app.render();
    }
    private static void advance(WorldDominationGame app,int n)throws Exception{
        Method m=WorldDominationGame.class.getDeclaredMethod("fixedUpdate",float.class);m.setAccessible(true);
        for(int i=0;i<n;i++)m.invoke(app,1f/24f);
    }
    private static void call(Object app,String name)throws Exception{Method m=app.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(app);}
    private static Field field(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
}
