package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

final class TreePlan {
    private final Map<String, PlannedTreeBlock> blocks = new LinkedHashMap<>();
    private final TreeCoordinateTranslator coordinateTranslator;
    private final List<Map<String, Object>> coordinateConflictSamples =
            new ArrayList<>();
    private Map<String, PlannedTreeBlock> publishedBlocks;
    private List<TreeBranchPlan> branchPlans = List.of();
    private List<TreeBranchPlan.BranchTip> branchEnvelopeCleanupTips = List.of();
    private List<PlannedTreeBlock> orderedBlocksCache;
    private TreeBlueprintValidation blueprintValidation;
    private int coordinateProposalCount;
    private int coordinateConflictCount;
    private int prunedBranchCount;
    private boolean sealed;
    private TreePlacementAugment activeAugment =
            TreePlacementAugment.UNCLASSIFIED;

    TreePlan(TreeDna dna) {
        coordinateTranslator = new TreeCoordinateTranslator(dna);
    }

    void add(PlannedTreeBlock block) {
        requireDraft();
        if (block.augment() == TreePlacementAugment.UNCLASSIFIED
                && activeAugment != TreePlacementAugment.UNCLASSIFIED) {
            block = block.withAugment(activeAugment);
        }
        block = coordinateTranslator.canonicalize(block);
        coordinateProposalCount++;
        PlannedTreeBlock current = blocks.get(block.key());
        if (current != null && !current.equals(block)) {
            recordCoordinateConflict(current, block);
        }
        if (current == null || rolePriority(block) <= rolePriority(current)) {
            blocks.put(block.key(), block);
            orderedBlocksCache = null;
        }
    }

    void withAugment(TreePlacementAugment augment, Runnable action) {
        withAugment(augment, () -> {
            action.run();
            return null;
        });
    }

    <T> T withAugment(
            TreePlacementAugment augment,
            Supplier<T> action
    ) {
        // ## Nested species augments temporarily override the broad planner
        // label, then restore it even if a planner contract throws.
        TreePlacementAugment previous = activeAugment;
        activeAugment = augment == null
                ? TreePlacementAugment.UNCLASSIFIED : augment;
        try {
            return action.get();
        } finally {
            activeAugment = previous;
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
        requireDraft();
        this.branchPlans = List.copyOf(branchPlans);
    }

    List<TreeBranchPlan> branchPlans() {
        return branchPlans;
    }

    void setBranchEnvelopeCleanupTips(
            List<TreeBranchPlan.BranchTip> branchEnvelopeCleanupTips) {
        requireDraft();
        this.branchEnvelopeCleanupTips = List.copyOf(branchEnvelopeCleanupTips);
    }

    List<TreeBranchPlan.BranchTip> branchEnvelopeCleanupTips() {
        return branchEnvelopeCleanupTips;
    }

    Map<String, PlannedTreeBlock> blocksByKey() {
        return sealed ? publishedBlocks : blocks;
    }

    void removeBranch(int branchId) {
        requireDraft();
        if (blocks.entrySet().removeIf(entry ->
                entry.getValue().role() == TreeBlockRole.BRANCH
                        && entry.getValue().branchId() == branchId)) {
            orderedBlocksCache = null;
        }
    }

    void removeCanopy(Set<String> keys) {
        requireDraft();
        if (keys == null || keys.isEmpty()) {
            return;
        }
        if (blocks.entrySet().removeIf(entry ->
                keys.contains(entry.getKey())
                        && entry.getValue().role()
                                == TreeBlockRole.CANOPY)) {
            orderedBlocksCache = null;
        }
    }

    void recordPrunedBranches(int count) {
        requireDraft();
        prunedBranchCount += Math.max(0, count);
    }

    int prunedBranchCount() {
        return prunedBranchCount;
    }

    int size() {
        return blocks.size();
    }

    TreeCoordinateTranslator coordinateTranslator() {
        return coordinateTranslator;
    }

    int coordinateProposalCount() {
        return coordinateProposalCount;
    }

    int coordinateConflictCount() {
        return coordinateConflictCount;
    }

    List<Map<String, Object>> coordinateConflictSamples() {
        return List.copyOf(coordinateConflictSamples);
    }

    TreeBlueprintValidation blueprintValidation() {
        if (blueprintValidation == null) {
            throw new IllegalStateException(
                    "Tree blueprint has not been approved");
        }
        return blueprintValidation;
    }

    boolean sealed() {
        return sealed;
    }

    void seal(TreeBlueprintValidation validation) {
        requireDraft();
        if (validation == null || !validation.approved()) {
            throw new IllegalArgumentException(
                    "Only an approved blueprint may be sealed");
        }
        blueprintValidation = validation;
        publishedBlocks = Map.copyOf(blocks);
        orderedBlocksCache = List.copyOf(orderedBlocks());
        sealed = true;
    }

    private void recordCoordinateConflict(
            PlannedTreeBlock current,
            PlannedTreeBlock proposed
    ) {
        coordinateConflictCount++;
        if (coordinateConflictSamples.size() >= 24) {
            return;
        }
        PlannedTreeBlock winner = rolePriority(proposed)
                <= rolePriority(current) ? proposed : current;
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("relative-coordinate",
                coordinateTranslator.relativeKey(proposed));
        sample.put("world-coordinate", proposed.key());
        sample.put("existing", coordinateLabel(current));
        sample.put("proposed", coordinateLabel(proposed));
        sample.put("winner", coordinateLabel(winner));
        sample.put("resolution", "role-priority");
        coordinateConflictSamples.add(Map.copyOf(sample));
    }

    private String coordinateLabel(PlannedTreeBlock block) {
        return block.role() + "/" + block.material()
                + "/" + block.augment();
    }

    private void requireDraft() {
        if (sealed) {
            throw new IllegalStateException(
                    "A sealed tree blueprint cannot be modified");
        }
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
