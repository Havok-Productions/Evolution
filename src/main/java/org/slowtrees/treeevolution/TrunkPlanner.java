package org.slowtrees.treeevolution;

import org.bukkit.Axis;

final class TrunkPlanner {
    void plan(TreePlan plan, TreeDna dna) {
        int visibleHeight = TreeSpeciesStageStyle.visibleHeight(dna);
        for (int y = dna.baseY(); y < dna.baseY() + visibleHeight; y++) {
            planLeanTransition(plan, dna, y);
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

    private void planLeanTransition(TreePlan plan, TreeDna dna, int y) {
        if (y <= dna.baseY()) {
            return;
        }

        int previousX = dna.trunkXAt(y - 1);
        int previousZ = dna.trunkZAt(y - 1);
        int targetX = dna.trunkXAt(y);
        int targetZ = dna.trunkZAt(y);
        if (previousX == targetX && previousZ == targetZ) {
            return;
        }

        // ## A leaning center cannot jump diagonally between Y layers. These
        // planned support cells keep gradual placement face-connected.
        plan.add(trunkBlock(dna, previousX, y, previousZ, Axis.Y));
        int x = previousX;
        int z = previousZ;
        while (x != targetX) {
            x += Integer.signum(targetX - x);
            plan.add(trunkBlock(dna, x, y, z, Axis.X));
        }
        while (z != targetZ) {
            z += Integer.signum(targetZ - z);
            plan.add(trunkBlock(dna, x, y, z, Axis.Z));
        }
    }

    private PlannedTreeBlock trunkBlock(
            TreeDna dna, int x, int y, int z, Axis axis) {
        return new PlannedTreeBlock(
                x,
                y,
                z,
                dna.species().logMaterial(),
                TreeBlockRole.TRUNK,
                axis,
                null
        );
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
