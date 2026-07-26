package org.evolution.features.treeevolution;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;

record PlannedTreeBlock(
        int x,
        int y,
        int z,
        Material material,
        TreeBlockRole role,
        Axis axis,
        BlockFace supportFace,
        int branchId,
        int branchStep,
        int parentX,
        int parentY,
        int parentZ,
        boolean branchTip
) {
    PlannedTreeBlock(int x, int y, int z, Material material, TreeBlockRole role, Axis axis, BlockFace supportFace) {
        this(x, y, z, material, role, axis, supportFace, -1, -1, x, y, z, false);
    }

    PlannedTreeBlock branchStep(int id, int step, int parentX, int parentY, int parentZ, boolean tip) {
        return new PlannedTreeBlock(x, y, z, material, role, axis, supportFace, id, step, parentX, parentY, parentZ, tip);
    }

    String key() {
        return x + ":" + y + ":" + z;
    }

    String parentKey() {
        return parentX + ":" + parentY + ":" + parentZ;
    }

    boolean hasBranchPath() {
        return branchId >= 0 && branchStep >= 0;
    }
}
