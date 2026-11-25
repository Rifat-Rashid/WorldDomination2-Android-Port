package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/** Projects a geographic texture onto the front hemisphere of the menu globe. */
public final class MenuGlobeRenderer implements Disposable {
    // Public-domain Natural Earth 1:110m data. RGB stores land, coast and borders.
    private static final String MAP = "ui/menu/globe-lines.png";
    // The quad extends beyond the sphere to leave room for its atmosphere.
    private static final float QUAD_RADIUS = 1.12f;
    private static final String VERTEX_SHADER = String.join("\n",
            "attribute vec4 a_position;",
            "attribute vec4 a_color;",
            "attribute vec2 a_texCoord0;",
            "uniform mat4 u_projTrans;",
            "varying mediump vec2 v_uv;",
            "varying lowp vec4 v_color;",
            "void main() {",
            "    v_uv = a_texCoord0;",
            "    v_color = a_color;",
            "    v_color.a *= 255.0 / 254.0;",
            "    gl_Position = u_projTrans * a_position;",
            "}");
    private static final String FRAGMENT_SHADER = String.join("\n",
            "#ifdef GL_FRAGMENT_PRECISION_HIGH",
            "precision highp float;",
            "#else",
            "precision mediump float;",
            "#endif",
            "uniform sampler2D u_texture;",
            "uniform float u_turns;",
            "varying mediump vec2 v_uv;",
            "varying lowp vec4 v_color;",
            "const float pi = 3.14159265358979;",
            "void main() {",
            "    vec2 p = (v_uv - 0.5) * vec2(2.0, -2.0) * " + QUAD_RADIUS + ";",
            "    float r = length(p);",
            "    float halo = 1.0 - smoothstep(0.99, 1.105, r);",
            "    vec3 haloColor = vec3(0.09, 0.27, 0.39);",
            "    if (r > 1.0) {",
            "        gl_FragColor = vec4(haloColor * v_color.rgb, halo * 0.19 * v_color.a);",
            "        return;",
            "    }",
            "",
            "    // Orthographic projection. Tilt the view north by 16 degrees.",
            "    vec3 view = vec3(p, sqrt(max(0.0, 1.0 - dot(p, p))));",
            "    vec3 normal = vec3(view.x,",
            "            0.9613 * view.y + 0.2756 * view.z,",
            "            0.9613 * view.z - 0.2756 * view.y);",
            "    float z = abs(normal.z) < 0.0001 ? 0.0001 : normal.z;",
            "    float longitude = atan(normal.x, z);",
            "    float latitude = asin(clamp(normal.y, -1.0, 1.0));",
            "    vec2 mapUv = vec2(0.59 + longitude / (2.0 * pi) + u_turns,",
            "                      0.5 - latitude / pi);",
            "    vec3 map = texture2D(u_texture, mapUv).rgb;",
            "",
            "    vec3 keyLight = vec3(-0.52, 0.63, 0.57);",
            "    float lit = smoothstep(-0.42, 0.88, dot(view, keyLight));",
            "    float outline = max(map.g, map.b * 0.86);",
            "    float nearSide = smoothstep(0.025, 0.38, view.z);",
            "    vec3 ocean = mix(vec3(0.013, 0.025, 0.043), vec3(0.037, 0.100, 0.153), lit);",
            "    vec3 land = mix(vec3(0.017, 0.035, 0.052), vec3(0.050, 0.119, 0.171), lit);",
            "    vec3 color = mix(ocean, land, map.r);",
            "    float twilight = smoothstep(0.25, 0.95, dot(view, vec3(-0.72, -0.69, 0.0)));",
            "    vec3 lineColor = mix(vec3(0.34, 0.78, 0.94), vec3(0.53, 0.30, 0.17),",
            "                         twilight * (1.0 - lit) * 0.48);",
            "    color += lineColor * outline * nearSide * mix(0.40, 0.94, lit);",
            "    float rim = smoothstep(0.95, 0.995, r);",
            "    color += mix(haloColor, vec3(0.28, 0.14, 0.073), twilight * 0.58)",
            "             * rim * mix(0.24, 0.49, lit);",
            "    float alpha = mix(1.0, halo * 0.19, smoothstep(0.994, 1.0, r));",
            "    gl_FragColor = vec4(color * v_color.rgb, alpha * v_color.a);",
            "}");

    private final ShaderProgram shader;
    private final Texture map;

    public MenuGlobeRenderer() {
        ShaderProgram candidate = new ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if (!candidate.isCompiled()) {
            String log = candidate.getLog();
            candidate.dispose();
            throw new IllegalStateException("Menu globe shader: " + log);
        }
        try {
            map = new Texture(Gdx.files.internal(MAP), true);
        } catch (RuntimeException failure) {
            candidate.dispose();
            throw failure;
        }
        shader = candidate;
        map.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        map.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge);
    }

    /** Draws inside an active batch. Increasing turns brings eastern longitudes to the center. */
    public void draw(SpriteBatch batch, float cx, float cy, float radius, float rotationTurns) {
        if (radius <= 0f) return;
        float extent = radius * QUAD_RADIUS;
        ShaderProgram previous = batch.getShader();
        batch.setShader(shader);
        try {
            shader.setUniformf("u_turns", (float) (rotationTurns - Math.floor(rotationTurns)));
            batch.draw(map, cx - extent, cy - extent, extent * 2f, extent * 2f);
        } finally {
            batch.setShader(previous);
        }
    }

    @Override
    public void dispose() {
        map.dispose();
        shader.dispose();
    }
}
