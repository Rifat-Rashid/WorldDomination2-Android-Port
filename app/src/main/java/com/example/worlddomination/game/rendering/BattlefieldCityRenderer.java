package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.assets.OriginalCityAssets;
import com.example.worlddomination.game.assets.OriginalCityAssets.Frame;
import com.example.worlddomination.game.simulation.CityState;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;
import com.example.worlddomination.game.ui.BattlefieldCitySelection;

import java.util.List;

/** Draws source city artwork in the recovered back-to-front depth order. */
public final class BattlefieldCityRenderer {
    private static final String PROPAGANDA_TINT_VERTEX_SHADER =
            "attribute vec4 a_position;\n"
                    + "attribute vec4 a_color;\n"
                    + "attribute vec2 a_texCoord0;\n"
                    + "uniform mat4 u_projTrans;\n"
                    + "varying vec4 v_color;\n"
                    + "varying vec2 v_texCoords;\n"
                    + "void main() {\n"
                    + "  v_color = a_color;\n"
                    + "  v_color.a *= 255.0 / 254.0;\n"
                    + "  v_texCoords = a_texCoord0;\n"
                    + "  gl_Position = u_projTrans * a_position;\n"
                    + "}\n";
    private static final String PROPAGANDA_TINT_FRAGMENT_SHADER =
            "#ifdef GL_ES\n"
                    + "precision mediump float;\n"
                    + "#endif\n"
                    + "varying vec4 v_color;\n"
                    + "varying vec2 v_texCoords;\n"
                    + "uniform sampler2D u_texture;\n"
                    + "uniform vec4 u_flashMultipliers;\n"
                    + "uniform vec4 u_flashAdditions;\n"
                    + "void main() {\n"
                    + "  vec4 source = texture2D(u_texture, v_texCoords);\n"
                    + "  vec4 transformed = clamp(source * u_flashMultipliers"
                    + " + u_flashAdditions, 0.0, 1.0);\n"
                    + "  gl_FragColor = transformed * v_color;\n"
                    + "}\n";
    private static final int[] CITY_RENDER_ORDER = BattlefieldCityLayout.cityRenderOrder();
    private static final int CITY_COUNT = BattlefieldCityLayout.PLAYER_COUNT
            * BattlefieldCityLayout.CITIES_PER_PLAYER;
    private static final FlashColorTransform[] PLAYER_TINTS = createPlayerTints();

    private final OriginalCityAssets cityAssets;
    private final OriginalFlashAnimationAssets flashAssets;
    private final CityBioloadCloudRenderer bioloadCloudRenderer;
    private final CityShrinkSequence[] citySequences = new CityShrinkSequence[CITY_COUNT];
    private final CityPropagandaBeamSequence[] propagandaSequences =
            new CityPropagandaBeamSequence[CITY_COUNT];
    private final int[] trackedTargets = new int[CITY_COUNT];
    private final FlashAffineTransform[] beamTransforms = createBeamTransforms();
    private ShaderProgram propagandaTintShader;
    private boolean propagandaTintShaderChecked;

    public BattlefieldCityRenderer(OriginalCityAssets cityAssets,
                                   OriginalFlashAnimationAssets flashAssets) {
        this.cityAssets = cityAssets;
        this.flashAssets = flashAssets;
        bioloadCloudRenderer = new CityBioloadCloudRenderer(flashAssets);
        for (int address = 0; address < CITY_COUNT; address++) {
            citySequences[address] = new CityShrinkSequence();
            propagandaSequences[address] = new CityPropagandaBeamSequence();
            trackedTargets[address] = Integer.MIN_VALUE;
        }
    }

    /** Seeds presentation frames when a match starts, without animating initial cities. */
    public void reset(GameState game) {
        bioloadCloudRenderer.reset(game);
        for (int address = 0; address < CITY_COUNT; address++) {
            CityState city = cityAt(game, address);
            int frameNumber = city == null ? -1 : city.getArtworkFrame();
            citySequences[address].reset(frameNumber);
            propagandaSequences[address].reset();
            trackedTargets[address] = frameNumber;
        }
    }

    /** Starts original Flash beam clips for the resolved city results of one turn. */
    public void playPropagandaEvents(GameState game,
                                     List<GameState.PropagandaEvent> events) {
        if (game == null || events == null) {
            return;
        }
        for (GameState.PropagandaEvent event : events) {
            if (event.targetPlayerId < 0 || event.targetPlayerId >= CITY_COUNT
                    / BattlefieldCityLayout.CITIES_PER_PLAYER
                    || event.targetCityIndex < 0
                    || event.targetCityIndex >= BattlefieldCityLayout.CITIES_PER_PLAYER
                    || event.attackerPlayerId < 0
                    || event.attackerPlayerId >= BattlefieldCityLayout.PLAYER_COUNT) {
                continue;
            }
            GameState.PlayerState attacker = game.getPlayerState(event.attackerPlayerId);
            if (attacker == null) {
                continue;
            }
            int address = event.targetPlayerId * BattlefieldCityLayout.CITIES_PER_PLAYER
                    + event.targetCityIndex;
            propagandaSequences[address].play(event.targetLossSequenceIndex,
                    attacker.getPlayerColor());
        }
    }

