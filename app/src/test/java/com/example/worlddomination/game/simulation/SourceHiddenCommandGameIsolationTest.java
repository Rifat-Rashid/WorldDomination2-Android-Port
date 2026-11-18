package com.example.worlddomination.game.simulation;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source adapter command flags belong to presentation and must not change rules. */
public final class SourceHiddenCommandGameIsolationTest {
    @Test public void pendingCheatsDoNotChangeCanonicalTurnOrRandomStream() {
        GameState baseline = new GameState(1, 17L);
        GameState commanded = new GameState(1, 17L);
        assertEquals(canonical(baseline), canonical(commanded));

        SourceHiddenCommands flags = commanded.getHiddenCommands();
        flags.submit("dexter");
        flags.submit("gimmegimme");
        flags.submit("trump");
        flags.submit("psycho killer");
        assertTrue(flags.isScoreCorrupt());
        assertEquals("submitting commands must not mutate canonical players or RNG",
                canonical(baseline), canonical(commanded));

        for (GameState game : new GameState[] {baseline, commanded}) {
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue(game.acceptSelectedAction());
            game.selectAction(GameState.Action.MASS_MEDIA);
            assertTrue(game.acceptSelectedAction());
            game.confirmTurn();
        }
        assertEquals("pending presentation cheats must not alter normal turn results",
                canonical(baseline), canonical(commanded));
        assertTrue(flags.isActive(SourceHiddenCommands.Flag.TECH));
        assertTrue(flags.isActive(SourceHiddenCommands.Flag.STOCK));
        assertTrue(flags.isActive(SourceHiddenCommands.Flag.CASH));
        assertTrue(flags.isActive(SourceHiddenCommands.Flag.KILL_ALL));

        SourceHiddenCommands privateCopy = flags.copy();
        privateCopy.submit("trump");
        assertFalse(privateCopy.isActive(SourceHiddenCommands.Flag.CASH));
        assertTrue(flags.isActive(SourceHiddenCommands.Flag.CASH));
        assertEquals(canonical(baseline), canonical(commanded));
    }

    private static String canonical(GameState game) {
        StringBuilder value = new StringBuilder();
        value.append(game.getTurn()).append('|').append(game.getMatchStatus())
                .append('|').append(game.createPresentationRandomCopy().nextDouble());
        for (GameState.PlayerState player : game.getPlayers()) {
            value.append('|').append(player.id).append(':').append(player.alive)
                    .append(':').append(player.moneyMillions)
                    .append(':').append(player.turnMoneyMillions)
                    .append(':').append(player.populationMillions)
                    .append(':').append(player.religion)
                    .append(':').append(player.score)
                    .append(':').append(player.getInventedTechnologies());
            for (CityState city : player.getCities()) {
                value.append(':').append(city.populationMillions);
            }
            for (Map.Entry<String, GameState.WeaponState> weapon : player.getWeapons().entrySet()) {
                value.append(':').append(weapon.getKey()).append('=')
                        .append(weapon.getValue().inStock).append('/')
                        .append(weapon.getValue().active);
            }
            for (int other = 0; other < 5; other++) {
                value.append(':').append(player.getRegardTowardExact(other))
                        .append('/').append(player.getCeasefireTurnsWith(other));
            }
        }
        return value.toString();
    }
}
