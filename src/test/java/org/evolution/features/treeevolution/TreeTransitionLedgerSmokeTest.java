package org.evolution.features.treeevolution;

import java.util.List;
import java.util.Set;

public final class TreeTransitionLedgerSmokeTest {
    private TreeTransitionLedgerSmokeTest() {
    }

    public static void main(String[] args) {
        String sourceLeaf = "world:10:70:10";
        String evolvedLeaf = "world:12:72:10";
        TreeTransitionLedger captured = TreeTransitionLedger.capture(
                List.of("world:10:68:10"),
                List.of(sourceLeaf, "world:11:70:10"));

        require(captured.canRetireLeaf(sourceLeaf),
                "Captured source leaf should be retireable.");
        require(captured.requiresEvolvedLeafOwnership(),
                "A current transition must enforce evolved-leaf ownership.");
        require(!captured.countsAsEvolvedLeaf(sourceLeaf),
                "An original source leaf must not satisfy a new branch envelope.");

        TreeTransitionLedger reformed = captured.recordEvolvedLeaf(sourceLeaf);
        require(reformed.countsAsEvolvedLeaf(sourceLeaf),
                "An explicitly reformed source leaf must satisfy the envelope.");
        TreeTransitionLedger evolved = reformed.recordEvolvedLeaf(evolvedLeaf);
        require(evolved.countsAsEvolvedLeaf(evolvedLeaf),
                "A leaf placed by this evolution must be owned.");

        TreeTransitionLedger retired = evolved.retireLeaf(sourceLeaf);
        require(!retired.canRetireLeaf(sourceLeaf),
                "A retired coordinate must never be pruned twice.");
        require(retired.retireLeaf(sourceLeaf) == retired,
                "Repeated retirement should be idempotent.");

        TreeTransitionLedger restored = TreeTransitionLedger.restore(
                retired.sourceLogs(),
                retired.sourceLeaves(),
                retired.retiredLeaves(),
                retired.evolvedLogs(),
                retired.evolvedLeaves(),
                retired.ownershipVersion());
        require(!restored.canRetireLeaf(sourceLeaf),
                "Persisted retirement must survive reload.");
        require(restored.sourceBlockCount() == 3,
                "The immutable source snapshot must remain intact.");
        require(restored.countsAsEvolvedLeaf(sourceLeaf),
                "Persisted reform ownership must survive reload.");

        TreeTransitionLedger completed = restored.completeTransition();
        require(!completed.hasSnapshot(),
                "Stage completion should close the original-shape snapshot.");
        require(completed.countsAsEvolvedLeaf(sourceLeaf)
                        && completed.countsAsEvolvedLeaf(evolvedLeaf),
                "Completed trees must retain the last evolved canopy epoch for audit.");

        TreeTransitionLedger nextStage = TreeTransitionLedger.capture(
                Set.of("world:10:68:10"), Set.of(sourceLeaf));
        require(!nextStage.countsAsEvolvedLeaf(sourceLeaf),
                "Capturing the next stage must reset ownership until leaves are reformed again.");

        TreeTransitionLedger legacyActive = TreeTransitionLedger.restore(
                Set.of("world:10:68:10"), Set.of(sourceLeaf), Set.of());
        require(!legacyActive.countsAsEvolvedLeaf(sourceLeaf),
                "Legacy source leaves must still be explicitly reformed.");
        require(legacyActive.countsAsEvolvedLeaf(evolvedLeaf),
                "A legacy active transition may infer only a post-snapshot leaf.");

        TreeTransitionLedger legacyCompleted = TreeTransitionLedger.restore(
                Set.of(), Set.of(), Set.of());
        require(legacyCompleted.countsAsEvolvedLeaf(sourceLeaf),
                "Legacy completed trees must remain grandfathered instead of reopening.");

        System.out.println(
                "Tree transition ledger smoke test passed: source-reform=true "
                        + "completion-audit=true next-stage-reset=true");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
