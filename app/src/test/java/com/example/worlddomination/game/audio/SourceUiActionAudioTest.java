package com.example.worlddomination.game.audio;

import com.badlogic.gdx.audio.Sound;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public final class SourceUiActionAudioTest {
    // Independent values from Sub_model.py's action_to_sound_arr. All ten
    // entries omit _vol; only switches and stock select provide _pan.
    private static final OriginalAudio.Cue[] SOURCE_ACTION_CUES = {
            OriginalAudio.Cue.STARTUP, OriginalAudio.Cue.ACTION_SWITCH,
            OriginalAudio.Cue.ACTION_SWITCH_OFF, OriginalAudio.Cue.STOCK_SELECT,
            OriginalAudio.Cue.UNIT_SELECT, OriginalAudio.Cue.CONFIRM_OPEN,
            OriginalAudio.Cue.CONFIRM_CLOSE, OriginalAudio.Cue.ACTION_ACCEPT,
            OriginalAudio.Cue.ACTION_OPEN, OriginalAudio.Cue.ACTION_CLOSE
    };
    private static final float[] SOURCE_PANS = {
            0f, -.8f, -.8f, .8f, 0f, 0f, 0f, 0f, 0f, 0f
    };

    @Test public void sourceInterfaceActionCuesStartAtFullGainWithRecordedPan() throws Exception {
        Fixture fixture = new Fixture();
        for (int i = 0; i < SOURCE_ACTION_CUES.length; i++) {
            fixture.audio.play(SOURCE_ACTION_CUES[i]);
            fixture.sound.assertVoice(i + 1L, SOURCE_PANS[i], 1f);
        }
        assertEquals(SOURCE_ACTION_CUES.length, fixture.sound.starts);
        fixture.audio.play(OriginalAudio.Cue.WARROCKET);
        fixture.sound.assertVoice(11L, 0f, .8f);
        fixture.audio.loop(OriginalAudio.Cue.WARROCKET);
        fixture.sound.assertVoice(12L, 0f, .8f);
    }

    @Test public void explicitPanMuteAndSourceSoundOptionPreservePerVoiceGain() throws Exception {
        Fixture fixture = new Fixture();
        long chosen = fixture.audio.playInstance(OriginalAudio.Cue.ACTION_SWITCH, .35f);
        fixture.sound.assertVoice(chosen, .35f, 1f);
        long stock = fixture.audio.playInstance(OriginalAudio.Cue.STOCK_SELECT, -.25f);
        fixture.sound.assertVoice(stock, -.25f, 1f);
        fixture.audio.toggleOutputMuted();
        fixture.sound.assertVoice(chosen, .35f, 0f);
        fixture.sound.assertVoice(stock, -.25f, 0f);
        fixture.audio.play(OriginalAudio.Cue.ACTION_SWITCH_OFF);
        long muted = fixture.sound.next;
        fixture.sound.assertVoice(muted, -.8f, 0f);
        fixture.audio.setInstancePan(OriginalAudio.Cue.ACTION_SWITCH_OFF, muted, .6f);
        fixture.sound.assertVoice(muted, .6f, 0f);
        fixture.audio.pause();
        fixture.audio.resume();
        assertEquals(3, fixture.sound.pauses);
        assertEquals(3, fixture.sound.resumes);
        assertFalse(fixture.audio.toggleSound());
        fixture.audio.toggleOutputMuted();
        fixture.sound.assertVoice(chosen, .35f, 1f);
        fixture.sound.assertVoice(stock, -.25f, 1f);
        fixture.sound.assertVoice(muted, .6f, 1f);
        fixture.audio.play(OriginalAudio.Cue.ACTION_ACCEPT);
        assertEquals(3, fixture.sound.starts);
    }

    private static final class Fixture {
        final OriginalAudio audio = new OriginalAudio();
        final FakeSound sound = new FakeSound();

        @SuppressWarnings("unchecked") Fixture() throws Exception {
            Field cache = OriginalAudio.class.getDeclaredField("sounds");
            cache.setAccessible(true);
            Map<OriginalAudio.Cue, Sound> sounds =
                    (Map<OriginalAudio.Cue, Sound>) cache.get(audio);
            for (OriginalAudio.Cue cue : SOURCE_ACTION_CUES) sounds.put(cue, sound.proxy);
            sounds.put(OriginalAudio.Cue.WARROCKET, sound.proxy);
        }
    }

    private static final class FakeSound {
        final Map<Long, float[]> voices = new HashMap<>();
        int starts, pauses, resumes;
        long next;
        final Sound proxy = (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(),
                new Class<?>[]{Sound.class}, (self, method, args) -> {
                    switch (method.getName()) {
                        case "play":
                        case "loop":
                            starts++;
                            voices.put(++next, new float[]{(Float) args[2], (Float) args[0]});
                            return next;
                        case "setPan":
                            float[] voice = voices.get((Long) args[0]);
                            if (voice != null) {
                                voice[0] = (Float) args[1];
                                voice[1] = (Float) args[2];
                            }
                            return null;
                        case "pause": pauses++; return null;
                        case "resume": resumes++; return null;
                        default: return null;
                    }
                });

        void assertVoice(long id, float pan, float volume) {
            float[] voice = voices.get(id);
            assertNotNull("Missing voice " + id, voice);
            assertEquals(pan, voice[0], 0f);
            assertEquals(volume, voice[1], 0f);
        }
    }
}
