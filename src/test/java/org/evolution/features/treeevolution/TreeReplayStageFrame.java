package org.evolution.features.treeevolution;

import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;

/**
 * ## One hierarchy boundary in the XYZ plus time constructor replay.
 *
 * <p>ENTER and EXIT frames bracket every uninterrupted smoke-tag run. The
 * lossless mutation timeline reconstructs every block change between them.</p>
 */
record TreeReplayStageFrame(
        int transition,
        int action,
        Boundary boundary,
        String snapshotId,
        TreeConstructionDecision decision,
        int mutationTime,
        TreeReplayProgress progress,
        TreeVisualQualityAudit.Report visual,
        String voxelFingerprint
) {
    enum Boundary {
        ENTER,
        EXIT
    }
}
