package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Music;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class AircraftAudioPlaybackTest {
    @Test public void fighterOwnersHaveIndependentRetainedCompletionChains() {
        Factory factory = new Factory();
        AircraftAudioPlayback playback = new AircraftAudioPlayback(factory::create, .8f);
        AircraftAudioPlayback.Owner first = playback.createOwner(AircraftAudioPlayback.Kind.FIGHTER);
        AircraftAudioPlayback.Owner second = playback.createOwner(AircraftAudioPlayback.Kind.FIGHTER);
        assertEquals("sound objects are made at the first audible request", 0, factory.created.size());
        assertTrue(first.requestTakeoff());
        assertFalse("Unit.soundAction repeats for eight source visits", first.requestTakeoff());
        assertTrue(second.requestTakeoff());
        assertEquals(4, factory.created.size());
        assertEquals(OriginalAudio.Cue.FIGHTER_TAKEOFF, factory.cues.get(0));
        assertEquals(OriginalAudio.Cue.FIGHTER_FLIGHT, factory.cues.get(1));
        for (FakeMusic sound : factory.created) assertFalse("source Sound.start is finite", sound.looping);
        FakeMusic takeoffOne = factory.created.get(0), flightOne = factory.created.get(1);
        FakeMusic takeoffTwo = factory.created.get(2), flightTwo = factory.created.get(3);
        assertEquals(1, takeoffOne.plays);
        assertEquals(1, takeoffTwo.plays);
        takeoffOne.complete();
        assertEquals(1, flightOne.plays);
        assertEquals(0, flightTwo.plays);
        flightOne.complete();
        assertEquals("source f12 callback remains attached", 2, flightOne.plays);
        flightOne.completeCallbackWithoutEndingPlayback();
        assertEquals("duplicate queued callback cannot overlap a live replay", 2, flightOne.plays);
        takeoffTwo.complete();
        assertEquals(1, flightTwo.plays);
        first.stop();
        assertEquals(1, takeoffOne.disposals);
        assertEquals(1, flightOne.disposals);
        flightOne.complete();
        assertEquals(2, flightOne.plays);
        flightTwo.complete();
        assertEquals(2, flightTwo.plays);
        playback.dispose();
        assertEquals(1, takeoffTwo.disposals);
        assertEquals(1, flightTwo.disposals);
    }

    @Test public void carrierCompletes999PlaysThenItsRetainedCallbackStartsAnotherGroup() {
        Factory factory = new Factory();
        AircraftAudioPlayback playback = new AircraftAudioPlayback(factory::create, 1f);
        AircraftAudioPlayback.Owner carrier = playback.createOwner(AircraftAudioPlayback.Kind.CARRIER);
        assertTrue(carrier.requestTakeoff());
        assertEquals(OriginalAudio.Cue.CARRIERJET_TAKEOFF, factory.cues.get(0));
        assertEquals(OriginalAudio.Cue.CARRIERJET_FLIGHT, factory.cues.get(1));
        FakeMusic takeoff = factory.created.get(0), flight = factory.created.get(1);
        takeoff.complete();
        assertEquals(1, flight.plays);
        for (int completed = 1; completed < 999; completed++) {
            flight.complete();
            assertEquals(completed + 1, flight.plays);
        }
        flight.complete();
        assertEquals("source f13 listener survives its 999-play group", 1000, flight.plays);
        assertFalse("replays count finite starts rather than endless Music looping", flight.looping);
        playback.finish();
        flight.complete();
        assertEquals(1000, flight.plays);
    }

    @Test public void mutedRequestRetriesAndExistingCompletionContinuesSilently() {
        Factory factory = new Factory();
        AircraftAudioPlayback playback = new AircraftAudioPlayback(factory::create, .8f);
        AircraftAudioPlayback.Owner owner = playback.createOwner(AircraftAudioPlayback.Kind.FIGHTER);
        playback.setMuted(true);
        assertFalse(owner.requestTakeoff());
        assertEquals(0, factory.created.size());
        playback.setMuted(false);
        assertTrue(owner.requestTakeoff());
        owner.setSourceVolumePercent(40);
        assertEquals(.32f, factory.created.get(0).volume, .0001f);
        playback.setMuted(true);
        factory.created.get(0).complete();
        assertEquals(1, factory.created.get(1).plays);
        assertEquals(0f, factory.created.get(1).volume, 0f);
        factory.created.get(1).complete();
        assertEquals("mute does not cancel retained source callbacks", 2,
                factory.created.get(1).plays);
        playback.setMuted(false);
        assertEquals(.32f, factory.created.get(1).volume, .0001f);
        playback.dispose();
    }

    @Test public void sourcePanAndFadeSurviveMuteAndRetainedFlightCompletion() {
        Factory factory = new Factory();
        AircraftAudioPlayback playback = new AircraftAudioPlayback(factory::create, .8f);
        AircraftAudioPlayback.Owner first = playback.createOwner(AircraftAudioPlayback.Kind.FIGHTER);
        AircraftAudioPlayback.Owner second = playback.createOwner(AircraftAudioPlayback.Kind.FIGHTER);
        assertTrue(first.requestTakeoff());
        assertTrue(second.requestTakeoff());
        FakeMusic takeoff = factory.created.get(0), flight = factory.created.get(1);
        FakeMusic other = factory.created.get(2);
        assertEquals(0f, takeoff.pan, 0f);
        assertEquals(.8f, takeoff.volume, 0f);
        first.setSourcePanPercent(-100);
        first.setSourceVolumePercent(98); // First source flyOff fade step.
        assertEquals(-1f, takeoff.pan, 0f);
        assertEquals(-1f, flight.pan, 0f);
        assertEquals(.784f, takeoff.volume, .0001f);
        assertEquals(.784f, flight.volume, .0001f);
        assertEquals(0f, other.pan, 0f);
        assertEquals(.8f, other.volume, 0f);
        first.setSourcePanPercent(100);
        playback.setMuted(true);
        assertEquals(1f, takeoff.pan, 0f);
        assertEquals(0f, takeoff.volume, 0f);
        assertEquals(0f, flight.volume, 0f);
        takeoff.complete();
        assertEquals(1, flight.plays);
        assertEquals(1f, flight.pan, 0f);
        flight.complete();
        assertEquals(2, flight.plays);
        playback.setMuted(false);
        assertEquals(1f, flight.pan, 0f);
        assertEquals(.784f, flight.volume, .0001f);
        first.setSourcePanPercent(-100);
        assertEquals(-1f, flight.pan, 0f);
        for (int invalid : new int[] {-101, 101}) {
            try { first.setSourcePanPercent(invalid); fail("invalid source pan " + invalid); }
            catch (IllegalArgumentException expected) { /* Source sound pan is clamped upstream. */ }
        }
        playback.dispose();
    }

    @Test public void pauseDefersCompletionsWithoutReplayingFinishedTakeoff() {
        Factory factory = new Factory();
        AircraftAudioPlayback playback = new AircraftAudioPlayback(factory::create, 1f);
        AircraftAudioPlayback.Owner owner = playback.createOwner(AircraftAudioPlayback.Kind.FIGHTER);
        assertTrue(owner.requestTakeoff());
        FakeMusic takeoff = factory.created.get(0), flight = factory.created.get(1);
        playback.pause();
        assertEquals(1, takeoff.pauses);
        takeoff.complete();
        takeoff.complete();
        assertEquals(0, flight.plays);
        playback.resume();
        assertEquals(1, takeoff.plays);
        assertEquals(1, flight.plays);
        playback.pause();
        flight.complete();
        assertEquals(1, flight.plays);
        playback.resume();
        assertEquals(2, flight.plays);
        playback.dispose();
    }

    @Test public void queuedNativeCompletionAcrossPauseDoesNotReplayFinishedCue() {
        Factory factory = new Factory();
        Dispatcher dispatcher = new Dispatcher();
        AircraftAudioPlayback playback = new AircraftAudioPlayback(
                factory::create, 1f, dispatcher::post);
        AircraftAudioPlayback.Owner owner = playback.createOwner(AircraftAudioPlayback.Kind.FIGHTER);
        assertTrue(owner.requestTakeoff());
        FakeMusic takeoff = factory.created.get(0), flight = factory.created.get(1);

        // AndroidMusic can finish on its audio thread before the posted listener runs.
        takeoff.finishWithoutCallback();
        dispatcher.post(takeoff::deliverQueuedCompletion);
        playback.pause();
        playback.resume();
        assertEquals("finished takeoff must not be started a second time", 1, takeoff.plays);
        dispatcher.runAll();
        assertEquals("the queued completion must still start flight", 1, flight.plays);

        flight.finishWithoutCallback();
        dispatcher.post(flight::deliverQueuedCompletion);
        playback.pause();
        playback.resume();
        assertEquals(1, flight.plays);
        dispatcher.runAll();
        assertEquals("late delivery cannot start a third overlapping flight", 2,
                flight.plays);
        playback.dispose();
    }

    @Test public void androidLifecycleMayPauseBackendBeforeOwnerPause() {
        Factory factory = new Factory();
        AircraftAudioPlayback playback = new AircraftAudioPlayback(factory::create, 1f);
        AircraftAudioPlayback.Owner owner = playback.createOwner(AircraftAudioPlayback.Kind.FIGHTER);
        assertTrue(owner.requestTakeoff());
        FakeMusic takeoff = factory.created.get(0), flight = factory.created.get(1);
        // AndroidApplication pauses all Music before its lifecycle listeners,
        // including OriginalAudio/World, receive pause(). A false isPlaying()
        // here is not evidence of completion.
        takeoff.pause();
        playback.pause();
        assertEquals(0, flight.plays);
        playback.resume();
        assertEquals("resume the same paused takeoff", 2, takeoff.plays);
        assertEquals(0, flight.plays);
        takeoff.complete();
        assertEquals(1, flight.plays);
        playback.dispose();
    }

    @Test public void postedResumeCannotEscapeLaterPauseFinishOrDispose() {
        Factory factory = new Factory();
        Dispatcher dispatcher = new Dispatcher();
        AircraftAudioPlayback playback = new AircraftAudioPlayback(
                factory::create, 1f, dispatcher::post);
        AircraftAudioPlayback.Owner owner = playback.createOwner(AircraftAudioPlayback.Kind.FIGHTER);
        assertTrue(owner.requestTakeoff());
        FakeMusic takeoff = factory.created.get(0);
        playback.pause();
        playback.resume();
        playback.pause(); // A second pause cancels the earlier queued resume.
        dispatcher.runAll();
        assertEquals(1, takeoff.plays);
        playback.resume();
        dispatcher.runAll();
        assertEquals(2, takeoff.plays);
        playback.pause();
        playback.resume();
        playback.finish();
        dispatcher.runAll();
        assertEquals(2, takeoff.plays);
        assertEquals(1, takeoff.disposals);
        AircraftAudioPlayback.Owner next = playback.createOwner(AircraftAudioPlayback.Kind.FIGHTER);
        assertFalse("battle remains paused after finish", next.requestTakeoff());
        playback.dispose();
        dispatcher.runAll();
    }

    @Test public void finishAndDisposeCloseEveryOldCallbackBeforeReusingBattle() {
        Factory factory = new Factory();
        AircraftAudioPlayback playback = new AircraftAudioPlayback(factory::create, .7f);
        AircraftAudioPlayback.Owner old = playback.createOwner(AircraftAudioPlayback.Kind.FIGHTER);
        assertTrue(old.requestTakeoff());
        FakeMusic oldTakeoff = factory.created.get(0), oldFlight = factory.created.get(1);
        playback.finish();
        assertFalse(old.requestTakeoff());
        oldTakeoff.complete();
        oldFlight.complete();
        assertEquals(0, oldFlight.plays);
        assertEquals(1, oldTakeoff.disposals);
        assertEquals(1, oldFlight.disposals);
        AircraftAudioPlayback.Owner next = playback.createOwner(AircraftAudioPlayback.Kind.FIGHTER);
        assertTrue(next.requestTakeoff());
        oldTakeoff.complete();
        assertEquals(0, factory.created.get(3).plays);
        playback.dispose();
        playback.dispose();
        assertFalse(next.requestTakeoff());
        for (FakeMusic sound : factory.created) assertEquals(1, sound.disposals);
    }

    @Test public void failedPairDisposesPartialVoiceAndCanRetry() {
        Factory factory = new Factory();
        factory.failFlight = true;
        AircraftAudioPlayback playback = new AircraftAudioPlayback(factory::create, 1f);
        AircraftAudioPlayback.Owner owner = playback.createOwner(AircraftAudioPlayback.Kind.FIGHTER);
        assertFalse(owner.requestTakeoff());
        assertEquals(1, factory.created.size());
        assertEquals(1, factory.created.get(0).disposals);
        factory.failFlight = false;
        assertTrue(owner.requestTakeoff());
        assertEquals(3, factory.created.size());
        assertEquals(1, factory.created.get(1).plays);
        playback.dispose();
        assertEquals(1, factory.created.get(1).disposals);
        assertEquals(1, factory.created.get(2).disposals);
    }

    private static final class Factory {
        final List<OriginalAudio.Cue> cues = new ArrayList<>();
        final List<FakeMusic> created = new ArrayList<>();
        boolean failFlight;
        Music create(OriginalAudio.Cue cue) {
            cues.add(cue);
            if (failFlight && cue == OriginalAudio.Cue.FIGHTER_FLIGHT) return null;
            FakeMusic sound = new FakeMusic();
            created.add(sound);
            return sound;
        }
    }

    private static final class Dispatcher {
        final List<Runnable> queue = new ArrayList<>();
        void post(Runnable runnable) { queue.add(runnable); }
        void runAll() {
            while (!queue.isEmpty()) queue.remove(0).run();
        }
    }

    private static final class FakeMusic implements Music {
        int plays, pauses, stops, disposals;
        float volume;
        float pan;
        boolean playing, looping = true;
        OnCompletionListener listener;
        void complete() {
            playing = false;
            if (listener != null) listener.onCompletion(this);
        }
        void completeCallbackWithoutEndingPlayback() {
            if (listener != null) listener.onCompletion(this);
        }
        void finishWithoutCallback() { playing = false; }
        void deliverQueuedCompletion() {
            if (listener != null) listener.onCompletion(this);
        }
        @Override public void play() { plays++; playing = true; }
        @Override public void pause() { pauses++; playing = false; }
        @Override public void stop() { stops++; playing = false; }
        @Override public boolean isPlaying() { return playing; }
        @Override public void setLooping(boolean looping) { this.looping = looping; }
        @Override public boolean isLooping() { return looping; }
        @Override public void setVolume(float volume) { this.volume = volume; }
        @Override public float getVolume() { return volume; }
        @Override public void setPan(float pan, float volume) {
            this.pan = pan;
            this.volume = volume;
        }
        @Override public void setPosition(float position) { }
        @Override public float getPosition() { return 0; }
        @Override public void dispose() { disposals++; }
        @Override public void setOnCompletionListener(OnCompletionListener listener) {
            this.listener = listener;
        }
    }
}
