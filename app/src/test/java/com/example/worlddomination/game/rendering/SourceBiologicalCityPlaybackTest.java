package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.BiologicalBattleModelTest;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** GPU-free source City/View callback boundaries; full route RNG is tested by World. */
public final class SourceBiologicalCityPlaybackTest {
    private static final int TARGET = 7;

    @Test
    public void impactCreatesCloudBeforeDustThenFrameSeekDetachesRetainedChildren() {
        GameState game = resolvedLegalStrike();
        BattlefieldCityRenderer city = new BattlefieldCityRenderer(null, null);
        city.reset(game);
        ReferenceRandom random = new ReferenceRandom(0L);
        city.beginBattle(game);
        city.beginSourceBiologicalPlayback(game, random);
        city.beginSourceTankCity(game, TARGET);
        assertEquals(4, city.getSourceCityFrame(TARGET));
        assertEquals(100, city.getSourceCityAlpha(TARGET));
        assertFalse(city.isSourceCityBioloaded(TARGET));
        assertEquals(0, city.getSourceCloudCount(TARGET));

        city.applySourceProjectileImpact(game, TARGET, 43, true, random);
        assertTrue(city.isSourceCityBioloaded(TARGET));
        assertTrue(city.isSourceCloudAttached(TARGET));
        assertEquals("fallback child allocator starts at zero", 0,
                city.getSourceCloudDepth(TARGET));
        assertEquals(17, city.getSourceCloudCount(TARGET));
        assertEquals("independent CPython Random(0) dust draw", 85997,
                city.getCityDustName(TARGET));
        BioloadCloudAnimation first = city.getSourceCloudCohort(TARGET, 0);
        assertCloud(first, 0, 4, -1, 41, 67, 1, 10, 2);
        assertCloud(first, 16, 13, -12, 35, 60, 2, 12, -4);
        assertEquals("dust plus 17 reverse-order seven-draw spawns",
                0.5759529480315407, random.copy().nextDouble(), 0d);

        // A Unit attached these children after MovieClipHost captured callbacks.
        city.tickSourceCities(game);
        assertEquals(4, city.getSourceCityFrame(TARGET));
        assertEquals(85, city.getSourceCityAlpha(TARGET));
        assertEquals("cloud depth precedes new dust", 0,
                city.getSourceCloudDepth(TARGET));
        assertEquals(1, city.getCityDustDepth(TARGET));
        assertEquals(0, city.getCityDustFrame(TARGET));
        assertCloud(first, 0, 4, -1, 41, 67, 1, 10, 2);
        assertEquals(0.5759529480315407, random.copy().nextDouble(), 0d);

        city.tickSourceCities(game);
        assertEquals(70, city.getSourceCityAlpha(TARGET));
        assertEquals(4.3, first.getSourceX(0), 1e-12);
        assertEquals(-3, first.getSourceY(0), 0d);
        assertEquals(40, first.getSourceAlpha(0), 0d);
        assertEquals(77, first.getSourceScalePercent(0), 1e-12);

        for (int callback = 3; callback <= 8; callback++) city.tickSourceCities(game);
        assertEquals(4, city.getSourceCityFrame(TARGET));
        assertEquals(-5, city.getSourceCityAlpha(TARGET));
        double frozenX = first.getSourceX(0);
        double frozenY = first.getSourceY(0);
        double frozenAlpha = first.getSourceAlpha(0);
        city.tickSourceCities(game); // Ninth callback seeks authored image frame 1.
        assertEquals(1, city.getSourceCityFrame(TARGET));
        assertEquals(-5, city.getSourceCityAlpha(TARGET));
        assertFalse(city.isSourceCloudAttached(TARGET));
        assertEquals("detached source container retains its depth", 0,
                city.getSourceCloudDepth(TARGET));
        assertEquals(17, city.getSourceCloudCount(TARGET));
        assertEquals(-1, city.getCityDustFrame(TARGET));
        for (int tick = 0; tick < 8; tick++) city.tickSourceCities(game);
        assertEquals("detached old callback freezes X", frozenX, first.getSourceX(0), 0d);
        assertEquals("detached old callback freezes Y", frozenY, first.getSourceY(0), 0d);
        assertEquals("detached old callback freezes alpha", frozenAlpha,
                first.getSourceAlpha(0), 0d);
        assertEquals("detached cohort draws no random", 0.5759529480315407,
                random.copy().nextDouble(), 0d);

        city.endBattle();
        assertFalse(city.isBattleActive());
        assertEquals(1, city.getSourceCityFrame(TARGET));
        assertEquals(100, city.getSourceCityAlpha(TARGET));
        assertFalse(city.isSourceCityBioloaded(TARGET));
        assertEquals(0, city.getSourceCloudCount(TARGET));
        assertFalse(game.getPlayerState(1).getCity(2).bioloaded);
        assertTrue(game.getPlayerState(1).getCity(2).bioloadInBattle);
    }

