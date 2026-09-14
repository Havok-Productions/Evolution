package org.evolution.features.treeevolution;

import java.util.Set;
import java.util.UUID;

/**
 * ## Proves known transitions resume from exact ownership receipts.
 */
public final class TreeKnownOwnershipPolicySmokeTest {
    private TreeKnownOwnershipPolicySmokeTest() {
    }

    public static void main(String[] args) {
        TreeDna dna = TreeShapeSmokeTest.sampleDna(
                TreeSpecies.OAK, TreeMaturityStage.MEDIUM, 91);
        String root = key(dna, 0, 0, 0);
        String sourceLeaf = key(dna, 1, 5, 0);
        String retiredLeaf = key(dna, -1, 5, 0);
        String evolvedLeaf = key(dna, 2, 8, 1);
        dna.restoreOriginalShape(
                Set.of(root),
                Set.of(sourceLeaf, retiredLeaf),
                Set.of(retiredLeaf),
                Set.of(key(dna, 0, 6, 0)),
                Set.of(evolvedLeaf),
                TreeTransitionLedger.CURRENT_OWNERSHIP_VERSION);

        TreeKnownOwnershipPolicy.Snapshot snapshot =
                TreeKnownOwnershipPolicy.snapshot(dna);
        require(snapshot.complete(),
                "current rooted ledger must provide complete ownership");
        require(snapshot.ownedKeys().contains(root),
                "source root must remain owned");
        require(snapshot.ownedKeys().contains(sourceLeaf),
                "unresolved source foliage must remain owned");
        require(snapshot.ownedKeys().contains(evolvedLeaf),
                "evolved foliage must remain owned");
        require(!snapshot.ownedKeys().contains(retiredLeaf),
                "retired source foliage must not re-enter ownership");

        dna.restoreOriginalShape(
                Set.of(root), Set.of(sourceLeaf), Set.of(),
                Set.of(), Set.of(), 1);
        require(!TreeKnownOwnershipPolicy.snapshot(dna).complete(),
                "legacy/incomplete ledgers must fall back to discovery");

        System.out.println(
                "Known ownership policy smoke test passed: "
                        + "current-ledger=true retired-excluded=true "
                        + "legacy-fallback=true");
    }

    private static String key(
            TreeDna dna, int dx, int dy, int dz) {
        UUID world = dna.worldId();
        return world + ":" + (dna.baseX() + dx)
                + ":" + (dna.baseY() + dy)
                + ":" + (dna.baseZ() + dz);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
