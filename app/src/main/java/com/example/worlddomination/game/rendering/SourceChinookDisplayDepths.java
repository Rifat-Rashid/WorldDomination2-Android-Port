package com.example.worlddomination.game.rendering;

import java.util.Objects;

/** Source battle-parent depth handles for Chinook, tank base, and turret. */
public final class SourceChinookDisplayDepths
        implements FlashChinookTankAnimation.ActorFrameListener {
    /** Stable owner across the tank_noShade to tankBase body replacement. */
    public static final class Binding implements SourceStoppedClipQueue.Clip {
        public final FlashChinookTankAnimation animation;
        public final int actorIndex;
        public final int sourceParameter;
        private final BattlefieldDisplayState state;
        private final String unitName;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> body;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> shadow;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot;

        private Binding(BattlefieldDisplayState state,
                        FlashChinookTankAnimation animation,
                        int actorIndex, int sourceParameter) {
            this.state = state;
            this.animation = animation;
            this.actorIndex = actorIndex;
            this.sourceParameter = sourceParameter;
            unitName = "unit" + sourceParameter / 5;
        }

        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> body() { return body; }
        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> shadow() { return shadow; }
        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot() { return dot; }

        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> attach(
                BattlefieldDisplayState.Kind kind, String name, int depth) {
            return state.attach(kind, actorIndex, name, this, depth);
        }

        private void attachInitial() {
            // Presenter constructs top, base, carrier; each Unit attaches its
            // body before optional shadow and permanent targetDot.
            int bodyDepth = actorIndex == 2 ? sourceParameter : state.nextDepth();
            body = attach(BattlefieldDisplayState.Kind.UNIT_BODY, unitName, bodyDepth);
            if (actorIndex == 0) {
                shadow = attach(BattlefieldDisplayState.Kind.UNIT_SHADOW,
                        "shadow_" + unitName, state.nextDepth());
            }
            dot = attach(BattlefieldDisplayState.Kind.UNIT_DOT,
                    "targetDot" + unitName, state.nextDepth());
        }

        private void replaceTankBase() {
            state.remove(body);
            // Unit.as:1200–1203 attaches tankBase at depth_, replacing the
            // authored occupant (land01 in the legal seed-0 alias capture).
            body = attach(BattlefieldDisplayState.Kind.UNIT_BODY,
                    unitName, sourceParameter);
        }

        /** Presenter.allUnitsDone detaches body/shadow; dots leave with parent. */
        @Override public void detach() {
            state.remove(body);
            state.remove(shadow);
        }
    }

    private final BattlefieldDisplayState state;
    private final Binding[] bindings = new Binding[3];

    public SourceChinookDisplayDepths(BattlefieldDisplayState state,
                                      FlashChinookTankAnimation animation,
                                      int precedingUnitParameter) {
        this.state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(animation, "animation");
        if (precedingUnitParameter < 0 || precedingUnitParameter % 5 != 0) {
            throw new IllegalArgumentException("Invalid preceding source unit parameter");
        }
        // Source construction order: top native2, base native1, carrier native0.
        for (int sourceRow = 0; sourceRow < bindings.length; sourceRow++) {
            int actorIndex = 2 - sourceRow;
            int parameter = precedingUnitParameter + (sourceRow + 1) * 5;
            Binding binding = new Binding(state, animation, actorIndex, parameter);
            binding.attachInitial();
            bindings[actorIndex] = binding;
            animation.getActor(actorIndex).bindClip(binding);
        }
        animation.setActorFrameListener(this);
    }

    public Binding binding(int nativeActorIndex) { return bindings[nativeActorIndex]; }

    @Override public void onActorFrame(int nativeActorIndex, double sourceY,
                                       boolean bodyChanged) {
        if (nativeActorIndex < 0 || nativeActorIndex >= bindings.length) {
            throw new IndexOutOfBoundsException("Invalid Chinook actor index");
        }
        // Chinook has no depthSwap. Its separate shadow and all dots retain
        // construction depths, even when a peer swaps into a dot's slot.
        if (nativeActorIndex == 0) return;
        Binding binding = bindings[nativeActorIndex];
        if (nativeActorIndex == 1) {
            if (bodyChanged) binding.replaceTankBase();
            int desired = "tankBase".equals(binding.animation.getActor(1).linkage)
                    ? (int) Math.floor(sourceY / 10d) * 500 + 120
                            + binding.sourceParameter
                    : bindings[0].body.getDepth() - 2;
            state.clips().swapDepths(binding.body, desired);
        } else {
            state.clips().swapDepths(binding.body, bindings[1].body.getDepth() + 1);
        }
    }
}
