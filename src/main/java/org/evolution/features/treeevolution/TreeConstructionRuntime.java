package org.evolution.features.treeevolution;

import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.evolution.coreparts.EvolutionPlugin;
import org.evolution.coreparts.ResourceReporter.ReportSample;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionOperations;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionResult;

/**
 * ## Executes one constructor hierarchy decision against its attached services.
 *
 * <p>This adapter never invents a phase. {@link TreeConstructorCore} selects one
 * exclusive phase/subrule, and the matching operation delegates to transition,
 * placement, maturity, reproduction, or audit ownership.</p>
 */
final class TreeConstructionRuntime {
    private final EvolutionPlugin plugin;
    private final TreeEvolutionDiagnostics diagnostics;
    private final TreeDnaRepository repository;
    private final TreeCandidateDiscoveryService candidateDiscovery;
    private final TreePlanAuditService planAudit;
    private final TreeMaturityService maturityService;
    private final TreePlacementService placementService;
    private final TreeCanopyRepairService canopyRepairService;
    private final TreeTransitionService transitionService;
    private final TreeReproductionService reproductionService;
    private final BiPredicate<Location, TreeEvolutionConfig> canWorkAt;
    private final AtomicLong changedBlocks;
    private final TreeConstructorCore constructorCore = new TreeConstructorCore();

