package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import java.io.File;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Repeated full World paints at one frozen gameplay state. */
@RunWith(AndroidJUnit4.class)
public final class HudTextStabilityInstrumentedTest {
    @Test public void repeatedFrozenWorldPaintsKeepEveryHudPixelStable() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameSetup setup = new GameSetup(0L);
            setup.setDifficulty(1);
            assertTrue(setup.toggleOpponent(0));
            assertTrue(setup.toggleOpponent(1));
            GameState game = new GameState(setup);
            field("game").set(app, game);
            field("modalOpen").setBoolean(app, false);
            ((BattlefieldCityRenderer) field("cityRenderer").get(app)).reset(game);
            int turn = game.getTurn();
            int money = game.getHuman().moneyMillions;
            double canonical = game.createPresentationRandomCopy().nextDouble();
            int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
            byte[][] frames = new byte[6][];
            for (int i = 0; i < frames.length; i++) {
                field("skipNextFrameDelta").setBoolean(app, true);
                app.render();
                frames[i] = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
                assertEquals("turn stays frozen", turn, game.getTurn());
                assertEquals("money stays frozen", money, game.getHuman().moneyMillions);
                assertEquals("render never consumes canonical RNG", canonical,
                        game.createPresentationRandomCopy().nextDouble(), 0d);
            }
            evidence("source-hud-text-stability-initial.png", frames[0], width, height);
            evidence("source-hud-text-stability-final.png", frames[5], width, height);
            int distinct = 0;
            for (int i = 4; i < frames[0].length; i += 4) {
                if (frames[0][i] != frames[0][0] || frames[0][i+1] != frames[0][1]
                        || frames[0][i+2] != frames[0][2]) distinct++;
            }
            assertTrue("actual World paints visible content", distinct > 100);
            for (int i = 1; i < frames.length; i++)
                assertArrayEquals("identical frozen World RGBA, paint " + i, frames[0], frames[i]);
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
        });
    }

    private static void evidence(String name, byte[] rgba, int width, int height) {
        File dir = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);
        assertTrue(dir.isDirectory() || dir.mkdirs());
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(rgba).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(dir, name).getAbsolutePath()), image);
        } finally { image.dispose(); }
    }

    private static Field field(String name) throws Exception {
        Field field = WorldDominationGame.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
