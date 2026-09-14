package org.evolution.features.treeevolution;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * ## Loads and anonymizes representative DNA directly from a live data file.
 */
final class TreeLiveDnaFixtureLoader {
    private TreeLiveDnaFixtureLoader() {
    }

    static List<Fixture> load(Path path, int maximum) {
        YamlConfiguration yaml =
                YamlConfiguration.loadConfiguration(path.toFile());
        ConfigurationSection trees =
                yaml.getConfigurationSection("trees");
        if (trees == null) {
            return List.of();
        }
        List<TreeDna> available = new ArrayList<>();
        for (String key : trees.getKeys(false)) {
            ConfigurationSection section =
                    trees.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            TreeDna dna = TreeDna.from(section);
            if (TreePersistedFixtureExporter.usable(dna)) {
                available.add(dna);
            }
        }
        available.sort(Comparator
                .comparing((TreeDna dna) -> dna.species().name())
                .thenComparing(dna -> dna.variant().name())
                .thenComparingInt(TreeDna::age)
                .reversed());

        Map<String, TreeDna> selected = new LinkedHashMap<>();
        for (TreeDna dna : available) {
            String family = dna.species().id() + ":"
                    + dna.variant().id();
            selected.putIfAbsent(family, dna);
            if (selected.size() >= maximum) {
                break;
            }
        }
        List<Fixture> fixtures = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, TreeDna> entry
                : selected.entrySet()) {
            TreeDna anonymized =
                    TreePersistedFixtureExporter.rebase(
                            entry.getValue());
            fixtures.add(new Fixture(
                    "live-" + entry.getKey().replace(':', '-')
                            + "-" + index++,
                    anonymized));
        }
        return List.copyOf(fixtures);
    }

    record Fixture(String id, TreeDna dna) {
    }
}
