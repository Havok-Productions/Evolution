package org.evolution.features.treeevolution.constructor.executor;

import java.util.Set;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;
import org.evolution.features.treeevolution.constructor.TreeConstructionAttachment;

public final class DetailConstructionExecutor implements TreeConstructionExecutor {
    @Override
    public Set<TreeConstructionSubrule> subrules() {
        return Set.of(TreeConstructionSubrule.POST_STRUCTURE_DETAIL);
    }

    @Override
    public Set<TreeConstructionAttachment> attachments() {
        return Set.of(TreeConstructionAttachment.DETAIL_PLANNERS);
    }

    @Override
    public TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return operations.buildDetails();
    }
}
