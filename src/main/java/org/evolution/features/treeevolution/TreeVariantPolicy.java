package org.evolution.features.treeevolution;

/**
 * ## Variant-level architecture constraints shared by all planners.
 */
final class TreeVariantPolicy {
    private TreeVariantPolicy() {
    }

    static int targetHeightFloor(
            TreeDna dna,
            TreePersonality personality,
            TreeRarity rarity
    ) {
        return targetHeightFloor(
                dna.species(), dna.variant(), personality, rarity);
    }

    static int targetHeightFloor(
            TreeSpecies species,
            TreeVariant variant,
            TreePersonality personality,
            TreeRarity rarity
    ) {
        int floor = switch (variant) {
            case OAK_STANDARD -> 10;
            case OAK_FANCY, OAK_BROAD -> 14;
            case OAK_TALL -> 17;
            case BIRCH_STANDARD -> 13;
            case BIRCH_TALL -> 19;
            case SPRUCE_CLASSIC -> 16;
            case SPRUCE_PINE -> 22;
            case SPRUCE_MEGA -> 28;
            case SPRUCE_MEGA_PINE -> 34;
            case JUNGLE_BUSH -> 6;
            case JUNGLE_SMALL -> 14;
            case JUNGLE_LARGE -> 24;
            case JUNGLE_MEGA -> 36;
            case ACACIA_SINGLE_FORK -> 12;
            case ACACIA_MULTI_FORK, ACACIA_WINDSWEPT -> 15;
            case DARK_OAK_STANDARD -> 15;
            case DARK_OAK_BROAD -> 17;
            case DARK_OAK_TALL -> 21;
            case MANGROVE_SHORT -> 11;
            case MANGROVE_TALL -> 18;
            case MANGROVE_SPREADING -> 16;
            case CHERRY_COMPACT -> 12;
            case CHERRY_BROAD -> 16;
            case CHERRY_LAYERED -> 18;
        };
        if (variant == TreeVariant.JUNGLE_BUSH) {
            floor += rarity == TreeRarity.COMMON ? 0 : 2;
        } else if (rarity == TreeRarity.RARE) {
            floor += species == TreeSpecies.JUNGLE
                    || species == TreeSpecies.SPRUCE ? 7 : 4;
        } else if (rarity == TreeRarity.LANDMARK
                || personality == TreePersonality.ANCIENT_LANDMARK) {
            floor += species == TreeSpecies.JUNGLE
                    || species == TreeSpecies.SPRUCE ? 14 : 8;
        }
        return Math.max(5, floor);
    }

    static int targetHeightCap(
            TreeSpecies species,
            TreeVariant variant,
            TreePersonality personality,
            TreeRarity rarity,
            TreeSourcePattern source
    ) {
        int cap = switch (variant) {
            case OAK_STANDARD -> 18;
            case OAK_FANCY -> 28;
            case OAK_TALL -> 32;
            case OAK_BROAD -> 24;
            case BIRCH_STANDARD -> 18;
            case BIRCH_TALL -> 34;
            case SPRUCE_CLASSIC -> 26;
            case SPRUCE_PINE -> 38;
            case SPRUCE_MEGA -> 48;
            case SPRUCE_MEGA_PINE -> 64;
            case JUNGLE_BUSH -> 10;
            case JUNGLE_SMALL -> 18;
            case JUNGLE_LARGE -> 32;
            case JUNGLE_MEGA -> 72;
            case ACACIA_SINGLE_FORK -> 18;
            case ACACIA_MULTI_FORK -> 24;
            case ACACIA_WINDSWEPT -> 26;
            case DARK_OAK_STANDARD -> 24;
            case DARK_OAK_BROAD -> 28;
            case DARK_OAK_TALL -> 34;
            case MANGROVE_SHORT -> 18;
            case MANGROVE_TALL -> 30;
            case MANGROVE_SPREADING -> 28;
            case CHERRY_COMPACT -> 18;
            case CHERRY_BROAD -> 25;
            case CHERRY_LAYERED -> 30;
        };
        if (rarity == TreeRarity.RARE) {
            cap += species == TreeSpecies.JUNGLE
                    || species == TreeSpecies.SPRUCE ? 8 : 4;
        } else if (rarity == TreeRarity.LANDMARK
                || personality == TreePersonality.ANCIENT_LANDMARK) {
            cap += species == TreeSpecies.JUNGLE
                    || species == TreeSpecies.SPRUCE ? 18 : 8;
        }
        if (variant == TreeVariant.JUNGLE_BUSH) {
            cap = Math.min(cap, rarity == TreeRarity.COMMON ? 10 : 12);
        }
        int sourceAwareCap = source.measured()
                ? Math.max(source.height(), cap) : cap;
        return Math.max(
                targetHeightFloor(
                        species, variant, personality, rarity),
                sourceAwareCap);
    }

