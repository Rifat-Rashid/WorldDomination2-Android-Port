package com.example.worlddomination.game.rendering;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.persistence.GameSaveCodec;
import com.example.worlddomination.game.persistence.GameSaveStore;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.GameSettingsMenu;
import com.example.worlddomination.platform.persistence.AndroidGameSaveStore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.SAVE_FILENAME;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.createStoreDirectory;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.game;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.installMatch;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.newMatch;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.openSettings;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.queue;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.removeStoreDirectory;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.render;
import static com.example.worlddomination.game.rendering.SettingsSaveLoadInstrumentedTest.tap;
import static com.example.worlddomination.game.rendering.SourceBattleCleanupInstrumentedTest.runInGame;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The host runs the save and load phases in different processes. */
@RunWith(AndroidJUnit4.class)
public final class ManualSaveProcessRestartInstrumentedTest {
    private static final String MARKER_FILENAME = "manual-save-process-restart.marker";
    private static final String RECEIPT_KEY = "wd2_match_save_receipt";
    private static final String HEX = "0123456789abcdef";
    private static final String[] SAVE_FILENAMES = {
            SAVE_FILENAME, "manual-game-slot-2.wd2", "manual-game-slot-3.wd2"
    };

    @Test
    public void manualSaveRestoresAfterProcessDeath() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        String phase = InstrumentationRegistry.getArguments()
                .getString("wd2MatchSavePhase", "roundtrip");
        assertEquals(GameSaveStore.SLOT_COUNT, SAVE_FILENAMES.length);
        GameState[] expected = new GameState[SAVE_FILENAMES.length];
        for (int i = 0; i < expected.length; i++) expected[i] = newCheckpoint(i + 1);
        if ("roundtrip".equals(phase)) {
            roundTripInCache(expected);
            return;
        }

        assertTrue("Unknown manual-save phase: " + phase,
                "save".equals(phase) || "load".equals(phase));
        Context context = instrumentation.getTargetContext();
        String packageName = context.getPackageName();
        assertTrue("Explicit manual-save phases require a local test package",
                "com.example.worlddomination".equals(packageName)
                        || "com.example.worlddomination.saveprobe".equals(packageName));
        File directory = context.getFilesDir();
        File marker = new File(directory, MARKER_FILENAME);
        String processToken = Process.myPid() + ":" + Process.getStartElapsedRealtime();
        Bundle status = new Bundle();

