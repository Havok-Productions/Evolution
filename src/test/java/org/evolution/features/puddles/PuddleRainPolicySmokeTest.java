package org.evolution.features.puddles;

public final class PuddleRainPolicySmokeTest {
    private PuddleRainPolicySmokeTest() {
    }

    public static void main(String[] args) {
        requireRejected("desert", 2.0D, true, "dry-biome");
        requireRejected("savanna", 1.2D, true, "dry-biome");
        requireRejected("plains", 0.8D, false, "covered");
        requireRejected("snowy_plains", 0.0D, true, "snowfall");
        requireAllowed("plains", 0.8D, true);
        requireAllowed("forest", 0.7D, true);

        String snowfallAllowed = PuddleRainPolicy.rejection(
                "snowy_plains", 0.0D, true, true, true, true);
        require(snowfallAllowed == null,
                "Snowfall override should allow cold precipitation.");

        System.out.println("Puddle rain policy smoke test passed.");
    }

    private static void requireRejected(
            String biome,
            double temperature,
            boolean skyExposed,
            String expected
    ) {
        String actual = PuddleRainPolicy.rejection(
                biome, temperature, skyExposed, true, true, false);
        require(expected.equals(actual),
                biome + " expected " + expected + " but got " + actual);
    }

    private static void requireAllowed(
            String biome,
            double temperature,
            boolean skyExposed
    ) {
        String actual = PuddleRainPolicy.rejection(
                biome, temperature, skyExposed, true, true, false);
        require(actual == null, biome + " should allow rain but got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
