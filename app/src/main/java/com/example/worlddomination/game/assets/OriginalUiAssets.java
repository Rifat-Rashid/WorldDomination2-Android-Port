package com.example.worlddomination.game.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.Disposable;

/** Cached, build-time converted Flash artwork used by the landscape presentation. */
public final class OriginalUiAssets implements Disposable {
    private final TextureAtlas atlas;
    private final Texture logoTexture;
    private final Texture battleMapTexture;
    private final Texture battlefieldFrameTexture;
    public final TextureRegion battlefieldFrame;
    private final Texture navigationPanelTexture;
    public final TextureRegion navigationPanel;
    private final Texture informationPanelTexture;
    public final TextureRegion informationPanel;
    private final Texture[] phonePanelTextures = new Texture[3];
    public final TextureRegion[] phonePanel = new TextureRegion[3];
    private final Texture[] equipmentCardTextures = new Texture[8];
    public final TextureRegion[] equipmentCards = new TextureRegion[8];
    private final Texture[] scoreTextures = new Texture[8];
    public final TextureRegion scoreBacking;
    public final TextureRegion scoreWindow;
    public final TextureRegion[] scoreDots = new TextureRegion[5];
    public final TextureRegion scoreTitle;
    private final Texture productionModalTexture;
    private final Texture neutralButtonTexture;
    private final Texture phoneGearTexture;
    private final Texture propagandaFrameTexture;
    private final Texture[] stockControlTextures = new Texture[4];
    public final TextureRegion propagandaFrame;
    public final BitmapFont propagandaStrengthFont;
    public final Texture metalPanel;
    public final TextureRegion[][] buttonPatches = new TextureRegion[3][3];

    public final TextureRegion metalTile;
    public final TextureRegion button;
    public final TextureRegion logo;
    public final TextureRegion[] portraits = new TextureRegion[4];
    public final TextureRegion[] navigationIcons = new TextureRegion[5];
    public final TextureRegion battleMap;
    public final TextureRegion resourceModal;
    public final TextureRegion productionModal;
    public final TextureRegion inventoryCounter;
    public final TextureRegion inventorySwitch;
    public final TextureRegion inventoryRadio;
    public final TextureRegion stockCounter;
    public final TextureRegion[] stockSwitches = new TextureRegion[3];
    public final TextureRegion[] actionControls = new TextureRegion[3];
    public final TextureRegion phoneGear;

