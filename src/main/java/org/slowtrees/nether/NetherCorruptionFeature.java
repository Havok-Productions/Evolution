package org.slowtrees.nether;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.slowtrees.core.PluginFeature;
import org.slowtrees.core.SlowTreesPlugin;

public final class NetherCorruptionFeature implements PluginFeature, Listener {
    private final SlowTreesPlugin plugin;
    private final ConcurrentMap<String, PortalSource> sources = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> nextPortalScanMillis = new ConcurrentHashMap<>();
    private final NetherTerrainMimic terrainMimic = new NetherTerrainMimic();
    private final NetherMapDebug mapDebug = new NetherMapDebug();
    private final Random random = new Random();
    private final AtomicLong changedBlocks = new AtomicLong();
    private volatile NetherCorruptionConfig config;

    public NetherCorruptionFeature(SlowTreesPlugin plugin) {
        this.plugin = plugin;
        this.config = NetherCorruptionConfig.load(plugin);
    }

    @Override
    public void onEnable() {
        plugin.pathDebug().trace(plugin, "nether", "enable.load-sources", "loading stored portal sources");
        loadSources();
    }

    @Override
    public void onDisable() {
        nextPortalScanMillis.clear();
        mapDebug.saveNow(plugin);
        saveSources();
    }

    @Override
    public void reload() {
        this.config = NetherCorruptionConfig.load(plugin);
    }

