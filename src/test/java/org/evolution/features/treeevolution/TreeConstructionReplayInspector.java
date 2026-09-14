package org.evolution.features.treeevolution;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Cell;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Ownership;

/**
 * ## Measures live replay state without changing virtual-world blocks.
 */
final class TreeConstructionReplayInspector {
    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private final TreePlan plan;
    private final List<PlannedTreeBlock> targets;

    TreeConstructionReplayInspector(
            TreePlan plan, List<PlannedTreeBlock> targets) {
        this.plan = plan;
        this.targets = targets;
    }

    TreeReplayProgress progress(
            TreeDna dna, TreeConstructionReplayWorld world) {
        int trunkTotal = 0;
        int trunkPlaced = 0;
        int branchTotal = 0;
        int branchPlaced = 0;
        int canopyTotal = 0;
        int canopyPlaced = 0;
        for (PlannedTreeBlock target : targets) {
            boolean placed = targetSatisfied(dna, world, target);
            switch (target.role()) {
                case TRUNK -> {
                    trunkTotal++;
                    trunkPlaced += placed ? 1 : 0;
                }
                case BRANCH -> {
                    branchTotal++;
                    branchPlaced += placed ? 1 : 0;
                }
                case CANOPY -> {
                    canopyTotal++;
                    canopyPlaced += placed ? 1 : 0;
                }
                default -> {
                }
            }
        }
        return new TreeReplayProgress(
                trunkPlaced, trunkTotal,
                branchPlaced, branchTotal,
                canopyPlaced, canopyTotal);
    }

    boolean targetSatisfied(
            TreeDna dna,
            TreeConstructionReplayWorld world,
            PlannedTreeBlock target
    ) {
        Cell cell = world.cell(target.key());
        if (cell == null || cell.material() != target.material()) {
            return false;
        }
        if (target.role() == TreeBlockRole.CANOPY) {
            return cell.ownership() == Ownership.EVOLVED;
        }
        return cell.ownership() == Ownership.SOURCE
                || cell.ownership() == Ownership.EVOLVED;
    }

    int liveHeight(
            TreeDna dna, TreeConstructionReplayWorld world) {
        return targets.stream()
                .filter(block -> block.role() == TreeBlockRole.TRUNK
                        && targetSatisfied(dna, world, block))
                .mapToInt(block -> block.y() - dna.baseY() + 1)
                .max().orElse(0);
    }

    int exposedUpperLogs(
            TreeDna dna, TreeConstructionReplayWorld world) {
        int topY = targets.stream()
                .filter(block -> block.role() == TreeBlockRole.TRUNK
                        && targetSatisfied(dna, world, block))
                .mapToInt(PlannedTreeBlock::y)
                .max().orElse(Integer.MIN_VALUE);
        if (topY == Integer.MIN_VALUE) {
            return 0;
        }
        int exposed = 0;
        for (PlannedTreeBlock block : targets) {
            if (block.role() == TreeBlockRole.TRUNK
                    && block.y() == topY
                    && targetSatisfied(dna, world, block)
                    && TreeCanopyIntegrityPolicy.requiresCanopyCover(
                            block.x(), block.y(), block.z(),
                            dna.species().leafMaterial(),
                            plan.blocksByKey(),
                            canopy -> {
                                Cell cover = world.cell(canopy.key());
                                return cover == null
                                        || cover.ownership()
                                                != Ownership.NEIGHBOR;
                            })
                    && !hasDirectLiveLeaf(
                            dna, world,
                            block.x(), block.y(), block.z())) {
                exposed++;
            }
        }
        return exposed;
    }

    int uncoveredBranchTips(
            TreeDna dna, TreeConstructionReplayWorld world) {
        return branchIntegrity(dna, world, true).totalProblems();
    }

