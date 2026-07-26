package org.evolution.features.wind;

final class LeafLitterStackPolicy {
    private LeafLitterStackPolicy() {
    }

    static int nextSegmentAmount(int current, int maximum) {
        if (maximum < 1) {
            throw new IllegalArgumentException("maximum must be positive");
        }
        return Math.min(maximum, Math.max(1, current) + 1);
    }
}
