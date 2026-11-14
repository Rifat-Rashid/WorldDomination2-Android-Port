package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Music;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Fake Music completions only; no Android playback is exercised here. */
public final class AntiDefenseAudioPlaybackTest {
    @Test public void independentRotationAndAirShotUseCompletionInsteadOfTickCooldown() {
        Factory factory = new Factory();
        Queue dispatcher = new Queue();
        AntiDefenseAudioPlayback audio = new AntiDefenseAudioPlayback(factory::create, dispatcher::post);
        AntiDefenseAudioPlayback.Owner first = audio.createOwner(AntiDefenseAudioPlayback.Type.AIR);
        AntiDefenseAudioPlayback.Owner second = audio.createOwner(AntiDefenseAudioPlayback.Type.AIR);

        assertTrue(first.requestRotation());
        assertFalse(first.requestRotation());
        assertTrue(first.requestShot());
        assertFalse(first.requestShot());
        assertTrue(second.requestRotation());
        assertEquals(OriginalAudio.Cue.ANTI_AIR_ROTATE, factory.cues.get(0));
        assertEquals(OriginalAudio.Cue.ANTI_AIR_GUN, factory.cues.get(1));
        assertEquals(OriginalAudio.Cue.ANTI_AIR_ROTATE, factory.cues.get(2));
        assertEquals(1, (int) factory.repeats.get(0));
        assertEquals(8, (int) factory.repeats.get(1));
        assertEquals(1, (int) factory.repeats.get(2));
        for (FakeMusic music : factory.created) {
            assertEquals(0f, music.pan, 0f);
            assertEquals(1f, music.volume, 0f);
            assertFalse(music.looping);
        }

        FakeMusic firstRotation = factory.created.get(0);
        firstRotation.complete();
        assertFalse(first.requestRotation()); // Completion is queued, not yet handled.
        dispatcher.runAll();
        assertTrue(first.requestRotation());
        assertFalse(second.requestRotation());

        FakeMusic gun = factory.created.get(1);
        assertEquals(1, gun.plays);
        assertEquals("one prepared stream covers all eight source repeats", 4,
                factory.created.size()); // Three rotations, one shot group.
        gun.complete();
        assertFalse(first.requestShot());
        dispatcher.runAll();
        assertTrue("one final group completion rearms the source owner", first.requestShot());
        assertEquals(8, (int) factory.repeats.get(4));
        gun.deliverStaleCompletion();
        dispatcher.runAll();
        assertEquals("prior group cannot complete the next group", 1,
                factory.created.get(4).plays);
        audio.finish();
    }

    @Test public void rocketShotCompletesOnceAndMuteBeforeFirstShotLatches() {
        Factory factory = new Factory();
        Queue dispatcher = new Queue();
        AntiDefenseAudioPlayback audio = new AntiDefenseAudioPlayback(factory::create, dispatcher::post);
        AntiDefenseAudioPlayback.Owner rocket = audio.createOwner(AntiDefenseAudioPlayback.Type.ROCKET);
        AntiDefenseAudioPlayback.Owner mutedFirst = audio.createOwner(AntiDefenseAudioPlayback.Type.AIR);
        audio.setMuted(true);
        assertFalse(mutedFirst.requestRotation());
        assertFalse(mutedFirst.requestShot());
        assertEquals(0, factory.created.size());
        audio.setMuted(false);
        assertFalse("source done=false has no callback to rearm", mutedFirst.requestShot());
        assertTrue(mutedFirst.requestRotation());
        assertTrue(rocket.requestShot());
        assertEquals(OriginalAudio.Cue.ANTI_ROCKET_FIRE, factory.cues.get(1));
        assertEquals(1, (int) factory.repeats.get(1));
        FakeMusic shot = factory.created.get(1);
        shot.complete();
        assertFalse(rocket.requestShot());
        dispatcher.runAll();
        assertTrue(rocket.requestShot());
        audio.dispose();
    }

    @Test public void muteAfterStartPreservesFiniteGroupAtZeroVolume() {
        Factory factory = new Factory();
        Queue dispatcher = new Queue();
        AntiDefenseAudioPlayback audio = new AntiDefenseAudioPlayback(factory::create, dispatcher::post);
        AntiDefenseAudioPlayback.Owner owner = audio.createOwner(AntiDefenseAudioPlayback.Type.AIR);
        assertTrue(owner.requestShot());
        FakeMusic first = factory.created.get(0);
        audio.setMuted(true);
        assertEquals(0f, first.volume, 0f);
        assertEquals(8, (int) factory.repeats.get(0));
        assertFalse(owner.requestShot());
        assertEquals(1, factory.created.size());
        first.complete();
        dispatcher.runAll();
        assertEquals("muting an active source group does not cancel completion", 1,
                first.disposals);
        audio.setMuted(false);
        assertTrue(owner.requestShot());
        assertEquals(2, factory.created.size());
        audio.finish();
    }

