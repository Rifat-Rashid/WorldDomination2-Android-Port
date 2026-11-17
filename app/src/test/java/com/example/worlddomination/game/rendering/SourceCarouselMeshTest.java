package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.ui.SetupScreenLayout;
import org.junit.Test;
import static org.junit.Assert.*;

public class SourceCarouselMeshTest {
    @Test public void sourceDimensionsProduceIdentityAcrossEveryVertex() {
        SourceCarouselMesh mesh = new SourceCarouselMesh();
        for (float scale : new float[]{.75f, 1f, 1.3f}) {
            Rectangle[] wells = new Rectangle[6];
            for (int i = 0; i < 6; i++) wells[i] = new Rectangle(37 + (173 + 74 * i) * scale,
                    19 + 191 * scale, 64 * scale, 70 * scale);
            assertTrue(mesh.update(new Rectangle(37 + 147*scale, 19 + 101*scale, 488*scale, 180*scale), wells,
                    new Rectangle(37 + 176*scale, 19 + 123*scale, 430*scale, 49*scale)));
            assertTrue(mesh.isUniform());
            for (int r = 0; r < mesh.rows(); r++) {
                assertEquals(19 + (600 - mesh.sourceY(r))*scale, mesh.destinationY(r), .001f);
                for (int c = 0; c < mesh.columns(); c++)
                    assertEquals(37 + mesh.sourceX(c)*scale, mesh.destinationX(r, c), .001f);
            }
        }
    }

    @Test public void responsiveGeometryCoversFrameAndFitsWellsAndInset() {
        SourceCarouselMesh mesh = new SourceCarouselMesh();
        for (float[] size : new float[][]{{800,600,0,0}, {896,414,0,0},
                {1194,552,0,0}, {2424,1080,0,0}, {930,414,54,20}}) {
            SetupScreenLayout layout = new SetupScreenLayout();
            layout.update(size[0], size[1], size[2], size[3], 0, 0);
            Rectangle frame = layout.leaderCarouselPanel, inset = layout.leaderDescription;
            mesh.update(frame, layout.leaderCards, inset);
            assertFalse(mesh.isUniform());
            for (int r = 0; r < mesh.rows(); r++) {
                assertEquals(frame.x, mesh.destinationX(r, 0), .001f);
                assertEquals(frame.x + frame.width, mesh.destinationX(r, mesh.columns()-1), .001f);
                if (r > 0) assertTrue(mesh.destinationY(r-1) > mesh.destinationY(r));
                for (int c = 1; c < mesh.columns(); c++)
                    assertTrue("positive cell width", mesh.destinationX(r,c) > mesh.destinationX(r,c-1));
            }
            assertEquals(frame.y + frame.height, mesh.destinationY(0), .001f);
            assertEquals(frame.y, mesh.destinationY(mesh.rows()-1), .001f);
            for (int i = 0; i < 6; i++) {
                Rectangle w = layout.leaderCards[i];
                float scale = Math.min(w.width / 64f, w.height / 70f);
                float x = w.x + (w.width - 64f * scale) / 2;
                assertEquals(x, mesh.destinationX(2, column(mesh, 173 + 74*i)), .001f);
                assertEquals(x + 64f*scale, mesh.destinationX(3, column(mesh, 237 + 74*i)), .001f);
                assertEquals(w.y + (w.height - 70f*scale)/2, mesh.destinationY(3), .001f);
            }
            assertEquals(inset.x, mesh.destinationX(4, column(mesh,176)), .001f);
            assertEquals(inset.x + inset.width, mesh.destinationX(7, column(mesh,606)), .001f);
            assertEquals(inset.y + inset.height, mesh.destinationY(4), .001f);
            assertEquals(inset.y, mesh.destinationY(7), .001f);
            float rail = Math.min(frame.width / 488f, frame.height / 180f);
            rail = Math.min(rail, Math.min(inset.x - frame.x,
                    frame.x + frame.width - inset.x - inset.width) / 29f);
            float border = Math.min(rail, Math.min(inset.width / 21f, inset.height / 21f));
            assertEquals(10f * border, mesh.destinationX(4, column(mesh,186))
                    - mesh.destinationX(4, column(mesh,176)), .001f);
            assertEquals(10f * border, mesh.destinationY(4) - mesh.destinationY(5), .001f);
            for (int r = 1; r < mesh.rows(); r++) {
                assertEquals(mesh.destinationX(0, column(mesh,172)), mesh.destinationX(r, column(mesh,172)), .001f);
                assertEquals(mesh.destinationX(0, column(mesh,610)), mesh.destinationX(r, column(mesh,610)), .001f);
            }
            float cached = mesh.destinationX(4, column(mesh,176));
            assertFalse(mesh.update(frame, layout.leaderCards, inset));
            assertEquals(cached, mesh.destinationX(4, column(mesh,176)), 0f);
            Rectangle shifted = new Rectangle(inset.x + .1f, inset.y, inset.width - .2f, inset.height);
            assertTrue(mesh.update(frame, layout.leaderCards, shifted));
            assertFalse(mesh.update(frame, layout.leaderCards, shifted));
            Rectangle invalid = new Rectangle(frame.x, inset.y, inset.width, inset.height);
            for (int retry = 0; retry < 2; retry++) {
                try { mesh.update(frame, layout.leaderCards, invalid); fail("invalid inset accepted"); }
                catch (IllegalArgumentException expected) { /* invalid updates must not become cached */ }
            }
            assertTrue(mesh.update(frame, layout.leaderCards, inset));
        }
    }

    @Test public void uniformDetectionTracksPerturbationsAndInvalidRecovery() {
        SourceCarouselMesh mesh = new SourceCarouselMesh();
        Rectangle frame = new Rectangle(147, 101, 488, 180);
        Rectangle inset = new Rectangle(176, 123, 430, 49);
        Rectangle[] wells = new Rectangle[6];
        for (int i = 0; i < 6; i++) wells[i] = new Rectangle(173 + 74*i, 191, 64, 70);
        assertTrue(mesh.update(frame, wells, inset));
        assertTrue(mesh.isUniform());
        assertFalse(mesh.update(frame, wells, inset));
        assertTrue(mesh.isUniform());
        inset.x += .1f;
        assertTrue(mesh.update(frame, wells, inset));
        assertFalse(mesh.isUniform());
        inset.x -= .1f;
        wells[2].x += .1f;
        assertTrue(mesh.update(frame, wells, inset));
        assertFalse(mesh.isUniform());
        wells[2].x -= .1f;
        assertTrue(mesh.update(frame, wells, inset));
        assertTrue(mesh.isUniform());
        inset.x = frame.x;
        try { mesh.update(frame, wells, inset); fail("invalid inset accepted"); }
        catch (IllegalArgumentException expected) { assertFalse(mesh.isUniform()); }
        inset.x = 176;
        assertTrue(mesh.update(frame, wells, inset));
        assertTrue(mesh.isUniform());
    }

    private static int column(SourceCarouselMesh mesh, float x) {
        for (int c = 0; c < mesh.columns(); c++) if (mesh.sourceX(c) == x) return c;
        throw new AssertionError("missing source knot " + x);
    }
}
