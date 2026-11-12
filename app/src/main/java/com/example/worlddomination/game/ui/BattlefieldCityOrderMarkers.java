package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.WeaponCatalog;

/** Python action_entry.sync_cities icon order for source-valid UI orders. */
public final class BattlefieldCityOrderMarkers {
    public enum Icon { SOLDIER, ROCKET, PLANE, PROPAGANDA, SPECIAL }

    private BattlefieldCityOrderMarkers() { }

    public static boolean isMarked(GameState game, int playerId, int cityIndex) {
        return iconCount(game, playerId, cityIndex) > 0;
    }

    public static int iconCount(GameState game, int playerId, int cityIndex) {
        if (!valid(game, playerId, cityIndex)) return 0;
        int count = 0;
        if (targetFor(game, GameState.Action.ATTACK, playerId, cityIndex) != null) count++;
        boolean prepared = targetFor(game, GameState.Action.PREPARED_ATTACK,
                playerId, cityIndex) != null;
        if (prepared) count++;
        if (targetFor(game, GameState.Action.PROPAGANDA,
                playerId, cityIndex) != null) count++;
        return count + shipCount(game, playerId, cityIndex);
    }

    /** Python presentation rebuilds all icon children in attack/prepared/media/ship order. */
    public static Icon iconAt(GameState game, int playerId, int cityIndex, int iconIndex) {
        if (!valid(game, playerId, cityIndex) || iconIndex < 0) return null;
        if (targetFor(game, GameState.Action.ATTACK, playerId, cityIndex) != null) {
            if (iconIndex-- == 0) return Icon.SOLDIER;
        }
        String carrier = targetFor(game, GameState.Action.PREPARED_ATTACK, playerId, cityIndex);
        if (carrier != null) {
            if (iconIndex-- == 0) return carrierIcon(carrier);
        }
        if (targetFor(game, GameState.Action.PROPAGANDA,
                playerId, cityIndex) != null) {
            if (iconIndex-- == 0) return Icon.PROPAGANDA;
        }
        return iconIndex < shipCount(game, playerId, cityIndex) ? Icon.SPECIAL : null;
    }

    private static boolean valid(GameState game, int playerId, int cityIndex) {
        return game != null && playerId >= 0 && playerId < BattlefieldCityLayout.PLAYER_COUNT
                && cityIndex >= 0 && cityIndex < BattlefieldCityLayout.CITIES_PER_PLAYER;
    }

    /** Source UI permits one queued order of each action; the latest slot wins if driven directly. */
    private static String targetFor(GameState game, GameState.Action action,
                                    int playerId, int cityIndex) {
        boolean activeSelected = game.getSelectedAction() == action
                && game.hasSelectedActionCityTarget();
        if (activeSelected) {
            if (game.getSelectedTargetPlayer() != playerId
                    || game.getSelectedTargetCity() != cityIndex) return null;
            if (action == GameState.Action.ATTACK
                    && game.getSelectedAttackWeapon() != null) return "soldier";
            if (action == GameState.Action.PREPARED_ATTACK
                    && game.getSelectedPreparedPayload() != null
                    && !"ship".equals(game.getSelectedPreparedCarrier()))
                return game.getSelectedPreparedCarrier();
            if (action == GameState.Action.PROPAGANDA) return "propaganda";
        }
        int latest = -1;
        for (int slot = 0; slot < 2; slot++) {
            if (game.getQueuedAction(slot) == action) latest = slot;
        }
        if (latest < 0 || game.getQueuedTargetPlayer(latest) != playerId
                || game.getQueuedTargetCity(latest) != cityIndex) return null;
        if (action == GameState.Action.PREPARED_ATTACK) {
            if (game.isQueuedCarrierPreparation(latest)
                    || "ship".equals(game.getQueuedWeaponName(latest))) return null;
            return game.getQueuedWeaponName(latest);
        }
        return action == GameState.Action.ATTACK ? "soldier" : "propaganda";
    }

    private static int shipCount(GameState game, int playerId, int cityIndex) {
        int count = 0;
        for (int slot = 0; slot < 2; slot++) {
            if (game.getQueuedAction(slot) != GameState.Action.PREPARED_ATTACK) continue;
            for (int strike = 0; strike < game.getQueuedShipStrikeCount(slot); strike++) {
                if (game.getQueuedShipStrikePlayer(slot, strike) == playerId
                        && game.getQueuedShipStrikeCity(slot, strike) == cityIndex) count++;
            }
        }
        if (game.getSelectedAction() == GameState.Action.PREPARED_ATTACK) {
            count += game.getSelectedShipCityUseCount(playerId, cityIndex);
        }
        return count;
    }

    private static Icon carrierIcon(String carrierName) {
        WeaponCatalog.Definition carrier = WeaponCatalog.byName(carrierName);
        if (carrier == null) return Icon.SPECIAL;
        if ("rocket".equals(carrier.type)) return Icon.ROCKET;
        if ("plane".equals(carrier.type)) return Icon.PLANE;
        return Icon.SPECIAL;
    }
}
