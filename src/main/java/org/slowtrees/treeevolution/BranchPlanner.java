package org.slowtrees.treeevolution;

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