    TreeConstructionRuntime(
            EvolutionPlugin plugin,
            TreeEvolutionDiagnostics diagnostics,
            TreeDnaRepository repository,
            TreeCandidateDiscoveryService candidateDiscovery,
            TreePlanAuditService planAudit,
            TreeMaturityService maturityService,
            TreePlacementService placementService,
            TreeCanopyRepairService canopyRepairService,
            TreeTransitionService transitionService,
            TreeReproductionService reproductionService,
            BiPredicate<Location, TreeEvolutionConfig> canWorkAt,
            AtomicLong changedBlocks
    ) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
        this.repository = repository;
        this.candidateDiscovery = candidateDiscovery;
        this.planAudit = planAudit;
        this.maturityService = maturityService;
        this.placementService = placementService;
        this.canopyRepairService = canopyRepairService;
        this.transitionService = transitionService;
        this.reproductionService = reproductionService;
        this.canWorkAt = canWorkAt;
        this.changedBlocks = changedBlocks;
    }
    boolean evolve(TreeCandidate candidate, TreeDna dna, TreeEvolutionConfig currentConfig) {
        try (ReportSample sample = plugin.resourceReporter().begin("tree-evolution", "action.evolve")) {
            if (!dna.stumpPresent()) {
                diagnostics.recordReject(currentConfig, "missing-stump", dna.key());
                sample.detail("missing-stump " + dna.key());
                return false;
            }
            if (candidate.baseBlock().getType() != dna.species().logMaterial()) {
                dna.setStumpPresent(false);
                repository.markDirty("base-not-log");
                repository.save(currentConfig);
                diagnostics.recordReject(currentConfig, "base-not-log", format(candidate.baseBlock()));
                sample.detail("base-not-log " + dna.key());
                return false;
            }
            if (!canWorkAt.test(candidate.baseLocation(), currentConfig)) {
                diagnostics.recordReject(currentConfig, "work-gate", format(candidate.baseLocation()));
                sample.detail("work-gate " + dna.key());
                return false;
            }
            boolean sourceCaptureRequired =
                    (!dna.hasOriginalShapeSnapshot()
                            && (dna.age() == 0 || dna.hasStageBurst()))
                    || (dna.hasOriginalShapeSnapshot()
                            && !dna.originalShapeCaptureIsCurrent());
            if (sourceCaptureRequired
                    && !maturityService.ensureOriginalShapeSnapshot(candidate, dna,
                            "before-world-change")) {
                sample.detail("original-shape-wait " + dna.key());
                return false;
            }
            maturityService.reconcileStageWithSourceHeight(candidate, dna, currentConfig);

        // ## TREE CONSTRUCTOR CORE
        // The feature gathers one immutable live snapshot, the hierarchy selects one
        // attached subsystem, and only that subsystem may change the tree this action.
        Biome biome = candidate.baseBlock().getBiome();
        CachedTreePlan cachedPlan = planAudit.cachedPlan(
                dna, biome, currentConfig.rootsEnabled());
        TreeGrowthIntent requestedIntent = refreshIntent(
                candidate, dna, currentConfig);
        diagnostics.recordPlan(currentConfig, dna, cachedPlan.plan(),
                cachedPlan.orderedBlocks(), candidate.world(), false);

        TreeGrowthQueuePolicy.Completion constructorCompletion =
                planAudit.stageCompletion(candidate, dna, cachedPlan);
        TreeGrowthQueuePolicy.Budget constructorBudget =
                TreeGrowthQueuePolicy.stageBudget(dna);
        int constructorExposedLogs = planAudit.exposedUpperLogCount(
                candidate, dna, cachedPlan.blocksByKey());
        BranchTipCoverage constructorBranchTips = planAudit.branchTipCoverage(
                candidate, dna, cachedPlan);
        boolean constructorStageComplete = TreeFocusPolicy.stageStructureComplete(
                constructorCompletion, constructorBudget,
                constructorExposedLogs, constructorBranchTips.uncoveredTips());
        boolean constructorNeedsCompleteOwnership =
                TreeFocusPolicy.completeOwnershipRequired(
                        dna.stageCleanupBurst(),
                        dna.damageCount(),
                        requestedIntent == TreeGrowthIntent.REPAIR,
                        constructorStageComplete,
                        dna.hasOriginalShapeSnapshot(),
                        dna.unresolvedOriginalShapeLeafCount());
        if (constructorNeedsCompleteOwnership && !candidate.ownershipComplete()) {
            candidate = candidateDiscovery.build(candidate.baseBlock(), true)
                    .orElse(candidate);
            constructorCompletion = planAudit.stageCompletion(
                    candidate, dna, cachedPlan);
            constructorExposedLogs = planAudit.exposedUpperLogCount(
                    candidate, dna, cachedPlan.blocksByKey());
            constructorBranchTips = planAudit.branchTipCoverage(
                    candidate, dna, cachedPlan);
            constructorStageComplete = TreeFocusPolicy.stageStructureComplete(
                    constructorCompletion, constructorBudget,
                    constructorExposedLogs, constructorBranchTips.uncoveredTips());
        }
        transitionService.reconcileSourceLeafLedger(
                candidate, dna, cachedPlan, currentConfig);
        boolean transitionPending = TreeFocusPolicy.transitionPending(
                dna.stageCleanupBurst(), dna.stageGrowthBurst(),
                constructorStageComplete, dna.hasOriginalShapeSnapshot());
        boolean transitionCleanupRequired = transitionPending
                && dna.unresolvedOriginalShapeLeafCount() > 0;
        boolean broadCleanupReady =
                TreeCanopyTransitionPolicy.allowsBroadCleanup(
                        dna, constructorCompletion.canopyPercent())
                && constructorCompletion.trunkPercent()
                        >= constructorBudget.trunkPercent()
                && constructorCompletion.branchPercent()
                        >= constructorBudget.branchPercent()
                && constructorCompletion.canopyPercent()
                        >= constructorBudget.canopyPercent();
        Optional<PlannedTarget> transitionBlocker =
                transitionCleanupRequired && candidate.ownershipComplete()
                        ? transitionService.readyTransitionBlocker(
                                candidate, dna, cachedPlan, currentConfig)
                        : Optional.empty();
        List<Block> retiredCrown =
                transitionCleanupRequired
                                && candidate.ownershipComplete()
                                && broadCleanupReady
                        ? transitionService.findRetiredCanopyLeaves(
                                candidate, dna, cachedPlan,
                                planAudit.pruneBatchSize(dna), currentConfig)
                        : List.of();
        TreeConstructionDecision construction = constructorCore.decide(
                candidate, dna, constructorCompletion, constructorBudget,
                requestedIntent, constructorExposedLogs,
                constructorBranchTips.uncoveredTips(),
                transitionBlocker.isPresent(), broadCleanupReady,
                dna.unresolvedOriginalShapeLeafCount() > 0);
        plugin.pathDebug().traceSampled(
                plugin, "tree-evolution",
                construction.finalAudit().passed()
                        ? "audit.constructor-final-contract-pass"
                        : "audit.constructor-final-contract-blocked",
                construction.marker()
                        + " tree=" + dna.key()
                        + " " + construction.finalAudit().marker()
                        + " first-failure="
                        + construction.finalAudit().firstFailure()
                        + " detail=" + construction.finalAudit().detail()
                        + " ## final audit independently rechecks every smaller constructor contract");
        String executorName = constructorCore.executorName(construction);
        diagnostics.recordConstructorDecision(
                currentConfig, dna, construction,
                constructorCompletion, constructorBudget, executorName);
        plugin.pathDebug().traceSampled(
                plugin, "tree-evolution", "constructor.phase",
                construction.marker() + " tree=" + dna.key()
                        + " reason=" + construction.reason());

        final TreeCandidate constructionCandidate = candidate;
        final TreeGrowthIntent constructionRequestedIntent = requestedIntent;
        TreeConstructionOperations operations = new TreeConstructionOperations() {
            @Override
            public TreeConstructionResult waitForOwnership() {
                return constructorWaitResult(
                        dna, construction, currentConfig, "ownership");
            }

            @Override
            public TreeConstructionResult waitForSourceSnapshot() {
                return constructorWaitResult(
                        dna, construction, currentConfig, "source-snapshot");
            }

            @Override
            public TreeConstructionResult repair() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.REPAIR,
                        constructionRequestedIntent);
            }

            @Override
            public TreeConstructionResult replaceTransitionBlocker() {
                return executeTransitionBlockerPhase(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, transitionBlocker);
            }

            @Override
            public TreeConstructionResult buildSupport() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.HEIGHT,
                        constructionRequestedIntent);
            }

            @Override
            public TreeConstructionResult buildCanopyShell() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.CANOPY,
                        constructionRequestedIntent);
            }

            @Override
            public TreeConstructionResult buildBranchFrame() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.BRANCH,
                        constructionRequestedIntent);
            }

            @Override
            public TreeConstructionResult fillCanopy() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.CANOPY,
                        constructionRequestedIntent);
            }

            @Override
            public TreeConstructionResult pruneRetiredCrown() {
                return executeRetiredCrownPhase(
                        dna, currentConfig, construction,
                        retiredCrown);
            }

            @Override
            public TreeConstructionResult finalizeTransition() {
                return executeTransitionFinalizer(
                        constructionCandidate, dna, currentConfig,
                        construction);
            }

            @Override
            public TreeConstructionResult buildDetails() {
                TreeGrowthIntent detailIntent =
                        constructionRequestedIntent
                                == TreeGrowthIntent.SEEDLING
                        ? TreeGrowthIntent.SEEDLING
                        : TreeGrowthIntent.DETAIL;
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, detailIntent,
                        constructionRequestedIntent);
            }

            @Override
            public TreeConstructionResult complete() {
                return executeConstructorComplete(
                        constructionCandidate, dna, currentConfig,
                        construction);
            }
        };

        TreeConstructionResult result;
        String resourceTask = "constructor."
                + construction.phase().name().toLowerCase(
                        java.util.Locale.ROOT);
        try (ReportSample executorSample = plugin.resourceReporter().begin(
                "tree-evolution", resourceTask)) {
            result = constructorCore.execute(construction, operations);
            executorSample.changedUnits(result.changedUnits())
                    .detail(executorName + " " + result.detail());
        }
        plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                "constructor.executor",
                construction.marker() + " executor=" + executorName
                        + " changed=" + result.worldChanged()
                        + " units=" + result.changedUnits()
                        + " detail=" + result.detail());
        sample.changedUnits(result.changedUnits()).detail(result.detail());
        return result.worldChanged();
        }
    }

    private TreeConstructionResult constructorWaitResult(
            TreeDna dna,
            TreeConstructionDecision construction,
            TreeEvolutionConfig currentConfig,
            String gate
    ) {
        diagnostics.recordReject(currentConfig,
                "constructor-wait", construction.marker()
                        + " tree=" + dna.key()
                        + " gate=" + gate
                        + " reason=" + construction.reason());
        return TreeConstructionResult.idle(
                "constructor.wait-" + gate + " " + dna.key());
    }

    private TreeConstructionResult executeTransitionBlockerPhase(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig,
            Optional<PlannedTarget> transitionBlocker
    ) {
        if (transitionBlocker.isPresent()
                && transitionService.replaceTransitionBlocker(
                        candidate, dna, cachedPlan, currentConfig,
                        transitionBlocker.get())) {
            return TreeConstructionResult.changed(
                    1, "constructor.atomic-blocker " + dna.key());
        }
        diagnostics.recordReject(currentConfig,
                "constructor-blocker-lost", dna.key());
        return TreeConstructionResult.idle(
                "constructor.blocker-lost " + dna.key());
    }

    private TreeConstructionResult executeRetiredCrownPhase(
            TreeDna dna,
            TreeEvolutionConfig currentConfig,
            TreeConstructionDecision construction,
            List<Block> retiredCrown
    ) {
        if (retiredCrown.isEmpty()) {
            diagnostics.recordReject(currentConfig,
                    "constructor-retired-crown-empty",
                    dna.key() + " unresolved-source-leaves="
                            + dna.unresolvedOriginalShapeLeafCount());
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "gate.source-leaf-unresolved",
                    "tree=" + dna.key()
                            + " unresolved="
                            + dna.unresolvedOriginalShapeLeafCount()
                            + " ## the source snapshot remains open; no unresolved leaf may be forgotten by transition finalization");
            return TreeConstructionResult.idle(
                    "constructor.retired-crown-empty " + dna.key());
        }
        Block leaf = retiredCrown.get(0);
        String retiredLeafKey = keyFor(leaf);
        if (!dna.markOriginalShapeLeafRetired(retiredLeafKey)) {
            diagnostics.recordReject(currentConfig,
                    "constructor-retired-leaf-already-processed",
                    dna.key() + " leaf=" + format(leaf));
            return TreeConstructionResult.idle(
                    "constructor.retired-leaf-already-processed "
                            + dna.key());
        }
        leaf.setType(Material.AIR, false);
        dna.markPrunedNow();
        repository.markDirty("retired source leaf " + retiredLeafKey);
                planAudit.invalidateLiveAnalysis(dna.key());
        changedBlocks.incrementAndGet();
        diagnostics.recordPrunedBatch(
                plugin, currentConfig, List.of(leaf), dna);
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "constructor.prune-retired-crown",
                construction.marker() + " tree=" + dna.key()
                        + " removed=" + format(leaf)
                        + " ## broad pruning runs only after the replacement structure reaches every stage target");
        return TreeConstructionResult.changed(
                1, "constructor.prune-retired-crown " + dna.key());
    }

    private TreeConstructionResult executeTransitionFinalizer(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig currentConfig,
            TreeConstructionDecision construction
    ) {
        int originalBlocks = dna.originalShapeBlockCount();
        dna.completeStageCleanup();
        TreeGrowthIntent nextIntent = dna.stageGrowthBurst() > 0
                ? TreeGrowthIntentPolicy.stageBurstIntent(candidate, dna)
                : TreeGrowthIntent.CANOPY;
        dna.setCurrentIntent(nextIntent);
        repository.markDirty("constructor transition cleanup complete "
                + dna.key());
        repository.save(currentConfig);
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "constructor.transition-finalize",
                construction.marker() + " tree=" + dna.key()
                        + " source-blocks=" + originalBlocks
                        + " next=" + nextIntent);
        return TreeConstructionResult.idle(
                "constructor.transition-finalize " + dna.key());
    }

    private TreeConstructionResult executeConstructorComplete(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig currentConfig,
            TreeConstructionDecision construction
    ) {
        TreeMaturityStage before = dna.maturityStage();
        maturityService.updateMaturity(candidate, dna, currentConfig);
        if (dna.maturityStage() != before) {
            repository.markDirty("constructor maturity handoff " + dna.key());
            repository.save(currentConfig);
        }
        plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                "constructor.complete",
                construction.marker() + " tree=" + dna.key()
                        + " stage=" + before + "->" + dna.maturityStage()
                        + " ## no structural planner runs after the current stage contract is complete");
        return TreeConstructionResult.idle(
                "constructor.complete " + dna.key());
    }

    private TreeConstructionResult executeIntentConstruction(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig,
            TreeGrowthIntent intent,
            TreeGrowthIntent requestedIntent
    ) {
        if (intent != dna.currentIntent()) {
            dna.setCurrentIntent(intent);
        }
        diagnostics.recordIntent(currentConfig, dna, intent,
                "requested=" + requestedIntent
                        + " cursor=" + dna.planCursor()
                        + " blocked=" + dna.blockedAttempts());
        Optional<Block> seedlingSpot = intent == TreeGrowthIntent.SEEDLING
                ? reproductionService.findSpot(candidate.world(), dna, currentConfig)
                : Optional.empty();
        if (seedlingSpot.isPresent()) {
            reproductionService.spread(seedlingSpot.get(), dna, currentConfig,
                    "intent");
            dna.markPlacedForIntent(intent, dna.planCursor());
            dna.consumeStageGrowthBurst();
            return TreeConstructionResult.changed(
                    1, "seedling.spread " + dna.key());
        }

        if (intent == TreeGrowthIntent.CANOPY) {
            BranchTipCoverage branchTips = planAudit.branchTipCoverage(
                    candidate, dna, cachedPlan);
            if (branchTips.firstUnplannedBareTip() != null
                    && planAudit.pruneUnplannedBareTerminal(
                            candidate, dna, cachedPlan, currentConfig,
                            branchTips.firstUnplannedBareTip())) {
                dna.markPlacedForIntent(intent, dna.planCursor());
                repository.markDirty("stale-terminal-log-retired " + dna.key());
                repository.save(currentConfig);
                planAudit.invalidateLiveAnalysis(dna.key());
                maturityService.updateMaturity(candidate, dna, currentConfig);
                return TreeConstructionResult.changed(
                        1, "prune.unplanned-bare-terminal " + dna.key());
            }
            if (branchTips.firstStalePersistentEnvelopeLeaf() != null
                    && planAudit.pruneStalePersistentEnvelopeLeaf(
                            candidate, dna, cachedPlan, currentConfig,
                            branchTips.firstStalePersistentEnvelopeLeaf())) {
                dna.markPlacedForIntent(intent, dna.planCursor());
                planAudit.invalidateLiveAnalysis(dna.key());
                maturityService.updateMaturity(candidate, dna, currentConfig);
                return TreeConstructionResult.changed(
                        1, "prune.stale-persistent-envelope-leaf "
                                + dna.key());
            }
            Optional<Block> exposedLog = canopyRepairService.findExposedUpperLog(
                    candidate, dna, cachedPlan.blocksByKey());
            if (exposedLog.isPresent()) {
                int liftedLeaves = canopyRepairService.coverExposedLog(
                        candidate, dna, currentConfig,
                        exposedLog.get(), cachedPlan.blocksByKey());
                if (liftedLeaves > 0) {
                    dna.markPlacedForIntent(intent, dna.planCursor());
                    dna.consumeStageGrowthBurst();
                planAudit.invalidateLiveAnalysis(dna.key());
                    maturityService.updateMaturity(candidate, dna, currentConfig);
                    plugin.pathDebug().trace(plugin, "tree-evolution",
                            "shape.integrity.canopy-cover",
                            "tree=" + dna.key()
                                    + " trunk=" + format(exposedLog.get())
                                    + " leaves=" + liftedLeaves
                                    + " ## canopy shell corrected an exposed live upper trunk before normal target selection");
                    return TreeConstructionResult.changed(
                            liftedLeaves,
                            "canopy.integrity-cover " + dna.key());
                }
            }
            if (branchTips.firstUncoveredTip() != null) {
                int attachedLeaves = canopyRepairService.coverBranchTip(
                        candidate, dna, currentConfig,
                        branchTips.firstUncoveredTip(),
                        branchTips.firstRequiredContacts(),
                        branchTips.firstRequiredCluster(),
                        cachedPlan.blocksByKey());
                if (attachedLeaves > 0) {
                    dna.markPlacedForIntent(intent, dna.planCursor());
                    dna.consumeStageGrowthBurst();
                planAudit.invalidateLiveAnalysis(dna.key());
                    maturityService.updateMaturity(candidate, dna, currentConfig);
                    plugin.pathDebug().trace(plugin, "tree-evolution",
                            "shape.integrity.branch-tip-cover",
                            "tree=" + dna.key()
                                    + " tip="
                                    + format(branchTips.firstUncoveredTip())
                                    + " leaves=" + attachedLeaves
                                    + " target-contacts="
                                    + branchTips.firstRequiredContacts()
                                    + " target-envelope="
                                    + branchTips.firstRequiredCluster()
                                    + " ## a protruding terminal limb builds its preplanned connected leaf envelope before general canopy fill");
                    return TreeConstructionResult.changed(
                            attachedLeaves,
                            "canopy.branch-tip-cover " + dna.key());
                }
            }
        }

        Optional<PlannedTarget> plannedTarget = placementService.nextPlannedTarget(
                candidate, dna, cachedPlan, intent, currentConfig);
        if (plannedTarget.isPresent()) {
            PlannedTreeBlock plannedBlock = plannedTarget.get().block();
            Block target = plannedTarget.get().target();
            placementService.place(target, plannedBlock);
            if (dna.markEvolvedBlock(keyFor(target), plannedBlock.role())) {
                repository.markDirty("recorded evolved " + plannedBlock.role()
                        + " " + keyFor(target));
            }
            dna.markPlacedForIntent(intent,
                    plannedTarget.get().nextCursor());
                planAudit.invalidateLiveAnalysis(dna.key());
            int liftedLeaves = canopyRepairService.maybeCoverExposedTopLog(
                    candidate, dna, currentConfig, target, plannedBlock,
                    cachedPlan.blocksByKey());
            if (intent != TreeGrowthIntent.CLEANUP
                    && intent != TreeGrowthIntent.SEEDLING) {
                dna.consumeStageGrowthBurst();
            }
            maturityService.updateMaturity(candidate, dna, currentConfig);
            changedBlocks.incrementAndGet();
            diagnostics.recordPlaced(
                    plugin, currentConfig, target, plannedBlock);
            plugin.pathDebug().trace(plugin, "tree-evolution",
                    "place.block",
                    plannedBlock.role() + " " + plannedBlock.material()
                            + " intent=" + intent
                            + " at " + format(target)
                            + (liftedLeaves > 0
                                    ? " canopy-lift-leaves=" + liftedLeaves
                                    : ""));
            return TreeConstructionResult.changed(
                    1, plannedBlock.role() + " "
                            + plannedBlock.material() + " intent=" + intent
                            + " dna=" + dna.key());
        }

        if (intent != TreeGrowthIntent.CLEANUP) {
            diagnostics.recordReject(currentConfig,
                    "prune-skipped-normal-growth",
                    dna.key() + " intent=" + intent);
        }
        dna.markBlocked();
        if (dna.blockedAttempts() >= 3) {
            dna.setCurrentIntent(
                    TreeGrowthIntentPolicy.nextAfterBlocked(dna.currentIntent()));
        }
        diagnostics.recordReject(currentConfig,
                "target-complete-or-blocked",
                dna.key() + " intent=" + intent
                        + " blocked=" + dna.blockedAttempts());
        return TreeConstructionResult.idle(
                "target-complete-or-blocked " + dna.key()
                        + " intent=" + intent);
    }
    private TreeGrowthIntent refreshIntent(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig currentConfig
    ) {
        if (dna.stageCleanupBurst() > 0) {
            dna.setCurrentIntent(TreeGrowthIntent.CLEANUP);
            return dna.currentIntent();
        }
        if (dna.currentIntent() == TreeGrowthIntent.CLEANUP) {
            // ## Cleanup is a transition state, not a weighted maintenance task.
            dna.setCurrentIntent(TreeGrowthIntent.CANOPY);
        }
        if (dna.stageGrowthBurst() > 0) {
            dna.setCurrentIntent(TreeGrowthIntentPolicy.stageBurstIntent(candidate, dna));
            return dna.currentIntent();
        }
        TreeGrowthIntent preferred = TreeGrowthIntentPolicy.preferredIntent(
                candidate, dna, currentConfig,
                reproductionService.cooldownUntil(dna.key()));
        if (dna.blockedAttempts() >= 3 || dna.age() - dna.lastIntentChangeAge() >= TreeGrowthIntentPolicy.intentSpan(dna, dna.currentIntent())) {
            dna.setCurrentIntent(preferred);
        }
        if (dna.damageCount() > 0 && dna.currentIntent() != TreeGrowthIntent.REPAIR) {
            dna.setCurrentIntent(TreeGrowthIntent.REPAIR);
        }
        return dna.currentIntent();
    }

    // ## TRANSITION RECONCILER
    // A source leaf may become planned wood only when the exact target and its
    // parent dependency are ready. This avoids an AIR frame and prevents a
    // cleanup pass from outrunning the replacement structure.

    private static String keyFor(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":"
                + block.getY() + ":" + block.getZ();
    }

    private String format(Block block) {
        return block.getWorld().getName() + " " + block.getX() + ","
                + block.getY() + "," + block.getZ();
    }

    private String format(Location location) {
        String worldName = location.getWorld() == null
                ? "unknown" : location.getWorld().getName();
        return worldName + " " + location.getBlockX() + ","
                + location.getBlockY() + "," + location.getBlockZ();
    }
}
