package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.bukkit.Axis;
import org.bukkit.block.BlockFace;

final class BranchPlanner {
    private static final List<BlockFace> FACES = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    List<TreeBranchPlan> plan(TreePlan plan, TreeDna dna) {
        List<TreeBranchPlan> branches = new ArrayList<>();
        int branchCount = TreeSpeciesStageStyle.branchCount(dna);
        if (branchCount <= 0) {
            plan.setBranchPlans(branches);
            return branches;
        }

        Random random = new Random(dna.seed() ^ 0x51A7B123L);
        int visibleHeight = TreeSpeciesStageStyle.visibleHeight(dna);
        int topY = dna.baseY() + visibleHeight - 1;
        int firstBranchY = Math.min(topY, dna.baseY() + Math.max(2, (int) Math.round(visibleHeight * TreeSpeciesStageStyle.branchStartRatio(dna))));
        int usableHeight = Math.max(1, topY - firstBranchY + 1);
        boolean developedStage = dna.maturityStage().ordinal() >= TreeMaturityStage.MATURE.ordinal();
        boolean coniferLayering = dna.species() == TreeSpecies.SPRUCE && TreeSpeciesStageStyle.canopyLayerCount(dna) > 0;
        if ((developedStage && dna.hugeArchitecture()) || coniferLayering || (developedStage && TreeSpeciesStageStyle.canopyLayerCount(dna) > 0)) {
            planLayerBranches(plan, dna, branches, random, firstBranchY, visibleHeight, topY);
        }
        if (!coniferLayering) {
            planSignatureBranches(plan, dna, branches, random, firstBranchY, visibleHeight, topY);
        }
        for (int index = 0; index < branchCount; index++) {
            BlockFace face = FACES.get(Math.floorMod(dna.branchBias() + index + random.nextInt(3), FACES.size()));
            int y = firstBranchY + random.nextInt(usableHeight);
            int length = TreeSpeciesStageStyle.branchLengthForHeight(dna, y, range(random, dna.minBranchLength(), dna.maxBranchLength()), random);
            Anchor anchor = branchAnchor(dna, y, face, random);
            int x = anchor.x();
            int z = anchor.z();
            TreeBranchPlan.Builder builder = TreeBranchPlan.builder(branches.size(), face, x, y, z);
            int parentX = x;
            int parentY = y;
            int parentZ = z;
            for (int step = 1; step <= length; step++) {
                x += face.getModX();
                z += face.getModZ();
                builder.add(step, x, y, z, parentX, parentY, parentZ);
                planBranchSegment(plan, dna, face, x, y, z, step, branches.size(), parentX, parentY, parentZ, step == length);
                parentX = x;
                parentY = y;
                parentZ = z;
                if (step > 1 && random.nextDouble() < dna.branchRiseChance() && y < topY) {
                    y++;
                    builder.add(step, x, y, z, parentX, parentY, parentZ);
                    plan.add(new PlannedTreeBlock(
                            x,
                            y,
                            z,
                            dna.species().logMaterial(),
                            TreeBlockRole.BRANCH,
                            Axis.Y,
                            null
                    ).branchStep(branches.size(), step, parentX, parentY, parentZ, step == length));
                    parentY = y;
                }
            }
            TreeBranchPlan branch = builder.build();
            branches.add(branch);
            if (developedStage
                    && (TreeSpeciesStageStyle.favorsFork(dna) || dna.personality() == TreePersonality.FORKED || dna.personality() == TreePersonality.ANCIENT_LANDMARK)
                    && length >= 3
                    && random.nextInt(dna.maturityStage() == TreeMaturityStage.MATURE ? 3 : 2) == 0) {
                BlockFace forkFace = FACES.get(Math.floorMod(FACES.indexOf(face) + (random.nextBoolean() ? 1 : -1), FACES.size()));
                int forkX = x + forkFace.getModX();
                int forkZ = z + forkFace.getModZ();
                int forkY = y;
                int forkId = branches.size();
                plan.add(new PlannedTreeBlock(
                        forkX,
                        forkY,
                        forkZ,
                        dna.species().logMaterial(),
                        TreeBlockRole.BRANCH,
                        forkFace == BlockFace.EAST || forkFace == BlockFace.WEST ? Axis.X : Axis.Z,
                        null
                ).branchStep(forkId, 1, x, y, z, true));
                TreeBranchPlan.Builder fork = TreeBranchPlan.builder(forkId, forkFace, x, y, z);
                fork.add(1, forkX, forkY, forkZ, x, y, z);
                branches.add(fork.build());
            }
        }
        plan.setBranchPlans(branches);
        return branches;
    }

