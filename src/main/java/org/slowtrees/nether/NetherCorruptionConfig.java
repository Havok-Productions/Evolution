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

    private NetherCorruptionConfig(
            boolean enabled,
            long spreadStepTicks,
            int blocksPerStep,
            int maxRadius,
            int verticalRadius,
            int attemptsPerStep,
            int requiredPlayerDistanceChunks
    ) {
        this.enabled = enabled;
        this.spreadStepTicks = spreadStepTicks;
        this.blocksPerStep = blocksPerStep;
        this.maxRadius = maxRadius;
        this.verticalRadius = verticalRadius;
        this.attemptsPerStep = attemptsPerStep;
        this.requiredPlayerDistanceChunks = requiredPlayerDistanceChunks;
    }

    static NetherCorruptionConfig load(SlowTreesPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new NetherCorruptionConfig(
                config.getBoolean("nether-corruption.enabled", true),
                Math.max(1L, config.getLong("nether-corruption.spread-step-ticks", 1200L)),
                Math.max(1, config.getInt("nether-corruption.blocks-per-step", 1)),
                Math.max(1, config.getInt("nether-corruption.max-radius", 24)),
                Math.max(1, config.getInt("nether-corruption.vertical-radius", 8)),
                Math.max(1, config.getInt("nether-corruption.attempts-per-step", 24)),
                Math.max(0, config.getInt("nether-corruption.required-player-distance-chunks", 8))
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
}
