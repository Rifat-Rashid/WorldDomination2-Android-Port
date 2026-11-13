package com.example.worlddomination.game.rendering;

import java.util.Objects;

/** Physical battleMC clips for one Apache and its three marine wrappers. */
public final class SourceApacheDisplayDepths implements FlashApacheMarineAnimation.MarineFrameListener {
    /** Stable render owner; its clip handles change when shooting reattaches a body. */
    public static final class Binding implements SourceStoppedClipQueue.Clip {
        public final FlashApacheMarineAnimation animation;
        public final int actorIndex;
        public final int sourceParameter;
        private final BattlefieldDisplayState state;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> body;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> shadow;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> tint;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot;
        private int lastBodyDepth;

        private Binding(BattlefieldDisplayState state, FlashApacheMarineAnimation animation,
                        int actorIndex, int sourceParameter) {
            this.state = state;
            this.animation = animation;
            this.actorIndex = actorIndex;
            this.sourceParameter = sourceParameter;
        }

        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> body() { return body; }
        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> shadow() { return shadow; }
        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> tint() { return tint; }
        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot() { return dot; }

        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> attach(
                BattlefieldDisplayState.Kind kind, String role) {
            return state.attach(kind, actorIndex, "unit" + (sourceParameter / 5)
                    + "." + role, this, state.nextDepth());
        }

        private void attachInitial(boolean apache) {
            body = attach(BattlefieldDisplayState.Kind.UNIT_BODY, "body");
            lastBodyDepth = body.getDepth();
            if (apache) shadow = attach(BattlefieldDisplayState.Kind.UNIT_SHADOW, "shadow");
            tint = attach(BattlefieldDisplayState.Kind.UNIT_TINT, "tint");
            dot = attach(BattlefieldDisplayState.Kind.UNIT_DOT, "dot");
        }

        /** Stopped wrapper clips leave the parent; target dot survives this removal. */
        @Override public void detach() {
            state.remove(body);
            state.remove(tint);
            state.remove(shadow);
        }

        private void replaceForShooting() {
            state.remove(body);
            state.remove(tint);
            body = attach(BattlefieldDisplayState.Kind.UNIT_BODY, "body");
            tint = attach(BattlefieldDisplayState.Kind.UNIT_TINT, "tint");
            // Unit.shoot reattaches at nextHighestDepth; View.f_37 then applies
            // the previously cached appMC_depth on this same source frame.
            state.clips().swapDepths(body, lastBodyDepth);
            state.clips().swapDepths(tint, body.getDepth() + 1);
        }
    }

    private static final int MARINE_COUNT = 3;
    private final BattlefieldDisplayState state;
    private final Binding[] bindings = new Binding[1 + MARINE_COUNT];

    public SourceApacheDisplayDepths(BattlefieldDisplayState state,
                                     FlashApacheMarineAnimation animation,
                                     int precedingUnitParameter) {
        this.state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(animation, "animation");
        if (precedingUnitParameter < 0 || precedingUnitParameter % 5 != 0)
            throw new IllegalArgumentException("Invalid preceding source unit parameter");
        // Source Presenter stores unit1..4 in reverse native actor order.
        // Each wrapper consumes five from the shared unit parameter clock.
        for (int sourceRow = 0; sourceRow < MARINE_COUNT + 1; sourceRow++) {
            int nativeIndex = MARINE_COUNT - sourceRow;
            int parameter = precedingUnitParameter + (sourceRow + 1) * 5;
            Binding binding = new Binding(state, animation, nativeIndex, parameter);
            binding.attachInitial(nativeIndex == 0);
            bindings[nativeIndex] = binding;
            animation.getActor(nativeIndex).bindClip(binding);
        }
        animation.setMarineFrameListener(this);
    }

    public Binding binding(int nativeActorIndex) { return bindings[nativeActorIndex]; }

    @Override public void onMarineFrame(int nativeActorIndex, double exactY,
                                        boolean walking, boolean firstShoot) {
        if (nativeActorIndex < 1 || nativeActorIndex > MARINE_COUNT)
            throw new IndexOutOfBoundsException("Invalid marine actor index");
        Binding binding = bindings[nativeActorIndex];
        if (firstShoot) {
            binding.replaceForShooting();
        } else if (binding.body.isAttached()) {
            if (walking) {
                binding.lastBodyDepth = (int) Math.floor(exactY / 10d) * 500 + 120
                        + binding.sourceParameter;
            }
            // View also restores a shooting peer displaced by a later unit's swap.
            state.clips().swapDepths(binding.body, binding.lastBodyDepth);
            state.clips().swapDepths(binding.tint, binding.body.getDepth() + 1);
        }
    }
}
