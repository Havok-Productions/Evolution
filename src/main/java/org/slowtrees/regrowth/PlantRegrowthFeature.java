package org.slowtrees.regrowth;

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
import org.slowtrees.core.PluginFeature;
import org.slowtrees.core.SlowTreesPlugin;

public final class PlantRegrowthFeature implements PluginFeature, Listener {
    private final SlowTreesPlugin plugin;
    private final ConcurrentMap<String, PendingRegrowth> pendingRegrowth = new ConcurrentHashMap<>();
    private volatile PlantRegrowthConfig config;

    public PlantRegrowthFeature(SlowTreesPlugin plugin) {
        this.plugin = plugin;
        this.config = PlantRegrowthConfig.load(plugin);
    }

    @Override
    public void onEnable() {
        loadQueuedRegrowth();
    }

    @Override
    public void onDisable() {
        saveQueuedRegrowth();
    }

    @Override
    public void reload() {
        this.config = PlantRegrowthConfig.load(plugin);
    }

    @Override
    public String status() {
        return "Plant regrowth has " + pendingRegrowth.size() + " queued structure(s).";
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        PlantRegrowthConfig currentConfig = config;
        if (!currentConfig.isWorldAllowed(block.getWorld())) {
            return;
        }

        Optional<TreeType> treeType = currentConfig.treeTypeFor(block.getType());
        if (treeType.isEmpty()) {
            Optional<PendingRegrowth> pendingMushroom = createPendingMushroom(block, currentConfig);
            pendingMushroom.ifPresent(pending -> queueRegrowth(pending, currentConfig));
            return;
        }

        Block baseBlock = findBaseOfSameType(block);
        if (isSameBlock(block, baseBlock)) {
            cancelAnchoredRegrowth(baseBlock);
            return;
        }

        PendingRegrowth pending = new PendingRegrowth(
                baseBlock.getWorld().getUID(),
                baseBlock.getX(),
                baseBlock.getY(),
                baseBlock.getZ(),
                treeType.get(),
                baseBlock.getType(),
                new Random().nextLong(),
                0
        );

        queueRegrowth(pending, currentConfig);
    }

    private void queueRegrowth(PendingRegrowth pending, PlantRegrowthConfig currentConfig) {
        PendingRegrowth previous = pendingRegrowth.putIfAbsent(pending.key(), pending);
        if (previous == null) {
            saveQueuedRegrowth();
            scheduleAttempt(pending, currentConfig.initialDelayTicks());
        }
    }

    private void cancelAnchoredRegrowth(Block baseBlock) {
        if (pendingRegrowth.remove(PendingRegrowth.keyFor(baseBlock)) != null) {
            saveQueuedRegrowth();
        }
    }

