package com.example.worlddomination.game.rendering;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class SourceStoppedClipQueueTest {
    @Test
    public void removesAtMostTwentyOneNewestClipsInReverseOrder() {
        for (int count : new int[] {0, 20, 21, 22, 40}) {
            SourceStoppedClipQueue queue = new SourceStoppedClipQueue();
            List<Integer> detached = new ArrayList<>();
            boolean[] visible = new boolean[count];
            for (int index = 0; index < count; index++) {
                final int clipIndex = index;
                visible[index] = true;
                queue.add(() -> {
                    visible[clipIndex] = false;
                    detached.add(clipIndex);
                });
            }

            int expectedRemoved = Math.min(count, 21);
            assertEquals(expectedRemoved, queue.removeAtAllUnitsDone());
            assertEquals(count, queue.size());
            assertEquals(expectedRemoved, detached.size());
            for (int index = 0; index < expectedRemoved; index++) {
                assertEquals(count - 1 - index, (int) detached.get(index));
            }
            for (int index = 0; index < count; index++) {
                assertEquals("Only the last 21 source wrappers lose their clips",
                        index < count - expectedRemoved, visible[index]);
            }
            assertEquals(0, queue.removeAtAllUnitsDone());
            assertEquals(expectedRemoved, detached.size());
        }
    }

    @Test
    public void resetDropsOldReferencesAndAllowsOneCleanupForTheNewBattle() {
        SourceStoppedClipQueue queue = new SourceStoppedClipQueue();
        boolean[] visible = {true, true, true};
        queue.add(() -> visible[0] = false);
        queue.add(() -> visible[1] = false);
        assertEquals(2, queue.size());
        assertEquals(2, queue.removeAtAllUnitsDone());
        assertFalse(visible[0]);
        assertFalse(visible[1]);

        queue.reset();
        assertEquals(0, queue.size());
        queue.add(() -> visible[2] = false);
        assertEquals(1, queue.removeAtAllUnitsDone());
        assertFalse(visible[2]);
        assertEquals(1, queue.size());
        assertEquals(0, queue.removeAtAllUnitsDone());
    }

    @Test(expected = NullPointerException.class)
    public void rejectsNullClip() {
        new SourceStoppedClipQueue().add(null);
    }
}
