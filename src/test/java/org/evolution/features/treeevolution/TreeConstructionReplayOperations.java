package org.evolution.features.treeevolution;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.Axis;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Cell;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Ownership;
import org.evolution.features.treeevolution.constructor.TreeConstructionDecision;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionOperations;
import org.evolution.features.treeevolution.constructor.executor.TreeConstructionResult;

/**
 * ## Maps hierarchy operations to one-block virtual-world mutations.
 */
final class TreeConstructionReplayOperations
        implements TreeConstructionOperations {
    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private final String scenario;
    private final TreePlan plan;
    private final List<PlannedTreeBlock> targets;
    private final List<PlannedTreeBlock> canopyTargets;
    private final TreeConstructionReplayInspector inspector;
    private final Set<String> allowedMultiMutationKeys;
    private final State state;
    private final List<String> ownershipRoleHistory = new ArrayList<>();

    TreeConstructionReplayOperations(
            String scenario,
            TreePlan plan,
            List<PlannedTreeBlock> targets,
            List<PlannedTreeBlock> canopyTargets,
            TreeConstructionReplayInspector inspector,
            Set<String> allowedMultiMutationKeys,
            State state
    ) {
        this.scenario = scenario;
        this.plan = plan;
        this.targets = targets;
        this.canopyTargets = canopyTargets;
        this.inspector = inspector;
        this.allowedMultiMutationKeys = allowedMultiMutationKeys;
        this.state = state;
    }

    @Override
    public TreeConstructionResult waitForOwnership() {
        return TreeConstructionResult.idle("replay.ownership-wait");
    }

    @Override
    public TreeConstructionResult waitForSourceSnapshot() {
        return TreeConstructionResult.idle("replay.snapshot-wait");
    }

    @Override
    public TreeConstructionResult reconcileOwnershipRole() {
        TreeOwnershipRoleReconciliationPolicy.Repair repair =
                inspector.ownershipRoleRepair(dna(), world())
                        .orElseThrow(() -> new IllegalStateException(
                                "ownership role repair had no mismatch"));
        require(dna().reconcileEvolvedRole(
                        repair.blockKey(), repair.liveRole()),
                "ownership role repair made no ledger progress "
                        + repair.marker());
        ownershipRoleHistory.add(repair.marker()
                + " after-log="
                + dna().evolvedShapeLogs().contains(repair.blockKey())
                + " after-leaf="
                + dna().evolvedShapeLeaves().contains(repair.blockKey()));
        dna().markGrownNow();
        return TreeConstructionResult.logicalProgress(
                "replay.ownership-role-reconciled " + repair.marker());
    }

    @Override
    public TreeConstructionResult repairDisconnectedTarget() {
        TreeTargetOwnershipRepairPolicy.Analysis analysis =
                targetOwnershipAnalysis();
        TreeTargetOwnershipRepairPolicy.Repair repair =
                analysis.repair().orElseThrow(() -> new IllegalStateException(
                        "disconnected target had no ownership bridge "
                                + analysis.marker()));
        return applyTargetRepair(
                repair, "repair-disconnected-parent-path");
    }

    @Override
    public TreeConstructionResult repairInterruptedDamage() {
        PlannedTreeBlock target = targets.stream()
                .filter(block -> allowedMultiMutationKeys.contains(block.key()))
                .filter(block -> !targetSatisfied(block))
                .findFirst()
                .orElseGet(() -> targets.stream()
                        .filter(block -> !targetSatisfied(block))
                        .filter(block -> block.role()
                                        == TreeBlockRole.CANOPY
                                || supportReady(block))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "damage repair had no reachable target")));
        return applyTarget(target, "repair");
    }

    @Override
    public TreeConstructionResult replaceTransitionBlocker() {
        PlannedTreeBlock blocker = inspector
                .readyTransitionBlocker(dna(), world())
                .orElseThrow(() -> new IllegalStateException(
                        "hierarchy selected a missing transition blocker"));
        return applyTarget(blocker, "transition-blocker");
    }

    @Override
    public TreeConstructionResult buildSupport() {
        PlannedTreeBlock target = targets.stream()
                .filter(block -> block.role() == TreeBlockRole.TRUNK)
                .filter(block -> !targetSatisfied(block))
                // ## Production can only place a trunk voxel beside existing
                // owned wood. Keep replay selection subject to the same gate.
                .filter(this::supportReady)
                .sorted(Comparator.comparingInt(PlannedTreeBlock::y))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "support phase had no reachable trunk target"));
        return applyTarget(target, "support");
    }

    @Override
    public TreeConstructionResult coverExposedSupport() {
        PlannedTreeBlock target = missingCanopy(
                this::touchesExposedTopSupport)
                .or(this::adaptiveExposedSupportLeaf)
                .orElseThrow(() -> new IllegalStateException(
                        "exposed support mini-constructor had no target scenario="
                                + scenario));
        return applyTarget(target, "cover-exposed-support");
    }

    @Override
    public TreeConstructionResult retireUnplannedBareTerminal() {
        String key = inspector.branchIntegrity(dna(), world(), true)
                .firstUnplannedBareTerminal()
                .orElseThrow(() -> new IllegalStateException(
                        "unplanned-terminal subrule had no replay target scenario="
                                + scenario));
        TreeConstructionMutationPolicy.TargetRetirement retirement =
                inspector.terminalRetirementPlan(dna(), world(), key);
        if (!retirement.safeToRetire()) {
            if (retirement.prerequisiteRetirement().isPresent()) {
                return retireEvolved(
                        retirement.prerequisiteRetirement()
                                .map(TreeConstructionReplayOperations
                                        ::coordinateKey),
                        "terminal-dependent");
            }
            TreeTargetOwnershipRepairPolicy.Repair bridge =
                    retirement.bridge().orElseThrow(() ->
                            new IllegalStateException(
                                    "unplanned terminal has no safe pre-retirement bridge scenario="
                                            + scenario + " key=" + key
                                            + " reason="
                                            + retirement.reason()));
            return applyTargetRepair(
                    bridge, "pre-retirement-parent-path");
        }
        Cell current = world().cell(key);
        require(current != null
                        && current.ownership() == Ownership.EVOLVED,
                "unplanned terminal lost evolved ownership");
        require(!inspector.matchesLiveTarget(world(), key),
                "monotonic guard rejected current-target terminal retirement "
                        + key);
        require(world().remove(key, "retire-unplanned-bare-terminal"),
                "unplanned terminal could not be removed " + key);
        PlannedTreeBlock targetAtKey = plan.blocksByKey().get(key);
        if (targetAtKey != null
                && targetAtKey.role() == TreeBlockRole.CANOPY) {
            // ## One wrong-role log removal followed by its planned leaf is
            // reconciliation, not oscillation. Further changes still fail
            // the bounded coordinate-churn invariant.
            allowedMultiMutationKeys.add(key);
        }
        require(dna().forgetEvolvedLog(worldKey(key)),
                "unplanned terminal receipt was not retired " + key);
        dna().markGrownNow();
        return TreeConstructionResult.changed(
                1, "replay.retire-unplanned-bare-terminal " + key);
    }

    private TreeConstructionResult applyTargetRepair(
            TreeTargetOwnershipRepairPolicy.Repair repair,
            String reason
    ) {
        if (repair.action()
                == TreeTargetOwnershipRepairPolicy.Action.ADOPT_LIVE_TARGET) {
            boolean adopted = world().adoptSourceBlock(
                    repair.block().key(), repair.block().role(), reason);
            boolean recorded = dna().markEvolvedBlock(
                    worldKey(repair.block().key()), repair.block().role());
            require(adopted || recorded,
                    "ownership bridge made no logical progress "
                            + repair.marker());
            dna().markGrownNow();
            return TreeConstructionResult.logicalProgress(
                    "replay." + reason + " " + repair.marker());
        }
        return applyTarget(repair.block(), reason);
    }

    @Override
    public TreeConstructionResult retireStaleEnvelopeLeaf() {
        String key = inspector.branchIntegrity(dna(), world(), true)
                .firstStaleEnvelopeLeaf()
                .orElseThrow(() -> new IllegalStateException(
                        "stale-envelope subrule had no replay target scenario="
                                + scenario));
        Cell current = world().cell(key);
        require(current != null && current.persistent(),
                "stale envelope lost its persistent-leaf marker");
        require(!inspector.matchesLiveTarget(world(), key),
                "monotonic guard rejected current-target stale-leaf retirement "
                        + key);
        require(world().remove(key, "retire-stale-envelope-leaf"),
                "stale envelope leaf could not be removed " + key);
        if (current.ownership() == Ownership.EVOLVED) {
            require(dna().forgetEvolvedLeaf(worldKey(key)),
                    "stale envelope leaf receipt was not retired " + key);
        }
        dna().markGrownNow();
        return TreeConstructionResult.changed(
                1, "replay.retire-stale-envelope-leaf " + key);
    }

    @Override
    public TreeConstructionResult repairBranchEnvelope() {
        PlannedTreeBlock target = missingCanopy(
                this::insideUncoveredTipEnvelope)
                .orElseThrow(() -> new IllegalStateException(
                        "branch envelope mini-constructor had no target scenario="
                                + scenario + " uncovered="
                                + inspector.uncoveredBranchTips(
                                        dna(), world())
                                + " " + branchEnvelopeTargetDiagnosis()));
        return applyTarget(target, "owned-branch-envelope");
    }

    private String branchEnvelopeTargetDiagnosis() {
        TreeConstructionReplayInspector.BranchIntegrity integrity =
                inspector.branchIntegrity(dna(), world(), true);
        List<String> supports = integrity.uncoveredPlannedEnvelopeKeys();
        int nearby = 0;
        int missing = 0;
        int reachable = 0;
        for (PlannedTreeBlock canopy : canopyTargets) {
            if (!insideEnvelopeOfAny(canopy, supports)) {
                continue;
            }
            nearby++;
            if (!targetSatisfied(canopy)) {
                missing++;
                reachable += canopyReachable(canopy) ? 1 : 0;
            }
        }
        String supportState = supports.stream()
                .map(key -> key + "=" + world().cell(key))
                .toList().toString();
        // ## This marker links TREE_42 to the precise support and candidate
        // counts when detector and executor disagree in a captured replay.
        return "branch-envelope-targets supports=" + supportState
                + " nearby=" + nearby
                + " missing=" + missing
                + " reachable=" + reachable;
    }

    private boolean insideEnvelopeOfAny(
            PlannedTreeBlock canopy, List<String> supports) {
        return supports.stream()
                .map(TreeConstructionReplayOperations::coordinateFromKey)
                .anyMatch(support ->
                        Math.abs(canopy.x() - support[0]) <= 2
                                && Math.abs(canopy.y() - support[1]) <= 1
                                && Math.abs(canopy.z() - support[2]) <= 2);
    }

    private static int[] coordinateFromKey(String key) {
        String[] parts = key.split(":");
        int offset = parts.length - 3;
        return new int[]{
                Integer.parseInt(parts[offset]),
                Integer.parseInt(parts[offset + 1]),
                Integer.parseInt(parts[offset + 2])
        };
    }

    @Override
    public TreeConstructionResult buildMinimumCrownShell() {
        PlannedTreeBlock target = missingCanopy(block -> true)
                .orElseThrow(() -> new IllegalStateException(
                        "minimum crown shell had no target scenario="
                                + scenario));
        return applyTarget(target, "minimum-crown-shell");
    }

    @Override
    public TreeConstructionResult buildBranchFrame() {
        PlannedTreeBlock target = targets.stream()
                .filter(block -> block.role() == TreeBlockRole.BRANCH)
                .filter(block -> !targetSatisfied(block))
                .filter(this::supportReady)
                .sorted(Comparator
                        .comparingInt(PlannedTreeBlock::branchId)
                        .thenComparingInt(PlannedTreeBlock::branchStep)
                        .thenComparingInt(PlannedTreeBlock::y))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "branch frame had no parent-linked target"));
        TreeBranchPlan branch = plan.branchPlans().stream()
                .filter(candidate -> candidate.id() == target.branchId())
                .findFirst().orElseThrow();
        TreeBranchPlan.BranchTip tip = branch.tip();
        require(TreeBranchTipIntegrityPolicy.hasPreplannedEnvelope(
                        dna(), tip.x(), tip.y(), tip.z(),
                        plan.blocksByKey()),
                "branch was selected without a preplanned leaf area");
        return applyTarget(target, "parent-linked-branch");
    }

    @Override
    public TreeConstructionResult fillCanopy() {
        PlannedTreeBlock target = missingCanopy(block -> true)
                .orElseThrow(() -> new IllegalStateException(
                        "canopy fill had no missing target scenario="
                                + scenario + " decision="
                                + decision().marker()));
        return applyTarget(target, "canopy-fill");
    }

    @Override
    public TreeConstructionResult retireDisconnectedEvolvedStructure() {
        return retireEvolved(
                inspector.disconnectedSafeRetirement(dna(), world()),
                "disconnected");
    }

    @Override
    public TreeConstructionResult retireConflictingEvolvedTarget() {
        return retireEvolved(
                inspector.conflictSafeRetirement(dna(), world()),
                "target-role-conflict");
    }

    @Override
    public TreeConstructionResult retireObsoleteEvolvedStructure() {
        return retireEvolved(
                inspector.obsoleteEvolvedTreeBlock(dna(), world()),
                "obsolete");
    }

    private TreeConstructionResult retireEvolved(
            Optional<String> selected, String route) {
        String key = selected
                .orElseThrow(() -> new IllegalStateException(
                        route + " mini-constructor had no evolved voxel"));
        Cell current = world().cell(key);
        PlannedTreeBlock plannedAtKey = plan.blocksByKey().get(key);
        require(current != null && current.ownership() == Ownership.EVOLVED,
                "obsolete evolved voxel lost ownership");
        require(!inspector.matchesLiveTarget(world(), key),
                "monotonic guard rejected current-target retirement route="
                        + route + " key=" + key);
        require(world().remove(key, "retire-obsolete-evolved"),
                "obsolete evolved voxel could not be removed");
        // ## A stale log may occupy a planned canopy coordinate. Its
        // deliberate AIR -> leaf reconciliation is not coordinate churn.
        allowedMultiMutationKeys.add(key);
        boolean forgotten = current.role() == TreeBlockRole.CANOPY
                ? dna().forgetEvolvedLeaf(worldKey(key))
                : dna().forgetEvolvedLog(worldKey(key));
        require(forgotten,
                "obsolete evolved ownership was not retired scenario="
                        + scenario + " decision=" + decision().marker()
                        + " key=" + key + " live=" + current
                        + " in-log-ledger="
                        + dna().evolvedShapeLogs().contains(worldKey(key))
                        + " in-leaf-ledger="
                        + dna().evolvedShapeLeaves().contains(worldKey(key))
                        + " target=" + (plannedAtKey == null
                                ? "none" : plannedAtKey.role())
                        + " key-history=" + world().mutations().stream()
                                .filter(line -> line.contains(key)).toList()
                        + " role-history=" + ownershipRoleHistory);
        dna().markGrownNow();
        return TreeConstructionResult.changed(
                1, "replay.retire-evolved route=" + route + " " + key
                        + " live=" + current
                        + " target=" + (plannedAtKey == null
                                ? "none"
                                : plannedAtKey.material() + "/"
                                        + plannedAtKey.role()));
    }

    @Override
    public TreeConstructionResult retireSourceCrown() {
        String key = inspector.retiredSourceLeaf(world())
                .orElseThrow(() -> new IllegalStateException(
                        "retired-crown phase had no owned source leaf"));
        require(world().remove(key, "retire-source-crown"),
                "retired source leaf could not be removed");
        require(dna().markOriginalShapeLeafRetired(worldKey(key)),
                "source leaf retirement was not recorded");
        dna().markGrownNow();
        return TreeConstructionResult.changed(
                1, "replay.retire-source-crown " + key);
    }

    @Override
    public TreeConstructionResult finalizeTransition() {
        require(dna().unresolvedOriginalShapeLeafCount() == 0,
                "transition finalized with unresolved source leaves");
        dna().completeStageCleanup();
        dna().completeStageTransition();
        state.markTransitionFinalized();
        return TreeConstructionResult.logicalProgress(
                "replay.transition-finalized");
    }

    @Override
    public TreeConstructionResult buildDetails() {
        return TreeConstructionResult.idle("replay.details-not-requested");
    }

    @Override
    public TreeConstructionResult complete() {
        return TreeConstructionResult.idle("replay.stage-complete");
    }

    private Optional<PlannedTreeBlock> adaptiveExposedSupportLeaf() {
        int topY = targets.stream()
                .filter(block -> block.role() == TreeBlockRole.TRUNK
                        && targetSatisfied(block))
                .mapToInt(PlannedTreeBlock::y)
                .max().orElse(Integer.MIN_VALUE);
        if (topY == Integer.MIN_VALUE) {
            return Optional.empty();
        }
        for (PlannedTreeBlock trunk : targets.stream()
                .filter(block -> block.role() == TreeBlockRole.TRUNK
                        && block.y() == topY
                        && targetSatisfied(block)
                        && !hasDirectEvolvedLeaf(
                                block.x(), block.y(), block.z()))
                .toList()) {
            for (int[] offset : NEIGHBORS) {
                int x = trunk.x() + offset[0];
                int y = trunk.y() + offset[1];
                int z = trunk.z() + offset[2];
                String key = key(x, y, z);
                Cell current = world().cell(key);
                if (current != null) {
                    boolean adoptableSourceLeaf =
                            current.ownership() == Ownership.SOURCE
                                    && current.material()
                                            == dna().species()
                                                    .leafMaterial();
                    if (!adoptableSourceLeaf) {
                        continue;
                    }
                }
                PlannedTreeBlock repair = new PlannedTreeBlock(
                        x, y, z, dna().species().leafMaterial(),
                        TreeBlockRole.CANOPY, Axis.Y, null);
                plan.add(repair);
                return Optional.of(repair);
            }
        }
        return Optional.empty();
    }

    private boolean hasDirectEvolvedLeaf(int x, int y, int z) {
        for (int[] offset : NEIGHBORS) {
            Cell cell = world().cell(key(
                    x + offset[0], y + offset[1], z + offset[2]));
            if (cell != null
                    && cell.ownership() == Ownership.EVOLVED
                    && cell.material()
                            == dna().species().leafMaterial()) {
                return true;
            }
        }
        return false;
    }

    private TreeConstructionResult applyTarget(
            PlannedTreeBlock target, String reason) {
        String labeledReason = reason + " augment=" + target.augment()
                + " expects=" + target.augment().expectedRoleLabel()
                + " constructor=" + decision().smokeTag();
        Cell current = world().cell(target.key());
        boolean changed;
        if (current != null
                && current.ownership() == Ownership.SOURCE
                && current.material() == target.material()
                && target.role() == TreeBlockRole.CANOPY) {
            boolean ownershipChanged = world().adoptSourceLeaf(
                    target.key(), TreeBlockRole.CANOPY, labeledReason);
            boolean receiptChanged =
                    dna().markEvolvedLeaf(worldKey(target.key()));
            // ## Either ownership adoption or a new DNA receipt is logical
            // progress. An existing receipt must not erase a successful
            // source-to-evolved ownership transition.
            changed = ownershipChanged || receiptChanged;
        } else {
            if (current != null
                    && current.ownership() == Ownership.SOURCE
                    && current.material().name().endsWith("_LEAVES")) {
                dna().markOriginalShapeLeafRetired(worldKey(target.key()));
            }
            changed = world().place(
                    target.key(), target.material(), target.role(),
                    Ownership.EVOLVED, labeledReason);
            if (target.role() == TreeBlockRole.CANOPY) {
                dna().markEvolvedLeaf(worldKey(target.key()));
            } else {
                dna().markEvolvedBlock(
                        worldKey(target.key()), target.role());
            }
        }
        if (!changed) {
            TreeSimulatedMinecraftEnvironment.GateDecision gate =
                    world().environment().canPlace(
                            target.key(), target.material(),
                            target.role(), current);
            throw new IllegalStateException(
                    "target action made no logical progress scenario="
                            + scenario + " at=" + target.key()
                            + " target=" + target.material() + "/"
                            + target.role() + " current=" + current
                            + " gate=" + gate.gate() + "/"
                            + gate.detail() + " decision="
                            + decision().marker());
        }
        dna().markGrownNow();
        return TreeConstructionResult.changed(
                1, "replay." + labeledReason + " " + target.key());
    }

    private Optional<PlannedTreeBlock> missingCanopy(
            Predicate<PlannedTreeBlock> preferred) {
        TreeCanopyGrowthBalancePolicy balance =
                TreeCanopyGrowthBalancePolicy.from(dna());
        return canopyTargets.stream()
                .filter(block -> !targetSatisfied(block))
                .filter(preferred)
                .filter(this::canopyReachable)
                .sorted(Comparator
                        // ## Match live crown balancing before coordinate ties.
                        .comparingInt(balance::load)
                        .thenComparingInt(this::canopyDistanceToSupport)
                        .thenComparing(
                                Comparator.comparingInt(
                                        PlannedTreeBlock::y).reversed())
                        .thenComparing(PlannedTreeBlock::key))
                .findFirst();
    }

    private boolean canopyReachable(PlannedTreeBlock canopy) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    Cell neighbor = world().cell(key(
                            canopy.x() + dx,
                            canopy.y() + dy,
                            canopy.z() + dz));
                    if (neighbor == null
                            || neighbor.ownership()
                                    == Ownership.NEIGHBOR) {
                        continue;
                    }
                    if (neighbor.role() == TreeBlockRole.TRUNK
                            || neighbor.role() == TreeBlockRole.BRANCH
                            || (neighbor.role() == TreeBlockRole.CANOPY
                                    && neighbor.ownership()
                                            == Ownership.EVOLVED)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int canopyDistanceToSupport(PlannedTreeBlock canopy) {
        return targets.stream()
                .filter(this::isStructural)
                .mapToInt(block ->
                        Math.abs(block.x() - canopy.x())
                                + Math.abs(block.y() - canopy.y())
                                + Math.abs(block.z() - canopy.z()))
                .min().orElse(99);
    }

    private boolean touchesExposedTopSupport(PlannedTreeBlock canopy) {
        int topY = targets.stream()
                .filter(block -> block.role() == TreeBlockRole.TRUNK
                        && targetSatisfied(block))
                .mapToInt(PlannedTreeBlock::y)
                .max().orElse(Integer.MIN_VALUE);
        return targets.stream().anyMatch(block ->
                block.role() == TreeBlockRole.TRUNK
                        && block.y() == topY
                        && targetSatisfied(block)
                        && adjacent(block, canopy));
    }

    private boolean insideUncoveredTipEnvelope(
            PlannedTreeBlock canopy) {
        return inspector.insideUncoveredTipEnvelope(
                dna(), world(), canopy);
    }

    private boolean targetSatisfied(PlannedTreeBlock target) {
        return inspector.targetSatisfied(dna(), world(), target);
    }

    private boolean supportReady(PlannedTreeBlock block) {
        return inspector.supportReady(dna(), world(), block);
    }

    private boolean adjacent(
            PlannedTreeBlock first, PlannedTreeBlock second) {
        return Math.abs(first.x() - second.x())
                + Math.abs(first.y() - second.y())
                + Math.abs(first.z() - second.z()) == 1;
    }

    private boolean isStructural(PlannedTreeBlock block) {
        return block.role() == TreeBlockRole.TRUNK
                || block.role() == TreeBlockRole.BRANCH;
    }

    private TreeTargetOwnershipRepairPolicy.Analysis
            targetOwnershipAnalysis() {
        Set<String> liveOwned = new java.util.HashSet<>();
        Set<String> receipts = new java.util.HashSet<>(
                dna().originalShapeLogs());
        receipts.addAll(dna().evolvedShapeLogs());
        for (String receipt : receipts) {
            String coordinate = coordinateKey(receipt);
            Cell cell = world().cell(coordinate);
            if (cell != null && isStructuralRole(cell.role())) {
                liveOwned.add(receipt);
            }
        }
        Set<String> livePlanned = new java.util.HashSet<>();
        Set<String> liveOwnedCanopy = new java.util.HashSet<>();
        for (String receipt : dna().evolvedShapeLeaves()) {
            Cell cell = world().cell(coordinateKey(receipt));
            if (cell != null
                    && cell.role() == TreeBlockRole.CANOPY
                    && cell.ownership() != Ownership.NEIGHBOR) {
                liveOwnedCanopy.add(receipt);
            }
        }
        Set<String> livePlannedCanopy = new java.util.HashSet<>();
        Set<String> protectedCoordinates = new java.util.HashSet<>();
        for (PlannedTreeBlock block : targets) {
            if (!isStructural(block)
                    && block.role() != TreeBlockRole.CANOPY) {
                continue;
            }
            Cell cell = world().cell(block.key());
            if (cell != null && cell.material() == block.material()) {
                if (isStructural(block)) {
                    livePlanned.add(worldKey(block.key()));
                } else {
                    livePlannedCanopy.add(worldKey(block.key()));
                }
                if (cell.ownership() == Ownership.NEIGHBOR) {
                    protectedCoordinates.add(block.key());
                }
            }
        }
        return TreeTargetOwnershipRepairPolicy.inspect(
                dna(), targets, liveOwned, livePlanned,
                liveOwnedCanopy, livePlannedCanopy,
                protectedCoordinates);
    }

    private static boolean isStructuralRole(TreeBlockRole role) {
        return role == TreeBlockRole.TRUNK
                || role == TreeBlockRole.BRANCH
                || role == TreeBlockRole.ROOT;
    }

    private static String coordinateKey(String worldKey) {
        String[] parts = worldKey.split(":");
        int offset = parts.length - 3;
        return parts[offset] + ":" + parts[offset + 1]
                + ":" + parts[offset + 2];
    }

    private TreeDna dna() {
        return state.dna();
    }

    private TreeConstructionReplayWorld world() {
        return state.world();
    }

    private TreeConstructionDecision decision() {
        return state.decision();
    }

    private String worldKey(String coordinateKey) {
        return dna().worldId() + ":" + coordinateKey;
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    interface State {
        TreeDna dna();

        TreeConstructionReplayWorld world();

        TreeConstructionDecision decision();

        void markTransitionFinalized();
    }
}
