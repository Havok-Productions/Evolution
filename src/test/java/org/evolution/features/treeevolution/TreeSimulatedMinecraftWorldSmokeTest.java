package org.evolution.features.treeevolution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * ## Runs real tree plans and constructor hierarchy in a deterministic world.
 */
public final class TreeSimulatedMinecraftWorldSmokeTest {
    private static final Path OUT = Path.of(
            "target", "tree-simulated-minecraft");

    private TreeSimulatedMinecraftWorldSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        resetOutput();
        List<String> summary = new ArrayList<>();
        summary.add("## Actual planner + constructor replay against "
                + "Minecraft-like world facts.");
        summary.add("scenario,species,variant,stage,actions,mutations,"
                + "simulatedTicks,biome,weather,unloadedGates,"
                + "ownershipGates,playerGates,visualPassed,finalVoxelExact");

        int scenarioIndex = 0;
        for (TreeSpecies species : TreeSpecies.values()) {
            TreeDna source = TreeShapeSmokeTest.sampleDna(
                    species, TreeMaturityStage.MEDIUM,
                    scenarioIndex % 6);
            TreeDna dna = new TreeDnaNormalizer()
                    .normalize(source, TreeMaturityStage.MEDIUM)
                    .dna();
            TreePlan plan = TreeShapeSmokeTest.treeBodyPlan(dna);
            TreeVisualQualityAudit.Report plannedVisual =
                    TreeVisualQualityAudit.auditPlan(dna, plan);
            require(plannedVisual.passed(),
                    "planner rejected before world simulation: "
                            + species + " "
                            + plannedVisual.failureSummary());

            TreeSimulatedMinecraftEnvironment environment =
                    TreeSimulatedMinecraftFixture.create(
                            dna, plan, scenarioIndex);
            String scenario = species.id() + "-"
                    + dna.variant().id() + "-medium-world";
            TreeConstructionReplayHarness.ReplayResult result =
                    new TreeConstructionReplayHarness(
                            scenario, dna, false, environment).run();
            TreeSimulatedMinecraftEnvironment.Report worldReport =
                    result.environmentReport();
            assertEnvironmentExercised(scenario, worldReport);

            TreeVisualQualityAudit.Report finalVisual =
                    result.visualReports().get("99-final");
            require(finalVisual != null && finalVisual.passed(),
                    scenario + " final world failed visual audit: "
                            + (finalVisual == null ? "missing-report"
                                    : finalVisual.failureSummary()));
            require(result.finalDiff().exact(),
                    scenario + " final world diverged from planner");

            Path scenarioDir = OUT.resolve(safeName(scenario));
            Files.createDirectories(scenarioDir);
            Files.writeString(
                    scenarioDir.resolve("environment-trace.csv"),
                    String.join(System.lineSeparator(),
                            worldReport.trace())
                            + System.lineSeparator());
            writeTerrain(
                    scenarioDir.resolve("terrain.csv"), environment);
            TreeReplayIsometricRenderer.renderAll(
                    scenarioDir,
                    "99-final",
                    scenario + " / simulated Minecraft final",
                    dna,
                    result.voxelSnapshots().get("99-final"),
                    finalVisual);

            summary.add(String.join(",",
                    scenario,
                    species.id(),
                    dna.variant().id(),
                    dna.maturityStage().name(),
                    String.valueOf(result.steps()),
                    String.valueOf(result.physicalMutations()),
                    String.valueOf(worldReport.simulatedTicks()),
                    worldReport.biome().id(),
                    worldReport.weather().name(),
                    String.valueOf(worldReport.count(
                            TreeSimulatedMinecraftEnvironment.Gate
                                    .CHUNK_UNLOADED)),
                    String.valueOf(worldReport.count(
                            TreeSimulatedMinecraftEnvironment.Gate
                                    .REGION_NOT_OWNED)),
                    String.valueOf(worldReport.count(
                            TreeSimulatedMinecraftEnvironment.Gate
                                    .NO_NEARBY_PLAYER)),
                    String.valueOf(finalVisual.passed()),
                    String.valueOf(result.finalDiff().exact())));
            scenarioIndex++;
        }

        Files.writeString(
                OUT.resolve("summary.csv"),
                String.join(System.lineSeparator(), summary)
                        + System.lineSeparator());
        System.out.println(
                "Simulated Minecraft tree smoke test passed: species="
                        + TreeSpecies.values().length
                        + " actual-planner=true actual-hierarchy=true "
                        + "block-by-block=true terrain=true biome=true "
                        + "weather=true light=true chunks=true "
                        + "folia-ownership=true players=true "
                        + "protection=true four-view-renders=true");
        System.out.println("Simulation artifacts: "
                + OUT.toAbsolutePath());
    }

    private static void assertEnvironmentExercised(
            String scenario,
            TreeSimulatedMinecraftEnvironment.Report report
    ) {
        require(report.count(
                        TreeSimulatedMinecraftEnvironment.Gate
                                .CHUNK_UNLOADED) > 0,
                scenario + " did not encounter an unloaded chunk");
        require(report.count(
                        TreeSimulatedMinecraftEnvironment.Gate
                                .REGION_NOT_OWNED) > 0,
                scenario + " did not encounter a Folia handoff");
        require(report.count(
                        TreeSimulatedMinecraftEnvironment.Gate
                                .NO_NEARBY_PLAYER) > 0,
                scenario + " did not encounter a player-range pause");
        require(report.count(
                        TreeSimulatedMinecraftEnvironment.Gate.ALLOWED) > 0,
                scenario + " did not perform a legal world mutation");
        require(report.terrainBlocks() > 100,
                scenario + " did not contain meaningful terrain");
        require(report.protectedVolumes() > 0,
                scenario + " did not contain protected terrain");
    }

    private static void writeTerrain(
            Path path,
            TreeSimulatedMinecraftEnvironment environment
    ) throws Exception {
        List<String> rows = new ArrayList<>();
        rows.add("x,y,z,material,natural,replaceable,playerPlaced");
        environment.terrain().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Coordinate coordinate = coordinate(entry.getKey());
                    TreeSimulatedMinecraftEnvironment.TerrainCell cell =
                            entry.getValue();
                    rows.add(String.join(",",
                            String.valueOf(coordinate.x()),
                            String.valueOf(coordinate.y()),
                            String.valueOf(coordinate.z()),
                            cell.material().name(),
                            String.valueOf(cell.natural()),
                            String.valueOf(cell.replaceable()),
                            String.valueOf(cell.playerPlaced())));
                });
        Files.writeString(path,
                String.join(System.lineSeparator(), rows)
                        + System.lineSeparator());
    }

    private static Coordinate coordinate(String key) {
        String[] parts = key.split(":");
        int offset = parts.length - 3;
        return new Coordinate(
                Integer.parseInt(parts[offset]),
                Integer.parseInt(parts[offset + 1]),
                Integer.parseInt(parts[offset + 2]));
    }

    private static void resetOutput() throws Exception {
        if (Files.exists(OUT)) {
            try (var paths = Files.walk(OUT)) {
                for (Path path : paths
                        .sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(OUT);
    }

    private static String safeName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Coordinate(int x, int y, int z) {
    }
}
