package org.slowtrees.treeevolution;

final class TreeDnaNormalizer {
    NormalizedDna normalize(TreeDna dna) {
        TreeMaturityStage stage = normalizedStage(dna);
        int targetHeight = Math.max(targetHeightFloor(dna, stage), Math.min(dna.targetHeight(), targetHeightCap(dna)));
        int branchCount = Math.max(TreeShapeProfile.branchCountFloor(dna.species(), dna.personality(), targetHeight), Math.min(dna.branchCount(), branchCountCap(dna)));
        int branchLengthFloor = TreeShapeProfile.branchLengthFloor(dna.species(), dna.personality(), targetHeight);
        int minBranchLength = Math.min(Math.max(dna.minBranchLength(), Math.min(2, branchLengthFloor)), branchLengthCap(dna));
        int maxBranchLength = Math.max(branchLengthFloor, Math.min(dna.maxBranchLength(), branchLengthCap(dna)));
        int canopyRadiusX = Math.max(TreeShapeProfile.canopyRadiusFloor(dna.species(), dna.personality(), dna.rarity(), targetHeight, true), Math.min(dna.canopyRadiusX(), canopyRadiusCap(dna, true)));
        int canopyRadiusZ = Math.max(TreeShapeProfile.canopyRadiusFloor(dna.species(), dna.personality(), dna.rarity(), targetHeight, false), Math.min(dna.canopyRadiusZ(), canopyRadiusCap(dna, false)));
        int canopyRadius = Math.max(canopyRadiusX, canopyRadiusZ);
        int canopyRadiusY = Math.max(TreeShapeProfile.canopyVerticalRadiusFloor(dna.species(), canopyRadius), Math.min(dna.canopyRadiusY(), canopyVerticalCap(dna, canopyRadius)));
        double canopyDensity = Math.min(dna.canopyDensity(), canopyDensityCap(dna));
        int trunkWidth = Math.max(TreeShapeProfile.trunkWidthFloor(dna.species(), dna.personality(), dna.rarity(), targetHeight), Math.min(dna.trunkWidth(), trunkWidthCap(dna)));
        int canopyLayerCount = Math.max(TreeShapeProfile.canopyLayerFloor(dna.species(), dna.personality(), dna.rarity(), targetHeight), Math.min(dna.canopyLayerCount(), canopyLayerCountCap(dna)));
        int canopyLayerSpread = Math.max(canopyRadius, Math.min(dna.canopyLayerSpread(), canopyLayerSpreadCap(dna)));
        double branchStartRatio = Math.min(dna.branchStartRatio(), branchStartCap(dna));
        double branchRiseChance = Math.min(dna.branchRiseChance(), branchRiseCap(dna));

        boolean changed = stage != dna.maturityStage()
                || targetHeight != dna.targetHeight()
                || branchCount != dna.branchCount()
                || minBranchLength != dna.minBranchLength()
                || maxBranchLength != dna.maxBranchLength()
                || canopyRadiusX != dna.canopyRadiusX()
                || canopyRadiusY != dna.canopyRadiusY()
                || canopyRadiusZ != dna.canopyRadiusZ()
                || Math.abs(canopyDensity - dna.canopyDensity()) > 0.0001D
                || trunkWidth != dna.trunkWidth()
                || canopyLayerCount != dna.canopyLayerCount()
                || canopyLayerSpread != dna.canopyLayerSpread()
                || Math.abs(branchStartRatio - dna.branchStartRatio()) > 0.0001D
                || Math.abs(branchRiseChance - dna.branchRiseChance()) > 0.0001D;
        if (!changed) {
            return new NormalizedDna(dna, false, "unchanged");
        }

        TreeDna normalized = new TreeDna(
                dna.worldId(),
                dna.baseX(),
                dna.baseY(),
                dna.baseZ(),
                dna.species(),
                dna.seed(),
                dna.personality(),
                dna.rarity(),
                targetHeight,
                branchCount,
                minBranchLength,
                Math.max(minBranchLength, maxBranchLength),
                dna.branchBias(),
                canopyRadius,
                canopyRadiusX,
                canopyRadiusY,
                canopyRadiusZ,
                canopyDensity,
                branchStartRatio,
                branchRiseChance,
                dna.rootChance(),
                dna.vineChance(),
                dna.groundDetailChance(),
                trunkWidth,
                canopyLayerCount,
                canopyLayerSpread,
                dna.leanX(),
                dna.leanZ(),
                dna.leanStartRatio(),
                dna.profileSampleId(),
                dna.profileSampleSource(),
                dna.parentKey(),
                dna.generation(),
                normalizedIntent(dna.currentIntent(), stage),
                changed ? 0 : dna.planCursor(),
                0,
                Math.min(3, dna.blockedAttempts()),
                Math.min(dna.lastIntentChangeAge(), dna.age()),
                Math.min(dna.stageCleanupBurst(), 3),
                Math.min(dna.stageGrowthBurst(), 12),
                dna.age(),
                stage,
                dna.lastGrowthMillis(),
                dna.stalledUntilMillis(),
                Math.min(8, dna.damageCount()),
                dna.stumpPresent()
        );
        return new NormalizedDna(normalized, true, summary(dna, normalized));
    }

