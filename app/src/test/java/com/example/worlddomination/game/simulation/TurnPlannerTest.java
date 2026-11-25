package com.example.worlddomination.game.simulation;

import com.example.worlddomination.game.ai.DecisionRequest;
import com.example.worlddomination.game.persistence.GameSaveCodec;
import com.example.worlddomination.game.simulation.GameState.Action;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TurnPlannerTest {
    @Test public void splitConfirmationWithFallbackKeepsCanonicalSourceRng() throws Exception {
        GameState baseline = new GameState(2, 771L);
        GameState split = new GameState(2, 771L);
        for (int turn = 0; turn < 8; turn++) {
            queue(baseline, Action.RESOURCES);
            queue(baseline, Action.MASS_MEDIA);
            queue(split, Action.RESOURCES);
            queue(split, Action.MASS_MEDIA);
            baseline.confirmTurn();
            split.beginOpponentPlanning();
            assertThrows(IllegalStateException.class, split::beginOpponentPlanning);
            assertThrows(java.io.IOException.class, () -> GameSaveCodec.encode(split));
            for (GameState.PlayerState actor : split.getPlayers()) {
                if (actor.id != 0 && actor.alive) {
                    assertFalse(DecisionRequest.capture(split, actor.id, "test").planIds.isEmpty());
                }
            }
            assertTrue(split.finishOpponentPlanning(Collections.emptyMap()).isEmpty());
            assertThrows(IllegalStateException.class,
                    () -> split.finishOpponentPlanning(Collections.emptyMap()));
            assertFalse(split.isOpponentPlanningPending());
            assertEquals(baseline.getMatchStatus(), split.getMatchStatus());
            if (baseline.getMatchStatus() != GameState.MatchStatus.ONGOING) break;
            assertArrayEquals(GameSaveCodec.encode(baseline), GameSaveCodec.encode(split));
        }
    }

    @Test public void changingPrivateRivalStateAndHumanOrdersLeavesActorRequestIdentical() {
        ReferenceRandom firstRandom = new ReferenceRandom(544L);
        ReferenceRandom secondRandom = new ReferenceRandom(544L);
        GameState original = new GameState(2, firstRandom);
        GameState altered = new GameState(2, secondRandom);
        altered.getPlayerState(2).moneyMillions = 7654321;
        altered.getPlayerState(2).religion = 1;
        altered.getPlayerState(2).getWeapons().get("soldier4").inStock = 973147;
        secondRandom.nextDouble();
        queue(original, Action.RESOURCES);
        queue(original, Action.MASS_MEDIA);
        queue(altered, Action.MASS_MEDIA);
        queue(altered, Action.MASS_MEDIA);
        original.beginOpponentPlanning();
        altered.beginOpponentPlanning();
        DecisionRequest request = DecisionRequest.capture(original, 1, "Test actor-only capture");
        assertEquals(request.user, DecisionRequest.capture(altered, 1,
                "Test actor-only capture").user);
        String id = request.planIds.get(0);
        assertNotNull(request.select("{\"plan_id\":\"" + id + "\"}"));
        assertNull(request.select("{\"plan_id\":\"" + id + "\",\"comment\":\"invalid\"}"));
        assertNull(request.select("{\"plan_id\":\"P99\"}"));
        assertFalse(altered.finishOpponentPlanning(Collections.singletonMap(1,
                request.selectId(id))).contains(1));
    }

    @Test public void completePlansCannotSpendNewIncomeOrUseNewlyPreparedCarrier() {
        GameState game = new GameState(2, 826L);
        GameState.PlayerState actor = game.getPlayerState(1);
        actor.moneyMillions = 10;
        queue(game, Action.RESOURCES);
        queue(game, Action.RESOURCES);
        game.beginOpponentPlanning();

        TurnPlan fundsAndResearch = new TurnPlan(game, actor.id,
                TurnPlan.Order.simple(Action.RESOURCES),
                TurnPlan.Order.research(Collections.singletonList("soldier3"), 15));
        assertFalse(TurnPlanner.isLegal(game, actor.id, fundsAndResearch));
        TurnPlan prepareAndLaunch = new TurnPlan(game, actor.id,
                TurnPlan.Order.prepare("rocket2"),
                TurnPlan.Order.targeted(Action.PREPARED_ATTACK, 0, 0, "rocket2", "warhead2", null));
        assertFalse(TurnPlanner.isLegal(game, actor.id, prepareAndLaunch));

        List<TurnPlan> candidates = TurnPlanner.candidates(game, actor.id, DecisionRequest.VERSION);
        assertTrue(candidates.size() >= 5);
        for (TurnPlan candidate : candidates) {
            assertEquals(2, candidate.orders.size());
            assertTrue(TurnPlanner.isLegal(game, actor.id, candidate));
        }
        TurnPlan selected = candidates.get(0);
        assertEquals(Collections.singleton(actor.id), game.finishOpponentPlanning(
                Collections.singletonMap(actor.id, selected)));
        for (int slot = 0; slot < 2; slot++) {
            assertEquals(selected.orders.get(slot).action, actor.getLastTurnActions().get(slot));
        }
    }

    @Test public void revalidationRejectsDoubleUseOfAStockWithOneUnit() {
        GameState game = new GameState(2, 931L);
        GameState.PlayerState actor = game.getPlayerState(1);
        actor.getWeapons().get("soldier2").inStock = 1;
        queue(game, Action.RESOURCES);
        queue(game, Action.MASS_MEDIA);
        game.beginOpponentPlanning();
        TurnPlan.Order attack = TurnPlan.Order.targeted(Action.ATTACK, 0, 0,
                "soldier2", null, null);
        TurnPlan illegal = new TurnPlan(game, actor.id, attack, attack);
        assertFalse(TurnPlanner.isLegal(game, actor.id, illegal));
        assertTrue(game.finishOpponentPlanning(Collections.singletonMap(actor.id, illegal)).isEmpty());
        assertEquals(1, game.getTurn());
    }

    private static void queue(GameState game, Action action) {
        game.selectAction(action);
        assertTrue(game.acceptSelectedAction());
    }
}
