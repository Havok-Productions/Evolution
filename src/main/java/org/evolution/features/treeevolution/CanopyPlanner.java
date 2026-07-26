package org.evolution.features.treeevolution;

import java.util.List;
import java.util.Random;
import org.bukkit.Axis;

final class CanopyPlanner {
    private static final int[][] LOWER_FRINGE_OFFSETS = {
            {1, 0}, {0, 1}, {-1, 0}, {0, -1},
            {1, 1}, {-1, 1}, {-1, -1}, {1, -1}
    };
    private static final int[][] TIP_CONTACT_OFFSETS = {
            {0, 1, 0},
            {1, 0, 0},
            {0, 0, 1},
            {-1, 0, 0},
            {0, 0, -1},
            {0, -1, 0}
    };

    void plan(TreePlan plan, TreeDna dna, List<TreeBranchPlan> branchPlans) {
        Random random = new Random(dna.seed() ^ 0xC0A0BEEFL);
        int topY = dna.baseY() + TreeSpeciesStageStyle.visibleHeight(dna) - 1;
        List<TreeBranchPlan.BranchTip> branchTips = branchPlans.stream().map(TreeBranchPlan::tip).toList();
        plan.setBranchEnvelopeCleanupTips(branchTips);
        if (usesFancyEarlyCrown(dna)) {
            planFancyEarlyCrown(plan, dna, branchTips, random, topY);
            planNaturalTipAnchors(plan, dna, branchTips);
            pruneBranchesWithoutEnvelope(plan, dna, branchPlans);
            return;
        }
        planLeafBlob(plan, dna, dna.trunkXAt(topY), topY, dna.trunkZAt(topY),
                TreeSpeciesStageStyle.canopyRadiusX(dna),
                TreeSpeciesStageStyle.canopyRadiusY(dna),
                TreeSpeciesStageStyle.canopyRadiusZ(dna),
                random);
        if (TreeSpeciesStageStyle.canopyLayerCount(dna) > 0) {
            planLayeredCanopy(plan, dna, random);
        }
        for (TreeBranchPlan.BranchTip tip : branchTips) {
            int tipRadiusX = Math.max(1, TreeSpeciesStageStyle.canopyRadiusX(dna) - random.nextInt(2));
            int tipRadiusY = Math.max(1, TreeSpeciesStageStyle.canopyRadiusY(dna) - random.nextInt(2));
            int tipRadiusZ = Math.max(1, TreeSpeciesStageStyle.canopyRadiusZ(dna) - random.nextInt(2));
            int tipCap = branchTipCanopyCap(dna);
            tipRadiusX = Math.min(tipRadiusX, tipCap);
            tipRadiusZ = Math.min(tipRadiusZ, tipCap);
            tipRadiusY = Math.min(tipRadiusY, branchTipVerticalCap(dna));
            if (dna.species() == TreeSpecies.CHERRY) {
                int cap = dna.maturityStage() == TreeMaturityStage.ANCIENT ? 3 : 2;
                tipRadiusX = Math.min(tipRadiusX, cap);
                tipRadiusY = 1;
                tipRadiusZ = Math.min(tipRadiusZ, cap);
            }
            if (random.nextBoolean()) {
                tipRadiusX = Math.max(1, tipRadiusX - 1);
            } else {
                tipRadiusZ = Math.max(1, tipRadiusZ - 1);
            }
            planLeafBlob(plan, dna, tip.x(), tip.y(), tip.z(), tipRadiusX, tipRadiusY, tipRadiusZ, random);
        }
        planNaturalTipAnchors(plan, dna, branchTips);
        pruneBranchesWithoutEnvelope(plan, dna, branchPlans);
    }

    // ## Impossible branch candidates are removed instead of becoming bare limbs or permanent retries.
    private void pruneBranchesWithoutEnvelope(
            TreePlan plan, TreeDna dna, List<TreeBranchPlan> branchPlans) {
        List<Integer> invalidBranchIds = branchPlans.stream()
                .filter(branch -> {
                    TreeBranchPlan.BranchTip tip = branch.tip();
                    return !TreeBranchTipIntegrityPolicy.hasPreplannedEnvelope(
                            dna, tip.x(), tip.y(), tip.z(), plan.blocksByKey());
                })
                .map(TreeBranchPlan::id)
                .toList();
        if (invalidBranchIds.isEmpty()) {
            return;
        }
        for (int branchId : invalidBranchIds) {
            plan.removeBranch(branchId);
        }
        plan.recordPrunedBranches(invalidBranchIds.size());
        branchPlans.removeIf(branch -> invalidBranchIds.contains(branch.id()));
        plan.setBranchPlans(branchPlans);
    }

