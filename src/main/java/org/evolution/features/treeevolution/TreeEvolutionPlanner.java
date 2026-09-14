package org.evolution.features.treeevolution;

import org.bukkit.block.Biome;

final class TreeEvolutionPlanner {
    private final TreeBlueprintCoordinator blueprintCoordinator =
            new TreeBlueprintCoordinator();

    TreePlan plan(TreeDna dna, Biome biome, boolean rootsEnabled) {
        return blueprintCoordinator.assemble(dna, biome, rootsEnabled);
    }
}
