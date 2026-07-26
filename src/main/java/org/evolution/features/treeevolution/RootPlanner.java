package org.evolution.features.treeevolution;

import java.util.List;
import java.util.Random;
import org.bukkit.Axis;
import org.bukkit.block.BlockFace;

final class RootPlanner {
    private static final List<BlockFace> FACES = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    void plan(TreePlan plan, TreeDna dna) {
        Random random = new Random(dna.seed() ^ 0x7007L);
        for (BlockFace face : FACES) {
            if (random.nextDouble() > dna.rootChance()) {
                continue;
            }
            int length = 1 + random.nextInt(dna.maturityStage() == TreeMaturityStage.ANCIENT ? 4 : 3);
            int x = dna.baseX();
            int z = dna.baseZ();
            for (int step = 1; step <= length; step++) {
                x += face.getModX();
                z += face.getModZ();
                plan.add(new PlannedTreeBlock(
                        x,
                        dna.baseY(),
                        z,
                        dna.species().logMaterial(),
                        TreeBlockRole.ROOT,
                        face == BlockFace.EAST || face == BlockFace.WEST ? Axis.X : Axis.Z,
                        null
                ));
            }
        }
    }
}
