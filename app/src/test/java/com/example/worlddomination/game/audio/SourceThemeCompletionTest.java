package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Source Sound.start(0,1) and onSoundComplete behavior, without a timing guess. */
public final class SourceThemeCompletionTest {
    @Test public void eachActualCompletionStartsOneFullGainNonloopingPlay() throws Exception {
        Fixture f = new Fixture();
        f.start();
        assertEquals(1, f.music.starts);
        assertEquals(1f, f.music.volume, 0f);
        assertFalse(f.music.looping);
        assertEquals(1, f.music.seeks);
        Music.OnCompletionListener first = f.music.listeners.get(0);
        f.music.complete(first);
        assertEquals(2, f.music.starts);
        assertEquals(2, f.music.seeks);
        Music.OnCompletionListener second = f.music.listeners.get(1);
        f.music.complete(second);
        assertEquals(3, f.music.starts);
        assertEquals(3, f.music.seeks);
        assertEquals(0, f.music.stops);
    }

    @Test public void toggledOffAndOldQueuedCompletionsCannotReviveNewVoice() throws Exception {
        Fixture f = new Fixture();
        f.start();
        FakeMusic old = f.music;
        Music.OnCompletionListener first = old.listeners.get(0);
        assertFalse(f.audio.toggleMusic());
        assertEquals(1, old.stops);
        assertEquals(1, old.disposals);
        old.fireQueued(first);
        assertEquals(1, old.starts);
        assertTrue(f.audio.toggleMusic());
        assertNotSame(old, f.music);
        assertEquals(1, f.music.starts);
        Music.OnCompletionListener second = f.music.listeners.get(0);
        old.fireQueued(first);
        assertEquals(1, f.music.starts);
        f.music.complete(second);
        assertEquals(2, f.music.starts);
        f.audio.dispose();
        f.music.complete(f.music.listeners.get(1));
        assertEquals(2, f.music.starts);
        assertEquals(1, f.music.disposals);
    }

    @Test public void pauseAndBackendAutoPauseResumeWithoutSeekingOrDoubleStart() throws Exception {
        Fixture f = new Fixture();
        f.start();
        f.music.position = 8.5f;
        f.audio.pause();
        f.audio.pause();
        assertEquals(1, f.music.pauses);
        f.audio.resume();
        f.audio.resume();
        assertEquals(2, f.music.starts);
        assertEquals(1, f.music.seeks);
        assertEquals(8.5f, f.music.position, 0f);

        // Android's backend may pause Music before ApplicationListener.pause.
        f.music.playing = false;
        f.music.position = 11f;
        f.audio.pause();
        assertEquals(1, f.music.pauses);
        f.audio.resume();
        assertEquals(3, f.music.starts);
        assertEquals(1, f.music.seeks);
        assertEquals(11f, f.music.position, 0f);
    }

    @Test public void completionArrivingWhilePausedRestartsFromZeroAfterResume() throws Exception {
        Fixture f = new Fixture();
        f.start();
        Music.OnCompletionListener first = f.music.listeners.get(0);
        f.audio.pause();
        f.music.complete(first);
        assertEquals(1, f.music.starts);
        f.audio.resume();
        assertEquals(2, f.music.starts);
        assertEquals(2, f.music.seeks);
        f.music.fireQueued(first);
        assertEquals(2, f.music.starts);
    }

    @Test public void optionOffThenOnDuringPauseDefersAFreshPlayAndRejectsOldCallback()
            throws Exception {
        Fixture f = new Fixture();
        f.start();
        FakeMusic old = f.music;
        Music.OnCompletionListener previous = old.listeners.get(0);
        f.audio.pause();
        assertFalse(f.audio.toggleMusic());
        assertTrue(f.audio.toggleMusic());
        assertNotSame(old, f.music);
        old.fireQueued(previous);
        assertEquals(0, f.music.starts);
        f.audio.resume();
        assertEquals(1, f.music.starts);
        assertEquals(1, f.music.seeks);
        old.fireQueued(previous);
        assertEquals(1, f.music.starts);
    }

    @Test public void androidQueuedOldCompletionCannotReachNewPausedOwner() throws Exception {
        Fixture f = new Fixture();
        f.start();
        FakeMusic old = f.music;
        // AndroidMusic enqueues a Runnable which reads the owner's CURRENT listener.
        Runnable queuedOld = old::fireQueuedCurrent;
        assertFalse(f.audio.toggleMusic());
        assertTrue(f.audio.toggleMusic());
        FakeMusic current = f.music;
        f.audio.pause();
        queuedOld.run();
        f.audio.resume();
        assertEquals("One initial play and one position-preserving resume", 2, current.starts);
        assertEquals(1, current.seeks);
        assertEquals(1, old.disposals);

        f.audio.pause();
        current.complete(current.listener);
        f.audio.resume();
        assertEquals("Actual paused completion starts a new play", 3, current.starts);
        assertEquals(2, current.seeks);
    }

