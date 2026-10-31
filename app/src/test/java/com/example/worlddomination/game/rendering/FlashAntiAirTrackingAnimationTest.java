package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class FlashAntiAirTrackingAnimationTest {
    @Test
    public void sourceDirectionRoundsTheOriginalEighteenWayCompass() {
        assertEquals(9, FlashAntiAirTrackingAnimation.sourceDirection(0f, 0f,
                200f, 0f));
        assertEquals(14, FlashAntiAirTrackingAnimation.sourceDirection(0f, 0f,
                0f, -200f));
        assertEquals(5, FlashAntiAirTrackingAnimation.sourceDirection(0f, 0f,
                0f, 200f));
        assertEquals(18, FlashAntiAirTrackingAnimation.sourceDirection(0f, 0f,
                -200f, 0f));
    }

    @Test
    public void countdownAndFadeOnlyAdvanceWhenThePlaneIsWithinTrackingRange() {
        TargetStub target = targetNear(1, 500f, 0f);
        FlashAntiAirTrackingAnimation animation = animation(target);

        animation.tick();
        assertEquals(FlashAntiAirTrackingAnimation.INITIAL_COUNTDOWN_TICKS,
                animation.getCountdownTicks());
        assertEquals(0f, animation.getAlpha(), 0f);

        setRelativeToBuilding(target, 1, 1, 399f, 0f);
        animation.tick();
        assertEquals(49, animation.getCountdownTicks());
        for (int tick = 1; tick < FlashAntiAirTrackingAnimation.INITIAL_COUNTDOWN_TICKS;
             tick++) {
            animation.tick();
        }
        assertEquals(0, animation.getCountdownTicks());
        assertEquals(0f, animation.getAlpha(), 0f);

        animation.tick();
        assertEquals(0.1f, animation.getAlpha(), 0.0001f);
        for (int tick = 0; tick < 9; tick++) {
            animation.tick();
        }
        assertEquals(1f, animation.getAlpha(), 0f);
    }

    @Test
    public void directionChangesAndFiringUseTheSourceAlternatingFrames() {
        TargetStub target = targetNear(1, 170f, 0f);
        FlashAntiAirTrackingAnimation animation = animation(target);

        animation.tick();
        assertEquals(9, animation.getDirection());
        assertEquals(16, animation.getFrameIndex());
        assertFalse(animation.takeGunCue());

        setRelativeToBuilding(target, 1, 1, 169f, 0f);
        animation.tick();
        assertEquals(17, animation.getFrameIndex());
        assertTrue(animation.takeGunCue());
        animation.tick();
        assertEquals(16, animation.getFrameIndex());
        assertFalse(animation.takeGunCue());
        animation.tick();
        assertEquals(17, animation.getFrameIndex());

        setRelativeToBuilding(target, 1, 1, 170f, 0f);
        animation.tick();
        assertEquals("The firing frame remains until an in-range update toggles it",
                17, animation.getFrameIndex());
    }

    @Test
    public void sourceFireOffsetRemainsSelectedUntilTheNextInRangeToggle() {
        TargetStub target = targetNear(1, 169f, 0f);
        FlashAntiAirTrackingAnimation animation = animation(target);

        animation.tick();
        assertEquals(17, animation.getFrameIndex());
        assertTrue(animation.takeGunCue());

        setRelativeToBuilding(target, 1, 1, 171f, 0f);
        animation.tick();
        assertEquals("The source keeps antiOffset=1 after leaving the firing radius",
                17, animation.getFrameIndex());

        setRelativeToBuilding(target, 1, 1, 169f, 0f);
        animation.tick();
        assertEquals(16, animation.getFrameIndex());
    }

    @Test
    public void animationUsesTheDefendersSelectedIslandSlotAndReligionFrame() {
        TargetStub target = targetNear(3, 0f, 0f);
        FlashAntiAirTrackingAnimation animation =
                new FlashAntiAirTrackingAnimation(target, 3, 2, "com");

        FlashBattleRoutes.Point expected = FlashBattleRoutes.actionBuildingPosition(3, 2);
        assertEquals(3, animation.getDefenderPlayerId());
        assertEquals(2, animation.getActionBuildingSlot());
        assertEquals(expected.x, animation.getBuildingPosition().x, 0f);
        assertEquals(expected.y, animation.getBuildingPosition().y, 0f);
        assertEquals(2, animation.getReligionFrameIndex());
        assertEquals(0, FlashAntiAirTrackingAnimation.religionFrameIndex("mus"));
        assertEquals(1, FlashAntiAirTrackingAnimation.religionFrameIndex("jew"));
        assertEquals(3, FlashAntiAirTrackingAnimation.religionFrameIndex("chr"));
        assertEquals(4, FlashAntiAirTrackingAnimation.religionFrameIndex("bhu"));
    }

    @Test
    public void defenderCanTrackOnlyOneAircraftAtATime() {
        FlashAntiAirTrackingAnimation.TargetLock lock =
                new FlashAntiAirTrackingAnimation.TargetLock();

        assertTrue(lock.tryLock(2));
        assertFalse(lock.tryLock(2));
        assertTrue(lock.tryLock(3));
    }

    @Test
    public void endedAircraftStopsItsTurretPresentation() {
        TargetStub target = targetNear(1, 100f, 0f);
        FlashAntiAirTrackingAnimation animation = animation(target);
        animation.tick();
        target.finished = true;

        animation.tick();

        assertTrue(animation.isFinished());
        assertFalse(animation.isVisible());
    }

    private static FlashAntiAirTrackingAnimation animation(TargetStub target) {
        FlashBattleRoutes.Point position = FlashBattleRoutes.actionBuildingPosition(1, 1);
        target.setPosition(position.x + target.offsetX, position.y + target.offsetY);
        return new FlashAntiAirTrackingAnimation(target, 1, 1, "chr");
    }

    private static void setRelativeToBuilding(TargetStub target, int defenderId,
                                              int slot, float dx, float dy) {
        FlashBattleRoutes.Point position =
                FlashBattleRoutes.actionBuildingPosition(defenderId, slot);
        target.setPosition(position.x + dx, position.y + dy);
    }

    private static TargetStub targetNear(int defenderId, float offsetX, float offsetY) {
        TargetStub target = new TargetStub(defenderId);
        target.offsetX = offsetX;
        target.offsetY = offsetY;
        return target;
    }

    private static final class TargetStub implements FlashAntiAirTrackingAnimation.Target {
        private final int targetPlayerId;
        private float x;
        private float y;
        private float offsetX;
        private float offsetY;
        private boolean visible = true;
        private boolean finished;

        private TargetStub(int targetPlayerId) {
            this.targetPlayerId = targetPlayerId;
        }

        private void setPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int targetPlayerId() {
            return targetPlayerId;
        }

        @Override
        public float sourceX() {
            return x;
        }

        @Override
        public float sourceY() {
            return y;
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public boolean isFinished() {
            return finished;
        }
    }
}
