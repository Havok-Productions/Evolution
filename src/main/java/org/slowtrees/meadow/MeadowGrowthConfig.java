package org.slowtrees.meadow;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.slowtrees.core.RuntimeProfile;
import org.slowtrees.core.SlowTreesPlugin;

final class MeadowGrowthConfig {
    private final boolean enabled;
    private final long stepTicks;
    private final int searchRadius;
    private final int attemptsPerStep;
    private final int blocksPerStep;
    private final int requiredPlayerDistanceChunks;
    private final int grassSpreadChancePercent;
    private final int plantGrowChancePercent;
    private final int flowerChancePercent;
    private final int heightGrowthChancePercent;
    private final int maxPlantsPerArea;
    private final boolean replaceLeafLitter;
    private final boolean testingEnabled;
    private final long testingStepTicks;
    private final int testingAttemptsPerStep;
    private final int testingBlocksPerStep;
    private final boolean worldHealthModeEnabled;
    private final double worldHealthGrowthSpeedMultiplier;
    private final Set<String> enabledWorlds;
    private final Set<String> disabledWorlds;

    private MeadowGrowthConfig(
            boolean enabled,
            long stepTicks,
            int searchRadius,
            int attemptsPerStep,
            int blocksPerStep,
            int requiredPlayerDistanceChunks,
            int grassSpreadChancePercent,
            int plantGrowChancePercent,
            int flowerChancePercent,
            int heightGrowthChancePercent,
            int maxPlantsPerArea,
            boolean replaceLeafLitter,
            boolean testingEnabled,
            long testingStepTicks,
            int testingAttemptsPerStep,
            int testingBlocksPerStep,
            boolean worldHealthModeEnabled,
            double worldHealthGrowthSpeedMultiplier,
            Set<String> enabledWorlds,
            Set<String> disabledWorlds
    ) {
        this.enabled = enabled;
        this.stepTicks = stepTicks;
        this.searchRadius = searchRadius;
        this.attemptsPerStep = attemptsPerStep;
        this.blocksPerStep = blocksPerStep;
        this.requiredPlayerDistanceChunks = requiredPlayerDistanceChunks;
        this.grassSpreadChancePercent = grassSpreadChancePercent;
        this.plantGrowChancePercent = plantGrowChancePercent;
        this.flowerChancePercent = flowerChancePercent;
        this.heightGrowthChancePercent = heightGrowthChancePercent;
        this.maxPlantsPerArea = maxPlantsPerArea;
        this.replaceLeafLitter = replaceLeafLitter;
        this.testingEnabled = testingEnabled;
        this.testingStepTicks = testingStepTicks;
        this.testingAttemptsPerStep = testingAttemptsPerStep;
        this.testingBlocksPerStep = testingBlocksPerStep;
        this.worldHealthModeEnabled = worldHealthModeEnabled;
        this.worldHealthGrowthSpeedMultiplier = worldHealthGrowthSpeedMultiplier;
        this.enabledWorlds = enabledWorlds;
        this.disabledWorlds = disabledWorlds;
    }

    static MeadowGrowthConfig load(SlowTreesPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        int configuredSearchRadius = Math.max(0, config.getInt("meadow-growth.search-radius", 0));
        int effectiveSearchRadius = configuredSearchRadius == 0
                ? Math.max(16, plugin.getServer().getViewDistance() * 16)
                : configuredSearchRadius;
        boolean testing = RuntimeProfile.testingEnabled(config) && config.getBoolean("meadow-growth.testing.enabled", true);
        return new MeadowGrowthConfig(
                config.getBoolean("meadow-growth.enabled", true),
                Math.max(20L, config.getLong("meadow-growth.step-ticks", 60L)),
                effectiveSearchRadius,
                Math.max(1, config.getInt("meadow-growth.attempts-per-step", 32)),
                Math.max(1, config.getInt("meadow-growth.blocks-per-step", 2)),
                Math.max(0, config.getInt("meadow-growth.required-player-distance-chunks", 6)),
                percent(config.getInt("meadow-growth.grass-spread-chance-percent", 75)),
                percent(config.getInt("meadow-growth.plant-grow-chance-percent", 50)),
                percent(config.getInt("meadow-growth.flower-chance-percent", 16)),
                percent(config.getInt("meadow-growth.height-growth-chance-percent", 35)),
                Math.max(0, config.getInt("meadow-growth.max-plants-per-area", 48)),
                config.getBoolean("meadow-growth.replace-leaf-litter", true),
                testing,
                Math.max(5L, config.getLong("meadow-growth.testing.step-ticks", 10L)),
                Math.max(1, config.getInt("meadow-growth.testing.attempts-per-step", 128)),
                Math.max(1, config.getInt("meadow-growth.testing.blocks-per-step", 8)),
                config.getBoolean("world-health-mode.enabled", true),
                sanitizeGrowthSpeedMultiplier(config.getDouble("world-health-mode.growth-speed-multiplier", 0.15D)),
                normalizeWorldNames(config.getStringList("enabled-worlds")),
                normalizeWorldNames(config.getStringList("disabled-worlds"))
        );
    }

    boolean enabled() {
        return enabled;
    }

    long stepTicks() {
        if (testingEnabled) {
            return testingStepTicks;
        }
        if (!worldHealthModeEnabled) {
            return stepTicks;
        }
        return Math.max(20L, Math.round(stepTicks / worldHealthGrowthSpeedMultiplier));
    }

    int searchRadius() {
        return searchRadius;
    }

    int attemptsPerStep() {
        return testingEnabled ? testingAttemptsPerStep : attemptsPerStep;
    }

    int blocksPerStep() {
        return testingEnabled ? testingBlocksPerStep : blocksPerStep;
    }

    int requiredPlayerDistanceChunks() {
        return requiredPlayerDistanceChunks;
    }

    int grassSpreadChancePercent() {
        return grassSpreadChancePercent;
    }

    int plantGrowChancePercent() {
        return plantGrowChancePercent;
    }

    int flowerChancePercent() {
        return flowerChancePercent;
    }

    int heightGrowthChancePercent() {
        return heightGrowthChancePercent;
    }

    int maxPlantsPerArea() {
        return maxPlantsPerArea;
    }

    boolean replaceLeafLitter() {
        return replaceLeafLitter;
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
                + ", step=" + stepTicks()
                + ", radius=" + searchRadius
                + ", attempts=" + attemptsPerStep
                + ", blocks=" + blocksPerStep
                + ", grass=" + grassSpreadChancePercent
                + ", plants=" + plantGrowChancePercent
                + ", flowers=" + flowerChancePercent
                + ", height=" + heightGrowthChancePercent
                + ", testing=" + testingEnabled;
    }

    private static int percent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private static double sanitizeGrowthSpeedMultiplier(double value) {
        if (Double.isFinite(value) && value > 0.0D) {
            return value;
        }
        return 1.0D;
    }

    private static Set<String> normalizeWorldNames(Iterable<String> names) {
        Set<String> normalized = new HashSet<>();
        for (String name : names) {
            normalized.add(name.toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(normalized);
    }
}
