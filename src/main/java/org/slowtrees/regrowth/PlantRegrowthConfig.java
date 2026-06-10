package org.slowtrees.regrowth;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.slowtrees.core.SlowTreesPlugin;

final class PlantRegrowthConfig {
    private final long initialDelayTicks;
    private final long growthStepTicks;
    private final int blocksPerGrowthStep;
    private final boolean worldHealthModeEnabled;
    private final double worldHealthGrowthSpeedMultiplier;
    private final long retryDelayTicks;
    private final int maxRegrowthAttempts;
    private final boolean plantDecayEnabled;
    private final long plantDecayDelayTicks;
    private final long plantDecayStepTicks;
    private final int plantDecayBlocksPerStep;
    private final int plantDecayMaxBlocks;
    private final int requiredPlayerDistanceChunks;
    private final int ownedChunkRadius;
    private final Set<String> enabledWorlds;
    private final Set<String> disabledWorlds;
    private final Set<Material> replaceableMaterials;
    private final Map<Material, TreeType> treeTypes;
    private final Map<Material, TreeType> mushroomTypes;

    private PlantRegrowthConfig(
            long initialDelayTicks,
            long growthStepTicks,
            int blocksPerGrowthStep,
            boolean worldHealthModeEnabled,
            double worldHealthGrowthSpeedMultiplier,
            long retryDelayTicks,
            int maxRegrowthAttempts,
            boolean plantDecayEnabled,
            long plantDecayDelayTicks,
            long plantDecayStepTicks,
            int plantDecayBlocksPerStep,
            int plantDecayMaxBlocks,
            int requiredPlayerDistanceChunks,
            int ownedChunkRadius,
            Set<String> enabledWorlds,
            Set<String> disabledWorlds,
            Set<Material> replaceableMaterials,
            Map<Material, TreeType> treeTypes,
            Map<Material, TreeType> mushroomTypes
    ) {
        this.initialDelayTicks = initialDelayTicks;
        this.growthStepTicks = growthStepTicks;
        this.blocksPerGrowthStep = blocksPerGrowthStep;
        this.worldHealthModeEnabled = worldHealthModeEnabled;
        this.worldHealthGrowthSpeedMultiplier = worldHealthGrowthSpeedMultiplier;
        this.retryDelayTicks = retryDelayTicks;
        this.maxRegrowthAttempts = maxRegrowthAttempts;
        this.plantDecayEnabled = plantDecayEnabled;
        this.plantDecayDelayTicks = plantDecayDelayTicks;
        this.plantDecayStepTicks = plantDecayStepTicks;
        this.plantDecayBlocksPerStep = plantDecayBlocksPerStep;
        this.plantDecayMaxBlocks = plantDecayMaxBlocks;
        this.requiredPlayerDistanceChunks = requiredPlayerDistanceChunks;
        this.ownedChunkRadius = ownedChunkRadius;
        this.enabledWorlds = enabledWorlds;
        this.disabledWorlds = disabledWorlds;
        this.replaceableMaterials = replaceableMaterials;
        this.treeTypes = treeTypes;
        this.mushroomTypes = mushroomTypes;
    }

    static PlantRegrowthConfig load(SlowTreesPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        Logger logger = plugin.getLogger();

        Map<Material, TreeType> treeTypes = loadTreeTypeMap(config, logger, "tree-types");
        Map<Material, TreeType> mushroomTypes = loadTreeTypeMap(config, logger, "mushroom-types");
        if (!config.isSet("mushroom-types")) {
            mushroomTypes.put(Material.RED_MUSHROOM_BLOCK, TreeType.RED_MUSHROOM);
            mushroomTypes.put(Material.BROWN_MUSHROOM_BLOCK, TreeType.BROWN_MUSHROOM);
        }

        Set<Material> replaceableMaterials = new HashSet<>();
        for (String materialName : config.getStringList("replaceable-materials")) {
            Material material = Material.matchMaterial(materialName);
            if (material == null) {
                logger.warning("Ignoring unknown replaceable material in config: " + materialName);
                continue;
            }
            replaceableMaterials.add(material);
        }

        return new PlantRegrowthConfig(
                Math.max(1L, config.getLong("initial-delay-ticks", 20L)),
                Math.max(1L, config.getLong("growth-step-ticks", 600L)),
                Math.max(1, config.getInt("blocks-per-growth-step", 1)),
                config.getBoolean("world-health-mode.enabled", true),
                sanitizeGrowthSpeedMultiplier(config.getDouble("world-health-mode.growth-speed-multiplier", 0.15D), logger),
                Math.max(1L, config.getLong("retry-delay-ticks", 6000L)),
                Math.max(0, config.getInt("max-regrowth-attempts", 0)),
                config.getBoolean("plant-decay.enabled", true),
                Math.max(1L, config.getLong("plant-decay.delay-ticks", 6000L)),
                Math.max(1L, config.getLong("plant-decay.step-ticks", 20L)),
                Math.max(1, config.getInt("plant-decay.blocks-per-step", 1)),
                Math.max(1, config.getInt("plant-decay.max-blocks", 128)),
                Math.max(0, config.getInt("required-player-distance-chunks", 8)),
                Math.max(0, config.getInt("owned-chunk-radius", 1)),
                normalizeWorldNames(config.getStringList("enabled-worlds")),
                normalizeWorldNames(config.getStringList("disabled-worlds")),
                Collections.unmodifiableSet(replaceableMaterials),
                Collections.unmodifiableMap(new HashMap<>(treeTypes)),
                Collections.unmodifiableMap(new HashMap<>(mushroomTypes))
        );
    }

