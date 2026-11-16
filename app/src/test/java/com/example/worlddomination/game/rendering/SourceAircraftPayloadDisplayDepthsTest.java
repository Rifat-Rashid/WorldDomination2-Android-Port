package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import static org.junit.Assert.*;

/** The successful payload attaches before source smoke children leave the root. */
public final class SourceAircraftPayloadDisplayDepthsTest {
    @Test public void warheadFallbackUsesLiveSmokeDepthAndStopsAtFrame52() {
        verify(null,"explosion_warhead_1",52);
    }
    @Test public void biologicalEffectStopsAtFrame56AndSurvivesUntilCleanup() {
        verify("explosion_bioload_1","explosion_bioload_1",56);
    }
    private static void verify(String requested,String expected,int stopFrame) {
        BattlefieldDisplayState display=new BattlefieldDisplayState();
        SourceStoppedClipQueue stopped=new SourceStoppedClipQueue();
        String[] names={"unit1","shadow_unit1","tintunit1","targetDotunit1"};
        for(int i=0;i<names.length;i++) display.attach(BattlefieldDisplayState.Kind.AUTHORED,
                i,names[i],null,22509+i);
        FlashStandalonePlaneAnimation plane=FlashStandalonePlaneAnimation.forPreparedLaunch(
                FlashBattleRoutes.createStandalonePlaneStrike(0,1,2,1,"plane1",false),
                1,0,requested,null,new ReferenceRandom(0));
        SourceInterceptedAircraftDisplayDepths binding=
                new SourceInterceptedAircraftDisplayDepths(display,plane,0,stopped);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> first=display.attach(
                BattlefieldDisplayState.Kind.EFFECT,0,"unit1trail0",null,22513);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> second=display.attach(
                BattlefieldDisplayState.Kind.EFFECT,1,"unit1trail1",null,22514);
        assertTrue(first.isAttached()); assertTrue(second.isAttached());
        binding.attachPayloadImpact();
        assertEquals("payload allocates while smoke depths remain occupied",22515,
                binding.explosion().getDepth());
        assertEquals(expected,binding.getExplosionLinkage());
        assertEquals(212.8,binding.getExplosionX(),0d);
        assertEquals(166.75,binding.getExplosionY(),0d);
        assertEquals(0,binding.getExplosionFrameIndex());
        assertTrue(binding.body().isAttached());
        display.remove(first); display.remove(second);
        for(int i=0;i<120;i++) binding.advanceAttachedTimelines();
        assertEquals(stopFrame-1,binding.getExplosionFrameIndex());
        assertTrue(binding.explosion().isAttached());
        for(int i=0;i<600 && !plane.isFinished();i++) {
            plane.tickSourceFrame(true);
            binding.afterUnitFrame();
        }
        assertTrue("natural source flyoff reaches removal",plane.isFinished());
        assertEquals(1,stopped.size());
        assertTrue("successful Unit body is retained before allUnitsDone",binding.body().isAttached());
        assertEquals(1,stopped.removeAtAllUnitsDone());
        assertFalse(binding.explosion().isAttached());
        assertFalse(binding.body().isAttached());
        assertTrue("source target dot survives cleanup",binding.dot().isAttached());
    }
}
