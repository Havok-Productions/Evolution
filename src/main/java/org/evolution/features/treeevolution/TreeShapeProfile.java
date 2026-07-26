package org.evolution.features.treeevolution;

final class TreeShapeProfile {
    private TreeShapeProfile() {
    }

    static int targetHeightFloor(TreeSpecies species, TreePersonality personality, TreeRarity rarity) {
        int floor = switch (species) {
            case OAK -> 14;
            case BIRCH -> 18;
            case SPRUCE -> 22;
            case JUNGLE -> 30;
            case ACACIA -> 14;
            case DARK_OAK -> 16;
            case MANGROVE -> 18;
            case CHERRY -> 16;
        };
        if (rarity == TreeRarity.RARE) {
            floor += switch (species) {
                case JUNGLE, SPRUCE -> 10;
                case DARK_OAK, OAK, CHERRY -> 6;
                default -> 4;
            };
        } else if (rarity == TreeRarity.LANDMARK || personality == TreePersonality.ANCIENT_LANDMARK) {
            floor += switch (species) {
                case JUNGLE -> 22;
                case SPRUCE -> 16;
                case OAK, DARK_OAK -> 12;
                case BIRCH -> 10;
                case CHERRY, MANGROVE -> 8;
                case ACACIA -> 6;
            };
        }
        if (personality == TreePersonality.SPARSE && rarity == TreeRarity.COMMON) {
            floor -= species == TreeSpecies.BIRCH ? 2 : 3;
        }
        return Math.max(8, floor);
    }

    static int trunkWidthFloor(TreeSpecies species, TreePersonality personality, TreeRarity rarity, int targetHeight) {
        int width = switch (species) {
            case OAK -> targetHeight >= 22 ? 3 : targetHeight >= 16 ? 2 : 1;
            case BIRCH -> targetHeight >= 28 && rarity != TreeRarity.COMMON ? 2 : 1;
            case SPRUCE -> targetHeight >= 34 ? 3 : targetHeight >= 22 ? 2 : 1;
            case JUNGLE -> targetHeight >= 48 ? 5 : targetHeight >= 36 ? 4 : targetHeight >= 28 ? 2 : 1;
            case ACACIA -> targetHeight >= 18 ? 3 : targetHeight >= 12 ? 2 : 1;
            case DARK_OAK -> targetHeight >= 26 ? 5 : targetHeight >= 18 ? 4 : 3;
            case MANGROVE -> targetHeight >= 28 ? 4 : targetHeight >= 18 ? 3 : 2;
            case CHERRY -> targetHeight >= 22 ? 3 : targetHeight >= 15 ? 2 : 1;
        };
        if (rarity == TreeRarity.LANDMARK || personality == TreePersonality.ANCIENT_LANDMARK) {
            width += switch (species) {
                case JUNGLE, DARK_OAK -> 1;
                case BIRCH -> 0;
                default -> targetHeight >= 20 ? 1 : 0;
            };
        }
        if (personality == TreePersonality.WIDE || personality == TreePersonality.HOLLOW) {
            width++;
        }
        return Math.max(1, Math.min(8, width));
    }

    static int canopyRadiusFloor(TreeSpecies species, TreePersonality personality, TreeRarity rarity, int targetHeight, boolean xAxis) {
        int radius = switch (species) {
            case OAK -> targetHeight >= 24 ? 5 : 4;
            case BIRCH -> targetHeight >= 24 ? 3 : 2;
            case SPRUCE -> targetHeight >= 30 ? 5 : 4;
            case JUNGLE -> targetHeight >= 42 ? 7 : 6;
            case ACACIA -> xAxis ? 5 : 4;
            case DARK_OAK -> targetHeight >= 24 ? 7 : 6;
            case MANGROVE -> targetHeight >= 24 ? 6 : 5;
            case CHERRY -> targetHeight >= 22 ? 6 : 5;
        };
        if (rarity == TreeRarity.RARE) {
            radius++;
        } else if (rarity == TreeRarity.LANDMARK || personality == TreePersonality.ANCIENT_LANDMARK) {
            radius += species == TreeSpecies.BIRCH ? 1 : 1;
        }
        if (personality == TreePersonality.UMBRELLA || personality == TreePersonality.WIDE || personality == TreePersonality.LAYERED) {
            radius++;
        }
        if (personality == TreePersonality.SPARSE && species != TreeSpecies.BIRCH) {
            radius--;
        }
        return Math.max(1, Math.min(12, radius));
    }

    static int canopyVerticalRadiusFloor(TreeSpecies species, int horizontalRadius) {
        return switch (species) {
            case SPRUCE -> Math.max(3, horizontalRadius / 2 + 1);
            case JUNGLE, DARK_OAK -> Math.max(3, horizontalRadius / 3 + 1);
            case BIRCH -> Math.max(3, horizontalRadius);
            case ACACIA, CHERRY -> 1;
            default -> Math.max(2, horizontalRadius / 3 + 1);
        };
    }

    static int branchCountFloor(TreeSpecies species, TreePersonality personality, int targetHeight) {
        int floor = switch (species) {
            case BIRCH -> targetHeight >= 22 ? 3 : 1;
            case SPRUCE -> targetHeight >= 28 ? 8 : 5;
            case JUNGLE -> targetHeight >= 40 ? 8 : 5;
            case ACACIA -> 4;
            case DARK_OAK -> 6;
            case MANGROVE -> 5;
            case CHERRY -> 5;
            case OAK -> 5;
        };
        if (personality == TreePersonality.SPARSE) {
            floor = Math.max(1, floor - 2);
        } else if (personality == TreePersonality.WIDE
                || personality == TreePersonality.FORKED
                || personality == TreePersonality.UMBRELLA
                || personality == TreePersonality.ANCIENT_LANDMARK) {
            floor += 2;
        }
        return floor;
    }

    static int branchLengthFloor(TreeSpecies species, TreePersonality personality, int targetHeight) {
        int floor = switch (species) {
            case BIRCH -> 2;
            case SPRUCE -> 3;
            case JUNGLE -> targetHeight >= 40 ? 6 : 4;
            case ACACIA -> 5;
            case DARK_OAK -> 5;
            case MANGROVE -> 4;
            case CHERRY -> 4;
            case OAK -> 5;
        };
        if (personality == TreePersonality.UMBRELLA || personality == TreePersonality.WIDE || personality == TreePersonality.ANCIENT_LANDMARK) {
            floor++;
        }
        return floor;
    }

    static int canopyLayerFloor(TreeSpecies species, TreePersonality personality, TreeRarity rarity, int targetHeight) {
        int layers = switch (species) {
            case SPRUCE -> targetHeight >= 30 ? 4 : 3;
            case JUNGLE -> targetHeight >= 40 ? 3 : 1;
            case CHERRY -> 2;
            case DARK_OAK -> targetHeight >= 22 ? 2 : 1;
            case OAK -> targetHeight >= 22 ? 1 : 0;
            default -> 0;
        };
        if (rarity == TreeRarity.LANDMARK || personality == TreePersonality.ANCIENT_LANDMARK) {
            layers++;
        }
        return Math.max(0, Math.min(7, layers));
    }
}
