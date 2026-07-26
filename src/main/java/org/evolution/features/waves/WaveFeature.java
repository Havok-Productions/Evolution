package org.evolution.features.waves;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import org.evolution.coreparts.PluginFeature;
import org.evolution.coreparts.ResourceReporter.ReportSample;
import org.evolution.coreparts.EvolutionPlugin;
import org.evolution.coreparts.hierarchy.FeatureActionHierarchy;
import org.evolution.coreparts.hierarchy.FeatureHierarchyTrace;
import org.evolution.features.waves.action.WaveActionPhase;
import org.evolution.features.waves.action.WaveActionSubrule;

public final class WaveFeature implements PluginFeature, Listener {
    private static final FeatureActionHierarchy<WaveActionPhase> ACTION_HIERARCHY =
            FeatureActionHierarchy.of("waves", WaveActionPhase.class);
    private static final int SHORE_STEERING_DISTANCE = 48;
    private final EvolutionPlugin plugin;
    private final WaveModel model = new WaveModel();
    private final WaveHeightStack heightStack = new WaveHeightStack();
    private final WaveEnvironmentModel environment = new WaveEnvironmentModel();
    private final ShoreRunupPolicy shoreRunupPolicy = new ShoreRunupPolicy();
    private final WaveDiagnostics diagnostics = new WaveDiagnostics();
    private final WaveSurfaceCache surfaceCache = new WaveSurfaceCache();
    private final WaveLakeFlowCache lakeFlowCache = new WaveLakeFlowCache();
    private final TravelingWaveRegistry travelingWaves = new TravelingWaveRegistry();
    private final Map<WaveRenderer.WaveKey, ShoreRunupState> shoreRunups = new ConcurrentHashMap<>();
    private final Map<UUID, ViewState> viewStates = new ConcurrentHashMap<>();
    private final WaveRenderer renderer;
    private volatile WaveConfig config;

    public WaveFeature(EvolutionPlugin plugin) {
        this.plugin = plugin;
        this.config = WaveConfig.load(plugin);
        this.renderer = new WaveRenderer(plugin, diagnostics);
        plugin.pathDebug().trace(plugin, "waves", "config.load", config.summary());
    }

