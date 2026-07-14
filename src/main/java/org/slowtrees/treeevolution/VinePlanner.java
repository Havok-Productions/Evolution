package org.slowtrees.treeevolution;

import java.util.List;
import java.util.Random;
import org.bukkit.Axis;
import org.bukkit.block.BlockFace;

final class VinePlanner {
    private static final List<BlockFace> FACES = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    void plan(TreePlan plan, TreeDna dna, List<TreeBranchPlan> branchPlans) {
        if (dna.species().vineMaterial() == null) {
            return;
        }

        List<TreeBranchPlan.BranchTip> branchTips = branchPlans.stream().map(TreeBranchPlan::tip).toList();
        Random random = new Random(dna.seed() ^ 0xB111E5L);
        for (TreeBranchPlan.BranchTip tip : branchTips) {
            if (random.nextDouble() > dna.vineChance()) {
                continue;
            }
            BlockFace face = FACES.get(random.nextInt(FACES.size()));
            int length = 1 + random.nextInt(dna.species() == TreeSpecies.JUNGLE || dna.species() == TreeSpecies.MANGROVE ? 4 : 2);
            for (int drop = 0; drop < length; drop++) {
                plan.add(new PlannedTreeBlock(
                        tip.x() + face.getModX(),
                        tip.y() - drop,
                        tip.z() + face.getModZ(),
                        dna.species().vineMaterial(),
                        TreeBlockRole.VINE,
                        Axis.Y,
                        face.getOppositeFace()
                ));
            }
        }
    }
}
