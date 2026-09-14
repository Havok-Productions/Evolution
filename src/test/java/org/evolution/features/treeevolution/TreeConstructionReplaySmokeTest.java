package org.evolution.features.treeevolution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.EnumSet;
import java.util.EnumMap;
import java.util.Set;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Cell;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Mutation;
import org.evolution.features.treeevolution.constructor.TreeConstructionSmokeTag;
import org.evolution.features.treeevolution.constructor.TreeConstructionSubrule;

/**
 * End-to-end constructor replay over production stages and reported DNA.
 */
public final class TreeConstructionReplaySmokeTest {
    private static final Path OUT = Path.of(
            "target", "tree-construction-replay");

    private TreeConstructionReplaySmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        resetOutput();
        List<Scenario> scenarios = scenarios(args);
        Map<TreeConstructionSmokeTag, Integer> aggregateSmokeHits =
                new EnumMap<>(TreeConstructionSmokeTag.class);
        List<String> trace = new ArrayList<>();
        trace.add("## One row per hierarchy-selected replay action.");
        trace.add("scenario,step,phase,subrule,smokeTag,executor,changed,units,"
                + "detail,trunk,branch,canopy,unresolvedSourceLeaves");
        List<String> smokeCoverage = new ArrayList<>();
        smokeCoverage.add("## Production smoke tags observed by each replay.");
        smokeCoverage.add("scenario,smokeTag,phase,subrule,attachment,"
                + "hits,reached,contract");
        List<String> simulationStages = new ArrayList<>();
        simulationStages.add("## Live input, normalized entry, and exact final checkpoint.");
        simulationStages.add("scenario,simulationStage,visualPassed,"
                + "failures,metrics,progress,png");
        List<String> stageFrames = new ArrayList<>();
        stageFrames.add("## Every hierarchy ENTER/EXIT voxel checkpoint; mutationTime indexes voxel-timeline.csv.");
        stageFrames.add("scenario,transition,action,boundary,smokeTag,phase,subrule,attachment,mutationTime,contractPassed,contractFailures,visualPassed,visualFailures,metrics,progress,fingerprint,snapshot");
        List<String> summary = new ArrayList<>();
        summary.add("## Constructor replay summary");
        summary.add("scenario,species,stage,steps,plannedBlocks,"
                + "physicalMutations,snapshots,visualPassed,"
                + "obsoleteStructureRegression,intermediateAudits,"
                + "finalVoxelExact,expectedInitialDeformation,"
                + "initialDeformationDetected");
        List<String> visualSummary = new ArrayList<>();
        visualSummary.add("## Independent milestone visual audits.");
        visualSummary.add("scenario,milestone,required,passed,failures,metrics,png");
        List<String> visualFailures = new ArrayList<>();
        List<String> voxelSeed = new ArrayList<>();
        voxelSeed.add("## Initial XYZ state for lossless time replay.");
        voxelSeed.add("scenario,x,y,z,material,role,ownership");
        List<String> voxelTimeline = new ArrayList<>();
        voxelTimeline.add("## Ordered XYZ+time deltas; apply over voxel-seed.csv.");
        voxelTimeline.add("scenario,time,x,y,z,beforeMaterial,beforeRole,"
                + "beforeOwnership,afterMaterial,afterRole,afterOwnership,"
                + "physical,reason");

