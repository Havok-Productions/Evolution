package org.evolution.features.treeevolution;

final class TreeGrowthQueuePolicy {
    private TreeGrowthQueuePolicy() {
    }

    static Budget stageBudget(TreeDna dna) {
        return stageBudget(dna.maturityStage());
    }

    static Budget stageBudget(TreeMaturityStage stage) {
        // ## A projected smoke model is the constructor contract, not a suggestion.
        // Every stage finishes its reachable structure before the queue releases it.
        return new Budget(1.0D, 1.0D, 1.0D);
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