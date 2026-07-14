package org.slowtrees.treeevolution;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slowtrees.core.SlowTreesPlugin;

final class TreeEvolutionDiagnostics {
    private final AtomicLong searches = new AtomicLong();
    private final AtomicLong candidates = new AtomicLong();
    private final AtomicLong dnaCreated = new AtomicLong();
    private final AtomicLong dnaLoaded = new AtomicLong();
    private final AtomicLong planned = new AtomicLong();
    private final AtomicLong placed = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong stalled = new AtomicLong();
    private final AtomicLong forcedSteps = new AtomicLong();
    private final AtomicLong intentUpdates = new AtomicLong();
    private final AtomicLong stageTransitions = new AtomicLong();
    private final AtomicLong dnaNormalized = new AtomicLong();
    private final AtomicBoolean saveRunning = new AtomicBoolean();
    private final AtomicLong nextSaveMillis = new AtomicLong();
    private final AtomicLong next3dBuildMillis = new AtomicLong();
    private final Deque<String> recentEvents = new ArrayDeque<>();
    private final Deque<String> recentStageEvents = new ArrayDeque<>();
    private final String sessionStartedAt = Instant.now().toString();
    private volatile List<String> lastMapRows = List.of();
    private volatile String lastMapCenter = "none";
    private volatile String lastPlanSummary = "none";
    private volatile List<String> lastPlanPreview = List.of();
    private volatile Map<String, Object> lastStageSnapshot = Map.of();
    private volatile Map<String, Object> last3dStageSnapshot = Map.of();
    private volatile String lastPlan3dSummary = "none";
    private volatile Map<String, Object> lastPlan3dBounds = Map.of();
    private volatile Map<String, Integer> lastPlan3dRoleCounts = Map.of();
    private volatile List<Map<String, Object>> lastPlan3dLayers = List.of();
    private volatile String lastLive3dSummary = "none";
    private volatile Map<String, Object> lastLive3dStatusCounts = Map.of();
    private volatile List<Map<String, Object>> lastLive3dLayers = List.of();
    private volatile Map<String, Object> lastReplaySummary = Map.of();
    private volatile Map<String, Object> lastReplayRoleProgress = Map.of();
    private volatile Map<String, Integer> lastReplayProvenanceCounts = Map.of();
    private volatile List<Map<String, Object>> lastReplaySamples = List.of();
    private volatile String lastLineageSummary = "none";

    void recordSearch() {
        searches.incrementAndGet();
    }

    void recordCandidate(TreeEvolutionConfig config, TreeCandidate candidate) {
        candidates.incrementAndGet();
        event(config, "[TRACE][tree-evolution] candidate species=" + candidate.species().id()
                + " base=" + format(candidate.baseBlock())
                + " height=" + candidate.height()
                + " logs=" + candidate.connectedLogs()
                + " leaves=" + candidate.connectedLeaves());
    }

    void recordDnaCreated(TreeEvolutionConfig config, TreeDna dna) {
        dnaCreated.incrementAndGet();
        event(config, "[STATE][tree-evolution] dna.create species=" + dna.species().id()
                + " base=" + dna.baseX() + "," + dna.baseY() + "," + dna.baseZ()
                + " personality=" + dna.personality()
                + " rarity=" + dna.rarity()
                + " age=" + dna.age()
                + " generation=" + dna.generation()
                + " target-height=" + dna.targetHeight()
                + " branches=" + dna.branchCount()
                + " branch-length=" + dna.minBranchLength() + "-" + dna.maxBranchLength()
                + " canopy-radius=" + dna.canopyRadiusX() + "x" + dna.canopyRadiusY() + "x" + dna.canopyRadiusZ()
                + " trunk-width=" + dna.trunkWidth()
                + " canopy-layers=" + dna.canopyLayerCount()
                + " layer-spread=" + dna.canopyLayerSpread()
                + " branch-start=" + round(dna.branchStartRatio())
                + " sample=" + dna.profileSampleId()
                + " source=" + dna.profileSampleSource()
                + " parent=" + dna.parentKey());
        lastLineageSummary = "tree=" + dna.key() + ", parent=" + dna.parentKey() + ", generation=" + dna.generation();
    }

    void recordDnaLoaded(long count) {
        dnaLoaded.addAndGet(count);
    }

    void recordDnaNormalized(TreeEvolutionConfig config, TreeDna before, TreeDna after, String detail) {
        dnaNormalized.incrementAndGet();
        event(config, "[STATE][tree-evolution] dna.normalize species=" + after.species().id()
                + " base=" + after.baseX() + "," + after.baseY() + "," + after.baseZ()
                + " sample=" + after.profileSampleId()
                + " source=" + after.profileSampleSource()
                + " ## legacy saved DNA normalized for staged shape planner: " + detail);
        lastLineageSummary = "normalized=" + after.key()
                + ", parent=" + after.parentKey()
                + ", generation=" + after.generation()
                + ", before-stage=" + before.maturityStage()
                + ", after-stage=" + after.maturityStage();
    }

