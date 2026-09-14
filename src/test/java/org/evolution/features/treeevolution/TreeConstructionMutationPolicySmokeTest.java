package org.evolution.features.treeevolution;

import java.util.LinkedHashMap;

/** Verifies the final world-mutation boundary is monotonic. */
public final class TreeConstructionMutationPolicySmokeTest {
    private TreeConstructionMutationPolicySmokeTest() {
    }

    public static void main(String[] args) {
        TreeDna dna = TreeShapeSmokeTest.sampleDna(
                TreeSpecies.OAK, TreeMaturityStage.MEDIUM, 0);
        TreePlan plan = TreeShapeSmokeTest.treeBodyPlan(dna);
        LinkedHashMap<String, PlannedTreeBlock> byKey =
                new LinkedHashMap<>();
        for (PlannedTreeBlock block : plan.orderedBlocks()) {
            byKey.put(block.key(), block);
        }
        CachedTreePlan cached = new CachedTreePlan(
                "mutation-policy-smoke", plan,
                plan.orderedBlocks(), java.util.Map.copyOf(byKey));
        PlannedTreeBlock wood = plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.TRUNK
                        || block.role() == TreeBlockRole.BRANCH)
                .findFirst().orElseThrow();
        String woodReceipt = dna.worldId() + ":" + wood.key();
        require(dna.markEvolvedBlock(woodReceipt, wood.role()),
                "could not seed current-target ownership");
        require(!TreeConstructionMutationPolicy.retirement(
                        dna, cached, woodReceipt,
                        wood.material(), false).allowed(),
                "current target wood was not protected");

        String obsolete = dna.worldId() + ":99:99:99";
        require(TreeConstructionMutationPolicy.retirement(
                        dna, cached, obsolete,
                        dna.species().logMaterial(), false).allowed(),
                "outside-target wood could not retire");

        PlannedTreeBlock canopy = plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.CANOPY)
                .findFirst().orElseThrow();
        String conflict = dna.worldId() + ":" + canopy.key();
        require(TreeConstructionMutationPolicy.retirement(
                        dna, cached, conflict,
                        dna.species().logMaterial(), false).allowed(),
                "wrong-role wood could not reconcile from a canopy target");
        System.out.println(
                "Tree construction mutation policy smoke passed: "
                        + "current-target=protected obsolete=allowed conflict=allowed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
