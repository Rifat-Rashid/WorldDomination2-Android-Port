package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.AntiDefenseAudioPlayback;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;
import org.junit.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

/** Captured callback coordinates plus explicitly synthetic lock boundaries. */
public final class SourceRocketDefenseControllerTest {
    private static final int DEFENDER = 1;
    private static final int SLOT = 2;

    @Test public void capturedFirstCallbackAndHostFrame() throws Exception {
        AntiDefenseAudioPlayback audio = silent();
        SourceRocketDefenseController c = controller(new FlashAntiDefenseTrackingAnimation.TargetLock());
        FlashRocketAnimation rocket = rocket();
        SourceRocketDefenseController.UnitVisit u = c.register(rocket,
                audio.createOwner(AntiDefenseAudioPlayback.Type.ROCKET));
        // Compatibility capture tick 196: View has moved to (396.25,354.25)
        // and elected the physical defense at (130.75,91.75).
        set(rocket, "activeAndMoving", true);
        set(rocket, "x", 396.25d); set(rocket, "y", 354.25d);
        c.afterUnitVisit(u, false, false);
        assertEquals(130.75d, u.getAntiX(), 0d);
        assertEquals(91.75d, u.getAntiY(), 0d);
        assertEquals(49, u.state.getCountdown());
        assertEquals(136, u.state.getRawBearingDegrees());
        c.advanceHostFrame();
        assertEquals(14, c.getPlayback().getTurretFrame());
        assertSame(rocket, c.getLockedTarget());
        assertEquals(FlashAntiDefenseTrackingAnimation.DefenseType.ROCKET,
                c.getTracker().getDefenseType());
        audio.finish();
    }

    @Test public void syntheticSelfKillClearsLockAndCannotReclaim() throws Exception {
        AntiDefenseAudioPlayback audio = silent();
        FlashAntiDefenseTrackingAnimation.TargetLock lock = new FlashAntiDefenseTrackingAnimation.TargetLock();
        SourceRocketDefenseController c = controller(lock);
        FlashRocketAnimation rocket = rocket();
        SourceRocketDefenseController.UnitVisit u = c.register(rocket,
                audio.createOwner(AntiDefenseAudioPlayback.Type.ROCKET));
        set(rocket, "activeAndMoving", true);
        set(rocket, "x", 243.1489951317337d); set(rocket, "y", 0.4153796688429736d);
        c.afterUnitVisit(u, false, false);
        assertSame(rocket, c.getLockedTarget());
        set(rocket, "previousX", 243.1489951317337d);
        set(rocket, "previousY", 0.4153796688429736d);
        set(rocket, "bodyAttached", false);
        set(rocket, "x", 239.66563049566392d); set(rocket, "y", 6.541847198286916d);
        c.afterUnitVisit(u, true, true);
        assertNull(c.getLockedTarget());
        assertFalse(lock.isLockedTo(DEFENDER, u));
        assertEquals(48, u.state.getCountdown());
        assertEquals(220, u.state.getRawBearingDegrees());
        assertEquals(1, u.state.getOffset());
        assertEquals(21, c.getPlayback().getTurretFrame());
        assertFalse(c.getPlayback().isTurretPlaying());
        audio.finish();
    }

    @Test public void syntheticOuterSeekResetsOnlyOnTransition() throws Exception {
        SourceRocketDefenseController c = controller(new FlashAntiDefenseTrackingAnimation.TargetLock());
        SourceAntiDefensePlayback before = c.getPlayback();
        before.publishHostFrame(17);
        c.notifyFireDefense(false);
        assertSame(before, c.getPlayback());
        assertEquals(17, c.getPlayback().getTurretFrame());
        c.notifyFireDefense(true);
        assertNotSame(before, c.getPlayback());
        assertEquals(1, c.getPlayback().getTurretFrame());
        assertFalse(c.getPlayback().isTurretPlaying());
    }

    private static void set(FlashRocketAnimation rocket, String name, Object value) throws Exception {
        Field f = SourceRocketPlayback.class.getDeclaredField(name);
        f.setAccessible(true); f.set(rocket.getSourcePlayback(), value);
    }
    private static AntiDefenseAudioPlayback silent() {
        return new AntiDefenseAudioPlayback((cue, repeatCount) -> null, Runnable::run);
    }
    private static FlashRocketAnimation rocket() {
        return FlashRocketAnimation.forSourceOrdinary(
                FlashBattleRoutes.createRocketStrike(0, DEFENDER, 2, 1, true),
                null, null, new ReferenceRandom(0), 5);
    }
    private static SourceRocketDefenseController controller(
            FlashAntiDefenseTrackingAnimation.TargetLock lock) throws Exception {
        Constructor<GameState.ActionBuildingEvent> constructor =
                GameState.ActionBuildingEvent.class.getDeclaredConstructor(int.class,
                        GameState.Action.class, int.class, String.class, String.class, int.class);
        constructor.setAccessible(true);
        GameState.ActionBuildingEvent event = constructor.newInstance(DEFENDER,
                GameState.Action.PREPARED_ATTACK, SLOT, "antiRocket_fire", "chr", 0);
        return new SourceRocketDefenseController(DEFENDER, SLOT, "chr",
                new FlashActionBuildingAnimation(event), lock);
    }
}