    void recordPlan(TreeEvolutionConfig config, TreeDna dna, TreePlan plan) {
        recordPlan(config, dna, plan, plan.orderedBlocks(), null, true);
    }

    void recordPlan(TreeEvolutionConfig config, TreeDna dna, TreePlan plan, World world) {
        recordPlan(config, dna, plan, plan.orderedBlocks(), world, true);
    }

    void recordPlan(TreeEvolutionConfig config, TreeDna dna, TreePlan plan, List<PlannedTreeBlock> orderedBlocks, World world, boolean force3d) {
        planned.incrementAndGet();
        lastPlanSummary = "species=" + dna.species().id()
                + ", personality=" + dna.personality()
                + ", rarity=" + dna.rarity()
                + ", age=" + dna.age()
                + ", generation=" + dna.generation()
                + ", stage=" + dna.maturityStage()
                + ", intent=" + dna.currentIntent()
                + ", cursor=" + dna.planCursor()
                + ", prunes=" + dna.consecutivePrunes()
                + ", blocked=" + dna.blockedAttempts()
                + ", burst=" + dna.stageCleanupBurst() + "/" + dna.stageGrowthBurst()
                + ", target-height=" + dna.targetHeight()
                + ", stage-height=" + TreeSpeciesStageStyle.visibleHeight(dna)
                + ", branches=" + dna.branchCount()
                + ", stage-branches=" + TreeSpeciesStageStyle.branchCount(dna)
                + ", branch-length=" + dna.minBranchLength() + "-" + dna.maxBranchLength()
                + ", canopy-radius=" + dna.canopyRadiusX() + "x" + dna.canopyRadiusY() + "x" + dna.canopyRadiusZ()
                + ", stage-canopy-radius=" + TreeSpeciesStageStyle.canopyRadiusX(dna)
                + "x" + TreeSpeciesStageStyle.canopyRadiusY(dna)
                + "x" + TreeSpeciesStageStyle.canopyRadiusZ(dna)
                + ", trunk-width=" + dna.trunkWidth()
                + ", canopy-layers=" + dna.canopyLayerCount()
                + ", stage-canopy-layers=" + TreeSpeciesStageStyle.canopyLayerCount(dna)
                + ", layer-spread=" + dna.canopyLayerSpread()
                + ", branch-start=" + round(dna.branchStartRatio())
                + ", stage-branch-start=" + round(TreeSpeciesStageStyle.branchStartRatio(dna))
                + ", sample=" + dna.profileSampleId()
                + ", planned-blocks=" + plan.size();
        lastPlanPreview = orderedBlocks.stream()
                .limit(10)
                .map(block -> block.role() + " " + block.material() + " " + block.x() + "," + block.y() + "," + block.z())
                .toList();
        Map<String, Object> stageSnapshot = stageSnapshot(config, dna, plan);
        lastStageSnapshot = stageSnapshot;
        stageEvent(config, "[TRACE][tree-evolution] stage.observe species=" + dna.species().id()
                + " base=" + dna.baseX() + "," + dna.baseY() + "," + dna.baseZ()
                + " stage=" + dna.maturityStage()
                + " age=" + dna.age()
                + " intent=" + dna.currentIntent()
                + " visible-height=" + TreeSpeciesStageStyle.visibleHeight(dna)
                + " planned-blocks=" + plan.size());
        if (config.debugReplayEnabled()) {
            TreeEvolutionReplay.Report replay = TreeEvolutionReplay.build(config, dna, orderedBlocks, world);
            lastReplaySummary = replay.summary();
            lastReplayRoleProgress = replay.roleProgress();
            lastReplayProvenanceCounts = replay.provenanceCounts();
            lastReplaySamples = replay.samples();
        }
        if (force3d || shouldBuild3d(config)) {
            last3dStageSnapshot = stageSnapshot;
            buildPlan3dMap(config, dna, orderedBlocks, world);
        }
        event(config, "[TRACE][tree-evolution] plan.target " + lastPlanSummary);
    }

