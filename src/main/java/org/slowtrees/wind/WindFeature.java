package org.slowtrees.wind;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.LeafLitter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.slowtrees.core.PluginFeature;
import org.slowtrees.core.ResourceReporter.ReportSample;
import org.slowtrees.core.SlowTreesPlugin;

public final class WindFeature implements PluginFeature, Listener {
    private final SlowTreesPlugin plugin;
    private final Random random = new Random();
    private final LeafLitterRules leafLitterRules = new LeafLitterRules();
    private final ConcurrentMap<UUID, Long> nextLitterAttemptMillis = new ConcurrentHashMap<>();
    private final WindDiagnostics diagnostics = new WindDiagnostics();
    private volatile WindConfig config;
    private volatile WindPattern pattern = WindPattern.calm();

    public WindFeature(SlowTreesPlugin plugin) {
        this.plugin = plugin;
        this.config = WindConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "wind", "config.load", config.summary());
    }

    @Override
    public void onEnable() {
        diagnostics.saveNow(plugin, config);
        plugin.pathDebug().trace(plugin, "wind", "enable.schedule-online-players", "players=" + Bukkit.getOnlinePlayers().size());
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayerWind(player, 1L);
        }
    }

    @Override
    public void onDisable() {
        diagnostics.saveNow(plugin, config);
        nextLitterAttemptMillis.clear();
    }

    @Override
    public void reload() {
        this.config = WindConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "wind", "config.reload", config.summary());
    }

    @Override
    public String status() {
        diagnostics.saveAsync(plugin, config);
        return "Wind is " + (config.enabled() ? "enabled" : "disabled")
                + ". Leaf particles: " + diagnostics.leafParticlesSpawned()
                + ", leaf litter placed: " + diagnostics.litterPlaced() + ".";
    }

    public boolean enabled() {
        return config.enabled();
    }

    public long leafParticlesSpawned() {
        return diagnostics.leafParticlesSpawned();
    }

    public long leafLitterPlaced() {
        return diagnostics.litterPlaced();
    }

    public double currentWindX() {
        return pattern.x();
    }

    public double currentWindZ() {
        return pattern.z();
    }

    public double currentWindStrength() {
        return pattern.strength();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        schedulePlayerWind(event.getPlayer(), 20L);
    }

    private void schedulePlayerWind(Player player, long delayTicks) {
        plugin.pathDebug().traceSampled(plugin, "wind", "scheduler.player-delay", "delay=" + Math.max(1L, delayTicks));
        player.getScheduler().runDelayed(
                plugin,
                task -> runNearPlayer(player),
                null,
                Math.max(1L, delayTicks)
        );
    }

    private void runNearPlayer(Player player) {
        try (ReportSample sample = plugin.resourceReporter().begin("wind", "tick.run-near-player")) {
            WindConfig currentConfig = config;
            if (!player.isOnline()) {
                plugin.pathDebug().trace(plugin, "wind", "tick.skip.offline-player", "player no longer online");
                sample.detail("offline-player");
                return;
            }

            if (!currentConfig.enabled()) {
                plugin.pathDebug().trace(plugin, "wind", "tick.skip.disabled", "gust interval=" + currentConfig.gustTickInterval());
                schedulePlayerWind(player, currentConfig.gustTickInterval());
                sample.detail("disabled");
                return;
            }

            WindPattern currentPattern = currentPattern(currentConfig);
            Location playerLocation = player.getLocation();
            World world = playerLocation.getWorld();
            if (world == null) {
                plugin.pathDebug().trace(plugin, "wind", "tick.skip.no-world", "player has no world");
                diagnostics.recordEvent(currentConfig, "wind-skip: player has no world");
                sample.detail("missing-world");
            } else if (world.getEnvironment() == World.Environment.NORMAL) {
                Optional<Block> canopy = findNearbyCanopy(playerLocation, currentConfig.treeSearchRadius());
                plugin.pathDebug().trace(plugin, "wind", canopy.isPresent() ? "canopy.search.found" : "canopy.search.miss",
                        "near=" + format(playerLocation) + " radius=" + currentConfig.treeSearchRadius());
                canopy.ifPresent(block -> {
                    spawnLeafDrift(player, block, currentPattern, currentConfig);
                    maybePlaceLeafLitter(player, block, currentPattern, currentConfig);
                });
                sample.changedUnits(canopy.isPresent() ? 1L : 0L).detail("canopy=" + canopy.isPresent() + " near=" + format(playerLocation));
            } else {
                plugin.pathDebug().trace(plugin, "wind", "tick.skip.environment", world.getEnvironment().name());
                diagnostics.recordEvent(currentConfig, "wind-skip: world environment is " + world.getEnvironment());
                sample.detail("environment " + world.getEnvironment());
            }

            schedulePlayerWind(player, currentConfig.gustTickInterval());
        }
    }

    private WindPattern currentPattern(WindConfig currentConfig) {
        WindPattern current = pattern;
        if (current.expired()) {
            current = WindPattern.next(random, currentConfig.patternChangeTicks());
            pattern = current;
            plugin.pathDebug().trace(plugin, "wind", "pattern.change", "strength=" + current.strength());
        }
        return current;
    }

    private Optional<Block> findNearbyCanopy(Location origin, int radius) {
        try (ReportSample sample = plugin.resourceReporter().begin("wind", "search.nearby-canopy")) {
            World world = origin.getWorld();
            if (world == null) {
                sample.detail("missing-world");
                return Optional.empty();
            }

            diagnostics.recordCanopySearch();
            int scanned = 0;
            for (int attempt = 0; attempt < 64; attempt++) {
                int x = origin.getBlockX() + random.nextInt(radius * 2 + 1) - radius;
                int z = origin.getBlockZ() + random.nextInt(radius * 2 + 1) - radius;
                int startY = Math.min(world.getMaxHeight() - 1, origin.getBlockY() + 14);
                int endY = Math.max(world.getMinHeight(), origin.getBlockY() - 4);
                int chunkX = x >> 4;
                int chunkZ = z >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                    plugin.pathDebug().failure(plugin, "wind", "chunk-or-region-gate", "canopy search chunk " + chunkX + "," + chunkZ);
                    continue;
                }

                for (int y = startY; y >= endY; y--) {
                    scanned++;
                    Block block = world.getBlockAt(x, y, z);
                    if (leafLitterRules.isLeaf(block.getType())) {
                        diagnostics.recordCanopyFound();
                        diagnostics.recordEvent(config, "canopy-found: leaf=" + block.getType() + " at " + format(block));
                        sample.workUnits(scanned).changedUnits(1).detail("found " + format(block));
                        return Optional.of(block);
                    }
                }
            }

            diagnostics.recordEvent(config, "canopy-failed: no leaves found near " + format(origin) + " radius=" + radius);
            sample.workUnits(scanned).detail("not-found radius=" + radius);
            return Optional.empty();
        }
    }

    private void spawnLeafDrift(Player player, Block canopy, WindPattern currentPattern, WindConfig currentConfig) {
        try (ReportSample sample = plugin.resourceReporter().begin("wind", "action.spawn-leaf-particles")) {
            World world = canopy.getWorld();
            boolean storm = world.hasStorm() && world.isThundering();
            boolean rain = world.hasStorm();
            int count = currentConfig.particleCount(storm, rain);
            double drift = Math.max(0.05D, currentPattern.strength() * (storm ? 0.18D : rain ? 0.06D : 0.12D));
            Location start = canopy.getLocation().add(0.5D, -0.2D, 0.5D);
            BlockData leafData = canopy.getBlockData();
            int spawned = 0;
            for (int index = 0; index < count; index++) {
                double step = (index + 1) * currentPattern.strength() * (storm ? 0.7D : rain ? 0.25D : 0.45D);
                Location driftPoint = start.clone().add(
                        currentPattern.x() * step,
                        -0.15D * index,
                        currentPattern.z() * step
                );
                if (!plugin.canEvolveAt(driftPoint, "wind")) {
                    continue;
                }
                player.spawnParticle(
                        Particle.BLOCK,
                        driftPoint,
                        1,
                        0.12D,
                        0.25D,
                        0.12D,
                        drift,
                        leafData
                );
                spawned++;
            }
            diagnostics.recordLeafParticles(spawned);
            sample.workUnits(count).changedUnits(spawned)
                    .detail("particles=" + spawned + "/" + count
                            + " canopy=" + format(canopy));
        }
    }

    private void maybePlaceLeafLitter(Player player, Block canopy, WindPattern currentPattern, WindConfig currentConfig) {
        try (ReportSample sample = plugin.resourceReporter().begin("wind", "action.maybe-place-litter")) {
            long now = System.currentTimeMillis();
            long nextAttempt = nextLitterAttemptMillis.getOrDefault(player.getUniqueId(), 0L);
            if (now < nextAttempt) {
                plugin.pathDebug().traceSampled(plugin, "wind", "litter.skip.cooldown", "remaining-ms=" + (nextAttempt - now));
                sample.detail("cooldown");
                return;
            }

            nextLitterAttemptMillis.put(player.getUniqueId(), now + (currentConfig.leafLitterPlacementTicks() * 50L));
            diagnostics.recordLitterCycle();
            World world = canopy.getWorld();
            boolean storm = world.hasStorm() && world.isThundering();
            boolean rain = world.hasStorm();
            int driftRadius = currentConfig.driftRadius(storm, rain);
            diagnostics.recordEvent(currentConfig, "litter-trigger: canopy=" + format(canopy)
                    + " weather=" + weatherName(storm, rain)
                    + " drift-radius=" + driftRadius
                    + " attempts=" + currentConfig.placementAttempts());
            if (rain && !storm && random.nextInt(100) < 25) {
                plugin.pathDebug().trace(plugin, "wind", "litter.skip.rain-roll", format(canopy));
                diagnostics.recordRainSkip();
                diagnostics.recordEvent(currentConfig, "litter-skip: rain settling roll skipped at " + format(canopy));
                diagnostics.saveSoon(plugin, currentConfig);
                sample.detail("rain-roll");
                return;
            }

            int attempts = 0;
            for (int attempt = 0; attempt < currentConfig.placementAttempts(); attempt++) {
                attempts++;
                Optional<Block> target = findLitterTarget(canopy, currentPattern, driftRadius, currentConfig, attempt < 3);
                if (target.isEmpty()) {
                    diagnostics.recordNoTarget();
                    continue;
                }
                diagnostics.recordTargetFound();

                Block block = target.get();
                if (!isNearPlayer(block.getLocation(), currentConfig.requiredPlayerDistanceChunks())) {
                    plugin.pathDebug().failure(plugin, "wind", "player-distance", format(block));
                    plugin.pathDebug().trace(plugin, "wind", "litter.reject.player-distance", format(block));
                    diagnostics.recordPlayerDistanceReject();
                    diagnostics.recordEvent(currentConfig, "litter-failed: target too far from player at " + format(block));
                    continue;
                }
                boolean stackingPile = isStackableLeafLitter(block);
                if (!stackingPile
                        && isLeafLitterChunkCapped(block, currentConfig)) {
                    plugin.pathDebug().failure(plugin, "wind", "chunk-cap", format(block));
                    plugin.pathDebug().trace(plugin, "wind", "litter.reject.chunk-cap", format(block));
                    diagnostics.recordChunkCapReject();
                    diagnostics.recordEvent(currentConfig, "litter-failed: chunk cap reached at " + format(block)
                            + " max=" + currentConfig.maxLeafLitterPerChunk());
                    continue;
                }
                if (!plugin.canEvolveAt(block.getLocation(), "wind")) {
                    continue;
                }
                if (stackingPile) {
                    LeafLitter litter = (LeafLitter) block.getBlockData();
                    int before = litter.getSegmentAmount();
                    litter.setSegmentAmount(
                            LeafLitterStackPolicy.nextSegmentAmount(
                                    before, litter.getMaximumSegmentAmount()));
                    block.setBlockData(litter, false);
                    plugin.pathDebug().trace(plugin, "wind", "litter.stack",
                            format(block) + " segments=" + before + "->"
                                    + litter.getSegmentAmount()
                                    + " ## an existing pile matured instead of creating another sparse tile");
                    diagnostics.recordLitterStacked();
                    diagnostics.recordEvent(currentConfig,
                            "litter-stacked: target=" + format(block)
                                    + " segments=" + before + "->"
                                    + litter.getSegmentAmount());
                    sample.workUnits(attempts).changedUnits(1)
                            .detail("stacked " + format(block));
                } else {
                    block.setType(Material.LEAF_LITTER, false);
                    plugin.pathDebug().trace(plugin, "wind", "litter.place", format(block) + " below=" + block.getRelative(0, -1, 0).getType());
                    diagnostics.recordLitterPlaced();
                    diagnostics.recordEvent(currentConfig, "litter-placed: target=" + format(block)
                            + " below=" + block.getRelative(0, -1, 0).getType());
                    sample.workUnits(attempts).changedUnits(1).detail("placed " + format(block));
                }
                diagnostics.saveSoon(plugin, currentConfig);
                return;
            }
            diagnostics.recordEvent(currentConfig, "litter-failed: all attempts rejected near canopy " + format(canopy));
            plugin.pathDebug().trace(plugin, "wind", "litter.fail.all-attempts", format(canopy));
            sample.workUnits(attempts).detail("all-attempts-failed canopy=" + format(canopy));
            diagnostics.saveSoon(plugin, currentConfig);
        }
    }

    private Optional<Block> findLitterTarget(Block canopy, WindPattern currentPattern, int driftRadius, WindConfig currentConfig, boolean recordFailure) {
        try (ReportSample sample = plugin.resourceReporter().begin("wind", "search.litter-target")) {
            World world = canopy.getWorld();
            int downwind = 1 + (int) Math.round(Math.pow(random.nextDouble(), 1.8D) * Math.max(1, driftRadius));
            int scatter = Math.max(1, driftRadius / 3);
            int x = canopy.getX() + (int) Math.round(currentPattern.x() * downwind) + random.nextInt(scatter * 2 + 1) - scatter;
            int z = canopy.getZ() + (int) Math.round(currentPattern.z() * downwind) + random.nextInt(scatter * 2 + 1) - scatter;
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                if (recordFailure) {
                    plugin.pathDebug().failure(plugin, "wind", "chunk-or-region-gate", "litter target chunk " + chunkX + "," + chunkZ);
                    diagnostics.recordEvent(currentConfig, "target-failed: chunk not loaded or not region-owned at chunk "
                            + chunkX + "," + chunkZ + " from canopy " + format(canopy));
                }
                sample.detail("chunk-or-region");
                return Optional.empty();
            }

            int startY = Math.min(world.getMaxHeight() - 1, canopy.getY() + 3);
            int endY = Math.max(world.getMinHeight(), canopy.getY() - 48);
            Block firstPotentialSurface = null;
            String firstPotentialFailure = null;
            int scanned = 0;
            for (int y = startY; y >= endY; y--) {
                scanned++;
                Block block = world.getBlockAt(x, y, z);
                String failure = leafLitterRules.placementFailure(block);
                if (failure == null) {
                    Block target = findStackableLitterNear(
                            block, currentConfig.leafLitterStackSearchRadius())
                            .orElse(block);
                    sample.workUnits(scanned).changedUnits(1).detail(
                            (target == block ? "found " : "stack-near ")
                                    + format(target));
                    return Optional.of(target);
                }
                if (recordFailure && firstPotentialSurface == null && leafLitterRules.isPotentialSurfaceSpace(block)) {
                    firstPotentialSurface = block;
                    firstPotentialFailure = failure;
                }
            }

            if (recordFailure) {
                if (firstPotentialSurface == null) {
                    diagnostics.recordEvent(currentConfig, "target-failed: no surface candidate at x=" + x
                            + " z=" + z + " y=" + startY + ".." + endY + " from canopy " + format(canopy));
                } else {
                    diagnostics.recordEvent(currentConfig, "target-failed: " + firstPotentialFailure
                            + " at " + format(firstPotentialSurface));
                }
            }
            sample.workUnits(scanned).detail(firstPotentialFailure == null ? "no-surface" : firstPotentialFailure);
            return Optional.empty();
        }
    }

    private Optional<Block> findStackableLitterNear(Block origin, int radius) {
        if (radius <= 0) {
            return Optional.empty();
        }
        try (ReportSample sample = plugin.resourceReporter().begin(
                "wind", "search.litter-stack-target")) {
            World world = origin.getWorld();
            int inspected = 0;
            for (int distance = 0; distance <= radius; distance++) {
                for (int dx = -distance; dx <= distance; dx++) {
                    for (int dz = -distance; dz <= distance; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) {
                            continue;
                        }
                        for (int dy : new int[]{0, 1, -1, 2, -2}) {
                            inspected++;
                            Block candidate = world.getBlockAt(
                                    origin.getX() + dx,
                                    origin.getY() + dy,
                                    origin.getZ() + dz);
                            int chunkX = candidate.getX() >> 4;
                            int chunkZ = candidate.getZ() >> 4;
                            if (!world.isChunkLoaded(chunkX, chunkZ)
                                    || !Bukkit.isOwnedByCurrentRegion(
                                            world, chunkX, chunkZ, 0)
                                    || !isStackableLeafLitter(candidate)
                                    || leafLitterRules.placementFailure(
                                            candidate) != null) {
                                continue;
                            }
                            sample.workUnits(inspected).changedUnits(1)
                                    .detail("found " + format(candidate));
                            return Optional.of(candidate);
                        }
                    }
                }
            }
            sample.workUnits(inspected).detail("none");
            return Optional.empty();
        }
    }

    private boolean isStackableLeafLitter(Block block) {
        if (block.getType() != Material.LEAF_LITTER
                || !(block.getBlockData() instanceof LeafLitter litter)) {
            return false;
        }
        return litter.getSegmentAmount() < litter.getMaximumSegmentAmount();
    }

    private boolean isLeafLitterChunkCapped(Block block, WindConfig currentConfig) {
        int maxPerChunk = currentConfig.maxLeafLitterPerChunk();
        return maxPerChunk > 0 && countLeafLitterInChunk(block) >= maxPerChunk;
    }

    private int countLeafLitterInChunk(Block block) {
        try (ReportSample sample = plugin.resourceReporter().begin("wind", "search.leaf-litter-chunk-count")) {
            int startX = block.getChunk().getX() << 4;
            int startZ = block.getChunk().getZ() << 4;
            int count = 0;
            int scanned = 0;
            for (int x = startX; x < startX + 16; x++) {
                for (int z = startZ; z < startZ + 16; z++) {
                    for (int y = Math.max(block.getWorld().getMinHeight(), block.getY() - 2); y <= Math.min(block.getWorld().getMaxHeight() - 1, block.getY() + 2); y++) {
                        scanned++;
                        if (block.getWorld().getBlockAt(x, y, z).getType() == Material.LEAF_LITTER) {
                            count++;
                        }
                    }
                }
            }
            sample.workUnits(scanned).changedUnits(count).detail("count=" + count + " chunk=" + block.getChunk().getX() + "," + block.getChunk().getZ());
            return count;
        }
    }

    private boolean isNearPlayer(Location location, int distanceChunks) {
        if (distanceChunks <= 0) {
            return true;
        }

        World world = location.getWorld();
        if (world == null) {
            plugin.pathDebug().failure(plugin, "wind", "missing-world", "player distance check");
            return false;
        }

        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        for (Player player : world.getPlayers()) {
            if (!Bukkit.isOwnedByCurrentRegion(player)) {
                continue;
            }

            Location playerLocation = player.getLocation();
            int playerChunkX = playerLocation.getBlockX() >> 4;
            int playerChunkZ = playerLocation.getBlockZ() >> 4;
            int distance = Math.max(Math.abs(playerChunkX - chunkX), Math.abs(playerChunkZ - chunkZ));
            if (distance <= distanceChunks) {
                return true;
            }
        }

        return false;
    }

    private String weatherName(boolean storm, boolean rain) {
        if (storm) {
            return "storm";
        }
        if (rain) {
            return "rain";
        }
        return "clear";
    }

    private String format(Block block) {
        return format(block.getLocation());
    }

    private String format(Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "unknown" : world.getName();
        return worldName + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
