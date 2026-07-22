package org.slowtrees.nether;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slowtrees.core.SlowTreesPlugin;

final class NetherMapDebug {
    private final ConcurrentMap<String, SourceMapSnapshot> sourceMaps = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, MapCell>> sourceCells = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TranslationRule> translationRules = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, AtomicLong> translationCounts = new ConcurrentHashMap<>();
    private final AtomicInteger nextTranslationId = new AtomicInteger(1);
    private final AtomicLong replacements = new AtomicLong();
    private final AtomicBoolean saveRunning = new AtomicBoolean();
    private final AtomicLong nextSaveMillis = new AtomicLong();
    private final Deque<String> recentEvents = new ArrayDeque<>();
    private final String sessionStartedAt = Instant.now().toString();

    void recordReplacement(
            SlowTreesPlugin plugin,
            NetherCorruptionConfig config,
            PortalSource source,
            Block block,
            Material original,
            NetherMimicResult result
    ) {
        TranslationRule rule = translationRule(original, result.material());
        replacements.incrementAndGet();
        translationCounts.computeIfAbsent(rule.id(), id -> new AtomicLong()).incrementAndGet();
        recordEvent(config, "replace: " + format(block) + " "
                + original + " -> " + result.material()
                + " translation=" + rule.id()
                + " style=" + result.style().displayName());

        sourceCells.computeIfAbsent(source.key(), key -> new ConcurrentHashMap<>())
                .put(cellKey(block.getX(), block.getZ()), new MapCell(block.getX(), block.getY(), block.getZ(), rule.id(), result.style().displayName()));
        sourceMaps.put(source.key(), buildSnapshot(source, block.getWorld(), config));
        saveSoon(plugin);
    }

    void saveSoon(SlowTreesPlugin plugin) {
        long now = System.currentTimeMillis();
        long next = nextSaveMillis.get();
        if (now < next || !nextSaveMillis.compareAndSet(next, now + 10000L)) {
            return;
        }
        plugin.pathDebug().trace(plugin, "nether", "persistence.save-map-debug.schedule", "MapDebug.yml");
        saveAsync(plugin);
    }

    void saveAsync(SlowTreesPlugin plugin) {
        if (!saveRunning.compareAndSet(false, true)) {
            return;
        }

        plugin.pathDebug().trace(plugin, "nether", "scheduler.async-debug-save", "MapDebug.yml");
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                save(plugin);
            } finally {
                saveRunning.set(false);
            }
        });
    }

    void saveNow(SlowTreesPlugin plugin) {
        save(plugin);
    }

    private SourceMapSnapshot buildSnapshot(PortalSource source, World world, NetherCorruptionConfig config) {
        int radius = config.debugMapRadius();
        List<String> rows = new ArrayList<>();
        ConcurrentMap<String, MapCell> cells = sourceCells.getOrDefault(source.key(), new ConcurrentHashMap<>());

        for (int z = source.centerZ() - radius; z <= source.centerZ() + radius; z++) {
            List<String> row = new ArrayList<>();
            for (int x = source.centerX() - radius; x <= source.centerX() + radius; x++) {
                row.add(tokenAt(world, x, z, source, cells));
            }
            rows.add(String.join(" ", row));
        }

        return new SourceMapSnapshot(format(source, world), radius, "translation tokens are replacement pairs from translation-map", rows);
    }

    private String tokenAt(World world, int x, int z, PortalSource source, ConcurrentMap<String, MapCell> cells) {
        if (x == source.centerX() && z == source.centerZ()) {
            return "P";
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return "?";
        }

        MapCell cell = cells.get(cellKey(x, z));
        if (cell != null) {
            return Integer.toString(cell.translationId());
        }

        return ".";
    }

    private void save(SlowTreesPlugin plugin) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("session-started-at", sessionStartedAt);
        writeLegend(yaml.createSection("legend"));
        writeTranslationMap(yaml.createSection("translation-map"));
        yaml.set("counters.replacements", replacements.get());
        writeTranslationCounts(yaml.createSection("counters.by-translation"));
        yaml.set("recent-events", recentEventsSnapshot());

        ConfigurationSection sources = yaml.createSection("sources");
        int index = 0;
        for (Map.Entry<String, SourceMapSnapshot> entry : sourceMaps.entrySet()) {
            SourceMapSnapshot snapshot = entry.getValue();
            ConfigurationSection section = sources.createSection(Integer.toString(index++));
            section.set("source", snapshot.source());
            section.set("radius", snapshot.radius());
            section.set("row-format", snapshot.rowFormat());
            section.set("rows", snapshot.rows());
        }

        File file = new File(plugin.getDataFolder(), "MapDebug.yml");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for MapDebug.yml.");
            return;
        }

        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save Evolution MapDebug.yml.", ex);
        }
    }

    private void writeLegend(ConfigurationSection section) {
        section.set("number", "translation id from translation-map, such as GRASS_BLOCK -> NETHERRACK");
        section.set(".", "no recorded replacement at this x/z in this debug session");
        section.set("P", "portal/source center");
        section.set("?", "chunk not loaded while map was built");
        section.set("rows", "space-separated tokens, so ids can grow past 9 without losing readability");
    }

    private void writeTranslationMap(ConfigurationSection section) {
        translationRules.values().stream()
                .sorted((first, second) -> Integer.compare(first.id(), second.id()))
                .forEach(rule -> {
                    ConfigurationSection ruleSection = section.createSection(Integer.toString(rule.id()));
                    ruleSection.set("from", rule.from());
                    ruleSection.set("to", rule.to());
                    ruleSection.set("label", rule.from() + " -> " + rule.to());
                });
    }

    private void writeTranslationCounts(ConfigurationSection section) {
        translationCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> section.set(Integer.toString(entry.getKey()), entry.getValue().get()));
    }

    private TranslationRule translationRule(Material original, Material replacement) {
        String key = original.name() + "->" + replacement.name();
        return translationRules.computeIfAbsent(key, ignored -> new TranslationRule(nextTranslationId.getAndIncrement(), original.name(), replacement.name()));
    }

    private void recordEvent(NetherCorruptionConfig config, String event) {
        int maxEvents = config.debugRecentEvents();
        if (maxEvents <= 0) {
            return;
        }

        synchronized (recentEvents) {
            recentEvents.addLast(Instant.now() + " " + event);
            while (recentEvents.size() > maxEvents) {
                recentEvents.removeFirst();
            }
        }
    }

    private List<String> recentEventsSnapshot() {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents);
        }
    }

    private String cellKey(int x, int z) {
        return x + ":" + z;
    }

    private String format(Block block) {
        return block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private String format(PortalSource source, World world) {
        return world.getName() + " " + source.centerX() + "," + source.centerY() + "," + source.centerZ();
    }

    private record SourceMapSnapshot(String source, int radius, String rowFormat, List<String> rows) {
    }

    private record TranslationRule(int id, String from, String to) {
    }

    private record MapCell(int x, int y, int z, int translationId, String style) {
    }
}
