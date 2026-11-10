package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;

/** Cached live values for the original Resources dialog, independent of its artwork. */
public final class ResourceMenuText {
    private int budget = Integer.MIN_VALUE;
    private int population = Integer.MIN_VALUE;
    private int income = Integer.MIN_VALUE;
    private String budgetText;
    private String populationText;
    private String incomeText;

    public void update(GameState state) {
        int nextBudget = state.getTurnMoneyMillions();
        int nextPopulation = state.getHuman().populationMillions;
        int nextIncome = state.getResourceIncomeMillions();
        if (budget != nextBudget) {
            budget = nextBudget;
            budgetText = "Budget: " + millions(budget);
        }
        if (population != nextPopulation) {
            population = nextPopulation;
            populationText = millions(population);
        }
        if (income != nextIncome) {
            income = nextIncome;
            incomeText = millions(income);
        }
    }

    public String getBudget() { return budgetText; }
    public String getPopulation() { return populationText; }
    public String getIncome() { return incomeText; }

    private static String millions(int value) {
        return value + ".000.000";
    }
}
