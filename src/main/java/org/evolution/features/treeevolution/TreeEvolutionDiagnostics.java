package org.evolution.features.treeevolution;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.configuration.file.YamlConfiguration;
import org.evolution.coreparts.EvolutionPlugin;
import org.evolution.coreparts.DebugFileRotator;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;
import org.evolution.coreparts.ResourceReporter.ReportSample;

final class TreeEvolutionDiagnostics {
    private final AtomicLong searches = new AtomicLong();
    private final AtomicLong candidates = new AtomicLong();
    private final AtomicLong dnaCreated = new AtomicLong();
    private final AtomicLong dnaLoaded = new AtomicLong();
    private final AtomicLong planned = new AtomicLong();
    private final AtomicLong placed = new AtomicLong();
    private final AtomicLong pruned = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong stalled = new AtomicLong();
    private final AtomicLong forcedSteps = new AtomicLong();
    private final AtomicLong intentUpdates = new AtomicLong();
    private final AtomicLong stageTransitions = new AtomicLong();
    private final AtomicLong dnaNormalized = new AtomicLong();
    private final AtomicLong constructorDecisions = new AtomicLong();
    private final AtomicLong cleanTreeObservations = new AtomicLong();
    private final AtomicLong observationPromotions = new AtomicLong();
    private final AtomicBoolean saveRunning = new AtomicBoolean();
    private final AtomicBoolean saveDirty = new AtomicBoolean();
    private final AtomicBoolean deformationAnalysisRunning =
            new AtomicBoolean();
    private final AtomicLong nextSaveMillis = new AtomicLong();
    private final AtomicLong next3dBuildMillis = new AtomicLong();
    private final AtomicLong nextSurfaceMapBuildMillis = new AtomicLong();
    private final AtomicLong nextAutomaticVoxelScanMillis =
            new AtomicLong();
    private final Object threeDimensionalSnapshotLock = new Object();
    private final Deque<String> recentEvents = new ArrayDeque<>();
    private final Deque<String> recentStageEvents = new ArrayDeque<>();
    private final Deque<Map<String, Object>> recentConstructorStageFrames =
            new ArrayDeque<>();
    private final LinkedHashMap<String, Map<String, Object>>
            liveVoxelEnvironmentCaptures = new LinkedHashMap<>();
    private final LinkedHashMap<String, DeformationAnalysisJob>
            pendingDeformationAnalyses = new LinkedHashMap<>();
    private final TreeDeformationHistoryStore deformationHistory =
            new TreeDeformationHistoryStore();
    private final TreeDeformationAudit deformationAudit =
            new TreeDeformationAudit();
    private final TreeTargetConformanceAudit targetConformanceAudit =
            new TreeTargetConformanceAudit();
    private final ConcurrentMap<String, Long> nextAnomalyCaptureMillis =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedSurfaceToken> surfaceTokenCache =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Character>>
            previousVoxelStatusByTree = new ConcurrentHashMap<>();
    private final TreeConstructorStageTimeline constructorStageTimeline =
            new TreeConstructorStageTimeline();
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
    private volatile Map<String, Integer> lastPlan3dAugmentCounts = Map.of();
    private volatile Map<String, List<String>>
            lastPlan3dAugmentCoordinateIndex = Map.of();
    private volatile List<Map<String, Object>> lastPlan3dLayers = List.of();
    private volatile String lastLive3dSummary = "none";
    private volatile Map<String, Object> lastLive3dStatusCounts = Map.of();
    private volatile List<Map<String, Object>> lastLive3dLayers = List.of();
    private volatile String lastVoxelGridSummary = "none";
    private volatile Map<String, Object> lastVoxelGridAxes = Map.of();
    private volatile List<Map<String, Object>> lastVoxelModelLayers = List.of();
    private volatile List<Map<String, Object>> lastVoxelLiveStatusLayers = List.of();
    private volatile List<String> lastVoxelStatusDelta = List.of();
    private volatile Map<String, Object> lastReplaySummary = Map.of();
    private volatile Map<String, Object> lastReplayRoleProgress = Map.of();
    private volatile Map<String, Integer> lastReplayProvenanceCounts = Map.of();
    private volatile Map<String, Integer> lastReplayAugmentCounts = Map.of();
    private volatile List<Map<String, Object>> lastReplaySamples = List.of();
    private volatile String lastLineageSummary = "none";
    private volatile String lastConstructorSummary = "none";
    private volatile String last3dTreeKey = "none";
    private volatile EvolutionPlugin plugin;

