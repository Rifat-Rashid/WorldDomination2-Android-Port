package com.example.worlddomination.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.example.worlddomination.game.persistence.GameSaveStore;
import com.example.worlddomination.game.ui.GameSettingsMenu;
import java.io.IOException;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.example.worlddomination.game.rendering.ResourceMenuRenderer;
import com.example.worlddomination.game.rendering.SourceHelpRenderer;
import com.example.worlddomination.game.rendering.SourceHiddenHelpRenderer;
import com.example.worlddomination.game.rendering.SourceHiddenBattleEffects;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Pixmap;
import com.example.worlddomination.game.ui.SourceHiddenHelpTypography;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.IntBuffer;
import com.example.worlddomination.game.rendering.SourceNavigationGradient;
import com.example.worlddomination.game.rendering.SourceNavigationPanel;
import com.example.worlddomination.game.rendering.SourceInformationPanel;
import com.example.worlddomination.game.rendering.SourceEquipmentCards;
import com.example.worlddomination.game.rendering.SourceScorePanel;
import com.example.worlddomination.game.ui.ScorePanelLayout;
import com.example.worlddomination.game.ui.SourceHelpState;
import com.example.worlddomination.game.rendering.MessagePanelRenderer;
import com.example.worlddomination.game.ui.MessagePanelLayout;
import com.example.worlddomination.game.assets.OriginalActionTubeAssets;
import com.example.worlddomination.game.ui.ActionSlotAnimation;
import com.example.worlddomination.game.ui.ConfirmTurnAnimation;
import com.example.worlddomination.game.ui.TurnActionDisplay;
import com.example.worlddomination.game.rendering.TurnControlRenderer;
import com.example.worlddomination.game.assets.HighResolutionBitmapFont;
import com.example.worlddomination.game.assets.TechnologyArtworkAssets;
import com.example.worlddomination.game.ui.InformationTextAlignment;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.audio.SatelliteAudioPlayback;
import com.example.worlddomination.game.audio.AircraftAudioPlayback;
import com.example.worlddomination.game.audio.AntiDefenseAudioPlayback;
import com.example.worlddomination.game.assets.OriginalCityAssets;
import com.example.worlddomination.game.assets.OriginalBattleDisplayAssets;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.assets.OriginalSplashAssets;
import com.example.worlddomination.game.assets.OriginalUiAssets;
import com.example.worlddomination.game.rendering.OriginalBattlefieldFrame;
import com.example.worlddomination.game.rendering.SourcePhonePanel;
import com.example.worlddomination.game.rendering.SourceSetupPanelRenderer;
import com.example.worlddomination.game.rendering.SourceSetupColorIndicators;
import com.example.worlddomination.game.rendering.SourceSetupBuildingIndicators;
import com.example.worlddomination.game.persistence.ScorePreferenceBackend;
import com.example.worlddomination.game.persistence.ScorePersistenceException;
import com.example.worlddomination.game.persistence.PersonalBestStore;
import com.example.worlddomination.game.rendering.BattleAnimationSequence;
import com.example.worlddomination.game.rendering.BattleExplosionRenderer;
import com.example.worlddomination.game.rendering.BattlefieldCityRenderer;
import com.example.worlddomination.game.rendering.BattlefieldDisplayState;
import com.example.worlddomination.game.rendering.SourceDisplayList;
import com.example.worlddomination.game.rendering.SourceApacheDisplayDepths;
import com.example.worlddomination.game.rendering.SourceInfantryDisplayDepths;
import com.example.worlddomination.game.rendering.SourceChinookDisplayDepths;
import com.example.worlddomination.game.rendering.SourceSuicideDisplayDepths;
import com.example.worlddomination.game.rendering.SourceRocketDisplayDepths;
import com.example.worlddomination.game.rendering.SourceInterceptedAircraftDisplayDepths;
import com.example.worlddomination.game.rendering.SourceCarrierPlaneDisplayDepths;
import com.example.worlddomination.game.rendering.SourceAirDefenseController;
import com.example.worlddomination.game.rendering.SourceRocketDefenseController;
import com.example.worlddomination.game.rendering.SourceAircraftTrail;
import com.example.worlddomination.game.rendering.SourceAircraftTrailDisplay;
import com.example.worlddomination.game.rendering.SourceActionLabelMapping;
import com.example.worlddomination.game.rendering.SourceBattleUnitAllocation;
import com.example.worlddomination.game.rendering.BattlefieldFlagRenderer;
import com.example.worlddomination.game.rendering.BattlefieldTerrainRenderer;
import com.example.worlddomination.game.rendering.FlashAntiDefenseTrackingAnimation;
import com.example.worlddomination.game.rendering.FlashActionBuildingAnimation;
import com.example.worlddomination.game.rendering.FlashBattleRoutes;
import com.example.worlddomination.game.rendering.FlashBattleUnitRenderer;
import com.example.worlddomination.game.rendering.FlashCarrierPlaneAnimation;
import com.example.worlddomination.game.rendering.FlashStandalonePlaneAnimation;
import com.example.worlddomination.game.rendering.FlashChinookTankAnimation;
import com.example.worlddomination.game.rendering.FlashApacheMarineAnimation;
import com.example.worlddomination.game.rendering.FlashSuicideBomberAnimation;
import com.example.worlddomination.game.rendering.FlashRocketAnimation;
import com.example.worlddomination.game.rendering.FlashSatelliteAnimation;
import com.example.worlddomination.game.rendering.FlashSoldierOneAnimation;
import com.example.worlddomination.game.rendering.FlashSubmarineAnimation;
import com.example.worlddomination.game.rendering.FlashAnimationTimeline;
import com.example.worlddomination.game.rendering.FlashCenterUiRenderer;
import com.example.worlddomination.game.rendering.RenderLoopPolicy;
import com.example.worlddomination.game.rendering.SourceCombatAnimationMapping;
import com.example.worlddomination.game.rendering.TurnPlaybackSequence;
import com.example.worlddomination.game.rendering.SourceBattleRecordClock;
import com.example.worlddomination.game.rendering.SourceBattleInterfaceFrames;
import com.example.worlddomination.game.rendering.SourceBattleActionFrames;
import com.example.worlddomination.game.rendering.SourceStoppedClipQueue;
import com.example.worlddomination.game.rendering.SourceBattleUnitPlayback;
import com.example.worlddomination.game.rendering.SourceBattleTicker;
import com.example.worlddomination.game.rendering.SourceEndgameRenderer;
import com.example.worlddomination.game.assets.OriginalEndgameAssets;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.SourceHiddenCommands;
import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import com.example.worlddomination.game.simulation.TechnologyCatalog;
import com.example.worlddomination.game.ui.ActionFooterLayout;
import com.example.worlddomination.game.ui.ActionScreenLayout;
import com.example.worlddomination.game.ui.SourceActionMenuState;
import com.example.worlddomination.game.ui.SourceStockControlState;
import com.example.worlddomination.game.ui.SourceActionInstruction;
import com.example.worlddomination.game.ui.EconomyWarningText;
import com.example.worlddomination.game.rendering.SourceActionMenuRenderer;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.ui.InformationPanelLayout;
import com.example.worlddomination.game.ui.InformationPanelText;
import com.example.worlddomination.game.ui.SourceStatisticBar;
import com.example.worlddomination.game.ui.MatchOutcomePresentation;
import com.example.worlddomination.game.ui.MatchOutcomeSequence;
import com.example.worlddomination.game.ui.MessageHistoryScrollState;
import com.example.worlddomination.game.ui.NavigationPanelLayout;
import com.example.worlddomination.game.ui.PreparedShipStrikeSelection;
import com.example.worlddomination.game.ui.ProductionScreenLayout;
import com.example.worlddomination.game.ui.ScrollPaneState;
import com.example.worlddomination.game.ui.SafeAreaInsets;
import com.example.worlddomination.game.ui.PanelGestureVelocity;
import com.example.worlddomination.game.ui.SplashScreenLayout;
import com.example.worlddomination.game.ui.SplashScreenState;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import com.example.worlddomination.game.ui.TechnologyScreenLayout;
import com.example.worlddomination.game.ui.TechnologySelectionTextLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;

/** Native OpenGL ES game screen; it loads no Python, browser, or network runtime. */
public final class WorldDominationGame extends ApplicationAdapter implements InputProcessor {
    private static final float BASE_WIDTH = 896f;
    private static final float BASE_HEIGHT = 414f;
    private static final float FIXED_STEP = 1f / 24f;
    private static final float TAP_SLOP = 9f;
    private static final float NAV_ROW_HEIGHT = 40f;
    private static final float NAV_ROW_GAP = 4f;
    private static final float EQUIPMENT_ROW_HEIGHT = 36f;
    private static final float EQUIPMENT_TOP_PADDING = 10f;
    private static final float MAP_WIDTH = 523f;
    private static final float MAP_HEIGHT = 318f;
    private static final int RESEARCH_ROWS_PER_PAGE = 6;
    private static final String[] RESEARCH_GROUP_LABELS = {
            "ROCKETS", "AIRCRAFT", "INFANTRY", "INTELLIGENCE", "SPECIAL CARRIER",
            "WARHEAD", "BIOLOAD"
    };
    private static final String[] DIFFICULTY_LABELS = {"EASY", "MEDIUM", "HARD"};
    private static final Color[] SETUP_PLAYER_COLORS = {
            new Color(0.10f, 0.20f, 0.58f, 1f),
            new Color(0.72f, 0.08f, 0.08f, 1f),
            new Color(0.68f, 0.39f, 0.06f, 1f),
            new Color(0.72f, 0.69f, 0.04f, 1f),
            new Color(0.02f, 0.78f, 0.13f, 1f)
    };
    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_NAV = 1;
    private static final int GESTURE_EQUIPMENT = 2;
    private static final int GESTURE_PRODUCTION = 3;
    private static final int GESTURE_MESSAGE = 4;

