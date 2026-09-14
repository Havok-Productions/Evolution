package org.evolution.features.treeevolution;

/**
 * ## Immutable measured completion for one virtual tree state.
 */
record TreeReplayProgress(
        int trunkPlaced,
        int trunkTotal,
        int branchPlaced,
        int branchTotal,
        int canopyPlaced,
        int canopyTotal
) {
    double trunk() {
        return ratio(trunkPlaced, trunkTotal);
    }

    double branch() {
        return ratio(branchPlaced, branchTotal);
    }

    double canopy() {
        return ratio(canopyPlaced, canopyTotal);
    }

    String csv() {
        return trunkPlaced + "/" + trunkTotal + ","
                + branchPlaced + "/" + branchTotal + ","
                + canopyPlaced + "/" + canopyTotal;
    }

    private static double ratio(int placed, int total) {
        return total == 0 ? 1.0D : placed / (double) total;
    }
}
