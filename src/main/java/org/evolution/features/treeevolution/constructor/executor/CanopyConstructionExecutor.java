package org.evolution.features.treeevolution.constructor.executor;

import java.util.Set;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionPhase;

public final class CanopyConstructionExecutor implements TreeConstructionExecutor {
    @Override
    public Set<TreeConstructionPhase> phases() {
        return Set.of(
                TreeConstructionPhase.BUILD_CANOPY_SHELL,
                TreeConstructionPhase.FILL_CANOPY);
    }

    @Override
    public TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return decision.phase() == TreeConstructionPhase.BUILD_CANOPY_SHELL
                ? operations.buildCanopyShell()
                : operations.fillCanopy();
    }
}