    static int trunkWidthFloor(TreeDna dna) {
        int sourceWidth = (int) Math.ceil(
                Math.sqrt(dna.sourcePattern().trunkFootprint()));
        int variantFloor = switch (dna.variant()) {
            case SPRUCE_MEGA, SPRUCE_MEGA_PINE, JUNGLE_MEGA -> 2;
            case DARK_OAK_STANDARD, DARK_OAK_BROAD,
                    DARK_OAK_TALL -> 2;
            default -> 1;
        };
        return Math.max(variantFloor,
                dna.sourcePattern().measured() ? sourceWidth : 1);
    }

    static int branchCountFloor(TreeDna dna) {
        return branchCountFloor(dna.variant());
    }

    static int branchCountFloor(TreeVariant variant) {
        return switch (variant) {
            case OAK_FANCY -> 6;
            case OAK_BROAD, OAK_TALL -> 5;
            case BIRCH_STANDARD -> 1;
            case BIRCH_TALL -> 3;
            case SPRUCE_CLASSIC -> 6;
            case SPRUCE_PINE -> 5;
            case SPRUCE_MEGA -> 10;
            case SPRUCE_MEGA_PINE -> 8;
            case JUNGLE_BUSH -> 1;
            case JUNGLE_SMALL -> 4;
            case JUNGLE_LARGE -> 7;
            case JUNGLE_MEGA -> 10;
            case ACACIA_SINGLE_FORK -> 2;
            case ACACIA_MULTI_FORK, ACACIA_WINDSWEPT -> 4;
            case DARK_OAK_STANDARD -> 5;
            case DARK_OAK_BROAD, DARK_OAK_TALL -> 7;
            case MANGROVE_SHORT -> 3;
            case MANGROVE_TALL -> 5;
            case MANGROVE_SPREADING -> 7;
            case CHERRY_COMPACT -> 3;
            case CHERRY_BROAD -> 5;
            case CHERRY_LAYERED -> 6;
            default -> 4;
        };
    }

    static int branchLengthFloor(TreeDna dna) {
        return branchLengthFloor(dna.variant());
    }

    static int branchLengthFloor(TreeVariant variant) {
        return switch (variant) {
            case JUNGLE_BUSH, BIRCH_STANDARD -> 1;
            case BIRCH_TALL, SPRUCE_CLASSIC -> 2;
            case OAK_FANCY, OAK_BROAD, JUNGLE_LARGE,
                    ACACIA_MULTI_FORK, ACACIA_WINDSWEPT,
                    DARK_OAK_BROAD, MANGROVE_SPREADING,
                    CHERRY_BROAD, CHERRY_LAYERED -> 5;
            case JUNGLE_MEGA, SPRUCE_MEGA,
                    SPRUCE_MEGA_PINE -> 6;
            default -> 3;
        };
    }

    static int canopyRadiusFloor(TreeDna dna, boolean xAxis) {
        int floor = switch (dna.variant()) {
            case JUNGLE_BUSH -> 2;
            case BIRCH_STANDARD, BIRCH_TALL -> 2;
            case SPRUCE_CLASSIC, SPRUCE_PINE -> 3;
            case SPRUCE_MEGA, SPRUCE_MEGA_PINE -> 5;
            case JUNGLE_SMALL -> 3;
            case JUNGLE_LARGE -> 5;
            case JUNGLE_MEGA -> 7;
            case ACACIA_SINGLE_FORK -> xAxis ? 4 : 3;
            case ACACIA_MULTI_FORK, ACACIA_WINDSWEPT ->
                    xAxis ? 6 : 5;
            case DARK_OAK_STANDARD -> 5;
            case DARK_OAK_BROAD -> 7;
            case DARK_OAK_TALL -> 6;
            case MANGROVE_SHORT -> 4;
            case MANGROVE_TALL -> 5;
            case MANGROVE_SPREADING -> 7;
            case CHERRY_COMPACT -> 3;
            case CHERRY_BROAD -> 5;
            case CHERRY_LAYERED -> 6;
            case OAK_STANDARD -> 3;
            case OAK_FANCY, OAK_TALL -> 4;
            case OAK_BROAD -> 6;
        };
        if (dna.sourcePattern().measured()) {
            floor = Math.max(floor,
                    Math.min(10, dna.sourcePattern().canopyRadius()));
        }
        return floor;
    }

    static int canopyLayerFloor(TreeDna dna) {
        return switch (dna.variant()) {
            case SPRUCE_CLASSIC, SPRUCE_PINE -> 2;
            case SPRUCE_MEGA, SPRUCE_MEGA_PINE -> 4;
            case JUNGLE_MEGA -> 2;
            case CHERRY_LAYERED -> 3;
            case CHERRY_BROAD, DARK_OAK_BROAD -> 2;
            default -> 0;
        };
    }

    static double branchStartAdjustment(TreeDna dna) {
        return switch (dna.variant()) {
            case SPRUCE_CLASSIC, SPRUCE_MEGA -> -0.14D;
            case SPRUCE_PINE, SPRUCE_MEGA_PINE -> 0.16D;
            case JUNGLE_BUSH -> -0.18D;
            case JUNGLE_LARGE, JUNGLE_MEGA -> 0.10D;
            case OAK_FANCY, DARK_OAK_BROAD,
                    MANGROVE_SPREADING -> -0.10D;
            case ACACIA_SINGLE_FORK -> 0.10D;
            case ACACIA_MULTI_FORK, ACACIA_WINDSWEPT -> -0.04D;
            case CHERRY_LAYERED -> -0.08D;
            default -> 0.0D;
        };
    }

