package org.evolution.features.treeevolution;

import java.util.UUID;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

/**
 * ## Persistent ownership receipt for one offspring sapling.
 *
 * <p>The receipt exists only between planting and germination. It lets Evolution
 * distinguish its offspring from player-planted saplings and preserves exact
 * species and lineage across restarts.</p>
 */
record TreeSeedlingRecord(
        UUID worldId,
        int x,
        int y,
        int z,
        TreeSpecies species,
        String parentKey,
        int generation,
        long plantedMillis
) {
    static TreeSeedlingRecord create(Block block, TreeDna parent) {
        return new TreeSeedlingRecord(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ(),
                parent.species(),
                parent.key(),
                parent.generation() + 1,
                System.currentTimeMillis());
    }

    static TreeSeedlingRecord from(ConfigurationSection section) {
        UUID worldId = UUID.fromString(section.getString("world-id", ""));
        TreeSpecies species = TreeSpecies.fromId(
                        section.getString("species", ""))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown seedling species"));
        return new TreeSeedlingRecord(
                worldId,
                section.getInt("x"),
                section.getInt("y"),
                section.getInt("z"),
                species,
                section.getString("parent-key", "wild"),
                Math.max(0, section.getInt("generation")),
                Math.max(0L, section.getLong("planted-millis")));
    }

    void writeTo(ConfigurationSection section) {
        section.set("world-id", worldId.toString());
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("species", species.id());
        section.set("parent-key", parentKey);
        section.set("generation", generation);
        section.set("planted-millis", plantedMillis);
    }

    String key() {
        return key(worldId, x, y, z);
    }

    boolean matches(Block block) {
        return worldId.equals(block.getWorld().getUID())
                && x == block.getX()
                && y == block.getY()
                && z == block.getZ()
                && block.getType() == species.saplingMaterial();
    }

    static String key(Block block) {
        return key(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ());
    }

    static String key(World world, int x, int y, int z) {
        return key(world.getUID(), x, y, z);
    }

    private static String key(UUID worldId, int x, int y, int z) {
        return worldId + ":" + x + ":" + y + ":" + z;
    }
}
