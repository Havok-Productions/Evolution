package org.slowtrees.treeevolution;

final class TreeFocusPolicy {
    static final int MAX_NO_PROGRESS_PASSES = 6;

    private TreeFocusPolicy() {
    }

    static boolean stageStructureComplete(
            TreeGrowthQueuePolicy.Completion completion,
            TreeGrowthQueuePolicy.Budget budget,
            int exposedUpperLogs
    ) {
        return exposedUpperLogs <= 0
                && completion.trunkPercent() >= budget.trunkPercent()
                && (completion.branchTotal() <= 0
                        || completion.branchPercent() >= budget.branchPercent())
                && (completion.canopyTotal() <= 0
                        || completion.canopyPercent() >= budget.canopyPercent());
    }

    static boolean needsFocus(boolean transitionPending,
            TreeGrowthQueuePolicy.Completion completion,
            TreeGrowthQueuePolicy.Budget budget,
            int exposedUpperLogs) {
        return transitionPending
                || !stageStructureComplete(completion, budget, exposedUpperLogs);
    }

    static int nextNoProgressPasses(int current, boolean changed) {
        return changed ? 0 : Math.min(MAX_NO_PROGRESS_PASSES, current + 1);
    }

    static boolean shouldYield(int noProgressPasses) {
        return noProgressPasses >= MAX_NO_PROGRESS_PASSES;
    }
}
