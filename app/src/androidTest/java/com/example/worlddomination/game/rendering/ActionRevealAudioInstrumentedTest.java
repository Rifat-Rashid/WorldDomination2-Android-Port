package com.example.worlddomination.game.rendering;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.WorldDominationGame;
import com.example.worlddomination.game.audio.OriginalAudio;
import com.example.worlddomination.game.simulation.GameSetup;
import com.example.worlddomination.game.simulation.GameState;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Actual World scheduling with fake sound voices, not audible-output evidence. */
@RunWith(AndroidJUnit4.class)
public final class ActionRevealAudioInstrumentedTest {
    @Test public void worldRevealsSourceOneShotsAndClosesOnlyBattleVoices() throws Exception {
        SourceBattleCleanupInstrumentedTest.runInGame(app -> {
            OriginalAudio audio = (OriginalAudio) field(WorldDominationGame.class, "audio").get(app);
            audio.initialize();
            OriginalAudio.Cue resources = cueWithAsset("80_building_Resource.wav.mp3.gz");
            OriginalAudio.Cue production = cueWithAsset("83_building_factory.wav.mp3.gz");
            @SuppressWarnings("unchecked") Map<OriginalAudio.Cue, Sound> cache =
                    (Map<OriginalAudio.Cue, Sound>) field(OriginalAudio.class, "sounds").get(audio);
            SoundSpy resourceSpy = new SoundSpy(), productionSpy = new SoundSpy();
            Sound oldResources = cache.put(resources, resourceSpy.sound);
            Sound oldProduction = cache.put(production, productionSpy.sound);
            try {
                long canonical = audio.playInstance(resources, 0f);
                assertTrue("Unrelated shared-sample voice exists", canonical >= 0);
                for (int battle = 0; battle < 2; battle++) {
                    GameState game = sourceGame();
                    field(WorldDominationGame.class, "game").set(app, game);
                    ((BattlefieldCityRenderer) field(WorldDominationGame.class,
                            "cityRenderer").get(app)).reset(game);
                    ((SourceBattleRecordClock) field(WorldDominationGame.class,
                            "battleRecordClock").get(app)).reset();
                    field(WorldDominationGame.class, "modalOpen").setBoolean(app, false);
                    field(WorldDominationGame.class, "skipNextFrameDelta").setBoolean(app, true);
                    app.render();
                    Rectangle row = ((Rectangle[]) field(WorldDominationGame.class, "navRows")
                            .get(app))[GameState.Action.RESOURCES.ordinal()];
                    Method activate = WorldDominationGame.class.getDeclaredMethod(
                            "activateNavigationRow", float.class, float.class);
                    activate.setAccessible(true);
                    int r0 = resourceSpy.starts.size(), p0 = productionSpy.starts.size();
                    activate.invoke(app, row.x + row.width / 2f, row.y + row.height / 2f);
                    assertEquals(GameState.Action.RESOURCES, game.getSelectedAction());
                    assertTrue(field(WorldDominationGame.class, "modalOpen").getBoolean(app));
                    app.keyDown(Input.Keys.BACK);
                    assertEquals(r0, resourceSpy.starts.size());
                    assertEquals(p0, productionSpy.starts.size());
                    queue(game, GameState.Action.RESOURCES);
                    game.selectAction(GameState.Action.PRODUCTION);
                    assertTrue(game.configureProduction("soldier", 20));
                    assertTrue(game.acceptSelectedAction());
                    invoke(app, "confirmTurnAndPreparePlayback");
                    TurnPlaybackSequence sequence = (TurnPlaybackSequence)
                            field(WorldDominationGame.class, "turnPlaybackSequence").get(app);
                    assertTrue(sequence.isWaitingForActionLabels());
                    assertEquals(r0, resourceSpy.starts.size());
                    assertEquals(p0, productionSpy.starts.size());
                    for (int fixedTick = 1; fixedTick <= 77; fixedTick++) {
                        advance(app);
                        assertEquals("Source tick 38 / fixed step 39", r0 + (fixedTick >= 39 ? 1 : 0), resourceSpy.starts.size());
                        assertEquals("Source tick 76 / fixed step 77", p0 + (fixedTick >= 77 ? 1 : 0), productionSpy.starts.size());
                    }
                    long resourceId = resourceSpy.starts.get(r0);
                    long productionId = productionSpy.starts.get(p0);
                    assertEquals(1f, resourceSpy.volumes.get(r0), 0f);
                    assertEquals(1f, productionSpy.volumes.get(p0), 0f);
                    assertTrue(resourceSpy.active.contains(resourceId));
                    assertTrue(productionSpy.active.contains(productionId));
                    int guard = 0;
                    while (sequence.isWaitingForActionLabels() && guard++ < 500) advance(app);
                    assertTrue("Label pass completes", guard < 500);
                    assertTrue("Reveal sound survives label boundary", resourceSpy.active.contains(resourceId));
                    assertTrue("Reveal sound survives label boundary", productionSpy.active.contains(productionId));
                    assertTrue(resourceSpy.active.contains(canonical));
                    guard = 0;
                    while (sequence.getPhase() != TurnPlaybackSequence.Phase.IDLE && guard++ < 500) advance(app);
                    assertEquals(TurnPlaybackSequence.Phase.IDLE, sequence.getPhase());
                    assertFalse(resourceSpy.active.contains(resourceId));
                    assertFalse(productionSpy.active.contains(productionId));
                    assertTrue("Battle cleanup preserves unrelated shared-sample handle", resourceSpy.active.contains(canonical));
                }
                audio.stop(resources, canonical);
            } finally {
                // Successful iterations already reached the source endBattle handoff.
                // Releasing only our sounds also preserves the original failure if
                // an assertion aborts a live fixture before normal handoff.
                audio.finishBattleActionSounds();
                if (oldResources == null) cache.remove(resources); else cache.put(resources, oldResources);
                if (oldProduction == null) cache.remove(production); else cache.put(production, oldProduction);
            }
        });
    }

