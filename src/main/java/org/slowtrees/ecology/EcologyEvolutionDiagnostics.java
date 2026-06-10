package org.slowtrees.ecology;

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
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slowtrees.core.SlowTreesPlugin;

final class EcologyEvolutionDiagnostics {
    private final AtomicLong searches = new AtomicLong();
    private final AtomicLong candidates = new AtomicLong();
    private final AtomicLong tallerTrees = new AtomicLong();
    private final AtomicLong branches = new AtomicLong();
    private final AtomicLong canopies = new AtomicLong();
    private final AtomicLong forestFloor = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicBoolean saveRunning = new AtomicBoolean();
    private final AtomicLong nextSaveMillis = new AtomicLong();
    private final Deque<String> recentEvents = new ArrayDeque<>();
    private final String sessionStartedAt = Instant.now().toString();
    private volatile List<String> lastMapRows = List.of();
    private volatile String lastMapCenter = "none";

    void recordSearch() {
        searches.incrementAndGet();
    }

    void recordCandidate() {
        candidates.incrementAndGet();
    }

    void recordReject() {
        rejected.incrementAndGet();
    }

    void recordAction(SlowTreesPlugin plugin, EcologyEvolutionConfig config, String action, Block block, String detail) {
        switch (action) {
            case "height" -> tallerTrees.incrementAndGet();
            case "branch" -> branches.incrementAndGet();
            case "canopy" -> canopies.incrementAndGet();
            case "floor" -> forestFloor.incrementAndGet();
            default -> {
            }
        }
        recordEvent(config, action + ": " + format(block) + " " + detail);
        buildMap(block, config.debugMapRadius());
        saveSoon(plugin, config);
    }

    void recordEvent(EcologyEvolutionConfig config, String event) {
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

    long changedBlocks() {
        return tallerTrees.get() + branches.get() + canopies.get() + forestFloor.get();
    }

    void saveSoon(SlowTreesPlugin plugin, EcologyEvolutionConfig config) {
        long now = System.currentTimeMillis();
        long next = nextSaveMillis.get();
        if (now < next || !nextSaveMillis.compareAndSet(next, now + 5000L)) {
            return;
        }
        saveAsync(plugin, config);
    }

    void saveAsync(SlowTreesPlugin plugin, EcologyEvolutionConfig config) {
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

    void saveNow(SlowTreesPlugin plugin, EcologyEvolutionConfig config) {
        save(plugin, config);
    }

    private void buildMap(Block center, int radius) {
        World world = center.getWorld();
        List<String> rows = new ArrayList<>();
        for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
            List<String> row = new ArrayList<>();
            for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
                row.add(tokenAt(world, x, z, center));
            }
            rows.add(String.join(" ", row));
        }
        lastMapCenter = format(center);
        lastMapRows = rows;
    }

    private String tokenAt(World world, int x, int z, Block center) {
        if (x == center.getX() && z == center.getZ()) {
            return "A";
        }
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return "?";
        }
        Block surface = world.getHighestBlockAt(x, z);
        Material type = surface.getType();
        if (isLog(type)) {
            return "T";
        }
        if (isLeaf(type)) {
            return "L";
        }
        Block below = surface.getType().isAir() ? surface.getRelative(0, -1, 0) : surface;
        if (isLog(below.getType())) {
            return "T";
        }
        if (isLeaf(below.getType())) {
            return "L";
        }
        if (type == Material.LEAF_LITTER || type == Material.SHORT_GRASS || type == Material.FERN) {
            return "U";
        }
        if (below.getType() == Material.GRASS_BLOCK || below.getType() == Material.DIRT || below.getType() == Material.PODZOL) {
            return "G";
        }
        return ".";
    }

    private void save(SlowTreesPlugin plugin, EcologyEvolutionConfig config) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("session-started-at", sessionStartedAt);
        yaml.set("enabled", config.enabled());
        yaml.set("step-ticks", config.stepTicks());
        yaml.set("counters.searches", searches.get());
        yaml.set("counters.candidates", candidates.get());
        yaml.set("counters.taller-trees", tallerTrees.get());
        yaml.set("counters.branches", branches.get());
        yaml.set("counters.canopies", canopies.get());
        yaml.set("counters.forest-floor", forestFloor.get());
        yaml.set("counters.rejected", rejected.get());
        yaml.set("recent-events", recentEventsSnapshot());
        yaml.set("map.legend.A", "latest action column");
        yaml.set("map.legend.T", "tree trunk/log column");
        yaml.set("map.legend.L", "leaf/canopy column");
        yaml.set("map.legend.U", "understory detail such as litter or small plants");
        yaml.set("map.legend.G", "natural ground");
        yaml.set("map.legend.?", "unloaded chunk");
        yaml.set("map.center", lastMapCenter);
        yaml.set("map.rows", lastMapRows);
        yaml.set("notes", "EvolutionDebug.yml traces slow tree/terrain enrichment decisions without copying external mod assets.");

        File file = new File(plugin.getDataFolder(), "EvolutionDebug.yml");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for EvolutionDebug.yml.");
            return;
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save SlowTrees EvolutionDebug.yml.", ex);
        }
    }

    private List<String> recentEventsSnapshot() {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents);
        }
    }

    private String format(Block block) {
        return block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private boolean isLog(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_STEM") || material == Material.MUSHROOM_STEM;
    }

    private boolean isLeaf(Material material) {
        return material.name().endsWith("_LEAVES");
    }
}
