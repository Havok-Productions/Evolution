package org.evolution.features.treeevolution;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Ownership;

/**
 * ## Builds a replay's initial live/source/neighbor voxel state.
 */
final class TreeConstructionReplaySeeder {
    private final TreeDna dna;
    private final TreePlan plan;
    private final List<PlannedTreeBlock> targets;
    private final TreeConstructionReplayWorld world;

    private TreeConstructionReplaySeeder(
            TreeDna dna,
            TreePlan plan,
            List<PlannedTreeBlock> targets,
            TreeConstructionReplayWorld world
    ) {
        this.dna = dna;
        this.plan = plan;
        this.targets = targets;
        this.world = world;
    }

    static void seed(
            TreeDna dna,
            TreePlan plan,
            List<PlannedTreeBlock> targets,
            TreeConstructionReplayWorld world,
            boolean injectObsoleteStructure,
            TreeCapturedEnvironmentFixture capturedEnvironment
    ) {
        TreeConstructionReplaySeeder seeder =
                new TreeConstructionReplaySeeder(
                        dna, plan, targets, world);
        boolean exactEnvironment = capturedEnvironment != null
                && capturedEnvironment.present();
        seeder.seedSourceTree(exactEnvironment);
        if (exactEnvironment) {
            seeder.applyCapturedEnvironment(capturedEnvironment);
        }
        if (injectObsoleteStructure) {
            // ## Every species must reconcile covered plugin-owned remnants,
            // not merely satisfy the blocks required by its latest target.
            seeder.seedObsoleteEvolvedStructure();
        }
    }

