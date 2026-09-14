package org.evolution.features.treeevolution;

/**
 * ## Gradually constructs every named variant through the live hierarchy.
 */
public final class TreeVariantConstructionSmokeTest {
    private TreeVariantConstructionSmokeTest() {
    }

    public static void main(String[] args) {
        int scenarios = 0;
        int steps = 0;
        for (TreeVariant variant : TreeVariant.values()) {
            TreeDna dna = TreeShapeSmokeTest.sampleDna(
                    variant, TreeMaturityStage.MEDIUM, 2);
            TreeConstructionReplayHarness.ReplayResult result =
                    new TreeConstructionReplayHarness(
                            "variant-" + variant.id(), dna, false)
                            .run();
            require(result.species() == variant.species(),
                    variant + " changed species during replay");
            require(result.stage() == TreeMaturityStage.MEDIUM,
                    variant + " changed maturity during replay");
            scenarios++;
            steps += result.steps();
        }
        System.out.println(
                "Tree variant construction smoke test passed: variants="
                        + scenarios + " block-by-block=true "
                        + "restart=true unload=true damage=true "
                        + "final-plan-match=true total-steps=" + steps);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
