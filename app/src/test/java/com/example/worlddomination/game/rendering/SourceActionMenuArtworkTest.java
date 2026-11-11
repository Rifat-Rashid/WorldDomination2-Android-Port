package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public final class SourceActionMenuArtworkTest {
    private static Path asset(String name) {
        Path path = Paths.get("src/main/assets/original/ui/action-menus", name);
        return Files.exists(path) ? path : Paths.get("app").resolve(path);
    }
    private static JsonValue menus() throws Exception {
        return new JsonReader().parse(Files.readString(asset("menus.json")));
    }
    @Test public void everySourceVariantHasFourTimesArtworkAndValidTextGeometry() throws Exception {
        JsonValue menus = menus(); assertEquals(96, menus.size);
        for (JsonValue menu : menus) {
            try (DataInputStream png = new DataInputStream(Files.newInputStream(asset(menu.name + ".png")))) {
                assertEquals(0x89504e470d0a1a0aL, png.readLong());
                png.skipBytes(8); assertEquals(1460, png.readInt()); assertEquals(1100, png.readInt());
            }
            for (JsonValue field : menu.get("fields")) {
                JsonValue b = field.get("bounds");
                assertTrue(menu.name + ": " + field.getString("name"), b.getFloat(2) > 0 && b.getFloat(3) > 0);
                assertTrue(field.getFloat("size") > 0);
            }
        }
    }
    @Test public void everySoldierCarrierDefenseAndSpyBuildingStateExists() throws Exception {
        JsonValue menus = menus();
        for (int i = 1; i <= 4; i++) {
            assertTrue(menus.has("attack2-soldier" + i)); assertTrue(menus.has("preAttack-rocket" + i));
        }
        for (String carrier : new String[]{"plane1", "plane2", "sub", "ship", "space"})
            assertTrue(menus.has("preAttack-" + carrier));
        for (String religion : new String[]{"chr", "mus", "bhu", "com", "jew"})
            for (String building : new String[]{"spyCenter", "tech", "religion", "resources", "factory"})
                assertTrue(menus.has("attack-spy-" + religion + "-" + building));
        assertTrue(menus.has("defense-antiAir")); assertTrue(menus.has("defense-antiRocket"));
    }
    @Test public void diplomacyHasFourOriginalPortraitsSixOptionsAndSelectionCorners() throws Exception {
        JsonValue menu = menus().get("diplomacy");
        assertEquals(4, menu.get("portraits").size); assertEquals(4, menu.get("selectedPortraits").size);
        for (int i = 1; i <= 6; i++) {
            boolean found = false;
            for (JsonValue control : menu.get("controls")) found |= control.getString("name").equals("btn" + i);
            assertTrue(found);
        }
        JsonValue fields = menu.get("fields");
        for (JsonValue field : fields) if (field.getString("name").equals("textFunds")) {
            assertEquals(229.5f, field.get("bounds").getFloat(0), .001f);
            assertEquals(7.75f, field.get("bounds").getFloat(1), .001f);
            assertEquals(133.7f, field.get("bounds").getFloat(2), .001f);
            return;
        }
        fail("Missing original live budget field");
    }
    @Test public void sourceTargetSelectionFramesAreTransparentMapSteps() throws Exception {
        JsonValue menus = menus();
        for (String key : new String[]{"attack1", "propaganda1", "preAttack4", "preAttack4-ship"})
            assertTrue(menus.get(key).getBoolean("map"));
        assertFalse(menus.get("attackSelect").getBoolean("map"));
        assertFalse(menus.get("propaganda2").getBoolean("map"));
    }
    @Test public void menuFitKeepsUniformSourceScaleAcrossLandscapeContainers() {
        for (Rectangle container : new Rectangle[]{new Rectangle(20, 40, 800, 300),
                new Rectangle(13, 7, 320, 400), new Rectangle(0, 0, 730, 550)}) {
            Rectangle fit = SourceActionMenuRenderer.fit(container, new Rectangle());
            assertEquals(fit.width / 365f, fit.height / 275f, .00001f);
            assertTrue(fit.x >= container.x - .001f && fit.y >= container.y - .001f);
            assertTrue(fit.x + fit.width <= container.x + container.width + .001f);
            assertTrue(fit.y + fit.height <= container.y + container.height + .001f);
            assertEquals(container.x + container.width / 2, fit.x + fit.width / 2, .001f);
            assertEquals(container.y + container.height / 2, fit.y + fit.height / 2, .001f);
        }
    }
    @Test public void allFiveMassMediaStylesUseOriginalFieldAndProgressRegistration() throws Exception {
        JsonValue menus = menus();
        for (String style : new String[]{"chr", "mus", "bhu", "com", "jew"}) {
            JsonValue menu = menus.get("religion-" + style);
            assertNotNull(menu); assertEquals("religion", menu.getString("frame"));
            assertFalse(menu.getBoolean("map"));
            JsonValue bar = menu.get("opinion"); assertFalse(bar.isNull());
            assertEquals(229.45f, bar.getFloat(0), .001f);
            assertEquals(121.45f, bar.getFloat(1), .001f);
            boolean gain = false;
            for (JsonValue field : menu.get("fields")) if (field.getString("name").equals("text3")) {
                assertEquals(140.2f, field.get("bounds").getFloat(1), .001f);
                assertEquals(76f, field.get("bounds").getFloat(2), .001f);
                gain = true;
            }
            assertTrue(gain);
        }
    }

    @Test public void skipUsesTheRecoveredDialogAndFooterRegistration() throws Exception {
        JsonValue menu = menus().get("skip_action");
        assertNotNull(menu); assertFalse(menu.getBoolean("map"));
        assertEquals("skip_action", menu.getString("frame"));
        assertEquals(-4.95f, menu.get("origin").getFloat(0), .001f);
        assertEquals(1.05f, menu.get("origin").getFloat(1), .001f);
        boolean prompt = false;
        for (JsonValue field : menu.get("fields"))
            if (field.getString("text").contains("Accept to skip this action and leave it empty.")) prompt = true;
        assertTrue(prompt); assertTrue(menu.getBoolean("buttons"));
        assertEquals(2, menu.get("controls").size);
    }
}
