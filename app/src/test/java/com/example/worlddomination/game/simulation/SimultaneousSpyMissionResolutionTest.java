package com.example.worlddomination.game.simulation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Source-parity coverage for spy missions that resolve in the same turn. */
public final class SimultaneousSpyMissionResolutionTest {
    @Test
    public void defeatSkipsSpyReadinessResetAfterTheMissionResolves() {
        GameState state = new GameState(1, 211L, false);
        GameState.PlayerState human = state.getHuman();
        human.spyReadiness = 75;
        for (CityState city : human.getCities()) {
            city.populationMillions = 0;
        }
        human.populationMillions = 0;
        GameState.WeaponState spy = human.getWeapons().get("spy1");
        spy.active = true;
        spy.inStock = 1;

        state.selectAction(GameState.Action.ATTACK);
        assertTrue(state.configureAttack(1, 0, "spy1"));
        assertTrue(state.acceptSelectedAction());
        state.selectAction(GameState.Action.RESOURCES);
        assertTrue(state.acceptSelectedAction());
        state.confirmTurn();

        assertEquals(GameState.MatchStatus.DEFEAT, state.getMatchStatus());
        assertEquals(1, state.getLastTurnSpyMissions().size());
        assertEquals("upkeep advances readiness but the skipped result phase cannot reset it",
                100, human.spyReadiness);
        assertEquals(1, spy.inStock);
    }

    @Test
    public void concurrentSpyAttacksUseTheSameSourceStockSnapshot() {
        GameState state = new GameState(1, 123L, false);
        GameState.PlayerState attacker = state.getHuman();
        GameState.PlayerState defender = state.getPlayerState(1);

        for (GameState.WeaponState weapon : attacker.getWeapons().values()) {
            weapon.inStock = 0;
        }
        for (GameState.WeaponState weapon : defender.getWeapons().values()) {
            weapon.inStock = 0;
        }

        GameState.WeaponState spy = attacker.getWeapons().get("spy1");
        spy.active = true;
        spy.inStock = 2;
        GameState.WeaponState acquiredWeapon = attacker.getWeapons().get("soldier1");
        acquiredWeapon.active = true;
        GameState.WeaponState onlyDefenderStock = defender.getWeapons().get("soldier1");
        onlyDefenderStock.active = true;
        onlyDefenderStock.inStock = 1;

        for (int mission = 0; mission < 2; mission++) {
            state.selectAction(GameState.Action.ATTACK);
            assertTrue(state.configureAttack(defender.id, 0, "spy1"));
            assertTrue(state.acceptSelectedAction());
        }

        state.confirmTurn();

        // Sub_presenter creates every spy result array before Presenter applies
        // any of them, so both missions can target this one source-stock item.
        // Presenter decrements the defender only while stock remains, while it
        // grants both accepted theft results to the already-active attacker.
        assertEquals(2, acquiredWeapon.inStock);
        assertEquals(0, onlyDefenderStock.inStock);
        assertEquals(2, state.getLastTurnSpyMissions().size());
    }
}
