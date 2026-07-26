package org.evolution.features.waves;

record OvalWavePulse(
        double centerU,
        double centerV,
        double halfLength,
        double halfWidth,
        double energy,
        double progress,
        boolean expanding
) {
    double strengthAt(double u, double v) {
        double du = (u - centerU) / Math.max(1.0D, halfLength);
        double dv = (v - centerV) / Math.max(1.0D, halfWidth);
        double lateral = Math.abs(dv);
        if (lateral >= 1.0D || du <= -1.0D || du >= 1.0D || energy <= 0.0D) {
            return 0.0D;
        }

        // ## The pulse begins below sea level. Only its forward crescent emerges,
        // leaving the trailing side open so the shape reads as a front, not a donut.
        if (energy < 0.08D) {
            return 0.0D;
        }
        double emergence = smoothStep((progress - 0.03D) / 0.16D);
        if (emergence <= 0.0D) {
            return 0.0D;
        }

        // The leading edge bends backward near its sides like a shoreline wave.
        // A broad low shoulder supports the crest, but the wake remains submerged.
        double frontU = 0.72D - (0.48D * lateral * lateral);
        double distanceBehindFront = frontU - du;
        // ## A breaking boomerang is deepest through its center and tapers at
        // both tips. This preserves width without turning the crest into a wall.
        double rearReach = 0.70D - (0.42D * Math.pow(lateral, 1.45D));
        double forwardReach = 0.18D - (0.08D * lateral);
        if (distanceBehindFront > rearReach || distanceBehindFront < -forwardReach) {
            return 0.0D;
        }

        double rearRise = smoothStep((rearReach - distanceBehindFront) / rearReach);
        double forwardFall = smoothStep((distanceBehindFront + forwardReach) / forwardReach);
        double lateralTaper = smoothStep((1.0D - lateral) / 0.16D);
        return clamp(rearRise * forwardFall * lateralTaper * energy * emergence);
    }

    private double smoothStep(double value) {
        double clamped = clamp(value);
        return clamped * clamped * (3.0D - (2.0D * clamped));
    }

    private double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
