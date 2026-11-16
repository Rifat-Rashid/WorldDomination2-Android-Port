package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.Assert.*;

/** A legal World preparation retains its physical airfield on the next turn. */
@RunWith(AndroidJUnit4.class)
public final class SourcePreparedAirfieldLocationInstrumentedTest {
    @Test public void slotTwoAirfieldRetainsPreparedPositionAcrossWorldPlayback() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameSetup setup = new GameSetup(0L); setup.setDifficulty(1);
            assertTrue(setup.toggleOpponent(0)); assertTrue(setup.toggleOpponent(1));
            GameState game = new GameState(setup);
            production(game, "soldier", 50); production(game, "planes", 100);
            production(game, "bioload", 100);
            decline(game);
            game.selectAction(GameState.Action.ATTACK);
            assertTrue(game.configureAttack(1,2,"soldier1"));
            assertTrue(game.acceptSelectedAction());
            game.selectAction(GameState.Action.PREPARED_ATTACK);
            assertTrue(game.configurePreparedCarrier("plane1"));
            assertTrue(game.acceptSelectedAction());
            field(WorldDominationGame.class,"game").set(app,game);
            BattlefieldCityRenderer cities=(BattlefieldCityRenderer)field(WorldDominationGame.class,"cityRenderer").get(app);
            cities.reset(game);
            ((SourceBattleRecordClock)field(WorldDominationGame.class,"battleRecordClock").get(app)).reset();
            Method fixed=WorldDominationGame.class.getDeclaredMethod("fixedUpdate",float.class);
            fixed.setAccessible(true);
            invoke(app,"confirmTurnAndPreparePlayback");
            assertEquals(4,game.getTurn());
            checkBuilding(app);
            for(int i=0;i<1600 && cities.isBattleActive();i++) fixed.invoke(app,1f/24f);
            assertFalse("preparation battle handed off",cities.isBattleActive());
            decline(game);
            game.selectAction(GameState.Action.PREPARED_ATTACK);
            assertTrue(game.configurePreparedLaunch(1,2,"bioload1"));
            assertTrue(game.acceptSelectedAction()); resources(game);
            invoke(app,"confirmTurnAndPreparePlayback");
            assertEquals(5,game.getTurn());
            checkBuilding(app);
            GameState.CombatEvent event=null;
            for(GameState.CombatEvent e:game.getLastTurnCombatEvents())
                if(e.attackerPlayerId==0 && "plane1".equals(e.weaponName)) event=e;
            assertNotNull(event); assertEquals(2,event.actionBuildingSlot);
            @SuppressWarnings("unchecked") List<FlashStandalonePlaneAnimation> planes=
                    (List<FlashStandalonePlaneAnimation>)field(WorldDominationGame.class,"standalonePlaneAnimations").get(app);
            FlashStandalonePlaneAnimation plane=null;
            for(FlashStandalonePlaneAnimation candidate:planes)
                if(candidate.targetPlayerId()==1 && "plane001".equals(candidate.getRoute().linkage)) plane=candidate;
            assertNotNull(plane);
            FlashBattleRoutes.StandalonePlaneStrike route=plane.getRoute();
            assertEquals(2,route.actionBuildingSlot);
            assertEquals(442.25,route.start.x,0d); assertEquals(296.75,route.start.y,0d);
            assertEquals(327d,route.liftOff.x,0d); assertEquals(246d,route.liftOff.y,0d);
            assertEquals(173d,route.orbitEntry.x,0d); assertEquals(298d,route.orbitEntry.y,0d);
            assertEquals(212.8,route.bombWaypoint.x,1e-4); assertEquals(66.75,route.bombWaypoint.y,0d);
            assertNotNull(plane.getSourcePlayback());
            double canonical=game.createPresentationRandomCopy().nextDouble();
            for(int tick=0;tick<1600 && cities.isBattleActive();tick++) {
                fixed.invoke(app,1f/24f);
                assertEquals("canonical RNG frozen during playback",canonical,
                        game.createPresentationRandomCopy().nextDouble(),0d);
            }
            assertFalse("battle handed off",cities.isBattleActive());
        });
    }
    private static void checkBuilding(WorldDominationGame app) throws Exception {
        @SuppressWarnings("unchecked") List<FlashActionBuildingAnimation> buildings=
                (List<FlashActionBuildingAnimation>)field(WorldDominationGame.class,"actionBuildingAnimations").get(app);
        FlashActionBuildingAnimation airfield=null;
        for(FlashActionBuildingAnimation b:buildings)
            if(b.playerId==0 && "airfield".equals(b.frameName)) airfield=b;
        assertNotNull("physical airfield",airfield); assertEquals(2,airfield.slot);
        FlashBattleRoutes.Point pos=FlashBattleRoutes.actionBuildingPosition(0,airfield.slot);
        // Source ab0_2 transform is identity with translation (432.25,296.75).
        assertEquals(432.25,pos.x,0d); assertEquals(296.75,pos.y,0d);
    }
    private static void production(GameState game,String category,int percent) {
        decline(game); game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction(category,percent)); assertTrue(game.acceptSelectedAction());
        resources(game); game.confirmTurn();
    }
    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES); assertTrue(game.acceptSelectedAction());
    }
    private static void decline(GameState game) {
        while(game.getPendingHumanDiplomacyProposal()!=null) assertTrue(game.respondToDiplomacyProposal(false));
    }
    private static Field field(Class<?> c,String name) throws Exception { Field f=c.getDeclaredField(name); f.setAccessible(true); return f; }
    private static void invoke(Object o,String name) throws Exception { Method m=o.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(o); }
}
