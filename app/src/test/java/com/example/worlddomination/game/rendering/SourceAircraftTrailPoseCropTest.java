package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source trail callback runs between linear movement and a possible circular step. */
public final class SourceAircraftTrailPoseCropTest {
    @Test public void finiteFighterUsesPriorPositionThroughStraightAndCircleVisits() {
        FlashBattleRoutes.StandalonePlaneStrike route=FlashBattleRoutes.createStandalonePlaneStrike(0,1,2,1,"plane1",false);
        FlashStandalonePlaneAnimation plane=FlashStandalonePlaneAnimation.forPreparedLaunch(route,1,0,null,null,new ReferenceRandom(0));
        SourceAircraftTrail trail=plane.getSourceTrail(); BattlefieldDisplayState root=new BattlefieldDisplayState();
        SourceAircraftTrailDisplay display=new SourceAircraftTrailDisplay(root,trail,0,0,1,plane::getSourceTrailCropProgress);
        for(int i=0;i<3;i++) plane.tickSourceFrame(false);
        boolean straight=false, transition=false, laterCircle=false; float priorPreviousProgress=0f;
        for(int tick=0;tick<180 && !laterCircle;tick++) {
            SourceAircraftPlayback core=plane.getSourcePlayback();
            double oldX=core.getX(),oldY=core.getY(),oldPreviousX=core.getPreviousX(),oldPreviousY=core.getPreviousY();
            float prior=plane.getActor().routeProgress; int before=trail.getCreatedCount();
            String previousAction=core.getAction();
            plane.tickSourceFrame(true);
            double sampleX=core.getTrailVisitX(),sampleY=core.getTrailVisitY();
            double previousX=core.getTrailVisitPreviousX(),previousY=core.getTrailVisitPreviousY();
            float sampleProgress=sampleProgress(route,prior,oldX,oldY,sampleX,sampleY);
            float expected;
            if(same(previousX,previousY,oldX,oldY)) expected=prior;
            else {
                assertTrue("previous coordinate comes from prior View visit",same(previousX,previousY,oldPreviousX,oldPreviousY));
                expected=priorPreviousProgress;
            }
            assertEquals("fighter source previous-position crop",expected,plane.getSourceTrailCropProgress(),1e-6f);
            display.afterUnitFrame();
            for(int slot=before;slot<trail.getCreatedCount();slot++)
                assertEquals("physical puff caches the sampled callback crop",
                        plane.getSourceTrailCropProgress(),display.getBirthCropBlend(slot),0f);
            if(before<trail.getCreatedCount()) straight=true;
            if("plane_circular_flight".equals(core.getAction())) {
                if(!"plane_circular_flight".equals(previousAction)) {
                    assertEquals("source enters circle on the 29th active visit",29,tick+1);
                    assertFalse("transition updates its linear trail pose",
                            same(sampleX,sampleY,oldX,oldY));
                    transition=true;
                } else if(transition && same(sampleX,sampleY,oldX,oldY)
                        && !same(previousX,previousY,oldX,oldY)) laterCircle=true;
            }
            if(same(core.getPreviousX(),core.getPreviousY(),sampleX,sampleY)) priorPreviousProgress=sampleProgress;
            else if(same(core.getPreviousX(),core.getPreviousY(),oldX,oldY)) priorPreviousProgress=prior;
        }
        assertTrue("actual straight-flight births",straight);
        assertTrue("linear-to-circle intermediate pose",transition);
        assertTrue("circular previous-position history",laterCircle);
        float retained=display.getBirthCropBlend(0);
        plane.tickSourceFrame(true); display.afterUnitFrame();
        assertEquals("attached child's crop remains fixed",retained,display.getBirthCropBlend(0),0f);
    }

