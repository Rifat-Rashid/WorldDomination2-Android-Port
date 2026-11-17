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
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalUiAssets;
import com.example.worlddomination.game.assets.OriginalCityAssets;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

/** Authored frame coverage and actual World placement, on GLES. */
@RunWith(AndroidJUnit4.class)
public final class OriginalBattlefieldFrameInstrumentedTest {
    @Test public void authoredFramePreservesSourceCornersAndWorldMapGeometry() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            OriginalUiAssets assets=(OriginalUiAssets)field("uiAssets").get(app);
            OriginalBattlefieldFrame frame=new OriginalBattlefieldFrame(assets.battlefieldFrame);
            SpriteBatch batch=new SpriteBatch();
            Texture independent=new Texture(Gdx.files.internal("original/ui/battlefield-frame.png"),true);
            independent.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);
            try {
                for (int[] size:new int[][]{{800,600},{1194,552}}) {
                    int w=size[0],h=size[1]; Rectangle bounds=new Rectangle(100,100,w-200,h-200);
                    FrameBuffer fb=new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false);
                    try {
                        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,w,h));
                        fb.begin();Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                        batch.begin();batch.setColor(.25f,.5f,.75f,.5f);float color=batch.getPackedColor();
                        frame.draw(batch,bounds);assertEquals("caller color restored",color,batch.getPackedColor(),0f);
                        batch.end();byte[] actual=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);
                        fb.end();
                        evidence("source-battlefield-frame-"+w+"x"+h+".png",actual,w,h);
                        int outside=0,inner=0,top=0,bottom=0,left=0,right=0;
                        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                            int alpha=actual[((h-1-y)*w+x)*4+3]&255;
                            if(alpha==0)continue;
                            if(x<bounds.x||x>=bounds.x+bounds.width||y<bounds.y||y>=bounds.y+bounds.height)outside++;
                            else if(x>=bounds.x+7&&x<bounds.x+bounds.width-7&&y>=bounds.y+7&&y<bounds.y+bounds.height-7)inner++;
                            else {if(y>=bounds.y+bounds.height-6)top++; if(y<bounds.y+6)bottom++;
                                if(x<bounds.x+6)left++;if(x>=bounds.x+bounds.width-6)right++;}
                        }
                        assertEquals("outside frame untouched",0,outside);
                        assertEquals("interior beyond six points untouched",0,inner);
                        assertTrue("top/bottom/left/right paint",top>20&&bottom>20&&left>20&&right>20);
                        // One-to-one source-pixel pass: 28x24 corner art maps to
                        // exactly 28x24 framebuffer pixels, avoiding mipmap rounding.
                        float scale=6f/28f;
                        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,w*scale,h*scale));
                        Rectangle nativeBounds=new Rectangle(100f*scale,100f*scale,
                                (w-200f)*scale,(h-200f)*scale);
                        fb.begin();Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                        batch.begin();frame.draw(batch,nativeBounds);batch.end();
                        byte[] nativeFrame=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);fb.end();
                        // Independent oracle draws the whole source texture once per
                        // corner, rather than reconstructing the eight frame slices.
                        for(int corner=0;corner<2;corner++) {
                            fb.begin();Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                            batch.begin();batch.setColor(1,1,1,1);
                            float x=corner==0?nativeBounds.x:nativeBounds.x+nativeBounds.width-413f*scale;
                            float y=corner==0?nativeBounds.y+nativeBounds.height-304f*scale:nativeBounds.y;
                            batch.draw(independent,x,y,413f*scale,304f*scale);batch.end();
                            byte[] oracle=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);fb.end();
                            int x0=corner==0?101:w-127, y0=corner==0?h-123:101;
                            int checked=0;
                            for(int py=y0;py<y0+22;py++)for(int px=x0;px<x0+26;px++) {
                                int i=((h-1-py)*w+px)*4;
                                if((oracle[i+3]&255)>0)checked++;
                                for(int c=0;c<4;c++)assertEquals("source corner RGBA",oracle[i+c]&255,nativeFrame[i+c]&255);
                            }
                            assertTrue("authored corner paints",checked>0);
                        }
                        assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                    } finally {fb.dispose();}
                }
            } finally {independent.dispose();batch.dispose();}
            GameSetup setup=new GameSetup(0L);assertTrue(setup.toggleOpponent(0));
            GameState game=new GameState(setup);field("game").set(app,game);field("modalOpen").setBoolean(app,false);
            ((BattlefieldCityRenderer)field("cityRenderer").get(app)).reset(game);
            field("skipNextFrameDelta").setBoolean(app,true);app.render();
            assertNotNull("World uses authored frame",field("battlefieldFrameRenderer").get(app));
            Rectangle frameBounds=(Rectangle)field("mapFrame").get(app);
            Rectangle map=(Rectangle)field("mapBounds").get(app);
            float expectedWidth=Math.min(frameBounds.width-12f,(frameBounds.height-12f)*523f/318f);
            assertEquals(expectedWidth,map.width,.001f);assertEquals(expectedWidth*318f/523f,map.height,.001f);
            assertEquals(frameBounds.x+(frameBounds.width-map.width)/2f,map.x,.001f);
            assertEquals(frameBounds.y+(frameBounds.height-map.height)/2f,map.y,.001f);
            OriginalCityAssets cityAssets=(OriginalCityAssets)field("cityAssets").get(app);
            OriginalCityAssets.Frame cityFrame=cityAssets.getFrame(game.getHuman().getReligionCode(),
                    game.getHuman().getCity(0).getArtworkFrame());
            Rectangle cityBounds=new Rectangle();
            CitySpriteHitGeometry.setBounds(cityBounds,map,0,0,cityFrame.offsetX,cityFrame.offsetY,
                    cityFrame.region.getRegionWidth(),cityFrame.region.getRegionHeight(),cityAssets.getArtworkScale());
            float hitX=BattlefieldCityLayout.x(map,0,0), hitY=cityBounds.y+2f;
            assertTrue(map.contains(hitX,hitY));
            assertEquals("city sprite hit uses unchanged map geometry",0,
                    ((BattlefieldCityRenderer)field("cityRenderer").get(app)).hitTest(hitX,hitY,map,game));
            int w=Gdx.graphics.getBackBufferWidth(),h=Gdx.graphics.getBackBufferHeight();
            byte[] world=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);
            evidence("source-battlefield-frame-world.png",world,w,h);
            OriginalBattlefieldFrame original=(OriginalBattlefieldFrame)field("battlefieldFrameRenderer").get(app);
            Pixmap transparent=new Pixmap(413,304,Pixmap.Format.RGBA8888);
            transparent.setColor(0,0,0,0);transparent.fill();
            Texture blank=null;
            try {
                // Keep the Pixmap alive as the managed texture's reload source.
                blank=new Texture(new PixmapTextureData(transparent,Pixmap.Format.RGBA8888,false,false,true));
                field("battlefieldFrameRenderer").set(app,new OriginalBattlefieldFrame(new TextureRegion(blank)));
                field("skipNextFrameDelta").setBoolean(app,true);app.render();
                byte[] missing=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);
                evidence("source-battlefield-frame-world-missing.png",missing,w,h);
                Viewport viewport=(Viewport)field("viewport").get(app);
                Vector2 lo=viewport.project(new Vector2(frameBounds.x,frameBounds.y));
                Vector2 hi=viewport.project(new Vector2(frameBounds.x+frameBounds.width,
                        frameBounds.y+frameBounds.height));
                Vector2 innerLo=viewport.project(new Vector2(frameBounds.x+7f,frameBounds.y+7f));
                Vector2 innerHi=viewport.project(new Vector2(frameBounds.x+frameBounds.width-7f,
                        frameBounds.y+frameBounds.height-7f));
                int changedBorder=0,changedOutside=0,changedInner=0;
                for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                    int index=((h-1-y)*w+x)*4;
                    boolean differs=false;
                    for(int c=0;c<4;c++) differs|=world[index+c]!=missing[index+c];
                    if(!differs)continue;
                    boolean outside=x<lo.x-1f||x>hi.x+1f||y<lo.y-1f||y>hi.y+1f;
                    boolean inside=x>=innerLo.x&&x<innerHi.x&&y>=innerLo.y&&y<innerHi.y;
                    if(outside)changedOutside++;else if(inside)changedInner++;else changedBorder++;
                }
                assertTrue("missing authored frame changes border",changedBorder>0);
                assertEquals("missing frame does not change exterior",0,changedOutside);
                assertEquals("missing frame does not change map interior",0,changedInner);
            } finally {
                field("battlefieldFrameRenderer").set(app,original);
                if(blank!=null)blank.dispose();
                transparent.dispose();
            }
            assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        });
    }
    private static void evidence(String name,byte[] rgba,int width,int height) {
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
        Pixmap p=new Pixmap(width,height,Pixmap.Format.RGBA8888);
        try {p.getPixels().put(rgba).position(0);PixmapIO.writePNG(Gdx.files.absolute(new File(dir,name).getAbsolutePath()),p);}
        finally {p.dispose();}
    }
    private static Field field(String name)throws Exception {Field f=WorldDominationGame.class.getDeclaredField(name);f.setAccessible(true);return f;}
}
