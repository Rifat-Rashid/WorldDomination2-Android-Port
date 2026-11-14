package com.example.worlddomination.game.rendering;

import java.util.Objects;
import java.util.function.Supplier;

/** Direct battle-root roles for one source submarine rocket and its smoke. */
public final class SourceRocketDisplayDepths {
    public static final int EXPLOSION_ROLE_INDEX = -1;

    public static final class Binding implements SourceStoppedClipQueue.Clip {
        public final FlashRocketAnimation animation;
        public final int actorIndex = 0;
        public final int sourceParameter;
        private final BattlefieldDisplayState state;
        private final String unitName;
        private final SourceDisplayList.Entry<BattlefieldDisplayState.Item>[] smoke;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> body;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> explosion;
        @SuppressWarnings("unchecked")
        private final SourceDisplayList.Entry<BattlefieldDisplayState.Item>[] explosions =
                new SourceDisplayList.Entry[2];
        private final int[] explosionFrameIndices = {-1, -1};
        private final double[] explosionX = new double[2];
        private final double[] explosionY = new double[2];
        private int explosionCount;

        @SuppressWarnings("unchecked")
        private Binding(BattlefieldDisplayState state, FlashRocketAnimation animation,
                        int parameter) {
            this.state = state;
            this.animation = animation;
            sourceParameter = parameter;
            unitName = "unit" + parameter / 5;
            smoke = new SourceDisplayList.Entry[animation.getSourcePlayback().getTrailSlotCount()];
        }

        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> body() { return body; }
        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot() { return dot; }
        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> smoke(int slot) {
            return smoke[slot];
        }
        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> explosion() {
            return explosion;
        }
        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> explosion(int index) {
            return explosions[index];
        }
        public int getExplosionCount() { return explosionCount; }
        public int getExplosionFrameIndex() {
            return explosionCount == 0 ? -1 : getExplosionFrameIndex(explosionCount - 1);
        }
        public int getExplosionFrameIndex(int index) { return explosionFrameIndices[index]; }
        public double getExplosionX(int index) { return explosionX[index]; }
        public double getExplosionY(int index) { return explosionY[index]; }
        public int getSmokeSlotCount() { return smoke.length; }
        public String getUnitName() { return unitName; }

        private void attachInitial() {
            body = state.attach(BattlefieldDisplayState.Kind.UNIT_BODY, actorIndex,
                    unitName, this, state.nextDepth());
            dot = state.attach(BattlefieldDisplayState.Kind.UNIT_DOT, actorIndex,
                    "targetDot" + unitName, this, state.nextDepth());
        }

        /** Presenter.allUnitsDone detaches the retained effect; source dot remains. */
        @Override public void detach() { state.remove(explosion); }
    }

    private final BattlefieldDisplayState state;
    private final Supplier<FlashAnimationTimeline> explosionTimelineSupplier;
    private FlashAnimationTimeline explosionTimeline;
    private final SourceStoppedClipQueue stoppedClips;
    private final Binding binding;
    private boolean queuedForCleanup;

    public SourceRocketDisplayDepths(BattlefieldDisplayState state,
                                     FlashRocketAnimation animation,
                                     int precedingUnitParameter,
                                     FlashAnimationTimeline explosionTimeline,
                                     SourceStoppedClipQueue stoppedClips) {
        this(state, animation, precedingUnitParameter,
                () -> explosionTimeline, stoppedClips);
    }

