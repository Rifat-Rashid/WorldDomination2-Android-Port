package com.example.worlddomination.game.rendering;

import java.util.Arrays;

/** Fixed orthographic view. Texture longitude moves, so the silhouette and lighting stay put. */
final class MenuGlobeGeometry {
    private static final int LONGITUDES = 128;
    private static final int LATITUDES = 64;
    private static final int STRIDE = 5; // view-space xyz, unwrapped map uv
    private static final float[] HALO_RADII = {1f, 1.025f, 1.05f, 1.075f, 1.105f};
    final float[] vertices = new float[(LONGITUDES + 1) * (LATITUDES + 1 + HALO_RADII.length) * STRIDE];
    final short[] indices;

    MenuGlobeGeometry() {
        int columns = LONGITUDES + 1;
        double tilt = Math.toRadians(16);
        int v = 0;
        for (int row = 0; row <= LATITUDES; row++) {
            double latitude = Math.PI * ((double) row / LATITUDES - .5);
            for (int col = 0; col <= LONGITUDES; col++) {
                double longitude = 2 * Math.PI * ((double) col / LONGITUDES - .5);
                double y = Math.sin(latitude);
                double z = Math.cos(latitude) * Math.cos(longitude);
                vertices[v++] = (float) (Math.cos(latitude) * Math.sin(longitude));
                vertices[v++] = (float) (Math.cos(tilt) * y - Math.sin(tilt) * z);
                vertices[v++] = (float) (Math.sin(tilt) * y + Math.cos(tilt) * z);
                vertices[v++] = .09f + (float) col / LONGITUDES;
                vertices[v++] = 1f - (float) row / LATITUDES;
            }
        }
        int firstHalo = v / STRIDE;
        for (float radius : HALO_RADII) {
            for (int col = 0; col <= LONGITUDES; col++) {
                double angle = 2 * Math.PI * col / LONGITUDES;
                vertices[v++] = radius * (float) Math.cos(angle);
                vertices[v++] = radius * (float) Math.sin(angle);
                vertices[v++] = -2f; // distinguishes the atmospheric ring from the sphere
                v += 2;
            }
        }
        short[] triangles = new short[6 * LONGITUDES * (LATITUDES + HALO_RADII.length - 1)];
        int count = 0;
        for (int row = 0; row < LATITUDES + HALO_RADII.length; row++) {
            if (row == LATITUDES) continue; // sphere and halo have separate edges
            for (int col = 0; col < LONGITUDES; col++) {
                int a = row * columns + col;
                count = triangle(triangles, count, a, a + 1, a + columns, a >= firstHalo);
                count = triangle(triangles, count, a + 1, a + columns + 1, a + columns, a >= firstHalo);
            }
        }
        indices = Arrays.copyOf(triangles, count);
    }

    private int triangle(short[] triangles, int count, int a, int b, int c, boolean halo) {
        if (!halo) {
            int i = a * STRIDE, j = b * STRIDE, k = c * STRIDE;
            float cross = (vertices[j] - vertices[i]) * (vertices[k + 1] - vertices[i + 1])
                    - (vertices[k] - vertices[i]) * (vertices[j + 1] - vertices[i + 1]);
            if (cross <= 0) return count;
        }
        triangles[count++] = (short) a;
        triangles[count++] = (short) b;
        triangles[count++] = (short) c;
        return count;
    }
}
