package org.evolution.features.treeevolution;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Cell;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Ownership;

/**
 * ## Independent coordinate diff between a target and a captured live volume.
 */
final class TreeLiveVoxelDiff {
    private TreeLiveVoxelDiff() {
    }

    static Report compare(
            TreeDna dna,
            TreePlan plan,
            Map<String, Cell> live
    ) {
        return compare(dna, plan.orderedBlocks(), live);
    }

    static Report compare(
            TreeDna dna,
            java.util.List<PlannedTreeBlock> effectiveTargets,
            Map<String, Cell> live
    ) {
        int matched = 0;
        int missing = 0;
        int wrongMaterial = 0;
        int foreignAtTarget = 0;
        Set<String> targetKeys = new HashSet<>();
        for (PlannedTreeBlock target : effectiveTargets) {
            if (!isTreeBody(target.role())) {
                continue;
            }
            targetKeys.add(target.key());
            Cell cell = live.get(target.key());
            if (cell == null) {
                missing++;
            } else if (cell.material() != target.material()) {
                wrongMaterial++;
            } else if (target.role() == TreeBlockRole.CANOPY
                    && cell.ownership() != Ownership.EVOLVED) {
                foreignAtTarget++;
            } else {
                matched++;
            }
        }
        int extraEvolved = 0;
        int protectedNeighbor = 0;
        for (Map.Entry<String, Cell> entry : live.entrySet()) {
            Cell cell = entry.getValue();
            if (cell.ownership() == Ownership.NEIGHBOR) {
                protectedNeighbor++;
            } else if (cell.ownership() == Ownership.EVOLVED
                    && isTreeBody(cell.role())
                    && !targetKeys.contains(entry.getKey())) {
                extraEvolved++;
            }
        }
        return new Report(
                matched, missing, wrongMaterial, foreignAtTarget,
                extraEvolved, protectedNeighbor,
                targetKeys.size(),
                dna.variant());
    }

    private static boolean isTreeBody(TreeBlockRole role) {
        return role == TreeBlockRole.TRUNK
                || role == TreeBlockRole.BRANCH
                || role == TreeBlockRole.CANOPY;
    }

    record Report(
            int matched,
            int missing,
            int wrongMaterial,
            int foreignAtTarget,
            int extraEvolved,
            int protectedNeighbor,
            int target,
            TreeVariant variant
    ) {
        boolean exact() {
            return missing == 0
                    && wrongMaterial == 0
                    && foreignAtTarget == 0
                    && extraEvolved == 0
                    && matched == target;
        }

        String csv() {
            return String.join(",",
                    variant.id(),
                    String.valueOf(target),
                    String.valueOf(matched),
                    String.valueOf(missing),
                    String.valueOf(wrongMaterial),
                    String.valueOf(foreignAtTarget),
                    String.valueOf(extraEvolved),
                    String.valueOf(protectedNeighbor),
                    String.valueOf(exact()));
        }
    }
}
