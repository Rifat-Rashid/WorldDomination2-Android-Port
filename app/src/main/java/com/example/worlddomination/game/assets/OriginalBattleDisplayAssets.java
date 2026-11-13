package com.example.worlddomination.game.assets;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.example.worlddomination.game.rendering.FlashAnimationTimeline;

/** Small source placement cache, available before asynchronous battle artwork loading. */
public final class OriginalBattleDisplayAssets {
    public static final int BATTLE_SYMBOL_ID = 5766;
    public static final String PATH = "original/runtime/battle-root.json.gz";

    private OriginalBattleDisplayAssets() { }

    public static FlashAnimationTimeline.Frame load() {
        JsonValue document = new JsonReader().parse(
                OriginalFlashAnimationAssets.readCompressedUtf8(PATH));
        if (!"wd2.battle-display.v1".equals(document.getString("format"))) {
            throw new IllegalStateException("Unsupported battle display metadata");
        }
        FlashAnimationTimeline timeline = FlashAnimationTimeline.fromSymbol(
                document.get("symbol"), document.getFloat("fps"));
        if (timeline.symbolId != BATTLE_SYMBOL_ID || timeline.getFrameCount() != 1) {
            throw new IllegalStateException("Missing original battle parent placement");
        }
        return timeline.getFrame(0);
    }
}
