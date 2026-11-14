package com.example.worlddomination.game.rendering;

import java.util.Objects;

/** Direct battle-root handles for one source suicide_bomber Unit. */
public final class SourceSuicideDisplayDepths
        implements FlashSuicideBomberAnimation.FrameListener {
    public static final class Binding implements SourceStoppedClipQueue.Clip {
        public final FlashSuicideBomberAnimation animation;
        public final int actorIndex = 0;
        public final int sourceParameter;
        private final BattlefieldDisplayState state;
        private final String unitName;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> body;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> tint;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> explosion;

        private Binding(BattlefieldDisplayState state,
                        FlashSuicideBomberAnimation animation, int parameter) {
            this.state = state;
            this.animation = animation;
            this.sourceParameter = parameter;
            unitName = "unit" + parameter / 5;
        }

        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> body() { return body; }
        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> tint() { return tint; }
        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot() { return dot; }
        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> explosion() {
            return explosion;
        }

        private void attachInitial() {
            body = state.attach(BattlefieldDisplayState.Kind.UNIT_BODY, 0,
                    unitName, this, state.nextDepth());
            // Unit.as swaps once immediately after its body attach.
            state.clips().swapDepths(body, state.nextDepth());
            tint = state.attach(BattlefieldDisplayState.Kind.UNIT_TINT, 1,
                    "tint" + unitName, this, state.nextDepth());
            dot = state.attach(BattlefieldDisplayState.Kind.UNIT_DOT, 0,
                    "targetDot" + unitName, this, state.nextDepth());
            // Unit.as:695–698 performs its second swap after tint and dot.
            state.clips().swapDepths(body, state.nextDepth());
        }

        /** Presenter.allUnitsDone detaches the retained explosion, not its dot. */
        @Override public void detach() {
            state.remove(explosion);
        }
    }

    private final BattlefieldDisplayState state;
    private final Binding binding;

    public SourceSuicideDisplayDepths(BattlefieldDisplayState state,
                                      FlashSuicideBomberAnimation animation,
                                      int precedingUnitParameter) {
        this.state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(animation, "animation");
        if (precedingUnitParameter < 0 || precedingUnitParameter % 5 != 0) {
            throw new IllegalArgumentException("Invalid preceding source unit parameter");
        }
        binding = new Binding(state, animation, precedingUnitParameter + 5);
        binding.attachInitial();
        animation.setFrameListener(this);
        animation.bindStoppedClip(binding);
    }

    public Binding binding() { return binding; }

    @Override public void onWaitingFrame(double sourceY) {
        // View.waitForHeliStart raises suicide MC once before the regular
        // source floor-depth swap on this same frame.
        state.clips().swapDepths(binding.body, state.nextDepth());
        onMovingFrame(sourceY);
    }

    @Override public void onMovingFrame(double sourceY) {
        if (binding.body == null || !binding.body.isAttached()) return;
        int bodyDepth = (int) Math.floor(sourceY / 10d) * 500 + 120
                + binding.sourceParameter;
        state.clips().swapDepths(binding.body, bodyDepth);
        // View swaps tint every visited frame, even when body depth did not move.
        state.clips().swapDepths(binding.tint, binding.body.getDepth() + 1);
    }

    @Override public void onExplosionAttached() {
        state.remove(binding.body);
        state.remove(binding.tint);
        binding.explosion = state.attach(BattlefieldDisplayState.Kind.UNIT_BODY, 2,
                binding.unitName + "explosion", binding, state.nextDepth());
    }
}
