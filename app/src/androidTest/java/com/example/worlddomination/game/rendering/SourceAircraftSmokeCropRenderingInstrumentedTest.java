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
import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

/** Independent projection of physical smoke clips born halfway between island crops. */
@RunWith(AndroidJUnit4.class)
public final class SourceAircraftSmokeCropRenderingInstrumentedTest {
    @Test public void singlePuffKeepsBirthCropInBothDirectionsAndRatios() throws Exception {
        verify(false);
    }
    @Test public void pairedPuffsKeepIndependentBirthCropInBothDirectionsAndRatios() throws Exception {
        verify(true);
    }
    private static void verify(boolean paired) throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            OriginalFlashAnimationAssets assets=(OriginalFlashAnimationAssets)field(app,"flashAnimationAssets");
            FlashBattleUnitRenderer renderer=(FlashBattleUnitRenderer)field(app,"battleUnitRenderer");
            assertTrue(assets.isLoaded());
            SpriteBatch batch=new SpriteBatch(); ShaderProgram caller=SpriteBatch.createDefaultShader();
            try {
                assertTrue(caller.getLog(),caller.isCompiled());
                for(int[] direction:new int[][]{{0,1},{1,0}}) {
                    ReferenceRandom random=new ReferenceRandom(0L);
                    SourceAircraftTrail trail=new SourceAircraftTrail(paired
                            ? SourceAircraftTrail.Origin.PAIRED_PLANE
                            : SourceAircraftTrail.Origin.PREVIOUS_UNIT_POSITION,random);
                    double[] blend={.5d};
                    SourceAircraftTrailDisplay display=new SourceAircraftTrailDisplay(
                            new BattlefieldDisplayState(),trail,direction[0],0,direction[1],()->blend[0]);
                    int visits=paired?1:5;
                    for(int i=0;i<visits;i++) trail.visit(true,"default",400d,300d,400d,300d,0d);
                    double beforeAttach=random.copy().nextDouble();
                    display.afterUnitFrame();
                    assertEquals("display attach must not draw random",beforeAttach,random.copy().nextDouble(),0d);
                    int count=paired?2:1;
                    for(int slot=0;slot<count;slot++) {
                        assertTrue("source puff attached "+slot,trail.isAttached(slot));
                        assertEquals(.5d,display.getBirthCropBlend(slot),0d);
                    }
                    blend[0]=.9d;
                    display.afterUnitFrame();
                    for(int slot=0;slot<count;slot++) {
                        assertEquals("retained birth blend",.5d,display.getBirthCropBlend(slot),0d);
                        for(int[] size:new int[][]{{800,600},{1194,552}})
                            compare(renderer,assets,batch,caller,display,slot,direction[0],direction[1],
                                    size[0],size[1],paired,random,beforeAttach);
                    }
                }
            } finally {caller.dispose();batch.dispose();}
        });
    }
    private static void compare(FlashBattleUnitRenderer renderer, OriginalFlashAnimationAssets assets,
            SpriteBatch batch, ShaderProgram caller, SourceAircraftTrailDisplay display, int slot,
            int from, int to, int width, int height, boolean paired, ReferenceRandom random,
            double randomBefore) throws Exception {
        Rectangle bounds=new Rectangle(0,0,width,height);
        FrameBuffer buffer=new FrameBuffer(Pixmap.Format.RGBA8888,width,height,false,true);
        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,width,height));
        try {
            byte[] actual=render(buffer,batch,width,height,()->{
                batch.setShader(caller);batch.setColor(.31f,.47f,.69f,.61f);
                float color=batch.getPackedColor(); Matrix4 transform=new Matrix4(batch.getTransformMatrix());
                renderer.drawSourceAircraftSmoke(batch,bounds,display,slot);
                assertSame("shader restored",caller,batch.getShader());
                assertEquals("color restored",color,batch.getPackedColor(),0f);
                assertArrayEquals("transform restored",transform.val,batch.getTransformMatrix().val,0f);
            });
            byte[] expected=render(buffer,batch,width,height,()->leaf(batch,assets,display.trail,slot,
                    from,to,width,height,.5f));
            byte[] old=render(buffer,batch,width,height,()->leaf(batch,assets,display.trail,slot,
                    from,to,width,height,0f));
            String prefix="source-aircraft-smoke-crop-"+(paired?"pair"+slot:"single")+"-"
                    +from+"to"+to+"-"+width+"x"+height+"-";
            evidence(prefix+"actual.png",actual,width,height);
            evidence(prefix+"expected.png",expected,width,height);
            evidence(prefix+"attacker-only.png",old,width,height);
            int painted=0,different=0;
            for(int i=0;i<expected.length;i+=4) {
                boolean changed=false;
                for(int c=0;c<4;c++) {
                    assertEquals(prefix+"pixel "+i/4+" channel "+c,expected[i+c]&255,actual[i+c]&255);
                    changed|=expected[i+c]!=old[i+c];
                }
                if((expected[i+3]&255)>0) painted++;
                if(changed) different++;
            }
            assertTrue("source smoke paints",painted>0);
            if(width==800) assertEquals("stage has no crop shift",0,different);
            else assertTrue("phone attacker-only placement differs",different>0);
            assertEquals("render must not draw random",randomBefore,random.copy().nextDouble(),0d);
            assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        } finally {buffer.dispose();}
    }
    /** Exported shape 4936 and literal 800x600 affine/crop arithmetic; no layout helper. */
    private static void leaf(SpriteBatch batch, OriginalFlashAnimationAssets assets,
            SourceAircraftTrail trail,int slot,int from,int to,int width,int height,float blend) {
        int shape=4936; TextureRegion region=assets.getShape(shape);assertNotNull(region);
        float scale=Math.min(width/800f,height/600f);
        float margin=(width-800f*scale)/2f;
        float fromShift=from==1?-margin:0f, toShift=to==1?-margin:0f;
        float sourceX=(float)trail.getX(slot), sourceY=(float)trail.getY(slot);
        // Match the two source projections' float rounding without production layout APIs.
        float base=margin+sourceX*scale+fromShift;
        float between=margin+sourceX*scale+(fromShift+(toShift-fromShift)*blend);
        float crop=between-base;
        float ox=assets.getShapeOriginX(shape),oy=assets.getShapeOriginY(shape);
        float[] xx={ox,ox,ox+region.getRegionWidth(),ox+region.getRegionWidth()};
        float[] yy={oy+region.getRegionHeight(),oy,oy,oy+region.getRegionHeight()};
        float[] u={region.getU(),region.getU(),region.getU2(),region.getU2()};
        float[] v={region.getV2(),region.getV(),region.getV(),region.getV2()};
        float[] vertices=new float[20]; batch.setShader(null);
        batch.setColor(1f,1f,1f,(float)(trail.getAlpha(slot)/100d));
        for(int i=0;i<4;i++) {
            float x=sourceX-5f+xx[i], y=sourceY-5f+yy[i];
            vertices[i*5]=margin+x*scale+fromShift+crop;
            vertices[i*5+1]=height-(height-600f*scale)/2f-y*scale;
            vertices[i*5+2]=batch.getPackedColor();vertices[i*5+3]=u[i];vertices[i*5+4]=v[i];
        }
        batch.draw(region.getTexture(),vertices,0,20);
    }
    private static byte[] render(FrameBuffer f,SpriteBatch b,int w,int h,Draw draw) throws Exception {
        f.begin();try {
            Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_STENCIL_BUFFER_BIT);
            b.setTransformMatrix(new Matrix4().idt());b.setShader(null);b.setColor(1,1,1,1);
            b.begin();try{draw.run();}finally{b.end();}
            return ScreenUtils.getFrameBufferPixels(0,0,w,h,true);
        }finally{f.end();}
    }
    private static void evidence(String name,byte[] rgba,int w,int h) {
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
        Pixmap image=new Pixmap(w,h,Pixmap.Format.RGBA8888);
        try{image.getPixels().put(rgba).position(0);PixmapIO.writePNG(
                Gdx.files.absolute(new File(dir,name).getAbsolutePath()),image,-1,false);}
        finally{image.dispose();}
    }
    private static Object field(Object owner,String name) throws Exception {
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    private interface Draw {void run() throws Exception;}
}
