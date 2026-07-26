package org.evolution.features.ecology;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Biome;

final class EcologyMicrohabitatTemplate {
    private EcologyMicrohabitatTemplate() {
    }

    static List<String> keys() {
        return List.of(
                "meadow-flora",
                "forest-understory",
                "birch-floor",
                "cherry-floor",
                "taiga-moss-berries",
                "jungle-undergrowth",
                "wetland-edge",
                "dry-scrub",
                "coastal-grasses",
                "fungal-colony"
        );
    }

    static Choice choose(Biome biome, BiomeEcologyPath path, EcologyMaturityStage stage, Set<EcologyMicrohabitat> habitats, Random random) {
        Template template = selectTemplate(biome, path, stage, habitats, random);
        List<Cell> cells = template.cells();
        Cell cell = cells.get(random.nextInt(cells.size()));
        return new Choice(template.key(), cell.dx(), cell.dz(), cell.material(), cell.groundMutation());
    }

    private static Template selectTemplate(Biome biome, BiomeEcologyPath path, EcologyMaturityStage stage, Set<EcologyMicrohabitat> habitats, Random random) {
        if (path == BiomeEcologyPath.FUNGAL) {
            return fungalColony(stage);
        }
        if (habitats.contains(EcologyMicrohabitat.WET) || path == BiomeEcologyPath.WETLAND) {
            return wetlandEdge(stage, habitats);
        }
        if (path == BiomeEcologyPath.DRY) {
            return dryScrub(stage, habitats);
        }
        if (path == BiomeEcologyPath.COASTAL) {
            return coastalGrass(stage, habitats);
        }
        if (path == BiomeEcologyPath.TROPICAL) {
            return jungleUndergrowth(stage, habitats);
        }
        if (path == BiomeEcologyPath.COLD_CONIFER || path == BiomeEcologyPath.ALPINE) {
            return taigaMoss(stage, habitats);
        }
        if (path == BiomeEcologyPath.DARK_WOODLAND || habitats.contains(EcologyMicrohabitat.SHADE)) {
            return forestUnderstory(stage, habitats);
        }
        if (path == BiomeEcologyPath.CHERRY) {
            return cherryFloor(stage, habitats);
        }
        if (path == BiomeEcologyPath.BIRCH && random.nextBoolean()) {
            return birchFloor(stage, habitats);
        }
        if (habitats.contains(EcologyMicrohabitat.OPEN) || biome.getKey().getKey().contains("meadow")) {
            return meadowFlora(stage);
        }
        return random.nextInt(100) < 55 ? forestUnderstory(stage, habitats) : meadowFlora(stage);
    }

    private static Template meadowFlora(EcologyMaturityStage stage) {
        List<Cell> cells = List.of(
                detail(-2, -1, Material.SHORT_GRASS), detail(-1, -1, Material.DANDELION), detail(0, -1, Material.SHORT_GRASS),
                detail(1, -1, Material.POPPY), detail(2, -1, Material.SHORT_GRASS), detail(-2, 0, Material.AZURE_BLUET),
                detail(-1, 0, Material.SHORT_GRASS), detail(0, 0, Material.TALL_GRASS), detail(1, 0, Material.OXEYE_DAISY),
                detail(2, 0, Material.SHORT_GRASS), detail(-1, 1, Material.CORNFLOWER), detail(0, 1, Material.SHORT_GRASS),
                detail(1, 1, Material.DANDELION), ground(2, 1, Material.GRASS_BLOCK), detail(0, 2, stage.atLeast(EcologyMaturityStage.DENSE) ? Material.ALLIUM : Material.SHORT_GRASS)
        );
        return new Template("meadow-flora", cells);
    }

    private static Template forestUnderstory(EcologyMaturityStage stage, Set<EcologyMicrohabitat> habitats) {
        List<Cell> cells = List.of(
                ground(-1, -1, Material.MOSS_BLOCK), detail(0, -1, Material.FERN), detail(1, -1, Material.LEAF_LITTER),
                detail(-2, 0, Material.BROWN_MUSHROOM), detail(-1, 0, Material.MOSS_CARPET), detail(0, 0, Material.FERN),
                detail(1, 0, Material.LEAF_LITTER), ground(2, 0, Material.ROOTED_DIRT), detail(-1, 1, Material.RED_MUSHROOM),
                detail(0, 1, Material.MOSS_CARPET), detail(1, 1, stage.atLeast(EcologyMaturityStage.MATURE) ? Material.LARGE_FERN : Material.FERN),
                detail(2, 1, Material.SHORT_GRASS), ground(0, 2, habitats.contains(EcologyMicrohabitat.SHADE) ? Material.MOSS_BLOCK : Material.GRASS_BLOCK)
        );
        return new Template("forest-understory", cells);
    }

