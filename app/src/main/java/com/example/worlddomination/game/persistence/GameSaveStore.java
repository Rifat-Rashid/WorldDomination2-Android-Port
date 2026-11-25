package com.example.worlddomination.game.persistence;

import com.example.worlddomination.game.simulation.GameState;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

/** Stores independent match checkpoints in app-private files. */
public final class GameSaveStore {
    public static final int SLOT_COUNT = 3;
    private static final int FIRST_SLOT = 1;

    @FunctionalInterface
    public interface AtomicReplace {
        /** Atomically moves a file over the destination, or fails and preserves the destination. */
        void replace(File temporary, File destination) throws IOException;
    }

    /** Display fields read from the same verified checkpoint. */
    public static final class SlotDescription {
        public final int turn;
        public final int difficulty;
        public final int fundsMillions;
        public final long score;

        private SlotDescription(GameState state) {
            turn = state.getTurn();
            difficulty = state.getDifficulty();
            fundsMillions = state.getTurnMoneyMillions();
            score = state.getHuman().score;
        }
    }

    /** Display fields for the compatible checkpoint selected for Continue. */
    public static final class LatestSave {
        public final int slot;
        public final int turn;

        private LatestSave(int slot, int turn) {
            this.slot = slot;
            this.turn = turn;
        }
    }

    private final File[] slotFiles = new File[SLOT_COUNT];
    private final AtomicReplace atomicReplace;

    /** Uses the original checkpoint path for slot 1 and sibling files for the remaining slots. */
    public GameSaveStore(File saveFile, AtomicReplace atomicReplace) {
        File legacyFile = Objects.requireNonNull(saveFile).getAbsoluteFile();
        slotFiles[FIRST_SLOT - 1] = legacyFile;
        String name = legacyFile.getName();
        int extensionIndex = name.lastIndexOf('.');
        if (extensionIndex <= 0) extensionIndex = name.length();
        String stem = name.substring(0, extensionIndex);
        String extension = name.substring(extensionIndex);
        for (int slot = FIRST_SLOT + 1; slot <= SLOT_COUNT; slot++) {
            slotFiles[slot - 1] = new File(legacyFile.getParentFile(), stem + "-slot-" + slot + extension);
        }
        this.atomicReplace = Objects.requireNonNull(atomicReplace);
    }

    public boolean hasSave() {
        return hasSave(FIRST_SLOT);
    }

    /** Checks for a regular checkpoint file in the one-based slot. */
    public boolean hasSave(int slot) {
        return fileForSlot(slot).isFile();
    }

    public void save(GameState state) throws IOException {
        save(FIRST_SLOT, state);
    }

    public void save(int slot, GameState state) throws IOException {
        File saveFile = fileForSlot(slot);
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
        return load(FIRST_SLOT);
    }

    public GameState load(int slot) throws IOException {
        return loadFile(fileForSlot(slot));
    }

    /** Returns null for an empty slot and reports unreadable checkpoints as an IOException. */
    public SlotDescription describe(int slot) throws IOException {
        File saveFile = fileForSlot(slot);
        return saveFile.isFile() ? new SlotDescription(loadFile(saveFile)) : null;
    }

    /**
     * Returns the compatible checkpoint with the newest file modification time, or null if none.
     * Ties favor the lowest slot.
     */
    public LatestSave latestCompatible() {
        LatestSave latest = null;
        long latestModified = Long.MIN_VALUE;
        for (int slot = FIRST_SLOT; slot <= SLOT_COUNT; slot++) {
            File saveFile = fileForSlot(slot);
            if (!saveFile.isFile()) continue;
            long modified = saveFile.lastModified();
            if (modified <= latestModified) continue;
            try {
                GameState state = loadFile(saveFile);
                latest = new LatestSave(slot, state.getTurn());
                latestModified = modified;
            } catch (IOException unreadable) {
                // A missing, damaged or unsupported checkpoint does not hide another slot.
            }
        }
        return latest;
    }

    private File fileForSlot(int slot) {
        if (slot < FIRST_SLOT || slot > SLOT_COUNT) {
            throw new IllegalArgumentException("Save slot must be between " + FIRST_SLOT + " and " + SLOT_COUNT);
        }
        return slotFiles[slot - 1];
    }

    private static GameState loadFile(File saveFile) throws IOException {
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
