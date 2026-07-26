package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.evolution.coreparts.EvolutionPlugin;

/**
 * ## Owns corrective canopy subrules.
 *
 * <p>Exposed upper logs, uncovered branch tips, and transition-era leaf
 * envelopes are repaired here. General target selection and block placement
 * remain in {@link TreePlacementService}.</p>
 */
final class TreeCanopyRepairService {
    private static final List<BlockFace> NEIGHBORS = List.of(
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
    private final EvolutionPlugin plugin;
    private final TreeEvolutionDiagnostics diagnostics;
    private final TreeDnaRepository repository;
    private final TreePlanAuditService planAudit;
    private final TreeMaturityService maturityService;
    private final AtomicLong changedBlocks;

    TreeCanopyRepairService(
            EvolutionPlugin plugin,
            TreeEvolutionDiagnostics diagnostics,
            TreeDnaRepository repository,
            TreePlanAuditService planAudit,
            TreeMaturityService maturityService,
            AtomicLong changedBlocks
    ) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
        this.repository = repository;
        this.planAudit = planAudit;
        this.maturityService = maturityService;
        this.changedBlocks = changedBlocks;
    }
    Optional<Block> findExposedUpperLog(TreeCandidate candidate, TreeDna dna,
            Map<String, PlannedTreeBlock> blocksByKey) {
        World world = candidate.world();
        int liveHeight = maturityService.liveTrunkHeight(world, dna);
        int liveTop = dna.baseY() + liveHeight - 1;
        int startY = Math.max(dna.baseY(), liveTop - 3);
        for (int y = liveTop; y >= startY; y--) {
            int width = TreeSpeciesStageStyle.trunkWidthAt(dna, y);
            int radius = Math.max(0, width / 2);
            int centerX = dna.trunkXAt(y);
            int centerZ = dna.trunkZAt(y);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(centerX + x, y, centerZ + z);
                    if (block.getType() == dna.species().logMaterial()
                            && TreeCanopyIntegrityPolicy.requiresCanopyCover(
                                    block.getX(), block.getY(), block.getZ(),
                                    dna.species().leafMaterial(), blocksByKey)
                            && planAudit.adjacentPlannedLeafContacts(
                                    world, dna, block,
                                    dna.species().leafMaterial(),
                                    blocksByKey) == 0) {
                        return Optional.of(block);
                    }
                }
            }
        }
        return Optional.empty();
    }

    int maybeCoverExposedTopLog(TreeCandidate candidate, TreeDna dna,
            TreeEvolutionConfig currentConfig, Block trunk,
            PlannedTreeBlock plannedBlock,
            Map<String, PlannedTreeBlock> blocksByKey) {
        if (plannedBlock.role() != TreeBlockRole.TRUNK || trunk.getY() < candidate.topY()) {
            return 0;
        }
        return coverExposedLog(candidate, dna, currentConfig, trunk,
                blocksByKey);
    }

    int coverBranchTip(TreeCandidate candidate, TreeDna dna,
            TreeEvolutionConfig currentConfig, Block tip, int requiredContacts,
            int requiredCluster, Map<String, PlannedTreeBlock> blocksByKey) {
        Material leafMaterial = dna.species().leafMaterial();
        int currentContacts = planAudit.adjacentPlannedLeafContacts(
                candidate.world(), dna, tip, leafMaterial, blocksByKey);
        int currentCluster = planAudit.plannedEnvelopeLiveLeaves(
                candidate.world(), dna, tip, leafMaterial, blocksByKey);
        if (currentContacts >= requiredContacts
                && currentCluster >= requiredCluster
                && planAudit.hasNaturalLiveEnvelope(
                        candidate.world(), dna, tip, leafMaterial, blocksByKey)) {
            return 0;
        }

        int placementLimit = 3;
        int placed = 0;
        int missingContacts = Math.max(0, requiredContacts - currentContacts);
        List<BlockFace> faces = List.of(
                BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN);
        int offset = Math.floorMod(
                (tip.getX() * 31) ^ (tip.getY() * 17) ^ (tip.getZ() * 13),
                faces.size());
        for (int index = 0; index < faces.size()
                && placed < placementLimit
                && placed < missingContacts; index++) {
            Block leaf = tip.getRelative(faces.get((index + offset) % faces.size()));
            if (reformOrPlaceOwnedCanopyLeaf(
                    candidate, dna, currentConfig, tip, leaf,
                    leafMaterial, blocksByKey, "branch-envelope-contact")) {
                placed++;
            }
        }

        int updatedCluster = planAudit.plannedEnvelopeLiveLeaves(
                candidate.world(), dna, tip, leafMaterial, blocksByKey);
        if (placed < placementLimit
                && (updatedCluster < requiredCluster
                        || !planAudit.hasNaturalLiveEnvelope(
                                candidate.world(), dna, tip,
                                leafMaterial, blocksByKey))) {
            List<PlannedTreeBlock> envelope = new ArrayList<>();
            for (PlannedTreeBlock planned : blocksByKey.values()) {
                if (planned.role() != TreeBlockRole.CANOPY
                        || planned.material() != leafMaterial
                        || Math.abs(planned.x() - tip.getX()) > 2
                        || Math.abs(planned.y() - tip.getY()) > 1
                        || Math.abs(planned.z() - tip.getZ()) > 2) {
                    continue;
                }
                envelope.add(planned);
            }
            envelope.sort(Comparator
                    .comparingInt((PlannedTreeBlock planned) ->
                            branchEnvelopePlacementPriority(tip, planned))
                    .thenComparing(PlannedTreeBlock::key));
            for (PlannedTreeBlock planned : envelope) {
                if (placed >= placementLimit
                        || (updatedCluster >= requiredCluster
                                && planAudit.hasNaturalLiveEnvelope(
                                        candidate.world(), dna, tip,
                                        leafMaterial, blocksByKey))) {
                    break;
                }
                Block leaf = candidate.world().getBlockAt(
                        planned.x(), planned.y(), planned.z());
                if (reformOrPlaceOwnedCanopyLeaf(
                        candidate, dna, currentConfig, tip, leaf,
                        leafMaterial, blocksByKey, "branch-envelope-cluster")) {
                    placed++;
                    updatedCluster = planAudit.plannedEnvelopeLiveLeaves(
                            candidate.world(), dna, tip, leafMaterial,
                            blocksByKey);
                }
            }
        }
        if (placed > 0) {
            dna.setCurrentIntent(TreeGrowthIntent.CANOPY);
            int finalContacts = planAudit.adjacentPlannedLeafContacts(
                    candidate.world(), dna, tip, leafMaterial, blocksByKey);
            int finalCluster = planAudit.plannedEnvelopeLiveLeaves(
                    candidate.world(), dna, tip, leafMaterial, blocksByKey);
            plugin.pathDebug().trace(plugin, "tree-evolution",
                    "canopy.branch-envelope-attach",
                    "tip=" + format(tip)
                            + " leaf=" + leafMaterial
                            + " owned-contacts=" + finalContacts + "/" + requiredContacts
                            + " owned-envelope=" + finalCluster + "/" + requiredCluster
                            + " ownership-version=" + dna.evolutionOwnershipVersion()
                            + " ## branch envelope grows from leaves explicitly placed or reformed by this evolution epoch");
        } else {
            diagnostics.recordReject(currentConfig, "branch-envelope-space",
                    "tip=" + format(tip)
                            + " owned-contacts=" + currentContacts + "/" + requiredContacts
                            + " owned-envelope=" + currentCluster + "/" + requiredCluster
                            + " no safe connected owned planned leaf space");
        }
        return placed;
    }

    private int branchEnvelopePlacementPriority(
            Block tip, PlannedTreeBlock planned) {
        int dx = Math.abs(planned.x() - tip.getX());
        int dy = Math.abs(planned.y() - tip.getY());
        int dz = Math.abs(planned.z() - tip.getZ());
        // ## Build vertical and side volume before filling the middle. This keeps
        // a branch crown cloud-like throughout construction instead of flat first.
        int volumeBias = dy > 0 ? -12 : 0;
        int sideBias = dx > 0 && dz > 0 ? -4 : 0;
        return volumeBias + sideBias + dx + dy + dz;
    }
    int coverExposedLog(TreeCandidate candidate, TreeDna dna,
            TreeEvolutionConfig currentConfig, Block trunk,
            Map<String, PlannedTreeBlock> blocksByKey) {
        Material leafMaterial = dna.species().leafMaterial();
        if (planAudit.adjacentPlannedLeafContacts(
                candidate.world(), dna, trunk, leafMaterial, blocksByKey) > 0) {
            return 0;
        }

        int desiredLeaves = switch (dna.maturityStage()) {
            case SMALL -> 4;
            case MEDIUM -> 4;
            case MATURE -> dna.hugeArchitecture() ? 6 : 5;
            case ANCIENT -> dna.hugeArchitecture() ? 8 : 6;
        };
        int placed = 0;
        List<BlockFace> faces = List.of(
                BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN);
        int offset = Math.floorMod(
                (trunk.getX() * 31) ^ (trunk.getY() * 17)
                        ^ (trunk.getZ() * 13),
                faces.size());
        for (int index = 0; index < faces.size()
                && placed < desiredLeaves; index++) {
            BlockFace face = faces.get((index + offset) % faces.size());
            Block leaf = trunk.getRelative(face);
            if (reformOrPlaceOwnedCanopyLeaf(
                    candidate, dna, currentConfig, trunk, leaf,
                    leafMaterial, blocksByKey, "canopy-shell")) {
                placed++;
            }
        }
        if (placed > 0) {
            dna.setCurrentIntent(TreeGrowthIntent.CANOPY);
            diagnostics.recordCanopyLift(plugin, currentConfig, trunk, dna, placed);
            plugin.pathDebug().trace(plugin, "tree-evolution", "canopy.lift-cover",
                    "trunk=" + format(trunk)
                            + " leaf=" + leafMaterial
                            + " owned=" + placed
                            + " ## canopy shell explicitly reforms or places leaves around exposed live support");
        } else {
            diagnostics.recordReject(currentConfig, "canopy-lift-space",
                    "exposed trunk=" + format(trunk)
                            + " no safe adjacent owned leaf space");
        }
        return placed;
    }

    private boolean reformOrPlaceOwnedCanopyLeaf(
            TreeCandidate candidate, TreeDna dna,
            TreeEvolutionConfig currentConfig, Block support, Block leaf,
            Material leafMaterial,
            Map<String, PlannedTreeBlock> blocksByKey, String reason) {
        String coordinateKey = leaf.getX() + ":" + leaf.getY()
                + ":" + leaf.getZ();
        PlannedTreeBlock planned = blocksByKey.get(coordinateKey);
        if (planned == null
                || planned.role() != TreeBlockRole.CANOPY
                || planned.material() != leafMaterial) {
            return false;
        }

        String ownershipKey = keyFor(leaf);
        if (leaf.getType() == leafMaterial
                && dna.countsAsEvolvedLeaf(ownershipKey)) {
            return false;
        }
        if (!canPlaceCanopyLiftLeaf(
                leaf, support, dna, currentConfig, blocksByKey)) {
            return false;
        }

        boolean preexistingLeaf = leaf.getType() == leafMaterial;
        boolean originalLeaf = dna.isOriginalShapeLeaf(ownershipKey);
        boolean sourceReform = TreeBranchEnvelopeOwnershipPolicy
                .shouldReformOriginalLeaf(
                        true, originalLeaf,
                        dna.countsAsEvolvedLeaf(ownershipKey));
        placePersistentLeaf(leaf, leafMaterial);
        dna.markEvolvedLeaf(ownershipKey);
        changedBlocks.incrementAndGet();
        repository.markDirty("owned canopy leaf " + ownershipKey);
                planAudit.invalidateLiveAnalysis(dna.key());
        plugin.pathDebug().trace(plugin, "tree-evolution",
                preexistingLeaf
                        ? "audit.branch-envelope-leaf-reformed"
                        : "canopy.branch-envelope-leaf-placed",
                "tree=" + dna.key()
                        + " reason=" + reason
                        + " support=" + format(support)
                        + " leaf=" + format(leaf)
                        + " original=" + originalLeaf
                        + " source-reform=" + sourceReform
                        + " ownership-version=" + dna.evolutionOwnershipVersion()
                        + " ## only explicitly evolved canopy leaves may satisfy the constructor hierarchy");
        return true;
    }
    private boolean canPlaceCanopyLiftLeaf(Block leaf, Block trunk, TreeDna dna,
            TreeEvolutionConfig currentConfig,
            Map<String, PlannedTreeBlock> blocksByKey) {
        PlannedTreeBlock planned = blocksByKey.get(
                leaf.getX() + ":" + leaf.getY() + ":" + leaf.getZ());
        if (planned == null || planned.role() != TreeBlockRole.CANOPY
                || planned.material() != dna.species().leafMaterial()) {
            return false;
        }
        int chunkX = leaf.getX() >> 4;
        int chunkZ = leaf.getZ() >> 4;
        if (!leaf.getWorld().isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(leaf.getWorld(), chunkX, chunkZ, currentConfig.ownedChunkRadius())) {
            return false;
        }
        if (!plugin.canEvolveAt(leaf.getLocation(), "tree-evolution")) {
            return false;
        }
        if (leaf.isLiquid() || (!currentConfig.isReplaceable(leaf.getType()) && leaf.getType() != dna.species().leafMaterial())) {
            return false;
        }
        return touchesBlock(leaf, trunk)
                || hasDirectWoodNeighbor(leaf)
                || hasAdjacentLeaf(leaf, dna.species().leafMaterial());
    }

    private boolean hasAdjacentLeaf(Block block, Material leafMaterial) {
        for (BlockFace face : NEIGHBORS) {
            Material type = block.getRelative(face).getType();
            if (type == leafMaterial || type.name().endsWith("_LEAVES")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDirectWoodNeighbor(Block block) {
        for (BlockFace face : NEIGHBORS) {
            if (isWoodSupport(block.getRelative(face).getType())) {
                return true;
            }
        }
        return false;
    }

    private boolean touchesBlock(Block first, Block second) {
        return first.getWorld().equals(second.getWorld())
                && Math.abs(first.getX() - second.getX()) + Math.abs(first.getY() - second.getY()) + Math.abs(first.getZ() - second.getZ()) == 1;
    }

    private void placePersistentLeaf(Block leaf, Material leafMaterial) {
        leaf.setType(leafMaterial, false);
        if (leaf.getBlockData() instanceof Leaves leaves) {
            leaves.setPersistent(true);
            leaf.setBlockData(leaves, false);
        }
    }


    private boolean isWoodSupport(Material material) {
        return material.name().endsWith("_LOG")
                || material.name().endsWith("_WOOD")
                || material == Material.MANGROVE_ROOTS
                || material == Material.MUDDY_MANGROVE_ROOTS;
    }

    private static String keyFor(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":"
                + block.getY() + ":" + block.getZ();
    }

    private String format(Block block) {
        return block.getWorld().getName() + " " + block.getX() + ","
                + block.getY() + "," + block.getZ();
    }
}
