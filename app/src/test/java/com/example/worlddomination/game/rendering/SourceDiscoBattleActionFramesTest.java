package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Legal source jew→disco→jew→disco callback trace on a private battle root. */
public final class SourceDiscoBattleActionFramesTest {
    private static final String FIXTURE_SHA256 =
            "22aaa9da98cc5d6156868e24ee7d59773d8fb42d3666e15e3759a5a4445005ae";

    @Test public void discoConsumesDetachedReligionsSecondFrameOneAndLeavesNoFlame()
            throws Exception {
        JsonValue source = fixture().get("cases");
        assertEquals(source.get("ordinary").getDouble("canonicalAfterTurnRngNext"),
                source.get("disco").getDouble("canonicalAfterTurnRngNext"), 0d);
        assertEquals(2, source.get("ordinary").getInt("sourceFlameStartCount"));
        assertEquals(3, source.get("disco").getInt("sourceFlameStartCount"));
        assertEquals("jew", source.get("disco").get("parentTargets").getString(0));
        assertEquals("disco", source.get("disco").get("parentTargets").getString(1));
        assertEquals("jew", source.get("disco").get("parentTargets").getString(2));
        assertEquals("disco", source.get("disco").get("parentTargets").getString(3));
        assertEquals(4084, source.get("disco").getInt("finalChild"));

        GameState.ActionBuildingEvent event = event("jew");
        FlashActionBuildingAnimation clip = new FlashActionBuildingAnimation(event, null, true);
        assertEquals("jew", clip.sourceReligionCode);
        assertEquals("disco", clip.religionCode);
        ReferenceRandom random = new ReferenceRandom(0L);
        ReferenceRandom expected = random.copy();
        for (int callback = 0; callback < 3; callback++) expected.nextInt(109);
        SourceBattleActionFrames owner = new SourceBattleActionFrames();
        owner.begin(List.of(clip), List.of(event), random, true);
        assertEquals("source first-frame draw count", expected.nextDouble(),
                random.copy().nextDouble(), 0d);
        assertNull("final disco child 4084 has no religion flame", flame(owner, 1));
        assertEquals(0, clip.getNestedFrameIndex(4015, 163));

        FlashActionBuildingAnimation ordinaryClip = new FlashActionBuildingAnimation(event);
        ReferenceRandom ordinary = new ReferenceRandom(0L);
        ReferenceRandom ordinaryExpected = ordinary.copy();
        ordinaryExpected.nextInt(109);
        ordinaryExpected.nextInt(109);
        SourceBattleActionFrames ordinaryOwner = new SourceBattleActionFrames();
        ordinaryOwner.begin(List.of(ordinaryClip), List.of(event), ordinary);
        assertEquals("ordinary source callback count", ordinaryExpected.nextDouble(),
                ordinary.copy().nextDouble(), 0d);
        assertNotNull(flame(ordinaryOwner, 1));
        assertEquals(4015, flameCharacterId(flame(ordinaryOwner, 1)));
    }

    @Test public void discoPoseDoesNotHideMismatchedAuthoredReligion() throws Exception {
        GameState.ActionBuildingEvent shown = event("jew");
        FlashActionBuildingAnimation wrong = new FlashActionBuildingAnimation(event("mus"),
                null, true);
        assertEquals("disco", wrong.religionCode);
        try {
            new SourceBattleActionFrames().begin(List.of(wrong), List.of(shown),
                    new ReferenceRandom(0L), true);
            fail("authored religion must still match source event");
        } catch (IllegalArgumentException expected) {
            assertEquals("Final shown action building is absent from all", expected.getMessage());
        }
    }

    private static JsonValue fixture() throws Exception {
        try (InputStream in = SourceDiscoBattleActionFramesTest.class.getResourceAsStream(
                "/source-disco-building-callbacks.json")) {
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

    private static GameState.ActionBuildingEvent event(String religion) throws Exception {
        Constructor<GameState.ActionBuildingEvent> ctor =
                GameState.ActionBuildingEvent.class.getDeclaredConstructor(int.class,
                        GameState.Action.class, int.class, String.class, String.class, int.class);
        ctor.setAccessible(true);
        return ctor.newInstance(0, GameState.Action.MASS_MEDIA, 2,
                "religion", religion, 0);
    }

    private static Object flame(SourceBattleActionFrames owner, int index) throws Exception {
        Field field = SourceBattleActionFrames.class.getDeclaredField("flames");
        field.setAccessible(true);
        return ((Object[]) field.get(owner))[index];
    }

    private static int flameCharacterId(Object flame) throws Exception {
        Field field = flame.getClass().getDeclaredField("characterId");
        field.setAccessible(true);
        return field.getInt(flame);
    }
}
