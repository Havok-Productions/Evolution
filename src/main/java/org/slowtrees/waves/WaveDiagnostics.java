package org.slowtrees.waves;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slowtrees.core.SlowTreesPlugin;

final class WaveDiagnostics {
    private final AtomicLong cycles = new AtomicLong();
    private final AtomicLong columnsScanned = new AtomicLong();
    private final AtomicLong crestsRendered = new AtomicLong();
    private final AtomicLong runupsRendered = new AtomicLong();
    private final AtomicLong particlesSpawned = new AtomicLong();
    private final AtomicLong boatsBobbed = new AtomicLong();
    private final AtomicLong restores = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong regionSkips = new AtomicLong();
    private final AtomicLong surfaceCacheHits = new AtomicLong();
    private final AtomicLong surfaceCacheMisses = new AtomicLong();
    private final AtomicLong visualMemoryHeld = new AtomicLong();
    private final AtomicLong uncertainSurfaceHeld = new AtomicLong();
    private final AtomicLong packetVisualsReasserted = new AtomicLong();
    private final AtomicLong easedTransitions = new AtomicLong();
    private final AtomicLong deferredUpperLayers = new AtomicLong();
    private final AtomicLong packedUpdateBatches = new AtomicLong();
    private final AtomicLong packedUpdateCells = new AtomicLong();
    private final AtomicLong phaseRuns = new AtomicLong();
    private final AtomicLong collectNanos = new AtomicLong();
    private final AtomicLong renderNanos = new AtomicLong();
    private final AtomicLong collectMaxNanos = new AtomicLong();
    private final AtomicLong renderMaxNanos = new AtomicLong();
    private final AtomicLong runupPropagations = new AtomicLong();
    private final AtomicLong boatEnvelopeSkips = new AtomicLong();
    private final AtomicLong travelingCrests = new AtomicLong();
    private final AtomicLong fizzlingCrests = new AtomicLong();
    private final AtomicLong ovalPulseContributors = new AtomicLong();
    private final AtomicLong ovalMergedColumns = new AtomicLong();
    private final AtomicLong shoreImpacts = new AtomicLong();
    private final AtomicLong shoreApproachingColumns = new AtomicLong();
    private final AtomicLong leewardColumns = new AtomicLong();
    private final AtomicLong fetchAttenuatedColumns = new AtomicLong();
    private final AtomicLong shoreHeightCaps = new AtomicLong();
    private final AtomicLong runupHeightStops = new AtomicLong();
    private final AtomicLong expandingColumns = new AtomicLong();
    private final AtomicLong closingColumns = new AtomicLong();
    private final AtomicLong lakeInboundColumns = new AtomicLong();
    private final AtomicLong latestLakeComponents = new AtomicLong();
    private final AtomicLong latestEnclosedLakeComponents = new AtomicLong();
    private final AtomicLong latestTopologyKnownCells = new AtomicLong();
    private final AtomicLong latestTopologyWaterCells = new AtomicLong();
    private final AtomicLong inheritedTopologyCells = new AtomicLong();
    private final AtomicLong frontsSpawned = new AtomicLong();
    private final AtomicLong frontsExpired = new AtomicLong();
    private final AtomicLong frontsDistanceCulled = new AtomicLong();
    private final AtomicLong shoreFizzlesStarted = new AtomicLong();
    private final AtomicLong distanceFizzlesStarted = new AtomicLong();
    private final AtomicLong frontStateMerges = new AtomicLong();
    private final AtomicLong steeringReverseCorrections = new AtomicLong();
    private final AtomicLong steeringOvershootPreventions = new AtomicLong();
    private final AtomicLong steeringTightWaterLocks = new AtomicLong();
    private final AtomicLong steeringChannelDirectionLocks = new AtomicLong();
    private final AtomicLong openWaterFanStarts = new AtomicLong();
    private final AtomicLong broadWaveStarts = new AtomicLong();
    private final AtomicLong broadWaveInsufficientTopology = new AtomicLong();
    private final AtomicLong broadWaveSingleDirection = new AtomicLong();
    private final AtomicLong visibilityEntered = new AtomicLong();
    private final AtomicLong visibilityRestored = new AtomicLong();
    private final AtomicLong visibilityChurnFrames = new AtomicLong();
    private final AtomicLong movingViewFrames = new AtomicLong();
    private final AtomicLong topologyAnchorMoves = new AtomicLong();
    private final AtomicLong cardinalHeadingSamples = new AtomicLong();
    private final AtomicLong diagonalHeadingSamples = new AtomicLong();
    private final AtomicLong staticSourceSamples = new AtomicLong();
    private final AtomicLong latestActiveSources = new AtomicLong();
    private final AtomicLong latestVisibleSourceFronts = new AtomicLong();
    private final AtomicLong edgeLayer = new AtomicLong();
    private final AtomicLong shoulderLayer = new AtomicLong();
    private final AtomicLong innerLayer = new AtomicLong();
    private final AtomicLong crestLayer = new AtomicLong();
    private final AtomicLong nextSaveMillis = new AtomicLong();
    private final AtomicBoolean saveRunning = new AtomicBoolean();
    private final Deque<String> recentEvents = new ArrayDeque<>();
    private final String sessionStartedAt = Instant.now().toString();
    private volatile String latestSourceSummary = "static-sources=0";

