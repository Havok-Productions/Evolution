package com.rajbe.slowtrees;

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

final class SlowTreesConfig {
    private final long initialDelayTicks;
    private final long growthStepTicks;
    private final int blocksPerGrowthStep;
    private final long retryDelayTicks;
    private final int maxRegrowthAttempts;
    private final int requiredPlayerDistanceChunks;
    private final int ownedChunkRadius;
    private final Set<String> enabledWorlds;
    private final Set<String> disabledWorlds;
    private final Set<Material> replaceableMaterials;
    private final Map<Material, TreeType> treeTypes;

    private SlowTreesConfig(
            long initialDelayTicks,
            long growthStepTicks,
            int blocksPerGrowthStep,
            long retryDelayTicks,
            int maxRegrowthAttempts,
            int requiredPlayerDistanceChunks,
            int ownedChunkRadius,
            Set<String> enabledWorlds,
            Set<String> disabledWorlds,
            Set<Material> replaceableMaterials,
            Map<Material, TreeType> treeTypes
    ) {
        this.initialDelayTicks = initialDelayTicks;
        this.growthStepTicks = growthStepTicks;
        this.blocksPerGrowthStep = blocksPerGrowthStep;
        this.retryDelayTicks = retryDelayTicks;
        this.maxRegrowthAttempts = maxRegrowthAttempts;
        this.requiredPlayerDistanceChunks = requiredPlayerDistanceChunks;
        this.ownedChunkRadius = ownedChunkRadius;
        this.enabledWorlds = enabledWorlds;
        this.disabledWorlds = disabledWorlds;
        this.replaceableMaterials = replaceableMaterials;
        this.treeTypes = treeTypes;
    }

    static SlowTreesConfig load(SlowTreesPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        Logger logger = plugin.getLogger();

        Map<Material, TreeType> treeTypes = new EnumMap<>(Material.class);
        ConfigurationSection section = config.getConfigurationSection("tree-types");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    logger.warning("Ignoring unknown tree log material in config: " + key);
                    continue;
                }

                String treeTypeName = section.getString(key, "").toUpperCase(Locale.ROOT);
                try {
                    treeTypes.put(material, TreeType.valueOf(treeTypeName));
                } catch (IllegalArgumentException ex) {
                    logger.warning("Ignoring unknown Bukkit TreeType in config: " + treeTypeName);
                }
            }
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

        return new SlowTreesConfig(
                Math.max(1L, config.getLong("initial-delay-ticks", 36000L)),
                Math.max(1L, config.getLong("growth-step-ticks", 20L)),
                Math.max(1, config.getInt("blocks-per-growth-step", 4)),
                Math.max(1L, config.getLong("retry-delay-ticks", 6000L)),
                Math.max(0, config.getInt("max-regrowth-attempts", 0)),
                Math.max(0, config.getInt("required-player-distance-chunks", 8)),
                Math.max(0, config.getInt("owned-chunk-radius", 1)),
                normalizeWorldNames(config.getStringList("enabled-worlds")),
                normalizeWorldNames(config.getStringList("disabled-worlds")),
                Collections.unmodifiableSet(replaceableMaterials),
                Collections.unmodifiableMap(new HashMap<>(treeTypes))
        );
    }

    Optional<TreeType> treeTypeFor(Material material) {
        return Optional.ofNullable(treeTypes.get(material));
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
        return growthStepTicks;
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

    int requiredPlayerDistanceChunks() {
        return requiredPlayerDistanceChunks;
    }

    int ownedChunkRadius() {
        return ownedChunkRadius;
    }

    private static Set<String> normalizeWorldNames(Iterable<String> names) {
        Set<String> normalized = new HashSet<>();
        for (String name : names) {
            normalized.add(name.toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(normalized);
    }
}
