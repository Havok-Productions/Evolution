package org.evolution.coreparts;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * ## Shared provenance for tree wood placed directly by players.
 *
 * <p>Both plant regrowth and tree evolution consult this registry before they
 * claim a structure. Removal is delayed by one tick so every MONITOR listener
 * observes the same receipt for the original break event.</p>
 */
public final class PlayerPlacedTreeBlockRegistry implements Listener {
    private static final long SAVE_DELAY_MILLIS = 10_000L;

    private final EvolutionPlugin plugin;
    private final ConcurrentMap<String, Long> receipts =
            new ConcurrentHashMap<>();
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private final AtomicLong dirtyVersion = new AtomicLong();
    private final AtomicLong savedVersion = new AtomicLong();
    private final Object saveLock = new Object();

    public PlayerPlacedTreeBlockRegistry(EvolutionPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = file();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("blocks");
        if (section == null) {
            return;
        }
        for (String encoded : section.getKeys(false)) {
            String key = section.getString(encoded + ".key", "");
            if (!key.isBlank()) {
                receipts.put(key, Math.max(0L,
                        section.getLong(encoded + ".placed-millis")));
            }
        }
        plugin.pathDebug().trace(
                plugin, "core", "provenance.player-tree-wood-load",
                "receipts=" + receipts.size());
    }

    public boolean contains(Block block) {
        return block != null && receipts.containsKey(key(block));
    }

    public boolean containsKey(String blockKey) {
        return blockKey != null && receipts.containsKey(blockKey);
    }

    public int size() {
        return receipts.size();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!isTreeWood(block.getType())) {
            return;
        }
        long receipt = System.currentTimeMillis();
        receipts.put(key(block), receipt);
        markDirty();
        plugin.pathDebug().traceSampled(
                plugin, "core", "provenance.player-tree-wood-place",
                format(block) + " material=" + block.getType()
                        + " ## player wood cannot seed either tree constructor");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String blockKey = key(block);
        Long receipt = receipts.get(blockKey);
        if (receipt == null) {
            return;
        }
        // ## Feature listeners run at MONITOR too. Keep this receipt alive for
        // their complete event pass, then remove only the exact old receipt so
        // a rapid replacement at the same coordinate cannot be forgotten.
        Bukkit.getRegionScheduler().runDelayed(
                plugin,
                block.getLocation(),
                task -> {
                    if (receipts.remove(blockKey, receipt)) {
                        markDirty();
                    }
                },
                1L);
    }

    public void saveNow(String reason) {
        try (ResourceReporter.ReportSample sample = plugin.resourceReporter()
                .begin("core", "persistence.player-tree-wood")) {
            synchronized (saveLock) {
                long version = dirtyVersion.get();
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.set("notes", "## Player-placed tree wood provenance shared by regrowth and tree evolution.");
                var section = yaml.createSection("blocks");
                int index = 0;
                for (Map.Entry<String, Long> entry : receipts.entrySet()) {
                    String path = Integer.toString(index++);
                    section.set(path + ".key", entry.getKey());
                    section.set(path + ".placed-millis", entry.getValue());
                }
                File file = file();
                File parent = file.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    sample.detail("folder-create-failed reason=" + reason);
                    return;
                }
                try {
                    yaml.save(file);
                    savedVersion.set(version);
                    sample.workUnits(receipts.size()).changedUnits(1)
                            .detail("receipts=" + receipts.size()
                                    + " reason=" + reason);
                } catch (IOException failure) {
                    sample.detail("failed reason=" + reason);
                    plugin.getLogger().log(
                            Level.WARNING,
                            "Could not save player tree-block provenance.",
                            failure);
                }
            }
        }
    }

    private void markDirty() {
        dirtyVersion.incrementAndGet();
        scheduleSave();
    }

    private void scheduleSave() {
        if (!plugin.isEnabled()
                || !saveScheduled.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getAsyncScheduler().runDelayed(
                plugin,
                task -> {
                    try {
                        saveNow("debounced");
                    } finally {
                        saveScheduled.set(false);
                        if (dirtyVersion.get() > savedVersion.get()) {
                            scheduleSave();
                        }
                    }
                },
                SAVE_DELAY_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    private File file() {
        return new File(
                plugin.getDataFolder(), "player-placed-tree-blocks.yml");
    }

    private static boolean isTreeWood(Material material) {
        String name = material.name();
        return name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE")
                || material == Material.MUSHROOM_STEM;
    }

    private static String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":"
                + block.getY() + ":" + block.getZ();
    }

    private static String format(Block block) {
        return block.getWorld().getName() + " " + block.getX() + ","
                + block.getY() + "," + block.getZ();
    }
}
