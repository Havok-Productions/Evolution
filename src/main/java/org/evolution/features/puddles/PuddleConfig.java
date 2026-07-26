package org.evolution.features.puddles;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.evolution.coreparts.RuntimeProfile;
import org.evolution.coreparts.EvolutionPlugin;

final class PuddleConfig {
    private final boolean enabled;
    private final boolean requireRainCapableBiome;
    private final boolean requireSkyExposure;
    private final boolean allowSnowfall;
    private final long stepTicks;
    private final int radius;
    private final int maxPuddlesPerWorld;
    private final int seedAttemptsPerCycle;
    private final int maxExpansionsPerCycle;
    private final int maxPuddleSize;
    private final int nearbyPuddlesToMerge;
    private final int mergeSearchRadius;
    private final int maxDepth;
    private final long dryDelayMillis;
    private final double seedChance;
    private final double expandChance;
    private final double singlePuddleExpandChance;
    private final double thunderstormMultiplier;
    private final double shrinkChance;
    private final boolean restoreOnDisable;
    private final long renderReassertMillis;
    private final int retentionRadiusMultiplier;
    private final int requiredPlayerDistanceChunks;
    private final int debugRecentEvents;
    private final boolean testingEnabled;
    private final long testingStepTicks;
    private final int testingSeedAttemptsPerCycle;
    private final int testingMaxExpansionsPerCycle;
    private final Set<String> enabledWorlds;
    private final Set<String> disabledWorlds;

    private PuddleConfig(
            boolean enabled,
            boolean requireRainCapableBiome,
            boolean requireSkyExposure,
            boolean allowSnowfall,
            long stepTicks,
            int radius,
            int maxPuddlesPerWorld,
            int seedAttemptsPerCycle,
            int maxExpansionsPerCycle,
            int maxPuddleSize,
            int nearbyPuddlesToMerge,
            int mergeSearchRadius,
            int maxDepth,
            long dryDelayMillis,
            double seedChance,
            double expandChance,
            double singlePuddleExpandChance,
            double thunderstormMultiplier,
            double shrinkChance,
            boolean restoreOnDisable,
            long renderReassertMillis,
            int retentionRadiusMultiplier,
            int requiredPlayerDistanceChunks,
            int debugRecentEvents,
            boolean testingEnabled,
            long testingStepTicks,
            int testingSeedAttemptsPerCycle,
            int testingMaxExpansionsPerCycle,
            Set<String> enabledWorlds,
            Set<String> disabledWorlds
    ) {
        this.enabled = enabled;
        this.requireRainCapableBiome = requireRainCapableBiome;
        this.requireSkyExposure = requireSkyExposure;
        this.allowSnowfall = allowSnowfall;
        this.stepTicks = stepTicks;
        this.radius = radius;
        this.maxPuddlesPerWorld = maxPuddlesPerWorld;
        this.seedAttemptsPerCycle = seedAttemptsPerCycle;
        this.maxExpansionsPerCycle = maxExpansionsPerCycle;
        this.maxPuddleSize = maxPuddleSize;
        this.nearbyPuddlesToMerge = nearbyPuddlesToMerge;
        this.mergeSearchRadius = mergeSearchRadius;
        this.maxDepth = maxDepth;
        this.dryDelayMillis = dryDelayMillis;
        this.seedChance = seedChance;
        this.expandChance = expandChance;
        this.singlePuddleExpandChance = singlePuddleExpandChance;
        this.thunderstormMultiplier = thunderstormMultiplier;
        this.shrinkChance = shrinkChance;
        this.restoreOnDisable = restoreOnDisable;
        this.renderReassertMillis = renderReassertMillis;
        this.retentionRadiusMultiplier = retentionRadiusMultiplier;
        this.requiredPlayerDistanceChunks = requiredPlayerDistanceChunks;
        this.debugRecentEvents = debugRecentEvents;
        this.testingEnabled = testingEnabled;
        this.testingStepTicks = testingStepTicks;
        this.testingSeedAttemptsPerCycle = testingSeedAttemptsPerCycle;
        this.testingMaxExpansionsPerCycle = testingMaxExpansionsPerCycle;
        this.enabledWorlds = enabledWorlds;
        this.disabledWorlds = disabledWorlds;
    }

