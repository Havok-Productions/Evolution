package org.evolution.features.treeevolution.constructor.executor;

/**
 * Result of one exclusive constructor phase.
 */
public record TreeConstructionResult(
        boolean worldChanged,
        int changedUnits,
        String detail
) {
    public TreeConstructionResult {
        changedUnits = Math.max(0, changedUnits);
        detail = detail == null ? "constructor.no-detail" : detail;
    }

    public static TreeConstructionResult idle(String detail) {
        return new TreeConstructionResult(false, 0, detail);
    }

    public static TreeConstructionResult changed(int units, String detail) {
        return new TreeConstructionResult(true, units, detail);
    }
}
