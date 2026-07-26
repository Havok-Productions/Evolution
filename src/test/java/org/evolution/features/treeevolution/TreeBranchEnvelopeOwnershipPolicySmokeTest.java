package org.evolution.features.treeevolution;

public final class TreeBranchEnvelopeOwnershipPolicySmokeTest {
    private TreeBranchEnvelopeOwnershipPolicySmokeTest() {
    }

    public static void main(String[] args) {
        require(TreeBranchEnvelopeOwnershipPolicy.countsAsEvolvedLeaf(
                        false, false, false, true),
                "A completed legacy tree should remain grandfathered.");
        require(!TreeBranchEnvelopeOwnershipPolicy.countsAsEvolvedLeaf(
                        true, false, false, true),
                "A current source leaf must not count before reform.");
        require(TreeBranchEnvelopeOwnershipPolicy.countsAsEvolvedLeaf(
                        true, true, false, true),
                "Explicit reform must transfer a source leaf into evolved ownership.");
        require(!TreeBranchEnvelopeOwnershipPolicy.countsAsEvolvedLeaf(
                        true, false, true, true),
                "Legacy migration must not infer ownership for a source leaf.");
        require(TreeBranchEnvelopeOwnershipPolicy.countsAsEvolvedLeaf(
                        true, false, true, false),
                "Legacy migration may infer a post-snapshot planned leaf.");
        require(TreeBranchEnvelopeOwnershipPolicy.shouldReformOriginalLeaf(
                        true, true, false),
                "An unowned original leaf in the planned canopy should be reformed.");
        require(!TreeBranchEnvelopeOwnershipPolicy.shouldReformOriginalLeaf(
                        true, true, true),
                "An already owned original leaf should not be reformed repeatedly.");
        require(!TreeBranchEnvelopeOwnershipPolicy.shouldReformOriginalLeaf(
                        false, true, false),
                "Leaves outside the planned canopy must not be claimed by reform.");

        System.out.println(
                "Tree branch-envelope ownership policy smoke test passed: "
                        + "preexisting=false reformed=true legacy-safe=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
