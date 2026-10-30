package com.example.worlddomination.game.ai;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.TechnologyCatalog;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class OpponentAITest {
    @Test
    public void researchTurnBonusContinuesPastTheUnusedSixtyTurnCap() {
        assertEquals(108.0, OpponentAI.researchTurnRatio(60, 5), 0.0);
        assertEquals(216.0, OpponentAI.researchTurnRatio(120, 5), 0.0);
        assertEquals(108.0, OpponentAI.researchTurnRatio(120, 10), 0.0);
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
        OpponentAI.Decision spyDecision = OpponentAI.planAttack(spyState,
                spyState.getPlayerState(1), new SequenceRandom(0, 0, 3, 0),
                Collections.<String, Integer>emptyMap());
        assertNotNull(spyDecision);
        assertEquals("spy4", spyDecision.weaponName);
        assertEquals(4, spyDecision.targetPlayerId);

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

        assertEquals(81.0, OpponentAI.baseAttackScore(state, actor), 0.0);
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
    public void specialAttackScoreDoesNotReceiveBonusAppliedAfterSourceActionArray() {
        GameState state = null;
        for (long seed = 0; seed < 500; seed++) {
            GameState candidate = new GameState(1, seed);
            if (candidate.getPlayerState(1).isSpecialPerson()) {
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
    public void plannerCanPrepareACompatibleCarrierWithoutDuplicatingTheSlot() {
        GameState state = new GameState(1, 53L);
        GameState.PlayerState opponent = state.getPlayerState(1);
        configureSourcePreparationScenario(opponent, "rocket1");

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

        OpponentAI.Decision decision = OpponentAI.planAction(state, opponent,
                GameState.Action.ATTACK, new SequenceRandom(0, 0, 0), 1,
                Collections.<String, Integer>emptyMap());

        assertNotNull(decision);
        assertEquals(GameState.Action.ATTACK, decision.action);
        assertEquals("spy1", decision.weaponName);
        assertEquals(state.getHuman().id, decision.targetPlayerId);
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
        GameState state = gameWithRegularFirstOpponent(54L);
        GameState.PlayerState opponent = state.getPlayerState(1);
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

        int antiRocketBefore = state.getWeaponStock(0, "antiRocket");
        state.selectAction(GameState.Action.DEFENSE);
        assertTrue(state.configureDefense("antiRocket"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertTrue(opponent.getLastTurnActions().contains(GameState.Action.PREPARED_ATTACK));
        assertNull(opponent.getPreparedCarrierName());
        assertEquals(0, state.getWeaponStock(1, "warhead1"));
        assertEquals(antiRocketBefore - 1, state.getWeaponStock(0, "antiRocket"));
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
        GameState state = gameWithRegularFirstOpponent(55L);
        GameState.PlayerState attacker = state.getPlayerState(1);
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

        GameState.PlayerState human = state.getHuman();
        assertTrue(human.isBioloaded());
        boolean contaminatedCityFound = false;
        for (int cityIndex = 0; cityIndex < 5; cityIndex++) {
            contaminatedCityFound |= human.getCity(cityIndex).bioloaded;
        }
        assertTrue(contaminatedCityFound);
        assertFalse(human.getBioloadedActions().isEmpty());
        GameState.Action blocked = human.getBioloadedActions().iterator().next();
        state.selectAction(blocked);
        assertNull(state.getSelectedAction());
        assertTrue(state.getLatestMessage().contains("biological contamination"));
        String disabledWeapon = null;
        for (GameState.WeaponState weapon : human.getWeapons().values()) {
            if (weapon.bioloaded) {
                assertFalse(weapon.active);
                disabledWeapon = weapon.definition.name;
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
        for (long seed = firstSeed; seed < firstSeed + 500; seed++) {
            GameState state = new GameState(1, seed);
            GameState.PlayerState opponent = state.getPlayerState(1);
            if (!opponent.isSpecialPerson() && !opponent.isSuicidePerson()) {
                return state;
            }
        }
        throw new AssertionError("seed sweep should find a regular first opponent");
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
        Arrays.fill((int[]) regardField.get(actor), 100);
        java.lang.reflect.Field enemyField = GameState.PlayerState.class
                .getDeclaredField("primaryEnemyId");
        enemyField.setAccessible(true);
        enemyField.setInt(actor, -1);
    }

    private static void setRegardToward(GameState.PlayerState actor, int playerId, int regard)
            throws Exception {
        java.lang.reflect.Field regardField = GameState.PlayerState.class
                .getDeclaredField("regardToward");
        regardField.setAccessible(true);
        ((int[]) regardField.get(actor))[playerId] = regard;
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
            int value = index < values.length ? values[index++] : 0;
            return Math.floorMod(value, bound);
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
