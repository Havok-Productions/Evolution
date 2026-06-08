package org.slowtrees.wind;

import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

final class LeafLitterRules {
    private static final Set<Material> NATURAL_GROUND = Set.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.PODZOL,
            Material.ROOTED_DIRT,
            Material.DIRT_PATH,
            Material.MOSS_BLOCK,
            Material.MUD,
            Material.STONE,
            Material.ANDESITE,
            Material.DIORITE,
            Material.GRANITE,
            Material.TUFF
    );
    private static final Set<Material> REPLACEABLE_SURFACE_SPACE = Set.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.SHORT_GRASS,
            Material.FERN,
            Material.SNOW
    );

    String placementFailure(Block targetBlock) {
        if (!REPLACEABLE_SURFACE_SPACE.contains(targetBlock.getType())) {
            return "target block is " + targetBlock.getType() + ", not a replaceable surface space";
        }

        World world = targetBlock.getWorld();
        if (!targetBlock.getRelative(0, 1, 0).getType().isAir()) {
            return "block above target is " + targetBlock.getRelative(0, 1, 0).getType() + ", not air";
        }

        Block ground = targetBlock.getRelative(0, -1, 0);
        if (!NATURAL_GROUND.contains(ground.getType())) {
            return "ground below target is " + ground.getType() + ", not natural ground";
        }

        if (ground.isLiquid() || targetBlock.isLiquid()) {
            return "target or ground is liquid";
        }

        if (targetBlock.getY() < world.getMinHeight() || targetBlock.getY() >= world.getMaxHeight()) {
            return "target is outside world height";
        }

        if (targetBlock.getLightFromSky() <= 0 && targetBlock.getLightLevel() < 8) {
            return "target has no sky light and low block light";
        }

        return null;
    }

    boolean canPlace(Block targetBlock) {
        return placementFailure(targetBlock) == null;
    }

    boolean isPotentialSurfaceSpace(Block targetBlock) {
        return REPLACEABLE_SURFACE_SPACE.contains(targetBlock.getType())
                && !targetBlock.getRelative(0, -1, 0).getType().isAir();
    }

    boolean isLeaf(Material material) {
        return material.name().endsWith("_LEAVES");
    }
}
