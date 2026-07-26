package org.evolution.features.treeevolution;

import java.util.UUID;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

public final class BlockProvenanceSmokeTest {
    private BlockProvenanceSmokeTest() {
    }

    public static void main(String[] args) {
        TreeDna dna = dna();
        PlannedTreeBlock trunk = new PlannedTreeBlock(0, 64, 0, Material.OAK_LOG, TreeBlockRole.TRUNK, Axis.Y, BlockFace.UP);
        PlannedTreeBlock canopy = new PlannedTreeBlock(2, 70, 0, Material.OAK_LEAVES, TreeBlockRole.CANOPY, Axis.Y, BlockFace.DOWN);

        assertProvenance("matched plan", BlockProvenance.MATCHED_PLAN,
                BlockProvenance.classify(null, dna, trunk, Material.OAK_LOG, true, true));
        assertProvenance("air is waiting/placeable", BlockProvenance.MISSING_REPLACEABLE,
                BlockProvenance.classify(null, dna, canopy, Material.AIR, true, true));
        assertProvenance("lower trunk absorbs natural ground", BlockProvenance.LOWER_TRUNK_NATURAL_GROUND,
                BlockProvenance.classify(null, dna, trunk, Material.DIRT, true, true));
        assertProvenance("liquid blocks tree growth", BlockProvenance.LIQUID,
                BlockProvenance.classify(null, dna, canopy, Material.WATER, true, true));
        assertProvenance("foreign solid remains blocked", BlockProvenance.PLAYER_OR_FOREIGN_BLOCK,
                BlockProvenance.classify(null, dna, canopy, Material.IRON_BLOCK, true, true));
        assertProvenance("world unavailable", BlockProvenance.UNCHECKED_WORLD_UNAVAILABLE,
                BlockProvenance.classify(null, dna, canopy, Material.AIR, false, false));
        assertProvenance("chunk or region unavailable", BlockProvenance.UNCHECKED_CHUNK_OR_REGION,
                BlockProvenance.classify(null, dna, canopy, Material.AIR, true, false));

        System.out.println("Block provenance smoke test passed.");
    }

    private static void assertProvenance(String name, BlockProvenance expected, BlockProvenance actual) {
        if (actual != expected) {
            throw new IllegalStateException(name + " expected " + expected + " but got " + actual);
        }
    }

    private static TreeDna dna() {
        return new TreeDna(
                UUID.nameUUIDFromBytes("block-provenance-smoke".getBytes()),
                0,
                64,
                0,
                TreeSpecies.OAK,
                12345L,
                TreePersonality.BALANCED,
                TreeRarity.COMMON,
                16,
                6,
                2,
                4,
                0,
                4,
                4,
                2,
                4,
                0.72D,
                0.50D,
                0.30D,
                0.0D,
                0.0D,
                0.20D,
                2,
                1,
                4,
                0,
                0,
                0.55D,
                "block-provenance-smoke",
                "BlockProvenanceSmokeTest",
                "wild",
                0,
                TreeDna.CURRENT_SHAPE_REVISION,
                TreeGrowthIntent.HEIGHT,
                0,
                0,
                0,
                0,
                0,
                0,
                40,
                TreeMaturityStage.MATURE,
                0L,
                0L,
                0,
                true
        );
    }
}