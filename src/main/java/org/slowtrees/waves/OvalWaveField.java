package org.slowtrees.waves;

final class OvalWaveField {
    private static final double PRIMARY_X = 0.86D;
    private static final double PRIMARY_Z = 0.50D;
    private final WaveFieldMerger merger = new WaveFieldMerger();

    FieldSample sample(WaveProfile profile, OvalWaveSettings settings, int x, int z, long tick) {
        return sample(profile, settings, x, z, tick, PRIMARY_X, PRIMARY_Z);
    }

    FieldSample sample(WaveProfile profile, OvalWaveSettings settings, int x, int z, long tick,
            double directionX, double directionZ) {
        double magnitude = Math.sqrt((directionX * directionX) + (directionZ * directionZ));
        double forwardX = magnitude > 0.001D ? directionX / magnitude : PRIMARY_X;
        double forwardZ = magnitude > 0.001D ? directionZ / magnitude : PRIMARY_Z;
        double u = (x * forwardX) + (z * forwardZ);
        double v = (x * -forwardZ) + (z * forwardX);
        return sampleProjected(profile, settings, u, v, tick);
    }

    FieldSample sampleProjected(WaveProfile profile, OvalWaveSettings settings,
            double u, double v, long tick) {
        double seconds = tick / 20.0D;
        double travelled = seconds * profile.speed();
        double crestSpacing = Math.max(6.0D, profile.wavelength() / profile.frequency());
        double travelSpacing = Math.max(10.0D, crestSpacing * settings.travelSpacingScale());
        double laneSpacing = Math.max(12.0D, profile.wavelength() * settings.laneSpacingScale());
        int centerTravel = floor((u - travelled) / travelSpacing);
        int centerLane = floor(v / laneSpacing);

        double combined = 0.0D;
        double dominant = 0.0D;
        double dominantProgress = 0.0D;
        boolean dominantExpanding = true;
        int overlaps = 0;
        int activePulses = 0;
        for (int travelIndex = centerTravel - 2; travelIndex <= centerTravel + 2; travelIndex++) {
            for (int laneIndex = centerLane - 1; laneIndex <= centerLane + 1; laneIndex++) {
                double seed = hash01(travelIndex, laneIndex);
                if (seed > profile.occurrence()) {
                    continue;
                }
                double progress = positiveModulo((seconds / settings.lifecycleSeconds()) + hash01(laneIndex, travelIndex), 1.0D);
                double growth = smoothStep(Math.min(1.0D, progress / Math.max(0.10D, profile.fadeStart())));
                boolean expanding = progress < profile.fadeStart();
                double fade = expanding ? 1.0D : Math.pow(1.0D - smoothStep(
                        (progress - profile.fadeStart()) / Math.max(0.01D, 1.0D - profile.fadeStart())), profile.fadePower());
                double widthScale = lerp(settings.widthMinScale(), settings.widthMaxScale(), growth);
                double lengthScale = lerp(settings.lengthMinScale(), settings.lengthMaxScale(), growth);
                double widthVariation = 0.88D + (hash01(travelIndex + 91, laneIndex - 37) * 0.24D);
                double lengthVariation = 0.88D + (hash01(travelIndex - 53, laneIndex + 71) * 0.24D);
                double centerU = (travelIndex * travelSpacing) + travelled + ((seed - 0.5D) * travelSpacing * 0.32D);
                double centerV = (laneIndex * laneSpacing)
                        + ((hash01(travelIndex + 17, laneIndex + 29) - 0.5D) * laneSpacing * 0.30D);
                OvalWavePulse pulse = new OvalWavePulse(centerU, centerV,
                        Math.max(3.0D, crestSpacing * lengthScale * lengthVariation),
                        Math.max(5.0D, profile.wavelength() * widthScale * widthVariation),
                        fade, progress, expanding);
                double strength = pulse.strengthAt(u, v);
                if (strength <= 0.001D) {
                    continue;
                }
                activePulses++;
                if (strength >= 0.12D) {
                    overlaps++;
                }
                combined = merger.merge(combined, strength, settings.mergeSoftness());
                if (strength > dominant) {
                    dominant = strength;
                    dominantProgress = pulse.progress();
                    dominantExpanding = pulse.expanding();
                }
            }
        }
        return new FieldSample(combined, dominantProgress, !dominantExpanding, overlaps, activePulses);
    }

