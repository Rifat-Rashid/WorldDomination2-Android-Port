package com.example.worlddomination.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.example.worlddomination.game.assets.OriginalFlashAnimationAssets;
import com.example.worlddomination.game.ui.BattlefieldCityLayout;

import java.util.List;

/** Draws cached original Flash unit frames in the fitted battlefield coordinate space. */
public final class FlashBattleUnitRenderer {
    private static final String[] SATELLITE_BASE_COLOR_HEX = {
            "0xFFFFFF", "0x0000FF", "0xFF0000", "0xFF9900", "0xFF000", "0x00FF00"
    };
    private static final int[][] SATELLITE_LASER_COLORS = buildSatelliteLaserColors();
    private static final int MAX_SATELLITE_CLIP_DEPTH = 8;
    private final FlashAffineTransform[] planeTransformScratch = createPlaneTransformScratch();
    private static final String SUBMARINE_TINT_VERTEX_SHADER =
            "attribute vec4 a_position;\n"
                    + "attribute vec4 a_color;\n"
                    + "attribute vec2 a_texCoord0;\n"
                    + "uniform mat4 u_projTrans;\n"
                    + "varying vec4 v_color;\n"
                    + "varying vec2 v_texCoords;\n"
                    + "void main() {\n"
                    + "  v_color = a_color;\n"
                    + "  v_texCoords = a_texCoord0;\n"
                    + "  gl_Position = u_projTrans * a_position;\n"
                    + "}\n";
    private static final String SUBMARINE_TINT_FRAGMENT_SHADER =
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
    private final OriginalFlashAnimationAssets assets;
    private final float[] satelliteLaserOriginScratch = new float[4];
    private ShaderProgram submarineTintShader;
    private boolean submarineTintShaderChecked;

    public FlashBattleUnitRenderer(OriginalFlashAnimationAssets assets) {
        this.assets = assets;
    }

    public void draw(SpriteBatch batch, Rectangle mapBounds,
                     List<FlashSoldierOneAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        for (FlashSoldierOneAnimation animation : animations) {
            for (int index = 0; index < animation.getActorCount(); index++) {
                FlashSoldierOneAnimation.Actor actor = animation.getActor(index);
                drawActor(batch, mapBounds, actor.linkage, actor.sourceFrame,
                        actor.sourceX, actor.sourceY, actor.islandPlayerId,
                        1f, 1f, actor.visible);
            }
        }
    }

    public void drawApacheMarine(SpriteBatch batch, Rectangle mapBounds,
                                 List<FlashApacheMarineAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        for (FlashApacheMarineAnimation animation : animations) {
            for (int index = 0; index < animation.getActorCount(); index++) {
                FlashApacheMarineAnimation.Actor actor = animation.getActor(index);
                drawActor(batch, mapBounds, actor.linkage, actor.sourceFrame,
                        actor.sourceX, actor.sourceY, actor.islandPlayerId,
                        actor.scale, actor.alpha, actor.visible);
            }
        }
    }

    public void drawSuicideBomber(SpriteBatch batch, Rectangle mapBounds,
                                  List<FlashSuicideBomberAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        for (FlashSuicideBomberAnimation animation : animations) {
            for (int index = 0; index < animation.getActorCount(); index++) {
                FlashSuicideBomberAnimation.Actor actor = animation.getActor(index);
                drawActor(batch, mapBounds, actor.linkage, actor.sourceFrame,
                        actor.sourceX, actor.sourceY, actor.islandPlayerId,
                        1f, actor.alpha, actor.visible);
            }
        }
    }

    public void drawChinookTank(SpriteBatch batch, Rectangle mapBounds,
                                List<FlashChinookTankAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        for (FlashChinookTankAnimation animation : animations) {
            for (int index = 0; index < animation.getActorCount(); index++) {
                FlashChinookTankAnimation.Actor actor = animation.getActor(index);
                drawActor(batch, mapBounds, actor.linkage, actor.sourceFrame,
                        actor.sourceX, actor.sourceY, actor.islandPlayerId,
                        1f, 1f, actor.visible);
            }
        }
    }

