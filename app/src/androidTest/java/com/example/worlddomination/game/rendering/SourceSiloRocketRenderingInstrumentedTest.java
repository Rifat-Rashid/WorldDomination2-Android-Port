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
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.io.File;
import static org.junit.Assert.*;

/** Independent shape/stencil oracle for symbol 798's silo and submarine rockets. */
@RunWith(AndroidJUnit4.class)
public final class SourceSiloRocketRenderingInstrumentedTest {
    // Paint captures: late-compat-rocket-seed0-turn22-target1-city2-paint,
    // ticks 1 and 252. Symbol 798 in scene.json.gz specifies the leaf placements.
    private static final float X = 396.25f;
    private static final float ANGLE = 89f;
    private static final String VERT = "attribute vec4 a_position; attribute vec4 a_color;"
            + "attribute vec2 a_texCoord0; uniform mat4 u_projTrans; varying vec4 c;"
            + "varying vec2 uv; void main(){c=a_color;c.a*=255.0/254.0;"
            + "uv=a_texCoord0;gl_Position=u_projTrans*a_position;}";
    private static final String MASK = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "varying vec2 uv; uniform sampler2D u_texture;"
            + "void main(){if(texture2D(u_texture,uv).a<=0.0039)discard;gl_FragColor=vec4(1.0);}";
    private static final String TINT = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "varying vec2 uv; varying vec4 c; uniform sampler2D u_texture;"
            + "void main(){vec4 s=texture2D(u_texture,uv);"
            + "gl_FragColor=clamp(s*vec4(0.0,0.5,0.0,0.75)+vec4(0.0,1.0,0.0,0.0),0.0,1.0)*c;}";

