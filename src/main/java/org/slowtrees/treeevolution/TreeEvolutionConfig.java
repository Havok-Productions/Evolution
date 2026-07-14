package org.slowtrees.treeevolution;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.slowtrees.core.RuntimeProfile;
import org.slowtrees.core.SlowTreesPlugin;

final class TreeEvolutionConfig {
    private final boolean enabled;
    private final long stepTicks;
    private final int searchRadius;
    private final int attemptsPerStep;
    private final int blocksPerStep;
    private final int requiredPlayerDistanceChunks;
    private final int ownedChunkRadius;
    private final long dnaSaveIntervalMillis;
    private final boolean dnaCleanupEnabled;
    private final long dnaCleanupIntervalMillis;
    private final long dnaCleanupMissingBaseMillis;
    private final long candidateCacheMillis;
    private final long damageStallTicks;
    private final boolean rootsEnabled;
    private final boolean testingEnabled;
    private final long testingStepTicks;
    private final long testingMinDelayTicks;
    private final int testingAttemptsPerStep;
    private final int testingBlocksPerStep;
    private final long testingDamageStallTicks;
    private final boolean testingStageAccelerationEnabled;
    private final int testingSmallToMediumAge;
    private final int testingMediumToMatureAge;
    private final int testingMatureToAncientAge;
    private final boolean testingAllowAnyRarityAncient;
    private final double testingStageBurstDelayMultiplier;
    private final double testingBreathingSkipChance;
    private final double damageSlowdownPerHit;
    private final double maxDamageSlowdown;
    private final boolean debugEnabled;
    private final int debugRecentEvents;
    private final int debugMapRadius;
    private final boolean debug3dEnabled;
    private final int debug3dRecentStageEvents;
    private final boolean debugReplayEnabled;
    private final int debugReplaySampleLimit;
    private final boolean autoScanOnStartup;
    private final boolean worldHealthModeEnabled;
    private final double worldHealthGrowthSpeedMultiplier;
    private final Map<TreeSpecies, TreeGrowthProfile> profiles;
    private final Set<String> enabledWorlds;
    private final Set<String> disabledWorlds;