    /** Draws the recovered Flash rocket sprite and its original fading smoke artwork. */
    public void drawRocket(SpriteBatch batch, Rectangle mapBounds,
                           List<FlashRocketAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        TextureRegion rocketShape = assets.getShape(
                OriginalFlashAnimationAssets.ROCKET_BODY_SHAPE_ID);
        TextureRegion smokeShape = assets.getShape(
                OriginalFlashAnimationAssets.ROCKET_TRAIL_SHAPE_ID);
        if (rocketShape == null || smokeShape == null) {
            return;
        }
        float mapScale = BattlefieldCityLayout.sourceScale(mapBounds);
        for (FlashRocketAnimation animation : animations) {
            FlashBattleRoutes.RocketStrike route = animation.getRoute();
            for (int trail = 0; trail < animation.getTrailCount(); trail++) {
                float trailX = animation.getTrailX(trail);
                drawRocketSprite(batch, mapBounds, smokeShape,
                        trailX, animation.getTrailY(trail), route.attackerPlayerId,
                        route.targetPlayerId, rocketProgress(route, trailX), 0f,
                        animation.getTrailAlpha(trail), mapScale, 1f);
            }
            FlashRocketAnimation.Actor rocket = animation.getRocket();
            if (rocket.visible) {
                drawRocketSprite(batch, mapBounds, rocketShape, rocket.sourceX, rocket.sourceY,
                        route.attackerPlayerId, route.targetPlayerId,
                        rocketProgress(route, rocket.sourceX), rocket.rotationDegrees, 1f,
                        mapScale, rocket.scaleY);
            }
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    /** Draws the original hull and source-colored tint clips during submarine launches. */
    public void drawSubmarines(SpriteBatch batch, Rectangle mapBounds,
                               List<FlashSubmarineAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        FlashAnimationTimeline timeline = assets.getTimeline("submarine");
        FlashAnimationTimeline.Frame rootFrame = timeline == null
                ? null : timeline.getFrame(0);
        if (rootFrame == null) {
            return;
        }
        for (FlashSubmarineAnimation animation : animations) {
            if (!animation.isStarted() || animation.isFinished()) {
                continue;
            }
            FlashBattleRoutes.RocketStrike route = animation.getRoute();
            drawSubmarineTimelineFrame(batch, mapBounds, rootFrame,
                    route.silo.x, route.silo.y, route.targetPlayerId,
                    animation.getSourceFrameIndex(), animation.getTint(), false, 1f, 0);
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
        if (batch.getShader() == submarineTintShader && submarineTintShader != null) {
            batch.setShader(null);
        }
    }

    /** Releases the cached player-tint shader with the other renderer-owned resources. */
    public void dispose() {
        if (submarineTintShader != null) {
            submarineTintShader.dispose();
            submarineTintShader = null;
        }
    }

    /** Draws ship-launched aircraft from the original 24-frame Flash linkage. */
    public void drawCarrierPlanes(SpriteBatch batch, Rectangle mapBounds,
                                  List<FlashCarrierPlaneAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        FlashAnimationTimeline timeline = assets.getTimeline("ship_plane");
        if (timeline == null) {
            return;
        }
        float mapScale = BattlefieldCityLayout.sourceScale(mapBounds);
        for (FlashCarrierPlaneAnimation animation : animations) {
            FlashCarrierPlaneAnimation.Actor actor = animation.getActor();
            if (!actor.visible) {
                continue;
            }
            FlashAnimationTimeline.Frame frame = timeline.getFrame(actor.sourceFrame);
            if (frame == null) {
                continue;
            }
            FlashBattleRoutes.CarrierPlaneStrike route = animation.getRoute();
            batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
            for (FlashAnimationTimeline.Layer layer : frame.layers) {
                TextureRegion region = assets.getShape(layer.shapeId);
                if (region == null) {
                    continue;
                }
                float width = region.getRegionWidth() * mapScale;
                float height = region.getRegionHeight() * mapScale;
                float x = BattlefieldCityLayout.sourceXBetween(mapBounds,
                        actor.sourceX + layer.tx, route.attackerPlayerId,
                        route.targetPlayerId, actor.routeProgress);
                float top = BattlefieldCityLayout.sourceY(mapBounds, actor.sourceY + layer.ty);
                FlashSpriteTransform transform = FlashSpriteTransform.fromMatrix(
                        layer.a, layer.b, layer.c, layer.d);
                float direction = actor.flippedHorizontally ? -1f : 1f;
                batch.draw(region, x, top - height, 0f, height, width, height,
                        transform.scaleX * direction, transform.scaleY,
                        transform.rotationDegrees);
            }
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    /** Draws the source fighter/bomber, color overlay, and raised shadow. */
    public void drawStandalonePlanes(SpriteBatch batch, Rectangle mapBounds,
                                     List<FlashStandalonePlaneAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        float mapScale = BattlefieldCityLayout.sourceScale(mapBounds);
        TextureRegion smokeRegion = assets.getShape(
                OriginalFlashAnimationAssets.ROCKET_TRAIL_SHAPE_ID);
        for (FlashStandalonePlaneAnimation animation : animations) {
            FlashStandalonePlaneAnimation.Actor actor = animation.getActor();
            FlashBattleRoutes.StandalonePlaneStrike route = animation.getRoute();
            if (smokeRegion != null) {
                for (int index = 0; index < animation.getTrailSlotCount(); index++) {
                    float alpha = animation.getTrailAlpha(index);
                    if (alpha <= 0f) {
                        continue;
                    }
                    drawStandalonePlaneTrail(batch, mapBounds, smokeRegion,
                            animation.getTrailX(index), animation.getTrailY(index),
                            route.attackerPlayerId, route.targetPlayerId,
                            animation.getTrailProgress(index),
                            animation.getTrailSize(index), alpha, mapScale);
                }
            }
            if (!actor.visible) {
                continue;
            }
            String linkage = route.linkage;
            drawPlaneLinkage(batch, mapBounds, assets.getTimeline(linkage),
                    actor.sourceFrame, actor.sourceX, actor.sourceY,
                    route.attackerPlayerId, route.targetPlayerId, actor.routeProgress,
                    actor.alpha, actor.scale * mapScale, null, false, false,
                    actor.sourceFrame, 1f, 0, planeTransformScratch[0].setIdentity());

            drawPlaneLinkage(batch, mapBounds, assets.getTimeline(linkage + "_shadow"),
                    0, actor.sourceX, actor.sourceY + actor.shadowYOffset,
                    route.attackerPlayerId, route.targetPlayerId, actor.routeProgress,
                    1f, actor.scale * mapScale, null, false, true,
                    actor.sourceFrame, 1f, 0, planeTransformScratch[0].setIdentity());

            beginSubmarineTint(batch, animation.getTint());
            drawPlaneLinkage(batch, mapBounds, assets.getTimeline(linkage + "_tint"),
                    actor.sourceFrame, actor.sourceX, actor.sourceY,
                    route.attackerPlayerId, route.targetPlayerId, actor.routeProgress,
                    actor.alpha, actor.scale * mapScale, animation.getTint(), true, false,
                    actor.sourceFrame, 1f, 0, planeTransformScratch[0].setIdentity());
            batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
            batch.setShader(null);
        }
    }

    /** Draws the original religion-specific anti-air turret with its tracked direction. */
    public void drawAntiAirTracking(SpriteBatch batch, Rectangle mapBounds,
                                    List<FlashAntiAirTrackingAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        FlashAnimationTimeline timeline = assets.getTimeline("buildings_antiAir");
        if (timeline == null) {
            return;
        }
        float mapScale = BattlefieldCityLayout.sourceScale(mapBounds);
        for (FlashAntiAirTrackingAnimation animation : animations) {
            if (!animation.isVisible()) {
                continue;
            }
            FlashBattleRoutes.Point position = animation.getBuildingPosition();
            drawPlaneLinkage(batch, mapBounds, timeline,
                    animation.getReligionFrameIndex(), position.x, position.y,
                    animation.getDefenderPlayerId(), animation.getDefenderPlayerId(),
                    0f, animation.getAlpha(), mapScale, null, false, false,
                    animation.getFrameIndex(), 1f, 0,
                    planeTransformScratch[0].setIdentity());
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    private static void drawStandalonePlaneTrail(SpriteBatch batch, Rectangle mapBounds,
                                                 TextureRegion smokeRegion,
                                                 float sourceX, float sourceY,
                                                 int fromPlayerId, int toPlayerId,
                                                 float routeProgress, float sourceSize,
                                                 float alpha, float mapScale) {
        float size = sourceSize * mapScale;
        float x = BattlefieldCityLayout.sourceXBetween(mapBounds, sourceX,
                fromPlayerId, toPlayerId, routeProgress);
        float y = BattlefieldCityLayout.sourceY(mapBounds, sourceY);
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(smokeRegion, x - size / 2f, y - size / 2f, size, size);
    }

    private void drawPlaneLinkage(SpriteBatch batch, Rectangle mapBounds,
                                  FlashAnimationTimeline timeline, int frameIndex,
                                  float sourceX, float sourceY, int fromPlayerId,
                                  int toPlayerId, float routeProgress, float alpha,
                                  float actorScale, FlashColorTransform tint,
                                  boolean tinted, boolean shadow, int childFrameIndex,
                                  float parentAlpha, int depth,
                                  FlashAffineTransform parentTransform) {
        if (timeline == null || depth > MAX_SATELLITE_CLIP_DEPTH || alpha <= 0f
                || parentAlpha <= 0f) {
            return;
        }
        FlashAnimationTimeline.Frame frame = timeline.getFrame(frameIndex);
        if (frame == null) {
            return;
        }
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            float layerAlpha = parentAlpha * layer.alpha;
            if (layerAlpha <= 0f) {
                continue;
            }
            FlashAffineTransform transform = planeTransformScratch[depth + 1]
                    .setProduct(parentTransform, layer.a, layer.b, layer.c, layer.d,
                            layer.tx, layer.ty);
            if (transform.scaleX == 0f || transform.scaleY == 0f) {
                continue;
            }
            TextureRegion region = assets.getShape(layer.shapeId);
            if (region != null) {
                float width = region.getRegionWidth() * actorScale;
                float height = region.getRegionHeight() * actorScale;
                float x = BattlefieldCityLayout.sourceXBetween(mapBounds,
                        sourceX + transform.tx, fromPlayerId, toPlayerId, routeProgress);
                float top = BattlefieldCityLayout.sourceY(mapBounds, sourceY + transform.ty);
                if (shadow) {
                    batch.setColor(0f, 0f, 0f, alpha * layerAlpha);
                } else {
                    setSubmarineLayerColor(batch, tint, tinted, alpha * layerAlpha);
                }
                batch.draw(region, x, top - height, 0f, height, width, height,
                        transform.scaleX, transform.scaleY, transform.rotationDegrees);
                continue;
            }

            FlashAnimationTimeline nested = assets.getTimelineBySymbolId(layer.shapeId);
            if (nested == null || nested.getFrameCount() == 0) {
                continue;
            }
            int nestedFrame = Math.min(childFrameIndex, nested.getFrameCount() - 1);
            drawPlaneLinkage(batch, mapBounds, nested, nestedFrame,
                    sourceX, sourceY, fromPlayerId, toPlayerId,
                    routeProgress, alpha, actorScale, tint, tinted, shadow,
                    childFrameIndex, layerAlpha, depth + 1, transform);
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    private static FlashAffineTransform[] createPlaneTransformScratch() {
        FlashAffineTransform[] transforms = new FlashAffineTransform[
                MAX_SATELLITE_CLIP_DEPTH + 2];
        for (int i = 0; i < transforms.length; i++) {
            transforms[i] = new FlashAffineTransform();
        }
        return transforms;
    }

    /** Draws the original religion-specific satellite symbol while it approaches orbit. */
    public void drawSatellites(SpriteBatch batch, Rectangle mapBounds,
                               List<FlashSatelliteAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        FlashAnimationTimeline timeline = assets.getTimeline("satellite");
        if (timeline == null) {
            return;
        }
        for (FlashSatelliteAnimation animation : animations) {
            if (!animation.isStarted() || animation.isFinished()) {
                continue;
            }
            FlashBattleRoutes.SatelliteStrike route = animation.getRoute();
            drawSatelliteTimelineFrame(batch, mapBounds,
                    timeline.getFrameByLabel(route.religionCode),
                    animation.getSourceX(), animation.getSourceY(),
                    route.attackerPlayerId, animation.getSourceClipFrameNumber() - 1,
                    1f, 0);
        }
    }

    /** Draws the source-colored multi-stroke laser between satellite and target city. */
    public void drawSatelliteBeams(ShapeRenderer renderer, Rectangle mapBounds,
                                   List<FlashSatelliteAnimation> animations) {
        if (!assets.isLoaded()) {
            return;
        }
        for (FlashSatelliteAnimation animation : animations) {
            float alpha = Math.max(0f, Math.min(1f, animation.getLaserAlpha()));
            if (!animation.isFiring() || alpha <= 0f) {
                continue;
            }
            FlashBattleRoutes.SatelliteStrike route = animation.getRoute();
            if (!findSatelliteLaserOrigin(animation, satelliteLaserOriginScratch)) {
                continue;
            }
            float sourceX = animation.getSourceX() + satelliteLaserOriginScratch[0]
                    + satelliteLaserOriginScratch[2];
            float sourceY = animation.getSourceY() + satelliteLaserOriginScratch[1]
                    + satelliteLaserOriginScratch[3];
            float startX = BattlefieldCityLayout.sourceX(mapBounds, sourceX,
                    route.attackerPlayerId);
            int[] colors = SATELLITE_LASER_COLORS[route.attackerColor];
            for (int offset = 0; offset < 4; offset++) {
                int color = colors[6 - offset];
                renderer.setColor(((color >>> 16) & 0xFF) / 255f,
                        ((color >>> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, alpha);
                float firstStartY = BattlefieldCityLayout.sourceY(mapBounds,
                        sourceY - offset);
                float firstEndX = BattlefieldCityLayout.sourceX(mapBounds,
                        route.targetCity.x + offset, route.targetPlayerId);
                float firstEndY = BattlefieldCityLayout.sourceY(mapBounds,
                        route.targetCity.y - offset);
                renderer.line(startX, firstStartY, firstEndX, firstEndY);

                float secondStartY = BattlefieldCityLayout.sourceY(mapBounds,
                        sourceY + offset);
                float secondEndX = BattlefieldCityLayout.sourceX(mapBounds,
                        route.targetCity.x, route.targetPlayerId);
                float secondEndY = BattlefieldCityLayout.sourceY(mapBounds,
                        route.targetCity.y + offset);
                renderer.line(startX, secondStartY, secondEndX, secondEndY);
            }
        }
        renderer.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    private void drawSatelliteTimelineFrame(SpriteBatch batch, Rectangle mapBounds,
                                            FlashAnimationTimeline.Frame frame,
                                            float sourceX, float sourceY,
                                            int islandPlayerId, int clipFrameIndex,
                                            float parentAlpha, int depth) {
        if (frame == null || parentAlpha <= 0f || depth > MAX_SATELLITE_CLIP_DEPTH) {
            return;
        }
        float mapScale = BattlefieldCityLayout.sourceScale(mapBounds);
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            float layerAlpha = parentAlpha * layer.alpha;
            if (layerAlpha <= 0f) {
                continue;
            }
            TextureRegion region = assets.getShape(layer.shapeId);
            if (region != null) {
                float width = region.getRegionWidth() * mapScale;
                float height = region.getRegionHeight() * mapScale;
                float x = BattlefieldCityLayout.sourceX(mapBounds,
                        sourceX + layer.tx, islandPlayerId);
                float top = BattlefieldCityLayout.sourceY(mapBounds, sourceY + layer.ty);
                FlashSpriteTransform transform = FlashSpriteTransform.fromMatrix(
                        layer.a, layer.b, layer.c, layer.d);
                batch.setColor(1f, 1f, 1f, layerAlpha);
                batch.draw(region, x, top - height, 0f, height, width, height,
                        transform.scaleX, transform.scaleY, transform.rotationDegrees);
                continue;
            }

            FlashAnimationTimeline nested = assets.getTimelineBySymbolId(layer.shapeId);
            if (nested == null || nested.getFrameCount() == 0) {
                continue;
            }
            int nestedFrameIndex = Math.min(clipFrameIndex, nested.getFrameCount() - 1);
            drawSatelliteTimelineFrame(batch, mapBounds, nested.getFrame(nestedFrameIndex),
                    sourceX + layer.tx, sourceY + layer.ty, islandPlayerId,
                    clipFrameIndex, layerAlpha, depth + 1);
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    private void drawSubmarineTimelineFrame(SpriteBatch batch, Rectangle mapBounds,
                                            FlashAnimationTimeline.Frame frame,
                                            float sourceX, float sourceY,
                                            int islandPlayerId, int clipFrameIndex,
                                            FlashColorTransform tint, boolean tinted,
                                            float parentAlpha, int depth) {
        if (frame == null || parentAlpha <= 0f || depth > MAX_SATELLITE_CLIP_DEPTH) {
            return;
        }
        float mapScale = BattlefieldCityLayout.sourceScale(mapBounds);
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            float layerAlpha = parentAlpha * layer.alpha;
            if (layerAlpha <= 0f) {
                continue;
            }
            TextureRegion region = assets.getShape(layer.shapeId);
            if (region != null) {
                float width = region.getRegionWidth() * mapScale;
                float height = region.getRegionHeight() * mapScale;
                float x = BattlefieldCityLayout.sourceX(mapBounds,
                        sourceX + layer.tx, islandPlayerId);
                float top = BattlefieldCityLayout.sourceY(mapBounds, sourceY + layer.ty);
                FlashSpriteTransform transform = FlashSpriteTransform.fromMatrix(
                        layer.a, layer.b, layer.c, layer.d);
                setSubmarineLayerColor(batch, tint, tinted, layerAlpha);
                batch.draw(region, x, top - height, 0f, height, width, height,
                        transform.scaleX, transform.scaleY, transform.rotationDegrees);
                continue;
            }

            FlashAnimationTimeline nested = assets.getTimelineBySymbolId(layer.shapeId);
            if (nested == null || nested.getFrameCount() == 0) {
                continue;
            }
            boolean tintChild = tinted || "tintMC".equals(layer.name);
            if (tintChild && !tinted) {
                beginSubmarineTint(batch, tint);
            }
            int nestedFrameIndex = Math.min(clipFrameIndex, nested.getFrameCount() - 1);
            drawSubmarineTimelineFrame(batch, mapBounds, nested.getFrame(nestedFrameIndex),
                    sourceX + layer.tx, sourceY + layer.ty, islandPlayerId,
                    clipFrameIndex, tint, tintChild, layerAlpha, depth + 1);
            if (tintChild && !tinted) {
                batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
                batch.setShader(null);
            }
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    private void beginSubmarineTint(SpriteBatch batch, FlashColorTransform tint) {
        ensureSubmarineTintShader();
        if (submarineTintShader == null) {
            return;
        }
        batch.setShader(submarineTintShader);
        submarineTintShader.setUniformf("u_flashMultipliers", tint.redMultiplier,
                tint.greenMultiplier, tint.blueMultiplier, tint.alphaMultiplier);
        submarineTintShader.setUniformf("u_flashAdditions", tint.redAdd,
                tint.greenAdd, tint.blueAdd, tint.alphaAdd);
    }

    private void setSubmarineLayerColor(SpriteBatch batch, FlashColorTransform tint,
                                        boolean tinted, float alpha) {
        if (!tinted) {
            batch.setColor(1f, 1f, 1f, alpha);
            return;
        }
        if (submarineTintShader != null) {
            batch.setColor(1f, 1f, 1f, alpha);
            return;
        }
        batch.setColor(approximateTint(tint.redMultiplier, tint.redAdd),
                approximateTint(tint.greenMultiplier, tint.greenAdd),
                approximateTint(tint.blueMultiplier, tint.blueAdd), alpha);
    }

    private void ensureSubmarineTintShader() {
        if (submarineTintShaderChecked) {
            return;
        }
        submarineTintShaderChecked = true;
        ShaderProgram candidate = new ShaderProgram(SUBMARINE_TINT_VERTEX_SHADER,
                SUBMARINE_TINT_FRAGMENT_SHADER);
        if (candidate.isCompiled()) {
            submarineTintShader = candidate;
        } else {
            if (Gdx.app != null) {
                Gdx.app.error("FlashBattleUnitRenderer",
                        "Submarine tint shader failed to compile: " + candidate.getLog());
            }
            candidate.dispose();
        }
    }

    private static float approximateTint(float multiplier, float addition) {
        return Math.max(0f, Math.min(1f, multiplier * 0.5f + addition));
    }

    private boolean findSatelliteLaserOrigin(FlashSatelliteAnimation animation,
                                             float[] origin) {
        FlashBattleRoutes.SatelliteStrike route = animation.getRoute();
        FlashAnimationTimeline satellite = assets.getTimeline("satellite");
        if (satellite == null) {
            return false;
        }
        FlashAnimationTimeline.Frame religionFrame =
                satellite.getFrameByLabel(route.religionCode);
        if (religionFrame == null) {
            return false;
        }
        for (FlashAnimationTimeline.Layer clipLayer : religionFrame.layers) {
            FlashAnimationTimeline clip = assets.getTimelineBySymbolId(clipLayer.shapeId);
            if (clip == null || clip.getFrameCount() == 0) {
                continue;
            }
            int clipFrameIndex = Math.min(animation.getSourceClipFrameNumber() - 1,
                    clip.getFrameCount() - 1);
            FlashAnimationTimeline.Frame frame = clip.getFrame(clipFrameIndex);
            if (frame == null) {
                continue;
            }
            for (FlashAnimationTimeline.Layer marker : frame.layers) {
                if ("laserStartMC".equals(marker.name)) {
                    origin[0] = clipLayer.tx;
                    origin[1] = clipLayer.ty;
                    origin[2] = marker.tx;
                    origin[3] = marker.ty;
                    return true;
                }
            }
        }
        return false;
    }

    private static int[][] buildSatelliteLaserColors() {
        int[][] colorsByPlayer = new int[SATELLITE_BASE_COLOR_HEX.length][8];
        for (int playerColor = 0; playerColor < colorsByPlayer.length; playerColor++) {
            String firstHex = brightenHexBySteps(SATELLITE_BASE_COLOR_HEX[playerColor], 2);
            String secondHex = brightenHexBySteps(firstHex, 2);
            String thirdHex = brightenHexBySteps(secondHex, 3);
            int first = rgbFromFlashHex(firstHex);
            int second = rgbFromFlashHex(secondHex);
            int third = rgbFromFlashHex(thirdHex);
            colorsByPlayer[playerColor] = new int[]{first, first, second, third,
                    third, 0xFFFFFF, 0xFFFFFF, 0xFFFFFF};
        }
        return colorsByPlayer;
    }

    private static String brightenHexBySteps(String color, int steps) {
        String result = color;
        for (int step = 0; step < steps; step++) {
            StringBuilder brighter = new StringBuilder("0x");
            for (int position = 2; position < 9; position++) {
                char digit = position < result.length() ? result.charAt(position) : '\0';
                if (digit == '\0') {
                    // ActionScript Number("") is zero; the source appends "1" here.
                    brighter.append('1');
                } else if (digit >= '0' && digit < '9') {
                    brighter.append((char) (digit + 1));
                } else if (digit == '9') {
                    brighter.append('A');
                } else if (digit >= 'A' && digit < 'F') {
                    brighter.append((char) (digit + 1));
                } else {
                    brighter.append(digit);
                }
            }
            result = brighter.toString();
        }
        return result;
    }

    static int sourceSatelliteLaserColor(int playerColor, int paletteIndex) {
        if (playerColor < 1 || playerColor >= SATELLITE_LASER_COLORS.length
                || paletteIndex < 0 || paletteIndex >= 8) {
            throw new IllegalArgumentException("Invalid source satellite laser color index");
        }
        return SATELLITE_LASER_COLORS[playerColor][paletteIndex];
    }

    private static int rgbFromFlashHex(String color) {
        return (int) (Long.parseLong(color.substring(2), 16) & 0xFFFFFFL);
    }

    private static void drawRocketSprite(SpriteBatch batch, Rectangle mapBounds,
                                         TextureRegion region, float sourceX,
                                         float sourceY, int fromPlayerId, int toPlayerId,
                                         float progress, float rotationDegrees, float alpha,
                                         float mapScale, float scaleY) {
        float width = region.getRegionWidth() * mapScale;
        float height = region.getRegionHeight() * mapScale;
        float centerX = BattlefieldCityLayout.sourceXBetween(mapBounds, sourceX,
                fromPlayerId, toPlayerId, progress);
        float centerY = BattlefieldCityLayout.sourceY(mapBounds, sourceY);
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(region, centerX - width / 2f, centerY - height / 2f,
                width / 2f, height / 2f, width, height, 1f, scaleY, rotationDegrees);
    }

    private static float rocketProgress(FlashBattleRoutes.RocketStrike route,
                                        float sourceX) {
        float horizontalDistance = route.target.x - route.launch.x;
        if (horizontalDistance == 0f) {
            return 0f;
        }
        return (sourceX - route.launch.x) / horizontalDistance;
    }

    private void drawActor(SpriteBatch batch, Rectangle mapBounds, String linkage,
                           int sourceFrame, float sourceX, float sourceY,
                           int islandPlayerId, float actorScale, float actorAlpha,
                           boolean visible) {
        if (!visible) {
            return;
        }
        FlashAnimationTimeline timeline = assets.getTimeline(linkage);
        if (timeline == null) {
            return;
        }
        drawTimelineFrame(batch, mapBounds, timeline.getFrame(sourceFrame), sourceX,
                sourceY, islandPlayerId, actorScale, actorAlpha);
    }

    private void drawTimelineFrame(SpriteBatch batch, Rectangle mapBounds,
                                   FlashAnimationTimeline.Frame frame,
                                   float sourceX, float sourceY, int islandPlayerId,
                                   float actorScale, float actorAlpha) {
        if (frame == null) {
            return;
        }
        float mapScale = BattlefieldCityLayout.sourceScale(mapBounds);
        batch.setColor(1f, 1f, 1f, actorAlpha);
        for (FlashAnimationTimeline.Layer layer : frame.layers) {
            TextureRegion region = assets.getShape(layer.shapeId);
            if (region == null) {
                continue;
            }
            float width = region.getRegionWidth() * mapScale;
            float height = region.getRegionHeight() * mapScale;
            float x = BattlefieldCityLayout.sourceX(mapBounds,
                    sourceX + layer.tx, islandPlayerId);
            float top = BattlefieldCityLayout.sourceY(mapBounds, sourceY + layer.ty);
            FlashSpriteTransform transform = FlashSpriteTransform.fromMatrix(
                    layer.a, layer.b, layer.c, layer.d);
            batch.draw(region, x, top - height, 0f, height, width, height,
                    transform.scaleX * actorScale, transform.scaleY * actorScale,
                    transform.rotationDegrees);
        }
        batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

}
