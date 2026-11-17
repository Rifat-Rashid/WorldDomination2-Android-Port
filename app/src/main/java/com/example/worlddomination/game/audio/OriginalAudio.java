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
import java.util.Objects;

/** Plays the original Flash-exported MP3 assets through libGDX's Android audio backend. */
public final class OriginalAudio {
    public enum Cue {
        THEME("2_WDTheme.wav.mp3.gz"),
        STARTUP("58_interface_startup.wav.mp3.gz"),
        ACTION_SWITCH("57_interface_Switch Action selection.wav.mp3.gz"),
        ACTION_SWITCH_OFF("56_interface_Switch Action selection2.wav.mp3.gz"),
        STOCK_SELECT("54_interface_Switch Sounds 1.wav.mp3.gz"),
        UNIT_SELECT("59_interface_select unit bleep.wav.mp3.gz"),
        CONFIRM_OPEN("62_interface_Closeconfirmbutton.wav.mp3.gz"),
        CONFIRM_CLOSE("60_interface_Openconfirmbutton.wav.mp3.gz"),
        ACTION_ACCEPT("64_interface_Accept action bleep.wav.mp3.gz"),
        ACTION_OPEN("61_interface_openactiondevice.wav.mp3.gz"),
        ACTION_CLOSE("63_interface_closeactiondevice.wav.mp3.gz"),
        SCORE_COUNTER("31_Score counter.Mp3.mp3.gz"),
        SATELLITE_MULTIPLIER("34_Satelite multiplier.Mp3.mp3.gz"),
        SATELLITE_FLIGHT("33_Satelite_flight.wav.mp3.gz"),
        SATELLITE_LASER("19_unit_Sattelite Lasershot.wav.mp3.gz"),
        SATELLITE_ATTACK("32_Sattelite attack.wav.mp3.gz"),
        FLAG_BONUS("75_Country domination bonus.Mp3.mp3.gz"),
        SOLDIER_GUN("14_unit_Soldier gun.wav.mp3.gz"),
        SEAL_GUN("16_unit_seal gun.wav.mp3.gz"),
        WARROCKET("3_unit_Warrocket.wav.mp3.gz"),
        TANK_DRIVE("4_unit_Tank.wav.mp3.gz"),
        TANK_GUN("5_unit_Tank gun.wav.mp3.gz"),
        TANK_CHOPPER("6_unit_Tank chopper.wav.mp3.gz"),
        SUICIDE_SCREAM("7_unit_Suicide scream.wav.mp3.gz"),
        SUICIDE_EXPLODE("9_unit_Suicide explode.wav.mp3.gz"),
        SUBMARINE_SURFACE("10_unit_Submarine water splash.wav.mp3.gz"),
        FIGHTER_FLIGHT("20_unit_fighterflight.wav.mp3.gz"),
        FIGHTER_TAKEOFF("66_unit_Fightertakeoff.wav.mp3.gz"),
        CARRIERJET_FLIGHT("21_unit_Carrierjet flight.wav.mp3.gz"),
        CARRIERJET_TAKEOFF("76_unit_Carrierjet take off.wav.mp3.gz"),
        ANTI_AIR_ROTATE("91_building_Anti air rotate.wav.mp3.gz"),
        ANTI_AIR_GUN("94_building_Anti air gun.wav.mp3.gz"),
        ANTI_ROCKET_FIRE("90_building_Anti rocket fire 4.wav.mp3.gz"),
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
        MASS_MEDIA_REVEAL_1("46_Massmedia 1.wav.mp3.gz"),
        MASS_MEDIA_REVEAL_2("45_Massmedia 2.wav.mp3.gz"),
        MASS_MEDIA_REVEAL_3("44_Massmedia 3.wav.mp3.gz"),
        MASS_MEDIA_REVEAL_4("43_Massmedia 4.wav.mp3.gz"),
        RESOURCES_REVEAL("80_building_Resource.wav.mp3.gz"),
        PRODUCTION_REVEAL("83_building_factory.wav.mp3.gz"),
        RESEARCH_REVEAL("77_building_tech.wav.mp3.gz"),
        PROPAGANDA_REVEAL("81_building_Propaganda.wav.mp3.gz"),
        DIPLOMACY_REVEAL("73_Diplomacy humans.wav.mp3.gz"),
        AIRFIELD_REVEAL("95_building_airfield.wav.mp3.gz"),
        SILO_REVEAL("79_building_silo.wav.mp3.gz"),
        SUB_REVEAL("29_sub_ping.wav.mp3.gz"),
        HATCH_REVEAL("8_unit_Suicide Hatch.wav.mp3.gz"),
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
    private static final float SOURCE_UI_VOLUME = 1f;

