package org.evolution.features.treeevolution;

import java.util.List;
import org.bukkit.Material;

/**
 * ## An anonymized local voxel neighborhood attached to captured tree DNA.
 */
record TreeCapturedEnvironmentFixture(
        List<Cell> cells,
        List<UnreadableArea> unreadableAreas
) {
    static TreeCapturedEnvironmentFixture empty() {
        return new TreeCapturedEnvironmentFixture(List.of(), List.of());
    }

    boolean present() {
        return !cells.isEmpty() || !unreadableAreas.isEmpty();
    }

    enum Category {
        SOURCE,
        EVOLVED,
        PLANNED_UNOWNED,
        NEIGHBOR,
        ENVIRONMENT,
        AIR_OVERRIDE
    }

    record Cell(
            int relativeX,
            int relativeY,
            int relativeZ,
            Material material,
            Category category,
            TreeBlockRole role,
            boolean natural,
            boolean replaceable,
            boolean playerPlaced,
            boolean persistent
    ) {
        Cell(
                int relativeX,
                int relativeY,
                int relativeZ,
                Material material,
                Category category,
                TreeBlockRole role,
                boolean natural,
                boolean replaceable,
                boolean playerPlaced
        ) {
            this(relativeX, relativeY, relativeZ, material, category,
                    role, natural, replaceable, playerPlaced, false);
        }
    }

    record UnreadableArea(
            int minimumRelativeX,
            int minimumRelativeZ,
            int maximumRelativeX,
            int maximumRelativeZ,
            TreeSimulatedMinecraftEnvironment.Gate gate
    ) {
    }
}
