package com.example.worlddomination.game.simulation;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Presenter_interface.btnPressed_accept accepts resources/religion/skip without a money gate. */
public final class NegativeBudgetActionAcceptanceTest {
    @Test public void resourcesAndMediaQueueAndCancelWithoutChangingNegativeOrZeroFunds() {
        for (int balance : new int[]{-140, 0}) {
            GameState game = gameWithBalance(balance);
            ReferenceRandom randomBefore = game.createPresentationRandomCopy();
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue("Resources at " + balance, game.acceptSelectedAction());
            game.selectAction(GameState.Action.MASS_MEDIA);
            assertTrue("Mass media at " + balance, game.acceptSelectedAction());
            assertEquals(GameState.Action.RESOURCES, game.getQueuedAction(0));
            assertEquals(GameState.Action.MASS_MEDIA, game.getQueuedAction(1));
            assertUnchangedFunds(game, balance);

            // A full pair remains full even when a third action is free.
            game.selectAction(GameState.Action.SKIP);
            assertFalse(game.acceptSelectedAction());
            assertEquals(2, game.getQueuedActionCount());
            game.removeQueuedAction(0);
            assertEquals(GameState.Action.MASS_MEDIA, game.getQueuedAction(1));
            assertUnchangedFunds(game, balance);
            game.removeQueuedAction(1);
            assertEquals(0, game.getQueuedActionCount());
            assertUnchangedFunds(game, balance);
            assertSameRandomStream(randomBefore, game);
        }
    }

    @Test public void skipQueuesAtNegativeBalanceAndClearingDoesNotCreateMoney() {
        GameState game = gameWithBalance(-140);
        ReferenceRandom randomBefore = game.createPresentationRandomCopy();
        game.selectAction(GameState.Action.SKIP);
        assertTrue(game.acceptSelectedAction());
        assertEquals(GameState.Action.SKIP, game.getQueuedAction(0));
        game.clearQueuedActions();
        assertEquals(0, game.getQueuedActionCount());
        assertUnchangedFunds(game, -140);
        assertSameRandomStream(randomBefore, game);
    }

    @Test public void validStockAttackQueuesWithoutMoneyOrPrematureStockConsumption() {
        GameState game = gameWithBalance(-140);
        ReferenceRandom randomBefore = game.createPresentationRandomCopy();
        int soldiersBefore = game.getHuman().getWeapons().get("soldier1").inStock;
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(1, 0, "soldier1"));
        assertTrue(game.acceptSelectedAction());
        assertEquals(GameState.Action.ATTACK, game.getQueuedAction(0));
        assertEquals(soldiersBefore, game.getHuman().getWeapons().get("soldier1").inStock);
        assertUnchangedFunds(game, -140);
        game.removeQueuedAction(0);
        assertEquals(soldiersBefore, game.getHuman().getWeapons().get("soldier1").inStock);
        assertUnchangedFunds(game, -140);
        assertSameRandomStream(randomBefore, game);
    }

    @Test public void emptyStockAndMissingResearchSelectionStillRejectAtNegativeBalance() {
        GameState game = gameWithBalance(-140);
        ReferenceRandom randomBefore = game.createPresentationRandomCopy();
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(1, 0, "soldier1"));
        game.getHuman().getWeapons().get("soldier1").inStock = 0;
        assertFalse(game.acceptSelectedAction());
        game.selectAction(GameState.Action.RESEARCH);
        assertFalse(game.acceptSelectedAction());
        assertEquals(0, game.getQueuedActionCount());
        assertUnchangedFunds(game, -140);
        assertSameRandomStream(randomBefore, game);
    }

    @Test public void contaminationStillRejectsFreeActionAtAcceptance() throws Exception {
        GameState game = gameWithBalance(-140);
        game.selectAction(GameState.Action.RESOURCES);
        Field field = GameState.PlayerState.class.getDeclaredField("bioloadedActions");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<GameState.Action> contaminated = (Set<GameState.Action>) field.get(game.getHuman());
        contaminated.add(GameState.Action.RESOURCES);
        assertFalse(game.acceptSelectedAction());
        assertEquals(0, game.getQueuedActionCount());
        assertUnchangedFunds(game, -140);
    }

    @Test public void paidResearchAndDiplomacyRemainUnaffordable() {
        for (int balance : new int[]{-140, 0}) {
            GameState game = gameWithBalance(balance);
            ReferenceRandom randomBefore = game.createPresentationRandomCopy();
            game.selectAction(GameState.Action.RESEARCH);
            assertFalse(game.selectResearchTechnology("soldier2"));
            assertFalse(game.acceptSelectedAction());
            game.selectAction(GameState.Action.DIPLOMACY);
            assertTrue(game.selectDiplomacyOption(0));
            assertFalse(game.canAcceptDiplomacy());
            assertFalse(game.acceptSelectedAction());
            assertEquals(0, game.getQueuedActionCount());
            assertUnchangedFunds(game, balance);
            assertSameRandomStream(randomBefore, game);
        }

        // A choice made while affordable must also be checked again at acceptance.
        GameState game = gameWithBalance(1000);
        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology("soldier2"));
        game.getHuman().moneyMillions = -140;
        game.getHuman().turnMoneyMillions = -140;
        assertFalse(game.acceptSelectedAction());
        assertEquals(0, game.getQueuedActionCount());
        assertUnchangedFunds(game, -140);
    }

    private static GameState gameWithBalance(int balance) {
        GameState game = new GameState(1, 0L, false);
        // Isolate the acceptance predicate; -140 is an observed legal Hard/seed-0
        // turn-17 balance, not a generated turn-history fixture.
        game.getHuman().moneyMillions = balance;
        game.getHuman().turnMoneyMillions = balance;
        return game;
    }

    private static void assertUnchangedFunds(GameState game, int balance) {
        assertEquals(balance, game.getHuman().moneyMillions);
        assertEquals(balance, game.getTurnMoneyMillions());
    }

    private static void assertSameRandomStream(ReferenceRandom before, GameState game) {
        ReferenceRandom after = game.createPresentationRandomCopy();
        for (int draw = 0; draw < 32; draw++) {
            assertEquals("Acceptance/cancellation RNG draw " + draw,
                    before.nextDouble(), after.nextDouble(), 0.0);
        }
    }
}
