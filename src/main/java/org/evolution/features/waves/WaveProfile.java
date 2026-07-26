package org.evolution.features.waves;

final class WaveProfile {
    private final double amplitude;
    private final double speed;
    private final int wavelength;
    private final double frequency;
    private final double heightVariation;
    private final double occurrence;
    private final int travelDistance;
    private final double fadeStart;
    private final double fadePower;

    WaveProfile(
            double amplitude,
            double speed,
            int wavelength,
            double frequency,
            double heightVariation,
            double occurrence,
            int travelDistance,
            double fadeStart,
            double fadePower
    ) {
        this.amplitude = Math.max(0.0D, amplitude);
        this.speed = Math.max(0.01D, speed);
        this.wavelength = Math.max(4, wavelength);
        this.frequency = Math.max(0.01D, frequency);
        this.heightVariation = clamp(heightVariation, 0.0D, 1.0D);
        this.occurrence = clamp(occurrence, 0.0D, 1.0D);
        this.travelDistance = Math.max(this.wavelength * 2, travelDistance);
        this.fadeStart = clamp(fadeStart, 0.10D, 0.85D);
        this.fadePower = clamp(fadePower, 0.35D, 4.0D);
    }

    double amplitude() {
        return amplitude;
    }

    double speed() {
        return speed;
    }

    int wavelength() {
        return wavelength;
    }

    double frequency() {
        return frequency;
    }

    double heightVariation() {
        return heightVariation;
    }

    double occurrence() {
        return occurrence;
    }

    int travelDistance() {
        return travelDistance;
    }

    double fadeStart() {
        return fadeStart;
    }

    double fadePower() {
        return fadePower;
    }

    String summary() {
        return "amp=" + amplitude + ", speed-blocks-per-second=" + speed + ", wavelength=" + wavelength + ", frequency=" + frequency
                + ", variation=" + heightVariation + ", occurrence=" + occurrence
                + ", travel=" + travelDistance + ", fade-start=" + fadeStart + ", fade-power=" + fadePower;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}