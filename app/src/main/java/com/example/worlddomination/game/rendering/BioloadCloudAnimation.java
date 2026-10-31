package com.example.worlddomination.game.rendering;

import java.util.Random;

/** One city's source-timed, visual-only biological cloud field. */
public final class BioloadCloudAnimation {
    public static final int CLOUD_COUNT = 17;

    private static final int MIN_X = -25;
    private static final int MAX_X = 25;
    private static final int MIN_Y = -20;
    private static final int MAX_Y = 0;
    private static final int MIN_ALPHA = 30;
    private static final int MAX_ALPHA = 50;
    private static final int MIN_SCALE = 40;
    private static final int MAX_SCALE = 80;
    private static final int MIN_ALPHA_DECAY = 1;
    private static final int MAX_ALPHA_DECAY = 3;
    private static final int MIN_SIZE_DECAY = 2;
    private static final int MAX_SIZE_DECAY = 15;
    private static final int HORIZONTAL_FLOAT_TICKS = 10;
    private static final float HORIZONTAL_SPEED = 0.3f;
    private static final float VERTICAL_SPEED = 2f;

    private static final class Cloud {
        float x;
        float y;
        float alpha;
        float scale;
        int alphaDecay;
        int sizeDecay;
        int horizontalTicks;
        int horizontalDirection;

        void spawn(Random random) {
            x = randomInclusive(random, MIN_X, MAX_X);
            y = randomInclusive(random, MIN_Y, MAX_Y);
            alpha = randomInclusive(random, MIN_ALPHA, MAX_ALPHA);
            scale = randomInclusive(random, MIN_SCALE, MAX_SCALE);
            alphaDecay = randomInclusive(random, MIN_ALPHA_DECAY, MAX_ALPHA_DECAY);
            sizeDecay = randomInclusive(random, MIN_SIZE_DECAY, MAX_SIZE_DECAY);
            horizontalTicks = randomInclusive(random,
                    -HORIZONTAL_FLOAT_TICKS, HORIZONTAL_FLOAT_TICKS);
            horizontalDirection = 1;
        }

        void tick(Random random) {
            if (alpha <= 0f) {
                spawn(random);
                return;
            }
            x += HORIZONTAL_SPEED * horizontalDirection;
            horizontalTicks--;
            if (horizontalTicks <= 0) {
                horizontalDirection *= -1;
                horizontalTicks = HORIZONTAL_FLOAT_TICKS;
            }
            y -= VERTICAL_SPEED;
            alpha -= alphaDecay;
            scale += sizeDecay;
        }
    }

    private final Random random;
    private final Cloud[] clouds = new Cloud[CLOUD_COUNT];

    public BioloadCloudAnimation(long seed) {
        this(new Random(seed));
    }

    BioloadCloudAnimation(Random random) {
        if (random == null) {
            throw new IllegalArgumentException("Cloud animation random cannot be null");
        }
        this.random = random;
        for (int index = 0; index < clouds.length; index++) {
            clouds[index] = new Cloud();
            clouds[index].spawn(random);
        }
    }

    /** Advances the 24 Hz source-style float, rise, fade, and respawn behavior. */
    public void tick() {
        for (Cloud cloud : clouds) {
            cloud.tick(random);
        }
    }

    public float getX(int index) {
        return cloud(index).x;
    }

    public float getY(int index) {
        return cloud(index).y;
    }

    public float getAlpha(int index) {
        return cloud(index).alpha;
    }

    public float getScalePercent(int index) {
        return cloud(index).scale;
    }

    private Cloud cloud(int index) {
        if (index < 0 || index >= clouds.length) {
            throw new IndexOutOfBoundsException("Cloud index must be 0..16");
        }
        return clouds[index];
    }

    private static int randomInclusive(Random random, int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }
}
