package com.example.worlddomination.game.rendering;

import java.util.Objects;

/** Physical source aircraft roles and a retained rocket_self effect in the battle root. */
public final class SourceInterceptedAircraftDisplayDepths implements SourceStoppedClipQueue.Clip {
    public final FlashStandalonePlaneAnimation animation;
    private final BattlefieldDisplayState display;
    private final SourceStoppedClipQueue stoppedClips;
    private final String unitName;
    private final SourceDisplayList.Entry<BattlefieldDisplayState.Item> body;
    private final SourceDisplayList.Entry<BattlefieldDisplayState.Item> shadow;
    private final SourceDisplayList.Entry<BattlefieldDisplayState.Item> tint;
    private final SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot;
    private SourceDisplayList.Entry<BattlefieldDisplayState.Item> explosion;
    private int explosionFrameIndex = -1;
    private double explosionX;
    private double explosionY;
    private boolean queued;

    public SourceInterceptedAircraftDisplayDepths(BattlefieldDisplayState display,
            FlashStandalonePlaneAnimation animation, int precedingUnitParameter,
            SourceStoppedClipQueue stoppedClips) {
        this.display = Objects.requireNonNull(display, "display");
        this.animation = Objects.requireNonNull(animation, "animation");
        this.stoppedClips = Objects.requireNonNull(stoppedClips, "stoppedClips");
        if (animation.getSourcePlayback() == null || !(animation.getRoute().intercepted
                || "plane002".equals(animation.getRoute().linkage)))
            throw new IllegalArgumentException("A source interception or bomber is required");
        if (precedingUnitParameter < 0 || precedingUnitParameter % 5 != 0)
            throw new IllegalArgumentException("Invalid source Unit parameter");
        unitName = "unit" + (precedingUnitParameter + 5) / 5;
        // SourceBattleUnitAllocation already reserved these four clips during
        // construction. Rebind their roles without allocating new source depths.
        body = bind(unitName, BattlefieldDisplayState.Kind.UNIT_BODY);
        shadow = bind("shadow_" + unitName, BattlefieldDisplayState.Kind.UNIT_SHADOW);
        tint = bind("tint" + unitName, BattlefieldDisplayState.Kind.UNIT_TINT);
        dot = bind("targetDot" + unitName, BattlefieldDisplayState.Kind.UNIT_DOT);
    }

    private SourceDisplayList.Entry<BattlefieldDisplayState.Item> bind(
            String name, BattlefieldDisplayState.Kind kind) {
        SourceDisplayList<BattlefieldDisplayState.Item> clips = display.clips();
        for (int index = 0; index < clips.size(); index++) {
            SourceDisplayList.Entry<BattlefieldDisplayState.Item> entry = clips.entryAt(index);
            if (name.equals(entry.getValue().name))
                return display.attach(kind, 0, name, this, entry.getDepth());
        }
        throw new IllegalStateException("Missing reserved aircraft role " + name);
    }

    /** Existing children autoplay before this frame's Unit callbacks; birth is frame 1. */
    public void advanceAttachedTimelines() {
        // explosion003's authored frame22 stops; it does not remove its parent.
        if (explosion != null && explosion.isAttached() && explosionFrameIndex < 21)
            explosionFrameIndex++;
    }

    /** Call after the Unit has removed all its smoke, before advancing other Units. */
    public void afterUnitFrame() {
        SourceAircraftPlayback core = animation.getSourcePlayback();
        if (!core.isBodyAttached() && explosion == null) {
            display.remove(body);
            display.remove(shadow);
            display.remove(tint);
            explosionX = core.getDeathEffectX();
            explosionY = core.getDeathEffectY();
            explosion = display.attach(BattlefieldDisplayState.Kind.EFFECT, -1,
                    unitName + "explosion", this, display.nextDepth());
            explosionFrameIndex = 0;
        }
        if (core.isRemoved() && !queued) {
            stoppedClips.add(this);
            queued = true;
        }
    }

    /** Presenter.allUnitsDone removes retained peers; the target dot remains. */
    @Override public void detach() {
        display.remove(body);
        display.remove(shadow);
        display.remove(tint);
        display.remove(explosion);
        animation.getActor().visible = false;
    }
    public SourceDisplayList.Entry<BattlefieldDisplayState.Item> body() { return body; }
    public SourceDisplayList.Entry<BattlefieldDisplayState.Item> shadow() { return shadow; }
    public SourceDisplayList.Entry<BattlefieldDisplayState.Item> tint() { return tint; }
    public SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot() { return dot; }
    public SourceDisplayList.Entry<BattlefieldDisplayState.Item> explosion() { return explosion; }
    public int getExplosionFrameIndex() { return explosionFrameIndex; }
    public double getExplosionX() { return explosionX; }
    public double getExplosionY() { return explosionY; }
}
