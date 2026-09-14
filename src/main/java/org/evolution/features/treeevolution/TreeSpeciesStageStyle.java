package org.evolution.features.treeevolution;

import java.util.Random;

final class TreeSpeciesStageStyle {
    private TreeSpeciesStageStyle() {
    }

    static int visibleHeight(TreeDna dna) {
        double scale = switch (dna.maturityStage()) {
            case SMALL -> 0.34D;
            case MEDIUM -> 0.58D;
            case MATURE -> 1.0D;
            case ANCIENT -> 1.12D;
        };
        if (dna.species() == TreeSpecies.BIRCH || dna.species() == TreeSpecies.SPRUCE || dna.species() == TreeSpecies.JUNGLE) {
            scale += dna.maturityStage() == TreeMaturityStage.SMALL ? 0.04D : 0.0D;
        }
        if (dna.species() == TreeSpecies.DARK_OAK || dna.species() == TreeSpecies.CHERRY) {
            scale -= dna.maturityStage() == TreeMaturityStage.SMALL ? 0.06D : 0.0D;
        }
        int targetHeight = Math.max(dna.targetHeight(), stageTargetHeightFloor(dna));
        int height = (int) Math.round(targetHeight * scale);
        int stageCap = stageHeightCap(dna);
        return Math.max(stageVisibleHeightFloor(dna), Math.min(Math.min(targetHeight + ancientBonus(dna), stageCap), height));
    }

    static int trunkWidthAt(TreeDna dna, int y) {
        int width = dna.trunkWidthAt(y);
        int cap = TreeVariantPolicy.stageTrunkWidthCap(dna);
        int floor = TreeVariantPolicy.stageTrunkWidthFloor(dna);
        return Math.max(1, Math.max(
                Math.min(floor, cap), Math.min(width, cap)));
    }

    static int branchCount(TreeDna dna) {
        double scale = switch (dna.maturityStage()) {
            case SMALL -> 0.34D;
            case MEDIUM -> 0.62D;
            case MATURE -> 1.0D;
            case ANCIENT -> 1.22D;
        };
        scale *= switch (dna.species()) {
            case BIRCH -> 0.72D;
            case SPRUCE -> 1.15D;
            case JUNGLE -> 1.18D;
            case ACACIA -> 1.12D;
            case DARK_OAK -> 1.10D;
            case MANGROVE -> 1.16D;
            case CHERRY -> 1.08D;
            case OAK -> 1.0D;
        };
        scale *= TreeVariantPolicy.branchLengthScale(dna);
        if (dna.personality() == TreePersonality.SPARSE) {
            scale *= 0.78D;
        } else if (dna.personality() == TreePersonality.WIDE
                || dna.personality() == TreePersonality.FORKED
                || dna.personality() == TreePersonality.ANCIENT_LANDMARK) {
            scale *= 1.15D;
        }
        int planned = (int) Math.round(dna.branchCount() * scale);
        planned = Math.min(planned, earlyFancyBranchCap(dna));
        int result = Math.max(0, Math.min(
                branchCap(dna),
                Math.min(dna.branchCount() + ancientBonus(dna), planned)));
        if (dna.species() == TreeSpecies.ACACIA) {
            // ## Acacia's identity comes from a few supported high forks. Extra
            // generic branches turn those forks into a flat wooden scaffold.
            result = Math.min(
                    result,
                    AcaciaArchitecturePolicy.forkCount(dna));
        }
        return result;
    }

