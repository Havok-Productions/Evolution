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
        int baseX = section.getInt("base.x");
        int baseY = section.getInt("base.y");
        int baseZ = section.getInt("base.z");
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
        java.util.List<String> originalLogs = readCoordinates(
                section, "original-logs", "original-shape-logs",
                worldId, baseX, baseY, baseZ);
        java.util.List<String> originalLeaves = readCoordinates(
                section, "original-leaves", "original-shape-leaves",
                worldId, baseX, baseY, baseZ);
        TreeSourcePattern sourcePattern = new TreeSourcePattern(
                section.getInt("source-pattern.height", 0),
                section.getInt("source-pattern.trunk-footprint",
                        Math.max(1, trunkWidth * trunkWidth)),
                section.getInt("source-pattern.log-count", 0),
                section.getInt("source-pattern.leaf-count", 0),
                section.getInt("source-pattern.branch-spread", 0),
                section.getInt("source-pattern.canopy-radius",
                        section.getInt("canopy-radius", 2)),
                section.getInt("source-pattern.canopy-depth", 0),
                section.getDouble(
                        "source-pattern.canopy-start-ratio", 0.5D),
                section.getInt("source-pattern.crown-tiers",
                        canopyLayerCount),
                section.getInt("source-pattern.trunk-drift", 0),
                section.getBoolean("source-pattern.measured", false));
        if (!sourcePattern.measured()
                && (!originalLogs.isEmpty()
                        || !originalLeaves.isEmpty())) {
            // ## Revision-10 migration recovers architecture from the
            // persisted pre-evolution snapshot. This preserves existing
            // trees more accurately than guessing from old personality DNA.
            sourcePattern = TreeSourcePattern.fromSnapshot(
                    baseX, baseY, baseZ,
                    originalLogs, originalLeaves);
        }
        TreeSourcePattern classifiedSource = sourcePattern;
        TreeVariant variant = TreeVariant.fromId(
                        species, section.getString("variant", ""))
                .orElseGet(() -> classifiedSource.measured()
                        ? TreeVariantClassifier.classify(
                                species, classifiedSource)
                        : TreeVariantClassifier.inferLegacy(
                                species,
                                personality,
                                targetHeight,
                                trunkWidth,
                                section.getInt("canopy-radius", 2),
                                canopyLayerCount));
        TreeDna dna = new TreeDna(
                worldId,
                baseX,
                baseY,
                baseZ,
                species,
                variant,
                sourcePattern,
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
                originalLogs,
                originalLeaves,
                readCoordinates(section, "retired-original-leaves",
                        "retired-original-leaves", worldId,
                        baseX, baseY, baseZ),
                readCoordinates(section, "evolved-logs", "evolved-logs",
                        worldId, baseX, baseY, baseZ),
                readCoordinates(section, "evolved-leaves", "evolved-leaves",
                        worldId, baseX, baseY, baseZ),
                section.getInt("transition.ownership-version", 0));
        return dna;
    }

    static void write(TreeDna dna, ConfigurationSection section) {
        section.set("world-id", dna.worldId().toString());
        section.set("base.x", dna.baseX());
        section.set("base.y", dna.baseY());
        section.set("base.z", dna.baseZ());
        section.set("species", dna.species().id());
        section.set("variant", dna.variant().id());
        section.set(
                "source-pattern.height", dna.sourcePattern().height());
        section.set("source-pattern.trunk-footprint",
                dna.sourcePattern().trunkFootprint());
        section.set("source-pattern.log-count",
                dna.sourcePattern().logCount());
        section.set("source-pattern.leaf-count",
                dna.sourcePattern().leafCount());
        section.set("source-pattern.branch-spread",
                dna.sourcePattern().branchSpread());
        section.set("source-pattern.canopy-radius",
                dna.sourcePattern().canopyRadius());
        section.set("source-pattern.canopy-depth",
                dna.sourcePattern().canopyDepth());
        section.set("source-pattern.canopy-start-ratio",
                dna.sourcePattern().canopyStartRatio());
        section.set("source-pattern.crown-tiers",
                dna.sourcePattern().crownTiers());
        section.set("source-pattern.trunk-drift",
                dna.sourcePattern().trunkDrift());
        section.set("source-pattern.measured",
                dna.sourcePattern().measured());
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
        section.set("transition.coordinates.format", "relative-v1");
        section.set("transition.coordinates.original-logs",
                writeCoordinates(dna, dna.originalShapeLogs()));
        section.set("transition.coordinates.original-leaves",
                writeCoordinates(dna, dna.originalShapeLeaves()));
        section.set("transition.coordinates.retired-original-leaves",
                writeCoordinates(dna, dna.retiredOriginalShapeLeaves()));
        section.set("transition.coordinates.evolved-logs",
                writeCoordinates(dna, dna.evolvedShapeLogs()));
        section.set("transition.coordinates.evolved-leaves",
                writeCoordinates(dna, dna.evolvedShapeLeaves()));
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

    private static java.util.List<String> readCoordinates(
            ConfigurationSection section,
            String compactName,
            String legacyName,
            UUID worldId,
            int baseX,
            int baseY,
            int baseZ) {
        String compactPath = "transition.coordinates." + compactName;
        if (!section.contains(compactPath)) {
            return section.getStringList("transition." + legacyName);
        }
        java.util.List<String> decoded = new java.util.ArrayList<>();
        for (String encoded : section.getStringList(compactPath)) {
            String[] parts = encoded.split(",", -1);
            if (parts.length != 3) {
                continue;
            }
            try {
                int x = baseX + Integer.parseInt(parts[0]);
                int y = baseY + Integer.parseInt(parts[1]);
                int z = baseZ + Integer.parseInt(parts[2]);
                decoded.add(worldId + ":" + x + ":" + y + ":" + z);
            } catch (NumberFormatException ignored) {
                // ## One malformed receipt must not discard the remaining DNA.
            }
        }
        return java.util.List.copyOf(decoded);
    }

    private static java.util.List<String> writeCoordinates(
            TreeDna dna, java.util.Set<String> coordinates) {
        java.util.List<String> encoded = new java.util.ArrayList<>();
        for (String coordinate : coordinates) {
            String[] parts = coordinate.split(":", -1);
            if (parts.length < 4) {
                continue;
            }
            try {
                int x = Integer.parseInt(parts[parts.length - 3]);
                int y = Integer.parseInt(parts[parts.length - 2]);
                int z = Integer.parseInt(parts[parts.length - 1]);
                encoded.add((x - dna.baseX()) + ","
                        + (y - dna.baseY()) + ","
                        + (z - dna.baseZ()));
            } catch (NumberFormatException ignored) {
                // ## Legacy/custom malformed coordinates are skipped safely.
            }
        }
        encoded.sort(String::compareTo);
        return java.util.List.copyOf(encoded);
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
