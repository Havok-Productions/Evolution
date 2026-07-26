package org.evolution.features.treeevolution;

public final class TreeFocusPolicySmokeTest {
    private TreeFocusPolicySmokeTest() {
    }

    public static void main(String[] args) {
        TreeGrowthQueuePolicy.Budget budget =
                new TreeGrowthQueuePolicy.Budget(0.98D, 1.0D, 0.82D);
        TreeGrowthQueuePolicy.Completion unfinished =
                new TreeGrowthQueuePolicy.Completion(
                        13, 13, 13, 13, 12, 18, 380, 493);
        TreeGrowthQueuePolicy.Completion finished =
                new TreeGrowthQueuePolicy.Completion(
                        13, 13, 13, 13, 18, 18, 410, 493);

        require(TreeFocusPolicy.needsFocus(false, unfinished, budget, 0, 0),
                "full-plan canopy completion must keep an unfinished tree focused");
        require(!TreeFocusPolicy.needsFocus(false, finished, budget, 0, 0),
                "a completed structural budget must release tree focus");
        require(TreeFocusPolicy.needsFocus(false, finished, budget, 0, 1),
                "an uncovered live branch tip must retain focus even after global canopy completion");
        require(TreeFocusPolicy.needsFocus(true, finished, budget, 0, 0),
                "an active transition must retain focus until its state is closed");
        require(TreeFocusPolicy.transitionPending(
                        0, 0, false, true),
                "an orphaned source snapshot must retain focus after numeric bursts expire");
        require(TreeFocusPolicy.transitionPending(
                        0, 0, true, true),
                "a complete source snapshot remains pending until finalization closes it");
        require(!TreeFocusPolicy.transitionPending(
                        0, 0, true, false),
                "a finalized complete tree must release transition focus");
        require(TreeFocusPolicy.completeOwnershipRequired(
                        0, 0, false, true, true, 6),
                "an orphaned completed source crown must reacquire full ownership for cleanup");
        require(!TreeFocusPolicy.completeOwnershipRequired(
                        0, 0, false, false, true, 6),
                "an incomplete target may keep building from compact known-DNA validation");

        require(TreeFocusPolicy.shouldFinalizeTransition(
                        true, 0, 0, true, 0),
                "a complete tree must release a stale snapshot after its action allowance expires");
        require(TreeFocusPolicy.shouldFinalizeTransition(
                        true, 0, 5, false, 0),
                "a complete tree must clear unused transition allowance");
        require(!TreeFocusPolicy.shouldFinalizeTransition(
                        false, 0, 0, true, 0),
                "an incomplete projected tree must retain its source snapshot");
        require(!TreeFocusPolicy.shouldFinalizeTransition(
                        true, 6, 0, true, 0),
                "active cleanup must finish before transition state is released");
        require(!TreeFocusPolicy.shouldFinalizeTransition(
                        true, 0, 0, true, 1),
                "an unresolved captured source leaf must keep the snapshot open");
        require(TreeFocusPolicy.readyForMaturity(
                        true, 0, 0, false),
                "a structurally complete and finalized stage may mature");
        require(!TreeFocusPolicy.readyForMaturity(
                        false, 0, 0, false),
                "age and height must not advance an incomplete stage");
        require(!TreeFocusPolicy.readyForMaturity(
                        true, 0, 0, true),
                "a source snapshot must finalize before the next stage begins");

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