    @Test public void queuedCompletionPauseResumeAndFinishRejectStaleVoices() {
        Factory factory = new Factory();
        Queue dispatcher = new Queue();
        AntiDefenseAudioPlayback audio = new AntiDefenseAudioPlayback(factory::create, dispatcher::post);
        AntiDefenseAudioPlayback.Owner owner = audio.createOwner(AntiDefenseAudioPlayback.Type.AIR);
        assertTrue(owner.requestShot());
        FakeMusic first = factory.created.get(0);
        first.complete(); // Native completion is queued before lifecycle pause.
        audio.pause();
        audio.resume();
        dispatcher.runAll();
        assertEquals("finished first play is not restarted", 1, first.plays);
        assertEquals(1, factory.created.size());
        assertTrue(owner.requestShot());
        FakeMusic second = factory.created.get(1);
        first.deliverStaleCompletion();
        dispatcher.runAll();
        assertEquals("old group cannot complete the next shot group", 2,
                factory.created.size());
        assertFalse(owner.requestShot());
        audio.pause();
        assertEquals(1, second.pauses);
        audio.resume();
        dispatcher.runAll();
        assertEquals(2, second.plays);
        second.complete();
        audio.finish();
        dispatcher.runAll();
        assertEquals(2, factory.created.size());
        assertEquals(1, first.disposals);
        assertEquals(1, second.disposals);
        assertFalse(owner.requestShot());
        AntiDefenseAudioPlayback.Owner next = audio.createOwner(AntiDefenseAudioPlayback.Type.ROCKET);
        assertTrue(next.requestShot());
        second.deliverStaleCompletion();
        dispatcher.runAll();
        assertEquals(3, factory.created.size());
        audio.dispose();
        audio.dispose();
        for (FakeMusic music : factory.created) assertEquals(1, music.disposals);
    }

    @Test public void backendMayPauseBeforeTheOwnerWithoutImplyingCompletion() {
        Factory factory = new Factory();
        Queue dispatcher = new Queue();
        AntiDefenseAudioPlayback audio = new AntiDefenseAudioPlayback(factory::create, dispatcher::post);
        AntiDefenseAudioPlayback.Owner owner = audio.createOwner(AntiDefenseAudioPlayback.Type.ROCKET);
        assertTrue(owner.requestShot());
        FakeMusic shot = factory.created.get(0);
        shot.pause(); // Android backend lifecycle can run before the game listener.
        audio.pause();
        audio.resume();
        dispatcher.runAll();
        assertEquals(2, shot.plays);
        assertEquals(1, factory.created.size());
        shot.complete();
        dispatcher.runAll();
        assertTrue(owner.requestShot());
        audio.dispose();
    }

    @Test public void optionalMasterGainChangesMixOnlyAndKeepsCenterPan() {
        Factory factory = new Factory();
        Queue dispatcher = new Queue();
        AntiDefenseAudioPlayback audio = new AntiDefenseAudioPlayback(
                factory::create, .8f, dispatcher::post);
        AntiDefenseAudioPlayback.Owner owner = audio.createOwner(AntiDefenseAudioPlayback.Type.ROCKET);
        assertTrue(owner.requestRotation());
        assertTrue(owner.requestShot());
        for (FakeMusic music : factory.created) {
            assertEquals(0f, music.pan, 0f);
            assertEquals(.8f, music.volume, 0f);
        }
        audio.setMuted(true);
        for (FakeMusic music : factory.created) assertEquals(0f, music.volume, 0f);
        audio.setMuted(false);
        for (FakeMusic music : factory.created) assertEquals(.8f, music.volume, 0f);
        audio.dispose();
    }

    private static final class Factory {
        final List<OriginalAudio.Cue> cues = new ArrayList<>();
        final List<Integer> repeats = new ArrayList<>();
        final List<FakeMusic> created = new ArrayList<>();
        Music create(OriginalAudio.Cue cue, int repeatCount) {
            cues.add(cue);
            repeats.add(repeatCount);
            FakeMusic music = new FakeMusic();
            created.add(music);
            return music;
        }
    }

    private static final class Queue {
        final List<Runnable> pending = new ArrayList<>();
        void post(Runnable action) { pending.add(action); }
        void runAll() { while (!pending.isEmpty()) pending.remove(0).run(); }
    }

    private static final class FakeMusic implements Music {
        int plays, pauses, disposals;
        float pan, volume;
        boolean playing, looping = true;
        OnCompletionListener listener;
        void complete() { playing = false; deliverStaleCompletion(); }
        void deliverStaleCompletion() {
            if (listener != null) listener.onCompletion(this);
        }
        @Override public void play() { plays++; playing = true; }
        @Override public void pause() { pauses++; playing = false; }
        @Override public void stop() { playing = false; }
        @Override public boolean isPlaying() { return playing; }
        @Override public void setLooping(boolean value) { looping = value; }
        @Override public boolean isLooping() { return looping; }
        @Override public void setVolume(float value) { volume = value; }
        @Override public float getVolume() { return volume; }
        @Override public void setPan(float newPan, float newVolume) {
            pan = newPan; volume = newVolume;
        }
        @Override public void setPosition(float position) { }
        @Override public float getPosition() { return 0; }
        @Override public void dispose() { disposals++; }
        @Override public void setOnCompletionListener(OnCompletionListener value) { listener = value; }
    }
}
