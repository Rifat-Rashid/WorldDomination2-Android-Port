package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.WeaponCatalog;

/** Source centerScreen4444 transitions, independent of graphics and Android. */
public final class SourceActionMenuState {
    private GameState.Action action;
    private boolean weaponChosen, cityChosen, leaderChosen, optionChosen, cancelling;
    private boolean pendingError;
    private boolean economyWarning;
    private String equipment;
    private int shipStrikeCount;
    private String payload;
    private String error;
    private String key;
    private int revision;

    public static boolean supports(GameState.Action action) {
        return action == GameState.Action.SKIP || action == GameState.Action.MASS_MEDIA || action == GameState.Action.ATTACK || action == GameState.Action.PREPARED_ATTACK
                || action == GameState.Action.DEFENSE || action == GameState.Action.PROPAGANDA
                || action == GameState.Action.DIPLOMACY;
    }

    public void begin(GameState game) {
        action = game.getSelectedAction();
        weaponChosen = cityChosen = leaderChosen = optionChosen = cancelling = false;
        equipment = payload = error = null;
        game.clearBattlefieldCitySelection();
        pendingError = false;
        economyWarning = false;
        refresh(game);
    }

    /** The source error frames replace the economy menu without losing its selections. */
    public void showEconomyWarning(GameState game, String message) {
        GameState.Action selected = game.getSelectedAction();
        if (selected != GameState.Action.PRODUCTION && selected != GameState.Action.RESEARCH)
            throw new IllegalStateException("Economy warnings require Production or Technology");
        if (message == null || message.isEmpty()) throw new IllegalArgumentException("Warning text is required");
        action = selected;
        error = message;
        economyWarning = true;
        pendingError = false;
        key = selected == GameState.Action.PRODUCTION ? "production_error" : "research_error-" + game.getHuman().getReligionCode();
        revision++;
    }

    public boolean isEconomyWarning(GameState game) {
        return economyWarning && game != null && action == game.getSelectedAction();
    }

    public void clearEconomyWarning() {
        if (!economyWarning) return;
        economyWarning = false; error = null; key = null; revision++;
    }

    public String getSelectedEquipment() { return equipment; }

    public String getKey() { return key; }
    public int getRevision() { return revision; }
    public String getError() { return error; }
    public boolean hasPendingError() { return pendingError; }
    public boolean hasLeader() { return leaderChosen; }
    public boolean hasOption() { return optionChosen; }

    /** Presenter_interface selects the appropriate action when idle stock is tapped. */
    public static GameState.Action actionForEquipment(GameState game, String name) {
        GameState.WeaponState weapon = game.getHuman().getWeapons().get(name);
        if (weapon == null || !weapon.active || weapon.inStock <= 0
                || game.getSelectedAction() != null || game.getQueuedActionCount() >= 2) return null;
        String type = weapon.definition.type;
        if ("soldier".equals(type) || "spy".equals(type)) return GameState.Action.ATTACK;
        if ("anti".equals(type)) return GameState.Action.DEFENSE;
        String carrier = game.getPreparedCarrierName(0);
        if (carrier == null && game.getAvailablePreparedCarrierStock(0).contains(name)
                || carrier != null && game.getAvailablePreparedPayloads(0, carrier).contains(name))
            return GameState.Action.PREPARED_ATTACK;
        return null;
    }

    public boolean chooseEquipment(GameState game, String name) {
        WeaponCatalog.Definition weapon = WeaponCatalog.byName(name);
        if (weapon == null || game.getSelectedAction() != action || cancelling) return false;
        cityChosen = false;
        game.clearBattlefieldCitySelection();
        if (availableStock(game, name) <= 0) { refresh(game); return false; }
        boolean selected = false;
        if (action == GameState.Action.ATTACK) {
            selected = game.previewAttackWeapon(name);
            if (!selected && "spy".equals(weapon.type)) {
                GameState.WeaponState stock = game.getHuman().getWeapons().get(name);
                if (stock != null && stock.active && stock.inStock > 0
                        && game.getSpyReadiness() < weapon.level * 25) error = "spy";
            }
        } else if (action == GameState.Action.DEFENSE) {
            selected = game.configureDefense(name);
        } else if (action == GameState.Action.PREPARED_ATTACK) {
            String carrier = game.getPreparedCarrierName(0);
            if (carrier == null) selected = game.configurePreparedCarrier(name);
            else if (canChoosePayload(game, carrier, name)) {
                payload = name;
                selected = true;
            }
        }
        if (selected) {
            boolean toggledOff = name.equals(equipment);
            equipment = toggledOff ? null : name;
            weaponChosen = !toggledOff;
            if (toggledOff) payload = null;
            error = null;
        }
        refresh(game);
        return selected;
    }

