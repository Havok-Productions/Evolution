package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ## Reconciles a physically complete target with an incomplete DNA ledger.
 *
 * <p>A live target voxel can predate the current plan and therefore satisfy
 * material progress without ever receiving evolved ownership. This policy
 * grows the ownership graph from the stump one bridge at a time. It never
 * chooses neighboring-tree coordinates protected by another DNA plan.</p>
 */
final class TreeTargetOwnershipRepairPolicy {
    private TreeTargetOwnershipRepairPolicy() {
    }

    static Analysis inspect(
            TreeDna dna,
            CachedTreePlan plan,
            Set<String> liveOwnedWood,
            Set<String> livePlannedWood,
            Set<String> protectedCoordinates
    ) {
        return inspect(dna, plan.orderedBlocks(), liveOwnedWood,
                livePlannedWood, Set.of(), Set.of(),
                protectedCoordinates);
    }

    static Analysis inspect(
            TreeDna dna,
            List<PlannedTreeBlock> plannedBlocks,
            Set<String> liveOwnedWood,
            Set<String> livePlannedWood,
            Set<String> protectedCoordinates
    ) {
        return inspect(dna, plannedBlocks, liveOwnedWood,
                livePlannedWood, Set.of(), Set.of(),
                protectedCoordinates);
    }

    static Analysis inspect(
            TreeDna dna,
            List<PlannedTreeBlock> plannedBlocks,
            Set<String> liveOwnedWood,
            Set<String> livePlannedWood,
            Set<String> liveOwnedCanopy,
            Set<String> livePlannedCanopy,
            Set<String> protectedCoordinates
    ) {
        Analysis wood = inspectWood(
                dna, plannedBlocks, liveOwnedWood,
                livePlannedWood, protectedCoordinates);
        return wood.required() ? wood : inspectCanopy(
                dna, plannedBlocks, liveOwnedWood,
                liveOwnedCanopy, livePlannedCanopy,
                protectedCoordinates);
    }

    private static Analysis inspectWood(
            TreeDna dna,
            List<PlannedTreeBlock> plannedBlocks,
            Set<String> liveOwnedWood,
            Set<String> livePlannedWood,
            Set<String> protectedCoordinates
    ) {
        Set<String> rooted = TreeWoodOwnershipGraph.connectedToRoot(
                dna, liveOwnedWood);
        WoodConnectivityIndex connectivity =
                WoodConnectivityIndex.build(dna, liveOwnedWood, rooted);
        Set<String> targetWood = new HashSet<>();
        for (PlannedTreeBlock block : plannedBlocks) {
            if (isWood(block)) {
                targetWood.add(worldKey(dna, block.key()));
            }
        }
        List<String> orphans = dna.evolvedShapeLogs().stream()
                .filter(liveOwnedWood::contains)
                .filter(targetWood::contains)
                .filter(key -> !rooted.contains(key))
                .sorted(TreeObsoleteRetirementPolicy.outermostFirst(dna))
                .toList();
        if (orphans.isEmpty()) {
            return new Analysis(
                    List.of(), Optional.empty(), rooted.size(),
                    targetWood.size(),
                    Math.max(0, targetWood.size()
                            - liveOwnedWood.size()));
        }

        List<Repair> choices = new ArrayList<>();
        for (PlannedTreeBlock block : plannedBlocks) {
            if (!isWood(block)
                    || protectedCoordinates.contains(block.key())) {
                continue;
            }
            String key = worldKey(dna, block.key());
            if (liveOwnedWood.contains(key)) {
                continue;
            }
            // ## Evaluate the same one-block bridge result from one shared
            // connectivity decomposition. Rebuilding the complete wood graph
            // for every planned candidate made blocked trees quadratic.
            int rootedAfter = connectivity.rootedSizeAfterAdding(key);
            if (rootedAfter <= rooted.size()) {
                continue;
            }
            Action action = livePlannedWood.contains(key)
                    ? Action.ADOPT_LIVE_TARGET
                    : Action.PLACE_MISSING_TARGET;
            choices.add(new Repair(
                    block, action, orphans.getFirst(),
                    rooted.size(), rootedAfter));
        }
        Optional<Repair> selected = choices.stream()
                .sorted((left, right) -> {
                    int gain = Integer.compare(
                            right.rootedAfter() - right.rootedBefore(),
                            left.rootedAfter() - left.rootedBefore());
                    if (gain != 0) {
                        return gain;
                    }
                    int action = left.action().compareTo(right.action());
                    if (action != 0) {
                        return action;
                    }
                    return left.block().key().compareTo(
                            right.block().key());
                })
                .findFirst();
        return new Analysis(
                List.copyOf(orphans), selected, rooted.size(),
                targetWood.size(),
                Math.max(0, targetWood.size()
                        - liveOwnedWood.size()));
    }

