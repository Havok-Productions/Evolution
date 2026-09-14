package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.evolution.coreparts.EvolutionPlugin;
import org.evolution.coreparts.ResourceReporter.ReportSample;

/**
 * ## Audits Evolution-owned leaves against their complete live wood graph.
 *
 * <p>Each Folia-owned chunk first contributes a read-only snapshot. The final
 * graph decision is made only after all snapshots arrive, so a valid indirect
 * leaf chain crossing a chunk border is not split into false fragments. Only
 * disconnected components are then released to vanilla decay.</p>
 */
final class TreeLeafDecayService {
    private final EvolutionPlugin plugin;

    TreeLeafDecayService(EvolutionPlugin plugin) {
        this.plugin = plugin;
    }

    void auditConnectivity(TreeCandidate candidate, TreeDna dna) {
        World world = candidate.world();
        Set<String> ownedKeys = new HashSet<>(dna.originalShapeLogs());
        ownedKeys.addAll(dna.originalShapeLeaves());
        ownedKeys.addAll(dna.evolvedShapeLogs());
        ownedKeys.addAll(dna.evolvedShapeLeaves());
        if (candidate.ownershipComplete()) {
            // ## A newly discovered tree may not yet have evolved receipts;
            // its completed ownership scan is the authoritative fallback.
            ownedKeys.addAll(candidate.naturalKeys());
        }

        Map<ChunkKey, List<TreeCoordinate>> byChunk = new HashMap<>();
        for (String key : ownedKeys) {
            TreeCoordinate coordinate = decode(world, key);
            if (coordinate == null) {
                continue;
            }
            byChunk.computeIfAbsent(
                    new ChunkKey(coordinate.x() >> 4, coordinate.z() >> 4),
                    ignored -> new ArrayList<>()).add(coordinate);
        }

        List<Map.Entry<ChunkKey, List<TreeCoordinate>>> loadedBatches =
                byChunk.entrySet().stream()
                        .filter(entry -> world.isChunkLoaded(
                                entry.getKey().x(), entry.getKey().z()))
                        .toList();
        if (loadedBatches.isEmpty()) {
            return;
        }

        ConnectivityAudit audit = new ConnectivityAudit(
                world, dna, loadedBatches.size());
        for (Map.Entry<ChunkKey, List<TreeCoordinate>> entry : loadedBatches) {
            List<TreeCoordinate> coordinates = List.copyOf(entry.getValue());
            TreeCoordinate anchor = coordinates.getFirst();
            Bukkit.getRegionScheduler().runDelayed(
                    plugin,
                    new Location(world, anchor.x(), anchor.y(), anchor.z()),
                    task -> snapshotBatch(audit, coordinates),
                    1L);
        }
        plugin.pathDebug().trace(
                plugin, "tree-evolution", "decay.connectivity-audit-scheduled",
                "tree=" + dna.key() + " owned-blocks=" + ownedKeys.size()
                        + " chunk-batches=" + loadedBatches.size()
                        + " ## all snapshots combine before leaf components"
                        + " are classified");
    }

    private void snapshotBatch(
            ConnectivityAudit audit,
            List<TreeCoordinate> coordinates
    ) {
        try (ReportSample sample = plugin.resourceReporter().begin(
                "tree-evolution", "decay.snapshot-connectivity")) {
            int inspected = 0;
            for (TreeCoordinate coordinate : coordinates) {
                inspected++;
                int chunkX = coordinate.x() >> 4;
                int chunkZ = coordinate.z() >> 4;
                if (!audit.world().isChunkLoaded(chunkX, chunkZ)
                        || !Bukkit.isOwnedByCurrentRegion(
                                audit.world(), chunkX, chunkZ, 0)) {
                    continue;
                }
                Block block = audit.world().getBlockAt(
                        coordinate.x(), coordinate.y(), coordinate.z());
                if (!plugin.canEvolveAt(
                        block.getLocation(), "tree-evolution")) {
                    continue;
                }
                String key = localKey(coordinate);
                if (block.getBlockData() instanceof Leaves) {
                    audit.liveLeaves().put(key, coordinate);
                } else if (isWood(block.getType())) {
                    audit.liveWood().add(key);
                }
            }
            sample.workUnits(inspected).detail(
                    "tree=" + audit.dna().key() + " inspected=" + inspected);
        } finally {
            if (audit.remainingSnapshots().decrementAndGet() == 0) {
                finishSnapshotAudit(audit);
            }
        }
    }

