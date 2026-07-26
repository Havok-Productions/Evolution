package org.evolution.features.waves;

import java.util.List;
import java.util.UUID;

public final class WaveStaticSourceSmokeTest {
    private WaveStaticSourceSmokeTest() {
    }

    public static void main(String[] args) {
        int radius = 120;
        int diameter = (radius * 2) + 1;
        boolean[] known = new boolean[diameter * diameter];
        boolean[] water = new boolean[diameter * diameter];
        int waterCells = 0;
        for (int localZ = 0; localZ < diameter; localZ++) {
            int z = localZ - radius;
            for (int localX = 0; localX < diameter; localX++) {
                int x = localX - radius;
                int index = (localZ * diameter) + localX;
                known[index] = true;
                water[index] = (x * x) + (z * z) <= 104 * 104;
                waterCells += water[index] ? 1 : 0;
            }
        }

        UUID worldId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID firstPlayer = UUID.fromString("00000000-0000-0000-0000-000000000012");
        UUID secondPlayer = UUID.fromString("00000000-0000-0000-0000-000000000013");
        LakeWaveFlowField field = LakeWaveFlowField.build(diameter, diameter, known, water);
        WaveLakeFlowCache.Snapshot topology = new WaveLakeFlowCache.Snapshot(
                worldId, 0, 0, radius, radius, 1, 0L,
                known.length, waterCells, field);
        WaveProfile profile = new WaveProfile(
                0.90D, 2.0D, 36, 2.2D, 0.5D, 1.0D, 160, 0.65D, 0.75D);
        TravelingWaveRegistry registry = new TravelingWaveRegistry();

        TravelingWaveRegistry.Update first = registry.update(
                firstPlayer, worldId, 0, 0, 0L, profile, OvalWaveSettings.defaults(),
                1.0D, 0.0D, 100, radius, topology);
        TravelingWaveRegistry.Update second = registry.update(
                secondPlayer, worldId, 12, -8, 0L, profile, OvalWaveSettings.defaults(),
                1.0D, 0.0D, 100, radius, topology);
        require(first.sources().activeSources() > 0,
                "a known body of water must resolve a world-fixed source");
        require(first.sources().anchors().equals(second.sources().anchors()),
                "nearby players must resolve the same static water-source anchors");
        require(signatures(first.fronts()).equals(signatures(second.fronts())),
                "players viewing the same water must receive identical front identities");

        TravelingWaveRegistry.Update movedFirst = registry.update(
                firstPlayer, worldId, 28, 14, 20L, profile, OvalWaveSettings.defaults(),
                1.0D, 0.0D, 100, radius, topology);
        TravelingWaveRegistry.Update movedSecond = registry.update(
                secondPlayer, worldId, 12, -8, 20L, profile, OvalWaveSettings.defaults(),
                1.0D, 0.0D, 100, radius, topology);
        require(signatures(movedFirst.fronts()).equals(signatures(movedSecond.fronts())),
                "moving a player must not move, reseed, or double-advance shared fronts");

        registry.remove(firstPlayer);
        TravelingWaveRegistry.Update afterLeave = registry.update(
                secondPlayer, worldId, 12, -8, 25L, profile, OvalWaveSettings.defaults(),
                1.0D, 0.0D, 100, radius, topology);
        require(ids(movedSecond.fronts()).equals(ids(afterLeave.fronts())),
                "one viewer leaving must not delete the world's source state");

        List<TravelingWaveRegistry.SourceCoordinate> gridA =
                TravelingWaveRegistry.sourceCoordinatesNear(worldId, 0, 0, radius);
        List<TravelingWaveRegistry.SourceCoordinate> gridB =
                TravelingWaveRegistry.sourceCoordinatesNear(worldId, 0, 0, radius);
        require(gridA.equals(gridB) && gridA.stream().anyMatch(source ->
                        source.centerX() == 0 && source.centerZ() == 0),
                "source discovery must be deterministic and anchored to world coordinates");

        System.out.println("Wave static-source smoke test passed: sources="
                + first.sources().summary() + " shared-fronts=" + first.fronts().size());
    }

    private static List<String> signatures(List<TravelingWaveFront> fronts) {
        return fronts.stream().map(front -> front.id() + "@"
                + round(front.x()) + "," + round(front.z())).sorted().toList();
    }

    private static List<Long> ids(List<TravelingWaveFront> fronts) {
        return fronts.stream().map(TravelingWaveFront::id).sorted().toList();
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
