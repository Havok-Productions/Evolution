package org.evolution.features.treeevolution;

import java.util.EnumMap;
import java.util.Map;

/**
 * ## Cross-variant checks that prevent named forms collapsing together.
 */
final class TreeVariantRelativeAudit {
    private TreeVariantRelativeAudit() {
    }

    static Report audit() {
        Map<TreeVariant, Metrics> metrics =
                new EnumMap<>(TreeVariant.class);
        for (TreeVariant variant : TreeVariant.values()) {
            TreeDna dna = TreeShapeSmokeTest.sampleDna(
                    variant, TreeMaturityStage.MEDIUM, 0);
            TreePlan plan = TreeShapeSmokeTest.treeBodyPlan(dna);
            TreeVisualQualityAudit.Report visual =
                    TreeVisualQualityAudit.auditPlan(dna, plan);
            int baseWood = (int) plan.orderedBlocks().stream()
                    .filter(block -> block.y() == dna.baseY())
                    .filter(block ->
                            block.role() == TreeBlockRole.TRUNK)
                    .count();
            metrics.put(variant, new Metrics(
                    visual.treeHeight(),
                    visual.crownWidth(),
                    visual.crownHeight(),
                    plan.branchPlans().size(),
                    baseWood,
                    dna.canopyLayerCount()));
        }

        requireGreater(metrics, TreeVariant.OAK_TALL,
                TreeVariant.OAK_STANDARD, Axis.HEIGHT, 2);
        requireGreater(metrics, TreeVariant.OAK_BROAD,
                TreeVariant.OAK_STANDARD, Axis.WIDTH, 2);
        requireGreater(metrics, TreeVariant.BIRCH_TALL,
                TreeVariant.BIRCH_STANDARD, Axis.HEIGHT, 2);
        requireGreater(metrics, TreeVariant.SPRUCE_PINE,
                TreeVariant.SPRUCE_CLASSIC, Axis.HEIGHT, 3);
        requireAtLeast(metrics, TreeVariant.SPRUCE_MEGA,
                Axis.BASE_WOOD, 4);
        requireAtLeast(metrics, TreeVariant.SPRUCE_MEGA_PINE,
                Axis.BASE_WOOD, 4);
        requireAtMost(metrics, TreeVariant.JUNGLE_BUSH,
                Axis.WIDTH, 9);
        requireGreater(metrics, TreeVariant.JUNGLE_SMALL,
                TreeVariant.JUNGLE_BUSH, Axis.HEIGHT, 2);
        requireGreater(metrics, TreeVariant.JUNGLE_LARGE,
                TreeVariant.JUNGLE_SMALL, Axis.HEIGHT, 4);
        requireAtLeast(metrics, TreeVariant.JUNGLE_MEGA,
                Axis.BASE_WOOD, 4);
        requireGreater(metrics, TreeVariant.DARK_OAK_BROAD,
                TreeVariant.DARK_OAK_STANDARD, Axis.WIDTH, 2);
        requireGreater(metrics, TreeVariant.DARK_OAK_TALL,
                TreeVariant.DARK_OAK_STANDARD, Axis.HEIGHT, 3);
        requireGreater(metrics, TreeVariant.MANGROVE_SPREADING,
                TreeVariant.MANGROVE_SHORT, Axis.WIDTH, 2);
        requireGreater(metrics, TreeVariant.MANGROVE_TALL,
                TreeVariant.MANGROVE_SHORT, Axis.HEIGHT, 2);
        requireGreater(metrics, TreeVariant.CHERRY_BROAD,
                TreeVariant.CHERRY_COMPACT, Axis.WIDTH, 2);
        requireAtLeast(metrics, TreeVariant.CHERRY_LAYERED,
                Axis.LAYERS, 2);
        requireGreater(metrics, TreeVariant.ACACIA_MULTI_FORK,
                TreeVariant.ACACIA_SINGLE_FORK,
                Axis.BRANCHES, 1);
        return new Report(Map.copyOf(metrics));
    }

    private static void requireGreater(
            Map<TreeVariant, Metrics> metrics,
            TreeVariant larger,
            TreeVariant smaller,
            Axis axis,
            int difference
    ) {
        int large = axis.value(metrics.get(larger));
        int small = axis.value(metrics.get(smaller));
        if (large < small + difference) {
            throw new IllegalStateException(
                    larger + " " + axis + "=" + large
                            + " did not exceed " + smaller
                            + "=" + small + " by " + difference);
        }
    }

    private static void requireAtLeast(
            Map<TreeVariant, Metrics> metrics,
            TreeVariant variant,
            Axis axis,
            int minimum
    ) {
        int value = axis.value(metrics.get(variant));
        if (value < minimum) {
            throw new IllegalStateException(
                    variant + " " + axis + "=" + value
                            + " minimum=" + minimum);
        }
    }

    private static void requireAtMost(
            Map<TreeVariant, Metrics> metrics,
            TreeVariant variant,
            Axis axis,
            int maximum
    ) {
        int value = axis.value(metrics.get(variant));
        if (value > maximum) {
            throw new IllegalStateException(
                    variant + " " + axis + "=" + value
                            + " maximum=" + maximum);
        }
    }

    enum Axis {
        HEIGHT {
            int value(Metrics metrics) {
                return metrics.height();
            }
        },
        WIDTH {
            int value(Metrics metrics) {
                return metrics.width();
            }
        },
        BRANCHES {
            int value(Metrics metrics) {
                return metrics.branches();
            }
        },
        BASE_WOOD {
            int value(Metrics metrics) {
                return metrics.baseWood();
            }
        },
        LAYERS {
            int value(Metrics metrics) {
                return metrics.layers();
            }
        };

        abstract int value(Metrics metrics);
    }

    record Metrics(
            int height,
            int width,
            int crownHeight,
            int branches,
            int baseWood,
            int layers
    ) {
    }

    record Report(Map<TreeVariant, Metrics> metrics) {
    }
}
