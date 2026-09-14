package org.evolution.features.treeevolution;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;

/**
 * ## Final monotonic guard for destructive constructor mutations.
 *
 * <p>Selection policies may evolve independently, but the world-change
 * boundary never removes an evolution-owned voxel that still satisfies the
 * active target. A changed plan naturally makes the old coordinate obsolete,
 * while a role conflict remains eligible for explicit reconciliation.</p>
 */
final class TreeConstructionMutationPolicy {
    private TreeConstructionMutationPolicy() {
    }

    static Retirement retirement(
            TreeDna dna,
            CachedTreePlan plan,
            String worldKey,
            Material liveMaterial,
            boolean leaf
    ) {
        String coordinateKey = coordinateKey(worldKey);
        PlannedTreeBlock target = plan.blocksByKey().get(coordinateKey);
        if (target == null) {
            return new Retirement(true, "outside-current-target");
        }
        boolean roleMatches = leaf
                ? target.role() == TreeBlockRole.CANOPY
                : target.role() == TreeBlockRole.TRUNK
                        || target.role() == TreeBlockRole.BRANCH
                        || target.role() == TreeBlockRole.ROOT;
        if (!roleMatches || target.material() != liveMaterial) {
            return new Retirement(true, "current-target-role-conflict");
        }
        boolean receiptOwned = leaf
                ? dna.evolvedShapeLeaves().contains(worldKey)
                : dna.evolvedShapeLogs().contains(worldKey);
        if (!receiptOwned) {
            return new Retirement(true, "ownership-unproven");
        }
        return new Retirement(
                false, "current-target-evolved-voxel-protected");
    }

    static Retirement rootedTargetRetirement(
            TreeDna dna,
            Collection<String> liveOwnedWood,
            String retiringWorldKey
    ) {
        Set<String> remaining = new HashSet<>(liveOwnedWood);
        remaining.remove(retiringWorldKey);
        Set<String> rooted = TreeWoodOwnershipGraph.connectedToRoot(
                dna, remaining);
        Set<String> required = new HashSet<>(liveOwnedWood);
        required.remove(retiringWorldKey);
        if (!rooted.containsAll(required)) {
            Set<String> disconnected = new HashSet<>(required);
            disconnected.removeAll(rooted);
            return new Retirement(
                    false, "current-target-dependency-protected:"
                            + disconnected.size());
        }
        return new Retirement(true, "rooted-targets-preserved");
    }

    static TargetRetirement targetRetirement(
            TreeDna dna,
            CachedTreePlan plan,
            Set<String> liveOwnedWood,
            Set<String> evolvedWood,
            Set<String> evolvedCanopy,
            Set<String> livePlannedWood,
            Set<String> currentTargetWood,
            Set<String> protectedCoordinates,
            String retiringWorldKey
    ) {
        Retirement safety = rootedTargetRetirement(
                dna, liveOwnedWood, retiringWorldKey);
        if (safety.allowed()) {
            return new TargetRetirement(
                    true, Optional.empty(), Optional.empty(),
                    safety.reason());
        }
        Optional<String> prerequisite =
                TreeConflictRetirementPolicy.next(
                        dna, liveOwnedWood, evolvedWood,
                        evolvedCanopy, currentTargetWood,
                        retiringWorldKey)
                        .filter(selected ->
                                !selected.equals(retiringWorldKey));
        if (prerequisite.isPresent()) {
            return new TargetRetirement(
                    false, Optional.empty(), prerequisite,
                    safety.reason());
        }
        Set<String> hypotheticalOwned = new HashSet<>(liveOwnedWood);
        hypotheticalOwned.remove(retiringWorldKey);
        TreeTargetOwnershipRepairPolicy.Analysis analysis =
                TreeTargetOwnershipRepairPolicy.inspect(
                        dna, plan.orderedBlocks(), hypotheticalOwned,
                        livePlannedWood, protectedCoordinates);
        return new TargetRetirement(
                false, analysis.repair(), Optional.empty(),
                safety.reason());
    }

    private static String coordinateKey(String worldKey) {
        String[] parts = worldKey.split(":");
        if (parts.length < 4) {
            return worldKey;
        }
        return parts[parts.length - 3] + ":"
                + parts[parts.length - 2] + ":"
                + parts[parts.length - 1];
    }

    record Retirement(boolean allowed, String reason) {
    }

    record TargetRetirement(
            boolean safeToRetire,
            Optional<TreeTargetOwnershipRepairPolicy.Repair> bridge,
            Optional<String> prerequisiteRetirement,
            String reason
    ) {
    }
}
