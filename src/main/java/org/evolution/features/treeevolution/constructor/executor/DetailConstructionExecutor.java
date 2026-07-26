package org.evolution.features.treeevolution.constructor.executor;

import java.util.Set;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionPhase;

public final class DetailConstructionExecutor implements TreeConstructionExecutor {
    @Override
    public Set<TreeConstructionPhase> phases() {
        return Set.of(TreeConstructionPhase.BUILD_DETAILS);
    }

    @Override
    public TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return operations.buildDetails();
    }
}
