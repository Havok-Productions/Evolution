package org.evolution.features.treeevolution.constructor.executor;

import java.util.Set;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;
import org.evolution.features.treeevolution.constructor.TreeConstructionAttachment;

public final class OwnershipGateExecutor implements TreeConstructionExecutor {
    @Override
    public Set<TreeConstructionSubrule> subrules() {
        return Set.of(
                TreeConstructionSubrule.ROOTED_TREE_OWNERSHIP,
                TreeConstructionSubrule.IMMUTABLE_SOURCE_SNAPSHOT);
    }

    @Override
    public Set<TreeConstructionAttachment> attachments() {
        return Set.of(TreeConstructionAttachment.OWNERSHIP_GATE,
                TreeConstructionAttachment.SOURCE_SNAPSHOT);
    }

    @Override
    public TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return decision.subrule()
                == TreeConstructionSubrule.ROOTED_TREE_OWNERSHIP
                ? operations.waitForOwnership()
                : operations.waitForSourceSnapshot();
    }
}
