package org.evolution.features.treeevolution;

import java.util.Map;
import java.util.Set;

/** Regression coverage for logical ownership-role migration. */
public final class TreeOwnershipRoleReconciliationPolicySmokeTest {
    private TreeOwnershipRoleReconciliationPolicySmokeTest() {
    }

    public static void main(String[] args) {
        TreeDna dna = TreeShapeSmokeTest.sampleDna(
                TreeSpecies.SPRUCE, TreeMaturityStage.MEDIUM, 0);
        String key = dna.worldId() + ":2:71:0";
        dna.restoreOriginalShape(
                Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(key),
                TreeTransitionLedger.CURRENT_OWNERSHIP_VERSION);

        TreeOwnershipRoleReconciliationPolicy.Repair leafToWood =
                TreeOwnershipRoleReconciliationPolicy.next(
                                dna.evolvedShapeLogs(),
                                dna.evolvedShapeLeaves(),
                                Map.of(key,
                                        TreeOwnershipRoleReconciliationPolicy
                                                .Role.WOOD),
                                java.util.List.of())
                        .orElseThrow();
        require(dna.reconcileEvolvedRole(
                        key, leafToWood.liveRole()),
                "leaf receipt did not move to wood");
        require(dna.evolvedShapeLogs().contains(key)
                        && !dna.evolvedShapeLeaves().contains(key),
                "leaf-to-wood move was not exclusive");

        require(dna.markEvolvedBlock(key, TreeBlockRole.CANOPY),
                "normal placement did not move wood receipt to canopy");
        require(!dna.evolvedShapeLogs().contains(key)
                        && dna.evolvedShapeLeaves().contains(key),
                "normal placement retained the opposite receipt role");

        dna.restoreOriginalShape(
                Set.of(), Set.of(), Set.of(),
                Set.of(key), Set.of(key),
                TreeTransitionLedger.CURRENT_OWNERSHIP_VERSION);
        TreeOwnershipRoleReconciliationPolicy.Repair duplicate =
                TreeOwnershipRoleReconciliationPolicy.next(
                                dna.evolvedShapeLogs(),
                                dna.evolvedShapeLeaves(),
                                Map.of(key,
                                        TreeOwnershipRoleReconciliationPolicy
                                                .Role.CANOPY),
                                java.util.List.of())
                        .orElseThrow();
        require(duplicate.duplicateReceipt(),
                "duplicate receipt was not identified");
        require(dna.reconcileEvolvedRole(key, duplicate.liveRole()),
                "duplicate receipt did not normalize");
        require(!dna.evolvedShapeLogs().contains(key)
                        && dna.evolvedShapeLeaves().contains(key),
                "duplicate receipt retained two roles");

        require(TreeObsoleteReceiptPolicy.classify(
                        true, false, false)
                        == TreeObsoleteReceiptPolicy.Disposition
                                .KEEP_CURRENT_TARGET,
                "bounded candidate omission retired a current target");
        require(TreeObsoleteReceiptPolicy.classify(
                        false, false, false)
                        == TreeObsoleteReceiptPolicy.Disposition
                                .RETIRE_DISCONNECTED_OUTSIDE_TARGET,
                "outside-target disconnected receipt was retained");

        System.out.println(
                "Tree ownership role reconciliation smoke test passed: "
                        + "exclusive-ledger=true target-retirement=false");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
