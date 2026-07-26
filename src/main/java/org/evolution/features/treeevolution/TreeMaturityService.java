package org.evolution.features.treeevolution;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.evolution.coreparts.EvolutionPlugin;

/**
 * ## Owns source snapshots and maturity-stage transitions.
 *
 * <p>This gate runs above constructor phase selection. A stage may advance only
 * after the shared plan audit reports completion and the full source crown is
 * captured, preserving the hierarchy contract across reloads.</p>
 */
final class TreeMaturityService {
    private final EvolutionPlugin plugin;
    private final TreeEvolutionDiagnostics diagnostics;
    private final TreeDnaRepository repository;
    private final TreeCandidateDiscoveryService candidateDiscovery;
    private final TreePlanAuditService planAudit;
    private final Supplier<TreeEvolutionConfig> configSupplier;

    TreeMaturityService(
            EvolutionPlugin plugin,
            TreeEvolutionDiagnostics diagnostics,
            TreeDnaRepository repository,
            TreeCandidateDiscoveryService candidateDiscovery,
            TreePlanAuditService planAudit,
            Supplier<TreeEvolutionConfig> configSupplier
    ) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
        this.repository = repository;
        this.candidateDiscovery = candidateDiscovery;
        this.planAudit = planAudit;
        this.configSupplier = configSupplier;
    }
    void reconcileStageWithSourceHeight(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig currentConfig
    ) {
        if (!dna.hasOriginalShapeSnapshot()) {
            return;
        }
        int observedHeight = Math.max(
                candidate.height(),
                liveTrunkHeight(candidate.world(), dna));
        int advanced = 0;
        while (TreeSourceStagePolicy.shouldAdvance(
                dna.maturityStage(), currentConfig.maximumStage(),
                observedHeight, TreeSpeciesStageStyle.visibleHeight(dna))) {
            TreeMaturityStage before = dna.maturityStage();
            int formerStageHeight = TreeSpeciesStageStyle.visibleHeight(dna);
            if (!dna.advanceMaturity()) {
                break;
            }
            advanced++;
            diagnostics.recordStageTransition(
                    currentConfig, dna, before, dna.maturityStage(),
                    "source-height-reconcile observed=" + observedHeight
                            + " former-stage-height=" + formerStageHeight
                            + " ## an existing trunk cannot be forced backward into a shorter stage");
        }
        if (advanced <= 0) {
            return;
        }
                planAudit.invalidateLiveAnalysis(dna.key());
        repository.markDirty("source-height stage reconcile " + dna.key());
        repository.save(currentConfig);
        plugin.pathDebug().trace(
                plugin, "tree-evolution", "state.source-height-stage-reconcile",
                "tree=" + dna.key()
                        + " observed-height=" + observedHeight
                        + " advanced=" + advanced
                        + " stage=" + dna.maturityStage()
                        + " planned-height="
                        + TreeSpeciesStageStyle.visibleHeight(dna)
                        + " ## source-size reconciliation runs before constructor routing");
    }

    void updateMaturity(TreeCandidate candidate, TreeDna dna, TreeEvolutionConfig currentConfig) {
        int current = Math.max(candidate.height(), liveTrunkHeight(candidate.world(), dna));
        TreeMaturityStage before = dna.maturityStage();
        if (before.ordinal() >= currentConfig.maximumStage().ordinal()) {
            return;
        }
        TreeWorkStatus stageStatus = planAudit.workStatus(
                candidate, dna, currentConfig);
        if (!TreeFocusPolicy.readyForMaturity(
                stageStatus.stageComplete(), dna.stageCleanupBurst(),
                dna.stageGrowthBurst(), dna.hasOriginalShapeSnapshot())) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "stage.wait.structure-complete",
                    "tree=" + dna.key() + " stage=" + before
                            + " " + stageStatus.summary()
                            + " cleanup=" + dna.stageCleanupBurst()
                            + " growth=" + dna.stageGrowthBurst()
                            + " snapshot=" + dna.hasOriginalShapeSnapshot()
                            + " ## age and height cannot advance maturity before the current projected stage is structurally complete");
            return;
        }
        if (currentConfig.testingStageAccelerationEnabled()) {
            if (before == TreeMaturityStage.SMALL
                    && dna.age() >= currentConfig.smallToMediumAge()
                    && current >= Math.max(4, TreeSpeciesStageStyle.visibleHeight(dna) - 1)) {
                if (advanceMaturity(candidate, dna, currentConfig, "update-maturity")) {
                    diagnostics.recordStageTransition(currentConfig, dna, before, dna.maturityStage(),
                            "testing-accelerated age=" + dna.age()
                                    + " gate=" + currentConfig.smallToMediumAge()
                                    + " height=" + current
                                    + " ## SMALL finished, tree is entering visible branch/canopy fill");
                }
                return;
            }
            if (before == TreeMaturityStage.MEDIUM
                    && dna.age() >= currentConfig.mediumToMatureAge()
                    && current >= Math.max(6, TreeSpeciesStageStyle.visibleHeight(dna) - 1)) {
                if (advanceMaturity(candidate, dna, currentConfig, "update-maturity")) {
                    diagnostics.recordStageTransition(currentConfig, dna, before, dna.maturityStage(),
                            "testing-accelerated age=" + dna.age()
                                    + " gate=" + currentConfig.mediumToMatureAge()
                                    + " height=" + current
                                    + " ## MEDIUM finished, tree is entering mature crown/branch fill");
                }
                return;
            }
            boolean ancientAllowed = currentConfig.allowAnyRarityAncient()
                    || dna.rarity() == TreeRarity.RARE
                    || dna.rarity() == TreeRarity.LANDMARK;
            if (before == TreeMaturityStage.MATURE
                    && ancientAllowed
                    && dna.age() >= currentConfig.matureToAncientAge()
                    && current >= Math.max(8, Math.round(dna.targetHeight() * 0.82F))) {
                if (advanceMaturity(candidate, dna, currentConfig, "update-maturity")) {
                    diagnostics.recordStageTransition(currentConfig, dna, before, dna.maturityStage(),
                            "testing-accelerated age=" + dna.age()
                                    + " gate=" + currentConfig.matureToAncientAge()
                                    + " rarity=" + dna.rarity()
                                    + " any-rarity=" + currentConfig.allowAnyRarityAncient()
                                    + " ## MATURE finished, tree is entering ancient/final test form");
                }
            }
            return;
        }
        if (!currentConfig.ancientStageOnHold()
                && dna.age() > currentConfig.matureToAncientAge()
                && (dna.rarity() == TreeRarity.RARE || dna.rarity() == TreeRarity.LANDMARK)) {
            while (dna.maturityStage() != TreeMaturityStage.ANCIENT) {
                if (!advanceMaturity(candidate, dna, currentConfig, "update-maturity")) {
                    break;
                }
                diagnostics.recordStageTransition(currentConfig, dna, before, dna.maturityStage(), "age=" + dna.age());
                before = dna.maturityStage();
            }
        } else if (current >= dna.targetHeight()) {
            if (advanceMaturity(candidate, dna, currentConfig, "update-maturity")) {
                diagnostics.recordStageTransition(currentConfig, dna, before, dna.maturityStage(), "height=" + current);
            }
        } else if (current >= TreeSpeciesStageStyle.visibleHeight(dna) - 1 && dna.maturityStage() == TreeMaturityStage.SMALL && dna.age() >= currentConfig.smallToMediumAge()) {
            if (advanceMaturity(candidate, dna, currentConfig, "update-maturity")) {
                diagnostics.recordStageTransition(currentConfig, dna, before, dna.maturityStage(), "stage-height=" + current);
            }
        } else if (current >= TreeSpeciesStageStyle.visibleHeight(dna) - 1 && dna.maturityStage() == TreeMaturityStage.MEDIUM && dna.age() >= currentConfig.mediumToMatureAge()) {
            if (advanceMaturity(candidate, dna, currentConfig, "update-maturity")) {
                diagnostics.recordStageTransition(currentConfig, dna, before, dna.maturityStage(), "stage-height=" + current);
            }
        }
    }

    private boolean advanceMaturity(TreeCandidate candidate, TreeDna dna, TreeEvolutionConfig currentConfig, String gate) {
        TreeMaturityStage next = dna.maturityStage().next();
        if (dna.maturityStage().ordinal() >= currentConfig.maximumStage().ordinal()
                || next.ordinal() > currentConfig.maximumStage().ordinal()) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "state.stage-hold",
                    dna.key() + " " + dna.maturityStage() + "->" + next + " blocked gate=" + gate
                            + " maximum-stage=" + currentConfig.maximumStage()
                            + " ## the configured fancy-tree ceiling prevents giant architecture from starting");
            return false;
        }
        if (!ensureOriginalShapeSnapshot(candidate, dna,
                "before-stage-" + next.name().toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        return dna.advanceMaturity();
    }

    boolean ensureOriginalShapeSnapshot(TreeCandidate candidate,
            TreeDna dna, String reason) {
        if (dna.hasOriginalShapeSnapshot()
                && dna.originalShapeCaptureIsCurrent()) {
            return true;
        }
        // ## Snapshot ownership always uses the authoritative full-crown walk.
        // A six-face candidate can be complete for scheduling while still missing
        // diagonally attached foliage from a fancy vanilla tree.
        TreeCandidate source =
                candidateDiscovery.build(candidate.baseBlock(), true).orElse(null);
        if (source == null || !source.ownershipComplete()) {
            diagnostics.recordReject(configSupplier.get(), "original-shape-incomplete",
                    dna.key() + " reason=" + reason);
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "gate.original-shape-incomplete",
                    "tree=" + dna.key() + " reason=" + reason
                            + " ## evolution waits until the bounded ownership walk can save the whole source crown");
            return false;
        }
        Set<String> logs = originalLogKeys(source, dna);
        Set<String> leaves = originalLeafKeys(source, dna);
        if (logs.isEmpty() || leaves.isEmpty()) {
            diagnostics.recordReject(configSupplier.get(), "original-shape-empty",
                    dna.key() + " reason=" + reason);
            return false;
        }
        boolean expanding = dna.hasOriginalShapeSnapshot();
        if (expanding) {
            dna.expandOriginalShape(logs, leaves);
        } else {
            dna.captureOriginalShape(logs, leaves);
        }
        repository.markDirty("original shape capture " + dna.key());
        repository.save(configSupplier.get());
        plugin.pathDebug().trace(plugin, "tree-evolution",
                expanding
                        ? "state.original-shape-expand"
                        : "state.original-shape-capture",
                "tree=" + dna.key() + " blocks="
                        + dna.originalShapeBlockCount()
                        + " scan-logs=" + logs.size()
                        + " scan-leaves=" + leaves.size()
                        + " unresolved="
                        + dna.unresolvedOriginalShapeLeafCount()
                        + " reason=" + reason
                        + " ## exact full-crown source leaves remain authoritative until target completion and pruning both pass");
        return true;
    }

    Set<String> originalLogKeys(TreeCandidate candidate, TreeDna dna) {
        Set<String> logs = new HashSet<>();
        for (String blockKey : candidate.naturalKeys()) {
            blockFromKey(candidate.world(), blockKey)
                    .filter(block -> block.getType()
                            == dna.species().logMaterial())
                    .ifPresent(block -> logs.add(blockKey));
        }
        return logs;
    }

    Set<String> originalLeafKeys(TreeCandidate candidate, TreeDna dna) {
        Set<String> leaves = new HashSet<>();
        for (String blockKey : candidate.naturalKeys()) {
            blockFromKey(candidate.world(), blockKey)
                    .filter(block -> block.getType()
                            == dna.species().leafMaterial())
                    .ifPresent(block -> leaves.add(blockKey));
        }
        return leaves;
    }

    int liveTrunkHeight(World world, TreeDna dna) {
        int top = dna.baseY();
        int misses = 0;
        int maxY = Math.min(world.getMaxHeight() - 1, dna.baseY() + dna.targetHeight() + 16);
        for (int y = dna.baseY(); y <= maxY; y++) {
            if (hasLiveTrunkAt(world, dna, y)) {
                top = y;
                misses = 0;
            } else if (++misses >= 3) {
                break;
            }
        }
        return top - dna.baseY() + 1;
    }

    private boolean hasLiveTrunkAt(World world, TreeDna dna, int y) {
        int width = TreeSpeciesStageStyle.trunkWidthAt(dna, y);
        int radius = Math.max(1, width / 2 + 1);
        int centerX = dna.trunkXAt(y);
        int centerZ = dna.trunkZAt(y);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (world.getBlockAt(centerX + x, y, centerZ + z).getType() == dna.species().logMaterial()) {
                    return true;
                }
            }
        }
        return false;
    }


    private Optional<Block> blockFromKey(World world, String key) {
        String[] parts = key.split(":");
        if (parts.length < 4) {
            return Optional.empty();
        }
        try {
            int x = Integer.parseInt(parts[parts.length - 3]);
            int y = Integer.parseInt(parts[parts.length - 2]);
            int z = Integer.parseInt(parts[parts.length - 1]);
            return Optional.of(world.getBlockAt(x, y, z));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