        if ("save".equals(phase)) {
            requireAbsent(marker);
            runInGame(app -> {
                GameState[] saved = saveAllFromSettings(app, directory, expected);
                String[] digests = checkpointDigests(directory, expected);
                writeMarker(marker, String.join("\n", digests) + "\n" + processToken);
                addReceipt(status, phase, processToken, saved, digests);
            });
        } else {
            byte[] ownership = readRegularFile(marker, 256);
            String markerText = new String(ownership, StandardCharsets.US_ASCII);
            String[] digests = checkpointDigests(directory, expected);
            String markerPrefix = String.join("\n", digests) + "\n";
            assertTrue("Invalid manual-save ownership marker",
                    markerText.matches(markerPrefix + "[1-9][0-9]*:[0-9]+"));
            assertNotEquals("Load must start in a fresh app process",
                    markerText.substring(markerPrefix.length()), processToken);
            runInGame(app -> addReceipt(status, phase, processToken,
                    loadAllFromSetup(app, expected), digests));
            // Changed checkpoints or marker remain in place for inspection.
            assertArrayEquals(ownership, readRegularFile(marker, 256));
            assertArrayEquals(digests, checkpointDigests(directory, expected));
            for (String name : SAVE_FILENAMES) {
                assertTrue("Could not remove test-owned save " + name,
                        new File(directory, name).delete());
            }
            assertTrue("Could not remove the test ownership marker", marker.delete());
        }
        instrumentation.sendStatus(0, status);
    }

    private static void roundTripInCache(GameState[] expected) throws Exception {
        File directory = createStoreDirectory();
        try {
            runInGame(app -> {
                app.setGameSaveStore(AndroidGameSaveStore.create(directory));
                saveAllFromSettings(app, directory, expected);
                checkpointDigests(directory, expected);
            });
            runInGame(app -> {
                app.setGameSaveStore(AndroidGameSaveStore.create(directory));
                loadAllFromSetup(app, expected);
            });
        } finally {
            removeStoreDirectory(directory);
        }
    }

    private static GameState newCheckpoint(int turn) {
        GameState match = newMatch();
        for (int i = 0; i < turn; i++) {
            queue(match, GameState.Action.RESOURCES);
            queue(match, GameState.Action.MASS_MEDIA);
            match.confirmTurn();
        }
        assertEquals(turn, match.getTurn());
        assertEquals(GameState.MatchStatus.ONGOING, match.getMatchStatus());
        assertEquals(0, match.getQueuedActionCount());

        queue(match, GameState.Action.RESOURCES);
        match.selectAction(GameState.Action.RESEARCH);
        assertTrue(match.selectResearchTechnology("soldier2"));
        int researchCost = match.getSelectedResearchCostMillions();
        int fundsBeforeResearch = match.getTurnMoneyMillions();
        assertTrue("The queued research reserves funds", researchCost > 0);
        assertTrue(match.acceptSelectedAction());
        assertEquals(fundsBeforeResearch - researchCost, match.getTurnMoneyMillions());
        match.cancelSelection();
        return match;
    }

    private static GameState[] saveAllFromSettings(WorldDominationGame app, File directory,
                                                   GameState[] expected) throws Exception {
        assertNull("A fresh launcher begins at setup", game(app));
        for (String name : SAVE_FILENAMES) requireAbsent(new File(directory, name));
        GameState[] saved = new GameState[expected.length];
        for (int i = 0; i < saved.length; i++) {
            GameState match = newCheckpoint(i + 1);
            installMatch(app, match);
            render(app);
            assertCheckpoint(expected[i], match);
            GameSettingsMenu menu = openSettings(app);
            tap(app, menu.slots[i]);
            assertTrue(menu.canSave());
            assertFalse(menu.canLoad());
            requireAbsent(new File(directory, SAVE_FILENAMES[i]));
            tap(app, menu.save);
            assertTrue(menu.canLoad());
            assertEquals("Game saved. Turn " + match.getTurn() + ".", menu.getStatus());
            saved[i] = match;
            tap(app, menu.close);
        }
        return saved;
    }

    private static GameState[] loadAllFromSetup(WorldDominationGame app, GameState[] expected)
            throws Exception {
        render(app);
        assertNull("Loading starts at setup", game(app));
        GameSettingsMenu menu = openSettings(app);
        assertFalse(menu.canSave());
        GameState[] restored = new GameState[expected.length];
        for (int i = 0; i < restored.length; i++) {
            tap(app, menu.slots[i]);
            assertTrue(menu.canLoad());
            GameState previous = game(app);
            tap(app, menu.load);
            assertTrue(menu.isConfirmingLoad());
            assertSame("The match changes only after confirmation", previous, game(app));
            tap(app, menu.confirm);
            assertEquals("Loaded turn " + expected[i].getTurn() + ".", menu.getStatus());
            assertFalse(menu.isConfirmingLoad());
            assertTrue(menu.canSave());
            GameState match = game(app);
            assertNotNull(match);
            assertCheckpoint(expected[i], match);
            restored[i] = match;
        }
        return restored;
    }

    private static void assertCheckpoint(GameState expected, GameState actual) throws Exception {
        assertEquals(expected.getTurn(), actual.getTurn());
        assertEquals(2, actual.getQueuedActionCount());
        assertEquals(GameState.Action.RESOURCES, actual.getQueuedAction(0));
        assertEquals(GameState.Action.RESEARCH, actual.getQueuedAction(1));
        assertEquals(expected.getTurnMoneyMillions(), actual.getTurnMoneyMillions());
        assertEquals(nextRandomBits(expected), nextRandomBits(actual));
        assertArrayEquals(GameSaveCodec.encode(expected), GameSaveCodec.encode(actual));
    }

    private static long nextRandomBits(GameState match) {
        return Double.doubleToLongBits(match.createPresentationRandomCopy().nextDouble());
    }

    private static void addReceipt(Bundle status, String phase, String processToken,
                                   GameState[] matches, String[] digests) throws Exception {
        JSONArray slots = new JSONArray();
        for (int i = 0; i < matches.length; i++) {
            GameState match = matches[i];
            JSONArray queued = new JSONArray();
            for (int index = 0; index < match.getQueuedActionCount(); index++) {
                queued.put(match.getQueuedAction(index).name());
            }
            JSONObject slot = new JSONObject();
            slot.put("slot", i + 1);
            slot.put("turn", match.getTurn());
            slot.put("queued", queued);
            slot.put("funds", match.getTurnMoneyMillions());
            slot.put("checkpointSha256", digests[i]);
            slot.put("nextRandomBits", nextRandomBits(match));
            slots.put(slot);
        }
        JSONObject receipt = new JSONObject();
        receipt.put("phase", phase);
        receipt.put("pid", Process.myPid());
        receipt.put("processToken", processToken);
        receipt.put("slots", slots);
        status.putString(RECEIPT_KEY, receipt.toString());
    }

    private static String[] checkpointDigests(File directory, GameState[] expected)
            throws Exception {
        String[] digests = new String[expected.length];
        for (int i = 0; i < digests.length; i++) {
            File saved = new File(directory, SAVE_FILENAMES[i]);
            digests[i] = checkpointDigest(saved, GameSaveCodec.encode(expected[i]));
        }
        return digests;
    }

    private static String checkpointDigest(File save, byte[] expected) throws Exception {
        byte[] actual = readRegularFile(save, GameSaveCodec.MAX_SAVE_BYTES);
        assertArrayEquals("The saved file matches the independently built checkpoint", expected, actual);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(actual);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(HEX.charAt((value & 0xff) >>> 4));
            hex.append(HEX.charAt(value & 0x0f));
        }
        return hex.toString();
    }

    private static void requireAbsent(File file) throws Exception {
        try {
            Os.lstat(file.getAbsolutePath());
            fail("Refusing to overwrite existing " + file.getName());
        } catch (ErrnoException error) {
            if (error.errno != OsConstants.ENOENT) throw error;
        }
    }

    private static byte[] readRegularFile(File file, int maxBytes) throws Exception {
        StructStat stat = Os.lstat(file.getAbsolutePath());
        assertTrue("Expected a regular file: " + file.getName(), OsConstants.S_ISREG(stat.st_mode));
        assertTrue("Unexpected file size: " + file.getName(), stat.st_size <= maxBytes);
        byte[] bytes = new byte[(int) stat.st_size];
        try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
            input.readFully(bytes);
            assertEquals("File changed while reading: " + file.getName(), -1, input.read());
        }
        return bytes;
    }

    private static void writeMarker(File marker, String ownership) throws Exception {
        assertTrue("Refusing to overwrite an ownership marker", marker.createNewFile());
        try (FileOutputStream output = new FileOutputStream(marker)) {
            output.write(ownership.getBytes(StandardCharsets.US_ASCII));
            output.getFD().sync();
        }
    }
}
