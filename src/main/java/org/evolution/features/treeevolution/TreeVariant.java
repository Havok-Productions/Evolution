package org.evolution.features.treeevolution;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * ## Persistent architecture family inside a wood species.
 *
 * <p>Species selects materials. Variant preserves the source tree's recognizable
 * construction pattern. Personality and seed only vary a tree inside that
 * family.</p>
 */
enum TreeVariant {
    OAK_STANDARD(TreeSpecies.OAK),
    OAK_FANCY(TreeSpecies.OAK),
    OAK_TALL(TreeSpecies.OAK),
    OAK_BROAD(TreeSpecies.OAK),

    BIRCH_STANDARD(TreeSpecies.BIRCH),
    BIRCH_TALL(TreeSpecies.BIRCH),

    SPRUCE_CLASSIC(TreeSpecies.SPRUCE),
    SPRUCE_PINE(TreeSpecies.SPRUCE),
    SPRUCE_MEGA(TreeSpecies.SPRUCE),
    SPRUCE_MEGA_PINE(TreeSpecies.SPRUCE),

    JUNGLE_BUSH(TreeSpecies.JUNGLE),
    JUNGLE_SMALL(TreeSpecies.JUNGLE),
    JUNGLE_LARGE(TreeSpecies.JUNGLE),
    JUNGLE_MEGA(TreeSpecies.JUNGLE),

    ACACIA_SINGLE_FORK(TreeSpecies.ACACIA),
    ACACIA_MULTI_FORK(TreeSpecies.ACACIA),
    ACACIA_WINDSWEPT(TreeSpecies.ACACIA),

    DARK_OAK_STANDARD(TreeSpecies.DARK_OAK),
    DARK_OAK_BROAD(TreeSpecies.DARK_OAK),
    DARK_OAK_TALL(TreeSpecies.DARK_OAK),

    MANGROVE_SHORT(TreeSpecies.MANGROVE),
    MANGROVE_TALL(TreeSpecies.MANGROVE),
    MANGROVE_SPREADING(TreeSpecies.MANGROVE),

    CHERRY_COMPACT(TreeSpecies.CHERRY),
    CHERRY_BROAD(TreeSpecies.CHERRY),
    CHERRY_LAYERED(TreeSpecies.CHERRY);

    private final TreeSpecies species;

    TreeVariant(TreeSpecies species) {
        this.species = species;
    }

    TreeSpecies species() {
        return species;
    }

    String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Optional<TreeVariant> fromId(
            TreeSpecies species,
            String value
    ) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_');
        return Arrays.stream(values())
                .filter(variant -> variant.species == species)
                .filter(variant -> variant.name().equals(normalized))
                .findFirst();
    }

    static TreeVariant defaultFor(TreeSpecies species) {
        return switch (species) {
            case OAK -> OAK_STANDARD;
            case BIRCH -> BIRCH_STANDARD;
            case SPRUCE -> SPRUCE_CLASSIC;
            case JUNGLE -> JUNGLE_SMALL;
            case ACACIA -> ACACIA_SINGLE_FORK;
            case DARK_OAK -> DARK_OAK_STANDARD;
            case MANGROVE -> MANGROVE_TALL;
            case CHERRY -> CHERRY_BROAD;
        };
    }
}