    public OriginalUiAssets() {
        String[] phonePanelNames = {"interior-tile", "chrome-42", "chrome-43"};
        for (int i = 0; i < phonePanelNames.length; i++) {
            phonePanelTextures[i] = new Texture(Gdx.files.internal(
                    "original/ui/phone-panel/" + phonePanelNames[i] + ".png"));
            phonePanelTextures[i].setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            phonePanel[i] = new TextureRegion(phonePanelTextures[i]);
        }
        atlas = new TextureAtlas(Gdx.files.internal("original/ui/ui.atlas"));
        metalPanel = new Texture(Gdx.files.internal("original/ui/metal-panel.png"), true);
        metalPanel.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        metalPanel.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);
        metalTile = require("metalTile");
        neutralButtonTexture = new Texture(
                Gdx.files.internal("original/ui/neutral-button.png"), true);
        neutralButtonTexture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
        button = new TextureRegion(neutralButtonTexture);
        phoneGearTexture = new Texture(Gdx.files.internal("original/ui/phone-gear-96.png"), true);
        phoneGearTexture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
        phoneGear = new TextureRegion(phoneGearTexture);
        logoTexture = new Texture(Gdx.files.internal("original/ui/logo-mark.png"), true);
        logoTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        logo = new TextureRegion(logoTexture);
        battleMapTexture = new Texture(
                Gdx.files.internal("original/ui/battle-terrain.png"), true);
        battleMapTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        battleMap = new TextureRegion(battleMapTexture);
        battlefieldFrameTexture = new Texture(
                Gdx.files.internal("original/ui/battlefield-frame.png"), true);
        battlefieldFrameTexture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
        battlefieldFrame = new TextureRegion(battlefieldFrameTexture);
        navigationPanelTexture = new Texture(
                Gdx.files.internal("original/ui/navigation-panel.png"), true);
        navigationPanelTexture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
        navigationPanel = new TextureRegion(navigationPanelTexture);
        informationPanelTexture = new Texture(
                Gdx.files.internal("original/ui/information-panel.png"), true);
        informationPanelTexture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
        informationPanel = new TextureRegion(informationPanelTexture);
        String[] equipmentNames = {"rockets", "aircraft", "special", "warheads",
                "bioloads", "infantry", "intelligence", "defense"};
        for (int group = 0; group < equipmentNames.length; group++) {
            Texture texture = new Texture(Gdx.files.internal(
                    "original/ui/equipment-cards/" + equipmentNames[group] + ".png"), true);
            texture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
            equipmentCardTextures[group] = texture;
            equipmentCards[group] = new TextureRegion(texture);
        }
        String[] scoreNames = {"backing", "window", "dot-1", "dot-2", "dot-3", "dot-4", "dot-5", "title"};
        for (int index = 0; index < scoreNames.length; index++) {
            Texture texture = new Texture(Gdx.files.internal(
                    "original/ui/score-panel/" + scoreNames[index] + ".png"), true);
            texture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
            scoreTextures[index] = texture;
        }
        scoreBacking = new TextureRegion(scoreTextures[0]);
        scoreWindow = new TextureRegion(scoreTextures[1]);
        for (int index = 0; index < scoreDots.length; index++)
            scoreDots[index] = new TextureRegion(scoreTextures[index + 2]);
        scoreTitle = new TextureRegion(scoreTextures[7]);
        productionModalTexture = new Texture(
                Gdx.files.internal("original/ui/production-modal.png"), true);
        productionModalTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        productionModal = new TextureRegion(productionModalTexture);
        propagandaFrameTexture = new Texture(
                Gdx.files.internal("original/ui/propaganda-frame.png"), true);
        propagandaFrameTexture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
        propagandaFrame = new TextureRegion(propagandaFrameTexture);
        propagandaStrengthFont = new BitmapFont(
                Gdx.files.internal("original/ui/fonts/propaganda-source-36.fnt"), false);
        propagandaStrengthFont.getRegion().getTexture().setFilter(
                TextureFilter.Linear, TextureFilter.Linear);
        propagandaStrengthFont.setUseIntegerPositions(false);
        resourceModal = require("resourceModal");
        inventoryCounter = require("inventoryCounter");
        inventorySwitch = require("inventorySwitch");
        inventoryRadio = require("inventoryRadio");
        String[] stockFiles = {"counter", "unavailable", "eligible", "selected"};
        for (int index = 0; index < stockFiles.length; index++) {
            Texture texture = new Texture(Gdx.files.internal(
                    "original/ui/stock-controls/" + stockFiles[index] + ".png"), true);
            texture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
            stockControlTextures[index] = texture;
        }
        stockCounter = new TextureRegion(stockControlTextures[0]);
        for (int index = 0; index < stockSwitches.length; index++) {
            stockSwitches[index] = new TextureRegion(stockControlTextures[index + 1]);
        }
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

        int edge = 16;
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
        battlefieldFrameTexture.dispose();
        navigationPanelTexture.dispose();
        informationPanelTexture.dispose();
        for (Texture texture : phonePanelTextures) texture.dispose();
        for (Texture texture : equipmentCardTextures) texture.dispose();
        for (Texture texture : scoreTextures) texture.dispose();
        productionModalTexture.dispose();
        propagandaFrameTexture.dispose();
        propagandaStrengthFont.dispose();
        metalPanel.dispose();
        neutralButtonTexture.dispose();
        phoneGearTexture.dispose();
        for (Texture texture : stockControlTextures) texture.dispose();
    }
}
