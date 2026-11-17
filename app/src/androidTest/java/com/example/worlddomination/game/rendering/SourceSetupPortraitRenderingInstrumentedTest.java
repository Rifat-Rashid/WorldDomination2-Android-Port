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
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalUiAssets;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Actual setup helper versus source atlas IDs and independently calculated fitting. */
@RunWith(AndroidJUnit4.class)
public final class SourceSetupPortraitRenderingInstrumentedTest {
    private static final int[] LEADERS={0,1,2,3,4,5,12,18};
    @Test public void allSetupCardsUseSourcePortraitsAndKeepSelectionAlpha() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            SpriteBatch batch=(SpriteBatch)field("batch").get(app);
            OriginalUiAssets ui=(OriginalUiAssets)field("uiAssets").get(app);
            Method draw=WorldDominationGame.class.getDeclaredMethod("drawSourceSetupPortrait",
                    int.class,float.class,float.class,float.class,float.class);
            draw.setAccessible(true);
            Matrix4 projection=new Matrix4(batch.getProjectionMatrix());
            float callerColor=batch.getPackedColor();
            TextureRegion[] source=new TextureRegion[LEADERS.length];
            OriginalFlashAnimationAssets flash=(OriginalFlashAnimationAssets)field("flashAnimationAssets").get(app);
            assertTrue(flash.areSetupPortraitsLoaded());
            FlashAnimationTimeline timeline=flash.getTimeline(OriginalFlashAnimationAssets.LEADER_PORTRAITS_TIMELINE);
            assertNotNull(timeline);
            try {
                for(int i=0;i<LEADERS.length;i++) {
                    int shapeId=3150+2*LEADERS[i];
                    assertEquals("source timeline mapping",shapeId,timeline.getFrame(
                            GameSetup.leader(LEADERS[i]).portraitFrame-1).shapeId);
                    source[i]=flash.getShape(shapeId);assertNotNull(source[i]);
                    assertEquals(70,source[i].getRegionWidth());assertEquals(75,source[i].getRegionHeight());
                }
                for(int mode=0;mode<2;mode++) {
                    int viewportWidth=mode==0?800:1194,viewportHeight=mode==0?600:552;
                    batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,viewportWidth,viewportHeight));
                    FrameBuffer fb=new FrameBuffer(Pixmap.Format.RGBA8888,viewportWidth,viewportHeight,false);
                    try {
                    float cardWidth=mode==0?90f:140f,cardHeight=mode==0?90f:70f;
                    byte[] actual=render(fb,batch,viewportWidth,viewportHeight,()-> {
                        for(int i=0;i<LEADERS.length;i++) {
                            float alpha=i%2==0?1f:SetupScreenLayout.SELECTED_LEADER_ALPHA;
                            batch.setColor(1f,1f,1f,alpha);float color=batch.getPackedColor();
                            draw.invoke(app,LEADERS[i],30f+(i%4)*190f,30f+(i/4)*180f,cardWidth,cardHeight);
                            assertEquals("portrait preserves caller color",color,batch.getPackedColor(),0f);
                        }
                    });
                    byte[] expected=render(fb,batch,viewportWidth,viewportHeight,()-> {
                        for(int i=0;i<LEADERS.length;i++) {
                            batch.setColor(1f,1f,1f,i%2==0?1f:SetupScreenLayout.SELECTED_LEADER_ALPHA);
                            float x=30f+(i%4)*190f,y=30f+(i/4)*180f;
                            float scale=Math.min(cardWidth/70f,cardHeight/75f);
                            float width=70f*scale,height=75f*scale;
                            batch.draw(source[i],x+(cardWidth-width)/2f,y+(cardHeight-height)/2f,width,height);
                        }
                    });
                    byte[] old=render(fb,batch,viewportWidth,viewportHeight,()-> {
                        for(int i=0;i<LEADERS.length;i++) {
                            batch.setColor(1f,1f,1f,i%2==0?1f:SetupScreenLayout.SELECTED_LEADER_ALPHA);
                            float x=30f+(i%4)*190f,y=30f+(i/4)*180f;
                            float iw=i<4?65f:70f,ih=i<4?60f:75f;
                            float scale=Math.min(cardWidth/iw,cardHeight/ih);
                            float width=iw*scale,height=ih*scale;
                            if(i<4)batch.draw(ui.portraits[i],x+(cardWidth-width)/2f,y+(cardHeight-height)/2f,width,height);
                            else batch.draw(source[i],x+(cardWidth-width)/2f,y+(cardHeight-height)/2f,width,height);
                        }
                    });
                    String prefix="source-setup-portraits-"+(mode==0?"square":"wide")+"-";
                    evidence(prefix+"actual.png",actual,viewportWidth,viewportHeight);evidence(prefix+"expected.png",expected,viewportWidth,viewportHeight);evidence(prefix+"old.png",old,viewportWidth,viewportHeight);
                    int painted=0;int[] wrongByCard=new int[4];
                    for(int y=0;y<viewportHeight;y++)for(int x=0;x<viewportWidth;x++) {
                        int offset=(y*viewportWidth+x)*4;
                        if((expected[offset+3]&255)>0)painted++;
                        boolean differs=false;
                        for(int c=0;c<4;c++) differs|=expected[offset+c]!=old[offset+c];
                        if(differs) {
                            // First four test cards occupy the lower row in world coordinates.
                            int worldY=viewportHeight-1-y;
                            for(int card=0;card<4;card++) {
                                float left=30f+card*190f;
                                if(x>=left && x<left+cardWidth && worldY>=30 && worldY<30+cardHeight)
                                    wrongByCard[card]++;
                            }
                        }
                    }
                    assertTrue("source portraits visibly paint",painted>0);
                    for(int card=0;card<4;card++) assertTrue("old crop differs for leader "+card,wrongByCard[card]>0);
                    assertArrayEquals("source atlas portrait routing, fitting and alpha",expected,actual);
                    assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                    } finally {fb.dispose();}
                }
            } finally {
                batch.setPackedColor(callerColor);batch.setProjectionMatrix(projection);
            }
        });
    }
    private interface Draw { void run() throws Exception; }
    private static byte[] render(FrameBuffer fb,SpriteBatch batch,int width,int height,Draw draw)throws Exception {
        fb.begin();Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();try{draw.run();}finally{batch.end();}
        byte[] pixels=ScreenUtils.getFrameBufferPixels(0,0,width,height,true);fb.end();return pixels;
    }
    private static Field field(String name)throws Exception {Field f=WorldDominationGame.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void evidence(String name,byte[] bytes,int width,int height) {
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
        Pixmap p=new Pixmap(width,height,Pixmap.Format.RGBA8888);try{p.getPixels().put(bytes).position(0);PixmapIO.writePNG(Gdx.files.absolute(new File(dir,name).getAbsolutePath()),p);}finally{p.dispose();}
    }
}
