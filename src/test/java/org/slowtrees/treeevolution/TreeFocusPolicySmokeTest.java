package org.slowtrees.treeevolution;

public final class TreeFocusPolicySmokeTest {
    private TreeFocusPolicySmokeTest() {
    }

    public static void main(String[] args) {
        TreeGrowthQueuePolicy.Budget budget =
                new TreeGrowthQueuePolicy.Budget(0.88D, 0.34D, 0.50D);
        TreeGrowthQueuePolicy.Completion unfinished =
                new TreeGrowthQueuePolicy.Completion(
                        13, 13, 13, 13, 5, 18, 200, 493);
        TreeGrowthQueuePolicy.Completion finished =
                new TreeGrowthQueuePolicy.Completion(
                        13, 13, 13, 13, 7, 18, 260, 493);

        require(TreeFocusPolicy.needsFocus(false, unfinished, budget, 0),
                "full-plan canopy completion must keep an unfinished tree focused");
        require(!TreeFocusPolicy.needsFocus(false, finished, budget, 0),
                "a completed structural budget must release tree focus");
        require(TreeFocusPolicy.needsFocus(true, finished, budget, 0),
                "an active transition must retain focus until its state is closed");

        int noProgress = 0;
        for (int pass = 0; pass < TreeFocusPolicy.MAX_NO_PROGRESS_PASSES; pass++) {
            noProgress = TreeFocusPolicy.nextNoProgressPasses(noProgress, false);
        }
        require(TreeFocusPolicy.shouldYield(noProgress),
                "a blocked focused tree must eventually yield to another candidate");
        require(TreeFocusPolicy.nextNoProgressPasses(noProgress, true) == 0,
                "successful work must reset the no-progress counter");

        System.out.println("Tree focus policy smoke test passed: "
                + "full-plan-completion=true bounded-yield=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
