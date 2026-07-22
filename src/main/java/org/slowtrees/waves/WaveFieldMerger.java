package org.slowtrees.waves;

final class WaveFieldMerger {
    double merge(double accumulated, double incoming, double softness) {
        double a = clamp(accumulated);
        double b = clamp(incoming * softness);
        // ## Probabilistic union joins overlapping ovals without seams or values above one.
        return 1.0D - ((1.0D - a) * (1.0D - b));
    }

    private double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