    private TreeEvolutionConfig(
            boolean enabled,
            long stepTicks,
            int searchRadius,
            int attemptsPerStep,
            int blocksPerStep,
            int requiredPlayerDistanceChunks,
            int ownedChunkRadius,
            long dnaSaveIntervalMillis,
            boolean dnaCleanupEnabled,
            long dnaCleanupIntervalMillis,
            long dnaCleanupMissingBaseMillis,
            long candidateCacheMillis,
            long damageStallTicks,
            boolean rootsEnabled,
            boolean testingEnabled,
            long testingStepTicks,
            long testingMinDelayTicks,
            int testingAttemptsPerStep,
            int testingBlocksPerStep,
            long testingDamageStallTicks,
            boolean testingStageAccelerationEnabled,
            int testingSmallToMediumAge,
            int testingMediumToMatureAge,
            int testingMatureToAncientAge,
            boolean testingAllowAnyRarityAncient,
            double testingStageBurstDelayMultiplier,
            double testingBreathingSkipChance,
            double damageSlowdownPerHit,
            double maxDamageSlowdown,
            boolean debugEnabled,
            int debugRecentEvents,
            int debugMapRadius,
            boolean debug3dEnabled,
            int debug3dRecentStageEvents,
            boolean debugReplayEnabled,
            int debugReplaySampleLimit,
            boolean autoScanOnStartup,
            boolean worldHealthModeEnabled,
            double worldHealthGrowthSpeedMultiplier,
            Map<TreeSpecies, TreeGrowthProfile> profiles,
            Set<String> enabledWorlds,
            Set<String> disabledWorlds
    ) {
        this.enabled = enabled;
        this.stepTicks = stepTicks;
        this.searchRadius = searchRadius;
        this.attemptsPerStep = attemptsPerStep;
        this.blocksPerStep = blocksPerStep;
        this.requiredPlayerDistanceChunks = requiredPlayerDistanceChunks;
        this.ownedChunkRadius = ownedChunkRadius;
        this.dnaSaveIntervalMillis = dnaSaveIntervalMillis;
        this.dnaCleanupEnabled = dnaCleanupEnabled;
        this.dnaCleanupIntervalMillis = dnaCleanupIntervalMillis;
        this.dnaCleanupMissingBaseMillis = dnaCleanupMissingBaseMillis;
        this.candidateCacheMillis = candidateCacheMillis;
        this.damageStallTicks = damageStallTicks;
        this.rootsEnabled = rootsEnabled;
        this.testingEnabled = testingEnabled;
        this.testingStepTicks = testingStepTicks;
        this.testingMinDelayTicks = testingMinDelayTicks;
        this.testingAttemptsPerStep = testingAttemptsPerStep;
        this.testingBlocksPerStep = testingBlocksPerStep;
        this.testingDamageStallTicks = testingDamageStallTicks;
        this.testingStageAccelerationEnabled = testingStageAccelerationEnabled;
        this.testingSmallToMediumAge = testingSmallToMediumAge;
        this.testingMediumToMatureAge = testingMediumToMatureAge;
        this.testingMatureToAncientAge = testingMatureToAncientAge;
        this.testingAllowAnyRarityAncient = testingAllowAnyRarityAncient;
        this.testingStageBurstDelayMultiplier = testingStageBurstDelayMultiplier;
        this.testingBreathingSkipChance = testingBreathingSkipChance;
        this.damageSlowdownPerHit = damageSlowdownPerHit;
        this.maxDamageSlowdown = maxDamageSlowdown;
        this.debugEnabled = debugEnabled;
        this.debugRecentEvents = debugRecentEvents;
        this.debugMapRadius = debugMapRadius;
        this.debug3dEnabled = debug3dEnabled;
        this.debug3dRecentStageEvents = debug3dRecentStageEvents;
        this.debugReplayEnabled = debugReplayEnabled;
        this.debugReplaySampleLimit = debugReplaySampleLimit;
        this.autoScanOnStartup = autoScanOnStartup;
        this.worldHealthModeEnabled = worldHealthModeEnabled;
        this.worldHealthGrowthSpeedMultiplier = worldHealthGrowthSpeedMultiplier;
        this.profiles = profiles;
        this.enabledWorlds = enabledWorlds;
        this.disabledWorlds = disabledWorlds;
    }