    private TreeMaturityStage normalizedStage(TreeDna dna) {
        if (dna.maturityStage() == TreeMaturityStage.ANCIENT && dna.age() < 24) {
            return TreeMaturityStage.MEDIUM;
        }
        if (dna.maturityStage() == TreeMaturityStage.ANCIENT && dna.age() < 80 && dna.rarity() != TreeRarity.LANDMARK) {
            return TreeMaturityStage.MATURE;
        }
        return dna.maturityStage();
    }

    private TreeGrowthIntent normalizedIntent(TreeGrowthIntent intent, TreeMaturityStage stage) {
        if (stage == TreeMaturityStage.SMALL || stage == TreeMaturityStage.MEDIUM) {
            return switch (intent) {
                case DETAIL, SEEDLING, CLEANUP -> TreeGrowthIntent.CANOPY;
                default -> intent;
            };
        }
        return intent;
    }

    private int targetHeightCap(TreeDna dna) {
        int cap = switch (dna.species()) {
            case BIRCH -> 28;
            case SPRUCE -> 34;
            case JUNGLE -> 48;
            case ACACIA -> 24;
            case DARK_OAK -> 32;
            case MANGROVE -> 30;
            case CHERRY -> 26;
            case OAK -> 30;
        };
        if (dna.rarity() == TreeRarity.RARE) {
            cap += 8;
        } else if (dna.rarity() == TreeRarity.LANDMARK || dna.personality() == TreePersonality.ANCIENT_LANDMARK) {
            cap += 18;
        }
        return cap;
    }

    private int targetHeightFloor(TreeDna dna, TreeMaturityStage stage) {
        int base = TreeShapeProfile.targetHeightFloor(dna.species(), dna.personality(), dna.rarity());
        int stageFloor = switch (stage) {
            case SMALL -> Math.max(8, (int) Math.round(base * 0.42D));
            case MEDIUM -> Math.max(10, (int) Math.round(base * 0.68D));
            case MATURE -> base;
            case ANCIENT -> Math.max(base + 2, (int) Math.round(base * 1.12D));
        };
        return Math.max(base, stageFloor);
    }

    private int branchCountCap(TreeDna dna) {
        int cap = switch (dna.species()) {
            case BIRCH -> 8;
            case SPRUCE -> 18;
            case JUNGLE -> 20;
            case ACACIA -> 12;
            case DARK_OAK -> 14;
            case MANGROVE -> 14;
            case CHERRY -> 12;
            case OAK -> 12;
        };
        if (dna.rarity() == TreeRarity.RARE) {
            cap += 4;
        } else if (dna.rarity() == TreeRarity.LANDMARK || dna.personality() == TreePersonality.ANCIENT_LANDMARK) {
            cap += 10;
        }
        return cap;
    }

    private int branchLengthCap(TreeDna dna) {
        int cap = switch (dna.species()) {
            case BIRCH -> 4;
            case SPRUCE -> 5;
            case JUNGLE, ACACIA -> 6;
            case DARK_OAK, MANGROVE, OAK, CHERRY -> 5;
        };
        if (dna.rarity() == TreeRarity.LANDMARK || dna.personality() == TreePersonality.ANCIENT_LANDMARK) {
            cap += 2;
        }
        return cap;
    }

