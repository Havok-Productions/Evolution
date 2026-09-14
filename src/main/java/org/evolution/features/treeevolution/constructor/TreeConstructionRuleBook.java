package org.evolution.features.treeevolution.constructor;

import java.util.EnumSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * ## SINGLE SOURCE OF CONSTRUCTOR PRECEDENCE
 *
 * <p>The hierarchy and final audit both read this immutable table. This keeps
 * routing, tracing, and completion checks from developing separate versions
 * of the constructor contract.</p>
 */
public final class TreeConstructionRuleBook {
    private static final List<Rule> RULES = List.of(
            blocking(TreeConstructionRuleId.OWNERSHIP_GATE,
                    TreeConstructionLayer.GATE,
                    TreeConstructionSubrule.ROOTED_TREE_OWNERSHIP,
                    state -> !state.ownershipComplete(),
                    "complete rooted-tree ownership is required",
                    "rooted-tree ownership is incomplete"),
            blocking(TreeConstructionRuleId.SOURCE_SNAPSHOT_GATE,
                    TreeConstructionLayer.GATE,
                    TreeConstructionSubrule.IMMUTABLE_SOURCE_SNAPSHOT,
                    state -> state.transitionPending()
                            && !state.sourceSnapshotReady(),
                    "the immutable source shape must exist before transition work",
                    "transition source snapshot is missing"),
            blocking(TreeConstructionRuleId.OWNERSHIP_ROLE_RECONCILIATION,
                    TreeConstructionLayer.LEDGER_INTEGRITY,
                    TreeConstructionSubrule.OWNERSHIP_ROLE_RECONCILIATION,
                    TreeConstructionState::ownershipRoleReconciliationRemaining,
                    "an evolved receipt disagrees with its live voxel role",
                    "an evolved receipt has the wrong live body role"),
            blocking(TreeConstructionRuleId.TARGET_ROLE_CONFLICT,
                    TreeConstructionLayer.LEDGER_INTEGRITY,
                    TreeConstructionSubrule.CONFLICTING_EVOLVED_TARGET,
                    TreeConstructionState::conflictingEvolvedTargetRemaining,
                    "plugin-owned material conflicts with its target role",
                    "an evolved receipt conflicts with the active target role"),
            blocking(TreeConstructionRuleId.DISCONNECTED_EVOLVED_STRUCTURE,
                    TreeConstructionLayer.STRUCTURE_REPAIR,
                    TreeConstructionSubrule.DISCONNECTED_EVOLVED_STRUCTURE,
                    TreeConstructionState::disconnectedEvolvedStructureRemaining,
                    "plugin-owned structure is disconnected from the rooted tree",
                    "plugin-owned structure is disconnected from the root"),
            blocking(TreeConstructionRuleId.DISCONNECTED_TARGET_REPAIR,
                    TreeConstructionLayer.STRUCTURE_REPAIR,
                    TreeConstructionSubrule.DISCONNECTED_TARGET_REPAIR,
                    TreeConstructionState::disconnectedTargetRepairRemaining,
                    "a planned evolved voxel needs its missing rooted parent path",
                    "a planned evolved voxel lacks its rooted parent path"),
            blocking(TreeConstructionRuleId.INTERRUPTED_DAMAGE_REPAIR,
                    TreeConstructionLayer.STRUCTURE_REPAIR,
                    TreeConstructionSubrule.INTERRUPTED_DAMAGE_REPAIR,
                    TreeConstructionState::damageRepairRequested,
                    "player damage or interrupted construction has priority",
                    "damage repair remains pending"),
            blocking(TreeConstructionRuleId.READY_SOURCE_LEAF_BLOCKER,
                    TreeConstructionLayer.TRANSITION,
                    TreeConstructionSubrule.READY_SOURCE_LEAF_BLOCKER,
                    state -> state.transitionPending()
                            && state.transitionBlockerReady(),
                    "replace one source leaf directly with its ready planned wood",
                    "a ready source-leaf blocker remains"),
            blocking(TreeConstructionRuleId.SUPPORT_STAGE_TARGET,
                    TreeConstructionLayer.SUPPORT,
                    TreeConstructionSubrule.SUPPORT_STAGE_TARGET,
                    state -> state.trunkProgress() < state.trunkTarget(),
                    "planned support structure is below its stage target",
                    "support structure is below target"),
            blocking(TreeConstructionRuleId.COVER_EXPOSED_SUPPORT,
                    TreeConstructionLayer.CANOPY_INTEGRITY,
                    TreeConstructionSubrule.COVER_EXPOSED_SUPPORT,
                    state -> state.exposedUpperLogs() > 0,
                    "upper support wood needs planned crown coverage",
                    "upper support wood remains exposed"),
            blocking(
                    TreeConstructionRuleId
                            .UNPLANNED_BARE_TERMINAL_RETIREMENT,
                    TreeConstructionLayer.CANOPY_INTEGRITY,
                    TreeConstructionSubrule
                            .UNPLANNED_BARE_TERMINAL_RETIREMENT,
                    state -> state.unplannedBareTerminals() > 0,
                    "an unplanned plugin-owned terminal must retire before canopy construction",
                    "an unplanned bare terminal remains"),
            blocking(
                    TreeConstructionRuleId.STALE_ENVELOPE_LEAF_RETIREMENT,
                    TreeConstructionLayer.CANOPY_INTEGRITY,
                    TreeConstructionSubrule.STALE_ENVELOPE_LEAF_RETIREMENT,
                    state -> state.staleEnvelopeLeaves() > 0,
                    "a stale plugin-owned envelope leaf must retire before canopy construction",
                    "a stale branch-envelope leaf remains"),
            blocking(TreeConstructionRuleId.OWNED_BRANCH_ENVELOPE,
                    TreeConstructionLayer.CANOPY_INTEGRITY,
                    TreeConstructionSubrule.OWNED_BRANCH_ENVELOPE,
                    state -> state.uncoveredPlannedBranchEnvelopes() > 0,
                    "a terminal limb needs its owned evolved leaf envelope",
                    "a terminal branch lacks its owned evolved leaf envelope"),
            blocking(TreeConstructionRuleId.MINIMUM_CROWN_SHELL,
                    TreeConstructionLayer.CANOPY_INTEGRITY,
                    TreeConstructionSubrule.MINIMUM_CROWN_SHELL,
                    state -> state.canopyProgress()
                            < state.canopyShellTarget(),
                    "the connected crown shell must exist before branch expansion",
                    "minimum connected crown shell is incomplete"),
            blocking(TreeConstructionRuleId.PARENT_LINKED_BRANCH_FRAME,
                    TreeConstructionLayer.BRANCH_FRAME,
                    TreeConstructionSubrule.PARENT_LINKED_BRANCH_FRAME,
                    state -> state.branchProgress() < state.branchTarget(),
                    "the exact parent-linked branch frame is incomplete",
                    "parent-linked branch frame is incomplete"),
            blocking(TreeConstructionRuleId.READY_OBSOLETE_EVOLVED_STRUCTURE,
                    TreeConstructionLayer.CLEANUP,
                    TreeConstructionSubrule.OBSOLETE_EVOLVED_STRUCTURE,
                    state -> state.broadCleanupReady()
                            && state.obsoleteEvolvedStructureRemaining(),
                    "replacement structure is ready to retire obsolete plugin-owned blocks",
                    "plugin-owned structure outside the target remains"),
            blocking(TreeConstructionRuleId.PACED_SOURCE_CROWN_RETIREMENT,
                    TreeConstructionLayer.CLEANUP,
                    TreeConstructionSubrule.RETIRED_SOURCE_CROWN,
                    state -> state.transitionPending()
                            && state.broadCleanupReady()
                            && state.retiredCrownRemaining(),
                    "a paced source-crown leaf may retire beside canopy growth",
                    "a paced source-crown cleanup action is ready"),
            blocking(TreeConstructionRuleId.CANOPY_STAGE_TARGET,
                    TreeConstructionLayer.CANOPY_FILL,
                    TreeConstructionSubrule.CANOPY_STAGE_TARGET,
                    state -> state.canopyProgress() < state.canopyTarget(),
                    "the replacement canopy is below its stage target",
                    "replacement canopy is below target"),
            blocking(TreeConstructionRuleId.FINAL_OBSOLETE_EVOLVED_STRUCTURE,
                    TreeConstructionLayer.CLEANUP,
                    TreeConstructionSubrule.OBSOLETE_EVOLVED_STRUCTURE,
                    TreeConstructionState::obsoleteEvolvedStructureRemaining,
                    "obsolete plugin-owned structure blocks final completion",
                    "obsolete evolved structure remains before completion"),
            blocking(TreeConstructionRuleId.FINAL_SOURCE_CROWN_RETIREMENT,
                    TreeConstructionLayer.CLEANUP,
                    TreeConstructionSubrule.RETIRED_SOURCE_CROWN,
                    state -> state.transitionPending()
                            && !state.sourceCrownResolved(),
                    "source-crown evidence remains unresolved after structure completion",
                    "the immutable source crown still has unresolved leaves"),
            terminal(TreeConstructionRuleId.TRANSITION_CONTRACT_COMPLETE,
                    TreeConstructionLayer.FINALIZATION,
                    TreeConstructionSubrule.TRANSITION_CONTRACT_COMPLETE,
                    TreeConstructionState::transitionPending,
                    "the shared formation audit passed; transition may close"),
            terminal(TreeConstructionRuleId.POST_STRUCTURE_DETAIL,
                    TreeConstructionLayer.DETAIL,
                    TreeConstructionSubrule.POST_STRUCTURE_DETAIL,
                    TreeConstructionState::detailRequested,
                    "structural audit passed and a detail pass was requested"),
            terminal(TreeConstructionRuleId.STAGE_CONTRACT_COMPLETE,
                    TreeConstructionLayer.COMPLETE,
                    TreeConstructionSubrule.STAGE_CONTRACT_COMPLETE,
                    state -> true,
                    "the shared formation audit passed for the current stage")
    );

