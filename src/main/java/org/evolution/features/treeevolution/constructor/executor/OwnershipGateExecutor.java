package org.evolution.features.treeevolution.constructor.executor;

import java.util.Set;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionPhase;

public final class OwnershipGateExecutor implements TreeConstructionExecutor {
    @Override
    public Set<TreeConstructionPhase> phases() {
        return Set.of(
                TreeConstructionPhase.WAIT_FOR_OWNERSHIP,
                TreeConstructionPhase.WAIT_FOR_SOURCE_SNAPSHOT);
    }

    @Override
    public TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return decision.phase() == TreeConstructionPhase.WAIT_FOR_OWNERSHIP
                ? operations.waitForOwnership()
                : operations.waitForSourceSnapshot();
    }
}
