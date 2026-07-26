package org.evolution.features.nether;

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
import org.bukkit.block.data.Bisected;
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
import org.evolution.coreparts.PluginFeature;
import org.evolution.coreparts.ResourceReporter.ReportSample;
import org.evolution.coreparts.EvolutionPlugin;
import org.evolution.coreparts.hierarchy.FeatureActionHierarchy;
import org.evolution.coreparts.hierarchy.FeatureHierarchyTrace;
import org.evolution.features.nether.action.NetherActionPhase;
import org.evolution.features.nether.action.NetherActionSubrule;

public final class NetherCorruptionFeature implements PluginFeature, Listener {
    private static final FeatureActionHierarchy<NetherActionPhase> ACTION_HIERARCHY =
            FeatureActionHierarchy.of("nether", NetherActionPhase.class);
    private final EvolutionPlugin plugin;
    private final ConcurrentMap<String, PortalSource> sources = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, NetherSpreadFrontier> frontiers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> nextPortalScanMillis = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> negativePortalScanCache = new ConcurrentHashMap<>();
    private final NetherTerrainMimic terrainMimic = new NetherTerrainMimic();
    private final NetherMapDebug mapDebug = new NetherMapDebug();
    private final Random random = new Random();
    private final AtomicLong changedBlocks = new AtomicLong();
    private volatile NetherCorruptionConfig config;

