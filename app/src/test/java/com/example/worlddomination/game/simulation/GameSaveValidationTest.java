package com.example.worlddomination.game.simulation;

import com.example.worlddomination.game.persistence.GameSaveCodec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.junit.Test;
import static org.junit.Assert.*;

public final class GameSaveValidationTest {
    @Test public void rejectsChecksummedDirectAttackWithoutAnOpponent() throws IOException {
        byte[] save = directAttackSave();
        int address = directAttackAddress(save);
        assertEquals(1, ByteBuffer.wrap(save).getInt(address));
        ByteBuffer.wrap(save).putInt(address, -1);
        refreshChecksum(save);
        assertThrows(IOException.class, () -> GameSaveCodec.decode(save));
    }

    @Test public void rejectsChecksummedDirectAttackWithWrongWeaponType() throws IOException {
        byte[] save = directAttackSave();
        int nameStart = directAttackAddress(save) + 2 * Integer.BYTES + 1 + Short.BYTES;
        assertEquals("soldier1", new String(save, nameStart, 8, StandardCharsets.US_ASCII));
        // A valid catalog key of the same length, so the rest of the record is unaffected.
        byte[] otherWeapon = "warhead1".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(otherWeapon, 0, save, nameStart, otherWeapon.length);
        refreshChecksum(save);
        assertThrows(IOException.class, () -> GameSaveCodec.decode(save));
    }

    private static byte[] directAttackSave() throws IOException {
        GameState game = new GameState(1, 8L, false);
        game.getHuman().getWeapons().get("soldier1").inStock = 1;
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(1, 0, "soldier1"));
        assertTrue(game.acceptSelectedAction());
        byte[] save = GameSaveCodec.encode(game);
        assertEquals(GameState.Action.ATTACK, GameSaveCodec.decode(save).getQueuedAction(0));
        return save;
    }

    private static int directAttackAddress(byte[] save) {
        byte[] prefix = {0, 6, 'A', 'T', 'T', 'A', 'C', 'K'};
        for (int i = 12; i <= save.length - prefix.length; i++) {
            int count = 0;
            while (count < prefix.length && save[i + count] == prefix[count]) count++;
            if (count == prefix.length) {
                // Empty production, no sliders, empty research, then reserved funds.
                return i + prefix.length + 3 * Integer.BYTES + 1;
            }
        }
        throw new AssertionError("Queued attack record is missing");
    }

    private static void refreshChecksum(byte[] save) {
        CRC32 checksum = new CRC32();
        int headerSize = 3 * Integer.BYTES;
        checksum.update(save, headerSize, save.length - headerSize);
        ByteBuffer.wrap(save).putInt(2 * Integer.BYTES, (int) checksum.getValue());
    }
}
