package org.evolution.features.treeevolution;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.evolution.coreparts.EvolutionPlugin;

final class TreeProfileSampleStore {
    private static final int MAX_SAMPLES_PER_SPECIES = 64;

    Map<TreeSpecies, List<TreeProfileSample>> load(EvolutionPlugin plugin) {
        File file = file(plugin);
        if (!file.exists()) {
            return Map.of();
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection samples = yaml.getConfigurationSection("samples");
        if (samples == null) {
            return Map.of();
        }

        Map<TreeSpecies, List<TreeProfileSample>> bySpecies = new EnumMap<>(TreeSpecies.class);
        for (String key : samples.getKeys(false)) {
            ConfigurationSection section = samples.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            TreeSpecies.fromId(section.getString("species", "unknown")).ifPresent(species -> {
                TreeProfileSample sample = sampleFromSection(key, species, section);
                bySpecies.computeIfAbsent(species, ignored -> new ArrayList<>()).add(sample);
            });
        }
        return freeze(bySpecies);
    }

    Map<TreeSpecies, List<TreeProfileSample>> saveFromScan(EvolutionPlugin plugin, StructureScanResult result) {
        Map<TreeSpecies, List<TreeProfileSample>> bySpecies = new EnumMap<>(TreeSpecies.class);
        for (WorldgenScanSummary archive : result.worldgenArchives()) {
            for (WorldgenProfileSuggestion suggestion : archive.profileSuggestions()) {
                TreeSpecies.fromId(suggestion.species()).ifPresent(species -> addSample(bySpecies, species, suggestion));
            }
        }
        save(plugin, bySpecies);
        return freeze(bySpecies);
    }

    private void addSample(Map<TreeSpecies, List<TreeProfileSample>> bySpecies, TreeSpecies species, WorldgenProfileSuggestion suggestion) {
        List<TreeProfileSample> samples = bySpecies.computeIfAbsent(species, ignored -> new ArrayList<>());
        if (samples.size() >= MAX_SAMPLES_PER_SPECIES) {
            return;
        }
        String id = species.id() + "." + sanitizeId(suggestion.sourceFile());
        samples.add(new TreeProfileSample(
                id,
                suggestion.sourceFile(),
                stringValue(suggestion.generatedProfile().get("species-source"), "scan"),
                suggestion.trunkPlacer(),
                suggestion.foliagePlacer(),
                new TreeGrowthProfile(
                        species,
                        Math.max(3, suggestion.targetHeightMin()),
                        Math.max(4, suggestion.targetHeightMax()),
                        Math.max(0, suggestion.branchesMin()),
                        Math.max(0, suggestion.branchesMax()),
                        Math.max(1, suggestion.branchLengthMin()),
                        Math.max(1, suggestion.branchLengthMax()),
                        Math.max(1, suggestion.canopyRadius()),
                        clamp01(suggestion.canopyDensity()),
                        clamp01(suggestion.rootChance()),
                        clamp01(suggestion.vineChance()),
                        clamp01(suggestion.groundDetailChance())
                )
        ));
    }

    private TreeProfileSample sampleFromSection(String id, TreeSpecies species, ConfigurationSection section) {
        ConfigurationSection profileSection = section.getConfigurationSection("profile");
        TreeGrowthProfile fallback = TreeGrowthProfile.loadProfiles(null).get(species);
        TreeGrowthProfile profile = profileSection == null ? fallback : new TreeGrowthProfile(
                species,
                Math.max(3, profileSection.getInt("target-height-min", fallback.minTargetHeight())),
                Math.max(4, profileSection.getInt("target-height-max", fallback.maxTargetHeight())),
                Math.max(0, profileSection.getInt("branches-min", fallback.minBranches())),
                Math.max(0, profileSection.getInt("branches-max", fallback.maxBranches())),
                Math.max(1, profileSection.getInt("branch-length-min", fallback.minBranchLength())),
                Math.max(1, profileSection.getInt("branch-length-max", fallback.maxBranchLength())),
                Math.max(1, profileSection.getInt("canopy-radius", fallback.canopyRadius())),
                clamp01(profileSection.getDouble("canopy-density", fallback.canopyDensity())),
                clamp01(profileSection.getDouble("root-chance", fallback.rootChance())),
                clamp01(profileSection.getDouble("vine-chance", fallback.vineChance())),
                clamp01(profileSection.getDouble("ground-detail-chance", fallback.groundDetailChance()))
        );
        return new TreeProfileSample(
                id,
                section.getString("source", "unknown"),
                section.getString("species-source", "stored"),
                section.getString("trunk-placer", "unknown"),
                section.getString("foliage-placer", "unknown"),
                profile
        );
    }

    private void save(EvolutionPlugin plugin, Map<TreeSpecies, List<TreeProfileSample>> bySpecies) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("notes", "## Generated profile samples derived from scan measurements only. These are not copied layouts or source JSON.");
        Map<String, Integer> counts = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<TreeSpecies, List<TreeProfileSample>> entry : bySpecies.entrySet()) {
            counts.put(entry.getKey().id(), entry.getValue().size());
            for (TreeProfileSample sample : entry.getValue()) {
                writeSample(yaml, "samples." + index++, entry.getKey(), sample);
            }
        }
        yaml.set("counts-by-species", counts);

