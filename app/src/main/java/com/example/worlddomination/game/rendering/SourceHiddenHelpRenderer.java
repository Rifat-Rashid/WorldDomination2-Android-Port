package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;

/** Authored hidden Help frame135 and frame7 warning, using the phone Help crop.
 * Dynamic input/message glyphs and keyboard focus belong to the caller.
 */
public final class SourceHiddenHelpRenderer implements Disposable {
    public static final String CHEAT_ASSET = "original/ui/help/hidden-art/hidden-cheat-frame135.png";
    public static final String MANIFEST = "original/ui/help/hidden-art/hidden-manifest.json";
    public static final String WARNING_ASSET = "original/ui/help/hidden-art/hidden-warning-frame7.png";
    public static final float CROP_X = 205f, CROP_Y = 135f;
    public static final float SOURCE_WIDTH = 365f, SOURCE_HEIGHT = 275f;
    public static final float COMMAND_FONT_SIZE = 30f, PASSWORD_FONT_SIZE = 16f;
    public static final float WARNING_FONT_SIZE = 10f, WARNING_HEADER_FONT_SIZE = 9f;
    public static final int COMMAND_RGBA = 0xff7800ff, PASSWORD_RGBA = 0x000000ff;
    public static final float TEXT_TOP_OFFSET = 4f, TEXT_LEFT_PADDING = 2f;

    private final Rectangle fitted = new Rectangle();
    private Texture cheat, warning;

    /** Call outside a begun batch. Both textures are managed and retained until dispose. */
    public void prepare() {
        if (isPrepared()) return;
        Texture nextCheat = null, nextWarning = null;
        try {
            nextCheat = load(CHEAT_ASSET);
            nextWarning = load(WARNING_ASSET);
            cheat = nextCheat;
            warning = nextWarning;
        } catch (RuntimeException failure) {
            if (nextCheat != null) nextCheat.dispose();
            if (nextWarning != null) nextWarning.dispose();
            throw failure;
        }
    }

    public boolean isPrepared() { return cheat != null && warning != null; }

    /** Blank authored command panel. Does not paint a generic input field or typed text. */
    public void draw(Batch batch, Rectangle container) { drawTexture(batch, container, cheat); }

    /** Actual source frame7 backing, static Warning header and OK; caller paints message only. */
    public void drawWarning(Batch batch, Rectangle container) { drawTexture(batch, container, warning); }

    private void drawTexture(Batch batch, Rectangle container, Texture texture) {
        if (texture == null) throw new IllegalStateException("prepare hidden Help outside the batch before drawing");
        if (container.width <= 0 || container.height <= 0) return;
        fit(container, fitted);
        float color = batch.getPackedColor();
        batch.setColor(Color.WHITE);
        try { batch.draw(texture, fitted.x, fitted.y, fitted.width, fitted.height); }
        finally { batch.setPackedColor(color); }
    }

    private static Texture load(String asset) {
        Texture texture = new Texture(Gdx.files.internal(asset));
        try {
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return texture;
        } catch (RuntimeException failure) {
            texture.dispose();
            throw failure;
        }
    }

    public static Rectangle fit(Rectangle container, Rectangle output) {
        if (container.width <= 0 || container.height <= 0)
            return output.set(container.x, container.y, 0, 0);
        return SourceHelpRenderer.fit(container, output);
    }

    /** Nominal editable field AABB: stage (227.05,291.05)..(545.05,328.55). */
    public static Rectangle commandBounds(Rectangle container, Rectangle output) {
        return cropBounds(container, output, 22.05f, 156.05f, 318f, 37.5f);
    }

    /** The source's rasterized dynamic field uses ceil(318)xceil(37.5), not its hit AABB. */
    public static Rectangle commandTextClipBounds(Rectangle container, Rectangle output) {
        return cropBounds(container, output, 22.05f, 156.05f, 318f, 38f);
    }

    /** Stage (223.15,139.05)..(402.60,160.95), input_ frame11.
     * This is a focus target; its source glyphs are occluded by the orange Help header.
     */
    public static Rectangle passwordBounds(Rectangle container, Rectangle output) {
        return cropBounds(container, output, 18.15f, 4.05f, 179.45f, 21.9f);
    }

    public static Rectangle passwordTextClipBounds(Rectangle container, Rectangle output) {
        return cropBounds(container, output, 18.15f, 4.05f, 180f, 22f);
    }

    /** Same authored Index/Cheat control: stage (361,383.45)..(416,407.45). */
    public static Rectangle buttonBounds(Rectangle container, Rectangle output) {
        return cropBounds(container, output, 156f, 248.45f, 55f, 24f);
    }

    public static Rectangle warningOkBounds(Rectangle container, Rectangle output) {
        return cropBounds(container, output, 160.2174f, 152.2174f, 36.5897f, 15.9664f);
    }

    /** Source warning text is centered white Arial10, wrapped, with source line step12. */
    public static Rectangle warningTextBounds(Rectangle container, Rectangle output) {
        return cropBounds(container, output, 107.05f, 91.05f, 140.4f, 56.8f);
    }

    public static Rectangle warningTextClipBounds(Rectangle container, Rectangle output) {
        return cropBounds(container, output, 107.05f, 91.05f, 141f, 57f);
    }

    /** Header is left-aligned white Arial9; both warning strings need source +2/+4 padding. */
    public static Rectangle warningHeaderBounds(Rectangle container, Rectangle output) {
        return cropBounds(container, output, 99.95f, 70.05f, 49.2f, 14.3f);
    }

    public static Rectangle warningHeaderTextClipBounds(Rectangle container, Rectangle output) {
        return cropBounds(container, output, 99.95f, 70.05f, 50f, 15f);
    }

    private static Rectangle cropBounds(Rectangle container, Rectangle output,
                                        float x, float y, float width, float height) {
        fit(container, output);
        float scale = output.width / SOURCE_WIDTH;
        float left = output.x, top = output.y + output.height;
        return output.set(left + x * scale, top - (y + height) * scale,
                width * scale, height * scale);
    }

    @Override public void dispose() {
        if (cheat != null) cheat.dispose();
        if (warning != null) warning.dispose();
        cheat = null;
        warning = null;
    }
}
