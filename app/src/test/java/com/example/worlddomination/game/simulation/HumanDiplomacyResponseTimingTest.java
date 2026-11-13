package com.example.worlddomination.game.simulation;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Source Presenter_interface queues answers for the following turn result phase. */
public final class HumanDiplomacyResponseTimingTest {
    @Test public void seedZeroSatelliteTurnKeepsAiPlanBeforeHumanOfferRefund() {
        GameSetup setup = new GameSetup(0L);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        String[] research = {"rocket2", "rocket3", "rocket4", "warhead2",
                "warhead3", "warhead4", "bioload2", "bioload3", "bioload4",
                "soldier2", "soldier3", "soldier4", "sub", "ship", "space",
                "specialLoad"};
        int answered = 0;
        for (String technology : research) {
            answered += rejectPending(game);
            game.selectAction(GameState.Action.RESEARCH);
            assertTrue(technology, game.selectResearchTechnology(technology));
            assertTrue(game.acceptSelectedAction());
            resources(game);
            game.confirmTurn();
        }
        for (int turn = 17; turn <= 26; turn++) {
            answered += rejectPending(game);
            game.selectAction(GameState.Action.PRODUCTION);
            assertTrue(game.configureProduction("special", 100));
            assertTrue(game.acceptSelectedAction());
            resources(game);
            game.confirmTurn();
        }
        assertEquals(26, game.getTurn());
        GameState.DiplomacyProposal proposal = game.getPendingHumanDiplomacyProposal();
        assertNotNull(proposal);
        assertEquals(2, proposal.proposerId);
        assertEquals(1, proposal.optionIndex);
        GameState.PlayerState proposer = game.getPlayerState(2);
        assertEquals("source offer has already reserved 50", 65, proposer.moneyMillions);
        double regardBefore = proposer.getRegardTowardExact(0);
        assertTrue(game.respondToDiplomacyProposal(false));
        answered++;
        assertEquals(null, game.getPendingHumanDiplomacyProposal());
        assertEquals("source queues rejection until turn results", 65,
                proposer.moneyMillions);
        assertEquals(regardBefore, proposer.getRegardTowardExact(0), 0d);

        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedCarrier("space"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();
        assertEquals(27, game.getTurn());
        assertTrue("source AI researches no technology on turn 27",
                !proposer.getLastTurnActions().contains(GameState.Action.RESEARCH));
        assertTrue(proposer.getLastTurnActions().contains(GameState.Action.RESOURCES));
        assertTrue(proposer.getLastTurnActions().contains(GameState.Action.MASS_MEDIA));

        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedLaunch(1, 0, "specialLoad"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();
        assertEquals(28, game.getTurn());
        assertEquals(2, answered);
        assertTrue(proposer.getLastTurnActions().contains(GameState.Action.PROPAGANDA));
        assertTrue(proposer.getLastTurnActions().contains(GameState.Action.PRODUCTION));
        GameState.CombatEvent satellite = null;
        for (GameState.CombatEvent event : game.getLastTurnCombatEvents()) {
            if (event.type == GameState.CombatEventType.STRIKE_IMPACT
                    && "space".equals(event.weaponName)) satellite = event;
        }
        assertNotNull(satellite);
        assertEquals(93, satellite.casualtiesMillions);
        assertEquals("source turn-28 battle order", 2, satellite.battleStartNum);
        List<GameState.BattlePresentationRecord> records = game.getLastTurnBattleRecords();
        assertEquals(4, records.size());
        assertEquals(GameState.Action.PREPARED_ATTACK, records.get(1).action);
    }

    @Test public void mixedHumanAnswersLeaveModalImmediatelyAndSettleInSourceGroups() {
        GameState game = new GameState(1, 22L, false);
        GameState.PlayerState first = game.getPlayerState(1);
        GameState.PlayerState second = game.getPlayerState(2);
        assertTrue(game.submitDiplomacyProposal(1, 0, 0));
        assertTrue(game.submitDiplomacyProposal(2, 0, 0));
        int humanMoney = game.getHuman().moneyMillions;
        int firstMoney = first.moneyMillions;
        int secondMoney = second.moneyMillions;
        double firstRegard = first.getRegardTowardExact(0);
        double secondRegard = second.getRegardTowardExact(0);
        assertEquals(1, game.getPendingHumanDiplomacyProposal().proposerId);
        assertTrue(game.respondToDiplomacyProposal(true));
        assertEquals(2, game.getPendingHumanDiplomacyProposal().proposerId);
        assertTrue(game.respondToDiplomacyProposal(false));
        assertEquals(null, game.getPendingHumanDiplomacyProposal());
        assertEquals(humanMoney, game.getHuman().moneyMillions);
        assertEquals(firstMoney, first.moneyMillions);
        assertEquals(secondMoney, second.moneyMillions);
        assertEquals(firstRegard, first.getRegardTowardExact(0), 0d);
        assertEquals(secondRegard, second.getRegardTowardExact(0), 0d);
        assertFalse(game.getHuman().hasCeasefireWith(1));

        resources(game);
        game.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        assertTrue(game.getHuman().hasCeasefireWith(1));
        assertFalse(game.getHuman().hasCeasefireWith(2));
        assertEquals(secondMoney + 10, second.moneyMillions);
        List<String> messages = game.getMessages(0);
        int rejected = messages.indexOf("Nation 0 rejected diplomacy with nation 2.");
        int accepted = messages.indexOf("Nation 0 accepted diplomacy with nation 1.");
        assertTrue(rejected >= 0);
        assertTrue(accepted > rejected);
    }

    @Test public void acceptedDemandsReserveEachPaymentBeforeTheNextModal() {
        GameState game = new GameState(1, 27L, false);
        game.getHuman().moneyMillions = 200;
        game.getHuman().turnMoneyMillions = 200;
        assertTrue(game.submitDiplomacyProposal(1, 0, 5));
        assertTrue(game.submitDiplomacyProposal(2, 0, 5));
        assertTrue(game.submitDiplomacyProposal(3, 0, 5));
        assertTrue(game.respondToDiplomacyProposal(true));
        assertEquals(110, game.getTurnMoneyMillions());
        assertEquals(200, game.getHuman().moneyMillions);
        assertTrue(game.respondToDiplomacyProposal(true));
        assertEquals(20, game.getTurnMoneyMillions());
        assertEquals(200, game.getHuman().moneyMillions);
        assertFalse(game.canAcceptPendingHumanDiplomacy());
        assertFalse(game.respondToDiplomacyProposal(true));
        assertEquals(3, game.getPendingHumanDiplomacyProposal().proposerId);
        assertTrue(game.respondToDiplomacyProposal(false));
        assertEquals(null, game.getPendingHumanDiplomacyProposal());
    }

    @Test public void acceptedDemandIsNotRevotedAfterWalletDropsBeforeSettlement() {
        GameState game = new GameState(1, 31L, false);
        GameState.PlayerState human = game.getHuman();
        GameState.PlayerState proposer = game.getPlayerState(1);
        human.moneyMillions = 100;
        human.turnMoneyMillions = 100;
        assertTrue(game.submitDiplomacyProposal(1, 0, 5)); // demand 90
        int proposerMoney = proposer.moneyMillions;
        assertTrue(game.respondToDiplomacyProposal(true));
        assertEquals(10, game.getTurnMoneyMillions());
        assertEquals(100, human.moneyMillions);
        assertEquals(null, game.getPendingHumanDiplomacyProposal());

        // Simulate a later loss, such as a spy theft, after the UI recorded
        // the answer. Source Presenter.updatePlayerVars_2 case 8 transfers the
        // agreed amount without testing the current wallet again.
        human.moneyMillions = 0;
        resources(game);
        game.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        assertEquals(proposerMoney + 90, proposer.moneyMillions);
        assertTrue(human.hasCeasefireWith(1));
        assertTrue(game.getMessages(0).contains(
                "Nation 0 accepted diplomacy with nation 1."));
    }

    private static int rejectPending(GameState game) {
        GameState.DiplomacyProposal proposal = game.getPendingHumanDiplomacyProposal();
        if (proposal == null) return 0;
        assertEquals(12, game.getTurn());
        assertEquals(1, proposal.proposerId);
        assertEquals(5, proposal.optionIndex);
        assertTrue(game.respondToDiplomacyProposal(false));
        assertEquals(null, game.getPendingHumanDiplomacyProposal());
        return 1;
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }
}
