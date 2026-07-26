package org.evolution.features.treeevolution;

public final class TreeGrowthQueuePolicySmokeTest {
    private TreeGrowthQueuePolicySmokeTest() {
    }

    public static void main(String[] args) {
        for (TreeMaturityStage stage : TreeMaturityStage.values()) {
            TreeGrowthQueuePolicy.Budget budget =
                    TreeGrowthQueuePolicy.stageBudget(stage);
            require(budget.trunkPercent() == 1.0D,
                    stage + " trunk target is partial.");
            require(budget.branchPercent() == 1.0D,
                    stage + " branch target is partial.");
            require(budget.canopyPercent() == 1.0D,
                    stage + " canopy target is partial.");
        }

        System.out.println(
                "Tree growth queue policy smoke test passed: "
                        + "every stage requires its complete reachable model.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}