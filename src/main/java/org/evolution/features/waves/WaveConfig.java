package org.evolution.features.waves;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.evolution.coreparts.RuntimeProfile;
import org.evolution.coreparts.EvolutionPlugin;

final class WaveConfig {
    private static final Set<String> DEFAULT_BIOMES = Set.of(
            "ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean",
            "cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean",
            "river", "frozen_river", "swamp", "mangrove_swamp", "beach", "snowy_beach", "stony_shore"
    );

    private final boolean enabled;
    private final int simulationRadius;
    private final int renderRadius;
    private final long updateIntervalTicks;
    private final int maxBlockUpdatesPerPlayer;
    private final int maxColumnsPerTick;
    private final int columnStep;
    private final boolean packedBlockUpdates;
    private final long packetReassertIntervalTicks;
    private final int packetReassertBudget;
    private final long surfaceCacheTtlTicks;
    private final int surfaceCacheMaxChunks;
    private final int maxWaterDepthScan;
    private final long stickyVisualTicks;
    private final int crestLifecycleTicks;
    private final double crestSmoothing;
    private final boolean shorelineRunupEnabled;
    private final int shoreResponseDistance;
    private final int fetchDistance;
    private final double minimumShoreFacing;
    private final int shorelineRunupDistance;
    private final int runupAdvanceTicksPerBlock;
    private final int runupRetreatTicksPerBlock;
    private final boolean particlesEnabled;
    private final int particleBudget;
    private final boolean boatBobbingEnabled;
    private final int boatEnvelopeRadius;
    private final OvalWaveSettings ovalSettings;
    private final WaveProfile clearProfile;
    private final WaveProfile stormProfile;
    private final boolean biomeFilterEnabled;
    private final Set<String> worldsWhitelist;
    private final Set<String> biomesWhitelist;
    private final int debugRecentEvents;
    private final boolean testingEnabled;

    private WaveConfig(
            boolean enabled,
            int simulationRadius,
            int renderRadius,
            long updateIntervalTicks,
            int maxBlockUpdatesPerPlayer,
            int maxColumnsPerTick,
            int columnStep,
            boolean packedBlockUpdates,
            long packetReassertIntervalTicks,
            int packetReassertBudget,
            long surfaceCacheTtlTicks,
            int surfaceCacheMaxChunks,
            int maxWaterDepthScan,
            long stickyVisualTicks,
            int crestLifecycleTicks,
            double crestSmoothing,
            boolean shorelineRunupEnabled,
            int shoreResponseDistance,
            int fetchDistance,
            double minimumShoreFacing,
            int shorelineRunupDistance,
            int runupAdvanceTicksPerBlock,
            int runupRetreatTicksPerBlock,
            boolean particlesEnabled,
            int particleBudget,
            boolean boatBobbingEnabled,
            int boatEnvelopeRadius,
            OvalWaveSettings ovalSettings,
            WaveProfile clearProfile,
            WaveProfile stormProfile,
            boolean biomeFilterEnabled,
            Set<String> worldsWhitelist,
            Set<String> biomesWhitelist,
            int debugRecentEvents,
            boolean testingEnabled
    ) {
        this.enabled = enabled;
        this.simulationRadius = simulationRadius;
        this.renderRadius = Math.min(renderRadius, simulationRadius);
        this.updateIntervalTicks = updateIntervalTicks;
        this.maxBlockUpdatesPerPlayer = maxBlockUpdatesPerPlayer;
        this.maxColumnsPerTick = maxColumnsPerTick;
        this.columnStep = columnStep;
        this.packedBlockUpdates = packedBlockUpdates;
        this.packetReassertIntervalTicks = packetReassertIntervalTicks;
        this.packetReassertBudget = packetReassertBudget;
        this.surfaceCacheTtlTicks = surfaceCacheTtlTicks;
        this.surfaceCacheMaxChunks = surfaceCacheMaxChunks;
        this.maxWaterDepthScan = maxWaterDepthScan;
        this.stickyVisualTicks = stickyVisualTicks;
        this.crestLifecycleTicks = crestLifecycleTicks;
        this.crestSmoothing = crestSmoothing;
        this.shorelineRunupEnabled = shorelineRunupEnabled;
        this.shoreResponseDistance = shoreResponseDistance;
        this.fetchDistance = fetchDistance;
        this.minimumShoreFacing = minimumShoreFacing;
        this.shorelineRunupDistance = shorelineRunupDistance;
        this.runupAdvanceTicksPerBlock = runupAdvanceTicksPerBlock;
        this.runupRetreatTicksPerBlock = runupRetreatTicksPerBlock;
        this.particlesEnabled = particlesEnabled;
        this.particleBudget = particleBudget;
        this.boatBobbingEnabled = boatBobbingEnabled;
        this.boatEnvelopeRadius = boatEnvelopeRadius;
        this.ovalSettings = ovalSettings;
        this.clearProfile = clearProfile;
        this.stormProfile = stormProfile;
        this.biomeFilterEnabled = biomeFilterEnabled;
        this.worldsWhitelist = worldsWhitelist;
        this.biomesWhitelist = biomesWhitelist;
        this.debugRecentEvents = debugRecentEvents;
        this.testingEnabled = testingEnabled;
    }