    FieldSample sampleShoreGuided(WaveProfile profile, OvalWaveSettings settings,
            int x, int z, double directionX, double directionZ,
            int shoreDistance, int sourceDistance, long tick) {
        double magnitude = Math.sqrt((directionX * directionX) + (directionZ * directionZ));
        double forwardX = magnitude > 0.001D ? directionX / magnitude : PRIMARY_X;
        double forwardZ = magnitude > 0.001D ? directionZ / magnitude : PRIMARY_Z;
        double u = (x * forwardX) + (z * forwardZ);
        double v = (x * -forwardZ) + (z * forwardX);
        double source = Math.max(4.0D, sourceDistance);
        double journey = Math.max(0.0D, Math.min(1.0D,
                (source - Math.max(0, shoreDistance)) / source));
        double seconds = tick / 20.0D;
        double travelled = seconds * profile.speed();
        double crestSpacing = Math.max(6.0D, profile.wavelength() / profile.frequency());
        double travelSpacing = Math.max(10.0D, crestSpacing * settings.travelSpacingScale());
        double laneSpacing = Math.max(12.0D, profile.wavelength() * settings.laneSpacingScale());
        int centerTravel = floor((u - travelled) / travelSpacing);
        int centerLane = floor(v / laneSpacing);

        double sourceRise = smoothStep(Math.min(1.0D, journey / 0.08D));
        double growth = smoothStep(Math.min(1.0D, journey / 0.38D));
        boolean expanding = journey < 0.38D;
        double shoreFinish = journey <= 0.86D ? 1.0D
                : lerp(1.0D, 0.70D, smoothStep((journey - 0.86D) / 0.14D));
        double combined = 0.0D;
        double dominant = 0.0D;
        int overlaps = 0;
        int activePulses = 0;
        for (int travelIndex = centerTravel - 2; travelIndex <= centerTravel + 2; travelIndex++) {
            for (int laneIndex = centerLane - 1; laneIndex <= centerLane + 1; laneIndex++) {
                double seed = hash01(travelIndex, laneIndex);
                if (seed > profile.occurrence()) {
                    continue;
                }
                double widthScale = lerp(settings.widthMinScale(),
                        settings.widthMaxScale(), growth);
                double lengthScale = lerp(settings.lengthMinScale(),
                        settings.lengthMaxScale(), growth);
                double widthVariation = 0.88D
                        + (hash01(travelIndex + 91, laneIndex - 37) * 0.24D);
                double lengthVariation = 0.88D
                        + (hash01(travelIndex - 53, laneIndex + 71) * 0.24D);
                double centerU = (travelIndex * travelSpacing) + travelled
                        + ((seed - 0.5D) * travelSpacing * 0.32D);
                double centerV = (laneIndex * laneSpacing)
                        + ((hash01(travelIndex + 17, laneIndex + 29) - 0.5D)
                        * laneSpacing * 0.30D);
                OvalWavePulse pulse = new OvalWavePulse(centerU, centerV,
                        Math.max(3.0D, crestSpacing * lengthScale * lengthVariation),
                        Math.max(5.0D, profile.wavelength() * widthScale * widthVariation),
                        sourceRise * shoreFinish, journey, expanding);
                double strength = pulse.strengthAt(u, v);
                if (strength <= 0.001D) {
                    continue;
                }
                activePulses++;
                if (strength >= 0.12D) {
                    overlaps++;
                }
                combined = merger.merge(combined, strength, settings.mergeSoftness());
                dominant = Math.max(dominant, strength);
            }
        }
        return new FieldSample(combined, journey, journey >= 0.86D,
                overlaps, activePulses);
    }
    private int floor(double value) {
        return (int) Math.floor(value);
    }

    private double lerp(double start, double end, double amount) {
        return start + ((end - start) * amount);
    }

    private double positiveModulo(double value, double modulus) {
        double result = value % modulus;
        return result < 0.0D ? result + modulus : result;
    }

    private double smoothStep(double value) {
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        return clamped * clamped * (3.0D - (2.0D * clamped));
    }

    private double hash01(int x, int z) {
        long value = (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    record FieldSample(double strength, double progress, boolean fizzling, int overlaps, int activePulses) {
    }
}
