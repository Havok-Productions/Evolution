package org.evolution.features.treeevolution;

public final class TreeSourceStagePolicySmokeTest {
    private TreeSourceStagePolicySmokeTest() {
    }

    public static void main(String[] args) {
        require(TreeSourceStagePolicy.shouldAdvance(
                        TreeMaturityStage.SMALL,
                        TreeMaturityStage.MEDIUM,
                        11, 6),
                "an 11-block source trunk must not remain in a 6-block stage");
        require(!TreeSourceStagePolicy.shouldAdvance(
                        TreeMaturityStage.SMALL,
                        TreeMaturityStage.MEDIUM,
                        7, 6),
                "one incidental upper log should not force a stage promotion");
        require(!TreeSourceStagePolicy.shouldAdvance(
                        TreeMaturityStage.MEDIUM,
                        TreeMaturityStage.MEDIUM,
                        18, 13),
                "the configured maximum stage must remain authoritative");

        System.out.println("Tree source-stage policy smoke test passed: "
                + "tall-source-promoted=true tolerance=1 maximum-respected=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}