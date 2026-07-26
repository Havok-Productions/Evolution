package org.evolution.features.treeevolution;

import java.util.Random;
import org.bukkit.World;

/**
 * ## TREE DNA FACTORY
 *
 * <p>Owns seeded profile interpretation and initial immutable shape traits.
 * Runtime growth state remains owned by {@link TreeDna}.</p>
 */
final class TreeDnaFactory {
    private TreeDnaFactory() {
    }

    static TreeDna create(World world, TreeCandidate candidate,
            TreeGrowthProfile profile, TreeProfileSample sample,
            String parentKey, int generation) {
        long seed = new Random().nextLong();
        Random random = new Random(seed ^ candidate.baseKey().hashCode());
        String text = sampleText(candidate.species(), sample);
        TreeRarity rarity = rarityFor(text, random);
        TreePersonality personality = personalityFor(
                candidate.species(), text, rarity, random);
        int targetHeight = randomRange(
                random, profile.minTargetHeight(), profile.maxTargetHeight());
        targetHeight = scaledHeight(
                targetHeight, candidate.species(), personality,
                rarity, text, random);
        targetHeight = Math.max(
                targetHeight, candidate.height() + random.nextInt(4));
        ShapeTraits shape = shapeTraits(
                candidate.species(), profile, sample, personality,
                rarity, targetHeight, random);
        return new TreeDna(
                world.getUID(),
                candidate.baseX(),
                candidate.baseY(),
                candidate.baseZ(),
                candidate.species(),
                seed,
                personality,
                rarity,
                targetHeight,
                TreeDnaShapeRules.normalizeBranchCount(
                        candidate.species(), personality, targetHeight,
                        randomRange(random, profile.minBranches(),
                                profile.maxBranches())),
                profile.minBranchLength(),
                profile.maxBranchLength(),
                random.nextInt(4),
                profile.canopyRadius(),
                shape.canopyRadiusX(),
                shape.canopyRadiusY(),
                shape.canopyRadiusZ(),
                profile.canopyDensity(),
                shape.branchStartRatio(),
                shape.branchRiseChance(),
                profile.rootChance(),
                profile.vineChance(),
                profile.groundDetailChance(),
                shape.trunkRadius(),
                shape.canopyLayerCount(),
                shape.canopyLayerSpread(),
                shape.leanX(),
                shape.leanZ(),
                shape.leanStartRatio(),
                sample == null ? "config-default" : sample.id(),
                sample == null ? "config.yml" : sample.sourceFile(),
                parentKey,
                generation,
                TreeDna.CURRENT_SHAPE_REVISION,
                TreeGrowthIntent.HEIGHT,
                0, 0, 0, 0, 0, 0, 0,
                stageFor(candidate.height(), targetHeight),
                0L, 0L, 0, true);
    }

    static int legacyTrunkWidth(TreeSpecies species,
            TreePersonality personality, TreeRarity rarity,
            int targetHeight, long seed, int legacyWidth) {
        if (legacyWidth >= 4) {
            return legacyWidth;
        }
        return Math.max(legacyWidth, trunkWidthFor(
                species, personality, rarity, targetHeight, "",
                new Random(seed ^ 0x71A771EEL)));
    }

    static int legacyCanopyLayerCount(TreeSpecies species,
            TreePersonality personality, TreeRarity rarity,
            int targetHeight, long seed) {
        return canopyLayersFor(
                species, personality, rarity, targetHeight, "",
                new Random(seed ^ 0x1A7E5EEDL));
    }

    static int legacyCanopyLayerSpread(
            int layerCount, int canopyRadius, long seed) {
        if (layerCount <= 0) {
            return 0;
        }
        Random random = new Random(seed ^ 0x5F1EADL);
        return Math.max(2,
                Math.min(10, canopyRadius + 1 + random.nextInt(3)));
    }

    private static TreeMaturityStage stageFor(
            int currentHeight, int targetHeight) {
        if (currentHeight >= targetHeight) {
            return TreeMaturityStage.MATURE;
        }
        if (currentHeight >= Math.max(4, targetHeight * 2 / 3)) {
            return TreeMaturityStage.MEDIUM;
        }
        return TreeMaturityStage.SMALL;
    }

