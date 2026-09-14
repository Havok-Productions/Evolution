package org.evolution.features.treeevolution;

import java.util.Collection;

/**
 * ## Measures full horizontal crown volume instead of one projected width.
 */
final class TreeCrownVolumeAudit {
    private TreeCrownVolumeAudit() {
    }

    static Coverage inspect(
            TreeDna dna,
            TreePlan plan,
            Collection<Point> liveLeaves
    ) {
        int plannedMask = 0;
        for (PlannedTreeBlock block : plan.orderedBlocks()) {
            if (block.role() == TreeBlockRole.CANOPY) {
                plannedMask |= 1 << sector(
                        block.x() - dna.trunkXAt(block.y()),
                        block.z() - dna.trunkZAt(block.y()));
            }
        }

        int liveMask = 0;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Point leaf : liveLeaves) {
            liveMask |= 1 << sector(
                    leaf.x() - dna.trunkXAt(leaf.y()),
                    leaf.z() - dna.trunkZAt(leaf.y()));
            minX = Math.min(minX, leaf.x());
            maxX = Math.max(maxX, leaf.x());
            minZ = Math.min(minZ, leaf.z());
            maxZ = Math.max(maxZ, leaf.z());
        }
        int widthX = liveLeaves.isEmpty() ? 0 : maxX - minX + 1;
        int widthZ = liveLeaves.isEmpty() ? 0 : maxZ - minZ + 1;
        return new Coverage(
                widthX,
                widthZ,
                Integer.bitCount(plannedMask),
                Integer.bitCount(liveMask & plannedMask));
    }

    static int minimumAxisWidth(TreeDna dna) {
        int stage = dna.maturityStage().ordinal();
        return switch (dna.species()) {
            case BIRCH -> stage <= 1 ? 3 : 4;
            case ACACIA -> stage <= 1 ? 4 : 5;
            case SPRUCE -> stage <= 1 ? 5 : 6;
            case JUNGLE, DARK_OAK -> stage <= 1 ? 5 : 7;
            case OAK, MANGROVE, CHERRY -> stage <= 1 ? 5 : 6;
        };
    }

    private static int sector(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx < 0 ? 0 : 1;
        }
        return dz < 0 ? 2 : 3;
    }

    record Point(int x, int y, int z) {
    }

    record Coverage(
            int widthX,
            int widthZ,
            int plannedSectors,
            int occupiedSectors
    ) {
        boolean coversPlannedSectors() {
            return occupiedSectors >= plannedSectors;
        }
    }
}
