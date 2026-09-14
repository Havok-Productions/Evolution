package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Cell;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.TreeConstructionSmokeTag;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionResult;

/**
 * ## Records replay milestones, voxel states, visual audits, and decisions.
 */
final class TreeConstructionReplayRecorder {
    private final String scenario;
    private final TreePlan plan;
    private final TreeConstructionReplayInspector inspector;
    private final List<String> trace = new ArrayList<>();
    private final Map<String, String> snapshots = new LinkedHashMap<>();
    private final Map<String, Map<String, Cell>> voxelSnapshots =
            new LinkedHashMap<>();
    private final Map<String, TreeVisualQualityAudit.Report> visualReports =
            new LinkedHashMap<>();
    private final Map<TreeConstructionSmokeTag, Integer> smokeHits =
            new EnumMap<>(TreeConstructionSmokeTag.class);
    private final List<TreeReplayStageFrame> stageFrames =
            new ArrayList<>();
    private TreeConstructionDecision activeStage;
    private int stageTransition;

    TreeConstructionReplayRecorder(
            String scenario,
            TreePlan plan,
            TreeConstructionReplayInspector inspector
    ) {
        this.scenario = scenario;
        this.plan = plan;
        this.inspector = inspector;
    }

    void captureMilestones(
            TreeDna dna,
            TreeConstructionReplayWorld world,
            TreeReplayProgress progress,
            int uncoveredBranchTips
    ) {
        if (progress.canopy() >= canopyShellTarget(dna)) {
            captureIfAbsent(
                    "20-minimum-shell",
                    "minimum canopy shell reached",
                    false, dna, world);
        }
        if (progress.branch() >= 1.0D && uncoveredBranchTips == 0) {
            captureIfAbsent(
                    "40-branches-complete",
                    "parent-linked branch frame complete",
                    false, dna, world);
        }
        if (progress.canopy()
                >= TreeCanopyTransitionPolicy.minimumReplacementCanopy(dna)) {
            captureIfAbsent(
                    "60-cleanup-ready",
                    "replacement crown permits paced cleanup",
                    false, dna, world);
        }
    }

    void capture(
            String id,
            String title,
            boolean finalState,
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        TreeReplayProgress progress = inspector.progress(dna, world);
        TreeVisualQualityAudit.Report report =
                TreeVisualQualityAudit.auditReplay(
                        dna, plan, world.cells(), finalState,
                        progress.branch(), progress.canopy());
        snapshots.put(id, TreeReplayVolumeRenderer.render(
                title, dna, plan, world, progress.csv()));
        voxelSnapshots.put(id, world.cells());
        visualReports.put(id, report);
    }

    void traceAction(
            int step,
            TreeConstructionDecision decision,
            String executor,
            TreeConstructionResult result,
            TreeReplayProgress progress,
            int unresolvedSourceLeaves
    ) {
        smokeHits.merge(decision.smokeTag(), 1, Integer::sum);
        trace.add(String.join(",",
                scenario,
                String.valueOf(step),
                decision.phase().name(),
                decision.subrule().name(),
                decision.smokeTag().name(),
                executor,
                String.valueOf(result.worldChanged()),
                String.valueOf(result.changedUnits()),
                quote(result.detail()),
                progress.csv(),
                String.valueOf(unresolvedSourceLeaves)));
    }

    void beforeAction(
            int action,
            TreeConstructionDecision decision,
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        if (activeStage != null
                && activeStage.smokeTag() == decision.smokeTag()
                && activeStage.subrule() == decision.subrule()) {
            return;
        }
        if (activeStage != null) {
            captureStageBoundary(
                    action, TreeReplayStageFrame.Boundary.EXIT,
                    activeStage, dna, world);
        }
        activeStage = decision;
        stageTransition++;
        captureStageBoundary(
                action, TreeReplayStageFrame.Boundary.ENTER,
                decision, dna, world);
    }

    void finishActiveStage(
            int action,
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        if (activeStage == null) {
            return;
        }
        captureStageBoundary(
                action, TreeReplayStageFrame.Boundary.EXIT,
                activeStage, dna, world);
        activeStage = null;
    }

    void captureSmokeStageIfAbsent(
            TreeConstructionDecision decision,
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        TreeConstructionSmokeTag tag = decision.smokeTag();
        captureIfAbsent(
                "smoke-" + tag.name().toLowerCase(),
                tag.marker() + " " + tag.contract(),
                false, dna, world);
    }

    void traceEvent(int step, String event, String detail, String progress) {
        trace.add(String.join(",",
                scenario, String.valueOf(step), event, detail, progress));
    }

    List<String> trace() {
        return List.copyOf(trace);
    }

    Map<String, String> snapshots() {
        return Map.copyOf(snapshots);
    }

    Map<String, Map<String, Cell>> voxelSnapshots() {
        return Map.copyOf(voxelSnapshots);
    }

    Map<String, TreeVisualQualityAudit.Report> visualReports() {
        return Map.copyOf(visualReports);
    }

    Map<TreeConstructionSmokeTag, Integer> smokeHits() {
        return Map.copyOf(smokeHits);
    }

    List<TreeReplayStageFrame> stageFrames() {
        return List.copyOf(stageFrames);
    }

    private void captureStageBoundary(
            int action,
            TreeReplayStageFrame.Boundary boundary,
            TreeConstructionDecision decision,
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        String id = "stage-%04d-%s-%s".formatted(
                stageTransition,
                decision.smokeTag().name().toLowerCase(),
                boundary.name().toLowerCase());
        capture(
                id,
                decision.smokeTag().marker() + " "
                        + boundary + " "
                        + decision.smokeTag().contract(),
                false, dna, world);
        stageFrames.add(new TreeReplayStageFrame(
                stageTransition,
                action,
                boundary,
                id,
                decision,
                world.mutationTimeline().size(),
                inspector.progress(dna, world),
                visualReports.get(id),
                Integer.toUnsignedString(
                        world.fingerprint().hashCode(), 16)));
    }

    private void captureIfAbsent(
            String id,
            String title,
            boolean finalState,
            TreeDna dna,
            TreeConstructionReplayWorld world
    ) {
        if (!snapshots.containsKey(id)) {
            capture(id, title, finalState, dna, world);
        }
    }

    private static double canopyShellTarget(TreeDna dna) {
        return switch (dna.maturityStage()) {
            case SMALL -> 0.18D;
            case MEDIUM -> 0.24D;
            case MATURE -> 0.30D;
            case ANCIENT -> 0.32D;
        };
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

}
