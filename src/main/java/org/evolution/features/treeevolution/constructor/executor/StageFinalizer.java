package org.evolution.features.treeevolution.constructor.executor;

import java.util.Set;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionPhase;

public final class StageFinalizer implements TreeConstructionExecutor {
    @Override
    public Set<TreeConstructionPhase> phases() {
        return Set.of(
                TreeConstructionPhase.FINALIZE_TRANSITION,
                TreeConstructionPhase.COMPLETE);
    }

    @Override
    public TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return decision.phase() == TreeConstructionPhase.FINALIZE_TRANSITION
                ? operations.finalizeTransition()
                : operations.complete();
    }
}
