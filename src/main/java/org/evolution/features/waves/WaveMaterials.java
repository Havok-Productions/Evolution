package org.evolution.features.waves;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;

final class WaveMaterials {
    private WaveMaterials() {
    }

    static boolean isWater(Material material) {
        return material == Material.WATER || material == Material.BUBBLE_COLUMN
                || material == Material.KELP || material == Material.KELP_PLANT
                || material == Material.SEAGRASS || material == Material.TALL_SEAGRASS;
    }

    static boolean containsWater(Block block) {
        if (isWater(block.getType())) {
            return true;
        }
        return block.getBlockData() instanceof Waterlogged waterlogged
                && waterlogged.isWaterlogged();
    }

    static boolean isWaterSurfaceCover(Material material) {
        // ## Surface covers do not split one body of water into fake islands.
        // They remain visually untouched while the front continues underneath.
        return material == Material.LILY_PAD;
    }

    static boolean isVisualReplaceable(Material material) {
        return material.isAir() || material == Material.BUBBLE_COLUMN
                || material == Material.SEAGRASS || material == Material.TALL_SEAGRASS
                || material == Material.KELP || material == Material.KELP_PLANT;
    }

    static boolean isRunupGround(Material material) {
        return switch (material) {
            case SAND, RED_SAND, GRAVEL, CLAY, MUD, PACKED_MUD, DIRT, GRASS_BLOCK, STONE, COBBLESTONE, MOSS_BLOCK -> true;
            default -> false;
        };
    }
}