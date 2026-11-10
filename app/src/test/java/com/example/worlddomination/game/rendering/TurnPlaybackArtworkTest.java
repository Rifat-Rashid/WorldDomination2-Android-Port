package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameState;
import org.junit.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import static org.junit.Assert.*;

public final class TurnPlaybackArtworkTest {
    @Test public void cachedConfirmAtlasContainsEveryOriginalHatchFrame() throws Exception {
        JsonValue hatch = symbol(4611);
        assertEquals(12, hatch.get("timeline").get("frames").size);
        TextureAtlas.TextureAtlasData data = atlas("confirm-turn.atlas");
        assertEquals(12, data.getRegions().size);
        for (int frame = 1; frame <= 12; frame++) {
            TextureAtlas.TextureAtlasData.Region region = region(data, "confirm" + frame);
            assertEquals(240, region.width);
            assertEquals(228, region.height);
        }
    }

    @Test public void everyActionAndPlayerColorHasOriginalAnnouncementArtwork() throws Exception {
        JsonValue labels = symbol(4683).get("timeline").get("labels");
        TextureAtlas.TextureAtlasData data = atlas("turn-announcements.atlas");
        assertEquals(39, data.getRegions().size);
        for (GameState.Action action : GameState.Action.values()) {
            String label = TurnControlRenderer.sourceLabel(action);
            assertTrue("No recovered source frame for " + action, labels.has(label));
            assertEquals(832, region(data, label).width);
            assertEquals(200, region(data, label).height);
        }
        for (int player = 0; player < 5; player++) {
            for (int color = 0; color < 6; color++) {
                assertEquals(160, region(data, "player" + player + "color" + color).width);
            }
        }
    }

    @Test public void mapOverlayUsesOriginalRegistrationAndUniformSourceProportions() throws Exception {
        JsonValue geometry = new JsonReader().parse(Files.readString(asset("ui/turn-announcements.json")));
        JsonValue display = geometry.get("display");
        assertEquals(314.95f, display.getFloat("x") + 5.4f, 0.001f);
        assertEquals(20f, display.getFloat("y") + 15.05f, 0.001f);
        assertEquals(208f, display.getFloat("width"), 0f);
        assertEquals(50f, display.getFloat("height"), 0f);
        assertEquals(476.7f, geometry.get("scheme").getFloat("x"), 0.001f);
        assertEquals(14.15f, geometry.get("scheme").getFloat("y"), 0.001f);
        assertEquals(30, symbol(5277).get("timeline").get("frames").size);
        assertEquals(FlashActionBuildingAnimation.ACTION_SELECTION_FRAME_COUNT,
                symbol(5277).get("timeline").get("frames").size);
    }

    private static TextureAtlas.TextureAtlasData atlas(String file) {
        FileHandle ui = new FileHandle(asset("ui").toFile());
        return new TextureAtlas.TextureAtlasData(ui.child(file), ui, false);
    }

    private static TextureAtlas.TextureAtlasData.Region region(TextureAtlas.TextureAtlasData data, String name) {
        for (TextureAtlas.TextureAtlasData.Region region : data.getRegions()) {
            if (name.equals(region.name)) return region;
        }
        throw new AssertionError("Missing cached turn artwork " + name);
    }

    private static JsonValue symbol(int id) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(asset("runtime/scene.json.gz"))), StandardCharsets.UTF_8)) {
            JsonValue scene = new JsonReader().parse(reader);
            for (JsonValue symbol : scene.get("symbols")) if (symbol.getInt("id") == id) return symbol;
        }
        throw new AssertionError("Missing original symbol " + id);
    }

    private static Path asset(String relative) {
        Path path = Paths.get("src/main/assets/original", relative);
        return Files.exists(path) ? path : Paths.get("app").resolve(path);
    }
}
