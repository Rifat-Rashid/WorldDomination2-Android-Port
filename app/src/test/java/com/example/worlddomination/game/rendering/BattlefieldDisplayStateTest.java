package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

public final class BattlefieldDisplayStateTest {
    // The 25 city image depths were independently recorded from the source
    // Presenter.spawn_cities / City.setImageDepth for a five-player setup.
    private static final int[] SOURCE_CITY_DEPTHS = {
            21504, 15505, 13006, 16507, 22508,
            13509, 13510, 9011, 4512, 4513,
            4514, 9015, 11516, 14017, 4518,
            30019, 25020, 22021, 21522, 30023,
            29524, 25525, 22026, 20527, 29028
    };

    @Test public void twoOpponentSetupSeedsAllAuthoredLayersAndOnlyPresentCities()
            throws Exception {
        FlashAnimationTimeline.Frame authored = battleFrame();
        BattlefieldDisplayState state = new BattlefieldDisplayState();
        state.reset(authored, game(2), false);

        assertEquals(122, authored.layers.length);
        assertEquals(authored.layers.length + 15, state.clips().size());
        for (FlashAnimationTimeline.Layer layer : authored.layers) {
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry =
                    state.clips().atDepth(layer.depth);
            assertNotNull("Missing authored depth " + layer.depth, entry);
            assertEquals(layer.name, entry.getValue().name);
        }
        for (int address = 0; address < 25; address++) {
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> city = state.city(address);
            if (address < 15) {
                assertNotNull("Missing city " + address, city);
                assertEquals(SOURCE_CITY_DEPTHS[address], city.getDepth());
                assertEquals(BattlefieldDisplayState.Kind.CITY, city.getValue().kind);
            } else assertNull("Absent opponent city " + address, city);
        }
        assertEquals(22509, state.nextDepth());
        assertEquals("city4", state.clips().entryAt(state.clips().size() - 1)
                .getValue().name);
    }

    @Test public void fourOpponentBattleAppliesSourceSwapsInReversePlayerOrder()
            throws Exception {
        FlashAnimationTimeline.Frame authored = battleFrame();
        BattlefieldDisplayState state = new BattlefieldDisplayState();
        state.reset(authored, game(4), true);

        assertEquals(authored.layers.length + 25, state.clips().size());
        for (int address = 0; address < 25; address++) {
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> city = state.city(address);
            assertNotNull("Missing city " + address, city);
            assertEquals(SOURCE_CITY_DEPTHS[address], city.getDepth());
            assertSame(city, state.clips().atDepth(SOURCE_CITY_DEPTHS[address]));
        }
        assertEquals(30024, state.nextDepth());
        assertEquals("city19", state.clips().entryAt(state.clips().size() - 1)
                .getValue().name);

        for (int player = 0; player < 5; player++) {
            FlashAnimationTimeline.Layer first = named(authored, "ab" + player + "_1");
            FlashAnimationTimeline.Layer second = named(authored, "ab" + player + "_2");
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> firstEntry =
                    state.actionBuilding(player, 1);
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> secondEntry =
                    state.actionBuilding(player, 2);
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> flag = state.flag(player);
            assertEquals(row(first.ty) + 2, firstEntry.getDepth());
            assertEquals(row(second.ty) + 1, secondEntry.getDepth());
            assertEquals(row(second.ty) + 3, flag.getDepth());
            assertSame(firstEntry, state.clips().atDepth(firstEntry.getDepth()));
            assertSame(flag, state.clips().atDepth(flag.getDepth()));
            assertEquals(BattlefieldDisplayState.Kind.ACTION_BUILDING,
                    firstEntry.getValue().kind);
            assertEquals(BattlefieldDisplayState.Kind.FLAG, flag.getValue().kind);
        }
        for (int i = 1; i < state.clips().size(); i++) {
            assertTrue(state.clips().entryAt(i - 1).getDepth()
                    < state.clips().entryAt(i).getDepth());
        }
    }

    @Test public void inactiveAuthoredSlotsStayAtTheirOriginalDepths() throws Exception {
        FlashAnimationTimeline.Frame authored = battleFrame();
        BattlefieldDisplayState state = new BattlefieldDisplayState();
        state.reset(authored, game(2), true);
        for (int player = 3; player < 5; player++) {
            assertEquals(named(authored, "ab" + player + "_1").depth,
                    state.actionBuilding(player, 1).getDepth());
            assertEquals(named(authored, "ab" + player + "_2").depth,
                    state.actionBuilding(player, 2).getDepth());
            assertEquals(named(authored, "flag" + player).depth,
                    state.flag(player).getDepth());
        }
        assertEquals(22509, state.nextDepth());
    }

    @Test public void leavingGameplayClearsSessionOwnersAndDetachesAllHandles() throws Exception {
        BattlefieldDisplayState state = new BattlefieldDisplayState();
        state.reset(battleFrame(), game(4), true);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> oldCity = state.city(19);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> oldFlag = state.flag(4);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> oldAction = state.actionBuilding(4, 2);
        state.bindActionBuildings(Collections.singletonList(animation(4, 2)));
        assertNotNull(oldAction.getValue().owner);
        state.clear();
        assertEquals(0, state.clips().size());
        assertEquals(0, state.nextDepth());
        assertNull(state.city(19));
        assertNull(state.flag(4));
        assertNull(state.actionBuilding(4, 2));
        assertFalse(oldCity.isAttached());
        assertFalse(oldFlag.isAttached());
        assertFalse(oldAction.isAttached());
        state.clear(); // Idempotent disposal/setup transitions.
    }

