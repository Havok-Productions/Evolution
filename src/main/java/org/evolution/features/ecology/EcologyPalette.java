package org.evolution.features.ecology;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Biome;

final class EcologyPalette {
    private EcologyPalette() {
    }

    static Material groundFor(BiomeEcologyPath path, Set<EcologyMicrohabitat> habitats, Material current, Random random) {
        if (habitats.contains(EcologyMicrohabitat.WET) || habitats.contains(EcologyMicrohabitat.NEAR_WATER)) {
            return switch (path) {
                case WETLAND -> weighted(random, Material.MUD, 50, Material.GRASS_BLOCK, 30, Material.MOSS_BLOCK, 20);
                case DRY, COASTAL -> current;
                case FUNGAL -> Material.MYCELIUM;
                case COLD_CONIFER -> weighted(random, Material.PODZOL, 55, Material.MOSS_BLOCK, 25, Material.GRASS_BLOCK, 20);
                default -> weighted(random, Material.GRASS_BLOCK, 60, Material.MOSS_BLOCK, 25, Material.ROOTED_DIRT, 15);
            };
        }
        if (habitats.contains(EcologyMicrohabitat.ROCKY) || habitats.contains(EcologyMicrohabitat.SLOPE)) {
            return switch (path) {
                case DRY -> weighted(random, Material.COARSE_DIRT, 42, Material.SAND, 28, Material.TERRACOTTA, 18, Material.GRAVEL, 12);
                case COASTAL -> weighted(random, Material.SAND, 50, Material.GRAVEL, 30, Material.COARSE_DIRT, 20);
                case ALPINE -> weighted(random, Material.COARSE_DIRT, 40, Material.GRAVEL, 35, Material.PODZOL, 25);
                default -> weighted(random, Material.COARSE_DIRT, 45, Material.ROOTED_DIRT, 30, Material.MOSS_BLOCK, 25);
            };
        }
        return switch (path) {
            case BIRCH, TEMPERATE, CHERRY -> weighted(random, Material.GRASS_BLOCK, 62, Material.ROOTED_DIRT, 20, Material.MOSS_BLOCK, 18);
            case DARK_WOODLAND -> weighted(random, Material.PODZOL, 44, Material.ROOTED_DIRT, 30, Material.MOSS_BLOCK, 26);
            case TROPICAL -> weighted(random, Material.GRASS_BLOCK, 46, Material.MOSS_BLOCK, 30, Material.ROOTED_DIRT, 24);
            case COLD_CONIFER -> weighted(random, Material.PODZOL, 55, Material.GRASS_BLOCK, 25, Material.MOSS_BLOCK, 20);
            case WETLAND -> weighted(random, Material.MUD, 45, Material.GRASS_BLOCK, 35, Material.MOSS_BLOCK, 20);
            case DRY -> weighted(random, Material.COARSE_DIRT, 40, Material.SAND, 35, Material.TERRACOTTA, 15, Material.GRAVEL, 10);
            case COASTAL -> weighted(random, Material.SAND, 62, Material.GRAVEL, 22, Material.COARSE_DIRT, 16);
            case ALPINE -> weighted(random, Material.PODZOL, 38, Material.COARSE_DIRT, 32, Material.MOSS_BLOCK, 18, Material.GRAVEL, 12);
            case FUNGAL -> weighted(random, Material.MYCELIUM, 72, Material.MOSS_BLOCK, 18, Material.PODZOL, 10);
        };
    }

