package org.evolution.features.treeevolution;

import java.nio.file.Path;
import java.util.Locale;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * ## Runtime values used by the production-aware smoke scheduler.
 */
record TreeProductionSmokeSettings(
        String profile,
        boolean testing,
        long stepTicks,
        long minimumDelayTicks,
        int attemptsPerStep,
        int blocksPerStep,
        int searchRadius,
        int requiredPlayerDistanceChunks,
        int ownedChunkRadius,
        TreeMaturityStage maximumStage,
        double worldHealthMultiplier,
        Path source
) {
    static TreeProductionSmokeSettings load(Path path) {
        YamlConfiguration yaml =
                YamlConfiguration.loadConfiguration(path.toFile());
        String profile = yaml.getString(
                "runtime-profile", "testing")
                .toLowerCase(Locale.ROOT);
        boolean testing = !profile.equals("survival")
                && !profile.equals("production");
        long normalStep = Math.max(20L,
                yaml.getLong("tree-evolution.step-ticks", 900L));
        boolean health = yaml.getBoolean(
                "world-health-mode.enabled", true);
        double multiplier = Math.max(0.01D,
                yaml.getDouble(
                        "world-health-mode.growth-speed-multiplier",
                        0.15D));
        long effectiveNormal = health
                ? Math.max(20L, Math.round(normalStep / multiplier))
                : normalStep;
        long step = testing
                ? Math.max(1L, yaml.getLong(
                        "tree-evolution.testing.step-ticks", 5L))
                : effectiveNormal;
        long minimumDelay = testing
                ? Math.max(1L, yaml.getLong(
                        "tree-evolution.testing.min-delay-ticks",
                        step))
                : 20L;
        int attempts = testing
                ? Math.max(1, yaml.getInt(
                        "tree-evolution.testing.attempts-per-step",
                        96))
                : Math.max(1, yaml.getInt(
                        "tree-evolution.attempts-per-step", 48));
        int blocks = testing
                ? Math.max(1, yaml.getInt(
                        "tree-evolution.testing.blocks-per-step", 1))
                : Math.max(1, yaml.getInt(
                        "tree-evolution.blocks-per-step", 1));
        TreeMaturityStage maximum = parseStage(yaml.getString(
                "tree-evolution.maximum-stage", "MEDIUM"));
        TreeProductionSmokeSettings settings =
                new TreeProductionSmokeSettings(
                        profile,
                        testing,
                        step,
                        minimumDelay,
                        attempts,
                        blocks,
                        Math.max(0, yaml.getInt(
                                "tree-evolution.search-radius", 0)),
                        Math.max(0, yaml.getInt(
                                "tree-evolution"
                                        + ".required-player-distance-chunks",
                                0)),
                        Math.max(0, yaml.getInt(
                                "tree-evolution.owned-chunk-radius", 1)),
                        maximum,
                        multiplier,
                        path.toAbsolutePath());
        settings.validate();
        return settings;
    }

    private void validate() {
        require(attemptsPerStep >= blocksPerStep,
                "attempt budget is smaller than block budget");
        require(stepTicks > 0 && minimumDelayTicks > 0,
                "scheduler delays must be positive");
        require(ownedChunkRadius <= 4,
                "owned region radius is unexpectedly broad: "
                        + ownedChunkRadius);
        require(worldHealthMultiplier > 0.0D,
                "world health multiplier must be positive");
    }

    String summary() {
        return "profile=" + profile
                + " testing=" + testing
                + " stepTicks=" + stepTicks
                + " minDelay=" + minimumDelayTicks
                + " attempts=" + attemptsPerStep
                + " blocks=" + blocksPerStep
                + " searchRadius=" + searchRadius
                + " playerDistanceChunks="
                + requiredPlayerDistanceChunks
                + " ownedRadius=" + ownedChunkRadius
                + " maximumStage=" + maximumStage
                + " worldHealth=" + worldHealthMultiplier;
    }

    private static TreeMaturityStage parseStage(String value) {
        try {
            return TreeMaturityStage.valueOf(
                    value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return TreeMaturityStage.MEDIUM;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
