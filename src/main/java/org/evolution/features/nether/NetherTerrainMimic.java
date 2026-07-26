package org.evolution.features.nether;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;

final class NetherTerrainMimic {
    private final Predicate<Material> flowerTag;

    NetherTerrainMimic() {
        this(material -> Tag.FLOWERS.isTagged(material));
    }

    NetherTerrainMimic(Predicate<Material> flowerTag) {
        this.flowerTag = flowerTag;
    }
    boolean canMimic(Material material) {
        return material == Material.WATER
                || isFlower(material)
                || isSoil(material)
                || isStone(material)
                || material == Material.SAND
                || material == Material.RED_SAND
                || material == Material.GRAVEL
                || material == Material.CLAY;
    }

    NetherMimicResult mimic(Block block, PortalSource source, Random random) {
        return mimic(block, source, random, java.util.List.of());
    }

    NetherMimicResult mimic(Block block, PortalSource source, Random random, Collection<Material> nearbyCorruption) {
        Material type = block.getType();
        NetherBiomeStyle style = styleAt(source, block.getX(), block.getZ());
        Material directReplacement = directReplacement(type);
        if (directReplacement != null) {
            return result(directReplacement, style);
        }

        NeighborInfluence influence = dominantInfluence(nearbyCorruption);
        if (isSoil(type)) {
            return soilResult(style, random, influence);
        }

        if (isStone(type)) {
            return stoneResult(style, random, influence);
        }

        if (type == Material.SAND || type == Material.RED_SAND) {
            return sandResult(style, random, influence);
        }

        if (type == Material.GRAVEL || type == Material.CLAY) {
            return gravelResult(style, random, influence);
        }

        return null;
    }

    boolean isDirectTranslation(Material material) {
        return directReplacement(material) != null;
    }

    Material directReplacement(Material material) {
        if (material == Material.WATER) {
            return Material.LAVA;
        }
        return isFlower(material) ? Material.WITHER_ROSE : null;
    }

    boolean isFlower(Material material) {
        return material != Material.WITHER_ROSE
                && flowerTag.test(material);
    }

