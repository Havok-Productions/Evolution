package org.slowtrees.wind;

import org.bukkit.configuration.file.FileConfiguration;
import org.slowtrees.core.SlowTreesPlugin;

final class WindConfig {
    private final boolean enabled;
    private final long gustTickInterval;
    private final long patternChangeTicks;
    private final long leafLitterPlacementTicks;
    private final int requiredPlayerDistanceChunks;
    private final int maxLeafLitterPerChunk;
    private final int treeSearchRadius;
    private final int placementAttempts;
    private final int clearDriftRadius;
    private final int rainDriftRadius;
    private final int stormDriftRadius;
    private final int clearParticleCount;
    private final int rainParticleCount;
    private final int stormParticleCount;
    private final int debugRecentEvents;

    private WindConfig(
            boolean enabled,
            long gustTickInterval,
            long patternChangeTicks,
            long leafLitterPlacementTicks,
            int requiredPlayerDistanceChunks,
            int maxLeafLitterPerChunk,
            int treeSearchRadius,
            int placementAttempts,
            int clearDriftRadius,
            int rainDriftRadius,
            int stormDriftRadius,
            int clearParticleCount,
            int rainParticleCount,
            int stormParticleCount,
            int debugRecentEvents
    ) {
        this.enabled = enabled;
        this.gustTickInterval = gustTickInterval;
        this.patternChangeTicks = patternChangeTicks;
        this.leafLitterPlacementTicks = leafLitterPlacementTicks;
        this.requiredPlayerDistanceChunks = requiredPlayerDistanceChunks;
        this.maxLeafLitterPerChunk = maxLeafLitterPerChunk;
        this.treeSearchRadius = treeSearchRadius;
        this.placementAttempts = placementAttempts;
        this.clearDriftRadius = clearDriftRadius;
        this.rainDriftRadius = rainDriftRadius;
        this.stormDriftRadius = stormDriftRadius;
        this.clearParticleCount = clearParticleCount;
        this.rainParticleCount = rainParticleCount;
        this.stormParticleCount = stormParticleCount;
        this.debugRecentEvents = debugRecentEvents;
    }

    static WindConfig load(SlowTreesPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new WindConfig(
                config.getBoolean("wind.enabled", true),
                Math.max(5L, config.getLong("wind.gust-tick-interval", 8L)),
                Math.max(100L, config.getLong("wind.pattern-change-ticks", 6000L)),
                Math.max(20L, config.getLong("wind.leaf-litter.placement-step-ticks", 80L)),
                Math.max(0, config.getInt("wind.required-player-distance-chunks", 6)),
                Math.max(0, config.getInt("wind.leaf-litter.max-per-chunk", 32)),
                Math.max(4, config.getInt("wind.tree-search-radius", 32)),
                Math.max(1, config.getInt("wind.leaf-litter.placement-attempts", 48)),
                Math.max(1, config.getInt("wind.weather.clear-drift-radius", 10)),
                Math.max(1, config.getInt("wind.weather.rain-drift-radius", 4)),
                Math.max(1, config.getInt("wind.weather.storm-drift-radius", 16)),
                Math.max(1, config.getInt("wind.particles.clear-count", 16)),
                Math.max(1, config.getInt("wind.particles.rain-count", 8)),
                Math.max(1, config.getInt("wind.particles.storm-count", 24)),
                Math.max(0, config.getInt("wind.debug.recent-events", 40))
        );
    }

    boolean enabled() {
        return enabled;
    }

    long gustTickInterval() {
        return gustTickInterval;
    }

    long patternChangeTicks() {
        return patternChangeTicks;
    }

    long leafLitterPlacementTicks() {
        return leafLitterPlacementTicks;
    }

    int requiredPlayerDistanceChunks() {
        return requiredPlayerDistanceChunks;
    }

    int maxLeafLitterPerChunk() {
        return maxLeafLitterPerChunk;
    }

    int treeSearchRadius() {
        return treeSearchRadius;
    }

    int placementAttempts() {
        return placementAttempts;
    }

    int driftRadius(boolean storm, boolean rain) {
        if (storm) {
            return stormDriftRadius;
        }
        if (rain) {
            return rainDriftRadius;
        }
        return clearDriftRadius;
    }

    int particleCount(boolean storm, boolean rain) {
        if (storm) {
            return stormParticleCount;
        }
        if (rain) {
            return rainParticleCount;
        }
        return clearParticleCount;
    }

    int debugRecentEvents() {
        return debugRecentEvents;
    }

    String summary() {
        return "enabled=" + enabled
                + ", gust-interval=" + gustTickInterval
                + ", pattern-change=" + patternChangeTicks
                + ", tree-radius=" + treeSearchRadius
                + ", player-distance-chunks=" + requiredPlayerDistanceChunks
                + ", litter-step=" + leafLitterPlacementTicks
                + ", litter-attempts=" + placementAttempts
                + ", litter-max-per-chunk=" + maxLeafLitterPerChunk
                + ", particles=" + clearParticleCount + "/" + rainParticleCount + "/" + stormParticleCount
                + ", debug-events=" + debugRecentEvents;
    }
}
