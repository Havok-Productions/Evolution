package org.evolution.features.treeevolution;

import java.util.Collection;
import java.util.Random;

/**
 * ## GROWTH INTENT POLICY
 *
 * <p>Selects and scopes the next constructor intent. This policy does not
 * mutate DNA or the world; the coordinator applies its decision.</p>
 */
final class TreeGrowthIntentPolicy {
    private TreeGrowthIntentPolicy() {
    }

    static TreeGrowthIntent stageBurstIntent(
            TreeCandidate candidate, TreeDna dna) {
        int visibleHeight = TreeSpeciesStageStyle.visibleHeight(dna);
        if (candidate.height() < Math.max(3, visibleHeight - 1)) {
            return dna.hugeArchitecture()
                    && candidate.height() >= Math.max(4, visibleHeight / 3)
                    ? TreeGrowthIntent.WIDTH
                    : TreeGrowthIntent.HEIGHT;
        }
        int remaining = dna.stageGrowthBurst();
        if (dna.maturityStage() == TreeMaturityStage.MEDIUM) {
            if (remaining >= 4) {
                return TreeGrowthIntent.CANOPY;
            }
            return remaining >= 2
                    ? TreeGrowthIntent.BRANCH : TreeGrowthIntent.CANOPY;
        }
        if (remaining >= 10) {
            return dna.hugeArchitecture()
                    ? TreeGrowthIntent.WIDTH : TreeGrowthIntent.HEIGHT;
        }
        if (remaining >= 7) {
            return TreeGrowthIntent.CANOPY;
        }
        if (remaining >= 4) {
            return TreeGrowthIntent.BRANCH;
        }
        if (remaining >= 2) {
            return TreeGrowthIntent.CANOPY;
        }
        return TreeGrowthIntent.DETAIL;
    }

    static TreeGrowthIntent preferredIntent(
            TreeCandidate candidate,
            TreeDna dna,
            TreeEvolutionConfig config,
            long seedlingCooldownUntil
    ) {
        Random random = new Random(
                dna.seed() ^ (dna.age() * 43L) ^ 0x1A17EEL);
        if (dna.damageCount() > 0) {
            return weightedIntent(random,
                    TreeGrowthIntent.REPAIR, 70,
                    TreeGrowthIntent.CANOPY, 30);
        }
        int visibleHeight = TreeSpeciesStageStyle.visibleHeight(dna);
        if (candidate.height() < Math.max(4, visibleHeight - 1)) {
            return weightedIntent(random,
                    TreeGrowthIntent.HEIGHT, 72,
                    TreeGrowthIntent.CANOPY, 18,
                    TreeGrowthIntent.BRANCH, 10);
        }
        if (dna.maturityStage() == TreeMaturityStage.SMALL) {
            return weightedIntent(random,
                    TreeGrowthIntent.CANOPY, 64,
                    TreeGrowthIntent.HEIGHT, 24,
                    TreeGrowthIntent.BRANCH, 12);
        }
        if (dna.hugeArchitecture() && dna.trunkWidth() > 1
                && candidate.height() >= Math.max(
                        5, dna.targetHeight() / 3)
                && random.nextInt(100) < 12) {
            return TreeGrowthIntent.WIDTH;
        }
        if (candidate.height() < Math.max(4, visibleHeight * 2 / 3)) {
            return weightedIntent(random,
                    TreeGrowthIntent.HEIGHT, 50,
                    TreeGrowthIntent.BRANCH, 28,
                    TreeGrowthIntent.CANOPY, 22);
        }
        int seedlingWeight = reproductionWeight(
                dna, config, seedlingCooldownUntil);
        if (dna.maturityStage() == TreeMaturityStage.MEDIUM) {
            return weightedIntent(random,
                    TreeGrowthIntent.CANOPY, 54,
                    TreeGrowthIntent.BRANCH, 26,
                    TreeGrowthIntent.HEIGHT, 14,
                    TreeGrowthIntent.DETAIL, 6,
                    TreeGrowthIntent.SEEDLING, seedlingWeight);
        }
        if (dna.maturityStage() == TreeMaturityStage.MATURE
                || dna.maturityStage() == TreeMaturityStage.ANCIENT) {
            return weightedIntent(random,
                    TreeGrowthIntent.CANOPY, 40,
                    TreeGrowthIntent.DETAIL, 26,
                    TreeGrowthIntent.BRANCH, 20,
                    TreeGrowthIntent.SEEDLING, seedlingWeight,
                    TreeGrowthIntent.WIDTH,
                    dna.hugeArchitecture() ? 7 : 0);
        }
        return TreeGrowthIntent.HEIGHT;
    }