    private void planSignatureBranches(TreePlan plan, TreeDna dna, List<TreeBranchPlan> branches, Random random, int firstBranchY, int visibleHeight, int topY) {
        int arms = signatureArmCount(dna);
        if (arms <= 0) {
            return;
        }
        for (int index = 0; index < arms; index++) {
            BlockFace primary = FACES.get(Math.floorMod(dna.branchBias() + index, FACES.size()));
            BlockFace secondary = signatureSecondaryFace(dna, primary, index);
            int y = signatureBranchY(dna, firstBranchY, visibleHeight, topY, index, arms);
            int length = signatureBranchLength(dna, y, index, random);
            int bendAfter = signatureBendAfter(dna, length, index);
            planSignatureBranchPath(plan, dna, branches, random, primary, secondary, y, length, bendAfter);
        }
    }

    private int signatureArmCount(TreeDna dna) {
        return switch (dna.species()) {
            case OAK -> switch (dna.maturityStage()) {
                case SMALL -> 2;
                case MEDIUM -> 3;
                case MATURE -> 5;
                case ANCIENT -> 7;
            };
            case BIRCH -> switch (dna.maturityStage()) {
                case SMALL -> 1;
                case MEDIUM -> 2;
                case MATURE -> 3;
                case ANCIENT -> 4;
            };
            case JUNGLE -> switch (dna.maturityStage()) {
                case SMALL -> 2;
                case MEDIUM -> 3;
                case MATURE -> 6;
                case ANCIENT -> 8;
            };
            case DARK_OAK -> switch (dna.maturityStage()) {
                case SMALL -> 2;
                case MEDIUM -> 4;
                case MATURE -> 6;
                case ANCIENT -> 8;
            };
            case ACACIA -> switch (dna.maturityStage()) {
                case SMALL -> 2;
                case MEDIUM -> 3;
                case MATURE -> 4;
                case ANCIENT -> 5;
            };
            case CHERRY -> switch (dna.maturityStage()) {
                case SMALL -> 2;
                case MEDIUM -> 4;
                case MATURE -> 5;
                case ANCIENT -> 6;
            };
            case MANGROVE -> switch (dna.maturityStage()) {
                case SMALL -> 2;
                case MEDIUM -> 4;
                case MATURE -> 6;
                case ANCIENT -> 7;
            };
            case SPRUCE -> 0;
        };
    }

    private int signatureBranchY(TreeDna dna, int firstBranchY, int visibleHeight, int topY, int index, int arms) {
        double low = switch (dna.species()) {
            case DARK_OAK, MANGROVE -> 0.34D;
            case ACACIA, CHERRY, OAK -> 0.46D;
            case BIRCH -> 0.62D;
            case JUNGLE -> 0.56D;
            case SPRUCE -> 0.30D;
        };
        double span = switch (dna.species()) {
            case BIRCH -> 0.22D;
            case ACACIA -> 0.30D;
            case JUNGLE -> 0.34D;
            case DARK_OAK, MANGROVE -> 0.36D;
            default -> 0.32D;
        };
        if (dna.maturityStage() == TreeMaturityStage.SMALL) {
            low += 0.10D;
            span *= 0.55D;
        } else if (dna.maturityStage() == TreeMaturityStage.ANCIENT) {
            low -= 0.04D;
            span += 0.08D;
        }
        double wave = arms <= 1 ? 0.0D : (index % arms) / (double) Math.max(1, arms - 1);
        int y = dna.baseY() + (int) Math.round(visibleHeight * (low + (span * wave)));
        return Math.min(topY, Math.max(firstBranchY, y));
    }

