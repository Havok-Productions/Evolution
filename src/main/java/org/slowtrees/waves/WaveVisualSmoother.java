package org.slowtrees.waves;

final class WaveVisualSmoother {
    private static final int THIN_WATER_LEVEL = 7;
    private static final int MAX_LEVEL_CHANGE_PER_FRAME = 1;


    int approach(int currentLevel, int desiredLevel, double smoothing) {
        int current = clamp(currentLevel);
        int desired = clamp(desiredLevel);
        double retained = Math.max(0.0D, Math.min(1.0D, smoothing));
        int blended = (int) Math.round((current * retained) + (desired * (1.0D - retained)));
        int change = Math.max(-MAX_LEVEL_CHANGE_PER_FRAME,
                Math.min(MAX_LEVEL_CHANGE_PER_FRAME, blended - current));
        if (change == 0 && current != desired) {
            change = Integer.compare(desired, current);
        }
        return clamp(current + change);
    }

    private int clamp(int level) {
        return Math.max(0, Math.min(THIN_WATER_LEVEL, level));
    }
}
