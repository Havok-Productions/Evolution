package org.evolution.features.treeevolution;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Biome;
import org.bukkit.block.data.type.Leaves;
import org.evolution.coreparts.EvolutionPlugin;

/**
 * ## Owns immutable target plans, completion projection, and terminal audits.
 *
 * <p>This is the read/audit side of the constructor hierarchy. It may remove only
 * terminal blocks explicitly classified by an audit repair rule; ordinary placement
 * remains owned by the placement service.</p>
 */
final class TreePlanAuditService {
    private static final List<BlockFace> NEIGHBORS = List.of(
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
    private final EvolutionPlugin plugin;
    private final TreeEvolutionDiagnostics diagnostics;
    private final AtomicLong changedBlocks;
    private final Supplier<TreeEvolutionConfig> configSupplier;
    private final TreeEvolutionPlanner planner = new TreeEvolutionPlanner();
    private final ConcurrentMap<String, CachedTreePlan> planCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedProjectionProgress> projectionProgressCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedLiveTerminalAudit> liveTerminalAuditCache = new ConcurrentHashMap<>();

    TreePlanAuditService(
            EvolutionPlugin plugin,
            TreeEvolutionDiagnostics diagnostics,
            AtomicLong changedBlocks,
            Supplier<TreeEvolutionConfig> configSupplier
    ) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
        this.changedBlocks = changedBlocks;
        this.configSupplier = configSupplier;
    }

    void invalidate(String treeKey) {
        planCache.remove(treeKey);
        projectionProgressCache.remove(treeKey);
        liveTerminalAuditCache.remove(treeKey);
    }

    void clear() {
        planCache.clear();
        projectionProgressCache.clear();
        liveTerminalAuditCache.clear();
    }

    void invalidateLiveAnalysis(String treeKey) {
        projectionProgressCache.remove(treeKey);
        liveTerminalAuditCache.remove(treeKey);
    }

    TreeWorkStatus workStatus(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig config
    ) {
        CachedTreePlan plan = cachedPlan(
                dna, candidate.baseBlock().getBiome(), config.rootsEnabled());
        TreeGrowthQueuePolicy.Completion completion =
                stageCompletion(candidate, dna, plan);
        TreeGrowthQueuePolicy.Budget budget = TreeGrowthQueuePolicy.stageBudget(dna);
        int exposedUpperLogs = exposedUpperLogCount(
                candidate, dna, plan.blocksByKey());
        BranchTipCoverage branchTips = branchTipCoverage(candidate, dna, plan);
        boolean stageComplete = TreeFocusPolicy.stageStructureComplete(
                completion, budget, exposedUpperLogs, branchTips.uncoveredTips());
        boolean transitionPending = TreeFocusPolicy.transitionPending(
                dna.stageCleanupBurst(), dna.stageGrowthBurst(),
                stageComplete, dna.hasOriginalShapeSnapshot());
        return new TreeWorkStatus(
                TreeFocusPolicy.needsFocus(
                        transitionPending, completion, budget,
                        exposedUpperLogs, branchTips.uncoveredTips()),
                stageComplete,
                transitionPending,
                dna.hasOriginalShapeSnapshot(),
                dna.originalShapeBlockCount(),
                dna.unresolvedOriginalShapeLeafCount(),
                completion,
                budget,
                exposedUpperLogs,
                branchTips.uncoveredTips());
    }

    TreeGrowthQueuePolicy.Completion stageCompletion(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan) {
        int visibleHeight = Math.max(1, TreeSpeciesStageStyle.visibleHeight(dna));
        int liveHeight = Math.max(candidate.height(), liveTrunkHeight(candidate.world(), dna));
        TreeProjectionProgress progress = cachedProjectionProgress(candidate.world(), dna, cachedPlan);
        return new TreeGrowthQueuePolicy.Completion(
                liveHeight,
                visibleHeight,
                progress.trunkPlaced(),
                progress.trunkTotal(),
                progress.branchPlaced(),
                progress.branchTotal(),
                progress.canopyPlaced(),
                progress.canopyTotal()
        );
    }

