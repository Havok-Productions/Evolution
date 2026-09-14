package org.evolution.features.treeevolution;

import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionHierarchy;
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

    TreeConstructionDecision decide(TreeConstructionSnapshot snapshot) {
        return hierarchy.decide(snapshot.state());
    }

    TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return executors.execute(decision, operations);
    }

    String executorName(TreeConstructionDecision decision) {
        return executors.executorName(decision.subrule());
    }

}
