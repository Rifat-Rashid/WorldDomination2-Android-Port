package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.TechnologyCatalog;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class OpponentAITest {
    @Test
    public void researchTurnBonusStopsGrowingAtTheSourceSixtyTurnCap() {
        assertEquals(0.0, OpponentAI.researchTurnRatio(0, 5), 0.0);
        assertEquals(54.0, OpponentAI.researchTurnRatio(30, 5), 0.0);
        assertEquals(108.0, OpponentAI.researchTurnRatio(60, 5), 0.0);
        assertEquals(108.0, OpponentAI.researchTurnRatio(120, 5), 0.0);
        assertEquals(54.0, OpponentAI.researchTurnRatio(120, 10), 0.0);
    }

    @Test
    public void plannerFallsThroughUnavailableTopActionsToFillBothSlots() throws Exception {
        GameState state = gameWithRegularFirstOpponent(7401L);
        GameState.PlayerState actor = state.getPlayerState(1);
        actor.moneyMillions = 0;
        actor.turnMoneyMillions = 0;
        actor.religion = 100;
        for (GameState.WeaponState weapon : actor.getWeapons().values()) {
            weapon.inStock = 0;
        }
        setDesiredStock(actor, 0);
        setCeasefireTurns(actor, 3);
        assertNull(OpponentAI.planDiplomacy(state, actor, new SequenceRandom(80, 0)));
        assertNull(OpponentAI.planAction(state, actor, GameState.Action.PROPAGANDA,
                new SequenceRandom(0), 1, Collections.<String, Integer>emptyMap()));
        assertNull(OpponentAI.planAction(state, actor, GameState.Action.RESEARCH,
                new SequenceRandom(0), 1, Collections.<String, Integer>emptyMap()));
        assertNull(OpponentAI.planAction(state, actor, GameState.Action.ATTACK,
                new SequenceRandom(0, 0), 1, Collections.<String, Integer>emptyMap()));
        assertNull(OpponentAI.planAction(state, actor, GameState.Action.DEFENSE,
                new SequenceRandom(0), 1, Collections.<String, Integer>emptyMap()));

        List<OpponentAI.Decision> decisions = OpponentAI.plan(state, actor,
                new SequenceRandom(80, 0));

        assertEquals("unavailable diplomacy, propaganda and research must not consume slots",
                2, decisions.size());
        for (OpponentAI.Decision decision : decisions) {
            assertTrue(decision.action == GameState.Action.PRODUCTION
                    || decision.action == GameState.Action.RESOURCES
                    || decision.action == GameState.Action.MASS_MEDIA);
        }
    }

    @Test
    public void turnPlannerScoresEveryOpponentBeforePlanningAnyOpponent() {
        long seed = firstOrderSensitiveSeed();
        assertTrue("fixture search must find a turn where interleaving changes AI plans",
                seed >= 0L);

        CountingRandom actualRandom = new CountingRandom(seed);
        GameState actualState = new GameState(1, actualRandom);
        actualRandom.resetDrawCount();
        List<OpponentAI.Decision> actual = OpponentAI.planTurn(actualState, actualRandom);

        CountingRandom referenceRandom = new CountingRandom(seed);
        GameState referenceState = new GameState(1, referenceRandom);
        referenceRandom.resetDrawCount();
        List<GameState.PlayerState> opponents = new ArrayList<>();
        List<List<OpponentAI.ScoredAction>> rankedByOpponent = new ArrayList<>();
        for (GameState.PlayerState player : referenceState.getPlayers()) {
            if (player.human || !player.alive) {
                continue;
            }
            opponents.add(player);
            List<OpponentAI.ScoredAction> scores = OpponentAI.scoreActions(
                    referenceState, player, referenceRandom);
            scores.sort(Comparator.comparingDouble(
                    (OpponentAI.ScoredAction scored) -> scored.score).reversed());
            rankedByOpponent.add(scores);
        }
        List<OpponentAI.Decision> twoPassReference = new ArrayList<>();
        for (int index = 0; index < opponents.size(); index++) {
            twoPassReference.addAll(OpponentAI.planScoredActions(referenceState,
                    opponents.get(index), referenceRandom, rankedByOpponent.get(index)));
        }

        CountingRandom interleavedRandom = new CountingRandom(seed);
        GameState interleavedState = new GameState(1, interleavedRandom);
        interleavedRandom.resetDrawCount();
        List<OpponentAI.Decision> interleaved = new ArrayList<>();
        for (GameState.PlayerState player : interleavedState.getPlayers()) {
            if (!player.human && player.alive) {
                interleaved.addAll(OpponentAI.plan(interleavedState, player,
                        interleavedRandom));
            }
        }

        assertEquals("the turn planner matches source score-all, then target-all ordering",
                summarize(twoPassReference), summarize(actual));
        assertEquals(referenceRandom.getDrawCount(), actualRandom.getDrawCount());
        assertFalse("the fixture exposes the legacy score/target interleaving",
                summarize(twoPassReference).equals(summarize(interleaved)));
    }

    private static long firstOrderSensitiveSeed() {
        for (long seed = 0; seed < 512; seed++) {
            CountingRandom sourceOrderRandom = new CountingRandom(seed);
            GameState sourceOrderState = new GameState(1, sourceOrderRandom);
            sourceOrderRandom.resetDrawCount();
            List<OpponentAI.Decision> sourceOrder = OpponentAI.planTurn(
                    sourceOrderState, sourceOrderRandom);

            CountingRandom interleavedRandom = new CountingRandom(seed);
            GameState interleavedState = new GameState(1, interleavedRandom);
            interleavedRandom.resetDrawCount();
            List<OpponentAI.Decision> interleaved = new ArrayList<>();
            for (GameState.PlayerState player : interleavedState.getPlayers()) {
                if (!player.human && player.alive) {
                    interleaved.addAll(OpponentAI.plan(interleavedState, player,
                            interleavedRandom));
                }
            }
            if (!summarize(sourceOrder).equals(summarize(interleaved))) {
                return seed;
            }
        }
        return -1L;
    }

    @Test
    public void resourcesActionRequiresFundsBelowTheFlashThreshold() {
        GameState state = new GameState(1, 121L);
        GameState.PlayerState actor = state.getPlayerState(1);
        actor.moneyMillions = 149;

        assertNotNull(OpponentAI.planAction(state, actor, GameState.Action.RESOURCES,
                new Random(1L), 0, Collections.<String, Integer>emptyMap()));

        actor.moneyMillions = 150;
        assertNull(OpponentAI.planAction(state, actor, GameState.Action.RESOURCES,
                new Random(1L), 0, Collections.<String, Integer>emptyMap()));
    }

    @Test
    public void diplomacyDoesNotSkipPoorestRivalForARicherRival() {
        GameState state = findAiWithNeutralHumanRegard();
        GameState.PlayerState actor = aiWithNeutralHumanRegard(state);
        GameState.PlayerState poorest = anotherRival(state, actor.id, -1);
        GameState.PlayerState richer = anotherRival(state, actor.id, poorest.id);
        configureDiplomacyPopulationAndFunds(state, actor, poorest, richer);
        poorest.moneyMillions = 50;
        richer.moneyMillions = 200;

        assertNull(OpponentAI.planDiplomacy(state, actor, new Random(81L)));
    }

    @Test
    public void diplomacyDemandsFromThePoorestRivalWhenThatRivalHasMoreFunds() {
        GameState state = findAiWithNeutralHumanRegard();
        GameState.PlayerState actor = aiWithNeutralHumanRegard(state);
        GameState.PlayerState poorest = anotherRival(state, actor.id, -1);
        GameState.PlayerState richer = anotherRival(state, actor.id, poorest.id);
        configureDiplomacyPopulationAndFunds(state, actor, poorest, richer);
        poorest.moneyMillions = 150;
        richer.moneyMillions = 200;

        OpponentAI.Decision decision = OpponentAI.planDiplomacy(state, actor,
                new Random(82L));
        assertNotNull(decision);
        assertEquals(GameState.Action.DIPLOMACY, decision.action);
        assertEquals(poorest.id, decision.targetPlayerId);
        assertEquals(5, decision.diplomacyOption);
    }

    @Test
    public void aiDiplomacyOfferChecksTurnBudgetWithoutReservingIt() throws Exception {
        GameState state = findAiWithNeutralHumanRegard();
        GameState.PlayerState actor = aiWithNeutralHumanRegard(state);
        actor.moneyMillions = 100;
        actor.turnMoneyMillions = 100;
        actor.populationMillions = 1_000;
        setRegardToward(actor, 0, 51);
        state.getHuman().moneyMillions = 100;

        OpponentAI.Decision decision = OpponentAI.planDiplomacy(state, actor,
                new SequenceRandom(0, 0, 0));

        assertNotNull(decision);
        assertEquals(GameState.Action.DIPLOMACY, decision.action);
        assertEquals(state.getHuman().id, decision.targetPlayerId);
        assertEquals(2, decision.diplomacyOption);
        assertEquals("The Flash planner checks offers against turn money but does not reserve it",
                0, decision.reservedMillions);
        assertEquals(100, actor.turnMoneyMillions);
    }

    @Test
    public void diplomacyHumanDemandOverrideUsesStrictFundsAndRegardThresholds()
            throws Exception {
        GameState state = findAiWithNeutralHumanRegard();
        GameState.PlayerState actor = aiWithNeutralHumanRegard(state);
        GameState.PlayerState poorest = anotherRival(state, actor.id, -1);
        GameState.PlayerState richer = anotherRival(state, actor.id, poorest.id);
        configureDiplomacyPopulationAndFunds(state, actor, poorest, richer);
        state.getHuman().moneyMillions = 51;
        setRegardToward(actor, state.getHuman().id, 49);

        OpponentAI.Decision decision = OpponentAI.planDiplomacy(state, actor,
                new Random(83L));

        assertNotNull(decision);
        assertEquals(GameState.Action.DIPLOMACY, decision.action);
        assertEquals("the human override demands from a target with over $50M",
                state.getHuman().id, decision.targetPlayerId);
        assertEquals("strictly affordable demand options select the greatest amount",
                4, decision.diplomacyOption);

        GameState regardBoundary = findAiWithNeutralHumanRegard();
        GameState.PlayerState boundaryActor = aiWithNeutralHumanRegard(regardBoundary);
        GameState.PlayerState boundaryPoorest = anotherRival(regardBoundary,
                boundaryActor.id, -1);
        GameState.PlayerState boundaryRicher = anotherRival(regardBoundary,
                boundaryActor.id, boundaryPoorest.id);
        configureDiplomacyPopulationAndFunds(regardBoundary, boundaryActor,
                boundaryPoorest, boundaryRicher);
        regardBoundary.getHuman().moneyMillions = 51;
        setRegardToward(boundaryActor, regardBoundary.getHuman().id, 50);

        assertNull("regard exactly 50 does not trigger the demand override",
                OpponentAI.planDiplomacy(regardBoundary, boundaryActor,
                        new Random(84L)));

        GameState fundsBoundary = findAiWithNeutralHumanRegard();
        GameState.PlayerState fundsActor = aiWithNeutralHumanRegard(fundsBoundary);
        GameState.PlayerState fundsPoorest = anotherRival(fundsBoundary,
                fundsActor.id, -1);
        GameState.PlayerState fundsRicher = anotherRival(fundsBoundary,
                fundsActor.id, fundsPoorest.id);
        configureDiplomacyPopulationAndFunds(fundsBoundary, fundsActor,
                fundsPoorest, fundsRicher);
        fundsBoundary.getHuman().moneyMillions = 50;
        setRegardToward(fundsActor, fundsBoundary.getHuman().id, 49);

        assertNull("human funds exactly $50M do not trigger the demand override",
                OpponentAI.planDiplomacy(fundsBoundary, fundsActor,
                        new Random(85L)));
    }

    @Test
    public void diplomacyHumanOfferOverrideUsesStrictCashAndBudgetThresholds()
            throws Exception {
        GameState state = findAiWithNeutralHumanRegard();
        GameState.PlayerState actor = aiWithNeutralHumanRegard(state);
        GameState.PlayerState poorest = anotherRival(state, actor.id, -1);
        GameState.PlayerState richer = anotherRival(state, actor.id, poorest.id);
        configureDiplomacyPopulationAndFunds(state, actor, poorest, richer);
        actor.moneyMillions = 51;
        actor.turnMoneyMillions = 50;
        setRegardToward(actor, state.getHuman().id, 51);

        OpponentAI.Decision decision = OpponentAI.planDiplomacy(state, actor,
                new Random(86L));

        assertNotNull(decision);
        assertEquals(state.getHuman().id, decision.targetPlayerId);
        assertEquals("an offer amount equal to the budget is not eligible",
                0, decision.diplomacyOption);

        actor.turnMoneyMillions = 51;
        decision = OpponentAI.planDiplomacy(state, actor, new Random(87L));
        assertNotNull(decision);
        assertEquals("the next offer becomes affordable only above its amount",
                1, decision.diplomacyOption);

        actor.turnMoneyMillions = 10;
        assertNull("an offer amount equal to the full budget is rejected",
                OpponentAI.planDiplomacy(state, actor, new Random(88L)));

        actor.moneyMillions = 50;
        actor.turnMoneyMillions = 100;
        assertNull("cash exactly $50M does not trigger the human offer override",
                OpponentAI.planDiplomacy(state, actor, new Random(89L)));
    }

    @Test
    public void diplomacyTargetScoreUsesStrictGreaterAndKeepsFirstRivalOnTies() {
        GameState state = findAiWithNeutralHumanRegard();
        GameState.PlayerState actor = aiWithNeutralHumanRegard(state);
        actor.populationMillions = 1;
        actor.moneyMillions = 100;
        actor.turnMoneyMillions = 100;
        state.getHuman().moneyMillions = 50;

        List<Integer> candidateIds = new ArrayList<>();
        for (GameState.PlayerState target : state.getPlayers()) {
            if (target.id == actor.id || !target.alive
                    || actor.hasCeasefireWith(target.id)) {
                continue;
            }
            target.populationMillions = target.id == 0 ? 2 : 3;
            target.moneyMillions = 50;
            candidateIds.add(target.id);
        }
        assertTrue("a living rival is required for the source score scan",
                candidateIds.size() >= 2);

        int[] scoreRolls = new int[candidateIds.size()];
        Arrays.fill(scoreRolls, 2); // Source randRange(1, 5) produces score 3.
        scoreRolls[scoreRolls.length - 1] = 3; // A strictly higher score replaces the winner.
        SequenceRandom random = new SequenceRandom(scoreRolls);

        OpponentAI.Decision decision = OpponentAI.planDiplomacy(state, actor, random);

        assertNotNull(decision);
        assertEquals(GameState.Action.DIPLOMACY, decision.action);
        assertEquals("equal scores keep the earlier rival; only a greater score replaces it",
                candidateIds.get(candidateIds.size() - 1).intValue(), decision.targetPlayerId);
        assertEquals(2, decision.diplomacyOption);
        assertEquals(candidateIds.size(), random.getDrawCount());
    }

    @Test
    public void diplomacyCooldownTargetsDoNotConsumeRandomCandidateDraws() {
        GameState state = findAiWithNeutralHumanRegard();
        GameState.PlayerState actor = aiWithNeutralHumanRegard(state);
        actor.populationMillions = 2_000;
        actor.moneyMillions = 200;
        actor.turnMoneyMillions = 100;
        state.getHuman().populationMillions = 500;
        state.getHuman().moneyMillions = 50;
        GameState.PlayerState cooldownTarget = anotherRival(state, actor.id, -1);
        for (GameState.PlayerState target : state.getPlayers()) {
            if (target.id != actor.id && target.alive) {
                target.populationMillions = 500;
                target.moneyMillions = 50;
            }
        }
        assertTrue(state.submitDiplomacyProposal(actor.id, cooldownTarget.id, 0));

        int expectedDraws = 0;
        for (GameState.PlayerState target : state.getPlayers()) {
            if (target.id != actor.id && target.alive
                    && !actor.hasCeasefireWith(target.id)
                    && actor.getDiplomacyOfferedLastTurn(target.id) == 0) {
                expectedDraws++;
            }
        }
        SequenceRandom random = new SequenceRandom(0, 1, 2, 3);

        assertNull(OpponentAI.planDiplomacy(state, actor, random));
        assertEquals("the source skips a random draw for each cooling-down target",
                expectedDraws, random.getDrawCount());
        assertEquals(2, actor.getDiplomacyOfferedLastTurn(cooldownTarget.id));
    }

    @Test
    public void spyTargetsTheRivalWithRequiredPerLevelInventory() {
        GameState state = prepareSpyTargetScenario(false);
        GameState.PlayerState attacker = state.getPlayerState(1);

        OpponentAI.Decision decision = OpponentAI.planAttack(state, attacker,
                new SequenceRandom(0, 0), Collections.<String, Integer>emptyMap());

        assertNotNull(decision);
        assertEquals("spy4", decision.weaponName);
        assertEquals(4, decision.targetPlayerId);
        assertTrue(OpponentAI.mostHostileTarget(state, attacker, false).id != 4);
    }

    @Test
    public void spyLevelTargetingUsesPerLevelStockEvenWhenThatLevelIsInactive() {
        GameState state = prepareSpyTargetScenario(false);
        GameState.PlayerState attacker = state.getPlayerState(1);
        GameState.WeaponState activeSpy = attacker.getWeapons().get("spy1");
        activeSpy.active = true;
        activeSpy.inStock = 1;
        attacker.getWeapons().get("spy4").active = false;
        attacker.getWeapons().get("spy4").inStock = 1;

        OpponentAI.Decision decision = OpponentAI.planAttack(state, attacker,
                new SequenceRandom(0, 0), Collections.<String, Integer>emptyMap());

        assertNotNull(decision);
        assertEquals("spy4", decision.weaponName);
        assertEquals(4, decision.targetPlayerId);
    }

    @Test
    public void highHumanFundsOverrideChoosesTheHighestAvailableSpyAgainstHuman() {
        GameState state = prepareSpyTargetScenario(false);
        GameState.PlayerState attacker = state.getPlayerState(1);
        attacker.spyReadiness = 0;
        attacker.getWeapons().get("spy2").active = true;
        attacker.getWeapons().get("spy2").inStock = 1;
        state.getHuman().moneyMillions = 10_000;

        OpponentAI.Decision decision = OpponentAI.planAttack(state, attacker,
                new SequenceRandom(0, 0), Collections.<String, Integer>emptyMap());

        assertNotNull(decision);
        assertEquals("spy4", decision.weaponName);
        assertEquals(0, decision.targetPlayerId);
    }

    @Test
    public void spyReplacesConventionalAttackOnlyOnSourceOneInSevenRoll() {
        GameState spyState = prepareSpyTargetScenario(true);
        SequenceRandom spyRandom = new SequenceRandom(0, 0, 3, 0, 4);
        OpponentAI.Decision spyDecision = OpponentAI.planAttack(spyState,
                spyState.getPlayerState(1), spyRandom,
                Collections.<String, Integer>emptyMap());
        assertNotNull(spyDecision);
        assertEquals("spy4", spyDecision.weaponName);
        assertEquals(4, spyDecision.targetPlayerId);
        assertEquals("factory", spyDecision.spyBuilding);
        assertEquals(5, spyRandom.getDrawCount());

        GameState soldierState = prepareSpyTargetScenario(true);
        OpponentAI.Decision soldierDecision = OpponentAI.planAttack(soldierState,
                soldierState.getPlayerState(1), new SequenceRandom(0, 0, 2, 0),
                Collections.<String, Integer>emptyMap());
        assertNotNull(soldierDecision);
        assertEquals("soldier1", soldierDecision.weaponName);
    }

    @Test
    public void directAttackSelectsSoldierBySourceRandomIndexInSortedInventory() {
        GameState strongestState = prepareSoldierSelectionScenario();
        OpponentAI.Decision strongest = OpponentAI.planAttack(strongestState,
                strongestState.getPlayerState(1), new SequenceRandom(0),
                Collections.<String, Integer>emptyMap());
        assertNotNull(strongest);
        assertEquals("soldier4", strongest.weaponName);

        GameState weakestState = prepareSoldierSelectionScenario();
        OpponentAI.Decision weakest = OpponentAI.planAttack(weakestState,
                weakestState.getPlayerState(1), new SequenceRandom(3),
                Collections.<String, Integer>emptyMap());
        assertNotNull(weakest);
        assertEquals("soldier1", weakest.weaponName);
    }

    @Test
    public void specialOpponentUsesItsPrimaryEnemyToBreakEqualHostilityTies() {
        GameState state = null;
        for (long seed = 0; seed < 500; seed++) {
            GameState candidate = new GameState(1, seed);
            if (candidate.getPlayerState(1).isSpecialPerson()) {
                state = candidate;
                break;
            }
        }
        assertNotNull("seed sweep should find a special first opponent", state);

        GameState.PlayerState special = state.getPlayerState(1);
        assertEquals(70, special.getRegardToward(0));
        assertEquals(special.getPrimaryEnemyId(),
                OpponentAI.mostHostileTarget(state, special, false).id);
    }

    @Test
    public void attackTargetUsesSourcePopulationFallbackWhenHostilityIsZero() throws Exception {
        GameState state = new GameState(1, 912L);
        GameState.PlayerState attacker = state.getPlayerState(1);
        setFriendlyRelationsAndNoPrimaryEnemy(attacker);

        GameState.PlayerState target = OpponentAI.attackTarget(state, attacker,
                new SequenceRandom(0));

        assertNotNull(target);
        // The Flash loop indexes activePlayerArr with the filtered rival-array index.
        assertEquals(3, target.id);
    }

    @Test
    public void attackTargetUsesSourceRandomFallbackWhenPopulationFallbackHasNoMatch()
            throws Exception {
        GameState state = new GameState(1, 913L);
        GameState.PlayerState attacker = state.getPlayerState(1);
        setFriendlyRelationsAndNoPrimaryEnemy(attacker);
        state.getPlayerState(0).populationMillions = 0;
        state.getPlayerState(2).populationMillions = 0;
        state.getPlayerState(3).populationMillions = 0;

        GameState.PlayerState target = OpponentAI.attackTarget(state, attacker,
                new SequenceRandom(3));

        assertNotNull(target);
        assertEquals(4, target.id);
    }

    @Test
    public void attackScoreRetainsLowestRegardForAnEliminatedRival() throws Exception {
        GameState state = new GameState(1, 915L);
        GameState.PlayerState actor = state.getPlayerState(1);
        setFriendlyRelationsAndNoPrimaryEnemy(actor);
        setRegardToward(actor, 4, 10);
        state.getPlayerState(4).alive = false;

        assertEquals(75.0, OpponentAI.baseAttackScore(state, actor), 0.0);
    }

    @Test
    public void attackScoreRetainsFractionalRegardPrecision() throws Exception {
        GameState state = new GameState(1, 916L);
        GameState.PlayerState actor = state.getPlayerState(1);
        setFriendlyRelationsAndNoPrimaryEnemy(actor);
        setRegardToward(actor, 4, 40.5);

        assertEquals(59.75, OpponentAI.baseAttackScore(state, actor), 0.0);
    }

    @Test
    public void attackScoreUsesSourceDecisionPointBasesForEveryDifficulty() throws Exception {
        double[] expectedBases = {86.0, 80.0, 86.0};
        for (int difficulty = 0; difficulty < expectedBases.length; difficulty++) {
            GameState state = gameWithRegularFirstOpponent(
                    difficulty, 9160L + difficulty * 500L);
            GameState.PlayerState actor = state.getPlayerState(1);
            setFriendlyRelationsAndNoPrimaryEnemy(actor);
            for (GameState.WeaponState weapon : actor.getWeapons().values()) {
                weapon.inStock = 0;
            }
            GameState.WeaponState soldier = actor.getWeapons().get("soldier1");
            soldier.active = true;
            soldier.inStock = 1;

            double expectedAttackScore = expectedBases[difficulty] - 50.0;
            assertEquals("source attack base at difficulty " + difficulty,
                    expectedAttackScore,
                    OpponentAI.baseAttackScore(state, actor), 0.0);
            assertEquals("ranked attack action at difficulty " + difficulty,
                    expectedAttackScore,
                    OpponentAI.scoreForAction(state, actor,
                            new Random(9160L + difficulty), GameState.Action.ATTACK), 0.0);
        }
    }

    @Test
    public void attackScoreAvailabilityCountsAnyActiveSpyStockBeforeReadiness() {
        GameState state = new GameState(1, 916L);
        GameState.PlayerState actor = state.getPlayerState(1);
        actor.spyReadiness = 0;
        for (GameState.WeaponState weapon : actor.getWeapons().values()) {
            weapon.inStock = 0;
        }
        GameState.WeaponState spy = actor.getWeapons().get("spy4");
        spy.active = true;
        spy.inStock = 1;

        assertTrue(OpponentAI.hasAvailableAttackForScoring(state, actor));

        spy.active = false;
        assertFalse(OpponentAI.hasAvailableAttackForScoring(state, actor));
    }

    @Test
    public void attackScoreAvailabilityIncludesPayloadForAStockedCarrier() {
        GameState state = new GameState(1, 917L);
        GameState.PlayerState actor = state.getPlayerState(1);
        for (GameState.WeaponState weapon : actor.getWeapons().values()) {
            weapon.inStock = 0;
        }
        GameState.WeaponState carrier = actor.getWeapons().get("rocket1");
        carrier.active = true;
        carrier.inStock = 1;
        GameState.WeaponState payload = actor.getWeapons().get("warhead1");
        payload.active = true;
        payload.inStock = 1;

        assertTrue(state.getAvailablePreparedCarriers(actor.id).contains("rocket1"));
        assertTrue(OpponentAI.hasAvailableAttackForScoring(state, actor));

        OpponentAI.Decision decision = OpponentAI.planAction(state, actor,
                GameState.Action.ATTACK, new Random(917L), 1,
                Collections.<String, Integer>emptyMap());
        assertNotNull(decision);
        assertEquals(GameState.Action.PREPARED_ATTACK, decision.action);
        assertTrue(decision.prepareCarrier);
        assertEquals("rocket1", decision.weaponName);
    }

    @Test
    public void attackScoreAvailabilityRequiresSourceWeaponsOrReadySpy() {
        GameState state = new GameState(1, 9171L);
        GameState.PlayerState actor = state.getPlayerState(1);
        for (GameState.WeaponState weapon : actor.getWeapons().values()) {
            weapon.inStock = 0;
        }
        GameState.WeaponState carrier = actor.getWeapons().get("rocket1");
        carrier.active = true;
        carrier.inStock = 1;

        assertFalse("carrier stock alone does not populate availableWeaponsArr",
                OpponentAI.hasAvailableAttackForScoring(state, actor));
    }

    @Test
    public void activePayloadUsesSourceCarrierStockRulesForAttackEligibility() {
        GameState state = new GameState(1, 9172L);
        GameState.PlayerState actor = state.getPlayerState(1);
        for (GameState.WeaponState weapon : actor.getWeapons().values()) {
            weapon.inStock = 0;
        }
        GameState.WeaponState payload = actor.getWeapons().get("warhead1");
        payload.active = true;
        payload.inStock = 1;
        GameState.WeaponState rocket = actor.getWeapons().get("rocket1");
        rocket.active = false;
        rocket.inStock = 1;

        assertTrue("the source does not require the matching carrier to be active",
                OpponentAI.hasAvailableAttackForScoring(state, actor));
        rocket.inStock = 0;
        assertFalse("payload without any stocked compatible carrier is unavailable",
                OpponentAI.hasAvailableAttackForScoring(state, actor));

        GameState.WeaponState bomber = actor.getWeapons().get("plane2");
        bomber.active = false;
        bomber.inStock = 1;
        assertTrue(OpponentAI.hasAvailableAttackForScoring(state, actor));
    }

    @Test
    public void stockLevelCountsResearchedWeaponsThatAreTemporarilyInactive()
            throws Exception {
        GameState state = new GameState(1, 922L);
        GameState.PlayerState actor = state.getPlayerState(1);
        java.lang.reflect.Field desireField = GameState.PlayerState.class
                .getDeclaredField("desiredStock");
        desireField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Integer> desired = (Map<String, Integer>) desireField.get(actor);
        for (GameState.WeaponState weapon : actor.getWeapons().values()) {
            weapon.active = false;
            weapon.inStock = 0;
            if (weapon.definition.productionGroup != 8) {
                desired.put(weapon.definition.name, 1);
            }
        }

        int researchedWeaponCount = 0;
        GameState.WeaponState researchedWeapon = null;
        for (GameState.WeaponState weapon : actor.getWeapons().values()) {
            if (weapon.definition.productionGroup != 8
                    && actor.hasTechnology(weapon.definition.technology)) {
                researchedWeaponCount++;
                if (researchedWeapon == null) {
                    researchedWeapon = weapon;
                }
            }
        }
        assertTrue(researchedWeaponCount > 0);
        assertNotNull(researchedWeapon);
        researchedWeapon.inStock = 1;

        assertEquals(100.0 / researchedWeaponCount,
                OpponentAI.weaponStockLevel(actor), 0.000001);
    }

    @Test
    public void productionConsumesRandomDrawsInSourceOrder() {
        GameState state = new GameState(1, 918L);
        GameState.PlayerState actor = state.getPlayerState(1);
        actor.getWeapons().get("antiAir").inStock = 0;
        actor.getWeapons().get("antiRocket").inStock = 0;
        BoundRecordingRandom random = new BoundRecordingRandom();

        assertNotNull(OpponentAI.planProduction(state, actor, random));

        assertEquals(Integer.valueOf(4), random.bounds.get(0));
        assertEquals(Integer.valueOf(16), random.bounds.get(1));
        assertEquals(Integer.valueOf(21), random.bounds.get(2));
    }

    @Test
    public void sourceEmptyCategoryZeroCanConsumeTheOnlyProductionSlot() {
        GameState state = new GameState(1, 62L);
        GameState.PlayerState actor = state.getPlayerState(1);
        assertFalse(actor.isSpecialPerson());
        assertFalse(actor.isSuicidePerson());
        actor.turnMoneyMillions = 100;

        boolean[] activeGroups = new boolean[8];
        for (GameState.WeaponState weapon : actor.getWeapons().values()) {
            if (weapon.definition.productionGroup == 8) {
                continue;
            }
            weapon.inStock = actor.getDesiredStock(weapon.definition.name);
            weapon.active = false;
            int group = weapon.definition.productionGroup;
            if (group >= 1 && group <= 7 && !activeGroups[group]) {
                weapon.active = true;
                activeGroups[group] = true;
            }
        }
        for (int group = 1; group <= 7; group++) {
            assertTrue("all source production groups need a zero-deficit active item",
                    activeGroups[group]);
        }

        SequenceRandom random = new SequenceRandom(0, 0, 0);
        OpponentAI.Decision decision = OpponentAI.planProduction(state, actor, random);

        assertNotNull(decision);
        assertEquals(GameState.Action.PRODUCTION, decision.action);
        assertTrue("the source's leading inactive category zero consumes this one-slot pick",
                decision.productionBudgets.isEmpty());
        assertEquals(0, decision.reservedMillions);
        assertEquals("no investment random draw occurs for inactive source category zero",
                2, random.getDrawCount());
    }

    @Test
    public void productionDeficitCountsUnresearchedWeaponsOnlyInActiveCategories()
            throws Exception {
        GameState state = new GameState(1, 919L);
        GameState.PlayerState actor = state.getPlayerState(1);
        java.lang.reflect.Field desireField = GameState.PlayerState.class
                .getDeclaredField("desiredStock");
        desireField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Integer> desired = (Map<String, Integer>) desireField.get(actor);

        for (GameState.WeaponState weapon : actor.getWeapons().values()) {
            weapon.active = false;
            weapon.inStock = 0;
            desired.put(weapon.definition.name, 0);
        }
        actor.getWeapons().get("rocket1").active = true;
        desired.put("rocket2", 2);
        desired.put("rocket3", 2);
        desired.put("rocket4", 5);
        actor.getWeapons().get("soldier1").active = true;
        desired.put("soldier1", 4);
        desired.put("warhead1", 100);
        desired.put("warhead2", 100);
        desired.put("warhead3", 100);
        desired.put("warhead4", 100);
        actor.getWeapons().get("antiAir").inStock = 2;
        actor.getWeapons().get("antiRocket").inStock = 2;

        OpponentAI.Decision decision = OpponentAI.planProduction(state, actor,
                new SequenceRandom(0, 0, 0));

        assertEquals(Integer.valueOf(26), decision.productionBudgets.get("rockets"));
        assertFalse(decision.productionBudgets.containsKey("soldier"));
        assertFalse(decision.productionBudgets.containsKey("warhead"));
    }

    @Test
    public void specialAttackScoreUsesTheSourceSnapshotBeforeItsLateLocalBonus() {
        GameState state = null;
        for (long seed = 0; seed < 500; seed++) {
            GameState candidate = new GameState(1, seed);
            if (candidate.getPlayerState(1).isSpecialPerson()
                    && !candidate.isActionBioloaded(1, GameState.Action.ATTACK)) {
                state = candidate;
                break;
            }
        }
        assertNotNull("seed sweep should find a special first opponent", state);

        GameState.PlayerState actor = state.getPlayerState(1);
        for (GameState.WeaponState weapon : actor.getWeapons().values()) {
            weapon.inStock = 0;
        }
        actor.getWeapons().get("ship").inStock = 1;
        actor.getWeapons().get("warhead3").inStock = 2;

        assertEquals(-999.0, OpponentAI.scoreForAction(state, actor,
                new Random(919L), GameState.Action.ATTACK), 0.0);
    }

    @Test
    public void specialAndSuicideAttackScoresUseTheirStrictSourceStockThresholds()
            throws Exception {
        GameState specialState = gameWithRegularFirstOpponent(9200L);
        GameState.PlayerState special = specialState.getPlayerState(1);
        clearWeaponStock(special);
        special.getWeapons().get("soldier1").active = true;
        special.getWeapons().get("soldier1").inStock = 1;
        special.getWeapons().get("warhead3").inStock = 2;
        setPersonality(special, true, false);

        assertEquals(-999.0, score(specialState, special, GameState.Action.ATTACK), 0.0);
        special.getWeapons().get("warhead3").inStock = 3;
        assertEquals(OpponentAI.baseAttackScore(specialState, special),
                score(specialState, special, GameState.Action.ATTACK), 0.0);

        GameState suicideState = gameWithRegularFirstOpponent(9700L);
        GameState.PlayerState suicide = suicideState.getPlayerState(1);
        clearWeaponStock(suicide);
        suicide.getWeapons().get("soldier4").active = true;
        suicide.getWeapons().get("soldier4").inStock = 1;
        setPersonality(suicide, false, true);

        assertEquals(-999.0, score(suicideState, suicide, GameState.Action.ATTACK), 0.0);
        suicide.getWeapons().get("soldier4").inStock = 2;
        assertEquals(OpponentAI.baseAttackScore(suicideState, suicide),
                score(suicideState, suicide, GameState.Action.ATTACK), 0.0);
    }

    @Test
    public void specialLeaderResourceScoreAddsTheTwoSourceStockShortfallBonuses()
            throws Exception {
        GameState state = gameWithRegularFirstOpponent(10200L);
        GameState.PlayerState actor = state.getPlayerState(1);
        actor.moneyMillions = 100;
        clearWeaponStock(actor);
        double regularScore = score(state, actor, GameState.Action.RESOURCES);

        setPersonality(actor, true, false);
        actor.getWeapons().get("ship").inStock = 0;
        actor.getWeapons().get("warhead3").inStock = 2;
        assertEquals(500.0,
                score(state, actor, GameState.Action.RESOURCES) - regularScore, 0.0);

        actor.getWeapons().get("ship").inStock = 1;
        actor.getWeapons().get("warhead3").inStock = 3;
        assertEquals(regularScore,
                score(state, actor, GameState.Action.RESOURCES), 0.0);
    }

    @Test
    public void specialLeaderDivertsProductionAndLaterResearchAtSourceTechBoundaries()
            throws Exception {
        GameState state = gameWithRegularFirstOpponent(10700L);
        GameState.PlayerState actor = state.getPlayerState(1);
        actor.moneyMillions = 1_000;
        clearWeaponStock(actor);
        actor.getWeapons().get("soldier1").active = true;
        actor.getWeapons().get("soldier1").inStock = 1;
        actor.getWeapons().get("warhead3").inStock = 3;
        actor.getWeapons().get("ship").inStock = 1;

        double regularProduction = score(state, actor, GameState.Action.PRODUCTION);
        double regularResearch = score(state, actor, GameState.Action.RESEARCH);
        assertTrue("the fixture must produce a positive source production score",
                regularProduction > 0.0);
        assertTrue("the fixture must have a positive source research score",
                regularResearch > 0.0);

        setPersonality(actor, true, false);
        assertEquals(-10.0,
                score(state, actor, GameState.Action.PRODUCTION), 0.0);
        assertEquals(regularResearch + regularProduction,
                score(state, actor, GameState.Action.RESEARCH), 0.0);

        setTechnology(actor, "ship", true);
        setTechnology(actor, "warhead3", true);
        setPersonality(actor, false, false);
        double researchedProduction = score(state, actor, GameState.Action.PRODUCTION);
        double researchedResearch = score(state, actor, GameState.Action.RESEARCH);
        assertTrue("the source special-tech transfer requires positive research",
                researchedResearch > 0.0);

        setPersonality(actor, true, false);
        assertEquals(-100.0, score(state, actor, GameState.Action.RESEARCH), 0.0);
        assertEquals(researchedProduction + researchedResearch,
                score(state, actor, GameState.Action.PRODUCTION), 0.0);
    }

    @Test
    public void suicideLeaderAddsPositiveProductionToResearchWithoutRemovingProduction()
            throws Exception {
        GameState state = gameWithRegularFirstOpponent(11200L);
        GameState.PlayerState actor = state.getPlayerState(1);
        actor.moneyMillions = 1_000;
        clearWeaponStock(actor);
        actor.getWeapons().get("soldier4").active = true;
        actor.getWeapons().get("soldier4").inStock = 2;

        double regularProduction = score(state, actor, GameState.Action.PRODUCTION);
        double regularResearch = score(state, actor, GameState.Action.RESEARCH);
        assertTrue(regularProduction > 0.0);

        setPersonality(actor, false, true);
        assertEquals(regularProduction,
                score(state, actor, GameState.Action.PRODUCTION), 0.0);
        assertEquals(regularResearch + regularProduction,
                score(state, actor, GameState.Action.RESEARCH), 0.0);

        setTechnology(actor, "soldier4", true);
        assertEquals(regularResearch,
                score(state, actor, GameState.Action.RESEARCH), 0.0);
    }

    @Test
    public void readyCarrierUsesSourceAttackBonusAndDisablesSeparateScore()
            throws Exception {
        GameState state = null;
        for (long seed = 920; seed < 1420; seed++) {
            GameState candidate = new GameState(1, seed);
            GameState.PlayerState candidateActor = candidate.getPlayerState(1);
            if (!candidateActor.isSpecialPerson() && !candidateActor.isSuicidePerson()) {
                state = candidate;
                break;
            }
        }
        assertNotNull("seed sweep should find a regular first opponent", state);
        GameState.PlayerState actor = state.getPlayerState(1);
        for (GameState.WeaponState weapon : actor.getWeapons().values()) {
            weapon.inStock = 0;
        }
        actor.getWeapons().get("rocket1").active = true;
        actor.getWeapons().get("warhead1").active = true;
        actor.getWeapons().get("warhead1").inStock = 1;
        setPreparedCarrier(actor, "rocket1");

        assertEquals(OpponentAI.baseAttackScore(state, actor) + 200,
                OpponentAI.scoreForAction(state, actor, new Random(920L),
                        GameState.Action.ATTACK), 0.0);
        assertEquals(-999.0, OpponentAI.scoreForAction(state, actor,
                new Random(921L), GameState.Action.PREPARED_ATTACK), 0.0);

        OpponentAI.Decision decision = OpponentAI.planAction(state, actor,
                GameState.Action.ATTACK, new SequenceRandom(0), 1,
                Collections.<String, Integer>emptyMap());
        assertNotNull(decision);
        assertEquals(GameState.Action.PREPARED_ATTACK, decision.action);
        assertFalse(decision.prepareCarrier);
        assertEquals("rocket1", decision.weaponName);
    }

    @Test
    public void preCounterScoreUsesTheThreateningEnemyRegardAndLowPopulationBonus() {
        GameState state = null;
        for (long seed = 0; seed < 500; seed++) {
            GameState candidate = new GameState(1, seed);
            GameState.PlayerState defender = candidate.getPlayerState(1);
            if (!defender.isSpecialPerson() && defender.getPrimaryEnemyId() != 0
                    && defender.getSecondaryEnemyId() != 0) {
                state = candidate;
                break;
            }
        }
        assertNotNull("seed sweep should leave the human outside the AI target pair", state);

        GameState.PlayerState defender = state.getPlayerState(1);
        int humanRegard = defender.getRegardToward(0);
        defender.populationMillions = 10;
        assertEquals(Math.max(0, 86 - humanRegard + 10),
                OpponentAI.preCounterScore(state, defender, Collections.singletonList(0)),
                0.0);
        defender.populationMillions = 20;
        assertEquals(Math.max(0, 86 - humanRegard),
                OpponentAI.preCounterScore(state, defender, Collections.singletonList(0)),
                0.0);
        assertEquals(0.0, OpponentAI.preCounterScore(state, defender,
                Collections.<Integer>emptyList()), 0.0);
    }

    @Test
    public void preparedDefenseTargetsHighestScoringEnemyThenSourceStrength() {
        GameState state = null;
        int defenderId = 1;
        int weakerThreatId = -1;
        int strongerThreatId = -1;
        for (long seed = 0; seed < 500 && state == null; seed++) {
            GameState candidate = new GameState(1, seed);
            GameState.PlayerState defender = candidate.getPlayerState(defenderId);
            for (int firstId = 0; firstId < 5 && state == null; firstId++) {
                if (firstId == defenderId || !candidate.getPlayerState(firstId).alive) {
                    continue;
                }
                for (int secondId = firstId + 1; secondId < 5; secondId++) {
                    if (secondId != defenderId && candidate.getPlayerState(secondId).alive
                            && defender.getRegardToward(firstId)
                            == defender.getRegardToward(secondId)
                            && defender.getRegardToward(firstId) < 86) {
                        state = candidate;
                        weakerThreatId = firstId;
                        strongerThreatId = secondId;
                        break;
                    }
                }
            }
        }
        assertNotNull("seed sweep should find equal-scoring prepared threats", state);

        GameState.PlayerState defender = state.getPlayerState(defenderId);
        GameState.PlayerState weakerThreat = state.getPlayerState(weakerThreatId);
        GameState.PlayerState strongerThreat = state.getPlayerState(strongerThreatId);
        weakerThreat.moneyMillions = 1;
        strongerThreat.moneyMillions = 1_000_000;
        assertTrue(strongerThreat.getStrength() > weakerThreat.getStrength());
        assertEquals(strongerThreatId, OpponentAI.mostThreateningPreparedEnemy(state,
                defender, Arrays.asList(weakerThreatId, strongerThreatId)));
    }

    @Test
    public void sourceStrengthCountsTechnologyCombatStockAndFundsButNotCounters() {
        GameState state = new GameState(1, 9231L);
        GameState.PlayerState actor = state.getPlayerState(1);
        int expected = actor.moneyMillions;
        for (String technologyName : actor.getInventedTechnologies()) {
            TechnologyCatalog.Definition technology = TechnologyCatalog.byName(technologyName);
            if (technology != null) {
                expected += technology.strengthWeight;
            }
        }
        for (GameState.WeaponState weapon : actor.getWeapons().values()) {
            if (!"anti".equals(weapon.definition.type)) {
                expected += weapon.inStock * weapon.definition.level;
            }
        }

        assertEquals(expected, actor.getStrength());
    }

    @Test
    public void sourceStrengthDoesNotChangeWhenCounterStockChanges() {
        GameState state = new GameState(1, 9232L);
        GameState.PlayerState actor = state.getPlayerState(1);
        int sourceStrength = actor.getStrength();

        actor.getWeapons().get("antiAir").inStock += 100;
        actor.getWeapons().get("antiRocket").inStock += 100;

        assertEquals(sourceStrength, actor.getStrength());
    }

    @Test
    public void preparedDefenseTriesNextEqualScoreThreatWhenStrongestHasNoCounter()
            throws Exception {
        GameState state = new GameState(1, 923L);
        GameState.PlayerState defender = state.getPlayerState(1);
        GameState.PlayerState strongest = state.getPlayerState(2);
        GameState.PlayerState counterable = state.getPlayerState(3);
        setRegardToward(defender, strongest.id, 20);
        setRegardToward(defender, counterable.id, 20);
        strongest.moneyMillions = 1_000;
        counterable.moneyMillions = 1;
        setPreparedCarrier(strongest, "rocket1");
        setPreparedCarrier(counterable, "plane1");
        defender.getWeapons().get("antiRocket").active = true;
        defender.getWeapons().get("antiRocket").inStock = 0;
        defender.getWeapons().get("antiAir").active = true;
        defender.getWeapons().get("antiAir").inStock = 1;

        assertTrue(strongest.getStrength() > counterable.getStrength());
        assertNull(state.getCounterWeaponForThreat(defender.id, strongest.id));
        assertEquals("antiAir", state.getCounterWeaponForThreat(
                defender.id, counterable.id));

        OpponentAI.Decision decision = OpponentAI.planDefense(state, defender,
                Collections.<String, Integer>emptyMap());
        assertNotNull(decision);
        assertEquals(GameState.Action.DEFENSE, decision.action);
        assertEquals("antiAir", decision.weaponName);
    }

    @Test
    public void opponentActionCountTracksWhetherAttackResetsTheOtherSlot() {
        GameState state = new GameState(1, 901L);
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals(1, state.getTurn());
        boolean opponentUsedAnotherSystem = false;
        for (int playerId = 1; playerId < 5; playerId++) {
            GameState.PlayerState opponent = state.getPlayerState(playerId);
            assertTrue("nation " + playerId + " should plan one or two source actions",
                    opponent.getLastTurnActions().size() >= 1
                            && opponent.getLastTurnActions().size() <= 2);
            assertFalse(opponent.getLastTurnActions().contains(null));
            assertTrue(opponent.turnMoneyMillions >= 0);
            for (GameState.Action action : opponent.getLastTurnActions()) {
                opponentUsedAnotherSystem |= action != GameState.Action.MASS_MEDIA;
            }
        }
        assertTrue(opponentUsedAnotherSystem);
    }

    @Test
    public void seededOpponentTurnsAreReproducibleAcrossActionAndInventoryState() {
        GameState first = completeOneHumanTurn(712L);
        GameState second = completeOneHumanTurn(712L);

        for (int playerId = 0; playerId < 5; playerId++) {
            GameState.PlayerState left = first.getPlayerState(playerId);
            GameState.PlayerState right = second.getPlayerState(playerId);
            assertEquals(left.getLastTurnActions(), right.getLastTurnActions());
            assertEquals(left.moneyMillions, right.moneyMillions);
            assertEquals(left.religion, right.religion);
            assertEquals(left.populationMillions, right.populationMillions);
            for (String weaponName : left.getWeapons().keySet()) {
                assertEquals(left.getWeaponStockByName(weaponName),
                        right.getWeaponStockByName(weaponName));
            }
            assertEquals(left.getInventedTechnologies(), right.getInventedTechnologies());
        }
    }

    @Test
    public void plannedSpendingMatchesSelectedProductionAndResearchOrders() {
        GameState state = new GameState(1, 52L);
        GameState.PlayerState opponent = state.getPlayerState(1);
        int startingFunds = opponent.turnMoneyMillions;
        List<OpponentAI.Decision> decisions = OpponentAI.plan(state, opponent,
                new Random(22L));
        int reserved = 0;
        for (OpponentAI.Decision decision : decisions) {
            reserved += decision.reservedMillions;
            int productionBudget = 0;
            for (int budget : decision.productionBudgets.values()) {
                productionBudget += budget;
            }
            if (decision.action == GameState.Action.PRODUCTION) {
                assertEquals(decision.reservedMillions, productionBudget);
            }
            if (decision.action == GameState.Action.RESEARCH) {
                int researchCost = 0;
                for (String technology : decision.technologies) {
                    researchCost += TechnologyCatalog.byName(technology).costMillions;
                }
                assertEquals(decision.reservedMillions, researchCost);
            }
        }
        assertEquals(2, decisions.size());
        assertEquals(startingFunds - reserved, opponent.turnMoneyMillions);
        assertTrue(opponent.turnMoneyMillions >= 0);
    }

    @Test
    public void plannerCanPrepareACompatibleCarrierWithoutDuplicatingTheSlot()
            throws Exception {
        GameState state = gameWithRegularFirstOpponent(53L);
        GameState.PlayerState opponent = state.getPlayerState(1);
        configureSourcePreparationScenario(opponent, "rocket1");
        setFriendlyRelationsAndNoPrimaryEnemy(opponent);
        setRegardToward(opponent, state.getHuman().id, 0);

        List<OpponentAI.Decision> decisions = OpponentAI.plan(state, opponent,
                new SequenceRandom(0, 0, 0, 0, 0, 0));
        int preparedActions = 0;
        for (OpponentAI.Decision decision : decisions) {
            if (decision.action == GameState.Action.PREPARED_ATTACK) {
                preparedActions++;
                assertTrue(decision.prepareCarrier);
                assertEquals("rocket1", decision.weaponName);
                assertNull(decision.payloadName);
            }
        }
        assertEquals("source preparation resets the companion action slot",
                1, decisions.size());
        assertTrue("planner should use the available strike option", preparedActions > 0);
        assertEquals(1, preparedActions);
    }

    @Test
    public void sourceAttackRouteRollChoosesPreparationOrDirectAttack() {
        GameState state = gameWithRegularFirstOpponent(531L);
        GameState.PlayerState opponent = state.getPlayerState(1);
        configureSourcePreparationScenario(opponent, "rocket1");
        GameState.WeaponState soldier = opponent.getWeapons().get("soldier1");
        soldier.active = true;
        soldier.inStock = 1;

        OpponentAI.Decision prepared = OpponentAI.planAction(state, opponent,
                GameState.Action.ATTACK, new SequenceRandom(0, 0, 0), 1,
                Collections.<String, Integer>emptyMap());
        assertNotNull(prepared);
        assertEquals(GameState.Action.PREPARED_ATTACK, prepared.action);
        assertTrue(prepared.prepareCarrier);

        OpponentAI.Decision direct = OpponentAI.planAction(state, opponent,
                GameState.Action.ATTACK, new SequenceRandom(1, 0, 0), 1,
                Collections.<String, Integer>emptyMap());
        assertNotNull(direct);
        assertEquals(GameState.Action.ATTACK, direct.action);
        assertEquals("soldier1", direct.weaponName);
    }

    @Test
    public void sourceCarrierSelectionUsesFirstLogicalCarrierOfChosenType() {
        GameState state = gameWithRegularFirstOpponent(531L);
        GameState.PlayerState opponent = state.getPlayerState(1);
        configureSourcePreparationScenario(opponent, "rocket1");
        GameState.WeaponState laterRocket = opponent.getWeapons().get("rocket4");
        laterRocket.active = true;
        laterRocket.inStock = 1;

        OpponentAI.Decision decision = OpponentAI.planAction(state, opponent,
                GameState.Action.ATTACK, new SequenceRandom(0, 0), 1,
                Collections.<String, Integer>emptyMap());

        assertNotNull(decision);
        assertTrue(decision.prepareCarrier);
        assertEquals("rocket1", decision.weaponName);
    }

    @Test
    public void sourceAllowDirCanRouteAnActiveSpyWithoutSoldiers() {
        GameState state = gameWithRegularFirstOpponent(5311L);
        GameState.PlayerState opponent = state.getPlayerState(1);
        for (GameState.WeaponState weapon : opponent.getWeapons().values()) {
            weapon.inStock = 0;
        }
        GameState.WeaponState spy = opponent.getWeapons().get("spy1");
        spy.active = true;
        spy.inStock = 1;
        opponent.spyReadiness = 0;
        state.getHuman().moneyMillions = 500;

        SequenceRandom random = new SequenceRandom(0, 0, 4);
        OpponentAI.Decision decision = OpponentAI.planAction(state, opponent,
                GameState.Action.ATTACK, random, 1,
                Collections.<String, Integer>emptyMap());

        assertNotNull(decision);
        assertEquals(GameState.Action.ATTACK, decision.action);
        assertEquals("spy1", decision.weaponName);
        assertEquals(state.getHuman().id, decision.targetPlayerId);
        assertEquals("factory", decision.spyBuilding);
        assertEquals("Source consumes alert, city, then building draws", 3,
                random.getDrawCount());
    }

    @Test
    public void repeatedReadyAttackPairsPayloadAndConventionalStrike() throws Exception {
        GameState state = gameWithRegularFirstOpponent(533L);
        GameState.PlayerState opponent = state.getPlayerState(1);
        configureSourcePreparationScenario(opponent, "rocket1");
        opponent.getWeapons().get("rocket1").inStock = 0;
        opponent.getWeapons().get("soldier1").active = true;
        opponent.getWeapons().get("soldier1").inStock = 1;
        setPreparedCarrier(opponent, "rocket1");

        List<OpponentAI.Decision> decisions = OpponentAI.plan(state, opponent,
                new SequenceRandom(0, 0, 0, 0, 0, 0));

        assertEquals(2, decisions.size());
        assertEquals(GameState.Action.PREPARED_ATTACK, decisions.get(0).action);
        assertEquals(GameState.Action.ATTACK, decisions.get(1).action);
        assertFalse(decisions.get(0).prepareCarrier);
        assertEquals("rocket1", decisions.get(0).weaponName);
        assertEquals("soldier1", decisions.get(1).weaponName);
        assertEquals(decisions.get(0).targetPlayerId, decisions.get(1).targetPlayerId);
    }

    @Test
    public void opponentPreparesAndLaunchesAStockedRocketAcrossTurns() {
        GameState state = new GameState(1, 59L);
        GameState.PlayerState opponent = state.getPlayerState(1);
        assertFalse(opponent.isSpecialPerson());
        assertFalse(opponent.isSuicidePerson());
        opponent.moneyMillions = 0;
        opponent.turnMoneyMillions = 0;
        for (GameState.WeaponState weapon : opponent.getWeapons().values()) {
            weapon.inStock = 0;
        }
        opponent.getWeapons().get("rocket1").active = true;
        opponent.getWeapons().get("rocket1").inStock = 1;
        opponent.getWeapons().get("warhead1").active = true;
        opponent.getWeapons().get("warhead1").inStock = 1;

        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals("rocket1", opponent.getPreparedCarrierName());
        assertTrue(opponent.getLastTurnActions().contains(GameState.Action.PREPARED_ATTACK));
        assertEquals(0, state.getWeaponStock(1, "rocket1"));
        assertEquals(1, state.getWeaponStock(1, "warhead1"));

        state.getHuman().getWeapons().get("antiRocket").active = true;
        state.getHuman().getWeapons().get("antiRocket").inStock = 1;
        state.selectAction(GameState.Action.DEFENSE);
        assertTrue(state.configureDefense("antiRocket"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertTrue(opponent.getLastTurnActions().contains(GameState.Action.PREPARED_ATTACK));
        assertNull(opponent.getPreparedCarrierName());
        assertEquals(0, state.getWeaponStock(1, "warhead1"));
        assertEquals(0, state.getWeaponStock(0, "antiRocket"));
    }

    @Test
    public void readyOpponentShipLaunchConsumesThreePayloadUnitsAcrossItsStrikeArray()
            throws Exception {
        GameState state = null;
        for (long seed = 0; seed < 500; seed++) {
            GameState candidate = new GameState(1, seed);
            GameState.PlayerState firstOpponent = candidate.getPlayerState(1);
            if (!firstOpponent.isSpecialPerson() && !firstOpponent.isSuicidePerson()) {
                state = candidate;
                break;
            }
        }
        assertNotNull("seed sweep should find a regular first opponent", state);
        GameState.PlayerState attacker = state.getPlayerState(1);
        configureSourcePreparationScenario(attacker, "ship");
        attacker.getWeapons().get("ship").active = true;
        attacker.getWeapons().get("ship").inStock = 0;
        attacker.getWeapons().get("warhead1").inStock = 2;
        attacker.getWeapons().get("bioload1").inStock = 1;
        setPreparedCarrier(attacker, "ship");

        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertNull(attacker.getPreparedCarrierName());
        assertEquals(0, attacker.getWeaponStockByName("warhead1"));
        assertEquals(0, attacker.getWeaponStockByName("bioload1"));
    }

    @Test
    public void biologicalStrikeBlocksHumanActionsAndRestoresTemporaryWeaponLocks() {
        GameState state = new GameState(1, 62L);
        GameState.PlayerState attacker = state.getPlayerState(1);
        assertFalse(attacker.isSpecialPerson());
        assertFalse(attacker.isSuicidePerson());
        attacker.moneyMillions = 0;
        attacker.turnMoneyMillions = 0;
        for (GameState.WeaponState weapon : attacker.getWeapons().values()) {
            weapon.inStock = 0;
        }
        attacker.getWeapons().get("rocket1").active = true;
        attacker.getWeapons().get("rocket1").inStock = 1;
        attacker.getWeapons().get("bioload1").active = true;
        attacker.getWeapons().get("warhead1").inStock = 0;
        attacker.getWeapons().get("bioload1").inStock = 1;
        for (GameState.WeaponState weapon : state.getHuman().getWeapons().values()) {
            if (weapon.definition.productionGroup != 8) {
                weapon.active = true;
            }
        }
        state.getHuman().getWeapons().get("antiRocket").inStock = 0;

        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        assertEquals("rocket1", attacker.getPreparedCarrierName());

        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        assertTrue(attacker.getLastTurnActions().contains(GameState.Action.PREPARED_ATTACK));
        assertEquals(0, state.getWeaponStock(1, "bioload1"));

        GameState.PlayerState human = state.getHuman();
        assertTrue(human.isBioloaded());
        boolean contaminatedCityFound = false;
        for (int cityIndex = 0; cityIndex < 5; cityIndex++) {
            contaminatedCityFound |= human.getCity(cityIndex).bioloaded;
        }
        assertTrue(contaminatedCityFound);
        assertFalse(human.getBioloadedActions().isEmpty());
        GameState.Action blocked = human.getBioloadedActions().iterator().next();
        assertTrue(state.isActionBioloaded(0, blocked));
        state.selectAction(blocked);
        assertNull(state.getSelectedAction());
        assertTrue(state.getLatestMessage().contains("biological contamination"));
        String disabledWeapon = null;
        for (GameState.WeaponState weapon : human.getWeapons().values()) {
            if (weapon.bioloaded) {
                assertFalse(weapon.active);
                disabledWeapon = weapon.definition.name;
                assertTrue(state.isWeaponBioloaded(0, disabledWeapon));
                break;
            }
        }
        assertTrue("source hazard attempts should disable active weapon stock",
                disabledWeapon != null);

        state.selectAction(GameState.Action.DEFENSE);
        assertTrue(state.configureDefense("antiAir"));
        assertTrue(state.acceptSelectedAction());
        queueAvailableActions(state);
        state.confirmTurn();

        assertFalse(human.isBioloaded());
        assertTrue(human.getBioloadedActions().isEmpty());
        for (int cityIndex = 0; cityIndex < 5; cityIndex++) {
            assertFalse(human.getCity(cityIndex).bioloaded);
        }
        assertFalse(human.getWeapons().get(disabledWeapon).bioloaded);
        assertFalse(state.isWeaponBioloaded(0, disabledWeapon));
        assertTrue(human.getWeapons().get(disabledWeapon).active);
    }

    @Test
    public void biologicalEffectsAlsoKeepContaminatedOpponentFromChoosingDisabledActions() {
        GameState state = new GameState(1, 552L);
        GameState.PlayerState human = state.getHuman();
        GameState.PlayerState target = state.getPlayerState(1);
        human.getWeapons().get("rocket1").inStock = 1;
        human.getWeapons().get("bioload1").inStock = 1;
        target.getWeapons().get("antiRocket").inStock = 0;

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedCarrier("rocket1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedLaunch(1, 0, "bioload1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertTrue(target.isBioloaded());
        assertFalse(target.getBioloadedActions().isEmpty());
        List<OpponentAI.Decision> decisions = OpponentAI.plan(state, target, new Random(553L));
        for (OpponentAI.Decision decision : decisions) {
            assertFalse("AI selected bio-disabled action " + decision.action,
                    target.isActionBioloaded(decision.action));
        }
    }

    @Test
    public void plannerDoesNotFillSecondSlotFromBioDisabledActionScores() throws Exception {
        GameState state = new GameState(1, 554L);
        GameState.PlayerState actor = state.getPlayerState(1);
        markEveryActionExceptBioloaded(actor, GameState.Action.RESOURCES);

        List<OpponentAI.Decision> decisions = OpponentAI.plan(state, actor,
                new Random(555L));

        assertEquals(1, decisions.size());
        assertEquals(GameState.Action.RESOURCES, decisions.get(0).action);
    }

    @Test
    public void plannerReturnsNoActionWhenEverySourceChoiceIsBioDisabled() throws Exception {
        GameState state = new GameState(1, 556L);
        GameState.PlayerState actor = state.getPlayerState(1);
        markEveryActionExceptBioloaded(actor, null);

        List<OpponentAI.Decision> decisions = OpponentAI.plan(state, actor,
                new Random(557L));

        assertTrue(decisions.isEmpty());
    }

    @Test
    public void repeatedTurnsKeepOpponentFundsAndCitiesWithinValidRanges() {
        GameState state = new GameState(2, 119L);
        for (int turn = 0; turn < 15 && state.getMatchStatus() == GameState.MatchStatus.ONGOING;
             turn++) {
            queueAvailableActions(state);
            state.confirmTurn();

            for (int playerId = 0; playerId < 5; playerId++) {
                GameState.PlayerState player = state.getPlayerState(playerId);
                assertTrue(player.moneyMillions >= 0);
                int cityPopulation = 0;
                for (int cityIndex = 0; cityIndex < 5; cityIndex++) {
                    assertTrue(player.getCity(cityIndex).populationMillions >= 0);
                    cityPopulation += player.getCity(cityIndex).populationMillions;
                }
                assertEquals(cityPopulation, player.populationMillions);
            }
        }
    }

    private static void queueAvailableActions(GameState state) {
        GameState.Action[] candidates = {
                GameState.Action.RESOURCES,
                GameState.Action.MASS_MEDIA,
                GameState.Action.PRODUCTION,
                GameState.Action.PROPAGANDA,
                GameState.Action.DIPLOMACY,
                GameState.Action.RESEARCH
        };
        for (GameState.Action action : candidates) {
            if (state.getQueuedActionCount() >= 2) {
                return;
            }
            if (state.isActionBioloaded(0, action)) {
                continue;
            }
            state.selectAction(action);
            if (action == GameState.Action.PRODUCTION) {
                state.configureProduction("soldier", 0);
            } else if (action == GameState.Action.PROPAGANDA) {
                boolean configured = false;
                for (int targetId = 1; targetId < 5 && !configured; targetId++) {
                    configured = state.configurePropaganda(targetId, 0);
                }
                if (!configured) {
                    state.cancelSelection();
                    continue;
                }
            } else if (action == GameState.Action.DIPLOMACY) {
                // The default proposal is affordable and targets the selected living island.
            } else if (action == GameState.Action.RESEARCH) {
                List<String> available = state.getAvailableResearch();
                if (available.isEmpty() || !state.selectResearchTechnology(available.get(0))) {
                    state.cancelSelection();
                    continue;
                }
            }
            if (!state.acceptSelectedAction()) {
                state.cancelSelection();
            }
        }
    }

    private static GameState completeOneHumanTurn(long seed) {
        GameState state = new GameState(1, seed);
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        return state;
    }

    private static GameState gameWithRegularFirstOpponent(long firstSeed) {
        return gameWithRegularFirstOpponent(1, firstSeed);
    }

    @SuppressWarnings("unchecked")
    private static void markEveryActionExceptBioloaded(GameState.PlayerState player,
                                                       GameState.Action available)
            throws Exception {
        Field field = GameState.PlayerState.class.getDeclaredField("bioloadedActions");
        field.setAccessible(true);
        Set<GameState.Action> bioloadedActions =
                (Set<GameState.Action>) field.get(player);
        for (GameState.Action action : GameState.Action.values()) {
            if (action != available) {
                bioloadedActions.add(action);
            }
        }
    }

    private static GameState gameWithRegularFirstOpponent(int difficulty, long firstSeed) {
        for (long seed = firstSeed; seed < firstSeed + 500; seed++) {
            GameState state = new GameState(difficulty, seed);
            GameState.PlayerState opponent = state.getPlayerState(1);
            if (!opponent.isSpecialPerson() && !opponent.isSuicidePerson()) {
                return state;
            }
        }
        throw new AssertionError("seed sweep should find a regular first opponent");
    }

    private static void clearWeaponStock(GameState.PlayerState player) {
        for (GameState.WeaponState weapon : player.getWeapons().values()) {
            weapon.inStock = 0;
        }
    }

    private static double score(GameState state, GameState.PlayerState player,
                                GameState.Action action) {
        return OpponentAI.scoreForAction(state, player, new Random(43L), action);
    }

    private static void setPersonality(GameState.PlayerState player, boolean special,
                                       boolean suicide) throws Exception {
        Field specialField = GameState.PlayerState.class.getDeclaredField("specialPerson");
        Field suicideField = GameState.PlayerState.class.getDeclaredField("suicidePerson");
        specialField.setAccessible(true);
        suicideField.setAccessible(true);
        specialField.setBoolean(player, special);
        suicideField.setBoolean(player, suicide);
    }

    @SuppressWarnings("unchecked")
    private static void setTechnology(GameState.PlayerState player, String technology,
                                      boolean invented) throws Exception {
        Field field = GameState.PlayerState.class.getDeclaredField("inventedTechnologies");
        field.setAccessible(true);
        Set<String> technologies = (Set<String>) field.get(player);
        if (invented) {
            technologies.add(technology);
        } else {
            technologies.remove(technology);
        }
    }

    private static GameState findAiWithNeutralHumanRegard() {
        for (long seed = 0; seed < 500; seed++) {
            GameState state = new GameState(1, seed);
            for (int playerId = 1; playerId < 5; playerId++) {
                GameState.PlayerState player = state.getPlayerState(playerId);
                if (player.alive && !player.isSpecialPerson()
                        && player.getRegardToward(0) == 50) {
                    return state;
                }
            }
        }
        throw new AssertionError("seed sweep should find an AI with 50 regard toward the human");
    }

    private static GameState.PlayerState aiWithNeutralHumanRegard(GameState state) {
        for (int playerId = 1; playerId < 5; playerId++) {
            GameState.PlayerState player = state.getPlayerState(playerId);
            if (player.alive && !player.isSpecialPerson()
                    && player.getRegardToward(0) == 50) {
                return player;
            }
        }
        throw new AssertionError("the selected game must include a neutral-regard AI");
    }

    private static GameState.PlayerState anotherRival(GameState state, int actorId,
                                                       int excludedId) {
        for (int playerId = 1; playerId < 5; playerId++) {
            if (playerId != actorId && playerId != excludedId
                    && state.getPlayerState(playerId).alive) {
                return state.getPlayerState(playerId);
            }
        }
        throw new AssertionError("the selected game must include two other active rivals");
    }

    private static void configureDiplomacyPopulationAndFunds(GameState state,
                                                              GameState.PlayerState actor,
                                                              GameState.PlayerState poorest,
                                                              GameState.PlayerState richer) {
        actor.moneyMillions = 100;
        actor.turnMoneyMillions = 100;
        actor.populationMillions = 1_000;
        state.getHuman().populationMillions = 500;
        state.getHuman().moneyMillions = 100;
        for (GameState.PlayerState target : state.getPlayers()) {
            if (target.alive && target.id != 0 && target.id != actor.id) {
                target.populationMillions = 500;
                target.moneyMillions = 50;
            }
        }
        poorest.populationMillions = 100;
        richer.populationMillions = 200;
    }

    private static GameState prepareSpyTargetScenario(boolean conventionalAttackAvailable) {
        GameState state = null;
        for (long seed = 0; seed < 500; seed++) {
            GameState candidate = new GameState(1, seed);
            GameState.PlayerState attacker = candidate.getPlayerState(1);
            if (OpponentAI.mostHostileTarget(candidate, attacker, false) != null
                    && OpponentAI.mostHostileTarget(candidate, attacker, false).id != 4) {
                state = candidate;
                break;
            }
        }
        assertNotNull("seed sweep should make the spy stock target non-hostile", state);

        GameState.PlayerState attacker = state.getPlayerState(1);
        attacker.spyReadiness = 100;
        for (GameState.WeaponState weapon : attacker.getWeapons().values()) {
            if ("soldier".equals(weapon.definition.type)) {
                weapon.inStock = 0;
            } else if ("spy".equals(weapon.definition.type)) {
                weapon.inStock = 0;
            }
        }
        GameState.WeaponState spy = attacker.getWeapons().get("spy4");
        spy.active = true;
        spy.inStock = 1;
        if (conventionalAttackAvailable) {
            GameState.WeaponState soldier = attacker.getWeapons().get("soldier1");
            soldier.active = true;
            soldier.inStock = 1;
        }

        state.getHuman().moneyMillions = 0;
        for (GameState.PlayerState target : state.getPlayers()) {
            if (target.id == attacker.id) {
                continue;
            }
            for (GameState.WeaponState weapon : target.getWeapons().values()) {
                weapon.inStock = 0;
            }
        }
        state.getPlayerState(4).getWeapons().get("warhead4").inStock = 8;
        return state;
    }

    private static GameState prepareSoldierSelectionScenario() {
        GameState state = new GameState(1, 914L);
        GameState.PlayerState attacker = state.getPlayerState(1);
        for (GameState.WeaponState weapon : attacker.getWeapons().values()) {
            weapon.inStock = 0;
            if ("soldier".equals(weapon.definition.type)) {
                weapon.active = true;
                weapon.inStock = 1;
            }
        }
        return state;
    }

    private static void setFriendlyRelationsAndNoPrimaryEnemy(GameState.PlayerState actor)
            throws Exception {
        java.lang.reflect.Field regardField = GameState.PlayerState.class
                .getDeclaredField("regardToward");
        regardField.setAccessible(true);
        Arrays.fill((double[]) regardField.get(actor), 100);
        java.lang.reflect.Field enemyField = GameState.PlayerState.class
                .getDeclaredField("primaryEnemyId");
        enemyField.setAccessible(true);
        enemyField.setInt(actor, -1);
    }

    private static void setRegardToward(GameState.PlayerState actor, int playerId, int regard)
            throws Exception {
        setRegardToward(actor, playerId, (double) regard);
    }

    private static void setRegardToward(GameState.PlayerState actor, int playerId,
                                        double regard) throws Exception {
        java.lang.reflect.Field regardField = GameState.PlayerState.class
                .getDeclaredField("regardToward");
        regardField.setAccessible(true);
        ((double[]) regardField.get(actor))[playerId] = regard;
    }

    private static void setCeasefireTurns(GameState.PlayerState actor, int turns)
            throws Exception {
        java.lang.reflect.Field ceasefireField = GameState.PlayerState.class
                .getDeclaredField("ceasefireTurns");
        ceasefireField.setAccessible(true);
        int[] ceasefireTurns = (int[]) ceasefireField.get(actor);
        Arrays.fill(ceasefireTurns, turns);
        ceasefireTurns[actor.id] = 0;
    }

    private static void setDesiredStock(GameState.PlayerState actor, int value)
            throws Exception {
        java.lang.reflect.Field desiredStockField = GameState.PlayerState.class
                .getDeclaredField("desiredStock");
        desiredStockField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Integer> desiredStock =
                (Map<String, Integer>) desiredStockField.get(actor);
        for (String weaponName : desiredStock.keySet()) {
            desiredStock.put(weaponName, value);
        }
    }

    private static void configureSourcePreparationScenario(
            GameState.PlayerState actor, String selectedCarrier) {
        actor.moneyMillions = 0;
        actor.turnMoneyMillions = 0;
        actor.religion = 100;
        for (GameState.WeaponState weapon : actor.getWeapons().values()) {
            String type = weapon.definition.type;
            if ("anti".equals(type) || "soldier".equals(type) || "spy".equals(type)
                    || "rocket".equals(type) || "plane".equals(type)
                    || "sub".equals(type) || "ship".equals(type)
                    || "space".equals(type)) {
                weapon.inStock = 0;
            } else {
                weapon.inStock = actor.hasTechnology(weapon.definition.technology)
                        ? actor.getDesiredStock(weapon.definition.name) : 0;
            }
        }
        GameState.WeaponState carrier = actor.getWeapons().get(selectedCarrier);
        carrier.active = true;
        carrier.inStock = 1;
        GameState.WeaponState warhead = actor.getWeapons().get("warhead1");
        warhead.active = true;
        warhead.inStock = "ship".equals(selectedCarrier) ? 2 : 1;
        if ("ship".equals(selectedCarrier)) {
            GameState.WeaponState bio = actor.getWeapons().get("bioload1");
            bio.active = true;
            bio.inStock = 1;
        }
    }

    private static void setPreparedCarrier(GameState.PlayerState actor, String carrierName)
            throws Exception {
        java.lang.reflect.Field carrierField = GameState.PlayerState.class
                .getDeclaredField("preparedCarrierName");
        carrierField.setAccessible(true);
        carrierField.set(actor, carrierName);
    }

    private static final class SequenceRandom extends Random {
        private final int[] values;
        private int index;

        SequenceRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            int value = index < values.length ? values[index] : 0;
            index++;
            return Math.floorMod(value, bound);
        }

        int getDrawCount() {
            return index;
        }
    }

    private static String summarize(List<OpponentAI.Decision> decisions) {
        StringBuilder result = new StringBuilder();
        for (OpponentAI.Decision decision : decisions) {
            if (result.length() > 0) {
                result.append(';');
            }
            result.append(decision.playerId).append(':').append(decision.action)
                    .append(':').append(decision.targetPlayerId)
                    .append(':').append(decision.targetCityIndex)
                    .append(':').append(decision.weaponName)
                    .append(':').append(decision.payloadName)
                    .append(':').append(decision.prepareCarrier)
                    .append(':').append(decision.diplomacyOption)
                    .append(':').append(decision.reservedMillions)
                    .append(':').append(decision.productionBudgets)
                    .append(':').append(decision.technologies)
                    .append(':').append(decision.spyBuilding)
                    .append(':').append(decision.shipStrikes);
        }
        return result.toString();
    }

    private static final class CountingRandom extends Random {
        private int drawCount;

        CountingRandom(long seed) {
            super(seed);
        }

        @Override
        public int nextInt(int bound) {
            drawCount++;
            return super.nextInt(bound);
        }

        int getDrawCount() {
            return drawCount;
        }

        void resetDrawCount() {
            drawCount = 0;
        }
    }

    private static final class BoundRecordingRandom extends Random {
        final List<Integer> bounds = new ArrayList<>();

        @Override
        public int nextInt(int bound) {
            bounds.add(bound);
            return 0;
        }
    }
}