    private TreeProjectionProgress cachedProjectionProgress(
            World world, TreeDna dna, CachedTreePlan cachedPlan) {
        long now = System.currentTimeMillis();
        CachedProjectionProgress cached = projectionProgressCache.get(dna.key());
        if (cached != null
                && cached.signature().equals(cachedPlan.signature())
                && now < cached.expiresMillis()) {
            return cached.progress();
        }
        // ## Completion must inspect the whole target. A capped canopy sample can
        // falsely call a large fluffy crown complete while most of it is absent.
        TreeProjectionProgress progress = projectionProgress(
                world, dna, cachedPlan.orderedBlocks(), Integer.MAX_VALUE);
        long ttl = configSupplier.get().testingEnabled() ? 750L : 2_000L;
        projectionProgressCache.put(dna.key(), new CachedProjectionProgress(
                cachedPlan.signature(), progress, now + ttl));
        return progress;
    }
    int exposedUpperLogCount(TreeCandidate candidate, TreeDna dna,
            Map<String, PlannedTreeBlock> blocksByKey) {
        World world = candidate.world();
        int liveHeight = liveTrunkHeight(world, dna);
        int liveTop = dna.baseY() + liveHeight - 1;
        if (liveHeight < 3) {
            return 0;
        }
        int startY = Math.max(dna.baseY(), liveTop - 3);
        int exposed = 0;
        for (int y = liveTop; y >= startY; y--) {
            int width = TreeSpeciesStageStyle.trunkWidthAt(dna, y);
            int radius = Math.max(0, width / 2);
            int centerX = dna.trunkXAt(y);
            int centerZ = dna.trunkZAt(y);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(centerX + x, y, centerZ + z);
                    if (block.getType() == dna.species().logMaterial()
                            && TreeCanopyIntegrityPolicy.requiresCanopyCover(
                                    block.getX(), block.getY(), block.getZ(),
                                    dna.species().leafMaterial(), blocksByKey)
                            && adjacentPlannedLeafContacts(
                                    world, dna, block,
                                    dna.species().leafMaterial(),
                                    blocksByKey) == 0) {
                        exposed++;
                    }
                }
            }
        }
        if (exposed > 0) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "shape.integrity.exposed-upper-logs",
                    "tree=" + dna.key() + " exposed=" + exposed + " live-top=" + liveTop
                            + " ## upper logs remain exposed until an evolution-owned planned leaf reforms their canopy cover");
        }
        return exposed;
    }

    BranchTipCoverage branchTipCoverage(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan) {
        World world = candidate.world();
        Set<String> visitedTips = new HashSet<>();
        int liveTips = 0;
        int uncoveredPlannedTips = 0;
        Block firstUncovered = null;
        int firstCurrentContacts = 0;
        int firstRequiredContacts = 0;
        int firstCurrentCluster = 0;
        int firstRequiredCluster = 0;
        boolean firstNaturalVolume = true;
        for (TreeBranchPlan branch : cachedPlan.plan().branchPlans()) {
            TreeBranchPlan.BranchTip tip = branch.tip();
            String key = tip.x() + ":" + tip.y() + ":" + tip.z();
            if (!visitedTips.add(key)
                    || !isReadableTreeCoordinate(world, tip.x(), tip.z())) {
                continue;
            }
            Block tipBlock = world.getBlockAt(tip.x(), tip.y(), tip.z());
            if (tipBlock.getType() != dna.species().logMaterial()) {
                continue;
            }
            int requiredContacts = TreeBranchTipIntegrityPolicy.targetLeafContacts(
                    dna, tip.x(), tip.y(), tip.z(), cachedPlan.blocksByKey());
            int requiredCluster = TreeBranchTipIntegrityPolicy.targetClusterLeaves(
                    dna, tip.x(), tip.y(), tip.z(), cachedPlan.blocksByKey());
            if (requiredContacts <= 0 || requiredCluster <= 0) {
                continue;
            }
            liveTips++;
            int currentContacts = adjacentPlannedLeafContacts(
                    world, dna, tipBlock, dna.species().leafMaterial(),
                    cachedPlan.blocksByKey());
            int currentCluster = plannedEnvelopeLiveLeaves(
                    world, dna, tipBlock, dna.species().leafMaterial(),
                    cachedPlan.blocksByKey());
            boolean naturalVolume = hasNaturalLiveEnvelope(
                    world, dna, tipBlock, dna.species().leafMaterial(),
                    cachedPlan.blocksByKey());
            if (currentContacts >= requiredContacts
                    && currentCluster >= requiredCluster
                    && naturalVolume) {
                continue;
            }
            uncoveredPlannedTips++;
            if (firstUncovered == null) {
                firstUncovered = tipBlock;
                firstCurrentContacts = currentContacts;
                firstRequiredContacts = requiredContacts;
                firstCurrentCluster = currentCluster;
                firstRequiredCluster = requiredCluster;
                firstNaturalVolume = naturalVolume;
            }
        }

        LiveTerminalAudit liveAudit = liveTerminalAudit(candidate, dna, cachedPlan);
        int totalUncovered = uncoveredPlannedTips
                + liveAudit.unplannedBareTips()
                + liveAudit.stalePersistentEnvelopeLeaves();
        if (totalUncovered > 0) {
            Block firstVisibleProblem = firstUncovered != null
                    ? firstUncovered
                    : liveAudit.firstUnplannedBareTip() != null
                            ? liveAudit.firstUnplannedBareTip()
                            : liveAudit.firstStalePersistentEnvelopeLeaf();
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "shape.integrity.uncovered-branch-tips",
                    "tree=" + dna.key()
                            + " live-planned-tips=" + liveTips
                            + " uncovered-planned=" + uncoveredPlannedTips
                            + " unplanned-bare-terminals=" + liveAudit.unplannedBareTips()
                            + " stale-persistent-envelope-leaves="
                            + liveAudit.stalePersistentEnvelopeLeaves()
                            + " first=" + format(firstVisibleProblem)
                            + " contacts=" + firstCurrentContacts + "/" + firstRequiredContacts
                            + " envelope=" + firstCurrentCluster + "/" + firstRequiredCluster
                            + " natural-volume=" + firstNaturalVolume
                            + " ## actual terminal logs are audited alongside planned tips so stale protrusions cannot hide outside the target plan");
            if (uncoveredPlannedTips > 0) {
                plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                        "audit.branch-envelope-ownership-failed",
                        "tree=" + dna.key()
                                + " first=" + format(firstUncovered)
                                + " owned-contacts=" + firstCurrentContacts + "/" + firstRequiredContacts
                                + " owned-envelope=" + firstCurrentCluster + "/" + firstRequiredCluster
                                + " natural-volume=" + firstNaturalVolume
                                + " ownership-version=" + dna.evolutionOwnershipVersion()
                                + " evolved-leaves=" + dna.evolvedLeafCount()
                                + " ownership-required=" + dna.requiresEvolvedLeafOwnership()
                                + " ## original/preexisting leaves do not satisfy a terminal branch until the current tree evolution explicitly reforms and records them");
            }
        }
        return new BranchTipCoverage(
                liveTips, totalUncovered, firstUncovered,
                firstCurrentContacts, firstRequiredContacts,
                firstCurrentCluster, firstRequiredCluster,
                liveAudit.unplannedBareTips(), liveAudit.firstUnplannedBareTip(),
                liveAudit.stalePersistentEnvelopeLeaves(),
                liveAudit.firstStalePersistentEnvelopeLeaf());
    }

    private LiveTerminalAudit liveTerminalAudit(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan) {
        long now = System.currentTimeMillis();
        CachedLiveTerminalAudit cached = liveTerminalAuditCache.get(dna.key());
        if (cached != null
                && cached.signature().equals(cachedPlan.signature())
                && now < cached.expiresMillis()) {
            return cached.audit();
        }
        int unplannedBareTips = 0;
        Block firstUnplannedBareTip = null;
        Set<String> auditableLogKeys = new HashSet<>(candidate.naturalKeys());
        // ## Compact known-tree scans intentionally omit distant lower branches.
        // Persisted evolved-log coordinates are exact plugin ownership receipts,
        // so they remain safe to audit after a restart without a full flood-fill.
        auditableLogKeys.addAll(dna.evolvedShapeLogs());
        for (String blockKey : auditableLogKeys) {
            Optional<Block> optional = blockFromKey(candidate.world(), blockKey);
            if (optional.isEmpty()) {
                continue;
            }
            Block block = optional.get();
            if (!isReadableTreeCoordinate(
                            candidate.world(), block.getX(), block.getZ())
                    || block.getY() < dna.baseY() + 2) {
                continue;
            }
            PlannedTreeBlock planned = cachedPlan.blocksByKey().get(
                    block.getX() + ":" + block.getY() + ":" + block.getZ());
            boolean evolvedLogOwned = dna.evolvedShapeLogs().contains(keyFor(block))
                    && !dna.originalShapeLogs().contains(keyFor(block));
            if (evolvedLogOwned && planned != null
                    && (planned.role() == TreeBlockRole.TRUNK
                            || planned.role() == TreeBlockRole.BRANCH)) {
                continue;
            }
            if (block.getType() != dna.species().logMaterial()) {
                continue;
            }
            int trunkDistance = Math.max(
                    Math.abs(block.getX() - dna.trunkXAt(block.getY())),
                    Math.abs(block.getZ() - dna.trunkZAt(block.getY())));
            TreeLiveTerminalPolicy.Decision decision =
                    TreeLiveTerminalPolicy.classify(
                            candidate.ownershipComplete(),
                            evolvedLogOwned,

                            planned == null ? null : planned.role(),
                            block.getY() - dna.baseY(),
                            trunkDistance,
                            sameSpeciesWoodNeighbors(block, dna));
            if (decision
                    != TreeLiveTerminalPolicy.Decision.PRUNE_UNPLANNED_BARE_TERMINAL) {
                continue;
            }
            unplannedBareTips++;
            if (firstUnplannedBareTip == null) {
                firstUnplannedBareTip = block;
            }
        }
        StaleEnvelopeLeafAudit staleLeafAudit = staleEnvelopeLeafAudit(
                candidate, dna, cachedPlan);
        LiveTerminalAudit audit = new LiveTerminalAudit(
                unplannedBareTips, firstUnplannedBareTip,
                staleLeafAudit.count(), staleLeafAudit.first());
        liveTerminalAuditCache.put(dna.key(), new CachedLiveTerminalAudit(
                cachedPlan.signature(), audit, now + 750L));
        if (unplannedBareTips > 0) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "shape.integrity.unplanned-bare-terminal",
                    "tree=" + dna.key()
                            + " count=" + unplannedBareTips
                            + " first=" + format(firstUnplannedBareTip)
                            + " ## live terminal wood absent from the target plan is stale structure, not a canopy candidate");
        }
        if (staleLeafAudit.count() > 0) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "shape.integrity.stale-persistent-envelope-leaf",
                    "tree=" + dna.key()
                            + " count=" + staleLeafAudit.count()
                            + " first=" + format(staleLeafAudit.first())
                            + " ## persistent leaves from the retired forced-envelope rule are outside the natural target canopy");
        }
        return audit;
    }

    private StaleEnvelopeLeafAudit staleEnvelopeLeafAudit(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan) {
        Set<String> visited = new HashSet<>();
        int count = 0;
        Block first = null;
        for (TreeBranchPlan.BranchTip tip
                : cachedPlan.plan().branchEnvelopeCleanupTips()) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int x = tip.x() + dx;
                        int y = tip.y() + dy;
                        int z = tip.z() + dz;
                        String coordinateKey = x + ":" + y + ":" + z;
                        if (!visited.add(coordinateKey)
                                || !isReadableTreeCoordinate(
                                        candidate.world(), x, z)) {
                            continue;
                        }
                        Block leaf = candidate.world().getBlockAt(x, y, z);
                        if (leaf.getType() != dna.species().leafMaterial()
                                || !candidate.naturalKeys().contains(keyFor(leaf))
                                || !(leaf.getBlockData() instanceof Leaves leaves)
                                || !leaves.isPersistent()) {
                            continue;
                        }
                        PlannedTreeBlock planned = cachedPlan.blocksByKey().get(
                                coordinateKey);
                        if (planned != null
                                && planned.role() == TreeBlockRole.CANOPY
                                && planned.material()
                                        == dna.species().leafMaterial()) {
                            continue;
                        }
                        count++;
                        if (first == null) {
                            first = leaf;
                        }
                    }
                }
            }
        }
        return new StaleEnvelopeLeafAudit(count, first);
    }

    private int sameSpeciesWoodNeighbors(Block block, TreeDna dna) {
        int neighbors = 0;
        for (BlockFace face : NEIGHBORS) {
            if (block.getRelative(face).getType() == dna.species().logMaterial()) {
                neighbors++;
            }
        }
        return neighbors;
    }


    boolean pruneStalePersistentEnvelopeLeaf(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig, Block leaf) {
        if (leaf == null
                || !candidate.ownershipComplete()
                || leaf.getType() != dna.species().leafMaterial()
                || !candidate.naturalKeys().contains(keyFor(leaf))
                || !(leaf.getBlockData() instanceof Leaves leaves)
                || !leaves.isPersistent()) {
            return false;
        }
        PlannedTreeBlock planned = cachedPlan.blocksByKey().get(
                leaf.getX() + ":" + leaf.getY() + ":" + leaf.getZ());
        if (planned != null
                && planned.role() == TreeBlockRole.CANOPY
                && planned.material() == dna.species().leafMaterial()) {
            return false;
        }
        int chunkX = leaf.getX() >> 4;
        int chunkZ = leaf.getZ() >> 4;
        if (!leaf.getWorld().isChunkLoaded(chunkX, chunkZ)
                || !Bukkit.isOwnedByCurrentRegion(
                        leaf.getWorld(), chunkX, chunkZ,
                        currentConfig.ownedChunkRadius())
                || !plugin.canEvolveAt(
                        leaf.getLocation(), "tree-evolution")) {
            diagnostics.recordReject(
                    currentConfig, "stale-envelope-leaf-safety", format(leaf));
            return false;
        }
        leaf.setType(Material.AIR, false);
        changedBlocks.incrementAndGet();
        liveTerminalAuditCache.remove(dna.key());
        projectionProgressCache.remove(dna.key());
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "prune.stale-persistent-envelope-leaf",
                "tree=" + dna.key() + " removed=" + format(leaf)
                        + " ## legacy forced-envelope leaf was outside the natural deterministic canopy target");
        return true;
    }

    boolean pruneUnplannedBareTerminal(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig, Block tip) {
        if (tip == null || tip.getType() != dna.species().logMaterial()) {
            return false;
        }
        PlannedTreeBlock planned = cachedPlan.blocksByKey().get(
                tip.getX() + ":" + tip.getY() + ":" + tip.getZ());
        int trunkDistance = Math.max(
                Math.abs(tip.getX() - dna.trunkXAt(tip.getY())),
                Math.abs(tip.getZ() - dna.trunkZAt(tip.getY())));
        TreeLiveTerminalPolicy.Decision decision = TreeLiveTerminalPolicy.classify(
                candidate.ownershipComplete(),
                dna.evolvedShapeLogs().contains(keyFor(tip))
                        && !dna.originalShapeLogs().contains(keyFor(tip)),
                planned == null ? null : planned.role(),
                tip.getY() - dna.baseY(),
                trunkDistance,
                sameSpeciesWoodNeighbors(tip, dna));
        if (decision
                != TreeLiveTerminalPolicy.Decision.PRUNE_UNPLANNED_BARE_TERMINAL) {
            return false;
        }
        int chunkX = tip.getX() >> 4;
        int chunkZ = tip.getZ() >> 4;
        if (!tip.getWorld().isChunkLoaded(chunkX, chunkZ)
                || !Bukkit.isOwnedByCurrentRegion(
                        tip.getWorld(), chunkX, chunkZ,
                        currentConfig.ownedChunkRadius())
                || !plugin.canEvolveAt(
                        tip.getLocation(), "tree-evolution")) {
            diagnostics.recordReject(currentConfig, "stale-terminal-safety", format(tip));
            return false;
        }
        String retiredKey = keyFor(tip);
        tip.setType(Material.AIR, false);
        changedBlocks.incrementAndGet();
        dna.forgetEvolvedLog(retiredKey);
        liveTerminalAuditCache.remove(dna.key());
        projectionProgressCache.remove(dna.key());
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "prune.unplanned-bare-terminal",
                "tree=" + dna.key() + " removed=" + format(tip)
                        + " ## stale live branch tip was absent from the target plan and had no nearby leaf support");
        return true;
    }
    int adjacentPlannedLeafContacts(
            World world, TreeDna dna, Block support, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey) {
        int contacts = 0;
        for (BlockFace face : NEIGHBORS) {
            int x = support.getX() + face.getModX();
            int y = support.getY() + face.getModY();
            int z = support.getZ() + face.getModZ();
            PlannedTreeBlock planned = blocksByKey.get(x + ":" + y + ":" + z);
            if (planned == null
                    || planned.role() != TreeBlockRole.CANOPY
                    || planned.material() != leafMaterial
                    || !isReadableTreeCoordinate(world, x, z)) {
                continue;
            }
            Block leaf = world.getBlockAt(x, y, z);
            if (leaf.getType() == leafMaterial
                    && dna.countsAsEvolvedLeaf(keyFor(leaf))) {
                contacts++;
            }
        }
        return contacts;
    }

    int plannedEnvelopeLiveLeaves(
            World world, TreeDna dna, Block tip, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey) {
        return liveEnvelopeShape(
                world, dna, tip, leafMaterial, blocksByKey).leaves();
    }

    boolean hasNaturalLiveEnvelope(
            World world, TreeDna dna, Block tip, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey) {
        return TreeBranchTipIntegrityPolicy.hasNaturalVolume(
                dna.maturityStage(), dna.species(),
                tip.getX(), tip.getY(), tip.getZ(),
                liveEnvelopeShape(world, dna, tip, leafMaterial, blocksByKey));
    }

    private TreeBranchTipIntegrityPolicy.EnvelopeShape liveEnvelopeShape(
            World world, TreeDna dna, Block tip, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey) {
        ArrayDeque<Block> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        for (BlockFace face : NEIGHBORS) {
            addLivePlannedLeaf(
                    world, dna, tip.getRelative(face), leafMaterial,
                    blocksByKey, pending, visited);
        }
        int leaves = 0;
        int minX = tip.getX();
        int maxX = tip.getX();
        int minY = tip.getY();
        int maxY = tip.getY();
        int minZ = tip.getZ();
        int maxZ = tip.getZ();
        while (!pending.isEmpty()) {
            Block current = pending.removeFirst();
            leaves++;
            minX = Math.min(minX, current.getX());
            maxX = Math.max(maxX, current.getX());
            minY = Math.min(minY, current.getY());
            maxY = Math.max(maxY, current.getY());
            minZ = Math.min(minZ, current.getZ());
            maxZ = Math.max(maxZ, current.getZ());
            for (BlockFace face : NEIGHBORS) {
                Block next = current.getRelative(face);
                if (Math.abs(next.getX() - tip.getX()) > 2
                        || Math.abs(next.getY() - tip.getY()) > 1
                        || Math.abs(next.getZ() - tip.getZ()) > 2) {
                    continue;
                }
                addLivePlannedLeaf(
                        world, dna, next, leafMaterial,
                        blocksByKey, pending, visited);
            }
        }
        return new TreeBranchTipIntegrityPolicy.EnvelopeShape(
                leaves, minX, maxX, minY, maxY, minZ, maxZ);
    }

    private void addLivePlannedLeaf(
            World world, TreeDna dna, Block leaf, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey,
            ArrayDeque<Block> pending, Set<String> visited) {
        String coordinateKey = leaf.getX() + ":" + leaf.getY() + ":" + leaf.getZ();
        if (visited.contains(coordinateKey)) {
            return;
        }
        PlannedTreeBlock planned = blocksByKey.get(coordinateKey);
        if (planned == null
                || planned.role() != TreeBlockRole.CANOPY
                || planned.material() != leafMaterial
                || !isReadableTreeCoordinate(world, leaf.getX(), leaf.getZ())
                || leaf.getType() != leafMaterial
                || !dna.countsAsEvolvedLeaf(keyFor(leaf))) {
            return;
        }
        visited.add(coordinateKey);
        pending.addLast(leaf);
    }

    boolean isReadableTreeCoordinate(World world, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        return world.isChunkLoaded(chunkX, chunkZ)
                && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0);
    }

    private TreeProjectionProgress projectionProgress(
            World world,
            TreeDna dna,
            List<PlannedTreeBlock> orderedBlocks,
            int sampleLimitPerRole
    ) {
        int trunkTotal = 0;
        int trunkPlaced = 0;
        int branchTotal = 0;
        int branchPlaced = 0;
        int canopyTotal = 0;
        int canopyPlaced = 0;
        Map<Long, Boolean> readableChunks = new HashMap<>();
        for (PlannedTreeBlock block : orderedBlocks) {
            if (block.role() != TreeBlockRole.TRUNK
                    && block.role() != TreeBlockRole.BRANCH
                    && block.role() != TreeBlockRole.CANOPY) {
                continue;
            }
            int chunkX = block.x() >> 4;
            int chunkZ = block.z() >> 4;
            long chunkKey = ((long) chunkX << 32)
                    ^ (chunkZ & 0xffffffffL);
            boolean readable = readableChunks.computeIfAbsent(chunkKey,
                    ignored -> world.isChunkLoaded(chunkX, chunkZ)
                            && Bukkit.isOwnedByCurrentRegion(
                                    world, chunkX, chunkZ, 0));
            Block liveBlock = readable
                    ? world.getBlockAt(block.x(), block.y(), block.z())
                    : null;
            Material live = liveBlock == null
                    ? Material.AIR : liveBlock.getType();
            BlockProvenance provenance = BlockProvenance.classify(
                    configSupplier.get(), dna, block, live, true, readable);
            boolean foreignWoodObstacle = readable
                    && liveBlock != null
                    && isStructuralWoodRole(block.role())
                    && isCompatibleOrganicOccupant(block.role(), live)
                    && !dna.countsAsOwnedLog(keyFor(liveBlock));
            if (foreignWoodObstacle) {
                // ## Neighboring wood is an immutable obstacle. It is excluded
                // from this target's completion denominator and never claimed.
                continue;
            }
            if (readable && (provenance == BlockProvenance.LIQUID
                    || provenance == BlockProvenance.PLAYER_OR_FOREIGN_BLOCK)) {
                // ## Immutable obstacles are routed around and do not make a stage
                // mathematically impossible to complete.
                continue;
            }
            boolean satisfied = readable
                    && isSatisfiedPlannedBlock(dna, block, liveBlock);
            if (block.role() == TreeBlockRole.TRUNK
                    && trunkTotal < sampleLimitPerRole) {
                trunkTotal++;
                if (satisfied) {
                    trunkPlaced++;
                }
            } else if (block.role() == TreeBlockRole.BRANCH
                    && branchTotal < sampleLimitPerRole) {
                branchTotal++;
                if (satisfied) {
                    branchPlaced++;
                }
            } else if (block.role() == TreeBlockRole.CANOPY
                    && canopyTotal < sampleLimitPerRole) {
                canopyTotal++;
                if (satisfied) {
                    canopyPlaced++;
                }
            }
            if (trunkTotal >= sampleLimitPerRole
                    && branchTotal >= sampleLimitPerRole
                    && canopyTotal >= sampleLimitPerRole) {
                break;
            }
        }
        return new TreeProjectionProgress(
                trunkPlaced, trunkTotal,
                branchPlaced, branchTotal,
                canopyPlaced, canopyTotal);
    }

    boolean isSatisfiedPlannedBlock(
            TreeDna dna, PlannedTreeBlock planned, Block liveBlock) {
        if (liveBlock == null) {
            return false;
        }
        Material live = liveBlock.getType();
        String blockKey = keyFor(liveBlock);
        boolean materialMatches = live == planned.material();
        boolean compatibleOrganic =
                isCompatibleOrganicOccupant(planned.role(), live);
        if (planned.role() == TreeBlockRole.CANOPY) {
            return TreeBranchEnvelopeOwnershipPolicy.plannedCanopySatisfied(
                    materialMatches, compatibleOrganic,
                    dna.wasOriginalShapeLeaf(blockKey),
                    dna.countsAsEvolvedLeaf(blockKey));
        }
        if (isStructuralWoodRole(planned.role()) && compatibleOrganic) {
            return dna.countsAsOwnedLog(blockKey);
        }
        return materialMatches || compatibleOrganic;
    }

    private boolean isStructuralWoodRole(TreeBlockRole role) {
        return role == TreeBlockRole.TRUNK
                || role == TreeBlockRole.BRANCH
                || role == TreeBlockRole.ROOT;
    }

    private boolean isCompatibleOrganicOccupant(
            TreeBlockRole role, Material live) {
        boolean wood = live.name().endsWith("_LOG")
                || live.name().endsWith("_WOOD")
                || live == Material.MANGROVE_ROOTS
                || live == Material.MUDDY_MANGROVE_ROOTS;
        if (role == TreeBlockRole.CANOPY) {
            return wood || live.name().endsWith("_LEAVES");
        }
        return (role == TreeBlockRole.TRUNK
                || role == TreeBlockRole.BRANCH
                || role == TreeBlockRole.ROOT) && wood;
    }
    int pruneBatchSize(TreeDna dna) {
        // ## One stale leaf per action keeps the visible transition gradual and
        // prevents fast testing ticks from stripping a crown between frames.
        return 1;
    }
    boolean requiresDirectWoodSupport(TreeBlockRole role) {
        return role == TreeBlockRole.TRUNK || role == TreeBlockRole.BRANCH;
    }

    CachedTreePlan cachedPlan(TreeDna dna, Biome biome, boolean rootsEnabled) {
        String signature = dna.planSignature(rootsEnabled, biome);
        CachedTreePlan cached = planCache.get(dna.key());
        if (cached != null && cached.signature().equals(signature)) {
            return cached;
        }
        TreePlan plan = planner.plan(dna, biome, rootsEnabled);
        if (plan.prunedBranchCount() > 0) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "shape.plan.branch-envelope-pruned",
                    "tree=" + dna.key()
                            + " rejected-branches=" + plan.prunedBranchCount()
                            + " accepted-branches=" + plan.branchPlans().size()
                            + " ## branch candidates without a connected preplanned leaf envelope are removed before live growth");
        }
        List<PlannedTreeBlock> orderedBlocks = plan.orderedBlocks();
        Map<String, PlannedTreeBlock> blocksByKey = new HashMap<>();
        for (PlannedTreeBlock block : orderedBlocks) {
            blocksByKey.put(block.key(), block);
        }
        CachedTreePlan fresh = new CachedTreePlan(signature, plan, orderedBlocks, blocksByKey);
        planCache.put(dna.key(), fresh);
        projectionProgressCache.remove(dna.key());
        liveTerminalAuditCache.remove(dna.key());
        return fresh;
    }


    private int liveTrunkHeight(World world, TreeDna dna) {
        int maxHeight = Math.max(1, Math.min(
                TreeSpeciesStageStyle.visibleHeight(dna) + 4,
                world.getMaxHeight() - dna.baseY()));
        int height = 0;
        int misses = 0;
        for (int y = dna.baseY(); y < dna.baseY() + maxHeight; y++) {
            if (hasLiveTrunkAt(world, dna, y)) {
                height = y - dna.baseY() + 1;
                misses = 0;
            } else if (++misses >= 2 && height > 0) {
                break;
            }
        }
        return Math.max(1, height);
    }

    private boolean hasLiveTrunkAt(World world, TreeDna dna, int y) {
        int width = TreeSpeciesStageStyle.trunkWidthAt(dna, y);
        int startX = dna.trunkXAt(y);
        int startZ = dna.trunkZAt(y);
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < width; dz++) {
                if (world.getBlockAt(startX + dx, y, startZ + dz).getType()
                        == dna.species().logMaterial()) {
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

    private static String keyFor(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":"
                + block.getY() + ":" + block.getZ();
    }

    private String format(Block block) {
        return block.getWorld().getName() + " " + block.getX() + ","
                + block.getY() + "," + block.getZ();
    }

    private record CachedProjectionProgress(
            String signature, TreeProjectionProgress progress, long expiresMillis) {
    }

    private record CachedLiveTerminalAudit(
            String signature, LiveTerminalAudit audit, long expiresMillis) {
    }

    private record LiveTerminalAudit(
            int unplannedBareTips,
            Block firstUnplannedBareTip,
            int stalePersistentEnvelopeLeaves,
            Block firstStalePersistentEnvelopeLeaf) {
        private static final LiveTerminalAudit NONE =
                new LiveTerminalAudit(0, null, 0, null);
    }

    private record StaleEnvelopeLeafAudit(int count, Block first) {
    }
}
