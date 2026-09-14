package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Cell;

/**
 * ## Minecraft-like environment facts surrounding a constructor replay.
 *
 * <p>The constructor still owns every tree decision. This layer supplies the
 * facts that a live Bukkit world normally supplies: terrain occupancy, biome,
 * weather, light/sky access, loaded chunks, Folia ownership, nearby players,
 * and protected volumes.
 */
final class TreeSimulatedMinecraftEnvironment {
    enum BiomeProfile {
        PLAINS("minecraft:plains"),
        FOREST("minecraft:forest"),
        BIRCH_FOREST("minecraft:birch_forest"),
        TAIGA("minecraft:taiga"),
        JUNGLE("minecraft:jungle"),
        SAVANNA("minecraft:savanna"),
        MANGROVE_SWAMP("minecraft:mangrove_swamp"),
        CHERRY_GROVE("minecraft:cherry_grove");

        private final String id;

        BiomeProfile(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }
    }

    enum Weather {
        CLEAR,
        RAIN,
        THUNDER
    }

    enum Gate {
        ALLOWED,
        CHUNK_UNLOADED,
        REGION_NOT_OWNED,
        NO_NEARBY_PLAYER,
        PROTECTED_REGION,
        PLAYER_BLOCK,
        IMPORTANT_BLOCK,
        CAVE_OR_LOW_LIGHT,
        FLUID
    }

    record TerrainCell(
            Material material,
            boolean natural,
            boolean replaceable,
            boolean playerPlaced
    ) {
    }

    record GateDecision(boolean allowed, Gate gate, String detail) {
        static GateDecision allow() {
            return new GateDecision(true, Gate.ALLOWED, "allowed");
        }

        static GateDecision block(Gate gate, String detail) {
            return new GateDecision(false, gate, detail);
        }
    }

    record ScheduledWindow(
            long startTick,
            long endTick,
            Gate gate
    ) {
        ScheduledWindow {
            if (startTick < 0L || endTick <= startTick
                    || gate == Gate.ALLOWED) {
                throw new IllegalArgumentException(
                        "invalid simulation gate window");
            }
        }

        boolean active(long tick) {
            return tick >= startTick && tick < endTick;
        }
    }

    record Box(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        Box {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("inverted box");
            }
        }

