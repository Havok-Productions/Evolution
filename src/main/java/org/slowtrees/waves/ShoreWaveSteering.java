package org.slowtrees.waves;

final class ShoreWaveSteering {
    Steering resolve(double windX, double windZ,
            double shoreX, double shoreZ,
            int shoreDistance, int influenceDistance) {
        Direction wind = normalize(windX, windZ);
        if ((Math.abs(shoreX) + Math.abs(shoreZ)) < 0.001D
                || shoreDistance < 0 || shoreDistance >= influenceDistance) {
            return new Steering(wind.x(), wind.z(), 0.0D);
        }
        Direction shore = normalize(shoreX, shoreZ);
        double proximity = 1.0D - (shoreDistance
                / (double) Math.max(1, influenceDistance));
        double influence = smoothStep(proximity);
        double windAngle = Math.atan2(wind.z(), wind.x());
        double shoreAngle = Math.atan2(shore.z(), shore.x());
        double turn = Math.atan2(
                Math.sin(shoreAngle - windAngle),
                Math.cos(shoreAngle - windAngle));
        double angle = windAngle + (turn * influence);
        return new Steering(Math.cos(angle), Math.sin(angle), influence);
    }

    private Direction normalize(double x, double z) {
        double magnitude = Math.sqrt((x * x) + (z * z));
        if (magnitude < 0.001D) {
            return new Direction(1.0D, 0.0D);
        }
        return new Direction(x / magnitude, z / magnitude);
    }

    private double smoothStep(double value) {
        double clamped = Math.max(0.0D, Math.min(1.0D, value));
        return clamped * clamped * (3.0D - (2.0D * clamped));
    }

    record Steering(double directionX, double directionZ, double influence) {
    }

    private record Direction(double x, double z) {
    }
}
