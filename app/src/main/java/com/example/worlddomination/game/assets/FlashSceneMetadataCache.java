package com.example.worlddomination.game.assets;

import com.badlogic.gdx.utils.JsonValue;

import java.lang.ref.SoftReference;
import java.util.Objects;
import java.util.function.Supplier;

/** Shares parsed, read-only scene metadata while asset loaders still hold it. */
final class FlashSceneMetadataCache {
    private SoftReference<JsonValue> cached = new SoftReference<>(null);

    synchronized JsonValue get(Supplier<JsonValue> parser) {
        JsonValue scene = cached.get();
        if (scene == null) {
            scene = Objects.requireNonNull(parser.get(), "Parsed Flash scene is missing");
            cached = new SoftReference<>(scene);
        }
        return scene;
    }
}
