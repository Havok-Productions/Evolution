package org.evolution.features.treeevolution;

import java.util.Collection;

/**
 * ## Defines the ownership boundary for destructive stale-leaf cleanup.
 *
 * <p>Touching same-species crowns may share a discovery volume, but they do
 * not share destructive rights. A tree may retire only its exclusive evolved
 * leaf receipt. Source foliage and leaves owned by a neighboring tree remain
 * untouched.</p>
 */
final class TreeStaleEnvelopeOwnershipPolicy {
    private TreeStaleEnvelopeOwnershipPolicy() {
    }

    static Decision classify(
            TreeDna active,
            Collection<TreeDna> knownTrees,
            String blockKey
    ) {
        if (active == null || blockKey == null
                || !active.evolvedShapeLeaves().contains(blockKey)) {
            return Decision.IGNORE_NOT_OWNED;
        }
        for (TreeDna other : knownTrees) {
            if (other == null || other == active) {
                continue;
            }
            boolean foreignEvolved =
                    other.evolvedShapeLeaves().contains(blockKey);
            boolean foreignSource = other.originalShapeLeaves()
                    .contains(blockKey)
                    && !other.retiredOriginalShapeLeaves()
                            .contains(blockKey);
            if (foreignEvolved || foreignSource) {
                return Decision.IGNORE_SHARED_WITH_FOREIGN_TREE;
            }
        }
        return Decision.RETIRE_EXCLUSIVE_EVOLVED_LEAF;
    }

    enum Decision {
        IGNORE_NOT_OWNED,
        IGNORE_SHARED_WITH_FOREIGN_TREE,
        RETIRE_EXCLUSIVE_EVOLVED_LEAF
    }
}
