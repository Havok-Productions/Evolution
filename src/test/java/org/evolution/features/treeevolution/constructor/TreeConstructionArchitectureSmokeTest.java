package org.evolution.features.treeevolution.constructor;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Proves that constructor routing is a table, not recursive phase feedback.
 */
public final class TreeConstructionArchitectureSmokeTest {
    private TreeConstructionArchitectureSmokeTest() {
    }

    public static void main(String[] args) {
        TreeConstructionHierarchy hierarchy = new TreeConstructionHierarchy();
        Set<Integer> orders = new HashSet<>();
        Set<TreeConstructionRuleId> ids =
                EnumSet.noneOf(TreeConstructionRuleId.class);
        Map<TreeConstructionSubrule, Integer> ownership =
                new EnumMap<>(TreeConstructionSubrule.class);
        boolean terminalSuffix = false;

        for (TreeConstructionRuleBook.RuleView rule : hierarchy.rules()) {
            require(orders.add(rule.order()),
                    "duplicate constructor precedence " + rule.order());
            require(ids.add(rule.id()),
                    "duplicate constructor path id " + rule.id());
            ownership.merge(rule.subrule(), 1, Integer::sum);
            if (!rule.blocksCompletion()) {
                terminalSuffix = true;
            } else {
                require(!terminalSuffix,
                        "blocking rule appears after terminal routing: "
                                + rule.id());
            }
            require(rule.subrule().phase() != null
                            && rule.subrule().attachment() != null
                            && rule.subrule().smokeTag() != null,
                    "rule has an incomplete ownership path: " + rule.id());
        }

        require(ids.equals(EnumSet.allOf(TreeConstructionRuleId.class)),
                "not every path id belongs to the rulebook: " + ids);
        require(ownership.keySet().equals(
                        EnumSet.allOf(TreeConstructionSubrule.class)),
                "not every subrule belongs to the rulebook: " + ownership);
        for (Map.Entry<TreeConstructionSubrule, Integer> entry
                : ownership.entrySet()) {
            if (entry.getValue() > 1) {
                require(entry.getKey()
                                == TreeConstructionSubrule
                                        .OBSOLETE_EVOLVED_STRUCTURE
                                || entry.getKey()
                                        == TreeConstructionSubrule
                                                .RETIRED_SOURCE_CROWN,
                        "only paced/final cleanup may have two ordered gates: "
                                + entry);
            }
        }

        System.out.println(
                "Tree constructor architecture smoke test passed: "
                        + ids.size() + " ordered paths, "
                        + ownership.size()
                        + " subrules, one shared rulebook, terminal suffix intact.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
