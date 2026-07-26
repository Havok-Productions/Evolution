package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the bounded neighborhood used to identify one live tree.
 */
final class TreeGroupTraversalPolicy {
    private static final List<int[]> ORTHOGONAL = offsets(false);
    private static final List<int[]> FULL_CROWN = offsets(true);

    private TreeGroupTraversalPolicy() {
    }

    static List<int[]> neighborOffsets(boolean thoroughCapture) {
        return thoroughCapture ? FULL_CROWN : ORTHOGONAL;
    }

    static int maximumDistance(int normalMaximum, boolean thoroughCapture) {
        return thoroughCapture ? Math.max(normalMaximum, 48) : normalMaximum;
    }

    private static List<int[]> offsets(boolean includeDiagonals) {
        List<int[]> result = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    if (includeDiagonals
                            || Math.abs(x) + Math.abs(y) + Math.abs(z) == 1) {
                        result.add(new int[]{x, y, z});
                    }
                }
            }
        }
        return List.copyOf(result);
    }
}
