package org.evolution.features.treeevolution.constructor.executor;

/**
 * Result of one exclusive constructor phase.
 */
public record TreeConstructionResult(
        boolean progressed,
        boolean worldChanged,
        int changedUnits,
        String detail
) {
    public TreeConstructionResult {
        changedUnits = Math.max(0, changedUnits);
        detail = detail == null ? "constructor.no-detail" : detail;
    }

    public static TreeConstructionResult idle(String detail) {
        return new TreeConstructionResult(false, false, 0, detail);
    }

    public static TreeConstructionResult logicalProgress(String detail) {
        return new TreeConstructionResult(true, false, 0, detail);
    }

    public static TreeConstructionResult changed(int units, String detail) {
        return new TreeConstructionResult(true, true, units, detail);
    }

    public TreeConstructionResult withPriorLogicalProgress(
            boolean priorProgress,
            String priorDetail
    ) {
        if (!priorProgress) {
            return this;
        }
        String prefix = priorDetail == null || priorDetail.isBlank()
                ? "preflight" : priorDetail;
        return new TreeConstructionResult(
                true, worldChanged, changedUnits,
                prefix + " -> " + detail);
    }
}
