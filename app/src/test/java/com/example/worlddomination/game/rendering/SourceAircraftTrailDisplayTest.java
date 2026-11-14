package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

/** Source smoke attaches after all legal Unit constructors, at the root's next depth. */
public final class SourceAircraftTrailDisplayTest {
    @Test
    public void legalPlaneAndShipRecordsSetPhysicalTrailNamesAndDepths() throws Exception {
        verifyFirstBirth("plane1", gameReadyForPlane(true), 22513, 1, 0);
        verifyFirstBirth("plane1-warhead1", gameReadyForPlane(false), 22513, 1, 0);
        verifyFirstBirth("ship-intercept", gameReadyForShip(1), 800027, 3, 1);
        verifyFirstBirth("ship-success", gameReadyForShip(2), 800027, 3, 1);
    }

    private static void verifyFirstBirth(String caseName, GameState game,
                                         int sourceFirstDepth, int shipRecords,
                                         int tankRecords) throws Exception {
        game.confirmTurn();
        BattlefieldDisplayState display = new BattlefieldDisplayState();
        display.reset(battleFrame(), game, true);
        SourceBattleUnitAllocation allocation = new SourceBattleUnitAllocation(display);
        int firstAircraftParameter = -1;
        int aircraftRecords = 0;
        int actualTankRecords = 0;
        Map<String, FlashBattleRoutes.Point> anchors = null;
        for (GameState.CombatEvent event
                : game.getLastTurnCombatEventsInUnitConstructionOrder()) {
            int preceding = allocation.reserveBeforeEvent(event, aircraftRecords);
            if (FlashBattleRoutes.supportsStandalonePlaneStrike(event)
                    || FlashBattleRoutes.supportsCarrierPlaneStrike(event)) {
                if (firstAircraftParameter < 0) firstAircraftParameter = preceding;
                aircraftRecords++;
            } else if (FlashBattleRoutes.supportsChinookTank(event)) {
                if (anchors == null) anchors = FlashBattleRoutes.parseAnchors(
                        Files.readString(projectAsset("original/runtime/battle_anchors.json")));
                String color = GameSetup.colorName(
                        game.getPlayerState(event.attackerPlayerId).getPlayerColor());
                FlashBattleRoutes.ChinookTankAttack route =
                        FlashBattleRoutes.createSoldierThreeAttack(
                                event.attackerPlayerId, event.defenderPlayerId,
                                event.cityIndex, event.actionBuildingSlot, color,
                                anchors, new ReferenceRandom(0L));
                new SourceChinookDisplayDepths(display,
                        new FlashChinookTankAnimation(route, 0), preceding);
                actualTankRecords++;
            } else {
                fail("Unexpected legal battle record " + event.weaponName);
            }
        }
        assertEquals(caseName + " aircraft record count", shipRecords, aircraftRecords);
        assertEquals(caseName + " AI tank record count", tankRecords, actualTankRecords);
        assertEquals(caseName + " first constructor parameter", 0, firstAircraftParameter);
        if (shipRecords == 3) assertShipRootRoles(display, sourceFirstDepth,
                "ship-intercept".equals(caseName) ? 1 : 2);
        assertEquals(caseName + " occupied parent high water",
                sourceFirstDepth, display.nextDepth());

        JsonValue samples = resourceJson("source-aircraft-trail-" + caseName
                + "-trail-samples.json");
        JsonValue provenance = resourceJson("source-aircraft-trail-provenance.json")
                .get("cases").get(caseName);
        JsonValue first = null;
        for (JsonValue row = samples.child; row != null; row = row.next) {
            if (row.getInt("tick") == provenance.getInt("firstTrailTick")
                    && "unit1".equals(row.getString("unitName"))
                    && row.get("children").size == 3) {
                first = row;
                break;
            }
        }
        assertNotNull(caseName + " source first smoke", first);
        SourceAircraftTrail trail = new SourceAircraftTrail(
                shipRecords == 3 ? SourceAircraftTrail.Origin.SHIP_OFFSET
                        : SourceAircraftTrail.Origin.PREVIOUS_UNIT_POSITION,
                new ReferenceRandom(0L));
        SourceAircraftTrailDisplay owner = new SourceAircraftTrailDisplay(
                display, trail, 0, firstAircraftParameter);
        for (int frame = 0; frame < 4; frame++) {
            trail.tick(0, 0, 0, 0, 0);
            owner.afterUnitFrame();
        }
        assertEquals(sourceFirstDepth, display.nextDepth());
        trail.tick(0, 0, 0, 0, 0);
        owner.afterUnitFrame();
        for (int slot = 0; slot < 3; slot++) {
            JsonValue source = first.get("children").get(slot);
            int depth = sourceFirstDepth + slot;
            if (shipRecords == 3) {
                // The trail sample is the untouched literal host. The one
                // explicit tank linkage repair adds one occupied root depth.
                assertEquals("literal ship smoke", 800026 + slot,
                        source.getInt("depth"));
                assertEquals("alias smoke follows literal by one", 1,
                        depth - source.getInt("depth"));
            } else {
                assertEquals(source.getInt("depth"), depth);
            }
            assertEquals(source.getString("name"), "unit1trail" + slot);
            assertEquals(depth, trail.getDepth(slot));
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry =
                    display.clips().atDepth(depth);
            assertNotNull(caseName + " smoke slot " + slot, entry);
            assertSame(owner, entry.getValue().owner);
            assertEquals(BattlefieldDisplayState.Kind.EFFECT, entry.getValue().kind);
            assertEquals(source.getString("name"), entry.getValue().name);
        }
        assertEquals(sourceFirstDepth + 3, display.nextDepth());

        trail.removeTrail();
        owner.afterUnitFrame();
        for (int slot = 0; slot < 3; slot++) {
            assertNull(display.clips().atDepth(sourceFirstDepth + slot));
            assertEquals("source slot keeps its original assigned depth",
                    sourceFirstDepth + slot, trail.getDepth(slot));
        }
        // getNextHighestDepth may reclaim a removed high depth; the old slots
        // remain created and never reattach to a reused display-list address.
        assertEquals(sourceFirstDepth, display.nextDepth());
        owner.afterUnitFrame();
        assertNull(display.clips().atDepth(sourceFirstDepth));
        display.clear();
        assertEquals(0, display.clips().size());
        assertEquals(0, display.nextDepth());
    }

