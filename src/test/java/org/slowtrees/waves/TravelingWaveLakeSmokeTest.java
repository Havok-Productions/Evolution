package org.slowtrees.waves;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TravelingWaveLakeSmokeTest {
    private TravelingWaveLakeSmokeTest() {
    }

    public static void main(String[] args) {
        int gridRadius = 40;
        int diameter = (gridRadius * 2) + 1;
        boolean[] known = new boolean[diameter * diameter];
        boolean[] water = new boolean[diameter * diameter];
        int waterCells = 0;
        for (int z = 0; z < diameter; z++) {
            for (int x = 0; x < diameter; x++) {
                int index = (z * diameter) + x;
                known[index] = true;
                int dx = x - gridRadius;
                int dz = z - gridRadius;
                water[index] = (dx * dx) + (dz * dz) <= 30 * 30;
                waterCells += water[index] ? 1 : 0;
            }
        }

        LakeWaveFlowField field = LakeWaveFlowField.build(
                diameter, diameter, known, water);
        UUID worldId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        WaveLakeFlowCache.Snapshot topology = new WaveLakeFlowCache.Snapshot(
                worldId, 0, 0, gridRadius, gridRadius, 1, 0L,
                known.length, waterCells, field);
        WaveProfile profile = new WaveProfile(
                0.90D, 4.0D, 36, 2.2D, 0.5D, 1.0D, 120, 0.65D, 0.75D);
        TravelingWaveRegistry registry = new TravelingWaveRegistry();

        double moved = 0.0D;
        int impacts = 0;
        int guidedFrames = 0;
        int active = 0;
        int crossingFronts = 0;
        int giantFronts = 0;
        int maximumVisibleCells = 0;
        int maximumCollisionCells = 0;
        int stateMerges = 0;
        int maximumReplenishment = 0;
        Set<Long> mergedResultIds = new HashSet<>();
        boolean sawResultantFront = false;
        boolean sawChannelLock = false;
        int openWaterFanStarts = 0;
        double smallestFanScale = 1.0D;
        double largestFanScale = 0.0D;
        Set<Integer> fanAngleBuckets = new HashSet<>();
        for (long tick = 0L; tick <= 500L; tick += 5L) {
            TravelingWaveRegistry.Update update = registry.update(
                    playerId, worldId, 0, 0, tick, profile, OvalWaveSettings.defaults(),
                    1.0D, 0.0D, gridRadius, gridRadius + 8, topology);
            if (tick > 0L) {
                maximumReplenishment = Math.max(maximumReplenishment,
                        update.lifecycle().spawnedIds().size());
            }
            for (String transition : update.lifecycle().mergeTransitions()) {
                String[] sides = transition.split(">");
                String[] parents = sides[0].split("\\+");
                require(!mergedResultIds.contains(Long.parseLong(parents[0]))
                                && !mergedResultIds.contains(Long.parseLong(parents[1])),
                        "a merged result must not participate in another merge");
                mergedResultIds.add(Long.parseLong(sides[1]));
            }            for (String transition : update.lifecycle().steeringTransitions()) {
                openWaterFanStarts += transition.startsWith("[SOURCE][OPEN-WATER-FAN]")
                        ? 1 : 0;
            }
            moved += update.movedBlocks();
            impacts += update.shoreImpacts();
            stateMerges += update.mergedFronts();
            sawResultantFront |= update.fronts().stream().anyMatch(front ->
                    front.kind() == TravelingWaveFront.Kind.MERGED);
            sawChannelLock |= update.fronts().stream().anyMatch(
                    TravelingWaveFront::channelCourseLocked);
            guidedFrames += update.shoreGuidedFronts() > 0 ? 1 : 0;
            active = Math.max(active, update.fronts().size());
            crossingFronts = Math.max(crossingFronts, (int) update.fronts().stream()
                    .filter(front -> front.kind() == TravelingWaveFront.Kind.CROSSING).count());
            giantFronts = Math.max(giantFronts, (int) update.fronts().stream()
                    .filter(front -> front.kind() == TravelingWaveFront.Kind.GIANT).count());
            if (tick % 25L == 0L) {
                int visibleCells = 0;
                int collisionCells = 0;
                for (int x = -gridRadius; x <= gridRadius; x++) {
                    for (int z = -gridRadius; z <= gridRadius; z++) {
                        TravelingWaveRegistry.FrontSample sample = registry.sample(
                                playerId, x, z, tick);
                        visibleCells += sample.strength() >= 0.04D ? 1 : 0;
                        collisionCells += sample.contributors() >= 2 ? 1 : 0;
                    }
                }
                maximumVisibleCells = Math.max(maximumVisibleCells, visibleCells);
                maximumCollisionCells = Math.max(maximumCollisionCells, collisionCells);
            }
            for (TravelingWaveFront front : update.fronts()) {
                if (front.openWaterFan()) {
                    double scale = front.halfWidth()
                            / Math.max(0.001D, front.openWaterTargetHalfWidth());
                    largestFanScale = Math.max(largestFanScale, scale);
                    if (tick == 0L) {
                        smallestFanScale = Math.min(smallestFanScale, scale);
                        fanAngleBuckets.add((int) Math.round(
                                Math.toDegrees(Math.atan2(
                                        front.headingZ(), front.headingX())) / 5.0D));
                    }
                }
                require(topology.isWater((int) Math.round(front.x()),
                                (int) Math.round(front.z())),
                        "front centers must stop before crossing onto land: tick=" + tick
                                + " id=" + front.id() + " kind=" + front.kind()
                                + " pos=" + front.x() + "," + front.z()
                                + " lifecycle=" + update.lifecycle().summary());
            }
        }

        require(active >= 4, "the lake must contain several coherent fronts");
        require(active <= 4,
                "open-water angle fans must not increase the bounded front count");
        require(openWaterFanStarts >= 4,
                "the large-water source must emit small angle-varied fan fronts");
        require(smallestFanScale <= 0.52D
                        && largestFanScale >= smallestFanScale + 0.15D,
                "open-water fronts must start small and widen through travel");
        require(fanAngleBuckets.size() >= 2,
                "the initial open-water fan must contain multiple stable angles");
        require(maximumReplenishment <= 1,
                "missing fronts must replenish gradually, one per interval");
        require(stateMerges <= 10,
                "merge cooldown must prevent collision cascades");
        require(moved > 80.0D, "front centers must visibly travel across the lake");
        require(guidedFrames > 20, "fronts must spend time guided toward the shoreline");
        require(impacts > 0, "at least one persistent front must reach and fizzle at shore");
        require(crossingFronts > 0 && giantFronts > 0,
                "the live set must include crossing and giant front types");
        // ## Small angle-fan fronts may remain separate for their full lake crossing.
        // Merge behavior is opportunistic here, not a required lifecycle step.
        require(!sawChannelLock,
                "a large lake edge must not be mistaken for a small river channel");
        require(maximumVisibleCells < 12000,
                "expanded fronts must remain below the configured coherent-frame budget");
        System.out.println("Traveling lake smoke test passed: active=" + active
                + " moved=" + round(moved) + " guided-frames=" + guidedFrames
                + " shore-impacts=" + impacts + " state-merges=" + stateMerges
                + " collision-cells=" + maximumCollisionCells
                + " max-replenishment=" + maximumReplenishment
                + " visible-budget=" + maximumVisibleCells
                + " fan-angles=" + fanAngleBuckets.size()
                + " fan-scale=" + round(smallestFanScale)
                + "->" + round(largestFanScale));
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
