package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.ui.SourceActionMenuState;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.*;

/** Real source-frame pixels versus an independently depth-sorted clip command list. */
@RunWith(AndroidJUnit4.class)
public final class SourceChinookDepthRenderingInstrumentedTest {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int TICK = 303;
    private static final int IMPACTED_CITY = 7;
    private static final Rectangle SOURCE_BOUNDS = new Rectangle(0, 0, WIDTH, HEIGHT);
    private static final String HEADER = "mode,tick,actor,unitName,mcID,action,role,exists,"
            + "attached,parentName,sourceDepth,lastDepth,linkage,visible,frame,curX,curY,"
            + "clipX,clipY,activeIndex,deleteIndex,unitDepthParam";

    @Test public void actualBattleDisplayFollowsSourceDepthAtPaintedTankCityOverlap()
            throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(
                SourceChinookDepthRenderingInstrumentedTest::verify);
    }

    @Test public void cityDustUsesSourceTransformAndFollowsCityFade() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(
                SourceChinookDepthRenderingInstrumentedTest::verifyCityDust);
    }

    private static void verify(WorldDominationGame app) throws Exception {
        GameState game = SourceChinookDepthsInstrumentedTest.gameReadyForAttack();
        field("game").set(app, game);
        BattlefieldCityRenderer cities = (BattlefieldCityRenderer) field("cityRenderer").get(app);
        cities.reset(game);
        ((SourceBattleRecordClock) field("battleRecordClock").get(app)).reset();
        invoke(app, "confirmTurnAndPreparePlayback");
        for (int tick = 1; tick <= TICK; tick++) invoke(app, "fixedUpdate", float.class, 1f / 24f);

        BattlefieldDisplayState state = (BattlefieldDisplayState)
                field("battlefieldDisplay").get(app);
        SourceDisplayList<BattlefieldDisplayState.Item> clips = state.clips();
        SourceChinookDisplayDepths.Binding[] bindings = new SourceChinookDisplayDepths.Binding[3];
        List<Command> expectedCommands = new ArrayList<>();
        for (int index = 0; index < clips.size(); index++) {
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = clips.entryAt(index);
            BattlefieldDisplayState.Item item = entry.getValue();
            if (item.owner instanceof SourceChinookDisplayDepths.Binding) {
                SourceChinookDisplayDepths.Binding binding =
                        (SourceChinookDisplayDepths.Binding) item.owner;
                bindings[binding.actorIndex] = binding;
            } else if (isDrawnStatic(item.kind)) {
                expectedCommands.add(new Command(entry.getDepth(), item.kind, item, null));
            }
        }
        for (int actor = 0; actor < bindings.length; actor++) assertNotNull(bindings[actor]);
        addSourceUnitCommands(expectedCommands, bindings);
        assertEquals("painted source tankBase depth", 10130,
                bindings[1].body().getDepth());
        assertEquals("painted source turret depth", 10131,
                bindings[2].body().getDepth());
        assertEquals("painted source city6 depth", 13510,
                state.city(6).getDepth());
        Collections.sort(expectedCommands, Comparator.comparingInt(command -> command.depth));
        for (int index = 1; index < expectedCommands.size(); index++) {
            assertTrue("distinct source direct depths", expectedCommands.get(index - 1).depth
                    < expectedCommands.get(index).depth);
        }
        List<Command> oldFamilyOrder = oldStaticThenChinookOrder(expectedCommands);

        SpriteBatch batch = (SpriteBatch) field("batch").get(app);
        Rectangle bounds = (Rectangle) field("mapBounds").get(app);
        Rectangle savedBounds = new Rectangle(bounds);
        Matrix4 savedProjection = new Matrix4(batch.getProjectionMatrix());
        Matrix4 savedTransform = new Matrix4(batch.getTransformMatrix());
        float savedColor = batch.getPackedColor();
        ShaderProgram savedShader = batch.getShader();
        FrameBuffer buffer = null;
        try {
            assertFalse("snapshot starts outside SpriteBatch", batch.isDrawing());
            // Real Android config requests an 8-bit stencil buffer; authored
            // action-building clipDepth masks must run under the same condition.
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false, true);
            bounds.set(SOURCE_BOUNDS);
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT));
            batch.setTransformMatrix(new Matrix4().idt());
            byte[] actual = render(buffer, batch, () -> invoke(app, "drawBattlefieldDisplay"));
            byte[] expected = render(buffer, batch,
                    () -> drawCommands(app, batch, bounds, game, expectedCommands));
            byte[] negative = render(buffer, batch,
                    () -> drawCommands(app, batch, bounds, game, oldFamilyOrder));
            writeEvidence("source-chinook-depth-t303-actual.png", actual);
            writeEvidence("source-chinook-depth-t303-expected.png", expected);
            writeEvidence("source-chinook-depth-t303-static-first.png", negative);
            assertEquals("source-ordered RGBA crop", 0, differingPixels(actual, expected));
            assertTrue("old static-first grouping must change a painted crop pixel",
                    differingPixels(negative, expected) > 0);
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
        } finally {
            if (buffer != null) buffer.dispose();
            bounds.set(savedBounds);
            batch.setProjectionMatrix(savedProjection);
            batch.setTransformMatrix(savedTransform);
            batch.setShader(savedShader);
            batch.setPackedColor(savedColor);
        }
    }

    private static void verifyCityDust(WorldDominationGame app) throws Exception {
        GameState game = SourceChinookDepthsInstrumentedTest.gameReadyForAttack();
        field("game").set(app, game);
        BattlefieldCityRenderer cities = (BattlefieldCityRenderer) field("cityRenderer").get(app);
        cities.reset(game);
        ((SourceBattleRecordClock) field("battleRecordClock").get(app)).reset();
        invoke(app, "confirmTurnAndPreparePlayback");

        OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets)
                field("flashAnimationAssets").get(app);
        OriginalCityAssets cityAssets = (OriginalCityAssets) field("cityAssets").get(app);
        assertEquals("source city parent scale", 0.8f, cityAssets.getArtworkScale(), 0.00001f);
        FlashAnimationTimeline dust = assets.getTimeline("explosion_dust");
        assertNotNull("source dust timeline cached", dust);
        assertEquals(50, dust.getFrameCount());

        SpriteBatch batch = (SpriteBatch) field("batch").get(app);
        Rectangle bounds = (Rectangle) field("mapBounds").get(app);
        Rectangle savedBounds = new Rectangle(bounds);
        Matrix4 savedProjection = new Matrix4(batch.getProjectionMatrix());
        Matrix4 savedTransform = new Matrix4(batch.getTransformMatrix());
        float savedColor = batch.getPackedColor();
        ShaderProgram savedShader = batch.getShader();
        SourceActionMenuState menu = (SourceActionMenuState) field("sourceActionMenu").get(app);
        String selectedEquipment = menu.getSelectedEquipment();
        FrameBuffer buffer = null;
        try {
            assertFalse("snapshot starts outside SpriteBatch", batch.isDrawing());
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false, true);
            bounds.set(SOURCE_BOUNDS);
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT));
            batch.setTransformMatrix(new Matrix4().idt());
            for (int tick = 1; tick <= 387; tick++) {
                invoke(app, "fixedUpdate", float.class, 1f / 24f);
                if (tick != 380 && tick != 383 && tick != 387) continue;
                final int currentTick = tick;
                byte[] actual = render(buffer, batch, () -> cities.drawCityBody(batch,
                        bounds, game, selectedEquipment, IMPACTED_CITY));
                byte[] bodyOnly = renderWithoutDust(buffer, batch, cities,
                        () -> cities.drawCityBody(batch, bounds, game,
                                selectedEquipment, IMPACTED_CITY));
                writeEvidence("source-city-dust-t" + tick + "-actual.png", actual);
                writeEvidence("source-city-dust-t" + tick + "-body-only.png", bodyOnly);
                if (tick == 387) {
                    assertEquals("city frame switch removed dynamic dust", -1,
                            cities.getCityDustFrame(IMPACTED_CITY));
                    assertEquals("no dust after source frame switch", 0,
                            differingDustPixels(actual, bodyOnly));
                    continue;
                }
                int frameIndex = tick == 380 ? 1 : 4;
                int shapeId = tick == 380 ? 2750 : 2752;
                int alpha = tick == 380 ? 70 : 25;
                assertEquals("source dust frame at tick " + tick, frameIndex,
                        cities.getCityDustFrame(IMPACTED_CITY));
                assertEquals("source city fade at tick " + tick, alpha,
                        cities.getSourceCityAlpha(IMPACTED_CITY));
                FlashAnimationTimeline.Frame frame = dust.getFrame(frameIndex);
                assertNotNull(frame);
                assertEquals("one direct dust leaf", 1, frame.layers.length);
                assertEquals("original frame leaf", shapeId, frame.layers[0].shapeId);
                byte[] expected = renderWithoutDust(buffer, batch, cities, () -> {
                    cities.drawCityBody(batch, bounds, game, selectedEquipment, IMPACTED_CITY);
                    drawIndependentDustLeaf(batch, bounds, assets, cityAssets, shapeId, alpha);
                });
                writeEvidence("source-city-dust-t" + currentTick + "-expected.png", expected);
                assertEquals("source dust placement and fade at tick " + tick, 0,
                        differingDustPixels(actual, expected));
                assertTrue("original dust leaf paints pixels at tick " + tick,
                        differingDustPixels(actual, bodyOnly) > 0);
            }
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
        } finally {
            if (buffer != null) buffer.dispose();
            bounds.set(savedBounds);
            batch.setProjectionMatrix(savedProjection);
            batch.setTransformMatrix(savedTransform);
            batch.setShader(savedShader);
            batch.setPackedColor(savedColor);
        }
    }

    @SuppressWarnings("unchecked")
    private static byte[] renderWithoutDust(FrameBuffer buffer, SpriteBatch batch,
                                            BattlefieldCityRenderer cities, Draw draw)
            throws Exception {
        Field sourceDust = BattlefieldCityRenderer.class.getDeclaredField("sourceDust");
        sourceDust.setAccessible(true);
        List<List<Object>> lists = (List<List<Object>>) sourceDust.get(cities);
        List<Object> cityDust = lists.get(IMPACTED_CITY);
        List<Object> saved = new ArrayList<>(cityDust);
        cityDust.clear();
        try {
            return render(buffer, batch, draw);
        } finally {
            cityDust.addAll(saved);
        }
    }

    private static void drawIndependentDustLeaf(SpriteBatch batch, Rectangle bounds,
                                                OriginalFlashAnimationAssets assets,
                                                OriginalCityAssets cityAssets,
                                                int shapeId, int cityAlpha) {
        TextureRegion region = assets.getShape(shapeId);
        assertNotNull("original dust SVG atlas leaf " + shapeId, region);
        float originX = assets.getShapeOriginX(shapeId);
        float originY = assets.getShapeOriginY(shapeId);
        assertEquals(-37f, originX, 0.001f);
        assertEquals(-41f, originY, 0.001f);
        // Source city7 is at (212.8, 166.75), scaled 80%. View attaches
        // explosion_dust at (-10,-3), scaled 130%, under that city root.
        float artworkScale = cityAssets.getArtworkScale();
        float sourceX = 212.8f + (-10f + 1.3f * originX) * artworkScale;
        float sourceY = 166.75f + (-3f + 1.3f * originY) * artworkScale;
        float x = BattlefieldCityLayout.sourceX(bounds, sourceX, 1);
        float top = BattlefieldCityLayout.sourceY(bounds, sourceY);
        float width = region.getRegionWidth() * artworkScale
                * BattlefieldCityLayout.sourceScale(bounds);
        float height = region.getRegionHeight() * artworkScale
                * BattlefieldCityLayout.sourceScale(bounds);
        batch.setShader(null);
        batch.setColor(1f, 1f, 1f, cityAlpha / 100f);
        batch.draw(region, x, top - height, 0f, height, width, height,
                1.3f, 1.3f, 0f);
    }

    private static int differingDustPixels(byte[] a, byte[] b) {
        assertEquals(a.length, b.length);
        int changed = 0;
        for (int y = 90; y <= 230; y++) {
            for (int x = 140; x <= 280; x++) {
                int offset = (y * WIDTH + x) * 4;
                if (a[offset] != b[offset] || a[offset + 1] != b[offset + 1]
                        || a[offset + 2] != b[offset + 2] || a[offset + 3] != b[offset + 3])
                    changed++;
            }
        }
        return changed;
    }

    private static boolean isDrawnStatic(BattlefieldDisplayState.Kind kind) {
        return kind == BattlefieldDisplayState.Kind.CITY
                || kind == BattlefieldDisplayState.Kind.CITY_MARKER
                || kind == BattlefieldDisplayState.Kind.FLAG
                || kind == BattlefieldDisplayState.Kind.ACTION_BUILDING;
    }

    private static void addSourceUnitCommands(List<Command> commands,
                                               SourceChinookDisplayDepths.Binding[] bindings)
            throws Exception {
        String[][] alias = tickRows("source-chinook-depths-alias.csv", "alias");
        String[][] literal = tickRows("source-chinook-depths-literal.csv", "literal");
        for (int sourceActor = 0; sourceActor < 2; sourceActor++) {
            // The carrier's constructor depth differs by one because the literal
            // tank_noShade attach fails. Post-drop base and turret do not differ.
            for (int field : new int[]{8, 10, 11, 12, 13, 14, 17, 18}) {
                assertEquals("literal/alias post-drop body field " + field,
                        literal[sourceActor * 5][field], alias[sourceActor * 5][field]);
            }
        }
        for (int row = 0; row < alias.length; row++) {
            String[] cells = alias[row];
            int sourceActor = Integer.parseInt(cells[2]);
            int nativeIndex = 2 - sourceActor;
            SourceChinookDisplayDepths.Binding binding = bindings[nativeIndex];
            String roleName = cells[6];
            if ("tint".equals(roleName) || "nestedShadow".equals(roleName)) {
                assertFalse("no independent root " + roleName,
                        Boolean.parseBoolean(cells[8]));
                continue;
            }
            BattlefieldDisplayState.Kind kind = role(roleName);
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> live = entry(binding, kind);
            boolean exists = Boolean.parseBoolean(cells[7]);
            assertEquals("source clip existence actor " + sourceActor + " " + roleName,
                    exists, live != null);
            if (!exists) continue;
            boolean attached = Boolean.parseBoolean(cells[8]);
            assertEquals("source attachment actor " + sourceActor + " " + roleName,
                    attached, live.isAttached());
            if (!attached) continue;
            assertFalse("attached clip has a depth", cells[10].isEmpty());
            int depth = Integer.parseInt(cells[10]);
            assertEquals("native occupancy actor " + sourceActor + " " + roleName,
                    depth, live.getDepth());
            if (Boolean.parseBoolean(cells[13]))
                commands.add(new Command(depth, kind, null, binding));
        }
    }

    private static String[][] tickRows(String asset, String mode) throws Exception {
        String[][] rows = new String[15][];
        int found = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open(asset), StandardCharsets.UTF_8))) {
            assertEquals(HEADER, reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                assertEquals(22, cells.length);
                if (Integer.parseInt(cells[1]) != TICK) continue;
                assertEquals(mode, cells[0]);
                int actor = Integer.parseInt(cells[2]);
                assertTrue(actor >= 0 && actor < 3);
                int slot = actor * 5 + roleSlot(cells[6]);
                assertNull("unique source role", rows[slot]);
                rows[slot] = cells;
                found++;
            }
        }
        assertEquals("three source wrappers times five roles", 15, found);
        for (String[] row : rows) assertNotNull(row);
        return rows;
    }

    private static int roleSlot(String value) {
        switch (value) {
            case "body": return 0;
            case "shadow": return 1;
            case "tint": return 2;
            case "dot": return 3;
            case "nestedShadow": return 4;
            default: throw new AssertionError("unknown source clip role " + value);
        }
    }

    private static SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry(
            SourceChinookDisplayDepths.Binding binding, BattlefieldDisplayState.Kind kind) {
        switch (kind) {
            case UNIT_BODY: return binding.body();
            case UNIT_SHADOW: return binding.shadow();
            case UNIT_DOT: return binding.dot();
            default: throw new AssertionError("unexpected Chinook kind " + kind);
        }
    }

    private static BattlefieldDisplayState.Kind role(String value) {
        switch (value) {
            case "body": return BattlefieldDisplayState.Kind.UNIT_BODY;
            case "shadow": return BattlefieldDisplayState.Kind.UNIT_SHADOW;
            case "dot": return BattlefieldDisplayState.Kind.UNIT_DOT;
            default: throw new AssertionError("unknown direct clip role " + value);
        }
    }

    private static List<Command> oldStaticThenChinookOrder(List<Command> sorted) {
        List<Command> result = new ArrayList<>(sorted.size());
        for (Command command : sorted) if (command.binding == null) result.add(command);
        for (int actor : new int[]{1, 2, 0}) {
            for (Command command : sorted) {
                if (command.binding != null && command.binding.actorIndex == actor
                        && command.kind == BattlefieldDisplayState.Kind.UNIT_BODY) result.add(command);
            }
        }
        for (Command command : sorted) {
            if (command.binding != null && command.kind == BattlefieldDisplayState.Kind.UNIT_SHADOW)
                result.add(command);
        }
        for (int actor = 0; actor < 3; actor++) {
            for (Command command : sorted) {
                if (command.binding != null && command.binding.actorIndex == actor
                        && command.kind == BattlefieldDisplayState.Kind.UNIT_DOT) result.add(command);
            }
        }
        assertEquals(sorted.size(), result.size());
        return result;
    }

    private static void drawCommands(WorldDominationGame app, SpriteBatch batch,
                                     Rectangle bounds, GameState game, List<Command> commands)
            throws Exception {
        BattlefieldCityRenderer city = (BattlefieldCityRenderer) field("cityRenderer").get(app);
        BattlefieldFlagRenderer flags = (BattlefieldFlagRenderer) field("flagRenderer").get(app);
        FlashBattleUnitRenderer units = (FlashBattleUnitRenderer) field("battleUnitRenderer").get(app);
        SourceActionMenuState menu = (SourceActionMenuState) field("sourceActionMenu").get(app);
        for (Command command : commands) {
            switch (command.kind) {
                case CITY:
                    city.drawCityBody(batch, bounds, game, menu.getSelectedEquipment(),
                            command.item.index);
                    break;
                case CITY_MARKER:
                    city.drawCityMarker(batch, bounds, game, command.item.index);
                    break;
                case FLAG:
                    flags.drawIsland(batch, bounds, game, command.item.index);
                    break;
                case ACTION_BUILDING:
                    units.drawActionBuilding(batch, bounds,
                            (FlashActionBuildingAnimation) command.item.owner);
                    break;
                case UNIT_BODY:
                    units.drawChinookActor(batch, bounds, command.binding.animation,
                            command.binding.actorIndex, false);
                    break;
                case UNIT_SHADOW:
                    units.drawChinookActor(batch, bounds, command.binding.animation,
                            command.binding.actorIndex, true);
                    break;
                case UNIT_DOT:
                    units.drawTargetDot(batch, bounds,
                            GameSetup.colorName(game.getPlayerState(
                                    command.binding.animation.getAttackerPlayerId())
                                    .getPlayerColor()));
                    break;
                default: throw new AssertionError("unexpected command " + command.kind);
            }
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

    private static int differingPixels(byte[] a, byte[] b) {
        assertEquals(a.length, b.length);
        int changed = 0;
        // SOURCE_BOUNDS has scale 1 and zero crop margin. ScreenUtils flipY=true
        // returns source top-down rows, so the source-painted crop is unchanged.
        for (int y = 194; y <= 237; y++) {
            for (int x = 136; x <= 181; x++) {
                int offset = (y * WIDTH + x) * 4;
                if (a[offset] != b[offset] || a[offset + 1] != b[offset + 1]
                        || a[offset + 2] != b[offset + 2]
                        || a[offset + 3] != b[offset + 3]) changed++;
            }
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

    private static Field field(String name) throws Exception {
        Field field = WorldDominationGame.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(app);
    }

    private static void invoke(WorldDominationGame app, String name, Class<?> argumentType,
                               Object argument) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name, argumentType);
        method.setAccessible(true);
        method.invoke(app, argument);
    }

    private static final class Command {
        final int depth;
        final BattlefieldDisplayState.Kind kind;
        final BattlefieldDisplayState.Item item;
        final SourceChinookDisplayDepths.Binding binding;

        Command(int depth, BattlefieldDisplayState.Kind kind,
                BattlefieldDisplayState.Item item, SourceChinookDisplayDepths.Binding binding) {
            this.depth = depth;
            this.kind = kind;
            this.item = item;
            this.binding = binding;
        }
    }

    private interface Draw { void draw() throws Exception; }
}
