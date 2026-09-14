package org.evolution.features.treeevolution;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.evolution.coreparts.EvolutionPlugin;
import org.evolution.coreparts.DebugFileRotator;
import org.evolution.coreparts.ResourceReporter.ReportSample;

/**
 * Bounded persistent evidence for tree deformation investigations.
 *
 * <p>## This is diagnostic state, not constructor state. Keeping it outside
 * {@link TreeDna} prevents historical observations from affecting scheduling,
 * ownership, or saved-tree performance.</p>
 */
final class TreeDeformationHistoryStore {
    static final String FILE_NAME = "tree-deformation-history.debug.yml";
    private static final int MAX_TREES = 32;
    private static final int MAX_COORDINATES_PER_TREE = 384;
    private static final int MAX_EVENTS_PER_COORDINATE = 12;
    private static final int MAX_ANOMALY_BUNDLES = 24;
    private static final long CHURN_REPORT_COOLDOWN_MILLIS = 15_000L;

    private final LinkedHashMap<String, TreeHistory> histories =
            new LinkedHashMap<>(16, 0.75F, true);
    private final Deque<Map<String, Object>> anomalyBundles =
            new ArrayDeque<>();
    private final Map<String, Long> lastChurnReport = new LinkedHashMap<>();
    private long sequence;

    synchronized void load(EvolutionPlugin plugin) {
        histories.clear();
        anomalyBundles.clear();
        lastChurnReport.clear();
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        sequence = Math.max(0L, yaml.getLong("sequence"));
        for (Map<?, ?> raw : yaml.getMapList("mutation-events")) {
            Map<String, Object> event = stringMap(raw);
            String tree = String.valueOf(event.getOrDefault("tree", "unknown"));
            String coordinate = String.valueOf(
                    event.getOrDefault("relative-coordinate", "unknown"));
            treeHistory(tree).add(coordinate, event);
        }
        for (Map<?, ?> raw : yaml.getMapList("anomaly-bundles")) {
            anomalyBundles.addLast(stringMap(raw));
        }
        trimBundles();
        trimTrees();
    }

    synchronized boolean recordMutation(
            TreeDna dna,
            Block block,
            Material before,
            Material after,
            TreeBlockRole role,
            TreePlacementAugment augment,
            String constructorMarker,
            String action,
            String reason
    ) {
        String relative = relativeCoordinate(dna, block);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("sequence", ++sequence);
        event.put("at", Instant.now().toString());
        event.put("tree", dna.key());
        event.put("relative-coordinate", relative);
        event.put("world-coordinate", block.getX() + "," + block.getY()
                + "," + block.getZ());
        event.put("action", action);
        event.put("before", before.name());
        event.put("after", after.name());
        event.put("role", role == null ? "UNKNOWN" : role.name());
        event.put("augment", augment == null
                ? TreePlacementAugment.UNCLASSIFIED.name() : augment.name());
        event.put("constructor", constructorMarker);
        event.put("reason", reason);
        event.put("stage", dna.maturityStage().name());
        event.put("intent", dna.currentIntent().name());
        event.put("shape-revision", dna.shapeRevision());
        event.put("plan-cursor", dna.planCursor());
        event.put("blocked-attempts", dna.blockedAttempts());
        TreeHistory history = treeHistory(dna.key());
        CoordinateHistory coordinate = history.add(relative, event);
        trimTrees();

        if (!coordinate.isChurning()) {
            return false;
        }
        String churnKey = dna.key() + "@" + relative;
        long now = System.currentTimeMillis();
        long last = lastChurnReport.getOrDefault(churnKey, 0L);
        if (now - last < CHURN_REPORT_COOLDOWN_MILLIS) {
            return false;
        }
        lastChurnReport.put(churnKey, now);
        return true;
    }

    synchronized void recordAnomalyBundle(Map<String, Object> bundle) {
        anomalyBundles.addLast(new LinkedHashMap<>(bundle));
        trimBundles();
    }

    synchronized List<Map<String, Object>> mutationEvents() {
        List<Map<String, Object>> events = new ArrayList<>();
        for (Map.Entry<String, TreeHistory> tree : histories.entrySet()) {
            for (Map.Entry<String, CoordinateHistory> coordinate
                    : tree.getValue().coordinates.entrySet()) {
                events.addAll(coordinate.getValue().events);
            }
        }
        events.sort((first, second) -> Long.compare(
                number(first.get("sequence")),
                number(second.get("sequence"))));
        return events;
    }

    synchronized List<Map<String, Object>> anomalyBundles() {
        return new ArrayList<>(anomalyBundles);
    }

