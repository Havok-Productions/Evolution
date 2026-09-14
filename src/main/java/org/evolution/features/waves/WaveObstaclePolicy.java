package org.evolution.features.waves;

final class WaveObstaclePolicy {
    private WaveObstaclePolicy() {
    }

    static Crossing findCrossing(WaveLakeFlowCache.Snapshot topology,
            double originX, double originZ, double directionX, double directionZ,
            TravelingWaveFront front) {
        double magnitude = Math.hypot(directionX, directionZ);
        if (magnitude <= 0.001D) {
            return Crossing.none();
        }
        double stepX = directionX / magnitude;
        double stepZ = directionZ / magnitude;
        int limit = bridgeLimit(front);
        int landCells = 0;
        boolean foundLand = false;
        int previousX = Integer.MIN_VALUE;
        int previousZ = Integer.MIN_VALUE;
        for (int distance = 1; distance <= limit + 2; distance++) {
            int x = (int) Math.round(originX + (stepX * distance));
            int z = (int) Math.round(originZ + (stepZ * distance));
            if (x == previousX && z == previousZ) {
                continue;
            }
            previousX = x;
            previousZ = z;
            if (!topology.isKnown(x, z)) {
                return Crossing.none();
            }
            if (topology.isWater(x, z)) {
                if (foundLand) {
                    return new Crossing(true, landCells, distance, x, z,
                            Math.max(0.70D, 1.0D - (landCells * 0.045D)));
                }
                continue;
            }
            foundLand = true;
            landCells++;
            if (landCells > limit) {
                return Crossing.none();
            }
        }
        return Crossing.none();
    }

    private static int bridgeLimit(TravelingWaveFront front) {
        int shapeLimit = (int) Math.round(Math.min(
                front.halfLength() * 0.60D, front.halfWidth() * 0.50D));
        int maximum = switch (front.kind()) {
            case GIANT -> 12;
            case STANDARD, CROSSING, MERGED -> 8;
        };
        if (front.channelCourseLocked()) {
            maximum = Math.min(maximum, 4);
        }
        return Math.max(2, Math.min(maximum, shapeLimit));
    }

    record Crossing(boolean crosses, int landCells, int travelDistance,
            int resumeX, int resumeZ, double energyScale) {
        static Crossing none() {
            return new Crossing(false, 0, 0, 0, 0, 1.0D);
        }
    }
}
