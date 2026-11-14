package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SourceBattleActionFramesTest {
    private static final int[] IDS = {4010, 4015, 4018, 4023, 4028};
    private static final int[] LENGTHS = {146, 163, 161, 121, 130};
    private static final String[] RELIGIONS = {"mus", "jew", "com", "chr", "bhu"};

    @Test
    public void firstFrameCallbacksUseShownOrderAfterRoutesNotAllListOrder() throws Exception {
        GameState.ActionBuildingEvent first = event(0, 1, "mus", "religion", 0);
        GameState.ActionBuildingEvent second = event(3, 2, "jew", "religion", 1);
        FlashActionBuildingAnimation firstClip = new FlashActionBuildingAnimation(first);
        FlashActionBuildingAnimation secondClip = new FlashActionBuildingAnimation(second);
        ReferenceRandom random = new ReferenceRandom(42L);
        ReferenceRandom expected = random.copy();
        SourceBattleActionFrames owner = new SourceBattleActionFrames();
        owner.begin(Arrays.asList(secondClip, firstClip), Arrays.asList(first, second), random);

        assertEquals(1 + expected.nextInt(109), firstClip.getNestedFrameIndex(4010, 146));
        expected.nextInt(109); // New religion parent first enters default mus.
        assertEquals(1 + expected.nextInt(109), secondClip.getNestedFrameIndex(4015, 163));
        assertEquals(expected.nextDouble(), random.nextDouble(), 0d);
    }

    @Test
    public void simultaneousWrapCallbacksFollowPhysicalDepthOrder() throws Exception {
        GameState.ActionBuildingEvent lowDepth = event(3, 2, "mus", "religion", 0);
        GameState.ActionBuildingEvent highDepth = event(0, 1, "mus", "religion", 1);
        FlashActionBuildingAnimation lowClip = new FlashActionBuildingAnimation(lowDepth);
        FlashActionBuildingAnimation highClip = new FlashActionBuildingAnimation(highDepth);
        ReferenceRandom random = new ReferenceRandom(17L);
        SourceBattleActionFrames owner = new SourceBattleActionFrames();
        owner.begin(Arrays.asList(highClip, lowClip),
                Arrays.asList(highDepth, lowDepth), random);
        ReferenceRandom expected = random.copy();
        setFlameFrame(owner, 7, 146); // ab3_2 depth 176
        setFlameFrame(owner, 0, 146); // ab0_1 depth 200

        owner.tick();
        assertEquals(1 + expected.nextInt(109), lowClip.getNestedFrameIndex(4010, 146));
        assertEquals(1 + expected.nextInt(109), highClip.getNestedFrameIndex(4010, 146));
        assertEquals(expected.nextDouble(), random.nextDouble(), 0d);
    }

    @Test
    public void allFiveReligionTimelinesWrapAtTheirAuthoredLengths() throws Exception {
        List<GameState.ActionBuildingEvent> events = new ArrayList<>();
        List<FlashActionBuildingAnimation> clips = new ArrayList<>();
        for (int player = 0; player < 5; player++) {
            GameState.ActionBuildingEvent event = event(player, 1, RELIGIONS[player],
                    "religion", player);
            events.add(event);
            clips.add(new FlashActionBuildingAnimation(event));
        }
        ReferenceRandom random = new ReferenceRandom(72L);
        ReferenceRandom expected = random.copy();
        SourceBattleActionFrames owner = new SourceBattleActionFrames();
        owner.begin(clips, events, random);
        int[] frames = new int[5];
        for (int index = 0; index < 5; index++) {
            if (index != 0) expected.nextInt(109); // Discarded default-mus child.
            frames[index] = 2 + expected.nextInt(109);
            assertEquals(frames[index] - 1,
                    clips.get(index).getNestedFrameIndex(IDS[index], LENGTHS[index]));
        }
        // Five slot-1 depths are player2, player3, player0, player1, player4.
        int[] depthOrder = {2, 3, 0, 1, 4};
        for (int tick = 1; tick <= 325; tick++) {
            owner.tick();
            for (int index : depthOrder) {
                frames[index] = frames[index] == LENGTHS[index]
                        ? 2 + expected.nextInt(109) : frames[index] + 1;
            }
            for (int index = 0; index < 5; index++) {
                assertEquals("religion " + RELIGIONS[index] + " tick " + tick,
                        frames[index] - 1,
                        clips.get(index).getNestedFrameIndex(IDS[index], LENGTHS[index]));
            }
        }
        assertEquals(expected.nextDouble(), random.nextDouble(), 0d);
    }

    @Test
    public void sameSlotReligionCarriesPhaseAcrossTurnsAndRebindsRandom() throws Exception {
        GameState.ActionBuildingEvent firstEvent = event(2, 2, "mus", "religion", 0);
        FlashActionBuildingAnimation first = new FlashActionBuildingAnimation(firstEvent);
        ReferenceRandom firstRandom = new ReferenceRandom(3L);
        SourceBattleActionFrames owner = new SourceBattleActionFrames();
        owner.begin(Arrays.asList(first), Arrays.asList(firstEvent), firstRandom);
        for (int tick = 0; tick < 23; tick++) owner.tick();
        int carried = first.getNestedFrameIndex(4010, 146);
        GameState.ActionBuildingEvent nextEvent = event(2, 2, "mus", "religion", 1);
        FlashActionBuildingAnimation next = new FlashActionBuildingAnimation(nextEvent, first);
        ReferenceRandom nextRandom = new ReferenceRandom(8L);
        ReferenceRandom expected = nextRandom.copy();
        owner.begin(Arrays.asList(next), Arrays.asList(nextEvent), nextRandom);
        assertEquals(carried, next.getNestedFrameIndex(4010, 146));
        assertEquals(0, first.getNestedFrameIndex(4010, 146));
        assertEquals("same frame must not redraw", expected.nextDouble(),
                nextRandom.copy().nextDouble(), 0d);
        owner.tick();
        assertEquals(carried + 1, next.getNestedFrameIndex(4010, 146));

        GameState.ActionBuildingEvent replacement = event(2, 2, "mus", "tech", 2);
        FlashActionBuildingAnimation technology = new FlashActionBuildingAnimation(replacement);
        owner.begin(Arrays.asList(technology), Arrays.asList(replacement), nextRandom);
        assertEquals(0, next.getNestedFrameIndex(4010, 146));
        owner.reset();
        assertEquals(0, technology.getNestedFrameIndex(4010, 146));
    }

    @Test
    public void soundVariantConsumesOnlyEnabledReligionLabelDraw() throws Exception {
        SourceBattleActionFrames owner = new SourceBattleActionFrames();
        ReferenceRandom random = new ReferenceRandom(22L);
        ReferenceRandom expected = random.copy();
        owner.begin(new ArrayList<>(), new ArrayList<>(), random);
        assertEquals(0, owner.onActionRevealed(GameState.Action.MASS_MEDIA, false));
        assertEquals(0, owner.onActionRevealed(GameState.Action.ATTACK, true));
        assertEquals(expected.nextDouble(), random.copy().nextDouble(), 0d);
        ReferenceRandom beforeSound = random.copy();
        assertEquals(1 + beforeSound.nextInt(4),
                owner.onActionRevealed(GameState.Action.MASS_MEDIA, true));
        assertEquals(beforeSound.nextDouble(), random.nextDouble(), 0d);
        owner.reset();
        assertEquals(0, owner.onActionRevealed(GameState.Action.MASS_MEDIA, false));
    }

    @Test
    public void validThreeTurnApacheUsesSourceFlameAndSoundRandomStream() throws Exception {
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
        GameState.CombatEvent attack = null;
        for (GameState.CombatEvent event
                : game.getLastTurnCombatEventsInSourcePresentationOrder()) {
            if (event.attackerPlayerId == 0 && "soldier2".equals(event.weaponName)) attack = event;
        }
        assertTrue(attack != null);
        ReferenceRandom random = game.createBattlePresentationRandomCopy();
        String anchorsJson = new String(Files.readAllBytes(projectAsset(
                "original/runtime/battle_anchors.json")), StandardCharsets.UTF_8);
        Map<String, FlashBattleRoutes.Point> anchors = FlashBattleRoutes.parseAnchors(anchorsJson);
        FlashBattleRoutes.createSoldierTwoAttack(attack.attackerPlayerId,
                attack.defenderPlayerId, attack.cityIndex, attack.actionBuildingSlot,
                anchors, random);
        assertEquals(0.9086575543967805, random.copy().nextDouble(), 0d);
        List<GameState.ActionBuildingEvent> shown = game.getLastTurnActionBuildings();
        List<FlashActionBuildingAnimation> all = new ArrayList<>();
        for (GameState.ActionBuildingEvent event : shown) {
            for (int index = all.size() - 1; index >= 0; index--) {
                FlashActionBuildingAnimation prior = all.get(index);
                if (prior.playerId == event.playerId && prior.slot == event.slot) all.remove(index);
            }
            all.add(new FlashActionBuildingAnimation(event));
        }
        SourceBattleActionFrames owner = new SourceBattleActionFrames();
        owner.begin(all, shown, random);
        assertEquals("source host construction", 0.4740046511372964,
                random.copy().nextDouble(), 0d);
        SourceBattleInterfaceFrames center = new SourceBattleInterfaceFrames();
        center.begin();
        TurnPlaybackSequence labels = new TurnPlaybackSequence();
        List<GameState.Action> actions = new ArrayList<>();
        for (GameState.ActionBuildingEvent event : shown) actions.add(event.action);
        labels.begin(actions);
        for (int tick = 1; tick <= 290; tick++) {
            owner.tick();
            center.tick(random);
            owner.onActionRevealed(labels.advance(1f / 24f), true);
        }
        assertEquals("source before first marine activation", 0.8943997782145745,
                random.copy().nextDouble(), 0d);
        assertEquals("source unit3 x at tick 291", 178, 160 + random.nextInt(21));
    }

    @Test
    public void newNonDefaultReligionRunsBothSourceFrameOneCallbacks() throws Exception {
        GameState.ActionBuildingEvent event = event(4, 1, "bhu", "religion", 0);
        FlashActionBuildingAnimation clip = new FlashActionBuildingAnimation(event);
        ReferenceRandom random = new ReferenceRandom(0L);
        ReferenceRandom expected = random.copy();
        expected.nextInt(109); // Symbol4085 frame1 -> flame4010.
        int expectedFrame = 1 + expected.nextInt(109); // frame5 -> flame4028.
        SourceBattleActionFrames owner = new SourceBattleActionFrames();
        owner.begin(Arrays.asList(clip), Arrays.asList(event), random);
        assertEquals(expectedFrame, clip.getNestedFrameIndex(4028, 130));
        assertEquals(expected.nextDouble(), random.copy().nextDouble(), 0d);
        ReferenceRandom retained = random.copy();
        owner.begin(Arrays.asList(clip), Arrays.asList(event), random);
        assertEquals("same physical child does not reenter frame1", retained.nextDouble(),
                random.copy().nextDouble(), 0d);
        owner.reset();
        expected = random.copy();
        expected.nextInt(109);
        expectedFrame = 1 + expected.nextInt(109);
        owner.begin(Arrays.asList(clip), Arrays.asList(event), random);
        assertEquals("fresh parent recreates both children", expectedFrame,
                clip.getNestedFrameIndex(4028, 130));
        assertEquals(expected.nextDouble(), random.copy().nextDouble(), 0d);
    }

    @Test
    public void occupiedDepthSwapsChangeSimultaneousFlameCallbackOrder() throws Exception {
        GameState.ActionBuildingEvent first = event(0, 1, "mus", "religion", 0);
        GameState.ActionBuildingEvent second = event(3, 2, "mus", "religion", 1);
        FlashActionBuildingAnimation firstClip = new FlashActionBuildingAnimation(first);
        FlashActionBuildingAnimation secondClip = new FlashActionBuildingAnimation(second);
        BattlefieldDisplayState display = new BattlefieldDisplayState();
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> high = display.attach(
                BattlefieldDisplayState.Kind.ACTION_BUILDING, 0, "ab0_1", firstClip, 200);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> low = display.attach(
                BattlefieldDisplayState.Kind.ACTION_BUILDING, 7, "ab3_2", secondClip, 176);
        ReferenceRandom random = new ReferenceRandom(17L);
        SourceBattleActionFrames owner = new SourceBattleActionFrames();
        owner.begin(Arrays.asList(firstClip, secondClip), Arrays.asList(first, second), random);
        setFlameFrame(owner, 0, 146);
        setFlameFrame(owner, 7, 146);
        display.clips().swapDepths(high, low);
        ReferenceRandom expected = random.copy();
        owner.tick(display);
        assertEquals(1 + expected.nextInt(109), firstClip.getNestedFrameIndex(4010, 146));
        assertEquals(1 + expected.nextInt(109), secondClip.getNestedFrameIndex(4010, 146));
        assertEquals(expected.nextDouble(), random.copy().nextDouble(), 0d);
        display.remove(low);
        setFlameFrame(owner, 0, 146);
        setFlameFrame(owner, 7, 146);
        int detachedFrame = secondClip.getNestedFrameIndex(4010, 146);
        expected = random.copy();
        owner.tick(display);
        assertEquals(1 + expected.nextInt(109), firstClip.getNestedFrameIndex(4010, 146));
        assertEquals("detached parent never advances", detachedFrame,
                secondClip.getNestedFrameIndex(4010, 146));
        assertEquals(expected.nextDouble(), random.copy().nextDouble(), 0d);
    }

    @Test
    public void frameStartCaptureSurvivesUnitSwapAndSkipsReplacedEntry() throws Exception {
        GameState.ActionBuildingEvent highEvent = event(0, 1, "mus", "religion", 0);
        GameState.ActionBuildingEvent lowEvent = event(3, 2, "mus", "religion", 1);
        FlashActionBuildingAnimation highClip = new FlashActionBuildingAnimation(highEvent);
        FlashActionBuildingAnimation lowClip = new FlashActionBuildingAnimation(lowEvent);
        BattlefieldDisplayState display = new BattlefieldDisplayState();
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> high = display.attach(
                BattlefieldDisplayState.Kind.ACTION_BUILDING, 0, "ab0_1", highClip, 200);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> low = display.attach(
                BattlefieldDisplayState.Kind.ACTION_BUILDING, 7, "ab3_2", lowClip, 176);
        ReferenceRandom random = new ReferenceRandom(31L);
        SourceBattleActionFrames owner = new SourceBattleActionFrames();
        owner.begin(Arrays.asList(highClip, lowClip),
                Arrays.asList(highEvent, lowEvent), random);
        setFlameFrame(owner, 0, 146);
        setFlameFrame(owner, 7, 146);

        // MovieClipHost takes a physical walk snapshot before Unit callbacks.
        owner.captureFrameOrder(display); // low, high
        display.clips().swapDepths(high, low); // Unit moves high ahead of low.
        ReferenceRandom expected = random.copy();
        owner.tickCapturedFrame();
        assertEquals("old low entry consumes the first wrap draw",
                1 + expected.nextInt(109), lowClip.getNestedFrameIndex(4010, 146));
        assertEquals("old high entry consumes the second wrap draw",
                1 + expected.nextInt(109), highClip.getNestedFrameIndex(4010, 146));
        assertEquals(expected.nextDouble(), random.copy().nextDouble(), 0d);

        setFlameFrame(owner, 0, 146);
        setFlameFrame(owner, 7, 146);
        owner.captureFrameOrder(display); // now high, low
        int unchangedHighFrame = highClip.getNestedFrameIndex(4010, 146);
        // A Unit replaces the captured clip; a newly attached peer cannot
        // inherit this frame's already captured callback.
        display.attach(BattlefieldDisplayState.Kind.EFFECT, 99,
                "unitReplacement", new Object(), high.getDepth());
        assertFalse(high.isAttached());
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> newHigh = display.attach(
                BattlefieldDisplayState.Kind.ACTION_BUILDING, 0, "ab0_1", highClip, 150);
        expected = random.copy();
        owner.tickCapturedFrame();
        assertEquals("replacement skips the detached old callback",
                unchangedHighFrame, highClip.getNestedFrameIndex(4010, 146));
        assertEquals("still attached low clip advances once",
                1 + expected.nextInt(109), lowClip.getNestedFrameIndex(4010, 146));
        assertEquals(expected.nextDouble(), random.copy().nextDouble(), 0d);

        setFlameFrame(owner, 0, 146);
        setFlameFrame(owner, 7, 146);
        owner.captureFrameOrder(display); // new high at 150, low at 200
        assertTrue(newHigh.isAttached());
        expected = random.copy();
        owner.tickCapturedFrame();
        assertEquals("new physical frame starts with newly attached low-depth peer",
                1 + expected.nextInt(109), highClip.getNestedFrameIndex(4010, 146));
        assertEquals("second peer follows new physical order",
                1 + expected.nextInt(109), lowClip.getNestedFrameIndex(4010, 146));
        assertEquals(expected.nextDouble(), random.copy().nextDouble(), 0d);
    }

    private static void queueResources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }

    private static void setFlameFrame(SourceBattleActionFrames owner, int index,
                                      int oneBasedFrame) throws Exception {
        Field flamesField = SourceBattleActionFrames.class.getDeclaredField("flames");
        flamesField.setAccessible(true);
        Object flame = ((Object[]) flamesField.get(owner))[index];
        Field frame = flame.getClass().getDeclaredField("frameOneBased");
        frame.setAccessible(true);
        frame.setInt(flame, oneBasedFrame);
    }

    private static GameState.ActionBuildingEvent event(int playerId, int slot,
                                                       String religion, String frame,
                                                       int sequenceIndex) throws Exception {
        Constructor<GameState.ActionBuildingEvent> ctor =
                GameState.ActionBuildingEvent.class.getDeclaredConstructor(int.class,
                        GameState.Action.class, int.class, String.class, String.class, int.class);
        ctor.setAccessible(true);
        return ctor.newInstance(playerId, GameState.Action.MASS_MEDIA, slot,
                frame, religion, sequenceIndex);
    }

    private static Path projectAsset(String relative) {
        Path moduleAsset = Paths.get("app", "src", "main", "assets", relative);
        return Files.isRegularFile(moduleAsset)
                ? moduleAsset : Paths.get("src", "main", "assets", relative);
    }
}
