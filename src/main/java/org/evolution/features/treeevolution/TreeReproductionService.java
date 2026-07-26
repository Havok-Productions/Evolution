package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.evolution.coreparts.EvolutionPlugin;
import org.evolution.coreparts.ResourceReporter.ReportSample;

/**
 * ## Owns tree reproduction as an independent work lane.
 *
 * <p>Parent eligibility, deterministic search expansion, spacing, vegetation replacement, and
 * cooldown state live here. Structural construction only asks this service for a reserved target
 * or records a successful child, so reproduction cannot compete by quietly changing tree plans.
 */
final class TreeReproductionService {
    private final EvolutionPlugin plugin;
    private final TreeEvolutionDiagnostics diagnostics;
    private final TreeDnaRepository repository;
    private final ConcurrentMap<String, TreeDna> treeDna;
    private final Set<Material> naturalGround;
    private final Set<Material> naturalDetails;
    private final AtomicLong changedBlocks;
    private final BiPredicate<Location, TreeEvolutionConfig> canWorkAt;
    private final ConcurrentMap<String, Long> cooldownUntil = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> searchSequence = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> nextReservedSearchMillis = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> reservedPassSequence = new ConcurrentHashMap<>();

    TreeReproductionService(
            EvolutionPlugin plugin,
            TreeEvolutionDiagnostics diagnostics,
            TreeDnaRepository repository,
            Set<Material> naturalGround,
            Set<Material> naturalDetails,
            AtomicLong changedBlocks,
            BiPredicate<Location, TreeEvolutionConfig> canWorkAt
    ) {
        this.plugin = plugin;
        this.diagnostics = diagnostics;
        this.repository = repository;
        this.treeDna = repository.records();
        this.naturalGround = Set.copyOf(naturalGround);
        this.naturalDetails = Set.copyOf(naturalDetails);
        this.changedBlocks = changedBlocks;
        this.canWorkAt = canWorkAt;
    }

