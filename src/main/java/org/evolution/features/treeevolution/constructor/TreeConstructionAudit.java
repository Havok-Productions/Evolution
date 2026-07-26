package org.evolution.features.treeevolution.constructor;

import java.util.Objects;

/**
 * Independent final-contract result attached to every constructor decision.
 */
public record TreeConstructionAudit(
        boolean passed,
        TreeConstructionSubrule firstFailure,
        String detail
) {
    public TreeConstructionAudit {
        detail = Objects.requireNonNull(detail, "detail");
        if (passed && firstFailure != null) {
            throw new IllegalArgumentException(
                    "A passing construction audit cannot have a failure.");
        }
        if (!passed && firstFailure == null) {
            throw new IllegalArgumentException(
                    "A blocked construction audit needs a subrule.");
        }
    }

    public static TreeConstructionAudit passed(String detail) {
        return new TreeConstructionAudit(true, null, detail);
    }

    public static TreeConstructionAudit blocked(
            TreeConstructionSubrule firstFailure, String detail) {
        return new TreeConstructionAudit(
                false, Objects.requireNonNull(firstFailure), detail);
    }

    public String marker() {
        return passed
                ? "[FINAL-AUDIT][PASS]"
                : "[FINAL-AUDIT][BLOCKED][" + firstFailure + "]";
    }
}