package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import java.util.List;
import java.util.Objects;

/** Shared source RNG clock for action-building religion flames and their label sound. */
public final class SourceBattleActionFrames {
    private static final int SLOT_COUNT = 10;
    // BattleMC (5766) placement depths 176,180,...,212 in scene.json.gz.
    // Slot index is playerId * 2 + slot - 1.
    private static final int[] DEPTH_ORDER = {7, 1, 4, 3, 9, 6, 0, 5, 2, 8};
    private static final int[] FLAME_IDS = {4010, 4015, 4018, 4023, 4028};
    private static final int[] FLAME_FRAMES = {146, 163, 161, 121, 130};
    private static final String[] RELIGIONS = {"mus", "jew", "com", "chr", "bhu"};
    private final Flame[] flames = new Flame[SLOT_COUNT];
    private final Flame[] capturedFlames = new Flame[SLOT_COUNT];
    @SuppressWarnings("unchecked")
    private final SourceDisplayList.Entry<BattlefieldDisplayState.Item>[] capturedEntries =
            new SourceDisplayList.Entry[SLOT_COUNT];
    private final int[] capturedSlots = new int[SLOT_COUNT];
    private int capturedCount;
    private ReferenceRandom random;

    /** Rebinds retained physical slots; new frame-1 callbacks run in Presenter order. */
    public void begin(List<FlashActionBuildingAnimation> all,
                      List<GameState.ActionBuildingEvent> shown,
                      ReferenceRandom presentationRandom) {
        Objects.requireNonNull(all, "all");
        Objects.requireNonNull(shown, "shown");
        random = Objects.requireNonNull(presentationRandom, "presentationRandom");
        FlashActionBuildingAnimation[] current = new FlashActionBuildingAnimation[SLOT_COUNT];
        GameState.ActionBuildingEvent[] lastShown =
                new GameState.ActionBuildingEvent[SLOT_COUNT];
        for (FlashActionBuildingAnimation animation : all) {
            Objects.requireNonNull(animation, "animation");
            int index = slotIndex(animation.playerId, animation.slot);
            if (current[index] != null) throw new IllegalArgumentException("Duplicate action-building slot");
            current[index] = animation;
        }
        for (GameState.ActionBuildingEvent event : shown) {
            Objects.requireNonNull(event, "event");
            int index = slotIndex(event.playerId, event.slot);
            lastShown[index] = event;
            int characterId = "religion".equals(event.frameName)
                    ? flameId(event.religionCode) : 0;
            if (characterId == 0) {
                if (flames[index] != null) {
                    if (flames[index].animation != null)
                        flames[index].animation.clearFlameFrame();
                    flames[index] = null;
                }
            } else if (flames[index] == null
                    || flames[index].characterId != characterId) {
                // View.showActionBuildingFromObj first enters the religion
                // parent at its default `mus` frame, then seeks the selected
                // religion. Both newly placed children run their frame-1 script.
                // A retained religion parent instead keeps its current child.
                if (flames[index] == null && characterId != FLAME_IDS[0]) {
                    random.nextInt(109);
                }
                if (flames[index] != null && flames[index].animation != null)
                    flames[index].animation.clearFlameFrame();
                Flame flame = new Flame(null, characterId,
                        flameFrameCount(characterId));
                flames[index] = flame;
                // DefineSprite frame 1 calls gotoAndPlay(randRange(2,110)).
                flame.frameOneBased = 2 + random.nextInt(109);
            }
        }
        for (int index = 0; index < SLOT_COUNT; index++) {
            FlashActionBuildingAnimation animation = current[index];
            GameState.ActionBuildingEvent event = lastShown[index];
            if (event != null && (animation == null
                    || animation.sequenceIndex != event.sequenceIndex
                    || !animation.frameName.equals(event.frameName)
                    || !animation.religionCode.equals(event.religionCode))) {
                throw new IllegalArgumentException("Final shown action building is absent from all");
            }
            Flame flame = flames[index];
            if (flame == null) continue;
            if (animation == null || !"religion".equals(animation.frameName)
                    || flame.characterId != flameId(animation.religionCode)) {
                if (flame.animation != null) flame.animation.clearFlameFrame();
                flames[index] = null;
            } else {
                if (flame.animation != null && flame.animation != animation)
                    flame.animation.clearFlameFrame();
                flame.animation = animation;
                animation.setFlameFrame(flame.characterId, flame.frameOneBased);
            }
        }
    }