    private final Map<Cue, Sound> sounds = new EnumMap<>(Cue.class);
    private final Map<Cue, Long> soundLifetimes = new EnumMap<>(Cue.class);
    private final SoundVoiceRegistry soundVoices = new SoundVoiceRegistry();
    private final ActionRevealSounds.CueSink actionRevealSink = this::playBattleActionCue;
    private Sound[] battleActionSamples = new Sound[16];
    private long[] battleActionIds = new long[16];
    private int battleActionCount;
    private final SatelliteAudioPlayback satellitePlayback;
    private final AircraftAudioPlayback aircraftPlayback;
    private final AntiDefenseAudioPlayback antiDefensePlayback = new AntiDefenseAudioPlayback(
            this::loadAntiDefenseMusic, EFFECT_VOLUME, OriginalAudio::dispatchAircraftResume);
    private Music theme;
    private final BattleAmbientPlayback battleAmbient = new BattleAmbientPlayback(this::loadAmbientSound);
    private boolean soundEnabled = true;
    private boolean musicEnabled = true;
    private boolean outputMuted;
    private boolean resumeMusicAfterPause;
    private boolean initialized;

    public OriginalAudio() {
        satellitePlayback = new SatelliteAudioPlayback(this::loadMusic, EFFECT_VOLUME);
        aircraftPlayback = new AircraftAudioPlayback(this::loadMusic, EFFECT_VOLUME,
                OriginalAudio::dispatchAircraftResume);
    }

    OriginalAudio(SatelliteAudioPlayback satellitePlayback) {
        this.satellitePlayback = Objects.requireNonNull(satellitePlayback, "satellitePlayback");
        aircraftPlayback = new AircraftAudioPlayback(this::loadMusic, EFFECT_VOLUME,
                OriginalAudio::dispatchAircraftResume);
    }

    OriginalAudio(SatelliteAudioPlayback satellitePlayback, AircraftAudioPlayback aircraftPlayback) {
        this.satellitePlayback = Objects.requireNonNull(satellitePlayback, "satellitePlayback");
        this.aircraftPlayback = Objects.requireNonNull(aircraftPlayback, "aircraftPlayback");
    }

    private static void dispatchAircraftResume(Runnable resume) {
        // AndroidMusic completion callbacks already in the GL queue must run
        // before an aircraft decides whether to resume takeoff or its next cue.
        if (Gdx.app == null) resume.run();
        else Gdx.app.postRunnable(resume);
    }

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
        preload(Cue.SATELLITE_FLIGHT);
        preload(Cue.FLAG_BONUS);
        preload(Cue.SOLDIER_GUN);
        preload(Cue.SEAL_GUN);
        preload(Cue.WARROCKET);
        preload(Cue.TANK_DRIVE);
        preload(Cue.TANK_GUN);
        preload(Cue.TANK_CHOPPER);
        preload(Cue.SUICIDE_SCREAM);
        preload(Cue.SUICIDE_EXPLODE);
        preload(Cue.SUBMARINE_SURFACE);
        preload(Cue.FIGHTER_FLIGHT);
        preload(Cue.FIGHTER_TAKEOFF);
        preload(Cue.CARRIERJET_FLIGHT);
        preload(Cue.CARRIERJET_TAKEOFF);
        preload(Cue.ANTI_AIR_ROTATE);
        preload(Cue.ANTI_AIR_GUN);
        preload(Cue.ANTI_ROCKET_FIRE);
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
        preload(Cue.MASS_MEDIA_REVEAL_1);
        preload(Cue.MASS_MEDIA_REVEAL_2);
        preload(Cue.MASS_MEDIA_REVEAL_3);
        preload(Cue.MASS_MEDIA_REVEAL_4);
        preload(Cue.RESOURCES_REVEAL);
        preload(Cue.PRODUCTION_REVEAL);
        preload(Cue.RESEARCH_REVEAL);
        preload(Cue.PROPAGANDA_REVEAL);
        preload(Cue.DIPLOMACY_REVEAL);
        preload(Cue.AIRFIELD_REVEAL);
        preload(Cue.SILO_REVEAL);
        preload(Cue.SUB_REVEAL);
        preload(Cue.HATCH_REVEAL);
        preload(Cue.TECHNOLOGY_AMBIENT);
        play(Cue.STARTUP);
        if (theme != null && musicEnabled) {
            theme.setLooping(true);
            theme.setVolume(outputMuted ? 0f : MUSIC_VOLUME);
            theme.play();
        }
    }

