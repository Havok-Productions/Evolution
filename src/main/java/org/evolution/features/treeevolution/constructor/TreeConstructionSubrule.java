package org.evolution.features.treeevolution.constructor;

/**
 * One ordered, traceable contract inside a top-level constructor phase.
 */
public enum TreeConstructionSubrule {
    ROOTED_TREE_OWNERSHIP(
            TreeConstructionPhase.WAIT_FOR_OWNERSHIP,
            TreeConstructionAttachment.OWNERSHIP_GATE),
    IMMUTABLE_SOURCE_SNAPSHOT(
            TreeConstructionPhase.WAIT_FOR_SOURCE_SNAPSHOT,
            TreeConstructionAttachment.SOURCE_SNAPSHOT),
    INTERRUPTED_DAMAGE_REPAIR(
            TreeConstructionPhase.REPAIR,
            TreeConstructionAttachment.DAMAGE_REPAIR),
    READY_SOURCE_LEAF_BLOCKER(
            TreeConstructionPhase.REPLACE_TRANSITION_BLOCKER,
            TreeConstructionAttachment.TRANSITION_RECONCILER),
    SUPPORT_STAGE_TARGET(
            TreeConstructionPhase.BUILD_SUPPORT,
            TreeConstructionAttachment.TRUNK_PLANNER),
    COVER_EXPOSED_SUPPORT(
            TreeConstructionPhase.BUILD_CANOPY_SHELL,
            TreeConstructionAttachment.CANOPY_PLANNER),
    OWNED_BRANCH_ENVELOPE(
            TreeConstructionPhase.BUILD_CANOPY_SHELL,
            TreeConstructionAttachment.CANOPY_PLANNER),
    MINIMUM_CROWN_SHELL(
            TreeConstructionPhase.BUILD_CANOPY_SHELL,
            TreeConstructionAttachment.CANOPY_PLANNER),
    PARENT_LINKED_BRANCH_FRAME(
            TreeConstructionPhase.BUILD_BRANCH_FRAME,
            TreeConstructionAttachment.BRANCH_PLANNER),
    CANOPY_STAGE_TARGET(
            TreeConstructionPhase.FILL_CANOPY,
            TreeConstructionAttachment.CANOPY_PLANNER),
    RETIRED_SOURCE_CROWN(
            TreeConstructionPhase.PRUNE_RETIRED_CROWN,
            TreeConstructionAttachment.TRANSITION_RECONCILER),
    TRANSITION_CONTRACT_COMPLETE(
            TreeConstructionPhase.FINALIZE_TRANSITION,
            TreeConstructionAttachment.STAGE_FINALIZER),
    POST_STRUCTURE_DETAIL(
            TreeConstructionPhase.BUILD_DETAILS,
            TreeConstructionAttachment.DETAIL_PLANNERS),
    STAGE_CONTRACT_COMPLETE(
            TreeConstructionPhase.COMPLETE,
            TreeConstructionAttachment.NONE);

    private final TreeConstructionPhase phase;
    private final TreeConstructionAttachment attachment;

    TreeConstructionSubrule(
            TreeConstructionPhase phase,
            TreeConstructionAttachment attachment) {
        this.phase = phase;
        this.attachment = attachment;
    }

    public TreeConstructionPhase phase() {
        return phase;
    }

    public TreeConstructionAttachment attachment() {
        return attachment;
    }

    public static TreeConstructionSubrule primaryFor(
            TreeConstructionPhase phase) {
        for (TreeConstructionSubrule subrule : values()) {
            if (subrule.phase == phase) {
                return subrule;
            }
        }
        throw new IllegalArgumentException(
                "No constructor subrule belongs to phase " + phase);
    }
}