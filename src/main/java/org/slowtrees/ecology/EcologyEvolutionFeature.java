package org.slowtrees.ecology;

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
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.slowtrees.core.PluginFeature;
import org.slowtrees.core.SlowTreesPlugin;

public final class EcologyEvolutionFeature implements PluginFeature, Listener {
    private static final List<BlockFace> HORIZONTAL_FACES = List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
    private static final Set<Material> NATURAL_GROUND = Set.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT,
            Material.PODZOL,
            Material.MOSS_BLOCK
    );
    private static final Set<Material> REPLACEABLE_DETAIL_SPACE = Set.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.SHORT_GRASS,
            Material.FERN,
            Material.LEAF_LITTER,
            Material.SNOW
    );

    private final SlowTreesPlugin plugin;
    private final Random random = new Random();
    private final EcologyEvolutionDiagnostics diagnostics = new EcologyEvolutionDiagnostics();
    private final AtomicLong changedBlocks = new AtomicLong();
    private volatile EcologyEvolutionConfig config;

    public EcologyEvolutionFeature(SlowTreesPlugin plugin) {
        this.plugin = plugin;
        this.config = EcologyEvolutionConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "ecology", "config.load", config.summary());
    }

    @Override
    public void onEnable() {
        diagnostics.saveNow(plugin, config);
        plugin.pathDebug().trace(plugin, "ecology", "enable.schedule-online-players", "players=" + Bukkit.getOnlinePlayers().size());
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayerEvolution(player, 60L);
        }
    }

    @Override
    public void onDisable() {
        diagnostics.saveNow(plugin, config);
    }

    @Override
    public void reload() {
        this.config = EcologyEvolutionConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "ecology", "config.reload", config.summary());
    }

    @Override
    public String status() {
        diagnostics.saveAsync(plugin, config);
        return "Ecology evolution changed " + changedBlocks.get() + " natural detail block(s).";
    }

    public boolean enabled() {
        return config.enabled();
    }

    public long changedBlockCount() {
        return changedBlocks.get();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        schedulePlayerEvolution(event.getPlayer(), 60L);
    }

    private void schedulePlayerEvolution(Player player, long delayTicks) {
        plugin.pathDebug().traceSampled(plugin, "ecology", "scheduler.player-delay", "delay=" + Math.max(1L, delayTicks));
        player.getScheduler().runDelayed(
                plugin,
                task -> runNearPlayer(player),
                null,
                Math.max(1L, delayTicks)
        );
    }

    private void runNearPlayer(Player player) {
        EcologyEvolutionConfig currentConfig = config;
        if (!player.isOnline()) {
            plugin.pathDebug().trace(plugin, "ecology", "tick.skip.offline-player", "player no longer online");
            return;
        }

        if (!currentConfig.enabled()) {
            plugin.pathDebug().trace(plugin, "ecology", "tick.skip.disabled", "step=" + currentConfig.stepTicks());
            schedulePlayerEvolution(player, currentConfig.stepTicks());
            return;
        }

        Location origin = player.getLocation();
        World world = origin.getWorld();
        if (world == null || world.getEnvironment() != World.Environment.NORMAL || !currentConfig.isWorldAllowed(world)) {
            plugin.pathDebug().trace(plugin, "ecology", "tick.skip.environment", world == null ? "missing-world" : world.getName());
            schedulePlayerEvolution(player, currentConfig.stepTicks());
            return;
        }

        int changed = 0;
        for (int attempt = 0; attempt < currentConfig.attemptsPerStep() && changed < currentConfig.blocksPerStep(); attempt++) {
            Optional<TreeCandidate> candidate = findCandidate(origin, currentConfig);
            if (candidate.isEmpty()) {
                continue;
            }

            if (evolve(candidate.get(), currentConfig)) {
                changed++;
            }
        }

        plugin.pathDebug().traceSampled(plugin, "ecology", changed > 0 ? "evolution.step.changed" : "evolution.step.no-change",
                "changed=" + changed + " near=" + format(origin));
        schedulePlayerEvolution(player, currentConfig.stepTicks());
    }

    private Optional<TreeCandidate> findCandidate(Location origin, EcologyEvolutionConfig currentConfig) {
        World world = origin.getWorld();
        if (world == null) {
            return Optional.empty();
        }

        diagnostics.recordSearch();
        int radius = currentConfig.searchRadius();
        int dx = random.nextInt(radius * 2 + 1) - radius;
        int dz = random.nextInt(radius * 2 + 1) - radius;
        if ((dx * dx) + (dz * dz) > radius * radius) {
            return Optional.empty();
        }

        int x = origin.getBlockX() + dx;
        int z = origin.getBlockZ() + dz;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            diagnostics.recordReject();
            plugin.pathDebug().failure(plugin, "ecology", "chunk-or-region-gate", "candidate chunk " + chunkX + "," + chunkZ);
            return Optional.empty();
        }

        Block highest = world.getHighestBlockAt(x, z);
        int minY = Math.max(world.getMinHeight(), highest.getY() - 28);
        for (int y = highest.getY(); y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (!isLog(block.getType())) {
                continue;
            }

            TreeCandidate candidate = buildCandidate(block);
            if (candidate != null && isNearPlayer(candidate.base().getLocation(), currentConfig.requiredPlayerDistanceChunks())) {
                diagnostics.recordCandidate();
                plugin.pathDebug().trace(plugin, "ecology", "candidate.tree",
                        candidate.logMaterial() + " base=" + format(candidate.base()) + " height=" + candidate.height());
                return Optional.of(candidate);
            }
        }

        diagnostics.recordReject();
        return Optional.empty();
    }

    private TreeCandidate buildCandidate(Block logBlock) {
        Material logMaterial = logBlock.getType();
        Block base = logBlock;
        while (base.getY() > base.getWorld().getMinHeight() && base.getRelative(BlockFace.DOWN).getType() == logMaterial) {
            base = base.getRelative(BlockFace.DOWN);
        }

        Block top = logBlock;
        while (top.getY() < top.getWorld().getMaxHeight() - 1 && top.getRelative(BlockFace.UP).getType() == logMaterial) {
            top = top.getRelative(BlockFace.UP);
        }

        int height = top.getY() - base.getY() + 1;
        if (height < 3 || height > 28 || !NATURAL_GROUND.contains(base.getRelative(BlockFace.DOWN).getType())) {
            return null;
        }

        Material leafMaterial = preferredLeafMaterial(logMaterial);
        if (!hasNearbyLeaves(top, leafMaterial, 4)) {
            return null;
        }

        return new TreeCandidate(base, top, logMaterial, leafMaterial, height);
    }

    private boolean evolve(TreeCandidate candidate, EcologyEvolutionConfig currentConfig) {
        int roll = random.nextInt(100);
        if (roll < currentConfig.heightChancePercent() && growTaller(candidate, currentConfig)) {
            return true;
        }
        roll -= currentConfig.heightChancePercent();
        if (roll < currentConfig.branchChancePercent() && growBranch(candidate, currentConfig)) {
            return true;
        }
        roll -= currentConfig.branchChancePercent();
        if (roll < currentConfig.canopyChancePercent() && growCanopy(candidate, currentConfig)) {
            return true;
        }
        return random.nextInt(100) < currentConfig.forestFloorChancePercent() && growForestFloor(candidate, currentConfig);
    }

    private boolean growTaller(TreeCandidate candidate, EcologyEvolutionConfig currentConfig) {
        int naturalCap = Math.max(candidate.height(), 5) + currentConfig.maxTreeHeightBonus();
        if (candidate.height() >= naturalCap) {
            return false;
        }

        Block target = candidate.top().getRelative(BlockFace.UP);
        if (!canReplaceForTreeGrowth(target, candidate.leafMaterial())) {
            return false;
        }

        target.setType(candidate.logMaterial(), false);
        changedBlocks.incrementAndGet();
        placeLeafCluster(target, candidate.leafMaterial(), 1);
        diagnostics.recordAction(plugin, currentConfig, "height", target, "log=" + candidate.logMaterial());
        plugin.pathDebug().trace(plugin, "ecology", "evolution.height", candidate.logMaterial() + " at " + format(target));
        return true;
    }

    private boolean growBranch(TreeCandidate candidate, EcologyEvolutionConfig currentConfig) {
        if (currentConfig.maxBranchesNearTree() > 0 && countSideBranches(candidate) >= currentConfig.maxBranchesNearTree()) {
            return false;
        }

        int branchY = Math.max(candidate.base().getY() + 2, candidate.top().getY() - random.nextInt(Math.max(1, Math.min(3, candidate.height()))));
        Block source = candidate.base().getWorld().getBlockAt(candidate.base().getX(), branchY, candidate.base().getZ());
        if (source.getType() != candidate.logMaterial()) {
            return false;
        }

        BlockFace face = HORIZONTAL_FACES.get(random.nextInt(HORIZONTAL_FACES.size()));
        Block branch = source.getRelative(face);
        if (!canReplaceForTreeGrowth(branch, candidate.leafMaterial())) {
            return false;
        }

        branch.setType(candidate.logMaterial(), false);
        changedBlocks.incrementAndGet();
        placeLeafCluster(branch, candidate.leafMaterial(), 2);
        diagnostics.recordAction(plugin, currentConfig, "branch", branch, "from=" + format(source) + " face=" + face);
        plugin.pathDebug().trace(plugin, "ecology", "evolution.branch", candidate.logMaterial() + " at " + format(branch));
        return true;
    }

    private boolean growCanopy(TreeCandidate candidate, EcologyEvolutionConfig currentConfig) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = candidate.top().getX() + random.nextInt(7) - 3;
            int y = candidate.top().getY() + random.nextInt(5) - 2;
            int z = candidate.top().getZ() + random.nextInt(7) - 3;
            Block target = candidate.top().getWorld().getBlockAt(x, y, z);
            if (!isAirLike(target.getType()) || !hasAdjacentLeaf(target, candidate.leafMaterial())) {
                continue;
            }

            target.setType(candidate.leafMaterial(), false);
            changedBlocks.incrementAndGet();
            diagnostics.recordAction(plugin, currentConfig, "canopy", target, "leaf=" + candidate.leafMaterial());
            plugin.pathDebug().trace(plugin, "ecology", "evolution.canopy", candidate.leafMaterial() + " at " + format(target));
            return true;
        }
        return false;
    }

    private boolean growForestFloor(TreeCandidate candidate, EcologyEvolutionConfig currentConfig) {
        int radius = 2 + random.nextInt(4);
        int x = candidate.base().getX() + random.nextInt(radius * 2 + 1) - radius;
        int z = candidate.base().getZ() + random.nextInt(radius * 2 + 1) - radius;
        Block highest = candidate.base().getWorld().getHighestBlockAt(x, z);
        Block ground = highest.getType().isAir() ? highest.getRelative(BlockFace.DOWN) : highest;
        Block target = ground.getRelative(BlockFace.UP);
        if (!NATURAL_GROUND.contains(ground.getType()) || !REPLACEABLE_DETAIL_SPACE.contains(target.getType()) || target.isLiquid()) {
            return false;
        }
        if (!Bukkit.isOwnedByCurrentRegion(target.getWorld(), target.getX() >> 4, target.getZ() >> 4, 0)) {
            return false;
        }

        Material detail = forestFloorDetail(candidate.base().getBiome());
        target.setType(detail, false);
        changedBlocks.incrementAndGet();
        diagnostics.recordAction(plugin, currentConfig, "floor", target, "detail=" + detail + " below=" + ground.getType());
        plugin.pathDebug().trace(plugin, "ecology", "evolution.floor", detail + " at " + format(target));
        return true;
    }

    private void placeLeafCluster(Block center, Material leafMaterial, int radius) {
        for (BlockFace face : HORIZONTAL_FACES) {
            if (random.nextBoolean()) {
                placeLeafIfAir(center.getRelative(face), leafMaterial);
            }
        }
        if (radius > 1) {
            placeLeafIfAir(center.getRelative(BlockFace.UP), leafMaterial);
            placeLeafIfAir(center.getRelative(BlockFace.DOWN), leafMaterial);
        }
    }

    private void placeLeafIfAir(Block block, Material leafMaterial) {
        if (isAirLike(block.getType())) {
            block.setType(leafMaterial, false);
            changedBlocks.incrementAndGet();
        }
    }

    private int countSideBranches(TreeCandidate candidate) {
        int branches = 0;
        for (int y = candidate.base().getY() + 1; y <= candidate.top().getY(); y++) {
            Block trunk = candidate.base().getWorld().getBlockAt(candidate.base().getX(), y, candidate.base().getZ());
            for (BlockFace face : HORIZONTAL_FACES) {
                if (trunk.getRelative(face).getType() == candidate.logMaterial()) {
                    branches++;
                }
            }
        }
        return branches;
    }

    private boolean hasNearbyLeaves(Block center, Material leafMaterial, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Material type = center.getRelative(x, y, z).getType();
                    if (type == leafMaterial || type.name().endsWith("_LEAVES")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasAdjacentLeaf(Block target, Material leafMaterial) {
        for (BlockFace face : List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Material type = target.getRelative(face).getType();
            if (type == leafMaterial || type.name().endsWith("_LEAVES")) {
                return true;
            }
        }
        return false;
    }

    private boolean canReplaceForTreeGrowth(Block block, Material leafMaterial) {
        Material type = block.getType();
        return isAirLike(type) || type == leafMaterial || type == Material.VINE;
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

    private Material preferredLeafMaterial(Material logMaterial) {
        String name = logMaterial.name();
        if (name.endsWith("_STEM")) {
            return name.startsWith("WARPED") ? Material.WARPED_WART_BLOCK : Material.NETHER_WART_BLOCK;
        }
        String leafName = name.replace("_LOG", "_LEAVES");
        Material leaves = Material.matchMaterial(leafName);
        return leaves == null ? Material.OAK_LEAVES : leaves;
    }

    private Material forestFloorDetail(Biome biome) {
        String name = biome.getKey().getKey().toUpperCase(Locale.ROOT);
        if (name.contains("TAIGA") || name.contains("OLD_GROWTH")) {
            return random.nextBoolean() ? Material.FERN : Material.LEAF_LITTER;
        }
        if (name.contains("JUNGLE")) {
            return random.nextBoolean() ? Material.SHORT_GRASS : Material.FERN;
        }
        return Material.LEAF_LITTER;
    }

    private boolean isAirLike(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private boolean isLog(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_STEM") || material == Material.MUSHROOM_STEM;
    }

    private String format(Block block) {
        return format(block.getLocation());
    }

    private String format(Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "unknown" : world.getName();
        return worldName + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private record TreeCandidate(Block base, Block top, Material logMaterial, Material leafMaterial, int height) {
    }
}
