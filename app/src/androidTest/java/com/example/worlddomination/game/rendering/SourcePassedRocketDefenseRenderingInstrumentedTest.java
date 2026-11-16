package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

/** Synthetic captured physical building states; this does not test playback scheduling. */
@RunWith(AndroidJUnit4.class)
public final class SourcePassedRocketDefenseRenderingInstrumentedTest {
    // scene.json.gz: 5658 frame 11 -> 5108 frame 4 (chr) -> 5035.
    // 5035 frame 14 paints 5012; frame 17 paints 5016; frame 1 paints 5000.
    // Actual UI seed 32 source capture: first defense callback tick 159 and removal tick 279.
    private static final float ANCHOR_X = 85.75f, ANCHOR_Y = 146.75f;
    private static final float PARENT_X = 54.2f, PARENT_Y = 3.55f;
    private static final float CHILD_X = -34.65f, CHILD_Y = -23.65f;
    private static final int LEAF = 5035, WRONG_SHAPE = 5000;

    @Test public void physicalDefenseMatchesCapturedFramesAtStageAndPhoneSizes() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) field(
                    WorldDominationGame.class, "flashAnimationAssets").get(app);
            FlashBattleUnitRenderer renderer = (FlashBattleUnitRenderer) field(
                    WorldDominationGame.class, "battleUnitRenderer").get(app);
            assertTrue(assets.isLoaded());
            assertEquals(5012, assets.getTimelineBySymbolId(LEAF).getFrame(13).layers[0].shapeId);
            assertEquals(5016, assets.getTimelineBySymbolId(LEAF).getFrame(16).layers[0].shapeId);
            assertEquals(WRONG_SHAPE, assets.getTimelineBySymbolId(LEAF).getFrame(0).layers[0].shapeId);
            SpriteBatch batch = new SpriteBatch();
            ShaderProgram caller = SpriteBatch.createDefaultShader();
            try {
                assertTrue(caller.getLog(), caller.isCompiled());
                for (int[] size : new int[][]{{800,600},{1194,552}}) {
                    verify(renderer, assets, batch, caller, size[0], size[1], 159, 14, 5012);
                    verify(renderer, assets, batch, caller, size[0], size[1], 279, 17, 5016);
                }
            } finally { caller.dispose(); batch.dispose(); }
        });
    }

    private static void verify(FlashBattleUnitRenderer renderer, OriginalFlashAnimationAssets assets,
                               SpriteBatch batch, ShaderProgram caller, int width, int height,
                               int tick, int frame, int shape) throws Exception {
        Constructor<GameState.ActionBuildingEvent> constructor = GameState.ActionBuildingEvent.class
                .getDeclaredConstructor(int.class, GameState.Action.class, int.class,
                        String.class, String.class, int.class);
        constructor.setAccessible(true);
        FlashActionBuildingAnimation building = new FlashActionBuildingAnimation(constructor.newInstance(
                1, GameState.Action.DEFENSE, 1, "antiRocket_fire", "chr", 0));
        for (int i=0;i<FlashActionBuildingAnimation.FADE_TICKS;i++) building.tick();
        SourceRocketDefenseController controller = new SourceRocketDefenseController(1,1,"chr",
                building, new FlashAntiDefenseTrackingAnimation.TargetLock());
        controller.getPlayback().publishHostFrame(frame);
        controller.advanceHostFrame(); // Publishes the stopped synthetic host frame to the building.
        assertEquals(frame-1, building.getNestedFrameIndex(LEAF,52));
        assertEquals(1f, building.getAlpha(),0f);
        assertEquals(100d,controller.getPlayback().getTurretAlphaPercent(),0d);
        Rectangle bounds = new Rectangle(0,0,width,height);
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888,width,height,false,true);
        try {
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,width,height));
            byte[] actual = render(buffer,batch,width,height,() -> {
                batch.setShader(caller); batch.setColor(.31f,.47f,.69f,.61f);
                float color=batch.getPackedColor(); Matrix4 transform=new Matrix4(batch.getTransformMatrix());
                renderer.drawActionBuilding(batch,bounds,building);
                assertSame(caller,batch.getShader()); assertEquals(color,batch.getPackedColor(),0f);
                assertArrayEquals(transform.val,batch.getTransformMatrix().val,0f);
            });
            byte[] expected = render(buffer,batch,width,height,() -> leaf(batch,assets,bounds,shape));
            byte[] wrong = render(buffer,batch,width,height,() -> leaf(batch,assets,bounds,WRONG_SHAPE));
            String prefix="source-pass-rocket-defense-"+width+"x"+height+"-"+tick+"-";
            evidence(prefix+"actual.png",actual,width,height);
            evidence(prefix+"expected.png",expected,width,height);
            evidence(prefix+"wrong-frame.png",wrong,width,height);
            int painted=0, different=0;
            for(int i=0;i<expected.length;i+=4) {
                boolean diff=false;
                for(int c=0;c<4;c++) {
                    assertEquals("tick "+tick+" pixel "+i/4+" channel "+c,
                            expected[i+c]&255,actual[i+c]&255);
                    diff |= expected[i+c]!=wrong[i+c];
                }
                if((expected[i]&255)<250 || (expected[i+1]&255)<250 || (expected[i+2]&255)<250) painted++;
                if(diff) different++;
            }
            assertTrue("source shape paints",painted>20);
            assertTrue("frame 1 differs",different>0);
            assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        } finally { buffer.dispose(); }
    }

    /** Compose only the exported leaf rectangle with literal captured parent placements. */
    private static void leaf(SpriteBatch batch, OriginalFlashAnimationAssets assets,
                             Rectangle bounds, int shape) {
        TextureRegion region=assets.getShape(shape); assertNotNull("shape "+shape,region);
        float ox=assets.getShapeOriginX(shape), oy=assets.getShapeOriginY(shape);
        float[] x={ox,ox,ox+region.getRegionWidth(),ox+region.getRegionWidth()};
        float[] y={oy+region.getRegionHeight(),oy,oy,oy+region.getRegionHeight()};
        float[] u={region.getU(),region.getU(),region.getU2(),region.getU2()};
        float[] v={region.getV2(),region.getV(),region.getV(),region.getV2()};
        float[] vertices=new float[20]; batch.setShader(null); batch.setColor(1,1,1,1);
        for(int i=0;i<4;i++) {
            vertices[i*5]=BattlefieldCityLayout.sourceX(bounds,ANCHOR_X+(PARENT_X+CHILD_X)+x[i],1);
            vertices[i*5+1]=BattlefieldCityLayout.sourceY(bounds,ANCHOR_Y+(PARENT_Y+CHILD_Y)+y[i]);
            vertices[i*5+2]=batch.getPackedColor(); vertices[i*5+3]=u[i]; vertices[i*5+4]=v[i];
        }
        batch.draw(region.getTexture(),vertices,0,20);
    }
    private static byte[] render(FrameBuffer buffer,SpriteBatch batch,int width,int height,Draw draw)
            throws Exception {
        buffer.begin(); try {
            Gdx.gl.glClearColor(1,1,1,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_STENCIL_BUFFER_BIT);
            batch.setTransformMatrix(new Matrix4().idt()); batch.setShader(null); batch.setColor(1,1,1,1);
            batch.begin(); try { draw.run(); } finally { batch.end(); }
            return ScreenUtils.getFrameBufferPixels(0,0,width,height,true);
        } finally { buffer.end(); }
    }
    private static void evidence(String name,byte[] rgba,int width,int height) {
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir); assertTrue(dir.isDirectory()||dir.mkdirs());
        Pixmap image=new Pixmap(width,height,Pixmap.Format.RGBA8888);
        try { image.getPixels().put(rgba).position(0); PixmapIO.writePNG(
                Gdx.files.absolute(new File(dir,name).getAbsolutePath()),image,-1,false); }
        finally { image.dispose(); }
    }
    private static Field field(Class<?> owner,String name) throws Exception {
        Field f=owner.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private interface Draw { void run() throws Exception; }
}
