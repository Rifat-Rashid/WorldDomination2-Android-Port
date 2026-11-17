package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.BufferUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.InterfaceFontData;
import java.io.File;
import java.nio.IntBuffer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Compare the actual HUD helper with its previous CharSequence rendering path. */
@RunWith(AndroidJUnit4.class)
public final class CenteredTextLayoutInstrumentedTest {
    @Test public void centeredTextReusesLayoutAndMatchesLegacyPixels() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            Field fontField=field("font"); BitmapFont original=(BitmapFont)fontField.get(app);
            SpriteBatch batch=(SpriteBatch)field("batch").get(app);
            Matrix4 projection=new Matrix4(batch.getProjectionMatrix());
            float originalScaleX=original.getScaleX(), originalScaleY=original.getScaleY();
            boolean scissorEnabled=Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
            IntBuffer scissorBox=BufferUtils.newIntBuffer(4);
            Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX,scissorBox);
            CountingFont proxy=new CountingFont(original);
            FrameBuffer fb=new FrameBuffer(Pixmap.Format.RGBA8888,800,600,false);
            Method method=WorldDominationGame.class.getDeclaredMethod("drawCentered",
                    String.class,float.class,float.class,float.class);
            method.setAccessible(true);
            try {
                Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                fontField.set(app,proxy);
                batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,800,600));
                String[] samples={"RESOURCES","Prepared attack", "First line\nSecond line", ""};
                float[] heights={8f,12f,20f,14f};
                Color[] colors={Color.WHITE,Color.GREEN,Color.SALMON,Color.WHITE};
                for(int i=0;i<samples.length;i++) {
                    proxy.setColor(colors[i]);float scale=proxy.getScaleX();
                    proxy.layoutCalls=0;proxy.textCalls=0;
                    fb.begin();Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    batch.begin();method.invoke(app,samples[i],400f,350f,heights[i]);batch.end();
                    byte[] actual=ScreenUtils.getFrameBufferPixels(0,0,800,600,true);fb.end();
                    int productionLayouts=proxy.layoutCalls, productionTextCalls=proxy.textCalls;
                    float scaleAfterProduction=proxy.getScaleX();
                    fb.begin();Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    batch.begin();
                    float oldScale=proxy.getScaleX();float textHeight=proxy.getLineHeight();
                    proxy.getData().setScale(heights[i]/Math.max(textHeight,1f));
                    GlyphLayout reference=new GlyphLayout();reference.setText(proxy,samples[i]);
                    proxy.draw(batch,samples[i],400f-reference.width/2f,350f);
                    proxy.getData().setScale(oldScale);batch.end();
                    byte[] legacy=ScreenUtils.getFrameBufferPixels(0,0,800,600,true);fb.end();
                    evidence("centered-text-layout-sample"+i+"-actual.png",actual);
                    evidence("centered-text-layout-sample"+i+"-legacy.png",legacy);
                    assertEquals("production uses precomputed layout",1,productionLayouts);
                    assertEquals("production avoids CharSequence overload",0,productionTextCalls);
                    assertEquals("logical font scale restored",scale,scaleAfterProduction,0f);
                    assertArrayEquals("legacy full RGBA for sample "+i,legacy,actual);
                    if (!samples[i].isEmpty()) {
                        int pixels=0;for(int j=3;j<actual.length;j+=4)if((actual[j]&255)>0)pixels++;
                        assertTrue("text paints",pixels>0);
                    }
                    assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                }
            } finally {
                original.getData().setScale(originalScaleX,originalScaleY);
                fontField.set(app,original);batch.setProjectionMatrix(projection);
                Gdx.gl.glScissor(scissorBox.get(0),scissorBox.get(1),scissorBox.get(2),scissorBox.get(3));
                if(scissorEnabled)Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                else Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                fb.dispose();proxy.dispose();
            }
        });
    }
    private static void evidence(String name,byte[] rgba) {
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
        Pixmap image=new Pixmap(800,600,Pixmap.Format.RGBA8888);
        try {image.getPixels().put(rgba).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(dir,name).getAbsolutePath()),image);
        } finally {image.dispose();}
    }
    private static Field field(String name)throws Exception {
        Field f=WorldDominationGame.class.getDeclaredField(name);f.setAccessible(true);return f;
    }
    /** Shares managed data/regions with World; never owns their textures. */
    private static final class CountingFont extends BitmapFont {
        int layoutCalls,textCalls;
        CountingFont(BitmapFont source) {super(source.getData(),source.getRegions(),false);setOwnsTexture(false);}
        @Override public float getScaleX(){return ((InterfaceFontData)getData()).getLogicalScaleX();}
        @Override public float getScaleY(){return ((InterfaceFontData)getData()).getLogicalScaleY();}
        @Override public void draw(Batch batch,GlyphLayout layout,float x,float y) {
            layoutCalls++;super.draw(batch,layout,x,y);
        }
        @Override public GlyphLayout draw(Batch batch,CharSequence text,float x,float y) {
            textCalls++;return super.draw(batch,text,x,y);
        }
    }
}
