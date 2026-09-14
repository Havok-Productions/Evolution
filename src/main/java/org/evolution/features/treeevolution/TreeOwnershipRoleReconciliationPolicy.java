package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ## Reconciles persisted ownership receipts with the live voxel role.
 *
 * <p>A coordinate is allowed to own exactly one evolved body role. This pure
 * policy is shared by Folia runtime construction and exact-volume replay so a
 * stale log receipt under a live leaf cannot be pruned and rebuilt forever.</p>
 */
final class TreeOwnershipRoleReconciliationPolicy {
    enum Role {
        WOOD,
        CANOPY
    }

    record Repair(
            String blockKey,
            Role receiptRole,
            Role liveRole,
            Role targetRole,
            boolean duplicateReceipt
    ) {
        String marker() {
            return "[LEDGER-ROLE][MOVE_TO_" + liveRole + "] key="
                    + blockKey + " receipt=" + receiptRole
                    + " live=" + liveRole
                    + " target=" + (targetRole == null
                            ? "OUTSIDE_TARGET" : targetRole)
                    + " duplicate=" + duplicateReceipt;
        }
    }

    private TreeOwnershipRoleReconciliationPolicy() {
    }

    static Optional<Repair> next(
            Set<String> evolvedLogs,
            Set<String> evolvedLeaves,
            Map<String, Role> liveRoles,
            List<PlannedTreeBlock> target
    ) {
        Map<String, Role> targetRoles = new HashMap<>();
        for (PlannedTreeBlock block : target) {
            Role role = roleOf(block.role());
            if (role != null) {
                targetRoles.put(block.key(), role);
            }
        }
        Set<String> receipts = new HashSet<>(evolvedLogs);
        receipts.addAll(evolvedLeaves);
        List<String> ordered = new ArrayList<>(receipts);
        ordered.sort(Comparator.naturalOrder());
        for (String worldKey : ordered) {
            Role live = liveRoles.get(worldKey);
            if (live == null) {
                continue;
            }
            boolean inWood = evolvedLogs.contains(worldKey);
            boolean inCanopy = evolvedLeaves.contains(worldKey);
            boolean duplicate = inWood && inCanopy;
            Role receipt = inWood && !inCanopy
                    ? Role.WOOD
                    : inCanopy && !inWood
                            ? Role.CANOPY : live;
            if (!duplicate && receipt == live) {
                continue;
            }
            return Optional.of(new Repair(
                    worldKey, receipt, live,
                    targetRoles.get(coordinateKey(worldKey)), duplicate));
        }
        return Optional.empty();
    }

    static Role roleOf(TreeBlockRole role) {
        if (role == TreeBlockRole.CANOPY) {
            return Role.CANOPY;
        }
        if (role == TreeBlockRole.TRUNK
                || role == TreeBlockRole.BRANCH
                || role == TreeBlockRole.ROOT) {
            return Role.WOOD;
        }
        return null;
    }

    private static String coordinateKey(String worldKey) {
        String[] parts = worldKey.split(":");
        int offset = parts.length - 3;
        return parts[offset] + ":" + parts[offset + 1]
                + ":" + parts[offset + 2];
    }
}
