package org.evolution.features.treeevolution.constructor.executor;

import java.util.EnumMap;
import java.util.Map;
import org.evolution.features.treeevolution.constructor.TreeConstructionAudit;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionPhase;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;

public final class TreeConstructionExecutorRegistrySmokeTest {
    private TreeConstructionExecutorRegistrySmokeTest() {
    }

    public static void main(String[] args) {
        TreeConstructionExecutorRegistry registry =
                new TreeConstructionExecutorRegistry();
        Map<TreeConstructionPhase, String> expected = expectedOperations();
        RecordingOperations operations = new RecordingOperations();

        for (TreeConstructionPhase phase : TreeConstructionPhase.values()) {
            TreeConstructionSubrule subrule =
                    TreeConstructionSubrule.primaryFor(phase);
            TreeConstructionDecision decision = new TreeConstructionDecision(
                    phase, subrule, subrule.attachment(),
                    TreeConstructionAudit.passed("executor smoke"), "smoke");
            TreeConstructionResult result =
                    registry.execute(decision, operations);
            String expectedOperation = expected.get(phase);
            if (!result.detail().equals(expectedOperation)) {
                throw new IllegalStateException(
                        phase + " expected operation " + expectedOperation
                                + " but dispatched " + result.detail());
            }
            String executorName = registry.executorName(phase);
            if (executorName == null || executorName.isBlank()) {
                throw new IllegalStateException(
                        phase + " has no labeled executor");
            }
        }

        System.out.println(
                "Tree constructor executor registry smoke test passed: "
                        + expected.size() + " phases have one owner.");
    }

    private static Map<TreeConstructionPhase, String> expectedOperations() {
        EnumMap<TreeConstructionPhase, String> expected =
                new EnumMap<>(TreeConstructionPhase.class);
        expected.put(TreeConstructionPhase.WAIT_FOR_OWNERSHIP, "ownership");
        expected.put(TreeConstructionPhase.WAIT_FOR_SOURCE_SNAPSHOT, "snapshot");
        expected.put(TreeConstructionPhase.REPAIR, "repair");
        expected.put(
                TreeConstructionPhase.REPLACE_TRANSITION_BLOCKER,
                "replace-blocker");
        expected.put(TreeConstructionPhase.BUILD_SUPPORT, "support");
        expected.put(TreeConstructionPhase.BUILD_CANOPY_SHELL, "canopy-shell");
        expected.put(TreeConstructionPhase.BUILD_BRANCH_FRAME, "branch-frame");
        expected.put(TreeConstructionPhase.FILL_CANOPY, "canopy-fill");
        expected.put(
                TreeConstructionPhase.PRUNE_RETIRED_CROWN,
                "prune-retired");
        expected.put(
                TreeConstructionPhase.FINALIZE_TRANSITION,
                "finalize-transition");
        expected.put(TreeConstructionPhase.BUILD_DETAILS, "details");
        expected.put(TreeConstructionPhase.COMPLETE, "complete");
        return Map.copyOf(expected);
    }

    private static final class RecordingOperations
            implements TreeConstructionOperations {
        @Override
        public TreeConstructionResult waitForOwnership() {
            return idle("ownership");
        }

        @Override
        public TreeConstructionResult waitForSourceSnapshot() {
            return idle("snapshot");
        }

        @Override
        public TreeConstructionResult repair() {
            return idle("repair");
        }

        @Override
        public TreeConstructionResult replaceTransitionBlocker() {
            return idle("replace-blocker");
        }

        @Override
        public TreeConstructionResult buildSupport() {
            return idle("support");
        }

        @Override
        public TreeConstructionResult buildCanopyShell() {
            return idle("canopy-shell");
        }

        @Override
        public TreeConstructionResult buildBranchFrame() {
            return idle("branch-frame");
        }

        @Override
        public TreeConstructionResult fillCanopy() {
            return idle("canopy-fill");
        }

        @Override
        public TreeConstructionResult pruneRetiredCrown() {
            return idle("prune-retired");
        }

        @Override
        public TreeConstructionResult finalizeTransition() {
            return idle("finalize-transition");
        }

        @Override
        public TreeConstructionResult buildDetails() {
            return idle("details");
        }

        @Override
        public TreeConstructionResult complete() {
            return idle("complete");
        }

        private TreeConstructionResult idle(String detail) {
            return TreeConstructionResult.idle(detail);
        }
    }
}
