package org.evolution.features.treeevolution.constructor;

import java.util.Objects;

/**
 * The single constructor decision allowed to own the next tree action.
 */
public record TreeConstructionDecision(
        TreeConstructionRuleId ruleId,
        int ruleOrder,
        TreeConstructionLayer layer,
        TreeConstructionPhase phase,
        TreeConstructionSubrule subrule,
        TreeConstructionAttachment attachment,
        TreeConstructionAudit finalAudit,
        String reason
) {
    public TreeConstructionDecision {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(subrule, "subrule");
        Objects.requireNonNull(attachment, "attachment");
        Objects.requireNonNull(finalAudit, "finalAudit");
        Objects.requireNonNull(reason, "reason");
        if (ruleOrder < 0) {
            throw new IllegalArgumentException("ruleOrder must be non-negative");
        }
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

    public TreeConstructionDecision(
            TreeConstructionPhase phase,
            TreeConstructionSubrule subrule,
            TreeConstructionAttachment attachment,
            TreeConstructionAudit finalAudit,
            String reason
    ) {
        this(TreeConstructionRuleBook.primaryRule(subrule), phase,
                subrule, attachment, finalAudit, reason);
    }

    private TreeConstructionDecision(
            TreeConstructionRuleBook.RuleView route,
            TreeConstructionPhase phase,
            TreeConstructionSubrule subrule,
            TreeConstructionAttachment attachment,
            TreeConstructionAudit finalAudit,
            String reason
    ) {
        this(route.id(), route.order(), route.layer(), phase, subrule,
                attachment, finalAudit, reason);
    }

    public String marker() {
        return "[CONSTRUCTOR][RULE="
                + String.format("%02d", ruleOrder) + ":" + ruleId
                + "][LAYER=" + layer + "][" + phase + "][" + subrule + "]["
                + attachment + "]" + subrule.smokeTag().marker();
    }

    public TreeConstructionSmokeTag smokeTag() {
        return subrule.smokeTag();
    }
}
