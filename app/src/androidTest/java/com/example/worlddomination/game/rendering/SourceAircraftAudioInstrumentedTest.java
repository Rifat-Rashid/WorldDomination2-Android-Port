package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.audio.AircraftAudioPlayback;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.audio.SatelliteAudioPlayback;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Real World fixed ticks with a deterministic, explicitly synthetic Music completion. */
@RunWith(AndroidJUnit4.class)
public final class SourceAircraftAudioInstrumentedTest {
    @Test public void legalPlaneOneAudioFollowsSourceVolumePanAndStop() throws Exception {
        compare("plane1", false, 1);
    }

    @Test public void legalInterceptedShipFirstPlaneStopsItsOwnSound() throws Exception {
        compare("ship", true, 1);
    }

    @Test public void legalSuccessfulShipFirstPlaneFadesBeforeHandoff() throws Exception {
        compare("ship_success", true, 2);
    }

    private static void compare(String family, boolean ship, int target) throws Exception {
        JsonValue source = sourceCase(family);
        int firstSoundTick = source.getInt("firstTakeoffRequestTick");
        int stopTick = source.getInt("ownedSoundStopTick");
        int removedTick = source.getInt("wrapperRemovedTick");
        int lastTick = source.getInt("battleLastTick");
        assertEquals("one source Unit visit starts the sound", firstSoundTick,
                source.getInt("firstVisitedTick"));
        assertEquals("source Sound ends before wrapper", stopTick + 1, removedTick);
        assertEquals("synthetic callback boundary is explicit", firstSoundTick,
                source.getInt("syntheticTakeoffCompletionTick"));
        JsonValue human = source.get("humanBattleEvents").get(0);
        assertEquals(target, human.getInt("defender"));
        if (ship) assertEquals("three legal ship payloads", 3,
                human.get("shipAttacks").size);

        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            Field audioField = field(WorldDominationGame.class, "audio");
            OriginalAudio original = (OriginalAudio) audioField.get(app);
            RecordingFactory factory = new RecordingFactory();
            AircraftAudioPlayback ownerAudio = new AircraftAudioPlayback(
                    factory::make, .8f, Runnable::run);
            SatelliteAudioPlayback satellite = new SatelliteAudioPlayback(
                    cue -> null, .8f);
            Constructor<OriginalAudio> constructor = OriginalAudio.class
                    .getDeclaredConstructor(SatelliteAudioPlayback.class,
                            AircraftAudioPlayback.class);
            constructor.setAccessible(true);
            OriginalAudio recordingAudio = constructor.newInstance(satellite, ownerAudio);
            audioField.set(app, recordingAudio);
            try {
                assertTrue("test sound option begins enabled",
                        recordingAudio.isSoundEnabled());
                GameState game = ship
                        ? SourceBiologicalCarriersInstrumentedTest.gameReadyForShip(target)
                        : SourceBiologicalCarriersInstrumentedTest.gameReadyForPlane(true);
                field(WorldDominationGame.class, "game").set(app, game);
                BattlefieldCityRenderer cityRenderer = (BattlefieldCityRenderer)
                        field(WorldDominationGame.class, "cityRenderer").get(app);
                cityRenderer.reset(game);
                ((SourceBattleRecordClock) field(WorldDominationGame.class,
                        "battleRecordClock").get(app)).reset();
                invoke(app, "confirmTurnAndPreparePlayback");
                assertEquals(ship ? 21 : 3, game.getTurn());
                ReferenceRandom privateRandom = (ReferenceRandom)
                        field(WorldDominationGame.class,
                                "battlePresentationRandom").get(app);
                assertNotNull(privateRandom);

                Object unit = firstPhysicalUnit(app, ship);
                JsonValue changed = source.get("sampledChangedRows").child;
                RecordingMusic takeoff = null;
                RecordingMusic flight = null;
                AircraftAudioPlayback.Owner owner = null;
                for (int tick = 0; tick <= lastTick; tick++) {
                    if (tick > 0) advance(app);
                    if (tick == firstSoundTick) {
                        owner = ownerFor(app, unit);
                        assertNotNull("source Unit owns its Sound", owner);
                        takeoff = (RecordingMusic) Proxy.getInvocationHandler(
                                (Music) field(owner.getClass(), "takeoff").get(owner));
                        flight = (RecordingMusic) Proxy.getInvocationHandler(
                                (Music) field(owner.getClass(), "flight").get(owner));
                        assertSame("one Unit owns both media", owner,
                                ownerFor(app, unit));
                        assertEquals(OriginalAudio.Cue.FIGHTER_TAKEOFF, takeoff.cue);
                        assertEquals(OriginalAudio.Cue.FIGHTER_FLIGHT, flight.cue);
                        assertEquals("one audible request", 1, takeoff.playCount);
                        assertEquals("callback has not fired", 0, flight.playCount);
                        double canonicalBefore = game.createPresentationRandomCopy()
                                .nextDouble();
                        double privateBefore = privateRandom.copy().nextDouble();
                        takeoff.complete(); // Synthetic completion, once, as fixture states.
                        assertEquals("completion starts flight", 1, flight.playCount);
                        assertEquals("callback cannot draw canonical RNG", canonicalBefore,
                                game.createPresentationRandomCopy().nextDouble(), 0d);
                        assertEquals("callback cannot draw private battle RNG", privateBefore,
                                privateRandom.copy().nextDouble(), 0d);
                    }
                    if (tick < firstSoundTick)
                        assertEquals("no early aircraft music t" + tick,
                                0, factory.created.size());
                    if (changed != null && tick == changed.getInt("tick")) {
                        if (changed.getBoolean("soundPlaying")
                                && changed.getBoolean("unitSoundSameObject")) {
                            assertNotNull("flight exists on changed row", flight);
                            assertEquals(family + " source volume t" + tick,
                                    .8f * changed.getFloat("soundVolume") / 100f,
                                    flight.volume, .001f);
                            assertEquals(family + " source pan t" + tick,
                                    changed.getFloat("soundPan") / 100f,
                                    flight.pan, .001f);
                        }
                        changed = changed.next;
                    }
                    if (tick == stopTick) {
                        assertNotNull(flight);
                        assertEquals("owned sound stops on source Unit tick", 1,
                                flight.stopCount);
                        int plays = flight.playCount;
                        flight.complete(); // A late callback cannot restart closed audio.
                        assertEquals("closed voice rejects late callback", plays,
                                flight.playCount);
                    }
                    if (tick > stopTick && tick < lastTick && flight != null)
                        assertEquals("no second stop after wrapper removal", 1,
                                flight.stopCount);
                }
                assertEquals("all source changed rows checked", null, changed);
                assertNotNull(owner);
                assertNotNull(takeoff);
                assertNotNull(flight);
                assertFalse("battle has handed off at source last tick",
                        cityRenderer.isBattleActive());
                TurnPlaybackSequence sequence = (TurnPlaybackSequence)
                        field(WorldDominationGame.class,
                                "turnPlaybackSequence").get(app);
                assertEquals(TurnPlaybackSequence.Phase.IDLE,
                        sequence.getPhase());
                assertEquals("physical first Unit stop before its wrapper removal",
                        stopTick + 1, removedTick);
                assertEquals("carrier uses fighter pair, even with three payloads",
                        0, factory.count(OriginalAudio.Cue.CARRIERJET_TAKEOFF));
                assertEquals(0, factory.count(OriginalAudio.Cue.CARRIERJET_FLIGHT));
            } finally {
                audioField.set(app, original);
                recordingAudio.dispose();
            }
        });
    }

    private static Object firstPhysicalUnit(WorldDominationGame app, boolean ship)
            throws Exception {
        if (!ship) {
            @SuppressWarnings("unchecked")
            List<FlashStandalonePlaneAnimation> live =
                    (List<FlashStandalonePlaneAnimation>)
                            field(WorldDominationGame.class,
                                    "standalonePlaneAnimations").get(app);
            assertEquals(1, live.size());
            assertNotNull(live.get(0).getSourcePlayback());
            return live.get(0);
        }
        @SuppressWarnings("unchecked")
        List<FlashCarrierPlaneAnimation> live =
                (List<FlashCarrierPlaneAnimation>)
                        field(WorldDominationGame.class, "carrierPlaneAnimations").get(app);
        assertEquals(3, live.size());
        for (FlashCarrierPlaneAnimation animation : live)
            if (animation.getSourcePlayback().getRoute().planeIndex == 0)
                return animation;
        throw new AssertionError("Missing source physical unit1");
    }

    private static AircraftAudioPlayback.Owner ownerFor(WorldDominationGame app,
                                                         Object unit) throws Exception {
        @SuppressWarnings("unchecked")
        IdentityHashMap<Object, AircraftAudioPlayback.Owner> owners =
                (IdentityHashMap<Object, AircraftAudioPlayback.Owner>)
                        field(WorldDominationGame.class, "aircraftAudioOwners").get(app);
        return owners.get(unit);
    }

    private static JsonValue sourceCase(String name) throws Exception {
        try (InputStream input = InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open("source-aircraft-audio-tails.json")) {
            JsonValue root = new JsonReader().parse(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            assertEquals("source-aircraft-audio-tails-v1", root.getString("schema"));
            assertTrue(root.getString("syntheticBoundaryNotice")
                    .contains("synthetic takeoff completion"));
            for (JsonValue row = root.get("cases").child;
                    row != null; row = row.next)
                if (name.equals(row.getString("family"))) return row;
        }
        throw new AssertionError("Missing source aircraft audio case " + name);
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
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class RecordingFactory {
        final List<RecordingMusic> created = new ArrayList<>();

        Music make(OriginalAudio.Cue cue) {
            RecordingMusic music = new RecordingMusic(cue);
            created.add(music);
            return music.music;
        }

        int count(OriginalAudio.Cue cue) {
            int result = 0;
            for (RecordingMusic music : created) if (music.cue == cue) result++;
            return result;
        }
    }

    private static final class RecordingMusic implements InvocationHandler {
        final OriginalAudio.Cue cue;
        final Music music;
        Music.OnCompletionListener listener;
        float volume;
        float pan;
        int playCount;
        int stopCount;
        boolean playing;

        RecordingMusic(OriginalAudio.Cue cue) {
            this.cue = cue;
            music = (Music) Proxy.newProxyInstance(Music.class.getClassLoader(),
                    new Class<?>[]{Music.class}, this);
        }

        void complete() {
            playing = false;
            if (listener != null) listener.onCompletion(music);
        }

        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "play": playCount++; playing = true; return null;
                case "stop": stopCount++; playing = false; return null;
                case "pause": playing = false; return null;
                case "isPlaying": return playing;
                case "setPan": pan = (Float) args[0]; volume = (Float) args[1]; return null;
                case "setVolume": volume = (Float) args[0]; return null;
                case "getVolume": return volume;
                case "setOnCompletionListener":
                    listener = (Music.OnCompletionListener) args[0]; return null;
                case "setLooping": return null;
                case "isLooping": return false;
                case "dispose": return null;
                case "getPosition": return 0f;
                case "setPosition": return null;
                default:
                    throw new AssertionError("Unexpected Music method " + method.getName());
            }
        }
    }
}