    private boolean shouldBuild3d(TreeEvolutionConfig config) {
        if (!config.debug3dEnabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long next = next3dBuildMillis.get();
        return now >= next && next3dBuildMillis.compareAndSet(next, now + 5000L);
    }
    void recordIntent(TreeEvolutionConfig config, TreeDna dna, TreeGrowthIntent intent, String detail) {
        intentUpdates.incrementAndGet();
        event(config, "[STATE][tree-evolution] intent=" + intent
                + " species=" + dna.species().id()
                + " base=" + dna.baseX() + "," + dna.baseY() + "," + dna.baseZ()
                + " stage=" + dna.maturityStage()
                + " age=" + dna.age()
                + " cursor=" + dna.planCursor()
                + " prunes=" + dna.consecutivePrunes()
                + " blocked=" + dna.blockedAttempts()
                + " burst=" + dna.stageCleanupBurst() + "/" + dna.stageGrowthBurst()
                + " " + detail);
    }

    void recordShapeChoice(TreeEvolutionConfig config, TreeDna dna, String reason, int candidates) {
        event(config, "[TRACE][tree-evolution] shape.choice species=" + dna.species().id()
                + " base=" + dna.baseX() + "," + dna.baseY() + "," + dna.baseZ()
                + " candidates=" + candidates
                + " " + reason);
    }

    void recordStageTransition(TreeEvolutionConfig config, TreeDna dna, TreeMaturityStage from, TreeMaturityStage to, String detail) {
        stageTransitions.incrementAndGet();
        stageEvent(config, "[STATE][tree-evolution] stage.transition " + from + "->" + to
                + " species=" + dna.species().id()
                + " base=" + dna.baseX() + "," + dna.baseY() + "," + dna.baseZ()
                + " age=" + dna.age()
                + " cleanup-burst=" + dna.stageCleanupBurst()
                + " growth-burst=" + dna.stageGrowthBurst()
                + " " + detail
                + " ## stage changed, next ticks should prune/fill canopy before normal growth resumes");
        event(config, "[STATE][tree-evolution] stage.transition " + from + "->" + to
                + " species=" + dna.species().id()
                + " base=" + dna.baseX() + "," + dna.baseY() + "," + dna.baseZ()
                + " cleanup-burst=" + dna.stageCleanupBurst()
                + " growth-burst=" + dna.stageGrowthBurst()
                + " " + detail);
    }

    void recordPlaced(SlowTreesPlugin plugin, TreeEvolutionConfig config, Block block, PlannedTreeBlock plannedBlock) {
        placed.incrementAndGet();
        event(config, "[ACTION][tree-evolution] place role=" + plannedBlock.role()
                + " material=" + plannedBlock.material()
                + " at=" + format(block));
        buildMap(block, config.debugMapRadius());
        saveSoon(plugin, config);
    }

    void recordPruned(SlowTreesPlugin plugin, TreeEvolutionConfig config, Block block, TreeDna dna) {
        placed.incrementAndGet();
        event(config, "[ACTION][tree-evolution] prune role=STALE_CANOPY"
                + " material=" + dna.species().leafMaterial()
                + " at=" + format(block)
                + " sample=" + dna.profileSampleId());
        buildMap(block, config.debugMapRadius());
        saveSoon(plugin, config);
    }

    void recordCanopyLift(SlowTreesPlugin plugin, TreeEvolutionConfig config, Block trunk, TreeDna dna, int leavesPlaced) {
        placed.addAndGet(leavesPlaced);
        event(config, "[ACTION][tree-evolution] canopy-lift"
                + " trunk=" + format(trunk)
                + " leaf=" + dna.species().leafMaterial()
                + " leaves-placed=" + leavesPlaced
                + " target-height=" + dna.targetHeight()
                + " stage=" + dna.maturityStage());
        buildMap(trunk, config.debugMapRadius());
        saveSoon(plugin, config);
    }

    void recordSeedling(SlowTreesPlugin plugin, TreeEvolutionConfig config, Block block, TreeDna parent) {
        placed.incrementAndGet();
        lastLineageSummary = "parent=" + parent.key()
                + ", generation=" + parent.generation()
                + ", child-sapling=" + format(block)
                + ", species=" + parent.species().id();
        event(config, "[ACTION][tree-evolution] seedling role=OFFSPRING"
                + " material=" + parent.species().saplingMaterial()
                + " at=" + format(block)
                + " parent=" + parent.key()
                + " generation=" + parent.generation());
        buildMap(block, config.debugMapRadius());
        saveSoon(plugin, config);
    }

    void recordReject(TreeEvolutionConfig config, String reason, String detail) {
        rejected.incrementAndGet();
        event(config, "[GATE][tree-evolution] blocked." + reason + " -> " + detail);
    }

    void recordStalled(TreeEvolutionConfig config, TreeDna dna, String detail) {
        stalled.incrementAndGet();
        event(config, "[STATE][tree-evolution] stalled key=" + dna.key() + " " + detail);
    }

    void recordForcedStep(TreeEvolutionConfig config, String detail) {
        forcedSteps.incrementAndGet();
        event(config, "[DEBUG][tree-evolution] force-step " + detail);
    }

    long placed() {
        return placed.get();
    }

    void event(TreeEvolutionConfig config, String event) {
        if (!config.debugEnabled() || config.debugRecentEvents() <= 0) {
            return;
        }
        synchronized (recentEvents) {
            recentEvents.addLast(Instant.now() + " " + event);
            while (recentEvents.size() > config.debugRecentEvents()) {
                recentEvents.removeFirst();
            }
        }
    }

    void stageEvent(TreeEvolutionConfig config, String event) {
        if (!config.debug3dEnabled() || config.debug3dRecentStageEvents() <= 0) {
            return;
        }
        synchronized (recentStageEvents) {
            recentStageEvents.addLast(Instant.now() + " " + event);
            while (recentStageEvents.size() > config.debug3dRecentStageEvents()) {
                recentStageEvents.removeFirst();
            }
        }
    }

    void saveSoon(SlowTreesPlugin plugin, TreeEvolutionConfig config) {
        if (!config.debugEnabled() || !plugin.isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        long next = nextSaveMillis.get();
        if (now < next || !nextSaveMillis.compareAndSet(next, now + 5000L)) {
            return;
        }
        saveAsync(plugin, config);
    }

    void saveAsync(SlowTreesPlugin plugin, TreeEvolutionConfig config) {
        if (!config.debugEnabled() || !saveRunning.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                save(plugin, config);
            } finally {
                saveRunning.set(false);
            }
        });
    }