    void recordCycle() { cycles.incrementAndGet(); }
    void recordColumns(int amount) { columnsScanned.addAndGet(Math.max(0, amount)); }
    void recordCrests(int amount) { crestsRendered.addAndGet(Math.max(0, amount)); }
    void recordRunups(int amount) { runupsRendered.addAndGet(Math.max(0, amount)); }
    void recordParticles(int amount) { particlesSpawned.addAndGet(Math.max(0, amount)); }
    void recordBoatBob() { boatsBobbed.incrementAndGet(); }
    void recordRestores(int amount) { restores.addAndGet(Math.max(0, amount)); }
    void recordRejected() { rejected.incrementAndGet(); }
    void recordRegionSkip() { regionSkips.incrementAndGet(); }
    void recordSurfaceCacheHit() { surfaceCacheHits.incrementAndGet(); }
    void recordSurfaceCacheMiss() { surfaceCacheMisses.incrementAndGet(); }
    void recordVisualMemoryHeld(int amount) { visualMemoryHeld.addAndGet(Math.max(0, amount)); }
    void recordContinuity(int uncertainHeld, int reasserted) {
        uncertainSurfaceHeld.addAndGet(Math.max(0, uncertainHeld));
        packetVisualsReasserted.addAndGet(Math.max(0, reasserted));
    }
    void recordTemporalSmoothing(int eased, int deferred) {
        easedTransitions.addAndGet(Math.max(0, eased));
        deferredUpperLayers.addAndGet(Math.max(0, deferred));
    }
    void recordPackedUpdate(int cells) {
        packedUpdateBatches.incrementAndGet();
        packedUpdateCells.addAndGet(Math.max(0, cells));
    }
    void recordPhases(long collectElapsedNanos, long renderElapsedNanos) {
        long collect = Math.max(0L, collectElapsedNanos);
        long render = Math.max(0L, renderElapsedNanos);
        phaseRuns.incrementAndGet();
        collectNanos.addAndGet(collect);
        renderNanos.addAndGet(render);
        collectMaxNanos.accumulateAndGet(collect, Math::max);
        renderMaxNanos.accumulateAndGet(render, Math::max);
    }
    void recordRunupPropagation(int amount) { runupPropagations.addAndGet(Math.max(0, amount)); }
    void recordBoatEnvelopeSkip() { boatEnvelopeSkips.incrementAndGet(); }
    void recordLayer(int layer) {
        switch (layer) {
            case 1 -> edgeLayer.incrementAndGet();
            case 2 -> shoulderLayer.incrementAndGet();
            case 3 -> innerLayer.incrementAndGet();
            case 4 -> crestLayer.incrementAndGet();
            default -> { }
        }
    }
    void recordCrestLifecycle(int traveling, int fizzling) {
        travelingCrests.addAndGet(Math.max(0, traveling));
        fizzlingCrests.addAndGet(Math.max(0, fizzling));
    }
    void recordShoreResponse(int approaching, int leeward, int fetchAttenuated, int heightCaps) {
        shoreApproachingColumns.addAndGet(Math.max(0, approaching));
        leewardColumns.addAndGet(Math.max(0, leeward));
        fetchAttenuatedColumns.addAndGet(Math.max(0, fetchAttenuated));
        shoreHeightCaps.addAndGet(Math.max(0, heightCaps));
    }
    void recordLakeFlow(int components, int enclosedComponents, int inbound,
            int knownCells, int waterCells, int inheritedCells) {
        latestLakeComponents.set(Math.max(0, components));
        latestEnclosedLakeComponents.set(Math.max(0, enclosedComponents));
        lakeInboundColumns.addAndGet(Math.max(0, inbound));
        latestTopologyKnownCells.set(Math.max(0, knownCells));
        latestTopologyWaterCells.set(Math.max(0, waterCells));
        inheritedTopologyCells.addAndGet(Math.max(0, inheritedCells));
    }
    void recordFrontLifecycle(TravelingWaveRegistry.Lifecycle lifecycle) {
        frontsSpawned.addAndGet(lifecycle.spawnedIds().size());
        frontsExpired.addAndGet(lifecycle.expiredIds().size());
        frontsDistanceCulled.addAndGet(lifecycle.distanceCulledIds().size());
        shoreFizzlesStarted.addAndGet(lifecycle.shoreFizzleIds().size());
        distanceFizzlesStarted.addAndGet(lifecycle.distanceFizzleIds().size());
        frontStateMerges.addAndGet(lifecycle.mergeTransitions().size());
        for (String transition : lifecycle.steeringTransitions()) {
            if (transition.startsWith("[STEER][REVERSE]")) {
                steeringReverseCorrections.incrementAndGet();
            } else if (transition.startsWith("[STEER][OVERSHOOT-PREVENTED]")) {
                steeringOvershootPreventions.incrementAndGet();
            } else if (transition.startsWith("[STEER][TIGHT-WATER]")) {
                steeringTightWaterLocks.incrementAndGet();
            } else if (transition.startsWith("[STEER][CHANNEL-LOCK]")) {
                steeringChannelDirectionLocks.incrementAndGet();
            } else if (transition.startsWith("[SOURCE][OPEN-WATER-FAN]")) {
                openWaterFanStarts.incrementAndGet();
            } else if (transition.startsWith("[SOURCE][BROAD-WAVE]")) {
                broadWaveStarts.incrementAndGet();
                if (transition.contains("reason=insufficient-topology")) {
                    broadWaveInsufficientTopology.incrementAndGet();
                } else if (transition.contains("reason=coherent-single-direction")) {
                    broadWaveSingleDirection.incrementAndGet();
                }
            }
        }
    }

