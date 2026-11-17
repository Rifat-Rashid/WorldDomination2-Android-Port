package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Music;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** The laser and attack cues belong to one moving satellite, not a global pan slot. */
public final class SatelliteAudioPanTest {
    @Test public void independentOwnersCarryPanAcrossOutOfOrderCompletionsAtSourceGain() {
        Fixture f = new Fixture();
        f.playback.prepare(2);
        SatelliteAudioPlayback.Owner left = f.playback.claim();
        SatelliteAudioPlayback.Owner right = f.playback.claim();
        assertNotNull(left);
        assertNotNull(right);
        left.setPan(-.75f);
        right.setPan(.5f);
        assertTrue(left.playLaser());
        assertTrue(right.playLaser());
        assertFalse(left.playLaser());
        f.music(0).assertMix(-.75f, 1f);
        f.music(2).assertMix(.5f, 1f);

        right.setPan(.8f);
        f.music(2).complete();
        f.music(3).assertMix(.8f, 1f);
        assertEquals(1, f.music(3).plays);
        assertEquals(0, f.music(1).plays);
        left.setPan(-.2f);
        f.music(0).complete();
        f.music(1).assertMix(-.2f, 1f);
        assertEquals(1, f.music(1).plays);
        f.music(0).complete();
        assertEquals(1, f.music(1).plays);
        f.playback.finish();
    }

    @Test public void suppressedOwnerRetainsSlotAndPanWhileLegacyDisabledPlayConsumesNothing() {
        Fixture f = new Fixture();
        f.playback.prepare(2);
        SatelliteAudioPlayback.Owner first = f.playback.claim();
        first.setPan(-1.3f);
        f.playback.setSoundEnabled(false);
        assertFalse(first.playLaser());
        assertFalse(f.playback.play());
        assertEquals(0, f.music(0).plays);
        f.playback.setOutputMuted(true);
        first.setPan(Float.NaN);
        f.music(0).assertMix(-1f, 0f);
        f.playback.setSoundEnabled(true);
        assertTrue(first.playLaser());
        assertTrue(f.playback.play()); // The legacy reveal gets the second pair.
        assertEquals(1, f.music(0).plays);
        assertEquals(1, f.music(2).plays);
        f.music(0).complete();
        f.music(1).assertMix(-1f, 0f);
        assertEquals(1, f.music(1).plays);
        f.playback.setOutputMuted(false);
        f.music(1).assertMix(-1f, 1f);
        f.music(3).assertMix(0f, 1f);
        f.playback.finish();
    }

    @Test public void pauseDefersMatchingAttackAndFinishClosesOwnersAndLateCallbacks() {
        Fixture f = new Fixture();
        f.playback.prepare(1);
        SatelliteAudioPlayback.Owner old = f.playback.claim();
        old.setPan(.35f);
        assertTrue(old.playLaser());
        f.playback.pause();
        f.music(0).complete();
        assertEquals(0, f.music(1).plays);
        old.setPan(.65f);
        f.playback.resume();
        assertEquals(1, f.music(1).plays);
        f.music(1).assertMix(.65f, 1f);
        f.playback.finish();
        old.setPan(-1f);
        assertFalse(old.playLaser());
        f.music(0).complete();
        assertEquals(1, f.music(1).plays);
        assertEquals(1, f.music(0).disposals);
        assertEquals(1, f.music(1).disposals);

        f.playback.prepare(1);
        SatelliteAudioPlayback.Owner next = f.playback.claim();
        assertNotNull(next);
        assertTrue(next.playLaser());
        f.music(0).complete();
        assertEquals(0, f.music(3).plays);
        f.music(2).complete();
        assertEquals(1, f.music(3).plays);
        f.playback.dispose();
        assertFalse(next.playLaser());
        assertEquals(1, f.music(2).disposals);
        assertEquals(1, f.music(3).disposals);
    }

    private static final class Fixture {
        final List<FakeMusic> created = new ArrayList<>();
        final SatelliteAudioPlayback playback = new SatelliteAudioPlayback(cue -> {
            FakeMusic music = new FakeMusic();
            created.add(music);
            return music;
        }, 1f);

        FakeMusic music(int index) { return created.get(index); }
    }

    private static final class FakeMusic implements Music {
        OnCompletionListener listener;
        int plays, disposals;
        float pan, volume;
        boolean playing, looping;

        void complete() {
            playing = false;
            if (listener != null) listener.onCompletion(this);
        }

        void assertMix(float expectedPan, float expectedVolume) {
            assertEquals(expectedPan, pan, 0f);
            assertEquals(expectedVolume, volume, 0f);
        }

        @Override public void play() { plays++; playing = true; }
        @Override public void pause() { playing = false; }
        @Override public void stop() { playing = false; }
        @Override public boolean isPlaying() { return playing; }
        @Override public void setLooping(boolean value) { looping = value; }
        @Override public boolean isLooping() { return looping; }
        @Override public void setVolume(float value) { volume = value; }
        @Override public float getVolume() { return volume; }
        @Override public void setPan(float pan, float value) { this.pan = pan; volume = value; }
        @Override public void setPosition(float value) { }
        @Override public float getPosition() { return 0f; }
        @Override public void dispose() { disposals++; playing = false; }
        @Override public void setOnCompletionListener(OnCompletionListener value) { listener = value; }
    }
}
