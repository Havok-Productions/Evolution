package org.evolution.features.treeevolution;

import java.util.Optional;
import org.bukkit.block.Block;
import org.evolution.coreparts.EvolutionPlugin;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionResult;

/**
 * Live mini-constructors that protect the visible canopy/branch contract.
 *
 * <p>Each public method owns exactly one hierarchy subrule. A missing target
 * stops the cycle; this class never falls through into a sibling operation.</p>
 */
final class TreeCanopyIntegrityOperations {
    private final EvolutionPlugin plugin;
    private final TreeEvolutionDiagnostics diagnostics;
    private final TreeDnaRepository repository;
    private final TreePlanAuditService planAudit;
    private final TreeMaturityService maturityService;
    private final TreeCanopyRepairService canopyRepairService;
    private final TreeTransitionService transitionService;

    TreeCanopyIntegrityOperations(
            EvolutionPlugin plugin,
            TreeEvolutionDiagnostics diagnostics,
            TreeDnaRepository repository,
            TreePlanAuditService planAudit,
            TreeMaturityService maturityService,
            TreeCanopyRepairService canopyRepairService,
            TreeTransitionService transitionService
    ) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
        this.repository = repository;
        this.planAudit = planAudit;
        this.maturityService = maturityService;
        this.canopyRepairService = canopyRepairService;
        this.transitionService = transitionService;
    }

    TreeConstructionResult coverExposedSupport(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan plan,
            TreeEvolutionConfig config
    ) {
        Optional<Block> exposedLog = canopyRepairService.findExposedUpperLog(
                candidate, dna, plan.blocksByKey());
        if (exposedLog.isEmpty()) {
            return lostTarget(dna, config, "exposed-support");
        }
        int liftedLeaves = canopyRepairService.coverExposedLog(
                candidate, dna, config, exposedLog.get(),
                plan.blocksByKey());
        if (liftedLeaves <= 0) {
            return lostTarget(dna, config, "exposed-support-blocked");
        }
        markCanopyProgress(candidate, dna, config, true);
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "shape.integrity.canopy-cover",
                "tree=" + dna.key() + " trunk=" + format(exposedLog.get())
                        + " leaves=" + liftedLeaves
                        + " ## COVER_EXPOSED_SUPPORT performed only its selected canopy-integrity operation");
        return TreeConstructionResult.changed(
                liftedLeaves, "canopy.integrity-cover " + dna.key());
    }

    TreeConstructionResult retireUnplannedBareTerminal(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan plan,
            TreeEvolutionConfig config
    ) {
        BranchTipCoverage coverage = planAudit.branchTipCoverage(
                candidate, dna, plan);
        Block terminal = coverage.firstUnplannedBareTip();
        if (terminal == null) {
            return lostTarget(dna, config, "unplanned-bare-terminal");
        }
        TreeConstructionMutationPolicy.TargetRetirement retirement =
                planAudit.terminalRetirementPlan(
                        candidate, dna, plan, terminal);
        if (!retirement.safeToRetire()) {
            if (retirement.prerequisiteRetirement().isPresent()) {
                String dependency = retirement
                        .prerequisiteRetirement().get();
                if (!transitionService.retirePreconditionDependency(
                        dna, plan, candidate.world(), dependency,
                        config)) {
                    return lostTarget(
                            dna, config,
                            "unplanned-terminal-dependent-retirement-blocked");
                }
                repository.save(config);
                return TreeConstructionResult.changed(
                        1, "constructor.pre-retirement-dependent "
                                + dependency);
            }
            if (retirement.bridge().isEmpty()
                    || !transitionService.applyDisconnectedTargetRepair(
                            candidate, dna, retirement.bridge().get(),
                            config)) {
                return lostTarget(
                        dna, config,
                        "unplanned-terminal-bridge-blocked");
            }
            TreeTargetOwnershipRepairPolicy.Repair bridge =
                    retirement.bridge().get();
            repository.markDirty(
                    "pre-retirement target bridge " + dna.key());
            repository.save(config);
            String detail = "constructor.pre-retirement-bridge "
                    + bridge.marker();
            return bridge.action()
                            == TreeTargetOwnershipRepairPolicy.Action
                                    .ADOPT_LIVE_TARGET
                    ? TreeConstructionResult.logicalProgress(detail)
                    : TreeConstructionResult.changed(1, detail);
        }
        if (!planAudit.pruneUnplannedBareTerminal(
                candidate, dna, plan, config, terminal)) {
            return lostTarget(dna, config, "unplanned-bare-terminal");
        }
        dna.markPlacedForIntent(TreeGrowthIntent.CANOPY, dna.planCursor());
        repository.markDirty("stale-terminal-log-retired " + dna.key());
        repository.save(config);
        planAudit.invalidateLiveAnalysis(dna.key());
        maturityService.updateMaturity(candidate, dna, config);
        return TreeConstructionResult.changed(
                1, "prune.unplanned-bare-terminal " + dna.key());
    }

    TreeConstructionResult retireStaleEnvelopeLeaf(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan plan,
            TreeEvolutionConfig config
    ) {
        BranchTipCoverage coverage = planAudit.branchTipCoverage(
                candidate, dna, plan);
        Block staleLeaf = coverage.firstStalePersistentEnvelopeLeaf();
        if (staleLeaf == null
                || !planAudit.pruneStalePersistentEnvelopeLeaf(
                        candidate, dna, plan, config, staleLeaf)) {
            return lostTarget(dna, config, "stale-envelope-leaf");
        }
        repository.markDirty(
                "stale-envelope-leaf-retired " + dna.key());
        repository.save(config);
        markCanopyProgress(candidate, dna, config, false);
        return TreeConstructionResult.changed(
                1, "prune.stale-persistent-envelope-leaf " + dna.key());
    }

    TreeConstructionResult repairBranchEnvelope(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan plan,
            TreeEvolutionConfig config
    ) {
        BranchTipCoverage coverage = planAudit.branchTipCoverage(
                candidate, dna, plan);
        if (coverage.firstUncoveredTip() == null) {
            return lostTarget(dna, config, "branch-envelope");
        }
        int attachedLeaves = canopyRepairService.coverBranchTip(
                candidate, dna, config, coverage.firstUncoveredTip(),
                coverage.firstRequiredContacts(),
                coverage.firstRequiredCluster(), plan.blocksByKey());
        if (attachedLeaves <= 0) {
            return lostTarget(dna, config, "branch-envelope-blocked");
        }
        markCanopyProgress(candidate, dna, config, true);
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "shape.integrity.branch-tip-cover",
                "tree=" + dna.key() + " tip="
                        + format(coverage.firstUncoveredTip())
                        + " leaves=" + attachedLeaves
                        + " target-contacts="
                        + coverage.firstRequiredContacts()
                        + " target-envelope="
                        + coverage.firstRequiredCluster()
                        + " ## OWNED_BRANCH_ENVELOPE performed only terminal-envelope repair");
        return TreeConstructionResult.changed(
                attachedLeaves, "canopy.branch-tip-cover " + dna.key());
    }

    private void markCanopyProgress(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig config,
            boolean consumeBurst
    ) {
        dna.markPlacedForIntent(TreeGrowthIntent.CANOPY, dna.planCursor());
        if (consumeBurst) {
            dna.consumeStageGrowthBurst();
        }
        planAudit.invalidateLiveAnalysis(dna.key());
        maturityService.updateMaturity(candidate, dna, config);
    }

    private TreeConstructionResult lostTarget(
            TreeDna dna,
            TreeEvolutionConfig config,
            String operation
    ) {
        diagnostics.recordReject(config, "constructor-mini-target-lost",
                dna.key() + " operation=" + operation);
        plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                "constructor.mini-target-lost",
                "tree=" + dna.key() + " operation=" + operation
                        + " ## live state changed after selection; the cycle stops instead of falling through to another mini-constructor");
        return TreeConstructionResult.idle(
                "constructor.mini-target-lost " + operation + " "
                        + dna.key());
    }

    private String format(Block block) {
        return block.getWorld().getName() + " " + block.getX() + ","
                + block.getY() + "," + block.getZ();
    }
}
