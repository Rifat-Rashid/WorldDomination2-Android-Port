package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import com.example.worlddomination.game.audio.OriginalAudio;

import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Direct-root roles independently observed in the legal source submarine trace. */
public final class SourceRocketDisplayDepthsTest {
    @Test
    public void failedSubmarineRetainsFirstOfTwoSourceSelfExplosions() {
        BattlefieldDisplayState state = new BattlefieldDisplayState();
        state.attach(BattlefieldDisplayState.Kind.AUTHORED, 0,
                "sourceRootMaximumBeforeRocket", null, 22508);
        FlashRocketAnimation animation = FlashRocketAnimation.forSourceSubmarine(
                FlashBattleRoutes.createSubmarineRocketStrike(0, 1, 2, true),
                "explosion_bioload_3", OriginalAudio.Cue.EXPLOSION_MEDIUM,
                new ReferenceRandom(0L), 5);
        SourceStoppedClipQueue stopped = new SourceStoppedClipQueue();
        SourceRocketDisplayDepths depths = new SourceRocketDisplayDepths(
                state, animation, 0, () -> sourceExplosion(), stopped);
        SourceRocketDisplayDepths.Binding binding = depths.binding();
        for (int tick = 235; tick <= 580; tick++) {
            depths.advanceAttachedTimelines();
            animation.tickSourceFrame(tick >= 424);
            depths.afterUnitFrame();
            if (tick == 517) {
                assertEquals(1, binding.getExplosionCount());
                assertEquals(22511, binding.explosion(0).getDepth());
                assertEquals(100.70786948030306d, binding.getExplosionX(0), 0.00001d);
                assertEquals(108.9801612444989d, binding.getExplosionY(0), 0.00001d);
            }
            if (tick == 518) {
                assertEquals(2, binding.getExplosionCount());
                assertEquals(22512, binding.explosion(1).getDepth());
                assertEquals(101.09191525855329d, binding.getExplosionX(1), 0.00001d);
                assertEquals(100.15614094316078d, binding.getExplosionY(1), 0.00001d);
                assertEquals(1, stopped.size());
            }
            if (tick == 538) assertEquals(21, binding.getExplosionFrameIndex(0));
            if (tick == 539) assertEquals(21, binding.getExplosionFrameIndex(1));
            if (tick == 579) {
                assertEquals(1, stopped.removeAtAllUnitsDone());
                assertTrue(binding.explosion(0).isAttached());
                assertFalse(binding.explosion(1).isAttached());
                assertTrue(binding.dot().isAttached());
            }
        }
    }

    @Test
    public void bodyDotSmokeAndStoppedExplosionReuseSourceDepths() {
        BattlefieldDisplayState state = new BattlefieldDisplayState();
        state.attach(BattlefieldDisplayState.Kind.AUTHORED, 0,
                "sourceRootMaximumBeforeRocket", null, 22508);
        FlashBattleRoutes.RocketStrike route = FlashBattleRoutes.createSubmarineRocketStrike(
                0, 1, 2);
        FlashRocketAnimation animation = FlashRocketAnimation.forSourceSubmarine(
                route, "explosion_bioload_3", null, new ReferenceRandom(0L), 5);
        SourceStoppedClipQueue stopped = new SourceStoppedClipQueue();
        AtomicInteger timelineLoads = new AtomicInteger();
        SourceRocketDisplayDepths depths = new SourceRocketDisplayDepths(
                state, animation, 0, () -> {
                    timelineLoads.incrementAndGet();
                    return sourceExplosion();
                },
                stopped);
        SourceRocketDisplayDepths.Binding binding = depths.binding();

        assertEquals(22509, binding.body().getDepth());
        assertEquals(22510, binding.dot().getDepth());
        assertSame(binding, binding.body().getValue().owner);
        assertSame(binding, binding.dot().getValue().owner);
        assertEquals("Timeline must remain lazy across confirm", 0, timelineLoads.get());

        for (int tick = 235; tick <= 640; tick++) {
            depths.advanceAttachedTimelines();
            animation.tickSourceFrame(tick >= 424);
            depths.afterUnitFrame();
            if (tick == 465) {
                assertEquals(0, timelineLoads.get());
                assertEquals(22511, binding.smoke(0).getDepth());
                assertEquals(22512, binding.smoke(1).getDepth());
                assertEquals(22513, binding.smoke(2).getDepth());
                assertSame(binding, binding.smoke(0).getValue().owner);
            }
            if (tick == 577) {
                assertEquals(0, timelineLoads.get());
                assertFalse(binding.body().isAttached());
                assertTrue(binding.dot().isAttached());
                assertFalse(binding.smoke(0).isAttached());
                assertEquals(22511, binding.explosion().getDepth());
                assertEquals(0, binding.getExplosionFrameIndex());
            }
            if (tick == 578) {
                assertEquals(1, timelineLoads.get());
                assertEquals(1, binding.getExplosionFrameIndex());
                assertEquals(1, stopped.size());
            }
            if (tick == 600) assertEquals(23, binding.getExplosionFrameIndex());
            if (tick == 640) {
                assertEquals(54, binding.getExplosionFrameIndex());
                assertEquals(1, stopped.removeAtAllUnitsDone());
                assertFalse(binding.explosion().isAttached());
                assertTrue(binding.dot().isAttached());
            }
        }
    }

    @Test
    public void sourceExplosionStopFramesMatchAllNinePackagedActionFrames() throws Exception {
        Path asset = Paths.get("app/src/main/assets/original/runtime/scene.json.gz");
        if (!Files.isRegularFile(asset)) {
            asset = Paths.get("src/main/assets/original/runtime/scene.json.gz");
        }
        JsonValue scene;
        try (InputStream stream = Files.newInputStream(asset);
             GZIPInputStream gzip = new GZIPInputStream(stream);
             InputStreamReader reader = new InputStreamReader(gzip,
                     StandardCharsets.UTF_8)) {
            scene = new JsonReader().parse(reader);
        }
        int checked = 0;
        for (JsonValue symbol = scene.get("symbols").child;
             symbol != null; symbol = symbol.next) {
            String linkage = symbol.getString("linkage", "");
            if (!linkage.startsWith("explosion_bioload_")
                    && !linkage.startsWith("explosion_warhead_")
                    && !"explosion003".equals(linkage)) continue;
            JsonValue timeline = symbol.get("timeline");
            int authoredStop = timeline.get("action_frames").getInt(0);
            int frames = timeline.get("frames").size;
            assertEquals(linkage, authoredStop,
                    SourceRocketDisplayDepths.sourceExplosionStopFrame(linkage, frames));
            checked++;
        }
        assertEquals(9, checked);
    }

    private static FlashAnimationTimeline sourceExplosion() {
        StringBuilder frames = new StringBuilder();
        for (int index = 0; index < 61; index++) {
            if (index > 0) frames.append(',');
            frames.append("[]");
        }
        return FlashAnimationTimeline.fromSymbol(new JsonReader().parse(
                "{\"id\":1577,\"linkage\":\"explosion_bioload_3\","
                        + "\"timeline\":{\"frames\":[" + frames + "]}}"), 24f);
    }
}
