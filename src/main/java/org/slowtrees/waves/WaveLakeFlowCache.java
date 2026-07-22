package org.slowtrees.waves;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.World;

final class WaveLakeFlowCache {
    private static final long REFRESH_TICKS = 100L;
    private static final int MINIMUM_MOVE_REFRESH_BLOCKS = 8;
    private static final int MINIMUM_GRID_STEP = 2;
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();

    Snapshot snapshot(UUID playerId, World world, int centerX, int centerZ, int radius,
            int step, long tick, WaveSurfaceCache surfaces, WaveConfig config) {
        // ## Lake topology is structural, so a two-block grid is sufficient.
        // Visible wave cells remain at the configured renderer spacing.
        int sampleStep = Math.max(MINIMUM_GRID_STEP, step);
        int anchoredCenterX = anchorToWorldGrid(centerX, sampleStep);
        int anchoredCenterZ = anchorToWorldGrid(centerZ, sampleStep);
        int movementBuffer = Math.max(MINIMUM_MOVE_REFRESH_BLOCKS,
                Math.max(0, radius - config.renderRadius()));
        Snapshot current = snapshots.get(playerId);
        if (current != null && current.worldId().equals(world.getUID())
                && current.radius() == radius && current.step() == sampleStep) {
            boolean insideStableWindow = Math.hypot(
                    centerX - current.centerX(), centerZ - current.centerZ()) <= movementBuffer;
            if (insideStableWindow && tick - current.createdTick() <= REFRESH_TICKS) {
                return current;
            }
            if (insideStableWindow) {
                // ## Refresh terrain data without moving the topology origin.
                anchoredCenterX = current.centerX();
                anchoredCenterZ = current.centerZ();
            }
        }

        int gridRadius = Math.max(1, radius / sampleStep);
        int diameter = (gridRadius * 2) + 1;
        int cells = diameter * diameter;
        boolean[] known = new boolean[cells];
        boolean[] water = new boolean[cells];
        int knownCells = 0;
        int waterCells = 0;
        int inheritedCells = 0;
        for (int localZ = 0; localZ < diameter; localZ++) {
            int dz = (localZ - gridRadius) * sampleStep;
            for (int localX = 0; localX < diameter; localX++) {
                int dx = (localX - gridRadius) * sampleStep;
                if ((dx * dx) + (dz * dz) > radius * radius) {
                    continue;
                }
                int index = (localZ * diameter) + localX;
                int worldX = anchoredCenterX + dx;
                int worldZ = anchoredCenterZ + dz;
                WaveSurfaceCache.TopologyLookup lookup = surfaces.topology(
                        world, worldX, worldZ, tick, config);
                boolean cellKnown = lookup.known();
                boolean cellWater = lookup.water();
                if (!cellKnown && current != null && current.worldId().equals(world.getUID())
                        && current.isKnown(worldX, worldZ)) {
                    // ## A Folia ownership boundary is UNKNOWN, not newly discovered land.
                    // Inherit the last confirmed cell so one partial refresh cannot redraw a lake.
                    cellKnown = true;
                    cellWater = current.isWater(worldX, worldZ);
                    inheritedCells++;
                }
                known[index] = cellKnown;
                water[index] = cellWater;
                knownCells += cellKnown ? 1 : 0;
                waterCells += cellWater ? 1 : 0;
            }
        }
        LakeWaveFlowField field = LakeWaveFlowField.build(diameter, diameter, known, water);
        Snapshot rebuilt = new Snapshot(world.getUID(), anchoredCenterX, anchoredCenterZ, radius,
                gridRadius, sampleStep, tick, knownCells, waterCells, inheritedCells, field);
        snapshots.put(playerId, rebuilt);
        return rebuilt;
    }

    void invalidate(UUID playerId) {
        snapshots.remove(playerId);
    }

    void clear() {
        snapshots.clear();
    }

    // ## Every rebuild uses the same world-space lattice phase. Moving a player
    // can translate the cached window, but cannot slide its sampled coastline.
    static int anchorToWorldGrid(int coordinate, int step) {
        int safeStep = Math.max(1, step);
        return Math.floorDiv(coordinate, safeStep) * safeStep;
    }

    static int advanceToWorldGrid(int minimum, int step) {
        int safeStep = Math.max(1, step);
        int remainder = Math.floorMod(minimum, safeStep);
        return remainder == 0 ? minimum : minimum + (safeStep - remainder);
    }

    record Snapshot(UUID worldId, int centerX, int centerZ, int radius, int gridRadius,
            int step, long createdTick, int knownCells, int waterCells, int inheritedCells,
            LakeWaveFlowField field) {
        Snapshot(UUID worldId, int centerX, int centerZ, int radius, int gridRadius,
                int step, long createdTick, int knownCells, int waterCells,
                LakeWaveFlowField field) {
            this(worldId, centerX, centerZ, radius, gridRadius, step, createdTick,
                    knownCells, waterCells, 0, field);
        }

        LakeWaveFlowField.Cell cell(int worldX, int worldZ) {
            int dx = worldX - centerX;
            int dz = worldZ - centerZ;
            int localX = (int) Math.round(dx / (double) step) + gridRadius;
            int localZ = (int) Math.round(dz / (double) step) + gridRadius;
            LakeWaveFlowField.Cell cell = field.cell(localX, localZ);
            if (!cell.shoreGuided()) {
                return cell;
            }
            return new LakeWaveFlowField.Cell(true, cell.enclosed(),
                    cell.shoreDistance() * step,
                    cell.sourceDistance() * step,
                    cell.directionX(), cell.directionZ());
        }

        boolean isKnown(int worldX, int worldZ) {
            int dx = worldX - centerX;
            int dz = worldZ - centerZ;
            int localX = (int) Math.round(dx / (double) step) + gridRadius;
            int localZ = (int) Math.round(dz / (double) step) + gridRadius;
            return field.isKnown(localX, localZ);
        }

        boolean isWater(int worldX, int worldZ) {
            int dx = worldX - centerX;
            int dz = worldZ - centerZ;
            int localX = (int) Math.round(dx / (double) step) + gridRadius;
            int localZ = (int) Math.round(dz / (double) step) + gridRadius;
            return field.isWater(localX, localZ);
        }
    }
}