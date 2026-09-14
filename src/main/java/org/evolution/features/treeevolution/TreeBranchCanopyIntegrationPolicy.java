package org.evolution.features.treeevolution;

import java.util.Map;

/**
 * Universal contract joining planned limbs to their species-shaped crown.
 *
 * <p>## Species planners may choose different silhouettes, but no planner may
 * leave the distal branch frame visually detached from its owned foliage.</p>
 */
final class TreeBranchCanopyIntegrationPolicy {
    private static final int[][] DIRECT_NEIGHBORS = {
            {0, 1, 0}, {0, -1, 0},
            {1, 0, 0}, {-1, 0, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private TreeBranchCanopyIntegrationPolicy() {
    }

    static boolean requiresCover(
            TreeBranchPlan branch,
            TreeBranchPlan.BranchSegment segment) {
        int maximumStep = branch.segments().stream()
                .mapToInt(TreeBranchPlan.BranchSegment::step)
                .max()
                .orElse(0);
        int firstCoveredStep = Math.max(
                2, (int) Math.ceil(maximumStep * 0.50D));
        return segment.step() >= firstCoveredStep;
    }

    static int requiredDirectContacts(TreeDna dna) {
        // ## Broad, visible acacia forks need a leafy throat on two faces.
        // One owned contact is enough to integrate slimmer species branches.
        return dna.species() == TreeSpecies.ACACIA ? 2 : 1;
    }

    static int desiredDirectContacts(
            TreeDna dna,
            int x,
            int y,
            int z,
            Map<String, PlannedTreeBlock> blocksByKey) {
        int availableFaces = 0;
        for (int[] offset : DIRECT_NEIGHBORS) {
            PlannedTreeBlock neighbor = blocksByKey.get(
                    (x + offset[0]) + ":"
                            + (y + offset[1]) + ":"
                            + (z + offset[2]));
            if (neighbor == null
                    || neighbor.role() == TreeBlockRole.CANOPY) {
                availableFaces++;
            }
        }
        // ## A wood-encased junction has no visibly exposed face to cover.
        return Math.min(requiredDirectContacts(dna), availableFaces);
    }

    static int targetDirectContacts(
            TreeDna dna,
            int x,
            int y,
            int z,
            Map<String, PlannedTreeBlock> blocksByKey) {
        int plannedContacts = TreeBranchTipIntegrityPolicy
                .plannedLeafContacts(
                        x, y, z, dna.species().leafMaterial(), blocksByKey);
        // ## Runtime may only demand leaves represented by the immutable final
        // target. Treating an empty planning face as a promised leaf created an
        // impossible TREE_42 retry when a species envelope rejected that face.
        return Math.min(requiredDirectContacts(dna), plannedContacts);
    }
}
