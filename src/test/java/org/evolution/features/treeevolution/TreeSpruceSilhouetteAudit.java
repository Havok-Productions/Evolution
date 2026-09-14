package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ## Independent conifer-envelope audit for live and planned spruce crowns.
 */
final class TreeSpruceSilhouetteAudit {
    record Point(int x, int y, int z) {
    }

    record Report(
            List<String> failures,
            int internalGaps,
            int postPeakWidenings,
            int maximumWidth,
            int topWidth
    ) {
        static Report notApplicable() {
            return new Report(List.of(), 0, 0, 0, 0);
        }
    }

    private TreeSpruceSilhouetteAudit() {
    }

    static Report inspect(
            TreeDna dna,
            Collection<Point> leaves
    ) {
        if (dna.species() != TreeSpecies.SPRUCE
                || leaves.size() < 30) {
            return Report.notApplicable();
        }
        Map<Integer, Bounds> layers = new HashMap<>();
        for (Point leaf : leaves) {
            layers.computeIfAbsent(
                    leaf.y(), ignored -> new Bounds())
                    .include(leaf.x(), leaf.z());
        }
        List<Integer> yLevels = layers.keySet().stream()
                .sorted().toList();
        int internalGaps = 0;
        for (int index = 1; index < yLevels.size(); index++) {
            internalGaps += Math.max(
                    0, yLevels.get(index) - yLevels.get(index - 1) - 1);
        }
        List<Integer> widths = yLevels.stream()
                .map(y -> layers.get(y).width())
                .toList();
        List<String> widthTrace = yLevels.stream()
                .map(y -> y + ":" + layers.get(y).width())
                .toList();
        int maximum = widths.stream().max(
                Comparator.naturalOrder()).orElse(0);
        int peak = widths.indexOf(maximum);
        int widenings = 0;
        int localMinimum = peak < widths.size()
                ? widths.get(peak) : 0;
        int maximumRebound = 0;
        for (int index = Math.max(peak + 1, 1);
                index < widths.size(); index++) {
            localMinimum = Math.min(localMinimum, widths.get(index));
            if (widths.get(index) > widths.get(index - 1)) {
                widenings++;
            }
            maximumRebound = Math.max(
                    maximumRebound,
                    widths.get(index) - localMinimum);
        }
        int topWidth = widths.isEmpty() ? 0
                : widths.get(widths.size() - 1);
        List<String> failures = new ArrayList<>();
        if (internalGaps > 0) {
            failures.add("spruce-crown-internal-gaps=" + internalGaps);
        }
        // ## One-block edge wobble can occur where branch wood replaces the
        // outermost leaf. Reject an actual second lobe: either a two-block
        // rebound after tapering or three separate outward reversals.
        if (widenings > 2 || maximumRebound > 1) {
            failures.add("spruce-post-peak-widenings=" + widenings
                    + " maximum-rebound=" + maximumRebound
                    + " limits=2/1 y-widths=" + widthTrace);
        }
        if (maximum >= 7
                && topWidth > Math.max(3, maximum / 2)) {
            failures.add("spruce-untapered-leader top=" + topWidth
                    + " maximum=" + maximum);
        }
        return new Report(
                List.copyOf(failures), internalGaps, widenings,
                maximum, topWidth);
    }

    private static final class Bounds {
        private int minX = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        void include(int x, int z) {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        int width() {
            return Math.max(maxX - minX + 1, maxZ - minZ + 1);
        }
    }
}
