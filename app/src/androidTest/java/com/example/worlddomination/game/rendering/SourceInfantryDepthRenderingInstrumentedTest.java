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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.*;

/** Real source-frame pixels versus an independently depth-sorted clip command list. */
@RunWith(AndroidJUnit4.class)
public final class SourceInfantryDepthRenderingInstrumentedTest {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int TICK = 215;
    private static final Rectangle SOURCE_BOUNDS = new Rectangle(0, 0, WIDTH, HEIGHT);
    private static final String HEADER = "tick,actor,unitName,mcID,action,role,exists,"
            + "attached,visible,sourceDepth,lastDepth,linkage,frame,activeIndex,"
            + "deleteIndex,unitDepthParam,curX,curY,clipX,clipY";

    @Test public void actualBattleDisplayFollowsSourceDepthAtPaintedBoatCityOverlap()
            throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(
                SourceInfantryDepthRenderingInstrumentedTest::verify);
    }

    private static void verify(WorldDominationGame app) throws Exception {
        GameState game = firstTurn();
        field("game").set(app, game);
        BattlefieldCityRenderer cities = (BattlefieldCityRenderer) field("cityRenderer").get(app);
        cities.reset(game);
        ((SourceBattleRecordClock) field("battleRecordClock").get(app)).reset();
        invoke(app, "confirmTurnAndPreparePlayback");
        for (int tick = 1; tick <= TICK; tick++) invoke(app, "fixedUpdate", float.class, 1f / 24f);

        BattlefieldDisplayState state = (BattlefieldDisplayState)
                field("battlefieldDisplay").get(app);
        SourceDisplayList<BattlefieldDisplayState.Item> clips = state.clips();
        SourceInfantryDisplayDepths.Binding[] bindings = new SourceInfantryDisplayDepths.Binding[4];
        List<Command> expectedCommands = new ArrayList<>();
        for (int index = 0; index < clips.size(); index++) {
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = clips.entryAt(index);
            BattlefieldDisplayState.Item item = entry.getValue();
            if (item.owner instanceof SourceInfantryDisplayDepths.Binding) {
                SourceInfantryDisplayDepths.Binding binding =
                        (SourceInfantryDisplayDepths.Binding) item.owner;
                bindings[binding.actorIndex] = binding;
            } else if (isDrawnStatic(item.kind)) {
                expectedCommands.add(new Command(entry.getDepth(), item.kind, item, null));
            }
        }
        for (int actor = 0; actor < bindings.length; actor++) assertNotNull(bindings[actor]);
        addSourceUnitCommands(expectedCommands, bindings);
        assertEquals("painted source boat body depth", 12140,
                bindings[0].body().getDepth());
        assertEquals("painted source boat tint depth", 12141,
                bindings[0].tint().getDepth());
        assertEquals("painted source city6 depth", 13510,
                state.city(6).getDepth());
        Collections.sort(expectedCommands, Comparator.comparingInt(command -> command.depth));
        for (int index = 1; index < expectedCommands.size(); index++) {
            assertTrue("distinct source direct depths", expectedCommands.get(index - 1).depth
                    < expectedCommands.get(index).depth);
        }
        List<Command> oldFamilyOrder = oldStaticThenInfantryOrder(expectedCommands);

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
            writeEvidence("source-infantry-depth-t215-actual.png", actual);
            writeEvidence("source-infantry-depth-t215-expected.png", expected);
            writeEvidence("source-infantry-depth-t215-static-first.png", negative);
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

    private static boolean isDrawnStatic(BattlefieldDisplayState.Kind kind) {
        return kind == BattlefieldDisplayState.Kind.CITY
                || kind == BattlefieldDisplayState.Kind.CITY_MARKER
                || kind == BattlefieldDisplayState.Kind.FLAG
                || kind == BattlefieldDisplayState.Kind.ACTION_BUILDING;
    }

    private static void addSourceUnitCommands(List<Command> commands,
                                               SourceInfantryDisplayDepths.Binding[] bindings)
            throws Exception {
        int seen = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                        .open("source-infantry-depths-2.csv"), StandardCharsets.UTF_8))) {
            assertEquals(HEADER, reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(",", -1);
                assertEquals(20, cells.length);
                if (Integer.parseInt(cells[0]) != TICK) continue;
                seen++;
                int nativeIndex = Integer.parseInt(cells[1]);
                assertTrue(nativeIndex >= 0 && nativeIndex < bindings.length);
                SourceInfantryDisplayDepths.Binding binding = bindings[nativeIndex];
                BattlefieldDisplayState.Kind kind = role(cells[5]);
                SourceDisplayList.Entry<BattlefieldDisplayState.Item> live = entry(binding, kind);
                assertEquals("source attachment actor " + nativeIndex + " " + kind,
                        Boolean.parseBoolean(cells[7]), live.isAttached());
                if (!Boolean.parseBoolean(cells[7])) continue;
                assertFalse("attached clip has a depth", cells[9].isEmpty());
                int depth = Integer.parseInt(cells[9]);
                assertEquals("native occupancy actor " + nativeIndex + " " + kind,
                        depth, live.getDepth());
                if (Boolean.parseBoolean(cells[8]))
                    commands.add(new Command(depth, kind, null, binding));
            }
        }
        assertEquals("four source wrappers times three roles", 12, seen);
    }

    private static SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry(
            SourceInfantryDisplayDepths.Binding binding, BattlefieldDisplayState.Kind kind) {
        switch (kind) {
            case UNIT_BODY: return binding.body();
            case UNIT_TINT: return binding.tint();
            case UNIT_DOT: return binding.dot();
            default: throw new AssertionError("unexpected infantry kind " + kind);
        }
    }

    private static BattlefieldDisplayState.Kind role(String value) {
        switch (value) {
            case "body": return BattlefieldDisplayState.Kind.UNIT_BODY;
            case "tint": return BattlefieldDisplayState.Kind.UNIT_TINT;
            case "dot": return BattlefieldDisplayState.Kind.UNIT_DOT;
            default: throw new AssertionError("unknown source clip role " + value);
        }
    }

    private static List<Command> oldStaticThenInfantryOrder(List<Command> sorted) {
        List<Command> result = new ArrayList<>(sorted.size());
        for (Command command : sorted) if (command.binding == null) result.add(command);
        for (int actor = 0; actor < 4; actor++) {
            for (BattlefieldDisplayState.Kind role : new BattlefieldDisplayState.Kind[]{
                    BattlefieldDisplayState.Kind.UNIT_BODY,
                    BattlefieldDisplayState.Kind.UNIT_TINT,
                    BattlefieldDisplayState.Kind.UNIT_DOT}) {
                for (Command command : sorted) {
                    if (command.binding != null && command.binding.actorIndex == actor
                            && command.kind == role) result.add(command);
                }
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
                    units.drawInfantryActor(batch, bounds, command.binding.animation,
                            command.binding.actorIndex, false);
                    break;
                case UNIT_TINT:
                    units.drawInfantryActor(batch, bounds, command.binding.animation,
                            command.binding.actorIndex, true);
                    break;
                case UNIT_DOT:
                    units.drawTargetDot(batch, bounds,
                            command.binding.animation.getTint());
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
        for (int y = 220; y <= 263; y++) {
            for (int x = 150; x <= 183; x++) {
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

    private static GameState firstTurn() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(1, 0, "soldier1"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        return game;
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
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
        final SourceInfantryDisplayDepths.Binding binding;

        Command(int depth, BattlefieldDisplayState.Kind kind,
                BattlefieldDisplayState.Item item, SourceInfantryDisplayDepths.Binding binding) {
            this.depth = depth;
            this.kind = kind;
            this.item = item;
            this.binding = binding;
        }
    }

    private interface Draw { void draw() throws Exception; }
}
