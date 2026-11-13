package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CitySpriteHitGeometryTest {
    private static final float EPSILON = 0.001f;

    @Test
    public void christianFrameSixRoofAndBaseUseSourceAtlasBounds() {
        // assets/cities/manifest.json: 1363_6.png, offset [-19,-96], crop 76x113, scale .8.
        Rectangle map = new Rectangle(0f, 0f, 523f, 318f);
        Rectangle[] bounds = emptyBounds();
        boolean[] visible = new boolean[25];
        CitySpriteHitGeometry.setBounds(bounds[0], map, 0, 0,
                -19f, -96f, 76f, 113f, .8f);
        visible[0] = true;
        assertEquals(219.3385f, bounds[0].x, EPSILON);
        assertEquals(89.941f, bounds[0].y, EPSILON);
        assertEquals(32.224f, bounds[0].width, EPSILON);
        assertEquals(47.912f, bounds[0].height, EPSILON);
        assertEquals(0, hit(227.3945f, 132.853f, map, bounds, visible)); // roof
        assertEquals(0, hit(227.3945f, 92f, map, bounds, visible)); // base
        assertEquals(-1, hit(227.3945f, 138f, map, bounds, visible));
        assertEquals(-1, hit(252f, 120f, map, bounds, visible));
    }

    @Test
    public void changesWithSourceThemeFrameAndUniformAspect() {
        Rectangle map = new Rectangle(0f, 0f, 523f, 318f);
        Rectangle[] bounds = emptyBounds();
        boolean[] visible = new boolean[25];
        visible[0] = true;
        // Christian frame 1: offset [-14,-20], crop 36x32.
        CitySpriteHitGeometry.setBounds(bounds[0], map, 0, 0,
                -14f, -20f, 36f, 32f, .8f);
        assertEquals(-1, hit(227.3945f, 132.853f, map, bounds, visible));
        // Muslim frame 6: offset [-27,-80.5], crop 84x91.
        CitySpriteHitGeometry.setBounds(bounds[0], map, 0, 0,
                -27f, -80.5f, 84f, 91f, .8f);
        assertEquals(0, hit(227.3945f, 125f, map, bounds, visible));
        Rectangle wide = new Rectangle(0f, 0f, 1200f, 552f);
        CitySpriteHitGeometry.setBounds(bounds[0], wide, 0, 0,
                -19f, -96f, 76f, 113f, .8f);
        float roofX = BattlefieldCityLayout.x(wide, 0, 0);
        float roofY = bounds[0].y + bounds[0].height - 5f;
        assertEquals(0, hit(roofX, roofY, wide, bounds, visible));
        assertEquals(-1, hit(-1f, roofY, wide, bounds, visible));
    }

    @Test
    public void appliesMapClipAndReverseSourceDepthOnSameIsland() {
        Rectangle map = new Rectangle(0f, 0f, 523f, 318f);
        Rectangle[] bounds = emptyBounds();
        boolean[] visible = new boolean[25];
        float x = BattlefieldCityLayout.x(map, 0, 0);
        float y = BattlefieldCityLayout.y(map, 0, 0);
        bounds[0].set(x - 20f, y - 20f, 40f, 40f);
        bounds[1].set(bounds[0]);
        visible[0] = visible[1] = true;
        int[] order = BattlefieldCityLayout.cityRenderOrder();
        int top = later(order, 0, 1);
        assertEquals(top, hit(x, y, map, bounds, visible));
        assertEquals(-1, hit(-1f, y, map, bounds, visible));
        visible[top] = false;
        assertEquals(top == 0 ? 1 : 0, hit(x, y, map, bounds, visible));
    }

    @Test
    public void overlappingIslandsChooseInverseCropBeforeDepthDispatch() {
        Rectangle map = new Rectangle(0f, 0f, 1000f, 600f);
        Rectangle[] bounds = emptyBounds();
        boolean[] visible = new boolean[25];
        // The two displayed rectangles overlap, but the touch is closer to player 1.
        bounds[5].set(300f, 200f, 80f, 80f);
        bounds[10].set(300f, 200f, 80f, 80f);
        visible[5] = visible[10] = true;
        // Force a common point nearest player 1's anchor by moving rectangles to it.
        float x = BattlefieldCityLayout.x(map, 1, 0);
        float y = BattlefieldCityLayout.y(map, 1, 0);
        bounds[5].set(x - 10f, y - 10f, 20f, 20f);
        bounds[10].set(bounds[5]);
        assertEquals(5, hit(x, y, map, bounds, visible));
    }

    @Test
    public void phoneSelectionExcludesFarEdgeButFinalControlIncludesIt() {
        Rectangle map = new Rectangle(0f, 0f, 1000f, 600f);
        Rectangle[] bounds = emptyBounds();
        boolean[] visible = new boolean[25];
        // A deliberately displaced city rectangle shows the inverse island choice.
        bounds[5].set(755f, 445f, 20f, 20f);
        visible[5] = true;
        assertEquals(1, CitySpriteHitGeometry.islandForTouch(
                774f, 455f, map, bounds, visible));
        assertEquals(2, CitySpriteHitGeometry.islandForTouch(
                775f, 455f, map, bounds, visible));
        assertEquals(2, CitySpriteHitGeometry.islandForTouch(
                765f, 445f, map, bounds, visible));

        Rectangle fitted = new Rectangle(0f, 0f, 523f, 318f);
        bounds[0] = new Rectangle();
        CitySpriteHitGeometry.setBounds(bounds[0], fitted, 0, 0,
                -19f, -96f, 76f, 113f, .8f);
        visible[5] = false;
        visible[0] = true;
        assertEquals(0, hit(bounds[0].x + bounds[0].width, 110f,
                fitted, bounds, visible));
        assertEquals(0, hit(227.3945f, bounds[0].y,
                fitted, bounds, visible));
    }

    @Test
    public void mapClipExcludesPhoneRightAndBottomEvenWhenCityExtendsPastIt() {
        Rectangle map = new Rectangle(0f, 0f, 523f, 318f);
        Rectangle[] bounds = emptyBounds();
        boolean[] visible = new boolean[25];
        bounds[0].set(-10f, -10f, 550f, 340f);
        visible[0] = true;
        assertEquals(-1, hit(523f, 100f, map, bounds, visible));
        assertEquals(-1, hit(100f, 0f, map, bounds, visible));
        assertEquals(0, hit(0f, 100f, map, bounds, visible));
        assertEquals(0, hit(100f, 318f, map, bounds, visible));
    }

    private static int hit(float x, float y, Rectangle map, Rectangle[] bounds,
                           boolean[] visible) {
        return CitySpriteHitGeometry.hitTest(x, y, map, bounds, visible,
                BattlefieldCityLayout.cityRenderOrder());
    }

    private static Rectangle[] emptyBounds() {
        Rectangle[] bounds = new Rectangle[25];
        for (int address = 0; address < bounds.length; address++) bounds[address] = new Rectangle();
        return bounds;
    }

    private static int later(int[] order, int left, int right) {
        for (int address : order) {
            if (address == left) left = -1;
            if (address == right) return left == -1 ? right : left;
        }
        throw new AssertionError();
    }
}
