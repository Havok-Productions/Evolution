package org.evolution.features.treeevolution.constructor;

/**
 * Rechecks the completed live tree contract independently of action routing.
 */
public final class TreeConstructionFinalAudit {
    public TreeConstructionAudit inspect(TreeConstructionState state) {
        if (!state.ownershipComplete()) {
            return blocked(TreeConstructionSubrule.ROOTED_TREE_OWNERSHIP,
                    "rooted-tree ownership is incomplete");
        }
        if (state.transitionPending() && !state.sourceSnapshotReady()) {
            return blocked(TreeConstructionSubrule.IMMUTABLE_SOURCE_SNAPSHOT,
                    "transition source snapshot is missing");
        }
        if (state.damageRepairRequested()) {
            return blocked(TreeConstructionSubrule.INTERRUPTED_DAMAGE_REPAIR,
                    "damage repair remains pending");
        }
        if (state.transitionPending() && state.transitionBlockerReady()) {
            return blocked(TreeConstructionSubrule.READY_SOURCE_LEAF_BLOCKER,
                    "a ready source-leaf blocker remains");
        }
        if (state.trunkProgress() < state.trunkTarget()) {
            return blocked(TreeConstructionSubrule.SUPPORT_STAGE_TARGET,
                    "support structure is below target");
        }
        if (state.exposedUpperLogs() > 0) {
            return blocked(TreeConstructionSubrule.COVER_EXPOSED_SUPPORT,
                    "upper support wood remains exposed");
        }
        if (state.uncoveredBranchTips() > 0) {
            return blocked(TreeConstructionSubrule.OWNED_BRANCH_ENVELOPE,
                    "a terminal branch lacks its owned evolved leaf envelope");
        }
        if (state.canopyProgress() < state.canopyShellTarget()) {
            return blocked(TreeConstructionSubrule.MINIMUM_CROWN_SHELL,
                    "minimum connected crown shell is incomplete");
        }
        if (state.branchProgress() < state.branchTarget()) {
            return blocked(TreeConstructionSubrule.PARENT_LINKED_BRANCH_FRAME,
                    "parent-linked branch frame is incomplete");
        }
        if (state.canopyProgress() < state.canopyTarget()) {
            return blocked(TreeConstructionSubrule.CANOPY_STAGE_TARGET,
                    "replacement canopy is below target");
        }
        if (state.transitionPending()
                && state.broadCleanupReady()
                && state.retiredCrownRemaining()) {
            return blocked(TreeConstructionSubrule.RETIRED_SOURCE_CROWN,
                    "retired source-crown leaves remain");
        }
        // ## Details are optional after structure. A transition or completed
        // stage reaches this point only after every structural subrule passes.
        return TreeConstructionAudit.passed(
                "ownership, support, branch, canopy, and transition contracts pass");
    }

    private TreeConstructionAudit blocked(
            TreeConstructionSubrule subrule, String detail) {
        return TreeConstructionAudit.blocked(subrule, detail);
    }
}