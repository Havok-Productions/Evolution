package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.List;

/**
 * ## Universal pre-construction shape contract.
 *
 * <p>This validator checks the assembled target rather than trusting each
 * species planner independently. It deliberately owns only universal rules;
 * species appearance remains inside the species and variant planners.</p>
 */
final class TreeBlueprintValidator {
    private final TreeShapeEngine shapeEngine = new TreeShapeEngine();

    TreeBlueprintValidation inspect(TreeDna dna, TreePlan plan) {
        List<String> failures = new ArrayList<>();
        List<PlannedTreeBlock> blocks = plan.orderedBlocks();
        if (blocks.isEmpty()) {
            failures.add("empty-target");
        }
        for (PlannedTreeBlock block : blocks) {
            if (!plan.coordinateTranslator().roundTrips(block)) {
                failures.add("coordinate-round-trip=" + block.key());
                break;
            }
            if (!block.augment().accepts(block.role())) {
                failures.add("augment-role=" + block.key() + ":"
                        + block.augment() + "/" + block.role());
                break;
            }
        }

        TreePlanRootConnectivityPolicy.Report rootConnectivity =
                TreePlanRootConnectivityPolicy.inspect(dna, blocks);
        if (!rootConnectivity.connected()) {
            failures.add(rootConnectivity.marker());
        }

        TreeShapeEngine.ShapeReport shape = shapeEngine.analyze(plan, dna);
        if (shape.floatingWood() > 0) {
            failures.add("floating-wood=" + shape.floatingWood());
        }
        if (shape.unanchoredBranchSegments() > 0) {
            failures.add("unanchored-branch-segments="
                    + shape.unanchoredBranchSegments());
        }
        if (!shape.topCovered()) {
            failures.add("uncovered-trunk-top");
        }
        if (shape.coveredBranchTips() < shape.branchTips()) {
            failures.add("uncovered-branch-tips="
                    + (shape.branchTips() - shape.coveredBranchTips()));
        }

        return new TreeBlueprintValidation(
                failures.isEmpty(),
                plan.coordinateTranslator().originLabel(),
                blocks.size(),
                plan.coordinateProposalCount(),
                plan.coordinateConflictCount(),
                rootConnectivity.rootedWood(),
                rootConnectivity.plannedWood(),
                shape.branchTips(),
                shape.coveredBranchTips(),
                failures,
                plan.coordinateConflictSamples());
    }
}
