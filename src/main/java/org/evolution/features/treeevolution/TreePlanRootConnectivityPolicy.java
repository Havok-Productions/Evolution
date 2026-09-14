package org.evolution.features.treeevolution;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ## Verifies that every planned wood voxel belongs to the stump component.
 */
final class TreePlanRootConnectivityPolicy {
    private TreePlanRootConnectivityPolicy() {
    }

    static Report inspect(
            TreeDna dna, List<PlannedTreeBlock> blocks) {
        Set<String> plannedWood = new HashSet<>();
        for (PlannedTreeBlock block : blocks) {
            if (block.role() == TreeBlockRole.TRUNK
                    || block.role() == TreeBlockRole.BRANCH
                    || block.role() == TreeBlockRole.ROOT) {
                plannedWood.add(dna.worldId() + ":" + block.key());
            }
        }
        Set<String> rooted = TreeWoodOwnershipGraph.connectedToRoot(
                dna, plannedWood);
        List<String> disconnected = plannedWood.stream()
                .filter(key -> !rooted.contains(key))
                .sorted(TreeObsoleteRetirementPolicy.outermostFirst(dna))
                .toList();
        return new Report(
                plannedWood.size(), rooted.size(), disconnected);
    }

    record Report(
            int plannedWood,
            int rootedWood,
            List<String> disconnectedKeys
    ) {
        boolean connected() {
            return plannedWood > 0
                    && plannedWood == rootedWood
                    && disconnectedKeys.isEmpty();
        }

        String marker() {
            return "[PLAN-ROOT-CONNECTIVITY]["
                    + (connected() ? "PASS" : "FAIL") + "] rooted="
                    + rootedWood + "/" + plannedWood
                    + " first-disconnected="
                    + (disconnectedKeys.isEmpty()
                            ? "none" : disconnectedKeys.getFirst());
        }
    }
}