    static WaveConfig load(EvolutionPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        boolean testing = RuntimeProfile.testingEnabled(config) && config.getBoolean("waves.testing.enabled", true);
        int simulationRadius = Math.max(8, config.getInt("waves.simulation-radius", 120));
        int renderRadius = Math.max(8, config.getInt("waves.render-radius", 100));
        long configuredUpdateInterval = Math.max(1L, testing
                ? config.getLong("waves.testing.update-interval-ticks", 6L)
                : config.getLong("waves.update-interval-ticks", 6L));
        long updateInterval = config.getBoolean("waves.smooth-motion.enabled", true)
                ? Math.min(configuredUpdateInterval,
                        Math.max(1L, config.getLong("waves.smooth-motion.max-update-interval-ticks", 4L)))
                : configuredUpdateInterval;
        return new WaveConfig(
                config.getBoolean("waves.enabled", true),
                testing ? Math.max(8, config.getInt("waves.testing.simulation-radius", simulationRadius)) : simulationRadius,
                testing ? Math.max(8, config.getInt("waves.testing.render-radius", renderRadius)) : renderRadius,
                updateInterval,
                Math.max(1, config.getInt("waves.max-block-updates-per-tick-per-player", 26000)),
                Math.max(16, testing ? config.getInt("waves.testing.max-columns-per-tick", 20000) : config.getInt("waves.max-columns-per-tick", 20000)),
                Math.max(1, config.getInt("waves.column-step", 1)),
                config.getBoolean("waves.render.packed-block-updates", true),
                Math.max(1L, config.getLong("waves.render.reassert-interval-ticks", 40L)),
                Math.max(0, config.getInt("waves.render.reassert-budget-per-frame", 1024)),
                Math.max(1L, config.getLong("waves.surface-cache.ttl-ticks", 600L)),
                Math.max(8, config.getInt("waves.surface-cache.max-chunks", 768)),
                Math.max(1, config.getInt("waves.surface-cache.max-water-depth-scan", 8)),
                Math.max(0L, config.getLong("waves.visual-memory.sticky-ticks", 8L)),
                Math.max(1, config.getInt("waves.visual-memory.crest-lifecycle-ticks", 12)),
                clamp(config.getDouble("waves.visual-memory.smoothing", 0.35D), 0.0D, 1.0D),
                config.getBoolean("waves.shoreline-runup.enabled", true),
                Math.max(1, config.getInt("waves.shoreline-response.distance", 16)),
                Math.max(1, config.getInt("waves.shoreline-response.fetch-distance", 16)),
                clamp(config.getDouble("waves.shoreline-response.minimum-facing", 0.20D), 0.0D, 0.95D),
                Math.max(1, config.getInt("waves.shoreline-runup.max-distance", 6)),
                Math.max(1, config.getInt("waves.shoreline-runup.advance-ticks-per-block", 3)),
                Math.max(1, config.getInt("waves.shoreline-runup.retreat-ticks-per-block", 5)),
                config.getBoolean("waves.particles.enabled", true),
                Math.max(0, config.getInt("waves.particles.budget-per-player", 18)),
                config.getBoolean("waves.boats.bobbing-enabled", true),
                Math.max(1, config.getInt("waves.boats.envelope-radius", 3)),
                OvalWaveSettings.load(config.getConfigurationSection("waves.oval-pulses")),
                profile(config.getConfigurationSection("waves.clear"), new WaveProfile(0.78D, 1.35D, 44, 2.20D, 0.55D, 0.98D, 168, 0.62D, 0.72D)),
                profile(config.getConfigurationSection("waves.storm"), new WaveProfile(2.10D, 2.00D, 36, 2.35D, 0.95D, 1.00D, 208, 0.68D, 0.62D)),
                config.getBoolean("waves.biome-filter-enabled", false),
                normalize(config.getStringList("waves.worlds-whitelist")),
                normalizeBiomeList(config.getStringList("waves.biomes-whitelist")),
                Math.max(0, config.getInt("waves.debug.recent-events", 100)),
                testing
        );
    }

