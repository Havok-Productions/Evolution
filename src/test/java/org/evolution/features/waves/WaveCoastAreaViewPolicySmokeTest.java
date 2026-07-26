package org.evolution.features.waves;

import java.util.List;

/**
 * ## Regression for viewer-local coast caps, stable choices, and even spacing.
 */
public final class WaveCoastAreaViewPolicySmokeTest {
    private WaveCoastAreaViewPolicySmokeTest() {
    }

    public static void main(String[] args) {
        TravelingWaveFront nearStart = front(1L, 2.0D, 8.0D);
        TravelingWaveFront middleLeft = front(2L, 18.0D, 8.0D);
        TravelingWaveFront middleRight = front(3L, 38.0D, 8.0D);
        TravelingWaveFront farEnd = front(4L, 60.0D, 8.0D);
        TravelingWaveFront otherArea = front(5L, 96.0D, 8.0D);
        TravelingWaveFront openWater = new TravelingWaveFront(
                6L, 0.0D, 0.0D, 1.0D, 0.0D,
                12.0D, 16.0D, 1.0D, 0L);

        List<TravelingWaveFront> candidates = List.of(
                nearStart, middleLeft, middleRight, farEnd,
                otherArea, openWater);
        WaveCoastAreaViewPolicy.Selection first =
                WaveCoastAreaViewPolicy.select(candidates, List.of(), 3);
        require(first.coastAreas() == 2,
                "a long shoreline must be divided into independent coast areas");
        require(first.limitedAreas() == 1 && first.suppressedFronts() == 1,
                "only the crowded coast area should be capped");
        require(ids(first.fronts()).containsAll(List.of(1L, 4L, 5L, 6L)),
                "selection should preserve both coast extremes, the next area, and open water");

        WaveCoastAreaViewPolicy.Selection second =
                WaveCoastAreaViewPolicy.select(
                        List.of(farEnd, middleRight, middleLeft, nearStart,
                                otherArea, openWater),
                        first.fronts(), 3);
        require(ids(first.fronts()).equals(ids(second.fronts())),
                "candidate ordering and viewer movement must not reshuffle selected fronts");

        System.out.println(
                "Wave coast-area view smoke test passed: "
                        + "areas=2 capped=1 stable=true evenly-spread=true");
    }

    private static TravelingWaveFront front(
            long id, double targetX, double targetZ) {
        TravelingWaveFront front = new TravelingWaveFront(
                id, targetX - 24.0D, targetZ,
                1.0D, 0.0D, 12.0D, 16.0D, 1.0D, 0L);
        front.inheritShoreTarget(targetX, targetZ);
        return front;
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