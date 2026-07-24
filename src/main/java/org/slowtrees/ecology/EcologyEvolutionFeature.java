package org.slowtrees.ecology;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.slowtrees.core.PluginFeature;
import org.slowtrees.core.ResourceReporter.ReportSample;
import org.slowtrees.core.SlowTreesPlugin;

public final class EcologyEvolutionFeature implements PluginFeature, Listener {
    private static final List<BlockFace> HORIZONTAL_FACES = List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
    private static final List<BlockFace> ALL_FACES = List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
    private static final List<BambooOffset> BAMBOO_SPREAD_OFFSETS = List.of(
            new BambooOffset(1, 0),
            new BambooOffset(-1, 0),
            new BambooOffset(0, 1),
            new BambooOffset(0, -1),
            new BambooOffset(1, 1),
            new BambooOffset(1, -1),
            new BambooOffset(-1, 1),
            new BambooOffset(-1, -1)
    );
    private static final Set<Material> TREE_GROUND = Set.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT,
            Material.PODZOL,
            Material.MOSS_BLOCK,
            Material.MYCELIUM,
            Material.MUD
    );
    private static final Set<Material> MUTABLE_GROUND = Set.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT,
            Material.PODZOL,
            Material.MOSS_BLOCK,
            Material.MYCELIUM,
            Material.MUD,
            Material.SAND,
            Material.RED_SAND,
            Material.GRAVEL,
            Material.TERRACOTTA
    );
    private static final Set<Material> REPLACEABLE_DETAIL_SPACE = Set.of(
            Material.AIR,
            Material.CAVE_AIR,
            Material.VOID_AIR,
            Material.SHORT_GRASS,
            Material.FERN,
            Material.LEAF_LITTER,
            Material.SNOW
    );
    private static final Set<Material> DETAIL_MATERIALS = Set.of(
            Material.SHORT_GRASS,
            Material.TALL_GRASS,
            Material.FERN,
            Material.LARGE_FERN,
            Material.LEAF_LITTER,
            Material.MOSS_CARPET,
            Material.BROWN_MUSHROOM,
            Material.RED_MUSHROOM,
            Material.DANDELION,
            Material.POPPY,
            Material.BLUE_ORCHID,
            Material.ALLIUM,
            Material.AZURE_BLUET,
            Material.OXEYE_DAISY,
            Material.CORNFLOWER,
            Material.LILY_OF_THE_VALLEY,
            Material.PINK_PETALS,
            Material.DEAD_BUSH,
            Material.SWEET_BERRY_BUSH,
            Material.SUGAR_CANE,
            Material.BAMBOO,
            Material.CACTUS,
            Material.PUMPKIN,
            Material.MELON
    );

    private final SlowTreesPlugin plugin;
    private final Random random = new Random();
    private final EcologyEvolutionDiagnostics diagnostics = new EcologyEvolutionDiagnostics();
    private final AtomicLong changedBlocks = new AtomicLong();
    private volatile EcologyEvolutionConfig config;

    public EcologyEvolutionFeature(SlowTreesPlugin plugin) {
        this.plugin = plugin;
        this.config = EcologyEvolutionConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "ecology", "config.load", config.summary());
    }

    @Override
    public void onEnable() {
        diagnostics.saveNow(plugin, config);
        plugin.pathDebug().trace(plugin, "ecology", "enable.schedule-online-players", "players=" + Bukkit.getOnlinePlayers().size());
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedulePlayerEvolution(player, 60L);
        }
    }

    @Override
    public void onDisable() {
        diagnostics.saveNow(plugin, config);
    }

    @Override
    public void reload() {
        this.config = EcologyEvolutionConfig.load(plugin);
        plugin.pathDebug().trace(plugin, "ecology", "config.reload", config.summary());
    }

    @Override
    public String status() {
        diagnostics.saveAsync(plugin, config);
        return "Ecology evolution changed " + changedBlocks.get() + " biome detail block(s).";
    }

    public boolean enabled() {
        return config.enabled();
    }

    public long changedBlockCount() {
        return changedBlocks.get();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        schedulePlayerEvolution(event.getPlayer(), 60L);
    }

    private void schedulePlayerEvolution(Player player, long delayTicks) {
        plugin.pathDebug().traceSampled(plugin, "ecology", "scheduler.player-delay", "delay=" + Math.max(1L, delayTicks));
        player.getScheduler().runDelayed(
                plugin,
                task -> runNearPlayer(player),
                null,
                Math.max(1L, delayTicks)
        );
    }

    private void runNearPlayer(Player player) {
        try (ReportSample sample = plugin.resourceReporter().begin("ecology", "tick.run-near-player")) {
            EcologyEvolutionConfig currentConfig = config;
            if (!player.isOnline()) {
                plugin.pathDebug().trace(plugin, "ecology", "tick.skip.offline-player", "player no longer online");
                sample.detail("offline-player");
                return;
            }

            if (!currentConfig.enabled()) {
                plugin.pathDebug().trace(plugin, "ecology", "tick.skip.disabled", "step=" + currentConfig.stepTicks());
                schedulePlayerEvolution(player, currentConfig.stepTicks());
                sample.detail("disabled");
                return;
            }

            Location origin = player.getLocation();
            World world = origin.getWorld();
            if (world == null || world.getEnvironment() != World.Environment.NORMAL || !currentConfig.isWorldAllowed(world)) {
                plugin.pathDebug().trace(plugin, "ecology", "tick.skip.environment", world == null ? "missing-world" : world.getName());
                schedulePlayerEvolution(player, currentConfig.stepTicks());
                sample.detail("environment-skip");
                return;
            }

            int changed = 0;
            int attempts = 0;
            for (int attempt = 0; attempt < currentConfig.attemptsPerStep() && changed < currentConfig.blocksPerStep(); attempt++) {
                attempts++;
                boolean surfaceFirst = random.nextInt(100) < 70;
                boolean action = surfaceFirst
                        ? findEcologyTarget(origin, currentConfig).map(target -> evolveSurface(target, currentConfig)).orElseGet(() -> findCandidate(origin, currentConfig).map(candidate -> evolveTree(candidate, currentConfig)).orElse(false))
                        : findCandidate(origin, currentConfig).map(candidate -> evolveTree(candidate, currentConfig)).orElseGet(() -> findEcologyTarget(origin, currentConfig).map(target -> evolveSurface(target, currentConfig)).orElse(false));
                if (action) {
                    changed++;
                }
            }

            sample.workUnits(attempts).changedUnits(changed).detail("changed=" + changed + " near=" + format(origin));
            plugin.pathDebug().traceSampled(plugin, "ecology", changed > 0 ? "evolution.step.changed" : "evolution.step.no-change",
                    "changed=" + changed + " near=" + format(origin));
            schedulePlayerEvolution(player, currentConfig.stepTicks());
        }
    }

    private Optional<EcologyTarget> findEcologyTarget(Location origin, EcologyEvolutionConfig currentConfig) {
        try (ReportSample sample = plugin.resourceReporter().begin("ecology", "search.surface-target")) {
            World world = origin.getWorld();
            if (world == null) {
                sample.detail("missing-world");
                return Optional.empty();
            }

            diagnostics.recordSearch();
            int radius = currentConfig.searchRadius();
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if ((dx * dx) + (dz * dz) > radius * radius) {
                diagnostics.recordRejectSampled(currentConfig, "radius-roll", "surface random-offset=" + dx + "," + dz + " radius=" + radius);
                sample.detail("radius-roll");
                return Optional.empty();
            }

            int x = origin.getBlockX() + dx;
            int z = origin.getBlockZ() + dz;
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                diagnostics.recordRejectSampled(currentConfig, "chunk-or-region", "surface chunk=" + chunkX + "," + chunkZ
                        + " loaded=" + world.isChunkLoaded(chunkX, chunkZ)
                        + " owned=" + Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)
                        + " random-offset=" + dx + "," + dz);
                plugin.pathDebug().failure(plugin, "ecology", "chunk-or-region-gate", "surface chunk " + chunkX + "," + chunkZ);
                sample.detail("chunk-or-region");
                return Optional.empty();
            }

            Optional<Block> ground = findSurfaceGround(world, x, z);
            if (ground.isEmpty()) {
                Block highest = world.getHighestBlockAt(x, z);
                diagnostics.recordRejectSampled(currentConfig, "surface-none", "x=" + x + " z=" + z
                        + " highest=" + format(highest)
                        + " highest-type=" + highest.getType()
                        + " light=" + highest.getLightFromSky()
                        + " random-offset=" + dx + "," + dz);
                sample.detail("surface-none");
                return Optional.empty();
            }
            Block surface = ground.get();
            if (!isNearPlayer(surface.getLocation(), currentConfig.requiredPlayerDistanceChunks())) {
                diagnostics.recordRejectSampled(currentConfig, "player-distance", surfaceContext(surface)
                        + " nearest-player-chunks=" + nearestPlayerDistanceChunks(surface.getLocation())
                        + " required=" + currentConfig.requiredPlayerDistanceChunks()
                        + " random-offset=" + dx + "," + dz);
                sample.detail("player-distance");
                return Optional.empty();
            }
            if (surface.isLiquid() || surface.getRelative(BlockFace.UP).isLiquid() || surface.getLightFromSky() < 4) {
                diagnostics.recordRejectSampled(currentConfig, "surface-safety", surfaceContext(surface)
                        + " surface-liquid=" + surface.isLiquid()
                        + " above-liquid=" + surface.getRelative(BlockFace.UP).isLiquid()
                        + " random-offset=" + dx + "," + dz);
                sample.detail("surface-safety");
                return Optional.empty();
            }

            Biome biome = surface.getBiome();
            BiomeEcologyPath path = BiomeEcologyPath.from(biome);
            EnumSet<EcologyMicrohabitat> habitats = microhabitats(surface, path);
            EcologyMaturityStage stage = maturityStage(surface, path, habitats);
            diagnostics.recordCandidate();
            diagnostics.recordState(currentConfig, "path=" + path + " stage=" + stage + " microhabitats=" + habitats + " at " + format(surface));
            sample.changedUnits(1).detail("path=" + path + " stage=" + stage + " at " + format(surface));
            return Optional.of(new EcologyTarget(surface, surface.getRelative(BlockFace.UP), biome, path, stage, habitats));
        }
    }

    private boolean evolveSurface(EcologyTarget target, EcologyEvolutionConfig currentConfig) {
        diagnostics.recordTrace(currentConfig, "surface.choose path=" + target.path() + " stage=" + target.stage()
                + " habitats=" + target.habitats() + " ground=" + target.ground().getType() + " target=" + target.target().getType());

        if (random.nextInt(100) < currentConfig.rareFeatureChancePercent()) {
            Material rare = EcologyPalette.rareFeatureFor(target.path(), target.habitats(), random);
            if (placeDetail(target, rare, "rare", currentConfig)) {
                return true;
            }
        }

        if (random.nextInt(100) < currentConfig.plantPaletteChancePercent() && applyMicrohabitatTemplate(target, currentConfig)) {
            return true;
        }

        if (random.nextInt(100) < currentConfig.groundPaletteChancePercent() && mutateGround(target, currentConfig)) {
            return true;
        }

        if (random.nextInt(100) < currentConfig.plantPaletteChancePercent()) {
            Material plant = EcologyPalette.plantFor(target.biome(), target.path(), target.stage(), target.habitats(), random);
            if (MUTABLE_GROUND.contains(plant)) {
                return mutateGround(target.withSuggestedGround(plant), currentConfig);
            }
            return placeDetail(target, plant, "plant", currentConfig);
        }

        return false;
    }

    private boolean applyMicrohabitatTemplate(EcologyTarget target, EcologyEvolutionConfig currentConfig) {
        EcologyMicrohabitatTemplate.Choice choice = EcologyMicrohabitatTemplate.choose(
                target.biome(),
                target.path(),
                target.stage(),
                target.habitats(),
                random
        );
        Optional<EcologyTarget> shifted = templateTarget(target, choice, currentConfig);
        if (shifted.isEmpty()) {
            diagnostics.recordRejectSampled(currentConfig, "template-target", targetContext(target)
                    + " template=" + choice.templateKey()
                    + " offset=" + choice.dx() + "," + choice.dz());
            return false;
        }
        EcologyTarget templateTarget = shifted.get();
        diagnostics.recordTrace(currentConfig, "template.choose key=" + choice.templateKey()
                + " material=" + choice.material()
                + " ground-mutation=" + choice.groundMutation()
                + " offset=" + choice.dx() + "," + choice.dz()
                + " path=" + target.path()
                + " stage=" + target.stage()
                + " habitats=" + target.habitats()
                + " ## microhabitat templates turn palette rolls into recognizable flora patches");
        plugin.pathDebug().traceSampled(plugin, "ecology", "template.choose",
                "key=" + choice.templateKey()
                        + " material=" + choice.material()
                        + " ground=" + choice.groundMutation()
                        + " offset=" + choice.dx() + "," + choice.dz()
                        + " at " + format(templateTarget.target()));
        if (choice.groundMutation() || MUTABLE_GROUND.contains(choice.material())) {
            return mutateGround(templateTarget.withSuggestedGround(choice.material()), currentConfig);
        }
        return placeDetail(templateTarget, choice.material(), "plant", currentConfig);
    }

    private Optional<EcologyTarget> templateTarget(EcologyTarget origin, EcologyMicrohabitatTemplate.Choice choice, EcologyEvolutionConfig currentConfig) {
        int x = origin.ground().getX() + choice.dx();
        int z = origin.ground().getZ() + choice.dz();
        World world = origin.ground().getWorld();
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
            return Optional.empty();
        }
        Optional<Block> ground = findSurfaceGround(world, x, z);
        if (ground.isEmpty()) {
            return Optional.empty();
        }
        Block surface = ground.get();
        if (!isNearPlayer(surface.getLocation(), currentConfig.requiredPlayerDistanceChunks())) {
            return Optional.empty();
        }
        return Optional.of(buildTarget(surface, BiomeEcologyPath.from(surface.getBiome())));
    }
    private boolean mutateGround(EcologyTarget target, EcologyEvolutionConfig currentConfig) {
        Block ground = target.ground();
        Material current = ground.getType();
        if (!isMutableGround(current) || !canReplaceDetailSpace(target.target())) {
            diagnostics.recordRejectSampled(currentConfig, "ground-mutable", targetContext(target)
                    + " mutable=" + isMutableGround(current)
                    + " replaceable-space=" + canReplaceDetailSpace(target.target()));
            return false;
        }

        Material replacement = target.suggestedGround() == null
                ? EcologyPalette.groundFor(target.path(), target.habitats(), current, random)
                : target.suggestedGround();
        if (replacement == current || !isMutableGround(replacement)) {
            diagnostics.recordRejectSampled(currentConfig, "ground-choice", targetContext(target)
                    + " current=" + current + " replacement=" + replacement
                    + " mutable-replacement=" + isMutableGround(replacement));
            return false;
        }
        if ((replacement == Material.MUD || replacement == Material.MOSS_BLOCK || replacement == Material.MYCELIUM) && target.habitats().contains(EcologyMicrohabitat.DRY)) {
            diagnostics.recordRejectSampled(currentConfig, "ground-dry-conflict", targetContext(target) + " replacement=" + replacement);
            return false;
        }

        if (!plugin.canEvolveAt(ground.getLocation(), "ecology")) {
            diagnostics.recordRejectSampled(currentConfig,
                    "worldguard", targetContext(target));
            return false;
        }
        ground.setType(replacement, false);
        changedBlocks.incrementAndGet();
        diagnostics.recordAction(plugin, currentConfig, "ground", ground,
                "path=" + target.path() + " stage=" + target.stage() + " " + current + "->" + replacement + " microhabitats=" + target.habitats());
        plugin.pathDebug().trace(plugin, "ecology", "ground.palette",
                current + "->" + replacement + " path=" + target.path() + " at " + format(ground));
        return true;
    }

    private boolean placeDetail(EcologyTarget target, Material material, String action, EcologyEvolutionConfig currentConfig) {
        if (material == Material.BAMBOO) {
            return placeBamboo(target, action, currentConfig);
        }
        Block block = target.target();
        Block ground = target.ground();
        if (!plugin.canEvolveAt(block.getLocation(), "ecology")) {
            diagnostics.recordRejectSampled(currentConfig,
                    "worldguard", targetContext(target));
            return false;
        }
        if (currentConfig.maxDetailsPerArea() > 0 && countNearbyDetails(block, 6) >= currentConfig.maxDetailsPerArea()) {
            diagnostics.recordRejectSampled(currentConfig, "density", targetContext(target)
                    + " nearby-details=" + countNearbyDetails(block, 6)
                    + " cap=" + currentConfig.maxDetailsPerArea());
            return false;
        }
        Material adjusted = adjustDetailForTerrain(material, target);
        if (!canPlaceDetail(block, ground, adjusted, target)) {
            diagnostics.recordRejectSampled(currentConfig, "detail-place", targetContext(target)
                    + " requested=" + material + " adjusted=" + adjusted
                    + " replaceable-space=" + canReplaceDetailSpace(block)
                    + " owned=" + Bukkit.isOwnedByCurrentRegion(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, 0)
                    + " nearby-water=" + hasAdjacentWater(ground));
            return false;
        }

        if (adjusted == Material.TALL_GRASS || adjusted == Material.LARGE_FERN) {
            if (!placeTallPlant(block, adjusted)) {
                return false;
            }
        } else {
            block.setType(adjusted, false);
        }

        changedBlocks.incrementAndGet();
        String counter = action.equals("rare") ? "rare" : "plant";
        diagnostics.recordAction(plugin, currentConfig, counter, block,
                "material=" + adjusted + " path=" + target.path() + " stage=" + target.stage() + " microhabitats=" + target.habitats());
        plugin.pathDebug().trace(plugin, "ecology", "detail.palette",
                adjusted + " path=" + target.path() + " stage=" + target.stage() + " at " + format(block));
        return true;
    }

    private boolean placeBamboo(EcologyTarget origin, String action,
            EcologyEvolutionConfig currentConfig) {
        if (origin.path() != BiomeEcologyPath.TROPICAL) {
            diagnostics.recordRejectSampled(currentConfig, "bamboo-biome",
                    targetContext(origin));
            return false;
        }

        Optional<Block> parent = findNearestBamboo(
                origin.target(), currentConfig.bambooSpreadRadius());
        EcologyTarget placement = origin;
        String mode = "seed";
        if (parent.isPresent()) {
            int nearby = countNearbyBamboo(parent.get(),
                    currentConfig.bambooSpreadRadius(),
                    currentConfig.maxBambooBlocksPerArea());
            if (nearby >= currentConfig.maxBambooBlocksPerArea()) {
                diagnostics.recordRejectSampled(currentConfig, "bamboo-density",
                        "parent=" + format(parent.get())
                                + " nearby=" + nearby
                                + " cap=" + currentConfig.maxBambooBlocksPerArea());
                return false;
            }
            Optional<EcologyTarget> spreadTarget = findBambooSpreadTarget(
                    parent.get(), currentConfig);
            if (spreadTarget.isEmpty()) {
                diagnostics.recordRejectSampled(currentConfig,
                        "bamboo-spread-target",
                        "parent=" + format(parent.get())
                                + " radius=" + currentConfig.bambooSpreadRadius());
                return false;
            }
            placement = spreadTarget.get();
            mode = "spread";
        }

        Block block = placement.target();
        if (!plugin.canEvolveAt(block.getLocation(), "ecology")
                || !canPlaceDetail(block, placement.ground(),
                        Material.BAMBOO, placement)) {
            diagnostics.recordRejectSampled(currentConfig, "bamboo-place",
                    targetContext(placement)
                            + " mode=" + mode
                            + " replaceable-space=" + canReplaceDetailSpace(block));
            return false;
        }
        int nearby = countNearbyBamboo(block,
                currentConfig.bambooSpreadRadius(),
                currentConfig.maxBambooBlocksPerArea());
        if (nearby >= currentConfig.maxBambooBlocksPerArea()) {
            diagnostics.recordRejectSampled(currentConfig, "bamboo-density",
                    "target=" + format(block)
                            + " nearby=" + nearby
                            + " cap=" + currentConfig.maxBambooBlocksPerArea());
            return false;
        }

        Material replaced = block.getType();
        block.setType(Material.BAMBOO, false);
        changedBlocks.incrementAndGet();
        diagnostics.recordAction(plugin, currentConfig,
                action.equals("rare") ? "rare" : "plant", block,
                "material=BAMBOO mode=" + mode
                        + " replaced=" + replaced
                        + " path=" + placement.path()
                        + parent.map(value -> " parent=" + format(value))
                                .orElse(""));
        plugin.pathDebug().trace(plugin, "ecology", "bamboo." + mode,
                "target=" + format(block)
                        + " replaced=" + replaced
                        + parent.map(value -> " parent=" + format(value))
                                .orElse("")
                        + " ## jungle bamboo expands as a connected patch from stable world anchors");
        return true;
    }

    private Optional<Block> findNearestBamboo(Block origin, int radius) {
        Block nearest = null;
        int nearestDistanceSquared = Integer.MAX_VALUE;
        World world = origin.getWorld();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distanceSquared = (dx * dx) + (dz * dz);
                if (distanceSquared > radius * radius
                        || distanceSquared >= nearestDistanceSquared) {
                    continue;
                }
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                if (!isOwnedLoaded(world, x, z)) {
                    continue;
                }
                Block bamboo = findBambooInColumn(world, x, z,
                        origin.getY(), 6).orElse(null);
                if (bamboo != null) {
                    nearest = bamboo;
                    nearestDistanceSquared = distanceSquared;
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    private Optional<Block> findBambooInColumn(World world, int x, int z,
            int centerY, int verticalRadius) {
        int minimumY = Math.max(world.getMinHeight(),
                centerY - verticalRadius);
        int maximumY = Math.min(world.getMaxHeight() - 1,
                centerY + verticalRadius);
        for (int y = minimumY; y <= maximumY; y++) {
            Block bamboo = world.getBlockAt(x, y, z);
            if (bamboo.getType() != Material.BAMBOO) {
                continue;
            }
            int descent = 0;
            while (bamboo.getY() > world.getMinHeight()
                    && bamboo.getRelative(BlockFace.DOWN).getType()
                            == Material.BAMBOO
                    && descent++ < 16) {
                bamboo = bamboo.getRelative(BlockFace.DOWN);
            }
            return Optional.of(bamboo);
        }
        return Optional.empty();
    }

    private Optional<EcologyTarget> findBambooSpreadTarget(Block parent,
            EcologyEvolutionConfig currentConfig) {
        int start = random.nextInt(BAMBOO_SPREAD_OFFSETS.size());
        World world = parent.getWorld();
        for (int index = 0; index < BAMBOO_SPREAD_OFFSETS.size(); index++) {
            BambooOffset offset = BAMBOO_SPREAD_OFFSETS.get(
                    (start + index) % BAMBOO_SPREAD_OFFSETS.size());
            int x = parent.getX() + offset.dx();
            int z = parent.getZ() + offset.dz();
            if (!isOwnedLoaded(world, x, z)) {
                continue;
            }
            Optional<Block> ground = findSurfaceGround(world, x, z);
            if (ground.isEmpty()) {
                continue;
            }
            EcologyTarget candidate = buildTarget(ground.get(),
                    BiomeEcologyPath.from(ground.get().getBiome()));
            if (candidate.path() != BiomeEcologyPath.TROPICAL
                    || !isNearPlayer(candidate.target().getLocation(),
                            currentConfig.requiredPlayerDistanceChunks())
                    || !plugin.canEvolveAt(candidate.target().getLocation(),
                            "ecology")
                    || !canPlaceDetail(candidate.target(), candidate.ground(),
                            Material.BAMBOO, candidate)) {
                continue;
            }
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private int countNearbyBamboo(Block center, int radius, int stopAt) {
        int count = 0;
        World world = center.getWorld();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > radius * radius) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (!isOwnedLoaded(world, x, z)) {
                    continue;
                }
                int minimumY = Math.max(world.getMinHeight(),
                        center.getY() - 2);
                int maximumY = Math.min(world.getMaxHeight() - 1,
                        center.getY() + 12);
                for (int y = minimumY; y <= maximumY; y++) {
                    if (world.getBlockAt(x, y, z).getType()
                            == Material.BAMBOO && ++count >= stopAt) {
                        return count;
                    }
                }
            }
        }
        return count;
    }

    private boolean isOwnedLoaded(World world, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        return world.isChunkLoaded(chunkX, chunkZ)
                && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0);
    }

    private Material adjustDetailForTerrain(Material material, EcologyTarget target) {
        if (material == Material.VINE && !hasAdjacentTreeBlock(target.target())) {
            return target.habitats().contains(EcologyMicrohabitat.SHADE) ? Material.FERN : Material.SHORT_GRASS;
        }
        if (material == Material.CACTUS && target.ground().getType() != Material.SAND && target.ground().getType() != Material.RED_SAND) {
            return target.habitats().contains(EcologyMicrohabitat.DRY) ? Material.DEAD_BUSH : Material.SHORT_GRASS;
        }
        if (material == Material.SUGAR_CANE && !hasAdjacentWater(target.ground())) {
            return target.habitats().contains(EcologyMicrohabitat.WET) ? Material.FERN : Material.SHORT_GRASS;
        }
        if ((material == Material.PUMPKIN || material == Material.MELON) && target.habitats().contains(EcologyMicrohabitat.SHADE)) {
            return Material.BROWN_MUSHROOM;
        }
        return material;
    }

    private boolean canPlaceDetail(Block block, Block ground, Material material, EcologyTarget target) {
        if (!DETAIL_MATERIALS.contains(material) || !canReplaceDetailSpace(block) || block.isLiquid()) {
            return false;
        }
        if (!Bukkit.isOwnedByCurrentRegion(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, 0)) {
            return false;
        }
        if (material == Material.CACTUS) {
            return (ground.getType() == Material.SAND || ground.getType() == Material.RED_SAND) && noSolidHorizontalNeighbors(block);
        }
        if (material == Material.SUGAR_CANE) {
            return hasAdjacentWater(ground) && (isMutableGround(ground.getType()) || ground.getType() == Material.SAND || ground.getType() == Material.RED_SAND);
        }
        if (material == Material.BAMBOO) {
            return target.path() == BiomeEcologyPath.TROPICAL && TREE_GROUND.contains(ground.getType());
        }
        if (material == Material.PUMPKIN || material == Material.MELON) {
            return block.getLightFromSky() >= 9 && (TREE_GROUND.contains(ground.getType()) || ground.getType() == Material.GRASS_BLOCK);
        }
        if (material == Material.DEAD_BUSH) {
            return ground.getType() == Material.SAND || ground.getType() == Material.RED_SAND || ground.getType() == Material.TERRACOTTA || ground.getType() == Material.COARSE_DIRT;
        }
        if (material == Material.MOSS_CARPET || material == Material.BROWN_MUSHROOM || material == Material.RED_MUSHROOM) {
            return target.habitats().contains(EcologyMicrohabitat.SHADE) || target.path() == BiomeEcologyPath.FUNGAL;
        }
        return TREE_GROUND.contains(ground.getType()) || ground.getType() == Material.SAND || ground.getType() == Material.RED_SAND;
    }

    private Optional<TreeCandidate> findCandidate(Location origin, EcologyEvolutionConfig currentConfig) {
        try (ReportSample sample = plugin.resourceReporter().begin("ecology", "search.tree-candidate")) {
            World world = origin.getWorld();
            if (world == null) {
                sample.detail("missing-world");
                return Optional.empty();
            }

            diagnostics.recordSearch();
            int radius = currentConfig.searchRadius();
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            if ((dx * dx) + (dz * dz) > radius * radius) {
                diagnostics.recordRejectSampled(currentConfig, "tree-radius-roll", "tree random-offset=" + dx + "," + dz + " radius=" + radius);
                sample.detail("radius-roll");
                return Optional.empty();
            }

            int x = origin.getBlockX() + dx;
            int z = origin.getBlockZ() + dz;
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ) || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                diagnostics.recordRejectSampled(currentConfig, "chunk-or-region", "tree chunk=" + chunkX + "," + chunkZ
                        + " loaded=" + world.isChunkLoaded(chunkX, chunkZ)
                        + " owned=" + Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)
                        + " random-offset=" + dx + "," + dz);
                plugin.pathDebug().failure(plugin, "ecology", "chunk-or-region-gate", "candidate chunk " + chunkX + "," + chunkZ);
                sample.detail("chunk-or-region");
                return Optional.empty();
            }

            Block highest = world.getHighestBlockAt(x, z);
            int minY = Math.max(world.getMinHeight(), highest.getY() - 28);
            int scanned = 0;
            for (int y = highest.getY(); y >= minY; y--) {
                scanned++;
                Block block = world.getBlockAt(x, y, z);
                if (!isLog(block.getType())) {
                    continue;
                }

                TreeCandidate candidate = buildCandidate(block);
                if (candidate != null && isNearPlayer(candidate.base().getLocation(), currentConfig.requiredPlayerDistanceChunks())) {
                    diagnostics.recordCandidate();
                    plugin.pathDebug().trace(plugin, "ecology", "candidate.tree",
                            candidate.logMaterial() + " base=" + format(candidate.base()) + " height=" + candidate.height());
                    sample.workUnits(scanned).changedUnits(1).detail(candidate.logMaterial() + " base=" + format(candidate.base()));
                    return Optional.of(candidate);
                }
            }

            diagnostics.recordRejectSampled(currentConfig, "tree-none", "x=" + x + " z=" + z
                    + " highest=" + format(highest)
                    + " highest-type=" + highest.getType()
                    + " random-offset=" + dx + "," + dz);
            sample.workUnits(scanned).detail("tree-none");
            return Optional.empty();
        }
    }

    private TreeCandidate buildCandidate(Block logBlock) {
        Material logMaterial = logBlock.getType();
        Block base = logBlock;
        while (base.getY() > base.getWorld().getMinHeight() && base.getRelative(BlockFace.DOWN).getType() == logMaterial) {
            base = base.getRelative(BlockFace.DOWN);
        }

        Block top = logBlock;
        while (top.getY() < top.getWorld().getMaxHeight() - 1 && top.getRelative(BlockFace.UP).getType() == logMaterial) {
            top = top.getRelative(BlockFace.UP);
        }

        int height = top.getY() - base.getY() + 1;
        if (height < 3 || height > 28 || !TREE_GROUND.contains(base.getRelative(BlockFace.DOWN).getType())) {
            return null;
        }

        Material leafMaterial = preferredLeafMaterial(logMaterial);
        if (!hasNearbyLeaves(top, leafMaterial, 4)) {
            return null;
        }

        return new TreeCandidate(base, top, logMaterial, leafMaterial, height);
    }

    private boolean evolveTree(TreeCandidate candidate, EcologyEvolutionConfig currentConfig) {
        if (plugin.treeEvolutionFeature().enabled()) {
            plugin.pathDebug().trace(plugin, "ecology", "tree-shape.skipped",
                    "TreeEvolution owns logs/leaves; floor-only near " + format(candidate.base()));
            return random.nextInt(100) < currentConfig.forestFloorChancePercent() && growForestFloor(candidate, currentConfig);
        }
        int roll = random.nextInt(100);
        if (roll < currentConfig.heightChancePercent() && growTaller(candidate, currentConfig)) {
            return true;
        }
        roll -= currentConfig.heightChancePercent();
        if (roll < currentConfig.branchChancePercent() && growBranch(candidate, currentConfig)) {
            return true;
        }
        roll -= currentConfig.branchChancePercent();
        if (roll < currentConfig.canopyChancePercent() && growCanopy(candidate, currentConfig)) {
            return true;
        }
        return random.nextInt(100) < currentConfig.forestFloorChancePercent() && growForestFloor(candidate, currentConfig);
    }

    private boolean growTaller(TreeCandidate candidate, EcologyEvolutionConfig currentConfig) {
        int naturalCap = Math.max(candidate.height(), 5) + currentConfig.maxTreeHeightBonus();
        if (candidate.height() >= naturalCap) {
            return false;
        }

        Block target = candidate.top().getRelative(BlockFace.UP);
        if (!canReplaceForTreeGrowth(target, candidate.leafMaterial())) {
            return false;
        }

        if (!plugin.canEvolveAt(target.getLocation(), "ecology")) {
            return false;
        }
        target.setType(candidate.logMaterial(), false);
        changedBlocks.incrementAndGet();
        placeLeafCluster(target, candidate.leafMaterial(), 1);
        diagnostics.recordAction(plugin, currentConfig, "height", target, "log=" + candidate.logMaterial());
        plugin.pathDebug().trace(plugin, "ecology", "evolution.height", candidate.logMaterial() + " at " + format(target));
        return true;
    }

    private boolean growBranch(TreeCandidate candidate, EcologyEvolutionConfig currentConfig) {
        if (currentConfig.maxBranchesNearTree() > 0 && countSideBranches(candidate) >= currentConfig.maxBranchesNearTree()) {
            return false;
        }

        int branchY = Math.max(candidate.base().getY() + 2, candidate.top().getY() - random.nextInt(Math.max(1, Math.min(3, candidate.height()))));
        Block source = candidate.base().getWorld().getBlockAt(candidate.base().getX(), branchY, candidate.base().getZ());
        if (source.getType() != candidate.logMaterial()) {
            return false;
        }

        BlockFace face = HORIZONTAL_FACES.get(random.nextInt(HORIZONTAL_FACES.size()));
        Block branch = source.getRelative(face);
        if (!canReplaceForTreeGrowth(branch, candidate.leafMaterial())) {
            return false;
        }

        if (!plugin.canEvolveAt(branch.getLocation(), "ecology")) {
            return false;
        }
        branch.setType(candidate.logMaterial(), false);
        changedBlocks.incrementAndGet();
        placeLeafCluster(branch, candidate.leafMaterial(), 2);
        diagnostics.recordAction(plugin, currentConfig, "branch", branch, "from=" + format(source) + " face=" + face);
        plugin.pathDebug().trace(plugin, "ecology", "evolution.branch", candidate.logMaterial() + " at " + format(branch));
        return true;
    }

    private boolean growCanopy(TreeCandidate candidate, EcologyEvolutionConfig currentConfig) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = candidate.top().getX() + random.nextInt(7) - 3;
            int y = candidate.top().getY() + random.nextInt(5) - 2;
            int z = candidate.top().getZ() + random.nextInt(7) - 3;
            Block target = candidate.top().getWorld().getBlockAt(x, y, z);
            if (!isAirLike(target.getType()) || !hasAdjacentLeaf(target, candidate.leafMaterial())) {
                continue;
            }

            if (!plugin.canEvolveAt(target.getLocation(), "ecology")) {
                continue;
            }
            placeLeaf(target, candidate.leafMaterial());
            changedBlocks.incrementAndGet();
            diagnostics.recordAction(plugin, currentConfig, "canopy", target, "leaf=" + candidate.leafMaterial());
            plugin.pathDebug().trace(plugin, "ecology", "evolution.canopy", candidate.leafMaterial() + " at " + format(target));
            return true;
        }
        return false;
    }

    private boolean growForestFloor(TreeCandidate candidate, EcologyEvolutionConfig currentConfig) {
        int radius = 2 + random.nextInt(4);
        int x = candidate.base().getX() + random.nextInt(radius * 2 + 1) - radius;
        int z = candidate.base().getZ() + random.nextInt(radius * 2 + 1) - radius;
        Optional<Block> ground = findSurfaceGround(candidate.base().getWorld(), x, z);
        if (ground.isEmpty()) {
            return false;
        }
        Block surface = ground.get();
        EcologyTarget target = buildTarget(surface, BiomeEcologyPath.from(surface.getBiome()));
        Material detail = EcologyPalette.plantFor(target.biome(), target.path(), target.stage(), target.habitats(), random);
        return placeDetail(target, detail, "floor", currentConfig);
    }

    private EcologyTarget buildTarget(Block ground, BiomeEcologyPath path) {
        EnumSet<EcologyMicrohabitat> habitats = microhabitats(ground, path);
        return new EcologyTarget(ground, ground.getRelative(BlockFace.UP), ground.getBiome(), path, maturityStage(ground, path, habitats), habitats, null);
    }

    private Optional<Block> findSurfaceGround(World world, int x, int z) {
        Block highest = world.getHighestBlockAt(x, z);
        int minY = Math.max(world.getMinHeight(), highest.getY() - 48);
        for (int y = highest.getY(); y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (block.isLiquid()) {
                return Optional.empty();
            }
            if (isLeaf(type) || isLog(type) || DETAIL_MATERIALS.contains(type) || type == Material.SNOW || type == Material.VINE) {
                continue;
            }
            if (isNaturalSurface(type)) {
                return Optional.of(block);
            }
            if (type.isSolid()) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private EnumSet<EcologyMicrohabitat> microhabitats(Block ground, BiomeEcologyPath path) {
        EnumSet<EcologyMicrohabitat> habitats = EnumSet.noneOf(EcologyMicrohabitat.class);
        boolean nearWater = hasWaterWithin(ground, 3);
        boolean nearTree = countNearby(ground, 5, this::isTreeBlock) >= 3;
        boolean shade = ground.getRelative(BlockFace.UP).getLightFromSky() < 9 || hasLeavesAbove(ground);
        boolean slope = isSlopedPocket(ground);
        boolean rocky = isRocky(ground.getType()) || slope;
        boolean dry = path == BiomeEcologyPath.DRY || path == BiomeEcologyPath.COASTAL || ground.getType() == Material.SAND || ground.getType() == Material.RED_SAND || ground.getType() == Material.COARSE_DIRT;

        if (nearWater) {
            habitats.add(EcologyMicrohabitat.NEAR_WATER);
        }
        if (nearWater || path == BiomeEcologyPath.WETLAND || ground.getType() == Material.MUD) {
            habitats.add(EcologyMicrohabitat.WET);
        }
        if (nearTree) {
            habitats.add(EcologyMicrohabitat.NEAR_TREE);
        }
        if (shade) {
            habitats.add(EcologyMicrohabitat.SHADE);
        }
        if (nearTree && !shade) {
            habitats.add(EcologyMicrohabitat.EDGE);
        }
        if (slope) {
            habitats.add(EcologyMicrohabitat.SLOPE);
        }
        if (rocky) {
            habitats.add(EcologyMicrohabitat.ROCKY);
        }
        if (dry) {
            habitats.add(EcologyMicrohabitat.DRY);
        }
        if (!nearTree && !shade && !nearWater) {
            habitats.add(EcologyMicrohabitat.OPEN);
        }
        if (habitats.isEmpty()) {
            habitats.add(EcologyMicrohabitat.OPEN);
        }
        return habitats;
    }

    private EcologyMaturityStage maturityStage(Block ground, BiomeEcologyPath path, Set<EcologyMicrohabitat> habitats) {
        int score = 0;
        score += Math.min(18, countNearby(ground, 7, this::isTreeBlock));
        score += Math.min(12, countNearby(ground, 5, DETAIL_MATERIALS::contains));
        score += Math.min(8, countNearby(ground, 5, material -> material == Material.MOSS_BLOCK || material == Material.PODZOL || material == Material.MYCELIUM || material == Material.MUD));
        if (habitats.contains(EcologyMicrohabitat.NEAR_WATER)) {
            score += 3;
        }
        if (habitats.contains(EcologyMicrohabitat.SHADE)) {
            score += 3;
        }
        if (path == BiomeEcologyPath.DRY || path == BiomeEcologyPath.COASTAL || path == BiomeEcologyPath.ALPINE) {
            score -= 4;
        }
        if (score >= 30) {
            return EcologyMaturityStage.OLD_GROWTH;
        }
        if (score >= 21) {
            return EcologyMaturityStage.MATURE;
        }
        if (score >= 13) {
            return EcologyMaturityStage.DENSE;
        }
        if (score >= 6) {
            return EcologyMaturityStage.ESTABLISHED;
        }
        return EcologyMaturityStage.SPARSE;
    }

    private boolean placeTallPlant(Block lower, Material material) {
        Block upper = lower.getRelative(BlockFace.UP);
        if (!upper.getType().isAir()
                || !plugin.canEvolveAt(lower.getLocation(), "ecology")
                || !plugin.canEvolveAt(upper.getLocation(), "ecology")) {
            return false;
        }
        BlockData lowerData = Bukkit.createBlockData(material);
        BlockData upperData = Bukkit.createBlockData(material);
        if (lowerData instanceof Bisected lowerBisected && upperData instanceof Bisected upperBisected) {
            lowerBisected.setHalf(Bisected.Half.BOTTOM);
            upperBisected.setHalf(Bisected.Half.TOP);
        }
        lower.setBlockData(lowerData, false);
        upper.setBlockData(upperData, false);
        return true;
    }

    private void placeLeafCluster(Block center, Material leafMaterial, int radius) {
        for (BlockFace face : HORIZONTAL_FACES) {
            if (random.nextBoolean()) {
                placeLeafIfAir(center.getRelative(face), leafMaterial);
            }
        }
        if (radius > 1) {
            placeLeafIfAir(center.getRelative(BlockFace.UP), leafMaterial);
            placeLeafIfAir(center.getRelative(BlockFace.DOWN), leafMaterial);
        }
    }

    private void placeLeafIfAir(Block block, Material leafMaterial) {
        if (isAirLike(block.getType())
                && plugin.canEvolveAt(block.getLocation(), "ecology")) {
            placeLeaf(block, leafMaterial);
            changedBlocks.incrementAndGet();
        }
    }

    private void placeLeaf(Block block, Material leafMaterial) {
        block.setType(leafMaterial, false);
        if (block.getBlockData() instanceof Leaves leaves) {
            leaves.setPersistent(true);
            block.setBlockData(leaves, false);
        }
    }

    private int countSideBranches(TreeCandidate candidate) {
        int branches = 0;
        for (int y = candidate.base().getY() + 1; y <= candidate.top().getY(); y++) {
            Block trunk = candidate.base().getWorld().getBlockAt(candidate.base().getX(), y, candidate.base().getZ());
            for (BlockFace face : HORIZONTAL_FACES) {
                if (trunk.getRelative(face).getType() == candidate.logMaterial()) {
                    branches++;
                }
            }
        }
        return branches;
    }

    private boolean hasNearbyLeaves(Block center, Material leafMaterial, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Material type = center.getRelative(x, y, z).getType();
                    if (type == leafMaterial || isLeaf(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasAdjacentLeaf(Block target, Material leafMaterial) {
        for (BlockFace face : ALL_FACES) {
            Material type = target.getRelative(face).getType();
            if (type == leafMaterial || isLeaf(type)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAdjacentTreeBlock(Block target) {
        for (BlockFace face : ALL_FACES) {
            Material type = target.getRelative(face).getType();
            if (isLog(type) || isLeaf(type)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLeavesAbove(Block ground) {
        for (int y = 2; y <= 9; y++) {
            if (isLeaf(ground.getRelative(0, y, 0).getType())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAdjacentWater(Block ground) {
        for (BlockFace face : HORIZONTAL_FACES) {
            if (ground.getRelative(face).isLiquid() || ground.getRelative(face).getRelative(BlockFace.UP).isLiquid()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWaterWithin(Block ground, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > radius * radius) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    if (ground.getRelative(dx, dy, dz).isLiquid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isSlopedPocket(Block ground) {
        int uneven = 0;
        int y = ground.getY();
        for (BlockFace face : HORIZONTAL_FACES) {
            Block neighbor = ground.getRelative(face);
            if (neighbor.getY() != y || !isNaturalSurface(neighbor.getType()) && !isNaturalSurface(neighbor.getRelative(BlockFace.DOWN).getType())) {
                uneven++;
            }
        }
        return uneven >= 2;
    }

    private int countNearbyDetails(Block center, int radius) {
        return countNearby(center, radius, DETAIL_MATERIALS::contains);
    }

    private int countNearby(Block center, int radius, MaterialPredicate predicate) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if ((dx * dx) + (dz * dz) > radius * radius) {
                    continue;
                }
                for (int dy = -2; dy <= 4; dy++) {
                    if (predicate.test(center.getRelative(dx, dy, dz).getType())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private boolean canReplaceForTreeGrowth(Block block, Material leafMaterial) {
        Material type = block.getType();
        return isAirLike(type) || type == leafMaterial || type == Material.VINE;
    }

    private boolean canReplaceDetailSpace(Block block) {
        return REPLACEABLE_DETAIL_SPACE.contains(block.getType());
    }

    private boolean noSolidHorizontalNeighbors(Block block) {
        for (BlockFace face : HORIZONTAL_FACES) {
            if (block.getRelative(face).getType().isSolid()) {
                return false;
            }
        }
        return true;
    }

    private boolean isNearPlayer(Location location, int distanceChunks) {
        if (distanceChunks <= 0) {
            return true;
        }

        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        for (Player player : world.getPlayers()) {
            if (!Bukkit.isOwnedByCurrentRegion(player)) {
                continue;
            }
            Location playerLocation = player.getLocation();
            int playerChunkX = playerLocation.getBlockX() >> 4;
            int playerChunkZ = playerLocation.getBlockZ() >> 4;
            int distance = Math.max(Math.abs(playerChunkX - chunkX), Math.abs(playerChunkZ - chunkZ));
            if (distance <= distanceChunks) {
                return true;
            }
        }
        return false;
    }

    private int nearestPlayerDistanceChunks(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return Integer.MAX_VALUE;
        }
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        int nearest = Integer.MAX_VALUE;
        for (Player player : world.getPlayers()) {
            Location playerLocation = player.getLocation();
            int playerChunkX = playerLocation.getBlockX() >> 4;
            int playerChunkZ = playerLocation.getBlockZ() >> 4;
            int distance = Math.max(Math.abs(playerChunkX - chunkX), Math.abs(playerChunkZ - chunkZ));
            nearest = Math.min(nearest, distance);
        }
        return nearest;
    }

    private String surfaceContext(Block surface) {
        Block above = surface.getRelative(BlockFace.UP);
        return "surface=" + format(surface)
                + " biome=" + surface.getBiome().getKey().getKey()
                + " ground=" + surface.getType()
                + " above=" + above.getType()
                + " light=" + above.getLightFromSky()
                + " chunk=" + (surface.getX() >> 4) + "," + (surface.getZ() >> 4);
    }

    private String targetContext(EcologyTarget target) {
        Block block = target.target();
        return "target=" + format(block)
                + " path=" + target.path()
                + " stage=" + target.stage()
                + " habitats=" + target.habitats()
                + " ground=" + target.ground().getType()
                + " target-type=" + block.getType()
                + " light=" + block.getLightFromSky()
                + " liquid=" + block.isLiquid();
    }

    private Material preferredLeafMaterial(Material logMaterial) {
        String name = logMaterial.name();
        if (name.endsWith("_STEM")) {
            return name.startsWith("WARPED") ? Material.WARPED_WART_BLOCK : Material.NETHER_WART_BLOCK;
        }
        String leafName = name.replace("_LOG", "_LEAVES");
        Material leaves = Material.matchMaterial(leafName);
        return leaves == null ? Material.OAK_LEAVES : leaves;
    }

    private boolean isAirLike(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private boolean isLog(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_STEM") || material == Material.MUSHROOM_STEM;
    }

    private boolean isLeaf(Material material) {
        return material.name().endsWith("_LEAVES");
    }

    private boolean isTreeBlock(Material material) {
        return isLog(material) || isLeaf(material);
    }

    private boolean isMutableGround(Material material) {
        return MUTABLE_GROUND.contains(material) || isTerracotta(material);
    }

    private boolean isNaturalSurface(Material material) {
        return isMutableGround(material)
                || material == Material.MUD
                || material == Material.MYCELIUM
                || material == Material.STONE
                || material == Material.ANDESITE
                || material == Material.GRANITE
                || material == Material.DIORITE
                || material == Material.SANDSTONE
                || material == Material.RED_SANDSTONE;
    }

    private boolean isRocky(Material material) {
        return material == Material.STONE
                || material == Material.ANDESITE
                || material == Material.GRANITE
                || material == Material.DIORITE
                || material == Material.GRAVEL
                || material == Material.SANDSTONE
                || material == Material.RED_SANDSTONE
                || isTerracotta(material);
    }

    private boolean isTerracotta(Material material) {
        return material.name().endsWith("TERRACOTTA");
    }

    private String format(Block block) {
        return format(block.getLocation());
    }

    private String format(Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "unknown" : world.getName();
        return worldName + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private record BambooOffset(int dx, int dz) {
    }

    private interface MaterialPredicate {
        boolean test(Material material);
    }

    private record TreeCandidate(Block base, Block top, Material logMaterial, Material leafMaterial, int height) {
    }

    private record EcologyTarget(
            Block ground,
            Block target,
            Biome biome,
            BiomeEcologyPath path,
            EcologyMaturityStage stage,
            Set<EcologyMicrohabitat> habitats,
            Material suggestedGround
    ) {
        private EcologyTarget(Block ground, Block target, Biome biome, BiomeEcologyPath path, EcologyMaturityStage stage, Set<EcologyMicrohabitat> habitats) {
            this(ground, target, biome, path, stage, habitats, null);
        }

        private EcologyTarget withSuggestedGround(Material material) {
            return new EcologyTarget(ground, target, biome, path, stage, habitats, material);
        }
    }
}
