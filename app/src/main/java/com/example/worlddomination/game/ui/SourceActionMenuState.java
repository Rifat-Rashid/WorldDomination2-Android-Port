package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.WeaponCatalog;

/** Source centerScreen4444 transitions, independent of graphics and Android. */
public final class SourceActionMenuState {
    private GameState.Action action;
    private boolean weaponChosen, cityChosen, leaderChosen, optionChosen, cancelling;
    private boolean pendingError;
    private String payload;
    private String error;
    private String key;
    private int revision;

    public static boolean supports(GameState.Action action) {
        return action == GameState.Action.ATTACK || action == GameState.Action.PREPARED_ATTACK
                || action == GameState.Action.DEFENSE || action == GameState.Action.PROPAGANDA
                || action == GameState.Action.DIPLOMACY;
    }

    public void begin(GameState game) {
        action = game.getSelectedAction();
        weaponChosen = cityChosen = leaderChosen = optionChosen = cancelling = false;
        payload = error = null;
        pendingError = false;
        refresh(game);
    }

    public String getKey() { return key; }
    public int getRevision() { return revision; }
    public String getError() { return error; }
    public boolean hasPendingError() { return pendingError; }
    public boolean hasLeader() { return leaderChosen; }
    public boolean hasOption() { return optionChosen; }

    public boolean chooseEquipment(GameState game, String name) {
        WeaponCatalog.Definition weapon = WeaponCatalog.byName(name);
        if (weapon == null || game.getSelectedAction() != action || cancelling) return false;
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
            else if (game.getAvailablePreparedPayloads(0, carrier).contains(name)) {
                payload = name;
                selected = true;
            }
        }
        if (selected) {
            weaponChosen = true;
            // The source keeps the selected target when switching compatible equipment.
            if (action == GameState.Action.ATTACK && cityChosen) {
                cityChosen = game.configureAttack(game.getSelectedTargetPlayer(),
                        game.getSelectedTargetCity(), name);
            } else if (action == GameState.Action.PREPARED_ATTACK) cityChosen = false;
            error = null;
        }
        refresh(game);
        return selected;
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
                if (selected) payload = null;
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
        if (action == GameState.Action.DIPLOMACY) return leaderChosen && optionChosen;
        if (action == GameState.Action.DEFENSE) return weaponChosen;
        if (action == GameState.Action.PROPAGANDA) return cityChosen;
        if (action == GameState.Action.ATTACK) return weaponChosen && cityChosen;
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

    public void dismissError(GameState game) { error = null; pendingError = false; refresh(game); }

    private void refresh(GameState game) {
        if (error != null) key = "spy".equals(error) ? "spy_error" : "diplomacy_error";
        else if (cancelling) key = "preCancel";
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
