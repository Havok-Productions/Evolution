package org.slowtrees.waves;

final class ShoreWaveResponse {
    Impact resolve(double sourceStrength, int shoreDistance, int waterDepth,
            OvalWaveSettings settings, double exposure) {
        if (shoreDistance < 0) {
            return new Impact(clamp(sourceStrength), clamp(sourceStrength), false);
        }
        double proximity = 1.0D - Math.min(1.0D, shoreDistance / 8.0D);
        double shallow = 1.0D - Math.min(1.0D, Math.max(1, waterDepth) / 8.0D);
        // ## Shore compression increases impact pressure for splash/run-up, while
        // visualStrength is hard-capped to the incoming oval's original height.
        double compressed = Math.pow(clamp(sourceStrength), 1.0D - (settings.shoreCompression() * proximity));
        double pressure = clamp(sourceStrength + ((compressed - sourceStrength) * exposure)
                + (settings.shoreBoost() * proximity * exposure)
                + (settings.shallowWaterBoost() * shallow * exposure));
        return new Impact(Math.min(clamp(sourceStrength), pressure), pressure, true);
    }

    private double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    record Impact(double visualStrength, double pressure, boolean impactsShore) {
    }
}