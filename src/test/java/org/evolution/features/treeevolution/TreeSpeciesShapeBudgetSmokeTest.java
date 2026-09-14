package org.evolution.features.treeevolution;

import java.util.List;

/**
 * Guards the shared branch budget and the high, thin acacia umbrella silhouette.
 */
public final class TreeSpeciesShapeBudgetSmokeTest {
    private TreeSpeciesShapeBudgetSmokeTest() {
    }

    public static void main(String[] args) {
        for (TreeSpecies species : TreeSpecies.values()) {
            for (TreeMaturityStage stage : TreeMaturityStage.values()) {
                TreeDna dna = TreeShapeSmokeTest.sampleDna(species, stage);
                TreePlan plan = TreeShapeSmokeTest.treeBodyPlan(dna);
                int budget = TreeSpeciesStageStyle.branchCount(dna);
                require(plan.branchPlans().size() <= budget,
                        species.id() + " " + stage
                                + " exceeded shared branch budget: "
                                + plan.branchPlans().size() + "/" + budget);
                for (TreeBranchPlan branch : plan.branchPlans()) {
                    for (TreeBranchPlan.BranchSegment segment
                            : branch.segments()) {
                        if (!TreeBranchCanopyIntegrationPolicy
                                .requiresCover(branch, segment)) {
                            continue;
                        }
                        int contacts = TreeBranchTipIntegrityPolicy
                                .plannedLeafContacts(
                                        segment.x(), segment.y(),
                                        segment.z(),
                                        dna.species().leafMaterial(),
                                        plan.blocksByKey());
                        int required = TreeBranchCanopyIntegrationPolicy
                                .desiredDirectContacts(
                                        dna, segment.x(), segment.y(),
                                        segment.z(), plan.blocksByKey());
                        require(contacts >= required,
                                species.id() + " " + stage
                                        + " left distal branch="
                                        + branch.id() + " step="
                                        + segment.step() + " at="
                                        + segment.x() + ":"
                                        + segment.y() + ":"
                                        + segment.z() + " contacts="
                                        + contacts + "/" + required);
                    }
                }
            }
        }

        for (TreeMaturityStage stage : TreeMaturityStage.values()) {
            TreeDna dna = TreeShapeSmokeTest.sampleDna(TreeSpecies.ACACIA, stage);
            TreePlan plan = TreeShapeSmokeTest.treeBodyPlan(dna);
            int visibleHeight = TreeSpeciesStageStyle.visibleHeight(dna);
            int minimumAnchorY = dna.baseY() + (int) Math.floor(
                    visibleHeight * minimumAcaciaForkRatio(stage));
            require(!plan.branchPlans().isEmpty(),
                    "acacia " + stage + " must retain at least one supported fork");
            require(plan.branchPlans().size()
                            <= AcaciaArchitecturePolicy.forkCount(dna),
                    "acacia " + stage
                            + " exceeded its controlled fork count");
            require(plan.branchPlans().stream().allMatch(
                            branch -> branch.anchorY() >= minimumAnchorY),
                    "acacia " + stage + " must fork in the upper trunk");
            require(plan.branchPlans().stream().allMatch(
                            branch -> branch.segments().stream()
                                    .mapToInt(TreeBranchPlan.BranchSegment::step)
                                    .max().orElse(0)
                                    <= AcaciaArchitecturePolicy
                                            .maximumBranchLength(dna)),
                    "acacia " + stage
                            + " grew a limb beyond its crown support scale");

            List<PlannedTreeBlock> canopy = plan.orderedBlocks().stream()
                    .filter(block -> block.role() == TreeBlockRole.CANOPY)
                    .toList();
            require(canopy.size() <= canopyBudget(stage),
                    "acacia " + stage + " regressed into an oversized generic cloud: "
                            + canopy.size());
            int horizontalSpan = Math.max(
                    spanX(canopy), spanZ(canopy));
            int verticalSpan = canopy.stream().mapToInt(PlannedTreeBlock::y).max().orElseThrow()
                    - canopy.stream().mapToInt(PlannedTreeBlock::y).min().orElseThrow() + 1;
            require(horizontalSpan > verticalSpan,
                    "acacia " + stage + " must read as a broad, thin umbrella");
            require(canopy.size() >= minimumAcaciaCanopy(stage),
                    "acacia " + stage
                            + " crown is too sparse to conceal its fork frame: "
                            + canopy.size());
            require(plan.branchPlans().stream().allMatch(branch -> {
                TreeBranchPlan.BranchTip tip = branch.tip();
                return TreeBranchTipIntegrityPolicy.hasPreplannedEnvelope(
                        dna, tip.x(), tip.y(), tip.z(), plan.blocksByKey());
            }), "every acacia fork must terminate inside its own planned leaf pad");
            require(plan.branchPlans().stream().allMatch(branch -> {
                TreeBranchPlan.BranchTip tip = branch.tip();
                return TreeBranchTipIntegrityPolicy.plannedLeafContacts(
                                tip.x(), tip.y(), tip.z(),
                                dna.species().leafMaterial(),
                                plan.blocksByKey())
                        >= AcaciaArchitecturePolicy.minimumTipLeafContacts();
            }), "every acacia fork must have a two-leaf terminal throat");
            require(plan.branchPlans().stream().allMatch(branch -> {
                TreeBranchPlan.BranchTip tip = branch.tip();
                TreeBranchTipIntegrityPolicy.EnvelopeShape envelope =
                        TreeBranchTipIntegrityPolicy.plannedEnvelopeShape(
                                tip.x(), tip.y(), tip.z(),
                                dna.species().leafMaterial(),
                                plan.blocksByKey());
                return envelope.maxX() - envelope.minX() >= 2
                        && envelope.maxZ() - envelope.minZ() >= 2
                        && envelope.maxY() > envelope.minY();
            }), "every acacia fork must end in a rounded 3D leaf lobe");
        }

        System.out.println(
                "Species-shape budget smoke test passed: shared-budget=true "
                        + "universal-branch-integration=true "
                        + "acacia-forks=true acacia-rounded-lobes=true");
    }

    private static double minimumAcaciaForkRatio(TreeMaturityStage stage) {
        return switch (stage) {
            case SMALL -> 0.64D;
            case MEDIUM -> 0.58D;
            case MATURE -> 0.50D;
            case ANCIENT -> 0.42D;
        };
    }

    private static int canopyBudget(TreeMaturityStage stage) {
        return switch (stage) {
            case SMALL -> 190;
            case MEDIUM -> 390;
            case MATURE -> 560;
            case ANCIENT -> 760;
        };
    }

    private static int minimumAcaciaCanopy(TreeMaturityStage stage) {
        return switch (stage) {
            case SMALL -> 45;
            case MEDIUM -> 95;
            case MATURE -> 145;
            case ANCIENT -> 210;
        };
    }

    private static int spanX(List<PlannedTreeBlock> blocks) {
        return blocks.stream().mapToInt(PlannedTreeBlock::x).max().orElseThrow()
                - blocks.stream().mapToInt(PlannedTreeBlock::x).min().orElseThrow() + 1;
    }

    private static int spanZ(List<PlannedTreeBlock> blocks) {
        return blocks.stream().mapToInt(PlannedTreeBlock::z).max().orElseThrow()
                - blocks.stream().mapToInt(PlannedTreeBlock::z).min().orElseThrow() + 1;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