    private static int randomRange(Random random, int minimum, int maximum) {
        int low = Math.min(minimum, maximum);
        int high = Math.max(minimum, maximum);
        return low + random.nextInt(high - low + 1);
    }

    private static String sampleText(
            TreeSpecies species, TreeProfileSample sample) {
        return ((sample == null
                ? ""
                : sample.sourceFile() + " " + sample.trunkPlacer()
                        + " " + sample.foliagePlacer())
                + " " + species.id()).toLowerCase(java.util.Locale.ROOT);
    }

    private static TreeRarity rarityFor(String text, Random random) {
        if (text.contains("mega") || text.contains("huge")
                || text.contains("giant") || text.contains("ancient")) {
            return random.nextDouble() < 0.35D
                    ? TreeRarity.LANDMARK : TreeRarity.RARE;
        }
        double roll = random.nextDouble();
        if (roll < 0.025D) {
            return TreeRarity.LANDMARK;
        }
        if (roll < 0.11D) {
            return TreeRarity.RARE;
        }
        if (roll < 0.34D) {
            return TreeRarity.UNCOMMON;
        }
        return TreeRarity.COMMON;
    }

    private static TreePersonality personalityFor(TreeSpecies species,
            String text, TreeRarity rarity, Random random) {
        if (rarity == TreeRarity.LANDMARK) {
            return random.nextBoolean()
                    ? TreePersonality.ANCIENT_LANDMARK
                    : TreePersonality.HOLLOW;
        }
        if (text.contains("fancy") || text.contains("large")
                || text.contains("wide")) {
            return TreePersonality.WIDE;
        }
        if (text.contains("tall") || text.contains("straight")
                || text.contains("mega")) {
            return TreePersonality.TALL;
        }
        if (text.contains("bending") || text.contains("forking")) {
            return TreePersonality.FORKED;
        }
        if (text.contains("bush") || text.contains("young")
                || text.contains("stump")) {
            return species == TreeSpecies.BIRCH
                    ? TreePersonality.TALL : TreePersonality.BALANCED;
        }
        if (species == TreeSpecies.SPRUCE) {
            return random.nextBoolean()
                    ? TreePersonality.SPIRE : TreePersonality.TALL;
        }
        if (species == TreeSpecies.ACACIA) {
            return random.nextBoolean()
                    ? TreePersonality.UMBRELLA : TreePersonality.CROOKED;
        }
        if (species == TreeSpecies.CHERRY) {
            return random.nextBoolean()
                    ? TreePersonality.LAYERED : TreePersonality.UMBRELLA;
        }
        if (species == TreeSpecies.DARK_OAK) {
            return random.nextBoolean()
                    ? TreePersonality.DENSE : TreePersonality.WIDE;
        }
        if (species == TreeSpecies.BIRCH) {
            return random.nextBoolean()
                    ? TreePersonality.TALL : TreePersonality.BALANCED;
        }
        return switch (random.nextInt(8)) {
            case 0 -> TreePersonality.WIDE;
            case 1 -> TreePersonality.CROOKED;
            case 2 -> TreePersonality.DENSE;
            case 3 -> TreePersonality.FORKED;
            case 4 -> TreePersonality.WINDSWEPT;
            default -> TreePersonality.BALANCED;
        };
    }

    private static int scaledHeight(int baseHeight, TreeSpecies species,
            TreePersonality personality, TreeRarity rarity,
            String text, Random random) {
        double factor = switch (rarity) {
            case COMMON -> 1.0D;
            case UNCOMMON -> 1.16D;
            case RARE -> 1.36D;
            case LANDMARK -> 1.65D;
        };
        if (personality == TreePersonality.TALL
                || personality == TreePersonality.SPIRE
                || personality == TreePersonality.ANCIENT_LANDMARK) {
            factor += 0.25D;
        }
        if (personality == TreePersonality.WIDE
                || personality == TreePersonality.UMBRELLA
                || personality == TreePersonality.SPARSE) {
            factor -= 0.08D;
        }
        if (species == TreeSpecies.JUNGLE || species == TreeSpecies.SPRUCE) {
            factor += rarity == TreeRarity.LANDMARK ? 0.20D : 0.08D;
        }
        if (text.contains("mega") || text.contains("giant")) {
            factor += 0.25D;
        }
        int variance = rarity == TreeRarity.LANDMARK ? 8 : 4;
        int scaled = (int) Math.round(baseHeight * factor)
                + random.nextInt(variance);
        return Math.max(
                TreeShapeProfile.targetHeightFloor(
                        species, personality, rarity),
                Math.min(96, scaled));
    }

