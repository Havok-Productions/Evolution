package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Proxy;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.evolution.features.treeevolution.constructor.TreeConstructionRuleId;

/** ## Verifies the planner-independent deformation evidence pipeline. */
public final class TreeDeformationDiagnosticsSmokeTest {
    private TreeDeformationDiagnosticsSmokeTest() {
    }

    public static void main(String[] args) {
        TreeDeformationAudit audit = new TreeDeformationAudit();
        int healthyScenarios = 0;
        for (TreeSpecies species : TreeSpecies.values()) {
            for (TreeMaturityStage stage : TreeMaturityStage.values()) {
                for (int seed = 0; seed < 3; seed++) {
                    TreeDna sample = TreeShapeSmokeTest.sampleDna(
                            species, stage, seed);
                    TreePlan samplePlan =
                            TreeShapeSmokeTest.treeBodyPlan(sample);
                    TreeDeformationAudit.Report report = audit.inspect(
                            sample, voxels(sample, samplePlan), true);
                    require(report.passed(), species + " " + stage + " v"
                            + seed + " failed independent deformation audit: "
                            + report.failures() + " " + report.metrics());
                    assertAugments(samplePlan);
                    healthyScenarios++;
                }
            }
        }

        TreeDna dna = TreeShapeSmokeTest.sampleDna(
                TreeSpecies.OAK, TreeMaturityStage.MATURE, 2);
        TreePlan plan = TreeShapeSmokeTest.treeBodyPlan(dna);
        List<TreeVoxelSnapshotRenderer.Voxel> healthy = voxels(dna, plan);

        TreeDeformationAudit.Report diagonalSupport = audit.inspect(
                TreeSpecies.OAK, TreeVariant.OAK_FANCY,
                TreeMaturityStage.SMALL,
                List.of(
                        new TreeVoxelSnapshotRenderer.Voxel(
                                0, 0, 0, TreeBlockRole.TRUNK,
                                Material.OAK_LOG.name()),
                        new TreeVoxelSnapshotRenderer.Voxel(
                                1, 1, 0, TreeBlockRole.BRANCH,
                                Material.OAK_LOG.name()),
                        new TreeVoxelSnapshotRenderer.Voxel(
                                2, 1, 0, TreeBlockRole.CANOPY,
                                Material.OAK_LEAVES.name())),
                false);
        require(diagonalSupport.passed(),
                "audit rejected constructor-valid diagonal support: "
                        + diagonalSupport.failures());

        TreeTargetConformanceAudit conformanceAudit =
                new TreeTargetConformanceAudit();
        TreeTargetConformanceAudit.Report exact = conformanceAudit.inspect(
                healthy, healthy, Set.of());
        require(exact.passed(), "identical current and target did not conform");
        List<TreeVoxelSnapshotRenderer.Voxel> drifted =
                new ArrayList<>(healthy.subList(1, healthy.size()));
        drifted.add(new TreeVoxelSnapshotRenderer.Voxel(
                28, 8, 28, TreeBlockRole.BRANCH,
                dna.species().logMaterial().name()));
        TreeTargetConformanceAudit.Report drift = conformanceAudit.inspect(
                drifted, healthy, Set.of());
        require(!drift.passed() && drift.missingTarget() == 1
                        && drift.extraCurrent() == 1,
                "exact target drift was not separated from silhouette quality: "
                        + drift.asMap());

        List<TreeVoxelSnapshotRenderer.Voxel> deformed =
                new ArrayList<>(healthy);
        deformed.add(new TreeVoxelSnapshotRenderer.Voxel(
                30, 8, 30, TreeBlockRole.BRANCH,
                dna.species().logMaterial().name()));
        TreeDeformationAudit.Report deformedReport =
                audit.inspect(dna, deformed, true);
        require(!deformedReport.passed(),
                "detached branch escaped independent audit");
        require(deformedReport.failures().stream()
                        .anyMatch(value -> value.startsWith(
                                "disconnected-wood=")),
                "detached branch did not report wood disconnection");

        Map<String, Object> snapshot =
                TreeVoxelSnapshotRenderer.snapshot("smoke", deformed);
        require(snapshot.containsKey("points"),
                "snapshot omitted voxel points");
        Object rawViews = snapshot.get("views");
        require(rawViews instanceof Map<?, ?> views && views.size() == 4,
                "snapshot must include north/south/east/west views");

        TreeDeformationHistoryStore history =
                new TreeDeformationHistoryStore();
        Block coordinate = blockAt(dna.baseX() + 2,
                dna.baseY() + 5, dna.baseZ() - 1);
        boolean churn = false;
        for (int index = 0; index < 4; index++) {
            boolean place = index % 2 == 0;
            churn = history.recordMutation(
                    dna, coordinate,
                    place ? Material.AIR : dna.species().leafMaterial(),
                    place ? dna.species().leafMaterial() : Material.AIR,
                    TreeBlockRole.CANOPY,
                    TreePlacementAugment.CANOPY_BRANCH_INTEGRATION,
                    "[SMOKE][TREE_41]", place ? "PLACE" : "REMOVE",
                    "churn-smoke");
        }
        require(churn, "four alternating coordinate mutations did not "
                + "trigger churn evidence");
        require(!history.hotCoordinates(dna.key()).isEmpty(),
                "churning coordinate omitted its bounded history");
        assertConstructorTranslation();

        System.out.println("Tree deformation diagnostics smoke test passed: "
                + "healthy-scenarios=" + healthyScenarios
                + " voxels=" + healthy.size() + " failures="
                + deformedReport.failures());
    }

    private static void assertConstructorTranslation() {
        Map<String, Object> translation =
                TreeConstructorDebugTranslator.translation();
        require(translation.get("constructor-rules")
                        instanceof List<?> rules
                        && rules.size()
                                == TreeConstructionRuleId.values().length,
                "constructor translator omitted a hierarchy rule");
        require(translation.get("formation-augments")
                        instanceof List<?> augments
                        && augments.size()
                                == TreePlacementAugment.values().length,
                "constructor translator omitted a planner augment");
        require(translation.get("anomaly-triggers")
                        instanceof List<?> triggers
                        && !triggers.isEmpty(),
                "constructor translator omitted anomaly classifications");
    }

    private static void assertAugments(TreePlan plan) {
        for (PlannedTreeBlock block : plan.orderedBlocks()) {
            require(block.augment() != TreePlacementAugment.UNCLASSIFIED,
                    "planner coordinate lacks augment at " + block.key());
            require(block.augment().accepts(block.role()),
                    "augment/role mismatch at " + block.key() + " "
                            + block.augment() + " -> " + block.role());
        }
    }

    private static List<TreeVoxelSnapshotRenderer.Voxel> voxels(
            TreeDna dna, TreePlan plan
    ) {
        return plan.orderedBlocks().stream()
                .map(block -> new TreeVoxelSnapshotRenderer.Voxel(
                        block.x() - dna.baseX(),
                        block.y() - dna.baseY(),
                        block.z() - dna.baseZ(),
                        block.role(), block.material().name()))
                .toList();
    }

    private static Block blockAt(int x, int y, int z) {
        return (Block) Proxy.newProxyInstance(
                Block.class.getClassLoader(), new Class<?>[]{Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getX" -> x;
                    case "getY" -> y;
                    case "getZ" -> z;
                    case "toString" -> "smoke-block(" + x + "," + y
                            + "," + z + ")";
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
