package org.slowtrees.wind;

import java.util.Random;

final class WindPattern {
    private static final double[] CARDINAL_X = {1.0D, -1.0D, 0.0D, 0.0D, 0.7D, 0.7D, -0.7D, -0.7D};
    private static final double[] CARDINAL_Z = {0.0D, 0.0D, 1.0D, -1.0D, 0.7D, -0.7D, 0.7D, -0.7D};

    private final double x;
    private final double z;
    private final double strength;
    private long expiresAtMillis;

    private WindPattern(double x, double z, double strength, long expiresAtMillis) {
        this.x = x;
        this.z = z;
        this.strength = strength;
        this.expiresAtMillis = expiresAtMillis;
    }

    static WindPattern calm() {
        return new WindPattern(1.0D, 0.0D, 0.4D, 0L);
    }

    static WindPattern next(Random random, long durationTicks) {
        int index = random.nextInt(CARDINAL_X.length);
        double strength = 0.35D + (random.nextDouble() * 0.9D);
        return new WindPattern(CARDINAL_X[index], CARDINAL_Z[index], strength, System.currentTimeMillis() + (durationTicks * 50L));
    }

    boolean expired() {
        return System.currentTimeMillis() >= expiresAtMillis;
    }

    double x() {
        return x;
    }

    double z() {
        return z;
    }

    double strength() {
        return strength;
    }
}
