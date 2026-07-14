package org.slowtrees.treeevolution;

import java.util.List;
import java.util.Random;
import org.bukkit.Axis;

final class CanopyPlanner {
    void plan(TreePlan plan, TreeDna dna, List<TreeBranchPlan> branchPlans) {
        Random random = new Random(dna.seed() ^ 0xC0A0BEEFL);
        int topY = dna.baseY() + TreeSpeciesStageStyle.visibleHeight(dna) - 1;
        List<TreeBranchPlan.BranchTip> branchTips = branchPlans.stream().map(TreeBranchPlan::tip).toList();
        if (usesFancyEarlyCrown(dna)) {
            planFancyEarlyCrown(plan, dna, branchTips, random, topY);
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

        int tipCap = dna.maturityStage() == TreeMaturityStage.SMALL ? 1 : 2;
        for (TreeBranchPlan.BranchTip tip : branchTips) {
            if (random.nextBoolean()) {
                planLeafBlob(plan, dna, tip.x(), tip.y(), tip.z(), tipCap, 1, tipCap, random);
            }
        }
    }

    private int branchTipCanopyCap(TreeDna dna) {
        int stageCap = switch (dna.maturityStage()) {
            case SMALL -> 2;
            case MEDIUM -> 3;
            case MATURE -> 4;
            case ANCIENT -> 5;
        };
        int speciesCap = switch (dna.species()) {
            case BIRCH, SPRUCE -> 2;
            case CHERRY -> 3;
            case OAK, ACACIA, MANGROVE -> 3;
            case DARK_OAK -> 4;
            case JUNGLE -> 5;
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
            case JUNGLE -> 6;
            case DARK_OAK -> 5;
            case CHERRY -> 5;
            case OAK, MANGROVE -> 4;
            case ACACIA -> 4;
            case BIRCH -> 3;
            case SPRUCE -> 7;
        };
        if (dna.maturityStage() == TreeMaturityStage.ANCIENT
                && dna.species() != TreeSpecies.BIRCH
                && dna.species() != TreeSpecies.OAK) {
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

    private void planLeafBlob(TreePlan plan, TreeDna dna, int centerX, int centerY, int centerZ, int radiusX, int radiusY, int radiusZ, Random random) {
        for (int x = -radiusX; x <= radiusX; x++) {
            for (int y = -radiusY; y <= radiusY; y++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    double nx = Math.abs(x) / Math.max(1.0D, radiusX);
                    double ny = Math.abs(y) / Math.max(1.0D, radiusY);
                    double nz = Math.abs(z) / Math.max(1.0D, radiusZ);
                    double normalized = (nx + (ny * 1.25D) + nz) / 2.65D;
                    if (normalized > 1.0D || random.nextDouble() > TreeSpeciesStageStyle.canopyDensity(dna) - (normalized * 0.18D)) {
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
