package org.evolution.features.waves;

import java.util.Arrays;
import java.util.UUID;

public final class WaveAngleDemandSmokeTest {
    private static final int RADIUS = 40;
    private static final int DIAMETER = (RADIUS * 2) + 1;
    private static final UUID WORLD_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000031");

    private WaveAngleDemandSmokeTest() {
    }

    public static void main(String[] args) {
        TravelingWaveRegistry registry = new TravelingWaveRegistry();

        TravelingWaveRegistry.ShoreAngleDecision lake = registry.shoreAngleDecision(
                circularLake(), 0, 0);
        require(lake.requiresFan(),
                "a lake center with several meaningful coast directions must split");
        require(lake.directionSectors() >= 2,
                "the split decision must be backed by multiple direction sectors");

        TravelingWaveRegistry.ShoreAngleDecision coast = registry.shoreAngleDecision(
                singleCoast(), 0, 0);
        require(!coast.requiresFan(),
                "one coherent shoreline direction must preserve the broad wave");
        require("coherent-single-direction".equals(coast.reason()),
                "a known straight coast must not be reported as uncertain");

        TravelingWaveRegistry.ShoreAngleDecision shoreless = registry.shoreAngleDecision(
                shorelessWater(), 0, 0);
        require(!shoreless.requiresFan(),
                "water without enough shore evidence must preserve the broad wave");
        require("insufficient-topology".equals(shoreless.reason()),
                "missing shore directions must be reported as insufficient topology");

        System.out.println("Wave angle-demand smoke test passed: lake-sectors="
                + lake.directionSectors() + " lake-coherence=" + round(lake.coherence())
                + " coast-sectors=" + coast.directionSectors()
                + " coast-coherence=" + round(coast.coherence())
                + " shoreless-reason=" + shoreless.reason());
    }

    private static WaveLakeFlowCache.Snapshot circularLake() {
        boolean[] known = new boolean[DIAMETER * DIAMETER];
        boolean[] water = new boolean[known.length];
        Arrays.fill(known, true);
        for (int z = -RADIUS; z <= RADIUS; z++) {
            for (int x = -RADIUS; x <= RADIUS; x++) {
                water[index(x, z)] = (x * x) + (z * z) <= 30 * 30;
            }
        }
        return snapshot(known, water);
    }

    private static WaveLakeFlowCache.Snapshot singleCoast() {
        boolean[] known = new boolean[DIAMETER * DIAMETER];
        boolean[] water = new boolean[known.length];
        Arrays.fill(known, true);
        for (int z = -RADIUS; z <= RADIUS; z++) {
            for (int x = -RADIUS; x <= RADIUS; x++) {
                water[index(x, z)] = x <= 28;
            }
        }
        return snapshot(known, water);
    }

    private static WaveLakeFlowCache.Snapshot shorelessWater() {
        boolean[] known = new boolean[DIAMETER * DIAMETER];
        boolean[] water = new boolean[known.length];
        Arrays.fill(known, true);
        Arrays.fill(water, true);
        return snapshot(known, water);
    }

    private static WaveLakeFlowCache.Snapshot snapshot(
            boolean[] known, boolean[] water) {
        int waterCells = 0;
        for (boolean cell : water) {
            waterCells += cell ? 1 : 0;
        }
        LakeWaveFlowField field = LakeWaveFlowField.build(
                DIAMETER, DIAMETER, known, water);
        return new WaveLakeFlowCache.Snapshot(WORLD_ID, 0, 0,
                RADIUS, RADIUS, 1, 0L, known.length, waterCells, field);
    }

    private static int index(int x, int z) {
        return ((z + RADIUS) * DIAMETER) + x + RADIUS;
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
