package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cached 3x captures of the original phone help crop and its source controls. */
public final class SourceHelpRenderer implements Disposable {
    private static final String ROOT = "original/ui/help/";
    private static final float WIDTH = 365f, HEIGHT = 275f;
    private static final int CACHE_LIMIT = 3;
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final LinkedHashMap<String, Texture> cache = new LinkedHashMap<>(4, .75f, true);
    private Page current;
    private Texture texture;
    private final Rectangle fitted = new Rectangle();

    private static final class Control {
        final String name;
        final float x, y, width, height;
        Control(JsonValue json) {
            name = json.getString("name");
            JsonValue bounds = json.get("bounds");
            x = bounds.getFloat(0); y = bounds.getFloat(1);
            width = bounds.getFloat(2); height = bounds.getFloat(3);
        }
    }
    private static final class Page {
        final String topic, asset;
        final Control[] controls;
        Page(JsonValue json) {
            topic = json.getString("topic"); asset = json.getString("asset");
            JsonValue entries = json.get("controls");
            controls = new Control[entries.size];
            int i = 0;
            for (JsonValue entry = entries.child; entry != null; entry = entry.next)
                controls[i++] = new Control(entry);
        }
    }

    public SourceHelpRenderer() {
        JsonValue entries = new JsonReader().parse(Gdx.files.internal(ROOT + "manifest.json")).get("pages");
        for (JsonValue entry = entries.child; entry != null; entry = entry.next) {
            Page page = new Page(entry); pages.put(page.topic, page);
        }
    }

    /** Load on a topic change, outside a begun batch. False leaves the displayed page intact. */
    public boolean prepare(String topic) {
        Page page = pages.get(topic);
        if (page == null) return false;
        Texture found = cache.get(topic);
        if (found == null) {
            found = new Texture(Gdx.files.internal(ROOT + page.asset));
            found.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            cache.put(topic, found);
            if (cache.size() > CACHE_LIMIT) {
                Map.Entry<String, Texture> oldest = cache.entrySet().iterator().next();
                cache.remove(oldest.getKey());
                oldest.getValue().dispose();
            }
        }
        current = page; texture = found;
        return true;
    }

    /** Draws uniformly fitted source artwork without mutating batch color or transform. */
    public void draw(Batch batch, Rectangle container) {
        if (texture == null) return;
        fit(container, fitted);
        batch.draw(texture, fitted.x, fitted.y, fitted.width, fitted.height);
    }

    public static Rectangle fit(Rectangle container, Rectangle output) {
        float scale = Math.min(container.width / WIDTH, container.height / HEIGHT);
        float width = WIDTH * scale, height = HEIGHT * scale;
        return output.set(container.x + (container.width-width)/2f,
                container.y + (container.height-height)/2f, width, height);
    }

    /** Return the original control name, or null outside enabled controls. */
    public String controlAt(Rectangle container, float x, float y) {
        if (current == null || container.width <= 0 || container.height <= 0) return null;
        fit(container, fitted);
        if (!fitted.contains(x, y)) return null;
        float scale = fitted.width / WIDTH;
        float sx = (x-fitted.x)/scale, sy = HEIGHT-(y-fitted.y)/scale;
        for (Control control : current.controls) {
            if (sx >= control.x && sx <= control.x+control.width
                    && sy >= control.y && sy <= control.y+control.height) return control.name;
        }
        return null;
    }

    @Override public void dispose() {
        for (Texture value : cache.values()) value.dispose();
        cache.clear(); texture = null; current = null;
    }
}
