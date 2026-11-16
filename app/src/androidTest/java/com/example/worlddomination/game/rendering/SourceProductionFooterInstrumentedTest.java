package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.ProductionScreenLayout;
import com.example.worlddomination.game.ui.ScrollPaneState;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Actual production controls and GPU glyphs against the source phone-crop fields. */
@RunWith(AndroidJUnit4.class)
public final class SourceProductionFooterInstrumentedTest {
    @Test public void selectedTotalFitsSourceFieldAtBothAspectRatios() throws Exception {
        verify(false);
    }
    @Test public void fiveSelectedRowsLeaveTheSourceFooterClear() throws Exception {
        verify(true);
    }
    private static void verify(boolean five) throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            int physicalWidth=Gdx.graphics.getWidth(),physicalHeight=Gdx.graphics.getHeight();
            try {
                for(int[] size:new int[][]{{800,600},{1194,552}}) {
                    GameSetup setup=new GameSetup(0L);setup.setDifficulty(1);
                    assertTrue(setup.toggleOpponent(0));assertTrue(setup.toggleOpponent(1));
                    GameState game=new GameState(setup);field("game").set(app,game);
                    field("modalOpen").setBoolean(app,false);
                    ((BattlefieldCityRenderer)field("cityRenderer").get(app)).reset(game);
                    app.resize(size[0],size[1]);
                    ScrollPaneState scroll=(ScrollPaneState)field("navigationScroll").get(app);
                    scroll.moveBy(-scroll.getOffset());render(app);
                    Rectangle nav=(Rectangle)field("navViewport").get(app);
                    Rectangle row=((Rectangle[])field("navRows").get(app))[GameState.Action.PRODUCTION.ordinal()];
                    scroll.moveBy(nav.y+nav.height/2f-(row.y+row.height/2f));render(app);
                    tap(app,((Rectangle[])field("navRows").get(app))[GameState.Action.PRODUCTION.ordinal()]);
                    Rectangle modal=(Rectangle)field("modal").get(app);
                    int[] sliders=five?new int[]{0,1,5,6,7}:new int[]{0};
                    for(int index:sliders) {
                        tap(app,ProductionScreenLayout.setHandleBounds(index,five?11:51,modal,new Rectangle()));
                        assertEquals(five?10:50,game.getProductionPercent(ProductionScreenLayout.categoryKey(index)));
                    }
                    assertEquals(five?5:1,field("productionSummaryCount").getInt(app));
                    double rng=game.createPresentationRandomCopy().nextDouble();render(app);render(app);
                    FrameBuffer fb=new FrameBuffer(Pixmap.Format.RGBA8888,size[0],size[1],false);
                    try {
                        fb.begin();
                        try {
                            render(app);byte[] actual=pixels(size);
                            int count=field("productionSummaryCount").getInt(app);
                            String[] names=(String[])field("productionSummaryCategories").get(app);
                            String[] values=(String[])field("productionSummaryAmounts").get(app);
                            String[] savedNames=Arrays.copyOf(names,count),savedValues=Arrays.copyOf(values,count);
                            Field totalField=field("productionSummaryTotal");String total=(String)totalField.get(app);
                            Arrays.fill(names,0,count,"");Arrays.fill(values,0,count,"");totalField.set(app,"");
                            render(app);byte[] blank=pixels(size);
                            SpriteBatch batch=(SpriteBatch)field("batch").get(app);
                            BitmapFont font=(BitmapFont)field("font").get(app);
                            drawExpected(batch,font,modal,savedNames,savedValues,total,false,five);
                            byte[] expected=pixels(size);
                            assertArrayEquals("all full glyphs at independent authored positions",expected,actual);
                            Viewport vp=(Viewport)field("viewport").get(app);
                            Rectangle footer=pixelField(vp,modal,129.6f,356.55f,226.65f,243.2f);
                            Rectangle categories=pixelField(vp,modal,128.95f,225.35f,156.1f,228f);
                            Rectangle amounts=pixelField(vp,modal,220.85f,355.05f,156.1f,228f);
                            int ink=0,outside=0;
                            for(int y=0;y<size[1];y++)for(int x=0;x<size[0];x++) {
                                if(changed(actual,blank,(y*size[0]+x)*4)) {
                                    ink++;
                                    if(!contains(footer,x,y)&&!contains(categories,x,y)&&!contains(amounts,x,y))outside++;
                                }
                            }
                            assertTrue("nonempty authored text",ink>20);
                            assertEquals("summary ink stays within source fields",0,outside);
                            render(app);
                            drawExpected(batch,font,modal,savedNames,savedValues,total,true,five);
                            byte[] wrong=pixels(size);int wrongDifferences=0;
                            for(int i=0;i<actual.length;i++)if(actual[i]!=wrong[i])wrongDifferences++;
                            assertTrue("old anchors are a meaningful negative",wrongDifferences>20);
                            String prefix="source-production-summary-"+(five?"five":"one")+"-"+size[0]+"x"+size[1];
                            evidence(prefix+"-actual.png",actual,size);
                            evidence(prefix+"-expected.png",expected,size);
                            evidence(prefix+"-blank.png",blank,size);
                            evidence(prefix+"-old-placement.png",wrong,size);
                            System.arraycopy(savedNames,0,names,0,count);System.arraycopy(savedValues,0,values,0,count);
                            totalField.set(app,total);
                            assertEquals("GPU drawing keeps canonical RNG",rng,game.createPresentationRandomCopy().nextDouble(),0d);
                        } finally {fb.end();}
                    } finally {fb.dispose();}
                }
            } finally {app.resize(physicalWidth,physicalHeight);}
        });
    }
    private static void drawExpected(SpriteBatch batch,BitmapFont font,Rectangle modal,
                                     String[] names,String[] values,String total,boolean old,boolean five) {
        float originalScale=font.getScaleX();font.getData().setScale(.64f*(modal.height/275f));
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);GlyphLayout glyphs=new GlyphLayout();batch.begin();
        font.setColor(1,1,1,1);
        for(int i=0;i<names.length;i++) {
            float y=modal.y+modal.height-(old&&five?173f+i*13.5f:160.1f+i*13f)*modal.height/275f;
            font.draw(batch,names[i],modal.x+130.95f*modal.width/365f,y);
            glyphs.setText(font,values[i]);
            float amountY=modal.y+modal.height-(old&&five?173f+i*13.5f:160.1f+i*12f)*modal.height/275f;
            font.draw(batch,values[i],modal.x+353.85f*modal.width/365f-glyphs.width,amountY);
        }
        font.setColor(0,36f/255f,60f/255f,1);glyphs.setText(font,total);
        font.draw(batch,total,modal.x+354.6f*modal.width/365f-glyphs.width,
                modal.y+modal.height-(old&&!five?237f:230.65f)*modal.height/275f);
        batch.end();font.getData().setScale(originalScale);font.setColor(1,1,1,1);
    }
    private static Rectangle pixelField(Viewport vp,Rectangle m,float left,float right,float top,float bottom) {
        Vector2 lo=vp.project(new Vector2(m.x+left*m.width/365f,m.y+m.height-bottom*m.height/275f));
        Vector2 hi=vp.project(new Vector2(m.x+right*m.width/365f,m.y+m.height-top*m.height/275f));
        return new Rectangle(lo.x-2,lo.y-2,hi.x-lo.x+4,hi.y-lo.y+4);
    }
    private static boolean contains(Rectangle r,int x,int y){return r.contains(x,y);}
    private static boolean changed(byte[] a,byte[] b,int offset) {
        return a[offset]!=b[offset]||a[offset+1]!=b[offset+1]||a[offset+2]!=b[offset+2]||a[offset+3]!=b[offset+3];
    }
    private static byte[] pixels(int[] s){return ScreenUtils.getFrameBufferPixels(0,0,s[0],s[1],false);}
    private static void evidence(String name,byte[] raw,int[] s)throws Exception {
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
        Pixmap image=new Pixmap(s[0],s[1],Pixmap.Format.RGBA8888);
        try {
            byte[] topDown=new byte[raw.length];int stride=s[0]*4;
            for(int y=0;y<s[1];y++)System.arraycopy(raw,(s[1]-1-y)*stride,topDown,y*stride,stride);
            image.getPixels().put(topDown).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(dir,name).getAbsolutePath()),image);
        } finally {image.dispose();}
    }
    private static void tap(WorldDominationGame app,Rectangle r)throws Exception {
        Vector2 p=((Viewport)field("viewport").get(app)).project(new Vector2(r.x+r.width/2f,r.y+r.height/2f));
        int x=Math.round(p.x),y=Gdx.graphics.getHeight()-Math.round(p.y);app.touchDown(x,y,0,0);app.touchUp(x,y,0,0);
    }
    private static void render(WorldDominationGame app)throws Exception{field("skipNextFrameDelta").setBoolean(app,true);app.render();}
    private static Field field(String name)throws Exception{Field f=WorldDominationGame.class.getDeclaredField(name);f.setAccessible(true);return f;}
}
