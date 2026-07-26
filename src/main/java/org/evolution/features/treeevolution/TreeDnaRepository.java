package org.evolution.features.treeevolution;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.evolution.coreparts.EvolutionPlugin;
import org.evolution.coreparts.ResourceReporter.ReportSample;

/**
 * ## Owns tree-DNA storage and persistence scheduling.
 *
 * <p>World inspection and live cache invalidation remain in the feature coordinator because
 * those operations must run on the owning Folia region. This repository only handles the
 * thread-safe record map and its disk representation.
 */
final class TreeDnaRepository {
    private final EvolutionPlugin plugin;
    private final TreeDnaNormalizer normalizer;
    private final TreeEvolutionDiagnostics diagnostics;
    private final ConcurrentMap<String, TreeDna> records = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TreeSeedlingRecord> seedlings =
            new ConcurrentHashMap<>();
    private final Object saveLock = new Object();
    private final AtomicBoolean saveRunning = new AtomicBoolean();
    private final AtomicLong nextSaveMillis = new AtomicLong();
    private final AtomicLong dirtyVersion = new AtomicLong();
    private final AtomicLong savedVersion = new AtomicLong();

    TreeDnaRepository(
            EvolutionPlugin plugin,
            TreeDnaNormalizer normalizer,
            TreeEvolutionDiagnostics diagnostics
    ) {
        this.plugin = plugin;
        this.normalizer = normalizer;
        this.diagnostics = diagnostics;
    }

    ConcurrentMap<String, TreeDna> records() {
        return records;
    }

    TreeSeedlingRecord seedlingAt(org.bukkit.block.Block block) {
        return seedlings.get(TreeSeedlingRecord.key(block));
    }

    void registerSeedling(TreeSeedlingRecord seedling) {
        seedlings.put(seedling.key(), seedling);
        markDirty("seedling-register");
    }

    TreeSeedlingRecord removeSeedling(
            org.bukkit.block.Block block, String reason) {
        TreeSeedlingRecord removed =
                seedlings.remove(TreeSeedlingRecord.key(block));
        if (removed != null) {
            markDirty("seedling-remove-" + reason);
        }
        return removed;
    }