    private static JsonValue resourceJson(String name) throws Exception {
        try (InputStream input = SourceAircraftTrailDisplayTest.class.getClassLoader()
                .getResourceAsStream(name)) {
            assertNotNull(name, input);
            return new JsonReader().parse(new InputStreamReader(input,
                    StandardCharsets.UTF_8));
        }
    }

    private static void assertShipRootRoles(BattlefieldDisplayState display,
                                            int firstTrailDepth,
                                            int targetPlayer) throws Exception {
        byte[] bytes;
        try (InputStream input = SourceAircraftTrailDisplayTest.class.getClassLoader()
                .getResourceAsStream("source-bio-ship-root-t0.json")) {
            assertNotNull(input);
            bytes = input.readAllBytes();
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte part : digest) hex.append(String.format("%02x", part & 0xff));
        assertEquals("source root occupancy fixture bytes",
                "62f89eea160c3623ed8eb4f078959e97f9671edc9d7492088ac4b0ee11987c66",
                hex.toString());
        JsonValue source = new JsonReader().parse(new String(bytes,
                StandardCharsets.UTF_8));
        assertTrue(source.getBoolean("literalTankNoShadeMissing"));
        assertEquals("literal control remains unmodified", 800025,
                source.getInt("maxDepth"));

        byte[] aliasBytes;
        try (InputStream input = SourceAircraftTrailDisplayTest.class.getClassLoader()
                .getResourceAsStream("source-bio-ship-alias-root-t0.json")) {
            assertNotNull(input);
            aliasBytes = input.readAllBytes();
        }
        digest = MessageDigest.getInstance("SHA-256").digest(aliasBytes);
        hex = new StringBuilder(digest.length * 2);
        for (byte part : digest) hex.append(String.format("%02x", part & 0xff));
        assertEquals("repaired source root fixture bytes",
                "a013528f05fb777660e4c3e5fe6953c2349c3c4c5476d9275627c8c5fb14acdc",
                hex.toString());
        JsonValue repaired = new JsonReader().parse(new String(aliasBytes,
                StandardCharsets.UTF_8));
        assertTrue(repaired.getBoolean("privateRngEqualLiteralAliasEveryTick"));
        assertTrue(repaired.getBoolean("simulationEventsEqualLiteralAlias"));
        assertTrue(repaired.getBoolean("sharedUnitAndCityStateEqualLiteralAliasEveryTick"));
        JsonValue caseData = null;
        for (JsonValue candidate = repaired.get("cases").child;
             candidate != null; candidate = candidate.next) {
            if (candidate.getInt("targetPlayer") == targetPlayer) caseData = candidate;
        }
        assertNotNull(caseData);
        assertEquals("repaired source next smoke depth", firstTrailDepth,
                caseData.get("alias").get("firstSmoke").getInt("depth"));
        assertEquals("repaired source root high water", firstTrailDepth - 1,
                caseData.get("alias").getInt("maxInitialDepth"));
        assertEquals("literal smoke retained as control", 800026,
                caseData.get("literal").get("firstSmoke").getInt("depth"));
        String[] critical = {"targetDotunit4", "unit5", "targetDotunit5", "unit6",
                "shadow_unit6", "targetDotunit6"};
        for (String name : critical) {
            JsonValue role = null;
            for (JsonValue entry = caseData.get("alias").get("roles").child;
                 entry != null; entry = entry.next) {
                if (name.equals(entry.getString("name"))) role = entry;
            }
            assertNotNull("missing source root role " + name, role);
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> nativeRole =
                    display.clips().atDepth(role.getInt("depth"));
            assertNotNull("missing native depth for " + name, nativeRole);
            assertEquals("source root role depth " + role.getInt("depth"),
                    name, nativeRole.getValue().name);
        }
        // The native alias maps the missing literal tank_noShade to symbol714;
        // it must match this separately captured repaired display graph.
    }

