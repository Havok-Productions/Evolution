package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.evolution.coreparts.PluginFeature;
import org.evolution.coreparts.ResourceReporter.ReportSample;
import org.evolution.coreparts.EvolutionPlugin;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionResult;

public final class TreeEvolutionFeature implements PluginFeature, Listener {
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
    private final TreeGroundDetailPolicy groundDetailPolicy =
            new TreeGroundDetailPolicy(NATURAL_GROUND, NATURAL_DETAILS);
    private final TreeDnaNormalizer dnaNormalizer = new TreeDnaNormalizer();
    private final TreeLeafOwnershipIndex leafOwnershipIndex =
            new TreeLeafOwnershipIndex();
    private final TreeEvolutionDiagnostics diagnostics = new TreeEvolutionDiagnostics();
    private final TreePlanAuditService planAudit;
    private final TreeProfileScanService profileScanService;
    private final TreeDnaRepository dnaRepository;
    private final TreeDnaLifecycleService dnaLifecycle;
    private final TreeCandidateDiscoveryService candidateDiscovery;
    private final TreeReproductionService reproductionService;
    private final TreeMaturityService maturityService;
    private final TreePlacementService placementService;
    private final TreeCanopyRepairService canopyRepairService;
    private final TreeTransitionService transitionService;
    private final TreeLeafDecayService leafDecayService;
    private final TreeConstructionRuntime constructionRuntime;
    private final ConcurrentMap<String, TreeDna> treeDna;
    private final ConcurrentMap<UUID, TreeFocusPool> focusedTreePools = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> focusYieldUntil = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedTreeCandidate> focusedCandidateCache = new ConcurrentHashMap<>();
    private final TreeDirtyWorkQueue dirtyTreeWork = new TreeDirtyWorkQueue();
    private final ConcurrentMap<String, Long> completedTreeBackoff =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> nextTreeObservationMillis =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> nextPlayerObservationSweep =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> nextPlayerDiscoverySweep =
            new ConcurrentHashMap<>();
    private final AtomicLong changedBlocks = new AtomicLong();
    private volatile TreeEvolutionConfig config;