    @Test public void sourceFramesMatchAtStageAndPhoneSizes() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) field(
                    WorldDominationGame.class, "flashAnimationAssets").get(app);
            FlashBattleUnitRenderer renderer = (FlashBattleUnitRenderer) field(
                    WorldDominationGame.class, "battleUnitRenderer").get(app);
            assertTrue(assets.isLoaded());
            SpriteBatch batch = new SpriteBatch();
            ShaderProgram mask = new ShaderProgram(VERT, MASK);
            ShaderProgram tint = new ShaderProgram(VERT, TINT);
            ShaderProgram caller = SpriteBatch.createDefaultShader();
            try {
                assertTrue(mask.getLog(), mask.isCompiled());
                assertTrue(tint.getLog(), tint.isCompiled());
                assertTrue(caller.getLog(), caller.isCompiled());
                for (int[] size : new int[][]{{800,600},{1194,552}}) {
                    for (int frame : new int[]{1,3}) {
                        verify(renderer, assets, batch, mask, tint, caller,
                                size[0], size[1], frame);
                        verifySubmarine(renderer, assets, batch, mask, tint, caller,
                                size[0], size[1], frame);
                    }
                }
            } finally { caller.dispose(); tint.dispose(); mask.dispose(); batch.dispose(); }
        });
    }

    private static void verify(FlashBattleUnitRenderer renderer, OriginalFlashAnimationAssets assets,
                               SpriteBatch batch, ShaderProgram mask, ShaderProgram tint,
                               ShaderProgram caller, int width, int height, int frame)
            throws Exception {
        Rectangle bounds = new Rectangle(0, 0, width, height);
        FlashBattleRoutes.RocketStrike route = FlashBattleRoutes.createRocketStrike(0, 1, 2, 1, false);
        FlashRocketAnimation animation = FlashRocketAnimation.forSourceOrdinary(route,
                "explosion003", OriginalAudio.Cue.EXPLOSION_SMALL3, new ReferenceRandom(0), 5);
        SourceRocketPlayback playback = animation.getSourcePlayback();
        float y = frame == 1 ? 356.25f : 126.75f;
        // Fix the display state to captured paint values; no game rules are exercised here.
        field(SourceRocketPlayback.class, "renderX").setDouble(playback, X);
        field(SourceRocketPlayback.class, "renderY").setDouble(playback, y);
        field(SourceRocketPlayback.class, "rotation").setDouble(playback, ANGLE);
        field(SourceRocketPlayback.class, "sourceFrame").setInt(playback, frame);
        SourceRocketDisplayDepths depths = new SourceRocketDisplayDepths(
                new BattlefieldDisplayState(), animation, 0,
                (FlashAnimationTimeline) null, new SourceStoppedClipQueue());
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false, true);
        try {
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, width, height));
            byte[] actual = render(buffer, batch, width, height, () -> {
                batch.setShader(caller); batch.setColor(.31f,.47f,.69f,.61f);
                float color = batch.getPackedColor();
                renderer.drawSourceRocketRole(batch, bounds, depths.binding(),
                        BattlefieldDisplayState.Kind.UNIT_BODY, 0);
                assertSame(caller, batch.getShader());
                assertEquals(color, batch.getPackedColor(), 0f);
            });
            byte[] expected = render(buffer, batch, width, height, () ->
                    oracle(batch, assets, bounds, route, mask, tint, frame, y, 1f, false));
            byte[] half = render(buffer, batch, width, height, () ->
                    oracle(batch, assets, bounds, route, mask, tint, frame, y, .5f, false));
            byte[] wrongTint = render(buffer, batch, width, height, () ->
                    oracle(batch, assets, bounds, route, mask, tint, frame, y, 1f, true));
            byte[] missing = render(buffer, batch, width, height, () -> { });
            String prefix = "source-silo-rocket-" + width + "x" + height + "-frame" + frame + "-";
            evidence(prefix + "actual.png", actual, width, height);
            evidence(prefix + "expected.png", expected, width, height);
            evidence(prefix + "halfheight.png", half, width, height);
            evidence(prefix + "wrongtint.png", wrongTint, width, height);
            evidence(prefix + "missing.png", missing, width, height);
            int painted = 0, halfDiff = 0, tintDiff = 0, missingDiff = 0;
            for (int i = 0; i < expected.length; i += 4) {
                boolean h = false, t = false, m = false;
                for (int c = 0; c < 4; c++) {
                    assertEquals("frame " + frame + " pixel " + i/4 + " channel " + c,
                            expected[i+c] & 255, actual[i+c] & 255);
                    h |= expected[i+c] != half[i+c];
                    t |= expected[i+c] != wrongTint[i+c];
                    m |= expected[i+c] != missing[i+c];
                }
                if (m) { painted++; missingDiff++; }
                if (h) halfDiff++;
                if (t) tintDiff++;
            }
            assertTrue("painted source pixels", painted > 20);
            assertTrue("half-height must differ", halfDiff > 0);
            assertTrue("wrong tint must differ", tintDiff > 0);
            assertTrue("missing role must differ", missingDiff > 0);
            if (frame == 3) verifySmoke(renderer, assets, batch, buffer, caller, bounds, route, depths, playback, width, height);
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
        } finally { buffer.dispose(); }
    }

    private static void verifySubmarine(FlashBattleUnitRenderer renderer,
            OriginalFlashAnimationAssets assets, SpriteBatch batch, ShaderProgram mask,
            ShaderProgram tint, ShaderProgram caller, int width, int height, int frame)
            throws Exception {
        // Actual source private submarine playback changes frames at ticks 513/514:
        // the new depth-4 tint clip is uncolored and outside depth-1's clipDepth 3.
        // Normalize its pose here to isolate the authored layer/parent-scale contract.
        Rectangle bounds = new Rectangle(0, 0, width, height);
        FlashBattleRoutes.RocketStrike route = FlashBattleRoutes.createSubmarineRocketStrike(0, 1, 2);
        FlashRocketAnimation animation = FlashRocketAnimation.forSourceSubmarine(route,
                "explosion003", OriginalAudio.Cue.EXPLOSION_SMALL3, new ReferenceRandom(0), 5);
        SourceRocketPlayback playback = animation.getSourcePlayback();
        assertEquals("submarine parent y scale", .5d, playback.getParentScaleY(), 0d);
        float y = frame == 1 ? 356.25f : 126.75f;
        field(SourceRocketPlayback.class, "renderX").setDouble(playback, X);
        field(SourceRocketPlayback.class, "renderY").setDouble(playback, y);
        field(SourceRocketPlayback.class, "rotation").setDouble(playback, ANGLE);
        field(SourceRocketPlayback.class, "sourceFrame").setInt(playback, frame);
        SourceRocketDisplayDepths depths = new SourceRocketDisplayDepths(
                new BattlefieldDisplayState(), animation, 0,
                (FlashAnimationTimeline) null, new SourceStoppedClipQueue());
        FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false, true);
        try {
            batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, width, height));
            byte[] actual = render(buffer, batch, width, height, () -> {
                batch.setShader(caller); batch.setColor(.31f,.47f,.69f,.61f);
                float color = batch.getPackedColor();
                renderer.drawSourceRocketRole(batch, bounds, depths.binding(),
                        BattlefieldDisplayState.Kind.UNIT_BODY, 0);
                assertSame(caller, batch.getShader());
                assertEquals(color, batch.getPackedColor(), 0f);
            });
            byte[] expected = render(buffer, batch, width, height, () ->
                    oracle(batch, assets, bounds, route, mask, tint, frame, y, .5f, false, false));
            byte[] wrongHeight = render(buffer, batch, width, height, () ->
                    oracle(batch, assets, bounds, route, mask, tint, frame, y, 1f, false, false));
            byte[] missing = render(buffer, batch, width, height, () -> { });
            byte[] missingOutside = frame == 3 ? render(buffer, batch, width, height, () ->
                    oracle(batch, assets, bounds, route, mask, tint, frame, y, .5f, false, true)) : null;
            String prefix = "source-submarine-rocket-" + width + "x" + height + "-frame" + frame + "-";
            evidence(prefix + "actual.png", actual, width, height);
            evidence(prefix + "expected.png", expected, width, height);
            evidence(prefix + "fullheight.png", wrongHeight, width, height);
            evidence(prefix + "missing.png", missing, width, height);
            if (missingOutside != null) evidence(prefix + "missing-outside-layer.png", missingOutside, width, height);
            int painted = 0, heightDiff = 0, outsideDiff = 0;
            for (int i = 0; i < expected.length; i += 4) {
                boolean paintedPixel = false, heightPixel = false, outsidePixel = false;
                for (int c = 0; c < 4; c++) {
                    assertEquals("submarine frame " + frame + " pixel " + i/4 + " channel " + c,
                            expected[i+c] & 255, actual[i+c] & 255);
                    paintedPixel |= expected[i+c] != missing[i+c];
                    heightPixel |= expected[i+c] != wrongHeight[i+c];
                    if (missingOutside != null) outsidePixel |= expected[i+c] != missingOutside[i+c];
                }
                if (paintedPixel) painted++;
                if (heightPixel) heightDiff++;
                if (outsidePixel) outsideDiff++;
            }
            // At phone scale the half-height frame-1 mask exposes only 17 pixels
            // on the Pixel 10. Require nonempty source coverage; exact RGBA above
            // and the missing/full-height controls enforce the actual geometry.
            assertTrue("submarine body paints at " + width + "x" + height, painted > 0);
            assertTrue("full-height control differs", heightDiff > 0);
            if (frame == 3) assertTrue("missing frame-3 outside layer differs", outsideDiff > 0);
            assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
        } finally { buffer.dispose(); }
    }

    private static void verifySmoke(FlashBattleUnitRenderer renderer,
            OriginalFlashAnimationAssets assets, SpriteBatch batch, FrameBuffer buffer,
            ShaderProgram caller, Rectangle bounds, FlashBattleRoutes.RocketStrike route,
            SourceRocketDisplayDepths depths, SourceRocketPlayback playback, int width, int height)
            throws Exception {
        // Source capture tick 205, physical slot 49; alpha is the Flash clip alpha.
        Object[] trails = (Object[]) field(SourceRocketPlayback.class, "trails").get(playback);
        Object trail = trails[49];
        field(trail.getClass(), "created").setBoolean(trail, true);
        field(trail.getClass(), "attached").setBoolean(trail, true);
        field(trail.getClass(), "x").setDouble(trail, 401.02902286525034d);
        field(trail.getClass(), "y").setDouble(trail, 349.39058351011175d);
        field(trail.getClass(), "alphaFactor").setDouble(trail, .15666666666666665d);
        depths.afterUnitFrame();
        byte[] actual = render(buffer, batch, width, height, true, () -> {
            batch.setShader(caller); batch.setColor(.31f,.47f,.69f,.61f);
            float color = batch.getPackedColor();
            renderer.drawSourceRocketRole(batch, bounds, depths.binding(),
                    BattlefieldDisplayState.Kind.EFFECT, 49);
            assertSame(caller, batch.getShader());
            assertEquals(color, batch.getPackedColor(), 0f);
        });
        byte[] expected = render(buffer, batch, width, height, true, () -> {
            int shape = OriginalFlashAnimationAssets.ROCKET_TRAIL_SHAPE_ID;
            TextureRegion r = assets.getShape(shape); assertNotNull("Missing source shape " + shape, r);
            float x = (float)401.02902286525034d, y = (float)349.39058351011175d;
            float progress = (x-route.launch.x)/(route.target.x-route.launch.x);
            float shift = BattlefieldCityLayout.sourceXBetween(bounds,x,0,1,progress)
                    - BattlefieldCityLayout.sourceX(bounds,x,0);
            float ox = assets.getShapeOriginX(shape), oy = assets.getShapeOriginY(shape);
            float[] xs={ox,ox,ox+r.getRegionWidth(),ox+r.getRegionWidth()};
            float[] ys={oy+r.getRegionHeight(),oy,oy,oy+r.getRegionHeight()};
            float[] us={r.getU(),r.getU(),r.getU2(),r.getU2()};
            float[] vs={r.getV2(),r.getV(),r.getV(),r.getV2()};
            float[] v=new float[20]; batch.setColor(1,1,1,(float).15666666666666665d);
            for(int i=0;i<4;i++) {
                v[i*5]=BattlefieldCityLayout.sourceX(bounds,x-5f+xs[i],0)+shift;
                v[i*5+1]=BattlefieldCityLayout.sourceY(bounds,y-5f+ys[i]);
                v[i*5+2]=batch.getPackedColor(); v[i*5+3]=us[i]; v[i*5+4]=vs[i];
            }
            batch.draw(r.getTexture(),v,0,20);
        });
        String prefix = "source-silo-rocket-" + width + "x" + height + "-smoke-";
        evidence(prefix + "actual.png", actual, width, height);
        evidence(prefix + "expected.png", expected, width, height);
        assertArrayEquals("captured smoke RGBA", expected, actual);
        int painted=0; for(int i=0;i<expected.length;i+=4)
            if ((expected[i+3]&255)>0) painted++;
        assertTrue("smoke paints", painted>0);
    }

    /** Literal symbol-798 placements, independently composed with GL stencil. */
    private static void oracle(SpriteBatch b, OriginalFlashAnimationAssets a, Rectangle bounds,
                               FlashBattleRoutes.RocketStrike route, ShaderProgram mask,
                               ShaderProgram tint, int frame, float y, float height,
                               boolean wrongTint) {
        oracle(b, a, bounds, route, mask, tint, frame, y, height, wrongTint, false);
    }

    private static void oracle(SpriteBatch b, OriginalFlashAnimationAssets a, Rectangle bounds,
                               FlashBattleRoutes.RocketStrike route, ShaderProgram mask,
                               ShaderProgram tint, int frame, float y, float height,
                               boolean wrongTint, boolean omitOutsideLayer) {
        b.flush(); Gdx.gl.glEnable(GL20.GL_STENCIL_TEST); Gdx.gl.glStencilMask(0xff);
        Gdx.gl.glClearStencil(0); Gdx.gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
        Gdx.gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xff);
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
        Gdx.gl.glColorMask(false,false,false,false); b.setShader(mask);
        if (frame == 1) leaf(b,a,bounds,route,789,y,height,.6921234130859375f,-133.2f,-27.45f);
        else leaf(b,a,bounds,route,797,y,height,1f,0f,0f);
        b.flush(); Gdx.gl.glColorMask(true,true,true,true); Gdx.gl.glStencilMask(0);
        Gdx.gl.glStencilFunc(GL20.GL_EQUAL,1,0xff);
        Gdx.gl.glStencilOp(GL20.GL_KEEP,GL20.GL_KEEP,GL20.GL_KEEP);
        b.setShader(null); leaf(b,a,bounds,route,792,y,height,1f,0f,0f);
        if (frame == 1) {
            b.setShader(wrongTint ? null : tint);
            leaf(b,a,bounds,route,794,y,height,1f,-22.05f,-5.55f);
        }
        b.flush(); Gdx.gl.glDisable(GL20.GL_STENCIL_TEST); Gdx.gl.glStencilMask(0xff);
        if (frame == 3 && !omitOutsideLayer) {
            b.setShader(wrongTint ? tint : null);
            leaf(b,a,bounds,route,794,y,height,1f,-22.05f,-5.55f);
        }
        b.setShader(null);
    }

    /** Vertices derived from the exported rectangle and captured 89-degree parent matrix. */
    private static void leaf(SpriteBatch b, OriginalFlashAnimationAssets a, Rectangle bounds,
                             FlashBattleRoutes.RocketStrike route, int shape, float y,
                             float height, float scale, float tx, float ty) {
        TextureRegion r = a.getShape(shape); assertNotNull("Missing source shape " + shape, r);
        float cos = (float)Math.cos(Math.toRadians(ANGLE));
        float sin = (float)Math.sin(Math.toRadians(ANGLE));
        float aa=cos*scale, bb=sin*scale, cc=-sin*height*scale, dd=cos*height*scale;
        float px=cos*tx-sin*height*ty, py=sin*tx+cos*height*ty;
        float ox=a.getShapeOriginX(shape), oy=a.getShapeOriginY(shape);
        float[] xx={ox,ox,ox+r.getRegionWidth(),ox+r.getRegionWidth()};
        float[] yy={oy+r.getRegionHeight(),oy,oy,oy+r.getRegionHeight()};
        float[] v=new float[20];
        float progress=(X-route.launch.x)/(route.target.x-route.launch.x);
        float shift=BattlefieldCityLayout.sourceXBetween(bounds,X,0,1,progress)
                - BattlefieldCityLayout.sourceX(bounds,X,0);
        b.setColor(1,1,1,1); float color=b.getPackedColor();
        float[] u={r.getU(),r.getU(),r.getU2(),r.getU2()};
        float[] vv={r.getV2(),r.getV(),r.getV(),r.getV2()};
        for(int i=0;i<4;i++) {
            v[i*5]=BattlefieldCityLayout.sourceX(bounds,X+px+aa*xx[i]+cc*yy[i],0)+shift;
            v[i*5+1]=BattlefieldCityLayout.sourceY(bounds,y+py+bb*xx[i]+dd*yy[i]);
            v[i*5+2]=color; v[i*5+3]=u[i]; v[i*5+4]=vv[i];
        }
        b.draw(r.getTexture(),v,0,20);
    }
    private static byte[] render(FrameBuffer f, SpriteBatch b, int w, int h, Draw draw) throws Exception {
        return render(f, b, w, h, false, draw);
    }
    private static byte[] render(FrameBuffer f, SpriteBatch b, int w, int h,
                                 boolean transparent, Draw draw) throws Exception {
        f.begin(); try {
            if (transparent) Gdx.gl.glClearColor(0,0,0,0);
            else Gdx.gl.glClearColor(1,1,1,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_STENCIL_BUFFER_BIT);
            b.setTransformMatrix(new Matrix4().idt()); b.setShader(null); b.setColor(1,1,1,1);
            b.begin(); try { draw.run(); } finally { b.end(); }
            return ScreenUtils.getFrameBufferPixels(0,0,w,h,true);
        } finally { f.end(); }
    }
    private static void evidence(String name, byte[] rgba, int width, int height) {
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(rgba).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()),
                    image, -1, false);
        } finally { image.dispose(); }
    }
    private static Field field(Class<?> c,String name) throws Exception {
        Field f=c.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private interface Draw { void run() throws Exception; }
}
