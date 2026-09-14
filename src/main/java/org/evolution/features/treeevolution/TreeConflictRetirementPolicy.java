package org.evolution.features.treeevolution;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ## Retires a role-conflicting articulation subtree from its outside inward.
 *
 * <p>A wood voxel whose new target is canopy may still support old evolved
 * branches. Removing that connector first creates a floating intermediate
 * tree. This policy removes dependent canopy, then dependent outer wood, and
 * only then releases the conflicting connector for its new target role.</p>
 */
final class TreeConflictRetirementPolicy {
    private TreeConflictRetirementPolicy() {
    }

    static Optional<String> next(
            TreeDna dna,
            Collection<String> liveWood,
            Collection<String> evolvedWood,
            Collection<String> evolvedCanopy,
            Collection<String> protectedCurrentTargets,
            String conflict
    ) {
        if (conflict == null || conflict.isBlank()) {
            return Optional.empty();
        }
        Set<String> woodWithoutConflict = new HashSet<>(liveWood);
        woodWithoutConflict.remove(conflict);
        Set<String> rooted = TreeWoodOwnershipGraph.connectedToRoot(
                dna, woodWithoutConflict);
        Set<String> dependentLiveWood = new HashSet<>(
                woodWithoutConflict);
        dependentLiveWood.removeAll(rooted);
        if (!new HashSet<>(evolvedWood)
                .containsAll(dependentLiveWood)) {
            // ## Source or protected wood may never be sacrificed merely to
            // clear this tree's role conflict. Leave the conflict blocked for
            // ownership/path repair instead of disconnecting foreign support.
            return Optional.empty();
        }

        Set<String> protectedTargets = new HashSet<>(
                protectedCurrentTargets);
        boolean protectedWoodWouldDisconnect = dependentLiveWood.stream()
                .anyMatch(protectedTargets::contains);
        if (protectedWoodWouldDisconnect) {
            // ## The conflict exists, but it is not actionable yet. Returning
            // empty lets the normal branch-frame constructor place the
            // current plan's alternate rootward path before cleanup retries.
            return Optional.empty();
        }
        Optional<String> dependentCanopy = unsupportedCanopy(
                rooted, evolvedCanopy).stream()
                // ## A dependency can be structurally inconvenient without
                // becoming obsolete. Current-plan voxels stay in place; the
                // role-conflicting connector is released and normal target
                // repair reconnects the retained structure afterward.
                .filter(key -> !protectedTargets.contains(key))
                .sorted(TreeObsoleteRetirementPolicy
                        .outermostFirst(dna))
                .findFirst();
        if (dependentCanopy.isPresent()) {
            return dependentCanopy;
        }
        Optional<String> dependentWood = dependentLiveWood.stream()
                .filter(key -> !key.equals(conflict))
                .filter(key -> !protectedTargets.contains(key))
                .sorted(TreeObsoleteRetirementPolicy
                        .outermostFirst(dna))
                .findFirst();
        return dependentWood.isPresent()
                ? dependentWood : Optional.of(conflict);
    }

    private static Set<String> unsupportedCanopy(
            Set<String> rootedWood,
            Collection<String> evolvedCanopy
    ) {
        Map<Coordinate, String> canopy = index(evolvedCanopy);
        Set<Coordinate> rootedCoordinates = index(rootedWood).keySet();
        Set<Coordinate> supported = new HashSet<>();
        ArrayDeque<Coordinate> pending = new ArrayDeque<>();
        for (Coordinate leaf : canopy.keySet()) {
            if (touches(leaf, rootedCoordinates)) {
                supported.add(leaf);
                pending.addLast(leaf);
            }
        }
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
                        if (canopy.containsKey(next)
                                && supported.add(next)) {
                            pending.addLast(next);
                        }
                    }
                }
            }
        }
        Set<String> unsupported = new HashSet<>();
        for (Map.Entry<Coordinate, String> entry : canopy.entrySet()) {
            if (!supported.contains(entry.getKey())) {
                unsupported.add(entry.getValue());
            }
        }
        return Set.copyOf(unsupported);
    }

    private static boolean touches(
            Coordinate source, Set<Coordinate> candidates) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    if (candidates.contains(new Coordinate(
                            source.x() + dx,
                            source.y() + dy,
                            source.z() + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Map<Coordinate, String> index(
            Collection<String> keys) {
        Map<Coordinate, String> indexed = new HashMap<>();
        for (String key : keys) {
            Coordinate coordinate = coordinate(key);
            if (coordinate != null) {
                indexed.put(coordinate, key);
            }
        }
        return indexed;
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
