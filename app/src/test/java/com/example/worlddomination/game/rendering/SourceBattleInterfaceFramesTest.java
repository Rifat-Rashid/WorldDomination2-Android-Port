package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class SourceBattleInterfaceFramesTest {
    private static final int[] FLAME_IDS = {4010, 4015, 4018, 4023, 4028};

    @Test public void sourceFlamesConsumeOnceAtParentFramesNinetyFiveThroughNinetyNine() {
        SourceBattleInterfaceFrames clock = new SourceBattleInterfaceFrames();
        ReferenceRandom presentation = new ReferenceRandom(0);
        ReferenceRandom expected = presentation.copy();
        clock.begin();
        assertEquals(1, clock.getCenterScreenFrame());
        for (int tick = 1; tick <= 442; tick++) {
            int action = clock.tick(presentation);
            assertEquals(tick < 133 ? tick + 1 : 1, clock.getCenterScreenFrame());
            int index = tick - 94;
            if (index >= 0 && index < FLAME_IDS.length) {
                assertEquals(FLAME_IDS[index], action);
                assertEquals(2 + expected.nextInt(109),
                        clock.getSelectedStartFrame(FLAME_IDS[index]));
            } else {
                assertEquals(0, action);
            }
        }
        assertEquals(expected.nextDouble(), presentation.nextDouble(), 0);
    }

    @Test public void frameNinetyFourDoesNotDrawAndFrameNinetyFiveDoes() {
        SourceBattleInterfaceFrames clock = new SourceBattleInterfaceFrames();
        ReferenceRandom random = new ReferenceRandom(153);
        clock.begin();
        for (int tick = 1; tick <= 93; tick++) {
            assertEquals(0, clock.tick(random));
        }
        assertEquals(94, clock.getCenterScreenFrame());
        assertEquals(0, clock.getSelectedStartFrame(4010));
        assertEquals(new ReferenceRandom(153).nextDouble(), random.nextDouble(), 0);

        clock.begin();
        random = new ReferenceRandom(153);
        for (int tick = 1; tick < 94; tick++) clock.tick(random);
        assertEquals(4010, clock.tick(random));
        assertEquals(110, clock.getSelectedStartFrame(4010));
        assertEquals(0, clock.getSelectedStartFrame(4015));
    }

    @Test public void inclusiveRandRangeCanSelectMinimumAndMaximum() {
        SourceBattleInterfaceFrames clock = new SourceBattleInterfaceFrames();
        for (int seed : new int[] {139, 153}) {
            clock.begin();
            ReferenceRandom presentation = new ReferenceRandom(seed);
            for (int tick = 0; tick < 94; tick++) clock.tick(presentation);
            assertEquals(seed == 139 ? 2 : 110, clock.getSelectedStartFrame(4010));
        }
    }

    @Test public void resetClearsResultsAndUnstartedTickCannotAdvanceRandom() {
        SourceBattleInterfaceFrames clock = new SourceBattleInterfaceFrames();
        ReferenceRandom presentation = new ReferenceRandom(77);
        ReferenceRandom simulation = presentation.copy();
        try {
            clock.tick(presentation);
            fail("An unstarted timeline must reject ticking");
        } catch (IllegalStateException expected) {
            assertEquals(simulation.nextDouble(), presentation.nextDouble(), 0);
        }
        clock.begin();
        for (int tick = 0; tick < 98; tick++) clock.tick(presentation);
        assertTrue(clock.getSelectedStartFrame(4028) > 0);
        clock.reset();
        assertFalse(clock.isRunning());
        assertEquals(0, clock.getElapsedTicks());
        assertEquals(1, clock.getCenterScreenFrame());
        for (int id : FLAME_IDS) assertEquals(0, clock.getSelectedStartFrame(id));
        clock.begin();
        ReferenceRandom newTurn = new ReferenceRandom(139);
        for (int tick = 0; tick < 94; tick++) clock.tick(newTurn);
        assertEquals(2, clock.getSelectedStartFrame(4010));
    }

    @Test public void presentationCopyLeavesSimulationStreamUnchanged() {
        ReferenceRandom simulation = new ReferenceRandom(0);
        ReferenceRandom presentation = simulation.copy();
        SourceBattleInterfaceFrames clock = new SourceBattleInterfaceFrames();
        clock.begin();
        for (int tick = 0; tick < 99; tick++) clock.tick(presentation);
        assertEquals(new ReferenceRandom(0).nextDouble(), simulation.nextDouble(), 0);
    }

    @Test public void packagedSourceScenePlacesTheFiveFrameActionSprites() throws Exception {
        Path asset = Paths.get("src/main/assets/original/runtime/scene.json.gz");
        if (!Files.isRegularFile(asset)) asset = Paths.get("app").resolve(asset);
        JsonValue scene;
        try (InputStream gzip = new GZIPInputStream(Files.newInputStream(asset));
             InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)) {
            scene = new JsonReader().parse(reader);
        }
        JsonValue symbols = scene.get("symbols");
        JsonValue center = symbol(symbols, 4444);
        JsonValue buildings = symbol(symbols, 4108);
        JsonValue church = symbol(symbols, 4085);
        assertPlacement(center, 95, 4108, "buildingsMC");
        assertPlacement(buildings, 1, 4085, "mc3");
        assertEquals(5, buildings.get("timeline").get("frames").size);
        for (int index = 0; index < FLAME_IDS.length; index++) {
            int flameId = SourceBattleInterfaceFrames.getFlameCharacterId(index);
            assertEquals(FLAME_IDS[index], flameId);
            assertPlacement(church, index + 1, flameId, null);
            assertEquals(1, symbol(symbols, flameId).get("timeline")
                    .get("action_frames").getInt(0));
        }
        assertEquals(FLAME_IDS.length, SourceBattleInterfaceFrames.getFlameCount());
    }

    private static JsonValue symbol(JsonValue symbols, int id) {
        for (JsonValue entry = symbols.child; entry != null; entry = entry.next) {
            if (entry.getInt("id") == id) return entry;
        }
        throw new AssertionError("Missing source symbol " + id);
    }

    private static void assertPlacement(JsonValue symbol, int frameNumber,
                                        int childId, String childName) {
        JsonValue frame = symbol.get("timeline").get("frames").get(frameNumber - 1);
        for (JsonValue operation = frame.child; operation != null; operation = operation.next) {
            JsonValue values = operation.get("values");
            if (values != null && values.getInt("character_id", -1) == childId) {
                if (childName != null) assertEquals(childName, values.getString("name"));
                return;
            }
        }
        throw new AssertionError("Missing symbol " + childId + " on frame " + frameNumber);
    }
}
