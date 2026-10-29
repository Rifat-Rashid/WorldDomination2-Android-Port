package com.example.worlddomination.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.example.worlddomination.game.assets.OriginalUiAssets;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.ScrollPaneState;

/** Native OpenGL ES game screen; it loads no Python, browser, or network runtime. */
public final class WorldDominationGame extends ApplicationAdapter implements InputProcessor {
    private static final float BASE_WIDTH = 896f;
    private static final float BASE_HEIGHT = 414f;
    private static final float FIXED_STEP = 1f / 24f;
    private static final float TAP_SLOP = 9f;
    private static final float NAV_ROW_HEIGHT = 40f;
    private static final float NAV_ROW_GAP = 4f;
    private static final float EQUIPMENT_ROW_HEIGHT = 36f;
    private static final float MAP_WIDTH = 523f;
    private static final float MAP_HEIGHT = 318f;
    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_NAV = 1;
    private static final int GESTURE_EQUIPMENT = 2;

    private static final GameState.Action[] ACTIONS = GameState.Action.values();
    private static final String[] EQUIPMENT_ROWS = {
            "ROCKETS", "10 meg", "25 meg", "50 meg", "95 meg",
            "AIRCRAFT", "Fighter", "Bomber",
            "SPECIAL CARRIERS", "Submarine", "Aircraft Carrier", "Space Rocket", "Satellite",
            "WARHEADS", "10 meg", "25 meg", "50 meg", "95 meg",
            "BIOLOADS", "10 meg", "25 meg", "50 meg", "95 meg",
            "INFANTRY", "Soldiers", "Seals", "Tanks", "Specials",
            "INTELLIGENCE", "Spy 1", "Spy 2", "Spy 3", "Spy 4",
            "DEFENSE", "Anti-aircraft", "Anti-rocket"
    };
    private static final String[] EQUIPMENT_GROUP_TITLES = {
            "Rockets", "Aircraft", "Special Carriers", "Warheads",
            "Bioloads", "Infantry", "Intelligence", "Defense"
    };
    private static final int[] EQUIPMENT_GROUP_STARTS = {0, 5, 8, 13, 18, 23, 28, 33};
    private static final int[] EQUIPMENT_GROUP_COUNTS = {4, 2, 4, 4, 4, 4, 4, 2};
    private static final float[][] ISLAND_CENTERS = {
            {286f, 160f}, {78f, 72f}, {472f, 65f}, {78f, 260f}, {478f, 270f}
    };
    private static final Color PANEL = new Color(0.025f, 0.075f, 0.105f, 0.97f);
    private static final Color PANEL_EDGE = new Color(0.40f, 0.56f, 0.63f, 1f);
    private static final Color BUTTON = new Color(0.10f, 0.20f, 0.26f, 1f);
    private static final Color BUTTON_SELECTED = new Color(0.12f, 0.34f, 0.45f, 1f);
    private static final Color DISABLED_TEXT = new Color(0.55f, 0.65f, 0.69f, 1f);
    private static final Color GREEN = new Color(0.17f, 0.95f, 0.30f, 1f);
    private static final Color TITLE_TEXT = new Color(0.86f, 0.91f, 0.93f, 1f);
    private static final Color SUBTITLE_TEXT = new Color(0.61f, 0.74f, 0.79f, 1f);
    private static final Color GOLD = new Color(1f, 0.82f, 0.28f, 1f);
    private static final Color MODAL_ORANGE = new Color(1f, 0.43f, 0.02f, 1f);
    private static final Color QUEUED_ACTION_TEXT = new Color(0.34f, 0.18f, 0.05f, 1f);
    private static final float[] BUTTON_SELECTED_TOP = {0.16f, 0.34f, 0.43f};
    private static final float[] BUTTON_SELECTED_BOTTOM = {0.05f, 0.17f, 0.24f};
    private static final float[] BUTTON_NORMAL_TOP = {0.19f, 0.27f, 0.31f};
    private static final float[] BUTTON_NORMAL_BOTTOM = {0.04f, 0.09f, 0.12f};
    private static final Color[] PORTRAIT_COLORS = {
            new Color(0.84f, 0.12f, 0.08f, 1f),
            new Color(0.94f, 0.58f, 0.09f, 1f),
            new Color(0.15f, 0.63f, 0.86f, 1f),
            new Color(0.89f, 0.79f, 0.12f, 1f)
    };

    private final GameState game = new GameState(1);
    private final ExtendViewport viewport = new ExtendViewport(BASE_WIDTH, BASE_HEIGHT, 1400f, 800f);
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Rectangle leftPanel = new Rectangle();
    private final Rectangle rightPanel = new Rectangle();
    private final Rectangle logoPanel = new Rectangle();
    private final Rectangle centerHeader = new Rectangle();
    private final Rectangle navPanel = new Rectangle();
    private final Rectangle infoPanel = new Rectangle();
    private final Rectangle navTitlePlate = new Rectangle();
    private final Rectangle infoTitlePlate = new Rectangle();
    private final Rectangle scoreTitlePlate = new Rectangle();
    private final Rectangle actionTitlePlate = new Rectangle();
    private final Rectangle portraitStrip = new Rectangle();
    private final Rectangle[] portraitCards = new Rectangle[4];
    private final Rectangle toolbarPanel = new Rectangle();
    private final Rectangle helpButton = new Rectangle();
    private final Rectangle muteButton = new Rectangle();
    private final Rectangle settingsButton = new Rectangle();
    private final Rectangle stockPanel = new Rectangle();
    private final Rectangle navViewport = new Rectangle();
    private final Rectangle equipmentViewport = new Rectangle();
    private final Rectangle mapFrame = new Rectangle();
    private final Rectangle mapBounds = new Rectangle();
    private final Rectangle messagePanel = new Rectangle();
    private final Rectangle scorePanel = new Rectangle();
    private final Rectangle actionPanel = new Rectangle();
    private final Rectangle actionSlots[] = {new Rectangle(), new Rectangle()};
    private final Rectangle confirmButton = new Rectangle();
    private final Rectangle modal = new Rectangle();
    private final Rectangle modalAccept = new Rectangle();
    private final Rectangle modalCancel = new Rectangle();
    private final Rectangle[] navRows = new Rectangle[ACTIONS.length];
    private final Rectangle[] equipmentRows = new Rectangle[EQUIPMENT_ROWS.length];
    private final Rectangle[] equipmentCards = new Rectangle[EQUIPMENT_GROUP_STARTS.length];
    private final ScrollPaneState navigationScroll = new ScrollPaneState();
    private final ScrollPaneState equipmentScroll = new ScrollPaneState();
    private final Vector3 pointer = new Vector3();
    private final GlyphLayout glyphLayout = new GlyphLayout();

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private OriginalUiAssets uiAssets;
    private float accumulator;
    private float worldWidth;
    private float worldHeight;
    private float topBarY;
    private volatile float pendingSafeLeft;
    private volatile float pendingSafeTop;
    private volatile float pendingSafeRight;
    private volatile float pendingSafeBottom;
    private volatile boolean safeAreaChanged;
    private float safeLeft;
    private float safeTop;
    private float safeRight;
    private float safeBottom;
    private float pointerStartX;
    private float pointerStartY;
    private float lastPointerY;
    private float lastPointerTime;
    private float gestureVelocityY;
    private float showExitPromptSeconds;
    private int gestureArea;
    private boolean dragging;
    private boolean modalOpen;
    private String selectedEquipment = "";

