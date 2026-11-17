package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.math.Rectangle;

/** Continuous piecewise mapping of the complete shape 5813 carousel crop. */
public final class SourceCarouselMesh {
    private static final float[] X = {147, 172, 173, 176, 186, 237, 247, 311, 321,
            385, 395, 459, 469, 533, 543, 596, 606, 607, 610, 635};
    private static final float[] Y = {319, 331, 339, 409, 428, 438, 467, 477, 487, 499};
    private final float[][] mappedX = new float[Y.length][X.length];
    private final float[] mappedY = new float[Y.length];
    private final float[] anchorX = new float[X.length];
    private final float[] previous = new float[32];
    private boolean initialized;
    private boolean uniform;

    public int columns() { return X.length; }
    public int rows() { return Y.length; }
    public float sourceX(int column) { return X[column]; }
    public float sourceY(int row) { return Y[row]; }
    public float destinationX(int row, int column) { return mappedX[row][column]; }
    public float destinationY(int row) { return mappedY[row]; }
    public boolean isUniform() { return initialized && uniform; }

    /** Fits each 64x70 well inside its hit rectangle, retaining source aspect. */
    public boolean update(Rectangle frame, Rectangle[] wells, Rectangle inset) {
        if (wells.length != 6) throw new IllegalArgumentException("Six wells required");
        int n = 0;
        boolean changed = !initialized;
        for (int i = 0; i < 8; i++) {
            Rectangle r = i == 0 ? frame : i == 7 ? inset : wells[i - 1];
            for (int component = 0; component < 4; component++) {
                float value = component == 0 ? r.x : component == 1 ? r.y
                        : component == 2 ? r.width : r.height;
                if (previous[n] != value) changed = true;
                previous[n++] = value;
            }
        }
        if (!changed) return false;
        initialized = false;
        uniform = false;
        float rail = Math.min(frame.width / 488f, frame.height / 180f);
        rail = Math.min(rail, Math.min(inset.x - frame.x,
                frame.x + frame.width - inset.x - inset.width) / 29f);
        float border = Math.min(rail, Math.min(inset.width / 21f, inset.height / 21f));
        float wellScale = Math.min(wells[0].width / 64f, wells[0].height / 70f);
        float wellBottom = wells[0].y + (wells[0].height - 70f * wellScale) / 2f;
        mappedY[0] = frame.y + frame.height;
        mappedY[2] = wellBottom + 70f * wellScale;
        mappedY[1] = mappedY[0] - Math.min(12f * rail, (mappedY[0] - mappedY[2]) * .75f);
        mappedY[3] = wellBottom;
        mappedY[4] = inset.y + inset.height;
        mappedY[5] = mappedY[4] - 10f * border;
        mappedY[6] = inset.y + 10f * border;
        mappedY[7] = inset.y;
        mappedY[8] = frame.y + Math.min(12f * rail, (inset.y - frame.y) * .75f);
        mappedY[9] = frame.y;
        for (int row = 0; row < Y.length; row++) {
            for (int c = 0; c < X.length; c++) anchorX[c] = Float.NaN;
            anchor(147, frame.x); anchor(172, frame.x + 25f * rail);
            anchor(610, frame.x + frame.width - 25f * rail);
            anchor(635, frame.x + frame.width);
            if (row == 2 || row == 3) {
                for (int i = 0; i < 6; i++) {
                    Rectangle well = wells[i];
                    float scale = Math.min(well.width / 64f, well.height / 70f);
                    float left = well.x + (well.width - 64f * scale) / 2f;
                    anchor(173 + 74 * i, left);
                    anchor(237 + 74 * i, left + 64f * scale);
                }
            } else if (row >= 4 && row <= 7) {
                anchor(176, inset.x); anchor(186, inset.x + 10f * border);
                anchor(596, inset.x + inset.width - 10f * border);
                anchor(606, inset.x + inset.width);
            }
            int left = 0;
            for (int right = 1; right < X.length; right++) {
                if (Float.isNaN(anchorX[right])) continue;
                for (int c = left; c <= right; c++)
                    mappedX[row][c] = anchorX[left] + (anchorX[right] - anchorX[left])
                            * (X[c] - X[left]) / (X[right] - X[left]);
                left = right;
            }
        }
        for (int row = 0; row < Y.length; row++) {
            if (row > 0 && mappedY[row] >= mappedY[row - 1])
                throw new IllegalArgumentException("Carousel rows overlap");
            for (int col = 1; col < X.length; col++)
                if (mappedX[row][col] <= mappedX[row][col - 1])
                    throw new IllegalArgumentException("Carousel columns overlap");
        }
        float sx = frame.width / 488f, sy = frame.height / 180f;
        uniform = close(sx, sy)
                && close(inset.x, frame.x + 29f * sx)
                && close(inset.y, frame.y + 22f * sy)
                && close(inset.width, 430f * sx)
                && close(inset.height, 49f * sy);
        for (int i = 0; i < 6 && uniform; i++) {
            Rectangle well = wells[i];
            float scale = Math.min(well.width / 64f, well.height / 70f);
            float x = well.x + (well.width - 64f * scale) / 2f;
            float y = well.y + (well.height - 70f * scale) / 2f;
            uniform = close(x, frame.x + (26f + 74f * i) * sx)
                    && close(y, frame.y + 90f * sy)
                    && close(64f * scale, 64f * sx)
                    && close(70f * scale, 70f * sy);
        }
        initialized = true;
        return true;
    }

    // Equivalent float expressions for a transformed source rectangle can differ
    // by a few rounding steps. This bound is in ULPs, not screen pixels.
    private static boolean close(float a, float b) {
        return Math.abs(a - b) <= 8f * Math.max(Math.ulp(a), Math.ulp(b));
    }

    private void anchor(float source, float destination) {
        for (int i = 0; i < X.length; i++) if (X[i] == source) { anchorX[i] = destination; return; }
        throw new IllegalArgumentException("Missing source knot");
    }
}
