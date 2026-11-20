package com.example.worlddomination.game.persistence;

import com.example.worlddomination.game.simulation.GameState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

/** Versioned file format for one settled offline match checkpoint. */
public final class GameSaveCodec {
    public static final int MAX_SAVE_BYTES = 2 * 1024 * 1024;
    private static final int MAGIC = 0x57443253; // WD2S
    private static final int VERSION = 1;
    private static final int HEADER_BYTES = 3 * Integer.BYTES;

    private GameSaveCodec() { }

    public static byte[] encode(GameState game) throws IOException {
        if (game == null) throw new IOException("There is no match to save");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            game.writeCheckpoint(output);
        }
        byte[] payload = buffer.toByteArray();
        if (payload.length > MAX_SAVE_BYTES - HEADER_BYTES) {
            throw new IOException("Saved match exceeds the file size limit");
        }
        CRC32 checksum = new CRC32();
        checksum.update(payload);
        ByteArrayOutputStream file = new ByteArrayOutputStream(HEADER_BYTES + payload.length);
        try (DataOutputStream output = new DataOutputStream(file)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeInt((int) checksum.getValue());
            output.write(payload);
        }
        return file.toByteArray();
    }

    public static GameState decode(byte[] encoded) throws IOException {
        if (encoded == null || encoded.length <= HEADER_BYTES || encoded.length > MAX_SAVE_BYTES) {
            throw new IOException("Invalid saved match file size");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            if (input.readInt() != MAGIC) throw new IOException("Invalid saved match header");
            if (input.readInt() != VERSION) throw new IOException("Unsupported saved match version");
            int expectedChecksum = input.readInt();
            CRC32 checksum = new CRC32();
            checksum.update(encoded, HEADER_BYTES, encoded.length - HEADER_BYTES);
            if (expectedChecksum != (int) checksum.getValue()) {
                throw new IOException("Saved match checksum does not match");
            }
            GameState game = GameState.readCheckpoint(input);
            if (input.read() != -1) throw new IOException("Unexpected data after saved match");
            return game;
        }
    }
}
