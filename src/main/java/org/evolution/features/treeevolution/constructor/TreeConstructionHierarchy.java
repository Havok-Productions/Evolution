package org.evolution.features.treeevolution.constructor;

/**
 * Selects one exclusive mini-constructor through the shared ordered rulebook.
 */
public final class TreeConstructionHierarchy {
    private final TreeConstructionRuleBook ruleBook =
            new TreeConstructionRuleBook();
    private final TreeConstructionFinalAudit finalAudit =
            new TreeConstructionFinalAudit(ruleBook);

    public TreeConstructionDecision decide(TreeConstructionState state) {
        TreeConstructionAudit audit = finalAudit.inspect(state);
        TreeConstructionRuleBook.Match match = ruleBook.firstMatch(state);
        return new TreeConstructionDecision(
                match.id(), match.order(), match.layer(),
                match.subrule().phase(), match.subrule(),
                match.subrule().attachment(), audit, match.reason());
    }

    public java.util.List<TreeConstructionRuleBook.RuleView> rules() {
        return ruleBook.rules();
    }
}
