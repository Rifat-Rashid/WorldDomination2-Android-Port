package com.example.worlddomination.game.ui;

/** Original Help_data order and View_interface callbacks; help is never a turn action. */
public final class SourceHelpState {
    private static final String[] TOPICS = {
        "help_index", "help_weapondetail", "help_actiondetails", "help_overview",
        "help_interface", "help_interface2", "help_interface3", "help_interface4",
        "help_attacking", "help_attacking2", "help_attacking3", "help_rockets",
        "help_aircraft", "help_subs", "help_carrier", "help_space", "help_warheads",
        "help_infantrie", "help_intelligence", "help_resources", "help_production",
        "help_technology", "help_massmedia", "help_directattack", "help_preattack",
        "help_defense", "help_propaganda", "help_diplomacy"
    };
    private static final String[][] LINKS = {
        {"btn_overview", "help_overview", "btn_interface", "help_interface",
         "btn_attacking", "help_attacking", "btn_weapondetails", "help_weapondetail",
         "btn_actiondetails", "help_actiondetails"},
        {"btn_rockets", "help_rockets", "btn_aircraft", "help_aircraft",
         "btn_specialcarriers", "help_subs", "btn_warheads", "help_warheads",
         "btn_infanterie", "help_infanterie", "btn_intelligence", "help_intelligence",
         "btn_antiweapons", "help_antiweapons"},
        {"btn_resources", "help_resources", "btn_production", "help_production",
         "btn_technology", "help_technology", "btn_massmedia", "help_massmedia",
         "btn_directattack", "help_directattack", "btn_preparedattack", "help_preattack",
         "btn_defense", "help_defense", "btn_propaganda", "help_propaganda",
         "btn_diplomacy", "help_diplomacy"}
    };
    private int page = -1;
    private boolean visible;

    public void open() { page = 0; visible = true; }
    public void close() { page = -1; visible = false; }
    public void hide() { visible = false; }
    public void show() { if (isOpen()) visible = true; }
    public boolean isOpen() { return page >= 0; }
    public boolean isVisible() { return isOpen() && visible; }
    public String getTopic() { return isOpen() ? TOPICS[page] : null; }
    public boolean hasIndex() { return page >= 3; }
    public boolean hasPrevious() { return page >= 3 && page != 8 && page != 11; }
    public boolean hasNext() { return page >= 3 && page < TOPICS.length - 1; }

    /** Unknown source links leave the visible page unchanged, as gotoAndStop does. */
    public boolean activate(String control) {
        if (!isOpen() || control == null) return false;
        if ("btnIndex".equals(control) && hasIndex()) { page = 0; return true; }
        if ("btnPrev".equals(control) && hasPrevious()) { page--; return true; }
        if ("btnNext".equals(control) && hasNext()) { page++; return true; }
        if (page < LINKS.length) {
            String[] links = LINKS[page];
            for (int i = 0; i < links.length; i += 2) {
                if (!links[i].equals(control)) continue;
                for (int target = 0; target < TOPICS.length; target++) {
                    if (TOPICS[target].equals(links[i + 1])) { page = target; return true; }
                }
                return false;
            }
        }
        return false;
    }
}
