package org.evolution.features.treeevolution;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;

/**
 * ## Builds deterministic terrain and runtime conditions around one tree.
 */
final class TreeSimulatedMinecraftFixture {
    private TreeSimulatedMinecraftFixture() {
    }

    static TreeSimulatedMinecraftEnvironment create(
            TreeDna dna,
            TreePlan plan,
            int scenarioIndex
    ) {
        TreeSimulatedMinecraftEnvironment environment =
                new TreeSimulatedMinecraftEnvironment(
                        biome(dna.species()), weather(dna.species()),
                        9, 96);
        environment.addPlayer(
                dna.baseX() + 12,
                dna.baseY() + 2,
                dna.baseZ() - 9,
                128);

        seedSurface(environment, dna, plan, scenarioIndex);
        seedNaturalObstacles(environment, dna, plan);
        seedPlayerAndProtectedEdges(environment, dna, plan);

        // ## These windows exercise the same loaded-region, Folia ownership,
        // and player-distance pauses that stop live evolution without loading
        // or mutating the world from the wrong scheduler.
        environment.scheduleGate(
                2L, 5L,
                TreeSimulatedMinecraftEnvironment.Gate.CHUNK_UNLOADED);
        environment.scheduleGate(
                9L, 12L,
                TreeSimulatedMinecraftEnvironment.Gate.REGION_NOT_OWNED);
        environment.scheduleGate(
                17L, 20L,
                TreeSimulatedMinecraftEnvironment.Gate.NO_NEARBY_PLAYER);
        return environment;
    }

    private static void seedSurface(
            TreeSimulatedMinecraftEnvironment environment,
            TreeDna dna,
            TreePlan plan,
            int scenarioIndex
    ) {
        Bounds bounds = Bounds.of(plan);
        int radius = Math.max(10,
                Math.max(bounds.widthX(), bounds.widthZ()) / 2 + 6);
        for (int x = dna.baseX() - radius;
                x <= dna.baseX() + radius; x++) {
            for (int z = dna.baseZ() - radius;
                    z <= dna.baseZ() + radius; z++) {
                int distance = Math.max(
                        Math.abs(x - dna.baseX()),
                        Math.abs(z - dna.baseZ()));
                int rise = distance > radius - 3
                        && Math.floorMod(x + z + scenarioIndex, 5) == 0
                        ? 1 : 0;
                int surfaceY = dna.baseY() - 1 + rise;
                Material surface = surfaceMaterial(dna.species(), x, z);
                environment.seedTerrain(
                        x, surfaceY, z, surface,
                        true, false, false);
                environment.seedTerrain(
                        x, surfaceY - 1, z, subsurface(surface),
                        true, false, false);
                environment.setLight(x, surfaceY + 1, z, 15);
            }
        }
    }

