package org.evolution.features.treeevolution;

final class TreeLiveTerminalPolicy {
    private TreeLiveTerminalPolicy() {
    }

    static Decision classify(
            boolean ownershipComplete,
            TreeBlockRole plannedRole,
            int heightAboveBase,
            int trunkDistance,
            int woodNeighbors) {
        if (!ownershipComplete) {
            return Decision.WAIT_OWNERSHIP;
        }
        if (heightAboveBase < 2 || trunkDistance <= 1) {
            return Decision.KEEP_TRUNK_CORE;
        }
        if (plannedRole == TreeBlockRole.TRUNK
                || plannedRole == TreeBlockRole.BRANCH) {
            return Decision.KEEP_PLANNED_WOOD;
        }
        if (woodNeighbors > 1) {
            return Decision.KEEP_INTERNAL_WOOD;
        }
        // ## Incidental leaves do not legitimize terminal wood absent from the deterministic branch plan.
        return Decision.PRUNE_UNPLANNED_BARE_TERMINAL;
    }

    enum Decision {
        WAIT_OWNERSHIP,
        KEEP_TRUNK_CORE,
        KEEP_PLANNED_WOOD,
        KEEP_INTERNAL_WOOD,
        PRUNE_UNPLANNED_BARE_TERMINAL
    }
}