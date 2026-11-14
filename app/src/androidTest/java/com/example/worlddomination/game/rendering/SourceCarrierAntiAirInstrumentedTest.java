package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.List;

import static org.junit.Assert.*;

/** Legal turn-21 source View lock, retained bearing, and smoke regression. */
@RunWith(AndroidJUnit4.class)
public final class SourceCarrierAntiAirInstrumentedTest {
    @Test public void interceptedShipTracksThirdPhysicalUnitBeforeLiftOff() throws Exception {
        verify("source-carrier-anti-intercept.json", 1);
    }

    @Test public void successfulShipKeepsOrdinaryThirdUnitBearing() throws Exception {
        verify("source-carrier-anti-success.json", 2);
    }

    private static void verify(String fixture, int targetPlayer) throws Exception {
        JsonValue source = read(fixture);
        assertEquals(1, source.getInt("schema"));
        assertEquals(0, source.getInt("seed"));
        assertEquals(21, source.getInt("turn"));
        assertEquals(targetPlayer, source.getInt("target"));
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            OriginalAudio audio = (OriginalAudio) field(WorldDominationGame.class,
                    "audio").get(app);
            boolean restoreMuted = !audio.isSoundEnabled();
            if (restoreMuted) audio.toggleSound();
            try {
                GameState game = SourceBiologicalCarriersInstrumentedTest
                        .gameReadyForShip(targetPlayer);
                field(WorldDominationGame.class, "game").set(app, game);
                BattlefieldCityRenderer cities = (BattlefieldCityRenderer)
                        field(WorldDominationGame.class, "cityRenderer").get(app);
                cities.reset(game);
                ((SourceBattleRecordClock) field(WorldDominationGame.class,
                        "battleRecordClock").get(app)).reset();
                invoke(app, "confirmTurnAndPreparePlayback");
                assertEquals("legal source strike turn", 21, game.getTurn());
                ReferenceRandom privateRandom = (ReferenceRandom)
                        field(WorldDominationGame.class, "battlePresentationRandom").get(app);
                assertNotNull("private presentation stream", privateRandom);
                @SuppressWarnings("unchecked")
                List<FlashCarrierPlaneAnimation> carriers =
                        (List<FlashCarrierPlaneAnimation>) field(WorldDominationGame.class,
                                "carrierPlaneAnimations").get(app);
                assertEquals("three legal ship payloads", 3, carriers.size());
                FlashCarrierPlaneAnimation[] physical = new FlashCarrierPlaneAnimation[3];
                IdentityHashMap<SourceAircraftTrail, Integer> trailOwner = new IdentityHashMap<>();
                for (FlashCarrierPlaneAnimation plane : carriers) {
                    SourceCarrierPlanePlayback core = plane.getSourcePlayback();
                    assertNotNull("source ship core", core);
                    int index = core.getRoute().planeIndex;
                    assertTrue("source physical index", index >= 0 && index < 3);
                    assertNull("unique physical aircraft", physical[index]);
                    physical[index] = plane;
                    trailOwner.put(plane.getSourceTrail(), index + 1);
                }
                assertNotNull("source unit3", physical[2]);
                JsonValue rng = source.get("rng");
                int lastTick = source.getInt("lastTick");
                assertEquals("one source RNG sample per tick", lastTick + 1, rng.size);
                int activation = source.getInt("recordActivationTick");
                int firstTrail = source.getInt("firstUnit3TrailTick");
                JsonValue expectedTrail = source.get("firstUnit3Trail");
                @SuppressWarnings("unchecked")
                List<SourceCarrierPlaneDisplayDepths> rootBindings =
                        (List<SourceCarrierPlaneDisplayDepths>) field(WorldDominationGame.class,
                                "sourceCarrierPlaneBindings").get(app);
                SourceCarrierPlaneDisplayDepths first = null;
                for (SourceCarrierPlaneDisplayDepths binding : rootBindings)
                    if (binding.animation == physical[0]) first = binding;
                assertNotNull("physical unit1 owns its retained root effect", first);
                for (int tick = 0; tick <= lastTick; tick++) {
                    if (tick > 0) advance(app);
                    int firstImpact = targetPlayer == 1 ? 238 : 296;
                    if (tick == firstImpact) {
                        assertNotNull(first.explosion());
                        assertTrue(first.explosion().isAttached());
                        assertEquals("unit1explosion", first.explosion().getValue().name);
                        assertEquals(targetPlayer == 1 ? "explosion001" : "explosion_bioload_3",
                                first.getExplosionLinkage());
                        assertEquals(targetPlayer == 1 ? 800126 : 800176,
                                first.explosion().getDepth());
                        assertEquals(targetPlayer == 1 ? 58.5d : 541d,
                                first.getExplosionX(), 0d);
                        assertEquals(targetPlayer == 1 ? 165.75d : 83d,
                                first.getExplosionY(), 0d);
                        assertEquals("birth frame1", 0, first.getExplosionFrameIndex());
                    }
                    if (tick == (targetPlayer == 1 ? 480 : 563)) {
                        assertTrue("stopped parent retained until cleanup", first.explosion().isAttached());
                        assertEquals(targetPlayer == 1 ? 20 : 54, first.getExplosionFrameIndex());
                    }
                    if (tick == activation) {
                        @SuppressWarnings("unchecked")
                        List<FlashAntiDefenseTrackingAnimation> tracking =
                                (List<FlashAntiDefenseTrackingAnimation>)
                                        field(WorldDominationGame.class,
                                                "antiDefenseTrackingAnimations").get(app);
                        if (targetPlayer == 1) {
                            FlashCarrierPlaneAnimation locked = null;
                            for (FlashAntiDefenseTrackingAnimation anti : tracking) {
                                if (anti.getDefenderPlayerId() == targetPlayer
                                        && anti.getDefenseType()
                                        == FlashAntiDefenseTrackingAnimation.DefenseType.AIR) {
                                    Object target = field(FlashAntiDefenseTrackingAnimation.class,
                                            "target").get(anti);
                                    assertTrue("ship aircraft owns anti target",
                                            target instanceof FlashCarrierPlaneAnimation);
                                    locked = (FlashCarrierPlaneAnimation) target;
                                }
                            }
                            assertNotNull("source defender acquires a target", locked);
                            assertEquals("View visits and locks waiting unit3 first",
                                    Integer.valueOf(3), trailOwner.get(locked.getSourceTrail()));
                            assertEquals("source fixture locked name", "unit3",
                                    source.getString("trackedUnitAtActivation"));
                            assertEquals("still waiting at lock", "waitForLiftOff",
                                    locked.getSourcePlayback().getAction());
                            assertEquals("source lock while movement eligible", 0,
                                    locked.getSourceTrail().getFramesActive());
                        } else {
                            for (FlashAntiDefenseTrackingAnimation anti : tracking)
                                assertFalse("successful target has no air lock",
                                        anti.getDefenderPlayerId() == targetPlayer
                                                && anti.getDefenseType()
                                                == FlashAntiDefenseTrackingAnimation.DefenseType.AIR);
                            assertTrue("source successful fixture has no lock",
                                    source.get("trackedUnitAtActivation").isNull());
                        }
                    }
                    if (tick == firstTrail) {
                        SourceAircraftTrail trail = physical[2].getSourceTrail();
                        assertTrue("source unit3 first smoke attached", trail.isAttached(0));
                        assertEquals("source unit3 first smoke X",
                                expectedTrail.getDouble("x"), trail.getX(0), 1e-9);
                        assertEquals("source unit3 first smoke Y",
                                expectedTrail.getDouble("y"), trail.getY(0), 1e-9);
                        assertEquals("physical smoke depth from independently repaired source",
                                expectedTrail.getInt("depth"), trail.getDepth(0));
                        assertEquals("literal source depth before known host alias",
                                targetPlayer == 1 ? 800126 : 800125,
                                source.get("firstUnit3TrailLiteral").getInt("depth"));
                        JsonValue unit = source.get("firstUnit3State");
                        SourceCarrierPlanePlayback core = physical[2].getSourcePlayback();
                        assertEquals("unit3 source action at birth",
                                unit.getString("action"), core.getAction());
                        assertEquals("unit3 logical X at birth",
                                Double.parseDouble(unit.getString("curX")), core.getX(), .02d);
                        assertEquals("unit3 logical Y at birth",
                                Double.parseDouble(unit.getString("curY")), core.getY(), .02d);
                        assertEquals("unit3 painted X at birth",
                                Double.parseDouble(unit.getString("mcX")), core.getRenderX(), .02d);
                        assertEquals("unit3 painted Y at birth",
                                Double.parseDouble(unit.getString("mcY")), core.getRenderY(), .02d);
                        assertEquals("unit3 source frame at birth",
                                (int) Double.parseDouble(unit.getString("mcFrame")),
                                core.getSourceFrame());
                    }
                    JsonValue sample = rng.get(tick);
                    assertEquals("isolated presentation RNG tick " + tick,
                            sample.get(0).asDouble(), privateRandom.copy().nextDouble(), 0d);
                    assertEquals("canonical game RNG tick " + tick,
                            sample.get(1).asDouble(),
                            game.createPresentationRandomCopy().nextDouble(), 0d);
                }
                assertFalse("source scene handed off", cities.isBattleActive());
                @SuppressWarnings("unchecked")
                java.util.Map<SourceAircraftTrail, SourceAircraftTrailDisplay> displays =
                        (java.util.Map<SourceAircraftTrail, SourceAircraftTrailDisplay>)
                                field(WorldDominationGame.class, "sourceAircraftTrails").get(app);
                assertTrue("source smoke bindings cleared at handoff", displays.isEmpty());
                assertTrue("source carrier effect bindings cleared at handoff", rootBindings.isEmpty());
            } finally {
                if (restoreMuted) audio.toggleSound();
            }
        });
    }

    private static JsonValue read(String name) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext()
                        .getAssets().open(name), StandardCharsets.UTF_8)) {
            return new JsonReader().parse(reader);
        }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }

    private static void advance(WorldDominationGame app) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        method.setAccessible(true);
        method.invoke(app, 1f / 24f);
    }
}
