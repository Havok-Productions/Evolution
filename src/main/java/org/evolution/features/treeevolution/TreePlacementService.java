package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Leaves;
import org.evolution.coreparts.EvolutionPlugin;

/**
 * ## Owns constructor target selection and Folia-safe world placement.
 *
 * <p>Hierarchy order is dependency gate, protection/natural-target gate,
 * deterministic shape choice, then one attached world action. Branch and canopy
 * repair use the same ownership-aware placement rules as ordinary growth.</p>
 */
final class TreePlacementService {
    private static final int WOOD_SUPPORT_RADIUS = 2;
    private static final List<BlockFace> NEIGHBORS = List.of(
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
    private final EvolutionPlugin plugin;
    private final TreeEvolutionDiagnostics diagnostics;
    private final TreeDnaRepository repository;
    private final TreePlanAuditService planAudit;
    private final TreeMaturityService maturityService;
    private final TreeGroundDetailPolicy groundDetailPolicy;
    private final AtomicLong changedBlocks;
    private final Set<Material> naturalGround;
    private final Set<Material> naturalDetails;
    private final TreeShapeEngine shapeEngine = new TreeShapeEngine();

    TreePlacementService(
            EvolutionPlugin plugin,
            TreeEvolutionDiagnostics diagnostics,
            TreeDnaRepository repository,
            TreePlanAuditService planAudit,
            TreeMaturityService maturityService,
            TreeGroundDetailPolicy groundDetailPolicy,
            AtomicLong changedBlocks,
            Set<Material> naturalGround,
            Set<Material> naturalDetails
    ) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
        this.repository = repository;
        this.planAudit = planAudit;
        this.maturityService = maturityService;
        this.groundDetailPolicy = groundDetailPolicy;
        this.changedBlocks = changedBlocks;
        this.naturalGround = Set.copyOf(naturalGround);
        this.naturalDetails = Set.copyOf(naturalDetails);
    }
    Optional<PlannedTarget> nextPlannedTarget(
            TreeCandidate candidate, TreeDna dna, CachedTreePlan cachedPlan,
            TreeGrowthIntent intent, TreeEvolutionConfig currentConfig) {
        List<PlannedTreeBlock> orderedBlocks = cachedPlan.orderedBlocks();
        Map<String, PlannedTreeBlock> blocksByKey = cachedPlan.blocksByKey();
        if (orderedBlocks.isEmpty()) {
            return Optional.empty();
        }
        int size = orderedBlocks.size();
        int start = Math.floorMod(dna.planCursor(), size);
        List<TreeShapeEngine.ShapeChoice> choices = new ArrayList<>();
        List<CandidateBlock> intentBlocks = new ArrayList<>();
        int nextHeight = candidate.topY() + 1;
        int liveTop = Math.max(candidate.topY(), dna.baseY() + maturityService.liveTrunkHeight(candidate.world(), dna) - 1);
        for (int checked = 0; checked < size; checked++) {
            int index = (start + checked) % size;
            PlannedTreeBlock plannedBlock = orderedBlocks.get(index);
            if (TreeGrowthIntentPolicy.matches(dna, plannedBlock, intent)) {
                int priority = dependencyPriority(dna, plannedBlock, intent, nextHeight, liveTop);
                intentBlocks.add(new CandidateBlock(plannedBlock, index, priority));
            }
        }
        intentBlocks.sort(growthOrder(candidate, dna));
        int dependencyWaits = 0;
        int placementRejects = 0;
        int checkedTargets = 0;
        int targetBudget = currentConfig.testingEnabled() ? 512 : 256;
        for (CandidateBlock candidateBlock : intentBlocks) {
            if (++checkedTargets > targetBudget && choices.isEmpty()) {
                plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.search-budget-stop",
                        "intent=" + intent + " checked=" + checkedTargets + " rejects=" + placementRejects
                                + " waits=" + dependencyWaits + " tree=" + dna.key()
                                + " ## bounded planner scan protects Folia region ticks; retry continues next cycle");
                return Optional.empty();
            }
            PlannedTreeBlock plannedBlock = candidateBlock.block();
            if (!hasPreplannedBranchEnvelope(
                    dna, plannedBlock, cachedPlan, currentConfig)) {
                placementRejects++;
                continue;
            }
            Block target = targetBlockFor(candidate.world(), plannedBlock);
            if (planAudit.isSatisfiedPlannedBlock(dna, plannedBlock, target)) {
                continue;
            }
            if (!isDependencyReady(candidate, dna, target, plannedBlock, intent, currentConfig, dependencyWaits < 8)) {
                Optional<PlannedTreeBlock> repairBlock = branchParentRepairBlock(candidate, dna, plannedBlock, blocksByKey, currentConfig);
                if (repairBlock.isPresent()) {
                    PlannedTreeBlock repair = repairBlock.get();
                    Block repairTarget = targetBlockFor(candidate.world(), repair);
                    if (repairTarget.getType() != repair.material()
                            && isDependencyReady(candidate, dna, repairTarget, repair, intent, currentConfig, false)
                            && canPlace(candidate, dna, repairTarget, repair, currentConfig)) {
                        int originalIndex = Math.max(0, orderedBlocks.indexOf(repair));
                        choices.add(shapeEngine.score(candidate, dna, repair, repairTarget, intent, (originalIndex + 1) % size));
                        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.parent-repair-choice",
                                "child=" + plannedBlock.branchId() + ":" + plannedBlock.branchStep()
                                        + " repair-role=" + repair.role()
                                        + " repair=" + format(repairTarget)
                                        + " ## branch parent repair grows missing planned wood before retrying the child segment");
                        if (shapeEngine.hasEnoughChoices(choices)) {
                            break;
                        }
                    }
                }
                dependencyWaits++;
                continue;
            }
            if (!canPlace(candidate, dna, target, plannedBlock, currentConfig)) {
                placementRejects++;
                if (placementRejects >= 96 && choices.isEmpty()) {
                    plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.search-budget-stop",
                            "intent=" + intent + " rejects=" + placementRejects + " waits=" + dependencyWaits
                                    + " tree=" + dna.key());
                    return Optional.empty();
                }
                continue;
            }
            choices.add(shapeEngine.score(candidate, dna, plannedBlock, target, intent, (candidateBlock.index() + 1) % size));
            if (shapeEngine.hasEnoughChoices(choices)) {
                break;
            }
        }
        TreeShapeEngine.ShapeChoice best = shapeEngine.bestChoice(choices);
        if (best == null) {
            if (dependencyWaits > 0) {
                plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.waiting-dependencies",
                        "intent=" + intent + " waits=" + dependencyWaits + " tree=" + dna.key()
                                + " ## dependency builder is waiting for trunk/parent branch blocks instead of forcing floating wood");
            }
            return Optional.empty();
        }
        diagnostics.recordShapeChoice(currentConfig, dna, best.reason(), choices.size());
        return Optional.of(new PlannedTarget(best.block(), best.target(), best.nextCursor(), best.score(), best.reason()));
    }

    private Comparator<CandidateBlock> growthOrder(TreeCandidate candidate, TreeDna dna) {
        return Comparator
                .comparingInt(CandidateBlock::priority)
                .thenComparingInt(block -> block.block().branchId() < 0 ? Integer.MAX_VALUE : block.block().branchId())
                .thenComparingInt(block -> block.block().branchStep() < 0 ? Integer.MAX_VALUE : block.block().branchStep())
                .thenComparingInt(block -> Math.abs(block.block().y() - (candidate.topY() + 1)))
                .thenComparingInt(block -> block.block().y())
                .thenComparingInt(block -> block.block().x())
                .thenComparingInt(block -> block.block().z());
    }

    private int dependencyPriority(TreeDna dna, PlannedTreeBlock block, TreeGrowthIntent intent, int nextHeight, int liveTop) {
        return switch (block.role()) {
            case TRUNK -> {
                int verticalDistance = Math.abs(block.y() - nextHeight);
                int horizontal = Math.abs(block.x() - dna.trunkXAt(block.y())) + Math.abs(block.z() - dna.trunkZAt(block.y()));
                yield (verticalDistance * 12) + horizontal;
            }
            case BRANCH -> (Math.max(0, block.branchStep()) * 20) + Math.max(0, block.branchId());
            case CANOPY -> {
                int topY = dna.baseY() + TreeSpeciesStageStyle.visibleHeight(dna) - 1;
                int topDistance = Math.min(Math.abs(block.y() - topY), Math.abs(block.y() - liveTop));
                int horizontal = Math.max(Math.abs(block.x() - dna.trunkXAt(Math.min(topY, block.y()))), Math.abs(block.z() - dna.trunkZAt(Math.min(topY, block.y()))));
                yield (topDistance * 8) + horizontal;
            }
            case VINE, GROUND_DETAIL, FALLEN_LOG, SAPLING, ROOT -> 1000 + block.y();
        };
    }

    boolean isDependencyReady(TreeCandidate candidate, TreeDna dna, Block target, PlannedTreeBlock plannedBlock, TreeGrowthIntent intent, TreeEvolutionConfig currentConfig, boolean logWait) {
        return switch (plannedBlock.role()) {
            case TRUNK -> isTrunkDependencyReady(candidate, dna, target, plannedBlock, currentConfig, logWait);
            case BRANCH -> isBranchDependencyReady(candidate, dna, target, plannedBlock, currentConfig, logWait);
            case CANOPY -> isCanopyDependencyReady(candidate, dna, target, plannedBlock, currentConfig, logWait);
            case ROOT, VINE, GROUND_DETAIL, FALLEN_LOG, SAPLING -> true;
        };
    }

    private boolean isTrunkDependencyReady(TreeCandidate candidate, TreeDna dna, Block target, PlannedTreeBlock plannedBlock, TreeEvolutionConfig currentConfig, boolean logWait) {
        int liveTop = dna.baseY() + maturityService.liveTrunkHeight(candidate.world(), dna) - 1;
        if (target.getY() > liveTop + 1) {
            if (logWait) {
                diagnostics.recordReject(currentConfig, "trunk-waiting-parent",
                        "role=TRUNK at " + format(target) + " live-top=" + liveTop
                                + " ## trunk spine grows one connected layer before higher tree pieces are allowed");
                plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.trunk-spine-wait",
                        plannedBlock.material() + " at " + format(target) + " live-top=" + liveTop);
            }
            return false;
        }
        return true;
    }

    private boolean isBranchDependencyReady(TreeCandidate candidate, TreeDna dna, Block target, PlannedTreeBlock plannedBlock, TreeEvolutionConfig currentConfig, boolean logWait) {
        if (!plannedBlock.hasBranchPath()) {
            return hasWoodSupportNearby(candidate, dna, target, plannedBlock, currentConfig, logWait);
        }
        Block parent = candidate.world().getBlockAt(plannedBlock.parentX(), plannedBlock.parentY(), plannedBlock.parentZ());
        if (isWoodSupport(parent.getType())) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.segment-ready",
                    "branch=" + plannedBlock.branchId() + " step=" + plannedBlock.branchStep()
                            + " parent=" + plannedBlock.parentX() + "," + plannedBlock.parentY() + "," + plannedBlock.parentZ()
                            + " target=" + format(target));
            return true;
        }
        if (logWait) {
            diagnostics.recordReject(currentConfig, "branch-waiting-parent",
                    "branch=" + plannedBlock.branchId() + " step=" + plannedBlock.branchStep()
                            + " target=" + format(target)
                            + " parent=" + plannedBlock.parentX() + "," + plannedBlock.parentY() + "," + plannedBlock.parentZ()
                            + " parent-type=" + parent.getType()
                            + " ## branch path waits for its exact trunk/branch parent instead of floating");
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.waiting-parent",
                    "branch=" + plannedBlock.branchId() + " step=" + plannedBlock.branchStep()
                            + " target=" + format(target) + " parent-type=" + parent.getType());
        }
        return false;
    }

    boolean hasPreplannedBranchEnvelope(
            TreeDna dna, PlannedTreeBlock plannedBlock, CachedTreePlan cachedPlan,
            TreeEvolutionConfig currentConfig) {
        if (plannedBlock.role() != TreeBlockRole.BRANCH) {
            return true;
        }
        Optional<TreeBranchPlan.BranchTip> tip = cachedPlan.plan().branchPlans().stream()
                .filter(branch -> branch.id() == plannedBlock.branchId())
                .map(TreeBranchPlan::tip)
                .findFirst();
        boolean valid = tip.isPresent()
                && TreeBranchTipIntegrityPolicy.hasPreplannedEnvelope(
                        dna, tip.get().x(), tip.get().y(), tip.get().z(),
                        cachedPlan.blocksByKey());
        if (!valid) {
            diagnostics.recordReject(currentConfig, "branch-envelope-unplanned",
                    dna.key() + " branch=" + plannedBlock.branchId()
                            + " step=" + plannedBlock.branchStep());
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "blocked.branch-envelope-unplanned",
                    "tree=" + dna.key()
                            + " branch=" + plannedBlock.branchId()
                            + " step=" + plannedBlock.branchStep()
                            + " tip=" + tip.map(value -> value.x() + "," + value.y() + "," + value.z())
                                    .orElse("missing")
                            + " ## branch wood cannot form until its connected leaf envelope exists in the target plan");
        }
        return valid;
    }

    private Optional<PlannedTreeBlock> branchParentRepairBlock(
            TreeCandidate candidate,
            TreeDna dna,
            PlannedTreeBlock blockedBranch,
            Map<String, PlannedTreeBlock> blocksByKey,
            TreeEvolutionConfig currentConfig
    ) {
        if (blockedBranch.role() != TreeBlockRole.BRANCH || !blockedBranch.hasBranchPath()) {
            return Optional.empty();
        }
        Block parent = candidate.world().getBlockAt(blockedBranch.parentX(), blockedBranch.parentY(), blockedBranch.parentZ());
        if (isWoodSupport(parent.getType())) {
            return Optional.empty();
        }
        if (!currentConfig.isReplaceable(parent.getType()) && !isNaturalTarget(dna, parent, blockedBranch)) {
            diagnostics.recordReject(currentConfig, "branch-parent-player-block",
                    "branch=" + blockedBranch.branchId() + " step=" + blockedBranch.branchStep()
                            + " parent=" + format(parent) + " parent-type=" + parent.getType());
            return Optional.empty();
        }
        PlannedTreeBlock planned = blocksByKey.get(blockedBranch.parentKey());
        if (planned != null && (planned.role() == TreeBlockRole.TRUNK || planned.role() == TreeBlockRole.BRANCH)) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.parent-repair",
                    "branch=" + blockedBranch.branchId() + " step=" + blockedBranch.branchStep()
                            + " parent-type=" + parent.getType()
                            + " repair-role=" + planned.role()
                            + " at " + format(parent)
                            + " ## cached parent lookup repairs planned wood without rescanning the whole tree plan");
            return Optional.of(planned);
        }
        diagnostics.recordReject(currentConfig, "branch-parent-missing-plan",
                "branch=" + blockedBranch.branchId() + " step=" + blockedBranch.branchStep()
                        + " parent=" + format(parent) + " parent-type=" + parent.getType());
        return Optional.empty();
    }

    private boolean isCanopyDependencyReady(TreeCandidate candidate, TreeDna dna, Block target, PlannedTreeBlock plannedBlock, TreeEvolutionConfig currentConfig, boolean logWait) {
        if (hasTreeSupportNearby(target, 2)) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.tip-canopy",
                    "leaf=" + plannedBlock.material() + " supported-near-live-tree at " + format(target));
            return true;
        }
        if (isUpperCanopyCloudTarget(candidate, dna, target, plannedBlock)) {
            return true;
        }
        int liveTop = dna.baseY() + maturityService.liveTrunkHeight(candidate.world(), dna) - 1;
        int vertical = Math.abs(target.getY() - liveTop);
        int horizontal = Math.max(Math.abs(target.getX() - dna.trunkXAt(liveTop)), Math.abs(target.getZ() - dna.trunkZAt(liveTop)));
        int softRadius = switch (dna.maturityStage()) {
            case SMALL -> 2;
            case MEDIUM -> 3;
            case MATURE -> 4;
            case ANCIENT -> dna.hugeArchitecture() ? 5 : 4;
        };
        if (vertical <= 2 && horizontal <= softRadius && liveTop >= dna.baseY() + Math.max(2, TreeSpeciesStageStyle.visibleHeight(dna) - 3)) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "gate.canopy-cloud-soft-pass",
                    plannedBlock.material() + " at " + format(target)
                            + " live-top=" + liveTop + " horizontal=" + horizontal + "/" + softRadius
                            + " ## upper crown cloud allowed to cover exposed growing trunk");
            return true;
        }
        if (logWait) {
            diagnostics.recordReject(currentConfig, "canopy-waiting-support",
                    "role=CANOPY material=" + plannedBlock.material() + " at " + format(target)
                            + " live-top=" + liveTop + " horizontal=" + horizontal + " vertical=" + vertical
                            + " ## canopy waits for nearby live wood/leaves or the active upper crown cloud");
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "branch.canopy-waiting-support",
                    plannedBlock.material() + " delayed at " + format(target)
                            + " live-top=" + liveTop + " horizontal=" + horizontal + " vertical=" + vertical);
        }
        return false;
    }

    boolean canPlace(TreeCandidate candidate, TreeDna dna, Block target, PlannedTreeBlock plannedBlock, TreeEvolutionConfig currentConfig) {
        if (target.getY() < target.getWorld().getMinHeight() || target.getY() >= target.getWorld().getMaxHeight()) {
            diagnostics.recordReject(currentConfig, "height-limit", format(target));
            return false;
        }
        if (!plugin.canEvolveAt(target.getLocation(), "tree-evolution")) {
            diagnostics.recordReject(currentConfig, "worldguard", format(target));
            return false;
        }
        int chunkX = target.getX() >> 4;
        int chunkZ = target.getZ() >> 4;
        if (!target.getWorld().isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(target.getWorld(), chunkX, chunkZ, currentConfig.ownedChunkRadius())) {
            diagnostics.recordReject(currentConfig, "chunk-or-region", format(target));
            return false;
        }
        if (target.isLiquid()) {
            diagnostics.recordReject(currentConfig, "liquid", format(target));
            return false;
        }
        if (isStructuralWoodRole(plannedBlock.role())
                && isWoodSupport(target.getType())
                && !dna.countsAsOwnedLog(keyFor(target))) {
            diagnostics.recordReject(currentConfig, "foreign-tree-wood",
                    target.getType() + " at " + format(target));
            plugin.pathDebug().traceSampled(plugin, "tree-evolution",
                    "gate.foreign-tree-wood",
                    "tree=" + dna.key() + " role=" + plannedBlock.role()
                            + " occupied=" + format(target)
                            + " ## neighboring wood is immutable and cannot be claimed or replaced by this model");
            return false;
        }
        if (plannedBlock.role() == TreeBlockRole.CANOPY && isWoodSupport(target.getType())) {
            diagnostics.recordReject(currentConfig, "canopy-occupied-by-tree-wood",
                    target.getType() + " at " + format(target)
                            + " ## live tree wood already owns this position, so canopy skips instead of blaming player blocks");
            return false;
        }
        if (!currentConfig.isReplaceable(target.getType()) && target.getType() != plannedBlock.material()) {
            if (isLowerTrunkNaturalGroundTarget(dna, target, plannedBlock)) {
                diagnostics.recordReject(currentConfig, "trunk-natural-ground-absorb",
                        target.getType() + " at " + format(target)
                                + " ## lower trunk may absorb natural ground so wide/ancient trunks finish their foundation");
            } else {
                diagnostics.recordReject(currentConfig, "player-block", target.getType() + " at " + format(target));
                return false;
            }
        }
        if (!candidate.naturalKeys().contains(keyFor(target)) && !isNaturalTarget(dna, target, plannedBlock)) {
            diagnostics.recordReject(currentConfig, "not-natural-target", target.getType() + " at " + format(target));
            return false;
        }
        if (plannedBlock.role() == TreeBlockRole.GROUND_DETAIL) {
            Material material = groundDetailPolicy.adjust(target, plannedBlock.material());
            Block ground = target.getRelative(BlockFace.DOWN);
            if (!naturalGround.contains(ground.getType()) || !currentConfig.isReplaceable(target.getType())) {
                return false;
            }
            if (material == Material.SUGAR_CANE && !groundDetailPolicy.hasAdjacentWater(ground)) {
                diagnostics.recordReject(currentConfig, "sugar-cane-water", format(target));
                return false;
            }
            if ((material == Material.PUMPKIN || material == Material.MELON) && target.getLightFromSky() < 9) {
                diagnostics.recordReject(currentConfig, "rare-feature-light", material + " at " + format(target));
                return false;
            }
            if (groundDetailPolicy.isRareGroundFeature(material) && groundDetailPolicy.countNearbyRareGroundFeatures(target, 10) >= 2) {
                diagnostics.recordReject(currentConfig, "rare-feature-density", material + " at " + format(target));
                return false;
            }
            if (groundDetailPolicy.countNearbyGroundDetails(target, 5) >= 18) {
                diagnostics.recordReject(currentConfig, "detail-density", format(target));
                return false;
            }
            if (groundDetailPolicy.isFlowerLike(material) && groundDetailPolicy.countNearbyFlowers(target, 6) >= 4) {
                diagnostics.recordReject(currentConfig, "flower-density", format(target));
                return false;
            }
            return true;
        }
        if (plannedBlock.role() == TreeBlockRole.SAPLING) {
            return target.getType().isAir() && naturalGround.contains(target.getRelative(BlockFace.DOWN).getType());
        }
        if (plannedBlock.role() == TreeBlockRole.FALLEN_LOG) {
            return currentConfig.isReplaceable(target.getType()) && naturalGround.contains(target.getRelative(BlockFace.DOWN).getType());
        }
        if (plannedBlock.role() == TreeBlockRole.VINE) {
            return target.getType().isAir() && plannedBlock.supportFace() != null
                    && isLogOrLeaf(target.getRelative(plannedBlock.supportFace()).getType());
        }
        if (plannedBlock.role() == TreeBlockRole.ROOT) {
            return naturalGround.contains(target.getRelative(BlockFace.DOWN).getType()) || currentConfig.isReplaceable(target.getType());
        }
        if (plannedBlock.role() == TreeBlockRole.TRUNK) {
            if (!hasWoodSupportNearby(candidate, dna, target, plannedBlock, currentConfig, true)) {
                return false;
            }
            return true;
        }
        if (plannedBlock.role() == TreeBlockRole.CANOPY) {
            // ## Dependency gate already proved live tree support or active upper-crown cloud eligibility.
            // Keep canPlace focused on world/player/natural safety so the older radius check does not choke canopy fill.
            return true;
        }
        if (plannedBlock.role() == TreeBlockRole.BRANCH) {
            if (!hasWoodSupportNearby(candidate, dna, target, plannedBlock, currentConfig, true)) {
                return false;
            }
            return true;
        }
        return true;
    }

    private boolean isNaturalTarget(TreeDna dna, Block target, PlannedTreeBlock plannedBlock) {
        if (target.getType().isAir() || naturalDetails.contains(target.getType()) || target.getType().name().endsWith("_LEAVES")) {
            return true;
        }
        if (isLowerTrunkNaturalGroundTarget(dna, target, plannedBlock)) {
            return true;
        }
        if (plannedBlock.role() == TreeBlockRole.GROUND_DETAIL) {
            return naturalGround.contains(target.getRelative(BlockFace.DOWN).getType());
        }
        if (plannedBlock.role() == TreeBlockRole.ROOT) {
            return naturalGround.contains(target.getRelative(BlockFace.DOWN).getType());
        }
        if (plannedBlock.role() == TreeBlockRole.SAPLING || plannedBlock.role() == TreeBlockRole.FALLEN_LOG) {
            return naturalGround.contains(target.getRelative(BlockFace.DOWN).getType());
        }
        return false;
    }

    private boolean isLowerTrunkNaturalGroundTarget(TreeDna dna, Block target, PlannedTreeBlock plannedBlock) {
        if (plannedBlock.role() != TreeBlockRole.TRUNK || !naturalGround.contains(target.getType())) {
            return false;
        }
        int vertical = target.getY() - dna.baseY();
        if (vertical < -1 || vertical > 2) {
            return false;
        }
        int trunkWidth = Math.max(1, TreeSpeciesStageStyle.trunkWidthAt(dna, target.getY()));
        int horizontal = Math.max(Math.abs(target.getX() - dna.trunkXAt(target.getY())), Math.abs(target.getZ() - dna.trunkZAt(target.getY())));
        return horizontal <= Math.max(1, trunkWidth / 2 + 1);
    }

    Block targetBlockFor(World world, PlannedTreeBlock plannedBlock) {
        if (plannedBlock.role() == TreeBlockRole.GROUND_DETAIL
                || plannedBlock.role() == TreeBlockRole.FALLEN_LOG
                || plannedBlock.role() == TreeBlockRole.SAPLING) {
            return surfaceDetailTarget(world, plannedBlock.x(), plannedBlock.z());
        }
        return world.getBlockAt(plannedBlock.x(), plannedBlock.y(), plannedBlock.z());
    }

    private Block surfaceDetailTarget(World world, int x, int z) {
        Block highest = world.getHighestBlockAt(x, z);
        for (int y = highest.getY(); y > world.getMinHeight(); y--) {
            Block ground = world.getBlockAt(x, y, z);
            if (!naturalGround.contains(ground.getType())) {
                continue;
            }
            Block target = ground.getRelative(BlockFace.UP);
            if (!target.isLiquid()) {
                return target;
            }
        }
        return highest.getType().isAir() ? highest : highest.getRelative(BlockFace.UP);
    }

    private boolean hasTreeSupportNearby(Block center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) + Math.abs(y) + Math.abs(z) > radius + 1) {
                        continue;
                    }
                    Material type = center.getRelative(x, y, z).getType();
                    if (isLogOrLeaf(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasWoodSupportNearby(TreeCandidate candidate, TreeDna dna, Block target, PlannedTreeBlock plannedBlock, TreeEvolutionConfig currentConfig, boolean logReject) {
        if (plannedBlock.role() == TreeBlockRole.TRUNK && target.getY() <= candidate.baseBlock().getY()) {
            return naturalGround.contains(target.getRelative(BlockFace.DOWN).getType())
                    || isLogOrLeaf(target.getRelative(BlockFace.DOWN).getType());
        }
        if (plannedBlock.role() == TreeBlockRole.TRUNK) {
            boolean supported = hasDirectWoodNeighbor(target, plannedBlock.material());
            if (!supported && logReject) {
                recordSupportReject(currentConfig, plannedBlock, target, "trunk-strict", 1);
            }
            return supported;
        }

        if (hasDirectWoodNeighbor(target, plannedBlock.material())) {
            return true;
        }

        if (plannedBlock.role() == TreeBlockRole.BRANCH && isFirstBranchSegment(dna, target)) {
            if (logReject) {
                recordSupportReject(currentConfig, plannedBlock, target, "first-branch-needs-touching-wood", 1);
            }
            return false;
        }

        int radius = supportRadius(dna, plannedBlock.role());
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    int manhattan = Math.abs(x) + Math.abs(y) + Math.abs(z);
                    if (manhattan > radius + 1) {
                        continue;
                    }
                    Material material = target.getRelative(x, y, z).getType();
                    if (material == plannedBlock.material() || isWoodSupport(material)) {
                        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "gate.support-path-pass",
                                plannedBlock.role() + " " + plannedBlock.material() + " at " + format(target)
                                        + " support-offset=" + x + "," + y + "," + z);
                        return true;
                    }
                }
            }
        }
        if (logReject) {
            recordSupportReject(currentConfig, plannedBlock, target, "branch-path-too-far", radius);
        }
        return false;
    }

    private boolean hasDirectWoodNeighbor(Block target, Material plannedMaterial) {
        for (BlockFace face : NEIGHBORS) {
            Block neighbor = target.getRelative(face);
            if (neighbor.getType() == plannedMaterial || isWoodSupport(neighbor.getType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isFirstBranchSegment(TreeDna dna, Block target) {
        int y = target.getY();
        int horizontal = Math.abs(target.getX() - dna.trunkXAt(y)) + Math.abs(target.getZ() - dna.trunkZAt(y));
        return horizontal <= Math.max(1, TreeSpeciesStageStyle.trunkWidthAt(dna, y) / 2 + 1);
    }

    private int supportRadius(TreeDna dna, TreeBlockRole role) {
        if (role == TreeBlockRole.BRANCH) {
            return switch (dna.maturityStage()) {
                case SMALL, MEDIUM -> 3;
                case MATURE -> 3;
                case ANCIENT -> 4;
            };
        }
        return WOOD_SUPPORT_RADIUS;
    }

    private int canopySupportRadius(TreeDna dna) {
        return switch (dna.maturityStage()) {
            case SMALL, MEDIUM -> 4;
            case MATURE -> 3;
            case ANCIENT -> 4;
        };
    }

    private boolean isUpperCanopyCloudTarget(TreeCandidate candidate, TreeDna dna, Block target, PlannedTreeBlock plannedBlock) {
        if (plannedBlock.role() != TreeBlockRole.CANOPY) {
            return false;
        }
        int visibleHeight = TreeSpeciesStageStyle.visibleHeight(dna);
        int topY = dna.baseY() + visibleHeight - 1;
        int liveHeight = Math.max(candidate.height(), maturityService.liveTrunkHeight(candidate.world(), dna));
        int liveTop = dna.baseY() + maturityService.liveTrunkHeight(candidate.world(), dna) - 1;
        if (liveHeight >= Math.max(4, (int) Math.round(visibleHeight * 0.62D))) {
            int liveHorizontal = Math.max(Math.abs(target.getX() - dna.trunkXAt(Math.min(topY, liveTop))), Math.abs(target.getZ() - dna.trunkZAt(Math.min(topY, liveTop))));
            int liveHorizontalLimit = Math.max(2, Math.min(Math.max(TreeSpeciesStageStyle.canopyRadiusX(dna), TreeSpeciesStageStyle.canopyRadiusZ(dna)), canopySupportRadius(dna)));
            int liveVertical = Math.abs(target.getY() - liveTop);
            if (liveHorizontal <= liveHorizontalLimit && liveVertical <= 2) {
                plugin.pathDebug().traceSampled(plugin, "tree-evolution", "gate.canopy-live-cloud-pass",
                        plannedBlock.material() + " at " + format(target)
                                + " live-height=" + liveHeight + " visible=" + visibleHeight
                                + " horizontal=" + liveHorizontal + "/" + liveHorizontalLimit
                                + " ## active crown cloud catches leaves up around the current top instead of waiting for final height");
                return true;
            }
        }
        if (liveHeight < Math.max(3, visibleHeight - 2)) {
            return false;
        }
        int horizontal = Math.max(Math.abs(target.getX() - dna.trunkXAt(topY)), Math.abs(target.getZ() - dna.trunkZAt(topY)));
        int horizontalLimit = Math.max(TreeSpeciesStageStyle.canopyRadiusX(dna), TreeSpeciesStageStyle.canopyRadiusZ(dna)) + 1;
        int vertical = Math.abs(target.getY() - topY);
        int verticalLimit = TreeSpeciesStageStyle.canopyRadiusY(dna) + 2;
        if (horizontal <= horizontalLimit && vertical <= verticalLimit) {
            plugin.pathDebug().traceSampled(plugin, "tree-evolution", "gate.canopy-cloud-soft-pass",
                    plannedBlock.material() + " at " + format(target)
                            + " live-height=" + liveHeight + " visible=" + visibleHeight
                            + " horizontal=" + horizontal + "/" + horizontalLimit);
            return true;
        }
        return false;
    }

    private void recordSupportReject(TreeEvolutionConfig currentConfig, PlannedTreeBlock plannedBlock, Block target, String reason, int radius) {
        diagnostics.recordReject(currentConfig, "support-too-strict",
                "role=" + plannedBlock.role() + " material=" + plannedBlock.material() + " at " + format(target)
                        + " reason=" + reason + " radius=" + radius);
        plugin.pathDebug().traceSampled(plugin, "tree-evolution", "gate.support-too-strict",
                plannedBlock.role() + " " + plannedBlock.material() + " delayed at " + format(target)
                        + " reason=" + reason + " radius=" + radius);
    }

    private boolean isStructuralWoodRole(TreeBlockRole role) {
        return role == TreeBlockRole.TRUNK
                || role == TreeBlockRole.BRANCH
                || role == TreeBlockRole.ROOT;
    }

    private boolean isWoodSupport(Material material) {
        String name = material.name();
        return name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE")
                || material == Material.MUSHROOM_STEM;
    }

    void place(Block target, PlannedTreeBlock plannedBlock) {
        Material material = plannedBlock.role() == TreeBlockRole.GROUND_DETAIL
                ? groundDetailPolicy.adjust(target, plannedBlock.material())
                : plannedBlock.material();
        target.setType(material, false);
        BlockData data = target.getBlockData();
        if (data instanceof Orientable orientable) {
            orientable.setAxis(plannedBlock.axis() == null ? Axis.Y : plannedBlock.axis());
            target.setBlockData(orientable, false);
        } else if (data instanceof Leaves leaves) {
            leaves.setPersistent(true);
            target.setBlockData(leaves, false);
        } else if (data instanceof MultipleFacing facing && plannedBlock.supportFace() != null && facing.getAllowedFaces().contains(plannedBlock.supportFace())) {
            facing.setFace(plannedBlock.supportFace(), true);
            target.setBlockData(facing, false);
        }
    }


    private boolean isLogOrLeaf(Material material) {
        return material.name().endsWith("_LOG")
                || material.name().endsWith("_WOOD")
                || material.name().endsWith("_LEAVES");
    }

    private static String keyFor(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":"
                + block.getY() + ":" + block.getZ();
    }

    private String format(Block block) {
        return block.getWorld().getName() + " " + block.getX() + ","
                + block.getY() + "," + block.getZ();
    }

    private record CandidateBlock(
            PlannedTreeBlock block, int index, int priority) {
    }
}