    void saveNow(SlowTreesPlugin plugin, TreeEvolutionConfig config) {
        if (config.debugEnabled()) {
            save(plugin, config);
        }
    }

    private void buildMap(Block center, int radius) {
        World world = center.getWorld();
        List<String> rows = new ArrayList<>();
        for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
            List<String> row = new ArrayList<>();
            for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
                row.add(tokenAt(world, x, z, center));
            }
            rows.add(String.join(" ", row));
        }
        lastMapCenter = format(center);
        lastMapRows = rows;
    }

    private String tokenAt(World world, int x, int z, Block center) {
        if (x == center.getX() && z == center.getZ()) {
            return "A";
        }
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return "?";
        }
        Block surface = world.getHighestBlockAt(x, z);
        Material type = surface.getType();
        Material below = surface.getRelative(0, -1, 0).getType();
        if (isLog(type) || isLog(below)) {
            return "T";
        }
        if (isLeaf(type) || isLeaf(below)) {
            return "L";
        }
        if (type == Material.VINE || below == Material.VINE) {
            return "V";
        }
        if (type == Material.LEAF_LITTER || type == Material.SHORT_GRASS || type == Material.FERN || type == Material.PINK_PETALS
                || type.name().endsWith("_SAPLING") || type == Material.MANGROVE_PROPAGULE || type == Material.BROWN_MUSHROOM || type == Material.RED_MUSHROOM) {
            return "U";
        }
        if (below == Material.GRASS_BLOCK || below == Material.DIRT || below == Material.PODZOL || below == Material.MOSS_BLOCK) {
            return "G";
        }
        return ".";
    }

    private void buildPlan3dMap(TreeEvolutionConfig config, TreeDna dna, List<PlannedTreeBlock> orderedBlocks, World world) {
        if (!config.debug3dEnabled() || orderedBlocks.isEmpty()) {
            return;
        }

        int fullMinX = Integer.MAX_VALUE;
        int fullMaxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int fullMinZ = Integer.MAX_VALUE;
        int fullMaxZ = Integer.MIN_VALUE;
        Map<TreeBlockRole, Integer> roleCounts = new EnumMap<>(TreeBlockRole.class);
        for (PlannedTreeBlock block : orderedBlocks) {
            fullMinX = Math.min(fullMinX, block.x());
            fullMaxX = Math.max(fullMaxX, block.x());
            minY = Math.min(minY, block.y());
            maxY = Math.max(maxY, block.y());
            fullMinZ = Math.min(fullMinZ, block.z());
            fullMaxZ = Math.max(fullMaxZ, block.z());
            roleCounts.merge(block.role(), 1, Integer::sum);
        }

        int radius = Math.max(4, Math.min(18, config.debugMapRadius()));
        int minX = dna.baseX() - radius;
        int maxX = dna.baseX() + radius;
        int minZ = dna.baseZ() - radius;
        int maxZ = dna.baseZ() + radius;
        boolean clipped = fullMinX < minX || fullMaxX > maxX || fullMinZ < minZ || fullMaxZ > maxZ;
        int layerStride = Math.max(1, (maxY - minY + 48) / 48);

        Map<String, Character> planned = new HashMap<>();
        Map<String, PlannedTreeBlock> plannedBlocks = new HashMap<>();
        for (PlannedTreeBlock block : orderedBlocks) {
            if (block.x() < minX || block.x() > maxX || block.z() < minZ || block.z() > maxZ) {
                continue;
            }
            String key = block.x() + ":" + block.y() + ":" + block.z();
            Character current = planned.get(key);
            char symbol = symbolFor(block);
            if (current == null || symbolPriority(symbol) >= symbolPriority(current)) {
                planned.put(key, symbol);
                plannedBlocks.put(key, block);
            }
        }

        List<Map<String, Object>> layers = new ArrayList<>();
        List<Map<String, Object>> liveLayers = new ArrayList<>();
        int livePlaced = 0;
        int liveMissing = 0;
        int liveBlocked = 0;
        int liveUnknown = 0;
        Map<TreeBlockRole, int[]> liveRoleCounts = new EnumMap<>(TreeBlockRole.class);
        for (int y = minY; y <= maxY; y++) {
            if ((y - minY) % layerStride != 0 && y != dna.baseY() && y != maxY) {
                continue;
            }
            List<String> rows = new ArrayList<>();
            List<String> liveRows = new ArrayList<>();
            boolean hasContent = false;
            boolean liveHasContent = false;
            for (int z = minZ; z <= maxZ; z++) {
                StringBuilder row = new StringBuilder();
                StringBuilder liveRow = new StringBuilder();
                for (int x = minX; x <= maxX; x++) {
                    String key = x + ":" + y + ":" + z;
                    char token = planned.getOrDefault(key, '.');
                    if (x == dna.baseX() && y == dna.baseY() && z == dna.baseZ()) {
                        token = token == '.' ? 'O' : Character.toLowerCase(token);
                    }
                    if (token != '.') {
                        hasContent = true;
                    }
                    row.append(token);
                    PlannedTreeBlock plannedBlock = plannedBlocks.get(key);
                    char liveToken = liveTokenFor(config, world, plannedBlock, token);
                    if (liveToken == '?') {
                        liveUnknown++;
                        countLiveRole(liveRoleCounts, plannedBlock, 3);
                    } else if (liveToken == 'X') {
                        liveBlocked++;
                        countLiveRole(liveRoleCounts, plannedBlock, 2);
                    } else if (plannedBlock != null && Character.isUpperCase(liveToken)) {
                        livePlaced++;
                        countLiveRole(liveRoleCounts, plannedBlock, 0);
                    } else if (plannedBlock != null && Character.isLowerCase(liveToken)) {
                        liveMissing++;
                        countLiveRole(liveRoleCounts, plannedBlock, 1);
                    }
                    if (liveToken != '.') {
                        liveHasContent = true;
                    }
                    liveRow.append(liveToken);
                }
                rows.add(row.toString());
                liveRows.add(liveRow.toString());
            }
            if (!hasContent) {
                continue;
            }
            Map<String, Object> layer = new LinkedHashMap<>();
            layer.put("y", y);
            layer.put("relative-y", y - dna.baseY());
            layer.put("rows", rows);
            layers.add(layer);
            if (liveHasContent) {
                Map<String, Object> liveLayer = new LinkedHashMap<>();
                liveLayer.put("y", y);
                liveLayer.put("relative-y", y - dna.baseY());
                liveLayer.put("rows", liveRows);
                liveLayers.add(liveLayer);
            }
        }

        Map<String, Object> bounds = new LinkedHashMap<>();
        bounds.put("base", dna.baseX() + "," + dna.baseY() + "," + dna.baseZ());
        bounds.put("full", fullMinX + "," + minY + "," + fullMinZ + " -> " + fullMaxX + "," + maxY + "," + fullMaxZ);
        bounds.put("shown", minX + "," + minY + "," + minZ + " -> " + maxX + "," + maxY + "," + maxZ);
        bounds.put("horizontal-radius-shown", radius);
        bounds.put("layer-step", layerStride);
        bounds.put("clipped", clipped);

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TreeBlockRole role : TreeBlockRole.values()) {
            counts.put(role.name(), roleCounts.getOrDefault(role, 0));
        }

        lastPlan3dSummary = "target-plan species=" + dna.species().id()
                + " stage=" + dna.maturityStage()
                + " intent=" + dna.currentIntent()
                + " age=" + dna.age()
                + " cursor=" + dna.planCursor()
                + " sample=" + dna.profileSampleId()
                + " layers=" + layers.size()
                + " clipped=" + clipped;
        lastPlan3dBounds = bounds;
        lastPlan3dRoleCounts = counts;
        lastPlan3dLayers = layers;

        Map<String, Object> liveCounts = new LinkedHashMap<>();
        liveCounts.put("placed", livePlaced);
        liveCounts.put("missing-placeable", liveMissing);
        liveCounts.put("blocked-or-different", liveBlocked);
        liveCounts.put("unknown-unloaded-or-other-region", liveUnknown);
        liveCounts.put("planned-shown", plannedBlocks.size());
        double progress = plannedBlocks.isEmpty() ? 0.0D : Math.round((livePlaced * 1000.0D) / plannedBlocks.size()) / 10.0D;
        liveCounts.put("progress-percent", progress);
        liveCounts.put("role-progress", liveRoleProgress(liveRoleCounts));
        lastLive3dSummary = "live-vs-plan species=" + dna.species().id()
                + " stage=" + dna.maturityStage()
                + " intent=" + dna.currentIntent()
                + " placed=" + livePlaced
                + " missing=" + liveMissing
                + " blocked=" + liveBlocked
                + " unknown=" + liveUnknown
                + " progress=" + progress + "%"
                + (world == null ? " world=unavailable" : " world=" + world.getName());
        lastLive3dStatusCounts = liveCounts;
        lastLive3dLayers = liveLayers;
    }

    private void countLiveRole(Map<TreeBlockRole, int[]> counts, PlannedTreeBlock plannedBlock, int index) {
        if (plannedBlock == null) {
            return;
        }
        int[] roleCounts = counts.computeIfAbsent(plannedBlock.role(), ignored -> new int[5]);
        roleCounts[index]++;
        roleCounts[4]++;
    }

    private Map<String, Object> liveRoleProgress(Map<TreeBlockRole, int[]> counts) {
        Map<String, Object> progress = new LinkedHashMap<>();
        for (TreeBlockRole role : TreeBlockRole.values()) {
            int[] roleCounts = counts.get(role);
            if (roleCounts == null || roleCounts[4] == 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("placed", roleCounts[0]);
            row.put("missing-placeable", roleCounts[1]);
            row.put("blocked-or-different", roleCounts[2]);
            row.put("unknown-unloaded-or-other-region", roleCounts[3]);
            row.put("planned-shown", roleCounts[4]);
            row.put("progress-percent", Math.round((roleCounts[0] * 1000.0D) / roleCounts[4]) / 10.0D);
            row.put("notes", "## Role progress tells whether the visible tree is unfinished by trunk, branch, canopy, or detail.");
            progress.put(role.name(), row);
        }
        return progress;
    }

    private char liveTokenFor(TreeEvolutionConfig config, World world, PlannedTreeBlock plannedBlock, char planToken) {
        if (plannedBlock == null) {
            return '.';
        }
        if (world == null) {
            return '?';
        }
        int chunkX = plannedBlock.x() >> 4;
        int chunkZ = plannedBlock.z() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            return '?';
        }
        Block liveBlock = world.getBlockAt(plannedBlock.x(), plannedBlock.y(), plannedBlock.z());
        Material live = liveBlock.getType();
        if (live == plannedBlock.material()) {
            return Character.toUpperCase(planToken);
        }
        if (live.isAir() || config.isReplaceable(live)) {
            return Character.toLowerCase(planToken);
        }
        return 'X';
    }

    private char symbolFor(PlannedTreeBlock block) {
        if (block.role() == TreeBlockRole.BRANCH && block.branchStep() == 1) {
            return 'A';
        }
        return switch (block.role()) {
            case TRUNK -> 'T';
            case BRANCH -> 'B';
            case CANOPY -> 'L';
            case ROOT -> 'R';
            case VINE -> 'V';
            case GROUND_DETAIL -> 'U';
            case FALLEN_LOG -> 'F';
            case SAPLING -> 'S';
        };
    }

    private int symbolPriority(char symbol) {
        return switch (Character.toUpperCase(symbol)) {
            case 'T' -> 7;
            case 'A' -> 6;
            case 'B' -> 6;
            case 'R' -> 5;
            case 'L' -> 4;
            case 'V' -> 3;
            case 'F' -> 2;
            case 'S' -> 1;
            case 'U' -> 0;
            default -> -1;
        };
    }

    private void save(SlowTreesPlugin plugin, TreeEvolutionConfig config) {
        saveTrace(plugin, config);
        saveMap(plugin);
        save3dDebug(plugin, config);
    }

    private void saveTrace(SlowTreesPlugin plugin, TreeEvolutionConfig config) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("session-started-at", sessionStartedAt);
        yaml.set("enabled", config.enabled());
        yaml.set("step-ticks", config.stepTicks());
        yaml.set("testing-enabled", config.testingEnabled());
        yaml.set("counters.searches", searches.get());
        yaml.set("counters.candidates", candidates.get());
        yaml.set("counters.dna-created", dnaCreated.get());
        yaml.set("counters.dna-loaded", dnaLoaded.get());
        yaml.set("counters.planned", planned.get());
        yaml.set("counters.placed", placed.get());
        yaml.set("counters.rejected", rejected.get());
        yaml.set("counters.stalled", stalled.get());
        yaml.set("counters.forced-steps", forcedSteps.get());
        yaml.set("counters.intent-updates", intentUpdates.get());
        yaml.set("counters.stage-transitions", stageTransitions.get());
        yaml.set("counters.dna-normalized", dnaNormalized.get());
        yaml.set("last-plan-summary", lastPlanSummary);
        yaml.set("last-plan-next-10", lastPlanPreview);
        yaml.set("last-stage-snapshot", lastStageSnapshot);
        yaml.set("last-lineage-summary", lastLineageSummary);
        yaml.set("recent-events", snapshot());
        yaml.set("notes", "## Tree evolution trace. Includes sample inspiration, personality, rarity, ancient/age state, trunk width, canopy layers, lineage, map state, and next planned blocks without extra commands.");
        saveYaml(plugin, yaml, "tree-evolution-trace.debug.yml");
    }

    private void saveMap(SlowTreesPlugin plugin) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("session-started-at", sessionStartedAt);
        yaml.set("legend.A", "latest placed/action column");
        yaml.set("legend.T", "log/trunk/root visible at surface");
        yaml.set("legend.L", "leaf/canopy visible at surface");
        yaml.set("legend.V", "vine visible at surface");
        yaml.set("legend.U", "understory detail such as litter, grass, fern, petals");
        yaml.set("legend.G", "natural ground");
        yaml.set("legend.?", "unloaded chunk");
        yaml.set("center", lastMapCenter);
        yaml.set("rows", lastMapRows);
        yaml.set("3d-debug-file", "tree-evolution-3dDebug.yml");
        yaml.set("notes", "## Tree evolution map preview. Surface rows show the live world near the last action. Detailed target tree Y-layer slices are written separately to tree-evolution-3dDebug.yml.");
        saveYaml(plugin, yaml, "tree-evolution-map.debug.yml");
    }

    private void save3dDebug(SlowTreesPlugin plugin, TreeEvolutionConfig config) {
        if (!config.debug3dEnabled()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("session-started-at", sessionStartedAt);
        yaml.set("enabled", true);
        yaml.set("testing.enabled", config.testingEnabled());
        yaml.set("testing.stage-acceleration-enabled", config.testingStageAccelerationEnabled());
        yaml.set("testing.stage-age-gates.small-to-medium", config.smallToMediumAge());
        yaml.set("testing.stage-age-gates.medium-to-mature", config.mediumToMatureAge());
        yaml.set("testing.stage-age-gates.mature-to-ancient", config.matureToAncientAge());
        yaml.set("testing.allow-any-rarity-ancient", config.allowAnyRarityAncient());
        yaml.set("testing.stage-burst-delay-multiplier", config.stageBurstDelayMultiplier());
        yaml.set("testing.breathing-skip-chance", config.breathingSkipChance());
        yaml.set("testing.placement-mode", config.testingEnabled()
                ? "## Fast testing: one physical block per cycle; stage age/progress is tracked separately from live 3D completion."
                : "survival");
        yaml.set("stage-ladder.SMALL", "seedling/young shape: first trunk lift and early canopy coverage");
        yaml.set("stage-ladder.MEDIUM", "young tree: starts branch/canopy burst after reaching its small-stage height");
        yaml.set("stage-ladder.MATURE", "full tree: fills species-shaped crown and stronger branch profile");
        yaml.set("stage-ladder.ANCIENT", "old-growth tree: rare/landmark expansion, layered canopy, extra detail");
        yaml.set("last-stage-snapshot", last3dStageSnapshot.isEmpty() ? lastStageSnapshot : last3dStageSnapshot);
        yaml.set("plan-3d.legend.O", "tree DNA base/stump position when empty in this layer");
        yaml.set("plan-3d.legend.lowercase", "tree DNA base/stump position with planned block on it");
        yaml.set("plan-3d.legend.T", "planned trunk log");
        yaml.set("plan-3d.legend.A", "planned branch anchor / first connected branch segment");
        yaml.set("plan-3d.legend.B", "planned branch log after the anchor; each segment waits for its parent");
        yaml.set("plan-3d.legend.L", "planned leaf/canopy block");
        yaml.set("plan-3d.legend.R", "planned root");
        yaml.set("plan-3d.legend.V", "planned vine");
        yaml.set("plan-3d.legend.U", "planned understory/ground detail");
        yaml.set("plan-3d.legend.F", "planned fallen log");
        yaml.set("plan-3d.legend.S", "planned sapling/offspring");
        yaml.set("plan-3d.legend.dot", "empty planned space");
        yaml.set("plan-3d.summary", lastPlan3dSummary);
        yaml.set("plan-3d.bounds", lastPlan3dBounds);
        yaml.set("plan-3d.role-counts", lastPlan3dRoleCounts);
        yaml.set("plan-3d.layers", lastPlan3dLayers);
        yaml.set("live-3d.legend.uppercase", "planned block is already present in the live world");
        yaml.set("live-3d.legend.lowercase", "planned block is missing but the live block is placeable");
        yaml.set("live-3d.legend.X", "planned block is blocked by a different non-replaceable live block");
        yaml.set("live-3d.legend.?", "live block was not checked because chunk is unloaded or not owned by current Folia region");
        yaml.set("live-3d.legend.dot", "not part of the shown target plan");
        yaml.set("live-3d.summary", lastLive3dSummary);
        yaml.set("live-3d.status-counts", lastLive3dStatusCounts);
        yaml.set("live-3d.layers", lastLive3dLayers);
        yaml.set("recent-stage-events", stageSnapshot());
        yaml.set("notes", "## 3dDebug stage/shape trace. plan-3d is the target. live-3d overlays actual world progress: uppercase is placed, lowercase is still missing/placeable, X is blocked, ? is unloaded or another Folia region.");
        saveYaml(plugin, yaml, "tree-evolution-3dDebug.yml");
    }

    private void saveReplayDebug(SlowTreesPlugin plugin, TreeEvolutionConfig config) {
        if (!config.debugReplayEnabled()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("session-started-at", sessionStartedAt);
        yaml.set("enabled", true);
        yaml.set("sample-limit", config.debugReplaySampleLimit());
        yaml.set("summary", lastReplaySummary);
        yaml.set("role-progress", lastReplayRoleProgress);
        yaml.set("provenance-counts", lastReplayProvenanceCounts);
        yaml.set("samples", lastReplaySamples);
        yaml.set("legend.MATCHED_PLAN", "planned block already exists in the live world");
        yaml.set("legend.MISSING_REPLACEABLE", "missing but air/replaceable, so normal growth can place it");
        yaml.set("legend.LOWER_TRUNK_NATURAL_GROUND", "lower thick trunk can absorb natural ground instead of treating it like player build");
        yaml.set("legend.NATURAL_TREE_MATERIAL", "existing tree material at planned position; likely organic overlap/progress");
        yaml.set("legend.LIQUID", "water/lava blocks the planned tree block");
        yaml.set("legend.PLAYER_OR_FOREIGN_BLOCK", "solid non-natural obstruction");
        yaml.set("legend.UNCHECKED_WORLD_UNAVAILABLE", "debug ran without a world reference");
        yaml.set("legend.UNCHECKED_CHUNK_OR_REGION", "chunk unloaded or not owned by this Folia region");
        yaml.set("notes", "## Replay/provenance trace. Use this when the planned smoke shape and live 3D shape disagree: samples name the exact live block and why the planner thinks it is placed, waiting, blocked, or unchecked.");
        saveYaml(plugin, yaml, "tree-evolution-replay.debug.yml");
    }
    private void saveYaml(SlowTreesPlugin plugin, YamlConfiguration yaml, String name) {
        File file = new File(plugin.getDataFolder(), name);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for " + name + ".");
            return;
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save SlowTrees " + name + ".", ex);
        }
    }

    private List<String> snapshot() {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents);
        }
    }

    private List<String> stageSnapshot() {
        synchronized (recentStageEvents) {
            return new ArrayList<>(recentStageEvents);
        }
    }

    private Map<String, Object> stageSnapshot(TreeEvolutionConfig config, TreeDna dna, TreePlan plan) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("species", dna.species().id());
        snapshot.put("base", dna.baseX() + "," + dna.baseY() + "," + dna.baseZ());
        snapshot.put("stage", dna.maturityStage().name());
        snapshot.put("age", dna.age());
        snapshot.put("intent", dna.currentIntent().name());
        snapshot.put("plan-cursor", dna.planCursor());
        snapshot.put("blocked-attempts", dna.blockedAttempts());
        snapshot.put("stage-cleanup-burst", dna.stageCleanupBurst());
        snapshot.put("stage-growth-burst", dna.stageGrowthBurst());
        snapshot.put("virtual-stage-age", dna.age());
        snapshot.put("virtual-stage-progress", virtualStageProgress(config, dna));
        snapshot.put("target-height", dna.targetHeight());
        snapshot.put("stage-visible-height", TreeSpeciesStageStyle.visibleHeight(dna));
        snapshot.put("stage-branches", TreeSpeciesStageStyle.branchCount(dna));
        snapshot.put("stage-canopy-radius", TreeSpeciesStageStyle.canopyRadiusX(dna)
                + "x" + TreeSpeciesStageStyle.canopyRadiusY(dna)
                + "x" + TreeSpeciesStageStyle.canopyRadiusZ(dna));
        snapshot.put("stage-canopy-layers", TreeSpeciesStageStyle.canopyLayerCount(dna));
        snapshot.put("trunk-width", dna.trunkWidth());
        snapshot.put("personality", dna.personality().name());
        snapshot.put("rarity", dna.rarity().name());
        snapshot.put("sample", dna.profileSampleId());
        snapshot.put("source", dna.profileSampleSource());
        snapshot.put("planned-blocks", plan.size());
        snapshot.put("notes", "## Stage snapshot is refreshed whenever a target tree plan is built.");
        return snapshot;
    }

    private Map<String, Object> virtualStageProgress(TreeEvolutionConfig config, TreeDna dna) {
        Map<String, Object> progress = new LinkedHashMap<>();
        int age = dna.age();
        int previousGate = switch (dna.maturityStage()) {
            case SMALL -> 0;
            case MEDIUM -> config.smallToMediumAge();
            case MATURE -> config.mediumToMatureAge();
            case ANCIENT -> config.matureToAncientAge();
        };
        int nextGate = switch (dna.maturityStage()) {
            case SMALL -> config.smallToMediumAge();
            case MEDIUM -> config.mediumToMatureAge();
            case MATURE -> config.matureToAncientAge();
            case ANCIENT -> Math.max(config.matureToAncientAge(), age);
        };
        int span = Math.max(1, nextGate - previousGate);
        int insideStage = Math.max(0, age - previousGate);
        double percent = dna.maturityStage() == TreeMaturityStage.ANCIENT
                ? 100.0D
                : Math.min(100.0D, Math.round((insideStage * 1000.0D) / span) / 10.0D);
        progress.put("stage", dna.maturityStage().name());
        progress.put("age", age);
        progress.put("previous-gate", previousGate);
        progress.put("next-gate", nextGate);
        progress.put("percent-to-next-stage", percent);
        progress.put("notes", "## Virtual stage age advances by one per placed/pruned block. live-3d.progress-percent shows actual block completion.");
        return progress;
    }

    private String format(Block block) {
        return block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private boolean isLog(Material material) {
        return material.name().endsWith("_LOG") || material.name().endsWith("_WOOD");
    }

    private boolean isLeaf(Material material) {
        return material.name().endsWith("_LEAVES");
    }

    private double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
