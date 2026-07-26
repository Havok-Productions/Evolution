package org.evolution.features.treeevolution;

import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * ## GROUND DETAIL POLICY
 *
 * <p>Owns terrain-aware material substitution and local density gates for
 * constructor ground details. It never schedules or places a block.</p>
 */
final class TreeGroundDetailPolicy {
    private final Set<Material> naturalGround;
    private final Set<Material> naturalDetails;

    TreeGroundDetailPolicy(Set<Material> naturalGround,
            Set<Material> naturalDetails) {
        this.naturalGround = Set.copyOf(naturalGround);
        this.naturalDetails = Set.copyOf(naturalDetails);
    }

    Material adjust(Block target, Material planned) {
        Block ground = target.getRelative(BlockFace.DOWN);
        String biome = target.getBiome().getKey().getKey();
        if (isWetPocket(target)) {
            if (biome.contains("swamp")) {
                return planned == Material.LEAF_LITTER
                        ? Material.BROWN_MUSHROOM : Material.BLUE_ORCHID;
            }
            if (planned == Material.DEAD_BUSH
                    || planned == Material.PUMPKIN
                    || planned == Material.MELON
                    || isFlowerLike(planned)) {
                return stableChoice(
                        target, Material.FERN, Material.SHORT_GRASS);
            }
        }
        if (planned == Material.SUGAR_CANE
                && !hasAdjacentWater(ground)) {
            return stableChoice(target,
                    Material.FERN, Material.SHORT_GRASS,
                    Material.BLUE_ORCHID);
        }
        if ((planned == Material.PUMPKIN || planned == Material.MELON)
                && target.getLightFromSky() < 9) {
            return stableChoice(target,
                    Material.SHORT_GRASS, Material.FERN,
                    Material.MOSS_CARPET);
        }
        if (isSlopedPocket(ground) && isFlowerLike(planned)) {
            return stableChoice(target,
                    Material.SHORT_GRASS, Material.FERN,
                    Material.LEAF_LITTER);
        }
        if (target.getLightFromSky() < 7
                && (isFlowerLike(planned)
                        || planned == Material.SHORT_GRASS)) {
            return stableChoice(target,
                    Material.LEAF_LITTER, Material.FERN,
                    Material.BROWN_MUSHROOM);
        }
        if (planned == Material.LEAF_LITTER
                && countNearbyMaterial(
                        target, Material.LEAF_LITTER, 4) >= 5) {
            return stableChoice(target,
                    Material.MOSS_CARPET, Material.FERN,
                    Material.SHORT_GRASS);
        }
        if (isFlowerLike(planned) && countNearbyFlowers(target, 5) >= 3) {
            return stableChoice(target,
                    Material.SHORT_GRASS, Material.FERN,
                    Material.MOSS_CARPET);
        }
        if (biome.contains("taiga") || biome.contains("old_growth")) {
            return planned == Material.DANDELION
                    || planned == Material.POPPY
                    ? Material.FERN : planned;
        }
        return planned;
    }

    int countNearbyGroundDetails(Block center, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (isGroundDetail(
                            center.getRelative(dx, dy, dz).getType())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    int countNearbyRareGroundFeatures(Block center, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (isRareGroundFeature(
                            center.getRelative(dx, dy, dz).getType())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    int countNearbyFlowers(Block center, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (isFlowerLike(
                        center.getRelative(dx, 0, dz).getType())) {
                    count++;
                }
            }
        }
        return count;
    }

    boolean isRareGroundFeature(Material material) {
        return material == Material.PUMPKIN
                || material == Material.MELON
                || material == Material.SWEET_BERRY_BUSH
                || material == Material.SUGAR_CANE
                || material == Material.DEAD_BUSH;
    }

    boolean hasAdjacentWater(Block ground) {
        for (BlockFace face : List.of(
                BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST)) {
            if (ground.getRelative(face).isLiquid()
                    || ground.getRelative(face)
                            .getRelative(BlockFace.UP).isLiquid()) {
                return true;
            }
        }
        return false;
    }

    boolean isFlowerLike(Material material) {
        return material == Material.DANDELION
                || material == Material.POPPY
                || material == Material.BLUE_ORCHID
                || material == Material.ALLIUM
                || material == Material.AZURE_BLUET
                || material == Material.OXEYE_DAISY
                || material == Material.CORNFLOWER
                || material == Material.LILY_OF_THE_VALLEY
                || material == Material.PINK_PETALS;
    }

    private int countNearbyMaterial(
            Block center, Material material, int radius) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (center.getRelative(dx, dy, dz).getType()
                            == material) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private boolean isGroundDetail(Material material) {
        return naturalDetails.contains(material)
                || material == Material.MOSS_CARPET
                || material == Material.BROWN_MUSHROOM
                || material == Material.RED_MUSHROOM
                || isRareGroundFeature(material);
    }

    private boolean isWetPocket(Block target) {
        for (BlockFace face : List.of(
                BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN)) {
            if (target.getRelative(face).isLiquid()) {
                return true;
            }
        }
        return false;
    }

    private boolean isSlopedPocket(Block ground) {
        int uneven = 0;
        for (BlockFace face : List.of(
                BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST)) {
            Block neighbor = ground.getRelative(face);
            if (!naturalGround.contains(neighbor.getType())
                    && !naturalGround.contains(
                            neighbor.getRelative(BlockFace.DOWN).getType())) {
                uneven++;
            }
        }
        return uneven >= 2;
    }

    private Material stableChoice(
            Block block, Material... materials) {
        int hash = (block.getX() * 73428767)
                ^ (block.getZ() * 912931)
                ^ (block.getY() * 19349663);
        return materials[Math.floorMod(hash, materials.length)];
    }
}
