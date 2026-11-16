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
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import static org.junit.Assert.*;

/** Original rocket self and city impact artwork at its physical root position. */
@RunWith(AndroidJUnit4.class)
public final class SourceRocketImpactRenderingInstrumentedTest {
    @Test public void capturedWorldInterceptionProjectsFromPreMovePosition() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            Method initial=SourceSiloRocketClockInstrumentedTest.class.getDeclaredMethod("initial",boolean.class);
            initial.setAccessible(true);GameState game=(GameState)initial.invoke(null,true);
            field(WorldDominationGame.class,"game").set(app,game);
            BattlefieldCityRenderer cities=(BattlefieldCityRenderer)field(WorldDominationGame.class,"cityRenderer").get(app);
            cities.reset(game);((SourceBattleRecordClock)field(WorldDominationGame.class,"battleRecordClock").get(app)).reset();
            Method step=WorldDominationGame.class.getDeclaredMethod("fixedUpdate",float.class);step.setAccessible(true);
            game.selectAction(GameState.Action.PREPARED_ATTACK);assertTrue(game.configurePreparedCarrier("rocket1"));
            assertTrue(game.acceptSelectedAction());game.selectAction(GameState.Action.RESOURCES);assertTrue(game.acceptSelectedAction());
            call(app,"confirmTurnAndPreparePlayback");
            for(int i=0;i<1200&&cities.isBattleActive();i++)step.invoke(app,1f/24f);
            assertFalse(cities.isBattleActive());
            game.selectAction(GameState.Action.PREPARED_ATTACK);assertTrue(game.configurePreparedLaunch(1,2,"bioload1"));
            assertTrue(game.acceptSelectedAction());game.selectAction(GameState.Action.RESOURCES);assertTrue(game.acceptSelectedAction());
            call(app,"confirmTurnAndPreparePlayback");
            @SuppressWarnings("unchecked") List<SourceRocketDisplayDepths> depths=(List<SourceRocketDisplayDepths>)
                    field(WorldDominationGame.class,"sourceRocketBindings").get(app);
            SourceRocketDisplayDepths.Binding binding=null;
            for(SourceRocketDisplayDepths d:depths) if(d.binding().animation.targetPlayerId()==1)binding=d.binding();
            assertNotNull(binding);
            double canonical=game.createPresentationRandomCopy().nextDouble();
            for(int tick=1;tick<=297;tick++)step.invoke(app,1f/24f);
            assertTrue(binding.animation.getSourcePlayback().isSelfImpact());
            assertEquals(1,binding.getExplosionCount());assertEquals(0,binding.getExplosionFrameIndex(0));
            // Source capture tick 296 position, attached before tick 297's final movement.
            assertEquals(243.1489951317337d,binding.getExplosionX(0),1e-6);
            assertEquals(.4153796688429736d,binding.getExplosionY(0),1e-6);
            ReferenceRandom random=(ReferenceRandom)field(WorldDominationGame.class,"battlePresentationRandom").get(app);
            compare(app,binding,random,game,canonical,"world");
        });
    }

    @Test public void reverseDirectionUsesSameSourceCrop() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            FlashBattleRoutes.RocketStrike route=FlashBattleRoutes.createRocketStrike(1,0,2,1,true);
            ReferenceRandom random=new ReferenceRandom(0L);
            FlashRocketAnimation animation=FlashRocketAnimation.forSourceOrdinary(route,"explosion003",
                    OriginalAudio.Cue.EXPLOSION_SMALL3,random,5);
            SourceRocketDisplayDepths depths=new SourceRocketDisplayDepths(new BattlefieldDisplayState(),
                    animation,0,(FlashAnimationTimeline)null,new SourceStoppedClipQueue());
            for(int i=0;i<1000&&!animation.getSourcePlayback().isSelfImpact();i++) {
                animation.tickSourceFrame(true);depths.afterUnitFrame();
            }
            assertTrue("reverse source Unit reached self impact",animation.getSourcePlayback().isSelfImpact());
            assertEquals(1,depths.binding().getExplosionCount());
            compare(app,depths.binding(),random,null,0d,"reverse-synthetic-route");
        });
    }

    @Test public void successfulBioloadUsesRegisteredTargetArtwork() throws Exception {
        verifyPayload("bioload1",7,1528,-29f,-38f);
    }
    @Test public void successfulWarheadUsesRegisteredTargetArtwork() throws Exception {
        verifyPayload("warhead1",5,1760,-30f,-42f);
    }
    private static void verifyPayload(String payload,int sourceFrame,int shape,float originX,float originY)
            throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            Method initial=SourceSiloRocketClockInstrumentedTest.class.getDeclaredMethod("initial",boolean.class);
            initial.setAccessible(true);GameState game=(GameState)initial.invoke(null,false);
            if(game.getHuman().getWeapons().get(payload).inStock<=0) {
                while(game.getPendingHumanDiplomacyProposal()!=null)
                    assertTrue(game.respondToDiplomacyProposal(false));
                game.selectAction(GameState.Action.PRODUCTION);
                assertTrue(game.configureProduction(payload.startsWith("warhead")?"warhead":"bioload",100));
                assertTrue(game.acceptSelectedAction());game.selectAction(GameState.Action.RESOURCES);
                assertTrue(game.acceptSelectedAction());game.confirmTurn();
            }
            assertTrue("legally produced payload",game.getHuman().getWeapons().get(payload).inStock>0);
            field(WorldDominationGame.class,"game").set(app,game);
            BattlefieldCityRenderer cities=(BattlefieldCityRenderer)field(WorldDominationGame.class,"cityRenderer").get(app);
            cities.reset(game);((SourceBattleRecordClock)field(WorldDominationGame.class,"battleRecordClock").get(app)).reset();
            Method step=WorldDominationGame.class.getDeclaredMethod("fixedUpdate",float.class);step.setAccessible(true);
            while(game.getPendingHumanDiplomacyProposal()!=null)assertTrue(game.respondToDiplomacyProposal(false));
            game.selectAction(GameState.Action.PREPARED_ATTACK);assertTrue(game.configurePreparedCarrier("rocket1"));
            assertTrue(game.acceptSelectedAction());game.selectAction(GameState.Action.RESOURCES);assertTrue(game.acceptSelectedAction());
            call(app,"confirmTurnAndPreparePlayback");
            for(int i=0;i<1200&&cities.isBattleActive();i++)step.invoke(app,1f/24f);
            assertFalse(cities.isBattleActive());
            while(game.getPendingHumanDiplomacyProposal()!=null)assertTrue(game.respondToDiplomacyProposal(false));
            game.selectAction(GameState.Action.PREPARED_ATTACK);assertTrue(game.configurePreparedLaunch(1,2,payload));
            assertTrue(game.acceptSelectedAction());game.selectAction(GameState.Action.RESOURCES);assertTrue(game.acceptSelectedAction());
            call(app,"confirmTurnAndPreparePlayback");
            @SuppressWarnings("unchecked") List<SourceRocketDisplayDepths> depths=(List<SourceRocketDisplayDepths>)
                    field(WorldDominationGame.class,"sourceRocketBindings").get(app);
            SourceRocketDisplayDepths.Binding binding=null;
            for(SourceRocketDisplayDepths d:depths)if(d.binding().animation.targetPlayerId()==1)binding=d.binding();
            assertNotNull(binding);
            assertEquals("explosion_"+payload.substring(0,payload.length()-1)+"_1",binding.animation.getImpactLinkage());
            assertEquals(OriginalAudio.Cue.EXPLOSION_SMALL3,binding.animation.getImpactCue());
            double canonical=game.createPresentationRandomCopy().nextDouble();
            for(int tick=0;tick<800&&binding.getExplosionCount()==0;tick++)step.invoke(app,1f/24f);
            assertEquals("successful city impact",1,binding.getExplosionCount());
            assertFalse(binding.animation.getSourcePlayback().isSelfImpact());
            assertEquals("effect starts at frame1",0,binding.getExplosionFrameIndex(0));
            // Source capture city1_2 uses decimal doubles, not widened float route storage.
            assertEquals(212.8d,binding.getExplosionX(0),0d);
            assertEquals(166.75d,binding.getExplosionY(0),0d);
            for(int i=1;i<sourceFrame;i++)step.invoke(app,1f/24f);
            assertEquals(sourceFrame-1,binding.getExplosionFrameIndex(0));
            ReferenceRandom random=(ReferenceRandom)field(WorldDominationGame.class,"battlePresentationRandom").get(app);
            payloadPixels(app,binding,random,game,canonical,payload,sourceFrame,shape,originX,originY);
        });
    }

    private static void payloadPixels(WorldDominationGame app,SourceRocketDisplayDepths.Binding binding,
            ReferenceRandom random,GameState game,double canonical,String payload,int sourceFrame,
            int shape,float ox,float oy)throws Exception {
        OriginalFlashAnimationAssets assets=(OriginalFlashAnimationAssets)field(WorldDominationGame.class,"flashAnimationAssets").get(app);
        FlashBattleUnitRenderer renderer=(FlashBattleUnitRenderer)field(WorldDominationGame.class,"battleUnitRenderer").get(app);
        FlashAnimationTimeline.Frame frame=assets.getTimeline(binding.animation.getImpactLinkage()).getFrame(sourceFrame-1);
        assertNotNull(frame);assertEquals(1,frame.layers.length);assertEquals(shape,frame.layers[0].shapeId);
        assertEquals(ox,assets.getShapeOriginX(shape),0f);assertEquals(oy,assets.getShapeOriginY(shape),0f);
        assertEquals(1f,frame.layers[0].a,0f);assertEquals(1f,frame.layers[0].d,0f);
        assertEquals(0f,frame.layers[0].b,0f);assertEquals(0f,frame.layers[0].c,0f);
        float tx=payload.equals("bioload1")?-1f:0f,ty=payload.equals("bioload1")?2f:0f;
        assertEquals(tx,frame.layers[0].tx,0f);assertEquals(ty,frame.layers[0].ty,0f);
        assertEquals(1f,frame.layers[0].alpha,0f);
        double before=random.copy().nextDouble();SpriteBatch batch=new SpriteBatch();ShaderProgram caller=SpriteBatch.createDefaultShader();
        try {
            assertTrue(caller.getLog(),caller.isCompiled());
            for(int[] size:new int[][]{{800,600},{1194,552}}) {
                int w=size[0],h=size[1];Rectangle bounds=new Rectangle(0,0,w,h);
                FrameBuffer buffer=new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false,true);
                batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,w,h));
                try {
                    byte[] actual=render(buffer,batch,w,h,()->{
                        batch.setShader(caller);batch.setColor(.31f,.47f,.69f,.61f);
                        float color=batch.getPackedColor();Matrix4 matrix=new Matrix4(batch.getTransformMatrix());
                        renderer.drawSourceRocketRole(batch,bounds,binding,BattlefieldDisplayState.Kind.EFFECT,-1);
                        assertSame(caller,batch.getShader());assertEquals(color,batch.getPackedColor(),0f);
                        assertArrayEquals(matrix.val,batch.getTransformMatrix().val,0f);
                    });
                    byte[] expected=render(buffer,batch,w,h,()->payloadLeaf(batch,assets,binding,w,h,shape,tx,ty,ox,oy));
                    byte[] wrong=render(buffer,batch,w,h,()->payloadLeaf(batch,assets,binding,w,h,shape,tx,ty,0f,0f));
                    String prefix="source-rocket-city-impact-"+payload+"-"+w+"x"+h+"-";
                    evidence(prefix+"actual.png",actual,w,h);evidence(prefix+"expected.png",expected,w,h);
                    evidence(prefix+"missing-registration.png",wrong,w,h);
                    int painted=0,diff=0;for(int i=0;i<actual.length;i+=4){boolean changed=false;
                        for(int c=0;c<4;c++){assertEquals(prefix+"pixel "+i/4+" channel "+c,
                                expected[i+c]&255,actual[i+c]&255);changed|=expected[i+c]!=wrong[i+c];}
                        if((expected[i+3]&255)>0)painted++;if(changed)diff++;}
                    assertTrue("registered effect paints",painted>0);assertTrue("missing registration differs",diff>0);
                    assertEquals(before,random.copy().nextDouble(),0d);
                    assertEquals(canonical,game.createPresentationRandomCopy().nextDouble(),0d);
                    assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                }finally{buffer.dispose();}
            }
        }finally{caller.dispose();batch.dispose();}
    }
    private static void payloadLeaf(SpriteBatch batch,OriginalFlashAnimationAssets assets,
            SourceRocketDisplayDepths.Binding binding,int w,int h,int shape,float tx,float ty,float ox,float oy) {
        TextureRegion r=assets.getShape(shape);assertNotNull(r);float x=(float)binding.getExplosionX(0),y=(float)binding.getExplosionY(0);
        FlashBattleRoutes.RocketStrike route=binding.animation.getRoute();
        float scale=Math.min(w/800f,h/600f),margin=(w-800f*scale)/2f;
        float from=shift(route.attackerPlayerId,margin),to=shift(route.targetPlayerId,margin);
        float base=margin+x*scale+from,target=margin+x*scale+to,crop=target-base;
        float[] xs={ox,ox,ox+r.getRegionWidth(),ox+r.getRegionWidth()};
        float[] ys={oy+r.getRegionHeight(),oy,oy,oy+r.getRegionHeight()};
        float[] us={r.getU(),r.getU(),r.getU2(),r.getU2()},vs={r.getV2(),r.getV(),r.getV(),r.getV2()};
        float[] v=new float[20];batch.setShader(null);batch.setColor(1,1,1,1);
        for(int i=0;i<4;i++){
            v[i*5]=margin+(x+tx+xs[i])*scale+from+crop;
            v[i*5+1]=h-(h-600f*scale)/2f-(y+ty+ys[i])*scale;
            v[i*5+2]=batch.getPackedColor();v[i*5+3]=us[i];v[i*5+4]=vs[i];
        }batch.draw(r.getTexture(),v,0,20);
    }

    private static void compare(WorldDominationGame app,SourceRocketDisplayDepths.Binding binding,
            ReferenceRandom random,GameState game,double canonical,String label)throws Exception {
        OriginalFlashAnimationAssets assets=(OriginalFlashAnimationAssets)field(WorldDominationGame.class,"flashAnimationAssets").get(app);
        FlashBattleUnitRenderer renderer=(FlashBattleUnitRenderer)field(WorldDominationGame.class,"battleUnitRenderer").get(app);
        assertTrue(assets.isLoaded());
        FlashAnimationTimeline.Frame first=assets.getTimeline("explosion003").getFrame(0);
        assertEquals(1,first.layers.length);assertEquals(1810,first.layers[0].shapeId);
        assertEquals(-54f,assets.getShapeOriginX(1810),0f);assertEquals(-47f,assets.getShapeOriginY(1810),0f);
        double before=random.copy().nextDouble();
        SpriteBatch batch=new SpriteBatch();ShaderProgram caller=SpriteBatch.createDefaultShader();
        try {
            assertTrue(caller.getLog(),caller.isCompiled());
            for(int[] size:new int[][]{{800,600},{1194,552}}) {
                int w=size[0],h=size[1];Rectangle bounds=new Rectangle(0,0,w,h);
                FrameBuffer buffer=new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false,true);
                batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,w,h));
                try {
                    byte[] actual=render(buffer,batch,w,h,()->{
                        batch.setShader(caller);batch.setColor(.31f,.47f,.69f,.61f);
                        float color=batch.getPackedColor();Matrix4 matrix=new Matrix4(batch.getTransformMatrix());
                        renderer.drawSourceRocketRole(batch,bounds,binding,BattlefieldDisplayState.Kind.EFFECT,-1);
                        assertSame(caller,batch.getShader());assertEquals(color,batch.getPackedColor(),0f);
                        assertArrayEquals(matrix.val,batch.getTransformMatrix().val,0f);
                    });
                    byte[] expected=render(buffer,batch,w,h,()->leaf(batch,assets,binding,w,h,false));
                    byte[] wrong=render(buffer,batch,w,h,()->leaf(batch,assets,binding,w,h,true));
                    String prefix="source-rocket-self-impact-"+label+"-"+w+"x"+h+"-";
                    evidence(prefix+"actual.png",actual,w,h);evidence(prefix+"expected.png",expected,w,h);
                    evidence(prefix+"target-only.png",wrong,w,h);
                    int painted=0,diff=0;
                    for(int i=0;i<actual.length;i+=4){boolean changed=false;for(int c=0;c<4;c++){
                        assertEquals(prefix+"pixel "+i/4+" channel "+c,expected[i+c]&255,actual[i+c]&255);
                        changed|=expected[i+c]!=wrong[i+c];}
                        if((expected[i+3]&255)>0)painted++;if(changed)diff++;}
                    assertTrue("source effect paints",painted>0);
                    if(w==800)assertEquals(0,diff);else assertTrue("target-only negative",diff>0);
                    assertEquals("render preserves private RNG",before,random.copy().nextDouble(),0d);
                    if(game!=null)assertEquals("canonical RNG",canonical,game.createPresentationRandomCopy().nextDouble(),0d);
                    assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                }finally{buffer.dispose();}
            }
        }finally{caller.dispose();batch.dispose();}
    }
    /** Literal source frame-one shape, registration and 800x600 projection/crop. */
    private static void leaf(SpriteBatch batch,OriginalFlashAnimationAssets assets,
            SourceRocketDisplayDepths.Binding binding,int w,int h,boolean targetOnly){
        TextureRegion r=assets.getShape(1810);assertNotNull(r);
        FlashBattleRoutes.RocketStrike route=binding.animation.getRoute();
        float x=(float)binding.getExplosionX(0),y=(float)binding.getExplosionY(0);
        float scale=Math.min(w/800f,h/600f),margin=(w-800f*scale)/2f;
        float from=shift(route.attackerPlayerId,margin),to=shift(route.targetPlayerId,margin);
        float progress=(x-route.launch.x)/(route.target.x-route.launch.x);
        progress=Math.max(0f,Math.min(1f,progress));
        float base=margin+x*scale+from;
        float blend=margin+x*scale+(from+(to-from)*progress);
        float offset=targetOnly ? to-from : blend-base;
        float ox=assets.getShapeOriginX(1810),oy=assets.getShapeOriginY(1810);
        float[] xs={ox,ox,ox+r.getRegionWidth(),ox+r.getRegionWidth()};
        float[] ys={oy+r.getRegionHeight(),oy,oy,oy+r.getRegionHeight()};
        float[] us={r.getU(),r.getU(),r.getU2(),r.getU2()},vs={r.getV2(),r.getV(),r.getV(),r.getV2()};
        float[] v=new float[20];batch.setShader(null);batch.setColor(1,1,1,1);
        for(int i=0;i<4;i++){
            v[i*5]=margin+(x+xs[i])*scale+from+offset;
            v[i*5+1]=h-(h-600f*scale)/2f-(y+ys[i])*scale;
            v[i*5+2]=batch.getPackedColor();v[i*5+3]=us[i];v[i*5+4]=vs[i];
        }batch.draw(r.getTexture(),v,0,20);
    }
    private static float shift(int p,float margin){return p==1||p==3?-margin:p==2||p==4?margin:0f;}
    private static byte[] render(FrameBuffer f,SpriteBatch b,int w,int h,Draw d)throws Exception{
        f.begin();try{Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_STENCIL_BUFFER_BIT);
            b.setTransformMatrix(new Matrix4().idt());b.setShader(null);b.setColor(1,1,1,1);b.begin();try{d.run();}finally{b.end();}
            return ScreenUtils.getFrameBufferPixels(0,0,w,h,true);}finally{f.end();}}
    private static void evidence(String n,byte[] rgba,int w,int h){File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());Pixmap p=new Pixmap(w,h,Pixmap.Format.RGBA8888);
        try{p.getPixels().put(rgba).position(0);PixmapIO.writePNG(Gdx.files.absolute(new File(dir,n).getAbsolutePath()),p,-1,false);}finally{p.dispose();}}
    private static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    private static void call(Object o,String n)throws Exception{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}
    private interface Draw{void run()throws Exception;}
}
