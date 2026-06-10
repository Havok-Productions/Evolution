package org.slowtrees.regrowth;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

final class PendingRegrowth {
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final TreeType treeType;
    private final Material anchorMaterial;
    private final long seed;
    private int attempts;

    PendingRegrowth(UUID worldId, int x, int y, int z, TreeType treeType, Material anchorMaterial, long seed, int attempts) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.treeType = treeType;
        this.anchorMaterial = anchorMaterial;
        this.seed = seed;
        this.attempts = attempts;
    }

    static PendingRegrowth from(ConfigurationSection section) {
        UUID worldId = UUID.fromString(section.getString("world-id", ""));
        TreeType treeType = TreeType.valueOf(section.getString("tree-type", "TREE"));
        String anchorMaterialName = section.getString("anchor-material", "");
        Material anchorMaterial = anchorMaterialName.isBlank() ? null : Material.matchMaterial(anchorMaterialName);
        return new PendingRegrowth(
                worldId,
                section.getInt("x"),
                section.getInt("y"),
                section.getInt("z"),
                treeType,
                anchorMaterial,
                section.getLong("seed"),
                section.getInt("attempts")
        );
    }

    void writeTo(ConfigurationSection section) {
        section.set("world-id", worldId.toString());
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("tree-type", treeType.name());
        section.set("anchor-material", anchorMaterial == null ? null : anchorMaterial.name());
        section.set("seed", seed);
        section.set("attempts", attempts);
    }

    World world() {
        return Bukkit.getWorld(worldId);
    }

    Location location(World world) {
        return new Location(world, x, y, z);
    }

    String key() {
        return worldId + ":" + x + ":" + y + ":" + z;
    }

    static String keyFor(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    TreeType treeType() {
        return treeType;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    int z() {
        return z;
    }

    Material anchorMaterial() {
        return anchorMaterial;
    }

    long seed() {
        return seed;
    }

    int attempts() {
        return attempts;
    }

    void incrementAttempts() {
        attempts++;
    }
}
