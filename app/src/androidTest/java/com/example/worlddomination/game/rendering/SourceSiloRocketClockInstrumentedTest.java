package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Legal world-clock replays; Python fixtures transfer only the preparation View's preStart.
 * The defense TSV is a column-preserving extract of source CSV SHA-256
 * 24ec582aedf0ca220c18be2930c159fbaff679a2aa0dfd433ae2fd38050d03a0;
 * extract SHA-256 72c3d31be23eee597d412618e039ffbcabc42838e2861b72486501979a3b526f.
 */
@RunWith(AndroidJUnit4.class)
public final class SourceSiloRocketClockInstrumentedTest {
    @Test public void earlySuccessfulStrikeMatches341HostTicks() throws Exception { verify(false); }
    @Test public void lateInterceptedStrikeMatches646HostTicks() throws Exception { verify(true); }

    private static void verify(boolean failed) throws Exception {
        String scenario = failed ? "failed" : "success";
        List<Map<String,String>> ticks = read("source-silo-rocket-" + scenario + "-ticks.tsv");
        List<Map<String,String>> defense = failed ? read("source-silo-rocket-failed-defense.tsv") : Collections.emptyList();
        if (failed) assertEquals(646, defense.size());
        List<Map<String,String>> trails = read("source-silo-rocket-" + scenario + "-trails.tsv");
        assertEquals(failed ? 646 : 341, ticks.size());
        Map<Integer,List<Map<String,String>>> byTick = new HashMap<>();
        for (Map<String,String> row : trails)
            byTick.computeIfAbsent(integer(row,"tick"), k -> new ArrayList<>()).add(row);
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = initial(failed);
            field(WorldDominationGame.class,"game").set(app,game);
            BattlefieldCityRenderer cities = (BattlefieldCityRenderer) field(WorldDominationGame.class,"cityRenderer").get(app);
            cities.reset(game);
            ((SourceBattleRecordClock) field(WorldDominationGame.class,"battleRecordClock").get(app)).reset();
            Method fixed = WorldDominationGame.class.getDeclaredMethod("fixedUpdate",float.class);
            fixed.setAccessible(true);
            game.selectAction(GameState.Action.PREPARED_ATTACK);
            assertTrue(game.configurePreparedCarrier("rocket1"));
            assertTrue(game.acceptSelectedAction());
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue(game.acceptSelectedAction());
            invoke(app,"confirmTurnAndPreparePlayback");
            for (int i=0; i<1200 && cities.isBattleActive(); i++) fixed.invoke(app,1f/24f);
            assertFalse("legal preparation playback completes",cities.isBattleActive());
            game.selectAction(GameState.Action.PREPARED_ATTACK);
            assertTrue(game.configurePreparedLaunch(1,2,"bioload1"));
            assertTrue(game.acceptSelectedAction());
            game.selectAction(GameState.Action.RESOURCES);
            assertTrue(game.acceptSelectedAction());
            invoke(app,"confirmTurnAndPreparePlayback");
            assertEquals(failed ? 22 : 2,game.getTurn());
            @SuppressWarnings("unchecked") List<FlashRocketAnimation> rockets =
                    (List<FlashRocketAnimation>) field(WorldDominationGame.class,"rocketBattleAnimations").get(app);
            FlashRocketAnimation rocket = null;
            for (FlashRocketAnimation candidate : rockets)
                if (!candidate.getRoute().submarineLaunch && candidate.targetPlayerId()==1) rocket=candidate;
            assertNotNull("source silo animation",rocket);
            SourceRocketPlayback core=rocket.getSourcePlayback();
            assertNotNull("silo uses source Unit",core);
            @SuppressWarnings("unchecked") List<SourceRocketDefenseController> groups =
                    (List<SourceRocketDefenseController>) field(WorldDominationGame.class,"sourceRocketDefenseControllers").get(app);
            assertEquals("physical antiRocket group",failed ? 1 : 0,groups.size());
            SourceRocketDefenseController group=failed ? groups.get(0) : null;
            SourceRocketDefenseController.UnitVisit visit=null;
            if(failed) {
                @SuppressWarnings("unchecked") List<SourceRocketDefenseController.UnitVisit> units =
                        (List<SourceRocketDefenseController.UnitVisit>) field(SourceRocketDefenseController.class,"units").get(group);
                assertEquals(1,units.size()); visit=units.get(0);
                assertSame(rocket,visit.animation);
                assertEquals(1,group.getDefenderPlayerId());
            }
            ReferenceRandom privateRng=(ReferenceRandom) field(WorldDominationGame.class,"battlePresentationRandom").get(app);
            assertNotNull(privateRng);
            double canonical=game.createPresentationRandomCopy().nextDouble();
            for (int tick=0;tick<ticks.size();tick++) {
                if (tick>0) fixed.invoke(app,1f/24f);
                Map<String,String> row=ticks.get(tick);
                String context=scenario+" tick "+tick;
                assertEquals(context,tick,integer(row,"tick"));
                assertEquals(context+" action",row.get("action"),core.getAction());
                assertEquals(context+" status",row.get("status"),core.getUnitStatus());
                number(context,row,"logicalX",core.getX()); number(context,row,"logicalY",core.getY());
                number(context,row,"renderX",core.getRenderX()); number(context,row,"renderY",core.getRenderY());
                number(context,row,"xStep",core.getXStep()); number(context,row,"yStep",core.getYStep());
                number(context,row,"ellipseAngle",core.getEllipseAngle());
                assertEquals(context+" body",bool(row,"bodyAttached"),core.isBodyAttached());
                assertEquals(context+" frame",integer(row,"bodyFrame"),core.getSourceFrame());
                assertEquals(context+" angle",integer(row,"rawAngle"),core.getRawAngle());
                assertEquals(context+" active wrapper",integer(row,"activeIndex")>=0,!core.isRemoved());
                assertEquals(context+" attached trail count",integer(row,"attachedTrailCount"),core.getAttachedTrailCount());
                boolean[] attached=new boolean[core.getTrailSlotCount()];
                List<Map<String,String>> smoke=byTick.get(tick);
                if(smoke!=null) for(Map<String,String> puff:smoke) {
                    int slot=integer(puff,"slot"); attached[slot]=true;
                    assertTrue(context+" puff "+slot,core.isTrailAttached(slot));
                    assertEquals(context+" depth",integer(puff,"depth"),core.getTrailDepth(slot));
                    number(context,puff,"x",core.getTrailX(slot)); number(context,puff,"y",core.getTrailY(slot));
                    number(context,puff,"alpha",core.getTrailAlpha(slot));
                    number(context,puff,"sizePlus",core.getTrailSizePlus(slot));
                    number(context,puff,"alphaMin",core.getTrailAlphaMin(slot));
                }
                for(int slot=0;slot<attached.length;slot++) assertEquals(context+" slot "+slot,attached[slot],core.isTrailAttached(slot));
                if (failed) {
                    Map<String,String> d=defense.get(tick);
                    assertEquals(context,tick,integer(d,"tick"));
                    assertEquals(context+" countdown",integer(d,"countdown"),visit.state.getCountdown());
                    if(!d.get("offset").isEmpty()) assertEquals(context+" offset",integer(d,"offset"),visit.state.getOffset());
                    number(context,d,"antiX",visit.getAntiX()); number(context,d,"antiY",visit.getAntiY());
                    assertEquals(context+" turret frame",integer(d,"turretFrame"),group.getPlayback().getTurretFrame());
                    assertEquals(context+" physical nested turret",integer(d,"turretFrame"),group.getBuilding().getNestedFrameIndex(5035,52)+1);
                    number(context,d,"turretAlpha",group.getPlayback().getTurretAlphaPercent());
                    assertEquals(context+" turret playing",bool(d,"turretPlaying"),group.getPlayback().isTurretPlaying());
                    if("unit1".equals(d.get("defenderLock"))) assertSame(context+" lock",rocket,group.getLockedTarget());
                    else assertNull(context+" lock",group.getLockedTarget());
                }
                assertEquals(context+" private RNG",Double.parseDouble(row.get("sourcePrivateRngNext")),privateRng.copy().nextDouble(),0d);
                assertEquals(context+" canonical RNG",canonical,game.createPresentationRandomCopy().nextDouble(),0d);
            }
            assertFalse("battle handed off",cities.isBattleActive());
        });
    }
    private static GameState initial(boolean failed) {
        GameSetup setup=new GameSetup(0L); setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0)); assertTrue(setup.toggleOpponent(1));
        GameState game=new GameState(setup);
        if(failed) {
            String[] research={"rocket2","rocket3","rocket4","warhead2","warhead3","warhead4","bioload2","bioload3","bioload4","soldier2","soldier3","soldier4","sub","ship"};
            for(String tech:research) { decline(game); game.selectAction(GameState.Action.RESEARCH); assertTrue(game.selectResearchTechnology(tech)); endTurn(game); }
            String[] production={"special","bioload","bioload","bioload","bioload","rockets"};
            for(String item:production) { decline(game); game.selectAction(GameState.Action.PRODUCTION); assertTrue(game.configureProduction(item,100)); endTurn(game); }
        }
        decline(game); return game;
    }
    private static void decline(GameState game) { while(game.getPendingHumanDiplomacyProposal()!=null) assertTrue(game.respondToDiplomacyProposal(false)); }
    private static void endTurn(GameState game) { assertTrue(game.acceptSelectedAction()); game.selectAction(GameState.Action.RESOURCES); assertTrue(game.acceptSelectedAction()); game.confirmTurn(); }
    private static Field field(Class<?> type,String name) throws Exception { Field f=type.getDeclaredField(name); f.setAccessible(true); return f; }
    private static void invoke(Object obj,String name) throws Exception { Method m=obj.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(obj); }
    private static int integer(Map<String,String> row,String name) { return Integer.parseInt(row.get(name)); }
    private static boolean bool(Map<String,String> row,String name) { return Boolean.parseBoolean(row.get(name)); }
    private static void number(String context,Map<String,String> row,String name,double value) { assertEquals(context+" "+name,Double.parseDouble(row.get(name)),value,1e-7); }
    private static List<Map<String,String>> read(String name) throws Exception {
        List<Map<String,String>> result=new ArrayList<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name),StandardCharsets.UTF_8))) {
            String[] columns=reader.readLine().split("\t",-1);
            for(String line;(line=reader.readLine())!=null;) { String[] cells=line.split("\t",-1); assertEquals(columns.length,cells.length); Map<String,String> row=new HashMap<>(); for(int i=0;i<columns.length;i++) row.put(columns[i],cells[i]); result.add(row); }
        }
        return result;
    }
}
