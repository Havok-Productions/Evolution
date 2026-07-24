package org.slowtrees.waves;

final class ShoreRunupPolicy {
    private static final int CONTACT_DISTANCE = 1;

    boolean hasArrived(int shoreDistance, boolean frontFizzling) {
        return frontFizzling
                && shoreDistance >= 0
                && shoreDistance <= CONTACT_DISTANCE;
    }

    int maximumReachableGroundY(int waterSurfaceY, double incomingHeight) {
        if (!Double.isFinite(incomingHeight) || incomingHeight <= 0.0D) {
            return waterSurfaceY - 1;
        }
        int visibleLayers = Math.max(1,
                (int) Math.ceil(Math.min(2.0D, incomingHeight) - 0.000001D));
        return waterSurfaceY + visibleLayers - 1;
    }

    boolean canReachGround(int waterSurfaceY, int groundY,
            double incomingHeight) {
        return groundY <= maximumReachableGroundY(
                waterSurfaceY, incomingHeight);
    }
}
