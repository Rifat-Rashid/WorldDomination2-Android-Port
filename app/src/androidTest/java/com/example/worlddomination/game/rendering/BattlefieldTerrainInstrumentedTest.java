package com.example.worlddomination.game.rendering;

import com.example.worlddomination.testing.GameActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.assets.OriginalBattlefieldTerrainAssets;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real GLES proof of terrain styles, empty slots, retained conquest styles and texture reuse. */
@RunWith(AndroidJUnit4.class)
public final class BattlefieldTerrainInstrumentedTest {
    @Test public void terrainMatchesOriginalNationAndCachesOnlyPresentIslands() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                BattlefieldTerrainRenderer terrain = null;
                SpriteBatch batch = null;
                FrameBuffer buffer = null;
                try {
                    terrain = new BattlefieldTerrainRenderer();
                    Field assetsField = BattlefieldTerrainRenderer.class.getDeclaredField("assets");
                    assetsField.setAccessible(true);
                    OriginalBattlefieldTerrainAssets assets = (OriginalBattlefieldTerrainAssets) assetsField.get(terrain);
                    batch = new SpriteBatch();
                    Set<Long> styles = new HashSet<>();
                    TextureRegion cachedOpponent = null;
                    for (int width : new int[]{986, 1200}) {
                        buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, 600, false);
                        OrthographicCamera camera = new OrthographicCamera();
                        camera.setToOrtho(false, width, 600); batch.setProjectionMatrix(camera.combined);
                        Rectangle bounds = new Rectangle(0, 0, width, 600);
                        for (int building = 1; building <= 5; building++) {
                            GameSetup setup = new GameSetup(0);
                            setup.toggleOpponent(0); setup.setStartingBuilding(building);
                            GameState game = new GameState(setup, 7L);
                            terrain.prepare(game);
                            assertEquals("Unused islands have no GPU tile", 2, terrain.getSelectedTileCount());
                            assertNull(assets.getTile(2)); assertNull(assets.getTile(3)); assertNull(assets.getTile(4));
                            assertTrue("Selected file-backed tile must be managed", assets.getTile(0).region.getTexture().isManaged());
                            if (cachedOpponent != null) assertSame("Unchanged tiles are reused", cachedOpponent, assets.getTile(1).region);
                            cachedOpponent = assets.getTile(1).region;
                            TextureRegion humanTile = assets.getTile(0).region;
                            terrain.prepare(game);
                            assertSame("Repeated preparation cannot upload another texture", humanTile, assets.getTile(0).region);
                            Pixmap image = render(buffer, batch, terrain, bounds);
                            try {
                                long signature = signature(image, bounds, 425, 345, 0, 50);
                                if (width == 986) assertTrue("Each source terrain style differs", styles.add(signature));
                                for (int island = 2; island < 5; island++) {
                                    int x = Math.round(BattlefieldCityLayout.sourceX(bounds, island == 2 ? 665 : island == 3 ? 150 : 665, island));
                                    int y = Math.round(BattlefieldCityLayout.sourceY(bounds, island == 2 ? 145 : island == 3 ? 500 : 490));
                                    int pixel = image.getPixel(x, y);
                                    int r = pixel >>> 24 & 255, g = pixel >>> 16 & 255, b = pixel >>> 8 & 255;
                                    assertTrue("Unused slot is authored ocean", b > r + 15 && b > g + 5);
                                }
                                capture(image, "source-terrain-" + width + "-building-" + building + ".png");
                            } finally { image.dispose(); }
                            Pixmap before = render(buffer, batch, terrain, bounds);
                            game.getPlayerState(1).alive = false;
                            Field attacker = GameState.PlayerState.class.getDeclaredField("lastAttackedByPlayerId");
                            attacker.setAccessible(true); attacker.setInt(game.getPlayerState(1), 0);
                            terrain.prepare(game);
                            assertSame("Conquest keeps original terrain", cachedOpponent, assets.getTile(1).region);
                            Pixmap after = render(buffer, batch, terrain, bounds);
                            try { assertEquals(signature(before, bounds, 140, 145, 1, 50), signature(after, bounds, 140, 145, 1, 50)); }
                            finally { before.dispose(); after.dispose(); }
                        }
                        GameSetup full = new GameSetup(0);
                        for (int leader : new int[]{0, 1, 3, 6}) assertTrue(full.toggleOpponent(leader));
                        terrain.prepare(new GameState(full, 9L));
                        assertEquals(5, terrain.getSelectedTileCount());
                        Pixmap fullImage = render(buffer, batch, terrain, bounds);
                        try { capture(fullImage, "source-terrain-" + width + "-five-islands.png"); }
                        finally { fullImage.dispose(); }
                        terrain.prepare(null);
                        assertEquals("Leaving a match releases selected tiles", 0, terrain.getSelectedTileCount());
                        cachedOpponent = null;
                        buffer.dispose(); buffer = null;
                    }
                    assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                } catch (Throwable error) { failure.set(error); }
                finally {
                    if (buffer != null) buffer.dispose();
                    if (batch != null) batch.dispose();
                    if (terrain != null) terrain.dispose();
                    done.countDown();
                }
            }));
            assertTrue("Original terrain test timed out", done.await(60, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Native original terrain", failure.get());
        }
    }

    private static Pixmap render(FrameBuffer buffer, SpriteBatch batch, BattlefieldTerrainRenderer terrain, Rectangle bounds) {
        buffer.begin(); Gdx.gl.glClearColor(0, 0, 0, 1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin(); batch.setColor(.25f, .5f, .75f, .5f);
        float color = batch.getPackedColor(); Object shader = batch.getShader();
        terrain.draw(batch, bounds);
        assertEquals("Restore caller color", color, batch.getPackedColor(), 0);
        assertSame("Restore caller shader", shader, batch.getShader());
        batch.end();
        Pixmap image = ScreenUtils.getFrameBufferPixmap(0, 0, buffer.getWidth(), buffer.getHeight());
        buffer.end();
        int black = 0;
        for (int y = 12; y < image.getHeight() - 12; y++) for (int x = 12; x < image.getWidth() - 12; x++)
            if (image.getPixel(x, y) >>> 8 == 0) black++;
        assertTrue("Widened ocean cannot expose black gaps: " + black, black < 200);
        return image;
    }

    private static long signature(Pixmap image, Rectangle bounds, float x, float y, int island, int radius) {
        int px = Math.round(BattlefieldCityLayout.sourceX(bounds, x, island));
        int py = Math.round(BattlefieldCityLayout.sourceY(bounds, y));
        long result = 1;
        for (int yy = py - radius; yy < py + radius; yy++) for (int xx = px - radius; xx < px + radius; xx++)
            result = 31 * result + image.getPixel(xx, yy);
        return result;
    }

    private static void capture(Pixmap image, String name) {
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()), image);
    }
}
