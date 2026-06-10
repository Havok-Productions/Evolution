package org.slowtrees.core;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ArchitecturePathDebug {
    private final ConcurrentMap<String, AtomicLong> moduleCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> pathCounts = new ConcurrentHashMap<>();
    private final AtomicBoolean saveRunning = new AtomicBoolean();
    private final AtomicLong nextSaveMillis = new AtomicLong();
    private final Deque<String> recentEvents = new ArrayDeque<>();
    private volatile ArchitecturePathDebugConfig config;

    ArchitecturePathDebug(SlowTreesPlugin plugin) {
        this.config = ArchitecturePathDebugConfig.load(plugin);
    }

    void reload(SlowTreesPlugin plugin) {
        this.config = ArchitecturePathDebugConfig.load(plugin);
        trace(plugin, "core", "config.reload", "architecture path debug config refreshed");
    }

    public void trace(SlowTreesPlugin plugin, String module, String path, String detail) {
        ArchitecturePathDebugConfig currentConfig = config;
        if (!currentConfig.enabled()) {
            return;
        }

        moduleCounts.computeIfAbsent(module, key -> new AtomicLong()).incrementAndGet();
        pathCounts.computeIfAbsent(module + "." + path, key -> new AtomicLong()).incrementAndGet();
        recordRecent(currentConfig, module, path, detail);
        saveSoon(plugin, currentConfig);
    }

    void saveNow(SlowTreesPlugin plugin) {
        save(plugin);
    }

    private void saveSoon(SlowTreesPlugin plugin, ArchitecturePathDebugConfig currentConfig) {
        if (!plugin.isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        long next = nextSaveMillis.get();
        if (now < next || !nextSaveMillis.compareAndSet(next, now + currentConfig.saveIntervalMillis())) {
            return;
        }

        saveAsync(plugin);
    }

    private void saveAsync(SlowTreesPlugin plugin) {
        if (!saveRunning.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                save(plugin);
            } finally {
                saveRunning.set(false);
            }
        });
    }

    private void save(SlowTreesPlugin plugin) {
        YamlConfiguration yaml = new YamlConfiguration();
        ArchitecturePathDebugConfig currentConfig = config;
        yaml.set("enabled", currentConfig.enabled());
        yaml.set("recent-event-limit", currentConfig.recentEvents());
        yaml.set("save-interval-millis", currentConfig.saveIntervalMillis());
        writeCounts(yaml.createSection("module-counts"), moduleCounts);
        writeCounts(yaml.createSection("path-counts"), pathCounts);
        yaml.set("recent-events", recentEventsSnapshot());
        yaml.set("notes", "This file traces high-level decision paths. Feature-specific files still hold deeper wind/nether maps.");

        File file = new File(plugin.getDataFolder(), "architecture-pathfinding.debug.yml");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for architecture-pathfinding.debug.yml.");
            return;
        }

        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save architecture-pathfinding.debug.yml.", ex);
        }
    }

    private void writeCounts(ConfigurationSection section, ConcurrentMap<String, AtomicLong> counts) {
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> section.set(entry.getKey(), entry.getValue().get()));
    }

    private void recordRecent(ArchitecturePathDebugConfig currentConfig, String module, String path, String detail) {
        int limit = currentConfig.recentEvents();
        if (limit <= 0) {
            return;
        }

        synchronized (recentEvents) {
            recentEvents.addLast(Instant.now() + " [" + module + "] " + path + " -> " + detail);
            while (recentEvents.size() > limit) {
                recentEvents.removeFirst();
            }
        }
    }

    private List<String> recentEventsSnapshot() {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents);
        }
    }
}
