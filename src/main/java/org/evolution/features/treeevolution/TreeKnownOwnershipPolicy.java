package org.evolution.features.treeevolution;

import java.util.HashSet;
import java.util.Set;

/**
 * Reconstructs one known tree's ownership from its persisted transition ledger.
 *
 * <p>Fresh trees still require discovery traversal. Once an immutable source
 * snapshot exists, repeating a flood-fill through touching forest crowns is both
 * less accurate and much more expensive than using the exact saved receipts.</p>
 */
final class TreeKnownOwnershipPolicy {
    private TreeKnownOwnershipPolicy() {
    }

    static Snapshot snapshot(TreeDna dna) {
        if (dna == null
                || !dna.hasOriginalShapeSnapshot()
                || !dna.originalShapeCaptureIsCurrent()) {
            return Snapshot.unavailable();
        }

        Set<String> allWood = new HashSet<>();
        allWood.addAll(dna.originalShapeLogs());
        allWood.addAll(dna.evolvedShapeLogs());
        Set<String> rootedWood =
                TreeWoodOwnershipGraph.connectedToRoot(dna, allWood);

        Set<String> keys = new HashSet<>(rootedWood);
        keys.addAll(dna.originalShapeLeaves());
        keys.removeAll(dna.retiredOriginalShapeLeaves());
        keys.addAll(dna.evolvedShapeLeaves());

        String baseKey = dna.worldId() + ":" + dna.baseX() + ":"
                + dna.baseY() + ":" + dna.baseZ();
        boolean rooted = rootedWood.contains(baseKey);
        return rooted && !keys.isEmpty()
                ? new Snapshot(true, Set.copyOf(keys))
                : Snapshot.unavailable();
    }

    record Snapshot(boolean complete, Set<String> ownedKeys) {
        private static Snapshot unavailable() {
            return new Snapshot(false, Set.of());
        }
    }
}
