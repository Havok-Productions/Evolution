package org.slowtrees.treeevolution;

import java.util.Map;

record StructureScanSummary(
        String fileName,
        String format,
        String speciesGuess,
        int blockCount,
        int logBlocks,
        int leafBlocks,
        int vineBlocks,
        int rootLikeBlocks,
        int widthX,
        int height,
        int widthZ,
        int branchCountEstimate,
        double averageBranchLengthEstimate,
        int branchStartHeightEstimate,
        int canopyRadiusEstimate,
        double canopyDensityEstimate,
        double vineFrequency,
        Map<String, Object> generatedProfile,
        Map<String, Integer> materialCounts
) {
}