    void recordViewport(WaveRenderer.RenderResult render,
            double playerMovement, double anchorMovement, boolean churn) {
        visibilityEntered.addAndGet(Math.max(0, render.entered()));
        visibilityRestored.addAndGet(Math.max(0, render.restored()));
        movingViewFrames.addAndGet(playerMovement > 0.01D ? 1L : 0L);
        topologyAnchorMoves.addAndGet(anchorMovement > 0.01D ? 1L : 0L);
        visibilityChurnFrames.addAndGet(churn ? 1L : 0L);
    }

    void recordDirections(TravelingWaveRegistry.DirectionSummary directions) {
        cardinalHeadingSamples.addAndGet(directions.cardinal());
        diagonalHeadingSamples.addAndGet(directions.diagonal());
    }

    void recordSources(TravelingWaveRegistry.SourceSummary sources) {
        staticSourceSamples.addAndGet(Math.max(0, sources.activeSources()));
        latestActiveSources.set(Math.max(0, sources.activeSources()));
        latestVisibleSourceFronts.set(Math.max(0, sources.visibleFronts()));
        latestSourceSummary = sources.summary();
    }

    void recordRunupHeightStop() { runupHeightStops.incrementAndGet(); }
    void recordOvalFrame(int pulseContributors, int mergedColumns, int impacts, int expanding, int closing) {
        ovalPulseContributors.addAndGet(Math.max(0, pulseContributors));
        ovalMergedColumns.addAndGet(Math.max(0, mergedColumns));
        shoreImpacts.addAndGet(Math.max(0, impacts));
        expandingColumns.addAndGet(Math.max(0, expanding));
        closingColumns.addAndGet(Math.max(0, closing));
    }
    long visualChanges() {
        return crestsRendered.get() + runupsRendered.get() + restores.get();
    }

