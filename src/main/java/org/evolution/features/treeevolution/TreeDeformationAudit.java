package org.evolution.features.treeevolution;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Independent live-voxel quality audit used by automatic anomaly capture.
 *
 * <p>## This audit never asks whether the planner considers its own target
 * valid. It measures occupied voxels: support connectivity, exposed wood,
 * canopy continuity/thickness, flat cuts, asymmetry, and species silhouette.</p>
 */
final class TreeDeformationAudit {
    private static final int[][] FACES = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    Report inspect(
            TreeDna dna,
            Collection<TreeVoxelSnapshotRenderer.Voxel> voxels,
            boolean finalState
    ) {
        return inspect(dna.species(), dna.variant(), dna.maturityStage(),
                voxels, finalState);
    }

    Report inspect(
            TreeSpecies species,
            TreeVariant variant,
            TreeMaturityStage maturityStage,
            Collection<TreeVoxelSnapshotRenderer.Voxel> voxels,
            boolean finalState
    ) {
        Map<String, TreeVoxelSnapshotRenderer.Voxel> byKey =
                new HashMap<>();
        List<TreeVoxelSnapshotRenderer.Voxel> wood = new ArrayList<>();
        List<TreeVoxelSnapshotRenderer.Voxel> leaves = new ArrayList<>();
        for (TreeVoxelSnapshotRenderer.Voxel voxel : voxels) {
            byKey.put(key(voxel.x(), voxel.y(), voxel.z()), voxel);
            if (voxel.role() == TreeBlockRole.CANOPY) {
                leaves.add(voxel);
            } else if (isWood(voxel.role())) {
                wood.add(voxel);
            }
        }

        int disconnectedWood = disconnectedWood(wood, byKey);
        int unsupportedLeaves = unsupportedLeaves(leaves, byKey);
        int exposedWood = exposedWood(wood, byKey);
        int bareTerminals = bareTerminals(wood, byKey);
        Shape shape = shape(leaves);
        double asymmetry = quadrantAsymmetry(leaves);
        double conformity = speciesConformity(
                species, maturityStage, shape);

        List<String> failures = new ArrayList<>();
        if (disconnectedWood > 0) {
            failures.add("disconnected-wood=" + disconnectedWood);
        }
        if (unsupportedLeaves > Math.max(2, leaves.size() / 20)) {
            failures.add("unsupported-leaves=" + unsupportedLeaves);
        }
        if (bareTerminals > 0 && (finalState || leaves.size() >= 24)) {
            failures.add("bare-wood-terminals=" + bareTerminals);
        }
        if (finalState && exposedWood > Math.max(3, wood.size() / 5)) {
            failures.add("exposed-wood=" + exposedWood);
        }
        if (finalState && shape.bottomLevels() < 2
                && leaves.size() >= 35) {
            failures.add("flat-bottom-levels=" + shape.bottomLevels());
        }
        if (finalState && shape.maximumStraightBorder() > 0.76D
                && shape.maximumLayerFill() > 0.84D) {
            failures.add("flat-cut-border="
                    + round(shape.maximumStraightBorder()));
        }
        if (finalState && shape.repeatedLayers() > 2
                && species != TreeSpecies.SPRUCE) {
            failures.add("repeated-canopy-layers="
                    + shape.repeatedLayers());
        }
        if (finalState && asymmetry > 0.72D
                && variant != TreeVariant.ACACIA_WINDSWEPT) {
            failures.add("extreme-canopy-asymmetry=" + round(asymmetry));
        }
        if (finalState && conformity < 0.58D) {
            failures.add("species-conformity=" + round(conformity));
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("wood", wood.size());
        metrics.put("leaves", leaves.size());
        metrics.put("disconnected-wood", disconnectedWood);
        metrics.put("unsupported-leaves", unsupportedLeaves);
        metrics.put("exposed-wood", exposedWood);
        metrics.put("bare-wood-terminals", bareTerminals);
        metrics.put("crown-width-x", shape.widthX());
        metrics.put("crown-width-z", shape.widthZ());
        metrics.put("crown-height", shape.height());
        metrics.put("crown-thickness", shape.thickness());
        metrics.put("maximum-layer-fill",
                round(shape.maximumLayerFill()));
        metrics.put("maximum-straight-border",
                round(shape.maximumStraightBorder()));
        metrics.put("repeated-canopy-layers", shape.repeatedLayers());
        metrics.put("bottom-levels", shape.bottomLevels());
        metrics.put("quadrant-asymmetry", round(asymmetry));
        metrics.put("species-conformity", round(conformity));
        return new Report(
                failures.isEmpty(), List.copyOf(failures),
                Map.copyOf(metrics));
    }

    private int disconnectedWood(
            List<TreeVoxelSnapshotRenderer.Voxel> wood,
            Map<String, TreeVoxelSnapshotRenderer.Voxel> byKey
    ) {
        if (wood.isEmpty()) {
            return 0;
        }
        int minimumY = wood.stream().mapToInt(
                TreeVoxelSnapshotRenderer.Voxel::y).min().orElse(0);
        Set<String> visited = new HashSet<>();
        Deque<TreeVoxelSnapshotRenderer.Voxel> pending = new ArrayDeque<>();
        for (TreeVoxelSnapshotRenderer.Voxel voxel : wood) {
            if (voxel.y() == minimumY && visited.add(voxelKey(voxel))) {
                pending.add(voxel);
            }
        }
        while (!pending.isEmpty()) {
            TreeVoxelSnapshotRenderer.Voxel current = pending.removeFirst();
            // ## Constructor support accepts touching corners and edges inside
            // one block. The independent audit must use the same geometric
            // vocabulary or it reports valid angled limbs as disconnected.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        String nextKey = key(current.x() + dx,
                                current.y() + dy, current.z() + dz);
                        TreeVoxelSnapshotRenderer.Voxel next = byKey.get(nextKey);
                        if (next != null && isWood(next.role())
                                && visited.add(nextKey)) {
                            pending.addLast(next);
                        }
                    }
                }
            }
        }
        return Math.max(0, wood.size() - visited.size());
    }

    private int unsupportedLeaves(
            List<TreeVoxelSnapshotRenderer.Voxel> leaves,
            Map<String, TreeVoxelSnapshotRenderer.Voxel> byKey
    ) {
        Set<String> leafKeys = new HashSet<>();
        Deque<TreeVoxelSnapshotRenderer.Voxel> pending = new ArrayDeque<>();
        Set<String> supported = new HashSet<>();
        for (TreeVoxelSnapshotRenderer.Voxel leaf : leaves) {
            String leafKey = voxelKey(leaf);
            leafKeys.add(leafKey);
            boolean nearWood = false;
            for (int dx = -2; dx <= 2 && !nearWood; dx++) {
                for (int dy = -2; dy <= 2 && !nearWood; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        TreeVoxelSnapshotRenderer.Voxel neighbor = byKey.get(
                                key(leaf.x() + dx, leaf.y() + dy,
                                        leaf.z() + dz));
                        if (neighbor != null && isWood(neighbor.role())) {
                            nearWood = true;
                            break;
                        }
                    }
                }
            }
            if (nearWood && supported.add(leafKey)) {
                pending.addLast(leaf);
            }
        }
        // ## Canopy support propagates through one contiguous leaf cloud.
        // Requiring every outer leaf to sit beside wood falsely labels normal
        // fluffy crowns as detached; isolated leaf islands still fail.
        while (!pending.isEmpty()) {
            TreeVoxelSnapshotRenderer.Voxel current = pending.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        String nextKey = key(current.x() + dx,
                                current.y() + dy, current.z() + dz);
                        if (!leafKeys.contains(nextKey)
                                || !supported.add(nextKey)) {
                            continue;
                        }
                        pending.addLast(byKey.get(nextKey));
                    }
                }
            }
        }
        return Math.max(0, leaves.size() - supported.size());
    }

    private int exposedWood(
            List<TreeVoxelSnapshotRenderer.Voxel> wood,
            Map<String, TreeVoxelSnapshotRenderer.Voxel> byKey
    ) {
        int exposed = 0;
        for (TreeVoxelSnapshotRenderer.Voxel voxel : wood) {
            if (voxel.role() == TreeBlockRole.ROOT) {
                continue;
            }
            int leafContacts = 0;
            for (int[] face : FACES) {
                TreeVoxelSnapshotRenderer.Voxel neighbor = byKey.get(key(
                        voxel.x() + face[0], voxel.y() + face[1],
                        voxel.z() + face[2]));
                if (neighbor != null
                        && neighbor.role() == TreeBlockRole.CANOPY) {
                    leafContacts++;
                }
            }
            if (voxel.role() == TreeBlockRole.BRANCH
                    && leafContacts == 0) {
                exposed++;
            }
        }
        return exposed;
    }

    private int bareTerminals(
            List<TreeVoxelSnapshotRenderer.Voxel> wood,
            Map<String, TreeVoxelSnapshotRenderer.Voxel> byKey
    ) {
        int terminals = 0;
        for (TreeVoxelSnapshotRenderer.Voxel voxel : wood) {
            if (voxel.role() != TreeBlockRole.BRANCH) {
                continue;
            }
            int woodContacts = 0;
            int leafContacts = 0;
            for (int[] face : FACES) {
                TreeVoxelSnapshotRenderer.Voxel neighbor = byKey.get(key(
                        voxel.x() + face[0], voxel.y() + face[1],
                        voxel.z() + face[2]));
                if (neighbor == null) {
                    continue;
                }
                if (isWood(neighbor.role())) {
                    woodContacts++;
                } else if (neighbor.role() == TreeBlockRole.CANOPY) {
                    leafContacts++;
                }
            }
            if (woodContacts <= 1 && leafContacts == 0) {
                terminals++;
            }
        }
        return terminals;
    }

    private Shape shape(List<TreeVoxelSnapshotRenderer.Voxel> leaves) {
        if (leaves.isEmpty()) {
            return Shape.EMPTY;
        }
        Map<Integer, Set<String>> layers = new HashMap<>();
        Map<String, Integer> bottoms = new HashMap<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (TreeVoxelSnapshotRenderer.Voxel leaf : leaves) {
            minX = Math.min(minX, leaf.x());
            maxX = Math.max(maxX, leaf.x());
            minY = Math.min(minY, leaf.y());
            maxY = Math.max(maxY, leaf.y());
            minZ = Math.min(minZ, leaf.z());
            maxZ = Math.max(maxZ, leaf.z());
            String horizontal = leaf.x() + ":" + leaf.z();
            layers.computeIfAbsent(leaf.y(), ignored -> new HashSet<>())
                    .add(horizontal);
            bottoms.merge(horizontal, leaf.y(), Math::min);
        }
        double maximumFill = 0.0D;
        double maximumBorder = 0.0D;
        List<Integer> ys = layers.keySet().stream().sorted().toList();
        for (int y : ys) {
            Layer layer = Layer.of(layers.get(y));
            maximumFill = Math.max(maximumFill, layer.fill());
            if (layer.count() >= 16
                    && layer.widthX() >= 4 && layer.widthZ() >= 4) {
                maximumBorder = Math.max(maximumBorder, layer.border());
            }
        }
        int repeated = 0;
        for (int index = 1; index < ys.size(); index++) {
            Set<String> first = layers.get(ys.get(index - 1));
            Set<String> second = layers.get(ys.get(index));
            Set<String> union = new HashSet<>(first);
            union.addAll(second);
            Set<String> intersection = new HashSet<>(first);
            intersection.retainAll(second);
            if (union.size() >= 16
                    && intersection.size() / (double) union.size()
                            >= 0.92D) {
                repeated++;
            }
        }
        long envelope = (long) (maxX - minX + 1)
                * (maxY - minY + 1) * (maxZ - minZ + 1);
        return new Shape(
                maxX - minX + 1, maxZ - minZ + 1,
                maxY - minY + 1,
                leaves.size() / (double) Math.max(1L, envelope),
                maximumFill, maximumBorder, repeated,
                new HashSet<>(bottoms.values()).size());
    }

    private double quadrantAsymmetry(
            List<TreeVoxelSnapshotRenderer.Voxel> leaves
    ) {
        if (leaves.isEmpty()) {
            return 0.0D;
        }
        int[] quadrants = new int[4];
        for (TreeVoxelSnapshotRenderer.Voxel leaf : leaves) {
            int index = (leaf.x() >= 0 ? 1 : 0)
                    + (leaf.z() >= 0 ? 2 : 0);
            quadrants[index]++;
        }
        int min = java.util.Arrays.stream(quadrants).min().orElse(0);
        int max = java.util.Arrays.stream(quadrants).max().orElse(0);
        return (max - min) / (double) Math.max(1, leaves.size());
    }

    private double speciesConformity(
            TreeSpecies species,
            TreeMaturityStage maturityStage,
            Shape shape
    ) {
        if (shape == Shape.EMPTY || shape.height() == 0) {
            return 0.0D;
        }
        int width = Math.max(shape.widthX(), shape.widthZ());
        double aspect = width / (double) shape.height();
        double ideal = switch (species) {
            case ACACIA -> 2.4D;
            case BIRCH -> 1.0D;
            case SPRUCE -> switch (maturityStage) {
                // ## Terrain-safe young spruces have a compact 5x5 crown.
                // The old 0.7 target rewarded foliage hanging into the stump
                // band and falsely rejected the corrected square cone.
                case SMALL -> 0.9D;
                case MEDIUM -> 0.6D;
                case MATURE -> 0.5D;
                case ANCIENT -> 0.45D;
            };
            case JUNGLE -> 1.35D;
            case DARK_OAK -> 1.75D;
            case CHERRY -> 1.8D;
            case OAK, MANGROVE -> 1.45D;
        };
        return Math.max(0.0D,
                1.0D - Math.abs(aspect - ideal) / Math.max(ideal, 0.5D));
    }

    private static boolean isWood(TreeBlockRole role) {
        return role == TreeBlockRole.TRUNK
                || role == TreeBlockRole.BRANCH
                || role == TreeBlockRole.ROOT;
    }

    private static String voxelKey(TreeVoxelSnapshotRenderer.Voxel voxel) {
        return key(voxel.x(), voxel.y(), voxel.z());
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    record Report(
            boolean passed,
            List<String> failures,
            Map<String, Object> metrics
    ) {
    }

    private record Shape(
            int widthX,
            int widthZ,
            int height,
            double thickness,
            double maximumLayerFill,
            double maximumStraightBorder,
            int repeatedLayers,
            int bottomLevels
    ) {
        private static final Shape EMPTY = new Shape(
                0, 0, 0, 0.0D, 0.0D, 0.0D, 0, 0);
    }

    private record Layer(
            int widthX,
            int widthZ,
            int count,
            int borderCount
    ) {
        static Layer of(Set<String> points) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            List<int[]> parsed = new ArrayList<>();
            for (String point : points) {
                String[] split = point.split(":");
                int x = Integer.parseInt(split[0]);
                int z = Integer.parseInt(split[1]);
                parsed.add(new int[]{x, z});
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
            int borders = 0;
            for (int[] point : parsed) {
                if (point[0] == minX || point[0] == maxX
                        || point[1] == minZ || point[1] == maxZ) {
                    borders++;
                }
            }
            return new Layer(maxX - minX + 1, maxZ - minZ + 1,
                    points.size(), borders);
        }

        double fill() {
            return count / (double) Math.max(1, widthX * widthZ);
        }

        double border() {
            int perimeter = Math.max(1,
                    (widthX * 2) + (widthZ * 2) - 4);
            return borderCount / (double) perimeter;
        }
    }
}
