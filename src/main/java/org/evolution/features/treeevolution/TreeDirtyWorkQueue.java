package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ## Ordered tree-work queue used ahead of broad candidate discovery.
 *
 * <p>Mutated or damaged trees are revisited first. Distance filtering happens
 * without world access, so an unrelated player's region never drains work it
 * cannot safely own.</p>
 */
final class TreeDirtyWorkQueue {
    private static final int MAX_ENTRIES = 4096;
    private final LinkedHashSet<String> treeKeys = new LinkedHashSet<>();

    synchronized void mark(String treeKey) {
        if (treeKey == null || treeKey.isBlank()) {
            return;
        }
        treeKeys.remove(treeKey);
        treeKeys.add(treeKey);
        while (treeKeys.size() > MAX_ENTRIES) {
            treeKeys.remove(treeKeys.iterator().next());
        }
    }

    synchronized void remove(String treeKey) {
        treeKeys.remove(treeKey);
    }

    synchronized boolean contains(String treeKey) {
        return treeKeys.contains(treeKey);
    }

    synchronized List<String> takeNear(UUID worldId, int x, int z,
            int radius, Map<String, TreeDna> records, int limit) {
        List<String> result = new ArrayList<>();
        long radiusSquared = (long) radius * radius;
        Iterator<String> iterator = treeKeys.iterator();
        while (iterator.hasNext() && result.size() < Math.max(0, limit)) {
            String key = iterator.next();
            TreeDna dna = records.get(key);
            if (dna == null) {
                iterator.remove();
                continue;
            }
            if (!dna.worldId().equals(worldId)) {
                continue;
            }
            long dx = (long) dna.baseX() - x;
            long dz = (long) dna.baseZ() - z;
            if ((dx * dx) + (dz * dz) > radiusSquared) {
                continue;
            }
            iterator.remove();
            result.add(key);
        }
        return List.copyOf(result);
    }

    synchronized int size() {
        return treeKeys.size();
    }

    synchronized void clear() {
        treeKeys.clear();
    }
}
