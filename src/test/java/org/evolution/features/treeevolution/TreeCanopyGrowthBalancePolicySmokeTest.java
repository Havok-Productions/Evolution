package org.evolution.features.treeevolution;

import java.util.List;

/**
 * ## Guards against coordinate-order canopy construction.
 */
public final class TreeCanopyGrowthBalancePolicySmokeTest {
    private TreeCanopyGrowthBalancePolicySmokeTest() {
    }

    public static void main(String[] args) {
        TreeDna dna = TreeShapeSmokeTest.sampleDna(
                TreeSpecies.OAK, TreeMaturityStage.MEDIUM, 37);
        List<PlannedTreeBlock> sectors = List.of(
                leaf(dna, -3, 8, 0),
                leaf(dna, 3, 8, 0),
                leaf(dna, 0, 8, -3),
                leaf(dna, 0, 8, 3));

        dna.markEvolvedLeaf(worldKey(dna, sectors.get(0)));
        TreeCanopyGrowthBalancePolicy balance =
                TreeCanopyGrowthBalancePolicy.from(dna);
        require(balance.bonus(sectors.get(0)) == 0.0D,
                "occupied west sector must not receive a balance bonus");
        require(balance.bonus(sectors.get(1)) > 0.0D
                        && balance.bonus(sectors.get(2)) > 0.0D
                        && balance.bonus(sectors.get(3)) > 0.0D,
                "every untouched sector must outrank coordinate continuation");

        for (int index = 1; index < sectors.size(); index++) {
            dna.markEvolvedLeaf(worldKey(dna, sectors.get(index)));
        }
        balance = TreeCanopyGrowthBalancePolicy.from(dna);
        for (PlannedTreeBlock sector : sectors) {
            require(balance.bonus(sector) == 0.0D,
                    "equally occupied sectors must return to species scoring");
        }
        System.out.println(
                "Canopy growth balance smoke test passed: "
                        + "four-sector-distribution=true deterministic=true");
    }

    private static PlannedTreeBlock leaf(
            TreeDna dna, int dx, int dy, int dz) {
        int y = dna.baseY() + dy;
        return new PlannedTreeBlock(
                dna.trunkXAt(y) + dx,
                y,
                dna.trunkZAt(y) + dz,
                dna.species().leafMaterial(),
                TreeBlockRole.CANOPY,
                org.bukkit.Axis.Y,
                null);
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
