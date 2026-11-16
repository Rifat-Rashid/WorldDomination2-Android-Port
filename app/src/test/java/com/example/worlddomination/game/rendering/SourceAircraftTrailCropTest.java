package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import org.junit.Test;
import static org.junit.Assert.*;

/** Crop projection is a physical smoke child property fixed on attachment. */
public final class SourceAircraftTrailCropTest {
    @Test public void retainedPuffKeepsItsBirthProjectionAsOwnerMoves() {
        ReferenceRandom random=new ReferenceRandom(0);
        SourceAircraftTrail trail=new SourceAircraftTrail(SourceAircraftTrail.Origin.PREVIOUS_UNIT_POSITION,random);
        double[] blend={0.25}; int[] calls={0};
        SourceAircraftTrailDisplay display=new SourceAircraftTrailDisplay(
                new BattlefieldDisplayState(),trail,0,0,1,()->{calls[0]++; return blend[0];});
        for(int i=0;i<5;i++) trail.tick(320,200,330,201,30);
        double next=random.copy().nextDouble();
        display.afterUnitFrame();
        assertEquals(3,calls[0]); assertEquals(next,random.copy().nextDouble(),0d);
        assertEquals(0.25f,display.getBirthCropBlend(0),0f);
        assertTrue(trail.isAttached(0)); double x=trail.getX(0), y=trail.getY(0);
        Rectangle phone=new Rectangle(0,0,1194,552);
        float projected=project(phone,trail,display,0);
        blend[0]=0.9; display.afterUnitFrame();
        assertEquals(3,calls[0]); assertEquals(0.25f,display.getBirthCropBlend(0),0f);
        assertEquals(x,trail.getX(0),0d); assertEquals(y,trail.getY(0),0d);
        assertEquals(projected,project(phone,trail,display,0),0f);
        assertNotEquals(projected,BattlefieldCityLayout.sourceXBetween(phone,(float)x,0,1,0.9f),0.001f);
    }

    @Test public void pairedChildrenCaptureTheirOwnBirthAndKeepConsecutiveDepths() {
        SourceAircraftTrail trail=new SourceAircraftTrail(SourceAircraftTrail.Origin.PAIRED_PLANE,new ReferenceRandom(0));
        double[] blend={0.4}; int[] calls={0}; BattlefieldDisplayState root=new BattlefieldDisplayState();
        SourceAircraftTrailDisplay display=new SourceAircraftTrailDisplay(root,trail,1,0,2,()->{calls[0]++;return blend[0];});
        trail.tick(0,0,200,100,45); display.afterUnitFrame();
        assertEquals(2,calls[0]); assertEquals(0.4f,display.getBirthCropBlend(0),0f);
        assertEquals(0.4f,display.getBirthCropBlend(1),0f);
        assertEquals(trail.getDepth(0)+1,trail.getDepth(1));
        blend[0]=0.8; trail.tick(0,0,220,90,45); display.afterUnitFrame();
        assertEquals(0.4f,display.getBirthCropBlend(0),0f);
        assertEquals(0.4f,display.getBirthCropBlend(1),0f);
        assertEquals(0.8f,display.getBirthCropBlend(2),0f);
        assertEquals(0.8f,display.getBirthCropBlend(3),0f);
        assertEquals(4,calls[0]);
    }

    @Test public void nonfiniteAndOutOfRangeValuesAreBoundedAndLegacyStaysIdentity() {
        for(double value:new double[]{-1,2,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            SourceAircraftTrail trail=new SourceAircraftTrail(SourceAircraftTrail.Origin.PAIRED_PLANE,new ReferenceRandom(0));
            SourceAircraftTrailDisplay display=new SourceAircraftTrailDisplay(new BattlefieldDisplayState(),trail,0,0,2,()->value);
            trail.tick(0,0,200,100,45); display.afterUnitFrame();
            assertEquals(value==2 ? 1f : 0f,display.getBirthCropBlend(0),0f);
        }
        SourceAircraftTrail trail=new SourceAircraftTrail(SourceAircraftTrail.Origin.PAIRED_PLANE,new ReferenceRandom(0));
        SourceAircraftTrailDisplay legacy=new SourceAircraftTrailDisplay(new BattlefieldDisplayState(),trail,3,0);
        trail.tick(0,0,200,100,45); legacy.afterUnitFrame();
        assertEquals(3,legacy.targetPlayerId); assertEquals(0f,legacy.getBirthCropBlend(0),0f);
        assertThrows(IndexOutOfBoundsException.class,()->legacy.getBirthCropBlend(-1));
        assertThrows(IndexOutOfBoundsException.class,()->legacy.getBirthCropBlend(trail.getSlotCount()));
    }

    @Test public void frozenBirthBlendProjectsBothDirectionsAndFourByThree() {
        Rectangle fourByThree=new Rectangle(0,0,800,600), phone=new Rectangle(0,0,1194,552);
        for(int[] players:new int[][]{{0,1},{1,0}}) {
            SourceAircraftTrail trail=new SourceAircraftTrail(SourceAircraftTrail.Origin.PAIRED_PLANE,new ReferenceRandom(0));
            SourceAircraftTrailDisplay display=new SourceAircraftTrailDisplay(new BattlefieldDisplayState(),trail,players[0],0,players[1],()->0.5);
            trail.tick(0,0,200,100,45); display.afterUnitFrame();
            float x=(float)trail.getX(0);
            assertEquals(BattlefieldCityLayout.sourceX(fourByThree,x,players[0]),
                    project(fourByThree,trail,display,0),0f);
            float from=BattlefieldCityLayout.sourceX(phone,x,players[0]);
            float to=BattlefieldCityLayout.sourceX(phone,x,players[1]);
            assertNotEquals(from,to,0.01f);
            assertEquals((from+to)/2f,project(phone,trail,display,0),0.0001f);
        }
    }
    private static float project(Rectangle bounds,SourceAircraftTrail trail,
                                 SourceAircraftTrailDisplay display,int slot) {
        return BattlefieldCityLayout.sourceXBetween(bounds,(float)trail.getX(slot),
                display.attackerPlayerId,display.targetPlayerId,display.getBirthCropBlend(slot));
    }
}