    private static Template birchFloor(EcologyMaturityStage stage, Set<EcologyMicrohabitat> habitats) {
        List<Cell> cells = List.of(
                detail(-1, -1, Material.OXEYE_DAISY), detail(0, -1, Material.SHORT_GRASS), detail(1, -1, Material.FERN),
                ground(-1, 0, Material.GRASS_BLOCK), detail(0, 0, Material.LILY_OF_THE_VALLEY), detail(1, 0, Material.SHORT_GRASS),
                detail(-2, 1, Material.DANDELION), detail(-1, 1, Material.FERN), detail(0, 1, Material.MOSS_CARPET),
                detail(1, 1, stage.atLeast(EcologyMaturityStage.DENSE) ? Material.LARGE_FERN : Material.SHORT_GRASS),
                ground(2, 1, habitats.contains(EcologyMicrohabitat.SHADE) ? Material.MOSS_BLOCK : Material.ROOTED_DIRT)
        );
        return new Template("birch-floor", cells);
    }

    private static Template cherryFloor(EcologyMaturityStage stage, Set<EcologyMicrohabitat> habitats) {
        List<Cell> cells = List.of(
                detail(-2, -1, Material.PINK_PETALS), detail(-1, -1, Material.SHORT_GRASS), detail(0, -1, Material.PINK_PETALS),
                detail(1, -1, Material.FERN), detail(-1, 0, Material.PINK_PETALS), ground(0, 0, Material.MOSS_BLOCK),
                detail(1, 0, Material.POPPY), detail(2, 0, Material.PINK_PETALS), detail(-1, 1, Material.SHORT_GRASS),
                detail(0, 1, stage.atLeast(EcologyMaturityStage.DENSE) ? Material.PINK_PETALS : Material.DANDELION),
                detail(1, 1, Material.MOSS_CARPET), ground(2, 1, habitats.contains(EcologyMicrohabitat.SHADE) ? Material.MOSS_BLOCK : Material.GRASS_BLOCK)
        );
        return new Template("cherry-floor", cells);
    }

    private static Template taigaMoss(EcologyMaturityStage stage, Set<EcologyMicrohabitat> habitats) {
        List<Cell> cells = List.of(
                ground(-2, -1, Material.PODZOL), detail(-1, -1, Material.FERN), detail(0, -1, Material.LEAF_LITTER),
                detail(1, -1, Material.SWEET_BERRY_BUSH), ground(2, -1, habitats.contains(EcologyMicrohabitat.ROCKY) ? Material.GRAVEL : Material.MOSS_BLOCK),
                detail(-1, 0, Material.MOSS_CARPET), detail(0, 0, Material.FERN), detail(1, 0, Material.LEAF_LITTER),
                detail(-2, 1, Material.BROWN_MUSHROOM), ground(-1, 1, Material.PODZOL), detail(0, 1, Material.SHORT_GRASS),
                detail(1, 1, stage.atLeast(EcologyMaturityStage.DENSE) ? Material.LARGE_FERN : Material.FERN), ground(2, 1, Material.COARSE_DIRT)
        );
        return new Template("taiga-moss-berries", cells);
    }

    private static Template jungleUndergrowth(EcologyMaturityStage stage, Set<EcologyMicrohabitat> habitats) {
        List<Cell> cells = List.of(
                ground(-1, -1, Material.MOSS_BLOCK), detail(0, -1, Material.FERN), detail(1, -1, Material.SHORT_GRASS),
                detail(2, -1, Material.BAMBOO), detail(-2, 0, Material.FERN), detail(-1, 0, Material.MOSS_CARPET),
                detail(0, 0, stage.atLeast(EcologyMaturityStage.MATURE) ? Material.MELON : Material.SHORT_GRASS),
                detail(1, 0, Material.FERN), ground(2, 0, Material.ROOTED_DIRT), detail(-1, 1, Material.BROWN_MUSHROOM),
                detail(0, 1, Material.SHORT_GRASS), detail(1, 1, Material.FERN), ground(0, 2, habitats.contains(EcologyMicrohabitat.WET) ? Material.MUD : Material.MOSS_BLOCK)
        );
        return new Template("jungle-undergrowth", cells);
    }

