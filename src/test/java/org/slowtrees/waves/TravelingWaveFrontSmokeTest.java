package org.slowtrees.waves;

public final class TravelingWaveFrontSmokeTest {
    private TravelingWaveFrontSmokeTest() {
    }

    public static void main(String[] args) {
        TravelingWaveFront front = new TravelingWaveFront(
                1L, 0.0D, 0.0D, 1.0D, 0.0D, 12.0D, 28.0D, 0.90D, 0L);
        double before = front.x();
        TravelingWaveFront.Motion motion = front.prepareMotion(
                20L, 1.35D, 1.0D, 0.0D, false);
        front.commitMotion(motion);
        require(front.x() > before + 1.30D,
                "a live front must advance horizontally by speed times elapsed time");

        double sampleX = front.x() + (front.headingX() * front.halfLength() * 0.60D);
        double sampleZ = front.z() + (front.headingZ() * front.halfLength() * 0.60D);
        double fixedFirst = front.strengthAt(sampleX, sampleZ, 20L);
        double fixedLater = front.strengthAt(sampleX, sampleZ, 30L);
        require(fixedFirst > 0.20D && close(fixedFirst, fixedLater),
                "a visible traveling crest must not pulse vertically while its center is stationary");

        TravelingWaveFront.Motion next = front.prepareMotion(
                40L, 4.0D, 1.0D, 0.0D, false);
        front.commitMotion(next);
        double oldCrestAfter = front.strengthAt(sampleX, sampleZ, 40L);
        require(Math.abs(fixedFirst - oldCrestAfter) > 0.05D,
                "crest strength must change at a world coordinate because the front moved through space");

        TravelingWaveFront turning = new TravelingWaveFront(
                2L, 0.0D, 0.0D, -1.0D, 0.0D, 12.0D, 28.0D, 0.90D, 0L);
        for (long tick = 10L; tick <= 100L; tick += 10L) {
            TravelingWaveFront.Motion turn = turning.prepareMotion(
                    tick, 1.0D, 1.0D, 0.0D, true);
            turning.commitMotion(turn);
        }
        require(turning.headingX() > 0.40D,
                "the whole front must turn toward shore instead of canceling or steering per cell");

        double contactX = turning.x() + (turning.headingX() * turning.halfLength() * 0.60D);
        double contactZ = turning.z() + (turning.headingZ() * turning.halfLength() * 0.60D);
        TravelingWaveFront lockedCourse = new TravelingWaveFront(
                8L, TravelingWaveFront.Kind.STANDARD, 0.0D,
                0.0D, 0.0D, 1.0D, 0.0D, 12.0D, 28.0D, 0.90D, 0L);
        lockedCourse.acquireShoreTarget(1.0D, 0.0D, 30);
        double lockedTargetX = lockedCourse.shoreTargetX();
        double lockedTargetZ = lockedCourse.shoreTargetZ();
        lockedCourse.acquireShoreTarget(0.0D, 1.0D, 12);
        require(close(lockedCourse.shoreTargetX(), lockedTargetX)
                        && close(lockedCourse.shoreTargetZ(), lockedTargetZ),
                "a front must keep its first shoreline target when player-centered topology changes");

        TravelingWaveFront crossing = new TravelingWaveFront(
                3L, TravelingWaveFront.Kind.CROSSING, Math.toRadians(30.0D),
                0.0D, 0.0D, Math.cos(Math.toRadians(30.0D)), Math.sin(Math.toRadians(30.0D)),
                12.0D, 28.0D, 0.90D, 0L);
        TravelingWaveFront.Direction offshoreCourse = crossing.courseDirection(
                1.0D, 0.0D, 48, true);
        TravelingWaveFront.Direction coastCourse = crossing.courseDirection(
                1.0D, 0.0D, 2, true);
        require(offshoreCourse.z() > 0.15D && offshoreCourse.z() < 0.25D,
                "crossing fronts keep a visible but shore-safe offshore approach angle");
        require(coastCourse.x() > 0.99D && Math.abs(coastCourse.z()) < 0.05D,
                "course variation must converge back toward land at coast contact");

        TravelingWaveFront standardShape = new TravelingWaveFront(
                4L, TravelingWaveFront.Kind.STANDARD, 0.0D,
                0.0D, 0.0D, 1.0D, 0.0D, 12.0D, 28.0D, 0.90D, 0L);
        TravelingWaveFront giantShape = new TravelingWaveFront(
                6L, TravelingWaveFront.Kind.GIANT, 0.0D,
                0.0D, 0.0D, 1.0D, 0.0D, 15.0D, 38.0D, 0.98D, 0L);
        int standardCells = visibleCells(standardShape, 0L);
        int giantCells = visibleCells(giantShape, 0L);
        require(giantCells > standardCells * 1.60D,
                "giant wave trains must have a materially larger layered footprint");

        TravelingWaveFront arriving = new TravelingWaveFront(
                9L, TravelingWaveFront.Kind.STANDARD, 0.0D,
                0.0D, 0.0D, 1.0D, 0.0D, 12.0D, 28.0D, 0.90D, 0L,
                false, 20L);
        double arrivalX = arriving.halfLength() * 0.60D;
        double hiddenArrival = arriving.strengthAt(arrivalX, 0.0D, 0L);
        double middleArrival = arriving.strengthAt(arrivalX, 0.0D, 10L);
        double completeArrival = arriving.strengthAt(arrivalX, 0.0D, 20L);
        require(hiddenArrival == 0.0D && middleArrival > hiddenArrival
                        && completeArrival > middleArrival,
                "replacement fronts must ease in instead of appearing in one frame");

        TravelingWaveFront mergedResult = new TravelingWaveFront(
                10L, TravelingWaveFront.Kind.MERGED, 0.0D,
                0.0D, 0.0D, 1.0D, 0.0D, 12.0D, 28.0D, 0.90D, 0L,
                true, 20L);
        require(!mergedResult.mergeEligible(200L),
                "a merged result must never recursively merge again");

        turning.beginShoreFizzle(100L);
        double contact = turning.strengthAt(contactX, contactZ, 100L);
        double fading = turning.strengthAt(contactX, contactZ, 125L);
        require(contact > 0.20D && fading < contact,
                "shore contact must fizzle the persistent front");

        System.out.println("Traveling wavefront smoke test passed: moved="
                + round(front.x()) + " stable-height=" + round(fixedFirst)
                + " shore-heading=" + round(turning.headingX()));
    }

    private static int visibleCells(TravelingWaveFront front, long tick) {
        int cells = 0;
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z++) {
                cells += front.strengthAt(x, z, tick) >= 0.20D ? 1 : 0;
            }
        }
        return cells;
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) < 0.000001D;
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