    private static Analysis inspectCanopy(
            TreeDna dna,
            List<PlannedTreeBlock> plannedBlocks,
            Set<String> liveOwnedWood,
            Set<String> liveOwnedCanopy,
            Set<String> livePlannedCanopy,
            Set<String> protectedCoordinates
    ) {
        Set<String> rootedWood = TreeWoodOwnershipGraph.connectedToRoot(
                dna, liveOwnedWood);
        Set<String> supported = supportedCanopy(
                rootedWood, liveOwnedCanopy);
        Set<String> targetCanopy = new HashSet<>();
        for (PlannedTreeBlock block : plannedBlocks) {
            if (block.role() == TreeBlockRole.CANOPY) {
                targetCanopy.add(worldKey(dna, block.key()));
            }
        }
        List<String> orphans = dna.evolvedShapeLeaves().stream()
                .filter(liveOwnedCanopy::contains)
                .filter(targetCanopy::contains)
                .filter(key -> !supported.contains(key))
                .sorted(TreeObsoleteRetirementPolicy.outermostFirst(dna))
                .toList();
        if (orphans.isEmpty()) {
            return new Analysis(
                    List.of(), Optional.empty(), supported.size(),
                    targetCanopy.size(),
                    Math.max(0, targetCanopy.size()
                            - liveOwnedCanopy.size()));
        }

        List<Repair> choices = new ArrayList<>();
        Set<String> supportedFrontier = new HashSet<>(rootedWood);
        supportedFrontier.addAll(supported);
        for (PlannedTreeBlock block : plannedBlocks) {
            if (block.role() != TreeBlockRole.CANOPY
                    || protectedCoordinates.contains(block.key())) {
                continue;
            }
            String key = worldKey(dna, block.key());
            if (liveOwnedCanopy.contains(key)) {
                continue;
            }
            if (!touchesAny(key, supportedFrontier)) {
                continue;
            }
            Action action = livePlannedCanopy.contains(key)
                    ? Action.ADOPT_LIVE_TARGET
                    : Action.PLACE_MISSING_TARGET;
            choices.add(new Repair(
                    block, action, orphans.getFirst(),
                    supported.size(), supported.size() + 1));
        }
        Optional<Repair> selected = choices.stream()
                .sorted((left, right) -> {
                    int gain = Integer.compare(
                            right.rootedAfter() - right.rootedBefore(),
                            left.rootedAfter() - left.rootedBefore());
                    return gain != 0 ? gain
                            : left.block().key().compareTo(
                                    right.block().key());
                })
                .findFirst();
        return new Analysis(
                List.copyOf(orphans), selected, supported.size(),
                targetCanopy.size(),
                Math.max(0, targetCanopy.size()
                        - liveOwnedCanopy.size()));
    }

