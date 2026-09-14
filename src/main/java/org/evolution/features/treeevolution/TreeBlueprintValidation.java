package org.evolution.features.treeevolution;

import java.util.List;
import java.util.Map;

/**
 * Immutable approval receipt produced before a tree plan reaches live code.
 */
record TreeBlueprintValidation(
        boolean approved,
        String origin,
        int blocks,
        int coordinateProposals,
        int coordinateConflicts,
        int rootedWood,
        int plannedWood,
        int branchTips,
        int coveredBranchTips,
        List<String> failures,
        List<Map<String, Object>> conflictSamples
) {
    TreeBlueprintValidation {
        failures = List.copyOf(failures);
        conflictSamples = List.copyOf(conflictSamples);
    }

    String marker() {
        return "[BLUEPRINT][" + (approved ? "APPROVED" : "REJECTED")
                + "] origin=" + origin
                + " blocks=" + blocks
                + " proposals=" + coordinateProposals
                + " conflicts=" + coordinateConflicts
                + " rooted=" + rootedWood + "/" + plannedWood
                + " tip-cover=" + coveredBranchTips + "/" + branchTips
                + " failures=" + failures;
    }
}