    private void finishSnapshotAudit(ConnectivityAudit audit) {
        Set<String> connected = TreeLeafConnectivityPolicy.connectedLeaves(
                audit.liveLeaves().keySet(), audit.liveWood());
        Map<ChunkKey, List<TreeCoordinate>> disconnectedByChunk =
                new HashMap<>();
        for (Map.Entry<String, TreeCoordinate> entry
                : audit.liveLeaves().entrySet()) {
            if (connected.contains(entry.getKey())) {
                continue;
            }
            TreeCoordinate coordinate = entry.getValue();
            disconnectedByChunk.computeIfAbsent(
                    new ChunkKey(coordinate.x() >> 4, coordinate.z() >> 4),
                    ignored -> new ArrayList<>()).add(coordinate);
        }

        if (disconnectedByChunk.isEmpty()) {
            traceAuditResult(audit, connected.size(), 0);
            return;
        }
        AtomicInteger remaining = new AtomicInteger(disconnectedByChunk.size());
        AtomicInteger released = new AtomicInteger();
        for (List<TreeCoordinate> coordinates : disconnectedByChunk.values()) {
            List<TreeCoordinate> batch = List.copyOf(coordinates);
            TreeCoordinate anchor = batch.getFirst();
            Bukkit.getRegionScheduler().run(
                    plugin,
                    new Location(audit.world(),
                            anchor.x(), anchor.y(), anchor.z()),
                    task -> {
                        released.addAndGet(releaseBatch(audit.world(), batch));
                        if (remaining.decrementAndGet() == 0) {
                            traceAuditResult(
                                    audit, connected.size(), released.get());
                        }
                    });
        }
    }

    private int releaseBatch(World world, List<TreeCoordinate> coordinates) {
        int released = 0;
        for (TreeCoordinate coordinate : coordinates) {
            int chunkX = coordinate.x() >> 4;
            int chunkZ = coordinate.z() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)
                    || !Bukkit.isOwnedByCurrentRegion(
                            world, chunkX, chunkZ, 0)) {
                continue;
            }
            Block block = world.getBlockAt(
                    coordinate.x(), coordinate.y(), coordinate.z());
            if (!plugin.canEvolveAt(block.getLocation(), "tree-evolution")
                    || !(block.getBlockData() instanceof Leaves leaves)) {
                continue;
            }
            // ## Recheck direct support in case the constructor placed new
            // wood between snapshot and application.
            if (hasDirectWoodNeighbor(block)) {
                continue;
            }
            leaves.setPersistent(false);
            leaves.setDistance(leaves.getMaximumDistance());
            block.setBlockData(leaves, true);
            released++;
        }
        return released;
    }

    private void traceAuditResult(
            ConnectivityAudit audit,
            int connected,
            int released
    ) {
        plugin.pathDebug().traceSampled(
                plugin, "tree-evolution", "decay.connectivity-audited",
                "tree=" + audit.dna().key() + " connected=" + connected
                        + " released=" + released + "/"
                        + audit.liveLeaves().size()
                        + " ## direct and indirect leaf chains to live wood"
                        + " remain supported");
    }

    private boolean hasDirectWoodNeighbor(Block block) {
        for (int[] offset : TreeLeafConnectivityPolicy.NEIGHBOR_OFFSETS) {
            if (isWood(block.getRelative(
                    offset[0], offset[1], offset[2]).getType())) {
                return true;
            }
        }
        return false;
    }

    private boolean isWood(Material material) {
        String name = material.name();
        return name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE")
                || material == Material.MUSHROOM_STEM;
    }

    private TreeCoordinate decode(World world, String key) {
        String[] parts = key.split(":");
        if (parts.length != 4
                || !world.getUID().toString().equals(parts[0])) {
            return null;
        }
        try {
            return new TreeCoordinate(
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String localKey(TreeCoordinate coordinate) {
        return localKey(coordinate.x(), coordinate.y(), coordinate.z());
    }

    private static String localKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private record ChunkKey(int x, int z) {
    }

    private record TreeCoordinate(int x, int y, int z) {
    }

    private record ConnectivityAudit(
            World world,
            TreeDna dna,
            AtomicInteger remainingSnapshots,
            Map<String, TreeCoordinate> liveLeaves,
            Set<String> liveWood
    ) {
        private ConnectivityAudit(World world, TreeDna dna, int snapshots) {
            this(world, dna, new AtomicInteger(snapshots),
                    new ConcurrentHashMap<>(), ConcurrentHashMap.newKeySet());
        }
    }
}
