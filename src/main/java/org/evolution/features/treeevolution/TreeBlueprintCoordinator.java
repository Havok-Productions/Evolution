package org.evolution.features.treeevolution;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.block.Biome;

/**
 * ## Centerpiece authority for all tree target coordinates.
 *
 * <p>Stage and species planners contribute focused geometry, but none of them
 * may publish a live target independently. This coordinator creates one draft,
 * routes every part through the same coordinate translator, validates the
 * combined trunk/branch/canopy contract, and seals the result consumed by the
 * universal runtime constructor.</p>
 */
final class TreeBlueprintCoordinator {
    private final TreePlanParts parts = new TreePlanParts();
    private final TreeBlueprintValidator validator =
            new TreeBlueprintValidator();
    private final Map<TreeMaturityStage, TreeStagePlanner> stagePlanners =
            new EnumMap<>(TreeMaturityStage.class);

    TreeBlueprintCoordinator() {
        stagePlanners.put(TreeMaturityStage.SMALL, new SmallTreePlanner());
        stagePlanners.put(TreeMaturityStage.MEDIUM, new MediumTreePlanner());
        stagePlanners.put(TreeMaturityStage.MATURE, new MatureTreePlanner());
        stagePlanners.put(TreeMaturityStage.ANCIENT, new AncientTreePlanner());
    }

    TreePlan assemble(
            TreeDna dna,
            Biome biome,
            boolean rootsEnabled
    ) {
        TreePlan plan = new TreePlan(dna);
        TreeStagePlanner stagePlanner = stagePlanners.getOrDefault(
                dna.maturityStage(),
                stagePlanners.get(TreeMaturityStage.SMALL));
        stagePlanner.contribute(plan, dna, biome, rootsEnabled, parts);

        TreeBlueprintValidation validation = validator.inspect(dna, plan);
        if (!validation.approved()) {
            throw new IllegalStateException(
                    "Tree blueprint rejected for " + dna.key() + ": "
                            + validation.marker());
        }
        plan.seal(validation);
        return plan;
    }
}