    boolean isCorruptionMaterial(Material material) {
        return material == Material.LAVA
                || material == Material.NETHERRACK
                || material == Material.CRIMSON_NYLIUM
                || material == Material.WARPED_NYLIUM
                || material == Material.SOUL_SOIL
                || material == Material.SOUL_SAND
                || material == Material.BLACKSTONE
                || material == Material.BASALT;
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

    private NetherMimicResult soilResult(NetherBiomeStyle style, Random random, NeighborInfluence influence) {
        Material preferred = soilPreference(influence, random);
        return switch (style) {
            case CRIMSON -> result(weightedWithInfluence(random, preferred, influence,
                    weighted(Material.NETHERRACK, 72),
                    weighted(Material.CRIMSON_NYLIUM, 26),
                    weighted(Material.BLACKSTONE, 2)), style);
            case WARPED -> result(weightedWithInfluence(random, preferred, influence,
                    weighted(Material.NETHERRACK, 72),
                    weighted(Material.WARPED_NYLIUM, 26),
                    weighted(Material.BLACKSTONE, 2)), style);
            case SOUL -> result(weightedWithInfluence(random, preferred, influence,
                    weighted(Material.NETHERRACK, 62),
                    weighted(Material.SOUL_SOIL, 30),
                    weighted(Material.SOUL_SAND, 8)), style);
            case BASALT -> result(weightedWithInfluence(random, preferred, influence,
                    weighted(Material.NETHERRACK, 72),
                    weighted(Material.BLACKSTONE, 20),
                    weighted(Material.BASALT, 8)), style);
            case WASTES -> result(weightedWithInfluence(random, preferred, influence,
                    weighted(Material.NETHERRACK, 88),
                    weighted(Material.CRIMSON_NYLIUM, 4),
                    weighted(Material.WARPED_NYLIUM, 4),
                    weighted(Material.SOUL_SOIL, 3),
                    weighted(Material.BLACKSTONE, 1)), style);
        };
    }

    private NetherMimicResult stoneResult(NetherBiomeStyle style, Random random, NeighborInfluence influence) {
        Material preferred = stonePreference(influence, random);
        return switch (style) {
            case BASALT -> result(weightedWithInfluence(random, preferred, influence,
                    weighted(Material.BASALT, 48),
                    weighted(Material.BLACKSTONE, 34),
                    weighted(Material.NETHERRACK, 18)), style);
            case SOUL -> result(weightedWithInfluence(random, preferred, influence,
                    weighted(Material.NETHERRACK, 65),
                    weighted(Material.BLACKSTONE, 20),
                    weighted(Material.BASALT, 15)), style);
            case CRIMSON, WARPED, WASTES -> result(weightedWithInfluence(random, preferred, influence,
                    weighted(Material.NETHERRACK, 75),
                    weighted(Material.BLACKSTONE, 18),
                    weighted(Material.BASALT, 7)), style);
        };
    }

    private NetherMimicResult sandResult(NetherBiomeStyle style, Random random, NeighborInfluence influence) {
        Material preferred = sandPreference(influence);
        if (style == NetherBiomeStyle.SOUL) {
            return result(weightedWithInfluence(random, preferred, influence,
                    weighted(Material.SOUL_SAND, 85),
                    weighted(Material.SOUL_SOIL, 15)), style);
        }
        return result(weightedWithInfluence(random, preferred, influence,
                weighted(Material.SOUL_SAND, 70),
                weighted(Material.NETHERRACK, 30)), style);
    }

    private NetherMimicResult gravelResult(NetherBiomeStyle style, Random random, NeighborInfluence influence) {
        Material preferred = gravelPreference(influence, random);
        if (style == NetherBiomeStyle.SOUL) {
            return result(weightedWithInfluence(random, preferred, influence,
                    weighted(Material.SOUL_SOIL, 80),
                    weighted(Material.SOUL_SAND, 20)), style);
        }
        return result(weightedWithInfluence(random, preferred, influence,
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

    private NeighborInfluence dominantInfluence(Collection<Material> materials) {
        Map<NetherMaterialFamily, Integer> counts = new EnumMap<>(NetherMaterialFamily.class);
        for (Material material : materials) {
            NetherMaterialFamily family = familyOf(material);
            if (family != null && family != NetherMaterialFamily.LAVA) {
                counts.merge(family, 1, Integer::sum);
            }
        }

        NetherMaterialFamily dominant = null;
        int dominantCount = 0;
        for (Map.Entry<NetherMaterialFamily, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > dominantCount) {
                dominant = entry.getKey();
                dominantCount = entry.getValue();
            }
        }
        return new NeighborInfluence(dominant, dominantCount);
    }

    private NetherMaterialFamily familyOf(Material material) {
        return switch (material) {
            case LAVA -> NetherMaterialFamily.LAVA;
            case NETHERRACK -> NetherMaterialFamily.NETHERRACK;
            case CRIMSON_NYLIUM -> NetherMaterialFamily.CRIMSON;
            case WARPED_NYLIUM -> NetherMaterialFamily.WARPED;
            case SOUL_SOIL, SOUL_SAND -> NetherMaterialFamily.SOUL;
            case BLACKSTONE, BASALT -> NetherMaterialFamily.BASALT;
            default -> null;
        };
    }

    private Material soilPreference(NeighborInfluence influence, Random random) {
        if (influence.family() == null) {
            return null;
        }
        return switch (influence.family()) {
            case NETHERRACK -> Material.NETHERRACK;
            case CRIMSON -> Material.CRIMSON_NYLIUM;
            case WARPED -> Material.WARPED_NYLIUM;
            case SOUL -> random.nextInt(100) < 80 ? Material.SOUL_SOIL : Material.SOUL_SAND;
            case BASALT -> random.nextInt(100) < 70 ? Material.BLACKSTONE : Material.BASALT;
            case LAVA -> null;
        };
    }

    private Material stonePreference(NeighborInfluence influence, Random random) {
        if (influence.family() == null) {
            return null;
        }
        return switch (influence.family()) {
            case BASALT -> random.nextInt(100) < 65 ? Material.BASALT : Material.BLACKSTONE;
            case SOUL -> random.nextInt(100) < 70 ? Material.NETHERRACK : Material.BLACKSTONE;
            case CRIMSON, WARPED, NETHERRACK -> random.nextInt(100) < 80 ? Material.NETHERRACK : Material.BLACKSTONE;
            case LAVA -> null;
        };
    }

    private Material sandPreference(NeighborInfluence influence) {
        if (influence.family() == null) {
            return null;
        }
        return switch (influence.family()) {
            case NETHERRACK, CRIMSON, WARPED -> Material.NETHERRACK;
            case SOUL, BASALT -> Material.SOUL_SAND;
            case LAVA -> null;
        };
    }

    private Material gravelPreference(NeighborInfluence influence, Random random) {
        if (influence.family() == null) {
            return null;
        }
        return switch (influence.family()) {
            case BASALT -> random.nextInt(100) < 65 ? Material.BLACKSTONE : Material.BASALT;
            case NETHERRACK, CRIMSON, WARPED -> Material.NETHERRACK;
            case SOUL -> random.nextInt(100) < 80 ? Material.SOUL_SOIL : Material.SOUL_SAND;
            case LAVA -> null;
        };
    }

    private Material weightedWithInfluence(Random random, Material preferred, NeighborInfluence influence, WeightedMaterial... materials) {
        if (preferred == null || influence.count() <= 0) {
            return weighted(random, materials);
        }

        WeightedMaterial[] influenced = new WeightedMaterial[materials.length + 1];
        System.arraycopy(materials, 0, influenced, 0, materials.length);
        influenced[materials.length] = weighted(preferred, Math.min(180, 70 + (influence.count() * 25)));
        return weighted(random, influenced);
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

    private record NeighborInfluence(NetherMaterialFamily family, int count) {
    }

    private enum NetherMaterialFamily {
        LAVA,
        NETHERRACK,
        CRIMSON,
        WARPED,
        SOUL,
        BASALT
    }
}
