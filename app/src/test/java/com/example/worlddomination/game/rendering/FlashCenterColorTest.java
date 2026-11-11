package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import static org.junit.Assert.*;

/** Recovered Flash CXFORM state and the authored research list's white bars. */
public final class FlashCenterColorTest {
    @Test public void sourceAdditionsUseByteUnitsWithoutPrematureClamping() {
        FlashColorTransform color = FlashColorTransform.source(.5f, .25f, 2, .75f,
                255, 128, -64, 32);
        assertEquals(.5f, color.redMultiplier, 0);
        assertEquals(2, color.blueMultiplier, 0);
        assertEquals(1, color.redAdd, 0);
        assertEquals(128f / 255, color.greenAdd, 0);
        assertEquals(-64f / 255, color.blueAdd, 0);
        assertEquals(32f / 255, color.alphaAdd, 0);
    }

    @Test public void parentTransformRunsAfterChildIncludingAdditionsAndAlpha() {
        FlashColorTransform.Composition root = new FlashColorTransform.Composition();
        FlashColorTransform.Composition parent = new FlashColorTransform.Composition().setProduct(root,
                FlashColorTransform.source(.5f, .25f, .75f, .5f, 64, 32, 16, 8));
        FlashColorTransform.Composition child = new FlashColorTransform.Composition().setProduct(parent,
                FlashColorTransform.source(.2f, .4f, .6f, .8f, 128, 64, 32, 16));
        assertEquals(.1f, child.redMultiplier, .00001f);
        assertEquals(.1f, child.greenMultiplier, .00001f);
        assertEquals(.45f, child.blueMultiplier, .00001f);
        assertEquals(.4f, child.alphaMultiplier, .00001f);
        assertEquals(128f / 255, child.redAdd, .00001f);
        assertEquals(48f / 255, child.greenAdd, .00001f);
        assertEquals(40f / 255, child.blueAdd, .00001f);
        assertEquals(16f / 255, child.alphaAdd, .00001f);
        assertFalse(child.isIdentity());
        child.setProduct(root, FlashColorTransform.IDENTITY);
        assertTrue("Sibling transforms cannot inherit prior color", child.isIdentity());
    }

    @Test public void moveRetainsColorWhileFreshPlacementResetsIt() {
        JsonValue symbol = new JsonReader().parse("{id:7,linkage:fixture,timeline:{frames:["
                + "[{depth:1,values:{character_id:41,color:{mult:[0,0,0,1],add:[255,255,255,0]}}}],"
                + "[{depth:1,move:true,values:{matrix:{tx:9}}}],"
                + "[{depth:1,move:false,values:{character_id:42}}]]}}");
        FlashAnimationTimeline timeline = FlashAnimationTimeline.fromSymbol(symbol, 24);
        FlashColorTransform first = timeline.getFrame(0).layers[0].colorTransform;
        assertEquals(0, first.redMultiplier, 0); assertEquals(1, first.redAdd, 0);
        assertSame(first, timeline.getFrame(1).layers[0].colorTransform);
        assertSame(FlashColorTransform.IDENTITY, timeline.getFrame(2).layers[0].colorTransform);
        assertEquals("Earlier frame snapshot is immutable", 1, first.redAdd, 0);
    }

    @Test public void sourceResearchSelectedListBarsAreWhiteRatherThanOrange() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(
                Paths.get("src/main/assets/original/runtime/scene.json.gz"))), StandardCharsets.UTF_8)) {
            JsonValue scene = new JsonReader().parse(reader);
            JsonValue center = null;
            for (JsonValue symbol : scene.get("symbols"))
                if (symbol.getInt("id") == 4444) center = symbol;
            assertNotNull(center);
            FlashAnimationTimeline timeline = FlashAnimationTimeline.fromSymbol(center, 24);
            int bars = 0;
            for (FlashAnimationTimeline.Layer layer : timeline.getFrameByLabel("research").layers) {
                if (layer.shapeId != 3261) continue;
                assertEquals(0, layer.colorTransform.redMultiplier, 0);
                assertEquals(0, layer.colorTransform.greenMultiplier, 0);
                assertEquals(0, layer.colorTransform.blueMultiplier, 0);
                assertEquals(1, layer.colorTransform.redAdd, 0);
                assertEquals(1, layer.colorTransform.greenAdd, 0);
                assertEquals(1, layer.colorTransform.blueAdd, 0);
                assertEquals(1, layer.colorTransform.alphaMultiplier, 0);
                bars++;
            }
            assertEquals(2, bars);
            JsonValue field = scene.get("text_fields").get("3328");
            assertEquals(1, field.getInt("align"));
            assertEquals(0, field.get("color").getInt(0));
            assertEquals(36, field.get("color").getInt(1));
            assertEquals(60, field.get("color").getInt(2));
            assertEquals(351.55f, com.example.worlddomination.game.ui.TechnologyScreenLayout.ESTIMATED_TOTAL_RIGHT, .001f);
            assertEquals(351.05f, com.example.worlddomination.game.ui.TechnologyScreenLayout.SELECTED_COSTS_RIGHT, .001f);
        }
    }
}
