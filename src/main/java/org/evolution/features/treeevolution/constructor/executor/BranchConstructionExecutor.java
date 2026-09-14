package org.evolution.features.treeevolution.constructor.executor;

import java.util.Set;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;
import org.evolution.features.treeevolution.constructor.TreeConstructionAttachment;

public final class BranchConstructionExecutor implements TreeConstructionExecutor {
    @Override
    public Set<TreeConstructionSubrule> subrules() {
        return Set.of(TreeConstructionSubrule.PARENT_LINKED_BRANCH_FRAME);
    }

    @Override
    public Set<TreeConstructionAttachment> attachments() {
        return Set.of(TreeConstructionAttachment.BRANCH_PLANNER);
    }

    @Override
    public TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return operations.buildBranchFrame();
    }
}