    private int signatureBranchLength(TreeDna dna, int y, int index, Random random) {
        int stageBase = switch (dna.maturityStage()) {
            case SMALL -> 2;
            case MEDIUM -> 3;
            case MATURE -> 4;
            case ANCIENT -> 6;
        };
        int speciesBonus = switch (dna.species()) {
            case ACACIA, JUNGLE -> 2;
            case DARK_OAK, CHERRY, MANGROVE -> 1;
            case BIRCH, SPRUCE -> -1;
            case OAK -> 0;
        };
        int silhouette = Math.max(stageBase, stageBase + speciesBonus + (index % 2));
        int rolled = TreeSpeciesStageStyle.branchLengthForHeight(dna, y, range(random, dna.minBranchLength(), dna.maxBranchLength()), random);
        int cap = Math.max(2, Math.max(TreeSpeciesStageStyle.canopyRadiusX(dna), TreeSpeciesStageStyle.canopyRadiusZ(dna)) + 1);
        if (dna.species() == TreeSpecies.BIRCH) {
            cap = Math.min(cap, dna.maturityStage().ordinal() >= TreeMaturityStage.MATURE.ordinal() ? 4 : 2);
        }
        return Math.max(1, Math.min(cap, Math.max(rolled, silhouette)));
    }

    private int signatureBendAfter(TreeDna dna, int length, int index) {
        if (length <= 2 || dna.species() == TreeSpecies.BIRCH) {
            return length + 1;
        }
        if (dna.species() == TreeSpecies.ACACIA || dna.species() == TreeSpecies.OAK || dna.species() == TreeSpecies.CHERRY) {
            return Math.max(1, length / 2);
        }
        return index % 2 == 0 ? Math.max(2, length - 2) : length + 1;
    }

    private BlockFace signatureSecondaryFace(TreeDna dna, BlockFace primary, int index) {
        if (dna.species() == TreeSpecies.BIRCH || index % 3 == 0) {
            return primary;
        }
        int direction = index % 2 == 0 ? 1 : -1;
        return FACES.get(Math.floorMod(FACES.indexOf(primary) + direction, FACES.size()));
    }

    private void planSignatureBranchPath(
            TreePlan plan,
            TreeDna dna,
            List<TreeBranchPlan> branches,
            Random random,
            BlockFace primary,
            BlockFace secondary,
            int y,
            int length,
            int bendAfter
    ) {
        int branchId = branches.size();
        Anchor anchor = branchAnchor(dna, y, primary, random);
        int x = anchor.x();
        int z = anchor.z();
        int branchY = y;
        int parentX = x;
        int parentY = branchY;
        int parentZ = z;
        TreeBranchPlan.Builder builder = TreeBranchPlan.builder(branchId, primary, x, branchY, z);
        for (int step = 1; step <= length; step++) {
            BlockFace face = step > bendAfter ? secondary : primary;
            x += face.getModX();
            z += face.getModZ();
            boolean rising = step > 1
                    && shouldRiseSignatureBranch(dna, step, length);
            if (rising) {
                // ## A branch rise is two face-connected moves. A diagonal jump
                // looks like floating wood even when its logical parent is nearby.
                builder.add(step, x, branchY, z,
                        parentX, parentY, parentZ);
                planBranchSegment(
                        plan, dna, face, x, branchY, z, step, branchId,
                        parentX, parentY, parentZ, false);
                parentX = x;
                parentY = branchY;
                parentZ = z;
                branchY++;
            }
            builder.add(step, x, branchY, z, parentX, parentY, parentZ);
            if (rising) {
                plan.add(new PlannedTreeBlock(
                        x,
                        branchY,
                        z,
                        dna.species().logMaterial(),
                        TreeBlockRole.BRANCH,
                        Axis.Y,
                        null
                ).branchStep(
                        branchId, step, parentX, parentY, parentZ,
                        step == length));
            } else {
                planBranchSegment(
                        plan, dna, face, x, branchY, z, step, branchId,
                        parentX, parentY, parentZ, step == length);
            }
            parentX = x;
            parentY = branchY;
            parentZ = z;
        }
        branches.add(builder.build());
    }