    /** Mirrors Presenter.endBattle, which resets the two city clips after turn playback. */
    public void resetPropagandaPlayback() {
        for (CityPropagandaBeamSequence sequence : propagandaSequences) {
            sequence.reset();
        }
    }

    /** Starts transitions for any changed city frames without consuming a source tick. */
    public void updateTargets(GameState game) {
        if (game == null) {
            return;
        }
        bioloadCloudRenderer.updateTargets(game);
        for (int address = 0; address < CITY_COUNT; address++) {
            CityState city = cityAt(game, address);
            int targetFrame = city == null ? -1 : city.getArtworkFrame();
            if (targetFrame != trackedTargets[address]) {
                citySequences[address].target(targetFrame);
                trackedTargets[address] = targetFrame;
            }
        }
    }

    /** Tracks model changes and advances each transition by one 24 Hz tick. */
    public void tick(GameState game) {
        if (game == null) {
            return;
        }
        updateTargets(game);
        bioloadCloudRenderer.tick();
        for (CityShrinkSequence sequence : citySequences) {
            sequence.tick();
        }
        for (CityPropagandaBeamSequence sequence : propagandaSequences) {
            sequence.tick();
        }
    }

    public boolean isAnimating() {
        if (bioloadCloudRenderer.isAnimating()) {
            return true;
        }
        for (CityShrinkSequence sequence : citySequences) {
            if (sequence.isAnimating()) {
                return true;
            }
        }
        for (CityPropagandaBeamSequence sequence : propagandaSequences) {
            if (sequence.isPlaying()) {
                return true;
            }
        }
        return false;
    }

    public void draw(SpriteBatch batch, Rectangle mapBounds, GameState game) {
        draw(batch, mapBounds, game, null);
    }

    public void draw(SpriteBatch batch, Rectangle mapBounds, GameState game,
                     String selectedEquipment) {
        if (game == null) {
            return;
        }
        float scale = CitySpriteLayout.scale(mapBounds, cityAssets.getArtworkScale());
        for (int address : CITY_RENDER_ORDER) {
            int playerId = address / BattlefieldCityLayout.CITIES_PER_PLAYER;
            int cityIndex = address % BattlefieldCityLayout.CITIES_PER_PLAYER;
            GameState.PlayerState owner = game.getPlayerState(playerId);
            CityState city = owner.getCity(cityIndex);
            if (city == null) {
                continue;
            }
            int cityAddress = playerId * BattlefieldCityLayout.CITIES_PER_PLAYER + cityIndex;
            CityShrinkSequence sequence = citySequences[cityAddress];
            int frameNumber = sequence.getFrameNumber();
            if (frameNumber >= 1) {
                Frame frame = cityAssets.getFrame(owner.getReligionCode(), frameNumber);
                if (frame != null) {
                    float width = frame.region.getRegionWidth() * scale;
                    float height = frame.region.getRegionHeight() * scale;
                    float x = BattlefieldCityLayout.x(mapBounds, playerId, cityIndex)
                            + frame.offsetX * scale;
                    float y = BattlefieldCityLayout.y(mapBounds, playerId, cityIndex)
                            - frame.offsetY * scale - height;
                    float red = batch.getColor().r;
                    float green = batch.getColor().g;
                    float blue = batch.getColor().b;
                    float previousAlpha = batch.getColor().a;
                    float alpha = sequence.getAlpha() / 100f;
                    if (alpha < 1f) {
                        batch.setColor(red, green, blue, previousAlpha * alpha);
                    }
                    BattlefieldCitySelection.Tint selection = BattlefieldCitySelection.tint(
                            game, playerId, cityIndex, selectedEquipment);
                    ShaderProgram previousShader = batch.getShader();
                    if (selection != BattlefieldCitySelection.Tint.DEFAULT) {
                        ensurePropagandaTintShader();
                        if (propagandaTintShader == null)
                            throw new IllegalStateException("Source city color shader is unavailable");
                        FlashColorTransform tint = selection == BattlefieldCitySelection.Tint.RED
                                ? FlashColorTransform.CITY_RED : FlashColorTransform.CITY_GREEN;
                        batch.setShader(propagandaTintShader);
                        propagandaTintShader.setUniformf("u_flashMultipliers", tint.redMultiplier,
                                tint.greenMultiplier, tint.blueMultiplier, tint.alphaMultiplier);
                        propagandaTintShader.setUniformf("u_flashAdditions", tint.redAdd,
                                tint.greenAdd, tint.blueAdd, tint.alphaAdd);
                    }
                    batch.draw(frame.region, x, y, width, height);
                    if (selection != BattlefieldCitySelection.Tint.DEFAULT)
                        batch.setShader(previousShader);
                    if (alpha < 1f) {
                        batch.setColor(red, green, blue, previousAlpha);
                    }
                }
            }
            drawPropagandaBeams(batch, mapBounds, owner, playerId, cityIndex,
                    cityAddress, sequence.getAlpha());
            bioloadCloudRenderer.drawCity(batch, mapBounds, playerId, cityIndex);
        }
    }