    private static final GameState.Action[] ACTIONS = GameState.Action.navigationValues();
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
    private static final String[] EQUIPMENT_WEAPON_NAMES = {
            null, "rocket1", "rocket2", "rocket3", "rocket4",
            null, "plane1", "plane2", null, "sub", "ship", "space", "specialLoad",
            null, "warhead1", "warhead2", "warhead3", "warhead4",
            null, "bioload1", "bioload2", "bioload3", "bioload4",
            null, "soldier1", "soldier2", "soldier3", "soldier4",
            null, "spy1", "spy2", "spy3", "spy4",
            null, "antiAir", "antiRocket"
    };
    private static final String[] EQUIPMENT_GROUP_TITLES = {
            "Rockets", "Aircraft", "Special Carriers", "Warheads",
            "Bioloads", "Infantry", "Intelligence", "Defense"
    };
    private static final String[] PRODUCTION_SPECIAL_WEAPONS = {
            "sub", "ship", "space", "specialLoad"
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
    private static final Color RESEARCH_TOTAL_TEXT = new Color(0f, 36f / 255f, 60f / 255f, 1f);
    private static final Color RESEARCH_UNAVAILABLE_TEXT = new Color(0.95f, 0.36f, 0.25f, 1f);
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

    private static final class PendingBattleAnimation {
        final GameState.CombatEvent event;
        final int delayTicks;

        PendingBattleAnimation(GameState.CombatEvent event, int delayTicks) {
            this.event = event;
            this.delayTicks = delayTicks;
        }
    }

    private GameState game;
    private GameSaveStore gameSaveStore;
    private final GameSettingsMenu settingsMenu = new GameSettingsMenu();

    /** Installed by the Android launcher; no platform storage enters the simulation. */
    public void setGameSaveStore(GameSaveStore store) {
        gameSaveStore = store;
    }
    private GameSetup setup;
    private final SplashScreenState splashState = new SplashScreenState();
    private final Rectangle splashBounds = new Rectangle();
    private PersonalBestStore personalBestStore;
    private String outcomePersonalBestText = "";
    private OriginalEndgameAssets endgameAssets;
    private SourceEndgameRenderer endgameRenderer;
    private final Rectangle endgameContentBounds = new Rectangle();
    private final OriginalAudio audio = new OriginalAudio();
    private final List<BattleAnimationSequence> battleAnimations = new ArrayList<>();
    private final List<FlashActionBuildingAnimation> actionBuildingAnimations =
            new ArrayList<>();
    private final String[] actionSoundSlotFrames = new String[10];
    private final TurnPlaybackSequence turnPlaybackSequence = new TurnPlaybackSequence();
    private final SourceBattleRecordClock battleRecordClock = new SourceBattleRecordClock();
    private final SourceBattleInterfaceFrames battleInterfaceFrames = new SourceBattleInterfaceFrames();
    private final SourceBattleActionFrames battleActionFrames = new SourceBattleActionFrames();
    private final SourceStoppedClipQueue sourceStoppedClips = new SourceStoppedClipQueue();
    private final SourceBattleUnitPlayback sourceUnitPlayback = new SourceBattleUnitPlayback();
    private ReferenceRandom battlePresentationRandom;
    private final SourceBattleTicker battleTicker = new SourceBattleTicker();
    private final SourceHiddenBattleEffects hiddenBattleEffects = new SourceHiddenBattleEffects();
    private final SourceHiddenBattleEffects.Listener hiddenBattleListener =
            new SourceHiddenBattleEffects.Listener() {
                @Override public void explodeCity(int address) {
                    cityRenderer.destroySourceHiddenCity(game, address, battlePresentationRandom);
                }
                @Override public void stopBattleTicker() { battleTicker.reset(); }
                @Override public void allUnitsDone() {
                    sourceStoppedClips.removeAtAllUnitsDone();
                    cityRenderer.removeSourceDustAtAllUnitsDone();
                }
                @Override public void endBattle() {
                    turnPlaybackSequence.reset();
                    finishBattlePresentation();
                    turnActionDisplay.hide();
                    cityRenderer.resetPropagandaPlayback();
                }
            };
    private List<GameState.BattlePresentationRecord> battlePresentationRecords = Collections.emptyList();
    private final List<FlashSoldierOneAnimation> soldierBattleAnimations = new ArrayList<>();
    private final List<FlashApacheMarineAnimation> apacheMarineAnimations = new ArrayList<>();
    private final List<FlashChinookTankAnimation> chinookTankAnimations = new ArrayList<>();
    private final List<FlashSuicideBomberAnimation> suicideBomberAnimations = new ArrayList<>();
    private final List<FlashRocketAnimation> rocketBattleAnimations = new ArrayList<>();
    private final List<SourceRocketDisplayDepths> sourceRocketBindings = new ArrayList<>();
    private final List<SourceInterceptedAircraftDisplayDepths> sourceInterceptedAircraftBindings =
            new ArrayList<>();
    private final java.util.Map<SourceAircraftTrail, SourceAircraftTrailDisplay>
            sourceAircraftTrails = new java.util.IdentityHashMap<>();
    private final List<FlashSubmarineAnimation> submarineBattleAnimations = new ArrayList<>();
    private final List<FlashCarrierPlaneAnimation> carrierPlaneAnimations = new ArrayList<>();
    private final List<SourceCarrierPlaneDisplayDepths> sourceCarrierPlaneBindings =
            new ArrayList<>();
    private final List<SourceRocketDefenseController> sourceRocketDefenseControllers =
            new ArrayList<>();
    private final List<SourceAirDefenseController> sourceAirDefenseControllers =
            new ArrayList<>();
    private final List<FlashStandalonePlaneAnimation> standalonePlaneAnimations = new ArrayList<>();
    private final java.util.Map<Object, AircraftAudioPlayback.Owner> aircraftAudioOwners =
            new java.util.IdentityHashMap<>();
    private final List<FlashAntiDefenseTrackingAnimation> antiDefenseTrackingAnimations =
            new ArrayList<>();
    private final List<FlashSatelliteAnimation> satelliteBattleAnimations = new ArrayList<>();
    private final java.util.Map<FlashSatelliteAnimation, SatelliteAudioPlayback.Owner>
            satelliteAudioOwners = new java.util.IdentityHashMap<>();
    private final List<PendingBattleAnimation> pendingBattleAnimations = new ArrayList<>();
    private final MatchOutcomeSequence outcomeSequence =
            new MatchOutcomeSequence();
    private long scoreCounterSoundId = -1L;
    private final ExtendViewport viewport = new ExtendViewport(BASE_WIDTH, BASE_HEIGHT, 1400f, 800f);
    private final OrthographicCamera camera = new OrthographicCamera();
    private final Rectangle leftPanel = new Rectangle();
    private final Rectangle rightPanel = new Rectangle();
    private final Rectangle logoPanel = new Rectangle();
    private final Rectangle centerHeader = new Rectangle();
    private final Rectangle navPanel = new Rectangle();
    private final Rectangle infoPanel = new Rectangle();
    private final Rectangle portraitStrip = new Rectangle();
    private final Rectangle[] portraitCards = new Rectangle[4];
    private final int[] portraitPlayerIds = {-1, -1, -1, -1};
    private final Rectangle toolbarPanel = new Rectangle();
    private final Rectangle helpButton = new Rectangle();
    private final Rectangle helpMapButton = new Rectangle();
    private final Rectangle muteButton = new Rectangle();
    private final Rectangle settingsButton = new Rectangle();
    private final Rectangle stockPanel = new Rectangle();
    private final Rectangle navViewport = new Rectangle();
    private final Rectangle equipmentViewport = new Rectangle();
    private final Rectangle mapFrame = new Rectangle();
    private final Rectangle mapBounds = new Rectangle();
    private final Rectangle messagePanel = new Rectangle();
    private final Rectangle messageViewport = new Rectangle();
    private final Rectangle scorePanel = new Rectangle();
    private final Rectangle scoreValueBounds = new Rectangle();
    private final Rectangle actionPanel = new Rectangle();
    private final Rectangle actionSlots[] = {new Rectangle(), new Rectangle()};
    private final Rectangle confirmButton = new Rectangle();
    private final Rectangle modal = new Rectangle();
    private final Rectangle modalAccept = new Rectangle();
    private final Rectangle modalCancel = new Rectangle();
    private final SetupScreenLayout setupLayout = new SetupScreenLayout();
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
    private final Rectangle[] productionSliderHitTargets =
            new Rectangle[ProductionScreenLayout.sliderCount()];
    private final Rectangle[] productionSliderMarkers =
            new Rectangle[ProductionScreenLayout.sliderCount()];
    private final Rectangle[] technologyCards =
            new Rectangle[TechnologyScreenLayout.CARD_COUNT];
    private final String[] researchChoiceByCard =
            new String[TechnologyScreenLayout.CARD_COUNT];
    private final StringBuilder selectedResearchSummary = new StringBuilder(128);
    private final int[] researchFrameIndexes = new int[TechnologyScreenLayout.CARD_COUNT];
    private final StringBuilder actionFooterText = new StringBuilder(96);
    private final Rectangle technologySelectedArea = new Rectangle();
    private final Rectangle[] actionFooterButtons = {
            new Rectangle(), new Rectangle(), new Rectangle()
    };
    private final Rectangle[] productionSpecialStatFills = {
            new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle()
    };
    private final float[] productionSpecialProgress = new float[4];
    private final String[] productionSummaryCategories =
            new String[ProductionScreenLayout.sliderCount()];
    private final String[] productionSummaryAmounts =
            new String[ProductionScreenLayout.sliderCount()];
    private final Rectangle[] researchRows = new Rectangle[RESEARCH_ROWS_PER_PAGE];
    private final ScrollPaneState navigationScroll = new ScrollPaneState();
    private final ScrollPaneState equipmentScroll = new ScrollPaneState();
    private final MessageHistoryScrollState messageHistoryScroll =
            new MessageHistoryScrollState();
    private final Vector3 pointer = new Vector3();
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final GlyphLayout productionSummaryLayout = new GlyphLayout();
    private final Rectangle productionSummaryTotalBounds = new Rectangle();
    private final Rectangle productionSummaryCategoryBounds = new Rectangle();
    private final Rectangle productionSummaryAmountBounds = new Rectangle();
    private final GlyphLayout messageHistoryLayout = new GlyphLayout();
    private final GlyphLayout instructionLayout = new GlyphLayout();
    private String laidOutInstruction;
    private float instructionLayoutWidth = -1f;
    private final Rectangle instructionViewport = new Rectangle();

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private TechnologySelectionTextLayout researchTextLayout;
    private ResourceMenuRenderer resourceMenuRenderer;
    private SourceHelpRenderer helpRenderer;
    private SourceHiddenHelpRenderer hiddenHelpRenderer;
    private BitmapFont hiddenHelpCommandFont, hiddenHelpWarningFont;
    private TextureRegion hiddenHelpCaretPixel;
    private long sourcePresentationTicks;
    private final GlyphLayout hiddenHelpCommandGlyph = new GlyphLayout();
    private final GlyphLayout[] hiddenHelpWarningGlyphs = {
            new GlyphLayout(), new GlyphLayout(), new GlyphLayout(), new GlyphLayout()};
    private final Rectangle hiddenHelpFit = new Rectangle();
    private final Rectangle hiddenHelpClip = new Rectangle();
    private final Rectangle hiddenHelpHit = new Rectangle();
    private final Matrix4 hiddenHelpSavedTransform = new Matrix4();
    private final Matrix4 hiddenHelpTextTransform = new Matrix4();
    private final IntBuffer hiddenHelpScissorBox = BufferUtils.newIntBuffer(4);
    private final Color hiddenHelpCommandColor = new Color(SourceHiddenHelpRenderer.COMMAND_RGBA);
    private String hiddenHelpCachedCommand, hiddenHelpCachedWarning;
    private int hiddenHelpWarningLines;
    private boolean hiddenHelpKeyboardVisible;
    private final SourceHelpState helpState = new SourceHelpState();
    private SourceActionMenuRenderer sourceActionMenuRenderer;
    private final SourceActionMenuState sourceActionMenu = new SourceActionMenuState();
    private final SourceActionInstruction sourceActionInstruction = new SourceActionInstruction();
    private final SourceActionMenuRenderer.PortraitDrawer menuPortraitDrawer = this::drawSetupLeaderPortrait;
    private MessagePanelRenderer messagePanelRenderer;
    private SourceNavigationPanel navigationPanelRenderer;
    private SourcePhonePanel phonePanelRenderer;
    private SourceInformationPanel informationPanelRenderer;
    private SourceEquipmentCards equipmentCardsRenderer;
    private SourceScorePanel scorePanelRenderer;
    private OriginalActionTubeAssets actionTubeAssets;
    private final ActionSlotAnimation actionSlotAnimation = new ActionSlotAnimation();
    private final ConfirmTurnAnimation confirmTurnAnimation = new ConfirmTurnAnimation();
    private final TurnActionDisplay turnActionDisplay = new TurnActionDisplay();
    private TurnControlRenderer turnControlRenderer;
    private TechnologyArtworkAssets technologyArtwork;
    private OriginalUiAssets uiAssets;
    private OriginalBattlefieldFrame battlefieldFrameRenderer;
    private SourceSetupPanelRenderer setupPanelRenderer;
    private OriginalSplashAssets splashAssets;
    private OriginalCityAssets cityAssets;
    private OriginalFlashAnimationAssets flashAnimationAssets;
    private BattlefieldCityRenderer cityRenderer;
    private BattlefieldFlagRenderer flagRenderer;
    private BattlefieldTerrainRenderer terrainRenderer;
    private final BattlefieldDisplayState battlefieldDisplay = new BattlefieldDisplayState();
    private FlashAnimationTimeline.Frame authoredBattleFrame;
    private GameState displayGame;
    private GameState preparedTerrainGame;
    private BattleExplosionRenderer explosionRenderer;
    private FlashBattleUnitRenderer battleUnitRenderer;
    private final FlashCenterUiRenderer centerUiRenderer = new FlashCenterUiRenderer();
    private java.util.Map<String, FlashBattleRoutes.Point> battleAnchors;
    private float accumulator;
    private float worldWidth;
    private float worldHeight;
    private float topBarY;
    // Publish all four edges together; a newer UI update stays pending during layout.
    private volatile SafeAreaInsets pendingSafeInsets = SafeAreaInsets.NONE;
    private SafeAreaInsets appliedSafeInsets;
    private float safeLeft;
    private float safeTop;
    private float safeRight;
    private float safeBottom;
    private float pointerStartX;
    private float pointerStartY;
    private float lastPointerY;
    private long lastPointerNanos;
    private float gestureVelocityY;
    private float productionGestureHandleOffsetX;
    private float messageLineHeight = 1f;
    private float messageLayoutWidth = -1f;
    private float showExitPromptSeconds;
    private int gestureArea;
    private int messageDragStartScroll = 1;
    private int cachedMessageCount = -1;
    private int cachedMessageTurn = -1;
    private int preparedCarrierIndex;
    private int preparedTargetPlayer = 1;
    private int preparedCityIndex;
    private int preparedPayloadIndex;
    private int defenseWeaponIndex;
    private int setupLeaderListStart;
    private boolean dragging;
    private boolean ignorePlaybackTouchUntilUp;
    private String cachedLatestMessage = "";
    private String cachedMessageText = "";
    private boolean modalOpen;
    private boolean continuousRenderingEnabled = true;
    private boolean renderingFrame;
    private boolean skipNextFrameDelta;
    private int researchPage;
    private int productionGestureSlider = -1;
    private int productionSummaryCount;
    private String productionSummaryTotal = "total estimated amount: 0.000.000";
    private String selectedEquipment = "";
    private String setupMessage = "SELECT ONE TO FOUR OPPONENTS TO BEGIN";
    private boolean showSetupMessage;
    private String setupLeaderName = "";
    private boolean satelliteFlightCuePlayed;
    private boolean splashPointerDown;
    private boolean splashContinuePending;

    public WorldDominationGame(ScorePreferenceBackend scoreBackend) {
        personalBestStore = new PersonalBestStore(scoreBackend);
        for (int i = 0; i < navRows.length; i++) {
            navRows[i] = new Rectangle();
        }
        for (int i = 0; i < equipmentRows.length; i++) {
            equipmentRows[i] = new Rectangle();
        }
        for (int i = 0; i < equipmentCards.length; i++) {
            equipmentCards[i] = new Rectangle();
        }
        for (int i = 0; i < productionSliderHitTargets.length; i++) {
            productionSliderHitTargets[i] = new Rectangle();
            productionSliderMarkers[i] = new Rectangle();
        }
        for (int i = 0; i < technologyCards.length; i++) {
            technologyCards[i] = new Rectangle();
        }
        for (int i = 0; i < researchRows.length; i++) {
            researchRows[i] = new Rectangle();
        }
        for (int i = 0; i < portraitCards.length; i++) {
            portraitCards[i] = new Rectangle();
        }
    }

    private static float equipmentContentHeight() {
        // Include the inset used by drawing so the last frame can scroll fully into view.
        float height = EQUIPMENT_TOP_PADDING;
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
        Gdx.graphics.setContinuousRendering(false);
        continuousRenderingEnabled = false;
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        authoredBattleFrame = OriginalBattleDisplayAssets.load();
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        resourceMenuRenderer = new ResourceMenuRenderer();
        helpRenderer = new SourceHelpRenderer();
        hiddenHelpRenderer = new SourceHiddenHelpRenderer();
        sourceActionMenuRenderer = new SourceActionMenuRenderer();
        messagePanelRenderer = new MessagePanelRenderer();
        actionTubeAssets = new OriginalActionTubeAssets();
        turnControlRenderer = new TurnControlRenderer();
        font = new HighResolutionBitmapFont();
        font.getData().setScale(TechnologyScreenLayout.SELECTED_TEXT_LINE_HEIGHT / font.getLineHeight());
        researchTextLayout = new TechnologySelectionTextLayout(text -> {
            float advance = 0f;
            for (int index = 0; index < text.length(); index++) {
                advance += font.getData().getGlyph(text.charAt(index)).xadvance * font.getData().scaleX;
            }
            return advance;
        });
        font.getData().setScale(1f);
        technologyArtwork = new TechnologyArtworkAssets();
        centerUiRenderer.setTechnologyArtwork(technologyArtwork);
        font.setColor(Color.WHITE);
        uiAssets = new OriginalUiAssets();
        navigationPanelRenderer = new SourceNavigationPanel(uiAssets.navigationPanel);
        phonePanelRenderer = new SourcePhonePanel(uiAssets.phonePanel[0],
                uiAssets.phonePanel[1], uiAssets.phonePanel[2]);
        informationPanelRenderer = new SourceInformationPanel(uiAssets.informationPanel);
        equipmentCardsRenderer = new SourceEquipmentCards(uiAssets.equipmentCards);
        scorePanelRenderer = new SourceScorePanel(uiAssets.scoreBacking, uiAssets.scoreWindow,
                uiAssets.scoreDots, uiAssets.scoreTitle);
        battlefieldFrameRenderer = new OriginalBattlefieldFrame(uiAssets.battlefieldFrame);
        setupPanelRenderer = new SourceSetupPanelRenderer();
        splashAssets = new OriginalSplashAssets();
        cityAssets = new OriginalCityAssets(true);
        audio.initialize();
        setup = new GameSetup(System.nanoTime());
        Gdx.input.setInputProcessor(this);
        layout(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.graphics.requestRendering();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        layout(width, height);
        Gdx.graphics.requestRendering();
    }

    private void layout(int width, int height) {
        if (phonePanelRenderer != null) phonePanelRenderer.invalidate();
        SafeAreaInsets pixelInsets = pendingSafeInsets;
        viewport.update(width, height, true);
        camera.setToOrtho(false, viewport.getWorldWidth(), viewport.getWorldHeight());
        viewport.setCamera(camera);
        camera.position.set(viewport.getWorldWidth() / 2f, viewport.getWorldHeight() / 2f, 0f);
        camera.update();
        worldWidth = viewport.getWorldWidth();
        worldHeight = viewport.getWorldHeight();
        SafeAreaInsets worldInsets = pixelInsets.relativeTo(
                width, height, viewport.getScreenX(),
                height - viewport.getScreenY() - viewport.getScreenHeight(),
                viewport.getScreenWidth(), viewport.getScreenHeight()).toWorld(
                worldWidth, worldHeight, viewport.getScreenWidth(), viewport.getScreenHeight());
        safeLeft = worldInsets.left;
        safeTop = worldInsets.top;
        safeRight = worldInsets.right;
        safeBottom = worldInsets.bottom;
        appliedSafeInsets = pixelInsets;
        SplashScreenLayout.fit(worldWidth, worldHeight, splashBounds);

        float contentX = safeLeft + 4f;
        float contentY = safeBottom + 4f;
        float contentWidth = Math.max(1f, worldWidth - safeLeft - safeRight - 8f);
        float contentHeight = Math.max(1f, worldHeight - safeTop - safeBottom - 8f);
        endgameContentBounds.set(contentX, contentY, contentWidth, contentHeight);
        if (endgameRenderer != null) {
            endgameRenderer.layout().fit(contentX, contentY, contentWidth, contentHeight);
        }
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
        navPanel.set(leftPanel.x, contentY + 86f, leftWidth, navigationPanelHeight);
        NavigationPanelLayout.setContentBounds(navPanel, navViewport);
        infoPanel.set(leftPanel.x, contentY, leftWidth, 82f);
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
        MessagePanelLayout.updateViewport(messagePanel, messageViewport);
        messageLayoutWidth = -1f;
        scorePanel.set(messagePanel.x + messagePanel.width + 4f, contentY,
                centerRight - messagePanel.x - messagePanel.width - 4f, footerHeight);
        actionPanel.set(rightPanel.x, contentY, rightWidth, footerHeight);
        float firstWidth = (actionPanel.width - 42f) * 0.5f;
        float tubeY = actionPanel.y + 5f;
        actionSlots[0].set(actionPanel.x + 8f, tubeY, firstWidth, 33f);
        actionSlots[1].set(actionSlots[0].x + firstWidth + 4f, tubeY,
                firstWidth, 33f);
        confirmButton.set(actionPanel.x + actionPanel.width - 35f, tubeY, 27f, 33f);
        portraitStrip.set(centerHeader.x + centerHeader.width * 0.28f,
                centerHeader.y + 1f, centerHeader.width * 0.44f, 6f);
        for (int i = 0; i < actionFooterButtons.length; i++) {
            ActionFooterLayout.setButtonBounds(messagePanel, i, actionFooterButtons[i]);
        }

        ProductionScreenLayout.setModalBounds(mapFrame, modal);
        float modalScaleX = modal.width / ProductionScreenLayout.ART_WIDTH;
        float modalScaleY = modal.height / ProductionScreenLayout.ART_HEIGHT;
        modalCancel.set(modal.x + 22f * modalScaleX,
                modal.y + 6f * modalScaleY, 55f * modalScaleX, 26f * modalScaleY);
        modalAccept.set(modal.x + 272f * modalScaleX,
                modal.y + 6f * modalScaleY, 56f * modalScaleX, 26f * modalScaleY);
        for (int i = 0; i < productionSliderHitTargets.length; i++) {
            ProductionScreenLayout.setSliderHitBounds(
                    i, modal, productionSliderHitTargets[i]);
        }
        for (int i = 0; i < technologyCards.length; i++) {
            TechnologyScreenLayout.setCardBounds(i, modal, technologyCards[i]);
        }
        TechnologyScreenLayout.setSelectedBounds(modal, technologySelectedArea);
        float researchRowHeight = Math.min(18f, (modal.height - 83f) / RESEARCH_ROWS_PER_PAGE);
        float researchTop = modal.y + modal.height - 50f;
        for (int i = 0; i < researchRows.length; i++) {
            researchRows[i].set(modal.x + 9f,
                    researchTop - (i + 1) * researchRowHeight,
                    modal.width - 18f, researchRowHeight - 1.5f);
        }
        researchPrevious.set(modal.x + modal.width * 0.36f, modal.y + modal.height - 49f,
                modal.width * 0.10f, 16f);
        researchNext.set(modal.x + modal.width * 0.54f, modal.y + modal.height - 49f,
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

        setupLayout.update(worldWidth, worldHeight, safeLeft, safeTop, safeRight, safeBottom);

        navigationScroll.setRange(ACTIONS.length * (NAV_ROW_HEIGHT + NAV_ROW_GAP)
                - NAV_ROW_GAP, navViewport.height);
        equipmentScroll.setRange(equipmentContentHeight(), equipmentViewport.height);
    }

    @Override
    public void render() {
        renderingFrame = true;
        if (pendingSafeInsets != appliedSafeInsets) {
            layout(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }
        if (flashAnimationAssets != null) {
            flashAnimationAssets.update();
            if (splashContinuePending && flashAnimationAssets.areSetupPortraitsLoaded()) {
                splashContinuePending = false;
                splashState.dismiss();
            }
            preparePendingBattleAnimations();
        }
        if (cityRenderer != null) {
            cityRenderer.updateTargets(game);
        }
        if (terrainRenderer != null && game != preparedTerrainGame) {
            // Match transitions upload selected tiles before beginning any draw batch.
            terrainRenderer.prepare(game);
            preparedTerrainGame = game;
        }
        synchronizeOutcomeSequence();
        if (endgameRenderer != null) endgameRenderer.update();
        boolean discardElapsedTime = skipNextFrameDelta || !continuousRenderingEnabled || settingsMenu.isOpen();
        skipNextFrameDelta = false;
        if (discardElapsedTime) {
            accumulator = 0f;
        }
        float frameSeconds = discardElapsedTime
                ? 0f : Math.min(Gdx.graphics.getDeltaTime(), 0.25f);
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
        if (game == null && splashState.isVisible()) {
            drawOriginalSplash();
        } else if (game == null) {
            drawSetupScreen();
            drawSettingsGear();
        } else if (MatchOutcomePresentation.isFinished(game.getMatchStatus())
                && outcomeSequence.isEndgameVisible()) {
            drawEndgameOverlay();
        } else {
            drawPanelsAndMap();
            drawTextAndControls();
            if (!isTurnInputBlocked() && helpState.isOpen()) {
                drawHelpScreen();
            } else if (!isTurnInputBlocked() && game.getPendingHumanDiplomacyProposal() != null) {
                drawDiplomacyResponseModal();
            } else if (!isTurnInputBlocked() && (modalOpen
                    || SourceActionMenuState.supports(game.getSelectedAction())
                    && sourceActionMenu.isMapSelection() && sourceActionMenu.isCancelVisible())) {
                drawActionModal();
            }
        }
        if (settingsMenu.isOpen()) drawSettingsMenu();
        if (showExitPromptSeconds > 0f) {
            drawExitPrompt();
        }
        setContinuousRendering(RenderLoopPolicy.shouldRenderContinuously(game != null,
                showExitPromptSeconds > 0f, hasActivePresentation()));
        renderingFrame = false;
    }

    private void drawHelpScreen() {
        batch.setProjectionMatrix(camera.combined);
        batch.setColor(Color.WHITE);
        font.setColor(Color.WHITE);
        if (helpState.isVisible()) {
            boolean hiddenCommand = helpState.isCommandVisible();
            boolean warning = helpState.getCommandMessage() != null;
            if (hiddenCommand) {
                hiddenHelpRenderer.prepare();
                prepareHiddenHelpText();
            } else helpRenderer.prepare(helpState.getTopic());
            batch.begin();
            if (hiddenCommand) {
                helpMapButton.set(0, 0, 0, 0);
                if (warning) hiddenHelpRenderer.drawWarning(batch, mapBounds);
                else hiddenHelpRenderer.draw(batch, mapBounds);
                drawHiddenHelpText(warning);
            } else {
                helpRenderer.draw(batch, mapBounds);
                helpMapButton.set(mapBounds.x + mapBounds.width - 52f,
                        mapBounds.y + mapBounds.height - 24f, 50f, 22f);
                drawOriginalButton(helpMapButton);
                drawCentered("MAP", helpMapButton.x + 25f, helpMapButton.y + 14f, 8f);
                // The secret password field is hit-testable but lies behind the
                // source orange header. Do not paint its glyphs over the flat art.
            }
            batch.end();
        } else {
            batch.begin();
            for (int i = 0; i < actionFooterButtons.length; i++) {
                Rectangle button = actionFooterButtons[i];
                drawOriginalButton(button);
                drawCentered(i == 0 ? "DETAILS" : i == 1 ? "CANCEL" : "ACCEPT", button.x + button.width / 2f,
                        button.y + button.height / 2f + 3f, 8f);
            }
            batch.end();
        }
    }

    /** The source dynamic text rasterizes these recovered fonts at four times stage size. */
    private static BitmapFont loadHiddenHelpFont(String stem) {
        String path = "original/ui/help/hidden-art/" + stem;
        BitmapFont.BitmapFontData data = new BitmapFont.BitmapFontData(Gdx.files.internal(path + ".fnt"), false);
        // Preserve the atlas's white RGB and coverage without a lossy Pixmap conversion.
        Texture texture = new Texture(Gdx.files.internal(path + ".png"), Pixmap.Format.RGBA8888, false);
        try {
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            BitmapFont result = new BitmapFont(data, new TextureRegion(texture), false);
            result.setOwnsTexture(true);
            result.setUseIntegerPositions(false);
            result.getData().setScale(.25f);
            return result;
        } catch (RuntimeException failure) {
            texture.dispose();
            throw failure;
        }
    }

    private void prepareHiddenHelpText() {
        if (hiddenHelpCommandFont == null) {
            hiddenHelpCommandFont = loadHiddenHelpFont("hidden-font-command-120");
            BitmapFont.Glyph bar = hiddenHelpCommandFont.getData().getGlyph('|');
            hiddenHelpCaretPixel = new TextureRegion(hiddenHelpCommandFont.getRegion().getTexture(),
                    bar.srcX + bar.width / 2, bar.srcY + bar.height / 2, 1, 1);
        }
        if (hiddenHelpWarningFont == null) hiddenHelpWarningFont = loadHiddenHelpFont("hidden-font-warning-40");
        String command = helpState.getInputText();
        if (!command.equals(hiddenHelpCachedCommand)) {
            SourceHiddenHelpTypography.layout(hiddenHelpCommandGlyph, hiddenHelpCommandFont,
                    command, hiddenHelpCommandColor);
            hiddenHelpCachedCommand = command;
        }
        String message = helpState.getCommandMessage();
        if (message != null && !message.equals(hiddenHelpCachedWarning)) {
            hiddenHelpWarningLines = 0;
            for (String paragraph : message.split("\n", -1)) {
                String line = "";
                for (String word : paragraph.split("\\s+")) {
                    if (word.isEmpty()) continue;
                    String candidate = line.isEmpty() ? word : line + " " + word;
                    if (!line.isEmpty() && SourceHiddenHelpTypography.advance(
                            hiddenHelpWarningFont.getData(), candidate) > 137f) {
                        cacheHiddenHelpWarningLine(line);
                        line = word;
                    } else line = candidate;
                }
                cacheHiddenHelpWarningLine(line);
            }
            hiddenHelpCachedWarning = message;
        }
    }

    private void cacheHiddenHelpWarningLine(String line) {
        if (hiddenHelpWarningLines < hiddenHelpWarningGlyphs.length) {
            SourceHiddenHelpTypography.layout(hiddenHelpWarningGlyphs[hiddenHelpWarningLines++],
                    hiddenHelpWarningFont, line, Color.WHITE);
        }
    }

    private void drawHiddenHelpText(boolean warning) {
        SourceHiddenHelpRenderer.fit(mapBounds, hiddenHelpFit);
        float scale = hiddenHelpFit.width / SourceHiddenHelpRenderer.SOURCE_WIDTH;
        hiddenHelpSavedTransform.set(batch.getTransformMatrix());
        hiddenHelpTextTransform.set(hiddenHelpSavedTransform)
                .translate(hiddenHelpFit.x, hiddenHelpFit.y + hiddenHelpFit.height, 0)
                .scale(scale, scale, 1);
        batch.setTransformMatrix(hiddenHelpTextTransform);
        try {
            if (warning) {
                // The authored static Warning header is already in frame7 artwork.
                SourceHiddenHelpRenderer.warningTextClipBounds(mapBounds, hiddenHelpClip);
                for (int i = 0; i < hiddenHelpWarningLines; i++) {
                    GlyphLayout glyph = hiddenHelpWarningGlyphs[i];
                    drawHiddenHelpGlyph(hiddenHelpWarningFont, glyph,
                            107.05f + SourceHiddenHelpTypography.centeredOffset(141f, glyph.width), -(95.05f + i * 12f));
                }
            } else {
                SourceHiddenHelpRenderer.commandTextClipBounds(mapBounds, hiddenHelpClip);
                drawHiddenHelpGlyph(hiddenHelpCommandFont, hiddenHelpCommandGlyph,
                        22.05f + SourceHiddenHelpTypography.centeredOffset(318f, hiddenHelpCommandGlyph.width), -160.05f);
                if (helpState.isInputFocused() && SourceHiddenHelpTypography.caretVisible(sourcePresentationTicks)) {
                    // Source's caret uses its left gutter even though the text is centered.
                    float prefix = 0;
                    BitmapFont.BitmapFontData data = hiddenHelpCommandFont.getData();
                    String text = helpState.getInputText();
                    for (int i = 0; i < helpState.getCaret(); i++) {
                        BitmapFont.Glyph glyph = data.getGlyph(text.charAt(i));
                        if (glyph != null) prefix += glyph.xadvance * data.scaleX;
                    }
                    float color = batch.getPackedColor();
                    beginHiddenHelpScissor();
                    try {
                        batch.setColor(hiddenHelpCommandColor);
                        // Pillow draws the inclusive end point of its 4x vertical line.
                        batch.draw(hiddenHelpCaretPixel, 22.05f + 2f + prefix - .25f, -191.3f, 1f, 32.25f);
                        batch.flush();
                    } finally {
                        batch.setPackedColor(color);
                        endHiddenHelpScissor();
                    }
                }
            }
        } finally { batch.setTransformMatrix(hiddenHelpSavedTransform); }
    }

    private boolean hiddenHelpPreviousScissor;
    private void beginHiddenHelpScissor() {
        batch.flush();
        hiddenHelpPreviousScissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        if (hiddenHelpPreviousScissor) {
            hiddenHelpScissorBox.clear();
            Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, hiddenHelpScissorBox);
        }
        setScissor(hiddenHelpClip);
    }
    private void endHiddenHelpScissor() {
        if (hiddenHelpPreviousScissor) Gdx.gl.glScissor(hiddenHelpScissorBox.get(0), hiddenHelpScissorBox.get(1),
                hiddenHelpScissorBox.get(2), hiddenHelpScissorBox.get(3));
        else Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }
    /** BitmapFont's cap anchor is corrected to the source line's actual visual top. */
    private void drawHiddenHelpGlyph(BitmapFont textFont, GlyphLayout glyph, float x, float visualTop) {
        beginHiddenHelpScissor();
        try {
            textFont.draw(batch, glyph, x, visualTop - SourceHiddenHelpTypography.topOffset(textFont.getData(), glyph));
            batch.flush();
        } finally { endHiddenHelpScissor(); }
    }

    private void blurHiddenHelpInput() {
        helpState.blurInput();
        if (hiddenHelpKeyboardVisible && Gdx.input != null)
            Gdx.input.setOnscreenKeyboardVisible(false);
        hiddenHelpKeyboardVisible = false;
    }

    private boolean activateHiddenHelp(float x, float y) {
        if (helpState.getCommandMessage() != null) {
            if (SourceHiddenHelpRenderer.warningOkBounds(mapBounds, hiddenHelpHit).contains(x, y)) {
                blurHiddenHelpInput();
                helpState.dismissCommandMessage();
            }
            return true;
        }
        if (helpState.hasInputField()) {
            if (helpState.isCommandVisible()) SourceHiddenHelpRenderer.commandBounds(mapBounds, hiddenHelpHit);
            else SourceHiddenHelpRenderer.passwordBounds(mapBounds, hiddenHelpHit);
            if (hiddenHelpHit.contains(x, y)) {
                if (helpState.focusInput()) {
                    Gdx.input.setOnscreenKeyboardVisible(true, Input.OnscreenKeyboardType.Default);
                    hiddenHelpKeyboardVisible = true;
                }
                return true;
            }
        }
        if (SourceHiddenHelpRenderer.buttonBounds(mapBounds, hiddenHelpHit).contains(x, y)) {
            if (helpState.isCommandVisible()) {
                String input = helpState.getInputText();
                blurHiddenHelpInput();
                SourceHiddenCommands.Submission submitted = game.getHiddenCommands().submit(input);
                if (submitted.recognized) audio.play(OriginalAudio.Cue.UNIT_SELECT);
                if (submitted.message != null) helpState.showCommandMessage(submitted.message);
                else closeHelpSelection();
                return true;
            }
            if ("help_index".equals(helpState.getTopic())) {
                blurHiddenHelpInput();
                helpState.activate("btnIndex_c");
                return true;
            }
        }
        if (helpState.isCommandVisible()) {
            blurHiddenHelpInput();
            return true;
        }
        blurHiddenHelpInput();
        return false;
    }

    private boolean hasActivePresentation() {
        // Settings pauses the battlefield clock. Input and asset uploads request redraws.
        if (game == null || settingsMenu.isOpen()) {
            return false;
        }
        if (outcomeSequence.isEndgameVisible()) {
            return outcomeSequence.isAnimating()
                    || endgameRenderer == null || !endgameRenderer.isReady();
        }
        // The source flag cloth loops at 24 Hz even between player actions.
        if (flagRenderer != null) return true;
        if (flashAnimationAssets != null && !flashAnimationAssets.isLoaded()) {
            return true;
        }
        if (helpState.isInputFocused() || confirmTurnAnimation.isAnimating() || turnActionDisplay.isAnimating()
                || actionSlotAnimation.isAnimating()
                || (cityRenderer != null && cityRenderer.isAnimating())
                || !battleAnimations.isEmpty()
                || isTurnInputBlocked()
                || hasAnimatingActionBuilding()
                || !soldierBattleAnimations.isEmpty()
                || !apacheMarineAnimations.isEmpty()
                || !chinookTankAnimations.isEmpty()
                || !suicideBomberAnimations.isEmpty()
                || !rocketBattleAnimations.isEmpty()
                || !submarineBattleAnimations.isEmpty()
                || !carrierPlaneAnimations.isEmpty()
                || !standalonePlaneAnimations.isEmpty()
                || !antiDefenseTrackingAnimations.isEmpty()
                || !satelliteBattleAnimations.isEmpty()
                || !pendingBattleAnimations.isEmpty()
                || outcomeSequence.isAnimating()) {
            return true;
        }
        return navigationScroll.hasMotion() || equipmentScroll.hasMotion();
    }

    private boolean hasAnimatingActionBuilding() {
        for (FlashActionBuildingAnimation animation : actionBuildingAnimations) {
            if (animation.isAnimating()) {
                return true;
            }
        }
        return false;
    }

    private void fixedUpdate(float seconds) {
        sourcePresentationTicks++;
        ReferenceRandom frameRandom = battlePresentationRandom;
        boolean sourceFrameReady = frameRandom == null
                || (flashAnimationAssets != null && flashAnimationAssets.isLoaded());
        if (frameRandom != null && sourceFrameReady) {
            if (hiddenBattleEffects.isActive()) hiddenBattleEffects.advanceTimelines();
            battleActionFrames.captureFrameOrder(battlefieldDisplay);
            if (cityRenderer != null) cityRenderer.captureSourceCityFrame(battlefieldDisplay);
        }
        if (game != null) sourceActionInstruction.tick();
        navigationScroll.tick(seconds);
        equipmentScroll.tick(seconds);
        if (game != null && flagRenderer != null) flagRenderer.tick();
        if (cityRenderer != null) {
            cityRenderer.tick(game);
        }
        showExitPromptSeconds = Math.max(0f, showExitPromptSeconds - seconds);
        if (sourceFrameReady) advanceBattleAnimations(seconds);
        if (game != null) {
            if (confirmTurnAnimation.tick(seconds)) confirmTurnAndPreparePlayback();
            synchronizeTurnControls();
            actionSlotAnimation.tick();
            turnActionDisplay.tick();
        }
        if (cityRenderer != null && sourceFrameReady && frameRandom == battlePresentationRandom)
            cityRenderer.tickSourceCities(game);
        if (frameRandom != null && sourceFrameReady && frameRandom == battlePresentationRandom) {
            battleActionFrames.tickCapturedFrame();
            if (battleInterfaceFrames.isRunning()) battleInterfaceFrames.tick(frameRandom);
            // MovieClipHost advances this shared child only after every Unit,
            // city and action-building callback on the global source frame.
            for (SourceAirDefenseController defense : sourceAirDefenseControllers)
                defense.advanceHostFrame();
            for (SourceRocketDefenseController defense : sourceRocketDefenseControllers)
                defense.advanceHostFrame();
            // Source interval callbacks dispatch after every retained clip's frame.
            if (hiddenBattleEffects.isActive()) hiddenBattleEffects.dispatchIntervals(hiddenBattleListener);
        }
        advanceOutcomeSequence();
    }

    private void advanceBattleAnimations(float seconds) {
        // Flash starts with its artwork available. Keep every presentation clock
        // together while the native asynchronous atlas finishes loading.
        if (turnPlaybackSequence.isBlockingInput()
                && (flashAnimationAssets == null || !flashAnimationAssets.isLoaded())) return;
        for (int index = 0; index < apacheMarineAnimations.size(); index++) {
            apacheMarineAnimations.get(index).advanceAttachedTimelines();
        }
        for (int index = 0; index < suicideBomberAnimations.size(); index++) {
            suicideBomberAnimations.get(index).advanceAttachedTimelines();
        }
        for (int index = 0; index < sourceRocketBindings.size(); index++)
            sourceRocketBindings.get(index).advanceAttachedTimelines();
        for (int index = 0; index < sourceInterceptedAircraftBindings.size(); index++)
            sourceInterceptedAircraftBindings.get(index).advanceAttachedTimelines();
        for (int index = 0; index < sourceCarrierPlaneBindings.size(); index++)
            sourceCarrierPlaneBindings.get(index).advanceAttachedTimelines();
        // MovieClipHost dispatches all onEnterFrame callbacks before autoplay
        // frame actions; fixedUpdate advances those after the city callbacks.
        TurnPlaybackSequence.Phase previousPhase = turnPlaybackSequence.getPhase();
        boolean waitingForLabels = turnPlaybackSequence.isWaitingForActionLabels();
        GameState.Action revealedAction = turnPlaybackSequence.advance(seconds);
        if (waitingForLabels && !turnPlaybackSequence.isWaitingForActionLabels()) {
            audio.finishBattleAmbient();
        }
        if (revealedAction != null) {
            turnActionDisplay.reveal(revealedAction, game.getLastTurnActionBuildings());
            int mediaVariant = battleActionFrames.onActionRevealed(
                    revealedAction, audio.isSoundEnabled());
            Arrays.fill(actionSoundSlotFrames, null);
            for (FlashActionBuildingAnimation building : actionBuildingAnimations) {
                // Keep the occupied physical slot's authored label, not a later
                // combat frame override or an overwritten all-turn record.
                actionSoundSlotFrames[building.playerId * 2 + building.slot - 1] = building.frameName;
            }
            audio.playActionReveal(revealedAction, actionSoundSlotFrames, mediaVariant);
        } else if (!turnPlaybackSequence.isWaitingForActionLabels()) {
            turnActionDisplay.hide();
        }
        if (revealedAction == GameState.Action.PROPAGANDA && cityRenderer != null) {
            cityRenderer.playPropagandaEvents(game, game.getLastTurnPropagandaEvents());
        }
        if (previousPhase == TurnPlaybackSequence.Phase.CLEANUP_HANDOFF
                && turnPlaybackSequence.getPhase() == TurnPlaybackSequence.Phase.IDLE) {
            finishBattlePresentation();
            turnActionDisplay.hide();
            if (cityRenderer != null) cityRenderer.resetPropagandaPlayback();
        }
        if (previousPhase == TurnPlaybackSequence.Phase.RESET_BARRIER
                && turnPlaybackSequence.getPhase() == TurnPlaybackSequence.Phase.CLEANUP_HANDOFF) {
            // Presenter.allUnitsDone detaches stopped clips before endBattle.
            sourceStoppedClips.removeAtAllUnitsDone();
            if (cityRenderer != null) cityRenderer.removeSourceDustAtAllUnitsDone();
        }
        SourceBattleTicker.FrameDecision tickerDecision = !battleTicker.isRunning()
                && hiddenBattleEffects.suppressesNormalTickerCompletion()
                ? SourceBattleTicker.FrameDecision.SUPPRESS : SourceBattleTicker.FrameDecision.ADVANCE;
        if (battleTicker.isRunning()) {
            // f_37 checks live units before movement, even during action labels.
            tickerDecision = battleTicker.advanceFrame(hasSourceBattleEntries(),
                    hiddenBattleEffects.suppressesNormalTickerCompletion());
            if (tickerDecision == SourceBattleTicker.FrameDecision.CLEANUP) {
                // advance() already ran: this newly created timer starts next frame.
                turnPlaybackSequence.completeBattlePlayback();
            }
        }
        if (flashAnimationAssets == null || !flashAnimationAssets.isLoaded()) {
            return;
        }
        for (FlashActionBuildingAnimation animation : actionBuildingAnimations) {
            animation.tick();
            animation.revealAction(revealedAction);
        }
        // Before labels finish, View still seeks waiting boats. Once unit
        // visitation opens, each cohort owns its single depth callback; an
        // extra pre-movement swap could displace an earlier unit's occupant.
        if (battleTicker.isRunning()
                && (turnPlaybackSequence.isWaitingForActionLabels()
                || previousPhase == TurnPlaybackSequence.Phase.ACTION_LABELS)) {
            for (int index = 0; index < soldierBattleAnimations.size(); index++) {
                soldierBattleAnimations.get(index).presentWaitingFrame();
            }
            for (int index = 0; index < chinookTankAnimations.size(); index++) {
                chinookTankAnimations.get(index).presentWaitingFrame();
            }
            for (int index = 0; index < suicideBomberAnimations.size(); index++) {
                suicideBomberAnimations.get(index).presentWaitingFrame();
            }
        }
        for (int index = 0; index < satelliteBattleAnimations.size(); index++) {
            satelliteBattleAnimations.get(index).presentWaitingFrame();
        }
        for (int index = 0; index < apacheMarineAnimations.size(); index++) {
            apacheMarineAnimations.get(index).presentWaitingFrame();
        }
        if (turnPlaybackSequence.isWaitingForActionLabels()
                || previousPhase == TurnPlaybackSequence.Phase.ACTION_LABELS) {
            // Unvisited plane clips still autoplay; waiting ship clips still
            // receive View's idle seek before their battle record activates.
            for (int index = 0; index < standalonePlaneAnimations.size(); index++) {
                FlashStandalonePlaneAnimation plane = standalonePlaneAnimations.get(index);
                if (plane.getSourcePlayback() != null) plane.tickSourceFrame(false);
            }
            for (int index = 0; index < carrierPlaneAnimations.size(); index++) {
                FlashCarrierPlaneAnimation plane = carrierPlaneAnimations.get(index);
                if (plane.getSourcePlayback() != null) plane.tickSourceFrame(false);
            }
            for (FlashRocketAnimation rocket : rocketBattleAnimations) {
                if (rocket.getSourcePlayback() != null
                        && rocket.getSourcePlayback().isOrdinary())
                    rocket.visitWaitingSourceOrdinary();
            }
        }
        if (turnPlaybackSequence.isWaitingForActionLabels()) return;
        for (int i = battleAnimations.size() - 1; i >= 0; i--) {
            BattleAnimationSequence animation = battleAnimations.get(i);
            if (animation.tick() && animation.audioCue != null) {
                audio.play(animation.audioCue);
            }
            if (animation.isFinished()) {
                battleAnimations.remove(i);
            }
        }
        // Explosion clips autoplay independently after the source unit ticker stops.
        if (turnPlaybackSequence.isCleaningUp()
                || tickerDecision == SourceBattleTicker.FrameDecision.SUPPRESS
                || turnPlaybackSequence.isWaitingForActionLabels()
                || previousPhase == TurnPlaybackSequence.Phase.ACTION_LABELS) {
            return;
        }
        if (turnPlaybackSequence.isReadyForBattle()) battleRecordClock.tick();
        for (int i = submarineBattleAnimations.size() - 1; i >= 0; i--) {
            FlashSubmarineAnimation animation = submarineBattleAnimations.get(i);
            if (!turnPlaybackSequence.isReadyForBattle()) continue;
            animation.tick();
            if (animation.takeSurfaceCue()) {
                audio.play(OriginalAudio.Cue.SUBMARINE_SURFACE,
                        animation.getSurfaceSoundPan());
            }
            if (animation.isFinished()) {
                submarineBattleAnimations.remove(i);
            }
        }
        sourceUnitPlayback.advanceFrame();
    }

    private boolean advanceRocketUnit(FlashRocketAnimation animation,
                                       GameState.CombatEvent event,
                                       FlashSubmarineAnimation submarine,
                                       SourceRocketDisplayDepths sourceDepths) {
        if (animation.isFinished()) return false;
        if (animation.getSourcePlayback() != null
                && (!turnPlaybackSequence.isReadyForBattle()
                    || event.battleStartNum > battleRecordClock.getActivatedRecordCount())) {
            if (animation.getSourcePlayback().isOrdinary())
                animation.visitWaitingSourceOrdinary();
            return false;
        }
        if (submarine != null && animation.getSourcePlayback() == null
                && !turnPlaybackSequence.isReadyForBattle()) return false;
        boolean source = animation.getSourcePlayback() != null;
        float sourcePan = source && animation.getSourcePlayback().isOrdinary()
                ? (float) (Math.floor(((animation.getSourcePlayback().getRenderX() / 8d
                        - 50d) * 2d) + 0.5d) / 100d)
                : sourceAudioPan(animation.getRocket().sourceX);
        boolean impact = source ? animation.tickSourceFrame(submarine != null
                && submarine.getSourceFrameIndex() > 0) : animation.tick();
        if (sourceDepths != null) sourceDepths.afterUnitFrame();
        if (animation.takeLaunchSound()) {
            // Unit.sub_rocket_launch starts its owned sound in this callback.
            if (source) animation.setSourceLaunchSoundId(
                    audio.playInstance(animation.getLaunchCue(),
                            animation.getSourcePlayback().isOrdinary() ? sourcePan : 0f));
            else audio.play(animation.getLaunchCue());
        }
        if (impact) {
            applyProjectileCityImpact(event, !source || !animation.getSourcePlayback().isSelfImpact());
            if (source) {
                audio.stop(animation.getLaunchCue(), animation.getSourceLaunchSoundId());
                animation.setSourceLaunchSoundId(-1L);
                if (animation.getImpactCue() != null)
                    audio.play(animation.getImpactCue(), sourcePan);
            } else {
                FlashAnimationTimeline timeline = flashAnimationAssets.getTimeline(
                        animation.getImpactLinkage());
                if (timeline != null) {
                    FlashBattleRoutes.RocketStrike route = animation.getRoute();
                    battleAnimations.add(new BattleAnimationSequence(timeline,
                            route.targetPlayerId, route.targetCityIndex, 0,
                            animation.getImpactCue(), animation.getImpactSourceX(),
                            animation.getImpactSourceY()));
                } else if (animation.getImpactCue() != null) {
                    audio.play(animation.getImpactCue());
                }
            }
        }
        if (source && animation.getSourcePlayback().isOrdinary())
            audio.setInstancePan(animation.getLaunchCue(),
                    animation.getSourceLaunchSoundId(), sourcePan);
        if (animation.isFinished()) {
            rocketBattleAnimations.remove(animation);
        }
        return false;
    }

    private boolean advanceCarrierPlaneUnit(FlashCarrierPlaneAnimation animation,
                                             GameState.CombatEvent event,
                                             SourceCarrierPlaneDisplayDepths depths) {
        if (animation.isFinished()) return false;
        boolean impact = animation.getSourcePlayback() == null ? animation.tick()
                : animation.tickSourceFrame(turnPlaybackSequence.isReadyForBattle()
                    && event.battleStartNum <= battleRecordClock.getActivatedRecordCount());
        updateAircraftTrailDisplay(animation.getSourceTrail());
        if (depths != null) depths.afterUnitFrame(impact);
        AircraftAudioPlayback.Owner voice = aircraftAudioOwners.get(animation);
        if (voice != null) {
            if (animation.takeTakeoffCue()) voice.requestTakeoff();
            if (animation.getSourcePlayback() != null) {
                voice.setSourceVolumePercent(animation.getSourcePlayback()
                        .getSourceSoundVolumePercent());
                voice.setSourcePanPercent(animation.getSourcePlayback()
                        .getSourceSoundPanPercent());
                if (animation.getSourcePlayback().takeSoundStop()) voice.stop();
            }
        }
        if (impact) {
            applyProjectileCityImpact(event);
            if (depths != null) {
                // killUnit(default) has no explosion sound; a successful payload
                // uses its original cue while its effect paints in root depth order.
                if (!animation.getRoute().intercepted && animation.getImpactCue() != null)
                    audio.play(animation.getImpactCue());
            } else {
                FlashAnimationTimeline timeline = flashAnimationAssets.getTimeline(
                        animation.getImpactLinkage());
                if (timeline != null) {
                    FlashBattleRoutes.CarrierPlaneStrike route = animation.getRoute();
                    FlashBattleRoutes.Point impactPoint =
                            route.waypoints[route.waypoints.length - 1];
                    battleAnimations.add(new BattleAnimationSequence(timeline,
                            route.targetPlayerId, route.targetCityIndex, 0,
                            animation.getImpactCue(), impactPoint.x, impactPoint.y));
                } else if (animation.getImpactCue() != null) {
                    audio.play(animation.getImpactCue());
                }
            }
        }
        if (animation.isFinished()) {
            stopAircraftAudio(animation);
            carrierPlaneAnimations.remove(animation);
        }
        return false;
    }

    private boolean advanceStandalonePlaneUnit(FlashStandalonePlaneAnimation animation,
                                                GameState.CombatEvent event,
                                                SourceInterceptedAircraftDisplayDepths depths) {
        if (animation.isFinished()) return false;
        boolean impact = animation.getSourcePlayback() == null ? animation.tick()
                : animation.tickSourceFrame(turnPlaybackSequence.isReadyForBattle()
                    && event.battleStartNum <= battleRecordClock.getActivatedRecordCount());
        // Unit.bomb attaches its payload before removing the smoke peers;
        // their occupied depths determine the new effect's physical depth.
        if (impact && depths != null) depths.attachPayloadImpact();
        updateAircraftTrailDisplay(animation.getSourceTrail());
        if (depths != null) depths.afterUnitFrame();
        AircraftAudioPlayback.Owner voice = aircraftAudioOwners.get(animation);
        if (voice != null) {
            if (animation.takeTakeoffCue()) voice.requestTakeoff();
            if (animation.getSourcePlayback() != null) {
                voice.setSourceVolumePercent(animation.getSourcePlayback()
                        .getSourceSoundVolumePercent());
                voice.setSourcePanPercent(animation.getSourcePlayback()
                        .getSourceSoundPanPercent());
                if (animation.getSourcePlayback().takeSoundStop()) voice.stop();
            }
        }
        if (animation.getSourcePlayback() != null
                && animation.getSourcePlayback().takeDeath()) {
            // Unit.speedup_die updates the city even with zero casualties, then
            // killUnit captures an effect at logical XY while its cue uses old MC X.
            applyProjectileCityImpact(event, false);
            audio.play(OriginalAudio.Cue.EXPLOSION_SMALL3,
                    sourceAudioPan((float) animation.getSourcePlayback().getDeathSoundX()));
        }
        if (impact) {
            applyProjectileCityImpact(event);
            if (depths != null) {
                if (animation.getImpactCue() != null)
                    audio.play(animation.getImpactCue(), sourceAudioPan(
                            (float) animation.getSourcePlayback().getImpactSoundX()));
            } else {
                FlashAnimationTimeline timeline = flashAnimationAssets.getTimeline(
                        animation.getImpactLinkage());
                FlashBattleRoutes.StandalonePlaneStrike route = animation.getRoute();
                if (timeline != null) {
                    battleAnimations.add(new BattleAnimationSequence(timeline,
                            route.targetPlayerId, route.targetCityIndex, 0,
                            animation.getImpactCue(), route.target.x, route.target.y));
                } else if (animation.getImpactCue() != null) {
                    audio.play(animation.getImpactCue());
                }
            }
        }
        if (animation.isFinished()) {
            stopAircraftAudio(animation);
            standalonePlaneAnimations.remove(animation);
        }
        return false;
    }

    private void updateAircraftTrailDisplay(SourceAircraftTrail trail) {
        SourceAircraftTrailDisplay display = sourceAircraftTrails.get(trail);
        if (display != null) display.afterUnitFrame();
    }

    private void applyProjectileCityImpact(GameState.CombatEvent event) {
        applyProjectileCityImpact(event, true);
    }

    private void applyProjectileCityImpact(GameState.CombatEvent event, boolean biologicalCallback) {
        int address = event.defenderPlayerId * BattlefieldCityLayout.CITIES_PER_PLAYER
                + event.cityIndex;
        cityRenderer.applySourceProjectileImpact(game, address, event.casualtiesMillions,
                biologicalCallback && event.bioload
                        && event.type == GameState.CombatEventType.STRIKE_IMPACT,
                battlePresentationRandom);
    }

    private boolean advanceAntiDefenseUnit(FlashAntiDefenseTrackingAnimation animation) {
        if (animation.isFinished()) return false;
        animation.tick();
        if (animation.takeRotationCue()) {
            audio.play(OriginalAudio.Cue.ANTI_AIR_ROTATE,
                    sourceAudioPan(animation.getBuildingPosition().x));
        }
        if (animation.takeGunCue()) {
            audio.play(animation.getGunCue(),
                    sourceAudioPan(animation.getBuildingPosition().x));
        }
        if (animation.isFinished()) {
            antiDefenseTrackingAnimations.remove(animation);
        }
        return false;
    }

    private boolean advanceSatelliteUnit(FlashSatelliteAnimation animation) {
        if (animation.isFinished()) return false;
        animation.tick();
        animation.registerStoppedClip(sourceStoppedClips);
        if (animation.takeFlightCue() && !satelliteFlightCuePlayed) {
            audio.play(OriginalAudio.Cue.SATELLITE_FLIGHT);
            satelliteFlightCuePlayed = true;
        }
        if (animation.takeImpact()) {
            // Unit.satellite_explosion attaches explosion_warhead_4 in Flash.
            FlashAnimationTimeline impact = flashAnimationAssets.getTimeline(
                    "explosion_warhead_4");
            if (impact != null) {
                FlashBattleRoutes.SatelliteStrike route = animation.getRoute();
                battleAnimations.add(new BattleAnimationSequence(impact,
                        route.targetPlayerId, route.targetCityIndex, 0, null));
            }
        }
        SatelliteAudioPlayback.Owner voice = satelliteAudioOwners.get(animation);
        if (animation.takeLaserCue() && voice != null) voice.playLaser();
        // Source creates the laser at centered pan, then moveUnit updates it in
        // the same visit. The stop-frame break skips that update; removal's final
        // visit still runs it. Keep the owner alive until the battle handoff.
        if (voice != null && animation.hasFiredLaserSound() && !animation.didStopSourceFrame())
            voice.setPan(animation.getSourceSoundPan());
        // Stopped satellite clips remain until Presenter.allUnitsDone.
        if (animation.didStopSourceFrame()) return true;
        return false;
    }

    private boolean advanceInfantryUnit(FlashSoldierOneAnimation animation) {
        if (animation.isFinished()) return false;
        boolean removedInfantry = animation.tickSourceFrame();
        animation.registerStoppedClips(sourceStoppedClips);
        while (animation.takeAudioCue()) {
            audio.play(animation.getAudioCue());
        }
        // Removed wrappers keep their final clips until allUnitsDone.
        if (removedInfantry) return true;
        return false;
    }

    private boolean advanceMarineUnit(FlashApacheMarineAnimation animation) {
        if (animation.isFinished()) return false;
        boolean removedMarine = animation.tickSourceFrame();
        animation.registerStoppedClips(sourceStoppedClips);
        while (animation.takeAudioCue()) {
            audio.play(animation.getAudioCue());
        }
        // The wrapper leaves the active source array before its clip is
        // detached by allUnitsDone. A marine removal ends this unit frame.
        return removedMarine;
    }

    private boolean advanceTankUnit(FlashChinookTankAnimation animation,
                                    GameState.CombatEvent event) {
        if (animation.isFinished()) return false;
        animation.tick();
        if (animation.takeCityImpact()) {
            int address = event.defenderPlayerId * BattlefieldCityLayout.CITIES_PER_PLAYER
                    + event.cityIndex;
            cityRenderer.applySourceTankImpact(address, event.casualtiesMillions,
                    battlePresentationRandom);
        }
        animation.registerStoppedClips(sourceStoppedClips);
        while (animation.takeAudioCue()) {
            audio.play(animation.getAudioCue());
        }
        return false;
    }

    private boolean advanceSuicideUnit(FlashSuicideBomberAnimation animation,
                                       GameState.CombatEvent event) {
        if (animation.isFinished()) return false;
        animation.tickSourceFrame();
        if (animation.takeCityImpact()) {
            int address = event.defenderPlayerId * BattlefieldCityLayout.CITIES_PER_PLAYER
                    + event.cityIndex;
            cityRenderer.applySourceTankImpact(address, event.casualtiesMillions,
                    battlePresentationRandom);
        }
        animation.registerStoppedClips(sourceStoppedClips);
        while (animation.takeAudioCue()) {
            OriginalAudio.Cue cue = animation.getAudioCue();
            if (cue == OriginalAudio.Cue.SUICIDE_EXPLODE) {
                audio.play(cue, sourceAudioPan(animation.getActor(2).renderX));
            } else {
                audio.play(cue);
            }
        }
        // View continues to later wrappers after suicide removal. Its early
        // return applies only to marines and ordinary soldiers.
        return false;
    }

    /** Only source unit/building wrappers and queued subs gate f_37 completion. */
    private boolean hasSourceBattleEntries() {
        for (int i = 0; i < battlePresentationRecords.size(); i++) {
            GameState.BattlePresentationRecord record = battlePresentationRecords.get(i);
            if (record.pendingBuilding
                    && record.startNum > battleRecordClock.getActivatedRecordCount()) return true;
        }
        boolean hasActiveInfantry = false;
        for (int index = 0; index < soldierBattleAnimations.size(); index++) {
            if (!soldierBattleAnimations.get(index).isFinished()) {
                hasActiveInfantry = true;
                break;
            }
        }
        boolean hasActiveSatellite = false;
        for (int index = 0; index < satelliteBattleAnimations.size(); index++) {
            if (!satelliteBattleAnimations.get(index).isFinished()) {
                hasActiveSatellite = true;
                break;
            }
        }
        boolean hasActiveTank = false;
        for (int index = 0; index < chinookTankAnimations.size(); index++) {
            if (!chinookTankAnimations.get(index).isFinished()) {
                hasActiveTank = true;
                break;
            }
        }
        boolean hasActiveMarine = false;
        for (int index = 0; index < apacheMarineAnimations.size(); index++) {
            if (!apacheMarineAnimations.get(index).isFinished()) {
                hasActiveMarine = true;
                break;
            }
        }
        boolean hasActiveSuicide = false;
        for (int index = 0; index < suicideBomberAnimations.size(); index++) {
            if (!suicideBomberAnimations.get(index).isFinished()) {
                hasActiveSuicide = true;
                break;
            }
        }
        return hasActiveInfantry || hasActiveSatellite || hasActiveTank || hasActiveMarine
                || hasActiveSuicide
                || !rocketBattleAnimations.isEmpty()
                || !submarineBattleAnimations.isEmpty()
                || !carrierPlaneAnimations.isEmpty()
                || !standalonePlaneAnimations.isEmpty();
    }

    /** The phone adapter releases playback at the endBattle handoff. */
    private void finishBattlePresentation() {
        hiddenBattleEffects.finish();
        audio.finishBattleAmbient();
        audio.finishBattleActionSounds();
        audio.finishSatelliteAudio();
        audio.finishAircraftAudio();
        audio.finishAntiDefenseAudio();
        battleRecordClock.finishBattle();
        battleInterfaceFrames.reset();
        sourceStoppedClips.reset();
        battlePresentationRandom = null;
        battleAnimations.clear();
        sourceUnitPlayback.reset();
        soldierBattleAnimations.clear();
        apacheMarineAnimations.clear();
        chinookTankAnimations.clear();
        suicideBomberAnimations.clear();
        clearRocketBattleAnimations();
        sourceRocketBindings.clear();
        sourceInterceptedAircraftBindings.clear();
        sourceAircraftTrails.clear();
        submarineBattleAnimations.clear();
        clearCarrierPlaneAnimations();
        clearStandalonePlaneAnimations();
        antiDefenseTrackingAnimations.clear();
        clearSourceAirDefenses();
        satelliteBattleAnimations.clear();
        satelliteAudioOwners.clear();
        satelliteFlightCuePlayed = false;
        pendingBattleAnimations.clear();
        battlePresentationRecords = Collections.emptyList();
        actionBuildingAnimations.clear();
        battleActionFrames.reset();
        cityRenderer.endBattle();
        flagRenderer.endBattle();
        resetBattlefieldDisplay(false);
    }

    private void clearRocketBattleAnimations() {
        for (FlashRocketAnimation animation : rocketBattleAnimations)
            audio.stop(animation.getLaunchCue(), animation.getSourceLaunchSoundId());
        rocketBattleAnimations.clear();
    }

    private void clearCarrierPlaneAnimations() {
        for (FlashCarrierPlaneAnimation animation : carrierPlaneAnimations) {
            stopAircraftAudio(animation);
        }
        carrierPlaneAnimations.clear();
        sourceCarrierPlaneBindings.clear();
    }

    private void clearStandalonePlaneAnimations() {
        for (FlashStandalonePlaneAnimation animation : standalonePlaneAnimations) {
            stopAircraftAudio(animation);
        }
        standalonePlaneAnimations.clear();
    }

    private void stopAircraftAudio(Object animation) {
        AircraftAudioPlayback.Owner voice = aircraftAudioOwners.remove(animation);
        if (voice != null) voice.stop();
    }

    private FlashAntiDefenseTrackingAnimation addAntiDefenseTrackingAnimation(GameState.CombatEvent event,
                                                 FlashAntiDefenseTrackingAnimation.Target target,
                                                 FlashAntiDefenseTrackingAnimation.TargetLock targetLock) {
        FlashAntiDefenseTrackingAnimation.DefenseType defenseType;
        if ("antiAir".equals(event.counterWeaponName)
                && (target instanceof FlashCarrierPlaneAnimation
                || target instanceof FlashStandalonePlaneAnimation)) {
            defenseType = FlashAntiDefenseTrackingAnimation.DefenseType.AIR;
        } else if ("antiRocket".equals(event.counterWeaponName)
                && target instanceof FlashRocketAnimation
                && !((FlashRocketAnimation) target).getRoute().submarineLaunch) {
            defenseType = FlashAntiDefenseTrackingAnimation.DefenseType.ROCKET;
        } else {
            return null;
        }
        if (event.counterActionBuildingSlot < 1
                || event.counterActionBuildingSlot > 2
                || !targetLock.tryLock(event.defenderPlayerId)) {
            return null;
        }
        GameState.PlayerState defender = game.getPlayerState(event.defenderPlayerId);
        FlashAntiDefenseTrackingAnimation animation = new FlashAntiDefenseTrackingAnimation(target,
                event.defenderPlayerId, event.counterActionBuildingSlot,
                defender.getReligionCode(), defenseType);
        antiDefenseTrackingAnimations.add(animation);
        return animation;
    }

    /** One source turret belongs to the defender's actual anti-air building. */
    private SourceAirDefenseController airDefenseFor(GameState.CombatEvent event,
            FlashAntiDefenseTrackingAnimation.TargetLock sharedTargetLock) {
        if (!"antiAir".equals(event.counterWeaponName)) return null;
        for (SourceAirDefenseController controller : sourceAirDefenseControllers) {
            if (controller.getDefenderPlayerId() != event.defenderPlayerId) continue;
            if (controller.getActionBuildingSlot() != event.counterActionBuildingSlot)
                throw new IllegalStateException("Source defender has two anti-air locations");
            return controller;
        }
        FlashActionBuildingAnimation building = null;
        for (FlashActionBuildingAnimation candidate : actionBuildingAnimations) {
            if (candidate.playerId == event.defenderPlayerId
                    && candidate.slot == event.counterActionBuildingSlot) {
                building = candidate;
                break;
            }
        }
        SourceAirDefenseController controller = new SourceAirDefenseController(
                event.defenderPlayerId, event.counterActionBuildingSlot,
                game.getPlayerState(event.defenderPlayerId).getReligionCode(), building,
                sharedTargetLock);
        sourceAirDefenseControllers.add(controller);
        return controller;
    }

    private void synchronizeSourceAirDefenseTracker(SourceAirDefenseController controller) {
        if (!controller.takeTrackerChange()) return;
        for (int index = antiDefenseTrackingAnimations.size() - 1; index >= 0; index--) {
            FlashAntiDefenseTrackingAnimation previous = antiDefenseTrackingAnimations.get(index);
            if (previous.isSourceDriven()
                    && previous.getDefenderPlayerId() == controller.getDefenderPlayerId())
                antiDefenseTrackingAnimations.remove(index);
        }
        FlashAntiDefenseTrackingAnimation current = controller.getTracker();
        if (current != null) antiDefenseTrackingAnimations.add(current);
    }

    private SourceRocketDefenseController rocketDefenseFor(GameState.CombatEvent event,
            FlashAntiDefenseTrackingAnimation.TargetLock sharedTargetLock) {
        if (!"antiRocket".equals(event.counterWeaponName)) return null;
        for (SourceRocketDefenseController controller : sourceRocketDefenseControllers) {
            if (controller.getDefenderPlayerId() != event.defenderPlayerId) continue;
            if (controller.getActionBuildingSlot() != event.counterActionBuildingSlot)
                throw new IllegalStateException("Source defender has two anti-rocket locations");
            return controller;
        }
        FlashActionBuildingAnimation building = null;
        for (FlashActionBuildingAnimation candidate : actionBuildingAnimations) {
            if (candidate.playerId == event.defenderPlayerId
                    && candidate.slot == event.counterActionBuildingSlot) {
                building = candidate;
                break;
            }
        }
        SourceRocketDefenseController controller = new SourceRocketDefenseController(
                event.defenderPlayerId, event.counterActionBuildingSlot,
                game.getPlayerState(event.defenderPlayerId).getReligionCode(), building,
                sharedTargetLock);
        sourceRocketDefenseControllers.add(controller);
        return controller;
    }

    private void synchronizeSourceRocketDefenseTracker(SourceRocketDefenseController controller) {
        if (!controller.takeTrackerChange()) return;
        for (int index = antiDefenseTrackingAnimations.size() - 1; index >= 0; index--) {
            FlashAntiDefenseTrackingAnimation previous = antiDefenseTrackingAnimations.get(index);
            if (previous.isSourceDriven()
                    && previous.getDefenderPlayerId() == controller.getDefenderPlayerId())
                antiDefenseTrackingAnimations.remove(index);
        }
        FlashAntiDefenseTrackingAnimation current = controller.getTracker();
        if (current != null) antiDefenseTrackingAnimations.add(current);
    }

    private void clearSourceAirDefenses() {
        for (SourceAirDefenseController controller : sourceAirDefenseControllers)
            controller.clear();
        sourceAirDefenseControllers.clear();
        for (SourceRocketDefenseController controller : sourceRocketDefenseControllers)
            controller.clear();
        sourceRocketDefenseControllers.clear();
    }

    private static float sourceAudioPan(float sourceX) {
        return Math.max(-1f, Math.min(1f, (sourceX - 400f) / 400f));
    }

    private void preparePendingBattleAnimations() {
        for (int i = 0; i < pendingBattleAnimations.size();) {
            PendingBattleAnimation pending = pendingBattleAnimations.get(i);
            String linkage = SourceCombatAnimationMapping.linkageFor(pending.event);
            FlashAnimationTimeline timeline = linkage == null ? null
                    : flashAnimationAssets.getTimeline(linkage);
            if (timeline != null) {
                battleAnimations.add(new BattleAnimationSequence(timeline,
                        pending.event.defenderPlayerId, pending.event.cityIndex,
                        pending.delayTicks,
                        OriginalAudio.cueForCombatEvent(pending.event)));
                pendingBattleAnimations.remove(i);
            } else if (flashAnimationAssets.isLoaded()) {
                OriginalAudio.Cue cue = OriginalAudio.cueForCombatEvent(pending.event);
                if (cue != null) {
                    audio.play(cue);
                }
                pendingBattleAnimations.remove(i);
            } else {
                i++;
            }
        }
    }

    private void queueActionBuildingAnimations() {
        List<GameState.Action> resolvedActions = new ArrayList<>(
                game.getLastTurnActionBuildings().size());
        for (GameState.ActionBuildingEvent event : game.getLastTurnActionBuildings()) {
            GameState.Action label = SourceActionLabelMapping.actionForFrame(event.frameName);
            if (label != null) resolvedActions.add(label);
            FlashActionBuildingAnimation reused = null;
            for (int index = actionBuildingAnimations.size() - 1; index >= 0; index--) {
                FlashActionBuildingAnimation previous = actionBuildingAnimations.get(index);
                if (previous.playerId == event.playerId && previous.slot == event.slot) {
                    reused = previous;
                    actionBuildingAnimations.remove(index);
                }
            }
            actionBuildingAnimations.add(new FlashActionBuildingAnimation(event, reused,
                    game.getHiddenCommands().isActive(SourceHiddenCommands.Flag.DISCO)));
            audio.startBuildingAmbient(event.frameName);
        }
        turnActionDisplay.reset();
        turnPlaybackSequence.begin(resolvedActions);
        battlefieldDisplay.bindActionBuildings(actionBuildingAnimations);
    }

    private void advanceOutcomeSequence() {
        boolean alreadyAnimating = outcomeSequence.isAnimating();
        synchronizeOutcomeSequence();
        // A timer created at handoff cannot consume that same source frame.
        if (alreadyAnimating && outcomeSequence.isAnimating()) {
            playOutcomeEvents(outcomeSequence.tick());
        }
    }

    private void synchronizeOutcomeSequence() {
        if (game == null || !MatchOutcomePresentation.isFinished(game.getMatchStatus())) {
            if (outcomeSequence.getPhase() != MatchOutcomeSequence.Phase.INACTIVE) {
                stopScoreCounterSound();
                outcomeSequence.reset();
                disposeEndgameResources();
            }
            outcomePersonalBestText = "";
            return;
        }

        // Flash checks victory or defeat from initInterface_1 after endBattle.
        // Keep the turn's action labels, battle, and reset barrier visible first.
        if (confirmTurnAnimation.isPending() || turnPlaybackSequence.isBlockingInput()) {
            return;
        }

        if (outcomeSequence.getOutcome() != game.getMatchStatus()) {
            stopScoreCounterSound();
            if (endgameAssets == null) {
                endgameAssets = new OriginalEndgameAssets();
                endgameRenderer = new SourceEndgameRenderer(endgameAssets);
                endgameRenderer.layout().fit(endgameContentBounds.x, endgameContentBounds.y,
                        endgameContentBounds.width, endgameContentBounds.height);
                endgameRenderer.start();
            }
            if (game.getHiddenCommands().isScoreCorrupt()) {
                // Lifecycle's isolated endgame displays zero; canonical score stays intact.
                outcomePersonalBestText = "";
            } else if (personalBestStore != null) {
                try {
                    personalBestStore.recordFinalScore(game.getFinalScore());
                    outcomePersonalBestText = MatchOutcomePresentation.personalBestLine(
                            personalBestStore.getBestScore());
                } catch (ScorePersistenceException error) {
                    Gdx.app.error("Personal best", "Completed match score could not be saved", error);
                    outcomePersonalBestText = "PERSONAL BEST NOT SAVED";
                }
            } else {
                outcomePersonalBestText = MatchOutcomePresentation.personalBestLine(
                        game.getFinalScore());
            }
            outcomeSequence.begin(game.getMatchStatus(), game.getFinalScoreBreakdown(),
                    game.createOutcomePresentationRandomCopy(), game.getHiddenCommands().isScoreCorrupt());
            modalOpen = false;
            audio.finishBattleAmbient();
            audio.finishBattleActionSounds();
        }
    }

    private void playOutcomeEvents(int events) {
        if ((events & MatchOutcomeSequence.EVENT_DEFEAT) != 0) {
            audio.play(OriginalAudio.Cue.DEFEAT);
        }
        if ((events & MatchOutcomeSequence.EVENT_START_SCORE_COUNTER) != 0) {
            stopScoreCounterSound();
            scoreCounterSoundId = audio.loop(OriginalAudio.Cue.SCORE_COUNTER);
        }
        if ((events & MatchOutcomeSequence.EVENT_STOP_SCORE_COUNTER) != 0) {
            stopScoreCounterSound();
        }
        if ((events & MatchOutcomeSequence.EVENT_SATELLITE_BONUS) != 0) {
            audio.play(OriginalAudio.Cue.SATELLITE_MULTIPLIER);
        }
        if ((events & MatchOutcomeSequence.EVENT_FLAG_BONUS) != 0) {
            audio.play(OriginalAudio.Cue.FLAG_BONUS);
        }
    }

    private void stopScoreCounterSound() {
        if (scoreCounterSoundId >= 0L) {
            audio.stop(OriginalAudio.Cue.SCORE_COUNTER, scoreCounterSoundId);
            scoreCounterSoundId = -1L;
        }
    }

    /** GL-surface pixel insets arrive on the UI thread; layout projects them to world units. */
    public void setSafeAreaInsetsPixels(float left, float top, float right, float bottom) {
        SafeAreaInsets pixels = new SafeAreaInsets(left, top, right, bottom);
        if (pixels.sameAs(pendingSafeInsets)) return;
        pendingSafeInsets = pixels;
        Gdx.graphics.requestRendering();
    }

    private void drawOriginalSplash() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.setColor(0.36f, 0.44f, 0.48f, 1f);
        batch.draw(uiAssets.metalPanel, 0f, 0f, worldWidth, worldHeight,
                0f, 0f, worldWidth / 240f, worldHeight / 168f);
        batch.setColor(Color.WHITE);
        batch.draw(splashAssets.getTexture(), splashBounds.x, splashBounds.y,
                splashBounds.width, splashBounds.height);
        batch.end();
    }

    private void drawSetupScreen() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.setColor(0.42f, 0.56f, 0.61f, 0.52f);
        batch.draw(uiAssets.metalPanel, 0f, 0f, worldWidth, worldHeight,
                0f, 0f, worldWidth / 240f, worldHeight / 168f);
        batch.setColor(Color.WHITE);
        batch.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        setupPanelRenderer.draw(batch, setupLayout.difficultyPanel, SourceSetupPanelRenderer.Plate.DIFFICULTY);
        setupPanelRenderer.draw(batch, setupLayout.playerStylePanel, SourceSetupPanelRenderer.Plate.STYLE);
        setupPanelRenderer.draw(batch, setupLayout.enemiesPanel, SourceSetupPanelRenderer.Plate.ENEMIES);
        setupPanelRenderer.draw(batch, setupLayout.startPanel, SourceSetupPanelRenderer.Plate.START);
        batch.end();

        java.util.List<Integer> selectedIds = setup.getSelectedOpponentIds();
        shapes.setProjectionMatrix(camera.combined);
        batch.begin();
        setupPanelRenderer.drawCarousel(batch, setupLayout.leaderCarouselPanel,
                setupLayout.leaderCards, setupLayout.leaderDescription);
        drawSourceCarouselArrow(setupLayout.leaderPrevious, 5819);
        drawSourceCarouselArrow(setupLayout.leaderNext, 5822);
        batch.end();

        batch.begin();
        for (int i = 0; i < setupLayout.difficultyButtons.length; i++) {
            drawSourceSetupButton(setupLayout.difficultyButtons[i], i == setup.getDifficulty());
        }
        for (int i = 0; i < setupLayout.colorSwatches.length; i++) {
            drawSourceSetupColor(setupLayout.colorSwatches[i], i, i + 1 == setup.getPlayerColor());
        }
        for (int i = 0; i < setupLayout.buildingButtons.length; i++) {
            drawSourceSetupBuilding(setupLayout.buildingButtons[i], i, i + 1 == setup.getStartingBuilding());
        }
        setupPanelRenderer.drawOpponents(batch, setupLayout.opponentWells, setupLayout.opponentArrow);
        drawSourceSetupButton(setupLayout.startButton, setup.canStart());
        batch.end();

        batch.begin();

        font.setColor(Color.WHITE);
        drawContained(uiAssets.logo, setupLayout.logo.x + 2f,
                setupLayout.logo.y + 1f, setupLayout.logo.width - 4f,
                setupLayout.logo.height - 2f);

        font.setColor(TITLE_TEXT);
        drawSetupText("GAME DIFFICULTY", setupLayout.difficultyPanel.x + 18f,
                setupLayout.difficultyPanel.y + setupLayout.difficultyPanel.height - 12f, 11f);
        for (int i = 0; i < setupLayout.difficultyButtons.length; i++) {
            Rectangle button = setupLayout.difficultyButtons[i];
            drawCentered(DIFFICULTY_LABELS[i], button.x + button.width / 2f,
                    button.y + button.height / 2f + 3f, 13f);
        }

        drawSetupText("PLAYER STYLE", setupLayout.playerStylePanel.x + 18f,
                setupLayout.playerStylePanel.y + setupLayout.playerStylePanel.height - 10f, 11f);
        drawCentered("SELECT YOUR COLOR",
                setupLayout.playerStylePanel.x + setupLayout.playerStylePanel.width * 0.29f,
                setupLayout.playerStylePanel.y + setupLayout.playerStylePanel.height * 0.76f,
                10f);
        drawCentered("SELECT YOUR BUILDINGS",
                setupLayout.playerStylePanel.x + setupLayout.playerStylePanel.width * 0.76f,
                setupLayout.playerStylePanel.y + setupLayout.playerStylePanel.height * 0.76f,
                10f);

        drawSetupText("ENEMIES", setupLayout.enemiesPanel.x + 18f,
                setupLayout.enemiesPanel.y + setupLayout.enemiesPanel.height - 12f, 11f);
        drawCentered("SELECT YOUR ENEMIES",
                setupLayout.leaderCarouselPanel.x + setupLayout.leaderCarouselPanel.width / 2f,
                setupLayout.leaderCarouselPanel.y + setupLayout.leaderCarouselPanel.height + 12f,
                10f);
        for (int i = 0; i < setupLayout.leaderCards.length; i++) {
            int leaderId = SetupScreenLayout.leaderIdAt(setupLeaderListStart, i,
                    GameSetup.leaders().size());
            Rectangle card = setupLayout.leaderCards[i];
            boolean selected = selectedIds.contains(leaderId);
            if (selected) batch.setColor(1f, 1f, 1f, SetupScreenLayout.SELECTED_LEADER_ALPHA);
            drawSourceSetupPortrait(leaderId, card.x + 3f, card.y + 3f,
                    card.width - 6f, card.height - 6f);
            if (selected) batch.setColor(Color.WHITE);
        }
        for (int i = 0; i < selectedIds.size(); i++) {
            drawSourceSelectedEnemy(i, selectedIds.get(i));
        }
        if (!setupLeaderName.isEmpty()) {
            // Source EditText 5824: centered green Arial at 12 points. The cached
            // Arial atlas is authored at 48 points; line height is not font size.
            font.setColor(0f, 0.8f, 0f, 1f);
            float previousScale = font.getScaleX();
            font.getData().setScale(previousScale * (12f / 48f) / font.getData().scaleX);
            glyphLayout.setText(font, setupLeaderName);
            font.draw(batch, glyphLayout,
                    setupLayout.leaderDescription.x
                            + (setupLayout.leaderDescription.width - glyphLayout.width) / 2f,
                    setupLayout.leaderDescription.y
                            + (setupLayout.leaderDescription.height + glyphLayout.height) / 2f);
            font.getData().setScale(previousScale);
        } else {
            font.setColor(showSetupMessage ? GOLD : TITLE_TEXT);
            drawCentered(showSetupMessage ? setupMessage : "",
                    setupLayout.leaderDescription.x + setupLayout.leaderDescription.width / 2f,
                    setupLayout.leaderDescription.y + setupLayout.leaderDescription.height / 2f + 2f,
                    10f);
        }

        font.setColor(TITLE_TEXT);
        drawSetupText("START", setupLayout.startPanel.x + 14f,
                setupLayout.startPanel.y + setupLayout.startPanel.height / 2f + 3f, 11f);
        font.setColor(setup.canStart() ? Color.WHITE : DISABLED_TEXT);
        drawCentered("START GAME", setupLayout.startButton.x
                        + setupLayout.startButton.width / 2f,
                setupLayout.startButton.y + setupLayout.startButton.height / 2f + 4f, 13f);
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(PANEL_EDGE);
        shapes.line(setupLayout.playerStylePanel.x
                        + setupLayout.playerStylePanel.width * 0.52f,
                setupLayout.playerStylePanel.y + setupLayout.playerStylePanel.height * 0.12f,
                setupLayout.playerStylePanel.x
                        + setupLayout.playerStylePanel.width * 0.52f,
                setupLayout.playerStylePanel.y + setupLayout.playerStylePanel.height * 0.73f);
        shapes.end();
    }

    private void drawSourceSelectedEnemy(int index, int leaderId) {
        Rectangle portrait = setupLayout.opponentPortraits[index];
        drawSourceSetupPortrait(leaderId, portrait.x, portrait.y, portrait.width, portrait.height);
        if (flashAnimationAssets != null && flashAnimationAssets.areSetupPortraitsLoaded()) {
            TextureRegion removeIcon = flashAnimationAssets.getShape(
                    OriginalFlashAnimationAssets.SETUP_REMOVE_LEADER_SHAPE_ID);
            if (removeIcon != null) {
                Rectangle icon = setupLayout.opponentRemoveIcons[index];
                float color = batch.getPackedColor();
                batch.setColor(1f, 1f, 1f, (index == 0 ? 166f : 143f) / 256f);
                batch.draw(removeIcon, icon.x, icon.y, icon.width, icon.height);
                batch.setPackedColor(color);
            }
        }
    }

    private void drawSourceCarouselArrow(Rectangle target, int shapeId) {
        TextureRegion art = flashAnimationAssets.getShape(shapeId);
        if (art == null) throw new IllegalStateException("Missing setup carousel arrow " + shapeId);
        float scale = Math.min(target.width / 12f, target.height / 44f);
        float old = batch.getPackedColor();
        batch.setColor(Color.WHITE);
        batch.draw(art, target.x + (target.width - 12f * scale) / 2f,
                target.y + (target.height - 44f * scale) / 2f, 12f * scale, 44f * scale);
        batch.setPackedColor(old);
    }

    private void drawSourceSetupBuilding(Rectangle box, int index, boolean selected) {
        int id = SourceSetupBuildingIndicators.shapeId(index, selected);
        TextureRegion art = flashAnimationAssets.getShape(id);
        if (art == null) throw new IllegalStateException("Missing ready setup building shape " + id);
        float scale = SourceSetupBuildingIndicators.scale(box.width, box.height);
        float originX = flashAnimationAssets.getShapeOriginX(id);
        float originY = flashAnimationAssets.getShapeOriginY(id);
        float color = batch.getPackedColor();
        batch.setColor(Color.WHITE);
        batch.draw(art, box.x + (box.width - 53f * scale) / 2f + (originX + 3f) * scale,
                box.y + (box.height + 55.5f * scale) / 2f
                        - (originY + SourceSetupBuildingIndicators.parentOffsetY(index)
                                + 4.5f + art.getRegionHeight()) * scale,
                art.getRegionWidth() * scale, art.getRegionHeight() * scale);
        batch.setPackedColor(color);
    }

    private void drawSourceSetupColor(Rectangle box, int colorIndex, boolean selected) {
        int id = SourceSetupColorIndicators.shapeId(colorIndex, selected);
        TextureRegion art = flashAnimationAssets.getShape(id);
        if (art == null) throw new IllegalStateException("Missing ready setup color shape " + id);
        float scale = SourceSetupColorIndicators.scale(box.width, box.height);
        float originX = flashAnimationAssets.getShapeOriginX(id);
        float originY = flashAnimationAssets.getShapeOriginY(id);
        float color = batch.getPackedColor();
        batch.setColor(Color.WHITE);
        batch.draw(art, box.x + (box.width - 54f * scale) / 2f + originX * scale,
                box.y + (box.height + 55f * scale) / 2f
                        - (originY + art.getRegionHeight()) * scale,
                art.getRegionWidth() * scale, art.getRegionHeight() * scale);
        batch.setPackedColor(color);
    }

    /** 4586 retains two 4486 children stopped on their empty first frame. */
    private void drawSourceSetupButton(Rectangle box, boolean active) {
        int shape = active ? 4582 : 4580;
        TextureRegion art = flashAnimationAssets.getShape(shape);
        if (art == null) throw new IllegalStateException("Missing setup button shape " + shape);
        float scale = Math.min(box.width / 135f, box.height / 28f);
        float color = batch.getPackedColor();
        batch.setColor(Color.WHITE);
        batch.draw(art, box.x + (box.width - 135f * scale) / 2f,
                box.y + (box.height - 28f * scale) / 2f,
                135f * scale, 28f * scale);
        batch.setPackedColor(color);
    }

    private void drawSetupButtonGradient(Rectangle rectangle, boolean selected) {
        float left = rectangle.x + 2.5f;
        float bottom = rectangle.y + 3f;
        float width = rectangle.width - 5f;
        float height = rectangle.height - 6f;
        float[] top = selected ? BUTTON_SELECTED_TOP : BUTTON_NORMAL_TOP;
        float[] end = selected ? BUTTON_SELECTED_BOTTOM : BUTTON_NORMAL_BOTTOM;
        int bands = 16;
        for (int i = 0; i < bands; i++) {
            float t = i / (float) (bands - 1);
            shapes.setColor(end[0] + (top[0] - end[0]) * t,
                    end[1] + (top[1] - end[1]) * t,
                    end[2] + (top[2] - end[2]) * t, 1f);
            float bandHeight = height / bands;
            shapes.rect(left, bottom + i * bandHeight, width, bandHeight + 0.25f);
        }
        shapes.setColor(0.56f, 0.70f, 0.75f, 1f);
        shapes.rect(rectangle.x + 5f, rectangle.y + rectangle.height - 5.2f,
                rectangle.width - 10f, 1f);
        shapes.setColor(0.015f, 0.035f, 0.045f, 1f);
        shapes.rect(rectangle.x + 4f, rectangle.y + 3.5f,
                rectangle.width - 8f, 1f);
    }

    private void drawSetupText(String text, float x, float baselineY, float targetHeight) {
        float oldScale = font.getScaleX();
        font.getData().setScale(targetHeight / Math.max(font.getLineHeight(), 1f));
        font.draw(batch, text, x, baselineY);
        font.getData().setScale(oldScale);
    }

    /** Setup cards use the same original portrait timeline for every leader. */
    private void drawSourceSetupPortrait(int leaderId, float x, float y,
                                         float width, float height) {
        if (leaderId < 0) return;
        if (flashAnimationAssets != null && flashAnimationAssets.areSetupPortraitsLoaded()) {
            FlashAnimationTimeline faces = flashAnimationAssets.getTimeline(
                    OriginalFlashAnimationAssets.LEADER_PORTRAITS_TIMELINE);
            GameSetup.Leader leader = GameSetup.leader(leaderId);
            FlashAnimationTimeline.Frame frame = faces == null ? null
                    : faces.getFrame(leader.portraitFrame - 1);
            if (frame != null) {
                com.badlogic.gdx.graphics.g2d.TextureRegion region =
                        flashAnimationAssets.getShape(frame.shapeId);
                if (region != null) {
                    drawContained(region, x, y, width, height);
                    return;
                }
            }
        }
        drawSetupLeaderPortrait(leaderId, x, y, width, height);
    }

    private void drawSetupLeaderPortrait(int leaderId, float x, float y,
                                         float width, float height) {
        if (leaderId < 0) return;
        if (leaderId < uiAssets.portraits.length) {
            drawContained(uiAssets.portraits[leaderId], x, y, width, height);
            return;
        }
        if (flashAnimationAssets != null && flashAnimationAssets.areSetupPortraitsLoaded()) {
            FlashAnimationTimeline faces = flashAnimationAssets.getTimeline(
                    OriginalFlashAnimationAssets.LEADER_PORTRAITS_TIMELINE);
            GameSetup.Leader leader = GameSetup.leader(leaderId);
            FlashAnimationTimeline.Frame frame = faces == null ? null
                    : faces.getFrame(leader.portraitFrame - 1);
            if (frame != null) {
                com.badlogic.gdx.graphics.g2d.TextureRegion region =
                        flashAnimationAssets.getShape(frame.shapeId);
                if (region != null) {
                    drawContained(region, x, y, width, height);
                    return;
                }
            }
        }
    }

    private void drawPanelsAndMap() {
        preparePhonePanels();
        if (displayGame != game) resetBattlefieldDisplay(false);
        battlefieldDisplay.syncCityMarkers(game);
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPanelFill(leftPanel);
        drawPanelFill(rightPanel);
        drawPanelFill(mapFrame);
        shapes.setColor(0.015f, 0.035f, 0.048f, 1f);
        shapes.rect(portraitStrip.x, portraitStrip.y,
                portraitStrip.width, portraitStrip.height);
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawPanelTexture(leftPanel);
        drawPanelTexture(rightPanel);
        phonePanelRenderer.draw(batch, logoPanel);
        phonePanelRenderer.draw(batch, centerHeader);
        phonePanelRenderer.draw(batch, toolbarPanel);
        phonePanelRenderer.draw(batch, stockPanel);
        drawPanelTexture(mapFrame);
        phonePanelRenderer.draw(batch, messagePanel);
        messagePanelRenderer.drawBackground(batch, messagePanel);
        scorePanelRenderer.draw(batch, scorePanel, game.getHuman().getPlayerColor());
        phonePanelRenderer.draw(batch, actionPanel);
        battlefieldFrameRenderer.draw(batch, mapFrame);
        batch.flush();
        setScissor(mapBounds);
        terrainRenderer.draw(batch, mapBounds);
        drawBattlefieldDisplay();
        battleUnitRenderer.drawSubmarines(batch, mapBounds, submarineBattleAnimations);
        battleUnitRenderer.drawRocket(batch, mapBounds, rocketBattleAnimations);
        battleUnitRenderer.drawCarrierPlanes(batch, mapBounds, carrierPlaneAnimations);
        battleUnitRenderer.drawStandalonePlanes(batch, mapBounds, standalonePlaneAnimations);
        battleUnitRenderer.drawAntiDefenseTracking(batch, mapBounds,
                antiDefenseTrackingAnimations);
        battleUnitRenderer.drawSatellites(batch, mapBounds, satelliteBattleAnimations);
        explosionRenderer.draw(batch, mapBounds, battleAnimations);
        turnControlRenderer.drawAnnouncement(batch, mapBounds, turnActionDisplay, game);
        batch.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        drawContained(uiAssets.logo, logoPanel.x + 7f, logoPanel.y + 2f,
                logoPanel.width - 14f, logoPanel.height - 4f);
        drawPortraits();
        synchronizeTurnControls();
        batch.draw(actionTubeAssets.getFrame(actionSlotAnimation.getFrame(0)), actionSlots[0].x, actionSlots[0].y,
                actionSlots[0].width, actionSlots[0].height);
        batch.draw(actionTubeAssets.getFrame(actionSlotAnimation.getFrame(1)), actionSlots[1].x, actionSlots[1].y,
                actionSlots[1].width, actionSlots[1].height);
        turnControlRenderer.drawConfirm(batch, confirmButton, confirmTurnAnimation.getFrame());
        batch.setColor(0.56f, 0.70f, 0.75f, 0.90f);
        batch.draw(uiAssets.metalTile, portraitStrip.x, portraitStrip.y,
                portraitStrip.width, portraitStrip.height);
        batch.setColor(Color.WHITE);
        batch.end();
        drawPortraitMeters();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        setScissor(mapBounds);
        battleUnitRenderer.drawSatelliteBeams(shapes, mapBounds, satelliteBattleAnimations);
        shapes.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        drawPanelEdges(leftPanel);
        drawPanelEdges(rightPanel);
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        navigationPanelRenderer.draw(batch, navPanel);
        informationPanelRenderer.draw(batch, infoPanel);
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
    }

    private void drawPanelTexture(Rectangle rectangle) {
        drawPanelTexture(rectangle, 0.55f, 0.68f, 0.73f, 0.52f);
    }

    private void resetBattlefieldDisplay(boolean battle) {
        if (game == null || authoredBattleFrame == null) return;
        battlefieldDisplay.reset(authoredBattleFrame, game, battle);
        battlefieldDisplay.bindActionBuildings(actionBuildingAnimations);
        displayGame = game;
    }

    /** Direct peers share one occupied-depth list; city children remain grouped. */
    private void drawBattlefieldDisplay() {
        SourceDisplayList<BattlefieldDisplayState.Item> clips = battlefieldDisplay.clips();
        for (int i = 0; i < clips.size(); i++) {
            BattlefieldDisplayState.Item item = clips.entryAt(i).getValue();
            switch (item.kind) {
                case AUTHORED:
                    battleUnitRenderer.drawHiddenCreature(batch, mapBounds, item.name, hiddenBattleEffects);
                    break;
                case CITY:
                    cityRenderer.drawCityBody(batch, mapBounds, game,
                            sourceActionMenu.getSelectedEquipment(), item.index);
                    break;
                case CITY_MARKER:
                    cityRenderer.drawCityMarker(batch, mapBounds, game, item.index);
                    break;
                case FLAG:
                    flagRenderer.drawIsland(batch, mapBounds, game, item.index);
                    break;
                case ACTION_BUILDING:
                    battleUnitRenderer.drawActionBuilding(batch, mapBounds,
                            (FlashActionBuildingAnimation) item.owner);
                    break;
                case UNIT_BODY:
                case UNIT_TINT:
                case UNIT_SHADOW:
                case UNIT_DOT:
                case EFFECT:
                    if (item.owner instanceof SourceAircraftTrailDisplay) {
                        SourceAircraftTrailDisplay display = (SourceAircraftTrailDisplay) item.owner;
                        battleUnitRenderer.drawSourceAircraftSmoke(batch, mapBounds,
                                display, item.index);
                    } else if (item.owner instanceof SourceInterceptedAircraftDisplayDepths) {
                        battleUnitRenderer.drawInterceptedAircraftRole(batch, mapBounds,
                                (SourceInterceptedAircraftDisplayDepths) item.owner, item.kind);
                    } else if (item.owner instanceof SourceCarrierPlaneDisplayDepths) {
                        SourceCarrierPlaneDisplayDepths binding =
                                (SourceCarrierPlaneDisplayDepths) item.owner;
                        if (item.kind == BattlefieldDisplayState.Kind.EFFECT)
                            battleUnitRenderer.drawCarrierExplosion(batch, mapBounds, binding);
                        else battleUnitRenderer.drawCarrierRole(batch, mapBounds, binding, item.kind);
                    } else if (item.owner instanceof SourceRocketDisplayDepths.Binding) {
                        battleUnitRenderer.drawSourceRocketRole(batch, mapBounds,
                                (SourceRocketDisplayDepths.Binding) item.owner, item.kind, item.index);
                    } else if (item.owner instanceof SourceApacheDisplayDepths.Binding) {
                        SourceApacheDisplayDepths.Binding binding =
                                (SourceApacheDisplayDepths.Binding) item.owner;
                        if (item.kind == BattlefieldDisplayState.Kind.UNIT_DOT) {
                            battleUnitRenderer.drawTargetDot(batch, mapBounds,
                                    binding.animation.getColorName());
                        } else if (item.kind == BattlefieldDisplayState.Kind.UNIT_SHADOW) {
                            battleUnitRenderer.drawApacheShadow(batch, mapBounds,
                                    binding.animation);
                        } else {
                            battleUnitRenderer.drawApacheActor(batch, mapBounds,
                                    binding.animation, binding.actorIndex,
                                    item.kind == BattlefieldDisplayState.Kind.UNIT_TINT);
                        }
                    } else if (item.owner instanceof SourceInfantryDisplayDepths.Binding) {
                        SourceInfantryDisplayDepths.Binding binding =
                                (SourceInfantryDisplayDepths.Binding) item.owner;
                        if (item.kind == BattlefieldDisplayState.Kind.UNIT_DOT) {
                            battleUnitRenderer.drawTargetDot(batch, mapBounds,
                                    binding.animation.getTint());
                        } else {
                            battleUnitRenderer.drawInfantryActor(batch, mapBounds,
                                    binding.animation, binding.actorIndex,
                                    item.kind == BattlefieldDisplayState.Kind.UNIT_TINT);
                        }
                    } else if (item.owner instanceof SourceChinookDisplayDepths.Binding) {
                        SourceChinookDisplayDepths.Binding binding =
                                (SourceChinookDisplayDepths.Binding) item.owner;
                        if (item.kind == BattlefieldDisplayState.Kind.UNIT_DOT) {
                            battleUnitRenderer.drawTargetDot(batch, mapBounds,
                                    GameSetup.colorName(game.getPlayerState(
                                            binding.animation.getAttackerPlayerId())
                                            .getPlayerColor()));
                        } else {
                            battleUnitRenderer.drawChinookActor(batch, mapBounds,
                                    binding.animation, binding.actorIndex,
                                    item.kind == BattlefieldDisplayState.Kind.UNIT_SHADOW);
                        }
                    } else if (item.owner instanceof SourceSuicideDisplayDepths.Binding) {
                        SourceSuicideDisplayDepths.Binding binding =
                                (SourceSuicideDisplayDepths.Binding) item.owner;
                        String colorName = GameSetup.colorName(game.getPlayerState(
                                binding.animation.getAttackerPlayerId()).getPlayerColor());
                        if (item.kind == BattlefieldDisplayState.Kind.UNIT_DOT) {
                            battleUnitRenderer.drawTargetDot(batch, mapBounds, colorName);
                        } else {
                            battleUnitRenderer.drawSuicideActor(batch, mapBounds,
                                    binding.animation.getActor(item.index), colorName);
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void drawPanelTexture(Rectangle rectangle, float red, float green,
                                  float blue, float alpha) {
        float inset = 4f;
        float x = rectangle.x + inset;
        float y = rectangle.y + inset;
        float width = Math.max(0f, rectangle.width - inset * 2f);
        float height = Math.max(0f, rectangle.height - inset * 2f);
        batch.setColor(red, green, blue, alpha);
        batch.draw(uiAssets.metalPanel, x, y, width, height,
                0f, 0f, width / 240f, height / 168f);
        batch.setColor(Color.WHITE);
    }

    private void drawPortraits() {
        int visiblePlayers = 0;
        for (int playerId = 1; playerId < 5; playerId++) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            if (player.alive || player.getLeaderId() >= 0) {
                visiblePlayers++;
            }
        }
        for (int slot = 0; slot < portraitCards.length; slot++) {
            portraitPlayerIds[slot] = -1;
            portraitCards[slot].set(0f, 0f, 0f, 0f);
        }
        if (visiblePlayers == 0) {
            return;
        }

        float slotWidth = Math.min(84f, (centerHeader.width - 16f) / visiblePlayers);
        float cardWidth = slotWidth - 3f;
        float cardHeight = Math.min(51f, centerHeader.height - 9f);
        float startX = centerHeader.x + centerHeader.width / 2f
                - slotWidth * visiblePlayers / 2f;
        float y = centerHeader.y + 6f;
        int visibleSlot = 0;
        for (int playerId = 1; playerId < 5; playerId++) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            if (!player.alive && player.getLeaderId() < 0) {
                continue;
            }
            float x = startX + visibleSlot * slotWidth;
            Rectangle card = portraitCards[visibleSlot];
            card.set(x, y, cardWidth, cardHeight);
            portraitPlayerIds[visibleSlot] = playerId;
            phonePanelRenderer.draw(batch, card);
            int leaderId = player.getLeaderId();
            if (leaderId >= 0) {
                drawSetupLeaderPortrait(leaderId, x + 5f, y + 3f,
                        cardWidth - 10f, cardHeight - 6f);
            } else {
                int fallbackPortrait = (playerId - 1) % uiAssets.portraits.length;
                drawContained(uiAssets.portraits[fallbackPortrait], x + 5f, y + 3f,
                        cardWidth - 10f, cardHeight - 6f);
            }
            visibleSlot++;
        }
    }

    private void drawPortraitMeters() {
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < portraitCards.length; i++) {
            Rectangle card = portraitCards[i];
            if (portraitPlayerIds[i] < 0 || card.width <= 0f || card.height <= 0f) {
                continue;
            }
            GameState.PlayerState player = game.getPlayerState(portraitPlayerIds[i]);
            int color = player.getPlayerColor();
            shapes.setColor(color > 0 && color <= SETUP_PLAYER_COLORS.length
                    ? SETUP_PLAYER_COLORS[color - 1] : PORTRAIT_COLORS[i]);
            shapes.rect(card.x + 5f, card.y + card.height - 4f,
                    card.width - 10f, 2f);
        }
        shapes.end();
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
                SourceNavigationGradient.draw(
                        shapes, row, game.getSelectedAction() == ACTIONS[i]);
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
            if (game.isActionBioloaded(0, ACTIONS[i])) {
                float markerY = row.y + row.height / 2f;
                drawBioloadMarker(row.x + 11f, markerY, 14f, 12f);
                drawBioloadMarker(row.x + row.width - 11f, markerY, 14f, 12f);
            }
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
                batch.draw(uiAssets.buttonPatches[2 - row][column], patchX, patchY,
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

    /** Controls occupy disjoint row rectangles; group textures without changing artwork or clipping. */
    private void drawStockControlArtwork() {
        for (int texture = 0; texture <= uiAssets.stockSwitches.length; texture++) {
            boolean counter = texture == uiAssets.stockSwitches.length;
            for (int rowIndex = 0; rowIndex < EQUIPMENT_ROWS.length; rowIndex++) {
                if (isEquipmentHeading(rowIndex)) continue;
                Rectangle row = equipmentRows[rowIndex];
                if (row.y + row.height < equipmentViewport.y
                        || row.y > equipmentViewport.y + equipmentViewport.height) continue;
                if (!counter && SourceStockControlState.switchIndex(
                        SourceStockControlState.state(game, sourceActionMenu,
                                equipmentWeaponName(rowIndex))) != texture) continue;
                float controlHeight = Math.min(19f, row.height - 5f);
                float controlY = row.y + (row.height - controlHeight) / 2f;
                float counterWidth = controlHeight * 32f / 26f;
                float switchWidth = controlHeight * 30f / 26f;
                float counterX = row.x + row.width - counterWidth - switchWidth - 5f;
                if (counter) {
                    batch.draw(uiAssets.stockCounter, counterX, controlY, counterWidth, controlHeight);
                } else {
                    batch.draw(uiAssets.stockSwitches[texture], counterX + counterWidth + 2f,
                            controlY, switchWidth, controlHeight);
                }
            }
        }
    }

    private void drawEquipmentRows() {
        setScissor(equipmentViewport);
        float top = equipmentViewport.y + equipmentViewport.height
                + equipmentScroll.getOffset() - EQUIPMENT_TOP_PADDING;
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
            }
            top = cardY - 3f;
        }
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (int group = 0; group < equipmentCards.length; group++) {
            Rectangle card = equipmentCards[group];
            if (card.y + card.height >= equipmentViewport.y
                    && card.y <= equipmentViewport.y + equipmentViewport.height) {
                equipmentCardsRenderer.draw(batch, group, card);
            }
        }
        drawStockControlArtwork();
        batch.end();

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
            int stockState = SourceStockControlState.state(game, sourceActionMenu,
                    equipmentWeaponName(rowIndex));
            font.setColor(SourceStockControlState.usesGrayLabel(stockState)
                    ? DISABLED_TEXT : TITLE_TEXT);
            float controlHeight = Math.min(19f, row.height - 5f);
            float countWidth = controlHeight * 32f / 26f;
            float switchWidth = controlHeight * 30f / 26f;
            float countX = row.x + row.width - countWidth - switchWidth - 5f;
            float labelScale = font.getScaleX();
            float labelWidth = Math.max(1f, countX - row.x - 6f);
            glyphLayout.setText(font, EQUIPMENT_ROWS[rowIndex]);
            if (glyphLayout.width > labelWidth) {
                font.getData().setScale(labelScale * labelWidth / glyphLayout.width);
            }
            font.draw(batch, EQUIPMENT_ROWS[rowIndex], row.x + 2f,
                    row.y + row.height / 2f + font.getCapHeight() / 2f);
            font.getData().setScale(labelScale);
            font.setColor(Color.WHITE);
            int stock = SourceStockControlState.displayedCount(game, sourceActionMenu,
                    equipmentWeaponName(rowIndex));
            drawCentered(stock >= 0 ? Integer.toString(stock) : "-",
                    countX + countWidth / 2f, row.y + row.height / 2f + 3f, 9f);
            if (isEquipmentBioloaded(rowIndex)) {
                drawBioloadMarker(countX + countWidth - 1f,
                        row.y + row.height / 2f + 7f, 10f, 9f);
            }
        }
        font.getData().setScale(originalTextScale);
        batch.end();

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

    private boolean isEquipmentBioloaded(int rowIndex) {
        String weaponName = equipmentWeaponName(rowIndex);
        return weaponName != null && game.isWeaponBioloaded(0, weaponName);
    }

    /** Draws the recovered Flash contamination artwork over the affected control. */
    private void drawBioloadMarker(float centerX, float centerY, float width, float height) {
        TextureRegion marker = flashAnimationAssets == null ? null
                : flashAnimationAssets.getShape(OriginalFlashAnimationAssets.BIO_CLOUD_SHAPE_ID);
        if (marker == null) {
            font.setColor(GREEN);
            font.draw(batch, "BIO", centerX - width / 2f,
                    centerY + font.getLineHeight() * 0.28f);
            font.setColor(Color.WHITE);
            return;
        }
        batch.setColor(1f, 1f, 1f, 0.9f);
        batch.draw(marker, centerX - width / 2f, centerY - height / 2f, width, height);
        batch.setColor(Color.WHITE);
    }

    private static String equipmentWeaponName(int rowIndex) {
        return rowIndex < 0 || rowIndex >= EQUIPMENT_WEAPON_NAMES.length
                ? null : EQUIPMENT_WEAPON_NAMES[rowIndex];
    }



    private void drawTextAndControls() {
        updateMessageHistoryLayout();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(TITLE_TEXT);
        drawCentered("HELP", helpButton.x + helpButton.width / 2f,
                helpButton.y + helpButton.height / 2f + 3f, 9f);
        drawCentered(audio.isOutputMuted() ? "UNMUTE" : "MUTE", muteButton.x + muteButton.width / 2f,
                muteButton.y + muteButton.height / 2f + 3f, 9f);

        drawStatistics();
        if (!ActionScreenLayout.hasInlineActionControls(game.getSelectedAction())
                || SourceActionMenuState.supports(game.getSelectedAction())) drawMessageWell();
        font.setColor(TITLE_TEXT);
        drawScoreValue();
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
        shapes.end();
        batch.begin();
        drawQueuedActionLabels();
        batch.end();
        drawInlineActionFooter();
        drawSettingsGear();
    }

    /** Center complete glyph output in the source window, including large scores. */
    private void drawScoreValue() {
        ScorePanelLayout.valueBounds(scorePanel, scoreValueBounds);
        if (scoreValueBounds.width <= 0f || scoreValueBounds.height <= 0f) return;
        float oldX = font.getScaleX(), oldY = font.getScaleY();
        Color color = font.getColor();
        float oldR = color.r, oldG = color.g, oldB = color.b, oldA = color.a;
        String text = Long.toString(game.getHuman().score);
        font.getData().setScale(1f);
        glyphLayout.setText(font, text);
        float scale = Math.min(13f / Math.max(1f, glyphLayout.height),
                Math.min(scoreValueBounds.width / Math.max(1f, glyphLayout.width),
                        scoreValueBounds.height / Math.max(1f, glyphLayout.height)));
        font.getData().setScale(scale);
        font.setColor(Color.WHITE);
        glyphLayout.setText(font, text);
        font.draw(batch, glyphLayout, scoreValueBounds.x + (scoreValueBounds.width - glyphLayout.width) / 2f,
                scoreValueBounds.y + (scoreValueBounds.height + glyphLayout.height) / 2f);
        font.getData().setScale(oldX, oldY);
        font.setColor(oldR, oldG, oldB, oldA);
    }

    private void drawMessageWell() {
        instructionViewport.set(messageViewport);
        if (game.getSelectedAction() == GameState.Action.PROPAGANDA
                && sourceActionMenu.isMapSelection()) {
            float lowerEdge = actionFooterButtons[1].y + actionFooterButtons[1].height + 3f;
            float top = messageViewport.y + messageViewport.height;
            instructionViewport.y = Math.max(messageViewport.y, lowerEdge);
            instructionViewport.height = Math.max(1f, top - instructionViewport.y);
        }
        batch.flush();
        setScissor(instructionViewport);
        String instruction = sourceActionInstruction.getText();
        if (instruction == null) {
            messagePanelRenderer.drawHistory(batch, messageHistoryLayout, instructionViewport,
                    messageHistoryScroll.getFirstVisibleLine());
        } else {
            if (!instruction.equals(laidOutInstruction)
                    || Math.abs(instructionViewport.width - instructionLayoutWidth) > 0.01f) {
                instructionLayout.setText(messagePanelRenderer.getFont(), instruction,
                        MessagePanelRenderer.TEXT_COLOR, instructionViewport.width, Align.left, true);
                laidOutInstruction = instruction;
                instructionLayoutWidth = instructionViewport.width;
            }
            messagePanelRenderer.getFont().draw(batch, instructionLayout,
                    instructionViewport.x, instructionViewport.y + instructionViewport.height - 3f);
        }
        batch.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private void drawInlineActionFooter() {
        if (helpState.isOpen()) return;
        GameState.Action action = game.getSelectedAction();
        if (SourceActionMenuState.supports(action)) {
            if (action == GameState.Action.PROPAGANDA && sourceActionMenu.isMapSelection())
                drawPropagandaCityPrompt();
            return;
        }
        if (!ActionScreenLayout.hasInlineActionControls(action) || modalOpen) return;
        if (action == GameState.Action.PROPAGANDA) {
            drawPropagandaCityPrompt();
            return;
        }
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (Rectangle button : actionFooterButtons) {
            drawOriginalButton(button);
        }
        batch.end();

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < actionFooterButtons.length; i++) {
            boolean selected = i == 2 && (action != GameState.Action.ATTACK
                    || game.canAcceptAttack());
            drawButtonGradient(actionFooterButtons[i], selected);
        }
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(GREEN);
        CharSequence summary = action == GameState.Action.MASS_MEDIA
                ? massMediaFooterSummary() : directAttackFooterSummary();
        drawFittedLeft(summary, messagePanel.x + 8f,
                messagePanel.y + messagePanel.height - 10f,
                messagePanel.width - 16f, 9f);
        font.setColor(TITLE_TEXT);
        drawCentered("DETAILS", actionFooterButtons[0].x
                        + actionFooterButtons[0].width / 2f,
                actionFooterButtons[0].y + actionFooterButtons[0].height / 2f + 2f, 7.5f);
        drawCentered("CANCEL", actionFooterButtons[1].x
                        + actionFooterButtons[1].width / 2f,
                actionFooterButtons[1].y + actionFooterButtons[1].height / 2f + 2f, 7.5f);
        font.setColor(action != GameState.Action.ATTACK || game.canAcceptAttack()
                ? TITLE_TEXT : DISABLED_TEXT);
        drawCentered("ACCEPT", actionFooterButtons[2].x
                        + actionFooterButtons[2].width / 2f,
                actionFooterButtons[2].y + actionFooterButtons[2].height / 2f + 2f, 7.5f);
        batch.end();
    }

    private void drawPropagandaCityPrompt() {
        Rectangle cancel = actionFooterButtons[1];
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawOriginalButton(cancel);
        batch.end();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawButtonGradient(cancel, false);
        shapes.end();
        batch.begin();
        font.setColor(TITLE_TEXT);
        drawCentered("CANCEL", cancel.x + cancel.width / 2f,
                cancel.y + cancel.height / 2f + 2f, 7.5f);
        batch.end();
    }

    private CharSequence massMediaFooterSummary() {
        actionFooterText.setLength(0);
        actionFooterText.append("MASS MEDIA  +").append(game.getMassMediaGain())
                .append("% INFLUENCE");
        return actionFooterText;
    }

    private CharSequence directAttackFooterSummary() {
        actionFooterText.setLength(0);
        String weapon = game.getSelectedAttackWeapon();
        GameState.PlayerState target = game.getPlayerState(game.getSelectedTargetPlayer());
        actionFooterText.append("ATTACK: ");
        if (weapon == null) {
            actionFooterText.append("SELECT A UNIT");
        } else {
            appendUppercase(actionFooterText, weapon);
            actionFooterText.append(" x").append(game.getWeaponStock(weapon));
        }
        if (target != null) {
            actionFooterText.append("  |  NATION ").append(target.id)
                    .append("  CITY ").append(game.getSelectedTargetCity() + 1);
        }
        return actionFooterText;
    }

    private static void appendUppercase(StringBuilder output, String value) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            output.append(character >= 'a' && character <= 'z'
                    ? (char) (character - ('a' - 'A')) : character);
        }
    }

    private void drawFittedLeft(CharSequence text, float x, float baselineY,
                                float maxWidth, float targetHeight) {
        float originalScale = font.getScaleX();
        font.getData().setScale(targetHeight / Math.max(font.getLineHeight(), 1f));
        glyphLayout.setText(font, text);
        if (glyphLayout.width > maxWidth && glyphLayout.width > 0f) {
            font.getData().setScale(font.getScaleX() * maxWidth / glyphLayout.width);
        }
        font.draw(batch, text, x, baselineY);
        font.getData().setScale(originalScale);
    }

    private void drawStatistics() {
        float x = infoPanel.x + 9f;
        float width = infoPanel.width - 18f;
        GameState.PlayerState human = game.getHuman();
        float originalScale = font.getScaleX();
        float targetScale = InformationPanelLayout.fontScale(infoPanel, font.getLineHeight());
        font.getData().setScale(targetScale);
        font.setColor(TITLE_TEXT);
        drawInformationText("FUNDS", x, 0);
        String funds = InformationPanelText.funds(human.turnMoneyMillions);
        float fundsX = x + width * 0.43f;
        float fundsWidth = width * 0.57f;
        glyphLayout.setText(font, funds);
        if (glyphLayout.width > fundsWidth && glyphLayout.width > 0f) {
            font.getData().setScale(targetScale * fundsWidth / glyphLayout.width);
        }
        font.setColor(GOLD);
        drawInformationText(funds, fundsX, 0);
        font.getData().setScale(targetScale);
        font.setColor(TITLE_TEXT);
        drawInformationText("technology", x, 1);
        drawInformationText("propaganda", x, 2);
        drawInformationText("population", x, 3);
        drawInformationText("spy", x, 4);
        font.getData().setScale(originalScale);
    }

    private void drawInformationText(String text, float x, int row) {
        float center = InformationPanelLayout.rowCenter(infoPanel, row);
        font.draw(batch, text, x, InformationTextAlignment.drawY(font.getData(), text, center));
    }

    private void drawStatisticsBars() {
        float x = infoPanel.x + infoPanel.width * 0.47f;
        double width = infoPanel.width * 0.47d;
        GameState.PlayerState human = game.getHuman();
        long activePopulation = 0;
        for (int playerId = 0; playerId < 5; playerId++) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            if (player.alive) {
                activePopulation += player.populationMillions;
            }
        }
        double populationShare = SourceStatisticBar.populationPercentage(
                human.populationMillions, activePopulation);
        drawInformationBar(x, InformationPanelLayout.barY(infoPanel, 1), width,
                human.getTechnologyLevel());
        drawInformationBar(x, InformationPanelLayout.barY(infoPanel, 2), width,
                human.religion);
        drawInformationBar(x, InformationPanelLayout.barY(infoPanel, 3), width,
                populationShare);
        drawInformationBar(x, InformationPanelLayout.barY(infoPanel, 4), width,
                human.spyReadiness);
    }

