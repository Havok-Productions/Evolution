package org.evolution.features.treeevolution;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.evolution.coreparts.EvolutionPlugin;

/**
 * ## Applies DNA retention and migration policy.
 *
 * <p>The supplied invalidator is the single bridge back to runtime caches. Persistence and
 * normalization remain centralized while the coordinator decides which live construction caches
 * a changed tree must release.
 */
final class TreeDnaLifecycleService {
    private final EvolutionPlugin plugin;
    private final TreeDnaRepository repository;
    private final TreeDnaNormalizer normalizer;
    private final TreeEvolutionDiagnostics diagnostics;
    private final ConcurrentMap<String, TreeDna> treeDna;
    private final Consumer<String> invalidateRuntimeState;
    private final AtomicLong nextCleanupMillis = new AtomicLong();

    TreeDnaLifecycleService(
            EvolutionPlugin plugin,
            TreeDnaRepository repository,
            TreeDnaNormalizer normalizer,
            TreeEvolutionDiagnostics diagnostics,
            Consumer<String> invalidateRuntimeState
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.normalizer = normalizer;
        this.diagnostics = diagnostics;
        this.treeDna = repository.records();
        this.invalidateRuntimeState = invalidateRuntimeState;
    }

    void cleanup(TreeEvolutionConfig config, String reason) {
        if (!config.dnaCleanupEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        long next = nextCleanupMillis.get();
        if (now < next
                || !nextCleanupMillis.compareAndSet(
                        next, now + config.dnaCleanupIntervalMillis())) {
            return;
        }

        int removed = 0;
        for (TreeDna dna : treeDna.values()) {
            if (!shouldRemove(dna, config, now)) {
                continue;
            }
            if (treeDna.remove(dna.key(), dna)) {
                invalidateRuntimeState.accept(dna.key());
                removed++;
                plugin.pathDebug().traceSampled(
                        plugin,
                        "tree-evolution",
                        "cleanup.remove-dna",
                        dna.key() + " species=" + dna.species() + " reason=" + reason);
            }
        }
        if (removed > 0) {
            repository.markDirty("cleanup removed=" + removed);
            repository.save(config);
        }
        plugin.pathDebug().traceSampled(
                plugin,
                "tree-evolution",
                "cleanup.pass",
                "reason=" + reason + " removed=" + removed + " remaining=" + treeDna.size());
        plugin.resourceReporter().count(
                plugin,
                "tree-evolution",
                "cleanup.tree-dna",
                treeDna.size(),
                removed,
                "reason=" + reason);
    }

    void maybeCleanup(TreeEvolutionConfig config, String reason) {
        if (!config.dnaCleanupEnabled()
                || System.currentTimeMillis() < nextCleanupMillis.get()) {
            return;
        }
        cleanup(config, reason);
    }

    void normalize(TreeEvolutionConfig config, String reason) {
        int normalized = 0;
        for (TreeDna dna : treeDna.values()) {
            TreeDnaNormalizer.NormalizedDna result =
                    normalizer.normalize(dna, config.maximumStage());
            if (!result.changed() || !treeDna.replace(dna.key(), dna, result.dna())) {
                continue;
            }
            normalized++;
            invalidateRuntimeState.accept(dna.key());
            diagnostics.recordDnaNormalized(
                    config, dna, result.dna(), result.summary());
            plugin.pathDebug().traceSampled(
                    plugin,
                    "tree-evolution",
                    "state.dna-stage-ceiling",
                    result.dna().key()
                            + " reason=" + reason
                            + " " + result.summary());
        }
        if (normalized > 0) {
            repository.markDirty("stage-ceiling normalized=" + normalized);
            repository.save(config);
        }
    }

    private boolean shouldRemove(
            TreeDna dna,
            TreeEvolutionConfig config,
            long now
    ) {
        long lastTouched = dna.lastGrowthMillis() <= 0L ? 0L : dna.lastGrowthMillis();
        if (now - lastTouched < config.dnaCleanupMissingBaseMillis()) {
            return false;
        }

        World world = Bukkit.getWorld(dna.worldId());
        if (world == null) {
            return true;
        }
        int chunkX = dna.baseX() >> 4;
        int chunkZ = dna.baseZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)
                || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            return false;
        }
        if (!dna.stumpPresent()) {
            return true;
        }
        return world.getBlockAt(dna.baseX(), dna.baseY(), dna.baseZ()).getType()
                != dna.species().logMaterial();
    }
}
