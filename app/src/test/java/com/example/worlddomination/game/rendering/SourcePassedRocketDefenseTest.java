package com.example.worlddomination.game.rendering;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.Assert.*;

/** Seed 32 source replay: only preparation View preStart is transferred across isolated hosts. */
public final class SourcePassedRocketDefenseTest {
    @Test public void callbackAndHostClockMatchSourceAtEveryVisit() throws Exception {
        List<Map<String,String>> hosts=read("source-silo-pass-ticks.tsv");
        List<Map<String,String>> callbacks=read("source-silo-pass-callbacks.tsv");
        assertEquals(342,hosts.size()); assertEquals(122,callbacks.size());
        SourceAntiDefensePlayback model=SourceAntiDefensePlayback.forRocketReligion("chr");
        SourceAntiDefensePlayback.UnitState unit=new SourceAntiDefensePlayback.UnitState();
        int next=0, killed=0; double canonical=real(hosts.get(0),"sourceCanonicalRngNext");
        for(int tick=0;tick<hosts.size();tick++) {
            while(next<callbacks.size() && integer(callbacks.get(next),"tick")==tick) {
                Map<String,String> row=callbacks.get(next++);
                String context="tick "+tick+" callback "+next;
                boolean dead=bool(row,"dead");
                if(dead && killed==0) { unit.markKilled(); killed++; }
                assertEquals(context+" pre-frame",integer(row,"beforeFrame"),model.getTurretFrame());
                assertEquals(context+" pre-alpha",real(row,"beforeAlpha"),model.getTurretAlphaPercent(),0d);
                assertEquals(context+" pre-playing",bool(row,"beforePlaying"),model.isTurretPlaying());
                assertEquals(context+" pre-countdown",integer(row,"beforeCountdown"),unit.getCountdown());
                assertEquals(context+" pre-offset",optionalInteger(row,"beforeOffset"),unit.getOffset());
                assertEquals(context+" RNG unchanged",real(row,"beforeRng"),real(row,"afterRng"),0d);
                model.invoke(unit,true,real(row,"x"),real(row,"y"),real(row,"antiX"),real(row,"antiY"),dead);
                assertEquals(context+" frame",integer(row,"afterFrame"),model.getTurretFrame());
                assertEquals(context+" alpha",real(row,"afterAlpha"),model.getTurretAlphaPercent(),0d);
                assertEquals(context+" playing",bool(row,"afterPlaying"),model.isTurretPlaying());
                assertEquals(context+" countdown",integer(row,"afterCountdown"),unit.getCountdown());
                assertEquals(context+" offset",optionalInteger(row,"afterOffset"),unit.getOffset());
                assertEquals(context+" raw angle",integer(row,"afterRawAngle"),unit.getRawBearingDegrees());
            }
            model.advanceHostFrame();
            Map<String,String> row=hosts.get(tick); String context="host "+tick;
            assertEquals(context,tick,integer(row,"tick"));
            assertEquals(context+" frame",integer(row,"turretFrame"),model.getTurretFrame());
            assertEquals(context+" alpha",real(row,"turretAlpha"),model.getTurretAlphaPercent(),0d);
            assertEquals(context+" playing",bool(row,"turretPlaying"),model.isTurretPlaying());
            assertEquals(context+" canonical RNG frozen",canonical,real(row,"sourceCanonicalRngNext"),0d);
        }
        assertEquals(122,next); assertEquals(1,killed);
        assertEquals("Unit.moveUnit/Unit.killUnit",callbacks.get(119).get("origin"));
        assertEquals(278,integer(callbacks.get(119),"tick"));
        assertEquals(278,integer(callbacks.get(120),"tick"));
        assertEquals(279,integer(callbacks.get(121),"tick"));
    }

    @Test public void compactOracleBytesAndBoundariesArePinned() throws Exception {
        sha("source-silo-pass-ticks.tsv","0249cfeb22ac4f8d203298f373e95af471afd062e6258d5bdd1147cf5e4e0548");
        sha("source-silo-pass-trails.tsv","844fa11263aa45d9ef103786797dd910525b32bc9aff67a9335401e71f14ea9a");
        sha("source-silo-pass-callbacks.tsv","db12012b4c7f5ab332a3fa715590e8fe99deeda46a77c84e435f7b0c12cee29e");
        List<Map<String,String>> rows=read("source-silo-pass-ticks.tsv");
        assertEquals("rocket_follow_ellipse",rows.get(214).get("action"));
        assertEquals("rocket_explode_city",rows.get(277).get("action"));
        assertEquals("dead",rows.get(278).get("status"));
        assertEquals(-1,integer(rows.get(279),"activeIndex"));
        assertEquals(1006,read("source-silo-pass-trails.tsv").size());
    }
    private static void sha(String name,String expected) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=SourcePassedRocketDefenseTest.class.getResourceAsStream("/"+name)) {
            assertNotNull(name,in); byte[] buf=new byte[8192]; for(int n;(n=in.read(buf))!=-1;) md.update(buf,0,n);
        }
        StringBuilder value=new StringBuilder(); for(byte b:md.digest()) value.append(String.format("%02x",b&255));
        assertEquals(name,expected,value.toString());
    }
    private static List<Map<String,String>> read(String name) throws Exception {
        List<Map<String,String>> rows=new ArrayList<>();
        try(InputStream in=SourcePassedRocketDefenseTest.class.getResourceAsStream("/"+name)) {
            assertNotNull(name,in);
            try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {
                String[] header=r.readLine().split("\t",-1);
                for(String line;(line=r.readLine())!=null;) { String[] c=line.split("\t",-1); assertEquals(header.length,c.length); Map<String,String> row=new HashMap<>(); for(int i=0;i<c.length;i++) row.put(header[i],c[i]); rows.add(row); }
            }
        }
        return rows;
    }
    private static int integer(Map<String,String> row,String k){return Integer.parseInt(row.get(k));}
    private static int optionalInteger(Map<String,String> row,String k){return row.get(k).isEmpty()?0:integer(row,k);}
    private static double real(Map<String,String> row,String k){return Double.parseDouble(row.get(k));}
    private static boolean bool(Map<String,String> row,String k){return Boolean.parseBoolean(row.get(k));}
}
