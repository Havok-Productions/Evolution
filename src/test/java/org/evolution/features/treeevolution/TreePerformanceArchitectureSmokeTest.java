package org.evolution.features.treeevolution;

import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * ## Focused regression for indexed ownership and compact DNA persistence.
 */
public final class TreePerformanceArchitectureSmokeTest {
    private TreePerformanceArchitectureSmokeTest() {
    }

    public static void main(String[] args) {
        UUID worldId = UUID.nameUUIDFromBytes(
                "tree-performance-architecture".getBytes());
        TreeDna active = dna(worldId, 0);
        TreeDna neighbor = dna(worldId, 8);
        String shared = worldId + ":2:70:2";
        active.restoreOriginalShape(List.of(), List.of(), List.of(),
                List.of(), List.of(shared), 2);
        neighbor.restoreOriginalShape(List.of(), List.of(shared), List.of(),
                List.of(), List.of(), 2);

        TreeLeafOwnershipIndex index = new TreeLeafOwnershipIndex();
        index.rebuild(List.of(active, neighbor));
        assertDecision("shared leaf",
                TreeStaleEnvelopeOwnershipPolicy.Decision
                        .IGNORE_SHARED_WITH_FOREIGN_TREE,
                index.classify(active, shared));
        index.remove(neighbor.key());
        assertDecision("exclusive leaf",
                TreeStaleEnvelopeOwnershipPolicy.Decision
                        .RETIRE_EXCLUSIVE_EVOLVED_LEAF,
                index.classify(active, shared));

        YamlConfiguration yaml = new YamlConfiguration();
        TreeDnaCodec.write(active, yaml);
        if (!"relative-v1".equals(yaml.getString(
                "transition.coordinates.format"))) {
            throw new IllegalStateException("compact coordinate format missing");
        }
        if (yaml.contains("transition.evolved-leaves")) {
            throw new IllegalStateException("legacy full coordinate list was written");
        }
        TreeDna restored = TreeDnaCodec.read(yaml);
        if (!restored.evolvedShapeLeaves().equals(
                active.evolvedShapeLeaves())) {
            throw new IllegalStateException(
                    "relative coordinate round trip changed ownership");
        }
        System.out.println("Tree performance architecture smoke test passed");
    }

    private static void assertDecision(String name,
            TreeStaleEnvelopeOwnershipPolicy.Decision expected,
            TreeStaleEnvelopeOwnershipPolicy.Decision actual) {
        if (actual != expected) {
            throw new IllegalStateException(name + " expected "
                    + expected + " but got " + actual);
        }
    }

    private static TreeDna dna(UUID worldId, int x) {
        return new TreeDna(
                worldId, x, 64, 0, TreeSpecies.OAK,
                TreeVariant.OAK_STANDARD, TreeSourcePattern.unknown(),
                12345L + x, TreePersonality.BALANCED,
                TreeRarity.COMMON, 16, 6, 2, 4, 0,
                4, 4, 2, 4, 0.72D, 0.50D, 0.30D,
                0.0D, 0.0D, 0.20D, 2, 1, 4,
                0, 0, 0.55D, "performance-smoke",
                "TreePerformanceArchitectureSmokeTest", "wild", 0,
                TreeDna.CURRENT_SHAPE_REVISION,
                TreeGrowthIntent.HEIGHT, 0, 0, 0, 0, 0, 0,
                40, TreeMaturityStage.MATURE, 0L, 0L, 0, true);
    }
}
