package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds compact, deterministic voxel snapshots for deformation bundles. */
final class TreeVoxelSnapshotRenderer {
    private static final int MAX_POINTS = 2_500;
    private static final int MAX_AXIS = 41;

    private TreeVoxelSnapshotRenderer() {
    }

    static Map<String, Object> snapshot(
            String label,
            List<Voxel> input
    ) {
        List<Voxel> voxels = input.stream()
                .distinct()
                .sorted(Comparator.comparingInt(Voxel::y)
                        .thenComparingInt(Voxel::z)
                        .thenComparingInt(Voxel::x))
                .limit(MAX_POINTS)
                .toList();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("label", label);
        output.put("voxel-count", input.size());
        output.put("stored-voxel-count", voxels.size());
        output.put("truncated", input.size() > voxels.size());
        output.put("bounds", bounds(voxels));
        output.put("points", voxels.stream().map(Voxel::encoded).toList());
        Map<String, Object> views = new LinkedHashMap<>();
        views.put("north", view(voxels, View.NORTH));
        views.put("south", view(voxels, View.SOUTH));
        views.put("east", view(voxels, View.EAST));
        views.put("west", view(voxels, View.WEST));
        output.put("views", views);
        output.put("legend",
                "## T=trunk B=branch L=leaves R=root V=vine U=detail; rows run top-to-bottom by Y.");
        return output;
    }

    private static Map<String, Object> view(
            List<Voxel> voxels,
            View view
    ) {
        Map<String, Voxel> projected = new LinkedHashMap<>();
        for (Voxel voxel : voxels) {
            int horizontal = view.horizontal(voxel);
            String key = horizontal + ":" + voxel.y();
            Voxel current = projected.get(key);
            if (current == null
                    || view.depth(voxel) < view.depth(current)
                    || rolePriority(voxel.role())
                            > rolePriority(current.role())) {
                projected.put(key, voxel);
            }
        }
        if (projected.isEmpty()) {
            return Map.of("rows", List.of(), "empty", true);
        }
        int minH = projected.values().stream()
                .mapToInt(view::horizontal).min().orElse(0);
        int maxH = projected.values().stream()
                .mapToInt(view::horizontal).max().orElse(0);
        int minY = projected.values().stream()
                .mapToInt(Voxel::y).min().orElse(0);
        int maxY = projected.values().stream()
                .mapToInt(Voxel::y).max().orElse(0);
        boolean clipped = maxH - minH + 1 > MAX_AXIS
                || maxY - minY + 1 > MAX_AXIS;
        maxH = Math.min(maxH, minH + MAX_AXIS - 1);
        minY = Math.max(minY, maxY - MAX_AXIS + 1);
        List<String> rows = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            StringBuilder row = new StringBuilder();
            for (int h = minH; h <= maxH; h++) {
                Voxel voxel = projected.get(h + ":" + y);
                row.append(voxel == null ? '.' : symbol(voxel.role()));
            }
            rows.add(row.toString());
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("horizontal-range", minH + ".." + maxH);
        output.put("y-range", minY + ".." + maxY);
        output.put("clipped", clipped);
        output.put("rows", rows);
        return output;
    }

    private static String bounds(List<Voxel> voxels) {
        if (voxels.isEmpty()) {
            return "empty";
        }
        int minX = voxels.stream().mapToInt(Voxel::x).min().orElse(0);
        int maxX = voxels.stream().mapToInt(Voxel::x).max().orElse(0);
        int minY = voxels.stream().mapToInt(Voxel::y).min().orElse(0);
        int maxY = voxels.stream().mapToInt(Voxel::y).max().orElse(0);
        int minZ = voxels.stream().mapToInt(Voxel::z).min().orElse(0);
        int maxZ = voxels.stream().mapToInt(Voxel::z).max().orElse(0);
        return minX + "," + minY + "," + minZ + " -> "
                + maxX + "," + maxY + "," + maxZ;
    }

    private static int rolePriority(TreeBlockRole role) {
        return switch (role) {
            case TRUNK -> 8;
            case BRANCH -> 7;
            case ROOT -> 6;
            case CANOPY -> 5;
            case VINE -> 4;
            case FALLEN_LOG -> 3;
            case SAPLING -> 2;
            case GROUND_DETAIL -> 1;
        };
    }

    private static char symbol(TreeBlockRole role) {
        return switch (role) {
            case TRUNK -> 'T';
            case BRANCH -> 'B';
            case CANOPY -> 'L';
            case ROOT -> 'R';
            case VINE -> 'V';
            case GROUND_DETAIL -> 'U';
            case FALLEN_LOG -> 'F';
            case SAPLING -> 'S';
        };
    }

    record Voxel(int x, int y, int z, TreeBlockRole role, String material) {
        String encoded() {
            return x + "," + y + "," + z + " " + role + " "
                    + material;
        }
    }

    private enum View {
        NORTH {
            int horizontal(Voxel voxel) { return voxel.x(); }
            int depth(Voxel voxel) { return voxel.z(); }
        },
        SOUTH {
            int horizontal(Voxel voxel) { return -voxel.x(); }
            int depth(Voxel voxel) { return -voxel.z(); }
        },
        EAST {
            int horizontal(Voxel voxel) { return voxel.z(); }
            int depth(Voxel voxel) { return -voxel.x(); }
        },
        WEST {
            int horizontal(Voxel voxel) { return -voxel.z(); }
            int depth(Voxel voxel) { return voxel.x(); }
        };

        abstract int horizontal(Voxel voxel);

        abstract int depth(Voxel voxel);
    }
}
