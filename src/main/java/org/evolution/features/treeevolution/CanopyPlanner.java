package org.evolution.features.treeevolution;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.bukkit.Axis;

final class CanopyPlanner {
    private static final int MAX_SPRUCE_FRINGE_DROP = 2;
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
        if (dna.species() == TreeSpecies.SPRUCE) {
            plan.withAugment(
                    TreePlacementAugment.CANOPY_SPRUCE_ENVELOPE,
                    () -> planSpruceConiferCrown(
                            plan, dna, branchTips, random, topY));
            finishCanopyPlan(plan, dna, branchPlans, branchTips);
            return;
        }
        if (dna.species() == TreeSpecies.ACACIA) {
            planAcaciaUmbrellaCrown(plan, dna, branchPlans, random, topY);
            finishCanopyPlan(plan, dna, branchPlans, branchTips);
            return;
        }
        if (usesFancyEarlyCrown(dna)) {
            plan.withAugment(TreePlacementAugment.CANOPY_FANCY_CLOUD,
                    () -> planFancyEarlyCrown(
                            plan, dna, branchTips, random, topY));
            finishCanopyPlan(plan, dna, branchPlans, branchTips);
            return;
        }
        planLeafBlob(plan, dna, dna.trunkXAt(topY), topY, dna.trunkZAt(topY),
                mainCanopyRadius(dna,
                        TreeSpeciesStageStyle.canopyRadiusX(dna)),
                mainCanopyVerticalRadius(dna),
                mainCanopyRadius(dna,
                        TreeSpeciesStageStyle.canopyRadiusZ(dna)),
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
        finishCanopyPlan(plan, dna, branchPlans, branchTips);
    }

    private void finishCanopyPlan(
            TreePlan plan,
            TreeDna dna,
            List<TreeBranchPlan> branchPlans,
            List<TreeBranchPlan.BranchTip> branchTips
    ) {
        pruneSourceLogCanopy(plan, dna);
        plan.withAugment(
                TreePlacementAugment.CANOPY_BRANCH_INTEGRATION,
                () -> planBranchCanopyIntegration(
                        plan, dna, branchPlans));
        plan.withAugment(TreePlacementAugment.CANOPY_TIP_ANCHOR,
                () -> planNaturalTipAnchors(plan, dna, branchTips));
        pruneBranchesWithoutEnvelope(plan, dna, branchPlans);
        pruneDetachedCanopy(plan);
        plan.withAugment(
                TreePlacementAugment.CANOPY_BRANCH_INTEGRATION,
                () -> planFinalBranchCanopyContacts(
                        plan, dna, branchPlans));
    }

    private void planSpruceConiferCrown(
            TreePlan plan,
            TreeDna dna,
            List<TreeBranchPlan.BranchTip> branchTips,
            Random random,
            int topY
    ) {
        int bottomY = spruceCrownBottomY(dna);
        int maximumRadius = spruceMaximumRadius(dna);
        int crownSpan = Math.max(1, topY - bottomY);
        for (int y = bottomY; y <= topY; y++) {
            // ## One monotonic envelope replaces overlapping ellipsoids. A
            // spruce may hold a radius like a branch shelf, but it may not
            // narrow and then inflate into another stacked green ball.
            int radius = spruceEnvelopeRadius(
                    y, bottomY, topY, maximumRadius);
            planSpruceSlice(
                    plan, dna,
                    dna.trunkXAt(y), y, dna.trunkZAt(y),
                    radius, random);
        }
        planSpruceLowerFringe(
                plan, dna,
                dna.trunkXAt(bottomY), bottomY,
                dna.trunkZAt(bottomY), maximumRadius);
        for (TreeBranchPlan.BranchTip tip : branchTips) {
            planSpruceTipTuft(plan, dna, tip);
        }
    }

    private int spruceCrownBottomY(TreeDna dna) {
        int visibleHeight = TreeSpeciesStageStyle.visibleHeight(dna);
        double startRatio = switch (dna.variant()) {
            case SPRUCE_PINE, SPRUCE_MEGA_PINE -> 0.42D;
            default -> 0.24D;
        };
        // ## The lower-fringe constructor can hang two cells below the crown
        // shoulder. Reserve two clear trunk blocks above the stump before
        // that drop, otherwise small classic spruces target grass at base Y
        // and repeatedly stall with a chopped, merged-looking crown.
        int minimumShoulderY = dna.baseY()
                + MAX_SPRUCE_FRINGE_DROP + 2;
        return Math.max(
                minimumShoulderY,
                dna.baseY() + (int) Math.round(
                        visibleHeight * startRatio));
    }

    private int spruceMaximumRadius(TreeDna dna) {
        int stageRadius = switch (dna.maturityStage()) {
            case SMALL -> 3;
            case MEDIUM -> 4;
            case MATURE -> 5;
            case ANCIENT -> 6;
        };
        return Math.min(
                stageRadius,
                Math.max(TreeSpeciesStageStyle.canopyRadiusX(dna),
                        TreeSpeciesStageStyle.canopyRadiusZ(dna)));
    }

