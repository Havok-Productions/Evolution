package org.slowtrees.regrowth;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
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
    private final ConcurrentMap<String, ActiveRegrowth> activeRegrowth = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> activeBlockKeys = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PlantDecayPlan> activeDecay = new ConcurrentHashMap<>();
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
        activeDecay.clear();
        saveQueuedRegrowth();
    }

    @Override
    public void reload() {
        this.config = PlantRegrowthConfig.load(plugin);
    }

    @Override
    public String status() {
        return "Plant regrowth has " + pendingRegrowth.size() + " queued structure(s), "
                + activeRegrowth.size() + " actively growing, "
                + activeDecay.size() + " decaying plant(s).";
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        PlantRegrowthConfig currentConfig = config;
        if (!currentConfig.isWorldAllowed(block.getWorld())) {
            return;
        }

        String brokenBlockKey = PendingRegrowth.keyFor(block);
        PendingRegrowth anchoredPending = pendingRegrowth.get(brokenBlockKey);
        if (anchoredPending != null && block.getType() == anchoredPending.anchorMaterial()) {
            schedulePlantDecay(block, currentConfig);
            cancelRegrowth(anchoredPending);
            return;
        }

        if (interruptActiveRegrowth(block, currentConfig)) {
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
            schedulePlantDecay(baseBlock, currentConfig);
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
            return;
        }

        ActiveRegrowth active = activeRegrowth.get(previous.key());
        if (active != null) {
            active.resetCooldown(currentConfig.growthStepTicks());
        }
    }

    private void cancelAnchoredRegrowth(Block baseBlock) {
        PendingRegrowth pending = pendingRegrowth.get(PendingRegrowth.keyFor(baseBlock));
        if (pending != null) {
            cancelRegrowth(pending);
        }
    }

    private void cancelRegrowth(PendingRegrowth pending) {
        pendingRegrowth.remove(pending.key());
        unregisterActiveRegrowth(pending.key());
        saveQueuedRegrowth();
    }

    private void schedulePlantDecay(Block baseBlock, PlantRegrowthConfig currentConfig) {
        if (!currentConfig.plantDecayEnabled() || !isDecayMaterial(baseBlock.getType())) {
            return;
        }

        Deque<PlantDecayPlan.DecayBlock> blocks = collectDecayBlocks(baseBlock, currentConfig.plantDecayMaxBlocks());
        if (blocks.isEmpty()) {
            return;
        }

        String key = PendingRegrowth.keyFor(baseBlock);
        PlantDecayPlan plan = new PlantDecayPlan(baseBlock.getType(), blocks);
        if (activeDecay.putIfAbsent(key, plan) != null) {
            return;
        }

        Location location = baseBlock.getLocation();
        Bukkit.getRegionScheduler().runDelayed(
                plugin,
                location,
                task -> runPlantDecay(key, location, plan),
                currentConfig.plantDecayDelayTicks()
        );
    }

    private Deque<PlantDecayPlan.DecayBlock> collectDecayBlocks(Block baseBlock, int maxBlocks) {
        Deque<PlantDecayPlan.DecayBlock> decayBlocks = new ArrayDeque<>();
        Queue<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        Material material = baseBlock.getType();
        int baseY = baseBlock.getY();

        queue.add(baseBlock.getRelative(0, 1, 0));
        while (!queue.isEmpty() && decayBlocks.size() < maxBlocks) {
            Block block = queue.poll();
            if (block.getY() <= baseY || !visited.add(PendingRegrowth.keyFor(block)) || !canInspectDecayBlock(block)) {
                continue;
            }
            if (block.getType() != material) {
                continue;
            }

            decayBlocks.add(new PlantDecayPlan.DecayBlock(block.getX(), block.getY(), block.getZ()));
            queue.add(block.getRelative(1, 0, 0));
            queue.add(block.getRelative(-1, 0, 0));
            queue.add(block.getRelative(0, 1, 0));
            queue.add(block.getRelative(0, -1, 0));
            queue.add(block.getRelative(0, 0, 1));
            queue.add(block.getRelative(0, 0, -1));
        }

        return decayBlocks;
    }

    private void runPlantDecay(String key, Location location, PlantDecayPlan plan) {
        PlantRegrowthConfig currentConfig = config;
        World world = location.getWorld();
        if (world == null || !currentConfig.plantDecayEnabled()) {
            activeDecay.remove(key, plan);
            return;
        }

        if (!canWorkAt(location, currentConfig)) {
            schedulePlantDecayStep(key, location, plan, currentConfig.retryDelayTicks());
            return;
        }

        int removed = 0;
        while (removed < currentConfig.plantDecayBlocksPerStep() && !plan.isFinished()) {
            PlantDecayPlan.DecayBlock next = plan.peekNext();
            if (next == null) {
                break;
            }
            if (!isDecayBlockLoadedAndOwned(world, next)) {
                schedulePlantDecayStep(key, location, plan, currentConfig.retryDelayTicks());
                return;
            }

            Block block = world.getBlockAt(next.x(), next.y(), next.z());
            plan.removeNext();
            if (block.getType() == plan.originalMaterial()) {
                block.setType(Material.AIR, false);
                removed++;
            }
        }

        if (plan.isFinished()) {
            activeDecay.remove(key, plan);
            return;
        }

        schedulePlantDecayStep(key, location, plan, currentConfig.plantDecayStepTicks());
    }

    private void schedulePlantDecayStep(String key, Location location, PlantDecayPlan plan, long delayTicks) {
        Bukkit.getRegionScheduler().runDelayed(
                plugin,
                location,
                task -> runPlantDecay(key, location, plan),
                Math.max(1L, delayTicks)
        );
    }

    private boolean interruptActiveRegrowth(Block block, PlantRegrowthConfig currentConfig) {
        String blockKey = PendingRegrowth.keyFor(block);
        String regrowthKey = activeBlockKeys.get(blockKey);
        if (regrowthKey == null) {
            return false;
        }

        ActiveRegrowth active = activeRegrowth.get(regrowthKey);
        if (active == null) {
            activeBlockKeys.remove(blockKey);
            return false;
        }

        active.unmarkPlaced(blockKey);
        activeBlockKeys.remove(blockKey);
        active.requeueFirst(block.getState());
        active.resetCooldown(currentConfig.growthStepTicks());
        return true;
    }

    private void unregisterActiveRegrowth(String regrowthKey) {
        ActiveRegrowth active = activeRegrowth.remove(regrowthKey);
        if (active != null) {
            for (String blockKey : active.placedBlockKeysSnapshot()) {
                activeBlockKeys.remove(blockKey, regrowthKey);
            }
        }
    }

    private void markPlaced(ActiveRegrowth active, BlockState state) {
        String blockKey = PendingRegrowth.keyFor(state.getBlock());
        active.markPlaced(blockKey);
        activeBlockKeys.put(blockKey, active.pending().key());
    }

    private void finishRegrowth(PendingRegrowth pending) {
        pendingRegrowth.remove(pending.key());
        unregisterActiveRegrowth(pending.key());
        saveQueuedRegrowth();
    }

    private void removeRegrowth(PendingRegrowth pending) {
        pendingRegrowth.remove(pending.key());
        unregisterActiveRegrowth(pending.key());
        saveQueuedRegrowth();
    }

    private void saveIfPresent(PendingRegrowth pending) {
        if (pendingRegrowth.containsKey(pending.key())) {
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
            removeRegrowth(pending);
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
            removeRegrowth(pending);
            return;
        }

        Location location = pending.location(world);
        if (!canWorkAt(location, currentConfig)) {
            retryLater(pending);
            return;
        }

        if (!hasAnchorBlock(location, pending)) {
            removeRegrowth(pending);
            return;
        }

        Deque<BlockState> plannedBlocks = planStructure(location, pending.treeType(), pending.seed(), currentConfig);
        if (plannedBlocks.isEmpty()) {
            retryLater(pending);
            return;
        }

        ActiveRegrowth active = new ActiveRegrowth(pending, plannedBlocks);
        activeRegrowth.put(pending.key(), active);
        placeNextBatch(active);
    }

    private Deque<BlockState> planStructure(Location location, TreeType treeType, long seed, PlantRegrowthConfig currentConfig) {
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

    private void placeNextBatch(ActiveRegrowth active) {
        PendingRegrowth pending = active.pending();
        World world = pending.world();
        if (world == null) {
            removeRegrowth(pending);
            return;
        }

        PlantRegrowthConfig currentConfig = config;
        Location location = pending.location(world);
        long remainingCooldownTicks = active.remainingCooldownTicks();
        if (remainingCooldownTicks > 0L) {
            Bukkit.getRegionScheduler().runDelayed(
                    plugin,
                    location,
                    task -> placeNextBatch(active),
                    remainingCooldownTicks
            );
            return;
        }

        if (!canWorkAt(location, currentConfig)) {
            Bukkit.getRegionScheduler().runDelayed(
                    plugin,
                    location,
                    task -> placeNextBatch(active),
                    currentConfig.retryDelayTicks()
            );
            return;
        }

        if (!hasAnchorBlock(location, pending)) {
            removeRegrowth(pending);
            return;
        }

        int placed = 0;
        while (placed < currentConfig.blocksPerGrowthStep() && !active.isFinished()) {
            BlockState state = active.pollNextBlock();
            if (state != null && canPlace(state, currentConfig)) {
                state.update(true, false);
                markPlaced(active, state);
                placed++;
            }
        }

        if (active.isFinished()) {
            finishRegrowth(pending);
            return;
        }

        Bukkit.getRegionScheduler().runDelayed(
                plugin,
                location,
                task -> placeNextBatch(active),
                currentConfig.growthStepTicks()
        );
    }

    private void retryLater(PendingRegrowth pending) {
        PlantRegrowthConfig currentConfig = config;
        pending.incrementAttempts();
        if (currentConfig.maxRegrowthAttempts() > 0 && pending.attempts() >= currentConfig.maxRegrowthAttempts()) {
            removeRegrowth(pending);
            return;
        }

        saveIfPresent(pending);
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

    private boolean canInspectDecayBlock(Block block) {
        int chunkX = block.getX() >> 4;
        int chunkZ = block.getZ() >> 4;
        return block.getWorld().isChunkLoaded(chunkX, chunkZ)
                && Bukkit.isOwnedByCurrentRegion(block.getWorld(), chunkX, chunkZ, 0);
    }

    private boolean isDecayBlockLoadedAndOwned(World world, PlantDecayPlan.DecayBlock block) {
        int chunkX = block.x() >> 4;
        int chunkZ = block.z() >> 4;
        return world.isChunkLoaded(chunkX, chunkZ)
                && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0);
    }

    private boolean isDecayMaterial(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_STEM") || material == Material.MUSHROOM_STEM;
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
            schedulePlantDecay(base, currentConfig);
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
