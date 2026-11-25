package com.example.worlddomination.game;

import com.example.worlddomination.game.persistence.ScorePreferenceBackend;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.SourceActionMenuState;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import static com.example.worlddomination.game.simulation.GameState.Action.DIPLOMACY;
import static com.example.worlddomination.game.simulation.GameState.Action.MASS_MEDIA;
import static com.example.worlddomination.game.simulation.GameState.Action.PRODUCTION;
import static com.example.worlddomination.game.simulation.GameState.Action.RESOURCES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class EnemyPortraitDiplomacyTest {
    private WorldDominationGame view;
    private GameState game;
    private SourceActionMenuState menu;
    private Method activatePortrait;

    @Before public void start() throws Exception {
        GameSetup setup = new GameSetup(17L);
        assertTrue(setup.toggleOpponent(5));
        assertTrue(setup.toggleOpponent(9));
        assertTrue(setup.toggleOpponent(14));
        game = new GameState(setup);
        view = new WorldDominationGame(new ScorePreferenceBackend() {
            @Override public long getLong(String key, long fallback) { return fallback; }
            @Override public void putLong(String key, long value) { }
            @Override public void flush() { }
        });
        field(WorldDominationGame.class, "game").set(view, game);
        menu = (SourceActionMenuState) field(WorldDominationGame.class, "sourceActionMenu").get(view);
        activatePortrait = WorldDominationGame.class.getDeclaredMethod("activateDiplomacyPortrait", int.class);
        activatePortrait.setAccessible(true);
    }

    @Test public void opensFromAnotherActionAndRetargetsWithoutResettingOption() throws Exception {
        game.selectAction(PRODUCTION);
        tapPortrait(2);
        assertEquals(DIPLOMACY, game.getSelectedAction());
        assertEquals("diplomacy", menu.getKey());
        assertEquals(2, game.getSelectedTargetPlayer());
        assertTrue(menu.hasLeader());
        assertFalse(menu.isAcceptVisible());

        assertTrue(menu.chooseOption(game, 3));
        tapPortrait(3);
        tapPortrait(3);
        assertEquals(DIPLOMACY, game.getSelectedAction());
        assertEquals(3, game.getSelectedTargetPlayer());
        assertEquals(3, game.getSelectedDiplomacyOption());
        assertTrue(menu.isAcceptVisible());
        assertEquals(0, game.getQueuedActionCount());
    }

    @Test public void defeatedAndCeasefirePortraitsOpenWithoutPickingAnUnavailableRival() throws Exception {
        game.getPlayerState(2).alive = false;
        int[] ceasefireTurns = (int[]) field(GameState.PlayerState.class, "ceasefireTurns").get(game.getHuman());
        ceasefireTurns[3] = 2;

        for (int rival : new int[]{2, 3}) {
            game.cancelSelection();
            tapPortrait(rival);
            assertEquals(DIPLOMACY, game.getSelectedAction());
            assertFalse(menu.hasLeader());
            assertFalse(menu.isAcceptVisible());
        }
        tapPortrait(1);
        assertTrue(menu.chooseOption(game, 3));
        tapPortrait(2);
        tapPortrait(3);
        assertEquals(1, game.getSelectedTargetPlayer());
        assertTrue(menu.isAcceptVisible());
    }

    @Test public void incomingProposalBlocksThePortraitShortcut() throws Exception {
        assertTrue(game.submitDiplomacyProposal(1, 0, 3));
        tapPortrait(2);
        assertNull(game.getSelectedAction());
        assertNotNull(game.getPendingHumanDiplomacyProposal());
        assertFalse(menu.hasLeader());
    }

    @Test public void contaminationAndFullQueueBlockOpeningDiplomacy() throws Exception {
        @SuppressWarnings("unchecked")
        Set<GameState.Action> contaminated = (Set<GameState.Action>) field(GameState.PlayerState.class,
                "bioloadedActions").get(game.getHuman());
        contaminated.add(DIPLOMACY);
        game.selectAction(PRODUCTION);
        tapPortrait(2);
        assertEquals(PRODUCTION, game.getSelectedAction());
        contaminated.clear();

        queue(RESOURCES);
        queue(MASS_MEDIA);
        tapPortrait(2);
        assertNull(game.getSelectedAction());
        assertEquals(RESOURCES, game.getQueuedAction(0));
        assertEquals(MASS_MEDIA, game.getQueuedAction(1));
    }

    @Test public void reselectingQueuedDiplomacyOpensItsTubeAndRefundsTheOrder() throws Exception {
        queue(RESOURCES);
        int funds = game.getHuman().turnMoneyMillions;
        tapPortrait(2);
        assertTrue(menu.chooseOption(game, 0));
        assertTrue(menu.accept(game));
        assertEquals(DIPLOMACY, game.getQueuedAction(1));
        assertEquals(funds - game.getDiplomacyOptionAmount(0), game.getHuman().turnMoneyMillions);

        tapPortrait(3);
        assertNull(game.getSelectedAction());
        assertEquals(RESOURCES, game.getQueuedAction(0));
        assertNull(game.getQueuedAction(1));
        assertEquals(funds, game.getHuman().turnMoneyMillions);
    }

    private void queue(GameState.Action action) {
        game.selectAction(action);
        assertTrue(game.acceptSelectedAction());
    }

    private void tapPortrait(int playerId) throws Exception {
        activatePortrait.invoke(view, playerId);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