    /** Toggles source sound effects without changing the independent music setting. */
    public boolean toggleSound() {
        soundEnabled = !soundEnabled;
        // Option.setSound only gates new source soundAction requests. Existing
        // ambience and completion chains retain their current playback and gain.
        satellitePlayback.setSoundEnabled(soundEnabled);
        aircraftPlayback.setSoundEnabled(soundEnabled);
        antiDefensePlayback.setSoundEnabled(soundEnabled);
        battleAmbient.setSoundEnabled(soundEnabled);
        return soundEnabled;
    }

    /** Toggles the original theme track without changing sound effects or ambience. */
    public boolean toggleMusic() {
        musicEnabled = !musicEnabled;
        if (theme != null) {
            if (musicEnabled) {
                theme.setLooping(true);
                theme.setVolume(outputMuted ? 0f : MUSIC_VOLUME);
                theme.play();
            } else {
                theme.stop();
            }
        }
        return musicEnabled;
    }

    /** Legacy source sound-option alias. The phone toolbar uses toggleOutputMuted. */
    public boolean toggleMuted() {
        toggleSound();
        return !soundEnabled;
    }

    public boolean isMuted() {
        return !soundEnabled;
    }

    /** Phone backend mute: silence active/future output without changing source options. */
    public boolean toggleOutputMuted() {
        outputMuted = !outputMuted;
        soundVoices.setMuted(outputMuted);
        if (theme != null) theme.setVolume(outputMuted ? 0f : MUSIC_VOLUME);
        satellitePlayback.setOutputMuted(outputMuted);
        aircraftPlayback.setOutputMuted(outputMuted);
        antiDefensePlayback.setOutputMuted(outputMuted);
        battleAmbient.setOutputMuted(outputMuted);
        return outputMuted;
    }

    public boolean isOutputMuted() { return outputMuted; }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    /** The phone adapter constructs a fresh ambient slot ring for each private battle. */
    public void beginBattleAmbient() { battleAmbient.beginBattle(); }

    /** View.showActionBuildingFromObj uses the literal frame, before timed labels. */
    public boolean startBuildingAmbient(String frame) { return battleAmbient.startForBuilding(frame); }

    /** View.initiate_AB_interval stops these voices when its label pass is exhausted. */
    public void finishBattleAmbient() { battleAmbient.stopAll(); }

    /** A new private battle owns only the reveal voices it started. */
    public void beginBattleActionSounds() { finishBattleActionSounds(); }

    /** Called at each action-building label, with the private religion draw already resolved. */
    public void playActionReveal(GameState.Action action, String[] physicalSlots,
                                 int mediaVariant) {
        if (!soundEnabled) return;
        ActionRevealSounds.play(action, physicalSlots, mediaVariant, actionRevealSink);
    }

    /** Stop each retained id, without stopping another owner's voice of the same sample. */
    public void finishBattleActionSounds() {
        for (int i = 0; i < battleActionCount; i++) {
            battleActionSamples[i].stop(battleActionIds[i]);
            soundVoices.forget(battleActionSamples[i], battleActionIds[i]);
            battleActionSamples[i] = null;
        }
        battleActionCount = 0;
    }

    private void playBattleActionCue(Cue cue) {
        Sound sound = sounds.get(cue);
        if (sound == null) sound = loadSound(cue);
        if (sound == null) return;
        long id = sound.play(outputMuted ? 0f : 1f, 1f, 0f);
        if (id < 0) return;
        soundVoices.register(sound, id, 1f, 0f, false,
                soundLifetimes.getOrDefault(cue, Long.MAX_VALUE));
        if (battleActionCount == battleActionSamples.length) {
            int nextLength = battleActionSamples.length * 2;
            battleActionSamples = java.util.Arrays.copyOf(battleActionSamples, nextLength);
            battleActionIds = java.util.Arrays.copyOf(battleActionIds, nextLength);
        }
        battleActionSamples[battleActionCount] = sound;
        battleActionIds[battleActionCount++] = id;
    }

