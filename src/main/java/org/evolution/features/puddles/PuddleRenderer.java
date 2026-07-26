package org.evolution.features.puddles;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.evolution.coreparts.EvolutionPlugin;

final class PuddleRenderer {
    private final EvolutionPlugin plugin;
    private final PuddleDiagnostics diagnostics;
    private final Map<UUID, Map<PuddleKey, Integer>> activeByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextReassertMillis = new ConcurrentHashMap<>();

    PuddleRenderer(EvolutionPlugin plugin, PuddleDiagnostics diagnostics) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
    }

    void render(Player player, Set<Puddle> puddles, long reassertIntervalMillis) {
        World world = player.getWorld();
        UUID playerId = player.getUniqueId();
        Map<PuddleKey, Integer> next = new HashMap<>();
        for (Puddle puddle : puddles) {
            if (world.getUID().equals(puddle.worldId())) {
                next.put(new PuddleKey(puddle.worldId(), puddle.x(), puddle.y(), puddle.z()), puddle.depth());
            }
        }

        long now = System.currentTimeMillis();
        boolean reassert = !next.isEmpty()
                && now >= nextReassertMillis.getOrDefault(playerId, 0L);
        Map<PuddleKey, Integer> previous = activeByPlayer.getOrDefault(playerId, Collections.emptyMap());
        Map<PuddleKey, Integer> toAdd = new HashMap<>();
        for (Map.Entry<PuddleKey, Integer> entry : next.entrySet()) {
            Integer previousDepth = previous.get(entry.getKey());
            if (!entry.getValue().equals(previousDepth)) {
                toAdd.put(entry.getKey(), entry.getValue());
            }
        }
        if (reassert) {
            toAdd.putAll(next);
            nextReassertMillis.put(playerId,
                    now + Math.max(500L, reassertIntervalMillis));
            plugin.pathDebug().traceSampled(plugin, "puddles",
                    "render.reassert",
                    "world=" + world.getName() + " blocks=" + next.size()
                            + " ## visible packet puddles refreshed after client/chunk updates");
        }

        Set<PuddleKey> toRemove = new HashSet<>(previous.keySet());
        toRemove.removeAll(next.keySet());
        if (next.isEmpty()) {
            activeByPlayer.remove(playerId);
            nextReassertMillis.remove(playerId);
        } else {
            activeByPlayer.put(playerId, Collections.unmodifiableMap(next));
        }

        sendWater(player, toAdd);
        restore(player, toRemove);
        diagnostics.recordRendered(toAdd.size());
        if (reassert) {
            diagnostics.recordReasserted(toAdd.size());
        }
        diagnostics.recordRestored(toRemove.size());
    }

    void clear(Player player, boolean restoreBlocks) {
        Map<PuddleKey, Integer> previous = activeByPlayer.remove(player.getUniqueId());
        nextReassertMillis.remove(player.getUniqueId());
        if (previous == null || previous.isEmpty() || !restoreBlocks) {
            return;
        }
        restore(player, previous.keySet());
    }

    void clearAll(Iterable<? extends Player> players, boolean restoreBlocks) {
        for (Player player : players) {
            clear(player, restoreBlocks);
        }
        activeByPlayer.clear();
        nextReassertMillis.clear();
    }

    private void sendWater(Player player, Map<PuddleKey, Integer> locations) {
        if (locations.isEmpty()) {
            return;
        }
        World world = player.getWorld();
        for (Map.Entry<PuddleKey, Integer> entry : locations.entrySet()) {
            PuddleKey key = entry.getKey();
            if (!world.getUID().equals(key.worldId())) {
                continue;
            }
            Levelled water = (Levelled) Bukkit.createBlockData(Material.WATER);
            water.setLevel(toWaterLevel(entry.getValue()));
            player.sendBlockChange(new Location(world, key.x(), key.y(), key.z()), water);
        }
    }

    private int toWaterLevel(int depth) {
        if (depth >= 3) {
            return 5;
        }
        if (depth == 2) {
            return 6;
        }
        return 7;
    }

    private void restore(Player player, Set<PuddleKey> locations) {
        if (locations.isEmpty()) {
            return;
        }
        World world = player.getWorld();
        for (PuddleKey key : locations) {
            if (!world.getUID().equals(key.worldId())) {
                continue;
            }
            Location location = new Location(world, key.x(), key.y(), key.z());
            player.sendBlockChange(location, restoreData(world, key));
        }
    }

    private BlockData restoreData(World world, PuddleKey key) {
        int chunkX = key.x() >> 4;
        int chunkZ = key.z() >> 4;
        if (world.isChunkLoaded(chunkX, chunkZ) && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            return block.getBlockData();
        }
        plugin.pathDebug().failure(plugin, "puddles", "restore-region-gate", "sent AIR for " + key.x() + "," + key.y() + "," + key.z());
        return Bukkit.createBlockData(Material.AIR);
    }

    private record PuddleKey(UUID worldId, int x, int y, int z) {
    }
}