package org.slowtrees.treeevolution;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.slowtrees.core.PluginFeature;
import org.slowtrees.core.ResourceReporter.ReportSample;
import org.slowtrees.core.SlowTreesPlugin;

public final class TreeEvolutionFeature implements PluginFeature, Listener {
    private static final List<BlockFace> NEIGHBORS = List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
    private static final int WOOD_SUPPORT_RADIUS = 2;
    private static final int TREE_GROUP_MAX_VISITED = 1536;
    private static final int TREE_GROUP_MAX_QUEUED = 2048;
    private static final int TREE_GROUP_MAX_DISTANCE = 28;
    private static final long TREE_GROUP_MAX_NANOS = 2_000_000L;
    private static final Set<Material> NATURAL_GROUND = Set.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT,
            Material.PODZOL,
            Material.MOSS_BLOCK,
            Material.MUD,
            Material.MYCELIUM
    );
    private static final Set<Material> NATURAL_DETAILS = Set.of(
            Material.SHORT_GRASS,
            Material.TALL_GRASS,
            Material.FERN,
            Material.LARGE_FERN,
            Material.LEAF_LITTER,
            Material.SNOW,
            Material.MOSS_CARPET,
            Material.DANDELION,
            Material.POPPY,
            Material.BLUE_ORCHID,
            Material.PINK_PETALS,
            Material.OXEYE_DAISY,
            Material.BROWN_MUSHROOM,
            Material.RED_MUSHROOM
    );

    private final SlowTreesPlugin plugin;
    private final TreeEvolutionPlanner planner = new TreeEvolutionPlanner();
    private final TreeShapeEngine shapeEngine = new TreeShapeEngine();
    private final TreeDnaNormalizer dnaNormalizer = new TreeDnaNormalizer();
    private final TreeEvolutionDiagnostics diagnostics = new TreeEvolutionDiagnostics();
    private final StructurePatternScanner scanner = new StructurePatternScanner();
    private final TreeProfileSampleStore sampleStore = new TreeProfileSampleStore();
    private final ConcurrentMap<String, TreeDna> treeDna = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedTreePlan> planCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedProjectionProgress> projectionProgressCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TreeFocusPool> focusedTreePools = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> focusYieldUntil = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedTreeCandidate> focusedCandidateCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedTreeCandidate> nearestCandidateCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedKnownCandidates> knownCandidateCache = new ConcurrentHashMap<>();
    private final Object dnaSaveLock = new Object();
    private final AtomicLong changedBlocks = new AtomicLong();
    private final AtomicLong knownTrees = new AtomicLong();
    private final AtomicBoolean dnaSaveRunning = new AtomicBoolean();
    private final AtomicLong nextDnaSaveMillis = new AtomicLong();
    private final AtomicLong dnaDirtyVersion = new AtomicLong();
    private final AtomicLong dnaSavedVersion = new AtomicLong();
    private final AtomicLong nextDnaCleanupMillis = new AtomicLong();
    private volatile TreeEvolutionConfig config;
    private volatile Map<TreeSpecies, List<TreeProfileSample>> profileSamples = Map.of();

    public TreeEvolutionFeature(SlowTreesPlugin plugin) {
        this.plugin = plugin;
        this.config = TreeEvolutionConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "tree-evolution", "config.load", config.summary());
    }

    @Override
    public void onEnable() {
        ensureFolders();
        loadProfileSamples();
        loadTreeDna();
        cleanupTreeDna("startup");
        diagnostics.saveNow(plugin, config);
        scheduleAutoScan(config);
        plugin.pathDebug().trace(plugin, "tree-evolution", "scheduler.online-players", "players=" + Bukkit.getOnlinePlayers().size());
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayerEvolution(player, 80L);
        }
    }

    @Override
    public void onDisable() {
        diagnostics.saveNow(plugin, config);
        saveTreeDnaNow("disable");
        focusedTreePools.clear();
        focusedCandidateCache.clear();
        focusYieldUntil.clear();
        projectionProgressCache.clear();
    }

    @Override
    public void reload() {
        this.config = TreeEvolutionConfig.load(plugin);
        normalizeKnownTreeDna("config-reload");
        nearestCandidateCache.clear();
        knownCandidateCache.clear();
        focusedTreePools.clear();
        focusedCandidateCache.clear();
        focusYieldUntil.clear();
        projectionProgressCache.clear();
        plugin.pathDebug().trace(plugin, "tree-evolution", "config.reload", config.summary());
    }

    @Override
    public String status() {
        diagnostics.saveAsync(plugin, config);
        return "Tree evolution knows " + treeDna.size() + " tree DNA record(s) and placed "
                + changedBlocks.get() + " evolution block(s).";
    }

    public boolean enabled() {
        return config.enabled();
    }

    public int knownTreeCount() {
        return treeDna.size();
    }

    public long changedBlockCount() {
        return changedBlocks.get();
    }

    public long diagnosticPlacedCount() {
        return diagnostics.placed();
    }

    public boolean handleCommand(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("tree")) {
            return false;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /" + label + " tree <debug|step|preview|scan>");
            return true;
        }
        if (!(sender instanceof Player player) && (args[1].equalsIgnoreCase("debug") || args[1].equalsIgnoreCase("step") || args[1].equalsIgnoreCase("preview"))) {
            sender.sendMessage("This tree command needs an in-world player.");
            return true;
        }
        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "debug" -> {
                Player player = (Player) sender;
                Optional<TreeCandidate> candidate = findNearestCandidate(player.getLocation(), 8);
                if (candidate.isEmpty()) {
                    sender.sendMessage("No nearby natural tree candidate found.");
                    return true;
                }
                TreeDna dna = dnaFor(candidate.get());
                TreePlan plan = planner.plan(dna, candidate.get().baseBlock().getBiome(), config.rootsEnabled());
                diagnostics.recordPlan(config, dna, plan, candidate.get().world());
                diagnostics.saveNow(plugin, config);
                sender.sendMessage("Tree debug: " + dna.species().id() + " stage=" + dna.maturityStage()
                        + " personality=" + dna.personality() + " rarity=" + dna.rarity()
                        + " age=" + dna.age() + " generation=" + dna.generation()
                        + " targetHeight=" + dna.targetHeight() + " branches=" + dna.branchCount()
                        + " branchLength=" + dna.minBranchLength() + "-" + dna.maxBranchLength()
                        + " canopy=" + dna.canopyRadiusX() + "x" + dna.canopyRadiusY() + "x" + dna.canopyRadiusZ()
                        + " trunkWidth=" + dna.trunkWidth()
                        + " layers=" + dna.canopyLayerCount()
                        + " sample=" + dna.profileSampleId()
                        + " planBlocks=" + plan.size());
                return true;
            }
            case "step" -> {
                Player player = (Player) sender;
                diagnostics.recordForcedStep(config, "player=" + player.getName());
                int placed = runNearPlayer(player, true);
                sender.sendMessage("Tree evolution forced step placed " + placed + " block(s).");
                return true;
            }
            case "preview" -> {
                Player player = (Player) sender;
                Optional<TreeCandidate> candidate = findNearestCandidate(player.getLocation(), 8);
                if (candidate.isEmpty()) {
                    sender.sendMessage("No nearby natural tree candidate found.");
                    return true;
                }
                TreeDna dna = dnaFor(candidate.get());
                TreePlan plan = planner.plan(dna, candidate.get().baseBlock().getBiome(), config.rootsEnabled());
                diagnostics.recordPlan(config, dna, plan, candidate.get().world());
                diagnostics.saveNow(plugin, config);
                sender.sendMessage("Preview saved to tree-evolution-trace.debug.yml and tree-evolution-map.debug.yml.");
                return true;
            }
            case "scan" -> {
                StructureScanResult result = scanner.scanAll(plugin);
                refreshProfileSamples(result);
                int sampleCount = profileSamples.values().stream().mapToInt(List::size).sum();
                sender.sendMessage("Scanned " + result.structures().size() + " NBT/schematic structure file(s) and loaded "
                        + sampleCount + " tree profile sample(s). See structure-scan-debug.yml and tree-profile-samples.yml.");
                plugin.pathDebug().trace(plugin, "tree-evolution", "persistence.debug-structure-scan",
                        "structures=" + result.structures().size() + " profile-samples=" + sampleCount);
                return true;
            }
            default -> {
                sender.sendMessage("Usage: /" + label + " tree <debug|step|preview|scan>");
                return true;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        schedulePlayerEvolution(event.getPlayer(), 80L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<TreeSpecies> species = TreeSpecies.fromMaterial(block.getType());
        if (species.isEmpty() || !config.isWorldAllowed(block.getWorld())) {
            return;
        }
        nearestCandidateCache.clear();
        knownCandidateCache.clear();
        focusedCandidateCache.clear();

        Optional<TreeCandidate> candidate = buildCandidate(block);
        if (candidate.isEmpty()) {
            plugin.pathDebug().trace(plugin, "tree-evolution", "break.skip-no-candidate", block.getType() + " at " + format(block));
            return;
        }

        TreeDna dna = dnaFor(candidate.get());
        boolean lowest = candidate.get().baseX() == block.getX()
                && candidate.get().baseY() == block.getY()
                && candidate.get().baseZ() == block.getZ();
        if (lowest) {
            dna.setStumpPresent(false);
            diagnostics.recordStalled(config, dna, "stump removed at " + format(block));
            plugin.pathDebug().trace(plugin, "tree-evolution", "break.stump-removed", format(block));
        } else {
            dna.markDamaged(config.damageStallMillis());
            diagnostics.recordStalled(config, dna, "damage stall until=" + dna.stalledUntilMillis() + " at " + format(block));
            plugin.pathDebug().trace(plugin, "tree-evolution", "break.damage-stall", format(block));
        }
        saveTreeDna();
        diagnostics.saveSoon(plugin, config);
    }

    private void schedulePlayerEvolution(Player player, long delayTicks) {
        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "scheduler.player-delay", "delay=" + Math.max(1L, delayTicks));
        player.getScheduler().runDelayed(
                plugin,
                task -> {
                    runNearPlayer(player, false);
                    TreeEvolutionConfig currentConfig = config;
                    if (player.isOnline()) {
                        schedulePlayerEvolution(player, focusedScheduleDelay(player.getUniqueId(), currentConfig));
                    }
                },
                null,
                Math.max(1L, delayTicks)
        );
    }

    private long focusedScheduleDelay(UUID playerId, TreeEvolutionConfig currentConfig) {
        TreeFocusPool pool = focusedTreePools.get(playerId);
        int activeTrees = pool == null ? 1 : Math.max(1, pool.size());
        // ## More active trees increase scheduler frequency, not per-tree speed.
        // Each candidate therefore keeps its configured cadence without a burst.
        return Math.max(1L, (currentConfig.stepTicks() + activeTrees - 1L) / activeTrees);
    }
    private int runNearPlayer(Player player, boolean forced) {
        try (ReportSample sample = plugin.resourceReporter().begin(
                "tree-evolution", forced ? "tick.run-near-player-forced" : "tick.run-near-player")) {
            TreeEvolutionConfig currentConfig = config;
            UUID playerId = player.getUniqueId();
            if (!player.isOnline()) {
                focusedTreePools.remove(playerId);
                plugin.pathDebug().trace(plugin, "tree-evolution", "tick.skip.offline-player", "player no longer online");
                sample.detail("offline-player");
                return 0;
            }
            if (!currentConfig.enabled()) {
                focusedTreePools.remove(playerId);
                plugin.pathDebug().trace(plugin, "tree-evolution", "tick.skip.disabled", "disabled");
                sample.detail("disabled");
                return 0;
            }

            Location origin = player.getLocation();
            World world = origin.getWorld();
            if (world == null || world.getEnvironment() != World.Environment.NORMAL
                    || !currentConfig.isWorldAllowed(world)) {
                focusedTreePools.remove(playerId);
                plugin.pathDebug().trace(plugin, "tree-evolution", "tick.skip.environment",
                        world == null ? "missing-world" : world.getName());
                sample.detail("environment-skip");
                return 0;
            }
            maybeCleanupTreeDna("tick");

            int placed = 0;
            int attempts = 0;
            boolean dnaStateChanged = false;
            Map<String, Integer> chunkActivity = new HashMap<>();
            Map<String, TreeCandidate> activeCandidates = new HashMap<>();
            List<TreeCandidate> backgroundCandidates = new ArrayList<>();
            Set<String> backgroundKeys = new HashSet<>();
            TreeFocusPool pool = focusedTreePools.computeIfAbsent(playerId, ignored -> new TreeFocusPool());

            for (TreeFocusPool.Entry entry : pool.entries()) {
                Optional<TreeCandidate> candidate = focusedCandidateFor(entry.treeKey(), origin, currentConfig);
                if (candidate.isPresent()) {
                    activeCandidates.put(entry.treeKey(), candidate.get());
                } else {
                    releaseFocusedTree(pool, entry.treeKey(), "unavailable",
                            "candidate is unloaded, unowned, outside range, or missing its base log");
                }
            }

            if (pool.size() < TreeFocusPool.CAPACITY) {
                int discoveryLimit = Math.min(
                        currentConfig.attemptsPerStep(), TreeFocusPool.CAPACITY * 2);
                List<TreeCandidate> discovered = new ArrayList<>(
                        findKnownCandidatesNear(origin, currentConfig, discoveryLimit));
                findNearestCandidate(origin, Math.min(18, currentConfig.searchRadius()))
                        .ifPresent(discovered::add);
                int randomSearches = 0;
                while (discovered.size() < discoveryLimit && randomSearches < discoveryLimit) {
                    randomSearches++;
                    findCandidate(origin, currentConfig).ifPresent(discovered::add);
                }

                Set<String> inspectedTrees = new HashSet<>();
                for (TreeCandidate candidate : discovered) {
                    TreeDna dna = dnaFor(candidate);
                    if (!inspectedTrees.add(dna.key())) {
                        continue;
                    }
                    if (pool.contains(dna.key())) {
                        activeCandidates.put(dna.key(), candidate);
                        continue;
                    }
                    if (focusCoolingDown(dna.key())) {
                        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "focus.skip.cooldown",
                                "tree=" + dna.key()
                                        + " ## a recently blocked tree yielded so another candidate can progress");
                        continue;
                    }

                    TreeWorkStatus status = treeWorkStatus(candidate, dna, currentConfig);
                    if (completeSatisfiedStageGrowthBurst(dna, status)) {
                        dnaStateChanged = true;
                        status = treeWorkStatus(candidate, dna, currentConfig);
                    }
                    if (status.needsFocus() && pool.acquire(dna.key())) {
                        activeCandidates.put(dna.key(), candidate);
                        plugin.pathDebug().trace(plugin, "tree-evolution", "focus.acquire",
                                "tree=" + dna.key()
                                        + " slot=" + pool.size() + "/" + TreeFocusPool.CAPACITY
                                        + " stage=" + dna.maturityStage() + " " + status.summary()
                                        + " ## this candidate keeps its slot until complete or bounded yield");
                    } else if (!status.needsFocus() && backgroundKeys.add(dna.key())) {
                        backgroundCandidates.add(candidate);
                    }
                    if (pool.size() >= TreeFocusPool.CAPACITY) {
                        break;
                    }
                }
            }

            int blockBudget = currentConfig.blocksPerStep();
            int attemptBudget = currentConfig.attemptsPerStep();
            for (TreeFocusPool.Entry entry : pool.nextRotation()) {
                if (attempts >= attemptBudget || placed >= blockBudget) {
                    break;
                }
                attempts++;
                TreeCandidate candidate = activeCandidates.get(entry.treeKey());
                if (candidate == null) {
                    Optional<TreeCandidate> resolved = focusedCandidateFor(
                            entry.treeKey(), origin, currentConfig);
                    if (resolved.isEmpty()) {
                        releaseFocusedTree(pool, entry.treeKey(), "unavailable",
                                "candidate disappeared before its scheduled action");
                        continue;
                    }
                    candidate = resolved.get();
                    activeCandidates.put(entry.treeKey(), candidate);
                }
                TreeDna dna = treeDna.get(entry.treeKey());
                if (dna == null) {
                    releaseFocusedTree(pool, entry.treeKey(), "unavailable", "DNA record missing");
                    continue;
                }
                String chunkKey = chunkKey(candidate);
                if (chunkActivity.getOrDefault(chunkKey, 0) >= 3) {
                    diagnostics.recordReject(currentConfig, "chunk-activity-budget", chunkKey);
                    continue;
                }
                if (!forced && !canGrowNow(dna, currentConfig, candidate)) {
                    plugin.pathDebug().traceSampled(plugin, "tree-evolution", "focus.retain.waiting-tick",
                            "tree=" + dna.key() + " stage=" + dna.maturityStage()
                                    + " active=" + pool.size());
                    continue;
                }

                TreeWorkStatus before = treeWorkStatus(candidate, dna, currentConfig);
                if (completeSatisfiedStageGrowthBurst(dna, before)) {
                    dnaStateChanged = true;
                    before = treeWorkStatus(candidate, dna, currentConfig);
                }
                if (!before.needsFocus()) {
                    releaseFocusedTree(pool, dna.key(), "complete",
                            "stage=" + dna.maturityStage() + " " + before.summary());
                    if (backgroundKeys.add(dna.key())) {
                        backgroundCandidates.add(candidate);
                    }
                    continue;
                }

                boolean changed = evolve(candidate, dna, currentConfig);
                if (changed) {
                    placed++;
                    chunkActivity.merge(chunkKey, 1, Integer::sum);
                }
                TreeWorkStatus after = treeWorkStatus(candidate, dna, currentConfig);
                if (completeSatisfiedStageGrowthBurst(dna, after)) {
                    dnaStateChanged = true;
                    after = treeWorkStatus(candidate, dna, currentConfig);
                }
                if (!after.needsFocus()) {
                    releaseFocusedTree(pool, dna.key(), "complete",
                            "stage=" + dna.maturityStage() + " " + after.summary());
                } else {
                    int noProgress = pool.updateProgress(dna.key(), changed);
                    if (TreeFocusPolicy.shouldYield(noProgress)) {
                        long cooldown = currentConfig.testingEnabled() ? 3_000L : 30_000L;
                        focusYieldUntil.put(dna.key(), System.currentTimeMillis() + cooldown);
                        releaseFocusedTree(pool, dna.key(), "no-progress",
                                "passes=" + noProgress + " cooldown-ms=" + cooldown + " " + after.summary());
                    } else {
                        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "focus.retain",
                                "tree=" + dna.key() + " changed=" + changed
                                        + " no-progress=" + noProgress + "/"
                                        + TreeFocusPolicy.MAX_NO_PROGRESS_PASSES
                                        + " active=" + pool.size() + "/" + TreeFocusPool.CAPACITY
                                        + " " + after.summary());
                    }
                }
            }

            // ## Completed trees still receive occasional detail/seedling work.
            // Structural candidates always consume the block budget first.
            for (TreeCandidate candidate : backgroundCandidates) {
                if (attempts >= attemptBudget || placed >= blockBudget) {
                    break;
                }
                attempts++;
                TreeDna dna = dnaFor(candidate);
                if (pool.contains(dna.key()) || (!forced && !canGrowNow(dna, currentConfig, candidate))) {
                    continue;
                }
                String chunkKey = chunkKey(candidate);
                if (chunkActivity.getOrDefault(chunkKey, 0) >= 3) {
                    continue;
                }
                if (evolve(candidate, dna, currentConfig)) {
                    placed++;
                    chunkActivity.merge(chunkKey, 1, Integer::sum);
                }
            }

            if (pool.isEmpty()) {
                focusedTreePools.remove(playerId, pool);
            }
            if (placed > 0 || dnaStateChanged) {
                if (placed > 0) {
                    markTreeDnaDirty("evolution-step placed=" + placed);
                }
                saveTreeDna();
            }
            int activeTrees = pool.size();
            sample.workUnits(attempts).changedUnits(placed)
                    .detail("placed=" + placed + " active=" + activeTrees + " near=" + format(origin));
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "focus.pool",
                    "active=" + activeTrees + "/" + TreeFocusPool.CAPACITY
                            + " placed=" + placed + " attempts=" + attempts
                            + " next-delay=" + focusedScheduleDelay(playerId, currentConfig)
                            + " ## bounded working set advances trees fairly without changing per-tree cadence");
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    placed > 0 ? "evolution.step.changed" : "evolution.step.no-change",
                    "placed=" + placed + " active=" + activeTrees + " near=" + format(origin));
            plugin.resourceReporter().count(plugin, "tree-evolution", "focus.active-trees",
                    activeTrees, placed, "capacity=" + TreeFocusPool.CAPACITY);
            return placed;
        }
    }

    private Optional<TreeCandidate> focusedCandidateFor(
            String treeKey, Location origin, TreeEvolutionConfig currentConfig) {
        TreeDna dna = treeDna.get(treeKey);
        World world = origin.getWorld();
        if (dna == null || world == null || !world.getUID().equals(dna.worldId())
                || !dna.stumpPresent()) {
            focusedCandidateCache.remove(treeKey);
            return Optional.empty();
        }
        long dx = (long) dna.baseX() - origin.getBlockX();
        long dz = (long) dna.baseZ() - origin.getBlockZ();
        long radius = currentConfig.searchRadius();
        if ((dx * dx) + (dz * dz) > radius * radius) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        CachedTreeCandidate cached = focusedCandidateCache.get(treeKey);
        if (cached != null && now < cached.expiresMillis()) {
            if (cached.candidate() == null) {
                return Optional.empty();
            }
            if (isCandidateStillValid(cached.candidate())) {
                return Optional.of(cached.candidate());
            }
            focusedCandidateCache.remove(treeKey, cached);
        }

        Optional<TreeCandidate> candidate = buildKnownCandidateFromDna(world, dna, currentConfig);
        long ttl = currentConfig.testingEnabled() ? 750L : 2_000L;
        focusedCandidateCache.put(treeKey,
                new CachedTreeCandidate(candidate.orElse(null), now + ttl));
        return candidate;
    }
    private boolean focusCoolingDown(String treeKey) {
        Long until = focusYieldUntil.get(treeKey);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() < until) {
            return true;
        }
        focusYieldUntil.remove(treeKey, until);
        return false;
    }

    private void releaseFocusedTree(
            TreeFocusPool pool, String treeKey, String reason, String detail) {
        if (!pool.release(treeKey)) {
            return;
        }
        plugin.pathDebug().trace(plugin, "tree-evolution", "focus.release." + reason,
                "tree=" + treeKey + " active=" + pool.size() + "/" + TreeFocusPool.CAPACITY
                        + " " + detail
                        + " ## this slot now accepts another structural candidate");
    }

    private TreeWorkStatus treeWorkStatus(
            TreeCandidate candidate, TreeDna dna, TreeEvolutionConfig currentConfig) {
        CachedTreePlan plan = cachedPlan(
                dna, candidate.baseBlock().getBiome(), currentConfig.rootsEnabled());
        TreeGrowthQueuePolicy.Completion completion = stageCompletion(candidate, dna, plan);
        TreeGrowthQueuePolicy.Budget budget = TreeGrowthQueuePolicy.stageBudget(dna);
        int exposedUpperLogs = exposedUpperLogCount(candidate, dna);
        boolean stageComplete = TreeFocusPolicy.stageStructureComplete(
                completion, budget, exposedUpperLogs);
        boolean transitionPending = dna.stageCleanupBurst() > 0
                || (dna.stageGrowthBurst() > 0 && !stageComplete);
        return new TreeWorkStatus(
                TreeFocusPolicy.needsFocus(
                        transitionPending, completion, budget, exposedUpperLogs),
                stageComplete,
                completion,
                budget,
                exposedUpperLogs
        );
    }

    private boolean completeSatisfiedStageGrowthBurst(TreeDna dna, TreeWorkStatus status) {
        if (!status.stageComplete() || dna.stageCleanupBurst() > 0
                || dna.stageGrowthBurst() <= 0) {
            return false;
        }
        int previousBurst = dna.stageGrowthBurst();
        int originalBlockCount = dna.originalShapeBlockCount();
        dna.completeStageGrowthBurst();
        markTreeDnaDirty("stage growth budget complete " + dna.key());
        plugin.pathDebug().trace(plugin, "tree-evolution", "focus.stage-budget-complete",
                "tree=" + dna.key() + " cleared-growth-burst=" + previousBurst
                        + " original-snapshot-cleared=" + originalBlockCount
                        + " " + status.summary()
                        + " ## full projected structure satisfied the current stage budget and released its temporary source shape");
        return true;
    }
    private boolean canGrowNow(TreeDna dna, TreeEvolutionConfig currentConfig, TreeCandidate candidate) {
        long now = System.currentTimeMillis();
        if (now < dna.stalledUntilMillis()) {
            diagnostics.recordReject(currentConfig, "stalled", dna.key());
            return false;
        }
        TreeGrowthProfile profile = profileFor(dna, currentConfig);
        long delayMillis = currentConfig.delayTicksFor(dna, profile, candidate.baseBlock().getBiome()) * 50L;
        delayMillis = Math.round(delayMillis * dna.currentIntent().delayMultiplier(dna) * forestDelayMultiplier(dna));
        if (dna.hasStageBurst()) {
            delayMillis = Math.max(100L, Math.round(delayMillis * currentConfig.stageBurstDelayMultiplier()));
        }
        if (dna.lastGrowthMillis() > 0L && now - dna.lastGrowthMillis() < delayMillis) {
            diagnostics.recordReject(currentConfig, "cooldown", dna.key() + " remaining-ms=" + (delayMillis - (now - dna.lastGrowthMillis())));
            return false;
        }
        Random breathing = new Random(dna.seed() ^ dna.age() ^ (now / 5000L));
        double breathingSkipChance = currentConfig.breathingSkipChance();
        if (breathingSkipChance > 0.0D && breathing.nextDouble() < breathingSkipChance) {
            diagnostics.recordReject(currentConfig, "breathing", dna.key() + " intent=" + dna.currentIntent());
            return false;
        }
        return true;
    }

    private boolean evolve(TreeCandidate candidate, TreeDna dna, TreeEvolutionConfig currentConfig) {
        try (ReportSample sample = plugin.resourceReporter().begin("tree-evolution", "action.evolve")) {
            if (!dna.stumpPresent()) {
                diagnostics.recordReject(currentConfig, "missing-stump", dna.key());
                sample.detail("missing-stump " + dna.key());
                return false;
            }
            if (candidate.baseBlock().getType() != dna.species().logMaterial()) {
                dna.setStumpPresent(false);
                markTreeDnaDirty("base-not-log");
                saveTreeDna();
                diagnostics.recordReject(currentConfig, "base-not-log", format(candidate.baseBlock()));
                sample.detail("base-not-log " + dna.key());
                return false;
            }
            if (!canWorkAt(candidate.baseLocation(), currentConfig)) {
                diagnostics.recordReject(currentConfig, "work-gate", format(candidate.baseLocation()));
                sample.detail("work-gate " + dna.key());
                return false;
            }
            if (!dna.hasOriginalShapeSnapshot()
                    && (dna.age() == 0 || dna.hasStageBurst())
                    && !ensureOriginalShapeSnapshot(candidate, dna,
                            "before-world-change")) {
                sample.detail("original-shape-wait " + dna.key());
                return false;
            }

        Biome biome = candidate.baseBlock().getBiome();
        CachedTreePlan cachedPlan = cachedPlan(
                dna, biome, currentConfig.rootsEnabled());
        TreeGrowthIntent requestedIntent = refreshIntent(candidate, dna);
        diagnostics.recordPlan(currentConfig, dna, cachedPlan.plan(),
                cachedPlan.orderedBlocks(), candidate.world(), false);

        // ## Grow before shedding. Cleanup is legal only during an explicit
        // persisted transition, never as random maintenance on a finished tree.
        boolean transitionCleanup = requestedIntent == TreeGrowthIntent.CLEANUP
                && dna.stageCleanupBurst() > 0;
        if (transitionCleanup) {
            TreeCandidate cleanupCandidate = candidate;
            if (!cleanupCandidate.ownershipComplete()) {
                cleanupCandidate = buildCandidate(
                        candidate.baseBlock(), true).orElse(candidate);
            }
            if (!cleanupCandidate.ownershipComplete()) {
                requestedIntent = TreeGrowthIntent.CANOPY;
                dna.setCurrentIntent(requestedIntent);
                diagnostics.recordReject(currentConfig,
                        "prune-ownership-incomplete",
                        dna.key() + " keys=" + cleanupCandidate.naturalKeys().size());
                plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                        "prune.waiting-complete-ownership",
                        "tree=" + dna.key()
                                + " keys=" + cleanupCandidate.naturalKeys().size()
                                + " ## no leaves are pruned and cleanup cannot finish until the bounded ownership walk reaches every connected crown edge");
            } else {
                TreeGrowthQueuePolicy.Completion transitionProgress =
                        stageCompletion(cleanupCandidate, dna, cachedPlan);
                double canopyPercent = transitionProgress.canopyPercent();
                double canopyFloor =
                        TreeCanopyTransitionPolicy.minimumReplacementCanopy(dna);
                double progressiveFloor =
                        TreeCanopyTransitionPolicy.progressiveCleanupCanopy(dna);
                boolean broadPruningReady = canopyPercent >= canopyFloor;
                boolean progressivePeel = !broadPruningReady
                        && TreeCanopyTransitionPolicy.shouldPeelStaleLayer(
                                dna, canopyPercent, dna.consecutivePrunes());
                boolean woodBlockersOnly = !broadPruningReady && !progressivePeel;
                String cleanupMode = broadPruningReady ? "full"
                        : progressivePeel ? "progressive-shelf" : "wood-blockers";
                List<Block> staleLeaves = findStaleCanopyLeaves(
                        cleanupCandidate, dna, cachedPlan.orderedBlocks(),
                        pruneBatchSize(dna), currentConfig,
                        woodBlockersOnly);
                if (!staleLeaves.isEmpty()) {
                    for (Block leaf : staleLeaves) {
                        leaf.setType(Material.AIR, false);
                    }
                    dna.markPrunedNow();
                    changedBlocks.addAndGet(staleLeaves.size());
                    diagnostics.recordPrunedBatch(
                            plugin, currentConfig, staleLeaves, dna);
                    plugin.pathDebug().trace(plugin, "tree-evolution",
                            "prune.target-shape-batch",
                            "tree=" + dna.key()
                                    + " removed=" + staleLeaves.size()
                                    + " first=" + format(staleLeaves.get(0))
                                    + " canopy=" + Math.round(
                                            transitionProgress.canopyPercent()
                                                    * 100.0D)
                                    + "% floor=" + Math.round(
                                            canopyFloor * 100.0D)
                                    + "% progressive-floor=" + Math.round(
                                            progressiveFloor * 100.0D)
                                    + "% mode=" + cleanupMode
                                    + " ownership-complete=true"
                                    + " ## replacement canopy remains live while owned obsolete leaves clear");
                    sample.changedUnits(staleLeaves.size())
                            .detail("prune.target-shape-batch " + dna.key());
                    return true;
                }
                if (!broadPruningReady) {
                    requestedIntent = TreeGrowthIntent.CANOPY;
                    dna.setCurrentIntent(requestedIntent);
                    plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                            "prune.waiting-replacement-canopy",
                            "tree=" + dna.key()
                                    + " canopy=" + Math.round(
                                            transitionProgress.canopyPercent()
                                                    * 100.0D)
                                    + "% floor=" + Math.round(
                                            canopyFloor * 100.0D)
                                    + "% progressive-floor=" + Math.round(
                                            progressiveFloor * 100.0D)
                                    + "% mode=" + cleanupMode
                                    + " ## the new crown grows between bounded old-shelf peel actions");
                } else {
                    dna.completeStageCleanup();
                    requestedIntent = dna.stageGrowthBurst() > 0
                            ? stageBurstIntent(cleanupCandidate, dna)
                            : TreeGrowthIntent.CANOPY;
                    dna.setCurrentIntent(requestedIntent);
                    markTreeDnaDirty(
                            "transition cleanup complete " + dna.key());
                    saveTreeDna();
                    plugin.pathDebug().trace(plugin, "tree-evolution",
                            "prune.target-shape-complete",
                            "tree=" + dna.key()
                                    + " next=" + requestedIntent
                                    + " canopy=" + Math.round(
                                            transitionProgress.canopyPercent()
                                                    * 100.0D)
                                    + "% growth-burst="
                                    + dna.stageGrowthBurst()
                                    + " ownership-complete=true"
                                    + " ## old crown is clear and the replacement crown is already established");
                }
            }
        }

        TreeGrowthIntent intent = stageBudgetIntent(
                candidate, dna, cachedPlan, requestedIntent, currentConfig);
        diagnostics.recordIntent(currentConfig, dna, intent,
                "requested=" + requestedIntent
                        + " cursor=" + dna.planCursor()
                        + " blocked=" + dna.blockedAttempts());
        Optional<Block> seedlingSpot = intent == TreeGrowthIntent.SEEDLING ? findSeedlingSpot(candidate, dna) : Optional.empty();
        if (seedlingSpot.isPresent()) {
            Block sapling = seedlingSpot.get();
            sapling.setType(dna.species().saplingMaterial(), false);
            dna.markPlacedForIntent(intent, dna.planCursor());
            dna.consumeStageGrowthBurst();
            changedBlocks.incrementAndGet();
            diagnostics.recordSeedling(plugin, currentConfig, sapling, dna);
            plugin.pathDebug().trace(plugin, "tree-evolution", "seedling.spread",
                    dna.species().saplingMaterial() + " child-of=" + dna.key() + " at " + format(sapling));
            sample.changedUnits(1).detail("seedling.spread " + dna.key());
            return true;
        }

        if (intent == TreeGrowthIntent.CANOPY) {
            Optional<Block> exposedLog = findExposedUpperLog(candidate, dna);
            if (exposedLog.isPresent()) {
                int liftedLeaves = coverExposedLog(candidate, dna, currentConfig, exposedLog.get());
                if (liftedLeaves > 0) {
                    dna.markPlacedForIntent(intent, dna.planCursor());
                    dna.consumeStageGrowthBurst();
                    updateMaturity(candidate, dna, currentConfig);
                    plugin.pathDebug().trace(plugin, "tree-evolution", "shape.integrity.canopy-cover",
                            "tree=" + dna.key() + " trunk=" + format(exposedLog.get()) + " leaves=" + liftedLeaves
                                    + " ## canopy shell corrected an exposed live upper trunk before normal target selection");
                    sample.changedUnits(liftedLeaves).detail("canopy.integrity-cover " + dna.key());
                    return true;
                }
            }
        }

        Optional<PlannedTarget> plannedTarget = nextPlannedTarget(candidate, dna, cachedPlan.orderedBlocks(), cachedPlan.blocksByKey(), intent, currentConfig);
        if (plannedTarget.isPresent()) {
            PlannedTreeBlock plannedBlock = plannedTarget.get().block();
            Block target = plannedTarget.get().target();
            place(target, plannedBlock);
            dna.markPlacedForIntent(intent, plannedTarget.get().nextCursor());
            int liftedLeaves = maybeCoverExposedTopLog(candidate, dna, currentConfig, target, plannedBlock);
            if (intent != TreeGrowthIntent.CLEANUP && intent != TreeGrowthIntent.SEEDLING) {
                dna.consumeStageGrowthBurst();
            }
            updateMaturity(candidate, dna, currentConfig);
            changedBlocks.incrementAndGet();
            diagnostics.recordPlaced(plugin, currentConfig, target, plannedBlock);
            plugin.pathDebug().trace(plugin, "tree-evolution", "place.block",
                    plannedBlock.role() + " " + plannedBlock.material() + " intent=" + intent + " at " + format(target)
                            + (liftedLeaves > 0 ? " canopy-lift-leaves=" + liftedLeaves : ""));
            sample.changedUnits(1).detail(plannedBlock.role() + " " + plannedBlock.material() + " intent=" + intent + " dna=" + dna.key());
            return true;
        }

        if (intent != TreeGrowthIntent.CLEANUP) {
            diagnostics.recordReject(currentConfig, "prune-skipped-normal-growth", dna.key() + " intent=" + intent);
        }
        dna.markBlocked();
        if (dna.blockedAttempts() >= 3) {
            dna.setCurrentIntent(nextIntentAfterBlocked(dna.currentIntent()));
        }
        diagnostics.recordReject(currentConfig, "target-complete-or-blocked", dna.key() + " intent=" + intent + " blocked=" + dna.blockedAttempts());
        sample.detail("target-complete-or-blocked " + dna.key() + " intent=" + intent);
        return false;
        }
    }

    private Optional<PlannedTarget> nextPlannedTarget(TreeCandidate candidate, TreeDna dna, List<PlannedTreeBlock> orderedBlocks, Map<String, PlannedTreeBlock> blocksByKey, TreeGrowthIntent intent, TreeEvolutionConfig currentConfig) {
        if (orderedBlocks.isEmpty()) {
            return Optional.empty();
        }
        int size = orderedBlocks.size();
        int start = Math.floorMod(dna.planCursor(), size);
        List<TreeShapeEngine.ShapeChoice> choices = new ArrayList<>();
        List<CandidateBlock> intentBlocks = new ArrayList<>();
        int nextHeight = candidate.topY() + 1;
        int liveTop = Math.max(candidate.topY(), dna.baseY() + liveTrunkHeight(candidate.world(), dna) - 1);
        for (int checked = 0; checked < size; checked++) {
            int index = (start + checked) % size;
            PlannedTreeBlock plannedBlock = orderedBlocks.get(index);
            if (matchesIntent(dna, plannedBlock, intent)) {
                int priority = dependencyPriority(dna, plannedBlock, intent, nextHeight, liveTop);
                intentBlocks.add(new CandidateBlock(plannedBlock, index, priority));
            }
        }
        intentBlocks.sort(growthOrder(candidate, dna));
        int dependencyWaits = 0;
        int placementRejects = 0;
        int checkedTargets = 0;
        int targetBudget = currentConfig.testingEnabled() ? 512 : 256;
        for (CandidateBlock candidateBlock : intentBlocks) {
            if (++checkedTargets > targetBudget && choices.isEmpty()) {
                plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.search-budget-stop",
                        "intent=" + intent + " checked=" + checkedTargets + " rejects=" + placementRejects
                                + " waits=" + dependencyWaits + " tree=" + dna.key()
                                + " ## bounded planner scan protects Folia region ticks; retry continues next cycle");
                return Optional.empty();
            }
            PlannedTreeBlock plannedBlock = candidateBlock.block();
            Block target = targetBlockFor(candidate.world(), plannedBlock);
            if (target.getType() == plannedBlock.material()) {
                continue;
            }
            if (!isDependencyReady(candidate, dna, target, plannedBlock, intent, currentConfig, dependencyWaits < 8)) {
                Optional<PlannedTreeBlock> repairBlock = branchParentRepairBlock(candidate, dna, plannedBlock, blocksByKey, currentConfig);
                if (repairBlock.isPresent()) {
                    PlannedTreeBlock repair = repairBlock.get();
                    Block repairTarget = targetBlockFor(candidate.world(), repair);
                    if (repairTarget.getType() != repair.material()
                            && isDependencyReady(candidate, dna, repairTarget, repair, intent, currentConfig, false)
                            && canPlace(candidate, dna, repairTarget, repair, currentConfig)) {
                        int originalIndex = Math.max(0, orderedBlocks.indexOf(repair));
                        choices.add(shapeEngine.score(candidate, dna, repair, repairTarget, intent, (originalIndex + 1) % size));
                        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.parent-repair-choice",
                                "child=" + plannedBlock.branchId() + ":" + plannedBlock.branchStep()
                                        + " repair-role=" + repair.role()
                                        + " repair=" + format(repairTarget)
                                        + " ## branch parent repair grows missing planned wood before retrying the child segment");
                        if (shapeEngine.hasEnoughChoices(choices)) {
                            break;
                        }
                    }
                }
                dependencyWaits++;
                continue;
            }
            if (!canPlace(candidate, dna, target, plannedBlock, currentConfig)) {
                placementRejects++;
                if (placementRejects >= 96 && choices.isEmpty()) {
                    plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.search-budget-stop",
                            "intent=" + intent + " rejects=" + placementRejects + " waits=" + dependencyWaits
                                    + " tree=" + dna.key());
                    return Optional.empty();
                }
                continue;
            }
            choices.add(shapeEngine.score(candidate, dna, plannedBlock, target, intent, (candidateBlock.index() + 1) % size));
            if (shapeEngine.hasEnoughChoices(choices)) {
                break;
            }
        }
        TreeShapeEngine.ShapeChoice best = shapeEngine.bestChoice(choices);
        if (best == null) {
            if (dependencyWaits > 0) {
                plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.waiting-dependencies",
                        "intent=" + intent + " waits=" + dependencyWaits + " tree=" + dna.key()
                                + " ## dependency builder is waiting for trunk/parent branch blocks instead of forcing floating wood");
            }
            return Optional.empty();
        }
        diagnostics.recordShapeChoice(currentConfig, dna, best.reason(), choices.size());
        return Optional.of(new PlannedTarget(best.block(), best.target(), best.nextCursor(), best.score(), best.reason()));
    }

    private Comparator<CandidateBlock> growthOrder(TreeCandidate candidate, TreeDna dna) {
        return Comparator
                .comparingInt(CandidateBlock::priority)
                .thenComparingInt(block -> block.block().branchId() < 0 ? Integer.MAX_VALUE : block.block().branchId())
                .thenComparingInt(block -> block.block().branchStep() < 0 ? Integer.MAX_VALUE : block.block().branchStep())
                .thenComparingInt(block -> Math.abs(block.block().y() - (candidate.topY() + 1)))
                .thenComparingInt(block -> block.block().y())
                .thenComparingInt(block -> block.block().x())
                .thenComparingInt(block -> block.block().z());
    }

    private int dependencyPriority(TreeDna dna, PlannedTreeBlock block, TreeGrowthIntent intent, int nextHeight, int liveTop) {
        return switch (block.role()) {
            case TRUNK -> {
                int verticalDistance = Math.abs(block.y() - nextHeight);
                int horizontal = Math.abs(block.x() - dna.trunkXAt(block.y())) + Math.abs(block.z() - dna.trunkZAt(block.y()));
                yield (verticalDistance * 12) + horizontal;
            }
            case BRANCH -> (Math.max(0, block.branchStep()) * 20) + Math.max(0, block.branchId());
            case CANOPY -> {
                int topY = dna.baseY() + TreeSpeciesStageStyle.visibleHeight(dna) - 1;
                int topDistance = Math.min(Math.abs(block.y() - topY), Math.abs(block.y() - liveTop));
                int horizontal = Math.max(Math.abs(block.x() - dna.trunkXAt(Math.min(topY, block.y()))), Math.abs(block.z() - dna.trunkZAt(Math.min(topY, block.y()))));
                yield (topDistance * 8) + horizontal;
            }
            case VINE, GROUND_DETAIL, FALLEN_LOG, SAPLING, ROOT -> 1000 + block.y();
        };
    }
    private TreeGrowthIntent refreshIntent(TreeCandidate candidate, TreeDna dna) {
        if (dna.stageCleanupBurst() > 0) {
            dna.setCurrentIntent(TreeGrowthIntent.CLEANUP);
            return dna.currentIntent();
        }
        if (dna.currentIntent() == TreeGrowthIntent.CLEANUP) {
            // ## Cleanup is a transition state, not a weighted maintenance task.
            dna.setCurrentIntent(TreeGrowthIntent.CANOPY);
        }
        if (dna.stageGrowthBurst() > 0) {
            dna.setCurrentIntent(stageBurstIntent(candidate, dna));
            return dna.currentIntent();
        }
        TreeGrowthIntent preferred = preferredIntent(candidate, dna);
        if (dna.blockedAttempts() >= 3 || dna.age() - dna.lastIntentChangeAge() >= intentSpan(dna, dna.currentIntent())) {
            dna.setCurrentIntent(preferred);
        }
        if (dna.damageCount() > 0 && dna.currentIntent() != TreeGrowthIntent.REPAIR) {
            dna.setCurrentIntent(TreeGrowthIntent.REPAIR);
        }
        return dna.currentIntent();
    }

    private TreeGrowthIntent stageBudgetIntent(TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan, TreeGrowthIntent intent, TreeEvolutionConfig currentConfig) {
        TreeGrowthQueuePolicy.Completion completion = stageCompletion(candidate, dna, cachedPlan);
        TreeGrowthQueuePolicy.Budget budget = TreeGrowthQueuePolicy.stageBudget(dna);
        int exposedUpperLogs = exposedUpperLogCount(candidate, dna);
        TreeGrowthContract.Assessment contract = TreeGrowthContract.assess(
                dna,
                completion,
                budget,
                intent,
                candidate.connectedLogs(),
                candidate.connectedLeaves(),
                exposedUpperLogs
        );
        TreeGrowthIntent selected = contract.intent();
        if (selected != intent) {
            dna.setCurrentIntent(selected);
        }
        String detail = contract.summary(completion, budget, intent);
        diagnostics.recordGrowthContract(currentConfig, dna, contract, detail);
        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "stage.growth-contract",
                "tree=" + dna.key()
                        + " stage=" + dna.maturityStage()
                        + " " + detail
                        + " ## live integrity can override weighted intent when projected and actual shape drift apart");
        return selected;
    }

    private TreeGrowthQueuePolicy.Completion stageCompletion(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan) {
        int visibleHeight = Math.max(1, TreeSpeciesStageStyle.visibleHeight(dna));
        int liveHeight = Math.max(candidate.height(), liveTrunkHeight(candidate.world(), dna));
        TreeProjectionProgress progress = cachedProjectionProgress(candidate.world(), dna, cachedPlan);
        return new TreeGrowthQueuePolicy.Completion(
                liveHeight,
                visibleHeight,
                progress.trunkPlaced(),
                progress.trunkTotal(),
                progress.branchPlaced(),
                progress.branchTotal(),
                progress.canopyPlaced(),
                progress.canopyTotal()
        );
    }

    private TreeProjectionProgress cachedProjectionProgress(
            World world, TreeDna dna, CachedTreePlan cachedPlan) {
        long now = System.currentTimeMillis();
        CachedProjectionProgress cached = projectionProgressCache.get(dna.key());
        if (cached != null
                && cached.signature().equals(cachedPlan.signature())
                && now < cached.expiresMillis()) {
            return cached.progress();
        }
        // ## Completion must inspect the whole target. A capped canopy sample can
        // falsely call a large fluffy crown complete while most of it is absent.
        TreeProjectionProgress progress = projectionProgress(
                world, cachedPlan.orderedBlocks(), Integer.MAX_VALUE);
        long ttl = config.testingEnabled() ? 750L : 2_000L;
        projectionProgressCache.put(dna.key(), new CachedProjectionProgress(
                cachedPlan.signature(), progress, now + ttl));
        return progress;
    }
    private int exposedUpperLogCount(TreeCandidate candidate, TreeDna dna) {
        World world = candidate.world();
        int liveHeight = liveTrunkHeight(world, dna);
        int liveTop = dna.baseY() + liveHeight - 1;
        if (liveHeight < 3) {
            return 0;
        }
        int startY = Math.max(dna.baseY(), liveTop - 3);
        int exposed = 0;
        for (int y = liveTop; y >= startY; y--) {
            int width = TreeSpeciesStageStyle.trunkWidthAt(dna, y);
            int radius = Math.max(0, width / 2);
            int centerX = dna.trunkXAt(y);
            int centerZ = dna.trunkZAt(y);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(centerX + x, y, centerZ + z);
                    if (block.getType() == dna.species().logMaterial() && !hasAdjacentLeaf(block, dna.species().leafMaterial())) {
                        exposed++;
                    }
                }
            }
        }
        if (exposed > 0) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "shape.integrity.exposed-upper-logs",
                    "tree=" + dna.key() + " exposed=" + exposed + " live-top=" + liveTop
                            + " ## exposed upper logs force canopy shell before the tree keeps widening or detailing");
        }
        return exposed;
    }

    private TreeProjectionProgress projectionProgress(World world, List<PlannedTreeBlock> orderedBlocks, int sampleLimitPerRole) {
        int trunkTotal = 0;
        int trunkPlaced = 0;
        int branchTotal = 0;
        int branchPlaced = 0;
        int canopyTotal = 0;
        int canopyPlaced = 0;
        Map<Long, Boolean> readableChunks = new HashMap<>();
        for (PlannedTreeBlock block : orderedBlocks) {
            int chunkX = block.x() >> 4;
            int chunkZ = block.z() >> 4;
            long chunkKey = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
            boolean readable = readableChunks.computeIfAbsent(chunkKey,
                    ignored -> world.isChunkLoaded(chunkX, chunkZ)
                            && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0));
            if (block.role() == TreeBlockRole.TRUNK && trunkTotal < sampleLimitPerRole) {
                trunkTotal++;
                if (readable && world.getBlockAt(block.x(), block.y(), block.z()).getType() == block.material()) {
                    trunkPlaced++;
                }
            } else if (block.role() == TreeBlockRole.BRANCH && branchTotal < sampleLimitPerRole) {
                branchTotal++;
                if (readable && world.getBlockAt(block.x(), block.y(), block.z()).getType() == block.material()) {
                    branchPlaced++;
                }
            } else if (block.role() == TreeBlockRole.CANOPY && canopyTotal < sampleLimitPerRole) {
                canopyTotal++;
                if (readable && world.getBlockAt(block.x(), block.y(), block.z()).getType() == block.material()) {
                    canopyPlaced++;
                }
            }
            if (trunkTotal >= sampleLimitPerRole && branchTotal >= sampleLimitPerRole && canopyTotal >= sampleLimitPerRole) {
                break;
            }
        }
        return new TreeProjectionProgress(trunkPlaced, trunkTotal, branchPlaced, branchTotal, canopyPlaced, canopyTotal);
    }

    private TreeGrowthIntent stageBurstIntent(TreeCandidate candidate, TreeDna dna) {
        int visibleHeight = TreeSpeciesStageStyle.visibleHeight(dna);
        if (candidate.height() < Math.max(3, visibleHeight - 1)) {
            return dna.hugeArchitecture() && candidate.height() >= Math.max(4, visibleHeight / 3)
                    ? TreeGrowthIntent.WIDTH
                    : TreeGrowthIntent.HEIGHT;
        }
        int remaining = dna.stageGrowthBurst();
        if (dna.maturityStage() == TreeMaturityStage.MEDIUM) {
            if (remaining >= 4) {
                return TreeGrowthIntent.CANOPY;
            }
            if (remaining >= 2) {
                return TreeGrowthIntent.BRANCH;
            }
            return TreeGrowthIntent.CANOPY;
        }
        if (remaining >= 10) {
            return dna.hugeArchitecture() ? TreeGrowthIntent.WIDTH : TreeGrowthIntent.HEIGHT;
        }
        if (remaining >= 7) {
            return TreeGrowthIntent.CANOPY;
        }
        if (remaining >= 4) {
            return TreeGrowthIntent.BRANCH;
        }
        if (remaining >= 2) {
            return TreeGrowthIntent.CANOPY;
        }
        return TreeGrowthIntent.DETAIL;
    }

    private TreeGrowthIntent preferredIntent(TreeCandidate candidate, TreeDna dna) {
        Random random = new Random(dna.seed() ^ (dna.age() * 43L) ^ 0x1A17EEL);
        if (dna.damageCount() > 0) {
            return weightedIntent(random, TreeGrowthIntent.REPAIR, 70, TreeGrowthIntent.CANOPY, 30);
        }
        int visibleHeight = TreeSpeciesStageStyle.visibleHeight(dna);
        if (candidate.height() < Math.max(4, visibleHeight - 1)) {
            return weightedIntent(random, TreeGrowthIntent.HEIGHT, 72, TreeGrowthIntent.CANOPY, 18, TreeGrowthIntent.BRANCH, 10);
        }
        if (dna.maturityStage() == TreeMaturityStage.SMALL) {
            return weightedIntent(random, TreeGrowthIntent.CANOPY, 64, TreeGrowthIntent.HEIGHT, 24, TreeGrowthIntent.BRANCH, 12);
        }
        if (dna.hugeArchitecture() && dna.trunkWidth() > 1 && candidate.height() >= Math.max(5, dna.targetHeight() / 3) && random.nextInt(100) < 12) {
            return TreeGrowthIntent.WIDTH;
        }
        if (candidate.height() < Math.max(4, visibleHeight * 2 / 3)) {
            return weightedIntent(random, TreeGrowthIntent.HEIGHT, 50, TreeGrowthIntent.BRANCH, 28, TreeGrowthIntent.CANOPY, 22);
        }
        if (dna.maturityStage() == TreeMaturityStage.MEDIUM) {
            return weightedIntent(random, TreeGrowthIntent.CANOPY, 54, TreeGrowthIntent.BRANCH, 26, TreeGrowthIntent.HEIGHT, 14, TreeGrowthIntent.DETAIL, 6);
        }
        if (dna.maturityStage() == TreeMaturityStage.MATURE || dna.maturityStage() == TreeMaturityStage.ANCIENT) {
            int seedlingWeight = dna.age() > 24 && dna.damageCount() == 0 ? 7 : 0;
            return weightedIntent(random, TreeGrowthIntent.CANOPY, 40, TreeGrowthIntent.DETAIL, 26, TreeGrowthIntent.BRANCH, 20, TreeGrowthIntent.SEEDLING, seedlingWeight, TreeGrowthIntent.WIDTH, dna.hugeArchitecture() ? 7 : 0);
        }
        return TreeGrowthIntent.HEIGHT;
    }

    private TreeGrowthIntent weightedIntent(Random random, Object... pairs) {
        int total = 0;
        for (int index = 1; index < pairs.length; index += 2) {
            total += Math.max(0, (Integer) pairs[index]);
        }
        if (total <= 0) {
            return TreeGrowthIntent.HEIGHT;
        }
        int roll = random.nextInt(total);
        for (int index = 0; index < pairs.length; index += 2) {
            TreeGrowthIntent intent = (TreeGrowthIntent) pairs[index];
            int weight = Math.max(0, (Integer) pairs[index + 1]);
            if (roll < weight) {
                return intent;
            }
            roll -= weight;
        }
        return TreeGrowthIntent.HEIGHT;
    }

    private int intentSpan(TreeDna dna, TreeGrowthIntent intent) {
        return switch (intent) {
            case HEIGHT -> 5;
            case WIDTH -> 4;
            case BRANCH -> 5;
            case CANOPY -> dna.hasStageBurst() ? 2 : 6;
            case CLEANUP -> 2;
            case DETAIL -> 4;
            case SEEDLING -> 1;
            case REPAIR -> 3;
        };
    }

    private TreeGrowthIntent nextIntentAfterBlocked(TreeGrowthIntent intent) {
        return switch (intent) {
            case HEIGHT -> TreeGrowthIntent.BRANCH;
            case WIDTH -> TreeGrowthIntent.BRANCH;
            case BRANCH -> TreeGrowthIntent.CANOPY;
            case CANOPY -> TreeGrowthIntent.DETAIL;
            case CLEANUP -> TreeGrowthIntent.CANOPY;
            case DETAIL -> TreeGrowthIntent.CANOPY;
            case SEEDLING -> TreeGrowthIntent.CANOPY;
            case REPAIR -> TreeGrowthIntent.CANOPY;
        };
    }

    private int pruneBatchSize(TreeDna dna) {
        // ## One stale leaf per action keeps the visible transition gradual and
        // prevents fast testing ticks from stripping a crown between frames.
        return 1;
    }
    private boolean requiresDirectWoodSupport(TreeBlockRole role) {
        return role == TreeBlockRole.TRUNK || role == TreeBlockRole.BRANCH;
    }

    private boolean matchesIntent(TreeDna dna, PlannedTreeBlock plannedBlock, TreeGrowthIntent intent) {
        return switch (intent) {
            case HEIGHT -> plannedBlock.role() == TreeBlockRole.TRUNK
                    && plannedBlock.y() <= dna.baseY() + TreeSpeciesStageStyle.visibleHeight(dna) - 1;
            case WIDTH -> plannedBlock.role() == TreeBlockRole.TRUNK
                    && TreeSpeciesStageStyle.trunkWidthAt(dna, plannedBlock.y()) > 1
                    && plannedBlock.y() <= dna.baseY() + Math.max(4, Math.round(dna.targetHeight() * 0.58F));
            case BRANCH -> plannedBlock.role() == TreeBlockRole.BRANCH;
            case CANOPY -> plannedBlock.role() == TreeBlockRole.CANOPY;
            case CLEANUP -> false;
            case DETAIL -> plannedBlock.role() == TreeBlockRole.VINE
                    || plannedBlock.role() == TreeBlockRole.GROUND_DETAIL
                    || plannedBlock.role() == TreeBlockRole.FALLEN_LOG;
            case SEEDLING -> plannedBlock.role() == TreeBlockRole.SAPLING;
            case REPAIR -> plannedBlock.role() == TreeBlockRole.TRUNK
                    || plannedBlock.role() == TreeBlockRole.BRANCH
                    || plannedBlock.role() == TreeBlockRole.CANOPY;
        };
    }

    private CachedTreePlan cachedPlan(TreeDna dna, Biome biome, boolean rootsEnabled) {
        String signature = dna.planSignature(rootsEnabled, biome);
        CachedTreePlan cached = planCache.get(dna.key());
        if (cached != null && cached.signature().equals(signature)) {
            return cached;
        }
        TreePlan plan = planner.plan(dna, biome, rootsEnabled);
        List<PlannedTreeBlock> orderedBlocks = plan.orderedBlocks();
        Map<String, PlannedTreeBlock> blocksByKey = new HashMap<>();
        for (PlannedTreeBlock block : orderedBlocks) {
            blocksByKey.put(block.key(), block);
        }
        CachedTreePlan fresh = new CachedTreePlan(signature, plan, orderedBlocks, blocksByKey);
        planCache.put(dna.key(), fresh);
        projectionProgressCache.remove(dna.key());
        return fresh;
    }

    private double forestDelayMultiplier(TreeDna dna) {
        int nearby = 0;
        for (TreeDna other : treeDna.values()) {
            if (other == dna || other.worldId() == null || !other.worldId().equals(dna.worldId())) {
                continue;
            }
            int distance = Math.abs(other.baseX() - dna.baseX()) + Math.abs(other.baseZ() - dna.baseZ());
            if (distance <= 36 && other.damageCount() <= 1 && other.stumpPresent()) {
                nearby++;
            }
            if (nearby >= 6) {
                return 0.82D;
            }
        }
        return nearby >= 3 ? 0.92D : 1.0D;
    }

    private List<Block> findStaleCanopyLeaves(TreeCandidate candidate, TreeDna dna,
            List<PlannedTreeBlock> orderedBlocks, int limit,
            TreeEvolutionConfig currentConfig, boolean woodBlockersOnly) {
        if (limit <= 0) {
            return List.of();
        }
        TreeCanopyTransitionPolicy policy = TreeCanopyTransitionPolicy.from(
                dna, orderedBlocks, candidate.topY());
        Set<String> protectedCanopyKeys = nearbyPlannedCanopyKeys(
                candidate, dna, currentConfig);
        Map<String, Block> staleByKey = new HashMap<>();

        // ## Planned wood wins over old leaves so the new trunk and supported
        // branches cannot be permanently blocked by their former crown.
        for (PlannedTreeBlock woodTarget : policy.woodTargets()) {
            Block block = candidate.world().getBlockAt(
                    woodTarget.x(), woodTarget.y(), woodTarget.z());
            collectStaleCanopyLeaf(candidate, dna, policy,
                    protectedCanopyKeys, block, staleByKey);
        }

        if (woodBlockersOnly) {
            List<Block> blockers = new ArrayList<>(staleByKey.values());
            blockers.sort(Comparator
                    .comparingInt(Block::getY)
                    .thenComparingInt(Block::getX)
                    .thenComparingInt(Block::getZ));
            return List.copyOf(blockers.subList(
                    0, Math.min(limit, blockers.size())));
        }

        // ## Read the saved source shape directly. This catches disconnected
        // residual shelves outside the newer crown corridor without claiming
        // any leaf that appeared after this transition started.
        for (String originalLeafKey : dna.originalShapeLeaves()) {
            blockFromKey(candidate.world(), originalLeafKey)
                    .ifPresent(block -> collectStaleCanopyLeaf(
                            candidate, dna, policy, protectedCanopyKeys,
                            block, staleByKey));
        }

        List<Block> staleLeaves = new ArrayList<>(staleByKey.values());
        staleLeaves.sort((first, second) -> {
            int firstWood = policy.replacesWithWood(
                    first.getX(), first.getY(), first.getZ()) ? 0 : 1;
            int secondWood = policy.replacesWithWood(
                    second.getX(), second.getY(), second.getZ()) ? 0 : 1;
            int comparison = Integer.compare(firstWood, secondWood);
            if (comparison != 0) {
                return comparison;
            }
            int firstShelf = policy.isLegacyShelf(first.getY()) ? 0 : 1;
            int secondShelf = policy.isLegacyShelf(second.getY()) ? 0 : 1;
            comparison = Integer.compare(firstShelf, secondShelf);
            if (comparison != 0) {
                return comparison;
            }
            comparison = firstShelf == 0
                    ? Integer.compare(first.getY(), second.getY())
                    : Integer.compare(second.getY(), first.getY());
            if (comparison != 0) {
                return comparison;
            }
            int firstDistance = Math.abs(first.getX() - dna.trunkXAt(first.getY()))
                    + Math.abs(first.getZ() - dna.trunkZAt(first.getY()));
            int secondDistance = Math.abs(second.getX() - dna.trunkXAt(second.getY()))
                    + Math.abs(second.getZ() - dna.trunkZAt(second.getY()));
            return Integer.compare(secondDistance, firstDistance);
        });
        return List.copyOf(staleLeaves.subList(
                0, Math.min(limit, staleLeaves.size())));
    }

    private Set<String> nearbyPlannedCanopyKeys(TreeCandidate candidate,
            TreeDna activeDna, TreeEvolutionConfig currentConfig) {
        Set<String> protectedKeys = new HashSet<>();
        Biome planningBiome = candidate.baseBlock().getBiome();
        for (TreeDna nearbyDna : treeDna.values()) {
            if (nearbyDna.key().equals(activeDna.key())
                    || !nearbyDna.stumpPresent()
                    || !nearbyDna.worldId().equals(activeDna.worldId())
                    || Math.abs(nearbyDna.baseX() - activeDna.baseX()) > 20
                    || Math.abs(nearbyDna.baseZ() - activeDna.baseZ()) > 20
                    || Math.abs(nearbyDna.baseY() - activeDna.baseY()) > 32) {
                continue;
            }
            CachedTreePlan nearbyPlan = cachedPlan(
                    nearbyDna, planningBiome, currentConfig.rootsEnabled());
            for (PlannedTreeBlock block : nearbyPlan.orderedBlocks()) {
                if (block.role() == TreeBlockRole.CANOPY
                        && TreeLeafOwnershipPolicy.neighborPlanOwnsPosition(
                                block.x(), block.z(),
                                activeDna.trunkXAt(block.y()),
                                activeDna.trunkZAt(block.y()),
                                nearbyDna.trunkXAt(block.y()),
                                nearbyDna.trunkZAt(block.y()))) {
                    protectedKeys.add(block.key());
                }
            }
        }
        return protectedKeys;
    }

    private void collectStaleCanopyLeaf(TreeCandidate candidate, TreeDna dna,
            TreeCanopyTransitionPolicy policy, Set<String> protectedCanopyKeys,
            Block leaf, Map<String, Block> staleByKey) {
        String leafKey = keyFor(leaf);
        String coordinateKey = leaf.getX() + ":" + leaf.getY() + ":" + leaf.getZ();
        boolean plannedWoodBlocker = policy.replacesWithWood(
                leaf.getX(), leaf.getY(), leaf.getZ());
        boolean nearbyCrownOwnsLeaf = protectedCanopyKeys.contains(coordinateKey)
                && !plannedWoodBlocker;
        if (!isOwnedLoaded(leaf)
                || !plugin.canEvolveAt(
                        leaf.getLocation(), "tree-evolution")
                || leaf.getType() != dna.species().leafMaterial()
                || policy.preservesLeaf(leaf.getX(), leaf.getY(), leaf.getZ())
                || nearbyCrownOwnsLeaf
                || (!plannedWoodBlocker
                        && !dna.wasOriginalShapeLeaf(leafKey))) {
            return;
        }
        staleByKey.putIfAbsent(leafKey, leaf);
    }
    private Optional<Block> findSeedlingSpot(TreeCandidate candidate, TreeDna dna) {
        if (dna.maturityStage() != TreeMaturityStage.MATURE && dna.maturityStage() != TreeMaturityStage.ANCIENT) {
            return Optional.empty();
        }
        if (dna.age() < 20 || dna.damageCount() > 4) {
            return Optional.empty();
        }
        double chance = dna.maturityStage() == TreeMaturityStage.ANCIENT ? 0.12D : 0.045D;
        if (dna.rarity() == TreeRarity.LANDMARK || dna.personality() == TreePersonality.ANCIENT_LANDMARK) {
            chance *= 1.8D;
        }
        Random random = new Random(dna.seed() ^ (dna.age() * 31L) ^ 0x5EEDL);
        if (random.nextDouble() > chance) {
            return Optional.empty();
        }

        int radius = dna.maturityStage() == TreeMaturityStage.ANCIENT ? 10 : 7;
        for (int attempt = 0; attempt < 18; attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if ((dx * dx) + (dz * dz) < 9 || (dx * dx) + (dz * dz) > radius * radius) {
                continue;
            }
            Block surface = candidate.world().getHighestBlockAt(dna.baseX() + dx, dna.baseZ() + dz);
            Block ground = surface.getRelative(BlockFace.DOWN);
            if (!surface.getType().isAir() || !NATURAL_GROUND.contains(ground.getType()) || !surface.getWorld().isChunkLoaded(surface.getX() >> 4, surface.getZ() >> 4)) {
                continue;
            }
            if (surface.getLightFromSky() < 9) {
                continue;
            }
            if (nearExistingSaplingOrLog(surface, 3)) {
                continue;
            }
            if (plugin.canEvolveAt(surface.getLocation(),
                    "tree-evolution")) {
                return Optional.of(surface);
            }
        }
        return Optional.empty();
    }

    private boolean nearExistingSaplingOrLog(Block center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Material type = center.getRelative(x, y, z).getType();
                    if (isLogOrLeaf(type) || type.name().endsWith("_SAPLING") || type == Material.MANGROVE_PROPAGULE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isNearPlannedCanopyZone(Block block, TreeDna dna) {
        int canopyCenterY = dna.baseY() + TreeSpeciesStageStyle.visibleHeight(dna);
        int verticalDistance = Math.abs(block.getY() - canopyCenterY);
        int horizontalDistance = Math.max(Math.abs(block.getX() - dna.baseX()), Math.abs(block.getZ() - dna.baseZ()));
        int verticalRadius = TreeSpeciesStageStyle.canopyRadiusY(dna) + 1;
        int horizontalRadius = Math.max(TreeSpeciesStageStyle.canopyRadiusX(dna), TreeSpeciesStageStyle.canopyRadiusZ(dna)) + 1;
        return verticalDistance <= verticalRadius && horizontalDistance <= horizontalRadius;
    }

    private Optional<Block> blockFromKey(World world, String key) {
        String[] parts = key.split(":");
        if (parts.length < 4) {
            return Optional.empty();
        }
        try {
            int x = Integer.parseInt(parts[parts.length - 3]);
            int y = Integer.parseInt(parts[parts.length - 2]);
            int z = Integer.parseInt(parts[parts.length - 1]);
            return Optional.of(world.getBlockAt(x, y, z));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private void updateMaturity(TreeCandidate candidate, TreeDna dna, TreeEvolutionConfig currentConfig) {
        int current = Math.max(candidate.height(), liveTrunkHeight(candidate.world(), dna));
        TreeMaturityStage before = dna.maturityStage();
        if (before.ordinal() >= currentConfig.maximumStage().ordinal()) {
            return;
        }
        if (currentConfig.testingStageAccelerationEnabled()) {
            if (before == TreeMaturityStage.SMALL
                    && dna.age() >= currentConfig.smallToMediumAge()
                    && current >= Math.max(4, TreeSpeciesStageStyle.visibleHeight(dna) - 1)) {
                if (advanceMaturity(candidate, dna, currentConfig, "update-maturity")) {
                    diagnostics.recordStageTransition(currentConfig, dna, before, dna.maturityStage(),
                            "testing-accelerated age=" + dna.age()
                                    + " gate=" + currentConfig.smallToMediumAge()
                                    + " height=" + current
                                    + " ## SMALL finished, tree is entering visible branch/canopy fill");
                }
                return;
            }
            if (before == TreeMaturityStage.MEDIUM
                    && dna.age() >= currentConfig.mediumToMatureAge()
                    && current >= Math.max(6, TreeSpeciesStageStyle.visibleHeight(dna) - 1)) {
                if (advanceMaturity(candidate, dna, currentConfig, "update-maturity")) {
                    diagnostics.recordStageTransition(currentConfig, dna, before, dna.maturityStage(),
                            "testing-accelerated age=" + dna.age()
                                    + " gate=" + currentConfig.mediumToMatureAge()
                                    + " height=" + current
                                    + " ## MEDIUM finished, tree is entering mature crown/branch fill");
                }
                return;
            }
            boolean ancientAllowed = currentConfig.allowAnyRarityAncient()
                    || dna.rarity() == TreeRarity.RARE
                    || dna.rarity() == TreeRarity.LANDMARK;
            if (before == TreeMaturityStage.MATURE
                    && ancientAllowed
                    && dna.age() >= currentConfig.matureToAncientAge()
                    && current >= Math.max(8, Math.round(dna.targetHeight() * 0.82F))) {
                if (advanceMaturity(candidate, dna, currentConfig, "update-maturity")) {
                    diagnostics.recordStageTransition(currentConfig, dna, before, dna.maturityStage(),
                            "testing-accelerated age=" + dna.age()
                                    + " gate=" + currentConfig.matureToAncientAge()
                                    + " rarity=" + dna.rarity()
                                    + " any-rarity=" + currentConfig.allowAnyRarityAncient()
                                    + " ## MATURE finished, tree is entering ancient/final test form");
                }
            }
            return;
        }
        if (!currentConfig.ancientStageOnHold()
                && dna.age() > currentConfig.matureToAncientAge()
                && (dna.rarity() == TreeRarity.RARE || dna.rarity() == TreeRarity.LANDMARK)) {
            while (dna.maturityStage() != TreeMaturityStage.ANCIENT) {
                if (!advanceMaturity(candidate, dna, currentConfig, "update-maturity")) {
                    break;
                }
                diagnostics.recordStageTransition(currentConfig, dna, before, dna.maturityStage(), "age=" + dna.age());
                before = dna.maturityStage();
            }
        } else if (current >= dna.targetHeight()) {
            if (advanceMaturity(candidate, dna, currentConfig, "update-maturity")) {
                diagnostics.recordStageTransition(currentConfig, dna, before, dna.maturityStage(), "height=" + current);
            }
        } else if (current >= TreeSpeciesStageStyle.visibleHeight(dna) - 1 && dna.maturityStage() == TreeMaturityStage.SMALL && dna.age() >= currentConfig.smallToMediumAge()) {
            if (advanceMaturity(candidate, dna, currentConfig, "update-maturity")) {
                diagnostics.recordStageTransition(currentConfig, dna, before, dna.maturityStage(), "stage-height=" + current);
            }
        } else if (current >= TreeSpeciesStageStyle.visibleHeight(dna) - 1 && dna.maturityStage() == TreeMaturityStage.MEDIUM && dna.age() >= currentConfig.mediumToMatureAge()) {
            if (advanceMaturity(candidate, dna, currentConfig, "update-maturity")) {
                diagnostics.recordStageTransition(currentConfig, dna, before, dna.maturityStage(), "stage-height=" + current);
            }
        }
    }

    private boolean advanceMaturity(TreeCandidate candidate, TreeDna dna, TreeEvolutionConfig currentConfig, String gate) {
        TreeMaturityStage next = dna.maturityStage().next();
        if (dna.maturityStage().ordinal() >= currentConfig.maximumStage().ordinal()
                || next.ordinal() > currentConfig.maximumStage().ordinal()) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "state.stage-hold",
                    dna.key() + " " + dna.maturityStage() + "->" + next + " blocked gate=" + gate
                            + " maximum-stage=" + currentConfig.maximumStage()
                            + " ## the configured fancy-tree ceiling prevents giant architecture from starting");
            return false;
        }
        if (!ensureOriginalShapeSnapshot(candidate, dna,
                "before-stage-" + next.name().toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        return dna.advanceMaturity();
    }

    private boolean ensureOriginalShapeSnapshot(TreeCandidate candidate,
            TreeDna dna, String reason) {
        if (dna.hasOriginalShapeSnapshot()) {
            return true;
        }
        TreeCandidate source = candidate;
        if (!source.ownershipComplete()) {
            source = buildCandidate(candidate.baseBlock(), true).orElse(null);
        }
        if (source == null || !source.ownershipComplete()) {
            diagnostics.recordReject(config, "original-shape-incomplete",
                    dna.key() + " reason=" + reason);
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "gate.original-shape-incomplete",
                    "tree=" + dna.key() + " reason=" + reason
                            + " ## evolution waits until the bounded ownership walk can save the whole source crown");
            return false;
        }
        Set<String> logs = originalLogKeys(source, dna);
        Set<String> leaves = originalLeafKeys(source, dna);
        if (logs.isEmpty() || leaves.isEmpty()) {
            diagnostics.recordReject(config, "original-shape-empty",
                    dna.key() + " reason=" + reason);
            return false;
        }
        dna.captureOriginalShape(logs, leaves);
        markTreeDnaDirty("original shape capture " + dna.key());
        saveTreeDna();
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "state.original-shape-capture",
                "tree=" + dna.key() + " blocks="
                        + (logs.size() + leaves.size())
                        + " logs=" + logs.size()
                        + " leaves=" + leaves.size()
                        + " reason=" + reason
                        + " ## exact source leaves remain authoritative until this structural stage completes");
        return true;
    }

    private Set<String> originalLogKeys(TreeCandidate candidate, TreeDna dna) {
        Set<String> logs = new HashSet<>();
        for (String blockKey : candidate.naturalKeys()) {
            blockFromKey(candidate.world(), blockKey)
                    .filter(block -> block.getType()
                            == dna.species().logMaterial())
                    .ifPresent(block -> logs.add(blockKey));
        }
        return logs;
    }

    private Set<String> originalLeafKeys(TreeCandidate candidate, TreeDna dna) {
        Set<String> leaves = new HashSet<>();
        for (String blockKey : candidate.naturalKeys()) {
            blockFromKey(candidate.world(), blockKey)
                    .filter(block -> block.getType()
                            == dna.species().leafMaterial())
                    .ifPresent(block -> leaves.add(blockKey));
        }
        return leaves;
    }

    private int liveTrunkHeight(World world, TreeDna dna) {
        int top = dna.baseY();
        int misses = 0;
        int maxY = Math.min(world.getMaxHeight() - 1, dna.baseY() + dna.targetHeight() + 16);
        for (int y = dna.baseY(); y <= maxY; y++) {
            if (hasLiveTrunkAt(world, dna, y)) {
                top = y;
                misses = 0;
            } else if (++misses >= 3) {
                break;
            }
        }
        return top - dna.baseY() + 1;
    }

    private boolean hasLiveTrunkAt(World world, TreeDna dna, int y) {
        int width = TreeSpeciesStageStyle.trunkWidthAt(dna, y);
        int radius = Math.max(1, width / 2 + 1);
        int centerX = dna.trunkXAt(y);
        int centerZ = dna.trunkZAt(y);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (world.getBlockAt(centerX + x, y, centerZ + z).getType() == dna.species().logMaterial()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isDependencyReady(TreeCandidate candidate, TreeDna dna, Block target, PlannedTreeBlock plannedBlock, TreeGrowthIntent intent, TreeEvolutionConfig currentConfig, boolean logWait) {
        return switch (plannedBlock.role()) {
            case TRUNK -> isTrunkDependencyReady(candidate, dna, target, plannedBlock, currentConfig, logWait);
            case BRANCH -> isBranchDependencyReady(candidate, dna, target, plannedBlock, currentConfig, logWait);
            case CANOPY -> isCanopyDependencyReady(candidate, dna, target, plannedBlock, currentConfig, logWait);
            case ROOT, VINE, GROUND_DETAIL, FALLEN_LOG, SAPLING -> true;
        };
    }

    private boolean isTrunkDependencyReady(TreeCandidate candidate, TreeDna dna, Block target, PlannedTreeBlock plannedBlock, TreeEvolutionConfig currentConfig, boolean logWait) {
        int liveTop = dna.baseY() + liveTrunkHeight(candidate.world(), dna) - 1;
        if (target.getY() > liveTop + 1) {
            if (logWait) {
                diagnostics.recordReject(currentConfig, "trunk-waiting-parent",
                        "role=TRUNK at " + format(target) + " live-top=" + liveTop
                                + " ## trunk spine grows one connected layer before higher tree pieces are allowed");
                plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.trunk-spine-wait",
                        plannedBlock.material() + " at " + format(target) + " live-top=" + liveTop);
            }
            return false;
        }
        return true;
    }

    private boolean isBranchDependencyReady(TreeCandidate candidate, TreeDna dna, Block target, PlannedTreeBlock plannedBlock, TreeEvolutionConfig currentConfig, boolean logWait) {
        if (!plannedBlock.hasBranchPath()) {
            return hasWoodSupportNearby(candidate, dna, target, plannedBlock, currentConfig, logWait);
        }
        Block parent = candidate.world().getBlockAt(plannedBlock.parentX(), plannedBlock.parentY(), plannedBlock.parentZ());
        if (isWoodSupport(parent.getType())) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.segment-ready",
                    "branch=" + plannedBlock.branchId() + " step=" + plannedBlock.branchStep()
                            + " parent=" + plannedBlock.parentX() + "," + plannedBlock.parentY() + "," + plannedBlock.parentZ()
                            + " target=" + format(target));
            return true;
        }
        if (logWait) {
            diagnostics.recordReject(currentConfig, "branch-waiting-parent",
                    "branch=" + plannedBlock.branchId() + " step=" + plannedBlock.branchStep()
                            + " target=" + format(target)
                            + " parent=" + plannedBlock.parentX() + "," + plannedBlock.parentY() + "," + plannedBlock.parentZ()
                            + " parent-type=" + parent.getType()
                            + " ## branch path waits for its exact trunk/branch parent instead of floating");
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.waiting-parent",
                    "branch=" + plannedBlock.branchId() + " step=" + plannedBlock.branchStep()
                            + " target=" + format(target) + " parent-type=" + parent.getType());
        }
        return false;
    }

    private Optional<PlannedTreeBlock> branchParentRepairBlock(
            TreeCandidate candidate,
            TreeDna dna,
            PlannedTreeBlock blockedBranch,
            Map<String, PlannedTreeBlock> blocksByKey,
            TreeEvolutionConfig currentConfig
    ) {
        if (blockedBranch.role() != TreeBlockRole.BRANCH || !blockedBranch.hasBranchPath()) {
            return Optional.empty();
        }
        Block parent = candidate.world().getBlockAt(blockedBranch.parentX(), blockedBranch.parentY(), blockedBranch.parentZ());
        if (isWoodSupport(parent.getType())) {
            return Optional.empty();
        }
        if (!currentConfig.isReplaceable(parent.getType()) && !isNaturalTarget(dna, parent, blockedBranch)) {
            diagnostics.recordReject(currentConfig, "branch-parent-player-block",
                    "branch=" + blockedBranch.branchId() + " step=" + blockedBranch.branchStep()
                            + " parent=" + format(parent) + " parent-type=" + parent.getType());
            return Optional.empty();
        }
        PlannedTreeBlock planned = blocksByKey.get(blockedBranch.parentKey());
        if (planned != null && (planned.role() == TreeBlockRole.TRUNK || planned.role() == TreeBlockRole.BRANCH)) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.parent-repair",
                    "branch=" + blockedBranch.branchId() + " step=" + blockedBranch.branchStep()
                            + " parent-type=" + parent.getType()
                            + " repair-role=" + planned.role()
                            + " at " + format(parent)
                            + " ## cached parent lookup repairs planned wood without rescanning the whole tree plan");
            return Optional.of(planned);
        }
        diagnostics.recordReject(currentConfig, "branch-parent-missing-plan",
                "branch=" + blockedBranch.branchId() + " step=" + blockedBranch.branchStep()
                        + " parent=" + format(parent) + " parent-type=" + parent.getType());
        return Optional.empty();
    }

    private boolean isCanopyDependencyReady(TreeCandidate candidate, TreeDna dna, Block target, PlannedTreeBlock plannedBlock, TreeEvolutionConfig currentConfig, boolean logWait) {
        if (hasTreeSupportNearby(target, 2)) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.tip-canopy",
                    "leaf=" + plannedBlock.material() + " supported-near-live-tree at " + format(target));
            return true;
        }
        if (isUpperCanopyCloudTarget(candidate, dna, target, plannedBlock)) {
            return true;
        }
        int liveTop = dna.baseY() + liveTrunkHeight(candidate.world(), dna) - 1;
        int vertical = Math.abs(target.getY() - liveTop);
        int horizontal = Math.max(Math.abs(target.getX() - dna.trunkXAt(liveTop)), Math.abs(target.getZ() - dna.trunkZAt(liveTop)));
        int softRadius = switch (dna.maturityStage()) {
            case SMALL -> 2;
            case MEDIUM -> 3;
            case MATURE -> 4;
            case ANCIENT -> dna.hugeArchitecture() ? 5 : 4;
        };
        if (vertical <= 2 && horizontal <= softRadius && liveTop >= dna.baseY() + Math.max(2, TreeSpeciesStageStyle.visibleHeight(dna) - 3)) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "gate.canopy-cloud-soft-pass",
                    plannedBlock.material() + " at " + format(target)
                            + " live-top=" + liveTop + " horizontal=" + horizontal + "/" + softRadius
                            + " ## upper crown cloud allowed to cover exposed growing trunk");
            return true;
        }
        if (logWait) {
            diagnostics.recordReject(currentConfig, "canopy-waiting-support",
                    "role=CANOPY material=" + plannedBlock.material() + " at " + format(target)
                            + " live-top=" + liveTop + " horizontal=" + horizontal + " vertical=" + vertical
                            + " ## canopy waits for nearby live wood/leaves or the active upper crown cloud");
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.canopy-waiting-support",
                    plannedBlock.material() + " delayed at " + format(target)
                            + " live-top=" + liveTop + " horizontal=" + horizontal + " vertical=" + vertical);
        }
        return false;
    }

    private boolean canPlace(TreeCandidate candidate, TreeDna dna, Block target, PlannedTreeBlock plannedBlock, TreeEvolutionConfig currentConfig) {
        if (target.getY() < target.getWorld().getMinHeight() || target.getY() >= target.getWorld().getMaxHeight()) {
            diagnostics.recordReject(currentConfig, "height-limit", format(target));
            return false;
        }
        if (!plugin.canEvolveAt(target.getLocation(), "tree-evolution")) {
            diagnostics.recordReject(currentConfig, "worldguard", format(target));
            return false;
        }
        int chunkX = target.getX() >> 4;
        int chunkZ = target.getZ() >> 4;
        if (!target.getWorld().isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(target.getWorld(), chunkX, chunkZ, currentConfig.ownedChunkRadius())) {
            diagnostics.recordReject(currentConfig, "chunk-or-region", format(target));
            return false;
        }
        if (target.isLiquid()) {
            diagnostics.recordReject(currentConfig, "liquid", format(target));
            return false;
        }
        if (plannedBlock.role() == TreeBlockRole.CANOPY && isWoodSupport(target.getType())) {
            diagnostics.recordReject(currentConfig, "canopy-occupied-by-tree-wood",
                    target.getType() + " at " + format(target)
                            + " ## live tree wood already owns this position, so canopy skips instead of blaming player blocks");
            return false;
        }
        if (!currentConfig.isReplaceable(target.getType()) && target.getType() != plannedBlock.material()) {
            if (isLowerTrunkNaturalGroundTarget(dna, target, plannedBlock)) {
                diagnostics.recordReject(currentConfig, "trunk-natural-ground-absorb",
                        target.getType() + " at " + format(target)
                                + " ## lower trunk may absorb natural ground so wide/ancient trunks finish their foundation");
            } else {
                diagnostics.recordReject(currentConfig, "player-block", target.getType() + " at " + format(target));
                return false;
            }
        }
        if (!candidate.naturalKeys().contains(keyFor(target)) && !isNaturalTarget(dna, target, plannedBlock)) {
            diagnostics.recordReject(currentConfig, "not-natural-target", target.getType() + " at " + format(target));
            return false;
        }
        if (plannedBlock.role() == TreeBlockRole.GROUND_DETAIL) {
            Material material = terrainAdjustedGroundDetail(target, plannedBlock.material());
            Block ground = target.getRelative(BlockFace.DOWN);
            if (!NATURAL_GROUND.contains(ground.getType()) || !currentConfig.isReplaceable(target.getType())) {
                return false;
            }
            if (material == Material.SUGAR_CANE && !hasAdjacentWater(ground)) {
                diagnostics.recordReject(currentConfig, "sugar-cane-water", format(target));
                return false;
            }
            if ((material == Material.PUMPKIN || material == Material.MELON) && target.getLightFromSky() < 9) {
                diagnostics.recordReject(currentConfig, "rare-feature-light", material + " at " + format(target));
                return false;
            }
            if (isRareGroundFeature(material) && countNearbyRareGroundFeatures(target, 10) >= 2) {
                diagnostics.recordReject(currentConfig, "rare-feature-density", material + " at " + format(target));
                return false;
            }
            if (countNearbyGroundDetails(target, 5) >= 18) {
                diagnostics.recordReject(currentConfig, "detail-density", format(target));
                return false;
            }
            if (isFlowerLike(material) && countNearbyFlowers(target, 6) >= 4) {
                diagnostics.recordReject(currentConfig, "flower-density", format(target));
                return false;
            }
            return true;
        }
        if (plannedBlock.role() == TreeBlockRole.SAPLING) {
            return target.getType().isAir() && NATURAL_GROUND.contains(target.getRelative(BlockFace.DOWN).getType());
        }
        if (plannedBlock.role() == TreeBlockRole.FALLEN_LOG) {
            return currentConfig.isReplaceable(target.getType()) && NATURAL_GROUND.contains(target.getRelative(BlockFace.DOWN).getType());
        }
        if (plannedBlock.role() == TreeBlockRole.VINE) {
            return target.getType().isAir() && plannedBlock.supportFace() != null
                    && isLogOrLeaf(target.getRelative(plannedBlock.supportFace()).getType());
        }
        if (plannedBlock.role() == TreeBlockRole.ROOT) {
            return NATURAL_GROUND.contains(target.getRelative(BlockFace.DOWN).getType()) || currentConfig.isReplaceable(target.getType());
        }
        if (plannedBlock.role() == TreeBlockRole.TRUNK) {
            if (!hasWoodSupportNearby(candidate, dna, target, plannedBlock, currentConfig, true)) {
                return false;
            }
            return true;
        }
        if (plannedBlock.role() == TreeBlockRole.CANOPY) {
            // ## Dependency gate already proved live tree support or active upper-crown cloud eligibility.
            // Keep canPlace focused on world/player/natural safety so the older radius check does not choke canopy fill.
            return true;
        }
        if (plannedBlock.role() == TreeBlockRole.BRANCH) {
            if (!hasWoodSupportNearby(candidate, dna, target, plannedBlock, currentConfig, true)) {
                return false;
            }
            return true;
        }
        return true;
    }

    private boolean isNaturalTarget(TreeDna dna, Block target, PlannedTreeBlock plannedBlock) {
        if (target.getType().isAir() || NATURAL_DETAILS.contains(target.getType()) || target.getType().name().endsWith("_LEAVES")) {
            return true;
        }
        if (isLowerTrunkNaturalGroundTarget(dna, target, plannedBlock)) {
            return true;
        }
        if (plannedBlock.role() == TreeBlockRole.GROUND_DETAIL) {
            return NATURAL_GROUND.contains(target.getRelative(BlockFace.DOWN).getType());
        }
        if (plannedBlock.role() == TreeBlockRole.ROOT) {
            return NATURAL_GROUND.contains(target.getRelative(BlockFace.DOWN).getType());
        }
        if (plannedBlock.role() == TreeBlockRole.SAPLING || plannedBlock.role() == TreeBlockRole.FALLEN_LOG) {
            return NATURAL_GROUND.contains(target.getRelative(BlockFace.DOWN).getType());
        }
        return false;
    }

    private boolean isLowerTrunkNaturalGroundTarget(TreeDna dna, Block target, PlannedTreeBlock plannedBlock) {
        if (plannedBlock.role() != TreeBlockRole.TRUNK || !NATURAL_GROUND.contains(target.getType())) {
            return false;
        }
        int vertical = target.getY() - dna.baseY();
        if (vertical < -1 || vertical > 2) {
            return false;
        }
        int trunkWidth = Math.max(1, TreeSpeciesStageStyle.trunkWidthAt(dna, target.getY()));
        int horizontal = Math.max(Math.abs(target.getX() - dna.trunkXAt(target.getY())), Math.abs(target.getZ() - dna.trunkZAt(target.getY())));
        return horizontal <= Math.max(1, trunkWidth / 2 + 1);
    }

    private Block targetBlockFor(World world, PlannedTreeBlock plannedBlock) {
        if (plannedBlock.role() == TreeBlockRole.GROUND_DETAIL
                || plannedBlock.role() == TreeBlockRole.FALLEN_LOG
                || plannedBlock.role() == TreeBlockRole.SAPLING) {
            return surfaceDetailTarget(world, plannedBlock.x(), plannedBlock.z());
        }
        return world.getBlockAt(plannedBlock.x(), plannedBlock.y(), plannedBlock.z());
    }

    private Block surfaceDetailTarget(World world, int x, int z) {
        Block highest = world.getHighestBlockAt(x, z);
        for (int y = highest.getY(); y > world.getMinHeight(); y--) {
            Block ground = world.getBlockAt(x, y, z);
            if (!NATURAL_GROUND.contains(ground.getType())) {
                continue;
            }
            Block target = ground.getRelative(BlockFace.UP);
            if (!target.isLiquid()) {
                return target;
            }
        }
        return highest.getType().isAir() ? highest : highest.getRelative(BlockFace.UP);
    }

    private boolean hasTreeSupportNearby(Block center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) + Math.abs(y) + Math.abs(z) > radius + 1) {
                        continue;
                    }
                    Material type = center.getRelative(x, y, z).getType();
                    if (isLogOrLeaf(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasWoodSupportNearby(TreeCandidate candidate, TreeDna dna, Block target, PlannedTreeBlock plannedBlock, TreeEvolutionConfig currentConfig, boolean logReject) {
        if (plannedBlock.role() == TreeBlockRole.TRUNK && target.getY() <= candidate.baseBlock().getY()) {
            return NATURAL_GROUND.contains(target.getRelative(BlockFace.DOWN).getType())
                    || isLogOrLeaf(target.getRelative(BlockFace.DOWN).getType());
        }
        if (plannedBlock.role() == TreeBlockRole.TRUNK) {
            boolean supported = hasDirectWoodNeighbor(target, plannedBlock.material());
            if (!supported && logReject) {
                recordSupportReject(currentConfig, plannedBlock, target, "trunk-strict", 1);
            }
            return supported;
        }

        if (hasDirectWoodNeighbor(target, plannedBlock.material())) {
            return true;
        }

        if (plannedBlock.role() == TreeBlockRole.BRANCH && isFirstBranchSegment(dna, target)) {
            if (logReject) {
                recordSupportReject(currentConfig, plannedBlock, target, "first-branch-needs-touching-wood", 1);
            }
            return false;
        }

        int radius = supportRadius(dna, plannedBlock.role());
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    int manhattan = Math.abs(x) + Math.abs(y) + Math.abs(z);
                    if (manhattan > radius + 1) {
                        continue;
                    }
                    Material material = target.getRelative(x, y, z).getType();
                    if (material == plannedBlock.material() || isWoodSupport(material)) {
                        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "gate.support-path-pass",
                                plannedBlock.role() + " " + plannedBlock.material() + " at " + format(target)
                                        + " support-offset=" + x + "," + y + "," + z);
                        return true;
                    }
                }
            }
        }
        if (logReject) {
            recordSupportReject(currentConfig, plannedBlock, target, "branch-path-too-far", radius);
        }
        return false;
    }

    private boolean hasDirectWoodNeighbor(Block target, Material plannedMaterial) {
        for (BlockFace face : NEIGHBORS) {
            Block neighbor = target.getRelative(face);
            if (neighbor.getType() == plannedMaterial || isWoodSupport(neighbor.getType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isFirstBranchSegment(TreeDna dna, Block target) {
        int y = target.getY();
        int horizontal = Math.abs(target.getX() - dna.trunkXAt(y)) + Math.abs(target.getZ() - dna.trunkZAt(y));
        return horizontal <= Math.max(1, TreeSpeciesStageStyle.trunkWidthAt(dna, y) / 2 + 1);
    }

    private int supportRadius(TreeDna dna, TreeBlockRole role) {
        if (role == TreeBlockRole.BRANCH) {
            return switch (dna.maturityStage()) {
                case SMALL, MEDIUM -> 3;
                case MATURE -> 3;
                case ANCIENT -> 4;
            };
        }
        return WOOD_SUPPORT_RADIUS;
    }

    private int canopySupportRadius(TreeDna dna) {
        return switch (dna.maturityStage()) {
            case SMALL, MEDIUM -> 4;
            case MATURE -> 3;
            case ANCIENT -> 4;
        };
    }

    private boolean isUpperCanopyCloudTarget(TreeCandidate candidate, TreeDna dna, Block target, PlannedTreeBlock plannedBlock) {
        if (plannedBlock.role() != TreeBlockRole.CANOPY) {
            return false;
        }
        int visibleHeight = TreeSpeciesStageStyle.visibleHeight(dna);
        int topY = dna.baseY() + visibleHeight - 1;
        int liveHeight = Math.max(candidate.height(), liveTrunkHeight(candidate.world(), dna));
        int liveTop = dna.baseY() + liveTrunkHeight(candidate.world(), dna) - 1;
        if (liveHeight >= Math.max(4, (int) Math.round(visibleHeight * 0.62D))) {
            int liveHorizontal = Math.max(Math.abs(target.getX() - dna.trunkXAt(Math.min(topY, liveTop))), Math.abs(target.getZ() - dna.trunkZAt(Math.min(topY, liveTop))));
            int liveHorizontalLimit = Math.max(2, Math.min(Math.max(TreeSpeciesStageStyle.canopyRadiusX(dna), TreeSpeciesStageStyle.canopyRadiusZ(dna)), canopySupportRadius(dna)));
            int liveVertical = Math.abs(target.getY() - liveTop);
            if (liveHorizontal <= liveHorizontalLimit && liveVertical <= 2) {
                plugin.pathDebug().traceSampled(plugin, "tree-evolution", "gate.canopy-live-cloud-pass",
                        plannedBlock.material() + " at " + format(target)
                                + " live-height=" + liveHeight + " visible=" + visibleHeight
                                + " horizontal=" + liveHorizontal + "/" + liveHorizontalLimit
                                + " ## active crown cloud catches leaves up around the current top instead of waiting for final height");
                return true;
            }
        }
        if (liveHeight < Math.max(3, visibleHeight - 2)) {
            return false;
        }
        int horizontal = Math.max(Math.abs(target.getX() - dna.trunkXAt(topY)), Math.abs(target.getZ() - dna.trunkZAt(topY)));
        int horizontalLimit = Math.max(TreeSpeciesStageStyle.canopyRadiusX(dna), TreeSpeciesStageStyle.canopyRadiusZ(dna)) + 1;
        int vertical = Math.abs(target.getY() - topY);
        int verticalLimit = TreeSpeciesStageStyle.canopyRadiusY(dna) + 2;
        if (horizontal <= horizontalLimit && vertical <= verticalLimit) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "gate.canopy-cloud-soft-pass",
                    plannedBlock.material() + " at " + format(target)
                            + " live-height=" + liveHeight + " visible=" + visibleHeight
                            + " horizontal=" + horizontal + "/" + horizontalLimit);
            return true;
        }
        return false;
    }

    private void recordSupportReject(TreeEvolutionConfig currentConfig, PlannedTreeBlock plannedBlock, Block target, String reason, int radius) {
        diagnostics.recordReject(currentConfig, "support-too-strict",
                "role=" + plannedBlock.role() + " material=" + plannedBlock.material() + " at " + format(target)
                        + " reason=" + reason + " radius=" + radius);
        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "gate.support-too-strict",
                plannedBlock.role() + " " + plannedBlock.material() + " delayed at " + format(target)
                        + " reason=" + reason + " radius=" + radius);
    }

    private boolean isWoodSupport(Material material) {
        String name = material.name();
        return name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE")
                || material == Material.MUSHROOM_STEM;
    }

    private Optional<Block> findExposedUpperLog(TreeCandidate candidate, TreeDna dna) {
        World world = candidate.world();
        int liveHeight = liveTrunkHeight(world, dna);
        int liveTop = dna.baseY() + liveHeight - 1;
        int startY = Math.max(dna.baseY(), liveTop - 3);
        for (int y = liveTop; y >= startY; y--) {
            int width = TreeSpeciesStageStyle.trunkWidthAt(dna, y);
            int radius = Math.max(0, width / 2);
            int centerX = dna.trunkXAt(y);
            int centerZ = dna.trunkZAt(y);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(centerX + x, y, centerZ + z);
                    if (block.getType() == dna.species().logMaterial() && !hasAdjacentLeaf(block, dna.species().leafMaterial())) {
                        return Optional.of(block);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private int maybeCoverExposedTopLog(TreeCandidate candidate, TreeDna dna, TreeEvolutionConfig currentConfig, Block trunk, PlannedTreeBlock plannedBlock) {
        if (plannedBlock.role() != TreeBlockRole.TRUNK || trunk.getY() < candidate.topY()) {
            return 0;
        }
        return coverExposedLog(candidate, dna, currentConfig, trunk);
    }

    private int coverExposedLog(TreeCandidate candidate, TreeDna dna, TreeEvolutionConfig currentConfig, Block trunk) {
        if (hasAdjacentLeaf(trunk, dna.species().leafMaterial())) {
            return 0;
        }

        int desiredLeaves = switch (dna.maturityStage()) {
            case SMALL -> 4;
            case MEDIUM -> 4;
            case MATURE -> dna.hugeArchitecture() ? 6 : 5;
            case ANCIENT -> dna.hugeArchitecture() ? 8 : 6;
        };
        int placed = 0;
        List<BlockFace> faces = List.of(BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
        int offset = Math.floorMod((trunk.getX() * 31) ^ (trunk.getY() * 17) ^ (trunk.getZ() * 13), faces.size());
        for (int index = 0; index < faces.size() && placed < desiredLeaves; index++) {
            BlockFace face = faces.get((index + offset) % faces.size());
            Block leaf = trunk.getRelative(face);
            if (!canPlaceCanopyLiftLeaf(leaf, trunk, dna, currentConfig)) {
                continue;
            }
            placePersistentLeaf(leaf, dna.species().leafMaterial());
            placed++;
            changedBlocks.incrementAndGet();
        }
        if (placed > 0) {
            dna.setCurrentIntent(TreeGrowthIntent.CANOPY);
            diagnostics.recordCanopyLift(plugin, currentConfig, trunk, dna, placed);
            plugin.pathDebug().trace(plugin, "tree-evolution", "canopy.lift-cover",
                    "trunk=" + format(trunk) + " leaf=" + dna.species().leafMaterial() + " placed=" + placed
                            + " ## canopy shell grows around exposed live support before the tree keeps expanding");
        } else {
            diagnostics.recordReject(currentConfig, "canopy-lift-space",
                    "exposed trunk=" + format(trunk) + " no safe adjacent leaf space");
        }
        return placed;
    }

    private boolean canPlaceCanopyLiftLeaf(Block leaf, Block trunk, TreeDna dna, TreeEvolutionConfig currentConfig) {
        int chunkX = leaf.getX() >> 4;
        int chunkZ = leaf.getZ() >> 4;
        if (!leaf.getWorld().isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(leaf.getWorld(), chunkX, chunkZ, currentConfig.ownedChunkRadius())) {
            return false;
        }
        if (!plugin.canEvolveAt(leaf.getLocation(), "tree-evolution")) {
            return false;
        }
        if (leaf.isLiquid() || (!currentConfig.isReplaceable(leaf.getType()) && leaf.getType() != dna.species().leafMaterial())) {
            return false;
        }
        return touchesBlock(leaf, trunk) || hasDirectWoodNeighbor(leaf);
    }

    private boolean hasAdjacentLeaf(Block block, Material leafMaterial) {
        for (BlockFace face : NEIGHBORS) {
            Material type = block.getRelative(face).getType();
            if (type == leafMaterial || type.name().endsWith("_LEAVES")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDirectWoodNeighbor(Block block) {
        for (BlockFace face : NEIGHBORS) {
            if (isWoodSupport(block.getRelative(face).getType())) {
                return true;
            }
        }
        return false;
    }

    private boolean touchesBlock(Block first, Block second) {
        return first.getWorld().equals(second.getWorld())
                && Math.abs(first.getX() - second.getX()) + Math.abs(first.getY() - second.getY()) + Math.abs(first.getZ() - second.getZ()) == 1;
    }

    private void placePersistentLeaf(Block leaf, Material leafMaterial) {
        leaf.setType(leafMaterial, false);
        if (leaf.getBlockData() instanceof Leaves leaves) {
            leaves.setPersistent(true);
            leaf.setBlockData(leaves, false);
        }
    }

    private void place(Block target, PlannedTreeBlock plannedBlock) {
        Material material = plannedBlock.role() == TreeBlockRole.GROUND_DETAIL
                ? terrainAdjustedGroundDetail(target, plannedBlock.material())
                : plannedBlock.material();
        target.setType(material, false);
        BlockData data = target.getBlockData();
        if (data instanceof Orientable orientable) {
            orientable.setAxis(plannedBlock.axis() == null ? Axis.Y : plannedBlock.axis());
            target.setBlockData(orientable, false);
        } else if (data instanceof Leaves leaves) {
            leaves.setPersistent(true);
            target.setBlockData(leaves, false);
        } else if (data instanceof MultipleFacing facing && plannedBlock.supportFace() != null && facing.getAllowedFaces().contains(plannedBlock.supportFace())) {
            facing.setFace(plannedBlock.supportFace(), true);
            target.setBlockData(facing, false);
        }
    }

    private Material terrainAdjustedGroundDetail(Block target, Material planned) {
        Block ground = target.getRelative(BlockFace.DOWN);
        String biome = target.getBiome().getKey().getKey();
        if (isWetPocket(target)) {
            if (biome.contains("swamp")) {
                return planned == Material.LEAF_LITTER ? Material.BROWN_MUSHROOM : Material.BLUE_ORCHID;
            }
            if (planned == Material.DEAD_BUSH || planned == Material.PUMPKIN || planned == Material.MELON) {
                return stableChoice(target, Material.FERN, Material.SHORT_GRASS);
            }
            if (isFlowerLike(planned)) {
                return stableChoice(target, Material.FERN, Material.SHORT_GRASS);
            }
        }
        if (planned == Material.SUGAR_CANE && !hasAdjacentWater(ground)) {
            return stableChoice(target, Material.FERN, Material.SHORT_GRASS, Material.BLUE_ORCHID);
        }
        if ((planned == Material.PUMPKIN || planned == Material.MELON) && target.getLightFromSky() < 9) {
            return stableChoice(target, Material.SHORT_GRASS, Material.FERN, Material.MOSS_CARPET);
        }
        if (isSlopedPocket(ground) && isFlowerLike(planned)) {
            return stableChoice(target, Material.SHORT_GRASS, Material.FERN, Material.LEAF_LITTER);
        }
        if (target.getLightFromSky() < 7 && (isFlowerLike(planned) || planned == Material.SHORT_GRASS)) {
            return stableChoice(target, Material.LEAF_LITTER, Material.FERN, Material.BROWN_MUSHROOM);
        }
        if (planned == Material.LEAF_LITTER && countNearbyMaterial(target, Material.LEAF_LITTER, 4) >= 5) {
            return stableChoice(target, Material.MOSS_CARPET, Material.FERN, Material.SHORT_GRASS);
        }
        if (isFlowerLike(planned) && countNearbyFlowers(target, 5) >= 3) {
            return stableChoice(target, Material.SHORT_GRASS, Material.FERN, Material.MOSS_CARPET);
        }
        if (biome.contains("taiga") || biome.contains("old_growth")) {
            return planned == Material.DANDELION || planned == Material.POPPY ? Material.FERN : planned;
        }
        return planned;
    }

    private int countNearbyGroundDetails(Block center, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (isGroundDetail(center.getRelative(dx, dy, dz).getType())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int countNearbyRareGroundFeatures(Block center, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (isRareGroundFeature(center.getRelative(dx, dy, dz).getType())) {
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
                if (isFlowerLike(center.getRelative(dx, 0, dz).getType())) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countNearbyMaterial(Block center, Material material, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (center.getRelative(dx, dy, dz).getType() == material) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private boolean isGroundDetail(Material material) {
        return NATURAL_DETAILS.contains(material)
                || material == Material.MOSS_CARPET
                || material == Material.BROWN_MUSHROOM
                || material == Material.RED_MUSHROOM
                || isRareGroundFeature(material);
    }

    private boolean isRareGroundFeature(Material material) {
        return material == Material.PUMPKIN
                || material == Material.MELON
                || material == Material.SWEET_BERRY_BUSH
                || material == Material.SUGAR_CANE
                || material == Material.DEAD_BUSH;
    }

    private boolean hasAdjacentWater(Block ground) {
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            if (ground.getRelative(face).isLiquid() || ground.getRelative(face).getRelative(BlockFace.UP).isLiquid()) {
                return true;
            }
        }
        return false;
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
            if (!NATURAL_GROUND.contains(neighbor.getType()) && !NATURAL_GROUND.contains(neighbor.getRelative(BlockFace.DOWN).getType())) {
                uneven++;
            }
        }
        return uneven >= 2;
    }

    private boolean isFlowerLike(Material material) {
        return material == Material.DANDELION
                || material == Material.POPPY
                || material == Material.BLUE_ORCHID
                || material == Material.ALLIUM
                || material == Material.AZURE_BLUET
                || material == Material.OXEYE_DAISY
                || material == Material.CORNFLOWER
                || material == Material.LILY_OF_THE_VALLEY
                || material == Material.PINK_PETALS;
    }

    private Material stableChoice(Block block, Material... materials) {
        int hash = (block.getX() * 73428767) ^ (block.getZ() * 912931) ^ (block.getY() * 19349663);
        return materials[Math.floorMod(hash, materials.length)];
    }

    private Optional<TreeCandidate> findCandidate(Location origin, TreeEvolutionConfig currentConfig) {
        try (ReportSample sample = plugin.resourceReporter().begin("tree-evolution", "search.random-candidate")) {
            World world = origin.getWorld();
            if (world == null) {
                sample.detail("missing-world");
                return Optional.empty();
            }
            diagnostics.recordSearch();
            int radius = currentConfig.searchRadius();
            Random random = new Random();
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
                diagnostics.recordReject(currentConfig, "candidate-chunk-region", chunkX + "," + chunkZ);
                plugin.pathDebug().failure(plugin, "tree-evolution", "chunk-or-region-gate", "candidate " + chunkX + "," + chunkZ);
                sample.detail("chunk-or-region");
                return Optional.empty();
            }
            Block highest = world.getHighestBlockAt(x, z);
            int minY = Math.max(world.getMinHeight(), highest.getY() - 32);
            int scanned = 0;
            for (int y = highest.getY(); y >= minY; y--) {
                scanned++;
                Optional<TreeCandidate> candidate = buildCandidate(world.getBlockAt(x, y, z));
                if (candidate.isPresent() && isNearPlayer(candidate.get().baseLocation(), currentConfig.requiredPlayerDistanceChunks())) {
                    diagnostics.recordCandidate(currentConfig, candidate.get());
                    sample.workUnits(scanned).changedUnits(1).detail(candidate.get().species() + " " + candidate.get().baseKey());
                    return candidate;
                }
            }
            sample.workUnits(scanned).detail("not-found");
            return Optional.empty();
        }
    }

    private Optional<TreeCandidate> findNearestCandidate(Location origin, int radius) {
        try (ReportSample sample = plugin.resourceReporter().begin("tree-evolution", "search.nearest-candidate")) {
            World world = origin.getWorld();
            if (world == null) {
                sample.detail("missing-world");
                return Optional.empty();
            }
            String cacheKey = nearestCandidateCacheKey(origin, radius);
            CachedTreeCandidate cached = nearestCandidateCache.get(cacheKey);
            long now = System.currentTimeMillis();
            if (cached != null && now < cached.expiresMillis()) {
                TreeCandidate candidate = cached.candidate();
                if (candidate == null) {
                    sample.detail("cache-miss-hit radius=" + radius);
                    plugin.pathDebug().traceSampled(plugin, "tree-evolution", "cache.nearest-candidate-empty", cacheKey);
                    return Optional.empty();
                }
                if (isCandidateStillValid(candidate)) {
                    sample.changedUnits(1).detail("cache-hit " + candidate.species() + " " + candidate.baseKey());
                    plugin.pathDebug().traceSampled(plugin, "tree-evolution", "cache.nearest-candidate-hit", candidate.baseKey());
                    return Optional.of(candidate);
                }
                nearestCandidateCache.remove(cacheKey, cached);
            }
            int scanned = 0;
            for (int y = origin.getBlockY() + 8; y >= origin.getBlockY() - 8; y--) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        scanned++;
                        Optional<TreeCandidate> candidate = buildCandidate(world.getBlockAt(origin.getBlockX() + dx, y, origin.getBlockZ() + dz));
                        if (candidate.isPresent()) {
                            nearestCandidateCache.put(cacheKey, new CachedTreeCandidate(candidate.get(), now + config.candidateCacheMillis()));
                            sample.workUnits(scanned).changedUnits(1).detail(candidate.get().species() + " " + candidate.get().baseKey());
                            return candidate;
                        }
                    }
                }
            }
            nearestCandidateCache.put(cacheKey, new CachedTreeCandidate(null, now + config.candidateCacheMillis()));
            sample.workUnits(scanned).detail("not-found radius=" + radius);
            return Optional.empty();
        }
    }

    private List<TreeCandidate> findKnownCandidatesNear(Location origin, TreeEvolutionConfig currentConfig, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        World world = origin.getWorld();
        if (world == null) {
            return List.of();
        }
        String cacheKey = knownCandidateCacheKey(origin, currentConfig.searchRadius(), limit);
        long now = System.currentTimeMillis();
        CachedKnownCandidates cached = knownCandidateCache.get(cacheKey);
        if (cached != null && now < cached.expiresMillis()) {
            return cached.candidates().stream()
                    .filter(this::isCandidateStillValid)
                    .limit(limit)
                    .toList();
        }

        int radius = currentConfig.searchRadius();
        int radiusSquared = radius * radius;
        List<TreeDna> nearbyDna = new ArrayList<>();
        List<TreeCandidate> candidates = new ArrayList<>();
        for (TreeDna dna : treeDna.values()) {
            if (!world.getUID().equals(dna.worldId()) || !dna.stumpPresent()) {
                continue;
            }
            int dx = dna.baseX() - origin.getBlockX();
            int dz = dna.baseZ() - origin.getBlockZ();
            if ((dx * dx) + (dz * dz) > radiusSquared) {
                continue;
            }
            int chunkX = dna.baseX() >> 4;
            int chunkZ = dna.baseZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                continue;
            }
            nearbyDna.add(dna);
        }
        nearbyDna.sort(Comparator
                .comparingLong(TreeDna::lastGrowthMillis)
                .thenComparingInt(dna -> Math.abs(dna.baseX() - origin.getBlockX()) + Math.abs(dna.baseZ() - origin.getBlockZ())));
        for (TreeDna dna : nearbyDna) {
            if (candidates.size() >= limit) {
                break;
            }
            buildKnownCandidateFromDna(world, dna, currentConfig)
                    .filter(candidate -> isNearPlayer(candidate.baseLocation(), currentConfig.requiredPlayerDistanceChunks()))
                    .ifPresent(candidates::add);
        }
        List<TreeCandidate> limited = candidates.stream().limit(limit).toList();
        long ttl = Math.max(250L, Math.min(1500L, currentConfig.candidateCacheMillis()));
        knownCandidateCache.put(cacheKey, new CachedKnownCandidates(limited, now + ttl));
        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "cache.known-candidates-refresh",
                "nearby-dna=" + nearbyDna.size() + " built=" + candidates.size() + " used=" + limited.size() + " radius=" + radius);
        return limited;
    }

    private boolean isCandidateStillValid(TreeCandidate candidate) {
        World world = candidate.world();
        int chunkX = candidate.baseX() >> 4;
        int chunkZ = candidate.baseZ() >> 4;
        return world != null
                && world.isChunkLoaded(chunkX, chunkZ)
                && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)
                && candidate.baseBlock().getType() == candidate.species().logMaterial();
    }

    private Optional<TreeCandidate> buildKnownCandidateFromDna(World world, TreeDna dna, TreeEvolutionConfig currentConfig) {
        int chunkX = dna.baseX() >> 4;
        int chunkZ = dna.baseZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            diagnostics.recordReject(currentConfig, "known-dna-chunk-region", dna.key());
            return Optional.empty();
        }
        Block base = world.getBlockAt(dna.baseX(), dna.baseY(), dna.baseZ());
        if (base.getType() != dna.species().logMaterial()) {
            diagnostics.recordReject(currentConfig, "known-dna-base-not-log", base.getType() + " at " + format(base));
            return Optional.empty();
        }

        Set<String> naturalKeys = new HashSet<>();
        int topY = dna.baseY();
        int logs = 0;
        int misses = 0;
        int maxY = Math.min(world.getMaxHeight() - 1, dna.baseY() + Math.max(6, TreeSpeciesStageStyle.visibleHeight(dna) + 4));
        for (int y = dna.baseY(); y <= maxY; y++) {
            int found = countKnownTrunkBlocksAt(world, dna, y, naturalKeys);
            if (found > 0) {
                logs += found;
                topY = y;
                misses = 0;
            } else if (++misses >= 3) {
                break;
            }
        }

        CanopySample canopy = sampleKnownCanopy(world, dna, topY, naturalKeys);
        int height = Math.max(1, topY - dna.baseY() + 1);
        int connectedLogs = Math.max(height, logs + canopy.logs());
        int connectedLeaves = Math.max(canopy.leaves(), 2);
        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "cache.known-dna-candidate",
                dna.species().id() + " base=" + format(base)
                        + " height=" + height
                        + " logs=" + connectedLogs
                        + " leaves=" + connectedLeaves
                        + " keys=" + naturalKeys.size()
                        + " ## known DNA candidate used compact validation instead of full tree flood-fill");
        return Optional.of(new TreeCandidate(
                world,
                dna.baseX(),
                dna.baseY(),
                dna.baseZ(),
                topY,
                height,
                dna.species(),
                connectedLogs,
                connectedLeaves,
                naturalKeys,
                false
        ));
    }

    private int countKnownTrunkBlocksAt(World world, TreeDna dna, int y, Set<String> naturalKeys) {
        int width = TreeSpeciesStageStyle.trunkWidthAt(dna, y);
        int radius = Math.max(0, Math.min(3, width / 2 + 1));
        int centerX = dna.trunkXAt(y);
        int centerZ = dna.trunkZAt(y);
        int found = 0;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                if (!isOwnedLoaded(world, x, z)) {
                    continue;
                }
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() == dna.species().logMaterial()) {
                    found++;
                    naturalKeys.add(keyFor(block));
                }
            }
        }
        return found;
    }

    private CanopySample sampleKnownCanopy(World world, TreeDna dna, int topY, Set<String> naturalKeys) {
        int radiusX = Math.max(2, Math.min(5, TreeSpeciesStageStyle.canopyRadiusX(dna) + 1));
        int radiusZ = Math.max(2, Math.min(5, TreeSpeciesStageStyle.canopyRadiusZ(dna) + 1));
        int radiusY = Math.max(1, Math.min(3, TreeSpeciesStageStyle.canopyRadiusY(dna) + 1));
        int centerX = dna.trunkXAt(topY);
        int centerZ = dna.trunkZAt(topY);
        int leaves = 0;
        int logs = 0;
        for (int y = Math.max(world.getMinHeight(), topY - radiusY); y <= Math.min(world.getMaxHeight() - 1, topY + radiusY); y++) {
            for (int x = centerX - radiusX; x <= centerX + radiusX; x++) {
                for (int z = centerZ - radiusZ; z <= centerZ + radiusZ; z++) {
                    if (!isOwnedLoaded(world, x, z)) {
                        continue;
                    }
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type == dna.species().leafMaterial()) {
                        leaves++;
                        naturalKeys.add(keyFor(block));
                    } else if (type == dna.species().logMaterial()) {
                        logs++;
                        naturalKeys.add(keyFor(block));
                    }
                }
            }
        }
        return new CanopySample(leaves, logs);
    }

    private String nearestCandidateCacheKey(Location origin, int radius) {
        World world = origin.getWorld();
        String worldKey = world == null ? "unknown" : world.getUID().toString();
        return worldKey + ":" + (origin.getBlockX() >> 4) + ":" + (origin.getBlockZ() >> 4) + ":" + radius;
    }

    private String knownCandidateCacheKey(Location origin, int radius, int limit) {
        World world = origin.getWorld();
        String worldKey = world == null ? "unknown" : world.getUID().toString();
        return worldKey + ":" + (origin.getBlockX() >> 4) + ":" + (origin.getBlockZ() >> 4) + ":" + radius + ":" + limit;
    }

    private Optional<TreeCandidate> buildCandidate(Block start) {
        return buildCandidate(start, false);
    }

    private Optional<TreeCandidate> buildCandidate(Block start,
            boolean thoroughOwnershipScan) {
        Optional<TreeSpecies> species = TreeSpecies.fromMaterial(start.getType());
        if (species.isEmpty() || start.getType() != species.get().logMaterial()) {
            return Optional.empty();
        }
        Block base = start;
        while (base.getY() > base.getWorld().getMinHeight() && base.getRelative(BlockFace.DOWN).getType() == species.get().logMaterial()) {
            base = base.getRelative(BlockFace.DOWN);
        }
        Block top = start;
        while (top.getY() < top.getWorld().getMaxHeight() - 1 && top.getRelative(BlockFace.UP).getType() == species.get().logMaterial()) {
            top = top.getRelative(BlockFace.UP);
        }
        int height = top.getY() - base.getY() + 1;
        if (height < 2 || height > 128 || !NATURAL_GROUND.contains(base.getRelative(BlockFace.DOWN).getType())) {
            return Optional.empty();
        }

        TreeGroup group = collectTreeGroup(base, species.get(), thoroughOwnershipScan);
        if (group.leaves() < 2) {
            return Optional.empty();
        }
        return Optional.of(new TreeCandidate(
                base.getWorld(),
                base.getX(),
                base.getY(),
                base.getZ(),
                top.getY(),
                height,
                species.get(),
                group.logs(),
                group.leaves(),
                group.keys(),
                group.ownershipComplete()
        ));
    }

    private TreeGroup collectTreeGroup(Block base, TreeSpecies species,
            boolean thoroughOwnershipScan) {
        Queue<Block> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        Set<String> queued = new HashSet<>();
        Set<TreeLeafOwnershipPolicy.Column> foreignTrunks = new HashSet<>();
        Map<String, Optional<TreeLeafOwnershipPolicy.Column>> rootedColumns =
                new HashMap<>();
        long started = System.nanoTime();
        int maxVisited = thoroughOwnershipScan
                ? TREE_GROUP_MAX_VISITED * 2 : TREE_GROUP_MAX_VISITED;
        int maxQueued = thoroughOwnershipScan
                ? TREE_GROUP_MAX_QUEUED * 2 : TREE_GROUP_MAX_QUEUED;
        long maxNanos = thoroughOwnershipScan
                ? TREE_GROUP_MAX_NANOS * 2L : TREE_GROUP_MAX_NANOS;
        queue.add(base);
        queued.add(keyFor(base));
        while (!queue.isEmpty()
                && visited.size() < maxVisited
                && queued.size() < maxQueued
                && System.nanoTime() - started < maxNanos) {
            Block block = queue.poll();
            String key = keyFor(block);
            if (!visited.add(key)) {
                continue;
            }
            Material type = block.getType();
            if (type == species.logMaterial()) {
                String columnKey = block.getX() + ":" + block.getZ();
                Optional<TreeLeafOwnershipPolicy.Column> rooted =
                        rootedColumns.computeIfAbsent(columnKey,
                                ignored -> rootedTreeColumn(block, species));
                if (rooted.isPresent()
                        && !TreeLeafOwnershipPolicy.isActiveTrunkColumn(
                                species, base.getX(), base.getZ(), rooted.get())) {
                    foreignTrunks.add(rooted.get());
                    continue;
                }
            } else if (type != species.leafMaterial()
                    && !type.name().endsWith("_LEAVES")
                    && type != Material.VINE
                    && !NATURAL_DETAILS.contains(type)) {
                continue;
            }
            for (BlockFace face : NEIGHBORS) {
                Block relative = block.getRelative(face);
                int distance = Math.abs(relative.getX() - base.getX())
                        + Math.abs(relative.getY() - base.getY())
                        + Math.abs(relative.getZ() - base.getZ());
                if (distance <= TREE_GROUP_MAX_DISTANCE
                        && relative.getY() >= base.getWorld().getMinHeight()
                        && relative.getY() < base.getWorld().getMaxHeight()
                        && isOwnedLoaded(relative)
                        && isLogOrLeaf(relative.getType())) {
                    String relativeKey = keyFor(relative);
                    if (!visited.contains(relativeKey)
                            && queued.add(relativeKey)) {
                        queue.add(relative);
                    }
                }
            }
        }
        if (!queue.isEmpty()) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "gate.tree-group-budget",
                    "base=" + format(base)
                            + " visited=" + visited.size()
                            + " queued=" + queued.size()
                            + " remaining=" + queue.size()
                            + " nanos=" + (System.nanoTime() - started)
                            + " thorough=" + thoroughOwnershipScan
                            + " ## candidate traversal stopped early to protect Folia region tick");
        }

        Set<String> ownedKeys = new HashSet<>();
        int logs = 0;
        int leaves = 0;
        for (String key : visited) {
            Optional<Block> found = blockFromKey(base.getWorld(), key);
            if (found.isEmpty()) {
                continue;
            }
            Block block = found.get();
            if (!TreeLeafOwnershipPolicy.belongsToActiveTree(
                    block.getX(), block.getZ(), base.getX(), base.getZ(),
                    foreignTrunks)) {
                continue;
            }
            ownedKeys.add(key);
            Material type = block.getType();
            if (type == species.logMaterial()) {
                logs++;
            } else if (type == species.leafMaterial()
                    || type.name().endsWith("_LEAVES")) {
                leaves++;
            }
        }
        if (!foreignTrunks.isEmpty()) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "candidate.touching-crowns-separated",
                    "base=" + format(base)
                            + " foreign-trunks=" + foreignTrunks.size()
                            + " connected=" + visited.size()
                            + " owned=" + ownedKeys.size()
                            + " ## touching leaves remain valid while rooted neighboring trees keep separate ownership");
        }
        return new TreeGroup(logs, leaves, ownedKeys, queue.isEmpty());
    }

    private Optional<TreeLeafOwnershipPolicy.Column> rootedTreeColumn(
            Block block, TreeSpecies species) {
        Block bottom = block;
        int descent = 0;
        while (descent++ < 128
                && bottom.getY() > bottom.getWorld().getMinHeight()
                && bottom.getRelative(BlockFace.DOWN).getType()
                        == species.logMaterial()) {
            bottom = bottom.getRelative(BlockFace.DOWN);
        }
        if (!NATURAL_GROUND.contains(
                bottom.getRelative(BlockFace.DOWN).getType())) {
            return Optional.empty();
        }
        int verticalRun = 0;
        Block cursor = bottom;
        while (verticalRun < 4
                && cursor.getType() == species.logMaterial()) {
            verticalRun++;
            cursor = cursor.getRelative(BlockFace.UP);
        }
        if (verticalRun < 2) {
            return Optional.empty();
        }
        return Optional.of(new TreeLeafOwnershipPolicy.Column(
                bottom.getX(), bottom.getZ()));
    }
    private boolean isOwnedLoaded(Block block) {
        return isOwnedLoaded(block.getWorld(), block.getX(), block.getZ());
    }

    private boolean isOwnedLoaded(World world, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        return world.isChunkLoaded(chunkX, chunkZ) && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0);
    }

    private TreeDna dnaFor(TreeCandidate candidate) {
        TreeDna existing = treeDna.get(candidate.baseKey());
        if (existing == null) {
            existing = findNearbyExistingDna(candidate).orElse(null);
        }
        if (existing != null) {
            knownTrees.set(treeDna.size());
            return existing;
        }
        TreeProfileSample sample = chooseProfileSample(candidate);
        TreeGrowthProfile profile = sample == null ? config.profile(candidate.species()) : sample.profile();
        TreeDna parent = findParentDna(candidate).orElse(null);
        TreeDna rawCreated = TreeDna.create(
                candidate.world(),
                candidate,
                profile,
                sample,
                parent == null ? "wild" : parent.key(),
                parent == null ? 0 : parent.generation() + 1
        );
        TreeDnaNormalizer.NormalizedDna creationNormalization = dnaNormalizer.normalize(rawCreated, config.maximumStage());
        TreeDna created = creationNormalization.dna();
        if (candidate.ownershipComplete()) {
            created.captureOriginalShape(
                    originalLogKeys(candidate, created),
                    originalLeafKeys(candidate, created));
        }
        if (creationNormalization.changed()) {
            diagnostics.recordDnaNormalized(config, rawCreated, created, creationNormalization.summary());
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "state.dna-create-normalize",
                    created.key() + " " + creationNormalization.summary()
                            + " ## newly discovered tree was capped at the fancy mature profile");
        }
        TreeDna previous = treeDna.putIfAbsent(created.key(), created);
        TreeDna result = previous == null ? created : previous;
        if (previous == null) {
            markTreeDnaDirty("dna-create");
            diagnostics.recordDnaCreated(config, created);
            plugin.pathDebug().trace(plugin, "tree-evolution", "state.dna-create",
                    candidate.species().id() + " at " + format(candidate.baseLocation())
                            + " sample=" + created.profileSampleId()
                            + " personality=" + created.personality()
                            + " rarity=" + created.rarity()
                            + " parent=" + created.parentKey()
                            + " original-blocks="
                            + created.originalShapeBlockCount()
                            + " original-logs="
                            + created.originalShapeLogCount()
                            + " original-leaves="
                            + created.originalShapeLeafCount());
            saveTreeDna();
        }
        knownTrees.set(treeDna.size());
        return result;
    }

    private Optional<TreeDna> findNearbyExistingDna(TreeCandidate candidate) {
        TreeDna best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (TreeDna dna : treeDna.values()) {
            if (!dna.worldId().equals(candidate.world().getUID()) || dna.species() != candidate.species()) {
                continue;
            }
            int vertical = Math.abs(dna.baseY() - candidate.baseY());
            int horizontal = Math.max(Math.abs(dna.baseX() - candidate.baseX()), Math.abs(dna.baseZ() - candidate.baseZ()));
            int allowed = Math.max(2, dna.trunkWidth() + 1);
            int distance = horizontal + vertical;
            if (vertical <= 3 && horizontal <= allowed && distance < bestDistance) {
                best = dna;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private Optional<TreeDna> findParentDna(TreeCandidate candidate) {
        TreeDna best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (TreeDna dna : treeDna.values()) {
            if (dna.species() != candidate.species() || dna.maturityStage() == TreeMaturityStage.SMALL) {
                continue;
            }
            int distance = Math.abs(dna.baseX() - candidate.baseX()) + Math.abs(dna.baseZ() - candidate.baseZ());
            if (distance > 42 || distance < 4 || distance >= bestDistance) {
                continue;
            }
            best = dna;
            bestDistance = distance;
        }
        return Optional.ofNullable(best);
    }

    private TreeProfileSample chooseProfileSample(TreeCandidate candidate) {
        List<TreeProfileSample> samples = profileSamples.getOrDefault(candidate.species(), List.of());
        if (samples.isEmpty()) {
            return null;
        }
        List<TreeProfileSample> filtered = filteredProfileSamples(candidate, samples);
        int index = Math.floorMod(candidate.baseKey().hashCode(), filtered.size());
        return filtered.get(index);
    }

    private List<TreeProfileSample> filteredProfileSamples(TreeCandidate candidate, List<TreeProfileSample> samples) {
        List<TreeProfileSample> filtered = new ArrayList<>();
        boolean uprightTree = candidate.height() >= 5 && candidate.connectedLogs() >= 5;
        for (TreeProfileSample sample : samples) {
            String text = (sample.id() + " " + sample.sourceFile() + " " + sample.trunkPlacer() + " " + sample.foliagePlacer()).toLowerCase(java.util.Locale.ROOT);
            if (uprightTree && isLowGrowthSample(text)) {
                continue;
            }
            if (candidate.species() != TreeSpecies.JUNGLE && text.contains("bamboo")) {
                continue;
            }
            filtered.add(sample);
        }
        return filtered.isEmpty() ? samples : filtered;
    }

    private boolean isLowGrowthSample(String text) {
        return text.contains("fallen_log")
                || text.contains("fallen.log")
                || text.contains("tree_stump")
                || text.contains("stump")
                || text.contains("bush")
                || text.contains("mini")
                || text.contains("young")
                || text.contains("sapling");
    }

    private TreeGrowthProfile profileFor(TreeDna dna, TreeEvolutionConfig currentConfig) {
        for (TreeProfileSample sample : profileSamples.getOrDefault(dna.species(), List.of())) {
            if (sample.id().equals(dna.profileSampleId())) {
                return sample.profile();
            }
        }
        return currentConfig.profile(dna.species());
    }

    private boolean canWorkAt(Location location, TreeEvolutionConfig currentConfig) {
        World world = location.getWorld();
        if (world == null) {
            plugin.pathDebug().failure(plugin, "tree-evolution", "missing-world", "canWorkAt");
            return false;
        }
        if (!plugin.canEvolveAt(location, "tree-evolution")) {
            return false;
        }
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        int radius = currentConfig.ownedChunkRadius();
        for (int x = chunkX - radius; x <= chunkX + radius; x++) {
            for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
                if (!world.isChunkLoaded(x, z)) {
                    plugin.pathDebug().failure(plugin, "tree-evolution", "unloaded-chunk", format(location));
                    return false;
                }
            }
        }
        if (!Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, radius)) {
            plugin.pathDebug().failure(plugin, "tree-evolution", "region-ownership", format(location));
            return false;
        }
        boolean nearPlayer = isNearPlayer(location, currentConfig.requiredPlayerDistanceChunks());
        if (!nearPlayer) {
            plugin.pathDebug().failure(plugin, "tree-evolution", "player-distance", format(location));
        }
        return nearPlayer;
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

    private void cleanupTreeDna(String reason) {
        TreeEvolutionConfig currentConfig = config;
        if (!currentConfig.dnaCleanupEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        long next = nextDnaCleanupMillis.get();
        if (now < next || !nextDnaCleanupMillis.compareAndSet(next, now + currentConfig.dnaCleanupIntervalMillis())) {
            return;
        }

        int removed = 0;
        for (TreeDna dna : treeDna.values()) {
            if (!shouldRemoveDna(dna, currentConfig, now)) {
                continue;
            }
            if (treeDna.remove(dna.key(), dna)) {
                planCache.remove(dna.key());
                projectionProgressCache.remove(dna.key());
                focusYieldUntil.remove(dna.key());
                focusedCandidateCache.remove(dna.key());
                removed++;
                plugin.pathDebug().traceSampled(plugin, "tree-evolution", "cleanup.remove-dna",
                        dna.key() + " species=" + dna.species() + " reason=" + reason);
            }
        }
        if (removed > 0) {
            knownTrees.set(treeDna.size());
            nearestCandidateCache.clear();
            knownCandidateCache.clear();
            markTreeDnaDirty("cleanup removed=" + removed);
            saveTreeDna();
        }
        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "cleanup.pass",
                "reason=" + reason + " removed=" + removed + " remaining=" + treeDna.size());
        plugin.resourceReporter().count(plugin, "tree-evolution", "cleanup.tree-dna", treeDna.size(), removed, "reason=" + reason);
    }

    private void maybeCleanupTreeDna(String reason) {
        TreeEvolutionConfig currentConfig = config;
        if (!currentConfig.dnaCleanupEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long next = nextDnaCleanupMillis.get();
        if (now < next) {
            return;
        }
        cleanupTreeDna(reason);
    }

    private boolean shouldRemoveDna(TreeDna dna, TreeEvolutionConfig currentConfig, long now) {
        long lastTouched = dna.lastGrowthMillis() <= 0L ? 0L : dna.lastGrowthMillis();
        boolean oldEnough = now - lastTouched >= currentConfig.dnaCleanupMissingBaseMillis();
        if (!oldEnough) {
            return false;
        }

        World world = Bukkit.getWorld(dna.worldId());
        if (world == null) {
            return true;
        }
        int chunkX = dna.baseX() >> 4;
        int chunkZ = dna.baseZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            return false;
        }
        if (!dna.stumpPresent()) {
            return true;
        }
        return world.getBlockAt(dna.baseX(), dna.baseY(), dna.baseZ()).getType() != dna.species().logMaterial();
    }

    private void normalizeKnownTreeDna(String reason) {
        int normalized = 0;
        for (TreeDna dna : treeDna.values()) {
            TreeDnaNormalizer.NormalizedDna result = dnaNormalizer.normalize(dna, config.maximumStage());
            if (!result.changed() || !treeDna.replace(dna.key(), dna, result.dna())) {
                continue;
            }
            normalized++;
            planCache.remove(dna.key());
            projectionProgressCache.remove(dna.key());
            focusYieldUntil.remove(dna.key());
            focusedCandidateCache.remove(dna.key());
            diagnostics.recordDnaNormalized(config, dna, result.dna(), result.summary());
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "state.dna-stage-ceiling",
                    result.dna().key() + " reason=" + reason + " " + result.summary());
        }
        if (normalized > 0) {
            markTreeDnaDirty("stage-ceiling normalized=" + normalized);
            saveTreeDna();
        }
    }

    private void loadTreeDna() {
        File file = dnaFile();
        if (!file.exists()) {
            plugin.pathDebug().trace(plugin, "tree-evolution", "persistence.load-missing", "tree-evolution.yml");
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection trees = yaml.getConfigurationSection("trees");
        if (trees == null) {
            return;
        }
        int loaded = 0;
        int normalized = 0;
        for (String key : trees.getKeys(false)) {
            ConfigurationSection section = trees.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                TreeDna dna = TreeDna.from(section);
                TreeDnaNormalizer.NormalizedDna normalizedDna = dnaNormalizer.normalize(dna, config.maximumStage());
                treeDna.put(normalizedDna.dna().key(), normalizedDna.dna());
                if (normalizedDna.changed()) {
                    normalized++;
                    diagnostics.recordDnaNormalized(config, dna, normalizedDna.dna(), normalizedDna.summary());
                    plugin.pathDebug().traceSampled(plugin, "tree-evolution", "state.dna-normalize",
                            normalizedDna.dna().key() + " " + normalizedDna.summary());
                }
                loaded++;
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipping invalid tree DNA entry '" + key + "': " + ex.getMessage());
            }
        }
        knownTrees.set(treeDna.size());
        diagnostics.recordDnaLoaded(loaded);
        plugin.pathDebug().trace(plugin, "tree-evolution", "persistence.load", "tree-evolution.yml entries=" + loaded + " normalized=" + normalized);
        if (normalized > 0) {
            saveTreeDnaNow("dna-normalize");
        }
    }

    private void loadProfileSamples() {
        profileSamples = sampleStore.load(plugin);
        int sampleCount = profileSamples.values().stream().mapToInt(List::size).sum();
        plugin.pathDebug().trace(plugin, "tree-evolution", "persistence.profile-samples-load",
                "samples=" + sampleCount + " species=" + profileSamples.keySet().size());
    }

    private void refreshProfileSamples(StructureScanResult result) {
        profileSamples = sampleStore.saveFromScan(plugin, result);
        int sampleCount = profileSamples.values().stream().mapToInt(List::size).sum();
        plugin.pathDebug().trace(plugin, "tree-evolution", "persistence.profile-samples-refresh",
                "samples=" + sampleCount + " species=" + profileSamples.keySet().size());
    }

    private void saveTreeDna() {
        TreeEvolutionConfig currentConfig = config;
        if (dnaDirtyVersion.get() <= dnaSavedVersion.get()) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "persistence.save-clean",
                    "tree-evolution.yml entries=" + treeDna.size());
            return;
        }
        if (!plugin.isEnabled()) {
            saveTreeDnaNow("plugin-disabled");
            return;
        }

        long now = System.currentTimeMillis();
        long next = nextDnaSaveMillis.get();
        if (now < next || !nextDnaSaveMillis.compareAndSet(next, now + currentConfig.dnaSaveIntervalMillis())) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "persistence.save-debounce",
                    "tree-evolution.yml entries=" + treeDna.size() + " next-ms=" + Math.max(0L, next - now));
            return;
        }
        saveTreeDnaAsync();
    }

    private void markTreeDnaDirty(String reason) {
        long version = dnaDirtyVersion.incrementAndGet();
        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "persistence.dirty",
                "version=" + version + " reason=" + reason + " entries=" + treeDna.size());
    }

    private void saveTreeDnaAsync() {
        if (!dnaSaveRunning.compareAndSet(false, true)) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "persistence.save-skip-running",
                    "tree-evolution.yml entries=" + treeDna.size());
            return;
        }

        plugin.pathDebug().trace(plugin, "tree-evolution", "scheduler.async-save", "tree-evolution.yml entries=" + treeDna.size());
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                saveTreeDnaNow("async");
            } finally {
                dnaSaveRunning.set(false);
            }
        });
    }

    private void saveTreeDnaNow(String reason) {
        try (ReportSample sample = plugin.resourceReporter().begin("tree-evolution", "persistence.save-tree-dna")) {
            synchronized (dnaSaveLock) {
                plugin.pathDebug().trace(plugin, "tree-evolution", "persistence.save", "tree-evolution.yml entries=" + treeDna.size() + " reason=" + reason);
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.set("notes", "## Persistent tree DNA. This stores generated parameters, not copied schematic layouts.");
                ConfigurationSection trees = yaml.createSection("trees");
                int index = 0;
                for (TreeDna dna : treeDna.values()) {
                    dna.writeTo(trees.createSection(Integer.toString(index++)));
                }
                File file = dnaFile();
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    plugin.getLogger().warning("Could not create plugin data folder for tree DNA.");
                    sample.detail("folder-create-failed entries=" + treeDna.size() + " reason=" + reason);
                    return;
                }
                try {
                    yaml.save(file);
                    dnaSavedVersion.set(dnaDirtyVersion.get());
                    sample.workUnits(treeDna.size()).changedUnits(1).detail("entries=" + treeDna.size() + " reason=" + reason);
                } catch (IOException ex) {
                    sample.detail("failed entries=" + treeDna.size() + " reason=" + reason + " " + ex.getClass().getSimpleName());
                    plugin.getLogger().log(Level.WARNING, "Could not save tree evolution DNA.", ex);
                }
            }
        }
    }

    private void ensureFolders() {
        File folder = scanner.scanFolder(plugin);
        if (!folder.exists() && folder.mkdirs()) {
            plugin.pathDebug().trace(plugin, "tree-evolution", "persistence.debug-folder-create", "structure-scan");
        }
    }

    private void scheduleAutoScan(TreeEvolutionConfig currentConfig) {
        if (!currentConfig.debugEnabled() || !currentConfig.autoScanOnStartup()) {
            plugin.pathDebug().trace(plugin, "tree-evolution", "persistence.debug-auto-scan-skip", "disabled");
            return;
        }

        File folder = scanner.scanFolder(plugin);
        File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            return lower.endsWith(".nbt")
                    || lower.endsWith(".schem")
                    || lower.endsWith(".schematic")
                    || lower.endsWith(".zip")
                    || lower.endsWith(".jar");
        });
        if (files == null || files.length == 0) {
            plugin.pathDebug().trace(plugin, "tree-evolution", "persistence.debug-auto-scan-empty", "structure-scan");
            return;
        }

        plugin.pathDebug().trace(plugin, "tree-evolution", "scheduler.async-delay", "auto structure scan files=" + files.length);
        Bukkit.getAsyncScheduler().runDelayed(
                plugin,
                task -> {
                    StructureScanResult result = scanner.scanAll(plugin);
                    refreshProfileSamples(result);
                    int sampleCount = profileSamples.values().stream().mapToInt(List::size).sum();
                    plugin.pathDebug().trace(plugin, "tree-evolution", "persistence.debug-auto-scan-done",
                            "structures=" + result.structures().size()
                                    + " profile-samples=" + sampleCount
                                    + " files=" + files.length);
                },
                40L,
                java.util.concurrent.TimeUnit.MILLISECONDS
        );
    }

    private File dnaFile() {
        return new File(plugin.getDataFolder(), "tree-evolution.yml");
    }

    private String chunkKey(TreeCandidate candidate) {
        return candidate.world().getUID() + ":" + (candidate.baseX() >> 4) + ":" + (candidate.baseZ() >> 4);
    }

    private boolean isLogOrLeaf(Material material) {
        return material.name().endsWith("_LOG") || material.name().endsWith("_WOOD") || material.name().endsWith("_LEAVES");
    }

    private static String keyFor(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private String pct(double value) {
        return Math.round(value * 1000.0D) / 10.0D + "%";
    }

    private String format(Block block) {
        return format(block.getLocation());
    }

    private String format(Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "unknown" : world.getName();
        return worldName + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private record TreeGroup(int logs, int leaves, Set<String> keys,
            boolean ownershipComplete) {
    }

    private record CanopySample(int leaves, int logs) {
    }

    private record CachedTreePlan(String signature, TreePlan plan, List<PlannedTreeBlock> orderedBlocks, Map<String, PlannedTreeBlock> blocksByKey) {
    }

    private record CachedProjectionProgress(
            String signature, TreeProjectionProgress progress, long expiresMillis) {
    }

    private record TreeWorkStatus(
            boolean needsFocus,
            boolean stageComplete,
            TreeGrowthQueuePolicy.Completion completion,
            TreeGrowthQueuePolicy.Budget budget,
            int exposedUpperLogs
    ) {
        String summary() {
            return "stage-complete=" + stageComplete
                    + " trunk=" + completion.trunkSummary()
                    + " branch=" + completion.branchSummary()
                    + " canopy=" + completion.canopySummary()
                    + " budget=" + percent(budget.trunkPercent())
                    + "/" + percent(budget.branchPercent())
                    + "/" + percent(budget.canopyPercent())
                    + " exposed-upper-logs=" + exposedUpperLogs;
        }

        private static String percent(double value) {
            return Math.round(value * 100.0D) + "%";
        }
    }
    private record CachedTreeCandidate(TreeCandidate candidate, long expiresMillis) {
    }

    private record CachedKnownCandidates(List<TreeCandidate> candidates, long expiresMillis) {
    }

    private record CandidateBlock(PlannedTreeBlock block, int index, int priority) {
    }

    private record TreeProjectionProgress(int trunkPlaced, int trunkTotal, int branchPlaced, int branchTotal, int canopyPlaced, int canopyTotal) {
        double branchPercent() {
            return branchTotal == 0 ? 1.0D : branchPlaced / (double) branchTotal;
        }

        double canopyPercent() {
            return canopyTotal == 0 ? 1.0D : canopyPlaced / (double) canopyTotal;
        }

        String branchSummary() {
            return branchPlaced + "/" + branchTotal + "=" + Math.round(branchPercent() * 1000.0D) / 10.0D + "%";
        }

        String canopySummary() {
            return canopyPlaced + "/" + canopyTotal + "=" + Math.round(canopyPercent() * 1000.0D) / 10.0D + "%";
        }
    }

    private record PlannedTarget(PlannedTreeBlock block, Block target, int nextCursor, double shapeScore, String shapeReason) {
    }
}
