package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

/** Unit rocket payload attaches before killUnit; rocket_self attaches inside killUnit. */
public final class SourceRocketImpactAttachmentOrderTest {
    @Test public void syntheticPayloadAttachesAboveLiveSmokeBeforeCleanup() throws Exception {
        Fixture f=new Fixture(false);
        f.advanceUntilSmoke();
        int sourceNextDepth=f.display.nextDepth();
        assertTrue(f.rocket.getSourcePlayback().getAttachedTrailCount()>0);
        setAction(f.rocket,"rocket_explode_city");
        double next=f.random.copy().nextDouble();
        assertTrue(f.rocket.tickSourceFrame(false));
        f.depths.afterUnitFrame();
        assertEquals("source attaches effect before removeTrail",sourceNextDepth,
                f.binding.explosion().getDepth());
        assertEquals(next,f.random.copy().nextDouble(),0d);
        assertFalse(f.binding.body().isAttached()); assertTrue(f.binding.dot().isAttached());
        for(int slot=0;slot<f.binding.getSmokeSlotCount();slot++)
            if(f.binding.smoke(slot)!=null) assertFalse(f.binding.smoke(slot).isAttached());
        assertTrue(f.binding.explosion().isAttached());
        assertEquals(0,f.binding.getExplosionFrameIndex());
    }

    @Test public void syntheticSelfKillReusesDepthAfterSmokeAndBodyRemoval() throws Exception {
        Fixture f=new Fixture(true);
        f.advanceUntilSmoke();
        int withSmoke=f.display.nextDepth();
        setAction(f.rocket,"rocket_explode_self");
        assertTrue(f.rocket.tickSourceFrame(false));
        f.depths.afterUnitFrame();
        assertEquals("rocket_self effect attaches after removal",22511,
                f.binding.explosion().getDepth());
        assertTrue(f.binding.explosion().getDepth()<withSmoke);
        assertFalse(f.binding.body().isAttached()); assertTrue(f.binding.dot().isAttached());
        assertTrue(f.rocket.getSourcePlayback().isSelfImpact());
        assertEquals(1,f.binding.getExplosionCount());
    }

    @Test public void legalSourceRouteWithoutSmokeAtImpactRetainsCapturedDepth() throws Exception {
        Fixture f=new Fixture(false);
        restoreCapturedRandom(f.random);
        boolean hit=false;
        for(int tick=0;tick<400;tick++) {
            f.depths.advanceAttachedTimelines();
            int smokeBeforeVisit=f.rocket.getSourcePlayback().getAttachedTrailCount();
            boolean impact=f.rocket.tickSourceFrame(false);
            if(impact) {
                // Existing UI-confirmed compatibility capture: slot-1 ordinary
                // successful rocket, tick 277; all smoke has already detached.
                assertEquals("source capture reaches the city without live smoke",0,
                        smokeBeforeVisit);
                f.depths.afterUnitFrame();
                assertEquals(22511,f.binding.explosion().getDepth());
                assertEquals(212.8,f.binding.getExplosionX(0),1e-5);
                assertEquals(166.75,f.binding.getExplosionY(0),1e-5);
                hit=true; break;
            }
            f.depths.afterUnitFrame();
        }
        assertTrue("ordinary source route reaches its payload",hit);
    }

    private static void restoreCapturedRandom(ReferenceRandom random) throws Exception {
        String name="source-silo-rocket-success-rng-pre-move.json";
        String json;
        try(java.io.InputStream input=SourceRocketImpactAttachmentOrderTest.class
                .getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input);
            json=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
        int start=json.indexOf("\"stateWords\":[")+"\"stateWords\":[".length();
        int end=json.indexOf(']',start);
        String[] words=json.substring(start,end).split(",");
        assertEquals(624,words.length);
        Field state=ReferenceRandom.class.getDeclaredField("state"); state.setAccessible(true);
        int[] target=(int[])state.get(random);
        for(int i=0;i<target.length;i++) target[i]=(int)Long.parseLong(words[i].trim());
        start=json.indexOf("\"stateIndex\":",end)+"\"stateIndex\":".length();
        end=json.indexOf(',',start);
        Field index=ReferenceRandom.class.getDeclaredField("stateIndex"); index.setAccessible(true);
        index.setInt(random,Integer.parseInt(json.substring(start,end).trim()));
    }

    private static void setAction(FlashRocketAnimation rocket,String action) throws Exception {
        Field field=SourceRocketPlayback.class.getDeclaredField("action");
        field.setAccessible(true); field.set(rocket.getSourcePlayback(),action);
    }
    private static final class Fixture {
        final BattlefieldDisplayState display=new BattlefieldDisplayState();
        final ReferenceRandom random=new ReferenceRandom(0);
        final FlashRocketAnimation rocket;
        final SourceRocketDisplayDepths depths;
        final SourceRocketDisplayDepths.Binding binding;
        Fixture(boolean intercepted) {
            display.attach(BattlefieldDisplayState.Kind.AUTHORED,0,"rootAnchor",null,22508);
            rocket=FlashRocketAnimation.forSourceOrdinary(
                    FlashBattleRoutes.createRocketStrike(0,1,2,1,intercepted),
                    "explosion_bioload_1",null,random,5);
            depths=new SourceRocketDisplayDepths(display,rocket,0,
                    (FlashAnimationTimeline)null,new SourceStoppedClipQueue());
            binding=depths.binding();
        }
        void advanceUntilSmoke() {
            for(int tick=0;tick<100 && rocket.getSourcePlayback().getAttachedTrailCount()==0;tick++) {
                rocket.tickSourceFrame(false); depths.afterUnitFrame();
            }
            assertTrue("real rocket source smoke was born",
                    rocket.getSourcePlayback().getAttachedTrailCount()>0);
        }
    }
}
