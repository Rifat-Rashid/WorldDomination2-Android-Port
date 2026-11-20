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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The host runs the save and load phases in different processes. */
@RunWith(AndroidJUnit4.class)
public final class ManualSaveProcessRestartInstrumentedTest {
    private static final String MARKER_FILENAME = "manual-save-process-restart.marker";
    private static final String RECEIPT_KEY = "wd2_match_save_receipt";
    private static final String HEX = "0123456789abcdef";

    @Test
    public void manualSaveRestoresAfterProcessDeath() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        String phase = InstrumentationRegistry.getArguments()
                .getString("wd2MatchSavePhase", "roundtrip");
        GameState expected = newCheckpoint();
        byte[] checkpoint = GameSaveCodec.encode(expected);
        if ("roundtrip".equals(phase)) {
            roundTripInCache(expected, checkpoint);
            return;
        }

        assertTrue("Unknown manual-save phase: " + phase,
                "save".equals(phase) || "load".equals(phase));
        Context context = instrumentation.getTargetContext();
        String packageName = context.getPackageName();
        assertTrue("Explicit manual-save phases require a local test package",
                "com.example.worlddomination".equals(packageName)
                        || "com.example.worlddomination.saveprobe".equals(packageName));
        File save = new File(context.getFilesDir(), SAVE_FILENAME);
        File marker = new File(context.getFilesDir(), MARKER_FILENAME);
        String processToken = Process.myPid() + ":" + Process.getStartElapsedRealtime();
        Bundle status = new Bundle();

        if ("save".equals(phase)) {
            requireAbsent(save);
            requireAbsent(marker);
            runInGame(app -> {
                GameState match = prepareSave(app, expected);
                GameSettingsMenu menu = openSettings(app);
                requireAbsent(save);
                requireAbsent(marker);
                String digest = saveFromSettings(app, menu, save, checkpoint);
                writeMarker(marker, digest + "\n" + processToken);
                addReceipt(status, phase, processToken, match, digest);
            });
        } else {
            byte[] ownership = readRegularFile(marker, 128);
            String markerText = new String(ownership, StandardCharsets.US_ASCII);
            String digest = checkpointDigest(save, checkpoint);
            String markerPrefix = digest + "\n";
            assertTrue("Invalid manual-save ownership marker",
                    markerText.matches(markerPrefix + "[1-9][0-9]*:[0-9]+"));
            assertNotEquals("Load must start in a fresh app process",
                    markerText.substring(markerPrefix.length()), processToken);
            runInGame(app -> {
                GameState restored = loadFromSetup(app, expected);
                addReceipt(status, phase, processToken, restored, digest);
            });
            // A changed file or marker remains in place for inspection.
            assertArrayEquals(ownership, readRegularFile(marker, 128));
            assertEquals(digest, checkpointDigest(save, checkpoint));
            assertTrue("Could not remove the test-owned save", save.delete());
            assertTrue("Could not remove the test ownership marker", marker.delete());
        }
        instrumentation.sendStatus(0, status);
    }

    private static void roundTripInCache(GameState expected, byte[] checkpoint) throws Exception {
        File directory = createStoreDirectory();
        File save = new File(directory, SAVE_FILENAME);
        try {
            runInGame(app -> {
                app.setGameSaveStore(AndroidGameSaveStore.create(directory));
                prepareSave(app, expected);
                saveFromSettings(app, openSettings(app), save, checkpoint);
            });
            runInGame(app -> {
                app.setGameSaveStore(AndroidGameSaveStore.create(directory));
                loadFromSetup(app, expected);
            });
        } finally {
            removeStoreDirectory(directory);
        }
    }

    private static GameState newCheckpoint() {
        GameState match = newMatch();
        queue(match, GameState.Action.RESOURCES);
        queue(match, GameState.Action.MASS_MEDIA);
        match.confirmTurn();
        assertEquals(1, match.getTurn());
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

    private static GameState prepareSave(WorldDominationGame app, GameState expected)
            throws Exception {
        assertNull("A fresh launcher begins at setup", game(app));
        GameState match = newCheckpoint();
        installMatch(app, match);
        render(app);
        assertCheckpoint(expected, match);
        return match;
    }

    private static String saveFromSettings(WorldDominationGame app, GameSettingsMenu menu,
                                           File save, byte[] checkpoint) throws Exception {
        assertTrue(menu.canSave());
        assertFalse(menu.canLoad());
        tap(app, menu.save);
        assertTrue(menu.canLoad());
        assertEquals("Game saved. Turn 1.", menu.getStatus());
        return checkpointDigest(save, checkpoint);
    }

    private static GameState loadFromSetup(WorldDominationGame app, GameState expected)
            throws Exception {
        render(app);
        assertNull("Loading starts at setup", game(app));
        GameSettingsMenu menu = openSettings(app);
        assertFalse(menu.canSave());
        assertTrue(menu.canLoad());
        tap(app, menu.load);
        assertTrue(menu.isConfirmingLoad());
        assertNull("The match appears only after confirmation", game(app));
        tap(app, menu.confirm);
        assertEquals("Loaded turn 1.", menu.getStatus());
        assertFalse(menu.isConfirmingLoad());
        assertTrue(menu.canSave());
        GameState restored = game(app);
        assertNotNull(restored);
        assertCheckpoint(expected, restored);
        return restored;
    }

    private static void assertCheckpoint(GameState expected, GameState actual) throws Exception {
        assertEquals(1, actual.getTurn());
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
                                   GameState match, String digest) throws Exception {
        JSONArray queued = new JSONArray();
        for (int index = 0; index < match.getQueuedActionCount(); index++) {
            queued.put(match.getQueuedAction(index).name());
        }
        JSONObject receipt = new JSONObject();
        receipt.put("phase", phase);
        receipt.put("pid", Process.myPid());
        receipt.put("processToken", processToken);
        receipt.put("turn", match.getTurn());
        receipt.put("queued", queued);
        receipt.put("funds", match.getTurnMoneyMillions());
        receipt.put("checkpointSha256", digest);
        receipt.put("nextRandomBits", nextRandomBits(match));
        status.putString(RECEIPT_KEY, receipt.toString());
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
