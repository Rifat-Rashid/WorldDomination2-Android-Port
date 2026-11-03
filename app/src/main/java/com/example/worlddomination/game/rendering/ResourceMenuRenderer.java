package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.ResourceMenuText;

/** Original Resources artwork at four-times resolution with separately rendered live text. */
public final class ResourceMenuRenderer implements Disposable {
    private static final Color INK = new Color(0f, 0.14f, 0.24f, 1f);
    private static final Color ORANGE = new Color(1f, 0.47f, 0f, 1f);
    private final Texture background;
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private final ResourceMenuText text = new ResourceMenuText();

    public ResourceMenuRenderer() {
        background = new Texture(Gdx.files.internal("original/ui/resource-menu-hd.png"));
        background.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        font = new BitmapFont(Gdx.files.internal("original/ui/fonts/resource-arial-48.fnt"));
        font.getRegion().getTexture().setFilter(
                Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        font.setUseIntegerPositions(false);
    }

    /** The caller owns the active batch, projection and modal hit geometry. */
    public void draw(SpriteBatch batch, Rectangle bounds, GameState state) {
        text.update(state);
        batch.setColor(Color.WHITE);
        batch.draw(background, bounds.x, bounds.y, bounds.width, bounds.height);
        label(batch, bounds, "RESOURCE MENU", 9f, 9f, 12.5f, Color.WHITE, false);
        label(batch, bounds, text.getBudget(), 357f, 16f, 6.8f, INK, true);
        label(batch, bounds, "Resource Information", 237f, 49f, 6.9f, Color.WHITE, false);
        label(batch, bounds, "Population:", 237f, 67f, 7.4f, Color.WHITE, false);
        label(batch, bounds, text.getPopulation(), 357f, 67f, 7.4f, Color.WHITE, true);
        label(batch, bounds, "Resource:", 237f, 82f, 7.4f, Color.WHITE, false);
        label(batch, bounds, "OIL", 357f, 82f, 7.4f, Color.WHITE, true);
        label(batch, bounds, "Estimated income:", 237f, 117f, 7.4f, ORANGE, false);
        label(batch, bounds, text.getIncome(), 357f, 130f, 10.5f, ORANGE, true);
        label(batch, bounds, "CANCEL", 76f, 255f, 7.5f, Color.WHITE, true);
        label(batch, bounds, "ACCEPT", 327f, 255f, 7.5f, Color.WHITE, true);
        batch.setColor(Color.WHITE);
    }

    private void label(SpriteBatch batch, Rectangle bounds, String value,
                       float x, float top, float capHeight, Color color, boolean right) {
        float scaleX = bounds.width / FlashCenterUiRenderer.ART_WIDTH;
        float scaleY = bounds.height / FlashCenterUiRenderer.ART_HEIGHT;
        font.getData().setScale(1f);
        font.getData().setScale(capHeight * scaleY / font.getCapHeight());
        font.setColor(color);
        layout.setText(font, value);
        float targetX = bounds.x + x * scaleX;
        if (right) {
            targetX -= layout.width;
        }
        font.draw(batch, layout, targetX, bounds.y + bounds.height - top * scaleY);
    }

    @Override
    public void dispose() {
        background.dispose();
        font.dispose();
    }
}