    static double horizontalScale(TreeDna dna) {
        return switch (dna.variant()) {
            case OAK_BROAD, DARK_OAK_BROAD,
                    MANGROVE_SPREADING -> 1.18D;
            case OAK_TALL, BIRCH_TALL, SPRUCE_PINE,
                    SPRUCE_MEGA_PINE, DARK_OAK_TALL -> 0.86D;
            case JUNGLE_BUSH, CHERRY_COMPACT -> 0.78D;
            case JUNGLE_LARGE, JUNGLE_MEGA,
                    ACACIA_MULTI_FORK, ACACIA_WINDSWEPT,
                    CHERRY_LAYERED -> 1.10D;
            default -> 1.0D;
        };
    }

    static double branchLengthScale(TreeDna dna) {
        return switch (dna.variant()) {
            case JUNGLE_BUSH, CHERRY_COMPACT,
                    BIRCH_STANDARD -> 0.72D;
            case OAK_FANCY, OAK_BROAD, JUNGLE_LARGE,
                    JUNGLE_MEGA, ACACIA_MULTI_FORK,
                    ACACIA_WINDSWEPT, DARK_OAK_BROAD,
                    MANGROVE_SPREADING -> 1.18D;
            default -> 1.0D;
        };
    }

    static int stageTrunkWidthFloor(TreeDna dna) {
        int source = trunkWidthFloor(dna);
        if (dna.maturityStage() == TreeMaturityStage.SMALL) {
            return Math.min(2, source);
        }
        return source;
    }

    static int stageTrunkWidthCap(TreeDna dna) {
        int target = Math.max(trunkWidthFloor(dna), dna.trunkWidth());
        return switch (dna.maturityStage()) {
            case SMALL -> Math.min(target,
                    switch (dna.variant()) {
                        case SPRUCE_MEGA, SPRUCE_MEGA_PINE,
                                JUNGLE_MEGA, DARK_OAK_STANDARD,
                                DARK_OAK_BROAD, DARK_OAK_TALL -> 2;
                        default -> 1;
                    });
            case MEDIUM -> Math.min(target,
                    switch (dna.variant()) {
                        case JUNGLE_MEGA, SPRUCE_MEGA,
                                SPRUCE_MEGA_PINE -> 2;
                        case DARK_OAK_STANDARD, DARK_OAK_BROAD,
                                DARK_OAK_TALL, MANGROVE_SPREADING -> 3;
                        default -> 1;
                    });
            case MATURE -> Math.max(2, target);
            case ANCIENT -> target;
        };
    }

    static int stageVisibleHeightFloor(TreeDna dna) {
        int mature = targetHeightFloor(
                dna, dna.personality(), dna.rarity());
        return switch (dna.maturityStage()) {
            case SMALL -> Math.max(4, (int) Math.round(mature * 0.38D));
            case MEDIUM -> Math.max(6, (int) Math.round(mature * 0.62D));
            case MATURE -> mature;
            case ANCIENT -> mature + Math.max(2, mature / 9);
        };
    }

    static int earlyBranchCap(TreeDna dna) {
        return switch (dna.maturityStage()) {
            case SMALL -> switch (dna.variant()) {
                case JUNGLE_BUSH, BIRCH_STANDARD,
                        BIRCH_TALL -> 1;
                case SPRUCE_MEGA, SPRUCE_MEGA_PINE -> 6;
                case SPRUCE_CLASSIC, SPRUCE_PINE -> 5;
                case ACACIA_MULTI_FORK, ACACIA_WINDSWEPT -> 3;
                default -> 2;
            };
            case MEDIUM -> switch (dna.variant()) {
                case JUNGLE_BUSH -> 2;
                case JUNGLE_MEGA -> 7;
                case SPRUCE_MEGA, SPRUCE_MEGA_PINE -> 11;
                case SPRUCE_CLASSIC, SPRUCE_PINE -> 8;
                case BIRCH_STANDARD -> 2;
                case BIRCH_TALL -> 3;
                case OAK_FANCY, ACACIA_MULTI_FORK,
                        ACACIA_WINDSWEPT, DARK_OAK_BROAD,
                        MANGROVE_SPREADING, CHERRY_LAYERED -> 5;
                default -> 4;
            };
            case MATURE, ANCIENT -> Integer.MAX_VALUE;
        };
    }

    static boolean allowsEarlyLayers(TreeDna dna) {
        return switch (dna.variant()) {
            case SPRUCE_CLASSIC, SPRUCE_PINE,
                    SPRUCE_MEGA, SPRUCE_MEGA_PINE,
                    CHERRY_LAYERED, JUNGLE_MEGA -> true;
            default -> false;
        };
    }
}
