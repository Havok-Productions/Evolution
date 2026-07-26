package org.evolution.features.treeevolution;

import java.util.Collection;

import java.util.Set;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

final class TreeDna {
    // ## Revision 6 recaptures revision-5 live crowns once so completed trees
    // can retire residual source foliage under the ownership-aware constructor.
    static final int CURRENT_SHAPE_REVISION = 6;
    private final UUID worldId;
    private final int baseX;
    private final int baseY;
    private final int baseZ;
    private final TreeSpecies species;
    private final long seed;
    private final TreePersonality personality;
    private final TreeRarity rarity;
    private final int targetHeight;
    private final int branchCount;
    private final int minBranchLength;
    private final int maxBranchLength;
    private final int branchBias;
    private final int canopyRadius;
    private final int canopyRadiusX;
    private final int canopyRadiusY;
    private final int canopyRadiusZ;
    private final double canopyDensity;
    private final double branchStartRatio;
    private final double branchRiseChance;
    private final double rootChance;
    private final double vineChance;
    private final double groundDetailChance;
    private final int trunkRadius;
    private final int canopyLayerCount;
    private final int canopyLayerSpread;
    private final int leanX;
    private final int leanZ;
    private final double leanStartRatio;
    private final String profileSampleId;
    private final String profileSampleSource;
    private final String parentKey;
    private final int generation;
    private final int shapeRevision;
    private TreeGrowthIntent currentIntent;
    private int planCursor;
    private int consecutivePrunes;
    private int blockedAttempts;
    private int lastIntentChangeAge;
    private int stageCleanupBurst;
    private int stageGrowthBurst;
    private int age;
    private TreeMaturityStage maturityStage;
    private long lastGrowthMillis;
    private long stalledUntilMillis;
    private int damageCount;
    private boolean stumpPresent;
    private volatile TreeTransitionLedger transitionLedger =
            TreeTransitionLedger.empty();

