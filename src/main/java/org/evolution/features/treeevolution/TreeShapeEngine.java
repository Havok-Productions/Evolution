package org.evolution.features.treeevolution;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.block.Block;

final class TreeShapeEngine {
    private static final int MAX_VALID_CHOICES = 48;

    ShapeChoice bestChoice(List<ShapeChoice> choices) {
        return choices.stream()
                .max(Comparator.comparingDouble(ShapeChoice::score))
                .orElse(null);
    }

    boolean hasEnoughChoices(List<ShapeChoice> choices) {
        return choices.size() >= MAX_VALID_CHOICES;
    }

    ShapeChoice score(TreeCandidate candidate, TreeDna dna, PlannedTreeBlock block, Block target, TreeGrowthIntent intent, int nextCursor) {
        double score = roleBase(block.role());
        int visibleHeight = TreeSpeciesStageStyle.visibleHeight(dna);
        int relativeY = block.y() - dna.baseY();
        int topY = dna.baseY() + visibleHeight - 1;
        int horizontal = Math.max(Math.abs(block.x() - dna.trunkXAt(block.y())), Math.abs(block.z() - dna.trunkZAt(block.y())));
        double heightProgress = relativeY / Math.max(1.0D, visibleHeight);

        score += stageRoleScore(dna, block.role(), intent);
        score += speciesSilhouetteScore(dna, block, horizontal, heightProgress);
        score += continuityScore(candidate, dna, block, target);

        if (block.role() == TreeBlockRole.TRUNK) {
            score += Math.max(0, 40 - Math.abs(block.y() - (candidate.topY() + 1)) * 8);
            score += horizontal == 0 ? 18 : -horizontal * 4;
            if (block.y() > topY + 1) {
                score -= 40;
            }
        } else if (block.role() == TreeBlockRole.BRANCH) {
            double branchStart = TreeSpeciesStageStyle.branchStartRatio(dna);
            score += Math.max(0, 24 - Math.abs(heightProgress - branchStart) * 30);
            score += horizontal >= 1 ? 12 : -18;
        } else if (block.role() == TreeBlockRole.CANOPY) {
            int activeTopY = Math.min(topY, Math.max(candidate.topY(), dna.baseY() + 2));
            score += Math.max(0, 30 - Math.abs(block.y() - topY) * 6);
            if (intent == TreeGrowthIntent.CANOPY) {
                int activeHorizontal = Math.max(Math.abs(block.x() - dna.trunkXAt(activeTopY)), Math.abs(block.z() - dna.trunkZAt(activeTopY)));
                score += Math.max(0, 42 - Math.abs(block.y() - activeTopY) * 9 - activeHorizontal * 4);
                if (block.y() > activeTopY + 3) {
                    score -= 28;
                }
            }
            if (candidate.connectedLeaves() < Math.max(10, candidate.connectedLogs())) {
                score += 24;
            }
            if (Math.abs(block.x() - dna.trunkXAt(topY)) <= 2
                    && Math.abs(block.z() - dna.trunkZAt(topY)) <= 2
                    && Math.abs(block.y() - topY) <= 2) {
                score += 20;
            }
            if (Math.abs(block.x() - dna.trunkXAt(activeTopY)) <= 2
                    && Math.abs(block.z() - dna.trunkZAt(activeTopY)) <= 2
                    && Math.abs(block.y() - activeTopY) <= 2) {
                score += 24;
            }
        }

        String reason = "role=" + block.role()
                + " score=" + rounded(score)
                + " y=" + relativeY + "/" + visibleHeight
                + " horizontal=" + horizontal
                + " stage=" + dna.maturityStage()
                + " intent=" + intent;
        return new ShapeChoice(block, target, nextCursor, score, reason);
    }

