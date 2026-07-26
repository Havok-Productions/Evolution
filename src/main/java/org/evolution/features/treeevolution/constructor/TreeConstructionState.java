package org.evolution.features.treeevolution.constructor;

/**
 * Immutable live-versus-plan state consumed by the constructor hierarchy.
 */
public record TreeConstructionState(
        boolean ownershipComplete,
        boolean sourceSnapshotReady,
        boolean transitionPending,
        boolean damageRepairRequested,
        boolean transitionBlockerReady,
        boolean broadCleanupReady,
        boolean retiredCrownRemaining,
        int exposedUpperLogs,
        int uncoveredBranchTips,
        double trunkProgress,
        double branchProgress,
        double canopyProgress,
        double trunkTarget,
        double branchTarget,
        double canopyShellTarget,
        double canopyTarget,
        boolean detailRequested
) {
    public TreeConstructionState {
        exposedUpperLogs = Math.max(0, exposedUpperLogs);
        uncoveredBranchTips = Math.max(0, uncoveredBranchTips);
        trunkProgress = clamp(trunkProgress);
        branchProgress = clamp(branchProgress);
        canopyProgress = clamp(canopyProgress);
        trunkTarget = clamp(trunkTarget);
        branchTarget = clamp(branchTarget);
        canopyShellTarget = clamp(canopyShellTarget);
        canopyTarget = clamp(canopyTarget);
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
