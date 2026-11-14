package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalCityAssets;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.SourceActionMenuState;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Pixel oracle for the source phone adapter's isolated battle to canonical idle handoff. */
@RunWith(AndroidJUnit4.class)
public final class SourceBattlePhaseRenderingInstrumentedTest {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final Rectangle SOURCE_BOUNDS = new Rectangle(0, 0, WIDTH, HEIGHT);

    @Test public void battleRootDoesNotLeakBuildingOrFlagPhaseIntoCanonicalIdle()
            throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(
                SourceBattlePhaseRenderingInstrumentedTest::verify);
    }

    private static void verify(WorldDominationGame app) throws Exception {
        // Same legal seed-0, normal, two-opponent action pair as the read-only
        // canonical-phase-audit: research soldier2 then resources.
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        field("game").set(app, game);
        ((BattlefieldCityRenderer) field("cityRenderer").get(app)).reset(game);
        ((SourceBattleRecordClock) field("battleRecordClock").get(app)).reset();
        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology("soldier2"));
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        invoke(app, "confirmTurnAndPreparePlayback");

        for (int tick = 1; tick <= 218; tick++) advance(app);
        BattlefieldDisplayState liveDisplay = (BattlefieldDisplayState)
                field("battlefieldDisplay").get(app);
        List<Clip> oldBattle = capture(liveDisplay);
        int paintedBuildings = 0;
        for (Clip clip : oldBattle) {
            if (clip.kind == BattlefieldDisplayState.Kind.ACTION_BUILDING
                    && clip.owner instanceof FlashActionBuildingAnimation
                    && ((FlashActionBuildingAnimation) clip.owner).getAlpha() > 0f) {
                paintedBuildings++;
            }
        }
        assertTrue("real battle-root buildings remain painted before handoff",
                paintedBuildings > 0);

        SpriteBatch batch = (SpriteBatch) field("batch").get(app);
        Rectangle bounds = (Rectangle) field("mapBounds").get(app);
        Rectangle savedBounds = new Rectangle(bounds);
        Matrix4 savedProjection = new Matrix4(batch.getProjectionMatrix());
        Matrix4 savedTransform = new Matrix4(batch.getTransformMatrix());
        ShaderProgram savedShader = batch.getShader();
        float savedColor = batch.getPackedColor();
        OriginalFlashAnimationAssets flash = (OriginalFlashAnimationAssets)
                field("flashAnimationAssets").get(app);
        OriginalCityAssets cityAssets = (OriginalCityAssets) field("cityAssets").get(app);
        BattlefieldCityRenderer expectedCity = new BattlefieldCityRenderer(cityAssets, flash);
        BattlefieldFlagRenderer expectedFlag = new BattlefieldFlagRenderer(flash);
        BattlefieldFlagRenderer oldBattleFlag = new BattlefieldFlagRenderer(flash);
        BattlefieldDisplayState expectedDisplay = new BattlefieldDisplayState();
        FlashBattleUnitRenderer units = (FlashBattleUnitRenderer)
                field("battleUnitRenderer").get(app);
        SourceActionMenuState menu = (SourceActionMenuState)
                field("sourceActionMenu").get(app);
        FrameBuffer buffer = null;
        try {
            assertFalse("snapshot starts outside SpriteBatch", batch.isDrawing());
            expectedCity.reset(game);
            expectedFlag.reset();
            oldBattleFlag.reset();
            for (int tick = 0; tick < 19; tick++) oldBattleFlag.tick();
            expectedDisplay.reset((FlashAnimationTimeline.Frame)
                    field("authoredBattleFrame").get(app), game, false);
            expectedDisplay.syncCityMarkers(game);
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false, true);
            bounds.set(SOURCE_BOUNDS);
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT));
            batch.setTransformMatrix(new Matrix4().idt());

            for (int sourceTick = 219; sourceTick <= 220; sourceTick++) {
                advance(app);
                if (sourceTick == 220) expectedFlag.tick();
                assertEquals("source handoff completes at tick 219",
                        TurnPlaybackSequence.Phase.IDLE,
                        ((TurnPlaybackSequence) field("turnPlaybackSequence").get(app)).getPhase());
                assertFalse("battle city state discarded",
                        ((BattlefieldCityRenderer) field("cityRenderer").get(app))
                                .isBattleActive());
                final FrameBuffer target = buffer;
                byte[] actual = render(target, batch,
                        () -> invoke(app, "drawBattlefieldDisplay"));
                byte[] expected = render(target, batch, () -> drawExpected(batch, bounds, game,
                        expectedDisplay, expectedCity, expectedFlag, menu));
                byte[] negative = render(target, batch, () -> drawOldBattle(batch, bounds, game,
                        oldBattle, expectedCity, oldBattleFlag, units, menu));
                String prefix = "source-phase-t" + sourceTick + '-';
                writeEvidence(prefix + "actual.png", actual);
                writeEvidence(prefix + "expected.png", expected);
                writeEvidence(prefix + "old-battle.png", negative);
                assertEquals("canonical full-stage RGBA at tick " + sourceTick,
                        0, differingPixels(actual, expected));
                assertTrue("retained battle root must paint differently at tick " + sourceTick,
                        differingPixels(negative, expected) > 0);
                assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            }
        } finally {
            if (buffer != null) buffer.dispose();
            expectedDisplay.clear();
            expectedCity.dispose();
            expectedFlag.dispose();
            oldBattleFlag.dispose();
            bounds.set(savedBounds);
            batch.setProjectionMatrix(savedProjection);
            batch.setTransformMatrix(savedTransform);
            batch.setShader(savedShader);
            batch.setPackedColor(savedColor);
        }
    }

    private static List<Clip> capture(BattlefieldDisplayState display) {
        List<Clip> result = new ArrayList<>();
        SourceDisplayList<BattlefieldDisplayState.Item> clips = display.clips();
        for (int index = 0; index < clips.size(); index++) {
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = clips.entryAt(index);
            BattlefieldDisplayState.Item item = entry.getValue();
            if (item.kind == BattlefieldDisplayState.Kind.CITY
                    || item.kind == BattlefieldDisplayState.Kind.CITY_MARKER
                    || item.kind == BattlefieldDisplayState.Kind.FLAG
                    || item.kind == BattlefieldDisplayState.Kind.ACTION_BUILDING) {
                result.add(new Clip(entry.getDepth(), item.kind, item.index, item.owner));
            }
        }
        return result;
    }

    private static void drawExpected(SpriteBatch batch, Rectangle bounds, GameState game,
                                     BattlefieldDisplayState display,
                                     BattlefieldCityRenderer cities,
                                     BattlefieldFlagRenderer flags,
                                     SourceActionMenuState menu) {
        SourceDisplayList<BattlefieldDisplayState.Item> clips = display.clips();
        for (int index = 0; index < clips.size(); index++) {
            BattlefieldDisplayState.Item item = clips.entryAt(index).getValue();
            draw(batch, bounds, game, cities, flags, null, menu,
                    item.kind, item.index, null);
        }
    }

    private static void drawOldBattle(SpriteBatch batch, Rectangle bounds, GameState game,
                                      List<Clip> oldBattle, BattlefieldCityRenderer cities,
                                      BattlefieldFlagRenderer flags,
                                      FlashBattleUnitRenderer units,
                                      SourceActionMenuState menu) {
        for (Clip clip : oldBattle) {
            draw(batch, bounds, game, cities, flags, units, menu,
                    clip.kind, clip.index, clip.owner);
        }
    }

    private static void draw(SpriteBatch batch, Rectangle bounds, GameState game,
                             BattlefieldCityRenderer cities, BattlefieldFlagRenderer flags,
                             FlashBattleUnitRenderer units, SourceActionMenuState menu,
                             BattlefieldDisplayState.Kind kind, int index, Object owner) {
        switch (kind) {
            case CITY:
                cities.drawCityBody(batch, bounds, game, menu.getSelectedEquipment(), index);
                break;
            case CITY_MARKER:
                cities.drawCityMarker(batch, bounds, game, index);
                break;
            case FLAG:
                flags.drawIsland(batch, bounds, game, index);
                break;
            case ACTION_BUILDING:
                if (units != null && owner instanceof FlashActionBuildingAnimation) {
                    units.drawActionBuilding(batch, bounds,
                            (FlashActionBuildingAnimation) owner);
                }
                break;
            default:
                break;
        }
    }

    private static byte[] render(FrameBuffer buffer, SpriteBatch batch, Draw draw)
            throws Exception {
        buffer.begin();
        try {
            Gdx.gl.glClearColor(1f, 1f, 1f, 1f);
            Gdx.gl.glStencilMask(0xff);
            Gdx.gl.glClearStencil(0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_STENCIL_BUFFER_BIT);
            batch.setShader(null);
            batch.setColor(1f, 1f, 1f, 1f);
            batch.begin();
            try { draw.draw(); }
            finally { batch.end(); }
            return ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, true);
        } finally {
            buffer.end();
        }
    }

    private static int differingPixels(byte[] first, byte[] second) {
        assertEquals(first.length, second.length);
        int changed = 0;
        for (int offset = 0; offset < first.length; offset += 4) {
            if (first[offset] != second[offset]
                    || first[offset + 1] != second[offset + 1]
                    || first[offset + 2] != second[offset + 2]
                    || first[offset + 3] != second[offset + 3]) changed++;
        }
        return changed;
    }

    private static void writeEvidence(String name, byte[] rgba) {
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Pixmap image = new Pixmap(WIDTH, HEIGHT, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(rgba).position(0);
            // ScreenUtils already returned top-down rows with flipY=true.
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()),
                    image, -1, false);
        } finally {
            image.dispose();
        }
    }

    private static void advance(WorldDominationGame app) throws Exception {
        invoke(app, "fixedUpdate", float.class, 1f / 24f);
    }

    private static Field field(String name) throws Exception {
        Field result = WorldDominationGame.class.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }

    private static void invoke(WorldDominationGame app, String name,
                               Class<?> argumentType, Object argument) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name, argumentType);
        method.setAccessible(true);
        method.invoke(app, argument);
    }

    private static final class Clip {
        final int depth;
        final BattlefieldDisplayState.Kind kind;
        final int index;
        final Object owner;

        Clip(int depth, BattlefieldDisplayState.Kind kind, int index, Object owner) {
            this.depth = depth;
            this.kind = kind;
            this.index = index;
            this.owner = owner;
        }
    }

    private interface Draw { void draw() throws Exception; }
}