    @Test
    public void secondImpactOnAttachedContainerDoesNotRespawnThenNewContainerDoes() {
        GameState game = resolvedLegalStrike();
        BattlefieldCityRenderer city = new BattlefieldCityRenderer(null, null);
        city.reset(game);
        city.beginBattle(game);
        ReferenceRandom random = new ReferenceRandom(0L);
        city.beginSourceBiologicalPlayback(game, random);
        city.beginSourceTankCity(game, TARGET);
        city.applySourceProjectileImpact(game, TARGET, 43, true, random);
        BioloadCloudAnimation old = city.getSourceCloudCohort(TARGET, 0);
        ReferenceRandom unchanged = random.copy();
        city.applySourceProjectileImpact(game, TARGET, 0, true, random);
        assertEquals(17, city.getSourceCloudCount(TARGET));
        assertTrue(city.isSourceCloudAttached(TARGET));
        assertEquals(unchanged.nextDouble(), random.copy().nextDouble(), 0d);
        for (int callback = 1; callback <= 9; callback++) city.tickSourceCities(game);
        assertFalse(city.isSourceCloudAttached(TARGET));

        city.applySourceProjectileImpact(game, TARGET, 0, true, random);
        assertTrue(city.isSourceCloudAttached(TARGET));
        assertEquals("old 17 references plus new 17 children", 34,
                city.getSourceCloudCount(TARGET));
        assertEquals("frame seek restarts child depth allocation", 0,
                city.getSourceCloudDepth(TARGET));
        BioloadCloudAnimation newer = city.getSourceCloudCohort(TARGET, 1);
        // Independent CPython Random(0): new 119 draws precede old orphan 119 draws.
        assertCloud(newer, 0, -11, 0, 32, 75, 2, 3, -5);
        assertCloud(old, 0, 0, -6, 34, 65, 1, 6, 10);
        assertEquals(0.8990080380310076, random.copy().nextDouble(), 0d);
        city.tickSourceCities(game);
        assertCloud(newer, 0, -11, 0, 32, 75, 2, 3, -5);
        assertEquals("orphan stays detached after its respawn", 0,
                old.getSourceX(0), 0d);
        city.tickSourceCities(game);
        assertEquals(-10.7, newer.getSourceX(0), 1e-12);
        assertEquals("orphan receives no callback", 0, old.getSourceX(0), 0d);
    }

    @Test
    public void payloadAndSnapshotFlagsGateCloudWithoutChangingRandom() {
        GameState game = resolvedLegalStrike();
        BattlefieldCityRenderer city = new BattlefieldCityRenderer(null, null);
        city.reset(game);
        city.beginBattle(game);
        ReferenceRandom random = new ReferenceRandom(0L);
        city.beginSourceBiologicalPlayback(game, random);
        city.beginSourceTankCity(game, TARGET);
        city.beginSourceTankCity(game, 6); // Neighbor city lacks retained bio flag.
        ReferenceRandom untouched = random.copy();
        city.applySourceProjectileImpact(game, TARGET, 0, false, random);
        city.applySourceProjectileImpact(game, 6, 0, true, random);
        assertEquals(0, city.getSourceCloudCount(TARGET));
        assertEquals(0, city.getSourceCloudCount(6));
        assertFalse(city.isSourceCityBioloaded(TARGET));
        assertFalse(city.isSourceCityBioloaded(6));
        assertEquals(untouched.nextDouble(), random.copy().nextDouble(), 0d);
        city.endBattle();

        // The resolved flag persists, while neither the canonical city nor
        // the next fresh source battle inherits an attached cloud.
        resources(game); resources(game); game.confirmTurn();
        assertTrue(game.getPlayerState(1).getCity(2).bioloadInBattle);
        assertFalse(game.getPlayerState(1).getCity(2).bioloaded);
        city.beginBattle(game);
        city.beginSourceBiologicalPlayback(game, random);
        assertEquals(0, city.getSourceCloudCount(TARGET));
        assertFalse(city.isSourceCityBioloaded(TARGET));
        assertFalse(city.isSourceCloudAttached(TARGET));
    }

    private static GameState resolvedLegalStrike() {
        GameState game = BiologicalBattleModelTest.legalSeventeenTurnBioLaunchQueued();
        game.confirmTurn();
        assertEquals(17, game.getTurn());
        assertEquals(50, game.getBattlePresentationCity(1, 2).populationMillions);
        assertEquals(4, game.getBattlePresentationCity(1, 2).artworkFrame);
        assertTrue(game.getBattlePresentationCity(1, 2).bioloadInBattle);
        assertFalse(game.getBattlePresentationCity(1, 2).bioloaded);
        return game;
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }

    private static void assertCloud(BioloadCloudAnimation field, int index,
                                    double x, double y, double alpha, double scale,
                                    int alphaDecay, int sizeDecay, int horizontalOffset) {
        assertEquals(x, field.getSourceX(index), 1e-12);
        assertEquals(y, field.getSourceY(index), 1e-12);
        assertEquals(alpha, field.getSourceAlpha(index), 1e-12);
        assertEquals(scale, field.getSourceScalePercent(index), 1e-12);
        assertEquals(alphaDecay, field.getSourceAlphaDecay(index));
        assertEquals(sizeDecay, field.getSourceSizeDecay(index));
        assertEquals(horizontalOffset, field.getSourceHorizontalTicks(index));
    }
}
