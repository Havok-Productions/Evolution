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
    private final boolean testingEnabled;
    private final long testingSpreadStepTicks;
    private final int testingBlocksPerStep;
    private final boolean branchingEnabled;
    private final int branchChancePercent;
    private final int branchRadius;
    private final int maxFrontierSize;
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
            boolean testingEnabled,
            long testingSpreadStepTicks,
            int testingBlocksPerStep,
            boolean branchingEnabled,
            int branchChancePercent,
            int branchRadius,
            int maxFrontierSize,
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
        this.testingEnabled = testingEnabled;
        this.testingSpreadStepTicks = testingSpreadStepTicks;
        this.testingBlocksPerStep = testingBlocksPerStep;
        this.branchingEnabled = branchingEnabled;
        this.branchChancePercent = branchChancePercent;
        this.branchRadius = branchRadius;
        this.maxFrontierSize = maxFrontierSize;
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
                config.getBoolean("nether-corruption.testing.enabled", true),
                Math.max(1L, config.getLong("nether-corruption.testing.spread-step-ticks", 40L)),
                Math.max(1, config.getInt("nether-corruption.testing.blocks-per-step", 4)),
                config.getBoolean("nether-corruption.branching.enabled", true),
                Math.max(0, Math.min(100, config.getInt("nether-corruption.branching.branch-chance-percent", 85))),
                Math.max(1, config.getInt("nether-corruption.branching.branch-radius", 4)),
                Math.max(16, config.getInt("nether-corruption.branching.max-frontier-size", 512)),
                Math.max(0, config.getInt("nether-corruption.debug.recent-events", 40)),
                Math.max(4, config.getInt("nether-corruption.debug.map-radius", 24))
        );
    }

    boolean enabled() {
        return enabled;
    }

    long spreadStepTicks() {
        return testingEnabled ? testingSpreadStepTicks : spreadStepTicks;
    }

    int blocksPerStep() {
        return testingEnabled ? testingBlocksPerStep : blocksPerStep;
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

    boolean branchingEnabled() {
        return branchingEnabled;
    }

    int branchChancePercent() {
        return branchChancePercent;
    }

    int branchRadius() {
        return branchRadius;
    }

    int maxFrontierSize() {
        return maxFrontierSize;
    }

    String summary() {
        return "enabled=" + enabled
                + ", spread-step=" + spreadStepTicks
                + ", blocks-per-step=" + blocksPerStep
                + ", effective-spread-step=" + spreadStepTicks()
                + ", effective-blocks-per-step=" + blocksPerStep()
                + ", testing=" + testingEnabled
                + ", radius=" + maxRadius
                + ", vertical-radius=" + verticalRadius
                + ", attempts=" + attemptsPerStep
                + ", branching=" + branchingEnabled
                + ", branch-chance=" + branchChancePercent
                + ", branch-radius=" + branchRadius
                + ", frontier-max=" + maxFrontierSize
                + ", player-distance-chunks=" + requiredPlayerDistanceChunks
                + ", portal-scan-radius=" + playerPortalScanRadius
                + ", debug-events=" + debugRecentEvents
                + ", debug-map-radius=" + debugMapRadius;
    }
}
