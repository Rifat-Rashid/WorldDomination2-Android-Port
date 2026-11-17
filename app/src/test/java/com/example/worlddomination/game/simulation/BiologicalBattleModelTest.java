package com.example.worlddomination.game.simulation;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Source Sub_presenter resolves a retained flag; View creates clouds only on impact. */
public final class BiologicalBattleModelTest {
    @Test
    public void legalSeventeenTurnSubmarineBioPayloadMatchesSourceModelBoundary() {
        GameState game = legalSeventeenTurnBioLaunchQueued();
        assertEquals(16, game.getTurn());
        assertEquals(50, game.getPlayerState(1).getCity(2).populationMillions);
        assertEquals(4, game.getPlayerState(1).getCity(2).getArtworkFrame());
        assertFalse(game.getPlayerState(1).getCity(2).bioloaded);
        assertFalse(game.getPlayerState(1).getCity(2).bioloadInBattle);

        game.confirmTurn();
        assertEquals(17, game.getTurn());
        GameState.CombatEvent strike = null;
        for (GameState.CombatEvent event : game.getLastTurnCombatEvents()) {
            if ("sub".equals(event.weaponName) && "bioload3".equals(event.payloadName)) {
                strike = event;
                break;
            }
        }
        assertTrue("source turn 17 contains the successful bioload3 submarine hit",
                strike != null);
        assertEquals(GameState.CombatEventType.STRIKE_IMPACT, strike.type);
        assertEquals(1, strike.defenderPlayerId);
        assertEquals(2, strike.cityIndex);
        assertEquals(43, strike.casualtiesMillions);
        assertTrue(strike.bioload);

        GameState.BattleCitySnapshot snapshot = game.getBattlePresentationCity(1, 2);
        assertEquals(50, snapshot.populationMillions);
        assertEquals(4, snapshot.artworkFrame);
        assertFalse(snapshot.bioloaded);
        assertTrue(snapshot.bioloadInBattle);
        CityState canonical = game.getPlayerState(1).getCity(2);
        assertEquals(7, canonical.populationMillions);
        assertEquals(1, canonical.getArtworkFrame());
        assertFalse(canonical.bioloaded);
        assertTrue(canonical.bioloadInBattle);
        assertTrue(game.getPlayerState(1).isBioloaded());
        assertEquals(0.9551472373193942,
                game.createPresentationRandomCopy().nextDouble(), 0d);
    }