    private static void seedNaturalObstacles(
            TreeSimulatedMinecraftEnvironment environment,
            TreeDna dna,
            TreePlan plan
    ) {
        Set<String> planned = new HashSet<>(plan.blocksByKey().keySet());
        int edgeX = dna.baseX() + 9;
        int edgeZ = dna.baseZ() + 7;
        for (int offset = -2; offset <= 2; offset++) {
            environment.seedTerrain(
                    edgeX + offset, dna.baseY(), edgeZ,
                    offset == 0 ? Material.WATER : Material.SHORT_GRASS,
                    true, offset != 0, false);
        }

        // ## A pre-existing vine in a future crown cell verifies that natural,
        // replaceable vegetation can be consumed by the actual constructor.
        plan.orderedBlocks().stream()
                .filter(block -> block.role() == TreeBlockRole.CANOPY)
                .filter(block -> chebyshev(
                        block.x(), block.y(), block.z(),
                        dna.baseX(), dna.baseY() + 4, dna.baseZ()) >= 4)
                .max(Comparator.comparingInt(block ->
                        Math.abs(block.x() - dna.baseX())
                                + Math.abs(block.z() - dna.baseZ())))
                .ifPresent(block -> environment.seedTerrain(
                        block.x(), block.y(), block.z(),
                        Material.VINE, true, true, false));

        // ## A small shaded rock pocket is deliberately outside the target.
        // It proves that caves and fluids exist in the simulated world without
        // weakening the tree's own target to force a pass.
        int caveX = dna.baseX() - 8;
        int caveZ = dna.baseZ() + 8;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                String key = key(caveX + dx, dna.baseY() + 3, caveZ + dz);
                if (!planned.contains(key)) {
                    environment.seedTerrain(
                            caveX + dx, dna.baseY() + 3, caveZ + dz,
                            Material.STONE, true, false, false);
                    environment.blockSky(
                            caveX + dx, dna.baseY(), caveZ + dz);
                    environment.setLight(
                            caveX + dx, dna.baseY(), caveZ + dz, 3);
                }
            }
        }
    }

    private static void seedPlayerAndProtectedEdges(
            TreeSimulatedMinecraftEnvironment environment,
            TreeDna dna,
            TreePlan plan
    ) {
        Bounds bounds = Bounds.of(plan);
        int wallX = bounds.maxX() + 4;
        int wallZ = dna.baseZ() - 3;
        for (int y = dna.baseY(); y <= dna.baseY() + 4; y++) {
            environment.seedTerrain(
                    wallX, y, wallZ,
                    Material.OAK_PLANKS, false, false, true);
        }
        environment.protect(new TreeSimulatedMinecraftEnvironment.Box(
                bounds.minX() - 6,
                dna.baseY() - 2,
                bounds.maxZ() + 3,
                bounds.minX() - 3,
                bounds.maxY() + 4,
                bounds.maxZ() + 7));
    }

    private static TreeSimulatedMinecraftEnvironment.BiomeProfile biome(
            TreeSpecies species
    ) {
        return switch (species) {
            case OAK, DARK_OAK ->
                    TreeSimulatedMinecraftEnvironment.BiomeProfile.FOREST;
            case BIRCH -> TreeSimulatedMinecraftEnvironment.BiomeProfile
                    .BIRCH_FOREST;
            case SPRUCE ->
                    TreeSimulatedMinecraftEnvironment.BiomeProfile.TAIGA;
            case JUNGLE ->
                    TreeSimulatedMinecraftEnvironment.BiomeProfile.JUNGLE;
            case ACACIA ->
                    TreeSimulatedMinecraftEnvironment.BiomeProfile.SAVANNA;
            case MANGROVE -> TreeSimulatedMinecraftEnvironment.BiomeProfile
                    .MANGROVE_SWAMP;
            case CHERRY -> TreeSimulatedMinecraftEnvironment.BiomeProfile
                    .CHERRY_GROVE;
        };
    }

    private static TreeSimulatedMinecraftEnvironment.Weather weather(
            TreeSpecies species
    ) {
        return switch (species) {
            case JUNGLE, MANGROVE ->
                    TreeSimulatedMinecraftEnvironment.Weather.THUNDER;
            case SPRUCE, BIRCH, CHERRY ->
                    TreeSimulatedMinecraftEnvironment.Weather.RAIN;
            default -> TreeSimulatedMinecraftEnvironment.Weather.CLEAR;
        };
    }

    private static Material surfaceMaterial(
            TreeSpecies species,
            int x,
            int z
    ) {
        return switch (species) {
            case SPRUCE -> Math.floorMod(x + z, 5) == 0
                    ? Material.PODZOL : Material.GRASS_BLOCK;
            case JUNGLE -> Math.floorMod(x * 3 + z, 7) == 0
                    ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
            case MANGROVE -> Math.floorMod(x + z, 4) == 0
                    ? Material.MUD : Material.GRASS_BLOCK;
            case ACACIA -> Math.floorMod(x - z, 6) == 0
                    ? Material.COARSE_DIRT : Material.GRASS_BLOCK;
            default -> Material.GRASS_BLOCK;
        };
    }

    private static Material subsurface(Material surface) {
        return surface == Material.MUD ? Material.MUD : Material.DIRT;
    }

    private static int chebyshev(
            int x1, int y1, int z1,
            int x2, int y2, int z2
    ) {
        return Math.max(
                Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2)),
                Math.abs(z1 - z2));
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private record Bounds(
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ
    ) {
        static Bounds of(TreePlan plan) {
            return new Bounds(
                    plan.orderedBlocks().stream()
                            .mapToInt(PlannedTreeBlock::x).min().orElse(0),
                    plan.orderedBlocks().stream()
                            .mapToInt(PlannedTreeBlock::x).max().orElse(0),
                    plan.orderedBlocks().stream()
                            .mapToInt(PlannedTreeBlock::y).min().orElse(0),
                    plan.orderedBlocks().stream()
                            .mapToInt(PlannedTreeBlock::y).max().orElse(0),
                    plan.orderedBlocks().stream()
                            .mapToInt(PlannedTreeBlock::z).min().orElse(0),
                    plan.orderedBlocks().stream()
                            .mapToInt(PlannedTreeBlock::z).max().orElse(0));
        }

        int widthX() {
            return maxX - minX + 1;
        }

        int widthZ() {
            return maxZ - minZ + 1;
        }
    }
}
