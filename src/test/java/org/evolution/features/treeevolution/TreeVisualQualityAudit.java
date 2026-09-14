package org.evolution.features.treeevolution;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Cell;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Ownership;

/**
 * ## Independent voxel-quality audit for generated and live replay trees.
 *
 * <p>This deliberately does not ask the planner whether its own output is
 * correct. It measures the resulting silhouette, support, crown continuity,
 * branch exposure, and species proportions from occupied voxels.
 */
final class TreeVisualQualityAudit {
    private static final int[][] FACE_NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private TreeVisualQualityAudit() {
    }

    static Report auditPlan(TreeDna dna, TreePlan plan) {
        List<Voxel> voxels = plan.orderedBlocks().stream()
                .filter(TreeVisualQualityAudit::isTreeBody)
                .map(block -> new Voxel(
                        block.x(), block.y(), block.z(), block.role(), true))
                .toList();
        return audit(dna, plan, voxels, true, 1.0D, 1.0D);
    }

    static Report auditReplay(
            TreeDna dna,
            TreePlan plan,
            Map<String, Cell> cells,
            boolean finalState,
            double branchProgress,
            double canopyProgress
    ) {
        List<Voxel> voxels = new ArrayList<>();
        for (Map.Entry<String, Cell> entry : cells.entrySet()) {
            Cell cell = entry.getValue();
            if (!isTreeBody(cell.role())
                    || cell.ownership() == Ownership.NEIGHBOR) {
                continue;
            }
            Coordinate coordinate = coordinate(entry.getKey());
            voxels.add(new Voxel(
                    coordinate.x(), coordinate.y(), coordinate.z(),
                    cell.role(), cell.ownership() == Ownership.EVOLVED));
        }
        return audit(
                dna, plan, voxels, finalState,
                branchProgress, canopyProgress);
    }