    /** Advances attached child timelines, including retained slots during idle. */
    public void tick() {
        for (int index : DEPTH_ORDER) {
            advance(flames[index]);
        }
    }

    /** Battle depth swaps change the host's callback order as well as its drawing. */
    public void tick(BattlefieldDisplayState display) {
        captureFrameOrder(display);
        tickCapturedFrame();
    }

    /** MovieClipHost fixes traversal order before any Unit can swap peer depths. */
    public void captureFrameOrder(BattlefieldDisplayState display) {
        clearCapturedFrame();
        SourceDisplayList<BattlefieldDisplayState.Item> clips =
                Objects.requireNonNull(display, "display").clips();
        for (int index = 0; index < clips.size(); index++) {
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = clips.entryAt(index);
            BattlefieldDisplayState.Item item = entry.getValue();
            if (item.kind != BattlefieldDisplayState.Kind.ACTION_BUILDING) continue;
            Flame flame = flames[item.index];
            if (flame != null && flame.animation == item.owner) {
                capturedEntries[capturedCount] = entry;
                capturedFlames[capturedCount] = flame;
                capturedSlots[capturedCount++] = item.index;
            }
        }
    }

    /** Frame actions follow the complete onEnterFrame pass, including cloud callbacks. */
    public void tickCapturedFrame() {
        for (int index = 0; index < capturedCount; index++) {
            Flame flame = capturedFlames[index];
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = capturedEntries[index];
            if (entry.isAttached() && entry.getValue().owner == flame.animation
                    && flames[capturedSlots[index]] == flame) advance(flame);
        }
        clearCapturedFrame();
    }

    private void clearCapturedFrame() {
        for (int index = 0; index < capturedCount; index++) {
            capturedEntries[index] = null;
            capturedFlames[index] = null;
        }
        capturedCount = 0;
    }

    private void advance(Flame flame) {
        if (flame == null) return;
        if (flame.frameOneBased == flame.frameCount) {
            flame.frameOneBased = 2 + random.nextInt(109);
        } else {
            flame.frameOneBased++;
        }
        flame.animation.setFlameFrame(flame.characterId, flame.frameOneBased);
    }

    /** Source religion label chooses Massmedia 1..4 only while sound is enabled. */
    public int onActionRevealed(GameState.Action action, boolean soundEnabled) {
        return action == GameState.Action.MASS_MEDIA && soundEnabled
                ? 1 + random.nextInt(4) : 0;
    }

    /** A new parent drops old physical clip phases and random-stream ownership. */
    public void reset() {
        clearCapturedFrame();
        for (int index = 0; index < SLOT_COUNT; index++) {
            if (flames[index] != null && flames[index].animation != null)
                flames[index].animation.clearFlameFrame();
            flames[index] = null;
        }
        random = null;
    }

    private static int slotIndex(int playerId, int slot) {
        if (playerId < 0 || playerId > 4 || slot < 1 || slot > 2)
            throw new IllegalArgumentException("Invalid source action-building slot");
        return playerId * 2 + slot - 1;
    }

    private static int flameId(String religionCode) {
        for (int index = 0; index < RELIGIONS.length; index++) {
            if (RELIGIONS[index].equals(religionCode)) return FLAME_IDS[index];
        }
        return 0;
    }

    private static int flameFrameCount(int characterId) {
        for (int index = 0; index < FLAME_IDS.length; index++) {
            if (FLAME_IDS[index] == characterId) return FLAME_FRAMES[index];
        }
        throw new IllegalArgumentException("Unknown source flame");
    }

    private static final class Flame {
        private FlashActionBuildingAnimation animation;
        private final int characterId;
        private final int frameCount;
        private int frameOneBased;

        Flame(FlashActionBuildingAnimation animation, int characterId, int frameCount) {
            this.animation = animation;
            this.characterId = characterId;
            this.frameCount = frameCount;
        }
    }
}
