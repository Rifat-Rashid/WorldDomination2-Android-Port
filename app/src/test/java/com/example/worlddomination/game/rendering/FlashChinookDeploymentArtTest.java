package com.example.worlddomination.game.rendering;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Presentation state recovered from the Chinook and tank Flash display lists. */
public final class FlashChinookDeploymentArtTest {
    @Test
    public void delayedStartKeepsSourceClipsAttachedBeforeFirstActiveTick()
            throws Exception {
        FlashChinookTankAnimation animation = animation(2);
        FlashChinookTankAnimation.Actor carrier = animation.getActor(0);
        FlashChinookTankAnimation.Actor base = animation.getActor(1);
        FlashChinookTankAnimation.Actor shadow = animation.getChinookShadow();

        assertTrue("source constructor attaches a visible carrier", carrier.visible);
        assertTrue("native repaired source linkage attaches a visible tank", base.visible);
        assertEquals("painted body starts at parent origin", 0f, carrier.renderX, 0f);
        assertEquals(0f, carrier.renderY, 0f);
        assertEquals(0f, base.renderX, 0f);
        assertEquals(0f, base.renderY, 0f);

        for (int tick = 0; tick < 2; tick++) {
            animation.tick();
            assertTrue(carrier.visible);
            assertTrue(base.visible);
            assertTrue(shadow.visible);
            assertFalse("waiting does not launch audio", animation.takeAudioCue());
            assertFalse(animation.isTankShadowVisible());
            assertFalse(animation.isTankDeployed());
        }

        animation.tick();
        assertTrue(carrier.visible);
        assertTrue(base.visible);
        assertTrue(shadow.visible);
        assertEquals("tank_noshade", base.linkage);
        assertFalse(animation.isTankShadowVisible());
        assertEquals(40f, animation.getTankShadowOffsetY(), 0f);
    }

    @Test
    public void sourceFrameRewindsResetChildOffsetWithoutHidingTheDropShadow() throws Exception {
        FlashChinookTankAnimation animation = animation(0);
        // Executed source shadow local Y minus its authored -3.15 placement.
        float[] offsets = {0, 0, 0, 0, 0, 0, -2, -4, 0, -2,
                -4, -6, -8, -10, -12, -14, -16, -18, -20, -22};
        for (int tick = 1; tick <= 51; tick++) {
            animation.tick();
            if (tick < 32) {
                assertFalse("Shadow starts hidden at tick " + tick,
                        animation.isTankShadowVisible());
                continue;
            }
            FlashChinookTankAnimation.Actor base = animation.getActor(1);
            int descent = tick - 31;
            assertTrue("Drop shadow at tick " + tick, animation.isTankShadowVisible());
            assertEquals("Source drop offset at tick " + tick,
                    offsets[descent - 1], animation.getTankShadowOffsetY(), 0f);
            assertEquals("tank_noshade", base.linkage);
        }
        assertEquals(-22f, animation.getTankShadowOffsetY(), 0f);
    }

    @Test
    public void helicopterShadowTracksPositionFrameAndVisibilityThroughFlyOff()
            throws Exception {
        FlashChinookTankAnimation animation = animation(0);
        FlashChinookTankAnimation.Actor carrier = animation.getActor(0);
        FlashChinookTankAnimation.Actor shadow = animation.getChinookShadow();
        assertEquals("chinook_shadow", shadow.linkage);
        for (int tick = 1; tick <= 180; tick++) {
            animation.tick();
            assertSame("Stable shadow actor", shadow, animation.getChinookShadow());
            assertEquals("Shadow frame at tick " + tick,
                    carrier.sourceFrame, shadow.sourceFrame);
            assertEquals("Shadow X at tick " + tick, carrier.sourceX, shadow.sourceX, 0f);
            assertEquals("Shadow Y at tick " + tick,
                    carrier.sourceY + 70f, shadow.sourceY, 0f);
            assertEquals("Shadow visibility at tick " + tick,
                    carrier.visible, shadow.visible);
            assertEquals("Shadow island crop at tick " + tick,
                    carrier.islandPlayerId, shadow.islandPlayerId);
        }
    }

    @Test
    public void deploymentSwapsBodyAndPreservesStableActorsAndCropOwnership()
            throws Exception {
        FlashChinookTankAnimation animation = animation(0);
        FlashChinookTankAnimation.Actor carrier = animation.getActor(0);
        FlashChinookTankAnimation.Actor base = animation.getActor(1);
        FlashChinookTankAnimation.Actor turret = animation.getActor(2);
        FlashChinookTankAnimation.Actor shadow = animation.getChinookShadow();
        for (int tick = 1; tick <= 51; tick++) {
            animation.tick();
            assertEquals("Attached base crop at tick " + tick,
                    carrier.islandPlayerId, base.islandPlayerId);
            assertEquals("Attached turret crop at tick " + tick,
                    carrier.islandPlayerId, turret.islandPlayerId);
        }
        assertFalse(animation.isTankDeployed());
        assertTrue(animation.isTankShadowVisible());
        assertEquals("tank_noshade", base.linkage);
        assertEquals(2, base.islandPlayerId);
        assertEquals(base.islandPlayerId, turret.islandPlayerId);
        assertEquals(carrier.islandPlayerId, shadow.islandPlayerId);

        animation.tick(); // Source tick 52 replaces the attached tank movie clip.
        assertTrue(animation.isTankDeployed());
        assertFalse(animation.isTankShadowVisible());
        assertEquals("tankBase", base.linkage);
        assertSame(carrier, animation.getActor(0));
        assertSame(base, animation.getActor(1));
        assertSame(turret, animation.getActor(2));
        assertSame(shadow, animation.getChinookShadow());
        assertEquals(2, base.islandPlayerId);
        assertEquals(base.islandPlayerId, turret.islandPlayerId);
        assertEquals(carrier.islandPlayerId, shadow.islandPlayerId);
    }

    private static FlashChinookTankAnimation animation(int delayTicks) throws Exception {
        Path anchors = Paths.get("src/main/assets/original/runtime/battle_anchors.json");
        if (!Files.isRegularFile(anchors)) {
            anchors = Paths.get("app").resolve(anchors);
        }
        Map<String, FlashBattleRoutes.Point> points = FlashBattleRoutes.parseAnchors(
                new String(Files.readAllBytes(anchors), StandardCharsets.UTF_8));
        return new FlashChinookTankAnimation(FlashBattleRoutes.createSoldierThreeAttack(
                0, 2, 0, 2, "green", points), delayTicks);
    }
}
