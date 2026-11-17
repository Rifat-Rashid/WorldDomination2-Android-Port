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
    private final SourceCarouselMesh carouselMesh = new SourceCarouselMesh();
    private final TextureRegion carouselSource;
    private final float[] carouselVertices = new float[(carouselMesh.rows() - 1)
            * (carouselMesh.columns() - 1) * 20];
    private final TextureRegion[] opponentWells = new TextureRegion[4];
    private final TextureRegion opponentArrow;
    private final TextureRegion[] arrowMargin = new TextureRegion[8];
    private final float[] vertices = new float[20];

    public SourceSetupPanelRenderer() {
        texture = new Texture(Gdx.files.internal("original/ui/setup-panel-source-5813.png"));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        carouselSource = new TextureRegion(texture, 147, 319, 488, 180);
        body = new TextureRegion(texture, BODY_X, BODY_Y, BODY_WIDTH, BODY_HEIGHT);
        for (int i = 0; i < 4; i++)
            opponentWells[i] = new TextureRegion(texture, 320 + 74 * i, 226, 64, 70);
        opponentArrow = new TextureRegion(texture, 205, 234, 100, 53);
        // Surrounding source background, ending before the adjacent well/text.
        int[][] margin = {{190,220,15,14}, {205,220,100,14}, {305,220,15,14},
                {190,234,15,53}, {305,234,15,53},
                {190,287,15,14}, {205,287,100,14}, {305,287,15,14}};
        for (int i = 0; i < margin.length; i++) {
            int[] m = margin[i];
            arrowMargin[i] = new TextureRegion(texture, m[0], m[1], m[2], m[3]);
        }
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

    /** Draw the complete authored panel with continuous UVs and fitted wells. */
    public void drawCarousel(SpriteBatch batch, Rectangle bounds, Rectangle[] wells, Rectangle inset) {
        float color = batch.getPackedColor();
        batch.setColor(Color.WHITE);
        if (carouselMesh.update(bounds, wells, inset)) rebuildCarouselVertices();
        // A uniform transform needs no tessellation of the original bitmap.
        if (carouselMesh.isUniform())
            batch.draw(carouselSource, bounds.x, bounds.y, bounds.width, bounds.height);
        else batch.draw(texture, carouselVertices, 0, carouselVertices.length);
        batch.setPackedColor(color);
    }

    private void rebuildCarouselVertices() {
        float tw = texture.getWidth(), th = texture.getHeight();
        int offset = 0;
        for (int row = 0; row < carouselMesh.rows() - 1; row++) {
            float top = carouselMesh.destinationY(row), bottom = carouselMesh.destinationY(row + 1);
            float vTop = carouselMesh.sourceY(row) / th, vBottom = carouselMesh.sourceY(row + 1) / th;
            for (int col = 0; col < carouselMesh.columns() - 1; col++) {
                float u0 = carouselMesh.sourceX(col) / tw, u1 = carouselMesh.sourceX(col + 1) / tw;
                carouselVertex(offset, carouselMesh.destinationX(row + 1, col), bottom, u0, vBottom);
                carouselVertex(offset + 5, carouselMesh.destinationX(row, col), top, u0, vTop);
                carouselVertex(offset + 10, carouselMesh.destinationX(row, col + 1), top, u1, vTop);
                carouselVertex(offset + 15, carouselMesh.destinationX(row + 1, col + 1), bottom, u1, vBottom);
                offset += 20;
            }
        }
    }

    private void carouselVertex(int offset, float x, float y, float u, float v) {
        carouselVertices[offset] = x; carouselVertices[offset + 1] = y;
        carouselVertices[offset + 2] = Color.WHITE.toFloatBits();
        carouselVertices[offset + 3] = u; carouselVertices[offset + 4] = v;
    }

    /** The four wells and arrow are distinct regions of the authored background. */
    public void drawOpponents(SpriteBatch batch, Rectangle[] wells, Rectangle arrow) {
        float color = batch.getPackedColor();
        batch.setColor(Color.WHITE);
        float scale = arrow.width / 100f;
        float side = 15f * scale, cap = 14f * scale;
        float left = arrow.x - side, right = arrow.x + arrow.width;
        float bottom = arrow.y - cap, top = arrow.y + arrow.height;
        float fade = 15f * scale;
        faded(batch, arrowMargin[0], left, top, side, cap, -1, 1, fade);
        faded(batch, arrowMargin[1], arrow.x, top, arrow.width, cap, 0, 1, fade);
        faded(batch, arrowMargin[2], right, top, side, cap, 1, 1, fade);
        faded(batch, arrowMargin[3], left, arrow.y, side, arrow.height, -1, 0, fade);
        faded(batch, arrowMargin[4], right, arrow.y, side, arrow.height, 1, 0, fade);
        faded(batch, arrowMargin[5], left, bottom, side, cap, -1, -1, fade);
        faded(batch, arrowMargin[6], arrow.x, bottom, arrow.width, cap, 0, -1, fade);
        faded(batch, arrowMargin[7], right, bottom, side, cap, 1, -1, fade);
        batch.draw(opponentArrow, arrow.x, arrow.y, arrow.width, arrow.height);
        for (int i = 0; i < opponentWells.length; i++) {
            Rectangle well = wells[i];
            batch.draw(opponentWells[i], well.x, well.y, well.width, well.height);
        }
        batch.setPackedColor(color);
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