    // ## Preserve the species canopy shape; add only the single bridge needed to attach it to a limb.
    private void planNaturalTipAnchors(
            TreePlan plan, TreeDna dna, List<TreeBranchPlan.BranchTip> branchTips) {
        for (TreeBranchPlan.BranchTip tip : branchTips) {
            if (plannedLeafContacts(plan, dna, tip) > 0) {
                continue;
            }
            int offset = Math.floorMod(
                    Long.hashCode(dna.seed()) ^ (tip.branchId() * 31),
                    TIP_CONTACT_OFFSETS.length);
            for (int index = 0; index < TIP_CONTACT_OFFSETS.length; index++) {
                int[] contact = TIP_CONTACT_OFFSETS[
                        (offset + index) % TIP_CONTACT_OFFSETS.length];
                if (touchesPlannedCanopy(plan, dna, tip, contact)) {
                    addTipLeaf(plan, dna, tip, contact);
                    break;
                }
            }
        }
    }

    private void addTipLeaf(
            TreePlan plan, TreeDna dna, TreeBranchPlan.BranchTip tip, int[] offset) {
        plan.add(new PlannedTreeBlock(
                tip.x() + offset[0],
                tip.y() + offset[1],
                tip.z() + offset[2],
                dna.species().leafMaterial(),
                TreeBlockRole.CANOPY,
                Axis.Y,
                null
        ));
    }

    private boolean touchesPlannedCanopy(
            TreePlan plan, TreeDna dna, TreeBranchPlan.BranchTip tip, int[] offset) {
        int x = tip.x() + offset[0];
        int y = tip.y() + offset[1];
        int z = tip.z() + offset[2];
        PlannedTreeBlock occupied = plan.blocksByKey().get(x + ":" + y + ":" + z);
        if (occupied != null && occupied.role() != TreeBlockRole.CANOPY) {
            return false;
        }
        for (int[] neighbor : TIP_CONTACT_OFFSETS) {
            PlannedTreeBlock planned = plan.blocksByKey().get(
                    (x + neighbor[0]) + ":"
                            + (y + neighbor[1]) + ":"
                            + (z + neighbor[2]));
            if (planned != null
                    && planned.role() == TreeBlockRole.CANOPY
                    && planned.material() == dna.species().leafMaterial()) {
                return true;
            }
        }
        return false;
    }

    private int plannedLeafContacts(
            TreePlan plan, TreeDna dna, TreeBranchPlan.BranchTip tip) {
        return TreeBranchTipIntegrityPolicy.plannedLeafContacts(
                tip.x(), tip.y(), tip.z(), dna.species().leafMaterial(),
                plan.blocksByKey());
    }
    private boolean usesFancyEarlyCrown(TreeDna dna) {
        return dna.maturityStage().ordinal() < TreeMaturityStage.MATURE.ordinal()
                && dna.species() != TreeSpecies.SPRUCE;
    }

    private void planFancyEarlyCrown(TreePlan plan, TreeDna dna, List<TreeBranchPlan.BranchTip> branchTips, Random random, int topY) {
        int radiusX = Math.min(TreeSpeciesStageStyle.canopyRadiusX(dna), dna.maturityStage() == TreeMaturityStage.SMALL ? 3 : 4);
        int radiusZ = Math.min(TreeSpeciesStageStyle.canopyRadiusZ(dna), dna.maturityStage() == TreeMaturityStage.SMALL ? 3 : 4);
        int centerX = dna.trunkXAt(topY);
        int centerZ = dna.trunkZAt(topY);
        int baseY = Math.max(dna.baseY() + 2, topY - 1);

        // ## Fancy-tree early crown: stacked uneven discs first, dramatic old-growth shaping later.
        planLeafBlob(plan, dna, centerX, baseY, centerZ, radiusX, 1, radiusZ, random);
        planLeafBlob(plan, dna, centerX, baseY + 1, centerZ, Math.max(1, radiusX - 1), 1, Math.max(1, radiusZ - 1), random);
        if (dna.maturityStage() == TreeMaturityStage.MEDIUM) {
            planLeafBlob(plan, dna, centerX, baseY + 2, centerZ, Math.max(1, radiusX - 2), 1, Math.max(1, radiusZ - 2), random);
        }
        planNaturalMainCrownFringe(
                plan, dna, centerX, baseY, centerZ, radiusX, radiusZ);

        int tipCap = earlyBranchTipCanopyCap(dna);
        for (TreeBranchPlan.BranchTip tip : branchTips) {
            planLeafBlob(plan, dna, tip.x(), tip.y(), tip.z(), tipCap, 1, tipCap, random);
            if (dna.maturityStage() == TreeMaturityStage.MEDIUM && dna.species() != TreeSpecies.BIRCH) {
                planLeafBlob(plan, dna, tip.x(), tip.y() + 1, tip.z(), Math.max(1, tipCap - 1), 1, Math.max(1, tipCap - 1), random);
            }
            planNaturalLowerFringe(plan, dna, tip);
        }
    }

