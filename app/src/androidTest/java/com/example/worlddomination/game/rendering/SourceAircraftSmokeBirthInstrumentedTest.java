package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

/** World-driven source aircraft births and retained physical smoke projection. */
@RunWith(AndroidJUnit4.class)
public final class SourceAircraftSmokeBirthInstrumentedTest {
    @Test public void preparedFighterBirthAndHandoff() throws Exception { verify("fighter"); }
    @Test public void preparedBomberBirthAndHandoff() throws Exception { verify("bomber"); }
    @Test public void carrierAircraftBirthAndHandoff() throws Exception { verify("carrier"); }

    private static void verify(String kind) throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            OriginalAudio audio=(OriginalAudio)field(app,"audio");
            boolean restore=audio.isSoundEnabled();
            if(restore) audio.toggleSound();
            try {
                GameState game=game(kind);
                set(app,"game",game);
                BattlefieldCityRenderer cities=(BattlefieldCityRenderer)field(app,"cityRenderer");
                cities.reset(game);
                ((SourceBattleRecordClock)field(app,"battleRecordClock")).reset();
                call(app,"confirmTurnAndPreparePlayback");
                double canonical=game.createPresentationRandomCopy().nextDouble();
                @SuppressWarnings("unchecked") Map<SourceAircraftTrail,SourceAircraftTrailDisplay> displays=
                        (Map<SourceAircraftTrail,SourceAircraftTrailDisplay>)field(app,"sourceAircraftTrails");
                @SuppressWarnings("unchecked") List<FlashStandalonePlaneAnimation> standalone=
                        (List<FlashStandalonePlaneAnimation>)field(app,"standalonePlaneAnimations");
                @SuppressWarnings("unchecked") List<FlashCarrierPlaneAnimation> carriers=
                        (List<FlashCarrierPlaneAnimation>)field(app,"carrierPlaneAnimations");
                IdentityHashMap<SourceAircraftTrail,Snapshot[]> recorded=new IdentityHashMap<>();
                int births=0, retainedWithMovingOwner=0;
                Method update=WorldDominationGame.class.getDeclaredMethod("fixedUpdate",float.class);
                update.setAccessible(true);
                boolean handedOff=false;
                for(int tick=1;tick<=1000;tick++) {
                    update.invoke(app,1f/24f);
                    assertEquals("canonical random frozen "+kind+" tick "+tick,
                            canonical,game.createPresentationRandomCopy().nextDouble(),0d);
                    for(FlashStandalonePlaneAnimation plane:standalone) {
                        SourceAircraftTrail trail=plane.getSourceTrail();
                        if(trail==null || !displays.containsKey(trail))continue;
                        int[] counts=check(trail,displays.get(trail),plane.getSourceTrailCropProgress(),recorded);
                        births+=counts[0];retainedWithMovingOwner+=counts[1];
                    }
                    for(FlashCarrierPlaneAnimation plane:carriers) {
                        SourceAircraftTrail trail=plane.getSourceTrail();
                        if(trail==null || !displays.containsKey(trail))continue;
                        int[] counts=check(trail,displays.get(trail),plane.getSourceTrailCropProgress(),recorded);
                        births+=counts[0];retainedWithMovingOwner+=counts[1];
                    }
                    if(!cities.isBattleActive()) {handedOff=true;break;}
                }
                assertTrue(kind+" creates real physical smoke",births>0);
                assertTrue(kind+" retains a puff while owner advances",retainedWithMovingOwner>0);
                assertTrue(kind+" reaches canonical handoff",handedOff);
                assertTrue("private smoke displays cleared at handoff",displays.isEmpty());
            } finally {if(restore) audio.toggleSound();}
        });
    }

    private static int[] check(SourceAircraftTrail trail,SourceAircraftTrailDisplay display,
            float ownerProgress,IdentityHashMap<SourceAircraftTrail,Snapshot[]> recorded) {
        Snapshot[] slots=recorded.get(trail);
        if(slots==null){slots=new Snapshot[trail.getSlotCount()];recorded.put(trail,slots);}
        int births=0, retained=0;
        for(int i=0;i<slots.length;i++) {
            if(!trail.isCreated(i))continue;
            if(slots[i]==null) {
                assertEquals("birth samples source trail pose progress",ownerProgress,
                        display.getBirthCropBlend(i),0f);
                slots[i]=new Snapshot(display.getBirthCropBlend(i),trail.getX(i),trail.getY(i));
                births++;
            } else if(trail.isAttached(i)) {
                Snapshot snapshot=slots[i];
                assertEquals("retained clip crop",snapshot.blend,display.getBirthCropBlend(i),0f);
                assertEquals("retained source x",snapshot.x,trail.getX(i),0d);
                assertEquals("retained source y",snapshot.y,trail.getY(i),0d);
                // At phone size, same physical point and birth blend must remain at the same x.
                assertEquals("retained phone projection",phoneX(display,snapshot.x,snapshot.blend),
                        phoneX(display,trail.getX(i),display.getBirthCropBlend(i)),0f);
                if(Math.abs(ownerProgress-snapshot.blend)>1e-6f) retained++;
            }
        }
        return new int[]{births,retained};
    }
    private static float phoneX(SourceAircraftTrailDisplay display,double x,float progress) {
        float margin=229f;
        float from=display.attackerPlayerId==1||display.attackerPlayerId==3?-margin:
                display.attackerPlayerId==2||display.attackerPlayerId==4?margin:0f;
        float to=display.targetPlayerId==1||display.targetPlayerId==3?-margin:
                display.targetPlayerId==2||display.targetPlayerId==4?margin:0f;
        return margin+(float)x*.92f+from+(to-from)*progress;
    }
    private static final class Snapshot {
        final float blend;final double x,y;
        Snapshot(float blend,double x,double y){this.blend=blend;this.x=x;this.y=y;}
    }
    private static GameState game(String kind) throws Exception {
        if("fighter".equals(kind)) {
            Method m=SourceInterceptedAircraftInstrumentedTest.class.getDeclaredMethod("ready");
            m.setAccessible(true);return (GameState)m.invoke(null);
        }
        if("carrier".equals(kind))return SourceBiologicalCarriersInstrumentedTest.gameReadyForShip(1);
        JsonValue oracle;
        try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                .open("source-finite-plane002-intercept.json")){oracle=new JsonReader().parse(in);}
        Method m=SourceBomberPlaybackInstrumentedTest.class.getDeclaredMethod("legalGame",JsonValue.class);
        m.setAccessible(true);return (GameState)m.invoke(null,oracle);
    }
    private static Object field(Object owner,String name)throws Exception{
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    private static void set(Object owner,String name,Object value)throws Exception{
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);
    }
    private static void call(Object owner,String name)throws Exception{
        Method m=owner.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(owner);
    }
}