    private void drawPropagandaBeams(SpriteBatch batch, Rectangle mapBounds,
                                     GameState.PlayerState owner, int playerId,
                                     int cityIndex, int cityAddress, int cityAlpha) {
        CityPropagandaBeamSequence sequence = propagandaSequences[cityAddress];
        if (!sequence.isPlaying()) {
            return;
        }
        // The style-specific root supplies each overlay's exact source placement.
        // Its data lives beside the nested beam clips in the Flash asset cache.
        FlashAnimationTimeline cityTimeline = flashAssetsCityTimeline(
                owner.getReligionCode());
        FlashAnimationTimeline.Frame cityFrame = cityTimeline == null
                ? null : cityTimeline.getFrame(0);
        if (cityFrame == null) {
            return;
        }
        for (FlashAnimationTimeline.Layer layer : cityFrame.layers) {
            int beamIndex = "propagandaBeams".equals(layer.name) ? 0
                    : ("propagandaBeams2".equals(layer.name) ? 1 : -1);
            if (beamIndex < 0) {
                continue;
            }
            int frameIndex = sequence.getFrameIndex(beamIndex);
            if (frameIndex < 0) {
                continue;
            }
            FlashAnimationTimeline beamTimeline = flashAssetsTimeline(layer.shapeId);
            FlashAnimationTimeline.Frame beamFrame = beamTimeline == null
                    ? null : beamTimeline.getFrame(frameIndex);
            if (beamFrame == null) {
                continue;
            }
            drawTintedBeam(batch, mapBounds, playerId, cityIndex, layer,
                    beamFrame, frameIndex, cityAlpha / 100f,
                    sequence.getAttackerColor(beamIndex));
        }
    }

    private FlashAnimationTimeline flashAssetsCityTimeline(String religionCode) {
        return flashAssets == null ? null : flashAssets.getCityTimeline(religionCode);
    }

    private FlashAnimationTimeline flashAssetsTimeline(int symbolId) {
        return flashAssets == null ? null : flashAssets.getTimelineBySymbolId(symbolId);
    }

    private void drawTintedBeam(SpriteBatch batch, Rectangle mapBounds,
                                int cityOwnerId, int cityIndex,
                                FlashAnimationTimeline.Layer placement,
                                FlashAnimationTimeline.Frame beamFrame,
                                int clipFrameIndex, float alpha, int playerColor) {
        FlashColorTransform tint = PLAYER_TINTS[playerColor - 1];
        ShaderProgram previousShader = batch.getShader();
        ensurePropagandaTintShader();
        boolean shaderApplied = propagandaTintShader != null;
        if (shaderApplied) {
            batch.setShader(propagandaTintShader);
            propagandaTintShader.setUniformf("u_flashMultipliers",
                    tint.redMultiplier, tint.greenMultiplier,
                    tint.blueMultiplier, tint.alphaMultiplier);
            propagandaTintShader.setUniformf("u_flashAdditions", tint.redAdd,
                    tint.greenAdd, tint.blueAdd, tint.alphaAdd);
        }
        batch.setColor(1f, 1f, 1f, alpha);
        FlashAffineTransform parent = beamTransforms[0].setIdentity();
        beamTransforms[1].setProduct(parent, placement.a, placement.b,
                placement.c, placement.d, placement.tx, placement.ty);
        drawBeamFrame(batch, mapBounds, beamFrame, cityOwnerId, cityIndex,
                clipFrameIndex, 1f, alpha, shaderApplied ? null : tint, 1);
        batch.setColor(Color.WHITE);
        batch.setShader(previousShader);
    }

