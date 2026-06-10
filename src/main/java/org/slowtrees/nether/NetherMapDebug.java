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
    private final AtomicLong replacements = new AtomicLong();
    private final AtomicLong symbolOne = new AtomicLong();
    private final AtomicLong symbolTwo = new AtomicLong();
    private final AtomicLong symbolThree = new AtomicLong();
    private final AtomicLong symbolFour = new AtomicLong();
    private final AtomicLong symbolFive = new AtomicLong();
    private final AtomicLong symbolSix = new AtomicLong();
    private final AtomicBoolean saveRunning = new AtomicBoolean();
    private final AtomicLong nextSaveMillis = new AtomicLong();
    private final Deque<String> recentEvents = new ArrayDeque<>();

    void recordReplacement(
            SlowTreesPlugin plugin,
            NetherCorruptionConfig config,
            PortalSource source,
            Block block,
            Material original,
            NetherMimicResult result,
            NetherTerrainMimic terrainMimic
    ) {
        replacements.incrementAndGet();
        incrementSymbol(result.mapSymbol());
        recordEvent(config, "replace: " + format(block) + " "
                + original + " -> " + result.material()
                + " symbol=" + result.mapSymbol()
                + " style=" + result.style().displayName());

        sourceMaps.put(source.key(), buildSnapshot(source, block.getWorld(), config, terrainMimic));
        saveSoon(plugin);
    }

    void saveSoon(SlowTreesPlugin plugin) {
        long now = System.currentTimeMillis();
        long next = nextSaveMillis.get();
        if (now < next || !nextSaveMillis.compareAndSet(next, now + 10000L)) {
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

    private SourceMapSnapshot buildSnapshot(PortalSource source, World world, NetherCorruptionConfig config, NetherTerrainMimic terrainMimic) {
        int radius = config.debugMapRadius();
        int startY = Math.min(world.getMaxHeight() - 1, source.centerY() + config.verticalRadius());
        int endY = Math.max(world.getMinHeight(), source.centerY() - config.verticalRadius());
        List<String> rows = new ArrayList<>();

        for (int z = source.centerZ() - radius; z <= source.centerZ() + radius; z++) {
            StringBuilder row = new StringBuilder(radius * 2 + 1);
            for (int x = source.centerX() - radius; x <= source.centerX() + radius; x++) {
                row.append(symbolAt(world, x, z, startY, endY, source, terrainMimic));
            }
            rows.add(row.toString());
        }

        return new SourceMapSnapshot(format(source, world), radius, startY + ".." + endY, rows);
    }

    private char symbolAt(World world, int x, int z, int startY, int endY, PortalSource source, NetherTerrainMimic terrainMimic) {
        if (x == source.centerX() && z == source.centerZ()) {
            return 'P';
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return '?';
        }

        for (int y = startY; y >= endY; y--) {
            Material material = world.getBlockAt(x, y, z).getType();
            if (material == Material.NETHER_PORTAL) {
                return 'P';
            }

            int symbol = terrainMimic.mapSymbol(material);
            if (symbol > 0) {
                return Character.forDigit(symbol, 10);
            }
        }

        return '.';
    }

    private void save(SlowTreesPlugin plugin) {
        YamlConfiguration yaml = new YamlConfiguration();
        writeLegend(yaml.createSection("legend"));
        yaml.set("counters.replacements", replacements.get());
        yaml.set("counters.by-symbol.1", symbolOne.get());
        yaml.set("counters.by-symbol.2", symbolTwo.get());
        yaml.set("counters.by-symbol.3", symbolThree.get());
        yaml.set("counters.by-symbol.4", symbolFour.get());
        yaml.set("counters.by-symbol.5", symbolFive.get());
        yaml.set("counters.by-symbol.6", symbolSix.get());
        yaml.set("recent-events", recentEventsSnapshot());

        ConfigurationSection sources = yaml.createSection("sources");
        int index = 0;
        for (Map.Entry<String, SourceMapSnapshot> entry : sourceMaps.entrySet()) {
            SourceMapSnapshot snapshot = entry.getValue();
            ConfigurationSection section = sources.createSection(Integer.toString(index++));
            section.set("source", snapshot.source());
            section.set("radius", snapshot.radius());
            section.set("y-range", snapshot.yRange());
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
            plugin.getLogger().log(Level.WARNING, "Could not save SlowTrees MapDebug.yml.", ex);
        }
    }

    private void writeLegend(ConfigurationSection section) {
        section.set("1", "NETHERRACK");
        section.set("2", "CRIMSON_NYLIUM");
        section.set("3", "WARPED_NYLIUM");
        section.set("4", "SOUL_SOIL or SOUL_SAND");
        section.set("5", "BLACKSTONE or BASALT");
        section.set("6", "LAVA");
        section.set(".", "unchanged or no Nether mimic block in scanned y-range");
        section.set("P", "portal/source center");
        section.set("?", "chunk not loaded while map was built");
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

    private void incrementSymbol(int symbol) {
        switch (symbol) {
            case 1 -> symbolOne.incrementAndGet();
            case 2 -> symbolTwo.incrementAndGet();
            case 3 -> symbolThree.incrementAndGet();
            case 4 -> symbolFour.incrementAndGet();
            case 5 -> symbolFive.incrementAndGet();
            case 6 -> symbolSix.incrementAndGet();
            default -> {
            }
        }
    }

    private String format(Block block) {
        return block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private String format(PortalSource source, World world) {
        return world.getName() + " " + source.centerX() + "," + source.centerY() + "," + source.centerZ();
    }

    private record SourceMapSnapshot(String source, int radius, String yRange, List<String> rows) {
    }
}