    boolean enabled() { return enabled; }
    int simulationRadius() { return simulationRadius; }
    int renderRadius() { return renderRadius; }
    long updateIntervalTicks() { return updateIntervalTicks; }
    int maxBlockUpdatesPerPlayer() { return maxBlockUpdatesPerPlayer; }
    int maxColumnsPerTick() { return maxColumnsPerTick; }
    int columnStep() { return columnStep; }
    boolean packedBlockUpdates() { return packedBlockUpdates; }
    long packetReassertIntervalTicks() { return packetReassertIntervalTicks; }
    int packetReassertBudget() { return packetReassertBudget; }
    long surfaceCacheTtlTicks() { return surfaceCacheTtlTicks; }
    int surfaceCacheMaxChunks() { return surfaceCacheMaxChunks; }
    int maxWaterDepthScan() { return maxWaterDepthScan; }
    long stickyVisualTicks() { return stickyVisualTicks; }
    int crestLifecycleTicks() { return crestLifecycleTicks; }
    double crestSmoothing() { return crestSmoothing; }
    boolean shorelineRunupEnabled() { return shorelineRunupEnabled; }
    int shoreResponseDistance() { return shoreResponseDistance; }
    int fetchDistance() { return fetchDistance; }
    double minimumShoreFacing() { return minimumShoreFacing; }
    int shorelineRunupDistance() { return shorelineRunupDistance; }
    int runupAdvanceTicksPerBlock() { return runupAdvanceTicksPerBlock; }
    int runupRetreatTicksPerBlock() { return runupRetreatTicksPerBlock; }
    boolean particlesEnabled() { return particlesEnabled; }
    int particleBudget() { return particleBudget; }
    boolean boatBobbingEnabled() { return boatBobbingEnabled; }
    int boatEnvelopeRadius() { return boatEnvelopeRadius; }
    OvalWaveSettings ovalSettings() { return ovalSettings; }

    WaveProfile profile(World world) {
        return world.hasStorm() || world.isThundering() ? stormProfile : clearProfile;
    }

    boolean isWorldAllowed(World world) {
        return worldsWhitelist.isEmpty() || worldsWhitelist.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    boolean isBiomeAllowed(Biome biome) {
        return !biomeFilterEnabled || biomesWhitelist.isEmpty()
                || biomesWhitelist.contains(normalizeBiome(biome.getKey().getKey()));
    }

    int debugRecentEvents() { return debugRecentEvents; }

    String summary() {
        return "enabled=" + enabled + ", update=" + updateIntervalTicks + ", sim-radius=" + simulationRadius
                + ", render-radius=" + renderRadius + ", max-columns=" + maxColumnsPerTick
                + ", max-updates=" + maxBlockUpdatesPerPlayer + ", step=" + columnStep
                + ", packed=" + packedBlockUpdates + ", reassert=" + packetReassertIntervalTicks
                + "/" + packetReassertBudget + ", sticky=" + stickyVisualTicks
                + ", cache-ttl=" + surfaceCacheTtlTicks + ", shore-response=" + shoreResponseDistance
                + ", fetch=" + fetchDistance + ", minimum-facing=" + minimumShoreFacing + ", runup=" + shorelineRunupEnabled
                + ", particles=" + particlesEnabled + ", boats=" + boatBobbingEnabled
                + ", biome-filter=" + biomeFilterEnabled
                + ", " + ovalSettings.summary() + ", testing=" + testingEnabled;
    }

    private static WaveProfile profile(ConfigurationSection section, WaveProfile fallback) {
        if (section == null) {
            return fallback;
        }
        return new WaveProfile(
                section.getDouble("amplitude", fallback.amplitude()),
                section.getDouble("speed", fallback.speed()),
                section.getInt("wavelength", fallback.wavelength()),
                section.getDouble("frequency", fallback.frequency()),
                section.getDouble("height-variation", fallback.heightVariation()),
                section.getDouble("occurrence", fallback.occurrence()),
                section.getInt("travel-distance", fallback.travelDistance()),
                section.getDouble("fade-start", fallback.fadeStart()),
                section.getDouble("fade-power", fallback.fadePower())
        );
    }

    private static Set<String> normalize(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static Set<String> normalizeBiomeList(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return DEFAULT_BIOMES;
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(normalizeBiome(value));
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static String normalizeBiome(String value) {
        return value.toLowerCase(Locale.ROOT).replace("minecraft:", "").replace('-', '_');
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}