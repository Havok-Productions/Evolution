package org.evolution.features.puddles;

import java.util.Locale;
import java.util.Set;

final class PuddleRainPolicy {
    private static final double SNOW_TEMPERATURE = 0.15D;
    private static final Set<String> DRY_VANILLA_BIOMES = Set.of(
            "badlands",
            "desert",
            "eroded_badlands",
            "savanna",
            "savanna_plateau",
            "windswept_savanna",
            "wooded_badlands"
    );

    private PuddleRainPolicy() {
    }

    static String rejection(
            String biomeKey,
            double temperature,
            boolean skyExposed,
            boolean requireRainCapableBiome,
            boolean requireSkyExposure,
            boolean allowSnowfall
    ) {
        if (requireSkyExposure && !skyExposed) {
            return "covered";
        }
        String normalizedBiome = biomeKey == null
                ? ""
                : biomeKey.toLowerCase(Locale.ROOT);
        if (requireRainCapableBiome && DRY_VANILLA_BIOMES.contains(normalizedBiome)) {
            return "dry-biome";
        }
        if (!allowSnowfall && temperature < SNOW_TEMPERATURE) {
            return "snowfall";
        }
        return null;
    }
}