    private boolean shouldRiseSignatureBranch(TreeDna dna, int step, int length) {
        return switch (dna.species()) {
            case ACACIA -> step == Math.max(2, length / 2) || step == length;
            case JUNGLE -> step >= Math.max(3, length - 2) && step % 2 == 0;
            case OAK, CHERRY -> step == length && dna.maturityStage().ordinal() >= TreeMaturityStage.MEDIUM.ordinal();
            case DARK_OAK, MANGROVE -> false;
            case BIRCH -> step == length && dna.maturityStage().ordinal() >= TreeMaturityStage.MATURE.ordinal();
            case SPRUCE -> false;
        };
    }
    private void planLayerBranches(TreePlan plan, TreeDna dna, List<TreeBranchPlan> branches, Random random, int firstBranchY, int visibleHeight, int topY) {
        int layers = Math.max(1, TreeSpeciesStageStyle.canopyLayerCount(dna));
        for (int layer = 0; layer < layers; layer++) {
            double start = dna.species() == TreeSpecies.SPRUCE ? 0.28D : 0.36D;
            double progress = start + ((layer + 1.0D) / (layers + 1.0D)) * (dna.species() == TreeSpecies.SPRUCE ? 0.54D : 0.46D);
            int y = Math.min(topY, Math.max(firstBranchY, dna.baseY() + (int) Math.round(visibleHeight * progress)));
            int layerLength = Math.max(dna.maxBranchLength() + 1, TreeSpeciesStageStyle.canopyLayerSpread(dna) - Math.max(0, layer - 1));
            int arms = dna.rarity() == TreeRarity.LANDMARK || dna.personality() == TreePersonality.ANCIENT_LANDMARK ? 4 : 2 + random.nextInt(3);
            for (int arm = 0; arm < arms; arm++) {
                BlockFace face = FACES.get(Math.floorMod(dna.branchBias() + arm + layer, FACES.size()));
                int branchY = y;
                Anchor anchor = branchAnchor(dna, branchY, face, random);
                int x = anchor.x();
                int z = anchor.z();
                int length = TreeSpeciesStageStyle.branchLengthForHeight(dna, branchY, Math.max(1, layerLength - random.nextInt(3)), random);
                int branchId = branches.size();
                TreeBranchPlan.Builder builder = TreeBranchPlan.builder(branchId, face, x, branchY, z);
                int parentX = x;
                int parentY = branchY;
                int parentZ = z;
                for (int step = 1; step <= length; step++) {
                    x += face.getModX();
                    z += face.getModZ();
                    builder.add(step, x, branchY, z, parentX, parentY, parentZ);
                    planBranchSegment(plan, dna, face, x, branchY, z, step, branchId, parentX, parentY, parentZ, step == length);
                    parentX = x;
                    parentY = branchY;
                    parentZ = z;
                    if (step > 2 && step % 3 == 0 && random.nextBoolean()) {
                        branchY = Math.min(branchY + 1, topY);
                        builder.add(step, x, branchY, z, parentX, parentY, parentZ);
                        plan.add(new PlannedTreeBlock(
                                x,
                                branchY,
                                z,
                                dna.species().logMaterial(),
                                TreeBlockRole.BRANCH,
                                Axis.Y,
                                null
                        ).branchStep(branchId, step, parentX, parentY, parentZ, step == length));
                        parentY = branchY;
                    }
                }
                branches.add(builder.build());
            }
        }
    }

