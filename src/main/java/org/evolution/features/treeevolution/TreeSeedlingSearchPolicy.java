package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class TreeSeedlingSearchPolicy {
    private TreeSeedlingSearchPolicy() {
    }

    static List<Offset> sampleRing(
            int minimumRadius,
            int maximumRadius,
            int attemptLimit,
            long seed
    ) {
        int inner = Math.max(0, minimumRadius);
        int outer = Math.max(inner, maximumRadius);
        int innerSquared = inner * inner;
        int outerSquared = outer * outer;
        List<Offset> offsets = new ArrayList<>();
        for (int x = -outer; x <= outer; x++) {
            for (int z = -outer; z <= outer; z++) {
                int distanceSquared = (x * x) + (z * z);
                if (distanceSquared >= innerSquared
                        && distanceSquared <= outerSquared) {
                    offsets.add(new Offset(x, z));
                }
            }
        }
        Collections.shuffle(offsets, new Random(seed));
        int limit = Math.max(0, Math.min(attemptLimit, offsets.size()));
        return List.copyOf(offsets.subList(0, limit));
    }

    static int requiredBaseDistance(
            int existingCanopyRadius,
            int futureCanopyRadius
    ) {
        return Math.max(2, existingCanopyRadius)
                + Math.max(2, futureCanopyRadius) + 2;
    }

    static boolean footprintsOverlap(
            int deltaX,
            int deltaZ,
            int existingCanopyRadius,
            int futureCanopyRadius
    ) {
        int required = requiredBaseDistance(
                existingCanopyRadius, futureCanopyRadius);
        return (deltaX * deltaX) + (deltaZ * deltaZ)
                < required * required;
    }

    record Offset(int x, int z) {
    }
}
