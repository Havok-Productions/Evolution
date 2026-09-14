package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Cell;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Ownership;

/**
 * ## Full padded environment fixture, including implicit air coordinates.
 */
final class TreeReplayNeighborhoodSnapshot {
    private static final int MINIMUM_RADIUS = 16;

    private TreeReplayNeighborhoodSnapshot() {
    }

    static TreeConstructionReplayWorld seed(
            TreeDna dna,
            TreePlan plan,
            Environment environment
    ) {
        TreeConstructionReplayWorld world =
                new TreeConstructionReplayWorld();
        List<PlannedTreeBlock> targets = plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.TRUNK
                        || block.role() == TreeBlockRole.BRANCH
                        || block.role() == TreeBlockRole.CANOPY)
                .toList();
        TreeConstructionReplaySeeder.seed(
                dna, plan, targets, world, false,
                TreeCapturedEnvironmentFixture.empty());
        int radius = radius(dna, plan);
        for (int x = dna.baseX() - radius;
                x <= dna.baseX() + radius; x++) {
            for (int z = dna.baseZ() - radius;
                    z <= dna.baseZ() + radius; z++) {
                seedIfEmpty(
                        world, key(x, dna.baseY() - 1, z),
                        environment == Environment.WET_EDGE
                                && x > dna.baseX() + radius / 2
                                ? Material.WATER : Material.GRASS_BLOCK,
                        TreeBlockRole.GROUND_DETAIL);
            }
        }
        if (environment == Environment.PLAYER_EDGE) {
            for (int y = dna.baseY();
                    y <= dna.baseY() + 3; y++) {
                seedIfEmpty(
                        world,
                        key(dna.baseX() + radius - 1, y,
                                dna.baseZ() + 2),
                        Material.STONE,
                        TreeBlockRole.GROUND_DETAIL);
            }
        } else if (environment == Environment.CANOPY_OVERLAP) {
            plan.orderedBlocks().stream()
                    .filter(block ->
                            block.role() == TreeBlockRole.CANOPY)
                    .filter(block ->
                            block.x() > dna.baseX())
                    .limit(12)
                    .forEach(block -> seedIfEmpty(
                            world, block.key(),
                            dna.species().leafMaterial(),
                            TreeBlockRole.CANOPY));
        }
        return world;
    }

    static Snapshot capture(
            TreeDna dna,
            TreePlan plan,
            TreeConstructionReplayWorld world,
            Environment environment
    ) {
        int radius = radius(dna, plan);
        int minY = dna.baseY() - 1;
        int maxY = plan.orderedBlocks().stream()
                .mapToInt(PlannedTreeBlock::y)
                .max().orElse(dna.baseY()) + 2;
        long total = 0;
        long air = 0;
        long source = 0;
        long evolved = 0;
        long neighbor = 0;
        long plannedMissing = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int z = dna.baseZ() - radius;
                    z <= dna.baseZ() + radius; z++) {
                for (int x = dna.baseX() - radius;
                        x <= dna.baseX() + radius; x++) {
                    total++;
                    String key = key(x, y, z);
                    Cell cell = world.cell(key);
                    if (cell == null) {
                        air++;
                        if (plan.blocksByKey().containsKey(key)) {
                            plannedMissing++;
                        }
                    } else {
                        switch (cell.ownership()) {
                            case SOURCE -> source++;
                            case EVOLVED -> evolved++;
                            case NEIGHBOR -> neighbor++;
                        }
                    }
                }
            }
        }
        return new Snapshot(
                environment, radius, minY, maxY,
                total, air, source, evolved, neighbor,
                plannedMissing,
                render(dna, plan, world, radius, minY, maxY));
    }

    private static String render(
            TreeDna dna,
            TreePlan plan,
            TreeConstructionReplayWorld world,
            int radius,
            int minY,
            int maxY
    ) {
        StringBuilder output = new StringBuilder();
        output.append("## Full padded live-region fixture")
                .append(System.lineSeparator());
        output.append("## x/z radius=").append(radius)
                .append(" y=").append(minY).append("..")
                .append(maxY).append(System.lineSeparator());
        output.append("## .=air g=ground W=water X=foreign ")
                .append("s/o=source T/B/L=evolved N=neighbor ")
                .append("p=planned-missing")
                .append(System.lineSeparator());
        for (int y = maxY; y >= minY; y--) {
            output.append("y=").append(y)
                    .append(" rel=").append(y - dna.baseY())
                    .append(System.lineSeparator());
            for (int z = dna.baseZ() - radius;
                    z <= dna.baseZ() + radius; z++) {
                for (int x = dna.baseX() - radius;
                        x <= dna.baseX() + radius; x++) {
                    String key = key(x, y, z);
                    output.append(symbol(
                            world.cell(key),
                            plan.blocksByKey().get(key)));
                }
                output.append(System.lineSeparator());
            }
        }
        return output.toString();
    }

    private static char symbol(Cell cell, PlannedTreeBlock target) {
        if (cell == null) {
            return target == null ? '.' : 'p';
        }
        if (cell.material() == Material.WATER) {
            return 'W';
        }
        if (cell.material() == Material.GRASS_BLOCK) {
            return 'g';
        }
        if (cell.material() == Material.STONE) {
            return 'X';
        }
        if (cell.ownership() == Ownership.NEIGHBOR) {
            return 'N';
        }
        if (cell.ownership() == Ownership.SOURCE) {
            return cell.role() == TreeBlockRole.CANOPY ? 'o' : 's';
        }
        return switch (cell.role()) {
            case TRUNK -> 'T';
            case BRANCH -> 'B';
            case CANOPY -> 'L';
            default -> 'E';
        };
    }

    private static int radius(TreeDna dna, TreePlan plan) {
        int extent = plan.orderedBlocks().stream()
                .mapToInt(block -> Math.max(
                        Math.abs(block.x() - dna.baseX()),
                        Math.abs(block.z() - dna.baseZ())))
                .max().orElse(0);
        return Math.max(MINIMUM_RADIUS, extent + 3);
    }

    private static void seedIfEmpty(
            TreeConstructionReplayWorld world,
            String key,
            Material material,
            TreeBlockRole role
    ) {
        if (world.cell(key) == null) {
            world.seed(key, material, role, Ownership.NEIGHBOR);
        }
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    enum Environment {
        OPEN,
        CANOPY_OVERLAP,
        PLAYER_EDGE,
        WET_EDGE
    }

    record Snapshot(
            Environment environment,
            int radius,
            int minY,
            int maxY,
            long total,
            long air,
            long source,
            long evolved,
            long neighbor,
            long plannedMissing,
            String volume
    ) {
        String csv() {
            return String.join(",",
                    environment.name(),
                    String.valueOf(radius),
                    minY + ".." + maxY,
                    String.valueOf(total),
                    String.valueOf(air),
                    String.valueOf(source),
                    String.valueOf(evolved),
                    String.valueOf(neighbor),
                    String.valueOf(plannedMissing));
        }
    }
}