    @Test
    public void zeroIdRocketSurvivesBiologicalPenaltyAndKeepsNextAiTurnOnSourceStream() {
        GameState game = legalSeventeenTurnBioLaunchQueued();
        game.confirmTurn();
        GameState.PlayerState target = game.getPlayerState(1);
        GameState.WeaponState rocket = target.getWeapons().get("rocket1");
        // Presenter.deactivateAfterBio tests truth(_stock): rocket1's ID 0 is false.
        assertTrue("sampled zero-ID rocket stays active", rocket.active);
        assertFalse("zero-ID stock does not receive contamination", rocket.bioloaded);
        assertEquals(2, rocket.inStock);
        for (String name : new String[]{"warhead1", "bioload1", "soldier1", "soldier3", "soldier4"}) {
            GameState.WeaponState weapon = target.getWeapons().get(name);
            assertFalse("positive-ID sampled stock disabled: " + name, weapon.active);
            assertTrue("positive-ID sampled stock contaminated: " + name, weapon.bioloaded);
        }
        assertTrue("unsampled soldier remains active", target.getWeapons().get("soldier2").active);
        assertEquals(0.9551472373193942,
                game.createPresentationRandomCopy().nextDouble(), 0d);

        rejectPendingDiplomacy(game);
        resources(game);
        game.selectAction(GameState.Action.MASS_MEDIA);
        assertTrue(game.acceptSelectedAction());
        game.confirmTurn();

        assertEquals(18, game.getTurn());
        assertEquals(java.util.Arrays.asList(GameState.Action.PREPARED_ATTACK,
                GameState.Action.PRODUCTION), target.getLastTurnActions());
        assertEquals(java.util.Arrays.asList(GameState.Action.RESOURCES,
                GameState.Action.MASS_MEDIA), game.getPlayerState(2).getLastTurnActions());
        assertEquals("rocket1", target.getPreparedCarrierName());
        assertEquals(1, rocket.inStock);
        assertFalse(target.isBioloaded());
        assertTrue(target.getWeapons().get("warhead1").active);
        assertFalse(target.getWeapons().get("warhead1").bioloaded);
        // Independent Python capture, including all canonical city populations.
        int[][] populations = {{91, 94, 75, 103, 37}, {93, 77, 7, 74, 171},
                {102, 109, 65, 96, 93}};
        int[] totals = {400, 422, 465};
        int[] religions = {34, 44, 51};
        long[] money = {150, 97, 126};
        long[] scores = {335162, 224104, 491052};
        for (int playerId = 0; playerId < 3; playerId++) {
            GameState.PlayerState player = game.getPlayerState(playerId);
            assertEquals("population player " + playerId, totals[playerId], player.populationMillions);
            assertEquals("religion player " + playerId, religions[playerId], player.religion);
            assertEquals("money player " + playerId, money[playerId], player.moneyMillions);
            assertEquals("score player " + playerId, scores[playerId], player.score);
            for (int city = 0; city < 5; city++) {
                assertEquals("population player " + playerId + " city " + city,
                        populations[playerId][city], player.getCity(city).populationMillions);
            }
        }
        assertEquals("canonical next draw after 32 source draws", 0.013467678145623552,
                game.createPresentationRandomCopy().nextDouble(), 0d);
    }

