package org.slowtrees.meadow;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.slowtrees.core.PluginFeature;
import org.slowtrees.core.ResourceReporter.ReportSample;
import org.slowtrees.core.SlowTreesPlugin;

public final class MeadowGrowthFeature implements PluginFeature, Listener {
    private static final Set<Material> SPREADABLE_GROUND = Set.of(
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT,
            Material.PODZOL,
            Material.MOSS_BLOCK
    );
    private static final Set<Material> MEADOW_GROUND = Set.of(
            Material.GRASS_BLOCK,
            Material.MOSS_BLOCK,
            Material.PODZOL
    );
    private static final Set<Material> REPLACEABLE_PLANT_SPACE = Set.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.SHORT_GRASS,
            Material.FERN,
            Material.LEAF_LITTER,
            Material.SNOW
    );
    private static final Set<Material> MEADOW_PLANTS = Set.of(
            Material.SHORT_GRASS,
            Material.TALL_GRASS,
            Material.FERN,
            Material.LARGE_FERN,
            Material.DANDELION,
            Material.POPPY,
            Material.BLUE_ORCHID,
            Material.ALLIUM,
            Material.AZURE_BLUET,
            Material.RED_TULIP,
            Material.ORANGE_TULIP,
            Material.WHITE_TULIP,
            Material.PINK_TULIP,
            Material.OXEYE_DAISY,
            Material.CORNFLOWER,
            Material.LILY_OF_THE_VALLEY,
            Material.PINK_PETALS
    );

    private final SlowTreesPlugin plugin;
    private final Random random = new Random();
    private final AtomicLong grassBlocksSpread = new AtomicLong();
    private final AtomicLong plantsGrown = new AtomicLong();
    private final AtomicLong flowersGrown = new AtomicLong();
    private volatile MeadowGrowthConfig config;

    public MeadowGrowthFeature(SlowTreesPlugin plugin) {
        this.plugin = plugin;
        this.config = MeadowGrowthConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "meadow", "config.load", config.summary());
    }

    @Override
    public void onEnable() {
        plugin.pathDebug().trace(plugin, "meadow", "enable.schedule-online-players", "players=" + Bukkit.getOnlinePlayers().size());
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayerMeadow(player, 40L);
        }
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void reload() {
        this.config = MeadowGrowthConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "meadow", "config.reload", config.summary());
    }

    @Override
    public String status() {
        return "Meadow growth spread " + grassBlocksSpread.get()
                + " grass block(s), grew " + plantsGrown.get()
                + " plant(s), and grew " + flowersGrown.get() + " flower(s).";
    }

    public boolean enabled() {
        return config.enabled();
    }

    public long grassBlocksSpread() {
        return grassBlocksSpread.get();
    }

    public long plantsGrown() {
        return plantsGrown.get();
    }

    public long flowersGrown() {
        return flowersGrown.get();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        schedulePlayerMeadow(event.getPlayer(), 40L);
    }

    private void schedulePlayerMeadow(Player player, long delayTicks) {
        plugin.pathDebug().traceSampled(plugin, "meadow", "scheduler.player-delay", "delay=" + Math.max(1L, delayTicks));
        player.getScheduler().runDelayed(
                plugin,
                task -> runNearPlayer(player),
                null,
                Math.max(1L, delayTicks)
        );
    }

    private void runNearPlayer(Player player) {
        try (ReportSample sample = plugin.resourceReporter().begin("meadow", "tick.run-near-player")) {
            MeadowGrowthConfig currentConfig = config;
            if (!player.isOnline()) {
                plugin.pathDebug().trace(plugin, "meadow", "tick.skip.offline-player", "player no longer online");
                sample.detail("offline-player");
                return;
            }

            if (!currentConfig.enabled()) {
                plugin.pathDebug().trace(plugin, "meadow", "tick.skip.disabled", "step=" + currentConfig.stepTicks());
                schedulePlayerMeadow(player, currentConfig.stepTicks());
                sample.detail("disabled");
                return;
            }

            Location origin = player.getLocation();
            World world = origin.getWorld();
            if (world == null || world.getEnvironment() != World.Environment.NORMAL || !currentConfig.isWorldAllowed(world)) {
                plugin.pathDebug().trace(plugin, "meadow", "tick.skip.environment", world == null ? "missing-world" : world.getName());
                schedulePlayerMeadow(player, currentConfig.stepTicks());
                sample.detail("environment-skip");
                return;
            }

            int changed = 0;
            int attempts = 0;
            for (int attempt = 0; attempt < currentConfig.attemptsPerStep() && changed < currentConfig.blocksPerStep(); attempt++) {
                attempts++;
                Optional<Block> target = findTarget(origin, currentConfig);
                if (target.isEmpty()) {
                    continue;
                }

                if (growAt(target.get(), currentConfig)) {
                    changed++;
                }
            }

            sample.workUnits(attempts).changedUnits(changed).detail("changed=" + changed + " near=" + format(origin));
            plugin.pathDebug().traceSampled(plugin, "meadow", changed > 0 ? "growth.step.changed" : "growth.step.no-change",
                    "changed=" + changed + " near=" + format(origin));
            schedulePlayerMeadow(player, currentConfig.stepTicks());
        }
    }

    private Optional<Block> findTarget(Location origin, MeadowGrowthConfig currentConfig) {
        try (ReportSample sample = plugin.resourceReporter().begin("meadow", "search.find-target")) {
            World world = origin.getWorld();
            if (world == null) {
                sample.detail("missing-world");
                return Optional.empty();
            }

            int radius = currentConfig.searchRadius();
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
            if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                plugin.pathDebug().failure(plugin, "meadow", "chunk-or-region-gate", "target chunk " + chunkX + "," + chunkZ);
                sample.detail("chunk-or-region");
                return Optional.empty();
            }

            Block highest = world.getHighestBlockAt(x, z);
            if (highest.getY() <= world.getMinHeight()) {
                sample.detail("min-height");
                return Optional.empty();
            }

            Block ground = highest.getType().isAir() ? highest.getRelative(0, -1, 0) : highest;
            if (!isSurfaceCandidate(ground)) {
                ground = ground.getRelative(0, -1, 0);
            }
            if (!isNearPlayer(ground.getLocation(), currentConfig.requiredPlayerDistanceChunks())) {
                plugin.pathDebug().failure(plugin, "meadow", "player-distance", format(ground));
                sample.detail("player-distance");
                return Optional.empty();
            }
            sample.changedUnits(1).detail("found " + format(ground));
            return Optional.of(ground);
        }
    }

    private boolean growAt(Block block, MeadowGrowthConfig currentConfig) {
        try (ReportSample sample = plugin.resourceReporter().begin("meadow", "action.grow-at")) {
            if (!hasSurfaceLight(block) || block.isLiquid()) {
                sample.detail("surface-light-or-liquid");
                return false;
            }

            Material type = block.getType();
            boolean changed;
            if (SPREADABLE_GROUND.contains(type)) {
                changed = spreadGrass(block, currentConfig);
            } else if (MEADOW_GROUND.contains(type)) {
                changed = growSurfacePlant(block, currentConfig);
            } else if ((type == Material.SHORT_GRASS || type == Material.FERN) && random.nextInt(100) < currentConfig.heightGrowthChancePercent()) {
                changed = growPlantTaller(block, currentConfig);
            } else {
                changed = false;
            }

            sample.changedUnits(changed ? 1L : 0L).detail(type + " at " + format(block));
            return changed;
        }
    }

    private boolean spreadGrass(Block ground, MeadowGrowthConfig currentConfig) {
        if (random.nextInt(100) >= currentConfig.grassSpreadChancePercent()) {
            return false;
        }

        Block above = ground.getRelative(0, 1, 0);
        if (!canReplacePlantSpace(above, currentConfig) || !hasNearbyMeadowSource(ground)) {
            return false;
        }

        ground.setType(Material.GRASS_BLOCK, false);
        grassBlocksSpread.incrementAndGet();
        plugin.pathDebug().trace(plugin, "meadow", "growth.grass-spread", format(ground));
        return true;
    }

    private boolean growSurfacePlant(Block ground, MeadowGrowthConfig currentConfig) {
        if (random.nextInt(100) >= currentConfig.plantGrowChancePercent()) {
            return false;
        }

        Block target = ground.getRelative(0, 1, 0);
        if (!canReplacePlantSpace(target, currentConfig) || target.isLiquid()) {
            return false;
        }

        if (currentConfig.maxPlantsPerArea() > 0 && countNearbyPlants(target, 5) >= currentConfig.maxPlantsPerArea()) {
            return false;
        }

        Material plant = choosePlant(target.getBiome(), target, currentConfig);
        if (plant == Material.TALL_GRASS || plant == Material.LARGE_FERN) {
            return placeTallPlant(target, plant);
        }

        target.setType(plant, false);
        plantsGrown.incrementAndGet();
        if (isFlower(plant)) {
            flowersGrown.incrementAndGet();
            plugin.pathDebug().trace(plugin, "meadow", "growth.flower", format(target) + " " + plant);
        } else {
            plugin.pathDebug().trace(plugin, "meadow", "growth.plant", format(target) + " " + plant);
        }
        return true;
    }

    private boolean growPlantTaller(Block plant, MeadowGrowthConfig currentConfig) {
        Block above = plant.getRelative(0, 1, 0);
        if (!above.getType().isAir()) {
            return false;
        }

        Material tallType = plant.getType() == Material.FERN ? Material.LARGE_FERN : Material.TALL_GRASS;
        return placeTallPlant(plant, tallType);
    }

    private boolean placeTallPlant(Block lower, Material material) {
        Block upper = lower.getRelative(0, 1, 0);
        if (!upper.getType().isAir()) {
            return false;
        }

        BlockData lowerData = Bukkit.createBlockData(material);
        BlockData upperData = Bukkit.createBlockData(material);
        if (lowerData instanceof Bisected lowerBisected && upperData instanceof Bisected upperBisected) {
            lowerBisected.setHalf(Bisected.Half.BOTTOM);
            upperBisected.setHalf(Bisected.Half.TOP);
        }

        lower.setBlockData(lowerData, false);
        upper.setBlockData(upperData, false);
        plantsGrown.incrementAndGet();
        plugin.pathDebug().trace(plugin, "meadow", "growth.height", format(lower) + " " + material);
        return true;
    }

    private Material choosePlant(Biome biome, Block target, MeadowGrowthConfig currentConfig) {
        Material clustered = nearbyFlower(target);
        if (clustered != null && countNearbyFlowers(target, 5) < 4 && random.nextInt(100) < 42) {
            return clustered;
        }

        if (isWetPocket(target)) {
            return biomeKey(biome).contains("SWAMP") ? Material.BLUE_ORCHID : (random.nextBoolean() ? Material.FERN : Material.SHORT_GRASS);
        }
        if (isSlopedPocket(target.getRelative(BlockFace.DOWN))) {
            return random.nextInt(100) < 68 ? Material.SHORT_GRASS : Material.FERN;
        }
        if (target.getLightFromSky() < 7) {
            return random.nextInt(100) < 58 ? Material.FERN : Material.SHORT_GRASS;
        }

        Material rareFeature = rareSurfaceFeature(biome, target);
        if (rareFeature != null) {
            return rareFeature;
        }

        int flowerChance = currentConfig.maxPlantsPerArea() > 0 && countNearbyPlants(target, 5) > currentConfig.maxPlantsPerArea() / 2
                ? Math.max(4, currentConfig.flowerChancePercent() / 2)
                : currentConfig.flowerChancePercent();
        if (countNearbyFlowers(target, 6) < 4 && random.nextInt(100) < flowerChance) {
            return flowerForBiome(biome, target.getX(), target.getZ());
        }

        String biomeKey = biomeKey(biome);
        if (biomeKey.contains("TAIGA") || biomeKey.contains("OLD_GROWTH") || biomeKey.contains("FOREST")) {
            return random.nextInt(100) < 30 ? Material.FERN : Material.SHORT_GRASS;
        }
        return random.nextInt(100) < 12 ? Material.FERN : Material.SHORT_GRASS;
    }

    private Material rareSurfaceFeature(Biome biome, Block target) {
        if (random.nextInt(100) >= 3 || countNearbyRareFeatures(target, 12) >= 2) {
            return null;
        }
        String key = biomeKey(biome);
        if (hasAdjacentWater(target.getRelative(BlockFace.DOWN)) && random.nextBoolean()) {
            return Material.SUGAR_CANE;
        }
        if (key.contains("JUNGLE")) {
            return target.getLightFromSky() >= 9 ? Material.MELON : null;
        }
        if (key.contains("TAIGA") || key.contains("OLD_GROWTH")) {
            return Material.SWEET_BERRY_BUSH;
        }
        if (key.contains("SAVANNA") || key.contains("DESERT") || key.contains("BADLANDS")) {
            return Material.DEAD_BUSH;
        }
        if (key.contains("FOREST") || key.contains("PLAINS") || key.contains("MEADOW")) {
            return target.getLightFromSky() >= 9 ? Material.PUMPKIN : null;
        }
        return null;
    }

    private boolean isWetPocket(Block target) {
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN)) {
            if (target.getRelative(face).isLiquid()) {
                return true;
            }
        }
        return false;
    }

    private boolean isSlopedPocket(Block ground) {
        int uneven = 0;
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Block neighbor = ground.getRelative(face);
            if (!MEADOW_GROUND.contains(neighbor.getType()) && !SPREADABLE_GROUND.contains(neighbor.getType())
                    && !MEADOW_GROUND.contains(neighbor.getRelative(BlockFace.DOWN).getType())
                    && !SPREADABLE_GROUND.contains(neighbor.getRelative(BlockFace.DOWN).getType())) {
                uneven++;
            }
        }
        return uneven >= 2;
    }

    private boolean hasAdjacentWater(Block ground) {
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            if (ground.getRelative(face).isLiquid() || ground.getRelative(face).getRelative(BlockFace.UP).isLiquid()) {
                return true;
            }
        }
        return false;
    }

    private Material flowerForBiome(Biome biome, int x, int z) {
        String name = biomeKey(biome);
        if (name.contains("SWAMP")) {
            return Material.BLUE_ORCHID;
        }
        if (name.contains("CHERRY")) {
            return Material.PINK_PETALS;
        }
        if (name.contains("MEADOW")) {
            return pickStable(x, z, List.of(
                    Material.DANDELION,
                    Material.POPPY,
                    Material.ALLIUM,
                    Material.AZURE_BLUET,
                    Material.OXEYE_DAISY,
                    Material.CORNFLOWER
            ));
        }
        if (name.contains("FOREST") || name.contains("BIRCH")) {
            return pickStable(x, z, List.of(
                    Material.OXEYE_DAISY,
                    Material.LILY_OF_THE_VALLEY,
                    Material.POPPY,
                    Material.DANDELION
            ));
        }
        return pickStable(x, z, List.of(
                Material.DANDELION,
                Material.POPPY,
                Material.AZURE_BLUET,
                Material.OXEYE_DAISY,
                Material.CORNFLOWER
        ));
    }

    private String biomeKey(Biome biome) {
        return biome.getKey().getKey().toUpperCase(Locale.ROOT);
    }

    private Material pickStable(int x, int z, List<Material> materials) {
        int cellX = Math.floorDiv(x, 7);
        int cellZ = Math.floorDiv(z, 7);
        int index = Math.floorMod((cellX * 73428767) ^ (cellZ * 912931), materials.size());
        return materials.get(index);
    }

    private Material nearbyFlower(Block target) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if ((dx * dx) + (dz * dz) > 9) {
                    continue;
                }
                Material type = target.getRelative(dx, 0, dz).getType();
                if (isFlower(type)) {
                    return type;
                }
            }
        }
        return null;
    }

    private boolean hasNearbyMeadowSource(Block ground) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }

                    Block neighbor = ground.getRelative(dx, dy, dz);
                    Material type = neighbor.getType();
                    if (MEADOW_GROUND.contains(type) || MEADOW_PLANTS.contains(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int countNearbyPlants(Block center, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (MEADOW_PLANTS.contains(center.getRelative(dx, dy, dz).getType())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int countNearbyFlowers(Block center, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (isFlower(center.getRelative(dx, dy, dz).getType())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int countNearbyRareFeatures(Block center, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Material type = center.getRelative(dx, dy, dz).getType();
                    if (type == Material.PUMPKIN
                            || type == Material.MELON
                            || type == Material.SWEET_BERRY_BUSH
                            || type == Material.SUGAR_CANE
                            || type == Material.DEAD_BUSH) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private boolean isSurfaceCandidate(Block block) {
        return SPREADABLE_GROUND.contains(block.getType())
                || MEADOW_GROUND.contains(block.getType())
                || block.getType() == Material.SHORT_GRASS
                || block.getType() == Material.FERN;
    }

    private boolean canReplacePlantSpace(Block block, MeadowGrowthConfig currentConfig) {
        if (!REPLACEABLE_PLANT_SPACE.contains(block.getType())) {
            return false;
        }
        return currentConfig.replaceLeafLitter() || block.getType() != Material.LEAF_LITTER;
    }

    private boolean hasSurfaceLight(Block block) {
        Block above = block.getRelative(0, 1, 0);
        return above.getLightFromSky() > 0 || above.getLightLevel() >= 9;
    }

    private boolean isFlower(Material material) {
        return material == Material.DANDELION
                || material == Material.POPPY
                || material == Material.BLUE_ORCHID
                || material == Material.ALLIUM
                || material == Material.AZURE_BLUET
                || material == Material.RED_TULIP
                || material == Material.ORANGE_TULIP
                || material == Material.WHITE_TULIP
                || material == Material.PINK_TULIP
                || material == Material.OXEYE_DAISY
                || material == Material.CORNFLOWER
                || material == Material.LILY_OF_THE_VALLEY
                || material == Material.PINK_PETALS;
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
            int playerChunkX = player.getLocation().getBlockX() >> 4;
            int playerChunkZ = player.getLocation().getBlockZ() >> 4;
            if (Math.abs(playerChunkX - chunkX) <= distanceChunks && Math.abs(playerChunkZ - chunkZ) <= distanceChunks) {
                return true;
            }
        }
        return false;
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
