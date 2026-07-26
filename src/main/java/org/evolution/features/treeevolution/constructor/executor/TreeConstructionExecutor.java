package org.evolution.features.treeevolution.constructor.executor;

import java.util.Set;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionPhase;

public interface TreeConstructionExecutor {
    Set<TreeConstructionPhase> phases();

    TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations);
}
