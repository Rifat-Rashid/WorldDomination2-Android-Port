package com.example.worlddomination.game.ai;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

/** Source target-selection inputs after scoring; independent of earlier Java campaign resolution. */
public final class SourceCompanionTargetRetryTest {
    private static final String FIXTURE = "/source-ai-companion-retry.json.gz";
    private static final String SHA256 = "f89488a1ed9042514267e9ad7365e88fb7bdb4d16d766c5e6ec1e7c45aa81119";

    @Test
    public void failedDiplomacyCompanionRetriesBeforeMediaFallback() throws Exception {
        JsonValue capture = load("failed-diplomacy-companion");
        assertTrue("Natural source campaign instrumentation preserves the canonical result",
                capture.get("provenance").getBoolean("canonicalFinalMatchesOriginal"));
        JsonValue calls = capture.get("targetCalls");
        assertEquals("Source first preparation, two rejected companions, then fallback", 4, calls.size);
        for (int index = 1; index <= 2; index++) {
            assertEquals("source companion slot", 2, calls.get(index).get("before").getInt("slot"));
            assertEquals("source repeated candidate", "diplomacy",
                    calls.get(index).get("before").getString("action"));
            assertFalse("source target rejected", calls.get(index).getBoolean("accepted"));
            assertEquals("three eligible source target rolls per attempt", 3, calls.get(index).getInt("drawCount"));
        }
        assertEquals("source fallback", "religion", calls.get(3).get("after").getString("action"));
        assertTrue("source fallback accepted", calls.get(3).getBoolean("accepted"));
        assertPlan(capture);
    }

    @Test
    public void successfulMediaCompanionDoesNotRepeatTargetSelection() throws Exception {
        JsonValue capture = load("successful-media-companion");
        assertFalse("This source control explicitly changes the ranked input",
                capture.get("provenance").getBoolean("canonicalFinalMatchesOriginal"));
        assertEquals("Source preparation followed directly by accepted media", 2,
                capture.get("targetCalls").size);
        assertEquals("Accepted source media needs no random target", 0,
                capture.get("targetCalls").get(1).getInt("drawCount"));
        assertPlan(capture);
    }

    private static void assertPlan(JsonValue capture) throws Exception {
        JsonValue entry = capture.get("entry");
        GameState game = restorePlannerEntry(entry, capture.get("provenance"));
        GameState.PlayerState actor = game.getPlayerState(entry.getInt("actor"));
        assertEquals("Source preparation starts without a ready carrier", "inactive",
                entry.get("players").get(actor.id).getString("preAttackStatus"));
        assertNull(actor.getPreparedCarrierName());
        List<OpponentAI.ScoredAction> ranked = new ArrayList<>();
        for (JsonValue row : entry.get("rankedActions")) {
            ranked.add(new OpponentAI.ScoredAction(action(row.getString("id")), row.getDouble("amount")));
        }
        RecordedRandom random = new RecordedRandom(capture.get("draws"), capture.get("expected").getDouble("nextRandom"));
        assertEquals("First source target-selection draw", entry.getDouble("rngNext"), random.peek(), 0d);
        List<OpponentAI.Decision> decisions = OpponentAI.planScoredActions(game, actor, random, ranked);
        JsonValue expected = capture.get("expected");
        assertEquals("Source fills both action slots", expected.get("actions").size, decisions.size());
        for (int index = 0; index < decisions.size(); index++) {
            JsonValue source = expected.get("actions").get(index);
            OpponentAI.Decision actual = decisions.get(index);
            assertEquals("source actor", actor.id, actual.playerId);
            assertEquals("source action slot " + (index + 1), action(source.getString("action")), actual.action);
            assertEquals("No source budget reserved for either selected action", 0, actual.reservedMillions);
            if (actual.action == GameState.Action.PREPARED_ATTACK) {
                assertTrue("Source selects a new carrier, not an already-ready launch", actual.prepareCarrier);
                assertEquals(source.getString("carrier"), actual.weaponName);
                assertEquals(source.getInt("targetPlayer"), actual.targetPlayerId);
                assertEquals("Preparation has no target city yet", -1, actual.targetCityIndex);
                assertNull(actual.payloadName);
            }
        }
        assertEquals("Source spending budget after target planning", expected.getInt("turnMoney"), actor.turnMoneyMillions);
        assertEquals("Consume every recorded source draw, including both failed companion attempts",
                capture.get("draws").size, random.consumed);
        assertEquals("Exact source stream continuation after fallback", expected.getDouble("nextRandom"),
                random.nextDouble(), 0d);
    }

