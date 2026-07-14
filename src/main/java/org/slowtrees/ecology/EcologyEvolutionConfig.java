package org.slowtrees.ecology;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.slowtrees.core.RuntimeProfile;
import org.slowtrees.core.SlowTreesPlugin;

final class EcologyEvolutionConfig {
    private final boolean enabled;
    private final long stepTicks;
    private final int searchRadius;
    private final int attemptsPerStep;
    private final int blocksPerStep;
    private final int requiredPlayerDistanceChunks;
    private final int heightChancePercent;
    private final int branchChancePercent;
    private final int canopyChancePercent;
    private final int forestFloorChancePercent;
    private final int groundPaletteChancePercent;
    private final int plantPaletteChancePercent;
    private final int rareFeatureChancePercent;
    private final int maxDetailsPerArea;
    private final int maxTreeHeightBonus;
    private final int maxBranchesNearTree;
    private final int debugMapRadius;
    private final int debugRecentEvents;
    private final boolean testingEnabled;
    private final boolean worldHealthModeEnabled;
    private final double worldHealthGrowthSpeedMultiplier;
    private final Set<String> enabledWorlds;
    private final Set<String> disabledWorlds;

    private EcologyEvolutionConfig(
            boolean enabled,
            long stepTicks,
            int searchRadius,
            int attemptsPerStep,
            int blocksPerStep,
            int requiredPlayerDistanceChunks,
            int heightChancePercent,
            int branchChancePercent,
            int canopyChancePercent,
            int forestFloorChancePercent,
            int groundPaletteChancePercent,
            int plantPaletteChancePercent,
            int rareFeatureChancePercent,
            int maxDetailsPerArea,
            int maxTreeHeightBonus,
            int maxBranchesNearTree,
            int debugMapRadius,
            int debugRecentEvents,
            boolean testingEnabled,
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
        this.heightChancePercent = heightChancePercent;
        this.branchChancePercent = branchChancePercent;
        this.canopyChancePercent = canopyChancePercent;
        this.forestFloorChancePercent = forestFloorChancePercent;
        this.groundPaletteChancePercent = groundPaletteChancePercent;
        this.plantPaletteChancePercent = plantPaletteChancePercent;
        this.rareFeatureChancePercent = rareFeatureChancePercent;
        this.maxDetailsPerArea = maxDetailsPerArea;
        this.maxTreeHeightBonus = maxTreeHeightBonus;
        this.maxBranchesNearTree = maxBranchesNearTree;
        this.debugMapRadius = debugMapRadius;
        this.debugRecentEvents = debugRecentEvents;
        this.testingEnabled = testingEnabled;
        this.worldHealthModeEnabled = worldHealthModeEnabled;
        this.worldHealthGrowthSpeedMultiplier = worldHealthGrowthSpeedMultiplier;
        this.enabledWorlds = enabledWorlds;
        this.disabledWorlds = disabledWorlds;
    }

