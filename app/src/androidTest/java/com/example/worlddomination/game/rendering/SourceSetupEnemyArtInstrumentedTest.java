package com.example.worlddomination.game.rendering;

import android.util.Base64;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import com.caverock.androidsvg.SVG;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.graphics.PixmapIO;
import java.io.File;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Compares packaged artwork against the independently decoded source SVG bitmap. */
@RunWith(AndroidJUnit4.class)
public final class SourceSetupEnemyArtInstrumentedTest {
    @Test public void originalFourWellsAndArrowAtBothSizes() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            byte[] svg;
            try (InputStream stream = new GZIPInputStream(Gdx.files.internal(
                    "original/runtime/shapes/5813.svgz").read());
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = stream.read(buffer)) != -1) out.write(buffer, 0, count);
                svg = out.toByteArray();
            }
            Matcher match = Pattern.compile("data:image/[^;]+;base64,([^\"]+)")
                    .matcher(new String(svg, java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(match.find());
            byte[] image = Base64.decode(match.group(1), Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
            assertNotNull(bitmap);
            Pixmap pixels = new Pixmap(bitmap.getWidth(), bitmap.getHeight(), Pixmap.Format.RGBA8888);
            for (int y=0; y<bitmap.getHeight(); y++) for (int x=0; x<bitmap.getWidth(); x++) {
                int argb=bitmap.getPixel(x,y);
                pixels.drawPixel(x,y, (argb << 8) | (argb >>> 24));
            }
            bitmap.recycle();
            Texture source = new Texture(pixels); pixels.dispose();
            source.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            SpriteBatch batch = new SpriteBatch();
            SourceSetupPanelRenderer renderer = new SourceSetupPanelRenderer();
            try {
                for (int[] size : new int[][]{{800,600},{1194,552}}) {
                    int w=size[0], h=size[1];
                    SetupScreenLayout layout=new SetupScreenLayout();
                    layout.update(w,h,0,0,0,0);
                    batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,w,h));
                    FrameBuffer fb=new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false);
                    try {
                        fb.begin(); clear(); batch.begin();
                        renderer.drawOpponents(batch,layout.opponentWells,layout.opponentArrow);
                        batch.end(); byte[] actual=ScreenUtils.getFrameBufferPixels(0,0,w,h,true); fb.end();
                        fb.begin(); clear(); batch.begin();
                        Rectangle a=layout.opponentArrow;
                        drawSourceArrowMargins(batch,source,a);
                        batch.draw(new TextureRegion(source,205,234,100,53),a.x,a.y,a.width,a.height);
                        for(int i=0;i<4;i++) {
                            Rectangle r=layout.opponentWells[i];
                            batch.draw(new TextureRegion(source,320+74*i,226,64,70),r.x,r.y,r.width,r.height);
                        }
                        batch.end(); byte[] expected=ScreenUtils.getFrameBufferPixels(0,0,w,h,true); fb.end();
                        evidence("source-enemy-wells-"+w+"-actual.png",actual,w,h);
                        evidence("source-enemy-wells-"+w+"-expected.png",expected,w,h);
                        assertArrayEquals("independent decoded bitmap",expected,actual);
                        // A core-only source image verifies that all arrow-core pixels
                        // remain untouched and that a hard cut omits the margins.
                        fb.begin(); clear(); batch.begin();
                        batch.draw(new TextureRegion(source,205,234,100,53),a.x,a.y,a.width,a.height);
                        for(int i=0;i<4;i++) {
                            Rectangle r=layout.opponentWells[i];
                            batch.draw(new TextureRegion(source,320+74*i,226,64,70),r.x,r.y,r.width,r.height);
                        }
                        batch.end(); byte[] hardCut=ScreenUtils.getFrameBufferPixels(0,0,w,h,true); fb.end();
                        int marginDifferences=0, corePixels=0;
                        float padding=15f*a.width/100f;
                        for(int y=(int)(a.y-padding);y<a.y+a.height+padding;y++)
                            for(int x=(int)(a.x-padding);x<a.x+a.width+padding;x++) {
                                int k=((h-1-y)*w+x)*4;
                                boolean inside=x+.5f>=a.x && x+.5f<a.x+a.width
                                        && y+.5f>=a.y && y+.5f<a.y+a.height;
                                if(inside) {
                                    for(int c=0;c<4;c++) assertEquals("arrow core remains source",hardCut[k+c],actual[k+c]);
                                    corePixels++;
                                } else if(actual[k]!=hardCut[k] || actual[k+1]!=hardCut[k+1]
                                        || actual[k+2]!=hardCut[k+2] || actual[k+3]!=hardCut[k+3]) marginDifferences++;
                            }
                        assertTrue("core was sampled",corePixels>0);
                        assertTrue("hard-cut margin must differ",marginDifferences>0);
                        fb.begin(); clear(); batch.begin();
                        drawSourceArrowMargins(batch,source,a);
                        batch.draw(new TextureRegion(source,205,234,100,53),a.x,a.y,a.width,a.height);
                        for(int i=0;i<4;i++) {
                            Rectangle r=layout.opponentWells[i];
                            batch.draw(new TextureRegion(source,320,226,64,70),r.x,r.y,r.width,r.height);
                        }
                        batch.end(); byte[] wrong=ScreenUtils.getFrameBufferPixels(0,0,w,h,true); fb.end();
                        evidence("source-enemy-wells-"+w+"-wrong.png",wrong,w,h);
                        int differences=0;
                        for(int i=0;i<actual.length;i+=4)
                            if(actual[i]!=wrong[i] || actual[i+1]!=wrong[i+1] || actual[i+2]!=wrong[i+2]) differences++;
                        assertTrue("reusing first well is detectable",differences>20);
                    } finally { fb.dispose(); }
                }
            } finally { renderer.dispose(); batch.dispose(); source.dispose(); }
        });
    }
    // Independently place eight literal source patches. Alpha is one at the
    // core-facing corners and zero at the outer corners; no renderer helper is used.
    private static void drawSourceArrowMargins(SpriteBatch batch, Texture source, Rectangle a) {
        float scale=a.width/100f, side=15f*scale, cap=14f*scale;
        float left=a.x-side, right=a.x+a.width, bottom=a.y-cap, top=a.y+a.height;
        sourcePatch(batch,source,190,220,15,14,left,top,side,cap, 0,0,0,1);
        sourcePatch(batch,source,205,220,100,14,a.x,top,a.width,cap, 1,0,0,1);
        sourcePatch(batch,source,305,220,15,14,right,top,side,cap, 1,0,0,0);
        sourcePatch(batch,source,190,234,15,53,left,a.y,side,a.height, 0,0,1,1);
        sourcePatch(batch,source,305,234,15,53,right,a.y,side,a.height, 1,1,0,0);
        sourcePatch(batch,source,190,287,15,14,left,bottom,side,cap, 0,0,1,0);
        sourcePatch(batch,source,205,287,100,14,a.x,bottom,a.width,cap, 0,1,1,0);
        sourcePatch(batch,source,305,287,15,14,right,bottom,side,cap, 0,1,0,0);
    }

    private static void sourcePatch(SpriteBatch batch,Texture texture,int sx,int sy,int sw,int sh,
                                    float x,float y,float width,float height,
                                    float bottomLeft,float topLeft,float topRight,float bottomRight) {
        TextureRegion region=new TextureRegion(texture,sx,sy,sw,sh);
        float u0=region.getU(),u1=u0+(region.getU2()-u0);
        float v0=region.getV2(),v1=v0+(region.getV()-v0);
        float[] vertices={
                x,y,Color.toFloatBits(1,1,1,bottomLeft),u0,v0,
                x,y+height,Color.toFloatBits(1,1,1,topLeft),u0,v1,
                x+width,y+height,Color.toFloatBits(1,1,1,topRight),u1,v1,
                x+width,y,Color.toFloatBits(1,1,1,bottomRight),u1,v0};
        batch.draw(texture,vertices,0,vertices.length);
    }

    @Test public void portraitAndCancelUseIndependentSourceRegistrationAndOpacity() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            Field assetsField = WorldDominationGame.class.getDeclaredField("flashAnimationAssets");
            assetsField.setAccessible(true);
            OriginalFlashAnimationAssets assets = (OriginalFlashAnimationAssets) assetsField.get(app);
            assertTrue(assets.areSetupPortraitsLoaded());
            Field layoutField = WorldDominationGame.class.getDeclaredField("setupLayout");
            layoutField.setAccessible(true);
            SetupScreenLayout layout = (SetupScreenLayout) layoutField.get(app);
            Field batchField = WorldDominationGame.class.getDeclaredField("batch");
            batchField.setAccessible(true);
            SpriteBatch batch = (SpriteBatch) batchField.get(app);
            Method draw = WorldDominationGame.class.getDeclaredMethod("drawSourceSelectedEnemy", int.class, int.class);
            draw.setAccessible(true);
            TextureRegion portraitPacked = assets.getShape(3150);
            TextureRegion cancelPacked = assets.getShape(5825);
            assertNotNull(portraitPacked); assertNotNull(cancelPacked);
            Texture portrait = decodeSource(3150, false, portraitPacked);
            Texture cancel = decodeSource(5825, true, cancelPacked);
            TextureRegion portraitOracle = new TextureRegion(portrait, portraitPacked.getRegionX(),
                    portraitPacked.getRegionY(), portraitPacked.getRegionWidth(), portraitPacked.getRegionHeight());
            TextureRegion cancelOracle = new TextureRegion(cancel, cancelPacked.getRegionX(),
                    cancelPacked.getRegionY(), cancelPacked.getRegionWidth(), cancelPacked.getRegionHeight());
            Matrix4 previous = new Matrix4(batch.getProjectionMatrix());
            try {
                for (int[] size : new int[][]{{800,600},{1194,552}}) {
                    int w=size[0], h=size[1];
                    layout.update(w,h,0,0,0,0);
                    batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,w,h));
                    FrameBuffer fb = new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false);
                    try {
                        for (int count=0; count<=4; count++) {
                            fb.begin(); clear(); batch.begin(); batch.setColor(1,1,1,1);
                            for (int i=0; i<count; i++) draw.invoke(app,i,0);
                            batch.end(); byte[] actual=ScreenUtils.getFrameBufferPixels(0,0,w,h,true); fb.end();
                            byte[] oracle = renderLeaves(fb,batch,layout,portraitOracle,cancelOracle,w,h,count,0);
                            evidence("source-selected-"+w+"-"+count+"-actual.png",actual,w,h);
                            evidence("source-selected-"+w+"-"+count+"-expected.png",oracle,w,h);
                            assertArrayEquals("source registration and opacity for " + count,oracle,actual);
                            for (int i=0;i<count;i++) {
                                Rectangle slot=layout.opponentSlots[i]; int painted=0;
                                for (int y=(int)slot.y;y<slot.y+slot.height;y++)
                                    for (int x=(int)slot.x;x<slot.x+slot.width;x++)
                                        if ((actual[((h-1-y)*w+x)*4+3]&255)!=0) painted++;
                                assertTrue("occupied slot must contain source pixels "+i,painted>0);
                            }
                            if (count > 0) {
                                byte[] shifted = renderLeaves(fb,batch,layout,portraitOracle,cancelOracle,w,h,count,1);
                                byte[] opaque = renderLeaves(fb,batch,layout,portraitOracle,cancelOracle,w,h,count,2);
                                evidence("source-selected-"+w+"-"+count+"-shifted.png",shifted,w,h);
                                evidence("source-selected-"+w+"-"+count+"-opaque.png",opaque,w,h);
                                for (int i=0;i<count;i++) {
                                    Rectangle slot=layout.opponentSlots[i];
                                    assertTrue("portrait shift must differ in slot "+i,
                                            differsIn(slot,actual,shifted,w,h));
                                    assertTrue("wrong cancel alpha must differ in slot "+i,
                                            differsIn(slot,actual,opaque,w,h));
                                }
                            }
                        }
                    } finally { fb.dispose(); }
                }
            } finally { batch.setProjectionMatrix(previous); portrait.dispose(); cancel.dispose(); }
        });
    }

    private static byte[] renderLeaves(FrameBuffer fb, SpriteBatch batch, SetupScreenLayout layout,
                                       TextureRegion portrait, TextureRegion cancel, int w, int h, int count, int wrong) {
        float[] px={-2.3f,-.75f,-2.3f,-1.85f}, py={-3f,-3f,-3.5f,-3.5f};
        float[] cx={48.25f,49.25f,47.25f,48.25f};
        fb.begin(); clear(); batch.begin(); batch.setColor(1,1,1,1);
        for (int i=0;i<count;i++) {
            Rectangle slot=layout.opponentSlots[i];
            float scale=Math.min(slot.width/70f,slot.height/75f);
            float x=slot.x+(slot.width-70f*scale)/2f;
            float y=slot.y+(slot.height-75f*scale)/2f;
            float targetWidth=70f*scale, targetHeight=75f*scale;
            float fit=Math.min(targetWidth/portrait.getRegionWidth(),targetHeight/portrait.getRegionHeight());
            float drawWidth=portrait.getRegionWidth()*fit, drawHeight=portrait.getRegionHeight()*fit;
            batch.draw(portrait,x+(targetWidth-drawWidth)/2f+(wrong==1?4f*scale:0f),
                    y+(targetHeight-drawHeight)/2f,drawWidth,drawHeight);
            float wellX=x-px[i]*scale, wellY=y+(py[i]+5f)*scale;
            batch.setColor(1,1,1,wrong==2?1f:(i==0?166f:143f)/256f);
            batch.draw(cancel,wellX+cx[i]*scale,wellY+(70f-.35f-17.1f)*scale,
                    17.3f*scale,17.1f*scale);
            batch.setColor(1,1,1,1);
        }
        batch.end(); byte[] result=ScreenUtils.getFrameBufferPixels(0,0,w,h,true); fb.end();
        return result;
    }

    private static boolean differsIn(Rectangle slot, byte[] actual, byte[] wrong, int width, int height) {
        for (int y=(int)slot.y; y<slot.y+slot.height; y++)
            for (int x=(int)slot.x; x<slot.x+slot.width; x++) {
                int offset=((height-1-y)*width+x)*4;
                for (int c=0;c<4;c++) if (actual[offset+c]!=wrong[offset+c]) return true;
            }
        return false;
    }

    private static void evidence(String name, byte[] pixels, int width, int height) {
        File directory=InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory); assertTrue(directory.exists() || directory.mkdirs());
        Pixmap image=new Pixmap(width,height,Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(pixels); image.getPixels().position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory,name).getAbsolutePath()),image);
        } finally { image.dispose(); }
    }

    private static Texture decodeSource(int shape, boolean vector, TextureRegion packed) throws Exception {
        byte[] bytes;
        try (InputStream in=new GZIPInputStream(Gdx.files.internal(
                "original/runtime/shapes/"+shape+".svgz").read());
             ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int n;
            while ((n=in.read(buffer))!=-1) out.write(buffer,0,n);
            bytes=out.toByteArray();
        }
        String svgText=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        Bitmap bitmap;
        if (vector) {
            SVG svg=SVG.getFromString(svgText);
            bitmap=Bitmap.createBitmap((int)Math.ceil(svg.getDocumentWidth()),
                    (int)Math.ceil(svg.getDocumentHeight()),Bitmap.Config.ARGB_8888);
            svg.renderToCanvas(new Canvas(bitmap));
        } else {
            Matcher match=Pattern.compile("data:image/[^;]+;base64,([^\"]+)").matcher(svgText);
            assertTrue(match.find());
            byte[] encoded=Base64.decode(match.group(1),Base64.DEFAULT);
            bitmap=BitmapFactory.decodeByteArray(encoded,0,encoded.length);
        }
        assertNotNull(bitmap);
        Pixmap pixmap=new Pixmap(bitmap.getWidth(),bitmap.getHeight(),Pixmap.Format.RGBA8888);
        java.nio.ByteBuffer data=pixmap.getPixels();
        for(int y=0;y<bitmap.getHeight();y++) for(int x=0;x<bitmap.getWidth();x++) {
            int color=bitmap.getPixel(x,y);
            data.put((byte)(color>>>16)); data.put((byte)(color>>>8));
            data.put((byte)color); data.put((byte)(color>>>24));
        }
        data.position(0); bitmap.recycle();
        int width=pixmap.getWidth(), height=pixmap.getHeight();
        assertEquals(width,packed.getRegionWidth()); assertEquals(height,packed.getRegionHeight());
        int x=packed.getRegionX(), y=packed.getRegionY();
        Pixmap page=new Pixmap(packed.getTexture().getWidth(),packed.getTexture().getHeight(),Pixmap.Format.RGBA8888);
        try {
            page.setBlending(Pixmap.Blending.None); page.drawPixmap(pixmap,x,y);
            page.drawPixmap(pixmap,0,0,1,height,x-1,y,1,height);
            page.drawPixmap(pixmap,width-1,0,1,height,x+width,y,1,height);
            page.drawPixmap(pixmap,0,0,width,1,x,y-1,width,1);
            page.drawPixmap(pixmap,0,height-1,width,1,x,y+height,width,1);
            page.drawPixmap(pixmap,0,0,1,1,x-1,y-1,1,1);
            page.drawPixmap(pixmap,width-1,0,1,1,x+width,y-1,1,1);
            page.drawPixmap(pixmap,0,height-1,1,1,x-1,y+height,1,1);
            page.drawPixmap(pixmap,width-1,height-1,1,1,x+width,y+height,1,1);
            Texture result=new Texture(page);
            result.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
            return result;
        } finally { page.dispose(); pixmap.dispose(); }
    }

    private static void clear() {
        Gdx.gl.glClearColor(0,0,0,0); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }
}
