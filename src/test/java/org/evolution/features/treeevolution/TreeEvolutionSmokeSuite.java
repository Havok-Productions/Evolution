package org.evolution.features.treeevolution;

import java.util.Arrays;
import org.evolution.features.treeevolution.constructor.TreeConstructionHierarchySmokeTest;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionExecutorRegistrySmokeTest;

/**
 * One entry point for the current tree evolution regression layers.
 */
public final class TreeEvolutionSmokeSuite {
    private TreeEvolutionSmokeSuite() {
    }

    public static void main(String[] args) throws Exception {
        boolean render = Arrays.asList(args).contains("--render");
        TreeConstructionHierarchySmokeTest.main(new String[0]);
        TreeConstructionExecutorRegistrySmokeTest.main(new String[0]);
        TreeBranchEnvelopeOwnershipPolicySmokeTest.main(new String[0]);
        TreeBranchTipIntegrityPolicySmokeTest.main(new String[0]);
        TreeCanopyIntegrityPolicySmokeTest.main(new String[0]);
        TreeCanopyTransitionPolicySmokeTest.main(new String[0]);
        TreeTransitionLedgerSmokeTest.main(new String[0]);
        TreeGroupTraversalPolicySmokeTest.main(new String[0]);
        TreeGrowthQueuePolicySmokeTest.main(new String[0]);
        TreeFocusPolicySmokeTest.main(new String[0]);
        TreeSourceStagePolicySmokeTest.main(new String[0]);
        TreeCanopySilhouetteSmokeTest.main(new String[0]);
        LiveTreeDnaRegressionSmokeTest.main(new String[0]);
        TreeEvolutionLifecycleSmokeTest.main(new String[0]);
        if (render) {
            TreeShapeSmokeTest.main(new String[0]);
        }
        System.out.println("Evolution tree smoke suite passed: mode="
                + (render ? "full-render" : "fast"));
    }
}