package com.example.worlddomination.game.persistence;

import com.example.worlddomination.game.simulation.GameState;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

/** Stores a single match in an app-private file. */
public final class GameSaveStore {
    @FunctionalInterface
    public interface AtomicReplace {
        /** Atomically moves a file over the destination, or fails and preserves the destination. */
        void replace(File temporary, File destination) throws IOException;
    }

    private final File saveFile;
    private final AtomicReplace atomicReplace;

    public GameSaveStore(File saveFile, AtomicReplace atomicReplace) {
        this.saveFile = Objects.requireNonNull(saveFile).getAbsoluteFile();
        this.atomicReplace = Objects.requireNonNull(atomicReplace);
    }

    public boolean hasSave() {
        return saveFile.isFile();
    }

    public void save(GameState state) throws IOException {
        byte[] encoded = GameSaveCodec.encode(state);
        File directory = saveFile.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create the save directory");
        }
        File temporary = File.createTempFile("match-", ".tmp", directory);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                output.write(encoded);
                output.getFD().sync();
            }
            atomicReplace.replace(temporary, saveFile);
        } catch (IOException | RuntimeException failure) {
            if (!temporary.delete() && temporary.exists()) {
                failure.addSuppressed(new IOException("Could not remove the temporary save"));
            }
            throw failure;
        }
    }

    public GameState load() throws IOException {
        try (FileInputStream input = new FileInputStream(saveFile)) {
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > GameSaveCodec.MAX_SAVE_BYTES - encoded.size()) {
                    throw new IOException("Saved match exceeds the size limit");
                }
                encoded.write(buffer, 0, count);
            }
            return GameSaveCodec.decode(encoded.toByteArray());
        }
    }
}
