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
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
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
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.*;

/** Source-ordered GLES pixels for the legal soldier4 city-depth inversion. */
@RunWith(AndroidJUnit4.class)
public final class SourceSuicideDepthRenderingInstrumentedTest {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int CITY_ADDRESS = 7;
    private static final Rectangle SOURCE_BOUNDS = new Rectangle(0, 0, WIDTH, HEIGHT);
    private static final String UNIT_HEADER = "tick,role,action,status,storedParam,activeIndex,"
            + "deleteIndex,curX,curY,exists,attached,parent,sourceDepth,lastDepth,linkage,"
            + "visible,frame,alpha,clipX,clipY";
    private static final String CITY_HEADER = "tick,prevPopulation,population,curCasualties,"
            + "previousFrame,deathFrame,imageFrame,rawAlpha,targetFrame,hasOnEnterFrame,"
            + "randString,dustCount,dustFrame,dustDepth";

    @Test
    public void legalBattlePaintsBomberBehindCityAndRetainsExplosionUntilCleanup()
            throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(
                SourceSuicideDepthRenderingInstrumentedTest::verify);
    }

    private static void verify(WorldDominationGame app) throws Exception {
        GameState game = gameReadyForAttack();
        field("game").set(app, game);
        BattlefieldCityRenderer cities = (BattlefieldCityRenderer) field("cityRenderer").get(app);
        cities.reset(game);
        ((SourceBattleRecordClock) field("battleRecordClock").get(app)).reset();
        invoke(app, "confirmTurnAndPreparePlayback");

        SpriteBatch batch = (SpriteBatch) field("batch").get(app);
        Rectangle bounds = (Rectangle) field("mapBounds").get(app);
        Rectangle savedBounds = new Rectangle(bounds);
        Matrix4 savedProjection = new Matrix4(batch.getProjectionMatrix());
        Matrix4 savedTransform = new Matrix4(batch.getTransformMatrix());
        float savedColor = batch.getPackedColor();
        ShaderProgram savedShader = batch.getShader();
        FrameBuffer buffer = null;
        try {
            assertFalse(batch.isDrawing());
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, WIDTH, HEIGHT, false, true);
            bounds.set(SOURCE_BOUNDS);
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, WIDTH, HEIGHT));
            batch.setTransformMatrix(new Matrix4().idt());
            for (int tick = 1; tick <= 362; tick++) {
                invoke(app, "fixedUpdate", float.class, 1f / 24f);
                if (tick == 299 || tick == 300 || tick == 320 || tick == 361 || tick == 362) {
                    checkFrame(app, game, cities, batch, bounds, buffer, tick);
                }
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

    private static void checkFrame(WorldDominationGame app, GameState game,
                                   BattlefieldCityRenderer cities, SpriteBatch batch,
                                   Rectangle bounds, FrameBuffer buffer, int tick)
            throws Exception {
        BattlefieldDisplayState state = (BattlefieldDisplayState)
                field("battlefieldDisplay").get(app);
        SourceDisplayList<BattlefieldDisplayState.Item> clips = state.clips();
        SourceSuicideDisplayDepths.Binding binding = null;
        List<Command> commands = new ArrayList<>();
        for (int index = 0; index < clips.size(); index++) {
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = clips.entryAt(index);
            BattlefieldDisplayState.Item item = entry.getValue();
            if (item.owner instanceof SourceSuicideDisplayDepths.Binding) {
                binding = (SourceSuicideDisplayDepths.Binding) item.owner;
            } else if (isDrawnStatic(item.kind)) {
                commands.add(new Command(entry.getDepth(), item.kind, item, null, -1));
            }
        }
        assertNotNull("source suicide binding remains available through cleanup", binding);
        UnitRow[] rows = unitRows(tick);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item>[] entries = new SourceDisplayList.Entry[]{
                binding.body(), binding.tint(), binding.dot(), binding.explosion()
        };
        for (int role = 0; role < rows.length; role++) {
            UnitRow row = rows[role];
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = entries[role];
            assertEquals("root attachment tick " + tick + " " + row.role,
                    row.attached, entry != null && entry.isAttached());
            if (!row.attached) continue;
            assertEquals("source depth tick " + tick + " " + row.role,
                    row.depth, entry.getDepth());
            if (role != 2) assertEquals("native suicide draw role", role == 3 ? 2 : role,
                    entry.getValue().index);
            if (row.visible) commands.add(new Command(row.depth,
                    entry.getValue().kind, null, binding, role));
            if (role == 2) continue; // redDot is a separate fixed child.
            FlashSuicideBomberAnimation.Actor actor = binding.animation.getActor(
                    role == 3 ? 2 : role);
            assertEquals("source displayed X tick " + tick + " " + row.role,
                    row.clipX, actor.renderX, 0.03f);
            assertEquals("source displayed Y tick " + tick + " " + row.role,
                    row.clipY, actor.renderY, 0.03f);
            assertEquals("source one-based frame tick " + tick + " " + row.role,
                    row.frame, actor.sourceFrame + 1);
            assertEquals("source alpha tick " + tick + " " + row.role,
                    row.alpha / 100f, actor.alpha, 0.001f);
        }
        assertEquals("city7 source depth", 9011, state.city(CITY_ADDRESS).getDepth());
        if (tick == 299) {
            assertEquals(8125, entries[0].getDepth());
            assertEquals(8126, entries[1].getDepth());
            assertTrue(entries[0].getDepth() < state.city(CITY_ADDRESS).getDepth());
        }
        if (tick == 300) {
            assertEquals(22513, entries[3].getDepth());
            assertEquals(98982, cities.getCityDustName(CITY_ADDRESS));
            assertEquals(0, cities.getCityDustFrame(CITY_ADDRESS)); // source frame 1.
        }
        if (tick == 320 || tick == 361) assertEquals(21, rows[3].frame);
        if (tick == 362) assertFalse(entries[3].isAttached());
        CityRow city = cityRow(tick);
        assertEquals("source city frame tick " + tick,
                city.frame, cities.getSourceCityFrame(CITY_ADDRESS));
        assertEquals("source city alpha tick " + tick,
                city.alpha, cities.getSourceCityAlpha(CITY_ADDRESS), 0.001f);

        commands.sort(Comparator.comparingInt(command -> command.depth));
        List<Command> staticFirst = new ArrayList<>();
        for (Command command : commands) if (command.binding == null) staticFirst.add(command);
        for (Command command : commands) if (command.binding != null) staticFirst.add(command);
        byte[] actual = render(buffer, batch, () -> invoke(app, "drawBattlefieldDisplay"));
        byte[] expected = render(buffer, batch,
                () -> drawCommands(app, batch, bounds, game, commands));
        assertArrayEquals("full source-ordered composition tick " + tick, expected, actual);
        assertEquals("source-ordered painted crop tick " + tick, 0,
                differingPaintedCrop(actual, expected));
        if (tick == 299) {
            byte[] negative = render(buffer, batch,
                    () -> drawCommands(app, batch, bounds, game, staticFirst));
            writeEvidence("source-suicide-depth-t299-actual.png", actual);
            writeEvidence("source-suicide-depth-t299-expected.png", expected);
            writeEvidence("source-suicide-depth-t299-static-first.png", negative);
            assertTrue("static-first order changes original painted overlap",
                    differingPaintedCrop(negative, expected) > 0);
        } else if (tick == 300) {
            writeEvidence("source-suicide-depth-t300-actual.png", actual);
            writeEvidence("source-suicide-depth-t300-expected.png", expected);
            List<Command> withoutExplosion = new ArrayList<>();
            for (Command command : commands) {
                if (command.binding == null || command.suicideRole != 3)
                    withoutExplosion.add(command);
            }
            byte[] noExplosion = render(buffer, batch,
                    () -> drawCommands(app, batch, bounds, game, withoutExplosion));
            writeEvidence("source-suicide-depth-t300-no-explosion.png", noExplosion);
            assertTrue("source explosion paints the city overlap at impact",
                    differingImpactCrop(expected, noExplosion) > 0);
        } else if (tick == 320 || tick == 361 || tick == 362) {
            writeEvidence("source-suicide-depth-t" + tick + "-actual.png", actual);
        }
    }

    private static void drawCommands(WorldDominationGame app, SpriteBatch batch,
                                     Rectangle bounds, GameState game,
                                     List<Command> commands) throws Exception {
        BattlefieldCityRenderer cities = (BattlefieldCityRenderer) field("cityRenderer").get(app);
        BattlefieldFlagRenderer flags = (BattlefieldFlagRenderer) field("flagRenderer").get(app);
        FlashBattleUnitRenderer units = (FlashBattleUnitRenderer) field("battleUnitRenderer").get(app);
        SourceActionMenuState menu = (SourceActionMenuState) field("sourceActionMenu").get(app);
        for (Command command : commands) {
            switch (command.kind) {
                case CITY:
                    cities.drawCityBody(batch, bounds, game, menu.getSelectedEquipment(),
                            command.item.index);
                    break;
                case CITY_MARKER:
                    cities.drawCityMarker(batch, bounds, game, command.item.index);
                    break;
                case FLAG:
                    flags.drawIsland(batch, bounds, game, command.item.index);
                    break;
                case ACTION_BUILDING:
                    units.drawActionBuilding(batch, bounds,
                            (FlashActionBuildingAnimation) command.item.owner);
                    break;
                case UNIT_BODY:
                case UNIT_TINT:
                case EFFECT:
                    units.drawSuicideActor(batch, bounds, command.binding.animation.getActor(
                                    command.suicideRole == 3 ? 2 : command.suicideRole),
                            attackerColor(game, command.binding));
                    break;
                case UNIT_DOT:
                    units.drawTargetDot(batch, bounds, attackerColor(game, command.binding));
                    break;
                default:
                    throw new AssertionError("unexpected source command " + command.kind);
            }
        }
    }

    private static String attackerColor(GameState game, SourceSuicideDisplayDepths.Binding binding) {
        return GameSetup.colorName(game.getPlayerState(
                binding.animation.getAttackerPlayerId()).getPlayerColor());
    }

    private static boolean isDrawnStatic(BattlefieldDisplayState.Kind kind) {
        return kind == BattlefieldDisplayState.Kind.CITY
                || kind == BattlefieldDisplayState.Kind.CITY_MARKER
                || kind == BattlefieldDisplayState.Kind.FLAG
                || kind == BattlefieldDisplayState.Kind.ACTION_BUILDING;
    }

    private static GameState gameReadyForAttack() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        for (String technology : new String[]{"soldier2", "soldier3", "soldier4"}) {
            game.selectAction(GameState.Action.RESEARCH);
            assertTrue(game.selectResearchTechnology(technology));
            assertTrue(game.acceptSelectedAction());
            if ("soldier4".equals(technology)) {
                game.selectAction(GameState.Action.PRODUCTION);
                assertTrue(game.configureProduction("soldier", 100));
                assertTrue(game.acceptSelectedAction());
            } else resources(game);
            game.confirmTurn();
        }
        assertTrue(game.getHuman().getWeapons().get("soldier4").active);
        assertTrue(game.getHuman().getWeapons().get("soldier4").inStock > 0);
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(1, 2, "soldier4"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        return game;
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }

    private static UnitRow[] unitRows(int tick) throws Exception {
        UnitRow[] rows = new UnitRow[4];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open("source-suicide-unit.csv"), StandardCharsets.UTF_8))) {
            assertEquals(UNIT_HEADER, reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cell = line.split(",", -1);
                if (Integer.parseInt(cell[0]) != tick) continue;
                UnitRow row = new UnitRow(cell);
                rows[roleIndex(row.role)] = row;
            }
        }
        for (UnitRow row : rows) assertNotNull("four source roles at tick " + tick, row);
        return rows;
    }

    private static CityRow cityRow(int tick) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open("source-suicide-city.csv"), StandardCharsets.UTF_8))) {
            assertEquals(CITY_HEADER, reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cell = line.split(",", -1);
                if (Integer.parseInt(cell[0]) == tick) return new CityRow(cell);
            }
        }
        throw new AssertionError("missing source city tick " + tick);
    }

    private static int roleIndex(String name) {
        switch (name) {
            case "body": return 0;
            case "tint": return 1;
            case "dot": return 2;
            case "explosion": return 3;
            default: throw new AssertionError("unknown source role " + name);
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
            float callerColor = batch.getPackedColor();
            ShaderProgram callerShader = batch.getShader();
            float[] callerProjection = batch.getProjectionMatrix().val.clone();
            float[] callerTransform = batch.getTransformMatrix().val.clone();
            batch.begin();
            try {
                draw.draw();
                assertSame("caller shader restored", callerShader, batch.getShader());
                assertEquals("caller color restored", callerColor, batch.getPackedColor(), 0f);
                assertArrayEquals("caller projection restored", callerProjection,
                        batch.getProjectionMatrix().val, 0f);
                assertArrayEquals("caller transform restored", callerTransform,
                        batch.getTransformMatrix().val, 0f);
            }
            finally { batch.end(); }
            return ScreenUtils.getFrameBufferPixels(0, 0, WIDTH, HEIGHT, true);
        } finally {
            buffer.end();
        }
    }

    private static int differingPaintedCrop(byte[] left, byte[] right) {
        // Tick 299 source body/city two-layer overlap crop.
        return differingPixelsInCrop(left, right, 214, 166, 225, 179);
    }

    private static int differingImpactCrop(byte[] left, byte[] right) {
        // Read-only source tick300 city7 and explosion001 RGBA masks have 42
        // jointly painted pixels with exact exclusive bounds [196,154,201,163).
        return differingPixelsInCrop(left, right, 196, 154, 201, 163);
    }

    private static int differingPixelsInCrop(byte[] left, byte[] right,
                                             int minX, int minY,
                                             int maxXExclusive, int maxYExclusive) {
        assertEquals(left.length, right.length);
        int changed = 0;
        // Full native composition has tint and masks, so source mask counts
        // establish the region but are not asserted as Android pixel counts.
        for (int y = minY; y < maxYExclusive; y++) {
            for (int x = minX; x < maxXExclusive; x++) {
                int offset = (y * WIDTH + x) * 4;
                for (int channel = 0; channel < 4; channel++) {
                    if (left[offset + channel] != right[offset + channel]) {
                        changed++;
                        break;
                    }
                }
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

    private static void invoke(WorldDominationGame app, String name,
                               Class<?> type, Object argument) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(app, argument);
    }

    private static final class UnitRow {
        final String role;
        final boolean attached;
        final boolean visible;
        final int depth;
        final int frame;
        final float alpha;
        final float clipX;
        final float clipY;

        UnitRow(String[] cell) {
            role = cell[1];
            attached = "1".equals(cell[10]);
            visible = "1".equals(cell[15]);
            depth = Integer.parseInt(cell[12]);
            frame = Integer.parseInt(cell[16]);
            alpha = cell[17].isEmpty() ? 0f : Float.parseFloat(cell[17]);
            clipX = cell[18].isEmpty() ? 0f : Float.parseFloat(cell[18]);
            clipY = cell[19].isEmpty() ? 0f : Float.parseFloat(cell[19]);
        }
    }

    private static final class CityRow {
        final int frame;
        final float alpha;

        CityRow(String[] cell) {
            frame = Integer.parseInt(cell[6]);
            alpha = Float.parseFloat(cell[7]);
        }
    }

    private static final class Command {
        final int depth;
        final BattlefieldDisplayState.Kind kind;
        final BattlefieldDisplayState.Item item;
        final SourceSuicideDisplayDepths.Binding binding;
        final int suicideRole;

        Command(int depth, BattlefieldDisplayState.Kind kind,
                BattlefieldDisplayState.Item item, SourceSuicideDisplayDepths.Binding binding,
                int suicideRole) {
            this.depth = depth;
            this.kind = kind;
            this.item = item;
            this.binding = binding;
            this.suicideRole = suicideRole;
        }
    }

    private interface Draw { void draw() throws Exception; }
}
