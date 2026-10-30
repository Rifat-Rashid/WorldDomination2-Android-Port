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
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.ui.MatchOutcomePresentation;
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
    private static final String[] PRODUCTION_CATEGORIES = {
            "ROCKETS", "PLANES", "SPECIAL", "WARHEAD", "BIOLOAD", "SOLDIER", "SPY", "DEFENSE"
    };
    private static final int RESEARCH_ROWS_PER_PAGE = 6;
    private static final String[] DIFFICULTY_LABELS = {"EASY", "NORMAL", "HARD"};
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

    private GameState game;
    private GameSetup setup;
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
    private final Rectangle setupPanel = new Rectangle();
    private final Rectangle setupDifficultyPrevious = new Rectangle();
    private final Rectangle setupDifficultyNext = new Rectangle();
    private final Rectangle setupColorPrevious = new Rectangle();
    private final Rectangle setupColorNext = new Rectangle();
    private final Rectangle setupBuildingPrevious = new Rectangle();
    private final Rectangle setupBuildingNext = new Rectangle();
    private final Rectangle setupLeaderPrevious = new Rectangle();
    private final Rectangle setupLeaderNext = new Rectangle();
    private final Rectangle setupLeaderToggle = new Rectangle();
    private final Rectangle[] setupOpponentSlots = new Rectangle[GameSetup.MAX_OPPONENTS];
    private final Rectangle setupStartButton = new Rectangle();
    private final Rectangle productionSlider = new Rectangle();
    private final Rectangle researchPrevious = new Rectangle();
    private final Rectangle researchNext = new Rectangle();
    private final Rectangle attackTargetPrevious = new Rectangle();
    private final Rectangle attackTargetNext = new Rectangle();
    private final Rectangle attackCityPrevious = new Rectangle();
    private final Rectangle attackCityNext = new Rectangle();
    private final Rectangle attackWeaponPrevious = new Rectangle();
    private final Rectangle attackWeaponNext = new Rectangle();
    private final Rectangle preparedStrikeAdd = new Rectangle();
    private final Rectangle[] navRows = new Rectangle[ACTIONS.length];
    private final Rectangle[] equipmentRows = new Rectangle[EQUIPMENT_ROWS.length];
    private final Rectangle[] equipmentCards = new Rectangle[EQUIPMENT_GROUP_STARTS.length];
    private final Rectangle[] productionCategoryRows = new Rectangle[PRODUCTION_CATEGORIES.length];
    private final Rectangle[] researchRows = new Rectangle[RESEARCH_ROWS_PER_PAGE];
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
    private int preparedCarrierIndex;
    private int preparedTargetPlayer = 1;
    private int preparedCityIndex;
    private int preparedPayloadIndex;
    private int defenseWeaponIndex;
    private boolean dragging;
    private boolean modalOpen;
    private int researchPage;
    private String selectedEquipment = "";
    private String setupMessage = "SELECT ONE TO FOUR OPPONENTS TO BEGIN";
    private boolean showSetupMessage;

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
        for (int i = 0; i < productionCategoryRows.length; i++) {
            productionCategoryRows[i] = new Rectangle();
        }
        for (int i = 0; i < researchRows.length; i++) {
            researchRows[i] = new Rectangle();
        }
        for (int i = 0; i < portraitCards.length; i++) {
            portraitCards[i] = new Rectangle();
        }
        for (int i = 0; i < setupOpponentSlots.length; i++) {
            setupOpponentSlots[i] = new Rectangle();
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
        setup = new GameSetup();
        java.util.Random setupRandom = new java.util.Random(System.nanoTime());
        setup.setPlayerColor(setupRandom.nextInt(5) + 1);
        setup.setStartingBuilding(setupRandom.nextInt(5) + 1);
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
        float categoryGap = 4f;
        float categoryWidth = (modal.width - 18f - categoryGap) / 2f;
        float categoryHeight = Math.min(17f, modal.height * 0.075f);
        float categoryTop = modal.y + modal.height * 0.69f;
        for (int i = 0; i < productionCategoryRows.length; i++) {
            int row = i / 2;
            int column = i % 2;
            productionCategoryRows[i].set(modal.x + 7f + column * (categoryWidth + categoryGap),
                    categoryTop - row * (categoryHeight + 3f) - categoryHeight,
                    categoryWidth, categoryHeight);
        }
        productionSlider.set(modal.x + modal.width * 0.20f, modal.y + modal.height * 0.22f,
                modal.width * 0.60f, 13f);
        float researchRowHeight = Math.min(18f, (modal.height - 83f) / RESEARCH_ROWS_PER_PAGE);
        float researchTop = modal.y + modal.height - 44f;
        for (int i = 0; i < researchRows.length; i++) {
            researchRows[i].set(modal.x + 9f,
                    researchTop - (i + 1) * researchRowHeight,
                    modal.width - 18f, researchRowHeight - 1.5f);
        }
        researchPrevious.set(modal.x + modal.width * 0.36f, modal.y + modal.height - 33f,
                modal.width * 0.10f, 16f);
        researchNext.set(modal.x + modal.width * 0.54f, modal.y + modal.height - 33f,
                modal.width * 0.10f, 16f);
        float attackControlWidth = Math.min(25f, modal.width * 0.12f);
        float attackControlHeight = Math.min(18f, modal.height * 0.085f);
        float attackLeftX = modal.x + modal.width * 0.11f;
        float attackRightX = modal.x + modal.width * 0.77f;
        float attackTargetY = modal.y + modal.height * 0.65f;
        float attackCityY = modal.y + modal.height * 0.46f;
        float attackWeaponY = modal.y + modal.height * 0.27f;
        attackTargetPrevious.set(attackLeftX, attackTargetY, attackControlWidth,
                attackControlHeight);
        attackTargetNext.set(attackRightX, attackTargetY, attackControlWidth,
                attackControlHeight);
        attackCityPrevious.set(attackLeftX, attackCityY, attackControlWidth,
                attackControlHeight);
        attackCityNext.set(attackRightX, attackCityY, attackControlWidth,
                attackControlHeight);
        attackWeaponPrevious.set(attackLeftX, attackWeaponY, attackControlWidth,
                attackControlHeight);
        attackWeaponNext.set(attackRightX, attackWeaponY, attackControlWidth,
                attackControlHeight);
        preparedStrikeAdd.set(modal.x + modal.width * 0.31f,
                modal.y + modal.height * 0.14f, modal.width * 0.38f,
                Math.min(18f, modal.height * 0.075f));

        setupPanel.set(worldWidth * 0.10f, worldHeight * 0.055f,
                worldWidth * 0.80f, worldHeight * 0.89f);
        setupDifficultyPrevious.set(setupPanel.x + setupPanel.width * 0.28f,
                setupPanel.y + setupPanel.height * 0.68f, 24f, 21f);
        setupDifficultyNext.set(setupPanel.x + setupPanel.width * 0.68f,
                setupPanel.y + setupPanel.height * 0.68f, 24f, 21f);
        setupColorPrevious.set(setupPanel.x + setupPanel.width * 0.28f,
                setupPanel.y + setupPanel.height * 0.57f, 24f, 21f);
        setupColorNext.set(setupPanel.x + setupPanel.width * 0.68f,
                setupPanel.y + setupPanel.height * 0.57f, 24f, 21f);
        setupBuildingPrevious.set(setupPanel.x + setupPanel.width * 0.28f,
                setupPanel.y + setupPanel.height * 0.46f, 24f, 21f);
        setupBuildingNext.set(setupPanel.x + setupPanel.width * 0.68f,
                setupPanel.y + setupPanel.height * 0.46f, 24f, 21f);
        setupLeaderPrevious.set(setupPanel.x + setupPanel.width * 0.18f,
                setupPanel.y + setupPanel.height * 0.35f, 24f, 21f);
        setupLeaderNext.set(setupPanel.x + setupPanel.width * 0.78f,
                setupPanel.y + setupPanel.height * 0.35f, 24f, 21f);
        setupLeaderToggle.set(setupPanel.x + setupPanel.width * 0.35f,
                setupPanel.y + setupPanel.height * 0.19f,
                setupPanel.width * 0.30f, 17f);
        float slotGap = 5f;
        float slotWidth = (setupPanel.width * 0.76f - slotGap * 3f) / 4f;
        float slotStartX = setupPanel.x + setupPanel.width * 0.12f;
        for (int i = 0; i < setupOpponentSlots.length; i++) {
            setupOpponentSlots[i].set(slotStartX + i * (slotWidth + slotGap),
                    setupPanel.y + setupPanel.height * 0.105f,
                    slotWidth, 15f);
        }
        setupStartButton.set(setupPanel.x + setupPanel.width * 0.40f,
                setupPanel.y + 1f, setupPanel.width * 0.20f, 15f);

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
        if (game == null) {
            drawSetupScreen();
        } else {
            drawPanelsAndMap();
            drawTextAndControls();
            if (game.getPendingHumanDiplomacyProposal() != null) {
                drawDiplomacyResponseModal();
            } else if (modalOpen) {
                drawActionModal();
            }
            if (MatchOutcomePresentation.isFinished(game.getMatchStatus())) {
                drawEndgameOverlay();
            }
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

    private void drawSetupScreen() {
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPanelFill(setupPanel);
        shapes.setColor(MODAL_ORANGE);
        shapes.rect(setupPanel.x + 3f, setupPanel.y + setupPanel.height - 31f,
                setupPanel.width - 6f, 27f);
        drawSetupChoiceBackground(setupDifficultyPrevious, setupDifficultyNext);
        drawSetupChoiceBackground(setupColorPrevious, setupColorNext);
        drawSetupChoiceBackground(setupBuildingPrevious, setupBuildingNext);
        drawSetupChoiceBackground(setupLeaderPrevious, setupLeaderNext);
        java.util.List<Integer> selectedIds = setup.getSelectedOpponentIds();
        java.util.List<GameSetup.Leader> selected = setup.getSelectedOpponents();
        shapes.setColor(selectedIds.contains(setup.getCandidateLeaderId())
                ? BUTTON_SELECTED : BUTTON);
        shapes.rect(setupLeaderToggle.x, setupLeaderToggle.y,
                setupLeaderToggle.width, setupLeaderToggle.height);
        drawButtonGradient(setupLeaderToggle,
                selectedIds.contains(setup.getCandidateLeaderId()));
        for (int i = 0; i < setupOpponentSlots.length; i++) {
            Rectangle slot = setupOpponentSlots[i];
            shapes.setColor(i < selected.size() ? BUTTON_SELECTED : BUTTON);
            shapes.rect(slot.x, slot.y, slot.width, slot.height);
            drawButtonGradient(slot, i < selected.size());
        }
        shapes.setColor(setup.canStart() ? BUTTON_SELECTED : BUTTON);
        shapes.rect(setupStartButton.x, setupStartButton.y,
                setupStartButton.width, setupStartButton.height);
        drawButtonGradient(setupStartButton, setup.canStart());
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawPanelTexture(setupPanel);
        batch.draw(uiAssets.logo, setupPanel.x + setupPanel.width * 0.19f,
                setupPanel.y + setupPanel.height - 28f, 21f, 24f);
        font.setColor(Color.WHITE);
        drawCentered("WORLD DOMINATION 2", setupPanel.x + setupPanel.width * 0.52f,
                setupPanel.y + setupPanel.height - 13f, 13f);
        font.setColor(TITLE_TEXT);
        drawCentered("SELECT DIFFICULTY", setupPanel.x + setupPanel.width * 0.13f,
                setupDifficultyPrevious.y + 9f, 7f);
        drawCentered(DIFFICULTY_LABELS[setup.getDifficulty()],
                setupPanel.x + setupPanel.width * 0.50f,
                setupDifficultyPrevious.y + 10f, 8f);
        drawCentered("PLAYER COLOR", setupPanel.x + setupPanel.width * 0.13f,
                setupColorPrevious.y + 9f, 7f);
        drawCentered(setup.getPlayerColorName().toUpperCase(java.util.Locale.US),
                setupPanel.x + setupPanel.width * 0.50f,
                setupColorPrevious.y + 10f, 8f);
        drawCentered("BUILDING", setupPanel.x + setupPanel.width * 0.13f,
                setupBuildingPrevious.y + 9f, 7f);
        drawCentered(setup.getStartingBuildingName().toUpperCase(java.util.Locale.US)
                        + " | " + setup.getHumanReligionCode().toUpperCase(java.util.Locale.US),
                setupPanel.x + setupPanel.width * 0.50f,
                setupBuildingPrevious.y + 10f, 8f);
        drawSetupArrows(setupDifficultyPrevious, setupDifficultyNext);
        drawSetupArrows(setupColorPrevious, setupColorNext);
        drawSetupArrows(setupBuildingPrevious, setupBuildingNext);
        GameSetup.Leader candidate = setup.getCandidateLeader();
        drawSetupArrows(setupLeaderPrevious, setupLeaderNext);
        drawCentered(candidate.name, setupPanel.x + setupPanel.width * 0.50f,
                setupLeaderPrevious.y + 12f, 9f);
        font.setColor(SUBTITLE_TEXT);
        String candidateDetails = "LEADER " + (candidate.id + 1) + " OF 19 | "
                + candidate.displayReligionCode;
        drawCentered(showSetupMessage ? setupMessage : candidateDetails,
                setupPanel.x + setupPanel.width * 0.50f,
                setupLeaderPrevious.y + 2f, 6.5f);
        font.setColor(TITLE_TEXT);
        drawCentered((selectedIds.contains(candidate.id) ? "REMOVE RIVAL " : "ADD RIVAL ")
                        + selected.size() + "/4",
                setupLeaderToggle.x + setupLeaderToggle.width / 2f,
                setupLeaderToggle.y + setupLeaderToggle.height / 2f + 3f, 7.5f);
        for (int i = 0; i < setupOpponentSlots.length; i++) {
            String label = i < selected.size() ? selected.get(i).name : "EMPTY";
            Rectangle slot = setupOpponentSlots[i];
            drawCentered(label, slot.x + slot.width / 2f,
                    slot.y + slot.height / 2f + 3f, 6.4f);
        }
        font.setColor(setup.canStart() ? Color.WHITE : DISABLED_TEXT);
        drawCentered("START GAME", setupStartButton.x + setupStartButton.width / 2f,
                setupStartButton.y + setupStartButton.height / 2f + 4f, 9f);
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawPanelEdges(setupPanel);
        drawButtonEdges(setupDifficultyPrevious);
        drawButtonEdges(setupDifficultyNext);
        drawButtonEdges(setupColorPrevious);
        drawButtonEdges(setupColorNext);
        drawButtonEdges(setupBuildingPrevious);
        drawButtonEdges(setupBuildingNext);
        drawButtonEdges(setupLeaderPrevious);
        drawButtonEdges(setupLeaderNext);
        drawButtonEdges(setupLeaderToggle);
        for (Rectangle slot : setupOpponentSlots) {
            drawButtonEdges(slot);
        }
        drawButtonEdges(setupStartButton);
        shapes.end();
    }

    private void drawSetupChoiceBackground(Rectangle previous, Rectangle next) {
        shapes.setColor(BUTTON);
        shapes.rect(previous.x, previous.y, previous.width, previous.height);
        shapes.rect(next.x, next.y, next.width, next.height);
        drawButtonGradient(previous, false);
        drawButtonGradient(next, false);
    }

    private void drawSetupArrows(Rectangle previous, Rectangle next) {
        drawCentered("<", previous.x + previous.width / 2f,
                previous.y + previous.height / 2f + 3f, 8f);
        drawCentered(">", next.x + next.width / 2f,
                next.y + next.height / 2f + 3f, 8f);
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

    /** Draw helper used inside the filled-shape pass for the attack target controls. */
    private void drawAttackControlRow(Rectangle previous, Rectangle next) {
        float rowY = previous.y - 1f;
        shapes.setColor(BUTTON);
        shapes.rect(modal.x + 7f, rowY, modal.width - 14f, previous.height + 2f);
        shapes.setColor(PANEL_EDGE);
        shapes.rect(previous.x, previous.y, previous.width, previous.height);
        shapes.rect(next.x, next.y, next.width, next.height);
    }

    private void drawAttackControlLabels() {
        drawTargetAndCityControlLabels();
        drawCentered("<", attackWeaponPrevious.x + attackWeaponPrevious.width / 2f,
                attackWeaponPrevious.y + attackWeaponPrevious.height / 2f + 3f, 8f);
        drawCentered(">", attackWeaponNext.x + attackWeaponNext.width / 2f,
                attackWeaponNext.y + attackWeaponNext.height / 2f + 3f, 8f);
    }

    private void drawPreparedAttackControls() {
        String preparedCarrier = game.getPreparedCarrierName(0);
        if (preparedCarrier == null) {
            java.util.List<String> carriers = game.getAvailablePreparedCarriers(0);
            drawCentered("PREPARE A CARRIER FOR A LATER LAUNCH",
                    modal.x + modal.width / 2f, modal.y + modal.height * 0.73f, 8f);
            if (carriers.isEmpty()) {
                drawCentered("NO ACTIVE CARRIER HAS A COMPATIBLE PAYLOAD IN STOCK",
                        modal.x + modal.width / 2f, modal.y + modal.height * 0.45f, 7f);
            } else {
                preparedCarrierIndex = Math.floorMod(preparedCarrierIndex, carriers.size());
                String carrier = carriers.get(preparedCarrierIndex);
                drawCentered("<", attackWeaponPrevious.x + attackWeaponPrevious.width / 2f,
                        attackWeaponPrevious.y + attackWeaponPrevious.height / 2f + 3f, 8f);
                drawCentered(">", attackWeaponNext.x + attackWeaponNext.width / 2f,
                        attackWeaponNext.y + attackWeaponNext.height / 2f + 3f, 8f);
                drawCentered(carrier.toUpperCase(java.util.Locale.US) + " | STOCK: "
                                + game.getWeaponStock(0, carrier),
                        modal.x + modal.width / 2f,
                        attackWeaponPrevious.y + attackWeaponPrevious.height / 2f + 3f, 7f);
            }
            if (!game.canAcceptPreparedAttack()) {
                drawCentered("SELECT A STOCKED CARRIER BEFORE ACCEPTING",
                        modal.x + modal.width / 2f, modal.y + modal.height * 0.18f, 7f);
            }
            return;
        }

        boolean ship = isPreparedShipCarrier();
        java.util.List<GameState.ShipStrike> strikes = game.getSelectedShipStrikes();
        int strikeCount = strikes.size();
        if (ship && strikeCount > 0) {
            preparedTargetPlayer = strikes.get(0).targetPlayerId;
        }
        if (strikeCount == 0 || !ship) {
            drawTargetAndCityControlLabels();
        } else {
            drawCentered("<", attackCityPrevious.x + attackCityPrevious.width / 2f,
                    attackCityPrevious.y + attackCityPrevious.height / 2f + 3f, 8f);
            drawCentered(">", attackCityNext.x + attackCityNext.width / 2f,
                    attackCityNext.y + attackCityNext.height / 2f + 3f, 8f);
        }
        drawCentered("<", attackWeaponPrevious.x + attackWeaponPrevious.width / 2f,
                attackWeaponPrevious.y + attackWeaponPrevious.height / 2f + 3f, 8f);
        drawCentered(">", attackWeaponNext.x + attackWeaponNext.width / 2f,
                attackWeaponNext.y + attackWeaponNext.height / 2f + 3f, 8f);

        GameState.PlayerState target = preparedTargetPlayer > 0
                ? game.getPlayerState(preparedTargetPlayer) : null;
        CityState city = target == null ? null : target.getCity(preparedCityIndex);
        java.util.List<String> payloads = getPreparedPayloadChoices();
        drawCentered("CARRIER: " + preparedCarrier.toUpperCase(java.util.Locale.US),
                modal.x + modal.width / 2f, modal.y + modal.height * 0.80f, 8f);
        drawCentered(target == null ? "NO VALID TARGET" : "NATION " + target.id
                        + "   POPULATION " + target.populationMillions + "M",
                modal.x + modal.width / 2f,
                attackTargetPrevious.y + attackTargetPrevious.height / 2f + 3f, 7.5f);
        drawCentered(city == null ? "NO POPULATED CITY"
                        : "CITY " + (city.index + 1) + "   POPULATION "
                        + city.populationMillions + "M",
                modal.x + modal.width / 2f,
                attackCityPrevious.y + attackCityPrevious.height / 2f + 3f, 7.5f);
        if (payloads.isEmpty()) {
            drawCentered("NO COMPATIBLE PAYLOAD STOCK",
                    modal.x + modal.width / 2f,
                    attackWeaponPrevious.y + attackWeaponPrevious.height / 2f + 3f, 7f);
        } else {
            preparedPayloadIndex = Math.floorMod(preparedPayloadIndex, payloads.size());
            String payload = payloads.get(preparedPayloadIndex);
            drawCentered(payload.toUpperCase(java.util.Locale.US) + " | STOCK: "
                            + game.getWeaponStock(0, payload),
                    modal.x + modal.width / 2f,
                    attackWeaponPrevious.y + attackWeaponPrevious.height / 2f + 3f, 7f);
        }
        if (ship) {
            drawCentered("STRIKES " + strikeCount + " / 3",
                    modal.x + modal.width / 2f,
                    preparedStrikeAdd.y + preparedStrikeAdd.height + 11f, 7.5f);
            drawCentered(strikeCount == 3 ? "ADD STRIKE (REPLACES THE OLDEST)"
                            : "ADD STRIKE",
                    preparedStrikeAdd.x + preparedStrikeAdd.width / 2f,
                    preparedStrikeAdd.y + preparedStrikeAdd.height / 2f + 2.5f, 7f);
            if (!game.canAcceptPreparedAttack()) {
                drawCentered("ADD THREE VALID STRIKES TO LAUNCH",
                        modal.x + modal.width / 2f, modal.y + modal.height * 0.23f, 7f);
            }
        } else if (!game.canAcceptPreparedAttack()) {
            drawCentered("CHOOSE A VALID TARGET, CITY, AND PAYLOAD",
                    modal.x + modal.width / 2f, modal.y + modal.height * 0.18f, 7f);
        }
    }

    private void drawDefenseControls() {
        java.util.List<String> weapons = game.getAvailableDefenseWeapons(0);
        if (weapons.isEmpty()) {
            drawCentered("NO ACTIVE DEFENSE WEAPON IS IN STOCK",
                    modal.x + modal.width / 2f, modal.y + modal.height * 0.55f, 8f);
            drawCentered("PRODUCE ANTI-AIRCRAFT OR ANTI-ROCKET DEFENSE",
                    modal.x + modal.width / 2f, modal.y + modal.height * 0.40f, 7f);
            return;
        }
        defenseWeaponIndex = Math.floorMod(defenseWeaponIndex, weapons.size());
        String weapon = weapons.get(defenseWeaponIndex);
        drawCentered("SELECT DEFENSE | THIS TURN",
                modal.x + modal.width / 2f, modal.y + modal.height * 0.70f, 8f);
        drawCentered("<", attackWeaponPrevious.x + attackWeaponPrevious.width / 2f,
                attackWeaponPrevious.y + attackWeaponPrevious.height / 2f + 3f, 8f);
        drawCentered(">", attackWeaponNext.x + attackWeaponNext.width / 2f,
                attackWeaponNext.y + attackWeaponNext.height / 2f + 3f, 8f);
        drawCentered(getDefenseDisplayName(weapon) + " | STOCK: "
                        + game.getWeaponStock(0, weapon),
                modal.x + modal.width / 2f,
                attackWeaponPrevious.y + attackWeaponPrevious.height / 2f + 3f, 8f);
        if (!game.canAcceptDefense()) {
            drawCentered("SELECT AN ACTIVE DEFENSE WEAPON",
                    modal.x + modal.width / 2f, modal.y + modal.height * 0.18f, 7f);
        }
    }

    private boolean isPreparedShipCarrier() {
        String carrier = game.getPreparedCarrierName(0);
        GameState.WeaponState weapon = carrier == null ? null
                : game.getHuman().getWeapons().get(carrier);
        return weapon != null && "ship".equals(weapon.definition.type);
    }

    private static String getDefenseDisplayName(String weapon) {
        if ("antiAir".equals(weapon)) {
            return "ANTI-AIRCRAFT";
        }
        if ("antiRocket".equals(weapon)) {
            return "ANTI-ROCKET";
        }
        return weapon.toUpperCase(java.util.Locale.US);
    }

    private void drawTargetAndCityControlLabels() {
        drawCentered("<", attackTargetPrevious.x + attackTargetPrevious.width / 2f,
                attackTargetPrevious.y + attackTargetPrevious.height / 2f + 3f, 8f);
        drawCentered(">", attackTargetNext.x + attackTargetNext.width / 2f,
                attackTargetNext.y + attackTargetNext.height / 2f + 3f, 8f);
        drawCentered("<", attackCityPrevious.x + attackCityPrevious.width / 2f,
                attackCityPrevious.y + attackCityPrevious.height / 2f + 3f, 8f);
        drawCentered(">", attackCityNext.x + attackCityNext.width / 2f,
                attackCityNext.y + attackCityNext.height / 2f + 3f, 8f);
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

    private boolean isEquipmentAvailable(int rowIndex) {
        String weaponName = equipmentWeaponName(rowIndex);
        return weaponName != null && game.isWeaponActive(0, weaponName);
    }

    private int equipmentStock(int rowIndex) {
        String weaponName = equipmentWeaponName(rowIndex);
        return weaponName == null || !game.isWeaponActive(0, weaponName)
                ? -1 : game.getWeaponStock(weaponName);
    }

    private static String equipmentWeaponName(int rowIndex) {
        if (rowIndex >= 1 && rowIndex <= 4) return "rocket" + rowIndex;
        if (rowIndex == 6) return "plane1";
        if (rowIndex == 7) return "plane2";
        if (rowIndex >= 9 && rowIndex <= 12) {
            switch (rowIndex) {
                case 9: return "sub";
                case 10: return "ship";
                case 11: return "space";
                default: return "specialLoad";
            }
        }
        if (rowIndex >= 14 && rowIndex <= 17) return "warhead" + (rowIndex - 13);
        if (rowIndex >= 19 && rowIndex <= 22) return "bioload" + (rowIndex - 18);
        if (rowIndex >= 24 && rowIndex <= 27) return "soldier" + (rowIndex - 23);
        if (rowIndex >= 29 && rowIndex <= 32) return "spy" + (rowIndex - 28);
        if (rowIndex == 34) return "antiAir";
        if (rowIndex == 35) return "antiRocket";
        return null;
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

        java.util.List<String> availableResearch = action == GameState.Action.RESEARCH
                ? game.getAvailableResearch() : java.util.Collections.emptyList();
        int pageCount = Math.max(1,
                (availableResearch.size() + RESEARCH_ROWS_PER_PAGE - 1) / RESEARCH_ROWS_PER_PAGE);
        researchPage = Math.min(researchPage, pageCount - 1);

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
        if (action == GameState.Action.PRODUCTION) {
            for (int i = 0; i < productionCategoryRows.length; i++) {
                String category = PRODUCTION_CATEGORIES[i].toLowerCase(java.util.Locale.US);
                boolean selected = category.equals(game.getSelectedProductionCategory());
                shapes.setColor(selected ? BUTTON_SELECTED : BUTTON);
                shapes.rect(productionCategoryRows[i].x, productionCategoryRows[i].y,
                        productionCategoryRows[i].width, productionCategoryRows[i].height);
            }
            shapes.setColor(0.015f, 0.05f, 0.07f, 1f);
            shapes.rect(productionSlider.x, productionSlider.y + 5f,
                    productionSlider.width, 3f);
            shapes.setColor(MODAL_ORANGE);
            shapes.rect(productionSlider.x, productionSlider.y + 5f,
                    productionSlider.width * game.getSelectedProductionPercent() / 100f, 3f);
        } else if (action == GameState.Action.RESEARCH) {
            int first = researchPage * RESEARCH_ROWS_PER_PAGE;
            float originalScale = font.getScaleX();
            font.getData().setScale(0.58f);
            for (int i = 0; i < researchRows.length; i++) {
                int item = first + i;
                if (item >= availableResearch.size()) {
                    break;
                }
                String technology = availableResearch.get(item);
                boolean selected = technology.equals(game.getSelectedTechnology());
                shapes.setColor(selected ? BUTTON_SELECTED : BUTTON);
                Rectangle row = researchRows[i];
                shapes.rect(row.x, row.y, row.width, row.height);
            }
            shapes.setColor(BUTTON);
            shapes.rect(researchPrevious.x, researchPrevious.y,
                    researchPrevious.width, researchPrevious.height);
            shapes.rect(researchNext.x, researchNext.y,
                    researchNext.width, researchNext.height);
        } else if (action == GameState.Action.ATTACK
                || action == GameState.Action.PROPAGANDA
                || action == GameState.Action.DIPLOMACY) {
            drawAttackControlRow(attackTargetPrevious, attackTargetNext);
            drawAttackControlRow(attackCityPrevious, attackCityNext);
            if (action == GameState.Action.ATTACK) {
                drawAttackControlRow(attackWeaponPrevious, attackWeaponNext);
            } else if (action == GameState.Action.DIPLOMACY) {
                shapes.setColor(BUTTON);
                shapes.rect(modal.x + 7f, attackWeaponPrevious.y - 1f,
                        modal.width - 14f, attackWeaponPrevious.height + 2f);
            }
        } else if (action == GameState.Action.PREPARED_ATTACK) {
            if (game.getPreparedCarrierName(0) == null) {
                drawAttackControlRow(attackWeaponPrevious, attackWeaponNext);
            } else {
                int strikeCount = game.getSelectedShipStrikeCount();
                if (strikeCount == 0) {
                    drawAttackControlRow(attackTargetPrevious, attackTargetNext);
                } else {
                    shapes.setColor(BUTTON);
                    shapes.rect(modal.x + 7f, attackTargetPrevious.y - 1f,
                            modal.width - 14f, attackTargetPrevious.height + 2f);
                }
                drawAttackControlRow(attackCityPrevious, attackCityNext);
                drawAttackControlRow(attackWeaponPrevious, attackWeaponNext);
                if (isPreparedShipCarrier()) {
                    shapes.setColor(BUTTON);
                    shapes.rect(preparedStrikeAdd.x, preparedStrikeAdd.y,
                            preparedStrikeAdd.width, preparedStrikeAdd.height);
                }
            }
        } else if (action == GameState.Action.DEFENSE) {
            drawAttackControlRow(attackWeaponPrevious, attackWeaponNext);
        }
        shapes.end();
        batch.begin();
        font.setColor(Color.WHITE);
        drawCentered(action == null ? "SELECT AN ACTION" : action.label.toUpperCase(),
                modal.x + modal.width / 2f, modal.y + modal.height - 12f, 12f);
        font.setColor(TITLE_TEXT);
        if (action == GameState.Action.MASS_MEDIA) {
            drawCentered("Increase Mass Media influence.", modal.x + modal.width / 2f,
                    modal.y + modal.height - 55f, 10f);
            drawCentered("Influence gain: +" + game.getMassMediaGain(),
                    modal.x + modal.width / 2f, modal.y + modal.height - 76f, 10f);
        } else if (action == GameState.Action.PRODUCTION) {
            drawCentered("Select category and investment.", modal.x + modal.width / 2f,
                    modal.y + modal.height - 37f, 8f);
            for (int i = 0; i < productionCategoryRows.length; i++) {
                Rectangle row = productionCategoryRows[i];
                drawCentered(PRODUCTION_CATEGORIES[i], row.x + row.width / 2f,
                        row.y + row.height / 2f + 2.5f, 7f);
            }
            drawCentered(game.getSelectedProductionPercent() + "%    Budget: $"
                            + formatMillions(game.getSelectedProductionBudgetMillions()),
                    modal.x + modal.width / 2f, productionSlider.y + 15f, 8f);
        } else if (action == GameState.Action.RESEARCH) {
            int first = researchPage * RESEARCH_ROWS_PER_PAGE;
            float originalScale = font.getScaleX();
            font.getData().setScale(0.58f);
            for (int i = 0; i < researchRows.length; i++) {
                int item = first + i;
                if (item >= availableResearch.size()) {
                    break;
                }
                String technology = availableResearch.get(item);
                Rectangle row = researchRows[i];
                String label = game.getTechnologyLabel(technology) + "   $"
                        + game.getTechnologyCost(technology) + "M";
                font.draw(batch, label, row.x + 7f, row.y + row.height * 0.72f);
            }
            font.getData().setScale(originalScale);
            drawCentered("PREV", researchPrevious.x + researchPrevious.width / 2f,
                    researchPrevious.y + researchPrevious.height / 2f + 2f, 6.5f);
            drawCentered((researchPage + 1) + " / " + pageCount,
                    modal.x + modal.width / 2f,
                    researchPrevious.y + researchPrevious.height / 2f + 2f, 6.5f);
            drawCentered("NEXT", researchNext.x + researchNext.width / 2f,
                    researchNext.y + researchNext.height / 2f + 2f, 6.5f);
            if (availableResearch.isEmpty()) {
                drawCentered("No technology can be researched with the current budget.",
                        modal.x + modal.width / 2f, modal.y + modal.height / 2f, 8f);
            }
        } else if (action == GameState.Action.ATTACK
                || action == GameState.Action.PROPAGANDA
                || action == GameState.Action.DIPLOMACY) {
            GameState.PlayerState target = game.getPlayerState(game.getSelectedTargetPlayer());
            CityState city = target.getCity(game.getSelectedTargetCity());
            if (action == GameState.Action.ATTACK) {
                drawAttackControlLabels();
            } else {
                drawTargetAndCityControlLabels();
            }
            drawCentered("NATION " + target.id + (target.alive ? "" : " (DEFEATED)"),
                    modal.x + modal.width / 2f,
                    attackTargetPrevious.y + attackTargetPrevious.height / 2f + 3f, 8f);
            if (action == GameState.Action.DIPLOMACY) {
                String type = game.getDiplomacyOptionType(game.getSelectedDiplomacyOption());
                String optionText = type.toUpperCase(java.util.Locale.US) + "  $"
                        + game.getSelectedDiplomacyAmount() + "M  /  "
                        + game.getSelectedDiplomacyDuration() + " TURNS";
                drawCentered(optionText, modal.x + modal.width / 2f,
                        attackCityPrevious.y + attackCityPrevious.height / 2f + 3f, 8f);
                drawCentered("REGARD " + game.getHuman().getRegardToward(target.id)
                                + "     CEASEFIRE "
                                + (game.getHuman().hasCeasefireWith(target.id)
                                ? game.getHuman().getCeasefireTurnsWith(target.id) + " TURNS"
                                : "NONE"),
                        modal.x + modal.width / 2f,
                        attackWeaponPrevious.y + attackWeaponPrevious.height / 2f + 3f, 7f);
                if (!game.canAcceptDiplomacy()) {
                    String error = !target.alive ? "Select a living nation."
                            : game.getDiplomacyOptionType(game.getSelectedDiplomacyOption())
                            .equals("offer")
                            && game.getSelectedDiplomacyAmount() > game.getTurnMoneyMillions()
                            ? "The selected offer exceeds the remaining budget."
                            : "A ceasefire or proposal is already active.";
                    drawCentered(error, modal.x + modal.width / 2f,
                            modal.y + modal.height * 0.18f, 7f);
                }
            } else if (game.isSelectedAttackSpy()) {
                drawCentered("SPY BUILDING: "
                                + game.getSelectedSpyBuilding().toUpperCase(java.util.Locale.US),
                        modal.x + modal.width / 2f,
                        attackCityPrevious.y + attackCityPrevious.height / 2f + 3f, 7f);
            } else {
                drawCentered(city == null ? "NO CITY" : "CITY " + (city.index + 1)
                                + "   POPULATION " + city.populationMillions + "M",
                        modal.x + modal.width / 2f,
                        attackCityPrevious.y + attackCityPrevious.height / 2f + 3f, 7f);
            }
            if (action == GameState.Action.ATTACK) {
                int stock = game.getWeaponStock(game.getSelectedAttackWeapon());
                String weaponLabel = game.getSelectedAttackWeapon()
                        .toUpperCase(java.util.Locale.US) + "   STOCK " + stock;
                if (game.isSelectedAttackSpy()) {
                    weaponLabel += "   READINESS " + game.getSpyReadiness() + "%";
                }
                drawCentered(weaponLabel,
                        modal.x + modal.width / 2f,
                        attackWeaponPrevious.y + attackWeaponPrevious.height / 2f + 3f, 7f);
                if (!game.canAcceptAttack()) {
                    drawCentered(game.isSelectedAttackSpy()
                                    ? "Spy readiness or available spy stock is too low."
                                    : "Choose a living target and available unit.",
                            modal.x + modal.width / 2f, modal.y + modal.height * 0.18f, 7f);
                }
            } else if (action == GameState.Action.PROPAGANDA) {
                drawCentered("ESTIMATED CITIZEN TRANSFER: "
                                + game.getPropagandaTransferEstimate(target.id, city.index) + "M",
                        modal.x + modal.width / 2f,
                        attackWeaponPrevious.y + attackWeaponPrevious.height / 2f + 3f, 7f);
                if (!game.canAcceptPropaganda()) {
                    drawCentered("Choose a living nation and populated city.",
                            modal.x + modal.width / 2f, modal.y + modal.height * 0.18f, 7f);
                }
            }
        } else if (action == GameState.Action.PREPARED_ATTACK) {
            drawPreparedAttackControls();
        } else if (action == GameState.Action.DEFENSE) {
            drawDefenseControls();
        }
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

    private void drawEndgameOverlay() {
        GameState.MatchStatus outcome = game.getMatchStatus();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.70f);
        shapes.rect(0f, 0f, worldWidth, worldHeight);
        drawPanelFill(modal);
        shapes.setColor(MODAL_ORANGE);
        shapes.rect(modal.x + 3f, modal.y + modal.height - 29f,
                modal.width - 6f, 25f);
        shapes.rect(modal.x + 3f, modal.y + 3f,
                modal.width - 6f, 25f);
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawPanelTexture(modal);
        drawOriginalButton(modalAccept);
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawButtonGradient(modalAccept, true);
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        drawCentered(MatchOutcomePresentation.title(outcome),
                modal.x + modal.width / 2f, modal.y + modal.height - 12f, 12f);
        font.setColor(TITLE_TEXT);
        drawCentered(MatchOutcomePresentation.message(outcome),
                modal.x + modal.width / 2f, modal.y + modal.height * 0.67f, 8f);
        drawCentered("FINAL SCORE", modal.x + modal.width / 2f,
                modal.y + modal.height * 0.48f, 8f);
        drawCentered(MatchOutcomePresentation.formatScore(game.getHuman().score),
                modal.x + modal.width / 2f, modal.y + modal.height * 0.37f, 15f);
        drawCentered("NEW GAME", modalAccept.x + modalAccept.width / 2f,
                modalAccept.y + modalAccept.height / 2f + 3f, 8f);
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawPanelEdges(modal);
        drawButtonEdges(modalAccept);
        shapes.end();
    }

    private void beginNewGameSetup() {
        setup = new GameSetup();
        java.util.Random setupRandom = new java.util.Random(System.nanoTime());
        setup.setPlayerColor(setupRandom.nextInt(5) + 1);
        setup.setStartingBuilding(setupRandom.nextInt(5) + 1);
        setupMessage = "SELECT ONE TO FOUR OPPONENTS TO BEGIN";
        showSetupMessage = false;
        modalOpen = false;
        showExitPromptSeconds = 0f;
        accumulator = 0f;
        game = null;
    }

    private void drawDiplomacyResponseModal() {
        GameState.DiplomacyProposal proposal = game.getPendingHumanDiplomacyProposal();
        if (proposal == null) {
            return;
        }
        GameState.PlayerState proposer = game.getPlayerState(proposal.proposerId);
        String kind = game.getDiplomacyOptionType(proposal.optionIndex);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPanelFill(modal);
        shapes.setColor(MODAL_ORANGE);
        shapes.rect(modal.x + 3f, modal.y + modal.height - 29f,
                modal.width - 6f, 25f);
        shapes.rect(modal.x + 3f, modal.y + 3f, modal.width - 6f, 25f);
        drawButtonGradient(modalAccept, false);
        drawButtonGradient(modalCancel, false);
        shapes.end();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawPanelTexture(modal);
        drawOriginalButton(modalAccept);
        drawOriginalButton(modalCancel);
        font.setColor(Color.WHITE);
        drawCentered("DIPLOMACY PROPOSAL", modal.x + modal.width / 2f,
                modal.y + modal.height - 12f, 12f);
        font.setColor(TITLE_TEXT);
        drawCentered("NATION " + proposer.id + " MAKES A " + kind.toUpperCase(
                        java.util.Locale.US), modal.x + modal.width / 2f,
                modal.y + modal.height * 0.68f, 9f);
        String moneyDirection = kind.equals("offer") ? "YOU RECEIVE $" : "YOU PAY $";
        drawCentered(moneyDirection + game.getDiplomacyOptionAmount(proposal.optionIndex)
                        + "M   /   "
                        + game.getDiplomacyOptionDuration(proposal.optionIndex) + " TURNS",
                modal.x + modal.width / 2f, modal.y + modal.height * 0.50f, 9f);
        if (!game.canAcceptPendingHumanDiplomacy()) {
            drawCentered("INSUFFICIENT FUNDS TO ACCEPT THIS DEMAND",
                    modal.x + modal.width / 2f, modal.y + modal.height * 0.31f, 7f);
        }
        font.setColor(TITLE_TEXT);
        drawCentered(game.canAcceptPendingHumanDiplomacy() ? "ACCEPT" : "CANNOT ACCEPT",
                modalAccept.x + modalAccept.width / 2f,
                modalAccept.y + modalAccept.height / 2f + 3f, 8f);
        drawCentered("REJECT", modalCancel.x + modalCancel.width / 2f,
                modalCancel.y + modalCancel.height / 2f + 3f, 8f);
        batch.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawPanelEdges(modal);
        shapes.end();
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
                if (action == GameState.Action.RESEARCH) {
                    researchPage = 0;
                }
                modalOpen = game.getSelectedAction() != null;
                if (modalOpen && action == GameState.Action.PREPARED_ATTACK) {
                    initializePreparedAttackSelection();
                } else if (modalOpen && action == GameState.Action.DEFENSE) {
                    initializeDefenseSelection();
                }
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

    private void activateSetupAt(float x, float y) {
        if (setupDifficultyPrevious.contains(x, y)) {
            setup.setDifficulty(Math.floorMod(setup.getDifficulty() - 1, 3));
            showSetupMessage = false;
        } else if (setupDifficultyNext.contains(x, y)) {
            setup.setDifficulty(Math.floorMod(setup.getDifficulty() + 1, 3));
            showSetupMessage = false;
        } else if (setupColorPrevious.contains(x, y)) {
            setup.setPlayerColor(Math.floorMod(setup.getPlayerColor() - 2, 5) + 1);
            showSetupMessage = false;
        } else if (setupColorNext.contains(x, y)) {
            setup.setPlayerColor(Math.floorMod(setup.getPlayerColor(), 5) + 1);
            showSetupMessage = false;
        } else if (setupBuildingPrevious.contains(x, y)) {
            setup.setStartingBuilding(Math.floorMod(setup.getStartingBuilding() - 2, 5) + 1);
            showSetupMessage = false;
        } else if (setupBuildingNext.contains(x, y)) {
            setup.setStartingBuilding(Math.floorMod(setup.getStartingBuilding(), 5) + 1);
            showSetupMessage = false;
        } else if (setupLeaderPrevious.contains(x, y)) {
            setup.cycleCandidateLeader(-1);
            showSetupMessage = false;
        } else if (setupLeaderNext.contains(x, y)) {
            setup.cycleCandidateLeader(1);
            showSetupMessage = false;
        } else if (setupLeaderToggle.contains(x, y)) {
            int leaderId = setup.getCandidateLeaderId();
            boolean alreadySelected = setup.getSelectedOpponentIds().contains(leaderId);
            if (setup.toggleOpponent(leaderId)) {
                setupMessage = alreadySelected ? "RIVAL REMOVED FROM THE MATCH"
                        : "RIVAL ADDED TO THE MATCH";
            } else {
                setupMessage = "THE SOURCE ALLOWS UP TO FOUR RIVALS";
            }
            showSetupMessage = true;
        } else if (setupStartButton.contains(x, y)) {
            if (setup.canStart()) {
                game = new GameState(setup, System.nanoTime());
                modalOpen = false;
                accumulator = 0f;
            } else {
                setupMessage = "SELECT AT LEAST ONE RIVAL BEFORE STARTING";
                showSetupMessage = true;
            }
        } else {
            for (int i = 0; i < setupOpponentSlots.length; i++) {
                if (setupOpponentSlots[i].contains(x, y)) {
                    if (setup.removeOpponentAt(i)) {
                        setupMessage = "RIVAL REMOVED FROM THE MATCH";
                        showSetupMessage = true;
                    }
                    return;
                }
            }
        }
    }

    private void initializePreparedAttackSelection() {
        String preparedCarrier = game.getPreparedCarrierName(0);
        if (preparedCarrier == null) {
            java.util.List<String> carriers = game.getAvailablePreparedCarriers(0);
            preparedCarrierIndex = 0;
            if (!carriers.isEmpty()) {
                game.configurePreparedCarrier(carriers.get(0));
            }
            return;
        }
        preparedPayloadIndex = 0;
        preparedTargetPlayer = findPreparedTarget(game.getSelectedTargetPlayer(), 1);
        preparedCityIndex = findPopulatedPreparedCity(preparedTargetPlayer, 0, 1);
        if (!isPreparedShipCarrier()) {
            refreshPreparedLaunch();
        }
    }

    private void initializeDefenseSelection() {
        java.util.List<String> weapons = game.getAvailableDefenseWeapons(0);
        defenseWeaponIndex = 0;
        if (!weapons.isEmpty()) {
            game.configureDefense(weapons.get(0));
        }
    }

    private java.util.List<String> getPreparedPayloadChoices() {
        String carrier = game.getPreparedCarrierName(0);
        if (carrier == null) {
            java.util.List<String> carriers = game.getAvailablePreparedCarriers(0);
            if (carriers.isEmpty()) {
                return java.util.Collections.emptyList();
            }
            preparedCarrierIndex = Math.floorMod(preparedCarrierIndex, carriers.size());
            carrier = carriers.get(preparedCarrierIndex);
        }
        GameState.WeaponState carrierWeapon = game.getHuman().getWeapons().get(carrier);
        if (carrierWeapon == null) {
            return java.util.Collections.emptyList();
        }
        java.util.List<String> payloads = game.getAvailablePreparedPayloads(0, carrier);
        if (!"ship".equals(carrierWeapon.definition.type)) {
            return payloads;
        }
        java.util.List<GameState.ShipStrike> strikes = game.getSelectedShipStrikes();
        java.util.List<String> selectable = new java.util.ArrayList<>();
        for (String payload : payloads) {
            int selected = 0;
            for (GameState.ShipStrike strike : strikes) {
                if (strike.payloadName.equals(payload)) {
                    selected++;
                }
            }
            boolean replacingOldest = strikes.size() == 3
                    && strikes.get(0).payloadName.equals(payload);
            if (game.getWeaponStock(0, payload) - selected + (replacingOldest ? 1 : 0) > 0) {
                selectable.add(payload);
            }
        }
        return selectable;
    }

    private int findPreparedTarget(int start, int direction) {
        int normalizedStart = start >= 1 && start <= 4 ? start : 1;
        for (int offset = 0; offset < 4; offset++) {
            int candidate = 1 + Math.floorMod(normalizedStart - 1 + direction * offset, 4);
            GameState.PlayerState player = game.getPlayerState(candidate);
            if (!player.alive || player.hasCeasefireWith(0)) {
                continue;
            }
            for (CityState city : player.getCities()) {
                if (city.populationMillions > 0) {
                    return candidate;
                }
            }
        }
        return -1;
    }

    private int findPopulatedPreparedCity(int targetId, int start, int direction) {
        if (targetId <= 0 || targetId >= 5) {
            return -1;
        }
        GameState.PlayerState target = game.getPlayerState(targetId);
        for (int offset = 0; offset < 5; offset++) {
            int candidate = Math.floorMod(start + direction * offset, 5);
            CityState city = target.getCity(candidate);
            if (city != null && city.populationMillions > 0) {
                return candidate;
            }
        }
        return -1;
    }

    private void cyclePreparedCarrier(int direction) {
        java.util.List<String> carriers = game.getAvailablePreparedCarriers(0);
        if (carriers.isEmpty() || direction == 0) {
            return;
        }
        preparedCarrierIndex = Math.floorMod(preparedCarrierIndex + Integer.signum(direction),
                carriers.size());
        game.configurePreparedCarrier(carriers.get(preparedCarrierIndex));
    }

    private void cyclePreparedTarget(int direction) {
        if (direction == 0 || (isPreparedShipCarrier()
                && game.getSelectedShipStrikeCount() > 0)) {
            return;
        }
        int next = findPreparedTarget(preparedTargetPlayer + Integer.signum(direction),
                Integer.signum(direction));
        if (next > 0) {
            preparedTargetPlayer = next;
            preparedCityIndex = findPopulatedPreparedCity(next, 0, 1);
            refreshPreparedLaunch();
        }
    }

    private void cyclePreparedCity(int direction) {
        if (direction == 0) {
            return;
        }
        preparedCityIndex = findPopulatedPreparedCity(preparedTargetPlayer,
                preparedCityIndex + Integer.signum(direction), Integer.signum(direction));
        refreshPreparedLaunch();
    }

    private void cyclePreparedPayload(int direction) {
        java.util.List<String> payloads = getPreparedPayloadChoices();
        if (payloads.isEmpty() || direction == 0) {
            return;
        }
        preparedPayloadIndex = Math.floorMod(preparedPayloadIndex + Integer.signum(direction),
                payloads.size());
        refreshPreparedLaunch();
    }

    private void refreshPreparedLaunch() {
        if (isPreparedShipCarrier()) {
            return;
        }
        java.util.List<String> payloads = getPreparedPayloadChoices();
        if (preparedTargetPlayer <= 0 || preparedCityIndex < 0 || payloads.isEmpty()) {
            return;
        }
        preparedPayloadIndex = Math.floorMod(preparedPayloadIndex, payloads.size());
        game.configurePreparedLaunch(preparedTargetPlayer, preparedCityIndex,
                payloads.get(preparedPayloadIndex));
    }

    private void addPreparedShipStrike() {
        java.util.List<String> payloads = getPreparedPayloadChoices();
        if (preparedTargetPlayer <= 0 || preparedCityIndex < 0 || payloads.isEmpty()) {
            return;
        }
        preparedPayloadIndex = Math.floorMod(preparedPayloadIndex, payloads.size());
        String payload = payloads.get(preparedPayloadIndex);
        game.configurePreparedShipStrike(preparedTargetPlayer, preparedCityIndex, payload);
        java.util.List<GameState.ShipStrike> strikes = game.getSelectedShipStrikes();
        if (!strikes.isEmpty()) {
            preparedTargetPlayer = strikes.get(0).targetPlayerId;
        }
    }

    private void cycleDefenseWeapon(int direction) {
        java.util.List<String> weapons = game.getAvailableDefenseWeapons(0);
        if (weapons.isEmpty() || direction == 0) {
            return;
        }
        defenseWeaponIndex = Math.floorMod(defenseWeaponIndex + Integer.signum(direction),
                weapons.size());
        game.configureDefense(weapons.get(defenseWeaponIndex));
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
        if (game == null) {
            activateSetupAt(x, y);
            return;
        }
        if (MatchOutcomePresentation.isFinished(game.getMatchStatus())) {
            if (modalAccept.contains(x, y)) {
                beginNewGameSetup();
            }
            return;
        }
        if (game.getPendingHumanDiplomacyProposal() != null) {
            if (modalAccept.contains(x, y)) {
                if (game.respondToDiplomacyProposal(true)) {
                    game.cancelSelection();
                    modalOpen = false;
                }
            } else if (modalCancel.contains(x, y)) {
                if (game.respondToDiplomacyProposal(false)) {
                    game.cancelSelection();
                    modalOpen = false;
                }
            }
            return;
        }
        if (modalOpen) {
            GameState.Action action = game.getSelectedAction();
            if (action == GameState.Action.PREPARED_ATTACK) {
                if (game.getPreparedCarrierName(0) == null) {
                    if (attackWeaponPrevious.contains(x, y)) {
                        cyclePreparedCarrier(-1);
                        return;
                    }
                    if (attackWeaponNext.contains(x, y)) {
                        cyclePreparedCarrier(1);
                        return;
                    }
                } else {
                    if (isPreparedShipCarrier() && preparedStrikeAdd.contains(x, y)) {
                        addPreparedShipStrike();
                        return;
                    }
                    if (attackTargetPrevious.contains(x, y)) {
                        cyclePreparedTarget(-1);
                        return;
                    }
                    if (attackTargetNext.contains(x, y)) {
                        cyclePreparedTarget(1);
                        return;
                    }
                    if (attackCityPrevious.contains(x, y)) {
                        cyclePreparedCity(-1);
                        return;
                    }
                    if (attackCityNext.contains(x, y)) {
                        cyclePreparedCity(1);
                        return;
                    }
                    if (attackWeaponPrevious.contains(x, y)) {
                        cyclePreparedPayload(-1);
                        return;
                    }
                    if (attackWeaponNext.contains(x, y)) {
                        cyclePreparedPayload(1);
                        return;
                    }
                }
            } else if (action == GameState.Action.DEFENSE) {
                if (attackWeaponPrevious.contains(x, y)) {
                    cycleDefenseWeapon(-1);
                    return;
                }
                if (attackWeaponNext.contains(x, y)) {
                    cycleDefenseWeapon(1);
                    return;
                }
            } else if (action == GameState.Action.PRODUCTION) {
                for (int i = 0; i < productionCategoryRows.length; i++) {
                    if (productionCategoryRows[i].contains(x, y)) {
                        game.configureProduction(
                                PRODUCTION_CATEGORIES[i].toLowerCase(java.util.Locale.US),
                                game.getSelectedProductionPercent());
                        return;
                    }
                }
                if (productionSlider.contains(x, y)) {
                    float sliderX = Math.max(productionSlider.x,
                            Math.min(x, productionSlider.x + productionSlider.width));
                    int percent = Math.round(100f * (sliderX - productionSlider.x)
                            / productionSlider.width);
                    game.configureProduction(game.getSelectedProductionCategory(), percent);
                    return;
                }
            } else if (action == GameState.Action.RESEARCH) {
                if (researchPrevious.contains(x, y)) {
                    researchPage = Math.max(0, researchPage - 1);
                    return;
                }
                int pageCount = Math.max(1, (game.getAvailableResearch().size()
                        + RESEARCH_ROWS_PER_PAGE - 1) / RESEARCH_ROWS_PER_PAGE);
                if (researchNext.contains(x, y)) {
                    researchPage = Math.min(pageCount - 1, researchPage + 1);
                    return;
                }
                java.util.List<String> available = game.getAvailableResearch();
                int first = researchPage * RESEARCH_ROWS_PER_PAGE;
                for (int i = 0; i < researchRows.length; i++) {
                    int item = first + i;
                    if (item < available.size() && researchRows[i].contains(x, y)) {
                        game.selectResearchTechnology(available.get(item));
                        return;
                    }
                }
            } else if (action == GameState.Action.ATTACK) {
                if (attackTargetPrevious.contains(x, y)) {
                    game.cycleAttackTarget(-1);
                    return;
                }
                if (attackTargetNext.contains(x, y)) {
                    game.cycleAttackTarget(1);
                    return;
                }
                if (attackCityPrevious.contains(x, y)) {
                    if (game.isSelectedAttackSpy()) {
                        game.cycleSpyBuilding(-1);
                    } else {
                        game.cycleAttackCity(-1);
                    }
                    return;
                }
                if (attackCityNext.contains(x, y)) {
                    if (game.isSelectedAttackSpy()) {
                        game.cycleSpyBuilding(1);
                    } else {
                        game.cycleAttackCity(1);
                    }
                    return;
                }
                if (attackWeaponPrevious.contains(x, y)) {
                    game.cycleAttackWeapon(-1);
                    return;
                }
                if (attackWeaponNext.contains(x, y)) {
                    game.cycleAttackWeapon(1);
                    return;
                }
            } else if (action == GameState.Action.PROPAGANDA) {
                if (attackTargetPrevious.contains(x, y)) {
                    game.cyclePropagandaTarget(-1);
                    return;
                }
                if (attackTargetNext.contains(x, y)) {
                    game.cyclePropagandaTarget(1);
                    return;
                }
                if (attackCityPrevious.contains(x, y)) {
                    game.cyclePropagandaCity(-1);
                    return;
                }
                if (attackCityNext.contains(x, y)) {
                    game.cyclePropagandaCity(1);
                    return;
                }
            } else if (action == GameState.Action.DIPLOMACY) {
                if (attackTargetPrevious.contains(x, y)) {
                    game.cycleDiplomacyTarget(-1);
                    return;
                }
                if (attackTargetNext.contains(x, y)) {
                    game.cycleDiplomacyTarget(1);
                    return;
                }
                if (attackCityPrevious.contains(x, y)) {
                    game.cycleDiplomacyOption(-1);
                    return;
                }
                if (attackCityNext.contains(x, y)) {
                    game.cycleDiplomacyOption(1);
                    return;
                }
            }
            if (modalAccept.contains(x, y)) {
                if (game.acceptSelectedAction()) {
                    modalOpen = false;
                }
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
        if (game == null) {
            if (showExitPromptSeconds > 0f) {
                Gdx.app.exit();
            } else {
                showExitPromptSeconds = 2.4f;
            }
            return true;
        }
        if (MatchOutcomePresentation.isFinished(game.getMatchStatus())) {
            if (showExitPromptSeconds > 0f) {
                Gdx.app.exit();
            } else {
                showExitPromptSeconds = 2.4f;
            }
            return true;
        }
        if (game.getPendingHumanDiplomacyProposal() != null) {
            return true;
        }
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
