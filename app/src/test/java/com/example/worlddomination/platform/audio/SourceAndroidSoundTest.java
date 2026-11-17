package com.example.worlddomination.platform.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class SourceAndroidSoundTest {
    @Test public void finiteRepeatWaitsForLoadAndUsesNativeRepeatCount() {
        FakeDriver driver = new FakeDriver();
        SourceAndroidSound sound = new SourceAndroidSound(driver, 7, 1_000_000L);
        long first = sound.playFinite(.21f, 1f, -.5f, 9999);
        long second = sound.playFinite(.4f, 1f, .5f, 2);
        assertNotEquals(first, second);
        assertEquals(0, driver.plays.size());

        sound.loaded();
        assertEquals(2, driver.plays.size());
        assertEquals("7:0.21:0.105:9998", driver.plays.get(0));
        assertEquals("7:0.2:0.4:1", driver.plays.get(1));
        sound.stop(first);
        assertEquals(1, driver.stops.size());
        sound.stop(second);
        assertEquals(2, driver.stops.size());
    }

    @Test public void pendingStopAndDisposeCannotResurrectAfterLoadCallback() {
        FakeDriver driver = new FakeDriver();
        SourceAndroidSound sound = new SourceAndroidSound(driver, 4, 1_000_000L);
        long stopped = sound.playFinite(.21f, 1f, 0f, 9999);
        sound.stop(stopped);
        sound.play(.3f);
        sound.dispose();
        sound.loaded();
        assertEquals(0, driver.plays.size());
        assertEquals(1, driver.unloads.size());
        assertEquals(-1L, sound.play());
        sound.dispose();
        assertEquals(1, driver.unloads.size());
    }

    @Test public void pendingVolumePanPauseAndResumeKeepOneVoice() {
        FakeDriver driver = new FakeDriver();
        SourceAndroidSound sound = new SourceAndroidSound(driver, 2, 1_000_000L);
        long handle = sound.loop(.8f);
        sound.setPan(handle, 1f, .21f);
        sound.setPitch(handle, 1.5f);
        sound.pause(handle);
        sound.loaded();
        assertEquals(0, driver.plays.size());
        sound.resume(handle);
        assertEquals(1, driver.plays.size());
        assertEquals("2:0.0:0.21:-1", driver.plays.get(0));
        assertEquals(1.5f, driver.pitch, 0f);
        sound.setVolume(handle, 0f);
        assertEquals("0.0:0.0", driver.volumes.get(0));
        sound.setVolume(handle, .21f);
        assertEquals("0.0:0.21", driver.volumes.get(1));
        sound.pause(handle);
        sound.resume(handle);
        assertEquals(1, driver.plays.size());
        assertEquals(1, driver.pauses.size());
        assertEquals(1, driver.resumes.size());
    }

    @Test public void loadFailureDropsRequestsAndRejectsLaterStarts() {
        FakeDriver driver = new FakeDriver();
        SourceAndroidSound sound = new SourceAndroidSound(driver, 3, 1_000_000L);
        sound.playFinite(.21f, 1f, 0f, 9999);
        sound.loadFailed();
        sound.loaded();
        assertEquals(0, driver.plays.size());
        assertEquals(-1L, sound.play());
    }

    @Test public void longLoopSurvivesMoreThan256ShortRequests() {
        FakeDriver driver = new FakeDriver();
        SourceAndroidSound sound = new SourceAndroidSound(driver, 5, 1_000_000L);
        sound.loaded();
        long loop = sound.playFinite(.21f, 1f, 0f, 9999);
        for (int index = 0; index < 300; index++) {
            sound.play(.21f);
            driver.now += 2_000_000L;
        }
        assertEquals(301, driver.plays.size());
        assertEquals(0, driver.stops.size());
        sound.stop(loop);
        assertEquals(1, driver.stops.size());
        assertEquals(1, (int) driver.stops.get(0));
        sound.dispose();
        assertEquals(1, driver.unloads.size());
    }

    @Test public void positiveFiniteCountIsRequired() {
        SourceAndroidSound sound = new SourceAndroidSound(new FakeDriver(), 1, 1_000_000L);
        boolean rejected = false;
        try { sound.playFinite(.2f, 1f, 0f, 0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        assertTrue(rejected);
    }

    @Test public void globalSoundPauseAndResumeUseSharedPoolLifecycle() {
        FakeDriver driver = new FakeDriver();
        SourceAndroidSound sound = new SourceAndroidSound(driver, 1, 1_000_000L);
        sound.pause();
        sound.resume();
        assertEquals(1, driver.pauseAllCount);
        assertEquals(1, driver.resumeAllCount);
        sound.dispose();
        sound.pause();
        sound.resume();
        assertEquals(1, driver.pauseAllCount);
        assertEquals(1, driver.resumeAllCount);
    }

    @Test public void duplicateLoadCallbackDoesNotStartVoicesAgain() {
        FakeDriver driver = new FakeDriver();
        SourceAndroidSound sound = new SourceAndroidSound(driver, 1, 1_000_000L);
        sound.play(.21f);
        sound.loaded();
        sound.loaded();
        assertEquals(1, driver.plays.size());
    }

    @Test public void nativeAllocationZeroReturnsFailureWithoutRetryOnResume() {
        FakeDriver driver = new FakeDriver();
        driver.failNextPlay = true;
        SourceAndroidSound sound = new SourceAndroidSound(driver, 1, 1_000_000L);
        sound.loaded();
        long failed = sound.play(.21f);
        assertEquals(-1L, failed);
        sound.resume(1L);
        assertEquals(1, driver.plays.size());
    }

    @Test public void asyncLoadWaitsForGlobalResumeAndPendingCancellationWins() {
        FakeDriver driver = new FakeDriver();
        SourceAndroidSound sound = new SourceAndroidSound(driver, 1, 1_000_000L);
        driver.pauseAll();
        long cancelled = sound.playFinite(.21f, 1f, 0f, 9999);
        long retained = sound.play(.21f);
        sound.loaded();
        assertEquals(0, driver.plays.size());
        sound.stop(cancelled);
        driver.resumeAll();
        sound.poolResumed();
        assertEquals(1, driver.plays.size());
        sound.stop(retained);
        assertEquals(1, driver.stops.size());
    }

    @Test public void perVoiceAndGlobalPauseExtendSafePruningDeadline() {
        FakeDriver driver = new FakeDriver();
        SourceAndroidSound sound = new SourceAndroidSound(driver, 1, 1_000_000L);
        sound.loaded();
        long first = sound.play(.21f);
        driver.now += 500_000L;
        sound.pause(first);
        driver.now += 10_000_000L;
        sound.play(.21f); // Pruning must retain the paused first handle.
        sound.resume(first);
        driver.pauseAll();
        driver.now += 10_000_000L;
        driver.resumeAll();
        driver.now += 400_000L;
        sound.play(.21f);
        sound.stop(first);
        assertEquals(1, driver.stops.size());
        assertEquals(1, (int) driver.stops.get(0));
    }

    @Test public void resumeDuringGlobalPauseIsDeferredUntilPoolResumes() {
        FakeDriver driver = new FakeDriver();
        SourceAndroidSound sound = new SourceAndroidSound(driver, 1, 1_000_000L);
        sound.loaded();
        long handle = sound.play(.21f);
        sound.pause(handle);
        driver.now += 10_000_000L; // Per-voice pause predates the global pause.
        driver.pauseAll();
        sound.resume(handle);
        assertEquals(0, driver.resumes.size());
        assertTrue("Deferred resume must retain the finite handle", sound.isVoiceRetained(handle));
        driver.now += 10_000_000L;
        driver.resumeAll();
        sound.poolResumed();
        assertEquals(1, driver.resumes.size());
        assertEquals(1, driver.plays.size());
        assertTrue(sound.isVoiceRetained(handle));
    }

    @Test public void loadCallbackCannotStartAfterPauseAcquiresPoolLock() throws Exception {
        FakeDriver driver = new FakeDriver();
        SourceAndroidSound sound = new SourceAndroidSound(driver, 1, 1_000_000L);
        long pending = sound.playFinite(.21f, 1f, 0f, 9999);
        driver.pauseEntered = new CountDownLatch(1);
        driver.allowPause = new CountDownLatch(1);
        Thread pauser = new Thread(driver::pauseAll);
        pauser.start();
        assertTrue(driver.pauseEntered.await(2, TimeUnit.SECONDS));
        Thread loader = new Thread(sound::loaded);
        loader.start();
        driver.allowPause.countDown();
        pauser.join(2000L);
        loader.join(2000L);
        assertFalse(pauser.isAlive());
        assertFalse(loader.isAlive());
        assertEquals(0, driver.plays.size());
        assertTrue(sound.isVoiceRetained(pending));
        driver.resumeAll();
        sound.poolResumed();
        assertEquals(1, driver.plays.size());
    }

    @Test public void streamResumeCannotRacePastPoolAutoPause() throws Exception {
        FakeDriver driver = new FakeDriver();
        SourceAndroidSound sound = new SourceAndroidSound(driver, 1, 1_000_000L);
        sound.loaded();
        long handle = sound.loop(.21f);
        sound.pause(handle);
        driver.pauseEntered = new CountDownLatch(1);
        driver.allowPause = new CountDownLatch(1);
        Thread pauser = new Thread(driver::pauseAll);
        pauser.start();
        assertTrue(driver.pauseEntered.await(2, TimeUnit.SECONDS));
        Thread resumer = new Thread(() -> sound.resume(handle));
        resumer.start();
        driver.allowPause.countDown();
        pauser.join(2000L);
        resumer.join(2000L);
        assertFalse(pauser.isAlive());
        assertFalse(resumer.isAlive());
        assertEquals(0, driver.resumes.size());
        driver.resumeAll();
        sound.poolResumed();
        assertEquals(1, driver.resumes.size());
    }

    private static final class FakeDriver implements SourceAndroidSound.Driver {
        final List<String> plays = new ArrayList<>();
        final List<Integer> stops = new ArrayList<>();
        final List<Integer> pauses = new ArrayList<>();
        final List<Integer> resumes = new ArrayList<>();
        final List<Integer> unloads = new ArrayList<>();
        final List<String> volumes = new ArrayList<>();
        int nextStream = 1;
        float pitch;
        int pauseAllCount;
        int resumeAllCount;
        long now;
        long pauseStarted;
        long pausedNanos;
        boolean poolPaused;
        boolean failNextPlay;
        CountDownLatch pauseEntered;
        CountDownLatch allowPause;
        final Object poolLock = new Object();

        @Override public int play(int sample, float left, float right, int repeats, float rate) {
            synchronized (poolLock) {
                if (poolPaused) return -1;
                plays.add(sample + ":" + left + ":" + right + ":" + repeats);
                pitch = rate;
                if (failNextPlay) { failNextPlay = false; return 0; }
                return nextStream++;
            }
        }
        @Override public void stop(int stream) { stops.add(stream); }
        @Override public void pause(int stream) { pauses.add(stream); }
        @Override public boolean resume(int stream) {
            synchronized (poolLock) {
                if (poolPaused) return false;
                resumes.add(stream);
                return true;
            }
        }
        @Override public void pauseAll() {
            synchronized (poolLock) {
                pauseAllCount++;
                if (!poolPaused) { poolPaused = true; pauseStarted = now; }
                if (pauseEntered != null) pauseEntered.countDown();
                if (allowPause != null) {
                    try { assertTrue(allowPause.await(2, TimeUnit.SECONDS)); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(interrupted);
                    }
                }
            }
        }
        @Override public void resumeAll() {
            synchronized (poolLock) {
                resumeAllCount++;
                if (poolPaused) { pausedNanos += now - pauseStarted; poolPaused = false; }
            }
        }
        @Override public void setRate(int stream, float rate) { pitch = rate; }
        @Override public void setVolume(int stream, float left, float right) {
            volumes.add(left + ":" + right);
        }
        @Override public void setLoop(int stream, int repeats) { }
        @Override public void unload(int sample) { unloads.add(sample); }
        @Override public long activeNowNanos() {
            synchronized (poolLock) { return (poolPaused ? pauseStarted : now) - pausedNanos; }
        }
    }
}
