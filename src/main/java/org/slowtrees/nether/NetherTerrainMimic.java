package org.slowtrees.nether;

import java.util.Optional;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.block.Block;

final class NetherTerrainMimic {
    boolean canMimic(Material material) {
        return material == Material.WATER
                || isSoil(material)
                || isStone(material)
                || material == Material.SAND
                || material == Material.RED_SAND
                || material == Material.GRAVEL
                || material == Material.CLAY;
    }

    Optional<Material> mimic(Block block, Random random) {
        Material type = block.getType();
        if (type == Material.WATER) {
            return Optional.of(Material.LAVA);
        }

        if (isSoil(type)) {
            return Optional.of(randomSoil(random));
        }

        if (isStone(type)) {
            return Optional.of(randomStone(random));
        }

        if (type == Material.SAND || type == Material.RED_SAND) {
            return Optional.of(Material.SOUL_SAND);
        }

        if (type == Material.GRAVEL || type == Material.CLAY) {
            return Optional.of(Material.SOUL_SOIL);
        }

        return Optional.empty();
    }

    private boolean isSoil(Material material) {
        return material == Material.GRASS_BLOCK
                || material == Material.DIRT
                || material == Material.COARSE_DIRT
                || material == Material.PODZOL
                || material == Material.ROOTED_DIRT
                || material == Material.MUD
                || material == Material.MOSS_BLOCK;
    }

    private boolean isStone(Material material) {
        return material == Material.STONE
                || material == Material.COBBLESTONE
                || material == Material.MOSSY_COBBLESTONE
                || material == Material.ANDESITE
                || material == Material.DIORITE
                || material == Material.GRANITE
                || material == Material.TUFF
                || material == Material.CALCITE
                || material == Material.DEEPSLATE
                || material == Material.COBBLED_DEEPSLATE;
    }

    private Material randomSoil(Random random) {
        int roll = random.nextInt(100);
        if (roll < 70) {
            return Material.NETHERRACK;
        }
        if (roll < 82) {
            return Material.CRIMSON_NYLIUM;
        }
        if (roll < 94) {
            return Material.WARPED_NYLIUM;
        }
        return Material.SOUL_SOIL;
    }

    private Material randomStone(Random random) {
        int roll = random.nextInt(100);
        if (roll < 55) {
            return Material.NETHERRACK;
        }
        if (roll < 80) {
            return Material.BLACKSTONE;
        }
        return Material.BASALT;
    }
}
