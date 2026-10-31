package com.example.worlddomination.game.simulation;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GameSetupTest {
    @Test
    public void originalLeaderRosterAndReligionsArePreserved() {
        assertEquals(19, GameSetup.leaders().size());
        assertEquals("The Bombki", GameSetup.leader(0).name);
        assertEquals("chr", GameSetup.leader(0).religionCode);
        assertEquals("JP Barksender", GameSetup.leader(14).name);
        assertEquals(15, GameSetup.leader(14).portraitFrame);
        assertEquals("Bossi Isasad", GameSetup.leader(18).name);
        assertEquals("mus", GameSetup.leader(18).religionCode);
    }

    @Test
    public void opponentRosterRejectsDuplicatesAndAFifthLeader() {
        GameSetup setup = new GameSetup();
        assertFalse(setup.canStart());
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        assertTrue(setup.toggleOpponent(2));
        assertTrue(setup.toggleOpponent(3));
        assertFalse(setup.toggleOpponent(4));
        assertEquals(Arrays.asList(0, 1, 2, 3), setup.getSelectedOpponentIds());
        assertTrue(setup.toggleOpponent(2));
        assertEquals(Arrays.asList(0, 1, 3), setup.getSelectedOpponentIds());
        assertTrue(setup.canStart());
    }

    @Test
    public void setupOptionsUseOriginalDifficultyColorAndBuildingTables() {
        GameSetup setup = new GameSetup();
        setup.setDifficulty(2);
        setup.setPlayerColor(5);
        setup.setStartingBuilding(1);
        assertEquals(2, setup.getDifficulty());
        assertEquals("green", setup.getPlayerColorName());
        assertEquals("Buddhist", setup.getStartingBuildingName());
        assertEquals("bhu", setup.getHumanReligionCode());
        setup.cycleCandidateLeader(-1);
        assertEquals(18, setup.getCandidateLeaderId());
        setup.cycleCandidateLeader(1);
        assertEquals(0, setup.getCandidateLeaderId());
    }

    @Test
    public void selectingCarouselLeaderChangesCandidateWithoutChangingOpponentRoster() {
        GameSetup setup = new GameSetup();
        assertTrue(setup.toggleOpponent(2));

        setup.selectCandidateLeader(18);

        assertEquals(18, setup.getCandidateLeaderId());
        assertEquals("Bossi Isasad", setup.getCandidateLeader().name);
        assertEquals(Arrays.asList(2), setup.getSelectedOpponentIds());
    }

    @Test
    public void selectingCarouselLeaderRejectsIdsOutsideOriginalRoster() {
        GameSetup setup = new GameSetup();
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> setup.selectCandidateLeader(-1));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> setup.selectCandidateLeader(19));
        assertEquals(0, setup.getCandidateLeaderId());
    }

    @Test
    public void selectedOpponentLeadersKeepTheirSetupOrderInTheMatchSlots() {
        GameSetup setup = new GameSetup();
        assertTrue(setup.toggleOpponent(17));
        assertTrue(setup.toggleOpponent(3));

        GameState game = new GameState(setup, new ReferenceRandom(818L));

        assertEquals(17, game.getPlayerState(1).getLeaderId());
        assertEquals(3, game.getPlayerState(2).getLeaderId());
        assertEquals(GameSetup.leader(17).religionCode,
                game.getPlayerState(1).getReligionCode());
        assertEquals(GameSetup.leader(3).religionCode,
                game.getPlayerState(2).getReligionCode());
        assertTrue(game.getPlayerState(1).alive);
        assertTrue(game.getPlayerState(2).alive);
        assertFalse(game.getPlayerState(3).alive);
        assertFalse(game.getPlayerState(4).alive);
        assertEquals(-1, game.getPlayerState(3).getLeaderId());
        assertEquals(-1, game.getPlayerState(4).getLeaderId());
    }

    @Test
    public void sourcePlayerColorIdsMapToTankTurretLibraryNames() {
        assertEquals("blue", GameSetup.colorName(1));
        assertEquals("red", GameSetup.colorName(2));
        assertEquals("orange", GameSetup.colorName(3));
        assertEquals("yellow", GameSetup.colorName(4));
        assertEquals("green", GameSetup.colorName(5));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> GameSetup.colorName(0));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> GameSetup.colorName(6));
    }

    @Test
    public void seededSetupUsesSourceMenuDrawsAndNormalDifficulty() {
        GameSetup actualSetup = new GameSetup(0L);
        assertEquals(5, actualSetup.getPlayerColor());
        assertEquals(4, actualSetup.getStartingBuilding());
        assertEquals(1, actualSetup.getDifficulty());
    }

    @Test
    public void restartedSetupContinuesMenuRandomStreamIndependentOfMatchDraws() {
        GameSetup actualSetup = new GameSetup(0L);
        assertTrue(actualSetup.toggleOpponent(0));
        GameState actualGame = new GameState(actualSetup);
        assertTrue(actualGame.getPlayerState(1).alive);
        GameSetup actualRematch = actualSetup.createNextSetup();

        ReferenceRandom expectedMenuRandom = new ReferenceRandom(0L);
        expectedMenuRandom.nextInt(5);
        expectedMenuRandom.nextInt(5);
        GameSetup expectedRematch = new GameSetup(expectedMenuRandom);
        assertEquals(expectedRematch.getPlayerColor(), actualRematch.getPlayerColor());
        assertEquals(expectedRematch.getStartingBuilding(), actualRematch.getStartingBuilding());
        assertFalse(actualRematch.canStart());
    }
}
