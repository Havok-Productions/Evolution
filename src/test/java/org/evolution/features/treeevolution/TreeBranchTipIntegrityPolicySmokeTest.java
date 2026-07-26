package org.evolution.features.treeevolution;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Axis;
import org.bukkit.Material;

public final class TreeBranchTipIntegrityPolicySmokeTest {
    private TreeBranchTipIntegrityPolicySmokeTest() {
    }

    public static void main(String[] args) {
        Map<String, PlannedTreeBlock> blocks = new HashMap<>();
        addCanopy(blocks, 1, 64, 0, Material.OAK_LEAVES);
        addCanopy(blocks, -1, 64, 0, Material.OAK_LEAVES);
        addCanopy(blocks, 0, 65, 0, Material.OAK_LEAVES);
        addCanopy(blocks, 1, 65, 0, Material.OAK_LEAVES);
        addCanopy(blocks, 1, 64, 1, Material.OAK_LEAVES);
        addCanopy(blocks, -1, 64, 1, Material.OAK_LEAVES);
        addCanopy(blocks, 1, 64, -1, Material.OAK_LEAVES);
        addCanopy(blocks, -1, 64, -1, Material.OAK_LEAVES);
        addCanopy(blocks, 0, 65, 1, Material.OAK_LEAVES);
        addCanopy(blocks, 0, 65, -1, Material.OAK_LEAVES);

        require(TreeBranchTipIntegrityPolicy.plannedLeafContacts(
                        0, 64, 0, Material.OAK_LEAVES, blocks) == 3,
                "only directly attached planned canopy may satisfy a branch tip");
        require(TreeBranchTipIntegrityPolicy.requiredLeafContacts(
                        TreeMaturityStage.MEDIUM, TreeSpecies.OAK) == 1,
                "one direct leaf anchor should connect the branch to its full envelope");
        require(TreeBranchTipIntegrityPolicy.requiredClusterLeaves(
                        TreeMaturityStage.MEDIUM, TreeSpecies.OAK) == 10,
                "medium oak branches should own a substantial local leaf envelope");
        require(TreeBranchTipIntegrityPolicy.plannedClusterLeaves(
                        0, 64, 0, Material.OAK_LEAVES, blocks) == 10,
                "the local envelope should count only nearby planned canopy");
        require(TreeBranchTipIntegrityPolicy.hasPreplannedEnvelope(
                        TreeMaturityStage.MEDIUM, TreeSpecies.OAK,
                        0, 64, 0, blocks),
                "branch placement must be allowed only after its full envelope is planned");

        Map<String, PlannedTreeBlock> disconnected = new HashMap<>();
        addCanopy(disconnected, 1, 64, 0, Material.OAK_LEAVES);
        addCanopy(disconnected, -1, 64, 0, Material.OAK_LEAVES);
        addCanopy(disconnected, 0, 65, 0, Material.OAK_LEAVES);
        addCanopy(disconnected, -2, 63, 2, Material.OAK_LEAVES);
        addCanopy(disconnected, -1, 63, 2, Material.OAK_LEAVES);
        addCanopy(disconnected, 0, 63, 2, Material.OAK_LEAVES);
        addCanopy(disconnected, 1, 63, 2, Material.OAK_LEAVES);
        addCanopy(disconnected, 2, 63, 2, Material.OAK_LEAVES);
        addCanopy(disconnected, -2, 64, 2, Material.OAK_LEAVES);
        addCanopy(disconnected, 2, 64, 2, Material.OAK_LEAVES);
        require(TreeBranchTipIntegrityPolicy.plannedLeafContacts(
                        0, 64, 0, Material.OAK_LEAVES, disconnected) == 3,
                "the disconnected fixture should still have enough direct contacts");
        require(TreeBranchTipIntegrityPolicy.plannedClusterLeaves(
                        0, 64, 0, Material.OAK_LEAVES, disconnected) == 3,
                "disconnected nearby leaves must not count toward the branch envelope");
        require(!TreeBranchTipIntegrityPolicy.hasPreplannedEnvelope(
                        TreeMaturityStage.MEDIUM, TreeSpecies.OAK,
                        0, 64, 0, disconnected),
                "a disconnected leaf patch must not permit branch formation");
        require(TreeBranchTipIntegrityPolicy.requiredClusterLeaves(
                        TreeMaturityStage.MATURE, TreeSpecies.BIRCH) == 9,
                "birch envelopes should stay lighter than broad-crowned species");

        System.out.println("Tree branch-tip integrity smoke test passed: "
                + "direct-anchor=1 connected-envelope=10 species-aware=true");
    }

    private static void addCanopy(Map<String, PlannedTreeBlock> blocks,
            int x, int y, int z, Material material) {
        PlannedTreeBlock block = new PlannedTreeBlock(
                x, y, z, material, TreeBlockRole.CANOPY, Axis.Y, null);
        blocks.put(block.key(), block);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
