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
        boolean branchTip,
        TreePlacementAugment augment
) {
    PlannedTreeBlock {
        augment = augment == null
                ? TreePlacementAugment.UNCLASSIFIED : augment;
        if (!augment.accepts(role)) {
            throw new IllegalArgumentException(
                    augment + " cannot plan role " + role);
        }
    }

    PlannedTreeBlock(int x, int y, int z, Material material, TreeBlockRole role, Axis axis, BlockFace supportFace) {
        this(x, y, z, material, role, axis, supportFace,
                -1, -1, x, y, z, false,
                TreePlacementAugment.UNCLASSIFIED);
    }

    PlannedTreeBlock branchStep(int id, int step, int parentX, int parentY, int parentZ, boolean tip) {
        return new PlannedTreeBlock(x, y, z, material, role, axis,
                supportFace, id, step, parentX, parentY, parentZ, tip,
                augment);
    }

    PlannedTreeBlock withAugment(TreePlacementAugment placementAugment) {
        return new PlannedTreeBlock(x, y, z, material, role, axis,
                supportFace, branchId, branchStep,
                parentX, parentY, parentZ, branchTip,
                placementAugment);
    }

    PlannedTreeBlock at(
            int canonicalX,
            int canonicalY,
            int canonicalZ,
            int canonicalParentX,
            int canonicalParentY,
            int canonicalParentZ
    ) {
        return new PlannedTreeBlock(
                canonicalX, canonicalY, canonicalZ,
                material, role, axis, supportFace,
                branchId, branchStep,
                canonicalParentX, canonicalParentY, canonicalParentZ,
                branchTip, augment);
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
