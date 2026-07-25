package org.slowtrees.puddles;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.slowtrees.core.PluginFeature;
import org.slowtrees.core.ResourceReporter.ReportSample;
import org.slowtrees.core.SlowTreesPlugin;

public final class PuddleFeature implements PluginFeature, Listener {
    private static final int[][] CARDINAL_DIRECTIONS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1}
    };
    private static final int[][] GROWTH_DIRECTIONS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1},
            {1, 1},
            {1, -1},
            {-1, 1},
            {-1, -1}
    };

    private final SlowTreesPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Set<Puddle>> puddlesByWorld = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dryStartedMillisByWorld = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerWorlds = new ConcurrentHashMap<>();
    private final PuddleDiagnostics diagnostics = new PuddleDiagnostics();
    private final PuddleRenderer renderer;
    private volatile PuddleConfig config;

    public PuddleFeature(SlowTreesPlugin plugin) {
        this.plugin = plugin;
        this.config = PuddleConfig.load(plugin);
        this.renderer = new PuddleRenderer(plugin, diagnostics);
        plugin.pathDebug().trace(plugin, "puddles", "config.load", config.summary());
    }

    @Override
    public void onEnable() {
        diagnostics.saveNow(plugin, config);
        plugin.pathDebug().trace(plugin, "puddles", "enable.schedule-online-players", "players=" + Bukkit.getOnlinePlayers().size());
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayerPuddles(player, 20L);
        }
    }

    @Override
    public void onDisable() {
        renderer.clearAll(Bukkit.getOnlinePlayers(), config.restoreOnDisable());
        puddlesByWorld.clear();
        dryStartedMillisByWorld.clear();
        playerWorlds.clear();
        diagnostics.saveNow(plugin, config);
    }

    @Override
    public void reload() {
        this.config = PuddleConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "puddles", "config.reload", config.summary());
    }

    @Override
    public String status() {
        diagnostics.saveAsync(plugin, config);
        return "Puddles are " + (config.enabled() ? "enabled" : "disabled")
                + ". Active puddles: " + activePuddleCount()
                + ", lifecycle changes: " + diagnostics.activeChanges() + ".";
    }

    public boolean enabled() {
        return config.enabled();
    }

    public int activePuddleCount() {
        int count = 0;
        for (Set<Puddle> puddles : puddlesByWorld.values()) {
            count += puddles.size();
        }
        return count;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        schedulePlayerPuddles(event.getPlayer(), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearPlayer(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        clearPlayer(event.getPlayer(), false);
        schedulePlayerPuddles(event.getPlayer(), 20L);
    }

    private void schedulePlayerPuddles(Player player, long delayTicks) {
        plugin.pathDebug().traceSampled(plugin, "puddles", "scheduler.player-delay", "delay=" + Math.max(1L, delayTicks));
        player.getScheduler().runDelayed(
                plugin,
                task -> runNearPlayer(player),
                null,
                Math.max(1L, delayTicks)
        );
    }

    private void runNearPlayer(Player player) {
        try (ReportSample sample = plugin.resourceReporter().begin("puddles", "tick.run-near-player")) {
            PuddleConfig currentConfig = config;
            if (!player.isOnline()) {
                plugin.pathDebug().trace(plugin, "puddles", "tick.skip.offline-player", "player no longer online");
                sample.detail("offline-player");
                return;
            }

            if (!currentConfig.enabled()) {
                renderer.render(player, Set.of(),
                        currentConfig.renderReassertMillis());
                schedulePlayerPuddles(player, currentConfig.stepTicks());
                sample.detail("disabled");
                return;
            }

            World world = player.getWorld();
            if (world.getEnvironment() != World.Environment.NORMAL || !currentConfig.isWorldAllowed(world)) {
                renderer.render(player, Set.of(),
                        currentConfig.renderReassertMillis());
                schedulePlayerPuddles(player, currentConfig.stepTicks());
                plugin.pathDebug().traceSampled(plugin, "puddles", "tick.skip.environment", world.getName() + " " + world.getEnvironment());
                sample.detail("environment-skip");
                return;
            }

            diagnostics.recordCycle();
            UUID worldId = world.getUID();
            UUID previousWorld = playerWorlds.put(player.getUniqueId(), worldId);
            if (previousWorld != null && !previousWorld.equals(worldId)) {
                renderer.clear(player, false);
            }
            Set<Puddle> puddles = puddlesByWorld.computeIfAbsent(worldId, ignored -> ConcurrentHashMap.newKeySet());
            int before = puddles.size();
            retireRainRejectedPuddles(world, puddles, currentConfig);
            retireDistantPuddles(world, puddles, currentConfig);

            if (world.hasStorm()) {
                dryStartedMillisByWorld.remove(worldId);
                growNearPlayer(player, puddles, rainMultiplier(world), currentConfig);
            } else if (readyToDry(worldId, currentConfig)) {
                dryNearPlayer(player, puddles, currentConfig);
            }

            if (puddles.isEmpty()) {
                puddlesByWorld.remove(worldId, puddles);
                dryStartedMillisByWorld.remove(worldId);
            }

            Set<Puddle> visible = visiblePuddles(player, puddles, currentConfig);
            renderer.render(player, visible,
                    currentConfig.renderReassertMillis());
            int delta = puddles.size() - before;
            sample.workUnits(currentConfig.seedAttemptsPerCycle() + currentConfig.maxExpansionsPerCycle())
                    .changedUnits(Math.abs(delta))
                    .detail("active=" + puddles.size() + " visible=" + visible.size() + " rain=" + world.hasStorm());
            diagnostics.saveSoon(plugin, currentConfig);
            schedulePlayerPuddles(player, currentConfig.stepTicks());
        }
    }

    private void growNearPlayer(Player player, Set<Puddle> puddles, double rainMultiplier, PuddleConfig currentConfig) {
        int available = currentConfig.maxPuddlesPerWorld() - puddles.size();
        if (available <= 0) {
            plugin.pathDebug().traceSampled(plugin, "puddles", "growth.skip.world-cap", "active=" + puddles.size());
            return;
        }

        for (int i = 0; i < currentConfig.seedAttemptsPerCycle() && available > 0; i++) {
            if (roll(currentConfig.seedChance() * rainMultiplier) && addStarterPuddle(player, puddles, currentConfig)) {
                available--;
            }
        }

        int growthBudget = Math.min(available, Math.max(1, (int) Math.round(currentConfig.maxExpansionsPerCycle() * rainMultiplier)));
        if (growthBudget > 0 && !puddles.isEmpty()) {
            expandExistingPuddles(player, puddles, growthBudget, rainMultiplier, currentConfig);
        }
    }

    private boolean addStarterPuddle(Player player, Set<Puddle> puddles, PuddleConfig currentConfig) {
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return false;
        }
        for (int attempt = 0; attempt < currentConfig.maxExpansionsPerCycle() * 3; attempt++) {
            int x = center.getBlockX() + random.nextInt(currentConfig.radius() * 2 + 1) - currentConfig.radius();
            int z = center.getBlockZ() + random.nextInt(currentConfig.radius() * 2 + 1) - currentConfig.radius();
            Optional<Puddle> puddle = createPuddleIfValid(world, world.getUID(), x, z, null, currentConfig);
            if (puddle.isEmpty() || containsColumn(puddles, puddle.get())) {
                continue;
            }
            if (puddles.add(puddle.get())) {
                diagnostics.recordSeeded();
                diagnostics.recordEvent(currentConfig, "[ACTION][puddles] seed at " + format(puddle.get()) + " ## rain seeded a packet puddle");
                plugin.pathDebug().traceSampled(plugin, "puddles", "seed.place", format(puddle.get()));
                return true;
            }
        }
        return false;
    }

    private void expandExistingPuddles(Player player, Set<Puddle> puddles, int growthBudget, double rainMultiplier, PuddleConfig currentConfig) {
        World world = player.getWorld();
        List<Puddle> edges = new ArrayList<>();
        for (Puddle puddle : puddles) {
            if (!world.getUID().equals(puddle.worldId())) {
                continue;
            }
            if (!isNearPlayer(player, puddle, currentConfig.radius() * 2)) {
                continue;
            }
            if (!isEdge(puddles, puddle)) {
                continue;
            }
            int patchSize = connectedPuddleSize(puddles, puddle, currentConfig.maxPuddleSize());
            if (patchSize >= currentConfig.maxPuddleSize()) {
                continue;
            }
            if (patchSize == 1 && nearbyPuddleCount(puddles, puddle, currentConfig.mergeSearchRadius()) < currentConfig.nearbyPuddlesToMerge()) {
                continue;
            }
            edges.add(puddle);
        }
        Collections.shuffle(edges, random);

        int added = 0;
        for (Puddle edge : edges) {
            if (added >= growthBudget) {
                return;
            }
            int oldDepth = edge.depth();
            edge.soak(currentConfig.maxDepth());
            if (edge.depth() != oldDepth) {
                diagnostics.recordSoaked();
            }
            List<int[]> directions = shuffledDirections();
            for (int[] direction : directions) {
                if (added >= growthBudget) {
                    return;
                }
                int x = edge.x() + direction[0];
                int z = edge.z() + direction[1];
                Optional<Material> ground = groundMaterial(world, x, z);
                if (ground.isEmpty()) {
                    continue;
                }
                int patchSize = connectedPuddleSize(puddles, edge, currentConfig.maxPuddleSize());
                double baseChance = patchSize == 1 ? currentConfig.singlePuddleExpandChance() : currentConfig.expandChance();
                double chance = baseChance * rainMultiplier * growthMultiplier(ground.get());
                if (!roll(chance)) {
                    continue;
                }
                Optional<Puddle> puddle = createPuddleIfValid(world, edge.worldId(), x, z, edge, currentConfig);
                if (puddle.isEmpty() || containsColumn(puddles, puddle.get()) || patchSize >= currentConfig.maxPuddleSize()) {
                    continue;
                }
                if (puddles.add(puddle.get())) {
                    added++;
                    diagnostics.recordExpanded();
                    diagnostics.recordEvent(currentConfig, "[ACTION][puddles] expand from=" + format(edge) + " to=" + format(puddle.get()));
                    plugin.pathDebug().traceSampled(plugin, "puddles", "expand.place", format(puddle.get()) + " parent=" + format(edge));
                    break;
                }
            }
        }
    }

    private void dryNearPlayer(Player player, Set<Puddle> puddles, PuddleConfig currentConfig) {
        List<Puddle> candidates = new ArrayList<>();
        for (Puddle puddle : puddles) {
            if (player.getWorld().getUID().equals(puddle.worldId()) && isNearPlayer(player, puddle, currentConfig.radius() * 2)) {
                candidates.add(puddle);
            }
        }
        Collections.shuffle(candidates, random);
        for (Puddle puddle : candidates) {
            Optional<Material> ground = groundAt(player.getWorld(), puddle.x(), puddle.y() - 1, puddle.z());
            if (ground.isEmpty()) {
                continue;
            }
            double chance = currentConfig.shrinkChance() * dryingMultiplier(ground.get());
            chance *= isEdge(puddles, puddle) ? 1.5D : 0.45D;
            if (!roll(chance)) {
                continue;
            }
            puddle.dry();
            diagnostics.recordDried();
            diagnostics.recordEvent(currentConfig, "[ACTION][puddles] dry at " + format(puddle));
            if (puddle.isDry()) {
                puddles.remove(puddle);
            }
        }
    }

    private Optional<Puddle> createPuddleIfValid(World world, UUID worldId, int x, int z, Puddle parent, PuddleConfig currentConfig) {
        if (!isOwnedLoaded(world, x, z, currentConfig)) {
            diagnostics.recordRegionSkip();
            return Optional.empty();
        }
        int groundY = world.getHighestBlockYAt(x, z);
        int waterY = groundY + 1;
        String rainRejection = rainRejection(world, x, waterY, z, currentConfig);
        if (rainRejection != null) {
            diagnostics.recordRejected();
            String rainDetail = x + "," + waterY + "," + z
                    + " biome=" + world.getBiome(x, waterY, z).getKey().getKey()
                    + " temperature=" + world.getTemperature(x, waterY, z);
            diagnostics.recordEvent(currentConfig,
                    "[GATE][puddles] rain.reject." + rainRejection + " " + rainDetail);
            plugin.pathDebug().traceSampled(plugin, "puddles",
                    "rain.reject." + rainRejection, rainDetail);
            return Optional.empty();
        }
        if (parent != null && Math.abs(groundY - (parent.y() - 1)) > 1) {
            diagnostics.recordRejected();
            return Optional.empty();
        }
        Block waterBlock = world.getBlockAt(x, waterY, z);
        if (!plugin.canEvolveAt(waterBlock.getLocation(), "puddles")
                || !waterBlock.getType().isAir()
                || waterBlock.getLightFromSky() <= 0) {
            diagnostics.recordRejected();
            return Optional.empty();
        }
        Optional<Material> ground = groundAt(world, x, groundY, z);
        if (ground.isEmpty() || growthMultiplier(ground.get()) <= 0.0D || !isFlatEnough(world, x, groundY, z, currentConfig)) {
            diagnostics.recordRejected();
            return Optional.empty();
        }
        int depth = random.nextDouble() < 0.15D ? 2 : 1;
        return Optional.of(new Puddle(worldId, x, waterY, z, depth));
    }

    private String rainRejection(
            World world,
            int x,
            int y,
            int z,
            PuddleConfig currentConfig
    ) {
        boolean skyExposed = y > world.getHighestBlockYAt(
                x, z, HeightMap.MOTION_BLOCKING);
        return PuddleRainPolicy.rejection(
                world.getBiome(x, y, z).getKey().getKey(),
                world.getTemperature(x, y, z),
                skyExposed,
                currentConfig.requireRainCapableBiome(),
                currentConfig.requireSkyExposure(),
                currentConfig.allowSnowfall()
        );
    }

    private void retireRainRejectedPuddles(
            World world,
            Set<Puddle> puddles,
            PuddleConfig currentConfig
    ) {
        if (puddles.isEmpty()) {
            return;
        }
        List<Puddle> retired = new ArrayList<>();
        for (Puddle puddle : puddles) {
            int chunkX = puddle.x() >> 4;
            int chunkZ = puddle.z() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)
                    || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                continue;
            }
            String rejection = rainRejection(world, puddle.x(), puddle.y(),
                    puddle.z(), currentConfig);
            if (rejection != null) {
                retired.add(puddle);
            }
        }
        if (retired.isEmpty()) {
            return;
        }
        puddles.removeAll(retired);
        for (int index = 0; index < retired.size(); index++) {
            diagnostics.recordDried();
        }
        diagnostics.recordEvent(currentConfig,
                "[STATE][puddles] retired-non-rain=" + retired.size()
                        + " ## dry, snowy, and covered columns cannot retain puddles");
        plugin.pathDebug().traceSampled(plugin, "puddles",
                "state.retire-non-rain", "removed=" + retired.size());
    }

    private Optional<Material> groundMaterial(World world, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            diagnostics.recordRegionSkip();
            return Optional.empty();
        }
        int groundY = world.getHighestBlockYAt(x, z);
        return groundAt(world, x, groundY, z);
    }

    private Optional<Material> groundAt(World world, int x, int y, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            diagnostics.recordRegionSkip();
            return Optional.empty();
        }
        return Optional.of(world.getBlockAt(x, y, z).getType());
    }

    private boolean isFlatEnough(World world, int x, int y, int z, PuddleConfig currentConfig) {
        for (int[] direction : CARDINAL_DIRECTIONS) {
            int nx = x + direction[0];
            int nz = z + direction[1];
            if (!isOwnedLoaded(world, nx, nz, currentConfig)) {
                return false;
            }
            int ny = world.getHighestBlockYAt(nx, nz);
            if (Math.abs(ny - y) > 1) {
                return false;
            }
        }
        return true;
    }

    private boolean isOwnedLoaded(World world, int x, int z, PuddleConfig currentConfig) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            plugin.pathDebug().failure(plugin, "puddles", "chunk-or-region-gate", "target chunk " + chunkX + "," + chunkZ);
            return false;
        }
        if (currentConfig.requiredPlayerDistanceChunks() <= 0) {
            return true;
        }
        int maxDistance = currentConfig.requiredPlayerDistanceChunks() * 16;
        for (Player player : world.getPlayers()) {
            int dx = player.getLocation().getBlockX() - x;
            int dz = player.getLocation().getBlockZ() - z;
            if ((long) dx * dx + (long) dz * dz <= (long) maxDistance * maxDistance) {
                return true;
            }
        }
        diagnostics.recordRejected();
        plugin.pathDebug().failure(plugin, "puddles", "player-distance", x + "," + z);
        return false;
    }

    private void retireDistantPuddles(
            World world,
            Set<Puddle> puddles,
            PuddleConfig currentConfig
    ) {
        if (puddles.isEmpty()) {
            return;
        }
        int retentionRadius = currentConfig.retentionRadius();
        long retentionSquared = (long) retentionRadius * retentionRadius;
        List<Puddle> retired = new ArrayList<>();
        for (Puddle puddle : puddles) {
            boolean nearPlayer = false;
            for (Player online : world.getPlayers()) {
                int dx = puddle.x() - online.getLocation().getBlockX();
                int dz = puddle.z() - online.getLocation().getBlockZ();
                if ((long) dx * dx + (long) dz * dz
                        <= retentionSquared) {
                    nearPlayer = true;
                    break;
                }
            }
            if (!nearPlayer) {
                retired.add(puddle);
            }
        }
        if (retired.isEmpty()) {
            return;
        }
        puddles.removeAll(retired);
        diagnostics.recordRetired(retired.size());
        diagnostics.recordEvent(currentConfig,
                "[STATE][puddles] retired-distant=" + retired.size()
                        + " remaining=" + puddles.size()
                        + " retention-radius=" + retentionRadius);
        plugin.pathDebug().traceSampled(plugin, "puddles",
                "state.retire-distant",
                "removed=" + retired.size() + " remaining=" + puddles.size()
                        + " radius=" + retentionRadius
                        + " ## old travel paths release the bounded world pool");
    }

    private Set<Puddle> visiblePuddles(Player player, Set<Puddle> puddles, PuddleConfig currentConfig) {
        Set<Puddle> visible = new HashSet<>();
        for (Puddle puddle : puddles) {
            if (player.getWorld().getUID().equals(puddle.worldId())
                    && isNearPlayer(player, puddle, currentConfig.radius() * 2)
                    && plugin.canEvolveAt(new Location(player.getWorld(),
                            puddle.x(), puddle.y(), puddle.z()), "puddles")) {
                visible.add(puddle);
            }
        }
        return visible;
    }

    private boolean readyToDry(UUID worldId, PuddleConfig currentConfig) {
        long now = System.currentTimeMillis();
        long started = dryStartedMillisByWorld.computeIfAbsent(worldId, ignored -> now);
        return now - started >= currentConfig.dryDelayMillis();
    }

    private double rainMultiplier(World world) {
        return world.isThundering() ? config.thunderstormMultiplier() : 1.0D;
    }

    private boolean isNearPlayer(Player player, Puddle puddle, int distance) {
        int dx = puddle.x() - player.getLocation().getBlockX();
        int dz = puddle.z() - player.getLocation().getBlockZ();
        return (long) dx * dx + (long) dz * dz <= (long) distance * distance;
    }

    private boolean isEdge(Set<Puddle> puddles, Puddle puddle) {
        for (int[] direction : CARDINAL_DIRECTIONS) {
            if (!containsColumn(puddles, puddle.worldId(), puddle.x() + direction[0], puddle.z() + direction[1])) {
                return true;
            }
        }
        return false;
    }

    private boolean containsColumn(Set<Puddle> puddles, Puddle candidate) {
        return containsColumn(puddles, candidate.worldId(), candidate.x(), candidate.z());
    }

    private boolean containsColumn(Set<Puddle> puddles, UUID worldId, int x, int z) {
        for (Puddle puddle : puddles) {
            if (puddle.worldId().equals(worldId) && puddle.x() == x && puddle.z() == z) {
                return true;
            }
        }
        return false;
    }

    private int connectedPuddleSize(Set<Puddle> puddles, Puddle start, int stopAt) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<Puddle> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty() && visited.size() < stopAt) {
            Puddle current = queue.removeFirst();
            if (!visited.add(current.columnKey())) {
                continue;
            }
            for (int[] direction : CARDINAL_DIRECTIONS) {
                Puddle neighbor = findColumn(puddles, current.worldId(), current.x() + direction[0], current.z() + direction[1]);
                if (neighbor != null && !visited.contains(neighbor.columnKey())) {
                    queue.add(neighbor);
                }
            }
        }
        return visited.size();
    }

    private Puddle findColumn(Set<Puddle> puddles, UUID worldId, int x, int z) {
        for (Puddle puddle : puddles) {
            if (puddle.worldId().equals(worldId) && puddle.x() == x && puddle.z() == z) {
                return puddle;
            }
        }
        return null;
    }

    private int nearbyPuddleCount(Set<Puddle> puddles, Puddle center, int searchRadius) {
        int count = 0;
        long maxDistanceSquared = (long) searchRadius * searchRadius;
        for (Puddle puddle : puddles) {
            if (puddle.equals(center) || !puddle.worldId().equals(center.worldId())) {
                continue;
            }
            int dx = puddle.x() - center.x();
            int dz = puddle.z() - center.z();
            if ((long) dx * dx + (long) dz * dz <= maxDistanceSquared) {
                count++;
            }
        }
        return count;
    }

    private List<int[]> shuffledDirections() {
        List<int[]> directions = new ArrayList<>(GROWTH_DIRECTIONS.length);
        Collections.addAll(directions, GROWTH_DIRECTIONS);
        Collections.shuffle(directions, random);
        return directions;
    }

    private double growthMultiplier(Material type) {
        return switch (type) {
            case CLAY, MUD, PACKED_MUD -> 1.25D;
            case STONE, COBBLESTONE, ANDESITE, DIORITE, GRANITE, DEEPSLATE, TUFF, CALCITE, MOSS_BLOCK -> 1.0D;
            case GRASS_BLOCK, DIRT, COARSE_DIRT, PODZOL, ROOTED_DIRT -> 0.75D;
            case SAND, RED_SAND, GRAVEL -> 0.45D;
            default -> 0.0D;
        };
    }

    private double dryingMultiplier(Material type) {
        return switch (type) {
            case SAND, RED_SAND, GRAVEL -> 1.55D;
            case GRASS_BLOCK, DIRT, COARSE_DIRT, PODZOL, ROOTED_DIRT -> 1.25D;
            case CLAY, MUD, PACKED_MUD -> 0.65D;
            default -> 1.0D;
        };
    }

    private boolean roll(double chance) {
        return random.nextDouble() < Math.max(0.0D, Math.min(1.0D, chance));
    }

    private void clearPlayer(Player player, boolean restoreBlocks) {
        playerWorlds.remove(player.getUniqueId());
        renderer.clear(player, restoreBlocks);
    }

    private String format(Puddle puddle) {
        return puddle.x() + "," + puddle.y() + "," + puddle.z() + " depth=" + puddle.depth();
    }
}