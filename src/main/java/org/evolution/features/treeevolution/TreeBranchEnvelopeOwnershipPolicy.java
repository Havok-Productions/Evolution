package org.evolution.features.treeevolution;

/**
 * Decides whether a live planned leaf may satisfy a branch envelope.
 */
final class TreeBranchEnvelopeOwnershipPolicy {
    private TreeBranchEnvelopeOwnershipPolicy() {
    }

    static boolean countsAsEvolvedLeaf(
            boolean ownershipAuditRequired,
            boolean explicitlyEvolved,
            boolean legacyTransition,
            boolean originalLeaf
    ) {
        if (!ownershipAuditRequired) {
            return true;
        }
        if (explicitlyEvolved) {
            return true;
        }
        // ## Legacy migration may infer only post-snapshot leaves. A leaf present
        // in the captured source crown must be explicitly reformed by this tree.
        return legacyTransition && !originalLeaf;
    }

    static boolean shouldReformOriginalLeaf(
            boolean plannedCanopyLeaf,
            boolean originalLeaf,
            boolean explicitlyEvolved
    ) {
        return plannedCanopyLeaf && originalLeaf && !explicitlyEvolved;
    }

    static boolean plannedCanopySatisfied(
            boolean materialMatches,
            boolean compatibleOrganicOccupant,
            boolean originalLeaf,
            boolean explicitlyEvolved
    ) {
        // ## Coordinate and material alone cannot complete a transition. A leaf
        // captured in the source crown must first join the evolved ownership epoch.
        if (materialMatches && shouldReformOriginalLeaf(
                true, originalLeaf, explicitlyEvolved)) {
            return false;
        }
        return materialMatches || compatibleOrganicOccupant;
    }
}