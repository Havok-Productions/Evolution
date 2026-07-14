package org.slowtrees.treeevolution;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.block.Biome;

final class TreeEvolutionPlanner {
    private final TreePlanParts parts = new TreePlanParts();
    private final Map<TreeMaturityStage, TreeStagePlanner> stagePlanners = new EnumMap<>(TreeMaturityStage.class);

    TreeEvolutionPlanner() {
        stagePlanners.put(TreeMaturityStage.SMALL, new SmallTreePlanner());
        stagePlanners.put(TreeMaturityStage.MEDIUM, new MediumTreePlanner());
        stagePlanners.put(TreeMaturityStage.MATURE, new MatureTreePlanner());
        stagePlanners.put(TreeMaturityStage.ANCIENT, new AncientTreePlanner());
    }

    TreePlan plan(TreeDna dna, Biome biome, boolean rootsEnabled) {
        return stagePlanners.getOrDefault(dna.maturityStage(), stagePlanners.get(TreeMaturityStage.SMALL))
                .plan(dna, biome, rootsEnabled, parts);
    }
}
