package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;

/** Original phone HUD's recessed message well and readable Arial message typography. */
public final class MessagePanelRenderer implements Disposable {
    public static final Color TEXT_COLOR = new Color(61f / 255f, 242f / 255f, 32f / 255f, 1f);
    private final Texture background;
    private final NinePatch well;
    private final BitmapFont font;

    public MessagePanelRenderer() {
        background = new Texture(Gdx.files.internal("original/ui/message-well.png"));
        background.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        well = new NinePatch(background, 16, 16, 16, 16);
        well.scale(0.25f, 0.25f);
        font = new BitmapFont(Gdx.files.internal("original/ui/fonts/resource-arial-48.fnt"));
        font.getRegion().getTexture().setFilter(
                Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        font.getData().setScale(8.5f / font.getCapHeight());
        font.setUseIntegerPositions(false);
        font.setColor(TEXT_COLOR);
    }

    public BitmapFont getFont() { return font; }

    public void drawBackground(SpriteBatch batch, Rectangle panel) {
        batch.setColor(Color.WHITE);
        well.draw(batch, panel.x + 6f, panel.y + 6f,
                Math.max(1f, panel.width - 12f), Math.max(1f, panel.height - 12f));
    }

    @Override
    public void dispose() {
        font.dispose();
        background.dispose();
    }
}
