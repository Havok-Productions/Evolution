package org.slowtrees.regrowth;

import java.util.ArrayDeque;
import java.util.Deque;
import org.bukkit.Material;

final class PlantDecayPlan {
    private final Material originalMaterial;
    private final Deque<DecayBlock> blocks;

    PlantDecayPlan(Material originalMaterial, Deque<DecayBlock> blocks) {
        this.originalMaterial = originalMaterial;
        this.blocks = new ArrayDeque<>(blocks);
    }

    Material originalMaterial() {
        return originalMaterial;
    }

    DecayBlock peekNext() {
        return blocks.peekFirst();
    }

    void removeNext() {
        blocks.removeFirst();
    }

    boolean isFinished() {
        return blocks.isEmpty();
    }

    record DecayBlock(int x, int y, int z) {
    }
}
