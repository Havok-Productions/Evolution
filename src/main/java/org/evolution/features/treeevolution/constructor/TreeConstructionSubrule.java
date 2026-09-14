package org.evolution.features.treeevolution.constructor;

/**
 * One ordered, traceable contract inside a top-level constructor phase.
 */
public enum TreeConstructionSubrule {
    ROOTED_TREE_OWNERSHIP(
            TreeConstructionPhase.WAIT_FOR_OWNERSHIP,
            TreeConstructionAttachment.OWNERSHIP_GATE,
            TreeConstructionSmokeTag.TREE_00_OWNERSHIP),
    IMMUTABLE_SOURCE_SNAPSHOT(
            TreeConstructionPhase.WAIT_FOR_SOURCE_SNAPSHOT,
            TreeConstructionAttachment.SOURCE_SNAPSHOT,
            TreeConstructionSmokeTag.TREE_05_SOURCE_SNAPSHOT),
    OWNERSHIP_ROLE_RECONCILIATION(
            TreeConstructionPhase.REPAIR,
            TreeConstructionAttachment.DAMAGE_REPAIR,
            TreeConstructionSmokeTag
                    .TREE_09_OWNERSHIP_ROLE_RECONCILIATION),
    INTERRUPTED_DAMAGE_REPAIR(
            TreeConstructionPhase.REPAIR,
            TreeConstructionAttachment.DAMAGE_REPAIR,
            TreeConstructionSmokeTag.TREE_12_DAMAGE_REPAIR),
    READY_SOURCE_LEAF_BLOCKER(
            TreeConstructionPhase.REPLACE_TRANSITION_BLOCKER,
            TreeConstructionAttachment.TRANSITION_RECONCILER,
            TreeConstructionSmokeTag.TREE_20_TRANSITION_BLOCKER),
    DISCONNECTED_EVOLVED_STRUCTURE(
            TreeConstructionPhase.PRUNE_RETIRED_CROWN,
            TreeConstructionAttachment.TRANSITION_RECONCILER,
            TreeConstructionSmokeTag
                    .TREE_10_DISCONNECTED_EVOLVED_REPAIR),
    DISCONNECTED_TARGET_REPAIR(
            TreeConstructionPhase.REPAIR,
            TreeConstructionAttachment.DAMAGE_REPAIR,
            TreeConstructionSmokeTag.TREE_11_DISCONNECTED_TARGET_REPAIR),
    SUPPORT_STAGE_TARGET(
            TreeConstructionPhase.BUILD_SUPPORT,
            TreeConstructionAttachment.TRUNK_PLANNER,
            TreeConstructionSmokeTag.TREE_30_SUPPORT_TARGET),
    COVER_EXPOSED_SUPPORT(
            TreeConstructionPhase.BUILD_CANOPY_SHELL,
            TreeConstructionAttachment.CANOPY_PLANNER,
            TreeConstructionSmokeTag.TREE_40_EXPOSED_SUPPORT_COVER),
    UNPLANNED_BARE_TERMINAL_RETIREMENT(
            TreeConstructionPhase.BUILD_CANOPY_SHELL,
            TreeConstructionAttachment.CANOPY_PLANNER,
            TreeConstructionSmokeTag
                    .TREE_40A_UNPLANNED_TERMINAL_RETIREMENT),
    STALE_ENVELOPE_LEAF_RETIREMENT(
            TreeConstructionPhase.BUILD_CANOPY_SHELL,
            TreeConstructionAttachment.CANOPY_PLANNER,
            TreeConstructionSmokeTag
                    .TREE_40B_STALE_ENVELOPE_RETIREMENT),
    OWNED_BRANCH_ENVELOPE(
            TreeConstructionPhase.BUILD_CANOPY_SHELL,
            TreeConstructionAttachment.CANOPY_PLANNER,
            TreeConstructionSmokeTag.TREE_41_BRANCH_ENVELOPE),
    MINIMUM_CROWN_SHELL(
            TreeConstructionPhase.BUILD_CANOPY_SHELL,
            TreeConstructionAttachment.CANOPY_PLANNER,
            TreeConstructionSmokeTag.TREE_42_MINIMUM_CROWN_SHELL),
    PARENT_LINKED_BRANCH_FRAME(
            TreeConstructionPhase.BUILD_BRANCH_FRAME,
            TreeConstructionAttachment.BRANCH_PLANNER,
            TreeConstructionSmokeTag
                    .TREE_50_PARENT_LINKED_BRANCH_FRAME),
    CONFLICTING_EVOLVED_TARGET(
            TreeConstructionPhase.PRUNE_RETIRED_CROWN,
            TreeConstructionAttachment.TRANSITION_RECONCILER,
            TreeConstructionSmokeTag.TREE_59_TARGET_ROLE_CONFLICT),
    CANOPY_STAGE_TARGET(
            TreeConstructionPhase.FILL_CANOPY,
            TreeConstructionAttachment.CANOPY_PLANNER,
            TreeConstructionSmokeTag.TREE_70_CANOPY_TARGET),
    OBSOLETE_EVOLVED_STRUCTURE(
            TreeConstructionPhase.PRUNE_RETIRED_CROWN,
            TreeConstructionAttachment.TRANSITION_RECONCILER,
            TreeConstructionSmokeTag
                    .TREE_60_OBSOLETE_STRUCTURE_RETIREMENT),
    RETIRED_SOURCE_CROWN(
            TreeConstructionPhase.PRUNE_RETIRED_CROWN,
            TreeConstructionAttachment.TRANSITION_RECONCILER,
            TreeConstructionSmokeTag.TREE_61_SOURCE_CROWN_RETIREMENT),
    TRANSITION_CONTRACT_COMPLETE(
            TreeConstructionPhase.FINALIZE_TRANSITION,
            TreeConstructionAttachment.STAGE_FINALIZER,
            TreeConstructionSmokeTag.TREE_80_TRANSITION_FINAL_AUDIT),
    POST_STRUCTURE_DETAIL(
            TreeConstructionPhase.BUILD_DETAILS,
            TreeConstructionAttachment.DETAIL_PLANNERS,
            TreeConstructionSmokeTag.TREE_90_POST_STRUCTURE_DETAIL),
    STAGE_CONTRACT_COMPLETE(
            TreeConstructionPhase.COMPLETE,
            TreeConstructionAttachment.NONE,
            TreeConstructionSmokeTag.TREE_99_STAGE_COMPLETE);

    private final TreeConstructionPhase phase;
    private final TreeConstructionAttachment attachment;
    private final TreeConstructionSmokeTag smokeTag;

    TreeConstructionSubrule(
            TreeConstructionPhase phase,
            TreeConstructionAttachment attachment,
            TreeConstructionSmokeTag smokeTag) {
        this.phase = phase;
        this.attachment = attachment;
        this.smokeTag = smokeTag;
        if (smokeTag.phase() != phase) {
            throw new IllegalArgumentException(
                    "Smoke tag " + smokeTag + " belongs to "
                            + smokeTag.phase() + ", not " + phase);
        }
    }

    public TreeConstructionPhase phase() {
        return phase;
    }

    public TreeConstructionAttachment attachment() {
        return attachment;
    }

    public TreeConstructionSmokeTag smokeTag() {
        return smokeTag;
    }

}
