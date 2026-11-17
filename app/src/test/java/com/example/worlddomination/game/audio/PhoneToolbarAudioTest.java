package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public final class PhoneToolbarAudioTest {
    @Test public void outputMutePreservesOptionsThemeAndEveryExistingSoundInstance() throws Exception {
        Fixture f = new Fixture();
        long first = f.audio.playInstance(OriginalAudio.Cue.WARROCKET, -.75f);
        long second = f.audio.playInstance(OriginalAudio.Cue.WARROCKET, .25f);
        assertTrue(f.audio.toggleOutputMuted());
        assertTrue(f.audio.isSoundEnabled());
        assertTrue(f.audio.isMusicEnabled());
        f.sound.assertVoice(first, -.75f, 0f);
        f.sound.assertVoice(second, .25f, 0f);
        assertEquals(0f, f.theme.volume, 0f);
        assertFalse(f.audio.toggleOutputMuted());
        f.sound.assertVoice(first, -.75f, .8f);
        f.sound.assertVoice(second, .25f, .8f);
        assertEquals(1f, f.theme.volume, 0f);
        assertEquals(2, f.sound.starts);
        assertEquals(0, f.sound.stops + f.sound.pauses + f.sound.resumes);
        assertEquals(0, f.theme.starts + f.theme.stops + f.theme.pauses);
        assertTrue(f.theme.playing);
    }

    @Test public void newEffectsAndLoopsStartSilentlyAndPanChangesSurviveUnmute() throws Exception {
        Fixture f = new Fixture();
        f.audio.toggleOutputMuted();
        long effect = f.audio.playInstance(OriginalAudio.Cue.WARROCKET, -.3f);
        long loop = f.audio.loop(OriginalAudio.Cue.WARROCKET);
        f.sound.assertVoice(effect, -.3f, 0f);
        f.sound.assertVoice(loop, 0f, 0f);
        f.audio.setInstancePan(OriginalAudio.Cue.WARROCKET, effect, 2f);
        f.audio.setInstancePan(OriginalAudio.Cue.WARROCKET, loop, -.8f);
        f.sound.assertVoice(effect, 1f, 0f);
        f.audio.toggleOutputMuted();
        f.sound.assertVoice(effect, 1f, .8f);
        f.sound.assertVoice(loop, -.8f, .8f);
        assertEquals(2, f.sound.starts);
        assertEquals(0, f.sound.stops);
    }

    @Test public void gearGatesNewStartsWithoutStoppingOrMutingRetainedVoices() throws Exception {
        Fixture f = new Fixture();
        long existing = f.audio.playInstance(OriginalAudio.Cue.WARROCKET, .4f);
        assertFalse(f.audio.toggleSound());
        assertEquals(-1L, f.audio.playInstance(OriginalAudio.Cue.WARROCKET, 0f));
        assertEquals(-1L, f.audio.loop(OriginalAudio.Cue.WARROCKET));
        f.sound.assertVoice(existing, .4f, .8f);
        assertEquals(1f, f.theme.volume, 0f);
        assertFalse(f.audio.isOutputMuted());
        assertEquals(0, f.sound.stops);
        f.audio.toggleOutputMuted();
        f.audio.toggleOutputMuted();
        assertFalse(f.audio.isSoundEnabled());
        f.sound.assertVoice(existing, .4f, .8f);
        assertTrue(f.audio.toggleSound());
        assertEquals(1, f.sound.starts);
        assertTrue(f.audio.playInstance(OriginalAudio.Cue.WARROCKET, 0f) >= 0);
    }

    @Test public void disabledAmbientRequestPreservesOverlappingBattleVoices() throws Exception {
        Fixture f = new Fixture();
        f.audio.beginBattleAmbient();
        f.audio.startBuildingAmbient("resources");
        long first = f.sound.nextId;
        f.audio.startBuildingAmbient("resources");
        long second = f.sound.nextId;
        assertEquals(2, f.sound.starts);
        assertEquals(9999, f.sound.finitePlays);
        f.sound.assertVoice(first, 0f, .21f);
        f.sound.assertVoice(second, 0f, .21f);
        f.audio.toggleSound();
        f.audio.startBuildingAmbient("game");
        assertEquals(2, f.sound.starts);
        assertEquals(0, f.sound.stops);
        f.audio.toggleOutputMuted();
        f.sound.assertVoice(first, 0f, 0f);
        f.sound.assertVoice(second, 0f, 0f);
        f.audio.toggleOutputMuted();
        f.sound.assertVoice(first, 0f, .21f);
        f.sound.assertVoice(second, 0f, .21f);
        f.audio.toggleSound();
        f.audio.startBuildingAmbient("resources");
        assertEquals(3, f.sound.starts);
        assertEquals(0, f.sound.stops);
        f.audio.finishBattleAmbient();
        assertEquals(3, f.sound.stops);
    }

    @Test public void musicOptionAndLifecycleKeepOutputMuteAndSoundOptionIndependent() throws Exception {
        Fixture f = new Fixture();
        long id = f.audio.playInstance(OriginalAudio.Cue.WARROCKET, .6f);
        f.audio.toggleOutputMuted();
        f.audio.toggleSound();
        f.audio.pause();
        f.audio.resume();
        f.sound.assertVoice(id, .6f, 0f);
        assertEquals(1, f.sound.pauses);
        assertEquals(1, f.sound.resumes);
        assertTrue(f.theme.playing);
        assertEquals(0f, f.theme.volume, 0f);
        f.audio.toggleMusic();
        assertFalse(f.theme.playing);
        f.audio.toggleMusic();
        assertTrue(f.theme.playing);
        assertEquals(0f, f.theme.volume, 0f);
        assertFalse(f.audio.isSoundEnabled());
        f.audio.toggleOutputMuted();
        assertEquals(1f, f.theme.volume, 0f);
        f.sound.assertVoice(id, .6f, .8f);
    }

    @Test public void stoppedIdsAndFailedStartsAreNeverRestoredByUnmute() throws Exception {
        Fixture f = new Fixture();
        long id = f.audio.playInstance(OriginalAudio.Cue.WARROCKET, 0f);
        f.audio.stop(OriginalAudio.Cue.WARROCKET, id);
        f.sound.failStart = true;
        assertEquals(-1L, f.audio.playInstance(OriginalAudio.Cue.WARROCKET, 0f));
        int changes = f.sound.changes;
        f.audio.toggleOutputMuted();
        f.audio.toggleOutputMuted();
        f.audio.setInstancePan(OriginalAudio.Cue.WARROCKET, id, .5f);
        assertEquals(changes, f.sound.changes);
        assertEquals(1, f.sound.stops);
    }

    @Test public void expiredFiniteHandlesRetireWithoutStoppingOrDroppingLongLivedLoops() {
        AtomicLong clock = new AtomicLong();
        SoundVoiceRegistry registry = new SoundVoiceRegistry(clock::get);
        FakeSound sound = new FakeSound();
        registry.register(sound, 1L, .8f, -.2f, false, 100L);
        registry.register(sound, 2L, .6f, .7f, true, 100L);
        clock.set(100L);
        registry.setMuted(true);
        sound.assertVoice(1L, -.2f, 0f);
        sound.assertVoice(2L, .7f, 0f);
        clock.incrementAndGet();
        registry.setMuted(false);
        sound.assertVoice(1L, -.2f, 0f); // Expired id was not touched again.
        sound.assertVoice(2L, .7f, .6f);
        assertEquals(0, sound.stops);
    }

    @Test public void pausedTimeDoesNotExpireSoundHandlesAndResumeDoesNotUnmuteThem() {
        AtomicLong clock = new AtomicLong();
        SoundVoiceRegistry registry = new SoundVoiceRegistry(clock::get);
        FakeSound sound = new FakeSound();
        registry.register(sound, 3L, .8f, .1f, false, 100L);
        clock.set(50L);
        registry.pause();
        clock.set(1_000_000L);
        registry.setMuted(true);
        registry.pan(sound, 3L, -.4f);
        registry.resume();
        sound.assertVoice(3L, -.4f, 0f);
        registry.setMuted(false);
        sound.assertVoice(3L, -.4f, .8f);
        assertEquals(1, sound.pauses);
        assertEquals(1, sound.resumes);
    }

    @Test public void overlappingIdsAreScopedToTheirSoundAndDuplicateHandleReplacesOnlyItself() {
        SoundVoiceRegistry registry = new SoundVoiceRegistry(() -> 0L);
        FakeSound a = new FakeSound(), b = new FakeSound();
        registry.register(a, 1, .8f, -.5f, false, 100);
        registry.register(b, 1, .6f, .2f, true, 100);
        registry.register(a, 1, .4f, .9f, false, 100);
        registry.setMuted(true);
        registry.forget(b, 1);
        registry.setMuted(false);
        a.assertVoice(1, .9f, .4f);
        b.assertVoice(1, .2f, 0f);
        assertEquals(2, a.changes);
        assertEquals(1, b.changes);
    }

    @Test public void asynchronousBackendOwnsHandleLifetimeUntilItRetiresTheVoice() {
        AtomicLong clock = new AtomicLong();
        SoundVoiceRegistry registry = new SoundVoiceRegistry(clock::get);
        class OwnedSound extends FakeSound implements SoundHandleOwner {
            boolean retained = true;
            @Override public boolean isVoiceRetained(long id) { return retained; }
        }
        OwnedSound sound = new OwnedSound();
        registry.register(sound, 1, .8f, .3f, false, 100);
        clock.set(1_000_000L); // Loading may take longer than encoded playback.
        registry.setMuted(true);
        sound.assertVoice(1, .3f, 0f);
        registry.setMuted(false);
        sound.assertVoice(1, .3f, .8f);
        sound.retained = false; // Native failure/stop/proven expiry, not a request timer.
        int changes = sound.changes;
        registry.setMuted(true);
        assertEquals(changes, sound.changes);
        assertEquals(0, sound.stops);
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static final class Fixture {
        final OriginalAudio audio;
        final FakeSound sound = new FakeSound();
        FakeMusic theme = new FakeMusic();
        @SuppressWarnings("unchecked") Fixture() throws Exception {
            audio = new OriginalAudio(() -> {
                theme = new FakeMusic();
                return theme;
            });
            Map<OriginalAudio.Cue, Sound> sounds =
                    (Map<OriginalAudio.Cue, Sound>) field(audio, "sounds");
            sounds.put(OriginalAudio.Cue.WARROCKET, sound);
            sounds.put(OriginalAudio.Cue.GAME_AMBIENT, sound);
            sounds.put(OriginalAudio.Cue.RESOURCES_AMBIENT, sound);
            Field f = OriginalAudio.class.getDeclaredField("theme");
            f.setAccessible(true);
            f.set(audio, theme);
            // The fixture injects a Music that is already playing, as initialize does.
            f = OriginalAudio.class.getDeclaredField("themeActive");
            f.setAccessible(true);
            f.setBoolean(audio, true);
        }
    }

    private static class FakeSound implements FiniteRepeatSound {
        final Map<Long, float[]> voices = new HashMap<>();
        long nextId;
        int starts, stops, pauses, resumes, changes;
        boolean failStart;
        int finitePlays;
        public long playFinite(float volume, float pitch, float pan, int totalPlays) {
            finitePlays = totalPlays;
            return play(volume, pitch, pan);
        }
        void assertVoice(long id, float pan, float volume) {
            assertNotNull(voices.get(id));
            assertEquals(pan, voices.get(id)[0], 0f);
            assertEquals(volume, voices.get(id)[1], 0f);
        }
        public long play() { return play(1f); }
        public long play(float volume) { return play(volume, 1f, 0f); }
        public long play(float volume, float pitch, float pan) {
            if (failStart) return -1;
            starts++;
            voices.put(++nextId, new float[]{pan, volume});
            return nextId;
        }
        public long loop() { return loop(1f); }
        public long loop(float volume) { return loop(volume, 1f, 0f); }
        public long loop(float volume, float pitch, float pan) { return play(volume, pitch, pan); }
        public void stop() { stops++; }
        public void stop(long id) { stops++; }
        public void pause() { pauses++; }
        public void pause(long id) { pauses++; }
        public void resume() { resumes++; }
        public void resume(long id) { resumes++; }
        public void dispose() { }
        public void setLooping(long id, boolean value) { }
        public void setPitch(long id, float value) { }
        public void setVolume(long id, float value) { setPan(id, voices.get(id)[0], value); }
        public void setPan(long id, float pan, float volume) {
            changes++;
            voices.put(id, new float[]{pan, volume});
        }
    }

    private static final class FakeMusic implements Music {
        float volume = 1f;
        boolean playing = true, looping;
        int starts, stops, pauses;
        public void play() { starts++; playing = true; }
        public void pause() { pauses++; playing = false; }
        public void stop() { stops++; playing = false; }
        public boolean isPlaying() { return playing; }
        public void setLooping(boolean value) { looping = value; }
        public boolean isLooping() { return looping; }
        public void setVolume(float value) { volume = value; }
        public float getVolume() { return volume; }
        public void setPan(float pan, float value) { volume = value; }
        public void setPosition(float value) { }
        public float getPosition() { return 0f; }
        public void dispose() { }
        public void setOnCompletionListener(OnCompletionListener listener) { }
    }
}
