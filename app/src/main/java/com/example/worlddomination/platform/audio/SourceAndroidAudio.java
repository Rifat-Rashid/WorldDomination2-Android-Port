package com.example.worlddomination.platform.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;

import com.badlogic.gdx.Files.FileType;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.AndroidFileHandle;
import com.badlogic.gdx.backends.android.DefaultAndroidAudio;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Android-only finite repeats for all effects, sharing one active SoundPool.
 * DefaultAndroidAudio still owns an idle private pool; its dispose releases it. */
public final class SourceAndroidAudio extends DefaultAndroidAudio implements SourceAndroidSound.Driver {
    private final SoundPool effectsPool;
    private final Map<Integer, SourceAndroidSound> samples = new HashMap<>();
    private final Object clockLock = new Object();
    private boolean poolPaused;
    private long pauseStarted;
    private long pausedNanos;
    private boolean disposed;

    public SourceAndroidAudio(Context context, AndroidApplicationConfiguration config) {
        super(context, config);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        effectsPool = new SoundPool.Builder().setAudioAttributes(attributes)
                .setMaxStreams(config.maxSimultaneousSounds).build();
        effectsPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            SourceAndroidSound sound;
            synchronized (samples) { sound = disposed ? null : samples.get(sampleId); }
            if (sound != null) {
                if (status == 0) sound.loaded();
                else sound.loadFailed();
            }
        });
    }

    @Override public Sound newSound(FileHandle file) {
        synchronized (samples) {
            if (disposed) throw new IllegalStateException("Audio has been disposed");
            int sampleId;
            if (file.type() == FileType.Internal) {
                if (!(file instanceof AndroidFileHandle)) {
                    throw new GdxRuntimeException("Internal sound needs AndroidFileHandle: " + file);
                }
                try (AssetFileDescriptor descriptor =
                             ((AndroidFileHandle) file).getAssetFileDescriptor()) {
                    sampleId = effectsPool.load(descriptor, 1);
                } catch (IOException exception) {
                    throw new GdxRuntimeException("Could not load sound: " + file, exception);
                }
            } else {
                sampleId = effectsPool.load(file.file().getPath(), 1);
            }
            if (sampleId == 0) throw new GdxRuntimeException("Could not load sound: " + file);
            // MPEG at the minimum supported 8 kbit/s is at most one millisecond
            // per encoded byte, plus padding. Double for SoundPool's .5 pitch.
            long onePlayBound = saturatingAdd(saturatingMultiply(file.length(), 2_000_000L),
                    2_000_000_000L);
            SourceAndroidSound sound = new SourceAndroidSound(this, sampleId, onePlayBound);
            samples.put(sampleId, sound);
            return sound;
        }
    }

    @Override public int play(int sampleId, float left, float right, int repeats, float pitch) {
        synchronized (clockLock) {
            if (poolPaused) return -1;
            return effectsPool.play(sampleId, left, right, repeats == 0 ? 1 : 2,
                    repeats, pitch);
        }
    }
    @Override public void stop(int streamId) { effectsPool.stop(streamId); }
    @Override public void pause(int streamId) { effectsPool.pause(streamId); }
    @Override public boolean resume(int streamId) {
        synchronized (clockLock) {
            if (poolPaused) return false;
            effectsPool.resume(streamId);
            return true;
        }
    }
    @Override public void pauseAll() {
        synchronized (clockLock) {
            if (poolPaused) return;
            poolPaused = true;
            pauseStarted = System.nanoTime();
            effectsPool.autoPause();
        }
    }
    @Override public void resumeAll() {
        synchronized (clockLock) {
            if (!poolPaused) return;
            effectsPool.autoResume();
            pausedNanos = saturatingAdd(pausedNanos, System.nanoTime() - pauseStarted);
            poolPaused = false;
        }
        ArrayList<SourceAndroidSound> owned;
        synchronized (samples) { owned = new ArrayList<>(samples.values()); }
        for (SourceAndroidSound sound : owned) sound.poolResumed();
    }
    @Override public long activeNowNanos() {
        synchronized (clockLock) {
            return (poolPaused ? pauseStarted : System.nanoTime()) - pausedNanos;
        }
    }
    private static long saturatingMultiply(long first, long second) {
        return first > Long.MAX_VALUE / second ? Long.MAX_VALUE : first * second;
    }
    private static long saturatingAdd(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }
    @Override public void setRate(int streamId, float pitch) { effectsPool.setRate(streamId, pitch); }
    @Override public void setVolume(int streamId, float left, float right) {
        effectsPool.setVolume(streamId, left, right);
    }
    @Override public void setLoop(int streamId, int repeats) { effectsPool.setLoop(streamId, repeats); }
    @Override public void unload(int sampleId) {
        synchronized (samples) { samples.remove(sampleId); }
        effectsPool.unload(sampleId);
    }

    @Override public void pause() {
        pauseAll();
        super.pause();
    }
    @Override public void resume() {
        super.resume();
        resumeAll();
    }
    @Override public void dispose() {
        ArrayList<SourceAndroidSound> owned;
        synchronized (samples) {
            if (disposed) return;
            disposed = true;
            owned = new ArrayList<>(samples.values());
        }
        for (SourceAndroidSound sound : owned) sound.dispose();
        effectsPool.release();
        super.dispose();
    }
}
