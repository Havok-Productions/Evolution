package org.evolution.features.treeevolution.constructor.executor;

import java.util.Set;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionPhase;

public final class TransitionReconciler implements TreeConstructionExecutor {
    @Override
    public Set<TreeConstructionPhase> phases() {
        return Set.of(
                TreeConstructionPhase.REPLACE_TRANSITION_BLOCKER,
                TreeConstructionPhase.PRUNE_RETIRED_CROWN);
    }

    @Override
    public TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return decision.phase()
                == TreeConstructionPhase.REPLACE_TRANSITION_BLOCKER
                ? operations.replaceTransitionBlocker()
                : operations.pruneRetiredCrown();
    }
}