        try {
            File target = file(plugin);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(target);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Could not save tree profile samples.", ex);
        }
    }

    private void writeSample(YamlConfiguration yaml, String path, TreeSpecies species, TreeProfileSample sample) {
        yaml.set(path + ".id", sample.id());
        yaml.set(path + ".species", species.id());
        yaml.set(path + ".source", sample.sourceFile());
        yaml.set(path + ".species-source", sample.speciesSource());
        yaml.set(path + ".trunk-placer", sample.trunkPlacer());
        yaml.set(path + ".foliage-placer", sample.foliagePlacer());
        yaml.set(path + ".profile.target-height-min", sample.profile().minTargetHeight());
        yaml.set(path + ".profile.target-height-max", sample.profile().maxTargetHeight());
        yaml.set(path + ".profile.branches-min", sample.profile().minBranches());
        yaml.set(path + ".profile.branches-max", sample.profile().maxBranches());
        yaml.set(path + ".profile.branch-length-min", sample.profile().minBranchLength());
        yaml.set(path + ".profile.branch-length-max", sample.profile().maxBranchLength());
        yaml.set(path + ".profile.canopy-radius", sample.profile().canopyRadius());
        yaml.set(path + ".profile.canopy-density", sample.profile().canopyDensity());
        yaml.set(path + ".profile.root-chance", sample.profile().rootChance());
        yaml.set(path + ".profile.vine-chance", sample.profile().vineChance());
        yaml.set(path + ".profile.ground-detail-chance", sample.profile().groundDetailChance());
    }

    private Map<TreeSpecies, List<TreeProfileSample>> freeze(Map<TreeSpecies, List<TreeProfileSample>> bySpecies) {
        Map<TreeSpecies, List<TreeProfileSample>> frozen = new EnumMap<>(TreeSpecies.class);
        for (Map.Entry<TreeSpecies, List<TreeProfileSample>> entry : bySpecies.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(frozen);
    }

    private File file(EvolutionPlugin plugin) {
        return new File(plugin.getDataFolder(), "tree-profile-samples.yml");
    }

    private String sanitizeId(String source) {
        String clean = source.toLowerCase(Locale.ROOT)
                .replace(".json", "")
                .replace('\\', '/')
                .replaceAll("[^a-z0-9_/.-]", "_")
                .replace('/', '.')
                .replaceAll("\\.+", ".");
        if (clean.length() > 96) {
            clean = clean.substring(clean.length() - 96);
        }
        return clean;
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
