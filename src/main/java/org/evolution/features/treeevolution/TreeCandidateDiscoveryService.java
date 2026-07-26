package org.evolution.features.treeevolution;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiPredicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.evolution.coreparts.EvolutionPlugin;
import org.evolution.coreparts.ResourceReporter.ReportSample;

/**
 * ## Discovers natural trees and resolves ownership for touching crowns.
 *
 * <p>Hierarchy: known-DNA compact reconstruction, cached spatial search, then bounded fresh-tree
 * traversal. Every traversal remains on the owning Folia region and has visit, queue, and time
 * limits so discovery cannot monopolize a region tick.
 */
final class TreeCandidateDiscoveryService {
    private static final int TREE_GROUP_MAX_VISITED = 1536;
    private static final int TREE_GROUP_MAX_QUEUED = 2048;
    private static final int TREE_GROUP_MAX_DISTANCE = 28;
    private static final long TREE_GROUP_MAX_NANOS = 2_000_000L;

    private final EvolutionPlugin plugin;
    private final TreeEvolutionDiagnostics diagnostics;
    private final ConcurrentMap<String, TreeDna> treeDna;
    private final Set<Material> naturalGround;
    private final Set<Material> naturalDetails;
    private final BiPredicate<Location, Integer> nearPlayer;
    private final ConcurrentMap<String, CachedTreeCandidate> nearestCache =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedKnownCandidates> knownCache =
            new ConcurrentHashMap<>();

