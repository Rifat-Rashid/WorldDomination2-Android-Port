package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ScreenUtils;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** An unmodified legal turn22 fighter interception, with its concurrent AI suicide Unit. */
@RunWith(AndroidJUnit4.class)
public final class SourceInterceptedAircraftInstrumentedTest {
    private static final String PREFIX = "source-intercepted-plane001-";

    @Test public void legalInterceptMatchesUnitRootCityRandomnessAndOwnedAudio() throws Exception {
        verify(0);
    }

    @Test public void unmutingDuringLiftOffStartsOnlyOneOwnedVoice() throws Exception {
        verify(198);
    }

    private static void verify(int muteThroughTick) throws Exception {
        List<Map<String,String>> source = csv(muteThroughTick > 0 ? "muted-state.csv" : "state.csv");
        List<Map<String,String>> roles = csv("roles.csv");
        List<Map<String,String>> city = csv("city.csv");
        assertEquals(378, source.size());
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            OriginalAudio audio = (OriginalAudio) field(app, "audio");
            @SuppressWarnings("unchecked") Map<OriginalAudio.Cue,Sound> sounds =
                    (Map<OriginalAudio.Cue,Sound>) field(audio, "sounds");
            int[] tick = {0};
            Voice takeoff = new Voice(tick, 901), hit = new Voice(tick, 902);
            Sound oldTakeoff = sounds.put(OriginalAudio.Cue.FIGHTER_TAKEOFF, takeoff.spy());
            Sound oldHit = sounds.put(OriginalAudio.Cue.EXPLOSION_SMALL3, hit.spy());
            boolean muted = !audio.isSoundEnabled();
            if (audio.isSoundEnabled() == (muteThroughTick > 0)) audio.toggleSound();
            try {
                GameState game = ready();
                set(app, "game", game);
                BattlefieldCityRenderer cities = (BattlefieldCityRenderer) field(app, "cityRenderer");
                cities.reset(game);
                invoke(app, "confirmTurnAndPreparePlayback");
                assertEquals(22, game.getTurn());
                GameState.CombatEvent intercepted = null;
                for (GameState.CombatEvent event : game.getLastTurnCombatEvents())
                    if (event.attackerPlayerId == 0 && "plane1".equals(event.weaponName)) intercepted = event;
                assertNotNull("legal source plane event", intercepted);
                assertEquals(GameState.CombatEventType.STRIKE_INTERCEPTED, intercepted.type);
                assertEquals(0, intercepted.casualtiesMillions);
                assertEquals(1, intercepted.defenderPlayerId);
                assertEquals(0, intercepted.cityIndex);
                @SuppressWarnings("unchecked") List<FlashStandalonePlaneAnimation> planes =
                        (List<FlashStandalonePlaneAnimation>) field(app,"standalonePlaneAnimations");
                assertEquals(1, planes.size());
                FlashStandalonePlaneAnimation plane = planes.get(0);
                SourceAircraftPlayback core = plane.getSourcePlayback();
                assertNotNull("interception must use shared source core", core);
                @SuppressWarnings("unchecked") List<SourceInterceptedAircraftDisplayDepths> bindings =
                        (List<SourceInterceptedAircraftDisplayDepths>) field(app,"sourceInterceptedAircraftBindings");
                assertEquals(1, bindings.size());
                SourceInterceptedAircraftDisplayDepths binding = bindings.get(0);
                ReferenceRandom random = (ReferenceRandom) field(app,"battlePresentationRandom");
                BattlefieldDisplayState root = (BattlefieldDisplayState) field(app,"battlefieldDisplay");
                double canonical = game.createPresentationRandomCopy().nextDouble();
                Method update = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
                update.setAccessible(true);
                int roleCursor = 0;
                for (int frame = 0; frame <= 377; frame++) {
                    tick[0] = frame;
                    if (muteThroughTick > 0 && frame == muteThroughTick + 1) audio.toggleSound();
                    if (frame > 0) update.invoke(app, 1f / 24f);
                    Map<String,String> row = source.get(frame);
                    assertEquals("source tick", frame, integer(row,"tick"));
                    assertEquals("action t"+frame, row.get("action"), core.getAction());
                    number(row,"curX",core.getX(),frame);
                    number(row,"curY",core.getY(),frame);
                    number(row,"mcX",plane.getActor().sourceX,frame);
                    number(row,"mcY",plane.getActor().sourceY,frame);
                    assertEquals("frame t"+frame, integer(row,"mcFrame")-1,plane.getActor().sourceFrame);
                    assertEquals("alpha t"+frame, decimal(row,"mcAlpha")/100,plane.getActor().alpha,.00001);
                    assertEquals("scale t"+frame, decimal(row,"mcScaleX")/100,plane.getActor().scale,.00001);
                    assertEquals("wrapper t"+frame, bool(row,"active"),!core.isRemoved());
                    assertEquals("body t"+frame,bool(row,"bodyAttached"),plane.getActor().visible);
                    assertEquals("isolated RNG t"+frame,decimal(row,"rngNext"),random.copy().nextDouble(),0);
                    assertEquals("canonical RNG frozen t"+frame,canonical,game.createPresentationRandomCopy().nextDouble(),0);
                    if (frame < 377) {
                        assertTrue("private city still active t"+frame,cities.isBattleActive());
                        assertEquals("city frame t"+frame,integer(city.get(frame),"imageFrame"),cities.getSourceCityFrame(5));
                        assertEquals("no biological callback on intercept t"+frame,0,cities.getSourceCloudCount(5));
                    } else assertFalse(cities.isBattleActive());
                    while (roleCursor < roles.size() && integer(roles.get(roleCursor),"tick") == frame) {
                        Map<String,String> role = roles.get(roleCursor++);
                        if (frame == 377) continue; // Detached Python adapter retains its root after native canonical handoff.
                        if (role.get("role").startsWith("rootExplosion:")) {
                            SourceDisplayList.Entry<BattlefieldDisplayState.Item> effect = root.clips()
                                    .atDepth(integer(role,"depth"));
                            assertNotNull("concurrent root effect t"+frame,effect);
                            assertEquals("concurrent effect identity t"+frame,role.get("name"),effect.getValue().name);
                            if ("unit2explosion".equals(role.get("name"))) {
                                // Existing suicide playback uses its body's drawing role for the replacement clip.
                                assertEquals(BattlefieldDisplayState.Kind.UNIT_BODY,effect.getValue().kind);
                                assertTrue(effect.getValue().owner instanceof SourceSuicideDisplayDepths.Binding);
                                assertSame(effect,((SourceSuicideDisplayDepths.Binding)effect.getValue().owner).explosion());
                            } else {
                                assertEquals(BattlefieldDisplayState.Kind.EFFECT,effect.getValue().kind);
                                assertSame(binding,effect.getValue().owner);
                                assertSame(binding.explosion(),effect);
                            }
                            assertTrue(effect.isAttached());
                            continue;
                        }
                        SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = role(binding,role.get("role"));
                        if (!bool(role,"exists")) { assertNull(entry); continue; }
                        assertNotNull("physical "+role.get("role")+" t"+frame,entry);
                        assertEquals("depth "+role.get("role")+" t"+frame,integer(role,"depth"),entry.getDepth());
                        assertEquals("attachment "+role.get("role")+" t"+frame,bool(role,"attached"),entry.isAttached());
                        if ("explosion".equals(role.get("role"))) {
                            assertEquals("effect frame t"+frame,integer(role,"frame")-1,binding.getExplosionFrameIndex());
                            number(role,"x",binding.getExplosionX(),frame);
                            number(role,"y",binding.getExplosionY(),frame);
                        }
                    }
                    assertEquals("one coalesced takeoff t"+frame,
                            frame < Math.max(196,muteThroughTick+1) ? 0 : 1,takeoff.plays);
                    assertEquals("owned takeoff stop t"+frame,frame < 314 ? 0 : 1,takeoff.stops);
                    assertEquals("one death cue t"+frame,frame < 314 ? 0 : 1,hit.plays);
                    if (frame == 313 && muteThroughTick == 0)
                        SourceInterceptedAircraftTintPixels.verify(app,binding);
                    if (frame == 314 && muteThroughTick == 0) verifyExplosionPixels(app,binding);
                }
                assertEquals(roles.size(),roleCursor);
                assertEquals(901L,takeoff.stoppedId);
                assertEquals(314,takeoff.stopTick);
                assertEquals(314,hit.playTick);
                assertEquals(-.8793181211582746f,hit.pan,.000001f);
            } finally {
                restore(sounds,OriginalAudio.Cue.FIGHTER_TAKEOFF,oldTakeoff);
                restore(sounds,OriginalAudio.Cue.EXPLOSION_SMALL3,oldHit);
                if (audio.isSoundEnabled() == muted) audio.toggleSound();
            }
        });
    }

    private static GameState ready() throws Exception {
        GameSetup setup = new GameSetup(0);
        setup.setDifficulty(1); assertTrue(setup.toggleOpponent(0)); assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        JsonValue meta;
        try (java.io.InputStream in = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open(PREFIX+"provenance.json")) { meta = new JsonReader().parse(in); }
        for (JsonValue step : meta.get("schedule")) {
            while (game.getPendingHumanDiplomacyProposal() != null) assertTrue(game.respondToDiplomacyProposal(false));
            JsonValue options = step.get("options");
            switch (step.getString("action")) {
                case "research":
                    game.selectAction(GameState.Action.RESEARCH);
                    assertTrue(game.selectResearchTechnology(options.getString("tech"))); break;
                case "production":
                    game.selectAction(GameState.Action.PRODUCTION);
                    assertTrue(game.configureProduction(options.getString("category"),options.getInt("percent"))); break;
                case "preAttack":
                    game.selectAction(GameState.Action.PREPARED_ATTACK);
                    if (options.has("target")) assertTrue(game.configurePreparedLaunch(options.getInt("target"),options.getInt("city"),options.getString("payload")));
                    else assertTrue(game.configurePreparedCarrier(options.getString("weapon")));
                    break;
                default: fail("Unexpected source action");
            }
            assertTrue(game.acceptSelectedAction());
            game.selectAction(GameState.Action.RESOURCES); assertTrue(game.acceptSelectedAction());
            if (step.getInt("turn") < 22) game.confirmTurn();
        }
        assertEquals(21,game.getTurn());
        return game;
    }

    private static SourceDisplayList.Entry<BattlefieldDisplayState.Item> role(SourceInterceptedAircraftDisplayDepths b,String role) {
        switch(role) { case "body":return b.body(); case "shadow":return b.shadow(); case "tint":return b.tint(); case "dot":return b.dot(); case "explosion":return b.explosion(); default:throw new AssertionError(role); }
    }

    private static void verifyExplosionPixels(WorldDominationGame app,
            SourceInterceptedAircraftDisplayDepths binding) throws Exception {
        OriginalFlashAnimationAssets assets=(OriginalFlashAnimationAssets)field(app,"flashAnimationAssets");
        FlashBattleUnitRenderer renderer=(FlashBattleUnitRenderer)field(app,"battleUnitRenderer");
        FlashAnimationTimeline.Frame frame=assets.getTimeline("explosion003").getFrame(0);
        assertEquals("source frame1 direct leaf",1,frame.layers.length);
        assertEquals(1810,frame.layers[0].shapeId);
        assertEquals(1f,frame.a,0f);assertEquals(1f,frame.d,0f);
        assertEquals(0f,frame.b,0f);assertEquals(0f,frame.c,0f);
        assertEquals(0f,frame.tx,0f);assertEquals(0f,frame.ty,0f);
        assertEquals(-54f,assets.getShapeOriginX(1810),0f);
        assertEquals(-47f,assets.getShapeOriginY(1810),0f);
        Rectangle bounds=new Rectangle(0,0,800,600);
        TextureRegion shape=assets.getShape(1810); assertNotNull(shape);
        FrameBuffer buffer=new FrameBuffer(Pixmap.Format.RGBA8888,800,600,false,true);
        SpriteBatch batch=new SpriteBatch();
        batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0,0,800,600));
        try {
            byte[] actual=render(buffer,batch,()->{
                batch.setColor(.2f,.3f,.4f,.5f);float color=batch.getPackedColor();
                renderer.drawInterceptedAircraftRole(batch,bounds,binding,BattlefieldDisplayState.Kind.EFFECT);
                assertEquals("effect restores caller batch color",color,batch.getPackedColor(),0f);
            });
            byte[] expected=render(buffer,batch,()->{
                float x=BattlefieldCityLayout.sourceX(bounds,(float)binding.getExplosionX()-54f,1);
                float top=BattlefieldCityLayout.sourceY(bounds,(float)binding.getExplosionY()-47f);
                batch.draw(shape,x,top-shape.getRegionHeight(),shape.getRegionWidth(),shape.getRegionHeight());
            });
            byte[] unregistered=render(buffer,batch,()->{
                float x=BattlefieldCityLayout.sourceX(bounds,(float)binding.getExplosionX(),1);
                float top=BattlefieldCityLayout.sourceY(bounds,(float)binding.getExplosionY());
                batch.draw(shape,x,top-shape.getRegionHeight(),shape.getRegionWidth(),shape.getRegionHeight());
            });
            evidence("source-intercepted-plane-t314-actual.png",actual);
            evidence("source-intercepted-plane-t314-expected.png",expected);
            evidence("source-intercepted-plane-t314-unregistered.png",unregistered);
            assertTrue("negative detects missing original registration",differences(expected,unregistered)>100);
            assertEquals("source explosion registration pixels",0,differences(expected,actual));
        } finally {batch.dispose();buffer.dispose();}
    }
    private interface Draw {void run() throws Exception;}
    private static byte[] render(FrameBuffer buffer,SpriteBatch batch,Draw draw) throws Exception {
        buffer.begin();
        try {Gdx.gl.glClearColor(1,1,1,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.setShader(null);batch.setColor(1,1,1,1);batch.begin();
            try{draw.run();}finally{batch.end();}
            return ScreenUtils.getFrameBufferPixels(0,0,800,600,true);
        }finally{buffer.end();}
    }
    private static int differences(byte[] a,byte[] b){assertEquals(a.length,b.length);int n=0;
        for(int i=0;i<a.length;i+=4)if(a[i]!=b[i]||a[i+1]!=b[i+1]||a[i+2]!=b[i+2]||a[i+3]!=b[i+3])n++;return n;}
    private static void evidence(String name,byte[] rgba){
        java.io.File dir=InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir("beta-ui-evidence");
        assertNotNull(dir);assertTrue(dir.isDirectory()||dir.mkdirs());
        Pixmap image=new Pixmap(800,600,Pixmap.Format.RGBA8888);
        try{image.getPixels().put(rgba).position(0);PixmapIO.writePNG(Gdx.files.absolute(new java.io.File(dir,name).getAbsolutePath()),image,-1,false);}
        finally{image.dispose();}
    }
    private static List<Map<String,String>> csv(String suffix) throws Exception {
        List<Map<String,String>> result = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open(PREFIX+suffix),StandardCharsets.UTF_8))) {
            String[] names=in.readLine().split(",",-1); String line;
            while((line=in.readLine())!=null) { String[] cells=line.split(",",-1); assertEquals(names.length,cells.length);
                Map<String,String> row=new HashMap<>(); for(int i=0;i<names.length;i++)row.put(names[i],cells[i]); result.add(row); }
        }
        return result;
    }
    private static int integer(Map<String,String> row,String key) { return Integer.parseInt(row.get(key)); }
    private static double decimal(Map<String,String> row,String key) { return Double.parseDouble(row.get(key)); }
    private static boolean bool(Map<String,String> row,String key) { return Boolean.parseBoolean(row.get(key)); }
    private static void number(Map<String,String> row,String key,double actual,int tick) {
        double expected="nan".equals(row.get(key))?Double.NaN:decimal(row,key);
        if(Double.isNaN(expected))assertTrue(key+" t"+tick,Double.isNaN(actual));
        else assertEquals(key+" t"+tick,expected,actual,.001);
    }
    private static Object field(Object owner,String name) throws Exception { Field f=owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
    private static void set(Object owner,String name,Object value) throws Exception { Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value); }
    private static void invoke(Object owner,String name) throws Exception { Method m=owner.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(owner); }
    private static void restore(Map<OriginalAudio.Cue,Sound> sounds,OriginalAudio.Cue cue,Sound value) { if(value==null)sounds.remove(cue);else sounds.put(cue,value); }
    private static final class Voice {
        final int[] tick; final long id; int plays,stops,playTick=-1,stopTick=-1; long stoppedId=-1; float pan;
        Voice(int[] tick,long id){this.tick=tick;this.id=id;}
        Sound spy(){return(Sound)Proxy.newProxyInstance(Sound.class.getClassLoader(),new Class<?>[]{Sound.class},(p,m,a)->{
            if("play".equals(m.getName())){plays++;playTick=tick[0];pan=((Number)a[2]).floatValue();return id;}
            if("stop".equals(m.getName())&&a!=null&&a.length==1){stops++;stopTick=tick[0];stoppedId=((Number)a[0]).longValue();}
            return m.getReturnType()==long.class?0L:null;
        });}
    }
}