    private static Template wetlandEdge(EcologyMaturityStage stage, Set<EcologyMicrohabitat> habitats) {
        List<Cell> cells = List.of(
                ground(-2, -1, Material.MUD), detail(-1, -1, Material.FERN), detail(0, -1, Material.BLUE_ORCHID),
                detail(1, -1, Material.SHORT_GRASS), detail(2, -1, Material.SUGAR_CANE), ground(-1, 0, Material.MOSS_BLOCK),
                detail(0, 0, Material.BROWN_MUSHROOM), detail(1, 0, Material.FERN), ground(2, 0, Material.MUD),
                detail(-1, 1, Material.BLUE_ORCHID), detail(0, 1, stage.atLeast(EcologyMaturityStage.DENSE) ? Material.LARGE_FERN : Material.FERN),
                detail(1, 1, Material.MOSS_CARPET), ground(0, 2, habitats.contains(EcologyMicrohabitat.NEAR_WATER) ? Material.MUD : Material.GRASS_BLOCK)
        );
        return new Template("wetland-edge", cells);
    }

    private static Template dryScrub(EcologyMaturityStage stage, Set<EcologyMicrohabitat> habitats) {
        List<Cell> cells = List.of(
                ground(-2, -1, Material.SAND), detail(-1, -1, Material.DEAD_BUSH), ground(0, -1, Material.COARSE_DIRT),
                detail(1, -1, Material.SHORT_GRASS), ground(2, -1, Material.GRAVEL), detail(-2, 0, Material.DEAD_BUSH),
                ground(-1, 0, Material.TERRACOTTA), detail(0, 0, stage.atLeast(EcologyMaturityStage.ESTABLISHED) ? Material.CACTUS : Material.DEAD_BUSH),
                ground(1, 0, Material.SAND), detail(2, 0, Material.DEAD_BUSH), ground(-1, 1, Material.COARSE_DIRT),
                detail(0, 1, Material.SHORT_GRASS), ground(1, 1, habitats.contains(EcologyMicrohabitat.ROCKY) ? Material.GRAVEL : Material.SAND)
        );
        return new Template("dry-scrub", cells);
    }

    private static Template coastalGrass(EcologyMaturityStage stage, Set<EcologyMicrohabitat> habitats) {
        List<Cell> cells = List.of(
                ground(-2, -1, Material.SAND), detail(-1, -1, Material.SHORT_GRASS), ground(0, -1, Material.GRAVEL),
                detail(1, -1, Material.DEAD_BUSH), detail(2, -1, Material.SUGAR_CANE), ground(-1, 0, Material.COARSE_DIRT),
                detail(0, 0, Material.SHORT_GRASS), detail(1, 0, Material.FERN), ground(2, 0, Material.SAND),
                detail(-1, 1, stage.atLeast(EcologyMaturityStage.DENSE) ? Material.OXEYE_DAISY : Material.SHORT_GRASS),
                ground(0, 1, Material.GRAVEL), detail(1, 1, Material.DEAD_BUSH), ground(2, 1, habitats.contains(EcologyMicrohabitat.NEAR_WATER) ? Material.SAND : Material.COARSE_DIRT)
        );
        return new Template("coastal-grasses", cells);
    }

    private static Template fungalColony(EcologyMaturityStage stage) {
        List<Cell> cells = List.of(
                ground(-2, -1, Material.MYCELIUM), detail(-1, -1, Material.BROWN_MUSHROOM), detail(0, -1, Material.MOSS_CARPET),
                detail(1, -1, Material.RED_MUSHROOM), ground(2, -1, Material.MYCELIUM), detail(-1, 0, Material.RED_MUSHROOM),
                ground(0, 0, Material.MYCELIUM), detail(1, 0, Material.BROWN_MUSHROOM), detail(2, 0, Material.MOSS_CARPET),
                detail(-1, 1, Material.BROWN_MUSHROOM), detail(0, 1, stage.atLeast(EcologyMaturityStage.DENSE) ? Material.RED_MUSHROOM : Material.MOSS_CARPET),
                ground(1, 1, Material.MOSS_BLOCK), detail(2, 1, Material.BROWN_MUSHROOM)
        );
        return new Template("fungal-colony", cells);
    }

    private static Cell detail(int dx, int dz, Material material) {
        return new Cell(dx, dz, material, false);
    }

    private static Cell ground(int dx, int dz, Material material) {
        return new Cell(dx, dz, material, true);
    }

    record Choice(String templateKey, int dx, int dz, Material material, boolean groundMutation) {
    }

    private record Template(String key, List<Cell> cells) {
    }

    private record Cell(int dx, int dz, Material material, boolean groundMutation) {
    }
}
