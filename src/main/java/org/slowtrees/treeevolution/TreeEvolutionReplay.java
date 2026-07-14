package org.slowtrees.treeevolution;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

final class TreeEvolutionReplay {
    private TreeEvolutionReplay() {
    }

    static Report build(TreeEvolutionConfig config, TreeDna dna, List<PlannedTreeBlock> orderedBlocks, World world) {
        int limit = Math.max(1, config.debugReplaySampleLimit());
        Map<TreeBlockRole, RoleStats> roleStats = new EnumMap<>(TreeBlockRole.class);
        Map<BlockProvenance, Integer> provenanceCounts = new EnumMap<>(BlockProvenance.class);
        List<Map<String, Object>> samples = new ArrayList<>();
        int scanned = 0;

        for (PlannedTreeBlock plannedBlock : orderedBlocks) {
            if (scanned >= limit) {
                break;
            }
            scanned++;
            LiveProbe probe = probe(config, dna, plannedBlock, world);
            roleStats.computeIfAbsent(plannedBlock.role(), ignored -> new RoleStats()).accept(probe.provenance());
            provenanceCounts.merge(probe.provenance(), 1, Integer::sum);
            if (samples.size() < 80 && probe.shouldSample()) {
                samples.add(sample(dna, plannedBlock, probe));
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tree", dna.key());
        summary.put("species", dna.species().id());
        summary.put("base", dna.baseX() + "," + dna.baseY() + "," + dna.baseZ());
        summary.put("stage", dna.maturityStage().name());
        summary.put("intent", dna.currentIntent().name());
        summary.put("age", dna.age());
        summary.put("sample", dna.profileSampleId());
        summary.put("source", dna.profileSampleSource());
        summary.put("planned-total", orderedBlocks.size());
        summary.put("scanned", scanned);
        summary.put("scan-limit", limit);
        summary.put("world", world == null ? "unavailable" : world.getName());
        summary.put("next-advice", advice(roleStats, provenanceCounts));
        summary.put("notes", "## Replay compares the current live world against the planned tree and names why each sampled position is placed, waiting, blocked, or unchecked.");

        return new Report(summary, roleProgress(roleStats), provenanceMap(provenanceCounts), samples);
    }

    private static LiveProbe probe(TreeEvolutionConfig config, TreeDna dna, PlannedTreeBlock plannedBlock, World world) {
        if (world == null) {
            return new LiveProbe(Material.AIR, BlockProvenance.classify(config, dna, plannedBlock, Material.AIR, false, false));
        }
        int chunkX = plannedBlock.x() >> 4;
        int chunkZ = plannedBlock.z() >> 4;
        boolean ready = world.isChunkLoaded(chunkX, chunkZ) && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0);
        Material live = ready ? world.getBlockAt(plannedBlock.x(), plannedBlock.y(), plannedBlock.z()).getType() : Material.AIR;
        return new LiveProbe(live, BlockProvenance.classify(config, dna, plannedBlock, live, true, ready));
    }

    private static Map<String, Object> sample(TreeDna dna, PlannedTreeBlock plannedBlock, LiveProbe probe) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("relative", (plannedBlock.x() - dna.baseX()) + "," + (plannedBlock.y() - dna.baseY()) + "," + (plannedBlock.z() - dna.baseZ()));
        row.put("absolute", plannedBlock.x() + "," + plannedBlock.y() + "," + plannedBlock.z());
        row.put("role", plannedBlock.role().name());
        row.put("planned", plannedBlock.material().name());
        row.put("live", probe.live().name());
        row.put("provenance", probe.provenance().name());
        row.put("note", probe.provenance().note());
        if (plannedBlock.hasBranchPath()) {
            row.put("branch", plannedBlock.branchId() + ":" + plannedBlock.branchStep());
            row.put("parent", plannedBlock.parentX() + "," + plannedBlock.parentY() + "," + plannedBlock.parentZ());
        }
        return row;
    }

    private static Map<String, Object> roleProgress(Map<TreeBlockRole, RoleStats> roleStats) {
        Map<String, Object> progress = new LinkedHashMap<>();
        for (TreeBlockRole role : TreeBlockRole.values()) {
            RoleStats stats = roleStats.get(role);
            if (stats == null || stats.total == 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("placed", stats.placed);
            row.put("waiting-placeable", stats.placeable);
            row.put("blocked", stats.blocked);
            row.put("unchecked", stats.unchecked);
            row.put("scanned", stats.total);
            row.put("progress-percent", percent(stats.placed, stats.total));
            progress.put(role.name(), row);
        }
        return progress;
    }

    private static Map<String, Integer> provenanceMap(Map<BlockProvenance, Integer> provenanceCounts) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (BlockProvenance provenance : BlockProvenance.values()) {
            int count = provenanceCounts.getOrDefault(provenance, 0);
            if (count > 0) {
                result.put(provenance.name(), count);
            }
        }
        return result;
    }

    private static String advice(Map<TreeBlockRole, RoleStats> roleStats, Map<BlockProvenance, Integer> provenanceCounts) {
        RoleStats trunk = roleStats.get(TreeBlockRole.TRUNK);
        RoleStats branch = roleStats.get(TreeBlockRole.BRANCH);
        RoleStats canopy = roleStats.get(TreeBlockRole.CANOPY);
        if (provenanceCounts.getOrDefault(BlockProvenance.UNCHECKED_CHUNK_OR_REGION, 0) > 0) {
            return "wait-for-loaded-owned-region";
        }
        if (trunk != null && trunk.percentPlaced() < 92.0D) {
            return "finish-trunk-before-wide-branching";
        }
        if (branch != null && branch.percentPlaced() < 30.0D) {
            return "start-supported-branches";
        }
        if (canopy != null && canopy.percentPlaced() < 55.0D) {
            return "canopy-catch-up-to-hide-exposed-wood";
        }
        if (provenanceCounts.getOrDefault(BlockProvenance.PLAYER_OR_FOREIGN_BLOCK, 0) > 0) {
            return "inspect-foreign-block-obstructions";
        }
        return "tree-progress-aligns-with-plan";
    }

    private static double percent(int part, int total) {
        return total <= 0 ? 0.0D : Math.round((part * 1000.0D) / total) / 10.0D;
    }

    record Report(
            Map<String, Object> summary,
            Map<String, Object> roleProgress,
            Map<String, Integer> provenanceCounts,
            List<Map<String, Object>> samples
    ) {
    }

    private record LiveProbe(Material live, BlockProvenance provenance) {
        boolean shouldSample() {
            return provenance != BlockProvenance.MATCHED_PLAN;
        }
    }

    private static final class RoleStats {
        private int placed;
        private int placeable;
        private int blocked;
        private int unchecked;
        private int total;

        private void accept(BlockProvenance provenance) {
            total++;
            if (provenance.isPlaced()) {
                placed++;
            } else if (provenance.isMissingButPlaceable()) {
                placeable++;
            } else if (provenance.isUnchecked()) {
                unchecked++;
            } else {
                blocked++;
            }
        }

        private double percentPlaced() {
            return percent(placed, total);
        }
    }
}