    private static Set<String> supportedCanopy(
            Set<String> rootedWood,
            Set<String> canopy
    ) {
        java.util.Map<Coordinate, String> canopyIndex =
                new java.util.HashMap<>();
        Set<Coordinate> rootedIndex = new HashSet<>();
        rootedWood.forEach(key -> rootedIndex.add(coordinateOf(key)));
        canopy.forEach(key -> canopyIndex.put(coordinateOf(key), key));
        Set<String> supported = new HashSet<>();
        java.util.ArrayDeque<Coordinate> pending =
                new java.util.ArrayDeque<>();
        for (java.util.Map.Entry<Coordinate, String> entry
                : canopyIndex.entrySet()) {
            if (touchesAny(entry.getKey(), rootedIndex)) {
                supported.add(entry.getValue());
                pending.add(entry.getKey());
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
                        String leaf = canopyIndex.get(next);
                        if (leaf != null && supported.add(leaf)) {
                            pending.addLast(next);
                        }
                    }
                }
            }
        }
        return Set.copyOf(supported);
    }

    private static boolean touchesAny(
            String key, Set<String> candidates) {
        return candidates.stream().anyMatch(candidate ->
                touches(key, candidate));
    }

    private static boolean touches(String first, String second) {
        Coordinate a = coordinateOf(first);
        Coordinate b = coordinateOf(second);
        return Math.max(Math.abs(a.x() - b.x()),
                Math.max(Math.abs(a.y() - b.y()),
                        Math.abs(a.z() - b.z()))) <= 1;
    }

    private static Coordinate coordinateOf(String key) {
        String[] parts = key.split(":");
        int offset = parts.length - 3;
        return new Coordinate(
                Integer.parseInt(parts[offset]),
                Integer.parseInt(parts[offset + 1]),
                Integer.parseInt(parts[offset + 2]));
    }

    private static boolean touchesAny(
            Coordinate coordinate, Set<Coordinate> candidates) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dy != 0 || dz != 0)
                            && candidates.contains(new Coordinate(
                                    coordinate.x() + dx,
                                    coordinate.y() + dy,
                                    coordinate.z() + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private record Coordinate(int x, int y, int z) {
    }

    private record WoodConnectivityIndex(
            Coordinate root,
            Set<Coordinate> rooted,
            Map<Coordinate, Integer> componentByCoordinate,
            Map<Integer, Integer> componentSizes
    ) {
        static WoodConnectivityIndex build(
                TreeDna dna,
                Set<String> liveOwnedWood,
                Set<String> rootedKeys
        ) {
            Set<Coordinate> all = new HashSet<>();
            for (String key : liveOwnedWood) {
                all.add(coordinateOf(key));
            }
            Set<Coordinate> rooted = new HashSet<>();
            for (String key : rootedKeys) {
                rooted.add(coordinateOf(key));
            }

            Set<Coordinate> unvisited = new HashSet<>(all);
            unvisited.removeAll(rooted);
            Map<Coordinate, Integer> componentByCoordinate =
                    new HashMap<>();
            Map<Integer, Integer> componentSizes = new HashMap<>();
            int componentId = 0;
            while (!unvisited.isEmpty()) {
                Coordinate start = unvisited.iterator().next();
                ArrayDeque<Coordinate> pending = new ArrayDeque<>();
                pending.add(start);
                unvisited.remove(start);
                int size = 0;
                while (!pending.isEmpty()) {
                    Coordinate current = pending.removeFirst();
                    componentByCoordinate.put(current, componentId);
                    size++;
                    forEachNeighbor(current, neighbor -> {
                        if (unvisited.remove(neighbor)) {
                            pending.addLast(neighbor);
                        }
                    });
                }
                componentSizes.put(componentId, size);
                componentId++;
            }
            return new WoodConnectivityIndex(
                    new Coordinate(dna.baseX(), dna.baseY(), dna.baseZ()),
                    Set.copyOf(rooted), Map.copyOf(componentByCoordinate),
                    Map.copyOf(componentSizes));
        }

        int rootedSizeAfterAdding(String key) {
            Coordinate candidate = coordinateOf(key);
            boolean anchorsRoot = candidate.equals(root)
                    || touchesAny(candidate, rooted);
            if (!anchorsRoot) {
                return rooted.size();
            }
            Set<Integer> joinedComponents = new HashSet<>();
            forEachNeighbor(candidate, neighbor -> {
                Integer component = componentByCoordinate.get(neighbor);
                if (component != null) {
                    joinedComponents.add(component);
                }
            });
            int connected = rooted.size() + 1;
            for (Integer component : joinedComponents) {
                connected += componentSizes.getOrDefault(component, 0);
            }
            return connected;
        }

        private static void forEachNeighbor(
                Coordinate center,
                java.util.function.Consumer<Coordinate> consumer
        ) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dy != 0 || dz != 0) {
                            consumer.accept(new Coordinate(
                                    center.x() + dx,
                                    center.y() + dy,
                                    center.z() + dz));
                        }
                    }
                }
            }
        }
    }

    private static boolean isWood(PlannedTreeBlock block) {
        return block.role() == TreeBlockRole.TRUNK
                || block.role() == TreeBlockRole.BRANCH
                || block.role() == TreeBlockRole.ROOT;
    }

    private static String worldKey(TreeDna dna, String coordinateKey) {
        return dna.worldId() + ":" + coordinateKey;
    }

    enum Action {
        ADOPT_LIVE_TARGET,
        PLACE_MISSING_TARGET
    }

    record Repair(
            PlannedTreeBlock block,
            Action action,
            String orphanKey,
            int rootedBefore,
            int rootedAfter
    ) {
        String marker() {
            return "[REPAIR-BRIDGE][" + action + "] orphan="
                    + orphanKey + " bridge=" + block.key()
                    + " rooted=" + rootedBefore + "->" + rootedAfter;
        }
    }

    record Analysis(
            List<String> disconnectedTargetKeys,
            Optional<Repair> repair,
            int rootedOwnedWood,
            int plannedWood,
            int unownedPlannedWood
    ) {
        boolean required() {
            return !disconnectedTargetKeys.isEmpty();
        }

        String marker() {
            return "orphans=" + disconnectedTargetKeys.size()
                    + " rooted-owned=" + rootedOwnedWood
                    + " planned-wood=" + plannedWood
                    + " unowned-planned=" + unownedPlannedWood
                    + " repair=" + repair.map(Repair::marker)
                            .orElse("none");
        }
    }
}
