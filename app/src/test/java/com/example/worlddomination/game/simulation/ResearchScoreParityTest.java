package com.example.worlddomination.game.simulation;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Research scoring follows Presenter.updatePlayerVars_2 even when payment fails. */
public final class ResearchScoreParityTest {
    @Test
    public void lostFundsStillScoreAndConsumeTheResearchRandomDraw() {
        ReferenceRandom failedRandom = new ReferenceRandom(826L);
        ReferenceRandom paidRandom = new ReferenceRandom(826L);
        GameState failed = queuedResearch(failedRandom);
        GameState paid = queuedResearch(paidRandom);

        // Spy transfers run before research results; reservations only reduce
        // turnMoneyMillions, so the queued purchase can lose its live funds.
        failed.getHuman().moneyMillions = 9;
        failed.confirmTurn();
        paid.confirmTurn();

        assertFalse(failed.getHuman().hasTechnology("soldier2"));
        assertEquals(9, failed.getHuman().moneyMillions);
        assertTrue(paid.getHuman().hasTechnology("soldier2"));
        assertEquals(0, paid.getHuman().moneyMillions);
        assertTrue(failed.getHuman().score > 0);
        assertEquals(paid.getHuman().score, failed.getHuman().score);
        assertEquals(paidRandom.nextDouble(), failedRandom.nextDouble(), 0.0);
    }

    @Test
    public void firstPurchaseCanExhaustLiveFundsWithoutSuppressingSecondScore() {
        ReferenceRandom failedRandom = new ReferenceRandom(826L);
        ReferenceRandom paidRandom = new ReferenceRandom(826L);
        GameState failed = queuedTwoResearches(failedRandom);
        GameState paid = queuedTwoResearches(paidRandom);

        failed.getHuman().moneyMillions = 10;
        failed.confirmTurn();
        paid.confirmTurn();

        assertTrue(failed.getHuman().hasTechnology("soldier2"));
        assertFalse(failed.getHuman().hasTechnology("rocket2"));
        assertEquals(0, failed.getHuman().moneyMillions);
        assertTrue(paid.getHuman().hasTechnology("soldier2"));
        assertTrue(paid.getHuman().hasTechnology("rocket2"));
        assertEquals(0, paid.getHuman().moneyMillions);
        assertTrue(failed.getHuman().score > 6000);
        assertEquals(paid.getHuman().score, failed.getHuman().score);
        assertEquals(paidRandom.nextDouble(), failedRandom.nextDouble(), 0.0);
    }

    @Test
    public void singleResearchResultMatchesSourceHandlerScoreAndRng() throws Exception {
        assertHandlerResult(queuedResearch(new ReferenceRandom(826L)), 9,
                false, false, 6060, 0.6379299778542493);
        assertHandlerResult(queuedResearch(new ReferenceRandom(826L)), 10,
                true, false, 6060, 0.6379299778542493);
    }

    @Test
    public void twoResearchResultsMatchSourceHandlerScoreAndRng() throws Exception {
        assertHandlerResult(queuedTwoResearches(new ReferenceRandom(826L)), 10,
                true, false, 12128, 0.4778696846611814);
        assertHandlerResult(queuedTwoResearches(new ReferenceRandom(826L)), 20,
                true, true, 12128, 0.4778696846611814);
    }

    private static void assertHandlerResult(GameState state, int liveMoney,
                                            boolean soldier2, boolean rocket2,
                                            int sourceScore, double sourceNextRandom) throws Exception {
        state.getHuman().moneyMillions = liveMoney;
        Field ordersField = GameState.class.getDeclaredField("queuedActions");
        ordersField.setAccessible(true);
        List<?> orders = (List<?>) ordersField.get(state);
        Method resolveResearch = GameState.class.getDeclaredMethod(
                "resolveResearch", orders.get(0).getClass());
        resolveResearch.setAccessible(true);
        // Presenter.updatePlayerVars_2 was probed with only its research result
        // records and seed 826 immediately before the handler call.
        for (Object order : orders) {
            if (isResearch(order)) {
                resolveResearch.invoke(state, order);
            }
        }
        assertEquals(soldier2, state.getHuman().hasTechnology("soldier2"));
        assertEquals(rocket2, state.getHuman().hasTechnology("rocket2"));
        assertEquals(sourceScore, state.getHuman().score);
        assertEquals(sourceNextRandom, state.createPresentationRandomCopy().nextDouble(), 0.0);
    }

    private static boolean isResearch(Object order) throws Exception {
        Field actionField = order.getClass().getDeclaredField("action");
        actionField.setAccessible(true);
        return actionField.get(order) == GameState.Action.RESEARCH;
    }

    private static GameState queuedResearch(ReferenceRandom random) {
        GameState state = new GameState(1, random, false);
        state.getHuman().moneyMillions = 10;
        state.getHuman().turnMoneyMillions = 10;
        state.selectAction(GameState.Action.RESEARCH);
        assertTrue(state.selectResearchTechnology("soldier2"));
        assertTrue(state.acceptSelectedAction());
        assertEquals(0, state.getTurnMoneyMillions());
        state.selectAction(GameState.Action.SKIP);
        assertTrue(state.acceptSelectedAction());
        // The handler parity tests invoke research immediately after this reset.
        random.setSeed(826L);
        return state;
    }

    private static GameState queuedTwoResearches(ReferenceRandom random) {
        GameState state = new GameState(1, random, false);
        state.getHuman().moneyMillions = 20;
        state.getHuman().turnMoneyMillions = 20;
        state.selectAction(GameState.Action.RESEARCH);
        assertTrue(state.selectResearchTechnology("soldier2"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESEARCH);
        assertTrue(state.selectResearchTechnology("rocket2"));
        assertTrue(state.acceptSelectedAction());
        assertEquals(0, state.getTurnMoneyMillions());
        random.setSeed(826L);
        return state;
    }
}