    public TreeEvolutionFeature(EvolutionPlugin plugin) {
        this.plugin = plugin;
        this.profileScanService = new TreeProfileScanService(plugin);
        this.dnaRepository = new TreeDnaRepository(
                plugin, dnaNormalizer, diagnostics, leafOwnershipIndex);
        this.treeDna = dnaRepository.records();
        this.planAudit = new TreePlanAuditService(
                plugin, diagnostics, changedBlocks, () -> config,
                leafOwnershipIndex);
        this.candidateDiscovery = new TreeCandidateDiscoveryService(
                plugin, diagnostics, treeDna, NATURAL_GROUND, NATURAL_DETAILS,
                this::isNearPlayer,
                plugin.playerPlacedTreeBlocks()::containsKey);
        this.reproductionService = new TreeReproductionService(
                plugin, diagnostics, dnaRepository, NATURAL_GROUND, NATURAL_DETAILS,
                changedBlocks, this::canWorkAt);
        this.maturityService = new TreeMaturityService(
                plugin, diagnostics, dnaRepository, candidateDiscovery,
                planAudit, () -> config);
        this.placementService = new TreePlacementService(
                plugin, diagnostics, dnaRepository, planAudit, maturityService,
                groundDetailPolicy, changedBlocks, NATURAL_GROUND, NATURAL_DETAILS);
        this.canopyRepairService = new TreeCanopyRepairService(
                plugin, diagnostics, dnaRepository, planAudit, changedBlocks);
        this.transitionService = new TreeTransitionService(
                plugin, diagnostics, dnaRepository, planAudit, placementService,
                changedBlocks);
        this.leafDecayService = new TreeLeafDecayService(plugin);
        this.constructionRuntime = new TreeConstructionRuntime(
                plugin, diagnostics, dnaRepository, candidateDiscovery, planAudit,
                maturityService, placementService, canopyRepairService,
                transitionService, reproductionService, this::canWorkAt, changedBlocks);
        this.dnaLifecycle = new TreeDnaLifecycleService(
                plugin, dnaRepository, dnaNormalizer, diagnostics,
                this::invalidateTreeRuntimeState);
        this.config = TreeEvolutionConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "tree-evolution", "config.load", config.summary());
        plugin.pathDebug().trace(plugin, "tree-evolution", "architecture.runtime-owners",
                "facade=TreeEvolutionFeature hierarchy=TreeConstructionRuntime"
                        + " audit=TreePlanAuditService maturity=TreeMaturityService"
                        + " transition=TreeTransitionService placement=TreePlacementService"
                        + " canopy-repair=TreeCanopyRepairService"
                        + " reproduction=TreeReproductionService"
                        + " ## each constructor phase delegates to one named runtime owner");
    }

    @Override
    public void onEnable() {
        diagnostics.initialize(plugin);
        profileScanService.initialize(config);
        loadTreeDna();
        cleanupTreeDna("startup");
        diagnostics.saveNow(plugin, config);
        plugin.pathDebug().trace(plugin, "tree-evolution", "scheduler.online-players", "players=" + Bukkit.getOnlinePlayers().size());
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayerEvolution(player, 80L);
        }
    }

    @Override
    public void onDisable() {
        diagnostics.saveNow(plugin, config);
        dnaRepository.saveNow("disable");
        focusedTreePools.clear();
        focusedCandidateCache.clear();
        focusYieldUntil.clear();
        completedTreeBackoff.clear();
        dirtyTreeWork.clear();
        nextTreeObservationMillis.clear();
        nextPlayerObservationSweep.clear();
        nextPlayerDiscoverySweep.clear();
        reproductionService.clearPlayerState();
        planAudit.clear();
    }

    @Override
    public void reload() {
        this.config = TreeEvolutionConfig.load(plugin);
        normalizeKnownTreeDna("config-reload");
        candidateDiscovery.clearSpatialCaches();
        focusedTreePools.clear();
        focusedCandidateCache.clear();
        focusYieldUntil.clear();
        completedTreeBackoff.clear();
        dirtyTreeWork.clear();
        nextTreeObservationMillis.clear();
        nextPlayerObservationSweep.clear();
        nextPlayerDiscoverySweep.clear();
        reproductionService.clearPlayerState();
        planAudit.clear();
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
                StructureScanResult result = profileScanService.scanNow();
                int sampleCount = profileScanService.sampleCount();
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        Block origin = event.getLocation().getBlock();
        Optional<TreeSeedlingRecord> owned =
                reproductionService.ownedSeedling(origin);
        if (owned.isEmpty()) {
            return;
        }

        TreeSeedlingRecord seedling = owned.get();
        if (!seedling.matches(origin)) {
            reproductionService.forgetSeedling(
                    origin, config, "stale-material-" + origin.getType());
            return;
        }
        if (!config.enabled() || !config.isWorldAllowed(origin.getWorld())) {
            // ## Turning tree evolution off returns old offspring to vanilla.
            reproductionService.forgetSeedling(
                    origin, config, "feature-disabled-or-world-excluded");
            return;
        }

        // ## Only Evolution-owned offspring are intercepted. Player saplings keep
        // normal Minecraft growth because they have no persistent ownership receipt.
        event.setCancelled(true);
        if (!canWorkAt(origin.getLocation(), config)) {
            plugin.pathDebug().traceSampled(
                    plugin,
                    "tree-evolution",
                    "seedling.germinate-wait",
                    seedling.species().id() + " at " + format(origin)
                            + " ## whole-tree growth stayed cancelled until the "
                            + "loaded Folia/protection/player gates permit gradual growth");
            return;
        }

        String source = event.isFromBonemeal()
                ? "bonemeal-attempt" : "natural-growth-attempt";
        TreeCandidate candidate = reproductionService.germinate(
                origin, seedling, source);
        TreeDna child = createNewDna(
                candidate,
                seedling.parentKey(),
                seedling.generation(),
                "owned-seedling-germination");
        reproductionService.forgetSeedling(origin, config, "germinated");
        candidateDiscovery.clearSpatialCaches();
        focusedCandidateCache.clear();
        diagnostics.recordSeedlingGerminated(
                plugin, config, origin, child, source);
        diagnostics.saveSoon(plugin, config);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (plugin.playerPlacedTreeBlocks().contains(block)) {
            candidateDiscovery.clearSpatialCaches();
            focusedCandidateCache.clear();
            plugin.pathDebug().trace(
                    plugin, "tree-evolution", "break.skip-player-wood",
                    block.getType() + " at " + format(block)
                            + " ## player construction cannot create or damage tree DNA");
            return;
        }
        if (TreeSpecies.fromSaplingMaterial(block.getType()).isPresent()) {
            reproductionService.forgetSeedling(
                    block, config, "block-broken");
            return;
        }
        Optional<TreeSpecies> species = TreeSpecies.fromMaterial(block.getType());
        if (species.isEmpty() || !config.isWorldAllowed(block.getWorld())) {
            return;
        }
        candidateDiscovery.clearSpatialCaches();
        focusedCandidateCache.clear();

        Optional<TreeCandidate> candidate = buildCandidate(block);
        if (candidate.isEmpty()) {
            plugin.pathDebug().trace(plugin, "tree-evolution", "break.skip-no-candidate", block.getType() + " at " + format(block));
            return;
        }

        TreeDna dna = dnaFor(candidate.get());
        // ## A side branch can be discovered as a short vertical log column
        // and still resolve to nearby persisted DNA. Stump damage is therefore
        // checked against the canonical DNA base, never the discovery alias.
        boolean lowest = dna.baseX() == block.getX()
                && dna.baseY() == block.getY()
                && dna.baseZ() == block.getZ();
        // ## Every natural-log break rechecks the complete leaf graph. Leaves
        // connected through any direct or indirect leaf chain remain; only
        // components with no path back to live wood are released to decay.
        leafDecayService.auditConnectivity(candidate.get(), dna);
        if (lowest) {
            dna.setStumpPresent(false);
            diagnostics.recordStalled(config, dna, "stump removed at " + format(block));
            plugin.pathDebug().trace(plugin, "tree-evolution", "break.stump-removed", format(block));
        } else {
            dna.markDamaged(config.damageStallMillis());
            diagnostics.recordStalled(config, dna, "damage stall until=" + dna.stalledUntilMillis() + " at " + format(block));
            plugin.pathDebug().trace(plugin, "tree-evolution", "break.damage-stall", format(block));
        }
        completedTreeBackoff.remove(dna.key());
        dirtyTreeWork.mark(dna.key());
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
        if (pool == null || pool.size() == 0) {
            return currentConfig.stepTicks();
        }
        int activeTrees = pool.size();
        // ## More active trees increase scheduler frequency, not per-tree
        // block count. Every focused tree therefore receives testing-speed
        // construction while discovery remains on the slower intake clock.
        return Math.max(1L,
                (currentConfig.constructionStepTicks() + activeTrees - 1L)
                        / activeTrees);
    }
    private int runNearPlayer(Player player, boolean forced) {
        try (ReportSample sample = plugin.resourceReporter().begin(
                "tree-evolution", forced ? "tick.run-near-player-forced" : "tick.run-near-player")) {
            TreeEvolutionConfig currentConfig = config;
            UUID playerId = player.getUniqueId();
            if (!player.isOnline()) {
                focusedTreePools.remove(playerId);
                reproductionService.forgetPlayer(playerId);
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

            observeOneNonDirtyTree(
                    playerId, origin, pool, currentConfig);

            boolean discoveryDue = forced
                    || claimDiscoverySweep(playerId, currentConfig);
            if (pool.size() < TreeFocusPool.CAPACITY && discoveryDue) {
                int discoveryLimit = Math.min(
                        currentConfig.attemptsPerStep(), TreeFocusPool.CAPACITY * 2);
                List<TreeCandidate> discovered = new ArrayList<>();
                for (String dirtyKey : dirtyTreeWork.takeNear(
                        world.getUID(), origin.getBlockX(), origin.getBlockZ(),
                        currentConfig.searchRadius(), treeDna,
                        discoveryLimit)) {
                    Optional<TreeCandidate> dirtyCandidate = focusedCandidateFor(
                            dirtyKey, origin, currentConfig);
                    if (dirtyCandidate.isPresent()) {
                        discovered.add(dirtyCandidate.get());
                    } else {
                        dirtyTreeWork.mark(dirtyKey);
                    }
                }
                if (discovered.size() < discoveryLimit) {
                    discovered.addAll(findKnownCandidatesNear(
                            origin, currentConfig,
                            discoveryLimit - discovered.size()));
                }
                findNearestCandidate(origin, Math.min(18, currentConfig.searchRadius()))
                        .ifPresent(discovered::add);
                int randomSearches = 0;
                while (discovered.size() < discoveryLimit && randomSearches < discoveryLimit) {
                    randomSearches++;
                    findCandidate(origin, currentConfig).ifPresent(discovered::add);
                }

                Set<String> inspectedTrees = new HashSet<>();
                for (TreeCandidate discoveredCandidate : discovered) {
                    TreeDna dna = dnaFor(discoveredCandidate);
                    if (!inspectedTrees.add(dna.key())) {
                        continue;
                    }
                    Optional<TreeCandidate> canonicalCandidate =
                            canonicalCandidateFor(
                                    discoveredCandidate, dna,
                                    currentConfig);
                    if (canonicalCandidate.isEmpty()) {
                        continue;
                    }
                    TreeCandidate candidate = canonicalCandidate.get();
                    if (pool.contains(dna.key())) {
                        // ## A previously focused tree already has a canonical
                        // candidate reconstructed from its DNA stump. Discovery
                        // may touch a branch column from the same crown; it must
                        // never replace the rooted candidate with that alias.
                        activeCandidates.putIfAbsent(dna.key(), candidate);
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
                    } else if (!status.needsFocus()
                            && completedWorkDue(dna.key(), currentConfig)
                            && backgroundKeys.add(dna.key())) {
                        backgroundCandidates.add(candidate);
                    }
                    if (pool.size() >= TreeFocusPool.CAPACITY) {
                        break;
                    }
                }
            }
            if (!discoveryDue && pool.size() < TreeFocusPool.CAPACITY) {
                plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                        "discovery.waiting-intake-clock",
                        "active=" + pool.size() + "/" + TreeFocusPool.CAPACITY
                                + " ## active construction stays fast while"
                                + " new-tree intake remains slow");
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
                    leafDecayService.auditConnectivity(candidate, dna);
                    releaseFocusedTree(pool, dna.key(), "complete",
                            "stage=" + dna.maturityStage() + " " + before.summary());
                    if (completedWorkDue(dna.key(), currentConfig)
                            && backgroundKeys.add(dna.key())) {
                        backgroundCandidates.add(candidate);
                    }
                    continue;
                }

                TreeConstructionResult constructionResult =
                        constructionRuntime.evolve(
                                candidate, dna, currentConfig);
                dnaRepository.refreshOwnership(dna);
                boolean changed = constructionResult.worldChanged();
                boolean progressed = constructionResult.progressed();
                if (changed || progressed) {
                    dirtyTreeWork.mark(dna.key());
                    completedTreeBackoff.remove(dna.key());
                }
                if (progressed && !changed) {
                    dnaStateChanged = true;
                }
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
                    leafDecayService.auditConnectivity(candidate, dna);
                    releaseFocusedTree(pool, dna.key(), "complete",
                            "stage=" + dna.maturityStage() + " " + after.summary());
                } else {
                    int noProgress = pool.updateProgress(
                            dna.key(), progressed);
                    if (TreeFocusPolicy.shouldYield(noProgress)) {
                        long cooldown = currentConfig.testingEnabled() ? 3_000L : 30_000L;
                        focusYieldUntil.put(dna.key(), System.currentTimeMillis() + cooldown);
                        leafDecayService.auditConnectivity(candidate, dna);
                        releaseFocusedTree(pool, dna.key(), "no-progress",
                                "passes=" + noProgress + " cooldown-ms=" + cooldown + " " + after.summary());
                    } else {
                        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "focus.retain",
                                "tree=" + dna.key() + " changed=" + changed
                                        + " progressed=" + progressed
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
                Optional<TreeCandidate> canonicalCandidate =
                        canonicalCandidateFor(
                                candidate, dna, currentConfig);
                if (canonicalCandidate.isEmpty()) {
                    continue;
                }
                candidate = canonicalCandidate.get();
                if (pool.contains(dna.key()) || (!forced && !canGrowNow(dna, currentConfig, candidate))) {
                    continue;
                }
                String chunkKey = chunkKey(candidate);
                if (chunkActivity.getOrDefault(chunkKey, 0) >= 3) {
                    continue;
                }
                TreeConstructionResult backgroundResult =
                        constructionRuntime.evolve(
                                candidate, dna, currentConfig);
                dnaRepository.refreshOwnership(dna);
                if (backgroundResult.worldChanged()
                        || backgroundResult.progressed()) {
                    dirtyTreeWork.mark(dna.key());
                } else {
                    markCompletedBackoff(dna.key(), currentConfig);
                }
                if (backgroundResult.progressed()
                        && !backgroundResult.worldChanged()) {
                    dnaStateChanged = true;
                }
                if (backgroundResult.worldChanged()) {
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

    private boolean claimDiscoverySweep(
            UUID playerId,
            TreeEvolutionConfig currentConfig
    ) {
        long now = System.currentTimeMillis();
        Long due = nextPlayerDiscoverySweep.get(playerId);
        if (due != null && now < due) {
            return false;
        }
        long intervalMillis = Math.max(50L,
                currentConfig.stepTicks() * 50L);
        nextPlayerDiscoverySweep.put(playerId, now + intervalMillis);
        return true;
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

        Optional<TreeCandidate> candidate = candidateDiscovery.buildKnown(world, dna, currentConfig);
        long ttl = currentConfig.testingEnabled() ? 750L : 2_000L;
        focusedCandidateCache.put(treeKey,
                new CachedTreeCandidate(candidate.orElse(null), now + ttl));
        return candidate;
    }

    private Optional<TreeCandidate> canonicalCandidateFor(
            TreeCandidate discovered,
            TreeDna dna,
            TreeEvolutionConfig currentConfig
    ) {
        if (TreeCandidateBindingPolicy.isCanonical(discovered, dna)) {
            return Optional.of(discovered);
        }
        Optional<TreeCandidate> canonical = candidateDiscovery.buildKnown(
                discovered.world(), dna, currentConfig);
        plugin.pathDebug().traceSampled(
                plugin,
                "tree-evolution",
                canonical.isPresent()
                        ? "state.candidate-dna-rebound"
                        : "gate.candidate-dna-rebound-failed",
                "discovered=" + discovered.baseKey()
                        + " dna=" + dna.key()
                        + " canonical="
                        + canonical.map(TreeCandidate::baseKey)
                                .orElse("missing")
                        + " ## scheduler aliases are rebound to the persisted DNA stump before any hierarchy audit or mutation");
        return canonical;
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
        if ("complete".equals(reason)) {
            dirtyTreeWork.remove(treeKey);
            markCompletedBackoff(treeKey, config);
        }
        plugin.pathDebug().trace(plugin, "tree-evolution", "focus.release." + reason,
                "tree=" + treeKey + " active=" + pool.size() + "/" + TreeFocusPool.CAPACITY
                        + " " + detail
                        + " ## this slot now accepts another structural candidate");
    }

    private TreeWorkStatus treeWorkStatus(
            TreeCandidate candidate, TreeDna dna, TreeEvolutionConfig currentConfig) {
        return planAudit.workStatus(candidate, dna, currentConfig);
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
        dnaRepository.refreshOwnership(dna);
        dirtyTreeWork.mark(dna.key());
        markTreeDnaDirty("stage transition finalized " + dna.key());
        plugin.pathDebug().trace(plugin, "tree-evolution", "transition.finalize-complete",
                "tree=" + dna.key() + " cleared-growth-burst=" + previousBurst
                        + " original-snapshot-cleared=" + originalBlockCount
                        + " allowance-expired=" + (previousBurst == 0)
                        + " " + status.summary()
                        + " ## full projected structure closed the transition and released its temporary source shape even when the action allowance expired earlier");
        maturityService.updateMaturity(candidate, dna, currentConfig);
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
        delayMillis = Math.round(delayMillis * dna.currentIntent().delayMultiplier(dna) * TreeGrowthIntentPolicy.forestDelayMultiplier(dna, treeDna.values()));
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

    private int runReservedSeedlingSearch(
            UUID playerId,
            Location origin,
            TreeEvolutionConfig currentConfig
    ) {
        return reproductionService.runReserved(playerId, origin, currentConfig);
    }

    private void observeOneNonDirtyTree(
            UUID playerId,
            Location origin,
            TreeFocusPool pool,
            TreeEvolutionConfig currentConfig
    ) {
        long now = System.currentTimeMillis();
        long sweepInterval = currentConfig.testingEnabled()
                ? 1_000L : 5_000L;
        Long nextSweep = nextPlayerObservationSweep.get(playerId);
        if (nextSweep != null && now < nextSweep) {
            return;
        }
        nextPlayerObservationSweep.put(playerId, now + sweepInterval);

        List<TreeCandidate> candidates =
                candidateDiscovery.findKnownForObservation(
                        origin,
                        currentConfig,
                        1,
                        dna -> !pool.contains(dna.key())
                                && !dirtyTreeWork.contains(dna.key()),
                        dna -> nextTreeObservationMillis.getOrDefault(
                                dna.key(), 0L));
        if (candidates.isEmpty()) {
            return;
        }
        TreeCandidate candidate = candidates.getFirst();
        TreeDna dna = treeDna.get(candidate.baseKey());
        if (dna == null) {
            dna = findNearbyExistingDna(candidate).orElse(null);
        }
        if (dna == null) {
            return;
        }

        long observationDelay = currentConfig.testingEnabled()
                ? 30_000L : 120_000L;
        nextTreeObservationMillis.put(
                dna.key(), now + observationDelay);
        try (ReportSample sample = plugin.resourceReporter().begin(
                "tree-evolution", "audit.observe-non-dirty-tree")) {
            TreeWorkStatus status = treeWorkStatus(
                    candidate, dna, currentConfig);
            boolean promoted = status.needsFocus();
            if (promoted) {
                completedTreeBackoff.remove(dna.key());
                dirtyTreeWork.mark(dna.key());
                plugin.pathDebug().trace(
                        plugin, "tree-evolution",
                        "audit.clean-tree-promoted-dirty",
                        "tree=" + dna.key() + " " + status.summary()
                                + " ## periodic observation found work that"
                                + " dirty-event tracking did not report");
            }
            diagnostics.recordCleanTreeObservation(
                    currentConfig, dna, status, promoted);
            diagnostics.saveSoon(plugin, currentConfig);
            sample.workUnits(1)
                    .changedUnits(promoted ? 1 : 0)
                    .detail("tree=" + dna.key() + " result="
                            + (promoted ? "promoted-dirty" : "clean"));
        }
    }

    private Optional<TreeCandidate> findCandidate(Location origin, TreeEvolutionConfig currentConfig) {
        return candidateDiscovery.findRandom(origin, currentConfig);
    }

    private Optional<TreeCandidate> findNearestCandidate(Location origin, int radius) {
        return candidateDiscovery.findNearest(origin, radius, config);
    }

    private List<TreeCandidate> findKnownCandidatesNear(
            Location origin,
            TreeEvolutionConfig currentConfig,
            int limit
    ) {
        return candidateDiscovery.findKnown(origin, currentConfig, limit);
    }

    private boolean isCandidateStillValid(TreeCandidate candidate) {
        return candidateDiscovery.isStillValid(candidate);
    }

    private Optional<TreeCandidate> buildCandidate(Block start) {
        return candidateDiscovery.build(start);
    }

    private Optional<TreeCandidate> buildCandidate(Block start, boolean thoroughOwnershipScan) {
        return candidateDiscovery.build(start, thoroughOwnershipScan);
    }


    private TreeDna dnaFor(TreeCandidate candidate) {
        TreeDna existing = treeDna.get(candidate.baseKey());
        if (existing == null) {
            existing = findNearbyExistingDna(candidate).orElse(null);
        }
        if (existing != null) {
            return existing;
        }
        TreeDna parent = findParentDna(candidate).orElse(null);
        return createNewDna(
                candidate,
                parent == null ? "wild" : parent.key(),
                parent == null ? 0 : parent.generation() + 1,
                "discovered-tree");
    }

    private TreeDna createNewDna(
            TreeCandidate candidate,
            String parentKey,
            int generation,
            String creationSource
    ) {
        TreeDna existing = treeDna.get(candidate.baseKey());
        if (existing != null) {
            return existing;
        }
        TreeSourcePattern sourcePattern =
                TreeSourcePattern.capture(candidate);
        TreeVariant variant = TreeVariantClassifier.classify(
                candidate.species(), sourcePattern);
        TreeProfileSample sample =
                chooseProfileSample(candidate, variant);
        TreeGrowthProfile profile = sample == null
                ? config.profile(candidate.species()) : sample.profile();
        TreeDna rawCreated = TreeDna.create(
                candidate.world(),
                candidate,
                profile,
                sample,
                variant,
                sourcePattern,
                parentKey,
                generation);
        TreeDnaNormalizer.NormalizedDna creationNormalization =
                dnaNormalizer.normalize(rawCreated, config.maximumStage());
        TreeDna created = creationNormalization.dna();
        if (candidate.ownershipComplete()) {
            created.captureOriginalShape(
                    maturityService.originalLogKeys(candidate, created),
                    maturityService.originalLeafKeys(candidate, created));
        }
        if (creationNormalization.changed()) {
            diagnostics.recordDnaNormalized(
                    config, rawCreated, created, creationNormalization.summary());
            plugin.pathDebug().traceSampled(
                    plugin,
                    "tree-evolution",
                    "state.dna-create-normalize",
                    created.key() + " " + creationNormalization.summary()
                            + " ## newly created tree was capped at the fancy mature profile");
        }
        TreeDna previous = treeDna.putIfAbsent(created.key(), created);
        TreeDna result = previous == null ? created : previous;
        if (previous == null) {
            dnaRepository.refreshOwnership(created);
            dirtyTreeWork.mark(created.key());
            markTreeDnaDirty("dna-create-" + creationSource);
            diagnostics.recordDnaCreated(config, created);
            plugin.pathDebug().trace(
                    plugin,
                    "tree-evolution",
                    "state.dna-create",
                    candidate.species().id() + " at "
                            + format(candidate.baseLocation())
                            + " source=" + creationSource
                            + " sample=" + created.profileSampleId()
                            + " personality=" + created.personality()
                            + " rarity=" + created.rarity()
                            + " parent=" + created.parentKey()
                            + " generation=" + created.generation()
                            + " original-blocks="
                            + created.originalShapeBlockCount()
                            + " original-logs="
                            + created.originalShapeLogCount()
                            + " original-leaves="
                            + created.originalShapeLeafCount());
            saveTreeDna();
        }
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

    private TreeProfileSample chooseProfileSample(
            TreeCandidate candidate,
            TreeVariant variant
    ) {
        List<TreeProfileSample> samples = profileScanService.samples(candidate.species());
        if (samples.isEmpty()) {
            return null;
        }
        List<TreeProfileSample> filtered =
                filteredProfileSamples(candidate, variant, samples);
        int index = Math.floorMod(candidate.baseKey().hashCode(), filtered.size());
        return filtered.get(index);
    }

    private List<TreeProfileSample> filteredProfileSamples(
            TreeCandidate candidate,
            TreeVariant variant,
            List<TreeProfileSample> samples
    ) {
        List<TreeProfileSample> filtered = new ArrayList<>();
        boolean uprightTree = candidate.height() >= 5 && candidate.connectedLogs() >= 5;
        boolean megaVariant = variant == TreeVariant.SPRUCE_MEGA
                || variant == TreeVariant.SPRUCE_MEGA_PINE
                || variant == TreeVariant.JUNGLE_MEGA;
        // ## Decide scale compatibility once. Profile libraries can contain
        // hundreds of samples, so variant filtering must remain linear.
        boolean hasMatchingScale = samples.stream()
                .anyMatch(sample ->
                        megaVariant == isMegaProfileSample(sample));
        for (TreeProfileSample sample : samples) {
            String text = profileSampleText(sample);
            if (uprightTree && isLowGrowthSample(text)) {
                continue;
            }
            if (candidate.species() != TreeSpecies.JUNGLE && text.contains("bamboo")) {
                continue;
            }
            if (hasMatchingScale
                    && megaVariant != isMegaProfileSample(sample)) {
                continue;
            }
            filtered.add(sample);
        }
        return filtered.isEmpty() ? samples : filtered;
    }

    private boolean isMegaProfileSample(TreeProfileSample sample) {
        String text = profileSampleText(sample);
        return text.contains("mega")
                || text.contains("giant")
                || text.contains("huge");
    }

    private String profileSampleText(TreeProfileSample sample) {
        return (sample.id() + " "
                + sample.sourceFile() + " "
                + sample.trunkPlacer() + " "
                + sample.foliagePlacer())
                .toLowerCase(java.util.Locale.ROOT);
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
        for (TreeProfileSample sample : profileScanService.samples(dna.species())) {
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
        dnaLifecycle.cleanup(config, reason);
    }

    private void maybeCleanupTreeDna(String reason) {
        dnaLifecycle.maybeCleanup(config, reason);
    }

    private void normalizeKnownTreeDna(String reason) {
        dnaLifecycle.normalize(config, reason);
    }

    private void invalidateTreeRuntimeState(String treeKey) {
        planAudit.invalidate(treeKey);
        focusYieldUntil.remove(treeKey);
        completedTreeBackoff.remove(treeKey);
        dirtyTreeWork.remove(treeKey);
        nextTreeObservationMillis.remove(treeKey);
        reproductionService.forgetTree(treeKey);
        focusedCandidateCache.remove(treeKey);
        candidateDiscovery.clearSpatialCaches();
    }

    private boolean completedWorkDue(
            String treeKey, TreeEvolutionConfig currentConfig) {
        Long until = completedTreeBackoff.get(treeKey);
        if (until == null) {
            return true;
        }
        if (System.currentTimeMillis() >= until) {
            completedTreeBackoff.remove(treeKey, until);
            return true;
        }
        return false;
    }

    private void markCompletedBackoff(
            String treeKey, TreeEvolutionConfig currentConfig) {
        long delay = currentConfig.testingEnabled() ? 30_000L : 120_000L;
        completedTreeBackoff.put(
                treeKey, System.currentTimeMillis() + delay);
        plugin.pathDebug().traceSampled(
                plugin, "tree-evolution", "focus.complete-backoff",
                "tree=" + treeKey + " delay-ms=" + delay
                        + " ## completed trees retain slow monitoring without entering every player pass");
    }

    private void loadTreeDna() {
        dnaRepository.load(config);
    }

    private void saveTreeDna() {
        dnaRepository.save(config);
    }

    private void markTreeDnaDirty(String reason) {
        dnaRepository.markDirty(reason);
    }

    private String chunkKey(TreeCandidate candidate) {
        return candidate.world().getUID() + ":" + (candidate.baseX() >> 4) + ":" + (candidate.baseZ() >> 4);
    }

    private String format(Block block) {
        return format(block.getLocation());
    }

    private String format(Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "unknown" : world.getName();
        return worldName + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private record CachedTreeCandidate(TreeCandidate candidate, long expiresMillis) {
    }


}
