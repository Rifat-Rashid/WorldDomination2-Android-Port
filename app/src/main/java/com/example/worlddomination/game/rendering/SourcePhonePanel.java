package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;

/** Phone adapter panel, using extracted source tile and source-drawn chrome. */
public final class SourcePhonePanel {
    private static final int CAP = 10;
    private static final int CACHE_SIZE = 32;
    private final int[] widths = new int[CACHE_SIZE];
    private final int[] heights = new int[CACHE_SIZE];
    private final FrameBuffer[] buffers = new FrameBuffer[CACHE_SIZE];
    private final TextureRegion[] composites = new TextureRegion[CACHE_SIZE];
    private final IntBuffer intState = BufferUtils.newIntBuffer(4);
    private final FloatBuffer clearState = BufferUtils.newFloatBuffer(4);
    private final Matrix4 projection = new Matrix4();
    private SpriteBatch compositor;
    private int nextSlot;
    private boolean disposed;
    private final TextureRegion tile;
    private final TextureRegion partialTile;
    private final TextureRegion[][] parts = new TextureRegion[2][8];

    /** The caller owns the managed textures and must retain them through each draw. */
    public SourcePhonePanel(TextureRegion tile, TextureRegion shortChrome, TextureRegion tallChrome) {
        if (tile.getRegionWidth() != 160 || tile.getRegionHeight() != 14
                || shortChrome.getRegionWidth() != 160 || shortChrome.getRegionHeight() != 42
                || tallChrome.getRegionWidth() != 160 || tallChrome.getRegionHeight() != 43) {
            throw new IllegalArgumentException("Unexpected phone panel source dimensions");
        }
        this.tile = tile;
        partialTile = new TextureRegion(tile);
        makeParts(0, shortChrome);
        makeParts(1, tallChrome);
    }

    private void makeParts(int index, TextureRegion source) {
        int bottom = source.getRegionHeight() - CAP;
        parts[index][0] = new TextureRegion(source, 0, 0, CAP, CAP);
        parts[index][1] = new TextureRegion(source, 150, 0, CAP, CAP);
        parts[index][2] = new TextureRegion(source, 0, bottom, CAP, CAP);
        parts[index][3] = new TextureRegion(source, 150, bottom, CAP, CAP);
        parts[index][4] = new TextureRegion(source, 10, 0, 1, CAP);
        parts[index][5] = new TextureRegion(source, 10, bottom, 1, CAP);
        parts[index][6] = new TextureRegion(source, 0, 10, CAP, 1);
        parts[index][7] = new TextureRegion(source, 150, 10, CAP, 1);
    }

    /** Prepare outside any begun SpriteBatch; preserves the caller's framebuffer and viewport. */
    public void prepare(int width, int height) {
        if (disposed) throw new IllegalStateException("Panel renderer is disposed");
        if (width < 20 || height < 20 || find(width, height) >= 0) return;
        int framebuffer = readInt(GL20.GL_FRAMEBUFFER_BINDING);
        intState.clear(); Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, intState);
        int viewportX = intState.get(0), viewportY = intState.get(1);
        int viewportWidth = intState.get(2), viewportHeight = intState.get(3);
        clearState.clear(); Gdx.gl.glGetFloatv(GL20.GL_COLOR_CLEAR_VALUE, clearState);
        float red = clearState.get(0), green = clearState.get(1);
        float blue = clearState.get(2), alpha = clearState.get(3);
        boolean scissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        boolean blend = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
        int srcRgb = readInt(GL20.GL_BLEND_SRC_RGB), dstRgb = readInt(GL20.GL_BLEND_DST_RGB);
        int srcAlpha = readInt(GL20.GL_BLEND_SRC_ALPHA), dstAlpha = readInt(GL20.GL_BLEND_DST_ALPHA);
        FrameBuffer created = null;
        try {
            if (compositor == null) compositor = new SpriteBatch();
            created = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
            created.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            created.begin();
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glClearColor(0, 0, 0, 0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            compositor.setProjectionMatrix(projection.setToOrtho2D(0, 0, width, height));
            compositor.setColor(1f, 1f, 1f, 1f);
            compositor.begin();
            try { drawLayers(compositor, 0, 0, width, height); }
            finally { compositor.end(); }
            int slot = nextSlot;
            if (buffers[slot] != null) buffers[slot].dispose();
            buffers[slot] = created;
            widths[slot] = width; heights[slot] = height;
            TextureRegion region = new TextureRegion(created.getColorBufferTexture());
            region.flip(false, true);
            composites[slot] = region;
            nextSlot = (slot + 1) % CACHE_SIZE;
            created = null;
        } finally {
            if (created != null) created.dispose();
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer);
            Gdx.gl.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            Gdx.gl.glClearColor(red, green, blue, alpha);
            if (scissor) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST); else Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) Gdx.gl.glEnable(GL20.GL_BLEND); else Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    private int readInt(int name) {
        intState.clear(); Gdx.gl.glGetIntegerv(name, intState); return intState.get(0);
    }

    private int find(int width, int height) {
        for (int i = 0; i < CACHE_SIZE; i++)
            if (buffers[i] != null && widths[i] == width && heights[i] == height) return i;
        return -1;
    }

    /** Uses prepared pixels only. Call prepare for the rounded bounds before beginning the batch. */
    public void draw(SpriteBatch batch, Rectangle bounds) {
        int width = Math.round(bounds.width), height = Math.round(bounds.height);
        if (width < 20 || height < 20) return;
        int index = find(width, height);
        if (index < 0) throw new IllegalStateException("Phone panel size was not prepared");
        float savedColor = batch.getPackedColor();
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(composites[index], bounds.x, bounds.y, bounds.width, bounds.height);
        batch.setPackedColor(savedColor);
    }

    /** Discard render-target contents on context restoration; prepare them again before drawing. */
    public void invalidate() {
        for (int i = 0; i < CACHE_SIZE; i++) {
            if (buffers[i] != null) buffers[i].dispose();
            buffers[i] = null; composites[i] = null;
        }
        nextSlot = 0;
    }

    public void dispose() {
        if (disposed) return;
        invalidate();
        if (compositor != null) compositor.dispose();
        compositor = null;
        disposed = true;
    }

    private void drawLayers(SpriteBatch batch, float x, float y, int width, int height) {
        int innerWidth = width - 7, innerHeight = height - 7;
        for (int top = 0; top < innerHeight; top += 14) {
            int h = Math.min(14, innerHeight - top);
            for (int left = 0; left < innerWidth; left += 160) {
                int w = Math.min(160, innerWidth - left);
                partialTile.setRegion(tile, 0, 0, w, h);
                batch.draw(partialTile, x + 3 + left, y + height - 3 - top - h, w, h);
            }
        }
        TextureRegion[] p = parts[height > 42 ? 1 : 0];
        batch.draw(p[0], x, y + height - CAP, CAP, CAP);
        batch.draw(p[1], x + width - CAP, y + height - CAP, CAP, CAP);
        batch.draw(p[2], x, y, CAP, CAP);
        batch.draw(p[3], x + width - CAP, y, CAP, CAP);
        batch.draw(p[4], x + CAP, y + height - CAP, width - 2 * CAP, CAP);
        batch.draw(p[5], x + CAP, y, width - 2 * CAP, CAP);
        batch.draw(p[6], x, y + CAP, CAP, height - 2 * CAP);
        batch.draw(p[7], x + width - CAP, y + CAP, CAP, height - 2 * CAP);
    }
}