    /** Assets may finish loading after confirm, so resolve the effect at impact. */
    public SourceRocketDisplayDepths(BattlefieldDisplayState state,
                                     FlashRocketAnimation animation,
                                     int precedingUnitParameter,
                                     Supplier<FlashAnimationTimeline> explosionTimelineSupplier,
                                     SourceStoppedClipQueue stoppedClips) {
        this.state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(animation, "animation");
        if (animation.getSourcePlayback() == null) {
            throw new IllegalArgumentException("Source rocket animation required");
        }
        if (precedingUnitParameter < 0 || precedingUnitParameter % 5 != 0) {
            throw new IllegalArgumentException("Invalid preceding source unit parameter");
        }
        this.explosionTimelineSupplier = Objects.requireNonNull(explosionTimelineSupplier,
                "explosionTimelineSupplier");
        this.stoppedClips = Objects.requireNonNull(stoppedClips, "stoppedClips");
        binding = new Binding(state, animation, precedingUnitParameter + 5);
        binding.attachInitial();
    }

    public Binding binding() { return binding; }

    /** MovieClip autoplay runs before View visits the Unit on each source frame. */
    public void advanceAttachedTimelines() {
        for (int index = 0; index < binding.explosionCount; index++) {
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry =
                    binding.explosions[index];
            if (entry.isAttached()
                    && binding.explosionFrameIndices[index] + 1 < explosionStopFrame()) {
                binding.explosionFrameIndices[index]++;
            }
        }
    }

    private int explosionStopFrame() {
        if (explosionTimeline == null) explosionTimeline = explosionTimelineSupplier.get();
        if (explosionTimeline == null) return 1;
        return sourceExplosionStopFrame(binding.animation.getImpactLinkage(),
                explosionTimeline.getFrameCount());
    }

    static int sourceExplosionStopFrame(String linkage, int frameCount) {
        // scene.json.gz action_frames: each of these frame scripts stops the
        // nested explosion clip before its authored timeline may end.
        int stopFrame;
        switch (linkage) {
            case "explosion_bioload_1": stopFrame = 56; break;
            case "explosion_bioload_2": stopFrame = 53; break;
            case "explosion_bioload_3": stopFrame = 55; break;
            case "explosion_bioload_4": stopFrame = 87; break;
            case "explosion_warhead_1": stopFrame = 52; break;
            case "explosion_warhead_2": stopFrame = 47; break;
            case "explosion_warhead_3": stopFrame = 55; break;
            case "explosion_warhead_4": stopFrame = 91; break;
            case "explosion003": stopFrame = 22; break;
            default: stopFrame = frameCount; break;
        }
        return Math.min(stopFrame, frameCount);
    }

    /** Applies direct-root attaches/removals after one source Unit visit. */
    public void afterUnitFrame() {
        SourceRocketPlayback rocket = binding.animation.getSourcePlayback();
        for (int slot = 0; slot < binding.smoke.length; slot++) {
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = binding.smoke[slot];
            if (rocket.isTrailAttached(slot)) {
                if (entry == null) {
                    if (slot == 0) rocket.setTrailDepthBase(state.nextDepth());
                    binding.smoke[slot] = state.attach(BattlefieldDisplayState.Kind.EFFECT,
                            slot, binding.unitName + "trail" + slot,
                            binding, state.nextDepth());
                }
            } else if (entry != null && entry.isAttached()) {
                state.remove(entry);
            }
        }
        if (!rocket.isBodyAttached() && binding.body.isAttached()) state.remove(binding.body);
        int expectedEffects = rocket.isSelfImpact()
                ? rocket.getSelfExplosionCount() : rocket.isBodyAttached() ? 0 : 1;
        while (binding.explosionCount < expectedEffects) {
            int index = binding.explosionCount++;
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = state.attach(
                    BattlefieldDisplayState.Kind.EFFECT, EXPLOSION_ROLE_INDEX - index,
                    binding.unitName + "explosion", binding, state.nextDepth());
            binding.explosions[index] = entry;
            binding.explosion = entry;
            binding.explosionFrameIndices[index] = 0;
            binding.explosionX[index] = rocket.getImpactEffectX();
            binding.explosionY[index] = rocket.getImpactEffectY();
        }
        if (rocket.isRemoved() && !queuedForCleanup) {
            stoppedClips.add(binding);
            queuedForCleanup = true;
        }
    }
}