    TreeDna(
            UUID worldId,
            int baseX,
            int baseY,
            int baseZ,
            TreeSpecies species,
            long seed,
            TreePersonality personality,
            TreeRarity rarity,
            int targetHeight,
            int branchCount,
            int minBranchLength,
            int maxBranchLength,
            int branchBias,
            int canopyRadius,
            int canopyRadiusX,
            int canopyRadiusY,
            int canopyRadiusZ,
            double canopyDensity,
            double branchStartRatio,
            double branchRiseChance,
            double rootChance,
            double vineChance,
            double groundDetailChance,
            int trunkRadius,
            int canopyLayerCount,
            int canopyLayerSpread,
            int leanX,
            int leanZ,
            double leanStartRatio,
            String profileSampleId,
            String profileSampleSource,
            String parentKey,
            int generation,
            int shapeRevision,
            TreeGrowthIntent currentIntent,
            int planCursor,
            int consecutivePrunes,
            int blockedAttempts,
            int lastIntentChangeAge,
            int stageCleanupBurst,
            int stageGrowthBurst,
            int age,
            TreeMaturityStage maturityStage,
            long lastGrowthMillis,
            long stalledUntilMillis,
            int damageCount,
            boolean stumpPresent
    ) {
        this.worldId = worldId;
        this.baseX = baseX;
        this.baseY = baseY;
        this.baseZ = baseZ;
        this.species = species;
        this.seed = seed;
        this.personality = personality == null ? TreePersonality.BALANCED : personality;
        this.rarity = rarity == null ? TreeRarity.COMMON : rarity;
        this.targetHeight = Math.max(Math.max(4, targetHeight), TreeShapeProfile.targetHeightFloor(this.species, this.personality, this.rarity));
        this.branchCount = TreeDnaShapeRules.normalizeBranchCount(this.species, this.personality, this.targetHeight, branchCount);
        int branchLengthFloor = TreeShapeProfile.branchLengthFloor(this.species, this.personality, this.targetHeight);
        this.minBranchLength = Math.max(1, Math.min(Math.max(minBranchLength, 1), Math.max(maxBranchLength, branchLengthFloor)));
        this.maxBranchLength = Math.max(this.minBranchLength, Math.max(maxBranchLength, branchLengthFloor));
        this.branchBias = branchBias;
        this.canopyRadius = canopyRadius;
        int horizontalFloor = Math.max(
                TreeDnaShapeRules.minimumHorizontalCanopyRadius(this.species, this.personality, this.targetHeight),
                TreeShapeProfile.canopyRadiusFloor(this.species, this.personality, this.rarity, this.targetHeight, true)
        );
        int normalizedRadiusX = Math.max(Math.max(1, canopyRadiusX), horizontalFloor);
        int normalizedRadiusZ = Math.max(Math.max(1, canopyRadiusZ),
                Math.max(TreeDnaShapeRules.minimumHorizontalCanopyRadius(this.species, this.personality, this.targetHeight),
                        TreeShapeProfile.canopyRadiusFloor(this.species, this.personality, this.rarity, this.targetHeight, false)));
        this.canopyRadiusX = normalizedRadiusX;
        this.canopyRadiusZ = normalizedRadiusZ;
        this.canopyRadiusY = TreeDnaShapeRules.normalizeCanopyVerticalRadius(this.species, this.personality, this.targetHeight, canopyRadiusY, normalizedRadiusX, normalizedRadiusZ);
        this.canopyDensity = canopyDensity;
        this.branchStartRatio = TreeDnaShapeRules.normalizeBranchStart(this.species, this.personality, this.targetHeight, branchStartRatio);
        this.branchRiseChance = TreeDnaShapeRules.clamp(branchRiseChance, 0.0D, 0.85D);
        this.rootChance = rootChance;
        this.vineChance = vineChance;
        this.groundDetailChance = groundDetailChance;
        this.trunkRadius = Math.max(TreeShapeProfile.trunkWidthFloor(this.species, this.personality, this.rarity, this.targetHeight), Math.max(1, Math.min(8, trunkRadius)));
        this.canopyLayerCount = Math.max(TreeShapeProfile.canopyLayerFloor(this.species, this.personality, this.rarity, this.targetHeight), Math.max(0, Math.min(7, canopyLayerCount)));
        this.canopyLayerSpread = Math.max(Math.max(this.canopyRadiusX, this.canopyRadiusZ), Math.max(0, Math.min(12, canopyLayerSpread)));
        this.leanX = Math.max(-1, Math.min(1, leanX));
        this.leanZ = Math.max(-1, Math.min(1, leanZ));
        this.leanStartRatio = TreeDnaShapeRules.clamp(leanStartRatio, 0.30D, 0.90D);
        this.profileSampleId = profileSampleId == null || profileSampleId.isBlank() ? "config-default" : profileSampleId;
        this.profileSampleSource = profileSampleSource == null || profileSampleSource.isBlank() ? "config.yml" : profileSampleSource;
        this.parentKey = parentKey == null || parentKey.isBlank() ? "wild" : parentKey;
        this.generation = Math.max(0, generation);
        this.shapeRevision = Math.max(0, shapeRevision);
        this.currentIntent = currentIntent == null ? TreeGrowthIntent.HEIGHT : currentIntent;
        this.planCursor = Math.max(0, planCursor);
        this.consecutivePrunes = Math.max(0, consecutivePrunes);
        this.blockedAttempts = Math.max(0, blockedAttempts);
        this.lastIntentChangeAge = Math.max(0, lastIntentChangeAge);
        this.stageCleanupBurst = Math.max(0, stageCleanupBurst);
        this.stageGrowthBurst = Math.max(0, stageGrowthBurst);
        this.age = Math.max(0, age);
        this.maturityStage = maturityStage;
        this.lastGrowthMillis = lastGrowthMillis;
        this.stalledUntilMillis = stalledUntilMillis;
        this.damageCount = damageCount;
        this.stumpPresent = stumpPresent;
    }

