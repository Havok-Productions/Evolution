package org.evolution.features.treeevolution;

public final class TreeSeedlingFootprintSmokeTest {
    private TreeSeedlingFootprintSmokeTest() {
    }

    public static void main(String[] args) {
        int required = TreeSeedlingSearchPolicy.requiredBaseDistance(4, 4);
        require(required == 10,
                "Two medium fluffy crowns need a two-block breathing margin.");
        require(TreeSeedlingSearchPolicy.footprintsOverlap(
                        8, 0, 4, 4),
                "Old four-block spacing still overlaps projected crowns.");
        require(!TreeSeedlingSearchPolicy.footprintsOverlap(
                        10, 0, 4, 4),
                "A valid crown boundary should remain plantable.");

        System.out.println(
                "Tree seedling footprint smoke test passed: "
                        + "overlapping future crowns are rejected.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
