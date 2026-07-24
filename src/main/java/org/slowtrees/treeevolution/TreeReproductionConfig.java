package org.slowtrees.treeevolution;

import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;
import org.slowtrees.core.RuntimeProfile;

record TreeReproductionConfig(
        boolean enabled,
        TreeMaturityStage minimumStage,
        int minimumAge,
        double mediumChance,
        double matureChance,
        double ancientChance,
        double landmarkMultiplier,
        int minimumRadius,
        int maximumRadius,
        int spacingRadius,
        int searchAttempts,
        long cooldownMillis,
        long reservedSearchIntervalMillis,
        int candidateRollsPerPass
) {
    static TreeReproductionConfig load(FileConfiguration config) {
        boolean testing = RuntimeProfile.testingEnabled(config)
                && config.getBoolean("tree-evolution.testing.enabled", true);
        double chanceMultiplier = testing
                ? positive(config.getDouble(
                        "tree-evolution.reproduction.testing-chance-multiplier",
                        4.0D), 4.0D)
                : 1.0D;
        long cooldownTicks = testing
                ? config.getLong(
                        "tree-evolution.reproduction.testing-cooldown-ticks",
                        200L)
                : config.getLong(
                        "tree-evolution.reproduction.cooldown-ticks",
                        12000L);
        long reservedSearchIntervalTicks = testing
                ? config.getLong(
                        "tree-evolution.reproduction.reserved-search.testing-interval-ticks",
                        100L)
                : config.getLong(
                        "tree-evolution.reproduction.reserved-search.interval-ticks",
                        600L);
        int minimumRadius = clamp(config.getInt(
                "tree-evolution.reproduction.minimum-radius", 4), 2, 16);
        int maximumRadius = clamp(config.getInt(
                "tree-evolution.reproduction.maximum-radius", 12),
                minimumRadius + 1, 24);

        return new TreeReproductionConfig(
                config.getBoolean("tree-evolution.reproduction.enabled", true),
                parseStage(config.getString(
                        "tree-evolution.reproduction.minimum-stage",
                        "MEDIUM")),
                Math.max(0, config.getInt(
                        "tree-evolution.reproduction.minimum-age", 20)),
                chance(config, "medium-chance-percent", 4.5D)
                        * chanceMultiplier,
                chance(config, "mature-chance-percent", 8.0D)
                        * chanceMultiplier,
                chance(config, "ancient-chance-percent", 12.0D)
                        * chanceMultiplier,
                positive(config.getDouble(
                        "tree-evolution.reproduction.landmark-multiplier",
                        1.8D), 1.8D),
                minimumRadius,
                maximumRadius,
                clamp(config.getInt(
                        "tree-evolution.reproduction.spacing-radius", 4),
                        2, 8),
                clamp(config.getInt(
                        "tree-evolution.reproduction.search-attempts", 32),
                        1, 64),
                Math.max(20L, cooldownTicks) * 50L,
                Math.max(20L, reservedSearchIntervalTicks) * 50L,
                clamp(config.getInt(
                        "tree-evolution.reproduction.reserved-search.candidate-rolls-per-pass",
                        4), 1, 8)
        );
    }

    boolean eligible(TreeDna dna, long now, long cooldownUntil) {
        return enabled
                && dna.maturityStage().ordinal() >= minimumStage.ordinal()
                && dna.age() >= minimumAge
                && dna.damageCount() == 0
                && now >= cooldownUntil;
    }

    double chanceFor(TreeDna dna) {
        double base = switch (dna.maturityStage()) {
            case SMALL -> 0.0D;
            case MEDIUM -> mediumChance;
            case MATURE -> matureChance;
            case ANCIENT -> ancientChance;
        };
        if (dna.rarity() == TreeRarity.LANDMARK
                || dna.personality() == TreePersonality.ANCIENT_LANDMARK) {
            base *= landmarkMultiplier;
        }
        return Math.min(1.0D, base);
    }

    int radiusFor(TreeDna dna) {
        // ## minimumRadius is the inner planting boundary; maximumRadius is
        // the outer search boundary. MEDIUM trees must search beyond the
        // spacing radius or every offspring location is impossible.
        return Math.max(minimumRadius, maximumRadius);
    }

    private static TreeMaturityStage parseStage(String configured) {
        try {
            return TreeMaturityStage.valueOf(
                    configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return TreeMaturityStage.MEDIUM;
        }
    }

    private static double chance(
            FileConfiguration config,
            String key,
            double fallback
    ) {
        double percent = config.getDouble(
                "tree-evolution.reproduction." + key, fallback);
        return Math.max(0.0D, Math.min(100.0D, percent)) / 100.0D;
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0D ? value : fallback;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
