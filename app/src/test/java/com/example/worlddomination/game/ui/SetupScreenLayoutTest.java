package com.example.worlddomination.game.ui;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SetupScreenLayoutTest {
    @Test public void wideCutoutKeepsEverySetupControlInsideTheSafeArea() {
        assertLayout(896, 672, 224, 36, 48, 24);
        assertLayout(1194, 552, 280, 12, 24, 8);
    }

    @Test
    public void removalArtworkKeepsSourceAspectRatioInsideEveryOccupiedCard() {
        for (float[] size : new float[][]{{800, 600}, {1194, 552}, {2424, 1080}}) {
            SetupScreenLayout layout = new SetupScreenLayout();
            layout.update(size[0], size[1], 0, 0, 0, 0);
            for (int i = 0; i < layout.opponentSlots.length; i++) {
                Rectangle card = layout.opponentSlots[i], icon = layout.opponentRemoveIcons[i];
                assertTrue(card.contains(icon));
                assertTrue(icon.x > card.x + card.width * .6f);
                assertTrue(icon.y > card.y + card.height * .6f);
                assertEquals(17.3f / 17.1f, icon.width / icon.height, .0001f);
            }
        }
    }

    @Test
    public void selectedEnemyLeavesShareTheSourceTransform() {
        float[] portraitX = {-2.3f, -.75f, -2.3f, -1.85f};
        float[] portraitY = {-3f, -3f, -3.5f, -3.5f};
        float[] cancelX = {48.25f, 49.25f, 47.25f, 48.25f};
        for (float[] size : new float[][]{{800, 600}, {1194, 552}}) {
            SetupScreenLayout layout = new SetupScreenLayout();
            layout.update(size[0], size[1], 0, 0, 0, 0);
            for (int i = 0; i < 4; i++) {
                Rectangle w = layout.opponentWells[i], p = layout.opponentPortraits[i];
                Rectangle x = layout.opponentRemoveIcons[i];
                float scale = w.width / 64f;
                assertEquals(70f * scale, w.height, .0001f);
                assertEquals(portraitX[i], (p.x - w.x) / scale, .001f);
                assertEquals(portraitY[i], (w.y + w.height - p.y - p.height) / scale, .001f);
                assertEquals(70f * scale, p.width, .0001f);
                assertEquals(75f * scale, p.height, .0001f);
                assertEquals(cancelX[i], (x.x - w.x) / scale, .001f);
                assertEquals(.35f, (w.y + w.height - x.y - x.height) / scale, .001f);
                assertTrue(layout.opponentSlots[i].contains(x));
            }
            assertEquals(100f / 53f, layout.opponentArrow.width / layout.opponentArrow.height, .0001f);
        }
    }

    @Test
    public void selectedEnemyRegistrationIsAnchoredInPackagedScene() throws Exception {
        Path base=Paths.get("src/main/assets/original/runtime");
        JsonValue root;
        try (GZIPInputStream in=new GZIPInputStream(Files.newInputStream(base.resolve("scene.json.gz")))) {
            root=new JsonReader().parse(new InputStreamReader(in,StandardCharsets.UTF_8));
        }
        JsonValue gameMenu=named(symbol(root,0),"gameMenu");
        assertEquals(5882,gameMenu.getInt("character_id"));
        assertMatrix(gameMenu.get("matrix"),-112f,-29f);
        JsonValue setup=symbol(root,5882);
        JsonValue alive=named(symbol(root,3205),"aliveMC");
        assertEquals(3191,alive.getInt("character_id"));
        assertMatrix(alive.get("matrix"),-34f,-38f);
        JsonValue portraitFrames=symbol(root,3191).get("timeline").get("frames");
        JsonValue first=portraitFrames.get(0).get(0).get("values");
        assertEquals(3150,first.getInt("character_id")); assertMatrix(first.get("matrix"),0f,0f);
        assertTrue(portraitFrames.get(19).get(0).getBoolean("remove"));
        JsonValue cancelFrames=symbol(root,5826).get("timeline").get("frames");
        assertEquals(0,cancelFrames.get(0).size);
        JsonValue cancelLeaf=cancelFrames.get(1).get(0).get("values");
        assertEquals(5825,cancelLeaf.getInt("character_id"));
        assertMatrix(cancelLeaf.get("matrix"),0f,0f);
        String portraitSvg=readSvg(base,3150), cancelSvg=readSvg(base,5825);
        assertTrue(portraitSvg.contains("width=\"70.0px\""));
        assertTrue(portraitSvg.contains("height=\"75.0px\""));
        assertTrue(cancelSvg.contains("width=\"17.3px\""));
        assertTrue(cancelSvg.contains("height=\"17.1px\""));
        assertTrue(cancelSvg.contains("-3.3, -0.1)"));
        float[] wells={320,394,468,542};
        SetupScreenLayout layout=new SetupScreenLayout();
        layout.update(1194,552,0,0,0,0);
        for (int i=0;i<4;i++) {
            JsonValue leader=named(setup,"leaderScrn"+(i+1));
            JsonValue cancel=named(setup,"btnCancel"+(i+1));
            assertEquals(3205,leader.getInt("character_id"));
            assertEquals(5826,cancel.getInt("character_id"));
            JsonValue lm=leader.get("matrix"), cm=cancel.get("matrix");
            assertMatrix(lm,lm.getFloat("tx"),lm.getFloat("ty"));
            assertMatrix(cm,cm.getFloat("tx"),cm.getFloat("ty"));
            JsonValue mult=cancel.get("color").get("mult");
            for (int c=0;c<3;c++) assertEquals(1f,mult.get(c).asFloat(),0f);
            assertEquals((i==0?166f:143f)/256f,mult.get(3).asFloat(),0f);
            JsonValue add=cancel.get("color").get("add");
            for (int c=0;c<4;c++) assertEquals(0,add.get(c).asInt());
            Rectangle well=layout.opponentWells[i], portrait=layout.opponentPortraits[i], icon=layout.opponentRemoveIcons[i];
            float scale=well.width/64f;
            float sourcePortraitX=lm.getFloat("tx")-112f-34f-wells[i];
            float sourcePortraitY=lm.getFloat("ty")-29f-38f-226f;
            assertEquals(sourcePortraitX,(portrait.x-well.x)/scale,.001f);
            assertEquals(sourcePortraitY,(well.y+well.height-portrait.y-portrait.height)/scale,.001f);
            float sourceCancelX=cm.getFloat("tx")-112f+3.3f-wells[i];
            float sourceCancelY=cm.getFloat("ty")-29f+.1f-226f;
            assertEquals(sourceCancelX,(icon.x-well.x)/scale,.001f);
            assertEquals(sourceCancelY,(well.y+well.height-icon.y-icon.height)/scale,.001f);
        }
    }

    private static JsonValue symbol(JsonValue root,int id) {
        for (JsonValue item:root.get("symbols")) if(item.getInt("id")==id) return item;
        throw new AssertionError("Missing scene symbol "+id);
    }
    private static JsonValue named(JsonValue symbol,String name) {
        for(JsonValue frame:symbol.get("timeline").get("frames")) for(JsonValue op:frame) {
            JsonValue value=op.get("values");
            if(name.equals(value.getString("name",""))) return value;
        }
        throw new AssertionError("Missing scene child "+name);
    }
    private static void assertMatrix(JsonValue m,float x,float y) {
        assertEquals(1f,m.getFloat("a"),0f); assertEquals(0f,m.getFloat("b"),0f);
        assertEquals(0f,m.getFloat("c"),0f); assertEquals(1f,m.getFloat("d"),0f);
        assertEquals(x,m.getFloat("tx"),.0001f); assertEquals(y,m.getFloat("ty"),.0001f);
    }
    private static String readSvg(Path base,int shape) throws Exception {
        try(GZIPInputStream in=new GZIPInputStream(Files.newInputStream(base.resolve("shapes/"+shape+".svgz")))) {
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }

    @Test
    public void setupSectionsAndControlsFitCommonLandscapeSizesAndSafeInsets() {
        assertLayout(896f, 414f, 0f, 0f, 0f, 0f);
        assertLayout(1854f, 849f, 34f, 12f, 22f, 10f);
    }

    @Test
    public void setupSectionsKeepTheirOriginalVerticalHierarchy() {
        SetupScreenLayout layout = new SetupScreenLayout();
        layout.update(896f, 414f, 0f, 0f, 0f, 0f);

        assertTrue(layout.startPanel.y + layout.startPanel.height
                < layout.enemiesPanel.y);
        assertTrue(layout.enemiesPanel.y + layout.enemiesPanel.height
                < layout.playerStylePanel.y);
        assertTrue(layout.playerStylePanel.y + layout.playerStylePanel.height
                < layout.difficultyPanel.y);
        assertTrue(layout.difficultyPanel.x > layout.logo.x + layout.logo.width);
    }

    @Test
    public void rivalSlotsLeaveRoomForDirectionMarkerAndPortraitNameBar() {
        SetupScreenLayout layout = new SetupScreenLayout();
        layout.update(1854f, 849f, 34f, 12f, 22f, 10f);

        assertTrue("Rival cards sit to the right of the panel center",
                layout.opponentSlots[0].x > layout.enemiesPanel.x
                        + layout.enemiesPanel.width * 0.25f);
        assertTrue("Direction marker has its own clear hit-safe region",
                layout.opponentArrow.x + layout.opponentArrow.width
                        < layout.opponentSlots[0].x);
        assertTrue("Opponent cards sit above the portrait carousel",
                layout.opponentSlots[0].y
                        > layout.leaderCarouselPanel.y + layout.leaderCarouselPanel.height);
        assertTrue("Carousel leaves the original enemy panel footer visible",
                layout.leaderCarouselPanel.y > layout.enemiesPanel.y
                        + layout.enemiesPanel.height * 0.08f);
        assertTrue("The name bar remains within the carousel frame",
                layout.leaderDescription.x >= layout.leaderCarouselPanel.x
                        && layout.leaderDescription.x + layout.leaderDescription.width
                        <= layout.leaderCarouselPanel.x + layout.leaderCarouselPanel.width);
        assertTrue("Carousel arrows are vertically inset from portrait cards",
                layout.leaderPrevious.height < layout.leaderCards[0].height
                        && layout.leaderNext.height < layout.leaderCards[0].height);
        assertTrue("The name bar stays below the portrait cards",
                layout.leaderDescription.y + layout.leaderDescription.height
                        < layout.leaderCards[0].y);
    }

    @Test
    public void carouselPreservesAuthoredFooterWithoutCoveringCardsOrDescription() {
        for (float[] size : new float[][]{{800, 600}, {896, 414}, {1194, 552}, {2424, 1080}}) {
            SetupScreenLayout layout = new SetupScreenLayout();
            layout.update(size[0], size[1], 0, 0, 0, 0);
            Rectangle carousel = layout.leaderCarouselPanel;
            assertTrue(layout.enemiesPanel.contains(carousel));
            // Shape 5813 places its carousel about 28px above the 330px enemy
            // plate bottom. Keep that visible footer in the responsive layout.
            assertEquals(28f / 330f,
                    (carousel.y - layout.enemiesPanel.y) / layout.enemiesPanel.height, .001f);
            assertTrue(carousel.contains(layout.leaderDescription));
            assertTrue("Description must remain readable at the smallest phone size",
                    layout.leaderDescription.height >= 17f);
            assertTrue(layout.leaderDescription.y + layout.leaderDescription.height
                    < layout.leaderCards[0].y);
            for (Rectangle card : layout.leaderCards) assertTrue(carousel.contains(card));
            assertTrue(carousel.contains(layout.leaderPrevious));
            assertTrue(carousel.contains(layout.leaderNext));
        }
    }

    @Test
    public void carouselRetainsWideFootprintAndCardTargets() {
        for (float[] size : new float[][]{{800, 600}, {1194, 552}}) {
            SetupScreenLayout layout = new SetupScreenLayout();
            layout.update(size[0], size[1], 0, 0, 0, 0);
            Rectangle crop = layout.leaderCarouselPanel;
            assertEquals(layout.enemiesPanel.width * .86f, crop.width, .001f);
            float oldWidth = (layout.enemiesPanel.width * .86f
                    - 2f * layout.enemiesPanel.width * .018f
                    - 2f * layout.enemiesPanel.width * .028f
                    - 7f * layout.enemiesPanel.width * .012f) / 6f;
            for (Rectangle card : layout.leaderCards) {
                assertEquals(oldWidth, card.width, .001f);
                assertEquals(layout.enemiesPanel.height * .326f, card.height, .001f);
                assertTrue(crop.contains(card));
            }
            assertFalse(layout.leaderPrevious.overlaps(layout.leaderCards[0]));
            assertFalse(layout.leaderNext.overlaps(layout.leaderCards[5]));
            assertTrue(crop.contains(layout.leaderDescription));
        }
    }

    @Test
    public void landscapeCutoutDoesNotShiftTheMainSetupPanelsAcrossTheScreen() {
        SetupScreenLayout layout = new SetupScreenLayout();
        layout.update(930f, 414f, 54f, 0f, 0f, 0f);

        assertTrue("Logo stays clear of the left cutout", layout.logo.x >= 54f);
        assertTrue("Player panel keeps the reference left margin",
                layout.playerStylePanel.x < 930f * 0.15f);
        assertTrue("Difficulty panel remains beside the logo",
                layout.difficultyPanel.x < 930f * 0.20f);
    }

    @Test
    public void leaderCarouselWrapsAtTheLastSourcePortrait() {
        assertEquals(17, SetupScreenLayout.leaderIdAt(17, 0, 19));
        assertEquals(18, SetupScreenLayout.leaderIdAt(17, 1, 19));
        assertEquals(0, SetupScreenLayout.leaderIdAt(17, 2, 19));
        assertEquals(3, SetupScreenLayout.leaderIdAt(17, 5, 19));
    }

    @Test
    public void sourceRightArrowVisitsDuplicateFirstPageBeforeReset() {
        assertEquals(1, SetupScreenLayout.nextLeaderStart(0, 19));
        assertEquals(19, SetupScreenLayout.nextLeaderStart(18, 19));
        assertEquals(0, SetupScreenLayout.nextLeaderStart(19, 19));
        for (int card = 0; card < SetupScreenLayout.VISIBLE_LEADERS; card++) {
            assertEquals(card, SetupScreenLayout.leaderIdAt(0, card, 19));
            assertEquals(card, SetupScreenLayout.leaderIdAt(19, card, 19));
        }
    }

    @Test
    public void leaderCarouselRejectsInvalidVisibleIndicesAndEmptyRosters() {
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> SetupScreenLayout.leaderIdAt(0, -1, 19));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> SetupScreenLayout.leaderIdAt(0, SetupScreenLayout.VISIBLE_LEADERS, 19));
        org.junit.Assert.assertThrows(IllegalArgumentException.class,
                () -> SetupScreenLayout.leaderIdAt(0, 0, 0));
    }

    private static void assertLayout(float width, float height,
                                     float safeLeft, float safeTop,
                                     float safeRight, float safeBottom) {
        SetupScreenLayout layout = new SetupScreenLayout();
        layout.update(width, height, safeLeft, safeTop, safeRight, safeBottom);
        Rectangle[] sections = {
                layout.logo, layout.difficultyPanel, layout.playerStylePanel,
                layout.enemiesPanel, layout.leaderCarouselPanel, layout.startPanel
        };
        for (Rectangle section : sections) {
            assertInside(section, width, height, safeLeft, safeTop, safeRight, safeBottom);
        }
        for (Rectangle control : layout.difficultyButtons) {
            assertInside(control, width, height, safeLeft, safeTop, safeRight, safeBottom);
        }
        for (Rectangle control : layout.colorSwatches) {
            assertInside(control, width, height, safeLeft, safeTop, safeRight, safeBottom);
        }
        for (Rectangle control : layout.buildingButtons) {
            assertInside(control, width, height, safeLeft, safeTop, safeRight, safeBottom);
        }
        for (Rectangle control : layout.opponentSlots) {
            assertInside(control, width, height, safeLeft, safeTop, safeRight, safeBottom);
        }
        for (Rectangle control : layout.leaderCards) {
            assertInside(control, width, height, safeLeft, safeTop, safeRight, safeBottom);
        }
        assertInside(layout.leaderPrevious, width, height,
                safeLeft, safeTop, safeRight, safeBottom);
        assertInside(layout.leaderNext, width, height,
                safeLeft, safeTop, safeRight, safeBottom);
        assertInside(layout.leaderDescription, width, height,
                safeLeft, safeTop, safeRight, safeBottom);
        assertInside(layout.startButton, width, height,
                safeLeft, safeTop, safeRight, safeBottom);
        assertInside(layout.opponentArrow, width, height,
                safeLeft, safeTop, safeRight, safeBottom);

        for (int i = 0; i < layout.leaderCards.length - 1; i++) {
            assertTrue("Leader cards must not overlap", !layout.leaderCards[i]
                    .overlaps(layout.leaderCards[i + 1]));
        }
    }

    private static void assertInside(Rectangle rectangle, float width, float height,
                                     float safeLeft, float safeTop,
                                     float safeRight, float safeBottom) {
        assertTrue("Control width must be positive", rectangle.width > 0f);
        assertTrue("Control height must be positive", rectangle.height > 0f);
        assertTrue("Control crosses left safe area", rectangle.x >= safeLeft);
        assertTrue("Control crosses bottom safe area", rectangle.y >= safeBottom);
        assertTrue("Control crosses right safe area",
                rectangle.x + rectangle.width <= width - safeRight);
        assertTrue("Control crosses top safe area",
                rectangle.y + rectangle.height <= height - safeTop);
    }
}
