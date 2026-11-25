package com.example.worlddomination.game.persistence;

import com.example.worlddomination.game.simulation.GameState;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class GameSaveStoreTest {
    @Rule public final TemporaryFolder directory = new TemporaryFolder();

    @Test
    public void existingManualSaveRemainsSlotOneAndBothApisUseItsOriginalFile() throws IOException {
        Path legacyFile = directory.getRoot().toPath().resolve("manual-game.wd2");
        byte[] legacyCheckpoint = GameSaveCodec.encode(new GameState(1, 42L));
        Files.write(legacyFile, legacyCheckpoint);
        GameSaveStore store = createStore(legacyFile);

        assertTrue(store.hasSave());
        assertTrue(store.hasSave(1));
        assertArrayEquals(legacyCheckpoint, GameSaveCodec.encode(store.load()));
        assertArrayEquals(legacyCheckpoint, GameSaveCodec.encode(store.load(1)));
        assertFalse(store.hasSave(2));
        assertFalse(store.hasSave(3));
        assertNull(store.describe(2));
        assertNull(store.describe(3));
        assertArrayEquals(legacyCheckpoint, Files.readAllBytes(legacyFile));

        GameState replacement = new GameState(2, 59L);
        store.save(1, replacement);
        assertArrayEquals(GameSaveCodec.encode(replacement), GameSaveCodec.encode(store.load()));
        store.save(new GameState(0, 17L));
        assertEquals(0, store.load(1).getDifficulty());
        try (Stream<Path> savedFiles = Files.list(legacyFile.getParent())) {
            assertArrayEquals(new Path[]{legacyFile}, savedFiles.toArray(Path[]::new));
        }
    }

    @Test
    public void threeIndependentSlotsSurviveReopenAndExposeDecodedMetadata() throws IOException {
        Path firstFile = directory.getRoot().toPath().resolve("manual-game.wd2");
        GameSaveStore writer = createStore(firstFile);
        GameState[] matches = {new GameState(0, 17L), new GameState(1, 42L), new GameState(2, 59L)};
        for (int slot = 1; slot <= GameSaveStore.SLOT_COUNT; slot++) {
            writer.save(slot, matches[slot - 1]);
        }

        GameState replacement = matches[1];
        replacement.selectAction(GameState.Action.RESOURCES);
        assertTrue(replacement.acceptSelectedAction());
        replacement.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(replacement.acceptSelectedAction());
        replacement.confirmTurn();
        replacement.selectAction(GameState.Action.DIPLOMACY);
        assertTrue(replacement.selectDiplomacyTarget(1));
        assertTrue(replacement.selectDiplomacyOption(0));
        assertTrue(replacement.acceptSelectedAction());
        assertTrue(replacement.getTurnMoneyMillions() < replacement.getHuman().moneyMillions);
        replacement.getHuman().score = 3_000_000_001L;
        createStore(firstFile).save(2, replacement);

        GameSaveStore reopened = createStore(firstFile);
        for (int slot = 1; slot <= GameSaveStore.SLOT_COUNT; slot++) {
            GameState match = matches[slot - 1];
            assertTrue(reopened.hasSave(slot));
            assertArrayEquals(GameSaveCodec.encode(match), GameSaveCodec.encode(reopened.load(slot)));
            GameSaveStore.SlotDescription info = reopened.describe(slot);
            assertEquals(match.getTurn(), info.turn);
            assertEquals(match.getDifficulty(), info.difficulty);
            assertEquals(match.getTurnMoneyMillions(), info.fundsMillions);
            assertEquals(match.getHuman().score, info.score);
        }
        assertTrue(Files.isRegularFile(firstFile.resolveSibling("manual-game-slot-2.wd2")));
        assertTrue(Files.isRegularFile(firstFile.resolveSibling("manual-game-slot-3.wd2")));
    }

    @Test
    public void corruptionInOneSlotDoesNotBlockNeighborReadsOrWrites() throws IOException {
        Path firstFile = saveFile();
        GameSaveStore store = createStore(firstFile);
        GameState first = new GameState(0, 17L);
        GameState second = new GameState(1, 42L);
        GameState third = new GameState(2, 59L);
        store.save(1, first);
        store.save(2, second);
        Path secondFile = firstFile.resolveSibling("match-slot-2.wd2");
        byte[] truncated = Arrays.copyOf(Files.readAllBytes(secondFile), 9);
        Files.write(secondFile, truncated);

        assertTrue(store.hasSave(2));
        assertThrows(IOException.class, () -> store.load(2));
        assertThrows(IOException.class, () -> store.describe(2));
        assertEquals(0, store.describe(1).difficulty);
        store.save(3, third);
        assertArrayEquals(truncated, Files.readAllBytes(secondFile));
        store.save(2, second);
        assertEquals(1, store.describe(2).difficulty);
        assertArrayEquals(GameSaveCodec.encode(first), GameSaveCodec.encode(store.load(1)));
        assertArrayEquals(GameSaveCodec.encode(third), GameSaveCodec.encode(store.load(3)));
    }

    @Test
    public void invalidSlotNumbersAreRejectedBeforeAccessingFiles() throws IOException {
        Path firstFile = saveFile();
        GameSaveStore store = createStore(firstFile);
        GameState state = new GameState(0, 17L);
        for (int slot : new int[]{-1, 0, GameSaveStore.SLOT_COUNT + 1}) {
            assertThrows(IllegalArgumentException.class, () -> store.hasSave(slot));
            assertThrows(IllegalArgumentException.class, () -> store.save(slot, state));
            assertThrows(IllegalArgumentException.class, () -> store.load(slot));
            assertThrows(IllegalArgumentException.class, () -> store.describe(slot));
        }
        try (Stream<Path> savedFiles = Files.list(firstFile.getParent())) {
            assertEquals(0, savedFiles.count());
        }
    }

    @Test
    public void storesAndReplacesMatchAcrossStoreInstances() throws IOException {
        Path file = saveFile();
        GameSaveStore store = createStore(file);
        store.save(new GameState(0, 17L));
        assertTrue(createStore(file).hasSave());
        assertEquals(0, createStore(file).load().getDifficulty());

        GameState replacement = new GameState(2, 59L);
        replacement.selectAction(GameState.Action.RESOURCES);
        assertTrue(replacement.acceptSelectedAction());
        replacement.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(replacement.acceptSelectedAction());
        replacement.confirmTurn();
        store.save(replacement);

        GameState restored = createStore(file).load();
        assertEquals(2, restored.getDifficulty());
        assertEquals(1, restored.getTurn());
        assertEquals(replacement.getHuman().moneyMillions, restored.getHuman().moneyMillions);
        assertEquals(replacement.createPresentationRandomCopy().nextDouble(),
                restored.createPresentationRandomCopy().nextDouble(), 0.0);
    }

    @Test
    public void missingSaveIgnoresAnInterruptedTemporaryFile() throws IOException {
        Path file = saveFile();
        Files.write(file.resolveSibling("interrupted.tmp"), new byte[] {1, 2, 3});
        GameSaveStore store = createStore(file);
        store.save(2, new GameState(2, 59L));

        assertFalse(store.hasSave());
        assertNull(store.describe(1));
        assertThrows(IOException.class, store::load);
        assertTrue(store.hasSave(2));
    }

    @Test
    public void rejectsTruncatedCorruptAndOversizedFiles() throws IOException {
        Path file = saveFile();
        GameSaveStore store = createStore(file);
        store.save(new GameState(1, 42L));
        byte[] original = Files.readAllBytes(file);

        Files.write(file, Arrays.copyOf(original, original.length - 1));
        assertThrows(IOException.class, store::load);

        original[original.length - 1] ^= 1;
        Files.write(file, original);
        assertThrows(IOException.class, store::load);

        Files.write(file, new byte[GameSaveCodec.MAX_SAVE_BYTES + 1]);
        assertThrows(IOException.class, store::load);
    }

    @Test
    public void failedAtomicReplacementPreservesOldSaveAndCleansTemporaryFile() throws IOException {
        Path file = saveFile();
        createStore(file).save(new GameState(0, 17L));
        byte[] original = Files.readAllBytes(file);
        IOException replacementFailure = new IOException("Atomic replacement unavailable");
        GameSaveStore failingStore = new GameSaveStore(file.toFile(), (temporary, destination) -> {
            throw replacementFailure;
        });

        assertSame(replacementFailure,
                assertThrows(IOException.class, () -> failingStore.save(new GameState(2, 59L))));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertEquals(0, createStore(file).load().getDifficulty());
        try (Stream<Path> remainingFiles = Files.list(file.getParent())) {
            assertArrayEquals(new Path[] {file}, remainingFiles.toArray(Path[]::new));
        }
    }

    @Test
    public void loadsCompleteOldSaveUntilReplacementCommits() throws Exception {
        Path file = saveFile();
        GameState oldMatch = new GameState(0, 17L);
        GameState newMatch = new GameState(2, 59L);
        GameSaveStore reader = createStore(file);
        reader.save(oldMatch);
        GameState otherSlot = new GameState(1, 42L);
        reader.save(2, otherSlot);
        byte[] oldCheckpoint = GameSaveCodec.encode(oldMatch);
        byte[] newCheckpoint = GameSaveCodec.encode(newMatch);
        CountDownLatch replacementReady = new CountDownLatch(1);
        CountDownLatch allowReplacement = new CountDownLatch(1);
        GameSaveStore writer = new GameSaveStore(file.toFile(), (temporary, destination) -> {
            replacementReady.countDown();
            try {
                if (!allowReplacement.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting to replace save");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting to replace save", error);
            }
            Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> save = executor.submit(() -> {
                writer.save(newMatch);
                return null;
            });
            assertTrue("Writer reached the atomic replacement boundary",
                    replacementReady.await(10, TimeUnit.SECONDS));
            assertArrayEquals(oldCheckpoint, GameSaveCodec.encode(reader.load()));
            assertArrayEquals(GameSaveCodec.encode(otherSlot), GameSaveCodec.encode(reader.load(2)));
            reader.save(3, newMatch);
            assertArrayEquals(newCheckpoint, GameSaveCodec.encode(reader.load(3)));
            assertArrayEquals(oldCheckpoint, GameSaveCodec.encode(reader.load()));
            allowReplacement.countDown();
            save.get(10, TimeUnit.SECONDS);
            assertArrayEquals(newCheckpoint, GameSaveCodec.encode(reader.load()));
        } finally {
            allowReplacement.countDown();
            executor.shutdownNow();
            assertTrue("Writer stopped", executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private Path saveFile() {
        return directory.getRoot().toPath().resolve("match.wd2");
    }

    private static GameSaveStore createStore(Path file) {
        return new GameSaveStore(file.toFile(), (temporary, destination) -> Files.move(
                temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING));
    }
}