    private void planBranchSegment(TreePlan plan, TreeDna dna, BlockFace face, int x, int y, int z, int step, int branchId, int parentX, int parentY, int parentZ, boolean tip) {
        Axis axis = face == BlockFace.EAST || face == BlockFace.WEST ? Axis.X : Axis.Z;
        plan.add(new PlannedTreeBlock(
                x,
                y,
                z,
                dna.species().logMaterial(),
                TreeBlockRole.BRANCH,
                axis,
                null
        ).branchStep(branchId, step, parentX, parentY, parentZ, tip));
        if (dna.maturityStage().ordinal() < TreeMaturityStage.MATURE.ordinal()) {
            return;
        }
        int reinforcedSteps = reinforcedBranchSteps(dna);
        if (!dna.hugeArchitecture() || step > reinforcedSteps) {
            return;
        }
        BlockFace side = axis == Axis.X ? BlockFace.NORTH : BlockFace.EAST;
        plan.add(new PlannedTreeBlock(
                x + side.getModX(),
                y,
                z + side.getModZ(),
                dna.species().logMaterial(),
                TreeBlockRole.BRANCH,
                axis,
                null
        ).branchStep(branchId, step, x, y, z, false));
        if (dna.trunkWidth() >= 4 && step <= Math.max(2, reinforcedSteps - 1)) {
            plan.add(new PlannedTreeBlock(
                    x - side.getModX(),
                    y,
                    z - side.getModZ(),
                    dna.species().logMaterial(),
                    TreeBlockRole.BRANCH,
                    axis,
                    null
            ).branchStep(branchId, step, x, y, z, false));
        }
        if (dna.trunkWidth() >= 5 && step <= 2) {
            plan.add(new PlannedTreeBlock(
                    x,
                    Math.max(dna.baseY(), y - 1),
                    z,
                    dna.species().logMaterial(),
                    TreeBlockRole.BRANCH,
                    axis,
                    null
            ).branchStep(branchId, step, x, y, z, false));
        }
    }

    private int reinforcedBranchSteps(TreeDna dna) {
        int base = Math.max(2, dna.trunkWidth() - 1);
        return switch (dna.species()) {
            case JUNGLE, DARK_OAK, OAK -> base + 1;
            case CHERRY, ACACIA, MANGROVE -> base;
            case SPRUCE -> Math.max(2, base - 1);
            case BIRCH -> 1;
        };
    }

    private Anchor branchAnchor(TreeDna dna, int y, BlockFace face, Random random) {
        int width = TreeSpeciesStageStyle.trunkWidthAt(dna, y);
        if (dna.maturityStage().ordinal() < TreeMaturityStage.MATURE.ordinal()) {
            if (face.getModX() > 0 && width == 2) {
                return new Anchor(dna.trunkXAt(y) + 1, dna.trunkZAt(y));
            }
            if (face.getModZ() > 0 && width == 2) {
                return new Anchor(dna.trunkXAt(y), dna.trunkZAt(y) + 1);
            }
            return new Anchor(dna.trunkXAt(y), dna.trunkZAt(y));
        }
        int bestX = dna.trunkXAt(y);
        int bestZ = dna.trunkZAt(y);
        int bestScore = Integer.MIN_VALUE;
        for (int ox : TrunkPlanner.offsets(width)) {
            for (int oz : TrunkPlanner.offsets(width)) {
                if (!TrunkPlanner.isTrunkCell(dna, y, ox, oz, width)) {
                    continue;
                }
                int outward = (ox * face.getModX()) + (oz * face.getModZ());
                int perpendicular = face.getModX() == 0 ? -Math.abs(ox) : -Math.abs(oz);
                int jitter = width >= 4 ? random.nextInt(3) : 0;
                int score = (outward * 100) + (perpendicular * 8) + jitter;
                if (score > bestScore) {
                    bestScore = score;
                    bestX = dna.trunkXAt(y) + ox;
                    bestZ = dna.trunkZAt(y) + oz;
                }
            }
        }
        return new Anchor(bestX, bestZ);
    }

    private static int range(Random random, int min, int max) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        return low + random.nextInt(high - low + 1);
    }

    private record Anchor(int x, int z) {
    }

}
