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
                    field("setup").set(app, setup);
                    field("setupLeaderListStart").setInt(app, 0);
                    tap(app, viewport, layout.leaderNext);
                    assertEquals(1, field("setupLeaderListStart").getInt(app));
                    tap(app, viewport, layout.leaderPrevious);
                    assertEquals(0, field("setupLeaderListStart").getInt(app));
                    tap(app, viewport, layout.leaderPrevious);
                    assertEquals(GameSetup.leaders().size() - 1, field("setupLeaderListStart").getInt(app));
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
        } finally {image.dispose();buffer.dispose();}
    }

    private static Field field(String name) throws Exception {
        Field field=WorldDominationGame.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
