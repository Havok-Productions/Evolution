package org.evolution.features.treeevolution;

import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/**
 * ## TREE DNA CODEC
 *
 * <p>Owns the stable YAML contract and legacy field migration. It performs no
 * planning, scheduling, or world access.</p>
 */
final class TreeDnaCodec {
    private TreeDnaCodec() {
    }

    static TreeDna read(ConfigurationSection section) {
        UUID worldId = UUID.fromString(
                section.getString("world-id", ""));
        TreeSpecies species = TreeSpecies.fromId(
                section.getString("species", "oak"))
                .orElse(TreeSpecies.OAK);
        long seed = section.getLong("seed");
        TreePersonality personality = parsePersonality(
                section.getString("personality", "BALANCED"));
        TreeRarity rarity = parseRarity(
                section.getString("rarity", "COMMON"));
        int targetHeight = section.getInt("target-height");
        int legacyTrunkRadius = Math.max(
                1, section.getInt("trunk-radius", 1));
        int trunkWidth = section.contains("trunk-width")
                ? Math.max(1, section.getInt(
                        "trunk-width", legacyTrunkRadius))
                : TreeDnaFactory.legacyTrunkWidth(
                        species, personality, rarity, targetHeight,
                        seed, legacyTrunkRadius);
        int canopyLayerCount = section.contains("canopy-layer-count")
                ? Math.max(0, section.getInt("canopy-layer-count", 0))
                : TreeDnaFactory.legacyCanopyLayerCount(
                        species, personality, rarity, targetHeight, seed);
        int canopyLayerSpread = section.contains("canopy-layer-spread")
                ? Math.max(0, section.getInt("canopy-layer-spread", 0))
                : TreeDnaFactory.legacyCanopyLayerSpread(
                        canopyLayerCount,
                        section.getInt("canopy-radius", 2), seed);
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
                Math.max(1, section.getInt(
                        "canopy-radius-x",
                        section.getInt("canopy-radius"))),
                Math.max(1, section.getInt(
                        "canopy-radius-y",
                        section.getInt("canopy-radius"))),
                Math.max(1, section.getInt(
                        "canopy-radius-z",
                        section.getInt("canopy-radius"))),
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
                section.getString(
                        "profile-sample-id", "config-default"),
                section.getString(
                        "profile-sample-source", "config.yml"),
                section.getString("lineage.parent-key", "wild"),
                section.getInt("lineage.generation", 0),
                section.getInt("shape-revision", 0),
                parseIntent(section.getString(
                        "growth.intent", "HEIGHT")),
                section.getInt("growth.plan-cursor", 0),
                section.getInt("growth.consecutive-prunes", 0),
                section.getInt("growth.blocked-attempts", 0),
                section.getInt("growth.last-intent-change-age", 0),
                section.getInt("growth.stage-cleanup-burst", 0),
                section.getInt("growth.stage-growth-burst", 0),
                section.getInt("age", 0),
                parseStage(section.getString(
                        "maturity-stage", "SMALL")),
                section.getLong("last-growth-millis"),
                section.getLong("stalled-until-millis"),
                section.getInt("damage-count"),
                section.getBoolean("stump-present", true));
        dna.restoreOriginalShape(
                section.getStringList(
                        "transition.original-shape-logs"),
                section.getStringList(
                        "transition.original-shape-leaves"),
                section.getStringList(
                        "transition.retired-original-leaves"),
                section.getStringList("transition.evolved-logs"),
                section.getStringList("transition.evolved-leaves"),
                section.getInt("transition.ownership-version", 0));
        return dna;
    }

    static void write(TreeDna dna, ConfigurationSection section) {
        section.set("world-id", dna.worldId().toString());
        section.set("base.x", dna.baseX());
        section.set("base.y", dna.baseY());
        section.set("base.z", dna.baseZ());
        section.set("species", dna.species().id());
        section.set("seed", dna.seed());
        section.set("personality", dna.personality().name());
        section.set("rarity", dna.rarity().name());
        section.set("age", dna.age());
        section.set("target-height", dna.targetHeight());
        section.set("branch-count", dna.branchCount());
        section.set("branch-length-min", dna.minBranchLength());
        section.set("branch-length-max", dna.maxBranchLength());
        section.set("branch-bias", dna.branchBias());
        section.set("canopy-radius", dna.canopyRadius());
        section.set("canopy-radius-x", dna.canopyRadiusX());
        section.set("canopy-radius-y", dna.canopyRadiusY());
        section.set("canopy-radius-z", dna.canopyRadiusZ());
        section.set("canopy-density", dna.canopyDensity());
        section.set("branch-start-ratio", dna.branchStartRatio());
        section.set("branch-rise-chance", dna.branchRiseChance());
        section.set("root-chance", dna.rootChance());
        section.set("vine-chance", dna.vineChance());
        section.set(
                "ground-detail-chance", dna.groundDetailChance());
        section.set("trunk-width", dna.trunkRadius());
        section.set("trunk-radius", dna.trunkRadius());
        section.set("canopy-layer-count", dna.canopyLayerCount());
        section.set("canopy-layer-spread", dna.canopyLayerSpread());
        section.set("lean.x", dna.leanX());
        section.set("lean.z", dna.leanZ());
        section.set("lean-start-ratio", dna.leanStartRatio());
        section.set("profile-sample-id", dna.profileSampleId());
        section.set(
                "profile-sample-source", dna.profileSampleSource());
        section.set("lineage.parent-key", dna.parentKey());
        section.set("lineage.generation", dna.generation());
        section.set("shape-revision", dna.shapeRevision());
        section.set("growth.intent", dna.currentIntent().name());
        section.set("growth.plan-cursor", dna.planCursor());
        section.set(
                "growth.consecutive-prunes", dna.consecutivePrunes());
        section.set(
                "growth.blocked-attempts", dna.blockedAttempts());
        section.set("growth.last-intent-change-age",
                dna.lastIntentChangeAge());
        section.set(
                "growth.stage-cleanup-burst", dna.stageCleanupBurst());
        section.set(
                "growth.stage-growth-burst", dna.stageGrowthBurst());
        section.set("transition.original-shape-logs",
                dna.originalShapeLogs().stream().sorted().toList());
        section.set("transition.original-shape-leaves",
                dna.originalShapeLeaves().stream().sorted().toList());
        section.set("transition.retired-original-leaves",
                dna.retiredOriginalShapeLeaves()
                        .stream().sorted().toList());
        section.set("transition.evolved-logs",
                dna.evolvedShapeLogs().stream().sorted().toList());
        section.set("transition.evolved-leaves",
                dna.evolvedShapeLeaves().stream().sorted().toList());
        section.set("transition.ownership-version",
                dna.evolutionOwnershipVersion());
        section.set("maturity-stage", dna.maturityStage().name());
        section.set("last-growth-millis", dna.lastGrowthMillis());
        section.set("stalled-until-millis", dna.stalledUntilMillis());
        section.set("damage-count", dna.damageCount());
        section.set("stump-present", dna.stumpPresent());
    }

    private static TreeMaturityStage parseStage(String value) {
        try {
            return TreeMaturityStage.valueOf(
                    value.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            return TreeMaturityStage.SMALL;
        }
    }

    private static TreePersonality parsePersonality(String value) {
        try {
            return TreePersonality.valueOf(
                    value.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            return TreePersonality.BALANCED;
        }
    }

    private static TreeRarity parseRarity(String value) {
        try {
            return TreeRarity.valueOf(
                    value.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            return TreeRarity.COMMON;
        }
    }

    private static TreeGrowthIntent parseIntent(String value) {
        try {
            return TreeGrowthIntent.valueOf(
                    value.toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException exception) {
            return TreeGrowthIntent.HEIGHT;
        }
    }
}
