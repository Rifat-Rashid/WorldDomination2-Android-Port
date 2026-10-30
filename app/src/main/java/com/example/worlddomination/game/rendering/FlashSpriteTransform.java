package com.example.worlddomination.game.rendering;

/** Decomposes a Flash 2D matrix into the rotation, reflection, and scale SpriteBatch accepts. */
public final class FlashSpriteTransform {
    public final float scaleX;
    public final float scaleY;
    public final float rotationDegrees;

    private FlashSpriteTransform(float scaleX, float scaleY, float rotationDegrees) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.rotationDegrees = rotationDegrees;
    }

    public static FlashSpriteTransform fromMatrix(float a, float b, float c, float d) {
        float determinant = a * d - b * c;
        float xLength = (float) Math.hypot(a, b);
        float yLength = (float) Math.hypot(c, d);
        if (Math.abs(determinant) <= 0.0001f || xLength == 0f || yLength == 0f) {
            throw new IllegalArgumentException("Flash transform must be invertible");
        }
        float dotProduct = a * c + b * d;
        if (Math.abs(dotProduct) > 0.01f * xLength * yLength) {
            throw new IllegalArgumentException("Flash transform cannot contain shear");
        }
        float scaleX = determinant < 0f ? -xLength : xLength;
        float rotation = (float) Math.toDegrees(Math.atan2(-b / scaleX, a / scaleX));
        return new FlashSpriteTransform(scaleX, yLength, rotation);
    }
}
