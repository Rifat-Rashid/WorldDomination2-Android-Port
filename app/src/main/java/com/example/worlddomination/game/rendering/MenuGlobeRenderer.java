package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

/** Draws the rotating title globe in one indexed draw before the title UI batch. */
public final class MenuGlobeRenderer implements Disposable {
    private final ShaderProgram shader;
    private final Mesh surface;
    private final Texture map;

    public MenuGlobeRenderer() {
        shader = new ShaderProgram(Gdx.files.internal("ui/menu/globe.vert"),
                Gdx.files.internal("ui/menu/globe.frag"));
        if (!shader.isCompiled()) {
            String log = shader.getLog();
            shader.dispose();
            throw new IllegalStateException("Menu globe shader: " + log);
        }
        MenuGlobeGeometry geometry = new MenuGlobeGeometry();
        surface = new Mesh(true, geometry.vertices.length / 5, geometry.indices.length,
                VertexAttribute.Position(), VertexAttribute.TexCoords(0));
        try {
            surface.setVertices(geometry.vertices);
            surface.setIndices(geometry.indices);
            map = new Texture(Gdx.files.internal("ui/menu/globe-lines.png"), true);
        } catch (RuntimeException failure) {
            surface.dispose();
            shader.dispose();
            throw failure;
        }
        map.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        map.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.ClampToEdge);
    }

    public void draw(Matrix4 projection, float cx, float cy, float radius, float rotationTurns) {
        if (radius <= 0f) return;
        shader.bind();
        shader.setUniformMatrix("u_projTrans", projection);
        shader.setUniformf("u_globe", cx, cy, radius);
        shader.setUniformf("u_turns", rotationTurns);
        shader.setUniformi("u_texture", 0);
        map.bind(0);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        try {
            surface.render(shader, GL20.GL_TRIANGLES);
        } finally {
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    @Override
    public void dispose() {
        surface.dispose();
        map.dispose();
        shader.dispose();
    }
}
