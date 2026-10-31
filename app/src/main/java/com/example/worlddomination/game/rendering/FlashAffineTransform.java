package com.example.worlddomination.game.rendering;

/** Reusable Flash affine transform used while walking nested display-list symbols. */
final class FlashAffineTransform {
    float a = 1f;
    float b;
    float c;
    float d = 1f;
    float tx;
    float ty;
    float scaleX = 1f;
    float scaleY = 1f;
    float rotationDegrees;

    FlashAffineTransform setIdentity() {
        a = 1f;
        b = 0f;
        c = 0f;
        d = 1f;
        tx = 0f;
        ty = 0f;
        scaleX = 1f;
        scaleY = 1f;
        rotationDegrees = 0f;
        return this;
    }

    /** Sets this matrix to {@code parent * local} without allocating a new object. */
    FlashAffineTransform setProduct(FlashAffineTransform parent,
                                    float localA, float localB,
                                    float localC, float localD,
                                    float localTx, float localTy) {
        a = parent.a * localA + parent.c * localB;
        b = parent.b * localA + parent.d * localB;
        c = parent.a * localC + parent.c * localD;
        d = parent.b * localC + parent.d * localD;
        tx = parent.a * localTx + parent.c * localTy + parent.tx;
        ty = parent.b * localTx + parent.d * localTy + parent.ty;

        float determinant = a * d - b * c;
        float xLength = (float) Math.hypot(a, b);
        float yLength = (float) Math.hypot(c, d);
        if (Math.abs(determinant) <= 0.0001f || xLength == 0f || yLength == 0f) {
            scaleX = 0f;
            scaleY = 0f;
            rotationDegrees = 0f;
        } else {
            scaleX = determinant < 0f ? -xLength : xLength;
            scaleY = yLength;
            rotationDegrees = (float) Math.toDegrees(
                    Math.atan2(-b / scaleX, a / scaleX));
        }
        return this;
    }
}