    public WorldDominationGame() {
        for (int i = 0; i < navRows.length; i++) {
            navRows[i] = new Rectangle();
        }
        for (int i = 0; i < equipmentRows.length; i++) {
            equipmentRows[i] = new Rectangle();
        }
        for (int i = 0; i < equipmentCards.length; i++) {
            equipmentCards[i] = new Rectangle();
        }
        for (int i = 0; i < portraitCards.length; i++) {
            portraitCards[i] = new Rectangle();
        }
    }

    private static float equipmentContentHeight() {
        float height = 0f;
        for (int i = 0; i < EQUIPMENT_GROUP_STARTS.length; i++) {
            int count = EQUIPMENT_GROUP_COUNTS[i];
            int columns = i == 2 ? 1 : 2;
            int rows = (count + columns - 1) / columns;
            height += 23f + rows * EQUIPMENT_ROW_HEIGHT + 8f;
            if (i + 1 < EQUIPMENT_GROUP_STARTS.length) {
                height += 3f;
            }
        }
        return height;
    }

    @Override
    public void create() {
        Gdx.graphics.setForegroundFPS(60);
        Gdx.graphics.setVSync(true);
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        font.setColor(Color.WHITE);
        uiAssets = new OriginalUiAssets();
        Gdx.input.setInputProcessor(this);
        layout(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        layout(width, height);
    }

    private void layout(int width, int height) {
        safeLeft = pendingSafeLeft;
        safeTop = pendingSafeTop;
        safeRight = pendingSafeRight;
        safeBottom = pendingSafeBottom;
        safeAreaChanged = false;
        viewport.update(width, height, true);
        camera.setToOrtho(false, viewport.getWorldWidth(), viewport.getWorldHeight());
        viewport.setCamera(camera);
        camera.position.set(viewport.getWorldWidth() / 2f, viewport.getWorldHeight() / 2f, 0f);
        camera.update();
        worldWidth = viewport.getWorldWidth();
        worldHeight = viewport.getWorldHeight();

        float contentX = safeLeft + 4f;
        float contentY = safeBottom + 4f;
        float contentWidth = Math.max(1f, worldWidth - safeLeft - safeRight - 8f);
        float contentHeight = Math.max(1f, worldHeight - safeTop - safeBottom - 8f);
        float leftWidth = contentWidth * 0.215f;
        float rightWidth = contentWidth * 0.265f;
        leftPanel.set(contentX, contentY, leftWidth, contentHeight);
        rightPanel.set(contentX + contentWidth - rightWidth, contentY,
                rightWidth, contentHeight);
        float centerLeft = leftPanel.x + leftPanel.width + 5f;
        float centerRight = rightPanel.x - 5f;
        float centerWidth = Math.max(1f, centerRight - centerLeft);

        logoPanel.set(leftPanel.x, contentY + contentHeight - 62f, leftWidth, 62f);
        centerHeader.set(centerLeft, contentY + contentHeight - 62f, centerWidth, 62f);
        float navigationPanelHeight = Math.min(contentHeight - 144f,
                5f * (NAV_ROW_HEIGHT + NAV_ROW_GAP) + 24f);
        navPanel.set(leftPanel.x, contentY + 80f, leftWidth, navigationPanelHeight);
        navViewport.set(navPanel.x + 8f, navPanel.y + 8f,
                navPanel.width - 16f, navPanel.height - 24f);
        infoPanel.set(leftPanel.x, contentY, leftWidth, 75f);
        navTitlePlate.set(navPanel.x + navPanel.width / 2f,
                navPanel.y + navPanel.height - 5f, 10f, 14f);
        infoTitlePlate.set(infoPanel.x + infoPanel.width / 2f,
                infoPanel.y + infoPanel.height - 5f, 10f, 14f);
        toolbarPanel.set(rightPanel.x, rightPanel.y + rightPanel.height - 34f,
                rightWidth, 34f);
        helpButton.set(toolbarPanel.x + 7f, toolbarPanel.y + 6f,
                toolbarPanel.width * 0.34f, 22f);
        muteButton.set(helpButton.x + helpButton.width + 4f, toolbarPanel.y + 6f,
                toolbarPanel.width * 0.41f, 22f);
        settingsButton.set(toolbarPanel.x + toolbarPanel.width - 27f,
                toolbarPanel.y + 5f, 22f, 24f);
        stockPanel.set(rightPanel.x + 7f, contentY + 69f,
                rightWidth - 14f, contentHeight - 109f);
        equipmentViewport.set(stockPanel.x, stockPanel.y, stockPanel.width, stockPanel.height);

        mapFrame.set(centerLeft, contentY + 69f, centerWidth, contentHeight - 133f);
        float mapWidth = Math.min(mapFrame.width - 12f,
                (mapFrame.height - 12f) * MAP_WIDTH / MAP_HEIGHT);
        float mapHeight = mapWidth * MAP_HEIGHT / MAP_WIDTH;
        mapBounds.set(mapFrame.x + (mapFrame.width - mapWidth) / 2f,
                mapFrame.y + (mapFrame.height - mapHeight) / 2f, mapWidth, mapHeight);

        float footerHeight = 64f;
        messagePanel.set(centerLeft, contentY, centerWidth * 0.67f, footerHeight);
        scorePanel.set(messagePanel.x + messagePanel.width + 4f, contentY,
                centerRight - messagePanel.x - messagePanel.width - 4f, footerHeight);
        scoreTitlePlate.set(scorePanel.x + scorePanel.width / 2f,
                scorePanel.y + scorePanel.height - 5f, 10f, 14f);
        actionPanel.set(rightPanel.x, contentY, rightWidth, footerHeight);
        actionTitlePlate.set(actionPanel.x + actionPanel.width / 2f,
                actionPanel.y + actionPanel.height - 5f, 10f, 14f);
        float firstWidth = (actionPanel.width - 42f) * 0.5f;
        float tubeY = actionPanel.y + 5f;
        actionSlots[0].set(actionPanel.x + 8f, tubeY, firstWidth, 33f);
        actionSlots[1].set(actionSlots[0].x + firstWidth + 4f, tubeY,
                firstWidth, 33f);
        confirmButton.set(actionPanel.x + actionPanel.width - 35f, tubeY, 27f, 33f);
        portraitStrip.set(centerHeader.x + centerHeader.width * 0.28f,
                centerHeader.y + 1f, centerHeader.width * 0.44f, 6f);
        fitTitlePlate(navTitlePlate, "MAIN PANEL");
        fitTitlePlate(infoTitlePlate, "INFO PANEL");
        fitTitlePlate(scoreTitlePlate, "SCORE");
        fitTitlePlate(actionTitlePlate, "NEXT ACTION");

        modal.set(mapFrame.x + mapFrame.width * 0.11f,
                mapFrame.y + mapFrame.height * 0.02f,
                mapFrame.width * 0.78f, mapFrame.height * 0.96f);
        modalCancel.set(modal.x + modal.width * 0.055f, modal.y + 4f,
                modal.width * 0.15f, modal.height * 0.09f);
        modalAccept.set(modal.x + modal.width * 0.75f, modal.y + 4f,
                modal.width * 0.15f, modal.height * 0.09f);

        navigationScroll.setRange(ACTIONS.length * (NAV_ROW_HEIGHT + NAV_ROW_GAP)
                - NAV_ROW_GAP, navViewport.height);
        equipmentScroll.setRange(equipmentContentHeight(), equipmentViewport.height);
    }

    @Override
    public void render() {
        if (safeAreaChanged) {
            layout(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }
        float frameSeconds = Math.min(Gdx.graphics.getDeltaTime(), 0.25f);
        accumulator = Math.min(accumulator + frameSeconds, FIXED_STEP * 6f);
        while (accumulator >= FIXED_STEP) {
            fixedUpdate(FIXED_STEP);
            accumulator -= FIXED_STEP;
        }

        Gdx.gl.glViewport(viewport.getScreenX(), viewport.getScreenY(),
                viewport.getScreenWidth(), viewport.getScreenHeight());
        Gdx.gl.glClearColor(0.006f, 0.016f, 0.022f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        camera.update();
        drawPanelsAndMap();
        drawTextAndControls();
        if (modalOpen) {
            drawActionModal();
        }
        if (showExitPromptSeconds > 0f) {
            drawExitPrompt();
        }
    }

    private void fixedUpdate(float seconds) {
        navigationScroll.tick(seconds);
        equipmentScroll.tick(seconds);
        showExitPromptSeconds = Math.max(0f, showExitPromptSeconds - seconds);
    }

    /** Safe insets arrive from the Android UI thread and are applied on the game thread. */
    public void setSafeAreaInsets(float left, float top, float right, float bottom) {
        pendingSafeLeft = Math.max(0f, left);
        pendingSafeTop = Math.max(0f, top);
        pendingSafeRight = Math.max(0f, right);
        pendingSafeBottom = Math.max(0f, bottom);
        safeAreaChanged = true;
    }

    private void drawPanelsAndMap() {
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPanelFill(leftPanel);
        drawPanelFill(rightPanel);
        drawPanelFill(logoPanel);
        drawPanelFill(centerHeader);
        drawPanelFill(navPanel);
        drawPanelFill(infoPanel);
        drawPanelFill(toolbarPanel);
        drawPanelFill(stockPanel);
        drawPanelFill(mapFrame);
        drawPanelFill(messagePanel);
        drawPanelFill(scorePanel);
        drawPanelFill(actionPanel);
        shapes.setColor(0.015f, 0.035f, 0.048f, 1f);
        shapes.rect(portraitStrip.x, portraitStrip.y,
                portraitStrip.width, portraitStrip.height);
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawPanelTexture(leftPanel);
        drawPanelTexture(rightPanel);
        drawPanelTexture(logoPanel);
        drawPanelTexture(centerHeader);
        drawPanelTexture(navPanel);
        drawPanelTexture(infoPanel);
        drawPanelTexture(toolbarPanel);
        drawPanelTexture(stockPanel);
        drawPanelTexture(mapFrame);
        drawPanelTexture(messagePanel);
        drawPanelTexture(scorePanel);
        drawPanelTexture(actionPanel);
        batch.draw(uiAssets.battleMap, mapBounds.x, mapBounds.y,
                mapBounds.width, mapBounds.height);
        drawContained(uiAssets.logo, logoPanel.x + 7f, logoPanel.y + 2f,
                logoPanel.width - 14f, logoPanel.height - 4f);
        drawPortraits();
        batch.draw(uiAssets.actionControls[0], actionSlots[0].x, actionSlots[0].y,
                actionSlots[0].width, actionSlots[0].height);
        batch.draw(uiAssets.actionControls[1], actionSlots[1].x, actionSlots[1].y,
                actionSlots[1].width, actionSlots[1].height);
        batch.draw(uiAssets.actionControls[2], confirmButton.x, confirmButton.y,
                confirmButton.width, confirmButton.height);
        batch.setColor(0.56f, 0.70f, 0.75f, 0.90f);
        batch.draw(uiAssets.metalTile, portraitStrip.x, portraitStrip.y,
                portraitStrip.width, portraitStrip.height);
        batch.setColor(Color.WHITE);
        batch.end();
        drawPortraitMeters();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawPanelEdges(leftPanel);
        drawPanelEdges(rightPanel);
        drawPanelEdges(logoPanel);
        drawPanelEdges(centerHeader);
        drawPanelEdges(navPanel);
        drawPanelEdges(infoPanel);
        drawPanelEdges(toolbarPanel);
        drawPanelEdges(stockPanel);
        drawPanelEdges(mapFrame);
        drawPanelEdges(messagePanel);
        drawPanelEdges(scorePanel);
        drawPanelEdges(actionPanel);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawTitlePlate(navTitlePlate);
        drawTitlePlate(infoTitlePlate);
        drawTitlePlate(scoreTitlePlate);
        drawTitlePlate(actionTitlePlate);
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawOriginalButton(helpButton);
        drawOriginalButton(muteButton);
        batch.end();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawButtonGradient(helpButton, false);
        drawButtonGradient(muteButton, false);
        shapes.end();

        drawNavigationRows();
        drawEquipmentRows();
        drawPortraitCardEdges();
        drawMapSelection();
    }

    private void drawPanelTexture(Rectangle rectangle) {
        float inset = 4f;
        float x = rectangle.x + inset;
        float y = rectangle.y + inset;
        float width = Math.max(0f, rectangle.width - inset * 2f);
        float height = Math.max(0f, rectangle.height - inset * 2f);
        batch.setColor(0.55f, 0.68f, 0.73f, 0.52f);
        batch.draw(uiAssets.metalPanel, x, y, width, height,
                0f, 0f, width / 240f, height / 168f);
        batch.setColor(Color.WHITE);
    }

    private void drawPortraits() {
        float slotWidth = Math.min(84f, (centerHeader.width - 16f) / 4f);
        float cardWidth = slotWidth - 3f;
        float cardHeight = Math.min(51f, centerHeader.height - 9f);
        float startX = centerHeader.x + centerHeader.width / 2f - slotWidth * 2f;
        float y = centerHeader.y + 6f;
        for (int i = 0; i < uiAssets.portraits.length; i++) {
            float x = startX + i * slotWidth;
            Rectangle card = portraitCards[i];
            card.set(x, y, cardWidth, cardHeight);
            drawPanelTexture(card);
            drawContained(uiAssets.portraits[i], x + 5f, y + 3f,
                    cardWidth - 10f, cardHeight - 6f);
        }
    }

    private void drawPortraitMeters() {
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < PORTRAIT_COLORS.length; i++) {
            Rectangle card = portraitCards[i];
            shapes.setColor(PORTRAIT_COLORS[i]);
            shapes.rect(card.x + 5f, card.y + card.height - 4f,
                    card.width - 10f, 2f);
        }
        shapes.end();
    }

    private void drawPortraitCardEdges() {
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Rectangle card : portraitCards) {
            drawPanelEdges(card);
        }
        shapes.end();
    }

    private void fitTitlePlate(Rectangle plate, String title) {
        float centerX = plate.x;
        float centerY = plate.y;
        float height = plate.height;
        float previousScale = font.getScaleX();
        font.getData().setScale(8f / Math.max(font.getLineHeight(), 1f));
        glyphLayout.setText(font, title);
        float width = glyphLayout.width + 14f;
        plate.set(centerX - width / 2f, centerY - height / 2f, width, height);
        font.getData().setScale(previousScale);
    }

    private void drawContained(com.badlogic.gdx.graphics.g2d.TextureRegion region,
                               float x, float y, float width, float height) {
        float scale = Math.min(width / region.getRegionWidth(), height / region.getRegionHeight());
        float drawWidth = region.getRegionWidth() * scale;
        float drawHeight = region.getRegionHeight() * scale;
        batch.draw(region, x + (width - drawWidth) / 2f,
                y + (height - drawHeight) / 2f, drawWidth, drawHeight);
    }

    private void drawNavigationRows() {
        setScissor(navViewport);
        float rowStep = NAV_ROW_HEIGHT + NAV_ROW_GAP;
        float top = navViewport.y + navViewport.height;
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (int i = 0; i < ACTIONS.length; i++) {
            float y = top - NAV_ROW_HEIGHT - i * rowStep + navigationScroll.getOffset();
            navRows[i].set(navViewport.x, y, navViewport.width, NAV_ROW_HEIGHT);
            if (y + NAV_ROW_HEIGHT < navViewport.y || y > top) {
                continue;
            }
            drawOriginalButton(navRows[i]);
        }
        batch.end();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < ACTIONS.length; i++) {
            Rectangle row = navRows[i];
            if (row.y + row.height >= navViewport.y && row.y <= top) {
                drawButtonGradient(row, game.getSelectedAction() == ACTIONS[i]);
            }
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < ACTIONS.length; i++) {
            Rectangle row = navRows[i];
            if (row.y + row.height >= navViewport.y && row.y <= top) {
                shapes.setColor(0.51f, 0.67f, 0.73f, 1f);
                shapes.line(row.x + 6f, row.y + row.height - 2f,
                        row.x + row.width - 6f, row.y + row.height - 2f);
            }
        }
        shapes.end();

        batch.begin();
        for (int i = 0; i < ACTIONS.length; i++) {
            Rectangle row = navRows[i];
            if (row.y + row.height < navViewport.y || row.y > top) {
                continue;
            }
            font.setColor(ACTIONS[i].isPorted() ? Color.WHITE : DISABLED_TEXT);
            drawCentered(ACTIONS[i].label.toUpperCase(), row.x + row.width / 2f,
                    row.y + row.height / 2f + 4f, 10f);
        }
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        drawScrollbar(navViewport, navigationScroll,
                ACTIONS.length * (NAV_ROW_HEIGHT + NAV_ROW_GAP) - NAV_ROW_GAP);
    }

