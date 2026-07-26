package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TreeFocusPool {
    static final int CAPACITY = 6;

    private final Map<String, Integer> noProgressByTree = new LinkedHashMap<>();
    private int cursor;

    synchronized boolean acquire(String treeKey) {
        if (noProgressByTree.containsKey(treeKey)) {
            return false;
        }
        if (noProgressByTree.size() >= CAPACITY) {
            return false;
        }
        noProgressByTree.put(treeKey, 0);
        return true;
    }

    synchronized boolean contains(String treeKey) {
        return noProgressByTree.containsKey(treeKey);
    }

    synchronized int updateProgress(String treeKey, boolean changed) {
        Integer current = noProgressByTree.get(treeKey);
        if (current == null) {
            return 0;
        }
        int next = TreeFocusPolicy.nextNoProgressPasses(current, changed);
        noProgressByTree.put(treeKey, next);
        return next;
    }

    synchronized boolean release(String treeKey) {
        boolean removed = noProgressByTree.remove(treeKey) != null;
        normalizeCursor();
        return removed;
    }

    synchronized int size() {
        return noProgressByTree.size();
    }

    synchronized boolean isEmpty() {
        return noProgressByTree.isEmpty();
    }

    synchronized List<Entry> entries() {
        return noProgressByTree.entrySet().stream()
                .map(entry -> new Entry(entry.getKey(), entry.getValue()))
                .toList();
    }

    synchronized List<Entry> nextRotation() {
        List<Entry> entries = new ArrayList<>(entries());
        if (entries.size() <= 1) {
            return entries;
        }
        int start = Math.floorMod(cursor, entries.size());
        List<Entry> rotated = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            rotated.add(entries.get((start + index) % entries.size()));
        }
        cursor = (start + 1) % entries.size();
        return List.copyOf(rotated);
    }

    private void normalizeCursor() {
        cursor = noProgressByTree.isEmpty()
                ? 0
                : Math.floorMod(cursor, noProgressByTree.size());
    }

    record Entry(String treeKey, int noProgressPasses) {
    }
}
