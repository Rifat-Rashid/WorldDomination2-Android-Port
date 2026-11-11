package com.example.worlddomination.game.simulation;

import com.example.worlddomination.game.ui.BattlefieldFlagState;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class BattlefieldFlagStateTest {
    @Test
    public void centerFlagUsesEachSelectedHumanColorIncludingRed() {
        for (int color = 1; color <= 5; color++) {
            GameSetup setup = setupWithOpponent(0);
            setup.setPlayerColor(color);
            GameState game = new GameState(setup, 17L);
            assertEquals(0, BattlefieldFlagState.ownerPlayerId(game, 0));
            assertEquals(color, BattlefieldFlagState.color(game, 0));
        }
    }

    @Test
    public void centerPoleUsesEachSourceBuildingReligionLabel() {
        String[] labels = {"bhu", "chr", "com", "jew", "mus"};
        for (int building = 1; building <= labels.length; building++) {
            GameSetup setup = setupWithOpponent(0);
            setup.setStartingBuilding(building);
            GameState game = new GameState(setup, 19L);
            assertEquals(labels[building - 1], BattlefieldFlagState.religion(game, 0));
        }
    }

    @Test
    public void livingOpponentUsesItsAssignedColorAndOwnReligion() {
        int[] leaders = {0, 1, 3, 6, 9};
        String[] labels = {"chr", "mus", "bhu", "jew", "com"};
        for (int index = 0; index < leaders.length; index++) {
            GameState game = new GameState(setupWithOpponent(leaders[index]), 21L);
            GameState.PlayerState opponent = game.getPlayerState(1);
            opponent.lastAttackedByPlayerId = 0;
            assertEquals(1, BattlefieldFlagState.ownerPlayerId(game, 1));
            assertEquals(opponent.getPlayerColor(), BattlefieldFlagState.color(game, 1));
            assertEquals(labels[index], BattlefieldFlagState.religion(game, 1));
        }
    }

    @Test
    public void unusedIslandsRemainEmptyDespiteTheirDefaultHumanAttribution() {
        GameState game = new GameState(setupWithOpponent(0), 23L);
        for (int island = 2; island < BattlefieldFlagState.ISLAND_COUNT; island++) {
            assertEquals(0, game.getPlayerState(island).getPlayerColor());
            assertEmpty(game, island);
        }
    }

    @Test
    public void defeatedNationUsesCreditedAttackersColorAndReligion() {
        GameSetup setup = setupWithOpponent(1);
        assertTrue(setup.toggleOpponent(6));
        GameState game = new GameState(setup, 25L);
        game.getPlayerState(1).alive = false;
        game.getPlayerState(1).lastAttackedByPlayerId = 2;
        assertEquals(2, BattlefieldFlagState.ownerPlayerId(game, 1));
        assertEquals("jew", BattlefieldFlagState.religion(game, 1));
        assertEquals(game.getPlayerState(2).getPlayerColor(),
                BattlefieldFlagState.color(game, 1));
    }

    @Test
    public void defeatedHumanAlsoUsesItsCreditedAttackersFlag() {
        GameState game = new GameState(setupWithOpponent(1), 27L);
        game.getHuman().alive = false;
        game.getHuman().lastAttackedByPlayerId = 1;
        assertEquals(1, BattlefieldFlagState.ownerPlayerId(game, 0));
        assertEquals("mus", BattlefieldFlagState.religion(game, 0));
        assertEquals(game.getPlayerState(1).getPlayerColor(), BattlefieldFlagState.color(game, 0));
    }

    @Test
    public void missingInvalidOrUnusedCreditedOwnersLeaveTheFlagEmpty() {
        GameState game = new GameState(setupWithOpponent(0), 29L);
        GameState.PlayerState nation = game.getPlayerState(1);
        nation.alive = false;
        for (int owner : new int[]{-1, 5, Integer.MAX_VALUE, 4}) {
            nation.lastAttackedByPlayerId = owner;
            assertEmpty(game, 1);
        }
    }

    @Test
    public void viewReadsDirectSourceCreditWithoutInventingAChainTraversal() {
        GameState game = new GameState(1, 31L, false);
        game.getPlayerState(1).alive = false;
        game.getPlayerState(1).lastAttackedByPlayerId = 2;
        game.getPlayerState(2).alive = false;
        game.getPlayerState(2).lastAttackedByPlayerId = 3;
        assertEquals(2, BattlefieldFlagState.ownerPlayerId(game, 1));
        assertEquals(game.getPlayerState(2).getPlayerColor(), BattlefieldFlagState.color(game, 1));
    }

    @Test
    public void eliminatingAConquerorUpdatesAllOfItsPreviouslyCreditedFlags() {
        GameState game = new GameState(1, 214L, false);
        GameState.PlayerState conqueror = game.getPlayerState(1);
        GameState.PlayerState defeated = game.getPlayerState(2);
        for (GameState.PlayerState opponent : game.getPlayers()) {
            if (opponent.human) continue;
            for (CityState city : opponent.getCities()) city.populationMillions = 0;
            opponent.populationMillions = 0;
            if (opponent.id != conqueror.id) opponent.alive = false;
        }
        conqueror.getCity(0).populationMillions = 1;
        conqueror.populationMillions = 1;
        defeated.lastAttackedByPlayerId = conqueror.id;
        assertEquals(conqueror.id, BattlefieldFlagState.ownerPlayerId(game, defeated.id));

        game.getHuman().getWeapons().get("soldier1").inStock = 1;
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(conqueror.id, 0, "soldier1"));
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();

        assertFalse(conqueror.alive);
        assertEquals(0, BattlefieldFlagState.ownerPlayerId(game, conqueror.id));
        assertEquals(0, BattlefieldFlagState.ownerPlayerId(game, defeated.id));
        assertEquals(game.getHuman().getPlayerColor(), BattlefieldFlagState.color(game, defeated.id));
        assertEquals(game.getHuman().getReligionCode(), BattlefieldFlagState.religion(game, defeated.id));
    }

    @Test
    public void absentGameAndOutOfRangeIslandHaveNoFlag() {
        assertEmpty(null, 0);
        GameState game = new GameState(1, 33L, false);
        assertEmpty(game, -1);
        assertEmpty(game, BattlefieldFlagState.ISLAND_COUNT);
    }

    @Test
    public void anchorsMatchTheFiveOriginalBattleMoviePlacements() {
        float[] x = {453.5f, 153.4f, 674.8f, 106.65f, 738.8f};
        float[] y = {341.95f, 136.05f, 132.05f, 554.6f, 544.2f};
        for (int island = 0; island < x.length; island++) {
            assertEquals(x[island], BattlefieldFlagState.sourceX(island), 0.0001f);
            assertEquals(y[island], BattlefieldFlagState.sourceY(island), 0.0001f);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeAnchorIndexIsRejected() {
        BattlefieldFlagState.sourceX(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void anchorPastTheLastIslandIsRejected() {
        BattlefieldFlagState.sourceY(BattlefieldFlagState.ISLAND_COUNT);
    }

    @Test
    public void waveAndHighlightFramesLoopTogetherAfterTwentyFiveSourceTicks() {
        for (int frame = 0; frame < 25; frame++) {
            assertEquals(frame, BattlefieldFlagState.animationFrame(frame));
            assertEquals(frame, BattlefieldFlagState.animationFrame(25L * 500 + frame));
        }
        assertEquals(24, BattlefieldFlagState.animationFrame(24));
        assertEquals(0, BattlefieldFlagState.animationFrame(25));
        assertEquals(0, BattlefieldFlagState.animationFrame(0));
        assertEquals(24, BattlefieldFlagState.animationFrame(-1));
        assertEquals(7, BattlefieldFlagState.animationFrame(Long.MAX_VALUE));
        assertEquals(17, BattlefieldFlagState.animationFrame(Long.MIN_VALUE));
    }

    @Test
    public void readingAndAnimatingFlagsDoesNotConsumeSimulationRandomnessOrChangeCredit() {
        CountingRandom random = new CountingRandom(35L);
        GameState game = new GameState(1, random, false);
        game.getPlayerState(1).alive = false;
        game.getPlayerState(1).lastAttackedByPlayerId = 2;
        int draws = random.draws;
        for (long tick = 0; tick < 250; tick++) {
            BattlefieldFlagState.animationFrame(tick);
            for (int island = 0; island < 5; island++) {
                BattlefieldFlagState.ownerPlayerId(game, island);
                BattlefieldFlagState.color(game, island);
                BattlefieldFlagState.religion(game, island);
            }
        }
        assertEquals(draws, random.draws);
        assertFalse(game.getPlayerState(1).alive);
        assertEquals(2, game.getPlayerState(1).getLastAttackedByPlayerId());
    }

    private static GameSetup setupWithOpponent(int leaderId) {
        GameSetup setup = new GameSetup();
        assertTrue(setup.toggleOpponent(leaderId));
        return setup;
    }

    private static void assertEmpty(GameState game, int island) {
        assertEquals(-1, BattlefieldFlagState.ownerPlayerId(game, island));
        assertEquals(0, BattlefieldFlagState.color(game, island));
        assertNull(BattlefieldFlagState.religion(game, island));
    }

    private static final class CountingRandom extends Random {
        int draws;

        CountingRandom(long seed) {
            super(seed);
        }

        @Override protected int next(int bits) {
            draws++;
            return super.next(bits);
        }
    }
}
