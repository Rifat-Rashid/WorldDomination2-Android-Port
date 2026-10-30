package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonValue;

import java.util.Map;
import java.util.TreeMap;

/** Reconstructed Flash symbol display-list timeline in source stage coordinates. */
public final class FlashAnimationTimeline {
    /** One drawable Flash display-list entry at a particular depth. */
    public static final class Layer {
        public final int shapeId;
        public final float a;
        public final float b;
        public final float c;
        public final float d;
        public final float tx;
        public final float ty;

        private Layer(DisplayObject object) {
            shapeId = object.shapeId;
            a = object.a;
            b = object.b;
            c = object.c;
            d = object.d;
            tx = object.tx;
            ty = object.ty;
        }
    }

    public static final class Frame {
        /** Layers in Flash display-list depth order, lowest depth first. */
        public final Layer[] layers;
        public final int shapeId;
        public final float a;
        public final float b;
        public final float c;
        public final float d;
        public final float tx;
        public final float ty;

        private Frame(Layer[] layers) {
            this.layers = layers;
            Layer primary = layers[0];
            shapeId = primary.shapeId;
            a = primary.a;
            b = primary.b;
            c = primary.c;
            d = primary.d;
            tx = primary.tx;
            ty = primary.ty;
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
        Map<Integer, DisplayObject> displayList = new TreeMap<>();
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

            Layer[] layers = new Layer[displayList.size()];
            int layerIndex = 0;
            for (DisplayObject object : displayList.values()) {
                if (object.shapeId > 0) {
                    layers[layerIndex++] = new Layer(object);
                }
            }
            if (layerIndex > 0) {
                if (layerIndex != layers.length) {
                    Layer[] visibleLayers = new Layer[layerIndex];
                    System.arraycopy(layers, 0, visibleLayers, 0, layerIndex);
                    layers = visibleLayers;
                }
                frames[frameIndex] = new Frame(layers);
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
