package org.evolution.features.treeevolution;

/**
 * Keeps gradual canopy construction distributed around the active trunk.
 *
 * <p>The target model may be asymmetric, but equal-score placement must not
 * consume one coordinate half before touching the others. Persisted evolved
 * leaf receipts provide a cheap, deterministic sector load without scanning
 * the live world.</p>
 */
final class TreeCanopyGrowthBalancePolicy {
    private static final double BONUS_PER_MISSING_LEAF = 18.0D;
    private static final double MAX_BONUS = 54.0D;

    private final TreeDna dna;
    private final int[] sectorLoads;
    private final int maximumLoad;

    private TreeCanopyGrowthBalancePolicy(TreeDna dna, int[] sectorLoads) {
        this.dna = dna;
        this.sectorLoads = sectorLoads;
        this.maximumLoad = Math.max(
                Math.max(sectorLoads[0], sectorLoads[1]),
                Math.max(sectorLoads[2], sectorLoads[3]));
    }

    static TreeCanopyGrowthBalancePolicy from(TreeDna dna) {
        int[] loads = new int[4];
        for (String key : dna.evolvedShapeLeaves()) {
            Coordinate coordinate = coordinate(key);
            if (coordinate == null) {
                continue;
            }
            loads[sector(
                    coordinate.x() - dna.trunkXAt(coordinate.y()),
                    coordinate.z() - dna.trunkZAt(coordinate.y()))]++;
        }
        return new TreeCanopyGrowthBalancePolicy(dna, loads);
    }

    double bonus(PlannedTreeBlock block) {
        if (block.role() != TreeBlockRole.CANOPY) {
            return 0.0D;
        }
        int dx = block.x() - blockCenterX(block);
        int dz = block.z() - blockCenterZ(block);
        int deficit = maximumLoad - sectorLoads[sector(dx, dz)];
        return Math.min(MAX_BONUS, deficit * BONUS_PER_MISSING_LEAF);
    }

    int load(PlannedTreeBlock block) {
        int dx = block.x() - blockCenterX(block);
        int dz = block.z() - blockCenterZ(block);
        return sectorLoads[sector(dx, dz)];
    }

    private int blockCenterX(PlannedTreeBlock block) {
        return dna.trunkXAt(block.y());
    }

    private int blockCenterZ(PlannedTreeBlock block) {
        return dna.trunkZAt(block.y());
    }

    private static int sector(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx < 0 ? 0 : 1;
        }
        return dz < 0 ? 2 : 3;
    }

    private static Coordinate coordinate(String key) {
        String[] parts = key.split(":");
        if (parts.length < 4) {
            return null;
        }
        try {
            int offset = parts.length - 3;
            return new Coordinate(
                    Integer.parseInt(parts[offset]),
                    Integer.parseInt(parts[offset + 1]),
                    Integer.parseInt(parts[offset + 2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record Coordinate(int x, int y, int z) {
    }
}
