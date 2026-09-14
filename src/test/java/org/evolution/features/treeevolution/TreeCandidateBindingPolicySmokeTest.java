package org.evolution.features.treeevolution;

/**
 * ## Guards the scheduler bug where a branch-column alias replaced the stump.
 */
public final class TreeCandidateBindingPolicySmokeTest {
    private TreeCandidateBindingPolicySmokeTest() {
    }

    public static void main(String[] args) {
        String rooted = "fixture-world:10:64:10";
        require(TreeCandidateBindingPolicy.matches(rooted, rooted),
                "exact stump identity must remain canonical");
        require(!TreeCandidateBindingPolicy.matches(
                        "fixture-world:11:68:10", rooted),
                "nearby branch column must require DNA rebinding");
        require(!TreeCandidateBindingPolicy.matches(null, rooted),
                "missing candidate identity must fail closed");
        System.out.println(
                "Tree candidate binding smoke test passed: exact=true alias=false");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
