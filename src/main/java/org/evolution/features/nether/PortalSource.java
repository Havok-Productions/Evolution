package org.evolution.features.nether;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

/**
 * ## IMMUTABLE PORTAL SOURCE SNAPSHOT
 *
 * <p>The exact connected portal interior is retained so a partial or broken
 * portal cannot continue powering an old corruption frontier.</p>
 */
final class PortalSource {
    private static final int SNAPSHOT_VERSION = 2;

    private final UUID worldId;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final List<Cell> portalCells;
    private final Set<Cell> portalCellSet;
    private final boolean exactSnapshot;

    private PortalSource(
            UUID worldId,
            Collection<Cell> portalCells,
            boolean exactSnapshot) {
        if (portalCells.isEmpty()) {
            throw new IllegalArgumentException(
                    "Portal source requires at least one portal cell.");
        }
        this.worldId = worldId;
        this.portalCells = portalCells.stream()
                .distinct()
                .sorted(Comparator.comparingInt(Cell::y)
                        .thenComparingInt(Cell::x)
                        .thenComparingInt(Cell::z))
                .toList();
        this.portalCellSet = Set.copyOf(this.portalCells);
        this.exactSnapshot = exactSnapshot;
        this.minX = this.portalCells.stream().mapToInt(Cell::x).min()
                .orElseThrow();
        this.minY = this.portalCells.stream().mapToInt(Cell::y).min()
                .orElseThrow();
        this.minZ = this.portalCells.stream().mapToInt(Cell::z).min()
                .orElseThrow();
        this.maxX = this.portalCells.stream().mapToInt(Cell::x).max()
                .orElseThrow();
        this.maxY = this.portalCells.stream().mapToInt(Cell::y).max()
                .orElseThrow();
        this.maxZ = this.portalCells.stream().mapToInt(Cell::z).max()
                .orElseThrow();
    }

    static PortalSource fromBlocks(Collection<Block> blocks) {
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Portal source requires at least one block.");
        }

        Block first = blocks.iterator().next();
        UUID worldId = first.getWorld().getUID();
        List<Cell> cells = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            if (!block.getWorld().getUID().equals(worldId)) {
                throw new IllegalArgumentException(
                        "Portal source blocks must share one world.");
            }
            cells.add(new Cell(block.getX(), block.getY(), block.getZ()));
        }
        return new PortalSource(worldId, cells, true);
    }

    static PortalSource fromCells(UUID worldId, Collection<Cell> cells) {
        return new PortalSource(worldId, cells, true);
    }

    static PortalSource from(ConfigurationSection section) {
        UUID worldId = UUID.fromString(section.getString("world-id", ""));
        List<String> encodedCells = section.getStringList("portal-cells");
        if (!encodedCells.isEmpty()) {
            List<Cell> cells = encodedCells.stream()
                    .map(Cell::decode)
                    .toList();
            return new PortalSource(worldId, cells,
                    section.getInt("snapshot-version", SNAPSHOT_VERSION)
                            >= SNAPSHOT_VERSION);
        }

        // ## Legacy migration: old source files only knew a bounding box.
        // Requiring every box cell to remain a portal is conservative: an old
        // irregular source retires and is rediscovered with an exact snapshot.
        List<Cell> legacyCells = new ArrayList<>();
        for (int x = section.getInt("min-x");
                x <= section.getInt("max-x"); x++) {
            for (int y = section.getInt("min-y");
                    y <= section.getInt("max-y"); y++) {
                for (int z = section.getInt("min-z");
                        z <= section.getInt("max-z"); z++) {
                    legacyCells.add(new Cell(x, y, z));
                }
            }
        }
        return new PortalSource(worldId, legacyCells, false);
    }

    void writeTo(ConfigurationSection section) {
        section.set("snapshot-version", SNAPSHOT_VERSION);
        section.set("world-id", worldId.toString());
        section.set("min-x", minX);
        section.set("min-y", minY);
        section.set("min-z", minZ);
        section.set("max-x", maxX);
        section.set("max-y", maxY);
        section.set("max-z", maxZ);
        section.set("portal-cells", portalCells.stream()
                .map(Cell::encoded)
                .toList());
    }

    World world() {
        return Bukkit.getWorld(worldId);
    }

    UUID worldId() {
        return worldId;
    }

    Location center(World world) {
        return new Location(world, centerX(), centerY(), centerZ());
    }

    String key() {
        return worldId + ":" + minX + ":" + minY + ":" + minZ
                + ":" + maxX + ":" + maxY + ":" + maxZ;
    }

    String shortDescription() {
        return "near " + centerX() + "," + centerY() + "," + centerZ();
    }

    boolean matchesFingerprint(PortalSource other) {
        return worldId.equals(other.worldId)
                && portalCellSet.equals(other.portalCellSet);
    }

    boolean isExactSnapshot() {
        return exactSnapshot;
    }

    int portalCellCount() {
        return portalCells.size();
    }

    List<Cell> portalCells() {
        return portalCells;
    }

    boolean isIntact(Predicate<Cell> portalCellMatcher) {
        for (Cell cell : portalCells) {
            if (!portalCellMatcher.test(cell)) {
                return false;
            }
        }
        return true;
    }

    boolean containsPortalCell(int x, int y, int z) {
        return portalCellSet.contains(new Cell(x, y, z));
    }

    boolean touchesPortalFrame(int x, int y, int z) {
        for (Cell cell : portalCells) {
            int distance = Math.abs(cell.x() - x)
                    + Math.abs(cell.y() - y)
                    + Math.abs(cell.z() - z);
            if (distance == 1) {
                return true;
            }
        }
        return false;
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

        int dx = Math.max(Math.max(minX - block.getX(), 0),
                block.getX() - maxX);
        int dy = Math.max(Math.max(minY - block.getY(), 0),
                block.getY() - maxY);
        int dz = Math.max(Math.max(minZ - block.getZ(), 0),
                block.getZ() - maxZ);
        return Math.max(Math.max(dx, dy), dz) <= radius;
    }

    record Cell(int x, int y, int z) {
        String encoded() {
            return x + "," + y + "," + z;
        }

        static Cell decode(String encoded) {
            String[] parts = encoded.split(",", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException(
                        "Expected x,y,z portal source cell.");
            }
            return new Cell(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        }
    }
}