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

    boolean canPlace(Block targetBlock) {
        if (!REPLACEABLE_SURFACE_SPACE.contains(targetBlock.getType())) {
            return false;
        }

        World world = targetBlock.getWorld();
        if (!targetBlock.getRelative(0, 1, 0).getType().isAir()) {
            return false;
        }

        Block ground = targetBlock.getRelative(0, -1, 0);
        if (!NATURAL_GROUND.contains(ground.getType())) {
            return false;
        }

        if (ground.isLiquid() || targetBlock.isLiquid()) {
            return false;
        }

        return targetBlock.getY() >= world.getMinHeight()
                && targetBlock.getY() < world.getMaxHeight()
                && (targetBlock.getLightFromSky() > 0 || targetBlock.getLightLevel() >= 8);
    }

    boolean isLeaf(Material material) {
        return material.name().endsWith("_LEAVES");
    }
}
