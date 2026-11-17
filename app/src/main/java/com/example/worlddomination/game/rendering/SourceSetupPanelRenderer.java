package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;

/** Responsive dark plates retaining the corner and rail artwork of Flash shape 5813. */
public final class SourceSetupPanelRenderer implements Disposable {
    public enum Plate { DIFFICULTY, STYLE, ENEMIES, START }
    private static final int[] TOP = {12, 84, 199, 534};
    private static final int[] BOTTOM = {82, 198, 529, 595};
    private static final int[] TOP_CAP = {16, 22, 22, 18};
    private static final int[] BOTTOM_CAP = {16, 23, 23, 18};
    private static final int BODY_X = 143, BODY_Y = 106, BODY_WIDTH = 494, BODY_HEIGHT = 69;
    private final Texture texture;
    private final TextureRegion[][] pieces = new TextureRegion[Plate.values().length][8];
    private final TextureRegion body;
    private final TextureRegion[] carouselFrame = new TextureRegion[9];
    private final TextureRegion[] carouselInset = new TextureRegion[9];
    private final TextureRegion[] carouselWells = new TextureRegion[6];
    private final float[] vertices = new float[20];

    public SourceSetupPanelRenderer() {
        texture = new Texture(Gdx.files.internal("original/ui/setup-panel-source-5813.png"));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        body = new TextureRegion(texture, BODY_X, BODY_Y, BODY_WIDTH, BODY_HEIGHT);
        // 5813: light frame (147..635,319..499), clear metal at y414;
        // six wells (173+74*i,339,64,70), description inset (176,428,430,49).
        // 25px side caps retain the complete rods/fasteners without crossing
        // into the first or last well (the wells start at x=173 and end at 607).
        initNine(carouselFrame, 147, 319, 488, 180, 25, 12);
        carouselFrame[4] = new TextureRegion(texture, 250, 414, 300, 10);
        for (int i = 0; i < carouselWells.length; i++)
            carouselWells[i] = new TextureRegion(texture, 173 + 74 * i, 339, 64, 70);
        initNine(carouselInset, 176, 428, 430, 49, 10, 10);
        for (int p = 0; p < pieces.length; p++) {
            int top = TOP[p], bottom = BOTTOM[p], th = TOP_CAP[p], bh = BOTTOM_CAP[p];
            int mid = bottom - top - th - bh;
            // x300..600 avoids each plate's baked heading and other controls.
            pieces[p][0] = new TextureRegion(texture, 115, top, 28, th);
            pieces[p][1] = new TextureRegion(texture, 300, top, 300, th);
            pieces[p][2] = new TextureRegion(texture, 637, top, 28, th);
            pieces[p][3] = new TextureRegion(texture, 115, top + th, 28, mid);
            pieces[p][4] = new TextureRegion(texture, 637, top + th, 28, mid);
            pieces[p][5] = new TextureRegion(texture, 115, bottom - bh, 28, bh);
            pieces[p][6] = new TextureRegion(texture, 300, bottom - bh, 300, bh);
            pieces[p][7] = new TextureRegion(texture, 637, bottom - bh, 28, bh);
        }
    }

    /** Compose the authored light panel without stretching its baked wells. */
    public void drawCarousel(SpriteBatch batch, Rectangle bounds, Rectangle[] wells, Rectangle inset) {
        float color = batch.getPackedColor();
        batch.setColor(Color.WHITE);
        drawNine(batch, carouselFrame, bounds, 25f, 12f);
        for (int i = 0; i < carouselWells.length; i++) {
            Rectangle well = wells[i];
            float scale = Math.min(well.width / 64f, well.height / 70f);
            batch.draw(carouselWells[i], well.x + (well.width - 64f * scale) / 2f,
                    well.y + (well.height - 70f * scale) / 2f, 64f * scale, 70f * scale);
        }
        drawNine(batch, carouselInset, inset, 10f, 10f);
        batch.setPackedColor(color);
    }

