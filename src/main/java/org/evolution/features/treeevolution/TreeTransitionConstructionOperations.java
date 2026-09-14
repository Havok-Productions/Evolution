package org.evolution.features.treeevolution;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.evolution.coreparts.EvolutionPlugin;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionResult;

/**
 * Live mini-constructors for ledger repair, atomic transitions, and cleanup.
 */
final class TreeTransitionConstructionOperations {
    private final EvolutionPlugin plugin;
    private final TreeEvolutionDiagnostics diagnostics;
    private final TreeDnaRepository repository;
    private final TreePlanAuditService planAudit;
    private final TreeTransitionService transitionService;
    private final AtomicLong changedBlocks;

    TreeTransitionConstructionOperations(
            EvolutionPlugin plugin,
            TreeEvolutionDiagnostics diagnostics,
            TreeDnaRepository repository,
            TreePlanAuditService planAudit,
            TreeTransitionService transitionService,
            AtomicLong changedBlocks
    ) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
        this.repository = repository;
        this.planAudit = planAudit;
        this.transitionService = transitionService;
        this.changedBlocks = changedBlocks;
    }

    TreeConstructionResult replaceBlocker(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan plan,
            TreeEvolutionConfig config, Optional<PlannedTarget> blocker) {
        if (blocker.isPresent()
                && transitionService.replaceTransitionBlocker(
                        candidate, dna, plan, config, blocker.get())) {
            return TreeConstructionResult.changed(
                    1, "constructor.atomic-blocker " + dna.key());
        }
        diagnostics.recordReject(config, "constructor-blocker-lost", dna.key());
        return TreeConstructionResult.idle(
                "constructor.blocker-lost " + dna.key());
    }

    TreeConstructionResult repairDisconnectedTarget(
            TreeCandidate candidate, TreeDna dna,
            TreeEvolutionConfig config,
            Optional<TreeTargetOwnershipRepairPolicy.Repair> repair) {
        if (repair.isEmpty()) {
            diagnostics.recordReject(config,
                    "constructor-disconnected-target-no-bridge",
                    dna.key()
                            + " ## TREE_11 found an orphan receipt but no rootward planned bridge");
            plugin.pathDebug().failure(plugin, "tree-evolution",
                    "constructor.disconnected-target-no-bridge",
                    "tree=" + dna.key()
                            + " ## planner/ledger mismatch requires a new captured fixture");
            dna.markBlocked();
            return TreeConstructionResult.idle(
                    "constructor.disconnected-target-no-bridge " + dna.key());
        }
        TreeTargetOwnershipRepairPolicy.Repair selected = repair.get();
        if (!transitionService.applyDisconnectedTargetRepair(
                candidate, dna, selected, config)) {
            dna.markBlocked();
            diagnostics.recordReject(config,
                    "constructor-disconnected-target-bridge-blocked",
                    dna.key() + " " + selected.marker());
            return TreeConstructionResult.idle(
                    "constructor.disconnected-target-bridge-blocked "
                            + dna.key());
        }
        dna.markPlacedForIntent(TreeGrowthIntent.REPAIR, dna.planCursor());
        repository.markDirty(
                "constructor disconnected target bridge " + dna.key());
        repository.save(config);
        String detail = "constructor.disconnected-target-bridge "
                + selected.marker();
        return selected.action()
                        == TreeTargetOwnershipRepairPolicy.Action
                                .ADOPT_LIVE_TARGET
                ? TreeConstructionResult.logicalProgress(detail)
                : TreeConstructionResult.changed(1, detail);
    }

    TreeConstructionResult reconcileOwnershipRole(
            TreeDna dna, TreeEvolutionConfig config,
            Optional<TreeOwnershipRoleReconciliationPolicy.Repair> repair) {
        if (repair.isEmpty()) {
            diagnostics.recordReject(config,
                    "constructor-ownership-role-repair-lost", dna.key());
            return TreeConstructionResult.idle(
                    "constructor.ownership-role-repair-lost " + dna.key());
        }
        if (!transitionService.applyOwnershipRoleRepair(
                dna, repair.get(), config)) {
            diagnostics.recordReject(config,
                    "constructor-ownership-role-repair-no-progress",
                    dna.key() + " " + repair.get().marker());
            return TreeConstructionResult.idle(
                    "constructor.ownership-role-repair-no-progress "
                            + dna.key());
        }
        return TreeConstructionResult.logicalProgress(
                "constructor.ownership-role-reconciled "
                        + repair.get().marker());
    }

    TreeConstructionResult retireEvolvedStructure(
            TreeDna dna, CachedTreePlan plan, TreeEvolutionConfig config,
            Optional<TreeTransitionService.ObsoleteEvolvedBlock> structure,
            String route) {
        if (structure.isPresent()
                && transitionService.retireObsoleteEvolvedBlock(
                        dna, structure.get(), plan, config)) {
            if (structure.get().releaseOnly()) {
                repository.save(config);
                return TreeConstructionResult.logicalProgress(
                        "constructor.release-neighbor-ownership route="
                                + route + " " + dna.key());
            }
            dna.markPrunedNow();
            repository.save(config);
            return TreeConstructionResult.changed(
                    1, "constructor.prune-evolved-structure route="
                            + route + " " + dna.key() + " reason="
                            + structure.get().reason());
        }
        diagnostics.recordReject(config,
                "constructor-evolved-structure-lost",
                dna.key() + " route=" + route);
        return TreeConstructionResult.idle(
                "constructor.evolved-structure-lost route=" + route
                        + " " + dna.key());
    }

    TreeConstructionResult retireSourceCrown(
            TreeDna dna, TreeEvolutionConfig config,
            TreeConstructionDecision decision, List<Block> retiredCrown) {
        if (retiredCrown.isEmpty()) {
            diagnostics.recordReject(config,
                    "constructor-retired-crown-empty",
                    dna.key() + " unresolved-source-leaves="
                            + dna.unresolvedOriginalShapeLeafCount());
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "gate.source-leaf-unresolved",
                    "tree=" + dna.key() + " unresolved="
                            + dna.unresolvedOriginalShapeLeafCount()
                            + " ## the source snapshot remains open; no unresolved leaf may be forgotten by transition finalization");
            return TreeConstructionResult.idle(
                    "constructor.retired-crown-empty " + dna.key());
        }
        Block leaf = retiredCrown.get(0);
        String retiredLeafKey = keyFor(leaf);
        if (!dna.markOriginalShapeLeafRetired(retiredLeafKey)) {
            diagnostics.recordReject(config,
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
        diagnostics.recordRemoved(
                plugin, config, dna, leaf,
                dna.species().leafMaterial(), TreeBlockRole.CANOPY,
                TreePlacementAugment.UNCLASSIFIED,
                org.evolution.features.treeevolution.constructor
                        .TreeConstructionSubrule.RETIRED_SOURCE_CROWN,
                "retired-source-crown");
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "constructor.prune-retired-crown",
                decision.marker() + " tree=" + dna.key()
                        + " removed=" + format(leaf)
                        + " ## broad pruning runs only after the replacement structure reaches every stage target");
        return TreeConstructionResult.changed(
                1, "constructor.prune-retired-crown " + dna.key());
    }

    TreeConstructionResult finalizeTransition(
            TreeCandidate candidate, TreeDna dna,
            TreeEvolutionConfig config,
            TreeConstructionDecision decision) {
        int originalBlocks = dna.originalShapeBlockCount();
        dna.completeStageCleanup();
        TreeGrowthIntent nextIntent = dna.stageGrowthBurst() > 0
                ? TreeGrowthIntentPolicy.stageBurstIntent(candidate, dna)
                : TreeGrowthIntent.CANOPY;
        dna.setCurrentIntent(nextIntent);
        repository.markDirty(
                "constructor transition cleanup complete " + dna.key());
        repository.save(config);
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "constructor.transition-finalize",
                decision.marker() + " tree=" + dna.key()
                        + " source-blocks=" + originalBlocks
                        + " next=" + nextIntent);
        return TreeConstructionResult.logicalProgress(
                "constructor.transition-finalize " + dna.key());
    }

    private static String keyFor(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":"
                + block.getY() + ":" + block.getZ();
    }

    private String format(Block block) {
        return block.getWorld().getName() + " " + block.getX() + ","
                + block.getY() + "," + block.getZ();
    }
}