    BranchIntegrity branchIntegrity(
            TreeDna dna,
            TreeConstructionReplayWorld world,
            boolean ownershipComplete
    ) {
        Set<String> uncoveredPlanned = new java.util.LinkedHashSet<>();
        Set<String> visitedTips = new HashSet<>();
        for (TreeBranchPlan branch : plan.branchPlans()) {
            TreeBranchPlan.BranchTip tip = branch.tip();
            String tipKey = key(tip.x(), tip.y(), tip.z());
            PlannedTreeBlock target = plan.blocksByKey().get(tipKey);
            if (target != null
                    && visitedTips.add(tipKey)
                    && targetSatisfied(dna, world, target)
                    && !hasLiveEnvelope(dna, world, tip)) {
                uncoveredPlanned.add(tipKey);
            }
            for (TreeBranchPlan.BranchSegment segment
                    : branch.segments()) {
                String segmentKey = key(
                        segment.x(), segment.y(), segment.z());
                if (visitedTips.contains(segmentKey)
                        || !TreeBranchCanopyIntegrationPolicy
                                .requiresCover(branch, segment)) {
                    continue;
                }
                PlannedTreeBlock segmentTarget =
                        plan.blocksByKey().get(segmentKey);
                if (segmentTarget == null
                        || !targetSatisfied(
                                dna, world, segmentTarget)) {
                    continue;
                }
                int required = TreeBranchCanopyIntegrationPolicy
                        .targetDirectContacts(
                                dna, segment.x(), segment.y(),
                                segment.z(), plan.blocksByKey());
                if (required > 0
                        && adjacentPlannedLeafContacts(
                                dna, world, segment.x(), segment.y(),
                                segment.z()) < required) {
                    uncoveredPlanned.add(segmentKey);
                }
            }
        }
        List<String> unplanned = unplannedBareTerminalKeys(
                dna, world, ownershipComplete);
        List<String> stale = stalePersistentEnvelopeLeafKeys(
                dna, world);
        return new BranchIntegrity(
                List.copyOf(uncoveredPlanned), unplanned, stale);
    }

    boolean hasLiveEnvelope(
            TreeDna dna,
            TreeConstructionReplayWorld world,
            TreeBranchPlan.BranchTip tip
    ) {
        int contacts = 0;
        Deque<Coordinate> pending = new ArrayDeque<>();
        Set<String> visited = new java.util.HashSet<>();
        for (int[] offset : NEIGHBORS) {
            if (addLiveLeaf(
                    dna, world, pending, visited,
                    tip.x() + offset[0],
                    tip.y() + offset[1],
                    tip.z() + offset[2])) {
                contacts++;
            }
        }
        int leaves = 0;
        int minX = tip.x();
        int maxX = tip.x();
        int minY = tip.y();
        int maxY = tip.y();
        int minZ = tip.z();
        int maxZ = tip.z();
        while (!pending.isEmpty()) {
            Coordinate current = pending.removeFirst();
            leaves++;
            minX = Math.min(minX, current.x());
            maxX = Math.max(maxX, current.x());
            minY = Math.min(minY, current.y());
            maxY = Math.max(maxY, current.y());
            minZ = Math.min(minZ, current.z());
            maxZ = Math.max(maxZ, current.z());
            for (int[] offset : NEIGHBORS) {
                int x = current.x() + offset[0];
                int y = current.y() + offset[1];
                int z = current.z() + offset[2];
                if (Math.abs(x - tip.x()) <= 2
                        && Math.abs(y - tip.y()) <= 1
                        && Math.abs(z - tip.z()) <= 2) {
                    addLiveLeaf(
                            dna, world, pending, visited, x, y, z);
                }
            }
        }
        TreeBranchTipIntegrityPolicy.EnvelopeShape shape =
                new TreeBranchTipIntegrityPolicy.EnvelopeShape(
                        leaves, minX, maxX, minY, maxY, minZ, maxZ);
        int requiredContacts = TreeBranchTipIntegrityPolicy
                .targetLeafContacts(
                        dna, tip.x(), tip.y(), tip.z(),
                        plan.blocksByKey());
        int requiredCluster = TreeBranchTipIntegrityPolicy
                .targetClusterLeaves(
                        dna, tip.x(), tip.y(), tip.z(),
                        plan.blocksByKey());
        return contacts >= requiredContacts
                && leaves >= requiredCluster
                && TreeBranchTipIntegrityPolicy.hasNaturalVolume(
                        dna.maturityStage(), dna.species(),
                        tip.x(), tip.y(), tip.z(), shape);
    }

    private int adjacentPlannedLeafContacts(
            TreeDna dna,
            TreeConstructionReplayWorld world,
            int x,
            int y,
            int z
    ) {
        int contacts = 0;
        for (int[] offset : NEIGHBORS) {
            String neighborKey = key(
                    x + offset[0], y + offset[1], z + offset[2]);
            PlannedTreeBlock planned = plan.blocksByKey().get(neighborKey);
            Cell live = world.cell(neighborKey);
            if (planned != null
                    && planned.role() == TreeBlockRole.CANOPY
                    && live != null
                    && live.ownership() == Ownership.EVOLVED
                    && live.material() == dna.species().leafMaterial()) {
                contacts++;
            }
        }
        return contacts;
    }

