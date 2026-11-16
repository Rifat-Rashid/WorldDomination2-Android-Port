package com.example.worlddomination.game.simulation;

import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.assertEquals;

/** Source Sub_presenter.turnResults repairs negative cash before special income. */
public final class SourceTurnCashRecoveryTest {
    @Test public void negativeHumanAndAiCashRecoverWithoutChangingBudgets() throws Exception {
        GameState game = regularGame();
        GameState.PlayerState human = game.getPlayerState(0), ai = game.getPlayerState(1);
        human.moneyMillions = -1; human.turnMoneyMillions = -7;
        ai.moneyMillions = -44; ai.turnMoneyMillions = -48;
        upkeep(game);
        assertEquals(1, human.moneyMillions);
        assertEquals(1, ai.moneyMillions);
        assertEquals(-7, human.turnMoneyMillions);
        assertEquals(-48, ai.turnMoneyMillions);
    }

    @Test public void zeroAndPositiveCashAreNotRepaired() throws Exception {
        GameState game = regularGame();
        game.getPlayerState(0).moneyMillions = 0;
        game.getPlayerState(1).moneyMillions = 8;
        upkeep(game);
        assertEquals(0, game.getPlayerState(0).moneyMillions);
        assertEquals(8, game.getPlayerState(1).moneyMillions);
    }

    @Test public void specialIncomeFollowsRecoveryWithoutCreditingTurnBudget() throws Exception {
        GameState game = regularGame();
        GameState.PlayerState ai = game.getPlayerState(1);
        setSpecial(ai, true);
        ai.moneyMillions = -44; ai.turnMoneyMillions = -12;
        upkeep(game);
        assertEquals(4, ai.moneyMillions);
        assertEquals(-12, ai.turnMoneyMillions);
    }

    @Test public void inactivePlayerDoesNotReceiveRecoveryOrSpecialIncome() throws Exception {
        GameState game = regularGame();
        GameState.PlayerState ai = game.getPlayerState(1);
        setSpecial(ai, true);
        ai.alive = false; ai.moneyMillions = -44; ai.turnMoneyMillions = -12;
        upkeep(game);
        assertEquals(-44, ai.moneyMillions);
        assertEquals(-12, ai.turnMoneyMillions);
    }

    private static GameState regularGame() throws Exception {
        GameState game = new GameState(1, 0L);
        for (GameState.PlayerState player : game.getPlayers()) setSpecial(player, false);
        return game;
    }

    private static void setSpecial(GameState.PlayerState player, boolean value) throws Exception {
        Field field = GameState.PlayerState.class.getDeclaredField("specialPerson");
        field.setAccessible(true);
        field.setBoolean(player, value);
    }

    private static void upkeep(GameState game) throws Exception {
        Method method = GameState.class.getDeclaredMethod("updateTurnUpkeep");
        method.setAccessible(true);
        method.invoke(game);
    }
}
