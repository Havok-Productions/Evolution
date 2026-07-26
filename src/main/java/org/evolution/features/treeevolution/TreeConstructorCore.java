package org.evolution.features.treeevolution;

import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionHierarchy;
import org.evolution.features.treeevolution.constructor.TreeConstructionState;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionExecutorRegistry;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionOperations;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionResult;

/**
 * ## TREE CONSTRUCTOR CORE
 *
 * <p>This is the only adapter allowed to choose which constructor subsystem
 * owns the next live tree action. Species planners still create the immutable
 * target, while this core orders transition, trunk, branch, canopy, detail,
 * and finalization work without letting those systems act concurrently.</p>
 *
 * <p>## Each decision now carries one phase-owned subrule plus an independent
 * final formation audit. The executor remains owned only by the parent phase,
 * while debug output can identify the exact smaller contract that blocked it.</p>
 */
final class TreeConstructorCore {
    private final TreeConstructionHierarchy hierarchy =
            new TreeConstructionHierarchy();
    private final TreeConstructionExecutorRegistry executors =
            new TreeConstructionExecutorRegistry();

    TreeConstructionDecision decide(
            TreeCandidate candidate,
            TreeDna dna,
            TreeGrowthQueuePolicy.Completion completion,
            TreeGrowthQueuePolicy.Budget budget,
            TreeGrowthIntent requestedIntent,
            int exposedUpperLogs,
            int uncoveredBranchTips,
            boolean transitionBlockerReady,
            boolean broadCleanupReady,
            boolean retiredCrownRemaining
    ) {
        boolean stageComplete = TreeFocusPolicy.stageStructureComplete(
                completion, budget, exposedUpperLogs, uncoveredBranchTips);
        boolean transitionPending = TreeFocusPolicy.transitionPending(
                dna.stageCleanupBurst(), dna.stageGrowthBurst(),
                stageComplete, dna.hasOriginalShapeSnapshot());
        boolean completeOwnershipRequired =
                TreeFocusPolicy.completeOwnershipRequired(
                        dna.stageCleanupBurst(),
                        dna.damageCount(),
                        requestedIntent == TreeGrowthIntent.REPAIR,
                        stageComplete,
                        dna.hasOriginalShapeSnapshot(),
                        dna.unresolvedOriginalShapeLeafCount());
        TreeConstructionState state = new TreeConstructionState(
                !completeOwnershipRequired || candidate.ownershipComplete(),
                dna.hasOriginalShapeSnapshot(),
                transitionPending,
                dna.damageCount() > 0
                        || requestedIntent == TreeGrowthIntent.REPAIR,
                transitionBlockerReady,
                broadCleanupReady,
                retiredCrownRemaining,
                exposedUpperLogs,
                uncoveredBranchTips,
                completion.trunkPercent(),
                completion.branchPercent(),
                completion.canopyPercent(),
                budget.trunkPercent(),
                budget.branchPercent(),
                canopyShellTarget(dna),
                budget.canopyPercent(),
                requestedIntent == TreeGrowthIntent.DETAIL
                        || requestedIntent == TreeGrowthIntent.SEEDLING
        );
        return hierarchy.decide(state);
    }

    private double canopyShellTarget(TreeDna dna) {
        return switch (dna.maturityStage()) {
            case SMALL -> 0.18D;
            case MEDIUM -> 0.24D;
            case MATURE -> 0.30D;
            case ANCIENT -> 0.32D;
        };
    }

    TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return executors.execute(decision, operations);
    }

    String executorName(TreeConstructionDecision decision) {
        return executors.executorName(decision.phase());
    }

}
