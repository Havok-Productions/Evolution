package org.evolution.features.treeevolution.constructor;

/**
 * One directional layer in the universal tree-construction pipeline.
 *
 * <p>Layers describe responsibility, while the rulebook describes exact
 * precedence. A mini-constructor may never route back to an earlier layer.</p>
 */
public enum TreeConstructionLayer {
    GATE,
    LEDGER_INTEGRITY,
    STRUCTURE_REPAIR,
    TRANSITION,
    SUPPORT,
    CANOPY_INTEGRITY,
    BRANCH_FRAME,
    CLEANUP,
    CANOPY_FILL,
    FINALIZATION,
    DETAIL,
    COMPLETE
}
