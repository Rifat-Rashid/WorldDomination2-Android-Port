package com.example.worlddomination.game.audio;

import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class OriginalAudioTest {
    @Test
    public void everyMappedCueIsAnOriginalGzipWrappedMp3Asset() throws Exception {
        for (OriginalAudio.Cue cue : OriginalAudio.Cue.values()) {
            assertTrue(OriginalAudio.internalPath(cue).endsWith(".mp3"));
            assertEquals(OriginalAudio.internalPath(cue) + ".gz",
                    OriginalAudio.sourceAssetPath(cue));
            File asset = locateAsset(OriginalAudio.sourceAssetPath(cue));
            assertTrue("Missing original audio asset: " + cue.name(), asset.isFile());
            try (InputStream gzip = new GZIPInputStream(new FileInputStream(asset))) {
                int first = gzip.read();
                int second = gzip.read();
                boolean mpegSync = first == 0xff && (second & 0xe0) == 0xe0;
                boolean id3Header = first == 'I' && second == 'D';
                assertTrue("Audio asset is not an MP3 stream: " + cue.name(),
                        mpegSync || id3Header);
            }
            assertTrue(OriginalAudio.cachePath(cue).endsWith(".mp3"));
            assertTrue("Cache paths should be safe for Android filesystems",
                    !OriginalAudio.cachePath(cue).contains(" "));
        }
    }

    @Test
    public void themeUsesTheRecoveredWorldDominationTrack() {
        assertEquals("original/runtime/audio/2_WDTheme.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.THEME));
    }

    @Test
    public void endgameUsesRecoveredCounterAndBonusSounds() {
        assertEquals("original/runtime/audio/31_Score counter.Mp3.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.SCORE_COUNTER));
        assertEquals("original/runtime/audio/34_Satelite multiplier.Mp3.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.SATELLITE_MULTIPLIER));
        assertEquals("original/runtime/audio/75_Country domination bonus.Mp3.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.FLAG_BONUS));
        assertEquals("original/runtime/audio/48_Loose.Mp3.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.DEFEAT));
    }

    @Test
    public void resolvedCombatEventsUseTheFlashUnitAndExplosionSoundMap() {
        assertEquals(OriginalAudio.Cue.SOLDIER_GUN,
                OriginalAudio.cueForCombatEvent(
                        GameState.CombatEventType.DIRECT_ATTACK, null));
        assertEquals(OriginalAudio.Cue.EXPLOSION_SMALL3,
                OriginalAudio.cueForCombatEvent(
                        GameState.CombatEventType.STRIKE_INTERCEPTED, "warhead4"));
        assertEquals(OriginalAudio.Cue.EXPLOSION_SMALL3,
                OriginalAudio.cueForCombatEvent(
                        GameState.CombatEventType.STRIKE_IMPACT, "warhead1"));
        assertEquals(OriginalAudio.Cue.EXPLOSION_MEDIUM3,
                OriginalAudio.cueForCombatEvent(
                        GameState.CombatEventType.STRIKE_IMPACT, "bioload2"));
        assertEquals(OriginalAudio.Cue.EXPLOSION_MEDIUM,
                OriginalAudio.cueForCombatEvent(
                        GameState.CombatEventType.STRIKE_IMPACT, "warhead3"));
        assertEquals(OriginalAudio.Cue.EXPLOSION_LARGE2,
                OriginalAudio.cueForCombatEvent(
                        GameState.CombatEventType.STRIKE_IMPACT, "warhead4"));
        assertEquals(OriginalAudio.Cue.EXPLOSION_LARGE,
                OriginalAudio.cueForCombatEvent(
                        GameState.CombatEventType.STRIKE_IMPACT, "bioload4"));
        assertNull(OriginalAudio.cueForCombatEvent(
                GameState.CombatEventType.STRIKE_IMPACT, "unmapped-payload"));
        assertNull(OriginalAudio.cueForCombatEvent(null, null));
        assertNull(OriginalAudio.cueForCombatEvent((GameState.CombatEvent) null));
    }

    @Test
    public void combatCuesPointToTheOriginalPackagedSounds() {
        assertEquals("original/runtime/audio/14_unit_Soldier gun.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.SOLDIER_GUN));
        assertEquals("original/runtime/audio/16_unit_seal gun.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.SEAL_GUN));
        assertEquals("original/runtime/audio/3_unit_Warrocket.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.WARROCKET));
        assertEquals("original/runtime/audio/7_unit_Suicide scream.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.SUICIDE_SCREAM));
        assertEquals("original/runtime/audio/98_Explode small3.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.EXPLOSION_SMALL3));
        assertEquals("original/runtime/audio/71_Explode medium 3.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.EXPLOSION_MEDIUM3));
        assertEquals("original/runtime/audio/70_explode medium.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.EXPLOSION_MEDIUM));
        assertEquals("original/runtime/audio/97_Explode large2.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.EXPLOSION_LARGE2));
        assertEquals("original/runtime/audio/72_Explode large.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.EXPLOSION_LARGE));
    }

    @Test
    public void tankBattleCuesUseTheRecoveredOriginalSounds() {
        assertEquals("original/runtime/audio/4_unit_Tank.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.TANK_DRIVE));
        assertEquals("original/runtime/audio/5_unit_Tank gun.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.TANK_GUN));
        assertEquals("original/runtime/audio/6_unit_Tank chopper.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.TANK_CHOPPER));
    }

    @Test
    public void carrierAircraftCuesUseTheRecoveredOriginalTakeoffAndFlightSounds() {
        assertEquals("original/runtime/audio/76_unit_Carrierjet take off.wav.mp3.gz",
                OriginalAudio.sourceAssetPath(OriginalAudio.Cue.CARRIERJET_TAKEOFF));
        assertEquals("original/runtime/audio/21_unit_Carrierjet flight.wav.mp3.gz",
                OriginalAudio.sourceAssetPath(OriginalAudio.Cue.CARRIERJET_FLIGHT));
    }

    @Test
    public void actionPanelsUseTheirOriginalAmbientSoundAssignments() {
        assertEquals(OriginalAudio.Cue.RESOURCES_AMBIENT,
                OriginalAudio.ambientForAction(GameState.Action.RESOURCES));
        assertEquals(OriginalAudio.Cue.PRODUCTION_AMBIENT,
                OriginalAudio.ambientForAction(GameState.Action.PRODUCTION));
        assertEquals(OriginalAudio.Cue.TECHNOLOGY_AMBIENT,
                OriginalAudio.ambientForAction(GameState.Action.RESEARCH));
        assertEquals(OriginalAudio.Cue.MASS_MEDIA_AMBIENT,
                OriginalAudio.ambientForAction(GameState.Action.MASS_MEDIA));
        assertEquals(OriginalAudio.Cue.DIPLOMACY_AMBIENT,
                OriginalAudio.ambientForAction(GameState.Action.DIPLOMACY));
        assertEquals(OriginalAudio.Cue.DEFENSE_AMBIENT,
                OriginalAudio.ambientForAction(GameState.Action.DEFENSE));
        assertEquals(OriginalAudio.Cue.GAME_AMBIENT,
                OriginalAudio.ambientForAction(GameState.Action.ATTACK));
        assertEquals(OriginalAudio.Cue.GAME_AMBIENT,
                OriginalAudio.ambientForAction(GameState.Action.PREPARED_ATTACK));
        assertEquals(OriginalAudio.Cue.GAME_AMBIENT,
                OriginalAudio.ambientForAction(GameState.Action.PROPAGANDA));
        assertEquals(OriginalAudio.Cue.GAME_AMBIENT,
                OriginalAudio.ambientForAction(null));
    }

    @Test
    public void ambienceCuePathsMatchTheOriginalMenuMappings() {
        assertEquals("original/runtime/audio/65_General ambient.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.GAME_AMBIENT));
        assertEquals("original/runtime/audio/35_Resources ambient loop.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.RESOURCES_AMBIENT));
        assertEquals("original/runtime/audio/41_Production Ambient loop.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.PRODUCTION_AMBIENT));
        assertEquals("original/runtime/audio/96_Ambience Diplomacy.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.DIPLOMACY_AMBIENT));
        assertEquals("original/runtime/audio/74_Defence.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.DEFENSE_AMBIENT));
        assertEquals("original/runtime/audio/47_Massmedia ambient loop.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.MASS_MEDIA_AMBIENT));
        assertEquals("original/runtime/audio/28_Technology ambient loop.wav.mp3",
                OriginalAudio.internalPath(OriginalAudio.Cue.TECHNOLOGY_AMBIENT));
    }

    private static File locateAsset(String relativePath) {
        File directory = new File(System.getProperty("user.dir"));
        while (directory != null) {
            File appAsset = new File(directory, "app/src/main/assets/" + relativePath);
            if (appAsset.isFile()) {
                return appAsset;
            }
            File moduleAsset = new File(directory, "src/main/assets/" + relativePath);
            if (moduleAsset.isFile()) {
                return moduleAsset;
            }
            directory = directory.getParentFile();
        }
        return new File(System.getProperty("user.dir"), relativePath);
    }
}
