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

/** Seed-32 legal pass-defense host replay; source transferred preparation View preStart only. */
@RunWith(AndroidJUnit4.class)
public final class SourcePassedRocketDefenseClockInstrumentedTest {
    @Test public void successfulStrikeWithActiveDefenseMatches342HostTicks() throws Exception { verify(); }

    private static void verify() throws Exception {
        String scenario = "pass";
        List<Map<String,String>> ticks = read("source-silo-pass-ticks.tsv");
        List<Map<String,String>> defense = ticks;
        List<Map<String,String>> trails = read("source-silo-pass-trails.tsv");
        assertEquals(342, ticks.size());
        Map<Integer,List<Map<String,String>>> byTick = new HashMap<>();
        for (Map<String,String> row : trails)
            byTick.computeIfAbsent(integer(row,"tick"), k -> new ArrayList<>()).add(row);
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            GameState game = initial();
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
            assertEquals(22,game.getTurn());
            GameState.CombatEvent strike=null;
            for(GameState.CombatEvent event:game.getLastTurnCombatEvents())
                if(event.attackerPlayerId==0 && "rocket1".equals(event.weaponName)) strike=event;
            assertNotNull("legal rocket event",strike);
            assertEquals(GameState.CombatEventType.STRIKE_IMPACT,strike.type);
            assertEquals("antiRocket",strike.counterWeaponName);
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
            assertEquals("physical antiRocket group",1,groups.size());
            SourceRocketDefenseController group=groups.get(0);
            SourceRocketDefenseController.UnitVisit visit=null;
            {
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
                {
                    Map<String,String> d=defense.get(tick);
                    assertEquals(context,tick,integer(d,"tick"));
                    assertEquals(context+" countdown",integer(d,"countdown"),visit.state.getCountdown());
                    if(!d.get("offset").isEmpty()) assertEquals(context+" offset",integer(d,"offset"),visit.state.getOffset());
                    number(context,d,"antiX",visit.getAntiX()); number(context,d,"antiY",visit.getAntiY());
                    assertEquals(context+" turret frame",integer(d,"turretFrame"),group.getPlayback().getTurretFrame());
                    assertEquals(context+" physical nested turret",integer(d,"turretFrame"),group.getBuilding().getNestedFrameIndex(5035,52)+1);
                    number(context,d,"turretAlpha",group.getPlayback().getTurretAlphaPercent());
                    assertEquals(context+" turret playing",bool(d,"turretPlaying"),group.getPlayback().isTurretPlaying());
                    if(tick < 341 && "unit1".equals(d.get("defenderLock"))) assertSame(context+" lock",rocket,group.getLockedTarget());
                    else if(tick < 341) assertNull(context+" lock",group.getLockedTarget());
                }
                assertEquals(context+" private RNG",Double.parseDouble(row.get("sourcePrivateRngNext")),privateRng.copy().nextDouble(),0d);
                assertEquals(context+" canonical RNG",canonical,game.createPresentationRandomCopy().nextDouble(),0d);
                assertEquals(context+" source canonical RNG",Double.parseDouble(row.get("sourceCanonicalRngNext")),canonical,0d);
            }
            assertFalse("battle handed off",cities.isBattleActive());
            assertEquals("city impact is never a self kill",0,core.getSelfExplosionCount());
            assertTrue("defense released at handoff",groups.isEmpty());
        });
    }
    private static GameState initial() {
        GameSetup setup=new GameSetup(32L); setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0)); assertTrue(setup.toggleOpponent(1));
        GameState game=new GameState(setup);
        {
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
