package org.evolution.features.treeevolution.constructor;

/**
 * Stable path identifiers for the ordered universal constructor rulebook.
 *
 * <p>The declaration order is not the runtime priority. The rulebook owns
 * priority explicitly, including the two intentional cleanup opportunities.
 * Both cleanup routes still attach to the same mini-constructor.</p>
 */
public enum TreeConstructionRuleId {
    OWNERSHIP_GATE,
    SOURCE_SNAPSHOT_GATE,
    OWNERSHIP_ROLE_RECONCILIATION,
    TARGET_ROLE_CONFLICT,
    DISCONNECTED_EVOLVED_STRUCTURE,
    DISCONNECTED_TARGET_REPAIR,
    INTERRUPTED_DAMAGE_REPAIR,
    READY_SOURCE_LEAF_BLOCKER,
    SUPPORT_STAGE_TARGET,
    COVER_EXPOSED_SUPPORT,
    UNPLANNED_BARE_TERMINAL_RETIREMENT,
    STALE_ENVELOPE_LEAF_RETIREMENT,
    OWNED_BRANCH_ENVELOPE,
    MINIMUM_CROWN_SHELL,
    PARENT_LINKED_BRANCH_FRAME,
    READY_OBSOLETE_EVOLVED_STRUCTURE,
    PACED_SOURCE_CROWN_RETIREMENT,
    CANOPY_STAGE_TARGET,
    FINAL_OBSOLETE_EVOLVED_STRUCTURE,
    FINAL_SOURCE_CROWN_RETIREMENT,
    TRANSITION_CONTRACT_COMPLETE,
    POST_STRUCTURE_DETAIL,
    STAGE_CONTRACT_COMPLETE
}
