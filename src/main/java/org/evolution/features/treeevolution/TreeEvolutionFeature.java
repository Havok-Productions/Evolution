package org.evolution.features.treeevolution;

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
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
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
import org.evolution.coreparts.PluginFeature;
import org.evolution.coreparts.ResourceReporter.ReportSample;
import org.evolution.coreparts.EvolutionPlugin;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionOperations;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionResult;

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
            Material.ALLIUM,
            Material.AZURE_BLUET,
            Material.RED_TULIP,
            Material.ORANGE_TULIP,
            Material.WHITE_TULIP,
            Material.PINK_TULIP,
            Material.PINK_PETALS,
            Material.OXEYE_DAISY,
            Material.CORNFLOWER,
            Material.LILY_OF_THE_VALLEY,
            Material.BROWN_MUSHROOM,
            Material.RED_MUSHROOM
    );

    private final EvolutionPlugin plugin;
    private final TreeEvolutionPlanner planner = new TreeEvolutionPlanner();
    private final TreeConstructorCore constructorCore = new TreeConstructorCore();
    private final TreeShapeEngine shapeEngine = new TreeShapeEngine();
    private final TreeDnaNormalizer dnaNormalizer = new TreeDnaNormalizer();
    private final TreeEvolutionDiagnostics diagnostics = new TreeEvolutionDiagnostics();
    private final StructurePatternScanner scanner = new StructurePatternScanner();
    private final TreeProfileSampleStore sampleStore = new TreeProfileSampleStore();
    private final ConcurrentMap<String, TreeDna> treeDna = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedTreePlan> planCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedProjectionProgress> projectionProgressCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedLiveTerminalAudit> liveTerminalAuditCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TreeFocusPool> focusedTreePools = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> focusYieldUntil = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> seedlingCooldownUntil = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> seedlingSearchSequence = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> nextReservedSeedlingSearchMillis = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> reservedSeedlingPassSequence = new ConcurrentHashMap<>();
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

    public TreeEvolutionFeature(EvolutionPlugin plugin) {
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
        nextReservedSeedlingSearchMillis.clear();
        reservedSeedlingPassSequence.clear();
        projectionProgressCache.clear();
        liveTerminalAuditCache.clear();
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
        nextReservedSeedlingSearchMillis.clear();
        reservedSeedlingPassSequence.clear();
        projectionProgressCache.clear();
        liveTerminalAuditCache.clear();
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
                nextReservedSeedlingSearchMillis.remove(playerId);
                reservedSeedlingPassSequence.remove(playerId);
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
                    if (finalizeSatisfiedStageTransition(candidate, dna, status, currentConfig)) {
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
                if (finalizeSatisfiedStageTransition(candidate, dna, before, currentConfig)) {
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
                if (finalizeSatisfiedStageTransition(candidate, dna, after, currentConfig)) {
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

            // ## Reproduction has one independently paced lane. A successful
            // sapling may add one extra block, but structural tree work keeps
            // its original block budget and therefore cannot be starved.
            int reservedSeedlings = runReservedSeedlingSearch(
                    playerId, origin, currentConfig);
            placed += reservedSeedlings;

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
        int exposedUpperLogs = exposedUpperLogCount(candidate, dna, plan.blocksByKey());
        BranchTipCoverage branchTips = branchTipCoverage(candidate, dna, plan);
        boolean stageComplete = TreeFocusPolicy.stageStructureComplete(
                completion, budget, exposedUpperLogs, branchTips.uncoveredTips());
        boolean transitionPending = TreeFocusPolicy.transitionPending(
                dna.stageCleanupBurst(), dna.stageGrowthBurst(),
                stageComplete, dna.hasOriginalShapeSnapshot());
        return new TreeWorkStatus(
                TreeFocusPolicy.needsFocus(
                        transitionPending, completion, budget,
                        exposedUpperLogs, branchTips.uncoveredTips()),
                stageComplete,
                transitionPending,
                dna.hasOriginalShapeSnapshot(),
                dna.originalShapeBlockCount(),
                dna.unresolvedOriginalShapeLeafCount(),
                completion,
                budget,
                exposedUpperLogs,
                branchTips.uncoveredTips()
        );
    }

    private boolean finalizeSatisfiedStageTransition(TreeCandidate candidate, TreeDna dna,
            TreeWorkStatus status, TreeEvolutionConfig currentConfig) {
        if (!TreeFocusPolicy.shouldFinalizeTransition(
                status.stageComplete(), dna.stageCleanupBurst(),
                dna.stageGrowthBurst(), dna.hasOriginalShapeSnapshot(),
                status.unresolvedSourceLeaves())) {
            return false;
        }
        int previousBurst = dna.stageGrowthBurst();
        int originalBlockCount = dna.originalShapeBlockCount();
        dna.completeStageTransition();
        markTreeDnaDirty("stage transition finalized " + dna.key());
        plugin.pathDebug().trace(plugin, "tree-evolution", "transition.finalize-complete",
                "tree=" + dna.key() + " cleared-growth-burst=" + previousBurst
                        + " original-snapshot-cleared=" + originalBlockCount
                        + " allowance-expired=" + (previousBurst == 0)
                        + " " + status.summary()
                        + " ## full projected structure closed the transition and released its temporary source shape even when the action allowance expired earlier");
        updateMaturity(candidate, dna, currentConfig);
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
            boolean sourceCaptureRequired =
                    (!dna.hasOriginalShapeSnapshot()
                            && (dna.age() == 0 || dna.hasStageBurst()))
                    || (dna.hasOriginalShapeSnapshot()
                            && !dna.originalShapeCaptureIsCurrent());
            if (sourceCaptureRequired
                    && !ensureOriginalShapeSnapshot(candidate, dna,
                            "before-world-change")) {
                sample.detail("original-shape-wait " + dna.key());
                return false;
            }
            reconcileStageWithSourceHeight(candidate, dna, currentConfig);

        // ## TREE CONSTRUCTOR CORE
        // The feature gathers one immutable live snapshot, the hierarchy selects one
        // attached subsystem, and only that subsystem may change the tree this action.
        Biome biome = candidate.baseBlock().getBiome();
        CachedTreePlan cachedPlan = cachedPlan(
                dna, biome, currentConfig.rootsEnabled());
        reconcileSourceLeafLedger(candidate, dna, cachedPlan);
        TreeGrowthIntent requestedIntent = refreshIntent(
                candidate, dna, currentConfig);
        diagnostics.recordPlan(currentConfig, dna, cachedPlan.plan(),
                cachedPlan.orderedBlocks(), candidate.world(), false);

        boolean constructorNeedsCompleteOwnership = dna.stageCleanupBurst() > 0
                || dna.damageCount() > 0
                || requestedIntent == TreeGrowthIntent.REPAIR;
        if (constructorNeedsCompleteOwnership && !candidate.ownershipComplete()) {
            candidate = buildCandidate(candidate.baseBlock(), true)
                    .orElse(candidate);
        }
        TreeGrowthQueuePolicy.Completion constructorCompletion =
                stageCompletion(candidate, dna, cachedPlan);
        TreeGrowthQueuePolicy.Budget constructorBudget =
                TreeGrowthQueuePolicy.stageBudget(dna);
        int constructorExposedLogs = exposedUpperLogCount(
                candidate, dna, cachedPlan.blocksByKey());
        BranchTipCoverage constructorBranchTips = branchTipCoverage(
                candidate, dna, cachedPlan);
        boolean broadCleanupReady =
                TreeCanopyTransitionPolicy.allowsBroadCleanup(
                        dna, constructorCompletion.canopyPercent())
                && constructorCompletion.trunkPercent()
                        >= constructorBudget.trunkPercent()
                && constructorCompletion.branchPercent()
                        >= constructorBudget.branchPercent()
                && constructorCompletion.canopyPercent()
                        >= constructorBudget.canopyPercent();
        Optional<PlannedTarget> transitionBlocker =
                dna.stageCleanupBurst() > 0 && candidate.ownershipComplete()
                        ? readyTransitionBlocker(
                                candidate, dna, cachedPlan, currentConfig)
                        : Optional.empty();
        List<Block> retiredCrown =
                dna.stageCleanupBurst() > 0
                                && candidate.ownershipComplete()
                                && broadCleanupReady
                        ? findRetiredCanopyLeaves(
                                candidate, dna, cachedPlan,
                                pruneBatchSize(dna), currentConfig)
                        : List.of();
        TreeConstructionDecision construction = constructorCore.decide(
                candidate, dna, constructorCompletion, constructorBudget,
                requestedIntent, constructorExposedLogs,
                constructorBranchTips.uncoveredTips(),
                transitionBlocker.isPresent(), broadCleanupReady,
                dna.unresolvedOriginalShapeLeafCount() > 0);
        plugin.pathDebug().traceSampled(
                plugin, "tree-evolution",
                construction.finalAudit().passed()
                        ? "audit.constructor-final-contract-pass"
                        : "audit.constructor-final-contract-blocked",
                construction.marker()
                        + " tree=" + dna.key()
                        + " " + construction.finalAudit().marker()
                        + " first-failure="
                        + construction.finalAudit().firstFailure()
                        + " detail=" + construction.finalAudit().detail()
                        + " ## final audit independently rechecks every smaller constructor contract");
        String executorName = constructorCore.executorName(construction);
        diagnostics.recordConstructorDecision(
                currentConfig, dna, construction,
                constructorCompletion, constructorBudget, executorName);
        plugin.pathDebug().traceSampled(
                plugin, "tree-evolution", "constructor.phase",
                construction.marker() + " tree=" + dna.key()
                        + " reason=" + construction.reason());

        final TreeCandidate constructionCandidate = candidate;
        final TreeGrowthIntent constructionRequestedIntent = requestedIntent;
        TreeConstructionOperations operations = new TreeConstructionOperations() {
            @Override
            public TreeConstructionResult waitForOwnership() {
                return constructorWaitResult(
                        dna, construction, currentConfig, "ownership");
            }

            @Override
            public TreeConstructionResult waitForSourceSnapshot() {
                return constructorWaitResult(
                        dna, construction, currentConfig, "source-snapshot");
            }

            @Override
            public TreeConstructionResult repair() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.REPAIR,
                        constructionRequestedIntent);
            }

            @Override
            public TreeConstructionResult replaceTransitionBlocker() {
                return executeTransitionBlockerPhase(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, transitionBlocker);
            }

            @Override
            public TreeConstructionResult buildSupport() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.HEIGHT,
                        constructionRequestedIntent);
            }

            @Override
            public TreeConstructionResult buildCanopyShell() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.CANOPY,
                        constructionRequestedIntent);
            }

            @Override
            public TreeConstructionResult buildBranchFrame() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.BRANCH,
                        constructionRequestedIntent);
            }

            @Override
            public TreeConstructionResult fillCanopy() {
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, TreeGrowthIntent.CANOPY,
                        constructionRequestedIntent);
            }

            @Override
            public TreeConstructionResult pruneRetiredCrown() {
                return executeRetiredCrownPhase(
                        dna, currentConfig, construction,
                        retiredCrown);
            }

            @Override
            public TreeConstructionResult finalizeTransition() {
                return executeTransitionFinalizer(
                        constructionCandidate, dna, currentConfig,
                        construction);
            }

            @Override
            public TreeConstructionResult buildDetails() {
                TreeGrowthIntent detailIntent =
                        constructionRequestedIntent
                                == TreeGrowthIntent.SEEDLING
                        ? TreeGrowthIntent.SEEDLING
                        : TreeGrowthIntent.DETAIL;
                return executeIntentConstruction(
                        constructionCandidate, dna, cachedPlan,
                        currentConfig, detailIntent,
                        constructionRequestedIntent);
            }

            @Override
            public TreeConstructionResult complete() {
                return executeConstructorComplete(
                        constructionCandidate, dna, currentConfig,
                        construction);
            }
        };

        TreeConstructionResult result;
        String resourceTask = "constructor."
                + construction.phase().name().toLowerCase(
                        java.util.Locale.ROOT);
        try (ReportSample executorSample = plugin.resourceReporter().begin(
                "tree-evolution", resourceTask)) {
            result = constructorCore.execute(construction, operations);
            executorSample.changedUnits(result.changedUnits())
                    .detail(executorName + " " + result.detail());
        }
        plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                "constructor.executor",
                construction.marker() + " executor=" + executorName
                        + " changed=" + result.worldChanged()
                        + " units=" + result.changedUnits()
                        + " detail=" + result.detail());
        sample.changedUnits(result.changedUnits()).detail(result.detail());
        return result.worldChanged();
        }
    }

    private TreeConstructionResult constructorWaitResult(
            TreeDna dna,
            TreeConstructionDecision construction,
            TreeEvolutionConfig currentConfig,
            String gate
    ) {
        diagnostics.recordReject(currentConfig,
                "constructor-wait", construction.marker()
                        + " tree=" + dna.key()
                        + " gate=" + gate
                        + " reason=" + construction.reason());
        return TreeConstructionResult.idle(
                "constructor.wait-" + gate + " " + dna.key());
    }

    private TreeConstructionResult executeTransitionBlockerPhase(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig,
            Optional<PlannedTarget> transitionBlocker
    ) {
        if (transitionBlocker.isPresent()
                && replaceTransitionBlocker(
                        candidate, dna, cachedPlan, currentConfig,
                        transitionBlocker.get())) {
            return TreeConstructionResult.changed(
                    1, "constructor.atomic-blocker " + dna.key());
        }
        diagnostics.recordReject(currentConfig,
                "constructor-blocker-lost", dna.key());
        return TreeConstructionResult.idle(
                "constructor.blocker-lost " + dna.key());
    }

    private TreeConstructionResult executeRetiredCrownPhase(
            TreeDna dna,
            TreeEvolutionConfig currentConfig,
            TreeConstructionDecision construction,
            List<Block> retiredCrown
    ) {
        if (retiredCrown.isEmpty()) {
            diagnostics.recordReject(currentConfig,
                    "constructor-retired-crown-empty",
                    dna.key() + " unresolved-source-leaves="
                            + dna.unresolvedOriginalShapeLeafCount());
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "gate.source-leaf-unresolved",
                    "tree=" + dna.key()
                            + " unresolved="
                            + dna.unresolvedOriginalShapeLeafCount()
                            + " ## the source snapshot remains open; no unresolved leaf may be forgotten by transition finalization");
            return TreeConstructionResult.idle(
                    "constructor.retired-crown-empty " + dna.key());
        }
        Block leaf = retiredCrown.get(0);
        String retiredLeafKey = keyFor(leaf);
        if (!dna.markOriginalShapeLeafRetired(retiredLeafKey)) {
            diagnostics.recordReject(currentConfig,
                    "constructor-retired-leaf-already-processed",
                    dna.key() + " leaf=" + format(leaf));
            return TreeConstructionResult.idle(
                    "constructor.retired-leaf-already-processed "
                            + dna.key());
        }
        leaf.setType(Material.AIR, false);
        dna.markPrunedNow();
        markTreeDnaDirty("retired source leaf " + retiredLeafKey);
        projectionProgressCache.remove(dna.key());
        liveTerminalAuditCache.remove(dna.key());
        changedBlocks.incrementAndGet();
        diagnostics.recordPrunedBatch(
                plugin, currentConfig, List.of(leaf), dna);
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "constructor.prune-retired-crown",
                construction.marker() + " tree=" + dna.key()
                        + " removed=" + format(leaf)
                        + " ## broad pruning runs only after the replacement structure reaches every stage target");
        return TreeConstructionResult.changed(
                1, "constructor.prune-retired-crown " + dna.key());
    }

    private TreeConstructionResult executeTransitionFinalizer(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig currentConfig,
            TreeConstructionDecision construction
    ) {
        int originalBlocks = dna.originalShapeBlockCount();
        dna.completeStageCleanup();
        TreeGrowthIntent nextIntent = dna.stageGrowthBurst() > 0
                ? stageBurstIntent(candidate, dna)
                : TreeGrowthIntent.CANOPY;
        dna.setCurrentIntent(nextIntent);
        markTreeDnaDirty("constructor transition cleanup complete "
                + dna.key());
        saveTreeDna();
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "constructor.transition-finalize",
                construction.marker() + " tree=" + dna.key()
                        + " source-blocks=" + originalBlocks
                        + " next=" + nextIntent);
        return TreeConstructionResult.idle(
                "constructor.transition-finalize " + dna.key());
    }

    private TreeConstructionResult executeConstructorComplete(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig currentConfig,
            TreeConstructionDecision construction
    ) {
        TreeMaturityStage before = dna.maturityStage();
        updateMaturity(candidate, dna, currentConfig);
        if (dna.maturityStage() != before) {
            markTreeDnaDirty("constructor maturity handoff " + dna.key());
            saveTreeDna();
        }
        plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                "constructor.complete",
                construction.marker() + " tree=" + dna.key()
                        + " stage=" + before + "->" + dna.maturityStage()
                        + " ## no structural planner runs after the current stage contract is complete");
        return TreeConstructionResult.idle(
                "constructor.complete " + dna.key());
    }

    private TreeConstructionResult executeIntentConstruction(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig,
            TreeGrowthIntent intent,
            TreeGrowthIntent requestedIntent
    ) {
        if (intent != dna.currentIntent()) {
            dna.setCurrentIntent(intent);
        }
        diagnostics.recordIntent(currentConfig, dna, intent,
                "requested=" + requestedIntent
                        + " cursor=" + dna.planCursor()
                        + " blocked=" + dna.blockedAttempts());
        Optional<Block> seedlingSpot = intent == TreeGrowthIntent.SEEDLING
                ? findSeedlingSpot(candidate.world(), dna, currentConfig)
                : Optional.empty();
        if (seedlingSpot.isPresent()) {
            spreadSeedling(seedlingSpot.get(), dna, currentConfig,
                    "intent");
            dna.markPlacedForIntent(intent, dna.planCursor());
            dna.consumeStageGrowthBurst();
            return TreeConstructionResult.changed(
                    1, "seedling.spread " + dna.key());
        }

        if (intent == TreeGrowthIntent.CANOPY) {
            BranchTipCoverage branchTips = branchTipCoverage(
                    candidate, dna, cachedPlan);
            if (branchTips.firstUnplannedBareTip() != null
                    && pruneUnplannedBareTerminal(
                            candidate, dna, cachedPlan, currentConfig,
                            branchTips.firstUnplannedBareTip())) {
                dna.markPlacedForIntent(intent, dna.planCursor());
                projectionProgressCache.remove(dna.key());
                liveTerminalAuditCache.remove(dna.key());
                updateMaturity(candidate, dna, currentConfig);
                return TreeConstructionResult.changed(
                        1, "prune.unplanned-bare-terminal " + dna.key());
            }
            if (branchTips.firstStalePersistentEnvelopeLeaf() != null
                    && pruneStalePersistentEnvelopeLeaf(
                            candidate, dna, cachedPlan, currentConfig,
                            branchTips.firstStalePersistentEnvelopeLeaf())) {
                dna.markPlacedForIntent(intent, dna.planCursor());
                projectionProgressCache.remove(dna.key());
                liveTerminalAuditCache.remove(dna.key());
                updateMaturity(candidate, dna, currentConfig);
                return TreeConstructionResult.changed(
                        1, "prune.stale-persistent-envelope-leaf "
                                + dna.key());
            }
            Optional<Block> exposedLog = findExposedUpperLog(
                    candidate, dna, cachedPlan.blocksByKey());
            if (exposedLog.isPresent()) {
                int liftedLeaves = coverExposedLog(
                        candidate, dna, currentConfig,
                        exposedLog.get(), cachedPlan.blocksByKey());
                if (liftedLeaves > 0) {
                    dna.markPlacedForIntent(intent, dna.planCursor());
                    dna.consumeStageGrowthBurst();
                    projectionProgressCache.remove(dna.key());
                    liveTerminalAuditCache.remove(dna.key());
                    updateMaturity(candidate, dna, currentConfig);
                    plugin.pathDebug().trace(plugin, "tree-evolution",
                            "shape.integrity.canopy-cover",
                            "tree=" + dna.key()
                                    + " trunk=" + format(exposedLog.get())
                                    + " leaves=" + liftedLeaves
                                    + " ## canopy shell corrected an exposed live upper trunk before normal target selection");
                    return TreeConstructionResult.changed(
                            liftedLeaves,
                            "canopy.integrity-cover " + dna.key());
                }
            }
            if (branchTips.firstUncoveredTip() != null) {
                int attachedLeaves = coverBranchTip(
                        candidate, dna, currentConfig,
                        branchTips.firstUncoveredTip(),
                        branchTips.firstRequiredContacts(),
                        branchTips.firstRequiredCluster(),
                        cachedPlan.blocksByKey());
                if (attachedLeaves > 0) {
                    dna.markPlacedForIntent(intent, dna.planCursor());
                    dna.consumeStageGrowthBurst();
                    projectionProgressCache.remove(dna.key());
                    liveTerminalAuditCache.remove(dna.key());
                    updateMaturity(candidate, dna, currentConfig);
                    plugin.pathDebug().trace(plugin, "tree-evolution",
                            "shape.integrity.branch-tip-cover",
                            "tree=" + dna.key()
                                    + " tip="
                                    + format(branchTips.firstUncoveredTip())
                                    + " leaves=" + attachedLeaves
                                    + " target-contacts="
                                    + branchTips.firstRequiredContacts()
                                    + " target-envelope="
                                    + branchTips.firstRequiredCluster()
                                    + " ## a protruding terminal limb builds its preplanned connected leaf envelope before general canopy fill");
                    return TreeConstructionResult.changed(
                            attachedLeaves,
                            "canopy.branch-tip-cover " + dna.key());
                }
            }
        }

        Optional<PlannedTarget> plannedTarget = nextPlannedTarget(
                candidate, dna, cachedPlan, intent, currentConfig);
        if (plannedTarget.isPresent()) {
            PlannedTreeBlock plannedBlock = plannedTarget.get().block();
            Block target = plannedTarget.get().target();
            place(target, plannedBlock);
            if (dna.markEvolvedBlock(keyFor(target), plannedBlock.role())) {
                markTreeDnaDirty("recorded evolved " + plannedBlock.role()
                        + " " + keyFor(target));
            }
            dna.markPlacedForIntent(intent,
                    plannedTarget.get().nextCursor());
            projectionProgressCache.remove(dna.key());
            liveTerminalAuditCache.remove(dna.key());
            int liftedLeaves = maybeCoverExposedTopLog(
                    candidate, dna, currentConfig, target, plannedBlock,
                    cachedPlan.blocksByKey());
            if (intent != TreeGrowthIntent.CLEANUP
                    && intent != TreeGrowthIntent.SEEDLING) {
                dna.consumeStageGrowthBurst();
            }
            updateMaturity(candidate, dna, currentConfig);
            changedBlocks.incrementAndGet();
            diagnostics.recordPlaced(
                    plugin, currentConfig, target, plannedBlock);
            plugin.pathDebug().trace(plugin, "tree-evolution",
                    "place.block",
                    plannedBlock.role() + " " + plannedBlock.material()
                            + " intent=" + intent
                            + " at " + format(target)
                            + (liftedLeaves > 0
                                    ? " canopy-lift-leaves=" + liftedLeaves
                                    : ""));
            return TreeConstructionResult.changed(
                    1, plannedBlock.role() + " "
                            + plannedBlock.material() + " intent=" + intent
                            + " dna=" + dna.key());
        }

        if (intent != TreeGrowthIntent.CLEANUP) {
            diagnostics.recordReject(currentConfig,
                    "prune-skipped-normal-growth",
                    dna.key() + " intent=" + intent);
        }
        dna.markBlocked();
        if (dna.blockedAttempts() >= 3) {
            dna.setCurrentIntent(
                    nextIntentAfterBlocked(dna.currentIntent()));
        }
        diagnostics.recordReject(currentConfig,
                "target-complete-or-blocked",
                dna.key() + " intent=" + intent
                        + " blocked=" + dna.blockedAttempts());
        return TreeConstructionResult.idle(
                "target-complete-or-blocked " + dna.key()
                        + " intent=" + intent);
    }
    private Optional<PlannedTarget> nextPlannedTarget(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan,
            TreeGrowthIntent intent, TreeEvolutionConfig currentConfig) {
        List<PlannedTreeBlock> orderedBlocks = cachedPlan.orderedBlocks();
        Map<String, PlannedTreeBlock> blocksByKey = cachedPlan.blocksByKey();
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
            if (!hasPreplannedBranchEnvelope(
                    dna, plannedBlock, cachedPlan, currentConfig)) {
                placementRejects++;
                continue;
            }
            Block target = targetBlockFor(candidate.world(), plannedBlock);
            if (isSatisfiedPlannedBlock(dna, plannedBlock, target)) {
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
    private TreeGrowthIntent refreshIntent(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig currentConfig
    ) {
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
        TreeGrowthIntent preferred = preferredIntent(
                candidate, dna, currentConfig);
        if (dna.blockedAttempts() >= 3 || dna.age() - dna.lastIntentChangeAge() >= intentSpan(dna, dna.currentIntent())) {
            dna.setCurrentIntent(preferred);
        }
        if (dna.damageCount() > 0 && dna.currentIntent() != TreeGrowthIntent.REPAIR) {
            dna.setCurrentIntent(TreeGrowthIntent.REPAIR);
        }
        return dna.currentIntent();
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
                world, dna, cachedPlan.orderedBlocks(), Integer.MAX_VALUE);
        long ttl = config.testingEnabled() ? 750L : 2_000L;
        projectionProgressCache.put(dna.key(), new CachedProjectionProgress(
                cachedPlan.signature(), progress, now + ttl));
        return progress;
    }
    private int exposedUpperLogCount(TreeCandidate candidate, TreeDna dna,
            Map<String, PlannedTreeBlock> blocksByKey) {
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
                    if (block.getType() == dna.species().logMaterial()
                            && TreeCanopyIntegrityPolicy.requiresCanopyCover(
                                    block.getX(), block.getY(), block.getZ(),
                                    dna.species().leafMaterial(), blocksByKey)
                            && adjacentPlannedLeafContacts(
                                    world, dna, block,
                                    dna.species().leafMaterial(),
                                    blocksByKey) == 0) {
                        exposed++;
                    }
                }
            }
        }
        if (exposed > 0) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "shape.integrity.exposed-upper-logs",
                    "tree=" + dna.key() + " exposed=" + exposed + " live-top=" + liveTop
                            + " ## upper logs remain exposed until an evolution-owned planned leaf reforms their canopy cover");
        }
        return exposed;
    }

    private BranchTipCoverage branchTipCoverage(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan) {
        World world = candidate.world();
        Set<String> visitedTips = new HashSet<>();
        int liveTips = 0;
        int uncoveredPlannedTips = 0;
        Block firstUncovered = null;
        int firstCurrentContacts = 0;
        int firstRequiredContacts = 0;
        int firstCurrentCluster = 0;
        int firstRequiredCluster = 0;
        boolean firstNaturalVolume = true;
        for (TreeBranchPlan branch : cachedPlan.plan().branchPlans()) {
            TreeBranchPlan.BranchTip tip = branch.tip();
            String key = tip.x() + ":" + tip.y() + ":" + tip.z();
            if (!visitedTips.add(key)
                    || !isReadableTreeCoordinate(world, tip.x(), tip.z())) {
                continue;
            }
            Block tipBlock = world.getBlockAt(tip.x(), tip.y(), tip.z());
            if (tipBlock.getType() != dna.species().logMaterial()) {
                continue;
            }
            int requiredContacts = TreeBranchTipIntegrityPolicy.targetLeafContacts(
                    dna, tip.x(), tip.y(), tip.z(), cachedPlan.blocksByKey());
            int requiredCluster = TreeBranchTipIntegrityPolicy.targetClusterLeaves(
                    dna, tip.x(), tip.y(), tip.z(), cachedPlan.blocksByKey());
            if (requiredContacts <= 0 || requiredCluster <= 0) {
                continue;
            }
            liveTips++;
            int currentContacts = adjacentPlannedLeafContacts(
                    world, dna, tipBlock, dna.species().leafMaterial(),
                    cachedPlan.blocksByKey());
            int currentCluster = plannedEnvelopeLiveLeaves(
                    world, dna, tipBlock, dna.species().leafMaterial(),
                    cachedPlan.blocksByKey());
            boolean naturalVolume = hasNaturalLiveEnvelope(
                    world, dna, tipBlock, dna.species().leafMaterial(),
                    cachedPlan.blocksByKey());
            if (currentContacts >= requiredContacts
                    && currentCluster >= requiredCluster
                    && naturalVolume) {
                continue;
            }
            uncoveredPlannedTips++;
            if (firstUncovered == null) {
                firstUncovered = tipBlock;
                firstCurrentContacts = currentContacts;
                firstRequiredContacts = requiredContacts;
                firstCurrentCluster = currentCluster;
                firstRequiredCluster = requiredCluster;
                firstNaturalVolume = naturalVolume;
            }
        }

        LiveTerminalAudit liveAudit = liveTerminalAudit(candidate, dna, cachedPlan);
        int totalUncovered = uncoveredPlannedTips
                + liveAudit.unplannedBareTips()
                + liveAudit.stalePersistentEnvelopeLeaves();
        if (totalUncovered > 0) {
            Block firstVisibleProblem = firstUncovered != null
                    ? firstUncovered
                    : liveAudit.firstUnplannedBareTip() != null
                            ? liveAudit.firstUnplannedBareTip()
                            : liveAudit.firstStalePersistentEnvelopeLeaf();
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "shape.integrity.uncovered-branch-tips",
                    "tree=" + dna.key()
                            + " live-planned-tips=" + liveTips
                            + " uncovered-planned=" + uncoveredPlannedTips
                            + " unplanned-bare-terminals=" + liveAudit.unplannedBareTips()
                            + " stale-persistent-envelope-leaves="
                            + liveAudit.stalePersistentEnvelopeLeaves()
                            + " first=" + format(firstVisibleProblem)
                            + " contacts=" + firstCurrentContacts + "/" + firstRequiredContacts
                            + " envelope=" + firstCurrentCluster + "/" + firstRequiredCluster
                            + " natural-volume=" + firstNaturalVolume
                            + " ## actual terminal logs are audited alongside planned tips so stale protrusions cannot hide outside the target plan");
            if (uncoveredPlannedTips > 0) {
                plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                        "audit.branch-envelope-ownership-failed",
                        "tree=" + dna.key()
                                + " first=" + format(firstUncovered)
                                + " owned-contacts=" + firstCurrentContacts + "/" + firstRequiredContacts
                                + " owned-envelope=" + firstCurrentCluster + "/" + firstRequiredCluster
                                + " natural-volume=" + firstNaturalVolume
                                + " ownership-version=" + dna.evolutionOwnershipVersion()
                                + " evolved-leaves=" + dna.evolvedLeafCount()
                                + " ownership-required=" + dna.requiresEvolvedLeafOwnership()
                                + " ## original/preexisting leaves do not satisfy a terminal branch until the current tree evolution explicitly reforms and records them");
            }
        }
        return new BranchTipCoverage(
                liveTips, totalUncovered, firstUncovered,
                firstCurrentContacts, firstRequiredContacts,
                firstCurrentCluster, firstRequiredCluster,
                liveAudit.unplannedBareTips(), liveAudit.firstUnplannedBareTip(),
                liveAudit.stalePersistentEnvelopeLeaves(),
                liveAudit.firstStalePersistentEnvelopeLeaf());
    }

    private LiveTerminalAudit liveTerminalAudit(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan) {
        long now = System.currentTimeMillis();
        CachedLiveTerminalAudit cached = liveTerminalAuditCache.get(dna.key());
        if (cached != null
                && cached.signature().equals(cachedPlan.signature())
                && now < cached.expiresMillis()) {
            return cached.audit();
        }
        if (!candidate.ownershipComplete()) {
            return LiveTerminalAudit.NONE;
        }

        int unplannedBareTips = 0;
        Block firstUnplannedBareTip = null;
        for (String blockKey : candidate.naturalKeys()) {
            Optional<Block> optional = blockFromKey(candidate.world(), blockKey);
            if (optional.isEmpty()) {
                continue;
            }
            Block block = optional.get();
            if (block.getType() != dna.species().logMaterial()
                    || block.getY() < dna.baseY() + 2
                    || !isReadableTreeCoordinate(
                            candidate.world(), block.getX(), block.getZ())) {
                continue;
            }
            int trunkDistance = Math.max(
                    Math.abs(block.getX() - dna.trunkXAt(block.getY())),
                    Math.abs(block.getZ() - dna.trunkZAt(block.getY())));
            PlannedTreeBlock planned = cachedPlan.blocksByKey().get(
                    block.getX() + ":" + block.getY() + ":" + block.getZ());
            TreeLiveTerminalPolicy.Decision decision =
                    TreeLiveTerminalPolicy.classify(
                            candidate.ownershipComplete(),
                            planned == null ? null : planned.role(),
                            block.getY() - dna.baseY(),
                            trunkDistance,
                            sameSpeciesWoodNeighbors(block, dna));
            if (decision
                    != TreeLiveTerminalPolicy.Decision.PRUNE_UNPLANNED_BARE_TERMINAL) {
                continue;
            }
            unplannedBareTips++;
            if (firstUnplannedBareTip == null) {
                firstUnplannedBareTip = block;
            }
        }
        StaleEnvelopeLeafAudit staleLeafAudit = staleEnvelopeLeafAudit(
                candidate, dna, cachedPlan);
        LiveTerminalAudit audit = new LiveTerminalAudit(
                unplannedBareTips, firstUnplannedBareTip,
                staleLeafAudit.count(), staleLeafAudit.first());
        liveTerminalAuditCache.put(dna.key(), new CachedLiveTerminalAudit(
                cachedPlan.signature(), audit, now + 750L));
        if (unplannedBareTips > 0) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "shape.integrity.unplanned-bare-terminal",
                    "tree=" + dna.key()
                            + " count=" + unplannedBareTips
                            + " first=" + format(firstUnplannedBareTip)
                            + " ## live terminal wood absent from the target plan is stale structure, not a canopy candidate");
        }
        if (staleLeafAudit.count() > 0) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "shape.integrity.stale-persistent-envelope-leaf",
                    "tree=" + dna.key()
                            + " count=" + staleLeafAudit.count()
                            + " first=" + format(staleLeafAudit.first())
                            + " ## persistent leaves from the retired forced-envelope rule are outside the natural target canopy");
        }
        return audit;
    }

    private StaleEnvelopeLeafAudit staleEnvelopeLeafAudit(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan) {
        Set<String> visited = new HashSet<>();
        int count = 0;
        Block first = null;
        for (TreeBranchPlan.BranchTip tip
                : cachedPlan.plan().branchEnvelopeCleanupTips()) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int x = tip.x() + dx;
                        int y = tip.y() + dy;
                        int z = tip.z() + dz;
                        String coordinateKey = x + ":" + y + ":" + z;
                        if (!visited.add(coordinateKey)
                                || !isReadableTreeCoordinate(
                                        candidate.world(), x, z)) {
                            continue;
                        }
                        Block leaf = candidate.world().getBlockAt(x, y, z);
                        if (leaf.getType() != dna.species().leafMaterial()
                                || !candidate.naturalKeys().contains(keyFor(leaf))
                                || !(leaf.getBlockData() instanceof Leaves leaves)
                                || !leaves.isPersistent()) {
                            continue;
                        }
                        PlannedTreeBlock planned = cachedPlan.blocksByKey().get(
                                coordinateKey);
                        if (planned != null
                                && planned.role() == TreeBlockRole.CANOPY
                                && planned.material()
                                        == dna.species().leafMaterial()) {
                            continue;
                        }
                        count++;
                        if (first == null) {
                            first = leaf;
                        }
                    }
                }
            }
        }
        return new StaleEnvelopeLeafAudit(count, first);
    }

    private int sameSpeciesWoodNeighbors(Block block, TreeDna dna) {
        int neighbors = 0;
        for (BlockFace face : NEIGHBORS) {
            if (block.getRelative(face).getType() == dna.species().logMaterial()) {
                neighbors++;
            }
        }
        return neighbors;
    }


    private boolean pruneStalePersistentEnvelopeLeaf(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig, Block leaf) {
        if (leaf == null
                || !candidate.ownershipComplete()
                || leaf.getType() != dna.species().leafMaterial()
                || !candidate.naturalKeys().contains(keyFor(leaf))
                || !(leaf.getBlockData() instanceof Leaves leaves)
                || !leaves.isPersistent()) {
            return false;
        }
        PlannedTreeBlock planned = cachedPlan.blocksByKey().get(
                leaf.getX() + ":" + leaf.getY() + ":" + leaf.getZ());
        if (planned != null
                && planned.role() == TreeBlockRole.CANOPY
                && planned.material() == dna.species().leafMaterial()) {
            return false;
        }
        int chunkX = leaf.getX() >> 4;
        int chunkZ = leaf.getZ() >> 4;
        if (!leaf.getWorld().isChunkLoaded(chunkX, chunkZ)
                || !Bukkit.isOwnedByCurrentRegion(
                        leaf.getWorld(), chunkX, chunkZ,
                        currentConfig.ownedChunkRadius())
                || !plugin.canEvolveAt(
                        leaf.getLocation(), "tree-evolution")) {
            diagnostics.recordReject(
                    currentConfig, "stale-envelope-leaf-safety", format(leaf));
            return false;
        }
        leaf.setType(Material.AIR, false);
        changedBlocks.incrementAndGet();
        liveTerminalAuditCache.remove(dna.key());
        projectionProgressCache.remove(dna.key());
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "prune.stale-persistent-envelope-leaf",
                "tree=" + dna.key() + " removed=" + format(leaf)
                        + " ## legacy forced-envelope leaf was outside the natural deterministic canopy target");
        return true;
    }

    private boolean pruneUnplannedBareTerminal(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig, Block tip) {
        if (tip == null || tip.getType() != dna.species().logMaterial()) {
            return false;
        }
        PlannedTreeBlock planned = cachedPlan.blocksByKey().get(
                tip.getX() + ":" + tip.getY() + ":" + tip.getZ());
        int trunkDistance = Math.max(
                Math.abs(tip.getX() - dna.trunkXAt(tip.getY())),
                Math.abs(tip.getZ() - dna.trunkZAt(tip.getY())));
        TreeLiveTerminalPolicy.Decision decision = TreeLiveTerminalPolicy.classify(
                candidate.ownershipComplete(),
                planned == null ? null : planned.role(),
                tip.getY() - dna.baseY(),
                trunkDistance,
                sameSpeciesWoodNeighbors(tip, dna));
        if (decision
                != TreeLiveTerminalPolicy.Decision.PRUNE_UNPLANNED_BARE_TERMINAL) {
            return false;
        }
        int chunkX = tip.getX() >> 4;
        int chunkZ = tip.getZ() >> 4;
        if (!tip.getWorld().isChunkLoaded(chunkX, chunkZ)
                || !Bukkit.isOwnedByCurrentRegion(
                        tip.getWorld(), chunkX, chunkZ,
                        currentConfig.ownedChunkRadius())
                || !plugin.canEvolveAt(
                        tip.getLocation(), "tree-evolution")) {
            diagnostics.recordReject(currentConfig, "stale-terminal-safety", format(tip));
            return false;
        }
        tip.setType(Material.AIR, false);
        changedBlocks.incrementAndGet();
        liveTerminalAuditCache.remove(dna.key());
        projectionProgressCache.remove(dna.key());
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "prune.unplanned-bare-terminal",
                "tree=" + dna.key() + " removed=" + format(tip)
                        + " ## stale live branch tip was absent from the target plan and had no nearby leaf support");
        return true;
    }
    private int adjacentPlannedLeafContacts(
            World world, TreeDna dna, Block support, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey) {
        int contacts = 0;
        for (BlockFace face : NEIGHBORS) {
            int x = support.getX() + face.getModX();
            int y = support.getY() + face.getModY();
            int z = support.getZ() + face.getModZ();
            PlannedTreeBlock planned = blocksByKey.get(x + ":" + y + ":" + z);
            if (planned == null
                    || planned.role() != TreeBlockRole.CANOPY
                    || planned.material() != leafMaterial
                    || !isReadableTreeCoordinate(world, x, z)) {
                continue;
            }
            Block leaf = world.getBlockAt(x, y, z);
            if (leaf.getType() == leafMaterial
                    && dna.countsAsEvolvedLeaf(keyFor(leaf))) {
                contacts++;
            }
        }
        return contacts;
    }

    private int plannedEnvelopeLiveLeaves(
            World world, TreeDna dna, Block tip, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey) {
        return liveEnvelopeShape(
                world, dna, tip, leafMaterial, blocksByKey).leaves();
    }

    private boolean hasNaturalLiveEnvelope(
            World world, TreeDna dna, Block tip, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey) {
        return TreeBranchTipIntegrityPolicy.hasNaturalVolume(
                dna.maturityStage(), dna.species(),
                tip.getX(), tip.getY(), tip.getZ(),
                liveEnvelopeShape(world, dna, tip, leafMaterial, blocksByKey));
    }

    private TreeBranchTipIntegrityPolicy.EnvelopeShape liveEnvelopeShape(
            World world, TreeDna dna, Block tip, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey) {
        ArrayDeque<Block> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        for (BlockFace face : NEIGHBORS) {
            addLivePlannedLeaf(
                    world, dna, tip.getRelative(face), leafMaterial,
                    blocksByKey, pending, visited);
        }
        int leaves = 0;
        int minX = tip.getX();
        int maxX = tip.getX();
        int minY = tip.getY();
        int maxY = tip.getY();
        int minZ = tip.getZ();
        int maxZ = tip.getZ();
        while (!pending.isEmpty()) {
            Block current = pending.removeFirst();
            leaves++;
            minX = Math.min(minX, current.getX());
            maxX = Math.max(maxX, current.getX());
            minY = Math.min(minY, current.getY());
            maxY = Math.max(maxY, current.getY());
            minZ = Math.min(minZ, current.getZ());
            maxZ = Math.max(maxZ, current.getZ());
            for (BlockFace face : NEIGHBORS) {
                Block next = current.getRelative(face);
                if (Math.abs(next.getX() - tip.getX()) > 2
                        || Math.abs(next.getY() - tip.getY()) > 1
                        || Math.abs(next.getZ() - tip.getZ()) > 2) {
                    continue;
                }
                addLivePlannedLeaf(
                        world, dna, next, leafMaterial,
                        blocksByKey, pending, visited);
            }
        }
        return new TreeBranchTipIntegrityPolicy.EnvelopeShape(
                leaves, minX, maxX, minY, maxY, minZ, maxZ);
    }

    private void addLivePlannedLeaf(
            World world, TreeDna dna, Block leaf, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey,
            ArrayDeque<Block> pending, Set<String> visited) {
        String coordinateKey = leaf.getX() + ":" + leaf.getY() + ":" + leaf.getZ();
        if (visited.contains(coordinateKey)) {
            return;
        }
        PlannedTreeBlock planned = blocksByKey.get(coordinateKey);
        if (planned == null
                || planned.role() != TreeBlockRole.CANOPY
                || planned.material() != leafMaterial
                || !isReadableTreeCoordinate(world, leaf.getX(), leaf.getZ())
                || leaf.getType() != leafMaterial
                || !dna.countsAsEvolvedLeaf(keyFor(leaf))) {
            return;
        }
        visited.add(coordinateKey);
        pending.addLast(leaf);
    }

    private boolean isReadableTreeCoordinate(World world, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        return world.isChunkLoaded(chunkX, chunkZ)
                && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0);
    }

    private TreeProjectionProgress projectionProgress(
            World world,
            TreeDna dna,
            List<PlannedTreeBlock> orderedBlocks,
            int sampleLimitPerRole
    ) {
        int trunkTotal = 0;
        int trunkPlaced = 0;
        int branchTotal = 0;
        int branchPlaced = 0;
        int canopyTotal = 0;
        int canopyPlaced = 0;
        Map<Long, Boolean> readableChunks = new HashMap<>();
        for (PlannedTreeBlock block : orderedBlocks) {
            if (block.role() != TreeBlockRole.TRUNK
                    && block.role() != TreeBlockRole.BRANCH
                    && block.role() != TreeBlockRole.CANOPY) {
                continue;
            }
            int chunkX = block.x() >> 4;
            int chunkZ = block.z() >> 4;
            long chunkKey = ((long) chunkX << 32)
                    ^ (chunkZ & 0xffffffffL);
            boolean readable = readableChunks.computeIfAbsent(chunkKey,
                    ignored -> world.isChunkLoaded(chunkX, chunkZ)
                            && Bukkit.isOwnedByCurrentRegion(
                                    world, chunkX, chunkZ, 0));
            Block liveBlock = readable
                    ? world.getBlockAt(block.x(), block.y(), block.z())
                    : null;
            Material live = liveBlock == null
                    ? Material.AIR : liveBlock.getType();
            BlockProvenance provenance = BlockProvenance.classify(
                    config, dna, block, live, true, readable);
            if (readable && (provenance == BlockProvenance.LIQUID
                    || provenance == BlockProvenance.PLAYER_OR_FOREIGN_BLOCK)) {
                // ## Immutable obstacles are routed around and do not make a stage
                // mathematically impossible to complete.
                continue;
            }
            boolean satisfied = readable
                    && isSatisfiedPlannedBlock(dna, block, liveBlock);
            if (block.role() == TreeBlockRole.TRUNK
                    && trunkTotal < sampleLimitPerRole) {
                trunkTotal++;
                if (satisfied) {
                    trunkPlaced++;
                }
            } else if (block.role() == TreeBlockRole.BRANCH
                    && branchTotal < sampleLimitPerRole) {
                branchTotal++;
                if (satisfied) {
                    branchPlaced++;
                }
            } else if (block.role() == TreeBlockRole.CANOPY
                    && canopyTotal < sampleLimitPerRole) {
                canopyTotal++;
                if (satisfied) {
                    canopyPlaced++;
                }
            }
            if (trunkTotal >= sampleLimitPerRole
                    && branchTotal >= sampleLimitPerRole
                    && canopyTotal >= sampleLimitPerRole) {
                break;
            }
        }
        return new TreeProjectionProgress(
                trunkPlaced, trunkTotal,
                branchPlaced, branchTotal,
                canopyPlaced, canopyTotal);
    }

    private boolean isSatisfiedPlannedBlock(
            TreeDna dna, PlannedTreeBlock planned, Block liveBlock) {
        if (liveBlock == null) {
            return false;
        }
        Material live = liveBlock.getType();
        String blockKey = keyFor(liveBlock);
        boolean materialMatches = live == planned.material();
        boolean compatibleOrganic =
                isCompatibleOrganicOccupant(planned.role(), live);
        if (planned.role() == TreeBlockRole.CANOPY) {
            return TreeBranchEnvelopeOwnershipPolicy.plannedCanopySatisfied(
                    materialMatches, compatibleOrganic,
                    dna.wasOriginalShapeLeaf(blockKey),
                    dna.countsAsEvolvedLeaf(blockKey));
        }
        return materialMatches || compatibleOrganic;
    }

    private boolean isCompatibleOrganicOccupant(
            TreeBlockRole role, Material live) {
        boolean wood = live.name().endsWith("_LOG")
                || live.name().endsWith("_WOOD")
                || live == Material.MANGROVE_ROOTS
                || live == Material.MUDDY_MANGROVE_ROOTS;
        if (role == TreeBlockRole.CANOPY) {
            return wood || live.name().endsWith("_LEAVES");
        }
        return (role == TreeBlockRole.TRUNK
                || role == TreeBlockRole.BRANCH
                || role == TreeBlockRole.ROOT) && wood;
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

    private TreeGrowthIntent preferredIntent(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig currentConfig
    ) {
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
            int seedlingWeight = reproductionWeight(dna, currentConfig);
            return weightedIntent(random, TreeGrowthIntent.CANOPY, 54,
                    TreeGrowthIntent.BRANCH, 26,
                    TreeGrowthIntent.HEIGHT, 14,
                    TreeGrowthIntent.DETAIL, 6,
                    TreeGrowthIntent.SEEDLING, seedlingWeight);
        }
        if (dna.maturityStage() == TreeMaturityStage.MATURE || dna.maturityStage() == TreeMaturityStage.ANCIENT) {
            int seedlingWeight = reproductionWeight(dna, currentConfig);
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
        if (plan.prunedBranchCount() > 0) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "shape.plan.branch-envelope-pruned",
                    "tree=" + dna.key()
                            + " rejected-branches=" + plan.prunedBranchCount()
                            + " accepted-branches=" + plan.branchPlans().size()
                            + " ## branch candidates without a connected preplanned leaf envelope are removed before live growth");
        }
        List<PlannedTreeBlock> orderedBlocks = plan.orderedBlocks();
        Map<String, PlannedTreeBlock> blocksByKey = new HashMap<>();
        for (PlannedTreeBlock block : orderedBlocks) {
            blocksByKey.put(block.key(), block);
        }
        CachedTreePlan fresh = new CachedTreePlan(signature, plan, orderedBlocks, blocksByKey);
        planCache.put(dna.key(), fresh);
        projectionProgressCache.remove(dna.key());
        liveTerminalAuditCache.remove(dna.key());
        return fresh;
    }

    // ## TRANSITION RECONCILER
    // A source leaf may become planned wood only when the exact target and its
    // parent dependency are ready. This avoids an AIR frame and prevents a
    // cleanup pass from outrunning the replacement structure.
    private Optional<PlannedTarget> readyTransitionBlocker(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig
    ) {
        List<Block> blockers = findStaleCanopyLeaves(
                candidate, dna, cachedPlan.orderedBlocks(), 64,
                currentConfig, true);
        int size = Math.max(1, cachedPlan.orderedBlocks().size());
        for (Block blocker : blockers) {
            String coordinateKey = blocker.getX() + ":" + blocker.getY()
                    + ":" + blocker.getZ();
            PlannedTreeBlock planned = cachedPlan.blocksByKey().get(coordinateKey);
            if (planned == null
                    || (planned.role() != TreeBlockRole.TRUNK
                            && planned.role() != TreeBlockRole.BRANCH)) {
                continue;
            }
            if (!hasPreplannedBranchEnvelope(
                    dna, planned, cachedPlan, currentConfig)) {
                continue;
            }
            TreeGrowthIntent intent = planned.role() == TreeBlockRole.TRUNK
                    ? TreeGrowthIntent.HEIGHT : TreeGrowthIntent.BRANCH;
            if (!isDependencyReady(candidate, dna, blocker, planned,
                    intent, currentConfig, false)
                    || !canPlace(candidate, dna, blocker, planned,
                            currentConfig)) {
                continue;
            }
            int index = cachedPlan.orderedBlocks().indexOf(planned);
            int nextCursor = index < 0 ? dna.planCursor()
                    : (index + 1) % size;
            return Optional.of(new PlannedTarget(
                    planned, blocker, nextCursor, 0.0D,
                    "constructor.atomic-transition-blocker"));
        }
        return Optional.empty();
    }

    private boolean replaceTransitionBlocker(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig,
            PlannedTarget transitionBlocker
    ) {
        PlannedTreeBlock planned = transitionBlocker.block();
        Block target = transitionBlocker.target();
        if (target.getType() != dna.species().leafMaterial()
                || !canPlace(candidate, dna, target, planned, currentConfig)) {
            return false;
        }
        TreeGrowthIntent intent = planned.role() == TreeBlockRole.TRUNK
                ? TreeGrowthIntent.HEIGHT : TreeGrowthIntent.BRANCH;
        place(target, planned);
        if (dna.markEvolvedBlock(keyFor(target), planned.role())) {
            markTreeDnaDirty("recorded evolved transition " + planned.role()
                    + " " + keyFor(target));
        }
        dna.markPlacedForIntent(intent, transitionBlocker.nextCursor());
        changedBlocks.incrementAndGet();
        projectionProgressCache.remove(dna.key());
        liveTerminalAuditCache.remove(dna.key());
        diagnostics.recordPlaced(plugin, currentConfig, target, planned);
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "constructor.atomic-transition-blocker",
                "[CONSTRUCTOR][REPLACE_TRANSITION_BLOCKER]"
                        + "[TRANSITION_RECONCILER] tree=" + dna.key()
                        + " role=" + planned.role()
                        + " at=" + format(target)
                        + " ## source leaf became ready planned wood in one world change");
        return true;
    }

    private void reconcileSourceLeafLedger(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan
    ) {
        if (!dna.hasOriginalShapeSnapshot()) {
            return;
        }
        int adopted = 0;
        int absent = 0;
        String firstUnresolved = null;
        for (String sourceLeafKey : dna.originalShapeLeaves()) {
            if (dna.retiredOriginalShapeLeaves().contains(sourceLeafKey)
                    || dna.countsAsEvolvedLeaf(sourceLeafKey)) {
                continue;
            }
            Optional<Block> sourceLeaf =
                    blockFromKey(candidate.world(), sourceLeafKey);
            if (sourceLeaf.isEmpty()) {
                continue;
            }
            Block block = sourceLeaf.get();
            if (!isReadableTreeCoordinate(
                    candidate.world(), block.getX(), block.getZ())) {
                continue;
            }
            if (block.getType() != dna.species().leafMaterial()) {
                if (dna.markOriginalShapeLeafRetired(sourceLeafKey)) {
                    absent++;
                }
                continue;
            }
            PlannedTreeBlock planned = cachedPlan.blocksByKey().get(
                    block.getX() + ":" + block.getY() + ":" + block.getZ());
            if (planned != null
                    && planned.role() == TreeBlockRole.CANOPY
                    && planned.material() == block.getType()
                    && dna.markEvolvedLeaf(sourceLeafKey)) {
                adopted++;
                continue;
            }
            if (firstUnresolved == null) {
                firstUnresolved = sourceLeafKey;
            }
        }
        if (adopted <= 0 && absent <= 0) {
            return;
        }
        markTreeDnaDirty("source leaf ledger reconcile " + dna.key());
        projectionProgressCache.remove(dna.key());
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "state.source-leaf-reconcile",
                "tree=" + dna.key()
                        + " adopted-target=" + adopted
                        + " already-absent=" + absent
                        + " unresolved="
                        + dna.unresolvedOriginalShapeLeafCount()
                        + (firstUnresolved == null
                                ? ""
                                : " first-unresolved=" + firstUnresolved)
                        + " ## source evidence remains persisted until every original leaf is adopted or retired");
    }
    private List<Block> findRetiredCanopyLeaves(
            TreeCandidate candidate,
            TreeDna dna,
            CachedTreePlan cachedPlan,
            int limit,
            TreeEvolutionConfig currentConfig
    ) {
        List<Block> stale = findStaleCanopyLeaves(
                candidate, dna, cachedPlan.orderedBlocks(),
                Math.max(limit * 4, 16), currentConfig, false);
        List<Block> retired = new ArrayList<>();
        for (Block leaf : stale) {
            PlannedTreeBlock planned = cachedPlan.blocksByKey().get(
                    leaf.getX() + ":" + leaf.getY() + ":" + leaf.getZ());
            if (planned != null && (planned.role() == TreeBlockRole.TRUNK
                    || planned.role() == TreeBlockRole.BRANCH
                    || planned.role() == TreeBlockRole.ROOT)) {
                continue;
            }
            retired.add(leaf);
            if (retired.size() >= limit) {
                break;
            }
        }
        return List.copyOf(retired);
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

        // ## Active-tree planned wood may replace only its own saved source leaves.
        // Neighboring planned crowns remain protected by collectStaleCanopyLeaf.
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
        // ## Neighbor ownership outranks this tree's transition plan. An active
        // tree must route around a neighboring planned crown instead of deleting it.
        boolean nearbyCrownOwnsLeaf = protectedCanopyKeys.contains(coordinateKey);
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
    private int runReservedSeedlingSearch(
            UUID playerId,
            Location origin,
            TreeEvolutionConfig currentConfig
    ) {
        TreeReproductionConfig reproduction = currentConfig.reproduction();
        if (!reproduction.enabled()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long nextSearch = nextReservedSeedlingSearchMillis.getOrDefault(
                playerId, 0L);
        if (now < nextSearch) {
            return 0;
        }
        nextReservedSeedlingSearchMillis.put(playerId,
                now + reproduction.reservedSearchIntervalMillis());

        try (ReportSample sample = plugin.resourceReporter().begin(
                "tree-evolution", "action.reserved-seedling-search")) {
            World world = origin.getWorld();
            if (world == null) {
                sample.detail("missing-world");
                return 0;
            }
            int searchRadius = currentConfig.searchRadius();
            long searchRadiusSquared = (long) searchRadius * searchRadius;
            List<TreeDna> eligible = new ArrayList<>();
            for (TreeDna dna : treeDna.values()) {
                if (!world.getUID().equals(dna.worldId())
                        || !reproduction.eligible(dna, now,
                                seedlingCooldownUntil.getOrDefault(
                                        dna.key(), 0L))) {
                    continue;
                }
                long dx = (long) dna.baseX() - origin.getBlockX();
                long dz = (long) dna.baseZ() - origin.getBlockZ();
                if ((dx * dx) + (dz * dz) > searchRadiusSquared) {
                    continue;
                }
                int chunkX = dna.baseX() >> 4;
                int chunkZ = dna.baseZ() >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ)
                        || !Bukkit.isOwnedByCurrentRegion(
                                world, chunkX, chunkZ, 0)) {
                    continue;
                }
                eligible.add(dna);
            }
            eligible.sort(Comparator.comparing(TreeDna::key));
            if (eligible.isEmpty()) {
                plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                        "seedling.lane.no-eligible-parent",
                        "near=" + format(origin)
                                + " ## no loaded, owned, healthy tree meets the reproduction stage/age gate");
                sample.detail("no-eligible-parent");
                return 0;
            }

            long passSequence = reservedSeedlingPassSequence.merge(
                    playerId, 1L, Long::sum);
            int start = (int) Math.floorMod(
                    passSequence + playerId.getLeastSignificantBits(),
                    eligible.size());
            int candidateRolls = 0;
            for (int offset = 0;
                    offset < eligible.size()
                            && candidateRolls < reproduction.candidateRollsPerPass();
                    offset++) {
                TreeDna dna = eligible.get((start + offset) % eligible.size());
                Block base = world.getBlockAt(
                        dna.baseX(), dna.baseY(), dna.baseZ());
                if (base.getType() != dna.species().logMaterial()) {
                    continue;
                }
                candidateRolls++;
                double chance = reproduction.chanceFor(dna);
                Random chanceRoll = new Random(dna.seed()
                        ^ (passSequence * 0xD1342543DE82EF95L)
                        ^ playerId.getMostSignificantBits());
                if (chance <= 0.0D || chanceRoll.nextDouble() >= chance) {
                    plugin.pathDebug().traceSampled(plugin,
                            "tree-evolution", "seedling.lane.chance-miss",
                            "tree=" + dna.key() + " chance="
                                    + Math.round(chance * 1000.0D) / 10.0D
                                    + "% roll=" + candidateRolls + "/"
                                    + reproduction.candidateRollsPerPass());
                    continue;
                }
                if (!canWorkAt(base.getLocation(), currentConfig)) {
                    plugin.pathDebug().traceSampled(plugin,
                            "tree-evolution", "seedling.lane.parent-gate",
                            "tree=" + dna.key()
                                    + " ## parent is outside the current safe Folia/player/protection area");
                    continue;
                }

                plugin.pathDebug().trace(plugin, "tree-evolution",
                        "seedling.lane.search",
                        "tree=" + dna.key() + " pass=" + passSequence
                                + " candidates=" + eligible.size()
                                + " rolls=" + candidateRolls
                                + " attempts=" + reproduction.searchAttempts());
                Optional<Block> target = findSeedlingSpot(
                        world, dna, currentConfig);
                sample.workUnits(candidateRolls
                        + reproduction.searchAttempts());
                if (target.isEmpty()) {
                    sample.detail("search-exhausted tree=" + dna.key());
                    return 0;
                }
                spreadSeedling(target.get(), dna, currentConfig,
                        "reserved-lane");
                plugin.pathDebug().trace(plugin, "tree-evolution",
                        "seedling.lane.spread",
                        "tree=" + dna.key() + " pass=" + passSequence
                                + " ## independently paced reproduction placed one sapling without consuming structural budget");
                sample.changedUnits(1).detail(
                        "spread tree=" + dna.key());
                return 1;
            }

            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "seedling.lane.no-chance-pass",
                    "eligible=" + eligible.size() + " rolls="
                            + candidateRolls + "/"
                            + reproduction.candidateRollsPerPass());
            sample.workUnits(candidateRolls).detail("no-chance-pass");
            return 0;
        }
    }
    private int reproductionWeight(
            TreeDna dna,
            TreeEvolutionConfig currentConfig
    ) {
        TreeReproductionConfig reproduction = currentConfig.reproduction();
        long now = System.currentTimeMillis();
        if (!reproduction.eligible(dna, now,
                seedlingCooldownUntil.getOrDefault(dna.key(), 0L))) {
            return 0;
        }
        double chance = reproduction.chanceFor(dna);
        return chance <= 0.0D
                ? 0
                : Math.max(1, (int) Math.round(chance * 100.0D));
    }

    private Optional<Block> findSeedlingSpot(
            World world,
            TreeDna dna,
            TreeEvolutionConfig currentConfig
    ) {
        TreeReproductionConfig reproduction = currentConfig.reproduction();
        long now = System.currentTimeMillis();
        if (!reproduction.eligible(dna, now,
                seedlingCooldownUntil.getOrDefault(dna.key(), 0L))) {
            return Optional.empty();
        }

        long sequence = seedlingSearchSequence.merge(
                dna.key(), 1L, Long::sum);
        long searchSeed = dna.seed()
                ^ (dna.age() * 31L)
                ^ (sequence * 0x9E3779B97F4A7C15L)
                ^ 0x5EEDL;
        int futureCanopyRadius = projectedSeedlingCanopyRadius(dna.species());
        int radius = reproduction.radiusFor(dna);
        int minimumRadius = Math.min(radius, Math.max(
                reproduction.minimumRadius(),
                TreeSeedlingSearchPolicy.requiredBaseDistance(
                        Math.max(TreeSpeciesStageStyle.canopyRadiusX(dna),
                                TreeSpeciesStageStyle.canopyRadiusZ(dna)),
                        futureCanopyRadius)));
        List<TreeSeedlingSearchPolicy.Offset> offsets =
                TreeSeedlingSearchPolicy.sampleRing(
                        minimumRadius, radius,
                        reproduction.searchAttempts(), searchSeed);
        int regionRejects = 0;
        int surfaceRejects = 0;
        int lightRejects = 0;
        int spacingRejects = 0;
        int protectionRejects = 0;
        for (TreeSeedlingSearchPolicy.Offset offset : offsets) {
            int x = dna.baseX() + offset.x();
            int z = dna.baseZ() + offset.z();
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)
                    || !Bukkit.isOwnedByCurrentRegion(
                            world, chunkX, chunkZ, 0)) {
                regionRejects++;
                continue;
            }

            Block ground = world.getHighestBlockAt(
                    x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block surface = ground.getRelative(BlockFace.UP);
            if (!canReplaceForSeedling(surface)
                    || !NATURAL_GROUND.contains(ground.getType())) {
                surfaceRejects++;
                continue;
            }
            if (surface.getLightFromSky() < 9) {
                lightRejects++;
                continue;
            }
            int liveSpacingRadius = Math.max(
                    reproduction.spacingRadius(),
                    Math.min(8, futureCanopyRadius + 2));
            if (nearExistingSaplingOrLog(surface, liveSpacingRadius)
                    || nearKnownTreeFootprint(
                            world, surface, futureCanopyRadius)) {
                spacingRejects++;
                continue;
            }
            if (!plugin.canEvolveAt(surface.getLocation(),
                    "tree-reproduction")) {
                protectionRejects++;
                continue;
            }
            if (!canClearSeedlingVegetation(surface)) {
                protectionRejects++;
                continue;
            }
            plugin.pathDebug().trace(plugin, "tree-evolution",
                    "seedling.search-pass",
                    "tree=" + dna.key() + " sequence=" + sequence
                            + " ring=" + minimumRadius + ".." + radius
                            + " target=" + format(surface)
                            + " replacing=" + surface.getType());
            return Optional.of(surface);
        }

        String summary = "tree=" + dna.key()
                + " sequence=" + sequence
                + " ring=" + minimumRadius + ".." + radius
                + " attempts=" + offsets.size()
                + " sampled-ring=" + offsets.size()
                + " region=" + regionRejects
                + " surface=" + surfaceRejects
                + " light=" + lightRejects
                + " spacing=" + spacingRejects
                + " protection=" + protectionRejects;
        diagnostics.recordReject(currentConfig,
                "seedling-search-exhausted", summary);
        plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                "seedling.search-exhausted",
                summary + " ## the next retry uses a fresh deterministic sequence");
        return Optional.empty();
    }
    private boolean nearKnownTreeFootprint(
            World world,
            Block target,
            int futureCanopyRadius
    ) {
        for (TreeDna existing : treeDna.values()) {
            if (!existing.stumpPresent()
                    || !world.getUID().equals(existing.worldId())) {
                continue;
            }
            int deltaX = target.getX() - existing.baseX();
            int deltaZ = target.getZ() - existing.baseZ();
            int existingRadius = Math.max(
                    TreeSpeciesStageStyle.canopyRadiusX(existing),
                    TreeSpeciesStageStyle.canopyRadiusZ(existing));
            if (TreeSeedlingSearchPolicy.footprintsOverlap(
                    deltaX, deltaZ, existingRadius,
                    futureCanopyRadius)) {
                return true;
            }
        }
        return false;
    }

    private int projectedSeedlingCanopyRadius(TreeSpecies species) {
        return switch (species) {
            case BIRCH -> 3;
            case OAK, SPRUCE, ACACIA, CHERRY -> 4;
            case JUNGLE, DARK_OAK, MANGROVE -> 5;
        };
    }

    private void spreadSeedling(
            Block sapling,
            TreeDna dna,
            TreeEvolutionConfig currentConfig,
            String source
    ) {
        Material replaced = sapling.getType();
        clearSeedlingVegetation(sapling);
        sapling.setType(dna.species().saplingMaterial(), false);
        changedBlocks.incrementAndGet();
        diagnostics.recordSeedling(plugin, currentConfig, sapling, dna);
        seedlingCooldownUntil.put(dna.key(),
                System.currentTimeMillis()
                        + currentConfig.reproduction().cooldownMillis());
        plugin.pathDebug().trace(plugin, "tree-evolution",
                "seedling.spread",
                dna.species().saplingMaterial()
                        + " child-of=" + dna.key()
                        + " source=" + source
                        + " replaced=" + replaced
                        + " at " + format(sapling));
    }
    private boolean canReplaceForSeedling(Block surface) {
        return surface.getType().isAir()
                || NATURAL_DETAILS.contains(surface.getType());
    }

    private boolean canClearSeedlingVegetation(Block surface) {
        BlockData data = surface.getBlockData();
        if (!(data instanceof Bisected bisected)) {
            return true;
        }
        Block companion = bisected.getHalf() == Bisected.Half.TOP
                ? surface.getRelative(BlockFace.DOWN)
                : surface.getRelative(BlockFace.UP);
        return companion.getType() != surface.getType()
                || (isOwnedLoaded(companion)
                        && plugin.canEvolveAt(
                                companion.getLocation(),
                                "tree-reproduction"));
    }

    private void clearSeedlingVegetation(Block surface) {
        BlockData data = surface.getBlockData();
        if (!(data instanceof Bisected bisected)) {
            return;
        }
        Block companion = bisected.getHalf() == Bisected.Half.TOP
                ? surface.getRelative(BlockFace.DOWN)
                : surface.getRelative(BlockFace.UP);
        if (companion.getType() == surface.getType()) {
            companion.setType(Material.AIR, false);
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "seedling.replace-tall-vegetation",
                    "cleared=" + surface.getType()
                            + " companion=" + format(companion)
                            + " ## both halves are cleared before the sapling is placed");
        }
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

    private void reconcileStageWithSourceHeight(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig currentConfig
    ) {
        if (!dna.hasOriginalShapeSnapshot()) {
            return;
        }
        int observedHeight = Math.max(
                candidate.height(),
                liveTrunkHeight(candidate.world(), dna));
        int advanced = 0;
        while (TreeSourceStagePolicy.shouldAdvance(
                dna.maturityStage(), currentConfig.maximumStage(),
                observedHeight, TreeSpeciesStageStyle.visibleHeight(dna))) {
            TreeMaturityStage before = dna.maturityStage();
            int formerStageHeight = TreeSpeciesStageStyle.visibleHeight(dna);
            if (!dna.advanceMaturity()) {
                break;
            }
            advanced++;
            diagnostics.recordStageTransition(
                    currentConfig, dna, before, dna.maturityStage(),
                    "source-height-reconcile observed=" + observedHeight
                            + " former-stage-height=" + formerStageHeight
                            + " ## an existing trunk cannot be forced backward into a shorter stage");
        }
        if (advanced <= 0) {
            return;
        }
        projectionProgressCache.remove(dna.key());
        liveTerminalAuditCache.remove(dna.key());
        markTreeDnaDirty("source-height stage reconcile " + dna.key());
        saveTreeDna();
        plugin.pathDebug().trace(
                plugin, "tree-evolution", "state.source-height-stage-reconcile",
                "tree=" + dna.key()
                        + " observed-height=" + observedHeight
                        + " advanced=" + advanced
                        + " stage=" + dna.maturityStage()
                        + " planned-height="
                        + TreeSpeciesStageStyle.visibleHeight(dna)
                        + " ## source-size reconciliation runs before constructor routing");
    }

    private void updateMaturity(TreeCandidate candidate, TreeDna dna, TreeEvolutionConfig currentConfig) {
        int current = Math.max(candidate.height(), liveTrunkHeight(candidate.world(), dna));
        TreeMaturityStage before = dna.maturityStage();
        if (before.ordinal() >= currentConfig.maximumStage().ordinal()) {
            return;
        }
        TreeWorkStatus stageStatus = treeWorkStatus(
                candidate, dna, currentConfig);
        if (!TreeFocusPolicy.readyForMaturity(
                stageStatus.stageComplete(), dna.stageCleanupBurst(),
                dna.stageGrowthBurst(), dna.hasOriginalShapeSnapshot())) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "stage.wait.structure-complete",
                    "tree=" + dna.key() + " stage=" + before
                            + " " + stageStatus.summary()
                            + " cleanup=" + dna.stageCleanupBurst()
                            + " growth=" + dna.stageGrowthBurst()
                            + " snapshot=" + dna.hasOriginalShapeSnapshot()
                            + " ## age and height cannot advance maturity before the current projected stage is structurally complete");
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
        if (dna.hasOriginalShapeSnapshot()
                && dna.originalShapeCaptureIsCurrent()) {
            return true;
        }
        // ## Snapshot ownership always uses the authoritative full-crown walk.
        // A six-face candidate can be complete for scheduling while still missing
        // diagonally attached foliage from a fancy vanilla tree.
        TreeCandidate source =
                buildCandidate(candidate.baseBlock(), true).orElse(null);
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
        boolean expanding = dna.hasOriginalShapeSnapshot();
        if (expanding) {
            dna.expandOriginalShape(logs, leaves);
        } else {
            dna.captureOriginalShape(logs, leaves);
        }
        markTreeDnaDirty("original shape capture " + dna.key());
        saveTreeDna();
        plugin.pathDebug().trace(plugin, "tree-evolution",
                expanding
                        ? "state.original-shape-expand"
                        : "state.original-shape-capture",
                "tree=" + dna.key() + " blocks="
                        + dna.originalShapeBlockCount()
                        + " scan-logs=" + logs.size()
                        + " scan-leaves=" + leaves.size()
                        + " unresolved="
                        + dna.unresolvedOriginalShapeLeafCount()
                        + " reason=" + reason
                        + " ## exact full-crown source leaves remain authoritative until target completion and pruning both pass");
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

    private boolean hasPreplannedBranchEnvelope(
            TreeDna dna, PlannedTreeBlock plannedBlock, CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig) {
        if (plannedBlock.role() != TreeBlockRole.BRANCH) {
            return true;
        }
        Optional<TreeBranchPlan.BranchTip> tip = cachedPlan.plan().branchPlans().stream()
                .filter(branch -> branch.id() == plannedBlock.branchId())
                .map(TreeBranchPlan::tip)
                .findFirst();
        boolean valid = tip.isPresent()
                && TreeBranchTipIntegrityPolicy.hasPreplannedEnvelope(
                        dna, tip.get().x(), tip.get().y(), tip.get().z(),
                        cachedPlan.blocksByKey());
        if (!valid) {
            diagnostics.recordReject(currentConfig, "branch-envelope-unplanned",
                    dna.key() + " branch=" + plannedBlock.branchId()
                            + " step=" + plannedBlock.branchStep());
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "blocked.branch-envelope-unplanned",
                    "tree=" + dna.key()
                            + " branch=" + plannedBlock.branchId()
                            + " step=" + plannedBlock.branchStep()
                            + " tip=" + tip.map(value -> value.x() + "," + value.y() + "," + value.z())
                                    .orElse("missing")
                            + " ## branch wood cannot form until its connected leaf envelope exists in the target plan");
        }
        return valid;
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

    private Optional<Block> findExposedUpperLog(TreeCandidate candidate, TreeDna dna,
            Map<String, PlannedTreeBlock> blocksByKey) {
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
                    if (block.getType() == dna.species().logMaterial()
                            && TreeCanopyIntegrityPolicy.requiresCanopyCover(
                                    block.getX(), block.getY(), block.getZ(),
                                    dna.species().leafMaterial(), blocksByKey)
                            && adjacentPlannedLeafContacts(
                                    world, dna, block,
                                    dna.species().leafMaterial(),
                                    blocksByKey) == 0) {
                        return Optional.of(block);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private int maybeCoverExposedTopLog(TreeCandidate candidate, TreeDna dna,
            TreeEvolutionConfig currentConfig, Block trunk,
            PlannedTreeBlock plannedBlock,
            Map<String, PlannedTreeBlock> blocksByKey) {
        if (plannedBlock.role() != TreeBlockRole.TRUNK || trunk.getY() < candidate.topY()) {
            return 0;
        }
        return coverExposedLog(candidate, dna, currentConfig, trunk,
                blocksByKey);
    }

    private int coverBranchTip(TreeCandidate candidate, TreeDna dna,
            TreeEvolutionConfig currentConfig, Block tip, int requiredContacts,
            int requiredCluster, Map<String, PlannedTreeBlock> blocksByKey) {
        Material leafMaterial = dna.species().leafMaterial();
        int currentContacts = adjacentPlannedLeafContacts(
                candidate.world(), dna, tip, leafMaterial, blocksByKey);
        int currentCluster = plannedEnvelopeLiveLeaves(
                candidate.world(), dna, tip, leafMaterial, blocksByKey);
        if (currentContacts >= requiredContacts
                && currentCluster >= requiredCluster
                && hasNaturalLiveEnvelope(
                        candidate.world(), dna, tip, leafMaterial, blocksByKey)) {
            return 0;
        }

        int placementLimit = 3;
        int placed = 0;
        int missingContacts = Math.max(0, requiredContacts - currentContacts);
        List<BlockFace> faces = List.of(
                BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN);
        int offset = Math.floorMod(
                (tip.getX() * 31) ^ (tip.getY() * 17) ^ (tip.getZ() * 13),
                faces.size());
        for (int index = 0; index < faces.size()
                && placed < placementLimit
                && placed < missingContacts; index++) {
            Block leaf = tip.getRelative(faces.get((index + offset) % faces.size()));
            if (reformOrPlaceOwnedCanopyLeaf(
                    candidate, dna, currentConfig, tip, leaf,
                    leafMaterial, blocksByKey, "branch-envelope-contact")) {
                placed++;
            }
        }

        int updatedCluster = plannedEnvelopeLiveLeaves(
                candidate.world(), dna, tip, leafMaterial, blocksByKey);
        if (placed < placementLimit
                && (updatedCluster < requiredCluster
                        || !hasNaturalLiveEnvelope(
                                candidate.world(), dna, tip,
                                leafMaterial, blocksByKey))) {
            List<PlannedTreeBlock> envelope = new ArrayList<>();
            for (PlannedTreeBlock planned : blocksByKey.values()) {
                if (planned.role() != TreeBlockRole.CANOPY
                        || planned.material() != leafMaterial
                        || Math.abs(planned.x() - tip.getX()) > 2
                        || Math.abs(planned.y() - tip.getY()) > 1
                        || Math.abs(planned.z() - tip.getZ()) > 2) {
                    continue;
                }
                envelope.add(planned);
            }
            envelope.sort(Comparator
                    .comparingInt((PlannedTreeBlock planned) ->
                            branchEnvelopePlacementPriority(tip, planned))
                    .thenComparing(PlannedTreeBlock::key));
            for (PlannedTreeBlock planned : envelope) {
                if (placed >= placementLimit
                        || (updatedCluster >= requiredCluster
                                && hasNaturalLiveEnvelope(
                                        candidate.world(), dna, tip,
                                        leafMaterial, blocksByKey))) {
                    break;
                }
                Block leaf = candidate.world().getBlockAt(
                        planned.x(), planned.y(), planned.z());
                if (reformOrPlaceOwnedCanopyLeaf(
                        candidate, dna, currentConfig, tip, leaf,
                        leafMaterial, blocksByKey, "branch-envelope-cluster")) {
                    placed++;
                    updatedCluster = plannedEnvelopeLiveLeaves(
                            candidate.world(), dna, tip, leafMaterial,
                            blocksByKey);
                }
            }
        }
        if (placed > 0) {
            dna.setCurrentIntent(TreeGrowthIntent.CANOPY);
            int finalContacts = adjacentPlannedLeafContacts(
                    candidate.world(), dna, tip, leafMaterial, blocksByKey);
            int finalCluster = plannedEnvelopeLiveLeaves(
                    candidate.world(), dna, tip, leafMaterial, blocksByKey);
            plugin.pathDebug().trace(plugin, "tree-evolution",
                    "canopy.branch-envelope-attach",
                    "tip=" + format(tip)
                            + " leaf=" + leafMaterial
                            + " owned-contacts=" + finalContacts + "/" + requiredContacts
                            + " owned-envelope=" + finalCluster + "/" + requiredCluster
                            + " ownership-version=" + dna.evolutionOwnershipVersion()
                            + " ## branch envelope grows from leaves explicitly placed or reformed by this evolution epoch");
        } else {
            diagnostics.recordReject(currentConfig, "branch-envelope-space",
                    "tip=" + format(tip)
                            + " owned-contacts=" + currentContacts + "/" + requiredContacts
                            + " owned-envelope=" + currentCluster + "/" + requiredCluster
                            + " no safe connected owned planned leaf space");
        }
        return placed;
    }

    private int branchEnvelopePlacementPriority(
            Block tip, PlannedTreeBlock planned) {
        int dx = Math.abs(planned.x() - tip.getX());
        int dy = Math.abs(planned.y() - tip.getY());
        int dz = Math.abs(planned.z() - tip.getZ());
        // ## Build vertical and side volume before filling the middle. This keeps
        // a branch crown cloud-like throughout construction instead of flat first.
        int volumeBias = dy > 0 ? -12 : 0;
        int sideBias = dx > 0 && dz > 0 ? -4 : 0;
        return volumeBias + sideBias + dx + dy + dz;
    }
    private int coverExposedLog(TreeCandidate candidate, TreeDna dna,
            TreeEvolutionConfig currentConfig, Block trunk,
            Map<String, PlannedTreeBlock> blocksByKey) {
        Material leafMaterial = dna.species().leafMaterial();
        if (adjacentPlannedLeafContacts(
                candidate.world(), dna, trunk, leafMaterial, blocksByKey) > 0) {
            return 0;
        }

        int desiredLeaves = switch (dna.maturityStage()) {
            case SMALL -> 4;
            case MEDIUM -> 4;
            case MATURE -> dna.hugeArchitecture() ? 6 : 5;
            case ANCIENT -> dna.hugeArchitecture() ? 8 : 6;
        };
        int placed = 0;
        List<BlockFace> faces = List.of(
                BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN);
        int offset = Math.floorMod(
                (trunk.getX() * 31) ^ (trunk.getY() * 17)
                        ^ (trunk.getZ() * 13),
                faces.size());
        for (int index = 0; index < faces.size()
                && placed < desiredLeaves; index++) {
            BlockFace face = faces.get((index + offset) % faces.size());
            Block leaf = trunk.getRelative(face);
            if (reformOrPlaceOwnedCanopyLeaf(
                    candidate, dna, currentConfig, trunk, leaf,
                    leafMaterial, blocksByKey, "canopy-shell")) {
                placed++;
            }
        }
        if (placed > 0) {
            dna.setCurrentIntent(TreeGrowthIntent.CANOPY);
            diagnostics.recordCanopyLift(plugin, currentConfig, trunk, dna, placed);
            plugin.pathDebug().trace(plugin, "tree-evolution", "canopy.lift-cover",
                    "trunk=" + format(trunk)
                            + " leaf=" + leafMaterial
                            + " owned=" + placed
                            + " ## canopy shell explicitly reforms or places leaves around exposed live support");
        } else {
            diagnostics.recordReject(currentConfig, "canopy-lift-space",
                    "exposed trunk=" + format(trunk)
                            + " no safe adjacent owned leaf space");
        }
        return placed;
    }

    private boolean reformOrPlaceOwnedCanopyLeaf(
            TreeCandidate candidate, TreeDna dna,
            TreeEvolutionConfig currentConfig, Block support, Block leaf,
            Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey, String reason) {
        String coordinateKey = leaf.getX() + ":" + leaf.getY()
                + ":" + leaf.getZ();
        PlannedTreeBlock planned = blocksByKey.get(coordinateKey);
        if (planned == null
                || planned.role() != TreeBlockRole.CANOPY
                || planned.material() != leafMaterial) {
            return false;
        }

        String ownershipKey = keyFor(leaf);
        if (leaf.getType() == leafMaterial
                && dna.countsAsEvolvedLeaf(ownershipKey)) {
            return false;
        }
        if (!canPlaceCanopyLiftLeaf(
                leaf, support, dna, currentConfig, blocksByKey)) {
            return false;
        }

        boolean preexistingLeaf = leaf.getType() == leafMaterial;
        boolean originalLeaf = dna.isOriginalShapeLeaf(ownershipKey);
        boolean sourceReform = TreeBranchEnvelopeOwnershipPolicy
                .shouldReformOriginalLeaf(
                        true, originalLeaf,
                        dna.countsAsEvolvedLeaf(ownershipKey));
        placePersistentLeaf(leaf, leafMaterial);
        dna.markEvolvedLeaf(ownershipKey);
        changedBlocks.incrementAndGet();
        markTreeDnaDirty("owned canopy leaf " + ownershipKey);
        projectionProgressCache.remove(dna.key());
        liveTerminalAuditCache.remove(dna.key());
        plugin.pathDebug().trace(plugin, "tree-evolution",
                preexistingLeaf
                        ? "audit.branch-envelope-leaf-reformed"
                        : "canopy.branch-envelope-leaf-placed",
                "tree=" + dna.key()
                        + " reason=" + reason
                        + " support=" + format(support)
                        + " leaf=" + format(leaf)
                        + " original=" + originalLeaf
                        + " source-reform=" + sourceReform
                        + " ownership-version=" + dna.evolutionOwnershipVersion()
                        + " ## only explicitly evolved canopy leaves may satisfy the constructor hierarchy");
        return true;
    }
    private boolean canPlaceCanopyLiftLeaf(Block leaf, Block trunk, TreeDna dna,
            TreeEvolutionConfig currentConfig,
            Map<String, PlannedTreeBlock> blocksByKey) {
        PlannedTreeBlock planned = blocksByKey.get(
                leaf.getX() + ":" + leaf.getY() + ":" + leaf.getZ());
        if (planned == null || planned.role() != TreeBlockRole.CANOPY
                || planned.material() != dna.species().leafMaterial()) {
            return false;
        }
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
        return touchesBlock(leaf, trunk)
                || hasDirectWoodNeighbor(leaf)
                || hasAdjacentLeaf(leaf, dna.species().leafMaterial());
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
            for (int[] offset : TreeGroupTraversalPolicy.neighborOffsets(
                    thoroughOwnershipScan)) {
                Block relative = block.getRelative(
                        offset[0], offset[1], offset[2]);
                int distance = Math.abs(relative.getX() - base.getX())
                        + Math.abs(relative.getY() - base.getY())
                        + Math.abs(relative.getZ() - base.getZ());
                if (distance <= TreeGroupTraversalPolicy.maximumDistance(
                        TREE_GROUP_MAX_DISTANCE, thoroughOwnershipScan)
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
                liveTerminalAuditCache.remove(dna.key());
                focusYieldUntil.remove(dna.key());
                seedlingCooldownUntil.remove(dna.key());
                seedlingSearchSequence.remove(dna.key());
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
            liveTerminalAuditCache.remove(dna.key());
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

    private record CachedLiveTerminalAudit(
            String signature, LiveTerminalAudit audit, long expiresMillis) {
    }

    private record LiveTerminalAudit(
            int unplannedBareTips,
            Block firstUnplannedBareTip,
            int stalePersistentEnvelopeLeaves,
            Block firstStalePersistentEnvelopeLeaf) {
        private static final LiveTerminalAudit NONE =
                new LiveTerminalAudit(0, null, 0, null);
    }

    private record StaleEnvelopeLeafAudit(int count, Block first) {
    }

    private record TreeWorkStatus(
            boolean needsFocus,
            boolean stageComplete,
            boolean transitionPending,
            boolean sourceSnapshot,
            int sourceBlocks,
            int unresolvedSourceLeaves,
            TreeGrowthQueuePolicy.Completion completion,
            TreeGrowthQueuePolicy.Budget budget,
            int exposedUpperLogs,
            int uncoveredBranchTips
    ) {
        String summary() {
            return "stage-complete=" + stageComplete
                    + " transition-pending=" + transitionPending
                    + " source-snapshot=" + sourceSnapshot
                    + " source-blocks=" + sourceBlocks
                    + " unresolved-source-leaves="
                    + unresolvedSourceLeaves
                    + " trunk=" + completion.trunkSummary()
                    + " branch=" + completion.branchSummary()
                    + " canopy=" + completion.canopySummary()
                    + " budget=" + percent(budget.trunkPercent())
                    + "/" + percent(budget.branchPercent())
                    + "/" + percent(budget.canopyPercent())
                    + " exposed-upper-logs=" + exposedUpperLogs
                    + " uncovered-branch-tips=" + uncoveredBranchTips;
        }

        private static String percent(double value) {
            return Math.round(value * 100.0D) + "%";
        }
    }
    private record BranchTipCoverage(
            int liveTips,
            int uncoveredTips,
            Block firstUncoveredTip,
            int firstCurrentContacts,
            int firstRequiredContacts,
            int firstCurrentCluster,
            int firstRequiredCluster,
            int unplannedBareTips,
            Block firstUnplannedBareTip,
            int stalePersistentEnvelopeLeaves,
            Block firstStalePersistentEnvelopeLeaf
    ) {
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
