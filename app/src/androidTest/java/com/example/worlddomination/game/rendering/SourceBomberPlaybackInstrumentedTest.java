package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Legal source bomber turns through the real World fixed tick and display root. */
@RunWith(AndroidJUnit4.class)
public final class SourceBomberPlaybackInstrumentedTest {
    @Test public void legalSuccessRetainsBodyAfterWrapperUntilSourceCleanup() throws Exception {
        compare("success", 16, 571);
    }

    @Test public void legalInterceptionKeepsEffectThroughSourceCleanup() throws Exception {
        compare("intercept", 24, 363);
    }

    private static void compare(String name, int finalTurn, int lastTick) throws Exception {
        JsonValue oracle = source(name);
        assertEquals("source-plane002-compact-v1", oracle.getString("schema"));
        assertEquals(lastTick, oracle.getInt("lastTick"));
        assertEquals(finalTurn, oracle.get("schedule").size);
        assertEquals("source trail dimensions are unknown, not finite geometry",
                "_width", oracle.get("trailFields").get(5).asString());
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = legalGame(oracle);
            field(WorldDominationGame.class, "game").set(app, game);
            BattlefieldCityRenderer cities = (BattlefieldCityRenderer)
                    field(WorldDominationGame.class, "cityRenderer").get(app);
            cities.reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,
                    "battleRecordClock").get(app)).reset();
            invoke(app, "confirmTurnAndPreparePlayback");
            assertEquals("source attack follows the final preparation turn",
                    finalTurn + 1, game.getTurn());
            JsonValue sourceEvent = oracle.get("events").get(1);
            assertEquals(21, sourceEvent.getInt("_weapon"));
            assertEquals(7, sourceEvent.getInt("_city"));
            assertPlaneEvent(game, sourceEvent);

            @SuppressWarnings("unchecked")
            List<FlashStandalonePlaneAnimation> planes =
                    (List<FlashStandalonePlaneAnimation>)
                            field(WorldDominationGame.class,
                                    "standalonePlaneAnimations").get(app);
            assertEquals("one human bomber", 1, planes.size());
            FlashStandalonePlaneAnimation plane = planes.get(0);
            assertEquals("plane002", plane.getRoute().linkage);
            assertEquals(2, plane.getRoute().targetCityIndex);
            SourceAircraftPlayback core = plane.getSourcePlayback();
            assertNotNull("bomber must use source playback", core);
            SourceAircraftTrail trail = plane.getSourceTrail();
            assertNotNull("paired source trail", trail);
            assertTrue(trail.isPaired());
            @SuppressWarnings("unchecked")
            Map<SourceAircraftTrail, SourceAircraftTrailDisplay> trails =
                    (Map<SourceAircraftTrail, SourceAircraftTrailDisplay>)
                            field(WorldDominationGame.class, "sourceAircraftTrails").get(app);
            SourceAircraftTrailDisplay trailDisplay = trails.get(trail);
            assertNotNull("battle root owns paired smoke", trailDisplay);
            @SuppressWarnings("unchecked")
            List<SourceInterceptedAircraftDisplayDepths> bindings =
                    (List<SourceInterceptedAircraftDisplayDepths>)
                            field(WorldDominationGame.class,
                                    "sourceInterceptedAircraftBindings").get(app);
            assertEquals("one physical bomber role binding", 1, bindings.size());
            SourceInterceptedAircraftDisplayDepths binding = bindings.get(0);
            BattlefieldDisplayState display = (BattlefieldDisplayState)
                    field(WorldDominationGame.class, "battlefieldDisplay").get(app);
            ReferenceRandom privateRandom = (ReferenceRandom)
                    field(WorldDominationGame.class,
                            "battlePresentationRandom").get(app);
            assertNotNull(privateRandom);
            double canonical = game.createPresentationRandomCopy().nextDouble();
            JsonValue rows = oracle.get("rows");
            JsonValue nextTrailEvent = oracle.get("trailEvents").child;
            int bornTrailClips = 0;
            for (int tick = 0; tick <= lastTick; tick++) {
                if (tick > 0) advance(app);
                while (nextTrailEvent != null && nextTrailEvent.getInt("tick") == tick) {
                    if ("attach".equals(nextTrailEvent.getString("event")))
                        bornTrailClips++;
                    nextTrailEvent = nextTrailEvent.next;
                }
                JsonValue row = rows.get(tick);
                JsonValue unit = row.get(1);
                JsonValue mc = row.get(2);
                JsonValue shadow = row.get(4);
                JsonValue tint = row.get(6);
                FlashStandalonePlaneAnimation.Actor actor = plane.getActor();
                assertEquals("action t" + tick, unit.get(0).asString(), core.getAction());
                assertEquals("status t" + tick, unit.get(1).asString(), core.getUnitStatus());
                assertEquals("moving t" + tick, unit.get(2).asBoolean(),
                        core.isActiveAndMoving());
                assertEquals("target trigger t" + tick, unit.get(3).asBoolean(),
                        field(SourceAircraftPlayback.class, "atTarget").getBoolean(core));
                assertEquals("waypoint t" + tick, unit.get(4).asInt(),
                        core.getCurrentWaypoint());
                sourceNumber("logical X", unit.get(5), core.getX(), tick, .001);
                sourceNumber("logical Y", unit.get(6), core.getY(), tick, .001);
                sourceNumber("previous X", unit.get(7), core.getPreviousX(), tick, .001);
                sourceNumber("previous Y", unit.get(8), core.getPreviousY(), tick, .001);
                sourceNumber("xStep", unit.get(9), core.getXStep(), tick, .001);
                sourceNumber("yStep", unit.get(10), core.getYStep(), tick, .001);
                assertEquals("active frame clock t" + tick, unit.get(17).asInt(),
                        core.getActiveFrames());
                assertEquals("free-flight clock t" + tick, unit.get(19).asInt(),
                        field(SourceAircraftPlayback.class, "freeFlightCount").getInt(core));
                assertEquals("circular-air clock t" + tick, unit.get(18).asInt(),
                        field(SourceAircraftPlayback.class, "circularAirTime").getInt(core));
                assertEquals("circular frame clock t" + tick, unit.get(23).asInt(),
                        field(SourceAircraftPlayback.class, "circularFrames").getInt(core));
                assertEquals("orbit countdown t" + tick, unit.get(20).asInt(),
                        field(SourceAircraftPlayback.class, "circularCountdown").getInt(core));
                sourceNumber("orbit angle", unit.get(21),
                        field(SourceAircraftPlayback.class, "circularAngle").getDouble(core),
                        tick, .001);
                sourceNumber("orbit radius", unit.get(22),
                        field(SourceAircraftPlayback.class, "circularRadius").getDouble(core),
                        tick, .001);
                assertEquals("fly-off clock t" + tick, unit.get(26).asInt(),
                        field(SourceAircraftPlayback.class, "flyOffCount").getInt(core));
                sourceNumber("source shadow offset", unit.get(25),
                        core.getShadowYOffset(), tick, .001);
                assertEquals("trail active t" + tick, unit.get(28).asBoolean(),
                        trail.isTrailActive());
                sourceNumber("MC X", mc.get(0), actor.sourceX, tick, .001);
                sourceNumber("MC Y", mc.get(1), actor.sourceY, tick, .001);
                sourceNumber("source double render X", mc.get(0), core.getRenderX(),
                        tick, .001);
                sourceNumber("source double render Y", mc.get(1), core.getRenderY(),
                        tick, .001);
                assertEquals("body frame t" + tick, mc.get(2).asInt() - 1,
                        actor.sourceFrame);
                assertEquals("independent tint frame t" + tick,
                        tint.get(2).asInt() - 1, actor.sourceTintFrame);
                assertEquals("source tint timeline t" + tick,
                        tint.get(2).asInt(), core.getTintFrame());
                assertEquals("nested shadow direction t" + tick,
                        row.get(13).asInt(), actor.sourceTintFrame + 1);
                sourceNumber("body alpha", mc.get(3), actor.alpha * 100d, tick, .001);
                sourceNumber("body source alpha", mc.get(3), core.getAlpha(), tick, .001);
                sourceNumber("body scale", mc.get(4), actor.scale * 100d, tick, .001);
                sourceNumber("body source scale", mc.get(4), core.getScalePercent(),
                        tick, .001);
                assertEquals("body visible/attached t" + tick, row.get(3).asBoolean(),
                        actor.visible);
                assertEquals("wrapper t" + tick, row.get(0).asBoolean(),
                        !core.isRemoved());
                sourceNumber("shadow X", shadow.get(0), actor.sourceX, tick, .001);
                sourceNumber("shadow Y", shadow.get(1),
                        actor.sourceY + actor.shadowYOffset, tick, .001);
                sourceNumber("shadow scale", shadow.get(4), actor.scale * 100d,
                        tick, .001);
                sourceNumber("tint X", tint.get(0), actor.sourceX, tick, .001);
                sourceNumber("tint Y", tint.get(1), actor.sourceY, tick, .001);
                sourceNumber("tint scale", tint.get(4), actor.scale * 100d,
                        tick, .001);
                assertEquals("body physical attachment t" + tick,
                        row.get(3).asBoolean(), binding.body().isAttached());
                assertEquals("shadow physical attachment t" + tick,
                        row.get(5).asBoolean(), binding.shadow().isAttached());
                assertEquals("tint physical attachment t" + tick,
                        row.get(3).asBoolean(), binding.tint().isAttached());
                if (tick < lastTick) {
                    assertTrue("battle root active t" + tick, cities.isBattleActive());
                    assertEquals("source transient and retained trail births t" + tick,
                            bornTrailClips, trail.getCreatedCount());
                    compareTrails(row.get(10), trail, trailDisplay, display, tick);
                    compareExplosion(row, binding, tick);
                } else {
                    assertFalse("source handoff to canonical t" + tick,
                            cities.isBattleActive());
                    assertFalse(binding.body().isAttached());
                    assertFalse(binding.shadow().isAttached());
                    assertFalse(binding.tint().isAttached());
                }
                assertEquals("private RNG t" + tick, row.get(11).asDouble(),
                        privateRandom.copy().nextDouble(), 0d);
                assertEquals("canonical RNG isolation t" + tick, canonical,
                        game.createPresentationRandomCopy().nextDouble(), 0d);
                if (snapshotTick(name, tick)) snapshot(app, name, tick);
            }
            assertNull("all source trail operations consumed", nextTrailEvent);
            TurnPlaybackSequence playback = (TurnPlaybackSequence)
                    field(WorldDominationGame.class, "turnPlaybackSequence").get(app);
            assertEquals(TurnPlaybackSequence.Phase.IDLE, playback.getPhase());
        });
    }

    private static void compareTrails(JsonValue source, SourceAircraftTrail trail,
                                      SourceAircraftTrailDisplay owner,
                                      BattlefieldDisplayState display, int tick) {
        assertEquals("attached physical smoke count t" + tick, source.size,
                trail.getAttachedCount());
        for (int slot = 0; slot < trail.getSlotCount(); slot++) {
            String name = "unit1" + trail.getClipNameSuffix(slot);
            JsonValue expected = null;
            for (JsonValue candidate = source.child; candidate != null;
                    candidate = candidate.next)
                if (name.equals(candidate.get(0).asString())) expected = candidate;
            assertEquals("slot attachment " + name + " t" + tick,
                    expected != null, trail.isAttached(slot));
            if (expected == null) continue;
            int depth = expected.get(1).asInt();
            assertEquals("smoke depth " + name + " t" + tick, depth,
                    trail.getDepth(slot));
            sourceNumber("smoke X " + name, expected.get(2), trail.getX(slot),
                    tick, .001);
            sourceNumber("smoke Y " + name, expected.get(3), trail.getY(slot),
                    tick, .001);
            sourceNumber("smoke alpha " + name, expected.get(4),
                    trail.getAlpha(slot), tick, .001);
            sourceNumber("smoke growth " + name, expected.get(7),
                    trail.getSizePlus(slot), tick, .001);
            sourceNumber("smoke decay " + name, expected.get(8),
                    trail.getAlphaMin(slot), tick, .001);
            assertTrue("source width is unknown", expected.get(5).isNull());
            assertTrue("source height is unknown", expected.get(6).isNull());
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry =
                    display.clips().atDepth(depth);
            assertNotNull("root smoke " + name + " t" + tick, entry);
            assertTrue(entry.isAttached());
            assertEquals(name, entry.getValue().name);
            assertSame(owner, entry.getValue().owner);
        }
    }

    private static void compareExplosion(JsonValue row,
                                         SourceInterceptedAircraftDisplayDepths binding,
                                         int tick) {
        JsonValue source = row.get(7);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> actual = binding.explosion();
        if (source.isNull()) { assertNull("no premature explosion t" + tick, actual); return; }
        assertNotNull("source effect exists t" + tick, actual);
        assertEquals("effect attachment t" + tick, row.get(9).asBoolean(),
                actual.isAttached());
        if (row.get(9).asBoolean()) {
            assertEquals("effect root depth t" + tick, row.get(8).asInt(),
                    actual.getDepth());
            assertEquals("effect frame t" + tick, source.get(2).asInt() - 1,
                    binding.getExplosionFrameIndex());
            sourceNumber("effect X", source.get(0), binding.getExplosionX(),
                    tick, .001);
            sourceNumber("effect Y", source.get(1), binding.getExplosionY(),
                    tick, .001);
        }
    }

    private static GameState legalGame(JsonValue source) {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        JsonValue schedule = source.get("schedule");
        for (JsonValue step = schedule.child; step != null; step = step.next) {
            while (game.getPendingHumanDiplomacyProposal() != null)
                assertTrue(game.respondToDiplomacyProposal(false));
            assertEquals("legal turn number", step.getInt("turn") - 1,
                    game.getTurn());
            assertEquals("resources", step.getString("second"));
            JsonValue options = step.get("options");
            switch (step.getString("action")) {
                case "research":
                    game.selectAction(GameState.Action.RESEARCH);
                    assertTrue(game.selectResearchTechnology(options.getString("tech")));
                    break;
                case "production":
                    game.selectAction(GameState.Action.PRODUCTION);
                    assertTrue(game.configureProduction(options.getString("category"),
                            options.getInt("percent")));
                    break;
                case "preAttack":
                    game.selectAction(GameState.Action.PREPARED_ATTACK);
                    assertTrue(game.configurePreparedCarrier(options.getString("weapon")));
                    break;
                default: throw new AssertionError("Unexpected legal source action");
            }
            assertTrue(game.acceptSelectedAction());
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue(game.acceptSelectedAction());
            game.confirmTurn();
        }
        while (game.getPendingHumanDiplomacyProposal() != null)
            assertTrue(game.respondToDiplomacyProposal(false));
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertEquals("preAttack", schedule.get(schedule.size - 1).getString("action"));
        assertTrue(game.configurePreparedLaunch(1, 2, "warhead1"));
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        assertEquals(2, game.getQueuedActionCount());
        return game;
    }

    private static void assertPlaneEvent(GameState game, JsonValue source) {
        GameState.CombatEvent found = null;
        for (GameState.CombatEvent event : game.getLastTurnCombatEvents()) {
            if (event.attackerPlayerId != 0 || !"plane2".equals(event.weaponName))
                continue;
            assertNull("one human bomber event", found);
            found = event;
        }
        assertNotNull("legal human bomber event", found);
        assertEquals(1, found.defenderPlayerId);
        assertEquals(2, found.cityIndex);
        assertEquals(source.getBoolean("_result")
                        ? GameState.CombatEventType.STRIKE_IMPACT
                        : GameState.CombatEventType.STRIKE_INTERCEPTED,
                found.type);
        assertEquals(source.getInt("_casualties"), found.casualtiesMillions);
    }

    private static void sourceNumber(String label, JsonValue expected,
                                     double actual, int tick, double tolerance) {
        if (expected.isNull()) assertTrue(label + " undefined t" + tick,
                Double.isNaN(actual));
        else assertEquals(label + " t" + tick, expected.asDouble(), actual,
                tolerance);
    }

    private static JsonValue source(String name) throws Exception {
        try (InputStream input = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open("source-plane002-" + name + ".json")) {
            return new JsonReader().parse(input);
        }
    }

    private static boolean snapshotTick(String name, int tick) {
        return "success".equals(name) ? tick == 272 || tick == 509 || tick == 570
                : tick == 195 || tick == 299 || tick == 362;
    }

    private static void snapshot(WorldDominationGame app, String name, int tick)
            throws Exception {
        Method capture = SourceBattleCleanupInstrumentedTest.class.getDeclaredMethod(
                "capture", WorldDominationGame.class, String.class);
        capture.setAccessible(true);
        capture.invoke(null, app, "source-plane002-" + name + "-t" + tick + ".png");
    }

    private static void advance(WorldDominationGame app) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        method.setAccessible(true);
        method.invoke(app, 1f / 24f);
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
