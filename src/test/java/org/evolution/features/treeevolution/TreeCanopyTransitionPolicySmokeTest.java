package org.evolution.features.treeevolution;

import java.util.List;
import java.util.UUID;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

public final class TreeCanopyTransitionPolicySmokeTest {
    private TreeCanopyTransitionPolicySmokeTest() {
    }

    public static void main(String[] args) {
        TreeDna dna = dna();
        List<PlannedTreeBlock> plan = List.of(
                new PlannedTreeBlock(0, 64, 0, Material.OAK_LOG,
                        TreeBlockRole.TRUNK, Axis.Y, null),
                new PlannedTreeBlock(2, 70, 0, Material.OAK_LOG,
                        TreeBlockRole.BRANCH, Axis.X, null),
                new PlannedTreeBlock(2, 71, 0, Material.OAK_LEAVES,
                        TreeBlockRole.CANOPY, Axis.Y, null),
                new PlannedTreeBlock(0, 72, 0, Material.OAK_LEAVES,
                        TreeBlockRole.CANOPY, Axis.Y, null));
        TreeCanopyTransitionPolicy policy =
                TreeCanopyTransitionPolicy.from(dna, plan);

        require(policy.preservesLeaf(2, 71, 0),
                "new target-canopy leaves must survive transition cleanup");
        require(policy.replacesWithWood(2, 70, 0),
                "old leaves blocking a planned branch must clear first");
        require(!policy.preservesLeaf(0, 68, 0),
                "old trunk-collar leaves must not be mistaken for the raised crown");
        require(policy.corridorMinimumY() == 66
                        && policy.corridorMaximumY() >= 72,
                "cleanup volume must include residual leaves across the former crown");
        require(policy.corridorRadius() >= 3,
                "cleanup corridor must cover the old vanilla crown width");
        require(TreeCanopyTransitionPolicy.minimumReplacementCanopy(dna) >= 0.70D,
                "medium trees must grow a substantial replacement crown before full shedding");
        double cleanupFloor =
                TreeCanopyTransitionPolicy.minimumReplacementCanopy(dna);
        require(!TreeCanopyTransitionPolicy.allowsBroadCleanup(
                        dna, cleanupFloor - 0.01D),
                "old crown leaves must remain until the replacement canopy reaches its floor");
        require(TreeCanopyTransitionPolicy.allowsBroadCleanup(
                        dna, cleanupFloor),
                "broad cleanup may begin after the replacement canopy is established");
        require(policy.minimumCanopyY() == 71 && policy.isLegacyShelf(68),
                "leaves below the target canopy must be prioritized as legacy shelves");
        TreeLeafOwnershipPolicy.Column neighbor =
                new TreeLeafOwnershipPolicy.Column(8, 0);
        require(TreeLeafOwnershipPolicy.belongsToActiveTree(
                        2, 0, 0, 0, List.of(neighbor)),
                "leaves nearer the active trunk must remain active-tree owned");
        require(!TreeLeafOwnershipPolicy.belongsToActiveTree(
                        6, 0, 0, 0, List.of(neighbor)),
                "leaves nearer a neighboring rooted trunk must not be pruned");
        require(!TreeLeafOwnershipPolicy.belongsToActiveTree(
                        4, 0, 0, 0, List.of(neighbor)),
                "shared canopy boundary leaves must remain untouched");
        require(!TreeLeafOwnershipPolicy.neighborPlanOwnsPosition(
                        2, 0, 0, 0, 8, 0),
                "a distant future canopy must not reserve leaves near the active trunk");
        require(TreeLeafOwnershipPolicy.neighborPlanOwnsPosition(
                        4, 0, 0, 0, 8, 0),
                "shared planned-canopy boundaries must remain protected");

        TreeDna legacySparse = dna(TreePersonality.SPARSE, 0);
        String originalLog = legacySparse.worldId()
                + ":0:67:0";
        String originalLeaf = legacySparse.worldId()
                + ":2:68:0";
        legacySparse.captureOriginalShape(
                List.of(originalLog), List.of(originalLeaf));
        TreeDnaNormalizer.NormalizedDna migration = new TreeDnaNormalizer()
                .normalize(legacySparse, TreeMaturityStage.MEDIUM);
        require(migration.changed()
                        && migration.dna().shapeRevision()
                                == TreeDna.CURRENT_SHAPE_REVISION,
                "legacy DNA must receive the current canopy shape revision");
        require(migration.dna().personality() == TreePersonality.BALANCED,
                "legacy sparse oak must migrate to a full balanced crown");
        require(migration.dna().currentIntent() == TreeGrowthIntent.CLEANUP
                        && migration.dna().stageCleanupBurst() >= 6,
                "legacy trees must receive a one-time target-aware cleanup phase");
        require(migration.dna().stageGrowthBurst() >= 12,
                "migrated medium trees must rebuild immediately after cleanup");
        require(migration.dna().originalShapeLogs().contains(originalLog)
                        && migration.dna().wasOriginalShapeLeaf(originalLeaf),
                "normalization must preserve the active structural source snapshot");
        YamlConfiguration persisted = new YamlConfiguration();
        migration.dna().writeTo(persisted.createSection("tree"));
        TreeDna restored = TreeDna.from(
                persisted.getConfigurationSection("tree"));
        require(restored.originalShapeLogs().contains(originalLog)
                        && restored.wasOriginalShapeLeaf(originalLeaf),
                "save/load must preserve exact original log and leaf coordinates");
        restored.completeStageCleanup();
        require(restored.stageCleanupBurst() == 0
                        && restored.hasOriginalShapeSnapshot(),
                "cleanup completion must retain source evidence while structure still grows");
        restored.completeStageTransition();
        require(!restored.hasOriginalShapeSnapshot(),
                "full structural completion must release the temporary source shape");

        System.out.println("Tree canopy transition policy smoke test passed: "
                + "target-canopy-preserved=true planned-wood-cleared=true "
                + "corridor=" + policy.corridorMinimumY() + ".."
                + policy.corridorMaximumY() + " radius="
                + policy.corridorRadius());
    }

    private static TreeDna dna() {
        return dna(TreePersonality.BALANCED, TreeDna.CURRENT_SHAPE_REVISION);
    }

    private static TreeDna dna(TreePersonality personality, int shapeRevision) {
        return new TreeDna(
                UUID.nameUUIDFromBytes("canopy-transition-smoke".getBytes()),
                0, 64, 0, TreeSpecies.OAK, 42L,
                personality, TreeRarity.COMMON,
                16, 6, 2, 4, 0,
                4, 4, 2, 4, 0.74D,
                0.50D, 0.30D, 0.0D, 0.0D, 0.20D,
                1, 0, 4, 0, 0, 0.55D,
                "canopy-transition-smoke",
                "TreeCanopyTransitionPolicySmokeTest",
                "wild", 0, shapeRevision,
                TreeGrowthIntent.CLEANUP,
                0, 0, 0, 0, 6, 5, 12,
                TreeMaturityStage.MEDIUM,
                0L, 0L, 0, true);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
