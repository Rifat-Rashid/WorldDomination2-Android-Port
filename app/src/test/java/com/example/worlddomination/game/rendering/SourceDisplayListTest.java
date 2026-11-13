package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.*;

public final class SourceDisplayListTest {
    @Test public void attachReplacesOnlyTheOccupiedDepthAndOldHandleIsStale() {
        SourceDisplayList<String> list = new SourceDisplayList<>(0);
        SourceDisplayList.Entry<String> first = list.attach("same name", 7);
        SourceDisplayList.Entry<String> second = list.attach("same name", 7);
        assertNotSame(first, second);
        assertFalse(first.isAttached());
        assertEquals(7, first.getDepth());
        assertSame(second, list.atDepth(7));
        list.remove(first);
        list.swapDepths(first, 20);
        assertSame(second, list.atDepth(7));
        assertEquals(1, list.size());
        list.remove(second);
        assertFalse(second.isAttached());
        assertNull(list.atDepth(7));
        assertEquals(0, list.nextHighestDepth());
    }

    @Test public void samePayloadMayOccupySeveralDepthsAndMaximumCanDecrease() {
        Object payload = new Object();
        SourceDisplayList<Object> list = new SourceDisplayList<>(1);
        SourceDisplayList.Entry<Object> low = list.attach(payload, 2);
        SourceDisplayList.Entry<Object> high = list.attach(payload, 9);
        assertSame(payload, low.getValue());
        assertSame(payload, high.getValue());
        assertNotSame(low, high);
        assertEquals(10, list.nextHighestDepth());
        list.remove(high);
        assertEquals(3, list.nextHighestDepth());
        assertSame(low, list.atDepth(2));
    }

    @Test public void occupiedAndEmptySwapsMaintainAscendingOrderAndIdentities() {
        SourceDisplayList<String> list = new SourceDisplayList<>(1);
        SourceDisplayList.Entry<String> a = list.attach("a", 8);
        SourceDisplayList.Entry<String> b = list.attach("b", 2);
        SourceDisplayList.Entry<String> c = list.attach("c", 5);
        assertSame(b, list.entryAt(0));
        assertSame(c, list.entryAt(1));
        assertSame(a, list.entryAt(2));
        list.swapDepths(a, b);
        assertSame(a, list.atDepth(2));
        assertSame(b, list.atDepth(8));
        assertEquals(2, a.getDepth());
        assertEquals(8, b.getDepth());
        list.swapDepths(c, 11);
        assertNull(list.atDepth(5));
        assertSame(c, list.entryAt(2));
        assertEquals(12, list.nextHighestDepth());
        list.swapDepths(c, 0);
        assertSame(c, list.entryAt(0));
        assertSame(a, list.entryAt(1));
        assertSame(b, list.entryAt(2));
        assertEquals(9, list.nextHighestDepth());
        list.swapDepths(a, 2);
        assertSame(a, list.atDepth(2));
    }

    @Test public void crossParentAndDetachedClipSwapsAreNoOps() {
        SourceDisplayList<String> left = new SourceDisplayList<>();
        SourceDisplayList<String> right = new SourceDisplayList<>();
        SourceDisplayList.Entry<String> a = left.attach("a", 3);
        SourceDisplayList.Entry<String> b = right.attach("b", 5);
        left.swapDepths(a, b);
        left.swapDepths(b, 9);
        left.remove(b);
        assertSame(a, left.atDepth(3));
        assertSame(b, right.atDepth(5));
        right.clear();
        left.swapDepths(a, b);
        assertSame(a, left.atDepth(3));
        assertFalse(b.isAttached());
    }

    @Test public void clearDetachesEveryHandleAndReleasesAllOrderedEntries() {
        SourceDisplayList<Integer> list = new SourceDisplayList<>(0);
        SourceDisplayList.Entry<Integer> negative = list.attach(1, -4);
        SourceDisplayList.Entry<Integer> zero = list.attach(2, 0);
        SourceDisplayList.Entry<Integer> high = list.attach(3, 20);
        list.clear();
        assertEquals(0, list.size());
        assertEquals(0, list.nextHighestDepth());
        assertFalse(negative.isAttached());
        assertFalse(zero.isAttached());
        assertFalse(high.isAttached());
        assertEquals(20, high.getDepth());
        list.remove(high);
        list.swapDepths(high, 1);
        assertNull(list.atDepth(20));
        SourceDisplayList.Entry<Integer> fresh = list.attach(4, 0);
        assertSame(fresh, list.entryAt(0));
        assertThrows(IndexOutOfBoundsException.class, () -> list.entryAt(1));
    }

    @Test public void maximumIntegerDepthCannotOverflow() {
        SourceDisplayList<String> list = new SourceDisplayList<>();
        list.attach("top", Integer.MAX_VALUE);
        assertThrows(IllegalStateException.class, list::nextHighestDepth);
    }
}