    ShapeReport analyze(TreePlan plan, TreeDna dna) {
        List<PlannedTreeBlock> blocks = plan.orderedBlocks();
        Map<String, PlannedTreeBlock> byKey = new HashMap<>();
        int wood = 0;
        int leaves = 0;
        int branches = 0;
        int highestTrunkY = Integer.MIN_VALUE;
        for (PlannedTreeBlock block : blocks) {
            byKey.put(block.key(), block);
            if (block.role() == TreeBlockRole.TRUNK || block.role() == TreeBlockRole.BRANCH || block.role() == TreeBlockRole.ROOT) {
                wood++;
            }
            if (block.role() == TreeBlockRole.CANOPY) {
                leaves++;
            }
            if (block.role() == TreeBlockRole.BRANCH) {
                branches++;
            }
            if (block.role() == TreeBlockRole.TRUNK) {
                highestTrunkY = Math.max(highestTrunkY, block.y());
            }
        }

        int floatingWood = 0;
        int unanchoredBranchSegments = 0;
        for (PlannedTreeBlock block : blocks) {
            if (block.role() != TreeBlockRole.BRANCH && block.role() != TreeBlockRole.TRUNK) {
                continue;
            }
            if (block.role() == TreeBlockRole.TRUNK && block.y() == dna.baseY()) {
                continue;
            }
            if (!hasNeighborWood(byKey, block)) {
                floatingWood++;
            }
            if (block.role() == TreeBlockRole.BRANCH && block.hasBranchPath() && !hasParentWood(byKey, block)) {
                unanchoredBranchSegments++;
            }
        }

        boolean topCovered = highestTrunkY == Integer.MIN_VALUE || hasLeafNear(byKey, dna.trunkXAt(highestTrunkY), highestTrunkY, dna.trunkZAt(highestTrunkY), 2);
        int branchTips = 0;
        int coveredBranchTips = 0;
        for (TreeBranchPlan branch : plan.branchPlans()) {
            TreeBranchPlan.BranchTip tip = branch.tip();
            branchTips++;
            if (TreeBranchTipIntegrityPolicy.hasPreplannedEnvelope(
                    dna, tip.x(), tip.y(), tip.z(), byKey)) {
                coveredBranchTips++;
            }
        }
        double leafWoodRatio = wood == 0 ? 0.0D : (double) leaves / wood;
        boolean normalEnough = floatingWood == 0
                && unanchoredBranchSegments == 0
                && topCovered
                && branches >= minimumBranchCount(dna)
                && leafWoodRatio >= minimumLeafWoodRatio(dna)
                && coveredBranchTips >= minimumCoveredBranchTips(dna, branchTips);
        return new ShapeReport(wood, leaves, branches, floatingWood, unanchoredBranchSegments, branchTips, coveredBranchTips, topCovered, leafWoodRatio, normalEnough);
    }

    private double roleBase(TreeBlockRole role) {
        return switch (role) {
            case TRUNK -> 120;
            case BRANCH -> 100;
            case CANOPY -> 96;
            case ROOT -> 54;
            case VINE -> 32;
            case GROUND_DETAIL -> 26;
            case FALLEN_LOG -> 20;
            case SAPLING -> 18;
        };
    }

    private double stageRoleScore(TreeDna dna, TreeBlockRole role, TreeGrowthIntent intent) {
        if (intent == TreeGrowthIntent.REPAIR) {
            return role == TreeBlockRole.TRUNK || role == TreeBlockRole.BRANCH || role == TreeBlockRole.CANOPY ? 28 : -16;
        }
        return switch (dna.maturityStage()) {
            case SMALL -> switch (role) {
                case TRUNK -> 22;
                case CANOPY -> 20;
                case BRANCH -> dna.species() == TreeSpecies.BIRCH ? -8 : 6;
                default -> -10;
            };
            case MEDIUM -> switch (role) {
                case BRANCH -> 24;
                case CANOPY -> 22;
                case TRUNK -> 12;
                default -> 0;
            };
            case MATURE, ANCIENT -> switch (role) {
                case CANOPY -> 24;
                case BRANCH -> 18;
                case VINE, GROUND_DETAIL, ROOT -> 10;
                default -> 0;
            };
        };
    }

