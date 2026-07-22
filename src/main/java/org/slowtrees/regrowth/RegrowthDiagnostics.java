package org.slowtrees.regrowth;

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

final class RegrowthDiagnostics {
    private static final int MAX_EVENTS = 1000;

    private final AtomicLong breaksInspected = new AtomicLong();
    private final AtomicLong structuralBreaksSuppressed = new AtomicLong();
    private final AtomicLong upperQueues = new AtomicLong();
    private final AtomicLong blocksPlaced = new AtomicLong();
    private final AtomicLong placementsBlocked = new AtomicLong();
    private final AtomicBoolean saveRunning = new AtomicBoolean();
    private final AtomicLong nextSaveMillis = new AtomicLong();
    private final Deque<String> recentEvents = new ArrayDeque<>();
    private final String sessionStartedAt = Instant.now().toString();

    void recordBreak() {
        breaksInspected.incrementAndGet();
    }

    void recordStructuralSuppression() {
        structuralBreaksSuppressed.incrementAndGet();
    }

    void recordUpperQueue() {
        upperQueues.incrementAndGet();
    }

    void recordPlaced() {
        blocksPlaced.incrementAndGet();
    }

    void recordPlacementBlocked() {
        placementsBlocked.incrementAndGet();
    }

    void recordEvent(String event) {
        synchronized (recentEvents) {
            recentEvents.addLast(Instant.now() + " " + event);
            while (recentEvents.size() > MAX_EVENTS) {
                recentEvents.removeFirst();
            }
        }
    }

    void saveSoon(SlowTreesPlugin plugin) {
        long now = System.currentTimeMillis();
        long next = nextSaveMillis.get();
        if (now < next || !nextSaveMillis.compareAndSet(next, now + 2000L)) {
            return;
        }

        saveAsync(plugin);
    }

    void saveAsync(SlowTreesPlugin plugin) {
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

    void saveNow(SlowTreesPlugin plugin) {
        save(plugin);
    }

    private void save(SlowTreesPlugin plugin) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("session-started-at", sessionStartedAt);
        yaml.set("counters.breaks-inspected", breaksInspected.get());
        yaml.set("counters.structural-breaks-suppressed", structuralBreaksSuppressed.get());
        yaml.set("counters.upper-queues", upperQueues.get());
        yaml.set("counters.blocks-placed", blocksPlaced.get());
        yaml.set("counters.placements-blocked", placementsBlocked.get());
        yaml.set("recent-events", recentEventsSnapshot());
        yaml.set("notes", "Focused regrowth trace. Use this with architecture-pathfinding.debug.yml when a log appears to regrow instantly.");

        File file = new File(plugin.getDataFolder(), "RegrowthDebug.yml");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for RegrowthDebug.yml.");
            return;
        }

        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save Evolution RegrowthDebug.yml.", ex);
        }
    }

    private List<String> recentEventsSnapshot() {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents);
        }
    }
}
