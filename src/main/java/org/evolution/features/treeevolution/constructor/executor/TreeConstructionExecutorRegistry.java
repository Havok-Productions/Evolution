package org.evolution.features.treeevolution.constructor.executor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;

/**
 * Immutable phase-to-executor attachment table.
 */
public final class TreeConstructionExecutorRegistry {
    private final Map<TreeConstructionSubrule, TreeConstructionExecutor> executors;

    public TreeConstructionExecutorRegistry() {
        EnumMap<TreeConstructionSubrule, TreeConstructionExecutor> attachments =
                new EnumMap<>(TreeConstructionSubrule.class);
        for (TreeConstructionExecutor executor : List.of(
                new OwnershipGateExecutor(),
                new DamageRepairExecutor(),
                new TransitionReconciler(),
                new TrunkConstructionExecutor(),
                new CanopyConstructionExecutor(),
                new BranchConstructionExecutor(),
                new DetailConstructionExecutor(),
                new StageFinalizer())) {
            for (TreeConstructionSubrule subrule : executor.subrules()) {
                if (!executor.attachments().contains(
                        subrule.attachment())) {
                    throw new IllegalStateException(
                            executor.getClass().getSimpleName()
                                    + " claims " + subrule
                                    + " but does not own attachment "
                                    + subrule.attachment());
                }
                TreeConstructionExecutor previous =
                        attachments.putIfAbsent(subrule, executor);
                if (previous != null) {
                    throw new IllegalStateException(
                            "Constructor subrule " + subrule
                                    + " is attached to both "
                                    + previous.getClass().getSimpleName()
                                    + " and "
                                    + executor.getClass().getSimpleName());
                }
            }
        }
        for (TreeConstructionSubrule subrule
                : TreeConstructionSubrule.values()) {
            if (!attachments.containsKey(subrule)) {
                throw new IllegalStateException(
                        "Constructor subrule has no executor: " + subrule);
            }
        }
        executors = Map.copyOf(attachments);
    }

    public TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return executors.get(decision.subrule()).execute(decision, operations);
    }

    public String executorName(TreeConstructionSubrule subrule) {
        return executors.get(subrule).getClass().getSimpleName();
    }
}