    synchronized List<Map<String, Object>> hotCoordinates(String treeKey) {
        TreeHistory history = histories.get(treeKey);
        if (history == null) {
            return List.of();
        }
        return history.coordinates.entrySet().stream()
                .filter(entry -> entry.getValue().events.size() >= 3)
                .sorted((first, second) -> Integer.compare(
                        second.getValue().events.size(),
                        first.getValue().events.size()))
                .limit(24)
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("relative-coordinate", entry.getKey());
                    row.put("mutation-count", entry.getValue().events.size());
                    row.put("churning", entry.getValue().isChurning());
                    row.put("recent-events",
                            new ArrayList<>(entry.getValue().events));
                    return Map.copyOf(row);
                })
                .toList();
    }

    void save(EvolutionPlugin plugin) {
        PersistenceSnapshot snapshot;
        try (ReportSample sample = plugin.resourceReporter().begin(
                "tree-evolution",
                "diagnostics.deformation-history.snapshot")) {
            snapshot = persistenceSnapshot();
            sample.workUnits(snapshot.mutationEvents().size()
                            + snapshot.anomalyBundles().size())
                    .detail("events=" + snapshot.mutationEvents().size()
                            + " bundles="
                            + snapshot.anomalyBundles().size());
        }

        // ## Never hold the live history monitor while SnakeYAML constructs or
        // writes a multi-megabyte file. Region threads record and inspect audit
        // evidence; only the detached snapshot crosses into file I/O.
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("sequence", snapshot.sequence());
        yaml.set("limits.trees", MAX_TREES);
        yaml.set("limits.coordinates-per-tree", MAX_COORDINATES_PER_TREE);
        yaml.set("limits.events-per-coordinate", MAX_EVENTS_PER_COORDINATE);
        yaml.set("limits.anomaly-bundles", MAX_ANOMALY_BUNDLES);
        // ## Keep the numeric hierarchy and geometry dictionary beside the
        // evidence it explains. It is generated from production definitions,
        // so a constructor refactor cannot silently leave stale debug notes.
        yaml.set("constructor-translation",
                TreeConstructorDebugTranslator.translation());
        yaml.set("mutation-events", snapshot.mutationEvents());
        yaml.set("anomaly-bundles", snapshot.anomalyBundles());
        yaml.set("notes", "## Persistent bounded mutation timelines and "
                + "automatic original/current/target deformation bundles. "
                + "This file is diagnostic only and never drives construction.");
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning(
                    "Could not create plugin data folder for " + FILE_NAME);
            return;
        }
        try (ReportSample sample = plugin.resourceReporter().begin(
                "tree-evolution",
                "diagnostics.deformation-history.write")) {
            DebugFileRotator.rotateIfOversized(
                    plugin, file, 8L * 1024L * 1024L, 2);
            yaml.save(file);
            sample.workUnits(snapshot.mutationEvents().size()
                            + snapshot.anomalyBundles().size())
                    .changedUnits(1)
                    .detail("events=" + snapshot.mutationEvents().size()
                            + " bundles="
                            + snapshot.anomalyBundles().size());
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not save " + FILE_NAME + ".", ex);
        }
    }

    private synchronized PersistenceSnapshot persistenceSnapshot() {
        return new PersistenceSnapshot(
                sequence,
                List.copyOf(mutationEvents()),
                List.copyOf(anomalyBundles()));
    }

    private TreeHistory treeHistory(String treeKey) {
        return histories.computeIfAbsent(treeKey,
                ignored -> new TreeHistory());
    }

    private void trimTrees() {
        while (histories.size() > MAX_TREES) {
            String oldest = histories.keySet().iterator().next();
            histories.remove(oldest);
        }
    }

    private void trimBundles() {
        while (anomalyBundles.size() > MAX_ANOMALY_BUNDLES) {
            anomalyBundles.removeFirst();
        }
    }

    private static String relativeCoordinate(TreeDna dna, Block block) {
        return (block.getX() - dna.baseX()) + ","
                + (block.getY() - dna.baseY()) + ","
                + (block.getZ() - dna.baseZ());
    }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        Map<String, Object> converted = new LinkedHashMap<>();
        raw.forEach((key, value) -> converted.put(String.valueOf(key), value));
        return converted;
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private record PersistenceSnapshot(
            long sequence,
            List<Map<String, Object>> mutationEvents,
            List<Map<String, Object>> anomalyBundles
    ) {
    }

    private static final class TreeHistory {
        private final LinkedHashMap<String, CoordinateHistory> coordinates =
                new LinkedHashMap<>(16, 0.75F, true);

        private CoordinateHistory add(
                String coordinate, Map<String, Object> event) {
            CoordinateHistory history = coordinates.computeIfAbsent(
                    coordinate, ignored -> new CoordinateHistory());
            history.add(event);
            while (coordinates.size() > MAX_COORDINATES_PER_TREE) {
                String oldest = coordinates.keySet().iterator().next();
                coordinates.remove(oldest);
            }
            return history;
        }
    }

    private static final class CoordinateHistory {
        private final Deque<Map<String, Object>> events = new ArrayDeque<>();

        private void add(Map<String, Object> event) {
            events.addLast(Map.copyOf(event));
            while (events.size() > MAX_EVENTS_PER_COORDINATE) {
                events.removeFirst();
            }
        }

        private boolean isChurning() {
            if (events.size() < 4) {
                return false;
            }
            int directionChanges = 0;
            Boolean previousPlaced = null;
            for (Map<String, Object> event : events) {
                boolean placed = !"AIR".equals(event.get("after"));
                if (previousPlaced != null && previousPlaced != placed) {
                    directionChanges++;
                }
                previousPlaced = placed;
            }
            return directionChanges >= 3;
        }
    }
}
