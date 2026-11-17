package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.IntBuffer;
import com.badlogic.gdx.utils.BufferUtils;
import static org.junit.Assert.*;

/** GLES checks against independently selected shape 5813 source pixels. */
@RunWith(AndroidJUnit4.class)
public final class SourceSetupPanelsInstrumentedTest {
    @Test public void sourceCornersAndUniformInteriorAtBothSizes() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            SourceSetupPanelRenderer renderer = new SourceSetupPanelRenderer();
            Texture source = new Texture(Gdx.files.internal("original/ui/setup-panel-source-5813.png"));
            source.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            SpriteBatch batch = new SpriteBatch();
            try {
                for (int[] size : new int[][]{{800, 600}, {1194, 552}}) {
                    int w = size[0], h = size[1];
                    Rectangle[] bounds = {new Rectangle(50,h-75,w-100,60),
                            new Rectangle(50,h-185,w-100,100),
                            new Rectangle(50,80,w-100,h-280),
                            new Rectangle(50,15,w-100,55)};
                    SourceSetupPanelRenderer.Plate[] plates = SourceSetupPanelRenderer.Plate.values();
                    int[] tops = {12,84,199,534}, bottoms = {82,198,529,595};
                    int[] th = {16,22,22,18}, bh = {16,23,23,18};
                    FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false);
                    try {
                        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,w,h));
                        buffer.begin(); clear(); batch.begin(); batch.setColor(.2f,.4f,.6f,.8f);
                        float color = batch.getPackedColor();
                        for (int i=0;i<4;i++) {
                            renderer.draw(batch,bounds[i],plates[i]);
                            assertEquals("caller color",color,batch.getPackedColor(),0f);
                        }
                        batch.end(); byte[] actual=ScreenUtils.getFrameBufferPixels(0,0,w,h,true); buffer.end();
                        evidence("source-setup-panels-"+(w==800?"square":"wide")+".png",actual,w,h);
                        buffer.begin(); clear(); batch.begin(); batch.setColor(1,1,1,1);
                        for (int i=0;i<4;i++) {
                            Rectangle b=bounds[i];
                            // Source y is top-down; TextureRegion supplies the correct GL orientation.
                            batch.draw(new TextureRegion(source,115,tops[i],28,th[i]),b.x,b.y+b.height-th[i],28,th[i]);
                            batch.draw(new TextureRegion(source,637,tops[i],28,th[i]),b.x+b.width-28,b.y+b.height-th[i],28,th[i]);
                            batch.draw(new TextureRegion(source,115,bottoms[i]-bh[i],28,bh[i]),b.x,b.y,28,bh[i]);
                            batch.draw(new TextureRegion(source,637,bottoms[i]-bh[i],28,bh[i]),b.x+b.width-28,b.y,28,bh[i]);
                        }
                        batch.end(); byte[] oracle=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);buffer.end();
                        int checked=0;
                        for(int i=0;i<4;i++) {
                            Rectangle b=bounds[i];
                            for(int corner=0;corner<4;corner++) {
                                int x=(int)(corner%2==0?b.x:b.x+b.width-28);
                                int y=(int)(corner<2?b.y+b.height-th[i]:b.y);
                                int height=corner<2?th[i]:bh[i];
                                int xStart=x+(corner%2==0?1:7), xEnd=x+(corner%2==0?21:27);
                                int yStart=y+(corner<2?7:1), yEnd=y+height-(corner<2?1:7);
                                for(int yy=yStart;yy<yEnd;yy++) for(int xx=xStart;xx<xEnd;xx++) {
                                    int offset=((h-1-yy)*w+xx)*4;
                                    for(int c=0;c<4;c++) assertEquals("source corner "+i+"/"+corner,oracle[offset+c]&255,actual[offset+c]&255);
                                    checked++;
                                }
                            }
                        }
                        assertTrue("source corner pixels exercised",checked>0);
                        // Enemy body must differ from stretching the full 494x69 source area.
                        Rectangle enemy=bounds[2]; int mw=(int)enemy.width-56, mh=(int)enemy.height-45;
                        // Independent oracle: draw the entire source interior at
                        // one uniform scale, centered and clipped to the destination.
                        // Sampling may differ by at most two RGBA8 levels from the
                        // renderer's mathematically equivalent cropped UVs.
                        boolean scissorWasEnabled=Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
                        IntBuffer oldScissor=BufferUtils.newIntBuffer(4);
                        Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX,oldScissor);
                        byte[] expected;
                        buffer.begin();
                        try {
                            Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST); clear();
                            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                            Gdx.gl.glScissor((int)enemy.x+28,(int)enemy.y+23,mw,mh);
                            float factor=Math.max(enemy.width/494f,enemy.height/69f);
                            float sw=494f*factor,sh=69f*factor;
                            batch.begin();batch.setColor(1,1,1,1);
                            batch.draw(new TextureRegion(source,143,106,494,69),
                                    enemy.x+(enemy.width-sw)/2f,enemy.y+(enemy.height-sh)/2f,sw,sh);
                            batch.end();
                            expected=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);
                        } finally {
                            Gdx.gl.glScissor(oldScissor.get(0),oldScissor.get(1),oldScissor.get(2),oldScissor.get(3));
                            if(scissorWasEnabled) Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                            else Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                            buffer.end();
                        }
                        evidence("source-setup-panels-"+(w==800?"square":"wide")+"-expected.png",expected,w,h);
                        int compared=0;
                        for(int y=(int)enemy.y+30;y<enemy.y+enemy.height-30;y++)
                            for(int x=(int)enemy.x+35;x<enemy.x+enemy.width-35;x++) {
                                int k=((h-1-y)*w+x)*4;
                                for(int c=0;c<4;c++) assertTrue("uniform source interior RGBA",
                                        Math.abs((actual[k+c]&255)-(expected[k+c]&255))<=2);
                                compared++;
                            }
                        assertTrue("uniform interior compared",compared>0);
                        buffer.begin();clear();batch.begin();batch.setColor(1,1,1,1);
                        batch.draw(new TextureRegion(source,143,106,494,69),enemy.x+28,enemy.y+23,mw,mh);
                        batch.end();byte[] stretched=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);buffer.end();
                        evidence("source-setup-panels-"+(w==800?"square":"wide")+"-stretched.png",stretched,w,h);
                        int different=0,painted=0;
                        for(int y=(int)enemy.y+30;y<enemy.y+enemy.height-30;y++)
                            for(int x=(int)enemy.x+35;x<enemy.x+enemy.width-35;x++) {
                                int k=((h-1-y)*w+x)*4;
                                if((actual[k+3]&255)>0)painted++;
                                if(actual[k]!=stretched[k] || actual[k+1]!=stretched[k+1] || actual[k+2]!=stretched[k+2])different++;
                            }
                        assertTrue("enemy interior paints",painted>0);
                        assertTrue("uniform crop differs from stretched source",different>0);
                        assertEquals("GL error",GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                    } finally {buffer.dispose();}
                }
            } finally {batch.dispose();source.dispose();renderer.dispose();}
        });
    }
    private static void clear(){Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);}
    private static void evidence(String name,byte[] rgba,int w,int h){
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);assertTrue(dir.exists()||dir.mkdirs());
        Pixmap pixmap=new Pixmap(w,h,Pixmap.Format.RGBA8888);
        try {pixmap.getPixels().put(rgba);pixmap.getPixels().position(0);PixmapIO.writePNG(Gdx.files.absolute(new File(dir,name).getAbsolutePath()),pixmap);}
        finally {pixmap.dispose();}
    }
}
