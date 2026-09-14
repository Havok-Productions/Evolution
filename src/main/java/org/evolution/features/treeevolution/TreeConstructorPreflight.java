package org.evolution.features.treeevolution;

import org.evolution.coreparts.EvolutionPlugin;

/**
 * ## CONSTRUCTOR PREFLIGHT COORDINATOR
 *
 * <p>Owns every persistent logical normalization that must occur before the
 * immutable constructor state is assembled. It never changes world blocks.
 * The returned progress flag prevents the focus scheduler from treating a
 * successful DNA/ledger transition as a stalled constructor pass.</p>
 */
final class TreeConstructorPreflight {
    private final EvolutionPlugin plugin;
    private final TreeEvolutionDiagnostics diagnostics;
    private final TreeDnaRepository repository;
    private final TreeMaturityService maturityService;
    private final TreeTransitionService transitionService;
    private final TreeReproductionService reproductionService;

    TreeConstructorPreflight(
            EvolutionPlugin plugin,
            TreeEvolutionDiagnostics diagnostics,
            TreeDnaRepository repository,
            TreeMaturityService maturityService,
            TreeTransitionService transitionService,
            TreeReproductionService reproductionService
    ) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
        this.repository = repository;
        this.maturityService = maturityService;
        this.transitionService = transitionService;
        this.reproductionService = reproductionService;
    }

    SourceResult prepareSource(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig config
    ) {
        boolean snapshotBefore = dna.hasOriginalShapeSnapshot();
        boolean sourceCaptureRequired =
                (!snapshotBefore && (dna.age() == 0 || dna.hasStageBurst()))
                        || (snapshotBefore
                                && !dna.originalShapeCaptureIsCurrent());
        if (sourceCaptureRequired
                && !maturityService.ensureOriginalShapeSnapshot(
                        candidate, dna, "before-world-change")) {
            return new SourceResult(false, false,
                    "source-snapshot-wait");
        }
        boolean snapshotCreated = !snapshotBefore
                && dna.hasOriginalShapeSnapshot();
        boolean stageNormalized = maturityService
                .reconcileStageWithSourceHeight(candidate, dna, config);
        boolean progressed = snapshotCreated || stageNormalized;
        trace(dna, "source", progressed,
                "snapshot-created=" + snapshotCreated
                        + " stage-normalized=" + stageNormalized);
        return new SourceResult(true, progressed, "source-normalized");
    }

    IntentResult selectIntent(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig config
    ) {
        TreeGrowthIntent before = dna.currentIntent();
        boolean burstPinnedIntent = false;
        if (dna.stageCleanupBurst() > 0) {
            dna.setCurrentIntent(TreeGrowthIntent.CLEANUP);
            burstPinnedIntent = true;
        } else {
            if (dna.currentIntent() == TreeGrowthIntent.CLEANUP) {
                dna.setCurrentIntent(TreeGrowthIntent.CANOPY);
            }
            if (dna.stageGrowthBurst() > 0) {
                dna.setCurrentIntent(
                        TreeGrowthIntentPolicy.stageBurstIntent(candidate, dna));
                burstPinnedIntent = true;
            } else {
                TreeGrowthIntent preferred =
                        TreeGrowthIntentPolicy.preferredIntent(
                                candidate, dna, config,
                                reproductionService.cooldownUntil(dna.key()));
                if (dna.blockedAttempts() >= 3
                        || dna.age() - dna.lastIntentChangeAge()
                                >= TreeGrowthIntentPolicy.intentSpan(
                                        dna, dna.currentIntent())) {
                    dna.setCurrentIntent(preferred);
                }
            }
        }
        if (!burstPinnedIntent && dna.damageCount() > 0
                && dna.currentIntent() != TreeGrowthIntent.REPAIR) {
            dna.setCurrentIntent(TreeGrowthIntent.REPAIR);
        }
        boolean progressed = before != dna.currentIntent();
        if (progressed) {
            repository.markDirty("constructor preflight intent " + dna.key());
        }
        trace(dna, "intent", progressed,
                "before=" + before + " after=" + dna.currentIntent());
        return new IntentResult(dna.currentIntent(), progressed);
    }

    IntentResult normalizeStaleDamage(
            TreeDna dna,
            TreeGrowthIntent requestedIntent,
            boolean targetVoxelsComplete,
            TreeEvolutionConfig config
    ) {
        if (requestedIntent != TreeGrowthIntent.REPAIR
                || dna.damageCount() <= 0 || !targetVoxelsComplete) {
            return new IntentResult(requestedIntent, false);
        }
        int staleDamage = dna.damageCount();
        dna.clearDamage();
        dna.setCurrentIntent(dna.hasOriginalShapeSnapshot()
                ? TreeGrowthIntent.CLEANUP : TreeGrowthIntent.CANOPY);
        repository.markDirty("stale-damage-cleared " + dna.key());
        diagnostics.recordReject(config, "stale-damage-cleared",
                dna.key() + " count=" + staleDamage
                        + " target-voxels-complete=true");
        plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                "state.stale-damage-cleared",
                "tree=" + dna.key() + " count=" + staleDamage
                        + " next=" + dna.currentIntent()
                        + " ## no target voxel remained for REPAIR; integrity subrules now own any exposed or uncovered structure");
        trace(dna, "stale-damage", true,
                "count=" + staleDamage + " next=" + dna.currentIntent());
        return new IntentResult(dna.currentIntent(), true);
    }

    boolean reconcileSourceLedger(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan plan,
            TreeEvolutionConfig config,
            TreeTransitionService.NeighborProtection neighborProtection
    ) {
        boolean progressed = transitionService.reconcileSourceLeafLedger(
                candidate, dna, plan, config, neighborProtection);
        trace(dna, "source-ledger", progressed,
                "unresolved=" + dna.unresolvedOriginalShapeLeafCount());
        return progressed;
    }

    private void trace(
            TreeDna dna,
            String step,
            boolean progressed,
            String detail
    ) {
        plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                "constructor.preflight." + step,
                "[PREFLIGHT][" + step + "] tree=" + dna.key()
                        + " progressed=" + progressed + " " + detail);
    }

    record SourceResult(boolean ready, boolean progressed, String detail) {
    }

    record IntentResult(TreeGrowthIntent intent, boolean progressed) {
    }
}
