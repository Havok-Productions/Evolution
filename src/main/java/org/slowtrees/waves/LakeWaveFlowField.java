package org.slowtrees.waves;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

final class LakeWaveFlowField {
    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] NAVIGATION_NEIGHBORS = {
            {1, 0, 10}, {-1, 0, 10}, {0, 1, 10}, {0, -1, 10},
            {1, 1, 14}, {1, -1, 14}, {-1, 1, 14}, {-1, -1, 14}
    };

    private final int width;
    private final int height;
    private final boolean[] known;
    private final boolean[] water;
    private final int[] shoreDistance;
    private final int[] sourceDistance;
    private final boolean[] enclosed;
    private final int shoreGuidedComponents;
    private final int enclosedComponents;

    private LakeWaveFlowField(int width, int height, boolean[] known, boolean[] water,
            int[] shoreDistance, int[] sourceDistance, boolean[] enclosed,
            int shoreGuidedComponents, int enclosedComponents) {
        this.width = width;
        this.height = height;
        this.known = known;
        this.water = water;
        this.shoreDistance = shoreDistance;
        this.sourceDistance = sourceDistance;
        this.enclosed = enclosed;
        this.shoreGuidedComponents = shoreGuidedComponents;
        this.enclosedComponents = enclosedComponents;
    }

    static LakeWaveFlowField build(int width, int height, boolean[] known, boolean[] water) {
        int cells = width * height;
        if (known.length != cells || water.length != cells) {
            throw new IllegalArgumentException("lake masks must match field dimensions");
        }
        int[] distance = new int[cells];
        int[] source = new int[cells];
        boolean[] enclosedCells = new boolean[cells];
        int[] component = new int[cells];
        Arrays.fill(distance, -1);
        Arrays.fill(source, -1);
        Arrays.fill(component, -1);
        int guided = 0;
        int enclosedCount = 0;
        int componentId = 0;

        for (int index = 0; index < cells; index++) {
            if (!water[index] || component[index] >= 0) {
                continue;
            }
            List<Integer> members = new ArrayList<>();
            List<Integer> shoreSeeds = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(index);
            component[index] = componentId;
            boolean openOrUnknown = false;

            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                members.add(current);
                int x = current % width;
                int z = current / width;
                boolean touchesLand = false;
                for (int[] direction : CARDINALS) {
                    int nx = x + direction[0];
                    int nz = z + direction[1];
                    if (nx < 0 || nz < 0 || nx >= width || nz >= height) {
                        openOrUnknown = true;
                        continue;
                    }
                    int neighbor = (nz * width) + nx;
                    if (!known[neighbor]) {
                        openOrUnknown = true;
                    } else if (!water[neighbor]) {
                        touchesLand = true;
                    } else if (component[neighbor] < 0) {
                        component[neighbor] = componentId;
                        queue.addLast(neighbor);
                    }
                }
                if (touchesLand) {
                    shoreSeeds.add(current);
                }
            }

            // ## A component may continue beyond render distance and still have a
            // visible coast. Its distance basin remains useful for shore-bound fronts.
            if (!shoreSeeds.isEmpty()) {
                int maximum = assignDistances(width, height, component, componentId,
                        shoreSeeds, distance);
                boolean componentEnclosed = !openOrUnknown;
                for (int member : members) {
                    source[member] = maximum;
                    enclosedCells[member] = componentEnclosed;
                }
                guided++;
                if (componentEnclosed) {
                    enclosedCount++;
                }
            }
            componentId++;
        }
        return new LakeWaveFlowField(width, height, known.clone(), water.clone(), distance, source,
                enclosedCells, guided, enclosedCount);
    }

    private static int assignDistances(int width, int height, int[] component,
            int componentId, List<Integer> shoreSeeds, int[] distance) {
        PriorityQueue<DistanceNode> queue = new PriorityQueue<>();
        for (int seed : shoreSeeds) {
            distance[seed] = 10;
            queue.add(new DistanceNode(seed, 10));
        }
        int maximum = 10;
        while (!queue.isEmpty()) {
            DistanceNode node = queue.remove();
            int current = node.index();
            if (node.distance() != distance[current]) {
                continue;
            }
            maximum = Math.max(maximum, node.distance());
            int x = current % width;
            int z = current / width;
            for (int[] direction : NAVIGATION_NEIGHBORS) {
                int nx = x + direction[0];
                int nz = z + direction[1];
                if (nx < 0 || nz < 0 || nx >= width || nz >= height) {
                    continue;
                }
                int neighbor = (nz * width) + nx;
                if (component[neighbor] != componentId) {
                    continue;
                }
                int candidate = node.distance() + direction[2];
                if (distance[neighbor] >= 0 && distance[neighbor] <= candidate) {
                    continue;
                }
                distance[neighbor] = candidate;
                queue.add(new DistanceNode(neighbor, candidate));
            }
        }
        return maximum;
    }

    Cell cell(int x, int z) {
        if (x < 0 || z < 0 || x >= width || z >= height) {
            return Cell.open();
        }
        int index = (z * width) + x;
        int distanceCost = shoreDistance[index];
        int sourceCost = sourceDistance[index];
        if (distanceCost < 10 || sourceCost < 10) {
            return Cell.open();
        }
        int bestX = 0;
        int bestZ = 0;
        int bestDistance = distanceCost;
        for (int[] direction : NAVIGATION_NEIGHBORS) {
            int nx = x + direction[0];
            int nz = z + direction[1];
            if (nx < 0 || nz < 0 || nx >= width || nz >= height) {
                continue;
            }
            int neighborDistance = shoreDistance[(nz * width) + nx];
            if (neighborDistance >= 10 && neighborDistance < bestDistance) {
                bestDistance = neighborDistance;
                bestX = direction[0];
                bestZ = direction[1];
            }
        }
        return new Cell(true, enclosed[index], blocks(distanceCost), blocks(sourceCost),
                bestX, bestZ);
    }

    boolean isKnown(int x, int z) {
        return x >= 0 && z >= 0 && x < width && z < height
                && known[(z * width) + x];
    }

    boolean isWater(int x, int z) {
        return x >= 0 && z >= 0 && x < width && z < height
                && water[(z * width) + x];
    }

    int shoreGuidedComponents() {
        return shoreGuidedComponents;
    }

    int enclosedComponents() {
        return enclosedComponents;
    }

    private static int blocks(int weightedDistance) {
        return Math.max(1, (weightedDistance + 9) / 10);
    }

    private record DistanceNode(int index, int distance)
            implements Comparable<DistanceNode> {
        @Override
        public int compareTo(DistanceNode other) {
            return Integer.compare(distance, other.distance);
        }
    }

    record Cell(boolean shoreGuided, boolean enclosed, int shoreDistance,
            int sourceDistance, int directionX, int directionZ) {
        static Cell open() {
            return new Cell(false, false, -1, -1, 0, 0);
        }
    }
}