    private void planNaturalMainCrownFringe(
            TreePlan plan, TreeDna dna,
            int centerX, int baseY, int centerZ,
            int radiusX, int radiusZ) {
        int tufts = switch (dna.species()) {
            case BIRCH, ACACIA -> 2;
            case OAK, CHERRY, MANGROVE ->
                    dna.maturityStage() == TreeMaturityStage.SMALL ? 3 : 4;
            case DARK_OAK, JUNGLE -> 5;
            case SPRUCE -> 0;
        };
        int rotation = Math.floorMod(
                Long.hashCode(dna.seed()) ^ 0x51A0E77,
                LOWER_FRINGE_OFFSETS.length);
        int added = 0;
        int maximumDepth = Math.max(radiusX, radiusZ);
        for (int depth = 0; depth < maximumDepth && added < tufts; depth++) {
            int reachX = Math.max(1, radiusX - depth);
            int reachZ = Math.max(1, radiusZ - depth);
            for (int index = 0; index < LOWER_FRINGE_OFFSETS.length
                    && added < tufts; index++) {
                int[] direction = LOWER_FRINGE_OFFSETS[
                        (rotation + index) % LOWER_FRINGE_OFFSETS.length];
                int x = centerX + (direction[0] * reachX);
                int z = centerZ + (direction[1] * reachZ);
                int y = baseY - 2;
                PlannedTreeBlock support = plan.blocksByKey().get(
                        x + ":" + (y + 1) + ":" + z);
                if (support == null
                        || support.role() != TreeBlockRole.CANOPY
                        || plan.blocksByKey().containsKey(
                                x + ":" + y + ":" + z)) {
                    continue;
                }
                // ## Every lower tuft hangs from the planned main cloud. The
                // deterministic asymmetric selection breaks flat cut planes
                // without creating detached leaves or rerolling after restart.
                plan.add(new PlannedTreeBlock(
                        x, y, z, dna.species().leafMaterial(),
                        TreeBlockRole.CANOPY, Axis.Y, null));
                added++;
            }
        }
    }

    private void planNaturalLowerFringe(
            TreePlan plan, TreeDna dna, TreeBranchPlan.BranchTip tip) {
        int tufts = switch (dna.species()) {
            case BIRCH, ACACIA -> 1;
            case SPRUCE -> 2;
            case OAK, CHERRY, MANGROVE -> 3;
            case DARK_OAK, JUNGLE -> 4;
        };
        int rotation = Math.floorMod(
                Long.hashCode(dna.seed()) ^ (tip.branchId() * 37),
                LOWER_FRINGE_OFFSETS.length);
        int added = 0;
        for (int index = 0; index < LOWER_FRINGE_OFFSETS.length
                && added < tufts; index++) {
            int[] offset = LOWER_FRINGE_OFFSETS[
                    (rotation + index) % LOWER_FRINGE_OFFSETS.length];
            int x = tip.x() + offset[0];
            int y = tip.y() - 2;
            int z = tip.z() + offset[1];
            PlannedTreeBlock support = plan.blocksByKey().get(
                    x + ":" + (y + 1) + ":" + z);
            if (support == null || support.role() != TreeBlockRole.CANOPY
                    || plan.blocksByKey().containsKey(x + ":" + y + ":" + z)) {
                continue;
            }
            plan.add(new PlannedTreeBlock(
                    x, y, z, dna.species().leafMaterial(),
                    TreeBlockRole.CANOPY, Axis.Y, null));
            added++;
        }
    }

    private int earlyBranchTipCanopyCap(TreeDna dna) {
        return switch (dna.species()) {
            case BIRCH -> dna.maturityStage() == TreeMaturityStage.SMALL ? 1 : 2;
            case ACACIA -> dna.maturityStage() == TreeMaturityStage.SMALL ? 2 : 3;
            case OAK, CHERRY, DARK_OAK, MANGROVE -> dna.maturityStage() == TreeMaturityStage.SMALL ? 2 : 3;
            case JUNGLE -> dna.maturityStage() == TreeMaturityStage.SMALL ? 2 : 4;
            case SPRUCE -> 2;
        };
    }

    private int branchTipCanopyCap(TreeDna dna) {
        int stageCap = switch (dna.maturityStage()) {
            case SMALL -> 2;
            case MEDIUM -> 3;
            case MATURE -> 4;
            case ANCIENT -> 5;
        };
        int speciesCap = switch (dna.species()) {
            case BIRCH -> 2;
            case SPRUCE -> 3;
            case OAK, ACACIA -> 4;
            case CHERRY, MANGROVE -> 4;
            case DARK_OAK -> 5;
            case JUNGLE -> 6;
        };
        return Math.max(1, Math.min(stageCap, speciesCap));
    }

