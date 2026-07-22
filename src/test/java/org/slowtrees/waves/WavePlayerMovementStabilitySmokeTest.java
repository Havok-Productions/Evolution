package org.slowtrees.waves;

import java.util.UUID;

public final class WavePlayerMovementStabilitySmokeTest {
    private WavePlayerMovementStabilitySmokeTest() {
    }

    public static void main(String[] args) {
        int step = 2;
        require(WaveLakeFlowCache.anchorToWorldGrid(17, step) == 16,
                "positive topology anchors must use the world lattice");
        require(WaveLakeFlowCache.anchorToWorldGrid(-1, step) == -2,
                "negative topology anchors must use floor division");
        require(WaveLakeFlowCache.advanceToWorldGrid(17, step) == 18,
                "render sampling must advance to the next world-lattice column");
        require(WaveLakeFlowCache.advanceToWorldGrid(-17, step) == -16,
                "negative render bounds must retain the same world-lattice phase");

        WaveLakeFlowCache.Snapshot first = snapshot(0, 0, 28, step);
        WaveLakeFlowCache.Snapshot moved = snapshot(10, -8, 28, step);
        int overlapChecks = 0;
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                require(first.isWater(x, z) == moved.isWater(x, z),
                        "walking must not change the water mask at world " + x + "," + z);
                overlapChecks++;
            }
        }

        System.out.println("Wave player-movement stability smoke test passed: overlap="
                + overlapChecks + " topology-step=" + step + " world-phase=stable");
    }

    private static WaveLakeFlowCache.Snapshot snapshot(
            int centerX, int centerZ, int radius, int step) {
        int anchoredX = WaveLakeFlowCache.anchorToWorldGrid(centerX, step);
        int anchoredZ = WaveLakeFlowCache.anchorToWorldGrid(centerZ, step);
        int gridRadius = radius / step;
        int diameter = (gridRadius * 2) + 1;
        boolean[] known = new boolean[diameter * diameter];
        boolean[] water = new boolean[diameter * diameter];
        int waterCells = 0;
        for (int localZ = 0; localZ < diameter; localZ++) {
            int worldZ = anchoredZ + ((localZ - gridRadius) * step);
            for (int localX = 0; localX < diameter; localX++) {
                int worldX = anchoredX + ((localX - gridRadius) * step);
                int index = (localZ * diameter) + localX;
                known[index] = true;
                water[index] = worldWater(worldX, worldZ);
                waterCells += water[index] ? 1 : 0;
            }
        }
        LakeWaveFlowField field = LakeWaveFlowField.build(
                diameter, diameter, known, water);
        return new WaveLakeFlowCache.Snapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                anchoredX, anchoredZ, radius, gridRadius, step, 0L,
                known.length, waterCells, field);
    }

    private static boolean worldWater(int x, int z) {
        int island = ((x - 4) * (x - 4)) + ((z + 2) * (z + 2));
        return island > 36 && z < 18 && x > -22;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
