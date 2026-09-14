package org.evolution.features.treeevolution.constructor;

/**
 * Stable bridge between one live constructor rule and its smoke-test stage.
 *
 * <p>## These identifiers belong to production code. Tests consume them;
 * tests do not invent parallel labels that can drift away from live routing.</p>
 */
public enum TreeConstructionSmokeTag {
    TREE_00_OWNERSHIP(
            TreeConstructionPhase.WAIT_FOR_OWNERSHIP,
            "rooted ownership gate"),
    TREE_05_SOURCE_SNAPSHOT(
            TreeConstructionPhase.WAIT_FOR_SOURCE_SNAPSHOT,
            "immutable pre-transition source capture"),
    TREE_09_OWNERSHIP_ROLE_RECONCILIATION(
            TreeConstructionPhase.REPAIR,
            "align one evolved receipt with its live wood or canopy role"),
    TREE_10_DISCONNECTED_EVOLVED_REPAIR(
            TreeConstructionPhase.PRUNE_RETIRED_CROWN,
            "retire detached plugin-owned structure"),
    TREE_11_DISCONNECTED_TARGET_REPAIR(
            TreeConstructionPhase.REPAIR,
            "restore a missing parent path to target wood"),
    TREE_12_DAMAGE_REPAIR(
            TreeConstructionPhase.REPAIR,
            "resume interrupted or player-damaged construction"),
    TREE_20_TRANSITION_BLOCKER(
            TreeConstructionPhase.REPLACE_TRANSITION_BLOCKER,
            "replace a ready source leaf with planned wood"),
    TREE_30_SUPPORT_TARGET(
            TreeConstructionPhase.BUILD_SUPPORT,
            "finish the stage trunk and support target"),
    TREE_40_EXPOSED_SUPPORT_COVER(
            TreeConstructionPhase.BUILD_CANOPY_SHELL,
            "cover exposed upper support wood"),
    TREE_40A_UNPLANNED_TERMINAL_RETIREMENT(
            TreeConstructionPhase.BUILD_CANOPY_SHELL,
            "retire one plugin-owned terminal outside the active branch plan"),
    TREE_40B_STALE_ENVELOPE_RETIREMENT(
            TreeConstructionPhase.BUILD_CANOPY_SHELL,
            "retire one stale plugin-owned branch-envelope leaf"),
    TREE_41_BRANCH_ENVELOPE(
            TreeConstructionPhase.BUILD_CANOPY_SHELL,
            "form an evolved canopy envelope around a branch tip"),
    TREE_42_MINIMUM_CROWN_SHELL(
            TreeConstructionPhase.BUILD_CANOPY_SHELL,
            "establish a connected minimum crown shell"),
    TREE_50_PARENT_LINKED_BRANCH_FRAME(
            TreeConstructionPhase.BUILD_BRANCH_FRAME,
            "build branches through their planned parent chain"),
    TREE_59_TARGET_ROLE_CONFLICT(
            TreeConstructionPhase.PRUNE_RETIRED_CROWN,
            "retire evolved wood or leaves that conflict with target role"),
    TREE_60_OBSOLETE_STRUCTURE_RETIREMENT(
            TreeConstructionPhase.PRUNE_RETIRED_CROWN,
            "retire plugin-owned blocks outside the active target"),
    TREE_61_SOURCE_CROWN_RETIREMENT(
            TreeConstructionPhase.PRUNE_RETIRED_CROWN,
            "pace removal of the immutable source crown"),
    TREE_70_CANOPY_TARGET(
            TreeConstructionPhase.FILL_CANOPY,
            "fill the complete stage canopy target"),
    TREE_80_TRANSITION_FINAL_AUDIT(
            TreeConstructionPhase.FINALIZE_TRANSITION,
            "close a transition only after the independent audit"),
    TREE_90_POST_STRUCTURE_DETAIL(
            TreeConstructionPhase.BUILD_DETAILS,
            "add non-structural detail after shape completion"),
    TREE_99_STAGE_COMPLETE(
            TreeConstructionPhase.COMPLETE,
            "confirm the current stage contract is complete");

    private final TreeConstructionPhase phase;
    private final String contract;

    TreeConstructionSmokeTag(
            TreeConstructionPhase phase,
            String contract
    ) {
        this.phase = phase;
        this.contract = contract;
    }

    public TreeConstructionPhase phase() {
        return phase;
    }

    public String contract() {
        return contract;
    }

    public String marker() {
        return "[SMOKE][" + name() + "]";
    }
}
