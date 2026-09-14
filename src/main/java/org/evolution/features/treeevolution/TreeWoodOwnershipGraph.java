package org.evolution.features.treeevolution;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolves rooted wood ownership without walking through leaf connections.
 */
final class TreeWoodOwnershipGraph {
    private TreeWoodOwnershipGraph() {
    }

    static Set<String> connectedToRoot(
            TreeDna dna, Collection<String> woodKeys) {
        Map<Coordinate, String> indexed = new HashMap<>();
        for (String key : woodKeys) {
            Coordinate coordinate = coordinate(key);
            if (coordinate != null) {
                indexed.put(coordinate, key);
            }
        }
        Coordinate root = new Coordinate(
                dna.baseX(), dna.baseY(), dna.baseZ());
        if (!indexed.containsKey(root)) {
            return Set.of();
        }

        Set<Coordinate> visited = new HashSet<>();
        ArrayDeque<Coordinate> pending = new ArrayDeque<>();
        visited.add(root);
        pending.add(root);
        while (!pending.isEmpty()) {
            Coordinate current = pending.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Coordinate next = new Coordinate(
                                current.x() + dx,
                                current.y() + dy,
                                current.z() + dz);
                        if (indexed.containsKey(next)
                                && visited.add(next)) {
                            pending.addLast(next);
                        }
                    }
                }
            }
        }

        Set<String> connected = new HashSet<>();
        for (Coordinate coordinate : visited) {
            connected.add(indexed.get(coordinate));
        }
        return Set.copyOf(connected);
    }

    private static Coordinate coordinate(String key) {
        String[] parts = key.split(":");
        if (parts.length < 4) {
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
