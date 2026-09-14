package org.evolution.features.treeevolution;

import java.util.List;
import org.evolution.features.treeevolution.constructor.TreeConstructionAudit;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;

/**
 * ## Verifies that production stage diagnostics match replay EXIT/ENTER order.
 */
public final class TreeConstructorStageTimelineSmokeTest {
    private TreeConstructorStageTimelineSmokeTest() {
    }

    public static void main(String[] args) {
        TreeConstructorStageTimeline timeline =
                new TreeConstructorStageTimeline();
        TreeConstructionDecision support = decision(
                TreeConstructionSubrule.SUPPORT_STAGE_TARGET);
        TreeConstructionDecision canopy = decision(
                TreeConstructionSubrule.MINIMUM_CROWN_SHELL);
        TreeConstructionDecision complete = decision(
                TreeConstructionSubrule.STAGE_CONTRACT_COMPLETE);

        requireFrames(
                timeline.enter("tree", support),
                "1:ENTER:TREE_30_SUPPORT_TARGET");
        require(!timeline.enter("tree", support).changed(),
                "same stage created a duplicate boundary");
        requireFrames(
                timeline.enter("tree", canopy),
                "1:EXIT:TREE_30_SUPPORT_TARGET",
                "2:ENTER:TREE_42_MINIMUM_CROWN_SHELL");
        requireFrames(
                timeline.enter("tree", complete),
                "2:EXIT:TREE_42_MINIMUM_CROWN_SHELL",
                "3:ENTER:TREE_99_STAGE_COMPLETE");
        requireFrames(
                timeline.complete("tree", complete),
                "3:EXIT:TREE_99_STAGE_COMPLETE");
        require(!timeline.complete("tree", complete).changed(),
                "completed stage emitted more than one EXIT");
        System.out.println("Tree constructor stage timeline smoke test "
                + "passed: per-tree transitions=true exit-enter=true "
                + "final-exit-once=true");
    }

    private static TreeConstructionDecision decision(
            TreeConstructionSubrule subrule
    ) {
        return new TreeConstructionDecision(
                subrule.phase(), subrule,
                subrule.attachment(),
                TreeConstructionAudit.passed("smoke"),
                "timeline-smoke");
    }

    private static void requireFrames(
            TreeConstructorStageTimeline.Boundary boundary,
            String... expected
    ) {
        List<String> actual = boundary.frames().stream()
                .map(frame -> frame.stage().transition() + ":"
                        + frame.boundary() + ":"
                        + frame.stage().smokeTag())
                .toList();
        require(boundary.changed(), "boundary was not recorded");
        require(actual.equals(List.of(expected)),
                "frame mismatch expected=" + List.of(expected)
                        + " actual=" + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
