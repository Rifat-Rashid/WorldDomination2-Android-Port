package com.example.worlddomination.game.rendering;

/** Channel transforms recovered from the original Flash WD_colors palette. */
public final class FlashColorTransform {
    public final float redMultiplier;
    public final float redAdd;
    public final float greenMultiplier;
    public final float greenAdd;
    public final float blueMultiplier;
    public final float blueAdd;
    public final float alphaMultiplier;
    public final float alphaAdd;

    private FlashColorTransform(float redMultiplier, float redAdd,
                                float greenMultiplier, float greenAdd,
                                float blueMultiplier, float blueAdd,
                                float alphaMultiplier, float alphaAdd) {
        this.redMultiplier = redMultiplier;
        this.redAdd = redAdd;
        this.greenMultiplier = greenMultiplier;
        this.greenAdd = greenAdd;
        this.blueMultiplier = blueMultiplier;
        this.blueAdd = blueAdd;
        this.alphaMultiplier = alphaMultiplier;
        this.alphaAdd = alphaAdd;
    }

    public static final FlashColorTransform IDENTITY = source(1, 1, 1, 1, 0, 0, 0, 0);
    // WD_colors.city_red/city_green: percentage multipliers and byte additions.
    public static final FlashColorTransform CITY_RED = source(1, 1, 1, .75f, 200, 0, 0, 0);
    public static final FlashColorTransform CITY_GREEN = source(.5f, 1, .5f, .75f, 0, 100, 0, 0);

    /** Flash multipliers are normalized; additive channels are source 0..255 units. */
    public static FlashColorTransform source(float red, float green, float blue, float alpha,
                                              float addRed, float addGreen, float addBlue,
                                              float addAlpha) {
        return new FlashColorTransform(red, addRed / 255f, green, addGreen / 255f,
                blue, addBlue / 255f, alpha, addAlpha / 255f);
    }

    /** Reused during traversal: the child color transform is applied before its parent. */
    public static final class Composition {
        public float redMultiplier, greenMultiplier, blueMultiplier, alphaMultiplier;
        public float redAdd, greenAdd, blueAdd, alphaAdd;

        public Composition() { identity(); }

        public Composition identity() {
            redMultiplier = greenMultiplier = blueMultiplier = alphaMultiplier = 1;
            redAdd = greenAdd = blueAdd = alphaAdd = 0;
            return this;
        }

        public Composition setProduct(Composition parent, FlashColorTransform child) {
            redAdd = parent.redMultiplier * child.redAdd + parent.redAdd;
            greenAdd = parent.greenMultiplier * child.greenAdd + parent.greenAdd;
            blueAdd = parent.blueMultiplier * child.blueAdd + parent.blueAdd;
            alphaAdd = parent.alphaMultiplier * child.alphaAdd + parent.alphaAdd;
            redMultiplier = parent.redMultiplier * child.redMultiplier;
            greenMultiplier = parent.greenMultiplier * child.greenMultiplier;
            blueMultiplier = parent.blueMultiplier * child.blueMultiplier;
            alphaMultiplier = parent.alphaMultiplier * child.alphaMultiplier;
            return this;
        }

        public boolean isIdentity() {
            return redMultiplier == 1 && greenMultiplier == 1 && blueMultiplier == 1
                    && alphaMultiplier == 1 && redAdd == 0 && greenAdd == 0
                    && blueAdd == 0 && alphaAdd == 0;
        }
    }

    /** Returns the source ColorTransform for setup colors 1 (blue) through 5 (green). */
    public static FlashColorTransform forPlayerColor(int playerColor) {
        switch (playerColor) {
            case 1:
                return percentAndByte(15, 0, 74, 0, 100, 255, 100, 0);
            case 2:
                return percentAndByte(50, 255, 0, 0, 0, 0, 75, 0);
            case 3:
                return percentAndByte(100, 91, 62, 40, 0, 0, 100, 0);
            case 4:
                return percentAndByte(50, 255, 50, 255, 0, 0, 75, 0);
            case 5:
                return percentAndByte(0, 0, 50, 255, 0, 0, 75, 0);
            default:
                throw new IllegalArgumentException("Player color must be 1..5");
        }
    }

    private static FlashColorTransform percentAndByte(
            int redMultiplier, int redAdd, int greenMultiplier, int greenAdd,
            int blueMultiplier, int blueAdd, int alphaMultiplier, int alphaAdd) {
        return new FlashColorTransform(redMultiplier / 100f, redAdd / 255f,
                greenMultiplier / 100f, greenAdd / 255f,
                blueMultiplier / 100f, blueAdd / 255f,
                alphaMultiplier / 100f, alphaAdd / 255f);
    }
}
