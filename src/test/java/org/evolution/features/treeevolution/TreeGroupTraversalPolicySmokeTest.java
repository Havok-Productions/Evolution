package org.evolution.features.treeevolution;

public final class TreeGroupTraversalPolicySmokeTest {
    private TreeGroupTraversalPolicySmokeTest() {
    }

    public static void main(String[] args) {
        require(TreeGroupTraversalPolicy.neighborOffsets(false).size() == 6,
                "Routine candidate searches should retain six-face traversal.");
        require(TreeGroupTraversalPolicy.neighborOffsets(true).size() == 26,
                "Authoritative capture must include diagonal fancy-tree foliage.");
        require(TreeGroupTraversalPolicy.maximumDistance(28, true) == 48,
                "Full-crown capture should include wide fancy-tree crowns.");
        System.out.println(
                "Tree group traversal policy smoke test passed: "
                        + "routine=6 full-crown=26 distance=48");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
