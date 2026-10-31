package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonValue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Reconstructed Flash symbol display-list timeline in source stage coordinates. */
public final class FlashAnimationTimeline {
    /** One Flash display-list entry, including its shape or nested symbol ID. */
    public static final class Layer {
        public final int shapeId;
        public final float a;
        public final float b;
        public final float c;
        public final float d;
        public final float tx;
        public final float ty;
        public final String name;
        public final float alpha;

        private Layer(DisplayObject object) {
            shapeId = object.shapeId;
            a = object.a;
            b = object.b;
            c = object.c;
            d = object.d;
            tx = object.tx;
            ty = object.ty;
            name = object.name;
            alpha = object.alpha;
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
        String name = "";
        float alpha = 1f;
    }

    public final int symbolId;
    public final String linkage;
    public final float frameRate;
    private final Frame[] frames;
    private final Map<String, Integer> labels;

    private FlashAnimationTimeline(int symbolId, String linkage, float frameRate,
                                   Frame[] frames, Map<String, Integer> labels) {
        this.symbolId = symbolId;
        this.linkage = linkage;
        this.frameRate = frameRate;
        this.frames = frames;
        this.labels = Collections.unmodifiableMap(labels);
    }

    /** Applies Flash display-list deltas and captures ordered shape/symbol entries. */
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
                object.name = values.getString("name", object.name);
                JsonValue color = values.get("color");
                if (color != null) {
                    JsonValue multipliers = color.get("mult");
                    JsonValue additions = color.get("add");
                    float alphaMultiplier = getArrayFloat(multipliers, 3, 1f);
                    float alphaAddition = getArrayFloat(additions, 3, 0f);
                    object.alpha = Math.max(0f, Math.min(1f,
                            alphaMultiplier + alphaAddition));
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

        Map<String, Integer> labels = new LinkedHashMap<>();
        JsonValue sourceLabels = timeline.get("labels");
        if (sourceLabels != null && sourceLabels.isObject()) {
            for (JsonValue label = sourceLabels.child; label != null; label = label.next) {
                labels.put(label.name, label.asInt());
            }
        }
        return new FlashAnimationTimeline(symbol.getInt("id"),
                symbol.getString("linkage"), frameRate, frames, labels);
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

    /** Returns the source frame named by a Flash frame label, or null when absent. */
    public Frame getFrameByLabel(String label) {
        Integer oneBasedFrame = labels.get(label);
        return oneBasedFrame == null ? null : getFrame(oneBasedFrame - 1);
    }

    /** Returns a one-based Flash frame number, or -1 when the label is not present. */
    public int getFrameNumberForLabel(String label) {
        Integer oneBasedFrame = labels.get(label);
        return oneBasedFrame == null ? -1 : oneBasedFrame;
    }

    public float getDurationSeconds() {
        return frames.length / frameRate;
    }

    private static float getFloat(JsonValue value, String name, float fallback) {
        JsonValue field = value.get(name);
        return field == null ? fallback : field.asFloat();
    }

    private static float getArrayFloat(JsonValue values, int index, float fallback) {
        return values != null && values.isArray() && values.size > index
                ? values.get(index).asFloat() : fallback;
    }
}