    @Test public void finiteBomberUsesTrailVisitPoseBeforeCircularStep() {
        FlashBattleRoutes.StandalonePlaneStrike route=FlashBattleRoutes.createStandalonePlaneStrike(0,1,2,1,"plane2",false);
        FlashStandalonePlaneAnimation plane=FlashStandalonePlaneAnimation.forPreparedLaunch(route,1,0,null,null,new ReferenceRandom(0));
        SourceAircraftTrail trail=plane.getSourceTrail();
        SourceAircraftTrailDisplay display=new SourceAircraftTrailDisplay(new BattlefieldDisplayState(),trail,0,0,1,plane::getSourceTrailCropProgress);
        boolean births=false,transition=false,circle=false;
        for(int tick=0;tick<180 && !circle;tick++) {
            SourceAircraftPlayback core=plane.getSourcePlayback(); double oldX=core.getX(),oldY=core.getY();
            float prior=plane.getActor().routeProgress; int before=trail.getCreatedCount();
            String previousAction=core.getAction();
            plane.tickSourceFrame(true);
            double sampleX=core.getTrailVisitX(),sampleY=core.getTrailVisitY();
            float expected=sampleProgress(route,prior,oldX,oldY,sampleX,sampleY);
            assertEquals("paired smoke uses current trail-visit pose",expected,plane.getSourceTrailCropProgress(),1e-6f);
            display.afterUnitFrame();
            for(int slot=before;slot<trail.getCreatedCount();slot++)
                assertEquals(plane.getSourceTrailCropProgress(),display.getBirthCropBlend(slot),0f);
            if(before<trail.getCreatedCount()) births=true;
            if("plane_circular_flight".equals(core.getAction())) {
                if(!"plane_circular_flight".equals(previousAction)) {
                    assertEquals("source enters circle on the 29th active visit",29,tick+1);
                    assertFalse("transition updates its linear trail pose",
                            same(sampleX,sampleY,oldX,oldY));
                    transition=true;
                } else if(transition && same(sampleX,sampleY,oldX,oldY)
                        && !same(sampleX,sampleY,core.getX(),core.getY())) circle=true;
            }
        }
        assertTrue("actual paired births",births);
        assertTrue("intermediate entry pose sampled before circular step",transition);
        assertTrue("subsequent circular pose sampled before movement",circle);
    }

    @Test public void carrierOffsetUsesPublishedCurrentPoseAndRetainsBirthCrop() {
        FlashCarrierPlaneAnimation plane=new FlashCarrierPlaneAnimation(
                FlashBattleRoutes.createCarrierPlaneStrike(0,1,2,0,false),0,null,null,new ReferenceRandom(0));
        SourceAircraftTrail trail=plane.getSourceTrail();
        SourceAircraftTrailDisplay display=new SourceAircraftTrailDisplay(new BattlefieldDisplayState(),trail,0,0,1,plane::getSourceTrailCropProgress);
        for(int i=0;i<7;i++) { plane.tickSourceFrame(true); display.afterUnitFrame(); }
        assertTrue(trail.getCreatedCount()>0);
        float first=display.getBirthCropBlend(0);
        assertTrue("ship smoke offset follows travelled current pose",first>0f);
        assertEquals(plane.getActor().routeProgress,plane.getSourceTrailCropProgress(),0f);
        for(int i=0;i<4;i++) {plane.tickSourceFrame(true);display.afterUnitFrame();}
        assertEquals(first,display.getBirthCropBlend(0),0f);
        assertTrue(plane.getActor().routeProgress>first);
    }

    private static boolean same(double x,double y,double a,double b) { return x==a && y==b; }
    private static float sampleProgress(FlashBattleRoutes.StandalonePlaneStrike r,float prior,
                                        double oldX,double oldY,double x,double y) {
        if(same(x,y,oldX,oldY)) return prior;
        float startTarget=distance(r.start.x,r.start.y,r.target.x,r.target.y);
        float orbit=48f*r.orbitRadius*(float)Math.PI/180f;
        float total=Math.max(startTarget,distance(r.start.x,r.start.y,r.liftOff.x,r.liftOff.y)
                +distance(r.liftOff.x,r.liftOff.y,r.orbitEntry.x,r.orbitEntry.y)+orbit
                +distance(r.orbitEntry.x,r.orbitEntry.y,r.target.x,r.target.y));
        return Math.min(1f,prior+(float)Math.hypot(x-oldX,y-oldY)/total);
    }
    private static float distance(float x,float y,float a,float b) { return (float)Math.hypot(a-x,b-y); }
}