    private static Report audit(
            TreeDna dna,
            TreePlan plan,
            Collection<Voxel> voxels,
            boolean finalState,
            double branchProgress,
            double canopyProgress
    ) {
        Map<String, Voxel> byKey = new LinkedHashMap<>();
        List<Voxel> wood = new ArrayList<>();
        List<Voxel> leaves = new ArrayList<>();
        for (Voxel voxel : voxels) {
            byKey.put(voxel.key(), voxel);
            if (voxel.role() == TreeBlockRole.CANOPY) {
                leaves.add(voxel);
            } else if (isWood(voxel.role())) {
                wood.add(voxel);
            }
        }

        Bounds woodBounds = Bounds.of(wood);
        Bounds leafBounds = Bounds.of(leaves);
        int disconnectedWood = disconnectedWood(byKey, wood, dna);
        List<Voxel> auditableLeaves = finalState
                ? leaves
                : leaves.stream().filter(Voxel::evolved).toList();
        TreeCrownVolumeAudit.Coverage crownVolume =
                TreeCrownVolumeAudit.inspect(
                        dna, plan, auditableLeaves.stream()
                                .map(leaf -> new TreeCrownVolumeAudit.Point(
                                        leaf.x(), leaf.y(), leaf.z()))
                                .toList());
        ComponentReport canopyComponents =
                canopyComponents(auditableLeaves, wood);
        int maximumBareBranchRun = maximumBareBranchRun(
                dna, plan, byKey);
        int uncoveredLiveTips = uncoveredLiveTips(
                dna, plan, byKey);
        double flatBottomRatio = flatBottomRatio(leaves);
        double leafWoodRatio = wood.isEmpty()
                ? 0.0D : leaves.size() / (double) wood.size();
        int crownWidth = leafBounds.empty()
                ? 0 : Math.max(leafBounds.widthX(), leafBounds.widthZ());
        int crownHeight = leafBounds.empty() ? 0 : leafBounds.height();
        int treeHeight = woodBounds.empty() ? 0 : woodBounds.height();
        double crownAspect = crownHeight == 0
                ? 0.0D : crownWidth / (double) crownHeight;
        long crownEnvelope = leafBounds.empty() ? 0L
                : (long) leafBounds.widthX()
                        * leafBounds.height()
                        * leafBounds.widthZ();
        double crownFill = crownEnvelope == 0L
                ? 0.0D : leaves.size() / (double) crownEnvelope;
        TreeCrownSilhouetteAudit.Report silhouette =
                TreeCrownSilhouetteAudit.inspect(
                        dna,
                        leaves.stream()
                                .map(leaf ->
                                        new TreeCrownSilhouetteAudit.Point(
                                                leaf.x(), leaf.y(),
                                                leaf.z()))
                                .toList(),
                        finalState);
        List<TreeSpruceSilhouetteAudit.Point> spruceCrown =
                new ArrayList<>();
        leaves.forEach(leaf -> spruceCrown.add(
                new TreeSpruceSilhouetteAudit.Point(
                        leaf.x(), leaf.y(), leaf.z())));
        // ## Horizontal branch shelves are part of a conifer's visible crown.
        // Measuring leaves alone turns a log shelf into a false one-block
        // waist, while including TRUNK would hide a genuinely missing crown.
        wood.stream()
                .filter(voxel -> voxel.role() == TreeBlockRole.BRANCH)
                .forEach(branch -> spruceCrown.add(
                        new TreeSpruceSilhouetteAudit.Point(
                                branch.x(), branch.y(), branch.z())));
        TreeSpruceSilhouetteAudit.Report spruceSilhouette =
                TreeSpruceSilhouetteAudit.inspect(dna, spruceCrown);

        List<String> failures = new ArrayList<>();
        if (disconnectedWood > 0) {
            failures.add("disconnected-wood=" + disconnectedWood);
        }
        if (canopyComponents.unsupported() > 0) {
            failures.add("unsupported-canopy-components="
                    + canopyComponents.unsupported());
        }
        // ## A supported branch may own a visually separate cloud lobe. Cap
        // components by the number of real branch envelopes instead of an
        // arbitrary species constant; unsupported islands still fail above.
        int canopyComponentLimit = Math.max(
                2, Math.min(6, plan.branchPlans().size()));
        if (finalState
                && canopyComponents.total() > canopyComponentLimit) {
            failures.add("fragmented-canopy-components="
                    + canopyComponents.total() + " limit="
                    + canopyComponentLimit);
        }
        failures.addAll(silhouette.failures());
        failures.addAll(spruceSilhouette.failures());

        int bareLimit = bareBranchLimit(dna);
        if (maximumBareBranchRun > bareLimit
                && (finalState || canopyProgress >= 0.18D)) {
            failures.add("bare-branch-run=" + maximumBareBranchRun
                    + " limit=" + bareLimit);
        }
        if (uncoveredLiveTips > 0
                && (finalState || branchProgress >= 0.999D)) {
            failures.add("uncovered-branch-tips=" + uncoveredLiveTips);
        }
        if (finalState
                && dna.species() == TreeSpecies.OAK
                && flatBottomRatio > 0.34D
                && crownAspect > 1.70D) {
            failures.add("oak-platform-crown flat-bottom="
                    + round(flatBottomRatio)
                    + " aspect=" + round(crownAspect));
        }
        if (finalState
                && dna.species() == TreeSpecies.OAK
                && crownHeight >= 7
                && crownFill > 0.36D) {
            failures.add("oak-over-solid-crown fill="
                    + round(crownFill));
        }
        if (finalState && dna.species() == TreeSpecies.ACACIA) {
            long trunkColumns = plan.orderedBlocks().stream()
                    .filter(block ->
                            block.role() == TreeBlockRole.TRUNK)
                    .map(block -> block.x() + ":" + block.z())
                    .distinct()
                    .count();
            if ((dna.leanX() != 0 || dna.leanZ() != 0)
                    && trunkColumns < 2) {
                failures.add("acacia-planned-lean-not-visible");
            }
        }
        if (finalState
                && dna.species() == TreeSpecies.SPRUCE
                && dna.maturityStage() == TreeMaturityStage.SMALL
                && crownWidth > Math.max(8, treeHeight)) {
            failures.add("young-spruce-too-squat width="
                    + crownWidth + " tree-height=" + treeHeight);
        }
        int requiredCrownSectors = finalState
                || canopyProgress
                        >= TreeCanopyTransitionPolicy
                                .minimumReplacementCanopy(dna)
                ? crownVolume.plannedSectors()
                : Math.max(1, crownVolume.plannedSectors() - 1);
        if ((finalState || canopyProgress >= 0.18D)
                && auditableLeaves.size() >= 12
                && crownVolume.occupiedSectors()
                        < requiredCrownSectors) {
            failures.add("one-sided-crown-sectors="
                    + crownVolume.occupiedSectors() + "/"
                    + requiredCrownSectors
                    + " planned=" + crownVolume.plannedSectors());
        }

        if (finalState) {
            if (wood.isEmpty()) {
                failures.add("missing-wood");
            }
            if (leaves.isEmpty()) {
                failures.add("missing-canopy");
            }
            if (flatBottomRatio > flatBottomLimit(dna)
                    && leaves.size() >= 20) {
                failures.add("flat-canopy-bottom="
                        + round(flatBottomRatio)
                        + " limit=" + flatBottomLimit(dna));
            }
            if (leafWoodRatio < minimumLeafWoodRatio(dna)) {
                failures.add("thin-canopy-ratio=" + round(leafWoodRatio)
                        + " minimum=" + minimumLeafWoodRatio(dna));
            }
            int minimumAxisWidth =
                    TreeCrownVolumeAudit.minimumAxisWidth(dna);
            if (crownVolume.widthX() < minimumAxisWidth) {
                failures.add("crown-x-clipped="
                        + crownVolume.widthX()
                        + " minimum=" + minimumAxisWidth);
            }
            if (crownVolume.widthZ() < minimumAxisWidth) {
                failures.add("crown-z-clipped="
                        + crownVolume.widthZ()
                        + " minimum=" + minimumAxisWidth);
            }
            applySpeciesRules(
                    dna, crownWidth, crownHeight, treeHeight,
                    crownAspect, leafBounds, woodBounds, failures);
        }

        return new Report(
                failures.isEmpty(),
                List.copyOf(failures),
                wood.size(),
                leaves.size(),
                disconnectedWood,
                canopyComponents.total(),
                canopyComponents.unsupported(),
                maximumBareBranchRun,
                uncoveredLiveTips,
                round(flatBottomRatio),
                round(leafWoodRatio),
                round(crownFill),
                crownWidth,
                crownVolume.widthX(),
                crownVolume.widthZ(),
                crownVolume.occupiedSectors(),
                crownHeight,
                treeHeight,
                round(crownAspect),
                round(silhouette.maximumLayerFill()),
                round(silhouette.maximumStraightBorder()),
                silhouette.repeatedLayerPairs(),
                silhouette.bottomLevels(),
                spruceSilhouette.internalGaps(),
                spruceSilhouette.postPeakWidenings(),
                spruceSilhouette.maximumWidth(),
                spruceSilhouette.topWidth());
    }

