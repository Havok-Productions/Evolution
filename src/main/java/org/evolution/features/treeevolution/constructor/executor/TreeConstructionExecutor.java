package org.evolution.features.treeevolution.constructor.executor;

import java.util.Set;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;
import org.evolution.features.treeevolution.constructor.TreeConstructionAttachment;

public interface TreeConstructionExecutor {
    Set<TreeConstructionSubrule> subrules();

    Set<TreeConstructionAttachment> attachments();

    TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations);
}
