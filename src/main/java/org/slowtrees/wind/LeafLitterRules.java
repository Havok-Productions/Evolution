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
            Material.MOSS_BLOCK,
            Material.MUD,
            Material.STONE,
            Material.ANDESITE,
            Material.DIORITE,
            Material.GRANITE,
            Material.TUFF
    );

    boolean canPlace(Block airBlock) {
        if (airBlock.getType() != Material.AIR) {
            return false;
        }

        World world = airBlock.getWorld();
        if (!airBlock.getRelative(0, 1, 0).getType().isAir()) {
            return false;
        }

        Block ground = airBlock.getRelative(0, -1, 0);
        if (!NATURAL_GROUND.contains(ground.getType())) {
            return false;
        }

        if (ground.isLiquid() || airBlock.isLiquid()) {
            return false;
        }

        return airBlock.getY() >= world.getMinHeight()
                && airBlock.getY() < world.getMaxHeight()
                && (airBlock.getLightFromSky() >= 8 || airBlock.getLightLevel() >= 13);
    }

    boolean isLeaf(Material material) {
        return material.name().endsWith("_LEAVES");
    }
}
