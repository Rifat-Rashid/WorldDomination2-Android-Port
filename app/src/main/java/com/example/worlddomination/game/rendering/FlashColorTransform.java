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
