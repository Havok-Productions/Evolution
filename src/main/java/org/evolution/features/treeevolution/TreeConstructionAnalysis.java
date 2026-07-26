package org.evolution.features.treeevolution;

import java.util.List;
import java.util.Map;
import org.bukkit.block.Block;

/**
 * ## Immutable contracts shared by construction hierarchy services.
 *
 * <p>These records carry analysis results only. They never mutate the world,
 * which keeps phase decisions separate from their attached executors.</p>
 */
record CachedTreePlan(
        String signature,
        TreePlan plan,
        List<PlannedTreeBlock> orderedBlocks,
        Map<String, PlannedTreeBlock> blocksByKey
) {
}

record BranchTipCoverage(
        int liveTips,
        int uncoveredTips,
        Block firstUncoveredTip,
        int firstCurrentContacts,
        int firstRequiredContacts,
        int firstCurrentCluster,
        int firstRequiredCluster,
        int unplannedBareTips,
        Block firstUnplannedBareTip,
        int stalePersistentEnvelopeLeaves,
        Block firstStalePersistentEnvelopeLeaf
) {
}

record TreeProjectionProgress(
        int trunkPlaced,
        int trunkTotal,
        int branchPlaced,
        int branchTotal,
        int canopyPlaced,
        int canopyTotal
) {
    double branchPercent() {
        return branchTotal == 0 ? 1.0D : branchPlaced / (double) branchTotal;
    }

    double canopyPercent() {
        return canopyTotal == 0 ? 1.0D : canopyPlaced / (double) canopyTotal;
    }

    String branchSummary() {
        return branchPlaced + "/" + branchTotal + "="
                + Math.round(branchPercent() * 1000.0D) / 10.0D + "%";
    }

    String canopySummary() {
        return canopyPlaced + "/" + canopyTotal + "="
                + Math.round(canopyPercent() * 1000.0D) / 10.0D + "%";
    }
}

record PlannedTarget(
        PlannedTreeBlock block,
        Block target,
        int nextCursor,
        double shapeScore,
        String shapeReason
) {
}

record TreeWorkStatus(
        boolean needsFocus,
        boolean stageComplete,
        boolean transitionPending,
        boolean sourceSnapshot,
        int sourceBlocks,
        int unresolvedSourceLeaves,
        TreeGrowthQueuePolicy.Completion completion,
        TreeGrowthQueuePolicy.Budget budget,
        int exposedUpperLogs,
        int uncoveredBranchTips
) {
    String summary() {
        return "stage-complete=" + stageComplete
                + " transition-pending=" + transitionPending
                + " source-snapshot=" + sourceSnapshot
                + " source-blocks=" + sourceBlocks
                + " unresolved-source-leaves=" + unresolvedSourceLeaves
                + " trunk=" + completion.trunkSummary()
                + " branch=" + completion.branchSummary()
                + " canopy=" + completion.canopySummary()
                + " budget=" + percent(budget.trunkPercent())
                + "/" + percent(budget.branchPercent())
                + "/" + percent(budget.canopyPercent())
                + " exposed-upper-logs=" + exposedUpperLogs
                + " uncovered-branch-tips=" + uncoveredBranchTips;
    }

    private static String percent(double value) {
        return Math.round(value * 100.0D) + "%";
    }
}