    private void drawInformationBar(float x, float y, double width, double statistic) {
        // Pygame truncates the positive logical coordinates and track width;
        // the separately rounded fill can extend one column past that track.
        int left = (int) x;
        int bottom = (int) y;
        shapes.setColor(SourceStatisticBar.TRACK_RED / 255f,
                SourceStatisticBar.TRACK_GREEN / 255f, SourceStatisticBar.TRACK_BLUE / 255f, 1f);
        shapes.rect(left, bottom, (int) width, InformationPanelLayout.BAR_HEIGHT);
        int columns = SourceStatisticBar.filledColumns(width, statistic);
        for (int column = 0; column < columns; column++) {
            shapes.setColor(1f, SourceStatisticBar.greenAt(width, column) / 255f, 0f, 1f);
            shapes.rect(left + column, bottom, 1f, InformationPanelLayout.BAR_HEIGHT);
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
        if (turnPlaybackSequence.getCurrentAction() != null) {
            Rectangle target = actionSlots[0];
            shapes.setColor(1f, 0.48f, 0.02f, 0.92f);
            shapes.rect(target.x + target.width * 0.13f, target.y + 6f,
                    target.width * 0.74f, target.height - 12f);
        }
    }

    private void drawQueuedActionLabels() {
        for (int slot = 0; slot < actionSlots.length; slot++) {
            GameState.Action action = actionSlotAnimation.getDisplayedAction(slot);
            if (action == null) {
                continue;
            }
            Rectangle target = actionSlots[slot];
            font.setColor(QUEUED_ACTION_TEXT.r, QUEUED_ACTION_TEXT.g,
                    QUEUED_ACTION_TEXT.b, 0.65f * actionSlotAnimation.getLabelAlpha(slot));
            if (action == GameState.Action.ATTACK || action == GameState.Action.PREPARED_ATTACK) {
                drawCentered(action == GameState.Action.ATTACK ? "DIRECT" : "PREPARED",
                        target.x + target.width / 2f, target.y + target.height / 2f + 7f, 6.5f);
                drawCentered("ATTACK", target.x + target.width / 2f,
                        target.y + target.height / 2f - 2f, 6.5f);
            } else {
                drawCentered(sourceActionLabel(action), target.x + target.width / 2f,
                        target.y + target.height / 2f + 3f, 8f);
            }
        }
        font.setColor(Color.WHITE);
    }

    private void drawPlaybackActionLabel() {
        GameState.Action action = turnPlaybackSequence.getCurrentAction();
        if (action == null) {
            return;
        }
        Rectangle target = actionSlots[0];
        font.setColor(MODAL_ORANGE);
        drawCentered(sourceActionLabel(action), target.x + target.width / 2f,
                target.y + target.height / 2f + 3f, 8f);
        font.setColor(Color.WHITE);
    }

    private static String sourceActionLabel(GameState.Action action) {
        switch (action) {
            case RESOURCES:
                return "RESOURCES";
            case PRODUCTION:
                return "PRODUCTION";
            case RESEARCH:
                return "RESEARCH";
            case MASS_MEDIA:
                return "RELIGION";
            case PREPARED_ATTACK:
                return "PRE-ATTACK";
            case ATTACK:
                return "ATTACK";
            case DEFENSE:
                return "DEFENSE";
            case PROPAGANDA:
                return "PROPAGANDA";
            case DIPLOMACY:
                return "DIPLOMACY";
            case SKIP:
                return "SKIP";
            default:
                return action.label.toUpperCase(java.util.Locale.US);
        }
    }

    private void drawSettingsGear() {
        batch.setProjectionMatrix(camera.combined);
        float oldColor = batch.getPackedColor();
        batch.begin();
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        batch.draw(uiAssets.phoneGear, settingsButton.x,
                settingsButton.y + settingsButton.height - 23f, 23f, 23f);
        batch.end();
        batch.setPackedColor(oldColor);
    }

    private void drawActionModal() {
        GameState.Action action = game.getSelectedAction();
        if (SourceActionMenuState.supports(action) || sourceActionMenu.isEconomyWarning(game)) {
            drawSourceActionMenu();
            return;
        }
        if (action == GameState.Action.PRODUCTION) {
            drawProductionModal();
            return;
        }
        if (action == GameState.Action.RESOURCES) {
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            resourceMenuRenderer.draw(batch, modal, game);
            batch.end();
            return;
        }
        if (action == GameState.Action.RESEARCH) {
            drawTechnologyModal();
            return;
        }
        if (action == GameState.Action.MASS_MEDIA && drawMassMediaModal()) {
            return;
        }
        if (action == GameState.Action.PROPAGANDA) {
            drawPropagandaModal();
            return;
        }

        java.util.List<String> researchChoices = action == GameState.Action.RESEARCH
                ? game.getResearchChoices() : java.util.Collections.emptyList();
        int pageCount = Math.max(1,
                (researchChoices.size() + RESEARCH_ROWS_PER_PAGE - 1) / RESEARCH_ROWS_PER_PAGE);
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
        if (action == GameState.Action.RESEARCH) {
            int first = researchPage * RESEARCH_ROWS_PER_PAGE;
            float originalScale = font.getScaleX();
            font.getData().setScale(0.58f);
            for (int i = 0; i < researchRows.length; i++) {
                int item = first + i;
                if (item >= researchChoices.size()) {
                    break;
                }
                String technology = researchChoices.get(item);
                boolean selected = game.isResearchTechnologySelected(technology);
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
        } else if (action == GameState.Action.RESEARCH) {
            int first = researchPage * RESEARCH_ROWS_PER_PAGE;
            float originalScale = font.getScaleX();
            font.getData().setScale(0.58f);
            for (int i = 0; i < researchRows.length; i++) {
                int item = first + i;
                if (item >= researchChoices.size()) {
                    break;
                }
                String technology = researchChoices.get(item);
                Rectangle row = researchRows[i];
                String label = game.getTechnologyLabel(technology) + "   $"
                        + game.getTechnologyCost(technology) + "M";
                font.setColor(game.isResearchAvailable(technology)
                        ? Color.WHITE : RESEARCH_UNAVAILABLE_TEXT);
                font.draw(batch, label, row.x + 7f, row.y + row.height * 0.72f);
            }
            font.getData().setScale(originalScale);
            font.setColor(TITLE_TEXT);
            drawCentered("PREV", researchPrevious.x + researchPrevious.width / 2f,
                    researchPrevious.y + researchPrevious.height / 2f + 2f, 6.5f);
            drawCentered((researchPage + 1) + " / " + pageCount,
                    modal.x + modal.width / 2f,
                    researchPrevious.y + researchPrevious.height / 2f + 2f, 6.5f);
            drawCentered("NEXT", researchNext.x + researchNext.width / 2f,
                    researchNext.y + researchNext.height / 2f + 2f, 6.5f);
            drawCentered("ESTIMATED TOTAL: $ "
                            + game.getSelectedResearchCostMillions() + "M",
                    modal.x + modal.width / 2f, modal.y + 39f, 7f);
            if (researchChoices.isEmpty()) {
                drawCentered("No research choices available.",
                        modal.x + modal.width / 2f, modal.y + modal.height / 2f, 8f);
            }
        } else if (action == GameState.Action.ATTACK
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

    private void drawTechnologyModal() {
        java.util.List<String> choices = game.getResearchChoices();
        updateResearchChoiceCards(choices);

        if (drawOriginalTechnologyModal()) {
            return;
        }

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPanelFill(modal);
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawPanelTexture(modal);
        drawOriginalButton(modalCancel);
        drawOriginalButton(modalAccept);
        batch.end();

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(MODAL_ORANGE);
        shapes.rect(modal.x + 3f, modal.y + modal.height - 29f,
                modal.width - 6f, 25f);
        shapes.rect(modal.x + 3f, modal.y + 3f, modal.width - 6f, 25f);
        shapes.setColor(0.02f, 0.07f, 0.10f, 0.95f);
        shapes.rect(technologySelectedArea.x, technologySelectedArea.y,
                technologySelectedArea.width, technologySelectedArea.height);
        for (int i = 0; i < technologyCards.length; i++) {
            Rectangle card = technologyCards[i];
            String technology = researchChoiceByCard[i];
            boolean selected = technology != null
                    && game.isResearchTechnologySelected(technology);
            boolean available = technology != null && game.isResearchAvailable(technology);
            if (technology == null) {
                shapes.setColor(0.05f, 0.09f, 0.11f, 0.95f);
            } else if (selected) {
                shapes.setColor(0.12f, 0.30f, 0.35f, 0.98f);
            } else if (available) {
                shapes.setColor(0.08f, 0.17f, 0.21f, 0.98f);
            } else {
                shapes.setColor(0.10f, 0.10f, 0.11f, 0.96f);
            }
            shapes.rect(card.x, card.y, card.width, card.height);
        }
        drawButtonGradient(modalCancel, false);
        drawButtonGradient(modalAccept, false);
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        drawCentered("TECHNOLOGY", modal.x + modal.width / 2f,
                modal.y + modal.height - 12f, 12f);
        for (int i = 0; i < technologyCards.length; i++) {
            Rectangle card = technologyCards[i];
            String technology = researchChoiceByCard[i];
            drawFittedCentered(RESEARCH_GROUP_LABELS[i],
                    card.x + card.width / 2f, card.y + card.height - 7f,
                    6.5f, card.width - 8f);
            if (technology == null) {
                font.setColor(DISABLED_TEXT);
                drawFittedCentered("COMPLETED", card.x + card.width / 2f,
                        card.y + card.height * 0.52f, 7f, card.width - 8f);
                drawCentered("ALL", card.x + card.width / 2f, card.y + 12f, 6f);
                continue;
            }
            boolean selected = game.isResearchTechnologySelected(technology);
            boolean available = game.isResearchAvailable(technology);
            font.setColor(available ? TITLE_TEXT : DISABLED_TEXT);
            drawFittedCentered(game.getTechnologyLabel(technology),
                    card.x + card.width / 2f, card.y + card.height * 0.55f,
                    7f, card.width - 8f);
            font.setColor(selected ? GOLD : available ? GREEN : RESEARCH_UNAVAILABLE_TEXT);
            String status = selected ? "SELECTED"
                    : available ? researchCostLabel(game.getTechnologyCost(technology))
                    : "LOCKED";
            drawFittedCentered(status, card.x + card.width / 2f,
                    card.y + 12f, 6f, card.width - 8f);
        }

        CharSequence selectedCategories = selectedResearchCategoriesSummary();
        font.setColor(TITLE_TEXT);
        drawFittedCentered(selectedCategories, technologySelectedArea.x
                        + technologySelectedArea.width / 2f,
                technologySelectedArea.y + technologySelectedArea.height - 7f,
                7f, technologySelectedArea.width - 12f);
        font.setColor(GOLD);
        selectedResearchSummary.setLength(0);
        selectedResearchSummary.append("RESEARCH BUDGET  $")
                .append(game.getSelectedResearchCostMillions()).append('M');
        drawFittedCentered(selectedResearchSummary,
                technologySelectedArea.x + technologySelectedArea.width / 2f,
                technologySelectedArea.y + 14f, 7f,
                technologySelectedArea.width - 12f);
        font.setColor(TITLE_TEXT);
        drawCentered("CANCEL", modalCancel.x + modalCancel.width / 2f,
                modalCancel.y + modalCancel.height / 2f + 3f, 8f);
        drawCentered("ACCEPT", modalAccept.x + modalAccept.width / 2f,
                modalAccept.y + modalAccept.height / 2f + 3f, 8f);
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Rectangle card : technologyCards) {
            drawTechnologyFrame(card);
        }
        drawTechnologyFrame(technologySelectedArea);
        drawPanelEdges(modal);
        shapes.end();
    }

    private boolean drawOriginalTechnologyModal() {
        for (int i = 0; i < researchFrameIndexes.length; i++) {
            String technology = researchChoiceByCard[i];
            if (technology == null) {
                researchFrameIndexes[i] = -1;
                continue;
            }
            TechnologyCatalog.Definition definition = TechnologyCatalog.byName(technology);
            researchFrameIndexes[i] = definition == null ? -1
                    : definition.level - 1
                    + (game.isResearchTechnologySelected(technology) ? 4 : 0);
        }

        // The source HUD's foreground hides centerScreen's oversized backing shape.
        setScissor(modal);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        boolean drawn = centerUiRenderer.draw(batch, flashAnimationAssets, modal,
                FlashCenterUiRenderer.RESEARCH_FRAME,
                game.getHuman().getReligionCode(), researchFrameIndexes);
        if (drawn) {
            drawOriginalTechnologyText();
            batch.setColor(Color.WHITE);
            batch.draw(uiAssets.propagandaFrame, modal.x, modal.y, modal.width, modal.height);
        }
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        return drawn;
    }

    private void drawOriginalTechnologyText() {
        for (int slot = 0; slot < technologyCards.length; slot++) {
            String technology = researchChoiceByCard[slot];
            if (technology == null) {
                continue;
            }
            int cost = game.getTechnologyCost(technology);
            boolean available = game.isResearchAvailable(technology);
            String costText = "$ " + sourceMillions(cost);
            Color costColor = available ? Color.BLACK : RESEARCH_UNAVAILABLE_TEXT;
            if (TechnologyScreenLayout.sourceGroupAtCard(slot) == 7) {
                drawSourceTextCentered(costText, TechnologyScreenLayout.cardCostCenter(slot),
                        TechnologyScreenLayout.cardCostBaselineTop(slot), 10f, costColor);
            } else {
                drawSourceTextRight(costText, TechnologyScreenLayout.cardCostRight(slot),
                        TechnologyScreenLayout.cardCostBaselineTop(slot), 10f, costColor);
            }
        }

        int row = 0;
        int nameRow = 0;
        for (TechnologyCatalog.Definition definition : TechnologyCatalog.all()) {
            String technology = definition.name;
            if (!game.isResearchTechnologySelected(technology)) {
                continue;
            }
            // The two authored fields wrap and limit their lines independently.
            java.util.List<String> nameLines = researchTextLayout.nameLines(technology);
            for (int lineIndex = 0; lineIndex < nameLines.size(); lineIndex++) {
                if (nameRow < TechnologySelectionTextLayout.VISIBLE_LINES) {
                    drawSourceText(nameLines.get(lineIndex), TechnologyScreenLayout.SELECTED_NAMES_X,
                            TechnologyScreenLayout.selectedNamesTextTop()
                                    + nameRow * TechnologySelectionTextLayout.LINE_SPACING,
                            TechnologyScreenLayout.SELECTED_TEXT_LINE_HEIGHT, Color.WHITE);
                }
                nameRow++;
            }
            if (row < TechnologySelectionTextLayout.VISIBLE_LINES) {
                drawSourceTextRight(sourceMillions(game.getTechnologyCost(technology)),
                        TechnologyScreenLayout.SELECTED_COSTS_RIGHT,
                        TechnologyScreenLayout.selectedCostsTextTop()
                                + row * TechnologySelectionTextLayout.LINE_SPACING,
                        TechnologyScreenLayout.SELECTED_TEXT_LINE_HEIGHT, Color.WHITE);
            }
            row++;
        }
        if (row > 0) {
            drawSourceTextRight("estimated total: "
                            + sourceMillions(game.getSelectedResearchCostMillions()),
                    TechnologyScreenLayout.ESTIMATED_TOTAL_RIGHT,
                    TechnologyScreenLayout.estimatedTotalTextTop(),
                    TechnologyScreenLayout.SELECTED_TEXT_LINE_HEIGHT,
                    RESEARCH_TOTAL_TEXT);
        }
    }

    private boolean drawMassMediaModal() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        boolean drawn = centerUiRenderer.draw(batch, flashAnimationAssets, modal,
                FlashCenterUiRenderer.MASS_MEDIA_FRAME,
                game.getHuman().getReligionCode(), null);
        if (drawn) {
            GameState.PlayerState human = game.getHuman();
            int gain = game.getMassMediaGain();
            drawSourceTextRight(sourceMillions(human.populationMillions), 337f, 75f,
                    10f, TITLE_TEXT);
            drawSourceTextRight(human.religion + "%", 337f, 90f, 10f, TITLE_TEXT);
            drawSourceTextRight("+" + gain + "%", 337f, 161.15f,
                    20f, MODAL_ORANGE);
        }
        batch.end();
        return drawn;
    }

    private void drawPropagandaModal() {
        setScissor(modal);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        boolean drawn = centerUiRenderer.draw(batch, flashAnimationAssets, modal,
                FlashCenterUiRenderer.PROPAGANDA_FRAME, null, null);
        if (drawn) {
            // centerScreen.text1 at (37, 122), 9pt, right aligned in a 102.2pt field.
            float scaleX = modal.width / FlashCenterUiRenderer.ART_WIDTH;
            float scaleY = modal.height / FlashCenterUiRenderer.ART_HEIGHT;
            BitmapFont strengthFont = uiAssets.propagandaStrengthFont;
            strengthFont.getData().setScale(scaleY / 4f);
            strengthFont.setColor(MODAL_ORANGE);
            strengthFont.draw(batch, "Propaganda strength: " + game.getHuman().religion + " %",
                    modal.x + 37f * scaleX, modal.y + modal.height - 125.75f * scaleY,
                    98.2f * scaleX, com.badlogic.gdx.utils.Align.right, true);
            // The source HUD paints this glass/border layer above centerScreen.
            batch.setColor(Color.WHITE);
            batch.draw(uiAssets.propagandaFrame, modal.x, modal.y, modal.width, modal.height);
        }
        batch.end();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    private void drawSourceText(String text, float sourceX, float sourceTop,
                                float sourceHeight, Color color) {
        float scaleX = modal.width / FlashCenterUiRenderer.ART_WIDTH;
        float scaleY = modal.height / FlashCenterUiRenderer.ART_HEIGHT;
        float originalScale = font.getScaleX();
        font.getData().setScale(sourceHeight * scaleY
                / Math.max(font.getLineHeight(), 1f));
        font.setColor(color);
        font.draw(batch, text, modal.x + sourceX * scaleX,
                modal.y + modal.height - sourceTop * scaleY);
        font.getData().setScale(originalScale);
        font.setColor(Color.WHITE);
    }

    private void drawSourceTextRight(String text, float sourceRight, float sourceTop,
                                     float sourceHeight, Color color) {
        float scaleX = modal.width / FlashCenterUiRenderer.ART_WIDTH;
        float scaleY = modal.height / FlashCenterUiRenderer.ART_HEIGHT;
        float originalScale = font.getScaleX();
        font.getData().setScale(sourceHeight * scaleY
                / Math.max(font.getLineHeight(), 1f));
        glyphLayout.setText(font, text);
        font.setColor(color);
        font.draw(batch, text, modal.x + sourceRight * scaleX - glyphLayout.width,
                modal.y + modal.height - sourceTop * scaleY);
        font.getData().setScale(originalScale);
        font.setColor(Color.WHITE);
    }

    private void drawSourceTextCentered(String text, float sourceCenter, float sourceTop,
                                        float sourceHeight, Color color) {
        float scaleX = modal.width / FlashCenterUiRenderer.ART_WIDTH;
        float scaleY = modal.height / FlashCenterUiRenderer.ART_HEIGHT;
        float originalScale = font.getScaleX();
        font.getData().setScale(sourceHeight * scaleY / Math.max(font.getLineHeight(), 1f));
        glyphLayout.setText(font, text);
        font.setColor(color);
        font.draw(batch, text, modal.x + sourceCenter * scaleX - glyphLayout.width / 2f,
                modal.y + modal.height - sourceTop * scaleY);
        font.getData().setScale(originalScale);
        font.setColor(Color.WHITE);
    }

    private void drawTechnologyFrame(Rectangle rectangle) {
        shapes.setColor(PANEL_EDGE);
        shapes.rect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
        shapes.setColor(0.65f, 0.77f, 0.81f, 1f);
        shapes.line(rectangle.x + 5f, rectangle.y + rectangle.height - 1.5f,
                rectangle.x + rectangle.width - 5f,
                rectangle.y + rectangle.height - 1.5f);
    }

    private void updateResearchChoiceCards(java.util.List<String> choices) {
        for (int i = 0; i < researchChoiceByCard.length; i++) {
            researchChoiceByCard[i] = null;
        }
        for (String technology : choices) {
            TechnologyCatalog.Definition definition = TechnologyCatalog.byName(technology);
            if (definition == null) {
                continue;
            }
            int card = TechnologyScreenLayout.cardIndexForGroup(definition.group);
            if (card >= 0) {
                researchChoiceByCard[card] = technology;
            }
        }
    }

    private CharSequence selectedResearchCategoriesSummary() {
        selectedResearchSummary.setLength(0);
        boolean hasSelectedCategory = false;
        for (int i = 0; i < researchChoiceByCard.length; i++) {
            String technology = researchChoiceByCard[i];
            if (technology == null || !game.isResearchTechnologySelected(technology)) {
                continue;
            }
            if (hasSelectedCategory) {
                selectedResearchSummary.append("  |  ");
            }
            selectedResearchSummary.append(RESEARCH_GROUP_LABELS[i]);
            hasSelectedCategory = true;
        }
        if (!hasSelectedCategory) {
            selectedResearchSummary.append("SELECT A TECHNOLOGY CATEGORY");
        } else {
            selectedResearchSummary.insert(0, "SELECTED  ");
        }
        return selectedResearchSummary;
    }

    private String researchCostLabel(int costMillions) {
        switch (costMillions) {
            case 5:
                return "$5M";
            case 10:
                return "$10M";
            case 15:
                return "$15M";
            case 20:
                return "$20M";
            case 40:
                return "$40M";
            case 50:
                return "$50M";
            default:
                return "$" + costMillions + "M";
        }
    }

    private void drawFittedCentered(CharSequence text, float centerX, float baselineY,
                                    float targetHeight, float maxWidth) {
        float originalScale = font.getScaleX();
        font.getData().setScale(targetHeight / Math.max(font.getLineHeight(), 1f));
        glyphLayout.setText(font, text);
        if (glyphLayout.width > maxWidth && glyphLayout.width > 0f) {
            font.getData().setScale(font.getScaleX() * maxWidth / glyphLayout.width);
            glyphLayout.setText(font, text);
        }
        font.draw(batch, text, centerX - glyphLayout.width / 2f, baselineY);
        font.getData().setScale(originalScale);
    }

    private void drawProductionModal() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.draw(uiAssets.productionModal, modal.x, modal.y, modal.width, modal.height);
        batch.end();

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawProductionSpecialStatFills();
        shapes.end();

        TextureRegion sliderHandle = flashAnimationAssets == null ? null
                : flashAnimationAssets.getShape(
                        OriginalFlashAnimationAssets.PRODUCTION_SLIDER_HANDLE_SHAPE_ID);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (int i = 0; i < ProductionScreenLayout.sliderCount(); i++) {
            ProductionScreenLayout.setHandleBounds(i,
                    game.getProductionPercent(ProductionScreenLayout.categoryKey(i)),
                    modal, productionSliderMarkers[i]);
            if (sliderHandle != null) {
                Rectangle marker = productionSliderMarkers[i];
                batch.draw(sliderHandle, marker.x, marker.y, marker.width, marker.height);
            }
        }
        batch.end();

        if (sliderHandle == null) {
            shapes.setProjectionMatrix(camera.combined);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            drawProductionSliderHandleFallbacks();
            shapes.end();
        }

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawProductionSummary();
        batch.end();
        if (productionSummaryCount > 0) {
            drawProductionModalButtons();
        }
    }

    private void drawProductionModalButtons() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawOriginalButton(modalCancel);
        drawOriginalButton(modalAccept);
        batch.end();

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawButtonGradient(modalCancel, false);
        drawButtonGradient(modalAccept, false);
        shapes.end();

        batch.begin();
        font.setColor(TITLE_TEXT);
        drawCentered("CANCEL", modalCancel.x + modalCancel.width / 2f,
                modalCancel.y + modalCancel.height / 2f + 3f, 8f);
        drawCentered("ACCEPT", modalAccept.x + modalAccept.width / 2f,
                modalAccept.y + modalAccept.height / 2f + 3f, 8f);
        batch.end();
    }

    private void drawProductionSpecialStatFills() {
        shapes.setColor(0f, 0.54f, 0.89f, 1f);
        for (int i = 0; i < PRODUCTION_SPECIAL_WEAPONS.length; i++) {
            if (productionSpecialProgress[i] <= 0f) {
                continue;
            }
            Rectangle fill = ProductionScreenLayout.setSpecialStatFillBounds(
                    i, productionSpecialProgress[i], modal, productionSpecialStatFills[i]);
            shapes.rect(fill.x, fill.y, fill.width, fill.height);
        }
    }

    private void drawProductionSliderHandleFallbacks() {
        for (int index = 0; index < productionSliderMarkers.length; index++) {
            Rectangle marker = productionSliderMarkers[index];
            float x = marker.x;
            float y = marker.y;
            float width = marker.width;
            float height = marker.height;
            shapes.setColor(0f, 36f / 255f, 60f / 255f, 1f);
            shapes.triangle(x + width / 2f, y + height,
                    x, y, x + width, y);
            shapes.setColor(Color.WHITE);
            shapes.triangle(x + width / 2f, y + height * 0.78f,
                    x + width * 0.13f, y + height * 0.12f,
                    x + width * 0.87f, y + height * 0.12f);
        }
    }

    private void refreshProductionSummary() {
        productionSummaryCount = 0;
        if (game == null || game.getSelectedAction() != GameState.Action.PRODUCTION) {
            productionSummaryTotal = "total estimated amount: 0.000.000";
            return;
        }
        java.util.Map<String, GameState.WeaponState> weapons = game.getHuman().getWeapons();
        for (int i = 0; i < PRODUCTION_SPECIAL_WEAPONS.length; i++) {
            GameState.WeaponState weapon = weapons.get(PRODUCTION_SPECIAL_WEAPONS[i]);
            productionSpecialProgress[i] = weapon == null || weapon.definition.costMillions <= 0
                    ? 0f : weapon.investmentMillions / (float) weapon.definition.costMillions;
        }
        for (int i = 0; i < ProductionScreenLayout.sliderCount(); i++) {
            String category = ProductionScreenLayout.categoryKey(i);
            int percent = game.getProductionPercent(category);
            if (percent <= 0) {
                continue;
            }
            productionSummaryCategories[productionSummaryCount] = category;
            double amountMillions = game.getProductionSliderBudgetMillions(category);
            long amountDollars = Math.round(amountMillions * 1_000_000d);
            productionSummaryAmounts[productionSummaryCount] = percent
                    + " % ( $ " + formatProductionDollars(amountDollars) + " )";
            productionSummaryCount++;
        }
        productionSummaryTotal = "total estimated amount: "
                + formatMillions(game.getSelectedProductionSliderDisplayTotalBudgetMillions());
    }

    private void drawProductionSummary() {
        float scaleY = modal.height / ProductionScreenLayout.ART_HEIGHT;
        float originalScale = font.getScaleX();
        font.getData().setScale(0.64f * scaleY);
        font.setColor(Color.WHITE);

        int visibleLines = Math.min(5, productionSummaryCount);
        ProductionScreenLayout.setSummaryCategoryBounds(modal, productionSummaryCategoryBounds);
        batch.flush();
        setScissor(productionSummaryCategoryBounds);
        for (int i = 0; i < visibleLines; i++) {
            font.draw(batch, productionSummaryCategories[i],
                    ProductionScreenLayout.summaryCategoryLeft(modal),
                    ProductionScreenLayout.summaryRowTop(modal, i, false));
        }
        batch.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        ProductionScreenLayout.setSummaryAmountBounds(modal, productionSummaryAmountBounds);
        setScissor(productionSummaryAmountBounds);
        for (int i = 0; i < visibleLines; i++) {
            String amount = productionSummaryAmounts[i];
            productionSummaryLayout.setText(font, amount);
            font.draw(batch, amount,
                    ProductionScreenLayout.summaryAmountRight(modal) - productionSummaryLayout.width,
                    ProductionScreenLayout.summaryRowTop(modal, i, true));
        }
        batch.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);

        if (productionSummaryCount > 0) {
            font.setColor(0f, 36f / 255f, 60f / 255f, 1f);
            productionSummaryLayout.setText(font, productionSummaryTotal);
            ProductionScreenLayout.setSummaryTotalBounds(modal, productionSummaryTotalBounds);
            batch.flush();
            setScissor(productionSummaryTotalBounds);
            font.draw(batch, productionSummaryTotal,
                    ProductionScreenLayout.summaryTotalRight(modal) - productionSummaryLayout.width,
                    ProductionScreenLayout.summaryTotalTop(modal));
            batch.flush();
            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        }
        font.setColor(Color.WHITE);
        font.getData().setScale(originalScale);
    }

