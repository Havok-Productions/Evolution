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
                TreeSpecies.OAK, TreeVariant.OAK_STANDARD,
                TreeSourcePattern.unknown(), 91L,
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

        TreeConstructionDecision ownership = core.decide(snapshot(
                candidate(false), dna, complete, budget,
                false, true, true, false));
        require(ownership.subrule()
                        == TreeConstructionSubrule.ROOTED_TREE_OWNERSHIP,
                "an unresolved orphan snapshot must reacquire full tree ownership");

        TreeConstructionDecision cleanup = core.decide(snapshot(
                candidate(true), dna, complete, budget,
                false, true, true, false));
        require(cleanup.subrule()
                        == TreeConstructionSubrule.RETIRED_SOURCE_CROWN,
                "an owned orphan snapshot must resume source-crown retirement");

        TreeConstructionDecision unresolved = core.decide(snapshot(
                candidate(true), dna, complete, budget,
                false, false, true, false));
        require(unresolved.subrule()
                        == TreeConstructionSubrule.RETIRED_SOURCE_CROWN,
                "an unresolved source crown must never finalize when its next safe leaf is temporarily unavailable");

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

    private static TreeConstructionSnapshot snapshot(
            TreeCandidate candidate,
            TreeDna dna,
            TreeGrowthQueuePolicy.Completion completion,
            TreeGrowthQueuePolicy.Budget budget,
            boolean obsolete,
            boolean retiredCrown,
            boolean broadCleanup,
            boolean sourceResolved
    ) {
        return TreeConstructionSnapshot.capture(
                candidate, dna, completion, budget,
                TreeGrowthIntent.CANOPY,
                new TreeConstructionSnapshot.Facts(
                        0, 0, 0, 0, 0,
                        false, false, false, false, false,
                        broadCleanup, obsolete, retiredCrown,
                        sourceResolved));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
