package org.slowtrees.puddles;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slowtrees.core.SlowTreesPlugin;

final class PuddleDiagnostics {
    private final AtomicLong cycles = new AtomicLong();
    private final AtomicLong seeded = new AtomicLong();
    private final AtomicLong expanded = new AtomicLong();
    private final AtomicLong soaked = new AtomicLong();
    private final AtomicLong dried = new AtomicLong();
    private final AtomicLong rendered = new AtomicLong();
    private final AtomicLong restored = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong regionSkips = new AtomicLong();
    private final AtomicLong nextSaveMillis = new AtomicLong();
    private final AtomicBoolean saveRunning = new AtomicBoolean();
    private final Deque<String> recentEvents = new ArrayDeque<>();
    private final String sessionStartedAt = Instant.now().toString();

    void recordCycle() {
        cycles.incrementAndGet();
    }

    void recordSeeded() {
        seeded.incrementAndGet();
    }

    void recordExpanded() {
        expanded.incrementAndGet();
    }

    void recordSoaked() {
        soaked.incrementAndGet();
    }

    void recordDried() {
        dried.incrementAndGet();
    }

    void recordRendered(long amount) {
        rendered.addAndGet(Math.max(0L, amount));
    }

    void recordRestored(long amount) {
        restored.addAndGet(Math.max(0L, amount));
    }

    void recordRejected() {
        rejected.incrementAndGet();
    }

    void recordRegionSkip() {
        regionSkips.incrementAndGet();
    }

    long activeChanges() {
        return seeded.get() + expanded.get() + soaked.get() + dried.get();
    }

    void recordEvent(PuddleConfig config, String event) {
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

    void saveSoon(SlowTreesPlugin plugin, PuddleConfig config) {
        long now = System.currentTimeMillis();
        long next = nextSaveMillis.get();
        if (now < next || !nextSaveMillis.compareAndSet(next, now + 10000L)) {
            return;
        }
        plugin.pathDebug().trace(plugin, "puddles", "persistence.save-debug.schedule", "puddles.debug.yml");
        saveAsync(plugin, config);
    }

    void saveAsync(SlowTreesPlugin plugin, PuddleConfig config) {
        if (!saveRunning.compareAndSet(false, true)) {
            return;
        }
        plugin.pathDebug().trace(plugin, "puddles", "scheduler.async-debug-save", "puddles.debug.yml");
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                save(plugin, config);
            } finally {
                saveRunning.set(false);
            }
        });
    }

    void saveNow(SlowTreesPlugin plugin, PuddleConfig config) {
        plugin.pathDebug().trace(plugin, "puddles", "persistence.save-debug.now", "puddles.debug.yml");
        save(plugin, config);
    }

    private void save(SlowTreesPlugin plugin, PuddleConfig config) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("session-started-at", sessionStartedAt);
        yaml.set("enabled", config.enabled());
        yaml.set("step-ticks", config.stepTicks());
        yaml.set("radius", config.radius());
        yaml.set("max-puddles-per-world", config.maxPuddlesPerWorld());
        yaml.set("counters.cycles", cycles.get());
        yaml.set("counters.seeded", seeded.get());
        yaml.set("counters.expanded", expanded.get());
        yaml.set("counters.soaked", soaked.get());
        yaml.set("counters.dried", dried.get());
        yaml.set("counters.rendered-packet-blocks", rendered.get());
        yaml.set("counters.restored-packet-blocks", restored.get());
        yaml.set("counters.rejected", rejected.get());
        yaml.set("counters.region-skips", regionSkips.get());
        yaml.set("recent-events", snapshot());
        yaml.set("notes", "## Puddles are packet visuals, not real water blocks. Use this file with architecture-pathfinding.debug.yml and resource-report.debug.yml to trace rain growth, dry shrink, render, and safety gates.");
        File file = new File(plugin.getDataFolder(), "puddles.debug.yml");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for puddles.debug.yml.");
            return;
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save Evolution puddles.debug.yml.", ex);
        }
    }

    private List<String> snapshot() {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents);
        }
    }
}