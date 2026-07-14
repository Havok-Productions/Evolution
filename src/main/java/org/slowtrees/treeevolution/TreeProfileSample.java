package org.slowtrees.treeevolution;

record TreeProfileSample(
        String id,
        String sourceFile,
        String speciesSource,
        String trunkPlacer,
        String foliagePlacer,
        TreeGrowthProfile profile
) {
}