    static TreeDna create(World world, TreeCandidate candidate,
            TreeGrowthProfile profile, TreeProfileSample sample,
            String parentKey, int generation) {
        return TreeDnaFactory.create(
                world, candidate, profile, sample, parentKey, generation);
    }

    static TreeDna from(ConfigurationSection section) {
        return TreeDnaCodec.read(section);
    }

    void writeTo(ConfigurationSection section) {
        TreeDnaCodec.write(this, section);
    }

    String key() {
        return worldId + ":" + baseX + ":" + baseY + ":" + baseZ;
    }

    UUID worldId() {
        return worldId;
    }

    int baseX() {
        return baseX;
    }

    int baseY() {
        return baseY;
    }

    int baseZ() {
        return baseZ;
    }

    TreeSpecies species() {
        return species;
    }

    long seed() {
        return seed;
    }

    TreePersonality personality() {
        return personality;
    }

    TreeRarity rarity() {
        return rarity;
    }

    int age() {
        return age;
    }

    int targetHeight() {
        return targetHeight;
    }

    int branchCount() {
        return branchCount;
    }

    int minBranchLength() {
        return minBranchLength;
    }

    int maxBranchLength() {
        return maxBranchLength;
    }

    int branchBias() {
        return branchBias;
    }

    int canopyRadius() {
        return canopyRadius;
    }

    int canopyRadiusX() {
        return canopyRadiusX;
    }

    int canopyRadiusY() {
        return canopyRadiusY;
    }

    int canopyRadiusZ() {
        return canopyRadiusZ;
    }

    double canopyDensity() {
        return canopyDensity;
    }

    double branchStartRatio() {
        return branchStartRatio;
    }

    double branchRiseChance() {
        return branchRiseChance;
    }

    double rootChance() {
        return rootChance;
    }

    double vineChance() {
        return vineChance;
    }

    double groundDetailChance() {
        return groundDetailChance;
    }

    int trunkRadius() {
        return trunkRadius;
    }

    int trunkWidth() {
        return trunkRadius;
    }

    int trunkWidthAt(int y) {
        if (trunkRadius <= 3) {
            return trunkRadius;
        }
        double progress = (y - baseY) / Math.max(1.0D, targetHeight);
        if (species == TreeSpecies.JUNGLE) {
            if (progress < 0.70D) {
                return trunkRadius;
            }
            if (progress < 0.88D) {
                return Math.max(2, trunkRadius - 2);
            }
            return 1;
        }
        if (species == TreeSpecies.DARK_OAK) {
            if (progress < 0.42D) {
                return trunkRadius;
            }
            if (progress < 0.68D) {
                return Math.max(3, trunkRadius - 2);
            }
            if (progress < 0.82D) {
                return Math.max(2, trunkRadius - 3);
            }
            return 1;
        }
        if (species == TreeSpecies.OAK) {
            if (progress < 0.34D) {
                return trunkRadius;
            }
            if (progress < 0.58D) {
                return Math.max(2, trunkRadius - 1);
            }
            if (progress < 0.78D && (personality == TreePersonality.ANCIENT_LANDMARK || rarity == TreeRarity.LANDMARK)) {
                return Math.max(2, trunkRadius - 2);
            }
            return 1;
        }
        if (species == TreeSpecies.CHERRY || species == TreeSpecies.MANGROVE) {
            if (progress < 0.38D) {
                return trunkRadius;
            }
            if (progress < 0.66D) {
                return Math.max(2, trunkRadius - 1);
            }
            return 1;
        }
        if (species == TreeSpecies.SPRUCE) {
            if (progress < 0.62D) {
                return trunkRadius;
            }
            if (progress < 0.84D) {
                return Math.max(2, trunkRadius - 1);
            }
            return 1;
        }
        if (progress < 0.16D) {
            return trunkRadius;
        }
        if (progress < 0.34D) {
            return Math.max(3, trunkRadius - 2);
        }
        if (progress < 0.56D) {
            return Math.max(2, trunkRadius - 4);
        }
        if (progress < 0.78D && (personality == TreePersonality.ANCIENT_LANDMARK || rarity == TreeRarity.LANDMARK)) {
            return Math.max(2, trunkRadius - 5);
        }
        return 1;
    }

