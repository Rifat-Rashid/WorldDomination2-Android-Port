package com.example.worlddomination.game.ui;

import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.WeaponCatalog;

/** Source colorCities flags and View.updateCities color precedence. */
public final class BattlefieldCitySelection {
    public enum Tint { DEFAULT, RED, GREEN }

    private BattlefieldCitySelection() { }

    public static Tint tint(GameState game, int ownerId, int cityIndex,
                            String selectedEquipment) {
        if (game == null || ownerId < 0 || ownerId >= 5 || cityIndex < 0 || cityIndex >= 5)
            return Tint.DEFAULT;
        GameState.Action action = game.getSelectedAction();
        if (action != GameState.Action.ATTACK && action != GameState.Action.PREPARED_ATTACK
                && action != GameState.Action.PROPAGANDA) return Tint.DEFAULT;
        GameState.PlayerState owner = game.getPlayerState(ownerId);
        if (owner.getPlayerColor() == 0) return Tint.DEFAULT;
        CityState city = owner.getCity(cityIndex);
        if (city == null) return Tint.DEFAULT;
        // View.updateCities renders selected city clips before eligibility colors.
        if (BattlefieldCityOrderMarkers.isMarked(game, ownerId, cityIndex))
            return Tint.DEFAULT;
        WeaponCatalog.Definition weapon = selectedEquipment == null ? null
                : WeaponCatalog.byName(selectedEquipment);
        boolean allEnemy = action == GameState.Action.ATTACK && weapon != null
                && "spy".equals(weapon.type);
        boolean hidden = ownerId == game.getHuman().id
                || !allEnemy && game.getHuman().hasCeasefireWith(ownerId);
        if (!allEnemy && action == GameState.Action.PREPARED_ATTACK
                && game.getSelectedShipTargetPlayerId() >= 0
                && game.getSelectedShipTargetPlayerId() != ownerId)
            hidden = true;
        // hideForSelection takes precedence, even for a ruined/empty city.
        if (hidden) return Tint.RED;
        return city.populationMillions > 0 ? Tint.GREEN : Tint.DEFAULT;
    }
}
