package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.evolution.features.treeevolution.constructor.TreeConstructionSmokeTag;

/**
 * ## Audits what each constructor stage actually promises between XYZ frames.
 *
 * <p>A partially built tree is not a failed final tree. ENTER/EXIT pairs make
 * the fourth dimension explicit and let the smoke suite validate local stage
 * progress while reserving the complete visual contract for finalization.</p>
 */
final class TreeReplayStageContractAudit {
    private TreeReplayStageContractAudit() {
    }

    static List<Report> audit(List<TreeReplayStageFrame> frames) {
        Map<Integer, List<TreeReplayStageFrame>> transitions =
                new LinkedHashMap<>();
        for (TreeReplayStageFrame frame : frames) {
            transitions.computeIfAbsent(
                    frame.transition(), ignored -> new ArrayList<>())
                    .add(frame);
        }
        List<Report> reports = new ArrayList<>();
        transitions.forEach((transition, pair) ->
                reports.add(auditPair(transition, pair)));
        return List.copyOf(reports);
    }

    private static Report auditPair(
            int transition,
            List<TreeReplayStageFrame> pair
    ) {
        TreeReplayStageFrame enter = pair.stream()
                .filter(frame -> frame.boundary()
                        == TreeReplayStageFrame.Boundary.ENTER)
                .findFirst().orElse(null);
        TreeReplayStageFrame exit = pair.stream()
                .filter(frame -> frame.boundary()
                        == TreeReplayStageFrame.Boundary.EXIT)
                .findFirst().orElse(null);
        List<String> failures = new ArrayList<>();
        if (enter == null || exit == null) {
            failures.add("missing-enter-or-exit-frame");
            TreeReplayStageFrame available = enter != null ? enter : exit;
            return new Report(
                    transition,
                    available == null ? null
                            : available.decision().smokeTag(),
                    false, List.copyOf(failures));
        }
        if (enter.decision().smokeTag()
                != exit.decision().smokeTag()) {
            failures.add("smoke-tag-changed-inside-frame");
        }
        if (exit.mutationTime() < enter.mutationTime()) {
            failures.add("mutation-time-reversed");
        }

        TreeConstructionSmokeTag tag = exit.decision().smokeTag();
        TreeReplayProgress before = enter.progress();
        TreeReplayProgress after = exit.progress();
        TreeVisualQualityAudit.Report visual = exit.visual();
        switch (tag) {
            case TREE_30_SUPPORT_TARGET -> requireAdvanced(
                    failures, "trunk", before.trunkPlaced(),
                    after.trunkPlaced(), after.trunkTotal());
            case TREE_40_EXPOSED_SUPPORT_COVER,
                    TREE_42_MINIMUM_CROWN_SHELL -> requireAdvanced(
                            failures, "canopy", before.canopyPlaced(),
                            after.canopyPlaced(), after.canopyTotal());
            case TREE_41_BRANCH_ENVELOPE -> {
                requireAdvanced(
                        failures, "canopy", before.canopyPlaced(),
                        after.canopyPlaced(), after.canopyTotal());
                if (visual != null
                        && visual.uncoveredBranchTips() > 0) {
                    failures.add("branch-envelope-left-uncovered-tips="
                            + visual.uncoveredBranchTips());
                }
            }
            case TREE_50_PARENT_LINKED_BRANCH_FRAME -> requireAdvanced(
                    failures, "branch", before.branchPlaced(),
                    after.branchPlaced(), after.branchTotal());
            case TREE_70_CANOPY_TARGET -> requireAdvanced(
                    failures, "canopy", before.canopyPlaced(),
                    after.canopyPlaced(), after.canopyTotal());
            case TREE_80_TRANSITION_FINAL_AUDIT,
                    TREE_99_STAGE_COMPLETE -> {
                if (before.trunk() < 1.0D
                        || before.branch() < 1.0D
                        || before.canopy() < 1.0D) {
                    failures.add("finalization-entered-before-target-complete");
                }
                if (visual == null || !visual.passed()) {
                    failures.add("final-visual-contract-failed");
                }
            }
            default -> {
                // ## Ownership, repair, conflict, retirement, snapshot, and
                // detail stages are mutation/path contracts. Their world-state
                // safety is checked after every replay action by the harness.
            }
        }
        return new Report(
                transition, tag, failures.isEmpty(), List.copyOf(failures));
    }

    private static void requireAdvanced(
            List<String> failures,
            String role,
            int before,
            int after,
            int total
    ) {
        if (after < before) {
            failures.add(role + "-progress-regressed=" + before + "->" + after);
        } else if (after == before && after < total) {
            failures.add(role + "-stage-made-no-progress=" + after + "/" + total);
        }
    }

    record Report(
            int transition,
            TreeConstructionSmokeTag tag,
            boolean passed,
            List<String> failures
    ) {
        String failureSummary() {
            return failures.isEmpty() ? "none" : String.join("|", failures);
        }
    }
}
