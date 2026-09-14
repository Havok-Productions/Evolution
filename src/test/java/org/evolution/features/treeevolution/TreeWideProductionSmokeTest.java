package org.evolution.features.treeevolution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import org.evolution.features.treeevolution.TreeReplayNeighborhoodSnapshot.Environment;

/**
 * ## Wide production-aware tree smoke matrix.
 *
 * <p>Combines full padded neighborhoods, live configuration, Folia-style
 * scheduling, every-action replay invariants, subtype contracts, and
 * coordinate-level live/target diffs.</p>
 */
public final class TreeWideProductionSmokeTest {
    private static final Path OUT = Path.of(
            "target", "tree-wide-production-smoke");
    private static final int SEEDS = 3;

    private TreeWideProductionSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Options options = Options.parse(args);
        resetOutput();
        TreeProductionSmokeSettings settings =
                TreeProductionSmokeSettings.load(options.config());

        List<String> matrix = new ArrayList<>();
        matrix.add("## Wide species/subtype/environment matrix.");
        matrix.add("variant,species,stage,seed,environment,"
                + "visualPassed,subtypePassed,target,matched,missing,"
                + "wrongMaterial,unreformedAtTarget,extraEvolved,"
                + "protectedNeighbor,volumeCells,airCells");
        List<String> subtypeSummary = new ArrayList<>();
        subtypeSummary.add("## Human-authored subtype contracts.");
        subtypeSummary.add("variant,passed,failures,metrics");
        List<String> neighborhoodSummary = new ArrayList<>();
        neighborhoodSummary.add("## Complete padded XYZ fixture counts.");
        neighborhoodSummary.add("variant,stage,seed,environment,radius,"
                + "y,total,air,source,evolved,neighbor,plannedMissing");

        int scenarios = 0;
        int subtypePasses = 0;
        int environmentIndex = 0;
        for (TreeVariant variant : TreeVariant.values()) {
            for (TreeMaturityStage stage : reachableStages(settings)) {
                for (int seed = 0; seed < SEEDS; seed++) {
                    TreeDna dna = TreeShapeSmokeTest.sampleDna(
                            variant, stage, seed);
                    TreePlan plan =
                            TreeShapeSmokeTest.treeBodyPlan(dna);
                    TreeVisualQualityAudit.Report visual =
                            TreeVisualQualityAudit.auditPlan(dna, plan);
                    require(visual.passed(),
                            variant + " " + stage + " seed=" + seed
                                    + " visual failure "
                                    + visual.failureSummary());

                    TreeSubtypeExpectationAudit.Report subtype = null;
                    if (stage == TreeMaturityStage.MEDIUM) {
                        subtype = TreeSubtypeExpectationAudit
                                .auditMedium(dna, plan);
                        require(subtype.passed(),
                                variant + " subtype contract failed: "
                                        + subtype.failures()
                                        + " [" + subtype.metrics() + "]");
                        subtypePasses++;
                        if (seed == 0) {
                            subtypeSummary.add(String.join(",",
                                    variant.id(),
                                    String.valueOf(subtype.passed()),
                                    quote(String.join(
                                            "|", subtype.failures())),
                                    quote(subtype.metrics())));
                        }
                    }

                    Environment environment =
                            Environment.values()[
                                    Math.floorMod(
                                            environmentIndex++,
                                            Environment.values().length)];
                    TreeConstructionReplayWorld world =
                            TreeReplayNeighborhoodSnapshot.seed(
                                    dna, plan, environment);
                    TreeReplayNeighborhoodSnapshot.Snapshot snapshot =
                            TreeReplayNeighborhoodSnapshot.capture(
                                    dna, plan, world, environment);
                    TreeLiveVoxelDiff.Report diff =
                            TreeLiveVoxelDiff.compare(
                                    dna, plan, world.cells());
                    require(diff.target() > 0,
                            variant + " produced an empty diff target");
                    require(snapshot.total() > snapshot.air(),
                            variant + " neighborhood contains only air");
                    matrix.add(String.join(",",
                            variant.id(),
                            variant.species().id(),
                            stage.name(),
                            String.valueOf(seed),
                            environment.name(),
                            String.valueOf(visual.passed()),
                            String.valueOf(subtype == null
                                    || subtype.passed()),
                            String.valueOf(diff.target()),
                            String.valueOf(diff.matched()),
                            String.valueOf(diff.missing()),
                            String.valueOf(diff.wrongMaterial()),
                            String.valueOf(diff.foreignAtTarget()),
                            String.valueOf(diff.extraEvolved()),
                            String.valueOf(diff.protectedNeighbor()),
                            String.valueOf(snapshot.total()),
                            String.valueOf(snapshot.air())));
                    neighborhoodSummary.add(String.join(",",
                            variant.id(),
                            stage.name(),
                            String.valueOf(seed),
                            snapshot.csv()));
                    if (stage == TreeMaturityStage.MEDIUM
                            && seed == 0
                            && firstVariantForSpecies(variant)) {
                        Files.writeString(
                                OUT.resolve(
                                        variant.species().id()
                                                + "-live-region-xyz.txt"),
                                snapshot.volume());
                    }
                    scenarios++;
                }
            }
        }

