package org.slowtrees.waves;

import java.util.UUID;

public final class WaveTightWaterSteeringSmokeTest {
    private WaveTightWaterSteeringSmokeTest() {
    }

    public static void main(String[] args) {
        TravelingWaveFront guided = new TravelingWaveFront(
                41L, TravelingWaveFront.Kind.CROSSING, Math.toRadians(38.0D),
                0.0D, 0.0D, Math.cos(Math.toRadians(38.0D)),
                Math.sin(Math.toRadians(38.0D)),
                14.0D, 34.0D, 0.92D, 0L);
        guided.acquireShoreTarget(1.0D, 0.0D, 24);

        double previousDistance = guided.lockedShoreDistance();
        boolean reached = false;
        for (long tick = 5L; tick <= 500L; tick += 5L) {
            TravelingWaveFront.Direction target = guided.lockedShoreDirection();
            TravelingWaveFront.Direction course = guided.courseDirection(
                    target.x(), target.z(),
                    (int) Math.ceil(guided.lockedShoreDistance()), true);
            TravelingWaveFront.Motion motion = guided.prepareMotion(
                    tick, 2.0D, course.x(), course.z(), true);
            if (motion.steeringSignal() == TravelingWaveFront.SteeringSignal.SHORE_REACHED) {
                reached = true;
                break;
            }
            guided.commitMotion(motion);
            double nextDistance = guided.lockedShoreDistance();
            require(nextDistance <= previousDistance + 0.0001D,
                    "a shore-bound front must never reverse away from its locked coast");
            previousDistance = nextDistance;
        }
        require(reached, "the persistent front must reach its shore instead of orbiting it");

        int radius = 32;
        int diameter = (radius * 2) + 1;
        boolean[] known = new boolean[diameter * diameter];
        boolean[] water = new boolean[diameter * diameter];
        int waterCells = 0;
        for (int z = 0; z < diameter; z++) {
            for (int x = 0; x < diameter; x++) {
                int index = (z * diameter) + x;
                known[index] = true;
                water[index] = Math.abs(z - radius) <= 19;
                waterCells += water[index] ? 1 : 0;
            }
        }
        LakeWaveFlowField channel = LakeWaveFlowField.build(
                diameter, diameter, known, water);
        WaveLakeFlowCache.Snapshot channelTopology = new WaveLakeFlowCache.Snapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000043"),
                0, 0, radius, radius, 1, 0L, known.length, waterCells, channel);
        TravelingWaveRegistry registry = new TravelingWaveRegistry();
        require(registry.localWaterSpan(channelTopology, 0, 0) == 39,
                "local probing must classify opposing banks within forty blocks");
        WaveProfile riverProfile = new WaveProfile(
                0.90D, 4.0D, 36, 2.2D, 0.5D, 1.0D, 120, 0.65D, 0.75D);
        UUID riverPlayer = UUID.fromString(
                "00000000-0000-0000-0000-000000000044");
        TravelingWaveRegistry.Update riverStart = registry.update(
                riverPlayer,
                channelTopology.worldId(), 0, 0, 0L, riverProfile,
                OvalWaveSettings.defaults(), 1.0D, 0.0D,
                radius, radius, channelTopology);
        require(!riverStart.fronts().isEmpty(),
                "small enclosed water must resolve a stable wave source");
        require(riverStart.lifecycle().steeringTransitions().stream()
                        .anyMatch(event -> event.contains("[SOURCE][ENCLOSED-WATER]")
                                && event.contains("bank-span=39")),
                "a thirty-nine-block channel must shorten its initial wave approach");
        require(riverStart.fronts().stream().anyMatch(front ->
                        channelTopology.cell((int) Math.round(front.x()),
                                (int) Math.round(front.z())).shoreDistance() <= 12),
                "small-water fronts must begin within the twelve-block approach cap");
        require(riverStart.lifecycle().steeringTransitions().stream()
                        .anyMatch(event -> event.contains("[STEER][CHANNEL-LOCK]")),
                "the enclosed channel must record one-direction anchoring");
        require(riverStart.fronts().stream().allMatch(front ->
                        front.channelCourseLocked()
                                && !front.openWaterFan()
                                && !front.hasShoreTarget()
                                && front.headingX() > 0.999D
                                && Math.abs(front.headingZ()) < 0.001D),
                "all fronts from one river source must share the same channel direction");
        double startAverageX = riverStart.fronts().stream()
                .mapToDouble(TravelingWaveFront::x).average().orElseThrow();
        double startAverageZ = riverStart.fronts().stream()
                .mapToDouble(TravelingWaveFront::z).average().orElseThrow();
        TravelingWaveRegistry.Update riverNext = registry.update(
                riverPlayer, channelTopology.worldId(), 0, 0, 20L, riverProfile,
                OvalWaveSettings.defaults(), 1.0D, 0.0D,
                radius, radius, channelTopology);
        double nextAverageX = riverNext.fronts().stream()
                .mapToDouble(TravelingWaveFront::x).average().orElseThrow();
        double nextAverageZ = riverNext.fronts().stream()
                .mapToDouble(TravelingWaveFront::z).average().orElseThrow();
        require(nextAverageX > startAverageX + 1.0D
                        && Math.abs(nextAverageZ - startAverageZ) < 0.001D,
                "channel-locked fronts must travel straight without lateral spin");
        TravelingWaveFront tight = new TravelingWaveFront(
                42L, TravelingWaveFront.Kind.GIANT, 0.0D,
                0.0D, 0.0D, 1.0D, 0.0D,
                22.0D, 44.0D, 0.98D, 0L);
        require(tight.lockNarrowPassage(12),
                "a giant front wider than its water body must enter compact mode");
        require(!tight.lockNarrowPassage(7),
                "tight-water classification must lock once instead of fluctuating");
        TravelingWaveFront crowded = new TravelingWaveFront(
                43L, TravelingWaveFront.Kind.GIANT, 0.0D,
                0.0D, 0.0D, 1.0D, 0.0D,
                22.0D, 44.0D, 0.98D, 0L);
        require(crowded.lockNarrowPassage(12, 4),
                "four fronts in narrow water must enter crowd-compacted mode");
        require(crowded.passageCrowding() == 4
                        && crowded.passageHalfWidth() <= 3.50D,
                "crowded tight-water waves must become much smaller than lone waves");
        require(crowded.passageHalfWidth() < tight.passageHalfWidth() * 0.50D,
                "density compaction must materially reduce the fitted footprint");

        double wideBefore = tight.halfWidth();
        for (long tick = 5L; tick <= 40L; tick += 5L) {
            TravelingWaveFront.Motion motion = tight.prepareMotion(
                    tick, 1.0D, 1.0D, 0.0D, false);
            tight.commitMotion(motion);
        }
        require(tight.halfWidth() < wideBefore * 0.45D,
                "a tight-water front must ease into a materially smaller footprint");
        require(tight.narrowPassageLocked(),
                "the compact passage mode must remain stable after classification");

        System.out.println("Tight-water steering smoke test passed: shore-progress=true"
                + " compact-half-width=" + round(tight.halfWidth())
                + " crowded-target=" + round(crowded.passageHalfWidth())
                + " enclosed-start=true"
                + " shared-channel-direction=true"
                + " locked-course=true");
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
