package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class SourceCombatAnimationMappingTest {
    @Test
    public void allWarheadAndBioloadLevelsUseTheirOriginalSymbols() {
        for (int level = 1; level <= 4; level++) {
            assertEquals("explosion_warhead_" + level,
                    SourceCombatAnimationMapping.linkageFor(
                            GameState.CombatEventType.STRIKE_IMPACT,
                            "warhead" + level, null));
            assertEquals("explosion_bioload_" + level,
                    SourceCombatAnimationMapping.linkageFor(
                            GameState.CombatEventType.STRIKE_IMPACT,
                            "bioload" + level, null));
        }
        assertEquals("explosion_bioload_4",
                SourceCombatAnimationMapping.linkageFor(
                        GameState.CombatEventType.STRIKE_IMPACT,
                        "specialLoad", null));
    }

    @Test
    public void interceptionSymbolsFollowTheMatchingCounterWeapon() {
        assertEquals("explosion002", SourceCombatAnimationMapping.linkageFor(
                GameState.CombatEventType.STRIKE_INTERCEPTED, "warhead1", "antiRocket"));
        assertEquals("explosion003", SourceCombatAnimationMapping.linkageFor(
                GameState.CombatEventType.STRIKE_INTERCEPTED, "bioload4", "antiAir"));
        assertNull(SourceCombatAnimationMapping.linkageFor(
                GameState.CombatEventType.STRIKE_INTERCEPTED, "warhead1", null));
    }

    @Test
    public void unsupportedPayloadsAndDirectAttacksDoNotInventAnAnimation() {
        assertNull(SourceCombatAnimationMapping.linkageFor(
                GameState.CombatEventType.STRIKE_IMPACT, "warhead5", null));
        assertNull(SourceCombatAnimationMapping.linkageFor(
                GameState.CombatEventType.STRIKE_IMPACT, "unmapped", null));
        assertNull(SourceCombatAnimationMapping.linkageFor(
                GameState.CombatEventType.DIRECT_ATTACK, null, null));
    }
}
