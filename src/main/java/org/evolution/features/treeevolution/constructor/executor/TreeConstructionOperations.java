package org.evolution.features.treeevolution.constructor.executor;

/**
 * ## TREE CONSTRUCTOR OPERATIONS PORT
 *
 * <p>Executors may invoke only the operation assigned to their phase. The
 * live Evolution feature implements this port with Folia-safe world actions.</p>
 */
public interface TreeConstructionOperations {
    TreeConstructionResult waitForOwnership();

    TreeConstructionResult waitForSourceSnapshot();

    TreeConstructionResult reconcileOwnershipRole();

    TreeConstructionResult repairDisconnectedTarget();

    TreeConstructionResult repairInterruptedDamage();

    TreeConstructionResult replaceTransitionBlocker();

    TreeConstructionResult buildSupport();

    TreeConstructionResult coverExposedSupport();

    TreeConstructionResult retireUnplannedBareTerminal();

    TreeConstructionResult retireStaleEnvelopeLeaf();

    TreeConstructionResult repairBranchEnvelope();

    TreeConstructionResult buildMinimumCrownShell();

    TreeConstructionResult buildBranchFrame();

    TreeConstructionResult fillCanopy();

    TreeConstructionResult retireDisconnectedEvolvedStructure();

    TreeConstructionResult retireConflictingEvolvedTarget();

    TreeConstructionResult retireObsoleteEvolvedStructure();

    TreeConstructionResult retireSourceCrown();

    TreeConstructionResult finalizeTransition();

    TreeConstructionResult buildDetails();

    TreeConstructionResult complete();
}
