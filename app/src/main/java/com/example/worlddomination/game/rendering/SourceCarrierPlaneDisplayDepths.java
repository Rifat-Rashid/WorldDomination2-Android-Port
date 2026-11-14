package com.example.worlddomination.game.rendering;

import java.util.Objects;

/** Source battle-root occupancy for one ship_plane and its retained impact clip. */
public final class SourceCarrierPlaneDisplayDepths implements SourceStoppedClipQueue.Clip {
    public final FlashCarrierPlaneAnimation animation;
    private final BattlefieldDisplayState display;
    private final SourceStoppedClipQueue stoppedClips;
    private final String unitName;
    private final SourceDisplayList.Entry<BattlefieldDisplayState.Item> body;
    private final SourceDisplayList.Entry<BattlefieldDisplayState.Item> shadow;
    private final SourceDisplayList.Entry<BattlefieldDisplayState.Item> tint;
    private final SourceDisplayList.Entry<BattlefieldDisplayState.Item> dot;
    private SourceDisplayList.Entry<BattlefieldDisplayState.Item> explosion;
    private String explosionLinkage;
    private int explosionFrameIndex = -1;
    private int explosionStopFrame;
    private double explosionX;
    private double explosionY;
    private boolean queued;

    public SourceCarrierPlaneDisplayDepths(BattlefieldDisplayState display,
            FlashCarrierPlaneAnimation animation, int precedingUnitParameter,
            SourceStoppedClipQueue stoppedClips) {
        this.display = Objects.requireNonNull(display, "display");
        this.animation = Objects.requireNonNull(animation, "animation");
        this.stoppedClips = Objects.requireNonNull(stoppedClips, "stoppedClips");
        if (animation.getSourcePlayback() == null || precedingUnitParameter < 0
                || precedingUnitParameter % 5 != 0)
            throw new IllegalArgumentException("A source ship plane and Unit parameter are required");
        unitName = "unit" + (precedingUnitParameter + 5) / 5;
        // SourceBattleUnitAllocation already installed these physical handles.
        // Rebind each role at its occupied depth rather than allocating peers.
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
        throw new IllegalStateException("Missing constructed ship role " + name);
    }

    /** MovieClip autoplay precedes Unit visits; the impact frame itself is frame 1. */
    public void advanceAttachedTimelines() {
        if (explosion != null && explosion.isAttached()
                && explosionFrameIndex + 1 < explosionStopFrame)
            explosionFrameIndex++;
    }

    /** Call immediately after this physical Unit visit, before the next Unit. */
    public void afterUnitFrame(boolean impact) {
        SourceCarrierPlanePlayback core = animation.getSourcePlayback();
        if (!core.isBodyAttached()) {
            display.remove(body);
            display.remove(shadow);
            display.remove(tint);
        }
        if (impact) {
            if (explosion != null) throw new IllegalStateException("Ship impact attached twice");
            boolean intercepted = animation.getRoute().intercepted;
            if (intercepted) {
                // Unit.as:875-879 explode -> killUnit("default") -> explosion001.
                explosionLinkage = "explosion001";
                explosionStopFrame = 21; // DefineSprite_442_explosion001/frame_21: stop()
                explosionX = core.getPreviousX();
                explosionY = core.getPreviousY();
            } else {
                // Unit.as:944-959 ship_plane_bomb attaches the payload clip at
                // the last waypoint, 104 source pixels below its Y coordinate.
                explosionLinkage = animation.getImpactLinkage();
                if (explosionLinkage == null)
                    explosionLinkage = "explosion_warhead_1";
                explosionStopFrame = SourceRocketDisplayDepths.sourceExplosionStopFrame(
                        explosionLinkage, Integer.MAX_VALUE);
                FlashBattleRoutes.Point[] points = animation.getRoute().waypoints;
                FlashBattleRoutes.Point last = points[points.length - 1];
                explosionX = last.x;
                explosionY = last.y + 104d;
            }
            explosion = display.attach(BattlefieldDisplayState.Kind.EFFECT, -1,
                    unitName + "explosion", this, display.nextDepth());
            explosionFrameIndex = 0;
        }
        if (core.isRemoved() && !queued) {
            stoppedClips.add(this);
            queued = true;
        }
    }

    /** Presenter.allUnitsDone detaches the retained body and effect; dot remains. */
    @Override public void detach() {
        animation.getSourcePlayback().clearStoppedRoles();
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
    public String getExplosionLinkage() { return explosionLinkage; }
    public int getExplosionFrameIndex() { return explosionFrameIndex; }
    public double getExplosionX() { return explosionX; }
    public double getExplosionY() { return explosionY; }
}