    private static void applySpeciesRules(
            TreeDna dna,
            int crownWidth,
            int crownHeight,
            int treeHeight,
            double crownAspect,
            Bounds leafBounds,
            Bounds woodBounds,
            List<String> failures
    ) {
        int stage = dna.maturityStage().ordinal();
        int minimumWidth = switch (dna.species()) {
            case BIRCH -> stage <= 1 ? 3 : 4;
            case SPRUCE -> stage <= 1 ? 5 : 7;
            case ACACIA -> stage <= 1 ? 5 : 7;
            case JUNGLE, DARK_OAK -> stage <= 1 ? 5 : 8;
            case OAK, MANGROVE, CHERRY -> stage <= 1 ? 5 : 7;
        };
        if (crownWidth < minimumWidth) {
            failures.add("crown-too-narrow=" + crownWidth
                    + " minimum=" + minimumWidth);
        }

        int minimumCrownHeight = switch (dna.species()) {
            case ACACIA -> 2;
            case CHERRY -> stage <= 1 ? 3 : 4;
            case BIRCH -> stage <= 1 ? 3 : 5;
            case SPRUCE -> Math.max(4, treeHeight / 3);
            default -> stage <= 1 ? 3 : 5;
        };
        if (crownHeight < minimumCrownHeight) {
            failures.add("crown-too-shallow=" + crownHeight
                    + " minimum=" + minimumCrownHeight);
        }

        if (dna.species() == TreeSpecies.BIRCH
                && crownWidth > Math.max(7, treeHeight / 2 + 2)) {
            failures.add("birch-crown-too-wide=" + crownWidth);
        }
        if (dna.species() == TreeSpecies.ACACIA
                && crownAspect < 1.25D) {
            failures.add("acacia-not-umbrella-shaped="
                    + round(crownAspect));
        }
        if (dna.species() == TreeSpecies.SPRUCE
                && crownHeight < Math.max(4, crownWidth - 1)) {
            failures.add("spruce-not-conical-enough width="
                    + crownWidth + " height=" + crownHeight);
        }

        if (!leafBounds.empty() && !woodBounds.empty()) {
            int crownStart = leafBounds.minY() - woodBounds.minY();
            double crownStartRatio = crownStart
                    / (double) Math.max(1, treeHeight);
            double maximumStart = switch (dna.species()) {
                case JUNGLE, ACACIA -> 0.82D;
                case BIRCH -> 0.78D;
                default -> 0.72D;
            };
            if (crownStartRatio > maximumStart) {
                failures.add("crown-starts-too-high="
                        + round(crownStartRatio)
                        + " maximum=" + maximumStart);
            }
        }
    }

