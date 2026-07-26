package org.evolution.features.regrowth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.block.BlockState;

final class ActiveRegrowth {
    private static final long MILLIS_PER_TICK = 50L;

    private final PendingRegrowth pending;
    private final Deque<BlockState> plannedBlocks;
    private final Set<String> placedBlockKeys = new HashSet<>();
    private long nextPlacementNotBeforeMillis;

    ActiveRegrowth(PendingRegrowth pending, Deque<BlockState> plannedBlocks) {
        this.pending = pending;
        this.plannedBlocks = plannedBlocks;
    }

    PendingRegrowth pending() {
        return pending;
    }

    synchronized BlockState pollNextBlock() {
        return plannedBlocks.pollFirst();
    }

    synchronized void requeueFirst(BlockState state) {
        plannedBlocks.addFirst(state);
    }

    synchronized boolean isFinished() {
        return plannedBlocks.isEmpty();
    }

    synchronized int remainingBlockCount() {
        return plannedBlocks.size();
    }

    synchronized void markPlaced(String blockKey) {
        placedBlockKeys.add(blockKey);
    }

    synchronized void unmarkPlaced(String blockKey) {
        placedBlockKeys.remove(blockKey);
    }

    synchronized Set<String> placedBlockKeysSnapshot() {
        return new HashSet<>(placedBlockKeys);
    }

    synchronized int placedBlockCount() {
        return placedBlockKeys.size();
    }

    void resetCooldown(long ticks) {
        nextPlacementNotBeforeMillis = System.currentTimeMillis() + ticksToMillis(ticks);
    }

    long remainingCooldownTicks() {
        long remainingMillis = nextPlacementNotBeforeMillis - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            return 0L;
        }

        return Math.max(1L, (remainingMillis + MILLIS_PER_TICK - 1L) / MILLIS_PER_TICK);
    }

    private static long ticksToMillis(long ticks) {
        return Math.max(1L, ticks) * MILLIS_PER_TICK;
    }
}
