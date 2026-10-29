package com.example.worlddomination.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.ScrollPaneState;

/** Native OpenGL ES game screen; it loads no Python, browser, or network runtime. */
public final class WorldDominationGame extends ApplicationAdapter implements InputProcessor {
    private static final float BASE_WIDTH = 896f;
    private static final float BASE_HEIGHT = 414f;
    private static final float FIXED_STEP = 1f / 24f;
    private static final float TAP_SLOP = 9f;
    private static final float NAV_ROW_HEIGHT = 39f;
    private static final float NAV_ROW_GAP = 5f;
    private static final float EQUIPMENT_ROW_HEIGHT = 28f;
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
    private static final float[][] ISLAND_CENTERS = {
            {425f, 345f}, {140f, 145f}, {665f, 145f}, {150f, 500f}, {665f, 490f}
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

    private final GameState game = new GameState(1);
    private final ExtendViewport viewport = new ExtendViewport(BASE_WIDTH, BASE_HEIGHT, 1400f, 800f);
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Rectangle leftPanel = new Rectangle();
    private final Rectangle rightPanel = new Rectangle();
    private final Rectangle navViewport = new Rectangle();
    private final Rectangle equipmentViewport = new Rectangle();
    private final Rectangle mapFrame = new Rectangle();
    private final Rectangle mapBounds = new Rectangle();
    private final Rectangle footer = new Rectangle();
    private final Rectangle actionSlots[] = {new Rectangle(), new Rectangle()};
    private final Rectangle confirmButton = new Rectangle();
    private final Rectangle modal = new Rectangle();
    private final Rectangle modalAccept = new Rectangle();
    private final Rectangle modalCancel = new Rectangle();
    private final Rectangle[] navRows = new Rectangle[ACTIONS.length];
    private final Rectangle[] equipmentRows = new Rectangle[EQUIPMENT_ROWS.length];
    private final ScrollPaneState navigationScroll = new ScrollPaneState();
    private final ScrollPaneState equipmentScroll = new ScrollPaneState();
    private final Vector3 pointer = new Vector3();
    private final GlyphLayout glyphLayout = new GlyphLayout();

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private Texture mapTexture;
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
        mapTexture = new Texture(Gdx.files.internal("original/map.png"), true);
        mapTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
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

        float leftWidth = Math.max(158f, worldWidth * 0.215f);
        float rightWidth = Math.max(190f, worldWidth * 0.265f);
        float sideGap = 5f;
        float contentHeight = worldHeight - safeTop - safeBottom;
        leftPanel.set(safeLeft + 6f, safeBottom + 6f, leftWidth, contentHeight - 12f);
        rightPanel.set(worldWidth - safeRight - rightWidth - 6f,
                safeBottom + 6f, rightWidth, contentHeight - 12f);
        topBarY = worldHeight - safeTop - 38f;
        navViewport.set(leftPanel.x + 8f, leftPanel.y + 48f,
                leftPanel.width - 16f, leftPanel.height - 108f);
        equipmentViewport.set(rightPanel.x + 8f, rightPanel.y + 8f,
                rightPanel.width - 16f, rightPanel.height - 54f);
        float centerLeft = leftPanel.x + leftPanel.width + sideGap;
        float centerRight = rightPanel.x - sideGap;
        mapFrame.set(centerLeft, safeBottom + 58f, centerRight - centerLeft,
                contentHeight - 107f);
        float mapWidth = Math.min(mapFrame.width - 16f, (mapFrame.height - 16f) * 4f / 3f);
        float mapHeight = mapWidth * 0.75f;
        mapBounds.set(mapFrame.x + (mapFrame.width - mapWidth) / 2f,
                mapFrame.y + (mapFrame.height - mapHeight) / 2f, mapWidth, mapHeight);
        footer.set(centerLeft, safeBottom + 6f, centerRight - centerLeft, 50f);
        float slotWidth = Math.max(76f, (footer.width - 112f) * 0.31f);
        actionSlots[0].set(footer.x + 8f, footer.y + 7f, slotWidth, 36f);
        actionSlots[1].set(actionSlots[0].x + slotWidth + 7f, footer.y + 7f, slotWidth, 36f);
        confirmButton.set(footer.x + footer.width - 91f, footer.y + 7f, 82f, 36f);
        modal.set(mapFrame.x + mapFrame.width * 0.15f, mapFrame.y + mapFrame.height * 0.18f,
                mapFrame.width * 0.70f, mapFrame.height * 0.64f);
        modalAccept.set(modal.x + modal.width * 0.53f, modal.y + 10f, modal.width * 0.40f, 34f);
        modalCancel.set(modal.x + modal.width * 0.07f, modal.y + 10f, modal.width * 0.40f, 34f);

        navigationScroll.setRange(ACTIONS.length * (NAV_ROW_HEIGHT + NAV_ROW_GAP) - NAV_ROW_GAP,
                navViewport.height);
        equipmentScroll.setRange(EQUIPMENT_ROWS.length * EQUIPMENT_ROW_HEIGHT,
                equipmentViewport.height);
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
        drawPanelFill(mapFrame);
        drawPanelFill(footer);
        drawButtonFill(confirmButton, game.getQueuedActionCount() == 2 ? GREEN : BUTTON);
        for (int i = 0; i < actionSlots.length; i++) {
            GameState.Action action = game.getQueuedAction(i);
            drawButtonFill(actionSlots[i], action == null ? BUTTON : BUTTON_SELECTED);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawPanelEdges(leftPanel);
        drawPanelEdges(rightPanel);
        drawPanelEdges(mapFrame);
        drawPanelEdges(footer);
        drawButtonEdges(confirmButton);
        for (Rectangle actionSlot : actionSlots) {
            drawButtonEdges(actionSlot);
        }
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.draw(mapTexture, mapBounds.x, mapBounds.y, mapBounds.width, mapBounds.height);
        batch.end();

        drawNavigationRows();
        drawEquipmentRows();
        drawMapSelection();
    }

    private void drawNavigationRows() {
        setScissor(navViewport);
        float rowStep = NAV_ROW_HEIGHT + NAV_ROW_GAP;
        float top = navViewport.y + navViewport.height;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < ACTIONS.length; i++) {
            float y = top - NAV_ROW_HEIGHT - i * rowStep + navigationScroll.getOffset();
            navRows[i].set(navViewport.x, y, navViewport.width, NAV_ROW_HEIGHT);
            if (y + NAV_ROW_HEIGHT < navViewport.y || y > top) {
                continue;
            }
            boolean active = ACTIONS[i].isPorted();
            drawButtonFill(navRows[i], game.getSelectedAction() == ACTIONS[i]
                    ? BUTTON_SELECTED : (active ? BUTTON : PANEL));
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < ACTIONS.length; i++) {
            Rectangle row = navRows[i];
            if (row.y + row.height >= navViewport.y && row.y <= top) {
                drawButtonEdges(row);
            }
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private void drawEquipmentRows() {
        setScissor(equipmentViewport);
        float top = equipmentViewport.y + equipmentViewport.height;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < EQUIPMENT_ROWS.length; i++) {
            float y = top - EQUIPMENT_ROW_HEIGHT - i * EQUIPMENT_ROW_HEIGHT
                    + equipmentScroll.getOffset();
            equipmentRows[i].set(equipmentViewport.x, y, equipmentViewport.width,
                    EQUIPMENT_ROW_HEIGHT - 2f);
            if (isEquipmentHeading(i) || EQUIPMENT_ROWS[i].equals(selectedEquipment)) {
                Color color = EQUIPMENT_ROWS[i].equals(selectedEquipment)
                        ? BUTTON_SELECTED : PANEL_EDGE;
                shapes.setColor(color);
                shapes.rect(equipmentRows[i].x, equipmentRows[i].y,
                        equipmentRows[i].width, equipmentRows[i].height);
            }
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private void drawMapSelection() {
        int selected = game.getSelectedIsland();
        if (selected < 0) {
            return;
        }
        float cx = mapBounds.x + mapBounds.width * ISLAND_CENTERS[selected][0] / 800f;
        float cy = mapBounds.y + mapBounds.height * (600f - ISLAND_CENTERS[selected][1]) / 600f;
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
        drawCentered("WORLD DOMINATION II", worldWidth / 2f, topBarY + 21f, 17f);
        font.setColor(SUBTITLE_TEXT);
        drawCentered("NORMAL  |  TURN " + game.getTurn(), worldWidth / 2f,
                topBarY + 5f, 9f);
        drawCentered("MAIN PANEL", leftPanel.x + leftPanel.width / 2f,
                leftPanel.y + leftPanel.height - 16f, 10f);
        drawCentered("EQUIPMENT", rightPanel.x + rightPanel.width / 2f,
                rightPanel.y + rightPanel.height - 16f, 10f);

        drawStatistics();
        font.setColor(Color.WHITE);
        drawCentered(slotLabel(0), actionSlots[0].x + actionSlots[0].width / 2f,
                actionSlots[0].y + actionSlots[0].height / 2f + 3f, 9f);
        drawCentered(slotLabel(1), actionSlots[1].x + actionSlots[1].width / 2f,
                actionSlots[1].y + actionSlots[1].height / 2f + 3f, 9f);
        font.setColor(Color.BLACK);
        drawCentered("CONFIRM", confirmButton.x + confirmButton.width / 2f,
                confirmButton.y + confirmButton.height / 2f + 3f, 9f);
        font.setColor(GREEN);
        drawCentered(latestMessage(), mapFrame.x + mapFrame.width / 2f,
                mapFrame.y + mapFrame.height - 13f, 9f);
        if (!selectedEquipment.isEmpty()) {
            font.setColor(Color.WHITE);
            drawCentered("SELECTED: " + selectedEquipment, worldWidth / 2f, 53f, 8f);
        }
        batch.end();

        setScissor(navViewport);
        batch.begin();
        for (int i = 0; i < ACTIONS.length; i++) {
            Rectangle row = navRows[i];
            if (row.y + row.height < navViewport.y || row.y > navViewport.y + navViewport.height) {
                continue;
            }
            font.setColor(ACTIONS[i].isPorted() ? Color.WHITE : DISABLED_TEXT);
            drawCentered(ACTIONS[i].label.toUpperCase(), row.x + row.width / 2f,
                    row.y + row.height / 2f + 3f, 10f);
        }
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        setScissor(equipmentViewport);
        batch.begin();
        for (int i = 0; i < EQUIPMENT_ROWS.length; i++) {
            Rectangle row = equipmentRows[i];
            if (row.y + row.height < equipmentViewport.y
                    || row.y > equipmentViewport.y + equipmentViewport.height) {
                continue;
            }
            font.setColor(isEquipmentHeading(i) ? Color.WHITE : DISABLED_TEXT);
            font.draw(batch, EQUIPMENT_ROWS[i], row.x + 7f, row.y + row.height / 2f + 4f);
        }
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private void drawStatistics() {
        float x = leftPanel.x + 12f;
        float y = leftPanel.y + 14f;
        float width = leftPanel.width - 24f;
        GameState.PlayerState human = game.getHuman();
        font.setColor(GOLD);
        font.draw(batch, "FUNDS  $" + formatMillions(human.moneyMillions), x, y + 40f);
        font.setColor(Color.WHITE);
        font.draw(batch, "POPULATION  " + human.populationMillions + "M", x, y + 27f);
        font.draw(batch, "MASS MEDIA  " + human.religion + "%", x, y + 14f);
        font.draw(batch, "SCORE  " + human.score, x, y + 1f);
    }

    private void drawActionModal() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPanelFill(modal);
        drawButtonFill(modalAccept, GREEN);
        drawButtonFill(modalCancel, BUTTON);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawPanelEdges(modal);
        drawButtonEdges(modalAccept);
        drawButtonEdges(modalCancel);
        shapes.end();
        GameState.Action action = game.getSelectedAction();
        batch.begin();
        font.setColor(Color.WHITE);
        drawCentered(action == null ? "SELECT AN ACTION" : action.label.toUpperCase(),
                modal.x + modal.width / 2f, modal.y + modal.height - 20f, 13f);
        if (action == GameState.Action.RESOURCES) {
            font.setColor(TITLE_TEXT);
            drawCentered("Increase money by gathering resources.", modal.x + modal.width / 2f,
                    modal.y + modal.height - 43f, 10f);
            drawCentered("Current population: " + game.getHuman().populationMillions + ".000.000",
                    modal.x + modal.width / 2f, modal.y + modal.height - 61f, 10f);
            drawCentered("Budget: $" + formatMillions(game.getHuman().moneyMillions),
                    modal.x + modal.width / 2f, modal.y + modal.height - 78f, 10f);
            drawCentered("Estimated income: $" + formatMillions(game.getResourceIncomeMillions()),
                    modal.x + modal.width / 2f, modal.y + modal.height - 95f, 10f);
        } else if (action == GameState.Action.MASS_MEDIA) {
            font.setColor(TITLE_TEXT);
            drawCentered("Increase Mass Media influence.", modal.x + modal.width / 2f,
                    modal.y + modal.height - 48f, 10f);
            drawCentered("Influence gain: +" + game.getMassMediaGain(),
                    modal.x + modal.width / 2f, modal.y + modal.height - 67f, 10f);
        }
        font.setColor(Color.BLACK);
        drawCentered("ACCEPT", modalAccept.x + modalAccept.width / 2f,
                modalAccept.y + modalAccept.height / 2f + 3f, 10f);
        font.setColor(Color.WHITE);
        drawCentered("CANCEL", modalCancel.x + modalCancel.width / 2f,
                modalCancel.y + modalCancel.height / 2f + 3f, 10f);
        batch.end();
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
        shapes.setColor(PANEL_EDGE);
        shapes.rect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
        float radius = 2.2f;
        shapes.circle(rectangle.x + 6f, rectangle.y + 6f, radius, 8);
        shapes.circle(rectangle.x + rectangle.width - 6f, rectangle.y + 6f, radius, 8);
        shapes.circle(rectangle.x + 6f, rectangle.y + rectangle.height - 6f, radius, 8);
        shapes.circle(rectangle.x + rectangle.width - 6f,
                rectangle.y + rectangle.height - 6f, radius, 8);
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
        int top = Gdx.graphics.getHeight() - viewport.getScreenY()
                - Math.round((rectangle.y + rectangle.height) * scaleY);
        int width = Math.round(rectangle.width * scaleX);
        int height = Math.round(rectangle.height * scaleY);
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(Math.max(0, x), Math.max(0, top), width, height);
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
            float cx = mapBounds.x + mapBounds.width * ISLAND_CENTERS[i][0] / 800f;
            float cy = mapBounds.y + mapBounds.height * (600f - ISLAND_CENTERS[i][1]) / 600f;
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
                navigationScroll.dragBy(delta);
            } else {
                equipmentScroll.dragBy(delta);
            }
            float now = System.nanoTime() / 1_000_000_000f;
            float seconds = Math.max(0.001f, now - lastPointerTime);
            gestureVelocityY = delta / seconds;
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
        if (mapTexture != null) {
            mapTexture.dispose();
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