    Optional<TreeType> treeTypeFor(Material material) {
        return Optional.ofNullable(treeTypes.get(material));
    }

    Optional<TreeType> mushroomTypeFor(Material material) {
        return Optional.ofNullable(mushroomTypes.get(material));
    }

    boolean isReplaceable(Material material) {
        return replaceableMaterials.contains(material);
    }

    boolean isWorldAllowed(World world) {
        String worldName = world.getName().toLowerCase(Locale.ROOT);
        if (disabledWorlds.contains(worldName)) {
            return false;
        }
        return enabledWorlds.isEmpty() || enabledWorlds.contains(worldName);
    }

    long initialDelayTicks() {
        return initialDelayTicks;
    }

    long growthStepTicks() {
        if (!worldHealthModeEnabled) {
            return growthStepTicks;
        }

        return Math.max(1L, Math.round(growthStepTicks / worldHealthGrowthSpeedMultiplier));
    }

    int blocksPerGrowthStep() {
        return blocksPerGrowthStep;
    }

    long retryDelayTicks() {
        return retryDelayTicks;
    }

    int maxRegrowthAttempts() {
        return maxRegrowthAttempts;
    }

    boolean plantDecayEnabled() {
        return plantDecayEnabled;
    }

    long plantDecayDelayTicks() {
        return plantDecayDelayTicks;
    }

    long plantDecayStepTicks() {
        return plantDecayStepTicks;
    }

    int plantDecayBlocksPerStep() {
        return plantDecayBlocksPerStep;
    }

    int plantDecayMaxBlocks() {
        return plantDecayMaxBlocks;
    }

    int requiredPlayerDistanceChunks() {
        return requiredPlayerDistanceChunks;
    }

    int ownedChunkRadius() {
        return ownedChunkRadius;
    }

    String summary() {
        return "initial-delay=" + initialDelayTicks
                + ", growth-step=" + growthStepTicks()
                + ", blocks-per-step=" + blocksPerGrowthStep
                + ", world-health=" + worldHealthModeEnabled
                + ", health-multiplier=" + worldHealthGrowthSpeedMultiplier
                + ", retry=" + retryDelayTicks
                + ", player-distance-chunks=" + requiredPlayerDistanceChunks
                + ", owned-chunk-radius=" + ownedChunkRadius
                + ", plant-decay=" + plantDecayEnabled
                + ", decay-delay=" + plantDecayDelayTicks;
    }

    private static Set<String> normalizeWorldNames(Iterable<String> names) {
        Set<String> normalized = new HashSet<>();
        for (String name : names) {
            normalized.add(name.toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static double sanitizeGrowthSpeedMultiplier(double value, Logger logger) {
        if (Double.isFinite(value) && value > 0.0D) {
            return value;
        }

        logger.warning("world-health-mode.growth-speed-multiplier must be greater than 0. Using 1.0.");
        return 1.0D;
    }

    private static Map<Material, TreeType> loadTreeTypeMap(FileConfiguration config, Logger logger, String path) {
        Map<Material, TreeType> treeTypes = new EnumMap<>(Material.class);
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return treeTypes;
        }

        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                logger.warning("Ignoring unknown material in config section " + path + ": " + key);
                continue;
            }

            String treeTypeName = section.getString(key, "").toUpperCase(Locale.ROOT);
            try {
                treeTypes.put(material, TreeType.valueOf(treeTypeName));
            } catch (IllegalArgumentException ex) {
                logger.warning("Ignoring unknown Bukkit TreeType in config section " + path + ": " + treeTypeName);
            }
        }

        return treeTypes;
    }
}
