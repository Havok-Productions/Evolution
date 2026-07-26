package org.evolution.features.treeevolution;

import java.util.Collection;
import java.util.Random;
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
        this.branchCount = normalizeBranchCount(this.species, this.personality, this.targetHeight, branchCount);
        int branchLengthFloor = TreeShapeProfile.branchLengthFloor(this.species, this.personality, this.targetHeight);
        this.minBranchLength = Math.max(1, Math.min(Math.max(minBranchLength, 1), Math.max(maxBranchLength, branchLengthFloor)));
        this.maxBranchLength = Math.max(this.minBranchLength, Math.max(maxBranchLength, branchLengthFloor));
        this.branchBias = branchBias;
        this.canopyRadius = canopyRadius;
        int horizontalFloor = Math.max(
                minimumHorizontalCanopyRadius(this.species, this.personality, this.targetHeight),
                TreeShapeProfile.canopyRadiusFloor(this.species, this.personality, this.rarity, this.targetHeight, true)
        );
        int normalizedRadiusX = Math.max(Math.max(1, canopyRadiusX), horizontalFloor);
        int normalizedRadiusZ = Math.max(Math.max(1, canopyRadiusZ),
                Math.max(minimumHorizontalCanopyRadius(this.species, this.personality, this.targetHeight),
                        TreeShapeProfile.canopyRadiusFloor(this.species, this.personality, this.rarity, this.targetHeight, false)));
        this.canopyRadiusX = normalizedRadiusX;
        this.canopyRadiusZ = normalizedRadiusZ;
        this.canopyRadiusY = normalizeCanopyVerticalRadius(this.species, this.personality, this.targetHeight, canopyRadiusY, normalizedRadiusX, normalizedRadiusZ);
        this.canopyDensity = canopyDensity;
        this.branchStartRatio = normalizeBranchStart(this.species, this.personality, this.targetHeight, branchStartRatio);
        this.branchRiseChance = clamp(branchRiseChance, 0.0D, 0.85D);
        this.rootChance = rootChance;
        this.vineChance = vineChance;
        this.groundDetailChance = groundDetailChance;
        this.trunkRadius = Math.max(TreeShapeProfile.trunkWidthFloor(this.species, this.personality, this.rarity, this.targetHeight), Math.max(1, Math.min(8, trunkRadius)));
        this.canopyLayerCount = Math.max(TreeShapeProfile.canopyLayerFloor(this.species, this.personality, this.rarity, this.targetHeight), Math.max(0, Math.min(7, canopyLayerCount)));
        this.canopyLayerSpread = Math.max(Math.max(this.canopyRadiusX, this.canopyRadiusZ), Math.max(0, Math.min(12, canopyLayerSpread)));
        this.leanX = Math.max(-1, Math.min(1, leanX));
        this.leanZ = Math.max(-1, Math.min(1, leanZ));
        this.leanStartRatio = clamp(leanStartRatio, 0.30D, 0.90D);
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

    static TreeDna create(World world, TreeCandidate candidate, TreeGrowthProfile profile, TreeProfileSample sample, String parentKey, int generation) {
        long seed = new Random().nextLong();
        Random random = new Random(seed ^ candidate.baseKey().hashCode());
        String text = sampleText(candidate.species(), sample);
        TreeRarity rarity = rarityFor(text, random);
        TreePersonality personality = personalityFor(candidate.species(), text, rarity, random);
        int targetHeight = randomRange(random, profile.minTargetHeight(), profile.maxTargetHeight());
        targetHeight = scaledHeight(targetHeight, candidate.species(), personality, rarity, text, random);
        targetHeight = Math.max(targetHeight, candidate.height() + random.nextInt(4));
        ShapeTraits shape = shapeTraits(candidate.species(), profile, sample, personality, rarity, targetHeight, random);
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
                normalizeBranchCount(candidate.species(), personality, targetHeight, randomRange(random, profile.minBranches(), profile.maxBranches())),
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
                CURRENT_SHAPE_REVISION,
                TreeGrowthIntent.HEIGHT,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                stageFor(candidate.height(), targetHeight),
                0L,
                0L,
                0,
                true
        );
    }

    static TreeDna from(ConfigurationSection section) {
        UUID worldId = UUID.fromString(section.getString("world-id", ""));
        TreeSpecies species = TreeSpecies.fromId(section.getString("species", "oak")).orElse(TreeSpecies.OAK);
        long seed = section.getLong("seed");
        TreePersonality personality = parsePersonality(section.getString("personality", "BALANCED"));
        TreeRarity rarity = parseRarity(section.getString("rarity", "COMMON"));
        int targetHeight = section.getInt("target-height");
        int legacyTrunkRadius = Math.max(1, section.getInt("trunk-radius", 1));
        int trunkWidth = section.contains("trunk-width")
                ? Math.max(1, section.getInt("trunk-width", legacyTrunkRadius))
                : legacyTrunkWidth(species, personality, rarity, targetHeight, seed, legacyTrunkRadius);
        int canopyLayerCount = section.contains("canopy-layer-count")
                ? Math.max(0, section.getInt("canopy-layer-count", 0))
                : legacyCanopyLayerCount(species, personality, rarity, targetHeight, seed);
        int canopyLayerSpread = section.contains("canopy-layer-spread")
                ? Math.max(0, section.getInt("canopy-layer-spread", 0))
                : legacyCanopyLayerSpread(canopyLayerCount, section.getInt("canopy-radius", 2), seed);
        TreeDna dna = new TreeDna(
                worldId,
                section.getInt("base.x"),
                section.getInt("base.y"),
                section.getInt("base.z"),
                species,
                seed,
                personality,
                rarity,
                targetHeight,
                section.getInt("branch-count"),
                Math.max(1, section.getInt("branch-length-min", 1)),
                Math.max(1, section.getInt("branch-length-max", 4)),
                section.getInt("branch-bias"),
                section.getInt("canopy-radius"),
                Math.max(1, section.getInt("canopy-radius-x", section.getInt("canopy-radius"))),
                Math.max(1, section.getInt("canopy-radius-y", section.getInt("canopy-radius"))),
                Math.max(1, section.getInt("canopy-radius-z", section.getInt("canopy-radius"))),
                section.getDouble("canopy-density"),
                section.getDouble("branch-start-ratio", 0.55D),
                section.getDouble("branch-rise-chance", 0.33D),
                section.getDouble("root-chance"),
                section.getDouble("vine-chance"),
                section.getDouble("ground-detail-chance"),
                trunkWidth,
                canopyLayerCount,
                canopyLayerSpread,
                section.getInt("lean.x", 0),
                section.getInt("lean.z", 0),
                section.getDouble("lean-start-ratio", 0.65D),
                section.getString("profile-sample-id", "config-default"),
                section.getString("profile-sample-source", "config.yml"),
                section.getString("lineage.parent-key", "wild"),
                section.getInt("lineage.generation", 0),
                section.getInt("shape-revision", 0),
                parseIntent(section.getString("growth.intent", "HEIGHT")),
                section.getInt("growth.plan-cursor", 0),
                section.getInt("growth.consecutive-prunes", 0),
                section.getInt("growth.blocked-attempts", 0),
                section.getInt("growth.last-intent-change-age", 0),
                section.getInt("growth.stage-cleanup-burst", 0),
                section.getInt("growth.stage-growth-burst", 0),
                section.getInt("age", 0),
                parseStage(section.getString("maturity-stage", "SMALL")),
                section.getLong("last-growth-millis"),
                section.getLong("stalled-until-millis"),
                section.getInt("damage-count"),
                section.getBoolean("stump-present", true)
        );
        dna.restoreOriginalShape(
                section.getStringList("transition.original-shape-logs"),
                section.getStringList("transition.original-shape-leaves"),
                section.getStringList("transition.retired-original-leaves"),
                section.getStringList("transition.evolved-logs"),
                section.getStringList("transition.evolved-leaves"),
                section.getInt("transition.ownership-version", 0));
        return dna;
    }

    void writeTo(ConfigurationSection section) {
        section.set("world-id", worldId.toString());
        section.set("base.x", baseX);
        section.set("base.y", baseY);
        section.set("base.z", baseZ);
        section.set("species", species.id());
        section.set("seed", seed);
        section.set("personality", personality.name());
        section.set("rarity", rarity.name());
        section.set("age", age);
        section.set("target-height", targetHeight);
        section.set("branch-count", branchCount);
        section.set("branch-length-min", minBranchLength);
        section.set("branch-length-max", maxBranchLength);
        section.set("branch-bias", branchBias);
        section.set("canopy-radius", canopyRadius);
        section.set("canopy-radius-x", canopyRadiusX);
        section.set("canopy-radius-y", canopyRadiusY);
        section.set("canopy-radius-z", canopyRadiusZ);
        section.set("canopy-density", canopyDensity);
        section.set("branch-start-ratio", branchStartRatio);
        section.set("branch-rise-chance", branchRiseChance);
        section.set("root-chance", rootChance);
        section.set("vine-chance", vineChance);
        section.set("ground-detail-chance", groundDetailChance);
        section.set("trunk-width", trunkRadius);
        section.set("trunk-radius", trunkRadius);
        section.set("canopy-layer-count", canopyLayerCount);
        section.set("canopy-layer-spread", canopyLayerSpread);
        section.set("lean.x", leanX);
        section.set("lean.z", leanZ);
        section.set("lean-start-ratio", leanStartRatio);
        section.set("profile-sample-id", profileSampleId);
        section.set("profile-sample-source", profileSampleSource);
        section.set("lineage.parent-key", parentKey);
        section.set("lineage.generation", generation);
        section.set("shape-revision", shapeRevision);
        section.set("growth.intent", currentIntent.name());
        section.set("growth.plan-cursor", planCursor);
        section.set("growth.consecutive-prunes", consecutivePrunes);
        section.set("growth.blocked-attempts", blockedAttempts);
        section.set("growth.last-intent-change-age", lastIntentChangeAge);
        section.set("growth.stage-cleanup-burst", stageCleanupBurst);
        section.set("growth.stage-growth-burst", stageGrowthBurst);
        section.set("transition.original-shape-logs",
                transitionLedger.sourceLogs().stream().sorted().toList());
        section.set("transition.original-shape-leaves",
                transitionLedger.sourceLeaves().stream().sorted().toList());
        section.set("transition.retired-original-leaves",
                transitionLedger.retiredLeaves().stream().sorted().toList());
        section.set("transition.evolved-logs",
                transitionLedger.evolvedLogs().stream().sorted().toList());
        section.set("transition.evolved-leaves",
                transitionLedger.evolvedLeaves().stream().sorted().toList());
        section.set("transition.ownership-version",
                transitionLedger.ownershipVersion());
        section.set("maturity-stage", maturityStage.name());
        section.set("last-growth-millis", lastGrowthMillis);
        section.set("stalled-until-millis", stalledUntilMillis);
        section.set("damage-count", damageCount);
        section.set("stump-present", stumpPresent);
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

    private void restoreOriginalShape(Collection<String> logKeys,
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

    private static TreeMaturityStage stageFor(int currentHeight, int targetHeight) {
        if (currentHeight >= targetHeight) {
            return TreeMaturityStage.MATURE;
        }
        if (currentHeight >= Math.max(4, targetHeight * 2 / 3)) {
            return TreeMaturityStage.MEDIUM;
        }
        return TreeMaturityStage.SMALL;
    }

    private static int normalizeBranchCount(TreeSpecies species, TreePersonality personality, int targetHeight, int branchCount) {
        int minimum = TreeShapeProfile.branchCountFloor(species, personality, targetHeight);
        if (targetHeight >= 18) {
            minimum = Math.max(minimum, species == TreeSpecies.BIRCH ? 3 : 4);
        } else if (targetHeight >= 12) {
            minimum = Math.max(minimum, species == TreeSpecies.BIRCH || species == TreeSpecies.SPRUCE ? 2 : 3);
        } else if (targetHeight >= 8) {
            minimum = Math.max(minimum, species == TreeSpecies.SPRUCE ? 2 : 1);
        }
        if (personality == TreePersonality.SPARSE) {
            minimum = Math.max(1, minimum - 1);
        }
        if (personality == TreePersonality.WIDE
                || personality == TreePersonality.FORKED
                || personality == TreePersonality.UMBRELLA
                || personality == TreePersonality.ANCIENT_LANDMARK) {
            minimum++;
        }
        return Math.max(branchCount, minimum);
    }

    private static int minimumHorizontalCanopyRadius(TreeSpecies species, TreePersonality personality, int targetHeight) {
        int minimum = 1;
        if (targetHeight >= 18) {
            minimum = 3;
        } else if (targetHeight >= 10) {
            minimum = 2;
        }
        if (species == TreeSpecies.SPRUCE && (personality == TreePersonality.SPIRE || personality == TreePersonality.TALL)) {
            minimum = Math.max(1, minimum - 1);
        }
        if (species == TreeSpecies.JUNGLE
                || species == TreeSpecies.DARK_OAK
                || species == TreeSpecies.CHERRY
                || personality == TreePersonality.WIDE
                || personality == TreePersonality.UMBRELLA
                || personality == TreePersonality.LAYERED) {
            minimum++;
        }
        if (personality == TreePersonality.SPARSE && targetHeight < 12) {
            minimum = Math.max(1, minimum - 1);
        }
        return Math.max(1, Math.min(5, minimum));
    }

    private static int normalizeCanopyVerticalRadius(TreeSpecies species, TreePersonality personality, int targetHeight, int radiusY, int radiusX, int radiusZ) {
        int vertical = Math.max(1, radiusY);
        int horizontal = Math.max(radiusX, radiusZ);
        int floor = TreeShapeProfile.canopyVerticalRadiusFloor(species, horizontal);
        if (species == TreeSpecies.SPRUCE && (personality == TreePersonality.SPIRE || personality == TreePersonality.TALL)) {
            return Math.max(floor, Math.min(Math.max(vertical, floor), Math.max(2, horizontal + 1)));
        }
        if (personality == TreePersonality.TALL && targetHeight >= 18) {
            return Math.max(floor, Math.min(Math.max(vertical, floor), Math.max(2, horizontal)));
        }
        return Math.max(floor, Math.min(Math.max(vertical, floor), Math.max(2, (horizontal / 2) + 1)));
    }

    private static double normalizeBranchStart(TreeSpecies species, TreePersonality personality, int targetHeight, double branchStartRatio) {
        double upper = species == TreeSpecies.SPRUCE ? 0.72D : 0.66D;
        if (targetHeight >= 16 && species != TreeSpecies.SPRUCE) {
            upper -= 0.04D;
        }
        if (personality == TreePersonality.WIDE
                || personality == TreePersonality.FORKED
                || personality == TreePersonality.UMBRELLA
                || personality == TreePersonality.ANCIENT_LANDMARK) {
            upper -= 0.06D;
        }
        if (personality == TreePersonality.SPARSE && targetHeight < 10) {
            upper += 0.05D;
        }
        return clamp(branchStartRatio, 0.25D, Math.max(0.48D, upper));
    }

    private static TreeMaturityStage parseStage(String value) {
        try {
            return TreeMaturityStage.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException ex) {
            return TreeMaturityStage.SMALL;
        }
    }

    private static TreePersonality parsePersonality(String value) {
        try {
            return TreePersonality.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException ex) {
            return TreePersonality.BALANCED;
        }
    }

    private static TreeRarity parseRarity(String value) {
        try {
            return TreeRarity.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException ex) {
            return TreeRarity.COMMON;
        }
    }

    private static TreeGrowthIntent parseIntent(String value) {
        try {
            return TreeGrowthIntent.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException ex) {
            return TreeGrowthIntent.HEIGHT;
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

    private static int randomRange(Random random, int min, int max) {
        int low = Math.min(min, max);
        int high = Math.max(min, max);
        return low + random.nextInt(high - low + 1);
    }

    private static String sampleText(TreeSpecies species, TreeProfileSample sample) {
        return ((sample == null ? "" : sample.sourceFile() + " " + sample.trunkPlacer() + " " + sample.foliagePlacer()) + " " + species.id()).toLowerCase(java.util.Locale.ROOT);
    }

    private static TreeRarity rarityFor(String text, Random random) {
        if (text.contains("mega") || text.contains("huge") || text.contains("giant") || text.contains("ancient")) {
            return random.nextDouble() < 0.35D ? TreeRarity.LANDMARK : TreeRarity.RARE;
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

    private static TreePersonality personalityFor(TreeSpecies species, String text, TreeRarity rarity, Random random) {
        if (rarity == TreeRarity.LANDMARK) {
            return random.nextBoolean() ? TreePersonality.ANCIENT_LANDMARK : TreePersonality.HOLLOW;
        }
        if (text.contains("fancy") || text.contains("large") || text.contains("wide")) {
            return TreePersonality.WIDE;
        }
        if (text.contains("tall") || text.contains("straight") || text.contains("mega")) {
            return TreePersonality.TALL;
        }
        if (text.contains("bending") || text.contains("forking")) {
            return TreePersonality.FORKED;
        }
        if (text.contains("bush") || text.contains("young") || text.contains("stump")) {
            return species == TreeSpecies.BIRCH ? TreePersonality.TALL : TreePersonality.BALANCED;
        }
        if (species == TreeSpecies.SPRUCE) {
            return random.nextBoolean() ? TreePersonality.SPIRE : TreePersonality.TALL;
        }
        if (species == TreeSpecies.ACACIA) {
            return random.nextBoolean() ? TreePersonality.UMBRELLA : TreePersonality.CROOKED;
        }
        if (species == TreeSpecies.CHERRY) {
            return random.nextBoolean() ? TreePersonality.LAYERED : TreePersonality.UMBRELLA;
        }
        if (species == TreeSpecies.DARK_OAK) {
            return random.nextBoolean() ? TreePersonality.DENSE : TreePersonality.WIDE;
        }
        if (species == TreeSpecies.BIRCH) {
            return random.nextBoolean() ? TreePersonality.TALL : TreePersonality.BALANCED;
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

    private static int scaledHeight(int baseHeight, TreeSpecies species, TreePersonality personality, TreeRarity rarity, String text, Random random) {
        double factor = switch (rarity) {
            case COMMON -> 1.0D;
            case UNCOMMON -> 1.16D;
            case RARE -> 1.36D;
            case LANDMARK -> 1.65D;
        };
        if (personality == TreePersonality.TALL || personality == TreePersonality.SPIRE || personality == TreePersonality.ANCIENT_LANDMARK) {
            factor += 0.25D;
        }
        if (personality == TreePersonality.WIDE || personality == TreePersonality.UMBRELLA || personality == TreePersonality.SPARSE) {
            factor -= 0.08D;
        }
        if (species == TreeSpecies.JUNGLE || species == TreeSpecies.SPRUCE) {
            factor += rarity == TreeRarity.LANDMARK ? 0.20D : 0.08D;
        }
        if (text.contains("mega") || text.contains("giant")) {
            factor += 0.25D;
        }
        int scaled = (int) Math.round(baseHeight * factor) + random.nextInt(rarity == TreeRarity.LANDMARK ? 8 : 4);
        return Math.max(TreeShapeProfile.targetHeightFloor(species, personality, rarity), Math.min(96, scaled));
    }

    private static ShapeTraits shapeTraits(TreeSpecies species, TreeGrowthProfile profile, TreeProfileSample sample, TreePersonality personality, TreeRarity rarity, int targetHeight, Random random) {
        String text = sampleText(species, sample);
        int baseRadius = Math.max(1, profile.canopyRadius());
        int radiusX = Math.max(1, baseRadius + random.nextInt(3) - 1);
        int radiusZ = Math.max(1, baseRadius + random.nextInt(3) - 1);
        int radiusY = Math.max(1, baseRadius - 1 + random.nextInt(2));
        double branchStart = 0.42D + (random.nextDouble() * 0.28D);
        double branchRise = 0.18D + (random.nextDouble() * 0.42D);

        if (text.contains("tall") || text.contains("straight") || text.contains("spruce") || text.contains("pine")) {
            radiusX = Math.max(1, radiusX - 1);
            radiusZ = Math.max(1, radiusZ - 1);
            radiusY = Math.max(radiusY, baseRadius + 1);
            branchStart += 0.12D;
            branchRise += 0.12D;
        }
        if (text.contains("fancy") || text.contains("large") || text.contains("wide") || text.contains("dark_oak")) {
            radiusX += 1 + random.nextInt(2);
            radiusZ += random.nextInt(2);
            radiusY = Math.max(1, radiusY - 1);
            branchStart -= 0.08D;
        }
        if (text.contains("bush") || text.contains("young") || text.contains("stump")) {
            radiusX += random.nextInt(2);
            radiusZ += random.nextInt(2);
            radiusY = Math.max(1, radiusY - 1);
            branchStart -= 0.14D;
            branchRise -= 0.08D;
        }
        if (text.contains("bending") || text.contains("forking") || text.contains("acacia")) {
            branchStart -= 0.05D;
            branchRise += 0.18D;
            radiusX += random.nextInt(2);
            radiusZ += random.nextInt(2);
        }
        if (text.contains("cherry")) {
            radiusX += 1;
            radiusZ += 1;
            radiusY = Math.max(1, radiusY - 1);
            branchStart -= 0.06D;
        }
        switch (personality) {
            case TALL, SPIRE -> {
                radiusX = Math.max(1, radiusX - 1);
                radiusZ = Math.max(1, radiusZ - 1);
                radiusY += 1;
                branchStart += 0.10D;
            }
            case WIDE, UMBRELLA, LAYERED -> {
                radiusX += 2;
                radiusZ += 1 + random.nextInt(2);
                radiusY = Math.max(1, radiusY - 1);
                branchStart -= 0.10D;
            }
            case DENSE -> {
                radiusX += 1;
                radiusZ += 1;
            }
            case SPARSE -> {
                radiusX = Math.max(1, radiusX - 1);
                radiusZ = Math.max(1, radiusZ - 1);
                branchRise -= 0.08D;
            }
            case CROOKED, WINDSWEPT -> branchRise += 0.14D;
            case HOLLOW, ANCIENT_LANDMARK -> {
                radiusX += 1;
                radiusZ += 1;
                radiusY += 1;
                branchStart -= 0.06D;
            }
            default -> {
            }
        }
        if (rarity == TreeRarity.RARE || rarity == TreeRarity.LANDMARK) {
            radiusX += 1;
            radiusZ += 1;
        }
        int trunkRadius = trunkWidthFor(species, personality, rarity, targetHeight, text, random);
        radiusX = Math.max(radiusX, TreeShapeProfile.canopyRadiusFloor(species, personality, rarity, targetHeight, true));
        radiusZ = Math.max(radiusZ, TreeShapeProfile.canopyRadiusFloor(species, personality, rarity, targetHeight, false));
        radiusY = Math.max(radiusY, TreeShapeProfile.canopyVerticalRadiusFloor(species, Math.max(radiusX, radiusZ)));
        int canopyLayerCount = canopyLayersFor(species, personality, rarity, targetHeight, text, random);
        int canopyLayerSpread = canopyLayerCount == 0 ? 0 : Math.max(Math.max(radiusX, radiusZ), Math.min(12, Math.max(radiusX, radiusZ) + random.nextInt(3)));
        int leanX = (personality == TreePersonality.CROOKED || personality == TreePersonality.WINDSWEPT || species == TreeSpecies.ACACIA) ? random.nextInt(3) - 1 : 0;
        int leanZ = (personality == TreePersonality.CROOKED || personality == TreePersonality.WINDSWEPT || species == TreeSpecies.ACACIA) ? random.nextInt(3) - 1 : 0;
        if (leanX == 0 && leanZ == 0 && (personality == TreePersonality.CROOKED || personality == TreePersonality.WINDSWEPT)) {
            leanX = random.nextBoolean() ? 1 : -1;
        }
        double leanStart = 0.45D + random.nextDouble() * 0.25D;
        return new ShapeTraits(radiusX, radiusY, radiusZ, clamp(branchStart, 0.30D, 0.78D), clamp(branchRise, 0.05D, 0.75D), trunkRadius, canopyLayerCount, canopyLayerSpread, leanX, leanZ, leanStart);
    }

    private static int trunkWidthFor(TreeSpecies species, TreePersonality personality, TreeRarity rarity, int targetHeight, String text, Random random) {
        int floor = TreeShapeProfile.trunkWidthFloor(species, personality, rarity, targetHeight);
        boolean naturalGiantSpecies = species == TreeSpecies.JUNGLE || species == TreeSpecies.SPRUCE || species == TreeSpecies.DARK_OAK || species == TreeSpecies.MANGROVE;
        boolean giantSignal = text.contains("mega") || text.contains("giant") || text.contains("huge") || text.contains("ancient");
        if (rarity == TreeRarity.LANDMARK && targetHeight >= 24 && (naturalGiantSpecies || personality == TreePersonality.ANCIENT_LANDMARK || giantSignal)) {
            return Math.max(floor, 4 + random.nextInt(3));
        }
        if ((rarity == TreeRarity.LANDMARK || personality == TreePersonality.HOLLOW) && targetHeight >= 20) {
            return Math.max(floor, 3 + random.nextInt(2));
        }
        if (rarity == TreeRarity.RARE && targetHeight >= 18 && (naturalGiantSpecies || personality == TreePersonality.WIDE || giantSignal)) {
            return Math.max(floor, 3 + random.nextInt(2));
        }
        if (rarity == TreeRarity.LANDMARK && targetHeight >= 16) {
            return Math.max(floor, 3);
        }
        int baseline = rarity == TreeRarity.RARE || personality == TreePersonality.HOLLOW || personality == TreePersonality.WIDE ? 2 : 1;
        return Math.max(floor, baseline);
    }

    private static int legacyTrunkWidth(TreeSpecies species, TreePersonality personality, TreeRarity rarity, int targetHeight, long seed, int legacyWidth) {
        if (legacyWidth >= 4) {
            return legacyWidth;
        }
        return Math.max(legacyWidth, trunkWidthFor(species, personality, rarity, targetHeight, "", new Random(seed ^ 0x71A771EEL)));
    }

    private static int canopyLayersFor(TreeSpecies species, TreePersonality personality, TreeRarity rarity, int targetHeight, String text, Random random) {
        boolean layeredSignal = text.contains("layer") || text.contains("mega") || text.contains("giant") || text.contains("fancy") || text.contains("large");
        if (rarity == TreeRarity.LANDMARK && targetHeight >= 24) {
            return Math.max(TreeShapeProfile.canopyLayerFloor(species, personality, rarity, targetHeight), 2 + random.nextInt(personality == TreePersonality.ANCIENT_LANDMARK ? 4 : 3));
        }
        if ((rarity == TreeRarity.RARE || layeredSignal) && targetHeight >= 18) {
            return Math.max(TreeShapeProfile.canopyLayerFloor(species, personality, rarity, targetHeight), 2 + random.nextInt(3));
        }
        if (personality == TreePersonality.LAYERED || species == TreeSpecies.CHERRY || species == TreeSpecies.SPRUCE) {
            return Math.max(TreeShapeProfile.canopyLayerFloor(species, personality, rarity, targetHeight), 1 + random.nextInt(3));
        }
        return TreeShapeProfile.canopyLayerFloor(species, personality, rarity, targetHeight);
    }

    private static int legacyCanopyLayerCount(TreeSpecies species, TreePersonality personality, TreeRarity rarity, int targetHeight, long seed) {
        return canopyLayersFor(species, personality, rarity, targetHeight, "", new Random(seed ^ 0x1A7E5EEDL));
    }

    private static int legacyCanopyLayerSpread(int layerCount, int canopyRadius, long seed) {
        if (layerCount <= 0) {
            return 0;
        }
        Random random = new Random(seed ^ 0x5F1EADL);
        return Math.max(2, Math.min(10, canopyRadius + 1 + random.nextInt(3)));
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

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private record ShapeTraits(int canopyRadiusX, int canopyRadiusY, int canopyRadiusZ, double branchStartRatio, double branchRiseChance, int trunkRadius, int canopyLayerCount, int canopyLayerSpread, int leanX, int leanZ, double leanStartRatio) {
    }
}
