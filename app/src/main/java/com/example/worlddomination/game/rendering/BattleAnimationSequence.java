package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.audio.OriginalAudio;

/** One source-frame-timed Flash animation positioned at a battlefield city. */
public final class BattleAnimationSequence {
    public final int targetPlayerId;
    public final int cityIndex;
    public final FlashAnimationTimeline timeline;
    public final OriginalAudio.Cue audioCue;

    private int delayFrames;
    private int currentFrameIndex = -1;
    private boolean started;
    private boolean finished;

    public BattleAnimationSequence(FlashAnimationTimeline timeline, int targetPlayerId,
                                   int cityIndex, int delayFrames,
                                   OriginalAudio.Cue audioCue) {
        if (timeline == null || targetPlayerId < 0 || targetPlayerId >= 5
                || cityIndex < 0 || cityIndex >= 5 || delayFrames < 0) {
            throw new IllegalArgumentException("Invalid battlefield animation sequence");
        }
        this.timeline = timeline;
        this.targetPlayerId = targetPlayerId;
        this.cityIndex = cityIndex;
        this.delayFrames = delayFrames;
        this.audioCue = audioCue;
    }

    /** Advances one source tick; returns true only on the animation's first tick. */
    public boolean tick() {
        if (finished) {
            return false;
        }
        if (!started) {
            if (delayFrames > 0) {
                delayFrames--;
                return false;
            }
            started = true;
            currentFrameIndex = 0;
            finishIfAtEnd();
            return true;
        }
        currentFrameIndex++;
        finishIfAtEnd();
        return false;
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isFinished() {
        return finished;
    }

    public FlashAnimationTimeline.Frame getCurrentFrame() {
        return started && !finished ? timeline.getFrame(currentFrameIndex) : null;
    }

    private void finishIfAtEnd() {
        if (timeline.getFrameCount() == 0 || currentFrameIndex >= timeline.getFrameCount()) {
            finished = true;
            currentFrameIndex = -1;
        }
    }
}