    boolean hugeArchitecture() {
        return trunkRadius >= 4 || canopyLayerCount >= 3;
    }

    int canopyLayerCount() {
        return canopyLayerCount;
    }

    int canopyLayerSpread() {
        return canopyLayerSpread;
    }

    int trunkXAt(int y) {
        return baseX + leanOffset(y, leanX);
    }

    int trunkZAt(int y) {
        return baseZ + leanOffset(y, leanZ);
    }

    int leanX() {
        return leanX;
    }

    int leanZ() {
        return leanZ;
    }

    double leanStartRatio() {
        return leanStartRatio;
    }

    String profileSampleId() {
        return profileSampleId;
    }

    String profileSampleSource() {
        return profileSampleSource;
    }

    String parentKey() {
        return parentKey;
    }

    int generation() {
        return generation;
    }

    int shapeRevision() {
        return shapeRevision;
    }

    TreeGrowthIntent currentIntent() {
        return currentIntent;
    }

    void setCurrentIntent(TreeGrowthIntent intent) {
        if (intent == null || currentIntent == intent) {
            return;
        }
        currentIntent = intent;
        lastIntentChangeAge = age;
        consecutivePrunes = 0;
        blockedAttempts = 0;
    }

    int planCursor() {
        return planCursor;
    }

    void setPlanCursor(int planCursor) {
        this.planCursor = Math.max(0, planCursor);
    }

    int consecutivePrunes() {
        return consecutivePrunes;
    }

    void markPrunedNow() {
        consecutivePrunes = Math.min(12, consecutivePrunes + 1);
        blockedAttempts = 0;
        markGrownNow();
    }

    int blockedAttempts() {
        return blockedAttempts;
    }

    void markBlocked() {
        blockedAttempts = Math.min(24, blockedAttempts + 1);
    }

    void markPlacedForIntent(TreeGrowthIntent intent, int nextCursor) {
        setPlanCursor(nextCursor);
        if (intent != TreeGrowthIntent.CLEANUP) {
            consecutivePrunes = 0;
        }
        blockedAttempts = 0;
        markGrownNow();
    }

    int lastIntentChangeAge() {
        return lastIntentChangeAge;
    }

    int stageCleanupBurst() {
        return stageCleanupBurst;
    }

    int stageGrowthBurst() {
        return stageGrowthBurst;
    }

    boolean hasStageBurst() {
        return stageCleanupBurst > 0 || stageGrowthBurst > 0;
    }

    void consumeStageCleanupBurst() {
        if (stageCleanupBurst > 0) {
            stageCleanupBurst--;
        }
    }

    void completeStageCleanup() {
        // ## The persisted value now acts as an active transition marker. Cleanup
        // finishes only after a full scan returns clear, never after an attempt cap.
        stageCleanupBurst = 0;
        consecutivePrunes = 0;
        blockedAttempts = 0;
    }

    void consumeStageGrowthBurst() {
        if (stageGrowthBurst > 0) {
            stageGrowthBurst--;
        }
    }

    void completeStageTransition() {
        // ## Finalization closes the source snapshot but preserves the latest
        // evolved ownership epoch so completed branch envelopes remain auditable.
        stageGrowthBurst = 0;
        blockedAttempts = 0;
        transitionLedger = transitionLedger.completeTransition();
    }

    boolean hasOriginalShapeSnapshot() {
        return transitionLedger.hasSnapshot();
    }

