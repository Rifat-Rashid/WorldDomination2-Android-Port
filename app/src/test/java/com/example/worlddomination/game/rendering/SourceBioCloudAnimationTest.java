package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Values independently replayed from BioCloud.as with CPython seed zero. */
public final class SourceBioCloudAnimationTest {
    private static final double EPSILON = 1e-10;

    @Test
    public void attachmentSpawnsSeventeenCloudsInReverseArrayOrder() {
        ReferenceRandom battleRandom = new ReferenceRandom(0L);
        BioloadCloudAnimation animation = BioloadCloudAnimation.fromSource(battleRandom);

        // View.createBioCloud attaches indices 0..16, then AS2 for-in spawns
        // indices 16..0. Seven inclusive randRange draws are made per cloud.
        assertEquals(-23d, animation.getSourceX(0), 0d);
        assertEquals(-8d, animation.getSourceY(0), 0d);
        assertEquals(49d, animation.getSourceAlpha(0), 0d);
        assertEquals(61d, animation.getSourceScalePercent(0), EPSILON);
        assertEquals(3, animation.getSourceAlphaDecay(0));
        assertEquals(2, animation.getSourceSizeDecay(0));
        assertEquals(3, animation.getSourceHorizontalTicks(0));
        assertEquals(18d, animation.getSourceX(16), 0d);
        assertEquals(-5d, animation.getSourceY(16), 0d);
        assertEquals(38d, animation.getSourceAlpha(16), 0d);
        assertEquals(50d, animation.getSourceScalePercent(16), EPSILON);
        assertEquals(6, animation.getSourceHorizontalTicks(16));
        assertEquals(0.6063384177542189d, battleRandom.copy().nextDouble(), 0d);
    }

    @Test
    public void attachedCallbacksUseDoubleCoordinatesAndDoNotDrawRandomness() {
        ReferenceRandom battleRandom = new ReferenceRandom(0L);
        BioloadCloudAnimation animation = BioloadCloudAnimation.fromSource(battleRandom);

        animation.tick();
        assertEquals(-22.7d, animation.getSourceX(0), EPSILON);
        assertEquals(-10d, animation.getSourceY(0), 0d);
        assertEquals(46d, animation.getSourceAlpha(0), 0d);
        assertEquals(63d, animation.getSourceScalePercent(0), EPSILON);
        assertEquals(2, animation.getSourceHorizontalTicks(0));
        assertEquals(18.3d, animation.getSourceX(16), EPSILON);
        assertEquals(-7d, animation.getSourceY(16), 0d);
        assertEquals(0.6063384177542189d, battleRandom.copy().nextDouble(), 0d);

        for (int tick = 2; tick <= 5; tick++) animation.tick();
        assertEquals(-22.7d, animation.getSourceX(0), EPSILON);
        assertEquals(-18d, animation.getSourceY(0), 0d);
        assertEquals(34d, animation.getSourceAlpha(0), 0d);
        assertEquals(71d, animation.getSourceScalePercent(0), EPSILON);
        assertEquals(8, animation.getSourceHorizontalTicks(0));
        assertEquals(-1, animation.getSourceHorizontalDirection(0));
        assertEquals(0.6063384177542189d, battleRandom.copy().nextDouble(), 0d);
    }

    @Test
    public void expiredCloudRespawnsOnTheFollowingAttachedCallback() {
        ReferenceRandom battleRandom = new ReferenceRandom(0L);
        BioloadCloudAnimation animation = BioloadCloudAnimation.fromSource(battleRandom);
        for (int tick = 1; tick <= 13; tick++) animation.tick();

        assertEquals(-1.0000000000000033d, animation.getSourceAlpha(11), EPSILON);
        assertEquals(0.6063384177542189d, battleRandom.copy().nextDouble(), 0d);
        animation.tick();
        assertEquals(5d, animation.getSourceX(11), 0d);
        assertEquals(-8d, animation.getSourceY(11), 0d);
        assertEquals(38d, animation.getSourceAlpha(11), 0d);
        assertEquals(55d, animation.getSourceScalePercent(11), EPSILON);
        assertEquals(-10, animation.getSourceHorizontalTicks(11));
        assertEquals(0.9610312802396112d, battleRandom.copy().nextDouble(), 0d);
    }

    @Test
    public void retainedCloudsCanBeRespawnedWithoutAdvancingAttachedCallbacks() {
        ReferenceRandom battleRandom = new ReferenceRandom(0L);
        BioloadCloudAnimation animation = BioloadCloudAnimation.fromSource(battleRandom);

        animation.respawnSource();

        assertEquals(0.2747138434192621d, battleRandom.copy().nextDouble(), 0d);
        assertEquals(1, animation.getSourceHorizontalDirection(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void sourceFactoryRequiresSharedBattleRandom() {
        BioloadCloudAnimation.fromSource(null);
    }
}
