package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class TreeCanopyTransitionPolicy {
    private final Set<String> canopyKeys;
    private final Set<String> woodKeys;
    private final List<PlannedTreeBlock> woodTargets;
    private final int minimumCanopyY;
    private final int corridorMinimumY;
    private final int corridorMaximumY;
    private final int corridorRadius;

    private TreeCanopyTransitionPolicy(Set<String> canopyKeys, Set<String> woodKeys,
            List<PlannedTreeBlock> woodTargets, int minimumCanopyY,
            int corridorMinimumY, int corridorMaximumY, int corridorRadius) {
        this.canopyKeys = canopyKeys;
        this.woodKeys = woodKeys;
        this.woodTargets = woodTargets;
        this.minimumCanopyY = minimumCanopyY;
        this.corridorMinimumY = corridorMinimumY;
        this.corridorMaximumY = corridorMaximumY;
        this.corridorRadius = corridorRadius;
    }

    static TreeCanopyTransitionPolicy from(TreeDna dna,
            List<PlannedTreeBlock> orderedBlocks) {
        return from(dna, orderedBlocks,
                dna.baseY() + TreeSpeciesStageStyle.visibleHeight(dna) - 1);
    }

    static TreeCanopyTransitionPolicy from(TreeDna dna,
            List<PlannedTreeBlock> orderedBlocks, int observedTopY) {
        Set<String> canopyKeys = new HashSet<>();
        Set<String> woodKeys = new HashSet<>();
        List<PlannedTreeBlock> woodTargets = new ArrayList<>();
        int minimumCanopyY = Integer.MAX_VALUE;
        int maximumCanopyY = Integer.MIN_VALUE;
        for (PlannedTreeBlock block : orderedBlocks) {
            if (block.role() == TreeBlockRole.CANOPY) {
                canopyKeys.add(block.key());
                minimumCanopyY = Math.min(minimumCanopyY, block.y());
                maximumCanopyY = Math.max(maximumCanopyY, block.y());
            } else if (block.role() == TreeBlockRole.TRUNK
                    || block.role() == TreeBlockRole.BRANCH) {
                woodKeys.add(block.key());
                woodTargets.add(block);
            }
        }

        int visibleTop = dna.baseY() + TreeSpeciesStageStyle.visibleHeight(dna) - 1;
        if (minimumCanopyY == Integer.MAX_VALUE) {
            minimumCanopyY = visibleTop;
        }
        // ## Scan the complete former crown, not only the bare-trunk corridor.
        // Exact target leaves are protected separately, so residual old leaves at
        // crown height can clear without hollowing the new fluffy silhouette.
        int corridorMaximumY = Math.max(observedTopY + 1,
                visibleTop + TreeSpeciesStageStyle.canopyRadiusY(dna) + 1);
        if (maximumCanopyY != Integer.MIN_VALUE) {
            corridorMaximumY = Math.max(corridorMaximumY, maximumCanopyY);
        }
        int radius = Math.max(3, Math.min(8,
                Math.max(Math.max(dna.canopyRadiusX(), dna.canopyRadiusZ()),
                        Math.max(TreeSpeciesStageStyle.canopyRadiusX(dna),
                                TreeSpeciesStageStyle.canopyRadiusZ(dna))) + 2));
        return new TreeCanopyTransitionPolicy(
                Set.copyOf(canopyKeys), Set.copyOf(woodKeys), List.copyOf(woodTargets),
                minimumCanopyY, dna.baseY() + 2, corridorMaximumY, radius);
    }

    static double minimumReplacementCanopy(TreeDna dna) {
        // ## Grow before shedding. A substantial planned crown must already be
        // live before unrestricted old-crown cleanup can make the tree look sparse.
        return switch (dna.maturityStage()) {
            case SMALL -> 0.70D;
            case MEDIUM -> 0.72D;
            case MATURE -> 0.68D;
            case ANCIENT -> 0.65D;
        };
    }

    static boolean allowsBroadCleanup(TreeDna dna, double canopyPercent) {
        // ## Transitions are monotonic: establish the replacement crown before
        // removing non-blocking source leaves. This avoids visible leaf flicker.
        return canopyPercent >= minimumReplacementCanopy(dna);
    }

    boolean preservesLeaf(int x, int y, int z) {
        return canopyKeys.contains(key(x, y, z));
    }

    boolean replacesWithWood(int x, int y, int z) {
        return woodKeys.contains(key(x, y, z));
    }

    List<PlannedTreeBlock> woodTargets() {
        return woodTargets;
    }

    int minimumCanopyY() {
        return minimumCanopyY;
    }

    boolean isLegacyShelf(int y) {
        return y < minimumCanopyY;
    }

    int corridorMinimumY() {
        return corridorMinimumY;
    }

    int corridorMaximumY() {
        return corridorMaximumY;
    }

    int corridorRadius() {
        return corridorRadius;
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}