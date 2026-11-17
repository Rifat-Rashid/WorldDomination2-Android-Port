package com.example.worlddomination.game.assets;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public final class ArgbPixelTransferTest {
    @Test public void channelAndAlphaEdgesMatchTheFourByteReference() {
        int[] edges = {0, 1, 127, 128, 254, 255};
        int[] pixels = new int[edges.length * edges.length * edges.length * edges.length];
        int index = 0;
        for (int a : edges) for (int r : edges)
            for (int g : edges) for (int b : edges)
                pixels[index++] = a << 24 | r << 16 | g << 8 | b;
        for (boolean direct : new boolean[]{false, true}) {
            for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN})
                assertTransfer(pixels, direct, order, 0);
        }
    }

    @Test public void seededArgbWordsMatchForDirectAndHeapBuffersWithOffsets() {
        int[] pixels = new int[10003];
        Random random = new Random(0x51e4b17L);
        for (int i = 0; i < pixels.length; i++) pixels[i] = random.nextInt();
        for (boolean direct : new boolean[]{false, true}) {
            for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN})
                assertTransfer(pixels, direct, order, 3);
        }
    }

    @Test public void insufficientRemainingDoesNotMutateSourceOrTarget() {
        int[] pixels = {0x12345678, 0x90abcdef};
        ByteBuffer target = ByteBuffer.allocateDirect(12).order(ByteOrder.LITTLE_ENDIAN);
        target.position(2);
        target.limit(9); // Seven writable bytes for eight required bytes.
        try {
            ArgbPixelTransfer.intoRgba8888(pixels, target);
            fail("Expected a short-buffer failure");
        } catch (IllegalArgumentException expected) {
            assertArrayEquals(new int[]{0x12345678, 0x90abcdef}, pixels);
            assertEquals(2, target.position());
            assertEquals(9, target.limit());
            assertEquals(ByteOrder.LITTLE_ENDIAN, target.order());
            ByteBuffer inspection = target.duplicate();
            inspection.clear();
            for (int i = 0; i < target.capacity(); i++) assertEquals(0, inspection.get(i));
        }
    }

    private static void assertTransfer(int[] source, boolean direct, ByteOrder order, int offset) {
        int[] owned = source.clone();
        int size = offset + source.length * 4 + 5;
        ByteBuffer target = direct ? ByteBuffer.allocateDirect(size) : ByteBuffer.allocate(size);
        target.order(order);
        for (int i = 0; i < size; i++) target.put(i, (byte) 0x5a);
        target.position(offset);
        target.limit(offset + source.length * 4 + 2);
        int originalPosition = target.position(), originalLimit = target.limit();
        ArgbPixelTransfer.intoRgba8888(owned, target);
        assertEquals(originalPosition, target.position());
        assertEquals(originalLimit, target.limit());
        assertEquals(order, target.order());
        byte[] expected = new byte[size];
        Arrays.fill(expected, (byte) 0x5a);
        for (int i = 0; i < source.length; i++) {
            int argb = source[i], at = offset + i * 4;
            expected[at] = (byte) (argb >>> 16);
            expected[at + 1] = (byte) (argb >>> 8);
            expected[at + 2] = (byte) argb;
            expected[at + 3] = (byte) (argb >>> 24);
        }
        ByteBuffer inspection = target.duplicate();
        inspection.clear();
        for (int i = 0; i < size; i++)
            assertEquals("direct=" + direct + " order=" + order + " byte=" + i,
                    expected[i], inspection.get(i));
    }
}
