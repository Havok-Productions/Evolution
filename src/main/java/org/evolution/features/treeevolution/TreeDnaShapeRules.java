package org.evolution.features.treeevolution;

/**
 * ## DNA SHAPE RULES
 *
 * <p>Centralizes deterministic normalization applied to every newly created,
 * restored, or migrated tree. This layer never reads or changes the world.</p>
 */
final class TreeDnaShapeRules {
    private TreeDnaShapeRules() {
    }

    static int normalizeBranchCount(TreeSpecies species,
            TreePersonality personality, int targetHeight, int branchCount) {
        int minimum = TreeShapeProfile.branchCountFloor(
                species, personality, targetHeight);
        if (targetHeight >= 18) {
            minimum = Math.max(minimum,
                    species == TreeSpecies.BIRCH ? 3 : 4);
        } else if (targetHeight >= 12) {
            minimum = Math.max(minimum,
                    species == TreeSpecies.BIRCH
                            || species == TreeSpecies.SPRUCE ? 2 : 3);
        } else if (targetHeight >= 8) {
            minimum = Math.max(minimum,
                    species == TreeSpecies.SPRUCE ? 2 : 1);
        }
        if (personality == TreePersonality.SPARSE) {
            minimum = Math.max(1, minimum - 1);
        }
        if (personality == TreePersonality.WIDE
                || personality == TreePersonality.FORKED
                || personality == TreePersonality.UMBRELLA
                || personality == TreePersonality.ANCIENT_LANDMARK) {
            minimum++;
        }
        return Math.max(branchCount, minimum);
    }

    static int minimumHorizontalCanopyRadius(TreeSpecies species,
            TreePersonality personality, int targetHeight) {
        int minimum = 1;
        if (targetHeight >= 18) {
            minimum = 3;
        } else if (targetHeight >= 10) {
            minimum = 2;
        }
        if (species == TreeSpecies.SPRUCE
                && (personality == TreePersonality.SPIRE
                        || personality == TreePersonality.TALL)) {
            minimum = Math.max(1, minimum - 1);
        }
        if (species == TreeSpecies.JUNGLE
                || species == TreeSpecies.DARK_OAK
                || species == TreeSpecies.CHERRY
                || personality == TreePersonality.WIDE
                || personality == TreePersonality.UMBRELLA
                || personality == TreePersonality.LAYERED) {
            minimum++;
        }
        if (personality == TreePersonality.SPARSE && targetHeight < 12) {
            minimum = Math.max(1, minimum - 1);
        }
        return Math.max(1, Math.min(5, minimum));
    }

    static int normalizeCanopyVerticalRadius(TreeSpecies species,
            TreePersonality personality, int targetHeight, int radiusY,
            int radiusX, int radiusZ) {
        int vertical = Math.max(1, radiusY);
        int horizontal = Math.max(radiusX, radiusZ);
        int floor = TreeShapeProfile.canopyVerticalRadiusFloor(
                species, horizontal);
        if (species == TreeSpecies.SPRUCE
                && (personality == TreePersonality.SPIRE
                        || personality == TreePersonality.TALL)) {
            return Math.max(floor, Math.min(Math.max(vertical, floor),
                    Math.max(2, horizontal + 1)));
        }
        if (personality == TreePersonality.TALL && targetHeight >= 18) {
            return Math.max(floor, Math.min(Math.max(vertical, floor),
                    Math.max(2, horizontal)));
        }
        return Math.max(floor, Math.min(Math.max(vertical, floor),
                Math.max(2, (horizontal / 2) + 1)));
    }

    static double normalizeBranchStart(TreeSpecies species,
            TreePersonality personality, int targetHeight,
            double branchStartRatio) {
        double upper = species == TreeSpecies.SPRUCE ? 0.72D : 0.66D;
        if (targetHeight >= 16 && species != TreeSpecies.SPRUCE) {
            upper -= 0.04D;
        }
        if (personality == TreePersonality.WIDE
                || personality == TreePersonality.FORKED
                || personality == TreePersonality.UMBRELLA
                || personality == TreePersonality.ANCIENT_LANDMARK) {
            upper -= 0.06D;
        }
        if (personality == TreePersonality.SPARSE && targetHeight < 10) {
            upper += 0.05D;
        }
        return clamp(branchStartRatio, 0.25D, Math.max(0.48D, upper));
    }

    static double clamp(double value, double minimum,
            double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    static int targetHeightFloor(TreeSpecies species,
            TreePersonality personality, TreeRarity rarity) {
        return TreeShapeProfile.targetHeightFloor(
                species, personality, rarity);
    }
}
