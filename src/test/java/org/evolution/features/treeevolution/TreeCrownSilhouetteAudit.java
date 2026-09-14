package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ## Measures canopy shape without consulting planner intent.
 *
 * <p>Volume and connectivity alone allow rectangular decks to pass. These
 * measurements inspect each horizontal leaf slice for filled rectangles,
 * long straight borders, repeated floors, and an unnaturally uniform lower
 * contour.
 */
final class TreeCrownSilhouetteAudit {
    record Point(int x, int y, int z) {
    }

    record Report(
            boolean passed,
            List<String> failures,
            double maximumLayerFill,
            double maximumStraightBorder,
            int repeatedLayerPairs,
            int bottomLevels
    ) {
        String metrics() {
            return "layer-fill=" + round(maximumLayerFill)
                    + " straight-border="
                    + round(maximumStraightBorder)
                    + " repeated-layers=" + repeatedLayerPairs
                    + " bottom-levels=" + bottomLevels;
        }
    }

    private TreeCrownSilhouetteAudit() {
    }

    static Report inspect(
            TreeDna dna,
            Collection<Point> leaves,
            boolean finalState
    ) {
        Map<Integer, Set<Horizontal>> layers = new HashMap<>();
        Map<Horizontal, Integer> bottoms = new HashMap<>();
        for (Point leaf : leaves) {
            Horizontal horizontal = new Horizontal(leaf.x(), leaf.z());
            layers.computeIfAbsent(leaf.y(), ignored -> new HashSet<>())
                    .add(horizontal);
            bottoms.merge(horizontal, leaf.y(), Math::min);
        }

        double maximumFill = 0.0D;
        double maximumStraightBorder = 0.0D;
        List<Integer> orderedY = layers.keySet().stream().sorted().toList();
        for (int y : orderedY) {
            Layer layer = Layer.of(layers.get(y));
            if (layer.area() < 20 || layer.widthX() < 4
                    || layer.widthZ() < 4) {
                continue;
            }
            maximumFill = Math.max(maximumFill, layer.fill());
            maximumStraightBorder = Math.max(
                    maximumStraightBorder, layer.straightBorder());
        }

        int repeatedPairs = 0;
        for (int index = 1; index < orderedY.size(); index++) {
            int previousY = orderedY.get(index - 1);
            int currentY = orderedY.get(index);
            if (currentY != previousY + 1) {
                continue;
            }
            double similarity = jaccard(
                    layers.get(previousY), layers.get(currentY));
            if (similarity >= 0.92D
                    && Math.min(layers.get(previousY).size(),
                            layers.get(currentY).size()) >= 16) {
                repeatedPairs++;
            }
        }

        int bottomLevels = new HashSet<>(bottoms.values()).size();
        List<String> failures = new ArrayList<>();
        if (finalState && leaves.size() >= 30) {
            double fillLimit = switch (dna.species()) {
                case SPRUCE -> 0.96D;
                case ACACIA -> 0.86D;
                default -> 0.88D;
            };
            if (maximumFill > fillLimit
                    && maximumStraightBorder > 0.70D) {
                failures.add("rectangular-canopy-layer fill="
                        + round(maximumFill) + " border="
                        + round(maximumStraightBorder));
            }
            int repetitionLimit = switch (dna.species()) {
                case SPRUCE -> 4;
                case ACACIA, CHERRY -> 2;
                default -> 1;
            };
            if (repeatedPairs > repetitionLimit) {
                failures.add("repeated-canopy-decks=" + repeatedPairs
                        + " limit=" + repetitionLimit);
            }
            int bottomMinimum = dna.species() == TreeSpecies.ACACIA
                    ? 2 : 3;
            if (bottomLevels < bottomMinimum && leaves.size() >= 45) {
                failures.add("uniform-canopy-bottom-levels="
                        + bottomLevels + " minimum=" + bottomMinimum);
            }
        }
        return new Report(
                failures.isEmpty(), List.copyOf(failures),
                maximumFill, maximumStraightBorder,
                repeatedPairs, bottomLevels);
    }

    private static double jaccard(
            Set<Horizontal> first,
            Set<Horizontal> second
    ) {
        Set<Horizontal> intersection = new HashSet<>(first);
        intersection.retainAll(second);
        Set<Horizontal> union = new HashSet<>(first);
        union.addAll(second);
        return union.isEmpty()
                ? 0.0D : intersection.size() / (double) union.size();
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private record Horizontal(int x, int z) {
    }

    private record Layer(
            Set<Horizontal> points,
            int minX,
            int maxX,
            int minZ,
            int maxZ
    ) {
        static Layer of(Set<Horizontal> points) {
            return new Layer(
                    points,
                    points.stream().mapToInt(Horizontal::x)
                            .min().orElse(0),
                    points.stream().mapToInt(Horizontal::x)
                            .max().orElse(0),
                    points.stream().mapToInt(Horizontal::z)
                            .min().orElse(0),
                    points.stream().mapToInt(Horizontal::z)
                            .max().orElse(0));
        }

        int widthX() {
            return maxX - minX + 1;
        }

        int widthZ() {
            return maxZ - minZ + 1;
        }

        int area() {
            return widthX() * widthZ();
        }

        double fill() {
            return points.size() / (double) Math.max(1, area());
        }

        double straightBorder() {
            int occupied = 0;
            int perimeter = Math.max(1,
                    (widthX() * 2) + (widthZ() * 2) - 4);
            for (Horizontal point : points) {
                if (point.x() == minX || point.x() == maxX
                        || point.z() == minZ || point.z() == maxZ) {
                    occupied++;
                }
            }
            return occupied / (double) perimeter;
        }
    }
}