    TreeCandidateDiscoveryService(
            EvolutionPlugin plugin,
            TreeEvolutionDiagnostics diagnostics,
            ConcurrentMap<String, TreeDna> treeDna,
            Set<Material> naturalGround,
            Set<Material> naturalDetails,
            BiPredicate<Location, Integer> nearPlayer
    ) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
        this.treeDna = treeDna;
        this.naturalGround = Set.copyOf(naturalGround);
        this.naturalDetails = Set.copyOf(naturalDetails);
        this.nearPlayer = nearPlayer;
    }

    Optional<TreeCandidate> findRandom(Location origin, TreeEvolutionConfig config) {
        try (ReportSample sample =
                plugin.resourceReporter().begin("tree-evolution", "search.random-candidate")) {
            World world = origin.getWorld();
            if (world == null) {
                sample.detail("missing-world");
                return Optional.empty();
            }
            diagnostics.recordSearch();
            int radius = config.searchRadius();
            Random random = new Random();
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if ((dx * dx) + (dz * dz) > radius * radius) {
                sample.detail("radius-roll");
                return Optional.empty();
            }

            int x = origin.getBlockX() + dx;
            int z = origin.getBlockZ() + dz;
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)
                    || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                diagnostics.recordReject(config, "candidate-chunk-region", chunkX + "," + chunkZ);
                plugin.pathDebug().failure(
                        plugin,
                        "tree-evolution",
                        "chunk-or-region-gate",
                        "candidate " + chunkX + "," + chunkZ);
                sample.detail("chunk-or-region");
                return Optional.empty();
            }

            Block highest = world.getHighestBlockAt(x, z);
            int minY = Math.max(world.getMinHeight(), highest.getY() - 32);
            int scanned = 0;
            for (int y = highest.getY(); y >= minY; y--) {
                scanned++;
                Optional<TreeCandidate> candidate = build(world.getBlockAt(x, y, z));
                if (candidate.isPresent()
                        && nearPlayer.test(
                                candidate.get().baseLocation(),
                                config.requiredPlayerDistanceChunks())) {
                    diagnostics.recordCandidate(config, candidate.get());
                    sample.workUnits(scanned)
                            .changedUnits(1)
                            .detail(candidate.get().species() + " " + candidate.get().baseKey());
                    return candidate;
                }
            }
            sample.workUnits(scanned).detail("not-found");
            return Optional.empty();
        }
    }

    Optional<TreeCandidate> findNearest(
            Location origin,
            int radius,
            TreeEvolutionConfig config
    ) {
        try (ReportSample sample =
                plugin.resourceReporter().begin("tree-evolution", "search.nearest-candidate")) {
            World world = origin.getWorld();
            if (world == null) {
                sample.detail("missing-world");
                return Optional.empty();
            }
            String cacheKey = nearestCacheKey(origin, radius);
            CachedTreeCandidate cached = nearestCache.get(cacheKey);
            long now = System.currentTimeMillis();
            if (cached != null && now < cached.expiresMillis()) {
                TreeCandidate candidate = cached.candidate();
                if (candidate == null) {
                    sample.detail("cache-miss-hit radius=" + radius);
                    plugin.pathDebug().traceSampled(
                            plugin, "tree-evolution", "cache.nearest-candidate-empty", cacheKey);
                    return Optional.empty();
                }
                if (isStillValid(candidate)) {
                    sample.changedUnits(1)
                            .detail("cache-hit " + candidate.species() + " " + candidate.baseKey());
                    plugin.pathDebug().traceSampled(
                            plugin,
                            "tree-evolution",
                            "cache.nearest-candidate-hit",
                            candidate.baseKey());
                    return Optional.of(candidate);
                }
                nearestCache.remove(cacheKey, cached);
            }

            int scanned = 0;
            for (int y = origin.getBlockY() + 8; y >= origin.getBlockY() - 8; y--) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        scanned++;
                        Optional<TreeCandidate> candidate = build(world.getBlockAt(
                                origin.getBlockX() + dx,
                                y,
                                origin.getBlockZ() + dz));
                        if (candidate.isPresent()) {
                            nearestCache.put(
                                    cacheKey,
                                    new CachedTreeCandidate(
                                            candidate.get(),
                                            now + config.candidateCacheMillis()));
                            sample.workUnits(scanned)
                                    .changedUnits(1)
                                    .detail(candidate.get().species() + " "
                                            + candidate.get().baseKey());
                            return candidate;
                        }
                    }
                }
            }
            nearestCache.put(
                    cacheKey,
                    new CachedTreeCandidate(null, now + config.candidateCacheMillis()));
            sample.workUnits(scanned).detail("not-found radius=" + radius);
            return Optional.empty();
        }
    }

    List<TreeCandidate> findKnown(
            Location origin,
            TreeEvolutionConfig config,
            int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }
        World world = origin.getWorld();
        if (world == null) {
            return List.of();
        }
        String cacheKey = knownCacheKey(origin, config.searchRadius(), limit);
        long now = System.currentTimeMillis();
        CachedKnownCandidates cached = knownCache.get(cacheKey);
        if (cached != null && now < cached.expiresMillis()) {
            return cached.candidates().stream()
                    .filter(this::isStillValid)
                    .limit(limit)
                    .toList();
        }

        int radius = config.searchRadius();
        int radiusSquared = radius * radius;
        List<TreeDna> nearbyDna = new ArrayList<>();
        List<TreeCandidate> candidates = new ArrayList<>();
        for (TreeDna dna : treeDna.values()) {
            if (!world.getUID().equals(dna.worldId()) || !dna.stumpPresent()) {
                continue;
            }
            int dx = dna.baseX() - origin.getBlockX();
            int dz = dna.baseZ() - origin.getBlockZ();
            if ((dx * dx) + (dz * dz) > radiusSquared) {
                continue;
            }
            int chunkX = dna.baseX() >> 4;
            int chunkZ = dna.baseZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)
                    || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                continue;
            }
            nearbyDna.add(dna);
        }
        nearbyDna.sort(Comparator.comparingLong(TreeDna::lastGrowthMillis)
                .thenComparingInt(dna -> Math.abs(dna.baseX() - origin.getBlockX())
                        + Math.abs(dna.baseZ() - origin.getBlockZ())));
        for (TreeDna dna : nearbyDna) {
            if (candidates.size() >= limit) {
                break;
            }
            buildKnown(world, dna, config)
                    .filter(candidate -> nearPlayer.test(
                            candidate.baseLocation(),
                            config.requiredPlayerDistanceChunks()))
                    .ifPresent(candidates::add);
        }

        List<TreeCandidate> limited = candidates.stream().limit(limit).toList();
        long ttl = Math.max(250L, Math.min(1500L, config.candidateCacheMillis()));
        knownCache.put(cacheKey, new CachedKnownCandidates(limited, now + ttl));
        plugin.pathDebug().traceSampled(
                plugin,
                "tree-evolution",
                "cache.known-candidates-refresh",
                "nearby-dna=" + nearbyDna.size()
                        + " built=" + candidates.size()
                        + " used=" + limited.size()
                        + " radius=" + radius);
        return limited;
    }

    Optional<TreeCandidate> build(Block start) {
        return build(start, false);
    }

    Optional<TreeCandidate> build(Block start, boolean thoroughOwnershipScan) {
        Optional<TreeSpecies> species = TreeSpecies.fromMaterial(start.getType());
        if (species.isEmpty() || start.getType() != species.get().logMaterial()) {
            return Optional.empty();
        }
        Block base = start;
        while (base.getY() > base.getWorld().getMinHeight()
                && base.getRelative(BlockFace.DOWN).getType()
                        == species.get().logMaterial()) {
            base = base.getRelative(BlockFace.DOWN);
        }
        Block top = start;
        while (top.getY() < top.getWorld().getMaxHeight() - 1
                && top.getRelative(BlockFace.UP).getType()
                        == species.get().logMaterial()) {
            top = top.getRelative(BlockFace.UP);
        }
        int height = top.getY() - base.getY() + 1;
        if (height < 2
                || height > 128
                || !naturalGround.contains(base.getRelative(BlockFace.DOWN).getType())) {
            return Optional.empty();
        }

        TreeGroup group = collectGroup(base, species.get(), thoroughOwnershipScan);
        if (group.leaves() < 2) {
            return Optional.empty();
        }
        return Optional.of(new TreeCandidate(
                base.getWorld(),
                base.getX(),
                base.getY(),
                base.getZ(),
                top.getY(),
                height,
                species.get(),
                group.logs(),
                group.leaves(),
                group.keys(),
                group.ownershipComplete()));
    }

    boolean isStillValid(TreeCandidate candidate) {
        World world = candidate.world();
        int chunkX = candidate.baseX() >> 4;
        int chunkZ = candidate.baseZ() >> 4;
        return world != null
                && world.isChunkLoaded(chunkX, chunkZ)
                && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)
                && candidate.baseBlock().getType() == candidate.species().logMaterial();
    }

    void clearSpatialCaches() {
        nearestCache.clear();
        knownCache.clear();
    }

    Optional<TreeCandidate> buildKnown(
            World world,
            TreeDna dna,
            TreeEvolutionConfig config
    ) {
        int chunkX = dna.baseX() >> 4;
        int chunkZ = dna.baseZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)
                || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            diagnostics.recordReject(config, "known-dna-chunk-region", dna.key());
            return Optional.empty();
        }
        Block base = world.getBlockAt(dna.baseX(), dna.baseY(), dna.baseZ());
        if (base.getType() != dna.species().logMaterial()) {
            diagnostics.recordReject(
                    config,
                    "known-dna-base-not-log",
                    base.getType() + " at " + format(base));
            return Optional.empty();
        }

        Set<String> naturalKeys = new HashSet<>();
        int topY = dna.baseY();
        int logs = 0;
        int misses = 0;
        int maxY = Math.min(
                world.getMaxHeight() - 1,
                dna.baseY() + Math.max(6, TreeSpeciesStageStyle.visibleHeight(dna) + 4));
        for (int y = dna.baseY(); y <= maxY; y++) {
            int found = countKnownTrunkBlocksAt(world, dna, y, naturalKeys);
            if (found > 0) {
                logs += found;
                topY = y;
                misses = 0;
            } else if (++misses >= 3) {
                break;
            }
        }

        CanopySample canopy = sampleKnownCanopy(world, dna, topY, naturalKeys);
        int height = Math.max(1, topY - dna.baseY() + 1);
        int connectedLogs = Math.max(height, logs + canopy.logs());
        int connectedLeaves = Math.max(canopy.leaves(), 2);
        plugin.pathDebug().traceSampled(
                plugin,
                "tree-evolution",
                "cache.known-dna-candidate",
                dna.species().id() + " base=" + format(base)
                        + " height=" + height
                        + " logs=" + connectedLogs
                        + " leaves=" + connectedLeaves
                        + " keys=" + naturalKeys.size()
                        + " ## known DNA candidate used compact validation instead of full tree flood-fill");
        return Optional.of(new TreeCandidate(
                world,
                dna.baseX(),
                dna.baseY(),
                dna.baseZ(),
                topY,
                height,
                dna.species(),
                connectedLogs,
                connectedLeaves,
                naturalKeys,
                false));
    }

    private int countKnownTrunkBlocksAt(
            World world,
            TreeDna dna,
            int y,
            Set<String> naturalKeys
    ) {
        int width = TreeSpeciesStageStyle.trunkWidthAt(dna, y);
        int radius = Math.max(0, Math.min(3, width / 2 + 1));
        int centerX = dna.trunkXAt(y);
        int centerZ = dna.trunkZAt(y);
        int found = 0;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                if (!isOwnedLoaded(world, x, z)) {
                    continue;
                }
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() == dna.species().logMaterial()) {
                    found++;
                    naturalKeys.add(keyFor(block));
                }
            }
        }
        return found;
    }

    private CanopySample sampleKnownCanopy(
            World world,
            TreeDna dna,
            int topY,
            Set<String> naturalKeys
    ) {
        int radiusX = Math.max(2, Math.min(5, TreeSpeciesStageStyle.canopyRadiusX(dna) + 1));
        int radiusZ = Math.max(2, Math.min(5, TreeSpeciesStageStyle.canopyRadiusZ(dna) + 1));
        int radiusY = Math.max(1, Math.min(3, TreeSpeciesStageStyle.canopyRadiusY(dna) + 1));
        int centerX = dna.trunkXAt(topY);
        int centerZ = dna.trunkZAt(topY);
        int leaves = 0;
        int logs = 0;
        for (int y = Math.max(world.getMinHeight(), topY - radiusY);
                y <= Math.min(world.getMaxHeight() - 1, topY + radiusY);
                y++) {
            for (int x = centerX - radiusX; x <= centerX + radiusX; x++) {
                for (int z = centerZ - radiusZ; z <= centerZ + radiusZ; z++) {
                    if (!isOwnedLoaded(world, x, z)) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type == dna.species().leafMaterial()) {
                        leaves++;
                        naturalKeys.add(keyFor(block));
                    } else if (type == dna.species().logMaterial()) {
                        logs++;
                        naturalKeys.add(keyFor(block));
                    }
                }
            }
        }
        return new CanopySample(leaves, logs);
    }

    private TreeGroup collectGroup(
            Block base,
            TreeSpecies species,
            boolean thoroughOwnershipScan
    ) {
        Queue<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        Set<String> queued = new HashSet<>();
        Set<TreeLeafOwnershipPolicy.Column> foreignTrunks = new HashSet<>();
        Map<String, Optional<TreeLeafOwnershipPolicy.Column>> rootedColumns = new HashMap<>();
        long started = System.nanoTime();
        int maxVisited = thoroughOwnershipScan
                ? TREE_GROUP_MAX_VISITED * 2 : TREE_GROUP_MAX_VISITED;
        int maxQueued = thoroughOwnershipScan
                ? TREE_GROUP_MAX_QUEUED * 2 : TREE_GROUP_MAX_QUEUED;
        long maxNanos = thoroughOwnershipScan
                ? TREE_GROUP_MAX_NANOS * 2L : TREE_GROUP_MAX_NANOS;
        queue.add(base);
        queued.add(keyFor(base));
        while (!queue.isEmpty()
                && visited.size() < maxVisited
                && queued.size() < maxQueued
                && System.nanoTime() - started < maxNanos) {
            Block block = queue.poll();
            String key = keyFor(block);
            if (!visited.add(key)) {
                continue;
            }
            Material type = block.getType();
            if (type == species.logMaterial()) {
                String columnKey = block.getX() + ":" + block.getZ();
                Optional<TreeLeafOwnershipPolicy.Column> rooted =
                        rootedColumns.computeIfAbsent(
                                columnKey,
                                ignored -> rootedTreeColumn(block, species));
                if (rooted.isPresent()
                        && !TreeLeafOwnershipPolicy.isActiveTrunkColumn(
                                species, base.getX(), base.getZ(), rooted.get())) {
                    foreignTrunks.add(rooted.get());
                    continue;
                }
            } else if (type != species.leafMaterial()
                    && !type.name().endsWith("_LEAVES")
                    && type != Material.VINE
                    && !naturalDetails.contains(type)) {
                continue;
            }

            for (int[] offset : TreeGroupTraversalPolicy.neighborOffsets(thoroughOwnershipScan)) {
                Block relative = block.getRelative(offset[0], offset[1], offset[2]);
                int distance = Math.abs(relative.getX() - base.getX())
                        + Math.abs(relative.getY() - base.getY())
                        + Math.abs(relative.getZ() - base.getZ());
                if (distance <= TreeGroupTraversalPolicy.maximumDistance(
                                TREE_GROUP_MAX_DISTANCE, thoroughOwnershipScan)
                        && relative.getY() >= base.getWorld().getMinHeight()
                        && relative.getY() < base.getWorld().getMaxHeight()
                        && isOwnedLoaded(relative)
                        && isLogOrLeaf(relative.getType())) {
                    String relativeKey = keyFor(relative);
                    if (!visited.contains(relativeKey) && queued.add(relativeKey)) {
                        queue.add(relative);
                    }
                }
            }
        }
        if (!queue.isEmpty()) {
            plugin.pathDebug().traceSampled(
                    plugin,
                    "tree-evolution",
                    "gate.tree-group-budget",
                    "base=" + format(base)
                            + " visited=" + visited.size()
                            + " queued=" + queued.size()
                            + " remaining=" + queue.size()
                            + " nanos=" + (System.nanoTime() - started)
                            + " thorough=" + thoroughOwnershipScan
                            + " ## candidate traversal stopped early to protect Folia region tick");
        }

        Set<String> ownedKeys = new HashSet<>();
        int logs = 0;
        int leaves = 0;
        for (String key : visited) {
            Optional<Block> found = blockFromKey(base.getWorld(), key);
            if (found.isEmpty()) {
                continue;
            }
            Block block = found.get();
            if (!TreeLeafOwnershipPolicy.belongsToActiveTree(
                    block.getX(),
                    block.getZ(),
                    base.getX(),
                    base.getZ(),
                    foreignTrunks)) {
                continue;
            }
            ownedKeys.add(key);
            Material type = block.getType();
            if (type == species.logMaterial()) {
                logs++;
            } else if (type == species.leafMaterial()
                    || type.name().endsWith("_LEAVES")) {
                leaves++;
            }
        }
        if (!foreignTrunks.isEmpty()) {
            plugin.pathDebug().traceSampled(
                    plugin,
                    "tree-evolution",
                    "candidate.touching-crowns-separated",
                    "base=" + format(base)
                            + " foreign-trunks=" + foreignTrunks.size()
                            + " connected=" + visited.size()
                            + " owned=" + ownedKeys.size()
                            + " ## touching leaves remain valid while rooted neighboring trees keep separate ownership");
        }
        return new TreeGroup(logs, leaves, ownedKeys, queue.isEmpty());
    }

    private Optional<TreeLeafOwnershipPolicy.Column> rootedTreeColumn(
            Block block,
            TreeSpecies species
    ) {
        Block bottom = block;
        int descent = 0;
        while (descent++ < 128
                && bottom.getY() > bottom.getWorld().getMinHeight()
                && bottom.getRelative(BlockFace.DOWN).getType() == species.logMaterial()) {
            bottom = bottom.getRelative(BlockFace.DOWN);
        }
        if (naturalGround.contains(bottom.getRelative(BlockFace.DOWN).getType())) {
            int verticalRun = 0;
            Block cursor = bottom;
            while (verticalRun < 4
                    && cursor.getType() == species.logMaterial()) {
                verticalRun++;
                cursor = cursor.getRelative(BlockFace.UP);
            }
            if (verticalRun >= 2) {
                return Optional.of(new TreeLeafOwnershipPolicy.Column(
                        bottom.getX(), bottom.getZ()));
            }
        }

        if (species != TreeSpecies.ACACIA) {
            return Optional.empty();
        }
        // ## Acacia trunks may move diagonally between layers. Keep this more
        // expensive fallback species-scoped so ordinary candidate scans stay cheap.
        // Follow only level/downward wood so a touching crown cannot turn a
        // neighboring rooted trunk into an active-tree branch.
        Queue<RootSearchNode> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(new RootSearchNode(block, 0));
        visited.add(keyFor(block));
        int inspected = 0;
        while (!pending.isEmpty() && inspected++ < 128) {
            RootSearchNode node = pending.poll();
            Block current = node.block();
            if (naturalGround.contains(
                    current.getRelative(BlockFace.DOWN).getType())
                    && (node.depth() > 0
                            || hasSameSpeciesContinuation(current, species))) {
                plugin.pathDebug().traceSampled(
                        plugin,
                        "tree-evolution",
                        "candidate.angled-trunk-root",
                        "from=" + format(block) + " root=" + format(current)
                                + " depth=" + node.depth()
                                + " ## downward-biased ownership trace separated a bent rooted trunk");
                return Optional.of(new TreeLeafOwnershipPolicy.Column(
                        current.getX(), current.getZ()));
            }
            for (int dy = -1; dy <= 0; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        if (dy == 0 && Math.abs(dx) + Math.abs(dz) != 1) {
                            continue;
                        }
                        Block next = current.getRelative(dx, dy, dz);
                        if (next.getType() != species.logMaterial()
                                || !isOwnedLoaded(next)
                                || Math.abs(next.getX() - block.getX()) > 12
                                || Math.abs(next.getZ() - block.getZ()) > 12
                                || block.getY() - next.getY() > 48) {
                            continue;
                        }
                        if (visited.add(keyFor(next))) {
                            pending.add(new RootSearchNode(
                                    next, node.depth() + 1));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private boolean hasSameSpeciesContinuation(
            Block root,
            TreeSpecies species
    ) {
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    if (root.getRelative(dx, dy, dz).getType()
                            == species.logMaterial()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isOwnedLoaded(Block block) {
        return isOwnedLoaded(block.getWorld(), block.getX(), block.getZ());
    }

    private boolean isOwnedLoaded(World world, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        return world.isChunkLoaded(chunkX, chunkZ)
                && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0);
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

    private String nearestCacheKey(Location origin, int radius) {
        World world = origin.getWorld();
        String worldKey = world == null ? "unknown" : world.getUID().toString();
        return worldKey + ":" + (origin.getBlockX() >> 4)
                + ":" + (origin.getBlockZ() >> 4)
                + ":" + radius;
    }

    private String knownCacheKey(Location origin, int radius, int limit) {
        return nearestCacheKey(origin, radius) + ":" + limit;
    }

    private boolean isLogOrLeaf(Material material) {
        return material.name().endsWith("_LOG")
                || material.name().endsWith("_WOOD")
                || material.name().endsWith("_LEAVES");
    }

    private static String keyFor(Block block) {
        return block.getWorld().getUID()
                + ":" + block.getX()
                + ":" + block.getY()
                + ":" + block.getZ();
    }

    private String format(Block block) {
        Location location = block.getLocation();
        return location.getWorld().getName()
                + " " + location.getBlockX()
                + "," + location.getBlockY()
                + "," + location.getBlockZ();
    }

    private record RootSearchNode(Block block, int depth) {
    }

    private record TreeGroup(
            int logs,
            int leaves,
            Set<String> keys,
            boolean ownershipComplete
    ) {
    }

    private record CanopySample(int leaves, int logs) {
    }

    private record CachedTreeCandidate(TreeCandidate candidate, long expiresMillis) {
    }

    private record CachedKnownCandidates(List<TreeCandidate> candidates, long expiresMillis) {
    }
}