    private double speciesSilhouetteScore(TreeDna dna, PlannedTreeBlock block, int horizontal, double heightProgress) {
        return switch (dna.species()) {
            case BIRCH -> birchScore(block, horizontal, heightProgress);
            case SPRUCE -> spruceScore(block, horizontal, heightProgress);
            case JUNGLE -> jungleScore(block, horizontal, heightProgress);
            case ACACIA -> acaciaScore(block, horizontal, heightProgress);
            case DARK_OAK -> darkOakScore(block, horizontal, heightProgress);
            case MANGROVE -> mangroveScore(block, horizontal, heightProgress);
            case CHERRY -> cherryScore(block, horizontal, heightProgress);
            case OAK -> oakScore(block, horizontal, heightProgress);
        };
    }

    private double oakScore(PlannedTreeBlock block, int horizontal, double heightProgress) {
        if (block.role() == TreeBlockRole.BRANCH) {
            return between(heightProgress, 0.42D, 0.82D) ? 16 : -6;
        }
        if (block.role() == TreeBlockRole.CANOPY) {
            return between(heightProgress, 0.55D, 1.12D) && horizontal <= 4 ? 15 : 0;
        }
        return 0;
    }

    private double birchScore(PlannedTreeBlock block, int horizontal, double heightProgress) {
        if (block.role() == TreeBlockRole.TRUNK) {
            return horizontal == 0 ? 14 : -18;
        }
        if (block.role() == TreeBlockRole.BRANCH) {
            return horizontal <= 2 && heightProgress > 0.62D ? 4 : -18;
        }
        if (block.role() == TreeBlockRole.CANOPY) {
            return heightProgress > 0.62D && horizontal <= 3 ? 18 : -4;
        }
        return 0;
    }

    private double spruceScore(PlannedTreeBlock block, int horizontal, double heightProgress) {
        if (block.role() == TreeBlockRole.BRANCH) {
            return between(heightProgress, 0.28D, 0.82D) ? 18 - horizontal : -8;
        }
        if (block.role() == TreeBlockRole.CANOPY) {
            return horizontal <= Math.max(1, (int) Math.round((1.1D - heightProgress) * 5)) ? 16 : -6;
        }
        return 0;
    }

    private double jungleScore(PlannedTreeBlock block, int horizontal, double heightProgress) {
        if (block.role() == TreeBlockRole.TRUNK) {
            return 10;
        }
        if (block.role() == TreeBlockRole.BRANCH) {
            return heightProgress > 0.54D && horizontal >= 1 ? 20 : -5;
        }
        if (block.role() == TreeBlockRole.CANOPY) {
            return heightProgress > 0.58D ? 18 : -8;
        }
        return block.role() == TreeBlockRole.VINE ? 12 : 0;
    }

    private double acaciaScore(PlannedTreeBlock block, int horizontal, double heightProgress) {
        if (block.role() == TreeBlockRole.BRANCH) {
            return heightProgress > 0.45D && horizontal >= 2 ? 26 : -8;
        }
        if (block.role() == TreeBlockRole.CANOPY) {
            return heightProgress > 0.58D && horizontal >= 1 ? 18 : -4;
        }
        return 0;
    }

    private double darkOakScore(PlannedTreeBlock block, int horizontal, double heightProgress) {
        if (block.role() == TreeBlockRole.BRANCH || block.role() == TreeBlockRole.CANOPY) {
            return between(heightProgress, 0.34D, 0.94D) && horizontal <= 6 ? 18 : -3;
        }
        return block.role() == TreeBlockRole.TRUNK && horizontal <= 1 ? 10 : 0;
    }

