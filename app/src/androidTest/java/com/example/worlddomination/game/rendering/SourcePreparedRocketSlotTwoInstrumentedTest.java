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
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Seed-zero slot-two capture, with the reference's prep-view compatibility transfer. */
@RunWith(AndroidJUnit4.class)
public final class SourcePreparedRocketSlotTwoInstrumentedTest {
    private static final String VERT = "attribute vec4 a_position; attribute vec4 a_color;"
            + "attribute vec2 a_texCoord0; uniform mat4 u_projTrans; varying vec4 c;"
            + "varying vec2 uv; void main(){c=a_color;c.a*=255.0/254.0;"
            + "uv=a_texCoord0;gl_Position=u_projTrans*a_position;}";
    private static final String MASK = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "varying vec2 uv; uniform sampler2D u_texture;"
            + "void main(){if(texture2D(u_texture,uv).a<=0.0039)discard;gl_FragColor=vec4(1.0);}";
    private static final String TINT = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
            + "varying vec2 uv; varying vec4 c; uniform sampler2D u_texture;"
            + "void main(){vec4 s=texture2D(u_texture,uv);"
            + "gl_FragColor=clamp(s*vec4(0.0,0.5,0.0,0.75)+vec4(0.0,1.0,0.0,0.0),0.0,1.0)*c;}";