    private int canopyRadiusCap(TreeDna dna, boolean xAxis) {
        int cap = switch (dna.species()) {
            case BIRCH -> 3;
            case SPRUCE -> 4;
            case JUNGLE -> 6;
            case ACACIA -> xAxis ? 5 : 4;
            case DARK_OAK -> 6;
            case MANGROVE -> 5;
            case CHERRY -> 5;
            case OAK -> 5;
        };
        if (dna.rarity() == TreeRarity.RARE) {
            cap++;
        } else if (dna.rarity() == TreeRarity.LANDMARK || dna.personality() == TreePersonality.ANCIENT_LANDMARK) {
            cap += 2;
        }
        return cap;
    }

    private int canopyVerticalCap(TreeDna dna, int horizontalRadius) {
        int cap = switch (dna.species()) {
            case SPRUCE -> Math.max(5, horizontalRadius + 1);
            case JUNGLE, DARK_OAK, MANGROVE -> Math.max(4, horizontalRadius / 2 + 1);
            case BIRCH -> Math.max(4, horizontalRadius + 1);
            case CHERRY, ACACIA -> 2;
            default -> Math.max(3, horizontalRadius / 2 + 1);
        };
        return Math.max(TreeShapeProfile.canopyVerticalRadiusFloor(dna.species(), horizontalRadius), cap);
    }

    private double canopyDensityCap(TreeDna dna) {
        return switch (dna.species()) {
            case CHERRY -> 0.72D;
            case ACACIA, BIRCH -> 0.70D;
            case DARK_OAK -> 0.86D;
            default -> 0.82D;
        };
    }

    private int trunkWidthCap(TreeDna dna) {
        int cap = switch (dna.species()) {
            case JUNGLE -> 4;
            case DARK_OAK -> 5;
            case MANGROVE -> 4;
            default -> 2;
        };
        if (dna.rarity() == TreeRarity.LANDMARK || dna.personality() == TreePersonality.ANCIENT_LANDMARK || dna.personality() == TreePersonality.HOLLOW) {
            cap += 2;
        }
        return Math.min(6, cap);
    }

    private int canopyLayerCountCap(TreeDna dna) {
        int cap = switch (dna.species()) {
            case SPRUCE -> 4;
            case JUNGLE, DARK_OAK -> 3;
            case CHERRY -> 2;
            default -> 2;
        };
        if (dna.rarity() == TreeRarity.LANDMARK || dna.personality() == TreePersonality.ANCIENT_LANDMARK) {
            cap++;
        }
        return cap;
    }

    private int canopyLayerSpreadCap(TreeDna dna) {
        return switch (dna.species()) {
            case JUNGLE, DARK_OAK -> 7;
            case CHERRY, ACACIA -> 5;
            default -> 6;
        };
    }

    private double branchStartCap(TreeDna dna) {
        return dna.species() == TreeSpecies.BIRCH ? 0.78D : 0.68D;
    }

    private double branchRiseCap(TreeDna dna) {
        return dna.species() == TreeSpecies.BIRCH ? 0.24D : 0.55D;
    }

    private String summary(TreeDna before, TreeDna after) {
        return "target-height " + before.targetHeight() + "->" + after.targetHeight()
                + ", stage " + before.maturityStage() + "->" + after.maturityStage()
                + ", branches " + before.branchCount() + "->" + after.branchCount()
                + ", canopy " + before.canopyRadiusX() + "x" + before.canopyRadiusY() + "x" + before.canopyRadiusZ()
                + "->" + after.canopyRadiusX() + "x" + after.canopyRadiusY() + "x" + after.canopyRadiusZ()
                + ", trunk-width " + before.trunkWidth() + "->" + after.trunkWidth()
                + ", layers " + before.canopyLayerCount() + "/" + before.canopyLayerSpread()
                + "->" + after.canopyLayerCount() + "/" + after.canopyLayerSpread();
    }

    record NormalizedDna(TreeDna dna, boolean changed, String summary) {
    }
}
