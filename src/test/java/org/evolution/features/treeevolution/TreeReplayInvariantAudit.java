package org.evolution.features.treeevolution;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Cell;
import org.evolution.features.treeevolution.TreeConstructionReplayWorld.Ownership;

/**
 * ## Lightweight independent invariant check run after every replay action.
 */
final class TreeReplayInvariantAudit {
    private TreeReplayInvariantAudit() {
    }

    static Report inspect(
            TreeDna dna,
            TreePlan plan,
            TreeConstructionReplayWorld world
    ) {
        Map<String, Cell> cells = world.cells();
        Set<String> wood = new HashSet<>();
        int neighborCells = 0;
        int evolvedOutsideTarget = 0;
        int ownershipRoleMismatches = 0;
        for (Map.Entry<String, Cell> entry : cells.entrySet()) {
            Cell cell = entry.getValue();
            if (cell.ownership() == Ownership.NEIGHBOR) {
                neighborCells++;
                continue;
            }
            if (cell.role() == TreeBlockRole.TRUNK
                    || cell.role() == TreeBlockRole.BRANCH) {
                wood.add(entry.getKey());
            }
            if (cell.ownership() == Ownership.EVOLVED
                    && isTreeBody(cell.role())
                    && !plan.blocksByKey().containsKey(entry.getKey())
                    && !dna.evolvedShapeLogs().contains(
                            worldKey(dna, entry.getKey()))
                    && !dna.evolvedShapeLeaves().contains(
                            worldKey(dna, entry.getKey()))) {
                evolvedOutsideTarget++;
            }
            if (cell.ownership() == Ownership.EVOLVED) {
                String receipt = worldKey(dna, entry.getKey());
                boolean liveCanopy = cell.role() == TreeBlockRole.CANOPY;
                boolean ownedAsCanopy =
                        dna.evolvedShapeLeaves().contains(receipt);
                boolean ownedAsWood =
                        dna.evolvedShapeLogs().contains(receipt);
                if ((liveCanopy && (!ownedAsCanopy || ownedAsWood))
                        || (!liveCanopy && isTreeBody(cell.role())
                                && (!ownedAsWood || ownedAsCanopy))) {
                    ownershipRoleMismatches++;
                }
            }
        }

        Set<String> connected = connectedWood(dna, wood);
        int disconnectedWood = Math.max(0,
                wood.size() - connected.size());
        return new Report(
                disconnectedWood,
                evolvedOutsideTarget,
                ownershipRoleMismatches,
                neighborCells,
                world.physicalMutationCount());
    }

    private static Set<String> connectedWood(
            TreeDna dna, Set<String> wood) {
        Set<String> connected = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        for (String key : wood) {
            Coordinate coordinate = coordinate(key);
            if (coordinate.y() == dna.baseY()
                    && Math.abs(coordinate.x() - dna.baseX()) <= 1
                    && Math.abs(coordinate.z() - dna.baseZ()) <= 1) {
                connected.add(key);
                pending.addLast(key);
            }
        }
        while (!pending.isEmpty()) {
            Coordinate current = coordinate(pending.removeFirst());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        String next = key(
                                current.x() + dx,
                                current.y() + dy,
                                current.z() + dz);
                        if (wood.contains(next)
                                && connected.add(next)) {
                            pending.addLast(next);
                        }
                    }
                }
            }
        }
        return connected;
    }

    private static boolean isTreeBody(TreeBlockRole role) {
        return role == TreeBlockRole.TRUNK
                || role == TreeBlockRole.BRANCH
                || role == TreeBlockRole.CANOPY;
    }

    private static String worldKey(TreeDna dna, String key) {
        return dna.worldId() + ":" + key;
    }

    private static Coordinate coordinate(String value) {
        String[] parts = value.split(":");
        int offset = parts.length - 3;
        return new Coordinate(
                Integer.parseInt(parts[offset]),
                Integer.parseInt(parts[offset + 1]),
                Integer.parseInt(parts[offset + 2]));
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    record Report(
            int disconnectedWood,
            int untrackedEvolvedOutsideTarget,
            int ownershipRoleMismatches,
            int protectedNeighborCells,
            int physicalMutations
    ) {
        boolean passed() {
            return disconnectedWood == 0
                    && untrackedEvolvedOutsideTarget == 0
                    && ownershipRoleMismatches == 0;
        }

        boolean passed(int allowedInheritedDisconnectedWood) {
            return passed(
                    allowedInheritedDisconnectedWood,
                    ownershipRoleMismatches);
        }

        boolean passed(
                int allowedInheritedDisconnectedWood,
                int allowedOwnershipRoleMismatches) {
            return disconnectedWood
                            <= allowedInheritedDisconnectedWood
                    && untrackedEvolvedOutsideTarget == 0
                    && ownershipRoleMismatches
                            <= allowedOwnershipRoleMismatches;
        }

        String summary() {
            return "disconnectedWood=" + disconnectedWood
                    + " untrackedExtra="
                    + untrackedEvolvedOutsideTarget
                    + " ownershipRoleMismatch="
                    + ownershipRoleMismatches
                    + " protectedNeighbors=" + protectedNeighborCells
                    + " mutations=" + physicalMutations;
        }
    }

    private record Coordinate(int x, int y, int z) {
    }
}
