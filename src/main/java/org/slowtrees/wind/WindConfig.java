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
            int stormDriftRadius
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
    }

    static WindConfig load(SlowTreesPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new WindConfig(
                config.getBoolean("wind.enabled", true),
                Math.max(5L, config.getLong("wind.gust-tick-interval", 20L)),
                Math.max(100L, config.getLong("wind.pattern-change-ticks", 6000L)),
                Math.max(20L, config.getLong("wind.leaf-litter.placement-step-ticks", 1200L)),
                Math.max(0, config.getInt("wind.required-player-distance-chunks", 6)),
                Math.max(0, config.getInt("wind.leaf-litter.max-per-chunk", 8)),
                Math.max(4, config.getInt("wind.tree-search-radius", 10)),
                Math.max(1, config.getInt("wind.leaf-litter.placement-attempts", 8)),
                Math.max(1, config.getInt("wind.weather.clear-drift-radius", 10)),
                Math.max(1, config.getInt("wind.weather.rain-drift-radius", 4)),
                Math.max(1, config.getInt("wind.weather.storm-drift-radius", 16))
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
}
