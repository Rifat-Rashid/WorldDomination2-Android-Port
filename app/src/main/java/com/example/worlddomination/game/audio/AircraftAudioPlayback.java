package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Music;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Owns each source Unit's completion-driven takeoff and retained flight Sound. */
public final class AircraftAudioPlayback {
    public enum Kind { FIGHTER, CARRIER }

    private static final int SOURCE_CARRIER_FLIGHT_PLAYS = 999;

    private final Function<OriginalAudio.Cue, Music> factory;
    private final Consumer<Runnable> resumeDispatcher;
    private final float volume;
    private final List<Owner> owners = new ArrayList<>();
    private boolean muted;
    private boolean paused;
    private boolean disposed;
    private boolean resumeQueued;
    private long resumeEpoch;

    public AircraftAudioPlayback(Function<OriginalAudio.Cue, Music> factory, float volume) {
        this(factory, volume, Runnable::run);
    }

    /** Dispatch resume after native Music completion callbacks queued on the GL thread. */
    public AircraftAudioPlayback(Function<OriginalAudio.Cue, Music> factory, float volume,
                                 Consumer<Runnable> resumeDispatcher) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.resumeDispatcher = Objects.requireNonNull(resumeDispatcher, "resumeDispatcher");
        if (!Float.isFinite(volume) || volume < 0f || volume > 1f)
            throw new IllegalArgumentException("volume must be in [0, 1]");
        this.volume = volume;
    }

    /** Allocates the Unit owner now; loads its two Music streams on the first audible request. */
    public synchronized Owner createOwner(Kind kind) {
        if (disposed) throw new IllegalStateException("Aircraft audio has been disposed");
        Owner owner = new Owner(Objects.requireNonNull(kind, "kind"));
        owners.add(owner);
        return owner;
    }

    /** Source mute changes volume on active sounds without cancelling their callbacks. */
    public synchronized void setMuted(boolean muted) {
        if (disposed) return;
        this.muted = muted;
        for (Owner owner : owners) owner.applyAudio();
    }

    public synchronized void pause() {
        if (disposed) return;
        if (resumeQueued) {
            resumeQueued = false;
            resumeEpoch++;
        }
        if (paused) return;
        paused = true;
        for (Owner owner : owners) {
            if (owner.closed || !owner.started) continue;
            if (!owner.takeoffComplete) owner.takeoff.pause();
            else if (owner.flightStarted && !owner.flightPending) owner.flight.pause();
        }
    }

    public synchronized void resume() {
        if (disposed || !paused || resumeQueued) return;
        final long epoch = ++resumeEpoch;
        resumeQueued = true;
        try {
            resumeDispatcher.accept(() -> completeResume(epoch));
        } catch (RuntimeException failure) {
            resumeQueued = false;
            resumeEpoch++;
            throw failure;
        }
    }

    private synchronized void completeResume(long epoch) {
        if (disposed || !paused || !resumeQueued || epoch != resumeEpoch) return;
        resumeQueued = false;
        paused = false;
        for (Owner owner : owners) {
            if (owner.closed || !owner.started) continue;
            if (!owner.takeoffComplete) owner.takeoff.play();
            else if (!owner.flightStarted || owner.flightPending) owner.startFlight();
            else owner.flight.play();
        }
    }

    /** Ends the battle: reject callbacks before any native stop/disposal may post them. */
    public synchronized void finish() {
        resumeQueued = false;
        resumeEpoch++;
        for (Owner owner : owners) owner.closed = true;
        for (Owner owner : owners) owner.release();
        owners.clear();
    }

    public synchronized void dispose() {
        if (disposed) return;
        disposed = true;
        finish();
    }

    /** One Unit.Sound object; its completion listener survives linkage replacement. */
    public final class Owner {
        private final Kind kind;
        private Music takeoff;
        private Music flight;
        private boolean started;
        private boolean closed;
        private boolean takeoffComplete;
        private boolean flightStarted;
        private boolean flightPending;
        private int carrierPlaysLeft;
        private int sourceVolumePercent = 100;
        private int sourcePanPercent;

        private Owner(Kind kind) { this.kind = kind; }

        /** Coalesces repeated Unit.soundAction visits; muted starts remain retryable. */
        public boolean requestTakeoff() {
            synchronized (AircraftAudioPlayback.this) {
                if (disposed || closed || started || muted || paused) return false;
                if (!loadPair()) return false;
                started = true;
                takeoff.play();
                return true;
            }
        }

        /** Flash flyOff fades the Sound object's volume two source points per tick. */
        public void setSourceVolumePercent(int percent) {
            if (percent < 0 || percent > 100)
                throw new IllegalArgumentException("Source volume must be in [0, 100]");
            synchronized (AircraftAudioPlayback.this) {
                if (closed || disposed) return;
                sourceVolumePercent = percent;
                applyAudio();
            }
        }

        /** Unit.soundPan is a clamped percentage of its pre-move MC x position. */
        public void setSourcePanPercent(int percent) {
            if (percent < -100 || percent > 100)
                throw new IllegalArgumentException("Source pan must be in [-100, 100]");
            synchronized (AircraftAudioPlayback.this) {
                if (closed || disposed) return;
                sourcePanPercent = percent;
                applyAudio();
            }
        }

        /** Unit.killUnit/flyOff stop or a discarded parent closes only this voice. */
        public void stop() {
            synchronized (AircraftAudioPlayback.this) {
                if (closed) return;
                closed = true;
                release();
            }
        }

        private boolean loadPair() {
            OriginalAudio.Cue takeoffCue = kind == Kind.CARRIER
                    ? OriginalAudio.Cue.CARRIERJET_TAKEOFF : OriginalAudio.Cue.FIGHTER_TAKEOFF;
            OriginalAudio.Cue flightCue = kind == Kind.CARRIER
                    ? OriginalAudio.Cue.CARRIERJET_FLIGHT : OriginalAudio.Cue.FIGHTER_FLIGHT;
            Music newTakeoff = null;
            Music newFlight = null;
            try {
                newTakeoff = factory.apply(takeoffCue);
                newFlight = factory.apply(flightCue);
                if (newTakeoff == null || newFlight == null) {
                    if (newTakeoff != null) newTakeoff.dispose();
                    if (newFlight != null) newFlight.dispose();
                    return false;
                }
                newTakeoff.setLooping(false);
                newFlight.setLooping(false);
                takeoff = newTakeoff;
                flight = newFlight;
                applyAudio();
                takeoff.setOnCompletionListener(music -> takeoffFinished());
                flight.setOnCompletionListener(music -> flightFinished());
                return true;
            } catch (RuntimeException failure) {
                if (newTakeoff != null) newTakeoff.dispose();
                if (newFlight != null) newFlight.dispose();
                takeoff = null;
                flight = null;
                return false;
            }
        }

        private void applyAudio() {
            float audible = muted ? 0f : volume * sourceVolumePercent / 100f;
            float pan = sourcePanPercent / 100f;
            if (takeoff != null) takeoff.setPan(pan, audible);
            if (flight != null) flight.setPan(pan, audible);
        }

        private void takeoffFinished() {
            synchronized (AircraftAudioPlayback.this) {
                if (closed || !started || takeoffComplete || takeoff.isPlaying()) return;
                takeoffComplete = true;
                if (!paused) startFlight();
            }
        }

        private void flightFinished() {
            synchronized (AircraftAudioPlayback.this) {
                if (closed || !flightStarted || flightPending || flight.isPlaying()) return;
                if (kind == Kind.CARRIER) {
                    carrierPlaysLeft--;
                    if (carrierPlaysLeft == 0) {
                        // The retained f13 callback starts another start(0,999) group.
                        carrierPlaysLeft = SOURCE_CARRIER_FLIGHT_PLAYS;
                    }
                }
                if (paused) flightPending = true;
                else startFlight();
            }
        }

        private void startFlight() {
            if (closed || !takeoffComplete) return;
            if (!flightStarted && kind == Kind.CARRIER)
                carrierPlaysLeft = SOURCE_CARRIER_FLIGHT_PLAYS;
            flightStarted = true;
            flightPending = false;
            flight.play();
        }

        private void release() {
            if (takeoff != null) {
                takeoff.stop();
                takeoff.dispose();
                takeoff = null;
            }
            if (flight != null) {
                flight.stop();
                flight.dispose();
                flight = null;
            }
        }
    }
}
