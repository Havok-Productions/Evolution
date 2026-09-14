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
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;
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
    private final TreeConstructorPreflight preflight;
    private final TreeCanopyIntegrityOperations canopyIntegrityOperations;
    private final TreeTransitionConstructionOperations transitionOperations;
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
        this.preflight = new TreeConstructorPreflight(
                plugin, diagnostics, repository, maturityService,
                transitionService, reproductionService);
        this.canopyIntegrityOperations = new TreeCanopyIntegrityOperations(
                plugin, diagnostics, repository, planAudit,
                maturityService, canopyRepairService,
                transitionService);
        this.transitionOperations = new TreeTransitionConstructionOperations(
                plugin, diagnostics, repository, planAudit,
                transitionService, changedBlocks);
        this.canWorkAt = canWorkAt;
        this.changedBlocks = changedBlocks;
    }
    TreeConstructionResult evolve(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig currentConfig) {
        try (ReportSample sample = plugin.resourceReporter().begin("tree-evolution", "action.evolve")) {
            if (!dna.stumpPresent()) {
                diagnostics.recordReject(currentConfig, "missing-stump", dna.key());
                sample.detail("missing-stump " + dna.key());
                return TreeConstructionResult.idle(
                        "preflight.missing-stump " + dna.key());
            }
            if (!TreeCandidateBindingPolicy.isCanonical(candidate, dna)) {
                // ## This is the final safety boundary. The focus scheduler
                // must canonicalize discovery aliases before the constructor
                // sees them; continuing here would evaluate ownership from one
                // stump while mutating another tree's persisted plan.
                diagnostics.recordReject(
                        currentConfig,
                        "candidate-dna-mismatch",
                        "candidate=" + candidate.baseKey()
                                + " dna=" + dna.key());
                plugin.pathDebug().failure(
                        plugin,
                        "tree-evolution",
                        "candidate-dna-mismatch",
                        "candidate=" + candidate.baseKey()
                                + " dna=" + dna.key()
                                + " ## constructor stopped before world mutation");
                sample.detail("candidate-dna-mismatch " + dna.key());
                return TreeConstructionResult.idle(
                        "preflight.candidate-dna-mismatch " + dna.key());
            }
            if (candidate.baseBlock().getType() != dna.species().logMaterial()) {
                dna.setStumpPresent(false);
                repository.markDirty("base-not-log");
                repository.save(currentConfig);
                diagnostics.recordReject(currentConfig, "base-not-log", format(candidate.baseBlock()));
                sample.detail("base-not-log " + dna.key());
                return TreeConstructionResult.logicalProgress(
                        "preflight.base-not-log " + dna.key());
            }
            if (!canWorkAt.test(candidate.baseLocation(), currentConfig)) {
                diagnostics.recordReject(currentConfig, "work-gate", format(candidate.baseLocation()));
                sample.detail("work-gate " + dna.key());
                return TreeConstructionResult.idle(
                        "preflight.work-gate " + dna.key());
            }
            TreeConstructorPreflight.SourceResult sourcePreflight;
            try (ReportSample phase = plugin.resourceReporter().begin(
                    "tree-evolution", "phase.preflight-source")) {
                sourcePreflight = preflight.prepareSource(
                        candidate, dna, currentConfig);
                phase.workUnits(candidate.connectedLogs()
                                + candidate.connectedLeaves())
                        .detail(dna.key());
            }
            if (!sourcePreflight.ready()) {
                sample.detail("original-shape-wait " + dna.key());
                return TreeConstructionResult.idle(
                        "preflight." + sourcePreflight.detail() + " "
                                + dna.key());
            }
            boolean preflightProgress = sourcePreflight.progressed();

        // ## TREE CONSTRUCTOR CORE
        // The feature gathers one immutable live snapshot, the hierarchy selects one
        // attached subsystem, and only that subsystem may change the tree this action.
        Biome biome = candidate.baseBlock().getBiome();
        CachedTreePlan cachedPlan;
        try (ReportSample phase = plugin.resourceReporter().begin(
                "tree-evolution", "phase.plan-load")) {
            cachedPlan = planAudit.cachedPlan(
                    dna, biome, currentConfig.rootsEnabled());
            phase.workUnits(cachedPlan.orderedBlocks().size())
                    .detail(dna.species().id() + " " + dna.variant().id());
        }
        TreeConstructorPreflight.IntentResult initialIntent =
                preflight.selectIntent(candidate, dna, currentConfig);
        TreeGrowthIntent requestedIntent = initialIntent.intent();
        preflightProgress |= initialIntent.progressed();
        try (ReportSample phase = plugin.resourceReporter().begin(
                "tree-evolution", "phase.diagnostics-plan")) {
            diagnostics.recordPlan(currentConfig, dna, cachedPlan.plan(),
                    cachedPlan.orderedBlocks(), candidate.world(), false);
            phase.workUnits(cachedPlan.orderedBlocks().size())
                    .detail(dna.key());
        }

        TreeGrowthQueuePolicy.Completion constructorCompletion;
        TreeGrowthQueuePolicy.Budget constructorBudget =
                TreeGrowthQueuePolicy.stageBudget(dna);
        int constructorExposedLogs;
        BranchTipCoverage constructorBranchTips;
        try (ReportSample phase = plugin.resourceReporter().begin(
                "tree-evolution", "phase.live-plan-audit")) {
            constructorCompletion = planAudit.stageCompletion(
                    candidate, dna, cachedPlan);
            constructorExposedLogs = planAudit.exposedUpperLogCount(
                    candidate, dna, cachedPlan.blocksByKey());
            constructorBranchTips = planAudit.branchTipCoverage(
                    candidate, dna, cachedPlan);
            phase.workUnits(cachedPlan.orderedBlocks().size())
                    .detail(dna.key());
        }
        boolean constructorStageComplete = TreeFocusPolicy.stageStructureComplete(
                constructorCompletion, constructorBudget,
                constructorExposedLogs, constructorBranchTips.uncoveredTips());
        boolean constructorTargetVoxelsComplete =
                TreeFocusPolicy.targetVoxelsComplete(
                        constructorCompletion, constructorBudget);
        TreeConstructorPreflight.IntentResult normalizedIntent =
                preflight.normalizeStaleDamage(
                        dna, requestedIntent,
                        constructorTargetVoxelsComplete,
                        currentConfig);
        requestedIntent = normalizedIntent.intent();
        preflightProgress |= normalizedIntent.progressed();
        boolean constructorNeedsCompleteOwnership =
                TreeFocusPolicy.completeOwnershipRequired(
                        dna.stageCleanupBurst(),
                        dna.damageCount(),
                        requestedIntent == TreeGrowthIntent.REPAIR,
                        constructorStageComplete,
                        dna.hasOriginalShapeSnapshot(),
                        dna.unresolvedOriginalShapeLeafCount());
        boolean obsoleteEvolvedReceipt =
                transitionService.hasObsoleteEvolvedReceipt(
                        dna, cachedPlan);
        if ((constructorNeedsCompleteOwnership || obsoleteEvolvedReceipt)
                && !candidate.ownershipComplete()) {
            try (ReportSample phase = plugin.resourceReporter().begin(
                    "tree-evolution", "phase.complete-ownership-rescan")) {
                candidate = candidateDiscovery.build(
                                candidate.baseBlock(), true)
                        .orElse(candidate);
                constructorCompletion = planAudit.stageCompletion(
                        candidate, dna, cachedPlan);
                constructorExposedLogs = planAudit.exposedUpperLogCount(
                        candidate, dna, cachedPlan.blocksByKey());
                constructorBranchTips = planAudit.branchTipCoverage(
                        candidate, dna, cachedPlan);
                phase.workUnits(candidate.connectedLogs()
                                + candidate.connectedLeaves())
                        .detail(dna.key());
            }
            constructorStageComplete = TreeFocusPolicy.stageStructureComplete(
                    constructorCompletion, constructorBudget,
                    constructorExposedLogs, constructorBranchTips.uncoveredTips());
        }
        TreeTransitionService.NeighborProtection neighborProtection;
        try (ReportSample phase = plugin.resourceReporter().begin(
                "tree-evolution", "phase.neighbor-plan-protection")) {
            neighborProtection = transitionService.neighborProtection(
                    candidate, dna, currentConfig);
            phase.workUnits(neighborProtection.plannedBlocks())
                    .detail("tree=" + dna.key()
                            + " nearby=" + neighborProtection.nearbyTrees()
                            + " body=" + neighborProtection.bodyKeys().size()
                            + " canopy="
                            + neighborProtection.canopyKeys().size());
        }
        preflightProgress |= preflight.reconcileSourceLedger(
                candidate, dna, cachedPlan, currentConfig,
                neighborProtection);
        boolean transitionPending = TreeFocusPolicy.transitionPending(
                dna.stageCleanupBurst(), dna.stageGrowthBurst(),
                constructorStageComplete, dna.hasOriginalShapeSnapshot());
        boolean transitionCleanupRequired = transitionPending
                && dna.unresolvedOriginalShapeLeafCount() > 0;
        boolean cleanupTurnReady = constructorCompletion.canopyPercent()
                >= constructorBudget.canopyPercent()
                || dna.consecutivePrunes() < 2;
        boolean broadCleanupReady =
                TreeCanopyTransitionPolicy.allowsBroadCleanup(
                        dna, constructorCompletion.canopyPercent())
                && constructorCompletion.trunkPercent()
                        >= constructorBudget.trunkPercent()
                && constructorCompletion.branchPercent()
                        >= constructorBudget.branchPercent()
                && cleanupTurnReady;
        Optional<PlannedTarget> transitionBlocker;
        List<Block> retiredCrown;
        Optional<TreeOwnershipRoleReconciliationPolicy.Repair>
                ownershipRoleRepair;
        TreeTransitionService.DisconnectedEvolvedState
                disconnectedState;
        Optional<TreeTransitionService.ObsoleteEvolvedBlock>
                disconnectedEvolvedStructure;
        Optional<TreeTransitionService.ObsoleteEvolvedBlock>
                conflictingEvolvedStructure;
        Optional<TreeTransitionService.ObsoleteEvolvedBlock>
                conflictSafeRetirement;
        Optional<TreeTransitionService.ObsoleteEvolvedBlock>
                obsoleteEvolvedStructure;
        try (ReportSample phase = plugin.resourceReporter().begin(
                "tree-evolution", "phase.transition-reconciliation")) {
            transitionBlocker = transitionCleanupRequired
                            && candidate.ownershipComplete()
                    ? transitionService.readyTransitionBlocker(
                            candidate, dna, cachedPlan, currentConfig,
                            neighborProtection)
                    : Optional.empty();
            retiredCrown = transitionCleanupRequired
                            && candidate.ownershipComplete()
                            && broadCleanupReady
                    ? transitionService.findRetiredCanopyLeaves(
                            candidate, dna, cachedPlan,
                            planAudit.pruneBatchSize(dna), currentConfig,
                            neighborProtection)
                    : List.of();
            ownershipRoleRepair = transitionService.findOwnershipRoleRepair(
                    candidate, dna, cachedPlan, currentConfig);
            disconnectedState = transitionService.findDisconnectedEvolvedState(
                    candidate, dna, cachedPlan, currentConfig,
                    neighborProtection);
            disconnectedEvolvedStructure = disconnectedState.obsolete();
            conflictingEvolvedStructure =
                    transitionService.findConflictingEvolvedTargetBlock(
                            candidate, dna, cachedPlan, currentConfig,
                            neighborProtection);
            conflictSafeRetirement = conflictingEvolvedStructure.isPresent()
                    ? transitionService.findConflictSafeRetirement(
                            candidate, dna, cachedPlan, currentConfig,
                            conflictingEvolvedStructure.get(),
                            neighborProtection)
                    : Optional.empty();
            obsoleteEvolvedStructure = broadCleanupReady
                            && candidate.ownershipComplete()
                    ? transitionService.findObsoleteEvolvedBlock(
                            candidate, dna, cachedPlan, currentConfig,
                            neighborProtection)
                    : Optional.empty();
            phase.workUnits(dna.evolvedLogCount()
                            + dna.evolvedLeafCount()
                            + cachedPlan.orderedBlocks().size())
                    .detail(dna.key());
        }
        TreeConstructionSnapshot constructorSnapshot =
                TreeConstructionSnapshot.capture(
                        candidate, dna, constructorCompletion,
                        constructorBudget, requestedIntent,
                        new TreeConstructionSnapshot.Facts(
                                constructorExposedLogs,
                                constructorBranchTips.uncoveredTips(),
                                constructorBranchTips.unplannedBareTips(),
                                constructorBranchTips
                                        .stalePersistentEnvelopeLeaves(),
                                constructorBranchTips
                                        .uncoveredPlannedEnvelopes(),
                                transitionBlocker.isPresent(),
                                ownershipRoleRepair.isPresent(),
                                disconnectedEvolvedStructure.isPresent(),
                                disconnectedState.targetRepairRequired(),
                                conflictSafeRetirement.isPresent(),
                                broadCleanupReady,
                                obsoleteEvolvedStructure.isPresent(),
                                !retiredCrown.isEmpty(),
                                dna.unresolvedOriginalShapeLeafCount() <= 0));
        TreeConstructionDecision construction;
        try (ReportSample phase = plugin.resourceReporter().begin(
                "tree-evolution", "phase.hierarchy-decision")) {
            construction = constructorCore.decide(constructorSnapshot);
            phase.detail(construction.marker());
        }
        if ((construction.subrule()
                        == TreeConstructionSubrule
                                .DISCONNECTED_TARGET_REPAIR
                || construction.subrule()
                        == TreeConstructionSubrule
                                .OWNERSHIP_ROLE_RECONCILIATION
                || construction.subrule()
                        == TreeConstructionSubrule
                                .DISCONNECTED_EVOLVED_STRUCTURE
                || construction.subrule()
                        == TreeConstructionSubrule
                                .CONFLICTING_EVOLVED_TARGET
                || construction.subrule()
                        == TreeConstructionSubrule
                                .OBSOLETE_EVOLVED_STRUCTURE)
                && diagnostics.recordBlockedConstructorEnvironment(
                        currentConfig, dna, cachedPlan.plan(),
                        cachedPlan.orderedBlocks(), candidate.world())) {
            diagnostics.saveSoon(plugin, currentConfig);
        }
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
        TreeConstructorStageTimeline.Boundary constructorStageBoundary =
                diagnostics.recordConstructorDecision(
                currentConfig, dna, construction,
                constructorCompletion, constructorBudget, executorName);
        if (constructorStageBoundary.changed()) {
            // ## The expensive 3D world overlay is captured only when the
            // hierarchy changes smoke tags, not for every one-block action.
            try (ReportSample phase = plugin.resourceReporter().begin(
                    "tree-evolution", "phase.diagnostics-stage-frame")) {
                diagnostics.recordConstructorStageFrames(
                        currentConfig, dna, constructorStageBoundary,
                        cachedPlan.plan(), cachedPlan.orderedBlocks(),
                        candidate.world());
                phase.workUnits(cachedPlan.orderedBlocks().size())
                        .detail(dna.key() + " frames="
                                + constructorStageBoundary.frames().size());
            }
            diagnostics.saveSoon(plugin, currentConfig);
        }
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
            public TreeConstructionResult reconcileOwnershipRole() {
                return transitionOperations.reconcileOwnershipRole(
                        dna, currentConfig, ownershipRoleRepair);
            }

            @Override
            public TreeConstructionResult repairDisconnectedTarget() {
                return transitionOperations.repairDisconnectedTarget(
                        constructionCandidate, dna, currentConfig,
                        disconnectedState.targetRepair().repair());
            }

            @Override
            public TreeConstructionResult repairInterruptedDamage() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.REPAIR,
                        constructionRequestedIntent, construction);
            }

            @Override
            public TreeConstructionResult replaceTransitionBlocker() {
                return transitionOperations.replaceBlocker(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, transitionBlocker);
            }

            @Override
            public TreeConstructionResult buildSupport() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.HEIGHT,
                        constructionRequestedIntent, construction);
            }

            @Override
            public TreeConstructionResult coverExposedSupport() {
                return canopyIntegrityOperations.coverExposedSupport(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig);
            }

            @Override
            public TreeConstructionResult retireUnplannedBareTerminal() {
                return canopyIntegrityOperations.retireUnplannedBareTerminal(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig);
            }

            @Override
            public TreeConstructionResult retireStaleEnvelopeLeaf() {
                return canopyIntegrityOperations.retireStaleEnvelopeLeaf(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig);
            }

            @Override
            public TreeConstructionResult repairBranchEnvelope() {
                return canopyIntegrityOperations.repairBranchEnvelope(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig);
            }

            @Override
            public TreeConstructionResult buildMinimumCrownShell() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.CANOPY,
                        constructionRequestedIntent, construction);
            }

            @Override
            public TreeConstructionResult buildBranchFrame() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.BRANCH,
                        constructionRequestedIntent, construction);
            }

            @Override
            public TreeConstructionResult fillCanopy() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.CANOPY,
                        constructionRequestedIntent, construction);
            }

            @Override
            public TreeConstructionResult retireDisconnectedEvolvedStructure() {
                return transitionOperations.retireEvolvedStructure(
                        dna, cachedPlan, currentConfig,
                        disconnectedEvolvedStructure,
                        "disconnected-evolved");
            }

            @Override
            public TreeConstructionResult retireConflictingEvolvedTarget() {
                return transitionOperations.retireEvolvedStructure(
                        dna, cachedPlan, currentConfig,
                        conflictSafeRetirement,
                        "target-role-conflict");
            }

            @Override
            public TreeConstructionResult retireObsoleteEvolvedStructure() {
                return transitionOperations.retireEvolvedStructure(
                        dna, cachedPlan, currentConfig,
                        obsoleteEvolvedStructure,
                        "obsolete-evolved");
            }

            @Override
            public TreeConstructionResult retireSourceCrown() {
                return transitionOperations.retireSourceCrown(
                        dna, currentConfig, construction, retiredCrown);
            }

            @Override
            public TreeConstructionResult finalizeTransition() {
                return transitionOperations.finalizeTransition(
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
                        constructionRequestedIntent, construction);
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
        try (ReportSample phase = plugin.resourceReporter().begin(
                "tree-evolution", "phase.diagnostics-completion-frame")) {
            diagnostics.recordConstructorCompletionFrame(
                    currentConfig, dna, construction,
                    cachedPlan.plan(), cachedPlan.orderedBlocks(),
                    candidate.world());
            phase.workUnits(cachedPlan.orderedBlocks().size())
                    .detail(dna.key() + " " + construction.subrule());
        }
        plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                "constructor.executor",
                construction.marker() + " executor=" + executorName
                        + " progressed=" + result.progressed()
                        + " changed=" + result.worldChanged()
                        + " units=" + result.changedUnits()
                        + " detail=" + result.detail());
        sample.changedUnits(result.changedUnits()).detail(result.detail());
        return result.withPriorLogicalProgress(
                preflightProgress, "constructor.preflight");
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
        return dna.maturityStage() != before
                ? TreeConstructionResult.logicalProgress(
                        "constructor.complete stage-advanced " + dna.key())
                : TreeConstructionResult.idle(
                        "constructor.complete " + dna.key());
    }

    private TreeConstructionResult executeIntentConstruction(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig,
            TreeGrowthIntent intent,
            TreeGrowthIntent requestedIntent,
            TreeConstructionDecision construction
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

        Optional<PlannedTarget> plannedTarget = placementService.nextPlannedTarget(
                candidate, dna, cachedPlan, intent, currentConfig);
        if (plannedTarget.isPresent()) {
            PlannedTreeBlock plannedBlock = plannedTarget.get().block();
            Block target = plannedTarget.get().target();
            Material previousMaterial = target.getType();
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
            boolean coordinateChurn = diagnostics.recordPlaced(
                    plugin, currentConfig, dna, target,
                    previousMaterial, plannedBlock, construction);
            if (coordinateChurn) {
                diagnostics.recordPotentialDeformation(
                        currentConfig, dna, cachedPlan.plan(),
                        cachedPlan.orderedBlocks(), candidate.world(),
                        "coordinate-churn", false);
            }
            plugin.pathDebug().trace(plugin, "tree-evolution",
                    "place.block",
                    plannedBlock.role() + " " + plannedBlock.material()
                            + " intent=" + intent
                            + " at " + format(target)
                            + " " + plannedBlock.augment().marker()
                            + " " + construction.marker()
                            + (liftedLeaves > 0
                                    ? " canopy-lift-leaves=" + liftedLeaves
                                    : ""));
            return TreeConstructionResult.changed(
                    1, plannedBlock.role() + " "
                            + plannedBlock.material() + " intent=" + intent
                            + " augment=" + plannedBlock.augment()
                            + " constructor=" + construction.ruleId()
                            + " dna=" + dna.key());
        }

        if (intent != TreeGrowthIntent.CLEANUP) {
            diagnostics.recordReject(currentConfig,
                    "prune-skipped-normal-growth",
                    dna.key() + " intent=" + intent);
        }
        dna.markBlocked();
        if (dna.blockedAttempts() >= 3) {
            diagnostics.recordPotentialDeformation(
                    currentConfig, dna, cachedPlan.plan(),
                    cachedPlan.orderedBlocks(), candidate.world(),
                    "three-consecutive-blocked-attempts", false);
        }
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
