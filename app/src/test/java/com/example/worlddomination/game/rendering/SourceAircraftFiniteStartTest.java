package com.example.worlddomination.game.rendering;

import org.junit.Test;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import static org.junit.Assert.*;

/** Finite preparation View bridge; source host clock is provided by the fixture. */
public final class SourceAircraftFiniteStartTest {
    @Test public void legalSlotOneFighterMatchesAll500SourceHostStates() throws Exception {
        compare("success", false, 234, 500,
                "a28f61eb1f485eb8fab85eec019a6bfccbdd03fe37a098e60140244104f6f7aa",
                "c47269514816cab7192f7ba5082922e72c0bddf8f62b766a74737216ac7ea5c9");
    }

    @Test public void interceptedFighterMatches365StatesThroughDeathAndRemoval() throws Exception {
        compare("intercept", true, 196, 365,
                "8be5bbb881a34764b1a89a01553d62c5fb8ee5e2e7e56b443fd2f3e45964f596",
                "3c3d6ad468996947dc42efee9d80b614379c5794b930cfc71907ef7eed01f251");
    }

    private static void compare(String name, boolean intercepted, int activationTick, int total,
                                String fixtureHash, String provenanceHash) throws Exception {
        FlashBattleRoutes.StandalonePlaneStrike route =
                FlashBattleRoutes.createStandalonePlaneStrike(0,1,intercepted ? 0 : 2,1,
                        "plane1",intercepted);
        SourceAircraftPlayback core=SourceAircraftPlayback.forPreparedLaunch(route,null);
        byte[] fixture=resource("source-aircraft-finite-"+name+".tsv");
        assertEquals(fixtureHash,sha256(fixture));
        assertEquals(provenanceHash,sha256(resource("source-aircraft-finite-"+name+"-provenance.json")));
        int antiCalls=0, removedTick=-1, deathTick=-1;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(fixture),StandardCharsets.UTF_8))) {
            String header="tick\tcurX\tcurY\tstoredAngle\tmcX\tmcY\tmcFrame\taction\tstatus\tactiveAndMoving\tactive";
            assertEquals(intercepted ? header+"\tpostMoveAnti" : header,reader.readLine());
            int count=0;
            for(String line;(line=reader.readLine())!=null;) {
                String[] row=line.split("\t",-1); int tick=Integer.parseInt(row[0]);
                assertEquals(count++,tick);
                // Activation is an independently captured record-clock boundary,
                // not inferred from the row's expected moving flag.
                if(tick>0 && tick<activationTick) core.presentWaitingFrame();
                if(tick>=activationTick && !core.isRemoved()) core.tickSourceFrame();
                if(intercepted && Boolean.parseBoolean(row[11])) {
                    core.applySourceAntiDirection(130.75d,91.75d);
                    antiCalls++;
                }
                if(core.takeDeath()) { assertEquals(-1,deathTick); deathTick=tick; }
                if(core.isRemoved() && removedTick<0) removedTick=tick;
                String context=name+" source host tick "+tick;
                // Route coordinates are stored as floats; source uses decimal doubles.
                assertEquals(context+" x",Double.parseDouble(row[1]),core.getX(),1e-4);
                assertEquals(context+" y",Double.parseDouble(row[2]),core.getY(),1e-4);
                assertEquals(context+" angle",Double.parseDouble(row[3]),core.getAngle(),1e-6);
                assertEquals(context+" paint x",Double.parseDouble(row[4]),core.getRenderX(),1e-4);
                assertEquals(context+" paint y",Double.parseDouble(row[5]),core.getRenderY(),1e-4);
                assertEquals(context+" body frame",Integer.parseInt(row[6]),core.getFrame());
                assertEquals(context+" action",row[7],core.getAction());
                assertEquals(context+" status",row[8],core.getUnitStatus());
                assertEquals(context+" moving",Boolean.parseBoolean(row[9]),core.isActiveAndMoving());
                assertEquals(context+" active",Boolean.parseBoolean(row[10]),!core.isRemoved());
            }
            assertEquals(total,count);
        }
        assertEquals(intercepted ? 84 : 0,antiCalls);
        assertEquals(intercepted ? 280 : -1,deathTick);
        assertEquals(intercepted ? 281 : 437,removedTick);
    }

    private static byte[] resource(String name) throws Exception {
        try(InputStream in=SourceAircraftFiniteStartTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull("Missing fixture "+name,in);
            ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192];
            for(int count;(count=in.read(buffer))!=-1;) out.write(buffer,0,count);
            return out.toByteArray();
        }
    }
    private static String sha256(byte[] bytes) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result=new StringBuilder();
        for(byte b:hash) result.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
        return result.toString();
    }

    @Test public void slotTwoStartsFromPreparedLocationAndPublishesWaitingPose() {
        FlashBattleRoutes.StandalonePlaneStrike route =
                FlashBattleRoutes.createStandalonePlaneStrike(0,1,2,2,"plane1",false);
        SourceAircraftPlayback core=SourceAircraftPlayback.forPreparedLaunch(route,null);
        assertEquals(442.25,core.getX(),0d); assertEquals(296.75,core.getY(),0d);
        assertEquals(0d,core.getRenderX(),0d); assertEquals(0d,core.getRenderY(),0d);
        assertEquals(442.25,core.getShadowRenderX(),0d);
        assertEquals(296.75,core.getShadowRenderY(),0d);
        assertEquals(0d,core.getAngle(),0d);
        assertEquals(1,core.getFrame()); assertEquals(1,core.getTintFrame());
        core.presentWaitingFrame();
        assertEquals(442.25,core.getRenderX(),0d); assertEquals(296.75,core.getRenderY(),0d);
        assertEquals(147d,core.getAngle(),0d); assertEquals(10,core.getFrame());
        assertEquals(10,core.getTintFrame());
        assertEquals(core.getRenderX(),core.getShadowRenderX(),0d);
        assertEquals(core.getRenderY(),core.getShadowRenderY(),0d);
        assertFalse(core.isActiveAndMoving());
        core.tickSourceFrame();
        assertEquals(core.getFrame(),core.getTintFrame());
        for(int i=1;i<12;i++) core.tickSourceFrame();
        assertTrue(core.getShadowYOffset()>0d);
        assertEquals(core.getRenderX(),core.getShadowRenderX(),0d);
        assertEquals(core.getRenderY()+core.getShadowYOffset(),core.getShadowRenderY(),0d);
    }

    @Test public void legacyConstructorRepresentsMissingAdapterPreStart() {
        FlashBattleRoutes.StandalonePlaneStrike route =
                FlashBattleRoutes.createStandalonePlaneStrike(0,1,2,1,"plane1",false);
        SourceAircraftPlayback core=new SourceAircraftPlayback(route,null);
        assertTrue(Double.isNaN(core.getX())); assertTrue(Double.isNaN(core.getY()));
        core.presentWaitingFrame(); assertTrue(Double.isNaN(core.getAngle()));
        assertEquals(2,core.getFrame()); assertEquals(1,core.getTintFrame());
    }
}