    void initialize(EvolutionPlugin plugin) {
        this.plugin = plugin;
        deformationHistory.load(plugin);
    }

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
                + " variant=" + dna.variant().id()
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
                + " source-pattern=" + sourcePattern(dna)
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
                + ", variant=" + dna.variant().id()
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
                + ", planned-blocks=" + plan.size()
                + ", blueprint="
                + (plan.blueprintValidation().approved()
                        ? "approved" : "rejected")
                + ", coordinate-conflicts="
                + plan.coordinateConflictCount();
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
            lastReplayAugmentCounts = replay.augmentCounts();
            lastReplaySamples = replay.samples();
        }
        if (force3d || shouldBuild3d(config)) {
            synchronized (threeDimensionalSnapshotLock) {
                last3dTreeKey = dna.key();
                last3dStageSnapshot = stageSnapshot;
                buildPlan3dMap(config, dna, orderedBlocks, world);
            }
        }
        event(config, "[TRACE][tree-evolution] plan.target " + lastPlanSummary);
    }

    private boolean shouldBuild3d(TreeEvolutionConfig config) {
        if (!config.debug3dEnabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long next = next3dBuildMillis.get();
        return now >= next
                && next3dBuildMillis.compareAndSet(next, now + 30_000L);
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

    TreeConstructorStageTimeline.Boundary recordConstructorDecision(
            TreeEvolutionConfig config,
            TreeDna dna,
            TreeConstructionDecision decision,
            TreeGrowthQueuePolicy.Completion completion,
            TreeGrowthQueuePolicy.Budget budget,
            String executorName
    ) {
        constructorDecisions.incrementAndGet();
        TreeConstructorStageTimeline.Boundary stageBoundary =
                constructorStageTimeline.enter(dna.key(), decision);
        lastConstructorSummary = decision.marker()
                + " tree=" + dna.key()
                + " executor=" + executorName
                + " stage=" + dna.maturityStage()
                + " trunk=" + completion.trunkSummary()
                + "/target=" + Math.round(budget.trunkPercent() * 100.0D) + "%"
                + " branch=" + completion.branchSummary()
                + "/target=" + Math.round(budget.branchPercent() * 100.0D) + "%"
                + " canopy=" + completion.canopySummary()
                + "/target=" + Math.round(budget.canopyPercent() * 100.0D) + "%"
                + " final-audit=" + decision.finalAudit().marker()
                + " audit-first-failure="
                + decision.finalAudit().firstFailure()
                + " audit-detail=" + decision.finalAudit().detail()
                + " reason=" + decision.reason();
        event(config, "[TRACE][tree-evolution] " + lastConstructorSummary
                + " ## one hierarchy attachment owns this tree action");
        for (TreeConstructorStageTimeline.Frame frame
                : stageBoundary.frames()) {
            TreeConstructorStageTimeline.StageIdentity stage =
                    frame.stage();
            stageEvent(config,
                    "[SMOKE][" + frame.boundary() + "]["
                            + stage.smokeTag().name() + "] tree="
                            + dna.key() + " transition="
                            + stage.transition() + " phase="
                            + stage.phase() + " subrule="
                            + stage.subrule() + " attachment="
                            + stage.attachment()
                            + " ## live boundary matches the replay XYZ-time frame index");
        }
        return stageBoundary;
    }

    void recordConstructorStageFrames(
            TreeEvolutionConfig config,
            TreeDna dna,
            TreeConstructorStageTimeline.Boundary boundary,
            TreePlan plan,
            List<PlannedTreeBlock> orderedBlocks,
            World world
    ) {
        if (!config.debug3dEnabled() || world == null
                || boundary == null || !boundary.changed()) {
            return;
        }
        synchronized (threeDimensionalSnapshotLock) {
            last3dTreeKey = dna.key();
            Map<String, Object> snapshot = new LinkedHashMap<>(
                    stageSnapshot(config, dna, plan));
            TreeConstructorStageTimeline.Frame latest =
                    boundary.frames().get(boundary.frames().size() - 1);
            TreeConstructorStageTimeline.StageIdentity latestStage =
                    latest.stage();
            snapshot.put("constructor-smoke-tag",
                    latestStage.smokeTag().name());
            snapshot.put("constructor-phase",
                    latestStage.phase().name());
            snapshot.put("constructor-subrule",
                    latestStage.subrule().name());
            snapshot.put("constructor-attachment",
                    latestStage.attachment().name());
            snapshot.put("constructor-boundary",
                    latest.boundary().name());
            snapshot.put("constructor-transition",
                    latestStage.transition());
            snapshot.put("captured-at", Instant.now().toString());
            boolean finalBoundary = latestStage.smokeTag()
                    == org.evolution.features.treeevolution.constructor
                            .TreeConstructionSmokeTag
                            .TREE_99_STAGE_COMPLETE;
            boolean deepCapture = finalBoundary || shouldBuild3d(config);
            snapshot.put("audit-level",
                    deepCapture ? "DEEP_VOXEL" : "CONTINUOUS_CONTRACT");
            snapshot.put("notes",
                    "## Exact live EXIT/ENTER boundary; compare transition and smoke tag with stage-frame-index.csv.");
            last3dStageSnapshot = Map.copyOf(snapshot);
            if (deepCapture) {
                buildPlan3dMap(config, dna, orderedBlocks, world);
            }
            if (finalBoundary && deepCapture) {
                // ## Completion is the canonical before/current/target
                // comparison. Never let its anomaly audit reuse a slightly
                // older blocked-stage capture.
                liveVoxelEnvironmentCaptures.remove(dna.key());
            }
            if (deepCapture) {
                captureLiveVoxelEnvironment(
                        config, dna, orderedBlocks, world, true);
            }

            for (TreeConstructorStageTimeline.Frame stageFrame
                    : boundary.frames()) {
                appendConstructorStageFrame(
                        dna, stageFrame, snapshot.get("captured-at"),
                        deepCapture);
            }
            int maximum = Math.max(1, Math.min(
                    160, config.debug3dRecentStageEvents()));
            while (recentConstructorStageFrames.size() > maximum) {
                recentConstructorStageFrames.removeFirst();
            }
            if (deepCapture) {
                queuePotentialDeformation(
                        config, dna, plan, orderedBlocks, world,
                        "constructor-stage-boundary",
                        finalBoundary,
                        false);
            }
        }
    }

    void recordConstructorCompletionFrame(
            TreeEvolutionConfig config,
            TreeDna dna,
            TreeConstructionDecision decision,
            TreePlan plan,
            List<PlannedTreeBlock> orderedBlocks,
            World world
    ) {
        if (decision.smokeTag()
                != org.evolution.features.treeevolution.constructor
                        .TreeConstructionSmokeTag.TREE_99_STAGE_COMPLETE) {
            return;
        }
        TreeConstructorStageTimeline.Boundary boundary =
                constructorStageTimeline.complete(dna.key(), decision);
        recordConstructorStageFrames(
                config, dna, boundary, plan, orderedBlocks, world);
    }

    boolean recordBlockedConstructorEnvironment(
            TreeEvolutionConfig config,
            TreeDna dna,
            TreePlan plan,
            List<PlannedTreeBlock> orderedBlocks,
            World world
    ) {
        if (!config.debug3dEnabled() || world == null
                || plan == null || orderedBlocks.isEmpty()) {
            return false;
        }
        synchronized (threeDimensionalSnapshotLock) {
            boolean captured = captureLiveVoxelEnvironment(
                    config, dna, orderedBlocks, world, true);
            if (captured) {
                queuePotentialDeformation(
                        config, dna, plan, orderedBlocks, world,
                        "blocked-constructor", false, true);
            }
            return captured;
        }
    }

    private void appendConstructorStageFrame(
            TreeDna dna,
            TreeConstructorStageTimeline.Frame frame,
            Object capturedAt,
            boolean deepCapture
    ) {
        TreeConstructorStageTimeline.StageIdentity stage = frame.stage();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("tree", dna.key());
        output.put("transition", stage.transition());
        output.put("boundary", frame.boundary().name());
        output.put("smoke-tag", stage.smokeTag().name());
        output.put("phase", stage.phase().name());
        output.put("subrule", stage.subrule().name());
        output.put("attachment", stage.attachment().name());
        output.put("captured-at", capturedAt);
        output.put("audit-level",
                deepCapture ? "DEEP_VOXEL" : "CONTINUOUS_CONTRACT");
        if (deepCapture) {
            output.put("live-summary", lastLive3dSummary);
            output.put("live-status-counts",
                    new LinkedHashMap<>(lastLive3dStatusCounts));
            output.put("voxel-status-delta",
                    new ArrayList<>(lastVoxelStatusDelta));
        }
        output.put("notes",
                "## Production XYZ-time checkpoint. Continuous frames keep contracts; deep frames add only changed voxels.");
        recentConstructorStageFrames.addLast(Map.copyOf(output));
    }

    private boolean captureLiveVoxelEnvironment(
            TreeEvolutionConfig config,
            TreeDna dna,
            List<PlannedTreeBlock> orderedBlocks,
            World world,
            boolean refreshFailureCapture
    ) {
        long now = System.currentTimeMillis();
        Map<String, Object> existing =
                liveVoxelEnvironmentCaptures.remove(dna.key());
        if (existing != null) {
            // ## Access order keeps actively failing/reported trees in the
            // export window. A blocked constructor may refresh after a short
            // cooldown so its atomically paired DNA and voxels describe the
            // same moment without scanning thousands of blocks every tick.
            long capturedMillis = existing.get("captured-millis")
                    instanceof Number number ? number.longValue() : 0L;
            if (!refreshFailureCapture
                    || now - capturedMillis < 3_000L) {
                liveVoxelEnvironmentCaptures.put(dna.key(), existing);
                return false;
            }
        }
        if (orderedBlocks.isEmpty()) {
            return false;
        }
        int radius = Math.max(4, Math.min(
                12, config.debugMapRadius()));
        int minX = dna.baseX() - radius;
        int maxX = dna.baseX() + radius;
        int minZ = dna.baseZ() - radius;
        int maxZ = dna.baseZ() + radius;
        int plannedMinY = orderedBlocks.stream()
                .mapToInt(PlannedTreeBlock::y).min()
                .orElse(dna.baseY());
        int plannedMaxY = orderedBlocks.stream()
                .mapToInt(PlannedTreeBlock::y).max()
                .orElse(dna.baseY());
        int minY = Math.max(world.getMinHeight(),
                Math.min(dna.baseY() - 2, plannedMinY - 1));
        int maxY = Math.min(world.getMaxHeight() - 1,
                Math.min(minY + 47, plannedMaxY + 2));
        Map<String, PlannedTreeBlock> planByKey = new HashMap<>();
        for (PlannedTreeBlock block : orderedBlocks) {
            planByKey.put(block.key(), block);
        }

        List<Map<String, Object>> cells = new ArrayList<>();
        List<Map<String, Object>> unreadable = new ArrayList<>();
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        EvolutionPlugin currentPlugin = plugin;
        ReportSample captureSample = currentPlugin == null
                ? null : currentPlugin.resourceReporter().begin(
                        "tree-evolution",
                        "diagnostics.voxel-capture.region");
        try {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ;
                        chunkZ <= maxChunkZ; chunkZ++) {
                    int chunkMinX = Math.max(minX, chunkX << 4);
                    int chunkMaxX = Math.min(maxX, (chunkX << 4) + 15);
                    int chunkMinZ = Math.max(minZ, chunkZ << 4);
                    int chunkMaxZ = Math.min(maxZ, (chunkZ << 4) + 15);
                    boolean loaded = world.isChunkLoaded(chunkX, chunkZ);
                    boolean owned = loaded && Bukkit.isOwnedByCurrentRegion(
                            world, chunkX, chunkZ, 0);
                    if (!loaded || !owned) {
                        Map<String, Object> area = new LinkedHashMap<>();
                        area.put("min-x", chunkMinX - dna.baseX());
                        area.put("min-z", chunkMinZ - dna.baseZ());
                        area.put("max-x", chunkMaxX - dna.baseX());
                        area.put("max-z", chunkMaxZ - dna.baseZ());
                        area.put("gate", loaded
                                ? "REGION_NOT_OWNED" : "CHUNK_UNLOADED");
                        unreadable.add(area);
                        continue;
                    }
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
                            for (int x = chunkMinX;
                                    x <= chunkMaxX; x++) {
                                appendLiveEnvironmentCell(
                                        config, dna, world, planByKey,
                                        cells, x, y, z);
                            }
                        }
                    }
                }
            }
        } finally {
            if (captureSample != null) {
                long inspected = (long) (maxX - minX + 1)
                        * (maxY - minY + 1)
                        * (maxZ - minZ + 1);
                captureSample.workUnits(inspected)
                        .changedUnits(cells.size())
                        .detail("tree=" + dna.key()
                                + " sparse-cells=" + cells.size()
                                + " unreadable-areas=" + unreadable.size())
                        .close();
            }
        }

        Map<String, Object> capture = new LinkedHashMap<>();
        capture.put("tree", dna.key());
        capture.put("species", dna.species().id());
        capture.put("variant", dna.variant().id());
        capture.put("stage", dna.maturityStage().name());
        capture.put("captured-at", Instant.now().toString());
        capture.put("captured-millis", now);
        capture.put("dna-snapshot-yaml", dnaSnapshotYaml(dna));
        capture.put("ledger-fingerprint", ledgerFingerprint(dna));
        capture.put("relative-bounds", (minX - dna.baseX()) + ","
                + (minY - dna.baseY()) + ","
                + (minZ - dna.baseZ()) + " -> "
                + (maxX - dna.baseX()) + ","
                + (maxY - dna.baseY()) + ","
                + (maxZ - dna.baseZ()));
        capture.put("cells", List.copyOf(cells));
        capture.put("unreadable-areas", List.copyOf(unreadable));
        capture.put("notes", "## Sparse local world capture. Coordinates "
                + "are relative to the stump; the fixture exporter removes "
                + "the live tree key and timestamps.");
        liveVoxelEnvironmentCaptures.put(
                dna.key(), Map.copyOf(capture));
        while (liveVoxelEnvironmentCaptures.size() > 16) {
            String oldest = liveVoxelEnvironmentCaptures.keySet()
                    .iterator().next();
            liveVoxelEnvironmentCaptures.remove(oldest);
        }
        return true;
    }

    private String dnaSnapshotYaml(TreeDna dna) {
        YamlConfiguration snapshot = new YamlConfiguration();
        dna.writeTo(snapshot.createSection("dna"));
        return snapshot.saveToString();
    }

    private String ledgerFingerprint(TreeDna dna) {
        return dna.shapeRevision() + ":" + dna.maturityStage()
                + ":" + dna.currentIntent()
                + ":" + dna.originalShapeLogs().stream().sorted().toList().hashCode()
                + ":" + dna.originalShapeLeaves().stream().sorted().toList().hashCode()
                + ":" + dna.retiredOriginalShapeLeaves().stream().sorted().toList().hashCode()
                + ":" + dna.evolvedShapeLogs().stream().sorted().toList().hashCode()
                + ":" + dna.evolvedShapeLeaves().stream().sorted().toList().hashCode();
    }

    private void appendLiveEnvironmentCell(
            TreeEvolutionConfig config,
            TreeDna dna,
            World world,
            Map<String, PlannedTreeBlock> planByKey,
            List<Map<String, Object>> cells,
            int x,
            int y,
            int z
    ) {
        String coordinateKey = x + ":" + y + ":" + z;
        String receiptKey = dna.worldId() + ":" + coordinateKey;
        Block liveBlock = world.getBlockAt(x, y, z);
        Material material = liveBlock.getType();
        boolean persistedReceipt = dna.originalShapeLogs()
                        .contains(receiptKey)
                || (dna.originalShapeLeaves().contains(receiptKey)
                        && !dna.retiredOriginalShapeLeaves()
                                .contains(receiptKey))
                || dna.evolvedShapeLogs().contains(receiptKey)
                || dna.evolvedShapeLeaves().contains(receiptKey);
        if (material.isAir() && !persistedReceipt) {
            return;
        }
        PlannedTreeBlock planned = planByKey.get(coordinateKey);
        String category;
        boolean treeMaterial = isLog(material) || isLeaf(material);
        TreeBlockRole role = liveRole(material, planned);
        if (material.isAir()) {
            category = "AIR_OVERRIDE";
        } else if (treeMaterial
                && (dna.evolvedShapeLogs().contains(receiptKey)
                || dna.evolvedShapeLeaves().contains(receiptKey))) {
            category = "EVOLVED";
        } else if (treeMaterial
                && (dna.originalShapeLogs().contains(receiptKey)
                || (dna.originalShapeLeaves().contains(receiptKey)
                        && !dna.retiredOriginalShapeLeaves()
                                .contains(receiptKey)))) {
            category = "SOURCE";
        } else if (treeMaterial && planned != null
                && material == planned.material()) {
            // ## Material completion without a DNA receipt is the exact live
            // state that can strand TREE_11. Replay must preserve it instead
            // of omitting it as air or misclassifying it as generic neighbor.
            category = "PLANNED_UNOWNED";
        } else if (treeMaterial) {
            category = "NEIGHBOR";
        } else {
            // ## Live material wins over a stale tree receipt. This is the
            // obstruction the production constructor actually encountered.
            category = "ENVIRONMENT";
        }
        boolean replaceable = config.isReplaceable(material);
        if (planned != null && !material.isAir()) {
            BlockProvenance provenance = BlockProvenance.classify(
                    config, dna, planned, material, true, true);
            replaceable = replaceable
                    || provenance.isMissingButPlaceable();
        }
        Map<String, Object> cell = new LinkedHashMap<>();
        cell.put("x", x - dna.baseX());
        cell.put("y", y - dna.baseY());
        cell.put("z", z - dna.baseZ());
        cell.put("material", material.name());
        cell.put("category", category);
        if (role != null) {
            cell.put("role", role.name());
        }
        boolean likelyForeign = isLikelyForeign(material);
        cell.put("natural", !likelyForeign);
        cell.put("replaceable", replaceable);
        cell.put("player-placed", likelyForeign);
        if (planned != null) {
            BlockProvenance provenance = BlockProvenance.classify(
                    config, dna, planned, material, true, true);
            cell.put("planned-material", planned.material().name());
            cell.put("planned-role", planned.role().name());
            cell.put("planner-augment", planned.augment().name());
            cell.put("planner-augment-contract",
                    planned.augment().contract());
            cell.put("provenance", provenance.name());
        }
        if (liveBlock.getBlockData() instanceof Leaves leaves) {
            cell.put("persistent", leaves.isPersistent());
        }
        cells.add(Map.copyOf(cell));
    }

    void recordPotentialDeformation(
            TreeEvolutionConfig config,
            TreeDna dna,
            TreePlan plan,
            List<PlannedTreeBlock> orderedBlocks,
            World world,
            String trigger,
            boolean finalState
    ) {
        if (!config.debug3dEnabled() || world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextAnomalyCaptureMillis.getOrDefault(dna.key(), 0L)) {
            return;
        }
        long nextScan = nextAutomaticVoxelScanMillis.get();
        if (now < nextScan || !nextAutomaticVoxelScanMillis.compareAndSet(
                nextScan, now + 500L)) {
            return;
        }
        synchronized (threeDimensionalSnapshotLock) {
            captureLiveVoxelEnvironment(
                    config, dna, orderedBlocks, world, true);
            queuePotentialDeformation(
                    config, dna, plan, orderedBlocks, world,
                    trigger, finalState, true);
        }
    }

    private void queuePotentialDeformation(
            TreeEvolutionConfig config,
            TreeDna dna,
            TreePlan plan,
            List<PlannedTreeBlock> orderedBlocks,
            World world,
            String trigger,
            boolean finalState,
            boolean force
    ) {
        Map<String, Object> capture =
                liveVoxelEnvironmentCaptures.get(dna.key());
        if (capture == null || world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long eligible = nextAnomalyCaptureMillis.getOrDefault(
                dna.key(), 0L);
        if (now < eligible) {
            return;
        }
        DeformationAnalysisJob job = new DeformationAnalysisJob(
                config, dna.key(), dna.species(), dna.variant(),
                dna.maturityStage(), dna.currentIntent(),
                dna.shapeRevision(), trigger, finalState, force,
                capture, List.copyOf(originalVoxels(dna)),
                List.copyOf(targetVoxels(dna, orderedBlocks)));
        synchronized (pendingDeformationAnalyses) {
            // ## A newer capture supersedes an older pending analysis for the
            // same tree. This bounds diagnostic pressure during rapid stages.
            pendingDeformationAnalyses.remove(dna.key());
            pendingDeformationAnalyses.put(dna.key(), job);
            while (pendingDeformationAnalyses.size() > 16) {
                String oldest = pendingDeformationAnalyses.keySet()
                        .iterator().next();
                pendingDeformationAnalyses.remove(oldest);
            }
        }
        scheduleDeformationAnalysis();
    }

    private void scheduleDeformationAnalysis() {
        EvolutionPlugin currentPlugin = plugin;
        if (currentPlugin == null || !currentPlugin.isEnabled()
                || !deformationAnalysisRunning.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getAsyncScheduler().runNow(currentPlugin, task -> {
            try {
                DeformationAnalysisJob job;
                while ((job = pollDeformationAnalysis()) != null) {
                    analyzePotentialDeformation(currentPlugin, job);
                }
            } finally {
                deformationAnalysisRunning.set(false);
                synchronized (pendingDeformationAnalyses) {
                    if (!pendingDeformationAnalyses.isEmpty()) {
                        scheduleDeformationAnalysis();
                    }
                }
            }
        });
    }

    private DeformationAnalysisJob pollDeformationAnalysis() {
        synchronized (pendingDeformationAnalyses) {
            if (pendingDeformationAnalyses.isEmpty()) {
                return null;
            }
            String oldest = pendingDeformationAnalyses.keySet()
                    .iterator().next();
            return pendingDeformationAnalyses.remove(oldest);
        }
    }

    private void analyzePotentialDeformation(
            EvolutionPlugin plugin,
            DeformationAnalysisJob job
    ) {
        try (ReportSample sample = plugin.resourceReporter().begin(
                "tree-evolution", "diagnostics.deformation-analysis.async")) {
            List<TreeVoxelSnapshotRenderer.Voxel> current =
                    currentVoxels(job.capture());
            sample.workUnits(current.size());
            TreeDeformationAudit.Report audit = deformationAudit.inspect(
                    job.species(), job.variant(), job.maturityStage(),
                    current, job.finalState());
            TreeTargetConformanceAudit.Report conformance =
                    targetConformanceAudit.inspect(
                            current, job.target(),
                            blockedTargetCoordinates(job.capture()));
            sample.workUnits(job.target().size());
            boolean overallPassed = audit.passed()
                    && (!job.finalState() || conformance.passed());
            if (!job.force() && overallPassed) {
                return;
            }
            long now = System.currentTimeMillis();
            long eligible = nextAnomalyCaptureMillis.getOrDefault(
                    job.treeKey(), 0L);
            if (now < eligible) {
                return;
            }
            nextAnomalyCaptureMillis.put(
                    job.treeKey(), now + 10_000L);
            recordAnalyzedDeformation(
                    job, current, audit, conformance, overallPassed);
        }
    }

    private void recordAnalyzedDeformation(
            DeformationAnalysisJob job,
            List<TreeVoxelSnapshotRenderer.Voxel> current,
            TreeDeformationAudit.Report audit,
            TreeTargetConformanceAudit.Report conformance,
            boolean overallPassed
    ) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("captured-at", Instant.now().toString());
        bundle.put("trigger", job.trigger());
        bundle.put("tree", job.treeKey());
        bundle.put("species", job.species().id());
        bundle.put("variant", job.variant().id());
        bundle.put("stage", job.maturityStage().name());
        bundle.put("intent", job.intent().name());
        bundle.put("shape-revision", job.shapeRevision());
        bundle.put("final-state", job.finalState());
        bundle.put("audit-passed", overallPassed);
        bundle.put("quality-audit-passed", audit.passed());
        bundle.put("target-conformance-passed", conformance.passed());
        bundle.put("classification", deformationClassification(
                job.trigger(), job.finalState(), overallPassed));
        bundle.put("requires-constructor-change",
                job.finalState() && !overallPassed);
        bundle.put("audit-failures", audit.failures());
        bundle.put("audit-metrics", audit.metrics());
        bundle.put("target-conformance", conformance.asMap());
        bundle.put("original-snapshot",
                TreeVoxelSnapshotRenderer.snapshot(
                        "original", job.original()));
        bundle.put("current-snapshot",
                TreeVoxelSnapshotRenderer.snapshot("current", current));
        bundle.put("target-snapshot",
                TreeVoxelSnapshotRenderer.snapshot("target", job.target()));
        bundle.put("obstructions", obstructionSnapshot(job.capture()));
        bundle.put("unreadable-areas",
                job.capture().getOrDefault(
                        "unreadable-areas", List.of()));
        bundle.put("hot-coordinate-history",
                deformationHistory.hotCoordinates(job.treeKey()));
        bundle.put("notes", "## Automatic anomaly bundle. Original is the "
                + "captured source tree, current is live plugin-owned/source "
                + "tree matter, and target is the immutable constructor plan. "
                + "The quality audit reads shape independently of planner "
                + "validity; target-conformance separately reports exact "
                + "missing, extra, wrong-role, and environment-blocked voxels.");
        deformationHistory.recordAnomalyBundle(bundle);
        event(job.config(), "[DEBUG][tree-evolution] deformation.capture"
                + " tree=" + job.treeKey() + " trigger=" + job.trigger()
                + " final=" + job.finalState() + " audit="
                + (overallPassed ? "PASS" : audit.failures())
                + " conformance=" + conformance.asMap()
                + " history-file="
                + TreeDeformationHistoryStore.FILE_NAME);
    }

    private Set<String> blockedTargetCoordinates(
            Map<String, Object> capture
    ) {
        Object rawCells = capture.get("cells");
        if (!(rawCells instanceof List<?> cells)) {
            return Set.of();
        }
        Set<String> blocked = new HashSet<>();
        for (Object rawCell : cells) {
            if (!(rawCell instanceof Map<?, ?> raw)
                    || !raw.containsKey("planned-material")
                    || Boolean.TRUE.equals(raw.get("replaceable"))) {
                continue;
            }
            if (String.valueOf(raw.get("material"))
                    .equals(String.valueOf(raw.get("planned-material")))) {
                continue;
            }
            blocked.add(integer(raw.get("x")) + ":"
                    + integer(raw.get("y")) + ":"
                    + integer(raw.get("z")));
        }
        return Set.copyOf(blocked);
    }

    private static String deformationClassification(
            String trigger, boolean finalState, boolean auditPassed) {
        // ## A stage boundary is allowed to show unfinished work. Reserve the
        // actionable label for a completed stage that still fails its
        // independent voxel audit; this keeps debug evidence honest.
        if (finalState) {
            return auditPassed ? "final-healthy" : "final-deformation";
        }
        if (auditPassed) {
            return "blocked-constructor".equals(trigger)
                    ? "healthy-blocked"
                    : "healthy-transition";
        }
        return "transitional-deformation";
    }

    @SuppressWarnings("unchecked")
    private List<TreeVoxelSnapshotRenderer.Voxel> currentVoxels(
            Map<String, Object> capture
    ) {
        Object rawCells = capture.get("cells");
        if (!(rawCells instanceof List<?> cells)) {
            return List.of();
        }
        List<TreeVoxelSnapshotRenderer.Voxel> voxels = new ArrayList<>();
        for (Object rawCell : cells) {
            if (!(rawCell instanceof Map<?, ?> raw)) {
                continue;
            }
            String category = String.valueOf(raw.get("category"));
            if (!("SOURCE".equals(category) || "EVOLVED".equals(category)
                    || "PLANNED_UNOWNED".equals(category))) {
                continue;
            }
            TreeBlockRole role = parseRole(raw.get("role"));
            if (role == null) {
                continue;
            }
            voxels.add(new TreeVoxelSnapshotRenderer.Voxel(
                    integer(raw.get("x")), integer(raw.get("y")),
                    integer(raw.get("z")), role,
                    String.valueOf(raw.get("material"))));
        }
        return voxels;
    }

    private List<TreeVoxelSnapshotRenderer.Voxel> originalVoxels(TreeDna dna) {
        List<TreeVoxelSnapshotRenderer.Voxel> voxels = new ArrayList<>();
        for (String key : dna.originalShapeLogs()) {
            int[] point = relativeReceipt(dna, key);
            if (point != null) {
                voxels.add(new TreeVoxelSnapshotRenderer.Voxel(
                        point[0], point[1], point[2], TreeBlockRole.TRUNK,
                        dna.species().logMaterial().name()));
            }
        }
        for (String key : dna.originalShapeLeaves()) {
            int[] point = relativeReceipt(dna, key);
            if (point != null) {
                voxels.add(new TreeVoxelSnapshotRenderer.Voxel(
                        point[0], point[1], point[2], TreeBlockRole.CANOPY,
                        dna.species().leafMaterial().name()));
            }
        }
        return voxels;
    }

    private List<TreeVoxelSnapshotRenderer.Voxel> targetVoxels(
            TreeDna dna, List<PlannedTreeBlock> orderedBlocks
    ) {
        return orderedBlocks.stream()
                .map(block -> new TreeVoxelSnapshotRenderer.Voxel(
                        block.x() - dna.baseX(),
                        block.y() - dna.baseY(),
                        block.z() - dna.baseZ(),
                        block.role(), block.material().name()))
                .toList();
    }

    private List<Map<String, Object>> obstructionSnapshot(
            Map<String, Object> capture
    ) {
        Object rawCells = capture.get("cells");
        if (!(rawCells instanceof List<?> cells)) {
            return List.of();
        }
        List<Map<String, Object>> obstructions = new ArrayList<>();
        for (Object rawCell : cells) {
            if (!(rawCell instanceof Map<?, ?> raw)
                    || !raw.containsKey("planned-material")) {
                continue;
            }
            boolean replaceable = Boolean.TRUE.equals(raw.get("replaceable"));
            boolean matches = String.valueOf(raw.get("material"))
                    .equals(String.valueOf(raw.get("planned-material")));
            if (matches || replaceable) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            raw.forEach((key, value) -> row.put(String.valueOf(key), value));
            obstructions.add(Map.copyOf(row));
            if (obstructions.size() >= 128) {
                break;
            }
        }
        return obstructions;
    }

    private int[] relativeReceipt(TreeDna dna, String receipt) {
        String[] split = receipt.split(":");
        if (split.length < 4) {
            return null;
        }
        try {
            int offset = split.length - 3;
            return new int[]{
                    Integer.parseInt(split[offset]) - dna.baseX(),
                    Integer.parseInt(split[offset + 1]) - dna.baseY(),
                    Integer.parseInt(split[offset + 2]) - dna.baseZ()
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private TreeBlockRole parseRole(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return TreeBlockRole.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private int integer(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private TreeBlockRole liveRole(
            Material material, PlannedTreeBlock planned) {
        if (isLeaf(material)) {
            return TreeBlockRole.CANOPY;
        }
        if (!isLog(material)) {
            return null;
        }
        if (planned != null
                && (planned.role() == TreeBlockRole.TRUNK
                        || planned.role() == TreeBlockRole.BRANCH
                        || planned.role() == TreeBlockRole.ROOT)) {
            return planned.role();
        }
        // ## Material is the live truth. A log occupying a planned canopy
        // coordinate remains wood in the capture so TREE_59 sees the same
        // conflict as production instead of an impossible LEAVES/BRANCH cell.
        return TreeBlockRole.TRUNK;
    }

    private boolean isLikelyForeign(Material material) {
        if (material.isAir() || isLog(material) || isLeaf(material)
                || material == Material.WATER
                || material == Material.LAVA) {
            return false;
        }
        String name = material.name();
        return name.contains("CHEST") || name.contains("SHULKER")
                || name.contains("FURNACE") || name.contains("DOOR")
                || name.contains("BED") || name.contains("GLASS")
                || name.contains("CONCRETE") || name.contains("WOOL")
                || name.contains("REDSTONE")
                || name.endsWith("_PLANKS")
                || name.endsWith("_SLAB")
                || name.endsWith("_STAIRS");
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

    void recordTargetOwnershipAnalysis(
            TreeEvolutionConfig config,
            TreeDna dna,
            TreeTargetOwnershipRepairPolicy.Analysis analysis
    ) {
        event(config,
                "[TRACE][tree-evolution][TREE_11] ownership-path "
                        + "tree=" + dna.key() + " "
                        + analysis.marker()
                        + " ## marker uses the same root graph and bridge choice as live construction and smoke replay");
    }

    boolean recordPlaced(
            EvolutionPlugin plugin,
            TreeEvolutionConfig config,
            TreeDna dna,
            Block block,
            Material previousMaterial,
            PlannedTreeBlock plannedBlock,
            TreeConstructionDecision decision
    ) {
        return recordPlaced(plugin, config, dna, block, previousMaterial,
                plannedBlock,
                decision.marker());
    }

    boolean recordPlaced(
            EvolutionPlugin plugin,
            TreeEvolutionConfig config,
            TreeDna dna,
            Block block,
            Material previousMaterial,
            PlannedTreeBlock plannedBlock,
            TreeConstructionSubrule subrule
    ) {
        return recordPlaced(plugin, config, dna, block, previousMaterial,
                plannedBlock,
                "[CONSTRUCTOR][SUBRULE=" + subrule + "]"
                        + subrule.smokeTag().marker());
    }

    private boolean recordPlaced(
            EvolutionPlugin plugin,
            TreeEvolutionConfig config,
            TreeDna dna,
            Block block,
            Material previousMaterial,
            PlannedTreeBlock plannedBlock,
            String constructorMarker
    ) {
        placed.incrementAndGet();
        event(config, "[ACTION][tree-evolution] place role=" + plannedBlock.role()
                + " material=" + plannedBlock.material()
                + " at=" + format(block)
                + " " + plannedBlock.augment().marker()
                + " " + constructorMarker
                + (plannedBlock.hasBranchPath()
                        ? " branch=" + plannedBlock.branchId() + ":"
                                + plannedBlock.branchStep()
                                + " parent=" + plannedBlock.parentX() + ","
                                + plannedBlock.parentY() + ","
                                + plannedBlock.parentZ()
                        : ""));
        if (!config.debugEnabled()) {
            return false;
        }
        buildMapIfDue(block, config.debugMapRadius());
        saveSoon(plugin, config);
        return deformationHistory.recordMutation(
                dna, block, previousMaterial, plannedBlock.material(),
                plannedBlock.role(), plannedBlock.augment(),
                constructorMarker, "PLACE", "planned-construction");
    }

    boolean recordRemoved(
            EvolutionPlugin plugin,
            TreeEvolutionConfig config,
            TreeDna dna,
            Block block,
            Material previousMaterial,
            TreeBlockRole role,
            TreePlacementAugment augment,
            TreeConstructionSubrule subrule,
            String reason
    ) {
        pruned.incrementAndGet();
        String marker = "[CONSTRUCTOR][SUBRULE=" + subrule + "]"
                + subrule.smokeTag().marker();
        event(config, "[ACTION][tree-evolution] remove role=" + role
                + " material=" + previousMaterial
                + " at=" + format(block)
                + " " + (augment == null
                        ? TreePlacementAugment.UNCLASSIFIED.marker()
                        : augment.marker())
                + " " + marker + " reason=" + reason);
        if (!config.debugEnabled()) {
            return false;
        }
        buildMapIfDue(block, config.debugMapRadius());
        saveSoon(plugin, config);
        return deformationHistory.recordMutation(
                dna, block, previousMaterial, Material.AIR, role,
                augment, marker, "REMOVE", reason);
    }

    boolean recordCanopyRepairMutation(
            EvolutionPlugin plugin,
            TreeEvolutionConfig config,
            TreeDna dna,
            Block block,
            Material previousMaterial,
            PlannedTreeBlock plannedBlock,
            TreeConstructionSubrule subrule,
            String reason
    ) {
        String marker = "[CONSTRUCTOR][SUBRULE=" + subrule + "]"
                + subrule.smokeTag().marker();
        event(config, "[ACTION][tree-evolution] canopy-repair"
                + " role=" + plannedBlock.role()
                + " material=" + plannedBlock.material()
                + " at=" + format(block)
                + " " + plannedBlock.augment().marker()
                + " " + marker + " reason=" + reason);
        if (!config.debugEnabled()) {
            return false;
        }
        saveSoon(plugin, config);
        return deformationHistory.recordMutation(
                dna, block, previousMaterial, plannedBlock.material(),
                plannedBlock.role(), plannedBlock.augment(), marker,
                "PLACE", reason);
    }

    void recordPrunedBatch(EvolutionPlugin plugin, TreeEvolutionConfig config,
            List<Block> blocks, TreeDna dna) {
        if (blocks.isEmpty()) {
            return;
        }
        pruned.addAndGet(blocks.size());
        Block first = blocks.get(0);
        Block last = blocks.get(blocks.size() - 1);
        event(config, "[ACTION][tree-evolution] prune role=STALE_CANOPY"
                + " material=" + dna.species().leafMaterial()
                + " count=" + blocks.size()
                + " first=" + format(first)
                + " last=" + format(last)
                + " stage=" + dna.maturityStage()
                + " sample=" + dna.profileSampleId()
                + " ## target-aware batch cleared old crown leaves without counting them as placements");
        buildMapIfDue(first, config.debugMapRadius());
        saveSoon(plugin, config);
    }

    void recordCanopyLift(EvolutionPlugin plugin, TreeEvolutionConfig config, Block trunk, TreeDna dna, int leavesPlaced) {
        placed.addAndGet(leavesPlaced);
        event(config, "[ACTION][tree-evolution] canopy-lift"
                + " trunk=" + format(trunk)
                + " leaf=" + dna.species().leafMaterial()
                + " leaves-placed=" + leavesPlaced
                + " target-height=" + dna.targetHeight()
                + " stage=" + dna.maturityStage());
        buildMapIfDue(trunk, config.debugMapRadius());
        saveSoon(plugin, config);
    }

    void recordSeedling(EvolutionPlugin plugin, TreeEvolutionConfig config, Block block, TreeDna parent) {
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
        buildMapIfDue(block, config.debugMapRadius());
        saveSoon(plugin, config);
    }

    void recordSeedlingGerminated(
            EvolutionPlugin plugin,
            TreeEvolutionConfig config,
            Block block,
            TreeDna child,
            String source
    ) {
        placed.incrementAndGet();
        lastLineageSummary = "tree=" + child.key()
                + ", parent=" + child.parentKey()
                + ", generation=" + child.generation()
                + ", species=" + child.species().id()
                + ", germinated-at=" + format(block);
        event(config, "[ACTION][tree-evolution] seedling.germinate"
                + " material=" + child.species().logMaterial()
                + " at=" + format(block)
                + " parent=" + child.parentKey()
                + " generation=" + child.generation()
                + " source=" + source
                + " ## one trunk block entered the gradual constructor; "
                + "no vanilla whole-tree structure was applied");
        buildMapIfDue(block, config.debugMapRadius());
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

    void recordCleanTreeObservation(
            TreeEvolutionConfig config,
            TreeDna dna,
            TreeWorkStatus status,
            boolean promoted
    ) {
        cleanTreeObservations.incrementAndGet();
        if (promoted) {
            observationPromotions.incrementAndGet();
        }
        event(config, "[AUDIT][tree-evolution] clean-tree-observation"
                + " tree=" + dna.key()
                + " result=" + (promoted ? "PROMOTED_DIRTY" : "CLEAN")
                + " stage=" + dna.maturityStage()
                + " " + status.summary()
                + " ## clean is a scheduling hint, not permanent trust;"
                + " every nearby tree returns to this bounded observation lane");
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

    void saveSoon(EvolutionPlugin plugin, TreeEvolutionConfig config) {
        if (!config.debugEnabled() || !plugin.isEnabled()) {
            return;
        }
        saveDirty.set(true);
        long now = System.currentTimeMillis();
        long next = nextSaveMillis.get();
        if (now < next
                || !nextSaveMillis.compareAndSet(next, now + 30_000L)) {
            return;
        }
        saveAsync(plugin, config);
    }

    void saveAsync(EvolutionPlugin plugin, TreeEvolutionConfig config) {
        saveDirty.set(true);
        if (!config.debugEnabled() || !saveRunning.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                if (saveDirty.getAndSet(false)) {
                    save(plugin, config);
                }
            } finally {
                saveRunning.set(false);
            }
        });
    }

    void saveNow(EvolutionPlugin plugin, TreeEvolutionConfig config) {
        if (config.debugEnabled()) {
            saveDirty.set(false);
            save(plugin, config);
        }
    }

    private void buildMapIfDue(Block center, int radius) {
        String changedColumn = surfaceColumnKey(
                center.getWorld(), center.getX(), center.getZ());
        surfaceTokenCache.remove(changedColumn);
        long now = System.currentTimeMillis();
        long next = nextSurfaceMapBuildMillis.get();
        if (now < next || !nextSurfaceMapBuildMillis.compareAndSet(
                next, now + 10_000L)) {
            return;
        }
        buildMap(center, radius);
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
        String cacheKey = surfaceColumnKey(world, x, z);
        long now = System.currentTimeMillis();
        CachedSurfaceToken cached = surfaceTokenCache.get(cacheKey);
        if (cached != null && now < cached.expiresMillis()) {
            return cached.token();
        }
        Block surface = world.getHighestBlockAt(x, z);
        Material type = surface.getType();
        Material below = surface.getRelative(0, -1, 0).getType();
        String token;
        if (isLog(type) || isLog(below)) {
            token = "T";
        } else if (isLeaf(type) || isLeaf(below)) {
            token = "L";
        } else if (type == Material.VINE || below == Material.VINE) {
            token = "V";
        } else if (type == Material.LEAF_LITTER || type == Material.SHORT_GRASS || type == Material.FERN || type == Material.PINK_PETALS
                || type.name().endsWith("_SAPLING") || type == Material.MANGROVE_PROPAGULE || type == Material.BROWN_MUSHROOM || type == Material.RED_MUSHROOM) {
            token = "U";
        } else if (below == Material.GRASS_BLOCK || below == Material.DIRT || below == Material.PODZOL || below == Material.MOSS_BLOCK) {
            token = "G";
        } else {
            token = ".";
        }
        if (surfaceTokenCache.size() > 8192) {
            surfaceTokenCache.clear();
        }
        surfaceTokenCache.put(cacheKey,
                new CachedSurfaceToken(token, now + 30_000L));
        return token;
    }

    private String surfaceColumnKey(World world, int x, int z) {
        return world.getUID() + ":" + x + ":" + z;
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
        Map<TreePlacementAugment, Integer> augmentCounts =
                new EnumMap<>(TreePlacementAugment.class);
        Map<String, List<String>> augmentCoordinates =
                new LinkedHashMap<>();
        for (PlannedTreeBlock block : orderedBlocks) {
            fullMinX = Math.min(fullMinX, block.x());
            fullMaxX = Math.max(fullMaxX, block.x());
            minY = Math.min(minY, block.y());
            maxY = Math.max(maxY, block.y());
            fullMinZ = Math.min(fullMinZ, block.z());
            fullMaxZ = Math.max(fullMaxZ, block.z());
            roleCounts.merge(block.role(), 1, Integer::sum);
            augmentCounts.merge(block.augment(), 1, Integer::sum);
            List<String> coordinates = augmentCoordinates.computeIfAbsent(
                    block.augment().name(), ignored -> new ArrayList<>());
            if (coordinates.size() < 64) {
                coordinates.add(block.x() + "," + block.y() + ","
                        + block.z() + " role=" + block.role()
                        + " material=" + block.material()
                        + (block.hasBranchPath()
                                ? " branch=" + block.branchId() + ":"
                                        + block.branchStep()
                                        + " parent=" + block.parentX() + ","
                                        + block.parentY() + ","
                                        + block.parentZ()
                                : ""));
            }
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
        List<Map<String, Object>> voxelModelLayers = new ArrayList<>();
        List<Map<String, Object>> voxelLiveStatusLayers = new ArrayList<>();
        Map<String, Character> currentVoxelStatus = new HashMap<>();
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
            List<String> voxelModelRows = new ArrayList<>();
            List<String> voxelLiveStatusRows = new ArrayList<>();
            boolean hasContent = false;
            boolean liveHasContent = false;
            for (int z = minZ; z <= maxZ; z++) {
                StringBuilder row = new StringBuilder();
                StringBuilder liveRow = new StringBuilder();
                StringBuilder voxelModelRow = new StringBuilder();
                StringBuilder voxelLiveStatusRow = new StringBuilder();
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
                    voxelModelRow.append(voxelModelDigit(plannedBlock));
                    char voxelStatus = voxelLiveStatusDigit(
                            plannedBlock, liveToken);
                    voxelLiveStatusRow.append(voxelStatus);
                    if (plannedBlock != null) {
                        currentVoxelStatus.put(key, voxelStatus);
                    }
                }
                rows.add(row.toString());
                liveRows.add(liveRow.toString());
                voxelModelRows.add(voxelModelRow.toString());
                voxelLiveStatusRows.add(voxelLiveStatusRow.toString());
            }
            if (!hasContent) {
                continue;
            }
            Map<String, Object> layer = new LinkedHashMap<>();
            layer.put("y", y);
            layer.put("relative-y", y - dna.baseY());
            layer.put("rows", rows);
            layers.add(layer);
            Map<String, Object> voxelLayer = new LinkedHashMap<>();
            voxelLayer.put("y", y);
            voxelLayer.put("relative-y", y - dna.baseY());
            voxelLayer.put("rows", voxelModelRows);
            voxelModelLayers.add(voxelLayer);
            Map<String, Object> voxelLiveLayer = new LinkedHashMap<>();
            voxelLiveLayer.put("y", y);
            voxelLiveLayer.put("relative-y", y - dna.baseY());
            voxelLiveLayer.put("rows", voxelLiveStatusRows);
            voxelLiveStatusLayers.add(voxelLiveLayer);
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

        Map<String, Object> voxelAxes = new LinkedHashMap<>();
        voxelAxes.put("origin", "base/stump at world " + dna.baseX() + "," + dna.baseY() + "," + dna.baseZ());
        voxelAxes.put("x-axis", "columns left-to-right are world x " + minX + ".." + maxX + " / relative " + (minX - dna.baseX()) + ".." + (maxX - dna.baseX()));
        voxelAxes.put("z-axis", "rows top-to-bottom are world z " + minZ + ".." + maxZ + " / relative " + (minZ - dna.baseZ()) + ".." + (maxZ - dna.baseZ()));
        voxelAxes.put("y-axis", "layers are bottom-to-top world y " + minY + ".." + maxY + " / relative " + (minY - dna.baseY()) + ".." + (maxY - dna.baseY()));
        voxelAxes.put("center-column-index", dna.baseX() - minX);
        voxelAxes.put("center-row-index", dna.baseZ() - minZ);

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (TreeBlockRole role : TreeBlockRole.values()) {
            counts.put(role.name(), roleCounts.getOrDefault(role, 0));
        }
        Map<String, Integer> labeledCounts = new LinkedHashMap<>();
        for (TreePlacementAugment augment
                : TreePlacementAugment.values()) {
            int count = augmentCounts.getOrDefault(augment, 0);
            if (count > 0) {
                labeledCounts.put(augment.name(), count);
            }
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
        lastPlan3dAugmentCounts = labeledCounts;
        lastPlan3dAugmentCoordinateIndex = augmentCoordinates;
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
        lastVoxelGridSummary = "voxel-grid species=" + dna.species().id()
                + " stage=" + dna.maturityStage()
                + " model-layers=" + voxelModelLayers.size()
                + " radius=" + radius
                + " ## numeric 4D-style grid: x/z/y model slices at the current growth stage";
        lastVoxelGridAxes = voxelAxes;
        lastVoxelModelLayers = voxelModelLayers;
        lastVoxelLiveStatusLayers = voxelLiveStatusLayers;
        lastVoxelStatusDelta = voxelStatusDelta(
                dna.key(), currentVoxelStatus);
    }

    private List<String> voxelStatusDelta(
            String treeKey, Map<String, Character> current) {
        Map<String, Character> immutableCurrent = Map.copyOf(current);
        Map<String, Character> previous = previousVoxelStatusByTree.put(
                treeKey, immutableCurrent);
        if (previousVoxelStatusByTree.size() > 64) {
            previousVoxelStatusByTree.keySet().stream()
                    .filter(key -> !key.equals(treeKey))
                    .findFirst()
                    .ifPresent(previousVoxelStatusByTree::remove);
        }
        if (previous == null) {
            return List.of("initial-snapshot planned-voxels="
                    + immutableCurrent.size());
        }
        java.util.TreeSet<String> coordinates = new java.util.TreeSet<>();
        coordinates.addAll(previous.keySet());
        coordinates.addAll(immutableCurrent.keySet());
        List<String> delta = new ArrayList<>();
        for (String coordinate : coordinates) {
            char before = previous.getOrDefault(coordinate, '0');
            char after = immutableCurrent.getOrDefault(coordinate, '0');
            if (before == after) {
                continue;
            }
            delta.add(coordinate + " " + before + "->" + after);
            if (delta.size() >= 512) {
                delta.add("truncated-after=512");
                break;
            }
        }
        return List.copyOf(delta);
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

    private char voxelModelDigit(PlannedTreeBlock block) {
        if (block == null) {
            return '0';
        }
        return switch (block.role()) {
            case TRUNK -> '1';
            case CANOPY -> '2';
            case BRANCH -> '3';
            case ROOT -> '4';
            case VINE -> '5';
            case GROUND_DETAIL -> '6';
            case FALLEN_LOG -> '7';
            case SAPLING -> '8';
        };
    }

    private char voxelLiveStatusDigit(PlannedTreeBlock plannedBlock, char liveToken) {
        if (plannedBlock == null) {
            return '0';
        }
        if (liveToken == '?') {
            return '4';
        }
        if (liveToken == 'X') {
            return '3';
        }
        if (Character.isUpperCase(liveToken)) {
            return '1';
        }
        if (Character.isLowerCase(liveToken)) {
            return '2';
        }
        return '0';
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

    private void save(EvolutionPlugin plugin, TreeEvolutionConfig config) {
        saveTrace(plugin, config);
        saveMap(plugin);
        save3dDebug(plugin, config);
        saveReplayDebug(plugin, config);
        deformationHistory.save(plugin);
    }

    private void saveTrace(EvolutionPlugin plugin, TreeEvolutionConfig config) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("session-started-at", sessionStartedAt);
        yaml.set("enabled", config.enabled());
        yaml.set("discovery-step-ticks", config.stepTicks());
        yaml.set("construction-step-ticks", config.constructionStepTicks());
        yaml.set("testing-enabled", config.testingEnabled());
        yaml.set("counters.searches", searches.get());
        yaml.set("counters.candidates", candidates.get());
        yaml.set("counters.dna-created", dnaCreated.get());
        yaml.set("counters.dna-loaded", dnaLoaded.get());
        yaml.set("counters.planned", planned.get());
        yaml.set("counters.placed", placed.get());
        yaml.set("counters.pruned", pruned.get());
        yaml.set("counters.rejected", rejected.get());
        yaml.set("counters.stalled", stalled.get());
        yaml.set("counters.forced-steps", forcedSteps.get());
        yaml.set("counters.intent-updates", intentUpdates.get());
        yaml.set("counters.stage-transitions", stageTransitions.get());
        yaml.set("counters.dna-normalized", dnaNormalized.get());
        yaml.set("counters.constructor-decisions", constructorDecisions.get());
        yaml.set("counters.clean-tree-observations",
                cleanTreeObservations.get());
        yaml.set("counters.observation-promotions",
                observationPromotions.get());
        yaml.set("last-plan-summary", lastPlanSummary);
        yaml.set("last-plan-next-10", lastPlanPreview);
        yaml.set("last-stage-snapshot", lastStageSnapshot);
        yaml.set("last-lineage-summary", lastLineageSummary);
        yaml.set("last-constructor-summary", lastConstructorSummary);
        yaml.set("recent-events", snapshot());
        yaml.set("notes", "## Tree evolution trace. Includes sample inspiration, personality, rarity, shape revision, stage cleanup, dedicated placed/pruned counters, target-aware prune batches, lineage, map state, and next planned blocks without extra commands.");
        saveYaml(plugin, yaml, "tree-evolution-trace.debug.yml");
    }

    private void saveMap(EvolutionPlugin plugin) {
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

    private void save3dDebug(EvolutionPlugin plugin, TreeEvolutionConfig config) {
        if (!config.debug3dEnabled()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        synchronized (threeDimensionalSnapshotLock) {
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
            yaml.set("snapshot-tree-key", last3dTreeKey);
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
        yaml.set("plan-3d.planner-augment-counts",
                lastPlan3dAugmentCounts);
        yaml.set("plan-3d.planner-augment-coordinate-index",
                lastPlan3dAugmentCoordinateIndex);
        yaml.set("plan-3d.planner-augment-notes",
                "## Each coordinate names the exact target-shape augment. Pair it with the constructor smoke tag in action/replay logs to distinguish recipe errors from placement-order errors. Coordinate samples are capped at 64 per augment.");
        yaml.set("plan-3d.layers", lastPlan3dLayers);
        yaml.set("live-3d.legend.uppercase", "planned block is already present in the live world");
        yaml.set("live-3d.legend.lowercase", "planned block is missing but the live block is placeable");
        yaml.set("live-3d.legend.X", "planned block is blocked by a different non-replaceable live block");
        yaml.set("live-3d.legend.?", "live block was not checked because chunk is unloaded or not owned by current Folia region");
        yaml.set("live-3d.legend.dot", "not part of the shown target plan");
        yaml.set("live-3d.summary", lastLive3dSummary);
        yaml.set("live-3d.status-counts", lastLive3dStatusCounts);
        yaml.set("live-3d.layers", lastLive3dLayers);
        yaml.set("voxel-grid.summary", lastVoxelGridSummary);
        yaml.set("voxel-grid.axes", lastVoxelGridAxes);
        yaml.set("voxel-grid.model.legend.0", "empty space in the shown cube");
        yaml.set("voxel-grid.model.legend.1", "planned trunk / main support wood");
        yaml.set("voxel-grid.model.legend.2", "planned leaves / canopy cloud");
        yaml.set("voxel-grid.model.legend.3", "planned branch / limb wood");
        yaml.set("voxel-grid.model.legend.4", "planned root");
        yaml.set("voxel-grid.model.legend.5", "planned vine");
        yaml.set("voxel-grid.model.legend.6", "planned understory / ground detail");
        yaml.set("voxel-grid.model.legend.7", "planned fallen log");
        yaml.set("voxel-grid.model.legend.8", "planned sapling / offspring");
        yaml.set("voxel-grid.model.layers", lastVoxelModelLayers);
        yaml.set("voxel-grid.live-status.legend.0", "no planned block at this coordinate");
        yaml.set("voxel-grid.live-status.legend.1", "live world matches the model at this coordinate");
        yaml.set("voxel-grid.live-status.legend.2", "model expects a block here, but it is still missing/placeable");
        yaml.set("voxel-grid.live-status.legend.3", "model expects a block here, but a different non-replaceable block is blocking it");
        yaml.set("voxel-grid.live-status.legend.4", "not checked because the chunk is unloaded or owned by another Folia region");
        yaml.set("voxel-grid.live-status.layers", lastVoxelLiveStatusLayers);
        yaml.set("voxel-grid.live-status.delta-from-previous-deep-frame",
                lastVoxelStatusDelta);
        yaml.set("voxel-grid.notes", "## Battleship-style numeric cube. Read each Y layer as a z/x grid. The model grid is the planned tree; live-status shows whether that exact coordinate has caught up.");
        yaml.set("constructor-stage-frames",
                new ArrayList<>(recentConstructorStageFrames));
        yaml.set("constructor-stage-frames-notes",
                "## Continuous frames keep hierarchy contracts. Deep frames are sampled, completed, or anomalous and store voxel deltas instead of duplicate full cubes.");
        yaml.set("live-voxel-captures",
                new ArrayList<>(liveVoxelEnvironmentCaptures.values()));
        yaml.set("live-voxel-captures-notes",
                "## Up to sixteen sparse local neighborhoods with atomically paired DNA snapshots; blocked constructors refresh after a bounded cooldown.");
        yaml.set("deformation-history-file",
                TreeDeformationHistoryStore.FILE_NAME);
        yaml.set("deformation-history-notes",
                "## Persistent per-coordinate timelines and anomaly bundles live in the separate bounded history file.");
        yaml.set("recent-stage-events", stageSnapshot());
        yaml.set("notes", "## 3dDebug stage/shape trace. plan-3d is the target. live-3d overlays actual world progress: uppercase is placed, lowercase is still missing/placeable, X is blocked, ? is unloaded or another Folia region.");
        }
        // ## YAML serialization and disk I/O must never retain the live voxel
        // lock. Region threads only pause long enough to copy immutable values.
        saveYaml(plugin, yaml, "tree-evolution-3dDebug.yml");
    }

    private void saveReplayDebug(EvolutionPlugin plugin, TreeEvolutionConfig config) {
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
        yaml.set("planner-augment-counts", lastReplayAugmentCounts);
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
    private void saveYaml(EvolutionPlugin plugin, YamlConfiguration yaml, String name) {
        File file = new File(plugin.getDataFolder(), name);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for " + name + ".");
            return;
        }
        try {
            DebugFileRotator.rotateIfOversized(
                    plugin, file, 8L * 1024L * 1024L, 2);
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save Evolution " + name + ".", ex);
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
        snapshot.put("original-shape-blocks",
                dna.originalShapeBlockCount());
        snapshot.put("original-shape-logs",
                dna.originalShapeLogCount());
        snapshot.put("original-shape-leaves",
                dna.originalShapeLeafCount());
        snapshot.put("original-shape-unresolved-leaves",
                dna.unresolvedOriginalShapeLeafCount());
        snapshot.put("evolution-ownership-version",
                dna.evolutionOwnershipVersion());
        snapshot.put("evolved-shape-logs", dna.evolvedLogCount());
        snapshot.put("evolved-shape-leaves", dna.evolvedLeafCount());
        snapshot.put("branch-envelope-ownership-required",
                dna.requiresEvolvedLeafOwnership());
        snapshot.put("ownership-note",
                "## preexisting source leaves count only after this evolution explicitly reforms and records them");
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
        snapshot.put("variant", dna.variant().id());
        snapshot.put("source-pattern", sourcePattern(dna));
        snapshot.put("personality", dna.personality().name());
        snapshot.put("rarity", dna.rarity().name());
        snapshot.put("sample", dna.profileSampleId());
        snapshot.put("source", dna.profileSampleSource());
        snapshot.put("planned-blocks", plan.size());
        TreeBlueprintValidation blueprint = plan.blueprintValidation();
        Map<String, Object> blueprintSnapshot = new LinkedHashMap<>();
        blueprintSnapshot.put("approved", blueprint.approved());
        blueprintSnapshot.put("origin", blueprint.origin());
        blueprintSnapshot.put("coordinate-proposals",
                blueprint.coordinateProposals());
        blueprintSnapshot.put("coordinate-conflicts",
                blueprint.coordinateConflicts());
        blueprintSnapshot.put("rooted-wood",
                blueprint.rootedWood() + "/" + blueprint.plannedWood());
        blueprintSnapshot.put("covered-branch-tips",
                blueprint.coveredBranchTips() + "/"
                        + blueprint.branchTips());
        blueprintSnapshot.put("failures", blueprint.failures());
        blueprintSnapshot.put("conflict-samples",
                blueprint.conflictSamples().stream().limit(8).toList());
        blueprintSnapshot.put("notes",
                "## The centerpiece blueprint coordinator translated and approved every target XYZ before the runtime constructor received it.");
        snapshot.put("blueprint-coordinator", blueprintSnapshot);
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

    private String sourcePattern(TreeDna dna) {
        TreeSourcePattern source = dna.sourcePattern();
        return "measured=" + source.measured()
                + ",height=" + source.height()
                + ",footprint=" + source.trunkFootprint()
                + ",branch-spread=" + source.branchSpread()
                + ",canopy=" + source.canopyRadius()
                + "x" + source.canopyDepth()
                + ",tiers=" + source.crownTiers()
                + ",drift=" + source.trunkDrift();
    }

    private double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private record CachedSurfaceToken(String token, long expiresMillis) {
    }

    private record DeformationAnalysisJob(
            TreeEvolutionConfig config,
            String treeKey,
            TreeSpecies species,
            TreeVariant variant,
            TreeMaturityStage maturityStage,
            TreeGrowthIntent intent,
            long shapeRevision,
            String trigger,
            boolean finalState,
            boolean force,
            Map<String, Object> capture,
            List<TreeVoxelSnapshotRenderer.Voxel> original,
            List<TreeVoxelSnapshotRenderer.Voxel> target
    ) {
    }
}