    private int branchTipVerticalCap(TreeDna dna) {
        return switch (dna.species()) {
            case BIRCH, ACACIA, CHERRY -> 1;
            case SPRUCE -> 2;
            default -> dna.maturityStage() == TreeMaturityStage.ANCIENT ? 3 : 2;
        };
    }

    private void planLayeredCanopy(TreePlan plan, TreeDna dna, Random random) {
        int layers = TreeSpeciesStageStyle.canopyLayerCount(dna);
        int visibleHeight = TreeSpeciesStageStyle.visibleHeight(dna);
        for (int layer = 0; layer < layers; layer++) {
            double start = dna.species() == TreeSpecies.SPRUCE ? 0.30D : 0.42D;
            double width = dna.species() == TreeSpecies.SPRUCE ? 0.54D : 0.42D;
            double progress = start + ((layer + 1.0D) / (layers + 1.0D)) * width;
            int centerY = dna.baseY() + (int) Math.round(visibleHeight * progress);
            int spread = Math.max(1, TreeSpeciesStageStyle.canopyLayerSpread(dna) - Math.max(0, layer - 1));
            int radiusX = Math.max(TreeSpeciesStageStyle.canopyRadiusX(dna), spread + random.nextInt(2));
            int radiusZ = Math.max(TreeSpeciesStageStyle.canopyRadiusZ(dna), spread + random.nextInt(2));
            int radiusY = Math.max(1, TreeSpeciesStageStyle.canopyRadiusY(dna) - 1);
            if (dna.species() == TreeSpecies.SPRUCE) {
                radiusX = Math.max(1, radiusX - layer);
                radiusZ = Math.max(1, radiusZ - layer);
            } else {
                int cap = layerCanopyCap(dna, layer);
                radiusX = Math.min(radiusX, cap);
                radiusZ = Math.min(radiusZ, cap);
                radiusY = Math.min(radiusY, layerVerticalCap(dna));
            }
            planLeafBlob(plan, dna, dna.trunkXAt(centerY), centerY, dna.trunkZAt(centerY), radiusX, radiusY, radiusZ, random);
        }
    }

    private int layerCanopyCap(TreeDna dna, int layer) {
        int base = switch (dna.species()) {
            case JUNGLE -> 8;
            case DARK_OAK -> 7;
            case CHERRY -> 6;
            case OAK, MANGROVE -> 6;
            case ACACIA -> 6;
            case BIRCH -> 3;
            case SPRUCE -> 7;
        };
        if (dna.maturityStage() == TreeMaturityStage.ANCIENT
                && dna.species() != TreeSpecies.BIRCH) {
            base++;
        }
        return Math.max(2, base - Math.max(0, layer / 2));
    }

    private int layerVerticalCap(TreeDna dna) {
        return switch (dna.species()) {
            case CHERRY, ACACIA -> 1;
            case BIRCH -> 2;
            default -> 2;
        };
    }

    private double fancyCanopyDensityFloor(TreeDna dna) {
        return switch (dna.species()) {
            case BIRCH -> 0.72D;
            case ACACIA -> 0.70D;
            case CHERRY -> 0.78D;
            case SPRUCE -> 0.0D;
            default -> 0.80D;
        };
    }
    private void planLeafBlob(TreePlan plan, TreeDna dna, int centerX, int centerY, int centerZ, int radiusX, int radiusY, int radiusZ, Random random) {
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int y = -radiusY; y <= radiusY; y++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    double nx = Math.abs(x) / Math.max(1.0D, radiusX);
                    double ny = Math.abs(y) / Math.max(1.0D, radiusY);
                    double nz = Math.abs(z) / Math.max(1.0D, radiusZ);
                    double normalized = (nx + (ny * 1.25D) + nz) / 2.65D;
                    boolean fancyCloud = usesFancyEarlyCrown(dna);
                    double density = fancyCloud
                            ? Math.max(TreeSpeciesStageStyle.canopyDensity(dna),
                                    fancyCanopyDensityFloor(dna))
                            : TreeSpeciesStageStyle.canopyDensity(dna);
                    boolean solidInnerCloud = fancyCloud && normalized <= 0.58D;
                    double edgeVariation = fancyCloud ? 0.10D : 0.18D;
                    if (normalized > 1.0D
                            || (!solidInnerCloud
                                    && random.nextDouble() > density
                                            - (normalized * edgeVariation))) {
                        continue;
                    }
                    plan.add(new PlannedTreeBlock(
                            centerX + x,
                            centerY + y,
                            centerZ + z,
                            dna.species().leafMaterial(),
                            TreeBlockRole.CANOPY,
                            Axis.Y,
                            null
                    ));
                }
            }
        }
    }
}