    void load(TreeEvolutionConfig config) {
        File file = file();
        if (!file.exists()) {
            plugin.pathDebug().trace(plugin, "tree-evolution", "persistence.load-missing", "tree-evolution.yml");
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int loaded = 0;
        int normalized = 0;
        ConfigurationSection trees = yaml.getConfigurationSection("trees");
        if (trees != null) {
            for (String key : trees.getKeys(false)) {
                ConfigurationSection section = trees.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                try {
                    TreeDna dna = TreeDna.from(section);
                    TreeDnaNormalizer.NormalizedDna normalizedDna =
                            normalizer.normalize(dna, config.maximumStage());
                    records.put(normalizedDna.dna().key(), normalizedDna.dna());
                    if (normalizedDna.changed()) {
                        normalized++;
                        diagnostics.recordDnaNormalized(
                                config, dna, normalizedDna.dna(), normalizedDna.summary());
                        plugin.pathDebug().traceSampled(
                                plugin,
                                "tree-evolution",
                                "state.dna-normalize",
                                normalizedDna.dna().key() + " " + normalizedDna.summary());
                    }
                    loaded++;
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning(
                            "Skipping invalid tree DNA entry '" + key + "': " + ex.getMessage());
                }
            }
        }

        int loadedSeedlings = 0;
        ConfigurationSection seedlingSection =
                yaml.getConfigurationSection("seedlings");
        if (seedlingSection != null) {
            for (String key : seedlingSection.getKeys(false)) {
                ConfigurationSection section =
                        seedlingSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                try {
                    TreeSeedlingRecord seedling = TreeSeedlingRecord.from(section);
                    seedlings.put(seedling.key(), seedling);
                    loadedSeedlings++;
                } catch (RuntimeException ex) {
                    plugin.getLogger().warning(
                            "Skipping invalid owned seedling entry '" + key
                                    + "': " + ex.getMessage());
                }
            }
        }

        diagnostics.recordDnaLoaded(loaded);
        plugin.pathDebug().trace(
                plugin,
                "tree-evolution",
                "persistence.load",
                "tree-evolution.yml entries=" + loaded
                        + " seedlings=" + loadedSeedlings
                        + " normalized=" + normalized);
        if (normalized > 0) {
            markDirty("load-normalized=" + normalized);
            saveNow("dna-normalize");
        }
    }
    void markDirty(String reason) {
        long version = dirtyVersion.incrementAndGet();
        plugin.pathDebug().traceSampled(
                plugin,
                "tree-evolution",
                "persistence.dirty",
                "version=" + version + " reason=" + reason + " entries=" + records.size());
    }

    void save(TreeEvolutionConfig config) {
        if (dirtyVersion.get() <= savedVersion.get()) {
            plugin.pathDebug().traceSampled(
                    plugin,
                    "tree-evolution",
                    "persistence.save-clean",
                    "tree-evolution.yml entries=" + records.size());
            return;
        }
        if (!plugin.isEnabled()) {
            saveNow("plugin-disabled");
            return;
        }

        long now = System.currentTimeMillis();
        long next = nextSaveMillis.get();
        if (now < next
                || !nextSaveMillis.compareAndSet(next, now + config.dnaSaveIntervalMillis())) {
            plugin.pathDebug().traceSampled(
                    plugin,
                    "tree-evolution",
                    "persistence.save-debounce",
                    "tree-evolution.yml entries=" + records.size()
                            + " next-ms=" + Math.max(0L, next - now));
            return;
        }
        saveAsync();
    }

    void saveNow(String reason) {
        try (ReportSample sample =
                plugin.resourceReporter().begin("tree-evolution", "persistence.save-tree-dna")) {
            synchronized (saveLock) {
                plugin.pathDebug().trace(
                        plugin,
                        "tree-evolution",
                        "persistence.save",
                        "tree-evolution.yml entries=" + records.size() + " reason=" + reason);
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.set(
                        "notes",
                        "## Persistent tree DNA and owned offspring saplings. "
                                + "This stores generated parameters, not copied schematic layouts.");
                ConfigurationSection trees = yaml.createSection("trees");
                int index = 0;
                for (TreeDna dna : records.values()) {
                    dna.writeTo(trees.createSection(Integer.toString(index++)));
                }
                ConfigurationSection seedlingSection =
                        yaml.createSection("seedlings");
                int seedlingIndex = 0;
                for (TreeSeedlingRecord seedling : seedlings.values()) {
                    seedling.writeTo(seedlingSection.createSection(
                            Integer.toString(seedlingIndex++)));
                }

                File file = file();
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    plugin.getLogger().warning("Could not create plugin data folder for tree DNA.");
                    sample.detail(
                            "folder-create-failed entries=" + records.size() + " reason=" + reason);
                    return;
                }
                try {
                    yaml.save(file);
                    savedVersion.set(dirtyVersion.get());
                    sample.workUnits(records.size())
                            .changedUnits(1)
                            .detail("entries=" + records.size()
                                    + " seedlings=" + seedlings.size()
                                    + " reason=" + reason);
                } catch (IOException ex) {
                    sample.detail(
                            "failed entries=" + records.size() + " reason=" + reason + " "
                                    + ex.getClass().getSimpleName());
                    plugin.getLogger().log(Level.WARNING, "Could not save tree evolution DNA.", ex);
                }
            }
        }
    }

    private void saveAsync() {
        if (!saveRunning.compareAndSet(false, true)) {
            plugin.pathDebug().traceSampled(
                    plugin,
                    "tree-evolution",
                    "persistence.save-skip-running",
                    "tree-evolution.yml entries=" + records.size());
            return;
        }

        plugin.pathDebug().trace(
                plugin,
                "tree-evolution",
                "scheduler.async-save",
                "tree-evolution.yml entries=" + records.size());
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                saveNow("async");
            } finally {
                saveRunning.set(false);
            }
        });
    }

    private File file() {
        return new File(plugin.getDataFolder(), "tree-evolution.yml");
    }
}
