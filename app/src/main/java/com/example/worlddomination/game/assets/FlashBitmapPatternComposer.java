package com.example.worlddomination.game.assets;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Base64;

import com.badlogic.gdx.graphics.Pixmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reconstructs the two bitmap-filled rectangles in the authored endgame art. */
final class FlashBitmapPatternComposer {
    static final class Part {
        final float x, y, width, height;
        final String base64;
        Part(float x, float y, float width, float height, String base64) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            this.base64 = base64;
        }
    }
    static final class Plan {
        final int width, height;
        final float originX, originY;
        final List<Part> parts;
        Plan(int width, int height, float originX, float originY, List<Part> parts) {
            this.width = width; this.height = height;
            this.originX = originX; this.originY = originY;
            this.parts = Collections.unmodifiableList(parts);
        }
    }

    private static final Pattern SVG = Pattern.compile("<svg\\b([^>]*)>");
    private static final Pattern BLOCK = Pattern.compile("(?s)<pattern\\b([^>]*)>(.*?)</pattern>");
    private static final Pattern IMAGE = Pattern.compile(
            "xlink:href=\"data:image/webp;base64,([^\"]+)\"");
    private static final Pattern MATRIX = Pattern.compile("matrix\\(([^)]+)\\)");

    static Plan parse(String svg) {
        Matcher header = SVG.matcher(svg);
        if (!header.find()) throw new IllegalArgumentException("Missing SVG root");
        float width = attribute(header.group(1), "width");
        float height = attribute(header.group(1), "height");
        float[] origin = new float[2];
        OriginalFlashAnimationAssets.parseRegistrationOrigin(svg, origin);
        List<Part> parts = new ArrayList<>();
        Matcher block = BLOCK.matcher(svg);
        while (block.find()) {
            String attrs = block.group(1);
            Matcher matrix = MATRIX.matcher(stringAttribute(attrs, "patternTransform"));
            Matcher image = IMAGE.matcher(block.group(2));
            if (!matrix.find() || !image.find()) continue;
            String[] terms = matrix.group(1).trim().split("[,\\s]+");
            if (terms.length != 6 || Float.parseFloat(terms[0]) != 1f
                    || Float.parseFloat(terms[1]) != 0f || Float.parseFloat(terms[2]) != 0f
                    || Float.parseFloat(terms[3]) != 1f)
                throw new IllegalArgumentException("Unexpected endgame bitmap transform");
            // Pixmap coordinates include the exporter's negative outer group.
            // The renderer reapplies the positive registration origin once.
            parts.add(new Part(Float.parseFloat(terms[4]) - origin[0],
                    Float.parseFloat(terms[5]) - origin[1],
                    attribute(attrs, "width"), attribute(attrs, "height"), image.group(1)));
        }
        if (parts.size() != 2) throw new IllegalArgumentException(
                "Expected two original endgame bitmap patterns, found " + parts.size());
        return new Plan((int) Math.ceil(width), (int) Math.ceil(height),
                origin[0], origin[1], parts);
    }

    static Pixmap decode(int shapeId, float[] origin) {
        String svg = OriginalFlashAnimationAssets.readCompressedUtf8(
                "original/runtime/shapes/" + shapeId + ".svgz");
        Plan plan = parse(svg);
        origin[0] = plan.originX;
        origin[1] = plan.originY;
        Bitmap canvasBitmap = Bitmap.createBitmap(plan.width, plan.height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(canvasBitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            for (Part part : plan.parts) {
                byte[] encoded = Base64.decode(part.base64, Base64.NO_WRAP);
                Bitmap image = BitmapFactory.decodeByteArray(encoded, 0, encoded.length);
                if (image == null) throw new IllegalStateException(
                        "Could not decode endgame bitmap pattern in shape " + shapeId);
                try {
                    canvas.drawBitmap(image, null, new RectF(part.x, part.y,
                            part.x + part.width, part.y + part.height), paint);
                } finally { image.recycle(); }
            }
            return OriginalFlashAnimationAssets.copyBitmapToPixmap(canvasBitmap);
        } finally { canvasBitmap.recycle(); }
    }

    private static float attribute(String attrs, String name) {
        return Float.parseFloat(stringAttribute(attrs, name).replace("px", ""));
    }
    private static String stringAttribute(String attrs, String name) {
        Matcher match = Pattern.compile("(?:^|\\s)" + Pattern.quote(name)
                + "=\"([^\"]+)\"").matcher(attrs);
        if (!match.find()) throw new IllegalArgumentException("Missing SVG " + name);
        return match.group(1);
    }

    private FlashBitmapPatternComposer() { }
}
