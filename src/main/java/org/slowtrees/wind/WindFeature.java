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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.slowtrees.core.PluginFeature;
import org.slowtrees.core.SlowTreesPlugin;

public final class WindFeature implements PluginFeature, Listener {
    private final SlowTreesPlugin plugin;
    private final Random random = new Random();
    private final LeafLitterRules leafLitterRules = new LeafLitterRules();
    private final ConcurrentMap<UUID, Long> nextLitterAttemptMillis = new ConcurrentHashMap<>();
    private volatile WindConfig config;
    private volatile WindPattern pattern = WindPattern.calm();

    public WindFeature(SlowTreesPlugin plugin) {
        this.plugin = plugin;
        this.config = WindConfig.load(plugin);
    }

    @Override
    public void onEnable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayerWind(player, 1L);
        }
    }

    @Override
    public void onDisable() {
        nextLitterAttemptMillis.clear();
    }

    @Override
    public void reload() {
        this.config = WindConfig.load(plugin);
    }

    @Override
    public String status() {
        return "Wind is " + (config.enabled() ? "enabled" : "disabled") + ".";
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        schedulePlayerWind(event.getPlayer(), 20L);
    }

    private void schedulePlayerWind(Player player, long delayTicks) {
        player.getScheduler().runDelayed(
                plugin,
                task -> runNearPlayer(player),
                null,
                Math.max(1L, delayTicks)
        );
    }

    private void runNearPlayer(Player player) {
        WindConfig currentConfig = config;
        if (!player.isOnline()) {
            return;
        }

        if (!currentConfig.enabled()) {
            schedulePlayerWind(player, currentConfig.gustTickInterval());
            return;
        }

        WindPattern currentPattern = currentPattern(currentConfig);
        Location playerLocation = player.getLocation();
        World world = playerLocation.getWorld();
        if (world != null && world.getEnvironment() == World.Environment.NORMAL) {
            Optional<Block> canopy = findNearbyCanopy(playerLocation, currentConfig.treeSearchRadius());
            canopy.ifPresent(block -> {
                spawnLeafDrift(player, block, currentPattern, currentConfig);
                maybePlaceLeafLitter(player, block, currentPattern, currentConfig);
            });
        }

        schedulePlayerWind(player, currentConfig.gustTickInterval());
    }

    private WindPattern currentPattern(WindConfig currentConfig) {
        WindPattern current = pattern;
        if (current.expired()) {
            current = WindPattern.next(random, currentConfig.patternChangeTicks());
            pattern = current;
        }
        return current;
    }

    private Optional<Block> findNearbyCanopy(Location origin, int radius) {
        World world = origin.getWorld();
        if (world == null) {
            return Optional.empty();
        }

        for (int attempt = 0; attempt < 16; attempt++) {
            int x = origin.getBlockX() + random.nextInt(radius * 2 + 1) - radius;
            int z = origin.getBlockZ() + random.nextInt(radius * 2 + 1) - radius;
            int startY = Math.min(world.getMaxHeight() - 1, origin.getBlockY() + 14);
            int endY = Math.max(world.getMinHeight(), origin.getBlockY() - 4);
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                continue;
            }

            for (int y = startY; y >= endY; y--) {
                Block block = world.getBlockAt(x, y, z);
                if (leafLitterRules.isLeaf(block.getType())) {
                    return Optional.of(block);
                }
            }
        }

        return Optional.empty();
    }

    private void spawnLeafDrift(Player player, Block canopy, WindPattern currentPattern, WindConfig currentConfig) {
        World world = canopy.getWorld();
        boolean storm = world.hasStorm() && world.isThundering();
        boolean rain = world.hasStorm();
        int count = storm ? 5 : rain ? 1 : 3;
        double drift = Math.max(0.05D, currentPattern.strength() * (storm ? 0.18D : rain ? 0.06D : 0.12D));
        Location start = canopy.getLocation().add(0.5D, -0.2D, 0.5D);
        BlockData leafData = canopy.getBlockData();
        for (int index = 0; index < count; index++) {
            double step = (index + 1) * currentPattern.strength() * (storm ? 0.7D : rain ? 0.25D : 0.45D);
            Location driftPoint = start.clone().add(
                    currentPattern.x() * step,
                    -0.15D * index,
                    currentPattern.z() * step
            );
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
        }
    }

    private void maybePlaceLeafLitter(Player player, Block canopy, WindPattern currentPattern, WindConfig currentConfig) {
        if (currentConfig.maxLeafLitterPerChunk() <= 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long nextAttempt = nextLitterAttemptMillis.getOrDefault(player.getUniqueId(), 0L);
        if (now < nextAttempt) {
            return;
        }

        nextLitterAttemptMillis.put(player.getUniqueId(), now + (currentConfig.leafLitterPlacementTicks() * 50L));
        World world = canopy.getWorld();
        boolean storm = world.hasStorm() && world.isThundering();
        boolean rain = world.hasStorm();
        int driftRadius = currentConfig.driftRadius(storm, rain);
        if (rain && !storm && random.nextInt(100) < 45) {
            return;
        }

        for (int attempt = 0; attempt < currentConfig.placementAttempts(); attempt++) {
            Optional<Block> target = findLitterTarget(canopy, currentPattern, driftRadius);
            if (target.isEmpty()) {
                continue;
            }

            Block block = target.get();
            if (isNearPlayer(block.getLocation(), currentConfig.requiredPlayerDistanceChunks())
                    && leafLitterRules.canPlace(block)
                    && countLeafLitterInChunk(block) < currentConfig.maxLeafLitterPerChunk()) {
                block.setType(Material.LEAF_LITTER, false);
                return;
            }
        }
    }

    private Optional<Block> findLitterTarget(Block canopy, WindPattern currentPattern, int driftRadius) {
        World world = canopy.getWorld();
        int downwind = 1 + (int) Math.round(Math.pow(random.nextDouble(), 1.8D) * Math.max(1, driftRadius));
        int scatter = Math.max(1, driftRadius / 3);
        int x = canopy.getX() + (int) Math.round(currentPattern.x() * downwind) + random.nextInt(scatter * 2 + 1) - scatter;
        int z = canopy.getZ() + (int) Math.round(currentPattern.z() * downwind) + random.nextInt(scatter * 2 + 1) - scatter;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            return Optional.empty();
        }

        int startY = Math.min(world.getMaxHeight() - 1, canopy.getY() + 2);
        int endY = Math.max(world.getMinHeight(), canopy.getY() - 24);
        for (int y = startY; y >= endY; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isAir() && !block.getRelative(0, -1, 0).getType().isAir()) {
                return Optional.of(block);
            }
        }

        return Optional.empty();
    }

    private int countLeafLitterInChunk(Block block) {
        int startX = block.getChunk().getX() << 4;
        int startZ = block.getChunk().getZ() << 4;
        int count = 0;
        for (int x = startX; x < startX + 16; x++) {
            for (int z = startZ; z < startZ + 16; z++) {
                for (int y = Math.max(block.getWorld().getMinHeight(), block.getY() - 2); y <= Math.min(block.getWorld().getMaxHeight() - 1, block.getY() + 2); y++) {
                    if (block.getWorld().getBlockAt(x, y, z).getType() == Material.LEAF_LITTER) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private boolean isNearPlayer(Location location, int distanceChunks) {
        if (distanceChunks <= 0) {
            return true;
        }

        World world = location.getWorld();
        if (world == null) {
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
}
