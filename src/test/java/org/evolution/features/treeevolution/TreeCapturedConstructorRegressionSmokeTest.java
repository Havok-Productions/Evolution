package org.evolution.features.treeevolution;

import java.nio.file.Path;

/**
 * ## Fast exact-volume constructor replay without PNG rendering.
 *
 * <p>Use this first for reported live coordinates. The full replay renderer
 * remains available after constructor termination is proven.</p>
 */
public final class TreeCapturedConstructorRegressionSmokeTest {
    private TreeCapturedConstructorRegressionSmokeTest() {
    }

    public static void main(String[] args) {
        int scenarios = 0;
        java.util.List<TreeCapturedFixtureStore.Fixture> fixtures =
                args.length == 0
                        ? TreeCapturedFixtureStore
                                .loadRequiredLiveVolumes()
                        : java.util.Arrays.stream(args)
                                .flatMap(argument ->
                                        TreeCapturedFixtureStore.load(
                                                Path.of(argument)).stream())
                                .toList();
        for (TreeCapturedFixtureStore.Fixture fixture : fixtures) {
                if (!fixture.environment().present()) {
                    throw new IllegalStateException(
                            "captured regression lacks a voxel environment: "
                                    + fixture.id());
                }
                TreeDna normalized = new TreeDnaNormalizer().normalize(
                        fixture.dna(), fixture.dna().maturityStage()).dna();
                TreeConstructionReplayHarness.ReplayResult result =
                        new TreeConstructionReplayHarness(
                                fixture.id(), normalized, false,
                                TreeSimulatedMinecraftEnvironment
                                        .permissive(),
                                fixture.environment()).run();
                if (!result.finalDiff().exact()) {
                    throw new IllegalStateException(
                            "captured replay did not reach exact target: "
                                    + fixture.id() + " "
                                    + result.finalDiff().csv());
                }
                scenarios++;
        }
        System.out.println(
                "Captured constructor regression smoke test passed: "
                        + "scenarios=" + scenarios
                        + " exact-target=true live-volume-required=true render=false");
    }
}
