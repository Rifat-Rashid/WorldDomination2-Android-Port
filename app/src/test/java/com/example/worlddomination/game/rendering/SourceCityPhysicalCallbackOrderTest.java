package com.example.worlddomination.game.rendering;

import com.example.worlddomination.game.rendering.BattlefieldDisplayState.Kind;
import com.example.worlddomination.game.simulation.BiologicalBattleModelTest;
import com.example.worlddomination.game.simulation.GameState;
import com.example.worlddomination.game.simulation.ReferenceRandom;

import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

/** The host snapshots physical preorder before Unit callbacks can swap or attach clips. */
public final class SourceCityPhysicalCallbackOrderTest {
    @Test
    public void swapsAfterCaptureCannotReorderTwoCompetingCloudRespawns() throws Exception {
        Fixture fixture = new Fixture();
        // Player 1 city 7 is physically before cities 5 and 6 despite its larger ID.
        fixture.display.attach(Kind.CITY, 5, "city5", null, 50);
        fixture.display.attach(Kind.CITY, 7, "city7", null, 10);
        fixture.clouds.createSourceCloud(5, 0, fixture.random, false);
        fixture.clouds.createSourceCloud(7, 0, fixture.random, false);
        BioloadCloudAnimation five = fixture.city.getSourceCloudCohort(5, 0);
        BioloadCloudAnimation seven = fixture.city.getSourceCloudCohort(7, 0);
        setFirstAlphaToZero(five);
        setFirstAlphaToZero(seven);
        ReferenceRandom expected = fixture.random.copy();
        CloudDraw first = CloudDraw.next(expected);
        CloudDraw second = CloudDraw.next(expected);

        fixture.city.captureSourceCityFrame(fixture.display);
        fixture.display.clips().swapDepths(fixture.display.clips().atDepth(10), 60);
        fixture.city.tickSourceCities(fixture.game);
        first.assertOn(seven);
        second.assertOn(five);
        assertEquals(expected.nextDouble(), fixture.random.nextDouble(), 0d);

        // A fresh capture sees the moved depth and therefore reverses this order.
        setFirstAlphaToZero(five);
        setFirstAlphaToZero(seven);
        expected = fixture.random.copy();
        first = CloudDraw.next(expected);
        second = CloudDraw.next(expected);
        fixture.city.captureSourceCityFrame(fixture.display);
        fixture.city.tickSourceCities(fixture.game);
        first.assertOn(five);
        second.assertOn(seven);
        assertEquals(expected.nextDouble(), fixture.random.nextDouble(), 0d);
    }

    @Test
    public void detachedClipIsSkippedAndNewClipWaitsUntilNextCapture() throws Exception {
        Fixture fixture = new Fixture();
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> five =
                fixture.display.attach(Kind.CITY, 5, "city5", null, 50);
        SourceDisplayList.Entry<BattlefieldDisplayState.Item> seven =
                fixture.display.attach(Kind.CITY, 7, "city7", null, 10);
        fixture.clouds.createSourceCloud(5, 0, fixture.random, false);
        fixture.clouds.createSourceCloud(7, 0, fixture.random, false);
        fixture.clouds.createSourceCloud(6, 0, fixture.random, false);
        BioloadCloudAnimation cloudFive = fixture.city.getSourceCloudCohort(5, 0);
        BioloadCloudAnimation cloudSeven = fixture.city.getSourceCloudCohort(7, 0);
        BioloadCloudAnimation cloudSix = fixture.city.getSourceCloudCohort(6, 0);
        setFirstAlphaToZero(cloudFive);
        setFirstAlphaToZero(cloudSeven);
        setFirstAlphaToZero(cloudSix);
        double sevenBefore = cloudSeven.getSourceX(0);
        double sixBefore = cloudSix.getSourceX(0);
        fixture.city.captureSourceCityFrame(fixture.display);
        fixture.display.remove(seven);
        fixture.display.attach(Kind.CITY, 6, "city6", null, 1);

        ReferenceRandom expected = fixture.random.copy();
        CloudDraw fiveDraw = CloudDraw.next(expected);
        fixture.city.tickSourceCities(fixture.game);
        fiveDraw.assertOn(cloudFive);
        assertEquals(sevenBefore, cloudSeven.getSourceX(0), 0d);
        assertEquals(sixBefore, cloudSix.getSourceX(0), 0d);
        assertEquals(expected.nextDouble(), fixture.random.nextDouble(), 0d);

        // A new frame's snapshot includes city 6; city 7 remains detached.
        fixture.city.captureSourceCityFrame(fixture.display);
        expected = fixture.random.copy();
        CloudDraw sixDraw = CloudDraw.next(expected);
        fixture.city.tickSourceCities(fixture.game);
        sixDraw.assertOn(cloudSix);
        assertEquals(sevenBefore, cloudSeven.getSourceX(0), 0d);
        assertEquals(expected.nextDouble(), fixture.random.nextDouble(), 0d);
        assertEquals(50, five.getDepth());
    }