    static int branchLength(TreeDna dna, int rolledLength, Random random) {
        double scale = switch (dna.maturityStage()) {
            case SMALL -> 0.42D;
            case MEDIUM -> 0.68D;
            case MATURE -> 1.0D;
            case ANCIENT -> 1.18D;
        };
        scale *= switch (dna.species()) {
            case BIRCH -> 0.72D;
            case SPRUCE -> 0.82D;
            case JUNGLE -> 1.28D;
            case ACACIA -> 1.35D;
            case DARK_OAK -> 1.06D;
            case MANGROVE -> 1.12D;
            case CHERRY -> 1.16D;
            case OAK -> 1.0D;
        };
        if (dna.personality() == TreePersonality.UMBRELLA || dna.personality() == TreePersonality.WIDE) {
            scale += 0.18D;
        }
        int jitter = dna.maturityStage() == TreeMaturityStage.ANCIENT && random.nextBoolean() ? 1 : 0;
        return Math.max(1, (int) Math.round(rolledLength * scale) + jitter);
    }

    static int branchLengthForHeight(TreeDna dna, int anchorY, int rolledLength, Random random) {
        int length = branchLength(dna, rolledLength, random);
        int visibleHeight = Math.max(4, visibleHeight(dna));
        int anchorHeight = Math.max(1, anchorY - dna.baseY() + 1);
        double heightProgress = Math.min(1.0D, anchorHeight / (double) visibleHeight);
        int stageCap = switch (dna.maturityStage()) {
            case SMALL -> Math.max(2, (int) Math.round(visibleHeight * 0.20D));
            case MEDIUM -> Math.max(3, (int) Math.round(visibleHeight * 0.30D));
            case MATURE -> Math.max(3, (int) Math.round(visibleHeight * 0.34D));
            case ANCIENT -> Math.max(4, (int) Math.round(visibleHeight * 0.42D));
        };
        int heightCap = Math.max(1, (int) Math.round(stageCap * (0.55D + (heightProgress * 0.45D))));
        int speciesBonus = switch (dna.species()) {
            case ACACIA, JUNGLE -> dna.maturityStage().ordinal() >= TreeMaturityStage.MEDIUM.ordinal() ? 1 : 0;
            case DARK_OAK, CHERRY, MANGROVE -> dna.maturityStage().ordinal() >= TreeMaturityStage.MATURE.ordinal() ? 1 : 0;
            default -> 0;
        };
        int shapeCap = Math.max(1, Math.max(canopyRadiusX(dna), canopyRadiusZ(dna)) + speciesBonus);
        if (dna.species() == TreeSpecies.SPRUCE) {
            // ## Conifer limbs establish the lower crown and shorten toward
            // the leader. The generic increasing height cap produced several
            // equally wide crown masses stacked up the trunk.
            double crownProgress = Math.max(
                    0.0D, (heightProgress - 0.22D) / 0.78D);
            int coniferCap = Math.max(1, (int) Math.ceil(
                    shapeCap * (1.0D - 0.72D * crownProgress)));
            return Math.max(1, Math.min(length, coniferCap));
        }
        return Math.max(1, Math.min(length, Math.min(heightCap + speciesBonus, shapeCap)));
    }

    static double branchStartRatio(TreeDna dna) {
        double ratio = dna.branchStartRatio();
        if (dna.species() == TreeSpecies.ACACIA) {
            // ## Vanilla acacia reads as a high fork with an umbrella crown.
            // Keep young stages clear-trunked; older stages may broaden lower.
            double floor = switch (dna.maturityStage()) {
                case SMALL -> 0.68D;
                case MEDIUM -> 0.62D;
                case MATURE -> 0.54D;
                case ANCIENT -> 0.48D;
            };
            return clamp(
                    Math.max(floor, dna.branchStartRatio()
                            + TreeVariantPolicy.branchStartAdjustment(dna)),
                    floor, 0.76D);
        }
        ratio += switch (dna.species()) {
            case BIRCH -> 0.08D;
            case JUNGLE -> 0.10D;
            case SPRUCE -> -0.10D;
            case ACACIA -> -0.16D;
            case DARK_OAK -> -0.16D;
            case MANGROVE -> -0.14D;
            case CHERRY -> -0.08D;
            case OAK -> -0.04D;
        };
        ratio += switch (dna.maturityStage()) {
            case SMALL -> 0.16D;
            case MEDIUM -> 0.08D;
            case MATURE -> -0.05D;
            case ANCIENT -> -0.10D;
        };
        if (dna.personality() == TreePersonality.UMBRELLA || dna.personality() == TreePersonality.WIDE) {
            ratio -= 0.06D;
        }
        ratio += TreeVariantPolicy.branchStartAdjustment(dna);
        return clamp(ratio, 0.24D, dna.species() == TreeSpecies.BIRCH ? 0.72D : 0.62D);
    }

