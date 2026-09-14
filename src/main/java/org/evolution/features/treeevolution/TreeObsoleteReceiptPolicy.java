package org.evolution.features.treeevolution;

/**
 * ## Exclusive ownership rule for TREE_60 retirement.
 *
 * <p>Current-target connectivity is repaired by TREE_11. TREE_60 retires a
 * receipt outside the immutable target. If a neighboring plan uses that live
 * coordinate, only this tree's stale ledger claim is released.</p>
 */
final class TreeObsoleteReceiptPolicy {
    enum Disposition {
        KEEP_CURRENT_TARGET,
        RELEASE_TO_NEIGHBOR_TARGET,
        RETIRE_DISCONNECTED_OUTSIDE_TARGET,
        RETIRE_OUTSIDE_TARGET
    }

    private TreeObsoleteReceiptPolicy() {
    }

    static Disposition classify(
            boolean matchesCurrentTarget,
            boolean protectedNeighborTarget,
            boolean rediscoveredByCandidate
    ) {
        if (matchesCurrentTarget) {
            return Disposition.KEEP_CURRENT_TARGET;
        }
        if (protectedNeighborTarget) {
            return Disposition.RELEASE_TO_NEIGHBOR_TARGET;
        }
        return rediscoveredByCandidate
                ? Disposition.RETIRE_OUTSIDE_TARGET
                : Disposition.RETIRE_DISCONNECTED_OUTSIDE_TARGET;
    }
}
