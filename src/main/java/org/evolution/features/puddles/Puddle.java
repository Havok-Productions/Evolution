package org.evolution.features.puddles;

import java.util.Objects;
import java.util.UUID;

final class Puddle {
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private int depth;

    Puddle(UUID worldId, int x, int y, int z, int depth) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.depth = Math.max(1, depth);
    }

    UUID worldId() {
        return worldId;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    int z() {
        return z;
    }

    int depth() {
        return depth;
    }

    void soak(int maxDepth) {
        if (depth < maxDepth) {
            depth++;
        }
    }

    void dry() {
        depth--;
    }

    boolean isDry() {
        return depth <= 0;
    }

    String columnKey() {
        return worldId + ":" + x + ":" + z;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Puddle puddle)) {
            return false;
        }
        return x == puddle.x && y == puddle.y && z == puddle.z && worldId.equals(puddle.worldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, x, y, z);
    }
}