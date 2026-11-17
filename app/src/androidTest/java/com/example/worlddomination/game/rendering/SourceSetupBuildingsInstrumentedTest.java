package com.example.worlddomination.game.rendering;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.Base64;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class SourceSetupBuildingsInstrumentedTest {
    @Test public void earlyBuildingsMatchOriginalFramesAtBothSizes() throws Exception {
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
            Method draw = WorldDominationGame.class.getDeclaredMethod("drawSourceSetupBuilding",Rectangle.class,int.class,boolean.class);
            draw.setAccessible(true);
            Texture[][] oracle = new Texture[5][2];
            Texture[][] frameOnly = new Texture[5][2];
            try {
                assertTrue("first source stage",published.await(60,TimeUnit.SECONDS));
                assets.update();
                assertTrue("setup atlas uploaded",assets.areSetupPortraitsLoaded());
                assertFalse("later atlas is still blocked",assets.isLoaded());
                int[][] ids={{5844,5845},{5836,5837},{5840,5841},{5831,5833},{5848,5849}};
                for(int i=0;i<5;i++) for(int s=0;s<2;s++) {
                    assertNotNull("ready shape "+ids[i][s],assets.getShape(ids[i][s]));
                    assertEquals("source x origin",s==0?6f:-3f,assets.getShapeOriginX(ids[i][s]),0f);
                    assertEquals("source y origin",s==0?(i==4?6.5f:6f):-4f,assets.getShapeOriginY(ids[i][s]),0f);
                }
                for (int i=0;i<5;i++) for (int state=0;state<2;state++) {
                    oracle[i][state]=sourceTexture(ids[i][state],true,assets.getShape(ids[i][state]));
                    frameOnly[i][state]=sourceTexture(ids[i][state],false,assets.getShape(ids[i][state]));
                }
                assetField.set(app,assets);
                for(int[] size:new int[][]{{800,600},{1194,552}}) {
                    int w=size[0],h=size[1];
                    FrameBuffer fb=new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false);
                    try {
                        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,w,h));
                        fb.begin();clear();batch.begin();batch.setColor(.2f,.4f,.6f,.8f);
                        float callerColor=batch.getPackedColor();
                        for(int i=0;i<5;i++)for(int state=0;state<2;state++) {
                            Rectangle box=box(i,state,w,h);
                            draw.invoke(app,box,i,state==1);
                            assertEquals("caller color restored",callerColor,batch.getPackedColor(),0f);
                        }
                        batch.end();byte[] actual=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);fb.end();
                        fb.begin();clear();batch.begin();batch.setColor(1,1,1,1);
                        for(int i=0;i<5;i++)for(int state=0;state<2;state++) {
                            Rectangle box=box(i,state,w,h);
                            TextureRegion packed=assets.getShape(ids[i][state]);
                            TextureRegion shape=new TextureRegion(oracle[i][state], packed.getRegionX(),
                                    packed.getRegionY(), packed.getRegionWidth(), packed.getRegionHeight());
                            float sx=state==0?6f:-3f,sy=state==0?(i==4?6.5f:6f):-4f;
                            float scale=Math.min(box.width/53f,box.height/55.5f);
                            batch.draw(shape,box.x+(box.width-53f*scale)/2f+(sx+3f)*scale,
                                    box.y+(box.height+55.5f*scale)/2f-(sy+(i==4?-.5f:0f)+4.5f+shape.getRegionHeight())*scale,
                                    shape.getRegionWidth()*scale,shape.getRegionHeight()*scale);
                        }
                        batch.end();byte[] expected=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);fb.end();
                        evidence("source-setup-buildings-"+w+"x"+h+"-actual.png",actual,w,h);
                        evidence("source-setup-buildings-"+w+"x"+h+"-expected.png",expected,w,h);
                        assertArrayEquals("literal source registration",expected,actual);
                        for(int i=0;i<5;i++)for(int state=0;state<2;state++) {
                            Rectangle b=box(i,state,w,h);int painted=0;
                            for(int y=(int)b.y;y<b.y+b.height;y++)for(int x=(int)b.x;x<b.x+b.width;x++)
                                if((actual[((h-1-y)*w+x)*4+3]&255)!=0)painted++;
                            assertTrue("nonempty building "+i+" state "+state,painted>0);
                        }
                        // The frame-only negative must differ for every building and state.
                        fb.begin();clear();batch.begin();batch.setColor(1,1,1,1);
                        for(int i=0;i<5;i++)for(int state=0;state<2;state++) {
                            Rectangle b=box(i,state,w,h);
                            float scale=Math.min(b.width/53f,b.height/55.5f);
                            float ox=state==0?6f:-3f;
                            float oy=(state==0?(i==4?6.5f:6f):-4f)+(i==4?-.5f:0f);
                            TextureRegion packed=assets.getShape(ids[i][state]);
                            TextureRegion t=new TextureRegion(frameOnly[i][state], packed.getRegionX(),
                                    packed.getRegionY(), packed.getRegionWidth(), packed.getRegionHeight());
                            batch.draw(t,b.x+(b.width-53f*scale)/2f+(ox+3f)*scale,
                                    b.y+(b.height+55.5f*scale)/2f-(oy+4.5f+t.getRegionHeight())*scale,
                                    t.getRegionWidth()*scale,t.getRegionHeight()*scale);
                        }
                        batch.end();byte[] wrong=ScreenUtils.getFrameBufferPixels(0,0,w,h,true);fb.end();
                        evidence("source-setup-buildings-"+w+"x"+h+"-wrong.png",wrong,w,h);
                        for(int i=0;i<5;i++)for(int state=0;state<2;state++) {
                            Rectangle b=box(i,state,w,h);int differences=0;
                            for(int y=(int)b.y;y<b.y+b.height;y++)for(int x=(int)b.x;x<b.x+b.width;x++) {
                                int k=((h-1-y)*w+x)*4;
                                if(actual[k]!=wrong[k]||actual[k+1]!=wrong[k+1]
                                        ||actual[k+2]!=wrong[k+2]||actual[k+3]!=wrong[k+3])differences++;
                            }
                            assertTrue("icon contribution "+i+" state "+state,differences>0);
                        }
                        assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                    } finally {fb.dispose();}
                }
            } finally {
                assetField.set(app,previous);batch.setProjectionMatrix(oldProjection);batch.setPackedColor(oldColor);
                release.countDown();assets.dispose();
                for (int i=0;i<5;i++) for (int state=0;state<2;state++) {
                    if (oracle[i][state]!=null) oracle[i][state].dispose();
                    if (frameOnly[i][state]!=null) frameOnly[i][state].dispose();
                }
            }
        });
    }
    // Independently decode both SVG image patterns and compose their authored
    // transforms and rectangular fill paths; never use the production atlas.
    private static Texture sourceTexture(int id,boolean includeIcon,TextureRegion packed) throws Exception {
        String path="original/runtime/shapes/"+id+".svgz";
        String xml;
        try(InputStream raw=Gdx.files.internal(path).read(); GZIPInputStream zip=new GZIPInputStream(raw);
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[8192];int n;while((n=zip.read(bytes))!=-1)out.write(bytes,0,n);
            xml=new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
        Document document=DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        NodeList groups=document.getElementsByTagName("g");
        NodeList patterns=document.getElementsByTagName("pattern");
        NodeList paths=document.getElementsByTagName("path");
        assertEquals(1,groups.getLength());assertEquals(2,patterns.getLength());assertEquals(2,paths.getLength());
        Element group=(Element)groups.item(0), backgroundPattern=(Element)patterns.item(0);
        Element iconPattern=(Element)patterns.item(1), iconPath=(Element)paths.item(1);
        float[] groupMatrix=matrix(group.getAttribute("transform"));
        float[] patternMatrix=matrix(iconPattern.getAttribute("patternTransform"));
        assertEquals(1f,groupMatrix[0],0f);assertEquals(1f,groupMatrix[3],0f);
        assertEquals(0f,groupMatrix[1],0f);assertEquals(0f,groupMatrix[2],0f);
        assertEquals(0f,patternMatrix[1],0f);assertEquals(0f,patternMatrix[2],0f);
        assertTrue(iconPath.getAttribute("style").contains("#"+iconPattern.getAttribute("id")));
        Bitmap bg=decodeEmbedded(backgroundPattern), icon=decodeEmbedded(iconPattern);
        float[] clip=rectanglePath(iconPath.getAttribute("d"));
        Bitmap merged=Bitmap.createBitmap(bg.getWidth(),bg.getHeight(),Bitmap.Config.ARGB_8888);
        Pixmap pixmap=null, page=null;
        try {
            Canvas canvas=new Canvas(merged);canvas.drawBitmap(bg,0,0,null);
            if(includeIcon) {
                float gx=groupMatrix[4],gy=groupMatrix[5];
                canvas.save();canvas.clipRect(clip[0]+gx,clip[1]+gy,clip[2]+gx,clip[3]+gy);
                float x=patternMatrix[4]+gx,y=patternMatrix[5]+gy;
                canvas.drawBitmap(icon,null,new RectF(x,y,x+icon.getWidth()*patternMatrix[0],
                        y+icon.getHeight()*patternMatrix[3]),null);
                canvas.restore();
            }
            int width=merged.getWidth(),height=merged.getHeight();int[] pixels=new int[width*height];
            merged.getPixels(pixels,0,width,0,0,width,height);
            pixmap=new Pixmap(width,height,Pixmap.Format.RGBA8888);ByteBuffer buffer=pixmap.getPixels();
            for(int argb:pixels){buffer.put((byte)(argb>>>16));buffer.put((byte)(argb>>>8));buffer.put((byte)argb);buffer.put((byte)(argb>>>24));}
            buffer.position(0);
            assertEquals(width,packed.getRegionWidth());assertEquals(height,packed.getRegionHeight());
            int x=packed.getRegionX(),y=packed.getRegionY();
            page=new Pixmap(packed.getTexture().getWidth(),packed.getTexture().getHeight(),Pixmap.Format.RGBA8888);
            page.setBlending(Pixmap.Blending.None);
            page.drawPixmap(pixmap,x,y);
            // PixmapPacker duplicates one edge pixel into the padding. Recreate
            // it from independent oracle pixels at the same atlas coordinates.
            page.drawPixmap(pixmap,0,0,1,height,x-1,y,1,height);
            page.drawPixmap(pixmap,width-1,0,1,height,x+width,y,1,height);
            page.drawPixmap(pixmap,0,0,width,1,x,y-1,width,1);
            page.drawPixmap(pixmap,0,height-1,width,1,x,y+height,width,1);
            page.drawPixmap(pixmap,0,0,1,1,x-1,y-1,1,1);
            page.drawPixmap(pixmap,width-1,0,1,1,x+width,y-1,1,1);
            page.drawPixmap(pixmap,0,height-1,1,1,x-1,y+height,1,1);
            page.drawPixmap(pixmap,width-1,height-1,1,1,x+width,y+height,1,1);
            Texture result=new Texture(page);
            result.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);return result;
        } finally {if(page!=null)page.dispose();if(pixmap!=null)pixmap.dispose();merged.recycle();bg.recycle();icon.recycle();}
    }
    private static Bitmap decodeEmbedded(Element pattern) {
        NodeList images=pattern.getElementsByTagName("image");assertEquals(1,images.getLength());
        Element image=(Element)images.item(0);String href=image.getAttribute("xlink:href");
        String prefix="data:image/webp;base64,";assertTrue(href.startsWith(prefix));
        byte[] data=Base64.decode(href.substring(prefix.length()),Base64.NO_WRAP);
        Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length);assertNotNull(bitmap);
        assertEquals(Integer.parseInt(image.getAttribute("width")),bitmap.getWidth());
        assertEquals(Integer.parseInt(image.getAttribute("height")),bitmap.getHeight());
        return bitmap;
    }
    private static float[] matrix(String text) {
        assertTrue(text.startsWith("matrix(") && text.endsWith(")"));
        String[] values=text.substring(7,text.length()-1).split("[,\\s]+");
        assertEquals(6,values.length);float[] result=new float[6];
        for(int i=0;i<6;i++) result[i]=Float.parseFloat(values[i]);
        return result;
    }
    private static float[] rectanglePath(String text) {
        // All ten source paths are five axis-aligned points in M/L notation.
        assertTrue(text.matches("[ML0-9.\\s-]+"));
        Matcher m=Pattern.compile("-?(?:\\d+\\.?\\d*|\\.\\d+)").matcher(text);
        float[] points=new float[10];int count=0;
        while(m.find()){assertTrue(count<points.length);points[count++]=Float.parseFloat(m.group());}
        assertEquals(10,count);assertEquals(points[0],points[8],0f);assertEquals(points[1],points[9],0f);
        float left=Float.POSITIVE_INFINITY,top=Float.POSITIVE_INFINITY,right=Float.NEGATIVE_INFINITY,bottom=Float.NEGATIVE_INFINITY;
        for(int i=0;i<10;i+=2){left=Math.min(left,points[i]);right=Math.max(right,points[i]);top=Math.min(top,points[i+1]);bottom=Math.max(bottom,points[i+1]);}
        for(int i=0;i<8;i+=2){assertTrue(points[i]==left||points[i]==right);assertTrue(points[i+1]==top||points[i+1]==bottom);}
        assertTrue(right>left&&bottom>top);return new float[]{left,top,right,bottom};
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
