package org.evolution.features.treeevolution;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * ## Immutable measurements captured before the first evolution mutation.
 */
record TreeSourcePattern(
        int height,
        int trunkFootprint,
        int logCount,
        int leafCount,
        int branchSpread,
        int canopyRadius,
        int canopyDepth,
        double canopyStartRatio,
        int crownTiers,
        int trunkDrift,
        boolean measured
) {
    static TreeSourcePattern capture(TreeCandidate candidate) {
        int minLeafY = Integer.MAX_VALUE;
        int maxLeafY = Integer.MIN_VALUE;
        int maxLogDistance = 0;
        int maxLeafDistance = 0;
        int logs = 0;
        int leaves = 0;
        Map<Integer, Set<String>> lowTrunkColumns = new HashMap<>();
        Set<Integer> leafLevels = new HashSet<>();
        Set<String> topLogColumns = new HashSet<>();
        int topLogY = Integer.MIN_VALUE;

        for (String key : candidate.naturalKeys()) {
            Coordinate coordinate = coordinate(key);
            if (coordinate == null) {
                continue;
            }
            Block block = candidate.world().getBlockAt(
                    coordinate.x(), coordinate.y(), coordinate.z());
            Material material = block.getType();
            if (material == candidate.species().logMaterial()) {
                logs++;
                maxLogDistance = Math.max(maxLogDistance,
                        horizontalDistance(candidate, coordinate));
                if (coordinate.y() <= candidate.baseY() + 2) {
                    lowTrunkColumns.computeIfAbsent(
                            coordinate.y(), ignored -> new HashSet<>())
                            .add(coordinate.x() + ":" + coordinate.z());
                }
                if (coordinate.y() > topLogY) {
                    topLogY = coordinate.y();
                    topLogColumns.clear();
                }
                if (coordinate.y() == topLogY) {
                    topLogColumns.add(
                            coordinate.x() + ":" + coordinate.z());
                }
            } else if (material == candidate.species().leafMaterial()) {
                leaves++;
                minLeafY = Math.min(minLeafY, coordinate.y());
                maxLeafY = Math.max(maxLeafY, coordinate.y());
                maxLeafDistance = Math.max(maxLeafDistance,
                        horizontalDistance(candidate, coordinate));
                leafLevels.add(coordinate.y());
            }
        }

        int footprint = lowTrunkColumns.values().stream()
                .mapToInt(Set::size)
                .max()
                .orElse(1);
        int sourceHeight = Math.max(1, candidate.height());
        int canopyDepth = leaves == 0
                ? 0 : Math.max(1, maxLeafY - minLeafY + 1);
        double canopyStart = leaves == 0
                ? 0.5D
                : Math.max(0.0D, Math.min(1.0D,
                        (minLeafY - candidate.baseY())
                                / (double) sourceHeight));
        int tiers = countCrownTiers(leafLevels);
        int drift = topLogColumns.stream()
                .map(TreeSourcePattern::column)
                .mapToInt(column -> Math.max(
                        Math.abs(column.x() - candidate.baseX()),
                        Math.abs(column.z() - candidate.baseZ())))
                .max()
                .orElse(0);
        return new TreeSourcePattern(
                sourceHeight,
                Math.max(1, footprint),
                Math.max(logs, candidate.connectedLogs()),
                Math.max(leaves, candidate.connectedLeaves()),
                maxLogDistance,
                maxLeafDistance,
                canopyDepth,
                canopyStart,
                tiers,
                drift,
                true);
    }

    static TreeSourcePattern fromSnapshot(
            int baseX,
            int baseY,
            int baseZ,
            Collection<String> logKeys,
            Collection<String> leafKeys
    ) {
        int minLeafY = Integer.MAX_VALUE;
        int maxLeafY = Integer.MIN_VALUE;
        int maxLogY = Integer.MIN_VALUE;
        int maxLogDistance = 0;
        int maxLeafDistance = 0;
        Map<Integer, Set<String>> lowTrunkColumns = new HashMap<>();
        Set<Integer> leafLevels = new HashSet<>();
        Set<String> topLogColumns = new HashSet<>();

        for (String key : logKeys) {
            Coordinate coordinate = coordinate(key);
            if (coordinate == null) {
                continue;
            }
            maxLogDistance = Math.max(maxLogDistance,
                    horizontalDistance(baseX, baseZ, coordinate));
            if (coordinate.y() <= baseY + 2) {
                lowTrunkColumns.computeIfAbsent(
                                coordinate.y(),
                                ignored -> new HashSet<>())
                        .add(coordinate.x() + ":" + coordinate.z());
            }
            if (coordinate.y() > maxLogY) {
                maxLogY = coordinate.y();
                topLogColumns.clear();
            }
            if (coordinate.y() == maxLogY) {
                topLogColumns.add(
                        coordinate.x() + ":" + coordinate.z());
            }
        }
        for (String key : leafKeys) {
            Coordinate coordinate = coordinate(key);
            if (coordinate == null) {
                continue;
            }
            minLeafY = Math.min(minLeafY, coordinate.y());
            maxLeafY = Math.max(maxLeafY, coordinate.y());
            maxLeafDistance = Math.max(maxLeafDistance,
                    horizontalDistance(baseX, baseZ, coordinate));
            leafLevels.add(coordinate.y());
        }

        int logCount = logKeys.size();
        int leafCount = leafKeys.size();
        boolean measured = logCount > 0 || leafCount > 0;
        int height = maxLogY == Integer.MIN_VALUE
                ? 1 : Math.max(1, maxLogY - baseY + 1);
        int footprint = lowTrunkColumns.values().stream()
                .mapToInt(Set::size)
                .max()
                .orElse(1);
        int canopyDepth = leafCount == 0
                ? 0 : Math.max(1, maxLeafY - minLeafY + 1);
        double canopyStart = leafCount == 0
                ? 0.5D
                : Math.max(0.0D, Math.min(1.0D,
                        (minLeafY - baseY) / (double) height));
        int drift = topLogColumns.stream()
                .map(TreeSourcePattern::column)
                .mapToInt(column -> Math.max(
                        Math.abs(column.x() - baseX),
                        Math.abs(column.z() - baseZ)))
                .max()
                .orElse(0);
        return new TreeSourcePattern(
                height,
                Math.max(1, footprint),
                logCount,
                leafCount,
                maxLogDistance,
                maxLeafDistance,
                canopyDepth,
                canopyStart,
                countCrownTiers(leafLevels),
                drift,
                measured);
    }

    static TreeSourcePattern unknown() {
        return new TreeSourcePattern(
                0, 1, 0, 0, 0, 0, 0, 0.5D, 0, 0, false);
    }

    static TreeSourcePattern legacy(
            int height,
            int trunkWidth,
            int canopyRadius,
            int canopyLayers
    ) {
        return new TreeSourcePattern(
                Math.max(0, height),
                Math.max(1, trunkWidth * trunkWidth),
                0,
                0,
                Math.max(0, canopyRadius / 2),
                Math.max(0, canopyRadius),
                Math.max(0, canopyLayers + 2),
                0.5D,
                Math.max(0, canopyLayers),
                0,
                false);
    }

    private static int horizontalDistance(
            TreeCandidate candidate,
            Coordinate coordinate
    ) {
        return horizontalDistance(
                candidate.baseX(), candidate.baseZ(), coordinate);
    }

    private static int horizontalDistance(
            int baseX,
            int baseZ,
            Coordinate coordinate
    ) {
        return Math.max(
                Math.abs(coordinate.x() - baseX),
                Math.abs(coordinate.z() - baseZ));
    }

    private static int countCrownTiers(Set<Integer> levels) {
        if (levels.isEmpty()) {
            return 0;
        }
        int tiers = 0;
        int previous = Integer.MIN_VALUE;
        for (int level : levels.stream().sorted().toList()) {
            if (previous == Integer.MIN_VALUE || level > previous + 1) {
                tiers++;
            }
            previous = level;
        }
        return Math.max(1, tiers);
    }

    private static Coordinate coordinate(String key) {
        String[] parts = key.split(":");
        if (parts.length < 3) {
            return null;
        }
        try {
            int offset = parts.length - 3;
            return new Coordinate(
                    Integer.parseInt(parts[offset]),
                    Integer.parseInt(parts[offset + 1]),
                    Integer.parseInt(parts[offset + 2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Coordinate column(String key) {
        String[] parts = key.split(":");
        return new Coordinate(
                Integer.parseInt(parts[0]), 0, Integer.parseInt(parts[1]));
    }

    private record Coordinate(int x, int y, int z) {
    }
}
