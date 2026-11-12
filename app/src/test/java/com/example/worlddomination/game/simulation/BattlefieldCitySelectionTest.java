package com.example.worlddomination.game.simulation;

import com.example.worlddomination.game.rendering.FlashColorTransform;
import com.example.worlddomination.game.ui.BattlefieldCitySelection;
import com.example.worlddomination.game.ui.BattlefieldCitySelection.Tint;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public final class BattlefieldCitySelectionTest {
    @Test public void sourceTargetingActionsColorOwnAndEnemyCities() {
        GameState game = new GameState(1, 7L, false);
        for (GameState.Action action : new GameState.Action[]{GameState.Action.ATTACK,
                GameState.Action.PREPARED_ATTACK, GameState.Action.PROPAGANDA}) {
            game.selectAction(action);
            for (int city = 0; city < 5; city++) {
                assertEquals(Tint.RED, BattlefieldCitySelection.tint(game, 0, city, null));
                for (int owner = 1; owner < 5; owner++)
                    assertEquals(Tint.GREEN, BattlefieldCitySelection.tint(game, owner, city, null));
            }
        }
    }

    @Test public void sourceSpySelectionIgnoresCeasefireButOrdinaryTargetsDoNot() throws Exception {
        GameState game = new GameState(1, 8L, false);
        Field field = GameState.PlayerState.class.getDeclaredField("ceasefireTurns");
        field.setAccessible(true);
        ((int[]) field.get(game.getHuman()))[1] = 2;
        game.selectAction(GameState.Action.ATTACK);
        assertEquals(Tint.RED, BattlefieldCitySelection.tint(game, 1, 0, "soldier1"));
        assertEquals(Tint.GREEN, BattlefieldCitySelection.tint(game, 1, 0, "spy1"));
        assertEquals(Tint.RED, BattlefieldCitySelection.tint(game, 0, 0, "spy1"));
        game.selectAction(GameState.Action.PROPAGANDA);
        assertEquals(Tint.RED, BattlefieldCitySelection.tint(game, 1, 0, null));
    }

    @Test public void redFlagPrecedesEmptyPopulationAndGreenRequiresPopulation() {
        GameState game = new GameState(1, 9L, false);
        game.getHuman().getCity(0).populationMillions = 0;
        game.getPlayerState(1).getCity(0).setPopulationMillions(0);
        game.getPlayerState(1).alive = false;
        game.selectAction(GameState.Action.PROPAGANDA);
        assertEquals(Tint.RED, BattlefieldCitySelection.tint(game, 0, 0, null));
        assertEquals(Tint.DEFAULT, BattlefieldCitySelection.tint(game, 1, 0, null));
    }

    @Test public void shipPayloadSelectionsRestrictTheOtherIslands() {
        GameState game = new GameState(1, 10L, false);
        game.getHuman().getWeapons().get("ship").active = true;
        game.getHuman().getWeapons().get("ship").inStock = 1;
        game.getHuman().getWeapons().get("warhead1").active = true;
        game.getHuman().getWeapons().get("warhead1").inStock = 3;
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertEquals(-1, game.getSelectedShipTargetPlayerId());
        assertTrue(game.configurePreparedCarrier("ship"));
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.SKIP);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedShipStrike(2, 0, "warhead1"));
        assertEquals(2, game.getSelectedShipTargetPlayerId());
        assertEquals(Tint.DEFAULT, BattlefieldCitySelection.tint(game, 2, 0, "warhead1"));
        assertEquals(Tint.RED, BattlefieldCitySelection.tint(game, 1, 0, "warhead1"));
        assertEquals(Tint.RED, BattlefieldCitySelection.tint(game, 3, 0, "warhead1"));
    }

    @Test public void cancellationEconomyMenusAndUnusedSlotsHaveNoTint() {
        GameSetup setup = new GameSetup(); setup.toggleOpponent(0);
        GameState game = new GameState(setup, 11L);
        game.selectAction(GameState.Action.PROPAGANDA);
        assertEquals(Tint.DEFAULT, BattlefieldCitySelection.tint(game, 2, 0, null));
        game.cancelSelection();
        assertEquals(Tint.DEFAULT, BattlefieldCitySelection.tint(game, 0, 0, null));
        game.selectAction(GameState.Action.RESOURCES);
        assertEquals(Tint.DEFAULT, BattlefieldCitySelection.tint(game, 1, 0, null));
        assertEquals(Tint.DEFAULT, BattlefieldCitySelection.tint(game, -1, 0, null));
    }

    @Test public void exactSourceCityPaletteRetainsAdditiveChannelsAndAlpha() {
        assertEquals(1f, FlashColorTransform.CITY_RED.redMultiplier, 0f);
        assertEquals(200 / 255f, FlashColorTransform.CITY_RED.redAdd, 0f);
        assertEquals(.75f, FlashColorTransform.CITY_RED.alphaMultiplier, 0f);
        assertEquals(.5f, FlashColorTransform.CITY_GREEN.redMultiplier, 0f);
        assertEquals(100 / 255f, FlashColorTransform.CITY_GREEN.greenAdd, 0f);
        assertEquals(.5f, FlashColorTransform.CITY_GREEN.blueMultiplier, 0f);
        assertEquals(.75f, FlashColorTransform.CITY_GREEN.alphaMultiplier, 0f);
    }
}
