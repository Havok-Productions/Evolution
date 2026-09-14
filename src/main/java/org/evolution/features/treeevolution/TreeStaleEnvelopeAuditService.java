package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;

/**
 * ## Resumable stale-envelope audit beneath the constructor audit hierarchy.
 *
 * <p>A pass examines a bounded slice and preserves its cursor. This protects a
 * Folia region from a large crown while still completing the same full audit
 * over subsequent actions.</p>
 */
final class TreeStaleEnvelopeAuditService {
    private static final int COORDINATES_PER_PASS = 256;
    private static final int MAX_CURSORS = 256;

    private final TreeLeafOwnershipIndex ownershipIndex;
    private final Map<String, Cursor> cursors = new ConcurrentHashMap<>();

    TreeStaleEnvelopeAuditService(TreeLeafOwnershipIndex ownershipIndex) {
        this.ownershipIndex = ownershipIndex;
    }

    Audit audit(TreeCandidate candidate, TreeDna dna,
            CachedTreePlan cachedPlan) {
        Cursor cursor = cursors.compute(dna.key(), (key, existing) -> {
            if (existing != null
                    && existing.signature.equals(cachedPlan.signature())) {
                return existing;
            }
            return new Cursor(cachedPlan.signature(),
                    coordinates(cachedPlan.plan()));
        });
        trim();
        synchronized (cursor) {
            int inspected = 0;
            long now = System.currentTimeMillis();
            boolean advanced = now >= cursor.nextPassMillis;
            if (advanced) {
                cursor.nextPassMillis = now + 25L;
                while (cursor.position < cursor.coordinates.size()
                        && inspected < COORDINATES_PER_PASS) {
                    Coordinate coordinate = cursor.coordinates.get(
                            cursor.position++);
                    inspected++;
                    Block leaf = inspect(
                            candidate, dna, cachedPlan, coordinate);
                    if (leaf != null) {
                        cursor.currentFindings.put(keyFor(leaf), leaf);
                    }
                }
            }
            boolean complete = advanced
                    && cursor.position >= cursor.coordinates.size();
            if (complete) {
                cursor.completedFindings = Map.copyOf(cursor.currentFindings);
                cursor.currentFindings.clear();
                cursor.position = 0;
                cursor.completedOnce = true;
            }
            Map<String, Block> visible = cursor.currentFindings.isEmpty()
                    ? cursor.completedFindings
                    : merge(cursor.completedFindings, cursor.currentFindings);
            // ## Pending without a finding keeps the audit visible to the
            // hierarchy, but does not invent a block for the prune executor.
            int effectiveCount = visible.isEmpty() && !cursor.completedOnce
                    ? 1 : visible.size();
            Block first = visible.values().stream().findFirst().orElse(null);
            return new Audit(effectiveCount, first,
                    cursor.completedOnce && complete, inspected,
                    cursor.coordinates.size());
        }
    }

    void invalidate(String treeKey) {
        cursors.remove(treeKey);
    }

    void forget(String treeKey, String blockKey) {
        Cursor cursor = cursors.get(treeKey);
        if (cursor == null) {
            return;
        }
        synchronized (cursor) {
            cursor.currentFindings.remove(blockKey);
            if (cursor.completedFindings.containsKey(blockKey)) {
                Map<String, Block> updated = new java.util.LinkedHashMap<>(
                        cursor.completedFindings);
                updated.remove(blockKey);
                cursor.completedFindings = Map.copyOf(updated);
            }
        }
    }

    void clear() {
        cursors.clear();
    }

    private Block inspect(TreeCandidate candidate, TreeDna dna,
            CachedTreePlan cachedPlan, Coordinate coordinate) {
        World world = candidate.world();
        if (!isReadable(world, coordinate.x(), coordinate.z())) {
            return null;
        }
        PlannedTreeBlock planned = cachedPlan.blocksByKey().get(
                coordinate.key());
        if (planned != null
                && planned.role() == TreeBlockRole.CANOPY
                && planned.material() == dna.species().leafMaterial()) {
            return null;
        }
        Block leaf = world.getBlockAt(
                coordinate.x(), coordinate.y(), coordinate.z());
        String leafKey = keyFor(leaf);
        if (leaf.getType() != dna.species().leafMaterial()
                || !candidate.naturalKeys().contains(leafKey)
                || !(leaf.getBlockData() instanceof Leaves leaves)
                || !leaves.isPersistent()
                || ownershipIndex.classify(dna, leafKey)
                        != TreeStaleEnvelopeOwnershipPolicy.Decision
                                .RETIRE_EXCLUSIVE_EVOLVED_LEAF) {
            return null;
        }
        return leaf;
    }

    private static List<Coordinate> coordinates(TreePlan plan) {
        List<Coordinate> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (TreeBranchPlan.BranchTip tip : plan.branchEnvelopeCleanupTips()) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        Coordinate coordinate = new Coordinate(
                                tip.x() + dx, tip.y() + dy, tip.z() + dz);
                        if (visited.add(coordinate.key())) {
                            result.add(coordinate);
                        }
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static Map<String, Block> merge(
            Map<String, Block> first, Map<String, Block> second) {
        Map<String, Block> merged = new java.util.LinkedHashMap<>(first);
        merged.putAll(second);
        return merged;
    }

    private void trim() {
        if (cursors.size() <= MAX_CURSORS) {
            return;
        }
        String oldest = cursors.keySet().stream().findFirst().orElse(null);
        if (oldest != null) {
            cursors.remove(oldest);
        }
    }

    private static boolean isReadable(World world, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        return world.isChunkLoaded(chunkX, chunkZ)
                && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0);
    }

    private static String keyFor(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":"
                + block.getY() + ":" + block.getZ();
    }

    record Audit(int count, Block first, boolean complete,
            int inspected, int total) {
    }

    private record Coordinate(int x, int y, int z) {
        private String key() {
            return x + ":" + y + ":" + z;
        }
    }

    private static final class Cursor {
        private final String signature;
        private final List<Coordinate> coordinates;
        private final Map<String, Block> currentFindings =
                new java.util.LinkedHashMap<>();
        private Map<String, Block> completedFindings = Map.of();
        private int position;
        private boolean completedOnce;
        private long nextPassMillis;

        private Cursor(String signature, List<Coordinate> coordinates) {
            this.signature = signature;
            this.coordinates = coordinates;
        }
    }
}