    @Test public void capturedSetupAndTwoCompletionCyclesKeepSourceCommandOrder()
            throws Exception {
        JsonValue rows;
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream("/source-theme-audio/theme-source-trace.json"),
                StandardCharsets.UTF_8)) {
            rows = new JsonReader().parse(reader).get("rows");
        }
        assertEquals(7, rows.size);
        Fixture f = new Fixture();
        assertFalse(f.audio.toggleMusic());
        f.music.events.clear();
        assertTrue(f.audio.toggleMusic());
        assertSourceRow(rows.get(0), f);
        FakeMusic firstOwner = f.music;
        Music.OnCompletionListener first = firstOwner.listeners.get(0);
        f.music.complete(first);
        assertSourceRow(rows.get(1), f);
        f.music.complete(f.music.listeners.get(1));
        assertSourceRow(rows.get(2), f);
        assertFalse(f.audio.toggleMusic());
        assertSourceRow(rows.get(3), f);
        firstOwner.fireQueued(first);
        assertSourceRow(rows.get(4), f);
        assertTrue(f.audio.toggleMusic());
        assertNotSame(firstOwner, f.music);
        assertSourceRow(rows.get(5), f);
        // Source's new-game handoff does not request another theme start.
        assertSourceRow(rows.get(6), f);
    }

    private static void assertSourceRow(JsonValue row, Fixture f) {
        assertEquals(row.getString("label"), row.getBoolean("option"), f.audio.isMusicEnabled());
        assertEquals(row.getString("label"), row.getBoolean("playing"), f.music.playing);
        assertEquals(row.getInt("volume") / 100f, f.music.volume, 0f);
        assertEquals(0, row.getInt("pan"));
        assertEquals(1, row.getInt("loops"));
        assertFalse(f.music.looping);
        List<String> expected = new ArrayList<>();
        for (JsonValue source = row.get("events").child; source != null; source = source.next) {
            String event = source.getString("event");
            assertEquals("WDTheme.wav", source.getString("linkage"));
            if ("start".equals(event)) {
                assertEquals(1, source.getInt("loops"));
                expected.add("start:" + source.getInt("volume") + ":" + source.getInt("pan"));
            } else {
                expected.add(event);
            }
        }
        assertEquals(row.getString("label"), expected, f.music.events);
        f.music.events.clear();
    }

    @Test public void outputMuteAndSoundOptionDoNotGateThemeCompletion() throws Exception {
        Fixture f = new Fixture();
        f.audio.toggleOutputMuted();
        f.start();
        assertEquals(0f, f.music.volume, 0f);
        assertFalse(f.audio.toggleSound());
        f.music.complete(f.music.listeners.get(0));
        assertEquals(2, f.music.starts);
        assertEquals(0f, f.music.volume, 0f);
        f.audio.toggleOutputMuted();
        assertEquals(1f, f.music.volume, 0f);
        assertFalse(f.audio.isSoundEnabled());
    }

    private static final class Fixture {
        final OriginalAudio audio;
        FakeMusic music = new FakeMusic();

        Fixture() throws Exception {
            audio = new OriginalAudio(() -> {
                music = new FakeMusic();
                return music;
            });
            Field theme = OriginalAudio.class.getDeclaredField("theme");
            theme.setAccessible(true);
            theme.set(audio, music);
        }

        void start() {
            assertFalse(audio.toggleMusic());
            music.stops = 0;
            music.events.clear();
            assertTrue(audio.toggleMusic());
        }
    }

    private static final class FakeMusic implements Music {
        final List<OnCompletionListener> listeners = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        OnCompletionListener listener;
        boolean playing, looping;
        float volume, position;
        int starts, stops, pauses, seeks, disposals;

        void complete(OnCompletionListener callback) {
            playing = false;
            events.add("complete");
            callback.onCompletion(this);
        }

        void fireQueued(OnCompletionListener callback) { callback.onCompletion(this); }
        void fireQueuedCurrent() {
            if (listener != null) listener.onCompletion(this);
        }

        @Override public void play() {
            starts++;
            playing = true;
            events.add("start:" + Math.round(volume * 100f) + ":0");
        }
        @Override public void pause() { pauses++; playing = false; }
        @Override public void stop() {
            stops++;
            playing = false;
            position = 0f;
            events.add("stop");
        }
        @Override public boolean isPlaying() { return playing; }
        @Override public void setLooping(boolean value) { looping = value; }
        @Override public boolean isLooping() { return looping; }
        @Override public void setVolume(float value) { volume = value; }
        @Override public float getVolume() { return volume; }
        @Override public void setPan(float pan, float value) { volume = value; }
        @Override public void setPosition(float value) { seeks++; position = value; }
        @Override public float getPosition() { return position; }
        @Override public void dispose() { disposals++; playing = false; }
        @Override public void setOnCompletionListener(OnCompletionListener value) {
            listener = value;
            if (value != null) listeners.add(value);
        }
    }
}