    public NetherCorruptionFeature(EvolutionPlugin plugin) {
        this.plugin = plugin;
        this.config = NetherCorruptionConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "nether", "config.load", config.summary());
    }

    @Override
    public void onEnable() {
        plugin.pathDebug().trace(plugin, "nether", "persistence.save-map-debug.now", "MapDebug.yml startup refresh");
        mapDebug.saveNow(plugin);
        plugin.pathDebug().trace(plugin, "nether", "enable.load-sources", "loading stored portal sources");
        loadSources();
    }

    @Override
    public void onDisable() {
        nextPortalScanMillis.clear();
        negativePortalScanCache.clear();
        plugin.pathDebug().trace(plugin, "nether", "persistence.save-map-debug.now", "MapDebug.yml");
        mapDebug.saveNow(plugin);
        saveSources();
    }

    @Override
    public void reload() {
        this.config = NetherCorruptionConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "nether", "config.reload", config.summary());
    }

    @Override
    public String status() {
        return "Nether corruption is tracking " + sources.size()
                + " portal source(s), changed " + changedBlocks.get() + " block(s) since reload.";
    }

    public boolean enabled() {
        return config.enabled();
    }

    public int trackedSourceCount() {
        return sources.size();
    }

    public long changedBlockCount() {
        return changedBlocks.get();
    }

    public boolean isMimicable(Material material) {
        return terrainMimic.canMimic(material);
    }

    public boolean isCorruptionMaterial(Material material) {
        return terrainMimic.isCorruptionMaterial(material);
    }

    public boolean registerPortalSource(Block portalBlock) {
        NetherCorruptionConfig currentConfig = config;
        World world = portalBlock.getWorld();
        if (!currentConfig.enabled()
                || world.getEnvironment() != World.Environment.NORMAL
                || portalBlock.getType() != Material.NETHER_PORTAL) {
            return false;
        }

        List<Block> portalBlocks = findConnectedPortalBlocks(portalBlock);
        return !portalBlocks.isEmpty()
                && queueSource(
                        PortalSource.fromBlocks(portalBlocks), currentConfig);
    }
    public boolean registerPortalSourceNear(Location location, int radius) {
        NetherCorruptionConfig currentConfig = config;
        World world = location.getWorld();
        if (!currentConfig.enabled() || world == null || world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }

        Optional<Block> portal = findNearbyPortalBlock(location, radius);
        if (portal.isEmpty()) {
            return false;
        }

        List<Block> portalBlocks = findConnectedPortalBlocks(portal.get());
        if (portalBlocks.isEmpty()) {
            return false;
        }

        return queueSource(PortalSource.fromBlocks(portalBlocks), currentConfig);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        try (ReportSample sample = plugin.resourceReporter().begin("nether", "event.player-move-portal-scan")) {
            NetherCorruptionConfig currentConfig = config;
            Location location = event.getTo();
            World world = location.getWorld();
            if (!currentConfig.enabled() || world == null || world.getEnvironment() != World.Environment.NORMAL) {
                plugin.pathDebug().trace(plugin, "nether", "portal-scan.skip", "enabled=" + currentConfig.enabled()
                        + " world=" + (world == null ? "none" : world.getEnvironment()));
                sample.detail("disabled-or-environment");
                return;
            }

            String key = event.getPlayer().getUniqueId().toString();
            long now = System.currentTimeMillis();
            long nextScan = nextPortalScanMillis.getOrDefault(key, 0L);
            if (now < nextScan) {
                plugin.pathDebug().traceSampled(plugin, "nether", "portal-scan.skip.cooldown", "remaining-ms=" + (nextScan - now));
                sample.detail("cooldown");
                return;
            }

            nextPortalScanMillis.put(key, now + currentConfig.portalScanCooldownMillis());
            String negativeKey = portalNegativeCacheKey(location, currentConfig.playerPortalScanRadius());
            long negativeExpires = negativePortalScanCache.getOrDefault(negativeKey, 0L);
            if (now < negativeExpires) {
                plugin.pathDebug().traceSampled(plugin, "nether", "portal-scan.skip.negative-cache",
                        negativeKey + " remaining-ms=" + (negativeExpires - now));
                sample.detail("negative-cache " + negativeKey);
                return;
            }
            plugin.pathDebug().trace(plugin, "nether", "portal-scan.start", "radius=" + currentConfig.playerPortalScanRadius());
            Optional<Block> portal = findNearbyPortalBlock(location, currentConfig.playerPortalScanRadius());
            if (portal.isPresent()) {
                List<Block> portalBlocks = findConnectedPortalBlocks(portal.get());
                plugin.pathDebug().trace(plugin, "nether", "portal-scan.portal-found", "blocks=" + portalBlocks.size() + " at " + format(portal.get()));
                boolean queued = !portalBlocks.isEmpty()
                        && queueSource(PortalSource.fromBlocks(portalBlocks), currentConfig);
                sample.changedUnits(queued ? 1L : 0L).workUnits(portalBlocks.size())
                        .detail("portal-found blocks=" + portalBlocks.size() + " queued=" + queued);
            } else {
                negativePortalScanCache.put(negativeKey, now + currentConfig.portalNegativeCacheMillis());
                sample.detail("portal-none radius=" + currentConfig.playerPortalScanRadius());
            }
        }
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
        if (block.getType() != Material.OBSIDIAN
                && block.getType() != Material.NETHER_PORTAL) {
            return;
        }

        boolean retired = false;
        for (PortalSource source : List.copyOf(sources.values())) {
            if (!source.isSameWorld(block.getWorld())) {
                continue;
            }
            boolean interiorBroken = block.getType() == Material.NETHER_PORTAL
                    && source.containsPortalCell(
                            block.getX(), block.getY(), block.getZ());
            boolean frameBroken = block.getType() == Material.OBSIDIAN
                    && source.touchesPortalFrame(
                            block.getX(), block.getY(), block.getZ());
            if (interiorBroken || frameBroken) {
                retired |= retireSource(source,
                        interiorBroken
                                ? "portal-interior-broken"
                                : "portal-frame-broken",
                        false);
            }
        }
        if (retired) {
            saveSources();
        }
    }

    /**
     * Retires the exact source owning a shaped portal before its interior is
     * cleared. This keeps custom and vanilla portal teardown under one rule.
     */
    public boolean retirePortalSource(Block portalBlock, String reason) {
        boolean retired = false;
        for (PortalSource source : List.copyOf(sources.values())) {
            if (source.isSameWorld(portalBlock.getWorld())
                    && source.containsPortalCell(
                            portalBlock.getX(), portalBlock.getY(),
                            portalBlock.getZ())) {
                retired |= retireSource(source, reason, false);
            }
        }
        if (retired) {
            saveSources();
        }
        return retired;
    }

    private boolean queueSource(PortalSource source,
            NetherCorruptionConfig currentConfig) {
        World world = source.world();
        if (world == null || !isPortalSourceAllowed(source, world)) {
            plugin.pathDebug().traceSampled(plugin, "nether",
                    "source.queue.worldguard-deny",
                    source.shortDescription()
                            + " ## A protected or incomplete Nether portal cannot seed corruption outside its region");
            return false;
        }

        PortalSource active = sources.compute(source.key(),
                (key, previous) -> previous != null
                        && previous.matchesFingerprint(source)
                        ? previous
                        : source);
        if (active == source) {
            frontiers.put(source.key(), new NetherSpreadFrontier(source));
            saveSources();
            plugin.getLogger().info("Tracking Nether corruption source at "
                    + source.shortDescription() + ".");
            plugin.pathDebug().trace(plugin, "nether", "source.queue.new",
                    source.shortDescription()
                            + " cells=" + source.portalCellCount()
                            + " exact=" + source.isExactSnapshot());
            scheduleSpread(source,
                    Math.min(20L, currentConfig.spreadStepTicks()));
        } else {
            plugin.pathDebug().trace(plugin, "nether",
                    "source.queue.existing", source.shortDescription()
                            + " cells=" + active.portalCellCount());
        }
        return true;
    }

    private void scheduleSpread(PortalSource source, long delayTicks) {
        World world = source.world();
        if (world == null) {
            retireSource(source, "missing-world-before-schedule", true);
            return;
        }

        plugin.pathDebug().trace(plugin, "nether", "scheduler.region-delay", source.shortDescription() + " delay=" + Math.max(1L, delayTicks));
        Bukkit.getRegionScheduler().runDelayed(
                plugin,
                source.center(world),
                task -> spreadFrom(source),
                Math.max(1L, delayTicks)
        );
    }

    private void spreadFrom(PortalSource source) {
        try (ReportSample sample = plugin.resourceReporter().begin(
                "nether", "tick.spread-from-source")) {
            traceHierarchy(NetherActionPhase.SOURCE_GATE,
                    NetherActionSubrule.SOURCE_IS_TRACKED,
                    "spread-from-source");
            NetherCorruptionConfig currentConfig = config;
            if (!currentConfig.enabled()
                    || sources.get(source.key()) != source) {
                plugin.pathDebug().trace(plugin, "nether",
                        "spread.skip.disabled-or-stale-source",
                        source.shortDescription());
                sample.detail("disabled-or-stale "
                        + source.shortDescription());
                return;
            }

            traceHierarchy(NetherActionPhase.SOURCE_GATE,
                    NetherActionSubrule.SOURCE_WORLD_AVAILABLE,
                    "source-world");
            World world = source.world();
            if (world == null) {
                retireSource(source, "missing-world", true);
                sample.detail("missing-world");
                return;
            }

            SourceState state = sourceState(source, world, currentConfig);
            if (state == SourceState.GONE) {
                retireSource(source, "portal-fingerprint-mismatch", true);
                sample.detail("portal-gone");
                return;
            }
            if (state == SourceState.WAIT) {
                plugin.pathDebug().trace(plugin, "nether", "spread.wait",
                        source.shortDescription());
                scheduleSpread(source, currentConfig.spreadStepTicks());
                sample.detail("wait " + source.shortDescription());
                return;
            }
            if (state == SourceState.PROTECTED) {
                plugin.pathDebug().traceSampled(plugin, "nether",
                        "spread.wait.worldguard-source",
                        source.shortDescription()
                                + " ## evolution=deny pauses this portal source and all outward spread");
                scheduleSpread(source, currentConfig.spreadStepTicks());
                sample.detail("worldguard-protected-source "
                        + source.shortDescription());
                return;
            }

            plugin.pathDebug().trace(plugin, "nether", "spread.active",
                    source.shortDescription()
                            + " attempts=" + currentConfig.attemptsPerStep()
                            + " frontier=" + frontierFor(source).size()
                            + " portal-cells=" + source.portalCellCount());
            int changed = 0;
            int attempts = 0;
            for (int attempt = 0;
                    attempt < currentConfig.attemptsPerStep()
                            && changed < currentConfig.blocksPerStep();
                    attempt++) {
                if (sources.get(source.key()) != source) {
                    plugin.pathDebug().trace(plugin, "nether",
                            "spread.stop.source-retired-during-cycle",
                            source.shortDescription());
                    break;
                }
                attempts++;
                Optional<Block> target = nextTarget(
                        source, world, currentConfig);
                if (target.isEmpty()) {
                    continue;
                }

                Block block = target.get();
                Material original = block.getType();
                traceHierarchy(NetherActionPhase.MIMIC_TERRAIN,
                        terrainMimic.isDirectTranslation(original)
                                ? NetherActionSubrule.DIRECT_MATERIAL_TRANSLATION
                                : NetherActionSubrule.NEIGHBOR_STYLE_TRANSLATION,
                        "terrain-mimic " + original);
                NetherMimicResult mimic = terrainMimic.mimic(
                        block, source, random,
                        nearbyCorruptionMaterials(world, block));
                if (mimic == null || mimic.material() == original) {
                    continue;
                }

                traceHierarchy(NetherActionPhase.MIMIC_TERRAIN,
                        NetherActionSubrule.TARGET_PROTECTION_ALLOWED,
                        "target-protection");
                if (!plugin.canEvolveAt(
                        block.getLocation(), "nether-corruption")) {
                    continue;
                }

                int committed = commitMimic(block, original, mimic);
                if (committed <= 0) {
                    continue;
                }
                traceHierarchy(NetherActionPhase.COMMIT_FRONTIER,
                        NetherActionSubrule.COMMIT_WORLD_CHANGE,
                        "replacement-committed");
                frontierFor(source).add(
                        block.getX(), block.getY(), block.getZ(),
                        currentConfig.maxFrontierSize());
                traceHierarchy(NetherActionPhase.COMMIT_FRONTIER,
                        NetherActionSubrule.EXTEND_CONNECTED_FRONTIER,
                        "frontier-extended");
                plugin.pathDebug().trace(plugin, "nether", "spread.replace",
                        format(block) + " " + original + "->"
                                + mimic.material() + " style="
                                + mimic.style().displayName()
                                + " changed-blocks=" + committed);
                mapDebug.recordReplacement(plugin, currentConfig,
                        source, block, original, mimic);
                changed += committed;
            }
            if (changed > 0) {
                changedBlocks.addAndGet(changed);
            } else {
                plugin.pathDebug().trace(plugin, "nether",
                        "spread.no-change", source.shortDescription());
            }

            sample.workUnits(attempts).changedUnits(changed)
                    .detail("changed=" + changed + " source="
                            + source.shortDescription());
            if (sources.get(source.key()) == source) {
                scheduleSpread(source, currentConfig.spreadStepTicks());
            }
        }
    }
    private int commitMimic(
            Block block,
            Material original,
            NetherMimicResult mimic) {
        if (terrainMimic.isFlower(original)
                && block.getBlockData() instanceof Bisected bisected) {
            Block lower = bisected.getHalf() == Bisected.Half.BOTTOM
                    ? block
                    : block.getRelative(0, -1, 0);
            Block upper = lower.getRelative(0, 1, 0);
            if (lower.getType() != original
                    || upper.getType() != original
                    || !plugin.canEvolveAt(
                            upper.getLocation(), "nether-corruption")) {
                plugin.pathDebug().failure(plugin, "nether",
                        "tall-flower-atomic-gate",
                        format(block) + " lower=" + lower.getType()
                                + " upper=" + upper.getType());
                return 0;
            }

            // ## Tall flowers are one logical translation. Clear the upper
            // half first so no orphaned vanilla flower survives the change.
            upper.setType(Material.AIR, false);
            lower.setType(mimic.material(), false);
            plugin.pathDebug().trace(plugin, "nether",
                    "spread.replace.tall-flower-companion",
                    format(upper) + " " + original + "->AIR");
            return 2;
        }

        block.setType(mimic.material(), false);
        return 1;
    }

    private boolean retireSource(
            PortalSource source,
            String reason,
            boolean persist) {
        traceHierarchy(NetherActionPhase.RETIRE_SOURCE,
                NetherActionSubrule.RETIRE_BROKEN_SOURCE,
                reason);
        if (!sources.remove(source.key(), source)) {
            return false;
        }
        frontiers.remove(source.key());
        plugin.pathDebug().trace(plugin, "nether", "source.retire",
                source.shortDescription() + " reason=" + reason
                        + " cells=" + source.portalCellCount()
                        + " ## Source and queued frontier retired together");
        plugin.pathDebug().failure(plugin, "nether",
                "source-retired-" + reason, source.shortDescription());
        if (persist) {
            saveSources();
        }
        return true;
    }
    private SourceState sourceState(
            PortalSource source,
            World world,
            NetherCorruptionConfig currentConfig) {
        traceHierarchy(NetherActionPhase.SOURCE_GATE,
                NetherActionSubrule.SOURCE_BOUNDS_LOADED,
                "source-bounds-loaded");
        if (!areBoundsLoaded(source, world)) {
            plugin.pathDebug().failure(plugin, "nether",
                    "unloaded-chunk", source.shortDescription());
            return SourceState.WAIT;
        }

        traceHierarchy(NetherActionPhase.SOURCE_GATE,
                NetherActionSubrule.SOURCE_REGION_OWNED,
                "source-region-owned");
        for (int chunkX = source.minChunkX();
                chunkX <= source.maxChunkX(); chunkX++) {
            for (int chunkZ = source.minChunkZ();
                    chunkZ <= source.maxChunkZ(); chunkZ++) {
                if (!Bukkit.isOwnedByCurrentRegion(
                        world, chunkX, chunkZ, 0)) {
                    plugin.pathDebug().failure(plugin, "nether",
                            "region-ownership", "source chunk "
                                    + chunkX + "," + chunkZ);
                    return SourceState.WAIT;
                }
            }
        }

        traceHierarchy(NetherActionPhase.SOURCE_GATE,
                NetherActionSubrule.PORTAL_FINGERPRINT_INTACT,
                "exact-portal-cell-snapshot");
        for (PortalSource.Cell cell : source.portalCells()) {
            Block block = world.getBlockAt(cell.x(), cell.y(), cell.z());
            if (block.getType() != Material.NETHER_PORTAL) {
                plugin.pathDebug().failure(plugin, "nether",
                        "portal-fingerprint-mismatch",
                        format(block) + " expected=NETHER_PORTAL actual="
                                + block.getType() + " source-cells="
                                + source.portalCellCount()
                                + " exact=" + source.isExactSnapshot());
                return SourceState.GONE;
            }
        }

        traceHierarchy(NetherActionPhase.SOURCE_GATE,
                NetherActionSubrule.SOURCE_PROTECTION_ALLOWED,
                "source-worldguard");
        for (PortalSource.Cell cell : source.portalCells()) {
            Block block = world.getBlockAt(cell.x(), cell.y(), cell.z());
            if (!plugin.canEvolveAt(
                    block.getLocation(), "nether-portal-source")) {
                plugin.pathDebug().failure(plugin, "nether",
                        "worldguard-portal-source",
                        format(block)
                                + " ## evolution=deny includes the Nether portal source");
                return SourceState.PROTECTED;
            }
        }

        traceHierarchy(NetherActionPhase.SOURCE_GATE,
                NetherActionSubrule.NEARBY_PLAYER_ACTIVE,
                "nearby-player");
        if (!isNearPlayer(source.center(world),
                currentConfig.requiredPlayerDistanceChunks())) {
            plugin.pathDebug().failure(plugin, "nether",
                    "player-distance", source.shortDescription());
            return SourceState.WAIT;
        }
        return SourceState.ACTIVE;
    }

    private boolean isPortalSourceAllowed(
            PortalSource source,
            World world) {
        for (PortalSource.Cell cell : source.portalCells()) {
            Block block = world.getBlockAt(cell.x(), cell.y(), cell.z());
            if (block.getType() != Material.NETHER_PORTAL
                    || !plugin.canEvolveAt(
                            block.getLocation(), "nether-portal-source")) {
                return false;
            }
        }
        return true;
    }
    private Optional<Block> nextTarget(PortalSource source, World world, NetherCorruptionConfig currentConfig) {
        if (currentConfig.branchingEnabled() && random.nextInt(100) < currentConfig.branchChancePercent()) {
            Optional<Block> branched = branchTarget(source, world, currentConfig);
            if (branched.isPresent()) {
                return branched;
            }
        }
        return nearPortalTarget(source, world, currentConfig);
    }

    private Optional<Block> branchTarget(PortalSource source, World world, NetherCorruptionConfig currentConfig) {
        traceHierarchy(NetherActionPhase.SELECT_FRONTIER,
                NetherActionSubrule.CONNECTED_FRONTIER_TARGET,
                "branch-target");
        NetherSpreadFrontier.Point point = frontierFor(source).randomPoint(random);
        if (point == null) {
            return Optional.empty();
        }

        int branchRadius = currentConfig.branchRadius();
        int dx = random.nextInt(branchRadius * 2 + 1) - branchRadius;
        int dz = random.nextInt(branchRadius * 2 + 1) - branchRadius;
        if (dx == 0 && dz == 0) {
            dx = random.nextBoolean() ? 1 : -1;
        }

        int x = point.x() + dx;
        int z = point.z() + dz;
        if (distanceSquared(source, x, z) > currentConfig.maxRadius() * currentConfig.maxRadius()) {
            return Optional.empty();
        }

        return mimicableTargetAt(source, world, currentConfig, x, point.y(), z, "branch");
    }

    private Optional<Block> nearPortalTarget(PortalSource source, World world, NetherCorruptionConfig currentConfig) {
        traceHierarchy(NetherActionPhase.SELECT_NEAR_PORTAL,
                NetherActionSubrule.PORTAL_ORIGIN_TARGET,
                "near-portal-target");
        int radius = activeFallbackRadius(source, currentConfig);
        traceHierarchy(NetherActionPhase.SELECT_FALLBACK,
                NetherActionSubrule.BOUNDED_FALLBACK_RADIUS,
                "active-fallback-radius");
        int dx = random.nextInt(radius * 2 + 1) - radius;
        int dz = random.nextInt(radius * 2 + 1) - radius;
        if ((dx * dx) + (dz * dz) > radius * radius) {
            return Optional.empty();
        }

        int x = source.centerX() + dx;
        int z = source.centerZ() + dz;
        return mimicableTargetAt(source, world, currentConfig, x, source.centerY(), z, "portal-random");
    }

    private Optional<Block> mimicableTargetAt(PortalSource source, World world, NetherCorruptionConfig currentConfig, int x, int centerY, int z, String mode) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            plugin.pathDebug().failure(plugin, "nether", "chunk-or-region-gate", mode + " target chunk " + chunkX + "," + chunkZ);
            return Optional.empty();
        }

        int startY = Math.min(world.getMaxHeight() - 1, centerY + currentConfig.verticalRadius());
        int endY = Math.max(world.getMinHeight(), centerY - currentConfig.verticalRadius());
        for (int y = startY; y >= endY; y--) {
            Block scanned = world.getBlockAt(x, y, z);
            if (!terrainMimic.canMimic(scanned.getType())) {
                continue;
            }
            Block target = canonicalMimicTarget(scanned);
            if (target != null && canSelectMimicTarget(world, target)) {
                return Optional.of(target);
            }
        }

        return Optional.empty();
    }

    private Block canonicalMimicTarget(Block block) {
        if (!terrainMimic.isFlower(block.getType())
                || !(block.getBlockData() instanceof Bisected bisected)
                || bisected.getHalf() == Bisected.Half.BOTTOM) {
            return block;
        }
        Block lower = block.getRelative(0, -1, 0);
        return lower.getType() == block.getType() ? lower : null;
    }

    private boolean canSelectMimicTarget(World world, Block block) {
        if (block.getType() != Material.WATER) {
            return true;
        }
        return isWaterExpansionEdge(world, block);
    }

    private boolean isWaterExpansionEdge(World world, Block water) {
        return isWaterExpansionNeighbor(world, water, 1, 0)
                || isWaterExpansionNeighbor(world, water, -1, 0)
                || isWaterExpansionNeighbor(world, water, 0, 1)
                || isWaterExpansionNeighbor(world, water, 0, -1);
    }

    private boolean isWaterExpansionNeighbor(World world, Block water, int dx, int dz) {
        int x = water.getX() + dx;
        int z = water.getZ() + dz;
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            return false;
        }

        Material type = world.getBlockAt(x, water.getY(), z).getType();
        return type == Material.LAVA || type == Material.NETHERRACK
                || type == Material.CRIMSON_NYLIUM
                || type == Material.WARPED_NYLIUM
                || type == Material.SOUL_SOIL
                || type == Material.SOUL_SAND
                || type == Material.BLACKSTONE
                || type == Material.BASALT
                || (type != Material.WATER && terrainMimic.canMimic(type));
    }

    private List<Material> nearbyCorruptionMaterials(World world, Block block) {
        List<Material> materials = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }

                    int x = block.getX() + dx;
                    int y = block.getY() + dy;
                    int z = block.getZ() + dz;
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                        continue;
                    }

                    int chunkX = x >> 4;
                    int chunkZ = z >> 4;
                    if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                        continue;
                    }

                    Material type = world.getBlockAt(x, y, z).getType();
                    if (terrainMimic.isCorruptionMaterial(type)) {
                        materials.add(type);
                    }
                }
            }
        }
        return materials;
    }

    private NetherSpreadFrontier frontierFor(PortalSource source) {
        return frontiers.computeIfAbsent(source.key(), key -> new NetherSpreadFrontier(source));
    }

    private int activeFallbackRadius(PortalSource source, NetherCorruptionConfig currentConfig) {
        if (!currentConfig.branchingEnabled()) {
            return currentConfig.maxRadius();
        }

        int frontierDistance = frontierFor(source).maxHorizontalDistanceFrom(source.centerX(), source.centerZ());
        int creepingRadius = Math.max(2, frontierDistance + currentConfig.branchRadius());
        return Math.min(currentConfig.maxRadius(), creepingRadius);
    }

    private int distanceSquared(PortalSource source, int x, int z) {
        int dx = x - source.centerX();
        int dz = z - source.centerZ();
        return (dx * dx) + (dz * dz);
    }

    private List<Block> findConnectedPortalBlocks(Block start) {
        List<Block> found = new ArrayList<>();
        Queue<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(start);

        while (!queue.isEmpty() && found.size() < 512) {
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
        try (ReportSample sample = plugin.resourceReporter().begin("nether", "search.nearby-portal")) {
            World world = location.getWorld();
            if (world == null) {
                sample.detail("missing-world");
                return Optional.empty();
            }

            int baseX = location.getBlockX();
            int baseY = location.getBlockY();
            int baseZ = location.getBlockZ();
            int horizontalRadius = Math.max(3, radius);
            int verticalRadius = Math.min(8, Math.max(2, radius / 4));
            int scanned = 0;
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

                        scanned++;
                        Block block = world.getBlockAt(blockX, blockY, blockZ);
                        if (block.getType() == Material.NETHER_PORTAL) {
                            sample.workUnits(scanned).changedUnits(1).detail("found " + format(block));
                            return Optional.of(block);
                        }
                    }
                }
            }

            sample.workUnits(scanned).detail("not-found radius=" + radius);
            return Optional.empty();
        }
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
            plugin.pathDebug().trace(plugin, "nether", "persistence.load-missing", "nether-sources.yml");
            return;
        }

        plugin.pathDebug().trace(plugin, "nether", "persistence.load", "nether-sources.yml");
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
                frontierFor(source);
                plugin.pathDebug().trace(plugin, "nether", "scheduler.region-delay", "loaded-source spread=" + config.spreadStepTicks());
                scheduleSpread(source, config.spreadStepTicks());
            } catch (RuntimeException ex) {
                plugin.pathDebug().failure(plugin, "nether", "persistence-invalid-entry", "nether-sources.yml entry skipped");
                plugin.getLogger().warning("Skipping invalid Nether corruption source '" + key + "': " + ex.getMessage());
            }
        }
    }

    private void saveSources() {
        try (ReportSample sample = plugin.resourceReporter().begin("nether", "persistence.save-sources")) {
            traceHierarchy(NetherActionPhase.PERSIST_SOURCE,
                    NetherActionSubrule.SAVE_EXACT_SOURCE_SNAPSHOT,
                    "save-sources");
            plugin.pathDebug().trace(plugin, "nether", "persistence.save", "nether-sources.yml sources=" + sources.size());
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
                sample.detail("folder-create-failed sources=" + sources.size());
                return;
            }

            try {
                yaml.save(file);
                sample.workUnits(sources.size()).changedUnits(1).detail("sources=" + sources.size());
            } catch (IOException ex) {
                sample.detail("failed sources=" + sources.size() + " " + ex.getClass().getSimpleName());
                plugin.getLogger().log(Level.WARNING, "Could not save Nether corruption sources.", ex);
            }
        }
    }

    private File sourceFile() {
        return new File(plugin.getDataFolder(), "nether-sources.yml");
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private String portalNegativeCacheKey(Location location, int radius) {
        World world = location.getWorld();
        String worldKey = world == null ? "unknown" : world.getUID().toString();
        return worldKey + ":" + (location.getBlockX() >> 4) + ":" + (location.getBlockZ() >> 4) + ":" + radius;
    }

    private String format(Block block) {
        return block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private enum SourceState {
        ACTIVE,
        WAIT,
        PROTECTED,
        GONE
    }
    // ## NETHER ACTION HIERARCHY
    private void traceHierarchy(
            NetherActionPhase phase,
            NetherActionSubrule subrule,
            String reason) {
        FeatureHierarchyTrace.record(
                plugin, ACTION_HIERARCHY, phase, subrule, reason);
    }

}
