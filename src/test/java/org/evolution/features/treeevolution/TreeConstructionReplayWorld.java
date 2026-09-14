package org.evolution.features.treeevolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Material;

/**
 * Deterministic block/provenance store used by the constructor replay.
 */
final class TreeConstructionReplayWorld {
    enum Ownership {
        SOURCE,
        EVOLVED,
        NEIGHBOR
    }

    record Cell(
            Material material,
            TreeBlockRole role,
            Ownership ownership,
            boolean persistent
    ) {
        Cell(Material material, TreeBlockRole role, Ownership ownership) {
            this(material, role, ownership, false);
        }
    }

    /**
     * ## One lossless XYZ+time delta. Replaying these entries over the seeded
     * voxel map reconstructs every intermediate constructor state exactly.
     */
    record Mutation(
            int sequence,
            String key,
            Cell before,
            Cell after,
            boolean physical,
            String reason
    ) {
    }

    private final Map<String, Cell> cells = new LinkedHashMap<>();
    private final Map<String, Integer> physicalChanges = new LinkedHashMap<>();
    private final List<String> mutations = new ArrayList<>();
    private final List<Mutation> mutationTimeline = new ArrayList<>();
    private final TreeSimulatedMinecraftEnvironment environment;

    TreeConstructionReplayWorld() {
        this(TreeSimulatedMinecraftEnvironment.permissive());
    }

    TreeConstructionReplayWorld(
            TreeSimulatedMinecraftEnvironment environment
    ) {
        this.environment = Objects.requireNonNull(environment);
    }

    TreeConstructionReplayWorld copy() {
        // ## A restart copies block state while keeping the same external
        // world timeline. Folia ownership and weather do not reset when a
        // plugin reloads its DNA.
        TreeConstructionReplayWorld copy =
                new TreeConstructionReplayWorld(environment);
        copy.cells.putAll(cells);
        copy.physicalChanges.putAll(physicalChanges);
        copy.mutations.addAll(mutations);
        copy.mutationTimeline.addAll(mutationTimeline);
        return copy;
    }

    Cell cell(String key) {
        return cells.get(key);
    }

    Map<String, Cell> cells() {
        return Map.copyOf(cells);
    }

    void seed(String key, Material material, TreeBlockRole role,
            Ownership ownership) {
        seed(key, material, role, ownership, false);
    }

    void seed(String key, Material material, TreeBlockRole role,
            Ownership ownership, boolean persistent) {
        cells.put(key, new Cell(
                material, role, ownership, persistent));
    }

    void clearSeed(String key) {
        cells.remove(key);
    }

    boolean place(String key, Material material, TreeBlockRole role,
            Ownership ownership, String reason) {
        Cell current = cells.get(key);
        if (current != null && current.material() == material
                && current.role() == role
                && current.ownership() == ownership) {
            return false;
        }
        requireMutable(key, current);
        TreeSimulatedMinecraftEnvironment.GateDecision gate =
                environment.canPlace(key, material, role, current);
        if (!gate.allowed()) {
            return false;
        }
        Cell replacement = new Cell(material, role, ownership, false);
        cells.put(key, replacement);
        environment.recordAllowedMutation(
                "place", key, material.name() + "/" + role);
        changed(key, current, replacement, reason + " -> " + material);
        return true;
    }

    boolean remove(String key, String reason) {
        Cell current = cells.get(key);
        if (current == null) {
            return false;
        }
        requireMutable(key, current);
        TreeSimulatedMinecraftEnvironment.GateDecision gate =
                environment.canRemove(key, current);
        if (!gate.allowed()) {
            return false;
        }
        cells.remove(key);
        environment.recordAllowedMutation(
                "remove", key, current.material().name());
        changed(key, current, null, reason + " -> AIR");
        return true;
    }

    boolean adoptSourceLeaf(String key, TreeBlockRole role, String reason) {
        Cell current = cells.get(key);
        if (current == null || current.ownership() != Ownership.SOURCE
                || !current.material().name().endsWith("_LEAVES")) {
            return false;
        }
        Cell replacement = new Cell(
                current.material(), role, Ownership.EVOLVED,
                current.persistent());
        cells.put(key, replacement);
        mutations.add(reason + " adopt " + key);
        mutationTimeline.add(new Mutation(
                mutationTimeline.size(), key, current, replacement,
                false, reason + " adopt"));
        return true;
    }

    boolean adoptSourceBlock(
            String key, TreeBlockRole role, String reason) {
        Cell current = cells.get(key);
        if (current == null || current.ownership() != Ownership.SOURCE) {
            return false;
        }
        Cell replacement = new Cell(
                current.material(), role, Ownership.EVOLVED,
                current.persistent());
        cells.put(key, replacement);
        mutations.add(reason + " adopt " + key);
        mutationTimeline.add(new Mutation(
                mutationTimeline.size(), key, current, replacement,
                false, reason + " adopt"));
        return true;
    }

    int physicalMutationCount() {
        return physicalChanges.values().stream()
                .mapToInt(Integer::intValue).sum();
    }

    List<String> mutations() {
        return List.copyOf(mutations);
    }

    List<Mutation> mutationTimeline() {
        return List.copyOf(mutationTimeline);
    }

    TreeSimulatedMinecraftEnvironment environment() {
        return environment;
    }

    String fingerprint() {
        return cells.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "="
                        + entry.getValue().material() + "/"
                        + entry.getValue().role() + "/"
                        + entry.getValue().ownership())
                .reduce("", (left, right) -> left + "|" + right);
    }

    void assertNoCoordinateChurn(Set<String> allowedMultiMutationKeys) {
        for (Map.Entry<String, Integer> entry : physicalChanges.entrySet()) {
            int maximum = allowedMultiMutationKeys.contains(entry.getKey())
                    ? 3 : 1;
            if (entry.getValue() > maximum) {
                throw new IllegalStateException(
                        "coordinate churn at " + entry.getKey()
                                + " changes=" + entry.getValue()
                                + " maximum=" + maximum
                                + " history=" + mutationTimeline.stream()
                                        .filter(mutation -> mutation.key()
                                                .equals(entry.getKey()))
                                        .toList());
            }
        }
    }

    void assertTimelineReconstructs(Map<String, Cell> initialCells) {
        Map<String, Cell> reconstructed = new LinkedHashMap<>(initialCells);
        for (Mutation mutation : mutationTimeline) {
            Cell current = reconstructed.get(mutation.key());
            if (!Objects.equals(current, mutation.before())) {
                throw new IllegalStateException(
                        "voxel timeline diverged before time="
                                + mutation.sequence() + " key="
                                + mutation.key() + " expected="
                                + mutation.before() + " actual=" + current);
            }
            if (mutation.after() == null) {
                reconstructed.remove(mutation.key());
            } else {
                reconstructed.put(mutation.key(), mutation.after());
            }
        }
        if (!reconstructed.equals(cells)) {
            throw new IllegalStateException(
                    "voxel timeline did not reconstruct the final world");
        }
    }

    List<String> sortedKeys() {
        return cells.keySet().stream().sorted(Comparator.naturalOrder()).toList();
    }

    private void requireMutable(String key, Cell current) {
        if (current != null && current.ownership() == Ownership.NEIGHBOR) {
            throw new IllegalStateException(
                    "constructor attempted to mutate neighboring tree block "
                            + key);
        }
    }

    private void changed(
            String key,
            Cell before,
            Cell after,
            String reason
    ) {
        physicalChanges.merge(key, 1, Integer::sum);
        mutations.add(reason + " " + key);
        mutationTimeline.add(new Mutation(
                mutationTimeline.size(), key, before, after, true, reason));
    }
}
