package com.example.worlddomination.game.simulation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.Random;

public final class GameStateTest {
    @Test
    public void normalResourcesAndMassMediaResolveOneTurn() {
        GameState state = new GameState(1);
        assertEquals(65, state.getHuman().moneyMillions);
        assertEquals(450, state.getHuman().populationMillions);
        assertEquals(65, state.getHuman().religion);
        assertEquals(41, state.getResourceIncomeMillions());
        assertEquals(30, state.getMassMediaGain());

        state.selectAction(GameState.Action.RESOURCES);
        assertEquals(true, state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertEquals(true, state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals(1, state.getTurn());
        assertEquals(106, state.getHuman().moneyMillions);
        assertTrue(state.getHuman().religion >= 85);
        assertTrue(state.getHuman().religion <= 90);
        assertEquals(0, state.getQueuedActionCount());
        assertNull(state.getSelectedAction());
    }

    @Test
    public void resourceYieldUsesDifficultyDividerAndSourceRounding() {
        assertEquals(56, new GameState(0).getResourceIncomeMillions());
        assertEquals(41, new GameState(1).getResourceIncomeMillions());
        assertEquals(31, new GameState(2).getResourceIncomeMillions());
    }

    @Test
    public void sourceTurnRequiresTwoActions() {
        GameState state = new GameState(1);
        int funds = state.getHuman().moneyMillions;
        int influence = state.getHuman().religion;
        state.selectAction(GameState.Action.RESOURCES);
        state.acceptSelectedAction();
        try {
            state.confirmTurn();
            fail("A source turn needs two action slots");
        } catch (IllegalStateException expected) {
            assertEquals(0, state.getTurn());
        }
        assertEquals(funds, state.getHuman().moneyMillions);
        assertEquals(influence, state.getHuman().religion);
        assertEquals(1, state.getQueuedActionCount());
    }

    @Test
    public void tappingAQueuedResourcesActionRemovesItForRequeue() {
        GameState state = new GameState(1);
        state.selectAction(GameState.Action.RESOURCES);
        state.acceptSelectedAction();
        assertEquals(1, state.getQueuedActionCount());

        state.selectAction(GameState.Action.RESOURCES);
        assertEquals(0, state.getQueuedActionCount());
        assertNull(state.getSelectedAction());

        state.selectAction(GameState.Action.RESOURCES);
        assertEquals(true, state.acceptSelectedAction());
        assertEquals(1, state.getQueuedActionCount());
    }

    @Test
    public void sourceStartingTablesIncludeOpponentEconomyAndBaseInventory() {
        GameState state = new GameState(1, 17L);
        assertEquals(65, state.getHuman().moneyMillions);
        assertEquals(65, state.getHuman().religion);
        assertEquals(65, state.getPlayers()[1].moneyMillions);
        assertEquals(40, state.getPlayers()[1].religion);
        assertEquals(45, state.getResourceIncomeMillions(1));
        assertEquals(35, state.getMassMediaGain(1));
        assertEquals(1, state.getWeaponStock(0, "soldier1"));
        assertEquals(1, state.getWeaponStock(0, "rocket1"));
        assertEquals(2, state.getWeaponStock(0, "antiAir"));
        assertTrue(state.isWeaponActive(0, "plane1"));
        assertFalse(state.isWeaponActive(0, "plane2"));
    }

    @Test
    public void researchRequiresPrerequisiteAndReservesItsSourceCost() {
        GameState state = new GameState(1, 3L);
        state.selectAction(GameState.Action.RESEARCH);
        assertFalse(state.selectResearchTechnology("soldier3"));
        assertTrue(state.selectResearchTechnology("soldier2"));
        assertTrue(state.acceptSelectedAction());
        assertEquals(55, state.getTurnMoneyMillions());
        assertEquals(65, state.getHuman().moneyMillions);
        assertFalse(state.getHuman().hasTechnology("soldier2"));
    }

    @Test
    public void researchCompletesAndActivatesTheCorrespondingWeapon() {
        GameState state = new GameState(1, 9L);
        state.selectAction(GameState.Action.RESEARCH);
        assertTrue(state.selectResearchTechnology("soldier2"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertTrue(state.getHuman().hasTechnology("soldier2"));
        assertTrue(state.isWeaponActive(0, "soldier2"));
        assertEquals(55, state.getHuman().moneyMillions);
        assertEquals(55, state.getTurnMoneyMillions());
        assertEquals(1, state.getTurn());
    }

    @Test
    public void productionReservesBudgetAndTurnsItIntoWeaponStock() {
        GameState state = new GameState(1, 0L);
        state.selectAction(GameState.Action.PRODUCTION);
        state.configureProduction("soldier", 50);
        assertEquals(32, state.getSelectedProductionBudgetMillions());
        assertTrue(state.acceptSelectedAction());
        assertEquals(33, state.getTurnMoneyMillions());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertTrue(state.getWeaponStock("soldier1") > 1);
        assertTrue(state.getHuman().moneyMillions < 106);
        assertEquals(state.getHuman().moneyMillions, state.getTurnMoneyMillions());
    }

    @Test
    public void removingReservedActionRestoresTheUnspentTurnBudget() {
        GameState state = new GameState(1, 0L);
        state.selectAction(GameState.Action.RESEARCH);
        assertTrue(state.selectResearchTechnology("soldier2"));
        assertTrue(state.acceptSelectedAction());
        assertEquals(55, state.getTurnMoneyMillions());

        state.removeQueuedAction(0);
        assertEquals(65, state.getTurnMoneyMillions());
        assertFalse(state.getHuman().hasTechnology("soldier2"));
    }

    @Test
    public void researchThenProductionUsesUnlockedUnitAndSourceCostOrder() {
        GameState state = new GameState(1, 0L);
        state.selectAction(GameState.Action.RESEARCH);
        assertTrue(state.selectResearchTechnology("soldier2"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.PRODUCTION);
        state.configureProduction("soldier", 50);
        assertEquals(27, state.getSelectedProductionBudgetMillions());
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertTrue(state.getHuman().hasTechnology("soldier2"));
        assertTrue(state.isWeaponActive(0, "soldier2"));
        assertEquals(30, state.getHuman().moneyMillions);
        assertTrue(state.getWeaponStock("soldier1") + state.getWeaponStock("soldier2") > 1);
        assertTrue(state.getHuman().score > 6004);
    }

    @Test
    public void startingPopulationIsDistributedAcrossFiveCitiesForEachPlayer() {
        GameState state = new GameState(1, 21L);
        for (GameState.PlayerState player : state.getPlayers()) {
            assertEquals(5, player.getCities().size());
            int total = 0;
            for (CityState city : player.getCities()) {
                assertEquals(player.id, city.ownerId);
                assertTrue(city.populationMillions > 0);
                total += city.populationMillions;
            }
            assertEquals(player.populationMillions, total);
        }
    }

    @Test
    public void directSoldierAttackConsumesStockAndDamagesSelectedCity() {
        GameState state = new GameState(1, 0L);
        int beforeCity = state.getCityPopulationMillions(1, 0);
        int beforePlayer = state.getPlayers()[1].populationMillions;
        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(1, 0, "soldier1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        int casualties = beforeCity - state.getCityPopulationMillions(1, 0);
        assertTrue(casualties >= 2 && casualties <= 4);
        assertEquals(beforePlayer - casualties, state.getPlayers()[1].populationMillions);
        assertEquals(0, state.getWeaponStock("soldier1"));
        assertTrue(state.getHuman().score > 0);
    }

    @Test
    public void directAttackRejectsDeadTargetsEmptyCitiesAndUnavailableWeapons() {
        GameState state = new GameState(1, 4L);
        state.selectAction(GameState.Action.ATTACK);
        assertFalse(state.configureAttack(0, 0, "soldier1"));
        assertFalse(state.configureAttack(1, 5, "soldier1"));
        assertFalse(state.configureAttack(1, 0, "soldier2"));
        assertTrue(state.configureAttack(1, 0, "spy1"));
        assertTrue(state.configureAttack(1, 0, "soldier1"));
        assertTrue(state.acceptSelectedAction());
        assertEquals(GameState.Action.ATTACK, state.getQueuedAction(0));
    }

    @Test
    public void rocketStrikeConsumesCarrierWhenPreparedAndPayloadWhenLaunched() {
        GameState state = new GameState(1, 201L, false);
        GameState.PlayerState human = state.getHuman();
        human.getWeapons().get("warhead1").inStock = 1;
        int cityBefore = state.getCityPopulationMillions(1, 0);

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.getAvailablePreparedCarriers(0).contains("rocket1"));
        assertTrue(state.configurePreparedCarrier("rocket1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals(0, state.getWeaponStock(0, "rocket1"));
        assertEquals(1, state.getWeaponStock(0, "warhead1"));
        assertEquals("rocket1", human.getPreparedCarrierName());
        assertEquals("antiRocket", state.getCounterWeaponForPreparedThreat(1));

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.getAvailablePreparedPayloads(0, "rocket1").contains("warhead1"));
        assertTrue(state.configurePreparedLaunch(1, 0, "warhead1"));
        assertTrue(state.canAcceptPreparedAttack());
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        int casualties = cityBefore - state.getCityPopulationMillions(1, 0);
        assertTrue("human rocket damage follows source 10..15M roll: " + casualties,
                casualties >= 10 && casualties <= 15);
        assertEquals(0, state.getWeaponStock(0, "warhead1"));
        assertNull(human.getPreparedCarrierName());
    }

    @Test
    public void preparedLaunchRejectsIncompatiblePayload() {
        GameState state = new GameState(1, 202L, false);
        GameState.PlayerState human = state.getHuman();
        human.getWeapons().get("warhead1").inStock = 1;
        human.getWeapons().get("plane1").inStock = 1;
        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedCarrier("rocket1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertFalse(state.configurePreparedLaunch(1, 0, "plane1"));
        assertFalse(state.canAcceptPreparedAttack());
    }

    @Test
    public void preparedShipRequiresThreeSameNationPayloadCityPairsAndResolvesEach() {
        GameState state = new GameState(1, 207L, false);
        GameState.PlayerState human = state.getHuman();
        GameState.PlayerState target = state.getPlayerState(1);
        human.getWeapons().get("ship").active = true;
        human.getWeapons().get("ship").inStock = 1;
        human.getWeapons().get("warhead1").inStock = 2;
        human.getWeapons().get("bioload1").inStock = 1;

        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedCarrier("ship"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        assertEquals("ship", human.getPreparedCarrierName());
        assertEquals(0, state.getWeaponStock(0, "ship"));

        int[] before = new int[3];
        for (int city = 1; city <= 3; city++) {
            before[city - 1] = target.getCity(city).populationMillions;
        }
        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertFalse(state.canAcceptPreparedAttack());
        assertTrue(state.configurePreparedShipStrike(1, 0, "warhead1"));
        assertFalse("all three city selections must belong to one nation",
                state.configurePreparedShipStrike(2, 0, "warhead1"));
        assertTrue(state.configurePreparedShipStrike(1, 1, "warhead1"));
        assertTrue(state.configurePreparedShipStrike(1, 2, "bioload1"));
        assertEquals(3, state.getSelectedShipStrikeCount());
        assertTrue("selecting a fourth city replaces the oldest ship strike",
                state.configurePreparedShipStrike(1, 3, "warhead1"));
        assertEquals(3, state.getSelectedShipStrikeCount());
        assertEquals(1, state.getSelectedShipStrikes().get(0).cityIndex);
        assertEquals(3, state.getSelectedShipStrikes().get(2).cityIndex);
        assertTrue(state.canAcceptPreparedAttack());
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertNull(human.getPreparedCarrierName());
        assertEquals(0, state.getWeaponStock(0, "warhead1"));
        assertEquals(0, state.getWeaponStock(0, "bioload1"));
        for (int city = 1; city <= 3; city++) {
            assertTrue("ship payload did not damage city " + city,
                    target.getCity(city).populationMillions < before[city - 1]);
        }
    }

    @Test
    public void shipCounterAndCasualtyRollsMatchSourceBoundaries() {
        for (int roll = 1; roll <= 3; roll++) {
            final int sourceRoll = roll;
            Random fixed = new Random(0L) {
                @Override
                public int nextInt(int bound) {
                    assertEquals(3, bound);
                    return sourceRoll - 1;
                }
            };
            assertEquals(roll == 2, GameState.shipCounterRollIntercepts(fixed));
        }
        Random minimum = new Random(0L) {
            @Override
            public float nextFloat() {
                return 0f;
            }
        };
        Random maximum = new Random(0L) {
            @Override
            public float nextFloat() {
                return 0.9999f;
            }
        };
        assertEquals(8, GameState.sourceShipStrikeCasualties(15, minimum));
        assertEquals(16, GameState.sourceShipStrikeCasualties(15, maximum));
    }

    @Test
    public void defenseSelectionConsumesOneMatchingCounter() {
        GameState state = new GameState(1, 203L, false);
        assertTrue(state.getAvailableDefenseWeapons(0).contains("antiRocket"));
        state.selectAction(GameState.Action.DEFENSE);
        assertTrue(state.configureDefense("antiRocket"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals(1, state.getWeaponStock(0, "antiRocket"));
        assertTrue(state.getLatestMessage().contains("complete"));
    }

    @Test
    public void carrierCounterMappingsAndPayloadLevelsMatchWeaponStock() {
        assertEquals("antiRocket", WeaponCatalog.byName("rocket4").counteredBy);
        assertEquals("antiAir", WeaponCatalog.byName("plane1").counteredBy);
        assertEquals("antiAir", WeaponCatalog.byName("ship").counteredBy);
        assertEquals(2, WeaponCatalog.byName("plane1").level);
        assertEquals(4, WeaponCatalog.byName("plane2").level);

        GameState state = new GameState(1, 206L, false);
        GameState.PlayerState human = state.getHuman();
        human.getWeapons().get("plane1").inStock = 1;
        human.getWeapons().get("warhead2").active = true;
        human.getWeapons().get("warhead2").inStock = 1;
        assertTrue(state.getAvailablePreparedPayloads(0, "plane1")
                .contains("warhead2"));
        human.getWeapons().get("ship").active = true;
        human.getWeapons().get("ship").inStock = 1;
        assertTrue(state.getAvailablePreparedCarriers(0).contains("ship"));
    }

    @Test
    public void counterInterceptRuleBlocksEightOfNineSourceRolls() {
        for (int roll = 1; roll <= 9; roll++) {
            final int sourceRoll = roll;
            Random fixed = new Random(0L) {
                @Override
                public int nextInt(int bound) {
                    assertEquals(9, bound);
                    return sourceRoll - 1;
                }
            };
            assertEquals(roll != 5, GameState.counterIntercepts(fixed));
        }
    }

    @Test
    public void preparedStrikeCasualtyRangesMatchSourceAttackerAndTargetCases() {
        Random random = new Random(204L);
        for (int i = 0; i < 100; i++) {
            int humanToAi = GameState.sourceStrikeCasualties(15, true, false, random);
            assertTrue(humanToAi >= 10 && humanToAi <= 15);
            int aiToHuman = GameState.sourceStrikeCasualties(15, false, true, random);
            assertTrue(aiToHuman >= 15 && aiToHuman <= 18);
        }
    }

    @Test
    public void unusedPreparedStrikeExpiresAtEndOfTheFollowingTurn() {
        GameState state = new GameState(1, 205L, false);
        state.getHuman().getWeapons().get("warhead1").inStock = 1;
        state.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(state.configurePreparedCarrier("rocket1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        assertEquals("rocket1", state.getHuman().getPreparedCarrierName());

        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertNull(state.getHuman().getPreparedCarrierName());
    }

    @Test
    public void spyAttackStealsMoneyAndStockThenFollowsReadinessCooldown() {
        GameState state = new GameState(1, 11L, false);
        GameState.PlayerState attacker = state.getHuman();
        GameState.PlayerState defender = state.getPlayers()[1];
        int attackerFundsBefore = attacker.moneyMillions;
        int defenderFundsBefore = defender.moneyMillions;
        int enemyCityBefore = defender.getCity(0).populationMillions;
        int attackerStarterStock = state.getWeaponStock(0, "rocket1")
                + state.getWeaponStock(0, "soldier1");

        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.getAvailableAttackWeapons().contains("spy1"));
        assertFalse(state.getAvailableAttackWeapons().contains("spy2"));
        assertFalse(state.configureAttack(1, 0, "spy2"));
        assertTrue(state.configureAttack(1, 0, "spy1"));
        state.cycleSpyBuilding(1);
        assertEquals("tech", state.getSelectedSpyBuilding());
        assertTrue(state.canAcceptAttack());
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertTrue(attacker.moneyMillions > attackerFundsBefore
                + state.getResourceIncomeMillions());
        assertTrue(defender.moneyMillions < defenderFundsBefore);
        assertEquals(enemyCityBefore, defender.getCity(0).populationMillions);
        assertEquals(0, state.getWeaponStock(0, "spy1"));
        assertEquals(0, state.getSpyReadiness());
        assertTrue(state.getWeaponStock(0, "rocket1")
                + state.getWeaponStock(0, "soldier1") > attackerStarterStock);
        assertTrue(attacker.score > 0);

        attacker.getWeapons().get("spy1").inStock = 1;
        assertFalse(state.getAvailableAttackWeapons().contains("spy1"));
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        assertEquals(25, state.getSpyReadiness());
        assertTrue(state.getAvailableAttackWeapons().contains("spy1"));
    }

    @Test
    public void spyStealsTechnologyWithoutRemovingEnemyUnitStock() {
        GameState state = new GameState(1, 14L, false);
        GameState.PlayerState attacker = state.getHuman();
        GameState.PlayerState defender = state.getPlayers()[1];
        GameState.WeaponState attackerSoldier = attacker.getWeapons().get("soldier1");
        GameState.WeaponState defenderSoldier = defender.getWeapons().get("soldier1");
        attackerSoldier.active = false;
        defender.getWeapons().get("rocket1").inStock = 0;
        defenderSoldier.inStock = 1;

        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(1, 0, "spy1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertTrue(attackerSoldier.active);
        assertEquals(1, defenderSoldier.inStock);
        assertEquals(0, state.getWeaponStock(0, "spy1"));
        assertTrue(attacker.score > 0);
    }

    @Test
    public void spyCanTargetAnEmptyCityAndDifficultyCapsStolenFunds() {
        GameState state = new GameState(0, 47L, false);
        GameState.PlayerState attacker = state.getHuman();
        GameState.PlayerState defender = state.getPlayers()[1];
        defender.moneyMillions = 1000;
        int emptiedCityPopulation = defender.getCity(0).populationMillions;
        defender.getCity(0).populationMillions = 0;
        int defenderPopulationBefore = defender.populationMillions - emptiedCityPopulation;
        defender.populationMillions = defenderPopulationBefore;
        int attackerFundsBefore = attacker.moneyMillions;
        int resourceIncome = state.getResourceIncomeMillions();

        state.selectAction(GameState.Action.ATTACK);
        assertFalse(state.configureAttack(1, 0, "soldier1"));
        assertTrue(state.configureAttack(1, 0, "spy1"));
        assertTrue(state.canAcceptAttack());
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals(910, defender.moneyMillions);
        assertEquals(attackerFundsBefore + 90 + resourceIncome, attacker.moneyMillions);
        assertEquals(0, defender.getCity(0).populationMillions);
        assertEquals(defenderPopulationBefore, defender.populationMillions);
        assertEquals(0, state.getWeaponStock(0, "spy1"));
        assertEquals(0, state.getSpyReadiness());
    }

    @Test
    public void propagandaTransfersSourceCalculatedCitizensFromChosenCity() {
        GameState state = new GameState(1, 32L);
        GameState.PlayerState attacker = state.getHuman();
        GameState.PlayerState defender = state.getPlayers()[1];
        attacker.religion = 0;
        defender.religion = 0;
        CityState targetCity = defender.getCity(0);
        int targetBefore = targetCity.populationMillions;
        int[] attackerCitiesBefore = new int[5];
        for (int i = 0; i < attackerCitiesBefore.length; i++) {
            attackerCitiesBefore[i] = attacker.getCity(i).populationMillions;
        }
        int transfer = state.getPropagandaTransferEstimate(1, 0);
        int expectedTransfer = Math.round(Math.round(targetBefore * 5f / 100f) / 2f);
        assertEquals(expectedTransfer, transfer);

        state.selectAction(GameState.Action.PROPAGANDA);
        assertTrue(state.configurePropaganda(1, 0));
        assertTrue(state.canAcceptPropaganda());
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals(targetBefore - transfer, targetCity.populationMillions);
        int expectedCityGain = Math.round(transfer / 5f);
        int expectedPlayerGain = 0;
        for (int i = 0; i < attackerCitiesBefore.length; i++) {
            assertEquals(attackerCitiesBefore[i] + expectedCityGain,
                    attacker.getCity(i).populationMillions);
            expectedPlayerGain += expectedCityGain;
        }
        assertEquals(450 + expectedPlayerGain, attacker.populationMillions);
        assertEquals(447, defender.populationMillions);
        assertTrue(attacker.score > 0);
    }

    @Test
    public void propagandaHonorsReligionCapAndRejectsInvalidTargets() {
        GameState state = new GameState(1, 19L);
        GameState.PlayerState attacker = state.getHuman();
        GameState.PlayerState defender = state.getPlayers()[1];
        attacker.religion = 11;
        defender.religion = 100;
        CityState targetCity = defender.getCity(0);
        int targetBefore = targetCity.populationMillions;
        int transfer = state.getPropagandaTransferEstimate(1, 0);
        assertEquals(Math.round(Math.round(targetBefore * 5f / 100f) / 2f), transfer);

        state.selectAction(GameState.Action.PROPAGANDA);
        assertFalse(state.configurePropaganda(0, 0));
        assertFalse(state.configurePropaganda(1, 5));
        assertTrue(state.configurePropaganda(1, 0));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        assertEquals(targetBefore - transfer, targetCity.populationMillions);
        assertEquals(450 - transfer, defender.populationMillions);
    }

    @Test
    public void propagandaUsesStrictPopulationThresholdsAndHumanReligionCap() {
        GameState state = new GameState(1, 7L);
        GameState.PlayerState attacker = state.getHuman();
        GameState.PlayerState defender = state.getPlayers()[1];
        CityState city = defender.getCity(0);
        city.populationMillions = 64;
        attacker.religion = 80;
        defender.religion = 60;
        defender.populationMillions = 450;

        int[] populationDifferences = {20, 21, 40, 41, 80, 81};
        int[] expectedTransfers = {7, 6, 6, 6, 6, 5};
        for (int i = 0; i < populationDifferences.length; i++) {
            attacker.populationMillions = 450 + populationDifferences[i];
            assertEquals(expectedTransfers[i], state.getPropagandaTransferEstimate(1, 0));
        }

        attacker.populationMillions = 450;
        attacker.religion = 100;
        defender.religion = 0;
        assertEquals(7, state.getPropagandaTransferEstimate(1, 0));

        attacker.religion = 99;
        defender.religion = 100;
        assertEquals(2, state.getPropagandaTransferEstimate(1, 0));
    }

    @Test
    public void eliminatingTheLastCityMarksOpponentDefeated() {
        GameState state = new GameState(1, 5L);
        GameState.PlayerState opponent = state.getPlayers()[1];
        for (CityState city : opponent.getCities()) {
            city.populationMillions = 0;
        }
        opponent.getCity(0).populationMillions = 1;
        opponent.populationMillions = 1;
        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(1, 0, "soldier1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals(0, opponent.populationMillions);
        assertFalse(opponent.alive);
    }

    @Test
    public void matchEndsInVictoryAfterEveryRivalIsEliminated() {
        GameState state = new GameState(1, 8L);
        for (int playerId = 1; playerId < 5; playerId++) {
            GameState.PlayerState opponent = state.getPlayers()[playerId];
            for (CityState city : opponent.getCities()) {
                city.populationMillions = 0;
            }
            opponent.populationMillions = 0;
        }
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals(GameState.MatchStatus.VICTORY, state.getMatchStatus());
    }

    @Test
    public void matchEndsInDefeatWhenHumanPopulationReachesZero() {
        GameState state = new GameState(1, 12L);
        for (CityState city : state.getHuman().getCities()) {
            city.populationMillions = 0;
        }
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertFalse(state.getHuman().alive);
        assertEquals(GameState.MatchStatus.DEFEAT, state.getMatchStatus());
    }

    @Test
    public void diplomacyOptionsMatchReferenceTableAndReserveOfferFunds() {
        GameState state = new GameState(1, 4L);
        assertEquals(65, state.getHuman().getRegardToward(1));
        assertEquals("offer", state.getDiplomacyOptionType(0));
        assertEquals(10, state.getDiplomacyOptionAmount(0));
        assertEquals(3, state.getDiplomacyOptionDuration(0));
        assertEquals("offer", state.getDiplomacyOptionType(1));
        assertEquals(50, state.getDiplomacyOptionAmount(1));
        assertEquals(6, state.getDiplomacyOptionDuration(1));
        assertEquals("offer", state.getDiplomacyOptionType(2));
        assertEquals(90, state.getDiplomacyOptionAmount(2));
        assertEquals(10, state.getDiplomacyOptionDuration(2));
        assertEquals("demand", state.getDiplomacyOptionType(3));

        state.selectAction(GameState.Action.DIPLOMACY);
        state.cycleDiplomacyOption(1);
        assertEquals(1, state.getSelectedDiplomacyOption());
        assertTrue(state.canAcceptDiplomacy());
        assertTrue(state.acceptSelectedAction());
        assertEquals(15, state.getTurnMoneyMillions());
        state.removeQueuedAction(0);
        assertEquals(65, state.getTurnMoneyMillions());

        state.selectAction(GameState.Action.DIPLOMACY);
        state.cycleDiplomacyOption(1);
        state.cycleDiplomacyOption(1);
        assertEquals(2, state.getSelectedDiplomacyOption());
        assertFalse(state.canAcceptDiplomacy());
    }

    @Test
    public void diplomacyOfferIsResolvedByAiAndCreatesTimedSymmetricCeasefire() {
        GameState state = new GameState(1, 18L, false);
        state.selectAction(GameState.Action.DIPLOMACY);
        assertTrue(state.acceptSelectedAction());
        assertEquals(55, state.getTurnMoneyMillions());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals(96, state.getHuman().moneyMillions);
        assertEquals(65, state.getPlayerState(1).moneyMillions);
        assertEquals(67, state.getHuman().getRegardToward(1));
        assertEquals(67, state.getPlayerState(1).getRegardToward(0));
        assertEquals(0, state.getPendingDiplomacyProposal(1).proposerId);

        completeTurnWithResourcesAndMedia(state);
        assertEquals(75, state.getPlayerState(1).moneyMillions);
        assertEquals(2, state.getHuman().getCeasefireTurnsWith(1));
        assertEquals(2, state.getPlayerState(1).getCeasefireTurnsWith(0));

        state.selectAction(GameState.Action.ATTACK);
        assertFalse(state.configureAttack(1, 0, "soldier1"));
        assertTrue(state.configureAttack(1, 0, "spy1"));
        assertTrue(state.canAcceptAttack());

        completeTurnWithResourcesAndMedia(state);
        assertEquals(1, state.getHuman().getCeasefireTurnsWith(1));
        completeTurnWithResourcesAndMedia(state);
        assertFalse(state.getHuman().hasCeasefireWith(1));
    }

    @Test
    public void rejectedDiplomacyOfferRefundsItsFullCost() {
        GameState state = new GameState(1, 13L);
        GameState.PlayerState opponent = state.getPlayerState(1);
        opponent.moneyMillions = 200;
        opponent.turnMoneyMillions = 200;
        setRegard(opponent, 0, 0);

        state.selectAction(GameState.Action.DIPLOMACY);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
        int afterOfferAndIncome = state.getHuman().moneyMillions;
        assertEquals(65 - 10 + 41, afterOfferAndIncome);

        completeTurnWithResourcesAndMedia(state);
        assertEquals(afterOfferAndIncome + 10 + 41, state.getHuman().moneyMillions);
        assertFalse(state.getHuman().hasCeasefireWith(1));
    }

    @Test
    public void diplomacyDemandUsesAiAcceptanceRulesAndTransfersMoney() {
        GameState state = new GameState(1, 20L, false);
        GameState.PlayerState human = state.getHuman();
        GameState.PlayerState opponent = state.getPlayerState(1);
        human.moneyMillions = 1000;
        human.turnMoneyMillions = 1000;
        human.populationMillions = 500;
        opponent.moneyMillions = 10;
        opponent.turnMoneyMillions = 10;
        opponent.populationMillions = 400;

        state.selectAction(GameState.Action.DIPLOMACY);
        for (int i = 0; i < 3; i++) {
            state.cycleDiplomacyOption(1);
        }
        assertEquals(3, state.getSelectedDiplomacyOption());
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        completeTurnWithResourcesAndMedia(state);
        assertEquals(1051, human.moneyMillions);
        assertEquals(0, opponent.moneyMillions);
        assertTrue(human.hasCeasefireWith(1));
    }

    @Test
    public void humanCanRespondToAiOfferAndDemandProposals() {
        GameState offerState = new GameState(1, 6L);
        assertTrue(offerState.submitDiplomacyProposal(1, 0, 0));
        assertEquals(55, offerState.getPlayerState(1).moneyMillions);
        assertTrue(offerState.respondToDiplomacyProposal(true));
        assertEquals(75, offerState.getHuman().moneyMillions);
        assertEquals(3, offerState.getHuman().getCeasefireTurnsWith(1));

        GameState demandState = new GameState(1, 6L);
        assertTrue(demandState.submitDiplomacyProposal(1, 0, 3));
        assertTrue(demandState.respondToDiplomacyProposal(true));
        assertEquals(55, demandState.getHuman().moneyMillions);
        assertEquals(75, demandState.getPlayerState(1).moneyMillions);
        assertTrue(demandState.getPlayerState(1).hasCeasefireWith(0));
    }

    private static void completeTurnWithResourcesAndMedia(GameState state) {
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();
    }

    private static void setRegard(GameState.PlayerState player, int opponentId, int value) {
        try {
            java.lang.reflect.Field field = GameState.PlayerState.class
                    .getDeclaredField("regardToward");
            field.setAccessible(true);
            ((int[]) field.get(player))[opponentId] = value;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    public void unportedActionsCannotBeSelectedOrQueued() {
        GameState state = new GameState(1);
        GameState.Action[] unportedActions = {
                GameState.Action.PREPARED_ATTACK,
                GameState.Action.DEFENSE
        };
        for (GameState.Action action : unportedActions) {
            state.selectAction(action);
            assertNull(state.getSelectedAction());
            assertEquals(0, state.getQueuedActionCount());
        }
    }

    @Test
    public void cancellingCurrentSelectionPreservesAcceptedAction() {
        GameState state = new GameState(1);
        state.selectAction(GameState.Action.RESOURCES);
        assertEquals(true, state.acceptSelectedAction());
        state.selectAction(GameState.Action.MASS_MEDIA);

        state.cancelSelection();

        assertNull(state.getSelectedAction());
        assertEquals(1, state.getQueuedActionCount());
        assertEquals(GameState.Action.RESOURCES, state.getQueuedAction(0));
    }

    @Test
    public void hardMassMediaCannotRaiseInfluenceAboveOneHundred() {
        GameState state = new GameState(2);
        for (int turn = 0; turn < 5; turn++) {
            state.selectAction(GameState.Action.MASS_MEDIA);
            assertEquals(true, state.acceptSelectedAction());
            state.selectAction(GameState.Action.RESOURCES);
            assertEquals(true, state.acceptSelectedAction());
            state.confirmTurn();
        }

        assertEquals(100, state.getHuman().religion);
        assertEquals(210, state.getHuman().moneyMillions);
        assertEquals(5, state.getTurn());
    }

    @Test
    public void confirmingHumanActionsAppliesSourceReligionDecayToOpponents() {
        GameState state = new GameState(1, 0L);
        GameState.PlayerState opponent = state.getPlayers()[1];
        int opponentMoney = opponent.moneyMillions;
        int opponentReligion = opponent.religion;

        state.selectAction(GameState.Action.RESOURCES);
        state.acceptSelectedAction();
        state.selectAction(GameState.Action.MASS_MEDIA);
        state.acceptSelectedAction();
        state.confirmTurn();

        assertEquals(opponentMoney, opponent.moneyMillions);
        assertTrue(opponent.religion <= opponentReligion - 5);
        assertTrue(opponent.religion >= opponentReligion - 10);
    }

    @Test
    public void sourceDifficultyTablesInitializeHumanAndOpponent() {
        int[] humanMoney = {80, 65, 55};
        int[] humanPopulation = {500, 450, 400};
        int[] humanReligion = {80, 65, 55};
        int[] enemyMoney = {50, 65, 60};
        int[] enemyReligion = {30, 40, 60};

        for (int difficulty = 0; difficulty < 3; difficulty++) {
            GameState state = new GameState(difficulty);
            GameState.PlayerState[] players = state.getPlayers();
            assertEquals(humanMoney[difficulty], players[0].moneyMillions);
            assertEquals(humanPopulation[difficulty], players[0].populationMillions);
            assertEquals(humanReligion[difficulty], players[0].religion);
            assertEquals(enemyMoney[difficulty], players[1].moneyMillions);
            assertEquals(450, players[1].populationMillions);
            assertEquals(enemyReligion[difficulty], players[1].religion);
        }
    }

    @Test
    public void difficultyOutsideSourceTableIsRejected() {
        for (int difficulty : new int[]{-1, 3}) {
            try {
                new GameState(difficulty);
                fail("Unsupported difficulty should be rejected: " + difficulty);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("Difficulty"));
            }
        }
    }

    @Test
    public void queuedResourcesDoNotChangeFundsBeforeTurnConfirmation() {
        GameState state = new GameState(1);
        int startingFunds = state.getHuman().moneyMillions;

        state.selectAction(GameState.Action.RESOURCES);
        state.acceptSelectedAction();
        assertEquals(startingFunds, state.getHuman().moneyMillions);
        state.selectAction(GameState.Action.MASS_MEDIA);
        assertEquals(startingFunds, state.getHuman().moneyMillions);
    }

    @Test
    public void clearingTurnActionsRemovesQueuedAndPendingActions() {
        GameState state = new GameState(1);
        state.selectAction(GameState.Action.RESOURCES);
        state.acceptSelectedAction();
        state.selectAction(GameState.Action.MASS_MEDIA);

        state.clearQueuedActions();

        assertEquals(0, state.getQueuedActionCount());
        assertNull(state.getSelectedAction());
        assertEquals(65, state.getHuman().moneyMillions);
        assertEquals(65, state.getHuman().religion);
    }
}
