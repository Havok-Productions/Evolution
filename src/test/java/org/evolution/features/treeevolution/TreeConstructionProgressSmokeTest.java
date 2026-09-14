package org.evolution.features.treeevolution;

import org.evolution.features.treeevolution.constructor.executor.TreeConstructionResult;

/** Verifies logical progress cannot be confused with a physical block edit. */
public final class TreeConstructionProgressSmokeTest {
    private TreeConstructionProgressSmokeTest() {
    }

    public static void main(String[] args) {
        TreeConstructionResult idle = TreeConstructionResult.idle("idle");
        TreeConstructionResult logical =
                TreeConstructionResult.logicalProgress("ledger");
        TreeConstructionResult changed =
                TreeConstructionResult.changed(1, "block");
        TreeConstructionResult combined = idle.withPriorLogicalProgress(
                true, "preflight");

        require(!idle.progressed() && !idle.worldChanged(),
                "idle result reported progress");
        require(logical.progressed() && !logical.worldChanged()
                        && logical.changedUnits() == 0,
                "logical progress was counted as a world change");
        require(changed.progressed() && changed.worldChanged()
                        && changed.changedUnits() == 1,
                "physical change lost its progress contract");
        require(combined.progressed() && !combined.worldChanged()
                        && combined.detail().contains("preflight"),
                "preflight progress was not preserved");

        System.out.println(
                "Tree construction progress smoke test passed: "
                        + "idle/logical/world accounting is independent.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
