package org.evolution.features.treeevolution;

import java.util.List;
import java.util.Map;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Cell;

/**
 * ## Reconstructs persisted receipts before DNA migration changes their role.
 */
final class TreeCapturedLiveStateAudit {
    private TreeCapturedLiveStateAudit() {
    }

    static Snapshot inspect(TreeDna dna) {
        TreePlan plan = TreeShapeSmokeTest.treeBodyPlan(dna);
        List<PlannedTreeBlock> targets = plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.TRUNK
                        || block.role() == TreeBlockRole.BRANCH
                        || block.role() == TreeBlockRole.CANOPY)
                .filter(block -> block.role() != TreeBlockRole.CANOPY
                        || !dna.originalShapeLogs().contains(
                                dna.worldId() + ":" + block.key()))
                .toList();
        TreeConstructionReplayWorld world =
                new TreeConstructionReplayWorld(
                        TreeSimulatedMinecraftEnvironment.permissive());
        TreeConstructionReplaySeeder.seed(
                dna, plan, targets, world, false,
                TreeCapturedEnvironmentFixture.empty());
        TreeConstructionReplayInspector inspector =
                new TreeConstructionReplayInspector(plan, targets);
        TreeReplayProgress progress = inspector.progress(dna, world);
        TreeVisualQualityAudit.Report visual =
                TreeVisualQualityAudit.auditReplay(
                        dna, plan, world.cells(), false,
                        progress.branch(), progress.canopy());
        TreeVisualQualityAudit.Report report =
                TreeTransitionStallAudit.inspect(dna, progress)
                        .map(visual::withFailure)
                        .orElse(visual);
        report = TreeCapturedShapeDriftAudit.inspect(dna, plan)
                .map(report::withFailure)
                .orElse(report);
        if (dna.species() == TreeSpecies.OAK
                && dna.maturityStage().ordinal()
                        <= TreeMaturityStage.MEDIUM.ordinal()
                && report.crownHeight() > report.treeHeight() + 2) {
            // ## This is a captured-entry diagnosis, not a rule for every
            // intermediate cleanup frame. It identifies the stacked old/new
            // crown column while allowing the replay to visibly prune it.
            report = report.withFailure(
                    "oak-crown-outruns-frame crown-height="
                            + report.crownHeight()
                            + " tree-height=" + report.treeHeight());
        }
        return new Snapshot(
                dna, plan, world.cells(), progress, report);
    }

    record Snapshot(
            TreeDna dna,
            TreePlan plan,
            Map<String, Cell> cells,
            TreeReplayProgress progress,
            TreeVisualQualityAudit.Report report
    ) {
    }
}
