package com.example.worlddomination.game.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

/** Cached, build-time converted Flash artwork used by the landscape presentation. */
public final class OriginalUiAssets implements Disposable {
    private final TextureAtlas atlas;
    private final Texture logoTexture;
    private final Texture battleMapTexture;
    public final Texture metalPanel;
    public final TextureRegion[][] buttonPatches = new TextureRegion[3][3];

    public final TextureRegion metalTile;
    public final TextureRegion button;
    public final TextureRegion logo;
    public final TextureRegion[] portraits = new TextureRegion[4];
    public final TextureRegion[] navigationIcons = new TextureRegion[5];
    public final TextureRegion battleMap;
    public final TextureRegion resourceModal;
    public final TextureRegion inventoryCounter;
    public final TextureRegion inventorySwitch;
    public final TextureRegion inventoryRadio;
    public final TextureRegion[] actionControls = new TextureRegion[3];

    public OriginalUiAssets() {
        atlas = new TextureAtlas(Gdx.files.internal("original/ui/ui.atlas"));
        metalPanel = new Texture(Gdx.files.internal("original/ui/metal-panel.png"), true);
        metalPanel.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        metalPanel.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);
        metalTile = require("metalTile");
        button = require("button");
        logoTexture = new Texture(Gdx.files.internal("original/ui/logo-mark.png"), true);
        logoTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        logo = new TextureRegion(logoTexture);
        battleMapTexture = new Texture(
                Gdx.files.internal("original/ui/battle-map-hd.png"), true);
        battleMapTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        battleMap = new TextureRegion(battleMapTexture);
        resourceModal = require("resourceModal");
        inventoryCounter = require("inventoryCounter");
        inventorySwitch = require("inventorySwitch");
        inventoryRadio = require("inventoryRadio");
        actionControls[0] = require("actionFirst");
        actionControls[1] = require("actionSecond");
        actionControls[2] = require("actionConfirm");

        portraits[0] = require("portrait1");
        portraits[1] = require("portrait2");
        portraits[2] = require("portrait3");
        portraits[3] = require("portrait4");
        navigationIcons[0] = require("navResources");
        navigationIcons[1] = require("navProduction");
        navigationIcons[2] = require("navTechnology");
        navigationIcons[3] = require("navMedia");
        navigationIcons[4] = require("navAttack");

        int edge = 4;
        int[] sourceX = {0, edge, button.getRegionWidth() - edge};
        int[] sourceY = {0, edge, button.getRegionHeight() - edge};
        int[] sourceWidth = {edge, button.getRegionWidth() - 2 * edge, edge};
        int[] sourceHeight = {edge, button.getRegionHeight() - 2 * edge, edge};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                buttonPatches[row][column] = new TextureRegion(button,
                        sourceX[column], sourceY[row], sourceWidth[column], sourceHeight[row]);
            }
        }
    }

    private TextureRegion require(String name) {
        TextureRegion region = atlas.findRegion(name);
        if (region == null) {
            throw new IllegalStateException("Missing original UI atlas region: " + name);
        }
        return region;
    }

    @Override
    public void dispose() {
        atlas.dispose();
        logoTexture.dispose();
        battleMapTexture.dispose();
        metalPanel.dispose();
    }
}