    private static GameState sourceGame() {
        GameSetup setup = new GameSetup(7L);
        setup.setDifficulty(1);
        setup.setStartingBuilding(2);
        assertTrue(setup.toggleOpponent(2));
        assertTrue(setup.toggleOpponent(9));
        return new GameState(setup);
    }
    private static void queue(GameState game, GameState.Action action) {
        game.selectAction(action);
        assertTrue(game.acceptSelectedAction());
    }
    private static OriginalAudio.Cue cueWithAsset(String asset) throws Exception {
        Field compressed = field(OriginalAudio.Cue.class, "compressedAsset");
        for (OriginalAudio.Cue cue : OriginalAudio.Cue.values()) {
            if (asset.equals(compressed.get(cue))) return cue;
        }
        throw new AssertionError("Missing source sound " + asset);
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static void invoke(WorldDominationGame app, String name) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod(name);
        method.setAccessible(true); method.invoke(app);
    }
    private static void advance(WorldDominationGame app) throws Exception {
        Method method = WorldDominationGame.class.getDeclaredMethod("fixedUpdate", float.class);
        method.setAccessible(true); method.invoke(app, 1f / 24f);
    }
    private static final class SoundSpy {
        long next;
        final List<Long> starts = new ArrayList<>();
        final List<Float> volumes = new ArrayList<>();
        final Set<Long> active = new HashSet<>();
        final Sound sound = (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(),
                new Class<?>[]{Sound.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "play":
                            assertEquals(3, args.length);
                            assertEquals(1f, (Float) args[1], 0f);
                            assertEquals(0f, (Float) args[2], 0f);
                            long id = ++next;
                            starts.add(id); volumes.add((Float) args[0]); active.add(id); return id;
                        case "stop":
                            if (args != null && args.length == 1) active.remove((Long) args[0]);
                            return null;
                        case "loop": throw new AssertionError("Reveal must play once");
                        default: return method.getReturnType() == long.class ? -1L : null;
                    }
                });
    }
}
