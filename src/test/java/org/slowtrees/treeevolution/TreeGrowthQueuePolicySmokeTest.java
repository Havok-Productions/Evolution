package org.slowtrees.treeevolution;

import java.util.UUID;

public final class TreeGrowthQueuePolicySmokeTest {
    private TreeGrowthQueuePolicySmokeTest() {
    }

    public static void main(String[] args) {
        TreeDna oak = dna(TreeMaturityStage.MATURE);
        TreeGrowthQueuePolicy.Budget budget = TreeGrowthQueuePolicy.stageBudget(oak);

        assertSelection(
                "trunk leads first",
                TreeGrowthIntent.HEIGHT,
                "trunk-budget",
                TreeGrowthQueuePolicy.select(oak, new TreeGrowthQueuePolicy.Completion(5, 16, 5, 100, 0, 100, 0, 100), budget, TreeGrowthIntent.WIDTH)
        );
        assertSelection(
                "planned trunk progress wins over misleading live height",
                TreeGrowthIntent.HEIGHT,
                "trunk-budget",
                TreeGrowthQueuePolicy.select(oak, new TreeGrowthQueuePolicy.Completion(16, 16, 30, 100, 0, 100, 50, 100), budget, TreeGrowthIntent.CANOPY)
        );
        assertSelection(
                "branch starts after trunk budget",
                TreeGrowthIntent.BRANCH,
                "branch-budget",
                TreeGrowthQueuePolicy.select(oak, new TreeGrowthQueuePolicy.Completion(16, 16, 100, 100, 5, 100, 45, 100), budget, TreeGrowthIntent.CANOPY)
        );
        assertSelection(
                "canopy catches up when branch outruns leaves",
                TreeGrowthIntent.CANOPY,
                "canopy-catch-up",
                TreeGrowthQueuePolicy.select(oak, new TreeGrowthQueuePolicy.Completion(16, 16, 100, 100, 36, 100, 8, 100), budget, TreeGrowthIntent.BRANCH)
        );
        assertSelection(
                "canopy fills before detail when branches are already ready",
                TreeGrowthIntent.CANOPY,
                "canopy-catch-up",
                TreeGrowthQueuePolicy.select(oak, new TreeGrowthQueuePolicy.Completion(16, 16, 100, 100, 60, 100, 48, 100), budget, TreeGrowthIntent.DETAIL)
        );
        assertSelection(
                "plain canopy budget still works without branch pressure",
                TreeGrowthIntent.CANOPY,
                "canopy-budget",
                TreeGrowthQueuePolicy.select(oak, new TreeGrowthQueuePolicy.Completion(16, 16, 100, 100, 0, 0, 30, 100), budget, TreeGrowthIntent.DETAIL)
        );
        assertSelection(
                "detail waits for structure budgets",
                TreeGrowthIntent.DETAIL,
                "stage-complete",
                TreeGrowthQueuePolicy.select(oak, new TreeGrowthQueuePolicy.Completion(16, 16, 100, 100, 70, 100, 70, 100), budget, TreeGrowthIntent.DETAIL)
        );

        System.out.println("Tree growth queue policy smoke test passed.");
    }
    private static void assertSelection(String name, TreeGrowthIntent expectedIntent, String expectedReason, TreeGrowthQueuePolicy.Selection actual) {
        if (actual.intent() != expectedIntent || !actual.reason().equals(expectedReason)) {
            throw new IllegalStateException(name + " expected " + expectedIntent + "/" + expectedReason
                    + " but got " + actual.intent() + "/" + actual.reason());
        }
        if (actual.intent() == TreeGrowthIntent.WIDTH) {
            throw new IllegalStateException(name + " selected WIDTH, which should not hijack this progress queue");
        }
    }

    private static TreeDna dna(TreeMaturityStage stage) {
        return new TreeDna(
                UUID.nameUUIDFromBytes(("queue-policy-" + stage).getBytes()),
                0,
                64,
                0,
                TreeSpecies.OAK,
                12345L,
                TreePersonality.BALANCED,
                TreeRarity.COMMON,
                16,
                6,
                2,
                4,
                0,
                4,
                4,
                2,
                4,
                0.72D,
                0.50D,
                0.30D,
                0.0D,
                0.0D,
                0.20D,
                1,
                0,
                4,
                0,
                0,
                0.55D,
                "queue-policy-smoke",
                "TreeGrowthQueuePolicySmokeTest",
                "wild",
                0,
                TreeGrowthIntent.HEIGHT,
                0,
                0,
                0,
                0,
                0,
                0,
                switch (stage) {
                    case SMALL -> 2;
                    case MEDIUM -> 12;
                    case MATURE -> 40;
                    case ANCIENT -> 240;
                },
                stage,
                0L,
                0L,
                0,
                true
        );
    }
}
