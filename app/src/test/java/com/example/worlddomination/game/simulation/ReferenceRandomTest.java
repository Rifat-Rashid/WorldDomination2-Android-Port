package com.example.worlddomination.game.simulation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class ReferenceRandomTest {
    @Test
    public void randomDoublesMatchCpythonSeededVectors() {
        ReferenceRandom seedZero = new ReferenceRandom(0L);
        assertEquals(0.8444218515250481, seedZero.nextDouble(), 0.0);
        assertEquals(0.7579544029403025, seedZero.nextDouble(), 0.0);

        ReferenceRandom seedFortyTwo = new ReferenceRandom(42L);
        assertEquals(0.6394267984578837, seedFortyTwo.nextDouble(), 0.0);
        assertEquals(0.025010755222666936, seedFortyTwo.nextDouble(), 0.0);
        assertEquals(0.27502931836911926, seedFortyTwo.nextDouble(), 0.0);
    }

    @Test
    public void boundedDrawsMatchTheSourceInclusiveRangeMapping() {
        ReferenceRandom random = new ReferenceRandom(42L);
        int[] expected = {6, 0, 2, 2, 7, 6, 8, 0, 4, 0};
        for (int value : expected) {
            assertEquals(value, random.nextInt(10));
        }
    }

    @Test
    public void negativeSeedAndReseedingUseTheAbsolutePythonIntegerSeed() {
        ReferenceRandom positive = new ReferenceRandom(42L);
        ReferenceRandom negative = new ReferenceRandom(-42L);
        assertEquals(positive.nextDouble(), negative.nextDouble(), 0.0);

        double expected = new ReferenceRandom(1234L).nextDouble();
        negative.setSeed(1234L);
        assertEquals(expected, negative.nextDouble(), 0.0);
    }

    @Test
    public void booleanDrawConsumesTheSameSourceRollAsBoundedTwoWayChoice() {
        ReferenceRandom booleans = new ReferenceRandom(2718L);
        ReferenceRandom bounded = new ReferenceRandom(2718L);
        for (int index = 0; index < 20; index++) {
            assertEquals(booleans.nextBoolean(), bounded.nextInt(2) == 1);
        }
    }

    @Test
    public void copiedRandomPreservesTheStreamWithoutAdvancingCanonicalState() {
        ReferenceRandom canonical = new ReferenceRandom(2026L);
        ReferenceRandom expectedCanonical = new ReferenceRandom(2026L);
        for (int index = 0; index < 7; index++) {
            canonical.nextDouble();
            expectedCanonical.nextDouble();
        }

        ReferenceRandom presentation = canonical.copy();
        double canonicalNext = expectedCanonical.nextDouble();
        assertEquals(canonicalNext, canonical.nextDouble(), 0.0);
        assertEquals(canonicalNext, presentation.nextDouble(), 0.0);

        presentation.nextInt(1000);
        assertEquals(expectedCanonical.nextInt(1000), canonical.nextInt(1000));
        assertEquals(expectedCanonical.nextDouble(), canonical.nextDouble(), 0.0);
    }

    @Test
    public void boundedDrawRejectsNonPositiveRanges() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReferenceRandom(1L).nextInt(0));
    }
}
