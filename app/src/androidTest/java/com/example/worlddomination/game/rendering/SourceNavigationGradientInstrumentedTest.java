package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

/** Phone gradient endpoints and live navigation behavior. */
@RunWith(AndroidJUnit4.class)
public final class SourceNavigationGradientInstrumentedTest {
    @Test public void gradientMatchesPhoneColorsAndRespectsClip() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            ShapeRenderer shapes=new ShapeRenderer();
            try {
                for(int[] size:new int[][]{{800,600},{1194,552}}) {
                    int w=size[0],h=size[1]; FrameBuffer fb=new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false);
                    try {
                        shapes.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,w,h));
                        Rectangle row=new Rectangle(100,100,300,100);
                        for(boolean selected:new boolean[]{false,true}) {
                            fb.begin();Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                            Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                            shapes.begin(ShapeRenderer.ShapeType.Filled);
                            shapes.setColor(.2f,.3f,.4f,.5f);
                            float prior=shapes.getColor().toFloatBits();
                            SourceNavigationGradient.draw(shapes,row,selected);
                            assertEquals("shape caller color preserved",prior,shapes.getColor().toFloatBits(),0f);
                            shapes.end();byte[] image=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);fb.end();
                            evidence("source-navigation-gradient-"+w+"x"+h+"-"+(selected?"selected":"normal")+".png",image,w,h);
                            int[] bottom=selected?new int[]{12,47,68}:new int[]{11,23,29};
                            int[] top=selected?new int[]{42,90,115}:new int[]{48,66,75};
                            Set<Integer> levels=new HashSet<>();int previous=-1;
                            for(int y=103;y<197;y++) {
                                int i=((h-1-y)*w+250)*4;
                                double t=(y+.5-103)/94.0;
                                for(int c=0;c<3;c++) {
                                    int actual=image[i+c]&255;
                                    double expected=bottom[c]+(top[c]-bottom[c])*t;
                                    assertTrue("source gradient channel",Math.abs(actual-expected)<=2.0);
                                }
                                int red=image[i]&255;
                                assertTrue("light rises toward top",red>=previous);previous=red;levels.add(red);
                                assertEquals(255,image[i+3]&255);
                            }
                            assertTrue("smooth gradient has more than eight distinct levels",levels.size()>8);
                            assertEquals("outside gradient remains transparent",0,image[((h-1-150)*w+101)*4+3]&255);
                        }
                        fb.begin();Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                        Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);Gdx.gl.glScissor(150,120,100,50);
                        shapes.begin(ShapeRenderer.ShapeType.Filled);SourceNavigationGradient.draw(shapes,row,false);shapes.end();
                        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                        byte[] clipped=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);fb.end();
                        int inside=0,outside=0;
                        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((clipped[((h-1-y)*w+x)*4+3]&255)>0) {
                            if(x>=150&&x<250&&y>=120&&y<170)inside++;else outside++;
                        }
                        assertTrue("clipped portion paints",inside>0);assertEquals("scissor contains paint",0,outside);
                        assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                    } finally {fb.dispose();}
                }
            } finally {shapes.dispose();}
        });
    }

    @Test public void worldNavigationTouchSelectsSourceGradientRow() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameSetup setup=new GameSetup(0L);setup.setDifficulty(1);
            assertTrue(setup.toggleOpponent(0));assertTrue(setup.toggleOpponent(1));
            GameState game=new GameState(setup);field("game").set(app,game);field("modalOpen").setBoolean(app,false);
            ((BattlefieldCityRenderer)field("cityRenderer").get(app)).reset(game);
            render(app);Rectangle viewport=(Rectangle)field("navViewport").get(app);
            Rectangle[] rows=(Rectangle[])field("navRows").get(app);Rectangle row=rows[GameState.Action.RESOURCES.ordinal()];
            assertTrue(viewport.contains(row.x+row.width/2f,row.y+row.height/2f));
            double rng=game.createPresentationRandomCopy().nextDouble();
            int w=Gdx.graphics.getBackBufferWidth(),h=Gdx.graphics.getBackBufferHeight();
            byte[] normal=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);
            Viewport worldViewport=(Viewport)field("viewport").get(app);
            Vector2 point=worldViewport.project(new Vector2(row.x+row.width/2f,row.y+row.height/2f));
            int x=Math.round(point.x),y=Gdx.graphics.getHeight()-Math.round(point.y);
            app.touchDown(x,y,0,0);app.touchUp(x,y,0,0);
            assertEquals(GameState.Action.RESOURCES,game.getSelectedAction());render(app);
            assertEquals(rng,game.createPresentationRandomCopy().nextDouble(),0d);
            byte[] selected=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);
            evidence("source-navigation-gradient-world-normal.png",normal,w,h);
            evidence("source-navigation-gradient-world-selected.png",selected,w,h);
            // Sample away from the centered glyph, the 2.5-unit frame inset,
            // and the pre-existing top highlight line.
            Vector2 lower=worldViewport.project(new Vector2(row.x+20f,row.y+3f));
            Vector2 upper=worldViewport.project(new Vector2(row.x+20f,row.y+row.height-3f));
            int px=(int)Math.floor(lower.x);
            int[] normalBottom={11,23,29}, normalTop={48,66,75};
            int[] selectedBottom={12,47,68}, selectedTop={42,90,115};
            int wrongStateDifference=0;
            for(double fraction:new double[]{.25,.75}) {
                int py=(int)Math.floor(lower.y+(upper.y-lower.y)*fraction);
                double t=(py+.5-lower.y)/(upper.y-lower.y);
                int index=((h-1-py)*w+px)*4;
                for(int c=0;c<3;c++) {
                    int expectedNormal=(int)Math.round(normalBottom[c]+(normalTop[c]-normalBottom[c])*t);
                    int expectedSelected=(int)Math.round(selectedBottom[c]+(selectedTop[c]-selectedBottom[c])*t);
                    assertTrue("live normal gradient channel",Math.abs((normal[index+c]&255)-expectedNormal)<=3);
                    assertTrue("live selected gradient channel",Math.abs((selected[index+c]&255)-expectedSelected)<=3);
                    wrongStateDifference+=Math.abs((selected[index+c]&255)-expectedNormal);
                }
            }
            assertTrue("selected row differs from wrong normal-state control",wrongStateDifference>12);
            assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        });
    }
    private static void render(WorldDominationGame app)throws Exception {field("skipNextFrameDelta").setBoolean(app,true);app.render();}
    private static Field field(String name)throws Exception {Field f=WorldDominationGame.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void evidence(String name,byte[] bytes,int width,int height) {
        File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
        Pixmap image=new Pixmap(width,height,Pixmap.Format.RGBA8888);
        try {image.getPixels().put(bytes).position(0);PixmapIO.writePNG(Gdx.files.absolute(new File(dir,name).getAbsolutePath()),image);}
        finally {image.dispose();}
    }
}
