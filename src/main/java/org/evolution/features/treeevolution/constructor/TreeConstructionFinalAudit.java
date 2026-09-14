package org.evolution.features.treeevolution.constructor;

/**
 * Rechecks completion through the same immutable rulebook used for routing.
 */
public final class TreeConstructionFinalAudit {
    private final TreeConstructionRuleBook ruleBook;

    public TreeConstructionFinalAudit() {
        this(new TreeConstructionRuleBook());
    }

    TreeConstructionFinalAudit(TreeConstructionRuleBook ruleBook) {
        this.ruleBook = ruleBook;
    }

    public TreeConstructionAudit inspect(TreeConstructionState state) {
        TreeConstructionRuleBook.Match blocked =
                ruleBook.firstBlockingMatch(state);
        if (blocked == null) {
            return TreeConstructionAudit.passed(
                    "shared ownership, support, branch, canopy, and transition contracts pass");
        }
        return TreeConstructionAudit.blocked(
                blocked.subrule(), blocked.marker() + " "
                        + blocked.auditDetail());
    }
}
