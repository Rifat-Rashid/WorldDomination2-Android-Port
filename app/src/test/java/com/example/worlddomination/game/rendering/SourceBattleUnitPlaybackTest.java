package com.example.worlddomination.game.rendering;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class SourceBattleUnitPlaybackTest {
    @Test public void constructedSatelliteThenChinookVisitsChinookBeforeLaserStop() {
        // Legal seed-zero turn 28: source construction puts satellite before
        // the Chinook cohort, but View.f_37 enumerates active keys in reverse.
        // At global tick 424 the Chinook moves to (1087.75,833.75) before the
        // satellite enters stop and ends this unit frame.
        SourceBattleUnitPlayback playback = new SourceBattleUnitPlayback();
        List<String> visited = new ArrayList<>();
        playback.addConstructed(() -> { visited.add("satellite-stop"); return true; });
        playback.addConstructed(() -> { visited.add("chinook"); return false; });
        assertTrue(playback.advanceFrame());
        assertEquals(Arrays.asList("chinook", "satellite-stop"), visited);
    }

    @Test public void sourceStopBreakKeepsAlreadyVisitedUnitsAndSkipsLaterOnes() {
        SourceBattleUnitPlayback playback = new SourceBattleUnitPlayback();
        List<String> visited = new ArrayList<>();
        boolean[] stopLaser = {true};
        playback.add(() -> { visited.add("chinook"); visited.add("tank");
            visited.add("turret"); return false; });
        playback.add(() -> { visited.add("satellite"); return stopLaser[0]; });
        playback.add(() -> { visited.add("later-unit"); return false; });
        assertTrue(playback.advanceFrame());
        assertEquals(Arrays.asList("chinook", "tank", "turret", "satellite"), visited);
        visited.clear();
        stopLaser[0] = false;
        assertFalse(playback.advanceFrame());
        assertEquals(Arrays.asList("chinook", "tank", "turret", "satellite", "later-unit"), visited);
    }

    @Test public void resetDropsOldBattleCallbacksAndEmptyFrameDoesNothing() {
        SourceBattleUnitPlayback playback = new SourceBattleUnitPlayback();
        playback.add(() -> { fail("Old battle must not tick"); return false; });
        assertEquals(1, playback.size());
        playback.reset();
        assertEquals(0, playback.size());
        assertFalse(playback.advanceFrame());
    }

    @Test(expected = NullPointerException.class) public void rejectsMissingFrameAction() {
        new SourceBattleUnitPlayback().add(null);
    }
}
