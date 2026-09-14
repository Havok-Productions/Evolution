package org.evolution.features.treeevolution;

import java.util.Set;

/**
 * ## Proves touching leaves cannot assign detached neighboring wood.
 */
public final class TreeWoodOwnershipGraphSmokeTest {
    private TreeWoodOwnershipGraphSmokeTest() {
    }

    public static void main(String[] args) {
        TreeDna dna = TreeShapeSmokeTest.sampleDna(
                TreeSpecies.OAK, TreeMaturityStage.MEDIUM, 51);
        String root = key(dna, 0, 0, 0);
        String trunk = key(dna, 0, 1, 0);
        String diagonalBranch = key(dna, 1, 2, 0);
        String foreignOne = key(dna, 5, 3, 2);
        String foreignTwo = key(dna, 6, 4, 2);
        Set<String> connected = TreeWoodOwnershipGraph.connectedToRoot(
                dna, Set.of(
                        root, trunk, diagonalBranch,
                        foreignOne, foreignTwo));
        require(connected.equals(
                        Set.of(root, trunk, diagonalBranch)),
                "only the stump-connected 3D wood graph may be owned");
        System.out.println(
                "Wood ownership graph smoke test passed: "
                        + "diagonal-branch=true detached-neighbor=false");
    }

    private static String key(
            TreeDna dna, int dx, int dy, int dz) {
        return dna.worldId() + ":" + (dna.baseX() + dx)
                + ":" + (dna.baseY() + dy)
                + ":" + (dna.baseZ() + dz);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
