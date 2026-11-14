package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;

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

    /** BioCloud.as display fields retain double precision until the final draw cast. */
    private static final class SourceCloud {
        double x;
        double y;
        double alphaFactor;
        // MovieClip stores the scale as a matrix factor. Its _xscale getter
        // converts that factor back to percent before each += assignment.
        double scaleFactor;
        int alphaDecay;
        int sizeDecay;
        int horizontalTicks;
        int horizontalDirection;

        void spawn(ReferenceRandom random) {
            // BioCloud.spawn has exactly seven inclusive Tools.randRange calls.
            x = randomInclusive(random, MIN_X, MAX_X);
            y = randomInclusive(random, MIN_Y, MAX_Y);
            alphaFactor = randomInclusive(random, MIN_ALPHA, MAX_ALPHA) / 100d;
            scaleFactor = randomInclusive(random, MIN_SCALE, MAX_SCALE) / 100d;
            alphaDecay = randomInclusive(random, MIN_ALPHA_DECAY, MAX_ALPHA_DECAY);
            sizeDecay = randomInclusive(random, MIN_SIZE_DECAY, MAX_SIZE_DECAY);
            horizontalTicks = randomInclusive(random,
                    -HORIZONTAL_FLOAT_TICKS, HORIZONTAL_FLOAT_TICKS);
            horizontalDirection = 1;
        }

        void tick(ReferenceRandom random) {
            if (alphaPercent() <= 0d) {
                spawn(random);
                return;
            }
            x += 0.3d * horizontalDirection;
            horizontalTicks--;
            if (horizontalTicks <= 0) {
                horizontalDirection *= -1;
                horizontalTicks = HORIZONTAL_FLOAT_TICKS;
            }
            y -= 2d;
            alphaFactor = (alphaPercent() - alphaDecay) / 100d;
            double currentScale = scaleFactor * 100d;
            scaleFactor *= (currentScale + sizeDecay) / currentScale;
        }

        double alphaPercent() { return alphaFactor * 100d; }
        double scalePercent() { return scaleFactor * 100d; }
    }

    private final Random random;
    private final Cloud[] clouds = new Cloud[CLOUD_COUNT];
    private final ReferenceRandom sourceRandom;
    private final SourceCloud[] sourceClouds;

    public BioloadCloudAnimation(long seed) {
        this(new Random(seed));
    }

    BioloadCloudAnimation(Random random) {
        if (random == null) {
            throw new IllegalArgumentException("Cloud animation random cannot be null");
        }
        this.random = random;
        sourceRandom = null;
        sourceClouds = null;
        for (int index = 0; index < clouds.length; index++) {
            clouds[index] = new Cloud();
            clouds[index].spawn(random);
        }
    }

    private BioloadCloudAnimation(ReferenceRandom sharedBattleRandom) {
        if (sharedBattleRandom == null) {
            throw new IllegalArgumentException("Source cloud random cannot be null");
        }
        random = null;
        sourceRandom = sharedBattleRandom;
        sourceClouds = new SourceCloud[CLOUD_COUNT];
        // View.createBioCloud attaches children at depths 0..16, then AS2's
        // `for (i in array)` enumerates the array backwards for initial spawn.
        for (int index = 0; index < sourceClouds.length; index++) {
            sourceClouds[index] = new SourceCloud();
        }
        for (int index = sourceClouds.length - 1; index >= 0; index--) {
            sourceClouds[index].spawn(sourceRandom);
        }
    }

    /** Shares the private battle stream with source action and unit callbacks. */
    public static BioloadCloudAnimation fromSource(ReferenceRandom sharedBattleRandom) {
        return new BioloadCloudAnimation(sharedBattleRandom);
    }

    /** View.createBioCloud respawns every retained child's fields, newest or detached. */
    public void respawnSource() {
        if (sourceClouds == null) {
            throw new IllegalStateException("Not a source battle cloud animation");
        }
        for (int index = sourceClouds.length - 1; index >= 0; index--) {
            sourceClouds[index].spawn(sourceRandom);
        }
    }

    /** Advances the 24 Hz source-style float, rise, fade, and respawn behavior. */
    public void tick() {
        if (sourceClouds != null) {
            // MovieClipHost walks attached children by ascending physical depth.
            for (SourceCloud cloud : sourceClouds) cloud.tick(sourceRandom);
            return;
        }
        for (Cloud cloud : clouds) {
            cloud.tick(random);
        }
    }

    public float getX(int index) {
        return sourceClouds == null ? cloud(index).x : (float) sourceCloud(index).x;
    }

    public float getY(int index) {
        return sourceClouds == null ? cloud(index).y : (float) sourceCloud(index).y;
    }

    public float getAlpha(int index) {
        return sourceClouds == null ? cloud(index).alpha : (float) sourceCloud(index).alphaPercent();
    }

    public float getScalePercent(int index) {
        return sourceClouds == null ? cloud(index).scale : (float) sourceCloud(index).scalePercent();
    }

    public double getSourceX(int index) { return sourceCloud(index).x; }
    public double getSourceY(int index) { return sourceCloud(index).y; }
    public double getSourceAlpha(int index) { return sourceCloud(index).alphaPercent(); }
    public double getSourceScalePercent(int index) { return sourceCloud(index).scalePercent(); }
    public int getSourceAlphaDecay(int index) { return sourceCloud(index).alphaDecay; }
    public int getSourceSizeDecay(int index) { return sourceCloud(index).sizeDecay; }
    public int getSourceHorizontalTicks(int index) { return sourceCloud(index).horizontalTicks; }
    public int getSourceHorizontalDirection(int index) {
        return sourceCloud(index).horizontalDirection;
    }

    private SourceCloud sourceCloud(int index) {
        if (sourceClouds == null) {
            throw new IllegalStateException("Not a source battle cloud animation");
        }
        if (index < 0 || index >= CLOUD_COUNT) {
            throw new IndexOutOfBoundsException("Cloud index must be 0..16");
        }
        return sourceClouds[index];
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
