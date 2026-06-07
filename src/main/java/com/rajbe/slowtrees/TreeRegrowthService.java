package com.rajbe.slowtrees;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

final class TreeRegrowthService implements Listener {
    private final SlowTreesPlugin plugin;
    private final ConcurrentMap<String, PendingTree> pendingTrees = new ConcurrentHashMap<>();
    private volatile SlowTreesConfig config;

    TreeRegrowthService(SlowTreesPlugin plugin, SlowTreesConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    void updateConfig(SlowTreesConfig config) {
        this.config = config;
    }

    int pendingCount() {
        return pendingTrees.size();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        SlowTreesConfig currentConfig = config;
        if (!currentConfig.isWorldAllowed(block.getWorld())) {
            return;
        }

        Optional<TreeType> treeType = currentConfig.treeTypeFor(block.getType());
        if (treeType.isEmpty()) {
            Optional<PendingTree> pendingMushroom = createPendingMushroom(block, currentConfig);
            pendingMushroom.ifPresent(pendingTree -> queueRegrowth(pendingTree, currentConfig));
            return;
        }

        Block baseBlock = findBaseOfSameType(block);
        PendingTree pendingTree = new PendingTree(
                baseBlock.getWorld().getUID(),
                baseBlock.getX(),
                baseBlock.getY(),
                baseBlock.getZ(),
                treeType.get(),
                new Random().nextLong(),
                0
        );

        queueRegrowth(pendingTree, currentConfig);
    }

    private void queueRegrowth(PendingTree pendingTree, SlowTreesConfig currentConfig) {
        PendingTree previous = pendingTrees.putIfAbsent(pendingTree.key(), pendingTree);
        if (previous == null) {
            saveQueuedTrees();
            scheduleAttempt(pendingTree, currentConfig.initialDelayTicks());
        }
    }

    void loadQueuedTrees() {
        File file = queueFile();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection trees = yaml.getConfigurationSection("trees");
        if (trees == null) {
            return;
        }

        for (String key : trees.getKeys(false)) {
            ConfigurationSection section = trees.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            try {
                PendingTree pendingTree = PendingTree.from(section);
                pendingTrees.put(pendingTree.key(), pendingTree);
                scheduleAttempt(pendingTree, config.retryDelayTicks());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipping invalid queued tree entry '" + key + "': " + ex.getMessage());
            }
        }
    }

    void saveQueuedTrees() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection trees = yaml.createSection("trees");
        int index = 0;
        for (PendingTree pendingTree : pendingTrees.values()) {
            pendingTree.writeTo(trees.createSection(Integer.toString(index++)));
        }

        File file = queueFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for queued tree storage.");
            return;
        }

        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save queued trees.", ex);
        }
    }

    private void scheduleAttempt(PendingTree pendingTree, long delayTicks) {
        World world = pendingTree.world();
        if (world == null) {
            pendingTrees.remove(pendingTree.key());
            saveQueuedTrees();
            return;
        }

        Bukkit.getRegionScheduler().runDelayed(
                plugin,
                pendingTree.location(world),
                task -> attemptRegrowth(pendingTree),
                Math.max(1L, delayTicks)
        );
    }

    private void attemptRegrowth(PendingTree pendingTree) {
        SlowTreesConfig currentConfig = config;
        World world = pendingTree.world();
        if (world == null || !currentConfig.isWorldAllowed(world)) {
            pendingTrees.remove(pendingTree.key());
            saveQueuedTrees();
            return;
        }

        Location location = pendingTree.location(world);
        if (!canWorkAt(location, currentConfig)) {
            retryLater(pendingTree);
            return;
        }

        Queue<BlockState> plannedBlocks = planTree(location, pendingTree.treeType(), pendingTree.seed(), currentConfig);
        if (plannedBlocks.isEmpty()) {
            retryLater(pendingTree);
            return;
        }

        placeNextBatch(pendingTree, plannedBlocks);
    }

    private Queue<BlockState> planTree(Location location, TreeType treeType, long seed, SlowTreesConfig currentConfig) {
        List<BlockState> generatedStates = new ArrayList<>();
        try {
            location.getWorld().generateTree(location, new Random(seed), treeType, state -> {
                generatedStates.add(state);
                return false;
            });
        } catch (RuntimeException ex) {
            plugin.getLogger().fine("Tree generation failed at " + format(location) + ": " + ex.getMessage());
            return new ArrayDeque<>();
        }

        generatedStates.removeIf(state -> !canPlace(state, currentConfig));
        generatedStates.sort(Comparator.comparingInt(TreeRegrowthService::growthPriority));
        return new ArrayDeque<>(generatedStates);
    }

    private void placeNextBatch(PendingTree pendingTree, Queue<BlockState> plannedBlocks) {
        World world = pendingTree.world();
        if (world == null) {
            pendingTrees.remove(pendingTree.key());
            saveQueuedTrees();
            return;
        }

        SlowTreesConfig currentConfig = config;
        Location location = pendingTree.location(world);
        if (!canWorkAt(location, currentConfig)) {
            Bukkit.getRegionScheduler().runDelayed(
                    plugin,
                    location,
                    task -> placeNextBatch(pendingTree, plannedBlocks),
                    currentConfig.retryDelayTicks()
            );
            return;
        }

        int placed = 0;
        while (placed < currentConfig.blocksPerGrowthStep() && !plannedBlocks.isEmpty()) {
            BlockState state = plannedBlocks.poll();
            if (state != null && canPlace(state, currentConfig)) {
                state.update(true, false);
                placed++;
            }
        }

        if (plannedBlocks.isEmpty()) {
            pendingTrees.remove(pendingTree.key());
            saveQueuedTrees();
            return;
        }

        Bukkit.getRegionScheduler().runDelayed(
                plugin,
                location,
                task -> placeNextBatch(pendingTree, plannedBlocks),
                currentConfig.growthStepTicks()
        );
    }

    private void retryLater(PendingTree pendingTree) {
        SlowTreesConfig currentConfig = config;
        pendingTree.incrementAttempts();
        if (currentConfig.maxRegrowthAttempts() > 0 && pendingTree.attempts() >= currentConfig.maxRegrowthAttempts()) {
            pendingTrees.remove(pendingTree.key());
            saveQueuedTrees();
            return;
        }

        saveQueuedTrees();
        scheduleAttempt(pendingTree, currentConfig.retryDelayTicks());
    }

    private boolean canWorkAt(Location location, SlowTreesConfig currentConfig) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        int radius = currentConfig.ownedChunkRadius();
        if (!areChunksLoaded(world, chunkX, chunkZ, radius)) {
            return false;
        }

        if (!Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, radius)) {
            return false;
        }

        return isNearPlayer(location, currentConfig.requiredPlayerDistanceChunks());
    }

    private boolean areChunksLoaded(World world, int chunkX, int chunkZ, int radius) {
        for (int x = chunkX - radius; x <= chunkX + radius; x++) {
            for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
                if (!world.isChunkLoaded(x, z)) {
                    return false;
                }
            }
        }
        return true;
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

    private boolean canPlace(BlockState state, SlowTreesConfig currentConfig) {
        Block block = state.getBlock();
        Material currentType = block.getType();
        Material plannedType = state.getType();
        return currentType == plannedType || currentConfig.isReplaceable(currentType);
    }

    private Optional<PendingTree> createPendingMushroom(Block block, SlowTreesConfig currentConfig) {
        Optional<TreeType> mushroomType = resolveMushroomType(block, currentConfig);
        if (mushroomType.isEmpty()) {
            return Optional.empty();
        }

        Optional<Block> baseBlock = findMushroomBase(block);
        if (baseBlock.isEmpty()) {
            return Optional.empty();
        }

        Block base = baseBlock.get();
        return Optional.of(new PendingTree(
                base.getWorld().getUID(),
                base.getX(),
                base.getY(),
                base.getZ(),
                mushroomType.get(),
                new Random().nextLong(),
                0
        ));
    }

    private Optional<TreeType> resolveMushroomType(Block block, SlowTreesConfig currentConfig) {
        Optional<TreeType> directType = currentConfig.mushroomTypeFor(block.getType());
        if (directType.isPresent()) {
            return directType;
        }

        if (block.getType() != Material.MUSHROOM_STEM) {
            return Optional.empty();
        }

        TreeType bestType = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = -1; y <= 8; y++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    Block candidate = block.getRelative(x, y, z);
                    Optional<TreeType> candidateType = currentConfig.mushroomTypeFor(candidate.getType());
                    if (candidateType.isEmpty()) {
                        continue;
                    }

                    int distance = Math.abs(x) + Math.abs(y) + Math.abs(z);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestType = candidateType.get();
                    }
                }
            }
        }

        return Optional.ofNullable(bestType);
    }

    private Optional<Block> findMushroomBase(Block block) {
        if (block.getType() == Material.MUSHROOM_STEM) {
            return Optional.of(findBaseOfSameType(block));
        }

        if (block.getType() != Material.RED_MUSHROOM_BLOCK && block.getType() != Material.BROWN_MUSHROOM_BLOCK) {
            return Optional.empty();
        }

        Block bestStem = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = -8; y <= 1; y++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    Block candidate = block.getRelative(x, y, z);
                    if (candidate.getType() != Material.MUSHROOM_STEM) {
                        continue;
                    }

                    int distance = Math.abs(x) + Math.abs(y) + Math.abs(z);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestStem = candidate;
                    }
                }
            }
        }

        return bestStem == null ? Optional.empty() : Optional.of(findBaseOfSameType(bestStem));
    }

    private Block findBaseOfSameType(Block start) {
        Material material = start.getType();
        Block cursor = start;
        while (cursor.getY() > cursor.getWorld().getMinHeight()) {
            Block below = cursor.getRelative(0, -1, 0);
            if (below.getType() != material) {
                break;
            }
            cursor = below;
        }
        return cursor;
    }

    private File queueFile() {
        return new File(plugin.getDataFolder(), "queued-trees.yml");
    }

    private static int growthPriority(BlockState state) {
        String name = state.getType().name();
        if (name.endsWith("_LOG") || name.endsWith("_STEM")) {
            return 0;
        }
        if (name.endsWith("_LEAVES")) {
            return 2;
        }
        return 1;
    }

    private static String format(Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "unknown" : world.getName();
        return worldName + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
