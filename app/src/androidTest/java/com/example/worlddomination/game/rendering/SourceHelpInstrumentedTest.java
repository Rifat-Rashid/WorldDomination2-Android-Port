package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.ProductionScreenLayout;
import com.example.worlddomination.game.ui.SourceHelpState;
import java.io.File;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import static org.junit.Assert.*;

/** Real Android touch routing, source topic controls and original GPU artwork. */
@RunWith(AndroidJUnit4.class)
public final class SourceHelpInstrumentedTest {
    private static final String ROOT = "original/ui/help/";

    @Test public void navigationPreservesSelectionsReservationsAndGameplayRandom() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameSetup setup = new GameSetup(0L); setup.setDifficulty(1);
            assertTrue(setup.toggleOpponent(0)); assertTrue(setup.toggleOpponent(1));
            GameState game = new GameState(setup); field("game").set(app, game);
            ((BattlefieldCityRenderer) field("cityRenderer").get(app)).reset(game);
            double rng = game.createPresentationRandomCopy().nextDouble();
            render(app);
            tap(app, ((Rectangle[]) field("navRows").get(app))[1]);
            Rectangle modal = (Rectangle) field("modal").get(app);
            tap(app, ProductionScreenLayout.setHandleBounds(0, 51, modal, new Rectangle()));
            assertEquals(50, game.getProductionPercent("rockets"));
            tap(app, (Rectangle) field("helpButton").get(app)); render(app);
            SourceHelpState help = (SourceHelpState) field("helpState").get(app);
            assertEquals("help_index", help.getTopic());
            assertEquals(50, game.getProductionPercent("rockets"));
            tap(app,((Rectangle[])field("actionSlots").get(app))[0]);render(app);
            assertTrue("Empty source tube cannot open Skip while Help is selected",help.isOpen());
            tap(app,(Rectangle)field("confirmButton").get(app));render(app);
            assertTrue("Closed confirm cannot dismiss Help",help.isOpen());
            assertEquals(0,game.getQueuedActionCount());
            assertEquals(50,game.getProductionPercent("rockets"));
            control(app, "btn_overview");
            assertEquals("help_overview", help.getTopic());
            evidence("source-help-live-overview.png",ScreenUtils.getFrameBufferPixels(0,0,
                    Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),true),
                    new int[]{Gdx.graphics.getWidth(),Gdx.graphics.getHeight()});
            tap(app, (Rectangle) field("helpMapButton").get(app)); render(app);
            assertFalse(help.isVisible()); assertTrue(help.isOpen());
            assertEquals(50, game.getProductionPercent("rockets"));
            tap(app, ((Rectangle[]) field("actionFooterButtons").get(app))[0]); render(app);
            assertTrue(help.isVisible()); assertEquals("help_overview", help.getTopic());
            // The source reopens Production with its unaccepted sliders cleared.
            tap(app, ((Rectangle[]) field("navRows").get(app))[1]); render(app);
            assertFalse(help.isOpen()); assertEquals(0, game.getProductionPercent("rockets"));
            tap(app, ProductionScreenLayout.setHandleBounds(0, 51, modal, new Rectangle()));
            tap(app, (Rectangle) field("modalAccept").get(app));
            assertEquals(1, game.getQueuedActionCount()); assertEquals(32, game.getHuman().turnMoneyMillions);
            tap(app, (Rectangle) field("helpButton").get(app)); render(app);
            control(app, "btn_weapondetails");
            control(app, "btn_infanterie"); assertEquals("help_weapondetail", help.getTopic());
            control(app, "btn_antiweapons"); assertEquals("help_weapondetail", help.getTopic());
            tap(app, (Rectangle) field("helpButton").get(app)); render(app);
            control(app, "btn_overview");
            JsonValue pages = new JsonReader().parse(Gdx.files.internal(ROOT + "manifest.json")).get("pages");
            for (int i = 3; i < pages.size; i++) {
                assertEquals(pages.get(i).getString("topic"), help.getTopic());
                verifyControls(app, pages.get(i));
                if (i + 1 < pages.size) control(app, "btnNext");
            }
            assertFalse(help.hasNext());
            control(app, "btnIndex"); assertEquals("help_index", help.getTopic());
            assertEquals(1, game.getQueuedActionCount());
            assertEquals(GameState.Action.PRODUCTION, game.getQueuedAction(0));
            assertEquals(32, game.getHuman().turnMoneyMillions);
            assertEquals("Help never consumes gameplay randomness", rng,
                    game.createPresentationRandomCopy().nextDouble(), 0d);
            tap(app,(Rectangle)field("helpMapButton").get(app));render(app);
            tap(app,((Rectangle[])field("actionFooterButtons").get(app))[1]);render(app);
            assertFalse(help.isOpen());assertEquals(1,game.getQueuedActionCount());
            assertEquals(32,game.getHuman().turnMoneyMillions);
            tap(app,(Rectangle)field("helpButton").get(app));render(app);
            app.keyDown(Input.Keys.BACK); assertFalse(help.isOpen());
            assertNull(game.getSelectedAction()); assertEquals(1, game.getQueuedActionCount());
            tap(app, ((Rectangle[]) field("navRows").get(app))[0]);
            assertEquals(GameState.Action.RESOURCES, game.getSelectedAction());
            assertEquals(1, game.getQueuedActionCount());
            tap(app,(Rectangle)field("modalAccept").get(app));
            java.lang.reflect.Method update=WorldDominationGame.class.getDeclaredMethod("fixedUpdate",float.class);
            update.setAccessible(true);
            for(int i=0;i<11;i++)update.invoke(app,1f/24f);
            tap(app,(Rectangle)field("helpButton").get(app));render(app);
            assertTrue(help.isOpen());
            tap(app,(Rectangle)field("confirmButton").get(app));
            assertFalse(help.isOpen());assertEquals(0,game.getTurn());
            for(int i=0;i<22;i++)update.invoke(app,1f/24f);
            assertEquals(1,game.getTurn());assertEquals(5,game.getWeaponStock("rocket1"));
            assertEquals(86,game.getHuman().moneyMillions);assertEquals(17181,game.getHuman().score);
        });
    }

    @Test public void originalPagesAndHitTransformsMatchAtBothAspectRatios() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            JsonValue pages = new JsonReader().parse(Gdx.files.internal(ROOT + "manifest.json")).get("pages");
            SourceHelpRenderer renderer = new SourceHelpRenderer();
            SpriteBatch batch = new SpriteBatch();
            try {
                for (int[] size : new int[][]{{800,600},{1194,552}}) {
                    Rectangle container = new Rectangle(25, 15, size[0]-50, size[1]-30);
                    FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, size[0], size[1], false);
                    try {
                        fb.begin();
                        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,size[0],size[1]));
                        for (int index : new int[]{0,4,20,27}) {
                            JsonValue page = pages.get(index);
                            assertTrue(renderer.prepare(page.getString("topic")));
                            Texture source = new Texture(Gdx.files.internal(ROOT+page.getString("asset")));
                            source.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                            try {
                                clear(); batch.begin(); batch.setColor(Color.WHITE);
                                renderer.draw(batch, container); batch.end();
                                byte[] actual = ScreenUtils.getFrameBufferPixels(0,0,size[0],size[1],true);
                                clear(); batch.begin();
                                float s = Math.min(container.width/365f,container.height/275f);
                                float x = container.x+(container.width-365f*s)/2f;
                                float y = container.y+(container.height-275f*s)/2f;
                                batch.draw(source,x,y,365f*s,275f*s); batch.end();
                                byte[] expected = ScreenUtils.getFrameBufferPixels(0,0,size[0],size[1],true);
                                assertArrayEquals("original full page in uniform source crop",expected,actual);
                                clear(); batch.begin();
                                batch.draw(source,container.x,container.y,container.width,container.height);batch.end();
                                byte[] stretched = ScreenUtils.getFrameBufferPixels(0,0,size[0],size[1],true);
                                int changed = 0;
                                for (int k=0;k<actual.length;k++) if (actual[k]!=stretched[k]) changed++;
                                assertTrue("stretched page is a meaningful negative control",changed>400);
                                String prefix = "source-help-"+page.getString("topic")+"-"+size[0]+"x"+size[1];
                                evidence(prefix+"-actual.png",actual,size);
                                evidence(prefix+"-expected.png",expected,size);
                                evidence(prefix+"-stretched.png",stretched,size);
                                for (JsonValue control : page.get("controls")) {
                                    JsonValue b = control.get("bounds");
                                    assertEquals(control.getString("name"), renderer.controlAt(container,
                                            x+(b.getFloat(0)+b.getFloat(2)/2f)*s,
                                            y+(275f-b.getFloat(1)-b.getFloat(3)/2f)*s));
                                }
                                assertNull(renderer.controlAt(container,container.x-1,container.y));
                                if (365f*s < container.width-1) assertNull(renderer.controlAt(container,container.x+1,container.y+container.height/2));
                            } finally { source.dispose(); }
                        }
                    } finally { fb.end(); fb.dispose(); }
                }
            } finally { batch.dispose(); renderer.dispose(); }
        });
    }

    private static void clear() {
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glClearColor(0,0,0,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }
    private static void verifyControls(WorldDominationGame app, JsonValue page) throws Exception {
        SourceHelpRenderer renderer = (SourceHelpRenderer) field("helpRenderer").get(app);
        Rectangle map = (Rectangle) field("mapBounds").get(app);
        float scale = Math.min(map.width/365f,map.height/275f);
        float x = map.x+(map.width-365f*scale)/2f, y=map.y+(map.height-275f*scale)/2f;
        for (JsonValue c : page.get("controls")) {
            JsonValue b=c.get("bounds");
            assertEquals(c.getString("name"),renderer.controlAt(map,
                    x+(b.getFloat(0)+b.getFloat(2)/2f)*scale,
                    y+(275f-b.getFloat(1)-b.getFloat(3)/2f)*scale));
        }
    }
    private static void control(WorldDominationGame app, String name) throws Exception {
        SourceHelpState help = (SourceHelpState) field("helpState").get(app);
        JsonValue pages=new JsonReader().parse(Gdx.files.internal(ROOT+"manifest.json")).get("pages");
        for(JsonValue page:pages) if(page.getString("topic").equals(help.getTopic())) {
            for(JsonValue c:page.get("controls")) if(c.getString("name").equals(name)) {
                Rectangle map=(Rectangle)field("mapBounds").get(app);
                float scale=Math.min(map.width/365f,map.height/275f);
                JsonValue b=c.get("bounds");
                tap(app,new Rectangle(map.x+(map.width-365f*scale)/2f+b.getFloat(0)*scale,
                        map.y+(map.height-275f*scale)/2f+(275f-b.getFloat(1)-b.getFloat(3))*scale,
                        b.getFloat(2)*scale,b.getFloat(3)*scale));
                render(app); return;
            }
        }
        fail("Missing visible source control "+help.getTopic()+"/"+name);
    }
    private static void tap(WorldDominationGame app, Rectangle b) throws Exception {
        Viewport vp=(Viewport)field("viewport").get(app);
        Vector2 p=vp.project(new Vector2(b.x+b.width/2f,b.y+b.height/2f));
        app.touchDown(Math.round(p.x),Gdx.graphics.getHeight()-Math.round(p.y),0,0);
        app.touchUp(Math.round(p.x),Gdx.graphics.getHeight()-Math.round(p.y),0,0);
    }
    private static void render(WorldDominationGame app) throws Exception {
        field("accumulator").setFloat(app,0);field("skipNextFrameDelta").setBoolean(app,true);app.render();
    }
    private static Field field(String name) throws Exception {
        Field f=WorldDominationGame.class.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static void evidence(String name, byte[] bytes, int[] size) {
        File dir=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
        Pixmap pixmap=new Pixmap(size[0],size[1],Pixmap.Format.RGBA8888);
        try {pixmap.getPixels().put(bytes);PixmapIO.writePNG(Gdx.files.absolute(new File(dir,name).getAbsolutePath()),pixmap);}
        finally {pixmap.dispose();}
    }
}
