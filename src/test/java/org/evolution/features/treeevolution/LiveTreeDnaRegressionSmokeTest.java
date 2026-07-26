package org.evolution.features.treeevolution;

import java.util.List;
import java.util.UUID;

public final class LiveTreeDnaRegressionSmokeTest {
    private static final TreeEvolutionPlanner PLANNER = new TreeEvolutionPlanner();
    private static final TreeShapeEngine SHAPE_ENGINE = new TreeShapeEngine();
    private static final UUID FIXTURE_WORLD = new UUID(0L, 0L);

    private LiveTreeDnaRegressionSmokeTest() {
    }

    public static void main(String[] args) {
        assertLiveTerminalClassification();
        List<TreeDna> fixtures = List.of(reportedOak(), reportedBirch());
        for (TreeDna dna : fixtures) {
            TreePlan plan = PLANNER.plan(dna, null, false);
            TreeShapeEngine.ShapeReport report = SHAPE_ENGINE.analyze(plan, dna);
            require(report.branchTips() > 0,
                    dna.species() + " fixture lost all branch variation");
            require(report.coveredBranchTips() == report.branchTips(),
                    dna.species() + " fixture has a branch without a natural canopy envelope");
            require(report.topCovered(),
                    dna.species() + " fixture leaves its upper trunk exposed");
            for (TreeBranchPlan branch : plan.branchPlans()) {
                TreeBranchPlan.BranchTip tip = branch.tip();
                require(TreeBranchTipIntegrityPolicy.hasPreplannedEnvelope(
                                dna, tip.x(), tip.y(), tip.z(), plan.blocksByKey()),
                        dna.species() + " branch " + branch.id()
                                + " lacks a connected natural envelope");
            }
            System.out.println(dna.species().id()
                    + " base=" + dna.baseX() + "," + dna.baseY() + "," + dna.baseZ()
                    + " branches=" + report.branchTips()
                    + " covered=" + report.coveredBranchTips()
                    + " leaves=" + report.leaves()
                    + " pruned-invalid=" + plan.prunedBranchCount());
        }
        System.out.println("Live tree DNA regression smoke test passed: fixtures=2");
    }

    private static void assertLiveTerminalClassification() {
        require(TreeLiveTerminalPolicy.classify(
                        true, null, 8, 4, 0)
                        == TreeLiveTerminalPolicy.Decision.PRUNE_UNPLANNED_BARE_TERMINAL,
                "a floating unplanned log must be pruned");
        require(TreeLiveTerminalPolicy.classify(
                        true, null, 8, 4, 1)
                        == TreeLiveTerminalPolicy.Decision.PRUNE_UNPLANNED_BARE_TERMINAL,
                "a stale one-parent protrusion must be pruned");
        require(TreeLiveTerminalPolicy.classify(
                        true, TreeBlockRole.BRANCH, 8, 4, 1)
                        == TreeLiveTerminalPolicy.Decision.KEEP_PLANNED_WOOD,
                "an incomplete planned branch must remain available for growth");
        require(TreeLiveTerminalPolicy.classify(
                        true, null, 8, 4, 1)
                        == TreeLiveTerminalPolicy.Decision.PRUNE_UNPLANNED_BARE_TERMINAL,
                "incidental leaves must not legitimize wood absent from the branch plan");
    }
    // ## Exact structural traits from the reported live oak; no world or player data is required.
    private static TreeDna reportedOak() {
        return new TreeDna(
                FIXTURE_WORLD, -345, 72, 2421,
                TreeSpecies.OAK, 8243259114096774696L,
                TreePersonality.WINDSWEPT, TreeRarity.COMMON,
                22, 7, 2, 5, 1,
                5, 5, 3, 5, 0.74D,
                0.5589341422593272D, 0.32666098653255393D,
                0.32D, 0.08D, 0.28D,
                3, 1, 6, -1, 0, 0.6860276658139011D,
                "config-default", "config.yml", "wild", 0, 5,
                TreeGrowthIntent.CANOPY, 34, 0, 2, 622, 0, 0,
                625, TreeMaturityStage.MEDIUM,
                0L, 0L, 0, true);
    }

    // ## Exact structural traits from the reported live birch; this guards its narrow natural crown.
    private static TreeDna reportedBirch() {
        return new TreeDna(
                FIXTURE_WORLD, 759, 70, -3571,
                TreeSpecies.BIRCH, 239086054919454925L,
                TreePersonality.TALL, TreeRarity.UNCOMMON,
                26, 3, 2, 3, 0,
                3, 3, 3, 3, 0.58D,
                0.62D, 0.24D, 0.08D, 0.02D, 0.16D,
                1, 0, 3, 0, 0, 0.5768312142615037D,
                "config-default", "config.yml", "wild", 0, 5,
                TreeGrowthIntent.CANOPY, 14, 0, 0, 231, 0, 0,
                231, TreeMaturityStage.MEDIUM,
                0L, 0L, 0, true);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}