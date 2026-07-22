package org.slowtrees.waves;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.slowtrees.core.SlowTreesPlugin;

final class WaveRenderer {
    private final SlowTreesPlugin plugin;
    private final WaveDiagnostics diagnostics;
    private final WaveVisualSmoother visualSmoother = new WaveVisualSmoother();
    private final Map<UUID, Map<WaveKey, VisualState>> activeByPlayer = new ConcurrentHashMap<>();
    private final BlockData[] waterLevels = new BlockData[8];
    private final BlockData airData;

    WaveRenderer(SlowTreesPlugin plugin, WaveDiagnostics diagnostics) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
        for (int level = 0; level < waterLevels.length; level++) {
            Levelled water = (Levelled) Bukkit.createBlockData(Material.WATER);
            water.setLevel(level);
            waterLevels[level] = water;
        }
        this.airData = Bukkit.createBlockData(Material.AIR);
    }

    RenderResult render(Player player, Map<WaveKey, Integer> next,
            Set<Long> uncertainColumns, boolean forceReassert,
            WaveConfig config, long tick) {
        UUID playerId = player.getUniqueId();
        int maxUpdates = config.maxBlockUpdatesPerPlayer();
        Map<WaveKey, VisualState> previous = activeByPlayer.getOrDefault(playerId, Collections.emptyMap());
        Map<WaveKey, VisualState> merged = new HashMap<>();
        Map<WaveKey, Integer> toAdd = new HashMap<>();
        int easedTransitions = 0;
        int deferredUpperLayers = 0;
        int entered = 0;

        for (Map.Entry<WaveKey, Integer> entry : next.entrySet()) {
            VisualState old = previous.get(entry.getKey());
            if (old == null && waitingForLowerLayer(entry.getKey(), next, previous)) {
                // ## Do not show the upper slice until its supporting lower water layer is full.
                continue;
            }
            VisualState state = updatedState(old, entry.getValue(), tick, config);
            boolean changed = old == null || state.level() != old.level();
            if (state.level() != clampLevel(entry.getValue())) {
                easedTransitions++;
            }
            if (!changed) {
                merged.put(entry.getKey(), state);
                continue;
            }
            if (toAdd.size() < maxUpdates) {
                state = state.sentAt(tick);
                merged.put(entry.getKey(), state);
                toAdd.put(entry.getKey(), state.level());
                entered += old == null ? 1 : 0;
            } else if (old != null) {
                // ## Keep the last client-visible state and retry this change next scan.
                // Never mark an unsent column as complete or drop the rest of a solid crest.
                merged.put(entry.getKey(), old);
            }
        }

        int held = 0;
        int uncertainHeld = 0;
        Set<WaveKey> toRemove = new HashSet<>();
        for (Map.Entry<WaveKey, VisualState> entry : previous.entrySet()) {
            if (next.containsKey(entry.getKey())) {
                continue;
            }
            VisualState old = entry.getValue();
            if (uncertainColumns.contains(entry.getKey().columnKey())) {
                // ## UNKNOWN means Folia could not confirm this column during this frame.
                // Preserve the client-visible state instead of interpreting a partial scan as land.
                merged.put(entry.getKey(), old.seenAt(tick));
                uncertainHeld++;
                continue;
            }
            long ageSinceSeen = tick - old.lastSeenTick();
            if (ageSinceSeen <= Math.max(config.stickyVisualTicks(), config.crestLifecycleTicks())) {
                VisualState faded = fadedState(old, tick, config);
                held++;
                if (faded.level() != old.level() && toAdd.size() < maxUpdates) {
                    faded = faded.sentAt(tick);
                    toAdd.put(entry.getKey(), faded.level());
                }
                merged.put(entry.getKey(), faded);
                continue;
            }
            toRemove.add(entry.getKey());
        }
        if (toRemove.size() + toAdd.size() > maxUpdates) {
            toRemove = new HashSet<>(toRemove.stream()
                    .limit(Math.max(0, maxUpdates - toAdd.size())).toList());
        }

        int reasserted = 0;
        int reassertBudget = Math.min(config.packetReassertBudget(),
                Math.max(0, maxUpdates - toAdd.size() - toRemove.size()));
        if (reassertBudget > 0) {
            for (Map.Entry<WaveKey, Integer> entry : next.entrySet()) {
                if (reasserted >= reassertBudget) {
                    break;
                }
                VisualState old = previous.get(entry.getKey());
                VisualState state = merged.get(entry.getKey());
                if (old == null || state == null || state.level() != old.level()
                        || !WaveFrameContinuity.reassertDue(
                                tick, old.lastSentTick(),
                                config.packetReassertIntervalTicks(), forceReassert)) {
                    continue;
                }
                VisualState refreshed = state.sentAt(tick);
                merged.put(entry.getKey(), refreshed);
                toAdd.put(entry.getKey(), refreshed.level());
                reasserted++;
            }
        }

        if (merged.isEmpty()) {
            activeByPlayer.remove(playerId);
        } else {
            activeByPlayer.put(playerId, Collections.unmodifiableMap(new HashMap<>(merged)));
        }

        sendWater(player, toAdd, config.packedBlockUpdates());
        restore(player, toRemove, config.packedBlockUpdates());
        diagnostics.recordCrests(toAdd.size());
        diagnostics.recordRestores(toRemove.size());
        diagnostics.recordVisualMemoryHeld(held);
        diagnostics.recordContinuity(uncertainHeld, reasserted);
        diagnostics.recordTemporalSmoothing(easedTransitions, deferredUpperLayers);
        return new RenderResult(toAdd.size() + toRemove.size(), entered,
                toRemove.size(), held, uncertainHeld, reasserted, merged.size(), next.size());
    }
    void clear(Player player, boolean restoreBlocks) {
        Map<WaveKey, VisualState> previous = activeByPlayer.remove(player.getUniqueId());
        if (previous == null || previous.isEmpty() || !restoreBlocks) {
            return;
        }
        restore(player, previous.keySet(), false);
    }

    void clearAll(Iterable<? extends Player> players, boolean restoreBlocks) {
        for (Player player : players) {
            clear(player, restoreBlocks);
        }
        activeByPlayer.clear();
    }

    private void sendWater(Player player, Map<WaveKey, Integer> locations, boolean packed) {
        if (locations.isEmpty()) {
            return;
        }
        World world = player.getWorld();
        if (!packed) {
            for (Map.Entry<WaveKey, Integer> entry : locations.entrySet()) {
                WaveKey key = entry.getKey();
                if (world.getUID().equals(key.worldId())) {
                    player.sendBlockChange(new Location(world, key.x(), key.y(), key.z()),
                            waterLevels[clampLevel(entry.getValue())]);
                }
            }
            return;
        }

        // ## Paper groups BlockState snapshots into section update packets. Reusing the
        // eight water data instances removes thousands of allocations from every frame.
        List<BlockState> states = new ArrayList<>(locations.size());
        for (Map.Entry<WaveKey, Integer> entry : locations.entrySet()) {
            WaveKey key = entry.getKey();
            if (!world.getUID().equals(key.worldId())) {
                continue;
            }
            BlockState state = world.getBlockAt(key.x(), key.y(), key.z()).getState();
            state.setBlockData(waterLevels[clampLevel(entry.getValue())]);
            states.add(state);
        }
        if (!states.isEmpty()) {
            player.sendBlockChanges(states);
            diagnostics.recordPackedUpdate(states.size());
        }
    }

    private void restore(Player player, Set<WaveKey> locations, boolean packed) {
        if (locations.isEmpty()) {
            return;
        }
        World world = player.getWorld();
        if (!packed) {
            for (WaveKey key : locations) {
                if (world.getUID().equals(key.worldId())) {
                    player.sendBlockChange(new Location(world, key.x(), key.y(), key.z()), restoreData(world, key));
                }
            }
            return;
        }

        List<BlockState> states = new ArrayList<>(locations.size());
        for (WaveKey key : locations) {
            if (!world.getUID().equals(key.worldId())) {
                continue;
            }
            int chunkX = key.x() >> 4;
            int chunkZ = key.z() >> 4;
            if (world.isChunkLoaded(chunkX, chunkZ) && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                states.add(world.getBlockAt(key.x(), key.y(), key.z()).getState());
            } else {
                plugin.pathDebug().failure(plugin, "waves", "restore-region-gate",
                        "sent AIR for " + key.x() + "," + key.y() + "," + key.z());
                player.sendBlockChange(new Location(world, key.x(), key.y(), key.z()), airData);
            }
        }
        if (!states.isEmpty()) {
            player.sendBlockChanges(states);
            diagnostics.recordPackedUpdate(states.size());
        }
    }

    private BlockData restoreData(World world, WaveKey key) {
        int chunkX = key.x() >> 4;
        int chunkZ = key.z() >> 4;
        if (world.isChunkLoaded(chunkX, chunkZ) && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            return block.getBlockData();
        }
        plugin.pathDebug().failure(plugin, "waves", "restore-region-gate",
                "sent AIR for " + key.x() + "," + key.y() + "," + key.z());
        return airData;
    }
    private VisualState updatedState(VisualState old, int desiredLevel, long tick, WaveConfig config) {
        int clamped = clampLevel(desiredLevel);
        if (old == null) {
            // ## The spatial crest profile owns height. Starting at the requested
            // level prevents an independent vertical birth animation from making
            // a horizontally traveling front look like water bobbing in place.
            return new VisualState(clamped, tick, tick, tick);
        }
        int smoothed = visualSmoother.approach(old.level(), clamped, config.crestSmoothing());
        return new VisualState(smoothed, tick, old.createdTick(), old.lastSentTick());
    }

    private VisualState fadedState(VisualState old, long tick, WaveConfig config) {
        long sinceSeen = Math.max(0L, tick - old.lastSeenTick());
        long fadeTicks = Math.max(config.stickyVisualTicks(), config.crestLifecycleTicks());
        double fade = Math.min(1.0D, (double) sinceSeen / Math.max(1L, fadeTicks));
        int target = (int) Math.round(old.level() + ((7 - old.level()) * fade));
        int level = visualSmoother.approach(old.level(), target, config.crestSmoothing());
        return new VisualState(level, old.lastSeenTick(), old.createdTick(), old.lastSentTick());
    }

    private boolean waitingForLowerLayer(WaveKey key, Map<WaveKey, Integer> next,
            Map<WaveKey, VisualState> previous) {
        WaveKey below = new WaveKey(key.worldId(), key.x(), key.y() - 1, key.z());
        if (!next.containsKey(below)) {
            return false;
        }
        VisualState lower = previous.get(below);
        return lower == null || lower.level() != 0;
    }

    private int clampLevel(int level) {
        return Math.max(0, Math.min(7, level));
    }
    record RenderResult(int changed, int entered, int restored, int held,
            int uncertainHeld, int reasserted, int active, int requested) {
    }

    record WaveKey(UUID worldId, int x, int y, int z) {
        long columnKey() {
            return (((long) x) << 32) ^ (z & 0xffffffffL);
        }
    }

    private record VisualState(int level, long lastSeenTick, long createdTick,
            long lastSentTick) {
        VisualState seenAt(long tick) {
            return new VisualState(level, tick, createdTick, lastSentTick);
        }

        VisualState sentAt(long tick) {
            return new VisualState(level, lastSeenTick, createdTick, tick);
        }
    }}
