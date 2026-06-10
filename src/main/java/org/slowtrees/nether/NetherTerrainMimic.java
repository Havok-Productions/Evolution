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
            return sandResult(style, random);
        }

        if (type == Material.GRAVEL || type == Material.CLAY) {
            return gravelResult(style, random);
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
            case CRIMSON -> result(weighted(random,
                    weighted(Material.NETHERRACK, 72),
                    weighted(Material.CRIMSON_NYLIUM, 26),
                    weighted(Material.BLACKSTONE, 2)), style);
            case WARPED -> result(weighted(random,
                    weighted(Material.NETHERRACK, 72),
                    weighted(Material.WARPED_NYLIUM, 26),
                    weighted(Material.BLACKSTONE, 2)), style);
            case SOUL -> result(weighted(random,
                    weighted(Material.NETHERRACK, 62),
                    weighted(Material.SOUL_SOIL, 30),
                    weighted(Material.SOUL_SAND, 8)), style);
            case BASALT -> result(weighted(random,
                    weighted(Material.NETHERRACK, 72),
                    weighted(Material.BLACKSTONE, 20),
                    weighted(Material.BASALT, 8)), style);
            case WASTES -> result(weighted(random,
                    weighted(Material.NETHERRACK, 88),
                    weighted(Material.CRIMSON_NYLIUM, 4),
                    weighted(Material.WARPED_NYLIUM, 4),
                    weighted(Material.SOUL_SOIL, 3),
                    weighted(Material.BLACKSTONE, 1)), style);
        };
    }

    private NetherMimicResult stoneResult(NetherBiomeStyle style, Random random) {
        return switch (style) {
            case BASALT -> result(weighted(random,
                    weighted(Material.BASALT, 48),
                    weighted(Material.BLACKSTONE, 34),
                    weighted(Material.NETHERRACK, 18)), style);
            case SOUL -> result(weighted(random,
                    weighted(Material.NETHERRACK, 65),
                    weighted(Material.BLACKSTONE, 20),
                    weighted(Material.BASALT, 15)), style);
            case CRIMSON, WARPED, WASTES -> result(weighted(random,
                    weighted(Material.NETHERRACK, 75),
                    weighted(Material.BLACKSTONE, 18),
                    weighted(Material.BASALT, 7)), style);
        };
    }

    private NetherMimicResult sandResult(NetherBiomeStyle style, Random random) {
        if (style == NetherBiomeStyle.SOUL) {
            return result(weighted(random,
                    weighted(Material.SOUL_SAND, 85),
                    weighted(Material.SOUL_SOIL, 15)), style);
        }
        return result(weighted(random,
                weighted(Material.SOUL_SAND, 70),
                weighted(Material.NETHERRACK, 30)), style);
    }

    private NetherMimicResult gravelResult(NetherBiomeStyle style, Random random) {
        if (style == NetherBiomeStyle.SOUL) {
            return result(weighted(random,
                    weighted(Material.SOUL_SOIL, 80),
                    weighted(Material.SOUL_SAND, 20)), style);
        }
        return result(weighted(random,
                weighted(Material.SOUL_SOIL, 65),
                weighted(Material.NETHERRACK, 35)), style);
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

    private Material weighted(Random random, WeightedMaterial... materials) {
        int total = 0;
        for (WeightedMaterial material : materials) {
            total += material.weight();
        }

        int roll = random.nextInt(total);
        int running = 0;
        for (WeightedMaterial material : materials) {
            running += material.weight();
            if (roll < running) {
                return material.material();
            }
        }
        return materials[materials.length - 1].material();
    }

    private WeightedMaterial weighted(Material material, int weight) {
        return new WeightedMaterial(material, weight);
    }

    private record WeightedMaterial(Material material, int weight) {
    }
}