    @Override
    public String status() {
        return "Nether corruption is tracking " + sources.size()
                + " portal source(s), changed " + changedBlocks.get() + " block(s) since reload.";
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        NetherCorruptionConfig currentConfig = config;
        Location location = event.getTo();
        World world = location.getWorld();
        if (!currentConfig.enabled() || world == null || world.getEnvironment() != World.Environment.NORMAL) {
            plugin.pathDebug().trace(plugin, "nether", "portal-scan.skip", "enabled=" + currentConfig.enabled()
                    + " world=" + (world == null ? "none" : world.getEnvironment()));
            return;
        }

        String key = event.getPlayer().getUniqueId().toString();
        long now = System.currentTimeMillis();
        long nextScan = nextPortalScanMillis.getOrDefault(key, 0L);
        if (now < nextScan) {
            return;
        }

        nextPortalScanMillis.put(key, now + 5000L);
        plugin.pathDebug().trace(plugin, "nether", "portal-scan.start", "radius=" + currentConfig.playerPortalScanRadius());
        findNearbyPortalBlock(location, currentConfig.playerPortalScanRadius()).ifPresent(block -> {
            List<Block> portalBlocks = findConnectedPortalBlocks(block);
            plugin.pathDebug().trace(plugin, "nether", "portal-scan.portal-found", "blocks=" + portalBlocks.size() + " at " + format(block));
            if (!portalBlocks.isEmpty()) {
                queueSource(PortalSource.fromBlocks(portalBlocks), currentConfig);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        NetherCorruptionConfig currentConfig = config;
        if (!currentConfig.enabled() || event.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        for (BlockState state : event.getBlocks()) {
            Block block = state.getBlock();
            if (block.getType() != Material.NETHER_PORTAL) {
                continue;
            }

            List<Block> portalBlocks = findConnectedPortalBlocks(block);
            if (!portalBlocks.isEmpty()) {
                queueSource(PortalSource.fromBlocks(portalBlocks), currentConfig);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        NetherCorruptionConfig currentConfig = config;
        World world = event.getFrom().getWorld();
        if (!currentConfig.enabled() || world == null || world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        findNearbyPortalBlock(event.getFrom(), 4).ifPresent(block -> {
            List<Block> portalBlocks = findConnectedPortalBlocks(block);
            if (!portalBlocks.isEmpty()) {
                queueSource(PortalSource.fromBlocks(portalBlocks), currentConfig);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.OBSIDIAN && block.getType() != Material.NETHER_PORTAL) {
            return;
        }

        for (PortalSource source : sources.values()) {
            if (source.isNear(block, 5)) {
                scheduleSpread(source, 1L);
            }
        }
    }

    private void queueSource(PortalSource source, NetherCorruptionConfig currentConfig) {
        PortalSource previous = sources.putIfAbsent(source.key(), source);
        if (previous == null) {
            saveSources();
            plugin.getLogger().info("Tracking Nether corruption source at " + source.shortDescription() + ".");
            plugin.pathDebug().trace(plugin, "nether", "source.queue.new", source.shortDescription());
            scheduleSpread(source, Math.min(20L, currentConfig.spreadStepTicks()));
        } else {
            plugin.pathDebug().trace(plugin, "nether", "source.queue.existing", source.shortDescription());
        }
    }

    private void scheduleSpread(PortalSource source, long delayTicks) {
        World world = source.world();
        if (world == null) {
            plugin.pathDebug().trace(plugin, "nether", "spread.schedule.remove-missing-world", source.shortDescription());
            sources.remove(source.key());
            saveSources();
            return;
        }

        Bukkit.getRegionScheduler().runDelayed(
                plugin,
                source.center(world),
                task -> spreadFrom(source),
                Math.max(1L, delayTicks)
        );
    }

    private void spreadFrom(PortalSource source) {
        NetherCorruptionConfig currentConfig = config;
        if (!currentConfig.enabled() || !sources.containsKey(source.key())) {
            plugin.pathDebug().trace(plugin, "nether", "spread.skip.disabled-or-untracked", source.shortDescription());
            return;
        }

        World world = source.world();
        if (world == null) {
            plugin.pathDebug().trace(plugin, "nether", "spread.remove.missing-world", source.shortDescription());
            sources.remove(source.key());
            saveSources();
            return;
        }

        SourceState state = sourceState(source, world, currentConfig);
        if (state == SourceState.GONE) {
            plugin.pathDebug().trace(plugin, "nether", "spread.remove.portal-gone", source.shortDescription());
            sources.remove(source.key());
            saveSources();
            return;
        }
        if (state == SourceState.WAIT) {
            plugin.pathDebug().trace(plugin, "nether", "spread.wait", source.shortDescription());
            scheduleSpread(source, currentConfig.spreadStepTicks());
            return;
        }

        plugin.pathDebug().trace(plugin, "nether", "spread.active", source.shortDescription()
                + " attempts=" + currentConfig.attemptsPerStep());
        int changed = 0;
        for (int attempt = 0; attempt < currentConfig.attemptsPerStep() && changed < currentConfig.blocksPerStep(); attempt++) {
            Optional<Block> target = randomTarget(source, world, currentConfig);
            if (target.isEmpty()) {
                continue;
            }

            Block block = target.get();
            Material original = block.getType();
            NetherMimicResult mimic = terrainMimic.mimic(block, source, random);
            if (mimic != null && mimic.material() != original) {
                block.setType(mimic.material(), false);
                plugin.pathDebug().trace(plugin, "nether", "spread.replace", format(block)
                        + " " + original + "->" + mimic.material()
                        + " style=" + mimic.style().displayName());
                mapDebug.recordReplacement(plugin, currentConfig, source, block, original, mimic);
                changed++;
            }
        }
        if (changed > 0) {
            changedBlocks.addAndGet(changed);
        } else {
            plugin.pathDebug().trace(plugin, "nether", "spread.no-change", source.shortDescription());
        }

        scheduleSpread(source, currentConfig.spreadStepTicks());
    }

    private SourceState sourceState(PortalSource source, World world, NetherCorruptionConfig currentConfig) {
        if (!areBoundsLoaded(source, world)) {
            return SourceState.WAIT;
        }

        for (int chunkX = source.minChunkX(); chunkX <= source.maxChunkX(); chunkX++) {
            for (int chunkZ = source.minChunkZ(); chunkZ <= source.maxChunkZ(); chunkZ++) {
                if (!Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                    return SourceState.WAIT;
                }
            }
        }

        if (!isNearPlayer(source.center(world), currentConfig.requiredPlayerDistanceChunks())) {
            return SourceState.WAIT;
        }

        for (int x = source.minX(); x <= source.maxX(); x++) {
            for (int y = source.minY(); y <= source.maxY(); y++) {
                for (int z = source.minZ(); z <= source.maxZ(); z++) {
                    if (world.getBlockAt(x, y, z).getType() == Material.NETHER_PORTAL) {
                        return SourceState.ACTIVE;
                    }
                }
            }
        }

        return SourceState.GONE;
    }

    private Optional<Block> randomTarget(PortalSource source, World world, NetherCorruptionConfig currentConfig) {
        int dx = random.nextInt(currentConfig.maxRadius() * 2 + 1) - currentConfig.maxRadius();
        int dz = random.nextInt(currentConfig.maxRadius() * 2 + 1) - currentConfig.maxRadius();
        if ((dx * dx) + (dz * dz) > currentConfig.maxRadius() * currentConfig.maxRadius()) {
            return Optional.empty();
        }

        int x = source.centerX() + dx;
        int z = source.centerZ() + dz;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            return Optional.empty();
        }

        int startY = Math.min(world.getMaxHeight() - 1, source.centerY() + currentConfig.verticalRadius());
        int endY = Math.max(world.getMinHeight(), source.centerY() - currentConfig.verticalRadius());
        for (int y = startY; y >= endY; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (terrainMimic.canMimic(block.getType())) {
                return Optional.of(block);
            }
        }

        return Optional.empty();
    }

    private List<Block> findConnectedPortalBlocks(Block start) {
        List<Block> found = new ArrayList<>();
        Queue<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(start);

        while (!queue.isEmpty() && found.size() < 256) {
            Block block = queue.poll();
            String key = blockKey(block);
            if (!visited.add(key) || block.getType() != Material.NETHER_PORTAL) {
                continue;
            }

            found.add(block);
            queue.add(block.getRelative(1, 0, 0));
            queue.add(block.getRelative(-1, 0, 0));
            queue.add(block.getRelative(0, 1, 0));
            queue.add(block.getRelative(0, -1, 0));
            queue.add(block.getRelative(0, 0, 1));
            queue.add(block.getRelative(0, 0, -1));
        }

        return found;
    }

    private Optional<Block> findNearbyPortalBlock(Location location, int radius) {
        World world = location.getWorld();
        if (world == null) {
            return Optional.empty();
        }

        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();
        int horizontalRadius = Math.max(3, radius);
        int verticalRadius = Math.min(8, Math.max(2, radius / 4));
        for (int y = -verticalRadius; y <= verticalRadius; y++) {
            for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    if ((x * x) + (z * z) > horizontalRadius * horizontalRadius) {
                        continue;
                    }

                    int blockY = baseY + y;
                    if (blockY < world.getMinHeight() || blockY >= world.getMaxHeight()) {
                        continue;
                    }

                    int blockX = baseX + x;
                    int blockZ = baseZ + z;
                    int chunkX = blockX >> 4;
                    int chunkZ = blockZ >> 4;
                    if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                        continue;
                    }

                    Block block = world.getBlockAt(blockX, blockY, blockZ);
                    if (block.getType() == Material.NETHER_PORTAL) {
                        return Optional.of(block);
                    }
                }
            }
        }

        return Optional.empty();
    }

    private boolean areBoundsLoaded(PortalSource source, World world) {
        for (int chunkX = source.minChunkX(); chunkX <= source.maxChunkX(); chunkX++) {
            for (int chunkZ = source.minChunkZ(); chunkZ <= source.maxChunkZ(); chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
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

    private void loadSources() {
        File file = sourceFile();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sourceSection = yaml.getConfigurationSection("sources");
        if (sourceSection == null) {
            return;
        }

        for (String key : sourceSection.getKeys(false)) {
            ConfigurationSection section = sourceSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            try {
                PortalSource source = PortalSource.from(section);
                sources.put(source.key(), source);
                scheduleSpread(source, config.spreadStepTicks());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipping invalid Nether corruption source '" + key + "': " + ex.getMessage());
            }
        }
    }

    private void saveSources() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection sourceSection = yaml.createSection("sources");
        int index = 0;
        for (PortalSource source : sources.values()) {
            source.writeTo(sourceSection.createSection(Integer.toString(index++)));
        }

        File file = sourceFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for Nether corruption storage.");
            return;
        }

        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save Nether corruption sources.", ex);
        }
    }

    private File sourceFile() {
        return new File(plugin.getDataFolder(), "nether-sources.yml");
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private String format(Block block) {
        return block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private enum SourceState {
        ACTIVE,
        WAIT,
        GONE
    }
}
