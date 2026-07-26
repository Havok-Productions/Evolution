package org.evolution.features.treeevolution;

import java.util.List;

/**
 * ## Guards the fancy-tree main crown against a perfectly planar underside.
 */
public final class TreeCanopySilhouetteSmokeTest {
    private TreeCanopySilhouetteSmokeTest() {
    }

    public static void main(String[] args) {
        for (TreeMaturityStage stage : List.of(
                TreeMaturityStage.SMALL, TreeMaturityStage.MEDIUM)) {
            TreeDna dna = TreeShapeSmokeTest.sampleDna(
                    TreeSpecies.OAK, stage);
            TreePlan plan = TreeShapeSmokeTest.treeBodyPlan(dna);
            int topY = dna.baseY()
                    + TreeSpeciesStageStyle.visibleHeight(dna) - 1;
            int fringeY = topY - 3;
            long attachedFringe = plan.orderedBlocks().stream()
                    .filter(block -> block.role() == TreeBlockRole.CANOPY)
                    .filter(block -> block.y() == fringeY)
                    .filter(block -> {
                        PlannedTreeBlock support = plan.blocksByKey().get(
                                block.x() + ":" + (block.y() + 1)
                                        + ":" + block.z());
                        return support != null
                                && support.role() == TreeBlockRole.CANOPY;
                    })
                    .count();
            int required = stage == TreeMaturityStage.SMALL ? 3 : 4;
            require(attachedFringe >= required,
                    stage + " oak main crown has only "
                            + attachedFringe + "/" + required
                            + " attached lower tufts");
        }

        System.out.println("Tree canopy silhouette smoke test passed: "
                + "small/medium oak main crowns have attached uneven fringes");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}