    static TreeEvolutionConfig load(SlowTreesPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        int configuredSearchRadius = Math.max(0, config.getInt("tree-evolution.search-radius", 0));
        int effectiveSearchRadius = configuredSearchRadius == 0
                ? Math.max(16, plugin.getServer().getViewDistance() * 16)
                : configuredSearchRadius;
        boolean testing = RuntimeProfile.testingEnabled(config) && config.getBoolean("tree-evolution.testing.enabled", true);
        return new TreeEvolutionConfig(
                config.getBoolean("tree-evolution.enabled", true),
                Math.max(20L, config.getLong("tree-evolution.step-ticks", 900L)),
                effectiveSearchRadius,
                Math.max(1, config.getInt("tree-evolution.attempts-per-step", 48)),
                Math.max(1, config.getInt("tree-evolution.blocks-per-step", 1)),
                Math.max(0, config.getInt("tree-evolution.required-player-distance-chunks", 0)),
                Math.max(0, config.getInt("tree-evolution.owned-chunk-radius", 1)),
                Math.max(1000L, config.getLong("tree-evolution.dna-save-interval-millis", 60000L)),
                config.getBoolean("tree-evolution.dna-cleanup.enabled", true),
                Math.max(10000L, config.getLong("tree-evolution.dna-cleanup.interval-millis", 60000L)),
                Math.max(60000L, config.getLong("tree-evolution.dna-cleanup.missing-base-max-age-millis", 600000L)),
                Math.max(250L, config.getLong("tree-evolution.candidate-cache-millis", 2500L)),
                Math.max(20L, config.getLong("tree-evolution.damage-stall-ticks", 600L)),
                config.getBoolean("tree-evolution.roots.enabled", false),
                testing,
                Math.max(1L, config.getLong("tree-evolution.testing.step-ticks", 5L)),
                Math.max(1L, config.getLong("tree-evolution.testing.min-delay-ticks", 5L)),
                Math.max(1, config.getInt("tree-evolution.testing.attempts-per-step", 96)),
                Math.max(1, config.getInt("tree-evolution.testing.blocks-per-step", 96)),
                Math.max(1L, config.getLong("tree-evolution.testing.damage-stall-ticks", 20L)),
                config.getBoolean("tree-evolution.testing.stage-acceleration.enabled", true),
                Math.max(1, config.getInt("tree-evolution.testing.stage-acceleration.small-to-medium-age", 4)),
                Math.max(2, config.getInt("tree-evolution.testing.stage-acceleration.medium-to-mature-age", 12)),
                Math.max(3, config.getInt("tree-evolution.testing.stage-acceleration.mature-to-ancient-age", 36)),
                config.getBoolean("tree-evolution.testing.stage-acceleration.allow-any-rarity-ancient", true),
                clamp(config.getDouble("tree-evolution.testing.stage-acceleration.stage-burst-delay-multiplier", 0.04D), 0.01D, 1.0D),
                clamp(config.getDouble("tree-evolution.testing.stage-acceleration.breathing-skip-percent", 0.0D) / 100.0D, 0.0D, 0.90D),
                Math.max(0.0D, config.getDouble("tree-evolution.dynamic-slowdown.damage-delay-per-hit", 0.12D)),
                Math.max(1.0D, config.getDouble("tree-evolution.dynamic-slowdown.max-delay-multiplier", 3.0D)),
                config.getBoolean("tree-evolution.debug.enabled", true),
                Math.max(0, config.getInt("tree-evolution.debug.recent-events", 200)),
                Math.max(3, config.getInt("tree-evolution.debug.map-radius", 10)),
                config.getBoolean("tree-evolution.debug.3d.enabled", true),
                Math.max(0, config.getInt("tree-evolution.debug.3d.recent-stage-events", 80)),
                config.getBoolean("tree-evolution.debug.replay.enabled", true),
                Math.max(32, config.getInt("tree-evolution.debug.replay.sample-limit", 512)),
                config.getBoolean("tree-evolution.debug.auto-scan-on-startup", true),
                config.getBoolean("world-health-mode.enabled", true),
                sanitizeGrowthSpeedMultiplier(config.getDouble("world-health-mode.growth-speed-multiplier", 0.15D)),
                TreeGrowthProfile.loadProfiles(config.getConfigurationSection("tree-evolution.profiles")),
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

    long delayTicksFor(TreeDna dna, TreeGrowthProfile profile, org.bukkit.block.Biome biome) {
        double biomeFactor = profile.biomeGrowthFactor(biome);
        double slowdown = Math.min(maxDamageSlowdown, 1.0D + (dna.damageCount() * damageSlowdownPerHit));
        long minimum = testingEnabled ? testingMinDelayTicks : 20L;
        return Math.max(minimum, Math.round((stepTicks() * slowdown) / biomeFactor));
    }

    long damageStallMillis() {
        return (testingEnabled ? testingDamageStallTicks : damageStallTicks) * 50L;
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

    int ownedChunkRadius() {
        return ownedChunkRadius;
    }

    long dnaSaveIntervalMillis() {
        return dnaSaveIntervalMillis;
    }

    boolean dnaCleanupEnabled() {
        return dnaCleanupEnabled;
    }

    long dnaCleanupIntervalMillis() {
        return dnaCleanupIntervalMillis;
    }

    long dnaCleanupMissingBaseMillis() {
        return dnaCleanupMissingBaseMillis;
    }

    long candidateCacheMillis() {
        return candidateCacheMillis;
    }

    boolean rootsEnabled() {
        return rootsEnabled;
    }

    boolean debugEnabled() {
        return debugEnabled;
    }

    int debugRecentEvents() {
        return debugRecentEvents;
    }

    int debugMapRadius() {
        return debugMapRadius;
    }

    boolean debug3dEnabled() {
        return debugEnabled && debug3dEnabled;
    }

    int debug3dRecentStageEvents() {
        return debug3dRecentStageEvents;
    }

    boolean debugReplayEnabled() {
        return debugEnabled && debugReplayEnabled;
    }

    int debugReplaySampleLimit() {
        return debugReplaySampleLimit;
    }

    boolean autoScanOnStartup() {
        return autoScanOnStartup;
    }

    boolean testingEnabled() {
        return testingEnabled;
    }

    boolean testingStageAccelerationEnabled() {
        return testingEnabled && testingStageAccelerationEnabled;
    }

    int smallToMediumAge() {
        return testingStageAccelerationEnabled() ? testingSmallToMediumAge : 6;
    }

    int mediumToMatureAge() {
        return testingStageAccelerationEnabled() ? testingMediumToMatureAge : 18;
    }

    int matureToAncientAge() {
        return testingStageAccelerationEnabled() ? testingMatureToAncientAge : 220;
    }

    boolean allowAnyRarityAncient() {
        return testingStageAccelerationEnabled() && testingAllowAnyRarityAncient;
    }

    double stageBurstDelayMultiplier() {
        return testingStageAccelerationEnabled() ? testingStageBurstDelayMultiplier : 0.28D;
    }

    double breathingSkipChance() {
        return testingEnabled ? testingBreathingSkipChance : 0.10D;
    }

    TreeGrowthProfile profile(TreeSpecies species) {
        return profiles.get(species);
    }

    boolean isWorldAllowed(World world) {
        String worldName = world.getName().toLowerCase(Locale.ROOT);
        if (disabledWorlds.contains(worldName)) {
            return false;
        }
        return enabledWorlds.isEmpty() || enabledWorlds.contains(worldName);
    }

    boolean isReplaceable(Material material) {
        return material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || material == Material.SHORT_GRASS
                || material == Material.TALL_GRASS
                || material == Material.FERN
                || material == Material.LARGE_FERN
                || material == Material.VINE
                || material == Material.LEAF_LITTER
                || material == Material.MOSS_CARPET
                || material == Material.SNOW
                || material == Material.BROWN_MUSHROOM
                || material == Material.RED_MUSHROOM
                || material == Material.PINK_PETALS
                || material.name().endsWith("_LEAVES");
    }

    String summary() {
        return "enabled=" + enabled
                + ", step=" + stepTicks()
                + ", radius=" + searchRadius
                + ", attempts=" + attemptsPerStep()
                + ", blocks=" + blocksPerStep()
                + ", player-distance-chunks=" + requiredPlayerDistanceChunks
                + ", dna-save-interval-ms=" + dnaSaveIntervalMillis
                + ", dna-cleanup=" + dnaCleanupEnabled
                + ", candidate-cache-ms=" + candidateCacheMillis
                + ", roots=" + rootsEnabled
                + ", debug=" + debugEnabled
                + ", 3d-debug=" + debug3dEnabled()
                + ", replay-debug=" + debugReplayEnabled()
                + ", stage-accel=" + testingStageAccelerationEnabled()
                + ", stage-ages=" + smallToMediumAge() + "/" + mediumToMatureAge() + "/" + matureToAncientAge()
                + ", auto-scan=" + autoScanOnStartup
                + ", testing=" + testingEnabled;
    }

    private static double sanitizeGrowthSpeedMultiplier(double value) {
        if (Double.isFinite(value) && value > 0.0D) {
            return value;
        }
        return 1.0D;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static Set<String> normalizeWorldNames(Iterable<String> names) {
        Set<String> normalized = new HashSet<>();
        for (String name : names) {
            normalized.add(name.toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(normalized);
    }
}