    @Test public void legalSlotTwoWorldMatchesAll366SourceFrames() throws Exception {
        List<Map<String,String>> rows=csv("source-silo-slot2-ticks.csv");
        List<Map<String,String>> smoke=csv("source-silo-slot2-smoke.csv");
        assertEquals(366,rows.size());assertEquals(990,smoke.size());
        Map<Integer,List<Map<String,String>>> byTick=new HashMap<>();
        for(Map<String,String> p:smoke)byTick.computeIfAbsent(integer(p,"tick"),k->new ArrayList<>()).add(p);
        SourceBattleCleanupInstrumentedTest.runInGame(app->{
            Scenario scenario=prepare(app); SourceRocketPlayback core=scenario.binding.animation.getSourcePlayback();
            assertEquals(.8887720676319878,scenario.privateRandom.copy().nextDouble(),0d);
            assertEquals(.007184567252270457,scenario.canonical,0d);
            for(int tick=0;tick<rows.size();tick++){
                if(tick>0)scenario.step.invoke(app,1f/24f);
                Map<String,String> r=rows.get(tick);String c="tick "+tick;
                assertEquals(tick,integer(r,"tick"));assertEquals(c,r.get("action"),core.getAction());
                assertEquals(c,r.get("status"),core.getUnitStatus());
                num(r,"x",core.getX(),c);num(r,"y",core.getY(),c);
                num(r,"previousX",core.getPreviousX(),c);num(r,"previousY",core.getPreviousY(),c);
                num(r,"xStep",core.getXStep(),c);num(r,"yStep",core.getYStep(),c);
                num(r,"ellipseAngle",core.getEllipseAngle(),c);num(r,"mcX",core.getRenderX(),c);num(r,"mcY",core.getRenderY(),c);
                num(r,"rotation",normalizedRotation(core.getRotation()),c);assertEquals(c,integer(r,"angle"),core.getRawAngle());
                assertEquals(c,integer(r,"mcFrame"),core.getSourceFrame());assertEquals(c,bool(r,"moving"),core.isActiveAndMoving());
                assertEquals(c,bool(r,"attached"),scenario.binding.body().isAttached());
                assertEquals(c,integer(r,"activeIndex")>=0,!core.isRemoved());
                assertEquals(c,integer(r,"attachedSmoke"),core.getAttachedTrailCount());
                assertEquals(c,decimal(r,"privateRngNext"),scenario.privateRandom.copy().nextDouble(),0d);
                assertEquals(c,decimal(r,"canonicalRngNext"),scenario.game.createPresentationRandomCopy().nextDouble(),0d);
                boolean[] attached=new boolean[core.getTrailSlotCount()];
                for(Map<String,String> p:byTick.getOrDefault(tick,Collections.emptyList())){
                    int slot=integer(p,"slot");attached[slot]=true;assertTrue(core.isTrailAttached(slot));
                    assertEquals(integer(p,"depth"),core.getTrailDepth(slot));
                    num(p,"x",core.getTrailX(slot),c);num(p,"y",core.getTrailY(slot),c);
                    num(p,"alpha",core.getTrailAlpha(slot),c);num(p,"sizePlus",core.getTrailSizePlus(slot),c);
                    num(p,"alphaMin",core.getTrailAlphaMin(slot),c);
                }
                for(int slot=0;slot<attached.length;slot++)assertEquals(c+" smoke "+slot,attached[slot],core.isTrailAttached(slot));
                boolean effect=bool(r,"effectAttached");
                if(scenario.binding.getExplosionCount()>0){
                    assertEquals(c,effect,scenario.binding.explosion(0).isAttached());
                    assertEquals(integer(r,"effectDepth"),scenario.binding.explosion(0).getDepth());
                    num(r,"effectX",scenario.binding.getExplosionX(0),c);num(r,"effectY",scenario.binding.getExplosionY(0),c);
                    assertEquals(integer(r,"effectFrame")-1,scenario.binding.getExplosionFrameIndex(0));
                }else assertFalse(c,effect);
                assertEquals(c,tick<365,scenario.cities.isBattleActive());
            }
        });
    }
    @Test public void movingRocketAndPhysicalSiloUseSlotTwoArtwork() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app->{
            Scenario s=prepare(app);for(int tick=1;tick<=197;tick++)s.step.invoke(app,1f/24f);
            assertEquals(442.25,s.binding.animation.getSourcePlayback().getRenderX(),0d);
            assertEquals(298.75,s.binding.animation.getSourcePlayback().getRenderY(),0d);
            pixels(app,s);
        });
    }
    private static final class Scenario {
        GameState game;BattlefieldCityRenderer cities;Method step;SourceRocketDisplayDepths.Binding binding;
        FlashActionBuildingAnimation silo;ReferenceRandom privateRandom;double canonical;
    }
    private static Scenario prepare(WorldDominationGame app)throws Exception {
        GameSetup setup=new GameSetup(0);setup.setDifficulty(1);assertTrue(setup.toggleOpponent(0));assertTrue(setup.toggleOpponent(1));
        GameState g=new GameState(setup);
        production(g,"soldier",50);production(g,"rockets",100);production(g,"bioload",100);
        field(WorldDominationGame.class,"game").set(app,g);
        BattlefieldCityRenderer cities=(BattlefieldCityRenderer)field(WorldDominationGame.class,"cityRenderer").get(app);
        cities.reset(g);((SourceBattleRecordClock)field(WorldDominationGame.class,"battleRecordClock").get(app)).reset();
        Method step=WorldDominationGame.class.getDeclaredMethod("fixedUpdate",float.class);step.setAccessible(true);
        decline(g);g.selectAction(GameState.Action.ATTACK);assertTrue(g.configureAttack(1,2,"soldier1"));assertTrue(g.acceptSelectedAction());
        g.selectAction(GameState.Action.PREPARED_ATTACK);assertTrue(g.configurePreparedCarrier("rocket1"));assertTrue(g.acceptSelectedAction());
        call(app,"confirmTurnAndPreparePlayback");
        @SuppressWarnings("unchecked") List<FlashActionBuildingAnimation> buildings=(List<FlashActionBuildingAnimation>)field(WorldDominationGame.class,"actionBuildingAnimations").get(app);
        FlashActionBuildingAnimation prep=null;for(FlashActionBuildingAnimation a:buildings)if(a.playerId==0&&a.slot==2&&"silo".equals(a.frameName))prep=a;
        assertNotNull("preparation physically occupies slot2",prep);
        assertEquals(432.25f,FlashBattleRoutes.actionBuildingPosition(0,2).x,0f);
        assertEquals(296.75f,FlashBattleRoutes.actionBuildingPosition(0,2).y,0f);
        for(int i=0;i<1200&&cities.isBattleActive();i++)step.invoke(app,1f/24f);assertFalse(cities.isBattleActive());
        decline(g);g.selectAction(GameState.Action.PREPARED_ATTACK);assertTrue(g.configurePreparedLaunch(1,2,"bioload1"));assertTrue(g.acceptSelectedAction());
        g.selectAction(GameState.Action.RESOURCES);assertTrue(g.acceptSelectedAction());call(app,"confirmTurnAndPreparePlayback");
        assertEquals(5,g.getTurn());
        GameState.CombatEvent event=null;
        for(GameState.CombatEvent candidate:g.getLastTurnCombatEvents())
            if(candidate.attackerPlayerId==0 && "rocket1".equals(candidate.weaponName))event=candidate;
        assertNotNull("source rocket event",event);assertEquals(5,event.battleStartNum);
        assertEquals(2,event.actionBuildingSlot);
        @SuppressWarnings("unchecked") List<SourceRocketDisplayDepths> depths=(List<SourceRocketDisplayDepths>)field(WorldDominationGame.class,"sourceRocketBindings").get(app);
        SourceRocketDisplayDepths.Binding binding=null;for(SourceRocketDisplayDepths d:depths)if(d.binding().animation.getRoute().attackerPlayerId==0 && d.binding().animation.targetPlayerId()==1)binding=d.binding();
        assertNotNull(binding);assertEquals("unit1",binding.getUnitName());assertEquals(5,binding.sourceParameter);
        assertEquals(22509,binding.body().getDepth());assertEquals(22510,binding.dot().getDepth());assertEquals(2,binding.animation.getRoute().actionBuildingSlot);
        assertEquals(442.25f,binding.animation.getRoute().launch.x,0f);assertEquals(300.75f,binding.animation.getRoute().launch.y,0f);
        FlashActionBuildingAnimation silo=null;for(FlashActionBuildingAnimation a:buildings)if(a.playerId==0&&a.slot==2&&"silo".equals(a.frameName))silo=a;
        assertNotNull("launch retains slot2 silo",silo);
        Scenario s=new Scenario();s.game=g;s.cities=cities;s.step=step;s.binding=binding;s.silo=silo;
        s.privateRandom=(ReferenceRandom)field(WorldDominationGame.class,"battlePresentationRandom").get(app);
        s.canonical=g.createPresentationRandomCopy().nextDouble();return s;
    }
    private static void production(GameState g,String category,int percent){decline(g);g.selectAction(GameState.Action.PRODUCTION);assertTrue(g.configureProduction(category,percent));assertTrue(g.acceptSelectedAction());g.selectAction(GameState.Action.RESOURCES);assertTrue(g.acceptSelectedAction());g.confirmTurn();}
    private static void decline(GameState g){while(g.getPendingHumanDiplomacyProposal()!=null)assertTrue(g.respondToDiplomacyProposal(false));}
    private static List<Map<String,String>> csv(String name)throws Exception{
        List<Map<String,String>> out=new ArrayList<>();try(BufferedReader r=new BufferedReader(new InputStreamReader(InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name),StandardCharsets.UTF_8))){
            String[] h=r.readLine().split(",",-1);for(String line;(line=r.readLine())!=null;){String[] v=line.split(",",-1);assertEquals(h.length,v.length);Map<String,String> row=new HashMap<>();for(int i=0;i<h.length;i++)row.put(h[i],v[i]);out.add(row);}}
        return out;
    }
    // Reference MovieClip getter normalizes its matrix rotation; Unit retains the assignment.
    private static double normalizedRotation(double value){value%=360d;if(value<-180d)value+=360d;if(value>180d)value-=360d;return value;}
    private static int integer(Map<String,String> r,String k){return Integer.parseInt(r.get(k));}
    private static double decimal(Map<String,String> r,String k){return Double.parseDouble(r.get(k));}
    private static boolean bool(Map<String,String> r,String k){return Boolean.parseBoolean(r.get(k));}
    private static void num(Map<String,String> r,String k,double x,String c){assertEquals(c+" "+k,decimal(r,k),x,1e-7);}
    private static Field field(Class<?> c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    private static void call(Object o,String n)throws Exception{Method m=o.getClass().getDeclaredMethod(n);m.setAccessible(true);m.invoke(o);}
    /** Literal symbol-798 placements, independently composed with GL stencil. */
    private static void oracle(SpriteBatch b, OriginalFlashAnimationAssets a, Rectangle bounds,
                               FlashBattleRoutes.RocketStrike route, ShaderProgram mask,
                               ShaderProgram tint, int frame, float y, float height,
                               boolean wrongTint, float rocketX) {
        b.flush(); Gdx.gl.glEnable(GL20.GL_STENCIL_TEST); Gdx.gl.glStencilMask(0xff);
        Gdx.gl.glClearStencil(0); Gdx.gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
        Gdx.gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xff);
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
        Gdx.gl.glColorMask(false,false,false,false); b.setShader(mask);
        if (frame == 1) leaf(b,a,bounds,route,789,y,height,.6921234130859375f,-131.2f,-27.45f,rocketX);
        else leaf(b,a,bounds,route,797,y,height,1f,0f,0f,rocketX);
        b.flush(); Gdx.gl.glColorMask(true,true,true,true); Gdx.gl.glStencilMask(0);
        Gdx.gl.glStencilFunc(GL20.GL_EQUAL,1,0xff);
        Gdx.gl.glStencilOp(GL20.GL_KEEP,GL20.GL_KEEP,GL20.GL_KEEP);
        b.setShader(null); leaf(b,a,bounds,route,792,y,height,1f,0f,0f,rocketX);
        if (frame == 1) {
            b.setShader(wrongTint ? null : tint);
            leaf(b,a,bounds,route,794,y,height,1f,-22.05f,-5.55f,rocketX);
        }
        b.flush(); Gdx.gl.glDisable(GL20.GL_STENCIL_TEST); Gdx.gl.glStencilMask(0xff);
        if (frame == 3) {
            b.setShader(wrongTint ? tint : null);
            leaf(b,a,bounds,route,794,y,height,1f,-22.05f,-5.55f,rocketX);
        }
        b.setShader(null);
    }

    /** Vertices derived from the exported rectangle and captured 89-degree parent matrix. */
    private static void leaf(SpriteBatch b, OriginalFlashAnimationAssets a, Rectangle bounds,
                             FlashBattleRoutes.RocketStrike route, int shape, float y,
                             float height, float scale, float tx, float ty, float rocketX) {
        TextureRegion r = a.getShape(shape); assertNotNull("Missing source shape " + shape, r);
        float cos = (float)Math.cos(Math.toRadians(89f));
        float sin = (float)Math.sin(Math.toRadians(89f));
        float aa=cos*scale, bb=sin*scale, cc=-sin*height*scale, dd=cos*height*scale;
        float px=cos*tx-sin*height*ty, py=sin*tx+cos*height*ty;
        float ox=a.getShapeOriginX(shape), oy=a.getShapeOriginY(shape);
        float[] xx={ox,ox,ox+r.getRegionWidth(),ox+r.getRegionWidth()};
        float[] yy={oy+r.getRegionHeight(),oy,oy,oy+r.getRegionHeight()};
        float[] v=new float[20];
        float progress=(rocketX-route.launch.x)/(route.target.x-route.launch.x);
        float shift=BattlefieldCityLayout.sourceXBetween(bounds,rocketX,0,1,progress)
                - BattlefieldCityLayout.sourceX(bounds,rocketX,0);
        b.setColor(1,1,1,1); float color=b.getPackedColor();
        float[] u={r.getU(),r.getU(),r.getU2(),r.getU2()};
        float[] vv={r.getV2(),r.getV(),r.getV(),r.getV2()};
        for(int i=0;i<4;i++) {
            v[i*5]=BattlefieldCityLayout.sourceX(bounds,rocketX+px+aa*xx[i]+cc*yy[i],0)+shift;
            v[i*5+1]=BattlefieldCityLayout.sourceY(bounds,y+py+bb*xx[i]+dd*yy[i]);
            v[i*5+2]=color; v[i*5+3]=u[i]; v[i*5+4]=vv[i];
        }
        b.draw(r.getTexture(),v,0,20);
    }
    private static byte[] render(FrameBuffer f, SpriteBatch b, int w, int h, Draw draw) throws Exception {
        return render(f, b, w, h, false, draw);
    }
    private static byte[] render(FrameBuffer f, SpriteBatch b, int w, int h,
                                 boolean transparent, Draw draw) throws Exception {
        f.begin(); try {
            if (transparent) Gdx.gl.glClearColor(0,0,0,0);
            else Gdx.gl.glClearColor(1,1,1,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_STENCIL_BUFFER_BIT);
            b.setTransformMatrix(new Matrix4().idt()); b.setShader(null); b.setColor(1,1,1,1);
            b.begin(); try { draw.draw(); } finally { b.end(); }
            return ScreenUtils.getFrameBufferPixels(0,0,w,h,true);
        } finally { f.end(); }
    }
    private static void evidence(String name, byte[] rgba, int width, int height) {
        File directory = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir("beta-ui-evidence");
        assertNotNull(directory);
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            image.getPixels().put(rgba).position(0);
            PixmapIO.writePNG(Gdx.files.absolute(new File(directory, name).getAbsolutePath()),
                    image, -1, false);
        } finally { image.dispose(); }
    }
    private static void pixels(WorldDominationGame app,Scenario s)throws Exception{
        OriginalFlashAnimationAssets assets=(OriginalFlashAnimationAssets)field(WorldDominationGame.class,"flashAnimationAssets").get(app);
        FlashBattleUnitRenderer renderer=(FlashBattleUnitRenderer)field(WorldDominationGame.class,"battleUnitRenderer").get(app);
        SpriteBatch batch=new SpriteBatch();ShaderProgram mask=new ShaderProgram(VERT,MASK),tint=new ShaderProgram(VERT,TINT),caller=SpriteBatch.createDefaultShader();
        try{
            assertTrue(mask.getLog(),mask.isCompiled());assertTrue(tint.getLog(),tint.isCompiled());assertTrue(caller.getLog(),caller.isCompiled());
            for(int[] size:new int[][]{{800,600},{1194,552}}){int w=size[0],h=size[1];Rectangle bounds=new Rectangle(0,0,w,h);
                FrameBuffer buffer=new FrameBuffer(Pixmap.Format.RGBA8888,w,h,false,true);batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,w,h));
                try{
                    byte[] actual=render(buffer,batch,w,h,()->{batch.setShader(caller);batch.setColor(.31f,.47f,.69f,.61f);float color=batch.getPackedColor();
                        renderer.drawSourceRocketRole(batch,bounds,s.binding,BattlefieldDisplayState.Kind.UNIT_BODY,0);
                        assertSame(caller,batch.getShader());assertEquals(color,batch.getPackedColor(),0f);});
                    byte[] expected=render(buffer,batch,w,h,()->oracle(batch,assets,bounds,s.binding.animation.getRoute(),mask,tint,1,298.75f,1f,false,442.25f));
                    byte[] wrong=render(buffer,batch,w,h,()->oracle(batch,assets,bounds,s.binding.animation.getRoute(),mask,tint,1,354.25f,1f,false,396.25f));
                    String prefix="source-slot2-rocket-"+w+"x"+h+"-";evidence(prefix+"actual.png",actual,w,h);evidence(prefix+"expected.png",expected,w,h);evidence(prefix+"slot1.png",wrong,w,h);
                    assertArrayEquals("slot2 rocket RGBA",expected,actual);assertTrue("slot1 rocket differs",different(expected,wrong)>0);
                    byte[] building=render(buffer,batch,w,h,()->renderer.drawActionBuilding(batch,bounds,s.silo));
                    byte[] correctSilo=render(buffer,batch,w,h,()->siloLeaf(batch,assets,bounds,s.silo.religionCode,432.25f,296.75f));
                    byte[] wrongSilo=render(buffer,batch,w,h,()->siloLeaf(batch,assets,bounds,s.silo.religionCode,386.25f,352.25f));
                    prefix="source-slot2-silo-"+w+"x"+h+"-";evidence(prefix+"actual.png",building,w,h);evidence(prefix+"expected.png",correctSilo,w,h);evidence(prefix+"slot1.png",wrongSilo,w,h);
                    assertArrayEquals("slot2 silo RGBA",correctSilo,building);assertTrue("slot1 silo differs",different(correctSilo,wrongSilo)>0);
                    assertTrue("rocket paints",different(expected,render(buffer,batch,w,h,()->{}))>0);
                    assertTrue("silo paints",different(correctSilo,render(buffer,batch,w,h,()->{}))>0);
                    assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                }finally{buffer.dispose();}
            }
        }finally{caller.dispose();tint.dispose();mask.dispose();batch.dispose();}
    }
    /** scene 5658 frame12 -> 4246 religion -> first leaf; captured source transforms. */
    private static void siloLeaf(SpriteBatch b,OriginalFlashAnimationAssets a,Rectangle bounds,String religion,float anchorX,float anchorY){
        int shape;float childX,childY;
        switch(religion){case "mus":shape=4172;childX=-79.65f;childY=-55f;break;
            case "jew":shape=4187;childX=-80.65f;childY=-55f;break;
            case "com":shape=4202;childX=-79.65f;childY=-55.5f;break;
            case "chr":shape=4217;childX=-80.15f;childY=-54f;break;
            case "bhu":shape=4232;childX=-78.65f;childY=-56.5f;break;default:throw new AssertionError(religion);}
        TextureRegion r=a.getShape(shape);assertNotNull(r);float ox=a.getShapeOriginX(shape),oy=a.getShapeOriginY(shape);
        float tx=33f+childX,ty=5.75f+childY;float[] xs={ox,ox,ox+r.getRegionWidth(),ox+r.getRegionWidth()};
        float[] ys={oy+r.getRegionHeight(),oy,oy,oy+r.getRegionHeight()};float[] us={r.getU(),r.getU(),r.getU2(),r.getU2()},vs={r.getV2(),r.getV(),r.getV(),r.getV2()};
        float[] v=new float[20];b.setShader(null);b.setColor(1,1,1,1);for(int i=0;i<4;i++){
            v[i*5]=BattlefieldCityLayout.sourceX(bounds,anchorX+tx+xs[i],0);v[i*5+1]=BattlefieldCityLayout.sourceY(bounds,anchorY+ty+ys[i]);
            v[i*5+2]=b.getPackedColor();v[i*5+3]=us[i];v[i*5+4]=vs[i];}b.draw(r.getTexture(),v,0,20);
    }
    private static int different(byte[] a,byte[] b){int n=0;for(int i=0;i<a.length;i+=4)if(a[i]!=b[i]||a[i+1]!=b[i+1]||a[i+2]!=b[i+2]||a[i+3]!=b[i+3])n++;return n;}
    private interface Draw{void draw()throws Exception;}
}