    int originalShapeBlockCount() {
        return transitionLedger.sourceBlockCount();
    }

    int originalShapeLogCount() {
        return transitionLedger.sourceLogs().size();
    }

    int originalShapeLeafCount() {
        return transitionLedger.sourceLeaves().size();
    }

    int unresolvedOriginalShapeLeafCount() {
        return transitionLedger.unresolvedSourceLeafCount();
    }

    boolean originalShapeCaptureIsCurrent() {
        return transitionLedger.captureIsCurrent();
    }

    Set<String> originalShapeLogs() {
        return transitionLedger.sourceLogs();
    }

    Set<String> originalShapeLeaves() {
        return transitionLedger.sourceLeaves();
    }

    boolean wasOriginalShapeLeaf(String blockKey) {
        return transitionLedger.canRetireLeaf(blockKey);
    }

    Set<String> retiredOriginalShapeLeaves() {
        return transitionLedger.retiredLeaves();
    }

    int evolutionOwnershipVersion() {
        return transitionLedger.ownershipVersion();
    }

    int evolvedLogCount() {
        return transitionLedger.evolvedLogs().size();
    }

    int evolvedLeafCount() {
        return transitionLedger.evolvedLeaves().size();
    }
    Set<String> evolvedShapeLogs() {
        return transitionLedger.evolvedLogs();
    }

    Set<String> evolvedShapeLeaves() {
        return transitionLedger.evolvedLeaves();
    }

    boolean countsAsOwnedLog(String blockKey) {
        return transitionLedger.countsAsOwnedLog(blockKey);
    }

    boolean requiresEvolvedLeafOwnership() {
        return transitionLedger.requiresEvolvedLeafOwnership();
    }

    boolean isOriginalShapeLeaf(String blockKey) {
        return transitionLedger.isOriginalLeaf(blockKey);
    }

    boolean countsAsEvolvedLeaf(String blockKey) {
        return transitionLedger.countsAsEvolvedLeaf(blockKey);
    }

    synchronized boolean markEvolvedBlock(
            String blockKey, TreeBlockRole role) {
        TreeTransitionLedger updated = role == TreeBlockRole.CANOPY
                ? transitionLedger.recordEvolvedLeaf(blockKey)
                : role == TreeBlockRole.TRUNK
                        || role == TreeBlockRole.BRANCH
                        || role == TreeBlockRole.ROOT
                        ? transitionLedger.recordEvolvedLog(blockKey)
                        : transitionLedger;
        if (updated == transitionLedger) {
            return false;
        }
        transitionLedger = updated;
        return true;
    }

    synchronized boolean forgetEvolvedLog(String blockKey) {
        TreeTransitionLedger updated =
                transitionLedger.retireEvolvedLog(blockKey);
        if (updated == transitionLedger) {
            return false;
        }
        transitionLedger = updated;
        return true;
    }
    synchronized boolean markEvolvedLeaf(String blockKey) {
        TreeTransitionLedger updated =
                transitionLedger.recordEvolvedLeaf(blockKey);
        if (updated == transitionLedger) {
            return false;
        }
        transitionLedger = updated;
        return true;
    }

    synchronized boolean markOriginalShapeLeafRetired(String blockKey) {
        TreeTransitionLedger updated = transitionLedger.retireLeaf(blockKey);
        if (updated == transitionLedger) {
            return false;
        }
        transitionLedger = updated;
        return true;
    }

    void copyTransitionLedgerFrom(TreeDna source) {
        transitionLedger = source == null
                ? TreeTransitionLedger.empty()
                : source.transitionLedger;
    }

    void captureOriginalShape(Collection<String> logKeys,
            Collection<String> leafKeys) {
        // ## The transition owns an immutable block-level snapshot. Later live
        // tree changes cannot silently redefine which source blocks belonged
        // to the original trunk or crown.
        transitionLedger = TreeTransitionLedger.capture(logKeys, leafKeys);
    }

