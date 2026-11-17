package com.example.worlddomination.game.assets;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.util.Objects;

/** Copies Android getPixels ARGB words into libGDX RGBA8888 storage. */
final class ArgbPixelTransfer {
    private ArgbPixelTransfer() { }

    static void intoRgba8888(int[] ownedArgb, ByteBuffer target) {
        Objects.requireNonNull(ownedArgb, "ownedArgb");
        Objects.requireNonNull(target, "target");
        if (target.isReadOnly()) throw new ReadOnlyBufferException();
        if (ownedArgb.length > target.remaining() / Integer.BYTES)
            throw new IllegalArgumentException("RGBA buffer is too small");

        // The caller owns this temporary getPixels array. Rotate each ARGB word
        // to RRGGBBAA; a big-endian int view then writes the identical four bytes
        // as the former per-channel put loop, independent of target byte order.
        for (int i = 0; i < ownedArgb.length; i++) {
            int argb = ownedArgb[i];
            ownedArgb[i] = (argb << 8) | (argb >>> 24);
        }
        target.duplicate().order(ByteOrder.BIG_ENDIAN).asIntBuffer().put(ownedArgb);
    }
}