    static int intentSpan(TreeDna dna, TreeGrowthIntent intent) {
        return switch (intent) {
            case HEIGHT -> 5;
            case WIDTH -> 4;
            case BRANCH -> 5;
            case CANOPY -> dna.hasStageBurst() ? 2 : 6;
            case CLEANUP -> 2;
            case DETAIL -> 4;
            case SEEDLING -> 1;
            case REPAIR -> 3;
        };
    }

    static TreeGrowthIntent nextAfterBlocked(TreeGrowthIntent intent) {
        return switch (intent) {
            case HEIGHT, WIDTH -> TreeGrowthIntent.BRANCH;
            case BRANCH, CLEANUP, DETAIL, SEEDLING, REPAIR ->
                    TreeGrowthIntent.CANOPY;
            case CANOPY -> TreeGrowthIntent.DETAIL;
        };
    }

    static boolean matches(TreeDna dna, PlannedTreeBlock block,
            TreeGrowthIntent intent) {
        return switch (intent) {
            case HEIGHT -> block.role() == TreeBlockRole.TRUNK
                    && block.y() <= dna.baseY()
                            + TreeSpeciesStageStyle.visibleHeight(dna) - 1;
            case WIDTH -> block.role() == TreeBlockRole.TRUNK
                    && TreeSpeciesStageStyle.trunkWidthAt(
                            dna, block.y()) > 1
                    && block.y() <= dna.baseY() + Math.max(
                            4, Math.round(dna.targetHeight() * 0.58F));
            case BRANCH -> block.role() == TreeBlockRole.BRANCH;
            case CANOPY -> block.role() == TreeBlockRole.CANOPY;
            case CLEANUP -> false;
            case DETAIL -> block.role() == TreeBlockRole.VINE
                    || block.role() == TreeBlockRole.GROUND_DETAIL
                    || block.role() == TreeBlockRole.FALLEN_LOG;
            case SEEDLING -> block.role() == TreeBlockRole.SAPLING;
            case REPAIR -> block.role() == TreeBlockRole.TRUNK
                    || block.role() == TreeBlockRole.BRANCH
                    || block.role() == TreeBlockRole.CANOPY;
        };
    }

    static double forestDelayMultiplier(
            TreeDna dna, Collection<TreeDna> knownTrees) {
        int nearby = 0;
        for (TreeDna other : knownTrees) {
            if (other == dna || other.worldId() == null
                    || !other.worldId().equals(dna.worldId())) {
                continue;
            }
            int distance = Math.abs(other.baseX() - dna.baseX())
                    + Math.abs(other.baseZ() - dna.baseZ());
            if (distance <= 36 && other.damageCount() <= 1
                    && other.stumpPresent()) {
                nearby++;
            }
            if (nearby >= 6) {
                return 0.82D;
            }
        }
        return nearby >= 3 ? 0.92D : 1.0D;
    }

    private static int reproductionWeight(
            TreeDna dna,
            TreeEvolutionConfig config,
            long seedlingCooldownUntil
    ) {
        TreeReproductionConfig reproduction = config.reproduction();
        if (!reproduction.eligible(
                dna, System.currentTimeMillis(), seedlingCooldownUntil)) {
            return 0;
        }
        double chance = reproduction.chanceFor(dna);
        return chance <= 0.0D
                ? 0
                : Math.max(1, (int) Math.round(chance * 100.0D));
    }

    private static TreeGrowthIntent weightedIntent(
            Random random, Object... pairs) {
        int total = 0;
        for (int index = 1; index < pairs.length; index += 2) {
            total += Math.max(0, (Integer) pairs[index]);
        }
        if (total <= 0) {
            return TreeGrowthIntent.HEIGHT;
        }
        int roll = random.nextInt(total);
        for (int index = 0; index < pairs.length; index += 2) {
            TreeGrowthIntent intent = (TreeGrowthIntent) pairs[index];
            int weight = Math.max(0, (Integer) pairs[index + 1]);
            if (roll < weight) {
                return intent;
            }
            roll -= weight;
        }
        return TreeGrowthIntent.HEIGHT;
    }
}
