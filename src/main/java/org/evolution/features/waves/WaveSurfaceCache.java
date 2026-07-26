package org.evolution.features.waves;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.evolution.coreparts.EvolutionPlugin;

final class WaveSurfaceCache {
    private final ConcurrentMap<ChunkKey, ChunkSurfaceCache> chunks = new ConcurrentHashMap<>();
    private final ConcurrentMap<ChunkKey, ChunkTopologyCache> topologyChunks = new ConcurrentHashMap<>();
    private static final WaveEnvironmentModel ENVIRONMENT = new WaveEnvironmentModel();
    private static final long TOPOLOGY_TTL_TICKS = 2400L;
    private static final int[][] SHORE_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    SurfaceLookup surfaceLookup(EvolutionPlugin plugin, WaveDiagnostics diagnostics, World world,
            int x, int z, long tick, WaveConfig config) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        ChunkKey chunkKey = new ChunkKey(world.getUID(), chunkX, chunkZ);
        long columnKey = columnKey(x, z);
        ChunkSurfaceCache chunk = chunks.get(chunkKey);
        CachedSurface cached = chunk == null ? null : chunk.columns.get(columnKey);
        if (cached != null && tick - cached.tick <= config.surfaceCacheTtlTicks()) {
            chunk.lastTouchedTick = tick;
            diagnostics.recordSurfaceCacheHit();
            return cached.column == null
                    ? SurfaceLookup.absent()
                    : SurfaceLookup.available(cached.column);
        }
        if (!world.isChunkLoaded(chunkX, chunkZ)
                || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            diagnostics.recordRegionSkip();
            plugin.pathDebug().failure(plugin, "waves", "chunk-or-region-gate",
                    "unknown target chunk " + chunkX + "," + chunkZ);
            return SurfaceLookup.unknown();
        }
        chunk = chunks.computeIfAbsent(chunkKey, ignored -> new ChunkSurfaceCache(tick));
        chunk.lastTouchedTick = tick;
        diagnostics.recordSurfaceCacheMiss();
        SurfaceColumn column = scanSurface(world, x, z, config).orElse(null);
        chunk.columns.put(columnKey, new CachedSurface(tick, column));
        trim(config);
        return column == null ? SurfaceLookup.absent() : SurfaceLookup.available(column);
    }

    CachedLookup peek(World world, int x, int z, long tick, WaveConfig config) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        ChunkSurfaceCache chunk = chunks.get(new ChunkKey(world.getUID(), chunkX, chunkZ));
        if (chunk != null) {
            CachedSurface cached = chunk.columns.get(columnKey(x, z));
            if (cached != null && tick - cached.tick <= config.surfaceCacheTtlTicks()) {
                return new CachedLookup(true, cached.column);
            }
        }
        return CachedLookup.unknown();
    }

    TopologyLookup topology(World world, int x, int z, long tick, WaveConfig config) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        CachedLookup surface = peek(world, x, z, tick, config);
        if (surface.known() && surface.column() != null) {
            return new TopologyLookup(true, true);
        }
        // ## A column may be connected water but intentionally non-renderable,
        // such as a lily pad or waterlogged block. Do not cache that visual skip as land.
        ChunkKey chunkKey = new ChunkKey(world.getUID(), chunkX, chunkZ);
        long columnKey = columnKey(x, z);
        ChunkTopologyCache chunk = topologyChunks.get(chunkKey);
        CachedTopology cached = chunk == null ? null : chunk.columns.get(columnKey);
        if (cached != null && tick - cached.tick() <= Math.max(config.surfaceCacheTtlTicks(), TOPOLOGY_TTL_TICKS)) {
            chunk.lastTouchedTick = tick;
            return new TopologyLookup(true, cached.water());
        }
        if (!world.isChunkLoaded(chunkX, chunkZ)
                || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            return TopologyLookup.unknown();
        }
        chunk = topologyChunks.computeIfAbsent(
                chunkKey, ignored -> new ChunkTopologyCache(tick));
        chunk.lastTouchedTick = tick;
        int y = world.getHighestBlockYAt(x, z);
        Block highest = world.getBlockAt(x, y, z);
        boolean water = isConnectedWaterSurface(highest);
        chunk.columns.put(columnKey, new CachedTopology(tick, water));
        trimTopology(config);
        return new TopologyLookup(true, water);
    }

    Optional<SurfaceColumn> cachedShoreSurface(WaveDiagnostics diagnostics, World world,
            int x, int z, long tick, WaveConfig config) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)
                || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            return Optional.empty();
        }
        ChunkSurfaceCache chunk = chunks.get(new ChunkKey(world.getUID(), chunkX, chunkZ));
        if (chunk == null) {
            return Optional.empty();
        }
        CachedSurface cached = chunk.columns.get(columnKey(x, z));
        if (cached == null || tick - cached.tick > config.surfaceCacheTtlTicks()
                || cached.column == null || !cached.column.hasShoreBias()
                || cached.column.shoreDistance() > config.shoreResponseDistance()) {
            return Optional.empty();
        }
        chunk.lastTouchedTick = tick;
        diagnostics.recordSurfaceCacheHit();
        return Optional.of(cached.column);
    }
    void invalidate() {
        chunks.clear();
        topologyChunks.clear();
    }

    private Optional<SurfaceColumn> scanSurface(World world, int x, int z, WaveConfig config) {
        int y = world.getHighestBlockYAt(x, z);
        Block highest = world.getBlockAt(x, y, z);
        int waterY;
        if (WaveMaterials.isWater(highest.getType())) {
            waterY = y;
        } else {
            Block below = highest.getRelative(0, -1, 0);
            if (!WaveMaterials.containsWater(below)
                    || !WaveMaterials.isVisualReplaceable(highest.getType())) {
                return Optional.empty();
            }
            waterY = below.getY();
        }
        int depth = waterDepth(world, x, waterY, z, config.maxWaterDepthScan());
        int shoreSearchDistance = Math.max(Math.max(config.shoreResponseDistance(), config.fetchDistance()),
                config.shorelineRunupDistance() + 3);
        ShoreBias bias = shoreBias(world, x, waterY, z, shoreSearchDistance);
        boolean biomeAllowed = config.isBiomeAllowed(world.getBiome(x, waterY, z));
        return Optional.of(new SurfaceColumn(x, waterY, z, depth, biomeAllowed,
                bias.dx(), bias.dz(), bias.shoreY(), bias.distance(),
                bias.directionalDistances()));
    }

    private boolean isConnectedWaterSurface(Block highest) {
        if (WaveMaterials.containsWater(highest)) {
            return true;
        }
        Material material = highest.getType();
        if (!WaveMaterials.isVisualReplaceable(material)
                && !WaveMaterials.isWaterSurfaceCover(material)) {
            return false;
        }
        return WaveMaterials.containsWater(highest.getRelative(0, -1, 0));
    }

    private int waterDepth(World world, int x, int y, int z, int maxDepth) {
        int depth = 0;
        for (int dy = 0; dy < maxDepth; dy++) {
            if (!WaveMaterials.containsWater(world.getBlockAt(x, y - dy, z))) {
                break;
            }
            depth++;
        }
        return Math.max(1, depth);
    }

    private ShoreBias shoreBias(World world, int x, int y, int z, int maxDistance) {
        int[] directionalDistances = new int[SHORE_DIRECTIONS.length];
        Arrays.fill(directionalDistances, -1);
        int bestDx = 0;
        int bestDz = 0;
        int bestY = -1;
        int bestDistance = -1;
        for (int directionIndex = 0; directionIndex < SHORE_DIRECTIONS.length; directionIndex++) {
            int[] direction = SHORE_DIRECTIONS[directionIndex];
            for (int distance = 1; distance <= maxDistance; distance++) {
                int nx = x + (direction[0] * distance);
                int nz = z + (direction[1] * distance);
                int chunkX = nx >> 4;
                int chunkZ = nz >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ)
                        || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                    break;
                }
                int groundY = world.getHighestBlockYAt(nx, nz);
                if (Math.abs(groundY - y) > 3) {
                    break;
                }
                Block surface = world.getBlockAt(nx, groundY, nz);
                if (isConnectedWaterSurface(surface)) {
                    continue;
                }
                if (WaveMaterials.isRunupGround(world.getBlockAt(nx, groundY, nz).getType())) {
                    directionalDistances[directionIndex] = distance;
                    if (bestDistance < 0 || distance < bestDistance) {
                        bestDx = direction[0];
                        bestDz = direction[1];
                        bestY = groundY;
                        bestDistance = distance;
                    }
                }
                break;
            }
        }
        return new ShoreBias(bestDx, bestDz, bestY, bestDistance, directionalDistances);
    }

    private void trim(WaveConfig config) {
        if (chunks.size() <= config.surfaceCacheMaxChunks()) {
            return;
        }
        int remove = chunks.size() - config.surfaceCacheMaxChunks();
        for (var entry : chunks.entrySet()) {
            if (remove-- <= 0) {
                break;
            }
            chunks.remove(entry.getKey(), entry.getValue());
        }
    }

    private void trimTopology(WaveConfig config) {
        if (topologyChunks.size() <= config.surfaceCacheMaxChunks()) {
            return;
        }
        int remove = topologyChunks.size() - config.surfaceCacheMaxChunks();
        for (var entry : topologyChunks.entrySet()) {
            if (remove-- <= 0) {
                break;
            }
            topologyChunks.remove(entry.getKey(), entry.getValue());
        }
    }

    private long columnKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    record CachedLookup(boolean known, SurfaceColumn column) {
        static CachedLookup unknown() {
            return new CachedLookup(false, null);
        }
    }

    enum LookupStatus {
        AVAILABLE,
        ABSENT,
        UNKNOWN
    }

    record SurfaceLookup(LookupStatus status, SurfaceColumn column) {
        static SurfaceLookup available(SurfaceColumn column) {
            return new SurfaceLookup(LookupStatus.AVAILABLE, column);
        }

        static SurfaceLookup absent() {
            return new SurfaceLookup(LookupStatus.ABSENT, null);
        }

        static SurfaceLookup unknown() {
            return new SurfaceLookup(LookupStatus.UNKNOWN, null);
        }
    }

    record TopologyLookup(boolean known, boolean water) {
        static TopologyLookup unknown() {
            return new TopologyLookup(false, false);
        }
    }
    record SurfaceColumn(int x, int y, int z, int waterDepth, boolean biomeAllowed,
            int shoreDx, int shoreDz, int shoreY, int shoreDistance,
            int[] directionalShoreDistances) {
        boolean hasShoreBias() {
            return shoreDistance >= 0 && shoreY >= 0 && (shoreDx != 0 || shoreDz != 0);
        }

        int upwindFetch(double windX, double windZ, int maximumFetch) {
            int direction = directionIndex(-windX, -windZ);
            int shore = direction >= 0 && direction < directionalShoreDistances.length
                    ? directionalShoreDistances[direction] : -1;
            return shore < 0 ? maximumFetch : Math.min(maximumFetch, Math.max(0, shore - 1));
        }

        double shoreExposure(double windX, double windZ) {
            return ENVIRONMENT.coastExposure(
                    windX, windZ, shoreDx, shoreDz);
        }

        double shoreHeightCap() {
            return hasShoreBias() ? Math.max(0.0D, shoreY - y) : Double.POSITIVE_INFINITY;
        }
    }

    private static int directionIndex(double x, double z) {
        WaveEnvironmentModel.Direction target = ENVIRONMENT.normalize(x, z);
        int bestIndex = 0;
        double bestDot = -Double.MAX_VALUE;
        for (int index = 0; index < SHORE_DIRECTIONS.length; index++) {
            int[] candidate = SHORE_DIRECTIONS[index];
            WaveEnvironmentModel.Direction direction = ENVIRONMENT.normalize(
                    candidate[0], candidate[1]);
            double dot = (target.x() * direction.x()) + (target.z() * direction.z());
            if (dot > bestDot) {
                bestDot = dot;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private record ShoreBias(int dx, int dz, int shoreY, int distance,
            int[] directionalDistances) {
    }

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {
    }

    private static final class ChunkSurfaceCache {
        private final ConcurrentMap<Long, CachedSurface> columns = new ConcurrentHashMap<>();
        private volatile long lastTouchedTick;

        private ChunkSurfaceCache(long tick) {
            this.lastTouchedTick = tick;
        }
    }

    private static final class ChunkTopologyCache {
        private final ConcurrentMap<Long, CachedTopology> columns = new ConcurrentHashMap<>();
        private volatile long lastTouchedTick;

        private ChunkTopologyCache(long tick) {
            this.lastTouchedTick = tick;
        }
    }

    private record CachedSurface(long tick, SurfaceColumn column) {
    }

    private record CachedTopology(long tick, boolean water) {
    }
}