    private void drawBeamFrame(SpriteBatch batch, Rectangle mapBounds,
                               FlashAnimationTimeline.Frame frame,
                               int cityOwnerId, int cityIndex,
                               int clipFrameIndex,
                               float parentAlpha, float cityAlpha,
                               FlashColorTransform fallbackTint, int depth) {
        if (frame == null || parentAlpha <= 0f || depth >= beamTransforms.length - 1) {
            return;
        }
        float mapScale = CitySpriteLayout.scale(mapBounds, cityAssets.getArtworkScale());
        float sourceX = BattlefieldCityLayout.sourceCityX(cityOwnerId, cityIndex);
        float sourceY = BattlefieldCityLayout.sourceCityY(cityOwnerId, cityIndex);
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            float layerAlpha = parentAlpha * layer.alpha;
            if (layerAlpha <= 0f) {
                continue;
            }
            FlashAffineTransform transform = beamTransforms[depth + 1].setProduct(
                    beamTransforms[depth], layer.a, layer.b, layer.c, layer.d,
                    layer.tx, layer.ty);
            if (transform.scaleX == 0f || transform.scaleY == 0f) {
                continue;
            }
            TextureRegion region = flashAssets.getShape(layer.shapeId);
            if (region != null) {
                float originX = flashAssets.getShapeOriginX(layer.shapeId);
                float originY = flashAssets.getShapeOriginY(layer.shapeId);
                float registrationX = transform.tx + transform.a * originX
                        + transform.c * originY;
                float registrationY = transform.ty + transform.b * originX
                        + transform.d * originY;
                float width = region.getRegionWidth() * mapScale;
                float height = region.getRegionHeight() * mapScale;
                float x = BattlefieldCityLayout.sourceX(mapBounds,
                        sourceX + registrationX * cityAssets.getArtworkScale(), cityOwnerId);
                float top = BattlefieldCityLayout.sourceY(mapBounds,
                        sourceY + registrationY * cityAssets.getArtworkScale());
                if (fallbackTint == null) {
                    batch.setColor(1f, 1f, 1f, cityAlpha * layerAlpha);
                } else {
                    batch.setColor(approximateTint(fallbackTint.redMultiplier,
                                    fallbackTint.redAdd),
                            approximateTint(fallbackTint.greenMultiplier,
                                    fallbackTint.greenAdd),
                            approximateTint(fallbackTint.blueMultiplier,
                                    fallbackTint.blueAdd), cityAlpha * layerAlpha);
                }
                batch.draw(region, x, top - height, 0f, height, width, height,
                        transform.scaleX, transform.scaleY, transform.rotationDegrees);
                continue;
            }
            FlashAnimationTimeline nested = flashAssets.getTimelineBySymbolId(layer.shapeId);
            if (nested == null || nested.getFrameCount() == 0) {
                continue;
            }
            int nestedFrameIndex = Math.min(clipFrameIndex, nested.getFrameCount() - 1);
            drawBeamFrame(batch, mapBounds, nested.getFrame(nestedFrameIndex),
                    cityOwnerId, cityIndex, clipFrameIndex, layerAlpha, cityAlpha,
                    fallbackTint, depth + 1);
        }
    }

    private void ensurePropagandaTintShader() {
        if (propagandaTintShaderChecked) {
            return;
        }
        propagandaTintShaderChecked = true;
        ShaderProgram candidate = new ShaderProgram(PROPAGANDA_TINT_VERTEX_SHADER,
                PROPAGANDA_TINT_FRAGMENT_SHADER);
        if (candidate.isCompiled()) {
            propagandaTintShader = candidate;
        } else {
            if (Gdx.app != null) {
                Gdx.app.error("BattlefieldCityRenderer",
                        "Propaganda tint shader failed to compile: " + candidate.getLog());
            }
            candidate.dispose();
        }
    }

    private static float approximateTint(float multiplier, float addition) {
        return Math.max(0f, Math.min(1f, multiplier * 0.5f + addition));
    }

    private static FlashColorTransform[] createPlayerTints() {
        FlashColorTransform[] tints = new FlashColorTransform[5];
        for (int index = 0; index < tints.length; index++) {
            tints[index] = FlashColorTransform.forPlayerColor(index + 1);
        }
        return tints;
    }

    private static FlashAffineTransform[] createBeamTransforms() {
        FlashAffineTransform[] transforms = new FlashAffineTransform[10];
        for (int index = 0; index < transforms.length; index++) {
            transforms[index] = new FlashAffineTransform();
        }
        return transforms;
    }

    public void dispose() {
        if (propagandaTintShader != null) {
            propagandaTintShader.dispose();
            propagandaTintShader = null;
        }
    }

    private static CityState cityAt(GameState game, int address) {
        if (game == null) {
            return null;
        }
        int playerId = address / BattlefieldCityLayout.CITIES_PER_PLAYER;
        int cityIndex = address % BattlefieldCityLayout.CITIES_PER_PLAYER;
        GameState.PlayerState owner = game.getPlayerState(playerId);
        return owner == null ? null : owner.getCity(cityIndex);
    }
}
