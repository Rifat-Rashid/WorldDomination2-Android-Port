package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/** Flash clip-depth masks: paint coverage into stencil, never into the color buffer. */
final class FlashStencilMasks {
    static final int MAX_MASKS = 8;
    private ShaderProgram coverageShader;
    private ShaderProgram previousShader;
    private int activeBits;
    private int count;

    void beginCoverage(SpriteBatch batch) {
        if (count == MAX_MASKS) {
            throw new IllegalStateException("Flash mask nesting exceeds the stencil buffer");
        }
        if (coverageShader == null) {
            coverageShader = new ShaderProgram(
                    "attribute vec4 a_position; attribute vec4 a_color;"
                            + "attribute vec2 a_texCoord0; uniform mat4 u_projTrans;"
                            + "varying vec2 v_uv; void main(){v_uv=a_texCoord0;"
                            + "gl_Position=u_projTrans*a_position;}",
                    "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
                            + "varying vec2 v_uv; uniform sampler2D u_texture;"
                            + "void main(){if(texture2D(u_texture,v_uv).a<=0.0039) discard;"
                            + "gl_FragColor=vec4(1.0);}");
            if (!coverageShader.isCompiled()) {
                throw new IllegalStateException("Flash mask shader: " + coverageShader.getLog());
            }
        }
        batch.flush();
        previousShader = batch.getShader();
        int bit = 1 << count;
        Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
        Gdx.gl.glStencilMask(bit);
        Gdx.gl.glClearStencil(0);
        Gdx.gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
        Gdx.gl.glStencilFunc(GL20.GL_EQUAL, activeBits | bit, activeBits);
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
        Gdx.gl.glColorMask(false, false, false, false);
        batch.setShader(coverageShader);
        activeBits |= bit;
        count++;
    }

    void endCoverage(SpriteBatch batch) {
        batch.flush();
        batch.setShader(previousShader);
        Gdx.gl.glColorMask(true, true, true, true);
        Gdx.gl.glStencilMask(0);
        Gdx.gl.glStencilFunc(GL20.GL_EQUAL, activeBits, activeBits);
        Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP);
    }

    void pop(SpriteBatch batch) {
        batch.flush();
        activeBits &= ~(1 << --count);
        if (count == 0) {
            Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
            Gdx.gl.glStencilMask(0xff);
        } else {
            Gdx.gl.glStencilFunc(GL20.GL_EQUAL, activeBits, activeBits);
        }
    }

    void dispose() {
        if (coverageShader != null) {
            coverageShader.dispose();
            coverageShader = null;
        }
    }
}