    private static ShapeTraits shapeTraits(TreeSpecies species,
            TreeGrowthProfile profile, TreeProfileSample sample,
            TreePersonality personality, TreeRarity rarity,
            int targetHeight, Random random) {
        String text = sampleText(species, sample);
        int baseRadius = Math.max(1, profile.canopyRadius());
        int radiusX = Math.max(1, baseRadius + random.nextInt(3) - 1);
        int radiusZ = Math.max(1, baseRadius + random.nextInt(3) - 1);
        int radiusY = Math.max(1, baseRadius - 1 + random.nextInt(2));
        double branchStart = 0.42D + (random.nextDouble() * 0.28D);
        double branchRise = 0.18D + (random.nextDouble() * 0.42D);

        if (text.contains("tall") || text.contains("straight")
                || text.contains("spruce") || text.contains("pine")) {
            radiusX = Math.max(1, radiusX - 1);
            radiusZ = Math.max(1, radiusZ - 1);
            radiusY = Math.max(radiusY, baseRadius + 1);
            branchStart += 0.12D;
            branchRise += 0.12D;
        }
        if (text.contains("fancy") || text.contains("large")
                || text.contains("wide") || text.contains("dark_oak")) {
            radiusX += 1 + random.nextInt(2);
            radiusZ += random.nextInt(2);
            radiusY = Math.max(1, radiusY - 1);
            branchStart -= 0.08D;
        }
        if (text.contains("bush") || text.contains("young")
                || text.contains("stump")) {
            radiusX += random.nextInt(2);
            radiusZ += random.nextInt(2);
            radiusY = Math.max(1, radiusY - 1);
            branchStart -= 0.14D;
            branchRise -= 0.08D;
        }
        if (text.contains("bending") || text.contains("forking")
                || text.contains("acacia")) {
            branchStart -= 0.05D;
            branchRise += 0.18D;
            radiusX += random.nextInt(2);
            radiusZ += random.nextInt(2);
        }
        if (text.contains("cherry")) {
            radiusX++;
            radiusZ++;
            radiusY = Math.max(1, radiusY - 1);
            branchStart -= 0.06D;
        }

        switch (personality) {
            case TALL, SPIRE -> {
                radiusX = Math.max(1, radiusX - 1);
                radiusZ = Math.max(1, radiusZ - 1);
                radiusY++;
                branchStart += 0.10D;
            }
            case WIDE, UMBRELLA, LAYERED -> {
                radiusX += 2;
                radiusZ += 1 + random.nextInt(2);
                radiusY = Math.max(1, radiusY - 1);
                branchStart -= 0.10D;
            }
            case DENSE -> {
                radiusX++;
                radiusZ++;
            }
            case SPARSE -> {
                radiusX = Math.max(1, radiusX - 1);
                radiusZ = Math.max(1, radiusZ - 1);
                branchRise -= 0.08D;
            }
            case CROOKED, WINDSWEPT -> branchRise += 0.14D;
            case HOLLOW, ANCIENT_LANDMARK -> {
                radiusX++;
                radiusZ++;
                radiusY++;
                branchStart -= 0.06D;
            }
            default -> {
            }
        }
        if (rarity == TreeRarity.RARE || rarity == TreeRarity.LANDMARK) {
            radiusX++;
            radiusZ++;
        }

        int trunkRadius = trunkWidthFor(
                species, personality, rarity, targetHeight, text, random);
        radiusX = Math.max(radiusX, TreeShapeProfile.canopyRadiusFloor(
                species, personality, rarity, targetHeight, true));
        radiusZ = Math.max(radiusZ, TreeShapeProfile.canopyRadiusFloor(
                species, personality, rarity, targetHeight, false));
        radiusY = Math.max(radiusY,
                TreeShapeProfile.canopyVerticalRadiusFloor(
                        species, Math.max(radiusX, radiusZ)));
        int layers = canopyLayersFor(
                species, personality, rarity, targetHeight, text, random);
        int spread = layers == 0 ? 0 : Math.max(
                Math.max(radiusX, radiusZ),
                Math.min(12, Math.max(radiusX, radiusZ)
                        + random.nextInt(3)));
        boolean leaning = personality == TreePersonality.CROOKED
                || personality == TreePersonality.WINDSWEPT
                || species == TreeSpecies.ACACIA;
        int leanX = leaning ? random.nextInt(3) - 1 : 0;
        int leanZ = leaning ? random.nextInt(3) - 1 : 0;
        if (leanX == 0 && leanZ == 0
                && (personality == TreePersonality.CROOKED
                        || personality == TreePersonality.WINDSWEPT)) {
            leanX = random.nextBoolean() ? 1 : -1;
        }
        double leanStart = 0.45D + random.nextDouble() * 0.25D;
        return new ShapeTraits(
                radiusX, radiusY, radiusZ,
                TreeDnaShapeRules.clamp(branchStart, 0.30D, 0.78D),
                TreeDnaShapeRules.clamp(branchRise, 0.05D, 0.75D),
                trunkRadius, layers, spread, leanX, leanZ, leanStart);
    }

