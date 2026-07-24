package org.slowtrees.portal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.slowtrees.core.PluginFeature;
import org.slowtrees.core.ResourceReporter.ReportSample;
import org.slowtrees.core.SlowTreesPlugin;

public final class ShapedPortalFeature implements PluginFeature, Listener {
    private static final Set<Material> INTERIOR_MATERIALS = Set.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.NETHER_PORTAL
    );

    private final SlowTreesPlugin plugin;
    private final ConcurrentMap<String, TrackedPortal> portals =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> portalByBlock =
            new ConcurrentHashMap<>();
    private final AtomicLong createdPortals = new AtomicLong();
    private final AtomicLong removedPortals = new AtomicLong();
    private volatile ShapedPortalConfig config;

    public ShapedPortalFeature(SlowTreesPlugin plugin) {
        this.plugin = plugin;
        this.config = ShapedPortalConfig.load(plugin);
    }

    @Override
    public void onEnable() {
        loadPortals();
        plugin.pathDebug().trace(plugin, "shaped-portals",
                "config.loaded", config.summary());
        plugin.pathDebug().trace(plugin, "shaped-portals",
                "persistence.loaded",
                "tracked=" + portals.size()
                        + " ## Custom portal shapes survive restarts");
    }

    @Override
    public void onDisable() {
        savePortals();
    }

    @Override
    public void reload() {
        config = ShapedPortalConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "shaped-portals",
                "config.reload", config.summary());
    }

    @Override
    public String status() {
        return "Shaped portals: " + (config.enabled() ? "enabled" : "disabled")
                + ", tracked=" + portals.size()
                + ", created=" + createdPortals.get()
                + ", removed=" + removedPortals.get() + ".";
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        ShapedPortalConfig currentConfig = config;
        if (!currentConfig.enabled()
                || !supportsPortals(event.getBlock().getWorld())) {
            return;
        }
        if (event.getCause() != BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL
                && event.getCause() != BlockIgniteEvent.IgniteCause.FIREBALL) {
            return;
        }

        Block start = event.getBlock();
        World world = start.getWorld();
        int x = start.getX();
        int y = start.getY();
        int z = start.getZ();
        plugin.pathDebug().traceSampled(plugin, "shaped-portals",
                "scheduler.region-delay",
                "ignite=" + format(start)
                        + " ## Wait one tick so vanilla portals win first");
        Bukkit.getRegionScheduler().runDelayed(
                plugin,
                world,
                x >> 4,
                z >> 4,
                task -> attemptCreate(world, x, y, z),
                1L
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.NETHER_PORTAL) {
            return;
        }
        if (portalByBlock.containsKey(blockKey(block.getWorld(),
                block.getX(), block.getY(), block.getZ()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String portalId = portalByBlock.get(blockKey(block.getWorld(),
                block.getX(), block.getY(), block.getZ()));
        if (portalId == null) {
            return;
        }

        TrackedPortal portal = portals.get(portalId);
        if (portal == null) {
            return;
        }
        plugin.pathDebug().trace(plugin, "shaped-portals",
                "state.frame-broken",
                "portal=" + portal.id() + " block=" + format(block)
                        + " ## Breaking any tracked frame/interior block closes the shape");
        Bukkit.getRegionScheduler().runDelayed(
                plugin,
                block.getWorld(),
                block.getX() >> 4,
                block.getZ() >> 4,
                task -> removePortal(portal, "frame-or-interior-broken"),
                1L
        );
    }

    private void attemptCreate(World world, int x, int y, int z) {
        try (ReportSample sample = plugin.resourceReporter().begin(
                "shaped-portals", "create.detect-frame")) {
            ShapedPortalConfig currentConfig = config;
            if (!currentConfig.enabled()
                    || !world.isChunkLoaded(x >> 4, z >> 4)
                    || !Bukkit.isOwnedByCurrentRegion(
                            world, x >> 4, z >> 4, 0)) {
                sample.detail("disabled-unloaded-or-unowned");
                return;
            }

            Block startBlock = world.getBlockAt(x, y, z);
            if (startBlock.getType() == Material.NETHER_PORTAL) {
                sample.detail("vanilla-portal-created");
                return;
            }
            if (!INTERIOR_MATERIALS.contains(startBlock.getType())) {
                sample.detail("ignition-cell-blocked "
                        + startBlock.getType());
                return;
            }

            PortalCell start = new PortalCell(x, y, z);
            Optional<PortalShapePlan> plan = List.of(
                            detect(world, start, PortalPlane.X, currentConfig),
                            detect(world, start, PortalPlane.Z, currentConfig))
                    .stream()
                    .flatMap(Optional::stream)
                    .max(Comparator.comparingInt(
                            shape -> shape.interior().size()));
            if (plan.isEmpty()) {
                trace(currentConfig, "gate.no-enclosed-frame",
                        "start=" + format(startBlock));
                sample.detail("no-enclosed-frame");
                return;
            }

            PortalShapePlan shape = plan.get();
            for (PortalCell cell : shape.interior()) {
                if (!plugin.canEvolveAt(
                        world.getBlockAt(cell.x(), cell.y(), cell.z())
                                .getLocation(),
                        "shaped-nether-portal")) {
                    trace(currentConfig, "gate.worldguard-deny",
                            "cell=" + cell.encoded()
                                    + " ## evolution=deny blocks custom portal creation");
                    sample.detail("worldguard-deny");
                    return;
                }
            }

            Orientable portalData = (Orientable) Bukkit.createBlockData(
                    Material.NETHER_PORTAL);
            portalData.setAxis(shape.plane().axis());
            for (PortalCell cell : shape.interior()) {
                Block block = world.getBlockAt(
                        cell.x(), cell.y(), cell.z());
                BlockData copy = portalData.clone();
                block.setBlockData(copy, false);
            }

            TrackedPortal portal = new TrackedPortal(
                    UUID.randomUUID().toString(),
                    world.getUID(),
                    world.getName(),
                    shape.plane(),
                    shape.interior(),
                    shape.frame());
            track(portal);
            savePortals();
            createdPortals.incrementAndGet();
            trace(currentConfig, "action.create",
                    "portal=" + portal.id()
                            + " axis=" + portal.plane()
                            + " interior=" + portal.interior().size()
                            + " frame=" + portal.frame().size()
                            + " start=" + format(startBlock));
            plugin.netherFeature().registerPortalSource(startBlock);
            sample.workUnits(shape.interior().size()
                            + shape.frame().size())
                    .changedUnits(shape.interior().size())
                    .detail("created axis=" + shape.plane()
                            + " interior=" + shape.interior().size());
        }
    }

    private Optional<PortalShapePlan> detect(
            World world,
            PortalCell start,
            PortalPlane plane,
            ShapedPortalConfig currentConfig
    ) {
        return PortalFrameDetector.detect(
                start,
                plane,
                currentConfig.minimumInteriorBlocks(),
                currentConfig.maximumInteriorBlocks(),
                currentConfig.maximumWidth(),
                currentConfig.maximumHeight(),
                cell -> cellType(world, cell));
    }

    private PortalFrameDetector.CellType cellType(
            World world,
            PortalCell cell
    ) {
        if (cell.y() < world.getMinHeight()
                || cell.y() >= world.getMaxHeight()) {
            return PortalFrameDetector.CellType.UNAVAILABLE;
        }
        int chunkX = cell.x() >> 4;
        int chunkZ = cell.z() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)
                || !Bukkit.isOwnedByCurrentRegion(
                        world, chunkX, chunkZ, 0)) {
            return PortalFrameDetector.CellType.UNAVAILABLE;
        }
        Material material = world.getBlockAt(
                cell.x(), cell.y(), cell.z()).getType();
        if (material == Material.OBSIDIAN) {
            return PortalFrameDetector.CellType.FRAME;
        }
        return INTERIOR_MATERIALS.contains(material)
                ? PortalFrameDetector.CellType.INTERIOR
                : PortalFrameDetector.CellType.BLOCKED;
    }

    private void removePortal(TrackedPortal portal, String reason) {
        if (!portals.remove(portal.id(), portal)) {
            return;
        }
        unindex(portal);
        savePortals();
        removedPortals.incrementAndGet();

        World world = portal.world();
        if (world != null) {
            Map<ChunkKey, List<PortalCell>> byChunk = new HashMap<>();
            for (PortalCell cell : portal.interior()) {
                byChunk.computeIfAbsent(
                                new ChunkKey(cell.x() >> 4, cell.z() >> 4),
                                ignored -> new ArrayList<>())
                        .add(cell);
            }
            for (Map.Entry<ChunkKey, List<PortalCell>> entry
                    : byChunk.entrySet()) {
                ChunkKey chunk = entry.getKey();
                List<PortalCell> cells = List.copyOf(entry.getValue());
                if (!world.isChunkLoaded(chunk.x(), chunk.z())) {
                    continue;
                }
                Bukkit.getRegionScheduler().runDelayed(
                        plugin,
                        world,
                        chunk.x(),
                        chunk.z(),
                        task -> clearPortalCells(world, cells),
                        1L
                );
            }
        }
        trace(config, "action.remove",
                "portal=" + portal.id() + " reason=" + reason);
    }

    private void clearPortalCells(World world, List<PortalCell> cells) {
        for (PortalCell cell : cells) {
            Block block = world.getBlockAt(
                    cell.x(), cell.y(), cell.z());
            if (block.getType() == Material.NETHER_PORTAL) {
                block.setType(Material.AIR, false);
            }
        }
    }

    private void track(TrackedPortal portal) {
        portals.put(portal.id(), portal);
        for (PortalCell cell : portal.interior()) {
            portalByBlock.put(blockKey(portal.worldId(), cell), portal.id());
        }
        for (PortalCell cell : portal.frame()) {
            portalByBlock.put(blockKey(portal.worldId(), cell), portal.id());
        }
    }

    private void unindex(TrackedPortal portal) {
        for (PortalCell cell : portal.interior()) {
            portalByBlock.remove(
                    blockKey(portal.worldId(), cell), portal.id());
        }
        for (PortalCell cell : portal.frame()) {
            portalByBlock.remove(
                    blockKey(portal.worldId(), cell), portal.id());
        }
    }

    private void loadPortals() {
        File file = portalFile();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection(
                "portals");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection portalSection =
                    section.getConfigurationSection(id);
            if (portalSection == null) {
                continue;
            }
            try {
                UUID worldId = UUID.fromString(
                        portalSection.getString("world-id", ""));
                String worldName = portalSection.getString(
                        "world-name", "");
                PortalPlane plane = PortalPlane.valueOf(
                        portalSection.getString("plane", "X"));
                Set<PortalCell> interior = decodeCells(
                        portalSection.getStringList("interior"));
                Set<PortalCell> frame = decodeCells(
                        portalSection.getStringList("frame"));
                if (interior.isEmpty() || frame.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Portal shape is empty.");
                }
                track(new TrackedPortal(id, worldId, worldName,
                        plane, interior, frame));
            } catch (IllegalArgumentException failure) {
                plugin.getLogger().warning(
                        "Skipping invalid shaped portal '" + id + "'.");
            }
        }
    }

    private Set<PortalCell> decodeCells(List<String> encoded) {
        Set<PortalCell> cells = new HashSet<>();
        for (String value : encoded) {
            cells.add(PortalCell.decode(value));
        }
        return Set.copyOf(cells);
    }

    private synchronized void savePortals() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("notes",
                "## Plugin-created enclosed obsidian portal shapes. "
                        + "Coordinates are retained so physics and frame "
                        + "breaks remain stable after restart.");
        ConfigurationSection section = yaml.createSection("portals");
        for (TrackedPortal portal : portals.values()) {
            ConfigurationSection portalSection =
                    section.createSection(portal.id());
            portalSection.set("world-id", portal.worldId().toString());
            portalSection.set("world-name", portal.worldName());
            portalSection.set("plane", portal.plane().name());
            portalSection.set("interior", portal.interior().stream()
                    .sorted(cellComparator())
                    .map(PortalCell::encoded)
                    .toList());
            portalSection.set("frame", portal.frame().stream()
                    .sorted(cellComparator())
                    .map(PortalCell::encoded)
                    .toList());
        }

        File file = portalFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning(
                    "Could not create the shaped portal data folder.");
            return;
        }
        try {
            yaml.save(file);
            plugin.pathDebug().trace(plugin, "shaped-portals",
                    "persistence.save",
                    "shaped-portals.yml tracked=" + portals.size());
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not save shaped-portals.yml.", failure);
        }
    }

    private Comparator<PortalCell> cellComparator() {
        return Comparator.comparingInt(PortalCell::y)
                .thenComparingInt(PortalCell::x)
                .thenComparingInt(PortalCell::z);
    }

    private File portalFile() {
        return new File(plugin.getDataFolder(), "shaped-portals.yml");
    }

    private boolean supportsPortals(World world) {
        return world.getEnvironment() == World.Environment.NORMAL
                || world.getEnvironment() == World.Environment.NETHER;
    }

    private void trace(
            ShapedPortalConfig currentConfig,
            String path,
            String detail
    ) {
        if (currentConfig.debugEnabled()) {
            plugin.pathDebug().traceSampled(
                    plugin, "shaped-portals", path, detail);
        }
    }

    private String blockKey(World world, int x, int y, int z) {
        return world.getUID() + ":" + x + ":" + y + ":" + z;
    }

    private String blockKey(UUID worldId, PortalCell cell) {
        return worldId + ":" + cell.x() + ":" + cell.y() + ":" + cell.z();
    }

    private String format(Block block) {
        return block.getWorld().getName() + " "
                + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private record ChunkKey(int x, int z) {
    }
}