    static PuddleConfig load(EvolutionPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        boolean testing = RuntimeProfile.testingEnabled(config) && config.getBoolean("puddles.testing.enabled", true);
        return new PuddleConfig(
                config.getBoolean("puddles.enabled", true),
                config.getBoolean("puddles.rain-restrictions.require-rain-capable-biome", true),
                config.getBoolean("puddles.rain-restrictions.require-sky-exposure", true),
                config.getBoolean("puddles.rain-restrictions.allow-snowfall", false),
                Math.max(5L, config.getLong("puddles.step-ticks", 20L)),
                Math.max(4, config.getInt("puddles.radius", 32)),
                Math.max(0, config.getInt("puddles.max-puddles-per-world", 300)),
                Math.max(1, config.getInt("puddles.growth.seed-attempts-per-cycle", 6)),
                Math.max(1, config.getInt("puddles.growth.max-expansions-per-cycle", 2)),
                Math.max(1, config.getInt("puddles.growth.max-puddle-size", 5)),
                Math.max(1, config.getInt("puddles.growth.nearby-puddles-to-merge", 3)),
                Math.max(1, config.getInt("puddles.growth.merge-search-radius", 4)),
                Math.max(1, config.getInt("puddles.growth.max-depth", 3)),
                Math.max(0L, Math.round(config.getDouble("puddles.drying.start-after-rain-seconds", 5.0D) * 1000.0D)),
                chance(config.getDouble("puddles.growth.seed-chance", 0.16D)),
                chance(config.getDouble("puddles.growth.expand-chance", 0.08D)),
                chance(config.getDouble("puddles.growth.single-puddle-expand-chance", 0.025D)),
                Math.max(1.0D, finite(config.getDouble("puddles.growth.thunderstorm-multiplier", 2.0D), 2.0D)),
                chance(config.getDouble("puddles.drying.shrink-chance", 0.18D)),
                config.getBoolean("puddles.render.restore-on-disable", true),
                Math.max(10L, config.getLong(
                        "puddles.render.reassert-interval-ticks", 40L)) * 50L,
                Math.max(1, config.getInt(
                        "puddles.retention-radius-multiplier", 3)),
                Math.max(0, config.getInt("puddles.required-player-distance-chunks", 6)),
                Math.max(0, config.getInt("puddles.debug.recent-events", 80)),
                testing,
                Math.max(5L, config.getLong("puddles.testing.step-ticks", 10L)),
                Math.max(1, config.getInt("puddles.testing.seed-attempts-per-cycle", 10)),
                Math.max(1, config.getInt("puddles.testing.max-expansions-per-cycle", 4)),
                normalizeWorldNames(config.getStringList("enabled-worlds")),
                normalizeWorldNames(config.getStringList("disabled-worlds"))
        );
    }

    boolean enabled() {
        return enabled;
    }

    boolean requireRainCapableBiome() {
        return requireRainCapableBiome;
    }

    boolean requireSkyExposure() {
        return requireSkyExposure;
    }

    boolean allowSnowfall() {
        return allowSnowfall;
    }

    long stepTicks() {
        return testingEnabled ? testingStepTicks : stepTicks;
    }

    int radius() {
        return radius;
    }

    int maxPuddlesPerWorld() {
        return maxPuddlesPerWorld;
    }

    int seedAttemptsPerCycle() {
        return testingEnabled ? testingSeedAttemptsPerCycle : seedAttemptsPerCycle;
    }

    int maxExpansionsPerCycle() {
        return testingEnabled ? testingMaxExpansionsPerCycle : maxExpansionsPerCycle;
    }

    int maxPuddleSize() {
        return maxPuddleSize;
    }

    int nearbyPuddlesToMerge() {
        return nearbyPuddlesToMerge;
    }

    int mergeSearchRadius() {
        return mergeSearchRadius;
    }

    int maxDepth() {
        return maxDepth;
    }

    long dryDelayMillis() {
        return dryDelayMillis;
    }

    double seedChance() {
        return seedChance;
    }

    double expandChance() {
        return expandChance;
    }

    double singlePuddleExpandChance() {
        return singlePuddleExpandChance;
    }

    double thunderstormMultiplier() {
        return thunderstormMultiplier;
    }

    double shrinkChance() {
        return shrinkChance;
    }

    boolean restoreOnDisable() {
        return restoreOnDisable;
    }

    long renderReassertMillis() {
        return renderReassertMillis;
    }

    int retentionRadius() {
        return radius * retentionRadiusMultiplier;
    }

    int requiredPlayerDistanceChunks() {
        return requiredPlayerDistanceChunks;
    }

    int debugRecentEvents() {
        return debugRecentEvents;
    }

    boolean isWorldAllowed(World world) {
        String worldName = world.getName().toLowerCase(Locale.ROOT);
        if (disabledWorlds.contains(worldName)) {
            return false;
        }
        return enabledWorlds.isEmpty() || enabledWorlds.contains(worldName);
    }

    String summary() {
        return "enabled=" + enabled
                + ", rain-biome=" + requireRainCapableBiome
                + ", sky-exposure=" + requireSkyExposure
                + ", snowfall=" + allowSnowfall
                + ", step=" + stepTicks()
                + ", radius=" + radius
                + ", max-world=" + maxPuddlesPerWorld
                + ", seeds=" + seedAttemptsPerCycle()
                + ", expansions=" + maxExpansionsPerCycle()
                + ", size=" + maxPuddleSize
                + ", depth=" + maxDepth
                + ", retention-radius=" + retentionRadius()
                + ", reassert-ms=" + renderReassertMillis
                + ", dry-delay-ms=" + dryDelayMillis
                + ", debug-events=" + debugRecentEvents;
    }

    private static double chance(double value) {
        return Math.max(0.0D, Math.min(1.0D, finite(value, 0.0D)));
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static Set<String> normalizeWorldNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                normalized.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(normalized);
    }
}