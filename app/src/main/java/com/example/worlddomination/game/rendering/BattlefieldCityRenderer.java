package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.assets.OriginalCityAssets;
import com.example.worlddomination.game.assets.OriginalCityAssets.Frame;
import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

/** Draws source city artwork in the recovered back-to-front depth order. */
public final class BattlefieldCityRenderer {
    private static final int[] CITY_RENDER_ORDER = BattlefieldCityLayout.cityRenderOrder();
    private static final int CITY_COUNT = BattlefieldCityLayout.PLAYER_COUNT
            * BattlefieldCityLayout.CITIES_PER_PLAYER;

    private final OriginalCityAssets cityAssets;
    private final CityShrinkSequence[] citySequences = new CityShrinkSequence[CITY_COUNT];
    private final int[] trackedTargets = new int[CITY_COUNT];

    public BattlefieldCityRenderer(OriginalCityAssets cityAssets) {
        this.cityAssets = cityAssets;
        for (int address = 0; address < CITY_COUNT; address++) {
            citySequences[address] = new CityShrinkSequence();
            trackedTargets[address] = Integer.MIN_VALUE;
        }
    }

    /** Seeds presentation frames when a match starts, without animating initial cities. */
    public void reset(GameState game) {
        for (int address = 0; address < CITY_COUNT; address++) {
            CityState city = cityAt(game, address);
            int frameNumber = city == null ? -1 : city.getArtworkFrame();
            citySequences[address].reset(frameNumber);
            trackedTargets[address] = frameNumber;
        }
    }

    /** Tracks model frame changes and advances each transition by one 24 Hz tick. */
    public void tick(GameState game) {
        if (game == null) {
            return;
        }
        for (int address = 0; address < CITY_COUNT; address++) {
            CityState city = cityAt(game, address);
            int targetFrame = city == null ? -1 : city.getArtworkFrame();
            if (targetFrame != trackedTargets[address]) {
                citySequences[address].target(targetFrame);
                trackedTargets[address] = targetFrame;
            }
            citySequences[address].tick();
        }
    }

    public void draw(SpriteBatch batch, Rectangle mapBounds, GameState game) {
        if (game == null) {
            return;
        }
        float scale = CitySpriteLayout.scale(mapBounds, cityAssets.getArtworkScale());
        for (int address : CITY_RENDER_ORDER) {
            int playerId = address / BattlefieldCityLayout.CITIES_PER_PLAYER;
            int cityIndex = address % BattlefieldCityLayout.CITIES_PER_PLAYER;
            GameState.PlayerState owner = game.getPlayerState(playerId);
            CityState city = owner.getCity(cityIndex);
            if (city == null) {
                continue;
            }
            int cityAddress = playerId * BattlefieldCityLayout.CITIES_PER_PLAYER + cityIndex;
            CityShrinkSequence sequence = citySequences[cityAddress];
            int frameNumber = sequence.getFrameNumber();
            if (frameNumber < 1) {
                continue;
            }
            Frame frame = cityAssets.getFrame(owner.getReligionCode(), frameNumber);
            if (frame == null) {
                continue;
            }
            float width = frame.region.getRegionWidth() * scale;
            float height = frame.region.getRegionHeight() * scale;
            float x = BattlefieldCityLayout.x(mapBounds, playerId, cityIndex)
                    + frame.offsetX * scale;
            float y = BattlefieldCityLayout.y(mapBounds, playerId, cityIndex)
                    - frame.offsetY * scale - height;
            float red = batch.getColor().r;
            float green = batch.getColor().g;
            float blue = batch.getColor().b;
            float previousAlpha = batch.getColor().a;
            float alpha = sequence.getAlpha() / 100f;
            if (alpha < 1f) {
                batch.setColor(red, green, blue, previousAlpha * alpha);
            }
            batch.draw(frame.region, x, y, width, height);
            if (alpha < 1f) {
                batch.setColor(red, green, blue, previousAlpha);
            }
        }
    }

    private static CityState cityAt(GameState game, int address) {
        if (game == null) {
            return null;
        }
        int playerId = address / BattlefieldCityLayout.CITIES_PER_PLAYER;
        int cityIndex = address % BattlefieldCityLayout.CITIES_PER_PLAYER;
        GameState.PlayerState owner = game.getPlayerState(playerId);
        return owner == null ? null : owner.getCity(cityIndex);
    }
}
