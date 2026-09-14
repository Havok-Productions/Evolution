package org.evolution.features.treeevolution;

/**
 * ## Converts measurable source geometry into a stable architecture family.
 */
final class TreeVariantClassifier {
    private TreeVariantClassifier() {
    }

    static TreeVariant classify(
            TreeSpecies species,
            TreeSourcePattern source
    ) {
        return switch (species) {
            case OAK -> classifyOak(source);
            case BIRCH -> source.height() >= 8
                    ? TreeVariant.BIRCH_TALL
                    : TreeVariant.BIRCH_STANDARD;
            case SPRUCE -> classifySpruce(source);
            case JUNGLE -> classifyJungle(source);
            case ACACIA -> classifyAcacia(source);
            case DARK_OAK -> classifyDarkOak(source);
            case MANGROVE -> classifyMangrove(source);
            case CHERRY -> classifyCherry(source);
        };
    }

    static TreeVariant inferLegacy(
            TreeSpecies species,
            TreePersonality personality,
            int targetHeight,
            int trunkWidth,
            int canopyRadius,
            int canopyLayers
    ) {
        TreeSourcePattern inferred = TreeSourcePattern.legacy(
                targetHeight, trunkWidth, canopyRadius, canopyLayers);
        if (species == TreeSpecies.SPRUCE
                && (personality == TreePersonality.SPIRE
                        || personality == TreePersonality.TALL)) {
            return trunkWidth >= 3
                    ? TreeVariant.SPRUCE_MEGA_PINE
                    : TreeVariant.SPRUCE_PINE;
        }
        if (species == TreeSpecies.OAK
                && (personality == TreePersonality.FORKED
                        || personality == TreePersonality.CROOKED)) {
            return TreeVariant.OAK_FANCY;
        }
        if (species == TreeSpecies.ACACIA
                && personality == TreePersonality.WINDSWEPT) {
            return TreeVariant.ACACIA_WINDSWEPT;
        }
        return classify(species, inferred);
    }

    private static TreeVariant classifyOak(TreeSourcePattern source) {
        if (source.branchSpread() >= 3 && source.logCount() >= 9) {
            return TreeVariant.OAK_FANCY;
        }
        if (source.height() >= 9) {
            return TreeVariant.OAK_TALL;
        }
        if (source.canopyRadius() >= 4) {
            return TreeVariant.OAK_BROAD;
        }
        return TreeVariant.OAK_STANDARD;
    }

    private static TreeVariant classifySpruce(TreeSourcePattern source) {
        boolean mega = source.trunkFootprint() >= 4
                || source.logCount() >= 28;
        boolean pine = source.canopyStartRatio() >= 0.42D
                || source.height() >= 15
                        && source.canopyDepth() <= 7;
        if (mega) {
            return pine ? TreeVariant.SPRUCE_MEGA_PINE
                    : TreeVariant.SPRUCE_MEGA;
        }
        return pine ? TreeVariant.SPRUCE_PINE
                : TreeVariant.SPRUCE_CLASSIC;
    }

    private static TreeVariant classifyJungle(TreeSourcePattern source) {
        if (source.trunkFootprint() >= 4
                || source.logCount() >= 30) {
            return TreeVariant.JUNGLE_MEGA;
        }
        if (source.height() <= 4 && source.logCount() <= 5) {
            return TreeVariant.JUNGLE_BUSH;
        }
        if (source.height() >= 10 || source.branchSpread() >= 3) {
            return TreeVariant.JUNGLE_LARGE;
        }
        return TreeVariant.JUNGLE_SMALL;
    }

    private static TreeVariant classifyAcacia(TreeSourcePattern source) {
        if (source.trunkDrift() >= 2) {
            return TreeVariant.ACACIA_WINDSWEPT;
        }
        if (source.branchSpread() >= 4
                || source.logCount() >= 12) {
            return TreeVariant.ACACIA_MULTI_FORK;
        }
        return TreeVariant.ACACIA_SINGLE_FORK;
    }

    private static TreeVariant classifyDarkOak(TreeSourcePattern source) {
        if (source.height() >= 11) {
            return TreeVariant.DARK_OAK_TALL;
        }
        if (source.canopyRadius() >= 5
                || source.branchSpread() >= 4) {
            return TreeVariant.DARK_OAK_BROAD;
        }
        return TreeVariant.DARK_OAK_STANDARD;
    }

    private static TreeVariant classifyMangrove(TreeSourcePattern source) {
        if (source.branchSpread() >= 4
                || source.canopyRadius() >= 5) {
            return TreeVariant.MANGROVE_SPREADING;
        }
        return source.height() <= 8
                ? TreeVariant.MANGROVE_SHORT
                : TreeVariant.MANGROVE_TALL;
    }

    private static TreeVariant classifyCherry(TreeSourcePattern source) {
        if (source.crownTiers() >= 2
                || source.canopyDepth() >= 5) {
            return TreeVariant.CHERRY_LAYERED;
        }
        if (source.canopyRadius() >= 4) {
            return TreeVariant.CHERRY_BROAD;
        }
        return TreeVariant.CHERRY_COMPACT;
    }
}
