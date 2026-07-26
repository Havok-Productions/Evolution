package org.evolution.features.waves;

final class WaveEnvironmentModel {
    Direction normalize(double x, double z) {
        double magnitude = Math.sqrt((x * x) + (z * z));
        if (magnitude < 0.001D) {
            return new Direction(1.0D, 0.0D);
        }
        return new Direction(x / magnitude, z / magnitude);
    }

    double coastExposure(double windX, double windZ, int shoreDx, int shoreDz) {
        if (shoreDx == 0 && shoreDz == 0) {
            return -1.0D;
        }
        Direction wind = normalize(windX, windZ);
        Direction shore = normalize(shoreDx, shoreDz);
        return clamp((wind.x() * shore.x()) + (wind.z() * shore.z()), -1.0D, 1.0D);
    }

    double coastResponse(double exposure, double minimumFacing) {
        double minimum = clamp(minimumFacing, 0.0D, 0.95D);
        if (exposure <= minimum) {
            return 0.0D;
        }
        return smoothStep((exposure - minimum) / (1.0D - minimum));
    }

    double fetchGrowth(int fetchDistance, int maximumFetch, double windStrength) {
        double fetch = clamp(fetchDistance / (double) Math.max(1, maximumFetch), 0.0D, 1.0D);
        double developed = 0.07D + (0.93D * smoothStep(fetch));
        double normalizedWind = clamp((windStrength - 0.35D) / 0.90D, 0.0D, 1.0D);
        return clamp(developed * (0.85D + (0.15D * normalizedWind)), 0.0D, 1.0D);
    }

    private double smoothStep(double value) {
        double clamped = clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - (2.0D * clamped));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    record Direction(double x, double z) {
    }
}
