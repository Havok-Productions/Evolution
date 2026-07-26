package org.evolution.features.treeevolution;

import java.util.Map;
import org.bukkit.Axis;
import org.bukkit.Material;

public final class TreeCanopyIntegrityPolicySmokeTest {
    private TreeCanopyIntegrityPolicySmokeTest() {
    }

    public static void main(String[] args) {
        PlannedTreeBlock adjacentLeaf = new PlannedTreeBlock(
                1, 70, 0, Material.OAK_LEAVES,
                TreeBlockRole.CANOPY, Axis.Y, null);
        PlannedTreeBlock distantLeaf = new PlannedTreeBlock(
                3, 70, 0, Material.OAK_LEAVES,
                TreeBlockRole.CANOPY, Axis.Y, null);
        PlannedTreeBlock adjacentBranch = new PlannedTreeBlock(
                0, 71, 0, Material.OAK_LOG,
                TreeBlockRole.BRANCH, Axis.X, null);

        require(TreeCanopyIntegrityPolicy.requiresCanopyCover(
                        0, 70, 0, Material.OAK_LEAVES,
                        Map.of(adjacentLeaf.key(), adjacentLeaf)),
                "a log beside planned canopy must receive integrity coverage");
        require(!TreeCanopyIntegrityPolicy.requiresCanopyCover(
                        0, 70, 0, Material.OAK_LEAVES,
                        Map.of(distantLeaf.key(), distantLeaf)),
                "a trunk section below the planned crown must not block completion");
        require(!TreeCanopyIntegrityPolicy.requiresCanopyCover(
                        0, 70, 0, Material.OAK_LEAVES,
                        Map.of(adjacentBranch.key(), adjacentBranch)),
                "adjacent planned wood is not canopy coverage");
        require(!TreeCanopyIntegrityPolicy.requiresCanopyCover(
                        0, 70, 0, Material.BIRCH_LEAVES,
                        Map.of(adjacentLeaf.key(), adjacentLeaf)),
                "foreign leaf materials must not satisfy this tree's canopy plan");

        System.out.println("Tree canopy integrity policy smoke test passed: "
                + "plan-aware-cover=true unplanned-trunk-ignored=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
