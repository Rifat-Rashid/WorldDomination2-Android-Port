package com.example.worlddomination.game.simulation;

import java.util.EnumSet;

/** Source ch_018/ch_019 state, owned by one game or one private battle copy. */
public final class SourceHiddenCommands {
    public enum Flag { ORCA, EASTER, NESSY, DISCO, KILL_ALL, BUSH, CASH, TECH, STOCK }

    public static final class Submission {
        public final boolean recognized;
        public final String message;

        private Submission(boolean recognized, String message) {
            this.recognized = recognized;
            this.message = message;
        }
    }

    private static final Submission TOGGLE = new Submission(true, null);
    private static final Submission UNKNOWN = new Submission(false, null);

    private final EnumSet<Flag> active = EnumSet.noneOf(Flag.class);
    private boolean scoreCorrupt;

    /** Reports an exact source match and any authored message. */
    public Submission submit(String input) {
        scoreCorrupt = true;
        if (input == null) {
            return UNKNOWN;
        }
        switch (input) {
            case "free willy": toggle(Flag.ORCA); return TOGGLE;
            case "rapa nui": toggle(Flag.EASTER); return TOGGLE;
            case "nessy": toggle(Flag.NESSY); return TOGGLE;
            case "saturdaynight": toggle(Flag.DISCO); return TOGGLE;
            case "psycho killer": toggle(Flag.KILL_ALL); return TOGGLE;
            case "I am Bush": toggle(Flag.BUSH); return TOGGLE;
            case "trump": toggle(Flag.CASH); return TOGGLE;
            case "dexter": toggle(Flag.TECH); return TOGGLE;
            case "gimmegimme": toggle(Flag.STOCK); return TOGGLE;
            case "#2dawgz": return new Submission(true, "## 2 dawgz / 2 pirates. No ninja's. ##");
            case "#thenizz": return new Submission(true, "## dawgz 4 life, homez 4e4 ##");
            case "#lollerskates": return new Submission(true, "## 2 long, 2 much ##");
            case "#fc1": return new Submission(true, "## It is only after you've lost everything, that you are free to do anything... ## ~Chuck Palahniuk, Fight Club, Chapter 8 ");
            case "#sa1": return new Submission(true, "## 4 teh goons, humor me with your witty sarcasm. As no one seems to get. ##");
            case "#error": return new Submission(true, "## get a life, you deserve it ##");
            default: return UNKNOWN;
        }
    }

    public boolean isActive(Flag flag) {
        return active.contains(flag);
    }

    public boolean isScoreCorrupt() {
        return scoreCorrupt;
    }

    public SourceHiddenCommands copy() {
        SourceHiddenCommands copy = new SourceHiddenCommands();
        copy.restore(active, scoreCorrupt);
        return copy;
    }

    void restore(EnumSet<Flag> flags, boolean scoreCorrupt) {
        active.clear();
        active.addAll(flags);
        this.scoreCorrupt = scoreCorrupt;
    }

    private void toggle(Flag flag) {
        if (!active.remove(flag)) {
            active.add(flag);
        }
    }
}
