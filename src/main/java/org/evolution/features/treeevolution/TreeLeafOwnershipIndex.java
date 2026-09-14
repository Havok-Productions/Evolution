package org.evolution.features.treeevolution;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * ## Constant-time ownership lookup for evolved and captured tree leaves.
 *
 * <p>The constructor must never walk every saved tree to decide whether one
 * coordinate is shared. Each tree refresh replaces only that tree's immutable
 * ownership snapshot, while coordinate queries inspect the usually tiny set of
 * trees that actually claim the block.</p>
 */
final class TreeLeafOwnershipIndex {
    private final Map<String, Map<String, Ownership>> ownersByBlock =
            new HashMap<>();
    private final Map<String, Snapshot> snapshotsByTree = new HashMap<>();

    synchronized void rebuild(Collection<TreeDna> trees) {
        ownersByBlock.clear();
        snapshotsByTree.clear();
        if (trees == null) {
            return;
        }
        for (TreeDna tree : trees) {
            refresh(tree);
        }
    }

    synchronized void refresh(TreeDna tree) {
        if (tree == null) {
            return;
        }
        Snapshot previous = snapshotsByTree.get(tree.key());
        Set<String> evolved = tree.evolvedShapeLeaves();
        Set<String> source = tree.originalShapeLeaves();
        Set<String> retired = tree.retiredOriginalShapeLeaves();
        if (previous != null
                && previous.evolved() == evolved
                && previous.source() == source
                && previous.retired() == retired) {
            return;
        }
        removeSnapshot(tree.key(), previous);
        Snapshot current = new Snapshot(evolved, source, retired);
        snapshotsByTree.put(tree.key(), current);
        for (String key : evolved) {
            add(key, tree.key(), Ownership.EVOLVED);
        }
        for (String key : source) {
            if (!retired.contains(key)) {
                add(key, tree.key(), Ownership.SOURCE);
            }
        }
    }

    synchronized void remove(String treeKey) {
        removeSnapshot(treeKey, snapshotsByTree.remove(treeKey));
    }

    synchronized TreeStaleEnvelopeOwnershipPolicy.Decision classify(
            TreeDna active, String blockKey) {
        if (active == null || blockKey == null
                || !active.evolvedShapeLeaves().contains(blockKey)) {
            return TreeStaleEnvelopeOwnershipPolicy.Decision.IGNORE_NOT_OWNED;
        }
        Map<String, Ownership> owners = ownersByBlock.get(blockKey);
        if (owners == null || owners.isEmpty()) {
            // ## A just-mutated active tree may be queried before its end-of-action
            // refresh. Its own DNA remains authoritative for exclusive ownership.
            return TreeStaleEnvelopeOwnershipPolicy.Decision
                    .RETIRE_EXCLUSIVE_EVOLVED_LEAF;
        }
        for (String owner : owners.keySet()) {
            if (!owner.equals(active.key())) {
                return TreeStaleEnvelopeOwnershipPolicy.Decision
                        .IGNORE_SHARED_WITH_FOREIGN_TREE;
            }
        }
        return TreeStaleEnvelopeOwnershipPolicy.Decision
                .RETIRE_EXCLUSIVE_EVOLVED_LEAF;
    }

    synchronized int indexedBlockCount() {
        return ownersByBlock.size();
    }

    synchronized int indexedTreeCount() {
        return snapshotsByTree.size();
    }

    private void add(String blockKey, String treeKey, Ownership ownership) {
        ownersByBlock.computeIfAbsent(blockKey, ignored -> new HashMap<>())
                .merge(treeKey, ownership, Ownership::merge);
    }

    private void removeSnapshot(String treeKey, Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        for (String key : snapshot.evolved()) {
            removeOwner(key, treeKey);
        }
        for (String key : snapshot.source()) {
            removeOwner(key, treeKey);
        }
    }

    private void removeOwner(String blockKey, String treeKey) {
        Map<String, Ownership> owners = ownersByBlock.get(blockKey);
        if (owners == null) {
            return;
        }
        owners.remove(treeKey);
        if (owners.isEmpty()) {
            ownersByBlock.remove(blockKey);
        }
    }

    private enum Ownership {
        SOURCE,
        EVOLVED,
        SOURCE_AND_EVOLVED;

        private static Ownership merge(Ownership first, Ownership second) {
            return first == second ? first : SOURCE_AND_EVOLVED;
        }
    }

    private record Snapshot(
            Set<String> evolved,
            Set<String> source,
            Set<String> retired) {
    }
}
