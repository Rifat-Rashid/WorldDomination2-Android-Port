package com.example.worlddomination.game.audio;

import com.example.worlddomination.game.simulation.GameState;
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
        SOLDIER_GUN("14_unit_Soldier gun.wav.mp3.gz"),
        SEAL_GUN("16_unit_seal gun.wav.mp3.gz"),
        WARROCKET("3_unit_Warrocket.wav.mp3.gz"),
        TANK_DRIVE("4_unit_Tank.wav.mp3.gz"),
        TANK_GUN("5_unit_Tank gun.wav.mp3.gz"),
        TANK_CHOPPER("6_unit_Tank chopper.wav.mp3.gz"),
        SUICIDE_SCREAM("7_unit_Suicide scream.wav.mp3.gz"),
        EXPLOSION_SMALL3("98_Explode small3.wav.mp3.gz"),
        EXPLOSION_MEDIUM3("71_Explode medium 3.wav.mp3.gz"),
        EXPLOSION_MEDIUM("70_explode medium.wav.mp3.gz"),
        EXPLOSION_LARGE2("97_Explode large2.wav.mp3.gz"),
        EXPLOSION_LARGE("72_Explode large.wav.mp3.gz"),
        DEFEAT("48_Loose.Mp3.mp3.gz"),
        GAME_AMBIENT("65_General ambient.wav.mp3.gz"),
        RESOURCES_AMBIENT("35_Resources ambient loop.wav.mp3.gz"),
        PRODUCTION_AMBIENT("41_Production Ambient loop.wav.mp3.gz"),
        DIPLOMACY_AMBIENT("96_Ambience Diplomacy.wav.mp3.gz"),
        DEFENSE_AMBIENT("74_Defence.wav.mp3.gz"),
        MASS_MEDIA_AMBIENT("47_Massmedia ambient loop.wav.mp3.gz"),
        TECHNOLOGY_AMBIENT("28_Technology ambient loop.wav.mp3.gz");

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
    private Cue ambientCue;
    private long ambientSoundId = -1L;
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
        preload(Cue.SOLDIER_GUN);
        preload(Cue.SEAL_GUN);
        preload(Cue.WARROCKET);
        preload(Cue.TANK_DRIVE);
        preload(Cue.TANK_GUN);
        preload(Cue.TANK_CHOPPER);
        preload(Cue.SUICIDE_SCREAM);
        preload(Cue.EXPLOSION_SMALL3);
        preload(Cue.EXPLOSION_MEDIUM3);
        preload(Cue.EXPLOSION_MEDIUM);
        preload(Cue.EXPLOSION_LARGE2);
        preload(Cue.EXPLOSION_LARGE);
        preload(Cue.DEFEAT);
        preload(Cue.GAME_AMBIENT);
        preload(Cue.RESOURCES_AMBIENT);
        preload(Cue.PRODUCTION_AMBIENT);
        preload(Cue.DIPLOMACY_AMBIENT);
        preload(Cue.DEFENSE_AMBIENT);
        preload(Cue.MASS_MEDIA_AMBIENT);
        preload(Cue.TECHNOLOGY_AMBIENT);
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
        if (muted) {
            for (Sound sound : sounds.values()) {
                sound.stop();
            }
            stopAmbientPlayback();
        } else if (ambientCue != null) {
            ambientSoundId = loop(ambientCue);
        }
        return muted;
    }

    public boolean isMuted() {
        return muted;
    }

    /** Starts the requested original ambience loop, replacing the previous one. */
    public void playAmbient(Cue cue) {
        if (ambientCue == cue && (muted || ambientSoundId >= 0L)) {
            return;
        }
        stopAmbientPlayback();
        ambientCue = cue;
        if (!muted && cue != null) {
            ambientSoundId = loop(cue);
        }
    }

    public void stopAmbient() {
        stopAmbientPlayback();
        ambientCue = null;
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
        if (!muted) {
            for (Sound sound : sounds.values()) {
                sound.resume();
            }
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
        ambientCue = null;
        ambientSoundId = -1L;
        initialized = false;
    }

    /** Maps the active source action panel to its original looping ambience. */
    public static Cue ambientForAction(GameState.Action action) {
        if (action == null) {
            return Cue.GAME_AMBIENT;
        }
        switch (action) {
            case RESOURCES:
                return Cue.RESOURCES_AMBIENT;
            case PRODUCTION:
                return Cue.PRODUCTION_AMBIENT;
            case RESEARCH:
                return Cue.TECHNOLOGY_AMBIENT;
            case MASS_MEDIA:
                return Cue.MASS_MEDIA_AMBIENT;
            case DIPLOMACY:
                return Cue.DIPLOMACY_AMBIENT;
            case DEFENSE:
                return Cue.DEFENSE_AMBIENT;
            default:
                return Cue.GAME_AMBIENT;
        }
    }

    /** Maps resolved combat results to the original Flash unit and explosion cues. */
    public static Cue cueForCombatEvent(GameState.CombatEvent event) {
        if (event == null) {
            return null;
        }
        if (event.type == GameState.CombatEventType.DIRECT_ATTACK
                && "soldier2".equals(event.weaponName)) {
            // Unit.soundAction('marine_shoot') attaches the original Seal gun asset.
            return Cue.SEAL_GUN;
        }
        if (event.type == GameState.CombatEventType.DIRECT_ATTACK
                && "soldier3".equals(event.weaponName)) {
            return Cue.TANK_CHOPPER;
        }
        if (event.type == GameState.CombatEventType.DIRECT_ATTACK
                && "soldier4".equals(event.weaponName)) {
            // Unit.soundAction('suicide_scream') starts as its first source waypoint resolves.
            return Cue.SUICIDE_SCREAM;
        }
        return cueForCombatEvent(event.type, event.payloadName);
    }

    /** Maps the source event kind and Flash payload identifier to an original cue. */
    public static Cue cueForCombatEvent(GameState.CombatEventType type,
                                        String payloadName) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case DIRECT_ATTACK:
                // Unit.soundAction('soldier_shoot') attaches this original asset.
                return Cue.SOLDIER_GUN;
            case STRIKE_INTERCEPTED:
                // explosion002/explosion003 both map to "Explode small3.wav".
                return Cue.EXPLOSION_SMALL3;
            case STRIKE_IMPACT:
                return explosionForPayload(payloadName);
            default:
                return null;
        }
    }

    private static Cue explosionForPayload(String payloadName) {
        if (payloadName == null) {
            return null;
        }
        switch (payloadName) {
            case "warhead1":
            case "bioload1":
                return Cue.EXPLOSION_SMALL3;
            case "warhead2":
            case "bioload2":
                return Cue.EXPLOSION_MEDIUM3;
            case "warhead3":
            case "bioload3":
                return Cue.EXPLOSION_MEDIUM;
            case "warhead4":
                return Cue.EXPLOSION_LARGE2;
            case "bioload4":
                return Cue.EXPLOSION_LARGE;
            default:
                return null;
        }
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

    private void stopAmbientPlayback() {
        if (ambientCue != null && ambientSoundId >= 0L) {
            stop(ambientCue, ambientSoundId);
        }
        ambientSoundId = -1L;
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
