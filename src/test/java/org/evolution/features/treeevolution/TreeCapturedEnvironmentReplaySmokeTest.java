package org.evolution.features.treeevolution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.evolution.features.treeevolution.TreeCapturedEnvironmentFixture.Category;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Ownership;

/**
 * ## Proves that exported local Minecraft facts survive fixture replay.
 */
public final class TreeCapturedEnvironmentReplaySmokeTest {
    private TreeCapturedEnvironmentReplaySmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        replaysSparseLiveVolumeThroughProductionHierarchy();
        scopesCapturedFoliaPauseToItsLocalVolume();
        exportsOnlyAnonymizedRelativeVolumeFacts();
        System.out.println("Captured environment replay smoke passed");
    }

    private static void replaysSparseLiveVolumeThroughProductionHierarchy() {
        TreeDna dna = TreeShapeSmokeTest.sampleDna(
                TreeSpecies.OAK, TreeMaturityStage.SMALL, 1);
        TreePlan plan = TreeShapeSmokeTest.treeBodyPlan(dna);
        PlannedTreeBlock replaceableTarget = plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.CANOPY)
                .findFirst()
                .orElseThrow();
        int maximumX = plan.orderedBlocks().stream()
                .mapToInt(PlannedTreeBlock::x).max().orElse(dna.baseX());
        int neighborX = maximumX + 3;
        int neighborY = dna.baseY() + 2;
        int neighborZ = dna.baseZ();

        TreeCapturedEnvironmentFixture fixture =
                new TreeCapturedEnvironmentFixture(
                        List.of(
                                new TreeCapturedEnvironmentFixture.Cell(
                                        replaceableTarget.x() - dna.baseX(),
                                        replaceableTarget.y() - dna.baseY(),
                                        replaceableTarget.z() - dna.baseZ(),
                                        Material.VINE,
                                        Category.ENVIRONMENT,
                                        null, true, true, false),
                                new TreeCapturedEnvironmentFixture.Cell(
                                        neighborX - dna.baseX(),
                                        neighborY - dna.baseY(),
                                        neighborZ - dna.baseZ(),
                                        Material.OAK_LOG,
                                        Category.NEIGHBOR,
                                        TreeBlockRole.TRUNK,
                                        true, false, false)),
                        List.of());
        TreeConstructionReplayHarness.ReplayResult result =
                new TreeConstructionReplayHarness(
                        "captured-local-volume", dna, false,
                        TreeSimulatedMinecraftEnvironment.permissive(),
                        fixture).run();

        String neighborKey = neighborX + ":" + neighborY + ":" + neighborZ;
        require(result.initialCells().get(neighborKey) != null
                        && result.initialCells().get(neighborKey).ownership()
                                == Ownership.NEIGHBOR,
                "captured neighboring tree lost its immutable ownership");
        require(result.environmentReport().trace().stream()
                        .anyMatch(line -> line.contains("replace-natural")
                                && line.contains("VINE->")),
                "production replay did not consume captured replaceable terrain");
    }

    private static void scopesCapturedFoliaPauseToItsLocalVolume() {
        TreeSimulatedMinecraftEnvironment environment =
                TreeSimulatedMinecraftEnvironment.permissive();
        environment.temporarilyUnavailable(
                new TreeSimulatedMinecraftEnvironment.Box(
                        4, 60, 4, 8, 90, 8),
                2L,
                TreeSimulatedMinecraftEnvironment.Gate.REGION_NOT_OWNED);
        require(environment.canSchedule(5, 64, 5).gate()
                        == TreeSimulatedMinecraftEnvironment.Gate
                                .REGION_NOT_OWNED,
                "captured Folia gate did not block its local volume");
        require(environment.canSchedule(0, 64, 0).allowed(),
                "captured Folia gate leaked outside its local volume");
        environment.advanceTick();
        environment.advanceTick();
        require(environment.canSchedule(5, 64, 5).allowed(),
                "captured Folia gate did not release for deterministic retry");
    }

    private static void exportsOnlyAnonymizedRelativeVolumeFacts()
            throws Exception {
        Path directory = Path.of(
                "target", "captured-environment-export-smoke");
        Files.createDirectories(directory);
        Path dnaPath = directory.resolve("tree-evolution.yml");
        Path volumePath = directory.resolve("tree-3d-debug.yml");
        Path fixturePath = directory.resolve("fixture.yml");

        TreeDna dna = TreeShapeSmokeTest.sampleDna(
                TreeSpecies.OAK, TreeMaturityStage.SMALL, 2);
        String prefix = dna.worldId() + ":";
        dna.captureOriginalShape(
                List.of(prefix + "0:64:0", prefix + "0:65:0"),
                List.of(prefix + "0:66:0", prefix + "1:66:0"));
        YamlConfiguration atomicSnapshot = new YamlConfiguration();
        dna.writeTo(atomicSnapshot.createSection("dna"));
        String capturedDnaYaml = atomicSnapshot.saveToString();
        // ## Simulate the live repository advancing after the voxel capture.
        // Export must use the DNA paired with the volume, not this later save.
        dna.markEvolvedBlock(prefix + "1:66:0", TreeBlockRole.CANOPY);
        YamlConfiguration source = new YamlConfiguration();
        dna.writeTo(source.createSection("trees")
                .createSection("captured-tree"));
        source.save(dnaPath.toFile());

        LinkedHashMap<String, Object> cell = new LinkedHashMap<>();
        cell.put("x", 3);
        cell.put("y", 2);
        cell.put("z", -1);
        cell.put("material", "OAK_LOG");
        cell.put("category", "PLANNED_UNOWNED");
        cell.put("role", "TRUNK");
        cell.put("natural", true);
        cell.put("replaceable", false);
        cell.put("player-placed", false);
        cell.put("world", "private-world-marker");
        LinkedHashMap<String, Object> capture = new LinkedHashMap<>();
        capture.put("tree", dna.key());
        capture.put("captured-at", "private-timestamp-marker");
        capture.put("source-path", "private-path-marker");
        capture.put("dna-snapshot-yaml", capturedDnaYaml);
        capture.put("cells", List.of(cell));
        capture.put("unreadable-areas", List.of());
        YamlConfiguration volume = new YamlConfiguration();
        volume.set("live-voxel-captures", List.of(capture));
        volume.save(volumePath.toFile());

        TreePersistedFixtureExporter.main(new String[]{
                dnaPath.toString(), fixturePath.toString(),
                "--volume=" + volumePath});
        List<TreeCapturedFixtureStore.Fixture> fixtures =
                TreeCapturedFixtureStore.load(fixturePath);
        require(fixtures.size() == 1
                        && fixtures.getFirst().environment().cells().size()
                                == 1,
                "exported local volume did not round-trip into replay");
        require(fixtures.getFirst().dna().evolvedShapeLeaves().isEmpty(),
                "exporter paired the volume with newer repository DNA instead of its atomic snapshot");
        TreeCapturedEnvironmentFixture.Cell exported =
                fixtures.getFirst().environment().cells().getFirst();
        require(exported.relativeX() == 3
                        && exported.relativeY() == 2
                        && exported.relativeZ() == -1
                        && exported.category()
                                == Category.PLANNED_UNOWNED,
                "exported local volume changed its relative voxel facts");
        String fixtureText = Files.readString(fixturePath);
        require(!fixtureText.contains(dna.worldId().toString())
                        && !fixtureText.contains("private-world-marker")
                        && !fixtureText.contains("private-timestamp-marker")
                        && !fixtureText.contains("private-path-marker"),
                "fixture exporter leaked a live identifier");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
