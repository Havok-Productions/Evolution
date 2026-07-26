package org.evolution.features.ecology;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.evolution.coreparts.EvolutionPlugin;

final class EcologyEvolutionDiagnostics {
    private final AtomicLong searches = new AtomicLong();
    private final AtomicLong candidates = new AtomicLong();
    private final AtomicLong tallerTrees = new AtomicLong();
    private final AtomicLong branches = new AtomicLong();
    private final AtomicLong canopies = new AtomicLong();
    private final AtomicLong forestFloor = new AtomicLong();
    private final AtomicLong groundChanges = new AtomicLong();
    private final AtomicLong plantDetails = new AtomicLong();
    private final AtomicLong rareFeatures = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final Map<String, AtomicLong> rejectReasons = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicBoolean saveRunning = new AtomicBoolean();
    private final AtomicLong nextSaveMillis = new AtomicLong();
    private final Deque<String> recentEvents = new ArrayDeque<>();
    private final String sessionStartedAt = Instant.now().toString();
    private volatile List<String> lastMapRows = List.of();
    private volatile String lastMapCenter = "none";
    private volatile String lastDetailedReject = "none";

    void recordSearch() {
        searches.incrementAndGet();
    }

    void recordCandidate() {
        candidates.incrementAndGet();
    }

    void recordReject() {
        rejected.incrementAndGet();
        rejectReasons.computeIfAbsent("unknown", ignored -> new AtomicLong()).incrementAndGet();
    }

    void recordReject(EcologyEvolutionConfig config, String reason, String detail) {
        rejected.incrementAndGet();
        rejectReasons.computeIfAbsent(reason, ignored -> new AtomicLong()).incrementAndGet();
        lastDetailedReject = "reason=" + reason + " " + detail;
        recordEvent(config, "[GATE][ecology] blocked." + reason + " -> " + detail);
    }

    void recordRejectSampled(EcologyEvolutionConfig config, String reason, String detail) {
        rejected.incrementAndGet();
        long count = rejectReasons.computeIfAbsent(reason, ignored -> new AtomicLong()).incrementAndGet();
        lastDetailedReject = "reason=" + reason + " count=" + count + " " + detail;
        if (count <= 12 || count % 250 == 0) {
            recordEvent(config, "[GATE][ecology] blocked." + reason + " count=" + count + " -> " + detail);
        }
    }

    void recordAction(EvolutionPlugin plugin, EcologyEvolutionConfig config, String action, Block block, String detail) {
        switch (action) {
            case "height" -> tallerTrees.incrementAndGet();
            case "branch" -> branches.incrementAndGet();
            case "canopy" -> canopies.incrementAndGet();
            case "floor" -> forestFloor.incrementAndGet();
            case "ground" -> groundChanges.incrementAndGet();
            case "plant" -> plantDetails.incrementAndGet();
            case "rare" -> rareFeatures.incrementAndGet();
            default -> {
            }
        }
        recordEvent(config, "[ACTION][ecology] " + action + " -> " + format(block) + " " + detail);
        buildMap(block, config.debugMapRadius());
        saveSoon(plugin, config);
    }

    void recordState(EcologyEvolutionConfig config, String detail) {
        recordEvent(config, "[STATE][ecology] " + detail);
    }

    void recordTrace(EcologyEvolutionConfig config, String detail) {
        recordEvent(config, "[TRACE][ecology] " + detail);
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
        return tallerTrees.get() + branches.get() + canopies.get() + forestFloor.get() + groundChanges.get() + plantDetails.get() + rareFeatures.get();
    }

    void saveSoon(EvolutionPlugin plugin, EcologyEvolutionConfig config) {
        long now = System.currentTimeMillis();
        long next = nextSaveMillis.get();
        if (now < next || !nextSaveMillis.compareAndSet(next, now + 5000L)) {
            return;
        }
        saveAsync(plugin, config);
    }

