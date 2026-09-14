package org.evolution.features.treeevolution;

import java.util.Map;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Cell;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Ownership;

/**
 * ## Writes every Y slice across the union of planned and live voxel bounds.
 */
final class TreeReplayVolumeRenderer {
    private TreeReplayVolumeRenderer() {
    }

    static String render(
            String title,
            TreeDna dna,
            TreePlan plan,
            TreeConstructionReplayWorld world,
            String progress
    ) {
        Bounds bounds = Bounds.from(dna, plan, world.cells());
        StringBuilder output = new StringBuilder();
        output.append("## ").append(title).append(System.lineSeparator());
        output.append("## Full-volume bounds x=")
                .append(bounds.minX()).append("..").append(bounds.maxX())
                .append(" y=").append(bounds.minY()).append("..")
                .append(bounds.maxY())
                .append(" z=").append(bounds.minZ()).append("..")
                .append(bounds.maxZ()).append(System.lineSeparator());
        output.append("## Legend: T/B/L live evolved, s/o source, N neighbor, ")
                .append("t/b/l planned missing").append(System.lineSeparator());
        output.append("progress=").append(progress)
                .append(" unresolved-source=")
                .append(dna.unresolvedOriginalShapeLeafCount())
                .append(System.lineSeparator());
        for (int y = bounds.maxY(); y >= bounds.minY(); y--) {
            output.append("y=").append(y)
                    .append(" rel=").append(y - dna.baseY())
                    .append(System.lineSeparator());
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    String key = x + ":" + y + ":" + z;
                    output.append(cell(
                            world.cell(key), plan.blocksByKey().get(key)));
                }
                output.append(System.lineSeparator());
            }
        }
        return output.toString();
    }

    private static char cell(Cell live, PlannedTreeBlock planned) {
        if (live != null) {
            if (live.ownership() == Ownership.NEIGHBOR) {
                return 'N';
            }
            if (live.ownership() == Ownership.SOURCE) {
                return live.material().name().endsWith("_LEAVES")
                        ? 'o' : 's';
            }
            return switch (live.role()) {
                case TRUNK -> 'T';
                case BRANCH -> 'B';
                case CANOPY -> 'L';
                default -> 'E';
            };
        }
        if (planned == null) {
            return '.';
        }
        return switch (planned.role()) {
            case TRUNK -> 't';
            case BRANCH -> 'b';
            case CANOPY -> 'l';
            default -> '+';
        };
    }

    private record Bounds(
            int minX, int maxX,
            int minY, int maxY,
            int minZ, int maxZ
    ) {
        static Bounds from(
                TreeDna dna,
                TreePlan plan,
                Map<String, Cell> cells
        ) {
            int minX = dna.baseX();
            int maxX = dna.baseX();
            int minY = dna.baseY();
            int maxY = dna.baseY();
            int minZ = dna.baseZ();
            int maxZ = dna.baseZ();
            for (PlannedTreeBlock block : plan.orderedBlocks()) {
                minX = Math.min(minX, block.x());
                maxX = Math.max(maxX, block.x());
                minY = Math.min(minY, block.y());
                maxY = Math.max(maxY, block.y());
                minZ = Math.min(minZ, block.z());
                maxZ = Math.max(maxZ, block.z());
            }
            for (String key : cells.keySet()) {
                Coordinate coordinate = coordinate(key);
                minX = Math.min(minX, coordinate.x());
                maxX = Math.max(maxX, coordinate.x());
                minY = Math.min(minY, coordinate.y());
                maxY = Math.max(maxY, coordinate.y());
                minZ = Math.min(minZ, coordinate.z());
                maxZ = Math.max(maxZ, coordinate.z());
            }
            return new Bounds(
                    minX, maxX, minY, maxY, minZ, maxZ);
        }
    }

    private static Coordinate coordinate(String value) {
        String[] parts = value.split(":");
        int offset = parts.length - 3;
        return new Coordinate(
                Integer.parseInt(parts[offset]),
                Integer.parseInt(parts[offset + 1]),
                Integer.parseInt(parts[offset + 2]));
    }

    private record Coordinate(int x, int y, int z) {
    }
}
