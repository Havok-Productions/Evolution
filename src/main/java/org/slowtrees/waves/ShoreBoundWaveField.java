package org.slowtrees.waves;

final class ShoreBoundWaveField {
    private static final double MINIMUM_FRONT_SPACING = 14.0D;
    private static final double MAXIMUM_FRONT_SPACING = 24.0D;

    FieldSample sample(int shoreDistance, int sourceDistance, double speed, long tick) {
        int source = Math.max(4, sourceDistance);
        double spacing = Math.max(MINIMUM_FRONT_SPACING,
                Math.min(MAXIMUM_FRONT_SPACING, source * 0.35D));
        double phaseTravel = positiveModulo(
                (tick / 20.0D) * Math.max(0.10D, speed), spacing);
        double strongest = 0.0D;
        double strongestProgress = 0.0D;
        boolean strongestFizzling = false;

        // ## Large coasts carry a train of finite fronts. Every member was emitted
        // at the offshore source and follows the same decreasing-distance basin.
        for (double travelled = phaseTravel; travelled <= source; travelled += spacing) {
            double progress = travelled / source;
            double frontDistance = source - travelled;
            double offset = shoreDistance - frontDistance;
            double tailLength = 4.5D;
            if (offset < -0.75D || offset > tailLength) {
                continue;
            }

            // ## The newest front is submerged at birth. Older train members are
            // already in transit; none are created at the shoreline.
            double birthDistance = Math.min(5.0D, Math.max(2.0D, source * 0.18D));
            double birth = smoothStep(Math.min(1.0D, travelled / birthDistance));
            double shape = offset <= 0.50D
                    ? 1.0D - (Math.abs(offset) / 1.25D)
                    : 1.0D - ((offset - 0.50D) / tailLength);
            double strength = clamp(shape) * birth;
            if (strength > strongest) {
                strongest = strength;
                strongestProgress = progress;
                strongestFizzling = progress > 0.82D;
            }
        }
        return new FieldSample(strongest, strongestProgress, strongestFizzling);
    }

    private double positiveModulo(double value, double modulus) {
        double result = value % modulus;
        return result < 0.0D ? result + modulus : result;
    }

    private double smoothStep(double value) {
        double clamped = clamp(value);
        return clamped * clamped * (3.0D - (2.0D * clamped));
    }

    private double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    record FieldSample(double strength, double progress, boolean fizzling) {
    }
}