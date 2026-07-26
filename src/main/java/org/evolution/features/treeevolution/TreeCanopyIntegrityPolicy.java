package org.evolution.features.treeevolution;

import java.util.Map;
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
        for (int[] offset : NEIGHBOR_OFFSETS) {
            PlannedTreeBlock planned = blocksByKey.get(
                    key(x + offset[0], y + offset[1], z + offset[2]));
            if (planned != null
                    && planned.role() == TreeBlockRole.CANOPY
                    && planned.material() == leafMaterial) {
                return true;
            }
        }
        return false;
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}
