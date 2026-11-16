package com.example.worlddomination.game.ui;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.Assert.*;

/** Comparisons use independently captured Help_data and source View controls. */
public final class SourceHelpStateTest {
    private static final String FIXTURE = "source-help-navigation.json";

    @Test public void allFiveIndexLinksFollowCapturedTargets() throws Exception {
        JsonValue index = pages().get(0);
        assertEquals(5, index.get("links").size);
        for (JsonValue link : index.get("links")) {
            SourceHelpState state = opened();
            assertTrue(state.activate(link.getString("_mcName")));
            assertEquals(link.getString("_trgtString"), state.getTopic());
        }
    }

    @Test public void nextVisitsEverySequentialTopicWithCapturedControls() throws Exception {
        JsonValue pages = pages();
        assertEquals(28, pages.size);
        SourceHelpState state = opened();
        assertTrue(state.activate("btn_overview"));
        for (int i = 3; i < pages.size; i++) {
            JsonValue expected = pages.get(i);
            assertEquals(i, expected.getInt("id"));
            assertEquals(expected.getString("topic"), state.getTopic());
            assertEquals(expected.getBoolean("index"), state.hasIndex());
            assertEquals(expected.getBoolean("previous"), state.hasPrevious());
            assertEquals(expected.getBoolean("next"), state.hasNext());
            assertEquals(i + 1 < pages.size, state.activate("btnNext"));
        }
        assertEquals(pages.get(27).getString("topic"), state.getTopic());
    }

    @Test public void previousMatchesSourceAtEveryTopicAndDoesNotWrap() throws Exception {
        JsonValue pages = pages();
        for (int i = 3; i < pages.size; i++) {
            SourceHelpState state = page(i);
            boolean allowed = pages.get(i).getBoolean("previous");
            assertEquals(allowed, state.activate("btnPrev"));
            assertEquals(pages.get(allowed ? i - 1 : i).getString("topic"), state.getTopic());
        }
        SourceHelpState index = opened();
        assertFalse(index.activate("btnPrev"));
        assertEquals(pages.get(0).getString("topic"), index.getTopic());
    }

    @Test public void submenuLinksAndHiddenIndexMatchCapturedData() throws Exception {
        JsonValue pages = pages();
        for (int i = 1; i <= 2; i++) {
            JsonValue expected = pages.get(i);
            SourceHelpState submenu = submenu(i);
            assertEquals(expected.getString("topic"), submenu.getTopic());
            assertEquals(expected.getBoolean("index"), submenu.hasIndex());
            assertFalse(submenu.activate("btnIndex"));
            assertFalse(submenu.hasPrevious());
            assertFalse(submenu.hasNext());
            for (JsonValue link : expected.get("links")) {
                String target = link.getString("_trgtString");
                boolean exists = containsTopic(pages, target);
                SourceHelpState selected = submenu(i);
                assertEquals(exists, selected.activate(link.getString("_mcName")));
                assertEquals(exists ? target : expected.getString("topic"), selected.getTopic());
            }
        }
    }

    @Test public void missingPreviousAndFinalNextStayOnVisiblePage() throws Exception {
        for (int i : new int[]{8, 11}) {
            SourceHelpState state = page(i);
            String topic = state.getTopic();
            assertFalse(state.hasPrevious());
            assertFalse(state.activate("btnPrev"));
            assertEquals(topic, state.getTopic());
        }
        SourceHelpState last = page(27);
        assertFalse(last.hasNext());
        assertFalse(last.activate("btnNext"));
        assertEquals(pages().get(27).getString("topic"), last.getTopic());
    }

    @Test public void invalidControlsAndBrokenWeaponLinksKeepVisiblePage() {
        SourceHelpState state = submenu(1);
        for (String control : new String[]{"btn_infanterie", "btn_antiweapons", "unknown"}) {
            assertFalse(state.activate(control));
            assertEquals("help_weapondetail", state.getTopic());
        }
        assertFalse(state.activate(null));
    }

    @Test public void openingAndClosingAlwaysResetToIndex() {
        SourceHelpState state = new SourceHelpState();
        assertFalse(state.isOpen());
        assertNull(state.getTopic());
        assertFalse(state.activate("btn_overview"));
        state.open();
        assertTrue(state.activate("btn_overview"));
        state.close();
        assertFalse(state.isOpen());
        assertNull(state.getTopic());
        state.open();
        assertEquals("help_index", state.getTopic());
        state.open();
        assertEquals("help_index", state.getTopic());
    }

    @Test public void hideAndShowPreserveTopicUntilClose() {
        SourceHelpState state = opened();
        assertTrue(state.isVisible());
        assertTrue(state.activate("btn_overview"));
        state.hide();
        assertTrue(state.isOpen());
        assertFalse(state.isVisible());
        assertEquals("help_overview", state.getTopic());
        state.show();
        assertTrue(state.isVisible());
        assertEquals("help_overview", state.getTopic());
        state.close();
        assertFalse(state.isOpen());
        assertFalse(state.isVisible());
        state.show();
        assertFalse(state.isVisible());
        state.open();
        assertTrue(state.isVisible());
        assertEquals("help_index", state.getTopic());
    }

    @Test public void capturedFixtureBytesRemainStable() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(resource());
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        assertEquals("dda754c00f663e8df3ba1a3c5975c29019fa2adc330fea057d3d6474a461fc8a", hex.toString());
    }

    private static SourceHelpState opened() {
        SourceHelpState state = new SourceHelpState();
        state.open();
        return state;
    }

    private static SourceHelpState submenu(int index) {
        SourceHelpState state = opened();
        assertTrue(state.activate(index == 1 ? "btn_weapondetails" : "btn_actiondetails"));
        return state;
    }

    private static SourceHelpState page(int index) {
        SourceHelpState state = opened();
        assertTrue(state.activate("btn_overview"));
        for (int i = 3; i < index; i++) assertTrue(state.activate("btnNext"));
        return state;
    }

    private static boolean containsTopic(JsonValue pages, String topic) {
        for (JsonValue page : pages) if (page.getString("topic").equals(topic)) return true;
        return false;
    }

    private static JsonValue pages() throws Exception {
        return new JsonReader().parse(new String(resource(), StandardCharsets.UTF_8)).get("pages");
    }

    private static byte[] resource() throws Exception {
        try (InputStream input = SourceHelpStateTest.class.getClassLoader().getResourceAsStream(FIXTURE)) {
            assertNotNull(FIXTURE, input);
            return input.readAllBytes();
        }
    }
}
