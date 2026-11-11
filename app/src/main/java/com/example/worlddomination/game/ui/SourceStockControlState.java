package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.WeaponCatalog;

/** Source Btn_stock switch state and number, independent of drawing. */
public final class SourceStockControlState {
    public static final int UNAVAILABLE = 1;
    public static final int ELIGIBLE = 2;
    public static final int SELECTED = 3;
    public static final int INACTIVE = 4;

    public static final int UNAVAILABLE_SHAPE = 4490;
    public static final int ELIGIBLE_SHAPE = 4491;
    public static final int SELECTED_SHAPE = 4493;

    private SourceStockControlState() { }

    /** Mirrors Sub_Presenter_interface.updateView's stock state order. */
    public static int state(GameState game, SourceActionMenuState menu, String weaponName) {
        GameState.WeaponState weapon = weapon(game, weaponName);
        if (weapon == null) return UNAVAILABLE;
        GameState.Action action = game.getSelectedAction();
        int result = isStockAction(action) && !weapon.active ? INACTIVE : eligible(game, weapon, action)
                ? ELIGIBLE : UNAVAILABLE;
        if (isStockAction(action) && menu != null
                && weaponName.equals(menu.getSelectedEquipment())) result = SELECTED;
        // Presenter forcibly resets Satellite outside a selected Space Rocket.
        if ("specialLoad".equals(weaponName)
                && !"space".equals(selectedCarrier(game))) result = UNAVAILABLE;
        return result;
    }

    /** The source's -1 means a dash; queued and preview use consume the visible count. */
    public static int displayedCount(GameState game, SourceActionMenuState menu,
                                     String weaponName) {
        GameState.WeaponState weapon = weapon(game, weaponName);
        if (weapon == null || !weapon.active) return -1;
        int count = game.getAvailableWeaponStock(0, weaponName);
        if (game.getSelectedAction() == GameState.Action.PREPARED_ATTACK
                && "ship".equals(game.getPreparedCarrierName(0))) {
            count -= game.getSelectedShipPayloadUseCount(weaponName);
        } else if (isStockAction(game.getSelectedAction()) && menu != null
                && weaponName.equals(menu.getSelectedEquipment())) {
            count--;
        }
        return Math.max(0, count);
    }

    public static int shapeId(int state) {
        switch (state) {
            case ELIGIBLE: return ELIGIBLE_SHAPE;
            case SELECTED: return SELECTED_SHAPE;
            case UNAVAILABLE:
            case INACTIVE: return UNAVAILABLE_SHAPE;
            default: throw new IllegalArgumentException("Unknown stock state: " + state);
        }
    }

    /** Index of the cached switch artwork in source frame order. */
    public static int switchIndex(int state) {
        switch (state) {
            case ELIGIBLE: return 1;
            case SELECTED: return 2;
            case UNAVAILABLE:
            case INACTIVE: return 0;
            default: throw new IllegalArgumentException("Unknown stock state: " + state);
        }
    }

    public static boolean usesGrayLabel(int state) { return state == INACTIVE; }

    private static GameState.WeaponState weapon(GameState game, String name) {
        if (game == null || name == null) return null;
        return game.getHuman().getWeapons().get(name);
    }

    private static String selectedCarrier(GameState game) {
        String prepared = game.getPreparedCarrierName(0);
        return prepared == null ? game.getSelectedPreparedCarrier() : prepared;
    }

    private static boolean eligible(GameState game, GameState.WeaponState weapon,
                                    GameState.Action action) {
        if (weapon.inStock <= 0) return false; // Source weaponInStock is raw stock.
        WeaponCatalog.Definition definition = weapon.definition;
        String type = definition.type;
        if (action == GameState.Action.ATTACK) {
            return ("soldier".equals(type) || "spy".equals(type))
                    && (!"spy".equals(type)
                    || game.getSpyReadiness() >= definition.level * 25);
        }
        if (action == GameState.Action.DEFENSE) return "anti".equals(type);
        if (action != GameState.Action.PREPARED_ATTACK) return false;
        String carrierName = game.getPreparedCarrierName(0);
        if (carrierName == null) return isCarrier(type);
        if ("specialLoad".equals(type)) return "space".equals(carrierName);
        if (!"rocketload".equals(type)) return false;
        GameState.WeaponState carrier = weapon(game, carrierName);
        return carrier != null && definition.level <= carrier.definition.level;
    }

    private static boolean isCarrier(String type) {
        return "rocket".equals(type) || "plane".equals(type)
                || "sub".equals(type) || "ship".equals(type)
                || "space".equals(type);
    }

    private static boolean isStockAction(GameState.Action action) {
        return action == GameState.Action.ATTACK || action == GameState.Action.DEFENSE
                || action == GameState.Action.PREPARED_ATTACK;
    }
}
