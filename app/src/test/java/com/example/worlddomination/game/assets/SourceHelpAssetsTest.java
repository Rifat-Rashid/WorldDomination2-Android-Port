package com.example.worlddomination.game.assets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Packaged originals must agree with the independently executed source Help_data. */
public final class SourceHelpAssetsTest {
    private static final Path ROOT = Path.of("src/main/assets/original/ui/help");
    private static JsonValue pages() throws Exception {
        return new JsonReader().parse(Files.readString(ROOT.resolve("manifest.json"))).get("pages");
    }
    @Test public void allOriginalPagesHaveMatchingSourceFramesAndVisibleControls() throws Exception {
        JsonValue expected = new JsonReader().parse(Files.readString(Path.of("src/test/resources/source-help-navigation.json"))).get("pages");
        JsonValue actual = pages(); assertEquals(28, actual.size); assertEquals(expected.size, actual.size);
        for (int i=0;i<actual.size;i++) {
            JsonValue page=actual.get(i), source=expected.get(i);
            assertEquals(source.getString("topic"),page.getString("topic"));
            assertEquals(source.getInt("frame"),page.getInt("frame"));
            Set<String> names=new HashSet<>();
            for(JsonValue control:page.get("controls")) {
                assertTrue("duplicate control",names.add(control.getString("name")));
                JsonValue b=control.get("bounds");assertEquals(4,b.size);
                assertTrue(b.getFloat(2)>0);assertTrue(b.getFloat(3)>0);
                assertTrue(b.getFloat(0)+b.getFloat(2)>0);assertTrue(b.getFloat(0)<365);
                assertTrue(b.getFloat(1)+b.getFloat(3)>0);assertTrue(b.getFloat(1)<275);
            }
            assertEquals(source.getBoolean("index"),names.remove("btnIndex"));
            assertEquals(source.getBoolean("previous"),names.remove("btnPrev"));
            assertEquals(source.getBoolean("next"),names.remove("btnNext"));
            for(JsonValue link:source.get("links")) {
                String name=link.getString("_mcName");assertTrue(names.remove(name));
                for(JsonValue control:page.get("controls")) if(name.equals(control.getString("name")))
                    assertEquals(link.getString("_trgtString"),control.getString("target"));
            }
            assertTrue("unrecognized source control",names.isEmpty());
        }
    }
    @Test public void allLosslessPagesKeepOriginalThreeTimesDimensionsAndHashes() throws Exception {
        Set<String> assets=new HashSet<>();
        for(JsonValue page:pages()) {
            String name=page.getString("asset");assertTrue(assets.add(name));
            assertEquals(page.getString("topic")+".png",name);
            byte[] bytes=Files.readAllBytes(ROOT.resolve(name));
            assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10},java.util.Arrays.copyOf(bytes,8));
            ByteBuffer header=ByteBuffer.wrap(bytes);
            assertEquals(1095,header.getInt(16));assertEquals(825,header.getInt(20));
            assertEquals(8,bytes[24]);assertEquals(6,bytes[25]); // straight RGBA, lossless PNG
            StringBuilder hash=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            assertEquals(name,page.getString("sha256"),hash.toString());
        }
        try(java.util.stream.Stream<Path> files=Files.list(ROOT)) {
            assertEquals(28,files.filter(p->p.toString().endsWith(".png")).count());
        }
    }
}