        List<String> liveSummary = auditLiveFixtures(
                options, settings);
        TreeVariantRelativeAudit.Report relative =
                TreeVariantRelativeAudit.audit();
        TreeFoliaScheduleWideSmoke.Report schedule =
                TreeFoliaScheduleWideSmoke.run(settings);
        Files.writeString(
                OUT.resolve("folia-schedule.csv"),
                String.join(System.lineSeparator(), schedule.trace())
                        + System.lineSeparator());
        Files.writeString(
                OUT.resolve("matrix.csv"),
                String.join(System.lineSeparator(), matrix)
                        + System.lineSeparator());
        Files.writeString(
                OUT.resolve("subtype-contracts.csv"),
                String.join(System.lineSeparator(), subtypeSummary)
                        + System.lineSeparator());
        Files.writeString(
                OUT.resolve("neighborhoods.csv"),
                String.join(System.lineSeparator(), neighborhoodSummary)
                        + System.lineSeparator());
        Files.writeString(
                OUT.resolve("live-fixtures.csv"),
                String.join(System.lineSeparator(), liveSummary)
                        + System.lineSeparator());
        Files.writeString(
                OUT.resolve("runtime-settings.txt"),
                "## Values loaded from the supplied runtime config."
                        + System.lineSeparator()
                        + settings.summary()
                        + System.lineSeparator());

        int replayScenarios = 0;
        int replayAudits = 0;
        if (options.replay()) {
            for (TreeVariant variant : TreeVariant.values()) {
                TreeDna dna = TreeShapeSmokeTest.sampleDna(
                        variant,
                        settings.maximumStage().ordinal()
                                >= TreeMaturityStage.MEDIUM.ordinal()
                                ? TreeMaturityStage.MEDIUM
                                : TreeMaturityStage.SMALL,
                        2);
                TreeConstructionReplayHarness.ReplayResult result;
                try {
                    result = new TreeConstructionReplayHarness(
                            "wide-" + variant.id(),
                            dna, false).run();
                } catch (RuntimeException failure) {
                    throw new IllegalStateException(
                            "wide constructor replay failed variant="
                                    + variant.id(),
                            failure);
                }
                require(result.finalDiff().exact(),
                        variant + " final live diff was not exact");
                require(result.intermediateAudits()
                                == result.steps(),
                        variant + " did not audit every changed step");
                replayScenarios++;
                replayAudits += result.intermediateAudits();
                // ## Deep replay is intentionally expensive. Emit one compact
                // marker per completed subtype so a slow or stalled variant
                // can be identified without weakening its action-by-action
                // audits.
                System.out.println("wide replay completed: variant="
                        + variant.id() + " steps=" + result.steps()
                        + " audits=" + result.intermediateAudits());
            }
        }

