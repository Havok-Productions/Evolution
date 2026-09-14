package org.evolution.features.treeevolution;

import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * ## Loads checked-in anonymized tree captures for deterministic replay.
 */
final class TreeCapturedFixtureStore {
    private static final List<String> RESOURCES = List.of(
            "tree-fixtures/captured-tree-fixtures.yml",
            "tree-fixtures/reported-tree-failures.yml",
            "tree-fixtures/reported-live-shapes.yml",
            // ## Two medium spruce reports that looked deformed live despite
            // passing the older broad visual thresholds.
            "tree-fixtures/reported-spruce-deformations.yml",
            // ## This completed-looking acacia retained nine evolved logs from
            // an older target. It guards bidirectional target reconciliation.
            "tree-fixtures/reported-acacia-obsolete-structure.yml",
            // ## Five anonymized live reports that exposed candidate/DNA
            // aliasing and stage overlap hidden by final-only replay.
            "tree-fixtures/reported-stage-accuracy.yml");
    private static final List<String> REQUIRED_LIVE_VOLUMES = List.of(
            "tree-fixtures/reported-live-volume-2026-08-06.yml",
            // ## Live spruce transition that exposed an impossible branch
            // envelope target and a neighboring-log completion obstacle.
            "tree-fixtures/reported-spruce-envelope-stall.yml",
            // ## A completed medium pine retained one log from its prior
            // target. Finalization must retire that protrusion before PASS.
            "tree-fixtures/reported-spruce-obsolete-protrusion.yml");

    private TreeCapturedFixtureStore() {
    }

    static List<Fixture> load() {
        List<Fixture> loaded = new ArrayList<>();
        for (String resource : RESOURCES) {
            loadResource(resource, loaded);
        }
        REQUIRED_LIVE_VOLUMES.forEach(
                resource -> loadResource(resource, loaded));
        return List.copyOf(loaded);
    }

    static List<Fixture> loadRequiredLiveVolumes() {
        List<Fixture> loaded = new ArrayList<>();
        REQUIRED_LIVE_VOLUMES.forEach(
                resource -> loadResource(resource, loaded));
        List<Fixture> exact = loaded.stream()
                .filter(fixture -> fixture.environment().present())
                .toList();
        if (exact.isEmpty()) {
            throw new IllegalStateException(
                    "required captured live voxel regression is missing");
        }
        return exact;
    }