    @Override
    public void onEnable() {
        diagnostics.saveNow(plugin, config);
        plugin.pathDebug().trace(plugin, "waves", "enable.schedule-online-players", "players=" + Bukkit.getOnlinePlayers().size());
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayerWaves(player, 20L);
        }
    }

    @Override
    public void onDisable() {
        renderer.clearAll(Bukkit.getOnlinePlayers(), true);
        lakeFlowCache.clear();
        travelingWaves.clear();
        viewStates.clear();
        diagnostics.saveNow(plugin, config);
    }

    @Override
    public void reload() {
        this.config = WaveConfig.load(plugin);
        surfaceCache.invalidate();
        lakeFlowCache.clear();
        travelingWaves.clear();
        viewStates.clear();
        shoreRunups.clear();
        plugin.pathDebug().trace(plugin, "waves", "config.reload", config.summary());
    }

    @Override
    public String status() {
        diagnostics.saveAsync(plugin, config);
        return "Waves are " + (config.enabled() ? "enabled" : "disabled")
                + ". Visual changes: " + diagnostics.visualChanges() + ".";
    }

    public boolean enabled() {
        return config.enabled();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        schedulePlayerWaves(event.getPlayer(), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        renderer.clear(event.getPlayer(), false);
        lakeFlowCache.invalidate(event.getPlayer().getUniqueId());
        travelingWaves.remove(event.getPlayer().getUniqueId());
        viewStates.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        renderer.clear(event.getPlayer(), false);
        lakeFlowCache.invalidate(event.getPlayer().getUniqueId());
        travelingWaves.remove(event.getPlayer().getUniqueId());
        viewStates.remove(event.getPlayer().getUniqueId());
        schedulePlayerWaves(event.getPlayer(), 20L);
    }

    private void schedulePlayerWaves(Player player, long delayTicks) {
        plugin.pathDebug().traceSampled(plugin, "waves", "scheduler.player-delay", "delay=" + Math.max(1L, delayTicks));
        player.getScheduler().runDelayed(plugin, task -> runNearPlayer(player), null, Math.max(1L, delayTicks));
    }

    private void runNearPlayer(Player player) {
        try (ReportSample sample = plugin.resourceReporter().begin("waves", "tick.run-near-player")) {
            traceHierarchy(WaveActionPhase.SAFETY_GATE, "run-near-player");
            WaveConfig currentConfig = config;
            if (!player.isOnline()) {
                sample.detail("offline-player");
                return;
            }
            if (!currentConfig.enabled()) {
                renderer.clear(player, true);
                schedulePlayerWaves(player, currentConfig.updateIntervalTicks());
                sample.detail("disabled");
                return;
            }

            World world = player.getWorld();
            if (world.getEnvironment() != World.Environment.NORMAL || !currentConfig.isWorldAllowed(world)) {
                renderer.clear(player, true);
                schedulePlayerWaves(player, currentConfig.updateIntervalTicks());
                sample.detail("environment-skip");
                return;
            }

            diagnostics.recordCycle();
            long tick = world.getGameTime();
            traceHierarchy(WaveActionPhase.UPDATE_ENVIRONMENT, "profile-and-wind");
            WaveProfile profile = currentConfig.profile(world);
            WaveWind wind = currentWind();
            Map<WaveRenderer.WaveKey, Integer> visuals = new HashMap<>();
            Set<Long> uncertainColumns = new HashSet<>();
            long collectStarted = System.nanoTime();
            traceHierarchy(WaveActionPhase.ADVANCE_FRONTS, "advance-traveling-fronts");
            traceHierarchy(WaveActionPhase.COLLECT_VISUALS, "collect-wave-visuals");
            ScanResult result = collectWaveVisuals(
                    player, profile, wind, tick, currentConfig, visuals, uncertainColumns);
            traceHierarchy(
                    WaveActionPhase.COLLECT_VISUALS,
                    WaveActionSubrule.PER_PLAYER_COAST_AREA_DISTRIBUTION,
                    "areas=" + result.sources().coastAreas()
                            + " limited=" + result.sources().limitedCoastAreas()
                            + " suppressed=" + result.sources().suppressedFronts()
                            + " cap=" + currentConfig.maximumIncomingFrontsPerCoastAreaPerPlayer());
            traceHierarchy(WaveActionPhase.MERGE_FRONTS, "merge-front-contributions");
            traceHierarchy(WaveActionPhase.SHORE_RUNUP, "shore-runup-state");
            trimExpiredRunups(tick, currentConfig);
            long renderStarted = System.nanoTime();
            traceHierarchy(WaveActionPhase.RENDER, "render-wave-frame");
            WaveRenderer.RenderResult renderResult = renderer.render(
                    player, visuals, uncertainColumns,
                    result.chunkBoundaryCrossed(), currentConfig, tick);
            int changed = renderResult.changed();
            diagnostics.recordPhases(renderStarted - collectStarted, System.nanoTime() - renderStarted);
            if (currentConfig.boatBobbingEnabled()) {
                traceHierarchy(WaveActionPhase.BOAT_RESPONSE, "boat-response");
                bobNearbyBoats(player, tick, currentConfig);
            }
            diagnostics.recordColumns(result.columnsScanned());
            diagnostics.recordRunups(result.runups());
            diagnostics.recordOvalFrame(result.activeFronts(), result.mergedColumns(), result.shoreImpacts(),
                    result.expandingColumns(), result.closingColumns());
            diagnostics.recordLakeFlow(result.lakeComponents(), result.enclosedLakeComponents(),
                    result.lakeInboundColumns(), result.topologyKnownCells(), result.topologyWaterCells(),
                    result.inheritedTopologyCells());
            diagnostics.recordFrontLifecycle(result.lifecycle());
            diagnostics.recordDirections(result.directions());
            diagnostics.recordSources(result.sources());
            boolean visibilityChurn = renderResult.entered() + renderResult.restored()
                    >= Math.max(400, renderResult.requested() / 3);
            diagnostics.recordViewport(renderResult, result.playerMovement(),
                    result.anchorMovement(), visibilityChurn);
            if (renderResult.uncertainHeld() > 0 || renderResult.reasserted() > 0
                    || result.inheritedTopologyCells() > 0) {
                String continuity = "[VIEW][CONTINUITY] unknown-surface=" + result.uncertainSurfaceColumns()
                        + " unknown-held=" + renderResult.uncertainHeld()
                        + " topology-inherited=" + result.inheritedTopologyCells()
                        + " packet-reasserted=" + renderResult.reasserted()
                        + " chunk-crossed=" + result.chunkBoundaryCrossed();
                plugin.pathDebug().traceSampled(plugin, "waves", "render.continuity", continuity);
                if (renderResult.uncertainHeld() >= Math.max(100, renderResult.requested() / 4)) {
                    diagnostics.recordEvent(currentConfig, continuity);
                }
            }
            if (result.lifecycle().hasEvents()) {
                String lifecycle = "[LIFECYCLE] " + result.lifecycle().summary();
                diagnostics.recordEvent(currentConfig, lifecycle);
                plugin.pathDebug().trace(plugin, "waves", "front.lifecycle-event", lifecycle);
            }
            if (visibilityChurn) {
                String churn = "[VIEW][CHURN] moved=" + rounded(result.playerMovement())
                        + " anchor-moved=" + rounded(result.anchorMovement())
                        + " entered=" + renderResult.entered()
                        + " restored=" + renderResult.restored()
                        + " held=" + renderResult.held()
                        + " unknown-held=" + renderResult.uncertainHeld()
                        + " reasserted=" + renderResult.reasserted()
                        + " active=" + renderResult.active()
                        + " requested=" + renderResult.requested();
                diagnostics.recordEvent(currentConfig, churn);
                plugin.pathDebug().trace(plugin, "waves", "render.visibility-churn", churn);
            }
            if (result.anchorMovement() > 0.01D) {
                String anchor = "[VIEW][ANCHOR] moved=" + rounded(result.anchorMovement())
                        + " player-step=" + rounded(result.playerMovement());
                diagnostics.recordEvent(currentConfig, anchor);
                plugin.pathDebug().trace(plugin, "waves", "topology.anchor-shift", anchor);
            }
            if (tick % 100L < currentConfig.updateIntervalTicks()) {
                diagnostics.recordEvent(currentConfig, "[FRONT] active=" + result.activeFronts() + " moved=" + (Math.round(result.frontMovementBlocks() * 100.0D) / 100.0D) + " shore-guided=" + result.shoreGuidedFronts()
                        + " [SOURCE] " + result.sources().summary()
                        + " [TRAVEL] columns=" + result.expandingColumns() + " [FIZZLE] columns=" + result.closingColumns()
                        + " [MERGE] fronts=" + result.stateMerges() + " columns=" + result.mergedColumns()
                        + " [COAST] columns=" + result.shoreApproachingColumns()

                        + " [STEER] radius=" + SHORE_STEERING_DISTANCE
                        + " guided-columns=" + result.lakeInboundColumns()
                        + " [LAKE] guided-components=" + result.lakeComponents()
                        + " enclosed=" + result.enclosedLakeComponents()
                        + " topology=" + result.topologyKnownCells() + "/" + result.topologyWaterCells()
                        + " inherited=" + result.inheritedTopologyCells()
                        + " unknown-surface=" + result.uncertainSurfaceColumns()
                        + " [CAP] columns=" + result.shoreHeightCaps() + " [SHORE] impacts=" + result.shoreImpacts()
                        + " [RUNUP] blocks=" + result.runups()
                        + " [VIEW] moved=" + rounded(result.playerMovement())
                        + " anchor-moved=" + rounded(result.anchorMovement())
                        + " entered=" + renderResult.entered() + " restored=" + renderResult.restored()
                        + " held=" + renderResult.held()
                        + " unknown-held=" + renderResult.uncertainHeld()
                        + " reasserted=" + renderResult.reasserted()
                        + " active=" + renderResult.active()
                        + " requested=" + renderResult.requested()
                        + " [LIFECYCLE] " + result.lifecycle().summary()
                        + " [DIRECTION] " + result.directions().summary());
            }
            diagnostics.saveSoon(plugin, currentConfig);
            sample.workUnits(result.columnsScanned()).changedUnits(changed).detail("visuals=" + visuals.size()
                    + " crests=" + result.crests() + " fronts=" + result.activeFronts()
                    + " static-sources=" + result.sources().activeSources() + " moved=" + (Math.round(result.frontMovementBlocks() * 100.0D) / 100.0D) + " state-merges=" + result.stateMerges() + " merged-columns=" + result.mergedColumns()

                    + " coast-columns=" + result.shoreApproachingColumns()
                    + " shore-guided-components=" + result.lakeComponents()
                    + " enclosed-lake-components=" + result.enclosedLakeComponents()
                    + " guided-columns=" + result.lakeInboundColumns()
                    + " height-caps=" + result.shoreHeightCaps()
                    + " shore-impacts=" + result.shoreImpacts() + " runups=" + result.runups() + " particles=" + result.particles());
            plugin.pathDebug().traceSampled(plugin, "waves", "render.tick", "columns=" + result.columnsScanned()
                    + " visuals=" + visuals.size() + " changed=" + changed + " wind=" + wind.summary()
                    + " profile=" + profile.summary());
            plugin.pathDebug().traceSampled(plugin, "waves", "render.visibility",
                    "[VIEW] moved=" + rounded(result.playerMovement())
                            + " anchor-moved=" + rounded(result.anchorMovement())
                            + " entered=" + renderResult.entered()
                            + " restored=" + renderResult.restored()
                            + " held=" + renderResult.held()
                            + " unknown-held=" + renderResult.uncertainHeld()
                            + " reasserted=" + renderResult.reasserted()
                            + " active=" + renderResult.active()
                            + " requested=" + renderResult.requested());
            plugin.pathDebug().traceSampled(plugin, "waves", "front.lifecycle",
                    "[LIFECYCLE] " + result.lifecycle().summary());
            plugin.pathDebug().traceSampled(plugin, "waves", "front.direction",
                    "[DIRECTION] " + result.directions().summary()
                            + " cardinal=" + result.directions().cardinal()
                            + " diagonal=" + result.directions().diagonal());
            plugin.pathDebug().traceSampled(plugin, "waves", "front.advance",
                    "[FRONT] active=" + result.activeFronts() + " moved=" + (Math.round(result.frontMovementBlocks() * 100.0D) / 100.0D) + " shore-guided=" + result.shoreGuidedFronts() + " traveling=" + result.expandingColumns()
                            + " fizzling=" + result.closingColumns());
            plugin.pathDebug().traceSampled(plugin, "waves", "front.merge",
                    "[FRONT][MERGE] resultant-fronts=" + result.stateMerges() + " connected-columns=" + result.mergedColumns());
            if (result.shoreApproachingColumns() > 0 || result.leewardColumns() > 0
                    || result.lakeInboundColumns() > 0 || result.fetchAttenuatedColumns() > 0
                    || result.shoreHeightCaps() > 0
                    || result.shoreImpacts() > 0 || result.runups() > 0) {
                plugin.pathDebug().traceSampled(plugin, "waves", "front.shore-impact",
                        "[FETCH] attenuated=" + result.fetchAttenuatedColumns()
                                + " [COAST] columns=" + result.shoreApproachingColumns()

                                + " [STEER] radius=" + SHORE_STEERING_DISTANCE
                                + " guided-columns=" + result.lakeInboundColumns()
                                + " [LAKE] guided-components=" + result.lakeComponents()
                                + " enclosed=" + result.enclosedLakeComponents()
                                + " [CAP] columns=" + result.shoreHeightCaps()
                                + " [SHORE] impacts=" + result.shoreImpacts()
                                + " [RUNUP] blocks=" + result.runups());
            }
            schedulePlayerWaves(player, currentConfig.updateIntervalTicks());
        }
    }

    private ScanResult collectWaveVisuals(Player player, WaveProfile profile, WaveWind wind,
            long tick, WaveConfig currentConfig, Map<WaveRenderer.WaveKey, Integer> visuals,
            Set<Long> uncertainColumns) {
        World world = player.getWorld();
        Location origin = player.getLocation();
        int radius = currentConfig.renderRadius();
        int simulationRadius = currentConfig.simulationRadius();
        int step = currentConfig.columnStep();
        int scanned = 0;
        int crests = 0;
        int runups = 0;
        int particles = 0;
        int travelingCrests = 0;
        int fizzlingCrests = 0;
        int mergedColumns = 0;
        int shoreApproachingColumns = 0;
        int leewardColumns = 0;
        int fetchAttenuatedColumns = 0;
        int shoreHeightCaps = 0;
        int expandingColumns = 0;
        int closingColumns = 0;
        int lakeInboundColumns = 0;
        WaveLakeFlowCache.Snapshot lakeFlow = lakeFlowCache.snapshot(
                player.getUniqueId(), world, origin.getBlockX(), origin.getBlockZ(),
                simulationRadius, step, tick, surfaceCache, currentConfig);
        TravelingWaveRegistry.Update frontUpdate = travelingWaves.update(
                player.getUniqueId(), world.getUID(), origin.getBlockX(), origin.getBlockZ(),
                tick, profile, currentConfig.ovalSettings(), wind.x(), wind.z(),
                radius, simulationRadius,
                currentConfig.maximumIncomingFrontsPerCoastAreaPerPlayer(), lakeFlow);
        ViewMotion viewMotion = trackView(player.getUniqueId(), world.getUID(),
                origin.getBlockX(), origin.getBlockZ(),
                lakeFlow.centerX(), lakeFlow.centerZ());
        plugin.pathDebug().traceSampled(plugin, "waves", "front.state",
                frontStateSummary(frontUpdate.fronts()));
        plugin.pathDebug().traceSampled(plugin, "waves", "source.static",
                "[SOURCE] " + frontUpdate.sources().summary());
        plugin.pathDebug().traceSampled(plugin, "waves", "topology.anchor",
                "[TOPOLOGY] player=" + origin.getBlockX() + "," + origin.getBlockZ()
                        + " anchor=" + lakeFlow.centerX() + "," + lakeFlow.centerZ()
                        + " visible-radius=" + radius + " simulation-radius=" + simulationRadius
                        + " lattice-step=" + lakeFlow.step());

        // ## A front is sampled from one persistent center and one persistent heading.
        // Terrain may steer that object, but individual columns cannot rotate or pulse
        // independently. Height therefore follows horizontal crest passage only.
        Map<Long, FrontCell> frontCells = new HashMap<>();
        int radiusSquared = radius * radius;
        for (TravelingWaveFront front : frontUpdate.fronts()) {
            int boundsX = front.boundsX();
            int boundsZ = front.boundsZ();
            int minX = Math.max(origin.getBlockX() - radius,
                    (int) Math.floor(front.x()) - boundsX);
            int maxX = Math.min(origin.getBlockX() + radius,
                    (int) Math.ceil(front.x()) + boundsX);
            int minZ = Math.max(origin.getBlockZ() - radius,
                    (int) Math.floor(front.z()) - boundsZ);
            int maxZ = Math.min(origin.getBlockZ() + radius,
                    (int) Math.ceil(front.z()) + boundsZ);
            minX = WaveLakeFlowCache.advanceToWorldGrid(minX, step);
            minZ = WaveLakeFlowCache.advanceToWorldGrid(minZ, step);
            for (int x = minX; x <= maxX; x += step) {
                int dx = x - origin.getBlockX();
                for (int z = minZ; z <= maxZ; z += step) {
                    int dz = z - origin.getBlockZ();
                    if ((dx * dx) + (dz * dz) > radiusSquared || !lakeFlow.isWater(x, z)) {
                        continue;
                    }
                    double strength = front.strengthAt(x + 0.5D, z + 0.5D, tick);
                    if (strength <= 0.001D) {
                        continue;
                    }
                    frontCells.computeIfAbsent(packXZ(x, z), ignored -> new FrontCell())
                            .add(strength, front.fizzling(), front.shoreGuided());
                }
            }
        }

        List<Map.Entry<Long, FrontCell>> candidates = new ArrayList<>(frontCells.entrySet());
        if (candidates.size() > currentConfig.maxColumnsPerTick()) {
            // Preserve the solid core of every front if an extreme overlap exceeds the
            // configured packet budget; dropping an arbitrary map tail created visible holes.
            candidates.sort((first, second) -> Double.compare(
                    second.getValue().strength, first.getValue().strength));
            plugin.pathDebug().failure(plugin, "waves", "front-field-budget",
                    "candidates=" + candidates.size() + " budget=" + currentConfig.maxColumnsPerTick());
        }
        int visibleCandidates = Math.min(candidates.size(), currentConfig.maxColumnsPerTick());
        for (int index = visibleCandidates; index < candidates.size(); index++) {
            // ## Budget-deferred is not absent. Keep any previous packet column until sampled.
            uncertainColumns.add(candidates.get(index).getKey());
        }
        int shoreImpacts = frontUpdate.shoreImpacts();
        for (int index = 0; index < visibleCandidates; index++) {
            Map.Entry<Long, FrontCell> entry = candidates.get(index);
            int x = unpackX(entry.getKey());
            int z = unpackZ(entry.getKey());
            FrontCell frontCell = entry.getValue();
            WaveModel.WaveSample sample = model.travelingFront(
                    currentConfig.ovalSettings(), frontCell.strength,
                    frontCell.fizzling, frontCell.contributors, frontCell.contributors);
            if (!sample.crest()) {
                continue;
            }
            WaveSurfaceCache.SurfaceLookup lookup = surfaceCache.surfaceLookup(
                    plugin, diagnostics, world, x, z, tick, currentConfig);
            if (lookup.status() == WaveSurfaceCache.LookupStatus.UNKNOWN) {
                uncertainColumns.add(entry.getKey());
                continue;
            }
            if (lookup.status() == WaveSurfaceCache.LookupStatus.ABSENT) {
                continue;
            }
            scanned++;
            WaveSurfaceCache.SurfaceColumn surface = lookup.column();
            if (!surface.biomeAllowed()) {
                diagnostics.recordRejected();
                continue;
            }

            boolean shoreZone = surface.hasShoreBias()
                    && surface.shoreDistance() <= currentConfig.shoreResponseDistance();
            double incomingHeight = sample.height();
            if (shoreZone) {
                sample = model.shoreAdjusted(currentConfig.ovalSettings(), sample,
                        surface.shoreDistance(), surface.waterDepth(),
                        surface.shoreHeightCap(), 1.0D, 0.0D);
                shoreHeightCaps++;
                shoreApproachingColumns++;
            }
            if (!sample.crest()) {
                continue;
            }
            boolean physicalShoreImpact = shoreZone
                    && shoreRunupPolicy.hasArrived(
                            surface.shoreDistance(), frontCell.fizzling);

            if (frontCell.contributors >= 2) {
                mergedColumns++;
            }
            if (frontCell.shoreGuided) {
                lakeInboundColumns++;
            }
            if (physicalShoreImpact) {
                shoreImpacts++;
            }
            if (sample.fizzling()) {
                closingColumns++;
                fizzlingCrests++;
            } else {
                expandingColumns++;
                travelingCrests++;
            }
            putWaveVisuals(visuals, world, x, surface.y(), z, sample);
            crests++;
            diagnostics.recordLayer(sample.layer());

            boolean shoreSplash = physicalShoreImpact && sample.layer() >= 2;
            boolean openWaterFoam = !shoreZone && sample.layer() == 4
                    && Math.floorMod((x * 31) + (z * 17) + (int) (tick / 20L), 19) == 0;
            if (currentConfig.particlesEnabled() && particles < currentConfig.particleBudget()
                    && (shoreSplash || openWaterFoam)) {
                spawnCrestParticle(player, x, surface.y() + 1, z, world.hasStorm(),
                        sample.energy(), shoreSplash);
                particles++;
            }
            boolean eligibleRunupEnergy = sample.energy() > 0.68D;
            if (currentConfig.shorelineRunupEnabled()
                    && sample.shoreImpact() && eligibleRunupEnergy
                    && !physicalShoreImpact) {
                diagnostics.recordRunupPrearrivalStop();
                plugin.pathDebug().traceSampled(plugin, "waves",
                        "runup.blocked-prearrival",
                        "water=" + x + "," + surface.y() + "," + z
                                + " shore-distance=" + surface.shoreDistance()
                                + " front-fizzling=" + frontCell.fizzling
                                + " ## shore compression is visual; land run-up waits for physical arrival");
            }
            if (currentConfig.shorelineRunupEnabled()
                    && physicalShoreImpact && eligibleRunupEnergy) {
                runups += addShorelineRunup(world, currentConfig, visuals,
                        surface, incomingHeight, sample.waterLevel(), tick);
            }
        }

        diagnostics.recordParticles(particles);
        diagnostics.recordCrestLifecycle(travelingCrests, fizzlingCrests);
        diagnostics.recordShoreResponse(shoreApproachingColumns, leewardColumns,
                fetchAttenuatedColumns, shoreHeightCaps);
        return new ScanResult(scanned, crests, runups, particles, travelingCrests, fizzlingCrests,
                frontUpdate.fronts().size(), mergedColumns, shoreImpacts, shoreApproachingColumns,
                leewardColumns, fetchAttenuatedColumns, shoreHeightCaps,
                expandingColumns, closingColumns, lakeFlow.field().shoreGuidedComponents(),
                lakeFlow.field().enclosedComponents(), lakeInboundColumns,
                lakeFlow.knownCells(), lakeFlow.waterCells(), lakeFlow.inheritedCells(),
                uncertainColumns.size(), frontUpdate.movedBlocks(),
                frontUpdate.shoreGuidedFronts(), frontUpdate.mergedFronts(),
                frontUpdate.lifecycle(), frontUpdate.directions(),
                frontUpdate.sources(),
                viewMotion.playerMovement(), viewMotion.anchorMovement(),
                viewMotion.chunkBoundaryCrossed());
    }

    private void putWaveVisuals(Map<WaveRenderer.WaveKey, Integer> visuals, World world,
            int x, int surfaceY, int z, WaveModel.WaveSample sample) {
        for (WaveHeightStack.VisualLayer layer : heightStack.layers(sample.height())) {
            int y = surfaceY + 1 + layer.yOffset();
            if (plugin.canEvolveAt(new Location(world, x, y, z), "waves")) {
                visuals.put(new WaveRenderer.WaveKey(
                        world.getUID(), x, y, z), layer.waterLevel());
            }
        }
    }
    private int addShorelineRunup(World world, WaveConfig currentConfig,
            Map<WaveRenderer.WaveKey, Integer> visuals,
            WaveSurfaceCache.SurfaceColumn surface, double incomingHeight,
            int level, long tick) {
        if (!surface.hasShoreBias()) {
            return 0;
        }
        int shoreX = surface.x() + (surface.shoreDx() * surface.shoreDistance());
        int shoreZ = surface.z() + (surface.shoreDz() * surface.shoreDistance());
        long frontStarted = runupStartedTick(world, currentConfig, shoreX, shoreZ,
                surface.shoreDx(), surface.shoreDz(), tick);
        int allowedBlocks = Math.max(1,
                (int) ((tick - frontStarted) / currentConfig.runupAdvanceTicksPerBlock()) + 1);
        int maxBlocks = Math.min(currentConfig.shorelineRunupDistance(), allowedBlocks);
        int maximumReachableGroundY = shoreRunupPolicy.maximumReachableGroundY(
                surface.y(), incomingHeight);
        int added = 0;
        // ## The detected coastline is the run-up origin. Earlier logic started at
        // the offshore crest, so a six-block run-up could never reach a shore found
        // up to sixteen blocks away.
        for (int offset = 0; offset < maxBlocks; offset++) {
            int x = shoreX + (surface.shoreDx() * offset);
            int z = shoreZ + (surface.shoreDz() * offset);
            if (!isOwnedLoaded(world, x, z)) {
                break;
            }
            int groundY = world.getHighestBlockYAt(x, z);
            if (!shoreRunupPolicy.canReachGround(
                    surface.y(), groundY, incomingHeight)) {
                diagnostics.recordRunupHeightStop();
                plugin.pathDebug().traceSampled(plugin, "waves",
                        "runup.blocked-crest-height",
                        "water-y=" + surface.y()
                                + " incoming-height=" + rounded(incomingHeight)
                                + " max-ground-y=" + maximumReachableGroundY
                                + " terrain-y=" + groundY
                                + " at=" + x + "," + z
                                + " ## land higher than the incoming crest stops propagation");
                break;
            }
            if (surface.shoreY() >= 0 && groundY > surface.shoreY()) {
                // ## Run-up crosses level shore ground but never climbs above coast elevation.
                break;
            }
            Block target = world.getBlockAt(x, groundY + 1, z);
            if (!plugin.canEvolveAt(target.getLocation(), "waves")) {
                continue;
            }
            Material ground = world.getBlockAt(x, groundY, z).getType();
            if (!WaveMaterials.isRunupGround(ground)
                    || !WaveMaterials.isVisualReplaceable(target.getType())
                    || target.getLightFromSky() <= 0) {
                break;
            }
            int runupLevel = Math.min(7, level + offset + 1);
            WaveRenderer.WaveKey key = new WaveRenderer.WaveKey(
                    world.getUID(), x, groundY + 1, z);
            long retreat = currentConfig.runupRetreatTicksPerBlock()
                    * (long) (currentConfig.shorelineRunupDistance() - offset);
            shoreRunups.put(key, new ShoreRunupState(
                    frontStarted, tick + Math.max(1L, retreat)));
            visuals.put(key, runupLevel);
            added++;
        }
        diagnostics.recordRunupPropagation(added);
        return added;
    }

    private boolean isOwnedLoaded(World world, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            plugin.pathDebug().failure(plugin, "waves", "chunk-or-region-gate", "target chunk " + chunkX + "," + chunkZ);
            return false;
        }
        return true;
    }

    private void spawnCrestParticle(Player player, int x, int y, int z, boolean storm,
            double energy, boolean shoreSplash) {
        Particle particle = shoreSplash || storm ? Particle.SPLASH : Particle.BUBBLE_POP;
        int count = shoreSplash ? (storm ? 4 : 3) : (storm ? 2 : 1);
        double spread = shoreSplash ? 0.38D : 0.24D;
        player.spawnParticle(particle, x + 0.5D, y + 0.08D, z + 0.5D, count,
                spread, shoreSplash ? 0.12D : 0.05D, spread, Math.max(0.01D, energy * 0.05D));
    }

    private WaveWind currentWind() {
        double x = plugin.windFeature() == null ? 0.86D : plugin.windFeature().currentWindX();
        double z = plugin.windFeature() == null ? 0.50D : plugin.windFeature().currentWindZ();
        double strength = plugin.windFeature() == null ? 0.80D : plugin.windFeature().currentWindStrength();
        WaveEnvironmentModel.Direction direction = environment.normalize(x, z);
        return new WaveWind(direction.x(), direction.z(), Math.max(0.0D, strength));
    }
    private void bobNearbyBoats(Player player, long tick, WaveConfig currentConfig) {
        if (!player.isOnline()) {
            return;
        }
        for (Entity entity : player.getNearbyEntities(12.0D, 4.0D, 12.0D)) {
            if (!(entity instanceof Boat boat) || !boat.isValid()) {
                continue;
            }
            Location location = boat.getLocation();
            if (!isOwnedLoaded(location.getWorld(), location.getBlockX(), location.getBlockZ())
                    || !plugin.canEvolveAt(location, "waves")) {
                continue;
            }
            TravelingWaveRegistry.FrontSample frontSample = travelingWaves.sample(
                    player.getUniqueId(), location.getX(), location.getZ(), tick);
            if (frontSample.contributors() == 0
                    || !boatInsideWaveEnvelope(player.getUniqueId(), location.getBlockX(),
                            location.getBlockZ(), tick, currentConfig)) {
                diagnostics.recordBoatEnvelopeSkip();
                continue;
            }
            WaveModel.WaveSample sample = model.travelingFront(
                    currentConfig.ovalSettings(), frontSample.strength(),
                    frontSample.fizzling(), frontSample.contributors(), frontSample.contributors());
            if (sample.energy() < 0.25D) {
                continue;
            }
            Vector velocity = boat.getVelocity();
            double lift = Math.max(-0.03D, Math.min(0.05D, sample.height() * 0.035D));
            boat.setVelocity(new Vector(velocity.getX(), Math.max(velocity.getY(), lift), velocity.getZ()));
            diagnostics.recordBoatBob();
        }
    }
    private long runupStartedTick(World world, WaveConfig currentConfig,
            int shoreX, int shoreZ, int shoreDx, int shoreDz, long tick) {
        for (int offset = 0; offset < currentConfig.shorelineRunupDistance(); offset++) {
            int x = shoreX + (shoreDx * offset);
            int z = shoreZ + (shoreDz * offset);
            int y = world.getHighestBlockYAt(x, z) + 1;
            ShoreRunupState state = shoreRunups.get(
                    new WaveRenderer.WaveKey(world.getUID(), x, y, z));
            if (state != null && tick <= state.expiresTick()) {
                return state.startedTick();
            }
        }
        return tick;
    }

    private void trimExpiredRunups(long tick, WaveConfig currentConfig) {
        shoreRunups.entrySet().removeIf(entry -> tick > entry.getValue().expiresTick());
        int excess = shoreRunups.size() - (currentConfig.maxColumnsPerTick() * 4);
        if (excess <= 0) {
            return;
        }
        for (Map.Entry<WaveRenderer.WaveKey, ShoreRunupState> entry : shoreRunups.entrySet()) {
            if (excess-- <= 0) {
                break;
            }
            shoreRunups.remove(entry.getKey(), entry.getValue());
        }
    }

    private boolean boatInsideWaveEnvelope(java.util.UUID playerId, int x, int z,
            long tick, WaveConfig currentConfig) {
        int radius = currentConfig.boatEnvelopeRadius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > radius * radius) {
                    continue;
                }
                TravelingWaveRegistry.FrontSample nearby = travelingWaves.sample(
                        playerId, x + dx, z + dz, tick);
                if (nearby.strength() > 0.03D) {
                    return true;
                }
            }
        }
        return false;
    }

    private ViewMotion trackView(UUID playerId, UUID worldId, int playerX, int playerZ,
            int anchorX, int anchorZ) {
        ViewState next = new ViewState(worldId, playerX, playerZ, anchorX, anchorZ);
        ViewState previous = viewStates.put(playerId, next);
        if (previous == null || !previous.worldId().equals(worldId)) {
            return new ViewMotion(0.0D, 0.0D, false);
        }
        return new ViewMotion(
                Math.hypot(playerX - previous.playerX(), playerZ - previous.playerZ()),
                Math.hypot(anchorX - previous.anchorX(), anchorZ - previous.anchorZ()),
                (playerX >> 4) != (previous.playerX() >> 4)
                        || (playerZ >> 4) != (previous.playerZ() >> 4));
    }

    private double rounded(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private String frontStateSummary(List<TravelingWaveFront> fronts) {
        if (fronts.isEmpty()) {
            return "[STATE][FRONT] active=0";
        }
        StringBuilder summary = new StringBuilder("[STATE][FRONT] active=")
                .append(fronts.size());
        int limit = Math.min(4, fronts.size());
        for (int index = 0; index < limit; index++) {
            TravelingWaveFront front = fronts.get(index);
            summary.append(" #").append(front.id())
                    .append(" kind=").append(front.kind())
                    .append(" pos=").append(Math.round(front.x() * 10.0D) / 10.0D)
                    .append(',').append(Math.round(front.z() * 10.0D) / 10.0D)
                    .append(" heading=").append(Math.round(front.headingX() * 100.0D) / 100.0D)
                    .append(',').append(Math.round(front.headingZ() * 100.0D) / 100.0D)
                    .append(" travelled=").append(Math.round(front.travelled() * 10.0D) / 10.0D)
                    .append(" guided=").append(front.shoreGuided())
                    .append(" mode=").append(front.channelCourseLocked()
                            ? "CHANNEL_DIRECTION_LOCKED"
                            : front.narrowPassageLocked()
                                    ? "TIGHT_WATER_LOCKED"
                                    : front.openWaterExpanding()
                                            ? "OPEN_WATER_FAN_EXPANDING"
                                            : front.openWaterFan()
                                                    ? "OPEN_WATER_FAN_MATURE"
                                                    : "OPEN_WATER");
            if (front.narrowPassageLocked()) {
                summary.append(" crowd=").append(front.passageCrowding())
                        .append(" half-width=")
                        .append(Math.round(front.halfWidth() * 10.0D) / 10.0D);
            }
            if (front.channelCourseLocked()) {
                TravelingWaveFront.Direction channel = front.channelCourse();
                summary.append(" channel-heading=")
                        .append(Math.round(channel.x() * 100.0D) / 100.0D)
                        .append(',').append(Math.round(channel.z() * 100.0D) / 100.0D);
            }            if (front.openWaterFan()) {
                summary.append(" fan-half-width=")
                        .append(Math.round(front.halfWidth() * 10.0D) / 10.0D)
                        .append('/').append(
                                Math.round(front.openWaterTargetHalfWidth() * 10.0D) / 10.0D);
            }
            summary.append(" phase=").append(front.fizzling() ? "FIZZLE" : "TRAVEL");
            if (front.hasShoreTarget()) {
                TravelingWaveFront.Direction shore = front.lockedShoreDirection();
                double alignment = (front.headingX() * shore.x())
                        + (front.headingZ() * shore.z());
                summary.append(" shore-target=")
                        .append(Math.round(front.shoreTargetX() * 10.0D) / 10.0D)
                        .append(',').append(Math.round(front.shoreTargetZ() * 10.0D) / 10.0D)
                        .append(" shore-distance=")
                        .append(Math.round(front.lockedShoreDistance() * 10.0D) / 10.0D)
                        .append(" shore-alignment=")
                        .append(Math.round(alignment * 100.0D) / 100.0D);
            }
        }
        return summary.toString();
    }

    private long packXZ(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private int unpackZ(long packed) {
        return (int) packed;
    }

    private static final class FrontCell {
        private double strength;
        private double dominantStrength;
        private int contributors;
        private boolean fizzling;
        private boolean shoreGuided;

        private void add(double nextStrength, boolean nextFizzling, boolean nextShoreGuided) {
            strength = 1.0D - ((1.0D - strength) * (1.0D - nextStrength));
            contributors++;
            shoreGuided |= nextShoreGuided;
            if (nextStrength > dominantStrength) {
                dominantStrength = nextStrength;
                fizzling = nextFizzling;
            }
        }
    }
    private record ScanResult(int columnsScanned, int crests, int runups, int particles,
            int travelingCrests, int fizzlingCrests, int activeFronts, int mergedColumns,
            int shoreImpacts, int shoreApproachingColumns, int leewardColumns,
            int fetchAttenuatedColumns, int shoreHeightCaps,
            int expandingColumns, int closingColumns, int lakeComponents,
            int enclosedLakeComponents, int lakeInboundColumns,
            int topologyKnownCells, int topologyWaterCells, int inheritedTopologyCells,
            int uncertainSurfaceColumns,
            double frontMovementBlocks, int shoreGuidedFronts, int stateMerges,
            TravelingWaveRegistry.Lifecycle lifecycle,
            TravelingWaveRegistry.DirectionSummary directions,
            TravelingWaveRegistry.SourceSummary sources,
            double playerMovement, double anchorMovement,
            boolean chunkBoundaryCrossed) {
    }

    private record ViewMotion(double playerMovement, double anchorMovement,
            boolean chunkBoundaryCrossed) {
    }

    private record ViewState(UUID worldId, int playerX, int playerZ,
            int anchorX, int anchorZ) {
    }

    private record WaveWind(double x, double z, double strength) {
        String summary() {
            return "direction=" + Math.round(x * 100.0D) / 100.0D + ","
                    + Math.round(z * 100.0D) / 100.0D
                    + " strength=" + Math.round(strength * 100.0D) / 100.0D;
        }
    }

    private record ShoreRunupState(long startedTick, long expiresTick) {
    }
    // ## WAVES ACTION HIERARCHY
    private void traceHierarchy(WaveActionPhase phase, String reason) {
        FeatureHierarchyTrace.record(plugin, ACTION_HIERARCHY, phase, reason);
    }

    private void traceHierarchy(
            WaveActionPhase phase,
            WaveActionSubrule subrule,
            String reason) {
        // ## Nested trace ownership makes viewer distribution distinguishable from
        // shared front simulation in architecture and resource debug reports.
        FeatureHierarchyTrace.record(
                plugin, ACTION_HIERARCHY, phase, subrule, reason);
    }

}