    private List<String> unplannedBareTerminalKeys(
            TreeDna dna,
            TreeConstructionReplayWorld world,
            boolean ownershipComplete
    ) {
        return world.cells().entrySet().stream()
                .filter(entry -> entry.getValue().ownership()
                        == Ownership.EVOLVED)
                .filter(entry -> entry.getValue().material()
                        == dna.species().logMaterial())
                .filter(entry -> {
                    int[] coordinate = coordinate(entry.getKey());
                    PlannedTreeBlock planned = plan.blocksByKey().get(
                            entry.getKey());
                    boolean evolvedLogOwned = dna.evolvedShapeLogs()
                            .contains(worldKey(dna, entry.getKey()))
                            && !dna.originalShapeLogs().contains(
                                    worldKey(dna, entry.getKey()));
                    int trunkDistance = Math.max(
                            Math.abs(coordinate[0]
                                    - dna.trunkXAt(coordinate[1])),
                            Math.abs(coordinate[2]
                                    - dna.trunkZAt(coordinate[1])));
                    return TreeLiveTerminalPolicy.classify(
                            ownershipComplete, evolvedLogOwned,
                            planned == null ? null : planned.role(),
                            coordinate[1] - dna.baseY(), trunkDistance,
                            sameSpeciesWoodNeighbors(
                                    dna, world, coordinate))
                            == TreeLiveTerminalPolicy.Decision
                                    .PRUNE_UNPLANNED_BARE_TERMINAL;
                })
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    TreeConstructionMutationPolicy.TargetRetirement
            terminalRetirementPlan(
                    TreeDna dna,
                    TreeConstructionReplayWorld world,
                    String retiringCoordinate
            ) {
        Set<String> liveOwnedWood = new HashSet<>();
        Set<String> livePlannedWood = new HashSet<>();
        Set<String> currentTargetWood = new HashSet<>();
        Set<String> evolvedWood = new HashSet<>();
        Set<String> evolvedCanopy = new HashSet<>();
        for (Map.Entry<String, Cell> entry : world.cells().entrySet()) {
            Cell cell = entry.getValue();
            if (cell.ownership() != Ownership.NEIGHBOR
                    && isWood(cell.role())) {
                String receipt = worldKey(dna, entry.getKey());
                liveOwnedWood.add(receipt);
                if (cell.ownership() == Ownership.EVOLVED) {
                    evolvedWood.add(receipt);
                }
                PlannedTreeBlock planned =
                        plan.blocksByKey().get(entry.getKey());
                if (planned != null && isWood(planned.role())) {
                    currentTargetWood.add(receipt);
                }
            } else if (cell.ownership() == Ownership.EVOLVED
                    && cell.role() == TreeBlockRole.CANOPY) {
                String receipt = worldKey(dna, entry.getKey());
                evolvedCanopy.add(receipt);
                PlannedTreeBlock planned =
                        plan.blocksByKey().get(entry.getKey());
                if (planned != null
                        && planned.role() == TreeBlockRole.CANOPY) {
                    currentTargetWood.add(receipt);
                }
            }
        }
        for (PlannedTreeBlock planned : targets) {
            if (!isWood(planned.role())) {
                continue;
            }
            Cell live = world.cell(planned.key());
            if (live != null
                    && live.material() == planned.material()) {
                livePlannedWood.add(worldKey(dna, planned.key()));
            }
        }
        CachedTreePlan cached = new CachedTreePlan(
                "replay-snapshot", plan, plan.orderedBlocks(),
                plan.blocksByKey());
        return TreeConstructionMutationPolicy.targetRetirement(
                dna, cached, liveOwnedWood,
                evolvedWood, evolvedCanopy, livePlannedWood,
                currentTargetWood, Set.of(),
                worldKey(dna, retiringCoordinate));
    }

    private int sameSpeciesWoodNeighbors(
            TreeDna dna,
            TreeConstructionReplayWorld world,
            int[] coordinate
    ) {
        int neighbors = 0;
        for (int[] offset : NEIGHBORS) {
            Cell neighbor = world.cell(key(
                    coordinate[0] + offset[0],
                    coordinate[1] + offset[1],
                    coordinate[2] + offset[2]));
            if (neighbor != null
                    && neighbor.material()
                            == dna.species().logMaterial()) {
                neighbors++;
            }
        }
        return neighbors;
    }

    private List<String> stalePersistentEnvelopeLeafKeys(
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        Set<String> stale = new java.util.TreeSet<>();
        for (TreeBranchPlan.BranchTip tip
                : plan.branchEnvelopeCleanupTips()) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        String coordinateKey = key(
                                tip.x() + dx, tip.y() + dy,
                                tip.z() + dz);
                        Cell live = world.cell(coordinateKey);
                        if (live == null || !live.persistent()
                                || live.material()
                                        != dna.species().leafMaterial()
                                || live.ownership() == Ownership.NEIGHBOR) {
                            continue;
                        }
                        PlannedTreeBlock planned =
                                plan.blocksByKey().get(coordinateKey);
                        if (planned == null
                                || planned.role()
                                        != TreeBlockRole.CANOPY
                                || planned.material()
                                        != dna.species().leafMaterial()) {
                            stale.add(coordinateKey);
                        }
                    }
                }
            }
        }
        return List.copyOf(stale);
    }

    private static String worldKey(TreeDna dna, String coordinateKey) {
        return dna.worldId() + ":" + coordinateKey;
    }

    Optional<PlannedTreeBlock> readyTransitionBlocker(
            TreeDna dna, TreeConstructionReplayWorld world) {
        return targets.stream()
                .filter(TreeConstructionReplayInspector::isStructural)
                .filter(block -> supportReady(dna, world, block))
                .filter(block -> {
                    Cell cell = world.cell(block.key());
                    return cell != null
                            && cell.ownership() == Ownership.SOURCE
                            && cell.material().name()
                                    .endsWith("_LEAVES");
                })
                .findFirst();
    }

    Optional<String> retiredSourceLeaf(
            TreeConstructionReplayWorld world) {
        return world.cells().entrySet().stream()
                .filter(entry -> entry.getValue().ownership()
                        == Ownership.SOURCE)
                .filter(entry -> entry.getValue().material()
                        .name().endsWith("_LEAVES"))
                .filter(entry -> {
                    PlannedTreeBlock planned =
                            plan.blocksByKey().get(entry.getKey());
                    return planned == null
                            || planned.role() != TreeBlockRole.CANOPY
                            || planned.material()
                                    != entry.getValue().material();
                })
                .map(Map.Entry::getKey)
                .sorted()
                .findFirst();
    }

    Optional<String> obsoleteEvolvedTreeBlock(
            TreeDna dna,
            TreeConstructionReplayWorld world) {
        return world.cells().entrySet().stream()
                .filter(entry -> entry.getValue().ownership()
                        == Ownership.EVOLVED)
                .filter(entry -> isObsolete(
                        dna, world, entry.getKey(), entry.getValue()))
                // ## Retire stale leaves before wood so a former crown does
                // not remain after its obsolete support is removed.
                .sorted((first, second) -> {
                    int firstPriority = first.getValue().role()
                            == TreeBlockRole.CANOPY ? 0 : 1;
                    int secondPriority = second.getValue().role()
                            == TreeBlockRole.CANOPY ? 0 : 1;
                    int comparison = Integer.compare(
                            firstPriority, secondPriority);
                    if (comparison != 0) {
                        return comparison;
                    }
                    if (firstPriority == 0) {
                        return first.getKey().compareTo(second.getKey());
                    }
                    return TreeObsoleteRetirementPolicy
                            .outermostFirst(dna)
                            .compare(first.getKey(), second.getKey());
                })
                .map(Map.Entry::getKey)
                .findFirst();
    }

    Optional<String> conflictingEvolvedTargetBlock(
            TreeConstructionReplayWorld world
    ) {
        return world.cells().entrySet().stream()
                .filter(entry -> entry.getValue().ownership()
                        == Ownership.EVOLVED)
                .filter(entry -> {
                    PlannedTreeBlock target =
                            plan.blocksByKey().get(entry.getKey());
                    return target != null
                            && !sameTreeRole(
                                    entry.getValue().role(),
                                    target.role());
                })
                // ## Retire conflicting wood before leaves because replacing
                // a connector log directly with canopy can split an old limb.
                .sorted((first, second) -> {
                    int firstPriority = isWood(first.getValue().role())
                            ? 0 : 1;
                    int secondPriority = isWood(second.getValue().role())
                            ? 0 : 1;
                    int comparison = Integer.compare(
                            firstPriority, secondPriority);
                    return comparison != 0 ? comparison
                            : first.getKey().compareTo(second.getKey());
                })
                .map(Map.Entry::getKey)
                .findFirst();
    }

    Optional<String> conflictSafeRetirement(
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        Optional<String> conflict =
                conflictingEvolvedTargetBlock(world);
        if (conflict.isEmpty()) {
            return Optional.empty();
        }
        Cell conflictCell = world.cell(conflict.get());
        if (conflictCell == null || !isWood(conflictCell.role())) {
            return conflict;
        }
        Set<String> liveWood = new HashSet<>();
        Set<String> evolvedWood = new HashSet<>();
        Set<String> evolvedCanopy = new HashSet<>();
        Set<String> protectedCurrentTargets = new HashSet<>();
        for (Map.Entry<String, Cell> entry : world.cells().entrySet()) {
            Cell cell = entry.getValue();
            if (cell.ownership() == Ownership.NEIGHBOR) {
                continue;
            }
            String worldKey = dna.worldId() + ":" + entry.getKey();
            if (isWood(cell.role())) {
                liveWood.add(worldKey);
                if (cell.ownership() == Ownership.EVOLVED) {
                    evolvedWood.add(worldKey);
                    if (matchesLiveTarget(world, entry.getKey())) {
                        protectedCurrentTargets.add(worldKey);
                    }
                }
            } else if (cell.ownership() == Ownership.EVOLVED
                    && cell.role() == TreeBlockRole.CANOPY) {
                evolvedCanopy.add(worldKey);
                if (matchesLiveTarget(world, entry.getKey())) {
                    protectedCurrentTargets.add(worldKey);
                }
            }
        }
        String worldConflict = dna.worldId() + ":" + conflict.get();
        return TreeConflictRetirementPolicy.next(
                        dna, liveWood, evolvedWood,
                        evolvedCanopy, protectedCurrentTargets,
                        worldConflict)
                .map(TreeConstructionReplayInspector::coordinateKey);
    }

    private static String coordinateKey(String worldKey) {
        int[] coordinate = coordinate(worldKey);
        return key(coordinate[0], coordinate[1], coordinate[2]);
    }

    private static boolean sameTreeRole(
            TreeBlockRole live,
            TreeBlockRole target
    ) {
        return live == TreeBlockRole.CANOPY
                ? target == TreeBlockRole.CANOPY
                : isWood(live) && isWood(target);
    }

    private static boolean isWood(TreeBlockRole role) {
        return role == TreeBlockRole.TRUNK
                || role == TreeBlockRole.BRANCH
                || role == TreeBlockRole.ROOT;
    }

    Optional<String> disconnectedEvolvedBodyBlock(
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        Set<String> rooted = rootConnectedReceiptWood(dna, world);
        Optional<String> wood = world.cells().entrySet().stream()
                .filter(entry -> entry.getValue().ownership()
                        == Ownership.EVOLVED)
                .filter(entry ->
                        entry.getValue().role() == TreeBlockRole.TRUNK
                                || entry.getValue().role()
                                        == TreeBlockRole.BRANCH
                                || entry.getValue().role()
                                        == TreeBlockRole.ROOT)
                .map(Map.Entry::getKey)
                .filter(key -> !rooted.contains(key))
                .sorted()
                .findFirst();
        return wood.isPresent() ? wood
                : unsupportedEvolvedCanopyKeys(
                        dna, world).stream().sorted().findFirst();
    }

    Optional<String> disconnectedSafeRetirement(
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        Set<String> disconnected = disconnectedEvolvedBodyKeys(
                dna, world);
        Optional<String> canopy = disconnected.stream()
                .filter(key -> !matchesLiveTarget(world, key))
                .filter(key -> {
                    Cell cell = world.cell(key);
                    return cell != null
                            && cell.role() == TreeBlockRole.CANOPY;
                })
                .sorted(TreeObsoleteRetirementPolicy
                        .outermostFirst(dna))
                .findFirst();
        if (canopy.isPresent()) {
            return canopy;
        }
        return disconnected.stream()
                .filter(key -> !matchesLiveTarget(world, key))
                .filter(key -> {
                    Cell cell = world.cell(key);
                    return cell != null && isWood(cell.role());
                })
                .sorted(TreeObsoleteRetirementPolicy
                        .outermostFirst(dna))
                .findFirst();
    }

    boolean disconnectedPlannedBodyRemaining(
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        return disconnectedEvolvedBodyKeys(dna, world).stream()
                .anyMatch(key -> matchesLiveTarget(world, key));
    }

    boolean disconnectedObsoleteBodyRemaining(
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        return disconnectedEvolvedBodyKeys(dna, world).stream()
                .anyMatch(key -> !matchesLiveTarget(world, key));
    }

    private Set<String> disconnectedEvolvedBodyKeys(
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        Set<String> rooted = rootConnectedReceiptWood(dna, world);
        Set<String> disconnected = new HashSet<>();
        for (Map.Entry<String, Cell> entry
                : world.cells().entrySet()) {
            Cell cell = entry.getValue();
            if (cell.ownership() != Ownership.EVOLVED) {
                continue;
            }
            if ((cell.role() == TreeBlockRole.TRUNK
                    || cell.role() == TreeBlockRole.BRANCH
                    || cell.role() == TreeBlockRole.ROOT)
                    && !rooted.contains(entry.getKey())) {
                disconnected.add(entry.getKey());
            }
        }
        disconnected.addAll(
                unsupportedEvolvedCanopyKeys(dna, world));
        return disconnected;
    }

    private Set<String> unsupportedEvolvedCanopyKeys(
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        return unsupportedEvolvedCanopyKeys(
                world, rootConnectedReceiptWood(dna, world));
    }

    private Set<String> rootConnectedReceiptWood(
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        Set<String> receipts = new HashSet<>(
                dna.originalShapeLogs());
        receipts.addAll(dna.evolvedShapeLogs());
        Set<String> liveReceipts = new HashSet<>();
        for (String receipt : receipts) {
            Cell cell = world.cell(coordinateKey(receipt));
            if (cell != null
                    && cell.ownership() != Ownership.NEIGHBOR
                    && isWood(cell.role())) {
                liveReceipts.add(receipt);
            }
        }
        Set<String> rootedCoordinates = new HashSet<>();
        for (String rooted : TreeWoodOwnershipGraph.connectedToRoot(
                dna, liveReceipts)) {
            rootedCoordinates.add(coordinateKey(rooted));
        }
        return rootedCoordinates;
    }

    private Set<String> unsupportedEvolvedCanopyKeys(
            TreeConstructionReplayWorld world,
            Set<String> rootedWood
    ) {
        Set<String> canopy = new HashSet<>();
        for (Map.Entry<String, Cell> entry
                : world.cells().entrySet()) {
            if (entry.getValue().ownership() == Ownership.EVOLVED
                    && entry.getValue().role()
                            == TreeBlockRole.CANOPY) {
                canopy.add(entry.getKey());
            }
        }
        Set<String> supported = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        for (String leaf : canopy) {
            if (touchesAny(leaf, rootedWood)) {
                supported.add(leaf);
                pending.addLast(leaf);
            }
        }
        while (!pending.isEmpty()) {
            int[] current = coordinate(pending.removeFirst());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        String next = key(
                                current[0] + dx,
                                current[1] + dy,
                                current[2] + dz);
                        if (canopy.contains(next)
                                && supported.add(next)) {
                            pending.addLast(next);
                        }
                    }
                }
            }
        }
        canopy.removeAll(supported);
        return canopy;
    }

    boolean matchesLiveTarget(
            TreeConstructionReplayWorld world,
            String key
    ) {
        Cell live = world.cell(key);
        PlannedTreeBlock planned = plan.blocksByKey().get(key);
        return live != null
                && planned != null
                && planned.material() == live.material()
                && sameTreeRole(live.role(), planned.role());
    }

    private static boolean touchesAny(
            String key, Set<String> candidates) {
        int[] coordinate = coordinate(key);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    if (candidates.contains(key(
                            coordinate[0] + dx,
                            coordinate[1] + dy,
                            coordinate[2] + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isObsolete(
            TreeDna dna,
            TreeConstructionReplayWorld world,
            String key,
            Cell live
    ) {
        PlannedTreeBlock planned = plan.blocksByKey().get(key);
        if (planned == null || planned.material() != live.material()) {
            return true;
        }
        return live.role() == TreeBlockRole.CANOPY
                ? planned.role() != TreeBlockRole.CANOPY
                : planned.role() != TreeBlockRole.TRUNK
                        && planned.role() != TreeBlockRole.BRANCH
                        && planned.role() != TreeBlockRole.ROOT;
    }

    private Set<String> rootConnectedOwnedWood(
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        return rootConnectedOwnedWood(dna, world, Set.of());
    }

    private Set<String> rootConnectedOwnedWood(
            TreeDna dna,
            TreeConstructionReplayWorld world,
            Set<String> excluded
    ) {
        Set<String> wood = new HashSet<>();
        for (Map.Entry<String, Cell> entry
                : world.cells().entrySet()) {
            Cell cell = entry.getValue();
            if (cell.ownership() == Ownership.NEIGHBOR
                    || excluded.contains(entry.getKey())
                    || (cell.role() != TreeBlockRole.TRUNK
                        && cell.role() != TreeBlockRole.BRANCH
                        && cell.role() != TreeBlockRole.ROOT)) {
                continue;
            }
            wood.add(entry.getKey());
        }
        Set<String> rooted = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        for (String woodKey : wood) {
            int[] coordinate = coordinate(woodKey);
            if (coordinate[1] == dna.baseY()
                    && Math.abs(coordinate[0] - dna.baseX()) <= 1
                    && Math.abs(coordinate[2] - dna.baseZ()) <= 1) {
                rooted.add(woodKey);
                pending.addLast(woodKey);
            }
        }
        while (!pending.isEmpty()) {
            int[] current = coordinate(pending.removeFirst());
            for (int[] neighbor : NEIGHBORS) {
                String next = key(
                        current[0] + neighbor[0],
                        current[1] + neighbor[1],
                        current[2] + neighbor[2]);
                if (wood.contains(next) && rooted.add(next)) {
                    pending.addLast(next);
                }
            }
        }
        return rooted;
    }

    private static int[] coordinate(String key) {
        String[] parts = key.split(":");
        int offset = parts.length - 3;
        return new int[]{
                Integer.parseInt(parts[offset]),
                Integer.parseInt(parts[offset + 1]),
                Integer.parseInt(parts[offset + 2])
        };
    }

    boolean supportReady(
            TreeDna dna,
            TreeConstructionReplayWorld world,
            PlannedTreeBlock block
    ) {
        if (block.role() == TreeBlockRole.TRUNK) {
            if (block.y() == dna.baseY()) {
                return true;
            }
            // ## Mirror TreePlacementService's direct wood support gate.
            // A lower trunk somewhere in the plan does not make a leaning or
            // widening voxel reachable at this exact coordinate.
            return hasDirectOwnedWoodNeighbor(world, block);
        }
        if (block.role() == TreeBlockRole.BRANCH
                && block.hasBranchPath()) {
            PlannedTreeBlock parent =
                    plan.blocksByKey().get(block.parentKey());
            return parent != null
                    && targetSatisfied(dna, world, parent);
        }
        return true;
    }

    private boolean hasDirectOwnedWoodNeighbor(
            TreeConstructionReplayWorld world,
            PlannedTreeBlock block
    ) {
        int[][] faces = {
                {1, 0, 0}, {-1, 0, 0},
                {0, 1, 0}, {0, -1, 0},
                {0, 0, 1}, {0, 0, -1}
        };
        for (int[] face : faces) {
            Cell neighbor = world.cell(key(
                    block.x() + face[0],
                    block.y() + face[1],
                    block.z() + face[2]));
            if (neighbor == null
                    || neighbor.ownership() == Ownership.NEIGHBOR) {
                continue;
            }
            if (neighbor.role() == TreeBlockRole.TRUNK
                    || neighbor.role() == TreeBlockRole.BRANCH
                    || neighbor.role() == TreeBlockRole.ROOT) {
                return true;
            }
        }
        return false;
    }

    boolean canopyReachable(
            TreeConstructionReplayWorld world,
            PlannedTreeBlock canopy
    ) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Cell neighbor = world.cell(key(
                            canopy.x() + dx,
                            canopy.y() + dy,
                            canopy.z() + dz));
                    if (neighbor == null
                            || neighbor.ownership()
                                    == Ownership.NEIGHBOR) {
                        continue;
                    }
                    if (neighbor.role() == TreeBlockRole.TRUNK
                            || neighbor.role() == TreeBlockRole.BRANCH
                            || (neighbor.role() == TreeBlockRole.CANOPY
                                    && neighbor.ownership()
                                            == Ownership.EVOLVED)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    int canopyDistanceToSupport(PlannedTreeBlock canopy) {
        return targets.stream()
                .filter(TreeConstructionReplayInspector::isStructural)
                .mapToInt(block ->
                        Math.abs(block.x() - canopy.x())
                                + Math.abs(block.y() - canopy.y())
                                + Math.abs(block.z() - canopy.z()))
                .min().orElse(99);
    }

    boolean touchesExposedTopSupport(
            TreeDna dna,
            TreeConstructionReplayWorld world,
            PlannedTreeBlock canopy
    ) {
        int topY = targets.stream()
                .filter(block -> block.role() == TreeBlockRole.TRUNK
                        && targetSatisfied(dna, world, block))
                .mapToInt(PlannedTreeBlock::y)
                .max().orElse(Integer.MIN_VALUE);
        return targets.stream().anyMatch(block ->
                block.role() == TreeBlockRole.TRUNK
                        && block.y() == topY
                        && targetSatisfied(dna, world, block)
                        && adjacent(block, canopy));
    }

    boolean insideUncoveredTipEnvelope(
            TreeDna dna,
            TreeConstructionReplayWorld world,
            PlannedTreeBlock canopy
    ) {
        return branchIntegrity(dna, world, true)
                .uncoveredPlannedEnvelopeKeys().stream()
                .map(TreeConstructionReplayInspector::coordinate)
                .anyMatch(support ->
                        Math.abs(canopy.x() - support[0]) <= 2
                                && Math.abs(canopy.y() - support[1]) <= 1
                                && Math.abs(canopy.z() - support[2]) <= 2);
    }

    private boolean addLiveLeaf(
            TreeDna dna,
            TreeConstructionReplayWorld world,
            Deque<Coordinate> pending,
            Set<String> visited,
            int x,
            int y,
            int z
    ) {
        String key = key(x, y, z);
        Cell cell = world.cell(key);
        if (cell == null
                || cell.ownership() != Ownership.EVOLVED
                || cell.material() != dna.species().leafMaterial()
                || !visited.add(key)) {
            return false;
        }
        pending.addLast(new Coordinate(x, y, z));
        return true;
    }

    private boolean hasDirectLiveLeaf(
            TreeDna dna,
            TreeConstructionReplayWorld world,
            int x,
            int y,
            int z
    ) {
        for (int[] offset : NEIGHBORS) {
            Cell cell = world.cell(key(
                    x + offset[0],
                    y + offset[1],
                    z + offset[2]));
            if (cell != null
                    && cell.ownership() == Ownership.EVOLVED
                    && cell.material()
                            == dna.species().leafMaterial()) {
                return true;
            }
        }
        return false;
    }

    private static boolean adjacent(
            PlannedTreeBlock first, PlannedTreeBlock second) {
        return Math.abs(first.x() - second.x())
                + Math.abs(first.y() - second.y())
                + Math.abs(first.z() - second.z()) == 1;
    }

    static boolean isStructural(PlannedTreeBlock block) {
        return block.role() == TreeBlockRole.TRUNK
                || block.role() == TreeBlockRole.BRANCH;
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    Optional<TreeOwnershipRoleReconciliationPolicy.Repair>
            ownershipRoleRepair(
                    TreeDna dna,
                    TreeConstructionReplayWorld world
            ) {
        Map<String, TreeOwnershipRoleReconciliationPolicy.Role> liveRoles =
                new HashMap<>();
        Set<String> receipts = new HashSet<>(dna.evolvedShapeLogs());
        receipts.addAll(dna.evolvedShapeLeaves());
        for (String receipt : receipts) {
            Cell live = world.cell(coordinateKey(receipt));
            if (live == null || live.ownership() == Ownership.NEIGHBOR) {
                continue;
            }
            TreeOwnershipRoleReconciliationPolicy.Role role =
                    TreeOwnershipRoleReconciliationPolicy.roleOf(
                            live.role());
            if (role != null) {
                liveRoles.put(receipt, role);
            }
        }
        return TreeOwnershipRoleReconciliationPolicy.next(
                dna.evolvedShapeLogs(), dna.evolvedShapeLeaves(),
                liveRoles, plan.orderedBlocks());
    }

    private record Coordinate(int x, int y, int z) {
    }

    record BranchIntegrity(
            List<String> uncoveredPlannedEnvelopeKeys,
            List<String> unplannedBareTerminalKeys,
            List<String> stalePersistentEnvelopeLeafKeys
    ) {
        int uncoveredPlannedEnvelopes() {
            return uncoveredPlannedEnvelopeKeys.size();
        }

        int unplannedBareTerminals() {
            return unplannedBareTerminalKeys.size();
        }

        int staleEnvelopeLeaves() {
            return stalePersistentEnvelopeLeafKeys.size();
        }

        int totalProblems() {
            return uncoveredPlannedEnvelopes()
                    + unplannedBareTerminals()
                    + staleEnvelopeLeaves();
        }

        Optional<String> firstUnplannedBareTerminal() {
            return unplannedBareTerminalKeys.stream().findFirst();
        }

        Optional<String> firstStaleEnvelopeLeaf() {
            return stalePersistentEnvelopeLeafKeys.stream().findFirst();
        }
    }
}