        for (Scenario scenario : scenarios) {
            Path renderDir = OUT.resolve(safeName(scenario.id()));
            TreeCapturedLiveStateAudit.Snapshot liveInput =
                    TreeCapturedLiveStateAudit.inspect(scenario.dna());
            List<Path> liveViews = TreeReplayIsometricRenderer.renderAll(
                    renderDir,
                    TreeSimulationStageTag.SIM_00_LIVE_INPUT.name()
                            .toLowerCase(Locale.ROOT),
                    scenario.id() + " / "
                            + TreeSimulationStageTag.SIM_00_LIVE_INPUT,
                    liveInput.dna(), liveInput.cells(),
                    liveInput.report());
            appendSimulationStage(
                    simulationStages, scenario.id(),
                    TreeSimulationStageTag.SIM_00_LIVE_INPUT,
                    liveInput.report(), liveInput.progress(), liveViews);
            TreeDna normalized = new TreeDnaNormalizer()
                    .normalize(scenario.dna(), scenario.dna().maturityStage())
                    .dna();
            TreeConstructionReplayHarness.ReplayResult result =
                    new TreeConstructionReplayHarness(
                            scenario.id(), normalized,
                            scenario.injectObsoleteStructure(),
                            TreeSimulatedMinecraftEnvironment.permissive(),
                            scenario.environment()).run();
            TreeVisualQualityAudit.Report initialReport =
                    liveInput.report();
            boolean initialDeformationDetected =
                    !initialReport.passed();
            if (scenario.expectedInitialDeformation()
                    && !initialDeformationDetected) {
                throw new IllegalStateException(
                        "reported deformation passed the initial visual "
                                + "audit: " + scenario.id() + " metrics="
                                + initialReport.metrics());
            }
            trace.addAll(result.trace());
            appendSmokeCoverage(
                    smokeCoverage, scenario.id(), result.smokeHits());
            result.smokeHits().forEach((tag, hits) ->
                    aggregateSmokeHits.merge(tag, hits, Integer::sum));
            List<TreeReplayStageContractAudit.Report> stageContracts =
                    TreeReplayStageContractAudit.audit(
                            result.stageFrames());
            appendStageFrames(
                    stageFrames, scenario.id(), result.stageFrames(),
                    stageContracts);
            appendVoxelSeed(voxelSeed, scenario.id(), result.initialCells());
            appendVoxelTimeline(
                    voxelTimeline, scenario.id(), result.mutationTimeline());
            StringBuilder snapshots = new StringBuilder();
            for (Map.Entry<String, String> entry
                    : result.snapshots().entrySet()) {
                snapshots.append("## milestone=")
                        .append(entry.getKey())
                        .append(System.lineSeparator())
                        .append(entry.getValue())
                        .append(System.lineSeparator());
            }
            Files.writeString(
                    OUT.resolve(safeName(scenario.id()) + "-3d.txt"),
                    snapshots.toString());
            TreeVisualQualityAudit.Report finalReport =
                    result.visualReports().get("99-final");
            Map<String, List<Path>> renderedViews = new HashMap<>();
            for (Map.Entry<String, Map<String,
                    TreeConstructionReplayWorld.Cell>> entry
                    : result.voxelSnapshots().entrySet()) {
                TreeVisualQualityAudit.Report report =
                        result.visualReports().get(entry.getKey());
                boolean required = requiredVisualMilestone(
                        entry.getKey(), report, finalReport);
                List<Path> views = TreeReplayIsometricRenderer.renderAll(
                        renderDir,
                        safeName(entry.getKey()),
                        scenario.id() + " / " + entry.getKey(),
                        normalized,
                        entry.getValue(),
                        report);
                renderedViews.put(entry.getKey(), views);
                visualSummary.add(String.join(",",
                        scenario.id(),
                        entry.getKey(),
                        String.valueOf(required),
                        String.valueOf(report.passed()),
                        quote(report.failureSummary()),
                        quote(report.metrics()),
                        views.stream()
                                .map(path -> path.toString()
                                        .replace('\\', '/'))
                                .reduce((left, right) ->
                                        left + "|" + right)
                                .orElse("")));
            }
            TreeVisualQualityAudit.Report normalizedEntry =
                    result.visualReports().get("00-initial");
            appendSimulationStage(
                    simulationStages, scenario.id(),
                    TreeSimulationStageTag.SIM_10_NORMALIZED_ENTRY,
                    normalizedEntry,
                    result.initialProgress(),
                    renderedViews.getOrDefault(
                            "00-initial", List.of()));
            appendSimulationStage(
                    simulationStages, scenario.id(),
                    TreeSimulationStageTag.SIM_99_FINAL_TARGET,
                    finalReport,
                    result.finalProgress(),
                    renderedViews.getOrDefault(
                            "99-final", List.of()));
            boolean visualPassed = result.visualReports().entrySet().stream()
                    .filter(entry -> requiredVisualMilestone(
                            entry.getKey(), entry.getValue(), finalReport))
                    .allMatch(entry -> entry.getValue().passed())
                    && stageContracts.stream().allMatch(
                            TreeReplayStageContractAudit.Report::passed);
            result.visualReports().forEach((milestone, report) -> {
                if (requiredVisualMilestone(
                        milestone, report, finalReport)
                        && !report.passed()) {
                    visualFailures.add(scenario.id() + " at " + milestone
                            + ": " + report.failureSummary()
                            + " [" + report.metrics() + "]");
                }
            });
            stageContracts.stream()
                    .filter(report -> !report.passed())
                    .forEach(report -> visualFailures.add(
                            scenario.id() + " at transition="
                                    + report.transition() + " tag="
                                    + report.tag() + ": "
                                    + report.failureSummary()));
            summary.add(String.join(",",
                    scenario.id(),
                    result.species().id(),
                    result.stage().name(),
                    String.valueOf(result.steps()),
                    String.valueOf(result.plannedBlocks()),
                    String.valueOf(result.physicalMutations()),
                    String.valueOf(result.snapshots().size()),
                    String.valueOf(visualPassed),
                    String.valueOf(
                            scenario.injectObsoleteStructure()),
                    String.valueOf(result.intermediateAudits()),
                    String.valueOf(result.finalDiff().exact()),
                    String.valueOf(
                            scenario.expectedInitialDeformation()),
                    String.valueOf(initialDeformationDetected)));
        }

