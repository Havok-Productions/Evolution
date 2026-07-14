package org.slowtrees.treeevolution;

import java.util.Map;

record WorldgenProfileSuggestion(
        String sourceFile,
        String species,
        String featureType,
        String trunkPlacer,
        String foliagePlacer,
        int targetHeightMin,
        int targetHeightMax,
        int branchesMin,
        int branchesMax,
        int branchLengthMin,
        int branchLengthMax,
        int canopyRadius,
        double canopyDensity,
        double rootChance,
        double vineChance,
        double groundDetailChance,
        Map<String, Integer> decorators,
        Map<String, Object> generatedProfile
) {
}