    /** Source HUD stockAmount subtracts both queued and current-selection inUse. */
    public int availableStock(GameState game, String name) {
        int remaining = game.getAvailableWeaponStock(0, name);
        if (game.getSelectedAction() != action) return remaining;
        for (GameState.ShipStrike strike : game.getSelectedShipStrikes())
            if (name.equals(strike.payloadName)) remaining--;
        boolean selectingShipPayload = action == GameState.Action.PREPARED_ATTACK
                && "ship".equals(game.getPreparedCarrierName(0));
        if (!selectingShipPayload && name.equals(equipment)) remaining--;
        return Math.max(0, remaining);
    }

    private static boolean canChoosePayload(GameState game, String carrier, String name) {
        if (!game.getAvailablePreparedPayloads(0, carrier).contains(name)) return false;
        int remaining = game.getAvailableWeaponStock(0, name);
        if ("ship".equals(carrier)) {
            for (GameState.ShipStrike strike : game.getSelectedShipStrikes())
                if (name.equals(strike.payloadName)) remaining--;
        }
        return remaining > 0;
    }

    public boolean chooseCity(GameState game, int player, int city) {
        if (cancelling || error != null || player <= 0 || player >= 5 || city < 0 || city >= 5)
            return false;
        boolean selected = false;
        if (action == GameState.Action.ATTACK && weaponChosen) {
            selected = game.configureAttack(player, city, game.getSelectedAttackWeapon());
        } else if (action == GameState.Action.PROPAGANDA) {
            selected = game.configurePropaganda(player, city);
        } else if (action == GameState.Action.PREPARED_ATTACK && payload != null) {
            if ("ship".equals(game.getPreparedCarrierName(0))) {
                selected = PreparedShipStrikeSelection.tryAddTargetCity(game, player, city, payload)
                        == PreparedShipStrikeSelection.Result.ADDED;
                if (selected) { payload = null; equipment = null; }
            } else selected = game.configurePreparedLaunch(player, city, payload);
        }
        if (selected) {
            cityChosen = true;
            game.selectBattlefieldCity(player, city);
        }
        refresh(game);
        return selected;
    }

    public boolean chooseLeader(GameState game, int player) {
        if (!game.selectDiplomacyTarget(player)) return false;
        leaderChosen = true;
        refresh(game);
        return true;
    }

    public boolean chooseOption(GameState game, int option) {
        if (option < 0 || option > 5 || game.getSelectedAction() != GameState.Action.DIPLOMACY) return false;
        if (option < 3 && game.getDiplomacyOptionAmount(option) > game.getHuman().turnMoneyMillions) {
            error = "money";
            refresh(game);
            return false;
        }
        if (!game.selectDiplomacyOption(option)) return false;
        optionChosen = true;
        refresh(game);
        return true;
    }

    public boolean chooseSpyBuilding(GameState game, int index) {
        String[] buildings = {"spyCenter", "tech", "religion", "resources", "factory"};
        if (index < 0 || index >= buildings.length || !game.selectSpyBuilding(buildings[index]))
            return false;
        refresh(game);
        return true;
    }

