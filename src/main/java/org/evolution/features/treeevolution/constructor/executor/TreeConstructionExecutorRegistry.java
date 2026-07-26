package org.evolution.features.treeevolution.constructor.executor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionPhase;

/**
 * Immutable phase-to-executor attachment table.
 */
public final class TreeConstructionExecutorRegistry {
    private final Map<TreeConstructionPhase, TreeConstructionExecutor> executors;

    public TreeConstructionExecutorRegistry() {
        EnumMap<TreeConstructionPhase, TreeConstructionExecutor> attachments =
                new EnumMap<>(TreeConstructionPhase.class);
        for (TreeConstructionExecutor executor : List.of(
                new OwnershipGateExecutor(),
                new DamageRepairExecutor(),
                new TransitionReconciler(),
                new TrunkConstructionExecutor(),
                new CanopyConstructionExecutor(),
                new BranchConstructionExecutor(),
                new DetailConstructionExecutor(),
                new StageFinalizer())) {
            for (TreeConstructionPhase phase : executor.phases()) {
                TreeConstructionExecutor previous =
                        attachments.putIfAbsent(phase, executor);
                if (previous != null) {
                    throw new IllegalStateException(
                            "Constructor phase " + phase
                                    + " is attached to both "
                                    + previous.getClass().getSimpleName()
                                    + " and "
                                    + executor.getClass().getSimpleName());
                }
            }
        }
        for (TreeConstructionPhase phase : TreeConstructionPhase.values()) {
            if (!attachments.containsKey(phase)) {
                throw new IllegalStateException(
                        "Constructor phase has no executor: " + phase);
            }
        }
        executors = Map.copyOf(attachments);
    }

    public TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return executors.get(decision.phase()).execute(decision, operations);
    }

    public String executorName(TreeConstructionPhase phase) {
        return executors.get(phase).getClass().getSimpleName();
    }
}
