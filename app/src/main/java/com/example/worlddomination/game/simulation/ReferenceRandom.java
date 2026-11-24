package com.example.worlddomination.game.simulation;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Reproduces the seeded CPython random stream used by the recovered runtime.
 * Bounded integer draws mirror Tools.randRange: floor(random() * width).
 */
public final class ReferenceRandom extends Random {
    private static final long serialVersionUID = 1L;
    private static final int STATE_SIZE = 624;
    private static final int PERIOD = 397;
    private static final int MATRIX_A = 0x9908b0df;
    private static final int UPPER_MASK = 0x80000000;
    private static final int LOWER_MASK = 0x7fffffff;

    private final int[] state;
    private int stateIndex;

    public ReferenceRandom(long seed) {
        super(0L);
        state = new int[STATE_SIZE];
        setSeed(seed);
    }

    private ReferenceRandom(int[] state, int stateIndex) {
        super(0L);
        this.state = state;
        this.stateIndex = stateIndex;
    }

    @Override
    protected synchronized int next(int bits) {
        if (bits < 0 || bits > Integer.SIZE) {
            throw new IllegalArgumentException("bits must be between 0 and 32");
        }
        return bits == 0 ? 0 : nextUnsignedInt() >>> (Integer.SIZE - bits);
    }

    @Override
    public synchronized void setSeed(long seed) {
        if (state == null) {
            // Random's constructor calls this override before this object's
            // fields have been initialized.
            super.setSeed(seed);
            return;
        }
        seedFromInteger(seed);
    }

    @Override
    public synchronized double nextDouble() {
        int high = nextUnsignedInt() >>> 5;
        int low = nextUnsignedInt() >>> 6;
        return (high * 67108864.0 + low) / 9007199254740992.0;
    }

    @Override
    public synchronized float nextFloat() {
        return (float) nextDouble();
    }

    @Override
    public synchronized int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        return (int) (nextDouble() * bound);
    }

    @Override
    public synchronized boolean nextBoolean() {
        return nextInt(2) != 0;
    }

    /** Returns an isolated copy for presentation code that must not advance game RNG. */
    public synchronized ReferenceRandom copy() {
        return new ReferenceRandom(state.clone(), stateIndex);
    }

    synchronized void writeCheckpoint(DataOutput output) throws IOException {
        output.writeInt(stateIndex);
        for (int word : state) output.writeInt(word);
    }

    static ReferenceRandom readCheckpoint(DataInput input) throws IOException {
        int index = input.readInt();
        if (index < 0 || index > STATE_SIZE) {
            throw new IOException("Invalid random stream position");
        }
        int[] words = new int[STATE_SIZE];
        int nonzeroBits = 0;
        for (int i = 0; i < words.length; i++) {
            words[i] = input.readInt();
            nonzeroBits |= words[i];
        }
        if (nonzeroBits == 0) throw new IOException("Invalid empty random stream");
        return new ReferenceRandom(words, index);
    }

    private void seedFromInteger(long seed) {
        BigInteger remaining = BigInteger.valueOf(seed).abs();
        List<Integer> words = new ArrayList<>(2);
        do {
            words.add(remaining.intValue());
            remaining = remaining.shiftRight(32);
        } while (remaining.signum() != 0);

        int[] key = new int[words.size()];
        for (int index = 0; index < words.size(); index++) {
            key[index] = words.get(index);
        }
        initializeByArray(key);
    }

    private void initializeByArray(int[] key) {
        state[0] = 19650218;
        for (int index = 1; index < STATE_SIZE; index++) {
            int previous = state[index - 1];
            state[index] = (1812433253 * (previous ^ (previous >>> 30))) + index;
        }

        int stateIndex = 1;
        int keyIndex = 0;
        int count = Math.max(STATE_SIZE, key.length);
        for (; count > 0; count--) {
            int previous = state[stateIndex - 1];
            state[stateIndex] = (state[stateIndex]
                    ^ ((previous ^ (previous >>> 30)) * 1664525))
                    + key[keyIndex] + keyIndex;
            stateIndex++;
            keyIndex++;
            if (stateIndex >= STATE_SIZE) {
                state[0] = state[STATE_SIZE - 1];
                stateIndex = 1;
            }
            if (keyIndex >= key.length) {
                keyIndex = 0;
            }
        }

        for (count = STATE_SIZE - 1; count > 0; count--) {
            int previous = state[stateIndex - 1];
            state[stateIndex] = (state[stateIndex]
                    ^ ((previous ^ (previous >>> 30)) * 1566083941)) - stateIndex;
            stateIndex++;
            if (stateIndex >= STATE_SIZE) {
                state[0] = state[STATE_SIZE - 1];
                stateIndex = 1;
            }
        }
        state[0] = UPPER_MASK;
        this.stateIndex = STATE_SIZE;
    }

    private int nextUnsignedInt() {
        if (stateIndex >= STATE_SIZE) {
            twist();
        }

        int value = state[stateIndex++];
        value ^= value >>> 11;
        value ^= (value << 7) & 0x9d2c5680;
        value ^= (value << 15) & 0xefc60000;
        value ^= value >>> 18;
        return value;
    }

    private void twist() {
        for (int index = 0; index < STATE_SIZE; index++) {
            int nextIndex = (index + 1) % STATE_SIZE;
            int periodIndex = (index + PERIOD) % STATE_SIZE;
            int value = (state[index] & UPPER_MASK) | (state[nextIndex] & LOWER_MASK);
            state[index] = state[periodIndex] ^ (value >>> 1)
                    ^ ((value & 1) == 0 ? 0 : MATRIX_A);
        }
        stateIndex = 0;
    }
}
