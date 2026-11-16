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
    @Test public void finiteSlotTwoFlightMatchesAll459SourceHostStates() throws Exception {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream in = androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().getContext().getAssets()
                .open("source-finite-prepared-airfield-slot2.csv")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1;) {
                digest.update(buffer, 0, n); bytes.write(buffer, 0, n);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b & 255));
            assertEquals("source capture bytes", "73741278558be0efeea96c77415768fe8930c2927a1f8b863f7f887f8f9f2688", hex.toString());
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    new java.io.ByteArrayInputStream(bytes.toByteArray()), java.nio.charset.StandardCharsets.UTF_8))) {
                assertEquals("tick,x,y,action,status,angle,moving,privateRngNext,canonicalRngNext,activeIndex", reader.readLine());
                for (String line; (line = reader.readLine()) != null;) {
                    String[] values = line.split(",", -1); assertEquals(10, values.length); rows.add(values);
                }
            }
        }
        assertEquals(459, rows.size());
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameSetup setup = new GameSetup(0L); setup.setDifficulty(1);
            assertTrue(setup.toggleOpponent(0)); assertTrue(setup.toggleOpponent(1));
            GameState game = new GameState(setup);
            production(game, "soldier", 50); production(game, "planes", 100); production(game, "bioload", 100);
            decline(game); game.selectAction(GameState.Action.ATTACK);
            assertTrue(game.configureAttack(1, 2, "soldier1")); assertTrue(game.acceptSelectedAction());
            game.selectAction(GameState.Action.PREPARED_ATTACK);
            assertTrue(game.configurePreparedCarrier("plane1")); assertTrue(game.acceptSelectedAction());
            field(WorldDominationGame.class, "game").set(app, game);
            BattlefieldCityRenderer cities = (BattlefieldCityRenderer) field(WorldDominationGame.class, "cityRenderer").get(app);
            cities.reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class, "battleRecordClock").get(app)).reset();
            Method fixed = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class); fixed.setAccessible(true);
            invoke(app, "confirmTurnAndPreparePlayback"); checkBuilding(app);
            for (int i = 0; i < 1600 && cities.isBattleActive(); i++) fixed.invoke(app, 1f / 24f);
            assertFalse("preparation handed off", cities.isBattleActive());
            decline(game); game.selectAction(GameState.Action.PREPARED_ATTACK);
            assertTrue(game.configurePreparedLaunch(1, 2, "bioload1")); assertTrue(game.acceptSelectedAction()); resources(game);
            invoke(app, "confirmTurnAndPreparePlayback"); checkBuilding(app); assertEquals(5, game.getTurn());
            @SuppressWarnings("unchecked") List<FlashStandalonePlaneAnimation> planes =
                    (List<FlashStandalonePlaneAnimation>) field(WorldDominationGame.class, "standalonePlaneAnimations").get(app);
            FlashStandalonePlaneAnimation plane = null;
            for (FlashStandalonePlaneAnimation candidate : planes)
                if (candidate.targetPlayerId() == 1 && "plane001".equals(candidate.getRoute().linkage)) plane = candidate;
            assertNotNull(plane); assertEquals(2, plane.getRoute().actionBuildingSlot);
            SourceAircraftPlayback core = plane.getSourcePlayback(); assertNotNull(core);
            com.example.worlddomination.game.simulation.ReferenceRandom rng =
                    (com.example.worlddomination.game.simulation.ReferenceRandom) field(WorldDominationGame.class, "battlePresentationRandom").get(app);
            double canonical = game.createPresentationRandomCopy().nextDouble();
            for (int tick = 0; tick < rows.size(); tick++) {
                if (tick > 0) fixed.invoke(app, 1f / 24f);
                String[] row = rows.get(tick); String label = "finite slot 2 host " + tick;
                assertEquals(label, tick, Integer.parseInt(row[0]));
                assertEquals(label + " x", Double.parseDouble(row[1]), core.getX(), 1e-9);
                assertEquals(label + " y", Double.parseDouble(row[2]), core.getY(), 1e-9);
                assertEquals(label + " action", row[3], core.getAction());
                assertEquals(label + " status", row[4], core.getUnitStatus());
                assertEquals(label + " angle", Double.parseDouble(row[5]), core.getAngle(), 0d);
                assertEquals(label + " moving", Boolean.parseBoolean(row[6]), core.isActiveAndMoving());
                assertEquals(label + " private RNG", Double.parseDouble(row[7]), rng.copy().nextDouble(), 0d);
                assertEquals(label + " canonical source RNG", Double.parseDouble(row[8]), canonical, 0d);
                assertEquals(label + " canonical frozen", canonical, game.createPresentationRandomCopy().nextDouble(), 0d);
                assertEquals(label + " retained wrapper", Integer.parseInt(row[9]) >= 0, !core.isRemoved());
            }
            assertFalse("flight handed off", cities.isBattleActive());
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
