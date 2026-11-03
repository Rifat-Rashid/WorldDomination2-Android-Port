package com.example.worlddomination.game.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

/** Eleven preprocessed source cocoon frames on one cached Android texture page. */
public final class OriginalActionTubeAssets implements Disposable {
    private final TextureAtlas atlas;
    private final TextureRegion[] frames = new TextureRegion[11];

    public OriginalActionTubeAssets() {
        atlas = new TextureAtlas(Gdx.files.internal("original/ui/action-tubes.atlas"));
        for (int index = 0; index < frames.length; index++) {
            frames[index] = atlas.findRegion("tube" + (index + 1));
            if (frames[index] == null) {
                throw new IllegalStateException("Missing original action tube frame " + (index + 1));
            }
        }
    }

    public TextureRegion getFrame(int oneBasedFrame) { return frames[oneBasedFrame - 1]; }

    @Override
    public void dispose() { atlas.dispose(); }
}