    private void seedSourceTree(boolean exactEnvironment) {
        boolean hasSourceSnapshot = dna.hasOriginalShapeSnapshot()
                // ## Shape-revision migration reopens old evolved leaves as
                // immutable source while its log receipts remain evolved.
                // The live world still contains both sets, so replay must not
                // replace that valid mixed ledger with a synthetic sapling.
                && (dna.originalShapeLogCount() > 0
                        || !dna.evolvedShapeLogs().isEmpty())
                && dna.originalShapeLeafCount() > 0;
        boolean hasCurrentSnapshot = !dna.evolvedShapeLogs().isEmpty()
                && !dna.evolvedShapeLeaves().isEmpty();
        if (hasSourceSnapshot || hasCurrentSnapshot) {
            // ## A current-only live capture is evidence, not a request for a
            // synthetic starter. Seed every persisted voxel before replay.
            seedCapturedSourceTree();
            if (!exactEnvironment) {
                seedNeighborTree(
                        dna.baseX(), dna.baseY() + 5, dna.baseZ());
            }
            return;
        }
        int visibleHeight = TreeSpeciesStageStyle.visibleHeight(dna);
        int sourceHeight = Math.max(4, Math.min(
                visibleHeight - 2,
                dna.maturityStage() == TreeMaturityStage.SMALL
                        ? 4 : Math.max(5, visibleHeight / 2)));
        Set<String> sourceLogs = new HashSet<>();
        Set<String> sourceLeaves = new HashSet<>();
        int sourceTopY = dna.baseY() + sourceHeight - 1;

        for (int y = dna.baseY(); y <= sourceTopY; y++) {
            int trunkY = y;
            PlannedTreeBlock trunk = targets.stream()
                    .filter(block -> block.role() == TreeBlockRole.TRUNK
                            && block.y() == trunkY)
                    .findFirst()
                    .orElseThrow();
            world.seed(trunk.key(), dna.species().logMaterial(),
                    TreeBlockRole.TRUNK, Ownership.SOURCE);
            sourceLogs.add(worldKey(trunk.key()));
        }

        int centerX = dna.trunkXAt(sourceTopY);
        int centerZ = dna.trunkZAt(sourceTopY);
        for (int y = -1; y <= 1; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(x) + Math.abs(z)
                            + (Math.abs(y) * 2) > 3) {
                        continue;
                    }
                    String key = key(
                            centerX + x, sourceTopY + y, centerZ + z);
                    if (world.cell(key) != null) {
                        continue;
                    }
                    world.seed(key, dna.species().leafMaterial(),
                            TreeBlockRole.CANOPY, Ownership.SOURCE);
                    sourceLeaves.add(worldKey(key));
                }
            }
        }

        targets.stream()
                .filter(block -> block.role() == TreeBlockRole.BRANCH)
                .min(Comparator.comparingInt(
                        PlannedTreeBlock::branchStep))
                .ifPresent(block -> {
                    if (world.cell(block.key()) == null) {
                        world.seed(
                                block.key(),
                                dna.species().leafMaterial(),
                                TreeBlockRole.CANOPY,
                                Ownership.SOURCE);
                        sourceLeaves.add(worldKey(block.key()));
                    }
                });

        String residualShelf = firstFreeOutsideTarget(
                centerX, sourceTopY, centerZ, 4);
        world.seed(residualShelf, dna.species().leafMaterial(),
                TreeBlockRole.CANOPY, Ownership.SOURCE);
        sourceLeaves.add(worldKey(residualShelf));
        if (!exactEnvironment) {
            seedNeighborTree(centerX, sourceTopY, centerZ);
        }
        dna.captureOriginalShape(sourceLogs, sourceLeaves);
    }

    private void applyCapturedEnvironment(
            TreeCapturedEnvironmentFixture captured
    ) {
        for (TreeCapturedEnvironmentFixture.Cell cell
                : captured.cells()) {
            int x = dna.baseX() + cell.relativeX();
            int y = dna.baseY() + cell.relativeY();
            int z = dna.baseZ() + cell.relativeZ();
            String key = key(x, y, z);
            if (cell.category()
                    == TreeCapturedEnvironmentFixture.Category.AIR_OVERRIDE) {
                world.clearSeed(key);
                continue;
            }
            if (cell.category()
                    == TreeCapturedEnvironmentFixture.Category.ENVIRONMENT) {
                world.environment().seedTerrain(
                        x, y, z, cell.material(), cell.natural(),
                        cell.replaceable(), cell.playerPlaced());
                continue;
            }
            TreeConstructionReplayWorld.Ownership ownership = switch (
                    cell.category()) {
                case SOURCE -> Ownership.SOURCE;
                case EVOLVED -> Ownership.EVOLVED;
                case PLANNED_UNOWNED -> Ownership.SOURCE;
                case NEIGHBOR -> Ownership.NEIGHBOR;
                case ENVIRONMENT, AIR_OVERRIDE -> throw new IllegalStateException(
                        "non-tree category reached tree ownership mapping");
            };
            TreeBlockRole role = cell.role() != null
                    ? cell.role()
                    : cell.material().name().endsWith("_LEAVES")
                            ? TreeBlockRole.CANOPY
                            : TreeBlockRole.TRUNK;
            world.seed(key, cell.material(), role, ownership,
                    cell.persistent());
        }
        for (TreeCapturedEnvironmentFixture.UnreadableArea area
                : captured.unreadableAreas()) {
            world.environment().temporarilyUnavailable(
                    new TreeSimulatedMinecraftEnvironment.Box(
                            dna.baseX() + area.minimumRelativeX(),
                            dna.baseY() - 4,
                            dna.baseZ() + area.minimumRelativeZ(),
                            dna.baseX() + area.maximumRelativeX(),
                            dna.baseY() + 64,
                            dna.baseZ() + area.maximumRelativeZ()),
                    3L, area.gate());
        }
    }

    private void seedCapturedSourceTree() {
        Set<String> allWood = new HashSet<>();
        allWood.addAll(dna.originalShapeLogs());
        allWood.addAll(dna.evolvedShapeLogs());
        Set<String> rootedWood =
                TreeWoodOwnershipGraph.connectedToRoot(dna, allWood);
        for (String sourceKey : dna.originalShapeLogs()) {
            Coordinate coordinate = coordinate(sourceKey);
            PlannedTreeBlock planned = plan.blocksByKey().get(key(
                    coordinate.x(), coordinate.y(), coordinate.z()));
            world.seed(
                    key(coordinate.x(), coordinate.y(), coordinate.z()),
                    dna.species().logMaterial(),
                    planned != null
                            && planned.role() == TreeBlockRole.BRANCH
                            ? TreeBlockRole.BRANCH
                            : TreeBlockRole.TRUNK,
                    rootedWood.contains(sourceKey)
                            ? Ownership.SOURCE : Ownership.NEIGHBOR);
        }
        for (String sourceKey : dna.originalShapeLeaves()) {
            if (dna.retiredOriginalShapeLeaves().contains(sourceKey)) {
                continue;
            }
            Coordinate coordinate = coordinate(sourceKey);
            String key = key(
                    coordinate.x(), coordinate.y(), coordinate.z());
            if (world.cell(key) == null) {
                world.seed(
                        key,
                        dna.species().leafMaterial(),
                        TreeBlockRole.CANOPY,
                        Ownership.SOURCE);
            }
        }
        for (String evolvedKey : dna.evolvedShapeLogs()) {
            Coordinate coordinate = coordinate(evolvedKey);
            String key = key(
                    coordinate.x(), coordinate.y(), coordinate.z());
            PlannedTreeBlock planned = plan.blocksByKey().get(key);
            TreeBlockRole role = planned != null
                    && planned.role() == TreeBlockRole.BRANCH
                    ? TreeBlockRole.BRANCH : TreeBlockRole.TRUNK;
            world.seed(
                    key, dna.species().logMaterial(),
                    role, Ownership.EVOLVED);
            // ## An evolved receipt is exact plugin provenance even when its
            // live voxel became disconnected. Preserve that ownership so the
            // hierarchy can retire and parent-link it again; only unreceipted
            // disconnected source wood is treated as neighboring structure.
        }
        for (String evolvedKey : dna.evolvedShapeLeaves()) {
            Coordinate coordinate = coordinate(evolvedKey);
            world.seed(
                    key(coordinate.x(), coordinate.y(), coordinate.z()),
                    dna.species().leafMaterial(),
                    TreeBlockRole.CANOPY,
                    Ownership.EVOLVED);
        }
    }

    private void seedObsoleteEvolvedStructure() {
        seedLegacyPersistentEnvelopeLeaf();
        List<int[]> directions = List.of(
                new int[]{1, 0, 0},
                new int[]{-1, 0, 0},
                new int[]{0, 0, 1},
                new int[]{0, 0, -1});
        for (var entry : world.cells().entrySet().stream()
                // ## Current-only captures already own their rooted trunk as
                // EVOLVED. Both source and evolved trunk voxels are legitimate
                // anchors for the synthetic stale-limb reconciliation case;
                // neighboring-tree ownership is never eligible.
                .filter(candidate -> candidate.getValue().ownership()
                        != Ownership.NEIGHBOR)
                .filter(candidate -> candidate.getValue().role()
                        == TreeBlockRole.TRUNK)
                .sorted((first, second) -> Integer.compare(
                        coordinate(second.getKey()).y(),
                        coordinate(first.getKey()).y()))
                .toList()) {
            Coordinate support = coordinate(entry.getKey());
            for (int[] direction : directions) {
                String staleWood = key(
                        support.x() + direction[0],
                        support.y() + direction[1],
                        support.z() + direction[2]);
                if (trySeedObsoleteStructure(staleWood)) {
                    return;
                }
            }
        }
        // ## Some captured malformed trees contain no rooted owned trunk at
        // replay entry. Seed the synthetic cleanup case outside the target
        // instead of borrowing disconnected source wood that belongs to an
        // unknown neighbor. The hierarchy must still retire the exact evolved
        // receipts before declaring the stage complete.
        int y = dna.baseY() + 2;
        for (int radius = 2; radius <= 12; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (trySeedObsoleteStructure(
                        key(dna.baseX() + dx, y,
                                dna.baseZ() - radius))
                        || trySeedObsoleteStructure(
                                key(dna.baseX() + dx, y,
                                        dna.baseZ() + radius))) {
                    return;
                }
            }
        }
        throw new IllegalStateException(
                "could not inject obsolete evolved structure variant="
                        + dna.variant() + " stage="
                        + dna.maturityStage() + " sourceLogs="
                        + dna.originalShapeLogCount()
                        + " evolvedLogs="
                        + dna.evolvedShapeLogs().size()
                        + " worldCells=" + world.cells().size());
    }

    private void seedLegacyPersistentEnvelopeLeaf() {
        for (TreeBranchPlan.BranchTip tip
                : plan.branchEnvelopeCleanupTips()) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        String staleLeaf = key(
                                tip.x() + dx, tip.y() + dy,
                                tip.z() + dz);
                        if (plan.blocksByKey().containsKey(staleLeaf)
                                || world.cell(staleLeaf) != null) {
                            continue;
                        }
                        // ## This models leaves from the retired forced-tip
                        // implementation. They were persistent world voxels,
                        // but predated the current evolved-leaf receipt ledger.
                        world.seed(
                                staleLeaf,
                                dna.species().leafMaterial(),
                                TreeBlockRole.CANOPY,
                                Ownership.SOURCE,
                                true);
                        return;
                    }
                }
            }
        }
    }

    private boolean trySeedObsoleteStructure(String staleWood) {
        if (plan.blocksByKey().containsKey(staleWood)
                || world.cell(staleWood) != null) {
            return false;
        }
        Coordinate support = coordinate(staleWood);
        List<String> leaves = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    String staleLeaf = key(
                            support.x() + dx, support.y() + dy,
                            support.z() + dz);
                    if (!plan.blocksByKey().containsKey(staleLeaf)
                            && world.cell(staleLeaf) == null) {
                        leaves.add(staleLeaf);
                    }
                }
            }
        }
        if (leaves.size() < 3) {
            return false;
        }
        world.seed(staleWood, dna.species().logMaterial(),
                TreeBlockRole.BRANCH, Ownership.EVOLVED);
        requireRecorded(staleWood, TreeBlockRole.BRANCH);
        for (String staleLeaf : leaves.subList(0, 3)) {
            world.seed(
                    staleLeaf, dna.species().leafMaterial(),
                    TreeBlockRole.CANOPY, Ownership.EVOLVED);
            requireRecorded(staleLeaf, TreeBlockRole.CANOPY);
        }
        return true;
    }

    private void requireRecorded(String blockKey, TreeBlockRole role) {
        if (!dna.markEvolvedBlock(worldKey(blockKey), role)) {
            throw new IllegalStateException(
                    "could not record injected evolved block " + blockKey);
        }
    }

    private void seedNeighborTree(int centerX, int y, int centerZ) {
        String anchor = firstFreeOutsideTarget(
                centerX, y, centerZ, 7);
        Coordinate base = coordinate(anchor);
        for (int dy = 0; dy <= 2; dy++) {
            String key = key(base.x(), base.y() + dy, base.z());
            if (!plan.blocksByKey().containsKey(key)
                    && world.cell(key) == null) {
                world.seed(
                        key, dna.species().logMaterial(),
                        TreeBlockRole.TRUNK, Ownership.NEIGHBOR);
            }
        }
        int topY = base.y() + 2;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                String key = key(
                        base.x() + dx, topY, base.z() + dz);
                if (!plan.blocksByKey().containsKey(key)
                        && world.cell(key) == null) {
                    world.seed(
                            key, dna.species().leafMaterial(),
                            TreeBlockRole.CANOPY, Ownership.NEIGHBOR);
                }
            }
        }
    }

    private String firstFreeOutsideTarget(
            int centerX, int y, int centerZ, int startingRadius) {
        for (int radius = startingRadius;
                radius <= startingRadius + 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz : List.of(-radius, radius)) {
                    String key = key(
                            centerX + dx, y, centerZ + dz);
                    if (!plan.blocksByKey().containsKey(key)
                            && world.cell(key) == null) {
                        return key;
                    }
                }
            }
        }
        throw new IllegalStateException(
                "could not seed outside target");
    }

    private String worldKey(String coordinateKey) {
        return dna.worldId() + ":" + coordinateKey;
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static Coordinate coordinate(String value) {
        String[] parts = value.split(":");
        int offset = parts.length - 3;
        return new Coordinate(
                Integer.parseInt(parts[offset]),
                Integer.parseInt(parts[offset + 1]),
                Integer.parseInt(parts[offset + 2]));
    }

    private record Coordinate(int x, int y, int z) {
    }
}
