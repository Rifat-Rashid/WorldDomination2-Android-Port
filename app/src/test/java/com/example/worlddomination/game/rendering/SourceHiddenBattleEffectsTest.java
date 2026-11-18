package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class SourceHiddenBattleEffectsTest {
    @Test public void creaturesMatchEveryOriginalFrameAndStopAfterWrapping() {
        SourceHiddenBattleEffects effects = new SourceHiddenBattleEffects();
        effects.begin(true, true, false, false, new int[0], new int[0]);
        Recorder recorder = new Recorder(effects);
        for (JsonValue row : fixture().get("creatures")) {
            long tick = row.getLong("tick");
            while (effects.getElapsedTicks() < tick) advance(effects, recorder);
            assertEquals("orca tick " + tick, row.getInt("orca"), effects.getOrcaFrame());
            assertEquals("nessy tick " + tick, row.getInt("nessy"), effects.getNessyFrame());
            assertEquals(row.getBoolean("orcaPlaying"), effects.isOrcaPlaying());
            assertEquals(row.getBoolean("nessyPlaying"), effects.isNessyPlaying());
        }
        assertTrue(recorder.events.isEmpty());
        assertFalse(effects.suppressesNormalTickerCompletion());
        assertEquals(318, SourceHiddenBattleEffects.ORCA_DEPTH);
        assertEquals(316, SourceHiddenBattleEffects.NESSY_DEPTH);
    }

    @Test public void killAllKeepsSourceCityOrderAndWaitsForExtraEmptyIteration() {
        verifyExplosions("kill_all", true, false, 817);
    }

    @Test public void bushDoesNotInventCompletionWhenItsQueueBecomesEmpty() {
        SourceHiddenBattleEffects effects = verifyExplosions("bush", false, true, 220);
        assertTrue(effects.isActive());
        assertFalse(effects.suppressesNormalTickerCompletion());
        effects.finish(); // Normal f_37 completion may finish this battle first.
        assertFalse(effects.isActive());
    }

    @Test public void combinedCommandsRetainSharedQueueAndOverwrittenTimerHandle() {
        verifyExplosions("both", true, true, 205);
    }

    private static SourceHiddenBattleEffects verifyExplosions(
            String name, boolean killAll, boolean bush, int lastTick) {
        JsonValue fixture = fixture();
        SourceHiddenBattleEffects effects = new SourceHiddenBattleEffects();
        effects.begin(false, false, killAll, bush,
                fixture.get("enemyCities").asIntArray(), fixture.get("humanCities").asIntArray());
        Recorder recorder = new Recorder(effects);
        assertEquals(killAll, effects.suppressesNormalTickerCompletion());
        while (effects.isActive() && effects.getElapsedTicks() < lastTick) advance(effects, recorder);
        List<String> expected = new ArrayList<>();
        for (JsonValue event : fixture.get(name)) expected.add(event.asString());
        assertEquals(name, expected, recorder.events);
        assertEquals(lastTick, effects.getElapsedTicks());
        if (killAll) assertFalse(effects.isActive());
        return effects;
    }

    private static void advance(SourceHiddenBattleEffects effects, Recorder recorder) {
        effects.advanceTimelines();
        effects.dispatchIntervals(recorder);
    }

    private static JsonValue fixture() {
        try (InputStream input = SourceHiddenBattleEffectsTest.class.getResourceAsStream(
                "/source-hidden-battle-effects.json")) {
            assertNotNull(input);
            return new JsonReader().parse(input);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class Recorder implements SourceHiddenBattleEffects.Listener {
        private final SourceHiddenBattleEffects effects;
        final List<String> events = new ArrayList<>();
        Recorder(SourceHiddenBattleEffects effects) { this.effects = effects; }
        private void add(String event) { events.add(effects.getElapsedTicks() + ":" + event); }
        @Override public void explodeCity(int address) { add("city:" + address); }
        @Override public void stopBattleTicker() { add("stopTicker"); }
        @Override public void allUnitsDone() { add("allUnitsDone"); }
        @Override public void endBattle() { add("endBattle"); }
    }
}
