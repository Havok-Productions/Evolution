package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TreePlan {
    private final Map<String, PlannedTreeBlock> blocks = new LinkedHashMap<>();
    private List<TreeBranchPlan> branchPlans = List.of();
    private List<TreeBranchPlan.BranchTip> branchEnvelopeCleanupTips = List.of();
    private List<PlannedTreeBlock> orderedBlocksCache;
    private int prunedBranchCount;

    void add(PlannedTreeBlock block) {
        PlannedTreeBlock current = blocks.get(block.key());
        if (current == null || rolePriority(block) <= rolePriority(current)) {
            blocks.put(block.key(), block);
            orderedBlocksCache = null;
        }
    }

    List<PlannedTreeBlock> orderedBlocks() {
        if (orderedBlocksCache == null) {
            orderedBlocksCache = new ArrayList<>(blocks.values()).stream()
                    .sorted(Comparator
                            .comparingInt(TreePlan::verticalPriority)
                            .thenComparingInt(TreePlan::rolePriority))
                    .toList();
        }
        return orderedBlocksCache;
    }

    void setBranchPlans(List<TreeBranchPlan> branchPlans) {
        this.branchPlans = List.copyOf(branchPlans);
    }

    List<TreeBranchPlan> branchPlans() {
        return branchPlans;
    }

    void setBranchEnvelopeCleanupTips(
            List<TreeBranchPlan.BranchTip> branchEnvelopeCleanupTips) {
        this.branchEnvelopeCleanupTips = List.copyOf(branchEnvelopeCleanupTips);
    }

    List<TreeBranchPlan.BranchTip> branchEnvelopeCleanupTips() {
        return branchEnvelopeCleanupTips;
    }

    Map<String, PlannedTreeBlock> blocksByKey() {
        return blocks;
    }

    void removeBranch(int branchId) {
        if (blocks.entrySet().removeIf(entry ->
                entry.getValue().role() == TreeBlockRole.BRANCH
                        && entry.getValue().branchId() == branchId)) {
            orderedBlocksCache = null;
        }
    }

    void recordPrunedBranches(int count) {
        prunedBranchCount += Math.max(0, count);
    }

    int prunedBranchCount() {
        return prunedBranchCount;
    }

    int size() {
        return blocks.size();
    }

    private static int verticalPriority(PlannedTreeBlock block) {
        return switch (block.role()) {
            case ROOT, GROUND_DETAIL -> block.y() - 2;
            case VINE -> block.y() + 1;
            default -> block.y();
        };
    }

    private static int rolePriority(PlannedTreeBlock block) {
        return switch (block.role()) {
            case ROOT -> 0;
            case TRUNK -> 1;
            case BRANCH -> 2;
            case CANOPY -> 3;
            case VINE -> 4;
            case FALLEN_LOG -> 5;
            case SAPLING -> 6;
            case GROUND_DETAIL -> 7;
        };
    }
}
