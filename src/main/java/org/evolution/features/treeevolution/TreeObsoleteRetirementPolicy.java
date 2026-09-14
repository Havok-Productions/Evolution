package org.evolution.features.treeevolution;

import java.util.Comparator;

/**
 * ## Orders obsolete tree receipts from crown tips back toward the stump.
 *
 * <p>Coordinate-text ordering can remove a branch parent before its child.
 * Retiring the farthest/highest owned voxel first mirrors pruning from the
 * outside inward and preserves rooted support throughout a transition.
 */
final class TreeObsoleteRetirementPolicy {
    private TreeObsoleteRetirementPolicy() {
    }

    static Comparator<String> outermostFirst(TreeDna dna) {
        return Comparator
                .comparingLong((String key) -> distanceScore(dna, key))
                .reversed()
                .thenComparing(Comparator.reverseOrder());
    }

    private static long distanceScore(TreeDna dna, String key) {
        Coordinate coordinate = coordinate(key);
        if (coordinate == null) {
            return Long.MIN_VALUE;
        }
        long dx = coordinate.x() - dna.baseX();
        long dy = coordinate.y() - dna.baseY();
        long dz = coordinate.z() - dna.baseZ();
        // ## Height receives extra weight because a vertical trunk segment
        // supports everything above it; horizontal distance then separates
        // parent/child positions along a branch at the same level.
        return (dy * dy * 4L) + (dx * dx) + (dz * dz);
    }

    private static Coordinate coordinate(String key) {
        String[] parts = key.split(":");
        if (parts.length < 3) {
            return null;
        }
        try {
            int offset = parts.length - 3;
            return new Coordinate(
                    Integer.parseInt(parts[offset]),
                    Integer.parseInt(parts[offset + 1]),
                    Integer.parseInt(parts[offset + 2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record Coordinate(int x, int y, int z) {
    }
}