    private static FlashAnimationTimeline.Frame battleFrame() throws Exception {
        try (InputStream file = Files.newInputStream(projectAsset(
                "original/runtime/scene.json.gz"));
             InputStream gzip = new GZIPInputStream(file);
             InputStreamReader reader = new InputStreamReader(gzip,
                     StandardCharsets.UTF_8)) {
            JsonValue scene = new JsonReader().parse(reader);
            for (JsonValue symbol = scene.get("symbols").child;
                 symbol != null; symbol = symbol.next) {
                if (symbol.getInt("id") == 5766)
                    return FlashAnimationTimeline.fromSymbol(symbol, 24f).getFrame(0);
            }
        }
        throw new AssertionError("Missing source battle root");
    }

    private static Path projectAsset(String relative) {
        Path module = Paths.get("app", "src", "main", "assets", relative);
        return Files.isRegularFile(module) ? module : Paths.get("src", "main", "assets", relative);
    }

    static GameState newGame() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        return new GameState(setup);
    }

    static GameState gameReadyForPlane(boolean biological) {
        GameState game = newGame();
        rejectDiplomacy(game); produce(game, "planes"); resources(game); game.confirmTurn();
        rejectDiplomacy(game); prepare(game, "plane1"); resources(game); game.confirmTurn();
        rejectDiplomacy(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedLaunch(1, 2,
                biological ? "bioload1" : "warhead1"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        assertEquals(2, game.getQueuedActionCount());
        return game;
    }

    static GameState gameReadyForShip(int target) {
        GameState game = newGame();
        for (String group : new String[]{"rocket", "warhead", "bioload", "soldier"}) {
            for (int level = 2; level <= 4; level++) {
                rejectDiplomacy(game);
                research(game, group + level); resources(game); game.confirmTurn();
            }
        }
        for (String technology : new String[]{"sub", "ship"}) {
            rejectDiplomacy(game);
            research(game, technology); resources(game); game.confirmTurn();
        }
        rejectDiplomacy(game); produce(game, "special"); resources(game); game.confirmTurn();
        for (int turn = 0; turn < 4; turn++) {
            rejectDiplomacy(game);
            produce(game, "bioload"); resources(game); game.confirmTurn();
        }
        rejectDiplomacy(game); prepare(game, "ship"); resources(game); game.confirmTurn();
        assertEquals(20, game.getTurn());
        rejectDiplomacy(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        for (int city = 0; city < 3; city++)
            assertTrue(game.configurePreparedShipStrike(target, city, "bioload3"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        return game;
    }

    static void rejectDiplomacy(GameState game) {
        while (game.getPendingHumanDiplomacyProposal() != null)
            assertTrue(game.respondToDiplomacyProposal(false));
    }

    private static void research(GameState game, String technology) {
        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology(technology));
        assertTrue(game.acceptSelectedAction());
    }

    private static void produce(GameState game, String category) {
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction(category, 100));
        assertTrue(game.acceptSelectedAction());
    }

    static void prepare(GameState game, String carrier) {
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedCarrier(carrier));
        assertTrue(game.acceptSelectedAction());
    }

    static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }
}
