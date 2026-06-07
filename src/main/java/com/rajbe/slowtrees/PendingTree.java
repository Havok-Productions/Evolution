package com.rajbe.slowtrees;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.TreeType;
import org.bukkit.configuration.ConfigurationSection;

final class PendingTree {
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final TreeType treeType;
    private final long seed;
    private int attempts;

    PendingTree(UUID worldId, int x, int y, int z, TreeType treeType, long seed, int attempts) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.treeType = treeType;
        this.seed = seed;
        this.attempts = attempts;
    }

    static PendingTree from(ConfigurationSection section) {
        UUID worldId = UUID.fromString(section.getString("world-id", ""));
        TreeType treeType = TreeType.valueOf(section.getString("tree-type", "TREE"));
        return new PendingTree(
                worldId,
                section.getInt("x"),
                section.getInt("y"),
                section.getInt("z"),
                treeType,
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

    TreeType treeType() {
        return treeType;
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