    static int canopyRadiusX(TreeDna dna) {
        return horizontalRadius(dna, dna.canopyRadiusX(), true);
    }

    static int canopyRadiusZ(TreeDna dna) {
        return horizontalRadius(dna, dna.canopyRadiusZ(), false);
    }

    static int canopyRadiusY(TreeDna dna) {
        double scale = switch (dna.maturityStage()) {
            case SMALL -> 0.70D;
            case MEDIUM -> 0.82D;
            case MATURE -> 1.0D;
            case ANCIENT -> 1.08D;
        };
        if (dna.species() == TreeSpecies.SPRUCE) {
            scale += 0.22D;
        } else if (dna.species() == TreeSpecies.CHERRY) {
            scale -= 0.28D;
        } else if (dna.species() == TreeSpecies.ACACIA) {
            scale -= 0.18D;
        }
        int radius = (int) Math.round(dna.canopyRadiusY() * scale);
        return Math.max(1, Math.min(6, radius));
    }

    static int canopyLayerCount(TreeDna dna) {
        int base = dna.canopyLayerCount();
        if (dna.maturityStage().ordinal()
                < TreeMaturityStage.MATURE.ordinal()
                && !TreeVariantPolicy.allowsEarlyLayers(dna)) {
            return 0;
        }
        if (dna.species() == TreeSpecies.SPRUCE) {
            base = Math.max(base, dna.maturityStage() == TreeMaturityStage.SMALL ? 1 : 2);
        } else if (dna.species() == TreeSpecies.CHERRY) {
            base = Math.max(base, dna.maturityStage() == TreeMaturityStage.SMALL ? 1 : 2);
            base = Math.min(base, dna.maturityStage() == TreeMaturityStage.ANCIENT ? 3 : 2);
        } else if (dna.species() == TreeSpecies.JUNGLE && dna.maturityStage().ordinal() >= TreeMaturityStage.MATURE.ordinal()) {
            base = Math.max(base, 1);
        }
        int cap = switch (dna.maturityStage()) {
            case SMALL -> 1;
            case MEDIUM -> 2;
            case MATURE -> Math.max(2, base);
            case ANCIENT -> Math.max(3, base + 1);
        };
        return Math.max(0, Math.min(cap, base + (dna.maturityStage() == TreeMaturityStage.ANCIENT ? 1 : 0)));
    }

    static int canopyLayerSpread(TreeDna dna) {
        int spread = Math.max(dna.canopyLayerSpread(), Math.max(canopyRadiusX(dna), canopyRadiusZ(dna)));
        if (dna.species() == TreeSpecies.SPRUCE) {
            spread = Math.max(2, spread - 1);
        }
        if (dna.species() == TreeSpecies.ACACIA) {
            spread++;
        }
        if (dna.species() == TreeSpecies.CHERRY) {
            spread = Math.max(2, spread - 1);
        }
        if (dna.maturityStage() == TreeMaturityStage.SMALL) {
            spread = Math.max(2, spread - 2);
        } else if (dna.maturityStage() == TreeMaturityStage.MEDIUM) {
            spread = Math.max(2, spread - 1);
        } else if (dna.maturityStage() == TreeMaturityStage.ANCIENT) {
            spread += 2;
        }
        return Math.max(1, Math.min(12, spread));
    }