    static {
        validate();
    }

    public Match firstMatch(TreeConstructionState state) {
        for (int index = 0; index < RULES.size(); index++) {
            Rule rule = RULES.get(index);
            if (rule.condition().test(state)) {
                return new Match(index, rule.id(), rule.layer(),
                        rule.subrule(), rule.reason(), rule.auditDetail(),
                        rule.blocksCompletion());
            }
        }
        throw new IllegalStateException("Constructor rulebook has no terminal rule");
    }

    public Match firstBlockingMatch(TreeConstructionState state) {
        for (int index = 0; index < RULES.size(); index++) {
            Rule rule = RULES.get(index);
            if (rule.blocksCompletion() && rule.condition().test(state)) {
                return new Match(index, rule.id(), rule.layer(),
                        rule.subrule(), rule.reason(), rule.auditDetail(), true);
            }
        }
        return null;
    }

    public List<RuleView> rules() {
        return java.util.stream.IntStream.range(0, RULES.size())
                .mapToObj(index -> {
                    Rule rule = RULES.get(index);
                    return new RuleView(index, rule.id(), rule.layer(),
                            rule.subrule(), rule.blocksCompletion());
                })
                .toList();
    }

    /**
     * ## Read-only translation data for constructor diagnostics.
     *
     * <p>The debug writer reads the same immutable rows that route live
     * actions. This prevents a hand-maintained legend from drifting away from
     * the actual hierarchy.</p>
     */
    public List<RuleDiagnostic> diagnostics() {
        return java.util.stream.IntStream.range(0, RULES.size())
                .mapToObj(index -> {
                    Rule rule = RULES.get(index);
                    return new RuleDiagnostic(
                            index, rule.id(), rule.layer(), rule.subrule(),
                            rule.reason(), rule.auditDetail(),
                            rule.blocksCompletion());
                })
                .toList();
    }

