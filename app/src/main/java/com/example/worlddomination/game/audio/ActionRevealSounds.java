package com.example.worlddomination.game.audio;

import com.example.worlddomination.game.simulation.GameState;

import java.util.Objects;

/** Source View.play_AB_selectMC label cue order, independent of physical draw order. */
final class ActionRevealSounds {
    interface CueSink { void play(OriginalAudio.Cue cue); }

    private ActionRevealSounds() { }

    static void play(GameState.Action action, String[] physicalSlots, int mediaVariant,
                     CueSink sink) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(sink, "sink");
        if (physicalSlots == null || physicalSlots.length != 10)
            throw new IllegalArgumentException("Expected two physical slots for each of five players");

        OriginalAudio.Cue general = generalCue(action, mediaVariant);
        if (general != null) sink.play(general);
        if (action != GameState.Action.PREPARED_ATTACK && action != GameState.Action.ATTACK)
            return;

        boolean airfield = false, silo = false, sub = false;
        // Source ASArray keys visit the last player first. The conventional input
        // uses player*2+slot-1, so visit slot 1 then slot 2 explicitly.
        for (int player = 4; player >= 0; player--) {
            for (int slot = 1; slot <= 2; slot++) {
                String frame = physicalSlots[player * 2 + slot - 1];
                if (action == GameState.Action.ATTACK) {
                    if ("hatch".equals(frame)) sink.play(OriginalAudio.Cue.HATCH_REVEAL);
                } else if ("airfield".equals(frame) && !airfield) {
                    sink.play(OriginalAudio.Cue.AIRFIELD_REVEAL);
                    airfield = true;
                } else if ("silo".equals(frame) && !silo) {
                    sink.play(OriginalAudio.Cue.SILO_REVEAL);
                    silo = true;
                } else if ("sub".equals(frame) && !sub) {
                    sink.play(OriginalAudio.Cue.SUB_REVEAL);
                    sub = true;
                }
                // Source requests building_space_launch_2.wav, which has no linkage.
            }
        }
    }

    private static OriginalAudio.Cue generalCue(GameState.Action action, int mediaVariant) {
        switch (action) {
            case RESOURCES: return OriginalAudio.Cue.RESOURCES_REVEAL;
            case PRODUCTION: return OriginalAudio.Cue.PRODUCTION_REVEAL;
            case RESEARCH: return OriginalAudio.Cue.RESEARCH_REVEAL;
            case PROPAGANDA: return OriginalAudio.Cue.PROPAGANDA_REVEAL;
            case DIPLOMACY: return OriginalAudio.Cue.DIPLOMACY_REVEAL;
            case MASS_MEDIA: return OriginalAudio.massMediaRevealCue(mediaVariant);
            default: return null;
        }
    }
}
