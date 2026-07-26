package org.evolution.features.waves;

final class WaveFrameContinuity {
    private WaveFrameContinuity() {
    }

    static boolean reassertDue(long tick, long lastSentTick,
            long intervalTicks, boolean force) {
        if (force && tick > lastSentTick) {
            return true;
        }
        return tick - lastSentTick >= Math.max(1L, intervalTicks);
    }
}
