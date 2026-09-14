package org.evolution.features.treeevolution.constructor.executor;

import java.util.Set;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;
import org.evolution.features.treeevolution.constructor.TreeConstructionAttachment;

public final class StageFinalizer implements TreeConstructionExecutor {
    @Override
    public Set<TreeConstructionSubrule> subrules() {
        return Set.of(
                TreeConstructionSubrule.TRANSITION_CONTRACT_COMPLETE,
                TreeConstructionSubrule.STAGE_CONTRACT_COMPLETE);
    }

    @Override
    public Set<TreeConstructionAttachment> attachments() {
        return Set.of(TreeConstructionAttachment.STAGE_FINALIZER,
                TreeConstructionAttachment.NONE);
    }

    @Override
    public TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return decision.subrule()
                == TreeConstructionSubrule.TRANSITION_CONTRACT_COMPLETE
                ? operations.finalizeTransition()
                : operations.complete();
    }
}
