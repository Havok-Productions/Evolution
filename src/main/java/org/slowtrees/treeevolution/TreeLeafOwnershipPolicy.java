package org.slowtrees.treeevolution;

import java.util.Collection;

final class TreeLeafOwnershipPolicy {
    private TreeLeafOwnershipPolicy() {
    }

    static boolean belongsToActiveTree(int leafX, int leafZ,
            int activeX, int activeZ, Collection<Column> foreignTrunks) {
        int activeDistance = horizontalDistance(leafX, leafZ, activeX, activeZ);
        int foreignDistance = Integer.MAX_VALUE;
        for (Column foreign : foreignTrunks) {
            foreignDistance = Math.min(foreignDistance,
                    horizontalDistance(leafX, leafZ, foreign.x(), foreign.z()));
        }
        // ## Ties stay untouched. Shared canopy edges are valid and neither tree
        // should claim the right to prune them.
        return activeDistance < foreignDistance;
    }

    static boolean isActiveTrunkColumn(TreeSpecies species,
            int activeX, int activeZ, Column column) {
        int radius = switch (species) {
            case JUNGLE, DARK_OAK, MANGROVE -> 2;
            default -> 1;
        };
        return Math.max(Math.abs(column.x() - activeX),
                Math.abs(column.z() - activeZ)) <= radius;
    }

    static boolean neighborPlanOwnsPosition(int x, int z,
            int activeX, int activeZ, int neighborX, int neighborZ) {
        // ## A neighboring future canopy protects only its side of the shared
        // boundary. It cannot reserve stale leaves closer to the active trunk.
        return horizontalDistance(x, z, neighborX, neighborZ)
                <= horizontalDistance(x, z, activeX, activeZ);
    }

    private static int horizontalDistance(int firstX, int firstZ,
            int secondX, int secondZ) {
        return Math.abs(firstX - secondX) + Math.abs(firstZ - secondZ);
    }

    record Column(int x, int z) {
    }
}
