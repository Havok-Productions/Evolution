package org.evolution.features.treeevolution.constructor.executor;

import java.util.Set;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;
import org.evolution.features.treeevolution.constructor.TreeConstructionAttachment;

public final class TransitionReconciler implements TreeConstructionExecutor {
    @Override
    public Set<TreeConstructionSubrule> subrules() {
        return Set.of(
                TreeConstructionSubrule.READY_SOURCE_LEAF_BLOCKER,
                TreeConstructionSubrule.DISCONNECTED_EVOLVED_STRUCTURE,
                TreeConstructionSubrule.CONFLICTING_EVOLVED_TARGET,
                TreeConstructionSubrule.OBSOLETE_EVOLVED_STRUCTURE,
                TreeConstructionSubrule.RETIRED_SOURCE_CROWN);
    }

    @Override
    public Set<TreeConstructionAttachment> attachments() {
        return Set.of(TreeConstructionAttachment.TRANSITION_RECONCILER);
    }

    @Override
    public TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return switch (decision.subrule()) {
            case READY_SOURCE_LEAF_BLOCKER ->
                    operations.replaceTransitionBlocker();
            case DISCONNECTED_EVOLVED_STRUCTURE ->
                    operations.retireDisconnectedEvolvedStructure();
            case CONFLICTING_EVOLVED_TARGET ->
                    operations.retireConflictingEvolvedTarget();
            case OBSOLETE_EVOLVED_STRUCTURE ->
                    operations.retireObsoleteEvolvedStructure();
            case RETIRED_SOURCE_CROWN -> operations.retireSourceCrown();
            default -> throw new IllegalStateException(
                    "Transition reconciler cannot own "
                            + decision.subrule());
        };
    }
}