    private int spruceEnvelopeRadius(
            int y, int bottomY, int topY, int maximumRadius) {
        if (y <= bottomY) {
            return maximumRadius;
        }
        int crownSpan = Math.max(1, topY - bottomY);
        double progress = Math.min(
                1.0D, (y - bottomY) / (double) crownSpan);
        return Math.max(1, (int) Math.ceil(
                maximumRadius * (1.0D - 0.78D * progress)));
    }

    private boolean insideSpruceEnvelope(
            TreeDna dna, int x, int y, int z) {
        if (dna.species() != TreeSpecies.SPRUCE) {
            return true;
        }
        int topY = dna.baseY()
                + TreeSpeciesStageStyle.visibleHeight(dna) - 1;
        int bottomY = spruceCrownBottomY(dna);
        if (y < bottomY - 2 || y > topY + 1) {
            return false;
        }
        int radius = spruceEnvelopeRadius(
                y, bottomY, topY, spruceMaximumRadius(dna));
        int centerY = Math.max(dna.baseY(), Math.min(topY, y));
        int dx = x - dna.trunkXAt(centerY);
        int dz = z - dna.trunkZAt(centerY);
        return ((dx * dx) + (dz * dz))
                <= Math.max(1, radius * radius) * 1.16D;
    }

    private void planSpruceLowerFringe(
            TreePlan plan,
            TreeDna dna,
            int centerX,
            int bottomY,
            int centerZ,
            int radius
    ) {
        int edge = Math.max(1, radius - 1);
        int[][] offsets = {
                {radius, 0}, {-radius, 0},
                {0, radius}, {0, -radius},
                {edge, edge}, {edge, -edge},
                {-edge, edge}, {-edge, -edge},
                {edge, 1}, {edge, -1},
                {-edge, 1}, {-edge, -1}
        };
        for (int index = 0; index < offsets.length; index++) {
            int x = centerX + offsets[index][0];
            int z = centerZ + offsets[index][1];
            // ## Every hanging needle column owns a face-connected shoulder.
            // Zero-, one-, and two-block drops break the mechanically flat
            // skirt; the lifted shoulder keeps even the deepest cell clear of
            // the stump and terrain band.
            plan.add(new PlannedTreeBlock(
                    x, bottomY, z,
                    dna.species().leafMaterial(),
                    TreeBlockRole.CANOPY, Axis.Y, null));
            int drop = index % (MAX_SPRUCE_FRINGE_DROP + 1);
            for (int depth = 1; depth <= drop; depth++) {
                plan.add(new PlannedTreeBlock(
                        x, bottomY - depth, z,
                        dna.species().leafMaterial(),
                        TreeBlockRole.CANOPY, Axis.Y, null));
            }
        }
    }