        String result = "Tree wide production smoke passed: scenarios="
                + scenarios
                + " species=" + TreeSpecies.values().length
                + " variants=" + TreeVariant.values().length
                + " relativeVariants="
                + relative.metrics().size()
                + " subtypePasses=" + subtypePasses
                + " environments=" + Environment.values().length
                + " liveFixtures=" + Math.max(0,
                        liveSummary.size() - 2)
                + " scheduleCycles=" + schedule.cycles()
                + " regionHandoffs=" + schedule.regionHandoffs()
                + " unloadPauses=" + schedule.unloadPauses()
                + " replayScenarios=" + replayScenarios
                + " intermediateAudits=" + replayAudits;
        Files.writeString(
                OUT.resolve("result.txt"),
                "## " + result + System.lineSeparator());
        System.out.println(result);
        System.out.println("Wide artifacts: " + OUT.toAbsolutePath());
    }

    private static List<String> auditLiveFixtures(
            Options options,
            TreeProductionSmokeSettings settings
    ) {
        List<String> summary = new ArrayList<>();
        summary.add("## Anonymized persisted live DNA against current targets.");
        summary.add("id,species,variant,stage,target,matched,missing,"
                + "wrongMaterial,unreformedAtTarget,extraEvolved,"
                + "protectedNeighbor,targetVisualPassed");
        List<TreeLiveDnaFixtureLoader.Fixture> live =
                options.dna() == null
                        ? List.of()
                        : TreeLiveDnaFixtureLoader.load(
                                options.dna(), 32);
        for (TreeLiveDnaFixtureLoader.Fixture fixture : live) {
            TreeDna dna = new TreeDnaNormalizer()
                    .normalize(
                            fixture.dna(),
                            settings.maximumStage())
                    .dna();
            TreePlan plan = TreeShapeSmokeTest.treeBodyPlan(dna);
            TreeConstructionReplayWorld world =
                    TreeReplayNeighborhoodSnapshot.seed(
                            dna, plan, Environment.OPEN);
            TreeLiveVoxelDiff.Report diff =
                    TreeLiveVoxelDiff.compare(
                            dna, plan, world.cells());
            TreeVisualQualityAudit.Report visual =
                    TreeVisualQualityAudit.auditPlan(dna, plan);
            require(visual.passed(),
                    fixture.id() + " target visual failure "
                            + visual.failureSummary());
            summary.add(String.join(",",
                    fixture.id(),
                    dna.species().id(),
                    dna.variant().id(),
                    dna.maturityStage().name(),
                    String.valueOf(diff.target()),
                    String.valueOf(diff.matched()),
                    String.valueOf(diff.missing()),
                    String.valueOf(diff.wrongMaterial()),
                    String.valueOf(diff.foreignAtTarget()),
                    String.valueOf(diff.extraEvolved()),
                    String.valueOf(diff.protectedNeighbor()),
                    String.valueOf(visual.passed())));
        }
        return List.copyOf(summary);
    }

    private static List<TreeMaturityStage> reachableStages(
            TreeProductionSmokeSettings settings) {
        return EnumSet.allOf(TreeMaturityStage.class).stream()
                .filter(stage -> stage.ordinal()
                        <= settings.maximumStage().ordinal())
                .toList();
    }

    private static boolean firstVariantForSpecies(
            TreeVariant variant) {
        return java.util.Arrays.stream(TreeVariant.values())
                .filter(candidate ->
                        candidate.species() == variant.species())
                .findFirst()
                .orElseThrow() == variant;
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

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Options(
            Path config,
            Path dna,
            boolean replay
    ) {
        static Options parse(String[] args) {
            Path config = Path.of(
                    "src", "main", "resources", "config.yml");
            Path dna = null;
            boolean replay = false;
            for (String argument : args) {
                if (argument.startsWith("--config=")) {
                    config = Path.of(argument.substring(
                            "--config=".length()));
                } else if (argument.startsWith("--dna=")) {
                    dna = Path.of(argument.substring(
                            "--dna=".length()));
                } else if (argument.equals("--replay")) {
                    replay = true;
                }
            }
            require(Files.isRegularFile(config),
                    "config file not found: " + config);
            if (dna != null) {
                require(Files.isRegularFile(dna),
                        "DNA file not found: " + dna);
            }
            return new Options(config, dna, replay);
        }
    }
}
