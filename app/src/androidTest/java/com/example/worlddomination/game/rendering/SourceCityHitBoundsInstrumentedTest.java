package com.example.worlddomination.game.rendering;

import com.example.worlddomination.testing.GameActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.MainActivity;
import com.example.worlddomination.game.assets.OriginalCityAssets;
import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Uses packaged city frames and a live Android GL context to check roof touch geometry. */
@RunWith(AndroidJUnit4.class)
public final class SourceCityHitBoundsInstrumentedTest {
    @Test
    public void tallCityRoofsUseCurrentArtworkBoundsAcrossLandscapeMaps() throws Exception {
        try (GameActivityScenario scenario = GameActivityScenario.launch(MainActivity.class)) {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            scenario.onActivity(activity -> Gdx.app.postRunnable(() -> {
                OriginalCityAssets assets = null;
                BattlefieldCityRenderer renderer = null;
                try {
                    assets = new OriginalCityAssets();
                    renderer = new BattlefieldCityRenderer(assets, null);
                    GameSetup setup = new GameSetup();
                    setup.toggleOpponent(0);
                    setup.toggleOpponent(4);
                    setup.setStartingBuilding(2); // Christian buildings in GameSetup.
                    GameState game = new GameState(setup, 2L);
                    assertEquals("chr", game.getHuman().getReligionCode());
                    assertEquals(6, game.getHuman().getCity(0).getArtworkFrame());
                    renderer.reset(game);
                    double nextRandom = game.createPresentationRandomCopy().nextDouble();
                    assertMap(renderer, assets, game, new Rectangle(0f, 0f, 523f, 318f), true);
                    assertMap(renderer, assets, game, new Rectangle(0f, 0f, 1194f, 552f), false);
                    assertEquals(nextRandom,
                            game.createPresentationRandomCopy().nextDouble(), 0d);
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    if (renderer != null) renderer.dispose();
                    if (assets != null) assets.dispose();
                    done.countDown();
                }
            }));
            assertTrue("Native city hit test timed out", done.await(90, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError("Native city hit bounds", failure.get());
        }
    }

    private static void assertMap(BattlefieldCityRenderer renderer, OriginalCityAssets assets,
                                  GameState game, Rectangle map, boolean checkGoldenPoint) {
        Rectangle frameBounds = new Rectangle();
        CityState city = game.getHuman().getCity(0);
        OriginalCityAssets.Frame frame = assets.getFrame(
                game.getHuman().getReligionCode(), city.getArtworkFrame());
        assertEquals(76, frame.region.getRegionWidth());
        assertEquals(113, frame.region.getRegionHeight());
        assertEquals(-19f, frame.offsetX, 0f);
        assertEquals(-96f, frame.offsetY, 0f);
        assertEquals(.8f, assets.getArtworkScale(), 0f);
        CitySpriteHitGeometry.setBounds(frameBounds, map, 0, 0,
                frame.offsetX, frame.offsetY, frame.region.getRegionWidth(),
                frame.region.getRegionHeight(), assets.getArtworkScale());
        float anchorX = BattlefieldCityLayout.x(map, 0, 0);
        float anchorY = BattlefieldCityLayout.y(map, 0, 0);
        float roofX = anchorX;
        float roofY = frameBounds.y + frameBounds.height - 5f;
        float baseY = frameBounds.y + 2f;
        if (checkGoldenPoint) {
            assertEquals(227.3945f, roofX, .001f);
            assertEquals(132.853f, roofY, .001f);
        }
        assertTrue(map.contains(roofX, roofY));
        assertTrue(map.contains(roofX, baseY));
        float dx = roofX - anchorX;
        float dy = roofY - anchorY;
        float radius = BattlefieldCityLayout.selectionRadius(map);
        assertTrue("Roof lies outside the old hit radius", dx * dx + dy * dy > radius * radius);
        assertNotEquals("The old circular hitbox misses the roof", 0,
                BattlefieldCityLayout.hitTest(roofX, roofY, map));
        assertEquals(0, renderer.hitTest(roofX, roofY, map, game));
        assertEquals(0, renderer.hitTest(roofX, baseY, map, game));
        assertNotEquals("Just outside the current frame", 0,
                renderer.hitTest(roofX, frameBounds.y + frameBounds.height + 1f,
                        map, game));
        assertTrue(game.selectBattlefieldCity(0, 0));
        game.cancelSelection();
        game.clearBattlefieldCitySelection();
        assertEquals(-1, game.getSelectedMapCityOwner());
        assertEquals(0, renderer.hitTest(roofX, roofY, map, game));
    }
}
