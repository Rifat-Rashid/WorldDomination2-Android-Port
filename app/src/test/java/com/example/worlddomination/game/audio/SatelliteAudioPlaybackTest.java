package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Music;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class SatelliteAudioPlaybackTest {
    @Test public void preparedVoicesAreIndependentAndCompletionStartsMatchingAttackOnce() {
        Factory factory = new Factory();
        SatelliteAudioPlayback playback = new SatelliteAudioPlayback(factory::create, .6f);
        playback.prepare(2);
        assertEquals(4, factory.created.size());
        for (FakeMusic music : factory.created) {
            assertEquals(0, music.plays);
            assertFalse("Satellite cues are one-shot even when factory defaults to looping",
                    music.looping);
        }
        assertTrue(playback.play());
        assertTrue(playback.play());
        FakeMusic laserOne = factory.created.get(0);
        FakeMusic attackOne = factory.created.get(1);
        FakeMusic laserTwo = factory.created.get(2);
        FakeMusic attackTwo = factory.created.get(3);
        assertEquals(1, laserOne.plays);
        assertEquals(1, laserTwo.plays);
        assertEquals(0, attackOne.plays);
        assertEquals(0, attackTwo.plays);
        laserTwo.complete();
        laserTwo.complete();
        assertEquals(1, attackTwo.plays);
        assertEquals(0, attackOne.plays);
        laserOne.complete();
        assertEquals(1, attackOne.plays);
        attackOne.complete();
        attackOne.complete();
        assertEquals(1, attackOne.plays);
        assertTrue("An extra reveal receives a fresh pair", playback.play());
        assertEquals(6, factory.created.size());
        assertEquals(1, factory.created.get(4).plays);
        playback.dispose();
        for (FakeMusic music : factory.created) assertEquals(1, music.disposals);
    }

    @Test public void muteChangesVolumeWithoutBreakingAnExistingChain() {
        Factory factory = new Factory();
        SatelliteAudioPlayback playback = new SatelliteAudioPlayback(factory::create, .7f);
        playback.prepare(1);
        assertTrue(playback.play());
        playback.setMuted(true);
        for (FakeMusic music : factory.created) assertEquals(0f, music.volume, 0f);
        factory.created.get(0).complete();
        assertEquals(1, factory.created.get(1).plays);
        assertEquals(0f, factory.created.get(1).volume, 0f);
        playback.setMuted(false);
        for (FakeMusic music : factory.created) assertEquals(.7f, music.volume, 0f);
        playback.finish();
    }

    @Test public void pauseDefersCompletionAndResumesOnlyTheCurrentCue() {
        Factory factory = new Factory();
        SatelliteAudioPlayback playback = new SatelliteAudioPlayback(factory::create, 1f);
        playback.prepare(2);
        playback.play();
        playback.play();
        FakeMusic firstLaser = factory.created.get(0);
        FakeMusic firstAttack = factory.created.get(1);
        FakeMusic secondLaser = factory.created.get(2);
        FakeMusic secondAttack = factory.created.get(3);
        playback.pause();
        assertEquals(1, firstLaser.pauses);
        assertEquals(1, secondLaser.pauses);
        firstLaser.complete();
        assertEquals(0, firstAttack.plays);
        playback.resume();
        assertEquals("Finished laser must not restart", 1, firstLaser.plays);
        assertEquals(1, firstAttack.plays);
        assertEquals(2, secondLaser.plays);
        playback.pause();
        firstAttack.complete();
        secondLaser.complete();
        playback.resume();
        assertEquals("Finished attack must not restart", 1, firstAttack.plays);
        assertEquals("Finished laser must not restart", 2, secondLaser.plays);
        assertEquals(1, secondAttack.plays);
        playback.finish();
    }

    @Test public void finishPrepareAndDisposeRejectLateOrDuplicateCallbacks() {
        Factory factory = new Factory();
        SatelliteAudioPlayback playback = new SatelliteAudioPlayback(factory::create, 1f);
        playback.prepare(1);
        playback.play();
        FakeMusic oldLaser = factory.created.get(0);
        FakeMusic oldAttack = factory.created.get(1);
        playback.finish();
        assertFalse(playback.play());
        oldLaser.complete();
        oldAttack.complete();
        assertEquals(0, oldAttack.plays);
        assertEquals(1, oldLaser.disposals);
        playback.prepare(1);
        assertTrue(playback.play());
        oldLaser.complete();
        assertEquals(0, factory.created.get(3).plays);
        factory.created.get(2).complete();
        assertEquals(1, factory.created.get(3).plays);
        playback.dispose();
        factory.created.get(2).complete();
        assertEquals(1, factory.created.get(3).plays);
        playback.dispose();
        for (FakeMusic music : factory.created) assertEquals(1, music.disposals);
    }

    @Test public void failedPartialVoiceDisposesItsOtherMusicAndCanRecover() {
        List<FakeMusic> created = new ArrayList<>();
        final boolean[] failAttack = {true};
        SatelliteAudioPlayback playback = new SatelliteAudioPlayback(cue -> {
            if (cue == OriginalAudio.Cue.SATELLITE_ATTACK && failAttack[0]) return null;
            FakeMusic music = new FakeMusic();
            created.add(music);
            return music;
        }, .5f);
        playback.prepare(1);
        assertEquals(1, created.size());
        assertEquals(1, created.get(0).disposals);
        failAttack[0] = false;
        assertTrue(playback.play());
        assertEquals(3, created.size());
        assertEquals(1, created.get(1).plays);
        playback.finish();
        assertEquals(1, created.get(1).disposals);
        assertEquals(1, created.get(2).disposals);
    }

    @Test public void prepareWhilePausedKeepsNewVoicesSilentUntilResume() {
        Factory factory = new Factory();
        SatelliteAudioPlayback playback = new SatelliteAudioPlayback(factory::create, 1f);
        playback.pause();
        playback.prepare(1);
        assertFalse(playback.play());
        assertEquals(0, factory.created.get(0).plays);
        playback.resume();
        assertTrue(playback.play());
        assertEquals(1, factory.created.get(0).plays);
        playback.dispose();
    }

    @Test public void originalAudioGatesMutedStartsWithoutReplayingOnUnmute() {
        Factory factory = new Factory();
        OriginalAudio audio = new OriginalAudio(new SatelliteAudioPlayback(factory::create, .8f));
        audio.prepareSatelliteAudio(1);
        assertFalse(audio.toggleSound());
        audio.play(OriginalAudio.Cue.SATELLITE_LASER);
        assertEquals(0, factory.created.get(0).plays);
        assertTrue(audio.toggleSound());
        assertEquals("Unmute cannot replay a previously suppressed start",
                0, factory.created.get(0).plays);
        audio.play(OriginalAudio.Cue.SATELLITE_LASER);
        assertEquals(1, factory.created.get(0).plays);
        audio.finishSatelliteAudio();
        audio.dispose();
    }

    @Test public void originalAudioMuteKeepsCompletionFlowAndMusicToggleIsIndependent() {
        Factory factory = new Factory();
        OriginalAudio audio = new OriginalAudio(new SatelliteAudioPlayback(factory::create, .8f));
        audio.prepareSatelliteAudio(1);
        audio.play(OriginalAudio.Cue.SATELLITE_LASER);
        assertEquals(1, factory.created.get(0).plays);
        assertFalse(audio.toggleSound());
        assertTrue(audio.isMusicEnabled());
        factory.created.get(0).complete();
        assertEquals(1, factory.created.get(1).plays);
        assertEquals(0f, factory.created.get(1).volume, 0f);
        assertFalse(audio.toggleMusic());
        assertFalse(audio.isSoundEnabled());
        assertTrue(audio.toggleSound());
        assertEquals(.8f, factory.created.get(1).volume, 0f);
        assertFalse(audio.isMusicEnabled());
        audio.dispose();
    }

    @Test public void originalAudioRelaysPauseResumeFinishAndDispose() {
        Factory factory = new Factory();
        OriginalAudio audio = new OriginalAudio(new SatelliteAudioPlayback(factory::create, .8f));
        audio.prepareSatelliteAudio(1);
        audio.play(OriginalAudio.Cue.SATELLITE_LASER);
        audio.pause();
        assertEquals(1, factory.created.get(0).pauses);
        factory.created.get(0).complete();
        assertEquals(0, factory.created.get(1).plays);
        audio.resume();
        assertEquals(1, factory.created.get(1).plays);
        audio.finishSatelliteAudio();
        factory.created.get(0).complete();
        assertEquals(1, factory.created.get(1).plays);
        assertEquals(1, factory.created.get(0).disposals);
        assertEquals(1, factory.created.get(1).disposals);
        audio.dispose();
        assertEquals(1, factory.created.get(0).disposals);
        assertEquals(1, factory.created.get(1).disposals);
    }

    private static final class Factory {
        final List<FakeMusic> created = new ArrayList<>();

        Music create(OriginalAudio.Cue cue) {
            FakeMusic music = new FakeMusic();
            created.add(music);
            return music;
        }
    }

    private static final class FakeMusic implements Music {
        int plays;
        int pauses;
        int disposals;
        float volume;
        boolean playing;
        boolean looping = true;
        float position;
        OnCompletionListener listener;

        void complete() {
            playing = false;
            if (listener != null) listener.onCompletion(this);
        }

        @Override public void play() { plays++; playing = true; }
        @Override public void pause() { pauses++; playing = false; }
        @Override public void stop() { playing = false; }
        @Override public boolean isPlaying() { return playing; }
        @Override public void setLooping(boolean looping) { this.looping = looping; }
        @Override public boolean isLooping() { return looping; }
        @Override public void setVolume(float volume) { this.volume = volume; }
        @Override public float getVolume() { return volume; }
        @Override public void setPan(float pan, float volume) { this.volume = volume; }
        @Override public void setPosition(float position) { this.position = position; }
        @Override public float getPosition() { return position; }
        @Override public void dispose() { disposals++; }
        @Override public void setOnCompletionListener(OnCompletionListener listener) {
            this.listener = listener;
        }
    }
}
