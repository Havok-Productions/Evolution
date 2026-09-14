package org.evolution.features.waves;

import java.util.List;

/**
 * ## Verifies the player-wide one-wave limit and stable visual ownership.
 */
public final class WavePlayerFrontViewPolicySmokeTest {
    private WavePlayerFrontViewPolicySmokeTest() {
    }

    public static void main(String[] args) {
        TravelingWaveFront near = front(1L, 8.0D);
        TravelingWaveFront middle = front(2L, 24.0D);
        TravelingWaveFront far = front(3L, 48.0D);

        WavePlayerFrontViewPolicy.Selection first =
                WavePlayerFrontViewPolicy.select(
                        List.of(far, middle, near), List.of(), 0, 0, 1);
        require(ids(first.fronts()).equals(List.of(1L))
                        && first.suppressed() == 2,
                "the nearest single wave should own an empty player view");

        WavePlayerFrontViewPolicy.Selection moved =
                WavePlayerFrontViewPolicy.select(
                        List.of(near, middle, far), first.fronts(), 50, 0, 1);
        require(ids(moved.fronts()).equals(List.of(1L)),
                "movement must retain the current wave instead of reshuffling");
        System.out.println(
                "Wave player-view smoke test passed: one stable wave visible.");
    }

    private static TravelingWaveFront front(long id, double x) {
        return new TravelingWaveFront(
                id, x, 0.0D, 1.0D, 0.0D,
                12.0D, 16.0D, 1.0D, 0L);
    }

    private static List<Long> ids(List<TravelingWaveFront> fronts) {
        return fronts.stream().map(TravelingWaveFront::id).toList();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