    /** Legal source queue at turn 17, before confirm; reusable by native tests. */
    public static GameState legalSeventeenTurnBioLaunchQueued() {
        GameSetup setup = new GameSetup(0L);
        setup.setDifficulty(1);
        assertTrue(setup.toggleOpponent(0));
        assertTrue(setup.toggleOpponent(1));
        GameState game = new GameState(setup);
        for (String group : new String[]{"rocket", "warhead", "bioload", "soldier"}) {
            for (int level = 2; level <= 4; level++) {
                rejectPendingDiplomacy(game);
                research(game, group + level);
                resources(game);
                game.confirmTurn();
            }
        }
        rejectPendingDiplomacy(game);
        research(game, "sub"); resources(game); game.confirmTurn();
        rejectPendingDiplomacy(game);
        production(game, "special"); resources(game); game.confirmTurn();
        rejectPendingDiplomacy(game);
        production(game, "bioload"); resources(game); game.confirmTurn();
        rejectPendingDiplomacy(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedCarrier("sub"));
        assertTrue(game.acceptSelectedAction());
        resources(game); game.confirmTurn();
        rejectPendingDiplomacy(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedLaunch(1, 2, "bioload3"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        return game;
    }

    private static void rejectPendingDiplomacy(GameState game) {
        while (game.getPendingHumanDiplomacyProposal() != null) {
            assertTrue(game.respondToDiplomacyProposal(false));
        }
    }

    private static void research(GameState game, String technology) {
        game.selectAction(GameState.Action.RESEARCH);
        assertTrue(game.selectResearchTechnology(technology));
        assertTrue(game.acceptSelectedAction());
    }

    private static void production(GameState game, String category) {
        game.selectAction(GameState.Action.PRODUCTION);
        assertTrue(game.configureProduction(category, 100));
        assertTrue(game.acceptSelectedAction());
    }

    @Test
    public void successfulPayloadRetainsBattleFlagWithoutCloudingCanonicalCity() {
        GameState game = new GameState(1, 0L, false);
        GameState.PlayerState target = game.getPlayerState(1);
        CityState city = target.getCity(2);
        assertFalse(city.bioloaded);
        assertFalse(city.bioloadInBattle);

        prepareRocket(game);
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedLaunch(1, 2, "bioload1"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();

        List<GameState.CombatEvent> events = game.getLastTurnCombatEvents();
        assertEquals(1, events.size());
        assertEquals(GameState.CombatEventType.STRIKE_IMPACT, events.get(0).type);
        assertTrue(events.get(0).bioload);
        assertTrue(target.isBioloaded());
        assertFalse(city.bioloaded);
        assertTrue(city.bioloadInBattle);
        GameState.BattleCitySnapshot snapshot = game.getBattlePresentationCity(1, 2);
        assertFalse(snapshot.bioloaded);
        assertTrue(snapshot.bioloadInBattle);
        double next = game.createBattlePresentationRandomCopy().nextDouble();
        assertEquals(next, game.createBattlePresentationRandomCopy().nextDouble(), 0d);

        resources(game);
        resources(game);
        game.confirmTurn();
        assertFalse(target.isBioloaded());
        assertFalse(city.bioloaded);
        assertTrue("City.bioload_in_battle is not cleared with player penalties",
                city.bioloadInBattle);
        assertFalse(game.getBattlePresentationCity(1, 2).bioloaded);
        assertTrue(game.getBattlePresentationCity(1, 2).bioloadInBattle);
    }

    @Test
    public void shipBioFlagTracksAllThreePayloadCities() {
        GameState game = new GameState(1, 207L, false);
        GameState.PlayerState human = game.getHuman();
        human.getWeapons().get("ship").active = true;
        human.getWeapons().get("ship").inStock = 1;
        human.getWeapons().get("bioload1").inStock = 3;
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedCarrier("ship"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();

        game.selectAction(GameState.Action.PREPARED_ATTACK);
        for (int index = 0; index < 3; index++) {
            assertTrue(game.configurePreparedShipStrike(1, index, "bioload1"));
        }
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();

        assertEquals(3, game.getLastTurnCombatEvents().size());
        for (int index = 0; index < 3; index++) {
            CityState city = game.getPlayerState(1).getCity(index);
            assertFalse(city.bioloaded);
            assertTrue(city.bioloadInBattle);
            assertFalse(game.getBattlePresentationCity(1, index).bioloaded);
            assertTrue(game.getBattlePresentationCity(1, index).bioloadInBattle);
        }
        for (GameState.CombatEvent event : game.getLastTurnCombatEvents()) {
            assertTrue(event.bioload);
        }
    }

    @Test
    public void interceptedRocketHasPayloadNameButNoResolvedBiologicalFlag() {
        GameSetup setup = new GameSetup(17L);
        setup.setDifficulty(2);
        for (int leader : new int[]{0, 9, 18, 5}) {
            assertTrue(setup.toggleOpponent(leader));
        }
        GameState game = new GameState(setup, setup.copyGameplayRandom());
        for (int turn = 0; turn < 21; turn++) {
            resources(game);
            game.selectAction(GameState.Action.MASS_MEDIA);
            assertTrue(game.acceptSelectedAction());
            game.confirmTurn();
        }
        game.selectAction(GameState.Action.DEFENSE);
        assertTrue(game.configureDefense("antiRocket"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();

        GameState.CombatEvent intercepted = game.getLastTurnCombatEvents().get(0);
        assertEquals(GameState.CombatEventType.STRIKE_INTERCEPTED, intercepted.type);
        assertEquals("bioload2", intercepted.payloadName);
        assertFalse(intercepted.bioload);
        assertFalse(game.getHuman().getCity(2).bioloadInBattle);
        assertFalse(game.getBattlePresentationCity(0, 2).bioloadInBattle);
    }

    private static void prepareRocket(GameState game) {
        game.selectAction(GameState.Action.PREPARED_ATTACK);
        assertTrue(game.configurePreparedCarrier("rocket1"));
        assertTrue(game.acceptSelectedAction());
        resources(game);
        game.confirmTurn();
    }

    private static void resources(GameState game) {
        game.selectAction(GameState.Action.RESOURCES);
        assertTrue(game.acceptSelectedAction());
    }
}
