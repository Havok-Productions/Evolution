package org.slowtrees.treeevolution;

import java.util.Map;
import java.util.List;

record WorldgenScanSummary(
        String archiveName,
        int jsonFiles,
        int nbtFiles,
        int biomeFiles,
        int configuredFeatureFiles,
        int placedFeatureFiles,
        int treeFeatureFiles,
        int vegetationFeatureFiles,
        int terrainFeatureFiles,
        Map<String, Integer> speciesSignals,
        Map<String, Integer> materialSignals,
        List<WorldgenProfileSuggestion> profileSuggestions
) {
}