    private static int trunkWidthFor(TreeSpecies species,
            TreePersonality personality, TreeRarity rarity,
            int targetHeight, String text, Random random) {
        int floor = TreeShapeProfile.trunkWidthFloor(
                species, personality, rarity, targetHeight);
        boolean naturalGiant = species == TreeSpecies.JUNGLE
                || species == TreeSpecies.SPRUCE
                || species == TreeSpecies.DARK_OAK
                || species == TreeSpecies.MANGROVE;
        boolean giantSignal = text.contains("mega")
                || text.contains("giant") || text.contains("huge")
                || text.contains("ancient");
        if (rarity == TreeRarity.LANDMARK && targetHeight >= 24
                && (naturalGiant
                        || personality == TreePersonality.ANCIENT_LANDMARK
                        || giantSignal)) {
            return Math.max(floor, 4 + random.nextInt(3));
        }
        if ((rarity == TreeRarity.LANDMARK
                || personality == TreePersonality.HOLLOW)
                && targetHeight >= 20) {
            return Math.max(floor, 3 + random.nextInt(2));
        }
        if (rarity == TreeRarity.RARE && targetHeight >= 18
                && (naturalGiant || personality == TreePersonality.WIDE
                        || giantSignal)) {
            return Math.max(floor, 3 + random.nextInt(2));
        }
        if (rarity == TreeRarity.LANDMARK && targetHeight >= 16) {
            return Math.max(floor, 3);
        }
        int baseline = rarity == TreeRarity.RARE
                || personality == TreePersonality.HOLLOW
                || personality == TreePersonality.WIDE ? 2 : 1;
        return Math.max(floor, baseline);
    }

    private static int canopyLayersFor(TreeSpecies species,
            TreePersonality personality, TreeRarity rarity,
            int targetHeight, String text, Random random) {
        boolean layeredSignal = text.contains("layer")
                || text.contains("mega") || text.contains("giant")
                || text.contains("fancy") || text.contains("large");
        int floor = TreeShapeProfile.canopyLayerFloor(
                species, personality, rarity, targetHeight);
        if (rarity == TreeRarity.LANDMARK && targetHeight >= 24) {
            return Math.max(floor, 2 + random.nextInt(
                    personality == TreePersonality.ANCIENT_LANDMARK ? 4 : 3));
        }
        if ((rarity == TreeRarity.RARE || layeredSignal)
                && targetHeight >= 18) {
            return Math.max(floor, 2 + random.nextInt(3));
        }
        if (personality == TreePersonality.LAYERED
                || species == TreeSpecies.CHERRY
                || species == TreeSpecies.SPRUCE) {
            return Math.max(floor, 1 + random.nextInt(3));
        }
        return floor;
    }

    private record ShapeTraits(
            int canopyRadiusX,
            int canopyRadiusY,
            int canopyRadiusZ,
            double branchStartRatio,
            double branchRiseChance,
            int trunkRadius,
            int canopyLayerCount,
            int canopyLayerSpread,
            int leanX,
            int leanZ,
            double leanStartRatio
    ) {
    }
}
