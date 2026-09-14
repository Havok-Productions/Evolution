package org.evolution.features.treeevolution;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * ## Read-only audit for persisted live tree DNA near reported coordinates.
 *
 * <p>The tool compares owned source/evolved voxels with the deterministic
 * target and runs the same planner-independent deformation scorer used by
 * production anomaly bundles. It never opens a world or changes plugin data.</p>
 */
public final class TreePersistedShapeAuditTool {
    private TreePersistedShapeAuditTool() {
    }

    public static void main(String[] args) {
        if (args.length < 4 || (args.length - 1) % 3 != 0) {
            throw new IllegalArgumentException(
                    "usage: <tree-evolution.yml> <x> <y> <z> [...]");
        }
        List<Point> reports = new ArrayList<>();
        for (int index = 1; index < args.length; index += 3) {
            reports.add(new Point(
                    Integer.parseInt(args[index]),
                    Integer.parseInt(args[index + 1]),
                    Integer.parseInt(args[index + 2])));
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new File(args[0]));
        ConfigurationSection trees = yaml.getConfigurationSection("trees");
        if (trees == null) {
            throw new IllegalStateException("trees section is missing");
        }
        List<AuditRow> rows = new ArrayList<>();
        for (String key : trees.getKeys(false)) {
            ConfigurationSection section = trees.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            TreeDna dna = TreeDnaCodec.read(section);
            if (reports.stream().noneMatch(point -> point.horizontalDistance(
                    dna.baseX(), dna.baseZ()) <= 96.0D)) {
                continue;
            }
            rows.add(audit(key, dna));
        }

        for (Point report : reports) {
            System.out.println("REPORT " + report);
            List<AuditRow> nearest = rows.stream().sorted(
                            Comparator.comparingDouble(
                            row -> report.horizontalDistance(
                                    row.dna.baseX(), row.dna.baseZ())))
                    .limit(5).toList();
            nearest.forEach(row -> System.out.println("  "
                    + row.summary(report)));
            if (!nearest.isEmpty()) {
                System.out.println("  " + nearest.getFirst().replay());
            }
        }
        System.out.println("NEARBY ANOMALIES");
        rows.stream().filter(AuditRow::anomalous)
                .sorted(Comparator.comparingInt(AuditRow::severity).reversed())
                .forEach(row -> System.out.println("  " + row.summary(null)));
    }

    private static AuditRow audit(String persistedKey, TreeDna dna) {
        TreePlan target = TreeShapeSmokeTest.treeBodyPlan(dna);
        Map<String, PlannedTreeBlock> plan = target.blocksByKey();
        Map<TreeBlockRole, Integer> targetRoles = new LinkedHashMap<>();
        target.orderedBlocks().forEach(block -> targetRoles.merge(
                block.role(), 1, Integer::sum));
        Set<String> currentLogs = new HashSet<>(dna.originalShapeLogs());
        currentLogs.addAll(dna.evolvedShapeLogs());
        Set<String> currentLeaves = new HashSet<>(dna.originalShapeLeaves());
        currentLeaves.removeAll(dna.retiredOriginalShapeLeaves());
        currentLeaves.addAll(dna.evolvedShapeLeaves());

        Set<String> currentCoordinates = new HashSet<>();
        currentLogs.forEach(key -> currentCoordinates.add(coordinate(key)));
        currentLeaves.forEach(key -> currentCoordinates.add(coordinate(key)));
        Set<String> targetCoordinates = plan.keySet();
        int missing = 0;
        Map<TreeBlockRole, Integer> missingRoles = new LinkedHashMap<>();
        for (PlannedTreeBlock block : target.orderedBlocks()) {
            boolean present = switch (block.role()) {
                case TRUNK, BRANCH, ROOT -> currentLogs.stream().anyMatch(
                        receipt -> coordinate(receipt).equals(block.key()));
                case CANOPY -> currentLeaves.stream().anyMatch(
                        receipt -> coordinate(receipt).equals(block.key()));
                default -> true;
            };
            if (!present) {
                missing++;
                missingRoles.merge(block.role(), 1, Integer::sum);
            }
        }
        Set<String> extraLogs = new HashSet<>();
        for (String receipt : currentLogs) {
            String coordinate = coordinate(receipt);
            PlannedTreeBlock planned = plan.get(coordinate);
            if (planned == null || (planned.role() != TreeBlockRole.TRUNK
                    && planned.role() != TreeBlockRole.BRANCH
                    && planned.role() != TreeBlockRole.ROOT)) {
                extraLogs.add(coordinate);
            }
        }
        Set<String> extraLeaves = new HashSet<>();
        for (String receipt : currentLeaves) {
            String coordinate = coordinate(receipt);
            PlannedTreeBlock planned = plan.get(coordinate);
            if (planned == null || planned.role() != TreeBlockRole.CANOPY) {
                extraLeaves.add(coordinate);
            }
        }

        List<TreeVoxelSnapshotRenderer.Voxel> voxels = new ArrayList<>();
        for (String receipt : currentLogs) {
            int[] point = absolute(receipt);
            if (point == null) {
                continue;
            }
            String coordinate = point[0] + ":" + point[1] + ":" + point[2];
            PlannedTreeBlock planned = plan.get(coordinate);
            TreeBlockRole role = planned != null
                    && planned.role() != TreeBlockRole.CANOPY
                    ? planned.role() : inferredWoodRole(dna, point);
            voxels.add(new TreeVoxelSnapshotRenderer.Voxel(
                    point[0] - dna.baseX(), point[1] - dna.baseY(),
                    point[2] - dna.baseZ(), role,
                    dna.species().logMaterial().name()));
        }
        for (String receipt : currentLeaves) {
            int[] point = absolute(receipt);
            if (point != null) {
                voxels.add(new TreeVoxelSnapshotRenderer.Voxel(
                        point[0] - dna.baseX(), point[1] - dna.baseY(),
                        point[2] - dna.baseZ(), TreeBlockRole.CANOPY,
                        dna.species().leafMaterial().name()));
            }
        }
        boolean completeSnapshot = !voxels.isEmpty()
                && !dna.hasOriginalShapeSnapshot();
        TreeDeformationAudit.Report visual = new TreeDeformationAudit()
                .inspect(dna, voxels, completeSnapshot);
        List<TreeVoxelSnapshotRenderer.Voxel> targetVoxels =
                target.orderedBlocks().stream()
                        .filter(block -> block.role()
                                        == TreeBlockRole.TRUNK
                                || block.role() == TreeBlockRole.BRANCH
                                || block.role() == TreeBlockRole.CANOPY
                                || block.role() == TreeBlockRole.ROOT)
                        .map(block -> new TreeVoxelSnapshotRenderer.Voxel(
                                block.x() - dna.baseX(),
                                block.y() - dna.baseY(),
                                block.z() - dna.baseZ(),
                                block.role(), block.material().name()))
                        .toList();
        // ## Score the immutable recipe independently from current progress.
        // A target failure is a planner defect; a current-only failure is a
        // transition or ownership defect.
        TreeDeformationAudit.Report targetVisual =
                new TreeDeformationAudit().inspect(
                        dna, targetVoxels, true);
        Optional<String> capturedDrift =
                TreeCapturedShapeDriftAudit.inspect(dna, target);
        return new AuditRow(persistedKey, dna, target.size(), targetRoles,
                currentCoordinates.size(), missing, missingRoles,
                extraLogs.size(), extraLeaves.size(), visual, targetVisual,
                capturedDrift.orElse("none"));
    }

