package org.evolution.features.treeevolution;

import java.util.Set;
import java.util.UUID;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;

/**
 * Guards the live failure where an expired cleanup counter left a persisted
 * source crown outside the constructor hierarchy forever.
 */
public final class TreeTransitionHierarchyRegressionSmokeTest {
    private TreeTransitionHierarchyRegressionSmokeTest() {
    }

    public static void main(String[] args) {
        TreeDna dna = new TreeDna(
                new UUID(0L, 0L), 0, 64, 0,
                TreeSpecies.OAK, 91L,
                TreePersonality.BALANCED, TreeRarity.COMMON,
                18, 6, 2, 5, 0,
                4, 4, 3, 4, 0.74D,
                0.55D, 0.25D, 0.32D, 0.08D, 0.28D,
                2, 0, 5, 0, 0, 0.60D,
                "config-default", "config.yml", "wild", 0, 6,
                TreeGrowthIntent.CANOPY, 0, 0, 0, 10, 0, 0,
                10, TreeMaturityStage.SMALL,
                0L, 0L, 0, true);
        dna.captureOriginalShape(
                Set.of("world:0:64:0"),
                Set.of("world:1:68:0"));

        TreeGrowthQueuePolicy.Completion complete =
                new TreeGrowthQueuePolicy.Completion(
                        7, 7, 7, 7, 4, 4, 100, 100);
        TreeGrowthQueuePolicy.Budget budget =
                new TreeGrowthQueuePolicy.Budget(1.0D, 1.0D, 1.0D);
        TreeConstructorCore core = new TreeConstructorCore();

        TreeConstructionDecision ownership = core.decide(
                candidate(false), dna, complete, budget,
                TreeGrowthIntent.CANOPY, 0, 0,
                false, true, true);
        require(ownership.subrule()
                        == TreeConstructionSubrule.ROOTED_TREE_OWNERSHIP,
                "an unresolved orphan snapshot must reacquire full tree ownership");

        TreeConstructionDecision cleanup = core.decide(
                candidate(true), dna, complete, budget,
                TreeGrowthIntent.CANOPY, 0, 0,
                false, true, true);
        require(cleanup.subrule()
                        == TreeConstructionSubrule.RETIRED_SOURCE_CROWN,
                "an owned orphan snapshot must resume source-crown retirement");

        System.out.println(
                "Tree transition hierarchy regression smoke test passed: "
                        + "orphan-snapshot=ownership->cleanup");
    }

    private static TreeCandidate candidate(boolean ownershipComplete) {
        return new TreeCandidate(
                null, 0, 64, 0, 70, 7,
                TreeSpecies.OAK, 7, 40, Set.of(),
                ownershipComplete);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}