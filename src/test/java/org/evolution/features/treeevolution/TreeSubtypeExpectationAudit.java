package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.List;

/**
 * ## Human-authored subtype silhouette contract independent of planner rules.
 */
final class TreeSubtypeExpectationAudit {
    private TreeSubtypeExpectationAudit() {
    }

    static Report auditMedium(TreeDna dna, TreePlan plan) {
        require(dna.maturityStage() == TreeMaturityStage.MEDIUM,
                "subtype contract expects MEDIUM DNA");
        TreeVisualQualityAudit.Report visual =
                TreeVisualQualityAudit.auditPlan(dna, plan);
        int branches = plan.branchPlans().size();
        int baseWood = (int) plan.orderedBlocks().stream()
                .filter(block -> block.y() == dna.baseY())
                .filter(block -> block.role() == TreeBlockRole.TRUNK)
                .count();
        int leafLevels = (int) plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.CANOPY)
                .map(PlannedTreeBlock::y)
                .distinct()
                .count();
        List<String> failures = new ArrayList<>();
        switch (dna.variant()) {
            case OAK_STANDARD -> {
                atLeast(failures, "branches", branches, 2);
                between(failures, "height", visual.treeHeight(), 7, 13);
            }
            case OAK_FANCY -> {
                atLeast(failures, "branches", branches, 4);
                atLeast(failures, "leaf-levels", leafLevels, 4);
            }
            case OAK_TALL -> {
                atLeast(failures, "height", visual.treeHeight(), 10);
                atMost(failures, "crown-width", visual.crownWidth(), 11);
            }
            case OAK_BROAD -> {
                atLeast(failures, "crown-width", visual.crownWidth(), 11);
                atLeast(failures, "branches", branches, 3);
            }
            case BIRCH_STANDARD -> {
                atMost(failures, "branches", branches, 2);
                atMost(failures, "crown-width", visual.crownWidth(), 7);
            }
            case BIRCH_TALL -> {
                atLeast(failures, "height", visual.treeHeight(), 11);
                atMost(failures, "crown-width", visual.crownWidth(), 7);
            }
            case SPRUCE_CLASSIC -> {
                atLeast(failures, "leaf-levels", leafLevels, 7);
                atLeast(failures, "branches", branches, 4);
            }
            case SPRUCE_PINE -> {
                atLeast(failures, "height", visual.treeHeight(), 13);
                atLeast(failures, "leaf-levels", leafLevels, 6);
            }
            case SPRUCE_MEGA -> {
                atLeast(failures, "base-wood", baseWood, 4);
                // ## The canopy pass may reject a requested conifer limb when
                // its tip falls outside the tapered leaf envelope. Audit the
                // final safe silhouette, not the pre-pruning branch budget.
                atLeast(failures, "branches", branches, 5);
                atLeast(failures, "crown-width", visual.crownWidth(), 9);
            }
            case SPRUCE_MEGA_PINE -> {
                atLeast(failures, "base-wood", baseWood, 4);
                atLeast(failures, "height", visual.treeHeight(), 20);
            }
            case JUNGLE_BUSH -> {
                atMost(failures, "height", visual.treeHeight(), 8);
                atMost(failures, "base-wood", baseWood, 1);
            }
            case JUNGLE_SMALL -> {
                between(failures, "height", visual.treeHeight(), 8, 13);
                atMost(failures, "base-wood", baseWood, 1);
            }
            case JUNGLE_LARGE -> {
                atLeast(failures, "height", visual.treeHeight(), 14);
                atLeast(failures, "branches", branches, 4);
            }
            case JUNGLE_MEGA -> {
                atLeast(failures, "base-wood", baseWood, 4);
                atLeast(failures, "height", visual.treeHeight(), 28);
                atLeast(failures, "crown-width", visual.crownWidth(), 13);
            }
            case ACACIA_SINGLE_FORK -> {
                between(failures, "branches", branches, 1, 2);
                atLeast(failures, "crown-aspect-x100",
                        (int) Math.round(visual.crownAspect() * 100), 125);
            }
            case ACACIA_MULTI_FORK -> {
                atLeast(failures, "branches", branches, 3);
                atLeast(failures, "crown-width", visual.crownWidth(), 9);
            }
            case ACACIA_WINDSWEPT -> {
                between(failures, "branches", branches, 2, 4);
                atLeast(failures, "crown-aspect-x100",
                        (int) Math.round(visual.crownAspect() * 100), 125);
            }
            case DARK_OAK_STANDARD -> {
                atLeast(failures, "base-wood", baseWood, 4);
                atLeast(failures, "crown-width", visual.crownWidth(), 9);
            }
            case DARK_OAK_BROAD -> {
                atLeast(failures, "base-wood", baseWood, 4);
                atLeast(failures, "crown-width", visual.crownWidth(), 12);
            }
            case DARK_OAK_TALL -> {
                atLeast(failures, "base-wood", baseWood, 4);
                atLeast(failures, "height", visual.treeHeight(), 12);
            }
            case MANGROVE_SHORT -> {
                atMost(failures, "height", visual.treeHeight(), 11);
                atLeast(failures, "branches", branches, 3);
            }
            case MANGROVE_TALL -> {
                atLeast(failures, "height", visual.treeHeight(), 10);
                atLeast(failures, "leaf-levels", leafLevels, 4);
            }
            case MANGROVE_SPREADING -> {
                atLeast(failures, "crown-width", visual.crownWidth(), 11);
                atLeast(failures, "branches", branches, 4);
            }
            case CHERRY_COMPACT -> {
                atMost(failures, "crown-width", visual.crownWidth(), 10);
                atLeast(failures, "leaf-levels", leafLevels, 3);
            }
            case CHERRY_BROAD -> {
                atLeast(failures, "crown-width", visual.crownWidth(), 9);
                atLeast(failures, "branches", branches, 3);
            }
            case CHERRY_LAYERED -> {
                atLeast(failures, "leaf-levels", leafLevels, 6);
                atLeast(failures, "branches", branches, 3);
            }
        }
        return new Report(
                failures.isEmpty(),
                List.copyOf(failures),
                visual.treeHeight(),
                visual.crownWidth(),
                visual.crownHeight(),
                branches,
                baseWood,
                leafLevels);
    }

    private static void atLeast(
            List<String> failures, String name, int actual, int minimum) {
        if (actual < minimum) {
            failures.add(name + "=" + actual + "<" + minimum);
        }
    }

    private static void atMost(
            List<String> failures, String name, int actual, int maximum) {
        if (actual > maximum) {
            failures.add(name + "=" + actual + ">" + maximum);
        }
    }

    private static void between(
            List<String> failures, String name, int actual,
            int minimum, int maximum) {
        atLeast(failures, name, actual, minimum);
        atMost(failures, name, actual, maximum);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    record Report(
            boolean passed,
            List<String> failures,
            int height,
            int crownWidth,
            int crownHeight,
            int branches,
            int baseWood,
            int leafLevels
    ) {
        String metrics() {
            return "height=" + height
                    + " crown=" + crownWidth + "x" + crownHeight
                    + " branches=" + branches
                    + " baseWood=" + baseWood
                    + " leafLevels=" + leafLevels;
        }
    }
}
