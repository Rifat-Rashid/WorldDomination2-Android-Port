package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Complete native setup composition and viewport-routed controls at both target sizes. */
@RunWith(AndroidJUnit4.class)
public final class SetupPanelLayoutInstrumentedTest {
    @Test public void fullSetupKeepsControlsAlignedWithSourcePanels() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            Viewport viewport = (Viewport) field("viewport").get(app);
            OrthographicCamera camera = (OrthographicCamera) field("camera").get(app);
            SetupScreenLayout layout = (SetupScreenLayout) field("setupLayout").get(app);
            Method draw = WorldDominationGame.class.getDeclaredMethod("drawSetupScreen");
            draw.setAccessible(true);
            try {
                for (int[] size : new int[][]{{800,600}, {1194,552}}) {
                    int width=size[0], height=size[1];
                    // Exercise the real drawing/input functions with exact logical sizes,
                    // independent of the emulator display's current aspect and cutout.
                    viewport.setWorldSize(width,height);
                    viewport.setScreenBounds(0,0,width,height);
                    camera.setToOrtho(false,width,height);
                    camera.update();
                    field("worldWidth").setFloat(app,width);
                    field("worldHeight").setFloat(app,height);
                    layout.update(width,height,0,0,0,0);
                    GameSetup setup = new GameSetup(0L);
                    field("setup").set(app,setup);
                    field("game").set(app,null);
                    field("setupLeaderListStart").setInt(app,0);
                    field("showSetupMessage").setBoolean(app,false);
                    assertFalse(setup.canStart());
                    tap(app,viewport,layout.startButton);
                    assertNull("Disabled Start cannot enter a match",field("game").get(app));
                    capture(app,draw,width,height,"disabled");
                    tap(app,viewport,layout.difficultyButtons[2]);
                    for (int color = 0; color < layout.colorSwatches.length; color++) {
                        tap(app,viewport,layout.colorSwatches[color]);
                        assertEquals("Each original color remains touch-selectable",
                                color + 1, setup.getPlayerColor());
                    }
                    tap(app,viewport,layout.colorSwatches[0]);
                    for (int building = 0; building < layout.buildingButtons.length; building++) {
                        tap(app, viewport, layout.buildingButtons[building]);
                        assertEquals("Every source building remains selectable", building + 1,
                                setup.getStartingBuilding());
                    }
                    tap(app,viewport,layout.buildingButtons[1]);
                    assertEquals(2,setup.getDifficulty());
                    assertEquals(1,setup.getPlayerColor());
                    assertEquals(2,setup.getStartingBuilding());
                    for (int card = 0; card < layout.leaderCards.length; card++) {
                        GameSetup isolated = new GameSetup(0L);
                        field("setup").set(app, isolated);
                        field("setupLeaderListStart").setInt(app, 0);
                        tap(app, viewport, layout.leaderCards[card]);
                        assertEquals("Every carousel well routes to its leader",
                                Arrays.asList(card), isolated.getSelectedOpponentIds());
                    }
                    assertLeaderNamesThroughViewport(app, viewport, layout, draw, width, height);
                    field("setup").set(app, setup);
                    field("setupLeaderListStart").setInt(app, 0);
                    tap(app, viewport, layout.leaderNext);
                    assertEquals(1, field("setupLeaderListStart").getInt(app));
                    tap(app, viewport, layout.leaderPrevious);
                    assertEquals(0, field("setupLeaderListStart").getInt(app));
                    tap(app, viewport, layout.leaderPrevious);
                    assertEquals(GameSetup.leaders().size() - 1, field("setupLeaderListStart").getInt(app));
                    tap(app, viewport, layout.leaderNext);
                    assertEquals(GameSetup.leaders().size(), field("setupLeaderListStart").getInt(app));
                    tap(app, viewport, layout.leaderPrevious);
                    assertEquals(GameSetup.leaders().size() - 1, field("setupLeaderListStart").getInt(app));
                    tap(app, viewport, layout.leaderNext);
                    assertEquals(GameSetup.leaders().size(), field("setupLeaderListStart").getInt(app));
                    tap(app, viewport, layout.leaderNext);
                    assertEquals(0, field("setupLeaderListStart").getInt(app));
                    tap(app,viewport,layout.leaderCards[4]);
                    tap(app,viewport,layout.leaderCards[0]);
                    assertEquals(Arrays.asList(4,0),setup.getSelectedOpponentIds());
                    assertTrue(setup.canStart());
                    assertTrue("Original footer remains exposed",
                            layout.leaderCarouselPanel.y > layout.enemiesPanel.y);
                    assertTrue(layout.leaderCarouselPanel.contains(layout.leaderDescription));
                    capture(app,draw,width,height,"enabled");
                    tap(app,viewport,layout.startButton);
                    assertNotNull("Enabled Start enters the match",field("game").get(app));
                    assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                }
            } finally {
                app.resize(Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
            }
        });
    }

    // Authored Model_gameMenu leader_arr names, independent of GameSetup's display values.
    private static final String[] SOURCE_NAMES = {
            "The Bombki", "M. Headinajar", "Col. Gedoffi", "Reh Moose Hunt",
            "Asama Bin Loader", "Any Kissonme", "Psyman Para's", "Ehum Oldbert",
            "Ehum Bareass", "Gladinme Putit", "Keen to Chill", "Angola Market",
            "Ballony Stare", "Who in There", "JP Barksender", "Jack Searock",
            "GW Blush", "Taxi Isinawater", "Bossi Isasad"
    };

    private static void assertLeaderNamesThroughViewport(WorldDominationGame app,
            Viewport viewport, SetupScreenLayout layout, Method draw, int width, int height)
            throws Exception {
        field("setupLeaderListStart").setInt(app, 0);
        for (int id = 0; id < SOURCE_NAMES.length; id++) {
            GameSetup isolated = new GameSetup(0L);
            field("setup").set(app, isolated);
            // Reach every source roster entry by the real carousel arrow, never by
            // injecting the visible leader or the selected opponent collection.
            if (id > 0) tap(app, viewport, layout.leaderNext);
            assertEquals("carousel source ID", id, field("setupLeaderListStart").getInt(app));
            Vector2 card = screen(viewport, layout.leaderCards[0]);
            app.mouseMoved((int) card.x, (int) card.y);
            assertEquals("hover source name " + id, SOURCE_NAMES[id], field("setupLeaderName").get(app));
            assertTrue("hover cannot choose a rival", isolated.getSelectedOpponentIds().isEmpty());
            Vector2 outside = screen(viewport, layout.leaderDescription);
            app.mouseMoved((int) outside.x, (int) outside.y);
            assertEquals("rollout clears source name " + id, "", field("setupLeaderName").get(app));
            app.touchDown((int) card.x, (int) card.y, 0, 0);
            assertEquals("name available before release " + id,
                    SOURCE_NAMES[id], field("setupLeaderName").get(app));
            assertTrue("press alone cannot choose a rival", isolated.getSelectedOpponentIds().isEmpty());
            app.touchUp((int) card.x, (int) card.y, 0, 0);
            assertEquals("released source name " + id, SOURCE_NAMES[id], field("setupLeaderName").get(app));
            assertEquals("touch chooses source ID " + id, Arrays.asList(id), isolated.getSelectedOpponentIds());
            if (id == 4) capture(app, draw, width, height, "leader-name");
        }
        // Wrap by the real arrow, then exercise capacity and duplicate feedback.
        tap(app, viewport, layout.leaderNext);
        assertEquals(19, field("setupLeaderListStart").getInt(app));
        Vector2 repeatedFirst = screen(viewport, layout.leaderCards[0]);
        app.mouseMoved((int) repeatedFirst.x, (int) repeatedFirst.y);
        assertEquals("source repeated page still identifies the first leader",
                SOURCE_NAMES[0], field("setupLeaderName").get(app));
        tap(app, viewport, layout.leaderNext);
        assertEquals(0, field("setupLeaderListStart").getInt(app));
        GameSetup full = new GameSetup(0L);
        field("setup").set(app, full);
        for (int id = 0; id < 4; id++) tap(app, viewport, layout.leaderCards[id]);
        tap(app, viewport, layout.leaderCards[2]);
        assertEquals("selected candidate keeps its name", SOURCE_NAMES[2], field("setupLeaderName").get(app));
        assertEquals("duplicate never toggles off", Arrays.asList(0, 1, 2, 3), full.getSelectedOpponentIds());
        tap(app, viewport, layout.leaderCards[4]);
        assertEquals("full roster still identifies candidate", SOURCE_NAMES[4], field("setupLeaderName").get(app));
        assertEquals("capacity rejection preserves roster", Arrays.asList(0, 1, 2, 3), full.getSelectedOpponentIds());
        tap(app, viewport, layout.opponentSlots[0]);
        assertEquals("removal leaves source roster order", Arrays.asList(1, 2, 3), full.getSelectedOpponentIds());
        assertEquals("selected-slot removal is outside candidate names", "", field("setupLeaderName").get(app));

        GameSetup dragged = new GameSetup(0L);
        field("setup").set(app, dragged);
        Vector2 card = screen(viewport, layout.leaderCards[0]);
        Vector2 outside = screen(viewport, layout.leaderDescription);
        app.mouseMoved((int) card.x, (int) card.y);
        app.mouseMoved((int) outside.x, (int) outside.y);
        assertEquals("moving outside clears source name", "", field("setupLeaderName").get(app));
        app.touchDown((int) card.x, (int) card.y, 0, 0);
        assertEquals(SOURCE_NAMES[0], field("setupLeaderName").get(app));
        app.touchDragged((int) outside.x, (int) outside.y, 0);
        assertEquals("dragging out clears name before release", "", field("setupLeaderName").get(app));
        app.touchUp((int) outside.x, (int) outside.y, 0, 0);
        assertTrue("drag-out cannot select a rival", dragged.getSelectedOpponentIds().isEmpty());
        assertEquals("drag-out remains cleared", "", field("setupLeaderName").get(app));

        app.touchDown((int) card.x, (int) card.y, 0, 0);
        Vector2 other = screen(viewport, layout.leaderCards[1]);
        assertFalse(app.touchDown((int) other.x, (int) other.y, 1, 0));
        assertFalse(app.touchDragged((int) outside.x, (int) outside.y, 1));
        assertFalse(app.touchUp((int) other.x, (int) other.y, 1, 0));
        assertEquals("secondary pointer cannot change primary name",
                SOURCE_NAMES[0], field("setupLeaderName").get(app));
        assertTrue("secondary pointer cannot select a rival", dragged.getSelectedOpponentIds().isEmpty());
        app.touchDragged((int) outside.x, (int) outside.y, 0);
        app.touchDragged((int) card.x, (int) card.y, 0);
        assertEquals("returning pointer restores name", SOURCE_NAMES[0], field("setupLeaderName").get(app));
        app.touchUp((int) card.x, (int) card.y, 0, 0);
        assertTrue("drag-out-and-back cannot become a tap", dragged.getSelectedOpponentIds().isEmpty());
        assertEquals("release retains returned pointer name", SOURCE_NAMES[0], field("setupLeaderName").get(app));
    }

    private static Vector2 screen(Viewport viewport, Rectangle bounds) {
        Vector2 point = viewport.project(new Vector2(bounds.x + bounds.width / 2f,
                bounds.y + bounds.height / 2f));
        return new Vector2(Math.round(point.x), Gdx.graphics.getHeight() - Math.round(point.y));
    }

    private static void tap(WorldDominationGame app,Viewport viewport,Rectangle bounds) {
        Vector2 screen=viewport.project(new Vector2(bounds.x+bounds.width/2f,
                bounds.y+bounds.height/2f));
        int x=Math.round(screen.x), y=Gdx.graphics.getHeight()-Math.round(screen.y);
        app.touchDown(x,y,0,0);
        app.touchUp(x,y,0,0);
    }

    private static void capture(WorldDominationGame app,Method draw,int width,int height,
                                String state) throws Exception {
        FrameBuffer buffer=new FrameBuffer(Pixmap.Format.RGBA8888,width,height,false);
        Pixmap image=new Pixmap(width,height,Pixmap.Format.RGBA8888);
        try {
            buffer.begin();
            try {
                Gdx.gl.glClearColor(0,0,0,1);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                draw.invoke(app);
                image.getPixels().put(ScreenUtils.getFrameBufferPixels(0,0,width,height,true));
                image.getPixels().position(0);
            } finally {buffer.end();}
            File directory=InstrumentationRegistry.getInstrumentation().getTargetContext()
                    .getExternalFilesDir("beta-ui-evidence");
            assertNotNull(directory);
            assertTrue(directory.isDirectory() || directory.mkdirs());
            File file=new File(directory,"source-setup-full-"+width+"x"+height+"-"+state+".png");
            PixmapIO.writePNG(Gdx.files.absolute(file.getAbsolutePath()),image);
            if ("leader-name".equals(state)) {
                Rectangle nameWell = ((SetupScreenLayout) field("setupLayout").get(app)).leaderDescription;
                int greenInk = 0;
                // Readback is top-down; stay inside the caption well, excluding chrome.
                int left = Math.max(0, (int) Math.ceil(nameWell.x + 2f));
                int right = Math.min(width, (int) Math.floor(nameWell.x + nameWell.width - 2f));
                int top = Math.max(0, height - (int) Math.floor(nameWell.y + nameWell.height - 2f));
                int bottom = Math.min(height, height - (int) Math.ceil(nameWell.y + 2f));
                for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
                    int rgba = image.getPixel(x, y);
                    int red = rgba >>> 24, green = (rgba >>> 16) & 255, blue = (rgba >>> 8) & 255;
                    if (green >= 160 && red <= 32 && blue <= 32) greenInk++;
                }
                assertTrue("Authored green leader caption is actually painted at " + width + "x" + height,
                        greenInk > 3);
            }
        } finally {image.dispose();buffer.dispose();}
    }

    private static Field field(String name) throws Exception {
        Field field=WorldDominationGame.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