    void saveAsync(EvolutionPlugin plugin, EcologyEvolutionConfig config) {
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

    void saveNow(EvolutionPlugin plugin, EcologyEvolutionConfig config) {
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
        if (below.getType() == Material.MUD || type == Material.SUGAR_CANE || type == Material.BLUE_ORCHID) {
            return "W";
        }
        if (below.getType() == Material.SAND || below.getType() == Material.TERRACOTTA || type == Material.DEAD_BUSH || type == Material.CACTUS) {
            return "D";
        }
        if (below.getType() == Material.MOSS_BLOCK || below.getType() == Material.MYCELIUM
                || type == Material.MOSS_CARPET || type == Material.BROWN_MUSHROOM || type == Material.RED_MUSHROOM) {
            return "M";
        }
        if (below.getType() == Material.GRASS_BLOCK || below.getType() == Material.DIRT || below.getType() == Material.PODZOL) {
            return "G";
        }
        return ".";
    }

    private void save(EvolutionPlugin plugin, EcologyEvolutionConfig config) {
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
        yaml.set("counters.ground-changes", groundChanges.get());
        yaml.set("counters.plant-details", plantDetails.get());
        yaml.set("counters.rare-features", rareFeatures.get());
        yaml.set("counters.rejected", rejected.get());
        yaml.set("reject-reasons", rejectReasonSnapshot());
        yaml.set("last-detailed-reject", lastDetailedReject);
        yaml.set("recent-events", recentEventsSnapshot());
        yaml.set("map.legend.A", "latest action column");
        yaml.set("map.legend.T", "tree trunk/log column");
        yaml.set("map.legend.L", "leaf/canopy column");
        yaml.set("map.legend.U", "understory detail such as litter or small plants");
        yaml.set("map.legend.W", "wetland/water-influenced surface");
        yaml.set("map.legend.D", "dry/sandy/coastal surface");
        yaml.set("map.legend.M", "moss/mycelium/mature ground");
        yaml.set("map.legend.G", "natural ground");
        yaml.set("map.legend.?", "unloaded chunk");
        yaml.set("map.center", lastMapCenter);
        yaml.set("map.rows", lastMapRows);
        yaml.set("ecology-paths.temperate", BiomeEcologyPath.TEMPERATE.progressionNote());
        yaml.set("ecology-paths.birch", BiomeEcologyPath.BIRCH.progressionNote());
        yaml.set("ecology-paths.dark-woodland", BiomeEcologyPath.DARK_WOODLAND.progressionNote());
        yaml.set("ecology-paths.cherry", BiomeEcologyPath.CHERRY.progressionNote());
        yaml.set("ecology-paths.tropical", BiomeEcologyPath.TROPICAL.progressionNote());
        yaml.set("ecology-paths.cold-conifer", BiomeEcologyPath.COLD_CONIFER.progressionNote());
        yaml.set("ecology-paths.wetland", BiomeEcologyPath.WETLAND.progressionNote());
        yaml.set("ecology-paths.dry", BiomeEcologyPath.DRY.progressionNote());
        yaml.set("ecology-paths.coastal", BiomeEcologyPath.COASTAL.progressionNote());
        yaml.set("ecology-paths.alpine", BiomeEcologyPath.ALPINE.progressionNote());
        yaml.set("ecology-paths.fungal", BiomeEcologyPath.FUNGAL.progressionNote());
        yaml.set("microhabitat-templates", EcologyMicrohabitatTemplate.keys());
        yaml.set("notes", "## EvolutionDebug.yml traces biome ecology paths, microhabitats, stages, explicit flora templates, and slow tree/terrain enrichment decisions without copying external mod assets.");

        File file = new File(plugin.getDataFolder(), "EvolutionDebug.yml");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder for EvolutionDebug.yml.");
            return;
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save Evolution EvolutionDebug.yml.", ex);
        }
    }

    private List<String> recentEventsSnapshot() {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents);
        }
    }

    private Map<String, Long> rejectReasonSnapshot() {
        Map<String, Long> snapshot = new HashMap<>();
        for (Map.Entry<String, AtomicLong> entry : rejectReasons.entrySet()) {
            snapshot.put(entry.getKey(), entry.getValue().get());
        }
        return snapshot;
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
