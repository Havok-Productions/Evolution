package org.evolution.features.treeevolution;

/**
 * Keeps a discovered live tree from being assigned a shorter constructor stage.
 */
final class TreeSourceStagePolicy {
    private TreeSourceStagePolicy() {
    }

    static boolean shouldAdvance(
            TreeMaturityStage currentStage,
            TreeMaturityStage maximumStage,
            int observedSourceHeight,
            int plannedStageHeight
    ) {
        if (currentStage.ordinal() >= maximumStage.ordinal()) {
            return false;
        }
        int tolerance = currentStage == TreeMaturityStage.SMALL ? 1 : 2;
        // ## One incidental log may exceed the plan. A materially taller rooted
        // trunk means the next stage must absorb the source instead of shrinking it.
        return observedSourceHeight > plannedStageHeight + tolerance;
    }
}