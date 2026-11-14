package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class SourceCityImpactStateTest {
    @Test
    public void legalTankHitChangesCitySevenFromFrameFourToThree() {
        // Legal seed-zero soldier3 battle: the next source draw at global tick
        // 379 is Python Random(0)'s zero-based draw 272.
        ReferenceRandom random = new ReferenceRandom(0);
        for (int draw = 0; draw < 272; draw++) random.nextDouble();
        SourceCityImpactState city = new SourceCityImpactState(47, 4);

        SourceCityImpactState.Transition transition = city.apply(12, random);
        assertNotNull(transition);
        assertEquals(3, transition.targetFrame);
        assertEquals(65848, transition.randomName);
        assertEquals(3, city.getTargetFrame());
        assertEquals(12.0, city.getCumulativeCasualties(), 0.0);
        assertEquals(0.041202949506209285, random.nextDouble(), 0.0);
    }

    @Test
    public void unchangedAndCappedFramesDoNotDrawOrStartTransition() {
        SourceCityImpactState city = new SourceCityImpactState(200, 6);
        ReferenceRandom random = new ReferenceRandom(17);
        ReferenceRandom untouched = random.copy();

        assertNull(city.apply(10.5, random));
        assertNull(city.apply(109.5, random));
        assertEquals(6, city.getTargetFrame());
        assertEquals(120.0, city.getCumulativeCasualties(), 0.0);
        assertEquals(untouched.nextDouble(), random.nextDouble(), 0.0);

        SourceCityImpactState.Transition transition = city.apply(21, random);
        assertNotNull(transition);
        assertEquals(4, transition.targetFrame);
        assertEquals(4, city.getTargetFrame());
    }

    @Test
    public void fractionalCasualtiesAccumulateBeforeCrossingFrameBoundary() {
        SourceCityImpactState city = new SourceCityImpactState(47, 4);
        ReferenceRandom random = new ReferenceRandom(23);
        ReferenceRandom untouched = random.copy();

        assertNull(city.apply(0.4, random));
        assertNull(city.apply(0.6, random));
        assertEquals(4, city.getTargetFrame());
        assertEquals(1.0, city.getCumulativeCasualties(), 0.0);
        assertEquals(untouched.nextDouble(), random.nextDouble(), 0.0);
        assertEquals(3, city.apply(11, random).targetFrame);
    }

    @Test
    public void deathUsesPreviousFramePlusNineAndNeverReplays() {
        SourceCityImpactState city = new SourceCityImpactState(20, 2);
        ReferenceRandom random = new ReferenceRandom(7);
        assertEquals(1, city.apply(19, random).targetFrame);

        SourceCityImpactState.Transition death = city.apply(1, random);
        assertNotNull(death);
        assertEquals(10, death.targetFrame);
        assertEquals(10, city.getTargetFrame());
        ReferenceRandom afterDeath = random.copy();
        assertNull(city.apply(5, random));
        assertEquals(20.0, city.getCumulativeCasualties(), 0.0);
        assertEquals(afterDeath.nextDouble(), random.nextDouble(), 0.0);
    }

    @Test
    public void alreadyDeadCityDoesNotStartAnotherShrink() {
        SourceCityImpactState city = new SourceCityImpactState(0, 13);
        ReferenceRandom random = new ReferenceRandom(11);
        ReferenceRandom untouched = random.copy();
        assertNull(city.apply(5, random));
        assertEquals(13, city.getTargetFrame());
        assertEquals(0.0, city.getCumulativeCasualties(), 0.0);
        assertEquals(untouched.nextDouble(), random.nextDouble(), 0.0);
    }

    @Test
    public void overCapBranchUpdatesPreviousFrameWithoutStartingShrink() {
        // Deliberately inconsistent initial artwork isolates City.as' >6 branch.
        SourceCityImpactState city = new SourceCityImpactState(200, 2);
        ReferenceRandom random = new ReferenceRandom(29);
        ReferenceRandom untouched = random.copy();

        assertNull(city.apply(10, random)); // Remaining 190 exceeds six frames.
        assertEquals(2, city.getTargetFrame());
        assertNull(city.apply(110, random)); // Remaining 80 computes frame six.
        assertEquals(untouched.nextDouble(), random.nextDouble(), 0.0);
        assertEquals(5, city.apply(10, random).targetFrame);
    }
}