    public void play(Cue cue) {
        play(cue, sourceUiPan(cue));
    }

    /** Plays an original effect at the source Flash stage's normalized horizontal position. */
    public void play(Cue cue, float pan) {
        playInstance(cue, pan);
    }

    /** Returns a battle-owned voice id so a source Unit can stop only its own sound. */
    public long playInstance(Cue cue, float pan) {
        if (!soundEnabled || cue == Cue.THEME) {
            return -1L;
        }
        if (cue == Cue.SATELLITE_LASER) {
            satellitePlayback.play();
            return -1L;
        }
        Sound sound = sounds.get(cue);
        if (sound == null) {
            sound = loadSound(cue);
        }
        if (sound == null) return -1L;
        float boundedPan = Float.isFinite(pan) ? Math.max(-1f, Math.min(1f, pan)) : 0f;
        float volume = sourceEffectVolume(cue);
        long id = sound.play(outputMuted ? 0f : volume, 1f, boundedPan);
        soundVoices.register(sound, id, volume, boundedPan, false,
                soundLifetimes.getOrDefault(cue, Long.MAX_VALUE));
        return id;
    }

    /** Starts a looping original cue and returns its libGDX sound instance id. */
    public long loop(Cue cue) {
        if (!soundEnabled || cue == Cue.THEME) {
            return -1L;
        }
        Sound sound = sounds.get(cue);
        if (sound == null) {
            sound = loadSound(cue);
        }
        if (sound == null) return -1L;
        float volume = sourceEffectVolume(cue);
        float pan = sourceUiPan(cue);
        long id = sound.loop(outputMuted ? 0f : volume, 1f, pan);
        soundVoices.register(sound, id, volume, pan, true, Long.MAX_VALUE);
        return id;
    }

    /** Sub_model's interface action table supplies two pan overrides and no volume overrides. */
    private static float sourceUiPan(Cue cue) {
        if (cue == Cue.ACTION_SWITCH || cue == Cue.ACTION_SWITCH_OFF) return -0.8f;
        if (cue == Cue.STOCK_SELECT) return 0.8f;
        return 0f;
    }

    private static float sourceEffectVolume(Cue cue) {
        switch (cue) {
            case STARTUP:
            case ACTION_SWITCH:
            case ACTION_SWITCH_OFF:
            case STOCK_SELECT:
            case UNIT_SELECT:
            case CONFIRM_OPEN:
            case CONFIRM_CLOSE:
            case ACTION_ACCEPT:
            case ACTION_OPEN:
            case ACTION_CLOSE:
                // MenuSound starts at 100%; none of these action records sets _vol.
                return SOURCE_UI_VOLUME;
            default:
                return EFFECT_VOLUME;
        }
    }

    public void stop(Cue cue, long soundId) {
        if (soundId < 0L) {
            return;
        }
        Sound sound = sounds.get(cue);
        if (sound != null) {
            sound.stop(soundId);
            soundVoices.forget(sound, soundId);
        }
    }

    /** Updates one existing libGDX voice without loading or restarting its cue. */
    public void setInstancePan(Cue cue, long soundId, float pan) {
        if (cue == null || soundId < 0L || !Float.isFinite(pan)) return;
        Sound sound = sounds.get(cue);
        if (sound != null) {
            soundVoices.pan(sound, soundId, Math.max(-1f, Math.min(1f, pan)));
        }
    }

    public void pause() {
        satellitePlayback.pause();
        aircraftPlayback.pause();
        antiDefensePlayback.pause();
        battleAmbient.pause();
        resumeMusicAfterPause = musicEnabled && theme != null && theme.isPlaying();
        if (resumeMusicAfterPause) {
            theme.pause();
        }
        soundVoices.pause();
    }