    private void loadQueuedRegrowth() {
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
                PendingRegrowth pending = PendingRegrowth.from(section);
                pendingRegrowth.put(pending.key(), pending);
                scheduleAttempt(pending, config.retryDelayTicks());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipping invalid queued plant regrowth entry '" + key + "': " + ex.getMessage());
            }
        }
    }

    private void saveQueuedRegrowth() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection trees = yaml.createSection("trees");
        int index = 0;
        for (PendingRegrowth pending : pendingRegrowth.values()) {
            pending.writeTo(trees.createSection(Integer.toString(index++)));
        }

        File file = queueFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for plant regrowth storage.");
            return;
        }

        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save queued plant regrowth.", ex);
        }
    }

    private void scheduleAttempt(PendingRegrowth pending, long delayTicks) {
        World world = pending.world();
        if (world == null) {
            pendingRegrowth.remove(pending.key());
            saveQueuedRegrowth();
            return;
        }

        Bukkit.getRegionScheduler().runDelayed(
                plugin,
                pending.location(world),
                task -> attemptRegrowth(pending),
                Math.max(1L, delayTicks)
        );
    }

    private void attemptRegrowth(PendingRegrowth pending) {
        PlantRegrowthConfig currentConfig = config;
        World world = pending.world();
        if (world == null || !currentConfig.isWorldAllowed(world)) {
            pendingRegrowth.remove(pending.key());
            saveQueuedRegrowth();
            return;
        }

        Location location = pending.location(world);
        if (!canWorkAt(location, currentConfig)) {
            retryLater(pending);
            return;
        }

        if (!hasAnchorBlock(location, pending)) {
            pendingRegrowth.remove(pending.key());
            saveQueuedRegrowth();
            return;
        }

        Queue<BlockState> plannedBlocks = planStructure(location, pending.treeType(), pending.seed(), currentConfig);
        if (plannedBlocks.isEmpty()) {
            retryLater(pending);
            return;
        }

        placeNextBatch(pending, plannedBlocks);
    }

    private Queue<BlockState> planStructure(Location location, TreeType treeType, long seed, PlantRegrowthConfig currentConfig) {
        List<BlockState> generatedStates = new ArrayList<>();
        try {
            location.getWorld().generateTree(location, new Random(seed), treeType, state -> {
                generatedStates.add(state);
                return false;
            });
        } catch (RuntimeException ex) {
            plugin.getLogger().fine("Structure generation failed at " + format(location) + ": " + ex.getMessage());
            return new ArrayDeque<>();
        }

        generatedStates.removeIf(state -> !canPlace(state, currentConfig));
        generatedStates.sort(Comparator.comparingInt(PlantRegrowthFeature::growthPriority));
        return new ArrayDeque<>(generatedStates);
    }

    private void placeNextBatch(PendingRegrowth pending, Queue<BlockState> plannedBlocks) {
        World world = pending.world();
        if (world == null) {
            pendingRegrowth.remove(pending.key());
            saveQueuedRegrowth();
            return;
        }

        PlantRegrowthConfig currentConfig = config;
        Location location = pending.location(world);
        if (!canWorkAt(location, currentConfig)) {
            Bukkit.getRegionScheduler().runDelayed(
                    plugin,
                    location,
                    task -> placeNextBatch(pending, plannedBlocks),
                    currentConfig.retryDelayTicks()
            );
            return;
        }

        if (!hasAnchorBlock(location, pending)) {
            pendingRegrowth.remove(pending.key());
            saveQueuedRegrowth();
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
            pendingRegrowth.remove(pending.key());
            saveQueuedRegrowth();
            return;
        }

        Bukkit.getRegionScheduler().runDelayed(
                plugin,
                location,
                task -> placeNextBatch(pending, plannedBlocks),
                currentConfig.growthStepTicks()
        );
    }

    private void retryLater(PendingRegrowth pending) {
        PlantRegrowthConfig currentConfig = config;
        pending.incrementAttempts();
        if (currentConfig.maxRegrowthAttempts() > 0 && pending.attempts() >= currentConfig.maxRegrowthAttempts()) {
            pendingRegrowth.remove(pending.key());
            saveQueuedRegrowth();
            return;
        }

        saveQueuedRegrowth();
        scheduleAttempt(pending, currentConfig.retryDelayTicks());
    }

    private boolean canWorkAt(Location location, PlantRegrowthConfig currentConfig) {
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

    private boolean canPlace(BlockState state, PlantRegrowthConfig currentConfig) {
        Block block = state.getBlock();
        Material currentType = block.getType();
        Material plannedType = state.getType();
        return currentType == plannedType || currentConfig.isReplaceable(currentType);
    }

    private Optional<PendingRegrowth> createPendingMushroom(Block block, PlantRegrowthConfig currentConfig) {
        Optional<TreeType> mushroomType = resolveMushroomType(block, currentConfig);
        if (mushroomType.isEmpty()) {
            return Optional.empty();
        }

        Optional<Block> baseBlock = findMushroomBase(block);
        if (baseBlock.isEmpty()) {
            return Optional.empty();
        }

        Block base = baseBlock.get();
        if (isSameBlock(block, base)) {
            cancelAnchoredRegrowth(base);
            return Optional.empty();
        }

        return Optional.of(new PendingRegrowth(
                base.getWorld().getUID(),
                base.getX(),
                base.getY(),
                base.getZ(),
                mushroomType.get(),
                base.getType(),
                new Random().nextLong(),
                0
        ));
    }

    private Optional<TreeType> resolveMushroomType(Block block, PlantRegrowthConfig currentConfig) {
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

    private boolean hasAnchorBlock(Location location, PendingRegrowth pending) {
        Material anchorMaterial = pending.anchorMaterial();
        return anchorMaterial == null || location.getBlock().getType() == anchorMaterial;
    }

    private boolean isSameBlock(Block first, Block second) {
        return first.getWorld().equals(second.getWorld())
                && first.getX() == second.getX()
                && first.getY() == second.getY()
                && first.getZ() == second.getZ();
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
        return new File(plugin.getDataFolder(), "queued-regrowth.yml");
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
