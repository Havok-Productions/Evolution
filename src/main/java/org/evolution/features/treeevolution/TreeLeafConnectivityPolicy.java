package org.evolution.features.treeevolution;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * ## Pure leaf-to-wood graph policy shared by live audits and smoke checks.
 */
final class TreeLeafConnectivityPolicy {
    static final int[][] NEIGHBOR_OFFSETS = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };

    private TreeLeafConnectivityPolicy() {
    }

    static Set<String> connectedLeaves(
            Set<String> leaves,
            Set<String> wood
    ) {
        Set<String> connected = new HashSet<>();
        ArrayDeque<String> frontier = new ArrayDeque<>();
        for (String leaf : leaves) {
            Coordinate coordinate = decode(leaf);
            if (coordinate != null && touchesAny(coordinate, wood)) {
                connected.add(leaf);
                frontier.addLast(leaf);
            }
        }

        while (!frontier.isEmpty()) {
            Coordinate coordinate = decode(frontier.removeFirst());
            if (coordinate == null) {
                continue;
            }
            for (int[] offset : NEIGHBOR_OFFSETS) {
                String neighbor = key(
                        coordinate.x() + offset[0],
                        coordinate.y() + offset[1],
                        coordinate.z() + offset[2]);
                if (leaves.contains(neighbor) && connected.add(neighbor)) {
                    frontier.addLast(neighbor);
                }
            }
        }
        return Set.copyOf(connected);
    }

    private static boolean touchesAny(Coordinate coordinate, Set<String> keys) {
        for (int[] offset : NEIGHBOR_OFFSETS) {
            if (keys.contains(key(
                    coordinate.x() + offset[0],
                    coordinate.y() + offset[1],
                    coordinate.z() + offset[2]))) {
                return true;
            }
        }
        return false;
    }

    private static Coordinate decode(String key) {
        String[] parts = key.split(":");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new Coordinate(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private record Coordinate(int x, int y, int z) {
    }
}