    public void resume() {
        satellitePlayback.resume();
        aircraftPlayback.resume();
        antiDefensePlayback.resume();
        battleAmbient.resume();
        if (resumeMusicAfterPause && musicEnabled && theme != null) {
            theme.play();
        }
        // The sound option cannot suppress resumption of previously active voices.
        soundVoices.resume();
        resumeMusicAfterPause = false;
    }

    public void dispose() {
        finishBattleActionSounds();
        satellitePlayback.dispose();
        aircraftPlayback.dispose();
        antiDefensePlayback.dispose();
        battleAmbient.dispose();
        if (theme != null) {
            theme.dispose();
            theme = null;
        }
        for (Sound sound : sounds.values()) {
            sound.dispose();
        }
        sounds.clear();
        soundLifetimes.clear();
        soundVoices.clear();
        initialized = false;
    }

    /** Warm each source satellite's independent completion-driven sound pair. */
    public void prepareSatelliteAudio(int count) {
        satellitePlayback.prepare(count);
        satellitePlayback.setSoundEnabled(soundEnabled);
    }

    /** Python BattlePlayback.finish closes remaining sounds at endBattle. */
    public void finishSatelliteAudio() {
        satellitePlayback.finish();
    }

    /** One source Unit owns both cues; actual completion advances its linkage. */
    public AircraftAudioPlayback.Owner createAircraftAudio(AircraftAudioPlayback.Kind kind) {
        aircraftPlayback.setSoundEnabled(soundEnabled);
        return aircraftPlayback.createOwner(kind);
    }

    /** BattlePlayback.finish closes every surviving Unit's sound and pending callback. */
    public void finishAircraftAudio() { aircraftPlayback.finish(); }

    /** Each source Unit owns separate rotation and finite firing sound groups. */
    public AntiDefenseAudioPlayback.Owner createAntiDefenseOwner(AntiDefenseAudioPlayback.Type type) {
        antiDefensePlayback.setSoundEnabled(soundEnabled);
        return antiDefensePlayback.createOwner(type);
    }

    public void finishAntiDefenseAudio() { antiDefensePlayback.finish(); }

    /** SWF sample bounds and finite repeat counts are baked before packaging. */
    public static String antiDefenseMusicPath(Cue cue, int repeats) {
        if (cue == Cue.ANTI_AIR_GUN && repeats == 8)
            return "generated/audio/anti-air-gun-eight.wav";
        if (cue == Cue.ANTI_AIR_ROTATE && repeats == 1)
            return "generated/audio/anti-air-rotate.wav";
        if (cue == Cue.ANTI_ROCKET_FIRE && repeats == 1)
            return "generated/audio/anti-rocket-fire.wav";
        throw new IllegalArgumentException("Unsupported source anti-defense sound group");
    }

    /** Presenter_interface.soundAction('religion') chooses one of four cues. */
    public static Cue massMediaRevealCue(int variant) {
        switch (variant) {
            case 1: return Cue.MASS_MEDIA_REVEAL_1;
            case 2: return Cue.MASS_MEDIA_REVEAL_2;
            case 3: return Cue.MASS_MEDIA_REVEAL_3;
            case 4: return Cue.MASS_MEDIA_REVEAL_4;
            default: return null;
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

    private FiniteRepeatSound loadAmbientSound(Cue cue) {
        Sound sound = sounds.get(cue);
        if (sound == null) sound = loadSound(cue);
        // MainActivity supplies the native finite-repeat backend. Never approximate
        // the source's 9999 plays with an infinite loop or a gameplay timer.
        return sound instanceof FiniteRepeatSound ? (FiniteRepeatSound) sound : null;
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
            // All packaged cues are MPEG audio, whose minimum supported bitrate is
            // 8 kbit/s. One millisecond per encoded byte plus a second is a generous
            // playback upper bound (including tags/padding). Retire stale SoundPool
            // handles only after that bound; loops persist until their source stop.
            soundLifetimes.put(cue, Math.addExact(Math.multiplyExact(file.length(),
                    1_000_000L), 1_000_000_000L));
            return sound;
        } catch (RuntimeException exception) {
            logFailure(cue, exception);
            return null;
        }
    }

    private Music loadAntiDefenseMusic(Cue cue, int repeats) {
        String path = antiDefenseMusicPath(cue, repeats);
        if (Gdx.audio == null || Gdx.files == null) return null;
        try {
            return Gdx.audio.newMusic(Gdx.files.internal(path));
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