    private static String formatProductionDollars(long dollars) {
        String digits = Long.toString(dollars);
        StringBuilder formatted = new StringBuilder(digits.length() + digits.length() / 3);
        int firstGroup = digits.length() % 3;
        if (firstGroup == 0) {
            firstGroup = 3;
        }
        formatted.append(digits, 0, firstGroup);
        for (int offset = firstGroup; offset < digits.length(); offset += 3) {
            formatted.append('.').append(digits, offset, offset + 3);
        }
        return formatted.toString();
    }

    private boolean updateProductionSlider(int sliderIndex, float worldX) {
        return updateProductionSlider(sliderIndex, worldX, 0f);
    }

    private boolean updateProductionSlider(int sliderIndex, float worldX,
                                           float handleGrabOffsetX) {
        if (sliderIndex < 0 || sliderIndex >= ProductionScreenLayout.sliderCount()
                || game == null || game.getSelectedAction() != GameState.Action.PRODUCTION) {
            return false;
        }
        String category = ProductionScreenLayout.categoryKey(sliderIndex);
        game.selectProductionCategory(category);
        int remainingPercent = game.getSelectedProductionMaximumPercent();
        int percent = ProductionScreenLayout.percentAt(
                sliderIndex, modal, worldX, handleGrabOffsetX, remainingPercent);
        boolean updated = game.configureProductionSlider(category, percent);
        if (updated) {
            refreshProductionSummary();
        } else {
            sourceActionMenu.showEconomyWarning(game, EconomyWarningText.PRODUCTION_LOCKED);
        }
        Gdx.graphics.requestRendering();
        return updated;
    }

