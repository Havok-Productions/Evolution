package org.evolution.features.nether;

import java.util.Set;
import org.bukkit.Material;

public final class NetherFlowerTranslationSmokeTest {
    private NetherFlowerTranslationSmokeTest() {
    }

    public static void main(String[] args) {
        Set<Material> taggedFlowers = Set.of(
                Material.DANDELION,
                Material.POPPY,
                Material.BLUE_ORCHID,
                Material.ALLIUM,
                Material.AZURE_BLUET,
                Material.RED_TULIP,
                Material.ORANGE_TULIP,
                Material.WHITE_TULIP,
                Material.PINK_TULIP,
                Material.OXEYE_DAISY,
                Material.CORNFLOWER,
                Material.LILY_OF_THE_VALLEY,
                Material.TORCHFLOWER,
                Material.SUNFLOWER,
                Material.LILAC,
                Material.ROSE_BUSH,
                Material.PEONY,
                Material.PINK_PETALS);
        NetherTerrainMimic mimic = new NetherTerrainMimic(
                taggedFlowers::contains);

        for (Material material : taggedFlowers) {
            require(mimic.canMimic(material),
                    material + " was omitted from target search.");
            require(mimic.isDirectTranslation(material),
                    material + " was routed through random style.");
            require(mimic.directReplacement(material)
                            == Material.WITHER_ROSE,
                    material + " did not become WITHER_ROSE.");
        }

        require(!mimic.canMimic(Material.WITHER_ROSE),
                "Existing wither roses must not be selected repeatedly.");
        require(mimic.directReplacement(Material.WATER) == Material.LAVA,
                "Water direct translation regressed.");
        require(mimic.directReplacement(Material.DIRT) == null,
                "Soil must remain neighbor/style translated.");

        System.out.println(
                "Nether flower translation smoke test passed: "
                        + taggedFlowers.size()
                        + " representative tagged flowers map directly to "
                        + "WITHER_ROSE; production uses Bukkit FLOWERS tag.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}