    @Test public void resetInvalidatesHandlesAndRebindsOnlyCurrentActionClips()
            throws Exception {
        FlashAnimationTimeline.Frame authored = battleFrame();
        BattlefieldDisplayState state = new BattlefieldDisplayState();
        state.reset(authored, game(2), true);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> oldCity = state.city(4);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> oldSlot =
                state.actionBuilding(0, 1);
        FlashActionBuildingAnimation first = animation(0, 1);
        FlashActionBuildingAnimation second = animation(0, 1);
        state.bindActionBuildings(Collections.singletonList(first));
        assertSame(first, oldSlot.getValue().owner);
        state.bindActionBuildings(Collections.singletonList(second));
        assertSame(second, oldSlot.getValue().owner);
        state.bindActionBuildings(Collections.emptyList());
        assertNull(oldSlot.getValue().owner);

        state.reset(authored, game(4), false);
        assertFalse(oldCity.isAttached());
        assertFalse(oldSlot.isAttached());
        state.remove(oldCity);
        state.clips().swapDepths(oldSlot, 99999);
        assertNotSame(oldSlot, state.actionBuilding(0, 1));
        assertNull(state.actionBuilding(0, 1).getValue().owner);
        assertEquals(30024, state.nextDepth());
        for (int player = 0; player < 5; player++) {
            assertEquals(named(authored, "ab" + player + "_1").depth,
                    state.actionBuilding(player, 1).getDepth());
        }
    }

    @Test public void cityMarkerSyncPreservesHandleUntilTheLastIconIsRemoved()
            throws Exception {
        GameState game = game(2);
        BattlefieldDisplayState state = new BattlefieldDisplayState();
        state.reset(battleFrame(), game, true);
        state.syncCityMarkers(game);
        assertNull(state.marker(5));
        int initialCount = state.clips().size();

        game.selectAction(GameState.Action.PROPAGANDA);
        assertTrue(game.configurePropaganda(1, 0));
        state.syncCityMarkers(game);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> marker = state.marker(5);
        assertNotNull(marker);
        assertEquals(BattlefieldDisplayState.Kind.CITY_MARKER, marker.getValue().kind);
        assertEquals(SOURCE_CITY_DEPTHS[5] + 1500, marker.getDepth());
        assertEquals(initialCount + 1, state.clips().size());
        state.syncCityMarkers(game);
        assertSame(marker, state.marker(5));

        game.cancelSelection();
        state.syncCityMarkers(game);
        assertNull(state.marker(5));
        assertFalse(marker.isAttached());
        assertEquals(initialCount, state.clips().size());
        state.reset(battleFrame(), game, false);
        assertNull(state.marker(5));
    }

    private static int row(float y) { return (int) Math.floor(y / 10.0) * 500; }

    private static FlashAnimationTimeline.Layer named(FlashAnimationTimeline.Frame frame,
                                                       String name) {
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            if (name.equals(layer.name)) return layer;
        }
        throw new AssertionError("Missing authored layer " + name);
    }

    private static GameState game(int opponents) {
        GameSetup setup = new GameSetup(0);
        for (int i = 0; i < opponents; i++) assertTrue(setup.toggleOpponent(i));
        return new GameState(setup);
    }

    private static FlashActionBuildingAnimation animation(int player, int slot)
            throws Exception {
        Constructor<GameState.ActionBuildingEvent> constructor =
                GameState.ActionBuildingEvent.class.getDeclaredConstructor(int.class,
                        GameState.Action.class, int.class, String.class, String.class, int.class);
        constructor.setAccessible(true);
        return new FlashActionBuildingAnimation(constructor.newInstance(player,
                GameState.Action.MASS_MEDIA, slot, "media", "chr", 0));
    }

    private static FlashAnimationTimeline.Frame battleFrame() throws Exception {
        Path asset = Paths.get("src/main/assets/original/runtime/scene.json.gz");
        if (!Files.isRegularFile(asset)) asset = Paths.get("app").resolve(asset);
        JsonValue scene;
        try (InputStream file = Files.newInputStream(asset);
             InputStream gzip = new GZIPInputStream(file);
             InputStreamReader reader = new InputStreamReader(gzip, StandardCharsets.UTF_8)) {
            scene = new JsonReader().parse(reader);
        }
        JsonValue symbols = scene.get("symbols");
        for (JsonValue symbol = symbols.child; symbol != null; symbol = symbol.next) {
            if (symbol.getInt("id") == 5766) {
                FlashAnimationTimeline timeline = FlashAnimationTimeline.fromSymbol(symbol, 24f);
                assertEquals(1, timeline.getFrameCount());
                return timeline.getFrame(0);
            }
        }
        throw new AssertionError("Missing source battle symbol 5766");
    }
}
