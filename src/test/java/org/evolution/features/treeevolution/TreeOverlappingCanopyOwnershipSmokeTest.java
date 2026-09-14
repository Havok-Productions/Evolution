package org.evolution.features.treeevolution;

import java.util.List;

/** Replays the ownership decision behind the reported overlapping spruces. */
public final class TreeOverlappingCanopyOwnershipSmokeTest {
    private TreeOverlappingCanopyOwnershipSmokeTest() {
    }

    public static void main(String[] args) {
        require(TreeObsoleteReceiptPolicy.classify(false, true, true)
                        == TreeObsoleteReceiptPolicy.Disposition
                                .RELEASE_TO_NEIGHBOR_TARGET,
                "neighbor-owned target should release only the stale receipt");
        TreeDna first = TreeShapeSmokeTest.sampleDna(
                TreeSpecies.SPRUCE, TreeMaturityStage.MEDIUM, 0);
        TreeDna second = TreeShapeSmokeTest.sampleDna(
                TreeSpecies.SPRUCE, TreeMaturityStage.MEDIUM, 1);
        String overlap = first.worldId() + ":369:85:-570";

        require(second.markEvolvedLeaf(overlap),
                "neighbor leaf receipt could not be seeded");
        require(TreeStaleEnvelopeOwnershipPolicy.classify(
                        first, List.of(first, second), overlap)
                        == TreeStaleEnvelopeOwnershipPolicy.Decision
                                .IGNORE_NOT_OWNED,
                "one spruce could retire its neighbor's canopy leaf");

        require(first.markEvolvedLeaf(overlap),
                "shared overlap receipt could not be seeded");
        require(TreeStaleEnvelopeOwnershipPolicy.classify(
                        first, List.of(first, second), overlap)
                        == TreeStaleEnvelopeOwnershipPolicy.Decision
                                .IGNORE_SHARED_WITH_FOREIGN_TREE,
                "shared canopy coordinate was destructively retired");

        require(second.forgetEvolvedLeaf(overlap),
                "neighbor overlap receipt could not be released");
        require(TreeStaleEnvelopeOwnershipPolicy.classify(
                        first, List.of(first, second), overlap)
                        == TreeStaleEnvelopeOwnershipPolicy.Decision
                                .RETIRE_EXCLUSIVE_EVOLVED_LEAF,
                "exclusive stale envelope leaf could not retire");

        System.out.println(
                "Overlapping canopy ownership smoke passed: "
                        + "foreign=ignored shared=ignored exclusive=retired");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
