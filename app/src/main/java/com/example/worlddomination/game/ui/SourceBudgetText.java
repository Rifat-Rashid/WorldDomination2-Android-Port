package com.example.worlddomination.game.ui;

/** Source Sub_model.display_turnMoney and Sub_Presenter_interface.display_textFunds. */
public final class SourceBudgetText {
    private SourceBudgetText() { }

    public static String displayTurnMoney(int turnMoneyMillions) {
        return turnMoneyMillions > 0 ? turnMoneyMillions + ".000.000" : "$ 0";
    }

    public static String displayTextFunds(int turnMoneyMillions) {
        return "Budget: " + displayTurnMoney(turnMoneyMillions);
    }
}
