package org.evolution.features.treeevolution;

import java.util.Map;
import java.util.function.Predicate;
import org.bukkit.Material;

final class TreeCanopyIntegrityPolicy {
    private static final int[][] NEIGHBOR_OFFSETS = {
            {0, 1, 0},
            {0, -1, 0},
            {1, 0, 0},
            {-1, 0, 0},
            {0, 0, 1},
            {0, 0, -1}
    };

    private TreeCanopyIntegrityPolicy() {
    }

    static boolean requiresCanopyCover(int x, int y, int z,
            Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey) {
        return requiresCanopyCover(
                x, y, z, leafMaterial, blocksByKey, ignored -> true);
    }

    static boolean requiresCanopyCover(int x, int y, int z,
            Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey,
            Predicate<PlannedTreeBlock> repairableTarget) {
        for (int[] offset : NEIGHBOR_OFFSETS) {
            PlannedTreeBlock planned = blocksByKey.get(
                    key(x + offset[0], y + offset[1], z + offset[2]));
            if (planned != null
                    && planned.role() == TreeBlockRole.CANOPY
                    && planned.material() == leafMaterial
                    && repairableTarget.test(planned)) {
                return true;
            }
        }
        return false;
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}
