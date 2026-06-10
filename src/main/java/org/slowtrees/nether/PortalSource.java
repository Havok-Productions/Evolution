package org.slowtrees.nether;

import java.util.Collection;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

final class PortalSource {
    private final UUID worldId;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    private PortalSource(UUID worldId, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.worldId = worldId;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    static PortalSource fromBlocks(Collection<Block> blocks) {
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("Portal source requires at least one block.");
        }

        Block first = blocks.iterator().next();
        int minX = first.getX();
        int minY = first.getY();
        int minZ = first.getZ();
        int maxX = first.getX();
        int maxY = first.getY();
        int maxZ = first.getZ();

        for (Block block : blocks) {
            minX = Math.min(minX, block.getX());
            minY = Math.min(minY, block.getY());
            minZ = Math.min(minZ, block.getZ());
            maxX = Math.max(maxX, block.getX());
            maxY = Math.max(maxY, block.getY());
            maxZ = Math.max(maxZ, block.getZ());
        }

        return new PortalSource(first.getWorld().getUID(), minX, minY, minZ, maxX, maxY, maxZ);
    }

    static PortalSource from(ConfigurationSection section) {
        return new PortalSource(
                UUID.fromString(section.getString("world-id", "")),
                section.getInt("min-x"),
                section.getInt("min-y"),
                section.getInt("min-z"),
                section.getInt("max-x"),
                section.getInt("max-y"),
                section.getInt("max-z")
        );
    }

    void writeTo(ConfigurationSection section) {
        section.set("world-id", worldId.toString());
        section.set("min-x", minX);
        section.set("min-y", minY);
        section.set("min-z", minZ);
        section.set("max-x", maxX);
        section.set("max-y", maxY);
        section.set("max-z", maxZ);
    }

    World world() {
        return Bukkit.getWorld(worldId);
    }

    Location center(World world) {
        return new Location(world, centerX(), centerY(), centerZ());
    }

    String key() {
        return worldId + ":" + minX + ":" + minY + ":" + minZ + ":" + maxX + ":" + maxY + ":" + maxZ;
    }

    String shortDescription() {
        return "near " + centerX() + "," + centerY() + "," + centerZ();
    }

    int minChunkX() {
        return minX >> 4;
    }

    int maxChunkX() {
        return maxX >> 4;
    }

    int minChunkZ() {
        return minZ >> 4;
    }

    int maxChunkZ() {
        return maxZ >> 4;
    }

    int minX() {
        return minX;
    }

    int minY() {
        return minY;
    }

    int minZ() {
        return minZ;
    }

    int maxX() {
        return maxX;
    }

    int maxY() {
        return maxY;
    }

    int maxZ() {
        return maxZ;
    }

    int centerX() {
        return Math.floorDiv(minX + maxX, 2);
    }

    int centerY() {
        return Math.floorDiv(minY + maxY, 2);
    }

    int centerZ() {
        return Math.floorDiv(minZ + maxZ, 2);
    }

    boolean isSameWorld(World world) {
        return world.getUID().equals(worldId);
    }

    boolean isNear(Block block, int radius) {
        if (!isSameWorld(block.getWorld())) {
            return false;
        }

        int dx = Math.max(Math.max(minX - block.getX(), 0), block.getX() - maxX);
        int dy = Math.max(Math.max(minY - block.getY(), 0), block.getY() - maxY);
        int dz = Math.max(Math.max(minZ - block.getZ(), 0), block.getZ() - maxZ);
        return Math.max(Math.max(dx, dy), dz) <= radius;
    }
}
