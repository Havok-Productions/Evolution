package org.slowtrees.waves;

public final class WaveFrameContinuitySmokeTest {
    private WaveFrameContinuitySmokeTest() {
    }

    public static void main(String[] args) {
        require(!WaveFrameContinuity.reassertDue(39L, 0L, 40L, false),
                "unchanged packet visuals must stay quiet before their refresh window");
        require(WaveFrameContinuity.reassertDue(40L, 0L, 40L, false),
                "unchanged packet visuals must be repaired after the refresh window");
        require(WaveFrameContinuity.reassertDue(6L, 4L, 40L, true),
                "crossing a chunk boundary must make an existing visual refreshable");
        require(!WaveFrameContinuity.reassertDue(4L, 4L, 40L, true),
                "one frame must never resend the same visual twice");

        boolean[] known = {true, false, true, true};
        boolean[] water = {true, false, false, true};
        LakeWaveFlowField field = LakeWaveFlowField.build(2, 2, known, water);
        require(field.isKnown(0, 0) && field.isWater(0, 0),
                "confirmed water must retain both topology flags");
        require(!field.isKnown(1, 0),
                "an unreadable Folia cell must remain UNKNOWN rather than becoming land");
        require(field.isKnown(0, 1) && !field.isWater(0, 1),
                "confirmed land must remain distinguishable from UNKNOWN");

        System.out.println("Wave continuity smoke test passed: tri-state topology and bounded packet reassertion are stable.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
