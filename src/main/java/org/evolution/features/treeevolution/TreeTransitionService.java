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
    Optional<PlannedTarget> readyTransitionBlocker(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig
    ) {
        List<Block> blockers = findStaleCanopyLeaves(
                candidate, dna, cachedPlan.orderedBlocks(), 64,
                currentConfig, true);
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
        TreeGrowthIntent intent = planned.role() == TreeBlockRole.TRUNK
                ? TreeGrowthIntent.HEIGHT : TreeGrowthIntent.BRANCH;
        placementService.place(target, planned);
        if (dna.markEvolvedBlock(keyFor(target), planned.role())) {
            repository.markDirty("recorded evolved transition " + planned.role()
                    + " " + keyFor(target));
        }
        dna.markPlacedForIntent(intent, transitionBlocker.nextCursor());
        changedBlocks.incrementAndGet();
                planAudit.invalidateLiveAnalysis(dna.key());
        diagnostics.recordPlaced(plugin, currentConfig, target, planned);
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "constructor.atomic-transition-blocker",
                "[CONSTRUCTOR][REPLACE_TRANSITION_BLOCKER]"
                        + "[TRANSITION_RECONCILER] tree=" + dna.key()
                        + " role=" + planned.role()
                        + " at=" + format(target)
                        + " ## source leaf became ready planned wood in one world change");
        return true;
    }

    void reconcileSourceLeafLedger(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig
    ) {
        if (!dna.hasOriginalShapeSnapshot()) {
            return;
        }
        int adopted = 0;
        int absent = 0;
        int releasedToNeighbor = 0;
        Set<String> protectedCanopyKeys = nearbyPlannedCanopyKeys(
                candidate, dna, currentConfig);
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
            return;
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
    }
    List<Block> findRetiredCanopyLeaves(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            int limit,
            TreeEvolutionConfig currentConfig
    ) {
        List<Block> stale = findStaleCanopyLeaves(
                candidate, dna, cachedPlan.orderedBlocks(),
                Math.max(limit * 4, 16), currentConfig, false);
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

    private List<Block> findStaleCanopyLeaves(TreeCandidate candidate, TreeDna dna,
            List<PlannedTreeBlock> orderedBlocks, int limit,
            TreeEvolutionConfig currentConfig, boolean woodBlockersOnly) {
        if (limit <= 0) {
            return List.of();
        }
        TreeCanopyTransitionPolicy policy = TreeCanopyTransitionPolicy.from(
                dna, orderedBlocks, candidate.topY());
        Set<String> protectedCanopyKeys = nearbyPlannedCanopyKeys(
                candidate, dna, currentConfig);
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

    private Set<String> nearbyPlannedCanopyKeys(TreeCandidate candidate,
            TreeDna activeDna, TreeEvolutionConfig currentConfig) {
        Set<String> protectedKeys = new HashSet<>();
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
            CachedTreePlan nearbyPlan = planAudit.cachedPlan(
                    nearbyDna, planningBiome, currentConfig.rootsEnabled());
            for (PlannedTreeBlock block : nearbyPlan.orderedBlocks()) {
                if (block.role() == TreeBlockRole.CANOPY
                        && TreeLeafOwnershipPolicy.neighborPlanOwnsPosition(
                                block.x(), block.z(),
                                activeDna.trunkXAt(block.y()),
                                activeDna.trunkZAt(block.y()),
                                nearbyDna.trunkXAt(block.y()),
                                nearbyDna.trunkZAt(block.y()))) {
                    protectedKeys.add(block.key());
                }
            }
        }
        return protectedKeys;
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
}
