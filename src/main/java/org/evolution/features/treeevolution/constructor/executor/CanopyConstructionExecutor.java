package org.evolution.features.treeevolution.constructor.executor;

import java.util.Set;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;
import org.evolution.features.treeevolution.constructor.TreeConstructionAttachment;

public final class CanopyConstructionExecutor implements TreeConstructionExecutor {
    @Override
    public Set<TreeConstructionSubrule> subrules() {
        return Set.of(
                TreeConstructionSubrule.COVER_EXPOSED_SUPPORT,
                TreeConstructionSubrule
                        .UNPLANNED_BARE_TERMINAL_RETIREMENT,
                TreeConstructionSubrule.STALE_ENVELOPE_LEAF_RETIREMENT,
                TreeConstructionSubrule.OWNED_BRANCH_ENVELOPE,
                TreeConstructionSubrule.MINIMUM_CROWN_SHELL,
                TreeConstructionSubrule.CANOPY_STAGE_TARGET);
    }

    @Override
    public Set<TreeConstructionAttachment> attachments() {
        return Set.of(TreeConstructionAttachment.CANOPY_PLANNER);
    }

    @Override
    public TreeConstructionResult execute(
            TreeConstructionDecision decision,
            TreeConstructionOperations operations) {
        return switch (decision.subrule()) {
            case COVER_EXPOSED_SUPPORT -> operations.coverExposedSupport();
            case UNPLANNED_BARE_TERMINAL_RETIREMENT ->
                    operations.retireUnplannedBareTerminal();
            case STALE_ENVELOPE_LEAF_RETIREMENT ->
                    operations.retireStaleEnvelopeLeaf();
            case OWNED_BRANCH_ENVELOPE -> operations.repairBranchEnvelope();
            case MINIMUM_CROWN_SHELL -> operations.buildMinimumCrownShell();
            case CANOPY_STAGE_TARGET -> operations.fillCanopy();
            default -> throw new IllegalStateException(
                    "Canopy executor cannot own " + decision.subrule());
        };
    }
}
