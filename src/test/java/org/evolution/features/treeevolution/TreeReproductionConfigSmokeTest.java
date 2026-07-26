package org.evolution.features.treeevolution;

import org.bukkit.configuration.file.YamlConfiguration;

public final class TreeReproductionConfigSmokeTest {
    private TreeReproductionConfigSmokeTest() {
    }

    public static void main(String[] args) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("runtime-profile", "testing");
        config.set("tree-evolution.testing.enabled", true);
        config.set("tree-evolution.reproduction.enabled", true);
        config.set("tree-evolution.reproduction.minimum-stage", "MEDIUM");
        config.set("tree-evolution.reproduction.medium-chance-percent", 5.0D);
        config.set("tree-evolution.reproduction.testing-chance-multiplier", 4.0D);
        config.set("tree-evolution.reproduction.testing-cooldown-ticks", 200L);
        config.set("tree-evolution.reproduction.reserved-search.testing-interval-ticks", 100L);
        config.set("tree-evolution.reproduction.reserved-search.candidate-rolls-per-pass", 4);

        TreeReproductionConfig reproduction =
                TreeReproductionConfig.load(config);
        require(reproduction.enabled(),
                "tree reproduction must be enabled");
        require(reproduction.minimumStage() == TreeMaturityStage.MEDIUM,
                "MEDIUM trees must be eligible at the configured stage ceiling");
        require(Math.abs(reproduction.mediumChance() - 0.20D) < 0.0001D,
                "testing must accelerate chance without changing placement rules");
        require(reproduction.cooldownMillis() == 10_000L,
                "200 testing ticks must become a ten-second cooldown");
        require(reproduction.spacingRadius() >= 2,
                "offspring must keep space from existing trees");
        require(reproduction.radiusFor(null) > reproduction.spacingRadius(),
                "MEDIUM offspring search must extend beyond the spacing gate");
        require(reproduction.radiusFor(null)
                        >= reproduction.minimumRadius(),
                "offspring search must preserve a valid configured ring");
        require(reproduction.searchAttempts() == 32,
                "expanded search must default to 32 bounded terrain checks");
        require(reproduction.reservedSearchIntervalMillis() == 5_000L,
                "testing must reserve one reproduction pass every five seconds");
        require(reproduction.candidateRollsPerPass() == 4,
                "reserved pass must inspect a bounded number of eligible parents");

        System.out.println("Tree reproduction config smoke test passed: "
                + "medium-stage=true testing-chance=0.20 "
                + "cooldown-ms=10000 search=32 reserved-ms=5000");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
