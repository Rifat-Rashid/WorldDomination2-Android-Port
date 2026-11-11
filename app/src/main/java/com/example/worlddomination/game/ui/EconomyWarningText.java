package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.TechnologyCatalog;

/** Exact full-version Presenter_interface warning messages and precedence. */
public final class EconomyWarningText {
    public static final String PRODUCTION_LOCKED = "You have not yet invented this Technology";
    public static final String RESEARCH_FUNDS = "Not enough money!";
    public static final String RESEARCH_SPECIAL = "You need to invent   4 complete technologies before you can start inventing special weapons.";
    private EconomyWarningText() { }

    public static String research(GameState game, String technology) {
        if (game.getSelectedAction() != GameState.Action.RESEARCH
                || !game.getResearchChoices().contains(technology)
                || game.isResearchTechnologySelected(technology)) return null;
        TechnologyCatalog.Definition definition = TechnologyCatalog.byName(technology);
        if (definition.group == 7 && !game.getHuman().allowsSpecialTechnologies()) return RESEARCH_SPECIAL;
        if (definition.costMillions > game.getTurnMoneyMillions() - game.getSelectedResearchCostMillions())
            return RESEARCH_FUNDS;
        return null;
    }
}