    public boolean isAcceptVisible() {
        if (error != null) return false;
        if (cancelling) return true;
        if (action == GameState.Action.SKIP || action == GameState.Action.MASS_MEDIA) return true;
        if (action == GameState.Action.DIPLOMACY) return leaderChosen && optionChosen;
        if (action == GameState.Action.DEFENSE) return weaponChosen;
        if (action == GameState.Action.PROPAGANDA) return cityChosen;
        if (action == GameState.Action.ATTACK) return weaponChosen && cityChosen;
        if (action == GameState.Action.PREPARED_ATTACK && shipStrikeCount > 0
                && (key.startsWith("ship1-") || "preAttack4-ship".equals(key))) return true;
        return key != null && (key.startsWith("preAttack-") || key.startsWith("preAttack3-")
                || "preAttack_space".equals(key) || "ship2".equals(key));
    }

    public boolean isCancelVisible() {
        return isAcceptVisible() || "preAttack2".equals(key) || key != null
                && (key.startsWith("ship1-") || "preAttack4-ship".equals(key));
    }

    public boolean isMapSelection() {
        return "attack1".equals(key) || "propaganda1".equals(key)
                || "preAttack4".equals(key) || "preAttack4-ship".equals(key);
    }

    public boolean accept(GameState game) {
        if (!isAcceptVisible()) return false;
        if (cancelling) return game.cancelPreparedAttack();
        if (game.acceptSelectedAction()) return true;
        if (action == GameState.Action.DIPLOMACY) { error = "money"; refresh(game); }
        return false;
    }

    /** Returns true when the menu closes; a prepared carrier requires source confirmation. */
    public boolean cancel(GameState game) {
        if (!cancelling && action == GameState.Action.PREPARED_ATTACK
                && game.getPreparedCarrierName(0) != null
                && !"ship".equals(game.getPreparedCarrierName(0))) {
            cancelling = true;
            refresh(game);
            return false;
        }
        game.cancelSelection();
        return true;
    }

    public void showMoneyError(GameState game) {
        if (game.getPendingHumanDiplomacyProposal() != null) pendingError = true;
        else error = "money";
        refresh(game);
    }

    public void dismissError(GameState game) {
        if (economyWarning) { clearEconomyWarning(); return; }
        error = null; pendingError = false; refresh(game);
    }

    private void refresh(GameState game) {
        shipStrikeCount = game.getSelectedShipStrikeCount();
        if (error != null) key = "spy".equals(error) ? "spy_error" : "diplomacy_error";
        else if (cancelling) key = "preCancel";
        else if (action == GameState.Action.SKIP) key = "skip_action";
        else if (action == GameState.Action.MASS_MEDIA) key = "religion-" + game.getHuman().getReligionCode();
        else if (action == GameState.Action.DIPLOMACY) key = "diplomacy";
        else if (action == GameState.Action.PROPAGANDA) key = cityChosen ? "propaganda2" : "propaganda1";
        else if (action == GameState.Action.DEFENSE) key = weaponChosen
                ? "defense-" + game.getSelectedDefenseWeapon() : "defenseSelect";
        else if (action == GameState.Action.ATTACK) {
            key = !weaponChosen ? "attackSelect" : !cityChosen ? "attack1"
                    : game.isSelectedAttackSpy() ? "attack-spy-" + game.getHuman().getReligionCode()
                    + "-" + game.getSelectedSpyBuilding() : "attack2-" + game.getSelectedAttackWeapon();
        } else if (action == GameState.Action.PREPARED_ATTACK) {
            String carrier = game.getPreparedCarrierName(0);
            if (carrier == null) {
                key = weaponChosen ? "preAttack-" + game.getSelectedPreparedCarrier() : "preAttackSelect";
                if (weaponChosen && "space".equals(game.getSelectedPreparedCarrier()))
                    key += "-" + Math.max(1, Math.min(25, game.getHuman().religion));
            }
            else if ("ship".equals(carrier)) key = game.getSelectedShipStrikeCount() == 3 ? "ship2"
                    : payload == null ? "ship1-" + game.getSelectedShipStrikeCount() : "preAttack4-ship";
            else if (payload == null) key = "preAttack2";
            else if (!cityChosen) key = "preAttack4";
            else key = "specialLoad".equals(payload) ? "preAttack_space"
                    : "preAttack3-" + (payload.startsWith("warhead") ? "warhead" : "bioload");
        } else key = null;
        revision++;
    }
}
