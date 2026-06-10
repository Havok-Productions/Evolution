package org.slowtrees.nether;

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

    NetherMimicResult mimic(Block block, PortalSource source, Random random) {
        Material type = block.getType();
        NetherBiomeStyle style = styleAt(source, block.getX(), block.getZ());
        if (type == Material.WATER) {
            return result(Material.LAVA, style);
        }

        if (isSoil(type)) {
            return soilResult(style, random);
        }

        if (isStone(type)) {
            return stoneResult(style, random);
        }

        if (type == Material.SAND || type == Material.RED_SAND) {
            return result(Material.SOUL_SAND, style);
        }

        if (type == Material.GRAVEL || type == Material.CLAY) {
            return result(Material.SOUL_SOIL, style);
        }

        return null;
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

    private NetherMimicResult soilResult(NetherBiomeStyle style, Random random) {
        return switch (style) {
            case CRIMSON -> result(random.nextInt(100) < 80 ? Material.CRIMSON_NYLIUM : Material.NETHERRACK, style);
            case WARPED -> result(random.nextInt(100) < 80 ? Material.WARPED_NYLIUM : Material.NETHERRACK, style);
            case SOUL -> result(random.nextInt(100) < 75 ? Material.SOUL_SOIL : Material.NETHERRACK, style);
            case BASALT -> result(random.nextInt(100) < 30 ? Material.BLACKSTONE : Material.NETHERRACK, style);
            case WASTES -> result(randomWastesSoil(random), style);
        };
    }

    private NetherMimicResult stoneResult(NetherBiomeStyle style, Random random) {
        return switch (style) {
            case BASALT -> result(random.nextInt(100) < 65 ? Material.BASALT : Material.BLACKSTONE, style);
            case SOUL -> result(random.nextInt(100) < 35 ? Material.BASALT : Material.NETHERRACK, style);
            case CRIMSON, WARPED, WASTES -> result(randomWastesStone(random), style);
        };
    }

    private NetherMimicResult result(Material material, NetherBiomeStyle style) {
        return new NetherMimicResult(material, style);
    }

    private NetherBiomeStyle styleAt(PortalSource source, int x, int z) {
        int cellX = Math.floorDiv(x - source.centerX(), 8);
        int cellZ = Math.floorDiv(z - source.centerZ(), 8);
        long hash = mix(cellX, cellZ, source.centerX(), source.centerZ());
        int roll = (int) Math.floorMod(hash, 100);
        if (roll < 45) {
            return NetherBiomeStyle.WASTES;
        }
        if (roll < 60) {
            return NetherBiomeStyle.SOUL;
        }
        if (roll < 75) {
            return NetherBiomeStyle.BASALT;
        }
        if (roll < 88) {
            return NetherBiomeStyle.CRIMSON;
        }
        return NetherBiomeStyle.WARPED;
    }

    private long mix(int cellX, int cellZ, int sourceX, int sourceZ) {
        long value = 1469598103934665603L;
        value ^= cellX * 73856093L;
        value *= 1099511628211L;
        value ^= cellZ * 19349663L;
        value *= 1099511628211L;
        value ^= sourceX * 83492791L;
        value *= 1099511628211L;
        value ^= sourceZ * 2654435761L;
        return value;
    }

    private Material randomWastesSoil(Random random) {
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

    private Material randomWastesStone(Random random) {
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
