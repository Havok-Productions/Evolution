package org.evolution.features.treeevolution;

import java.util.List;
import java.util.Set;
import org.bukkit.Axis;

/**
 * ## Reproduces the live 100%-material / disconnected-receipt mismatch.
 */
public final class TreeTargetOwnershipRepairPolicySmokeTest {
    private TreeTargetOwnershipRepairPolicySmokeTest() {
    }

    public static void main(String[] args) {
        TreeDna dna = TreeShapeSmokeTest.sampleDna(
                TreeSpecies.OAK, TreeMaturityStage.MEDIUM, 3);
        PlannedTreeBlock root = trunk(dna, 0);
        PlannedTreeBlock bridge = trunk(dna, 1);
        PlannedTreeBlock orphan = trunk(dna, 2);
        List<PlannedTreeBlock> plan = List.of(root, bridge, orphan);
        String rootKey = worldKey(dna, root);
        String bridgeKey = worldKey(dna, bridge);
        String orphanKey = worldKey(dna, orphan);
        dna.restoreOriginalShape(
                List.of(rootKey), List.of(), List.of(),
                List.of(orphanKey), List.of(), 2);

        TreeTargetOwnershipRepairPolicy.Analysis liveBridge =
                TreeTargetOwnershipRepairPolicy.inspect(
                        dna, plan,
                        Set.of(rootKey, orphanKey),
                        Set.of(rootKey, bridgeKey, orphanKey),
                        Set.of());
        require(liveBridge.required(),
                "orphan receipt was not detected");
        TreeTargetOwnershipRepairPolicy.Repair adoption =
                liveBridge.repair().orElseThrow();
        require(adoption.block().key().equals(bridge.key())
                        && adoption.action()
                                == TreeTargetOwnershipRepairPolicy.Action
                                        .ADOPT_LIVE_TARGET,
                "live unreceipted bridge was not selected for adoption");

        TreeTargetOwnershipRepairPolicy.Repair placement =
                TreeTargetOwnershipRepairPolicy.inspect(
                        dna, plan,
                        Set.of(rootKey, orphanKey),
                        Set.of(rootKey, orphanKey),
                        Set.of())
                        .repair().orElseThrow();
        require(placement.block().key().equals(bridge.key())
                        && placement.action()
                                == TreeTargetOwnershipRepairPolicy.Action
                                        .PLACE_MISSING_TARGET,
                "missing physical bridge was not selected for placement");

        require(dna.markEvolvedBlock(
                        bridgeKey, TreeBlockRole.TRUNK),
                "test could not adopt bridge receipt");
        require(!TreeTargetOwnershipRepairPolicy.inspect(
                        dna, plan,
                        Set.of(rootKey, bridgeKey, orphanKey),
                        Set.of(rootKey, bridgeKey, orphanKey),
                        Set.of()).required(),
                "adopted bridge did not reconnect the orphan receipt");
        selectsLargestDisconnectedComponentBridge();
        repairsDisconnectedCanopyOwnership(dna, rootKey);
        System.out.println("Target ownership repair policy smoke passed: "
                + "wood=true canopy=true adopt-live=true "
                + "place-missing=true reconnect=true");
    }

    private static void selectsLargestDisconnectedComponentBridge() {
        TreeDna dna = TreeShapeSmokeTest.sampleDna(
                TreeSpecies.OAK, TreeMaturityStage.MEDIUM, 17);
        PlannedTreeBlock root = trunk(dna, 0);
        PlannedTreeBlock bridge = trunk(dna, 1);
        PlannedTreeBlock orphan = trunk(dna, 2);
        PlannedTreeBlock orphanTop = trunk(dna, 3);
        PlannedTreeBlock lowGain = new PlannedTreeBlock(
                dna.baseX() - 1, dna.baseY(), dna.baseZ(),
                dna.species().logMaterial(), TreeBlockRole.BRANCH,
                Axis.X, null);
        String rootKey = worldKey(dna, root);
        String orphanKey = worldKey(dna, orphan);
        String orphanTopKey = worldKey(dna, orphanTop);
        dna.restoreOriginalShape(
                List.of(rootKey), List.of(), List.of(),
                List.of(orphanKey, orphanTopKey), List.of(), 2);

        TreeTargetOwnershipRepairPolicy.Repair selected =
                TreeTargetOwnershipRepairPolicy.inspect(
                        dna,
                        List.of(root, lowGain, bridge, orphan, orphanTop),
                        Set.of(rootKey, orphanKey, orphanTopKey),
                        Set.of(rootKey, orphanKey, orphanTopKey),
                        Set.of()).repair().orElseThrow();
        require(selected.block().key().equals(bridge.key())
                        && selected.rootedBefore() == 1
                        && selected.rootedAfter() == 4,
                "connectivity index did not select the maximum-gain bridge");
    }

    private static void repairsDisconnectedCanopyOwnership(
            TreeDna dna, String rootKey) {
        PlannedTreeBlock leafBridge = new PlannedTreeBlock(
                dna.baseX() + 1, dna.baseY(), dna.baseZ(),
                dna.species().leafMaterial(),
                TreeBlockRole.CANOPY, Axis.Y, null);
        PlannedTreeBlock leafOrphan = new PlannedTreeBlock(
                dna.baseX() + 2, dna.baseY(), dna.baseZ(),
                dna.species().leafMaterial(),
                TreeBlockRole.CANOPY, Axis.Y, null);
        String bridgeKey = worldKey(dna, leafBridge);
        String orphanKey = worldKey(dna, leafOrphan);
        dna.restoreOriginalShape(
                List.of(rootKey), List.of(), List.of(),
                List.of(), List.of(orphanKey), 2);
        TreeTargetOwnershipRepairPolicy.Repair repair =
                TreeTargetOwnershipRepairPolicy.inspect(
                        dna, List.of(trunk(dna, 0),
                                leafBridge, leafOrphan),
                        Set.of(rootKey), Set.of(rootKey),
                        Set.of(orphanKey),
                        Set.of(bridgeKey, orphanKey),
                        Set.of()).repair().orElseThrow();
        require(repair.block().key().equals(leafBridge.key())
                        && repair.action()
                                == TreeTargetOwnershipRepairPolicy.Action
                                        .ADOPT_LIVE_TARGET,
                "disconnected canopy did not select its rootward leaf bridge");
    }

    private static PlannedTreeBlock trunk(TreeDna dna, int dy) {
        return new PlannedTreeBlock(
                dna.baseX(), dna.baseY() + dy, dna.baseZ(),
                dna.species().logMaterial(),
                TreeBlockRole.TRUNK, Axis.Y, null);
    }

    private static String worldKey(
            TreeDna dna, PlannedTreeBlock block) {
        return dna.worldId() + ":" + block.key();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
