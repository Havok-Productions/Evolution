package org.slowtrees.treeevolution;

import java.util.List;

record StructureScanResult(
        List<StructureScanSummary> structures,
        List<WorldgenScanSummary> worldgenArchives
) {
    static StructureScanResult empty() {
        return new StructureScanResult(List.of(), List.of());
    }
}