    static Material plantFor(Biome biome, BiomeEcologyPath path, EcologyMaturityStage stage, Set<EcologyMicrohabitat> habitats, Random random) {
        if (habitats.contains(EcologyMicrohabitat.WET) || habitats.contains(EcologyMicrohabitat.NEAR_WATER)) {
            return switch (path) {
                case WETLAND -> weighted(random, Material.BLUE_ORCHID, 22, Material.FERN, 30, Material.SHORT_GRASS, 28, Material.SUGAR_CANE, 20);
                case TROPICAL -> weighted(random, Material.FERN, 35, Material.SHORT_GRASS, 30, Material.BAMBOO, 18, Material.SUGAR_CANE, 17);
                case DRY, COASTAL -> weighted(random, Material.SUGAR_CANE, 40, Material.SHORT_GRASS, 34, Material.DEAD_BUSH, 26);
                default -> weighted(random, Material.FERN, 34, Material.SHORT_GRASS, 32, flowerFor(biome, random), 24, Material.SUGAR_CANE, 10);
            };
        }
        if (habitats.contains(EcologyMicrohabitat.SHADE)) {
            return switch (path) {
                case FUNGAL -> weighted(random, Material.BROWN_MUSHROOM, 42, Material.RED_MUSHROOM, 32, Material.MOSS_CARPET, 26);
                case DARK_WOODLAND -> weighted(random, Material.FERN, 30, Material.MOSS_CARPET, 30, Material.BROWN_MUSHROOM, 18, Material.LEAF_LITTER, 22);
                case COLD_CONIFER -> weighted(random, Material.FERN, 36, Material.SWEET_BERRY_BUSH, 12, Material.MOSS_CARPET, 24, Material.LEAF_LITTER, 28);
                case TROPICAL -> weighted(random, Material.FERN, 34, Material.VINE, 14, Material.SHORT_GRASS, 28, Material.MOSS_CARPET, 24);
                default -> weighted(random, Material.FERN, 32, Material.SHORT_GRASS, 24, Material.MOSS_CARPET, 20, Material.LEAF_LITTER, 24);
            };
        }
        if (path == BiomeEcologyPath.DRY) {
            return weighted(random, Material.DEAD_BUSH, 45, Material.CACTUS, 20, Material.SHORT_GRASS, 20, Material.COARSE_DIRT, 15);
        }
        if (path == BiomeEcologyPath.COASTAL) {
            return weighted(random, Material.SHORT_GRASS, 35, Material.DEAD_BUSH, 20, Material.SUGAR_CANE, 15, Material.FERN, 10, Material.COARSE_DIRT, 20);
        }
        if (stage.atLeast(EcologyMaturityStage.MATURE) && random.nextInt(100) < 12) {
            return rareFeatureFor(path, habitats, random);
        }
        return switch (path) {
            case TEMPERATE -> weighted(random, Material.SHORT_GRASS, 38, Material.TALL_GRASS, 18, flowerFor(biome, random), 34, Material.FERN, 10);
            case BIRCH -> weighted(random, Material.SHORT_GRASS, 32, Material.FERN, 22, Material.OXEYE_DAISY, 24, Material.LILY_OF_THE_VALLEY, 22);
            case DARK_WOODLAND -> weighted(random, Material.FERN, 34, Material.LEAF_LITTER, 28, Material.BROWN_MUSHROOM, 18, Material.MOSS_CARPET, 20);
            case CHERRY -> weighted(random, Material.PINK_PETALS, 45, Material.SHORT_GRASS, 24, Material.FERN, 14, Material.POPPY, 17);
            case TROPICAL -> weighted(random, Material.FERN, 28, Material.SHORT_GRASS, 22, Material.BAMBOO, 18, Material.MELON, 8, Material.VINE, 24);
            case COLD_CONIFER -> weighted(random, Material.FERN, 34, Material.SWEET_BERRY_BUSH, 18, Material.LEAF_LITTER, 24, Material.SHORT_GRASS, 24);
            case WETLAND -> weighted(random, Material.BLUE_ORCHID, 26, Material.FERN, 24, Material.SHORT_GRASS, 24, Material.BROWN_MUSHROOM, 14, Material.MOSS_CARPET, 12);
            case ALPINE -> weighted(random, Material.FERN, 26, Material.SHORT_GRASS, 32, Material.SWEET_BERRY_BUSH, 12, Material.MOSS_CARPET, 20, Material.OXEYE_DAISY, 10);
            case FUNGAL -> weighted(random, Material.BROWN_MUSHROOM, 38, Material.RED_MUSHROOM, 30, Material.MOSS_CARPET, 22, Material.MYCELIUM, 10);
            case DRY, COASTAL -> Material.DEAD_BUSH;
        };
    }

    static Material rareFeatureFor(BiomeEcologyPath path, Set<EcologyMicrohabitat> habitats, Random random) {
        return switch (path) {
            case TEMPERATE, BIRCH, DARK_WOODLAND, CHERRY -> habitats.contains(EcologyMicrohabitat.OPEN) ? Material.PUMPKIN : Material.BROWN_MUSHROOM;
            case TROPICAL -> random.nextBoolean() ? Material.MELON : Material.BAMBOO;
            case COLD_CONIFER, ALPINE -> Material.SWEET_BERRY_BUSH;
            case WETLAND -> habitats.contains(EcologyMicrohabitat.NEAR_WATER) ? Material.SUGAR_CANE : Material.BLUE_ORCHID;
            case DRY, COASTAL -> habitats.contains(EcologyMicrohabitat.NEAR_WATER) ? Material.SUGAR_CANE : Material.CACTUS;
            case FUNGAL -> random.nextBoolean() ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM;
        };
    }

    static Material flowerFor(Biome biome, Random random) {
        String name = biome.getKey().getKey().toUpperCase(java.util.Locale.ROOT);
        if (name.contains("SWAMP")) {
            return Material.BLUE_ORCHID;
        }
        if (name.contains("CHERRY")) {
            return Material.PINK_PETALS;
        }
        if (name.contains("MEADOW")) {
            return pick(random, List.of(Material.DANDELION, Material.POPPY, Material.ALLIUM, Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.CORNFLOWER));
        }
        if (name.contains("FOREST") || name.contains("BIRCH")) {
            return pick(random, List.of(Material.OXEYE_DAISY, Material.LILY_OF_THE_VALLEY, Material.POPPY, Material.DANDELION));
        }
        return pick(random, List.of(Material.DANDELION, Material.POPPY, Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.CORNFLOWER));
    }

    private static Material pick(Random random, List<Material> materials) {
        return materials.get(random.nextInt(materials.size()));
    }

    private static Material weighted(Random random, Object... pairs) {
        int total = 0;
        for (int i = 1; i < pairs.length; i += 2) {
            total += Math.max(0, (Integer) pairs[i]);
        }
        int roll = random.nextInt(Math.max(1, total));
        for (int i = 0; i < pairs.length; i += 2) {
            Material material = (Material) pairs[i];
            int weight = Math.max(0, (Integer) pairs[i + 1]);
            if (roll < weight) {
                return material;
            }
            roll -= weight;
        }
        return (Material) pairs[0];
    }
}
