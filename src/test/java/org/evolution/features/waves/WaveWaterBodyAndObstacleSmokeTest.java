package org.evolution.features.waves;

import java.util.UUID;

public final class WaveWaterBodyAndObstacleSmokeTest {
    private WaveWaterBodyAndObstacleSmokeTest() {
    }

    public static void main(String[] args) {
        WaveWaterBodyPolicy.Classification lake = WaveWaterBodyPolicy.classify(
                new LakeWaveFlowField.Cell(true, true, 12, 30, 1, 0), 61);
        require(lake.body() == WaveWaterBodyPolicy.Body.ENCLOSED_LAKE,
                "wide enclosed water must remain a lake");
        require(lake.permittedKind(TravelingWaveFront.Kind.GIANT)
                        == TravelingWaveFront.Kind.STANDARD,
                "lakes must downgrade giant fronts");
        require(lake.fitHalfWidth(38.0D) <= 15.0D,
                "lake fronts must receive a bounded width");

        WaveWaterBodyPolicy.Classification coast = WaveWaterBodyPolicy.classify(
                new LakeWaveFlowField.Cell(true, false, 18, 90, 1, 0), 96);
        require(coast.body() == WaveWaterBodyPolicy.Body.OPEN_COAST,
                "deep unbounded coastal water must classify as open coast");
        require(coast.permittedKind(TravelingWaveFront.Kind.GIANT)
                        == TravelingWaveFront.Kind.GIANT,
                "open coasts may retain giant fronts");

        WaveWaterBodyPolicy.Classification river = WaveWaterBodyPolicy.classify(
                new LakeWaveFlowField.Cell(true, false, 6, 80, 1, 0), 14);
        require(river.channelLocked() && river.fitHalfWidth(30.0D) <= 3.5D,
                "narrow rivers must use minute stable fronts even when long");

        WaveLakeFlowCache.Snapshot smallIsland = topologyWithBarrier(0, 2);
        TravelingWaveFront front = new TravelingWaveFront(
                91L, TravelingWaveFront.Kind.STANDARD, 0.0D,
                -5.0D, 0.0D, 1.0D, 0.0D,
                12.0D, 12.0D, 0.90D, 0L);
        WaveObstaclePolicy.Crossing crossing = WaveObstaclePolicy.findCrossing(
                smallIsland, front.x(), front.z(), 1.0D, 0.0D, front);
        require(crossing.crosses() && crossing.landCells() == 2,
                "a two-block island must preserve the front beyond it");
        front.beginObstaclePassage(crossing.landCells(), crossing.travelDistance(),
                1.0D, 0.0D, crossing.energyScale());
        require(front.obstaclePassageActive() && front.obstacleEnergyScale() < 1.0D,
                "crossing an obstacle must retain motion with reduced energy");

        for (long tick = 5L; tick <= 100L; tick += 5L) {
            TravelingWaveFront.Motion motion = front.prepareMotion(
                    tick, 2.0D, 1.0D, 0.0D, false);
            front.commitMotion(motion);
        }
        require(front.x() > crossing.resumeX() && !front.obstaclePassageActive(),
                "the stable front must emerge beyond the island before unlocking");
        WaveLakeFlowCache.Snapshot largeCoast = topologyWithBarrier(0, 11);
        TravelingWaveFront coastFront = new TravelingWaveFront(
                92L, TravelingWaveFront.Kind.STANDARD, 0.0D,
                -5.0D, 0.0D, 1.0D, 0.0D,
                12.0D, 12.0D, 0.90D, 0L);
        require(!WaveObstaclePolicy.findCrossing(
                        largeCoast, coastFront.x(), coastFront.z(),
                        1.0D, 0.0D, coastFront).crosses(),
                "a land mass wider than the front bridge limit must remain a coast");

        System.out.println("Wave body/obstacle smoke test passed: lake-cap="
                + lake.fitHalfWidth(38.0D)
                + " river-cap=" + river.fitHalfWidth(30.0D)
                + " obstacle-energy=" + front.obstacleEnergyScale());
    }

    private static WaveLakeFlowCache.Snapshot topologyWithBarrier(
            int barrierStartX, int barrierWidth) {
        int radius = 20;
        int diameter = (radius * 2) + 1;
        boolean[] known = new boolean[diameter * diameter];
        boolean[] water = new boolean[diameter * diameter];
        int waterCells = 0;
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                int index = ((z + radius) * diameter) + x + radius;
                known[index] = true;
                water[index] = x < barrierStartX
                        || x >= barrierStartX + barrierWidth;
                waterCells += water[index] ? 1 : 0;
            }
        }
        LakeWaveFlowField field = LakeWaveFlowField.build(
                diameter, diameter, known, water);
        return new WaveLakeFlowCache.Snapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000091"),
                0, 0, radius, radius, 1, 0L,
                known.length, waterCells, field);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
