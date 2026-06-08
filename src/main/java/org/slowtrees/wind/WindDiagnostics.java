package org.slowtrees.wind;

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

final class WindDiagnostics {
    private final AtomicLong canopySearches = new AtomicLong();
    private final AtomicLong canopiesFound = new AtomicLong();
    private final AtomicLong leafParticlesSpawned = new AtomicLong();
    private final AtomicLong litterCycles = new AtomicLong();
    private final AtomicLong litterRainSkips = new AtomicLong();
    private final AtomicLong litterTargetsFound = new AtomicLong();
    private final AtomicLong litterRejectedNoTarget = new AtomicLong();
    private final AtomicLong litterRejectedPlayerDistance = new AtomicLong();
    private final AtomicLong litterRejectedChunkCap = new AtomicLong();
    private final AtomicLong litterPlaced = new AtomicLong();
    private final AtomicBoolean saveRunning = new AtomicBoolean();
    private final AtomicLong nextSaveMillis = new AtomicLong();
    private final Deque<String> recentEvents = new ArrayDeque<>();

    void recordCanopySearch() {
        canopySearches.incrementAndGet();
    }

    void recordCanopyFound() {
        canopiesFound.incrementAndGet();
    }

    void recordLeafParticles(int amount) {
        leafParticlesSpawned.addAndGet(amount);
    }

    void recordLitterCycle() {
        litterCycles.incrementAndGet();
    }

    void recordRainSkip() {
        litterRainSkips.incrementAndGet();
    }

    void recordTargetFound() {
        litterTargetsFound.incrementAndGet();
    }

    void recordNoTarget() {
        litterRejectedNoTarget.incrementAndGet();
    }

    void recordPlayerDistanceReject() {
        litterRejectedPlayerDistance.incrementAndGet();
    }

    void recordChunkCapReject() {
        litterRejectedChunkCap.incrementAndGet();
    }

    void recordLitterPlaced() {
        litterPlaced.incrementAndGet();
    }

    void recordEvent(WindConfig config, String event) {
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

    long leafParticlesSpawned() {
        return leafParticlesSpawned.get();
    }

    long litterPlaced() {
        return litterPlaced.get();
    }

    void saveSoon(SlowTreesPlugin plugin, WindConfig config) {
        long now = System.currentTimeMillis();
        long next = nextSaveMillis.get();
        if (now < next || !nextSaveMillis.compareAndSet(next, now + 10000L)) {
            return;
        }
        saveAsync(plugin, config);
    }

    void saveAsync(SlowTreesPlugin plugin, WindConfig config) {
        if (!saveRunning.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                save(plugin, config);
            } finally {
                saveRunning.set(false);
            }
        });
    }

    private void save(SlowTreesPlugin plugin, WindConfig config) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("wind.enabled", config.enabled());
        yaml.set("wind.tree-search-radius", config.treeSearchRadius());
        yaml.set("wind.required-player-distance-chunks", config.requiredPlayerDistanceChunks());
        yaml.set("wind.leaf-litter.placement-step-ticks", config.leafLitterPlacementTicks());
        yaml.set("wind.leaf-litter.max-per-chunk", config.maxLeafLitterPerChunk());
        yaml.set("wind.counters.canopy-searches", canopySearches.get());
        yaml.set("wind.counters.canopies-found", canopiesFound.get());
        yaml.set("wind.counters.leaf-particles-spawned", leafParticlesSpawned.get());
        yaml.set("wind.counters.litter-cycles", litterCycles.get());
        yaml.set("wind.counters.litter-rain-skips", litterRainSkips.get());
        yaml.set("wind.counters.litter-targets-found", litterTargetsFound.get());
        yaml.set("wind.counters.litter-rejected-no-target", litterRejectedNoTarget.get());
        yaml.set("wind.counters.litter-rejected-player-distance", litterRejectedPlayerDistance.get());
        yaml.set("wind.counters.litter-rejected-chunk-cap", litterRejectedChunkCap.get());
        yaml.set("wind.counters.litter-placed", litterPlaced.get());
        yaml.set("wind.recent-events", recentEventsSnapshot());
        yaml.set("wind.notes", "If canopies-found rises but litter-targets-found stays 0, no valid natural ground target was found.");

        File file = new File(plugin.getDataFolder(), "debug.yml");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for debug.yml.");
            return;
        }

        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save SlowTrees debug.yml.", ex);
        }
    }

    private List<String> recentEventsSnapshot() {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents);
        }
    }
}
