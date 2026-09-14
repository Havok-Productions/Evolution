package org.evolution.features.treeevolution;

import org.evolution.features.treeevolution.constructor.TreeConstructionState;

/**
 * ## One immutable constructor-decision snapshot.
 *
 * <p>Production and replay must describe a tree through this exact contract.
 * World scanning stays outside the hierarchy, while every fact used to choose
 * the next subrule is frozen before routing begins.</p>
 */
record TreeConstructionSnapshot(
        String treeKey,
        TreeConstructionState state
) {
    TreeConstructionSnapshot {
        if (treeKey == null || treeKey.isBlank()) {
            throw new IllegalArgumentException(
                    "constructor snapshot requires a tree key");
        }
        if (state == null) {
            throw new IllegalArgumentException(
                    "constructor snapshot requires hierarchy state");
        }
    }

    static TreeConstructionSnapshot capture(
            TreeCandidate candidate,
            TreeDna dna,
            TreeGrowthQueuePolicy.Completion completion,
            TreeGrowthQueuePolicy.Budget budget,
            TreeGrowthIntent requestedIntent,
            Facts facts
    ) {
        boolean stageComplete = TreeFocusPolicy.stageStructureComplete(
                completion, budget, facts.exposedUpperLogs(),
                facts.uncoveredBranchProblems());
        boolean transitionPending = TreeFocusPolicy.transitionPending(
                dna.stageCleanupBurst(), dna.stageGrowthBurst(),
                stageComplete, dna.hasOriginalShapeSnapshot());
        boolean completeOwnershipRequired =
                TreeFocusPolicy.completeOwnershipRequired(
                        dna.stageCleanupBurst(), dna.damageCount(),
                        requestedIntent == TreeGrowthIntent.REPAIR,
                        stageComplete, dna.hasOriginalShapeSnapshot(),
                        dna.unresolvedOriginalShapeLeafCount());
        TreeConstructionState state = new TreeConstructionState(
                !completeOwnershipRequired || candidate.ownershipComplete(),
                dna.hasOriginalShapeSnapshot(),
                transitionPending,
                facts.ownershipRoleReconciliationRemaining(),
                dna.damageCount() > 0
                        || requestedIntent == TreeGrowthIntent.REPAIR,
                facts.transitionBlockerReady(),
                facts.disconnectedEvolvedStructureRemaining(),
                facts.disconnectedTargetRepairRemaining(),
                facts.conflictingEvolvedTargetRemaining(),
                facts.broadCleanupReady(),
                facts.obsoleteEvolvedStructureRemaining(),
                facts.retiredCrownRemaining(),
                facts.sourceCrownResolved(),
                facts.exposedUpperLogs(),
                facts.uncoveredBranchProblems(),
                facts.unplannedBareTerminals(),
                facts.staleEnvelopeLeaves(),
                facts.uncoveredPlannedBranchEnvelopes(),
                completion.trunkPercent(),
                completion.branchPercent(),
                completion.canopyPercent(),
                budget.trunkPercent(),
                budget.branchPercent(),
                canopyShellTarget(dna),
                budget.canopyPercent(),
                requestedIntent == TreeGrowthIntent.DETAIL
                        || requestedIntent == TreeGrowthIntent.SEEDLING);
        return new TreeConstructionSnapshot(dna.key(), state);
    }

    private static double canopyShellTarget(TreeDna dna) {
        return switch (dna.maturityStage()) {
            case SMALL -> 0.18D;
            case MEDIUM -> 0.24D;
            case MATURE -> 0.30D;
            case ANCIENT -> 0.32D;
        };
    }

    /**
     * ## Fully explicit observations. There is deliberately no reduced
     * compatibility constructor because omitted facts previously made replay
     * choose a different subrule from production.
     */
    record Facts(
            int exposedUpperLogs,
            int uncoveredBranchProblems,
            int unplannedBareTerminals,
            int staleEnvelopeLeaves,
            int uncoveredPlannedBranchEnvelopes,
            boolean transitionBlockerReady,
            boolean ownershipRoleReconciliationRemaining,
            boolean disconnectedEvolvedStructureRemaining,
            boolean disconnectedTargetRepairRemaining,
            boolean conflictingEvolvedTargetRemaining,
            boolean broadCleanupReady,
            boolean obsoleteEvolvedStructureRemaining,
            boolean retiredCrownRemaining,
            boolean sourceCrownResolved
    ) {
        Facts {
            exposedUpperLogs = Math.max(0, exposedUpperLogs);
            uncoveredBranchProblems = Math.max(
                    0, uncoveredBranchProblems);
            unplannedBareTerminals = Math.max(
                    0, unplannedBareTerminals);
            staleEnvelopeLeaves = Math.max(0, staleEnvelopeLeaves);
            uncoveredPlannedBranchEnvelopes = Math.max(
                    0, uncoveredPlannedBranchEnvelopes);
            int classified = unplannedBareTerminals
                    + staleEnvelopeLeaves
                    + uncoveredPlannedBranchEnvelopes;
            if (classified != uncoveredBranchProblems) {
                throw new IllegalArgumentException(
                        "constructor branch facts disagree: total="
                                + uncoveredBranchProblems
                                + " classified=" + classified);
            }
        }
    }
}
