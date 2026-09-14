package org.evolution.features.treeevolution;

import java.util.List;
import org.bukkit.block.Biome;

interface TreeStagePlanner {
    void contribute(
            TreePlan plan,
            TreeDna dna,
            Biome biome,
            boolean rootsEnabled,
            TreePlanParts parts);
}

final class TreePlanParts {
    final TrunkPlanner trunk = new TrunkPlanner();
    final BranchPlanner branch = new BranchPlanner();
    final CanopyPlanner canopy = new CanopyPlanner();
    final RootPlanner root = new RootPlanner();
    final VinePlanner vine = new VinePlanner();
    final GroundDetailPlanner groundDetail = new GroundDetailPlanner();

    void planTrunk(TreePlan plan, TreeDna dna) {
        plan.withAugment(TreePlacementAugment.TRUNK_FRAME,
                () -> trunk.plan(plan, dna));
    }

    List<TreeBranchPlan> planBranches(TreePlan plan, TreeDna dna) {
        return plan.withAugment(
                TreePlacementAugment.BRANCH_PROCEDURAL_PATH,
                () -> branch.plan(plan, dna));
    }

    void planCanopy(
            TreePlan plan,
            TreeDna dna,
            List<TreeBranchPlan> branches
    ) {
        plan.withAugment(TreePlacementAugment.CANOPY_SPECIES_CROWN,
                () -> canopy.plan(plan, dna, branches));
    }

    void planRoots(TreePlan plan, TreeDna dna) {
        plan.withAugment(TreePlacementAugment.ROOT_ARCHITECTURE,
                () -> root.plan(plan, dna));
    }

    void planVines(
            TreePlan plan,
            TreeDna dna,
            List<TreeBranchPlan> branches
    ) {
        plan.withAugment(TreePlacementAugment.VINE_DETAIL,
                () -> vine.plan(plan, dna, branches));
    }

    void planGround(TreePlan plan, TreeDna dna, Biome biome) {
        plan.withAugment(TreePlacementAugment.GROUND_ECOLOGY,
                () -> groundDetail.plan(plan, dna, biome));
    }
}

final class SmallTreePlanner implements TreeStagePlanner {
    @Override
    public void contribute(TreePlan plan, TreeDna dna, Biome biome,
            boolean rootsEnabled, TreePlanParts parts) {
        parts.planTrunk(plan, dna);
        List<TreeBranchPlan> branchPlans = parts.planBranches(plan, dna);
        parts.planCanopy(plan, dna, branchPlans);
    }
}

final class MediumTreePlanner implements TreeStagePlanner {
    @Override
    public void contribute(TreePlan plan, TreeDna dna, Biome biome,
            boolean rootsEnabled, TreePlanParts parts) {
        parts.planTrunk(plan, dna);
        List<TreeBranchPlan> branchPlans = parts.planBranches(plan, dna);
        parts.planCanopy(plan, dna, branchPlans);
        if (dna.species() == TreeSpecies.JUNGLE || dna.species() == TreeSpecies.MANGROVE) {
            parts.planVines(plan, dna, branchPlans);
        }
    }
}

final class MatureTreePlanner implements TreeStagePlanner {
    @Override
    public void contribute(TreePlan plan, TreeDna dna, Biome biome,
            boolean rootsEnabled, TreePlanParts parts) {
        parts.planTrunk(plan, dna);
        List<TreeBranchPlan> branchPlans = parts.planBranches(plan, dna);
        parts.planCanopy(plan, dna, branchPlans);
        parts.planVines(plan, dna, branchPlans);
        if (biome != null) {
            parts.planGround(plan, dna, biome);
        }
    }
}

final class AncientTreePlanner implements TreeStagePlanner {
    @Override
    public void contribute(TreePlan plan, TreeDna dna, Biome biome,
            boolean rootsEnabled, TreePlanParts parts) {
        parts.planTrunk(plan, dna);
        List<TreeBranchPlan> branchPlans = parts.planBranches(plan, dna);
        parts.planCanopy(plan, dna, branchPlans);
        if (rootsEnabled) {
            parts.planRoots(plan, dna);
        }
        parts.planVines(plan, dna, branchPlans);
        if (biome != null) {
            parts.planGround(plan, dna, biome);
        }
    }
}
