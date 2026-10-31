package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameState;

/** Resolves completed source combat events to their Flash library symbols. */
public final class SourceCombatAnimationMapping {
    private SourceCombatAnimationMapping() { }

    public static String linkageFor(GameState.CombatEvent event) {
        if (event == null) {
            return null;
        }
        if (event.type == GameState.CombatEventType.STRIKE_IMPACT
                && "space".equals(event.weaponName)
                && "specialLoad".equals(event.payloadName)) {
            // Unit.satellite_explosion attaches explosion_warhead_4 directly.
            return "explosion_warhead_4";
        }
        return linkageFor(event.type, event.payloadName, event.counterWeaponName);
    }

    public static String linkageFor(GameState.CombatEventType type, String payloadName,
                                    String counterWeaponName) {
        if (type == null) {
            return null;
        }
        if (type == GameState.CombatEventType.STRIKE_IMPACT) {
            if ("specialLoad".equals(payloadName)) {
                // WeaponStock.specialLoad declares the Flash bioload level-four impact clip.
                return "explosion_bioload_4";
            }
            if (isPayload(payloadName, "warhead")) {
                return linkageForPayload(payloadName);
            }
            if (isPayload(payloadName, "bioload")) {
                return linkageForPayload(payloadName);
            }
            return null;
        }
        if (type == GameState.CombatEventType.STRIKE_INTERCEPTED) {
            if ("antiRocket".equals(counterWeaponName)) {
                return "explosion002";
            }
            if ("antiAir".equals(counterWeaponName)) {
                return "explosion003";
            }
        }
        return null;
    }

    private static String linkageForPayload(String payloadName) {
        return "explosion_" + payloadName.substring(0, payloadName.length() - 1)
                + "_" + payloadName.charAt(payloadName.length() - 1);
    }

    private static boolean isPayload(String name, String prefix) {
        if (name == null || !name.startsWith(prefix) || name.length() != prefix.length() + 1) {
            return false;
        }
        char level = name.charAt(name.length() - 1);
        return level >= '1' && level <= '4';
    }
}