    private static int disconnectedWood(
            Map<String, Voxel> byKey,
            List<Voxel> wood,
            TreeDna dna
    ) {
        if (wood.isEmpty()) {
            return 0;
        }
        Set<String> visited = new HashSet<>();
        Deque<Voxel> pending = new ArrayDeque<>();
        wood.stream()
                .filter(voxel -> voxel.y() == dna.baseY())
                .min(Comparator.comparingInt(voxel ->
                        Math.abs(voxel.x() - dna.baseX())
                                + Math.abs(voxel.z() - dna.baseZ())))
                .or(() -> wood.stream().min(Comparator.comparingInt(Voxel::y)))
                .ifPresent(voxel -> {
                    visited.add(voxel.key());
                    pending.add(voxel);
                });
        while (!pending.isEmpty()) {
            Voxel current = pending.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Voxel next = byKey.get(key(
                                current.x() + dx,
                                current.y() + dy,
                                current.z() + dz));
                        if (next != null && isWood(next.role())
                                && visited.add(next.key())) {
                            pending.addLast(next);
                        }
                    }
                }
            }
        }
        return Math.max(0, wood.size() - visited.size());
    }

    private static ComponentReport canopyComponents(
            List<Voxel> leaves,
            List<Voxel> wood
    ) {
        Map<String, Voxel> remaining = new HashMap<>();
        leaves.forEach(leaf -> remaining.put(leaf.key(), leaf));
        int total = 0;
        int unsupported = 0;
        while (!remaining.isEmpty()) {
            Voxel first = remaining.values().iterator().next();
            Deque<Voxel> pending = new ArrayDeque<>();
            List<Voxel> component = new ArrayList<>();
            pending.add(first);
            remaining.remove(first.key());
            while (!pending.isEmpty()) {
                Voxel current = pending.removeFirst();
                component.add(current);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            String neighbor = key(
                                    current.x() + dx,
                                    current.y() + dy,
                                    current.z() + dz);
                            Voxel found = remaining.remove(neighbor);
                            if (found != null) {
                                pending.addLast(found);
                            }
                        }
                    }
                }
            }
            total++;
            boolean supported = component.stream().anyMatch(leaf ->
                    wood.stream().anyMatch(log ->
                            chebyshev(leaf, log) <= 2));
            if (!supported) {
                unsupported++;
            }
        }
        return new ComponentReport(total, unsupported);
    }

    private static int maximumBareBranchRun(
            TreeDna dna,
            TreePlan plan,
            Map<String, Voxel> byKey
    ) {
        int maximum = 0;
        for (TreeBranchPlan branch : plan.branchPlans()) {
            int run = 0;
            for (TreeBranchPlan.BranchSegment segment
                    : branch.segments().stream()
                    .sorted(Comparator.comparingInt(
                            TreeBranchPlan.BranchSegment::step))
                    .toList()) {
                Voxel live = byKey.get(key(
                        segment.x(), segment.y(), segment.z()));
                if (live == null || live.role() != TreeBlockRole.BRANCH) {
                    run = 0;
                    continue;
                }
                if (hasLeafNear(byKey, live, 2, true)) {
                    run = 0;
                } else {
                    run++;
                    maximum = Math.max(maximum, run);
                }
            }
        }
        return maximum;
    }

    private static int uncoveredLiveTips(
            TreeDna dna,
            TreePlan plan,
            Map<String, Voxel> byKey
    ) {
        int uncovered = 0;
        for (TreeBranchPlan branch : plan.branchPlans()) {
            TreeBranchPlan.BranchTip tip = branch.tip();
            Voxel live = byKey.get(key(tip.x(), tip.y(), tip.z()));
            if (live != null
                    && live.role() == TreeBlockRole.BRANCH
                    && !hasLeafNear(byKey, live, 2, true)) {
                uncovered++;
            }
        }
        return uncovered;
    }

    private static boolean hasLeafNear(
            Map<String, Voxel> byKey,
            Voxel center,
            int radius,
            boolean requireEvolved
    ) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Voxel leaf = byKey.get(key(
                            center.x() + dx,
                            center.y() + dy,
                            center.z() + dz));
                    if (leaf != null
                            && leaf.role() == TreeBlockRole.CANOPY
                            && (!requireEvolved || leaf.evolved())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static double flatBottomRatio(List<Voxel> leaves) {
        if (leaves.isEmpty()) {
            return 0.0D;
        }
        Map<String, Integer> columnBottom = new HashMap<>();
        for (Voxel leaf : leaves) {
            columnBottom.merge(
                    leaf.x() + ":" + leaf.z(),
                    leaf.y(), Math::min);
        }
        Map<Integer, Integer> frequencies = new HashMap<>();
        columnBottom.values().forEach(y ->
                frequencies.merge(y, 1, Integer::sum));
        int mode = frequencies.values().stream()
                .mapToInt(Integer::intValue).max().orElse(0);
        return mode / (double) Math.max(1, columnBottom.size());
    }

    private static int bareBranchLimit(TreeDna dna) {
        int base = switch (dna.species()) {
            case BIRCH -> 2;
            case ACACIA, JUNGLE -> 4;
            case SPRUCE -> 3;
            default -> 3;
        };
        return base + (dna.maturityStage().ordinal() >= 2 ? 1 : 0);
    }

    private static double flatBottomLimit(TreeDna dna) {
        return switch (dna.species()) {
            case ACACIA -> 0.92D;
            case SPRUCE -> 0.86D;
            case BIRCH, CHERRY -> 0.82D;
            default -> 0.76D;
        };
    }

    private static double minimumLeafWoodRatio(TreeDna dna) {
        return switch (dna.species()) {
            // ## Acacia leftovers can remain connected and fully leaf-covered
            // while still becoming a dense wooden knot. Production stages
            // need a substantially leafier umbrella than mature landmarks.
            case ACACIA -> switch (dna.maturityStage()) {
                case SMALL -> 2.20D;
                case MEDIUM -> 3.20D;
                case MATURE -> 1.50D;
                case ANCIENT -> 1.80D;
            };
            case BIRCH -> 1.8D;
            case JUNGLE, SPRUCE -> 1.5D;
            default -> 2.0D;
        };
    }

    private static int chebyshev(Voxel first, Voxel second) {
        return Math.max(
                Math.max(
                        Math.abs(first.x() - second.x()),
                        Math.abs(first.y() - second.y())),
                Math.abs(first.z() - second.z()));
    }

    private static boolean isTreeBody(PlannedTreeBlock block) {
        return isTreeBody(block.role());
    }

    private static boolean isTreeBody(TreeBlockRole role) {
        return role == TreeBlockRole.TRUNK
                || role == TreeBlockRole.BRANCH
                || role == TreeBlockRole.CANOPY;
    }

    private static boolean isWood(TreeBlockRole role) {
        return role == TreeBlockRole.TRUNK
                || role == TreeBlockRole.BRANCH;
    }

    private static Coordinate coordinate(String value) {
        String[] parts = value.split(":");
        int offset = parts.length - 3;
        return new Coordinate(
                Integer.parseInt(parts[offset]),
                Integer.parseInt(parts[offset + 1]),
                Integer.parseInt(parts[offset + 2]));
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    record Report(
            boolean passed,
            List<String> failures,
            int wood,
            int leaves,
            int disconnectedWood,
            int canopyComponents,
            int unsupportedCanopyComponents,
            int maximumBareBranchRun,
            int uncoveredBranchTips,
            double flatBottomRatio,
            double leafWoodRatio,
            double crownFill,
            int crownWidth,
            int crownWidthX,
            int crownWidthZ,
            int crownSectors,
            int crownHeight,
            int treeHeight,
            double crownAspect,
            double maximumLayerFill,
            double maximumStraightBorder,
            int repeatedLayerPairs,
            int canopyBottomLevels,
            int spruceInternalGaps,
            int sprucePostPeakWidenings,
            int spruceMaximumWidth,
            int spruceTopWidth
    ) {
        Report withFailure(String failure) {
            if (failure == null || failure.isBlank()
                    || failures.contains(failure)) {
                return this;
            }
            List<String> updated = new ArrayList<>(failures);
            updated.add(failure);
            return new Report(
                    false, List.copyOf(updated),
                    wood, leaves, disconnectedWood,
                    canopyComponents, unsupportedCanopyComponents,
                    maximumBareBranchRun, uncoveredBranchTips,
                    flatBottomRatio, leafWoodRatio, crownFill,
                    crownWidth, crownWidthX, crownWidthZ,
                    crownSectors, crownHeight, treeHeight,
                    crownAspect, maximumLayerFill,
                    maximumStraightBorder, repeatedLayerPairs,
                    canopyBottomLevels, spruceInternalGaps,
                    sprucePostPeakWidenings,
                    spruceMaximumWidth, spruceTopWidth);
        }

        String metrics() {
            return "wood=" + wood
                    + " leaves=" + leaves
                    + " components=" + canopyComponents
                    + " unsupported=" + unsupportedCanopyComponents
                    + " bare-run=" + maximumBareBranchRun
                    + " uncovered-tips=" + uncoveredBranchTips
                    + " flat-bottom=" + flatBottomRatio
                    + " crown-fill=" + crownFill
                    + " crown=" + crownWidthX + "x"
                    + crownHeight + "x" + crownWidthZ
                    + " sectors=" + crownSectors + "/4"
                    + " tree-height=" + treeHeight
                    + " aspect=" + crownAspect
                    + " layer-fill=" + maximumLayerFill
                    + " straight-border=" + maximumStraightBorder
                    + " repeated-layers=" + repeatedLayerPairs
                    + " bottom-levels=" + canopyBottomLevels
                    + " spruce-gaps=" + spruceInternalGaps
                    + " spruce-widenings="
                    + sprucePostPeakWidenings
                    + " spruce-width=" + spruceMaximumWidth
                    + "->" + spruceTopWidth;
        }

        String failureSummary() {
            return failures.isEmpty() ? "none" : String.join("|", failures);
        }
    }

    private record Voxel(
            int x,
            int y,
            int z,
            TreeBlockRole role,
            boolean evolved
    ) {
        String key() {
            return TreeVisualQualityAudit.key(x, y, z);
        }
    }

    private record Coordinate(int x, int y, int z) {
    }

    private record ComponentReport(int total, int unsupported) {
    }

    private record Bounds(
            boolean empty,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ
    ) {
        static Bounds of(Collection<Voxel> voxels) {
            if (voxels.isEmpty()) {
                return new Bounds(true, 0, 0, 0, 0, 0, 0);
            }
            return new Bounds(
                    false,
                    voxels.stream().mapToInt(Voxel::x).min().orElse(0),
                    voxels.stream().mapToInt(Voxel::x).max().orElse(0),
                    voxels.stream().mapToInt(Voxel::y).min().orElse(0),
                    voxels.stream().mapToInt(Voxel::y).max().orElse(0),
                    voxels.stream().mapToInt(Voxel::z).min().orElse(0),
                    voxels.stream().mapToInt(Voxel::z).max().orElse(0));
        }

        int widthX() {
            return empty ? 0 : maxX - minX + 1;
        }

        int widthZ() {
            return empty ? 0 : maxZ - minZ + 1;
        }

        int height() {
            return empty ? 0 : maxY - minY + 1;
        }
    }
}
