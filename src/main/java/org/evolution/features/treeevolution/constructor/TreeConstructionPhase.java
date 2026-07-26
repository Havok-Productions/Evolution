package org.evolution.features.treeevolution.constructor;

/**
 * One exclusive phase in the live tree-construction hierarchy.
 */
public enum TreeConstructionPhase {
    WAIT_FOR_OWNERSHIP,
    WAIT_FOR_SOURCE_SNAPSHOT,
    REPAIR,
    REPLACE_TRANSITION_BLOCKER,
    BUILD_SUPPORT,
    BUILD_CANOPY_SHELL,
    BUILD_BRANCH_FRAME,
    FILL_CANOPY,
    PRUNE_RETIRED_CROWN,
    FINALIZE_TRANSITION,
    BUILD_DETAILS,
    COMPLETE
}
