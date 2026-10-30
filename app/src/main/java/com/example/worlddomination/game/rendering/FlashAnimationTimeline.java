package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonValue;

import java.util.HashMap;
import java.util.Map;

/** Reconstructed single-layer Flash symbol timeline, in source stage coordinates. */
public final class FlashAnimationTimeline {
    public static final class Frame {
        public final int shapeId;
        public final float a;
        public final float b;
        public final float c;
        public final float d;
        public final float tx;
        public final float ty;

        private Frame(DisplayObject object) {
            shapeId = object.shapeId;
            a = object.a;
            b = object.b;
            c = object.c;
            d = object.d;
            tx = object.tx;
            ty = object.ty;
        }
    }

    private static final class DisplayObject {
        int shapeId = -1;
        float a = 1f;
        float b;
        float c;
        float d = 1f;
        float tx;
        float ty;
    }

    public final int symbolId;
    public final String linkage;
    public final float frameRate;
    private final Frame[] frames;

    private FlashAnimationTimeline(int symbolId, String linkage, float frameRate,
                                   Frame[] frames) {
        this.symbolId = symbolId;
        this.linkage = linkage;
        this.frameRate = frameRate;
        this.frames = frames;
    }

    /** Applies Flash's display-list deltas and captures one renderable shape per frame. */
    public static FlashAnimationTimeline fromSymbol(JsonValue symbol, float frameRate) {
        if (symbol == null || frameRate <= 0f) {
            throw new IllegalArgumentException("A symbol and positive frame rate are required");
        }
        JsonValue timeline = symbol.get("timeline");
        JsonValue sourceFrames = timeline == null ? null : timeline.get("frames");
        if (sourceFrames == null || !sourceFrames.isArray()) {
            throw new IllegalArgumentException("Flash symbol has no frame array");
        }

        Frame[] frames = new Frame[sourceFrames.size];
        Map<Integer, DisplayObject> displayList = new HashMap<>();
        for (int frameIndex = 0; frameIndex < sourceFrames.size; frameIndex++) {
            JsonValue operations = sourceFrames.get(frameIndex);
            for (JsonValue operation = operations.child;
                 operation != null; operation = operation.next) {
                int depth = operation.getInt("depth");
                JsonValue remove = operation.get("remove");
                if (remove != null && remove.asBoolean()) {
                    displayList.remove(depth);
                    continue;
                }

                JsonValue values = operation.get("values");
                if (values == null) {
                    continue;
                }
                DisplayObject object = displayList.get(depth);
                if (object == null) {
                    object = new DisplayObject();
                    displayList.put(depth, object);
                }
                JsonValue shapeId = values.get("character_id");
                if (shapeId != null) {
                    object.shapeId = shapeId.asInt();
                }
                JsonValue matrix = values.get("matrix");
                if (matrix != null) {
                    object.a = getFloat(matrix, "a", object.a);
                    object.b = getFloat(matrix, "b", object.b);
                    object.c = getFloat(matrix, "c", object.c);
                    object.d = getFloat(matrix, "d", object.d);
                    object.tx = getFloat(matrix, "tx", object.tx);
                    object.ty = getFloat(matrix, "ty", object.ty);
                }
            }

            if (displayList.size() > 1) {
                throw new IllegalArgumentException("Flash effect "
                        + symbol.getString("linkage")
                        + " contains multiple display layers");
            }
            DisplayObject visible = displayList.get(1);
            if (visible != null && visible.shapeId > 0) {
                frames[frameIndex] = new Frame(visible);
            }
        }

        return new FlashAnimationTimeline(symbol.getInt("id"),
                symbol.getString("linkage"), frameRate, frames);
    }

    public int getFrameCount() {
        return frames.length;
    }

    public Frame getFrame(int frameIndex) {
        if (frameIndex < 0 || frameIndex >= frames.length) {
            return null;
        }
        return frames[frameIndex];
    }

    public float getDurationSeconds() {
        return frames.length / frameRate;
    }

    private static float getFloat(JsonValue value, String name, float fallback) {
        JsonValue field = value.get(name);
        return field == null ? fallback : field.asFloat();
    }
}
