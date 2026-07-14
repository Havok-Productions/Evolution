package org.slowtrees.treeevolution;

import java.util.EnumMap;
import java.util.Map;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;

public record TreeGrowthProfile(
        TreeSpecies species,
        int minTargetHeight,
        int maxTargetHeight,
        int minBranches,
        int maxBranches,
        int minBranchLength,
        int maxBranchLength,
        int canopyRadius,
        double canopyDensity,
        double rootChance,
        double vineChance,
        double groundDetailChance
) {
    static Map<TreeSpecies, TreeGrowthProfile> loadProfiles(ConfigurationSection section) {
        Map<TreeSpecies, TreeGrowthProfile> profiles = defaults();
        if (section == null) {
            return profiles;
        }

        for (TreeSpecies species : TreeSpecies.values()) {
            ConfigurationSection speciesSection = section.getConfigurationSection(species.id());
            if (speciesSection == null) {
                continue;
            }
            TreeGrowthProfile fallback = profiles.get(species);
            profiles.put(species, new TreeGrowthProfile(
                    species,
                    Math.max(3, speciesSection.getInt("target-height-min", fallback.minTargetHeight())),
                    Math.max(4, speciesSection.getInt("target-height-max", fallback.maxTargetHeight())),
                    Math.max(0, speciesSection.getInt("branches-min", fallback.minBranches())),
                    Math.max(0, speciesSection.getInt("branches-max", fallback.maxBranches())),
                    Math.max(1, speciesSection.getInt("branch-length-min", fallback.minBranchLength())),
                    Math.max(1, speciesSection.getInt("branch-length-max", fallback.maxBranchLength())),
                    Math.max(1, speciesSection.getInt("canopy-radius", fallback.canopyRadius())),
                    clamp01(speciesSection.getDouble("canopy-density", fallback.canopyDensity())),
                    clamp01(speciesSection.getDouble("root-chance", fallback.rootChance())),
                    clamp01(speciesSection.getDouble("vine-chance", fallback.vineChance())),
                    clamp01(speciesSection.getDouble("ground-detail-chance", fallback.groundDetailChance()))
            ));
        }
        return profiles;
    }

    public double biomeGrowthFactor(Biome biome) {
        String key = biome.getKey().getKey();
        if (key.contains("forest") || key.contains("jungle") || key.contains("taiga") || key.contains("grove")) {
            return 1.15D;
        }
        if (key.contains("desert") || key.contains("badlands") || key.contains("savanna")) {
            return species == TreeSpecies.ACACIA ? 1.05D : 0.72D;
        }
        if (key.contains("swamp") && (species == TreeSpecies.OAK || species == TreeSpecies.MANGROVE)) {
            return 1.1D;
        }
        return 1.0D;
    }

    private static Map<TreeSpecies, TreeGrowthProfile> defaults() {
        Map<TreeSpecies, TreeGrowthProfile> profiles = new EnumMap<>(TreeSpecies.class);
        profiles.put(TreeSpecies.OAK, new TreeGrowthProfile(TreeSpecies.OAK, 6, 13, 2, 6, 2, 5, 3, 0.74D, 0.32D, 0.08D, 0.28D));
        profiles.put(TreeSpecies.BIRCH, new TreeGrowthProfile(TreeSpecies.BIRCH, 7, 15, 0, 3, 1, 3, 2, 0.58D, 0.08D, 0.02D, 0.16D));
        profiles.put(TreeSpecies.SPRUCE, new TreeGrowthProfile(TreeSpecies.SPRUCE, 8, 18, 3, 8, 1, 4, 3, 0.68D, 0.16D, 0.03D, 0.22D));
        profiles.put(TreeSpecies.JUNGLE, new TreeGrowthProfile(TreeSpecies.JUNGLE, 10, 22, 2, 7, 2, 5, 4, 0.72D, 0.28D, 0.34D, 0.30D));
        profiles.put(TreeSpecies.ACACIA, new TreeGrowthProfile(TreeSpecies.ACACIA, 6, 12, 2, 5, 2, 5, 3, 0.54D, 0.18D, 0.02D, 0.12D));
        profiles.put(TreeSpecies.DARK_OAK, new TreeGrowthProfile(TreeSpecies.DARK_OAK, 7, 14, 3, 7, 2, 5, 4, 0.80D, 0.34D, 0.06D, 0.26D));
        profiles.put(TreeSpecies.MANGROVE, new TreeGrowthProfile(TreeSpecies.MANGROVE, 7, 16, 2, 6, 2, 5, 3, 0.70D, 0.45D, 0.20D, 0.20D));
        profiles.put(TreeSpecies.CHERRY, new TreeGrowthProfile(TreeSpecies.CHERRY, 6, 13, 2, 6, 2, 5, 4, 0.78D, 0.16D, 0.03D, 0.24D));
        return profiles;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
