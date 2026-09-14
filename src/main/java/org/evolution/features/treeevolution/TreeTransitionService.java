package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Biome;
import org.evolution.coreparts.EvolutionPlugin;

/**
 * ## Owns atomic transition reconciliation and source-crown retirement.
 *
 * <p>Planned replacement dependencies must pass the placement service before a
 * source leaf is removed. Neighboring planned crowns outrank this tree, and the
 * source ledger stays open until every authoritative leaf is resolved.</p>
 */
final class TreeTransitionService {
    private final EvolutionPlugin plugin;
    private final TreeEvolutionDiagnostics diagnostics;
    private final TreeDnaRepository repository;
    private final TreePlanAuditService planAudit;
    private final TreePlacementService placementService;
    private final ConcurrentMap<String, TreeDna> treeDna;
    private final AtomicLong changedBlocks;

    TreeTransitionService(
            EvolutionPlugin plugin,
            TreeEvolutionDiagnostics diagnostics,
            TreeDnaRepository repository,
            TreePlanAuditService planAudit,
            TreePlacementService placementService,
            AtomicLong changedBlocks
    ) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
        this.repository = repository;
        this.planAudit = planAudit;
        this.placementService = placementService;
        this.treeDna = repository.records();
        this.changedBlocks = changedBlocks;
    }

    Optional<TreeOwnershipRoleReconciliationPolicy.Repair>
            findOwnershipRoleRepair(
                    TreeCandidate candidate,
                    TreeDna dna,
                    CachedTreePlan cachedPlan,
                    TreeEvolutionConfig currentConfig
            ) {
        Set<String> receipts = new HashSet<>(dna.evolvedShapeLogs());
        receipts.addAll(dna.evolvedShapeLeaves());
        Map<String, TreeOwnershipRoleReconciliationPolicy.Role> liveRoles =
                new HashMap<>();
        for (String receipt : receipts.stream().sorted().toList()) {
            Optional<Block> live = blockFromKey(candidate.world(), receipt);
            if (live.isEmpty() || !isOwnedLoaded(live.get())
                    || !plugin.canEvolveAt(
                            live.get().getLocation(), "tree-evolution")) {
                continue;
            }
            if (live.get().getType() == dna.species().logMaterial()) {
                liveRoles.put(
                        receipt,
                        TreeOwnershipRoleReconciliationPolicy.Role.WOOD);
            } else if (live.get().getType()
                    == dna.species().leafMaterial()) {
                liveRoles.put(
                        receipt,
                        TreeOwnershipRoleReconciliationPolicy.Role.CANOPY);
            }
        }
        Optional<TreeOwnershipRoleReconciliationPolicy.Repair> repair =
                TreeOwnershipRoleReconciliationPolicy.next(
                        dna.evolvedShapeLogs(), dna.evolvedShapeLeaves(),
                        liveRoles, cachedPlan.orderedBlocks());
        repair.ifPresent(value -> plugin.pathDebug().traceSampled(
                plugin, "tree-evolution",
                "constructor.ownership-role-mismatch",
                "tree=" + dna.key() + " " + value.marker()
                        + " ## receipt role is repaired before any physical prune"));
        return repair;
    }

    boolean applyOwnershipRoleRepair(
            TreeDna dna,
            TreeOwnershipRoleReconciliationPolicy.Repair repair,
            TreeEvolutionConfig currentConfig
    ) {
        if (!dna.reconcileEvolvedRole(
                repair.blockKey(), repair.liveRole())) {
            return false;
        }
        repository.markDirty(
                "reconciled evolved ownership role " + repair.blockKey());
        repository.save(currentConfig);
        planAudit.invalidateLiveAnalysis(dna.key());
        plugin.pathDebug().trace(
                plugin, "tree-evolution",
                "constructor.ownership-role-reconciled",
                "tree=" + dna.key() + " " + repair.marker()
                        + " physical-change=false"
                        + " ## live material was already correct; only its stale DNA role moved");
        return true;
    }

    Optional<PlannedTarget> readyTransitionBlocker(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig,
            NeighborProtection neighborProtection
    ) {
        List<Block> blockers = findStaleCanopyLeaves(
                candidate, dna, cachedPlan.orderedBlocks(), 64,
                currentConfig, true, neighborProtection.canopyKeys());
        int size = Math.max(1, cachedPlan.orderedBlocks().size());
        for (Block blocker : blockers) {
            String coordinateKey = blocker.getX() + ":" + blocker.getY()
                    + ":" + blocker.getZ();
            PlannedTreeBlock planned = cachedPlan.blocksByKey().get(coordinateKey);
            if (planned == null
                    || (planned.role() != TreeBlockRole.TRUNK
                            && planned.role() != TreeBlockRole.BRANCH)) {
                continue;
            }
            if (!placementService.hasPreplannedBranchEnvelope(
                    dna, planned, cachedPlan, currentConfig)) {
                continue;
            }
            TreeGrowthIntent intent = planned.role() == TreeBlockRole.TRUNK
                    ? TreeGrowthIntent.HEIGHT : TreeGrowthIntent.BRANCH;
            if (!placementService.isDependencyReady(candidate, dna, blocker, planned,
                    intent, currentConfig, false)
                    || !placementService.canPlace(candidate, dna, blocker, planned,
                            currentConfig)) {
                continue;
            }
            int index = cachedPlan.orderedBlocks().indexOf(planned);
            int nextCursor = index < 0 ? dna.planCursor()
                    : (index + 1) % size;
            return Optional.of(new PlannedTarget(
                    planned, blocker, nextCursor, 0.0D,
                    "constructor.atomic-transition-blocker"));
        }
        return Optional.empty();
    }

    boolean replaceTransitionBlocker(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig,
            PlannedTarget transitionBlocker
    ) {
        PlannedTreeBlock planned = transitionBlocker.block();
        Block target = transitionBlocker.target();
        if (target.getType() != dna.species().leafMaterial()
                || !placementService.canPlace(candidate, dna, target, planned, currentConfig)) {
            return false;
        }
        TreeGrowthIntent intent = switch (planned.role()) {
            case TRUNK, ROOT -> TreeGrowthIntent.HEIGHT;
            case BRANCH -> TreeGrowthIntent.BRANCH;
            case CANOPY -> TreeGrowthIntent.CANOPY;
            default -> TreeGrowthIntent.REPAIR;
        };
        Material previousMaterial = target.getType();
        placementService.place(target, planned);
        if (dna.markEvolvedBlock(keyFor(target), planned.role())) {
            repository.markDirty("recorded evolved transition " + planned.role()
                    + " " + keyFor(target));
        }
        dna.markPlacedForIntent(intent, transitionBlocker.nextCursor());
        changedBlocks.incrementAndGet();
                planAudit.invalidateLiveAnalysis(dna.key());
        diagnostics.recordPlaced(
                plugin, currentConfig, dna, target, previousMaterial, planned,
                org.evolution.features.treeevolution.constructor
                        .TreeConstructionSubrule.READY_SOURCE_LEAF_BLOCKER);
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "constructor.atomic-transition-blocker",
                "[CONSTRUCTOR][REPLACE_TRANSITION_BLOCKER]"
                        + "[TRANSITION_RECONCILER] tree=" + dna.key()
                        + " role=" + planned.role()
                        + " at=" + format(target)
                        + " ## source leaf became ready planned wood in one world change");
        return true;
    }

    boolean reconcileSourceLeafLedger(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig,
            NeighborProtection neighborProtection
    ) {
        if (!dna.hasOriginalShapeSnapshot()) {
            return false;
        }
        int adopted = 0;
        int absent = 0;
        int releasedToNeighbor = 0;
        Set<String> protectedCanopyKeys =
                neighborProtection.canopyKeys();
        String firstUnresolved = null;
        for (String sourceLeafKey : dna.originalShapeLeaves()) {
            if (dna.retiredOriginalShapeLeaves().contains(sourceLeafKey)
                    || dna.countsAsEvolvedLeaf(sourceLeafKey)) {
                continue;
            }
            Optional<Block> sourceLeaf =
                    blockFromKey(candidate.world(), sourceLeafKey);
            if (sourceLeaf.isEmpty()) {
                continue;
            }
            Block block = sourceLeaf.get();
            if (!planAudit.isReadableTreeCoordinate(
                    candidate.world(), block.getX(), block.getZ())) {
                continue;
            }
            if (block.getType() != dna.species().leafMaterial()) {
                if (dna.markOriginalShapeLeafRetired(sourceLeafKey)) {
                    absent++;
                }
                continue;
            }
            PlannedTreeBlock planned = cachedPlan.blocksByKey().get(
                    block.getX() + ":" + block.getY() + ":" + block.getZ());
            if (planned != null
                    && planned.role() == TreeBlockRole.CANOPY
                    && planned.material() == block.getType()
                    && dna.markEvolvedLeaf(sourceLeafKey)) {
                adopted++;
                continue;
            }
            String coordinateKey = block.getX() + ":" + block.getY()
                    + ":" + block.getZ();
            if (protectedCanopyKeys.contains(coordinateKey)
                    && dna.markOriginalShapeLeafRetired(sourceLeafKey)) {
                // ## Shared crown evidence transfers to the neighboring plan
                // without deleting the live block or holding this tree open.
                releasedToNeighbor++;
                continue;
            }
            if (firstUnresolved == null) {
                firstUnresolved = sourceLeafKey;
            }
        }
        if (adopted <= 0 && absent <= 0 && releasedToNeighbor <= 0) {
            return false;
        }
        repository.markDirty("source leaf ledger reconcile " + dna.key());
        planAudit.invalidateLiveAnalysis(dna.key());
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "state.source-leaf-reconcile",
                "tree=" + dna.key()
                        + " adopted-target=" + adopted
                        + " already-absent=" + absent
                        + " released-to-neighbor=" + releasedToNeighbor
                        + " unresolved="
                        + dna.unresolvedOriginalShapeLeafCount()
                        + (firstUnresolved == null
                                ? ""
                                : " first-unresolved=" + firstUnresolved)
                        + " ## source evidence remains persisted until every original leaf is adopted or retired");
        return true;
    }
    List<Block> findRetiredCanopyLeaves(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            int limit,
            TreeEvolutionConfig currentConfig,
            NeighborProtection neighborProtection
    ) {
        List<Block> stale = findStaleCanopyLeaves(
                candidate, dna, cachedPlan.orderedBlocks(),
                Math.max(limit * 4, 16), currentConfig, false,
                neighborProtection.canopyKeys());
        List<Block> retired = new ArrayList<>();
        for (Block leaf : stale) {
            PlannedTreeBlock planned = cachedPlan.blocksByKey().get(
                    leaf.getX() + ":" + leaf.getY() + ":" + leaf.getZ());
            if (planned != null && (planned.role() == TreeBlockRole.TRUNK
                    || planned.role() == TreeBlockRole.BRANCH
                    || planned.role() == TreeBlockRole.ROOT)) {
                continue;
            }
            retired.add(leaf);
            if (retired.size() >= limit) {
                break;
            }
        }
        return List.copyOf(retired);
    }

    boolean hasObsoleteEvolvedReceipt(
            TreeDna dna, CachedTreePlan cachedPlan) {
        return dna.evolvedShapeLeaves().stream().anyMatch(key ->
                        !matchesCurrentTarget(key, true, cachedPlan))
                || dna.evolvedShapeLogs().stream().anyMatch(key ->
                        !matchesCurrentTarget(key, false, cachedPlan));
    }

    Optional<ObsoleteEvolvedBlock> findObsoleteEvolvedBlock(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig,
            NeighborProtection neighborProtection
    ) {
        if (!candidate.ownershipComplete()) {
            return Optional.empty();
        }
        Set<String> protectedNeighborBody =
                neighborProtection.bodyKeys();
        Optional<ObsoleteEvolvedBlock> staleLeaf = findObsoleteEvolvedBlock(
                candidate, dna, cachedPlan, currentConfig,
                dna.evolvedShapeLeaves(), protectedNeighborBody, true);
        return staleLeaf.isPresent() ? staleLeaf
                : findObsoleteEvolvedBlock(
                        candidate, dna, cachedPlan, currentConfig,
                        dna.evolvedShapeLogs(), protectedNeighborBody, false);
    }

    Optional<ObsoleteEvolvedBlock> findConflictingEvolvedTargetBlock(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig,
            NeighborProtection neighborProtection
    ) {
        if (!candidate.ownershipComplete()) {
            return Optional.empty();
        }
        Set<String> protectedNeighborBody = neighborProtection.bodyKeys();
        Optional<ObsoleteEvolvedBlock> woodConflict =
                findConflictingEvolvedTargetBlock(
                        candidate, dna, cachedPlan, currentConfig,
                        dna.evolvedShapeLogs(), protectedNeighborBody, false);
        return woodConflict.isPresent() ? woodConflict
                : findConflictingEvolvedTargetBlock(
                        candidate, dna, cachedPlan, currentConfig,
                        dna.evolvedShapeLeaves(), protectedNeighborBody, true);
    }

    Optional<ObsoleteEvolvedBlock> findConflictSafeRetirement(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig,
            ObsoleteEvolvedBlock conflict,
            NeighborProtection neighborProtection
    ) {
        if (conflict.leaf()) {
            return Optional.of(conflict);
        }
        Set<String> protectedNeighborBody = neighborProtection.bodyKeys();
        Set<String> liveWood = new HashSet<>();
        Set<String> evolvedWood = new HashSet<>();
        Set<String> evolvedCanopy = new HashSet<>();
        Set<String> protectedCurrentTargets = new HashSet<>();

        Set<String> allWoodReceipts = new HashSet<>(
                dna.originalShapeLogs());
        allWoodReceipts.addAll(dna.evolvedShapeLogs());
        for (String receipt : allWoodReceipts) {
            blockFromKey(candidate.world(), receipt)
                    .filter(block -> block.getType()
                            == dna.species().logMaterial())
                    .filter(this::isOwnedLoaded)
                    .ifPresent(block -> liveWood.add(receipt));
        }
        for (String receipt : dna.evolvedShapeLogs()) {
            if (protectedNeighborBody.contains(coordinateKey(receipt))) {
                continue;
            }
            blockFromKey(candidate.world(), receipt)
                    .filter(block -> block.getType()
                            == dna.species().logMaterial())
                    .filter(this::isOwnedLoaded)
                    .filter(block -> plugin.canEvolveAt(
                            block.getLocation(), "tree-evolution"))
                    .ifPresent(block -> evolvedWood.add(receipt));
            if (matchesCurrentTarget(receipt, false, cachedPlan)) {
                protectedCurrentTargets.add(receipt);
            }
        }
        for (String receipt : dna.evolvedShapeLeaves()) {
            if (protectedNeighborBody.contains(coordinateKey(receipt))) {
                continue;
            }
            blockFromKey(candidate.world(), receipt)
                    .filter(block -> block.getType()
                            == dna.species().leafMaterial())
                    .filter(this::isOwnedLoaded)
                    .filter(block -> plugin.canEvolveAt(
                            block.getLocation(), "tree-evolution"))
                    .ifPresent(block -> evolvedCanopy.add(receipt));
            if (matchesCurrentTarget(receipt, true, cachedPlan)) {
                protectedCurrentTargets.add(receipt);
            }
        }

        String conflictKey = keyFor(conflict.block());
        return TreeConflictRetirementPolicy.next(
                        dna, liveWood, evolvedWood,
                        evolvedCanopy, protectedCurrentTargets,
                        conflictKey)
                .flatMap(selected -> {
                    if (selected.equals(conflictKey)) {
                        return Optional.of(conflict);
                    }
                    boolean leaf = evolvedCanopy.contains(selected);
                    return blockFromKey(candidate.world(), selected)
                            .map(block -> new ObsoleteEvolvedBlock(
                                    block, leaf,
                                    leaf
                                            ? "conflict-dependent-canopy"
                                            : "conflict-dependent-wood"));
                });
    }

    private Optional<ObsoleteEvolvedBlock>
            findConflictingEvolvedTargetBlock(
                    TreeCandidate candidate,
                    TreeDna dna,
                    CachedTreePlan cachedPlan,
                    TreeEvolutionConfig currentConfig,
                    Set<String> receipts,
                    Set<String> protectedNeighborBody,
                    boolean leaf
            ) {
        for (String receipt : receipts.stream().sorted().toList()) {
            String coordinate = coordinateKey(receipt);
            PlannedTreeBlock target =
                    cachedPlan.blocksByKey().get(coordinate);
            if (target == null
                    || protectedNeighborBody.contains(coordinate)
                    || matchesCurrentTarget(
                            receipt, leaf, cachedPlan)) {
                continue;
            }
            Optional<Block> block = blockFromKey(
                    candidate.world(), receipt);
            Material expected = leaf
                    ? dna.species().leafMaterial()
                    : dna.species().logMaterial();
            if (block.isPresent()
                    && block.get().getType() == expected
                    && isOwnedLoaded(block.get())
                    && plugin.canEvolveAt(
                            block.get().getLocation(),
                            "tree-evolution")) {
                return Optional.of(new ObsoleteEvolvedBlock(
                        block.get(), leaf,
                        "active-target-role-conflict-"
                                + target.role().name().toLowerCase()));
            }
        }
        return Optional.empty();
    }

    DisconnectedEvolvedState findDisconnectedEvolvedState(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig,
            NeighborProtection neighborProtection
    ) {
        if (!candidate.ownershipComplete()) {
            return new DisconnectedEvolvedState(
                    Optional.empty(),
                    new TreeTargetOwnershipRepairPolicy.Analysis(
                            List.of(), Optional.empty(), 0, 0, 0));
        }
        Set<String> protectedNeighborBody =
                neighborProtection.bodyKeys();
        Set<String> liveOwnedWood = new HashSet<>();
        Set<String> ownedWoodReceipts = new HashSet<>(
                dna.originalShapeLogs());
        ownedWoodReceipts.addAll(dna.evolvedShapeLogs());
        for (String receipt : ownedWoodReceipts) {
            blockFromKey(candidate.world(), receipt)
                    .filter(block -> block.getType()
                            == dna.species().logMaterial())
                    .filter(this::isOwnedLoaded)
                    .ifPresent(block -> liveOwnedWood.add(receipt));
        }
        Set<String> livePlannedWood = new HashSet<>();
        Set<String> liveOwnedCanopy = new HashSet<>();
        for (String receipt : dna.evolvedShapeLeaves()) {
            blockFromKey(candidate.world(), receipt)
                    .filter(block -> block.getType()
                            == dna.species().leafMaterial())
                    .filter(this::isOwnedLoaded)
                    .ifPresent(block -> liveOwnedCanopy.add(receipt));
        }
        Set<String> livePlannedCanopy = new HashSet<>();
        for (PlannedTreeBlock planned : cachedPlan.orderedBlocks()) {
            boolean wood = planned.role() == TreeBlockRole.TRUNK
                    || planned.role() == TreeBlockRole.BRANCH
                    || planned.role() == TreeBlockRole.ROOT;
            boolean canopy = planned.role() == TreeBlockRole.CANOPY;
            if (!wood && !canopy) {
                continue;
            }
            Block block = candidate.world().getBlockAt(
                    planned.x(), planned.y(), planned.z());
            if (isOwnedLoaded(block)
                    && block.getType() == planned.material()) {
                if (wood) {
                    livePlannedWood.add(keyFor(block));
                } else {
                    livePlannedCanopy.add(keyFor(block));
                }
            }
        }
        TreeTargetOwnershipRepairPolicy.Analysis targetRepair =
                TreeTargetOwnershipRepairPolicy.inspect(
                        dna, cachedPlan.orderedBlocks(), liveOwnedWood,
                        livePlannedWood, liveOwnedCanopy,
                        livePlannedCanopy, protectedNeighborBody);
        Set<String> rootedLiveWood =
                TreeWoodOwnershipGraph.connectedToRoot(
                        dna, liveOwnedWood);
        if (targetRepair.required()) {
            diagnostics.recordTargetOwnershipAnalysis(
                    currentConfig, dna, targetRepair);
            String detail = targetRepair.repair()
                    .map(TreeTargetOwnershipRepairPolicy.Repair::marker)
                    .orElse("[REPAIR-BRIDGE][MISSING] orphan="
                            + targetRepair.disconnectedTargetKeys()
                                    .getFirst()
                            + " bridge=none rooted="
                            + rootedLiveWood.size());
            plugin.pathDebug().traceSampled(
                    plugin, "tree-evolution",
                    "constructor.disconnected-target-analysis",
                    "tree=" + dna.key() + " " + detail
                            + " ## live target ownership is reconciled before any visual mutation");
        }
        ObsoleteEvolvedBlock obsolete = null;
        for (String receipt
                : dna.evolvedShapeLogs().stream()
                        .sorted(TreeObsoleteRetirementPolicy
                                .outermostFirst(dna))
                        .toList()) {
            if (protectedNeighborBody.contains(
                            coordinateKey(receipt))) {
                continue;
            }
            Optional<Block> block =
                    blockFromKey(candidate.world(), receipt);
            if (block.isEmpty()
                    || block.get().getType()
                            != dna.species().logMaterial()
                    || !isOwnedLoaded(block.get())
                    || !plugin.canEvolveAt(
                            block.get().getLocation(),
                            "tree-evolution")) {
                continue;
            }
            if (rootedLiveWood.contains(receipt)) {
                continue;
            }
            if (targetRepair.disconnectedTargetKeys()
                    .contains(receipt)) {
                // ## Keep the visible target. TREE_11 adopts or places the
                // exact rootward bridge returned by the shared repair policy.
                continue;
            }
            if (obsolete == null) {
                obsolete = new ObsoleteEvolvedBlock(
                        block.get(), false,
                        "disconnected-outside-target");
            }
        }
        for (String receipt
                : dna.evolvedShapeLeaves().stream().sorted().toList()) {
            if (candidate.naturalKeys().contains(receipt)
                    || protectedNeighborBody.contains(
                            coordinateKey(receipt))) {
                continue;
            }
            Optional<Block> block =
                    blockFromKey(candidate.world(), receipt);
            if (block.isEmpty()
                    || block.get().getType()
                            != dna.species().leafMaterial()
                    || !isOwnedLoaded(block.get())
                    || !plugin.canEvolveAt(
                            block.get().getLocation(),
                            "tree-evolution")) {
                continue;
            }
            if (matchesCurrentTarget(
                    receipt, true, cachedPlan)) {
                // ## Planned detached canopy is bridged by normal dependency
                // repair; deleting and replacing it would create visible churn.
            } else if (obsolete == null) {
                obsolete = new ObsoleteEvolvedBlock(
                        block.get(), true,
                        "disconnected-canopy-outside-target");
            }
        }
        return new DisconnectedEvolvedState(
                Optional.ofNullable(obsolete), targetRepair);
    }

    boolean applyDisconnectedTargetRepair(
            TreeCandidate candidate,
            TreeDna dna,
            TreeTargetOwnershipRepairPolicy.Repair repair,
            TreeEvolutionConfig currentConfig
    ) {
        PlannedTreeBlock planned = repair.block();
        Block target = candidate.world().getBlockAt(
                planned.x(), planned.y(), planned.z());
        String targetKey = keyFor(target);
        if (!isOwnedLoaded(target)
                || !plugin.canEvolveAt(
                        target.getLocation(), "tree-evolution")) {
            plugin.pathDebug().failure(
                    plugin, "tree-evolution",
                    "constructor.repair-bridge-region-gate",
                    "tree=" + dna.key() + " " + repair.marker());
            return false;
        }
        if (repair.action()
                == TreeTargetOwnershipRepairPolicy.Action
                        .ADOPT_LIVE_TARGET) {
            if (target.getType() != planned.material()
                    || !dna.markEvolvedBlock(
                            targetKey, planned.role())) {
                return false;
            }
            repository.markDirty(
                    "adopted live target ownership " + targetKey);
            repository.save(currentConfig);
            planAudit.invalidateLiveAnalysis(dna.key());
            plugin.pathDebug().trace(
                    plugin, "tree-evolution",
                    "constructor.repair-ownership-adopt",
                    "tree=" + dna.key() + " " + repair.marker()
                            + " material=" + target.getType()
                            + " ## no block was deleted or replaced; only the missing DNA receipt was restored");
            return true;
        }

        TreeGrowthIntent intent = switch (planned.role()) {
            case TRUNK, ROOT -> TreeGrowthIntent.HEIGHT;
            case BRANCH -> TreeGrowthIntent.BRANCH;
            case CANOPY -> TreeGrowthIntent.CANOPY;
            default -> TreeGrowthIntent.REPAIR;
        };
        if (target.getType() == planned.material()) {
            return dna.markEvolvedBlock(targetKey, planned.role());
        }
        boolean dependencyReady = placementService.isDependencyReady(
                candidate, dna, target, planned,
                intent, currentConfig, true);
        boolean ownedWoodToCanopy = isOwnedWoodToCanopySwap(
                dna, target, targetKey, planned);
        boolean placementAllowed = dependencyReady
                && (ownedWoodToCanopy
                        || placementService.canPlace(
                                candidate, dna, target, planned,
                                currentConfig));
        if (!placementAllowed) {
            plugin.pathDebug().failure(
                    plugin, "tree-evolution",
                    "constructor.repair-bridge-blocked",
                    "tree=" + dna.key() + " " + repair.marker()
                            + " live=" + target.getType()
                            + " dependency-ready=" + dependencyReady
                            + " owned-wood-to-canopy="
                            + ownedWoodToCanopy
                            + " source-leaf="
                            + dna.wasOriginalShapeLeaf(targetKey)
                            + " ## distinguishes a real dependency wait from a constructor role conflict");
            return false;
        }
        Material previousMaterial = target.getType();
        boolean sourceLeafToWood = isSourceLeafToWoodSwap(
                dna, target, targetKey, planned);
        placementService.place(target, planned);
        dna.markEvolvedBlock(targetKey, planned.role());
        if (sourceLeafToWood) {
            // ## The physical leaf and its source role disappear together.
            // Leaving the source receipt unresolved would reopen this exact
            // coordinate during final reconciliation.
            dna.markOriginalShapeLeafRetired(targetKey);
        }
        repository.markDirty("placed ownership bridge " + targetKey);
        planAudit.invalidateLiveAnalysis(dna.key());
        changedBlocks.incrementAndGet();
        diagnostics.recordPlaced(
                plugin, currentConfig, dna, target, previousMaterial, planned,
                org.evolution.features.treeevolution.constructor
                        .TreeConstructionSubrule
                        .DISCONNECTED_TARGET_REPAIR);
        plugin.pathDebug().trace(
                plugin, "tree-evolution",
                 "constructor.repair-bridge-place",
                 "tree=" + dna.key() + " " + repair.marker()
                         + " role-swap="
                         + (ownedWoodToCanopy ? "source-wood-to-canopy"
                                 : sourceLeafToWood
                                         ? "source-leaf-to-wood" : "none")
                         + " ## missing rootward target was placed through normal Folia and protection gates");
        return true;
    }

    private boolean isOwnedWoodToCanopySwap(
            TreeDna dna,
            Block target,
            String targetKey,
            PlannedTreeBlock planned
    ) {
        // ## A captured log is authoritative tree state, not a player block.
        // The transition constructor may reshape it directly into planned
        // canopy after dependency, region, and protection checks have passed.
        return planned.role() == TreeBlockRole.CANOPY
                && target.getType() == dna.species().logMaterial()
                && dna.countsAsOwnedLog(targetKey);
    }

    private boolean isSourceLeafToWoodSwap(
            TreeDna dna,
            Block target,
            String targetKey,
            PlannedTreeBlock planned
    ) {
        boolean plannedWood = planned.role() == TreeBlockRole.TRUNK
                || planned.role() == TreeBlockRole.BRANCH
                || planned.role() == TreeBlockRole.ROOT;
        return plannedWood
                && target.getType() == dna.species().leafMaterial()
                && dna.wasOriginalShapeLeaf(targetKey);
    }

    boolean retireObsoleteEvolvedBlock(
            TreeDna dna,
            ObsoleteEvolvedBlock obsolete,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig
    ) {
        Block block = obsolete.block();
        String blockKey = keyFor(block);
        Material expected = obsolete.leaf()
                ? dna.species().leafMaterial()
                : dna.species().logMaterial();
        if (block.getType() != expected
                || !isOwnedLoaded(block)
                || !plugin.canEvolveAt(
                        block.getLocation(), "tree-evolution")) {
            return false;
        }
        TreeConstructionMutationPolicy.Retirement retirement =
                TreeConstructionMutationPolicy.retirement(
                        dna, cachedPlan, blockKey,
                        block.getType(), obsolete.leaf());
        if (!retirement.allowed()) {
            plugin.pathDebug().failure(
                    plugin, "tree-evolution",
                    "gate.monotonic-current-target-retirement",
                    "tree=" + dna.key() + " block=" + format(block)
                            + " route-reason=" + obsolete.reason()
                            + " policy=" + retirement.reason()
                            + " ## a current-plan evolved voxel cannot be deleted by cleanup");
            diagnostics.recordReject(
                    currentConfig,
                    "monotonic-current-target-retirement",
                    dna.key() + " " + format(block));
            return false;
        }
        Material previousMaterial = block.getType();
        boolean forgotten = obsolete.leaf()
                ? dna.forgetEvolvedLeaf(blockKey)
                : dna.forgetEvolvedLog(blockKey);
        if (!forgotten) {
            return false;
        }
        if (obsolete.releaseOnly()) {
            // ## A shared coordinate belongs to the neighboring plan. Retire
            // this DNA's stale receipt without deleting a valid world block,
            // preventing ownership overlap from appearing as final drift.
            repository.markDirty("released neighboring ownership " + blockKey);
            planAudit.invalidateLiveAnalysis(dna.key());
            plugin.pathDebug().trace(plugin, "tree-evolution",
                    "constructor.release-neighbor-ownership",
                    "tree=" + dna.key() + " block=" + format(block)
                            + " kind=" + (obsolete.leaf() ? "leaf" : "wood")
                            + " ## shared voxel remains for the neighboring target");
            return true;
        }
        block.setType(Material.AIR, false);
        repository.markDirty("obsolete evolved structure " + blockKey);
        planAudit.invalidateLiveAnalysis(dna.key());
        changedBlocks.incrementAndGet();
        PlannedTreeBlock formerPlan = cachedPlan.blocksByKey().get(
                block.getX() + ":" + block.getY() + ":" + block.getZ());
        diagnostics.recordRemoved(
                plugin, currentConfig, dna, block, previousMaterial,
                obsolete.leaf() ? TreeBlockRole.CANOPY
                        : TreeBlockRole.BRANCH,
                formerPlan == null ? TreePlacementAugment.UNCLASSIFIED
                        : formerPlan.augment(),
                org.evolution.features.treeevolution.constructor
                        .TreeConstructionSubrule
                        .OBSOLETE_EVOLVED_STRUCTURE,
                obsolete.reason());
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "constructor.prune-obsolete-evolved-structure",
                "[CONSTRUCTOR][PRUNE_RETIRED_CROWN]"
                        + "[OBSOLETE_EVOLVED_STRUCTURE]"
                        + "[TRANSITION_RECONCILER] tree=" + dna.key()
                        + " removed=" + format(block)
                        + " kind=" + (obsolete.leaf() ? "leaf" : "wood")
                        + " reason=" + obsolete.reason()
                        + " ## completion is bidirectional: required target"
                        + " blocks exist and obsolete plugin-owned voxels retire");
        return true;
    }

    boolean retirePreconditionDependency(
            TreeDna dna,
            CachedTreePlan cachedPlan,
            World world,
            String dependencyKey,
            TreeEvolutionConfig currentConfig
    ) {
        boolean leaf = dna.evolvedShapeLeaves().contains(dependencyKey);
        boolean wood = dna.evolvedShapeLogs().contains(dependencyKey);
        if (!leaf && !wood) {
            return false;
        }
        return blockFromKey(world, dependencyKey)
                .map(block -> retireObsoleteEvolvedBlock(
                        dna,
                        new ObsoleteEvolvedBlock(
                                block, leaf,
                                "terminal-dependent-"
                                        + (leaf ? "canopy" : "wood")),
                        cachedPlan, currentConfig))
                .orElse(false);
    }

    private Optional<ObsoleteEvolvedBlock> findObsoleteEvolvedBlock(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig,
            Set<String> receipts,
            Set<String> protectedNeighborBody,
            boolean leaf
    ) {
        var orderedReceipts = leaf
                ? receipts.stream().sorted().toList()
                : receipts.stream()
                        .sorted(TreeObsoleteRetirementPolicy
                                .outermostFirst(dna))
                        .toList();
        for (String receipt : orderedReceipts) {
            boolean rootedInCandidate =
                    candidate.naturalKeys().contains(receipt);
            boolean matchesTarget =
                    matchesCurrentTarget(receipt, leaf, cachedPlan);
            TreeObsoleteReceiptPolicy.Disposition disposition =
                    TreeObsoleteReceiptPolicy.classify(
                            matchesTarget,
                            protectedNeighborBody.contains(
                                    coordinateKey(receipt)),
                            rootedInCandidate);
            if (disposition
                    == TreeObsoleteReceiptPolicy.Disposition
                            .KEEP_CURRENT_TARGET) {
                // ## Current-target connectivity belongs exclusively to the
                // exact receipt/root graph in TREE_11. The candidate scanner
                // is bounded and may omit a valid distant limb; using that
                // omission here made TREE_60 delete what TREE_50 rebuilt.
                continue;
            }
            Optional<Block> block = blockFromKey(
                    candidate.world(), receipt);
            if (block.isEmpty()
                    || !isOwnedLoaded(block.get())
                    || !plugin.canEvolveAt(
                            block.get().getLocation(), "tree-evolution")) {
                continue;
            }
            Material expected = leaf
                    ? dna.species().leafMaterial()
                    : dna.species().logMaterial();
            if (block.get().getType() == expected) {
                if (disposition
                        == TreeObsoleteReceiptPolicy.Disposition
                                .RELEASE_TO_NEIGHBOR_TARGET) {
                    return Optional.of(new ObsoleteEvolvedBlock(
                            block.get(), leaf,
                            "release-to-neighbor-target"));
                }
                String reason = disposition
                        == TreeObsoleteReceiptPolicy.Disposition
                                .RETIRE_DISCONNECTED_OUTSIDE_TARGET
                        ? "disconnected-outside-target"
                        : "outside-current-target";
                return Optional.of(new ObsoleteEvolvedBlock(
                        block.get(), leaf, reason));
            }
        }
        return Optional.empty();
    }

    // ## Build one immutable neighbor-ownership view for the complete action.
    // Every transition subrule must consume this same view so they cannot
    // disagree or repeat the same nearby-plan traversal.
    NeighborProtection neighborProtection(
            TreeCandidate candidate,
            TreeDna activeDna,
            TreeEvolutionConfig currentConfig
    ) {
        Set<String> protectedBody = new HashSet<>();
        Set<String> protectedCanopy = new HashSet<>();
        int nearbyTrees = 0;
        int plannedBlocks = 0;
        Biome planningBiome = candidate.baseBlock().getBiome();
        for (TreeDna nearbyDna : treeDna.values()) {
            if (nearbyDna.key().equals(activeDna.key())
                    || !nearbyDna.stumpPresent()
                    || !nearbyDna.worldId().equals(activeDna.worldId())
                    || Math.abs(nearbyDna.baseX() - activeDna.baseX()) > 20
                    || Math.abs(nearbyDna.baseZ() - activeDna.baseZ()) > 20
                    || Math.abs(nearbyDna.baseY() - activeDna.baseY()) > 32) {
                continue;
            }
            nearbyTrees++;
            CachedTreePlan nearbyPlan = planAudit.cachedPlan(
                    nearbyDna, planningBiome, currentConfig.rootsEnabled());
            plannedBlocks += nearbyPlan.orderedBlocks().size();
            for (PlannedTreeBlock block : nearbyPlan.orderedBlocks()) {
                int activeDistance = Math.abs(
                        block.x() - activeDna.trunkXAt(block.y()))
                        + Math.abs(block.z()
                                - activeDna.trunkZAt(block.y()));
                int nearbyDistance = Math.abs(
                        block.x() - nearbyDna.trunkXAt(block.y()))
                        + Math.abs(block.z()
                                - nearbyDna.trunkZAt(block.y()));
                if ((block.role() == TreeBlockRole.TRUNK
                        || block.role() == TreeBlockRole.BRANCH
                        || block.role() == TreeBlockRole.CANOPY
                        || block.role() == TreeBlockRole.ROOT)
                        && nearbyDistance <= activeDistance) {
                    protectedBody.add(block.key());
                }
                if (block.role() == TreeBlockRole.CANOPY
                        && TreeLeafOwnershipPolicy
                                .neighborPlanOwnsPosition(
                                        block.x(), block.z(),
                                        activeDna.trunkXAt(block.y()),
                                        activeDna.trunkZAt(block.y()),
                                        nearbyDna.trunkXAt(block.y()),
                                        nearbyDna.trunkZAt(block.y()))) {
                    protectedCanopy.add(block.key());
                }
            }
        }
        return new NeighborProtection(
                Set.copyOf(protectedBody),
                Set.copyOf(protectedCanopy),
                nearbyTrees, plannedBlocks);
    }

    private boolean matchesCurrentTarget(
            String receipt,
            boolean leaf,
            CachedTreePlan cachedPlan
    ) {
        String coordinateKey = coordinateKey(receipt);
        PlannedTreeBlock planned =
                cachedPlan.blocksByKey().get(coordinateKey);
        if (planned == null) {
            return false;
        }
        return leaf
                ? planned.role() == TreeBlockRole.CANOPY
                : planned.role() == TreeBlockRole.TRUNK
                        || planned.role() == TreeBlockRole.BRANCH
                        || planned.role() == TreeBlockRole.ROOT;
    }

    private String coordinateKey(String worldKey) {
        String[] parts = worldKey.split(":");
        if (parts.length < 4) {
            return worldKey;
        }
        return parts[parts.length - 3] + ":"
                + parts[parts.length - 2] + ":"
                + parts[parts.length - 1];
    }

    private List<Block> findStaleCanopyLeaves(TreeCandidate candidate, TreeDna dna,
            List<PlannedTreeBlock> orderedBlocks, int limit,
            TreeEvolutionConfig currentConfig, boolean woodBlockersOnly,
            Set<String> protectedCanopyKeys) {
        if (limit <= 0) {
            return List.of();
        }
        TreeCanopyTransitionPolicy policy = TreeCanopyTransitionPolicy.from(
                dna, orderedBlocks, candidate.topY());
        Map<String, Block> staleByKey = new HashMap<>();

        // ## Active-tree planned wood may replace only its own saved source leaves.
        // Neighboring planned crowns remain protected by collectStaleCanopyLeaf.
        for (PlannedTreeBlock woodTarget : policy.woodTargets()) {
            Block block = candidate.world().getBlockAt(
                    woodTarget.x(), woodTarget.y(), woodTarget.z());
            collectStaleCanopyLeaf(candidate, dna, policy,
                    protectedCanopyKeys, block, staleByKey);
        }

        if (woodBlockersOnly) {
            List<Block> blockers = new ArrayList<>(staleByKey.values());
            blockers.sort(Comparator
                    .comparingInt(Block::getY)
                    .thenComparingInt(Block::getX)
                    .thenComparingInt(Block::getZ));
            return List.copyOf(blockers.subList(
                    0, Math.min(limit, blockers.size())));
        }

        // ## Read the saved source shape directly. This catches disconnected
        // residual shelves outside the newer crown corridor without claiming
        // any leaf that appeared after this transition started.
        for (String originalLeafKey : dna.originalShapeLeaves()) {
            blockFromKey(candidate.world(), originalLeafKey)
                    .ifPresent(block -> collectStaleCanopyLeaf(
                            candidate, dna, policy, protectedCanopyKeys,
                            block, staleByKey));
        }

        List<Block> staleLeaves = new ArrayList<>(staleByKey.values());
        staleLeaves.sort((first, second) -> {
            int firstWood = policy.replacesWithWood(
                    first.getX(), first.getY(), first.getZ()) ? 0 : 1;
            int secondWood = policy.replacesWithWood(
                    second.getX(), second.getY(), second.getZ()) ? 0 : 1;
            int comparison = Integer.compare(firstWood, secondWood);
            if (comparison != 0) {
                return comparison;
            }
            int firstShelf = policy.isLegacyShelf(first.getY()) ? 0 : 1;
            int secondShelf = policy.isLegacyShelf(second.getY()) ? 0 : 1;
            comparison = Integer.compare(firstShelf, secondShelf);
            if (comparison != 0) {
                return comparison;
            }
            comparison = firstShelf == 0
                    ? Integer.compare(first.getY(), second.getY())
                    : Integer.compare(second.getY(), first.getY());
            if (comparison != 0) {
                return comparison;
            }
            int firstDistance = Math.abs(first.getX() - dna.trunkXAt(first.getY()))
                    + Math.abs(first.getZ() - dna.trunkZAt(first.getY()));
            int secondDistance = Math.abs(second.getX() - dna.trunkXAt(second.getY()))
                    + Math.abs(second.getZ() - dna.trunkZAt(second.getY()));
            return Integer.compare(secondDistance, firstDistance);
        });
        return List.copyOf(staleLeaves.subList(
                0, Math.min(limit, staleLeaves.size())));
    }

    private void collectStaleCanopyLeaf(TreeCandidate candidate, TreeDna dna,
            TreeCanopyTransitionPolicy policy, Set<String> protectedCanopyKeys,
            Block leaf, Map<String, Block> staleByKey) {
        String leafKey = keyFor(leaf);
        String coordinateKey = leaf.getX() + ":" + leaf.getY() + ":" + leaf.getZ();
        boolean plannedWoodBlocker = policy.replacesWithWood(
                leaf.getX(), leaf.getY(), leaf.getZ());
        // ## Neighbor ownership outranks this tree's transition plan. An active
        // tree must route around a neighboring planned crown instead of deleting it.
        boolean nearbyCrownOwnsLeaf = protectedCanopyKeys.contains(coordinateKey);
        if (!isOwnedLoaded(leaf)
                || !plugin.canEvolveAt(
                        leaf.getLocation(), "tree-evolution")
                || leaf.getType() != dna.species().leafMaterial()
                || policy.preservesLeaf(leaf.getX(), leaf.getY(), leaf.getZ())
                || nearbyCrownOwnsLeaf
                || (!plannedWoodBlocker
                        && !dna.wasOriginalShapeLeaf(leafKey))) {
            return;
        }
        staleByKey.putIfAbsent(leafKey, leaf);
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

    private boolean isOwnedLoaded(Block block) {
        int chunkX = block.getX() >> 4;
        int chunkZ = block.getZ() >> 4;
        return block.getWorld().isChunkLoaded(chunkX, chunkZ)
                && Bukkit.isOwnedByCurrentRegion(
                        block.getWorld(), chunkX, chunkZ, 0);
    }

    private static String keyFor(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":"
                + block.getY() + ":" + block.getZ();
    }

    private String format(Block block) {
        return block.getWorld().getName() + " " + block.getX() + ","
                + block.getY() + "," + block.getZ();
    }

    record ObsoleteEvolvedBlock(
            Block block, boolean leaf, String reason) {
        boolean releaseOnly() {
            return "release-to-neighbor-target".equals(reason);
        }
    }

    record NeighborProtection(
            Set<String> bodyKeys,
            Set<String> canopyKeys,
            int nearbyTrees,
            int plannedBlocks
    ) {
    }

    record DisconnectedEvolvedState(
            Optional<ObsoleteEvolvedBlock> obsolete,
            TreeTargetOwnershipRepairPolicy.Analysis targetRepair) {
        boolean targetRepairRequired() {
            return targetRepair.required();
        }
    }
}
