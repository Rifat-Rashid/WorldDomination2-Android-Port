package com.example.worlddomination.game.simulation;

import com.example.worlddomination.game.ui.BattlefieldTerrainState;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class BattlefieldTerrainStateTest {
    @Test public void humanTerrainUsesAllFiveOriginalBuildingReligions() {
        String[] faiths = {"bhu", "chr", "com", "jew", "mus"};
        for (int building = 1; building <= faiths.length; building++) {
            GameSetup setup = setupWithOpponent(0);
            setup.setStartingBuilding(building);
            assertEquals(faiths[building - 1],
                    BattlefieldTerrainState.religion(new GameState(setup, 101L), 0));
        }
    }

    @Test public void opponentTerrainUsesItsOriginalLeadersReligion() {
        int[] leaders = {0, 1, 3, 6, 9};
        String[] faiths = {"chr", "mus", "bhu", "jew", "com"};
        for (int index = 0; index < leaders.length; index++) {
            GameState game = new GameState(setupWithOpponent(leaders[index]), 103L);
            assertEquals(faiths[index], BattlefieldTerrainState.religion(game, 1));
        }
    }

    @Test public void oneOpponentRetainsDefaultGroundInAllUnusedSlots() {
        GameState game = new GameState(setupWithOpponent(0), 105L);
        assertTrue(BattlefieldTerrainState.isPresent(game, 0));
        assertTrue(BattlefieldTerrainState.isPresent(game, 1));
        for (int island = 2; island < BattlefieldTerrainState.ISLAND_COUNT; island++) {
            assertTrue(BattlefieldTerrainState.isPresent(game, island));
            assertEquals("chr", BattlefieldTerrainState.religion(game, island));
            assertFalse("Ground does not create an opponent", game.getPlayerState(island).alive);
            assertEquals(0, game.getPlayerState(island).getPlayerColor());
        }
    }

    @Test public void fourOpponentsPopulateEveryIsland() {
        GameSetup setup = setupWithOpponent(0);
        assertTrue(setup.toggleOpponent(1));
        assertTrue(setup.toggleOpponent(6));
        assertTrue(setup.toggleOpponent(9));
        GameState game = new GameState(setup, 107L);
        for (int island = 0; island < BattlefieldTerrainState.ISLAND_COUNT; island++) {
            assertTrue(BattlefieldTerrainState.isPresent(game, island));
        }
    }

    @Test public void legalSourceSetupsRetainAllFiveObservedLandFrames() throws Exception {
        JsonValue source;
        try (InputStream input = getClass().getResourceAsStream("/source-unused-island-frames.json")) {
            org.junit.Assert.assertNotNull(input);
            byte[] bytes = input.readAllBytes();
            StringBuilder hash = new StringBuilder();
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hash.append(String.format(Locale.ROOT, "%02x", value & 255));
            }
            assertEquals("4c6a44436a630273ffd26aa12ec6248aadf8e0c381efce2fd325068589df5eaa", hash.toString());
            source = new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
        }
        JsonValue terrain = new JsonReader().parse(Files.readString(Paths.get(
                "src/main/assets/original/ui/terrain/manifest.json")));
        assertEquals(3, source.get("cases").size);
        for (JsonValue scenario : source.get("cases")) {
            GameSetup setup = new GameSetup(source.getLong("seed"));
            setup.setDifficulty(source.getInt("difficulty"));
            setup.setStartingBuilding(source.getInt("building"));
            for (JsonValue leader : scenario.get("leaders")) assertTrue(setup.toggleOpponent(leader.asInt()));
            GameState game = new GameState(setup, source.getLong("seed"));
            assertEquals(5, scenario.get("lands").size);
            for (JsonValue land : scenario.get("lands")) {
                int island = land.getInt("mapSide");
                assertTrue(land.getBoolean("visible"));
                assertTrue(BattlefieldTerrainState.isPresent(game, island));
                String religion = BattlefieldTerrainState.religion(game, island);
                assertEquals("Observed source clip frame for island " + island,
                        land.getInt("frame"), terrain.get("islands").get(island)
                                .get("variants").get(religion).getInt("frame"));
            }
        }
    }

    @Test public void defeatedOpponentRetainsItsTerrainInsteadOfAttackersReligion() {
        GameState game = new GameState(setupWithOpponent(1), 109L);
        game.getPlayerState(1).alive = false;
        game.getPlayerState(1).populationMillions = 0;
        game.getPlayerState(1).lastAttackedByPlayerId = 0;
        assertEquals("chr", game.getHuman().getReligionCode());
        assertEquals("mus", BattlefieldTerrainState.religion(game, 1));
        assertTrue(BattlefieldTerrainState.isPresent(game, 1));
    }

    @Test public void defeatedHumanRetainsTheSelectedBuildingTheme() {
        GameSetup setup = setupWithOpponent(1);
        setup.setStartingBuilding(1);
        GameState game = new GameState(setup, 111L);
        game.getHuman().alive = false;
        game.getHuman().lastAttackedByPlayerId = 1;
        assertEquals("bhu", BattlefieldTerrainState.religion(game, 0));
    }

    @Test public void changingFlagCreditDoesNotChangeTerrainOrEraseAnIsland() {
        GameSetup setup = setupWithOpponent(1);
        assertTrue(setup.toggleOpponent(6));
        GameState game = new GameState(setup, 113L);
        GameState.PlayerState nation = game.getPlayerState(1);
        nation.alive = false;
        for (int owner : new int[]{0, 2, -1, 4, 5}) {
            nation.lastAttackedByPlayerId = owner;
            assertEquals("mus", BattlefieldTerrainState.religion(game, 1));
        }
    }

    @Test public void playerColorDoesNotRecolorOrSelectTheTerrain() {
        for (int color = 1; color <= 5; color++) {
            GameSetup setup = setupWithOpponent(0);
            setup.setPlayerColor(color);
            setup.setStartingBuilding(4);
            assertEquals("jew", BattlefieldTerrainState.religion(new GameState(setup, 115L), 0));
        }
    }

    @Test public void absentGameAndInvalidSlotsHaveNoTerrain() {
        assertNull(BattlefieldTerrainState.religion(null, 0));
        GameState game = new GameState(1, 117L, false);
        assertNull(BattlefieldTerrainState.religion(game, -1));
        assertNull(BattlefieldTerrainState.religion(game, 5));
        assertFalse(BattlefieldTerrainState.isPresent(game, 5));
    }

    @Test public void inspectingTerrainDoesNotConsumeRandomnessOrMutateGameState() {
        CountingRandom random = new CountingRandom(119L);
        GameState game = new GameState(1, random, false);
        game.getPlayerState(1).alive = false;
        game.getPlayerState(1).lastAttackedByPlayerId = 2;
        int draws = random.draws;
        for (int frame = 0; frame < 600; frame++) {
            for (int island = 0; island < 5; island++) BattlefieldTerrainState.religion(game, island);
        }
        assertEquals(draws, random.draws);
        assertFalse(game.getPlayerState(1).alive);
        assertEquals(2, game.getPlayerState(1).getLastAttackedByPlayerId());
    }

    private static GameSetup setupWithOpponent(int leader) {
        GameSetup setup = new GameSetup();
        assertTrue(setup.toggleOpponent(leader));
        return setup;
    }

    private static final class CountingRandom extends Random {
        int draws;
        CountingRandom(long seed) { super(seed); }
        @Override protected int next(int bits) { draws++; return super.next(bits); }
    }
}
