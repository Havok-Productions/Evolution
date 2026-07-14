package org.slowtrees.treeevolution;

import java.util.Arrays;
import java.util.Optional;
import org.bukkit.Material;

public enum TreeSpecies {
    OAK("oak", Material.OAK_LOG, Material.OAK_LEAVES, Material.OAK_WOOD, Material.OAK_SAPLING, Material.VINE),
    BIRCH("birch", Material.BIRCH_LOG, Material.BIRCH_LEAVES, Material.BIRCH_WOOD, Material.BIRCH_SAPLING, Material.VINE),
    SPRUCE("spruce", Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, Material.SPRUCE_WOOD, Material.SPRUCE_SAPLING, Material.VINE),
    JUNGLE("jungle", Material.JUNGLE_LOG, Material.JUNGLE_LEAVES, Material.JUNGLE_WOOD, Material.JUNGLE_SAPLING, Material.VINE),
    ACACIA("acacia", Material.ACACIA_LOG, Material.ACACIA_LEAVES, Material.ACACIA_WOOD, Material.ACACIA_SAPLING, Material.VINE),
    DARK_OAK("dark_oak", Material.DARK_OAK_LOG, Material.DARK_OAK_LEAVES, Material.DARK_OAK_WOOD, Material.DARK_OAK_SAPLING, Material.VINE),
    MANGROVE("mangrove", Material.MANGROVE_LOG, Material.MANGROVE_LEAVES, Material.MANGROVE_WOOD, Material.MANGROVE_PROPAGULE, Material.VINE),
    CHERRY("cherry", Material.CHERRY_LOG, Material.CHERRY_LEAVES, Material.CHERRY_WOOD, Material.CHERRY_SAPLING, Material.VINE);

    private final String id;
    private final Material logMaterial;
    private final Material leafMaterial;
    private final Material woodMaterial;
    private final Material saplingMaterial;
    private final Material vineMaterial;

    TreeSpecies(String id, Material logMaterial, Material leafMaterial, Material woodMaterial, Material saplingMaterial, Material vineMaterial) {
        this.id = id;
        this.logMaterial = logMaterial;
        this.leafMaterial = leafMaterial;
        this.woodMaterial = woodMaterial;
        this.saplingMaterial = saplingMaterial;
        this.vineMaterial = vineMaterial;
    }

    public String id() {
        return id;
    }

    public Material logMaterial() {
        return logMaterial;
    }

    public Material leafMaterial() {
        return leafMaterial;
    }

    public Material woodMaterial() {
        return woodMaterial;
    }

    public Material saplingMaterial() {
        return saplingMaterial;
    }

    public Material vineMaterial() {
        return vineMaterial;
    }

    public static Optional<TreeSpecies> fromMaterial(Material material) {
        return Arrays.stream(values())
                .filter(species -> species.logMaterial == material || species.leafMaterial == material || species.woodMaterial == material)
                .findFirst();
    }

    public static Optional<TreeSpecies> fromId(String id) {
        return Arrays.stream(values())
                .filter(species -> species.id.equalsIgnoreCase(id) || species.name().equalsIgnoreCase(id))
                .findFirst();
    }
}
