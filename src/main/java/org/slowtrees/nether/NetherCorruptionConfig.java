package org.slowtrees.nether;

import org.bukkit.configuration.file.FileConfiguration;
import org.slowtrees.core.SlowTreesPlugin;

final class NetherCorruptionConfig {
    private final boolean enabled;
    private final long spreadStepTicks;
    private final int blocksPerStep;
    private final int maxRadius;
    private final int verticalRadius;
    private final int attemptsPerStep;
    private final int requiredPlayerDistanceChunks;
    private final int playerPortalScanRadius;
    private final int debugRecentEvents;
    private final int debugMapRadius;

    private NetherCorruptionConfig(
            boolean enabled,
            long spreadStepTicks,
            int blocksPerStep,
            int maxRadius,
            int verticalRadius,
            int attemptsPerStep,
            int requiredPlayerDistanceChunks,
            int playerPortalScanRadius,
            int debugRecentEvents,
            int debugMapRadius
    ) {
        this.enabled = enabled;
        this.spreadStepTicks = spreadStepTicks;
        this.blocksPerStep = blocksPerStep;
        this.maxRadius = maxRadius;
        this.verticalRadius = verticalRadius;
        this.attemptsPerStep = attemptsPerStep;
        this.requiredPlayerDistanceChunks = requiredPlayerDistanceChunks;
        this.playerPortalScanRadius = playerPortalScanRadius;
        this.debugRecentEvents = debugRecentEvents;
        this.debugMapRadius = debugMapRadius;
    }

    static NetherCorruptionConfig load(SlowTreesPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new NetherCorruptionConfig(
                config.getBoolean("nether-corruption.enabled", true),
                Math.max(1L, config.getLong("nether-corruption.spread-step-ticks", 1200L)),
                Math.max(1, config.getInt("nether-corruption.blocks-per-step", 1)),
                Math.max(1, config.getInt("nether-corruption.max-radius", 24)),
                Math.max(1, config.getInt("nether-corruption.vertical-radius", 8)),
                Math.max(1, config.getInt("nether-corruption.attempts-per-step", 96)),
                Math.max(0, config.getInt("nether-corruption.required-player-distance-chunks", 8)),
                Math.max(0, config.getInt("nether-corruption.player-portal-scan-radius", 32)),
                Math.max(0, config.getInt("nether-corruption.debug.recent-events", 40)),
                Math.max(4, config.getInt("nether-corruption.debug.map-radius", 24))
        );
    }

    boolean enabled() {
        return enabled;
    }

    long spreadStepTicks() {
        return spreadStepTicks;
    }

    int blocksPerStep() {
        return blocksPerStep;
    }

    int maxRadius() {
        return maxRadius;
    }

    int verticalRadius() {
        return verticalRadius;
    }

    int attemptsPerStep() {
        return attemptsPerStep;
    }

    int requiredPlayerDistanceChunks() {
        return requiredPlayerDistanceChunks;
    }

    int playerPortalScanRadius() {
        return playerPortalScanRadius;
    }

    int debugRecentEvents() {
        return debugRecentEvents;
    }

    int debugMapRadius() {
        return debugMapRadius;
    }
}
