package org.slowtrees.treeevolution;

import org.bukkit.Axis;

final class TrunkPlanner {
    void plan(TreePlan plan, TreeDna dna) {
        int visibleHeight = TreeSpeciesStageStyle.visibleHeight(dna);
        for (int y = dna.baseY(); y < dna.baseY() + visibleHeight; y++) {
            int width = TreeSpeciesStageStyle.trunkWidthAt(dna, y);
            for (int ox : offsets(width)) {
                for (int oz : offsets(width)) {
                    if (!isTrunkCell(dna, y, ox, oz, width)) {
                        continue;
                    }
                    plan.add(new PlannedTreeBlock(
                            dna.trunkXAt(y) + ox,
                            y,
                            dna.trunkZAt(y) + oz,
                            dna.species().logMaterial(),
                            TreeBlockRole.TRUNK,
                            Axis.Y,
                            null
                    ));
                }
            }
        }
    }

    static boolean isTrunkCell(TreeDna dna, int y, int ox, int oz, int width) {
        if (width <= 2) {
            return true;
        }
        if ((dna.personality() == TreePersonality.HOLLOW || dna.personality() == TreePersonality.ANCIENT_LANDMARK)
                && width >= 5
                && Math.abs(ox) <= 1
                && Math.abs(oz) <= 1
                && y > dna.baseY() + 4
                && y < dna.baseY() + Math.round(dna.targetHeight() * 0.66F)) {
            return false;
        }
        if (width == 3) {
            return true;
        }

        // Wide landmark trunks are oval and buttressed, not perfect log cubes.
        double centerOffset = width % 2 == 0 ? -0.5D : 0.0D;
        double radius = Math.max(1.0D, width / 2.0D);
        double nx = (ox - centerOffset) / radius;
        double nz = (oz - centerOffset) / radius;
        double distance = (nx * nx) + (nz * nz);
        if (distance <= 0.92D) {
            return true;
        }
        boolean lowerButtress = y < dna.baseY() + Math.max(3, Math.round(dna.targetHeight() * 0.20F));
        return lowerButtress && (Math.abs(ox) <= 1 || Math.abs(oz) <= 1) && distance <= 1.32D;
    }

    static int[] offsets(int width) {
        if (width <= 1) {
            return new int[]{0};
        }
        if (width == 2) {
            return new int[]{0, 1};
        }
        int start = -(width / 2);
        int[] offsets = new int[width];
        for (int index = 0; index < width; index++) {
            offsets[index] = start + index;
        }
        return offsets;
    }
}
