package org.evolution.features.treeevolution;

import java.util.Optional;

/**
 * ## Detects a nearly complete replacement trapped beneath its source crown.
 */
final class TreeTransitionStallAudit {
    private static final double NEAR_COMPLETE_CANOPY = 0.85D;

    private TreeTransitionStallAudit() {
    }

    static Optional<String> inspect(
            TreeDna dna,
            TreeReplayProgress progress
    ) {
        if (!dna.hasOriginalShapeSnapshot()
                || dna.unresolvedOriginalShapeLeafCount() <= 0
                || progress.trunk() < 0.999D
                || progress.branch() < 0.999D
                || progress.canopy() < NEAR_COMPLETE_CANOPY) {
            return Optional.empty();
        }
        return Optional.of(
                "stalled-source-crown-overlap unresolved="
                        + dna.unresolvedOriginalShapeLeafCount()
                        + " canopy="
                        + Math.round(progress.canopy() * 100.0D)
                        + "%");
    }
}
