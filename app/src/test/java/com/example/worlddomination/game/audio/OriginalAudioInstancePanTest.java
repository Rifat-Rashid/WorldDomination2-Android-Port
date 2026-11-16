package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Sound;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** Per-voice delegation; a fake Sound models libGDX's harmless unknown/stopped IDs. */
public final class OriginalAudioInstancePanTest {
    @Test public void panUpdatesOnlyTheRequestedVoiceAndRetainsPlaybackVolume() throws Exception {
        OriginalAudio audio = new OriginalAudio();
        FakeSound fake = new FakeSound();
        Field cache = OriginalAudio.class.getDeclaredField("sounds");
        cache.setAccessible(true);
        @SuppressWarnings("unchecked") Map<OriginalAudio.Cue, Sound> sounds =
                (Map<OriginalAudio.Cue, Sound>) cache.get(audio);
        sounds.put(OriginalAudio.Cue.WARROCKET, fake.sound);
        long first = audio.playInstance(OriginalAudio.Cue.WARROCKET, 0.25f);
        long second = audio.playInstance(OriginalAudio.Cue.WARROCKET, -0.25f);
        audio.setInstancePan(OriginalAudio.Cue.WARROCKET, first, 2f);
        assertEquals(1f, fake.pan.get(first), 0f);
        assertEquals(-0.25f, fake.pan.get(second), 0f);
        assertEquals(fake.playVolume, fake.volume.get(first), 0f);
        audio.setInstancePan(OriginalAudio.Cue.WARROCKET, second, -2f);
        assertEquals(-1f, fake.pan.get(second), 0f);
        assertEquals(1f, fake.pan.get(first), 0f);
        audio.setInstancePan(OriginalAudio.Cue.WARROCKET, -1, 0f);
        audio.setInstancePan(null, first, 0f);
        audio.setInstancePan(OriginalAudio.Cue.TANK_GUN, first, 0f);
        audio.setInstancePan(OriginalAudio.Cue.WARROCKET, first, Float.NaN);
        assertEquals(1f, fake.pan.get(first), 0f);
        audio.pause();
        audio.setInstancePan(OriginalAudio.Cue.WARROCKET, first, 0.5f);
        assertEquals(0.5f, fake.pan.get(first), 0f);
        audio.stop(OriginalAudio.Cue.WARROCKET, first);
        audio.setInstancePan(OriginalAudio.Cue.WARROCKET, first, -0.5f);
        audio.setInstancePan(OriginalAudio.Cue.WARROCKET, 999, 0f);
        assertFalse(fake.pan.containsKey(first));
        assertEquals(-1f, fake.pan.get(second), 0f);
        audio.resume();
    }

    private static final class FakeSound {
        final Map<Long,Float> pan = new HashMap<>();
        final Map<Long,Float> volume = new HashMap<>();
        long next = 1;
        float playVolume;
        final Sound sound = (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(),
                new Class<?>[]{Sound.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "play":
                            long id = next++;
                            playVolume = (Float) args[0];
                            volume.put(id, playVolume);
                            pan.put(id, (Float) args[2]);
                            return id;
                        case "setPan":
                            long target = (Long) args[0];
                            if (pan.containsKey(target)) {
                                pan.put(target, (Float) args[1]);
                                volume.put(target, (Float) args[2]);
                            }
                            return null;
                        case "stop":
                            if (args != null && args.length == 1) pan.remove((Long) args[0]);
                            return null;
                        default: return null;
                    }
                });
    }
}