    void expandOriginalShape(Collection<String> logKeys,
            Collection<String> leafKeys) {
        // ## Ownership capture version 2 includes diagonally connected fancy
        // foliage while preserving every evolved/pruned decision already made.
        transitionLedger = transitionLedger.expandSource(logKeys, leafKeys);
    }

    void restoreOriginalShape(Collection<String> logKeys,
            Collection<String> leafKeys,
            Collection<String> retiredLeafKeys,
            Collection<String> evolvedLogKeys,
            Collection<String> evolvedLeafKeys,
            int ownershipVersion) {
        transitionLedger = TreeTransitionLedger.restore(
                logKeys, leafKeys, retiredLeafKeys,
                evolvedLogKeys, evolvedLeafKeys, ownershipVersion);
    }

    TreeMaturityStage maturityStage() {
        return maturityStage;
    }

    boolean advanceMaturity() {
        TreeMaturityStage before = maturityStage;
        maturityStage = maturityStage.next();
        if (maturityStage == before) {
            return false;
        }
        // ## A positive cleanup marker keeps the transition active until a full
        // target-aware crown scan is clear; the value remains useful in debug.
        stageCleanupBurst = switch (maturityStage) {
            case SMALL -> 2;
            case MEDIUM -> 6;
            case MATURE -> 8;
            case ANCIENT -> 10;
        };
        stageGrowthBurst = switch (maturityStage) {
            case SMALL -> 0;
            case MEDIUM -> 5;
            case MATURE -> 8;
            case ANCIENT -> 12;
        };
        setCurrentIntent(TreeGrowthIntent.CLEANUP);
        planCursor = 0;
        return true;
    }

    long lastGrowthMillis() {
        return lastGrowthMillis;
    }

    void markGrownNow() {
        lastGrowthMillis = System.currentTimeMillis();
        age++;
        if (damageCount > 0) {
            damageCount--;
        }
    }

    long stalledUntilMillis() {
        return stalledUntilMillis;
    }

    int damageCount() {
        return damageCount;
    }

    void markDamaged(long stallMillis) {
        damageCount = Math.min(20, damageCount + 1);
        stalledUntilMillis = Math.max(stalledUntilMillis, System.currentTimeMillis() + stallMillis);
    }

    boolean stumpPresent() {
        return stumpPresent;
    }

    void setStumpPresent(boolean stumpPresent) {
        this.stumpPresent = stumpPresent;
        if (!stumpPresent) {
            stalledUntilMillis = Long.MAX_VALUE;
        }
    }

    String planSignature(boolean rootsEnabled, org.bukkit.block.Biome biome) {
        return species.id()
                + "|" + seed
                + "|" + personality
                + "|" + rarity
                + "|" + targetHeight
                + "|" + branchCount
                + "|" + minBranchLength + "-" + maxBranchLength
                + "|" + branchBias
                + "|" + canopyRadiusX + "x" + canopyRadiusY + "x" + canopyRadiusZ
                + "|" + Math.round(canopyDensity * 1000.0D)
                + "|" + Math.round(branchStartRatio * 1000.0D)
                + "|" + Math.round(branchRiseChance * 1000.0D)
                + "|" + trunkRadius
                + "|" + canopyLayerCount
                + "|" + canopyLayerSpread
                + "|" + leanX + "," + leanZ
                + "|" + maturityStage
                + "|" + rootsEnabled
                + "|" + biome;
    }

    private int leanOffset(int y, int direction) {
        if (direction == 0) {
            return 0;
        }
        int start = baseY + Math.max(2, (int) Math.round(targetHeight * leanStartRatio));
        if (y < start) {
            return 0;
        }
        int leanSpan = Math.max(3, targetHeight - (start - baseY));
        int offset = Math.min(2, Math.max(1, (y - start) / Math.max(2, leanSpan / 2)));
        return direction * offset;
    }

}
