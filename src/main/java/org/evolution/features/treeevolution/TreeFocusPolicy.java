package org.evolution.features.treeevolution;

final class TreeFocusPolicy {
    static final int MAX_NO_PROGRESS_PASSES = 6;

    private TreeFocusPolicy() {
    }

    static boolean stageStructureComplete(
            TreeGrowthQueuePolicy.Completion completion,
            TreeGrowthQueuePolicy.Budget budget,
            int exposedUpperLogs,
            int uncoveredBranchTips
    ) {
        return exposedUpperLogs <= 0
                && uncoveredBranchTips <= 0
                && completion.trunkPercent() >= budget.trunkPercent()
                && (completion.branchTotal() <= 0
                        || completion.branchPercent() >= budget.branchPercent())
                && (completion.canopyTotal() <= 0
                        || completion.canopyPercent() >= budget.canopyPercent());
    }

    static boolean needsFocus(boolean transitionPending,
            TreeGrowthQueuePolicy.Completion completion,
            TreeGrowthQueuePolicy.Budget budget,
            int exposedUpperLogs,
            int uncoveredBranchTips) {
        return transitionPending
                || !stageStructureComplete(
                        completion, budget, exposedUpperLogs, uncoveredBranchTips);
    }

    static boolean readyForMaturity(boolean stageComplete,
            int cleanupBurst, int growthBurst, boolean hasOriginalSnapshot) {
        return stageComplete
                && cleanupBurst <= 0
                && growthBurst <= 0
                && !hasOriginalSnapshot;
    }

    static boolean shouldFinalizeTransition(boolean stageComplete,
            int cleanupBurst, int growthBurst, boolean hasOriginalSnapshot) {
        return stageComplete
                && cleanupBurst <= 0
                && (growthBurst > 0 || hasOriginalSnapshot);
    }

    static int nextNoProgressPasses(int current, boolean changed) {
        return changed ? 0 : Math.min(MAX_NO_PROGRESS_PASSES, current + 1);
    }

    static boolean shouldYield(int noProgressPasses) {
        return noProgressPasses >= MAX_NO_PROGRESS_PASSES;
    }
}