    static double canopyDensity(TreeDna dna) {
        double density = dna.canopyDensity();
        density += switch (dna.species()) {
            case DARK_OAK -> 0.04D;
            case CHERRY -> -0.08D;
            case SPRUCE -> -0.03D;
            case ACACIA -> -0.07D;
            case BIRCH -> -0.04D;
            default -> 0.0D;
        };
        density += switch (dna.maturityStage()) {
            case SMALL -> -0.02D;
            case MEDIUM -> 0.02D;
            case MATURE -> 0.04D;
            case ANCIENT -> 0.03D;
        };
        return clamp(density, 0.38D, 0.92D);
    }

    static boolean favorsFork(TreeDna dna) {
        return dna.species() == TreeSpecies.OAK
                || dna.species() == TreeSpecies.JUNGLE
                || dna.species() == TreeSpecies.ACACIA
                || dna.personality() == TreePersonality.FORKED
                || dna.personality() == TreePersonality.ANCIENT_LANDMARK;
    }

    private static int horizontalRadius(TreeDna dna, int baseRadius, boolean xAxis) {
        double scale = switch (dna.maturityStage()) {
            case SMALL -> 0.62D;
            case MEDIUM -> 0.82D;
            case MATURE -> 1.0D;
            case ANCIENT -> 1.18D;
        };
        scale *= switch (dna.species()) {
            case BIRCH -> 0.78D;
            case SPRUCE -> 0.82D;
            case JUNGLE -> 1.16D;
            case ACACIA -> xAxis ? 1.35D : 1.18D;
            case DARK_OAK -> 1.20D;
            case MANGROVE -> 1.08D;
            case CHERRY -> 0.92D;
            case OAK -> 1.0D;
        };
        scale *= TreeVariantPolicy.horizontalScale(dna);
        if (dna.personality() == TreePersonality.UMBRELLA || dna.personality() == TreePersonality.WIDE) {
            scale += 0.18D;
        }
        int radius = (int) Math.round(baseRadius * scale);
        double stageFloorScale = switch (dna.maturityStage()) {
            case SMALL -> 0.58D;
            case MEDIUM -> 0.80D;
            case MATURE -> 1.0D;
            case ANCIENT -> 1.10D;
        };
        int floor = Math.max(1, (int) Math.round(
                TreeVariantPolicy.canopyRadiusFloor(dna, xAxis)
                        * stageFloorScale));
        int cap = switch (dna.species()) {
            case BIRCH -> switch (dna.maturityStage()) {
                case SMALL -> 2;
                case MEDIUM -> 3;
                case MATURE -> 3;
                case ANCIENT -> 4;
            };
            case SPRUCE -> switch (dna.maturityStage()) {
                case SMALL -> 4;
                case MEDIUM -> 5;
                case MATURE -> 6;
                case ANCIENT -> 7;
            };
            case JUNGLE -> switch (dna.maturityStage()) {
                case SMALL -> 5;
                case MEDIUM -> 7;
                case MATURE -> 8;
                case ANCIENT -> 9;
            };
            case DARK_OAK -> switch (dna.maturityStage()) {
                case SMALL -> 4;
                case MEDIUM -> 6;
                case MATURE -> 7;
                case ANCIENT -> 8;
            };
            case CHERRY -> switch (dna.maturityStage()) {
                case SMALL -> 3;
                case MEDIUM -> 4;
                case MATURE -> 5;
                case ANCIENT -> 6;
            };
            case ACACIA -> switch (dna.maturityStage()) {
                case SMALL -> 4;
                case MEDIUM -> 5;
                case MATURE -> 6;
                case ANCIENT -> 7;
            };
            case MANGROVE -> switch (dna.maturityStage()) {
                case SMALL -> 4;
                case MEDIUM -> 5;
                case MATURE -> 6;
                case ANCIENT -> 7;
            };
            case OAK -> switch (dna.maturityStage()) {
                case SMALL -> 4;
                case MEDIUM -> 6;
                case MATURE -> 7;
                case ANCIENT -> 8;
            };
        };
        cap = Math.max(cap, floor);
        return Math.max(floor, Math.min(cap, radius));
    }