    private void initNine(TextureRegion[] parts, int x, int y, int width, int height,
                          int side, int cap) {
        int[] xs = {x, x + side, x + width - side};
        int[] ys = {y, y + cap, y + height - cap};
        int[] widths = {side, width - 2 * side, side};
        int[] heights = {cap, height - 2 * cap, cap};
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++)
                parts[row * 3 + col] = new TextureRegion(texture, xs[col], ys[row], widths[col], heights[row]);
    }

    private void drawNine(SpriteBatch batch, TextureRegion[] parts, Rectangle bounds,
                          float side, float cap) {
        float scale = Math.min(1f, Math.min(bounds.width / (2f * side), bounds.height / (2f * cap)));
        float edgeX = side * scale, edgeY = cap * scale;
        for (int row = 0; row < 3; row++) {
            float y = row == 0 ? bounds.y + bounds.height - edgeY : row == 1 ? bounds.y + edgeY : bounds.y;
            float h = row == 1 ? bounds.height - 2f * edgeY : edgeY;
            for (int col = 0; col < 3; col++) {
                float x = col == 0 ? bounds.x : col == 1 ? bounds.x + edgeX : bounds.x + bounds.width - edgeX;
                float w = col == 1 ? bounds.width - 2f * edgeX : edgeX;
                if (w > 0f && h > 0f) batch.draw(parts[row * 3 + col], x, y, w, h);
            }
        }
    }

    public void draw(SpriteBatch batch, Rectangle bounds, Plate plate) {
        if (bounds.width <= 0f || bounds.height <= 0f) return;
        int p = plate.ordinal();
        float scale = Math.min(1f, Math.min(bounds.width / 56f,
                bounds.height / (TOP_CAP[p] + BOTTOM_CAP[p])));
        float side = 28f * scale, th = TOP_CAP[p] * scale, bh = BOTTOM_CAP[p] * scale;
        float mw = bounds.width - 2f * side, mh = bounds.height - th - bh;
        float x = bounds.x, y = bounds.y, right = x + bounds.width - side;
        float middleY = y + bh, topY = y + bounds.height - th;
        TextureRegion[] part = pieces[p];
        float color = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, 1f);
        // Fill beneath the frame so translucent inward edges always overlap
        // source texture, never the unrelated setup background.
        float cropW = BODY_WIDTH, cropH = BODY_HEIGHT;
        if (bounds.width / bounds.height > cropW / cropH) cropH = cropW * bounds.height / bounds.width;
        else cropW = cropH * bounds.width / bounds.height;
        float bx = BODY_X + (BODY_WIDTH - cropW) / 2f;
        float by = BODY_Y + (BODY_HEIGHT - cropH) / 2f;
        body.setRegion(bx / texture.getWidth(), by / texture.getHeight(),
                (bx + cropW) / texture.getWidth(), (by + cropH) / texture.getHeight());
        batch.draw(body, x, y, bounds.width, bounds.height);
        float fade = 6f * scale;
        // Rails run underneath corners, overlapping their fading inner edges.
        faded(batch, part[1], x + side - fade, topY, mw + 2f * fade, th, 0, -1, fade);
        faded(batch, part[6], x + side - fade, y, mw + 2f * fade, bh, 0, 1, fade);
        faded(batch, part[3], x, middleY - fade, side, mh + 2f * fade, 1, 0, fade);
        faded(batch, part[4], right, middleY - fade, side, mh + 2f * fade, -1, 0, fade);
        faded(batch, part[0], x, topY, side, th, 1, -1, fade);
        faded(batch, part[2], right, topY, side, th, -1, -1, fade);
        faded(batch, part[5], x, y, side, bh, 1, 1, fade);
        faded(batch, part[7], right, y, side, bh, -1, 1, fade);
        batch.setPackedColor(color);
    }

    // fadeX/fadeY indicate the inward edge (+1 high edge, -1 low edge).
    // Split each patch at the fade boundary so the source bolts stay opaque.
    private void faded(SpriteBatch batch, TextureRegion region, float x, float y,
                       float width, float height, int fadeX, int fadeY, float fade) {
        if (width <= 0f || height <= 0f) return;
        float fx = fadeX == 0 ? 0f : Math.min(fade / width, 1f);
        float fy = fadeY == 0 ? 0f : Math.min(fade / height, 1f);
        int nx = fadeX == 0 ? 1 : 2, ny = fadeY == 0 ? 1 : 2;
        for (int iy = 0; iy < ny; iy++) {
            float v0 = boundary(iy, ny, fadeY, fy), v1 = boundary(iy + 1, ny, fadeY, fy);
            for (int ix = 0; ix < nx; ix++) {
                float u0 = boundary(ix, nx, fadeX, fx), u1 = boundary(ix + 1, nx, fadeX, fx);
                float x0 = x + width * u0, x1 = x + width * u1;
                float y0 = y + height * v0, y1 = y + height * v1;
                float tu0 = region.getU() + (region.getU2() - region.getU()) * u0;
                float tu1 = region.getU() + (region.getU2() - region.getU()) * u1;
                float tv0 = region.getV2() + (region.getV() - region.getV2()) * v0;
                float tv1 = region.getV2() + (region.getV() - region.getV2()) * v1;
                vertex(0, x0, y0, alpha(u0, fadeX, fx) * alpha(v0, fadeY, fy), tu0, tv0);
                vertex(5, x0, y1, alpha(u0, fadeX, fx) * alpha(v1, fadeY, fy), tu0, tv1);
                vertex(10, x1, y1, alpha(u1, fadeX, fx) * alpha(v1, fadeY, fy), tu1, tv1);
                vertex(15, x1, y0, alpha(u1, fadeX, fx) * alpha(v0, fadeY, fy), tu1, tv0);
                batch.draw(texture, vertices, 0, vertices.length);
            }
        }
    }

    private static float boundary(int i, int count, int direction, float amount) {
        if (i == 0) return 0f;
        if (i == count) return 1f;
        return direction > 0 ? 1f - amount : amount;
    }
    private static float alpha(float value, int direction, float amount) {
        if (direction == 0 || amount == 0f) return 1f;
        return Math.min(1f, direction > 0 ? (1f - value) / amount : value / amount);
    }
    private void vertex(int offset, float x, float y, float alpha, float u, float v) {
        vertices[offset] = x; vertices[offset + 1] = y;
        vertices[offset + 2] = Color.toFloatBits(1f, 1f, 1f, alpha);
        vertices[offset + 3] = u; vertices[offset + 4] = v;
    }

    @Override public void dispose() { texture.dispose(); }
}