    /** Restores source planning data only; it never replays the campaign through Java's turn resolver. */
    @SuppressWarnings("unchecked")
    private static GameState restorePlannerEntry(JsonValue entry, JsonValue provenance) throws Exception {
        GameSetup setup = new GameSetup(provenance.getLong("seed"));
        setup.setDifficulty(entry.getInt("difficulty"));
        for (JsonValue opponent : provenance.get("opponents")) assertTrue(setup.toggleOpponent(opponent.asInt()));
        GameState game = new GameState(setup);
        field(GameState.class, "turn").setInt(game, entry.getInt("turn"));
        for (JsonValue source : entry.get("players")) {
            GameState.PlayerState player = game.getPlayerState(source.getInt("id"));
            player.alive = source.getBoolean("alive");
            player.moneyMillions = source.getInt("money");
            double budget = source.getDouble("turnMoney");
            if (Double.isNaN(budget)) player.invalidateAiTurnMoney();
            else player.turnMoneyMillions = (int) budget;
            player.populationMillions = source.getInt("population");
            player.religion = source.getInt("religion");
            player.score = source.getLong("score");
            player.spyReadiness = source.getInt("spyReadiness");
            field(GameState.PlayerState.class, "primaryEnemyId").setInt(player, source.getInt("primaryEnemy"));
            field(GameState.PlayerState.class, "secondaryEnemyId").setInt(player, source.getInt("secondaryEnemy"));
            field(GameState.PlayerState.class, "specialPerson").setBoolean(player, source.getBoolean("specialPerson"));
            field(GameState.PlayerState.class, "suicidePerson").setBoolean(player, source.getBoolean("suicidePerson"));
            field(GameState.PlayerState.class, "lastAttackedByPlayerId").setInt(player,
                    source.get("lastAttackedBy").isNull() ? -1 : source.getInt("lastAttackedBy"));
            field(GameState.PlayerState.class, "lastAttackedByCasualties").setInt(player,
                    source.getInt("lastAttackedByCasualties"));
            field(GameState.PlayerState.class, "satellitesUsed").setInt(player, source.getInt("satellitesUsed"));
            field(GameState.PlayerState.class, "bioloaded").setBoolean(player, source.getBoolean("bioloaded"));
            assertEquals("Captured players have no ready preparation at this boundary", "inactive",
                    source.getString("preAttackStatus"));
            Set<String> technologies = (Set<String>) field(GameState.PlayerState.class, "inventedTechnologies").get(player);
            technologies.clear();
            for (JsonValue technology : source.get("inventedTech")) technologies.add(technology.asString());
            Set<GameState.Action> locks = (Set<GameState.Action>) field(GameState.PlayerState.class, "bioloadedActions").get(player);
            locks.clear();
            for (JsonValue lock : source.get("bioloadedActions")) locks.add(action(lock.asString()));
            Map<String, Integer> desired = (Map<String, Integer>) field(GameState.PlayerState.class, "desiredStock").get(player);
            desired.clear();
            for (JsonValue weapon : source.get("weapons")) {
                GameState.WeaponState actual = player.getWeapons().get(weapon.getString("name"));
                assertNotNull("Source weapon exists in native catalog", actual);
                actual.inStock = weapon.getInt("stock");
                actual.active = weapon.getBoolean("active");
                actual.bioloaded = weapon.getBoolean("bioloaded");
                actual.investmentMillions = weapon.get("investment").isNull() ? 0 : weapon.getInt("investment");
                desired.put(actual.definition.name, weapon.get("desire").isNull() ? 0 : weapon.getInt("desire"));
            }
            for (int city = 0; city < 5; city++) player.getCity(city).populationMillions = source.get("cityPopulations").getInt(city);
            double[] regards = (double[]) field(GameState.PlayerState.class, "regardToward").get(player);
            int[] ceasefires = (int[]) field(GameState.PlayerState.class, "ceasefireTurns").get(player);
            boolean[] atZero = (boolean[]) field(GameState.PlayerState.class, "ceasefireAtZero").get(player);
            int[] offered = (int[]) field(GameState.PlayerState.class, "diplomacyOfferedLastTurn").get(player);
            for (int other = 0; other < 5; other++) {
                regards[other] = source.get("regards").getDouble(other);
                JsonValue ceasefire = source.get("ceasefires").get(other);
                ceasefires[other] = ceasefire.getInt("turnsLeft");
                atZero[other] = ceasefire.getBoolean("status") && ceasefires[other] == 0;
                offered[other] = source.get("offeredLastTurn").getInt(other);
            }
        }
        return game;
    }

    private static final class RecordedRandom extends Random {
        final double[] values;
        int consumed;
        RecordedRandom(JsonValue draws, double next) {
            values = new double[draws.size + 1];
            for (int i = 0; i < draws.size; i++) values[i] = draws.getDouble(i);
            values[draws.size] = next;
        }
        double peek() { return values[consumed]; }
        @Override public double nextDouble() {
            if (consumed == values.length) throw new AssertionError("Unexpected draw past source continuation");
            return values[consumed++];
        }
        @Override public int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound");
            return (int) (nextDouble() * bound);
        }
        @Override public float nextFloat() { return (float) nextDouble(); }
        @Override public boolean nextBoolean() { return nextInt(2) != 0; }
    }

    private static GameState.Action action(String source) {
        switch (source) {
            case "_attack": return GameState.Action.ATTACK;
            case "preAttack": return GameState.Action.PREPARED_ATTACK;
            case "preCounter": return GameState.Action.DEFENSE;
            case "diplomacy": return GameState.Action.DIPLOMACY;
            case "religion": return GameState.Action.MASS_MEDIA;
            case "propaganda": return GameState.Action.PROPAGANDA;
            case "produce": case "production": return GameState.Action.PRODUCTION;
            case "resources": return GameState.Action.RESOURCES;
            case "research": case "technology": return GameState.Action.RESEARCH;
            default: throw new AssertionError("Unexpected source action " + source);
        }
    }

    private static JsonValue load(String name) throws Exception {
        byte[] bytes;
        try (InputStream stream = SourceCompanionTargetRetryTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull("Independent source snapshot", stream);
            bytes = stream.readAllBytes();
        }
        StringBuilder hash = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            hash.append(String.format(Locale.ROOT, "%02x", value & 255));
        }
        assertEquals("Source fixture bytes", SHA256, hash.toString());
        try (GZIPInputStream stream = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            JsonValue fixture = new JsonReader().parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals("source-ai-companion-retry-v1", fixture.getString("schema"));
            for (JsonValue capture : fixture.get("cases")) if (name.equals(capture.getString("name"))) return capture;
        }
        throw new AssertionError("Missing source case " + name);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