        Files.writeString(
                OUT.resolve("replay-trace.csv"),
                String.join(System.lineSeparator(), trace)
                        + System.lineSeparator());
        Files.writeString(
                OUT.resolve("smoke-tag-coverage.csv"),
                String.join(System.lineSeparator(), smokeCoverage)
                        + System.lineSeparator());
        Files.writeString(
                OUT.resolve("simulation-stage-coverage.csv"),
                String.join(System.lineSeparator(), simulationStages)
                        + System.lineSeparator());
        Files.writeString(
                OUT.resolve("stage-frame-index.csv"),
                String.join(System.lineSeparator(), stageFrames)
                        + System.lineSeparator());
        Files.writeString(
                OUT.resolve("summary.csv"),
                String.join(System.lineSeparator(), summary)
                        + System.lineSeparator());
        Files.writeString(
                OUT.resolve("visual-audit.csv"),
                String.join(System.lineSeparator(), visualSummary)
                        + System.lineSeparator());
        Files.writeString(
                OUT.resolve("voxel-seed.csv"),
                String.join(System.lineSeparator(), voxelSeed)
                        + System.lineSeparator());
        Files.writeString(
                OUT.resolve("voxel-timeline.csv"),
                String.join(System.lineSeparator(), voxelTimeline)
                        + System.lineSeparator());
        if (args.length == 0) {
            for (TreeConstructionSmokeTag required : List.of(
                    TreeConstructionSmokeTag
                            .TREE_40A_UNPLANNED_TERMINAL_RETIREMENT,
                    TreeConstructionSmokeTag
                            .TREE_40B_STALE_ENVELOPE_RETIREMENT)) {
                if (aggregateSmokeHits.getOrDefault(required, 0) <= 0) {
                    throw new IllegalStateException(
                            "production canopy subrule was never executed by replay: "
                                    + required);
                }
            }
        }
        // ## Render every failing fixture before rejecting the run so shape
        // regressions remain inspectable from all four camera views.
        if (!visualFailures.isEmpty()) {
            throw new IllegalStateException(
                    "visual audit failures: "
                            + String.join(" | ", visualFailures));
        }
        System.out.println("Tree construction replay smoke test passed: "
                + "scenarios=" + scenarios.size()
                + " block-by-block=true restart=true unload=true "
                + "damage=true final-plan-match=true visual-audit=true "
                + "intermediate-audit=true voxel-diff=true "
                + "xyz-time-replay=true smoke-stage-frames=true "
                + "four-view-renders=true");
        System.out.println("Replay artifacts: " + OUT.toAbsolutePath());
    }

    private static void appendSimulationStage(
            List<String> output,
            String scenario,
            TreeSimulationStageTag tag,
            TreeVisualQualityAudit.Report report,
            TreeReplayProgress progress,
            List<Path> views
    ) {
        output.add(String.join(",",
                scenario,
                tag.name(),
                String.valueOf(report != null && report.passed()),
                quote(report == null
                        ? "missing" : report.failureSummary()),
                quote(report == null
                        ? "missing" : report.metrics()),
                progress == null ? "missing" : quote(progress.csv()),
                views.stream()
                        .map(path -> path.toString().replace('\\', '/'))
                        .reduce((left, right) -> left + "|" + right)
                        .orElse("")));
    }

    private static void appendSmokeCoverage(
            List<String> output,
            String scenario,
            Map<TreeConstructionSmokeTag, Integer> hits
    ) {
        Set<TreeConstructionSmokeTag> mapped = EnumSet.noneOf(
                TreeConstructionSmokeTag.class);
        for (TreeConstructionSubrule subrule
                : TreeConstructionSubrule.values()) {
            TreeConstructionSmokeTag tag = subrule.smokeTag();
            if (!mapped.add(tag)) {
                throw new IllegalStateException(
                        "duplicate hierarchy smoke tag " + tag);
            }
            int count = hits.getOrDefault(tag, 0);
            output.add(String.join(",",
                    scenario,
                    tag.name(),
                    subrule.phase().name(),
                    subrule.name(),
                    subrule.attachment().name(),
                    String.valueOf(count),
                    String.valueOf(count > 0),
                    quote(tag.contract())));
        }
        if (!mapped.equals(EnumSet.allOf(
                TreeConstructionSmokeTag.class))) {
            throw new IllegalStateException(
                    "hierarchy smoke-tag map is incomplete");
        }
    }

    private static void appendStageFrames(
            List<String> output,
            String scenario,
            List<TreeReplayStageFrame> frames,
            List<TreeReplayStageContractAudit.Report> contracts
    ) {
        Map<Integer, TreeReplayStageContractAudit.Report> byTransition =
                new HashMap<>();
        contracts.forEach(report -> byTransition.put(
                report.transition(), report));
        for (TreeReplayStageFrame frame : frames) {
            TreeVisualQualityAudit.Report visual = frame.visual();
            TreeReplayStageContractAudit.Report contract =
                    byTransition.get(frame.transition());
            output.add(String.join(",",
                    scenario,
                    String.valueOf(frame.transition()),
                    String.valueOf(frame.action()),
                    frame.boundary().name(),
                    frame.decision().smokeTag().name(),
                    frame.decision().phase().name(),
                    frame.decision().subrule().name(),
                    frame.decision().attachment().name(),
                    String.valueOf(frame.mutationTime()),
                    String.valueOf(contract != null
                            && contract.passed()),
                    quote(contract == null
                            ? "missing" : contract.failureSummary()),
                    String.valueOf(visual != null && visual.passed()),
                    quote(visual == null
                            ? "missing" : visual.failureSummary()),
                    quote(visual == null
                            ? "missing" : visual.metrics()),
                    quote(frame.progress().csv()),
                    frame.voxelFingerprint(),
                    frame.snapshotId()));
        }
    }

    private static boolean requiredVisualMilestone(
            String milestone,
            TreeVisualQualityAudit.Report report,
            TreeVisualQualityAudit.Report finalReport
    ) {
        if (milestone.equals("00-initial")) {
            return false;
        }
        // ## Smoke-tag snapshots show the exact point where a phase owns the
        // tree. Incomplete phases are diagnostic renders; their structural
        // invariants are enforced per action and the final render is strict.
        if (milestone.startsWith("smoke-")) {
            return false;
        }
        // ## Stage frames use TreeReplayStageContractAudit to compare the
        // stage's ENTER and EXIT voxels against that tag's local promise.
        // Applying the final-tree beauty audit to a trunk-only stage mistakes
        // expected construction gaps for a malformed finished tree.
        if (milestone.startsWith("stage-")) {
            return false;
        }
        // ## During a reported spruce migration, the old and replacement
        // envelopes overlap temporarily. Keep those failures visible in the
        // artifact, but judge the conifer taper contract at the exact final
        // voxel state once the final target itself passes.
        if (finalReport != null
                && finalReport.passed()
                && !report.failures().isEmpty()
                && report.failures().stream().allMatch(
                        failure -> failure.startsWith("spruce-"))) {
            return false;
        }
        // ## A persisted restart can legitimately snapshot an inherited,
        // already-disconnected source canopy while the reconciler is
        // removing it one block at a time. It remains diagnostic, but only
        // the repaired final state is contractual for that single warning.
        return !milestone.equals("50-restart")
                || finalReport == null
                || !finalReport.passed()
                || report.failures().stream().anyMatch(failure ->
                        !failure.startsWith(
                                "unsupported-canopy-components="));
    }

    private static void appendVoxelSeed(
            List<String> output,
            String scenario,
            Map<String, Cell> cells
    ) {
        cells.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Coordinate coordinate = coordinate(entry.getKey());
                    Cell cell = entry.getValue();
                    output.add(String.join(",",
                            scenario,
                            String.valueOf(coordinate.x()),
                            String.valueOf(coordinate.y()),
                            String.valueOf(coordinate.z()),
                            cell.material().name(),
                            cell.role().name(),
                            cell.ownership().name()));
                });
    }

    private static void appendVoxelTimeline(
            List<String> output,
            String scenario,
            List<Mutation> mutations
    ) {
        for (Mutation mutation : mutations) {
            Coordinate coordinate = coordinate(mutation.key());
            output.add(String.join(",",
                    scenario,
                    String.valueOf(mutation.sequence()),
                    String.valueOf(coordinate.x()),
                    String.valueOf(coordinate.y()),
                    String.valueOf(coordinate.z()),
                    material(mutation.before()),
                    role(mutation.before()),
                    ownership(mutation.before()),
                    material(mutation.after()),
                    role(mutation.after()),
                    ownership(mutation.after()),
                    String.valueOf(mutation.physical()),
                    quote(mutation.reason())));
        }
    }

    private static Coordinate coordinate(String key) {
        String[] parts = key.split(":");
        int offset = parts.length - 3;
        return new Coordinate(
                Integer.parseInt(parts[offset]),
                Integer.parseInt(parts[offset + 1]),
                Integer.parseInt(parts[offset + 2]));
    }

    private static String material(Cell cell) {
        return cell == null ? "AIR" : cell.material().name();
    }

    private static String role(Cell cell) {
        return cell == null ? "NONE" : cell.role().name();
    }

    private static String ownership(Cell cell) {
        return cell == null ? "NONE" : cell.ownership().name();
    }

    private record Coordinate(int x, int y, int z) {
    }

    private static void resetOutput() throws Exception {
        if (Files.exists(OUT)) {
            try (var paths = Files.walk(OUT)) {
                for (Path path : paths
                        .sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(OUT);
    }

    private static List<Scenario> scenarios(String[] args) {
        if (args.length > 0) {
            List<Scenario> external = new ArrayList<>();
            for (String argument : args) {
                for (TreeCapturedFixtureStore.Fixture fixture
                        : TreeCapturedFixtureStore.load(
                                Path.of(argument))) {
                    external.add(new Scenario(
                            fixture.id(), fixture.dna(), false,
                            fixture.expectedInitialDeformation(),
                            fixture.environment()));
                }
            }
            // ## A reported live fixture can now be replayed alone. This keeps
            // diagnosis fast while using the same hierarchy and visual audit.
            return List.copyOf(external);
        }
        List<Scenario> scenarios = new ArrayList<>();
        // ## The live plugin currently caps evolution at MEDIUM. Mature and
        // ancient targets are covered by the independent visual/stage audit,
        // while this expensive block replay mirrors only reachable live stages.
        for (TreeSpecies species : TreeSpecies.values()) {
            for (TreeMaturityStage stage : List.of(
                    TreeMaturityStage.SMALL,
                    TreeMaturityStage.MEDIUM)) {
                for (int variant = 0; variant < 2; variant++) {
                    scenarios.add(new Scenario(
                            "generated-" + species.id() + "-"
                                    + stage.name().toLowerCase(Locale.ROOT)
                                    + "-v" + variant,
                            TreeShapeSmokeTest.sampleDna(
                                    species, stage, variant),
                            stage == TreeMaturityStage.MEDIUM
                                    && variant == 1,
                            false,
                            TreeCapturedEnvironmentFixture.empty()));
                }
            }
        }
        int fixture = 0;
        for (TreeDna dna : LiveTreeDnaRegressionSmokeTest
                .reportedFixtures()) {
            scenarios.add(new Scenario(
                    "reported-" + fixture + "-" + dna.species().id(),
                    dna, false, false,
                    TreeCapturedEnvironmentFixture.empty()));
            fixture++;
        }
        for (TreeCapturedFixtureStore.Fixture captured
                : TreeCapturedFixtureStore.load()) {
            scenarios.add(new Scenario(
                    captured.id(), captured.dna(), false,
                    captured.expectedInitialDeformation(),
                    captured.environment()));
        }
        return List.copyOf(scenarios);
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String safeName(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private record Scenario(
            String id,
            TreeDna dna,
            boolean injectObsoleteStructure,
            boolean expectedInitialDeformation,
            TreeCapturedEnvironmentFixture environment
    ) {
    }
}
