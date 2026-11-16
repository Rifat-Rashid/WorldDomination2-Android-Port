package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.Assert.*;

/** Source bomber Unit replay, isolated preparation View preStart/preLocation compatibility transfer. */
public final class SourceFiniteBomberPlaybackTest {
    @Test public void legalSuccessMatchesAll560HostStates() throws Exception {
        trajectory("success",false,272,560,475,-1,0);
    }
    @Test public void legalInterceptMatchesAll364HostStates() throws Exception {
        trajectory("intercept",true,195,364,265,264,69);
    }
    @Test public void firstPairedSmokeUsesCapturedPrivateRandomAndSourcePose() throws Exception {
        firstSmoke("success",false,272,22519);
        firstSmoke("intercept",true,195,22517);
    }
    @Test public void compactFixturesAndProvenanceArePinned() throws Exception {
        pin("success","hosts.tsv","28b04269ab9958890109b7e7251903510c22394d3947f6bdd66061fdbe47b135");
        pin("success","smoke.tsv","6afbfd296522ab92bb8b2c70b74e8dd88f6c741fd6cd29ec35a254f4a162551e");
        pin("success","rng.json","5c6886aaa9fb3bd0bb0671599cd172e9c03d6092173e6d4d8c710aa0c5f15e85");
        pin("success","provenance.json","d0b6e6a9261866f1c3abbbe54cc444b466097e0be26b936d548ea58bc2c2c5b9");
        pin("intercept","hosts.tsv","9519776263341cab4267ea403a83328e2572855d7a00a8ecf8ee023d719b3e25");
        pin("intercept","smoke.tsv","a137469b781e51a916f6f5508c23770ec3557ffec0bf041200a30d90b8b50fbc");
        pin("intercept","rng.json","f87f07d28c677d95c534599f7af88d5dd591f1e2af2d4e8e92603e067f886b79");
        pin("intercept","provenance.json","4da62119a6a9747841f82046f4d82e9bdbff7ecf6e76883186bd5517bb90482d");
        assertEquals(730,rows("success","smoke.tsv").size());
        assertEquals(602,rows("intercept","smoke.tsv").size());
    }
    private static void trajectory(String name,boolean intercepted,int activation,int total,
                                   int removal,int death,int antiCount) throws Exception {
        SourceAircraftPlayback core=SourceAircraftPlayback.forPreparedLaunch(route(intercepted),null);
        List<Map<String,String>> rows=rows(name,"hosts.tsv"); assertEquals(total,rows.size());
        int anti=0,observedDeath=-1,observedRemoval=-1;
        for(int tick=0;tick<rows.size();tick++) {
            Map<String,String> r=rows.get(tick); String context=name+" host "+tick;
            assertEquals(context,tick,integer(r,"tick"));
            if(tick>0 && tick<activation) core.presentWaitingFrame();
            if(tick>=activation && !core.isRemoved()) core.tickSourceFrame();
            // The terminal kill callback is pre-move and does not perform the
            // generic View post-move overwrite; the fixture marks only the latter.
            if(bool(r,"postMoveAnti")) { core.applySourceAntiDirection(130.75d,91.75d); anti++; }
            if(core.takeDeath()) {
                observedDeath=tick;
                assertEquals(context+" pre-move explosion X",232.97812668284905,core.getDeathEffectX(),1e-4);
                assertEquals(context+" pre-move explosion Y",147.20742173674918,core.getDeathEffectY(),1e-4);
            }
            if(core.isRemoved() && observedRemoval<0) observedRemoval=tick;
            number(context,r,"curX",core.getX()); number(context,r,"curY",core.getY());
            assertEquals(context+" angle",real(r,"storedAngle"),core.getAngle(),1e-6);
            number(context,r,"mcX",core.getRenderX()); number(context,r,"mcY",core.getRenderY());
            assertEquals(context+" frame",integer(r,"mcFrame"),core.getFrame());
            assertEquals(context+" action",r.get("action"),core.getAction());
            assertEquals(context+" status",r.get("status"),core.getUnitStatus());
            assertEquals(context+" moving",bool(r,"activeAndMoving"),core.isActiveAndMoving());
            assertEquals(context+" retained wrapper",bool(r,"active"),!core.isRemoved());
        }
        assertEquals(antiCount,anti); assertEquals(removal,observedRemoval); assertEquals(death,observedDeath);
    }
    private static void firstSmoke(String name,boolean intercepted,int activation,int firstDepth) throws Exception {
        ReferenceRandom random=restore(name);
        SourceAircraftTrail trail=new SourceAircraftTrail(SourceAircraftTrail.Origin.PAIRED_PLANE,random);
        SourceAircraftPlayback core=SourceAircraftPlayback.forPreparedLaunch(route(intercepted),trail);
        for(int tick=1;tick<activation;tick++) core.presentWaitingFrame();
        core.tickSourceFrame();
        List<Map<String,String>> smoke=rows(name,"smoke.tsv");
        int matched=0;
        for(Map<String,String> r:smoke) {
            if(integer(r,"tick")!=activation) break;
            int slot=integer(r,"slot"); assertTrue(trail.isAttached(slot));
            assertEquals(firstDepth+slot,integer(r,"depth"));
            number(name,r,"x",trail.getX(slot)); number(name,r,"y",trail.getY(slot));
            assertEquals(real(r,"alpha"),trail.getAlpha(slot),1e-7);
            assertEquals(real(r,"sizePlus"),trail.getSizePlus(slot),0d);
            assertEquals(real(r,"alphaMin"),trail.getAlphaMin(slot),0d);
            matched++;
        }
        assertEquals(2,matched); assertEquals(2,trail.getCreatedCount());
        Map<String,String> source=rows(name,"hosts.tsv").get(activation);
        assertEquals(name+" source RNG after first pair",real(source,"rngNext"),random.copy().nextDouble(),0d);
    }
    private static FlashBattleRoutes.StandalonePlaneStrike route(boolean intercepted) {
        return FlashBattleRoutes.createStandalonePlaneStrike(0,1,2,1,"plane2",intercepted);
    }
    private static ReferenceRandom restore(String name) throws Exception {
        JsonValue data=new JsonReader().parse(new String(resource(name,"rng.json"),StandardCharsets.UTF_8));
        ReferenceRandom random=new ReferenceRandom(0L); Field f=ReferenceRandom.class.getDeclaredField("state"); f.setAccessible(true);
        int[] state=(int[])f.get(random); JsonValue words=data.get("stateWords"); assertEquals(624,words.size);
        for(int i=0;i<state.length;i++) state[i]=(int)words.get(i).asLong();
        f=ReferenceRandom.class.getDeclaredField("stateIndex"); f.setAccessible(true); f.setInt(random,data.getInt("stateIndex")); return random;
    }
    private static List<Map<String,String>> rows(String name,String suffix) throws Exception {
        List<Map<String,String>> result=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(new ByteArrayInputStream(resource(name,suffix)),StandardCharsets.UTF_8))) {
            String[] header=reader.readLine().split("\t",-1);
            for(String line;(line=reader.readLine())!=null;) {String[] values=line.split("\t",-1); assertEquals(header.length,values.length); Map<String,String> row=new HashMap<>(); for(int i=0;i<header.length;i++)row.put(header[i],values[i]);result.add(row);}
        } return result;
    }
    private static byte[] resource(String name,String suffix) throws Exception {
        String path="source-finite-bomber-"+name+"-"+suffix;
        try(InputStream in=SourceFiniteBomberPlaybackTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(path,in);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return out.toByteArray();
        }
    }
    private static void pin(String name,String suffix,String expected) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(resource(name,suffix));StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(Locale.ROOT,"%02x",b&255));assertEquals(expected,s.toString());
    }
    private static int integer(Map<String,String> r,String k){return Integer.parseInt(r.get(k));}
    private static double real(Map<String,String> r,String k){return Double.parseDouble(r.get(k));}
    private static boolean bool(Map<String,String> r,String k){return Boolean.parseBoolean(r.get(k));}
    private static void number(String ctx,Map<String,String> r,String k,double value){assertEquals(ctx+" "+k,real(r,k),value,1e-4);}
}
