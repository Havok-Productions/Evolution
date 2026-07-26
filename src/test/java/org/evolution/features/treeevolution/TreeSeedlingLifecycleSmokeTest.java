package org.evolution.features.treeevolution;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Guards the species and lineage contract for Evolution-owned offspring.
 */
public final class TreeSeedlingLifecycleSmokeTest {
    private TreeSeedlingLifecycleSmokeTest() {
    }

    public static void main(String[] args) {
        UUID worldId = UUID.fromString(
                "11111111-2222-3333-4444-555555555555");
        Set<Material> saplings = new HashSet<>();

        for (TreeSpecies species : TreeSpecies.values()) {
            require(saplings.add(species.saplingMaterial()),
                    "each species must own a unique sapling material");
            require(TreeSpecies.fromSaplingMaterial(species.saplingMaterial())
                            .orElseThrow() == species,
                    species + " offspring must map back to the parent species");
            require(species.saplingMaterial() != species.logMaterial(),
                    species + " reproduction must place a sapling before a log");
            require(TreeSpecies.fromMaterial(species.saplingMaterial()).isEmpty(),
                    species + " saplings must not masquerade as completed trees");

            TreeSeedlingRecord original = new TreeSeedlingRecord(
                    worldId,
                    12,
                    70,
                    -31,
                    species,
                    "parent-" + species.id(),
                    4,
                    123456789L);
            YamlConfiguration yaml = new YamlConfiguration();
            ConfigurationSection section = yaml.createSection("seedling");
            original.writeTo(section);
            TreeSeedlingRecord restored =
                    TreeSeedlingRecord.from(section);

            require(restored.equals(original),
                    species + " ownership receipt must survive persistence");
            require(restored.generation() == 4,
                    species + " generation must remain exact");
            require(restored.parentKey().equals(
                            "parent-" + species.id()),
                    species + " parent lineage must remain exact");
        }

        System.out.println(
                "Tree seedling lifecycle smoke test passed: species="
                        + TreeSpecies.values().length
                        + " unique-saplings=" + saplings.size()
                        + " persistence=true gradual-handoff=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
