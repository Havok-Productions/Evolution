package org.slowtrees.treeevolution;

final class TreeGrowthContract {
    private TreeGrowthContract() {
    }

    static Assessment assess(
            TreeDna dna,
            TreeGrowthQueuePolicy.Completion completion,
            TreeGrowthQueuePolicy.Budget budget,
            TreeGrowthIntent originalIntent,
            int connectedLogs,
            int connectedLeaves,
            int exposedUpperLogs
    ) {
        if (originalIntent == TreeGrowthIntent.REPAIR
                || originalIntent == TreeGrowthIntent.CLEANUP
                || dna.damageCount() > 0) {
            return new Assessment(originalIntent, Phase.REPAIR, "repair-or-cleanup", false, connectedLogs, connectedLeaves, exposedUpperLogs);
        }

        TreeGrowthIntent selected = originalIntent;
        Phase phase = Phase.WEIGHTED;
        String reason = "weighted";
        boolean corrective = false;
        double leafWoodRatio = connectedLogs <= 0 ? connectedLeaves : connectedLeaves / (double) connectedLogs;
        boolean trunkBehind = completion.trunkPercent() < budget.trunkPercent();
        boolean upperCrownReady = completion.liveHeight() >= crownReadyHeight(completion, dna);
        boolean canopyTooThin = completion.canopyTotal() > 0
                && upperCrownReady
                && (completion.canopyPercent() < canopyShellFloor(dna) || leafWoodRatio < liveLeafWoodFloor(dna));
        boolean branchOutranCanopy = completion.branchTotal() > 0
                && completion.branchPercent() - completion.canopyPercent() >= branchCanopyGap(dna);

        if (trunkBehind) {
            selected = TreeGrowthIntent.HEIGHT;
            phase = Phase.SUPPORT_STRUCTURE;
            reason = "support-trunk";
            corrective = true;
        } else if (exposedUpperLogs > 0 && upperCrownReady) {
            selected = TreeGrowthIntent.CANOPY;
            phase = Phase.CANOPY_SHELL;
            reason = "cover-exposed-upper-log";
            corrective = true;
        } else if (canopyTooThin) {
            selected = TreeGrowthIntent.CANOPY;
            phase = Phase.CANOPY_SHELL;
            reason = "canopy-shell";
            corrective = true;
        } else if (branchOutranCanopy) {
            selected = TreeGrowthIntent.CANOPY;
            phase = Phase.SHAPE_INTEGRITY;
            reason = "branch-outran-canopy";
            corrective = true;
        } else if (completion.branchTotal() > 0 && completion.branchPercent() < budget.branchPercent()) {
            selected = TreeGrowthIntent.BRANCH;
            phase = Phase.SCAFFOLD_BRANCHES;
            reason = "branch-scaffold";
        } else if (completion.canopyTotal() > 0 && completion.canopyPercent() < budget.canopyPercent()) {
            selected = TreeGrowthIntent.CANOPY;
            phase = Phase.CANOPY_THICKENING;
            reason = "canopy-budget";
        } else if (originalIntent == TreeGrowthIntent.DETAIL || originalIntent == TreeGrowthIntent.SEEDLING) {
            phase = Phase.DETAIL_PASS;
            reason = "stage-complete";
        }

        return new Assessment(selected, phase, reason, corrective, connectedLogs, connectedLeaves, exposedUpperLogs);
    }

    private static int crownReadyHeight(TreeGrowthQueuePolicy.Completion completion, TreeDna dna) {
        double ratio = switch (dna.species()) {
            case BIRCH, JUNGLE -> 0.68D;
            case SPRUCE -> 0.44D;
            case ACACIA, CHERRY, DARK_OAK -> 0.54D;
            case MANGROVE, OAK -> 0.58D;
        };
        return Math.max(4, (int) Math.round(completion.visibleHeight() * ratio));
    }

    private static double canopyShellFloor(TreeDna dna) {
        return switch (dna.maturityStage()) {
            case SMALL -> 0.22D;
            case MEDIUM -> 0.30D;
            case MATURE -> 0.36D;
            case ANCIENT -> 0.38D;
        };
    }

    private static double liveLeafWoodFloor(TreeDna dna) {
        return switch (dna.species()) {
            case BIRCH -> 0.55D;
            case SPRUCE -> 0.60D;
            case ACACIA -> 0.45D;
            case JUNGLE, DARK_OAK, CHERRY -> 0.85D;
            case MANGROVE, OAK -> 0.75D;
        };
    }

    private static double branchCanopyGap(TreeDna dna) {
        return switch (dna.species()) {
            case BIRCH -> 0.34D;
            case SPRUCE -> 0.22D;
            case ACACIA -> 0.26D;
            default -> 0.18D;
        };
    }

    enum Phase {
        SUPPORT_STRUCTURE,
        CANOPY_SHELL,
        SCAFFOLD_BRANCHES,
        CANOPY_THICKENING,
        DETAIL_PASS,
        SHAPE_INTEGRITY,
        REPAIR,
        WEIGHTED
    }

    record Assessment(
            TreeGrowthIntent intent,
            Phase phase,
            String reason,
            boolean corrective,
            int connectedLogs,
            int connectedLeaves,
            int exposedUpperLogs
    ) {
        String summary(TreeGrowthQueuePolicy.Completion completion, TreeGrowthQueuePolicy.Budget budget, TreeGrowthIntent originalIntent) {
            return "phase=" + phase
                    + " reason=" + reason
                    + " selected=" + intent
                    + " original=" + originalIntent
                    + " corrective=" + corrective
                    + " live-logs=" + connectedLogs
                    + " live-leaves=" + connectedLeaves
                    + " exposed-upper-logs=" + exposedUpperLogs
                    + " trunk=" + completion.trunkSummary() + "/target=" + pct(budget.trunkPercent())
                    + " branch=" + completion.branchSummary() + "/target=" + pct(budget.branchPercent())
                    + " canopy=" + completion.canopySummary() + "/target=" + pct(budget.canopyPercent());
        }

        private static String pct(double value) {
            return Math.round(value * 100.0D) + "%";
        }
    }
}
