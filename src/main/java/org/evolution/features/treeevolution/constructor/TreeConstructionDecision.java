package org.evolution.features.treeevolution.constructor;

import java.util.Objects;

/**
 * The single constructor decision allowed to own the next tree action.
 */
public record TreeConstructionDecision(
        TreeConstructionPhase phase,
        TreeConstructionSubrule subrule,
        TreeConstructionAttachment attachment,
        TreeConstructionAudit finalAudit,
        String reason
) {
    public TreeConstructionDecision {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(subrule, "subrule");
        Objects.requireNonNull(attachment, "attachment");
        Objects.requireNonNull(finalAudit, "finalAudit");
        Objects.requireNonNull(reason, "reason");
        if (subrule.phase() != phase) {
            throw new IllegalArgumentException(
                    "Subrule " + subrule + " belongs to "
                            + subrule.phase() + ", not " + phase);
        }
        if (subrule.attachment() != attachment) {
            throw new IllegalArgumentException(
                    "Subrule " + subrule + " belongs to "
                            + subrule.attachment() + ", not " + attachment);
        }
    }

    public String marker() {
        return "[CONSTRUCTOR][" + phase + "][" + subrule + "]["
                + attachment + "]";
    }
}