    int runReserved(UUID playerId, Location origin, TreeEvolutionConfig config) {
        TreeReproductionConfig reproduction = config.reproduction();
        if (!reproduction.enabled()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long nextSearch = nextReservedSearchMillis.getOrDefault(playerId, 0L);
        if (now < nextSearch) {
            return 0;
        }
        nextReservedSearchMillis.put(
                playerId, now + reproduction.reservedSearchIntervalMillis());

        try (ReportSample sample = plugin.resourceReporter().begin(
                "tree-evolution", "action.reserved-seedling-search")) {
            World world = origin.getWorld();
            if (world == null) {
                sample.detail("missing-world");
                return 0;
            }
            int searchRadius = config.searchRadius();
            long searchRadiusSquared = (long) searchRadius * searchRadius;
            List<TreeDna> eligible = new ArrayList<>();
            for (TreeDna dna : treeDna.values()) {
                if (!world.getUID().equals(dna.worldId())
                        || !reproduction.eligible(
                                dna, now, cooldownUntil.getOrDefault(dna.key(), 0L))) {
                    continue;
                }
                long dx = (long) dna.baseX() - origin.getBlockX();
                long dz = (long) dna.baseZ() - origin.getBlockZ();
                if ((dx * dx) + (dz * dz) > searchRadiusSquared) {
                    continue;
                }
                int chunkX = dna.baseX() >> 4;
                int chunkZ = dna.baseZ() >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ)
                        || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                    continue;
                }
                eligible.add(dna);
            }
            eligible.sort(Comparator.comparing(TreeDna::key));
            if (eligible.isEmpty()) {
                plugin.pathDebug().traceSampled(
                        plugin,
                        "tree-evolution",
                        "seedling.lane.no-eligible-parent",
                        "near=" + format(origin)
                                + " ## no loaded, owned, healthy tree meets the reproduction stage/age gate");
                sample.detail("no-eligible-parent");
                return 0;
            }

            long passSequence = reservedPassSequence.merge(playerId, 1L, Long::sum);
            int start = (int) Math.floorMod(
                    passSequence + playerId.getLeastSignificantBits(), eligible.size());
            int candidateRolls = 0;
            for (int offset = 0;
                    offset < eligible.size()
                            && candidateRolls < reproduction.candidateRollsPerPass();
                    offset++) {
                TreeDna dna = eligible.get((start + offset) % eligible.size());
                Block base = world.getBlockAt(dna.baseX(), dna.baseY(), dna.baseZ());
                if (base.getType() != dna.species().logMaterial()) {
                    continue;
                }
                candidateRolls++;
                double chance = reproduction.chanceFor(dna);
                Random chanceRoll = new Random(
                        dna.seed()
                                ^ (passSequence * 0xD1342543DE82EF95L)
                                ^ playerId.getMostSignificantBits());
                if (chance <= 0.0D || chanceRoll.nextDouble() >= chance) {
                    plugin.pathDebug().traceSampled(
                            plugin,
                            "tree-evolution",
                            "seedling.lane.chance-miss",
                            "tree=" + dna.key()
                                    + " chance=" + Math.round(chance * 1000.0D) / 10.0D
                                    + "% roll=" + candidateRolls + "/"
                                    + reproduction.candidateRollsPerPass());
                    continue;
                }
                if (!canWorkAt.test(base.getLocation(), config)) {
                    plugin.pathDebug().traceSampled(
                            plugin,
                            "tree-evolution",
                            "seedling.lane.parent-gate",
                            "tree=" + dna.key()
                                    + " ## parent is outside the current safe Folia/player/protection area");
                    continue;
                }

                plugin.pathDebug().trace(
                        plugin,
                        "tree-evolution",
                        "seedling.lane.search",
                        "tree=" + dna.key()
                                + " pass=" + passSequence
                                + " candidates=" + eligible.size()
                                + " rolls=" + candidateRolls
                                + " attempts=" + reproduction.searchAttempts());
                Optional<Block> target = findSpot(world, dna, config);
                sample.workUnits(candidateRolls + reproduction.searchAttempts());
                if (target.isEmpty()) {
                    sample.detail("search-exhausted tree=" + dna.key());
                    return 0;
                }
                spread(target.get(), dna, config, "reserved-lane");
                plugin.pathDebug().trace(
                        plugin,
                        "tree-evolution",
                        "seedling.lane.spread",
                        "tree=" + dna.key()
                                + " pass=" + passSequence
                                + " ## independently paced reproduction placed one sapling without consuming structural budget");
                sample.changedUnits(1).detail("spread tree=" + dna.key());
                return 1;
            }

            plugin.pathDebug().traceSampled(
                    plugin,
                    "tree-evolution",
                    "seedling.lane.no-chance-pass",
                    "eligible=" + eligible.size()
                            + " rolls=" + candidateRolls + "/"
                            + reproduction.candidateRollsPerPass());
            sample.workUnits(candidateRolls).detail("no-chance-pass");
            return 0;
        }
    }

    Optional<Block> findSpot(
            World world,
            TreeDna dna,
            TreeEvolutionConfig config
    ) {
        TreeReproductionConfig reproduction = config.reproduction();
        long now = System.currentTimeMillis();
        if (!reproduction.eligible(dna, now, cooldownUntil.getOrDefault(dna.key(), 0L))) {
            return Optional.empty();
        }

        long sequence = searchSequence.merge(dna.key(), 1L, Long::sum);
        long searchSeed = dna.seed()
                ^ (dna.age() * 31L)
                ^ (sequence * 0x9E3779B97F4A7C15L)
                ^ 0x5EEDL;
        int futureCanopyRadius = projectedCanopyRadius(dna.species());
        int radius = reproduction.radiusFor(dna);
        int minimumRadius = Math.min(
                radius,
                Math.max(
                        reproduction.minimumRadius(),
                        TreeSeedlingSearchPolicy.requiredBaseDistance(
                                Math.max(
                                        TreeSpeciesStageStyle.canopyRadiusX(dna),
                                        TreeSpeciesStageStyle.canopyRadiusZ(dna)),
                                futureCanopyRadius)));
        List<TreeSeedlingSearchPolicy.Offset> offsets = TreeSeedlingSearchPolicy.sampleRing(
                minimumRadius, radius, reproduction.searchAttempts(), searchSeed);
        int regionRejects = 0;
        int surfaceRejects = 0;
        int lightRejects = 0;
        int spacingRejects = 0;
        int protectionRejects = 0;
        for (TreeSeedlingSearchPolicy.Offset offset : offsets) {
            int x = dna.baseX() + offset.x();
            int z = dna.baseZ() + offset.z();
            int chunkX = x >> 4;
            int chunkZ = z >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)
                    || !Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0)) {
                regionRejects++;
                continue;
            }

            Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block surface = ground.getRelative(BlockFace.UP);
            if (!canReplace(surface) || !naturalGround.contains(ground.getType())) {
                surfaceRejects++;
                continue;
            }
            if (surface.getLightFromSky() < 9) {
                lightRejects++;
                continue;
            }
            int liveSpacingRadius = Math.max(
                    reproduction.spacingRadius(), Math.min(8, futureCanopyRadius + 2));
            if (nearExistingSaplingOrLog(surface, liveSpacingRadius)
                    || nearKnownTreeFootprint(world, surface, futureCanopyRadius)) {
                spacingRejects++;
                continue;
            }
            if (!plugin.canEvolveAt(surface.getLocation(), "tree-reproduction")) {
                protectionRejects++;
                continue;
            }
            if (!canClearVegetation(surface)) {
                protectionRejects++;
                continue;
            }
            plugin.pathDebug().trace(
                    plugin,
                    "tree-evolution",
                    "seedling.search-pass",
                    "tree=" + dna.key()
                            + " sequence=" + sequence
                            + " ring=" + minimumRadius + ".." + radius
                            + " target=" + format(surface)
                            + " replacing=" + surface.getType());
            return Optional.of(surface);
        }

        String summary = "tree=" + dna.key()
                + " sequence=" + sequence
                + " ring=" + minimumRadius + ".." + radius
                + " attempts=" + offsets.size()
                + " sampled-ring=" + offsets.size()
                + " region=" + regionRejects
                + " surface=" + surfaceRejects
                + " light=" + lightRejects
                + " spacing=" + spacingRejects
                + " protection=" + protectionRejects;
        diagnostics.recordReject(config, "seedling-search-exhausted", summary);
        plugin.pathDebug().traceSampled(
                plugin,
                "tree-evolution",
                "seedling.search-exhausted",
                summary + " ## the next retry uses a fresh deterministic sequence");
        return Optional.empty();
    }

    void spread(
            Block sapling,
            TreeDna dna,
            TreeEvolutionConfig config,
            String source
    ) {
        Material replaced = sapling.getType();
        clearVegetation(sapling);
        sapling.setType(dna.species().saplingMaterial(), false);
        changedBlocks.incrementAndGet();
        TreeSeedlingRecord seedling =
                TreeSeedlingRecord.create(sapling, dna);
        repository.registerSeedling(seedling);
        repository.save(config);
        diagnostics.recordSeedling(plugin, config, sapling, dna);
        cooldownUntil.put(
                dna.key(), System.currentTimeMillis() + config.reproduction().cooldownMillis());
        plugin.pathDebug().trace(
                plugin,
                "tree-evolution",
                "seedling.spread",
                dna.species().saplingMaterial()
                        + " child-of=" + dna.key()
                        + " source=" + source
                        + " replaced=" + replaced
                        + " owned-seedling=" + seedling.key()
                        + " at " + format(sapling));
    }

    Optional<TreeSeedlingRecord> ownedSeedling(Block block) {
        return Optional.ofNullable(repository.seedlingAt(block));
    }

    void forgetSeedling(Block block, TreeEvolutionConfig config, String reason) {
        TreeSeedlingRecord removed = repository.removeSeedling(block, reason);
        if (removed == null) {
            return;
        }
        repository.save(config);
        plugin.pathDebug().trace(
                plugin,
                "tree-evolution",
                "seedling.ownership-release",
                removed.species().id() + " reason=" + reason
                        + " at " + format(block));
    }

    TreeCandidate germinate(
            Block sapling,
            TreeSeedlingRecord seedling,
            String source
    ) {
        sapling.setType(seedling.species().logMaterial(), false);
        changedBlocks.incrementAndGet();
        String blockKey = TreeSeedlingRecord.key(sapling);
        plugin.pathDebug().trace(
                plugin,
                "tree-evolution",
                "seedling.germinate",
                seedling.species().id()
                        + " parent=" + seedling.parentKey()
                        + " generation=" + seedling.generation()
                        + " source=" + source
                        + " at " + format(sapling)
                        + " ## vanilla whole-tree growth was cancelled; "
                        + "the gradual constructor now owns this one-log start");
        return new TreeCandidate(
                sapling.getWorld(),
                sapling.getX(),
                sapling.getY(),
                sapling.getZ(),
                sapling.getY(),
                1,
                seedling.species(),
                1,
                0,
                Set.of(blockKey),
                true);
    }
    long cooldownUntil(String treeKey) {
        return cooldownUntil.getOrDefault(treeKey, 0L);
    }

    void forgetTree(String treeKey) {
        cooldownUntil.remove(treeKey);
        searchSequence.remove(treeKey);
    }

    void forgetPlayer(UUID playerId) {
        nextReservedSearchMillis.remove(playerId);
        reservedPassSequence.remove(playerId);
    }

    void clearPlayerState() {
        nextReservedSearchMillis.clear();
        reservedPassSequence.clear();
    }

    private boolean nearKnownTreeFootprint(
            World world,
            Block target,
            int futureCanopyRadius
    ) {
        for (TreeDna existing : treeDna.values()) {
            if (!existing.stumpPresent() || !world.getUID().equals(existing.worldId())) {
                continue;
            }
            int deltaX = target.getX() - existing.baseX();
            int deltaZ = target.getZ() - existing.baseZ();
            int existingRadius = Math.max(
                    TreeSpeciesStageStyle.canopyRadiusX(existing),
                    TreeSpeciesStageStyle.canopyRadiusZ(existing));
            if (TreeSeedlingSearchPolicy.footprintsOverlap(
                    deltaX, deltaZ, existingRadius, futureCanopyRadius)) {
                return true;
            }
        }
        return false;
    }

    private int projectedCanopyRadius(TreeSpecies species) {
        return switch (species) {
            case BIRCH -> 3;
            case OAK, SPRUCE, ACACIA, CHERRY -> 4;
            case JUNGLE, DARK_OAK, MANGROVE -> 5;
        };
    }

    private boolean canReplace(Block surface) {
        return surface.getType().isAir() || naturalDetails.contains(surface.getType());
    }

    private boolean canClearVegetation(Block surface) {
        BlockData data = surface.getBlockData();
        if (!(data instanceof Bisected bisected)) {
            return true;
        }
        Block companion = bisected.getHalf() == Bisected.Half.TOP
                ? surface.getRelative(BlockFace.DOWN)
                : surface.getRelative(BlockFace.UP);
        return companion.getType() != surface.getType()
                || (isOwnedLoaded(companion)
                        && plugin.canEvolveAt(
                                companion.getLocation(), "tree-reproduction"));
    }

    private void clearVegetation(Block surface) {
        BlockData data = surface.getBlockData();
        if (!(data instanceof Bisected bisected)) {
            return;
        }
        Block companion = bisected.getHalf() == Bisected.Half.TOP
                ? surface.getRelative(BlockFace.DOWN)
                : surface.getRelative(BlockFace.UP);
        if (companion.getType() == surface.getType()) {
            companion.setType(Material.AIR, false);
            plugin.pathDebug().traceSampled(
                    plugin,
                    "tree-evolution",
                    "seedling.replace-tall-vegetation",
                    "cleared=" + surface.getType()
                            + " companion=" + format(companion)
                            + " ## both halves are cleared before the sapling is placed");
        }
    }

    private boolean nearExistingSaplingOrLog(Block center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Material type = center.getRelative(x, y, z).getType();
                    if (isLogOrLeaf(type)
                            || type.name().endsWith("_SAPLING")
                            || type == Material.MANGROVE_PROPAGULE) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isOwnedLoaded(Block block) {
        World world = block.getWorld();
        int chunkX = block.getX() >> 4;
        int chunkZ = block.getZ() >> 4;
        return world.isChunkLoaded(chunkX, chunkZ)
                && Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ, 0);
    }

    private boolean isLogOrLeaf(Material material) {
        return material.name().endsWith("_LOG")
                || material.name().endsWith("_WOOD")
                || material.name().endsWith("_LEAVES");
    }

    private String format(Block block) {
        return format(block.getLocation());
    }

    private String format(Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "unknown" : world.getName();
        return worldName + " " + location.getBlockX()
                + "," + location.getBlockY()
                + "," + location.getBlockZ();
    }
}