    static EcologyEvolutionConfig load(SlowTreesPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        int configuredSearchRadius = Math.max(0, config.getInt("ecology-evolution.search-radius", 0));
        int effectiveSearchRadius = configuredSearchRadius == 0
                ? Math.max(16, plugin.getServer().getViewDistance() * 16)
                : configuredSearchRadius;
        boolean testing = RuntimeProfile.testingEnabled(config) && config.getBoolean("ecology-evolution.testing.enabled", true);
        return new EcologyEvolutionConfig(
                config.getBoolean("ecology-evolution.enabled", true),
                effectiveStepTicks(config, testing),
                effectiveSearchRadius,
                effectiveAttempts(config, testing),
                effectiveBlocks(config, testing),
                Math.max(0, config.getInt("ecology-evolution.required-player-distance-chunks", 6)),
                percent(config.getInt("ecology-evolution.height-chance-percent", 42)),
                percent(config.getInt("ecology-evolution.branch-chance-percent", 28)),
                percent(config.getInt("ecology-evolution.canopy-chance-percent", 50)),
                percent(config.getInt("ecology-evolution.forest-floor-chance-percent", 20)),
                percent(config.getInt("ecology-evolution.ground-palette-chance-percent", 35)),
                percent(config.getInt("ecology-evolution.plant-palette-chance-percent", 70)),
                percent(config.getInt("ecology-evolution.rare-feature-chance-percent", 5)),
                Math.max(0, config.getInt("ecology-evolution.max-details-per-area", 36)),
                Math.max(0, config.getInt("ecology-evolution.max-tree-height-bonus", 6)),
                Math.max(0, config.getInt("ecology-evolution.max-branches-near-tree", 6)),
                Math.max(3, config.getInt("ecology-evolution.debug.map-radius", 8)),
                Math.max(0, config.getInt("ecology-evolution.debug.recent-events", 120)),
                testing,
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
        if (testingEnabled || !worldHealthModeEnabled) {
            return stepTicks;
        }
        return Math.max(20L, Math.round(stepTicks / worldHealthGrowthSpeedMultiplier));
    }

    int searchRadius() {
        return searchRadius;
    }

    int attemptsPerStep() {
        return attemptsPerStep;
    }

    int blocksPerStep() {
        return blocksPerStep;
    }

    int requiredPlayerDistanceChunks() {
        return requiredPlayerDistanceChunks;
    }

    int heightChancePercent() {
        return heightChancePercent;
    }

    int branchChancePercent() {
        return branchChancePercent;
    }

    int canopyChancePercent() {
        return canopyChancePercent;
    }

    int forestFloorChancePercent() {
        return forestFloorChancePercent;
    }

    int groundPaletteChancePercent() {
        return groundPaletteChancePercent;
    }

    int plantPaletteChancePercent() {
        return plantPaletteChancePercent;
    }

    int rareFeatureChancePercent() {
        return rareFeatureChancePercent;
    }

    int maxDetailsPerArea() {
        return maxDetailsPerArea;
    }

    int maxTreeHeightBonus() {
        return maxTreeHeightBonus;
    }

    int maxBranchesNearTree() {
        return maxBranchesNearTree;
    }

    int debugMapRadius() {
        return debugMapRadius;
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
                + ", step=" + stepTicks()
                + ", radius=" + searchRadius
                + ", attempts=" + attemptsPerStep
                + ", blocks=" + blocksPerStep
                + ", height=" + heightChancePercent
                + ", branch=" + branchChancePercent
                + ", canopy=" + canopyChancePercent
                + ", floor=" + forestFloorChancePercent
                + ", ground=" + groundPaletteChancePercent
                + ", plant=" + plantPaletteChancePercent
                + ", rare=" + rareFeatureChancePercent
                + ", max-height-bonus=" + maxTreeHeightBonus;
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

    private static long effectiveStepTicks(FileConfiguration config, boolean testing) {
        if (testing) {
            return Math.max(5L, config.getLong("ecology-evolution.testing.step-ticks", 20L));
        }
        return Math.max(20L, config.getLong("ecology-evolution.step-ticks", 120L));
    }

    private static int effectiveAttempts(FileConfiguration config, boolean testing) {
        if (testing) {
            return Math.max(1, config.getInt("ecology-evolution.testing.attempts-per-step", 128));
        }
        return Math.max(1, config.getInt("ecology-evolution.attempts-per-step", 32));
    }

    private static int effectiveBlocks(FileConfiguration config, boolean testing) {
        if (testing) {
            return Math.max(1, config.getInt("ecology-evolution.testing.blocks-per-step", 8));
        }
        return Math.max(1, config.getInt("ecology-evolution.blocks-per-step", 1));
    }

    private static Set<String> normalizeWorldNames(Iterable<String> names) {
        Set<String> normalized = new HashSet<>();
        for (String name : names) {
            normalized.add(name.toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(normalized);
    }
}