        boolean contains(Coordinate coordinate) {
            return coordinate.x() >= minX && coordinate.x() <= maxX
                    && coordinate.y() >= minY
                    && coordinate.y() <= maxY
                    && coordinate.z() >= minZ
                    && coordinate.z() <= maxZ;
        }
    }

    record Player(int x, int y, int z, int range) {
        boolean reaches(Coordinate coordinate) {
            int dx = coordinate.x() - x;
            int dz = coordinate.z() - z;
            return (long) dx * dx + (long) dz * dz
                    <= (long) range * range;
        }
    }

    record Report(
            BiomeProfile biome,
            Weather weather,
            long simulatedTicks,
            int terrainBlocks,
            int protectedVolumes,
            int scheduledWindows,
            Map<Gate, Integer> gateCounts,
            List<String> trace
    ) {
        int count(Gate gate) {
            return gateCounts.getOrDefault(gate, 0);
        }

        String summary() {
            return "biome=" + biome.id()
                    + " weather=" + weather
                    + " ticks=" + simulatedTicks
                    + " terrain=" + terrainBlocks
                    + " protected=" + protectedVolumes
                    + " windows=" + scheduledWindows
                    + " gates=" + gateCounts;
        }
    }

    private final BiomeProfile biome;
    private final Weather weather;
    private final int minimumLight;
    private final int playerDistance;
    private final Map<String, TerrainCell> terrain = new LinkedHashMap<>();
    private final Map<String, Integer> light = new HashMap<>();
    private final Set<String> blockedSky = new HashSet<>();
    private final List<Box> protectedVolumes = new ArrayList<>();
    private final List<Player> players = new ArrayList<>();
    private final List<ScheduledWindow> scheduledWindows = new ArrayList<>();
    private final List<UnavailableArea> unavailableAreas = new ArrayList<>();
    private final Map<Gate, Integer> gateCounts = new EnumMap<>(Gate.class);
    private final List<String> trace = new ArrayList<>();
    private long tick;

    TreeSimulatedMinecraftEnvironment(
            BiomeProfile biome,
            Weather weather,
            int minimumLight,
            int playerDistance
    ) {
        this.biome = biome;
        this.weather = weather;
        this.minimumLight = Math.max(0, Math.min(15, minimumLight));
        this.playerDistance = Math.max(1, playerDistance);
        trace.add("## Minecraft-like tree replay environment gates.");
        trace.add("tick,operation,coordinate,gate,detail");
    }

    static TreeSimulatedMinecraftEnvironment permissive() {
        TreeSimulatedMinecraftEnvironment environment =
                new TreeSimulatedMinecraftEnvironment(
                        BiomeProfile.PLAINS, Weather.CLEAR, 0, 1);
        environment.addPlayer(0, 64, 0, 1_000_000);
        return environment;
    }

    BiomeProfile biome() {
        return biome;
    }

    Weather weather() {
        return weather;
    }

    long tick() {
        return tick;
    }

    void advanceTick() {
        tick++;
    }

    void addPlayer(int x, int y, int z, int range) {
        players.add(new Player(x, y, z, Math.max(1, range)));
    }

    void seedTerrain(
            int x,
            int y,
            int z,
            Material material,
            boolean natural,
            boolean replaceable,
            boolean playerPlaced
    ) {
        terrain.put(key(x, y, z), new TerrainCell(
                material, natural, replaceable, playerPlaced));
    }

    void setLight(int x, int y, int z, int level) {
        light.put(key(x, y, z), Math.max(0, Math.min(15, level)));
    }

    void blockSky(int x, int y, int z) {
        blockedSky.add(key(x, y, z));
    }

    void protect(Box box) {
        protectedVolumes.add(box);
    }

    void scheduleGate(long startTick, long endTick, Gate gate) {
        scheduledWindows.add(new ScheduledWindow(
                startTick, endTick, gate));
    }

    void temporarilyUnavailable(
            Box box, long endTick, Gate gate) {
        if (gate != Gate.CHUNK_UNLOADED
                && gate != Gate.REGION_NOT_OWNED) {
            throw new IllegalArgumentException(
                    "local unavailable area needs a Folia/chunk gate");
        }
        unavailableAreas.add(new UnavailableArea(
                box, Math.max(1L, endTick), gate));
    }

    GateDecision canSchedule(int x, int y, int z) {
        Coordinate coordinate = new Coordinate(x, y, z);
        for (UnavailableArea area : unavailableAreas) {
            if (tick < area.endTick()
                    && area.box().contains(coordinate)) {
                return record("schedule", coordinate,
                        GateDecision.block(
                                area.gate(), "captured-live-area"));
            }
        }
        Gate active = activeScheduleGate();
        if (active != Gate.ALLOWED) {
            return record("schedule", coordinate,
                    GateDecision.block(active, "scheduled-window"));
        }
        if (players.stream().noneMatch(player ->
                player.reaches(coordinate)
                        && player.range() >= playerDistance)) {
            return record("schedule", coordinate,
                    GateDecision.block(
                            Gate.NO_NEARBY_PLAYER,
                            "required=" + playerDistance));
        }
        int level = light.getOrDefault(key(x, y, z), 15);
        if (level < minimumLight || blockedSky.contains(key(x, y, z))) {
            return record("schedule", coordinate,
                    GateDecision.block(
                            Gate.CAVE_OR_LOW_LIGHT,
                            "light=" + level + " sky="
                                    + !blockedSky.contains(key(x, y, z))));
        }
        return GateDecision.allow();
    }

    GateDecision canPlace(
            String coordinateKey,
            Material material,
            TreeBlockRole role,
            Cell current
    ) {
        Coordinate coordinate = coordinate(coordinateKey);
        GateDecision schedule = canSchedule(
                coordinate.x(), coordinate.y(), coordinate.z());
        if (!schedule.allowed()) {
            return schedule;
        }
        if (protectedVolumes.stream().anyMatch(box ->
                box.contains(coordinate))) {
            return record("place", coordinate,
                    GateDecision.block(
                            Gate.PROTECTED_REGION, material.name()));
        }
        TerrainCell occupied = terrain.get(coordinate.key());
        if (occupied == null) {
            return GateDecision.allow();
        }
        if (occupied.playerPlaced()) {
            return record("place", coordinate,
                    GateDecision.block(
                            Gate.PLAYER_BLOCK,
                            occupied.material().name()));
        }
        if (occupied.material() == Material.WATER
                || occupied.material() == Material.LAVA) {
            return record("place", coordinate,
                    GateDecision.block(
                            Gate.FLUID, occupied.material().name()));
        }
        if (!occupied.natural() || !occupied.replaceable()) {
            return record("place", coordinate,
                    GateDecision.block(
                            Gate.IMPORTANT_BLOCK,
                            occupied.material().name()));
        }
        terrain.remove(coordinate.key());
        trace.add(csv(tick, "replace-natural", coordinate,
                Gate.ALLOWED, occupied.material() + "->" + material));
        gateCounts.merge(Gate.ALLOWED, 1, Integer::sum);
        return GateDecision.allow();
    }

    GateDecision canRemove(String coordinateKey, Cell current) {
        Coordinate coordinate = coordinate(coordinateKey);
        GateDecision schedule = canSchedule(
                coordinate.x(), coordinate.y(), coordinate.z());
        if (!schedule.allowed()) {
            return schedule;
        }
        if (protectedVolumes.stream().anyMatch(box ->
                box.contains(coordinate))) {
            return record("remove", coordinate,
                    GateDecision.block(
                            Gate.PROTECTED_REGION,
                            current == null ? "AIR"
                                    : current.material().name()));
        }
        return GateDecision.allow();
    }

    void recordAllowedMutation(
            String operation,
            String coordinateKey,
            String detail
    ) {
        Coordinate coordinate = coordinate(coordinateKey);
        gateCounts.merge(Gate.ALLOWED, 1, Integer::sum);
        trace.add(csv(tick, operation, coordinate,
                Gate.ALLOWED, detail));
    }

    TerrainCell terrain(String coordinateKey) {
        return terrain.get(coordinate(coordinateKey).key());
    }

    Map<String, TerrainCell> terrain() {
        return Map.copyOf(terrain);
    }

    Report report() {
        return new Report(
                biome, weather, tick, terrain.size(),
                protectedVolumes.size(), scheduledWindows.size(),
                Map.copyOf(gateCounts), List.copyOf(trace));
    }

    private Gate activeScheduleGate() {
        return scheduledWindows.stream()
                .filter(window -> window.active(tick))
                .map(ScheduledWindow::gate)
                .findFirst()
                .orElse(Gate.ALLOWED);
    }

    private GateDecision record(
            String operation,
            Coordinate coordinate,
            GateDecision decision
    ) {
        gateCounts.merge(decision.gate(), 1, Integer::sum);
        trace.add(csv(tick, operation, coordinate,
                decision.gate(), decision.detail()));
        return decision;
    }

    private static String csv(
            long tick,
            String operation,
            Coordinate coordinate,
            Gate gate,
            String detail
    ) {
        return tick + "," + operation + "," + coordinate.key()
                + "," + gate + ",\""
                + detail.replace("\"", "\"\"") + "\"";
    }

    private static Coordinate coordinate(String value) {
        String[] parts = value.split(":");
        int offset = parts.length - 3;
        return new Coordinate(
                Integer.parseInt(parts[offset]),
                Integer.parseInt(parts[offset + 1]),
                Integer.parseInt(parts[offset + 2]));
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private record Coordinate(int x, int y, int z) {
        String key() {
            return TreeSimulatedMinecraftEnvironment.key(x, y, z);
        }
    }

    private record UnavailableArea(
            Box box, long endTick, Gate gate) {
    }
}
