package org.slowtrees.treeevolution;

enum TreeGrowthIntent {
    HEIGHT,
    WIDTH,
    BRANCH,
    CANOPY,
    CLEANUP,
    DETAIL,
    SEEDLING,
    REPAIR;

    double delayMultiplier(TreeDna dna) {
        return switch (this) {
            case HEIGHT -> dna.maturityStage() == TreeMaturityStage.SMALL ? 0.72D : 0.92D;
            case WIDTH -> dna.hugeArchitecture() ? 1.18D : 1.0D;
            case BRANCH -> 0.90D;
            case CANOPY -> 0.82D;
            case CLEANUP -> 0.58D;
            case DETAIL -> 1.22D;
            case SEEDLING -> dna.rarity() == TreeRarity.LANDMARK ? 2.6D : 3.8D;
            case REPAIR -> 0.66D;
        };
    }
}
