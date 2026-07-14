package org.slowtrees.treeevolution;

final class TreeGrowthQueuePolicy {
    private TreeGrowthQueuePolicy() {
    }

    static Selection select(TreeDna dna, Completion completion, Budget budget, TreeGrowthIntent originalIntent) {
        TreeGrowthIntent selected = originalIntent;
        String reason = "weighted";
        if (completion.trunkPercent() < budget.trunkPercent()) {
            selected = TreeGrowthIntent.HEIGHT;
            reason = "trunk-budget";
        } else if (shouldCatchUpCanopy(dna, completion, budget)) {
            selected = TreeGrowthIntent.CANOPY;
            reason = "canopy-catch-up";
        } else if (completion.branchTotal() > 0 && completion.branchPercent() < budget.branchPercent()) {
            selected = TreeGrowthIntent.BRANCH;
            reason = "branch-budget";
        } else if (completion.canopyTotal() > 0 && completion.canopyPercent() < budget.canopyPercent()) {
            selected = TreeGrowthIntent.CANOPY;
            reason = "canopy-budget";
        } else if (originalIntent == TreeGrowthIntent.DETAIL
                || originalIntent == TreeGrowthIntent.SEEDLING
                || originalIntent == TreeGrowthIntent.CLEANUP) {
            selected = originalIntent;
            reason = "stage-complete";
        }
        return new Selection(selected, reason);
    }

    static Budget stageBudget(TreeDna dna) {
        return switch (dna.maturityStage()) {
            case SMALL -> new Budget(0.82D, 0.12D, 0.42D);
            case MEDIUM -> new Budget(0.88D, 0.34D, 0.50D);
            case MATURE -> new Budget(0.96D, 0.54D, 0.58D);
            case ANCIENT -> new Budget(0.98D, 0.48D, 0.56D);
        };
    }

    private static boolean shouldCatchUpCanopy(TreeDna dna, Completion completion, Budget budget) {
        if (completion.canopyTotal() <= 0) {
            return false;
        }
        double canopy = completion.canopyPercent();
        double branch = completion.branchPercent();
        double earlyCanopyFloor = switch (dna.maturityStage()) {
            case SMALL -> 0.18D;
            case MEDIUM -> 0.24D;
            case MATURE -> 0.30D;
            case ANCIENT -> 0.32D;
        };
        boolean enoughTrunkForCrown = completion.liveHeight() >= Math.max(4, (int) Math.round(completion.visibleHeight() * 0.62D))
                && completion.trunkPercent() >= 0.62D;
        if (enoughTrunkForCrown && canopy < Math.min(budget.canopyPercent(), earlyCanopyFloor)) {
            return true;
        }
        if (completion.branchTotal() > 0
                && branch >= Math.min(0.30D, budget.branchPercent())
                && branch - canopy >= 0.18D) {
            return true;
        }
        return completion.branchTotal() > 0
                && branch >= budget.branchPercent()
                && canopy < budget.canopyPercent();
    }

    record Selection(TreeGrowthIntent intent, String reason) {
    }

    record Budget(double trunkPercent, double branchPercent, double canopyPercent) {
    }

    record Completion(int liveHeight, int visibleHeight, int trunkPlaced, int trunkTotal, int branchPlaced, int branchTotal, int canopyPlaced, int canopyTotal) {
        double trunkPercent() {
            if (trunkTotal > 0) {
                return trunkPlaced / (double) trunkTotal;
            }
            return Math.min(1.0D, liveHeight / (double) Math.max(1, visibleHeight));
        }

        double branchPercent() {
            return branchTotal == 0 ? 1.0D : branchPlaced / (double) branchTotal;
        }

        double canopyPercent() {
            return canopyTotal == 0 ? 1.0D : canopyPlaced / (double) canopyTotal;
        }

        String trunkSummary() {
            return trunkPlaced + "/" + trunkTotal + "=" + pct(trunkPercent()) + " height=" + liveHeight + "/" + visibleHeight;
        }

        String branchSummary() {
            return branchPlaced + "/" + branchTotal + "=" + pct(branchPercent());
        }

        String canopySummary() {
            return canopyPlaced + "/" + canopyTotal + "=" + pct(canopyPercent());
        }

        private static String pct(double value) {
            return Math.round(value * 100.0D) + "%";
        }
    }
}