    private static TreeBlockRole inferredWoodRole(TreeDna dna, int[] point) {
        int trunkDistance = Math.max(
                Math.abs(point[0] - dna.trunkXAt(point[1])),
                Math.abs(point[2] - dna.trunkZAt(point[1])));
        return trunkDistance <= Math.max(0,
                TreeSpeciesStageStyle.trunkWidthAt(dna, point[1]) / 2)
                ? TreeBlockRole.TRUNK : TreeBlockRole.BRANCH;
    }

    private static String coordinate(String receipt) {
        int[] point = absolute(receipt);
        return point == null ? receipt
                : point[0] + ":" + point[1] + ":" + point[2];
    }

    private static int[] absolute(String receipt) {
        String[] split = receipt.split(":");
        if (split.length < 4) {
            return null;
        }
        try {
            int offset = split.length - 3;
            return new int[]{Integer.parseInt(split[offset]),
                    Integer.parseInt(split[offset + 1]),
                    Integer.parseInt(split[offset + 2])};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record Point(int x, int y, int z) {
        double horizontalDistance(int otherX, int otherZ) {
            return Math.hypot(otherX - x, otherZ - z);
        }

        @Override
        public String toString() {
            return x + "," + y + "," + z;
        }
    }

    private record AuditRow(
            String persistedKey,
            TreeDna dna,
            int targetBlocks,
            Map<TreeBlockRole, Integer> targetRoles,
            int ownedBlocks,
            int missing,
            Map<TreeBlockRole, Integer> missingRoles,
            int extraLogs,
            int extraLeaves,
            TreeDeformationAudit.Report visual,
            TreeDeformationAudit.Report targetVisual,
            String capturedDrift
    ) {
        boolean anomalous() {
            return missing > 0 || extraLogs > 0 || extraLeaves > 0
                    || !visual.passed() || !targetVisual.passed()
                    || !"none".equals(capturedDrift);
        }

        int severity() {
            return missing + extraLogs * 3 + extraLeaves
                    + visual.failures().size() * 20
                    + targetVisual.failures().size() * 30;
        }

        String summary(Point report) {
            String distance = report == null ? ""
                    : " distance=" + Math.round(report.horizontalDistance(
                            dna.baseX(), dna.baseZ()) * 10.0D) / 10.0D;
            return "tree=" + persistedKey + " base=" + dna.baseX() + ","
                    + dna.baseY() + "," + dna.baseZ() + distance
                    + " " + dna.species().id() + "/" + dna.variant().id()
                    + "/" + dna.maturityStage()
                    + " target=" + targetBlocks + targetRoles
                    + " owned=" + ownedBlocks
                    + " missing=" + missing + missingRoles
                    + " extra-wood=" + extraLogs
                    + " extra-leaves=" + extraLeaves
                    + " visual=" + visual.failures()
                    + " target-visual=" + targetVisual.failures()
                    + " drift=" + capturedDrift;
        }

        String replay() {
            TreeDna rebased = TreePersistedFixtureExporter.rebase(dna);
            TreeConstructionReplayHarness.ReplayResult result =
                    new TreeConstructionReplayHarness(
                            "reported-" + persistedKey,
                            rebased, false).run();
            return "current-constructor-replay steps=" + result.steps()
                    + " mutations=" + result.physicalMutations()
                    + " final-exact=" + result.finalDiff().exact()
                    + " final-diff=" + result.finalDiff().csv();
        }
    }
}
