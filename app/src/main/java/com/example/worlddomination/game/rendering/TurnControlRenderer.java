package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.ui.TurnActionDisplay;

/** Cached original confirm hatch and action announcement/scheme artwork. */
public final class TurnControlRenderer implements Disposable {
    private final TextureAtlas confirmAtlas;
    private final TextureAtlas announcementAtlas;
    private final TextureRegion[] confirmFrames = new TextureRegion[12];
    private final TextureRegion[] actionFrames = new TextureRegion[GameState.Action.values().length];
    private final TextureRegion[][] schemeFrames = new TextureRegion[5][6];
    private final Rectangle displaySource;
    private final Rectangle schemeSource;

    public TurnControlRenderer() {
        confirmAtlas = new TextureAtlas(Gdx.files.internal("original/ui/confirm-turn.atlas"));
        announcementAtlas = new TextureAtlas(Gdx.files.internal("original/ui/turn-announcements.atlas"));
        for (int index = 0; index < confirmFrames.length; index++) {
            confirmFrames[index] = require(confirmAtlas, "confirm" + (index + 1));
        }
        for (GameState.Action action : GameState.Action.values()) {
            actionFrames[action.ordinal()] = require(announcementAtlas, sourceLabel(action));
        }
        for (int player = 0; player < 5; player++) {
            for (int color = 0; color < 6; color++) {
                schemeFrames[player][color] = require(announcementAtlas, "player" + player + "color" + color);
            }
        }
        JsonValue geometry = new JsonReader().parse(Gdx.files.internal("original/ui/turn-announcements.json"));
        displaySource = rectangle(geometry.get("display"));
        schemeSource = rectangle(geometry.get("scheme"));
    }

    public void drawConfirm(SpriteBatch batch, Rectangle bounds, int frame) {
        TextureRegion region = confirmFrames[frame - 1];
        float scale = Math.min(bounds.width / region.getRegionWidth(), bounds.height / region.getRegionHeight());
        float width = region.getRegionWidth() * scale;
        float height = region.getRegionHeight() * scale;
        batch.draw(region, bounds.x + (bounds.width - width) / 2f,
                bounds.y + (bounds.height - height) / 2f, width, height);
    }

    public void drawAnnouncement(SpriteBatch batch, Rectangle mapBounds, TurnActionDisplay display,
                                 GameState state) {
        if (display.getAction() == null || display.getAlpha() <= 0) return;
        batch.setColor(1, 1, 1, display.getAlpha());
        drawSource(batch, actionFrames[display.getAction().ordinal()], mapBounds, displaySource);
        for (int player = 0; player < 5; player++) {
            boolean highlighted = display.includesPlayer(player);
            GameState.PlayerState nation = state.getPlayerState(player);
            int color = highlighted ? nation.getPlayerColor() : 0;
            batch.setColor(1, 1, 1, display.getAlpha() * (!highlighted && !nation.alive ? 0.3f : 1f));
            drawSource(batch, schemeFrames[player][color], mapBounds, schemeSource);
        }
        batch.setColor(1, 1, 1, 1);
    }

    private static void drawSource(SpriteBatch batch, TextureRegion region, Rectangle map, Rectangle source) {
        float scale = BattlefieldCityLayout.sourceScale(map);
        batch.draw(region, BattlefieldCityLayout.sourceX(map, source.x, 0),
                BattlefieldCityLayout.sourceY(map, source.y) - source.height * scale,
                source.width * scale, source.height * scale);
    }

    public static String sourceLabel(GameState.Action action) {
        switch (action) {
            case RESOURCES: return "resources";
            case PRODUCTION: return "production";
            case RESEARCH: return "research";
            case MASS_MEDIA: return "religion";
            case PREPARED_ATTACK: return "preAttack";
            case ATTACK: return "attack";
            case DEFENSE: return "defense";
            case PROPAGANDA: return "propaganda";
            case DIPLOMACY: return "diplomacy";
            default: throw new IllegalArgumentException("Unknown source action " + action);
        }
    }

    private static Rectangle rectangle(JsonValue value) {
        return new Rectangle(value.getFloat("x"), value.getFloat("y"),
                value.getFloat("width"), value.getFloat("height"));
    }

    private static TextureRegion require(TextureAtlas atlas, String name) {
        TextureRegion region = atlas.findRegion(name);
        if (region == null) throw new IllegalStateException("Missing source turn artwork " + name);
        return region;
    }

    @Override public void dispose() { confirmAtlas.dispose(); announcementAtlas.dispose(); }
}
