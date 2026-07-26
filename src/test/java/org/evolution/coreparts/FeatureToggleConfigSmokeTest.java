package org.evolution.coreparts;

import java.io.File;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;

public final class FeatureToggleConfigSmokeTest {
    private static final List<String> GAMEPLAY_TOGGLES = List.of(
            "plant-regrowth.enabled",
            "plant-decay.enabled",
            "meadow-growth.enabled",
            "ecology-evolution.enabled",
            "ecology-evolution.bamboo.enabled",
            "tree-evolution.enabled",
            "tree-evolution.reproduction.enabled",
            "shaped-portals.enabled",
            "nether-corruption.enabled",
            "wind.enabled",
            "wind.leaf-particles.enabled",
            "wind.leaf-litter.enabled",
            "puddles.enabled",
            "waves.enabled"
    );

    private FeatureToggleConfigSmokeTest() {
    }

    public static void main(String[] args) {
        File configFile = new File("src/main/resources/config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        for (String path : GAMEPLAY_TOGGLES) {
            require(config.isBoolean(path), "Missing boolean feature switch: " + path);
            require(config.getBoolean(path), "Default feature switch must remain enabled: " + path);
        }

        System.out.println("Feature toggle config smoke test passed: "
                + GAMEPLAY_TOGGLES.size() + " gameplay switches.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
