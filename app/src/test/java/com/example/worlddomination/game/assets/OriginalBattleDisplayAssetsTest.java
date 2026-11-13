package com.example.worlddomination.game.assets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.rendering.FlashAnimationTimeline;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

public final class OriginalBattleDisplayAssetsTest {
    @Test public void cachedBattleParentPreservesEveryOriginalPlacement() throws Exception {
        JsonValue scene = read("scene.json.gz");
        JsonValue cached = read("battle-root.json.gz");
        assertEquals(scene.getString("source_sha256"), cached.getString("source_sha256"));
        assertEquals(scene.getFloat("fps"), cached.getFloat("fps"), 0f);
        FlashAnimationTimeline.Frame original = OriginalFlashAnimationAssets
                .loadTimelineBySymbolId(scene, OriginalBattleDisplayAssets.BATTLE_SYMBOL_ID).getFrame(0);
        FlashAnimationTimeline.Frame copy = FlashAnimationTimeline.fromSymbol(
                cached.get("symbol"), cached.getFloat("fps")).getFrame(0);
        assertEquals(122, original.layers.length);
        assertEquals(original.layers.length, copy.layers.length);
        for (int i = 0; i < original.layers.length; i++) {
            FlashAnimationTimeline.Layer a = original.layers[i], b = copy.layers[i];
            assertEquals(a.depth, b.depth);
            assertEquals(a.shapeId, b.shapeId);
            assertEquals(a.name, b.name);
            assertEquals(a.clipDepth, b.clipDepth);
            assertEquals(a.a, b.a, 0f);
            assertEquals(a.b, b.b, 0f);
            assertEquals(a.c, b.c, 0f);
            assertEquals(a.d, b.d, 0f);
            assertEquals(a.tx, b.tx, 0f);
            assertEquals(a.ty, b.ty, 0f);
            assertEquals(a.alpha, b.alpha, 0f);
        }
    }

    private static JsonValue read(String name) throws Exception {
        Path path = Path.of("src/main/assets/original/runtime", name);
        if (!Files.isRegularFile(path)) path = Path.of("app").resolve(path);
        try (InputStream input = new GZIPInputStream(Files.newInputStream(path));
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return new JsonReader().parse(reader);
        }
    }
}
