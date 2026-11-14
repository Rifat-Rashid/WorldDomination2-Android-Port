package com.example.worlddomination.game.rendering;

import java.util.Objects;

/** Direct battle-parent clips for the soldier1 boat and its three tail soldiers. */
public final class SourceInfantryDisplayDepths
        implements FlashSoldierOneAnimation.ActorFrameListener {
    /** Stable render owner even when Unit replaces its body and tint MovieClips. */
    public static final class Binding implements SourceStoppedClipQueue.Clip {
        public final FlashSoldierOneAnimation animation;
        public final int actorIndex;
        public final int sourceParameter;
        private final BattlefieldDisplayState state;
        private final String unitName;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> body;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> tint;
        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot;
        private int lastBodyDepth;

        private Binding(BattlefieldDisplayState state, FlashSoldierOneAnimation animation,
                        int actorIndex, int sourceParameter) {
            this.state = state;
            this.animation = animation;
            this.actorIndex = actorIndex;
            this.sourceParameter = sourceParameter;
            unitName = "unit" + sourceParameter / 5;
        }

        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> body() { return body; }
        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> tint() { return tint; }
        public SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot() { return dot; }

        private SourceDisplayList.Entry<BattlefieldDisplayState.Item> attach(
                BattlefieldDisplayState.Kind kind, String name, int depth) {
            return state.attach(kind, actorIndex, name, this, depth);
        }

        private void attachInitial() {
            body = attach(BattlefieldDisplayState.Kind.UNIT_BODY, unitName, state.nextDepth());
            lastBodyDepth = body.getDepth();
            tint = attach(BattlefieldDisplayState.Kind.UNIT_TINT,
                    "tint" + unitName, state.nextDepth());
            dot = attach(BattlefieldDisplayState.Kind.UNIT_DOT,
                    "targetDot" + unitName, state.nextDepth());
        }

        /** Presenter.allUnitsDone removes body/tint, but leaves targetDot. */
        @Override public void detach() {
            state.remove(body);
            state.remove(tint);
        }

        private void replaceBoatForUnload() {
            state.remove(body);
            state.remove(tint);
            // Unit.moveUnit("unload") attaches still clips at depth_ and +1,
            // before View.f_37 applies the ordinary appMC_depth swap.
            body = attach(BattlefieldDisplayState.Kind.UNIT_BODY,
                    unitName, sourceParameter);
            tint = attach(BattlefieldDisplayState.Kind.UNIT_TINT,
                    "tint" + unitName, sourceParameter + 1);
        }

        private void replaceSoldierForShoot() {
            state.remove(body);
            state.remove(tint);
            // Unit.shoot attaches at current max+1, then View restores its
            // cached walking depth during this same source wrapper visit.
            body = attach(BattlefieldDisplayState.Kind.UNIT_BODY,
                    unitName, state.nextDepth());
            tint = attach(BattlefieldDisplayState.Kind.UNIT_TINT,
                    "tint" + unitName, state.nextDepth());
        }
    }

    private static final int SOLDIER_COUNT = 3;
    private final BattlefieldDisplayState state;
    private final Binding[] bindings = new Binding[1 + SOLDIER_COUNT];

    public SourceInfantryDisplayDepths(BattlefieldDisplayState state,
                                       FlashSoldierOneAnimation animation,
                                       int precedingUnitParameter) {
        this.state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(animation, "animation");
        if (precedingUnitParameter < 0 || precedingUnitParameter % 5 != 0) {
            throw new IllegalArgumentException("Invalid preceding source unit parameter");
        }
        // Presenter constructs source unit1..4; their stable native actor
        // indexes are soldier3, soldier2, soldier1, then boat0.
        for (int sourceRow = 0; sourceRow < bindings.length; sourceRow++) {
            int actorIndex = SOLDIER_COUNT - sourceRow;
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
            throw new IndexOutOfBoundsException("Invalid infantry actor index");
        }
        Binding binding = bindings[nativeActorIndex];
        if (nativeActorIndex == 0) {
            if (bodyChanged) binding.replaceBoatForUnload();
            binding.lastBodyDepth = sourceDepth(sourceY, binding.sourceParameter);
        } else if (bodyChanged) {
            binding.replaceSoldierForShoot();
        } else if ("soldier001".equals(animationLinkage(binding))) {
            // Walking and first activation use post-step curY. During shooting,
            // View keeps the cached depth even if another swap displaced it.
            binding.lastBodyDepth = sourceDepth(sourceY, binding.sourceParameter);
        }
        state.clips().swapDepths(binding.body, binding.lastBodyDepth);
        state.clips().swapDepths(binding.tint, binding.body.getDepth() + 1);
    }

    private static String animationLinkage(Binding binding) {
        return binding.animation.getActor(binding.actorIndex).linkage;
    }

    private static int sourceDepth(double y, int parameter) {
        if (!Double.isFinite(y)) throw new IllegalArgumentException("Undefined source Y");
        return (int) Math.floor(y / 10d) * 500 + 120 + parameter;
    }
}
