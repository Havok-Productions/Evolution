package org.evolution.features.treeevolution.constructor.executor;

import java.util.EnumMap;
import java.util.Map;
import org.evolution.features.treeevolution.constructor.TreeConstructionAudit;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;

public final class TreeConstructionExecutorRegistrySmokeTest {
    private TreeConstructionExecutorRegistrySmokeTest() {
    }

    public static void main(String[] args) {
        TreeConstructionExecutorRegistry registry =
                new TreeConstructionExecutorRegistry();
        Map<TreeConstructionSubrule, String> expected = expectedOperations();

        for (TreeConstructionSubrule subrule
                : TreeConstructionSubrule.values()) {
            RecordingOperations operations = new RecordingOperations();
            TreeConstructionDecision decision = new TreeConstructionDecision(
                    subrule.phase(), subrule, subrule.attachment(),
                    TreeConstructionAudit.passed("executor smoke"), "smoke");
            TreeConstructionResult result = registry.execute(decision, operations);
            String expectedOperation = expected.get(subrule);
            if (!result.detail().equals(expectedOperation)) {
                throw new IllegalStateException(
                        subrule + " expected operation " + expectedOperation
                                + " but dispatched " + result.detail());
            }
            if (operations.calls != 1) {
                throw new IllegalStateException(
                        subrule + " dispatched " + operations.calls
                                + " operations instead of exactly one");
            }
            String executorName = registry.executorName(subrule);
            if (executorName == null || executorName.isBlank()) {
                throw new IllegalStateException(
                        subrule + " has no labeled mini-constructor");
            }
        }

        if (expected.size() != TreeConstructionSubrule.values().length) {
            throw new IllegalStateException(
                    "Expected-operation table is incomplete: " + expected);
        }
        System.out.println(
                "Tree constructor executor registry smoke test passed: "
                        + expected.size()
                        + " subrules each dispatch exactly one operation.");
    }

    private static Map<TreeConstructionSubrule, String> expectedOperations() {
        EnumMap<TreeConstructionSubrule, String> expected =
                new EnumMap<>(TreeConstructionSubrule.class);
        expected.put(TreeConstructionSubrule.ROOTED_TREE_OWNERSHIP,
                "ownership");
        expected.put(TreeConstructionSubrule.IMMUTABLE_SOURCE_SNAPSHOT,
                "snapshot");
        expected.put(TreeConstructionSubrule.OWNERSHIP_ROLE_RECONCILIATION,
                "role-reconcile");
        expected.put(TreeConstructionSubrule.DISCONNECTED_TARGET_REPAIR,
                "target-repair");
        expected.put(TreeConstructionSubrule.INTERRUPTED_DAMAGE_REPAIR,
                "damage-repair");
        expected.put(TreeConstructionSubrule.READY_SOURCE_LEAF_BLOCKER,
                "replace-blocker");
        expected.put(TreeConstructionSubrule.DISCONNECTED_EVOLVED_STRUCTURE,
                "retire-disconnected");
        expected.put(TreeConstructionSubrule.CONFLICTING_EVOLVED_TARGET,
                "retire-conflict");
        expected.put(TreeConstructionSubrule.OBSOLETE_EVOLVED_STRUCTURE,
                "retire-obsolete");
        expected.put(TreeConstructionSubrule.RETIRED_SOURCE_CROWN,
                "retire-source");
        expected.put(TreeConstructionSubrule.SUPPORT_STAGE_TARGET,
                "support");
        expected.put(TreeConstructionSubrule.COVER_EXPOSED_SUPPORT,
                "cover-support");
        expected.put(
                TreeConstructionSubrule
                        .UNPLANNED_BARE_TERMINAL_RETIREMENT,
                "retire-unplanned-terminal");
        expected.put(TreeConstructionSubrule.STALE_ENVELOPE_LEAF_RETIREMENT,
                "retire-stale-envelope");
        expected.put(TreeConstructionSubrule.OWNED_BRANCH_ENVELOPE,
                "branch-envelope");
        expected.put(TreeConstructionSubrule.MINIMUM_CROWN_SHELL,
                "minimum-shell");
        expected.put(TreeConstructionSubrule.PARENT_LINKED_BRANCH_FRAME,
                "branch-frame");
        expected.put(TreeConstructionSubrule.CANOPY_STAGE_TARGET,
                "canopy-fill");
        expected.put(TreeConstructionSubrule.TRANSITION_CONTRACT_COMPLETE,
                "finalize-transition");
        expected.put(TreeConstructionSubrule.POST_STRUCTURE_DETAIL,
                "details");
        expected.put(TreeConstructionSubrule.STAGE_CONTRACT_COMPLETE,
                "complete");
        return Map.copyOf(expected);
    }

    private static final class RecordingOperations
            implements TreeConstructionOperations {
        private int calls;

        public TreeConstructionResult waitForOwnership() { return call("ownership"); }
        public TreeConstructionResult waitForSourceSnapshot() { return call("snapshot"); }
        public TreeConstructionResult reconcileOwnershipRole() { return call("role-reconcile"); }
        public TreeConstructionResult repairDisconnectedTarget() { return call("target-repair"); }
        public TreeConstructionResult repairInterruptedDamage() { return call("damage-repair"); }
        public TreeConstructionResult replaceTransitionBlocker() { return call("replace-blocker"); }
        public TreeConstructionResult buildSupport() { return call("support"); }
        public TreeConstructionResult coverExposedSupport() { return call("cover-support"); }
        public TreeConstructionResult retireUnplannedBareTerminal() { return call("retire-unplanned-terminal"); }
        public TreeConstructionResult retireStaleEnvelopeLeaf() { return call("retire-stale-envelope"); }
        public TreeConstructionResult repairBranchEnvelope() { return call("branch-envelope"); }
        public TreeConstructionResult buildMinimumCrownShell() { return call("minimum-shell"); }
        public TreeConstructionResult buildBranchFrame() { return call("branch-frame"); }
        public TreeConstructionResult fillCanopy() { return call("canopy-fill"); }
        public TreeConstructionResult retireDisconnectedEvolvedStructure() { return call("retire-disconnected"); }
        public TreeConstructionResult retireConflictingEvolvedTarget() { return call("retire-conflict"); }
        public TreeConstructionResult retireObsoleteEvolvedStructure() { return call("retire-obsolete"); }
        public TreeConstructionResult retireSourceCrown() { return call("retire-source"); }
        public TreeConstructionResult finalizeTransition() { return call("finalize-transition"); }
        public TreeConstructionResult buildDetails() { return call("details"); }
        public TreeConstructionResult complete() { return call("complete"); }

        private TreeConstructionResult call(String detail) {
            calls++;
            return TreeConstructionResult.idle(detail);
        }
    }
}
