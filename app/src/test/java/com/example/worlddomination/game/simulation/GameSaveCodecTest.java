package com.example.worlddomination.game.simulation;

import com.example.worlddomination.game.persistence.GameSaveCodec;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.CRC32;

import static com.example.worlddomination.game.simulation.GameState.Action.MASS_MEDIA;
import static com.example.worlddomination.game.simulation.GameState.Action.PREPARED_ATTACK;
import static com.example.worlddomination.game.simulation.GameState.Action.PRODUCTION;
import static com.example.worlddomination.game.simulation.GameState.Action.RESEARCH;
import static com.example.worlddomination.game.simulation.GameState.Action.RESOURCES;
import static com.example.worlddomination.game.simulation.GameState.Action.SKIP;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class GameSaveCodecTest {
    @Test
    public void queuedSliderResumesSourceTurnAndBothRandomStreams() throws IOException {
        GameSetup setup = new GameSetup(0L);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState uninterrupted = new GameState(setup);
        uninterrupted.selectAction(PRODUCTION);
        assertTrue(uninterrupted.configureProductionSlider("rockets", 50));
        assertEquals(32.5, uninterrupted.getProductionSliderBudgetMillions("rockets"), 0.0);
        assertTrue(uninterrupted.acceptSelectedAction());
        queue(uninterrupted, RESOURCES);
        GameState restored = restore(uninterrupted);
        assertEquals(32, restored.getTurnMoneyMillions());
        assertEquals(65, restored.getHuman().moneyMillions);
        assertEquals(2, restored.getQueuedActionCount());
        assertEquals(PRODUCTION, restored.getQueuedAction(0));
        assertEquals(RESOURCES, restored.getQueuedAction(1));
        assertEquals(uninterrupted.getHuman().getPlayerColor(), restored.getHuman().getPlayerColor());
        assertEquals(1, restored.getPlayerState(2).getLeaderId());
        assertFalse(restored.getPlayerState(3).alive);

        uninterrupted.confirmTurn();
        restored.confirmTurn();
        assertEquals(5, restored.getWeaponStock("rocket1"));
        assertEquals(86, restored.getHuman().moneyMillions);
        assertEquals(17181L, restored.getHuman().score);
        assertEquals(0.24013040782635398, restored.createPresentationRandomCopy().nextDouble(), 0.0);
        assertTrue(restored.getMessages().contains("Money returned from production: 13.000.000"));
        assertSameStream(uninterrupted.createOutcomePresentationRandomCopy(),
                restored.createOutcomePresentationRandomCopy());
        assertSameStream(uninterrupted.createPresentationRandomCopy(),
                restored.createPresentationRandomCopy());
        assertArrayEquals(GameSaveCodec.encode(uninterrupted), GameSaveCodec.encode(restored));

        restored = restore(restored);
        assertTrue(restored.getLastTurnBattleRecords().isEmpty());
        assertEquals(uninterrupted.getMessages(), restored.getMessages());
        queue(uninterrupted, RESOURCES);
        queue(uninterrupted, MASS_MEDIA);
        queue(restored, RESOURCES);
        queue(restored, MASS_MEDIA);
        uninterrupted.confirmTurn();
        restored.confirmTurn();
        assertArrayEquals(GameSaveCodec.encode(uninterrupted), GameSaveCodec.encode(restored));
    }

    @Test
    public void sparseSlotBudgetAndDiplomacyResponseOrderSurviveReload() throws IOException {
        GameState uninterrupted = new GameState(1, 6L, false);
        assertTrue(uninterrupted.submitDiplomacyProposal(1, 0, 3));
        assertTrue(uninterrupted.respondToDiplomacyProposal(true));
        assertTrue(uninterrupted.submitDiplomacyProposal(2, 0, 0));
        assertTrue(uninterrupted.respondToDiplomacyProposal(false));
        assertTrue(uninterrupted.submitDiplomacyProposal(4, 0, 0));
        assertTrue(uninterrupted.submitDiplomacyProposal(3, 0, 0));
        queue(uninterrupted, SKIP);
        uninterrupted.selectAction(RESEARCH);
        assertTrue(uninterrupted.selectResearchTechnology("soldier2"));
        assertTrue(uninterrupted.acceptSelectedAction());
        uninterrupted.removeQueuedAction(0);

        GameState restored = restore(uninterrupted);
        assertEquals(45, restored.getTurnMoneyMillions());
        assertNull(restored.getQueuedAction(0));
        assertEquals(RESEARCH, restored.getQueuedAction(1));
        GameState refundable = restore(restored);
        refundable.removeQueuedAction(1);
        assertEquals(55, refundable.getTurnMoneyMillions());
        for (GameState game : Arrays.asList(uninterrupted, restored)) {
            assertEquals(4, game.getPendingHumanDiplomacyProposal().proposerId);
            assertTrue(game.respondToDiplomacyProposal(false));
            assertEquals(3, game.getPendingHumanDiplomacyProposal().proposerId);
            assertTrue(game.respondToDiplomacyProposal(false));
            queue(game, SKIP);
            game.confirmTurn();
            assertTrue(game.getHuman().hasTechnology("soldier2"));
            assertEquals(2, game.getHuman().getCeasefireTurnsWith(1));
            assertFalse(game.getHuman().hasCeasefireWith(2));
        }
        assertArrayEquals(GameSaveCodec.encode(uninterrupted), GameSaveCodec.encode(restored));
    }

    @Test
    public void restoredPreparedShipKeepsPayloadOrderAndCanonicalCityFlags() throws IOException {
        GameState uninterrupted = new GameState(1, 207L, false);
        GameState.PlayerState human = uninterrupted.getHuman();
        human.getWeapons().get("ship").active = true;
        human.getWeapons().get("ship").inStock = 1;
        human.getWeapons().get("warhead1").inStock = 2;
        human.getWeapons().get("bioload1").inStock = 1;
        GameState.WeaponState stolen = human.getWeapons().get("rocket1");
        stolen.inStock = -1;
        stolen.investmentMillions = 3;

        uninterrupted.selectAction(PREPARED_ATTACK);
        assertTrue(uninterrupted.configurePreparedCarrier("ship"));
        assertTrue(uninterrupted.acceptSelectedAction());
        queue(uninterrupted, RESOURCES);
        uninterrupted.confirmTurn();
        uninterrupted.selectAction(PREPARED_ATTACK);
        assertTrue(uninterrupted.configurePreparedShipStrike(1, 0, "warhead1"));
        assertTrue(uninterrupted.configurePreparedShipStrike(1, 1, "warhead1"));
        assertTrue(uninterrupted.configurePreparedShipStrike(1, 2, "bioload1"));
        assertTrue(uninterrupted.acceptSelectedAction());
        queue(uninterrupted, RESOURCES);

        GameState.PlayerState untouchedNation = uninterrupted.getPlayerState(2);
        CityState ruined = untouchedNation.getCity(0);
        untouchedNation.populationMillions -= ruined.populationMillions;
        ruined.setPopulationMillions(0);
        ruined.bioloadInBattle = true;
        uninterrupted.getHiddenCommands().submit("free willy");

        GameState restored = restore(uninterrupted);
        assertTrue(restored.getHiddenCommands().isScoreCorrupt());
        assertTrue(restored.getHiddenCommands().isActive(SourceHiddenCommands.Flag.ORCA));
        assertEquals(ruined.getDeathFrame(), restored.getPlayerState(2).getCity(0).getDeathFrame());
        assertTrue(restored.getPlayerState(2).getCity(0).bioloadInBattle);
        assertEquals(-1, restored.getWeaponStock("rocket1"));
        assertEquals(3, restored.getHuman().getWeapons().get("rocket1").investmentMillions);
        assertEquals("ship", restored.getPreparedCarrierName(0));
        assertEquals(3, restored.getQueuedShipStrikeCount(0));
        assertEquals(2, restored.getQueuedShipStrikeCity(0, 2));
        uninterrupted.confirmTurn();
        restored.confirmTurn();
        assertEquals(0, restored.getWeaponStock("warhead1"));
        assertEquals(0, restored.getWeaponStock("bioload1"));
        assertArrayEquals(GameSaveCodec.encode(uninterrupted), GameSaveCodec.encode(restored));
    }

    @Test
    public void draftSelectionsAndUnknownCommandScoreFlagRoundTrip() throws IOException {
        GameState game = new GameState(1, 41L, false);
        assertTrue(game.selectBattlefieldCity(3, 1));
        game.selectAction(PRODUCTION);
        assertTrue(game.configureProductionSlider("soldier", 29));
        assertTrue(game.configureProductionSlider("defense", 13));
        assertFalse(game.getHiddenCommands().submit("unknown").recognized);
        GameState restored = restore(game);
        assertEquals(PRODUCTION, restored.getSelectedAction());
        assertEquals(3, restored.getSelectedMapCityOwner());
        assertEquals(1, restored.getSelectedMapCityIndex());
        assertEquals(29, restored.getProductionPercent("soldier"));
        assertEquals(13, restored.getProductionPercent("defense"));
        assertTrue(restored.getHiddenCommands().isScoreCorrupt());
        for (SourceHiddenCommands.Flag flag : SourceHiddenCommands.Flag.values()) {
            assertFalse(restored.getHiddenCommands().isActive(flag));
        }
        assertTrue(restored.acceptSelectedAction());
        assertEquals(1, restored.getQueuedActionCount());
    }

    @Test
    public void malformedVersionsPayloadsAndRandomTypesAreRejected() throws IOException {
        byte[] valid = GameSaveCodec.encode(new GameState(1, 3L));
        assertThrows(IOException.class, () -> GameSaveCodec.decode(Arrays.copyOf(valid, 30)));
        assertThrows(IOException.class,
                () -> GameSaveCodec.decode(new byte[GameSaveCodec.MAX_SAVE_BYTES + 1]));
        byte[] wrongVersion = valid.clone();
        wrongVersion[7] = 2;
        assertThrows(IOException.class, () -> GameSaveCodec.decode(wrongVersion));
        byte[] corrupt = valid.clone();
        corrupt[corrupt.length - 1] ^= 1;
        assertThrows(IOException.class, () -> GameSaveCodec.decode(corrupt));
        byte[] invalidDifficulty = valid.clone();
        invalidDifficulty[15] = 9;
        CRC32 checksum = new CRC32();
        checksum.update(invalidDifficulty, 12, invalidDifficulty.length - 12);
        int value = (int) checksum.getValue();
        for (int offset = 11; offset >= 8; offset--) {
            invalidDifficulty[offset] = (byte) value;
            value >>>= Byte.SIZE;
        }
        assertThrows(IOException.class, () -> GameSaveCodec.decode(invalidDifficulty));
        assertThrows(IOException.class, () -> GameSaveCodec.encode(new GameState(1, new Random(3L))));
    }

    private static GameState restore(GameState game) throws IOException {
        byte[] original = GameSaveCodec.encode(game);
        GameState restored = GameSaveCodec.decode(original);
        assertArrayEquals(original, GameSaveCodec.encode(restored));
        return restored;
    }

    private static void queue(GameState game, GameState.Action action) {
        game.selectAction(action);
        assertTrue(game.acceptSelectedAction());
    }

    private static void assertSameStream(ReferenceRandom expected, ReferenceRandom actual) {
        // More than 624 words proves the saved ring continues through another twist.
        for (int draw = 0; draw < 350; draw++) {
            assertEquals(expected.nextDouble(), actual.nextDouble(), 0.0);
        }
    }
}
