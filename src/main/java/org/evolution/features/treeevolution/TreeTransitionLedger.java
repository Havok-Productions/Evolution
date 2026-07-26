package org.evolution.features.treeevolution;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable ownership ledger for one stage transition.
 */
final class TreeTransitionLedger {
    static final int CURRENT_OWNERSHIP_VERSION = 1;

    private static final TreeTransitionLedger EMPTY =
            new TreeTransitionLedger(
                    Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), 0);

    private final Set<String> sourceLogs;
    private final Set<String> sourceLeaves;
    private final Set<String> retiredLeaves;
    private final Set<String> evolvedLogs;
    private final Set<String> evolvedLeaves;
    private final int ownershipVersion;

    private TreeTransitionLedger(
            Set<String> sourceLogs,
            Set<String> sourceLeaves,
            Set<String> retiredLeaves,
            Set<String> evolvedLogs,
            Set<String> evolvedLeaves,
            int ownershipVersion
    ) {
        this.sourceLogs = sourceLogs;
        this.sourceLeaves = sourceLeaves;
        this.retiredLeaves = retiredLeaves;
        this.evolvedLogs = evolvedLogs;
        this.evolvedLeaves = evolvedLeaves;
        this.ownershipVersion = Math.max(0, ownershipVersion);
    }

    static TreeTransitionLedger empty() {
        return EMPTY;
    }

    static TreeTransitionLedger capture(
            Collection<String> logKeys,
            Collection<String> leafKeys
    ) {
        return restore(
                logKeys, leafKeys, Set.of(), Set.of(), Set.of(),
                CURRENT_OWNERSHIP_VERSION);
    }

    static TreeTransitionLedger restore(
            Collection<String> logKeys,
            Collection<String> leafKeys,
            Collection<String> retiredLeafKeys
    ) {
        return restore(
                logKeys, leafKeys, retiredLeafKeys,
                Set.of(), Set.of(), 0);
    }

    static TreeTransitionLedger restore(
            Collection<String> logKeys,
            Collection<String> leafKeys,
            Collection<String> retiredLeafKeys,
            Collection<String> evolvedLogKeys,
            Collection<String> evolvedLeafKeys,
            int ownershipVersion
    ) {
        Set<String> logs = immutableKeys(logKeys);
        Set<String> leaves = immutableKeys(leafKeys);
        Set<String> retired = new HashSet<>(immutableKeys(retiredLeafKeys));
        retired.retainAll(leaves);
        Set<String> evolvedLogs = immutableKeys(evolvedLogKeys);
        Set<String> evolvedLeaves = immutableKeys(evolvedLeafKeys);
        if (logs.isEmpty() && leaves.isEmpty()
                && evolvedLogs.isEmpty() && evolvedLeaves.isEmpty()
                && ownershipVersion <= 0) {
            return EMPTY;
        }
        return new TreeTransitionLedger(
                logs, leaves, Set.copyOf(retired),
                evolvedLogs, evolvedLeaves, ownershipVersion);
    }

    TreeTransitionLedger retireLeaf(String blockKey) {
        if (!sourceLeaves.contains(blockKey)
                || retiredLeaves.contains(blockKey)) {
            return this;
        }
        Set<String> updated = new HashSet<>(retiredLeaves);
        updated.add(blockKey);
        return copyWith(
                sourceLogs, sourceLeaves, Set.copyOf(updated),
                evolvedLogs, evolvedLeaves, ownershipVersion);
    }

    TreeTransitionLedger recordEvolvedLog(String blockKey) {
        return recordEvolved(blockKey, true);
    }

    TreeTransitionLedger recordEvolvedLeaf(String blockKey) {
        return recordEvolved(blockKey, false);
    }

    TreeTransitionLedger completeTransition() {
        // ## Keep the last evolution epoch after the source snapshot closes.
        // Completed trees remain auditable until the next stage captures a new epoch.
        return copyWith(
                Set.of(), Set.of(), Set.of(),
                evolvedLogs, evolvedLeaves, CURRENT_OWNERSHIP_VERSION);
    }

    boolean canRetireLeaf(String blockKey) {
        return sourceLeaves.contains(blockKey)
                && !retiredLeaves.contains(blockKey);
    }

    boolean hasSnapshot() {
        return !sourceLogs.isEmpty() || !sourceLeaves.isEmpty();
    }

    boolean requiresEvolvedLeafOwnership() {
        // Legacy completed trees have no source or ownership history and stay
        // grandfathered. Active legacy transitions remain auditable by snapshot.
        return ownershipVersion >= CURRENT_OWNERSHIP_VERSION || hasSnapshot();
    }

    boolean countsAsEvolvedLeaf(String blockKey) {
        return TreeBranchEnvelopeOwnershipPolicy.countsAsEvolvedLeaf(
                requiresEvolvedLeafOwnership(),
                evolvedLeaves.contains(blockKey),
                ownershipVersion < CURRENT_OWNERSHIP_VERSION && hasSnapshot(),
                sourceLeaves.contains(blockKey));
    }

    boolean isOriginalLeaf(String blockKey) {
        return sourceLeaves.contains(blockKey);
    }

    int sourceBlockCount() {
        return sourceLogs.size() + sourceLeaves.size();
    }

    int ownershipVersion() {
        return ownershipVersion;
    }

    Set<String> sourceLogs() {
        return sourceLogs;
    }

    Set<String> sourceLeaves() {
        return sourceLeaves;
    }

    Set<String> retiredLeaves() {
        return retiredLeaves;
    }

    Set<String> evolvedLogs() {
        return evolvedLogs;
    }

    Set<String> evolvedLeaves() {
        return evolvedLeaves;
    }

    private TreeTransitionLedger recordEvolved(
            String blockKey, boolean wood) {
        if (blockKey == null || blockKey.isBlank()) {
            return this;
        }
        Set<String> existing = wood ? evolvedLogs : evolvedLeaves;
        if (existing.contains(blockKey)) {
            return this;
        }
        Set<String> updated = new HashSet<>(existing);
        updated.add(blockKey);
        return wood
                ? copyWith(
                        sourceLogs, sourceLeaves, retiredLeaves,
                        Set.copyOf(updated), evolvedLeaves, ownershipVersion)
                : copyWith(
                        sourceLogs, sourceLeaves, retiredLeaves,
                        evolvedLogs, Set.copyOf(updated), ownershipVersion);
    }

    private TreeTransitionLedger copyWith(
            Set<String> logs,
            Set<String> leaves,
            Set<String> retired,
            Set<String> placedLogs,
            Set<String> placedLeaves,
            int version
    ) {
        return new TreeTransitionLedger(
                logs, leaves, retired, placedLogs, placedLeaves, version);
    }

    private static Set<String> immutableKeys(Collection<String> keys) {
        return keys == null || keys.isEmpty()
                ? Set.of()
                : Set.copyOf(keys);
    }
}