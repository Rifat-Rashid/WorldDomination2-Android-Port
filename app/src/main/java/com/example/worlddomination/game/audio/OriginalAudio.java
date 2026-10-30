package com.example.worlddomination.game.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumMap;
import java.util.Map;

/** Plays the original Flash-exported MP3 assets through libGDX's Android audio backend. */
public final class OriginalAudio {
    public enum Cue {
        THEME("2_WDTheme.wav.mp3.gz"),
        STARTUP("58_interface_startup.wav.mp3.gz"),
        ACTION_SWITCH("57_interface_Switch Action selection.wav.mp3.gz"),
        ACTION_SWITCH_OFF("56_interface_Switch Action selection2.wav.mp3.gz"),
        STOCK_SELECT("54_interface_Switch Sounds 1.wav.mp3.gz"),
        UNIT_SELECT("59_interface_select unit bleep.wav.mp3.gz"),
        ACTION_ACCEPT("64_interface_Accept action bleep.wav.mp3.gz"),
        ACTION_OPEN("61_interface_openactiondevice.wav.mp3.gz"),
        ACTION_CLOSE("63_interface_closeactiondevice.wav.mp3.gz"),
        SCORE_COUNTER("31_Score counter.Mp3.mp3.gz"),
        SATELLITE_MULTIPLIER("34_Satelite multiplier.Mp3.mp3.gz"),
        FLAG_BONUS("75_Country domination bonus.Mp3.mp3.gz"),
        DEFEAT("48_Loose.Mp3.mp3.gz");

        private final String compressedAsset;

        Cue(String compressedAsset) {
            this.compressedAsset = compressedAsset;
        }
    }

    private static final String INTERNAL_ROOT = "original/runtime/audio/";
    private static final String CACHE_ROOT = "worlddomination/audio/";
    private static final float MUSIC_VOLUME = 0.55f;
    private static final float EFFECT_VOLUME = 0.8f;

    private final Map<Cue, Sound> sounds = new EnumMap<>(Cue.class);
    private Music theme;
    private boolean muted;
    private boolean resumeMusicAfterPause;
    private boolean initialized;

    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        theme = loadMusic(Cue.THEME);
        preload(Cue.STARTUP);
        preload(Cue.ACTION_SWITCH);
        preload(Cue.ACTION_SWITCH_OFF);
        preload(Cue.STOCK_SELECT);
        preload(Cue.UNIT_SELECT);
        preload(Cue.ACTION_ACCEPT);
        preload(Cue.ACTION_OPEN);
        preload(Cue.ACTION_CLOSE);
        preload(Cue.SCORE_COUNTER);
        preload(Cue.SATELLITE_MULTIPLIER);
        preload(Cue.FLAG_BONUS);
        preload(Cue.DEFEAT);
        play(Cue.STARTUP);
        if (theme != null && !muted) {
            theme.setLooping(true);
            theme.setVolume(MUSIC_VOLUME);
            theme.play();
        }
    }

    public boolean toggleMuted() {
        muted = !muted;
        if (theme != null) {
            theme.setVolume(muted ? 0f : MUSIC_VOLUME);
            if (!muted && !theme.isPlaying()) {
                theme.play();
            }
        }
        return muted;
    }

    public boolean isMuted() {
        return muted;
    }

    public void play(Cue cue) {
        if (muted || cue == Cue.THEME) {
            return;
        }
        Sound sound = sounds.get(cue);
        if (sound == null) {
            sound = loadSound(cue);
        }
        if (sound != null) {
            sound.play(EFFECT_VOLUME);
        }
    }

    /** Starts a looping original cue and returns its libGDX sound instance id. */
    public long loop(Cue cue) {
        if (muted || cue == Cue.THEME) {
            return -1L;
        }
        Sound sound = sounds.get(cue);
        if (sound == null) {
            sound = loadSound(cue);
        }
        return sound == null ? -1L : sound.loop(EFFECT_VOLUME);
    }

    public void stop(Cue cue, long soundId) {
        if (soundId < 0L) {
            return;
        }
        Sound sound = sounds.get(cue);
        if (sound != null) {
            sound.stop(soundId);
        }
    }

    public void pause() {
        resumeMusicAfterPause = theme != null && theme.isPlaying();
        if (resumeMusicAfterPause) {
            theme.pause();
        }
        for (Sound sound : sounds.values()) {
            sound.pause();
        }
    }

    public void resume() {
        if (resumeMusicAfterPause && !muted && theme != null) {
            theme.play();
        }
        for (Sound sound : sounds.values()) {
            sound.resume();
        }
        resumeMusicAfterPause = false;
    }

    public void dispose() {
        if (theme != null) {
            theme.dispose();
            theme = null;
        }
        for (Sound sound : sounds.values()) {
            sound.dispose();
        }
        sounds.clear();
        initialized = false;
    }

    static String internalPath(Cue cue) {
        return INTERNAL_ROOT + packagedAssetName(cue);
    }

    static String sourceAssetPath(Cue cue) {
        return INTERNAL_ROOT + cue.compressedAsset;
    }

    private static String packagedAssetName(Cue cue) {
        return cue.compressedAsset.substring(0,
                cue.compressedAsset.length() - ".gz".length());
    }

    static String cachePath(Cue cue) {
        String outputName = cue.compressedAsset.substring(0,
                cue.compressedAsset.length() - ".gz".length());
        return CACHE_ROOT + outputName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void preload(Cue cue) {
        loadSound(cue);
    }

    private Sound loadSound(Cue cue) {
        if (Gdx.audio == null) {
            return null;
        }
        try {
            FileHandle file = unpack(cue);
            if (file == null) {
                return null;
            }
            Sound sound = Gdx.audio.newSound(file);
            sounds.put(cue, sound);
            return sound;
        } catch (RuntimeException exception) {
            logFailure(cue, exception);
            return null;
        }
    }

    private Music loadMusic(Cue cue) {
        if (Gdx.audio == null) {
            return null;
        }
        try {
            FileHandle file = unpack(cue);
            return file == null ? null : Gdx.audio.newMusic(file);
        } catch (RuntimeException exception) {
            logFailure(cue, exception);
            return null;
        }
    }

    private FileHandle unpack(Cue cue) {
        if (Gdx.files == null) {
            return null;
        }
        FileHandle cached = Gdx.files.local(cachePath(cue));
        if (cached.exists() && cached.length() > 0) {
            return cached;
        }
        FileHandle packagedAsset = Gdx.files.internal(internalPath(cue));
        try (InputStream input = packagedAsset.read();
             OutputStream output = cached.write(false)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) {
                    output.write(buffer, 0, count);
                }
            }
            return cached;
        } catch (IOException | RuntimeException exception) {
            logFailure(cue, exception);
            return null;
        }
    }

    private static void logFailure(Cue cue, Exception exception) {
        if (Gdx.app != null) {
            Gdx.app.error("OriginalAudio", "Could not load " + cue.name(), exception);
        }
    }
}
