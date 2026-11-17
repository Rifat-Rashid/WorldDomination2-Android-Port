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
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class SourceSetupCarouselInstrumentedTest {
    @Test public void cropAndArrowsMatchSourceAtBothSizes() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            Field batchField = WorldDominationGame.class.getDeclaredField("batch"); batchField.setAccessible(true);
            SpriteBatch batch = (SpriteBatch) batchField.get(app);
            Field assetsField = WorldDominationGame.class.getDeclaredField("flashAnimationAssets"); assetsField.setAccessible(true);
            OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) assetsField.get(app);
            Method arrow = WorldDominationGame.class.getDeclaredMethod("drawSourceCarouselArrow", Rectangle.class, int.class);
            arrow.setAccessible(true);
            SourceSetupPanelRenderer renderer = new SourceSetupPanelRenderer();
            Texture source = new Texture(Gdx.files.internal("original/ui/setup-panel-source-5813.png"));
            source.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            Matrix4 oldProjection = new Matrix4(batch.getProjectionMatrix());
            float oldColor = batch.getPackedColor();
            try {
                for (int id : new int[]{5819, 5822}) {
                    assertNotNull(assets.getShape(id));
                    assertEquals(12, assets.getShape(id).getRegionWidth());
                    assertEquals(44, assets.getShape(id).getRegionHeight());
                    assertEquals(0f, assets.getShapeOriginX(id), 0f);
                    assertEquals(0f, assets.getShapeOriginY(id), 0f);
                }
                for (int[] size : new int[][]{{800, 600}, {1194, 552}}) {
                    int w = size[0], h = size[1];
                    SetupScreenLayout layout = new SetupScreenLayout(); layout.update(w, h, 0, 0, 0, 0);
                    FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
                    try {
                        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, w, h));
                        fb.begin(); clear(); batch.begin(); batch.setColor(.2f,.4f,.6f,.8f);
                        float saved = batch.getPackedColor();
                        renderer.drawCarousel(batch, layout.leaderCarouselPanel, layout.leaderCards, layout.leaderDescription);
                        arrow.invoke(app, layout.leaderPrevious, 5819);
                        arrow.invoke(app, layout.leaderNext, 5822);
                        assertEquals(saved, batch.getPackedColor(), 0f);
                        batch.end(); byte[] actual = ScreenUtils.getFrameBufferPixels(0,0,w,h,true); fb.end();
                        byte[] expected = reference(fb,batch,source,assets,layout,w,h,false);
                        byte[] wrong = reference(fb,batch,source,assets,layout,w,h,true);
                        evidence("source-setup-carousel-"+w+"x"+h+"-actual.png",actual,w,h);
                        evidence("source-setup-carousel-"+w+"x"+h+"-expected.png",expected,w,h);
                        evidence("source-setup-carousel-"+w+"x"+h+"-wrong.png",wrong,w,h);
                        assertArrayEquals(expected,actual);
                        // Inspect each 25px source side independently, including the
                        // full rods and fasteners instead of only the outer corners.
                        Rectangle panel=layout.leaderCarouselPanel;
                        for(int side=0;side<2;side++) {
                            int left=(int)(side==0?panel.x:panel.x+panel.width-25f);
                            int compared=0,different=0;
                            for(int y=(int)panel.y+13;y<(int)(panel.y+panel.height)-13;y++)
                                for(int x=left+1;x<left+24;x++) {
                                    int k=((h-1-y)*w+x)*4;
                                    for(int c=0;c<4;c++) assertEquals("source hardware",expected[k+c],actual[k+c]);
                                    if(expected[k]!=wrong[k] || expected[k+1]!=wrong[k+1]
                                            || expected[k+2]!=wrong[k+2]) different++;
                                    compared++;
                                }
                            assertTrue("hardware pixels exercised",compared>0);
                            assertTrue("shifted source hardware differs",different>0);
                        }
                        int changed=0,painted=0;
                        for(int i=0;i<actual.length;i+=4) {
                            if((actual[i+3]&255)>0)painted++;
                            if(actual[i]!=wrong[i] || actual[i+1]!=wrong[i+1]
                                    || actual[i+2]!=wrong[i+2] || actual[i+3]!=wrong[i+3])changed++;
                        }
                        assertTrue(painted>0); assertTrue("shifted crop and swapped arrows must differ",changed>0);
                        assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                    } finally {fb.dispose();}
                }
            } finally {batch.setProjectionMatrix(oldProjection);batch.setPackedColor(oldColor);source.dispose();renderer.dispose();}
        });
    }
    private static byte[] reference(FrameBuffer fb, SpriteBatch batch, Texture source,
            OriginalFlashAnimationAssets assets, SetupScreenLayout layout, int w, int h, boolean wrong) {
        fb.begin();clear();batch.begin();batch.setColor(1,1,1,1);
        Rectangle crop=layout.leaderCarouselPanel;
        sourceNine(batch,source,crop,147+(wrong?1:0),319,488,180,25,12,true);
        for(int i=0;i<6;i++) {
            Rectangle well=layout.leaderCards[i];
            float scale=Math.min(well.width/64f,well.height/70f);
            batch.draw(new TextureRegion(source,173+74*i,339,64,70),
                    well.x+(well.width-64f*scale)/2f,well.y+(well.height-70f*scale)/2f,
                    64f*scale,70f*scale);
        }
        sourceNine(batch,source,layout.leaderDescription,176,428,430,49,10,10,false);
        for(int i=0;i<2;i++) {
            Rectangle target=i==0?layout.leaderPrevious:layout.leaderNext;
            float scale=Math.min(target.width/12f,target.height/44f);
            int id=(i==0)!=wrong?5819:5822;
            batch.draw(assets.getShape(id),target.x+(target.width-12f*scale)/2f,
                    target.y+(target.height-44f*scale)/2f,12f*scale,44f*scale);
        }
        batch.end();byte[] pixels=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);fb.end();return pixels;
    }
    // Literal source rectangles, including the clear metal strip used for the body.
    private static void sourceNine(SpriteBatch batch,Texture source,Rectangle dest,
            int sx,int sy,int sw,int sh,int side,int cap,boolean metal) {
        float scale=Math.min(1f,Math.min(dest.width/(2f*side),dest.height/(2f*cap)));
        float edgeX=scale*side,edgeY=scale*cap;
        int[] xx={sx,sx+side,sx+sw-side}, yy={sy,sy+cap,sy+sh-cap};
        int[] ww={side,sw-2*side,side}, hh={cap,sh-2*cap,cap};
        float[] dx={dest.x,dest.x+edgeX,dest.x+dest.width-edgeX};
        float[] dy={dest.y+dest.height-edgeY,dest.y+edgeY,dest.y};
        float[] dw={edgeX,dest.width-2*edgeX,edgeX}, dh={edgeY,dest.height-2*edgeY,edgeY};
        for(int row=0;row<3;row++)for(int col=0;col<3;col++) {
            TextureRegion part=(metal&&row==1&&col==1)?new TextureRegion(source,250,414,300,10)
                    :new TextureRegion(source,xx[col],yy[row],ww[col],hh[row]);
            if(dw[col]>0&&dh[row]>0)batch.draw(part,dx[col],dy[row],dw[col],dh[row]);
        }
    }
    private static void clear(){Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);}
    private static void evidence(String name,byte[] bytes,int w,int h){
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);assertTrue(dir.exists()||dir.mkdirs());
        Pixmap image=new Pixmap(w,h,Pixmap.Format.RGBA8888);
        try{image.getPixels().put(bytes);image.getPixels().position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(dir,name).getAbsolutePath()),image);
        }finally{image.dispose();}
    }
}
