package org.evolution.features.treeevolution;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

final class TreeBranchTipIntegrityPolicy {
    private static final int[][] DIRECT_NEIGHBORS = {
            {0, 1, 0},
            {0, -1, 0},
            {1, 0, 0},
            {-1, 0, 0},
            {0, 0, 1},
            {0, 0, -1}
    };

    private TreeBranchTipIntegrityPolicy() {
    }

    static int requiredLeafContacts(TreeDna dna) {
        return requiredLeafContacts(dna.maturityStage(), dna.species());
    }

    static int requiredLeafContacts(
            TreeMaturityStage maturityStage, TreeSpecies species) {
        // ## One direct anchor proves attachment; the connected envelope enforces visible leaf mass.
        return 1;
    }

    static int requiredClusterLeaves(TreeDna dna) {
        return requiredClusterLeaves(dna.maturityStage(), dna.species());
    }

    static int requiredClusterLeaves(
            TreeMaturityStage maturityStage, TreeSpecies species) {
        return switch (maturityStage) {
            case SMALL -> switch (species) {
                case BIRCH -> 5;
                case ACACIA -> 6;
                case SPRUCE -> 7;
                default -> 7;
            };
            case MEDIUM -> switch (species) {
                case BIRCH -> 7;
                case ACACIA -> 8;
                case SPRUCE -> 9;
                case OAK, CHERRY, MANGROVE -> 10;
                case DARK_OAK, JUNGLE -> 12;
            };
            case MATURE -> switch (species) {
                case BIRCH -> 9;
                case ACACIA -> 11;
                case SPRUCE -> 12;
                case OAK, CHERRY, MANGROVE -> 14;
                case DARK_OAK, JUNGLE -> 16;
            };
            case ANCIENT -> switch (species) {
                case BIRCH -> 11;
                case ACACIA -> 14;
                case SPRUCE -> 16;
                case OAK, CHERRY, MANGROVE -> 18;
                case DARK_OAK, JUNGLE -> 20;
            };
        };
    }

    static int plannedLeafContacts(int x, int y, int z, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey) {
        int contacts = 0;
        for (int[] offset : DIRECT_NEIGHBORS) {
            PlannedTreeBlock planned = blocksByKey.get(
                    key(x + offset[0], y + offset[1], z + offset[2]));
            if (planned != null
                    && planned.role() == TreeBlockRole.CANOPY
                    && planned.material() == leafMaterial) {
                contacts++;
            }
        }
        return contacts;
    }

    static int plannedClusterLeaves(int x, int y, int z, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey) {
        return connectedClusterShape(
                x, y, z, leafMaterial, blocksByKey).leaves();
    }

    static boolean hasPreplannedEnvelope(TreeDna dna, int x, int y, int z,
            Map<String, PlannedTreeBlock> blocksByKey) {
        return hasPreplannedEnvelope(
                dna.maturityStage(), dna.species(), x, y, z, blocksByKey);
    }

    static boolean hasPreplannedEnvelope(
            TreeMaturityStage maturityStage, TreeSpecies species,
            int x, int y, int z,
            Map<String, PlannedTreeBlock> blocksByKey) {
        EnvelopeShape shape = plannedEnvelopeShape(
                x, y, z, species.leafMaterial(), blocksByKey);
        return plannedLeafContacts(
                        x, y, z, species.leafMaterial(), blocksByKey)
                        >= requiredLeafContacts(maturityStage, species)
                && shape.leaves() >= requiredClusterLeaves(maturityStage, species)
                && hasNaturalVolume(maturityStage, species, x, y, z, shape);
    }

    static int targetClusterLeaves(TreeDna dna, int x, int y, int z,
            Map<String, PlannedTreeBlock> blocksByKey) {
        return Math.min(requiredClusterLeaves(dna),
                plannedClusterLeaves(
                        x, y, z, dna.species().leafMaterial(), blocksByKey));
    }

    static int targetLeafContacts(TreeDna dna, int x, int y, int z,
            Map<String, PlannedTreeBlock> blocksByKey) {
        return Math.min(requiredLeafContacts(dna),
                plannedLeafContacts(x, y, z, dna.species().leafMaterial(), blocksByKey));
    }

    static EnvelopeShape plannedEnvelopeShape(
            int x, int y, int z, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey) {
        return connectedClusterShape(x, y, z, leafMaterial, blocksByKey);
    }

    static boolean hasNaturalVolume(
            TreeMaturityStage maturityStage, TreeSpecies species,
            int tipX, int tipY, int tipZ, EnvelopeShape shape) {
        if (shape.leaves() <= 0
                || shape.maxX() - shape.minX() < 2
                || shape.maxZ() - shape.minZ() < 2) {
            return false;
        }
        boolean above = shape.maxY() > tipY;
        boolean below = shape.minY() < tipY;
        if (species == TreeSpecies.ACACIA) {
            // ## Acacia keeps an umbrella silhouette, but even its terminal crown
            // needs a second layer so a limb cannot terminate in a flat plate.
            return above || below;
        }
        return above && below;
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    // ## A branch envelope is one connected leaf body rooted directly on the terminal limb.
    private static EnvelopeShape connectedClusterShape(
            int tipX, int tipY, int tipZ, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey) {
        ArrayDeque<Cell> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        for (int[] offset : DIRECT_NEIGHBORS) {
            addPlannedLeaf(
                    pending, visited,
                    tipX + offset[0], tipY + offset[1], tipZ + offset[2],
                    leafMaterial, blocksByKey);
        }
        int leaves = 0;
        int minX = tipX;
        int maxX = tipX;
        int minY = tipY;
        int maxY = tipY;
        int minZ = tipZ;
        int maxZ = tipZ;
        while (!pending.isEmpty()) {
            Cell current = pending.removeFirst();
            leaves++;
            minX = Math.min(minX, current.x());
            maxX = Math.max(maxX, current.x());
            minY = Math.min(minY, current.y());
            maxY = Math.max(maxY, current.y());
            minZ = Math.min(minZ, current.z());
            maxZ = Math.max(maxZ, current.z());
            for (int[] offset : DIRECT_NEIGHBORS) {
                int nextX = current.x() + offset[0];
                int nextY = current.y() + offset[1];
                int nextZ = current.z() + offset[2];
                if (Math.abs(nextX - tipX) > 2
                        || Math.abs(nextY - tipY) > 1
                        || Math.abs(nextZ - tipZ) > 2) {
                    continue;
                }
                addPlannedLeaf(
                        pending, visited, nextX, nextY, nextZ,
                        leafMaterial, blocksByKey);
            }
        }
        return new EnvelopeShape(
                leaves, minX, maxX, minY, maxY, minZ, maxZ);
    }

    private static void addPlannedLeaf(
            ArrayDeque<Cell> pending, Set<String> visited,
            int x, int y, int z, Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey) {
        String key = key(x, y, z);
        if (visited.contains(key)) {
            return;
        }
        PlannedTreeBlock planned = blocksByKey.get(key);
        if (planned == null
                || planned.role() != TreeBlockRole.CANOPY
                || planned.material() != leafMaterial) {
            return;
        }
        visited.add(key);
        pending.addLast(new Cell(x, y, z));
    }

    private record Cell(int x, int y, int z) {
    }

    record EnvelopeShape(
            int leaves,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ
    ) {
    }
}