    public static RuleView primaryRule(
            TreeConstructionSubrule subrule) {
        for (int index = 0; index < RULES.size(); index++) {
            Rule rule = RULES.get(index);
            if (rule.subrule() == subrule) {
                return new RuleView(index, rule.id(), rule.layer(),
                        subrule, rule.blocksCompletion());
            }
        }
        throw new IllegalArgumentException(
                "Constructor subrule has no rulebook path: " + subrule);
    }

    private static Rule blocking(TreeConstructionRuleId id,
            TreeConstructionLayer layer, TreeConstructionSubrule subrule,
            Predicate<TreeConstructionState> condition, String reason,
            String auditDetail) {
        return new Rule(id, layer, subrule, condition, reason,
                auditDetail, true);
    }

    private static Rule terminal(TreeConstructionRuleId id,
            TreeConstructionLayer layer, TreeConstructionSubrule subrule,
            Predicate<TreeConstructionState> condition, String reason) {
        return new Rule(id, layer, subrule, condition, reason,
                "shared structural contract passed", false);
    }

    private static void validate() {
        Set<TreeConstructionRuleId> ids = EnumSet.noneOf(
                TreeConstructionRuleId.class);
        Set<TreeConstructionSubrule> subrules = EnumSet.noneOf(
                TreeConstructionSubrule.class);
        Map<TreeConstructionSubrule, Integer> ownershipCounts =
                new EnumMap<>(TreeConstructionSubrule.class);
        boolean terminalSuffix = false;
        for (Rule rule : RULES) {
            if (!ids.add(rule.id())) {
                throw new IllegalStateException(
                        "Duplicate constructor rule id: " + rule.id());
            }
            subrules.add(rule.subrule());
            ownershipCounts.merge(rule.subrule(), 1, Integer::sum);
            if (!rule.blocksCompletion()) {
                terminalSuffix = true;
            } else if (terminalSuffix) {
                throw new IllegalStateException(
                        "Blocking constructor rule follows terminal routing: "
                                + rule.id());
            }
        }
        if (!ids.equals(EnumSet.allOf(TreeConstructionRuleId.class))) {
            throw new IllegalStateException(
                    "Rulebook does not own every rule id: " + ids);
        }
        if (!subrules.equals(EnumSet.allOf(TreeConstructionSubrule.class))) {
            throw new IllegalStateException(
                    "Rulebook does not own every subrule: " + subrules);
        }
        for (Map.Entry<TreeConstructionSubrule, Integer> entry
                : ownershipCounts.entrySet()) {
            if (entry.getValue() <= 1) {
                continue;
            }
            boolean intentionalCleanupGate = entry.getValue() == 2
                    && (entry.getKey()
                                == TreeConstructionSubrule
                                        .OBSOLETE_EVOLVED_STRUCTURE
                            || entry.getKey()
                                == TreeConstructionSubrule
                                        .RETIRED_SOURCE_CROWN);
            if (!intentionalCleanupGate) {
                throw new IllegalStateException(
                        "Unexpected duplicate constructor subrule ownership: "
                                + entry);
            }
        }
    }

    private record Rule(TreeConstructionRuleId id,
                        TreeConstructionLayer layer,
                        TreeConstructionSubrule subrule,
                        Predicate<TreeConstructionState> condition,
                        String reason,
                        String auditDetail,
                        boolean blocksCompletion) {
    }

    public record Match(int order, TreeConstructionRuleId id,
                        TreeConstructionLayer layer,
                        TreeConstructionSubrule subrule,
                        String reason, String auditDetail,
                        boolean blocksCompletion) {
        public String marker() {
            return "[RULE][" + String.format("%02d", order) + ":"
                    + id + "][" + layer + "]";
        }
    }

    public record RuleView(int order, TreeConstructionRuleId id,
                           TreeConstructionLayer layer,
                           TreeConstructionSubrule subrule,
                           boolean blocksCompletion) {
    }

    public record RuleDiagnostic(
            int order,
            TreeConstructionRuleId id,
            TreeConstructionLayer layer,
            TreeConstructionSubrule subrule,
            String reason,
            String auditDetail,
            boolean blocksCompletion) {
    }
}
