package org.evolution.features.treeevolution;

import java.util.EnumSet;
import java.util.Set;

/**
 * Coordinate-level provenance for one block in the immutable tree target.
 *
 * <p>## Constructor smoke tags explain <em>when</em> a block was placed.
 * These labels explain <em>which planner augment requested it</em>. Keeping
 * both labels on an action lets a malformed limb be traced to either target
 * geometry or runtime hierarchy without guessing from its material.</p>
 */
enum TreePlacementAugment {
    UNCLASSIFIED(EnumSet.allOf(TreeBlockRole.class),
            "legacy or test-only block without planner provenance"),
    TRUNK_FRAME(EnumSet.of(TreeBlockRole.TRUNK),
            "species/stage trunk frame"),
    BRANCH_PROCEDURAL_PATH(EnumSet.of(TreeBlockRole.BRANCH),
            "ordinary seeded branch path"),
    BRANCH_SIGNATURE_PATH(EnumSet.of(TreeBlockRole.BRANCH),
            "species and variant signature limb"),
    BRANCH_LAYER_PATH(EnumSet.of(TreeBlockRole.BRANCH),
            "layered or landmark branch tier"),
    BRANCH_REINFORCEMENT(EnumSet.of(TreeBlockRole.BRANCH),
            "thick support beside a primary limb"),
    CANOPY_SPECIES_CROWN(EnumSet.of(TreeBlockRole.CANOPY),
            "general species-shaped crown volume"),
    CANOPY_FANCY_CLOUD(EnumSet.of(TreeBlockRole.CANOPY),
            "small/medium fluffy crown lobe"),
    CANOPY_SPRUCE_ENVELOPE(EnumSet.of(TreeBlockRole.CANOPY),
            "contiguous tapered spruce envelope"),
    CANOPY_ACACIA_UMBRELLA_PAD(EnumSet.of(TreeBlockRole.CANOPY),
            "layered acacia umbrella lobe"),
    CANOPY_ACACIA_TIP_THROAT(EnumSet.of(TreeBlockRole.CANOPY),
            "leaf throat masking an acacia fork tip"),
    CANOPY_ACACIA_LOWER_TUFT(EnumSet.of(TreeBlockRole.CANOPY),
            "irregular lower acacia crown tuft"),
    CANOPY_ACACIA_WINDSWEPT_FRINGE(EnumSet.of(TreeBlockRole.CANOPY),
            "wind-facing acacia crown extension"),
    CANOPY_BRANCH_INTEGRATION(EnumSet.of(TreeBlockRole.CANOPY),
            "leaf bridge integrating a limb into its crown"),
    CANOPY_TIP_ANCHOR(EnumSet.of(TreeBlockRole.CANOPY),
            "minimum terminal leaf contact"),
    ROOT_ARCHITECTURE(EnumSet.of(TreeBlockRole.ROOT),
            "old-growth root architecture"),
    VINE_DETAIL(EnumSet.of(TreeBlockRole.VINE),
            "species vine detail"),
    GROUND_ECOLOGY(EnumSet.of(
            TreeBlockRole.GROUND_DETAIL,
            TreeBlockRole.FALLEN_LOG,
            TreeBlockRole.SAPLING),
            "tree-adjacent ecological detail");

    private final Set<TreeBlockRole> expectedRoles;
    private final String contract;

    TreePlacementAugment(
            Set<TreeBlockRole> expectedRoles,
            String contract
    ) {
        this.expectedRoles = Set.copyOf(expectedRoles);
        this.contract = contract;
    }

    boolean accepts(TreeBlockRole role) {
        return expectedRoles.contains(role);
    }

    String expectedRoleLabel() {
        return expectedRoles.toString();
    }

    String contract() {
        return contract;
    }

    String marker() {
        return "[AUGMENT=" + name() + "][EXPECTS="
                + expectedRoleLabel() + "]";
    }
}
