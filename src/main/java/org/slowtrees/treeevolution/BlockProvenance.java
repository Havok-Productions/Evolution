package org.slowtrees.treeevolution;

import org.bukkit.Material;

enum BlockProvenance {
    MATCHED_PLAN,
    MISSING_REPLACEABLE,
    LOWER_TRUNK_NATURAL_GROUND,
    NATURAL_TREE_MATERIAL,
    LIQUID,
    PLAYER_OR_FOREIGN_BLOCK,
    UNCHECKED_WORLD_UNAVAILABLE,
    UNCHECKED_CHUNK_OR_REGION;

    static BlockProvenance classify(TreeEvolutionConfig config, TreeDna dna, PlannedTreeBlock plannedBlock, Material live, boolean worldAvailable, boolean chunkAndRegionReady) {
        if (plannedBlock == null) {
            return UNCHECKED_WORLD_UNAVAILABLE;
        }
        if (!worldAvailable) {
            return UNCHECKED_WORLD_UNAVAILABLE;
        }
        if (!chunkAndRegionReady) {
            return UNCHECKED_CHUNK_OR_REGION;
        }
        if (live == plannedBlock.material()) {
            return MATCHED_PLAN;
        }
        if (live == Material.WATER || live == Material.LAVA) {
            return LIQUID;
        }
        if (isReplaceable(config, live)) {
            return MISSING_REPLACEABLE;
        }
        if (plannedBlock.role() == TreeBlockRole.TRUNK && isLowerTrunkNaturalGround(dna, plannedBlock, live)) {
            return LOWER_TRUNK_NATURAL_GROUND;
        }
        if (isTreeMaterial(live)) {
            return NATURAL_TREE_MATERIAL;
        }
        return PLAYER_OR_FOREIGN_BLOCK;
    }

    boolean isPlaced() {
        return this == MATCHED_PLAN;
    }

    boolean isMissingButPlaceable() {
        return this == MISSING_REPLACEABLE || this == LOWER_TRUNK_NATURAL_GROUND || this == NATURAL_TREE_MATERIAL;
    }

    boolean isUnchecked() {
        return this == UNCHECKED_WORLD_UNAVAILABLE || this == UNCHECKED_CHUNK_OR_REGION;
    }

    boolean isBlocked() {
        return !isPlaced() && !isMissingButPlaceable() && !isUnchecked();
    }

    String note() {
        return switch (this) {
            case MATCHED_PLAN -> "planned block already exists";
            case MISSING_REPLACEABLE -> "air/replaceable live block; tree can place here later";
            case LOWER_TRUNK_NATURAL_GROUND -> "lower trunk may absorb natural ground while thickening";
            case NATURAL_TREE_MATERIAL -> "existing tree material nearby; likely safe to treat as organic progress";
            case LIQUID -> "blocked by water/lava";
            case PLAYER_OR_FOREIGN_BLOCK -> "blocked by non-natural or foreign block";
            case UNCHECKED_WORLD_UNAVAILABLE -> "world unavailable during offline/debug plan capture";
            case UNCHECKED_CHUNK_OR_REGION -> "chunk unloaded or not owned by current Folia region";
        };
    }

    private static boolean isLowerTrunkNaturalGround(TreeDna dna, PlannedTreeBlock plannedBlock, Material live) {
        if (!isNaturalGround(live)) {
            return false;
        }
        int relativeY = plannedBlock.y() - dna.baseY();
        if (relativeY < -1 || relativeY > 2) {
            return false;
        }
        int half = Math.max(0, (dna.trunkWidthAt(plannedBlock.y()) - 1) / 2);
        return Math.abs(plannedBlock.x() - dna.baseX()) <= half + 1
                && Math.abs(plannedBlock.z() - dna.baseZ()) <= half + 1;
    }

    private static boolean isNaturalGround(Material material) {
        return switch (material) {
            case DIRT, GRASS_BLOCK, PODZOL, COARSE_DIRT, ROOTED_DIRT, MOSS_BLOCK, MUD,
                    MUDDY_MANGROVE_ROOTS, CLAY, SAND, RED_SAND, GRAVEL, STONE, ANDESITE, DIORITE, GRANITE -> true;
            default -> material.name().endsWith("_NYLIUM") || material.name().endsWith("TERRACOTTA");
        };
    }

    private static boolean isReplaceable(TreeEvolutionConfig config, Material material) {
        if (config != null) {
            return config.isReplaceable(material);
        }
        return material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || material == Material.SHORT_GRASS
                || material == Material.TALL_GRASS
                || material == Material.FERN
                || material == Material.LARGE_FERN
                || material == Material.VINE
                || material == Material.LEAF_LITTER
                || material == Material.MOSS_CARPET
                || material == Material.SNOW
                || material == Material.BROWN_MUSHROOM
                || material == Material.RED_MUSHROOM
                || material == Material.PINK_PETALS
                || material.name().endsWith("_LEAVES");
    }

    private static boolean isTreeMaterial(Material material) {
        return material.name().endsWith("_LOG")
                || material.name().endsWith("_WOOD")
                || material.name().endsWith("_LEAVES")
                || material == Material.VINE
                || material == Material.MANGROVE_ROOTS
                || material == Material.MUDDY_MANGROVE_ROOTS;
    }
}
