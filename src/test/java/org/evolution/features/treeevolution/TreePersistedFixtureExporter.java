package org.evolution.features.treeevolution;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * ## Converts private live-server DNA into coordinate-free test fixtures.
 *
 * <p>World IDs, absolute coordinates, and server paths are never written.
 */
public final class TreePersistedFixtureExporter {
    private static final UUID FIXTURE_WORLD = new UUID(0L, 42L);
    private static final int FIXTURE_BASE_X = 0;
    private static final int FIXTURE_BASE_Y = 64;
    private static final int FIXTURE_BASE_Z = 0;
    private static final int MAX_FIXTURES = 4;

    private TreePersistedFixtureExporter() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "usage: <tree-evolution.yml> <fixture-output.yml> "
                            + "[--volume=<tree-evolution-3dDebug.yml>] "
                            + "[<near-x> <near-z>]...");
        }
        Path volumePath = null;
        List<String> coordinateArguments = new ArrayList<>();
        for (int index = 2; index < args.length; index++) {
            if (args[index].startsWith("--volume=")) {
                volumePath = Path.of(args[index].substring(
                        "--volume=".length()));
            } else {
                coordinateArguments.add(args[index]);
            }
        }
        if (coordinateArguments.size() % 2 != 0) {
            throw new IllegalArgumentException(
                    "near-tree coordinates must be x/z pairs");
        }
        YamlConfiguration source = YamlConfiguration.loadConfiguration(
                new File(args[0]));
        ConfigurationSection trees =
                source.getConfigurationSection("trees");
        if (trees == null) {
            throw new IllegalStateException("missing trees section");
        }

        YamlConfiguration output = new YamlConfiguration();
        output.set("notes", "## Anonymized source-tree captures. World IDs "
                + "and absolute server coordinates are intentionally removed.");
        ConfigurationSection fixtures =
                output.createSection("fixtures");
        List<TreeDna> available = new ArrayList<>();
        for (String key : trees.getKeys(false)) {
            TreeDna dna = TreeDna.from(trees.getConfigurationSection(key));
            if (usable(dna)) {
                available.add(dna);
            }
        }
        Map<String, Map<?, ?>> liveVolumes = loadLiveVolumes(volumePath);
        List<TreeDna> selected = !coordinateArguments.isEmpty()
                ? selectNearest(available, coordinateArguments)
                : selectSpeciesSamples(available);
        int written = 0;
        for (TreeDna dna : selected) {
            writeFixture(fixtures, written++, dna,
                    liveVolumes.get(dna.key()));
        }
        if (written == 0) {
            throw new IllegalStateException(
                    "no active original-shape captures were found");
        }

        Path target = Path.of(args[1]);
        Files.createDirectories(target.toAbsolutePath().getParent());
        output.save(target.toFile());
        System.out.println("Exported anonymized tree fixtures=" + written
                + " to " + target.toAbsolutePath());
    }

    private static List<TreeDna> selectNearest(
            List<TreeDna> available, List<String> coordinates) {
        Set<TreeDna> selected = new LinkedHashSet<>();
        for (int index = 0; index < coordinates.size(); index += 2) {
            int x = Integer.parseInt(coordinates.get(index));
            int z = Integer.parseInt(coordinates.get(index + 1));
            available.stream()
                    .min(Comparator
                            .comparingLong((TreeDna dna) ->
                                    squaredDistance(dna, x, z))
                            .thenComparing(TreeDna::key))
                    .ifPresent(selected::add);
        }
        return List.copyOf(selected);
    }

    private static long squaredDistance(TreeDna dna, int x, int z) {
        long dx = (long) dna.baseX() - x;
        long dz = (long) dna.baseZ() - z;
        return dx * dx + dz * dz;
    }

    private static List<TreeDna> selectSpeciesSamples(
            List<TreeDna> available) {
        Set<TreeSpecies> represented = new HashSet<>();
        List<TreeDna> selected = new ArrayList<>();
        List<TreeDna> deferred = new ArrayList<>();
        for (TreeDna dna : available) {
            if (represented.add(dna.species())) {
                selected.add(dna);
            } else {
                deferred.add(dna);
            }
            if (selected.size() >= MAX_FIXTURES) {
                return List.copyOf(selected);
            }
        }
        for (TreeDna dna : deferred) {
            if (selected.size() >= MAX_FIXTURES) {
                break;
            }
            selected.add(dna);
        }
        return List.copyOf(selected);
    }

    static boolean usable(TreeDna dna) {
        return dna != null
                && ((dna.originalShapeLogCount() > 0
                        && dna.originalShapeLeafCount() > 0)
                    || (!dna.evolvedShapeLogs().isEmpty()
                        && !dna.evolvedShapeLeaves().isEmpty()));
    }

    private static void writeFixture(
            ConfigurationSection fixtures,
            int index,
            TreeDna source,
            Map<?, ?> liveVolume
    ) {
        TreeDna captured = capturedDna(liveVolume).orElse(source);
        TreeDna rebased = rebase(captured);
        ConfigurationSection fixture = fixtures.createSection(
                "captured-" + captured.species().id() + "-" + index);
        fixture.set("capture-kind", captured.hasOriginalShapeSnapshot()
                ? "persisted-live-transition"
                : "persisted-live-current-shape");
        fixture.set("source-log-count", rebased.originalShapeLogCount());
        fixture.set("source-leaf-count", rebased.originalShapeLeafCount());
        fixture.set("evolved-log-count",
                rebased.evolvedShapeLogs().size());
        fixture.set("evolved-leaf-count",
                rebased.evolvedShapeLeaves().size());
        rebased.writeTo(fixture.createSection("dna"));
        writeAnonymizedEnvironment(fixture, liveVolume);
    }

    private static Optional<TreeDna> capturedDna(Map<?, ?> liveVolume) {
        if (liveVolume == null) {
            return Optional.empty();
        }
        Object yamlText = liveVolume.get("dna-snapshot-yaml");
        if (!(yamlText instanceof String text) || text.isBlank()) {
            return Optional.empty();
        }
        try {
            YamlConfiguration snapshot = new YamlConfiguration();
            snapshot.loadFromString(text);
            return Optional.ofNullable(TreeDna.from(
                    snapshot.getConfigurationSection("dna")));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Map<String, Map<?, ?>> loadLiveVolumes(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return Map.of();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                path.toFile());
        Map<String, Map<?, ?>> captures = new LinkedHashMap<>();
        for (Map<?, ?> capture
                : yaml.getMapList("live-voxel-captures")) {
            Object tree = capture.get("tree");
            if (tree != null) {
                captures.put(String.valueOf(tree), capture);
            }
        }
        return Map.copyOf(captures);
    }

    private static void writeAnonymizedEnvironment(
            ConfigurationSection fixture,
            Map<?, ?> liveVolume
    ) {
        if (liveVolume == null) {
            return;
        }
        ConfigurationSection environment =
                fixture.createSection("environment");
        environment.set("notes", "## Exact sparse local volume; all XYZ "
                + "values are stump-relative and live identifiers were "
                + "discarded by the exporter.");
        environment.set("cells", sanitizedRows(
                liveVolume.get("cells"), List.of(
                        "x", "y", "z", "material", "category", "role",
                        "natural", "replaceable", "player-placed",
                        "persistent")));
        environment.set("unreadable-areas", sanitizedRows(
                liveVolume.get("unreadable-areas"), List.of(
                        "min-x", "min-z", "max-x", "max-z", "gate")));
    }

    private static List<Map<String, Object>> sanitizedRows(
            Object value,
            List<String> allowedKeys
    ) {
        if (!(value instanceof List<?> rows)) {
            return List.of();
        }
        List<Map<String, Object>> sanitized = new ArrayList<>();
        for (Object valueRow : rows) {
            if (!(valueRow instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> clean = new LinkedHashMap<>();
            for (String key : allowedKeys) {
                if (row.containsKey(key) && row.get(key) != null) {
                    clean.put(key, row.get(key));
                }
            }
            if (!clean.isEmpty()) {
                sanitized.add(Map.copyOf(clean));
            }
        }
        return List.copyOf(sanitized);
    }

    static TreeDna rebase(TreeDna source) {
        TreeDna rebased = new TreeDna(
                FIXTURE_WORLD,
                FIXTURE_BASE_X,
                FIXTURE_BASE_Y,
                FIXTURE_BASE_Z,
                source.species(),
                source.variant(),
                source.sourcePattern(),
                source.seed(),
                source.personality(),
                source.rarity(),
                source.targetHeight(),
                source.branchCount(),
                source.minBranchLength(),
                source.maxBranchLength(),
                source.branchBias(),
                source.canopyRadius(),
                source.canopyRadiusX(),
                source.canopyRadiusY(),
                source.canopyRadiusZ(),
                source.canopyDensity(),
                source.branchStartRatio(),
                source.branchRiseChance(),
                source.rootChance(),
                source.vineChance(),
                source.groundDetailChance(),
                source.trunkWidth(),
                source.canopyLayerCount(),
                source.canopyLayerSpread(),
                source.leanX(),
                source.leanZ(),
                source.leanStartRatio(),
                "anonymized-live-capture",
                "captured-tree-fixtures.yml",
                "wild",
                0,
                source.shapeRevision(),
                source.currentIntent(),
                source.planCursor(),
                source.consecutivePrunes(),
                source.blockedAttempts(),
                source.lastIntentChangeAge(),
                source.stageCleanupBurst(),
                source.stageGrowthBurst(),
                source.age(),
                source.maturityStage(),
                0L,
                0L,
                source.damageCount(),
                source.stumpPresent());
        rebased.restoreOriginalShape(
                rebaseKeys(source, source.originalShapeLogs()),
                rebaseKeys(source, source.originalShapeLeaves()),
                rebaseKeys(source, source.retiredOriginalShapeLeaves()),
                rebaseKeys(source, source.evolvedShapeLogs()),
                rebaseKeys(source, source.evolvedShapeLeaves()),
                source.evolutionOwnershipVersion());
        return rebased;
    }

    private static Set<String> rebaseKeys(
            TreeDna source,
            Set<String> keys
    ) {
        Set<String> rebased = new HashSet<>();
        for (String key : keys) {
            String[] parts = key.split(":");
            int offset = parts.length - 3;
            int x = Integer.parseInt(parts[offset]);
            int y = Integer.parseInt(parts[offset + 1]);
            int z = Integer.parseInt(parts[offset + 2]);
            rebased.add(FIXTURE_WORLD + ":"
                    + (FIXTURE_BASE_X + x - source.baseX()) + ":"
                    + (FIXTURE_BASE_Y + y - source.baseY()) + ":"
                    + (FIXTURE_BASE_Z + z - source.baseZ()));
        }
        return Set.copyOf(rebased);
    }
}
