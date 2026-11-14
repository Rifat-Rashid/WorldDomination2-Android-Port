package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class FlashApacheMarineAnimationTest {
    @Test public void hiddenMarineTimelinesAutoplayAndWrapWithoutMovingOrDrawingRandom()
            throws Exception {
        FlashApacheMarineAnimation animation = createAnimation(2);
        Field field = FlashApacheMarineAnimation.class.getDeclaredField("presentationRandom");
        field.setAccessible(true);
        ReferenceRandom random = (ReferenceRandom) field.get(animation);
        double next = random.copy().nextDouble();
        for (int tick = 1; tick <= 433; tick++) {
            animation.advanceAttachedTimelines();
            for (int actor = 1; actor <= 3; actor++) {
                assertFalse(animation.getActor(actor).visible);
                assertEquals(tick % 432, animation.getActor(actor).sourceFrame);
                assertEquals(0f, animation.getActor(actor).sourceX, 0f);
                assertEquals(0f, animation.getActor(actor).sourceY, 0f);
            }
            assertEquals(next, random.copy().nextDouble(), 0d);
        }
        assertEquals(0, animation.getActor(0).sourceFrame);
    }

    @Test
    public void waitingApacheUsesSourceOriginThenHeliStartBeforeRecordDelay() throws Exception {
        FlashApacheMarineAnimation animation = createAnimation(2);
        FlashApacheMarineAnimation.Actor apache = animation.getActor(0);

        assertTrue(apache.visible);
        assertEquals(0f, apache.sourceX, 0f);
        assertEquals(0f, apache.sourceY, 0f);
        assertEquals(0.5f, apache.scale, 0f);
        FlashApacheMarineAnimation.Actor shadow = animation.getApacheShadow();
        assertSame(shadow, animation.getApacheShadow());
        assertTrue(shadow.visible);
        assertEquals(0.5f, shadow.scale, 0f);
        animation.presentWaitingFrame();
        assertEquals(396.25f, apache.sourceX, 0f);
        assertEquals(352.25f, apache.sourceY, 0f);
        assertEquals(6, apache.sourceFrame);
        assertEquals(apache.sourceX, shadow.sourceX, 0f);
        assertEquals(apache.sourceY, shadow.sourceY, 0f);
        assertEquals(apache.sourceFrame, shadow.sourceFrame);
        animation.tick();
        assertEquals(396.25f, apache.sourceX, 0f);
        animation.tick();
        assertEquals(396.25f, apache.sourceX, 0f);
        assertTrue(apache.visible);
        assertEquals("apache", apache.linkage);
        assertSame(apache, animation.getActor(0));
    }

    @Test
    public void unloadsThreeMarinesAndEmitsOneOriginalCombatCuePerMarine() throws Exception {
        FlashApacheMarineAnimation animation = createAnimation(0);
        boolean[] becameVisible = new boolean[3];
        int combatCues = 0;
        int tick;
        for (tick = 0; tick < 6000 && !animation.isFinished(); tick++) {
            animation.tick();
            for (int marine = 0; marine < 3; marine++) {
                becameVisible[marine] |= animation.getActor(marine + 1).visible;
            }
            if (animation.takeAudioCue()) {
                combatCues++;
                assertEquals("soldier001_shoot",
                        animation.getActor(combatCues).linkage);
            }
        }

        assertTrue("The Apache, marines, and source shot loops should finish", tick < 6000);
        assertEquals(3, combatCues);
        for (int marine = 0; marine < 3; marine++) {
            assertTrue("Each Apache passenger should become visible", becameVisible[marine]);
            assertTrue("Removed marine clip stays until allUnitsDone",
                    animation.getActor(marine + 1).visible);
        }
        assertTrue("Removed Apache clip stays until allUnitsDone", animation.getActor(0).visible);
        SourceStoppedClipQueue queue = new SourceStoppedClipQueue();
        animation.registerStoppedClips(queue);
        assertEquals(4, queue.size());
        assertEquals(4, queue.removeAtAllUnitsDone());
        assertFalse("Paired Apache shadow detaches with its body", animation.getApacheShadow().visible);
        for (int actor = 0; actor < 4; actor++) {
            assertFalse(animation.getActor(actor).visible);
        }
        assertFalse(animation.takeAudioCue());
    }

    @Test
    public void marineActivationUsesSourceSeededXFrameYRangeOrder() throws Exception {
        Path asset = projectAsset("original/runtime/battle_anchors.json");
        String json = new String(Files.readAllBytes(asset), StandardCharsets.UTF_8);
        Map<String, FlashBattleRoutes.Point> anchors = FlashBattleRoutes.parseAnchors(json);
        FlashBattleRoutes.ApacheMarineAttack route =
                FlashBattleRoutes.createSoldierTwoAttack(0, 1, 1, 1, anchors,
                        new ReferenceRandom(2026L));
        ReferenceRandom activationRandom = new ReferenceRandom(42L);
        FlashApacheMarineAnimation animation = new FlashApacheMarineAnimation(route,
                activationRandom, OriginalAudio.Cue.SEAL_GUN, 0);

        Field marinesField = FlashApacheMarineAnimation.class.getDeclaredField("marines");
        marinesField.setAccessible(true);
        Object marine = ((Object[]) marinesField.get(animation))[0];
        Method start = marine.getClass().getDeclaredMethod("start", float.class,
                float.class, ReferenceRandom.class);
        start.setAccessible(true);
        start.invoke(marine, 0f, 0f, activationRandom);

        FlashApacheMarineAnimation.Actor actor = animation.getActor(1);
        FlashBattleRoutes.Point spawn = route.marines[0].waypoints[0];
        assertTrue(actor.visible);
        // Source Unit.moveUnit draws X, frame offset, Y, then range.
        // CPython Random(42): floor(random()*21)=13, *7=0, *16=4, *6+5=6.
        assertEquals(spawn.x + 13f, actor.sourceX, 0f);
        assertEquals(spawn.y + 4f, actor.sourceY, 0f);
        Field frameOffsetField = marine.getClass().getDeclaredField("frameOffset");
        frameOffsetField.setAccessible(true);
        assertEquals(0, frameOffsetField.getInt(marine));
        Field rangeField = marine.getClass().getDeclaredField("range");
        rangeField.setAccessible(true);
        assertEquals(6, rangeField.getInt(marine));
        assertEquals(0.7364712141640124, activationRandom.nextDouble(), 0d);
    }

    @Test
    public void horizontalLeftDirectionPreservesSourceIntAndFloatZero() throws Exception {
        Method direction = FlashApacheMarineAnimation.class.getDeclaredMethod(
                "directionFrame", double.class, double.class, boolean.class);
        direction.setAccessible(true);
        assertEquals("integer 202 - 202 yields positive zero and angle 361",
                18, direction.invoke(null, -0.5d, 0d, false));
        assertEquals("float 202.0 - 202.0 yields negative opposite and angle 1",
                0, direction.invoke(null, -0.5d, 0d, true));
    }

    @Test
    public void sourceThreeTurnTraceReplaysEveryFrameAgainstNativeActors()
            throws Exception {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology("soldier2"));
        assertTrue(game.acceptSelectedAction());
        queueResources(game);
        game.confirmTurn();
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction("soldier", 100));
        assertTrue(game.acceptSelectedAction());
        queueResources(game);
        game.confirmTurn();
        game.selectAction(GameState.Action.ATTACK);
        assertTrue(game.configureAttack(1, 0, "soldier2"));
        assertTrue(game.acceptSelectedAction());
        queueResources(game);
        game.confirmTurn();

        GameState.CombatEvent event = null;
        for (GameState.CombatEvent candidate
                : game.getLastTurnCombatEventsInSourcePresentationOrder()) {
            if (candidate.attackerPlayerId == 0 && "soldier2".equals(candidate.weaponName)) {
                event = candidate;
            }
        }
        assertTrue("The UI-reachable third turn must resolve a soldier2 attack", event != null);
        SourceBattleRecordClock recordClock = new SourceBattleRecordClock();
        boolean pendingBuilding = false;
        for (GameState.BattlePresentationRecord record : game.getLastTurnBattleRecords()) {
            pendingBuilding |= record.pendingBuilding;
        }
        recordClock.begin(game.getLastTurnBattleRecords().size(), pendingBuilding);
        int recordDelay = recordClock.delayForRecord(event.battleStartNum);
        ReferenceRandom presentationRandom = game.createBattlePresentationRandomCopy();
        String json = new String(Files.readAllBytes(projectAsset(
                "original/runtime/battle_anchors.json")), StandardCharsets.UTF_8);
        FlashBattleRoutes.ApacheMarineAttack route = FlashBattleRoutes.createSoldierTwoAttack(
                event.attackerPlayerId, event.defenderPlayerId, event.cityIndex,
                event.actionBuildingSlot, FlashBattleRoutes.parseAnchors(json),
                presentationRandom);
        FlashApacheMarineAnimation animation = new FlashApacheMarineAnimation(route,
                presentationRandom, OriginalAudio.Cue.SEAL_GUN, recordDelay);
        SourceStoppedClipQueue queue = new SourceStoppedClipQueue();
        TurnPlaybackSequence labels = new TurnPlaybackSequence();
        List<GameState.Action> resolvedActions = new ArrayList<>();
        for (GameState.ActionBuildingEvent building : game.getLastTurnActionBuildings()) {
            resolvedActions.add(building.action);
        }
        labels.begin(resolvedActions);
        SourceBattleTicker ticker = new SourceBattleTicker();
        ticker.begin();
        SourceBattleInterfaceFrames interfaceFrames = new SourceBattleInterfaceFrames();
        interfaceFrames.begin();
        List<String> rows = Files.readAllLines(projectSourceFixture(), StandardCharsets.UTF_8);
        assertEquals("Four source wrappers on every host tick", 1 + 526 * 4, rows.size());
        List<String> shadowRows = Files.readAllLines(projectShadowFixture(), StandardCharsets.UTF_8);
        assertEquals("Apache body/shadow at every host tick", 1 + 526, shadowRows.size());
        Field queuedClipsField = SourceStoppedClipQueue.class.getDeclaredField("clips");
        queuedClipsField.setAccessible(true);
        Field marineTracksField = FlashApacheMarineAnimation.class.getDeclaredField("marines");
        marineTracksField.setAccessible(true);
        Object[] marineTracks = (Object[]) marineTracksField.get(animation);
        Field shotCountField = marineTracks[0].getClass().getDeclaredField("shotCount");
        shotCountField.setAccessible(true);
        // Source array is unit1,unit2,unit3,unit4. Reverse View visitation is
        // Apache,unit3,unit2,unit1; native indices are 0,1,2,3 respectively.
        int[] nativeBySourceRow = {3, 2, 1, 0};
        List<String> mismatches = new ArrayList<>();
        compareDouble(mismatches, "post-route presentation RNG", 0.9086575543967805,
                presentationRandom.copy().nextDouble());
        // The source action-building flame sprite (4010, 146 frames) starts
        // during host construction, before source tick zero. Its first-frame
        // action calls Tools.randRange(2,110), then repeats when playback wraps.
        ActionBuildingFlameClock flame = new ActionBuildingFlameClock(presentationRandom);
        compareDouble(mismatches, "post-host-initialization RNG", 0.4740046511372964,
                presentationRandom.copy().nextDouble());
        for (int tick = 0; tick < 526; tick++) {
            boolean earlyReturn = false;
            if (tick > 0) {
                flame.tick(tick);
                interfaceFrames.tick(presentationRandom);
                TurnPlaybackSequence.Phase previousPhase = labels.getPhase();
                GameState.Action revealed = labels.advance(1f / 24f);
                // Presenter_interface.play_sound_by_building chooses one of
                // four Mass Media religion sounds from the shared source RNG.
                if (revealed == GameState.Action.MASS_MEDIA) {
                    presentationRandom.nextInt(4);
                }
                if (previousPhase == TurnPlaybackSequence.Phase.RESET_BARRIER
                        && labels.getPhase() == TurnPlaybackSequence.Phase.CLEANUP_HANDOFF) {
                    queue.removeAtAllUnitsDone();
                }
                SourceBattleTicker.FrameDecision decision =
                        SourceBattleTicker.FrameDecision.ADVANCE;
                if (ticker.isRunning()) {
                    decision = ticker.advanceFrame(!animation.isFinished(), false);
                    if (decision == SourceBattleTicker.FrameDecision.CLEANUP) {
                        labels.completeBattlePlayback();
                    }
                }
                animation.presentWaitingFrame();
                if (decision == SourceBattleTicker.FrameDecision.ADVANCE
                        && !labels.isCleaningUp()
                        && !labels.isWaitingForActionLabels()
                        && previousPhase != TurnPlaybackSequence.Phase.ACTION_LABELS) {
                    recordClock.tick();
                    earlyReturn = animation.tickSourceFrame();
                    animation.registerStoppedClips(queue);
                }
            }
            boolean marineRemovedThisTick = false;
            if (tick == 290) compareDouble(mismatches,
                    "RNG before first marine activation", 0.8943997782145745,
                    presentationRandom.copy().nextDouble());
            int sourceQueued = 0;
            int[] queueNativeOrder = new int[4];
            for (int sourceIndex = 0; sourceIndex < 4; sourceIndex++) {
                String[] source = rows.get(1 + tick * 4 + sourceIndex).split(",", -1);
                int nativeIndex = nativeBySourceRow[sourceIndex];
                FlashApacheMarineAnimation.Actor actual = animation.getActor(nativeIndex);
                String context = "tick=" + tick + " " + source[1];
                if (!source[5].isEmpty()) {
                    int queueIndex = Integer.parseInt(source[5]);
                    queueNativeOrder[queueIndex] = nativeIndex;
                    sourceQueued++;
                    if (nativeIndex != 0 && tick > 0) {
                        String[] previous = rows.get(1 + (tick - 1) * 4 + sourceIndex)
                                .split(",", -1);
                        marineRemovedThisTick |= previous[5].isEmpty();
                    }
                }
                // Hidden source marine clips exist before activation with undefined
                // coordinates and a passive Flash timeline. No coordinate is invented.
                if (!source[6].isEmpty()) compareFloat(mismatches, context + " x",
                        Float.parseFloat(source[6]), actual.sourceX);
                if (!source[7].isEmpty()) compareFloat(mismatches, context + " y",
                        Float.parseFloat(source[7]), actual.sourceY);
                compare(mismatches, context + " effective visibility",
                        Boolean.parseBoolean(source[9])
                                && Boolean.parseBoolean(source[10]), actual.visible);
                if (!source[6].isEmpty()) {
                    compare(mismatches, context + " linkage", source[12], actual.linkage);
                    compare(mismatches, context + " frame", Integer.parseInt(source[11]),
                            actual.sourceFrame + 1);
                }
                boolean removed = source[4].isEmpty() && !source[5].isEmpty();
                boolean stopped = "stop".equals(source[3]) || removed;
                if (nativeIndex == 0) {
                    compare(mismatches, context + " stopped", stopped,
                            animation.isApacheStopped());
                    compare(mismatches, context + " removed", removed,
                            animation.isApacheRemoved());
                } else {
                    compare(mismatches, context + " stopped", stopped,
                            animation.isMarineStopped(nativeIndex - 1));
                    compare(mismatches, context + " removed", removed,
                            animation.isMarineRemoved(nativeIndex - 1));
                    compare(mismatches, context + " shots", Integer.parseInt(source[8]),
                            shotCountField.getInt(marineTracks[nativeIndex - 1]));
                }
            }
            compare(mismatches, "tick=" + tick + " early return",
                    marineRemovedThisTick, earlyReturn);
            compare(mismatches, "tick=" + tick + " queued wrappers", sourceQueued,
                    queue.size());
            String[] visual = shadowRows.get(1 + tick).split(",", -1);
            compare(mismatches, "tick=" + tick + " shadow fixture clock", tick,
                    Integer.parseInt(visual[0]));
            FlashApacheMarineAnimation.Actor apacheBody = animation.getActor(0);
            FlashApacheMarineAnimation.Actor shadow = animation.getApacheShadow();
            compareFloat(mismatches, "tick=" + tick + " Apache body X",
                    Float.parseFloat(visual[1]), apacheBody.sourceX);
            compareFloat(mismatches, "tick=" + tick + " Apache body Y",
                    Float.parseFloat(visual[2]), apacheBody.sourceY);
            compareFloat(mismatches, "tick=" + tick + " Apache body scale",
                    Float.parseFloat(visual[3]) / 100f, apacheBody.scale);
            compare(mismatches, "tick=" + tick + " Apache body frame",
                    Integer.parseInt(visual[4]), apacheBody.sourceFrame + 1);
            compare(mismatches, "tick=" + tick + " Apache body visibility",
                    Boolean.parseBoolean(visual[5]), apacheBody.visible);
            compareFloat(mismatches, "tick=" + tick + " shadow X",
                    Float.parseFloat(visual[6]), shadow.sourceX);
            compareFloat(mismatches, "tick=" + tick + " shadow Y",
                    Float.parseFloat(visual[7]), shadow.sourceY);
            compareFloat(mismatches, "tick=" + tick + " shadow scale",
                    Float.parseFloat(visual[8]) / 100f, shadow.scale);
            compare(mismatches, "tick=" + tick + " shadow child frame",
                    Integer.parseInt(visual[9]), shadow.sourceFrame + 1);
            compare(mismatches, "tick=" + tick + " shadow visibility",
                    Boolean.parseBoolean(visual[10]), shadow.visible);
            compareFloat(mismatches, "tick=" + tick + " shadow Y offset",
                    Float.parseFloat(visual[11]), shadow.sourceY - apacheBody.sourceY);
            @SuppressWarnings("unchecked")
            List<SourceStoppedClipQueue.Clip> actualQueued =
                    (List<SourceStoppedClipQueue.Clip>) queuedClipsField.get(queue);
            for (int index = 0; index < Math.min(sourceQueued, actualQueued.size()); index++) {
                compare(mismatches, "tick=" + tick + " queue order " + index,
                        animation.getActor(queueNativeOrder[index]), actualQueued.get(index));
            }
        }
        assertTrue("Native replay differs from source on " + mismatches.size()
                + " observations: " + mismatches.subList(0, Math.min(24, mismatches.size())),
                mismatches.isEmpty());
    }

    private static void queueResources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }

    private static void compare(List<String> mismatches, String field, Object expected,
                                Object actual) {
        if (!expected.equals(actual)) mismatches.add(field + " expected=" + expected
                + " actual=" + actual);
    }

    private static void compareFloat(List<String> mismatches, String field, float expected,
                                     float actual) {
        if (!Float.isFinite(actual) || Math.abs(expected - actual) > 0.001f)
            mismatches.add(field + " expected="
                + expected + " actual=" + actual);
    }

    private static void compareDouble(List<String> mismatches, String field,
                                      double expected, double actual) {
        if (Math.abs(expected - actual) > 1e-12) mismatches.add(field + " expected="
                + expected + " actual=" + actual);
    }

    private static final class ActionBuildingFlameClock {
        private static final int TOTAL_FRAMES = 146;
        private final ReferenceRandom random;
        private int nextFirstFrameTick;

        ActionBuildingFlameClock(ReferenceRandom random) {
            this.random = random;
            advanceFirstFrame(0);
        }

        void tick(int tick) {
            if (tick == nextFirstFrameTick) advanceFirstFrame(tick);
        }

        private void advanceFirstFrame(int tick) {
            int selectedFrame = random.nextInt(109) + 2;
            nextFirstFrameTick = tick + TOTAL_FRAMES - selectedFrame + 1;
        }
    }

    @Test
    public void marineRemovalEndsSourceFrameAndClipsWaitForQueueCleanup() throws Exception {
        FlashApacheMarineAnimation animation = createAnimation(0);
        SourceStoppedClipQueue queue = new SourceStoppedClipQueue();
        int marineRemovalFrames = 0;
        int apacheStopTick = -1;
        int apacheRemoveTick = -1;
        float stoppedApacheX = 0f;
        float stoppedApacheY = 0f;
        float stoppedXStep = 0f;
        float stoppedYStep = 0f;
        Field xStep = FlashApacheMarineAnimation.class.getDeclaredField("apacheXStep");
        Field yStep = FlashApacheMarineAnimation.class.getDeclaredField("apacheYStep");
        xStep.setAccessible(true);
        yStep.setAccessible(true);
        for (int tick = 1; tick <= 1200 && !animation.isFinished(); tick++) {
            int removedBefore = 0;
            for (int marine = 0; marine < 3; marine++) {
                if (animation.isMarineRemoved(marine)) removedBefore++;
            }
            boolean endedSourceFrame = animation.tickSourceFrame();
            int removedAfter = 0;
            for (int marine = 0; marine < 3; marine++) {
                if (animation.isMarineRemoved(marine)) {
                    removedAfter++;
                    assertTrue("Marine body remains attached after wrapper removal",
                            animation.getActor(marine + 1).visible);
                }
            }
            assertEquals("Only one marine may end a View frame",
                    endedSourceFrame ? 1 : 0, removedAfter - removedBefore);
            if (endedSourceFrame) marineRemovalFrames++;
            if (apacheStopTick < 0 && animation.isApacheStopped()) {
                apacheStopTick = tick;
                stoppedApacheX = animation.getActor(0).sourceX;
                stoppedApacheY = animation.getActor(0).sourceY;
                stoppedXStep = xStep.getFloat(animation);
                stoppedYStep = yStep.getFloat(animation);
            }
            if (apacheRemoveTick < 0 && animation.isApacheRemoved()) {
                apacheRemoveTick = tick;
                assertEquals(stoppedApacheX + stoppedXStep,
                        animation.getActor(0).sourceX, 0f);
                assertEquals(stoppedApacheY + stoppedYStep,
                        animation.getActor(0).sourceY, 0f);
            }
            animation.registerStoppedClips(queue);
        }
        assertTrue("All source wrappers have been removed", animation.isFinished());
        assertEquals(3, marineRemovalFrames);
        assertEquals(apacheStopTick + 1, apacheRemoveTick);
        assertEquals(4, queue.size());
        animation.registerStoppedClips(queue);
        assertEquals("Source wrappers are enqueued once", 4, queue.size());
        for (int actor = 0; actor < 4; actor++) {
            assertTrue("Removed actor remains visible until allUnitsDone",
                    animation.getActor(actor).visible);
        }
        assertEquals(4, queue.removeAtAllUnitsDone());
        for (int actor = 0; actor < 4; actor++) {
            assertFalse(animation.getActor(actor).visible);
        }
    }

    private static Path projectSourceFixture() {
        String relative = "source/apache_marine_seed0_three_turns.csv";
        Path module = Paths.get("app", "src", "test", "resources", relative);
        return Files.isRegularFile(module) ? module : Paths.get("src", "test", "resources", relative);
    }

    private static Path projectShadowFixture() {
        String relative = "source/apache_shadow_seed0_three_turns.csv";
        Path module = Paths.get("app", "src", "test", "resources", relative);
        return Files.isRegularFile(module) ? module : Paths.get("src", "test", "resources", relative);
    }

    private static FlashApacheMarineAnimation createAnimation(int delayTicks) throws Exception {
        Path asset = projectAsset("original/runtime/battle_anchors.json");
        String json = new String(Files.readAllBytes(asset), StandardCharsets.UTF_8);
        Map<String, FlashBattleRoutes.Point> anchors = FlashBattleRoutes.parseAnchors(json);
        FlashBattleRoutes.ApacheMarineAttack route =
                FlashBattleRoutes.createSoldierTwoAttack(0, 1, 1, 1, anchors,
                        new ReferenceRandom(2026L));
        return new FlashApacheMarineAnimation(route, new ReferenceRandom(42L),
                OriginalAudio.Cue.SEAL_GUN, delayTicks);
    }

    private static Path projectAsset(String relative) {
        Path moduleAsset = Paths.get("app", "src", "main", "assets", relative);
        if (Files.isRegularFile(moduleAsset)) {
            return moduleAsset;
        }
        return Paths.get("src", "main", "assets", relative);
    }
}
