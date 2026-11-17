package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import static org.junit.Assert.*;

/** Replays captured source gain and pan observations through the production owner. */
public final class SourceAircraftGainTest {
    @Test public void fighterUsesSourceGainThroughFadeAndMute() throws Exception {
        verifyCapturedGain("plane1", AircraftAudioPlayback.Kind.FIGHTER,
                OriginalAudio.Cue.FIGHTER_TAKEOFF, OriginalAudio.Cue.FIGHTER_FLIGHT);
    }

    @Test public void carrierUsesSourceGainThroughFadeAndMute() throws Exception {
        verifyCapturedGain("plane2", AircraftAudioPlayback.Kind.CARRIER,
                OriginalAudio.Cue.CARRIERJET_TAKEOFF, OriginalAudio.Cue.CARRIERJET_FLIGHT);
    }

    private static void verifyCapturedGain(String family, AircraftAudioPlayback.Kind kind,
            OriginalAudio.Cue takeoffCue, OriginalAudio.Cue flightCue) throws Exception {
        JsonValue callback = named(fixture("source-aircraft-audio-callbacks.json"), family)
                .get("stages").get(1).get("events").get(1);
        JsonValue tail = named(fixture("source-aircraft-audio-tails.json"), family);
        JsonValue fade = null;
        for (JsonValue row = tail.get("sampledChangedRows").child; row != null; row = row.next) {
            if (row.getInt("tick") == tail.getInt("firstFadeTick")) fade = row;
        }
        assertNotNull(fade);
        assertEquals(100, callback.getInt("volume"));
        assertEquals(98, fade.getInt("soundVolume"));

        OriginalAudio audio = new OriginalAudio();
        try {
            Field playbackField = OriginalAudio.class.getDeclaredField("aircraftPlayback");
            playbackField.setAccessible(true);
            AircraftAudioPlayback playback = (AircraftAudioPlayback) playbackField.get(audio);
            List<OriginalAudio.Cue> cues = new ArrayList<>();
            List<FakeMusic> sounds = new ArrayList<>();
            Function<OriginalAudio.Cue, Music> factory = cue -> {
                cues.add(cue);
                FakeMusic music = new FakeMusic();
                sounds.add(music);
                return music;
            };
            Field factoryField = AircraftAudioPlayback.class.getDeclaredField("factory");
            factoryField.setAccessible(true);
            factoryField.set(playback, factory);

            AircraftAudioPlayback.Owner owner = audio.createAircraftAudio(kind);
            assertTrue(owner.requestTakeoff());
            assertEquals(takeoffCue, cues.get(0));
            assertEquals(flightCue, cues.get(1));
            FakeMusic takeoff = sounds.get(0), flight = sounds.get(1);
            assertEquals(1f, takeoff.volume, .00001f);
            owner.setSourcePanPercent(callback.getInt("pan"));
            takeoff.complete();
            assertEquals(1, flight.plays);
            assertEquals(callback.getInt("volume") / 100f, flight.volume, .00001f);
            assertEquals(callback.getInt("pan") / 100f, flight.pan, .00001f);
            owner.setSourceVolumePercent(fade.getInt("soundVolume"));
            owner.setSourcePanPercent(fade.getInt("soundPan"));
            assertEquals(.98f, flight.volume, .00001f);
            int takeoffPlays = takeoff.plays, flightPlays = flight.plays;
            audio.toggleOutputMuted();
            assertEquals(0f, flight.volume, .00001f);
            audio.toggleOutputMuted();
            assertEquals(.98f, flight.volume, .00001f);
            assertEquals(takeoffPlays, takeoff.plays);
            assertEquals(flightPlays, flight.plays);
            assertEquals(fade.getInt("soundPan") / 100f, flight.pan, .00001f);
            audio.dispose();
            assertEquals(1, takeoff.disposals);
            assertEquals(1, flight.disposals);
            takeoff.complete();
            flight.complete();
            assertEquals(flightPlays, flight.plays);
        } finally {
            audio.dispose();
        }
    }

    private static JsonValue fixture(String name) throws Exception {
        try (InputStream in = SourceAircraftGainTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(name, in);
            return new JsonReader().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
    private static JsonValue named(JsonValue root, String family) {
        for (JsonValue row = root.get("cases").child; row != null; row = row.next) {
            if (family.equals(row.getString("family"))) return row;
        }
        throw new AssertionError("Missing source case " + family);
    }
    private static final class FakeMusic implements Music {
        float volume, pan;
        int plays, disposals;
        boolean playing, looping;
        OnCompletionListener listener;
        void complete() { playing = false; listener.onCompletion(this); }
        @Override public void play() { plays++; playing = true; }
        @Override public void pause() { playing = false; }
        @Override public void stop() { playing = false; }
        @Override public boolean isPlaying() { return playing; }
        @Override public void setLooping(boolean value) { looping = value; }
        @Override public boolean isLooping() { return looping; }
        @Override public void setVolume(float value) { volume = value; }
        @Override public float getVolume() { return volume; }
        @Override public void setPan(float value, float gain) { pan = value; volume = gain; }
        @Override public void setPosition(float value) { }
        @Override public float getPosition() { return 0; }
        @Override public void dispose() { disposals++; }
        @Override public void setOnCompletionListener(OnCompletionListener value) { listener = value; }
    }
}