    private void drawSettingsMenu() {
        settingsMenu.layout(mapFrame);
        Rectangle panel = settingsMenu.panel;
        float scale = settingsMenu.getScale();
        if (scale == 0f) return;
        boolean confirmation = settingsMenu.isConfirmingLoad();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.28f, 0.46f, 0.54f, 1f);
        shapes.rect(panel.x, panel.y, panel.width, panel.height);
        shapes.setColor(0.036f, 0.081f, 0.11f, 1f);
        shapes.rect(panel.x + scale, panel.y + scale, panel.width - 2f * scale,
                panel.height - 2f * scale);
        if (confirmation) {
            drawSettingsButtonFill(settingsMenu.cancel, true, false);
            drawSettingsButtonFill(settingsMenu.confirm, settingsMenu.canLoad(), true);
        } else {
            drawSettingsButtonFill(settingsMenu.save, settingsMenu.canSave(), false);
            drawSettingsButtonFill(settingsMenu.load, settingsMenu.canLoad(), false);
            drawSettingsButtonFill(settingsMenu.sound, true, false);
            drawSettingsButtonFill(settingsMenu.close, true, false);
        }
        shapes.end();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.setColor(Color.WHITE);
        font.setColor(Color.WHITE);
        float centerX = panel.x + panel.width / 2f;
        float top = panel.y + panel.height;
        drawCentered(confirmation ? "LOAD SAVED GAME?" : "SETTINGS", centerX,
                top - 14f * scale, 19f * scale);
        if (confirmation) {
            if (game != null) {
                drawCentered("This replaces your current progress.", centerX,
                        top - 74f * scale, 14f * scale);
            }
            drawSettingsButtonLabel(settingsMenu.cancel, "CANCEL", true);
            drawSettingsButtonLabel(settingsMenu.confirm, "LOAD GAME", settingsMenu.canLoad());
        } else {
            drawCentered(settingsMenu.getStatus(), centerX, top - 39f * scale, 11f * scale);
            drawSettingsButtonLabel(settingsMenu.save, "SAVE GAME", settingsMenu.canSave());
            drawSettingsButtonLabel(settingsMenu.load, "LOAD GAME", settingsMenu.canLoad());
            drawSettingsButtonLabel(settingsMenu.sound, audio.isSoundEnabled() ? "SOUND: ON" : "SOUND: OFF", true);
            drawSettingsButtonLabel(settingsMenu.close, "CLOSE", true);
        }
        font.setColor(Color.WHITE);
        batch.end();
    }

    private void drawSettingsButtonFill(Rectangle button, boolean enabled, boolean primary) {
        if (primary && enabled) shapes.setColor(0.16f, 0.34f, 0.42f, 1f);
        else if (enabled) shapes.setColor(0.10f, 0.20f, 0.26f, 1f);
        else shapes.setColor(0.07f, 0.13f, 0.17f, 1f);
        shapes.rect(button.x, button.y, button.width, button.height);
    }

    private void drawSettingsButtonLabel(Rectangle button, String text, boolean enabled) {
        font.setColor(enabled ? Color.WHITE : Color.GRAY);
        float scale = settingsMenu.getScale();
        drawCentered(text, button.x + button.width / 2f,
                button.y + button.height / 2f + 6f * scale, 15f * scale);
    }

    private void activateSettingsAt(float x, float y) {
        switch (settingsMenu.hit(x, y)) {
            case SAVE:
                try {
                    gameSaveStore.save(game);
                    settingsMenu.saved(game.getTurn());
                } catch (IOException | IllegalArgumentException failure) {
                    Gdx.app.error("Game save", "Unable to save match", failure);
                    settingsMenu.message("Save failed. Previous save kept.");
                }
                break;
            case LOAD:
                settingsMenu.requestLoad();
                break;
            case CONFIRM_LOAD:
                try {
                    // Decode first: an invalid save must not replace the live match.
                    GameState loaded = gameSaveStore.load();
                    loaded.cancelSelection(); // Unaccepted menu drafts are not queued orders.
                    resetMatchPresentation();
                    activateGame(loaded);
                    sourceActionMenu.reset();
                    settingsMenu.open(true, true);
                    settingsMenu.message("Loaded turn " + loaded.getTurn() + ".");
                } catch (IOException | IllegalArgumentException failure) {
                    Gdx.app.error("Game save", "Unable to load match", failure);
                    settingsMenu.message("Cannot load this save. Game unchanged.");
                }
                break;
            case SOUND:
                audio.toggleSound();
                break;
            case CANCEL_LOAD:
                settingsMenu.back();
                break;
            case CLOSE:
                settingsMenu.close();
                break;
            default:
                break;
        }
        skipNextFrameDelta = true;
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
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        endgameRenderer.draw(batch, endgameContentBounds, outcomeSequence,
                outcomePersonalBestText, font);
        batch.end();
    }

    private void disposeEndgameResources() {
        if (endgameRenderer != null) {
            endgameRenderer.dispose();
            endgameRenderer = null;
        }
        if (endgameAssets != null) {
            endgameAssets.dispose();
            endgameAssets = null;
        }
    }

    private void drawWrappedCentered(String text, float x, float baselineY,
                                     float targetWidth, float targetHeight) {
        float originalScale = font.getScaleX();
        float scale = targetHeight / Math.max(font.getLineHeight(), 1f);
        font.getData().setScale(scale);
        font.draw(batch, text, x, baselineY, targetWidth, Align.center, true);
        font.getData().setScale(originalScale);
    }

    private void beginNewGameSetup() {
        resetMatchPresentation();
        setup = setup == null ? new GameSetup(System.nanoTime()) : setup.createNextSetup();
        setupLeaderListStart = 0;
        setupLeaderName = "";
        setupMessage = "SELECT ONE TO FOUR OPPONENTS TO BEGIN";
        showSetupMessage = false;
    }

    private void resetMatchPresentation() {
        blurHiddenHelpInput();
        helpState.resetMatch();
        audio.finishSatelliteAudio();
        audio.finishAircraftAudio();
        audio.finishAntiDefenseAudio();
        stopScoreCounterSound();
        audio.finishBattleAmbient();
        audio.finishBattleActionSounds();
        outcomeSequence.reset();
        disposeEndgameResources();
        outcomePersonalBestText = "";
        modalOpen = false;
        showExitPromptSeconds = 0f;
        accumulator = 0f;
        game = null;
        displayGame = null;
        battlefieldDisplay.clear();
        battleRecordClock.reset();
        battleActionFrames.reset();
        battleInterfaceFrames.reset();
        sourceStoppedClips.reset();
        battlePresentationRandom = null;
        battleTicker.reset();
        hiddenBattleEffects.finish();
        battlePresentationRecords = Collections.emptyList();
        laidOutInstruction = null;
        battleAnimations.clear();
        sourceUnitPlayback.reset();
        soldierBattleAnimations.clear();
        apacheMarineAnimations.clear();
        chinookTankAnimations.clear();
        suicideBomberAnimations.clear();
        clearRocketBattleAnimations();
        sourceRocketBindings.clear();
        sourceInterceptedAircraftBindings.clear();
        sourceAircraftTrails.clear();
        submarineBattleAnimations.clear();
        clearCarrierPlaneAnimations();
        clearStandalonePlaneAnimations();
        antiDefenseTrackingAnimations.clear();
        clearSourceAirDefenses();
        satelliteBattleAnimations.clear();
        satelliteAudioOwners.clear();
        satelliteFlightCuePlayed = false;
        pendingBattleAnimations.clear();
    }

    private void drawSourceActionMenu() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        sourceActionMenuRenderer.draw(batch, modal, game, sourceActionMenu, menuPortraitDrawer, turnControlRenderer,
                uiAssets.propagandaFrame, uiAssets.propagandaStrengthFont);
        batch.end();
    }

    private void drawDiplomacyResponseModal() {
        drawSourceActionMenu();
    }

    private void drawLegacyDiplomacyResponseModal() {
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

    private void preparePhonePanels() {
        preparePhonePanel(logoPanel);
        preparePhonePanel(centerHeader);
        preparePhonePanel(toolbarPanel);
        preparePhonePanel(stockPanel);
        preparePhonePanel(messagePanel);
        preparePhonePanel(actionPanel);
        int visiblePlayers = 0;
        for (int id = 1; id < 5; id++) {
            GameState.PlayerState player = game.getPlayerState(id);
            if (player.alive || player.getLeaderId() >= 0) visiblePlayers++;
        }
        if (visiblePlayers > 0) {
            phonePanelRenderer.prepare(Math.round(Math.min(84f,
                    (centerHeader.width - 16f) / visiblePlayers) - 3f),
                    Math.round(Math.min(51f, centerHeader.height - 9f)));
        }
    }

    private void preparePhonePanel(Rectangle bounds) {
        phonePanelRenderer.prepare(Math.round(bounds.width), Math.round(bounds.height));
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

    private void drawSetupPanelEdges(Rectangle rectangle) {
        shapes.setColor(0.015f, 0.035f, 0.045f, 1f);
        shapes.rect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
        shapes.setColor(0.42f, 0.57f, 0.63f, 1f);
        shapes.rect(rectangle.x + 1.5f, rectangle.y + 1.5f,
                rectangle.width - 3f, rectangle.height - 3f);
        shapes.setColor(0.025f, 0.07f, 0.09f, 1f);
        shapes.rect(rectangle.x + 3.5f, rectangle.y + 3.5f,
                rectangle.width - 7f, rectangle.height - 7f);
        shapes.setColor(0.68f, 0.78f, 0.82f, 1f);
        shapes.line(rectangle.x + 9f, rectangle.y + rectangle.height - 3f,
                rectangle.x + rectangle.width - 9f,
                rectangle.y + rectangle.height - 3f);
        shapes.line(rectangle.x + 3f, rectangle.y + 9f,
                rectangle.x + 3f, rectangle.y + rectangle.height - 9f);
        shapes.setColor(0.025f, 0.055f, 0.07f, 1f);
        shapes.line(rectangle.x + 9f, rectangle.y + 3f,
                rectangle.x + rectangle.width - 9f, rectangle.y + 3f);
        shapes.line(rectangle.x + rectangle.width - 3f, rectangle.y + 9f,
                rectangle.x + rectangle.width - 3f,
                rectangle.y + rectangle.height - 9f);

        float boltInset = Math.min(8f, Math.min(rectangle.width, rectangle.height) * 0.12f);
        float boltRadius = Math.min(3.2f, Math.min(rectangle.width, rectangle.height) * 0.08f);
        shapes.setColor(0.08f, 0.16f, 0.19f, 1f);
        shapes.circle(rectangle.x + boltInset, rectangle.y + boltInset, boltRadius + 1f, 12);
        shapes.circle(rectangle.x + rectangle.width - boltInset,
                rectangle.y + boltInset, boltRadius + 1f, 12);
        shapes.circle(rectangle.x + boltInset,
                rectangle.y + rectangle.height - boltInset, boltRadius + 1f, 12);
        shapes.circle(rectangle.x + rectangle.width - boltInset,
                rectangle.y + rectangle.height - boltInset, boltRadius + 1f, 12);
        shapes.setColor(0.77f, 0.85f, 0.87f, 1f);
        shapes.circle(rectangle.x + boltInset - 0.6f,
                rectangle.y + boltInset + 0.6f, boltRadius * 0.57f, 12);
        shapes.circle(rectangle.x + rectangle.width - boltInset - 0.6f,
                rectangle.y + boltInset + 0.6f, boltRadius * 0.57f, 12);
        shapes.circle(rectangle.x + boltInset - 0.6f,
                rectangle.y + rectangle.height - boltInset + 0.6f,
                boltRadius * 0.57f, 12);
        shapes.circle(rectangle.x + rectangle.width - boltInset - 0.6f,
                rectangle.y + rectangle.height - boltInset + 0.6f,
                boltRadius * 0.57f, 12);
    }

    private void drawSetupButtonEdges(Rectangle rectangle, boolean selected) {
        shapes.setColor(0.06f, 0.13f, 0.16f, 1f);
        shapes.rect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
        shapes.setColor(selected ? 0.92f : 0.47f,
                selected ? 0.74f : 0.62f, selected ? 0.25f : 0.68f, 1f);
        shapes.rect(rectangle.x + 1f, rectangle.y + 1f,
                rectangle.width - 2f, rectangle.height - 2f);
        shapes.setColor(0.045f, 0.095f, 0.12f, 1f);
        shapes.rect(rectangle.x + 3f, rectangle.y + 3f,
                rectangle.width - 6f, rectangle.height - 6f);
        shapes.setColor(0.72f, 0.81f, 0.84f, 1f);
        shapes.line(rectangle.x + 4f, rectangle.y + rectangle.height - 3f,
                rectangle.x + rectangle.width - 4f,
                rectangle.y + rectangle.height - 3f);
        shapes.setColor(0.02f, 0.05f, 0.07f, 1f);
        shapes.line(rectangle.x + 4f, rectangle.y + 3f,
                rectangle.x + rectangle.width - 4f, rectangle.y + 3f);
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
        font.draw(batch, glyphLayout, centerX - glyphLayout.width / 2f, baselineY);
        font.getData().setScale(originalScale);
    }

    private String slotLabel(int slot) {
        GameState.Action action = game.getQueuedAction(slot);
        String prefix = slot == 0 ? "FIRST: " : "SECOND: ";
        return action == null ? prefix + "EMPTY" : prefix + action.label.toUpperCase();
    }

    private void updateMessageHistoryLayout() {
        List<String> messages = game.getMessages();
        int messageCount = messages.size();
        String latest = game.getLatestMessage();
        int currentTurn = game.getTurn();
        boolean contentChanged = messageCount != cachedMessageCount
                || currentTurn != cachedMessageTurn
                || !latest.equals(cachedLatestMessage);
        boolean widthChanged = Math.abs(messageViewport.width - messageLayoutWidth) > 0.01f;
        if (!contentChanged && !widthChanged) {
            return;
        }

        if (contentChanged) {
            StringBuilder text = new StringBuilder();
            for (String message : messages) {
                if (message == null || message.isEmpty()) {
                    continue;
                }
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(message.replaceAll("(?i)<br\\s*/?>", "\n"));
            }
            cachedMessageText = text.length() == 0
                    ? "Choose two actions, then confirm." : text.toString();
            cachedMessageCount = messageCount;
            cachedMessageTurn = currentTurn;
            cachedLatestMessage = latest;
        }

        BitmapFont messageFont = messagePanelRenderer.getFont();
        messageHistoryLayout.setText(messageFont, cachedMessageText, MessagePanelRenderer.TEXT_COLOR,
                messageViewport.width, Align.left, true);
        messageLineHeight = Math.max(1f, messageFont.getLineHeight());
        int visibleLines = MessagePanelLayout.visibleLineCount(messageViewport.height, messageLineHeight);
        int totalLines = Math.max(1,
                Math.round(messageHistoryLayout.height / messageLineHeight));
        messageHistoryScroll.setLineRange(totalLines, visibleLines, contentChanged);
        messageLayoutWidth = messageViewport.width;
    }

    private static String formatMillions(int millions) {
        return String.format(java.util.Locale.US, "%,d.000.000", millions);
    }

    private static String sourceMillions(int millions) {
        return millions + ".000.000";
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
        if (settingsMenu.isOpen()) return GESTURE_NONE;
        if (outcomeSequence.isEndgameVisible()) return GESTURE_NONE;
        if (!helpState.isOpen() && modalOpen && game != null && !sourceActionMenu.isEconomyWarning(game)
                && game.getSelectedAction() == GameState.Action.PRODUCTION) {
            for (int i = 0; i < productionSliderHitTargets.length; i++) {
                if (productionSliderHitTargets[i].contains(x, y)) {
                    return GESTURE_PRODUCTION;
                }
            }
        }
        if (navViewport.contains(x, y)) {
            return GESTURE_NAV;
        }
        if (equipmentViewport.contains(x, y)) {
            return GESTURE_EQUIPMENT;
        }
        if (messageViewport.contains(x, y) && canScrollMessageHistory()) {
            return GESTURE_MESSAGE;
        }
        return GESTURE_NONE;
    }

    private boolean canScrollMessageHistory() {
        return game != null && !helpState.isOpen() && !modalOpen
                && !ActionScreenLayout.hasInlineActionControls(game.getSelectedAction());
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
            sourceActionMenu.clearEconomyWarning();
            for (int slot = 0; slot < actionSlots.length; slot++) {
                if (game.getQueuedAction(slot) == action) {
                    activateActionSlot(slot);
                    return;
                }
            }
            if (!action.isPorted()) {
                audio.play(OriginalAudio.Cue.ACTION_SWITCH);
                game.cancelSelection();
                continue;
            }
            if (game.getSelectedAction() == action) {
                if (action == GameState.Action.PRODUCTION) {
                    modalOpen = !modalOpen;
                    audio.play(modalOpen ? OriginalAudio.Cue.ACTION_OPEN
                            : OriginalAudio.Cue.ACTION_CLOSE);
                    return;
                }
                audio.play(OriginalAudio.Cue.ACTION_SWITCH_OFF);
                game.cancelSelection();
                selectedEquipment = "";
                modalOpen = false;
                audio.play(OriginalAudio.Cue.ACTION_CLOSE);
            } else {
                audio.play(OriginalAudio.Cue.ACTION_SWITCH);
                game.selectAction(action);
                if (action == GameState.Action.RESEARCH) {
                    researchPage = 0;
                } else if (action == GameState.Action.PRODUCTION) {
                    refreshProductionSummary();
                }
                if (SourceActionMenuState.supports(game.getSelectedAction())) {
                    sourceActionMenu.begin(game);
                    sourceActionInstruction.onSelectionUpdate(game.getSelectedAction(),
                            sourceActionMenu.getKey());
                    selectedEquipment = "";
                }
                modalOpen = SourceActionMenuState.supports(game.getSelectedAction())
                        ? !sourceActionMenu.isMapSelection()
                        : ActionScreenLayout.opensCenterDialog(game.getSelectedAction());
                if (modalOpen) {
                    audio.play(OriginalAudio.Cue.ACTION_OPEN);
                }
            }
            return;
        }
    }

    private void acceptProductionSelection() {
        if (game.acceptSelectedAction()) {
            modalOpen = false;
            audio.play(OriginalAudio.Cue.ACTION_ACCEPT);
        }
    }

    private void activateEquipmentRow(float x, float y) {
        for (int i = 0; i < equipmentRows.length; i++) {
            if (!isEquipmentHeading(i) && equipmentRows[i].contains(x, y)) {
                String weapon = EQUIPMENT_WEAPON_NAMES[i];
                GameState.Action automatic = SourceActionMenuState.actionForEquipment(game, weapon);
                if (automatic != null) {
                    game.selectAction(automatic);
                    sourceActionMenu.begin(game);
                    sourceActionInstruction.onSelectionUpdate(game.getSelectedAction(),
                            sourceActionMenu.getKey());
                    audio.play(OriginalAudio.Cue.ACTION_SWITCH);
                }
                if (SourceActionMenuState.supports(game.getSelectedAction()) && weapon != null) {
                    boolean chosen = sourceActionMenu.chooseEquipment(game, weapon);
                    sourceActionInstruction.onSelectionUpdate(game.getSelectedAction(),
                            sourceActionMenu.getKey());
                    if (chosen) {
                        String selected = sourceActionMenu.getSelectedEquipment();
                        selectedEquipment = selected == null ? "" : selected;
                        if (selected != null) audio.play(OriginalAudio.Cue.STOCK_SELECT);
                    }
                    modalOpen = !sourceActionMenu.isMapSelection();
                    if (automatic != null) {
                        if (modalOpen) audio.play(OriginalAudio.Cue.ACTION_OPEN);
                    }
                } else {
                    selectedEquipment = weapon;
                    audio.play(OriginalAudio.Cue.STOCK_SELECT);
                }
                return;
            }
        }
    }

    private void syncSelectedAttackEquipment() {
        String weapon = game.getSelectedAttackWeapon();
        if (weapon == null) {
            return;
        }
        for (int i = 0; i < EQUIPMENT_WEAPON_NAMES.length; i++) {
            if (weapon.equals(EQUIPMENT_WEAPON_NAMES[i])) {
                selectedEquipment = weapon;
                return;
            }
        }
    }

    /** Source portrait onRollOver writes leaderText; onRollOut clears it. */
    private void updateSetupLeaderName(float x, float y) {
        String name = "";
        for (int i = 0; i < setupLayout.leaderCards.length; i++) {
            if (setupLayout.leaderCards[i].contains(x, y)) {
                int id = SetupScreenLayout.leaderIdAt(setupLeaderListStart, i,
                        GameSetup.leaders().size());
                name = GameSetup.leader(id).name;
                break;
            }
        }
        if (!name.equals(setupLeaderName)) {
            setupLeaderName = name;
            Gdx.graphics.requestRendering();
        }
    }

    private void activateSetupAt(float x, float y) {
        updateSetupLeaderName(x, y);
        for (int i = 0; i < setupLayout.difficultyButtons.length; i++) {
            if (setupLayout.difficultyButtons[i].contains(x, y)) {
                setup.setDifficulty(i);
                showSetupMessage = false;
                return;
            }
        }
        for (int i = 0; i < setupLayout.colorSwatches.length; i++) {
            if (setupLayout.colorSwatches[i].contains(x, y)) {
                setup.setPlayerColor(i + 1);
                showSetupMessage = false;
                return;
            }
        }
        for (int i = 0; i < setupLayout.buildingButtons.length; i++) {
            if (setupLayout.buildingButtons[i].contains(x, y)) {
                setup.setStartingBuilding(i + 1);
                showSetupMessage = false;
                return;
            }
        }
        if (setupLayout.leaderPrevious.contains(x, y)) {
            setupLeaderListStart = Math.floorMod(setupLeaderListStart - 1,
                    GameSetup.leaders().size());
            showSetupMessage = false;
            return;
        }
        if (setupLayout.leaderNext.contains(x, y)) {
            setupLeaderListStart = SetupScreenLayout.nextLeaderStart(setupLeaderListStart,
                    GameSetup.leaders().size());
            showSetupMessage = false;
            return;
        }
        for (int i = 0; i < setupLayout.leaderCards.length; i++) {
            if (setupLayout.leaderCards[i].contains(x, y)) {
                int leaderId = SetupScreenLayout.leaderIdAt(setupLeaderListStart, i,
                        GameSetup.leaders().size());
                setup.selectCandidateLeader(leaderId);
                if (!setup.getSelectedOpponentIds().contains(leaderId)) {
                    setup.toggleOpponent(leaderId);
                }
                showSetupMessage = false;
                return;
            }
        }
        for (int i = 0; i < setupLayout.opponentSlots.length; i++) {
            if (setupLayout.opponentSlots[i].contains(x, y)) {
                if (setup.removeOpponentAt(i)) {
                    showSetupMessage = false;
                }
                return;
            }
        }
        if (setupLayout.startButton.contains(x, y)) {
            if (setup.canStart()) {
                activateGame(new GameState(setup));
            } else {
                setupMessage = "SELECT AT LEAST ONE RIVAL BEFORE STARTING";
                showSetupMessage = true;
            }
        }
    }

    private void activateGame(GameState nextGame) {
        initializeGameplayAssets();
        game = nextGame;
        cachedMessageCount = -1; // A different match can have the same turn and latest message.
        blurHiddenHelpInput();
        helpState.resetMatch();
        sourceActionInstruction.resetWelcome();
        laidOutInstruction = null;
        actionSlotAnimation.reset();
        confirmTurnAnimation.reset();
        turnActionDisplay.reset();
        cityRenderer.reset(game);
        flagRenderer.reset();
        battleAnimations.clear();
        sourceUnitPlayback.reset();
        actionBuildingAnimations.clear();
        battleActionFrames.reset();
        turnPlaybackSequence.reset();
        battleRecordClock.reset();
        battleInterfaceFrames.reset();
        sourceStoppedClips.reset();
        battlePresentationRandom = null;
        battleTicker.reset();
        hiddenBattleEffects.finish();
        battlePresentationRecords = Collections.emptyList();
        soldierBattleAnimations.clear();
        apacheMarineAnimations.clear();
        chinookTankAnimations.clear();
        suicideBomberAnimations.clear();
        clearRocketBattleAnimations();
        sourceRocketBindings.clear();
        sourceInterceptedAircraftBindings.clear();
        sourceAircraftTrails.clear();
        submarineBattleAnimations.clear();
        clearCarrierPlaneAnimations();
        clearStandalonePlaneAnimations();
        antiDefenseTrackingAnimations.clear();
        clearSourceAirDefenses();
        audio.finishAntiDefenseAudio();
        satelliteBattleAnimations.clear();
        satelliteAudioOwners.clear();
        satelliteFlightCuePlayed = false;
        pendingBattleAnimations.clear();
        modalOpen = false;
        accumulator = 0f;
        audio.play(OriginalAudio.Cue.ACTION_ACCEPT);
    }

    /** Loads city textures on demand so the title and setup screens can start quickly. */
    private void initializeGameplayAssets() {
        startFlashAssetLoading();
        if (cityAssets == null) {
            cityAssets = new OriginalCityAssets(true);
        }
        cityAssets.loadGameplayFrames();
        if (cityRenderer == null) {
            cityRenderer = new BattlefieldCityRenderer(cityAssets, flashAnimationAssets);
        }
        if (flagRenderer == null) {
            flagRenderer = new BattlefieldFlagRenderer(flashAnimationAssets);
        }
        if (terrainRenderer == null) {
            terrainRenderer = new BattlefieldTerrainRenderer();
        }
    }

    /** Defers large Flash timeline decoding until the player leaves the title splash. */
    private void startFlashAssetLoading() {
        if (flashAnimationAssets != null) {
            return;
        }
        flashAnimationAssets = new OriginalFlashAnimationAssets();
        explosionRenderer = new BattleExplosionRenderer(flashAnimationAssets);
        battleUnitRenderer = new FlashBattleUnitRenderer(flashAnimationAssets);
        battleAnchors = FlashBattleRoutes.parseAnchors(
                Gdx.files.internal("original/runtime/battle_anchors.json")
                        .readString("UTF-8"));
    }

    private void requestSplashContinue() {
        if (!splashState.isVisible()) return;
        splashContinuePending = true;
        startFlashAssetLoading();
        Gdx.graphics.requestRendering();
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
        PreparedShipStrikeSelection.Result result =
                PreparedShipStrikeSelection.tryAddTargetCity(game, preparedTargetPlayer,
                        preparedCityIndex, payload);
        if (result == PreparedShipStrikeSelection.Result.ADDED) {
            game.selectBattlefieldCity(preparedTargetPlayer, preparedCityIndex);
            audio.play(OriginalAudio.Cue.UNIT_SELECT);
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
        int selectedCity = cityRenderer == null ? -1
                : cityRenderer.hitTest(x, y, mapBounds, game);
        if (selectedCity >= 0) {
            int owner = selectedCity / BattlefieldCityLayout.CITIES_PER_PLAYER;
            sourceActionInstruction.onCityAttempt(owner, game.getHuman().hasCeasefireWith(owner));
        }
        if (selectedCity >= 0 && game.selectBattlefieldCity(
                selectedCity / BattlefieldCityLayout.CITIES_PER_PLAYER,
                selectedCity % BattlefieldCityLayout.CITIES_PER_PLAYER)) {
            audio.play(OriginalAudio.Cue.UNIT_SELECT);
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
            audio.play(OriginalAudio.Cue.UNIT_SELECT);
        }
    }

    private void activateSourceActionMenu(float x, float y, boolean pending) {
        if (!pending && game.getSelectedAction() == GameState.Action.PROPAGANDA
                && sourceActionMenu.isMapSelection() && actionFooterButtons[1].contains(x, y)) {
            sourceActionMenu.cancel(game);
            modalOpen = false;
            audio.play(OriginalAudio.Cue.ACTION_CLOSE);
            return;
        }
        String control = sourceActionMenuRenderer.controlAt(modal, x, y, game, sourceActionMenu);
        if (control != null) {
            if (control.endsWith("btnOK")) {
                sourceActionMenu.dismissError(game);
                sourceActionInstruction.onSelectionUpdate(game.getSelectedAction(),
                        sourceActionMenu.getKey());
            }
            else if ("btnAccept".equals(control)) {
                boolean accepted = pending ? game.respondToDiplomacyProposal(true) : sourceActionMenu.accept(game);
                if (accepted) {
                    selectedEquipment = "";
                    if (pending) game.cancelSelection();
                    modalOpen = false;
                    audio.play(OriginalAudio.Cue.ACTION_ACCEPT);
                } else if (pending) {
                    sourceActionMenu.showMoneyError(game);
                }
            } else if ("btnCancel".equals(control)) {
                boolean closed = pending ? game.respondToDiplomacyProposal(false) : sourceActionMenu.cancel(game);
                if (closed) {
                    selectedEquipment = "";
                    if (pending) game.cancelSelection();
                    modalOpen = false;
                    audio.play(OriginalAudio.Cue.ACTION_CLOSE);
                }
            } else if (!pending && control.startsWith("leader")) {
                if (sourceActionMenu.chooseLeader(game, control.charAt(6) - '0'))
                    audio.play(OriginalAudio.Cue.UNIT_SELECT);
                sourceActionInstruction.onSelectionUpdate(game.getSelectedAction(),
                        sourceActionMenu.getKey());
            } else if (!pending && control.matches("btn[1-6]")) {
                int index = control.charAt(3) - '1';
                boolean chosen = game.getSelectedAction() == GameState.Action.DIPLOMACY
                        ? sourceActionMenu.chooseOption(game, index)
                        : sourceActionMenu.chooseSpyBuilding(game, index);
                if (chosen) audio.play(OriginalAudio.Cue.STOCK_SELECT);
                sourceActionInstruction.onSelectionUpdate(game.getSelectedAction(),
                        sourceActionMenu.getKey());
            }
            return;
        }
        if (!pending && sourceActionMenu.isMapSelection() && mapBounds.contains(x, y)) {
            int address = cityRenderer == null ? -1
                    : cityRenderer.hitTest(x, y, mapBounds, game);
            if (address >= 0) {
                int owner = address / BattlefieldCityLayout.CITIES_PER_PLAYER;
                audio.play(OriginalAudio.Cue.UNIT_SELECT);
                boolean chosen = sourceActionMenu.chooseCity(game, owner,
                        address % BattlefieldCityLayout.CITIES_PER_PLAYER);
                if (chosen) {
                    sourceActionInstruction.onSelectionUpdate(game.getSelectedAction(),
                            sourceActionMenu.getKey());
                    modalOpen = !sourceActionMenu.isMapSelection();
                    String selected = sourceActionMenu.getSelectedEquipment();
                    selectedEquipment = selected == null ? "" : selected;
                    if (modalOpen) audio.play(OriginalAudio.Cue.ACTION_OPEN);
                }
                sourceActionInstruction.onCityAttempt(owner,
                        game.getHuman().hasCeasefireWith(owner));
            }
        }
    }

    private void closeHelpSelection() {
        blurHiddenHelpInput();
        helpState.close();
        game.cancelSelection();
        sourceActionMenu.clearEconomyWarning();
        selectedEquipment = "";
        modalOpen = false;
    }

    private void activateAt(float x, float y) {
        if (settingsMenu.isOpen()) {
            activateSettingsAt(x, y);
            return;
        }
        if (!splashState.isVisible() && settingsButton.contains(x, y)
                && (game == null || !MatchOutcomePresentation.isFinished(game.getMatchStatus()))) {
            blurHiddenHelpInput();
            showExitPromptSeconds = 0f;
            settingsMenu.open(gameSaveStore != null && game != null && !isTurnInputBlocked(),
                    gameSaveStore != null && gameSaveStore.hasSave());
            settingsMenu.layout(mapFrame);
            return;
        }
        if (game == null) {
            activateSetupAt(x, y);
            return;
        }
        if (MatchOutcomePresentation.isFinished(game.getMatchStatus())) {
            if (outcomeSequence.isEndgameVisible()) {
                if (outcomeSequence.isScoreStarted() && endgameRenderer != null
                        && endgameRenderer.isReady()
                        && endgameRenderer.layout().hitNewGame(x, y)) {
                    beginNewGameSetup();
                    audio.play(OriginalAudio.Cue.ACTION_CLOSE);
                }
            }
            return;
        }
        if (muteButton.contains(x, y)) {
            audio.toggleOutputMuted();
            return;
        }
        if (helpButton.contains(x, y)) {
            // The reference replaces the center view without cancelling an
            // unaccepted action or touching already reserved turn orders.
            blurHiddenHelpInput();
            helpState.open();
            return;
        }
        if (helpState.isOpen()) {
            helpRenderer.prepare(helpState.getTopic());
            if (navViewport.contains(x, y)) {
                for (Rectangle row : navRows) {
                    if (row.contains(x, y)) {
                        closeHelpSelection();
                        activateNavigationRow(x, y);
                        break;
                    }
                }
                return;
            } else if (equipmentViewport.contains(x, y)) {
                // Help is a selected source interface action, so a stock
                // button cannot invoke the idle automatic-action shortcut.
                return;
            } else {
                boolean turnControl = confirmButton.contains(x, y)
                        && game.getQueuedActionCount() == 2
                        && confirmTurnAnimation.getFrame() == ConfirmTurnAnimation.OPEN_FRAME;
                for (int slot = 0; slot < actionSlots.length; slot++) {
                    turnControl |= actionSlots[slot].contains(x, y)
                            && game.getQueuedAction(slot) != null;
                }
                if (!turnControl) {
                    if (helpState.isVisible()) {
                        if (activateHiddenHelp(x, y)) return;
                        if (helpMapButton.contains(x, y)) {
                            blurHiddenHelpInput();
                            helpState.hide();
                        } else helpState.activate(helpRenderer.controlAt(mapBounds, x, y));
                    } else if (actionFooterButtons[0].contains(x, y)) {
                        helpState.show();
                    } else if (actionFooterButtons[1].contains(x, y)) {
                        // Route the phone footer to Cancel directly: the
                        // reference adapter searches for an absent authored
                        // btnCancel on help pages, leaving its footer inert.
                        closeHelpSelection();
                    }
                    return;
                }
                closeHelpSelection();
            }
        }
        if (game.getPendingHumanDiplomacyProposal() != null) {
            activateSourceActionMenu(x, y, true);
            return;
        }
        for (int slot = 0; slot < actionSlots.length; slot++) {
            if (actionSlots[slot].contains(x, y)) {
                activateActionSlot(slot);
                return;
            }
        }
        if (sourceActionMenu.isEconomyWarning(game)) {
            if (navViewport.contains(x, y)) activateNavigationRow(x, y);
            else {
                String control = sourceActionMenuRenderer.controlAt(modal, x, y, game, sourceActionMenu);
                if (control != null && control.endsWith("btnOK")) sourceActionMenu.dismissError(game);
            }
            return;
        }
        if (SourceActionMenuState.supports(game.getSelectedAction())) {
            if (navViewport.contains(x, y)) activateNavigationRow(x, y);
            else if (equipmentViewport.contains(x, y)) activateEquipmentRow(x, y);
            else activateSourceActionMenu(x, y, false);
            return;
        }
        GameState.Action selectedAction = game.getSelectedAction();
        if (!modalOpen && selectedAction == GameState.Action.PROPAGANDA) {
            if (actionFooterButtons[1].contains(x, y)) {
                game.cancelSelection();
                audio.play(OriginalAudio.Cue.ACTION_CLOSE);
                return;
            }
            if (mapBounds.contains(x, y)) {
                selectMapTargetCity(selectedAction, x, y);
                return;
            }
        }
        if (!modalOpen && selectedAction == GameState.Action.ATTACK) {
            if (actionFooterButtons[0].contains(x, y)) {
                modalOpen = true;
                audio.play(OriginalAudio.Cue.ACTION_OPEN);
                return;
            }
            if (actionFooterButtons[1].contains(x, y)) {
                game.cancelSelection();
                modalOpen = false;
                audio.play(OriginalAudio.Cue.ACTION_CLOSE);
                return;
            }
            if (actionFooterButtons[2].contains(x, y)) {
                if (game.acceptSelectedAction()) {
                    modalOpen = false;
                    audio.play(OriginalAudio.Cue.ACTION_ACCEPT);
                }
                return;
            }
            if (selectedAction == GameState.Action.ATTACK && mapBounds.contains(x, y)) {
                selectMapTargetCity(selectedAction, x, y);
                return;
            }
        }
        if (modalOpen) {
            GameState.Action action = game.getSelectedAction();
            if (navViewport.contains(x, y)) {
                activateNavigationRow(x, y);
                return;
            }
            if (action == GameState.Action.PRODUCTION) {
                if (productionSummaryCount > 0 && modalAccept.contains(x, y)) {
                    acceptProductionSelection();
                    return;
                }
                if (productionSummaryCount > 0 && modalCancel.contains(x, y)) {
                    game.cancelSelection();
                    modalOpen = false;
                    audio.play(OriginalAudio.Cue.ACTION_CLOSE);
                    return;
                }
                for (int i = 0; i < productionSliderHitTargets.length; i++) {
                    if (productionSliderHitTargets[i].contains(x, y)) {
                        updateProductionSlider(i, x);
                        return;
                    }
                }
                return;
            }
            if (action == GameState.Action.RESEARCH) {
                updateResearchChoiceCards(game.getResearchChoices());
                for (int i = 0; i < technologyCards.length; i++) {
                    if (!technologyCards[i].contains(x, y)) {
                        continue;
                    }
                    String technology = researchChoiceByCard[i];
                    if (technology != null) {
                        boolean selected = game.selectResearchTechnology(technology);
                        if (!selected) {
                            String warning = EconomyWarningText.research(game, technology);
                            if (warning != null) sourceActionMenu.showEconomyWarning(game, warning);
                        }
                        audio.play(OriginalAudio.Cue.UNIT_SELECT);
                    }
                    return;
                }
            }
            if (selectMapTargetCity(action, x, y)) {
                return;
            }
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
                    audio.play(OriginalAudio.Cue.ACTION_ACCEPT);
                }
            } else if (modalCancel.contains(x, y)) {
                game.cancelSelection();
                modalOpen = false;
                audio.play(OriginalAudio.Cue.ACTION_CLOSE);
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
        } else if (confirmButton.contains(x, y)) {
            if (confirmTurnAnimation.press(game.getQueuedActionCount(), turnPlaybackSequence.isBlockingInput())) {
                audio.play(OriginalAudio.Cue.CONFIRM_CLOSE);
            }
        }
    }

    /** Original btnPressed_tube cancels an assigned action or offers Skip when idle. */
    private void activateActionSlot(int slot) {
        if (game.getQueuedAction(slot) != null) {
            game.removeQueuedAction(slot);
            game.cancelSelection();
            selectedEquipment = "";
            modalOpen = false;
            audio.play(OriginalAudio.Cue.ACTION_OPEN);
        } else if (game.getSelectedAction() == null) {
            game.selectAction(GameState.Action.SKIP);
            if (game.getSelectedAction() != GameState.Action.SKIP) return;
            sourceActionMenu.begin(game);
            sourceActionInstruction.onSelectionUpdate(game.getSelectedAction(),
                    sourceActionMenu.getKey());
            selectedEquipment = "";
            modalOpen = true;
            audio.play(OriginalAudio.Cue.ACTION_OPEN);
        }
    }

    private boolean isTurnInputBlocked() {
        if (outcomeSequence.isEndgameVisible()) return false;
        return confirmTurnAnimation.isPending() || turnPlaybackSequence.isBlockingInput();
    }

    private void synchronizeTurnControls() {
        if (game == null) return;
        if (confirmTurnAnimation.synchronize(game.getQueuedActionCount(), turnPlaybackSequence.isBlockingInput())) {
            audio.play(confirmTurnAnimation.isOpening() ? OriginalAudio.Cue.CONFIRM_OPEN : OriginalAudio.Cue.CONFIRM_CLOSE);
        }
        actionSlotAnimation.synchronize(game, turnPlaybackSequence.isBlockingInput(),
                confirmTurnAnimation.isPending(), confirmTurnAnimation.getElapsedSeconds());
    }

    private void confirmTurnAndPreparePlayback() {
        game.confirmTurn();
        SourceHiddenCommands hidden = game.getHiddenCommands();
        hiddenBattleEffects.begin(hidden.isActive(SourceHiddenCommands.Flag.ORCA),
                hidden.isActive(SourceHiddenCommands.Flag.NESSY),
                hidden.isActive(SourceHiddenCommands.Flag.KILL_ALL),
                hidden.isActive(SourceHiddenCommands.Flag.BUSH),
                game.getBattlePresentationPopulatedCities(false),
                game.getBattlePresentationPopulatedCities(true));
        // A fresh isolated battle root has a fresh anti-defense child timeline.
        audio.finishAntiDefenseAudio();
        clearSourceAirDefenses();
        // Canonical refresh seeks resolved city frames immediately. The fresh
        // battle root instead starts from the pre-casualty presentation snapshot.
        cityRenderer.beginBattle(game);
        // Python BattlePlayback creates a fresh root for each resolved turn.
        // Old canonical slots must not carry flame phases or stale buildings
        // into the new parent (even when its final building frame is identical).
        battleActionFrames.reset();
        actionBuildingAnimations.clear();
        // Its canonical snapshot has counter zero: Python replaces endBattle
        // with finish, so the isolated playback never carries Flash's final 20.
        battleRecordClock.reset();
        resetBattlefieldDisplay(true);
        // original_gameplay.refresh -> View.update_flags recreates the child clips.
        flagRenderer.beginBattle();
        audio.beginBattleAmbient();
        audio.beginBattleActionSounds();
        queueActionBuildingAnimations();
        audio.play(OriginalAudio.Cue.ACTION_ACCEPT);
        battlePresentationRecords = game.getLastTurnBattleRecords();
        boolean hasPendingBuilding = false;
        for (GameState.BattlePresentationRecord record : battlePresentationRecords) {
            hasPendingBuilding |= record.pendingBuilding;
        }
        battleRecordClock.begin(battlePresentationRecords.size(), hasPendingBuilding);
        battleTicker.begin();
        int submarineSequenceIndex = 0;
        FlashAntiDefenseTrackingAnimation.TargetLock antiDefenseTargetLock =
                new FlashAntiDefenseTrackingAnimation.TargetLock();
        battlePresentationRandom = game.createBattlePresentationRandomCopy();
        ReferenceRandom presentationRandom = battlePresentationRandom;
        cityRenderer.beginSourceBiologicalPlayback(game, presentationRandom);
        battleInterfaceFrames.begin();
        sourceStoppedClips.reset();
        sourceUnitPlayback.reset();
        // Keep source record groups and their reverse-expanded Unit order;
        // record readiness is still checked on each captured Unit visit.
        SourceBattleUnitAllocation unitAllocation = new SourceBattleUnitAllocation(battlefieldDisplay);
        List<GameState.CombatEvent> unitEvents = game.getLastTurnCombatEventsInUnitConstructionOrder();
        java.util.Map<GameState.CombatEvent, Object> randomizedRoutes =
                new java.util.IdentityHashMap<>();
        // Battle builds the reverse spawn array (including route jitter) before
        // Presenter constructs any Units. View later visits the Units in reverse.
        for (int index = unitEvents.size() - 1; index >= 0; index--) {
            GameState.CombatEvent event = unitEvents.get(index);
            if (FlashBattleRoutes.supports(event)) {
                randomizedRoutes.put(event, FlashBattleRoutes.createSoldierOneAttack(
                        event.attackerPlayerId, event.defenderPlayerId,
                        event.cityIndex, battleAnchors, presentationRandom));
            } else if (FlashBattleRoutes.supportsApacheMarine(event)) {
                randomizedRoutes.put(event, FlashBattleRoutes.createSoldierTwoAttack(
                        event.attackerPlayerId, event.defenderPlayerId,
                        event.cityIndex, event.actionBuildingSlot,
                        battleAnchors, presentationRandom));
            } else if (FlashBattleRoutes.supportsChinookTank(event)) {
                randomizedRoutes.put(event, FlashBattleRoutes.createSoldierThreeAttack(
                        event.attackerPlayerId, event.defenderPlayerId,
                        event.cityIndex, event.actionBuildingSlot,
                        GameSetup.colorName(game.getPlayerState(event.attackerPlayerId)
                                .getPlayerColor()), battleAnchors, presentationRandom));
            }
        }
        java.util.Map<Integer, Integer> carrierPayloadCounts = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> carrierPhysicalNumbers = new java.util.HashMap<>();
        for (GameState.CombatEvent event : unitEvents) {
            if (FlashBattleRoutes.supportsCarrierPlaneStrike(event))
                carrierPayloadCounts.merge(event.battleStartNum, 1, Integer::sum);
        }
        for (GameState.CombatEvent event : unitEvents) {
            int precedingUnitParameter = unitAllocation.reserveBeforeEvent(event,
                    carrierPhysicalNumbers.getOrDefault(event.battleStartNum, 0));
            if (FlashBattleRoutes.supportsRocketStrike(event)
                    || FlashBattleRoutes.supportsSubmarineRocketStrike(event)
                    || FlashBattleRoutes.supportsStandalonePlaneStrike(event)
                    || FlashBattleRoutes.supportsCarrierPlaneStrike(event))
                cityRenderer.beginSourceTankCity(game,
                        event.defenderPlayerId * BattlefieldCityLayout.CITIES_PER_PLAYER
                                + event.cityIndex);
            int recordDelay = battleRecordClock.delayForRecord(event.battleStartNum);
            OriginalAudio.Cue cue = OriginalAudio.cueForCombatEvent(event);
            if (FlashBattleRoutes.supportsSatelliteStrike(event)) {
                GameState.PlayerState attacker = game.getPlayerState(
                        event.attackerPlayerId);
                FlashBattleRoutes.SatelliteStrike route =
                        FlashBattleRoutes.createSatelliteStrike(
                                event.attackerPlayerId, event.defenderPlayerId,
                                event.cityIndex, attacker.getPlayerColor(),
                                attacker.getReligionCode());
                FlashSatelliteAnimation animation = new FlashSatelliteAnimation(route, recordDelay);
                satelliteBattleAnimations.add(animation);
                sourceUnitPlayback.addConstructed(() -> advanceSatelliteUnit(animation));
                continue;
            }
            if (FlashBattleRoutes.supportsCarrierPlaneStrike(event)) {
                int physicalNumber = carrierPhysicalNumbers.getOrDefault(event.battleStartNum, 0);
                carrierPhysicalNumbers.put(event.battleStartNum, physicalNumber + 1);
                FlashBattleRoutes.CarrierPlaneStrike route =
                        FlashBattleRoutes.createCarrierPlaneStrike(event.attackerPlayerId,
                                event.defenderPlayerId, event.cityIndex, physicalNumber,
                                event.type == GameState.CombatEventType.STRIKE_INTERCEPTED);
                FlashCarrierPlaneAnimation planeAnimation =
                        new FlashCarrierPlaneAnimation(route,
                        recordDelay,
                        SourceCombatAnimationMapping.linkageFor(event), cue, presentationRandom,
                        carrierPayloadCounts.get(event.battleStartNum),
                        game.getPlayerState(event.attackerPlayerId).getPlayerColor());
                carrierPlaneAnimations.add(planeAnimation);
                aircraftAudioOwners.put(planeAnimation,
                        audio.createAircraftAudio(AircraftAudioPlayback.Kind.FIGHTER));
                sourceAircraftTrails.put(planeAnimation.getSourceTrail(),
                        new SourceAircraftTrailDisplay(battlefieldDisplay,
                                planeAnimation.getSourceTrail(), event.attackerPlayerId,
                                precedingUnitParameter, event.defenderPlayerId,
                                planeAnimation::getSourceTrailCropProgress));
                SourceAirDefenseController defense = airDefenseFor(
                        event, antiDefenseTargetLock);
                SourceAirDefenseController.UnitVisit defenseUnit = defense == null
                        ? null : defense.register(planeAnimation,
                            audio.createAntiDefenseOwner(AntiDefenseAudioPlayback.Type.AIR));
                SourceCarrierPlaneDisplayDepths carrierDepths =
                        new SourceCarrierPlaneDisplayDepths(battlefieldDisplay,
                                planeAnimation, precedingUnitParameter, sourceStoppedClips);
                sourceCarrierPlaneBindings.add(carrierDepths);
                sourceUnitPlayback.addConstructed(() -> {
                    if (planeAnimation.isFinished()) return false;
                    boolean bodyWasAttached = planeAnimation.getSourcePlayback().isBodyAttached();
                    boolean stop = advanceCarrierPlaneUnit(planeAnimation, event, carrierDepths);
                    if (!stop && defenseUnit != null) {
                        defense.afterUnitVisit(defenseUnit,
                                bodyWasAttached && !planeAnimation.getSourcePlayback()
                                        .isBodyAttached());
                        synchronizeSourceAirDefenseTracker(defense);
                    }
                    return stop;
                });
                continue;
            }
            if (FlashBattleRoutes.supportsStandalonePlaneStrike(event)) {
                FlashBattleRoutes.StandalonePlaneStrike route =
                        FlashBattleRoutes.createStandalonePlaneStrike(
                                event.attackerPlayerId, event.defenderPlayerId,
                                event.cityIndex, event.actionBuildingSlot,
                                event.weaponName,
                                event.type == GameState.CombatEventType.STRIKE_INTERCEPTED);
                int playerColor = game.getPlayerState(event.attackerPlayerId)
                        .getPlayerColor();
                FlashStandalonePlaneAnimation planeAnimation =
                        FlashStandalonePlaneAnimation.forPreparedLaunch(
                        route, playerColor, recordDelay,
                        SourceCombatAnimationMapping.linkageFor(event), cue, presentationRandom);
                standalonePlaneAnimations.add(planeAnimation);
                aircraftAudioOwners.put(planeAnimation, audio.createAircraftAudio(
                        "plane002".equals(route.linkage) ? AircraftAudioPlayback.Kind.CARRIER
                                : AircraftAudioPlayback.Kind.FIGHTER));
                if (planeAnimation.getSourceTrail() != null)
                    sourceAircraftTrails.put(planeAnimation.getSourceTrail(),
                            new SourceAircraftTrailDisplay(battlefieldDisplay,
                                    planeAnimation.getSourceTrail(), event.attackerPlayerId,
                                    precedingUnitParameter, event.defenderPlayerId,
                                    planeAnimation::getSourceTrailCropProgress));
                SourceInterceptedAircraftDisplayDepths aircraftDepths = null;
                if (planeAnimation.getSourcePlayback() != null) {
                    aircraftDepths = new SourceInterceptedAircraftDisplayDepths(
                            battlefieldDisplay, planeAnimation, precedingUnitParameter,
                            sourceStoppedClips);
                    sourceInterceptedAircraftBindings.add(aircraftDepths);
                }
                final SourceInterceptedAircraftDisplayDepths sourceAircraftDepths = aircraftDepths;
                SourceAirDefenseController defense = airDefenseFor(event, antiDefenseTargetLock);
                SourceAirDefenseController.UnitVisit defenseUnit = defense == null
                        ? null : defense.register(planeAnimation,
                            audio.createAntiDefenseOwner(AntiDefenseAudioPlayback.Type.AIR));
                sourceUnitPlayback.addConstructed(() -> {
                    if (planeAnimation.isFinished()) return false;
                    boolean bodyWasAttached = planeAnimation.getSourcePlayback().isBodyAttached();
                    boolean stop = advanceStandalonePlaneUnit(planeAnimation, event, sourceAircraftDepths);
                    if (!stop && defenseUnit != null) {
                        defense.afterUnitVisit(defenseUnit,
                                bodyWasAttached && !planeAnimation.getSourcePlayback().isBodyAttached());
                        synchronizeSourceAirDefenseTracker(defense);
                    }
                    return stop;
                });
                continue;
            }
            if (FlashBattleRoutes.supportsRocketStrike(event)) {
                FlashBattleRoutes.RocketStrike route = FlashBattleRoutes.createRocketStrike(
                        event.attackerPlayerId, event.defenderPlayerId,
                        event.cityIndex, event.actionBuildingSlot,
                        event.type == GameState.CombatEventType.STRIKE_INTERCEPTED);
                FlashRocketAnimation rocketAnimation = FlashRocketAnimation.forSourceOrdinary(
                        route, SourceCombatAnimationMapping.linkageFor(event), cue,
                        presentationRandom,
                        game.getPlayerState(event.attackerPlayerId).getPlayerColor());
                rocketBattleAnimations.add(rocketAnimation);
                SourceRocketDisplayDepths depths = new SourceRocketDisplayDepths(
                        battlefieldDisplay, rocketAnimation, precedingUnitParameter,
                        () -> flashAnimationAssets.getTimeline(rocketAnimation.getImpactLinkage()),
                        sourceStoppedClips);
                sourceRocketBindings.add(depths);
                SourceRocketDefenseController defense = rocketDefenseFor(event, antiDefenseTargetLock);
                SourceRocketDefenseController.UnitVisit defenseUnit = defense == null ? null
                        : defense.register(rocketAnimation,
                            audio.createAntiDefenseOwner(AntiDefenseAudioPlayback.Type.ROCKET));
                sourceUnitPlayback.addConstructed(() -> {
                    if (rocketAnimation.isFinished()) return false;
                    boolean bodyWasAttached = rocketAnimation.getSourcePlayback().isBodyAttached();
                    boolean stop = advanceRocketUnit(rocketAnimation, event, null, depths);
                    if (!stop && defenseUnit != null) {
                        if (rocketAnimation.getSourcePlayback().takeFireDefense())
                            defense.notifyFireDefense(defense.getBuilding().seekSourceRocketFire());
                        boolean selfKill = rocketAnimation.getSourcePlayback().takeSelfKillPulse();
                        defense.afterUnitVisit(defenseUnit, selfKill,
                                selfKill || bodyWasAttached
                                    && !rocketAnimation.getSourcePlayback().isBodyAttached());
                        synchronizeSourceRocketDefenseTracker(defense);
                    }
                    return stop;
                });
                continue;
            }
            if (FlashBattleRoutes.supportsSubmarineRocketStrike(event)) {
                FlashBattleRoutes.RocketStrike route =
                        FlashBattleRoutes.createSubmarineRocketStrike(
                                event.attackerPlayerId, event.defenderPlayerId,
                                event.cityIndex,
                                event.type == GameState.CombatEventType.STRIKE_INTERCEPTED);
                int submarineDelay = FlashSubmarineAnimation.delayForSequenceIndex(
                        ++submarineSequenceIndex);
                FlashSubmarineAnimation submarine = new FlashSubmarineAnimation(route,
                        game.getPlayerState(event.attackerPlayerId).getPlayerColor(),
                        submarineDelay);
                submarineBattleAnimations.add(submarine);
                FlashRocketAnimation rocketAnimation = FlashRocketAnimation.forSourceSubmarine(route,
                            SourceCombatAnimationMapping.linkageFor(event), cue, presentationRandom,
                            game.getPlayerState(event.attackerPlayerId).getPlayerColor());
                rocketBattleAnimations.add(rocketAnimation);
                SourceRocketDisplayDepths depths = new SourceRocketDisplayDepths(
                        battlefieldDisplay, rocketAnimation, precedingUnitParameter,
                        () -> flashAnimationAssets.getTimeline(rocketAnimation.getImpactLinkage()),
                        sourceStoppedClips);
                sourceRocketBindings.add(depths);
                sourceUnitPlayback.addConstructed(() -> advanceRocketUnit(rocketAnimation, event, submarine, depths));
                continue;
            }
            if (FlashBattleRoutes.supports(event)) {
                FlashBattleRoutes.SoldierOneAttack route =
                        (FlashBattleRoutes.SoldierOneAttack) randomizedRoutes.get(event);
                FlashSoldierOneAnimation animation = new FlashSoldierOneAnimation(route,
                        presentationRandom, cue, recordDelay,
                        game.getPlayerState(event.attackerPlayerId).getPlayerColor());
                soldierBattleAnimations.add(animation);
                new SourceInfantryDisplayDepths(battlefieldDisplay, animation,
                        precedingUnitParameter);
                sourceUnitPlayback.addConstructed(() -> advanceInfantryUnit(animation));
                continue;
            }
            if (FlashBattleRoutes.supportsApacheMarine(event)) {
                FlashBattleRoutes.ApacheMarineAttack route =
                        (FlashBattleRoutes.ApacheMarineAttack) randomizedRoutes.get(event);
                FlashApacheMarineAnimation animation = new FlashApacheMarineAnimation(route,
                        presentationRandom, cue, recordDelay, GameSetup.colorName(
                                game.getPlayerState(event.attackerPlayerId).getPlayerColor()));
                apacheMarineAnimations.add(animation);
                new SourceApacheDisplayDepths(battlefieldDisplay, animation,
                        precedingUnitParameter);
                sourceUnitPlayback.addConstructed(() -> advanceMarineUnit(animation));
                continue;
            }
            if (FlashBattleRoutes.supportsChinookTank(event)) {
                String colorName = GameSetup.colorName(
                        game.getPlayerState(event.attackerPlayerId).getPlayerColor());
                FlashBattleRoutes.ChinookTankAttack route =
                        (FlashBattleRoutes.ChinookTankAttack) randomizedRoutes.get(event);
                FlashChinookTankAnimation animation = new FlashChinookTankAnimation(route, recordDelay);
                chinookTankAnimations.add(animation);
                cityRenderer.beginSourceTankCity(game,
                        event.defenderPlayerId * BattlefieldCityLayout.CITIES_PER_PLAYER
                                + event.cityIndex);
                new SourceChinookDisplayDepths(battlefieldDisplay, animation,
                        precedingUnitParameter);
                sourceUnitPlayback.addConstructed(() -> advanceTankUnit(animation, event));
                continue;
            }
            if (FlashBattleRoutes.supportsSuicideBomber(event)) {
                FlashBattleRoutes.SuicideBomberAttack route =
                        FlashBattleRoutes.createSuicideBomberAttack(
                                event.attackerPlayerId, event.defenderPlayerId,
                                event.cityIndex, event.actionBuildingSlot, battleAnchors);
                FlashSuicideBomberAnimation animation = new FlashSuicideBomberAnimation(route,
                        cue, recordDelay,
                        game.getPlayerState(event.attackerPlayerId).getPlayerColor());
                suicideBomberAnimations.add(animation);
                cityRenderer.beginSourceTankCity(game,
                        event.defenderPlayerId * BattlefieldCityLayout.CITIES_PER_PLAYER
                                + event.cityIndex);
                new SourceSuicideDisplayDepths(battlefieldDisplay, animation,
                        precedingUnitParameter);
                sourceUnitPlayback.addConstructed(() -> advanceSuicideUnit(animation, event));
                continue;
            }
            String linkage = SourceCombatAnimationMapping.linkageFor(event);
            FlashAnimationTimeline timeline = linkage == null ? null
                    : flashAnimationAssets.getTimeline(linkage);
            if (timeline != null) {
                battleAnimations.add(new BattleAnimationSequence(timeline,
                        event.defenderPlayerId, event.cityIndex,
                        recordDelay, cue));
            } else if (linkage != null && !flashAnimationAssets.isLoaded()) {
                pendingBattleAnimations.add(new PendingBattleAnimation(event,
                        recordDelay));
            } else if (cue != null) {
                audio.play(cue);
            }
        }
        // Presenter initializes religion child clips after constructing routes.
        // These callbacks share the private presentation stream with the units.
        audio.prepareSatelliteAudio(satelliteBattleAnimations.size());
        satelliteAudioOwners.clear();
        for (FlashSatelliteAnimation satellite : satelliteBattleAnimations) {
            SatelliteAudioPlayback.Owner owner = audio.claimSatelliteAudio();
            if (owner != null) satelliteAudioOwners.put(satellite, owner);
        }
        battleActionFrames.begin(actionBuildingAnimations,
                game.getLastTurnActionBuildings(), presentationRandom,
                game.getHiddenCommands().isActive(SourceHiddenCommands.Flag.DISCO));
    }

    private boolean selectMapTargetCity(GameState.Action action, float x, float y) {
        if (modalOpen && (isActionModalControlAt(action, x, y)
                || action == GameState.Action.PROPAGANDA && modal.contains(x, y))) {
            return false;
        }
        boolean targetAction = action == GameState.Action.ATTACK
                || action == GameState.Action.PROPAGANDA;
        boolean shipSelection = false;
        if (action == GameState.Action.PREPARED_ATTACK) {
            String carrier = game.getPreparedCarrierName(0);
            targetAction = carrier != null;
            shipSelection = targetAction && isPreparedShipCarrier();
        }
        if (!targetAction || !mapBounds.contains(x, y)) {
            return false;
        }
        int address = cityRenderer == null ? -1
                : cityRenderer.hitTest(x, y, mapBounds, game);
        if (address < 0) {
            return false;
        }
        int playerId = address / BattlefieldCityLayout.CITIES_PER_PLAYER;
        int cityIndex = address % BattlefieldCityLayout.CITIES_PER_PLAYER;
        if (playerId <= 0) {
            return true;
        }
        if (playerId > 0 && !(action == GameState.Action.ATTACK
                && game.isSelectedAttackSpy())
                && game.getHuman().hasCeasefireWith(playerId)) {
            return true;
        }
        if (shipSelection) {
            java.util.List<String> payloads = getPreparedPayloadChoices();
            if (payloads.isEmpty()) {
                return true;
            }
            preparedPayloadIndex = Math.floorMod(preparedPayloadIndex, payloads.size());
            PreparedShipStrikeSelection.Result result =
                    PreparedShipStrikeSelection.tryAddTargetCity(game, playerId, cityIndex,
                            payloads.get(preparedPayloadIndex));
            if (result == PreparedShipStrikeSelection.Result.ADDED) {
                preparedTargetPlayer = playerId;
                preparedCityIndex = cityIndex;
                game.selectBattlefieldCity(playerId, cityIndex);
                audio.play(OriginalAudio.Cue.UNIT_SELECT);
            }
            return true;
        }
        if (game.selectBattlefieldCity(playerId, cityIndex)) {
            audio.play(OriginalAudio.Cue.UNIT_SELECT);
            if (action == GameState.Action.PROPAGANDA) {
                modalOpen = true;
                audio.play(OriginalAudio.Cue.ACTION_OPEN);
            } else if (action == GameState.Action.PREPARED_ATTACK) {
                preparedTargetPlayer = playerId;
                preparedCityIndex = cityIndex;
                refreshPreparedLaunch();
            }
        }
        return true;
    }

    private boolean isActionModalControlAt(GameState.Action action, float x, float y) {
        if (modalAccept.contains(x, y) || modalCancel.contains(x, y)) {
            return true;
        }
        if (action == GameState.Action.ATTACK) {
            return attackTargetPrevious.contains(x, y) || attackTargetNext.contains(x, y)
                    || attackCityPrevious.contains(x, y) || attackCityNext.contains(x, y)
                    || action == GameState.Action.ATTACK
                    && (attackWeaponPrevious.contains(x, y)
                    || attackWeaponNext.contains(x, y));
        }
        if (action != GameState.Action.PREPARED_ATTACK) {
            return false;
        }
        if (game.getPreparedCarrierName(0) == null) {
            return attackWeaponPrevious.contains(x, y) || attackWeaponNext.contains(x, y);
        }
        return attackTargetPrevious.contains(x, y) || attackTargetNext.contains(x, y)
                || attackCityPrevious.contains(x, y) || attackCityNext.contains(x, y)
                || attackWeaponPrevious.contains(x, y) || attackWeaponNext.contains(x, y)
                || preparedStrikeAdd.contains(x, y);
    }

    private boolean handleBack() {
        if (settingsMenu.isOpen()) {
            settingsMenu.back();
            skipNextFrameDelta = true;
            return true;
        }
        if (isTurnInputBlocked()) return true;
        if (helpState.isOpen()) {
            closeHelpSelection();
            return true;
        }
        if (game == null) {
            if (splashState.isVisible()) {
                requestSplashContinue();
                return true;
            }
            if (showExitPromptSeconds > 0f) {
                Gdx.app.exit();
            } else {
                showExitPromptSeconds = 2.4f;
                setContinuousRendering(true);
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
        if (sourceActionMenu.isEconomyWarning(game)) {
            sourceActionMenu.dismissError(game);
            return true;
        }
        if (SourceActionMenuState.supports(game.getSelectedAction())) {
            modalOpen = !sourceActionMenu.cancel(game);
            if (!modalOpen) selectedEquipment = "";
            return true;
        }
        if (modalOpen) {
            modalOpen = false;
            game.cancelSelection();
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
        if (game != null && !isTurnInputBlocked() && helpState.isInputFocused()) {
            if (keycode == Input.Keys.ENTER || keycode == Input.Keys.ESCAPE || keycode == Input.Keys.BACK) {
                blurHiddenHelpInput();
                Gdx.graphics.requestRendering();
                return true;
            }
            int caret = helpState.getCaret();
            if (keycode == Input.Keys.LEFT) helpState.moveCaret(caret - 1);
            else if (keycode == Input.Keys.RIGHT) helpState.moveCaret(caret + 1);
            else if (keycode == Input.Keys.HOME) helpState.moveCaret(0);
            else if (keycode == Input.Keys.END) helpState.moveCaret(helpState.getInputText().length());
            else return false; // Character edits, including deletion, occur only in keyTyped.
            Gdx.graphics.requestRendering();
            return true;
        }
        boolean handled = keycode == Input.Keys.BACK && handleBack();
        if (handled) {
            Gdx.graphics.requestRendering();
        }
        return handled;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointerIndex, int button) {
        if (pointerIndex != 0) {
            return false;
        }
        if (splashState.isVisible()) {
            splashPointerDown = true;
            return true;
        }
        if (isTurnInputBlocked()) {
            ignorePlaybackTouchUntilUp = true;
            gestureArea = GESTURE_NONE;
            dragging = false;
            return true;
        }
        unproject(screenX, screenY);
        if (game == null) updateSetupLeaderName(pointer.x, pointer.y);
        pointerStartX = pointer.x;
        pointerStartY = pointer.y;
        lastPointerY = pointer.y;
        lastPointerNanos = System.nanoTime();
        gestureVelocityY = 0f;
        dragging = false;
        gestureArea = pointArea(pointer.x, pointer.y);
        if (gestureArea == GESTURE_NAV) navigationScroll.beginGesture();
        else if (gestureArea == GESTURE_EQUIPMENT) equipmentScroll.beginGesture();
        if (gestureArea == GESTURE_MESSAGE) {
            messageDragStartScroll = messageHistoryScroll.getScroll();
        }
        productionGestureSlider = -1;
        productionGestureHandleOffsetX = 0f;
        if (gestureArea == GESTURE_PRODUCTION) {
            for (int i = 0; i < productionSliderHitTargets.length; i++) {
                if (productionSliderHitTargets[i].contains(pointer.x, pointer.y)) {
                    productionGestureSlider = i;
                    int currentPercent = game.getProductionPercent(
                            ProductionScreenLayout.categoryKey(i));
                    Rectangle handle = ProductionScreenLayout.setHandleBounds(
                            i, currentPercent, modal, productionSliderMarkers[i]);
                    if (handle.contains(pointer.x, pointer.y)) {
                        productionGestureHandleOffsetX = pointer.x
                                - (handle.x + handle.width / 2f);
                    }
                    if (!updateProductionSlider(i, pointer.x,
                            productionGestureHandleOffsetX)) {
                        productionGestureSlider = -1;
                    }
                    break;
                }
            }
        }
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointerIndex, int button) {
        if (pointerIndex != 0) {
            return false;
        }
        if (splashPointerDown) {
            splashPointerDown = false;
            requestSplashContinue();
            return true;
        }
        if (splashState.isVisible()) {
            return true;
        }
        if (ignorePlaybackTouchUntilUp) {
            ignorePlaybackTouchUntilUp = false;
            gestureArea = GESTURE_NONE;
            dragging = false;
            return true;
        }
        if (isTurnInputBlocked()) {
            gestureArea = GESTURE_NONE;
            dragging = false;
            return true;
        }
        unproject(screenX, screenY);
        if (game == null) updateSetupLeaderName(pointer.x, pointer.y);
        float distance = (float) Math.hypot(pointer.x - pointerStartX, pointer.y - pointerStartY);
        if (gestureArea == GESTURE_PRODUCTION) {
            if (productionGestureSlider >= 0 && (dragging || distance >= TAP_SLOP)) {
                updateProductionSlider(productionGestureSlider, pointer.x,
                        productionGestureHandleOffsetX);
            }
        } else if (gestureArea == GESTURE_MESSAGE) {
            // The source uses separate up/down hit targets; on Android the panel swipes instead.
        } else if (gestureArea != GESTURE_NONE && dragging) {
            if (gestureArea == GESTURE_NAV) {
                navigationScroll.endGesture(gestureVelocityY);
            } else if (gestureArea == GESTURE_EQUIPMENT) {
                equipmentScroll.endGesture(gestureVelocityY);
            }
        } else if (!dragging && distance < TAP_SLOP) {
            activateAt(pointer.x, pointer.y);
        }
        gestureArea = GESTURE_NONE;
        productionGestureSlider = -1;
        productionGestureHandleOffsetX = 0f;
        dragging = false;
        Gdx.graphics.requestRendering();
        return true;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointerIndex, int button) {
        if (pointerIndex == 0 && splashPointerDown) {
            splashPointerDown = false;
            return true;
        }
        if (pointerIndex == 0) {
            ignorePlaybackTouchUntilUp = false;
            gestureArea = GESTURE_NONE;
            productionGestureSlider = -1;
            productionGestureHandleOffsetX = 0f;
            dragging = false;
            gestureVelocityY = 0f;
        }
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointerIndex) {
        if (splashPointerDown || splashState.isVisible()) {
            return true;
        }
        if (ignorePlaybackTouchUntilUp || isTurnInputBlocked()) {
            return true;
        }
        if (pointerIndex != 0) {
            return false;
        }
        if (game == null) {
            unproject(screenX, screenY);
            updateSetupLeaderName(pointer.x, pointer.y);
            if (Math.hypot(pointer.x - pointerStartX, pointer.y - pointerStartY) > TAP_SLOP) {
                dragging = true;
            }
            return true;
        }
        if (gestureArea == GESTURE_NONE) {
            return false;
        }
        unproject(screenX, screenY);
        float totalX = pointer.x - pointerStartX;
        float totalY = pointer.y - pointerStartY;
        if (gestureArea == GESTURE_PRODUCTION) {
            if (!dragging && Math.hypot(totalX, totalY) > TAP_SLOP) {
                dragging = true;
            }
            if (dragging) {
                updateProductionSlider(productionGestureSlider, pointer.x,
                        productionGestureHandleOffsetX);
            }
            return true;
        }
        if (gestureArea == GESTURE_MESSAGE) {
            if (!dragging && Math.abs(totalY) > TAP_SLOP
                    && Math.abs(totalY) > Math.abs(totalX)) {
                dragging = true;
            }
            if (dragging) {
                if (messageHistoryScroll.scrollFromDrag(messageDragStartScroll,
                        totalY, messageLineHeight)) {
                    Gdx.graphics.requestRendering();
                }
            }
            return true;
        }
        if (!dragging && Math.abs(totalY) > TAP_SLOP && Math.abs(totalY) > Math.abs(totalX)) {
            dragging = true;
        }
        long now = System.nanoTime();
        if (dragging) {
            float delta = pointer.y - lastPointerY;
            if (gestureArea == GESTURE_NAV) {
                navigationScroll.dragBy(-delta);
            } else {
                equipmentScroll.dragBy(-delta);
            }
            gestureVelocityY = PanelGestureVelocity.sample(-delta, lastPointerNanos, now);
            Gdx.graphics.requestRendering();
        }
        lastPointerNanos = now;
        lastPointerY = pointer.y;
        return true;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (settingsMenu.isOpen() || isTurnInputBlocked()) {
            return true;
        }
        unproject(Gdx.input.getX(), Gdx.input.getY());
        if (navViewport.contains(pointer.x, pointer.y)) {
            navigationScroll.moveBy(amountY * NAV_ROW_HEIGHT);
            Gdx.graphics.requestRendering();
            return true;
        }
        if (equipmentViewport.contains(pointer.x, pointer.y)) {
            equipmentScroll.moveBy(amountY * EQUIPMENT_ROW_HEIGHT);
            Gdx.graphics.requestRendering();
            return true;
        }
        if (messageViewport.contains(pointer.x, pointer.y) && canScrollMessageHistory()) {
            if (messageHistoryScroll.scrollBy(-Math.round(amountY))) {
                Gdx.graphics.requestRendering();
            }
            return true;
        }
        return false;
    }

    @Override
    public void pause() {
        accumulator = 0f;
        blurHiddenHelpInput();
        // Release regenerable targets while the old GL context is still current.
        // Android restores managed FBOs before our resume callback runs.
        if (phonePanelRenderer != null) phonePanelRenderer.invalidate();
        audio.pause();
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void resume() {
        accumulator = 0f;
        if (phonePanelRenderer != null) phonePanelRenderer.invalidate();
        audio.resume();
        Gdx.input.setInputProcessor(this);
        setContinuousRendering(RenderLoopPolicy.shouldRenderContinuously(game != null,
                showExitPromptSeconds > 0f, hasActivePresentation()));
        Gdx.graphics.requestRendering();
    }

    private void setContinuousRendering(boolean enabled) {
        if (continuousRenderingEnabled != enabled) {
            if (enabled && !renderingFrame) {
                accumulator = 0f;
                skipNextFrameDelta = true;
            }
            Gdx.graphics.setContinuousRendering(enabled);
            continuousRenderingEnabled = enabled;
        }
    }

    @Override
    public void dispose() {
        blurHiddenHelpInput();
        disposeEndgameResources();
        battlefieldDisplay.clear();
        displayGame = null;
        battleActionFrames.reset();
        turnPlaybackSequence.reset();
        battleRecordClock.reset();
        battleInterfaceFrames.reset();
        sourceStoppedClips.reset();
        battlePresentationRandom = null;
        battleTicker.reset();
        hiddenBattleEffects.finish();
        battlePresentationRecords = Collections.emptyList();
        battleAnimations.clear();
        sourceUnitPlayback.reset();
        actionBuildingAnimations.clear();
        soldierBattleAnimations.clear();
        apacheMarineAnimations.clear();
        chinookTankAnimations.clear();
        suicideBomberAnimations.clear();
        clearRocketBattleAnimations();
        sourceRocketBindings.clear();
        sourceInterceptedAircraftBindings.clear();
        sourceAircraftTrails.clear();
        submarineBattleAnimations.clear();
        clearCarrierPlaneAnimations();
        clearStandalonePlaneAnimations();
        antiDefenseTrackingAnimations.clear();
        clearSourceAirDefenses();
        satelliteBattleAnimations.clear();
        satelliteAudioOwners.clear();
        pendingBattleAnimations.clear();
        audio.dispose();
        if (setupPanelRenderer != null) setupPanelRenderer.dispose();
        if (splashAssets != null) {
            splashAssets.dispose();
        }
        if (phonePanelRenderer != null) phonePanelRenderer.dispose();
        if (uiAssets != null) {
            uiAssets.dispose();
        }
        if (cityAssets != null) {
            cityAssets.dispose();
        }
        if (cityRenderer != null) {
            cityRenderer.dispose();
        }
        if (flagRenderer != null) {
            flagRenderer.dispose();
        }
        if (terrainRenderer != null) {
            terrainRenderer.dispose();
        }
        if (flashAnimationAssets != null) {
            flashAnimationAssets.dispose();
        }
        if (battleUnitRenderer != null) {
            battleUnitRenderer.dispose();
        }
        if (font != null) {
            font.dispose();
        }
        if (sourceActionMenuRenderer != null) sourceActionMenuRenderer.dispose();
        if (helpRenderer != null) helpRenderer.dispose();
        if (hiddenHelpRenderer != null) hiddenHelpRenderer.dispose();
        if (hiddenHelpCommandFont != null) hiddenHelpCommandFont.dispose();
        if (hiddenHelpWarningFont != null) hiddenHelpWarningFont.dispose();
        if (resourceMenuRenderer != null) {
            resourceMenuRenderer.dispose();
        }
        if (messagePanelRenderer != null) {
            messagePanelRenderer.dispose();
        }
        if (turnControlRenderer != null) {
            turnControlRenderer.dispose();
        }
        if (actionTubeAssets != null) {
            actionTubeAssets.dispose();
        }
        centerUiRenderer.dispose();
        if (technologyArtwork != null) {
            technologyArtwork.dispose();
        }
        if (shapes != null) {
            shapes.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
    }

    @Override public boolean keyUp(int keycode) { return false; }
    @Override public boolean keyTyped(char character) {
        if (game == null || isTurnInputBlocked() || !helpState.isInputFocused()) return false;
        if (character == '\r' || character == '\n') blurHiddenHelpInput();
        else helpState.type(character);
        Gdx.graphics.requestRendering();
        return true;
    }
    @Override public boolean mouseMoved(int screenX, int screenY) {
        if (game != null || splashState.isVisible()) return false;
        unproject(screenX, screenY);
        updateSetupLeaderName(pointer.x, pointer.y);
        return true;
    }
}
