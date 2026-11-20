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
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class GameSaveStoreTest {
    @Rule public final TemporaryFolder directory = new TemporaryFolder();

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

        assertFalse(store.hasSave());
        assertThrows(IOException.class, store::load);
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

    private Path saveFile() {
        return directory.getRoot().toPath().resolve("match.wd2");
    }

    private static GameSaveStore createStore(Path file) {
        return new GameSaveStore(file.toFile(), (temporary, destination) -> Files.move(
                temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING));
    }
}