    private static int ancientBonus(TreeDna dna) {
        // ## Ancient growth must remain visibly monotonic even when the
        // integrity pass retires a weaker mature-stage branch.
        return dna.maturityStage() == TreeMaturityStage.ANCIENT
                ? Math.max(3, dna.targetHeight() / 10) : 0;
    }

    private static int stageTargetHeightFloor(TreeDna dna) {
        int base = TreeVariantPolicy.targetHeightFloor(
                dna, dna.personality(), dna.rarity());
        return switch (dna.maturityStage()) {
            case SMALL -> Math.max(8, (int) Math.round(base * 0.42D));
            case MEDIUM -> Math.max(10, (int) Math.round(base * 0.68D));
            case MATURE -> base;
            case ANCIENT -> Math.max(base, base + Math.max(2, base / 8));
        };
    }

    private static int stageVisibleHeightFloor(TreeDna dna) {
        return TreeVariantPolicy.stageVisibleHeightFloor(dna);
    }

    private static int earlyFancyBranchCap(TreeDna dna) {
        return TreeVariantPolicy.earlyBranchCap(dna);
    }

    private static int branchCap(TreeDna dna) {
        return switch (dna.species()) {
            case BIRCH -> switch (dna.maturityStage()) {
                case SMALL -> 2;
                case MEDIUM -> 4;
                case MATURE -> 6;
                case ANCIENT -> 10;
            };
            case CHERRY -> switch (dna.maturityStage()) {
                case SMALL -> 5;
                case MEDIUM -> 8;
                case MATURE -> 12;
                case ANCIENT -> 18;
            };
            case ACACIA -> switch (dna.maturityStage()) {
                case SMALL -> 5;
                case MEDIUM -> 8;
                case MATURE -> 12;
                case ANCIENT -> 18;
            };
            case JUNGLE -> switch (dna.maturityStage()) {
                case SMALL -> 8;
                case MEDIUM -> 14;
                case MATURE -> 28;
                case ANCIENT -> 42;
            };
            case SPRUCE -> switch (dna.maturityStage()) {
                case SMALL -> 10;
                case MEDIUM -> 18;
                case MATURE -> 28;
                case ANCIENT -> 42;
            };
            default -> switch (dna.maturityStage()) {
                case SMALL -> 6;
                case MEDIUM -> 10;
                case MATURE -> 16;
                case ANCIENT -> 24;
            };
        };
    }

    private static int stageHeightCap(TreeDna dna) {
        if (dna.maturityStage() == TreeMaturityStage.MATURE) {
            return Math.max(dna.targetHeight(), stageVisibleHeightFloor(dna));
        }
        if (dna.maturityStage() == TreeMaturityStage.ANCIENT) {
            return Math.max(dna.targetHeight() + ancientBonus(dna), stageVisibleHeightFloor(dna));
        }

        boolean giant = dna.rarity() == TreeRarity.LANDMARK
                || dna.personality() == TreePersonality.ANCIENT_LANDMARK
                || dna.targetHeight() >= 32
                || dna.variant() == TreeVariant.JUNGLE_MEGA
                || dna.variant() == TreeVariant.SPRUCE_MEGA
                || dna.variant() == TreeVariant.SPRUCE_MEGA_PINE;
        if (dna.maturityStage() == TreeMaturityStage.SMALL) {
            return switch (dna.species()) {
                case JUNGLE, SPRUCE, MANGROVE -> giant ? 18 : 14;
                case DARK_OAK -> 12;
                case BIRCH, CHERRY, ACACIA, OAK -> giant ? 14 : 11;
            };
        }
        return switch (dna.species()) {
            case JUNGLE -> giant ? 34 : 22;
            case SPRUCE, MANGROVE -> giant ? 28 : 20;
            case DARK_OAK -> giant ? 20 : 16;
            case BIRCH, CHERRY, ACACIA, OAK -> giant ? 22 : 18;
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