    static List<Fixture> load(Path path) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                path.toFile());
        List<Fixture> loaded = new ArrayList<>();
        loadYaml(path.getFileName().toString(), yaml, loaded);
        return List.copyOf(loaded);
    }

    private static void loadResource(
            String resource, List<Fixture> loaded) {
        var stream = TreeCapturedFixtureStore.class
                .getClassLoader().getResourceAsStream(resource);
        if (stream == null) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        loadYaml(resource.substring(resource.lastIndexOf('/') + 1),
                yaml, loaded);
    }

    private static void loadYaml(
            String label,
            YamlConfiguration yaml,
            List<Fixture> loaded) {
        ConfigurationSection fixtures =
                yaml.getConfigurationSection("fixtures");
        if (fixtures == null) {
            return;
        }
        for (String id : fixtures.getKeys(false)) {
            ConfigurationSection section =
                    fixtures.getConfigurationSection(id);
            TreeDna dna = section == null ? null
                    : TreeDna.from(section.getConfigurationSection("dna"));
            boolean hasCurrentShape = dna != null
                    && !dna.evolvedShapeLogs().isEmpty()
                    && !dna.evolvedShapeLeaves().isEmpty();
            if (dna == null
                    || (!dna.hasOriginalShapeSnapshot()
                        && !hasCurrentShape)) {
                throw new IllegalStateException(
                        "captured fixture " + id
                                + " has no source or evolved snapshot");
            }
            // ## Production normalizes persisted DNA before constructor
            // ownership is interpreted. Seed captured voxels from that same
            // migrated ledger so revision cleanup cannot label a source leaf
            // as evolved, or mistake retired evidence for owned foliage.
            TreeDna ownershipDna = new TreeDnaNormalizer().normalize(
                    dna, dna.maturityStage()).dna();
            loaded.add(new Fixture(
                    label + ":" + id,
                    dna,
                    section.getBoolean(
                            "expected-initial-deformation", false),
                    loadEnvironment(section, ownershipDna)));
        }
    }

    private static TreeCapturedEnvironmentFixture loadEnvironment(
            ConfigurationSection fixture,
            TreeDna dna
    ) {
        ConfigurationSection environment =
                fixture.getConfigurationSection("environment");
        if (environment == null) {
            return TreeCapturedEnvironmentFixture.empty();
        }
        List<TreeCapturedEnvironmentFixture.Cell> cells =
                new ArrayList<>();
        Map<String, PlannedTreeBlock> target =
                TreeShapeSmokeTest.treeBodyPlan(dna).blocksByKey();
        for (Map<?, ?> row : environment.getMapList("cells")) {
            Material material = Material.matchMaterial(
                    text(row, "material", "AIR"));
            TreeCapturedEnvironmentFixture.Category category = enumValue(
                    TreeCapturedEnvironmentFixture.Category.class,
                    text(row, "category", "ENVIRONMENT"),
                    TreeCapturedEnvironmentFixture.Category.ENVIRONMENT);
            category = coherentCategory(
                    dna, row, material, category, target);
            TreeBlockRole role = enumValue(
                    TreeBlockRole.class,
                    text(row, "role", ""), null);
            role = canonicalLiveRole(material, role);
            cells.add(new TreeCapturedEnvironmentFixture.Cell(
                    number(row, "x"), number(row, "y"),
                    number(row, "z"),
                    material == null ? Material.AIR : material,
                    category, role,
                    bool(row, "natural"),
                    bool(row, "replaceable"),
                    bool(row, "player-placed"),
                    bool(row, "persistent")));
        }
        List<TreeCapturedEnvironmentFixture.UnreadableArea> unreadable =
                new ArrayList<>();
        for (Map<?, ?> row
                : environment.getMapList("unreadable-areas")) {
            TreeSimulatedMinecraftEnvironment.Gate gate = enumValue(
                    TreeSimulatedMinecraftEnvironment.Gate.class,
                    text(row, "gate", "REGION_NOT_OWNED"),
                    TreeSimulatedMinecraftEnvironment.Gate.REGION_NOT_OWNED);
            unreadable.add(
                    new TreeCapturedEnvironmentFixture.UnreadableArea(
                            number(row, "min-x"),
                            number(row, "min-z"),
                            number(row, "max-x"),
                            number(row, "max-z"), gate));
        }
        return new TreeCapturedEnvironmentFixture(
                List.copyOf(cells), List.copyOf(unreadable));
    }

    private static TreeCapturedEnvironmentFixture.Category coherentCategory(
            TreeDna dna,
            Map<?, ?> row,
            Material material,
            TreeCapturedEnvironmentFixture.Category category,
            Map<String, PlannedTreeBlock> target
    ) {
        if (category == TreeCapturedEnvironmentFixture.Category.ENVIRONMENT
                || category
                        == TreeCapturedEnvironmentFixture.Category.AIR_OVERRIDE
                || category
                        == TreeCapturedEnvironmentFixture.Category.NEIGHBOR) {
            return category;
        }
        String coordinate = (dna.baseX() + number(row, "x")) + ":"
                + (dna.baseY() + number(row, "y")) + ":"
                + (dna.baseZ() + number(row, "z"));
        String key = dna.worldId() + ":" + coordinate;
        boolean leaf = material != null
                && material.name().endsWith("_LEAVES");
        boolean currentReceipt = leaf
                ? dna.evolvedShapeLeaves().contains(key)
                : dna.evolvedShapeLogs().contains(key);
        boolean sourceReceipt = leaf
                ? dna.originalShapeLeaves().contains(key)
                        && !dna.retiredOriginalShapeLeaves().contains(key)
                : dna.originalShapeLogs().contains(key);
        // ## Captured category labels are observations, while the paired DNA
        // ledger is ownership authority. Older files can contain SOURCE cells
        // whose receipt was already retired or EVOLVED cells paired with a
        // newer save. Never let either stale label authorize destruction.
        if (currentReceipt) {
            return TreeCapturedEnvironmentFixture.Category.EVOLVED;
        }
        if (sourceReceipt) {
            return TreeCapturedEnvironmentFixture.Category.SOURCE;
        }
        PlannedTreeBlock planned = target.get(coordinate);
        if (planned != null && material == planned.material()) {
            return TreeCapturedEnvironmentFixture.Category.PLANNED_UNOWNED;
        }
        return TreeCapturedEnvironmentFixture.Category.NEIGHBOR;
    }

    private static TreeBlockRole canonicalLiveRole(
            Material material, TreeBlockRole capturedRole) {
        if (material != null
                && material.name().endsWith("_LEAVES")) {
            return TreeBlockRole.CANOPY;
        }
        if (material != null && (material.name().endsWith("_LOG")
                || material.name().endsWith("_WOOD")
                || material.name().endsWith("_STEM")
                || material.name().endsWith("_HYPHAE"))) {
            return capturedRole == TreeBlockRole.TRUNK
                            || capturedRole == TreeBlockRole.BRANCH
                            || capturedRole == TreeBlockRole.ROOT
                    ? capturedRole : TreeBlockRole.TRUNK;
        }
        return capturedRole;
    }

    private static int number(Map<?, ?> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number
                ? number.intValue()
                : Integer.parseInt(String.valueOf(value));
    }

    private static boolean bool(Map<?, ?> row, String key) {
        Object value = row.get(key);
        return value instanceof Boolean bool
                ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String text(
            Map<?, ?> row, String key, String fallback) {
        Object value = row.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static <T extends Enum<T>> T enumValue(
            Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim()
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    record Fixture(
            String id,
            TreeDna dna,
            boolean expectedInitialDeformation,
            TreeCapturedEnvironmentFixture environment
    ) {
    }
}