    void recordEvent(WaveConfig config, String event) {
        int maxEvents = config.debugRecentEvents();
        if (maxEvents <= 0) {
            return;
        }
        synchronized (recentEvents) {
            recentEvents.addLast(Instant.now() + " " + event);
            while (recentEvents.size() > maxEvents) {
                recentEvents.removeFirst();
            }
        }
    }

    void saveSoon(SlowTreesPlugin plugin, WaveConfig config) {
        long now = System.currentTimeMillis();
        long next = nextSaveMillis.get();
        if (now < next || !nextSaveMillis.compareAndSet(next, now + 10000L)) {
            return;
        }
        plugin.pathDebug().trace(plugin, "waves", "persistence.save-debug.schedule", "waves.debug.yml");
        saveAsync(plugin, config);
    }

    void saveAsync(SlowTreesPlugin plugin, WaveConfig config) {
        if (!saveRunning.compareAndSet(false, true)) {
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

    void saveNow(SlowTreesPlugin plugin, WaveConfig config) {
        plugin.pathDebug().trace(plugin, "waves", "persistence.save-debug.now", "waves.debug.yml");
        save(plugin, config);
    }

    private void save(SlowTreesPlugin plugin, WaveConfig config) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("session-started-at", sessionStartedAt);
        yaml.set("enabled", config.enabled());
        yaml.set("update-interval-ticks", config.updateIntervalTicks());
        yaml.set("simulation-radius", config.simulationRadius());
        yaml.set("render-radius", config.renderRadius());
        yaml.set("shoreline-response.distance", config.shoreResponseDistance());
        yaml.set("shoreline-response.fetch-distance", config.fetchDistance());
        yaml.set("shoreline-response.minimum-facing", config.minimumShoreFacing());
        yaml.set("counters.cycles", cycles.get());
        yaml.set("counters.columns-scanned", columnsScanned.get());
        yaml.set("counters.crests-rendered", crestsRendered.get());
        yaml.set("counters.shore-runups-rendered", runupsRendered.get());
        yaml.set("counters.particles-spawned", particlesSpawned.get());
        yaml.set("counters.boats-bobbed", boatsBobbed.get());
        yaml.set("counters.restores", restores.get());
        yaml.set("counters.rejected", rejected.get());
        yaml.set("counters.region-skips", regionSkips.get());
        yaml.set("counters.surface-cache-hits", surfaceCacheHits.get());
        yaml.set("counters.surface-cache-misses", surfaceCacheMisses.get());
        yaml.set("counters.visual-memory-held", visualMemoryHeld.get());
        yaml.set("counters.continuity.unknown-columns-held", uncertainSurfaceHeld.get());
        yaml.set("counters.continuity.packet-visuals-reasserted", packetVisualsReasserted.get());
        yaml.set("counters.temporal-smoothing.eased-transitions", easedTransitions.get());
        yaml.set("counters.temporal-smoothing.deferred-upper-layers", deferredUpperLayers.get());
        yaml.set("counters.packet-batching.batches", packedUpdateBatches.get());
        yaml.set("counters.packet-batching.cells", packedUpdateCells.get());
        long measuredPhases = phaseRuns.get();
        yaml.set("phase-timing.runs", measuredPhases);
        yaml.set("phase-timing.collect-field-average-ms", millisAverage(collectNanos.get(), measuredPhases));
        yaml.set("phase-timing.collect-field-max-ms", millis(collectMaxNanos.get()));
        yaml.set("phase-timing.render-packets-average-ms", millisAverage(renderNanos.get(), measuredPhases));
        yaml.set("phase-timing.render-packets-max-ms", millis(renderMaxNanos.get()));
        yaml.set("counters.runup-propagations", runupPropagations.get());
        yaml.set("counters.boat-envelope-skips", boatEnvelopeSkips.get());
        yaml.set("counters.traveling-crests", travelingCrests.get());
        yaml.set("counters.fizzling-crests", fizzlingCrests.get());
        yaml.set("counters.traveling-fronts.active-samples", ovalPulseContributors.get());
        yaml.set("counters.traveling-fronts.merged-columns", ovalMergedColumns.get());
        yaml.set("counters.traveling-fronts.shore-impacts", shoreImpacts.get());
        yaml.set("counters.shore-response.windward-inbound-columns", shoreApproachingColumns.get());
        yaml.set("counters.shore-response.side-leeward-inbound-columns", leewardColumns.get());
        yaml.set("counters.shore-response.fetch-attenuated-open-water-columns", fetchAttenuatedColumns.get());
        yaml.set("counters.shore-response.height-capped-columns", shoreHeightCaps.get());
        yaml.set("counters.shore-response.runup-height-stops", runupHeightStops.get());
        yaml.set("counters.lake-flow.inbound-columns", lakeInboundColumns.get());
        yaml.set("counters.lake-flow.latest-shore-guided-components", latestLakeComponents.get());
        yaml.set("counters.lake-flow.latest-enclosed-components", latestEnclosedLakeComponents.get());
        yaml.set("counters.lake-flow.latest-known-topology-cells", latestTopologyKnownCells.get());
        yaml.set("counters.lake-flow.latest-water-topology-cells", latestTopologyWaterCells.get());
        yaml.set("counters.lake-flow.inherited-topology-cells", inheritedTopologyCells.get());
        yaml.set("counters.traveling-fronts.traveling-columns", expandingColumns.get());
        yaml.set("counters.traveling-fronts.fizzling-columns", closingColumns.get());
        yaml.set("counters.traveling-fronts.spawned", frontsSpawned.get());
        yaml.set("counters.traveling-fronts.expired", frontsExpired.get());
        yaml.set("counters.traveling-fronts.viewer-distance-hidden", frontsDistanceCulled.get());
        yaml.set("counters.traveling-fronts.shore-fizzles-started", shoreFizzlesStarted.get());
        yaml.set("counters.traveling-fronts.distance-fizzles-started", distanceFizzlesStarted.get());
        yaml.set("counters.traveling-fronts.state-merges", frontStateMerges.get());
        yaml.set("counters.viewport.entered-columns", visibilityEntered.get());
        yaml.set("counters.viewport.restored-columns", visibilityRestored.get());
        yaml.set("counters.viewport.churn-frames", visibilityChurnFrames.get());
        yaml.set("counters.viewport.moving-frames", movingViewFrames.get());
        yaml.set("counters.viewport.topology-anchor-moves", topologyAnchorMoves.get());
        yaml.set("counters.steering.cardinal-heading-samples", cardinalHeadingSamples.get());
        yaml.set("counters.steering.diagonal-heading-samples", diagonalHeadingSamples.get());
        yaml.set("counters.steering.reverse-corrections", steeringReverseCorrections.get());
        yaml.set("counters.steering.overshoot-preventions", steeringOvershootPreventions.get());
        yaml.set("counters.steering.tight-water-locks", steeringTightWaterLocks.get());
        yaml.set("counters.steering.channel-direction-locks", steeringChannelDirectionLocks.get());
        yaml.set("counters.static-sources.open-water-fan-starts", openWaterFanStarts.get());
        yaml.set("counters.static-sources.broad-wave-starts", broadWaveStarts.get());
        yaml.set("counters.static-sources.broad-wave-insufficient-topology",
                broadWaveInsufficientTopology.get());
        yaml.set("counters.static-sources.broad-wave-single-direction",
                broadWaveSingleDirection.get());
        yaml.set("counters.static-sources.active-source-samples", staticSourceSamples.get());
        yaml.set("counters.static-sources.latest-active-sources", latestActiveSources.get());
        yaml.set("counters.static-sources.latest-visible-fronts", latestVisibleSourceFronts.get());
        yaml.set("static-sources.latest-summary", latestSourceSummary);
        yaml.set("counters.layers.edge_0_2", edgeLayer.get());
        yaml.set("counters.layers.shoulder_0_5", shoulderLayer.get());
        yaml.set("counters.layers.inner_0_8", innerLayer.get());
        yaml.set("counters.layers.crest_1_8", crestLayer.get());
        yaml.set("recent-events", snapshot());
        yaml.set("notes", "## Waves are packet-only water illusions driven by persistent world-fixed water/shore sources; players only view shared nearby fronts. A static source resolves from a deterministic 192-block world lattice, locks its water origin, and never moves with a player. A front locks its first shoreline destination, and the buffered topology window keeps a world-fixed lattice and origin while the player moves inside it; walking changes the viewport rather than sliding or rotating the wave. Eligible original side-angle fronts whose live wave geometry overlaps consume both parent states and create one wider resultant front. A resultant front cannot merge again, merges have a two-second per-player cooldown, and missing fronts refill one at a time with a 20-tick fade-in; debug reports these controlled state transitions separately from visual column overlap. Four-tick frames ease packet-water height one level at a time. Standard fronts vary their offshore course, crossing fronts approach at opposing angles, and occasional giant fronts carry a second trailing shelf. Guided course bias is shore-safe, distance to the locked coast cannot increase, and a front fizzles instead of reversing after arrival. Opposing banks forty blocks apart or less define small enclosed water; fronts there begin from a short four-to-twelve-block shore approach instead of deep across the channel. Those fronts share one source-stable direction along the measured channel axis, ignoring conflicting nearby shore pointers until they fizzle. Large-water sources keep broad fronts by default; a bounded local probe only creates smaller angle-varied wavelets when reliable topology shows materially different coast directions. Unknown or single-direction water stays broad, and each split wavelet widens toward its planned size only as it actually travels. Tight water makes oversized fronts ease into compact locked footprints, and crowded fronts shrink substantially further so several waves can share a channel without bank-to-bank angle churn. Weighted eight-direction gradients allow genuine diagonal shoreline travel without joining water bodies across land corners. UNKNOWN Folia columns retain their previous visual state, partial topology scans inherit confirmed cells, and bounded packet reassertion repairs chunk resends without rebuilding the wave. Lifecycle, viewport churn, topology-anchor movement, continuity, and compass-heading counters trace disappearing or reappearing waves back to their actual cause. Connected-water topology crosses biome, plant, bubble-column, and waterlogged transitions while cached terrain and section-batched packets keep work Folia-local.");
        File file = new File(plugin.getDataFolder(), "waves.debug.yml");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for waves.debug.yml.");
            return;
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save Evolution waves.debug.yml.", ex);
        }
    }

    private double millisAverage(long nanos, long runs) {
        return runs <= 0L ? 0.0D : Math.round((nanos / 1_000_000.0D / runs) * 100.0D) / 100.0D;
    }

    private double millis(long nanos) {
        return Math.round((nanos / 1_000_000.0D) * 100.0D) / 100.0D;
    }

    private List<String> snapshot() {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents);
        }
    }
}
