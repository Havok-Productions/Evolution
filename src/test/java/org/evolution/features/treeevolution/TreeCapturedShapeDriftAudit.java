package org.evolution.features.treeevolution;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * ## Detects plugin-owned wood left behind by an older target revision.
 */
final class TreeCapturedShapeDriftAudit {
    private TreeCapturedShapeDriftAudit() {
    }

    static Optional<String> inspect(TreeDna dna, TreePlan plan) {
        if (dna.hasOriginalShapeSnapshot()
                || dna.evolvedShapeLogs().isEmpty()) {
            return Optional.empty();
        }
        Set<String> targetWood = new HashSet<>();
        plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.TRUNK
                        || block.role() == TreeBlockRole.BRANCH)
                .map(PlannedTreeBlock::key)
                .forEach(targetWood::add);
        List<String> obsolete = dna.evolvedShapeLogs().stream()
                .map(TreeCapturedShapeDriftAudit::coordinateKey)
                .filter(key -> !targetWood.contains(key))
                .distinct()
                .sorted()
                .toList();
        return !obsolete.isEmpty()
                ? Optional.of("obsolete-evolved-wood=" + obsolete.size()
                        + " coordinates=" + obsolete.stream()
                                .limit(8).toList())
                : Optional.empty();
    }

    private static String coordinateKey(String worldKey) {
        String[] parts = worldKey.split(":");
        int offset = parts.length - 3;
        return parts[offset] + ":" + parts[offset + 1]
                + ":" + parts[offset + 2];
    }
}
