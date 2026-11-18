package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.simulation.GameState;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** View.showSingleBuilding's disco override applies only to religion clips. */
public final class SourceDiscoActionBuildingTest {
    @Test public void discoChangesOnlyReligionPoseAndRecreatesItsChildClock() {
        GameState game = new GameState(1, 17L);
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
        game.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();
        List<GameState.ActionBuildingEvent> human = new ArrayList<>();
        for (GameState.ActionBuildingEvent event : game.getLastTurnActionBuildings()) {
            if (event.playerId == 0) human.add(event);
        }
        GameState.ActionBuildingEvent resources = null;
        GameState.ActionBuildingEvent religion = null;
        for (GameState.ActionBuildingEvent event : human) {
            if ("resources".equals(event.frameName)) resources = event;
            if ("religion".equals(event.frameName)) religion = event;
        }
        assertNotNull(resources);
        assertNotNull(religion);

        FlashActionBuildingAnimation ordinary =
                new FlashActionBuildingAnimation(religion, null, false);
        FlashActionBuildingAnimation disco =
                new FlashActionBuildingAnimation(religion, null, true);
        assertEquals(religion.religionCode, ordinary.religionCode);
        assertEquals("disco", disco.religionCode);
        assertEquals(religion.frameName, disco.frameName);
        assertEquals(religion.playerId, disco.playerId);
        assertEquals(religion.slot, disco.slot);

        FlashActionBuildingAnimation other =
                new FlashActionBuildingAnimation(resources, null, true);
        assertEquals(resources.religionCode, other.religionCode);
        assertEquals(resources.frameName, other.frameName);

        for (int i = 0; i < 13; i++) disco.tick();
        FlashActionBuildingAnimation reused =
                new FlashActionBuildingAnimation(religion, disco, true);
        assertEquals("the second disco seek constructs a fresh child",
                0, reused.getNestedFrameIndex(5334, 31));
        FlashActionBuildingAnimation changedPose =
                new FlashActionBuildingAnimation(religion, disco, false);
        assertEquals(0, changedPose.getNestedFrameIndex(5334, 31));
    }
}
