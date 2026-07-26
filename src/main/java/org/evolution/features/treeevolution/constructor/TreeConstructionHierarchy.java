package org.evolution.features.treeevolution.constructor;

/**
 * Selects one exclusive construction phase and ordered subrule.
 *
 * <p>The ordering is intentional: ownership and transition safety outrank
 * shape work, support outranks branches, and broad pruning cannot run until
 * the replacement structure has reached its stage targets.</p>
 */
public final class TreeConstructionHierarchy {
    private final TreeConstructionFinalAudit finalAudit =
            new TreeConstructionFinalAudit();

    public TreeConstructionDecision decide(TreeConstructionState state) {
        TreeConstructionAudit audit = finalAudit.inspect(state);
        if (!state.ownershipComplete()) {
            return decision(
                    TreeConstructionSubrule.ROOTED_TREE_OWNERSHIP, audit,
                    "complete rooted-tree ownership is required");
        }
        if (state.transitionPending() && !state.sourceSnapshotReady()) {
            return decision(
                    TreeConstructionSubrule.IMMUTABLE_SOURCE_SNAPSHOT, audit,
                    "the immutable source shape must exist before transition work");
        }
        if (state.damageRepairRequested()) {
            return decision(
                    TreeConstructionSubrule.INTERRUPTED_DAMAGE_REPAIR, audit,
                    "player damage or interrupted construction has priority");
        }
        if (state.transitionPending() && state.transitionBlockerReady()) {
            return decision(
                    TreeConstructionSubrule.READY_SOURCE_LEAF_BLOCKER, audit,
                    "replace one source leaf directly with its ready planned wood");
        }
        if (state.trunkProgress() < state.trunkTarget()) {
            return decision(
                    TreeConstructionSubrule.SUPPORT_STAGE_TARGET, audit,
                    "planned support structure is below its stage target");
        }
        if (state.exposedUpperLogs() > 0) {
            return decision(
                    TreeConstructionSubrule.COVER_EXPOSED_SUPPORT, audit,
                    "upper support wood needs planned crown coverage");
        }
        if (state.uncoveredBranchTips() > 0) {
            return decision(
                    TreeConstructionSubrule.OWNED_BRANCH_ENVELOPE, audit,
                    "a terminal limb needs its owned evolved leaf envelope");
        }
        if (state.canopyProgress() < state.canopyShellTarget()) {
            return decision(
                    TreeConstructionSubrule.MINIMUM_CROWN_SHELL, audit,
                    "the connected crown shell must exist before branch expansion");
        }
        if (state.branchProgress() < state.branchTarget()) {
            return decision(
                    TreeConstructionSubrule.PARENT_LINKED_BRANCH_FRAME, audit,
                    "the exact parent-linked branch frame is incomplete");
        }
        if (state.canopyProgress() < state.canopyTarget()) {
            return decision(
                    TreeConstructionSubrule.CANOPY_STAGE_TARGET, audit,
                    "the replacement canopy is below its stage target");
        }
        if (state.transitionPending()
                && state.broadCleanupReady()
                && state.retiredCrownRemaining()) {
            return decision(
                    TreeConstructionSubrule.RETIRED_SOURCE_CROWN, audit,
                    "replacement structure is complete enough for one retired leaf");
        }

        // ## This independent pass protects finalization if a future routing
        // edit forgets a subrule. It redirects to the audit's first failure.
        if (!audit.passed()) {
            return decision(
                    audit.firstFailure(), audit,
                    "final formation audit recovered an unmet subrule: "
                            + audit.detail());
        }
        if (state.transitionPending()) {
            return decision(
                    TreeConstructionSubrule.TRANSITION_CONTRACT_COMPLETE,
                    audit,
                    "the final formation audit passed; transition may close");
        }
        if (state.detailRequested()) {
            return decision(
                    TreeConstructionSubrule.POST_STRUCTURE_DETAIL, audit,
                    "structural audit passed and a detail pass was requested");
        }
        return decision(
                TreeConstructionSubrule.STAGE_CONTRACT_COMPLETE, audit,
                "the final formation audit passed for the current stage");
    }

    private TreeConstructionDecision decision(
            TreeConstructionSubrule subrule,
            TreeConstructionAudit audit,
            String reason) {
        return new TreeConstructionDecision(
                subrule.phase(), subrule, subrule.attachment(), audit, reason);
    }
}