    private double mangroveScore(PlannedTreeBlock block, int horizontal, double heightProgress) {
        if (block.role() == TreeBlockRole.ROOT) {
            return 18;
        }
        if (block.role() == TreeBlockRole.BRANCH) {
            return between(heightProgress, 0.35D, 0.82D) ? 16 : -4;
        }
        if (block.role() == TreeBlockRole.CANOPY) {
            return between(heightProgress, 0.48D, 1.02D) ? 16 : -4;
        }
        return 0;
    }

    private double cherryScore(PlannedTreeBlock block, int horizontal, double heightProgress) {
        if (block.role() == TreeBlockRole.BRANCH) {
            return heightProgress > 0.36D && horizontal >= 1 ? 18 : -3;
        }
        if (block.role() == TreeBlockRole.CANOPY) {
            return between(heightProgress, 0.42D, 1.02D) && horizontal <= 6 ? 22 : -2;
        }
        return 0;
    }

    private double continuityScore(TreeCandidate candidate, TreeDna dna, PlannedTreeBlock block, Block target) {
        if (block.role() == TreeBlockRole.TRUNK && target.getY() <= candidate.topY() + 1) {
            return 20;
        }
        if (block.role() == TreeBlockRole.CANOPY && candidate.connectedLeaves() < candidate.connectedLogs()) {
            return 14;
        }
        if (block.role() == TreeBlockRole.BRANCH && target.getY() <= dna.baseY() + TreeSpeciesStageStyle.visibleHeight(dna)) {
            return 10;
        }
        return 0;
    }

    private boolean hasNeighborWood(Map<String, PlannedTreeBlock> byKey, PlannedTreeBlock block) {
        int[][] offsets = {
                {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
        };
        for (int[] offset : offsets) {
            PlannedTreeBlock neighbor = byKey.get((block.x() + offset[0]) + ":" + (block.y() + offset[1]) + ":" + (block.z() + offset[2]));
            if (neighbor != null && (neighbor.role() == TreeBlockRole.TRUNK || neighbor.role() == TreeBlockRole.BRANCH || neighbor.role() == TreeBlockRole.ROOT)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasParentWood(Map<String, PlannedTreeBlock> byKey, PlannedTreeBlock block) {
        PlannedTreeBlock parent = byKey.get(block.parentKey());
        return parent != null && (parent.role() == TreeBlockRole.TRUNK || parent.role() == TreeBlockRole.BRANCH || parent.role() == TreeBlockRole.ROOT);
    }

    private boolean hasLeafNear(Map<String, PlannedTreeBlock> byKey, int x, int y, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    PlannedTreeBlock block = byKey.get((x + dx) + ":" + (y + dy) + ":" + (z + dz));
                    if (block != null && block.role() == TreeBlockRole.CANOPY) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int minimumBranchCount(TreeDna dna) {
        return switch (dna.species()) {
            case BIRCH -> 0;
            case SPRUCE -> dna.maturityStage() == TreeMaturityStage.SMALL ? 1 : 2;
            default -> dna.maturityStage() == TreeMaturityStage.SMALL ? 1 : 2;
        };
    }

    private double minimumLeafWoodRatio(TreeDna dna) {
        return switch (dna.species()) {
            case BIRCH -> 0.85D;
            case SPRUCE -> 0.70D;
            case JUNGLE -> 0.90D;
            case ACACIA -> 0.75D;
            default -> 1.0D;
        };
    }

    private int minimumCoveredBranchTips(TreeDna dna, int branchTips) {
        if (branchTips <= 0) {
            return 0;
        }
        return branchTips;
    }

    private boolean between(double value, double min, double max) {
        return value >= min && value <= max;
    }

    private double rounded(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }

    record ShapeChoice(PlannedTreeBlock block, Block target, int nextCursor, double score, String reason) {
    }

    record ShapeReport(
            int wood,
            int leaves,
            int branches,
            int floatingWood,
            int unanchoredBranchSegments,
            int branchTips,
            int coveredBranchTips,
            boolean topCovered,
            double leafWoodRatio,
            boolean normalEnough
    ) {
    }
}
