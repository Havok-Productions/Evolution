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

    TreeConstructionResult repair();

    TreeConstructionResult replaceTransitionBlocker();

    TreeConstructionResult buildSupport();

    TreeConstructionResult buildCanopyShell();

    TreeConstructionResult buildBranchFrame();

    TreeConstructionResult fillCanopy();

    TreeConstructionResult pruneRetiredCrown();

    TreeConstructionResult finalizeTransition();

    TreeConstructionResult buildDetails();

    TreeConstructionResult complete();
}
