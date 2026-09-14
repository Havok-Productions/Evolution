package org.evolution.features.treeevolution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.file.YamlConfiguration;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Cell;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Ownership;

/**
 * ## Full source-classification and rendered architecture matrix.
 */
public final class TreeVariantArchitectureSmokeTest {
    private static final Path OUT =
            Path.of("target", "tree-variant-smoke");
    private static final TreeShapeEngine SHAPE_ENGINE =
            new TreeShapeEngine();

    private TreeVariantArchitectureSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        assertClassifications();
        assertCodecRoundTrips();
        assertLegacySnapshotMigration();

        List<String> summary = new ArrayList<>();
        summary.add("## Every persistent tree subvariant at every stage.");
        summary.add("variant,species,stage,height,trunkWidth,branches,"
                + "radiusX,radiusY,radiusZ,layers,wood,leaves,"
                + "normal,visualPassed,png,txt");
        Map<TreeSpecies, Set<String>> mediumFingerprints =
                new EnumMap<>(TreeSpecies.class);
        int scenarios = 0;

        for (TreeVariant variant : TreeVariant.values()) {
            for (TreeMaturityStage stage
                    : TreeMaturityStage.values()) {
                TreeDna dna = TreeShapeSmokeTest.sampleDna(
                        variant, stage, 0);
                TreePlan plan = TreeShapeSmokeTest.treeBodyPlan(dna);
                TreeShapeEngine.ShapeReport shape =
                        SHAPE_ENGINE.analyze(plan, dna);
                TreeVisualQualityAudit.Report visual =
                        TreeVisualQualityAudit.auditPlan(dna, plan);
                assertPlacementAugments(dna, plan);
                require(shape.normalEnough(),
                        variant + " " + stage
                                + " failed shape engine: " + shape);
                require(visual.passed(),
                        variant + " " + stage
                                + " failed visual audit: "
                                + visual.failureSummary()
                                + " [" + visual.metrics() + "]");

                String name = variant.id() + "-"
                        + stage.name().toLowerCase();
                Path png = OUT.resolve(name + ".png");
                Path txt = OUT.resolve(name + ".txt");
                TreeShapeSmokeTest.renderPng(
                        plan, dna, shape, png);
                Files.writeString(txt,
                        TreeShapeSmokeTest.renderText(
                                plan, dna, shape));
                summary.add(String.join(",",
                        variant.id(),
                        variant.species().id(),
                        stage.name(),
                        String.valueOf(
                                TreeSpeciesStageStyle.visibleHeight(dna)),
                        String.valueOf(
                                TreeSpeciesStageStyle.trunkWidthAt(
                                        dna, dna.baseY())),
                        String.valueOf(
                                TreeSpeciesStageStyle.branchCount(dna)),
                        String.valueOf(
                                TreeSpeciesStageStyle.canopyRadiusX(dna)),
                        String.valueOf(
                                TreeSpeciesStageStyle.canopyRadiusY(dna)),
                        String.valueOf(
                                TreeSpeciesStageStyle.canopyRadiusZ(dna)),
                        String.valueOf(
                                TreeSpeciesStageStyle
                                        .canopyLayerCount(dna)),
                        String.valueOf(shape.wood()),
                        String.valueOf(shape.leaves()),
                        String.valueOf(shape.normalEnough()),
                        String.valueOf(visual.passed()),
                        png.toString().replace('\\', '/'),
                        txt.toString().replace('\\', '/')));
                if (stage == TreeMaturityStage.MEDIUM) {
                    // ## Medium is the live testing ceiling. Render it from
                    // four proper cube views so hidden trunks, flat cuts and
                    // lopsided crowns cannot pass behind a top-down image.
                    TreeReplayIsometricRenderer.renderAll(
                            OUT.resolve("medium-isometric"),
                            variant.id() + "-medium",
                            variant.id() + " medium target",
                            dna,
                            replayCells(plan),
                            visual);
                    mediumFingerprints
                            .computeIfAbsent(
                                    variant.species(),
                                    ignored -> new HashSet<>())
                            .add(fingerprint(dna, plan));
                }
                scenarios++;
            }
        }
        assertDistinctVariants(mediumFingerprints);
        Files.writeString(
                OUT.resolve("summary.csv"),
                String.join(System.lineSeparator(), summary)
                        + System.lineSeparator());
        System.out.println(
                "Tree variant architecture smoke test passed: variants="
                        + TreeVariant.values().length
                        + " stages="
                        + TreeMaturityStage.values().length
                        + " rendered-scenarios=" + scenarios
                        + " classification=true persistence=true "
                        + "distinct-medium-fingerprints=true "
                        + "coordinate-augment-provenance=true");
    }

    private static void assertPlacementAugments(
            TreeDna dna,
            TreePlan plan
    ) {
        Set<TreePlacementAugment> observed = new HashSet<>();
        for (PlannedTreeBlock block : plan.orderedBlocks()) {
            require(block.augment()
                            != TreePlacementAugment.UNCLASSIFIED,
                    dna.variant() + " " + dna.maturityStage()
                            + " left coordinate " + block.key()
                            + " without planner augment provenance");
            require(block.augment().accepts(block.role()),
                    dna.variant() + " " + block.key() + " augment "
                            + block.augment() + " expected "
                            + block.augment().expectedRoleLabel()
                            + " but planned " + block.role());
            if (block.role() == TreeBlockRole.BRANCH) {
                require(block.hasBranchPath(),
                        dna.variant() + " branch " + block.key()
                                + " has no parent-path provenance");
            }
            observed.add(block.augment());
        }
        require(observed.contains(TreePlacementAugment.TRUNK_FRAME),
                dna.variant() + " has no labeled trunk frame");
        require(observed.stream().anyMatch(augment ->
                        augment.name().startsWith("CANOPY_")),
                dna.variant() + " has no labeled canopy augment");
        if (dna.species() == TreeSpecies.ACACIA) {
            require(observed.contains(
                            TreePlacementAugment
                                    .BRANCH_SIGNATURE_PATH),
                    dna.variant() + " did not label its signature forks");
            require(observed.contains(
                            TreePlacementAugment
                                    .CANOPY_ACACIA_UMBRELLA_PAD),
                    dna.variant() + " did not label its umbrella pads");
            require(observed.contains(
                            TreePlacementAugment
                                    .CANOPY_ACACIA_TIP_THROAT),
                    dna.variant() + " did not label its tip masking leaves");
        }
    }

    private static void assertClassifications() {
        List<Classification> cases = List.of(
                c(TreeVariant.OAK_STANDARD,
                        p(7, 1, 7, 55, 1, 3, 4, 0.35, 1, 0)),
                c(TreeVariant.OAK_FANCY,
                        p(9, 1, 14, 85, 4, 4, 5, 0.35, 1, 1)),
                c(TreeVariant.OAK_TALL,
                        p(11, 1, 9, 65, 1, 3, 4, 0.50, 1, 0)),
                c(TreeVariant.OAK_BROAD,
                        p(7, 1, 8, 90, 2, 5, 4, 0.30, 1, 0)),
                c(TreeVariant.BIRCH_STANDARD,
                        p(7, 1, 7, 45, 1, 2, 5, 0.35, 1, 0)),
                c(TreeVariant.BIRCH_TALL,
                        p(11, 1, 11, 60, 1, 2, 7, 0.40, 1, 0)),
                c(TreeVariant.SPRUCE_CLASSIC,
                        p(10, 1, 11, 75, 2, 3, 9, 0.18, 1, 0)),
                c(TreeVariant.SPRUCE_PINE,
                        p(17, 1, 18, 65, 2, 3, 6, 0.58, 1, 0)),
                c(TreeVariant.SPRUCE_MEGA,
                        p(24, 4, 35, 180, 4, 6, 17, 0.16, 1, 0)),
                c(TreeVariant.SPRUCE_MEGA_PINE,
                        p(30, 4, 38, 155, 4, 6, 8, 0.58, 1, 0)),
                c(TreeVariant.JUNGLE_BUSH,
                        p(4, 1, 4, 38, 1, 2, 3, 0.20, 1, 0)),
                c(TreeVariant.JUNGLE_SMALL,
                        p(8, 1, 8, 70, 2, 3, 5, 0.40, 1, 0)),
                c(TreeVariant.JUNGLE_LARGE,
                        p(14, 1, 18, 120, 4, 5, 6, 0.52, 1, 1)),
                c(TreeVariant.JUNGLE_MEGA,
                        p(26, 4, 42, 230, 5, 7, 8, 0.58, 2, 0)),
                c(TreeVariant.ACACIA_SINGLE_FORK,
                        p(8, 1, 8, 55, 3, 4, 3, 0.60, 1, 1)),
                c(TreeVariant.ACACIA_MULTI_FORK,
                        p(9, 1, 14, 80, 5, 5, 3, 0.52, 1, 1)),
                c(TreeVariant.ACACIA_WINDSWEPT,
                        p(9, 1, 11, 65, 4, 5, 3, 0.55, 1, 3)),
                c(TreeVariant.DARK_OAK_STANDARD,
                        p(9, 4, 18, 120, 3, 4, 4, 0.30, 1, 0)),
                c(TreeVariant.DARK_OAK_BROAD,
                        p(9, 4, 25, 180, 5, 6, 5, 0.25, 1, 0)),
                c(TreeVariant.DARK_OAK_TALL,
                        p(13, 4, 28, 165, 4, 5, 6, 0.38, 1, 0)),
                c(TreeVariant.MANGROVE_SHORT,
                        p(7, 1, 10, 70, 2, 3, 4, 0.30, 1, 1)),
                c(TreeVariant.MANGROVE_TALL,
                        p(12, 1, 16, 95, 3, 4, 6, 0.40, 1, 1)),
                c(TreeVariant.MANGROVE_SPREADING,
                        p(10, 1, 22, 140, 5, 6, 5, 0.32, 1, 2)),
                c(TreeVariant.CHERRY_COMPACT,
                        p(7, 1, 8, 70, 2, 3, 3, 0.36, 1, 0)),
                c(TreeVariant.CHERRY_BROAD,
                        p(8, 1, 12, 120, 3, 5, 4, 0.32, 1, 0)),
                c(TreeVariant.CHERRY_LAYERED,
                        p(10, 1, 16, 150, 4, 5, 6, 0.28, 2, 0)));
        for (Classification test : cases) {
            TreeVariant actual = TreeVariantClassifier.classify(
                    test.expected().species(), test.source());
            require(actual == test.expected(),
                    "classifier expected " + test.expected()
                            + " but got " + actual);
        }
    }

    private static void assertCodecRoundTrips() {
        for (TreeVariant variant : TreeVariant.values()) {
            TreeDna source = TreeShapeSmokeTest.sampleDna(
                    variant, TreeMaturityStage.MEDIUM, 1);
            YamlConfiguration yaml = new YamlConfiguration();
            source.writeTo(yaml);
            TreeDna restored = TreeDna.from(yaml);
            require(restored.variant() == variant,
                    variant + " did not survive DNA persistence");
            require(restored.sourcePattern().equals(
                            source.sourcePattern()),
                    variant + " source measurements changed in persistence");
        }
    }

    private static void assertLegacySnapshotMigration() {
        TreeDna legacy = TreeShapeSmokeTest.sampleDna(
                TreeVariant.SPRUCE_CLASSIC,
                TreeMaturityStage.MEDIUM, 2);
        YamlConfiguration yaml = new YamlConfiguration();
        legacy.writeTo(yaml);
        yaml.set("variant", null);
        yaml.set("source-pattern", null);

        List<String> logs = new ArrayList<>();
        for (int y = legacy.baseY(); y < legacy.baseY() + 8; y++) {
            for (int x = legacy.baseX(); x <= legacy.baseX() + 1; x++) {
                for (int z = legacy.baseZ(); z <= legacy.baseZ() + 1; z++) {
                    logs.add(legacy.worldId() + ":"
                            + x + ":" + y + ":" + z);
                }
            }
        }
        List<String> leaves = new ArrayList<>();
        for (int y = legacy.baseY() + 2;
                y <= legacy.baseY() + 7; y++) {
            int radius = y < legacy.baseY() + 5 ? 3 : 2;
            for (int x = legacy.baseX() - radius;
                    x <= legacy.baseX() + 1 + radius; x++) {
                for (int z = legacy.baseZ() - radius;
                        z <= legacy.baseZ() + 1 + radius; z++) {
                    leaves.add(legacy.worldId() + ":"
                            + x + ":" + y + ":" + z);
                }
            }
        }
        yaml.set("transition.original-shape-logs", logs);
        yaml.set("transition.original-shape-leaves", leaves);

        TreeDna restored = TreeDna.from(yaml);
        require(restored.sourcePattern().measured(),
                "legacy source snapshot was not reconstructed");
        require(restored.sourcePattern().trunkFootprint() == 4,
                "legacy 2x2 source footprint was lost");
        require(restored.variant() == TreeVariant.SPRUCE_MEGA,
                "legacy source snapshot classified as "
                        + restored.variant());
    }

    private static void assertDistinctVariants(
            Map<TreeSpecies, Set<String>> fingerprints
    ) {
        for (TreeSpecies species : TreeSpecies.values()) {
            long variants = java.util.Arrays.stream(
                            TreeVariant.values())
                    .filter(variant -> variant.species() == species)
                    .count();
            int distinct = fingerprints.getOrDefault(
                    species, Set.of()).size();
            require(distinct == variants,
                    species + " collapsed " + variants
                            + " named variants into " + distinct
                            + " MEDIUM architecture fingerprints");
        }
    }

    private static String fingerprint(TreeDna dna, TreePlan plan) {
        long trunks = plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.TRUNK)
                .count();
        long branches = plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.BRANCH)
                .count();
        long leaves = plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.CANOPY)
                .count();
        return TreeSpeciesStageStyle.visibleHeight(dna) + "|"
                + TreeSpeciesStageStyle.trunkWidthAt(
                        dna, dna.baseY()) + "|"
                + TreeSpeciesStageStyle.branchCount(dna) + "|"
                + TreeSpeciesStageStyle.canopyRadiusX(dna) + "x"
                + TreeSpeciesStageStyle.canopyRadiusY(dna) + "x"
                + TreeSpeciesStageStyle.canopyRadiusZ(dna) + "|"
                + TreeSpeciesStageStyle.canopyLayerCount(dna) + "|"
                + Math.round(
                        TreeSpeciesStageStyle.branchStartRatio(dna)
                                * 100.0D) + "|"
                + trunks + "|" + branches + "|" + leaves;
    }

    private static Map<String, Cell> replayCells(TreePlan plan) {
        Map<String, Cell> cells = new java.util.LinkedHashMap<>();
        for (PlannedTreeBlock block : plan.orderedBlocks()) {
            cells.put(block.key(), new Cell(
                    block.material(), block.role(), Ownership.EVOLVED));
        }
        return Map.copyOf(cells);
    }

    private static Classification c(
            TreeVariant expected,
            TreeSourcePattern source
    ) {
        return new Classification(expected, source);
    }

    private static TreeSourcePattern p(
            int height,
            int footprint,
            int logs,
            int leaves,
            int branchSpread,
            int canopyRadius,
            int canopyDepth,
            double canopyStart,
            int tiers,
            int drift
    ) {
        return new TreeSourcePattern(
                height, footprint, logs, leaves, branchSpread,
                canopyRadius, canopyDepth, canopyStart,
                tiers, drift, true);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Classification(
            TreeVariant expected,
            TreeSourcePattern source
    ) {
    }
}
