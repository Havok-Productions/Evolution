package org.slowtrees.treeevolution;

import java.util.List;
import org.bukkit.block.Biome;

interface TreeStagePlanner {
    TreePlan plan(TreeDna dna, Biome biome, boolean rootsEnabled, TreePlanParts parts);
}

final class TreePlanParts {
    final TrunkPlanner trunk = new TrunkPlanner();
    final BranchPlanner branch = new BranchPlanner();
    final CanopyPlanner canopy = new CanopyPlanner();
    final RootPlanner root = new RootPlanner();
    final VinePlanner vine = new VinePlanner();
    final GroundDetailPlanner groundDetail = new GroundDetailPlanner();
}

final class SmallTreePlanner implements TreeStagePlanner {
    @Override
    public TreePlan plan(TreeDna dna, Biome biome, boolean rootsEnabled, TreePlanParts parts) {
        TreePlan plan = new TreePlan();
        parts.trunk.plan(plan, dna);
        List<TreeBranchPlan> branchPlans = parts.branch.plan(plan, dna);
        parts.canopy.plan(plan, dna, branchPlans);
        return plan;
    }
}

final class MediumTreePlanner implements TreeStagePlanner {
    @Override
    public TreePlan plan(TreeDna dna, Biome biome, boolean rootsEnabled, TreePlanParts parts) {
        TreePlan plan = new TreePlan();
        parts.trunk.plan(plan, dna);
        List<TreeBranchPlan> branchPlans = parts.branch.plan(plan, dna);
        parts.canopy.plan(plan, dna, branchPlans);
        if (dna.species() == TreeSpecies.JUNGLE || dna.species() == TreeSpecies.MANGROVE) {
            parts.vine.plan(plan, dna, branchPlans);
        }
        return plan;
    }
}

final class MatureTreePlanner implements TreeStagePlanner {
    @Override
    public TreePlan plan(TreeDna dna, Biome biome, boolean rootsEnabled, TreePlanParts parts) {
        TreePlan plan = new TreePlan();
        parts.trunk.plan(plan, dna);
        List<TreeBranchPlan> branchPlans = parts.branch.plan(plan, dna);
        parts.canopy.plan(plan, dna, branchPlans);
        parts.vine.plan(plan, dna, branchPlans);
        if (biome != null) {
            parts.groundDetail.plan(plan, dna, biome);
        }
        return plan;
    }
}

final class AncientTreePlanner implements TreeStagePlanner {
    @Override
    public TreePlan plan(TreeDna dna, Biome biome, boolean rootsEnabled, TreePlanParts parts) {
        TreePlan plan = new TreePlan();
        parts.trunk.plan(plan, dna);
        List<TreeBranchPlan> branchPlans = parts.branch.plan(plan, dna);
        parts.canopy.plan(plan, dna, branchPlans);
        if (rootsEnabled) {
            parts.root.plan(plan, dna);
        }
        parts.vine.plan(plan, dna, branchPlans);
        if (biome != null) {
            parts.groundDetail.plan(plan, dna, biome);
        }
        return plan;
    }
}
