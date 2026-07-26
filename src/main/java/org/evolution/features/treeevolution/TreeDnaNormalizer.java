package org.evolution.features.treeevolution;

final class TreeDnaNormalizer {
    NormalizedDna normalize(TreeDna dna) {
        return normalize(dna, TreeMaturityStage.ANCIENT);
    }

    NormalizedDna normalize(TreeDna dna, TreeMaturityStage maximumStage) {
        boolean giantStagesHeld = maximumStage.ordinal() < TreeMaturityStage.ANCIENT.ordinal();
        boolean shapeRevisionChanged = dna.shapeRevision() < TreeDna.CURRENT_SHAPE_REVISION;
        TreePersonality personality = normalizedPersonality(dna, giantStagesHeld);
        TreeRarity rarity = normalizedRarity(dna, giantStagesHeld);
        TreeMaturityStage stage = normalizedStage(dna, maximumStage);
        boolean stageDowngraded = stage.ordinal() < dna.maturityStage().ordinal();
        int targetHeight = Math.max(
                targetHeightFloor(dna, stage, personality, rarity),
                Math.min(dna.targetHeight(), targetHeightCap(dna, personality, rarity, giantStagesHeld))
        );
        int branchCount = Math.max(TreeShapeProfile.branchCountFloor(dna.species(), personality, targetHeight), Math.min(dna.branchCount(), branchCountCap(dna, personality, rarity)));
        int branchLengthFloor = TreeShapeProfile.branchLengthFloor(dna.species(), personality, targetHeight);
        int minBranchLength = Math.min(Math.max(dna.minBranchLength(), Math.min(2, branchLengthFloor)), branchLengthCap(dna, personality, rarity));
        int maxBranchLength = Math.max(branchLengthFloor, Math.min(dna.maxBranchLength(), branchLengthCap(dna, personality, rarity)));
        int canopyRadiusX = Math.max(TreeShapeProfile.canopyRadiusFloor(dna.species(), personality, rarity, targetHeight, true), Math.min(dna.canopyRadiusX(), canopyRadiusCap(dna, personality, rarity, true)));
        int canopyRadiusZ = Math.max(TreeShapeProfile.canopyRadiusFloor(dna.species(), personality, rarity, targetHeight, false), Math.min(dna.canopyRadiusZ(), canopyRadiusCap(dna, personality, rarity, false)));
        int canopyRadius = Math.max(canopyRadiusX, canopyRadiusZ);
        int canopyRadiusY = Math.max(TreeShapeProfile.canopyVerticalRadiusFloor(dna.species(), canopyRadius), Math.min(dna.canopyRadiusY(), canopyVerticalCap(dna, canopyRadius)));
        double canopyDensity = Math.min(dna.canopyDensity(), canopyDensityCap(dna));
        int trunkWidth = Math.max(TreeShapeProfile.trunkWidthFloor(dna.species(), personality, rarity, targetHeight), Math.min(dna.trunkWidth(), trunkWidthCap(dna, personality, rarity)));
        int canopyLayerCount = Math.max(TreeShapeProfile.canopyLayerFloor(dna.species(), personality, rarity, targetHeight), Math.min(dna.canopyLayerCount(), canopyLayerCountCap(dna, personality, rarity)));
        int canopyLayerSpread = Math.max(canopyRadius, Math.min(dna.canopyLayerSpread(), canopyLayerSpreadCap(dna)));
        double branchStartRatio = Math.min(dna.branchStartRatio(), branchStartCap(dna));
        double branchRiseChance = Math.min(dna.branchRiseChance(), branchRiseCap(dna));

        boolean changed = shapeRevisionChanged
                || stage != dna.maturityStage()
                || personality != dna.personality()
                || rarity != dna.rarity()
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
                personality,
                rarity,
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
                TreeDna.CURRENT_SHAPE_REVISION,
                stageDowngraded || shapeRevisionChanged
                        ? TreeGrowthIntent.CLEANUP
                        : normalizedIntent(dna.currentIntent(), stage),
                changed ? 0 : dna.planCursor(),
                0,
                stageDowngraded ? 0 : Math.min(3, dna.blockedAttempts()),
                Math.min(dna.lastIntentChangeAge(), dna.age()),
                stageDowngraded ? 8
                        : shapeRevisionChanged
                                ? Math.max(dna.stageCleanupBurst(), transitionCleanupBurst(stage))
                                : Math.min(dna.stageCleanupBurst(), 8),
                stageDowngraded ? 6
                        : shapeRevisionChanged
                                ? Math.max(dna.stageGrowthBurst(), transitionGrowthBurst(stage))
                                : Math.min(dna.stageGrowthBurst(), 12),
                dna.age(),
                stage,
                dna.lastGrowthMillis(),
                dna.stalledUntilMillis(),
                Math.min(8, dna.damageCount()),
                dna.stumpPresent()
        );
        // ## Normalization changes the future plan, never the persisted source
        // evidence used to finish an already active canopy transition.
        normalized.copyTransitionLedgerFrom(dna);
        return new NormalizedDna(normalized, true, summary(dna, normalized));
    }

    private TreeMaturityStage normalizedStage(TreeDna dna, TreeMaturityStage maximumStage) {
        if (dna.maturityStage().ordinal() > maximumStage.ordinal()) {
            return maximumStage;
        }
        if (dna.maturityStage() == TreeMaturityStage.ANCIENT && dna.age() < 24) {
            return TreeMaturityStage.MEDIUM;
        }
        if (dna.maturityStage() == TreeMaturityStage.ANCIENT && dna.age() < 80 && dna.rarity() != TreeRarity.LANDMARK) {
            return TreeMaturityStage.MATURE;
        }
        return dna.maturityStage();
    }

    private TreePersonality normalizedPersonality(TreeDna dna, boolean ancientStageOnHold) {
        if (dna.personality() == TreePersonality.SPARSE) {
            // ## Sparse DNA produced narrow leaf columns. Keep birch slim by height,
            // while giving every migrated crown enough horizontal cloud mass.
            return dna.species() == TreeSpecies.BIRCH
                    ? TreePersonality.TALL : TreePersonality.BALANCED;
        }
        if (!ancientStageOnHold || dna.personality() != TreePersonality.ANCIENT_LANDMARK) {
            return dna.personality();
        }
        return switch (dna.species()) {
            case OAK, DARK_OAK, MANGROVE -> TreePersonality.WIDE;
            case BIRCH, JUNGLE -> TreePersonality.TALL;
            case SPRUCE, CHERRY -> TreePersonality.LAYERED;
            case ACACIA -> TreePersonality.UMBRELLA;
        };
    }

    private int transitionCleanupBurst(TreeMaturityStage stage) {
        return switch (stage) {
            case SMALL -> 2;
            case MEDIUM -> 6;
            case MATURE -> 8;
            case ANCIENT -> 10;
        };
    }

    private int transitionGrowthBurst(TreeMaturityStage stage) {
        // ## A migrated or repaired tree receives enough deterministic work to
        // refill its planned frame and fluffy crown immediately after cleanup.
        return switch (stage) {
            case SMALL -> 6;
            case MEDIUM -> 12;
            case MATURE -> 16;
            case ANCIENT -> 20;
        };
    }

    private TreeRarity normalizedRarity(TreeDna dna, boolean ancientStageOnHold) {
        return ancientStageOnHold && dna.rarity() == TreeRarity.LANDMARK ? TreeRarity.RARE : dna.rarity();
    }

    private int fancyMatureHeightCap(TreeSpecies species) {
        return switch (species) {
            case OAK -> 22;
            case BIRCH -> 26;
            case SPRUCE -> 32;
            case JUNGLE -> 40;
            case ACACIA -> 20;
            case DARK_OAK, MANGROVE -> 24;
            case CHERRY -> 22;
        };
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

    private int targetHeightCap(TreeDna dna, TreePersonality personality, TreeRarity rarity, boolean ancientStageOnHold) {
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
        if (rarity == TreeRarity.RARE) {
            cap += 8;
        } else if (rarity == TreeRarity.LANDMARK || personality == TreePersonality.ANCIENT_LANDMARK) {
            cap += 18;
        }
        return ancientStageOnHold ? Math.min(cap, fancyMatureHeightCap(dna.species())) : cap;
    }

    private int targetHeightFloor(TreeDna dna, TreeMaturityStage stage, TreePersonality personality, TreeRarity rarity) {
        int base = TreeShapeProfile.targetHeightFloor(dna.species(), personality, rarity);
        int stageFloor = switch (stage) {
            case SMALL -> Math.max(8, (int) Math.round(base * 0.42D));
            case MEDIUM -> Math.max(10, (int) Math.round(base * 0.68D));
            case MATURE -> base;
            case ANCIENT -> Math.max(base + 2, (int) Math.round(base * 1.12D));
        };
        return Math.max(base, stageFloor);
    }

    private int branchCountCap(TreeDna dna, TreePersonality personality, TreeRarity rarity) {
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
        if (rarity == TreeRarity.RARE) {
            cap += 4;
        } else if (rarity == TreeRarity.LANDMARK || personality == TreePersonality.ANCIENT_LANDMARK) {
            cap += 10;
        }
        return cap;
    }

    private int branchLengthCap(TreeDna dna, TreePersonality personality, TreeRarity rarity) {
        int cap = switch (dna.species()) {
            case BIRCH -> 4;
            case SPRUCE -> 5;
            case JUNGLE, ACACIA -> 6;
            case DARK_OAK, MANGROVE, OAK, CHERRY -> 5;
        };
        if (rarity == TreeRarity.LANDMARK || personality == TreePersonality.ANCIENT_LANDMARK) {
            cap += 2;
        }
        return cap;
    }

    private int canopyRadiusCap(TreeDna dna, TreePersonality personality, TreeRarity rarity, boolean xAxis) {
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
        if (rarity == TreeRarity.RARE) {
            cap++;
        } else if (rarity == TreeRarity.LANDMARK || personality == TreePersonality.ANCIENT_LANDMARK) {
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

    private int trunkWidthCap(TreeDna dna, TreePersonality personality, TreeRarity rarity) {
        int cap = switch (dna.species()) {
            case JUNGLE -> 4;
            case DARK_OAK -> 5;
            case MANGROVE -> 4;
            default -> 2;
        };
        if (rarity == TreeRarity.LANDMARK || personality == TreePersonality.ANCIENT_LANDMARK || personality == TreePersonality.HOLLOW) {
            cap += 2;
        }
        return Math.min(6, cap);
    }

    private int canopyLayerCountCap(TreeDna dna, TreePersonality personality, TreeRarity rarity) {
        int cap = switch (dna.species()) {
            case SPRUCE -> 4;
            case JUNGLE, DARK_OAK -> 3;
            case CHERRY -> 2;
            default -> 2;
        };
        if (rarity == TreeRarity.LANDMARK || personality == TreePersonality.ANCIENT_LANDMARK) {
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
        return "shape-revision " + before.shapeRevision() + "->" + after.shapeRevision()
                + ", personality " + before.personality() + "->" + after.personality()
                + ", target-height " + before.targetHeight() + "->" + after.targetHeight()
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
