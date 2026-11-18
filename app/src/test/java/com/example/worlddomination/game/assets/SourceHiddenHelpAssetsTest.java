package com.example.worlddomination.game.assets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

/** Byte-pinned authored capture; GPU rendering and dynamic glyph comparison are separate. */
public final class SourceHiddenHelpAssetsTest {
    private static final Path ROOT = Path.of("src/main/assets/original/ui/help/hidden-art");
    @Test public void blankCheatArtworkIsTheThreeTimesAuthoredSourceCrop() throws Exception {
        JsonValue data = new JsonReader().parse(Files.readString(ROOT.resolve("hidden-manifest.json")));
        byte[] png = Files.readAllBytes(ROOT.resolve(data.getString("asset")));
        assertEquals("df16f460b289cdf15589b6eaeddd7fba92a859c89246aeb087525a65100b28cd", sha(png));
        assertEquals(data.getString("sha256"), sha(png));
        assertEquals(1095, ByteBuffer.wrap(png).getInt(16));
        assertEquals(825, ByteBuffer.wrap(png).getInt(20));
        assertEquals(135, data.getInt("frame"));
        assertEquals("", data.getString("capturedCommandText"));
        assertTrue(data.getBoolean("source1xCropExact"));
        assertEquals("8dfc9c6d65ede393344958e172c1f0c6336ac573f24258dfb34be2162a86c27c",
                data.getString("independentSourceScreenshotSha256"));
        assertArrayEquals(new int[]{205, 135, 365, 275}, data.get("crop").asIntArray());
        assertEquals(30, data.get("commandInput").getFloat("size"), 0f);
        assertEquals(2, data.get("commandInput").getInt("align"));
        assertFalse(data.get("commandInput").getBoolean("word_wrap"));
        assertEquals(16, data.get("passwordInput").getFloat("size"), 0f);
        try (java.util.stream.Stream<Path> files = Files.list(ROOT.getParent())) {
            assertEquals("Hidden pages must not alter the existing 28-page set", 28,
                    files.filter(path -> path.toString().endsWith(".png")).count());
        }
    }

    @Test public void warningRetainsActualStaticHeaderAndOriginalOkControl() throws Exception {
        JsonValue data = new JsonReader().parse(Files.readString(ROOT.resolve("hidden-manifest.json"))).get("warning");
        assertEquals("original/ui/help/hidden-art/hidden-warning-frame7.png", data.getString("asset"));
        assertTrue(data.getBoolean("staticHeaderBaked"));
        assertEquals("", data.getString("capturedDynamicBody"));
        assertEquals("0f076f7113312fab0c7630e59599b002cca64d9fa9889ead670ab31a1521dec5", data.getString("sha256"));
        assertEquals(data.getString("sha256"), sha(Files.readAllBytes(Path.of("src/main/assets").resolve(data.getString("asset")))));
        JsonValue source = new JsonReader().parse(Files.readString(Path.of("src/main/assets/original/ui/action-menus/menus.json"))).get("production_error");
        assertEquals(source.get("fields").toString(), data.get("fields").toString());
        assertEquals("error_window/btnOK", data.get("controls").get(0).getString("name"));
        assertEquals(160.2174f, data.get("controls").get(0).get("bounds").getFloat(0), .0001f);
        assertEquals(152.2174f, data.get("controls").get(0).get("bounds").getFloat(1), .0001f);
    }

    @Test public void dynamicFontsUseAuthoredTtfsAndExactSourceIntegerAdvances() throws Exception {
        JsonValue command = new JsonReader().parse(Files.readString(ROOT.resolve("hidden-font-command-120.json")));
        assertEquals("65b4697329429a8a73fee6bab5ae71cc340693ca954521d949bfb1a33c291bc7", command.getString("sourceTTFSHA256"));
        assertEquals(120, command.getInt("size"));
        assertEquals(307, command.get("probes").get("trump").getFloat("advance"), 0f);
        assertEquals(540, command.get("probes").get("I am Bush").getFloat("advance"), 0f);
        JsonValue warning = new JsonReader().parse(Files.readString(ROOT.resolve("hidden-font-warning-40.json")));
        assertEquals("3322_Arial_10pt_st.ttf.gz", warning.getString("source"));
        assertEquals(40, warning.getInt("size"));
        for (String name : new String[]{"hidden-font-command-120", "hidden-font-warning-40"}) {
            String fnt = Files.readString(ROOT.resolve(name + ".fnt"));
            assertTrue(fnt.contains("kernings count=0"));
            assertTrue(fnt.contains("file=\"" + name + ".png\""));
            assertTrue(Files.size(ROOT.resolve(name + ".png")) > 1000);
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes))
            result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
}