    private void planSpruceSlice(
            TreePlan plan,
            TreeDna dna,
            int centerX,
            int y,
            int centerZ,
            int radius,
            Random random
    ) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double normalized = ((x * x) + (z * z))
                        / (double) Math.max(1, radius * radius);
                if (normalized > 1.16D) {
                    continue;
                }
                boolean edge = normalized > 0.60D;
                boolean silhouetteAnchor =
                        Math.abs(x) == radius && z == 0
                                || Math.abs(z) == radius && x == 0;
                // ## Radius bands can span several vertical blocks on a tall
                // conifer. Vary their outer needle ring while retaining the
                // four silhouette anchors, so a long cone does not become a
                // stack of identical copied disks.
                if (edge && !silhouetteAnchor
                        && random.nextDouble() > 0.68D) {
                    continue;
                }
                plan.add(new PlannedTreeBlock(
                        centerX + x, y, centerZ + z,
                        dna.species().leafMaterial(),
                        TreeBlockRole.CANOPY, Axis.Y, null));
            }
        }
    }

    private void planSpruceTipTuft(
            TreePlan plan,
            TreeDna dna,
            TreeBranchPlan.BranchTip tip
    ) {
        int[][] offsets = {
                {0, 1, 0}, {0, -1, 0},
                {1, 0, 0}, {-1, 0, 0},
                {0, 0, 1}, {0, 0, -1},
                {1, 1, 0}, {-1, -1, 0},
                {0, 1, 1}, {0, -1, -1}
        };
        for (int[] offset : offsets) {
            int x = tip.x() + offset[0];
            int y = tip.y() + offset[1];
            int z = tip.z() + offset[2];
            // ## Tip cover obeys the same conical envelope as the body.
            // Otherwise every branch can inflate a second crown shelf after
            // the planner has already tapered the spruce leader.
            if (!insideSpruceEnvelope(dna, x, y, z)) {
                continue;
            }
            plan.add(new PlannedTreeBlock(
                    x, y, z,
                    dna.species().leafMaterial(),
                    TreeBlockRole.CANOPY, Axis.Y, null));
        }
    }

    private void planFinalBranchCanopyContacts(
            TreePlan plan,
            TreeDna dna,
            List<TreeBranchPlan> branchPlans
    ) {
        // ## Cleanup can remove a randomized fringe cell after the first
        // integration pass. Reassert the universal direct-contact contract on
        // the final voxel plan; these leaves are face-attached to owned wood,
        // so they cannot create a detached canopy component.
        planBranchCanopyIntegration(plan, dna, branchPlans);
    }

    private void planBranchCanopyIntegration(
            TreePlan plan,
            TreeDna dna,
            List<TreeBranchPlan> branchPlans) {
        for (TreeBranchPlan branch : branchPlans) {
            Set<String> visited = new HashSet<>();
            for (TreeBranchPlan.BranchSegment segment
                    : branch.segments()) {
                String segmentKey = segment.x() + ":" + segment.y()
                        + ":" + segment.z();
                // ## A bent/rising path can record one coordinate at multiple
                // logical steps. Non-distal occurrences must not consume the
                // coordinate before its later crown-covered occurrence.
                if (!TreeBranchCanopyIntegrationPolicy
                                .requiresCover(branch, segment)
                        || !visited.add(segmentKey)) {
                    continue;
                }
                int contacts = TreeBranchTipIntegrityPolicy
                        .plannedLeafContacts(
                                segment.x(), segment.y(), segment.z(),
                                dna.species().leafMaterial(),
                                plan.blocksByKey());
                int requiredContacts =
                        TreeBranchCanopyIntegrationPolicy
                                .desiredDirectContacts(
                                        dna,
                                        segment.x(), segment.y(),
                                        segment.z(),
                                        plan.blocksByKey());
                int rotation = Math.floorMod(
                        Long.hashCode(dna.seed())
                                ^ (branch.id() * 41)
                                ^ (segment.step() * 17),
                        TIP_CONTACT_OFFSETS.length);
                for (int index = 0;
                        contacts < requiredContacts
                                && index < TIP_CONTACT_OFFSETS.length;
                        index++) {
                    int[] offset = TIP_CONTACT_OFFSETS[
                            (rotation + index)
                                    % TIP_CONTACT_OFFSETS.length];
                    int x = segment.x() + offset[0];
                    int y = segment.y() + offset[1];
                    int z = segment.z() + offset[2];
                    String key = x + ":" + y + ":" + z;
                    PlannedTreeBlock occupied =
                            plan.blocksByKey().get(key);
                    if (occupied != null) {
                        // ## Existing canopy was included in the initial
                        // contact count. Re-adding that coordinate used to
                        // increment the counter without creating a new face.
                        continue;
                    }
                    if (dna.originalShapeLogs().contains(
                            dna.worldId() + ":" + key)) {
                        continue;
                    }
                    // ## A distal spruce limb may carry one direct leaf just
                    // beyond the strict cone. This tiny seam allowance covers
                    // the wood without inflating another full crown shelf.
                    plan.add(new PlannedTreeBlock(
                            x, y, z,
                            dna.species().leafMaterial(),
                            TreeBlockRole.CANOPY, Axis.Y, null));
                    contacts++;
                }
            }
        }
    }

    // ## Live placement never replaces source wood with leaves. Remove those
    // impossible canopy cells before envelope validation so the planner fills
    // around preserved branches instead of depending on blocked coordinates.
    private void pruneSourceLogCanopy(TreePlan plan, TreeDna dna) {
        Set<String> sourceLogCoordinates = new HashSet<>();
        for (String sourceKey : dna.originalShapeLogs()) {
            String[] parts = sourceKey.split(":");
            int offset = parts.length - 3;
            sourceLogCoordinates.add(
                    parts[offset] + ":" + parts[offset + 1]
                            + ":" + parts[offset + 2]);
        }
        plan.removeCanopy(sourceLogCoordinates);
    }
    // ## Randomized cloud edges may form tiny diagonal islands. Keep only
    // canopy components that are visibly supported by planned trunk/branch
    // wood, so final trees never contain floating fringe or leaf columns.
    private void pruneDetachedCanopy(TreePlan plan) {
        Map<String, PlannedTreeBlock> canopy = new HashMap<>();
        List<PlannedTreeBlock> wood = new ArrayList<>();
        for (PlannedTreeBlock block : plan.blocksByKey().values()) {
            if (block.role() == TreeBlockRole.CANOPY) {
                canopy.put(block.key(), block);
            } else if (block.role() == TreeBlockRole.TRUNK
                    || block.role() == TreeBlockRole.BRANCH) {
                wood.add(block);
            }
        }

        Set<String> supported = new HashSet<>();
        Deque<PlannedTreeBlock> pending = new ArrayDeque<>();
        for (PlannedTreeBlock leaf : canopy.values()) {
            if (wood.stream().anyMatch(log ->
                    chebyshev(leaf, log) <= 1)) {
                supported.add(leaf.key());
                pending.addLast(leaf);
            }
        }
        while (!pending.isEmpty()) {
            PlannedTreeBlock current = pending.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        PlannedTreeBlock next = canopy.get(
                                (current.x() + dx) + ":"
                                        + (current.y() + dy) + ":"
                                        + (current.z() + dz));
                        if (next != null && supported.add(next.key())) {
                            pending.addLast(next);
                        }
                    }
                }
            }
        }

        Set<String> detached = new HashSet<>(canopy.keySet());
        detached.removeAll(supported);
        plan.removeCanopy(detached);
    }

    private int chebyshev(
            PlannedTreeBlock first, PlannedTreeBlock second) {
        return Math.max(
                Math.max(
                        Math.abs(first.x() - second.x()),
                        Math.abs(first.y() - second.y())),
                Math.abs(first.z() - second.z()));
    }
    // ## Random canopy edges may not decide whether a valid limb survives.
    // Underfilled terminals receive one compact species-colored crown before audit.
    private void planMinimumTipEnvelopes(
            TreePlan plan, TreeDna dna, List<TreeBranchPlan> branchPlans) {
        for (TreeBranchPlan branch : branchPlans) {
            TreeBranchPlan.BranchTip tip = branch.tip();
            if (TreeBranchTipIntegrityPolicy.hasPreplannedEnvelope(
                    dna, tip.x(), tip.y(), tip.z(), plan.blocksByKey())) {
                continue;
            }
            for (int y = -1; y <= 1; y++) {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        if ((x == 0 && y == 0 && z == 0)
                                || Math.abs(x) + Math.abs(z)
                                        + (Math.abs(y) * 2) > 3) {
                            continue;
                        }
                        String coordinateKey = (tip.x() + x) + ":"
                                + (tip.y() + y) + ":" + (tip.z() + z);
                        if (dna.originalShapeLogs().contains(
                                dna.worldId() + ":" + coordinateKey)) {
                            continue;
                        }
                        if (!insideSpruceEnvelope(
                                dna, tip.x() + x, tip.y() + y,
                                tip.z() + z)) {
                            continue;
                        }
                        plan.add(new PlannedTreeBlock(
                                tip.x() + x, tip.y() + y, tip.z() + z,
                                dna.species().leafMaterial(),
                                TreeBlockRole.CANOPY, Axis.Y, null));
                    }
                }
            }
        }
    }

    // ## Impossible branch candidates are removed instead of becoming bare limbs or permanent retries.
    private void pruneBranchesWithoutEnvelope(
            TreePlan plan, TreeDna dna, List<TreeBranchPlan> branchPlans) {
        plan.withAugment(TreePlacementAugment.CANOPY_TIP_ANCHOR,
                () -> planMinimumTipEnvelopes(
                        plan, dna, branchPlans));
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
        int x = tip.x() + offset[0];
        int y = tip.y() + offset[1];
        int z = tip.z() + offset[2];
        if (!insideSpruceEnvelope(dna, x, y, z)) {
            return;
        }
        plan.add(new PlannedTreeBlock(
                x, y, z,
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
        if (!insideSpruceEnvelope(dna, x, y, z)) {
            return false;
        }
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

    // ## Acacia keeps a high umbrella silhouette, but each pad is a compact
    // three-dimensional leaf cloud. Dense overlapping lobes conceal the fork
    // frame during gradual construction without becoming a generic oak sphere.
    private void planAcaciaUmbrellaCrown(
            TreePlan plan, TreeDna dna,
            List<TreeBranchPlan> branchPlans, Random random, int topY) {
        int mainCap = AcaciaArchitecturePolicy.centerLobeRadius(
                dna);
        int mainRadiusX = Math.min(
                mainCap, TreeSpeciesStageStyle.canopyRadiusX(dna));
        int mainRadiusZ = Math.min(
                mainCap, TreeSpeciesStageStyle.canopyRadiusZ(dna));
        plan.withAugment(
                TreePlacementAugment.CANOPY_ACACIA_UMBRELLA_PAD,
                () -> planAcaciaUmbrellaPad(
                        plan, dna, dna.trunkXAt(topY), topY,
                        dna.trunkZAt(topY), mainRadiusX, mainRadiusZ,
                        random));
        plan.withAugment(
                TreePlacementAugment.CANOPY_ACACIA_LOWER_TUFT,
                () -> planAcaciaLowerTufts(
                        plan, dna, dna.trunkXAt(topY), topY,
                        dna.trunkZAt(topY), mainRadiusX, mainRadiusZ,
                        -1));

        int tipCap = AcaciaArchitecturePolicy.branchLobeRadius(
                dna);
        for (TreeBranchPlan branch : branchPlans) {
            TreeBranchPlan.BranchTip tip = branch.tip();
            HorizontalStep outward = finalHorizontalStep(branch);
            int centerX = tip.x() + outward.x();
            int centerZ = tip.z() + outward.z();
            plan.withAugment(
                    TreePlacementAugment.CANOPY_ACACIA_UMBRELLA_PAD,
                    () -> planAcaciaUmbrellaPad(
                            plan, dna, centerX, tip.y(), centerZ,
                            tipCap, tipCap, random));
            plan.withAugment(
                    TreePlacementAugment.CANOPY_ACACIA_LOWER_TUFT,
                    () -> planAcaciaLowerTufts(
                            plan, dna, centerX, tip.y(), centerZ,
                            tipCap, tipCap, tip.branchId()));
            plan.withAugment(
                    TreePlacementAugment.CANOPY_ACACIA_TIP_THROAT,
                    () -> planAcaciaTipThroat(
                            plan, dna, tip, outward));
        }
    }

    private void planAcaciaTipThroat(
            TreePlan plan,
            TreeDna dna,
            TreeBranchPlan.BranchTip tip,
            HorizontalStep outward) {
        // ## A five-direction throat buries the terminal log before the broad
        // pad finishes. This prevents a gradual build from showing a bare
        // branch spear while preserving the horizontal umbrella outline.
        int sideX = -outward.z();
        int sideZ = outward.x();
        plan.add(new PlannedTreeBlock(
                tip.x() + outward.x(), tip.y(), tip.z() + outward.z(),
                dna.species().leafMaterial(),
                TreeBlockRole.CANOPY, Axis.Y, null));
        plan.add(new PlannedTreeBlock(
                tip.x(), tip.y() + 1, tip.z(),
                dna.species().leafMaterial(),
                TreeBlockRole.CANOPY, Axis.Y, null));
        plan.add(new PlannedTreeBlock(
                tip.x() + outward.x(), tip.y() - 1,
                tip.z() + outward.z(),
                dna.species().leafMaterial(),
                TreeBlockRole.CANOPY, Axis.Y, null));
        plan.add(new PlannedTreeBlock(
                tip.x() + sideX, tip.y(), tip.z() + sideZ,
                dna.species().leafMaterial(),
                TreeBlockRole.CANOPY, Axis.Y, null));
        plan.add(new PlannedTreeBlock(
                tip.x() - sideX, tip.y(), tip.z() - sideZ,
                dna.species().leafMaterial(),
                TreeBlockRole.CANOPY, Axis.Y, null));
    }

    private HorizontalStep finalHorizontalStep(TreeBranchPlan branch) {
        List<TreeBranchPlan.BranchSegment> segments = branch.segments();
        for (int index = segments.size() - 1; index >= 0; index--) {
            TreeBranchPlan.BranchSegment segment = segments.get(index);
            int x = Integer.signum(segment.x() - segment.parentX());
            int z = Integer.signum(segment.z() - segment.parentZ());
            if (x != 0 || z != 0) {
                return new HorizontalStep(x, z);
            }
        }
        return new HorizontalStep(
                branch.direction().getModX(),
                branch.direction().getModZ());
    }

    private void planAcaciaUmbrellaPad(
            TreePlan plan, TreeDna dna, int centerX, int centerY, int centerZ,
            int radiusX, int radiusZ, Random random) {
        double edgeDensity =
                dna.variant() == TreeVariant.ACACIA_WINDSWEPT
                        ? 0.91D : 0.87D;
        for (int layer = -1; layer <= 1; layer++) {
            double layerLimit = switch (layer) {
                case -1 -> 0.58D;
                case 0 -> 1.08D;
                default -> 0.78D;
            };
            for (int x = -radiusX; x <= radiusX; x++) {
                for (int z = -radiusZ; z <= radiusZ; z++) {
                    double nx = x / Math.max(1.0D, radiusX);
                    double nz = z / Math.max(1.0D, radiusZ);
                    double ellipse = (nx * nx) + (nz * nz);
                    boolean solidCore = ellipse <= switch (layer) {
                        case -1 -> 0.34D;
                        case 0 -> 0.56D;
                        default -> 0.42D;
                    };
                    if (ellipse > layerLimit
                            || (!solidCore
                                    && random.nextDouble() > edgeDensity)) {
                        continue;
                    }
                    plan.add(new PlannedTreeBlock(
                            centerX + x, centerY + layer, centerZ + z,
                            dna.species().leafMaterial(),
                            TreeBlockRole.CANOPY, Axis.Y, null));
                }
            }
        }
        if (dna.variant() == TreeVariant.ACACIA_WINDSWEPT) {
            plan.withAugment(
                    TreePlacementAugment
                            .CANOPY_ACACIA_WINDSWEPT_FRINGE,
                    () -> planAcaciaWindwardFringe(
                            plan, dna, centerX, centerY, centerZ,
                            radiusX, radiusZ));
        }
    }

    private void planAcaciaWindwardFringe(
            TreePlan plan,
            TreeDna dna,
            int centerX,
            int centerY,
            int centerZ,
            int radiusX,
            int radiusZ
    ) {
        int direction = Math.floorMod(dna.branchBias(), 4);
        int dx = switch (direction) {
            case 1 -> 1;
            case 3 -> -1;
            default -> 0;
        };
        int dz = switch (direction) {
            case 0 -> -1;
            case 2 -> 1;
            default -> 0;
        };
        int sideX = -dz;
        int sideZ = dx;
        int reach = dx == 0 ? radiusZ + 1 : radiusX + 1;
        for (int side = -1; side <= 1; side++) {
            // ## Three connected edge cells create a readable wind-facing
            // tail and guarantee enough leaf mass for older branch-heavy DNA.
            plan.add(new PlannedTreeBlock(
                    centerX + (dx * reach) + (sideX * side),
                    centerY,
                    centerZ + (dz * reach) + (sideZ * side),
                    dna.species().leafMaterial(),
                    TreeBlockRole.CANOPY, Axis.Y, null));
        }
    }

    private record HorizontalStep(int x, int z) {
    }

    private void planAcaciaLowerTufts(
            TreePlan plan, TreeDna dna, int centerX, int centerY, int centerZ,
            int radiusX, int radiusZ, int branchId) {
        int desired = switch (dna.maturityStage()) {
            case SMALL -> 5;
            case MEDIUM -> 7;
            case MATURE -> 8;
            case ANCIENT -> 9;
        };
        if (dna.variant() == TreeVariant.ACACIA_WINDSWEPT
                || dna.variant()
                        == TreeVariant.ACACIA_SINGLE_FORK) {
            // ## The longer wind-facing arms need one additional supported
            // lower tuft per lobe; the sparse single-fork form needs the same
            // small mass correction around its much shorter frame.
            desired++;
        }
        int rotation = Math.floorMod(
                Long.hashCode(dna.seed()) ^ (branchId * 41),
                LOWER_FRINGE_OFFSETS.length);
        int added = 0;
        for (int index = 0; index < LOWER_FRINGE_OFFSETS.length
                && added < desired; index++) {
            int[] direction = LOWER_FRINGE_OFFSETS[
                    (rotation + index) % LOWER_FRINGE_OFFSETS.length];
            int x = centerX + (direction[0] * Math.max(1, radiusX - 1));
            int z = centerZ + (direction[1] * Math.max(1, radiusZ - 1));
            String supportKey = x + ":" + centerY + ":" + z;
            String tuftKey = x + ":" + (centerY - 2) + ":" + z;
            PlannedTreeBlock support = plan.blocksByKey().get(supportKey);
            if (support == null || support.role() != TreeBlockRole.CANOPY
                    || plan.blocksByKey().containsKey(tuftKey)) {
                continue;
            }
            plan.add(new PlannedTreeBlock(
                    x, centerY - 2, z, dna.species().leafMaterial(),
                    TreeBlockRole.CANOPY, Axis.Y, null));
            added++;
        }
    }
    private boolean usesFancyEarlyCrown(TreeDna dna) {
        // ## Oak keeps one recognizable cloud-crown grammar through every
        // stage. Switching mature oaks back to the generic blob made the
        // crown shrink during evolution.
        return dna.species() == TreeSpecies.OAK
                || dna.maturityStage().ordinal()
                        < TreeMaturityStage.MATURE.ordinal()
                && dna.species() != TreeSpecies.SPRUCE
                && dna.variant() != TreeVariant.JUNGLE_MEGA
                && dna.variant() != TreeVariant.CHERRY_LAYERED;
    }

    private void planFancyEarlyCrown(TreePlan plan, TreeDna dna, List<TreeBranchPlan.BranchTip> branchTips, Random random, int topY) {
        if (dna.species() == TreeSpecies.OAK) {
            planOakCloudCrown(
                    plan, dna, branchTips, random, topY);
            return;
        }
        int radiusCap = earlyMainCanopyCap(dna);
        int radiusX = Math.min(
                TreeSpeciesStageStyle.canopyRadiusX(dna), radiusCap);
        int radiusZ = Math.min(
                TreeSpeciesStageStyle.canopyRadiusZ(dna), radiusCap);
        int centerX = dna.trunkXAt(topY);
        int centerZ = dna.trunkZAt(topY);
        int baseY = Math.max(dna.baseY() + 2, topY - 1);

        // ## Fancy-tree early crown: stacked uneven discs first, dramatic old-growth shaping later.
        planLeafBlob(plan, dna, centerX, baseY, centerZ, radiusX, 1, radiusZ, random);
        planLeafBlob(plan, dna, centerX, baseY + 1, centerZ, Math.max(1, radiusX - 1), 1, Math.max(1, radiusZ - 1), random);
        if (dna.maturityStage() == TreeMaturityStage.MEDIUM) {
            planLeafBlob(plan, dna, centerX, baseY + 2, centerZ, Math.max(1, radiusX - 2), 1, Math.max(1, radiusZ - 2), random);
        }
        if (dna.species() == TreeSpecies.BIRCH
                && dna.maturityStage() == TreeMaturityStage.SMALL) {
            planYoungBirchTopTuft(
                    plan, dna, centerX, baseY + 2, centerZ);
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

    private void planYoungBirchTopTuft(
            TreePlan plan,
            TreeDna dna,
            int centerX,
            int centerY,
            int centerZ
    ) {
        // ## A radius-one ellipsoid is only a nine-leaf voxel cross. Four
        // shoulder leaves keep a young birch narrow while giving it a visible
        // tapered crown instead of a bare pole with one tiny cap.
        for (int[] offset : LOWER_FRINGE_OFFSETS) {
            if (Math.abs(offset[0]) + Math.abs(offset[1]) != 1) {
                continue;
            }
            plan.add(new PlannedTreeBlock(
                    centerX + offset[0], centerY,
                    centerZ + offset[1],
                    dna.species().leafMaterial(),
                    TreeBlockRole.CANOPY, Axis.Y, null));
        }
    }

    private void planOakCloudCrown(
            TreePlan plan,
            TreeDna dna,
            List<TreeBranchPlan.BranchTip> branchTips,
            Random random,
            int topY
    ) {
        int centerX = dna.trunkXAt(topY);
        int centerZ = dna.trunkZAt(topY);
        int centerY = Math.max(dna.baseY() + 3, topY - 1);
        int centralRadius = switch (dna.maturityStage()) {
            case SMALL -> 2;
            case MEDIUM -> 3;
            case MATURE -> 4;
            case ANCIENT -> 5;
        };
        planLeafBlob(
                plan, dna, centerX, centerY, centerZ,
                centralRadius, 2, centralRadius, random);

        int lobes = switch (dna.variant()) {
            case OAK_BROAD -> 4;
            case OAK_FANCY -> 4;
            case OAK_TALL -> 3;
            default -> 3;
        };
        int reach = switch (dna.maturityStage()) {
            case SMALL -> dna.variant() == TreeVariant.OAK_BROAD
                    ? 3 : 2;
            case MEDIUM -> dna.variant() == TreeVariant.OAK_BROAD
                            || dna.variant() == TreeVariant.OAK_FANCY
                    ? 4 : 3;
            case MATURE -> dna.variant() == TreeVariant.OAK_BROAD
                    ? 5 : 4;
            case ANCIENT -> dna.variant() == TreeVariant.OAK_BROAD
                    ? 6 : 5;
        };
        int lobeRadius = dna.maturityStage().ordinal()
                >= TreeMaturityStage.MATURE.ordinal() ? 3 : 2;
        int rotation = Math.floorMod(
                Long.hashCode(dna.seed()) ^ 0x0A4C10D,
                LOWER_FRINGE_OFFSETS.length);
        for (int index = 0; index < lobes; index++) {
            int[] direction = LOWER_FRINGE_OFFSETS[
                    (rotation + index * 2)
                            % LOWER_FRINGE_OFFSETS.length];
            int lobeX = centerX + direction[0] * reach;
            int lobeZ = centerZ + direction[1] * reach;
            int lobeY = centerY
                    + Math.floorMod(
                            Long.hashCode(dna.seed())
                                    + (index * 17),
                            3) - 1;
            planLeafBlob(
                    plan, dna, lobeX, lobeY, lobeZ,
                    lobeRadius, 2, lobeRadius, random);
        }

        for (TreeBranchPlan.BranchTip tip : branchTips) {
            int radius = dna.maturityStage().ordinal()
                    >= TreeMaturityStage.MATURE.ordinal() ? 3 : 2;
            planLeafBlob(
                    plan, dna, tip.x(), tip.y(), tip.z(),
                    radius, 2, radius, random);
            planNaturalLowerFringe(plan, dna, tip);
        }
        // ## Oak crowns need vertical lobes as well as horizontal reach.
        // Their overlap makes a rounded cloud while the offset lower lobe
        // prevents a flat plate beneath the branch frame.
        int[] lowerDirection = LOWER_FRINGE_OFFSETS[
                Math.floorMod(rotation + lobes, LOWER_FRINGE_OFFSETS.length)];
        planLeafBlob(
                plan, dna,
                centerX - lowerDirection[0],
                centerY + 3,
                centerZ - lowerDirection[1],
                2, 2, 2, random);
        planLeafBlob(
                plan, dna,
                centerX + lowerDirection[0],
                centerY - 2,
                centerZ + lowerDirection[1],
                2, 1, 2, random);
        planNaturalMainCrownFringe(
                plan, dna, centerX, centerY, centerZ,
                centralRadius, centralRadius);
    }

    private int mainCanopyRadius(TreeDna dna, int radius) {
        if (dna.species() != TreeSpecies.SPRUCE) {
            return radius;
        }
        int cap = switch (dna.maturityStage()) {
            case SMALL -> 2;
            case MEDIUM -> 3;
            case MATURE -> 4;
            case ANCIENT -> 5;
        };
        return Math.min(radius, cap);
    }

    private int mainCanopyVerticalRadius(TreeDna dna) {
        int radius = TreeSpeciesStageStyle.canopyRadiusY(dna);
        if (dna.species() != TreeSpecies.SPRUCE) {
            return radius;
        }
        int cap = switch (dna.maturityStage()) {
            case SMALL -> 2;
            case MEDIUM -> 3;
            case MATURE -> 4;
            case ANCIENT -> 5;
        };
        return Math.min(radius, cap);
    }

    private int earlyMainCanopyCap(TreeDna dna) {
        int base = dna.maturityStage() == TreeMaturityStage.SMALL
                ? 3 : 4;
        return switch (dna.variant()) {
            case JUNGLE_BUSH, CHERRY_COMPACT -> Math.min(2, base);
            case OAK_BROAD, DARK_OAK_BROAD,
                    MANGROVE_SPREADING -> base + 2;
            case OAK_FANCY, ACACIA_MULTI_FORK,
                    ACACIA_WINDSWEPT, CHERRY_BROAD -> base + 1;
            default -> base;
        };
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
            case JUNGLE -> switch (dna.variant()) {
                case JUNGLE_BUSH -> 1;
                case JUNGLE_SMALL -> dna.maturityStage()
                        == TreeMaturityStage.SMALL ? 1 : 2;
                case JUNGLE_LARGE -> dna.maturityStage()
                        == TreeMaturityStage.SMALL ? 2 : 3;
                case JUNGLE_MEGA -> dna.maturityStage()
                        == TreeMaturityStage.SMALL ? 2 : 4;
                default -> 2;
            };
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
            case SPRUCE -> switch (dna.maturityStage()) {
                case SMALL -> 1;
                case MEDIUM -> 2;
                case MATURE, ANCIENT -> 3;
            };
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
            double start = layerStart(dna);
            double width = layerSpan(dna);
            double progress = start + ((layer + 1.0D) / (layers + 1.0D)) * width;
            int centerY = dna.baseY() + (int) Math.round(visibleHeight * progress);
            int spread = Math.max(1, TreeSpeciesStageStyle.canopyLayerSpread(dna) - Math.max(0, layer - 1));
            int radiusX = Math.max(TreeSpeciesStageStyle.canopyRadiusX(dna), spread + random.nextInt(2));
            int radiusZ = Math.max(TreeSpeciesStageStyle.canopyRadiusZ(dna), spread + random.nextInt(2));
            int radiusY = Math.max(1, TreeSpeciesStageStyle.canopyRadiusY(dna) - 1);
            if (dna.species() == TreeSpecies.SPRUCE) {
                radiusX = Math.max(1, radiusX - layer);
                radiusZ = Math.max(1, radiusZ - layer);
                int cap = spruceLayerCap(dna, layer);
                radiusX = Math.min(radiusX, cap);
                radiusZ = Math.min(radiusZ, cap);
                radiusY = Math.min(radiusY,
                        dna.maturityStage()
                                == TreeMaturityStage.SMALL ? 1 : 2);
            } else {
                int cap = layerCanopyCap(dna, layer);
                radiusX = Math.min(radiusX, cap);
                radiusZ = Math.min(radiusZ, cap);
                radiusY = Math.min(radiusY, layerVerticalCap(dna));
            }
            planLeafBlob(plan, dna, dna.trunkXAt(centerY), centerY, dna.trunkZAt(centerY), radiusX, radiusY, radiusZ, random);
        }
    }

    private int spruceLayerCap(TreeDna dna, int layer) {
        int stageBase = switch (dna.maturityStage()) {
            case SMALL -> 3;
            case MEDIUM -> 4;
            case MATURE -> 5;
            case ANCIENT -> 6;
        };
        // ## Upper shelves taper deterministically; old DNA may describe a
        // much broader source crown but cannot inflate a young target.
        return Math.max(1, stageBase - Math.max(0, layer - 1));
    }

    private double layerStart(TreeDna dna) {
        return switch (dna.variant()) {
            case SPRUCE_CLASSIC, SPRUCE_MEGA -> 0.20D;
            case SPRUCE_PINE, SPRUCE_MEGA_PINE -> 0.44D;
            case JUNGLE_MEGA -> 0.46D;
            case CHERRY_LAYERED -> 0.38D;
            default -> dna.species() == TreeSpecies.SPRUCE
                    ? 0.30D : 0.42D;
        };
    }

    private double layerSpan(TreeDna dna) {
        return switch (dna.variant()) {
            case SPRUCE_CLASSIC, SPRUCE_MEGA -> 0.66D;
            case SPRUCE_PINE, SPRUCE_MEGA_PINE -> 0.38D;
            case JUNGLE_MEGA -> 0.34D;
            case CHERRY_LAYERED -> 0.48D;
            default -> dna.species() == TreeSpecies.SPRUCE
                    ? 0.54D : 0.42D;
        };
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
                    boolean fancyCloud = usesFancyEarlyCrown(dna);
                    // ## Every leaf blob is a rounded volume. The old
                    // non-oak Manhattan threshold admitted all four corners
                    // of wide slices, so overlapping lobes became square
                    // canopy floors even though their branch plans differed.
                    // Species identity comes from lobe placement, scale and
                    // vertical weight, not from allowing cuboid geometry.
                    double verticalWeight = switch (dna.species()) {
                        case SPRUCE -> 0.96D;
                        default -> 1.0D;
                    };
                    double normalized = (nx * nx)
                            + ((ny * verticalWeight)
                                    * (ny * verticalWeight))
                            + (nz * nz);
                    double density = fancyCloud
                            ? Math.max(TreeSpeciesStageStyle.canopyDensity(dna),
                                    fancyCanopyDensityFloor(dna))
                            : TreeSpeciesStageStyle.canopyDensity(dna);
                    double shapeLimit = fancyCloud ? 1.32D : 1.22D;
                    boolean cardinalCore = normalized <= 1.01D
                            && (x == 0 || z == 0)
                            && (y == 0 || (x == 0 && z == 0));
                    boolean solidInnerCloud = fancyCloud
                            && (normalized <= 0.62D || cardinalCore);
                    double edgeVariation = fancyCloud ? 0.08D : 0.14D;
                    if (normalized > shapeLimit
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