    private static void setFirstAlphaToZero(BioloadCloudAnimation cloud) throws Exception {
        Field array = BioloadCloudAnimation.class.getDeclaredField("sourceClouds");
        array.setAccessible(true);
        Object first = ((Object[]) array.get(cloud))[0];
        Field alpha = first.getClass().getDeclaredField("alphaFactor");
        alpha.setAccessible(true);
        alpha.setDouble(first, 0d);
    }

    private static final class Fixture {
        final GameState game = BiologicalBattleModelTest.legalSeventeenTurnBioLaunchQueued();
        final BattlefieldCityRenderer city = new BattlefieldCityRenderer(null, null);
        final BattlefieldDisplayState display = new BattlefieldDisplayState();
        final ReferenceRandom random = new ReferenceRandom(0L);
        final CityBioloadCloudRenderer clouds;

        Fixture() throws Exception {
            game.confirmTurn();
            city.reset(game);
            city.beginBattle(game);
            city.beginSourceBiologicalPlayback(game, random);
            Field field = BattlefieldCityRenderer.class.getDeclaredField("bioloadCloudRenderer");
            field.setAccessible(true);
            clouds = (CityBioloadCloudRenderer) field.get(city);
        }
    }

    /** Seven independent source Tools.randRange draws, kept outside the cloud implementation. */
    private static final class CloudDraw {
        final int x, y, alpha, scale, alphaDecay, sizeDecay, horizontalOffset;

        CloudDraw(int x, int y, int alpha, int scale, int alphaDecay,
                  int sizeDecay, int horizontalOffset) {
            this.x = x;
            this.y = y;
            this.alpha = alpha;
            this.scale = scale;
            this.alphaDecay = alphaDecay;
            this.sizeDecay = sizeDecay;
            this.horizontalOffset = horizontalOffset;
        }

        static CloudDraw next(ReferenceRandom random) {
            return new CloudDraw(-25 + random.nextInt(51), -20 + random.nextInt(21),
                    30 + random.nextInt(21), 40 + random.nextInt(41),
                    1 + random.nextInt(3), 2 + random.nextInt(14),
                    -10 + random.nextInt(21));
        }

        void assertOn(BioloadCloudAnimation cloud) {
            assertEquals(x, cloud.getSourceX(0), 0d);
            assertEquals(y, cloud.getSourceY(0), 0d);
            assertEquals(alpha, cloud.getSourceAlpha(0), 1e-10);
            assertEquals(scale, cloud.getSourceScalePercent(0), 1e-10);
            assertEquals(alphaDecay, cloud.getSourceAlphaDecay(0));
            assertEquals(sizeDecay, cloud.getSourceSizeDecay(0));
            assertEquals(horizontalOffset, cloud.getSourceHorizontalTicks(0));
        }
    }
}
