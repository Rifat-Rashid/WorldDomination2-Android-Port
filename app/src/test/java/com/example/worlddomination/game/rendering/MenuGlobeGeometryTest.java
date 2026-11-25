package com.example.worlddomination.game.rendering;

import org.junit.Test;

import static org.junit.Assert.*;

public class MenuGlobeGeometryTest {
    @Test public void visibleTrianglesKeepLongitudeContinuousAndCoverOneDisc() {
        MenuGlobeGeometry globe = new MenuGlobeGeometry();
        double area = 0;
        double haloArea = 0;
        for (int i = 0; i < globe.indices.length; i += 3) {
            int a = 5 * globe.indices[i], b = 5 * globe.indices[i + 1], c = 5 * globe.indices[i + 2];
            float[] v = globe.vertices;
            float cross = (v[b] - v[a]) * (v[c + 1] - v[a + 1])
                    - (v[c] - v[a]) * (v[b + 1] - v[a + 1]);
            if (v[a + 2] < -1) {
                assertTrue(v[b + 2] < -1 && v[c + 2] < -1);
                haloArea += Math.abs(cross) * .5;
            } else {
                assertTrue(cross > 0);
                assertTrue(Math.abs(v[a + 3] - v[b + 3]) < .01);
                assertTrue(Math.abs(v[a + 3] - v[c + 3]) < .01);
                area += cross * .5;
            }
        }
        assertEquals(Math.PI, area, .006);
        assertEquals(Math.PI * (1.105 * 1.105 - 1), haloArea, .001);
    }
}
