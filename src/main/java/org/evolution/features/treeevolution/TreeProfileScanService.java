package org.evolution.features.treeevolution;

import java.io.File;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.evolution.coreparts.EvolutionPlugin;

/**
 * ## Owns optional structure analysis and the derived profile-sample catalog.
 *
 * <p>Scanned files are analysis input only. The service stores statistical profile suggestions;
 * it does not copy source layouts into live tree plans.
 */
final class TreeProfileScanService {
    private final EvolutionPlugin plugin;
    private final StructurePatternScanner scanner = new StructurePatternScanner();
    private final TreeProfileSampleStore sampleStore = new TreeProfileSampleStore();
    private volatile Map<TreeSpecies, List<TreeProfileSample>> samples = Map.of();

    TreeProfileScanService(EvolutionPlugin plugin) {
        this.plugin = plugin;
    }

    void initialize(TreeEvolutionConfig config) {
        ensureFolder();
        load();
        scheduleAutoScan(config);
    }

    StructureScanResult scanNow() {
        StructureScanResult result = scanner.scanAll(plugin);
        refresh(result);
        return result;
    }

    List<TreeProfileSample> samples(TreeSpecies species) {
        return samples.getOrDefault(species, List.of());
    }

    int sampleCount() {
        return samples.values().stream().mapToInt(List::size).sum();
    }

    private void load() {
        samples = sampleStore.load(plugin);
        plugin.pathDebug().trace(
                plugin,
                "tree-evolution",
                "persistence.profile-samples-load",
                "samples=" + sampleCount() + " species=" + samples.keySet().size());
    }

    private void refresh(StructureScanResult result) {
        samples = sampleStore.saveFromScan(plugin, result);
        plugin.pathDebug().trace(
                plugin,
                "tree-evolution",
                "persistence.profile-samples-refresh",
                "samples=" + sampleCount() + " species=" + samples.keySet().size());
    }

    private void ensureFolder() {
        File folder = scanner.scanFolder(plugin);
        if (!folder.exists() && folder.mkdirs()) {
            plugin.pathDebug().trace(
                    plugin,
                    "tree-evolution",
                    "persistence.debug-folder-create",
                    "structure-scan");
        }
    }

    private void scheduleAutoScan(TreeEvolutionConfig config) {
        if (!config.debugEnabled() || !config.autoScanOnStartup()) {
            plugin.pathDebug().trace(
                    plugin,
                    "tree-evolution",
                    "persistence.debug-auto-scan-skip",
                    "disabled");
            return;
        }

        File folder = scanner.scanFolder(plugin);
        File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            return lower.endsWith(".nbt")
                    || lower.endsWith(".schem")
                    || lower.endsWith(".schematic")
                    || lower.endsWith(".zip")
                    || lower.endsWith(".jar");
        });
        if (files == null || files.length == 0) {
            plugin.pathDebug().trace(
                    plugin,
                    "tree-evolution",
                    "persistence.debug-auto-scan-empty",
                    "structure-scan");
            return;
        }

        plugin.pathDebug().trace(
                plugin,
                "tree-evolution",
                "scheduler.async-delay",
                "auto structure scan files=" + files.length);
        Bukkit.getAsyncScheduler().runDelayed(
                plugin,
                task -> {
                    StructureScanResult result = scanNow();
                    plugin.pathDebug().trace(
                            plugin,
                            "tree-evolution",
                            "persistence.debug-auto-scan-done",
                            "structures=" + result.structures().size()
                                    + " profile-samples=" + sampleCount()
                                    + " files=" + files.length);
                },
                40L,
                java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
