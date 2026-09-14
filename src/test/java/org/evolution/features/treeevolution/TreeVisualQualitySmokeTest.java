package org.evolution.features.treeevolution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ## Multi-seed species/stage quality test independent from planner completion.
 */
public final class TreeVisualQualitySmokeTest {
    private static final int VARIANTS = 6;
    private static final Path OUT = Path.of(
            "target", "tree-visual-quality");

    private TreeVisualQualitySmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        List<String> summary = new ArrayList<>();
        summary.add("## Independent visual quality audit. PASS is not derived "
                + "from planner completion.");
        summary.add("scenario,species,stage,passed,failures,metrics");
        List<String> failures = new ArrayList<>();
        int scenarios = 0;

        for (TreeSpecies species : TreeSpecies.values()) {
            for (TreeMaturityStage stage : TreeMaturityStage.values()) {
                for (int variant = 0; variant < VARIANTS; variant++) {
                    TreeDna dna = TreeShapeSmokeTest.sampleDna(
                            species, stage, variant);
                    TreePlan plan = TreeShapeSmokeTest.treeBodyPlan(dna);
                    assertTerrainClearance(dna, plan, variant);
                    TreeVisualQualityAudit.Report report =
                            TreeVisualQualityAudit.auditPlan(dna, plan);
                    String id = species.id() + "-"
                            + stage.name().toLowerCase() + "-v" + variant;
                    summary.add(String.join(",",
                            id,
                            species.id(),
                            stage.name(),
                            String.valueOf(report.passed()),
                            quote(report.failureSummary()),
                            quote(report.metrics())));
                    if (!report.passed()) {
                        failures.add(id + " -> "
                                + report.failureSummary()
                                + " [" + report.metrics() + "]");
                    }
                    scenarios++;
                }
            }
        }

        Files.writeString(
                OUT.resolve("summary.csv"),
                String.join(System.lineSeparator(), summary)
                        + System.lineSeparator());
        // ## Persist all per-shape measurements before progression checks.
        // A monotonic-stage failure must remain diagnosable instead of
        // leaving behind the previous run's summary.
        assertStageProgressions();
        if (!failures.isEmpty()) {
            Files.writeString(
                    OUT.resolve("failures.txt"),
                    String.join(System.lineSeparator(), failures)
                            + System.lineSeparator());
            throw new IllegalStateException(
                    "visual quality failures=" + failures.size()
                            + "/" + scenarios + " first="
                            + failures.get(0));
        }
        Files.deleteIfExists(OUT.resolve("failures.txt"));
        System.out.println("Tree visual quality smoke test passed: scenarios="
                + scenarios + " variants=" + VARIANTS
                + " species=" + TreeSpecies.values().length
                + " stages=" + TreeMaturityStage.values().length);
    }

    private static void assertTerrainClearance(
            TreeDna dna, TreePlan plan, int variant) {
        if (dna.species() != TreeSpecies.SPRUCE) {
            return;
        }
        int lowestCanopyY = plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.CANOPY)
                .mapToInt(PlannedTreeBlock::y)
                .min()
                .orElse(Integer.MAX_VALUE);
        // ## A valid conifer may hang low, but its foliage cannot claim the
        // stump or terrain band. Live grass then blocks a required target and
        // leaves the constructor cycling around an unfinished crown.
        require(lowestCanopyY >= dna.baseY() + 2,
                "spruce " + dna.maturityStage() + " v" + variant
                        + " canopy entered terrain band y=" + lowestCanopyY
                        + " base=" + dna.baseY());
    }

    private static void assertStageProgressions() {
        for (TreeSpecies species : TreeSpecies.values()) {
            for (int variant = 0; variant < VARIANTS; variant++) {
                TreeVisualQualityAudit.Report previous = null;
                for (TreeMaturityStage stage
                        : TreeMaturityStage.values()) {
                    TreeDna dna = TreeShapeSmokeTest.sampleDna(
                            species, stage, variant);
                    TreeVisualQualityAudit.Report current =
                            TreeVisualQualityAudit.auditPlan(
                                    dna,
                                    TreeShapeSmokeTest.treeBodyPlan(dna));
                    if (previous != null) {
                        require(current.treeHeight()
                                        >= previous.treeHeight(),
                                species + " v" + variant + " " + stage
                                        + " regressed tree height "
                                        + previous.treeHeight() + "->"
                                        + current.treeHeight());
                        int widthTolerance = species == TreeSpecies.SPRUCE
                                ? 2 : 1;
                        require(current.crownWidth()
                                        >= previous.crownWidth()
                                                - widthTolerance,
                                species + " v" + variant + " " + stage
                                        + " regressed crown width "
                                        + previous.crownWidth() + "->"
                                        + current.crownWidth());
                        require(current.wood() >= previous.wood(),
                                species + " v" + variant + " " + stage
                                        + " lost structural wood "
                                        + previous.wood() + "->"
                                        + current.wood());
                        require(current.leaves()
                                        >= previous.leaves() * 0.85D,
                                species + " v" + variant + " " + stage
                                        + " lost too much crown mass "
                                        + previous.leaves() + "->"
                                        + current.leaves());
                    }
                    previous = current;
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
