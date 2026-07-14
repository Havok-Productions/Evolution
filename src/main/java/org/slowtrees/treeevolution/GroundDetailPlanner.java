package org.slowtrees.treeevolution;

import java.util.List;
import java.util.Random;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.Biome;

final class GroundDetailPlanner {
    void plan(TreePlan plan, TreeDna dna, Biome biome) {
        Random random = new Random(dna.seed() ^ 0xD37A11L);
        int radius = switch (dna.maturityStage()) {
            case SMALL -> 2;
            case MEDIUM -> 3;
            case MATURE -> 4;
            case ANCIENT -> 5;
        };
        if (dna.rarity() == TreeRarity.LANDMARK || dna.hugeArchitecture()) {
            radius += 2;
        }
        int richness = richnessScore(dna);
        int attempts = 3 + radius + richness;
        planShadePocket(plan, dna, biome, random, radius, richness);
        planLivingEdge(plan, dna, biome, random, radius, richness);
        planPatchFamilies(plan, dna, biome, random, radius, richness);
        planRareFeaturePatches(plan, dna, biome, random, radius, richness);
        for (int attempt = 0; attempt < attempts; attempt++) {
            double detailChance = dna.groundDetailChance()
                    * (dna.rarity() == TreeRarity.LANDMARK ? 1.25D : 0.82D)
                    * (1.0D + (richness * 0.06D));
            if (random.nextDouble() > detailChance) {
                continue;
            }
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if ((dx * dx) + (dz * dz) > radius * radius) {
                continue;
            }
            plan.add(new PlannedTreeBlock(
                    dna.baseX() + dx,
                    dna.baseY(),
                    dna.baseZ() + dz,
                    detailForZone(dna, biome, random, zoneFor(dx, dz, radius)),
                    TreeBlockRole.GROUND_DETAIL,
                    Axis.Y,
                    null
            ));
        }
        if ((dna.maturityStage() == TreeMaturityStage.ANCIENT || dna.rarity() == TreeRarity.LANDMARK)
                && random.nextDouble() < 0.45D) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            Axis axis = Math.abs(dx) > Math.abs(dz) ? Axis.X : Axis.Z;
            plan.add(new PlannedTreeBlock(
                    dna.baseX() + dx,
                    dna.baseY(),
                    dna.baseZ() + dz,
                    dna.species().logMaterial(),
                    TreeBlockRole.FALLEN_LOG,
                    axis,
                    null
            ));
        }
    }

    private void planShadePocket(TreePlan plan, TreeDna dna, Biome biome, Random random, int radius, int richness) {
        if (dna.maturityStage() == TreeMaturityStage.SMALL) {
            return;
        }
        int pocketRadius = Math.max(2, Math.min(radius, 2 + richness / 2));
        int attempts = 3 + richness;
        for (int attempt = 0; attempt < attempts; attempt++) {
            int dx = random.nextInt(pocketRadius * 2 + 1) - pocketRadius;
            int dz = random.nextInt(pocketRadius * 2 + 1) - pocketRadius;
            if ((dx * dx) + (dz * dz) > pocketRadius * pocketRadius || random.nextDouble() > 0.58D) {
                continue;
            }
            plan.add(new PlannedTreeBlock(
                    dna.baseX() + dx,
                    dna.baseY(),
                    dna.baseZ() + dz,
                    shadeDetailFor(biome, random),
                    TreeBlockRole.GROUND_DETAIL,
                    Axis.Y,
                    null
            ));
        }
    }

    private void planLivingEdge(TreePlan plan, TreeDna dna, Biome biome, Random random, int radius, int richness) {
        if (dna.maturityStage() == TreeMaturityStage.SMALL || richness < 2) {
            return;
        }
        int edgeRadius = radius + 1 + random.nextInt(2);
        int attempts = 1 + Math.max(1, richness / 2);
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int dx = (int) Math.round(Math.cos(angle) * edgeRadius) + random.nextInt(3) - 1;
            int dz = (int) Math.round(Math.sin(angle) * edgeRadius) + random.nextInt(3) - 1;
            Material material = random.nextDouble() < edgeFlowerChance(dna, biome) ? flowerForBiome(biome, random) : edgeDetailFor(biome, random);
            plan.add(new PlannedTreeBlock(
                    dna.baseX() + dx,
                    dna.baseY(),
                    dna.baseZ() + dz,
                    material,
                    TreeBlockRole.GROUND_DETAIL,
                    Axis.Y,
                    null
            ));
        }
    }

    private void planPatchFamilies(TreePlan plan, TreeDna dna, Biome biome, Random random, int radius, int richness) {
        if (richness < 2) {
            return;
        }
        int patchCount = Math.min(4, 1 + richness / 3 + (dna.rarity() == TreeRarity.LANDMARK ? 1 : 0));
        for (int patch = 0; patch < patchCount; patch++) {
            Zone zone = patch == 0 ? Zone.SHADE : random.nextBoolean() ? Zone.UNDERSTORY : Zone.EDGE;
            int zoneRadius = switch (zone) {
                case SHADE -> Math.max(2, radius / 2);
                case UNDERSTORY -> Math.max(3, radius - 1);
                case EDGE -> radius + 1;
            };
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int centerDistance = switch (zone) {
                case SHADE -> 1 + random.nextInt(Math.max(1, zoneRadius));
                case UNDERSTORY -> Math.max(2, zoneRadius - random.nextInt(3));
                case EDGE -> zoneRadius;
            };
            int centerX = (int) Math.round(Math.cos(angle) * centerDistance);
            int centerZ = (int) Math.round(Math.sin(angle) * centerDistance);
            List<Material> family = patchFamilyFor(dna, biome, zone, random);
            int size = 2 + random.nextInt(Math.min(3, 2 + richness / 3));
            for (int index = 0; index < size; index++) {
                int dx = centerX + random.nextInt(5) - 2;
                int dz = centerZ + random.nextInt(5) - 2;
                if (zone == Zone.SHADE && (dx * dx) + (dz * dz) > zoneRadius * zoneRadius) {
                    continue;
                }
                if (zone == Zone.EDGE && (dx * dx) + (dz * dz) < Math.max(4, (radius - 2) * (radius - 2))) {
                    continue;
                }
                Material material = family.get(Math.floorMod(index + random.nextInt(family.size()), family.size()));
                plan.add(new PlannedTreeBlock(
                        dna.baseX() + dx,
                        dna.baseY(),
                        dna.baseZ() + dz,
                        material,
                        TreeBlockRole.GROUND_DETAIL,
                        Axis.Y,
                        null
                ));
            }
        }
    }

    private void planRareFeaturePatches(TreePlan plan, TreeDna dna, Biome biome, Random random, int radius, int richness) {
        if (dna.maturityStage() != TreeMaturityStage.MATURE && dna.maturityStage() != TreeMaturityStage.ANCIENT) {
            return;
        }
        int chance = 8 + richness * 3 + (dna.rarity() == TreeRarity.LANDMARK ? 8 : 0);
        if (random.nextInt(100) >= chance) {
            return;
        }

        Zone zone = random.nextInt(100) < 72 ? Zone.EDGE : Zone.SHADE;
        int featureRadius = zone == Zone.EDGE ? radius + 2 : Math.max(2, radius / 2);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        int centerDistance = zone == Zone.EDGE ? featureRadius : 1 + random.nextInt(Math.max(1, featureRadius));
        int centerX = (int) Math.round(Math.cos(angle) * centerDistance);
        int centerZ = (int) Math.round(Math.sin(angle) * centerDistance);
        Material feature = rareFeatureFor(dna, biome, zone, random);
        int companions = switch (feature) {
            case PUMPKIN, MELON -> 3;
            case SWEET_BERRY_BUSH, SUGAR_CANE -> 2;
            default -> 2;
        };

        plan.add(new PlannedTreeBlock(
                dna.baseX() + centerX,
                dna.baseY(),
                dna.baseZ() + centerZ,
                feature,
                TreeBlockRole.GROUND_DETAIL,
                Axis.Y,
                null
        ));
        List<Material> companionFamily = rareCompanionFamily(feature, biome, random);
        for (int index = 0; index < companions; index++) {
            int dx = centerX + random.nextInt(5) - 2;
            int dz = centerZ + random.nextInt(5) - 2;
            Material material = companionFamily.get(random.nextInt(companionFamily.size()));
            plan.add(new PlannedTreeBlock(
                    dna.baseX() + dx,
                    dna.baseY(),
                    dna.baseZ() + dz,
                    material,
                    TreeBlockRole.GROUND_DETAIL,
                    Axis.Y,
                    null
            ));
        }
    }

    private int richnessScore(TreeDna dna) {
        int richness = switch (dna.maturityStage()) {
            case SMALL -> 0;
            case MEDIUM -> 1;
            case MATURE -> 3;
            case ANCIENT -> 5;
        };
        if (dna.rarity() == TreeRarity.RARE) {
            richness += 1;
        } else if (dna.rarity() == TreeRarity.LANDMARK) {
            richness += 3;
        }
        if (dna.hugeArchitecture()) {
            richness += 2;
        }
        if (dna.damageCount() > 2) {
            richness -= 2;
        }
        return Math.max(0, Math.min(8, richness));
    }

    private Zone zoneFor(int dx, int dz, int radius) {
        int distanceSquared = (dx * dx) + (dz * dz);
        if (distanceSquared <= 6) {
            return Zone.SHADE;
        }
        if (distanceSquared >= Math.max(4, (radius - 1) * (radius - 1))) {
            return Zone.EDGE;
        }
        return Zone.UNDERSTORY;
    }

    private Material detailForZone(TreeDna dna, Biome biome, Random random, Zone zone) {
        if (random.nextInt(100) < 24) {
            return speciesDetailFor(dna, biome, random, zone);
        }
        return switch (zone) {
            case SHADE -> shadeDetailFor(biome, random);
            case EDGE -> random.nextInt(100) < 12 ? flowerForBiome(biome, random) : edgeDetailFor(biome, random);
            case UNDERSTORY -> understoryDetailFor(biome, random);
        };
    }

    private List<Material> patchFamilyFor(TreeDna dna, Biome biome, Zone zone, Random random) {
        if (zone == Zone.EDGE) {
            return edgePatchFamilyFor(dna, biome, random);
        }
        return switch (dna.species()) {
            case BIRCH -> zone == Zone.SHADE
                    ? List.of(Material.MOSS_CARPET, Material.SHORT_GRASS, Material.FERN)
                    : List.of(Material.SHORT_GRASS, Material.FERN, Material.OXEYE_DAISY, Material.DANDELION);
            case SPRUCE -> List.of(Material.FERN, Material.LARGE_FERN, Material.BROWN_MUSHROOM, Material.MOSS_CARPET, Material.LEAF_LITTER);
            case JUNGLE -> List.of(Material.FERN, Material.SHORT_GRASS, Material.BROWN_MUSHROOM, Material.MOSS_CARPET);
            case ACACIA -> List.of(Material.SHORT_GRASS, Material.FERN, Material.POPPY, Material.DANDELION);
            case DARK_OAK -> List.of(Material.MOSS_CARPET, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.FERN, Material.LEAF_LITTER);
            case MANGROVE -> List.of(Material.BROWN_MUSHROOM, Material.BLUE_ORCHID, Material.FERN, Material.MOSS_CARPET);
            case CHERRY -> List.of(Material.PINK_PETALS, Material.SHORT_GRASS, Material.FERN, Material.MOSS_CARPET);
            case OAK -> List.of(Material.MOSS_CARPET, Material.FERN, Material.BROWN_MUSHROOM, Material.SHORT_GRASS, Material.OXEYE_DAISY);
        };
    }

    private List<Material> edgePatchFamilyFor(TreeDna dna, Biome biome, Random random) {
        String key = biome.getKey().getKey();
        if (key.contains("jungle")) {
            return List.of(Material.SHORT_GRASS, Material.FERN, Material.BROWN_MUSHROOM, rareMaybe(Material.MELON, Material.SHORT_GRASS, random));
        }
        if (key.contains("taiga") || key.contains("old_growth")) {
            return List.of(Material.FERN, Material.MOSS_CARPET, Material.LEAF_LITTER, rareMaybe(Material.SWEET_BERRY_BUSH, Material.FERN, random));
        }
        if (key.contains("swamp")) {
            return List.of(Material.BLUE_ORCHID, Material.FERN, Material.BROWN_MUSHROOM, rareMaybe(Material.SUGAR_CANE, Material.LEAF_LITTER, random));
        }
        if (key.contains("savanna") || key.contains("desert") || key.contains("badlands")) {
            return List.of(Material.SHORT_GRASS, Material.FERN, Material.DEAD_BUSH, Material.POPPY);
        }
        if (dna.species() == TreeSpecies.CHERRY) {
            return List.of(Material.PINK_PETALS, Material.SHORT_GRASS, Material.DANDELION, flowerForBiome(biome, random));
        }
        return List.of(edgeDetailFor(biome, random), Material.SHORT_GRASS, Material.FERN, flowerForBiome(biome, random), rareMaybe(Material.PUMPKIN, Material.SHORT_GRASS, random));
    }

    private Material rareMaybe(Material rare, Material fallback, Random random) {
        return random.nextInt(100) < 18 ? rare : fallback;
    }

    private Material rareFeatureFor(TreeDna dna, Biome biome, Zone zone, Random random) {
        String key = biome.getKey().getKey();
        if (zone == Zone.SHADE) {
            return randomFrom(List.of(Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.MOSS_CARPET), random);
        }
        if (key.contains("jungle")) {
            return Material.MELON;
        }
        if (key.contains("taiga") || key.contains("old_growth")) {
            return Material.SWEET_BERRY_BUSH;
        }
        if (key.contains("swamp") || key.contains("river")) {
            return random.nextBoolean() ? Material.SUGAR_CANE : Material.BLUE_ORCHID;
        }
        if (key.contains("savanna") || key.contains("desert") || key.contains("badlands")) {
            return Material.DEAD_BUSH;
        }
        if (dna.species() == TreeSpecies.JUNGLE) {
            return Material.MELON;
        }
        return Material.PUMPKIN;
    }

    private List<Material> rareCompanionFamily(Material feature, Biome biome, Random random) {
        return switch (feature) {
            case PUMPKIN -> List.of(Material.SHORT_GRASS, Material.DANDELION, Material.FERN);
            case MELON -> List.of(Material.SHORT_GRASS, Material.FERN, Material.BROWN_MUSHROOM);
            case SWEET_BERRY_BUSH -> List.of(Material.FERN, Material.MOSS_CARPET, Material.LEAF_LITTER);
            case SUGAR_CANE -> List.of(Material.FERN, Material.SHORT_GRASS, Material.BLUE_ORCHID);
            case DEAD_BUSH -> List.of(Material.SHORT_GRASS, Material.FERN, Material.POPPY);
            default -> List.of(shadeDetailFor(biome, random), Material.FERN);
        };
    }

    private Material speciesDetailFor(TreeDna dna, Biome biome, Random random, Zone zone) {
        List<Material> family = patchFamilyFor(dna, biome, zone, random);
        return randomFrom(family, random);
    }

    private Material shadeDetailFor(Biome biome, Random random) {
        String key = biome.getKey().getKey();
        if (key.contains("swamp")) {
            return randomFrom(List.of(Material.BROWN_MUSHROOM, Material.BLUE_ORCHID, Material.MOSS_CARPET, Material.FERN), random);
        }
        if (key.contains("jungle")) {
            return randomFrom(List.of(Material.FERN, Material.BROWN_MUSHROOM, Material.MOSS_CARPET, Material.SHORT_GRASS), random);
        }
        if (key.contains("taiga") || key.contains("old_growth")) {
            return randomFrom(List.of(Material.FERN, Material.LARGE_FERN, Material.BROWN_MUSHROOM, Material.MOSS_CARPET, Material.LEAF_LITTER), random);
        }
        return randomFrom(List.of(Material.MOSS_CARPET, Material.FERN, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.LEAF_LITTER), random);
    }

    private Material understoryDetailFor(Biome biome, Random random) {
        String key = biome.getKey().getKey();
        if (key.contains("taiga") || key.contains("old_growth")) {
            return random.nextBoolean() ? Material.FERN : Material.LEAF_LITTER;
        }
        if (key.contains("jungle")) {
            return random.nextBoolean() ? Material.FERN : Material.SHORT_GRASS;
        }
        if (key.contains("cherry")) {
            return Material.PINK_PETALS;
        }
        if (key.contains("swamp")) {
            return randomFrom(List.of(Material.BLUE_ORCHID, Material.BROWN_MUSHROOM, Material.LEAF_LITTER), random);
        }
        if (key.contains("forest")) {
            return randomFrom(List.of(Material.MOSS_CARPET, Material.FERN, Material.BROWN_MUSHROOM, Material.SHORT_GRASS, Material.LEAF_LITTER), random);
        }
        if (key.contains("meadow") || key.contains("plains")) {
            return randomFrom(List.of(Material.SHORT_GRASS, Material.FERN, Material.DANDELION, Material.OXEYE_DAISY), random);
        }
        return randomFrom(List.of(Material.SHORT_GRASS, Material.FERN, Material.MOSS_CARPET, Material.DANDELION), random);
    }

    private Material edgeDetailFor(Biome biome, Random random) {
        String key = biome.getKey().getKey();
        if (key.contains("taiga") || key.contains("old_growth") || key.contains("forest")) {
            return randomFrom(List.of(Material.SHORT_GRASS, Material.FERN, Material.MOSS_CARPET, Material.LEAF_LITTER), random);
        }
        if (key.contains("cherry")) {
            return random.nextBoolean() ? Material.PINK_PETALS : Material.SHORT_GRASS;
        }
        return randomFrom(List.of(Material.SHORT_GRASS, Material.FERN, Material.DANDELION), random);
    }

    private double edgeFlowerChance(TreeDna dna, Biome biome) {
        String key = biome.getKey().getKey();
        double chance = key.contains("meadow") || key.contains("plains") || key.contains("cherry") ? 0.22D : 0.08D;
        if (dna.maturityStage() == TreeMaturityStage.ANCIENT || dna.rarity() == TreeRarity.LANDMARK) {
            chance += 0.05D;
        }
        return Math.min(0.32D, chance);
    }

    private Material flowerForBiome(Biome biome, Random random) {
        String key = biome.getKey().getKey();
        if (key.contains("swamp")) {
            return Material.BLUE_ORCHID;
        }
        if (key.contains("cherry")) {
            return Material.PINK_PETALS;
        }
        if (key.contains("forest")) {
            return randomFrom(List.of(Material.OXEYE_DAISY, Material.LILY_OF_THE_VALLEY, Material.POPPY, Material.DANDELION), random);
        }
        if (key.contains("meadow") || key.contains("plains")) {
            return randomFrom(List.of(Material.DANDELION, Material.POPPY, Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.CORNFLOWER), random);
        }
        return randomFrom(List.of(Material.DANDELION, Material.POPPY, Material.OXEYE_DAISY), random);
    }

    private static Material randomFrom(List<Material> materials, Random random) {
        return materials.get(random.nextInt(materials.size()));
    }

    private enum Zone {
        SHADE,
        UNDERSTORY,
        EDGE
    }
}
