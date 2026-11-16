package com.example.worlddomination.game.simulation;

import org.junit.Test;
import static org.junit.Assert.*;

/** UI callback captures: Presenter_interface.setSliderValue and Presenter.updatePlayerVars_3. */
public final class SourceHumanProductionSliderBudgetTest {
    @Test public void legalSliderHalfBudgetMatchesSourceFullTurnAndRandomStream() {
        GameState game=sourceSetup();
        assertEquals(65,game.getHuman().moneyMillions);
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProductionSlider("rockets",50));
        assertEquals(32.5d,game.getProductionSliderBudgetMillions("rockets"),0d);
        assertEquals(32.5d,game.getSelectedProductionSliderBudgetMillions(),0d);
        assertEquals(33,game.getSelectedProductionTotalBudgetMillions());
        assertTrue(game.acceptSelectedAction());
        assertEquals(65,game.getHuman().moneyMillions);
        assertEquals(32,game.getTurnMoneyMillions());
        game.selectAction(GameState.Action.RESOURCES); assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        assertEquals(5,game.getWeaponStock("rocket1"));
        assertEquals(86,game.getHuman().moneyMillions);
        assertEquals(86,game.getTurnMoneyMillions());
        assertEquals(17181L,game.getHuman().score);
        assertEquals(0.24013040782635398d,game.createPresentationRandomCopy().nextDouble(),0d);
        assertTrue(game.getMessages().contains("Money returned from production: 13.000.000"));
    }

    @Test public void sourceSplitRoundsReservationOnceAndNeverCreditsUnusedBudget() {
        GameState game=sourceSetup(); game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProductionSlider("rockets",50));
        assertTrue(game.configureProductionSlider("soldier",50));
        assertEquals(32.5d,game.getProductionSliderBudgetMillions("rockets"),0d);
        assertEquals(32.5d,game.getProductionSliderBudgetMillions("soldier"),0d);
        assertEquals(65,game.getSelectedProductionTotalBudgetMillions());
        assertTrue(game.acceptSelectedAction());
        assertEquals(0,game.getTurnMoneyMillions()); assertEquals(65,game.getHuman().moneyMillions);
        game.selectAction(GameState.Action.RESOURCES); assertTrue(game.acceptSelectedAction()); game.confirmTurn();
        assertEquals(5,game.getWeaponStock("rocket1")); assertEquals(5,game.getWeaponStock("soldier1"));
        assertEquals(66,game.getHuman().moneyMillions); assertEquals(66,game.getTurnMoneyMillions());
        assertEquals(25254L,game.getHuman().score);
        assertEquals(0.46689223535252355d,game.createPresentationRandomCopy().nextDouble(),0d);
        assertTrue(game.getMessages().contains("Money returned from production: 26.000.000"));
    }

    @Test public void rawSourceOperationOrderIsPreservedAt116() {
        GameState game=sourceSetup(); game.getHuman().turnMoneyMillions=116;
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProductionSlider("rockets",50));
        assertEquals(57.99999999999999d,game.getSelectedProductionSliderBudgetMillions(),0d);
        assertEquals(57,game.getSelectedProductionBudgetMillions());
        assertTrue(game.configureProductionSlider("rockets",100));
        assertEquals(115.99999999999999d,game.getSelectedProductionSliderBudgetMillions(),0d);
        assertEquals(116,game.getSelectedProductionTotalBudgetMillions());
    }

    @Test public void displayedPercentageTotalAndRowSumReservationKeepTheirOwnRounding() {
        GameState game=sourceSetup();game.getHuman().turnMoneyMillions=3;
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProductionSlider("rockets",5));
        assertTrue(game.configureProductionSlider("soldier",45));
        assertEquals(2,game.getSelectedProductionSliderDisplayTotalBudgetMillions());
        assertEquals(1,game.getSelectedProductionTotalBudgetMillions());
    }

    @Test public void cancelAndRemovalRestoreOnlyRoundedReservation() {
        GameState game=sourceSetup();game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProductionSlider("rockets",50));game.cancelSelection();
        assertEquals(65,game.getTurnMoneyMillions());
        game.selectAction(GameState.Action.PRODUCTION);assertTrue(game.configureProductionSlider("rockets",50));
        assertTrue(game.acceptSelectedAction());assertEquals(32,game.getTurnMoneyMillions());
        game.removeQueuedAction(0);assertEquals(65,game.getTurnMoneyMillions());assertEquals(65,game.getHuman().moneyMillions);
    }

    @Test public void syntheticZeroAndSubUnitSliderBudgetsStillVisitSourceProduction() {
        // Source UI capture forces only the initial money/turnMoney to 0 or 1;
        // all action selection, results and RNG are otherwise natural.
        for (int funds : new int[]{0, 1}) {
            GameState game=sourceSetup();
            game.getHuman().moneyMillions=funds; game.getHuman().turnMoneyMillions=funds;
            double before=game.createPresentationRandomCopy().nextDouble();
            game.selectAction(GameState.Action.PRODUCTION);
            assertTrue(game.configureProductionSlider("rockets",20));
            assertEquals(funds == 0 ? 0d : .2d,game.getSelectedProductionSliderBudgetMillions(),0d);
            assertEquals(0,game.getSelectedProductionTotalBudgetMillions());
            assertTrue(game.acceptSelectedAction()); assertEquals(funds,game.getTurnMoneyMillions());
            assertEquals(before,game.createPresentationRandomCopy().nextDouble(),0d);
            game.selectAction(GameState.Action.RESOURCES);assertTrue(game.acceptSelectedAction());
            game.confirmTurn();
            assertEquals(funds+41,game.getHuman().moneyMillions);
            assertEquals(funds+41,game.getTurnMoneyMillions());
            assertEquals(1,game.getWeaponStock("rocket1"));assertEquals(9102L,game.getHuman().score);
            assertEquals(0.18031790152968785d,game.createPresentationRandomCopy().nextDouble(),0d);
            assertFalse(game.getMessages().contains("Money returned from production: 1.000.000"));
        }
    }

    @Test public void defenseSliderBelowCostReturnsCapturedUnusedBudget() {
        assertDefenseTurn(65,20,13d,13,106,9102L,2,13,0.18031790152968785d);
    }

    @Test public void defenseSliderFractionalBudgetMatchesCapturedPurchase() {
        assertDefenseTurn(65,30,19.5d,20,91,11122L,3,5,0.5036365052098872d);
    }

    @Test public void defenseSliderAtExactCostBuysCounterInSyntheticFundsCase() {
        // Source capture changes only starting money/turnMoney to 50.
        assertDefenseTurn(50,30,15d,15,76,11122L,3,0,0.5036365052098872d);
    }

    private static void assertDefenseTurn(int funds,int percentage,double raw,int reserved,
                                          int finalMoney,long score,int rocketStock,
                                          int returned,double nextRandom) {
        GameState game=sourceSetup();
        game.getHuman().moneyMillions=funds; game.getHuman().turnMoneyMillions=funds;
        assertEquals(2,game.getWeaponStock("antiAir")); assertEquals(2,game.getWeaponStock("antiRocket"));
        assertEquals(.8050278270130223d,game.createPresentationRandomCopy().nextDouble(),0d);
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProductionSlider("defense",percentage));
        assertEquals(raw,game.getSelectedProductionSliderBudgetMillions(),0d);
        assertEquals(reserved,game.getSelectedProductionTotalBudgetMillions());
        assertTrue(game.acceptSelectedAction());
        assertEquals(funds,game.getHuman().moneyMillions);
        assertEquals(funds-reserved,game.getTurnMoneyMillions());
        assertEquals(.8050278270130223d,game.createPresentationRandomCopy().nextDouble(),0d);
        game.selectAction(GameState.Action.RESOURCES);assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        assertEquals(finalMoney,game.getHuman().moneyMillions);assertEquals(finalMoney,game.getTurnMoneyMillions());
        assertEquals(2,game.getWeaponStock("antiAir"));assertEquals(rocketStock,game.getWeaponStock("antiRocket"));
        assertEquals(score,game.getHuman().score);
        assertEquals(nextRandom,game.createPresentationRandomCopy().nextDouble(),0d);
        String message="Money returned from production: "+returned+".000.000";
        if(returned>0) assertTrue(game.getMessages().contains(message));
        else assertFalse(game.getMessages().contains(message));
    }

    @Test public void scriptedCompatibilityKeepsFlooredCategoryAmount() {
        GameState game=sourceSetup();game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction("rockets",50));assertEquals(32,game.getSelectedProductionBudgetMillions());
        assertTrue(game.acceptSelectedAction());game.selectAction(GameState.Action.RESOURCES);assertTrue(game.acceptSelectedAction());game.confirmTurn();
        assertTrue(game.getMessages().contains("Money returned from nation 0 production: 12.000.000"));
        assertFalse(game.getMessages().contains("Money returned from production: 13.000.000"));
    }
    @Test public void actualUiCaptureBytesArePinned() throws Exception {
        assertCaptureSha("source-production-slider-defense.json", "79d409ed0d934f49dade1ee9b29c51dc6be501e86b95dc4a04dba1dc3ced3a57");
        assertCaptureSha("source-production-slider-half.json", "c2b3fd3eb024e480b1720a20f206e05029abd39aeaa6ce9aabd0ad93fcfb9923");
        assertCaptureSha("source-production-slider-provenance.json", "40749a9df21dd69a738f49d91385915a90abfd82e0f8258aeb3a03c02aae4930");
        assertCaptureSha("source-production-slider-split.json", "6c69c7a9c6f9310bca843d939b55f7b22bd62bf14d8a584e9962a9953e56f773");
        assertCaptureSha("source-production-slider-zero.json", "21aff351a7782a7296de876fa8707b5f3d932225099a1d71e40d1628c066ef99");
    }
    private static void assertCaptureSha(String name,String expected) throws Exception {
        try(java.io.InputStream in=SourceHumanProductionSliderBudgetTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(name,in);
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
            StringBuilder hex=new StringBuilder();
            for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            assertEquals(name,expected,hex.toString());
        }
    }
    private static GameState sourceSetup() {
        GameSetup setup=new GameSetup(0L);setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));assertTrue(setup.toggleOpponent(1));
        return new GameState(setup);
    }
}
