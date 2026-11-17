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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class SourceSetupColorsInstrumentedTest {
    @Test public void earlyColorsMatchOriginalFramesAtBothSizes() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            CountDownLatch published = new CountDownLatch(1), release = new CountDownLatch(1);
            Constructor<OriginalFlashAnimationAssets> ctor = OriginalFlashAnimationAssets.class
                    .getDeclaredConstructor(Runnable.class);
            ctor.setAccessible(true);
            OriginalFlashAnimationAssets assets = ctor.newInstance((Runnable) () -> {
                published.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            Field assetField = field("flashAnimationAssets");
            Object previous = assetField.get(app);
            SpriteBatch batch = (SpriteBatch)field("batch").get(app);
            Matrix4 oldProjection = new Matrix4(batch.getProjectionMatrix());
            float oldColor = batch.getPackedColor();
            Method draw = WorldDominationGame.class.getDeclaredMethod("drawSourceSetupColor",Rectangle.class,int.class,boolean.class);
            draw.setAccessible(true);
            try {
                assertTrue("first source stage",published.await(60,TimeUnit.SECONDS));
                assets.update();
                assertTrue("setup atlas uploaded",assets.areSetupPortraitsLoaded());
                assertFalse("later atlas is still blocked",assets.isLoaded());
                int[][] ids={{5864,5866},{5859,5861},{5869,5871},{5874,5876},{5854,5856}};
                for(int i=0;i<5;i++) for(int s=0;s<2;s++) {
                    assertNotNull("ready shape "+ids[i][s],assets.getShape(ids[i][s]));
                    assertEquals("source x origin",s==0?6f:(i==0?1f:0f),assets.getShapeOriginX(ids[i][s]),0f);
                    assertEquals("source y origin",s==0?6f:0f,assets.getShapeOriginY(ids[i][s]),0f);
                }
                assetField.set(app,assets);
                for(int[] size:new int[][]{{800,600},{1194,552}}) {
                    int w=size[0],h=size[1];
                    FrameBuffer fb=new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false);
                    try {
                        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,w,h));
                        fb.begin();clear();batch.begin();batch.setColor(.2f,.4f,.6f,.8f);
                        float packed=batch.getPackedColor();
                        for(int i=0;i<5;i++)for(int state=0;state<2;state++) {
                            Rectangle box=box(i,state,w,h);
                            draw.invoke(app,box,i,state==1);
                            assertEquals("caller color restored",packed,batch.getPackedColor(),0f);
                        }
                        batch.end();byte[] actual=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);fb.end();
                        fb.begin();clear();batch.begin();batch.setColor(1,1,1,1);
                        for(int i=0;i<5;i++)for(int state=0;state<2;state++) {
                            Rectangle box=box(i,state,w,h);
                            TextureRegion shape=assets.getShape(ids[i][state]);
                            float sx=state==0?6f:(i==0?1f:0f),sy=state==0?6f:0f;
                            float scale=Math.min(box.width/54f,box.height/55f);
                            batch.draw(shape,box.x+(box.width-54f*scale)/2f+sx*scale,
                                    box.y+(box.height+55f*scale)/2f-(sy+shape.getRegionHeight())*scale,
                                    shape.getRegionWidth()*scale,shape.getRegionHeight()*scale);
                        }
                        batch.end();byte[] expected=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);fb.end();
                        evidence("source-setup-colors-"+w+"x"+h+"-actual.png",actual,w,h);
                        evidence("source-setup-colors-"+w+"x"+h+"-expected.png",expected,w,h);
                        assertArrayEquals("literal source registration",expected,actual);
                        for(int i=0;i<5;i++)for(int state=0;state<2;state++) {
                            Rectangle b=box(i,state,w,h);int painted=0;
                            for(int y=(int)b.y;y<b.y+b.height;y++)for(int x=(int)b.x;x<b.x+b.width;x++)
                                if((actual[((h-1-y)*w+x)*4+3]&255)!=0)painted++;
                            assertTrue("nonempty color "+i+" state "+state,painted>0);
                        }
                        // Incorrect state/registration control: original source art stretched to hit box.
                        fb.begin();clear();batch.begin();batch.setColor(1,1,1,1);
                        for(int i=0;i<5;i++)for(int state=0;state<2;state++) {
                            Rectangle b=box(i,state,w,h);
                            batch.draw(assets.getShape(ids[i][state]),b.x,b.y,b.width,b.height);
                        }
                        batch.end();byte[] wrong=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);fb.end();
                        int differences=0;for(int i=0;i<actual.length;i+=4)
                            if(actual[i]!=wrong[i]||actual[i+1]!=wrong[i+1]||actual[i+2]!=wrong[i+2]||actual[i+3]!=wrong[i+3])differences++;
                        assertTrue("wrong fitting must differ",differences>0);
                        assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                    } finally {fb.dispose();}
                }
            } finally {
                assetField.set(app,previous);batch.setProjectionMatrix(oldProjection);batch.setPackedColor(oldColor);
                release.countDown();assets.dispose();
            }
        });
    }
    private static Rectangle box(int color,int state,int w,int h){
        float cw=w==800?72:110,ch=w==800?80:78;
        return new Rectangle(30+color*(cw+12),h-100-state*(ch+15),cw,ch);
    }
    private static Field field(String name)throws Exception{Field f=WorldDominationGame.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void clear(){Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);}
    private static void evidence(String name,byte[] pixels,int w,int h){
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);assertTrue(dir.exists()||dir.mkdirs());
        Pixmap image=new Pixmap(w,h,Pixmap.Format.RGBA8888);
        try {image.getPixels().put(pixels);image.getPixels().position(0);PixmapIO.writePNG(Gdx.files.absolute(new File(dir,name).getAbsolutePath()),image);}
        finally {image.dispose();}
    }
}
