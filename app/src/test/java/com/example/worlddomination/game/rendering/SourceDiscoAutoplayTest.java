package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameState;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual source private-battle child 4084 frame sequence, ticks 0..100. */
public final class SourceDiscoAutoplayTest {
    private static final String FIXTURE_SHA256 =
            "b36e7259003eb7fe2dd0c2422021c7c31e11463958be6840b8d3cef361006b0a";

    @Test public void discoChildLoopsAllTwentyFourFramesAfterParentFade() throws Exception {
        JsonValue source = fixture();
        assertEquals(4084, source.getInt("symbolId"));
        assertEquals(24, source.getInt("frameCount"));
        assertTrue(source.getBoolean("allPlaying"));
        assertTrue(source.getBoolean("playbackIdentityRetained"));
        JsonValue frames = source.get("sourceFramesOneBased");
        assertEquals(101, frames.size);

        FlashActionBuildingAnimation disco = new FlashActionBuildingAnimation(
                religionEvent(), null, true);
        Set<Integer> seen = new HashSet<>();
        for (int tick = 0; tick <= 100; tick++) {
            int frame = disco.getNestedFrameIndex(FlashActionBuildingAnimation.DISCO_SYMBOL_ID,
                    source.getInt("frameCount")) + 1;
            assertEquals("source private battle tick " + tick, frames.getInt(tick), frame);
            seen.add(frame);
            assertTrue("autoplay outlives the parent fade", disco.isAnimating());
            if (tick < 100) disco.tick();
        }
        assertEquals(24, seen.size());
        assertEquals("frame 24 wraps to 1", frames.getInt(0), frames.getInt(24));
        assertEquals("second cycle wraps to 1", frames.getInt(0), frames.getInt(48));
        assertEquals(4037, source.get("leafSamples").get("0").getInt(0));
        assertEquals(4039, source.get("leafSamples").get("0").getInt(1));
        assertEquals(4041, source.get("leafSamples").get("2").getInt(1));
        assertEquals(4083, source.get("leafSamples").get("23").getInt(1));

        FlashActionBuildingAnimation ordinary = new FlashActionBuildingAnimation(
                religionEvent(), null, false);
        for (int tick = 0; tick < FlashActionBuildingAnimation.FADE_TICKS; tick++) {
            ordinary.tick();
        }
        assertFalse("ordinary religion has no disco child", ordinary.isAnimating());
        assertEquals(0, ordinary.getNestedFrameIndex(
                FlashActionBuildingAnimation.DISCO_SYMBOL_ID, 24));
    }

    private static JsonValue fixture() throws Exception {
        try (InputStream in = SourceDiscoAutoplayTest.class.getResourceAsStream(
                "/source-disco-100ticks.json")) {
            assertNotNull(in);
            byte[] bytes = in.readAllBytes();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(Character.forDigit((value >>> 4) & 15, 16));
                hex.append(Character.forDigit(value & 15, 16));
            }
            assertEquals(FIXTURE_SHA256, hex.toString());
            return new JsonReader().parse(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static GameState.ActionBuildingEvent religionEvent() throws Exception {
        Constructor<GameState.ActionBuildingEvent> ctor =
                GameState.ActionBuildingEvent.class.getDeclaredConstructor(int.class,
                        GameState.Action.class, int.class, String.class, String.class, int.class);
        ctor.setAccessible(true);
        return ctor.newInstance(0, GameState.Action.MASS_MEDIA, 2,
                "religion", "jew", 0);
    }
}