    private void drawOriginalButton(Rectangle rectangle) {
        float edge = Math.min(4f, Math.min(rectangle.width / 3f, rectangle.height / 3f));
        float x0 = rectangle.x;
        float x1 = x0 + edge;
        float x2 = rectangle.x + rectangle.width - edge;
        float x3 = rectangle.x + rectangle.width;
        float y0 = rectangle.y;
        float y1 = y0 + edge;
        float y2 = rectangle.y + rectangle.height - edge;
        float y3 = rectangle.y + rectangle.height;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                float patchX = column == 0 ? x0 : column == 1 ? x1 : x2;
                float patchRight = column == 0 ? x1 : column == 1 ? x2 : x3;
                float patchY = row == 0 ? y0 : row == 1 ? y1 : y2;
                float patchTop = row == 0 ? y1 : row == 1 ? y2 : y3;
                batch.draw(uiAssets.buttonPatches[row][column], patchX, patchY,
                        patchRight - patchX, patchTop - patchY);
            }
        }
    }

    private void drawButtonGradient(Rectangle rectangle, boolean selected) {
        float inset = 2.5f;
        float left = rectangle.x + inset;
        float bottom = rectangle.y + 3f;
        float width = rectangle.width - inset * 2f;
        float height = rectangle.height - 6f;
        float[] top = selected ? BUTTON_SELECTED_TOP : BUTTON_NORMAL_TOP;
        float[] end = selected ? BUTTON_SELECTED_BOTTOM : BUTTON_NORMAL_BOTTOM;
        int bands = 8;
        for (int i = 0; i < bands; i++) {
            float t = i / (float) (bands - 1);
            shapes.setColor(top[0] + (end[0] - top[0]) * t,
                    top[1] + (end[1] - top[1]) * t,
                    top[2] + (end[2] - top[2]) * t, 1f);
            float bandHeight = height / bands;
            shapes.rect(left, bottom + i * bandHeight, width, bandHeight + 0.25f);
        }
    }

    private void drawEquipmentRows() {
        setScissor(equipmentViewport);
        float top = equipmentViewport.y + equipmentViewport.height
                + equipmentScroll.getOffset() - 10f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int group = 0; group < EQUIPMENT_GROUP_STARTS.length; group++) {
            int start = EQUIPMENT_GROUP_STARTS[group];
            int count = EQUIPMENT_GROUP_COUNTS[group];
            int columns = group == 2 ? 1 : 2;
            int rowCount = (count + columns - 1) / columns;
            float cardHeight = 23f + rowCount * EQUIPMENT_ROW_HEIGHT + 8f;
            float cardY = top - cardHeight;
            Rectangle card = equipmentCards[group];
            card.set(equipmentViewport.x + 2f, cardY,
                    equipmentViewport.width - 8f, cardHeight);
            equipmentRows[start].set(card.x, card.y + card.height - 23f,
                    card.width, 22f);
            drawPanelFill(card);

            float bodyX = card.x + 8f;
            float bodyWidth = card.width - 16f;
            float gap = columns == 2 ? 8f : 0f;
            float cellWidth = (bodyWidth - gap) / columns;
            for (int item = 0; item < count; item++) {
                int itemRow;
                int itemColumn;
                if (columns == 1) {
                    itemRow = item;
                    itemColumn = 0;
                } else if (count > 2) {
                    itemRow = item % 2;
                    itemColumn = item / 2;
                } else {
                    itemRow = 0;
                    itemColumn = item;
                }
                float itemY = card.y + card.height - 23f
                        - (itemRow + 1) * EQUIPMENT_ROW_HEIGHT + 2f;
                Rectangle row = equipmentRows[start + item + 1];
                row.set(bodyX + itemColumn * (cellWidth + gap), itemY,
                        cellWidth, EQUIPMENT_ROW_HEIGHT - 4f);
                if (EQUIPMENT_ROWS[start + item + 1].equals(selectedEquipment)) {
                    shapes.setColor(BUTTON_SELECTED);
                    shapes.rect(row.x, row.y, row.width, row.height);
                }
            }
            top = cardY - 3f;
        }
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (int group = 0; group < equipmentCards.length; group++) {
            drawPanelTexture(equipmentCards[group]);
        }
        for (int rowIndex = 0; rowIndex < EQUIPMENT_ROWS.length; rowIndex++) {
            if (isEquipmentHeading(rowIndex)) {
                continue;
            }
            Rectangle row = equipmentRows[rowIndex];
            if (row.y + row.height < equipmentViewport.y
                    || row.y > equipmentViewport.y + equipmentViewport.height) {
                continue;
            }
            float controlHeight = Math.min(19f, row.height - 5f);
            float controlWidth = controlHeight * 0.95f;
            boolean stocked = isEquipmentAvailable(rowIndex);
            TextureRegion control = stocked ? uiAssets.inventoryCounter
                    : uiAssets.inventorySwitch;
            float controlX = row.x + row.width - controlWidth - 15f;
            float controlY = row.y + (row.height - controlHeight) / 2f;
            batch.draw(control, controlX, controlY, controlWidth, controlHeight);
        }
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int rowIndex = 0; rowIndex < EQUIPMENT_ROWS.length; rowIndex++) {
            if (isEquipmentHeading(rowIndex)) {
                continue;
            }
            Rectangle row = equipmentRows[rowIndex];
            if (row.y + row.height < equipmentViewport.y
                    || row.y > equipmentViewport.y + equipmentViewport.height) {
                continue;
            }
            float controlHeight = Math.min(19f, row.height - 5f);
            float controlWidth = controlHeight * 0.95f;
            float controlX = row.x + row.width - controlWidth - 15f;
            float controlY = row.y + (row.height - controlHeight) / 2f;
            shapes.setColor(0.02f, 0.06f, 0.08f, 1f);
            shapes.rect(controlX + controlWidth * 0.24f, controlY + controlHeight * 0.24f,
                    controlWidth * 0.52f, controlHeight * 0.52f);
            if (isEquipmentAvailable(rowIndex)) {
                shapes.setColor(0.015f, 0.045f, 0.055f, 1f);
                shapes.rect(controlX + controlWidth * 0.18f,
                        controlY + controlHeight * 0.18f,
                        controlWidth * 0.56f, controlHeight * 0.64f);
            }
            float indicatorX = row.x + row.width - 7f;
            float indicatorY = row.y + row.height / 2f;
            shapes.setColor(0.04f, 0.10f, 0.13f, 1f);
            shapes.circle(indicatorX, indicatorY, 5.2f, 12);
            shapes.setColor(0.57f, 0.19f, 0.13f, 1f);
            shapes.circle(indicatorX, indicatorY, 3.0f, 12);
            shapes.setColor(0.91f, 0.42f, 0.25f, 1f);
            shapes.circle(indicatorX - 0.7f, indicatorY + 0.9f, 1.0f, 8);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Rectangle card : equipmentCards) {
            if (card.y + card.height >= equipmentViewport.y
                    && card.y <= equipmentViewport.y + equipmentViewport.height) {
                drawPanelEdges(card);
            }
        }
        shapes.end();

        batch.begin();
        float originalTextScale = font.getScaleX();
        font.getData().setScale(0.80f);
        for (int group = 0; group < EQUIPMENT_GROUP_STARTS.length; group++) {
            int start = EQUIPMENT_GROUP_STARTS[group];
            Rectangle card = equipmentCards[group];
            if (card.y + card.height < equipmentViewport.y
                    || card.y > equipmentViewport.y + equipmentViewport.height) {
                continue;
            }
            Rectangle heading = equipmentRows[start];
            font.setColor(TITLE_TEXT);
            drawCentered(EQUIPMENT_GROUP_TITLES[group],
                    heading.x + heading.width / 2f, heading.y + 8f, 10f);
        }
        for (int rowIndex = 0; rowIndex < EQUIPMENT_ROWS.length; rowIndex++) {
            if (isEquipmentHeading(rowIndex)) {
                continue;
            }
            Rectangle row = equipmentRows[rowIndex];
            if (row.y + row.height < equipmentViewport.y
                    || row.y > equipmentViewport.y + equipmentViewport.height) {
                continue;
            }
            font.setColor(isEquipmentAvailable(rowIndex) ? TITLE_TEXT : DISABLED_TEXT);
            float countWidth = Math.min(21f, row.height - 7f);
            float countX = row.x + row.width - countWidth - 15f;
            font.draw(batch, EQUIPMENT_ROWS[rowIndex], row.x + 2f,
                    row.y + row.height / 2f + font.getLineHeight() * 0.32f);
            font.setColor(Color.WHITE);
            int stock = equipmentStock(rowIndex);
            drawCentered(stock >= 0 ? Integer.toString(stock) : "-",
                    countX + countWidth / 2f, row.y + row.height / 2f + 3f, 9f);
        }
        font.getData().setScale(originalTextScale);
        batch.end();

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.36f, 0.49f, 0.54f, 1f);
        for (int group = 0; group < EQUIPMENT_GROUP_STARTS.length; group++) {
            Rectangle card = equipmentCards[group];
            if (card.y + card.height < equipmentViewport.y
                    || card.y > equipmentViewport.y + equipmentViewport.height) {
                continue;
            }
            Rectangle heading = equipmentRows[EQUIPMENT_GROUP_STARTS[group]];
            shapes.line(card.x + 8f, heading.y + 11f,
                    heading.x + heading.width * 0.30f, heading.y + 11f);
            shapes.line(heading.x + heading.width * 0.70f, heading.y + 11f,
                    card.x + card.width - 8f, heading.y + 11f);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        drawScrollbar(equipmentViewport, equipmentScroll, equipmentContentHeight());
    }

    private void drawScrollbar(Rectangle area, ScrollPaneState scroll, float contentHeight) {
        if (scroll.getLimit() <= 0f || contentHeight <= 0f) {
            return;
        }
        float trackX = area.x + area.width - 5f;
        float trackY = area.y + 4f;
        float trackHeight = Math.max(1f, area.height - 8f);
        float thumbHeight = Math.max(14f,
                trackHeight * area.height / Math.max(contentHeight, area.height));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        float thumbY = trackY + (trackHeight - thumbHeight)
                * scroll.getOffset() / scroll.getLimit();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.015f, 0.045f, 0.058f, 1f);
        shapes.rect(trackX, trackY, 3f, trackHeight);
        shapes.setColor(0.45f, 0.58f, 0.63f, 1f);
        shapes.rect(trackX, thumbY, 3f, thumbHeight);
        shapes.setColor(0.68f, 0.77f, 0.80f, 1f);
        shapes.rect(trackX + 0.6f, thumbY + 1f, 1f, Math.max(0f, thumbHeight - 2f));
        shapes.end();
    }

    private static boolean isEquipmentAvailable(int rowIndex) {
        return equipmentStock(rowIndex) >= 0;
    }

    /** Source WeaponSlot defaults; production and technology will move these into GameState. */
    private static int equipmentStock(int rowIndex) {
        if (rowIndex == 6) { // Fighter is active in the normal source setup, initially empty.
            return 0;
        }
        if (rowIndex == 34 || rowIndex == 35) { // Anti-air and anti-rocket start at two.
            return 2;
        }
        return -1;
    }

    private void drawMapSelection() {
        int selected = game.getSelectedIsland();
        if (selected < 0) {
            return;
        }
        float cx = mapBounds.x + mapBounds.width * ISLAND_CENTERS[selected][0] / MAP_WIDTH;
        float cy = mapBounds.y + mapBounds.height
                * (MAP_HEIGHT - ISLAND_CENTERS[selected][1]) / MAP_HEIGHT;
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(GREEN);
        shapes.circle(cx, cy, 17f, 24);
        shapes.circle(cx, cy, 22f, 24);
        shapes.end();
    }

    private void drawTextAndControls() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(TITLE_TEXT);
        drawCentered("HELP", helpButton.x + helpButton.width / 2f,
                helpButton.y + helpButton.height / 2f + 3f, 9f);
        drawCentered("MUTE", muteButton.x + muteButton.width / 2f,
                muteButton.y + muteButton.height / 2f + 3f, 9f);
        drawCentered("MAIN PANEL", navTitlePlate.x + navTitlePlate.width / 2f,
                navTitlePlate.y + navTitlePlate.height / 2f - 2f, 8f);
        drawCentered("INFO PANEL", infoTitlePlate.x + infoTitlePlate.width / 2f,
                infoTitlePlate.y + infoTitlePlate.height / 2f - 2f, 8f);
        drawCentered("SCORE", scoreTitlePlate.x + scoreTitlePlate.width / 2f,
                scoreTitlePlate.y + scoreTitlePlate.height / 2f - 2f, 9f);
        drawCentered("NEXT ACTION", actionTitlePlate.x + actionTitlePlate.width / 2f,
                actionTitlePlate.y + actionTitlePlate.height / 2f - 2f, 9f);

        drawStatistics();
        font.setColor(GREEN);
        font.draw(batch, latestMessage(), messagePanel.x + 10f,
                messagePanel.y + messagePanel.height - 15f);
        font.setColor(TITLE_TEXT);
        drawCentered(Integer.toString(game.getHuman().score),
                scorePanel.x + scorePanel.width / 2f, scorePanel.y + scorePanel.height / 2f + 2f, 13f);
        drawCentered("FIRST ACTION", actionSlots[0].x + actionSlots[0].width / 2f,
                actionPanel.y + actionPanel.height - 15f, 7f);
        drawCentered("SECOND ACTION", actionSlots[1].x + actionSlots[1].width / 2f,
                actionPanel.y + actionPanel.height - 15f, 7f);
        drawCentered("CONFIRM", confirmButton.x + confirmButton.width / 2f,
                actionPanel.y + actionPanel.height - 15f, 6f);
        batch.end();

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawStatisticsBars();
        drawQueuedActionBackings();
        shapes.end();
        batch.begin();
        drawQueuedActionLabels();
        batch.end();
        drawSettingsGear();
    }

    private void drawTitlePlate(Rectangle rectangle) {
        float radius = rectangle.height / 2f;
        shapes.setColor(0.30f, 0.41f, 0.46f, 1f);
        shapes.rect(rectangle.x + radius, rectangle.y,
                rectangle.width - radius * 2f, rectangle.height);
        shapes.circle(rectangle.x + radius, rectangle.y + radius, radius, 12);
        shapes.circle(rectangle.x + rectangle.width - radius,
                rectangle.y + radius, radius, 12);
        shapes.setColor(0.045f, 0.085f, 0.105f, 1f);
        float inset = 1f;
        float innerRadius = Math.max(1f, radius - inset);
        shapes.rect(rectangle.x + radius, rectangle.y + inset,
                rectangle.width - radius * 2f, rectangle.height - inset * 2f);
        shapes.circle(rectangle.x + radius, rectangle.y + radius, innerRadius, 12);
        shapes.circle(rectangle.x + rectangle.width - radius,
                rectangle.y + radius, innerRadius, 12);
        shapes.setColor(0.23f, 0.32f, 0.37f, 1f);
        shapes.line(rectangle.x + radius, rectangle.y + rectangle.height - 1.1f,
                rectangle.x + rectangle.width - radius, rectangle.y + rectangle.height - 1.1f);
    }

    private void drawStatistics() {
        float x = infoPanel.x + 9f;
        float width = infoPanel.width - 18f;
        GameState.PlayerState human = game.getHuman();
        float rowHeight = 9.1f;
        float top = infoPanel.y + infoPanel.height - 14f;
        float originalScale = font.getScaleX();
        font.getData().setScale(0.60f);
        font.setColor(TITLE_TEXT);
        font.draw(batch, "FUNDS", x, top);
        font.setColor(GOLD);
        font.draw(batch, "$ " + formatMillions(human.moneyMillions),
                x + width * 0.43f, top);
        font.setColor(DISABLED_TEXT);
        font.draw(batch, "technology", x, top - rowHeight);
        font.draw(batch, "propaganda", x, top - rowHeight * 2f);
        font.draw(batch, "population", x, top - rowHeight * 3f);
        font.draw(batch, "spy", x, top - rowHeight * 4f);
        font.getData().setScale(originalScale);
    }

    private void drawStatisticsBars() {
        float x = infoPanel.x + infoPanel.width * 0.47f;
        float width = infoPanel.width * 0.47f;
        float top = infoPanel.y + infoPanel.height - 23.1f;
        float rowHeight = 9.1f;
        for (int i = 0; i < 4; i++) {
            int value = i == 1 ? game.getHuman().religion
                    : i == 2 ? Math.min(100, game.getHuman().populationMillions / 10) : 0;
            float y = top - i * rowHeight;
            shapes.setColor(0.02f, 0.055f, 0.07f, 1f);
            shapes.rect(x, y, width, 4.2f);
            float filledWidth = width * value / 100f;
            for (int band = 0; band < 8; band++) {
                float segmentLeft = x + filledWidth * band / 8f;
                float segmentWidth = filledWidth / 8f + 0.2f;
                float blend = band / 7f;
                shapes.setColor(1f, 0.19f + blend * 0.68f,
                        0.02f + blend * 0.01f, 1f);
                shapes.rect(segmentLeft, y, segmentWidth, 4.2f);
            }
        }
    }

    private void drawQueuedActionBackings() {
        for (int slot = 0; slot < actionSlots.length; slot++) {
            if (game.getQueuedAction(slot) == null) {
                continue;
            }
            Rectangle target = actionSlots[slot];
            shapes.setColor(1f, 0.48f, 0.02f, 0.92f);
            shapes.rect(target.x + target.width * 0.13f, target.y + 6f,
                    target.width * 0.74f, target.height - 12f);
        }
    }

    private void drawQueuedActionLabels() {
        for (int slot = 0; slot < actionSlots.length; slot++) {
            GameState.Action action = game.getQueuedAction(slot);
            if (action == null) {
                continue;
            }
            Rectangle target = actionSlots[slot];
            font.setColor(QUEUED_ACTION_TEXT);
            drawCentered(action.label.toUpperCase(), target.x + target.width / 2f,
                    target.y + target.height / 2f + 3f, 8f);
        }
    }

    private void drawSettingsGear() {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(TITLE_TEXT);
        float cx = settingsButton.x + settingsButton.width / 2f;
        float cy = settingsButton.y + settingsButton.height / 2f;
        shapes.circle(cx, cy, 6.5f, 16);
        shapes.circle(cx, cy, 2.6f, 12);
        for (int i = 0; i < 8; i++) {
            float angle = (float) (Math.PI * i / 4f);
            shapes.line(cx + (float) Math.cos(angle) * 6f,
                    cy + (float) Math.sin(angle) * 6f,
                    cx + (float) Math.cos(angle) * 9f,
                    cy + (float) Math.sin(angle) * 9f);
        }
        shapes.end();
    }

    private void drawActionModal() {
        GameState.Action action = game.getSelectedAction();
        if (action == GameState.Action.RESOURCES) {
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            batch.draw(uiAssets.resourceModal, modal.x, modal.y,
                    modal.width, modal.height);
            batch.end();
            if (game.getHuman().moneyMillions != 65) {
                shapes.setProjectionMatrix(camera.combined);
                shapes.begin(ShapeRenderer.ShapeType.Filled);
                shapes.setColor(MODAL_ORANGE);
                shapes.rect(modal.x + modal.width * 0.60f,
                        modal.y + modal.height * 0.88f,
                        modal.width * 0.34f, modal.height * 0.07f);
                shapes.end();
                batch.begin();
                font.setColor(Color.BLACK);
                font.draw(batch, "Budget: " + formatMillions(game.getHuman().moneyMillions),
                        modal.x + modal.width * 0.62f,
                        modal.y + modal.height * 0.93f);
                batch.end();
            }
            return;
        }

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPanelFill(modal);
        shapes.setColor(MODAL_ORANGE);
        shapes.rect(modal.x + 3f, modal.y + modal.height - 29f,
                modal.width - 6f, 25f);
        shapes.rect(modal.x + 3f, modal.y + 3f, modal.width - 6f, 25f);
        shapes.end();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawPanelTexture(modal);
        drawOriginalButton(modalAccept);
        drawOriginalButton(modalCancel);
        batch.end();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawButtonGradient(modalAccept, false);
        drawButtonGradient(modalCancel, false);
        shapes.end();
        batch.begin();
        font.setColor(Color.WHITE);
        drawCentered(action == null ? "SELECT AN ACTION" : action.label.toUpperCase(),
                modal.x + modal.width / 2f, modal.y + modal.height - 12f, 12f);
        font.setColor(TITLE_TEXT);
        drawCentered("Increase Mass Media influence.", modal.x + modal.width / 2f,
                modal.y + modal.height - 55f, 10f);
        drawCentered("Influence gain: +" + game.getMassMediaGain(),
                modal.x + modal.width / 2f, modal.y + modal.height - 76f, 10f);
        font.setColor(TITLE_TEXT);
        drawCentered("ACCEPT", modalAccept.x + modalAccept.width / 2f,
                modalAccept.y + modalAccept.height / 2f + 3f, 8f);
        drawCentered("CANCEL", modalCancel.x + modalCancel.width / 2f,
                modalCancel.y + modalCancel.height / 2f + 3f, 8f);
        batch.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawPanelEdges(modal);
        shapes.end();
    }

    private void drawExitPrompt() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPanelFill(modal);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawPanelEdges(modal);
        shapes.end();
        batch.begin();
        font.setColor(Color.WHITE);
        drawCentered("Press Back again to exit", worldWidth / 2f, worldHeight / 2f, 13f);
        batch.end();
    }

    private void drawPanelFill(Rectangle rectangle) {
        shapes.setColor(PANEL);
        shapes.rect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
    }

    private void drawPanelEdges(Rectangle rectangle) {
        shapes.setColor(0.37f, 0.51f, 0.57f, 1f);
        shapes.rect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
        shapes.setColor(0.09f, 0.17f, 0.21f, 1f);
        shapes.rect(rectangle.x + 2f, rectangle.y + 2f,
                rectangle.width - 4f, rectangle.height - 4f);
        shapes.setColor(0.65f, 0.77f, 0.81f, 1f);
        shapes.line(rectangle.x + 7f, rectangle.y + rectangle.height - 2f,
                rectangle.x + rectangle.width - 7f, rectangle.y + rectangle.height - 2f);
        shapes.setColor(0.04f, 0.09f, 0.12f, 1f);
        shapes.line(rectangle.x + 7f, rectangle.y + 3f,
                rectangle.x + rectangle.width - 7f, rectangle.y + 3f);
        float[][] points = {
                {rectangle.x + 5f, rectangle.y + 5f},
                {rectangle.x + rectangle.width - 5f, rectangle.y + 5f},
                {rectangle.x + 5f, rectangle.y + rectangle.height - 5f},
                {rectangle.x + rectangle.width - 5f, rectangle.y + rectangle.height - 5f}
        };
        shapes.setColor(0.16f, 0.25f, 0.29f, 1f);
        for (float[] point : points) {
            shapes.circle(point[0], point[1], 2.7f, 8);
        }
        shapes.setColor(0.72f, 0.82f, 0.84f, 1f);
        for (float[] point : points) {
            shapes.circle(point[0] - 0.5f, point[1] + 0.5f, 1f, 8);
        }
    }

    private void drawButtonFill(Rectangle rectangle, Color color) {
        shapes.setColor(color);
        shapes.rect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
    }

    private void drawButtonEdges(Rectangle rectangle) {
        shapes.setColor(PANEL_EDGE);
        shapes.rect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
    }

    private void drawCentered(String text, float centerX, float baselineY, float targetHeight) {
        float originalScale = font.getScaleX();
        float textHeight = font.getLineHeight();
        float scale = targetHeight / Math.max(textHeight, 1f);
        font.getData().setScale(scale);
        glyphLayout.setText(font, text);
        font.draw(batch, text, centerX - glyphLayout.width / 2f, baselineY);
        font.getData().setScale(originalScale);
    }

    private String slotLabel(int slot) {
        GameState.Action action = game.getQueuedAction(slot);
        String prefix = slot == 0 ? "FIRST: " : "SECOND: ";
        return action == null ? prefix + "EMPTY" : prefix + action.label.toUpperCase();
    }

    private String latestMessage() {
        return game.getLatestMessage();
    }

    private static String formatMillions(int millions) {
        return String.format(java.util.Locale.US, "%,d.000.000", millions);
    }

    private static boolean isEquipmentHeading(int row) {
        switch (row) {
            case 0:
            case 5:
            case 8:
            case 13:
            case 18:
            case 23:
            case 28:
            case 33:
                return true;
            default:
                return false;
        }
    }

    private void setScissor(Rectangle rectangle) {
        float scaleX = viewport.getScreenWidth() / worldWidth;
        float scaleY = viewport.getScreenHeight() / worldHeight;
        int x = viewport.getScreenX() + Math.round(rectangle.x * scaleX);
        int bottom = viewport.getScreenY() + Math.round(rectangle.y * scaleY);
        int width = Math.round(rectangle.width * scaleX);
        int height = Math.round(rectangle.height * scaleY);
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(Math.max(0, x), Math.max(0, bottom), width, height);
    }

    private int pointArea(float x, float y) {
        if (navViewport.contains(x, y)) {
            return GESTURE_NAV;
        }
        if (equipmentViewport.contains(x, y)) {
            return GESTURE_EQUIPMENT;
        }
        return GESTURE_NONE;
    }

    private void unproject(int screenX, int screenY) {
        pointer.set(screenX, screenY, 0f);
        viewport.unproject(pointer);
    }

    private void activateNavigationRow(float x, float y) {
        for (int i = 0; i < navRows.length; i++) {
            if (!navRows[i].contains(x, y)) {
                continue;
            }
            GameState.Action action = ACTIONS[i];
            if (!action.isPorted()) {
                game.cancelSelection();
                continue;
            }
            if (game.getSelectedAction() == action) {
                game.cancelSelection();
                modalOpen = false;
            } else {
                game.selectAction(action);
                modalOpen = game.getSelectedAction() != null;
            }
            return;
        }
    }

    private void activateEquipmentRow(float x, float y) {
        for (int i = 0; i < equipmentRows.length; i++) {
            if (!isEquipmentHeading(i) && equipmentRows[i].contains(x, y)) {
                selectedEquipment = EQUIPMENT_ROWS[i];
                return;
            }
        }
    }

    private void activateIsland(float x, float y) {
        if (!mapBounds.contains(x, y)) {
            return;
        }
        int best = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i < ISLAND_CENTERS.length; i++) {
            float cx = mapBounds.x + mapBounds.width * ISLAND_CENTERS[i][0] / MAP_WIDTH;
            float cy = mapBounds.y + mapBounds.height
                    * (MAP_HEIGHT - ISLAND_CENTERS[i][1]) / MAP_HEIGHT;
            float dx = x - cx;
            float dy = y - cy;
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        float hitRadius = Math.max(26f, mapBounds.height * 0.13f);
        if (best >= 0 && bestDistance <= hitRadius * hitRadius) {
            game.selectIsland(best);
        }
    }

    private void activateAt(float x, float y) {
        if (modalOpen) {
            if (modalAccept.contains(x, y)) {
                game.acceptSelectedAction();
                modalOpen = false;
            } else if (modalCancel.contains(x, y)) {
                game.cancelSelection();
                modalOpen = false;
            }
            return;
        }
        int area = pointArea(x, y);
        if (area == GESTURE_NAV) {
            activateNavigationRow(x, y);
        } else if (area == GESTURE_EQUIPMENT) {
            activateEquipmentRow(x, y);
        } else if (mapFrame.contains(x, y)) {
            activateIsland(x, y);
        } else if (confirmButton.contains(x, y) && game.getQueuedActionCount() == 2) {
            game.confirmTurn();
        } else if (actionSlots[0].contains(x, y)) {
            game.removeQueuedAction(0);
        } else if (actionSlots[1].contains(x, y)) {
            game.removeQueuedAction(1);
        }
    }

    private boolean handleBack() {
        if (modalOpen) {
            modalOpen = false;
            game.cancelSelection();
            return true;
        }
        if (game.getQueuedActionCount() > 0) {
            game.clearQueuedActions();
            return true;
        }
        if (showExitPromptSeconds > 0f) {
            Gdx.app.exit();
        } else {
            showExitPromptSeconds = 2.4f;
        }
        return true;
    }

    @Override
    public boolean keyDown(int keycode) {
        return keycode == Input.Keys.BACK && handleBack();
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointerIndex, int button) {
        if (pointerIndex != 0) {
            return false;
        }
        unproject(screenX, screenY);
        pointerStartX = pointer.x;
        pointerStartY = pointer.y;
        lastPointerY = pointer.y;
        lastPointerTime = System.nanoTime() / 1_000_000_000f;
        gestureVelocityY = 0f;
        dragging = false;
        gestureArea = pointArea(pointer.x, pointer.y);
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointerIndex, int button) {
        if (pointerIndex != 0) {
            return false;
        }
        unproject(screenX, screenY);
        float distance = (float) Math.hypot(pointer.x - pointerStartX, pointer.y - pointerStartY);
        if (gestureArea != GESTURE_NONE && dragging) {
            if (gestureArea == GESTURE_NAV) {
                navigationScroll.endGesture(gestureVelocityY);
            } else {
                equipmentScroll.endGesture(gestureVelocityY);
            }
        } else if (distance < TAP_SLOP) {
            activateAt(pointer.x, pointer.y);
        }
        gestureArea = GESTURE_NONE;
        dragging = false;
        return true;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointerIndex, int button) {
        if (pointerIndex == 0) {
            gestureArea = GESTURE_NONE;
            dragging = false;
            gestureVelocityY = 0f;
        }
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointerIndex) {
        if (pointerIndex != 0 || gestureArea == GESTURE_NONE) {
            return false;
        }
        unproject(screenX, screenY);
        float totalX = pointer.x - pointerStartX;
        float totalY = pointer.y - pointerStartY;
        if (!dragging && Math.abs(totalY) > TAP_SLOP && Math.abs(totalY) > Math.abs(totalX)) {
            dragging = true;
        }
        if (dragging) {
            float delta = pointer.y - lastPointerY;
            if (gestureArea == GESTURE_NAV) {
                navigationScroll.dragBy(-delta);
            } else {
                equipmentScroll.dragBy(-delta);
            }
            float now = System.nanoTime() / 1_000_000_000f;
            float seconds = Math.max(0.001f, now - lastPointerTime);
            gestureVelocityY = -delta / seconds;
            lastPointerTime = now;
            lastPointerY = pointer.y;
        }
        return true;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        unproject(Gdx.input.getX(), Gdx.input.getY());
        if (navViewport.contains(pointer.x, pointer.y)) {
            navigationScroll.moveBy(amountY * NAV_ROW_HEIGHT);
            return true;
        }
        if (equipmentViewport.contains(pointer.x, pointer.y)) {
            equipmentScroll.moveBy(amountY * EQUIPMENT_ROW_HEIGHT);
            return true;
        }
        return false;
    }

    @Override
    public void pause() {
        accumulator = 0f;
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void resume() {
        accumulator = 0f;
        Gdx.input.setInputProcessor(this);
    }

    @Override
    public void dispose() {
        if (uiAssets != null) {
            uiAssets.dispose();
        }
        if (font != null) {
            font.dispose();
        }
        if (shapes != null) {
            shapes.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) { return false; }
    @Override public boolean mouseMoved(int screenX, int screenY) { return false; }
}
